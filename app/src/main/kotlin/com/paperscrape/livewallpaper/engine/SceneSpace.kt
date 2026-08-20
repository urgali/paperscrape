package com.paperscrape.livewallpaper.engine

/**
 * The single source of truth for where the scene's ground plane is, how far away a point on it
 * reads as being, and how large a real-world object standing there must be drawn.
 *
 * Everything that touches size or vertical placement resolves through this object: static scene
 * objects, the road and its lanes, the vehicles on it, the pedestrians in front of it and the
 * lake's own inhabitants. Nothing else may define a ground line, a depth scale or a category
 * size, because the previous arrangement -- four multiplicative factors owned by three classes,
 * plus per-category constants that could not be compared with one another -- is what produced
 * five per-asset size patches across v67-v73 and left the scene with a person shorter than a car.
 *
 * Pure Kotlin with no Android types, so every relation below is directly unit-testable.
 *
 * ### The four stages
 *
 * ```
 * finalScale = variantScale        // metres -> local units, from the size table
 *            x sizeVariation       // per-candidate jitter around 1.0
 *            x perspectiveScale(y) // how far away that ground point is
 *            x sceneScale(height)  // the viewport's own size
 * ```
 *
 * Each stage answers exactly one question, and no stage may compensate for another. A sprite that
 * draws too large is a wrong entry in the size table, never a correction bolted onto the call
 * site -- see `AI_PROJECT_RULES.md` 7.3.
 */
object SceneSpace {

    // ---- Stage 4: the viewport ----------------------------------------------------------

    /**
     * The screen height every constant in this file is expressed against.
     *
     * Object sizes used to be absolute canvas pixels while every ground line was a fraction of
     * screen height, so the composition was only correct on one device: the same house occupied
     * a quarter of a small phone's ground band and a tenth of a tablet's. Sizes are now tied to
     * the same unit the placement already used.
     *
     * 2400 px is chosen so a contemporary phone renders at almost exactly 1.0 and the numbers
     * below can be read as pixels without conversion.
     */
    const val REFERENCE_SCREEN_HEIGHT_PX = 2400f

    /** How much larger or smaller this viewport is than the reference one. */
    fun sceneScale(screenHeightPx: Float): Float =
        if (screenHeightPx <= 0f) 1f else screenHeightPx / REFERENCE_SCREEN_HEIGHT_PX

    // ---- The hill layer the ground belongs to -------------------------------------------

    /** Top of the single hill layer, as a fraction of screen height. */
    const val HILL_LAYER_TOP_FRACTION = 0.60f

    /** Height of that layer, as a fraction of screen height. */
    const val HILL_LAYER_HEIGHT_FRACTION = 0.40f

    /**
     * How far down its own band the hill's silhouette is solid paper at *every* x, whatever the
     * top edge's sine wave is doing.
     *
     * `buildBaseHillPath` draws that edge over `0.13 +/- 0.09` of the layer's height, so 0.22 is
     * the lowest the visible paper can start and 0.26 keeps a margin past it. Anything placed
     * above this line can end up standing in open sky. Mountains, the lake's lower edge and the
     * rainbow's base all anchor here too, so they meet the hills with no gap at any x.
     *
     * Re-derive this if the hill path's amplitude ever changes.
     */
    const val HILL_SOLID_TOP_DEPTH_FRACTION = 0.26f

    /** The hill's guaranteed-solid line, as a fraction of screen height. */
    const val GROUND_SOLID_TOP_Y_FRACTION =
        HILL_LAYER_TOP_FRACTION + HILL_LAYER_HEIGHT_FRACTION * HILL_SOLID_TOP_DEPTH_FRACTION

    // ---- Stage 3: the ground plane and its perspective ----------------------------------

    /**
     * The vanishing line of the ground plane, as a fraction of screen height.
     *
     * It sits above the farthest ground the scene actually uses rather than on it: a point *on*
     * the horizon has zero apparent size, so the band has to start below it. The gap between this
     * line and [OBJECT_BAND_TOP_Y_FRACTION] is what decides how strongly the scene reads as
     * receding -- move it up and the scene flattens, move it down and the farthest objects
     * collapse to nothing.
     */
    const val HORIZON_Y_FRACTION = 0.655f

