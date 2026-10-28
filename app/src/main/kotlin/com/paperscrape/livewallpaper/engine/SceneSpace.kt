package com.paperscrape.livewallpaper.engine

/**
 * The single source of truth for where the scene's ground plane is, how far away a point on it
 * reads as being, and how large a real-world object standing there must be drawn.
 *
 * Everything that touches size or vertical placement resolves through this object: static scene
 * objects, the road and its lanes, the vehicles, the pedestrians and the lake's inhabitants.
 * Nothing else may define a ground line, a depth scale or a category size, so sizes stay
 * comparable across categories (a person is never drawn shorter than a car). Pure Kotlin, so
 * every relation below is unit-testable.
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
 * site: a per-asset patch fixes one object and leaves the shared fault to break the next.
 */
object SceneSpace {

    // ---- Stage 4: the viewport ----------------------------------------------------------

    /**
     * The screen height every constant in this file is expressed against: sizes scale with the
     * screen as the ground lines do, so a house takes the same share of the ground band on every
     * device. 2400 px puts a contemporary phone at about 1.0, so the numbers below read as pixels.
     */
    const val REFERENCE_SCREEN_HEIGHT_PX = 2400f

    /** How much larger or smaller this viewport is than the reference one. */
    fun sceneScale(screenHeightPx: Float): Float =
        if (screenHeightPx <= 0f) 1f else screenHeightPx / REFERENCE_SCREEN_HEIGHT_PX

    /**
     * How many pixels one scene metre occupies on this viewport, at [REFERENCE_Y_FRACTION]: the one
     * conversion from world size to screen size, for every category and the atmospheric effects.
     * The viewport always shows `2400 / 45 = 53.3` metres of world, so a fixed number of particles
     * is a fixed density on every device, and only each particle's size scales with the screen.
     */
    fun pixelsPerMetre(screenHeightPx: Float): Float =
        PIXELS_PER_METRE_AT_REFERENCE * sceneScale(screenHeightPx)

    // ---- The hill layer the ground belongs to -------------------------------------------

    /** Top of the single hill layer, as a fraction of screen height. */
    const val HILL_LAYER_TOP_FRACTION = 0.60f

    /** Height of that layer, as a fraction of screen height. */
    const val HILL_LAYER_HEIGHT_FRACTION = 0.40f

    /**
     * How far down its own band the hill is solid paper at every x. The crest runs over
     * `0.13 +/- 0.09` of the layer ([hillCrestFraction]), so 0.22 is the lowest it reaches and 0.26
     * keeps a margin: anything placed above this line can stand in open sky. Mountains, the lake's lower edge
     * and the rainbow's base anchor here too, so they meet the hills at every x. Re-derive it if the
     * crest's amplitude changes.
     */
    const val HILL_SOLID_TOP_DEPTH_FRACTION = 0.26f

    /** The hill's guaranteed-solid line, as a fraction of screen height. */
    const val GROUND_SOLID_TOP_Y_FRACTION =
        HILL_LAYER_TOP_FRACTION + HILL_LAYER_HEIGHT_FRACTION * HILL_SOLID_TOP_DEPTH_FRACTION

    // ---- The hill's crest ----------------------------------------------------------------

    /** Where the crest's wave is centred, as a fraction of the hill layer's height. */
    const val HILL_CREST_CENTRE_FRACTION = 0.13f

    /** How far the wave swings either side of its centre at *Hills variation* 100 %. */
    const val HILL_CREST_MAX_AMPLITUDE_FRACTION = 0.09f

    /**
     * How far down the hill layer its crest stands at [f] across the hill's tile (0..1, two screen
     * widths), at [hillsVariation] and the theme's [phase]: a sine, two whole waves per tile,
     * `0.13 +/- 0.09 x variation`. **The one statement of the crest**: `PaperRenderer.buildBaseHillPath`
     * draws the hills with it and [towerDrawnDepth] plants the towers by it, so the two cannot drift.
     */
    fun hillCrestFraction(f: Float, hillsVariation: Float, phase: Float): Float {
        val amp = HILL_CREST_MAX_AMPLITUDE_FRACTION * hillsVariation.coerceIn(0f, 1f)
        return HILL_CREST_CENTRE_FRACTION + amp * kotlin.math.sin(f * 4f * kotlin.math.PI.toFloat() + phase)
    }

