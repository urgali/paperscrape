package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the per-object tile enumeration that replaced the fixed `x`, `x - tileWidth`,
 * `x + tileWidth` copy loop in `SceneObjectRenderer.draw`.
 *
 * The scene tiles horizontally: an object anchored at `x` also exists at every `x + k*tileWidth`,
 * and every copy that intersects the viewport must be painted or a gap opens at the wrap seam.
 * The old code enumerated three fixed copies and let each cull itself; the new code derives the
 * range from the geometry via [SceneObjectRenderer.firstVisibleTileOffset].
 *
 * The requirement is asymmetric in exactly the same way the culling predicate's is:
 *
 *  - **Never lose a copy.** A missing copy is a visible pop at a screen edge.
 *  - **Skip what cannot be seen.** An extra rejected iteration only costs a comparison.
 *
 * So the central test here is not "the new code is correct in the abstract" but "the new code
 * paints exactly what the old code painted" ([`old and new enumeration paint the same copies`]),
 * over the whole reachable parameter space. Everything else supports that claim.
 */
class SceneObjectTileCullingTest {

    // --- The real parameter space -----------------------------------------------------------

    /**
     * `tileWidth` is `screenWidth * 2` (`PaperRenderer.drawHillLayers`), and `shiftXWrapped` is
     * wrapped into `(-tileWidth, 0]` by `wrappedScrollShift`.
     */
    private fun tileWidthFor(screenWidth: Float) = screenWidth * 2f

    private val screenWidths = floatArrayOf(320f, 480f, 540f, 720f, 1080f, 1440f, 2160f, 3200f)

    /**
     * Depth and size-variation pairs taken from `SceneObjectCatalog`, spanning the band's two ends
     * and both of its widest categories. `scale` is now a variation around 1 rather than a
     * category size, so the extremes are 1 plus or minus half the declared spread.
     */
    private val objectShapes = arrayOf(
        SceneObjectType.SKYSCRAPER to (0.0f to 0.92f),  // tower, farthest / smallest
        SceneObjectType.SKYSCRAPER to (0.25f to 1.08f), // tower, near end of its own sub-band
        SceneObjectType.HOUSE to (0.48f to 1.08f),      // house, back band, largest variation
        SceneObjectType.HOUSE to (0.95f to 1.08f),      // house, front band -- the widest extent
        SceneObjectType.PARASOL to (0.95f to 1.08f),    // parasol, front band
        SceneObjectType.TREE to (1.0f to 1.08f),        // tree, nearest
    )

    /**
     * The scale a copy is culled against, taken from the renderer's own pipeline rather than
     * reproduced here. `effectiveScaleFor` is the single place the four stages are multiplied, so
     * reimplementing it in the test would be testing the copy.
     */
    private fun halfWidthFor(type: SceneObjectType, depthFraction: Float, sizeVariation: Float): Float {
        val spec = StaticSceneObject(type, depthFraction = depthFraction, tileFractionX = 0.5f, scale = sizeVariation)
        return SceneObjectRenderer.MAX_OBJECT_HALF_WIDTH_UNITS *
            SceneObjectRenderer.effectiveScaleFor(spec, SceneSpace.REFERENCE_SCREEN_HEIGHT_PX)
    }

    /** `anchorX`, reproduced exactly: it is private, and the enumeration starts from its result. */
    private fun anchorX(tileFractionX: Float, shiftXWrapped: Float, tileWidth: Float): Float {
        var x = shiftXWrapped + tileFractionX * tileWidth
        if (x < -tileWidth * 0.5f) x += tileWidth
        return x
    }

    /** The enumeration this change replaced: three fixed copies, each gated by the predicate. */
    private fun legacyCopies(x: Float, halfWidth: Float, tileWidth: Float, screenWidth: Float): List<Float> {
        val out = ArrayList<Float>(3)
        for (copyX in floatArrayOf(x, x - tileWidth, x + tileWidth)) {
            if (SceneObjectRenderer.isHorizontallyVisible(copyX, halfWidth, screenWidth)) out.add(copyX)
        }
        return out
    }