    /** Where a static object of `depthFraction = 0` stands. The hill's guaranteed-solid line. */
    const val OBJECT_BAND_TOP_Y_FRACTION = GROUND_SOLID_TOP_Y_FRACTION

    /**
     * Where a static object of `depthFraction = 1` stands.
     *
     * Bounded by the road: a static object is drawn before the road strip, so anything standing
     * below [roadTopYFraction] would be painted over. The margin between the two is asserted by
     * `SceneSpaceTest` rather than left to be re-derived by hand, which is what the old
     * `ROAD_SAFE_DEPTH_MAX` required -- and that constant capped every category at 0.375 of the
     * depth range, which is why the whole scene stood inside a band 111 px tall.
     */
    const val OBJECT_BAND_BOTTOM_Y_FRACTION = 0.790f

    /**
     * The wheel line of the far traffic lane, as a fraction of screen height.
     *
     * Both lanes moved down by 0.016 in v76.7, keeping their spacing -- and therefore the width of
     * the carriageway -- exactly as it was. The move is what opens [PAVEMENT_FAR_Y_FRACTION] and
     * [PAVEMENT_NEAR_Y_FRACTION] as a band of ground between the buildings and the road, which is
     * where people belong: they were walking below the road's lower edge, which read as standing
     * on the tarmac and a long way from anything they might be walking to.
     */
    const val ROAD_LANE_FAR_Y_FRACTION = 0.834f

    /**
     * The wheel line of the near traffic lane.
     *
     * The spacing narrowed from 0.035 to 0.028 in the v76.6 tuning pass. On a device the
     * carriageway read as a dark band the traffic sat inside with room to spare, rather than as a
     * road: two lanes have to be about a vehicle apart, not comfortably more, or the strip
     * dominates the scene vertically. Narrowing the pair narrows the strip with it, because the
     * road's edges are derived from the lanes -- and the spacing is unchanged since, so moving the
     * road moves it without resizing it.
     */
    const val ROAD_LANE_NEAR_Y_FRACTION = 0.862f

    /**
     * How far past the outer lanes the painted surface reaches, as a fraction of one lane's own
     * half-spacing.
     *
     * The strip stays symmetric about the centre line by construction: each lane owns half of it,
     * plus this shoulder. Expressing the shoulder against the lane spacing rather than in local
     * units means a road built from a different lane pair stays in proportion, instead of gaining
     * a fixed verge that is generous at one spacing and invisible at another.
     */
    const val ROAD_SHOULDER_LANE_FRACTION = 0.16f

    /**
     * Where the farther row of pedestrians walks.
     *
     * **Between the buildings and the road, not in front of the road.** Until v76.7 both rows sat
     * below the carriageway, where a pedestrian read as standing on the road surface and as
     * having nothing to do with the village behind it. They now occupy the strip of ground the
     * road's downward move opened up: below [OBJECT_BAND_BOTTOM_Y_FRACTION], where the nearest
     * buildings stand, and above [roadTopYFraction].
     *
     * A consequence worth stating, because it looks like a regression and is not: people are
     * drawn considerably smaller than they were. They are further away now, and the projection
     * charges them for it exactly as it charges everything else. [PERSON_METRES_TALL] carries a
     * small reduction on top of that; almost all of the change is the move.
     */
    const val PAVEMENT_FAR_Y_FRACTION = 0.795f

    /** Where the nearer row of pedestrians walks. */
    const val PAVEMENT_NEAR_Y_FRACTION = 0.807f

    /**
     * The ground line at which [perspectiveScaleAt] is exactly 1, so the size table below reads
     * directly as "pixels at this line, on a reference-height screen".
     *
     * **Its own constant, not an alias for a lane.** It was defined as [ROAD_LANE_NEAR_Y_FRACTION]
     * until v76.7, which meant the metre was defined in terms of a composition element: moving the
     * road one step down rescaled every object in the scene, because the denominator below moved
     * with it. The reference line belongs to the projection. It keeps the value the near lane
     * happened to have, so nothing changed size when the two were separated.
     */
    const val REFERENCE_Y_FRACTION = 0.846f

