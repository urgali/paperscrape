package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cloud band, and the two things hung off it.
 *
 * A bolt that does not read the band comes out of the top of the sky, or out of clear air under
 * it: the band moves with the arc, its top at 0.26 of screen height at the default, so no fixed
 * fraction of the screen stays inside it. The invariant worth pinning is therefore not any one
 * number but the *relationship*: a bolt is born inside the clouds, at every arc height, and stays
 * clear of the horizon.
 */
class CloudBandTest {

    private val screenWidth = 1080
    private val screenHeight = 2424
    /** The hills' top: no cloud and no bolt reaches it, at any Sun/Cloud height. */
    private val horizonFraction = SceneSpace.HILL_LAYER_TOP_FRACTION

    /** The whole slider, plus the two ends, sampled finely enough to catch a sign error. */
    private val arcHeights: List<Float> =
        (0..20).map { SUN_CLOUD_HEIGHT_MIN + (SUN_CLOUD_HEIGHT_MAX - SUN_CLOUD_HEIGHT_MIN) * it / 20f }

    // -- the band itself ---------------------------------------------------------------------------

    @Test
    fun `the band moves with the arc and never reaches the horizon`() {
        for (arc in arcHeights) {
            val top = CloudBand.topFor(screenWidth, screenHeight, arc)
            val bottom = top + CloudBand.heightFor(screenHeight)
            assertTrue("band top below 0 at arc $arc", top >= 0f)
            assertTrue("band crosses the horizon at arc $arc", bottom < screenHeight * horizonFraction)
        }
    }

    @Test
    fun `a lower arc puts the band lower`() {
        var previous = -1f
        for (arc in arcHeights.sortedDescending()) {
            val top = CloudBand.topFor(screenWidth, screenHeight, arc)
            assertTrue("band did not descend monotonically at arc $arc", top > previous)
            previous = top
        }
    }

    @Test
    fun `the arc is clamped to its own range`() {
        assertEquals(CloudBand.topFor(screenWidth, screenHeight, SUN_CLOUD_HEIGHT_MIN), CloudBand.topFor(screenWidth, screenHeight, -5f), 0.001f)
        assertEquals(CloudBand.topFor(screenWidth, screenHeight, SUN_CLOUD_HEIGHT_MAX), CloudBand.topFor(screenWidth, screenHeight, 5f), 0.001f)
    }

    // -- what hangs off it ---------------------------------------------------------------------------

    /**
     * Both origins have to land *within* the band -- rain from its middle, lightning from deeper
     * still -- at every arc height. The band's top runs from 0.24 of screen height at the top of the
     * slider to 0.31 at its foot, and the band is 0.16 tall, so an origin at a fixed fraction of
     * the screen leaves the band somewhere on the slider: it has to be read off the band.
     */
    @Test
    fun `rain and lightning are both born inside the band`() {
        for (arc in arcHeights) {
            val top = CloudBand.topFor(screenWidth, screenHeight, arc)
            val bottom = top + CloudBand.heightFor(screenHeight)

            val rain = CloudBand.precipitationOriginY(screenWidth, screenHeight, arc)
            assertTrue("rain starts above the clouds at arc $arc", rain > top)
            assertTrue("rain starts below the clouds at arc $arc", rain < bottom)

            val bolt = CloudBand.lightningOriginY(screenWidth, screenHeight, arc)
            assertTrue("bolt starts above the clouds at arc $arc", bolt > top)
            assertTrue("bolt starts below the clouds at arc $arc", bolt < bottom)
        }
    }

    /** Deeper than the rain, so the bolt's head is behind cloud rather than at its lower edge. */
    @Test
    fun `a bolt is born deeper in the cloud than the rain`() {
        for (arc in arcHeights) {
            assertTrue(
                "bolt not deeper than rain at arc $arc",
                CloudBand.lightningOriginY(screenWidth, screenHeight, arc) > CloudBand.precipitationOriginY(screenWidth, screenHeight, arc),
            )
        }
    }