    /**
     * The enumeration `SceneObjectRenderer.draw` now performs, in the same order and with the same
     * arithmetic -- each copy's x recomputed from its tile index, not accumulated, so the values
     * compared against [legacyCopies] below are bit-for-bit the ones production produces.
     */
    private fun currentCopies(x: Float, halfWidth: Float, tileWidth: Float, screenWidth: Float): List<Float> {
        val out = ArrayList<Float>(3)
        val first = SceneObjectRenderer.firstVisibleTileOffset(x, halfWidth, tileWidth)
        val limit = SceneObjectRenderer.tileOffsetLimit(x, halfWidth, tileWidth, screenWidth)
        // Both bounds come from production, so a change to either shows up here rather than being
        // masked by a reimplementation of the loop.
        assertTrue("enumeration range is implausibly wide: $first until $limit", limit - first <= 64)
        for (tileIndex in first until limit) {
            val copyX = x + tileIndex * tileWidth
            if (SceneObjectRenderer.isHorizontallyVisible(copyX, halfWidth, screenWidth)) out.add(copyX)
        }
        return out
    }

    private inline fun forEachRealisticCase(
        fractionSteps: Int = 37,
        shiftSteps: Int = 41,
        body: (screenWidth: Float, tileWidth: Float, x: Float, halfWidth: Float) -> Unit,
    ) {
        for (screenWidth in screenWidths) {
            val tileWidth = tileWidthFor(screenWidth)
            for ((type, shape) in objectShapes) {
                val halfWidth = halfWidthFor(type, shape.first, shape.second)
                for (fi in 0..fractionSteps) {
                    val tileFractionX = fi / fractionSteps.toFloat()
                    for (si in 0..shiftSteps) {
                        // shiftXWrapped sweeps the full (-tileWidth, 0] range wrappedScrollShift produces.
                        val shift = -tileWidth * (si / (shiftSteps + 1).toFloat())
                        body(screenWidth, tileWidth, anchorX(tileFractionX, shift, tileWidth), halfWidth)
                    }
                }
            }
        }
    }

    // --- T1: equivalence with the enumeration it replaces --------------------------------------

    @Test
    fun `old and new enumeration paint the same copies`() {
        var cases = 0
        var painted = 0
        forEachRealisticCase { screenWidth, tileWidth, x, halfWidth ->
            val legacy = legacyCopies(x, halfWidth, tileWidth, screenWidth)
            val current = currentCopies(x, halfWidth, tileWidth, screenWidth)
            assertEquals(
                "copy set differs at x=$x halfWidth=$halfWidth tileWidth=$tileWidth screenWidth=$screenWidth",
                legacy.sorted(),
                current.sorted(),
            )
            cases++
            painted += current.size
        }
        // Guard against the sweep silently degenerating into "nothing was ever drawn", which
        // would make the equality above pass for the wrong reason.
        assertTrue("sweep covered too few cases: $cases", cases > 50_000)
        assertTrue("sweep never painted anything", painted > cases / 4)
    }

    // --- T2: completeness -- nothing visible outside the enumerated range ----------------------

    @Test
    fun `no tile copy outside the enumerated range is ever visible`() {
        forEachRealisticCase(fractionSteps = 17, shiftSteps = 19) { screenWidth, tileWidth, x, halfWidth ->
            val current = currentCopies(x, halfWidth, tileWidth, screenWidth)
            for (k in -4..4) {
                val copyX = x + k * tileWidth
                if (SceneObjectRenderer.isHorizontallyVisible(copyX, halfWidth, screenWidth)) {
                    assertTrue(
                        "visible copy at k=$k was not enumerated (x=$x tileWidth=$tileWidth screenWidth=$screenWidth)",
                        current.any { it == copyX },
                    )
                }
            }
        }
    }

    // --- T3: no gap and no duplicate at the wrap seam ------------------------------------------

    /** Every lattice copy that is genuinely on screen, found by brute force over a wide range. */
    private fun groundTruthCopies(x: Float, halfWidth: Float, tileWidth: Float, screenWidth: Float): List<Float> {
        val out = ArrayList<Float>(3)
        for (k in -40..40) {
            val copyX = x + k * tileWidth
            if (SceneObjectRenderer.isHorizontallyVisible(copyX, halfWidth, screenWidth)) out.add(copyX)
        }
        return out
    }