    /**
     * A theme's crest phase: the hills' own per-theme shift, from the theme's seed -- for hill layer
     * [layer], of which the scene draws one. Integer arithmetic on the id's hash, which the Java
     * language specifies exactly, so every device draws the same crest.
     */
    fun hillCrestPhase(themeId: String, layer: Int = 0): Float = ((themeId.hashCode().toLong() * 31 + layer) % 628L) / 100f

    /**
     * The crest's height on screen above a ground object at [tileFractionX], as a fraction of screen
     * height. An object at `tileFractionX` stands a quarter of the hill's tile from the hill path's
     * left edge (the path is built from half a screen left of the tile's origin, and the two scroll on
     * one shift), hence the `+ 0.25`.
     */
    fun hillCrestYFraction(tileFractionX: Float, hillsVariation: Float, phase: Float): Float =
        HILL_LAYER_TOP_FRACTION + HILL_LAYER_HEIGHT_FRACTION * hillCrestFraction(tileFractionX + 0.25f, hillsVariation, phase)

    /**
     * How far below the crest a tower's foot has to stand not to look set on top of it: 0.045 of
     * the screen, 65 px on the BV6600 (`DESIGN_NOTES.md` §12, decision 49).
     */
    const val TOWER_CREST_CLEARANCE_FRACTION = 0.045f

    /**
     * The deepest a tower is drawn when it is brought forward: in front of every tower, behind every
     * shop -- the shop band starts at [BUILDING_TOWER_MAX_DEPTH], and the shops' candidates at 0.356.
     */
    const val TOWER_PLANTED_MAX_DEPTH = 0.29f

    /**
     * The depth a tower at [depthFraction] and [tileFractionX] is *drawn* at. A tower whose foot
     * stands within [TOWER_CREST_CLEARANCE_FRACTION] of the crest above it reads as set on the
     * crest, so it is drawn just far enough forward to clear it, never past
     * [TOWER_PLANTED_MAX_DEPTH]; every other tower is drawn where it stands.
     *
     * Only the foot and, through the projection, the size move. The density threshold, the colour
     * coin, the deal, the height coin and the shop plan read the stored depth, so no tower appears,
     * goes or changes, and no shop moves. *Hills variation* moves the crest and these towers with it.
     */
    fun towerDrawnDepth(depthFraction: Float, tileFractionX: Float, hillsVariation: Float, phase: Float): Float {
        if (depthFraction >= BUILDING_TOWER_MAX_DEPTH) return depthFraction
        val crest = hillCrestYFraction(tileFractionX, hillsVariation, phase)
        val wanted = (crest + TOWER_CREST_CLEARANCE_FRACTION - OBJECT_BAND_TOP_Y_FRACTION) /
            (OBJECT_BAND_BOTTOM_Y_FRACTION - OBJECT_BAND_TOP_Y_FRACTION)
        return maxOf(depthFraction, minOf(wanted, TOWER_PLANTED_MAX_DEPTH))
    }

    // ---- Stage 3: the ground plane and its perspective ----------------------------------

    /**
     * The vanishing line of the ground plane, as a fraction of screen height. It sits above the
     * farthest ground used, because a point on it has zero size. Its gap to
     * [OBJECT_BAND_TOP_Y_FRACTION] sets how strongly the scene recedes: up flattens it, down
     * shrinks the farthest objects to nothing.
     */
    const val HORIZON_Y_FRACTION = 0.655f

    /** Where a static object of `depthFraction = 0` stands. The hill's guaranteed-solid line. */
    const val OBJECT_BAND_TOP_Y_FRACTION = GROUND_SOLID_TOP_Y_FRACTION

    /**
     * Where a static object of `depthFraction = 1` stands. Bounded by the road: static objects are
     * drawn before the road strip, so one standing below [roadTopYFraction] would be painted over.
     * `SceneSpaceTest` asserts the margin.
     */
    const val OBJECT_BAND_BOTTOM_Y_FRACTION = 0.790f

    /**
     * The wheel line of the far traffic lane, as a fraction of screen height. The road sits low
     * enough to leave [PAVEMENT_FAR_Y_FRACTION]..[PAVEMENT_NEAR_Y_FRACTION] as ground between the
     * buildings and the road, where people walk.
     */
    const val ROAD_LANE_FAR_Y_FRACTION = 0.834f

