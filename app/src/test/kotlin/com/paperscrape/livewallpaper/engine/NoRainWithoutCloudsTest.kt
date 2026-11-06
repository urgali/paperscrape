package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **No rain and no snow without clouds** (`DESIGN_NOTES.md` §12, decision 98): the theme's rain, snow and storm need its
 * clouds -- *Show Clouds* on above 0 % --; a forecast reporting something falling under a clearer sky than a fifth is drawn
 * with a fifth of clouds to fall from; the rain falls under the clouds drawn and stops with the last.
 */
class NoRainWithoutCloudsTest {

    @Test
    fun `a forecast with something falling is drawn with at least a fifth of the sky in clouds`() {
        assertEquals(0.2f, LiveWeatherSceneRules.FALLING_MIN_COVER, 0f)
        for (cover in listOf(0f, 0.05f, 0.19f)) {
            assertEquals("$cover, falling", 0.2f, LiveWeatherSceneRules.forecastCover(cover, somethingFalling = true), 0f)
            assertEquals("$cover, dry", cover, LiveWeatherSceneRules.forecastCover(cover, somethingFalling = false), 0f)
        }
        for (cover in listOf(0.2f, 0.5f, 1f)) assertEquals(cover, LiveWeatherSceneRules.forecastCover(cover, somethingFalling = true), 0f)
        assertTrue(LiveWeatherSceneRules.forecastCover(Float.NaN, somethingFalling = true).isNaN())
        // The cover drawn places clouds: the sky is not cloudless.
        val placed = LiveWeatherSceneRules.cloudDensity(LiveWeatherSceneRules.forecastCover(0f, true), themeCloudsVisible = false, themeCloudDensity = 0f)
        assertEquals(0.2f, placed, 0f)
        assertFalse(LiveWeatherSceneRules.cloudlessSky(lapsed = false, themeDensity = Float.NaN, placedDensity = placed, cloudsShowing = false))
    }

    @Test
    fun `the sky is cloudless with the theme's clouds off or at 0 percent, and not while a cloud still fades`() {
        val rules = LiveWeatherSceneRules
        for (visible in listOf(true, false)) for (density in listOf(0f, 0.01f, 0.5f)) for (lapsed in listOf(true, false))
            for (showing in listOf(true, false)) {
                val theme = rules.cloudDensity(Float.NaN, visible, density)
                val none = !visible || density <= 0f
                val expected = if (lapsed) none else none && !showing
                assertEquals("visible=$visible $density lapsed=$lapsed showing=$showing", expected, rules.cloudlessSky(lapsed, theme, theme, showing))
            }
        // The forecast's clouds fading after a lapse do not carry the theme's rain when the theme has none.
        assertTrue(rules.cloudlessSky(lapsed = true, themeDensity = Float.NaN, placedDensity = 0.6f, cloudsShowing = true))
    }

    /** The gallery card rains and snows only where the theme's clouds are on above 0 %, as the wallpaper. */
    @Test
    fun `the gallery card draws nothing falling from a theme with no cloud`() {
        val card = source("ThemePreviewScene.kt")
        assertTrue(card.contains("if (drawn(c.precipitation.visible, c.precipitation.intensity) && drawn(c.clouds.visible, c.clouds.density)) {"))
    }

    private fun source(name: String): String {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/engine/$name"
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = File(dir, "$prefix$suffix")
                if (f.isFile) return f.readText()
            }
            dir = dir.parentFile
        }
        error("$suffix not found")
    }
}
