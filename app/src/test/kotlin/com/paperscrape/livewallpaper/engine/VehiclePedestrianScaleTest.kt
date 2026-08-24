package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The depth hierarchy between the road and the pavement, as arithmetic.
 *
 * ### The defect
 *
 * The maintainer reported that cars, and the people visible inside them, looked too small against
 * the pedestrians walking *behind* them — which is backwards, because the road plane is nearer the
 * viewer than the pavement.
 *
 * Measured on rendered frames before changing anything, both draw paths turned out to reproduce
 * [SceneSpace]'s projection to within a pixel of antialiasing: an adult pedestrian measured 22 px
 * against 21.1 predicted, a child 17 against 16.7, a far-lane car 22 against 20.4 plus 2.5 px of
 * police light bar. The implementation was not the problem.
 *
 * [SceneSpace.PERSON_METRES_TALL] was. It read `1.9f` while its own comment, three lines above it,
 * said 1.75 m — and the 8.6% difference inverted exactly one comparison, the one the report is
 * about: **a car in the far lane against a pedestrian on the far pavement.** The car is nearer, so
 * it must be drawn larger; at 1.9 m it was not.
 *
 * ### What is asserted, and what is deliberately not
 *
 * Not "a car is bigger than a person". A 1.75 m person really is taller than a 1.45 m car, and on
 * *adjacent* ground lines they come out close together — that is the projection being right, not
 * wrong, and forcing an ordering there would mean lying about one of the two heights.
 *
 * What is asserted is that every pair whose ground lines genuinely separate them comes out in the
 * order the ground plane implies, and that the two declared heights stay the ones the table
 * documents.
 */
class VehiclePedestrianScaleTest {

    /** Drawn height in reference pixels: sprite units x base scale x the projection at its line. */
    private fun drawnHeight(spriteUnits: Float, baseScale: Float, groundYFraction: Float): Float =
        spriteUnits * baseScale * SceneSpace.perspectiveScaleAt(groundYFraction)

    private fun adultOn(row: Float) =
        drawnHeight(SceneSpace.PERSON_SPRITE_UNITS_TALL, SceneSpace.PERSON_BASE_SCALE, row)

    private fun childOn(row: Float) =
        drawnHeight(CHILD_SPRITE_UNITS_TALL, SceneSpace.PERSON_BASE_SCALE, row)

    private fun carOn(lane: Float) =
        drawnHeight(SceneSpace.CAR_SPRITE_UNITS_TALL, SceneSpace.CAR_BASE_SCALE, lane)

    private fun fireTruckOn(lane: Float) =
        drawnHeight(SceneSpace.FIRE_TRUCK_SPRITE_UNITS_TALL, SceneSpace.FIRE_TRUCK_BASE_SCALE, lane)

    // ------------------------------------------------------- the declared heights

    /**
     * The constant and its own documentation must agree.
     *
     * This is the whole defect, reduced to one line. It is asserted rather than merely fixed
     * because the number and the comment had already drifted apart once.
     */
    @Test
    fun `an adult pedestrian is the height the size table says`() {
        assertEquals(1.75f, SceneSpace.PERSON_METRES_TALL, 0.0001f)
    }

    /** And the vehicles keep theirs, so this release cannot be read as having resized the world. */
    @Test
    fun `the vehicles keep their declared heights`() {
        assertEquals(1.45f, SceneSpace.CAR_METRES_TALL, 0.0001f)
        assertEquals(2.9f, SceneSpace.FIRE_TRUCK_METRES_TALL, 0.0001f)
        assertEquals(48f, SceneSpace.CAR_SPRITE_UNITS_TALL, 0.0001f)
        assertEquals(68f, SceneSpace.FIRE_TRUCK_SPRITE_UNITS_TALL, 0.0001f)
    }

    /** A child is a fixed fraction of an adult, drawn shorter inside the same canvas. */
    @Test
    fun `a child is drawn as a child and not as a small adult`() {
        val ratio = CHILD_SPRITE_UNITS_TALL / SceneSpace.PERSON_SPRITE_UNITS_TALL
        assertEquals("child/adult height ratio", 0.775f, ratio, 0.001f)
        assertEquals(
            "a child's implied real height",
            1.356f,
            SceneSpace.PERSON_METRES_TALL * ratio,
            0.01f,
        )
    }

    // ------------------------------------------------------- the reported inversion

    /**
     * **The assertion the defect fails.**
     *
     * A vehicle in the far lane stands at 0.834 of screen height; a pedestrian on the far pavement
     * at 0.795. The vehicle is nearer, so it is drawn larger. At `PERSON_METRES_TALL = 1.9f` this
     * came out 62.7 for the pedestrian against 61.1 for the car and the car read as a toy.
     */
    @Test
    fun `a car in the far lane is drawn larger than a pedestrian on the far pavement`() {
        val car = carOn(SceneSpace.ROAD_LANE_FAR_Y_FRACTION)
        val pedestrian = adultOn(SceneSpace.PAVEMENT_FAR_Y_FRACTION)
        assertTrue(
            "far-lane car ${"%.1f".format(car)} is not larger than far-pavement adult ${"%.1f".format(pedestrian)}",
            car > pedestrian,
        )
    }

