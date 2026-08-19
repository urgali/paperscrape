package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the off-screen culling predicate that replaced the hardcoded
 * `x < -200f || x > 3000f` skip in `SceneObjectRenderer.drawStaticObject`.
 *
 * The requirement is asymmetric, and these tests are written around that asymmetry:
 *
 *  - **Never clip early.** An object with even one pixel on screen must be drawn. Getting this
 *    wrong is a visible regression — objects popping out at a screen edge.
 *  - **Skip what cannot be seen.** Getting this wrong only costs wasted draw calls.
 *
 * So the predicate is deliberately inclusive at the boundary, and the "no early clipping" tests
 * below check the exact touching case rather than approximating it.
 */
class SceneObjectCullingTest {

    /** Typical scaled half-widths: MAX_OBJECT_HALF_WIDTH_UNITS (96) x a category's own scale x the
     * depth scale, which together span roughly 0.4x to 6x -- see [SceneSpace]. */
    private val smallestHalfWidth = 96f * 2f * 0.55f // farthest object  = 105.6
    private val largestHalfWidth = 96f * 2f * 1.30f  // nearest object   = 249.6

    private val screenWidths = floatArrayOf(720f, 1080f, 1440f, 2160f, 2560f, 3200f)

    // --- Basic visibility ---------------------------------------------------------------------

    @Test
    fun `object in the middle of the screen is visible`() {
        assertTrue(SceneObjectRenderer.isHorizontallyVisible(540f, largestHalfWidth, 1080f))
    }

    @Test
    fun `object far off the left is culled`() {
        assertFalse(SceneObjectRenderer.isHorizontallyVisible(-5000f, largestHalfWidth, 1080f))
    }

    @Test
    fun `object far off the right is culled`() {
        assertFalse(SceneObjectRenderer.isHorizontallyVisible(5000f, largestHalfWidth, 1080f))
    }

    // --- No early clipping --------------------------------------------------------------------

    @Test
    fun `object just touching the left edge is still drawn`() {
        val halfWidth = largestHalfWidth
        // Right edge of the object lands exactly on x = 0.
        assertTrue(SceneObjectRenderer.isHorizontallyVisible(-halfWidth, halfWidth, 1080f))
        // One unit further on screen: definitely drawn.
        assertTrue(SceneObjectRenderer.isHorizontallyVisible(-halfWidth + 1f, halfWidth, 1080f))
    }

    @Test
    fun `object just touching the right edge is still drawn`() {
        val halfWidth = largestHalfWidth
        val screenWidth = 1080f
        assertTrue(SceneObjectRenderer.isHorizontallyVisible(screenWidth + halfWidth, halfWidth, screenWidth))
        assertTrue(SceneObjectRenderer.isHorizontallyVisible(screenWidth + halfWidth - 1f, halfWidth, screenWidth))
    }

    @Test
    fun `object one unit beyond either edge is culled`() {
        val halfWidth = largestHalfWidth
        val screenWidth = 1080f
        assertFalse(SceneObjectRenderer.isHorizontallyVisible(-halfWidth - 1f, halfWidth, screenWidth))
        assertFalse(SceneObjectRenderer.isHorizontallyVisible(screenWidth + halfWidth + 1f, halfWidth, screenWidth))
    }

    @Test
    fun `visibility is continuous as an object scrolls across the screen`() {
        // Walk an object from well off the left to well off the right and assert the visible
        // range is one unbroken interval: no flicker, no gap, no early disappearance.
        val halfWidth = largestHalfWidth
        val screenWidth = 1080f
        var seenVisible = false
        var seenHiddenAfterVisible = false

        var x = -2000f
        while (x <= 3200f) {
            val visible = SceneObjectRenderer.isHorizontallyVisible(x, halfWidth, screenWidth)
            if (visible) {
                assertFalse("visibility toggled back on after ending at x=$x", seenHiddenAfterVisible)
                seenVisible = true
            } else if (seenVisible) {
                seenHiddenAfterVisible = true
            }
            x += 1f
        }
        assertTrue("object was never visible anywhere", seenVisible)
        assertTrue("object never left the screen", seenHiddenAfterVisible)
    }

    // --- Relationship with the v73 behaviour it replaces ---------------------------------------

    /** The predicate this replaced, kept here purely so the comparison below is exact. */
    private fun legacyVisible(x: Float): Boolean = !(x < -200f || x > 3000f)

