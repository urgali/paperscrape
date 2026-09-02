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
        // v4.20: the redesigned saloon stands 50 units (roof -13 to ground 37) and the metres
        // moved with the units, so the car grew on screen by design and a unit stayed a unit.
        assertEquals(1.51f, SceneSpace.CAR_METRES_TALL, 0.0001f)
        assertEquals(2.9f, SceneSpace.FIRE_TRUCK_METRES_TALL, 0.0001f)
        assertEquals(50f, SceneSpace.CAR_SPRITE_UNITS_TALL, 0.0001f)
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
            assertEquals("fire truck / car at $lane", 2.9f / 1.51f, ratio, 0.0001f)
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
    fun `an occupant is the table's head in its pane and against its vehicle`() {
        // rc2: the head is the height table's, seat-fitted (see CAR_OCCUPANT_SCALE's doc), and
        // the pane was sized around it. The derived shares: a head is 72.4% of the sedan's
        // 23-unit pane and 69.4% of the cab's 17, 33.3% of the sedan's height and 17.3% of the
        // appliance's -- a person-sized person in a car-sized car.
        val sedanHead = SceneObjectRenderer.HEAD_CAR_HEAD_UNITS * SceneObjectRenderer.CAR_OCCUPANT_SCALE
        val cabHead = SceneObjectRenderer.HEAD_CAR_HEAD_UNITS * SceneObjectRenderer.FIRE_TRUCK_OCCUPANT_SCALE
        assertEquals("driver head over pane", 0.724f, sedanHead / SceneObjectRenderer.CAR_GLASS_HEIGHT_UNITS, 0.002f)
        assertEquals("cab head over pane", 0.694f, cabHead / SceneObjectRenderer.FIRE_TRUCK_GLASS_HEIGHT_UNITS, 0.002f)
        assertEquals("driver head over vehicle", 0.333f, sedanHead / SceneSpace.CAR_SPRITE_UNITS_TALL, 0.002f)
        assertEquals("cab head over vehicle", 0.173f, cabHead / SceneSpace.FIRE_TRUCK_SPRITE_UNITS_TALL, 0.002f)
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
        // rc2: the relation is no longer a wide guard band around a share-derived size -- it is
        // the rule itself. An occupant's head IS the pedestrian's, times the 0.97 seat fit,
        // whatever vehicle they sit in; the +/-10% band is the acceptance criterion's own.
        val pedestrianHead = SceneObjectRenderer.PERSON_HEAD_SPRITE_UNITS *
            (SceneSpace.PERSON_METRES_TALL / SceneSpace.PERSON_SPRITE_UNITS_TALL)
        assertEquals("a pedestrian's head, in metres", 0.518f, pedestrianHead, 0.005f)
        val perCarUnit = SceneSpace.CAR_METRES_TALL / SceneSpace.CAR_SPRITE_UNITS_TALL
        val perTruckUnit = SceneSpace.FIRE_TRUCK_METRES_TALL / SceneSpace.FIRE_TRUCK_SPRITE_UNITS_TALL
        for ((label, metres) in listOf(
            "sedan occupant" to SceneObjectRenderer.HEAD_CAR_HEAD_UNITS *
                SceneObjectRenderer.CAR_OCCUPANT_SCALE * perCarUnit,
            "appliance occupant" to SceneObjectRenderer.HEAD_CAR_HEAD_UNITS *
                SceneObjectRenderer.FIRE_TRUCK_OCCUPANT_SCALE * perTruckUnit,
        )) {
            val share = metres / pedestrianHead
            assertEquals("$label is the seat-fitted table head", SceneObjectRenderer.OCCUPANT_SEATED_FIT, share, 0.001f)
            assertTrue("$label inside the +/-10% criterion band", share in 0.9f..1.1f)
        }
    }

    /**
     * The drawn sizes stated absolutely, so a change to either side is caught here and not only as
     * a ratio that two compensating edits could keep in range.
     */
    @Test
    fun `the occupant heads are the drawn sizes this release chose`() {
        val actual = drawnHeads()
        // rc2: the table's signature is that every adult head in traffic measures the same at the
        // same lane, sedan and appliance alike -- 24.53 reference px near, 21.21 far -- because
        // the size is the person's, not the vehicle's.
        assertEquals("near-lane driver", 24.53f, actual.getValue("near-lane driver"), 0.05f)
        assertEquals("far-lane driver", 21.21f, actual.getValue("far-lane driver"), 0.05f)
        assertEquals("near-lane passenger", 24.53f, actual.getValue("near-lane passenger"), 0.05f)
        assertEquals("near-lane fire engine driver", 24.53f, actual.getValue("near-lane fire engine driver"), 0.05f)
        assertEquals(
            "the appliance's driver head equals the sedan's at the same lane: the table, visible",
            actual.getValue("near-lane driver"),
            actual.getValue("near-lane fire engine driver"),
            0.001f,
        )
    }

    /**
     * One scale per vehicle family, and every bust stands on its sill.
     *
     * rc2 set the rule and rc4 keeps it on the frontal family: the `head_car` set carries
     * adults at 35 local units of head and children at 31.5, so a child would be shorter
     * through the same number rather than through a scale of their own (no vehicle can seat one
     * today -- one seat, adults drive -- but the artwork keeps the discipline). What is asserted
     * here is that no per-seat scale has reappeared and that the anchor is the torso baseline,
     * which is what "stands on the sill" means mechanically; the pictures are
     * OccupantHeadFitTest's and the instrumented fit test's problem.
     */
    @Test
    fun `one scale per family and every bust stands on its sill`() {
        assertTrue(
            "the appliance's scale must differ from the sedan's (different metres per unit)",
            SceneObjectRenderer.FIRE_TRUCK_OCCUPANT_SCALE != SceneObjectRenderer.CAR_OCCUPANT_SCALE,
        )
        assertEquals(
            "the sedan's bust stands on the sill",
            SceneObjectRenderer.CAR_SILL_Y_UNITS,
            SceneObjectRenderer.CAR_HEAD_Y_UNITS,
            0.001f,
        )
        assertEquals(
            "the fire engine's bust stands on its own sill",
            SceneObjectRenderer.FIRE_TRUCK_SILL_Y_UNITS,
            SceneObjectRenderer.FIRE_TRUCK_HEAD_Y_UNITS,
            0.001f,
        )
        assertEquals(
            "the frontal anchor is the family's shared canvas bottom",
            44f,
            SceneObjectRenderer.HEAD_CAR_ANCHOR_Y_UNITS,
            0.001f,
        )
    }

    /**
     * The pane's rc2 geometry, and the door accessories riding the sill.
     *
     * The glasshouse grew for the table-sized occupants: top -11 (two units of roof band remain
     * over the flat roof at -13), sill 12 (three units of painted door remain above the arch
     * tops at 15). The livery is blitted *at the sill*, so it follows it rather than being a
     * second copy of the number; and the glass stays authored at its drawn size.
     */
    @Test
    fun `the glass stops short of the beltline and the accessories ride the sill`() {
        assertEquals("the glass top", -11f, SceneObjectRenderer.CAR_GLASS_ORIGIN_Y_UNITS, 0.001f)
        assertEquals("the sill", 12f, SceneObjectRenderer.CAR_SILL_Y_UNITS, 0.002f)
        assertTrue(
            "the sill must stay above the arch tops at y=15",
            SceneObjectRenderer.CAR_SILL_Y_UNITS < 15f,
        )
        assertEquals(
            "the police stripe and the taxi chequer are blitted at the sill, whatever it is",
            SceneObjectRenderer.CAR_SILL_Y_UNITS,
            DOOR_ACCESSORY_Y_UNITS,
            0.001f,
        )
        assertEquals(
            "the glass is authored at its drawn size",
            SceneObjectRenderer.CAR_GLASS_HEIGHT_UNITS,
            SceneObjectRenderer.CAR_GLASS_SPRITE_HEIGHT_UNITS,
            0.0001f,
        )
        assertEquals("so the stretch is retired", 1f, SceneObjectRenderer.CAR_GLASS_Y_SCALE, 0.0001f)
    }

    /**
     * Both busts stay inside the one pane, clear of both pillars, and the driver sits forward.
     *
     * rc5 geometry. rc4 seated one person because a 42-unit glasshouse could not light two
     * frontal heads; the pane is 47 units now and the shell gave the length up out of its bonnet
     * (the arithmetic is at [SceneObjectRenderer.CAR_HEAD_X_UNITS]). Constant-side, what can be
     * asserted without a renderer is the *envelope*: the widest member's whole content box,
     * placed and scaled the way `drawCar` places it, at both seats, keeping at least 13% of the
     * glass's nominal width to each edge -- and the driver's head centre landing in the forward
     * half of the pane, which is the rc5 criterion that a car reads as being driven.
     *
     * The clearances that matter are per row and per pillar and belong on rendered pixels;
     * `VehicleOccupantScaleTest` measures those, and the pane fill with them.
     */
    @Test
    fun `both busts stay inside the glass, clear of both pillars, driver forward`() {
        val scale = SceneObjectRenderer.CAR_OCCUPANT_SCALE
        val driver = contentSpanX(
            placementX = SceneObjectRenderer.CAR_HEAD_X_UNITS,
            anchorX = SceneObjectRenderer.HEAD_CAR_ANCHOR_X_UNITS,
            scale = scale,
            contentLeftUnits = HEAD_CAR_CONTENT_LEFT_UNITS,
            contentRightUnits = HEAD_CAR_CONTENT_RIGHT_UNITS,
        )
        val passenger = contentSpanX(
            placementX = SceneObjectRenderer.CAR_PASSENGER_X_UNITS,
            anchorX = SceneObjectRenderer.HEAD_CAR_ANCHOR_X_UNITS,
            scale = scale,
            contentLeftUnits = HEAD_CAR_CONTENT_LEFT_UNITS,
            contentRightUnits = HEAD_CAR_CONTENT_RIGHT_UNITS,
        )
        val glassLeft = SceneObjectRenderer.CAR_GLASS_ORIGIN_X_UNITS
        val clearance = 0.13f * SceneObjectRenderer.CAR_GLASS_WIDTH_UNITS
        assertTrue(
            "the driver presses the A-pillar: ${driver.first} vs ${glassLeft + clearance}",
            driver.first >= glassLeft + clearance,
        )
        assertTrue(
            "the passenger presses the C-pillar: ${passenger.second} vs ${GLASS_RIGHT_UNITS - clearance}",
            passenger.second <= GLASS_RIGHT_UNITS - clearance,
        )
        assertTrue(
            "the passenger must sit behind the driver, not beside or ahead of them",
            SceneObjectRenderer.CAR_PASSENGER_X_UNITS > SceneObjectRenderer.CAR_HEAD_X_UNITS,
        )
        // And by more than the widest head band: the two heads may not touch at all, whatever
        // the rendered gap measurement then says. 18.08 u is the widest ink any seatable bust
        // carries between its crown and its chin, measured off the artwork by OccupantHeadFitTest.
        assertTrue(
            "the seat pitch is only " +
                "${SceneObjectRenderer.CAR_PASSENGER_X_UNITS - SceneObjectRenderer.CAR_HEAD_X_UNITS} u " +
                "against a widest head band of 18.08 u -- the two heads would overlap",
            SceneObjectRenderer.CAR_PASSENGER_X_UNITS - SceneObjectRenderer.CAR_HEAD_X_UNITS > 18.08f,
        )
        // The pane's own centre at mid height: the sprite rakes 3 units at the windscreen and 3
        // at the backlight, so its mid-height centre is the nominal centre.
        val paneCentre = glassLeft + SceneObjectRenderer.CAR_GLASS_WIDTH_UNITS / 2f
        assertTrue(
            "the driver's head centre must fall in the forward half of the glass: " +
                "${SceneObjectRenderer.CAR_HEAD_X_UNITS} against a centre of $paneCentre " +
                "(the artwork drives toward local -x)",
            SceneObjectRenderer.CAR_HEAD_X_UNITS < paneCentre,
        )
    }

    /**
     * The pane is the sprite, and the sprite is the pane.
     *
     * [SceneObjectRenderer.CAR_GLASS_WIDTH_UNITS] is what every width criterion divides by, so it
     * has to be `car_window`'s own width and not a number that once was. Measured off the PNG.
     */
    @Test
    fun `the declared pane width is the glass sprite's own width`() {
        val image = javax.imageio.ImageIO.read(java.io.File(drawableDir(), "car_window.png"))
        assertEquals(
            "CAR_GLASS_WIDTH_UNITS vs car_window.png (${image.width} px)",
            image.width / SpriteBlitter.SPRITE_PIXELS_PER_UNIT,
            SceneObjectRenderer.CAR_GLASS_WIDTH_UNITS,
            0.0001f,
        )
        assertEquals(
            "and its height is the pane height",
            image.height / SpriteBlitter.SPRITE_PIXELS_PER_UNIT,
            SceneObjectRenderer.CAR_GLASS_SPRITE_HEIGHT_UNITS,
            0.0001f,
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
    fun `the redesign grew the car by units and metres together, not by scale`() {
        // v4.20 made the saloon taller on purpose -- but through the size table, with metres and
        // units moving together, never by inflating the pixels a unit is worth. 1.51/50 is within
        // half a percent of the 1.45/48 every other object was authored against, so a local unit
        // still lands on the same pixel and nothing else in the scene moved.
        assertEquals(1.45f / 48f, SceneSpace.CAR_METRES_TALL / SceneSpace.CAR_SPRITE_UNITS_TALL, 0.0002f)
        assertEquals(1.359f, SceneSpace.CAR_BASE_SCALE, 0.001f)
        assertEquals(45f, SceneSpace.PIXELS_PER_METRE_AT_REFERENCE, 0.0001f)
    }

    /**
     * Each occupant's head as it is actually drawn, in reference pixels.
     *
     * Head units x bust scale x the vehicle's base scale x the projection at the lane it stands
     * on -- the same chain `drawCar` applies, written out so a test can read it.
     */
    private fun drawnHeads(): Map<String, Float> {
        fun head(scale: Float, baseScale: Float, lane: Float) =
            SceneObjectRenderer.HEAD_CAR_HEAD_UNITS * scale * baseScale * SceneSpace.perspectiveScaleAt(lane)

        val near = SceneSpace.ROAD_LANE_NEAR_Y_FRACTION
        val far = SceneSpace.ROAD_LANE_FAR_Y_FRACTION
        val car = SceneSpace.CAR_BASE_SCALE
        val truck = SceneSpace.FIRE_TRUCK_BASE_SCALE
        return mapOf(
            "near-lane driver" to head(SceneObjectRenderer.CAR_OCCUPANT_SCALE, car, near),
            "far-lane driver" to head(SceneObjectRenderer.CAR_OCCUPANT_SCALE, car, far),
            "near-lane passenger" to head(SceneObjectRenderer.CAR_OCCUPANT_SCALE, car, near),
            "far-lane passenger" to head(SceneObjectRenderer.CAR_OCCUPANT_SCALE, car, far),
            "near-lane fire engine driver" to head(SceneObjectRenderer.FIRE_TRUCK_OCCUPANT_SCALE, truck, near),
            "far-lane fire engine driver" to head(SceneObjectRenderer.FIRE_TRUCK_OCCUPANT_SCALE, truck, far),
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

        /** The widest `person_*_head_car` alpha box on x -- the winter adults -- in the shared
         * 141 px canvas: 5..130 px. */
        const val HEAD_CAR_CONTENT_LEFT_UNITS = 5f / 3f
        const val HEAD_CAR_CONTENT_RIGHT_UNITS = 130f / 3f

        /** The glass's own right edge, derived rather than restated. */
        val GLASS_RIGHT_UNITS =
            SceneObjectRenderer.CAR_GLASS_ORIGIN_X_UNITS + SceneObjectRenderer.CAR_GLASS_WIDTH_UNITS

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
            val pattern =
                "R\\.drawable\\.(?:police_stripe|taxi_checker), [A-Za-z_.0-9-]+f?, ([A-Za-z_.]+|[0-9.]+f)\\)"
            val calls = Regex(pattern).findAll(source).map { it.groupValues[1] }.toList()
            require(calls.size == 2) { "expected two door-accessory blits, found $calls" }
            require(calls.distinct().size == 1) { "the two accessories no longer share a y: $calls" }
            when (val y = calls.first()) {
                "CAR_SILL_Y_UNITS" -> SceneObjectRenderer.CAR_SILL_Y_UNITS
                else -> y.removeSuffix("f").toFloat()
            }
        }

        /** The shipped artwork, found the same way the renderer source is. */
        fun drawableDir(): java.io.File {
            val suffix = "src/main/res/drawable-nodpi"
            var dir: java.io.File? = java.io.File(".").absoluteFile
            while (dir != null) {
                for (prefix in listOf("", "app/")) {
                    val candidate = java.io.File(dir, "$prefix$suffix")
                    if (candidate.isDirectory) return candidate
                }
                dir = dir.parentFile
            }
            error("could not locate $suffix")
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

    }
}
