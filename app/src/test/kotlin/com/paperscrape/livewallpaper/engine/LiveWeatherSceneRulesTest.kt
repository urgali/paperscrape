package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which layer's settings win while Live Weather is on.
 *
 * The defect this pins was an **asymmetry**, not a wrong value: precipitation ignored the theme's
 * own switch while the forecast was driving, and clouds did not. Measured on a clean emulator with
 * the cloud layer switched off and Open-Meteo reporting 100 % cover:
 *
 * ```
 * SCENE clouds.visible=false clouds.density=0.4 override.cloudCover=1.0 -> drawn=false
 * SCENE precip.visible=false precip.intensity=0.5 override.type=RAIN  -> drawn=true
 * ```
 *
 * Rain from the forecast, no clouds from the same forecast. Every test below is a statement about
 * the two layers agreeing.
 */
class LiveWeatherSceneRulesTest {

    // -- Live Weather on: the forecast decides -------------------------------------------------------

    /** The reported bug, as a unit. */
    @Test
    fun `a switched-off cloud layer does not veto the forecast`() {
        val density = LiveWeatherSceneRules.cloudDensity(
            liveCloudCover = 1f,
            themeCloudsVisible = false,
            themeCloudDensity = 0.4f,
        )
        assertNotNull("the forecast reported full cover; clouds must be drawn", density)
        assertEquals(1f, density!!, 0.0001f)
    }

    @Test
    fun `the forecast overrides the theme's own density too`() {
        assertEquals(
            0.25f,
            LiveWeatherSceneRules.cloudDensity(0.25f, themeCloudsVisible = true, themeCloudDensity = 0.9f)!!,
            0.0001f,
        )
    }

    /** A clear forecast draws no clouds, whatever the theme's switch and slider say. */
    @Test
    fun `a clear forecast draws no clouds even with the layer switched on`() {
        assertNull(LiveWeatherSceneRules.cloudDensity(0f, themeCloudsVisible = true, themeCloudDensity = 0.9f))
        assertNull(LiveWeatherSceneRules.cloudDensity(0f, themeCloudsVisible = false, themeCloudDensity = 0.9f))
    }

    /** Cover is a fraction. Anything outside 0..1 is clamped rather than trusted. */
    @Test
    fun `cover is clamped into range`() {
        assertEquals(1f, LiveWeatherSceneRules.cloudDensity(4f, true, 0.4f)!!, 0.0001f)
        assertNull(LiveWeatherSceneRules.cloudDensity(-1f, true, 0.4f))
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
                )
            }
        }
    }

    // -- Live Weather off: the theme decides ---------------------------------------------------------

    @Test
    fun `without the forecast the theme's switch still governs`() {
        assertNull(LiveWeatherSceneRules.cloudDensity(null, themeCloudsVisible = false, themeCloudDensity = 0.4f))
        assertEquals(
            0.4f,
            LiveWeatherSceneRules.cloudDensity(null, themeCloudsVisible = true, themeCloudDensity = 0.4f)!!,
            0.0001f,
        )
    }

    /** Unchanged from before the fix: a visible layer at zero density is still a visible layer. */
    @Test
    fun `a visible layer at zero density is not the same as a switched-off layer`() {
        assertEquals(
            0f,
            LiveWeatherSceneRules.cloudDensity(null, themeCloudsVisible = true, themeCloudDensity = 0f)!!,
            0.0001f,
        )
    }

    // -- coverage ------------------------------------------------------------------------------------

    /**
     * Precipitation is thinned by the cloud coverage under it. When no clouds are placed there is no
     * field to thin it with, and an empty one would silently cancel rain the forecast did report --
     * which is the same class of bug as the one above, one layer quietly overruling the other.
     */
    @Test
    fun `no clouds means uniform coverage, so precipitation is never silently cancelled`() {
        assertTrue(LiveWeatherSceneRules.coverageIsUniformWhenNoClouds())
    }
}