    /** Past the midpoint is what "comes out of the cloud" means here. */
    @Test
    fun `the lightning depth is past the middle of the band`() {
        assertTrue(CloudBand.LIGHTNING_DEPTH_FRACTION > 0.5f)
        assertTrue(CloudBand.LIGHTNING_DEPTH_FRACTION < 1f)
    }

    // -- the bolt's size -----------------------------------------------------------------------------

    /**
     * A bolt is sized against the band: never taller than the whole cloud layer, and the tallest
     * roll still ends well above the horizon from the lowest band position.
     */
    @Test
    fun `a bolt fits the cloud layer and never crosses the horizon`() {
        val minHeight = PaperRenderer.LIGHTNING_BOLT_MIN_HEIGHT_FRACTION
        val maxHeight = minHeight + PaperRenderer.LIGHTNING_BOLT_HEIGHT_SPREAD_FRACTION
        val band = CloudBand.heightFor(screenHeight) / screenHeight

        assertTrue("a bolt should not dwarf the cloud layer", maxHeight <= band + 0.0001f)
        assertTrue("a bolt should still be a visible fork", minHeight >= 0.06f)

        for (arc in arcHeights) {
            val bottom = CloudBand.lightningOriginY(screenWidth, screenHeight, arc) + screenHeight * maxHeight
            assertTrue("the longest bolt crosses the horizon at arc $arc", bottom < screenHeight * horizonFraction)
        }
    }

    /** Independent of resolution: every one of these is a fraction of the screen. */
    @Test
    fun `the geometry scales with the screen`() {
        val tall = 3200
        val short = 1600
        assertEquals(
            CloudBand.lightningOriginY(tall / 2, tall, 0.42f) / tall,
            CloudBand.lightningOriginY(short / 2, short, 0.42f) / short,
            0.0001f,
        )
    }

    /**
     * **The clouds follow the sun** (decision 106): at every value of the slider the band's top stands under the top of the
     * sun's arc ([SunArc.rise]) as far as it stood under it at that value with the arc rising as far as the value stored --
     * `0.36 + 0.5 * stored - rise` of the screen: 0.03 of the screen under the sun's middle as the slider comes.
     */
    @Test
    fun `the band stands under the sun as far as it stood at the same value of the slider`() {
        for ((w, h) in listOf(720 to 1440, 1080 to 2340, 1080 to 2400, 1440 to 3200)) {
            for (arc in arcHeights) {
                val rise = SunArc.rise(arc, w, h)
                val sunMiddle = h * PaperRenderer.HORIZON_Y_FRACTION_OF_ARC - rise * h
                // At the same value, the arc rising as far as the value stored: the band and the sun as they were.
                val wasSun = h * PaperRenderer.HORIZON_Y_FRACTION_OF_ARC - arc * h
                val wasBand = h * (0.06f + (SUN_CLOUD_HEIGHT_MAX - arc) * 0.5f)
                assertEquals("$w x $h at $arc: the band under the sun", wasBand - wasSun, CloudBand.topFor(w, h, arc) - sunMiddle, 0.01f)
            }
        }
        // On the BV6600 the slider as it comes (the stored 0.42) puts the band's top at 0.263 of the height,
        // 0.03 under the sun's middle, at 0.233.
        assertEquals(0.263f, CloudBand.topFor(720, 1440, 0.42f) / 1440f, 0.002f)
        assertEquals("at its foot where it always stood", 0.31f, CloudBand.topFor(720, 1440, SUN_CLOUD_HEIGHT_MIN) / 1440f, 0.001f)
    }

    /** On a screen much wider than tall, where the arc has little room ([SunArc.highest]), the band stays over the horizon. */
    @Test
    fun `on a wide screen the band stays clear of the horizon`() {
        for ((w, h) in listOf(2400 to 1080, 2560 to 1600, 1600 to 2560, 720 to 1440)) for (arc in arcHeights) {
            val bottom = CloudBand.topFor(w, h, arc) + CloudBand.heightFor(h)
            assertTrue("$w x $h at $arc: the band's foot at ${bottom / h}", bottom < h * PaperRenderer.HORIZON_Y_FRACTION_OF_ARC)
            assertTrue("$w x $h at $arc", CloudBand.topFor(w, h, arc) <= h * 0.31f + 0.01f)
        }
    }
}
