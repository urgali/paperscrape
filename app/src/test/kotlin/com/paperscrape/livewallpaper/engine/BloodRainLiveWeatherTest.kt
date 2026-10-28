package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.weather.LiveWeatherSnapshot
import com.paperscrape.livewallpaper.weather.OpenMeteoProvider
import com.paperscrape.livewallpaper.weather.WeatherSnapshotMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **The blood rain under Live Weather** (v5.17; the maintainer's question of 2026-10-23, *«la pioggia di sangue funziona
 * anche con live weather?»*, answered then from the code -- yes by the code, not yet proved --, and promised a test
 * and a photograph): it rains from a forecast -- Open-Meteo's answer as the app parses it, made a snapshot by the app's own
 * mapper, the snapshot the wallpaper hands its renderer -- on the Halloween theme as it ships, whose own rain is off, and the
 * rain is the blood rain: red, by night and by day, and twice as thick; the forecast's snow stays the theme's snow, as thin
 * as ever; with *Blood rain* off the forecast's rain is the theme's colours. The renderer draws precipitation by this one
 * rule ([PrecipitationLook]), the forecast's when Live Weather drives the scene.
 */
class BloodRainLiveWeatherTest {

    /** Open-Meteo's `current` block as the app asks for it: [rain] and [snowfall] measured, [code] the WMO summary. */
    private fun forecast(rain: Double, snowfall: Double, code: Int): LiveWeatherSnapshot {
        val json = """{"current":{"temperature_2m":9.5,"cloud_cover":100,"precipitation":${rain + snowfall},"rain":$rain,""" +
            """"showers":0.0,"snowfall":$snowfall,"weather_code":$code}}"""
        val observation = checkNotNull(OpenMeteoProvider.parse(json)) { json }
        return WeatherSnapshotMapper.toSnapshot(observation)
    }

    private val halloween = defaultCustomizationFor("halloween").let { it.copy(precipitation = it.precipitation.copy(visible = false, intensity = 0f)) }

    private fun red(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return r > g + 100 && r > b + 90
    }

    @Test
    fun `it rains blood from the forecast, red by night and by day and twice as thick`() {
        assertTrue("the theme's own rain is off: what falls is the forecast's", !halloween.precipitation.visible)
        assertEquals("without the forecast nothing falls", PrecipitationLook.NONE, PrecipitationLook.kind(halloween.precipitation, null))
        assertTrue("as the Halloween theme ships", halloween.bloodRainEnabled)
        for ((mm, code) in listOf(0.4 to 61, 3.0 to 63, 9.0 to 65, 2.0 to 80)) {
            val live = forecast(rain = mm, snowfall = 0.0, code = code)
            assertEquals("$mm mm", com.paperscrape.livewallpaper.engine.PrecipitationType.RAIN, live.precipitationType)
            val kind = PrecipitationLook.kind(halloween.precipitation, live)
            assertEquals("$mm mm: it rains", PrecipitationLook.RAIN, kind)
            assertEquals("$mm mm: as much as the forecast says", live.precipitationIntensity, PrecipitationLook.intensity(halloween.precipitation, live), 0f)
            assertTrue("$mm mm: blood", PrecipitationLook.blood(kind, halloween))
            for (dayBlend in listOf(0f, 0.3f, 0.7f, 1f)) {
                val colour = PrecipitationLook.colour(kind, true, halloween.precipitation, dayBlend)
                assertTrue("$mm mm at $dayBlend: red, #%06X".format(colour and 0xFFFFFF), red(colour))
            }
            assertEquals("night", PaperRenderer.BLOOD_RAIN_NIGHT, PrecipitationLook.colour(kind, true, halloween.precipitation, 0f))
            assertEquals("day", PaperRenderer.BLOOD_RAIN_DAY, PrecipitationLook.colour(kind, true, halloween.precipitation, 1f))
            assertEquals("twice as thick", 2f, PrecipitationLook.strokeFactor(true), 0f)
        }
    }

