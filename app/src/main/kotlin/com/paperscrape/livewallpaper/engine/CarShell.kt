package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R

/**
 * The three civilian bodies the street carries, and everything that differs between them.
 *
 * ### Why there are three
 *
 * v4.18 shipped one saloon that had been bent five times to satisfy criteria nobody derived, and
 * the v4.19 concept pass drew three replacements from a blank sheet. The maintainer kept all
 * three: the road reads better with variety than with one model repeated, and a plain car now
 * picks its body from this enum.
 *
 * ### What they share, and why that is the point
 *
 * **One local unit is the same on-screen pixel on all three.** Their metres come from
 * [SceneSpace.CAR_UNIT_METRES] times their own [unitsTall], so [SceneSpace.CAR_BASE_SCALE] is a
 * single number for the family and a unit never means two sizes. That is what makes three
 * different silhouettes read as one set rather than as three imported drawings.
 *
 * They also share the whole vertical layout of the cabin -- the pane's top
 * [SceneObjectRenderer.CAR_GLASS_TOP_Y_UNITS], its sill [SceneObjectRenderer.CAR_SILL_Y_UNITS],
 * and therefore the seated-occupant scale and both seat positions. Only the plan changes: length,
 * roof line, wheelbase, where the glass begins and ends. An occupant is consequently the same size
 * in every car, which is what the height table asks for and what `OccupantHeadFitTest` measures.
 *
 * Ground contact is [SceneObjectRenderer.VEHICLE_GROUND_Y_UNITS] for all of them and the lamps are
 * the *same four sprites* on every body -- see [lampFrontXUnits].
 *
 * ### v5.6F: three cards, and the pane stops being the sprite
 *
 * «Ritaglio» builds a car the way the reference photograph does: a sheet of glass **behind** a
 * sheet of body paper with the panes cut out of it, and two whole discs glued **in front**. Two
 * consequences reach this file, and both are declarations that used to be one number and are now
 * two:
 *
 *  - **There are no wheel arches.** The disc is a complete circle lying on the paper, 46 % of it
 *    below the shell's floor, so nothing is cut away over a wheel and there is no air to keep
 *    concentric. [wheelFrontXUnits] and [wheelRearXUnits] are where the discs are centred and
 *    nothing else.
 *  - **The glass sprite is bigger than the pane it fills.** The sheet is grown half a unit past
 *    every edge of the hole so no seam can open between the two papers, and its canvas carries
 *    another half unit of padding: `car_window_saloon` is 61 units wide for a 59-unit pane.
 *    [glassSpriteXUnits] is therefore where the sprite is *blitted* and [paneXUnits] /
 *    [paneWidthUnits] are the hole an occupant is actually seen through -- which is what every
 *    pillar-light and fill criterion has to measure against. The two were the same number until
 *    v5.5, and `VehiclePedestrianScaleTest` used to assert as much in a method called
 *    *"each pane is its own sprite, and the sprite is the pane"*.
 */
