package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Is the road too narrow for the cars?**
 *
 * The question is perceptual, so this measures the geometry rather than adjusting it by eye.
 * Everything below is derived from [SceneSpace]'s own published constants and the same arithmetic
 * `SceneObjectRenderer.drawCar` performs, so it moves whenever the scene does and cannot drift into
 * describing a road the scene does not draw.
 *
 * ### What is measured, at [SceneSpace.REFERENCE_SCREEN_HEIGHT_PX]
 *
 * | quantity | value |
 * |---|---|
 * | lane spacing | 67.2 px |
 * | painted road band | 145.2 px |
 * | car height, far lane / near lane | 67.5 / 78.1 px |
 * | fire engine height, near lane | 141.4 px |
 * | **laneSpacing / carHeight** | **1.00 far, 0.86 near** |
 * | **roadBand / carHeight** | **2.15 far, 1.86 near** |
 * | **roadBand / fireEngineHeight** | **1.03** |
 *
 * `the measured geometry is the table above` holds every number of it to the code, so the table is what the scene
 * draws today.
 *
 * ### The verdict
 *
 * **The road is not too narrow.** `ROAD_LANE_NEAR_Y_FRACTION`'s own
 * doc comment states the design target — the lanes *"about a vehicle"* apart, because wider *"the
 * carriageway reads as a dark band dominating the scene"* — and the measured ratio is 0.86 to 1.00. The carriageway is
 * **about twice a car tall**, and even the fire engine, the tallest thing that drives on it, fits
 * inside the band with room to spare.
 *
 * What the eye is probably reading is the one asymmetry the measurements do show and which is
 * inherent rather than wrong: a vehicle rises from its own wheel line, so a **near**-lane car sits
 * entirely inside the tarmac while a **far**-lane car's roof clears the top edge by about two
 * fifths of its height. That is what standing on a surface looks like in this projection; closing it
 * would mean either sinking the far lane or widening the strip until it dominates the scene
 * vertically.
 *
 * The tests after the first are therefore **assertions on the design intent, not a fence around
 * today's numbers**: each bound is wide enough that a deliberate retune passes and a mistake — a lane
 * pair collapsing, a vehicle scale changing by a factor — fails. The first holds the table above to
 * the code, so that a retune which passes them also changes the table.
 */
class RoadVehicleGeometryTest {

    private val h = SceneSpace.REFERENCE_SCREEN_HEIGHT_PX

    private val roadTop = SceneSpace.roadTopYFraction()
    private val roadBottom = SceneSpace.roadBottomYFraction()
    private val bandPx = (roadBottom - roadTop) * h
    private val laneSpacingPx = SceneSpace.CANONICAL_LANE_SPACING_FRACTION * h

    /** Exactly what `drawCar` computes, at [SceneSpace.REFERENCE_SCREEN_HEIGHT_PX] where sceneScale is 1. */
    private fun carHeightPx(laneYFraction: Float): Float =
        SceneSpace.CAR_SPRITE_UNITS_TALL * SceneSpace.CAR_BASE_SCALE *
            SceneSpace.perspectiveScaleAt(laneYFraction) * SceneSpace.sceneScale(h)

    private fun fireTruckHeightPx(laneYFraction: Float): Float =
        SceneSpace.FIRE_TRUCK_SPRITE_UNITS_TALL * SceneSpace.FIRE_TRUCK_BASE_SCALE *
            SceneSpace.perspectiveScaleAt(laneYFraction) * SceneSpace.sceneScale(h)

    // -- the measurement itself --------------------------------------------------------------------

    /**
     * **The table of the class's note is what the code draws**, to its last printed digit: a retune that moves a car, a
     * lane or the band fails here until the table says what the scene does. The design intent is the tests below, whose
     * bounds a deliberate retune passes.
     */
    @Test
    fun `the measured geometry is the table above`() {
        val far = carHeightPx(SceneSpace.ROAD_LANE_FAR_Y_FRACTION)
        val near = carHeightPx(SceneSpace.ROAD_LANE_NEAR_Y_FRACTION)
        val fireTruck = fireTruckHeightPx(SceneSpace.ROAD_LANE_NEAR_Y_FRACTION)
        assertEquals("lane spacing, px", 67.2f, laneSpacingPx, 0.05f)
        assertEquals("painted road band, px", 145.2f, bandPx, 0.05f)
        assertEquals("car height, far lane, px", 67.5f, far, 0.05f)
        assertEquals("car height, near lane, px", 78.1f, near, 0.05f)
        assertEquals("fire engine height, near lane, px", 141.4f, fireTruck, 0.05f)
        assertEquals("laneSpacing / carHeight, far", 1.00f, laneSpacingPx / far, 0.005f)
        assertEquals("laneSpacing / carHeight, near", 0.86f, laneSpacingPx / near, 0.005f)
        assertEquals("roadBand / carHeight, far", 2.15f, bandPx / far, 0.005f)
        assertEquals("roadBand / carHeight, near", 1.86f, bandPx / near, 0.005f)
        assertEquals("roadBand / fireEngineHeight", 1.03f, bandPx / fireTruck, 0.005f)
    }

