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
     * **An occupant inherits the vehicle's depth, and the arithmetic says so.**
     *
     * `drawCar` applies `scale(vehicleScale)` once around the whole vehicle, where `vehicleScale`
     * is `CAR_BASE_SCALE * perspectiveScaleAt(laneY) * sceneScale`, and both busts are blitted
     * *inside* that transform in the car's own local units. So an occupant is drawn at exactly the
     * projection of the lane their car stands on -- a near-lane occupant is larger than a far-lane
     * one by precisely the ratio the two lanes have, and by nothing else.
     *
     * Asserted because the alternative failure -- an occupant on a scale of its own that does not
     * follow the vehicle -- would be invisible in any single frame and would be the real defect if
     * it were ever true. It is not: the two ratios agree to five decimal places.
     */
    @Test
    fun `an occupant is drawn at its own vehicle's depth and nothing else`() {
        val laneRatio = SceneSpace.perspectiveScaleAt(SceneSpace.ROAD_LANE_NEAR_Y_FRACTION) /
            SceneSpace.perspectiveScaleAt(SceneSpace.ROAD_LANE_FAR_Y_FRACTION)
        val heads = drawnHeads()
        for (who in listOf("driver", "passenger", "fire engine driver")) {
            assertEquals(
                "$who near/far must be the lane ratio and not a scale of its own",
                laneRatio,
                heads.getValue("near-lane $who") / heads.getValue("far-lane $who"),
                0.00001f,
            )
        }
        assertEquals(
            "and it is the vehicle's own ratio",
            carOn(SceneSpace.ROAD_LANE_NEAR_Y_FRACTION) / carOn(SceneSpace.ROAD_LANE_FAR_Y_FRACTION),
            heads.getValue("near-lane driver") / heads.getValue("far-lane driver"),
            0.00001f,
        )
    }

    /**
     * **The primary proportions: occupant against pane, and occupant against vehicle.**
     *
     * These are the two relations the v4.15 report is about, and they are the two that a change of
     * [SceneSpace.CAR_METRES_TALL] cannot touch -- an occupant lives in the vehicle's local units,
     * so enlarging the vehicle enlarges the person with it and leaves both ratios exactly where
     * they were. Measured on a OnePlus 6T at 1.45 m and at 1.75 m: head over vehicle 31.3% in both,
     * head over pane 72.6% in both. Only the occupant's own scale moves them.
     *
     * v4.16 takes the head to 51.9% of its pane -- the share `drawWindowOccupant` has given a head
     * since v4.2 -- which brings the sedan's driver from 31.3% of the vehicle's height to 22.4%.
     */
    @Test
    fun `an occupant is a fixed share of its pane and of its vehicle`() {
        val share = SceneObjectRenderer.OCCUPANT_HEAD_PANE_SHARE
        assertEquals(
            "driver head over pane",
            share,
            SceneObjectRenderer.CAR_HEAD_HEAD_UNITS * CAR_HEAD_SCALE / SceneObjectRenderer.CAR_GLASS_HEIGHT_UNITS,
            0.0005f,
        )
        assertEquals(
            "driver head over the vehicle it rides in",
            0.224f,
            SceneObjectRenderer.CAR_HEAD_HEAD_UNITS * CAR_HEAD_SCALE / SceneSpace.CAR_SPRITE_UNITS_TALL,
            0.002f,
        )
        assertEquals(
            "fire engine driver head over the vehicle it rides in",
            0.107f,
            SceneObjectRenderer.CAR_HEAD_HEAD_UNITS * FIRE_TRUCK_HEAD_SCALE / SceneSpace.FIRE_TRUCK_SPRITE_UNITS_TALL,
            0.002f,
        )
    }

    /**
     * The pedestrians, as a **secondary** guard and measured at a common depth.
     *
     * Comparing an occupant with a pedestrian as each is drawn is not a valid test: they stand on
     * different ground lines, the road is nearer than the pavement, and the projection is supposed
     * to make the nearer one larger. So the comparison here removes depth by asking what the same
     * cartoon head would measure **if the pedestrian stood on the occupant's own lane**, which is
     * the ratio of the two heads in scene metres and nothing to do with where either one is.
     *
     * The band is wide on purpose. It is a guard against an occupant becoming absurd, not a target:
     * the proportions this release is chosen on are the two above, and a head seen through a
     * windscreen is legitimately a good deal smaller than the same cartoon head in the open,
     * because a pedestrian's head is drawn at 31% of their own body to carry a whole figure while a
     * car's glass is 29% of the car.
     */
    @Test
    fun `an occupant's head stays in a sane relation to a pedestrian's at the same depth`() {
        val pedestrianHead =
            PEDESTRIAN_HEAD_UNITS * (SceneSpace.PERSON_METRES_TALL / SceneSpace.PERSON_SPRITE_UNITS_TALL)
        assertEquals("a pedestrian's head, in metres", 0.547f, pedestrianHead, 0.005f)
        val perCarUnit = SceneSpace.CAR_METRES_TALL / SceneSpace.CAR_SPRITE_UNITS_TALL
        val perTruckUnit = SceneSpace.FIRE_TRUCK_METRES_TALL / SceneSpace.FIRE_TRUCK_SPRITE_UNITS_TALL
        for ((label, metres) in listOf(
            "driver" to SceneObjectRenderer.CAR_HEAD_HEAD_UNITS * CAR_HEAD_SCALE * perCarUnit,
            "passenger" to SceneObjectRenderer.WINDOW_HEAD_HEAD_UNITS * CAR_PASSENGER_SCALE * perCarUnit,
            "fire engine driver" to SceneObjectRenderer.CAR_HEAD_HEAD_UNITS * FIRE_TRUCK_HEAD_SCALE * perTruckUnit,
        )) {
            val share = metres / pedestrianHead
            assertTrue(
                "$label head is ${"%.3f".format(metres)} m against a pedestrian's " +
                    "${"%.3f".format(pedestrianHead)} m -- ${"%.0f".format(share * 100)}%",
                share in 0.45f..1.00f,
            )
        }
    }

    /**
     * The drawn sizes stated absolutely, so a change to either side is caught here and not only as
     * a ratio that two compensating edits could keep in range.
     */
    @Test
    fun `the occupant heads are the drawn sizes this release chose`() {
        val actual = drawnHeads()
        assertEquals("near-lane driver", 15.85f, actual.getValue("near-lane driver"), 0.05f)
        assertEquals("far-lane driver", 13.71f, actual.getValue("far-lane driver"), 0.05f)
        assertEquals("near-lane passenger", 15.85f, actual.getValue("near-lane passenger"), 0.05f)
        assertEquals("near-lane fire engine driver", 15.13f, actual.getValue("near-lane fire engine driver"), 0.05f)
    }

    /**
     * The bust scale is one shared head-share over each family's own head, and both stand on the sill.
     *
     * v4.6 replaced three separately tuned scales with `glass / content`; v4.16 replaces that with
     * [SceneObjectRenderer.OCCUPANT_HEAD_PANE_SHARE] times the pane over the family's own head
     * height. None of [CAR_HEAD_SCALE], [CAR_PASSENGER_SCALE] or [FIRE_TRUCK_HEAD_SCALE] is a
     * number anybody chose.
     *
     * **What these assertions do not check.** Each scale is *defined* this way, so
     * `content * scale == fill * glass` is a tautology for any content whatsoever -- it pins the
     * shape of the rule and says nothing about the pictures. That is how a window head 169 px tall
     * went on being divided by 155 until the winter woman's hat was measured 3 px above the glass
     * on a phone. `OccupantHeadFitTest` is the one that reads the PNGs; this one only states that
     * no fourth hand-tuned scale has appeared.
     */
    @Test
    fun `a bust is scaled by the shared head share and stands on the sill`() {
        val share = SceneObjectRenderer.OCCUPANT_HEAD_PANE_SHARE
        assertEquals(
            "driver head height",
            share * SceneObjectRenderer.CAR_GLASS_HEIGHT_UNITS,
            SceneObjectRenderer.CAR_HEAD_HEAD_UNITS * CAR_HEAD_SCALE,
            0.001f,
        )
        assertEquals(
            "passenger head height",
            share * SceneObjectRenderer.CAR_GLASS_HEIGHT_UNITS,
            SceneObjectRenderer.WINDOW_HEAD_HEAD_UNITS * CAR_PASSENGER_SCALE,
            0.001f,
        )
        assertEquals(
            "fire engine head height",
            share * SceneObjectRenderer.FIRE_TRUCK_GLASS_HEIGHT_UNITS,
            SceneObjectRenderer.CAR_HEAD_HEAD_UNITS * FIRE_TRUCK_HEAD_SCALE,
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
     * The pane stays inside the car, and the door accessories ride the sill.
     *
     * Upward there is no room -- `car_body`'s roof is at y=-11 and the glass top is -6 -- so the
     * stretch goes downward, and the sill sits at 14.716. **v4.16 does not move it**: that release
     * answers a report about the occupants, and asserting the pane here is what stops the fix
     * quietly resizing the car as well. `police_stripe` and `taxi_checker` are blitted *at the
     * sill*, so they follow it rather than being a second copy of the number. What may not happen
     * is the glass reaching the beltline at y=18, where the body stops being flat colour.
     */
    @Test
    fun `the glass stops short of the beltline and the accessories ride the sill`() {
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

    /**
     * Each occupant's head as it is actually drawn, in reference pixels.
     *
     * Head units x bust scale x the vehicle's base scale x the projection at the lane it stands
     * on -- the same chain `drawCar` applies, written out so a test can read it.
     */
    private fun drawnHeads(): Map<String, Float> {
        fun head(headUnits: Float, scale: Float, baseScale: Float, lane: Float) =
            headUnits * scale * baseScale * SceneSpace.perspectiveScaleAt(lane)

        val near = SceneSpace.ROAD_LANE_NEAR_Y_FRACTION
        val far = SceneSpace.ROAD_LANE_FAR_Y_FRACTION
        val carHead = SceneObjectRenderer.CAR_HEAD_HEAD_UNITS
        val winHead = SceneObjectRenderer.WINDOW_HEAD_HEAD_UNITS
        val car = SceneSpace.CAR_BASE_SCALE
        val truck = SceneSpace.FIRE_TRUCK_BASE_SCALE
        return mapOf(
            "near-lane driver" to head(carHead, CAR_HEAD_SCALE, car, near),
            "far-lane driver" to head(carHead, CAR_HEAD_SCALE, car, far),
            "near-lane passenger" to head(winHead, CAR_PASSENGER_SCALE, car, near),
            "far-lane passenger" to head(winHead, CAR_PASSENGER_SCALE, car, far),
            "near-lane fire engine driver" to head(carHead, FIRE_TRUCK_HEAD_SCALE, truck, near),
            "far-lane fire engine driver" to head(carHead, FIRE_TRUCK_HEAD_SCALE, truck, far),
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
