package com.paperscrape.livewallpaper.location

import com.paperscrape.livewallpaper.engine.SolarDaySchedule
import com.paperscrape.livewallpaper.engine.WEATHER_CHECK_INTERVAL_MS
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **A search for the phone's position that finds nothing is tried again after 5, 15 and 30 minutes,
 * then the hour** (v5.10E). The maintainer, 2026-10-04: *«voglio che in ogni caso, se la richiesta
 * posizione fallisce, riprovi dopo 5, 15 e 30 minuti - anche se ha già una posizione»* and *«non deve
 * più sfociare nel chiedere ogni 20 secondi ma nemmeno può rimanere in immobilità per 60 minuti
 * sempre»*; the rule as the PM put it to him: tries at 5, 20 and 50 minutes from the first failure,
 * then the hour; a success back to the hour at once; every new failure, the hour's too, starts
 * 5-15-30 again; the user's choice asks at once; no permission and the location switched off are not
 * failures.
 *
 * [LocationRequestThrottle] on a clock this test moves, and [DeviceLocationProvider.plan], the
 * provider's decision without the phone. The loop that asks is `LocationRequestRhythmTest`'s.
 */
class LocationRetryLadderTest {

    private val minute = 60 * 1000L
    private val hour = 60 * minute

    private class Clock { var now = 0L }

    private fun throttle(clock: Clock) = LocationRequestThrottle({ clock.now })

    /** The earliest moment after [from] at which [t] lets a request through, minute by minute. */
    private fun nextAllowed(t: LocationRequestThrottle, clock: Clock, from: Long): Long {
        clock.now = from
        while (!t.retryDue() && clock.now < from + 2 * hour) clock.now += minute
        return clock.now
    }

    @Test
    fun `a search that finds nothing is tried again after 5, then 15, then 30 minutes, then the hour`() {
        val clock = Clock()
        val t = throttle(clock)
        assertTrue(t.tryAcquire())
        t.requestEnded(gotPosition = false)
        val waits = mutableListOf<Long>()
        var last = 0L
        repeat(4) {
            // Not a minute before it is due ...
            clock.now = last
            val due = nextAllowed(t, clock, last)
            clock.now = due - minute
            assertFalse("a try let through early, after ${waits.size} failures", t.tryAcquire())
            // ... and at the minute.
            clock.now = due
            assertTrue(t.tryAcquire())
            t.requestEnded(gotPosition = false)
            waits += (due - last) / minute
            last = due
        }
        assertEquals("5, 15, 30, then the hour", listOf(5L, 15L, 30L, 60L), waits)
        assertEquals(listOf(5L, 15L, 30L, 60L), LocationRequestThrottle.RETRY_LADDER_MILLIS.map { it / minute })
    }

    @Test
    fun `the hour's try failing starts 5, 15, 30 again`() {
        val clock = Clock()
        val t = throttle(clock)
        val times = mutableListOf<Long>()
        while (times.size < 9) {
            if (t.tryAcquire()) {
                times += clock.now / minute
                t.requestEnded(gotPosition = false)
            }
            clock.now += minute
        }
        assertEquals(listOf(0L, 5, 20, 50, 110, 115, 130, 160, 220), times)
    }

    @Test
    fun `a search that brings a position puts it back on the hour at once`() {
        val clock = Clock()
        val t = throttle(clock)
        assertTrue(t.tryAcquire())
        t.requestEnded(gotPosition = false)
        clock.now = 5 * minute
        assertTrue(t.tryAcquire())
        t.requestEnded(gotPosition = true)
        assertFalse("no try waits after a success", t.retryDue())
        clock.now = 5 * minute + 59 * minute
        assertFalse("and the next request is an hour after the good one", t.tryAcquire())
        clock.now = 5 * minute + hour
        assertTrue(t.tryAcquire())
        // And a failure after it starts at 5 again, not at the rung the old failures had reached.
        t.requestEnded(gotPosition = false)
        assertEquals(5 * minute, nextAllowed(t, clock, clock.now) - (5 * minute + hour))
    }

    @Test
    fun `a fresh position the system already has stops the tries, and the hour still counts from the request`() {
        val clock = Clock()
        val t = throttle(clock)
        assertTrue(t.tryAcquire())
        t.requestEnded(gotPosition = false)
        clock.now = 2 * minute
        // Another app had a position a moment ago: the plan answers from it.
        assertEquals(
            DeviceLocationProvider.FixStep.FRESH_CACHE,
            DeviceLocationProvider.plan(true, cachedAgeMillis = minute, maxAgeMillis = DeviceLocationProvider.FRESH_ENOUGH_MS, providerOn = true, throttle = t, force = false),
        )
        clock.now = 10 * minute
        assertFalse("no try after a position arrived", t.retryDue())
        assertFalse("and no request inside the hour of the last one", t.tryAcquire())
        clock.now = hour
        assertTrue(t.tryAcquire())
    }

