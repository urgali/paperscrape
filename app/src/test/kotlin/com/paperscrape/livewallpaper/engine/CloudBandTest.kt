package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cloud band, and the two things hung off it.
 *
 * These exist because of a live-render report -- "i fulmini sono giganti e escono dalla cima del
 * cielo" -- whose cause was that the lightning did not read the band at all: it used a fixed 0.08
 * of screen height while the band, at the default arc, starts at 0.15. The invariant worth pinning
 * is therefore not any one number but the *relationship*: a bolt is born inside the clouds, at
 * every arc height, and stays clear of the horizon.
 */
class CloudBandTest {

    private val screenHeight = 2424
    private val horizonFraction = 0.62f

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
     * The defect, stated directly. Both origins have to land *within* the band -- rain from its
     * middle, lightning from deeper still -- at every arc height. The old lightning constant fails
     * this at every arc height except none: 0.08 * screenHeight is above the band's top even at the
     * band's highest position (0.06 + 0 = 0.06 only at the very top of the slider, where the bolt
     * would still start above the midpoint).
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
     * The other half of the report: the bolt was 0.26..0.40 of screen height, taller than the whole
     * cloud layer. It is now sized against the band, and the tallest roll still ends well above the
     * horizon from the lowest band position.
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