    @Test
    fun `new culling draws everything the old constant drew that could actually be seen`() {
        // The safety property: for every position and every screen size, if an object has any
        // pixel on screen then the new predicate keeps it. Framed against the old behaviour so a
        // regression relative to v73 is impossible to miss.
        for (screenWidth in screenWidths) {
            for (halfWidth in floatArrayOf(smallestHalfWidth, largestHalfWidth)) {
                var x = -3000f
                while (x <= 6000f) {
                    val actuallyOnScreen = (x + halfWidth) >= 0f && (x - halfWidth) <= screenWidth
                    if (actuallyOnScreen) {
                        assertTrue(
                            "clipped a visible object at x=$x halfWidth=$halfWidth screenWidth=$screenWidth",
                            SceneObjectRenderer.isHorizontallyVisible(x, halfWidth, screenWidth),
                        )
                    }
                    x += 7f
                }
            }
        }
    }

    @Test
    fun `new culling is more permissive than the old constant at the left edge`() {
        // The old skip cut at a fixed -200. The nearest, largest objects extend 249.6 units
        // either side, so the old constant could clip a large object that still had pixels on
        // screen. The new predicate does not.
        val x = -240f
        assertFalse("old behaviour clipped it", legacyVisible(x))
        assertTrue("new behaviour must keep it", SceneObjectRenderer.isHorizontallyVisible(x, largestHalfWidth, 1080f))
    }

    @Test
    fun `new culling skips the dead zone the old constant kept drawing`() {
        // On a 1080px phone the old constant kept drawing everything out to x = 3000, roughly
        // 1700px of guaranteed-invisible objects, every frame, for every wrap tile.
        val screenWidth = 1080f
        var wastedPositions = 0
        var x = screenWidth + largestHalfWidth + 1f
        while (x <= 3000f) {
            assertTrue("old behaviour drew it", legacyVisible(x))
            assertFalse(
                "new behaviour must skip it",
                SceneObjectRenderer.isHorizontallyVisible(x, largestHalfWidth, screenWidth),
            )
            wastedPositions++
            x += 1f
        }
        assertTrue("expected a substantial skipped range, got $wastedPositions", wastedPositions > 1000)
    }

    @Test
    fun `wide displays no longer lose objects the old constant would have culled`() {
        // A display wider than 3000px made the old constant cull genuinely visible objects.
        val screenWidth = 3200f
        val x = 3100f
        assertFalse("old behaviour would have culled a visible object", legacyVisible(x))
        assertTrue(
            "new behaviour must draw it",
            SceneObjectRenderer.isHorizontallyVisible(x, largestHalfWidth, screenWidth),
        )
    }

    // --- Degenerate inputs ----------------------------------------------------------------------

    @Test
    fun `zero half width behaves like a point test`() {
        assertTrue(SceneObjectRenderer.isHorizontallyVisible(0f, 0f, 1080f))
        assertTrue(SceneObjectRenderer.isHorizontallyVisible(1080f, 0f, 1080f))
        assertFalse(SceneObjectRenderer.isHorizontallyVisible(-1f, 0f, 1080f))
        assertFalse(SceneObjectRenderer.isHorizontallyVisible(1081f, 0f, 1080f))
    }

    @Test
    fun `an object wider than the screen is always visible while it overlaps`() {
        val halfWidth = 4000f
        assertTrue(SceneObjectRenderer.isHorizontallyVisible(540f, halfWidth, 1080f))
        assertTrue(SceneObjectRenderer.isHorizontallyVisible(-3000f, halfWidth, 1080f))
        assertFalse(SceneObjectRenderer.isHorizontallyVisible(-4001f, halfWidth, 1080f))
    }

    @Test
    fun `measured object extent stays within the declared bound`() {
        // MAX_OBJECT_HALF_WIDTH_UNITS is documented as an upper bound derived from measuring the
        // widest sprite blit (house_large_roof, +/-75 units) and the widest procedural primitive
        // (skyscraper ground shadow, +/-54). Pin that relationship so shrinking the constant
        // below the measured extent fails here rather than silently clipping on a device.
        val widestMeasuredExtent = 75f
        assertTrue(
            "MAX_OBJECT_HALF_WIDTH_UNITS must stay above the measured extent",
            SceneObjectRenderer.MAX_OBJECT_HALF_WIDTH_UNITS >= widestMeasuredExtent,
        )
    }
}
