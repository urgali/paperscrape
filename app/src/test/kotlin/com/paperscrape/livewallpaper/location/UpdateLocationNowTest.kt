package com.paperscrape.livewallpaper.location

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **Update location now, and the searches around it** (decision 111):
 *
 * - what the tap takes from the phone's answer ([DeviceLocationProvider.tapResult]): a position it was really consulted
 *   for, taken within the last minute, never the stale one a search that found nothing falls back to;
 * - **one search per choice** from the settings collector ([SearchPerChoice]): a store write while it runs starts none;
 * - **one search at a time in the process** ([LocationRequestLock]): the page's and the wallpaper's, and a caller that
 *   waited reads the user's choice after the wait, so a choice already answered is not asked of the radio twice.
 *
 * The phone's radio cannot run on the JVM: the rules are run here, and the wiring is read on the sources.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpdateLocationNowTest {

    private val milan = DeviceLocationFix(45.46, 9.19)

    // ------------------------------------------------------------------ what the tap takes

    @Test
    fun `the tap takes a position the phone was consulted for within the minute, with its age`() {
        assertEquals("a request's own position", HeldFix(milan, 0L), DeviceLocationProvider.tapResult(DeviceFixAnswer(milan, consulted = true, ageMillis = 0L)))
        assertEquals(
            "one the phone took 59 s ago stands for one",
            HeldFix(milan, 59_000L),
            DeviceLocationProvider.tapResult(DeviceFixAnswer(milan, consulted = true, ageMillis = 59_000L)),
        )
        assertEquals(
            "a minute exactly still does",
            HeldFix(milan, DeviceLocationProvider.TAP_MAX_AGE_MS),
            DeviceLocationProvider.tapResult(DeviceFixAnswer(milan, consulted = true, ageMillis = DeviceLocationProvider.TAP_MAX_AGE_MS)),
        )
    }

    @Test
    fun `the stale position a search that found nothing falls back to is not a position found`() {
        val threeDays = 3 * 24 * 60 * 60 * 1000L
        assertNull("a request that found nothing, the old cache returned", DeviceLocationProvider.tapResult(DeviceFixAnswer(milan, consulted = true, ageMillis = threeDays)))
        assertNull("a minute and a millisecond", DeviceLocationProvider.tapResult(DeviceFixAnswer(milan, consulted = true, ageMillis = DeviceLocationProvider.TAP_MAX_AGE_MS + 1)))
        assertNull("the phone not consulted (its location off)", DeviceLocationProvider.tapResult(DeviceFixAnswer(milan, consulted = false, ageMillis = 1_000L)))
        assertNull("nothing at all", DeviceLocationProvider.tapResult(DeviceFixAnswer(null, consulted = true)))
        assertNull("a position from the future says the clock moved", DeviceLocationProvider.tapResult(DeviceFixAnswer(milan, consulted = true, ageMillis = -1L)))
    }

    @Test
    fun `the tap searches a minute, past the wait, through the process's one lock`() {
        assertEquals(60_000L, DeviceLocationProvider.TAP_TIMEOUT_MS)
        assertEquals(60_000L, DeviceLocationProvider.TAP_MAX_AGE_MS)
        val provider = codeLines(source("location/DeviceLocationProvider.kt"))
        assertLinesInARow(
            "the tap's body: the lock, the time waited taken off the minute, the forced ask, the answer",
            provider,
            "suspend fun positionNow(kind: DeviceLocationKind): HeldFix? {",
            "val tappedAt = SystemClock.elapsedRealtime()",
            "val answer = LocationRequestLock.withLock {",
            "val timeout = tapTimeoutAfterWait(SystemClock.elapsedRealtime() - tappedAt)",
            "currentFix(kind, forceRequest = true, maxAgeMillis = TAP_MAX_AGE_MS, timeoutMillis = timeout)",
            "}",
            "return tapResult(answer)",
            "}",
        )
        // Every answer carries the position's age: a stale cached position keeps its true age.
        assertTrue("FixStep.FRESH_CACHE -> DeviceFixAnswer(cached?.toFix(), consulted = true, ageMillis = cachedAge)" in provider)
        assertTrue("FixStep.PROVIDER_OFF, FixStep.WAIT -> DeviceFixAnswer(cached?.toFix(), consulted = false, ageMillis = cachedAge)" in provider)
        assertTrue("DeviceFixAnswer(live.fix, consulted = true, ageMillis = live.ageMillis)" in provider)
    }

    @Test
    fun `the tap's search ends within the minute of the tap, the wait for the lock included`() {
        val minute = DeviceLocationProvider.TAP_TIMEOUT_MS
        val floor = DeviceLocationProvider.TAP_MIN_TIMEOUT_MS
        assertTrue("a small positive floor", floor in 1L..10_000L)
        assertEquals("no wait: the whole minute", minute, DeviceLocationProvider.tapTimeoutAfterWait(0L))
        assertEquals(
            "behind one of the engine's searches: the rest of the minute",
            minute - DeviceLocationProvider.REQUEST_TIMEOUT_MS,
            DeviceLocationProvider.tapTimeoutAfterWait(DeviceLocationProvider.REQUEST_TIMEOUT_MS),
        )
        assertEquals("a wait read out of order counts as none", minute, DeviceLocationProvider.tapTimeoutAfterWait(-5_000L))
        assertEquals("past the minute, the floor", floor, DeviceLocationProvider.tapTimeoutAfterWait(minute + 1))
        // Every wait up to the floor's edge: the wait and the search together are the minute, never more.
        var waited = 0L
        while (waited <= minute - floor) {
            assertEquals("waited $waited ms", minute, waited + DeviceLocationProvider.tapTimeoutAfterWait(waited))
            waited += 250L
        }
        // Behind both engines' searches (the home screen's and the picker's preview), the minute still holds.
        val twoSearches = 2 * DeviceLocationProvider.REQUEST_TIMEOUT_MS
        assertTrue(twoSearches + DeviceLocationProvider.tapTimeoutAfterWait(twoSearches) <= minute)
    }

    // ------------------------------------------------------------------ one search per choice

    @Test
    fun `a store write while the collector's search runs starts none, and another source gets its own`() = runTest {
        val searches = SearchPerChoice()
        val radio = CompletableDeferred<Unit>()
        val started = mutableListOf<LocationSource>()
        fun search(source: LocationSource): suspend () -> Unit = { started += source; radio.await() }

        assertTrue("the choice's search", searches.startUnlessRunning(this, LocationSource.GPS, search(LocationSource.GPS)))
        runCurrent()
        // The engine publishing its status, the page writing anything: each write runs the collector again.
        repeat(3) { assertFalse("a write while it runs", searches.startUnlessRunning(this, LocationSource.GPS, search(LocationSource.GPS))) }
        runCurrent()
        assertEquals("one search for the choice", listOf(LocationSource.GPS), started)
        assertTrue(searches.isRunningFor(LocationSource.GPS))

        // Network chosen while the GPS search runs is a new choice, answered by its own search.
        assertTrue(searches.startUnlessRunning(this, LocationSource.NETWORK, search(LocationSource.NETWORK)))
        runCurrent()
        assertFalse(searches.startUnlessRunning(this, LocationSource.NETWORK, search(LocationSource.NETWORK)))
        assertEquals(listOf(LocationSource.GPS, LocationSource.NETWORK), started)

        // Once it has ended, a later write may start one again, under the throttle's waits like any other ask.
        radio.complete(Unit)
        advanceUntilIdle()
        assertFalse(searches.isRunningFor(LocationSource.NETWORK))
        assertTrue(searches.startUnlessRunning(this, LocationSource.NETWORK, search(LocationSource.NETWORK)))
        advanceUntilIdle()
        assertEquals(3, started.size)
    }

    // ------------------------------------------------------------------ one search at a time

    @Test
    fun `two searches at once take turns on the real lock, and the second's block starts after the first has ended`() = runTest {
        // The engine's two callers -- the collector and the weather loop -- or the page's tap beside them. What the
        // second reads inside its block (the engine's force) is read once the first has spent it.
        val radio = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        var choicePending = true
        launch {
            LocationRequestLock.withLock {
                order += "first in, force $choicePending"
                radio.await()
                choicePending = false
                order += "first out"
            }
        }
        launch { LocationRequestLock.withLock { order += "second in, force $choicePending" } }
        runCurrent()
        assertTrue("the first holds the lock", LocationRequestLock.isLocked)
        assertEquals("the second waits", listOf("first in, force true"), order)
        radio.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("first in, force true", "first out", "second in, force false"), order)
        assertFalse(LocationRequestLock.isLocked)
    }

    @Test
    fun `the wallpaper and the settings page share the process's one lock`() {
        val engine = codeLines(source("engine/PaperWallpaperService.kt"))
        assertTrue("no lock of the service's own", engine.none { "Mutex" in it })
        assertTrue(engine.none { "locationRequestLock" in it })
        assertLinesInARow(
            "the engine's one ask: the force is a function, read inside the lock",
            engine,
            "private suspend fun deviceFix(kind: DeviceLocationKind, force: () -> Boolean): DeviceFixAnswer =",
            "LocationRequestLock.withLock { locationProvider().currentFix(kind, forceRequest = force()) }",
        )
        val ask = engine.indexOf("val answer = deviceFix(kind) { userChoseDeviceSource }")
        assertTrue("the collector's search hands the choice in as that function", ask >= 0)
        val spend = engine.indexOf("if (answer.consulted) userChoseDeviceSource = false")
        assertTrue("and spends it only once the phone was consulted, after the answer", spend > ask)
        assertEquals("every ask of the engine goes through deviceFix", 1, engine.count { ".currentFix(" in it })
        val page = codeLines(source("ui/WeatherTimeScreen.kt"))
        assertTrue("val found = DeviceLocationProvider(context.applicationContext).positionNow(kind)" in page)
        assertTrue("the page asks only through positionNow", page.none { ".currentFix(" in it })
        val provider = codeLines(source("location/DeviceLocationProvider.kt"))
        assertEquals("the provider takes the lock only for the tap", 1, provider.count { "LocationRequestLock.withLock" in it })
    }

    /**
     * **The engine's side of a refresh ask** (decision 111): when the page's ask changes, after the engine's first
     * settings, the saved position is taken for the sun times, the last request's place and time are forgotten -- so
     * the weather is asked at the next pass on screen whatever the distance (decision 101's 3 km rule not applied) --
     * and the loop is woken.
     */
    @Test
    fun `a refresh ask makes the engine forget the last request's place and time`() {
        val engine = codeLines(source("engine/PaperWallpaperService.kt"))
        assertLinesInARow(
            "the collector's block for a refresh ask",
            engine,
            "if (settingsApplied && newSettings.refreshAskedAtMillis != previousSettings.refreshAskedAtMillis) {",
            "val kind = requestedSource.deviceKind",
            "if (kind != null && newSettings.syncWithRealTime && deviceAccess(kind).mayUsePosition) {",
            "savedDeviceFix()?.let { updateSunTimesFromLocation(it) }",
            "}",
            "lastWeatherFetchLocation = null",
            "lastWeatherFetchElapsed = Long.MIN_VALUE / 4",
            "weatherWakeUp.trySend(Unit)",
            "}",
            "settingsApplied = true",
        )
    }

    /** [lines] holds [expected] one after the other, starting where its first line is. */
    private fun assertLinesInARow(message: String, lines: List<String>, vararg expected: String) {
        val at = lines.indexOf(expected.first())
        assertTrue("$message: no line `${expected.first()}`", at >= 0)
        assertEquals(message, expected.toList(), lines.subList(at, minOf(lines.size, at + expected.size)))
    }

    /** [text]'s lines, trimmed, with comment lines, trailing `//` comments and blank lines left out. */
    private fun codeLines(text: String): List<String> =
        text.lines().map { it.trim() }
            .filterNot { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }
            .map { it.substringBefore(" //").trim() }
            .filter { it.isNotEmpty() }

    private fun source(path: String): String {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/$path"
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = File(dir, "$prefix$suffix")
                if (f.isFile) return f.readText()
            }
            dir = dir.parentFile
        }
        error("source not found: $suffix")
    }
}
