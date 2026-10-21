package com.paperscrape.livewallpaper.weather

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ARC-02: an engine that cannot be seen does not run the Live Weather loop.
 *
 * The loop was `while (true) { …; withTimeoutOrNull(2 min) { wakeUp.receive() } }` launched in
 * `onCreate` and never gated on visibility. It therefore woke the main thread every two minutes for
 * the whole life of the engine — screen off included, Live Weather off included — and a wallpaper
 * process can host two engines at once, the picker's preview beside the live one, so both the ticks
 * and the hourly fetch behind them doubled whenever the picker was open.
 *
 * The fix is to park on the channel with **no timeout** while invisible. That is what removes the
 * polling rather than lengthening it, and it also makes the loop *more* responsive: coming back
 * visible sends on the channel, the body re-enters at once, and a refresh that fell due while the
 * screen was off is picked up immediately instead of up to two minutes later.
 *
 * **On screen means shown with a surface since v5.15** (inventory I-918): the picker's preview of a stopped package,
 * bound later to a window nobody shows, was "visible" with nothing to draw on and fetched the weather for it. The loop
 * parks on `onScreen` -- [com.paperscrape.livewallpaper.engine.GlLifecyclePolicy.countsAsVisible] of the visibility and
 * the surface -- and a surface arriving or going wakes it, as visibility does.
 *
 * ### Why this reads the source
 *
 * What has to hold is a shape: a park with no timeout, reached before any timed wait, and a producer
 * on the other side that runs when visibility changes. The loop lives inside a `WallpaperService`
 * engine and cannot be instantiated on the JVM, and an instrumented test of "did nothing happen for
 * two minutes" is a two-minute test that proves very little. The same reasoning
 * `IndoorClothingTest` and `InternetInventoryTest` are built on.
 */
class WeatherLoopVisibilityTest {

    private val source: String by lazy { serviceSource().readText() }

    @Test
    fun `the loop parks with no timeout while the engine is invisible`() {
        val park = Regex(
            """if \(!onScreen\) \{\s*\n\s*weatherWakeUp\.receive\(\)\s*\n\s*continue\s*\n\s*\}""",
        )
        assertTrue(
            "the Live Weather loop must park on weatherWakeUp while invisible, with no timeout",
            park.containsMatchIn(source),
        )
    }

    @Test
    fun `the park comes before the timed wait, so the timer only runs when visible`() {
        val parkAt = source.indexOf("if (!onScreen) {")
        // One timed wait, of the check interval or -- since v5.10E -- less, when a try for the phone's
        // position falls due before it (`SolarDaySchedule.nextPassDelayMillis`).
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
        /** The loop's one timed wait, as the service writes it since v5.10E, whatever the line breaks. */
        val TIMED_WAIT = Regex("""withTimeoutOrNull\(\s*SolarDaySchedule\.nextPassDelayMillis\(\s*checkIntervalMillis = WEATHER_CHECK_INTERVAL_MS,""")
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
