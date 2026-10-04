package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **At a fixed hour the location does not count** (v5.10D, the maintainer's decision of 2026-09-30 on
 * row 6 of the v5.10A table, *«6 - voglio B»*, against the PM's recommendation): the scene draws the
 * default 6:00 and 20:00 and the wallpaper asks the phone for nothing, so the Location row's grey
 * "Available while the scene follows real time" is true.
 *
 * The rules are [SolarDaySchedule.sceneDay] and [SolarDaySchedule.onTick] (`SolarDayScheduleTest`); this
 * holds the engine to calling them, on its source -- the engine is a `WallpaperService` inner class the
 * JVM cannot build, the reasoning `WeatherLoopVisibilityTest` gives. The photograph (19:00 at Milan,
 * before and after) is in `consegna_v5_10d/foto/`.
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
        assertTrue(render.contains("val today = SolarDaySchedule.sceneDay(current.syncWithRealTime, solarDay)"))
        // ...from the same read of the settings as the hour, so one frame cannot mix the two.
        assertTrue(render.contains("val current = settings"))
        assertTrue(render.contains("current.fixedHour"))
        assertEquals("solarDay reaches the frame only through sceneDay", 1, Regex("""\bsolarDay\b""").findAll(render.substringBefore("renderer?.draw(")).count())
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
