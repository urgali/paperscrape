package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **At a fixed hour the location does not count** (`DESIGN_NOTES.md` §12, decision 41, 2026-09-30:
 * *«6 - voglio B»*): the scene draws the default 6:00 and 20:00 and the wallpaper asks the phone for
 * nothing, so the Location row's grey "Available while the scene follows real time" is true.
 *
 * The rules are [SolarDaySchedule.sceneDay] and [SolarDaySchedule.onTick] (`SolarDayScheduleTest`); this
 * holds the engine to calling them, on its source -- the engine is a `WallpaperService` inner class the
 * JVM cannot build, the reasoning `WeatherLoopVisibilityTest` gives.
 */
class FixedHourIgnoresLocationTest {

    private val engine: String by lazy {
        var dir: File? = File(".").absoluteFile
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/engine/PaperWallpaperService.kt"
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = File(dir, "$prefix$suffix")
                if (f.isFile) return@lazy f.readText()
            }
            dir = dir.parentFile
        }
        error("not found")
    }

    @Test
    fun `the frame draws the scene day, never the held one directly`() {
        val render = engine.substring(engine.indexOf("private fun renderScene("))
        val frame = render.substringBefore("renderer?.draw(")
        assertTrue(render.contains("val today = SolarDaySchedule.sceneDay(current.syncWithRealTime, held.day)"))
        // ...from the same read of the settings as the hour, so one frame cannot mix the two.
        assertTrue(render.contains("val current = settings"))
        assertTrue(render.contains("current.fixedHour"))
        // ...and from one read of the sun times, the hour's offset with them (SolarDaySchedule.sceneHour).
        assertEquals("the sun times are read once a frame", 1, Regex("""\bsunTimes\b""").findAll(frame).count())
        assertEquals("the day reaches the frame only through sceneDay", 1, Regex("""\bheld\.day\b""").findAll(frame).count())
        assertEquals("never the held day directly", 0, Regex("""\bsolarDay\b""").findAll(frame).count())
    }

    @Test
    fun `a settings change at a fixed hour asks the phone for nothing`() {
        assertTrue(engine.contains("if (!solarDay.hasFix && newSettings.syncWithRealTime) launch { refreshDeviceFix(requestedSource) }"))
    }

    @Test
    fun `the loop tells the schedule whether the scene follows real time`() {
        assertTrue(engine.contains("followRealTime = settings.syncWithRealTime,"))
    }
}