    /**
     * The wheel line of the near traffic lane. The lanes are 0.028 apart, about a vehicle: wider
     * and the carriageway reads as a dark band dominating the scene. The road's edges derive from
     * the lanes, so moving the pair moves the road without resizing it.
     */
    const val ROAD_LANE_NEAR_Y_FRACTION = 0.862f

    /**
     * How far past the outer lanes the painted surface reaches, as a fraction of one lane's
     * half-spacing: each lane owns half of the strip plus this shoulder, so the strip stays
     * symmetric and in proportion for any lane pair.
     */
    const val ROAD_SHOULDER_LANE_FRACTION = 0.16f

    /**
     * Where the farther row of pedestrians walks: between the buildings and the road, below
     * [OBJECT_BAND_BOTTOM_Y_FRACTION] and above [roadTopYFraction], so a person reads as on the
     * pavement in front of the village, not on the tarmac. That far back, the projection draws
     * people small; this is distance, not a size error.
     */
    const val PAVEMENT_FAR_Y_FRACTION = 0.795f

    /** Where the nearer row of pedestrians walks. */
    const val PAVEMENT_NEAR_Y_FRACTION = 0.807f

    /**
     * The ground line at which [perspectiveScaleAt] is exactly 1, so the size table below reads
     * directly as "pixels at this line, on a reference-height screen". Its own constant, not a lane:
     * tied to a lane, moving the road would rescale every object in the scene.
     */
    const val REFERENCE_Y_FRACTION = 0.846f

    /** Where an object of [depthFraction] stands, as a fraction of screen height. */
    fun groundYFraction(depthFraction: Float): Float {
        val d = depthFraction.coerceIn(0f, 1f)
        return OBJECT_BAND_TOP_Y_FRACTION + d * (OBJECT_BAND_BOTTOM_Y_FRACTION - OBJECT_BAND_TOP_Y_FRACTION)
    }

    /**
     * How large something standing at [yFraction] reads, relative to the same thing standing at
     * [REFERENCE_Y_FRACTION]. The whole perspective model: on a flat ground plane, apparent size is
     * proportional to the distance below the horizon, so the road, the pavement and the object band
     * share one projection. Clamped at zero: a y above the horizon is not on this plane, and a
     * negative scale would mirror the sprite.
     */
    fun perspectiveScaleAt(yFraction: Float): Float =
        ((yFraction - HORIZON_Y_FRACTION) / (REFERENCE_Y_FRACTION - HORIZON_Y_FRACTION)).coerceAtLeast(0f)

    /** [perspectiveScaleAt] evaluated at the ground line of [depthFraction]. */
    fun depthScale(depthFraction: Float): Float = perspectiveScaleAt(groundYFraction(depthFraction))

    // ---- The distant houses ---------------------------------------------------------------

    /**
     * Where the distant houses stop shrinking: as small as a house standing 0.6722 down the screen,
     * 25 px under the horizon, where a small house is 14.0 px tall on the BV6600 (the maintainer's
     * 14 px minimum; 0.672 gives 13.84). The projection is zero at and above the horizon, where the
     * mountains are, so a house sized by it alone would vanish, and its details (a window, the
     * chalet's attic, the snow's lip) would fall under the 2 px every drawn detail is held to.
     */
    const val DISTANT_HOUSE_FLOOR_Y_FRACTION = 0.6722f

    /**
     * How many pixels tall a distant house stands with its foot at [footYFraction]: a small house
     * ([SceneVariant.HOUSE_SMALL]'s metres) at the projection of that point, never smaller than at
     * [DISTANT_HOUSE_FLOOR_Y_FRACTION], so on the mountains every house is the floor's size. A
     * village house is about 40 px on the BV6600, so a distant house is smaller than anything in town.
     */
    fun distantHousePixelsTall(footYFraction: Float, screenHeightPx: Float): Float =
        SceneVariant.HOUSE_SMALL.metresTall * pixelsPerMetre(screenHeightPx) *
            maxOf(perspectiveScaleAt(footYFraction), perspectiveScaleAt(DISTANT_HOUSE_FLOOR_Y_FRACTION))

    // ---- Road geometry ------------------------------------------------------------------

    /** The distance between the two canonical lanes, as a fraction of screen height. */
    const val CANONICAL_LANE_SPACING_FRACTION = ROAD_LANE_NEAR_Y_FRACTION - ROAD_LANE_FAR_Y_FRACTION

