package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.engine.LiveWeatherSceneRules
import com.paperscrape.livewallpaper.weather.LiveWeatherStatus
import com.paperscrape.livewallpaper.weather.WeatherProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The weather sentences of row 11 of the v5.10A table (v5.10D, the maintainer's *sì* of 2026-09-30):
 * *Show Clouds* and *Show Rain/Snow* do not say "off" while the forecast's clouds or rain are on screen
 * (inventory I-220), and "Required - not set" is said only of the provider chosen (I-221).
 */
class ForecastWeatherControlsTest {

    private fun weather(status: LiveWeatherStatus, enabled: Boolean = true) =
        SettingsUiModel.liveWeather(enabled, true, LocationMode.CUSTOM, true, false, true, status)

    // ------------------------------------------------------------------ I-220

    @Test
    fun `while the forecast drives the sky the theme's controls give way, and come back when it stops`() {
        assertTrue(SettingsUiModel.forecastOwnsTheWeatherControls(weather(LiveWeatherStatus.OK)))
        assertTrue("the last forecast still showing", SettingsUiModel.forecastOwnsTheWeatherControls(weather(LiveWeatherStatus.STALE)))
        for (status in LiveWeatherStatus.entries.filterNot { it.isDrivingTheScene }) {
            assertFalse("$status: the theme's weather shows, so its controls are there", SettingsUiModel.forecastOwnsTheWeatherControls(weather(status)))
        }
        for (status in LiveWeatherStatus.entries) {
            assertFalse("Live Weather off", SettingsUiModel.forecastOwnsTheWeatherControls(weather(status, enabled = false)))
        }
    }

    @Test
    fun `the theme's switch has no vote under a forecast, which is why it may not be drawn there`() {
        // The defect, as numbers: Show Clouds stored off, the forecast says 70 % cover -- the scene
        // draws the forecast's clouds (`LiveWeatherSceneRules.cloudDensity`), and the greyed switch
        // said off.
        assertEquals(0.7f, LiveWeatherSceneRules.cloudDensity(liveCloudCover = 0.7f, themeCloudsVisible = false, themeCloudDensity = 0.3f))
        // And Show Rain/Snow off under a live storm: the lightning comes from the forecast.
        assertTrue(LiveWeatherSceneRules.stormActive(liveIsThunderstorm = true, themePrecipitationVisible = false, themePrecipitationIsRain = false, themeThunderstorm = false))
    }

    @Test
    fun `the Clouds and Rain and snow pages draw no theme control while the forecast drives`() {
        val world = source("ui/WorldSceneScreen.kt")
        assertTrue(world.contains("val liveWeatherDriving = SettingsUiModel.forecastOwnsTheWeatherControls("))
        val clouds = block(world, "private fun CloudsSubScreen(")
        val cloudsElse = afterTheForecastBranch(clouds)
        assertTrue("Show Clouds only in the theme's branch", cloudsElse.contains("title = \"Show Clouds\""))
        assertTrue("its amount too", cloudsElse.contains("# of Clouds"))
        assertEquals("one Show Clouds on the page", 1, Regex("title = \"Show Clouds\"").findAll(clouds).count())
        assertFalse("no greyed copy left", clouds.contains("enabled = !liveWeatherDriving"))

        val rain = block(world, "private fun PrecipitationSubScreen(")
        val rainElse = afterTheForecastBranch(rain)
        for (control in listOf("title = \"Show Rain/Snow\"", "options = listOf(\"Rain\", \"Snow\")", "Intensity: ")) {
            assertTrue("$control only in the theme's branch", rainElse.contains(control))
        }
        assertTrue("Thunderstorm only without a forecast", rain.contains("if (!liveWeatherDriving) {\n            SectionTitle(\"Storms\")"))
        assertFalse(rain.contains("enabled = !liveWeatherDriving"))
        // The colours stay, forecast or not.
        assertTrue(rain.contains("SectionTitle(\"Rain Colors\")") && clouds.contains("DayNightColorPair("))
    }

    @Test
    fun `the sentences no longer promise read-only screens`() {
        val strings = repoFile("app/src/main/res/values/strings.xml").readText()
        assertFalse(strings.contains("screens switch to read-only"))
        assertFalse(source("ui/WeatherTimeScreen.kt").contains("screens are read-only"))
    }

    // ------------------------------------------------------------------ I-221

    @Test
    fun `Required is said only of the provider chosen`() {
        for (keyed in listOf(WeatherProviderId.WEATHER_API_COM, WeatherProviderId.OPEN_WEATHER)) {
            assertEquals("Required - not set", SettingsUiModel.apiKeyLine(keyed, selected = keyed, keySet = false))
            for (other in WeatherProviderId.entries - keyed) {
                val line = SettingsUiModel.apiKeyLine(keyed, selected = other, keySet = false)
                assertFalse("$keyed with $other chosen says \"$line\"", line.startsWith("Required"))
                assertTrue(line.contains("only if you choose ${keyed.displayName}"))
            }
            assertEquals("Set", SettingsUiModel.apiKeyLine(keyed, selected = keyed, keySet = true))
        }
    }

    @Test
    fun `Open-Meteo's row says it is in use only when it is`() {
        assertTrue(SettingsUiModel.apiKeyLine(WeatherProviderId.OPEN_METEO, WeatherProviderId.OPEN_METEO, false).contains("using Open-Meteo's free service"))
        for (other in listOf(WeatherProviderId.WEATHER_API_COM, WeatherProviderId.OPEN_WEATHER)) {
            for (set in listOf(true, false)) {
                assertFalse(SettingsUiModel.apiKeyLine(WeatherProviderId.OPEN_METEO, other, set).startsWith("Using"))
                assertFalse(SettingsUiModel.apiKeyLine(WeatherProviderId.OPEN_METEO, other, set).contains("using Open-Meteo's"))
            }
        }
    }

    @Test
    fun `the three key rows read the rule, and a keyed provider with no key still holds Live Weather off`() {
        val page = source("ui/WeatherTimeScreen.kt")
        assertEquals(3, Regex("""SettingsUiModel\.apiKeyLine\(WeatherProviderId\.""").findAll(page).count())
        assertFalse(page.contains("\"Required - not set\""))
        // The other half of row 11's provider line, repaired by v5.10C: a keyed provider without its key.
        val state = SettingsUiModel.liveWeather(true, true, LocationMode.CUSTOM, true, keyMissing = true, isTheWallpaper = true, status = LiveWeatherStatus.OFF)
        assertFalse(state.shownOn)
        assertEquals(LiveWeatherBlocker.MISSING_KEY, state.blocker)
    }

    /** What follows the `else` of the page's `if (liveWeatherDriving) {`: the theme's own branch. */
    private fun afterTheForecastBranch(page: String): String {
        val forecast = page.indexOf("        if (liveWeatherDriving) {")
        require(forecast >= 0) { "the forecast branch is gone" }
        val otherwise = page.indexOf("        } else {", forecast)
        require(otherwise >= 0) { "the theme's branch is gone" }
        return page.substring(otherwise)
    }

    private fun block(text: String, start: String): String {
        val at = text.indexOf(start)
        require(at >= 0) { "$start is gone" }
        val next = text.indexOf("\n@Composable", at + 1).let { if (it < 0) text.length else it }
        return text.substring(at, next)
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

    private fun source(path: String): String = repoFile("app/src/main/kotlin/com/paperscrape/livewallpaper/$path").readText()
}