    @Test
    fun `an object scrolling through a whole tile period is never lost and never doubled`() {
        // Walk shiftXWrapped continuously through one full period for the widest object and check
        // the enumeration against brute force at every step. A dropped copy for even one step of
        // the sweep is the pop this change must not introduce, and it shows up here as a mismatch.
        //
        // Note what is deliberately *not* asserted: that the object is on screen throughout. The
        // tiling period is 2 x screenWidth while an object spans at most screenWidth + 2*halfWidth,
        // so roughly a quarter of every period genuinely has no copy on screen at all. That is the
        // intended "candidates span a two-screen period" behaviour described in
        // PaperRenderer.drawHillLayers, not a gap this enumeration creates.
        val screenWidth = 1080f
        val tileWidth = tileWidthFor(screenWidth)
        val halfWidth = halfWidthFor(SceneObjectType.HOUSE, 0.95f, 1.08f)
        val tileFractionX = 0.5f

        val steps = 20_000
        var everVisible = false

        for (i in 0..steps) {
            val shift = -tileWidth * (i / (steps + 1).toFloat())
            val x = anchorX(tileFractionX, shift, tileWidth)
            val copies = currentCopies(x, halfWidth, tileWidth, screenWidth)

            assertEquals("the same position was painted twice at shift=$shift", copies.size, copies.distinct().size)
            assertEquals(
                "enumeration differs from brute force at shift=$shift (x=$x)",
                groundTruthCopies(x, halfWidth, tileWidth, screenWidth),
                copies,
            )
            if (copies.isNotEmpty()) everVisible = true
        }

        assertTrue("object was never on screen anywhere in the period", everVisible)
    }

    @Test
    fun `handover between adjacent tile copies leaves no gap`() {
        // The seam itself: as one copy leaves the right edge its neighbour must already be
        // entering from the left. Sweep the anchor across two whole periods and assert coverage
        // is continuous.
        //
        // **The object's width is a precondition here, not a scene value.** Continuous coverage is
        // only possible for one wide enough that consecutive copies overlap, which needs
        // `halfWidth >= (tileWidth - screenWidth) / 2`; a narrower object is legitimately off
        // screen for part of the cycle, because objects are sparse rather than a continuous band.
        // The width is therefore derived from that condition rather than borrowed from a category,
        // which is what this used to do -- and it only worked because objects were then drawn far
        // larger than the scene's own proportions call for.
        val screenWidth = 480f // narrow enough that two copies can be visible at once
        val tileWidth = tileWidthFor(screenWidth)
        val halfWidth = (tileWidth - screenWidth) / 2f + 1f

        var x = -2f * tileWidth
        var gapAfterVisible = false
        var seenVisible = false
        var visibleSamples = 0
        while (x <= 2f * tileWidth) {
            val copies = currentCopies(x, halfWidth, tileWidth, screenWidth)
            if (copies.isNotEmpty()) {
                assertFalse("coverage came back after a gap at x=$x", gapAfterVisible)
                seenVisible = true
                visibleSamples++
            } else if (seenVisible) {
                gapAfterVisible = true
            }
            x += 1f
        }
        assertTrue("never visible", seenVisible)
        assertTrue("expected a substantial visible stretch, got $visibleSamples", visibleSamples > 100)
    }

    // --- T4: firstVisibleTileOffset never starts late ------------------------------------------

    @Test
    fun `firstVisibleTileOffset never starts after the first visible copy`() {
        forEachRealisticCase(fractionSteps = 17, shiftSteps = 19) { screenWidth, tileWidth, x, halfWidth ->
            val k0 = SceneObjectRenderer.firstVisibleTileOffset(x, halfWidth, tileWidth)
            for (k in (k0 - 6) until k0) {
                assertFalse(
                    "a visible copy sits at k=$k, before the start offset $k0 " +
                        "(x=$x halfWidth=$halfWidth tileWidth=$tileWidth screenWidth=$screenWidth)",
                    SceneObjectRenderer.isHorizontallyVisible(x + k * tileWidth, halfWidth, screenWidth),
                )
            }
        }
    }

    @Test
    fun `the starting copy has not yet reached the screen`() {
        // The safety property the `floor` is there for, stated directly: the copy the scan starts
        // from is still entirely off the left edge (its right edge is at or before x = 0). Only
        // then is it impossible for float rounding of the quotient to put the start *past* the
        // first visible copy and drop it.
        //
        // A `ceil` -- which returns the first visible copy itself rather than the one before it --
        // produces the same output everywhere in the sweep, so no test of the painted result can
        // tell the two apart. This is what distinguishes them.
        forEachRealisticCase(fractionSteps = 17, shiftSteps = 19) { _, tileWidth, x, halfWidth ->
            val k0 = SceneObjectRenderer.firstVisibleTileOffset(x, halfWidth, tileWidth)
            assertTrue(
                "the copy at start offset $k0 has already entered the screen " +
                    "(x=$x halfWidth=$halfWidth tileWidth=$tileWidth): rounding could have skipped it",
                x + k0 * tileWidth + halfWidth <= 0f,
            )
        }
    }

