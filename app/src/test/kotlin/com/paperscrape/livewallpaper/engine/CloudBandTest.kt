package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cloud band, and the two things hung off it.
 *
 * A bolt that does not read the band comes out of the top of the sky, or out of clear air under
 * it: the band moves with the arc, its top at 0.15 of screen height at the default, so no fixed
 * fraction of the screen stays inside it. The invariant worth pinning is therefore not any one
 * number but the *relationship*: a bolt is born inside the clouds, at every arc height, and stays
 * clear of the horizon.
 */
class CloudBandTest {

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
            val top = CloudBand.topFor(screenHeight, arc)
            val bottom = top + CloudBand.heightFor(screenHeight)
            assertTrue("band top below 0 at arc $arc", top >= 0f)
            assertTrue("band crosses the horizon at arc $arc", bottom < screenHeight * horizonFraction)
        }
    }

    @Test
    fun `a lower arc puts the band lower`() {
        var previous = -1f
        for (arc in arcHeights.sortedDescending()) {
            val top = CloudBand.topFor(screenHeight, arc)
            assertTrue("band did not descend monotonically at arc $arc", top > previous)
            previous = top
        }
    }

    @Test
    fun `the arc is clamped to its own range`() {
        assertEquals(CloudBand.topFor(screenHeight, SUN_CLOUD_HEIGHT_MIN), CloudBand.topFor(screenHeight, -5f), 0.001f)
        assertEquals(CloudBand.topFor(screenHeight, SUN_CLOUD_HEIGHT_MAX), CloudBand.topFor(screenHeight, 5f), 0.001f)
    }

    // -- what hangs off it ---------------------------------------------------------------------------

    /**
     * Both origins have to land *within* the band -- rain from its middle, lightning from deeper
     * still -- at every arc height. The band's top runs from 0.06 of screen height at the top of the
     * slider to 0.31 at its foot, more than the band's own 0.16, so an origin at a fixed fraction of
     * the screen leaves the band somewhere on the slider: it has to be read off the band.
     */
    @Test
    fun `rain and lightning are both born inside the band`() {
        for (arc in arcHeights) {
            val top = CloudBand.topFor(screenHeight, arc)
            val bottom = top + CloudBand.heightFor(screenHeight)

            val rain = CloudBand.precipitationOriginY(screenHeight, arc)
            assertTrue("rain starts above the clouds at arc $arc", rain > top)
            assertTrue("rain starts below the clouds at arc $arc", rain < bottom)

            val bolt = CloudBand.lightningOriginY(screenHeight, arc)
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
                CloudBand.lightningOriginY(screenHeight, arc) > CloudBand.precipitationOriginY(screenHeight, arc),
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
            val bottom = CloudBand.lightningOriginY(screenHeight, arc) + screenHeight * maxHeight
            assertTrue("the longest bolt crosses the horizon at arc $arc", bottom < screenHeight * horizonFraction)
        }
    }

    /** Independent of resolution: every one of these is a fraction of the screen. */
    @Test
    fun `the geometry scales with the screen`() {
        val tall = 3200
        val short = 1600
        assertEquals(
            CloudBand.lightningOriginY(tall, 0.42f) / tall,
            CloudBand.lightningOriginY(short, 0.42f) / short,
            0.0001f,
        )
    }
}
