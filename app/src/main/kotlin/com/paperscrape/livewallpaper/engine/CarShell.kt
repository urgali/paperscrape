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
 * They also share the whole vertical layout of the cabin -- glass top [CAR_GLASS_Y_UNITS], sill
 * [CAR_SILL_Y_UNITS], and therefore the seated-occupant scale and both seat positions. Only the
 * plan changes: length, roof line, wheelbase, where the glass begins and ends. An occupant is
 * consequently the same size in every car, which is what the height table asks for and what
 * `OccupantHeadFitTest` measures.
 *
 * Ground contact is [SceneObjectRenderer.VEHICLE_GROUND_Y_UNITS] for all of them, the wheels are
 * [SceneObjectRenderer.CAR_WHEEL_RADIUS_UNITS] inside arches cut one unit larger and concentric
 * with the tyre, and the lamps are the *same four sprites* on every body -- see [lampFrontXUnits].
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
    val glassXUnits: Float,
    val glassWidthUnits: Float,
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
    /** A — the Compact: 92 units, the shortest and the tallest, cab-forward with a hatch tail. */
    COMPACT(
        bodyRes = R.drawable.car_body_compact, glassRes = R.drawable.car_window_compact,
        bodyXUnits = -48.5f, bodyYUnits = -20f, unitsTall = 57f, lengthUnits = 92f,
        glassXUnits = -28f, glassWidthUnits = 62f,
        wheelFrontXUnits = -30f, wheelRearXUnits = 30f,
        roofFrontXUnits = -20.4f, roofRearXUnits = 28f,
        lampFrontXUnits = -46.4f, lampFrontYUnits = 6.4f,
        lampRearXUnits = 38.5f, lampRearYUnits = 5.3f,
    ),

    /** B — the Saloon: 108 units, three boxes, the family's reference length. */
    SALOON(
        bodyRes = R.drawable.car_body_saloon, glassRes = R.drawable.car_window_saloon,
        bodyXUnits = -56.5f, bodyYUnits = -19f, unitsTall = 56f, lengthUnits = 108f,
        glassXUnits = -27f, glassWidthUnits = 59f,
        wheelFrontXUnits = -36f, wheelRearXUnits = 36f,
        roofFrontXUnits = -21.6f, roofRearXUnits = 22f,
        lampFrontXUnits = -54.4f, lampFrontYUnits = 7.4f,
        lampRearXUnits = 46.6f, lampRearYUnits = 4f,
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
     * [glassWidthUnits] spans both of its panes: the cabin, which seats the two occupants, and
     * the third window over the load bay. Only the cabin pane is measured against the occupant
     * criteria -- the load bay is not cabin glazing and would flatter the light and flatten the
     * fill if it were counted.
     */
    ESTATE(
        bodyRes = R.drawable.car_body_estate, glassRes = R.drawable.car_window_estate,
        bodyXUnits = -66.5f, bodyYUnits = -20.8f, unitsTall = 57.8f, lengthUnits = 124f,
        glassXUnits = -30f, glassWidthUnits = 81f,
        wheelFrontXUnits = -42f, wheelRearXUnits = 38f,
        roofFrontXUnits = -21f, roofRearXUnits = 48f,
        lampFrontXUnits = -64.4f, lampFrontYUnits = 5.6f,
        lampRearXUnits = 52.6f, lampRearYUnits = 4.2f,
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
