package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The GPU backend's transform arithmetic, pinned against the `Canvas` semantics it has to reproduce.
 *
 * This is the one piece of the migration whose failure mode is entirely silent. A wrong sign in
 * [SceneTransform.rotate] mirrors the waning moon instead of turning it; a wrong operand order in
 * [SceneTransform.translate] applies a sprite's origin in its parent's space instead of its own.
 * Neither throws, neither changes a test that exists, and the rendering paths themselves are not
 * covered by any JVM test in this project — so these assertions are the only automated statement
 * that the GPU backend places anything where the `Canvas` backend placed it.
 *
 * The expectations are hand-derived from the documented `Canvas` contract rather than compared
 * against `android.graphics.Matrix`, which under local unit tests resolves to the stubbed
 * `android.jar` and would assert nothing.
 */
class SceneTransformTest {

    private val tolerance = 1e-4f

    private fun assertMaps(
        transform: SceneTransform,
        x: Float,
        y: Float,
        expectedX: Float,
        expectedY: Float,
        message: String = "",
    ) {
        assertEquals("$message x", expectedX, transform.mapX(x, y), tolerance)
        assertEquals("$message y", expectedY, transform.mapY(x, y), tolerance)
    }

    @Test
    fun `identity maps a point to itself`() {
        val t = SceneTransform()
        assertMaps(t, 12f, -7f, 12f, -7f)
    }

    @Test
    fun `translate offsets the point`() {
        val t = SceneTransform()
        t.translate(10f, 20f)
        assertMaps(t, 3f, 4f, 13f, 24f)
    }

    @Test
    fun `scale multiplies the point about the origin`() {
        val t = SceneTransform()
        t.scale(2f, 3f)
        assertMaps(t, 5f, 5f, 10f, 15f)
    }

    @Test
    fun `rotate turns clockwise on screen`() {
        // Y points down, so the positive direction takes +x towards +y. This is the sign that
        // decides whether the waning moon is rotated or mirrored.
        val t = SceneTransform()
        t.rotate(90f)
        assertMaps(t, 1f, 0f, 0f, 1f, "x axis after 90deg")
        assertMaps(t, 0f, 1f, -1f, 0f, "y axis after 90deg")
    }

    @Test
    fun `two rotations compose into their sum`() {
        // A rotation applied to an already-rotated basis is the only case that exercises the cross
        // term feeding `a`: from an axis-aligned state `c` is zero, so a sign error there is
        // invisible. Mutation testing found exactly that gap.
        val composed = SceneTransform()
        composed.rotate(30f)
        composed.rotate(60f)
        val direct = SceneTransform()
        direct.rotate(90f)
        assertEquals(direct.mapX(13f, 5f), composed.mapX(13f, 5f), tolerance)
        assertEquals(direct.mapY(13f, 5f), composed.mapY(13f, 5f), tolerance)
    }

    @Test
    fun `a rotation composed with its inverse is the identity`() {
        val t = SceneTransform()
        t.rotate(47f)
        t.rotate(-47f)
        assertMaps(t, 9f, -4f, 9f, -4f)
    }

    @Test
    fun `rotate by 180 negates both axes`() {
        val t = SceneTransform()
        t.rotate(180f)
        // The moon's waning phases reuse the waxing sprites through exactly this rotation.
        assertMaps(t, 7f, -3f, -7f, 3f)
    }

    @Test
    fun `scale applies in the space established by an earlier translate`() {
        // Canvas post-multiplies: the scale acts on the already-translated space, so the origin
        // does not move and only the offset from it is scaled.
        val t = SceneTransform()
        t.translate(100f, 50f)
        t.scale(2f, 2f)
        assertMaps(t, 0f, 0f, 100f, 50f, "local origin is unmoved")
        assertMaps(t, 10f, 10f, 120f, 70f, "local offset is scaled")
    }

    @Test
    fun `translate applies in the space established by an earlier scale`() {
        val t = SceneTransform()
        t.scale(3f, 3f)
        t.translate(10f, 0f)
        // The translate is expressed in the scaled space, so it moves 30 device units, not 10.
        assertMaps(t, 0f, 0f, 30f, 0f)
    }

    @Test
    fun `translate after rotate moves along the rotated axes`() {
        val t = SceneTransform()
        t.rotate(90f)
        t.translate(10f, 0f)
        assertMaps(t, 0f, 0f, 0f, 10f)
    }

