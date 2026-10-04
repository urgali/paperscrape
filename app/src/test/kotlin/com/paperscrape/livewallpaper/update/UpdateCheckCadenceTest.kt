package com.paperscrape.livewallpaper.update

import com.paperscrape.livewallpaper.engine.WEATHER_CHECK_INTERVAL_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **The update check every three hours** (v5.10D, the maintainer's decision of 2026-09-30 on row 3 of
 * the v5.10A table, *«3 - voglio B»*, replacing his choice of 2026-09-23: once a day), asserted over a
 * day of the engine's passes on a clock this test moves, rather than read off the constant.
 *
 * The engine's loop wakes every [WEATHER_CHECK_INTERVAL_MS] while the wallpaper is on screen and asks
 * [UpdateNotificationPolicy.checkDue]; that it asks exactly that, with the monotonic clock and the
 * failure count, is held on its source at the end.
 */
class UpdateCheckCadenceTest {

    private val hour = 60 * 60 * 1000L

    /**
     * The loop's passes from a fresh engine, every two minutes for [hours] hours; [unreachable] says
     * whether a check made at a given moment fails. Returns the moments checks were made.
     */
    private fun checksOver(hours: Int, unreachable: (Long) -> Boolean = { false }): List<Long> {
        var last = Long.MIN_VALUE / 4 // the engine's sentinel: a fresh engine checks at once
        var failures = 0
        val made = mutableListOf<Long>()
        var now = 0L
        while (now < hours * hour) {
            if (UpdateNotificationPolicy.checkDue(now - last, failures)) {
                last = now
                made += now
                failures = if (unreachable(now)) failures + 1 else 0
            }
            now += WEATHER_CHECK_INTERVAL_MS
        }
        return made
    }

    @Test
    fun `a wallpaper on screen all day checks eight times, three hours apart`() {
        val made = checksOver(24)
        assertEquals("eight a day instead of one", 8, made.size)
        assertEquals("the first at once", 0L, made.first())
        assertEquals((0 until 8).map { it * 3 * hour }, made)
    }

    @Test
    fun `before v5_10D the same day held one check`() {
        // The rule the maintainer replaced, run through the same loop: the ladder capped at a day.
        var last = Long.MIN_VALUE / 4
        var count = 0
        var now = 0L
        while (now < 24 * hour) {
            val due = com.paperscrape.livewallpaper.weather.LiveWeatherSchedule.isAttemptDue(
                now - last,
                com.paperscrape.livewallpaper.weather.LiveWeatherSchedule.nextAttemptDelayMillis(0, 24 * hour),
            )
            if (due) { last = now; count++ }
            now += WEATHER_CHECK_INTERVAL_MS
        }
        assertEquals(1, count)
    }

    @Test
    fun `offline, the retries climb from two minutes and settle at three hours`() {
        val made = checksOver(12) { true }
        val gaps = made.zipWithNext { a, b -> (b - a) / 60_000L }
        assertEquals("2, 4, 8, 16, 32, 64, 128 minutes, then the three-hour cap", listOf(2L, 4, 8, 16, 32, 64, 128, 180, 180), gaps.take(9))
        assertTrue("never more often than the loop's tick", gaps.all { it >= WEATHER_CHECK_INTERVAL_MS / 60_000L })
    }

    @Test
    fun `one success puts it back on three hours`() {
        // Offline for the first half hour, then GitHub answers.
        val made = checksOver(12) { it < hour / 2 }
        val afterRecovery = made.dropWhile { it < hour / 2 }
        assertTrue(afterRecovery.zipWithNext { a, b -> b - a }.all { it == 3 * hour })
    }

    @Test
    fun `the engine asks the rule, on the monotonic clock, with its failure count`() {
        val engine = repoFile("app/src/main/kotlin/com/paperscrape/livewallpaper/engine/PaperWallpaperService.kt").readText()
        assertTrue(
            engine.contains(
                "if (!UpdateNotificationPolicy.checkDue(SystemClock.elapsedRealtime() - lastUpdateCheckElapsed, updateCheckTransientFailures)) return",
            ),
        )
        assertTrue("the engine has no schedule of its own", !engine.contains("UPDATE_CHECK_INTERVAL_MILLIS,"))
    }

    private fun repoFile(path: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, path)
            if (f.isFile) return f
            dir = dir.parentFile
        }
        error("not found: $path")
    }
}
