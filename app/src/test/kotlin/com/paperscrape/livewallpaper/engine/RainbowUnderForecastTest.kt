package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.weather.LiveWeatherSnapshot
import com.paperscrape.livewallpaper.weather.OpenMeteoProvider
import com.paperscrape.livewallpaper.weather.WeatherSnapshotMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **No rainbow under the forecast's rain, snow or thunderstorm** (`DESIGN_NOTES.md` §12, decision 108, the maintainer's
 * «Sì a tutte» of 2026-11-15 to the row that proposed it): forecasts as Open-Meteo answers them and the app maps them, the
 * rule ([LiveWeatherSceneRules.rainbowUnderForecast]) on each, and the renderer reading it, eased (decision 110), where it
 * draws the rainbow -- only while a forecast is drawn: with the theme's own weather (Live Weather off, or waiting) the
 * rainbow is as its switch says, in the theme's rain too.
 */
class RainbowUnderForecastTest {

    private fun forecast(rain: Double, snowfall: Double, code: Int, cover: Int = 100): LiveWeatherSnapshot {
        val json = """{"current":{"temperature_2m":9.5,"cloud_cover":$cover,"precipitation":${rain + snowfall},"rain":$rain,""" +
            """"showers":0.0,"snowfall":$snowfall,"weather_code":$code}}"""
        return WeatherSnapshotMapper.toSnapshot(checkNotNull(OpenMeteoProvider.parse(json)) { json })
    }

    private fun shows(live: LiveWeatherSnapshot) = LiveWeatherSceneRules.rainbowUnderForecast(live.precipitationType != null, live.isThunderstorm)

    @Test
    fun `the forecast's rain, snow and thunderstorm keep the rainbow away, and a forecast with nothing falling does not`() {
        for ((rain, code) in listOf(0.4 to 61, 3.0 to 63, 9.0 to 65, 2.0 to 80)) {
            val live = forecast(rain = rain, snowfall = 0.0, code = code)
            assertEquals(PrecipitationType.RAIN, live.precipitationType)
            assertFalse("rain $rain mm", shows(live))
        }
        for ((snow, code) in listOf(0.3 to 71, 1.5 to 73, 4.0 to 75)) {
            val live = forecast(rain = 0.0, snowfall = snow, code = code)
            assertEquals(PrecipitationType.SNOW, live.precipitationType)
            assertFalse("snow $snow cm", shows(live))
        }
        val storm = forecast(rain = 6.0, snowfall = 0.0, code = 95)
        assertTrue("a thunderstorm", storm.isThunderstorm)
        assertFalse("a thunderstorm", shows(storm))
        for ((cover, code) in listOf(0 to 0, 30 to 1, 80 to 3)) {
            val clear = forecast(rain = 0.0, snowfall = 0.0, code = code, cover = cover)
            assertEquals(null, clear.precipitationType)
            assertTrue("nothing falling at $cover % cover", shows(clear))
        }
        // The rule, each way.
        assertTrue(LiveWeatherSceneRules.rainbowUnderForecast(forecastFalling = false, forecastStorm = false))
        assertFalse(LiveWeatherSceneRules.rainbowUnderForecast(forecastFalling = true, forecastStorm = false))
        assertFalse(LiveWeatherSceneRules.rainbowUnderForecast(forecastFalling = false, forecastStorm = true))
        assertFalse(LiveWeatherSceneRules.rainbowUnderForecast(forecastFalling = true, forecastStorm = true))
    }

    /**
     * The renderer asks the rule only of a forecast drawn, and eases what it answers ([RainbowFade], decision 110): whole
     * lines of `drawRainbow`, before its sprite is drawn -- the permission worked out every frame before the switch is read,
     * and the eased level in the rainbow's visibility.
     */
    @Test
    fun `the renderer draws the rainbow by the rule, eased, and with no forecast as its switch says`() {
        val text = source("PaperRenderer.kt")
        val body = text.substring(text.indexOf("    private fun drawRainbow("), text.indexOf("R.drawable.rainbow_arc"))
        val lines = body.lines().map { it.trim() }
        val read = lines.indexOf("val live = liveWeatherOverride")
        val ease = lines.indexOf("val weatherLevel = rainbowFade.levelToward(")
        val rule = lines.indexOf("live == null || LiveWeatherSceneRules.rainbowUnderForecast(live.precipitationType != null, live.isThunderstorm),")
        val switch = lines.indexOf("if (!rainbow.visible) return")
        val visibility = lines.indexOf("val visibility = (dayPhase.dayBlend * 1.4f).coerceIn(0f, 1f) * rainbow.opacity.coerceIn(0f, 1f) * weatherLevel")
        assertTrue("the forecast drawn is read", read >= 0)
        assertTrue("the rule is eased", ease > read && rule == ease + 1 && lines[ease + 2] == "deltaSeconds,")
        assertTrue("eased before the switch is read, so it follows the weather with the rainbow off", switch > rule)
        val code = lines.map { it.substringBefore("//").trim() }
        assertTrue(
            "nothing leaves drawRainbow before the weather's level is eased",
            code.subList(0, ease).none { Regex("\\breturn\\b").containsMatchIn(it) },
        )
        assertTrue("the eased level is in the rainbow's visibility", visibility > switch)
        // Called on every frame: a line of its own directly in `draw`'s body, inside no branch, with the frame's own delta.
        val draw = text.substring(text.indexOf("    fun draw(canvas: SceneCanvas,")).lines()
        val call = draw.indexOfFirst { it.substringBefore("//").trimEnd() == "        drawRainbow(canvas, dayPhase, deltaSeconds)" }
        assertTrue("drawRainbow is called with the frame's own delta", call > 0)
        var depth = 0
        for (line in draw.subList(0, call)) {
            val c = line.substringBefore("//")
            depth += c.count { it == '{' } - c.count { it == '}' }
        }
        assertEquals("drawRainbow sits directly in draw's body, inside no branch", 1, depth)
        // The settings line under the switch says it, whole.
        val ui = File(repoRoot(), "app/src/main/kotlin/com/paperscrape/livewallpaper/ui/SettingsUiModel.kt").readLines().map { it.trim() }
        assertTrue(ui.contains("const val RAINBOW_LINE = \"Shown by day, except while Live Weather draws rain, snow or a thunderstorm.\""))
        // Under the switch while it reads on, as decision 40's lines say; under a switch reading off, nothing.
        val screen = File(repoRoot(), "app/src/main/kotlin/com/paperscrape/livewallpaper/ui/WorldSceneScreen.kt").readLines()
            .map { it.substringBefore("//").trim() }
        val row = screen.indexOf("title = \"Show Rainbow\",")
        assertTrue("the Show Rainbow row", row >= 0)
        assertEquals(
            listOf(
                "subtitle = when {",
                "!byDay.interactive -> SettingsUiModel.NIGHT_BY_DAY_LOCK_LINE",
                "shown.noneAtZero -> \"None at 0% opacity - tap to bring it back\"",
                "shown.shownOn -> SettingsUiModel.RAINBOW_LINE",
                "else -> \"\"",
                "},",
            ),
            screen.subList(row + 1, row + 7),
        )
    }

    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "app/src/main/res/drawable-nodpi").isDirectory) return dir
            dir = dir.parentFile
        }
        error("repository root not found")
    }

    private fun source(name: String): String = File(repoRoot(), "app/src/main/kotlin/com/paperscrape/livewallpaper/engine/$name").readText()
}