    /** Where an object of [depthFraction] stands, as a fraction of screen height. */
    fun groundYFraction(depthFraction: Float): Float {
        val d = depthFraction.coerceIn(0f, 1f)
        return OBJECT_BAND_TOP_Y_FRACTION + d * (OBJECT_BAND_BOTTOM_Y_FRACTION - OBJECT_BAND_TOP_Y_FRACTION)
    }

    /**
     * How large something standing at [yFraction] reads, relative to the same thing standing at
     * [REFERENCE_Y_FRACTION].
     *
     * This is the whole perspective model: on a flat ground plane seen from a fixed viewpoint,
     * apparent size is proportional to the distance below the horizon. Expressing it that way
     * rather than as a separate hand-tuned curve is what makes the road, the pavement and the
     * object band share one projection -- a pedestrian in front of the road is automatically
     * larger than a car on it, by exactly the amount their two ground lines imply, with nothing
     * to keep in step by hand.
     *
     * Clamped at zero rather than allowed to go negative: a caller passing a y above the horizon
     * is asking about something that is not on this ground plane at all, and a negative scale
     * would mirror the sprite rather than fail.
     */
    fun perspectiveScaleAt(yFraction: Float): Float =
        ((yFraction - HORIZON_Y_FRACTION) / (REFERENCE_Y_FRACTION - HORIZON_Y_FRACTION)).coerceAtLeast(0f)

    /** [perspectiveScaleAt] evaluated at the ground line of [depthFraction]. */
    fun depthScale(depthFraction: Float): Float = perspectiveScaleAt(groundYFraction(depthFraction))

    // ---- Road geometry ------------------------------------------------------------------

    /** The distance between the two canonical lanes, as a fraction of screen height. */
    const val CANONICAL_LANE_SPACING_FRACTION = ROAD_LANE_NEAR_Y_FRACTION - ROAD_LANE_FAR_Y_FRACTION

    /**
     * Below this spacing a lane pair is treated as degenerate and the canonical spacing is used
     * instead.
     *
     * A theme saved before v76.2 put every car on one lane fraction, so its "pair" has a spacing
     * of zero -- and a margin derived from zero is zero, which paints the road as a hairline.
     * Half the canonical spacing is comfortably below any real pair and comfortably above the
     * float noise in a saved value.
     */
    const val MIN_ROAD_LANE_SPACING_FRACTION = CANONICAL_LANE_SPACING_FRACTION / 2f

    /**
     * How far the painted road reaches beyond its outermost lane, given the lane pair in use, as
     * a fraction of screen height.
     *
     * Derived from the lanes rather than stated outright, so a custom theme saved with its own
     * lane pair still gets a strip centred on its own traffic. For the canonical lanes it yields
     * exactly [roadTopYFraction] and [roadBottomYFraction].
     *
     * **The lane pair must come from the theme's whole car list, never from the cars a density
     * setting happens to have kept.** Feeding it the filtered list makes the road's own width a
     * function of the density slider: thinning the traffic until only one lane survives collapses
     * the spacing to zero and the strip with it. The road is terrain, and terrain does not depend
     * on how much traffic is on it. The degenerate guard below is a second line of defence, not
     * the fix for that.
     */
    fun roadEdgeMarginFraction(minLaneYFraction: Float, maxLaneYFraction: Float): Float {
        val spacing = maxLaneYFraction - minLaneYFraction
        val effective = if (spacing >= MIN_ROAD_LANE_SPACING_FRACTION) spacing else CANONICAL_LANE_SPACING_FRACTION
        return (effective / 2f) * (1f + ROAD_SHOULDER_LANE_FRACTION)
    }

    /** The canonical road's top edge, as a fraction of screen height. */
    fun roadTopYFraction(): Float =
        ROAD_LANE_FAR_Y_FRACTION - roadEdgeMarginFraction(ROAD_LANE_FAR_Y_FRACTION, ROAD_LANE_NEAR_Y_FRACTION)

