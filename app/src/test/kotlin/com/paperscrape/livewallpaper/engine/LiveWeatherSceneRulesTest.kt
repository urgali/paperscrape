package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which layer's settings win while Live Weather is on.
 *
 * The defect this prevents is an **asymmetry**, not a wrong value: precipitation ignores the theme's
 * own switch while the forecast is driving, so clouds must too. With the cloud layer switched off and
 * the forecast at 100 % cover, a cloud layer that keeps its own switch draws rain from the forecast
 * and no clouds from the same forecast. Every test below is a statement about the two layers agreeing.
 */
class LiveWeatherSceneRulesTest {

    // -- Live Weather on: the forecast decides -------------------------------------------------------

    /** Full cover from the forecast, the cloud layer switched off: the clouds are drawn. */
    @Test
    fun `a switched-off cloud layer does not veto the forecast`() {
        val density = LiveWeatherSceneRules.cloudDensity(
            liveCloudCover = 1f,
            themeCloudsVisible = false,
            themeCloudDensity = 0.4f,
        )
        assertFalse("the forecast reported full cover; clouds must be drawn", density.isNaN())
        assertEquals(1f, density, 0.0001f)
    }

    @Test
    fun `the forecast overrides the theme's own density too`() {
        assertEquals(
            0.25f,
            LiveWeatherSceneRules.cloudDensity(0.25f, themeCloudsVisible = true, themeCloudDensity = 0.9f),
            0.0001f,
        )
    }

    /** A clear forecast draws no clouds, whatever the theme's switch and slider say. */
    @Test
    fun `a clear forecast draws no clouds even with the layer switched on`() {
        assertTrue(LiveWeatherSceneRules.cloudDensity(0f, themeCloudsVisible = true, themeCloudDensity = 0.9f).isNaN())
        assertTrue(LiveWeatherSceneRules.cloudDensity(0f, themeCloudsVisible = false, themeCloudDensity = 0.9f).isNaN())
    }

    /** Cover is a fraction. Anything outside 0..1 is clamped rather than trusted. */
    @Test
    fun `cover is clamped into range`() {
        assertEquals(1f, LiveWeatherSceneRules.cloudDensity(4f, true, 0.4f), 0.0001f)
        assertTrue(LiveWeatherSceneRules.cloudDensity(-1f, true, 0.4f).isNaN())
    }

    /**
     * The property that makes the asymmetry impossible to reintroduce: with Live Weather active and
     * any positive cover, the theme's switch changes nothing at all.
     */
    @Test
    fun `with the forecast driving, the theme's cloud switch has no effect`() {
        for (cover in listOf(0.01f, 0.2f, 0.5f, 0.8f, 1f)) {
            for (themeDensity in listOf(0f, 0.4f, 1f)) {
                assertEquals(
                    "cover $cover density $themeDensity",
                    LiveWeatherSceneRules.cloudDensity(cover, themeCloudsVisible = true, themeCloudDensity = themeDensity),
                    LiveWeatherSceneRules.cloudDensity(cover, themeCloudsVisible = false, themeCloudDensity = themeDensity),
                    0f,
                )
            }
        }
    }

    // -- Live Weather off: the theme decides ---------------------------------------------------------

    @Test
    fun `without the forecast the theme's switch still governs`() {
        assertTrue(LiveWeatherSceneRules.cloudDensity(Float.NaN, themeCloudsVisible = false, themeCloudDensity = 0.4f).isNaN())
        assertEquals(
            0.4f,
            LiveWeatherSceneRules.cloudDensity(Float.NaN, themeCloudsVisible = true, themeCloudDensity = 0.4f),
            0.0001f,
        )
    }

    /** A visible layer at zero density is still a visible layer. */
    @Test
    fun `a visible layer at zero density is not the same as a switched-off layer`() {
        assertEquals(
            0f,
            LiveWeatherSceneRules.cloudDensity(Float.NaN, themeCloudsVisible = true, themeCloudDensity = 0f),
            0.0001f,
        )
    }

    /** **No breaking waves under falling snow**, thundersnow included. */
    @Test
    fun `the lake raises waves in rain and in a storm, never under snow`() {
        org.junit.Assert.assertTrue(LiveWeatherSceneRules.wavesOnLake(raining = true, stormActive = false, snowing = false))
        org.junit.Assert.assertTrue(LiveWeatherSceneRules.wavesOnLake(raining = false, stormActive = true, snowing = false))
        org.junit.Assert.assertFalse("thundersnow", LiveWeatherSceneRules.wavesOnLake(raining = false, stormActive = true, snowing = true))
        org.junit.Assert.assertFalse(LiveWeatherSceneRules.wavesOnLake(raining = false, stormActive = false, snowing = true))
        org.junit.Assert.assertFalse(LiveWeatherSceneRules.wavesOnLake(raining = false, stormActive = false, snowing = false))
    }
}
