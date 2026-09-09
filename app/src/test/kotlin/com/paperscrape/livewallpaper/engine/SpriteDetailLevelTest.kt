package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which pre-reduced copy of a sprite the GL backend samples.
 *
 * The failure this guards is silent in both directions. Reduce too little and the sprite is
 * minified several times over by a single bilinear tap, which is the flicker the whole mechanism
 * exists to remove; reduce too much and the copy has to be magnified back up, which is blur that
 * no test would notice and that measurement showed is the worse of the two mistakes. Neither shows
 * up as a wrong pixel in a golden, because a golden is one frame and this is about what happens
 * *between* frames.
 *
 * So the central assertion is the invariant rather than a table of examples: whatever factor the
 * scene arrives with, the copy that gets sampled must stay inside the residual band. The examples
 * that follow it are the sizes actually measured on the device, kept so a change to the threshold
 * has to argue with the real scene and not only with the arithmetic.
 */
class SpriteDetailLevelTest {

    /** The band the class's own comment promises: a copy 1.4x to 2.8x the drawn size. */
    private val lowResidual = 0.3535f
    private val highResidual = 0.7072f

    @Test
    fun `residual stays inside the band across every factor the scene can produce`() {
        // From a sprite drawn larger than its own pixels down past the 26x worst case on the device.
        var factor = 2f
        while (factor > 0.02f) {
            val level = SpriteDetailLevel.levelFor(factor)
            val residual = factor * (1 shl level)
            if (level == 0) {
                assertTrue("$factor should not be reduced at all", residual >= lowResidual)
            } else {
                assertTrue("$factor gave residual $residual, below the band", residual >= lowResidual)
                assertTrue("$factor gave residual $residual, above the band", residual <= highResidual)
            }
            factor *= 0.997f
        }
    }

    @Test
    fun `a sprite drawn at or above its own resolution is never reduced`() {
        assertEquals(0, SpriteDetailLevel.levelFor(1f))
        assertEquals(0, SpriteDetailLevel.levelFor(3f))
        assertEquals(0, SpriteDetailLevel.levelFor(0.5f))
    }

    @Test
    fun `the levels the measured scene asks for`() {
        // Measured on the BV6600: an adult walker, a bust behind a windscreen, and the nearest and
        // furthest a bust behind a house window is drawn.
        assertEquals(2, SpriteDetailLevel.levelFor(0.1417f))
        assertEquals(2, SpriteDetailLevel.levelFor(0.1212f))
        assertEquals(2, SpriteDetailLevel.levelFor(0.1417f))
        assertEquals(4, SpriteDetailLevel.levelFor(0.0380f))
    }

    @Test
    fun `an absurd factor is capped rather than asked for a copy with no pixels`() {
        assertEquals(SpriteDetailLevel.MAX_LEVEL, SpriteDetailLevel.levelFor(0.00001f))
        assertEquals(SpriteDetailLevel.MAX_LEVEL, SpriteDetailLevel.levelFor(Float.MIN_VALUE))
    }

    @Test
    fun `a reduced dimension never reaches zero`() {
        for (level in 0..SpriteDetailLevel.MAX_LEVEL) {
            for (size in 1..40) {
                assertTrue(SpriteDetailLevel.reduced(size, level) >= 1)
            }
        }
        assertEquals(1, SpriteDetailLevel.reduced(1, SpriteDetailLevel.MAX_LEVEL))
        assertEquals(255 shr 2, SpriteDetailLevel.reduced(255, 2))
    }
}
