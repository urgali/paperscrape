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
            """if \(!visible\) \{\s*\n\s*weatherWakeUp\.receive\(\)\s*\n\s*continue\s*\n\s*\}""",
        )
        assertTrue(
            "the Live Weather loop must park on weatherWakeUp while invisible, with no timeout",
            park.containsMatchIn(source),
        )
    }

    @Test
    fun `the park comes before the timed wait, so the timer only runs when visible`() {
        val parkAt = source.indexOf("if (!visible) {")
        val timedAt = source.indexOf("withTimeoutOrNull(WEATHER_CHECK_INTERVAL_MS)")
        assertTrue("the visibility park is missing", parkAt > 0)
        assertTrue("the timed wait is missing", timedAt > 0)
        assertTrue(
            "the two-minute wait must sit after the park, or an invisible engine still ticks",
            parkAt < timedAt,
        )
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
            Regex("""withTimeoutOrNull\(WEATHER_CHECK_INTERVAL_MS\)""").findAll(source).count(),
        )
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