    /** The canonical road's bottom edge, as a fraction of screen height. */
    fun roadBottomYFraction(): Float =
        ROAD_LANE_NEAR_Y_FRACTION + roadEdgeMarginFraction(ROAD_LANE_FAR_Y_FRACTION, ROAD_LANE_NEAR_Y_FRACTION)

    /**
     * Road markings, in pixels at [REFERENCE_SCREEN_HEIGHT_PX].
     *
     * These belong to the road surface rather than to any object standing on it, so they scale
     * with the viewport and not with any object's depth. The values reproduce what the strip
     * looked like while they were local units multiplied by a global factor of two.
     */
    const val ROAD_EDGE_STROKE_PX = 4f
    const val ROAD_CENTRE_LINE_STROKE_PX = 8f
    const val ROAD_DASH_LENGTH_PX = 52f
    const val ROAD_DASH_GAP_PX = 40f

    // ---- Traffic and pedestrians ---------------------------------------------------------

    /**
     * The near lane's speed, in screen widths per second.
     *
     * One speed per lane, never one per car: a lane is a queue, and a queue only keeps its
     * spacing if nothing in it overtakes.
     */
    const val CAR_SPEED_NEAR = 0.075f

    /**
     * The far lane's speed, derived rather than chosen.
     *
     * Two cars travelling at the same real speed cover the same ground per second, so the farther
     * one must cross the screen more slowly by exactly the ratio its distance implies. Picking
     * this by hand is how the two lanes previously ended up merely *looking* unsynchronised
     * rather than being consistent with their own depths.
     */
    val CAR_SPEED_FAR: Float
        get() = CAR_SPEED_NEAR * perspectiveScaleAt(ROAD_LANE_FAR_Y_FRACTION) /
            perspectiveScaleAt(ROAD_LANE_NEAR_Y_FRACTION)

    /** Walking speed at [PAVEMENT_NEAR_Y_FRACTION], in screen widths per second. */
    const val PEDESTRIAN_SPEED_NEAR = 0.026f

    /**
     * Walking speed at [PAVEMENT_FAR_Y_FRACTION], derived from the near row the same way
     * [CAR_SPEED_FAR] is derived from [CAR_SPEED_NEAR].
     */
    val PEDESTRIAN_SPEED_FAR: Float
        get() = PEDESTRIAN_SPEED_NEAR * perspectiveScaleAt(PAVEMENT_FAR_Y_FRACTION) /
            perspectiveScaleAt(PAVEMENT_NEAR_Y_FRACTION)

    // ---- Stage 1: the size table --------------------------------------------------------

    /**
     * Local units per metre at [REFERENCE_Y_FRACTION], on a reference-height screen.
     *
     * Every category's base scale is derived from this and its own declared real height, so a
     * sprite drawn at any internal scale still lands at the right physical size. That derivation
     * is the point: measured on their own artwork, the V2 sprites run from roughly 13 units per
     * metre for a shop front to 46 for a person -- a spread of three and a half times -- and no
     * set of hand-authored per-category multipliers had ever corrected for it.
     */
    const val PIXELS_PER_METRE_AT_REFERENCE = 45f

    /*
     * **Raised from 40 in v2.5, and this is the only place the world's size is stated.**
     *
     * On a Pixel 9 the scene read as a model of a place rather than a place: correctly
     * proportioned and too small to have presence. One number governs that, because every
     * category's base scale is `metres * PIXELS_PER_METRE_AT_REFERENCE / spriteUnits` -- so
     * raising it enlarges houses, buildings, trees, people, cars and every other standing object
     * by the same 12.5% and **cannot change a single ratio between them**. A per-category
     * multiplier pass would have been the other way to do it and would have had to be argued
     * object by object, with the ratios as the thing at risk.
     *
     * 12.5% is deliberately short of what the impression alone would ask for. The road is laid out
     * in fractions of the screen and does not scale with this, so the objects standing on it grow
     * into a fixed band: a car is 1.45 m, which was 58 px against a 67 px lane spacing and is now
     * 65 px. Past this the near lane's traffic starts meeting the far lane's. Everything else
     * grows upward from a ground line and has room.
     *
     * The lake keeps its own metric (`LAKE_PIXELS_PER_METRE`) and is deliberately not raised with
     * this: it is a band at the horizon, and growing its boats and dolphins in step with the
     * foreground would flatten the depth the two separate metrics exist to express.
     */