    /** And the near lane, which was never in doubt, stays comfortably ahead of both pavements. */
    @Test
    fun `a car in the near lane is drawn larger than any pedestrian`() {
        val car = carOn(SceneSpace.ROAD_LANE_NEAR_Y_FRACTION)
        assertTrue("near-lane car vs far pavement", car > adultOn(SceneSpace.PAVEMENT_FAR_Y_FRACTION))
        assertTrue("near-lane car vs near pavement", car > adultOn(SceneSpace.PAVEMENT_NEAR_Y_FRACTION))
    }

    /**
     * The two lanes keep their own relationship, which this release must not disturb.
     *
     * Stated as the exact ratio the ground plane implies rather than as "one is bigger", so a
     * change to either lane constant fails here rather than passing silently.
     */
    @Test
    fun `the two traffic lanes keep the relationship the ground plane gives them`() {
        val expected = SceneSpace.perspectiveScaleAt(SceneSpace.ROAD_LANE_NEAR_Y_FRACTION) /
            SceneSpace.perspectiveScaleAt(SceneSpace.ROAD_LANE_FAR_Y_FRACTION)
        val actual = carOn(SceneSpace.ROAD_LANE_NEAR_Y_FRACTION) / carOn(SceneSpace.ROAD_LANE_FAR_Y_FRACTION)
        assertEquals("near/far lane size ratio", expected, actual, 0.0001f)
        assertEquals("and it is the value the lanes have always implied", 1.1564f, actual, 0.001f)
    }

    // ------------------------------------------------------- the whole ordering

    /**
     * Everything on the ground, ordered by its own ground line, must be ordered by size too --
     * *for its own kind*.
     *
     * Comparing different kinds is a statement about their real heights, not about depth, which is
     * why this compares each category with itself across the four ground lines the scene uses.
     */
    @Test
    fun `nearer is always drawn larger, within every category`() {
        val lines = listOf(
            SceneSpace.PAVEMENT_FAR_Y_FRACTION,
            SceneSpace.PAVEMENT_NEAR_Y_FRACTION,
            SceneSpace.ROAD_LANE_FAR_Y_FRACTION,
            SceneSpace.ROAD_LANE_NEAR_Y_FRACTION,
        ).sorted()
        for ((nearer, farther) in lines.zipWithNext().map { it.second to it.first }) {
            assertTrue("adults at $nearer vs $farther", adultOn(nearer) > adultOn(farther))
            assertTrue("children at $nearer vs $farther", childOn(nearer) > childOn(farther))
            assertTrue("cars at $nearer vs $farther", carOn(nearer) > carOn(farther))
            assertTrue("fire trucks at $nearer vs $farther", fireTruckOn(nearer) > fireTruckOn(farther))
        }
    }

    /** A fire engine is the tallest thing on the road, on either lane, as its 2.9 m says. */
    @Test
    fun `a fire engine towers over a car in the same lane`() {
        for (lane in listOf(SceneSpace.ROAD_LANE_FAR_Y_FRACTION, SceneSpace.ROAD_LANE_NEAR_Y_FRACTION)) {
            val ratio = fireTruckOn(lane) / carOn(lane)
            assertEquals("fire truck / car at $lane", 2.9f / 1.45f, ratio, 0.0001f)
        }
    }

    // ------------------------------------------------------- the busts behind the glass

    /**
     * A driver and a passenger are the right size for the person they are drawn as.
     *
     * They are the one part of the size system not derived from [SceneSpace.scaleForHeight]:
     * `CAR_HEAD_SCALE` and `CAR_PASSENGER_SCALE` size the bust against the *glass* it sits behind,
     * because a bust that ignored the window would not fit in it. That is legitimate, and it still
     * has to land on a believable person -- which is checked here by converting back: the bust's
     * drawn height, read in the car's own metres, must be a plausible head-and-shoulders for an
     * adult of [SceneSpace.PERSON_METRES_TALL].
     */
    @Test
    fun `the busts behind a windscreen are a believable size for an adult`() {
        val metresPerCarUnit = SceneSpace.CAR_METRES_TALL / SceneSpace.CAR_SPRITE_UNITS_TALL
        for ((label, scale) in listOf("driver" to CAR_HEAD_SCALE, "passenger" to CAR_PASSENGER_SCALE)) {
            val metres = CAR_HEAD_CONTENT_UNITS * scale * metresPerCarUnit
            val shareOfAPerson = metres / SceneSpace.PERSON_METRES_TALL
            assertTrue(
                "$label bust is ${"%.2f".format(metres)} m, ${"%.0f".format(shareOfAPerson * 100)}% of an adult",
                shareOfAPerson in 0.20f..0.30f,
            )
        }
    }

    /** Service vehicles keep their own hierarchy: the fire engine's cab sits its driver higher. */
    @Test
    fun `the fire engine's own bust scale is not silently equal to a car's`() {
        assertTrue(
            "the fire engine reuses the car's bust scale, losing its taller cab",
            FIRE_TRUCK_HEAD_SCALE != CAR_HEAD_SCALE,
        )
    }

    private companion object {
        /** The walk sprites' child content height, against the adults' 80. */
        const val CHILD_SPRITE_UNITS_TALL = 62f

        /** `person_*_head_car`'s own content height in local units, measured off the artwork. */
        const val CAR_HEAD_CONTENT_UNITS = 47.7f

        val CAR_HEAD_SCALE = SceneObjectRenderer.CAR_HEAD_SCALE
        val CAR_PASSENGER_SCALE = SceneObjectRenderer.CAR_PASSENGER_SCALE
        val FIRE_TRUCK_HEAD_SCALE = SceneObjectRenderer.FIRE_TRUCK_HEAD_SCALE
    }
}
