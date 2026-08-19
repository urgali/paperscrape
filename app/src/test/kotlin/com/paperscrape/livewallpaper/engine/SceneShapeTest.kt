package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [SceneShape]'s vertex bookkeeping.
 *
 * The shapes it holds are rebuilt every frame — two per mountain, two per falling gift — so the
 * contract that matters is that a rebuild produces exactly the vertices it was given and nothing
 * left over from the previous frame. A stale trailing vertex would not throw; it would add one
 * silent triangle to a mountain face.
 */
class SceneShapeTest {

    @Test
    fun `moveTo starts a fresh shape`() {
        val shape = SceneShape()
        shape.moveTo(1f, 2f)
        shape.lineTo(3f, 4f)
        shape.lineTo(5f, 6f)
        assertEquals(3, shape.pointCount)
        assertEquals(1f, shape.xAt(0), 0f)
        assertEquals(6f, shape.yAt(2), 0f)
    }

    @Test
    fun `rebuilding drops every vertex of the previous build`() {
        val shape = SceneShape()
        shape.moveTo(0f, 0f)
        repeat(20) { shape.lineTo(it.toFloat(), it.toFloat()) }
        shape.close()

        shape.moveTo(9f, 9f)
        shape.lineTo(8f, 8f)
        shape.lineTo(7f, 7f)
        shape.close()

        assertEquals(3, shape.pointCount)
        assertEquals(9f, shape.xAt(0), 0f)
        assertEquals(7f, shape.xAt(2), 0f)
    }

    @Test
    fun `growth past the initial capacity preserves every vertex in order`() {
        // The hill ridge is 67 vertices against a default capacity that does not start there, so the
        // grow path is on the normal route, not an edge case.
        val shape = SceneShape(initialCapacity = 4)
        val count = 67
        shape.moveTo(0f, 0f)
        for (i in 1 until count) shape.lineTo(i.toFloat(), i * 2f)
        assertEquals(count, shape.pointCount)
        for (i in 0 until count) {
            assertEquals(i.toFloat(), shape.xAt(i), 0f)
            assertEquals(i * 2f, shape.yAt(i), 0f)
        }
    }

    @Test
    fun `reset empties the shape`() {
        val shape = SceneShape()
        shape.moveTo(1f, 1f)
        shape.lineTo(2f, 2f)
        shape.reset()
        assertEquals(0, shape.pointCount)
    }

    @Test
    fun `a terrain shape keeps its first and last vertices on the base line`() {
        // The GPU backend fills a gradient terrain as columns down to shape.yAt(0), so the hill
        // ridge's own construction — base corner, ridge, base corner — is the shape of that
        // contract rather than an incidental detail of how it happens to be built.
        val shape = SceneShape()
        val baseY = 900f
        val startX = -540f
        val width = 2160f
        shape.moveTo(startX, baseY)
        val samples = 64
        for (i in 0..samples) {
            shape.lineTo(startX + (i / samples.toFloat()) * width, 500f)
        }
        shape.lineTo(startX + width, baseY)
        shape.close()

        assertEquals(samples + 3, shape.pointCount)
        assertEquals(baseY, shape.yAt(0), 0f)
        assertEquals(baseY, shape.yAt(shape.pointCount - 1), 0f)
        assertEquals(startX, shape.xAt(0), 0f)
        assertEquals(startX + width, shape.xAt(shape.pointCount - 1), 0f)
    }
}