    /**
     * Below this spacing a lane pair is degenerate and the canonical spacing is used instead: a
     * saved theme can put every car on one lane, and a margin derived from a zero spacing paints
     * the road as a hairline. Half the canonical spacing is well below any real pair and well
     * above the float noise in a saved value.
     */
    const val MIN_ROAD_LANE_SPACING_FRACTION = CANONICAL_LANE_SPACING_FRACTION / 2f

    /**
     * How far the painted road reaches beyond its outermost lane, given the lane pair in use, as
     * a fraction of screen height. Derived from the lanes, so a custom theme with its own lane pair
     * gets a strip centred on its traffic; for the canonical lanes it yields [roadTopYFraction] and
     * [roadBottomYFraction].
     *
     * **The lane pair comes from the theme's whole car list, never from the cars a density setting
     * kept**: otherwise thinning the traffic to one lane collapses the road. The degenerate guard
     * is a second line of defence, not the fix for that.
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
     * Road markings, in pixels at [REFERENCE_SCREEN_HEIGHT_PX]. They belong to the road surface,
     * so they scale with the viewport, not with any object's depth.
     */
    const val ROAD_EDGE_STROKE_PX = 4f
    const val ROAD_CENTRE_LINE_STROKE_PX = 8f
    const val ROAD_DASH_LENGTH_PX = 52f
    const val ROAD_DASH_GAP_PX = 40f

    // ---- Traffic and pedestrians ---------------------------------------------------------

    /**
     * The near lane's speed, in screen widths per second. One speed per lane, never per car: a
     * lane is a queue, and a queue keeps its spacing only if nothing overtakes.
     */
    const val CAR_SPEED_NEAR = 0.075f

    /**
     * The far lane's speed, derived: at the same real speed the farther car crosses the screen
     * more slowly by exactly the ratio of the two lanes' perspective scales.
     */
    val CAR_SPEED_FAR: Float
        get() = CAR_SPEED_NEAR * perspectiveScaleAt(ROAD_LANE_FAR_Y_FRACTION) /
            perspectiveScaleAt(ROAD_LANE_NEAR_Y_FRACTION)

    /**
     * Walking speed at [PAVEMENT_NEAR_Y_FRACTION], in ground tiles per second (a tile is two
     * screen widths).
     */
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
     * Local units per metre at [REFERENCE_Y_FRACTION], on a reference-height screen: the one number
     * that states the world's size. Every category's base scale is `metres * this / spriteUnits`, so a
     * sprite drawn at any internal scale lands at its declared size, and changing this enlarges every
     * standing object alike without changing a ratio between them. The road, laid out in screen
     * fractions, does not grow with it: a larger metre crowds the two lanes' traffic into each other.
     * The lake keeps its own metric ([LAKE_PIXELS_PER_METRE]), so its boats do not grow with the
     * foreground.
     */
    const val PIXELS_PER_METRE_AT_REFERENCE = 45f

    /*
     * The heights below are what each object reads as, anchored to real sizes and moved off them only
     * for legibility: a person 1.75 m and a car body 1.60 m, physical; a tower 15.6 m, short enough
     * not to dominate the foreground; a tree 9.479 m, a gift 0.905 m and a parasol 2.9 m, nudged up
     * for presence; a rabbit 0.89 m and an Easter egg 1.0 m, well past life size, or the Easter
     * theme's two subjects could not be made out. What must hold is the hierarchy, person < car <
     * house < tree < larger building; the numbers serve it.
     */

    /** [metresTall] of real object drawn across [spriteUnitsTall] of local units. */
    fun scaleForHeight(metresTall: Float, spriteUnitsTall: Float): Float =
        metresTall * PIXELS_PER_METRE_AT_REFERENCE / spriteUnitsTall

    /**
     * Every distinct thing the scene draws standing on the ground, with the real height it is
     * meant to read as and the local-unit height its own drawing occupies.
     *
     * **Height is governed, width follows the artwork**: the sprites are stylised (a cottage
     * narrower than a real one), governing width would make a person shorter than a car, and
     * heights are what the eye compares when every object meets the same ground line.
     * [spriteUnitsTall] is the extent that carries the object's identity, not its whole box: a
     * shop's wall, not its hanging sign; a tree's trunk plus canopy. `SceneSpaceTest` pins the
     * relations between the entries, `BuildingHeightDeclarationTest` the buildings' units against
     * their blits.
     */
    enum class SceneVariant(val metresTall: Float, val spriteUnitsTall: Float) {
        /**
         * A one-bay cottage; its door is 38 units, 2 m. Drawn at the large house's metres-per-unit,
         * so the two houses' windows, doors and sills are one family at one scale.
         */
        HOUSE_SMALL(5.76f, 110f),