    /**
     * ### On the word "metres"
     *
     * The heights below are what each object should **read as**, not a physical measurement. They
     * started as real-world sizes and remain within sight of them, because a table anchored to
     * something real is the only kind that can be argued about; but a live wallpaper is looked at
     * for a second at a time on a screen held at arm's length, and a few of them were tuned away
     * from the physical value in v76.6 after a device pass, for legibility:
     *
     * - **A person is 1.9 m, not 1.75.** At the physical height an adult was a readable silhouette
     *   and no more; the scene is meant to have people in it, not people-shaped marks. Trimmed
     *   from 2.0 in v76.7, where the foreground row read as slightly overscaled.
     * - **A car is 1.45 m, not 1.55.** The V2 car sprite is stubby -- 100 units long against 48
     *   tall, where a real car is nearer three to one -- so matching its height exactly made it
     *   read as bulky next to a person. Taking it slightly under corrects the impression the
     *   proportions of the artwork create.
     * - **A tower is 17 m, not 20.** Physically defensible either way, but at 20 it dominated the
     *   foreground it is supposed to sit behind.
     * - **A tree is 9.8 m**, **a gift 0.95 m** and **a parasol 2.9 m**, all nudged up for presence.
     * - **A rabbit is 0.9 m and an Easter egg 1.0 m**, well past life size. They are the Easter
     *   theme's two subjects and at their real heights neither could be made out at all.
     *
     * Departures are recorded here rather than made silently, and they are departures from a
     * stated anchor rather than free parameters. The hierarchy in `DESIGN_NOTES.md` §5 is what
     * must hold; the individual numbers serve it.
     */

    /** [metresTall] of real object drawn across [spriteUnitsTall] of local units. */
    fun scaleForHeight(metresTall: Float, spriteUnitsTall: Float): Float =
        metresTall * PIXELS_PER_METRE_AT_REFERENCE / spriteUnitsTall

    /**
     * Every distinct thing the scene draws standing on the ground, with the real height it is
     * meant to read as and the local-unit height its own drawing occupies.
     *
     * **Height is the governed dimension, and width follows the artwork.** The V2 sprites are
     * stylised: a cottage is drawn narrower than a real one and a car shorter than a real one.
     * Governing both dimensions is impossible without redrawing them, and governing width instead
     * makes a person shorter than a car -- the exact complaint this table exists to settle.
     * Heights are also what the eye compares in an elevation like this one, where every object
     * meets the same ground line.
     *
     * [spriteUnitsTall] is the drawn extent that carries the object's identity, not necessarily
     * its whole bounding box: a shop's height is its wall, not the top of the sign hanging above
     * it, and a tree's is trunk plus canopy. Each value is measured from the call site that draws
     * it, and `SceneSpaceTest` pins the relations between them.
     */
    enum class SceneVariant(val metresTall: Float, val spriteUnitsTall: Float) {
        /** A one-bay cottage. Its own door is 38 units, which fixes the internal scale at 2 m. */
        // Raised from 5.8 m in v2.5 with the second window. One window, a door pushed to one
        // side and a 5.8 m ridge made a cabin; the height is what settles whether the elevation
        // reads as a house, and 6.4 puts it in a defensible relation to the 7.6 m large house
        // rather than at three quarters of it.
        HOUSE_SMALL(6.4f, 110f),

        /** Two storeys plus a roof; its door is 45 units on the same reading. */
        HOUSE_LARGE(7.6f, 145f),

        /** Trunk to crown; the trunk is 36 % of it. Nudged up in v76.6 for presence beside the houses. */
        TREE(9.8f, 122f),

        /** Trunk plus frond fan, ground to the top of the blades. */
        PALM_TREE(8f, 90.33f),

