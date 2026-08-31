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
     * **The v4.6 defect, as one number.**
     *
     * A pedestrian's head is [PEDESTRIAN_HEAD_UNITS] of the [PEDESTRIAN_CONTENT_UNITS] their walk
     * sprite draws -- 31% of their own height, which is the paper-cutout proportion this whole
     * scene is drawn in -- so an adult's head reads as 0.547 m. The people behind glass were sized
     * against the window and nobody had compared the two: a driver's head was 0.320 m, **59% of
     * the head of the pedestrian walking past on a plane further away**, and they read as children.
     *
     * The band is deliberately not "equal". A head seen through glass may read a little smaller
     * than the same head in the open -- it is behind a pane, inside a body, and a pedestrian's own
     * head is drawn at cartoon size to carry a whole figure. What it may not do is read as a
     * different species. 70-90% is that judgement written down; v4.5 sat at 59% and 59%.
     */
    @Test
    fun `a person behind glass has a head the size of the people walking past`() {
        // The size table charges a walk sprite for 80 units and it draws 80.67, so the two agree
        // to within a percent and the head below can be read off either.
        assertEquals(
            "the size table and the artwork disagree about how tall a pedestrian is",
            SceneSpace.PERSON_SPRITE_UNITS_TALL,
            PEDESTRIAN_CONTENT_UNITS,
            1f,
        )
        val pedestrian = PEDESTRIAN_HEAD_UNITS * (SceneSpace.PERSON_METRES_TALL / SceneSpace.PERSON_SPRITE_UNITS_TALL)
        assertEquals("a pedestrian's head, in metres", 0.547f, pedestrian, 0.005f)

        for ((label, metres) in occupantHeadMetres()) {
            val share = metres / pedestrian
            assertTrue(
                "$label head is ${"%.3f".format(metres)} m against a pedestrian's " +
                    "${"%.3f".format(pedestrian)} m -- ${"%.0f".format(share * 100)}%",
                share in 0.70f..0.90f,
            )
        }
    }

    /**
     * The bust scale is glass-over-content and nothing else, and both busts stand on the sill.
     *
     * This is the rule that replaced three separately tuned scales, and asserting it is what stops
     * the next size complaint being answered with a fourth. None of [CAR_HEAD_SCALE],
     * [CAR_PASSENGER_SCALE] or [FIRE_TRUCK_HEAD_SCALE] is a number anybody chose: each is its own
     * glass height over its own sprite family's content height.
     *
     * **What the first three assertions do not check.** Each scale is *defined* as
     * `glass / content`, so `content * scale == glass` is `glass == glass` for any content
     * whatsoever — it pins the shape of the rule and says nothing about the pictures. That is how
     * a window head 169 px tall went on being divided by 155 until the winter woman's hat was
     * measured 3 px above the glass on a phone. `OccupantHeadFitTest` is the one that reads the
     * PNGs; this one only states that no fourth hand-tuned scale has appeared.
     */
    @Test
    fun `a bust is scaled by glass over content and stands on the sill`() {
        assertEquals(
            "driver bust height",
            SceneObjectRenderer.CAR_GLASS_HEIGHT_UNITS,
            SceneObjectRenderer.CAR_HEAD_CONTENT_UNITS * CAR_HEAD_SCALE,
            0.001f,
        )
        assertEquals(
            "passenger bust height",
            SceneObjectRenderer.CAR_GLASS_HEIGHT_UNITS,
            SceneObjectRenderer.WINDOW_HEAD_CONTENT_UNITS * CAR_PASSENGER_SCALE,
            0.001f,
        )
        assertEquals(
            "fire engine bust height",
            SceneObjectRenderer.FIRE_TRUCK_GLASS_HEIGHT_UNITS,
            SceneObjectRenderer.CAR_HEAD_CONTENT_UNITS * FIRE_TRUCK_HEAD_SCALE,
            0.001f,
        )
        assertEquals(
            "both sedan busts stand on the sill",
            SceneObjectRenderer.CAR_SILL_Y_UNITS,
            SceneObjectRenderer.CAR_HEAD_Y_UNITS,
            0.001f,
        )
        assertEquals(
            SceneObjectRenderer.CAR_SILL_Y_UNITS,
            SceneObjectRenderer.CAR_PASSENGER_Y_UNITS,
            0.001f,
        )
        assertEquals(
            "the fire engine's bust stands on its own sill",
            SceneObjectRenderer.FIRE_TRUCK_SILL_Y_UNITS,
            SceneObjectRenderer.FIRE_TRUCK_HEAD_Y_UNITS,
            0.001f,
        )
    }

    /**
     * The taller pane stays inside the car, and the door accessories ride the sill.
     *
     * Upward there is no room -- `car_body`'s roof is at y=-11 and the glass top is -6 -- so the
     * extra units go downward. **v4.15 moved the sill from 13 to 14.72** so the tallest winter bust
     * fits the pane instead of standing over the roof; see
     * [SceneObjectRenderer.CAR_GLASS_HEIGHT_UNITS]. The rule the old y=13 literal expressed is
     * unchanged and is now expressed directly: `police_stripe` and `taxi_checker` are blitted *at
     * the sill*, so they follow it rather than being a second copy of the number. What still may
     * not happen is the glass reaching the beltline at y=18, which is where the body stops being
     * flat colour.
     */
    @Test
    fun `the enlarged glass stops short of the beltline and the accessories ride the sill`() {
        assertEquals("the glass top has not moved", -6f, SceneObjectRenderer.CAR_GLASS_ORIGIN_Y_UNITS, 0.001f)
        assertEquals("the sill", 14.716f, SceneObjectRenderer.CAR_SILL_Y_UNITS, 0.002f)
        assertTrue(
            "the sill must stay above the beltline at y=18",
            SceneObjectRenderer.CAR_SILL_Y_UNITS < 18f,
        )
        assertEquals(
            "the police stripe and the taxi chequer are blitted at the sill, whatever it is",
            SceneObjectRenderer.CAR_SILL_Y_UNITS,
            DOOR_ACCESSORY_Y_UNITS,
            0.001f,
        )
        assertTrue(
            "the glass grew, so v4.5's 16 units is not still in force",
            SceneObjectRenderer.CAR_GLASS_HEIGHT_UNITS > SceneObjectRenderer.CAR_GLASS_SPRITE_HEIGHT_UNITS,
        )
        assertEquals(
            "the stretch is the ratio of the two",
            SceneObjectRenderer.CAR_GLASS_HEIGHT_UNITS / SceneObjectRenderer.CAR_GLASS_SPRITE_HEIGHT_UNITS,
            SceneObjectRenderer.CAR_GLASS_Y_SCALE,
            0.0001f,
        )
    }

    /**
     * The two busts stay in their own panes, either side of the glass's painted mullion.
     *
     * Both grew by a third, and the passenger's is the one with no room to spare: `car_window`'s
     * rear pane runs from the mullion's right edge at 7.33 to the glass edge at 26, which is 18.67
     * units for a bust 18.14 wide. Asserted rather than eyeballed because the margin either side
     * is a quarter of a unit.
     */
    @Test
    fun `driver and passenger stay either side of the mullion and inside the glass`() {
        val driver = contentSpanX(
            placementX = SceneObjectRenderer.CAR_HEAD_X_UNITS,
            anchorX = SceneObjectRenderer.CAR_HEAD_ANCHOR_X_UNITS,
            scale = CAR_HEAD_SCALE,
            contentLeftUnits = CAR_HEAD_CONTENT_LEFT_UNITS,
            contentRightUnits = CAR_HEAD_CONTENT_RIGHT_UNITS,
        )
        val passenger = contentSpanX(
            placementX = SceneObjectRenderer.CAR_PASSENGER_X_UNITS,
            anchorX = SceneObjectRenderer.WINDOW_HEAD_ANCHOR_X_UNITS,
            scale = CAR_PASSENGER_SCALE,
            contentLeftUnits = WINDOW_HEAD_CONTENT_LEFT_UNITS,
            contentRightUnits = WINDOW_HEAD_CONTENT_RIGHT_UNITS,
        )
        assertTrue(
            "the driver runs off the front of the glass: ${driver.first}",
            driver.first >= SceneObjectRenderer.CAR_GLASS_ORIGIN_X_UNITS,
        )
        assertTrue(
            "the driver crosses the mullion: ${driver.second} vs $MULLION_LEFT_UNITS",
            driver.second <= MULLION_LEFT_UNITS,
        )
        assertTrue(
            "the passenger crosses the mullion: ${passenger.first} vs $MULLION_RIGHT_UNITS",
            passenger.first >= MULLION_RIGHT_UNITS,
        )
        assertTrue(
            "the passenger runs off the back of the glass: ${passenger.second} vs $GLASS_RIGHT_UNITS",
            passenger.second <= GLASS_RIGHT_UNITS,
        )
    }

    /** Service vehicles keep their own hierarchy: the fire engine's cab sits its driver higher. */
    @Test
    fun `the fire engine's own bust scale is not silently equal to a car's`() {
        assertTrue(
            "the fire engine reuses the car's bust scale, losing its taller cab",
            FIRE_TRUCK_HEAD_SCALE != CAR_HEAD_SCALE,
        )
    }

    /**
     * v4.6 resized the people in the cars and **nothing else about a car**.
     *
     * The one thing the batch was explicitly not allowed to do is make the vehicle bigger to make
     * its occupants fit, so the two constants that would express that are pinned here as well as
     * in `the vehicles keep their declared heights` above -- once as a size table entry, once as a
     * statement about this release.
     */
    @Test
    fun `the fix did not quietly enlarge the car`() {
        assertEquals(1.45f, SceneSpace.CAR_METRES_TALL, 0.0001f)
        assertEquals(1.359375f, SceneSpace.CAR_BASE_SCALE, 0.0001f)
        assertEquals(45f, SceneSpace.PIXELS_PER_METRE_AT_REFERENCE, 0.0001f)
    }

    /** Each occupant's head height in scene metres, by vehicle. */
    private fun occupantHeadMetres(): List<Pair<String, Float>> {
        val perCarUnit = SceneSpace.CAR_METRES_TALL / SceneSpace.CAR_SPRITE_UNITS_TALL
        val perTruckUnit = SceneSpace.FIRE_TRUCK_METRES_TALL / SceneSpace.FIRE_TRUCK_SPRITE_UNITS_TALL
        return listOf(
            "driver" to SceneObjectRenderer.CAR_HEAD_HEAD_UNITS * CAR_HEAD_SCALE * perCarUnit,
            "passenger" to SceneObjectRenderer.WINDOW_HEAD_HEAD_UNITS * CAR_PASSENGER_SCALE * perCarUnit,
            "fire engine driver" to SceneObjectRenderer.CAR_HEAD_HEAD_UNITS * FIRE_TRUCK_HEAD_SCALE * perTruckUnit,
        )
    }

    /** Where a bust's own drawing lands on x, in the car's local units. `placement - anchor`. */
    private fun contentSpanX(
        placementX: Float,
        anchorX: Float,
        scale: Float,
        contentLeftUnits: Float,
        contentRightUnits: Float,
    ): Pair<Float, Float> {
        val origin = placementX - anchorX * scale
        return (origin + contentLeftUnits * scale) to (origin + contentRightUnits * scale)
    }

    private companion object {
        /** The walk sprites' child content height, against the adults' 80. */
        const val CHILD_SPRITE_UNITS_TALL = 62f

        /**
         * The walk sprites' own numbers, measured off the artwork rather than assumed.
         *
         * All twenty-four are 123x252 px. The alpha box is 242 px tall, and the head -- crown of
         * the hair down to the chin, where the silhouette pinches into the neck -- is the top 75
         * of those. Divided by `SpriteBlitter.SPRITE_PIXELS_PER_UNIT`.
         */
        const val PEDESTRIAN_CONTENT_UNITS = 242f / 3f
        const val PEDESTRIAN_HEAD_UNITS = 75f / 3f

        /** `person_*_head_car` alpha box on x: 4..119 px of a 120 px canvas. */
        const val CAR_HEAD_CONTENT_LEFT_UNITS = 4f / 3f
        const val CAR_HEAD_CONTENT_RIGHT_UNITS = 120f / 3f

        /** `person_*_head_window` alpha box on x: 4..152 px of a 159 px canvas. */
        const val WINDOW_HEAD_CONTENT_LEFT_UNITS = 4f / 3f
        const val WINDOW_HEAD_CONTENT_RIGHT_UNITS = 152f / 3f

        /** `car_window`'s painted mullion, in the car's local units: sprite x 72..81 px. */
        const val MULLION_LEFT_UNITS = -20f + 72f / 3f
        const val MULLION_RIGHT_UNITS = -20f + 82f / 3f

        /** The glass's own right edge, from the same sprite. */
        const val GLASS_RIGHT_UNITS = -20f + 138f / 3f

        /**
         * Where `police_stripe` and `taxi_checker` are actually blitted, **read from the source**.
         *
         * It used to be the literal `13f` copied out of `drawCar`, which is a second copy of a
         * number and went stale the moment v4.15 moved the sill. The call site now names
         * [SceneObjectRenderer.CAR_SILL_Y_UNITS] directly, and this reads that back out of the file
         * so the assertion is about the coupling rather than about a number somebody retyped.
         */
        val DOOR_ACCESSORY_Y_UNITS: Float by lazy {
            val source = rendererSource().readText()
            val pattern = "R\\.drawable\\.(?:police_stripe|taxi_checker), -34f, ([A-Za-z_.]+|[0-9.]+f)\\)"
            val calls = Regex(pattern).findAll(source).map { it.groupValues[1] }.toList()
            require(calls.size == 2) { "expected two door-accessory blits, found $calls" }
            require(calls.distinct().size == 1) { "the two accessories no longer share a y: $calls" }
            when (val y = calls.first()) {
                "CAR_SILL_Y_UNITS" -> SceneObjectRenderer.CAR_SILL_Y_UNITS
                else -> y.removeSuffix("f").toFloat()
            }
        }

        /** Walks up for the module root, the way `SkyscraperWindowTest` finds the renderer. */
        private fun rendererSource(): java.io.File {
            val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/engine/SceneObjectRenderer.kt"
            var dir: java.io.File? = java.io.File(".").absoluteFile
            while (dir != null) {
                for (prefix in listOf("", "app/")) {
                    val candidate = java.io.File(dir, "$prefix$suffix")
                    if (candidate.isFile) return candidate
                }
                dir = dir.parentFile
            }
            error("could not locate $suffix")
        }

        val CAR_HEAD_SCALE = SceneObjectRenderer.CAR_HEAD_SCALE
        val CAR_PASSENGER_SCALE = SceneObjectRenderer.CAR_PASSENGER_SCALE
        val FIRE_TRUCK_HEAD_SCALE = SceneObjectRenderer.FIRE_TRUCK_HEAD_SCALE
    }
}