        /**
         * Two or three storeys plus a roof (the storey slot repeats once or twice); its door is 45
         * units on the same reading.
         */
        HOUSE_LARGE(7.6f, 145f),

        /**
         * Trunk to crown; the trunk is 53 % of it. 118 units: `tree_canopy` stands at
         * `TreeSpriteLayout.CANOPY_Y` (-80) inside the canopy's `translate(0,-38)`, so the crown
         * tops out at -118. 9.479 m is those 118 units at the metre the tree is drawn at, a size
         * chosen by eye for presence beside the houses.
         */
        TREE(9.479f, 118f),

        /**
         * The Christmas fir that replaces one tree in three while *Christmas firs* is on. Never
         * returned by `variantFor`: a fir is a state of a TREE candidate
         * (`SceneObjectRenderer.standsAsFir`) drawn at TREE's scale, so this `baseScale` is not
         * applied. The entry follows the drawing (`FirTable`, 72 units wide and 126 tall;
         * `DESIGN_NOTES.md` §12, decision 66) at TREE's metres-per-unit, 10.12 m: one metre governs both, so a fir stays
         * in scale with the wood.
         */
        FIR(10.12f, 126f),

        /** Trunk plus frond fan, ground to the top of the blades. */
        PALM_TREE(8f, 90.33f),

        /**
         * The office block, behind the village and the tallest thing in the scene. Declared as the
         * middle of its three bodies, which stand 0.93 to 1.37 of it; every piece is drawn at 15.6 m
         * over 182 units, 0.0857 m per unit (`BuildingHeightDeclarationTest`).
         */
        TOWER(15.6f, 182f),

        /**
         * Shop front, measured to the top of the wall and not to its hanging sign:
         * `RESTAURANT_PAVILION` is a single-storey pavilion that tops out at 56 units, 4.78 m at the
         * common 0.085417 m per piece unit. These two numbers and `NeighbourhoodTable`'s `unitsTall`
         * describe one facade and move together, so the renderer's `metresTall * pixelsPerMetre /
         * unitsTall` stays the common metre (`BuildingHeightDeclarationTest`). The separation pass
         * places a shop by this height and the shop-front ceiling divides by it
         * (`SceneObject.frontRect`), so it is the drawn height, not a taller one. A shop stands
         * lower than a large house, as drawn, and stays so.
         */
        RESTAURANT(4.7833333f, 56f),

        /**
         * The same family, the chamfered corner unit: 53 units on one deal and 73 on the other,
         * declared at the taller, so a crown over the tall deal is never measured against a front
         * shorter than the building it covers (`ShopFrontVisibilityTest` measures each instance on
         * its own ink). 74.50136 is `73 * 92 / 90.146`, the family's own ratio, so
         * `spriteUnitsTall / unitsTall` is exact.
         */
        BAR(6.23544f, 74.50136f),

        /**
         * The school: a turret with a clock, four bay windows and a pencil on a plaque. 72 units =
         * 6.15 m at the common 0.085417 m per piece unit, so a child at one of its windows is a
         * child's size. It draws 74: the turret cornice tops out at -73.5, rounded to the sprite
         * grid; `BuildingHeightDeclarationTest` measures it off the blits. Between the restaurant
         * and the bar in height, as its depth band puts it ([RESTAURANT_MAX_DEPTH]).
         */
        SCHOOL(6.15f, 72f),

        /** Pole to canopy rim, past life size: smaller, it is lost in the composition. */
        PARASOL(2.9f, 84f),

        /** Three spheres and a hat: `snowman_body` spans -74..0, the nose and scarf inside it. */
        SNOWMAN(1.6773f, 74f),

        /**
         * A large wrapped present, measured over the bow, past life size so it is not a speck.
         * 40 units: `gift_box` and `gift_ribbon` together span -40..0.
         */
        GIFT(0.9048f, 40f),

        /**
         * An emperor penguin, 49 units of artwork. The only static whose art crosses the ground
         * line: `penguin_feet` is blitted at y 0, so the feet occupy 0..+4 while every other static
         * ends at 0. Moving the feet would move the bird, so they stay.
         */
        PENGUIN(1.1717f, 49f),