        /**
         * The office block. A backdrop rather than a landmark, but it has to out-top the houses
         * in front of it: at 17 m against v2.5's enlarged 7.6 m house it had stopped reading as a
         * different class of building. Raised to 21 m in v2.7 with the shops, so the hierarchy
         * the scene is supposed to show -- tower over shop over house -- holds by height rather
         * than by depth alone.
         */
        TOWER(21f, 196f),

        /**
         * Shop front, measured to the top of the wall and not to its hanging sign.
         *
         * **Raised from 5.2 m in v2.7, and the old number was the wrong shape of wrong.** It was
         * measured as a single storey, which put a restaurant *below* a 6.4 m cottage and a bar
         * below that -- so a parade of shops read as outbuildings behind the houses. A commercial
         * frontage is a taller storey than a domestic one and usually carries something above it;
         * 9 m is two domestic courses plus the parapet the sign hangs off, which is what the
         * artwork actually draws.
         */
        RESTAURANT(9f, 60f),

        /** The same, one course lower. */
        BAR(8.4f, 55f),

        /** Pole to canopy rim. Raised in v76.7: at 2.3 m it had shrunk out of the composition. */
        PARASOL(2.9f, 84f),

        /** Three spheres and a hat. */
        SNOWMAN(1.7f, 75f),

        /** A large wrapped present, measured over the bow. Nudged up in v76.6 -- at 0.6 m it read as a speck. */
        GIFT(0.95f, 42f),

        /** An emperor penguin, which is the size the artwork is drawn at. */
        PENGUIN(1.1f, 46f),

        /**
         * A rabbit sitting up, ears included. Raised from 0.55 m in v76.10: at the physical
         * height it was a couple of dozen pixels of the same colour as the ground behind it and
         * simply did not read. The Easter theme's two subjects are the eggs and the rabbit, and
         * an object nobody can see is not carrying a theme.
         */
        BUNNY(0.9f, 62f),

        /** An oversized decorative garden egg, not a hen's egg. Raised with the rabbit, and for
         * the same reason -- see [BUNNY]. */
        EASTER_EGG(1f, 40f),

        /** A prize pumpkin, measured over the stem. */
        PUMPKIN(0.5f, 42f),
        ;

        /** The scale this variant is drawn at when it stands on [REFERENCE_Y_FRACTION]. */
        val baseScale: Float = scaleForHeight(metresTall, spriteUnitsTall)
    }

    /**
     * The depth below which a building candidate is drawn as a [SceneVariant.TOWER] rather than
     * as a shop.
     *
     * One category covers both because they share a colour, a density slider and a candidate
     * pool. Which one a candidate becomes used to be a hash of its horizontal position, so a
     * four-metre shop front could land at the very back of the scene and a tower at the very
     * front. Deciding it by depth instead puts the towers behind the village and the street-level
     * businesses among the houses, which is both what the objects are and what the perspective
     * already implies.
     */
    const val BUILDING_TOWER_MAX_DEPTH = 0.30f

    /**
     * The vehicles, kept out of [SceneVariant] because they are placed by lane rather than by
     * depth and never appear in the static object list.
     *
     * A car's governed height is roof to wheel contact, 48 local units; the fire engine's own
     * body reaches 68 over the same wheel line.
     */
    const val CAR_METRES_TALL = 1.45f
    const val CAR_SPRITE_UNITS_TALL = 48f
    const val FIRE_TRUCK_METRES_TALL = 2.9f
    const val FIRE_TRUCK_SPRITE_UNITS_TALL = 68f

    /** Base scale for the low-sedan silhouette shared by the plain, police and taxi cars. */
    val CAR_BASE_SCALE: Float get() = scaleForHeight(CAR_METRES_TALL, CAR_SPRITE_UNITS_TALL)

    /** Base scale for the fire engine, which has its own taller body. */
    val FIRE_TRUCK_BASE_SCALE: Float
        get() = scaleForHeight(FIRE_TRUCK_METRES_TALL, FIRE_TRUCK_SPRITE_UNITS_TALL)