internal enum class CarShell(
    val bodyRes: Int,
    val glassRes: Int,
    /** Blit origin of the body sprite: its SVG viewBox minimum, in local scene units. */
    val bodyXUnits: Float,
    val bodyYUnits: Float,
    /** Roof to wheel contact. Drives this body's metres through [SceneSpace.CAR_UNIT_METRES]. */
    val unitsTall: Float,
    /** Nose to tail of the painted shell, the dimension §2 of the v4.19 brief measures. */
    val lengthUnits: Float,
    /** Blit origin of `car_window_*`: its SVG viewBox minimum, **not** the pane's own edge. */
    val glassSpriteXUnits: Float,
    /**
     * The cabin hole cut in the body paper -- left edge and width, in local units.
     *
     * The estate's third window sits over the load bay and is **not** in this width: it is not
     * cabin glazing, and counting it would flatter the pillar light and flatten the fill. That
     * exclusion used to live in the two test files as `ESTATE_CABIN_PANE_WIDTH_UNITS`, which is a
     * fact about the drawing kept in the things that measure it; it is declared here now.
     */
    val paneXUnits: Float,
    val paneWidthUnits: Float,
    /**
     * How far this body's seat pair sits from where the pane wants it, in local units.
     *
     * **One pair of seats serves three cabins, and the three are not the same shape.** The seats
     * are shared constants -- driver at [SceneObjectRenderer.CAR_HEAD_X_UNITS], passenger at
     * [SceneObjectRenderer.CAR_PASSENGER_X_UNITS] -- and each body then cuts its own glasshouse
     * around them. Measured on the rendered frame, the light between an occupant and its pillar
     * came out at 24% of a head on the compact and **4%** on the police saloon, whose livery
     * bands the lower glass and shortens the pane it leaves: the same pair, two very different
     * cabins.
     *
     * The percentages below were measured on the v4.19 cabins, which were narrower than
     * «Ritaglio»'s: the saloon's pane went from 59 units of sprite to a 59-unit hole with a unit
     * of sheet behind each edge, and the compact's from 62 to 68. The offset is kept because the
     * asymmetry it corrects is still there -- the police livery still bands the lower glass of the
     * saloon and of no other body -- and because changing it would move the driver of every police
     * car for a reason nobody measured.
     *
     * Derived rather than tuned, and **still load-bearing after the seat pitch was shortened**:
     * at the 21.5-unit pitch the police saloon measures 8.0% with no offset and 18.0% with this
     * one, while the plain saloon reads 22.0% and 18.0% -- so the shift costs the body that does
     * not need it four points it has to spare and gives the body that does the ten it does not.
     * The compact, the estate and the appliance measure 28-31% with no shift at all, so theirs is
     * zero: a body whose pane already carries the pair does not get an offset for symmetry's sake.
     */
    val seatOffsetXUnits: Float,
    val wheelFrontXUnits: Float,
    val wheelRearXUnits: Float,
    /** The flat run of roof a light bar or a taxi sign can stand on. */
    val roofFrontXUnits: Float,
    val roofRearXUnits: Float,
    /**
     * Where the two shared lamp lenses land. Each body bakes its own housing a shade larger, and
     * `car_lamp_front`/`car_lamp_rear` are blitted into it -- one pair of sprites for three
     * bodies and the fire engine, which is why replacing v4.18's two full-car-width overlays
     * (282x18 px each, almost entirely transparent) with four small lenses paid for most of the
     * memory the three bodies cost. See `BACKLOG_v4_19.md` item 7.
     */
    val lampFrontXUnits: Float,
    val lampFrontYUnits: Float,
    val lampRearXUnits: Float,
    val lampRearYUnits: Float,
) {
    /**
     * A — the Compact: 92 units, the shortest, cab-forward with a hatch tail.
     *
     * It was "the shortest and the tallest" until v5.6F, and it is not any more: «Ritaglio» is a
     * low slab and all three bodies stand 53 units, roof -16 to the road at 37. The three heights
     * that used to differ -- 57, 56, 57.8 -- were a difference nothing measured and nothing saw.
     */
    COMPACT(
        bodyRes = R.drawable.car_body_compact, glassRes = R.drawable.car_window_compact,
        bodyXUnits = -47f, bodyYUnits = -17f, unitsTall = 53f, lengthUnits = 92f,
        glassSpriteXUnits = -28f, paneXUnits = -27f, paneWidthUnits = 68f, seatOffsetXUnits = 0f,
        wheelFrontXUnits = -30f, wheelRearXUnits = 30f,
        roofFrontXUnits = -20f, roofRearXUnits = 34f,
        lampFrontXUnits = -46.5f, lampFrontYUnits = 9f,
        lampRearXUnits = 43f, lampRearYUnits = 6f,
    ),

    /** B — the Saloon: 108 units, three boxes, the family's reference length. */
    SALOON(
        bodyRes = R.drawable.car_body_saloon, glassRes = R.drawable.car_window_saloon,
        bodyXUnits = -55f, bodyYUnits = -17f, unitsTall = 53f, lengthUnits = 108f,
        glassSpriteXUnits = -27f, paneXUnits = -26f, paneWidthUnits = 59f, seatOffsetXUnits = -1.4f,
        wheelFrontXUnits = -38f, wheelRearXUnits = 38f,
        roofFrontXUnits = -19f, roofRearXUnits = 24f,
        lampFrontXUnits = -54.5f, lampFrontYUnits = 10f,
        lampRearXUnits = 51f, lampRearYUnits = 6f,
    ),

    /**
     * C — the Estate: 124 units, and the length is the requirement rather than a side effect.
     *
     * The v4.19 concept pass drew it the same 108 units as the saloon, so the estate was not
     * visibly the longer car it is supposed to be. §2 of the brief asked for the extra length in
     * front **and** behind: the nose went -58 -> -66 and the tail 50 -> 58, which is 124 units
     * against the saloon's 108 -- **14.81% longer**, measured on the shipped artwork by
     * `VehicleShellGeometryTest` rather than declared here.
     *
     * Its glass sprite spans three panes; [paneWidthUnits] spans **two**. The cabin runs
     * -30..28 and seats both occupants; the load bay's own window runs 33..57 and is not cabin
     * glazing, so the criteria stop at 28.
     */
    ESTATE(
        bodyRes = R.drawable.car_body_estate, glassRes = R.drawable.car_window_estate,
        bodyXUnits = -63f, bodyYUnits = -17f, unitsTall = 53f, lengthUnits = 124f,
        glassSpriteXUnits = -31f, paneXUnits = -30f, paneWidthUnits = 58f, seatOffsetXUnits = 0f,
        wheelFrontXUnits = -42f, wheelRearXUnits = 40f,
        roofFrontXUnits = -23f, roofRearXUnits = 56f,
        lampFrontXUnits = -62.5f, lampFrontYUnits = 10f,
        lampRearXUnits = 59f, lampRearYUnits = 4f,
    );

    /** This body's real-world height, from the one metre-per-unit the family shares. */
    val metresTall: Float get() = SceneSpace.CAR_UNIT_METRES * unitsTall

    /** The ground shadow's half-length: it is the car's own footprint, not a shared 40. */
    val shadowHalfLengthUnits: Float get() = lengthUnits * 0.42f

    companion object {

        /**
         * The body a given vehicle carries -- **a pure function of the vehicle's own identity**,
         * and that is the whole requirement.
         *
         * A car must not change model while it crosses the screen, while the home screen is
         * swiped, or when another car enters or leaves the frame. The way to fail that is to
         * index a rotation by position in a list the visibility pass rebuilds -- which is exactly
         * how v4.17's falling leaves picked their colour (`i % visibleCount` over a per-frame
         * filter) and exactly what must not be repeated. So the body comes from
         * [CarObject.laneYFraction] and [CarObject.startDelaySeconds], the two fields that are
         * fixed when the candidate is generated and never touched again, and `CarRuntime`
         * resolves it **once**, at construction, so nothing per-frame can reach it at all.
         *
         * ### Why v4.19's hash was replaced by a table, and what the arithmetic actually allows
         *
         * v4.19 mixed those two fields through a 32-bit avalanche and took the result modulo
         * three, on the reasoning that a plain multiply-and-modulo would deal the bodies out as a
         * strict A/B/C cycle down a lane's queue. The reasoning was sound and the result was not:
         * **the two fields carry exactly ten distinct values between them**, and a hash cannot
         * make ten items land three-three-and-a-bit. A lane is one of two constants and a start
         * delay is one of [SceneObjectCatalog.CAR_SLOTS_PER_LANE] points on an arithmetic
         * progression, both fixed for every theme the app ships, so the hash was not sampling a
         * distribution -- it was dealing one fixed hand, and it dealt
         * **five saloons, three estates and two compacts**. Measured on the shipped catalogue:
         * 43 / 26 / 16 over 85 civilian cars, which is 51 / 31 / 19, the same five-three-two.
         *
         * So the choice is a **table**, because with ten slots the distribution is not a matter of
         * mixing quality but of arithmetic: 4 / 3 / 3 is the most even deal that exists, and the
         * only way to get it is to write it down. [DEAL] is that deal.
         *
         * ### Why the estate gets the fourth slot
         *
         * Because the liveried types do not rotate. A taxi is always a [COMPACT] and a police car
         * always a [SALOON], so on a road that carries one of each the compact and the saloon
         * arrive with a body already spoken for and the estate does not. Giving the spare civilian
         * slot to the estate is what makes the three roughly equal *on the road* rather than only
         * in the civilian subset -- with one taxi and one patrol car among ten candidates the
         * three bodies land at about 2.8 / 3.1 / 3.1 instead of 2.1 / 3.1 / 3.8.
         *
         * ### Why the order inside the table is not a cycle
         *
         * A balanced deal laid out as A/B/C repeating would be balanced and would read as a
         * pattern, which is what v4.19 was avoiding. [DEAL] is ordered so that **no lane repeats a
         * body in consecutive queue positions**, **each lane carries all three bodies**, and **the
         * two lanes never hold the same body at the same queue position**. Those three properties
         * are asserted in `VehicleShellRotationTest` rather than trusted to the eye.
         */
        fun forCar(spec: CarObject): CarShell = when (spec.type) {
            CarType.TAXI -> COMPACT
            CarType.POLICE -> SALOON
            // The fire engine has its own body and never reads this; SALOON is returned only so
            // the function is total.
            CarType.FIRE_TRUCK -> SALOON
            CarType.PLAIN -> DEAL[SceneObjectCatalog.candidateIndexOf(spec)]
        }

        /**
         * The deal, indexed by [SceneObjectCatalog.candidateIndexOf].
         *
         * Read as two lanes of five: the near lane is estate, saloon, estate, compact, saloon and
         * the far lane compact, estate, saloon, estate, compact. Four estates, three saloons,
         * three compacts.
         */
        private val DEAL = arrayOf(
            ESTATE, COMPACT, // queue slot 0: near, far
            SALOON, ESTATE, //  queue slot 1
            ESTATE, SALOON, //  queue slot 2
            COMPACT, ESTATE, // queue slot 3
            SALOON, COMPACT, // queue slot 4
        )
    }
}