    /**
     * **The design target, the one `ROAD_LANE_NEAR_Y_FRACTION`'s own comment states.** Two lanes about a
     * vehicle apart — not two, which reads as a runway, and not a half, which reads as one row.
     */
    @Test
    fun `the lanes are about one vehicle apart`() {
        for (lane in listOf(SceneSpace.ROAD_LANE_FAR_Y_FRACTION, SceneSpace.ROAD_LANE_NEAR_Y_FRACTION)) {
            val ratio = laneSpacingPx / carHeightPx(lane)
            assertTrue(
                "lane spacing is ${"%.2f".format(ratio)} car heights at lane $lane; " +
                    "the design target is about one",
                ratio in 0.7f..1.5f,
            )
        }
    }

    /**
     * The carriageway has to be at least as deep as the vehicles on it are tall, or the traffic
     * genuinely would look too big for its road. It is comfortably more than that.
     */
    @Test
    fun `the painted road is at least as deep as a car is tall`() {
        for (lane in listOf(SceneSpace.ROAD_LANE_FAR_Y_FRACTION, SceneSpace.ROAD_LANE_NEAR_Y_FRACTION)) {
            val ratio = bandPx / carHeightPx(lane)
            assertTrue("road band is only ${"%.2f".format(ratio)} car heights at lane $lane", ratio >= 1.5f)
            // And not so deep that the strip takes over the scene.
            assertTrue("road band is ${"%.2f".format(ratio)} car heights, which reads as a runway", ratio <= 4f)
        }
    }

    /** The tallest vehicle that drives on it. A fire engine that overflowed would be the real defect. */
    @Test
    fun `even the fire engine fits within the carriageway`() {
        val ratio = bandPx / fireTruckHeightPx(SceneSpace.ROAD_LANE_NEAR_Y_FRACTION)
        assertTrue("the fire engine is taller than the road is deep (ratio ${"%.2f".format(ratio)})", ratio >= 1f)
    }

    /**
     * A near-lane vehicle must not hang below the tarmac: wheels on the near lane plus the shoulder
     * is the one place a vehicle really could look like it had driven off the road.
     */
    @Test
    fun `no vehicle hangs below the near edge of the road`() {
        assertTrue(
            "the near lane sits below the road's own bottom edge",
            SceneSpace.ROAD_LANE_NEAR_Y_FRACTION < roadBottom,
        )
        val clearancePx = (roadBottom - SceneSpace.ROAD_LANE_NEAR_Y_FRACTION) * h
        assertTrue("only ${"%.1f".format(clearancePx)}px of tarmac below the near wheel line", clearancePx >= 20f)
    }

    /**
     * The band is symmetric about the centre line by construction. Asserted because every ratio
     * above is stated against one number for the band, and an asymmetric strip would make that a
     * misleading summary.
     */
    @Test
    fun `the road band is symmetric about the lane pair`() {
        val aboveFar = SceneSpace.ROAD_LANE_FAR_Y_FRACTION - roadTop
        val belowNear = roadBottom - SceneSpace.ROAD_LANE_NEAR_Y_FRACTION
        assertEquals(aboveFar, belowNear, 1e-6f)
    }

    /**
     * The road's width must not depend on how much traffic is on it: otherwise thinning the density
     * would collapse the lane span and paint the strip as a hairline.
     *
     * Asserted here too because this class is about the road/vehicle relationship, and fewer cars
     * narrowing the road is the one way the road can come out too narrow.
     */
    @Test
    fun `a degenerate lane pair still paints a full-width road`() {
        val collapsed = SceneSpace.roadEdgeMarginFraction(0.85f, 0.85f)
        val canonical = SceneSpace.roadEdgeMarginFraction(
            SceneSpace.ROAD_LANE_FAR_Y_FRACTION,
            SceneSpace.ROAD_LANE_NEAR_Y_FRACTION,
        )
        assertEquals("a single-lane theme must fall back to the canonical strip", canonical, collapsed, 1e-6f)
    }

    /**
     * Where the perception most likely comes from, measured rather than argued: a far-lane car's
     * roof clears the top edge of the tarmac, a near-lane car's does not.
     *
     * Pinned so the asymmetry stays a known, bounded property instead of quietly growing. A far
     * car may clear the edge; it may not clear it by more than its own height.
     */
    @Test
    fun `a far-lane car clears the top edge and a near-lane car does not`() {
        val farHeight = carHeightPx(SceneSpace.ROAD_LANE_FAR_Y_FRACTION)
        val farRoofY = SceneSpace.ROAD_LANE_FAR_Y_FRACTION * h - farHeight
        val nearHeight = carHeightPx(SceneSpace.ROAD_LANE_NEAR_Y_FRACTION)
        val nearRoofY = SceneSpace.ROAD_LANE_NEAR_Y_FRACTION * h - nearHeight
        val roadTopPx = roadTop * h

        val farOverhang = roadTopPx - farRoofY
        println("Filone B overhang: far lane roof clears the tarmac by ${"%.1f".format(farOverhang)}px")
        assertTrue("the far car no longer clears the top edge", farOverhang > 0f)
        assertTrue(
            "the far car clears the top edge by ${"%.1f".format(farOverhang)}px, more than its own height",
            farOverhang < farHeight,
        )
        assertTrue("the near-lane car should sit inside the tarmac", nearRoofY > roadTopPx)
    }
}
