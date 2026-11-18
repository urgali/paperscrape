package com.paperscrape.livewallpaper.weather

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **An engine that cannot be seen does not run the Live Weather loop, and the loop never stops while the engine lives.**
 *
 * While the engine is not on screen the loop parks on its channel with **no timeout**: no tick, no request, screen off
 * and Live Weather off alike, and a process hosting two engines (the picker's preview beside the live one) does not tick
 * twice. Coming back on screen sends on the channel and the body runs at once, so a refresh that fell due meanwhile is
 * asked for then. On screen means shown **with a surface** ([com.paperscrape.livewallpaper.engine.GlLifecyclePolicy.countsAsVisible]):
 * an engine bound to a window nobody shows fetches nothing. A coming on screen is counted ([com.paperscrape.livewallpaper.engine.PaperWallpaperService]'s
 * `showings`), so a showing the loop slept through still asks ([ShowingRefresh], `DESIGN_NOTES.md` §12, decision 99).
 *
 * **Every pass is caught whole**: an exception from the position, the sun times, the decision or the scene is logged and
 * the loop goes on to its timed wait and its next pass; only its own cancellation ends it. Without that, one failure
 * would end the weather and, with the phone's position, the sun times for the engine's life.
 *
 * ### Why this reads the source
 *
 * What has to hold is a shape: a park with no timeout, reached before any timed wait, a `try` around the pass and outside
 * the wait, and producers that run when visibility changes. The loop lives inside a `WallpaperService` engine and cannot be
 * instantiated on the JVM; what it decides is pure and tested apart ([LiveWeatherScheduleTest], [ShowingRefreshTest]).
 */
class WeatherLoopVisibilityTest {

    private val source: String by lazy { serviceSource().readText() }

    @Test
    fun `the loop parks with no timeout while the engine is invisible`() {
        val park = Regex(
            """if \(!onScreen\) \{\s*\n\s*showingRefresh\.offScreen\(\)\s*\n\s*weatherWakeUp\.receive\(\)\s*\n\s*continue\s*\n\s*\}""",
        )
        assertTrue(
            "the Live Weather loop must park on weatherWakeUp while invisible, with no timeout",
            park.containsMatchIn(source),
        )
    }

    @Test
    fun `the park comes before the timed wait, so the timer only runs when visible`() {
        val parkAt = source.indexOf("if (!onScreen) {")
        // One timed wait, of the check interval or less, when a try for the phone's position falls due before it
        // (`SolarDaySchedule.nextPassDelayMillis`) or a quick retry of a showing does (`LiveWeatherSchedule.passDelayMillis`).
        val timedAt = TIMED_WAIT.find(source)?.range?.first ?: -1
        assertTrue("the visibility park is missing", parkAt > 0)
        assertTrue("the timed wait is missing", timedAt > 0)
        assertTrue(
            "the two-minute wait must sit after the park, or an invisible engine still ticks",
            parkAt < timedAt,
        )
    }

    @Test
    fun `on screen is shown with a surface, and a surface coming or going wakes the loop`() {
        assertTrue(source.contains("private val onScreen: Boolean get() = GlLifecyclePolicy.countsAsVisible(visible, hasSurface)"))
        for (callback in listOf("override fun onSurfaceCreated(", "override fun onSurfaceDestroyed(")) {
            val start = source.indexOf(callback)
            require(start > 0) { "$callback is gone" }
            val body = source.substring(start, source.indexOf("\n        }", start))
            assertTrue("$callback must keep the count and wake the loop", body.contains("weatherWakeUp.trySend(Unit)") && body.contains("changingOnScreen {"))
        }
    }

    @Test
    fun `visibility changes wake the loop`() {
        val start = source.indexOf("override fun onVisibilityChanged(")
        require(start > 0) { "onVisibilityChanged is gone" }
        val end = source.indexOf("\n        override fun ", start + 1).let {
            if (it < 0) source.length else it
        }
        val body = source.substring(start, end)
        assertTrue(
            "onVisibilityChanged must send on weatherWakeUp, or the parked loop never resumes:\n$body",
            body.contains("weatherWakeUp.trySend(Unit)"),
        )
    }

    @Test
    fun `there is still only one timed wait in the loop`() {
        // A second timer added elsewhere would reintroduce the polling this removes.
        assertEquals(
            "WEATHER_CHECK_INTERVAL_MS must be waited on in exactly one place",
            1,
            TIMED_WAIT.findAll(source).count(),
        )
        assertEquals("and nothing else in the service waits on a timer", 1, Regex("""withTimeoutOrNull\(""").findAll(source).count())
    }

    private companion object {
        /** The loop's one timed wait, as the service writes it, whatever the line breaks. */
        val TIMED_WAIT = Regex(
            """withTimeoutOrNull\(\s*LiveWeatherSchedule\.passDelayMillis\(\s*basePassMillis = SolarDaySchedule\.nextPassDelayMillis\(\s*""" +
                """checkIntervalMillis = WEATHER_CHECK_INTERVAL_MS,""",
        )
    }

    /**
     * **The pass is caught whole, its wait is not**: one `try` from the first line after the park to the line before the
     * timed wait, rethrowing the loop's own cancellation and logging every other exception, so a failing pass is followed
     * by the wait and the next pass. A `catch` of `Throwable`, a missing rethrow or a wait inside the `try` fails here.
     */
    @Test
    fun `every pass is caught and the loop goes on to its next one`() {
        val loopStart = source.indexOf("if (!onScreen) {")
        val tryAt = source.indexOf("\n                    try {\n", loopStart)
        val cancelAt = source.indexOf("} catch (cancelled: CancellationException) {", loopStart)
        val failureAt = source.indexOf("} catch (failure: Exception) {", loopStart)
        val timedAt = TIMED_WAIT.find(source)?.range?.first ?: -1
        assertTrue("the pass is not in a try", tryAt > loopStart)
        assertTrue("the cancellation is not let through first", cancelAt in tryAt..failureAt)
        assertTrue("the wait must follow the catch", failureAt in cancelAt..timedAt)
        val cancelled = source.substring(cancelAt, failureAt)
        assertTrue("the loop's cancellation must be thrown on: $cancelled", cancelled.contains("throw cancelled"))
        val caught = source.substring(failureAt, timedAt)
        assertTrue("the failure must be logged: $caught", caught.contains("Log.e(TAG, \"A weather pass failed; the loop goes on to its next pass\", failure)"))
        assertTrue("the pass's fetch is inside the try", source.indexOf("WeatherRepository.fetchCurrentConditions(", tryAt) in tryAt..cancelAt)
        assertTrue("the sun times are inside the try", source.indexOf("when (SolarDaySchedule.onTick(", tryAt) in tryAt..cancelAt)
        assertEquals("one try in the loop", 1, Regex("""catch \(cancelled: CancellationException\)""").findAll(source).count())
        assertTrue(source.contains("import kotlinx.coroutines.CancellationException"))
    }

    /**
     * **A coming on screen is counted where on screen changes**, and the loop hands the count to [ShowingRefresh] once a
     * pass, the request and the backoff's count going through it; the showing is a reason to ask the phone's position too.
     */
    @Test
    fun `a showing is counted where on screen changes, and the loop asks ShowingRefresh once a pass`() {
        val change = source.substring(source.indexOf("private inline fun changingOnScreen("), source.indexOf("private var showings = 0"))
        assertTrue(change.contains("if (onScreen && !before) {\n                showings++"))
        assertEquals(
            1,
            Regex("""val forShowing = showingRefresh\.asks\(SystemClock\.elapsedRealtime\(\), showings, WEATHER_REFRESH_INTERVAL_MS\)""")
                .findAll(source).count(),
        )
        assertTrue(source.contains("if (liveWeatherRuns && inputsUnchanged && fix != null && (movedSinceLastFetch || timerExpired || forShowing)) {"))
        assertTrue(source.contains("weatherTransientFailures = showingRefresh.answered("))
        assertTrue(source.contains("weatherAttemptDue = forShowing ||"))
        assertTrue(source.contains("showingRefresh.forget()"))
        assertTrue(source.contains("quickRetryInMillis = showingRefresh.quickRetryInMillis(SystemClock.elapsedRealtime()),"))
    }

    @Test
    fun `the channel is conflated, so a wake-up is never lost and never queues`() {
        assertTrue(
            "weatherWakeUp must stay CONFLATED: the park relies on a send that cannot be dropped",
            source.contains("private val weatherWakeUp = Channel<Unit>(Channel.CONFLATED)"),
        )
    }

    private fun serviceSource(): File {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/engine/PaperWallpaperService.kt"
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = File(dir, "$prefix$suffix")
                if (candidate.isFile) return candidate
            }
            dir = dir.parentFile
        }
        error("could not locate $suffix from ${File(".").absolutePath}")
    }
}
