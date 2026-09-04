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

    /**
     * How many pixels one scene metre occupies on this viewport, at [REFERENCE_Y_FRACTION].
     *
     * The single conversion between "how big is this thing in the world" and "how big is it on
     * this screen". Every category already goes through it indirectly -- a size table in metres,
     * [PIXELS_PER_METRE_AT_REFERENCE], then [sceneScale] -- and v4.5 brought the atmospheric
     * effects onto the same route, which is why it is now named rather than spelled out at each
     * call site.
     *
     * **A consequence worth stating, because a scaling decision keeps being made without it:** a
     * metre is `45 x screenHeight / 2400` pixels, so the viewport always shows the same
     * `2400 / 45 = 53.3` metres of world however tall it is. A fixed number of particles is
     * therefore already a fixed density *per square metre of world* on every device -- the count
     * does not need to scale with the screen, only the size of each particle does.
     */
    fun pixelsPerMetre(screenHeightPx: Float): Float =
        PIXELS_PER_METRE_AT_REFERENCE * sceneScale(screenHeightPx)

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
        // Lowered from 6.4 m in v2.8 to the large house's own metres-per-unit. At 6.4 the small
        // house's windows and door were 11 % larger than the large house's and its sills sat
        // 1.57 m up against the large house's 1.20 m -- the same family drawn at two scales. The
        // facade also went from 86 to 96 units wide, which is width and not height, so it is in
        // the artwork rather than here.
        HOUSE_SMALL(5.76f, 110f),

        /** Two storeys plus a roof; its door is 45 units on the same reading. */
        HOUSE_LARGE(7.6f, 145f),

        /**
         * Trunk to crown; the trunk is 53 % of it.
         *
         * **118 units, not 122.** Measured from the two blits that make a tree: `tree_trunk`
         * (96x186 px = 32x62 u) at (-16,-62), and `tree_canopy` (303x198 px = 101x66 u) at
         * `TreeSpriteLayout.CANOPY_Y` = -80 inside the canopy's own `translate(0,-38)`, so the
         * crown tops out at -118. The entry said 122 and the tree was therefore reading 9.48 m
         * rather than the 9.8 it declared.
         *
         * **v4.21 redrew both sprites and this entry did not move**, which is the point worth
         * recording: the "Quercia larga" is a wider tree, not a taller one. The crown still tops
         * out at -118 and the foot still stands on 0, so the metres-per-unit the whole size table
         * is argued against is untouched and no other object's scale had to be renegotiated. The
         * trunk's share of the height went from 37 % to 53 % because the stocky forked stem is
         * 62 units against the old rod's 44 -- the crown sits lower and spreads instead of
         * perching. Height stays the governed dimension; width followed the artwork, exactly as
         * this enum's own preamble says it should.
         *
         * The metre is what moved, not the artwork. 9.8 was tuned by eye in v76.6 "for presence
         * beside the houses", so the drawn size is the decision and the declaration was the thing
         * that had drifted; 9.479 is 118 units at the metres-per-unit this tree has always been
         * drawn at, which leaves the picture where it is to within 0.02 px at the reference
         * resolution.
         */
        TREE(9.479f, 118f),

        /**
         * The Christmas fir that replaces one tree in three while the Christmas layer is on.
         *
         * **122 units at [TREE]'s own metres-per-unit, which is why it reads taller than a leafy
         * tree rather than shorter.** `variantFor` never returns FIR -- a fir is a *state* of a
         * TREE candidate (`SceneObjectRenderer.standsAsFir`), so it is drawn under TREE's scale
         * and this `baseScale` is not applied; the only other reference is a `-> Unit` arm in the
         * draw dispatch. `tree_fir` is 240x366 px = 80x122 u blitted at (-40,-122), so a fir
         * occupies 122 units where the leafy tree occupies 118, and at 0.0803 m/unit that is
         * 9.8 m against the tree's 9.479. (v4.21 redrew the fir into the "Quercia larga" family --
         * stocky, three full skirts, a star at the tip -- and kept it at 122 units for the reason
         * this paragraph gives: one metre governs both, so a fir cannot drift out of scale with
         * the wood it stands in.)
         *
         * This entry used to say 9.3 m, and `SceneSpaceTest` pinned "a fir is shorter than a
         * tree". **Both were wrong, and the project said so itself.** v2.8 introduced the entry as
         * `9.3 m / 122 u` and explained it in the same breath: *"FIR shares TREE's 122 units so
         * one metre governs both: a fir cannot drift out of scale with the wood it stands in"*
         * (`RELEASE_HISTORY.md`). One metre governing both is exactly what sharing a scale means,
         * and it is what the code has always done -- but a fir at 9.3/122 would have had a
         * *different* metre-per-unit from a tree at 9.8/122, which is the drift that sentence
         * exists to forbid. The stated intent and the stated number contradicted each other; the
         * intent is the one the renderer implements, so the number is what moved.
         *
         * Nothing is drawn differently by this change: the value was never read.
         */
        FIR(9.8f, 122f),

        /** Trunk plus frond fan, ground to the top of the blades. */
        PALM_TREE(8f, 90.33f),

        /**
         * The office block. A backdrop rather than a landmark, but it has to out-top the houses
         * in front of it: at 17 m against v2.5's enlarged 7.6 m house it had stopped reading as a
         * different class of building. Raised to 21 m in v2.7 with the shops, so the hierarchy
         * the scene is supposed to show -- tower over shop over house -- holds by height rather
         * than by depth alone.
         */
        // Lowered from 21 m in v2.8 with a coarser window grid. At 21 m over an 18-unit grid a
        // window read as 0.86 m and a storey as under a metre; at 16.8 m over a 27-unit grid a
        // window is 1.2 m and a floor 2.3 m, which is a building people work in. Still comfortably
        // the tallest thing in the scene.
        //
        // **v4.15: 15.6 over 182, and the picture did not move.** 196 was measured to the tip of
        // the aerial `drawSkyscraperBuilding` strokes above the setback, and the rule at the top of
        // this enum excludes exactly that -- "a shop's height is its wall, not the top of the sign
        // hanging above it", which is why RESTAURANT declares 96 for a wall it draws at 96 and says
        // nothing about its own hanging sign. Every other variant here matches its blits to the
        // unit; the tower was the only one measuring an appendage, so it was drawing its 182 units
        // of facade-plus-setback at 182/196 of the size 16.8 m asks for and reading as 15.6.
        //
        // The metres-per-unit is untouched at 0.0857, so the scale `metres * pixelsPerMetre /
        // spriteUnitsTall` comes out at the same 3.857 px per unit it always has: **this changes no
        // pixel**, and the window-grid reasoning above still holds exactly. What changes is that
        // the number now describes the building. `BuildingHeightDeclarationTest` reads the blits and
        // fails if any variant drifts from them again.
        TOWER(15.6f, 182f),

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
        // **Both numbers changed together, and that is the whole correction.** v2.7 raised the
        // metres and left the 60-unit single-storey wall, which multiplied every opening the
        // artwork draws: a 4.2 m door and a 5.25 m sign on a building meant to read as a shop. The
        // wall is now 96 units -- a shop front with a residential storey over it -- and 8.2 m over
        // 96 puts the door back at 2.40 m, beside a house door of 2.36 m. The building is bigger
        // than a house by mass, not by openings.
        RESTAURANT(8.2f, 96f),

        /** The same, one course lower. */
        /** The same, one course lower: 92 units of wall at 7.7 m gives a 2.35 m door. */
        BAR(7.7f, 92f),

        /** Pole to canopy rim. Raised in v76.7: at 2.3 m it had shrunk out of the composition. */
        PARASOL(2.9f, 84f),

        /** Three spheres and a hat. 74 units: `snowman_body` is 114x222 px = 38x74 u at (-19,-74),
         *  and the nose and scarf sit inside it. The code's own "75-unit canvas" comment at the
         *  blit was one unit out; 1.6773 is 74 units at the metre-per-unit it has always drawn at. */
        SNOWMAN(1.6773f, 74f),

        /** A large wrapped present, measured over the bow. Nudged up in v76.6 -- at 0.6 m it read
         *  as a speck, and that nudge is a decision about the drawn size, so it is preserved here.
         *  40 units, not 42: `gift_box` (40x30 u at (-20,-30)) plus `gift_ribbon` (40x40 u at
         *  (-20,-40)) span -40..0. */
        GIFT(0.9048f, 40f),

        /**
         * An emperor penguin, which is the size the artwork is drawn at -- 49 units, not 46.
         *
         * It is also the **only** static whose art crosses the ground line: `penguin_body`
         * (28x44 u at (-14,-45)) stops one unit short of it, and `penguin_feet` (20x4 u) is
         * blitted at (-10, **0**), so the feet occupy y 0..+4 while every other static -- tree,
         * gift, snowman, bunny -- ends exactly at y=0. That is a separate question from this
         * table, and moving the feet would move the bird, so it is left as it is and recorded
         * here rather than silently corrected.
         */
        PENGUIN(1.1717f, 49f),

        /**
         * A rabbit sitting up, ears included. Raised from 0.55 m in v76.10: at the physical
         * height it was a couple of dozen pixels of the same colour as the ground behind it and
         * simply did not read. The Easter theme's two subjects are the eggs and the rabbit, and
         * an object nobody can see is not carrying a theme.
         */
        BUNNY(0.8855f, 61f),

        /** An oversized decorative garden egg, not a hen's egg. Raised with the rabbit, and for
         * the same reason -- see [BUNNY]. */
        EASTER_EGG(1f, 40f),

        /**
         * A prize pumpkin, measured over the stem.
         *
         * **0.5 m through v4.15, 0.85 in v4.16, 1.0 here, and each step was a device pass rather
         * than a calculation.** At 0.5 it was half an [EASTER_EGG] and read on a OnePlus 6T as an
         * orange bead beside the gifts and the snowmen; 0.85 was reported as still small. 0.85,
         * 1.00 and 1.10 were then rendered side by side on the phone with a gift, a snowman, a
         * penguin and an egg in the same frame: **1.10 stands in front of the penguin and hides
         * it**, and 1.00 reads as a pumpkin while leaving its neighbours alone. Level with the egg
         * is where it belongs -- both are decorative props of about the same bulk.
         *
         * `SceneSpaceTest` holds the floor (not smaller than a gift or a bunny) and the ceiling
         * (shorter than a penguin) so it cannot drift back down.
         */
        PUMPKIN(1.0f, 42f),
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
     * Within the shop band ([BUILDING_TOWER_MAX_DEPTH]..0.80), the depth below which a shop is
     * the restaurant and above which it is the bar.
     *
     * Which of the two a shop becomes used to be a hash of its horizontal position — sanctioned
     * at the time as "interchangeable at any depth", which is true of the *artwork* but made the
     * shop's identity a function of the hundredth-of-a-tile its jitter (or the visibility pass)
     * happened to land it on: nothing stopped every shop in a scene from hashing to the same
     * storefront, and the rc2 frames delivered exactly that — two identical trattorias in one
     * screen. Depth is the property that already decides what a building *is* (tower vs shop,
     * [BUILDING_TOWER_MAX_DEPTH]), it never changes after generation, and the catalogue keeps at
     * most one shop per half-band ([SceneObjectCatalog] `singleShopPerVariant`), so it also
     * decides *which* shop — stably, whatever the visibility pass does to x.
     */
    const val SHOP_VARIANT_DEPTH_SPLIT = 0.55f

    /**
     * The vehicles, kept out of [SceneVariant] because they are placed by lane rather than by
     * depth and never appear in the static object list.
     *
     * A car's governed height is roof to wheel contact, 48 local units; the fire engine's own
     * body reaches 68 over the same wheel line.
     */
    /**
     * **What one local unit of car artwork is worth in metres.** This, not a per-body height, is
     * the constant the vehicle family is built on since v4.19.
     *
     * v4.18 had one car, so a height in metres over a height in units said the same thing. v4.19
     * ships three ([CarShell]) and they are deliberately *not* the same height: the compact
     * stands 57 units, the saloon 56, the estate 57.8. Governing each one by its own
     * metres-over-units would give each its own scale, and a unit would then mean a different
     * number of pixels on each body -- which is precisely how three drawings stop reading as one
     * set. Fixing the metre-per-unit instead makes every body, every wheel radius, every corner
     * radius and every seat position directly comparable across the family, and lets
     * [CAR_BASE_SCALE] stay a single number.
     *
     * The value is v4.18's own: 1.51 m over 50 units. **Nothing about how large a car draws
     * changed by arithmetic** -- what changed is that the artwork itself grew, and it grew in
     * metres because it grew in units.
     */
    const val CAR_UNIT_METRES = 1.51f / 50f

    /**
     * The saloon, kept as the family's reference pair for the height table and for every test
     * that compares a car against a pedestrian.
     *
     * These are [CarShell.SALOON]'s own numbers, restated here because the height table is where
     * the scene's sizes are read from. The other two bodies derive their metres the same way,
     * through [CAR_UNIT_METRES]; see [CarShell.metresTall].
     */
    const val CAR_SPRITE_UNITS_TALL = 56f
    const val CAR_METRES_TALL = CAR_UNIT_METRES * CAR_SPRITE_UNITS_TALL

    /**
     * The fire engine keeps a metre-per-unit of its own (2.9 m over 68 units = 0.0426 m/u against
     * the cars' 0.0302), and that is deliberate rather than an oversight. It is a different
     * vehicle drawn at a different size; forcing it onto the cars' unit would need a 96-unit-tall
     * canvas to reach 2.9 m, which costs 0.29 MiB of decoded sprite for no visible gain. What has
     * to match is the *rendered* treatment, and v4.19's redraw achieves that by dividing its
     * radii by the ratio between the two units -- see `firetruck_body.svg`.
     */
    const val FIRE_TRUCK_METRES_TALL = 2.9f
    const val FIRE_TRUCK_SPRITE_UNITS_TALL = 68f

    /**
     * The one scale every car body is drawn at.
     *
     * Because metres are [CAR_UNIT_METRES] times units for all three bodies, this reduces to the
     * metre-per-unit times the reference projection: the body's own height cancels out. Three
     * silhouettes, one scale, and a local unit that means the same pixel on each of them.
     */
    val CAR_BASE_SCALE: Float get() = scaleForHeight(CAR_METRES_TALL, CAR_SPRITE_UNITS_TALL)

    /** Base scale for the fire engine, which has its own taller body. */
    val FIRE_TRUCK_BASE_SCALE: Float
        get() = scaleForHeight(FIRE_TRUCK_METRES_TALL, FIRE_TRUCK_SPRITE_UNITS_TALL)

    /**
     * An adult pedestrian: 1.75 m across the 80 local units of content the walk sprites carry
     * inside their 84-unit canvas.
     *
     * The children are drawn shorter *within the same canvas* -- 62 units against the adults' 80
     * -- so one scale gives them their own 0.77 of adult height with no second entry here. At
     * 1.75 m that puts a child at 1.36 m; at the 1.9 m this constant used to carry, 1.47 m.
     *
     * **v4.3: the constant said `1.9f` while this comment three lines above it said 1.75 m.** The
     * comment was right and the number was not, and the 8.6% it added to every pedestrian was the
     * whole of the reported "cars look too small next to the people walking behind them". Nothing
     * else was wrong: measured on rendered frames, both draw paths reproduce this model to within
     * a pixel of antialiasing, and `CAR_METRES_TALL` over [CAR_SPRITE_UNITS_TALL] is an honest
     * roof-to-wheel-contact reading of the artwork.
     *
     * What the extra 8.6% did was invert one comparison. A car in the **far lane** stands nearer
     * the viewer than a pedestrian on the **far pavement**, so it must be drawn larger; at 1.9 m
     * the pedestrian won that comparison (62.7 reference px against the car's 61.1) and a car
     * with a person visible behind it read as a toy. At the documented 1.75 m the ordering is the
     * one the ground plane implies -- 57.7 against 61.1 -- and `VehiclePedestrianScaleTest` fails
     * if it ever inverts again.
     *
     * **v4.6: the busts behind a windscreen were not governed by this, and that was the rest of
     * the same report.** `CAR_HEAD_SCALE` sizes them against the glass, which is the right thing
     * for it to do, but nobody had checked the result against the way this artwork actually draws
     * a person. A walk sprite gives a pedestrian a head 25.00 of its 80.67 content units tall --
     * **31% of their own height**, a paper-cutout proportion -- so an adult's head is 0.547 m. The
     * driver's was 0.320 m, 59% of it, on a plane nearer the viewer than the pavement; the people
     * inside the cars read as children. See `SceneObjectRenderer`'s bust block for the fix, which
     * is a taller pane and one rule instead of three tuned scales, and which leaves
     * [CAR_METRES_TALL] alone.
     */
    const val PERSON_METRES_TALL = 1.75f
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