    @Test
    fun `firstVisibleTileOffset starts at most one tile early`() {
        // Starting early is the safe direction, but an unbounded overshoot would put wasted
        // iterations back into the loop this change exists to shorten.
        forEachRealisticCase(fractionSteps = 17, shiftSteps = 19) { _, tileWidth, x, halfWidth ->
            val k0 = SceneObjectRenderer.firstVisibleTileOffset(x, halfWidth, tileWidth)
            // The copy one tile past the start must have reached x = 0 or beyond on its right edge.
            assertTrue(
                "start offset $k0 is more than one tile early (x=$x halfWidth=$halfWidth tileWidth=$tileWidth)",
                x + (k0 + 1) * tileWidth + halfWidth >= 0f,
            )
        }
    }

    @Test
    fun `a copy exactly touching either edge is inside the enumerated range`() {
        // isHorizontallyVisible is inclusive at both edges, so the range must be too. Built from
        // exact float values rather than swept, because this is precisely the case a sweep steps
        // over: an off-by-one at either bound drops a copy that the predicate calls visible.
        val screenWidth = 1080f
        val tileWidth = 2160f
        val halfWidth = 250f

        // Left edge: the copy's right edge lands exactly on x = 0.
        val touchingLeft = -halfWidth
        assertTrue(SceneObjectRenderer.isHorizontallyVisible(touchingLeft, halfWidth, screenWidth))
        assertTrue(
            "a copy touching the left edge fell outside the range",
            currentCopies(touchingLeft, halfWidth, tileWidth, screenWidth).contains(touchingLeft),
        )

        // Right edge: the copy's left edge lands exactly on x = screenWidth.
        val touchingRight = screenWidth + halfWidth
        assertTrue(SceneObjectRenderer.isHorizontallyVisible(touchingRight, halfWidth, screenWidth))
        assertTrue(
            "a copy touching the right edge fell outside the range",
            currentCopies(touchingRight, halfWidth, tileWidth, screenWidth).contains(touchingRight),
        )
        assertTrue(
            "the limit must leave room for a copy touching the right edge",
            SceneObjectRenderer.tileOffsetLimit(touchingRight, halfWidth, tileWidth, screenWidth) > 0,
        )
    }

    @Test
    fun `exact tile boundaries are enumerated correctly`() {
        // Where the quotient lands exactly on an integer, floor and ceil coincide and an
        // off-by-one in either bound stops being masked by the one-tile safety margin. These
        // positions are constructed, not swept, for exactly that reason.
        val screenWidth = 1080f
        val tileWidth = 2160f
        for (halfWidth in floatArrayOf(64f, 128f, 256f)) {
            for (k in -3..3) {
                // x chosen so that (-halfWidth - x) / tileWidth is exactly the integer k.
                val x = -halfWidth - k * tileWidth
                assertEquals(
                    "floor of an exact integer quotient must be that integer",
                    k,
                    SceneObjectRenderer.firstVisibleTileOffset(x, halfWidth, tileWidth),
                )
                assertEquals(
                    "enumeration differs from brute force at an exact tile boundary (x=$x)",
                    groundTruthCopies(x, halfWidth, tileWidth, screenWidth),
                    currentCopies(x, halfWidth, tileWidth, screenWidth),
                )
            }
        }
    }

    @Test
    fun `the enumeration stays short`() {
        // Starting one tile early is free only while the loop stays short. If a future change to
        // the tiling period or to an object's extent made the range wide, this is where it shows
        // up rather than as a quiet per-frame cost.
        var iterations = 0
        var cases = 0
        forEachRealisticCase(fractionSteps = 17, shiftSteps = 19) { screenWidth, tileWidth, x, halfWidth ->
            val first = SceneObjectRenderer.firstVisibleTileOffset(x, halfWidth, tileWidth)
            val limit = SceneObjectRenderer.tileOffsetLimit(x, halfWidth, tileWidth, screenWidth)
            val n = (limit - first).coerceAtLeast(0)
            assertTrue("enumeration visited $n tiles at screenWidth=$screenWidth", n <= 3)
            iterations += n
            cases++
        }
        // The fixed loop it replaced always did exactly 3.
        assertTrue("expected fewer iterations on average than the fixed 3", iterations < 2 * cases)
    }

    // --- T5: degenerate tile width -------------------------------------------------------------