        /**
         * A rabbit sitting up, ears included, past life size: at a real rabbit's height it is a
         * few dozen pixels of the ground's colour, and it is one of the Easter theme's two subjects.
         */
        BUNNY(0.8855f, 61f),

        /** An oversized decorative garden egg, not a hen's egg, past life size as [BUNNY] is. */
        EASTER_EGG(1f, 40f),

        /**
         * A prize pumpkin, measured over the stem, level with the egg: smaller it reads as a bead
         * beside the gifts, and at 1.10 m it hides a penguin standing behind it. `SceneSpaceTest`
         * holds it no smaller than a gift or a bunny and shorter than a penguin.
         */
        PUMPKIN(1.0f, 42f),

        /**
         * A scarecrow on its pole, the pole's foot to the tip of its hat: a person's 1.75 m on a pole
         * and a hat, beside the snowman whose places it shares (`DESIGN_NOTES.md` §12, decision 80).
         * The units are the drawing's, written by `tools/assets/scarecrow`.
         */
        SCARECROW(ScarecrowTable.METRES_TALL, ScarecrowTable.UNITS_TALL),
        ;

        /** The scale this variant is drawn at when it stands on [REFERENCE_Y_FRACTION]. */
        val baseScale: Float = scaleForHeight(metresTall, spriteUnitsTall)
    }

    /**
     * The depth below which a building candidate is drawn as a [SceneVariant.TOWER] rather than as
     * a shop. The two share a colour, a density slider and a candidate pool; deciding by depth puts
     * the towers behind the village and the shops among the houses, as the perspective implies.
     */
    const val BUILDING_TOWER_MAX_DEPTH = 0.30f

    /**
     * The far edge of the shop band: past this, no building candidate is generated. Stated here,
     * not only as `SceneObjectCatalog`'s `0.0f..0.80f`, because the thresholds below derive from it.
     */
    const val SHOP_BAND_MAX_DEPTH = 0.80f

    /**
     * **The shop band is divided in three equal parts, one per row of the size table: restaurant,
     * school, bar, from far to near.** Depth decides a place's row because it never changes after
     * generation, whatever the visibility pass does to x; which building a place draws is the
     * street's deal ([StorefrontStreet]).
     *
     * Equal thirds because a building candidate's depth has no jitter: `SceneObjectCatalog` lays
     * the ten on `i / 9 * 0.80`, so the six in the shop band stand at 0.356, 0.444, 0.533, 0.622,
     * 0.711 and 0.800 on every theme, and equal thirds put exactly two in each, with both splits
     * in a gap between candidates. A band with one candidate would lose its shop to the density
     * slider. Far to near also reads small to large: 4.78 m, 6.15 m, 6.24 m.
     */
    private const val SHOP_BAND_THIRD = (SHOP_BAND_MAX_DEPTH - BUILDING_TOWER_MAX_DEPTH) / 3f

    /**
     * Below this a place among the houses is in the restaurant's row of the size table: the first
     * third of the band.
     */
    const val RESTAURANT_MAX_DEPTH = BUILDING_TOWER_MAX_DEPTH + SHOP_BAND_THIRD

    /**
     * Below this, and above [RESTAURANT_MAX_DEPTH], a place is in the school's row of the size
     * table; above it, in the bar's.
     */
    const val SCHOOL_MAX_DEPTH = BUILDING_TOWER_MAX_DEPTH + 2f * SHOP_BAND_THIRD

    /*
     * The vehicles, kept out of [SceneVariant] because they are placed by lane rather than by
     * depth and never appear in the static object list. A car's governed height is roof to wheel
     * contact, 53 local units on all three bodies ([CarShell.unitsTall]); the fire engine's body
     * reaches 68 over the same wheel line.
     */
    /**
     * **What one local unit of car artwork is worth in metres**: the constant the vehicle family is
     * built on, rather than a height per body, so a unit is the same pixel on every body and every
     * body, wheel radius, corner radius and seat position is comparable across the family, with
     * one [CAR_BASE_SCALE]. 1.51 m over 50 units.
     */
    const val CAR_UNIT_METRES = 1.51f / 50f