    @Test
    fun `the sprite blitter's scale-then-offset composition lands where Canvas would`() {
        // The exact shape of every SCENE_UNITS blit: the caller positions and scales, then the
        // blitter divides the 3x oversample back out and pre-multiplies the origin by the same
        // factor. The two must cancel, or every scene sprite moves.
        val t = SceneTransform()
        t.translate(400f, 300f)
        t.scale(2f, 2f)
        t.scale(1f / SpriteBlitter.SPRITE_PIXELS_PER_UNIT, 1f / SpriteBlitter.SPRITE_PIXELS_PER_UNIT)
        val originUnits = -45f
        val premultiplied = originUnits * SpriteBlitter.SPRITE_PIXELS_PER_UNIT
        assertMaps(t, premultiplied, premultiplied, 400f - 90f, 300f - 90f)
    }

    @Test
    fun `restore returns the exact transform that was saved`() {
        val t = SceneTransform()
        t.translate(11f, 13f)
        // The saved state carries a rotation *and* a non-uniform scale on purpose, so that its two
        // off-diagonal terms differ. Saving an axis-aligned state instead would leave both zero,
        // and a restore that put them back in the wrong slots would still compare equal.
        t.rotate(23f)
        t.scale(1.5f, 2.5f)
        val expectedX = t.mapX(4f, 6f)
        val expectedY = t.mapY(4f, 6f)
        t.save()
        t.rotate(37f)
        t.translate(-99f, 250f)
        t.scale(0.1f, 8f)
        t.restore()
        assertEquals(expectedX, t.mapX(4f, 6f), tolerance)
        assertEquals(expectedY, t.mapY(4f, 6f), tolerance)
    }

    @Test
    fun `nested save and restore unwind in order`() {
        val t = SceneTransform()
        t.save()
        t.translate(10f, 0f)
        t.save()
        t.translate(10f, 0f)
        assertMaps(t, 0f, 0f, 20f, 0f, "two levels deep")
        t.restore()
        assertMaps(t, 0f, 0f, 10f, 0f, "one level deep")
        t.restore()
        assertMaps(t, 0f, 0f, 0f, 0f, "back at the root")
        assertEquals(0, t.saveDepth)
    }

    @Test
    fun `an unmatched restore leaves the transform alone`() {
        // A draw path that restores more than it saved must not corrupt the frame that follows it.
        val t = SceneTransform()
        t.translate(5f, 5f)
        t.restore()
        t.restore()
        assertMaps(t, 0f, 0f, 5f, 5f)
    }

    @Test
    fun `a save dropped by overflow has its restore dropped too`() {
        // The pairing has to be *counted*, not merely tolerated. Balancing the totals is not enough:
        // if an overflowed save's restore popped a real level, the transform would revert one draw
        // too early and everything after it in the frame would be positioned in the wrong space.
        val t = SceneTransform(maxDepth = 1)
        t.translate(100f, 0f)
        t.save()
        t.translate(10f, 0f)
        t.save() // overflows
        t.translate(1f, 0f)
        t.restore()
        assertMaps(t, 0f, 0f, 111f, 0f, "the dropped save's restore must change nothing")
        t.restore()
        assertMaps(t, 0f, 0f, 100f, 0f, "the real level is still there to come back to")
    }

    @Test
    fun `saves beyond the stack depth still pair with their restores`() {
        // Overflow is dropped rather than thrown, so the pairing has to be counted: if an overflowed
        // save's restore popped a real level instead, every subsequent draw in the frame would be
        // transformed by the wrong matrix.
        val t = SceneTransform(maxDepth = 2)
        t.translate(100f, 0f)
        repeat(5) {
            t.save()
            t.translate(1f, 0f)
        }
        repeat(5) { t.restore() }
        assertMaps(t, 0f, 0f, 100f, 0f)
        assertEquals(0, t.saveDepth)
    }

    @Test
    fun `uniformScale reports the factor curves are tessellated against`() {
        val t = SceneTransform()
        assertEquals(1f, t.uniformScale(), tolerance)
        t.scale(4f, 4f)
        assertEquals(4f, t.uniformScale(), tolerance)
        t.scale(1f / SpriteBlitter.SPRITE_PIXELS_PER_UNIT, 1f / SpriteBlitter.SPRITE_PIXELS_PER_UNIT)
        assertEquals(4f / 3f, t.uniformScale(), tolerance)
    }

    @Test
    fun `uniformScale survives rotation and mirroring`() {
        // Mirroring is how every leftward walker, bird and sleigh is drawn, and it makes the
        // determinant negative; a scale factor taken from it without the absolute value would be
        // NaN and every curve would collapse to the minimum segment count.
        val t = SceneTransform()
        t.scale(2f, 2f)
        t.rotate(41f)
        assertEquals(2f, t.uniformScale(), tolerance)
        t.scale(-1f, 1f)
        assertEquals(2f, t.uniformScale(), tolerance)
        assertTrue(t.uniformScale() > 0f)
    }

    @Test
    fun `a mirrored transform still maps points`() {
        val t = SceneTransform()
        t.translate(50f, 0f)
        t.scale(-1f, 1f)
        assertMaps(t, 10f, 0f, 40f, 0f)
    }
}