    @Test
    fun `the user's choice is asked at once, and starts the tries afresh`() {
        val clock = Clock()
        val t = throttle(clock)
        // Three failures in: the next try would be 30 minutes after the last.
        for (at in listOf(0L, 5, 20)) {
            clock.now = at * minute
            assertTrue(t.tryAcquire())
            t.requestEnded(gotPosition = false)
        }
        clock.now = 21 * minute
        assertFalse(t.tryAcquire())
        assertTrue("GPS just picked: asked at once", t.tryAcquire(force = true))
        t.requestEnded(gotPosition = false)
        assertEquals("and the next try is 5 minutes after it", 26 * minute, nextAllowed(t, clock, clock.now))
    }

    @Test
    fun `no permission is not a try - nothing is counted and the wait stays where it was`() {
        val clock = Clock()
        val t = throttle(clock)
        assertTrue(t.tryAcquire())
        t.requestEnded(gotPosition = false)
        // Pass after pass without the permission, for an hour: the phone is never asked ...
        while (clock.now < hour) {
            assertEquals(
                DeviceLocationProvider.FixStep.NOT_PERMITTED,
                DeviceLocationProvider.plan(false, cachedAgeMillis = null, maxAgeMillis = 0L, providerOn = true, throttle = t, force = true),
            )
            clock.now += WEATHER_CHECK_INTERVAL_MS
        }
        // ... and the throttle saw none of it: still the first failure's try, due since 5 minutes.
        assertTrue(t.retryDue())
        assertTrue(t.tryAcquire())
        t.requestEnded(gotPosition = false)
        assertEquals("the second rung, 15 minutes: the passes without permission were not failures", clock.now + 15 * minute, nextAllowed(t, clock, clock.now))
        // And the engine's own rule does not ask either (SolarDaySchedule.onTick).
        assertEquals(
            SolarDaySchedule.Action.NOTHING,
            SolarDaySchedule.onTick(
                followRealTime = true, liveWeatherEnabled = true, deviceSource = true, devicePositionUsable = false,
                weatherAttemptDue = true, hasPosition = false, holdsDevicePosition = false, dayIsStale = false,
                deviceRetryDue = true,
            ),
        )
    }

    @Test
    fun `the phone's location off is not a try`() {
        val clock = Clock()
        val t = throttle(clock)
        assertTrue(t.tryAcquire())
        t.requestEnded(gotPosition = false)
        clock.now = 5 * minute
        // The try is due, and the location is off: not asked, not counted, still due.
        assertEquals(
            DeviceLocationProvider.FixStep.PROVIDER_OFF,
            DeviceLocationProvider.plan(true, cachedAgeMillis = null, maxAgeMillis = DeviceLocationProvider.FRESH_ENOUGH_MS, providerOn = false, throttle = t, force = false),
        )
        assertTrue("still due", t.retryDue())
        // Switched back on: the try goes, and it is still the first rung's.
        assertEquals(
            DeviceLocationProvider.FixStep.REQUEST,
            DeviceLocationProvider.plan(true, cachedAgeMillis = null, maxAgeMillis = DeviceLocationProvider.FRESH_ENOUGH_MS, providerOn = true, throttle = t, force = false),
        )
        t.requestEnded(gotPosition = false)
        assertEquals(clock.now + 15 * minute, nextAllowed(t, clock, clock.now))
    }

    @Test
    fun `the plan asks the permission, then the system's position, then the location, then the throttle`() {
        val clock = Clock()
        val t = throttle(clock)
        val fresh = DeviceLocationProvider.FRESH_ENOUGH_MS
        fun plan(permitted: Boolean, age: Long?, on: Boolean) = DeviceLocationProvider.plan(permitted, age, fresh, on, t, force = false)
        assertEquals(DeviceLocationProvider.FixStep.NOT_PERMITTED, plan(false, minute, true))
        assertEquals(DeviceLocationProvider.FixStep.FRESH_CACHE, plan(true, minute, false))
        assertEquals(DeviceLocationProvider.FixStep.PROVIDER_OFF, plan(true, fresh + 1, false))
        assertEquals(DeviceLocationProvider.FixStep.REQUEST, plan(true, null, true))
        t.requestEnded(gotPosition = true)
        assertEquals("inside the hour of a good request", DeviceLocationProvider.FixStep.WAIT, plan(true, fresh + 1, true))
    }