    /**
     * The saloon ([CarShell.SALOON], 53 units, 1.60 m), the family's reference pair for the height
     * table and for every test that compares a car with a pedestrian. [CAR_BASE_SCALE] does not
     * depend on it (the height cancels); the bodies derive their metres through
     * [CAR_UNIT_METRES] ([CarShell.metresTall]).
     */
    const val CAR_SPRITE_UNITS_TALL = 53f
    const val CAR_METRES_TALL = CAR_UNIT_METRES * CAR_SPRITE_UNITS_TALL

    /**
     * The fire engine keeps a metre-per-unit of its own (2.9 m over 68 units = 0.0426 m/u against
     * the cars' 0.0302): on the cars' unit it would need a 96-unit-tall canvas, 0.29 MiB more of
     * decoded sprite for no visible gain. Its radii are divided by the ratio of the two units so it
     * renders like the cars (`firetruck_body.svg`).
     */
    const val FIRE_TRUCK_METRES_TALL = 2.9f
    const val FIRE_TRUCK_SPRITE_UNITS_TALL = 68f

    /**
     * The one scale every car body is drawn at: metres being [CAR_UNIT_METRES] times units on all
     * three bodies, the body's height cancels, and a local unit is the same pixel on each.
     */
    val CAR_BASE_SCALE: Float get() = scaleForHeight(CAR_METRES_TALL, CAR_SPRITE_UNITS_TALL)

    /** Base scale for the fire engine, which has its own taller body. */
    val FIRE_TRUCK_BASE_SCALE: Float
        get() = scaleForHeight(FIRE_TRUCK_METRES_TALL, FIRE_TRUCK_SPRITE_UNITS_TALL)

    /**
     * An adult pedestrian: 1.75 m across the 80 local units of content the walk sprites carry
     * inside their 84-unit canvas. The children are drawn 52 units tall in the same canvas, 0.65 of
     * an adult (1.14 m) with a child's share of leg, so one scale serves both; 0.65 is the
     * maintainer's choice from photographs on the phone (0.70 read as a short adult, 0.60 as a dot
     * at night).
     *
     * **The children's height is written in five places**, moved together: the drawing,
     * `VehiclePedestrianScaleTest.CHILD_SPRITE_UNITS_TALL`, `PrecipitationScaleTest.childMetres`
     * and `build_people_concepts.CHILD_OF_ADULT`, on this 80-unit metre, and
     * `VehicleScalePixelTest.CHILD_UNITS`, the child's ink in the sprite's own units with its ground
     * shadow, 54 rather than 52, which a search for 52 does not find.
     *
     * A car in the far lane stands nearer than a pedestrian on the far pavement, so it is drawn
     * larger: 67.5 reference px against 57.7. `VehiclePedestrianScaleTest` fails if that inverts.
     */
    const val PERSON_METRES_TALL = 1.75f
    const val PERSON_SPRITE_UNITS_TALL = 80f

    /** Base scale for a walking person. */
    val PERSON_BASE_SCALE: Float get() = scaleForHeight(PERSON_METRES_TALL, PERSON_SPRITE_UNITS_TALL)

    // ---- The lake -----------------------------------------------------------------------

    /**
     * The lake's own metric, separate from the ground plane's: the water sits at and just above
     * the horizon, where [perspectiveScaleAt] is near zero, so its inhabitants are sized against
     * each other by this one number (a dolphin shorter than the sailboat), which also sets how
     * large the pair reads on screen.
     */
    const val LAKE_PIXELS_PER_METRE = 21f

    /**
     * Bow to stern of the hull, over the 81.3 local units its content spans (of the 82-unit
     * `sailboat_hull` canvas), so the dolphin-to-sailboat ratio is the declared one.
     */
    const val SAILBOAT_METRES_LONG = 6.5f
    const val SAILBOAT_SPRITE_UNITS_LONG = 81.3f

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
     * side of 1. `StaticSceneObject.scale` holds only this variation, so 1 always means the
     * category's own size.
     */
    const val SIZE_VARIATION_SPREAD = 0.16f

    /** The smallest a per-candidate variation may shrink an object to. */
    const val MIN_SIZE_VARIATION = 0.5f

    /**
     * Converts an absolute `scale`, as older saved custom themes hold it, into the relative
     * variation: these are the base scales those values were written against. Kept beside the
     * size table so both halves of the conversion are in one place.
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
        // Never in a payload with absolute scales: the snowman's, whose places it shares.
        SceneObjectType.SCARECROW -> 1.3f
        SceneObjectType.CAR -> 1f
    }
}