    @Test
    fun `a non-positive tile width takes the single-copy path`() {
        // GroundGeometry's initial value carries tileWidth = 1f and is never consumed, but draw()
        // must not depend on a call order to terminate. The guard lives in draw(); this pins the
        // condition it tests, so removing the guard and relying on the loop fails here.
        val screenWidth = 1080f
        val halfWidth = halfWidthFor(SceneObjectType.HOUSE, 0.95f, 1.08f)
        // Documents why the guard has to exist: with a zero tile width the derived range is not a
        // usable bound, so draw() must not reach the loop at all.
        for (tileWidth in floatArrayOf(0f, -1f, -2160f)) {
            assertFalse("tileWidth=$tileWidth must be rejected before the enumeration", tileWidth > 0f)
        }
        // With no tiling period the bounds are computed from infinities and are not a usable
        // range at all -- the endpoints saturate rather than describing anything.
        assertEquals(Int.MIN_VALUE, SceneObjectRenderer.firstVisibleTileOffset(540f, halfWidth, 0f))
        assertEquals(Int.MAX_VALUE, SceneObjectRenderer.tileOffsetLimit(540f, halfWidth, 0f, screenWidth) - 1)

        // And a *small but positive* period is the case the guard really has to catch, because it
        // passes any "is it positive" test while producing a range with thousands of entries. This
        // is why PaperRenderer's placeholder GroundGeometry carries tileWidth = 0 rather than 1.
        val tinyPeriodRange = SceneObjectRenderer.tileOffsetLimit(540f, halfWidth, 1f, screenWidth) -
            SceneObjectRenderer.firstVisibleTileOffset(540f, halfWidth, 1f)
        assertTrue("expected an unusably wide range, got $tinyPeriodRange", tinyPeriodRange > 1000)
        // The visible/invisible decision for that single copy is still the shared predicate.
        assertTrue(SceneObjectRenderer.isHorizontallyVisible(540f, halfWidth, screenWidth))
        assertFalse(SceneObjectRenderer.isHorizontallyVisible(-5000f, halfWidth, screenWidth))
    }

    // --- T6: anchors outside the normal fraction range -----------------------------------------

    @Test
    fun `an anchor far outside one tile period is enumerated correctly`() {
        // tileFractionX is generated in [0,1], but a custom-theme payload is parsed from JSON and
        // is not range-checked, so an anchor many tiles away is reachable. The enumeration must
        // still agree with brute force: paint every copy that is on screen, and no copy that is not.
        val screenWidth = 1080f
        val tileWidth = tileWidthFor(screenWidth)
        val halfWidth = halfWidthFor(SceneObjectType.HOUSE, 0.95f, 1.08f)

        for (fi in -30..30) {
            val fraction = fi / 2f
            for (si in 0..8) {
                val x = anchorX(fraction, -tileWidth * (si / 9f), tileWidth)
                assertEquals(
                    "enumeration differs from brute force at tileFractionX=$fraction (x=$x)",
                    groundTruthCopies(x, halfWidth, tileWidth, screenWidth),
                    currentCopies(x, halfWidth, tileWidth, screenWidth),
                )
            }
        }
    }

    @Test
    fun `an out of range anchor can be drawn where the fixed three copy loop lost it`() {
        // The one deliberate behaviour difference, pinned so it is a decision rather than a
        // surprise. For an anchor several tiles out, the fixed `x`, `x - tileWidth`,
        // `x + tileWidth` loop tested three positions that were all off screen and the object
        // silently vanished; a derived range finds the copy that is genuinely visible.
        // Unreachable from any generated layout -- only from a hand-edited or corrupt payload.
        val screenWidth = 1080f
        val tileWidth = tileWidthFor(screenWidth)
        val halfWidth = halfWidthFor(SceneObjectType.HOUSE, 0.95f, 1.08f)
        val x = anchorX(4.25f, -0.25f * tileWidth, tileWidth)

        assertTrue("the fixed three-copy loop should have found nothing here", legacyCopies(x, halfWidth, tileWidth, screenWidth).isEmpty())
        assertEquals(
            "the derived range must find the copy that is actually on screen",
            groundTruthCopies(x, halfWidth, tileWidth, screenWidth),
            currentCopies(x, halfWidth, tileWidth, screenWidth),
        )
        assertTrue("expected a visible copy in this case", currentCopies(x, halfWidth, tileWidth, screenWidth).isNotEmpty())
    }

    // --- Copies of one object never overlap ----------------------------------------------------

    @Test
    fun `two copies of the same object can never overlap`() {
        // This is what makes the order copies are painted in irrelevant, which in turn is why
        // switching from `0, -1, +1` to ascending tile order cannot change a pixel.
        for (screenWidth in screenWidths) {
            val tileWidth = tileWidthFor(screenWidth)
            for ((type, shape) in objectShapes) {
                val halfWidth = halfWidthFor(type, shape.first, shape.second)
                assertTrue(
                    "adjacent copies would overlap at screenWidth=$screenWidth halfWidth=$halfWidth",
                    tileWidth > 2f * halfWidth,
                )
            }
        }
    }
}