    /**
     * **A search cancelled part-way is counted** (the read-only review of v5.10E, D3): an engine destroyed
     * during the 20 s search spent the request and brought nothing, so the next try is 5 minutes away,
     * not the hour a request never reported would leave.
     */
    @Test
    fun `a search cancelled part-way is a failure, and the next try is 5 minutes away`() = runBlocking {
        val clock = Clock()
        val t = throttle(clock)
        assertTrue(t.tryAcquire())
        val search = launch { t.counted<Any> { delay(Long.MAX_VALUE); null } }
        yield()
        search.cancel()
        search.join()
        clock.now = 5 * minute
        assertTrue("the cancelled search counted as a miss", t.retryDue())
        // And a search that answers is counted as a success.
        assertTrue(t.tryAcquire())
        assertEquals("here", t.counted { "here" })
        assertFalse(t.retryDue())
    }

    @Test
    fun `a try is due only after a failure, and only once its time has come`() {
        val clock = Clock()
        val t = throttle(clock)
        assertFalse("nothing asked yet", t.retryDue())
        assertTrue(t.tryAcquire())
        t.requestEnded(gotPosition = true)
        clock.now = 3 * hour
        assertFalse("a success leaves nothing to try, however long ago", t.retryDue())
        assertTrue(t.tryAcquire())
        t.requestEnded(gotPosition = false)
        clock.now += 4 * minute
        assertFalse(t.retryDue())
        clock.now += minute
        assertTrue(t.retryDue())
    }

    @Test
    fun `the loop's next pass comes when a try falls due, and never at once`() {
        val clock = Clock()
        val t = throttle(clock)
        val check = WEATHER_CHECK_INTERVAL_MS
        assertEquals("nothing waiting: the usual two minutes", check, t.passDelay(check))
        assertTrue(t.tryAcquire())
        t.requestEnded(gotPosition = false)
        clock.now = 4 * minute
        assertEquals("the try at 5 minutes: one minute away", minute, t.passDelay(check))
        clock.now = 5 * minute
        assertEquals("due and not taken (the location off): the usual wait, not a spin", check, t.passDelay(check))
        clock.now = 0
        assertEquals("more than two minutes away: the usual wait", check, t.passDelay(check))
        // The engine shortens its wait only for a phone source (SolarDaySchedule.nextPassDelayMillis).
        clock.now = 4 * minute
        assertEquals(minute, SolarDaySchedule.nextPassDelayMillis(check, deviceSource = true, retryPassDelayMillis = t.passDelay(check)))
        assertEquals(check, SolarDaySchedule.nextPassDelayMillis(check, deviceSource = false, retryPassDelayMillis = t.passDelay(check)))
    }

    /**
     * **The words on the screen say what the rule does** (the prompt: the lines that promised "at most
     * once an hour" had to be rewritten to be true, in plain words). GPS, Network and "No position from
     * your phone yet" each name the 5, 15 and 30 minutes; none says "at most once an hour" any more.
     */
    @Test
    fun `the Location lines say 5, 15 and 30 minutes and no longer promise at most once an hour`() {
        val screen = source("ui/WeatherTimeScreen.kt")
        assertFalse("the v5.10D promise is gone", screen.contains("Asked for at most once an hour"))
        assertFalse(screen.contains("asks your phone again at most once an hour"))
        val gps = screen.substringAfter("locationMode == LocationMode.GPS ->").substringBefore("locationMode == LocationMode.NETWORK ->")
        val network = screen.substringAfter("locationMode == LocationMode.NETWORK ->").substringBefore("locationMode == LocationMode.CUSTOM ->")
        val noPosition = screen.substringAfter("title = \"No position from your phone yet\",").substringBefore("icon =")
        for ((name, line) in listOf("GPS" to gps, "Network" to network, "no position yet" to noPosition)) {
            assertTrue("$name does not name the tries", line.contains("5, 15 and 30 minutes"))
        }
        assertTrue(gps.contains("once an hour", ignoreCase = true) && network.contains("once an hour", ignoreCase = true))
        // The words as the screen shows them: the string literals joined, whatever the line breaks.
        val shown = Regex(""""((?:[^"\\]|\\.)*)"""").findAll(noPosition).joinToString("") { it.groupValues[1] }
        assertTrue("and the hour after the tries ($shown)", shown.contains("then after an hour"))
    }

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