    /**
     * An adult pedestrian: 1.75 m across the 80 local units of content the walk sprites carry
     * inside their 84-unit canvas.
     *
     * The children are drawn shorter *within the same canvas* -- 62 units against the adults' 80
     * -- so one scale gives them their own 0.77 of adult height with no second entry here.
     */
    const val PERSON_METRES_TALL = 1.9f
    const val PERSON_SPRITE_UNITS_TALL = 80f

    /** Base scale for a walking person. */
    val PERSON_BASE_SCALE: Float get() = scaleForHeight(PERSON_METRES_TALL, PERSON_SPRITE_UNITS_TALL)

    // ---- The lake -----------------------------------------------------------------------

    /**
     * The lake's own metric, separate from the ground plane's.
     *
     * The water sits at and slightly above the horizon, where [perspectiveScaleAt] is at or near
     * zero, so nothing floating on it can take its size from the ground projection -- it would
     * vanish. What its inhabitants do need is to be the right size *relative to each other*,
     * which they were not: the dolphin was drawn longer than the sailboat.
     *
     * Raised from 15 in v76.6. Getting the two right relative to each other left both close to
     * invisible on a device, which is a different failure from the one it fixed. Their ratio is
     * what the metric protects; how large the pair is on screen is a legibility decision, and one
     * number moves both together.
     */
    const val LAKE_PIXELS_PER_METRE = 21f

    /** Bow to stern of the hull, over its 84 local units of content. */
    const val SAILBOAT_METRES_LONG = 6.5f
    const val SAILBOAT_SPRITE_UNITS_LONG = 84f

    /** Nose to fluke, over the 114.7 local units the body's content occupies. */
    const val DOLPHIN_METRES_LONG = 2.6f
    const val DOLPHIN_SPRITE_UNITS_LONG = 114.7f

    /** How high the animal clears the water at the top of its arc, in metres. */
    const val DOLPHIN_LEAP_METRES = 1.8f

    /** Base scale for the sailboat's hull and sail, which are drawn as one object. */
    val SAILBOAT_BASE_SCALE: Float
        get() = SAILBOAT_METRES_LONG * LAKE_PIXELS_PER_METRE / SAILBOAT_SPRITE_UNITS_LONG

    /** Base scale for the dolphin. */
    val DOLPHIN_BASE_SCALE: Float
        get() = DOLPHIN_METRES_LONG * LAKE_PIXELS_PER_METRE / DOLPHIN_SPRITE_UNITS_LONG

    // ---- Stage 2: per-candidate size variation ------------------------------------------

    /**
     * How much one candidate of a category may differ in size from another, as a fraction either
     * side of 1.
     *
     * `StaticSceneObject.scale` used to carry the category's whole base size, which meant two
     * unrelated decisions -- "what kind of thing is this" and "is this one slightly bigger than
     * its neighbour" -- shared a field, and neither could be read without knowing the other. It
     * is now purely the second, so a value of 1 always means "the size this category is supposed
     * to be".
     */
    const val SIZE_VARIATION_SPREAD = 0.16f

    /** The smallest a per-candidate variation may shrink an object to. */
    const val MIN_SIZE_VARIATION = 0.5f

    /**
     * Converts a pre-`SceneSpace` absolute `scale` into the relative variation replacing it.
     *
     * Persisted custom themes hold the old absolute values, and the base scales they were written
     * against are the only way to read them. Kept here beside the new table rather than inside the
     * migration so both halves of the conversion are visible in one place.
     */
    fun legacyBaseScaleFor(type: SceneObjectType): Float = when (type) {
        SceneObjectType.HOUSE -> 1.5f
        SceneObjectType.SKYSCRAPER -> 1.3f
        SceneObjectType.TREE, SceneObjectType.PALM_TREE -> 1.3f
        SceneObjectType.PARASOL -> 1.3f
        SceneObjectType.SNOWMAN -> 1.3f
        SceneObjectType.GIFT -> 1.25f
        SceneObjectType.PENGUIN -> 1.25f
        SceneObjectType.BUNNY -> 1.25f
        SceneObjectType.EASTER_EGG -> 1.1f
        SceneObjectType.PUMPKIN -> 1.2f
        SceneObjectType.CAR -> 1f
    }
}