    @Test
    fun `the forecast's snow stays snow, and with the switch off its rain is the theme's`() {
        val snow = forecast(rain = 0.0, snowfall = 1.5, code = 73)
        val kind = PrecipitationLook.kind(halloween.precipitation, snow)
        assertEquals(PrecipitationLook.SNOW, kind)
        assertTrue("no blood in the snow", !PrecipitationLook.blood(kind, halloween))
        for (dayBlend in listOf(0f, 1f)) {
            val colour = PrecipitationLook.colour(kind, false, halloween.precipitation, dayBlend)
            val theme = SceneColour.blendArgb(halloween.precipitation.snowColorNight, halloween.precipitation.snowColorDay, dayBlend)
            assertEquals("the theme's snow at $dayBlend", theme, colour)
            assertTrue("not red: #%06X".format(colour and 0xFFFFFF), !red(colour))
        }
        assertEquals("as thin as ever", 1f, PrecipitationLook.strokeFactor(false), 0f)
        val off = halloween.copy(bloodRainEnabled = false)
        val rain = forecast(rain = 3.0, snowfall = 0.0, code = 63)
        val k = PrecipitationLook.kind(off.precipitation, rain)
        assertTrue(!PrecipitationLook.blood(k, off))
        assertEquals("the theme's rain", SceneColour.blendArgb(off.precipitation.rainColorNight, off.precipitation.rainColorDay, 0.5f),
            PrecipitationLook.colour(k, false, off.precipitation, 0.5f))
        assertNotEquals(PrecipitationLook.colour(k, true, off.precipitation, 0.5f), PrecipitationLook.colour(k, false, off.precipitation, 0.5f))
        // A forecast with nothing falling, the theme's rain off: nothing falls; without Live Weather the theme's rain is blood too.
        assertEquals(PrecipitationLook.NONE, PrecipitationLook.kind(halloween.precipitation, forecast(0.0, 0.0, 3)))
        val themeRain = defaultCustomizationFor("halloween").let { it.copy(precipitation = it.precipitation.copy(visible = true, intensity = 0.6f, type = PrecipitationType.RAIN)) }
        val tk = PrecipitationLook.kind(themeRain.precipitation, null)
        assertEquals(PrecipitationLook.RAIN, tk)
        assertTrue(PrecipitationLook.blood(tk, themeRain))
        assertEquals(0.6f, PrecipitationLook.intensity(themeRain.precipitation, null), 0f)
    }

    /** The renderer draws by this rule, with the snapshot the wallpaper hands it from Live Weather. */
    @Test
    fun `the renderer rains what the rule says, from the snapshot Live Weather hands it`() {
        val renderer = File(sourceRoot(), "engine/PaperRenderer.kt").readLines().map { it.substringBefore("//").trim() }
        for (line in listOf(
            "val kind = PrecipitationLook.kind(precip, liveWeatherOverride)",
            "if (kind == PrecipitationLook.NONE) return",
            "val intensity = PrecipitationLook.intensity(precip, liveWeatherOverride)",
            "val blood = PrecipitationLook.blood(kind, sceneCustomization)",
            "val themeColour = PrecipitationLook.colour(kind, blood, precip, dayPhase.dayBlend)",
            "precipPaint.strokeWidth = RAIN_STROKE_WIDTH_METRES * metrePx * PrecipitationLook.strokeFactor(blood)",
        )) assertTrue("PaperRenderer: $line", line in renderer)
        val service = File(sourceRoot(), "engine/PaperWallpaperService.kt").readLines().map { it.trim() }
        assertTrue("the wallpaper hands its renderer Live Weather's snapshot", "renderer?.liveWeatherOverride = snapshot" in service)
    }

    private fun sourceRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = File(dir, "${prefix}src/main/kotlin/com/paperscrape/livewallpaper")
                if (f.isDirectory) return f
            }
            dir = dir.parentFile
        }
        throw AssertionError("source root not found")
    }
}
