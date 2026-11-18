package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.engine.LiveWeatherSceneRules
import com.paperscrape.livewallpaper.weather.LiveWeatherStatus
import com.paperscrape.livewallpaper.weather.WeatherProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The weather pages under Live Weather (`DESIGN_NOTES.md` §12, decision 108, the maintainer's *«A (consigliata)»* of
 * 2026-11-15): **as soon as the *Live Weather* switch reads on**, the Clouds and the Rain and snow pages leave out the
 * theme's controls -- *Show Clouds* and *Show Rain/Snow* never say "off" under the forecast's clouds or rain -- and say
 * what the scene draws at that moment: the forecast, or the theme's own weather while the first forecast is awaited or none
 * is in effect; with the switch reading off (off, or a blocker) the controls are back; the colours always stay. And
 * "Required - not set" is said only of the provider chosen.
 */
class ForecastWeatherControlsTest {

    private fun weather(status: LiveWeatherStatus, enabled: Boolean = true) =
        SettingsUiModel.liveWeather(enabled, true, LocationMode.CUSTOM, true, false, true, status)

    // ------------------------------------------------------------------ the weather pages

    @Test
    fun `as soon as Live Weather reads on the theme's controls give way, and come back when it reads off`() {
        for (status in LiveWeatherStatus.entries) {
            val state = weather(status)
            assertEquals("$status: hidden exactly while the switch reads on", state.shownOn, SettingsUiModel.forecastOwnsTheWeatherControls(state))
            assertFalse("$status, Live Weather off", SettingsUiModel.forecastOwnsTheWeatherControls(weather(status, enabled = false)))
            assertNull("$status, Live Weather off: the controls", SettingsUiModel.weatherPages(weather(status, enabled = false), status))
        }
        // Waiting, a service down, a limit, a refusal: the switch reads on, so they are hidden though the theme's weather shows.
        for (status in listOf(LiveWeatherStatus.OFF, LiveWeatherStatus.FAILED, LiveWeatherStatus.OK, LiveWeatherStatus.STALE)) {
            assertTrue("$status", SettingsUiModel.forecastOwnsTheWeatherControls(weather(status)))
        }
        // A blocker reads the switch off -- a refused key, no position -- and the controls are back.
        for (status in listOf(LiveWeatherStatus.REJECTED_API_KEY, LiveWeatherStatus.NO_LOCATION)) {
            assertFalse("$status", weather(status).shownOn)
            assertFalse("$status", SettingsUiModel.forecastOwnsTheWeatherControls(weather(status)))
            assertNull("$status", SettingsUiModel.weatherPages(weather(status), status))
        }
        // A key missing, another wallpaper, a fixed hour: the same.
        assertFalse(SettingsUiModel.forecastOwnsTheWeatherControls(SettingsUiModel.liveWeather(true, true, LocationMode.CUSTOM, true, true, true, LiveWeatherStatus.OFF)))
        assertFalse(SettingsUiModel.forecastOwnsTheWeatherControls(SettingsUiModel.liveWeather(true, true, LocationMode.CUSTOM, true, false, false, LiveWeatherStatus.OK)))
        assertFalse(SettingsUiModel.forecastOwnsTheWeatherControls(SettingsUiModel.liveWeather(true, false, LocationMode.CUSTOM, true, false, true, LiveWeatherStatus.OK)))
    }

