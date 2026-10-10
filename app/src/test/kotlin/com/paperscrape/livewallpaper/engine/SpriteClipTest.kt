package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A sprite cut to a box draws the part of the picture that is inside it, where it was** (v5.12,
 * [SpriteClip]; the GPU half of [SceneCanvas.drawSpriteClipped]). The quad is cut, and the texture
 * rectangle in the same proportion, so a texel stays at the place on the screen it had in the whole
 * sprite: a walker cut at a window's edge is the same picture with part of it missing, not a squeezed
 * one.
 */
class SpriteClipTest {

    private val out = FloatArray(SpriteClip.SIZE)

    /** Where texture coordinate [u] of the quad [left]..[right], textured [u0]..[u1], is drawn. */
    private fun xOf(u: Float, left: Float, right: Float, u0: Float, u1: Float) = left + (u - u0) / (u1 - u0) * (right - left)

    @Test
    fun `a box round the whole quad gives the quad back, to the bit`() {
        val quad = floatArrayOf(10.3f, -4.7f, 61.9f, 88.1f, 0.1234f, 0.5f, 0.3141f, 0.7777f)
        assertTrue(SpriteClip.clip(quad[0], quad[1], quad[2], quad[3], quad[4], quad[5], quad[6], quad[7], -1000f, -1000f, 1000f, 1000f, out))
        for (i in 0 until SpriteClip.SIZE) assertEquals("index $i", quad[i].toRawBits(), out[i].toRawBits())
        // A box that touches the quad exactly cuts nothing either.
        assertTrue(SpriteClip.clip(quad[0], quad[1], quad[2], quad[3], quad[4], quad[5], quad[6], quad[7], quad[0], quad[1], quad[2], quad[3], out))
        for (i in 0 until SpriteClip.SIZE) assertEquals("touching, index $i", quad[i].toRawBits(), out[i].toRawBits())
    }

    @Test
    fun `nothing inside, nothing drawn`() {
        out.fill(42f)
        assertFalse(SpriteClip.clip(0f, 0f, 10f, 10f, 0f, 0f, 1f, 1f, 10f, 0f, 20f, 10f, out)) // beside it
        assertFalse(SpriteClip.clip(0f, 0f, 10f, 10f, 0f, 0f, 1f, 1f, -5f, 12f, 15f, 20f, out)) // below it
        assertFalse(SpriteClip.clip(0f, 0f, 10f, 10f, 0f, 0f, 1f, 1f, 6f, 0f, 4f, 10f, out)) // an empty box
        assertTrue("untouched", out.all { it == 42f })
    }

    @Test
    fun `a cut side keeps every texel where it was drawn`() {
        val left = 12f; val top = 30f; val right = 52f; val bottom = 90f
        val u0 = 0.25f; val v0 = 0.5f; val u1 = 0.45f; val v1 = 0.8f
        for (cut in listOf(13f, 20.5f, 31f, 49.75f)) {
            assertTrue(SpriteClip.clip(left, top, right, bottom, u0, v0, u1, v1, cut, top - 1f, right + 1f, bottom + 1f, out))
            assertEquals(cut, out[SpriteClip.LEFT], 0f)
            assertEquals("the right side untouched", right, out[SpriteClip.RIGHT], 0f)
            assertEquals("its texture too", u1, out[SpriteClip.U1], 0f)
            assertEquals("the texel at the cut is drawn at the cut", cut, xOf(out[SpriteClip.U0], left, right, u0, u1), 1e-4f)
            assertEquals("nothing vertical moves", v0, out[SpriteClip.V0], 0f)
            assertEquals(v1, out[SpriteClip.V1], 0f)
            // And from the other side.
            assertTrue(SpriteClip.clip(left, top, right, bottom, u0, v0, u1, v1, left - 5f, top, cut, bottom, out))
            assertEquals(cut, out[SpriteClip.RIGHT], 0f)
            assertEquals(u0, out[SpriteClip.U0], 0f)
            assertEquals("the texel at the cut is drawn at the cut", cut, xOf(out[SpriteClip.U1], left, right, u0, u1), 1e-4f)
        }
        // Top and bottom the same way.
        assertTrue(SpriteClip.clip(left, top, right, bottom, u0, v0, u1, v1, left, 45f, right, 80f, out))
        assertEquals(v0 + (v1 - v0) * (15f / 60f), out[SpriteClip.V0], 1e-6f)
        assertEquals(v0 + (v1 - v0) * (50f / 60f), out[SpriteClip.V1], 1e-6f)
    }

    @Test
    fun `a texture held upside down or mirrored is cut the same way`() {
        // Texture coordinates need not grow with the quad: the proportion is what is kept.
        assertTrue(SpriteClip.clip(0f, 0f, 100f, 100f, 0.9f, 0.8f, 0.1f, 0.2f, 25f, 50f, 100f, 100f, out))
        assertEquals(0.9f + (0.1f - 0.9f) * 0.25f, out[SpriteClip.U0], 1e-6f)
        assertEquals(0.1f, out[SpriteClip.U1], 0f)
        assertEquals(0.8f + (0.2f - 0.8f) * 0.5f, out[SpriteClip.V0], 1e-6f)
    }
}