    /** Each page's line and each row say what the scene draws in that state (decision 40), word for word. */
    @Test
    fun `the pages and the rows say what the scene draws, in every state`() {
        val end = "Turn Live Weather off in Weather & time to set them here."
        for (status in LiveWeatherStatus.entries) {
            val state = weather(status)
            val pages = SettingsUiModel.weatherPages(state, status)
            // A line exactly while the switch reads on: the screen draws the line or the theme's controls by it.
            assertEquals("$status: a line while the switch reads on, the controls while it reads off", state.shownOn, pages != null)
            if (pages == null) continue
            when {
                state.drivingTheScene -> {
                    assertEquals("Driven by Live Weather", pages.rowSummary)
                    assertEquals("Live Weather is drawing the clouds from the real sky right now: the forecast decides whether there are any and how many. $end", pages.cloudsLine)
                    assertEquals("Live Weather is drawing rain, snow and thunderstorms from the real sky right now: the forecast decides whether anything falls, which, and how hard. $end", pages.rainLine)
                }
                status == LiveWeatherStatus.OFF -> {
                    assertEquals("Live Weather on - waiting for the first forecast", pages.rowSummary)
                    assertEquals("Live Weather is on and waiting for its first forecast. Until it arrives the scene shows this theme's own clouds; then the forecast decides whether there are any and how many. $end", pages.cloudsLine)
                    assertEquals("Live Weather is on and waiting for its first forecast. Until it arrives the scene shows this theme's own rain and snow; then the forecast decides whether anything falls, which, and how hard. $end", pages.rainLine)
                }
                else -> {
                    assertEquals("$status", "Live Weather on - no forecast right now", pages.rowSummary)
                    assertEquals("Live Weather is on, but no forecast is in effect right now - Weather & time says why - so the scene shows this theme's own clouds until one arrives. $end", pages.cloudsLine)
                    assertEquals("Live Weather is on, but no forecast is in effect right now - Weather & time says why - so the scene shows this theme's own rain and snow until one arrives. $end", pages.rainLine)
                }
            }
        }
        assertEquals("Driven by Live Weather", SettingsUiModel.weatherPages(weather(LiveWeatherStatus.STALE), LiveWeatherStatus.STALE)?.rowSummary)
        assertEquals("Live Weather on - no forecast right now", SettingsUiModel.weatherPages(weather(LiveWeatherStatus.FAILED), LiveWeatherStatus.FAILED)?.rowSummary)
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
        // Whole lines, the comments taken off: a line commented out does not count.
        val worldLines = world.lines().map { it.substringBefore("//").trim() }
        assertTrue(worldLines.any { it.startsWith("val weatherPages = SettingsUiModel.weatherPages(") })
        val clouds = block(world, "private fun CloudsSubScreen(")
        val cloudsElse = afterTheForecastBranch(clouds)
        assertTrue("Show Clouds only in the theme's branch", cloudsElse.contains("title = \"Show Clouds\""))
        assertTrue("its amount too", cloudsElse.contains("# of Clouds"))
        assertEquals("one Show Clouds on the page", 1, Regex("title = \"Show Clouds\"").findAll(clouds).count())
        assertFalse("no greyed copy left", clouds.contains("enabled = weatherPages"))
        assertTrue("the line in place of the controls", clouds.contains("weatherPages.cloudsLine,"))

        val rain = block(world, "private fun PrecipitationSubScreen(")
        val rainElse = afterTheForecastBranch(rain)
        for (control in listOf("title = \"Show Rain/Snow\"", "options = listOf(\"Rain\", \"Snow\")", "Intensity: ")) {
            assertTrue("$control only in the theme's branch", rainElse.contains(control))
        }
        assertTrue("Thunderstorm only without a forecast", rain.contains("if (weatherPages == null) {\n            SectionTitle(\"Storms\")"))
        assertFalse(rain.contains("enabled = weatherPages"))
        assertTrue("the line in place of the controls", rain.contains("weatherPages.rainLine,"))
        // The colours stay, forecast or not.
        assertTrue(rain.contains("SectionTitle(\"Rain Colors\")") && clouds.contains("DayNightColorPair("))
    }

    @Test
    fun `the sentences no longer promise read-only screens`() {
        val strings = repoFile("app/src/main/res/values/strings.xml").readText()
        assertFalse(strings.contains("screens switch to read-only"))
        assertFalse(source("ui/WeatherTimeScreen.kt").contains("screens are read-only"))
    }

    // ------------------------------------------------------------------ the key lines

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
        // The other half of the provider line: a keyed provider without its key.
        val state = SettingsUiModel.liveWeather(true, true, LocationMode.CUSTOM, true, keyMissing = true, isTheWallpaper = true, status = LiveWeatherStatus.OFF)
        assertFalse(state.shownOn)
        assertEquals(LiveWeatherBlocker.MISSING_KEY, state.blocker)
    }

    /** What follows the `else` of the page's `if (weatherPages != null) {`: the theme's own branch. */
    private fun afterTheForecastBranch(page: String): String {
        val forecast = page.indexOf("        if (weatherPages != null) {")
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
