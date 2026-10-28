package com.paperscrape.livewallpaper.engine

import android.content.Context
import android.graphics.Paint
import androidx.core.graphics.ColorUtils
import com.paperscrape.livewallpaper.R
import kotlin.math.sin

/**
 * Horizontal ground snapshot, computed by [PaperRenderer] each frame and shared with this renderer
 * so static objects (house, tree, building) scroll in perfect sync with the ground they're
 * anchored to. Only the horizontal half: the vertical half of the ground plane is [SceneSpace]'s
 * alone, so no second copy of it can disagree.
 */
data class GroundGeometry(
    // `var`: the renderer keeps one and rewrites it each frame (PaperRenderer.drawHillLayers).
    var shiftXWrapped: Float, // parallax shift, shared verbatim with the hills (see PaperRenderer.drawHillLayers)
    var tileWidth: Float,     // the objects' tiling period: twice the screen width, same as the hills'
    /**
     * Whole tiles the wrap has removed from the raw parallax shift, `round((shiftUnwrapped -
     * shiftXWrapped) / tileWidth)`. A copy's loop index steps by one each time the wrap crosses a
     * period; [SceneObjectRenderer.copyOf] takes these tiles out, and the tile the object's own
     * representative wraps by, so the number it gives stays with a physical copy for as long as it exists.
     */
    var scrollTileBias: Int = 0,
)

private class StaticRuntime(val spec: StaticSceneObject) {
    val idleSeed = SceneObjectRenderer.idleSeedOf(spec)

    /**
     * The depth this object is drawn at: its own, except a tower brought forward off the hill's crest
     * ([SceneSpace.towerDrawnDepth]). Where it stands and how large it is follow it; everything
     * that identifies the object -- its colour, its deal, its height, whether it stands at all -- reads
     * [spec]'s own depth. Set when the list is built, and again when *Hills variation* moves the crest.
     */
    var drawDepth = spec.depthFraction

    /**
     * A building's roster: who stands at its windows and, in a house, which are lit at night,
     * at each moment -- worked out once when the list is built, read every frame ([WindowRoster]).
     * Null for everything that is not a building with windows; a tower's people do not move from window
     * to window, so its roster is its first state for ever.
     */
    var roster: WindowRoster.Plan? = null

    /** The building's windows, measured once with its roster: where its people walk ([WindowWalk]). */
    var windowLayout: WindowWalk.Layout? = null

    /**
     * Who of a building among the houses or of a tower is in at its hours, who is walking out or in, and
     * how lit its glass is ([WindowWalk.Doorway], [WindowWalk.Doorway.glassAt]); null for a house, which keeps no hours.
     */
    var doorway: WindowWalk.Doorway? = null

    /**
     * For a place among the houses, its rank across the tile ([StorefrontStreet.places]): which
     * building stands in it, copy by copy, is the street's ([StorefrontStreet]), and the place draws it.
     * -1 for everything else.
     */
    var place: Int = -1

    /**
     * For the runtime of one of the six buildings among the houses, which one: its [spec] is the
     * building's identity ([StorefrontStreet.identities]) and its roster, windows and doorway are the
     * building's, whichever place it stands in. Null for every object of the layout.
     */
    var storefront: Storefront? = null

    /**
     * For a scarecrow, whether the snowman of its place stands too: then it is drawn
     * [SceneObjectRenderer.SCARECROW_ASIDE_METRES] to the side, so the two stand side by side. Set when the list is built.
     */
    var scarecrowAside = false
}

private class CarRuntime(val spec: CarObject, val layoutIndex: Int, val selectionRank: Int) {
    var progress = -spec.startDelaySeconds // negative = still waiting to start

    /**
     * Whether this slot's car is on the road. Every inventory slot keeps a ticking runtime whatever
     * the count, so the loop phases stay evenly spaced; the count only decides which of them draw.
     */
    var active = true

    /**
     * Where [active] is headed: the two differ only while a membership change waits for its car to
     * leave the screen ([CarSelection.offScreen]), so nothing pops in or out mid-road.
     */
    var targetActive = true

    /**
     * Which of the three bodies this car wears, resolved **once** from the immutable spec, so it can
     * depend on nothing per frame and a rebuilt list gives each car the same body.
     */
    val shell: CarShell = CarShell.forCar(spec)
}

class SceneObjectRenderer(
    private val layout: SceneObjectLayout,
    customization: SceneCustomization = SceneCustomization.DEFAULT,
    private val context: Context,
    /**
     * The theme this scene belongs to, seeding the people system and the car selection through
     * `theme.id.hashCode()`, whose value the Java language specifies, so a theme produces the same
     * street on every device and every run. The default is for tests; [PaperRenderer] passes the id.
     */
    private val themeId: String = "",
) {

    /** The per-theme seed [CarSelection] reads -- see [themeId] for why it is the id's hash. */
    private val themeSeed = themeId.hashCode()

    /**
     * The places among the houses in their order across the tile ([StorefrontStreet.places]), each
     * place's rank; declared above [staticRuntimes], which is built at construction.
     */
    private val places: List<StaticSceneObject> = StorefrontStreet.places(layout)

    /**
     * The six buildings among the houses, one runtime each in [Storefront.ALL]'s order, its spec the
     * building's identity ([StorefrontStreet.identities]); kept for the life of this renderer, list
     * rebuilds included, so whoever walks out at the hours goes on walking. A place draws the one the
     * street deals it ([drawStaticObject]). Null on a layout with no place among the houses.
     */
    // The id is passed for a built-in street's own layout only: a saved theme keeps the identities it stores,
    // unless its places are the built-in street's ([StorefrontStreet.builtInStreetOf]).
    private val storefronts: Array<StaticRuntime>? = StorefrontStreet.identities(
        layout, themeId.takeIf { CustomThemeRegistry.overrideLayoutFor(it) == null },
    )?.let { identities ->
        val deal = NeighbourhoodComposer.Deal()
        Array(Storefront.ALL.size) { k ->
            val storefront = Storefront.ALL[k]
            StaticRuntime(identities[k]).also {
                it.storefront = storefront
                giveRoster(it, storefront.family, storefront, deal)
            }
        }
    }

    /** Scratch for [StorefrontStreet.storefrontAt] on the draw path: two decks, reused every call. */
    private val streetDeckA = IntArray(StorefrontStreet.DECK)
    private val streetDeckB = IntArray(StorefrontStreet.DECK)

    /**
     * The active per-category configuration, applied **in place**, rebuilding only the runtime list
     * whose membership changed: the static list for a category's visibility or density or the palms
     * switch, the car list (restarting the cars) for the Cars visibility. *Hills variation* re-sorts
     * ([plantedAndSorted]); car density is applied per car by [update]; anything else rebuilds
     * nothing. A rebuilt static object resumes where it was: what it holds derives from its spec, the
     * theme's seed and the crest, but its doorway, handed on ([rebuildStaticRuntimes]). New opening
     * hours are a cut for the doorways ([advanceDoorways]).
     */
    var customization: SceneCustomization = customization
        set(value) {
            val previous = field
            field = value
            // A change of the opening hours is a cut for the people at the hours (see [advanceDoorways]).
            if (!previous.sameOpeningHours(value)) doorwaysSettle = true
            if (!previous.staticStructurallyEquals(value)) {
                rebuildStaticRuntimes()
            } else if (previous.hillsVariation != value.hillsVariation) {
                // The crest moved, and the towers planted by it move with it: the drawn depths, and
                // the draw order they decide. Nothing else of the list changes.
                staticRuntimes = plantedAndSorted(staticRuntimes)
            }
            // Cars: only visibility rebuilds. Density is [update]'s, per car and off screen: a rebuild
            // would reset every car to its start delay and empty the road for a slider drag.
            if (previous.cars.visible != value.cars.visible) rebuildCarRuntimes()
        }

    /**
     * Whether this layout plants palms of its own, which decides which half of the Palms switch the
     * scene reads ([SceneCustomization.palmSpeciesApplied]). A property of the layout, so read once;
     * declared above [staticRuntimes] because that list is built from it at construction.
     */
    private val layoutPlantsPalms: Boolean = layout.hasPalmSlots()

    /**
     * Back-to-front by drawn depth ([StaticRuntime.drawDepth]), so nearer objects overlap farther
     * ones; sorted when the list is built or the crest moves ([plantedAndSorted]), never per frame.
     */
    private var staticRuntimes: List<StaticRuntime> = buildStaticRuntimes()
    private var carRuntimes: List<CarRuntime> = buildCarRuntimes()

    private fun buildStaticRuntimes(): List<StaticRuntime> = layout.staticObjects
        .filter { spec -> customization.keepCandidate(spec, layout.densityScheme) }
        // The palms switch is resolved here and nowhere downstream (`DESIGN_NOTES.md` decisions 37 and
        // 39): from here on the slot *is* a tree or a palm, to the drawing, the size, the occlusion box
        // and the leaf recorder alike, so flipping it rebuilds this list (`staticStructurallyEquals`).
        .map { spec ->
            StaticRuntime(customization.palmSpeciesApplied(spec, layoutPlantsPalms)).also { r ->
                // A place among the houses keeps its rank across the tile: the street deals its building.
                r.place = places.indexOfFirst { it === spec }
                // A scarecrow in a place whose snowman stands too steps aside: both are seen.
                r.scarecrowAside = customization.scarecrowStandsAside(spec, layout.densityScheme)
            }
        }
        .let(::plantedAndSorted)
        .also(::giveRosters)

    /**
     * Every house and tower its roster and window layout, and every tower its doorway, once, as the
     * list is built: state, not a per-frame computation, kept until its inputs change. Roster and layout
     * are a function of the spec and the theme's seed, so a rebuilt list gives each building the same
     * ones; the doorway remembers, and [rebuildStaticRuntimes] hands it on. A place among the houses
     * has none: its buildings' are in [storefronts].
     */
    private fun giveRosters(runtimes: List<StaticRuntime>) {
        val deal = NeighbourhoodComposer.Deal()
        for (r in runtimes) {
            val spec = r.spec
            if (r.place >= 0) continue
            if (spec.type != SceneObjectType.HOUSE && spec.type != SceneObjectType.SKYSCRAPER) continue
            val family = NeighbourhoodTable.FAMILIES[variantFor(spec)] ?: continue
            giveRoster(r, family, null, deal)
        }
    }

    /**
     * [r]'s roster, windows and -- for anything but a house -- doorway, for the building drawn as
     * [family]: one of the six among the houses when [storefront] is set ([r]'s spec its identity), a
     * house or a tower otherwise. [deal] is scratch.
     */
    private fun giveRoster(r: StaticRuntime, family: BuildingFamily, storefront: Storefront?, deal: NeighbourhoodComposer.Deal) {
        val spec = r.spec
        if (storefront != null) dealStorefront(family, storefront, spec, deal) else NeighbourhoodComposer.deal(family, spec, deal)
        val buildingSeed = buildingSeedOf(spec)
        val people = WindowOccupants.occupantCount(themeSeed, buildingSeed, deal.peopleWindows, family.kind)
        val house = family.kind == WindowBuildingKind.HOUSE
        r.roster = WindowRoster.plan(
            themeSeed, buildingSeed, deal.windowCount, deal.paneCount, people,
            house = house,
            moves = family.kind != WindowBuildingKind.SKYSCRAPER,
        )
        r.windowLayout = WindowWalk.layoutOf(deal, open = !house)
        if (!house) r.doorway = WindowWalk.Doorway()
    }

    /**
     * [runtimes] with every tower's drawn depth set from the crest ([SceneSpace.towerDrawnDepth]), back
     * to front by it. `sortedBy` is stable, so objects at one depth keep the layout's order.
     */
    private fun plantedAndSorted(runtimes: List<StaticRuntime>): List<StaticRuntime> {
        val phase = SceneSpace.hillCrestPhase(themeId)
        for (r in runtimes) {
            r.drawDepth = if (r.spec.type == SceneObjectType.SKYSCRAPER) {
                SceneSpace.towerDrawnDepth(r.spec.depthFraction, r.spec.tileFractionX, customization.hillsVariation, phase)
            } else {
                r.spec.depthFraction
            }
        }
        return runtimes.sortedBy { it.drawDepth }
    }

    /**
     * Sorted by lane, far first: draw order is depth order, the lanes overlap vertically, and the
     * candidate index alternates lanes. **One runtime per inventory slot, whatever the density**:
     * membership is [CarRuntime.active], and every slot ticks along its loop even while retired, so
     * the generator's even spacing survives any number of count changes.
     */
    private fun buildCarRuntimes(): List<CarRuntime> {
        if (!customization.cars.visible) return emptyList()
        // Ranked once; per frame the count is compared with each stored rank ([update]), allocating
        // nothing. Every runtime starts off screen, so the first update() sets the hour's membership
        // before anything is drawn.
        val ranks = CarSelection.selectionRanks(layout.cars, themeSeed)
        val n = CarSelection.countFor(customization.cars.density, layout.cars.size)
        return layout.cars
            .mapIndexed { index, spec ->
                CarRuntime(spec, index, ranks[index]).apply {
                    active = CarSelection.isKept(ranks[index], n)
                    targetActive = active
                }
            }
            .sortedBy { it.spec.laneYFraction }
    }

    private fun rebuildStaticRuntimes() {
        val previous = staticRuntimes
        staticRuntimes = buildStaticRuntimes()
        // Whoever was walking out or in at the hours goes on walking: a building's doorway is handed to
        // the runtime of the same building in the new list (the specs are the layout's own objects).
        for (r in staticRuntimes) {
            val doorway = r.doorway ?: continue
            val before = previous.firstOrNull { it.spec === r.spec }?.doorway ?: continue
            doorway.copyFrom(before)
        }
    }

    /** Resets every car's `progress`, so it runs only when the Cars category's visibility changes. */
    private fun rebuildCarRuntimes() {
        carRuntimes = buildCarRuntimes()
    }


    /**
     * The lane pair the road is painted around, from the theme's **whole** car list, never from the
     * density-thinned [carRuntimes]: density never touches geometry, so the road does not move with the amount.
     */
    private val roadLaneMinFraction: Float = layout.cars.minOfOrNull { it.laneYFraction } ?: 0f
    private val roadLaneMaxFraction: Float = layout.cars.maxOfOrNull { it.laneYFraction } ?: 0f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = 0x33000000
    }
    private val sprites = SpriteBlitter(context)

    /**
     * This frame's scene hour, for the opening hours ([BusinessHours], [draw]'s `hour24`): a building
     * asks [SceneCustomization.opennessFor] at it, a tower by what it is drawn as, one of the six among
     * the houses by what it is ([Storefront]). A field set once a frame, like the paints, rather than a
     * parameter threaded several calls deep. With a group's switch off the openness is 1.
     */
    private var frameHour = 12f

    /** This frame's scene clock, in seconds of the day. See [PeopleColours.clockSeconds]. */
    private var clockSeconds = PeopleColours.clockSeconds(12f)

    companion object {
        /**
         * How lit a vehicle's lamps are: 80% of the [vehicleLampRamp], so a lamp reads as a lamp, not a
         * neon toy. Zero by day, and every call site is behind `if (alpha > 0)`.
         */
        internal fun litVehicleAlpha(nightGlow: Float): Int = (vehicleLampRamp(nightGlow) * 0.8f).toInt()

        /**
         * The curve the vehicles' lamps come up on ([litVehicleAlpha]): dark until nightGlow 0.35, full
         * (255) at 0.80.
         */
        internal fun vehicleLampRamp(nightGlow: Float): Int =
            (255f * ((nightGlow - 0.35f) / 0.45f).coerceIn(0f, 1f)).toInt()

        /** [fallLeafColorFor]'s four colours, built once rather than per tree per frame. */
        private val FALL_LEAF_TREE_PALETTE = intArrayOf(
            0xFFD2691E.toInt(), 0xFFB5451B.toInt(), 0xFFE0A93A.toInt(), 0xFF8F3B1B.toInt(),
        )
        /** How big a fir's three presents are (where they stand is [FirTable.GIFT_X]): built once, not per fir per frame. */
        internal val FIR_GIFT_SCALES = floatArrayOf(0.36f, 0.30f, 0.26f)

        /** The fir's ground shadow, as a share of its width: 26 units for every 80. */
        private const val FIR_SHADOW_SHARE = 26f / 80f

        /** The radius of a bulb on a string -- the sills' and the roofs' -- in units: [drawBulb]. */
        const val CHRISTMAS_LIGHT_BULB_RADIUS_UNITS = 1.5f

        /**
         * How far to the right of its snowman a scarecrow stands where both are kept, so both are seen
         * (`DESIGN_NOTES.md` decision 80): 1.27 m, the snowman's arm and the scarecrow's half way apart. In the
         * scarecrow's own units, [SCARECROW_ASIDE_UNITS].
         */
        const val SCARECROW_ASIDE_METRES = 1.27f
        val SCARECROW_ASIDE_UNITS: Float = SCARECROW_ASIDE_METRES / (ScarecrowTable.METRES_TALL / ScarecrowTable.UNITS_TALL)

        /** Half the width of a scarecrow's ground shadow, in its units: about its pole and its boots. */
        const val SCARECROW_SHADOW_HALF_UNITS = 14f

        /** The Christmas lights' four colours, the trees', the strings' and the distant houses' (`PaperRenderer`). */
        internal val CHRISTMAS_LIGHT_COLOURS = intArrayOf(
            0xFFE8564F.toInt(), 0xFFFFD54F.toInt(), 0xFF4F8FBF.toInt(), 0xFF6FCF6F.toInt(),
        )

        /** How much larger a tower's decorations, blinds' rails and shutter's slats are: see `drawDecorations`. */
        const val TOWER_DECORATION_SCALE = 1.5f

        /** The wreath on a front door, in piece units: how far below the door's top its middle hangs; and the drawing's
         *  own geometry (`deco_wreath`, whose SVG `ChristmasDecorationsTest` reads against these) -- its ring's radius
         *  and width, its bow's radius and how far below the middle, and the half of its canvas it is blitted back by. */
        const val WREATH_DROP = 6.5f
        const val WREATH_RADIUS = 3f
        const val WREATH_BAND = 3f
        const val WREATH_BOW_RADIUS = 1.4f
        const val WREATH_BOW_DROP = 3.4f
        const val WREATH_HALF = 5f

        /** A tower's star, in piece units before [TOWER_DECORATION_SCALE]: how far above the top its middle is, its radii. */
        const val TOWER_STAR_RISE = 6.5f
        const val TOWER_STAR_OUTER = 7f
        const val TOWER_STAR_INNER = 3f

        /** A star's ten directions, points and notches in turn from straight up: worked out once, not per frame. */
        internal val STAR_COS = FloatArray(10) { Math.cos(Math.toRadians(-90.0 + it * 36.0)).toFloat() }
        internal val STAR_SIN = FloatArray(10) { Math.sin(Math.toRadians(-90.0 + it * 36.0)).toFloat() }

        /** The fir's gold (`tree_fir_star`'s), for a tower's star. */
        internal const val STAR_GOLD = 0xFFF2C24A.toInt()

        /** A shut building's blind (`drawShut`): its share of the glass, its rail's height in piece units, its colours. */
        const val BLIND_SHARE = 0.7f
        const val BLIND_RAIL = 2.4f
        internal const val BLIND_DAY = 0xFFE9DFC8.toInt()
        internal const val BLIND_NIGHT = 0xFF4A4F5A.toInt()
        internal const val BLIND_RAIL_DAY = 0xFFA89878.toInt()
        internal const val BLIND_RAIL_NIGHT = 0xFF2E3138.toInt()

        /**
         * A shut building's shutter's colours (`drawShut`; its geometry is [DoorShutter]'s): the face, the box and
         * slats, and its shadow paper -- the face's mixed 34 % towards the scene's ink `#2B2A33`, the generator's rule
         * for every card's shadow (`tools/assets/buildings/core.py`, `RELIEF_T`).
         */
        internal const val SHUTTER_DAY = 0xFF9A9DA3.toInt()
        internal const val SHUTTER_NIGHT = 0xFF3E4148.toInt()
        internal const val SHUTTER_SLAT_DAY = 0xFF767A81.toInt()
        internal const val SHUTTER_SLAT_NIGHT = 0xFF2C2E33.toInt()
        internal const val SHUTTER_UNDER_DAY = 0xFF74767D.toInt()
        internal const val SHUTTER_UNDER_NIGHT = 0xFF383941.toInt()


        /**
         * The penguin's belly (see `penguinBellyColor`), here so the gallery card paints the same white.
         */
        const val PENGUIN_BELLY_COLOR = 0xFFF3F7FB.toInt()

        /** The road's asphalt by day and by night; [roadColor] blends the two on the day ramp. */
        const val ROAD_COLOR_DAY = 0xFF5B5650.toInt()
        const val ROAD_COLOR_NIGHT = 0xFF29271F.toInt()

        /** The road at [dayBlend]: what [drawRoad] paints, and what the gallery card paints. */
        fun roadColor(dayBlend: Float): Int =
            SceneColour.blendArgb(ROAD_COLOR_NIGHT, ROAD_COLOR_DAY, dayBlend.coerceIn(0f, 1f))

        /**
         * Whether a scene draws its road: its layout has lanes and the Cars category is on; density is
         * not consulted ([drawRoad]). The gallery card asks this too.
         */
        fun drawsRoad(layout: SceneObjectLayout, customization: SceneCustomization): Boolean =
            layout.cars.isNotEmpty() && customization.cars.visible

        /**
         * Whether any tree may stand as a fir: *Christmas firs* on. The gallery card asks this before it
         * draws one (`ThemePreviewTruthTest`), so it never shows a fir its wallpaper does not draw.
         */
        fun drawsFirs(customization: SceneCustomization): Boolean = customization.christmasFirsEnabled

        /**
         * Whether this slot stands as a Christmas fir: a tree slot, *Christmas firs* on ([drawsFirs]),
         * and one of the third of the trees its seed picks ([isFirSeed]). The drawing asks the runtime's
         * form per frame; this one is for [SceneCustomization.palmSpeciesApplied], which must know before
         * there is a runtime and leaves a fir a tree among palms (`DESIGN_NOTES.md` decision 39). One
         * seed, one hash, so the two always agree.
         */
        fun standsAsFir(spec: StaticSceneObject, customization: SceneCustomization): Boolean =
            spec.type == SceneObjectType.TREE && drawsFirs(customization) && isFirSeed(idleSeedOf(spec))

        /**
         * The idle seed of a static object, its sway phase and fir hash: a function of its position, so
         * a rebuilt list gets the same seeds.
         */
        internal fun idleSeedOf(spec: StaticSceneObject): Float = (spec.tileFractionX * 97f) % 6.28f

        /**
         * Which of the four autumn tones a tree wears ([fallLeafColorFor]), from its position's seed:
         * the plan keeps it for every tree it moves (`SceneObjectCatalog.relocated`).
         */
        internal fun fallToneOf(idleSeed: Float): Int = (kotlin.math.abs(idleSeed * 1000).toInt()) % FALL_LEAF_TREE_PALETTE.size

        /**
         * **One tree in three, from the tree's own seed**: a count would need state, a position would
         * put the firs on a line, and a hash of the seed gives the *same* third on every frame.
         */
        internal fun isFirSeed(idleSeed: Float): Boolean {
            var h = (idleSeed * 100000f).toInt() * 0x9E3779B1.toInt()
            h = h xor (h ushr 16)
            return (h and 0x7FFFFFFF) % 3 == 0
        }

        /**
         * The season index of the summer column. Named rather than written as `0` at the window bust,
         * whose artwork has one season: a bare zero there reads as "the first season" and means "the only one".
         */
        const val SUMMER_SEASON = 0

        /**
         * How far apart two cars' seats are addressed: more than the seats, so a driver and the next
         * car's passenger never share an address, and with it every colour ([CandidateNoise]).
         */
        const val SEAT_ADDRESS_STRIDE = 8
        /**
         * Which drawing a static object resolves to, so which entry of the size table governs it (a
         * house is small or large; a building a tower, a restaurant, a school or a bar): one place, so an
         * object's scale and cull extent come from the same entry. Pure, so the mapping is unit-testable.
         */
        fun variantFor(spec: StaticSceneObject): SceneSpace.SceneVariant = when (spec.type) {
            // The family follows the dealt silhouette ([SilhouetteDeal.HOUSES] deals both families as
            // one set; a position hash alone clumps). The hash remains for a spec with no dealt
            // silhouette: an older custom theme, a random-theme layout, the flat preview.
            SceneObjectType.HOUSE ->
                SilhouetteDeal.houseVariantOrNull(spec.silhouette)
                    ?: if (kotlin.math.abs((spec.tileFractionX * 7561f).toInt()) % 2 == 1) {
                        SceneSpace.SceneVariant.HOUSE_LARGE
                    } else {
                        SceneSpace.SceneVariant.HOUSE_SMALL
                    }
            // By depth, never by a hash of the position (which would tie identity to the jitter): a
            // tower on the skyline, a place among the houses in a third of the shop band, its row of the
            // size table; which of the six stands there is the street's ([StorefrontStreet]). These
            // arms read the same thresholds as `SceneObject.singleShopPerVariant`,
            // `SilhouetteDeal.catalogueFor` and `CustomThemeData.migrateDuplicateStorefronts`
            // (`SceneVariantResolutionTest`, `PrePassSixThemeMigrationTest`).
            SceneObjectType.SKYSCRAPER ->
                if (spec.depthFraction < SceneSpace.BUILDING_TOWER_MAX_DEPTH) {
                    SceneSpace.SceneVariant.TOWER
                } else if (spec.depthFraction < SceneSpace.RESTAURANT_MAX_DEPTH) {
                    SceneSpace.SceneVariant.RESTAURANT
                } else if (spec.depthFraction < SceneSpace.SCHOOL_MAX_DEPTH) {
                    SceneSpace.SceneVariant.SCHOOL
                } else {
                    SceneSpace.SceneVariant.BAR
                }
            SceneObjectType.TREE -> SceneSpace.SceneVariant.TREE
            SceneObjectType.PALM_TREE -> SceneSpace.SceneVariant.PALM_TREE
            SceneObjectType.PARASOL -> SceneSpace.SceneVariant.PARASOL
            SceneObjectType.SNOWMAN -> SceneSpace.SceneVariant.SNOWMAN
            SceneObjectType.GIFT -> SceneSpace.SceneVariant.GIFT
            SceneObjectType.PENGUIN -> SceneSpace.SceneVariant.PENGUIN
            SceneObjectType.BUNNY -> SceneSpace.SceneVariant.BUNNY
            SceneObjectType.EASTER_EGG -> SceneSpace.SceneVariant.EASTER_EGG
            SceneObjectType.PUMPKIN -> SceneSpace.SceneVariant.PUMPKIN
            SceneObjectType.SCARECROW -> SceneSpace.SceneVariant.SCARECROW
            // Cars are drawn by drawCar, from their lane, and never reach drawStaticObject.
            SceneObjectType.CAR -> SceneSpace.SceneVariant.HOUSE_SMALL
        }

        /**
         * The scale one static object is drawn at: the one place the four stages of `SceneSpace`'s doc
         * comment are multiplied, pure, so the size pipeline is testable without a `Canvas`.
         */
        fun effectiveScaleFor(spec: StaticSceneObject, screenHeightPx: Float): Float =
            effectiveScaleFor(spec, spec.depthFraction, screenHeightPx)

        /**
         * The same at [drawDepth] (a tower off the crest is as large as it is drawn,
         * [SceneSpace.towerDrawnDepth]). **A place among the houses is never smaller than
         * [placeScaleFloor]**: its buildings' details are 2.4 units or more at the built-in streets'
         * farthest place, where the floor never binds (`StorefrontDetailTest`); a random theme's places
         * (`RandomSceneGenerator`) can stand farther and smaller.
         */
        fun effectiveScaleFor(spec: StaticSceneObject, drawDepth: Float, screenHeightPx: Float): Float {
            val variant = variantFor(spec)
            val drawn = variant.baseScale *
                spec.scale *
                SceneSpace.depthScale(drawDepth) *
                SceneSpace.sceneScale(screenHeightPx)
            val floor = placeScaleFloor(variant, screenHeightPx)
            return if (drawn < floor) floor else drawn
        }

        /**
         * The smallest scale a place among the houses is drawn at (0 for anything else): a piece unit at
         * least [PLACE_UNIT_SCREEN_SHARE] of the screen's height, so the generator's 2.4-unit details
         * (`civic.DETAIL`) are 2 px on the BV6600's 1 440, the size under which a detail stops reading.
         */
        internal fun placeScaleFloor(variant: SceneSpace.SceneVariant, screenHeightPx: Float): Float = when (variant) {
            SceneSpace.SceneVariant.RESTAURANT -> screenHeightPx * PLACE_UNIT_SCREEN_SHARE * RESTAURANT_PIECE_UNITS_A_UNIT
            SceneSpace.SceneVariant.SCHOOL -> screenHeightPx * PLACE_UNIT_SCREEN_SHARE * SCHOOL_PIECE_UNITS_A_UNIT
            SceneSpace.SceneVariant.BAR -> screenHeightPx * PLACE_UNIT_SCREEN_SHARE * BAR_PIECE_UNITS_A_UNIT
            else -> 0f
        }

        /** See [placeScaleFloor]: 2 px for 2.4 units on 1 440, a piece unit 1/1 728 of the screen's height. */
        internal const val PLACE_UNIT_SCREEN_SHARE = 2f / (2.4f * 1440f)

        // The inverse of each place's variant units a piece unit (`spriteUnitsTall / unitsTall`), for
        // [placeScaleFloor]: computed once, so the frame's arithmetic looks nothing up.
        private val RESTAURANT_PIECE_UNITS_A_UNIT = piecesPerVariantUnit(SceneSpace.SceneVariant.RESTAURANT)
        private val SCHOOL_PIECE_UNITS_A_UNIT = piecesPerVariantUnit(SceneSpace.SceneVariant.SCHOOL)
        private val BAR_PIECE_UNITS_A_UNIT = piecesPerVariantUnit(SceneSpace.SceneVariant.BAR)

        private fun piecesPerVariantUnit(variant: SceneSpace.SceneVariant): Float =
            NeighbourhoodTable.FAMILIES.getValue(variant).unitsTall / variant.spriteUnitsTall

        /**
         * Cool glass by day, warm light at night: every lit window's two ends, on `nightGlow`
         * ([windowGlassColor]).
         */
        const val WINDOW_GLASS_DAY = 0xFFB9CBD9.toInt()
        const val WINDOW_GLASS_NIGHT = 0xFFFFE79A.toInt()

        /** A door lamp's bulb lit, and its glow. */
        const val LAMP_LIT = 0xFFFFD97A.toInt()

        /** A door lamp's bulb by day: paper, the warm grey of the buildings' cream trim. */
        const val LAMP_UNLIT = 0xFFCFC5AE.toInt()

        /** How strongly the glow round a lit bulb is painted: its night alpha. */
        const val LAMP_GLOW_ALPHA = 150f

        /** A door lamp's bulb at [lit] (0 by day, 1 at full night): paper to light, on the glass's ramp. */
        fun lampBulbColour(lit: Float): Int = SceneColour.blendArgb(LAMP_UNLIT, LAMP_LIT, lit.coerceIn(0f, 1f))

        /** The alpha of the glow round a door lamp at [lit]: none until it lights. */
        fun lampGlowAlpha(lit: Float): Int = (LAMP_GLOW_ALPHA * lit.coerceIn(0f, 1f)).toInt()

        /**
         * A lit window's colour, one pair for the whole scene; in the companion so the gallery card
         * asks it rather than copy it (`ThemePreviewScenes.neighbourhood`).
         */
        fun windowGlassColor(nightGlow: Float): Int =
            SceneColour.blendArgb(WINDOW_GLASS_DAY, WINDOW_GLASS_NIGHT, nightGlow.coerceIn(0f, 1f))

        /**
         * A window dark at night, at full night (`DESIGN_NOTES.md` decision 55): a window with the light
         * off, where a grey would read as cold light. A house's unlit windows ([WindowRoster]) and a
         * distant house's go to it on the same `nightGlow`.
         */
        const val UNLIT_GLASS_NIGHT = 0xFF343B47.toInt()

        /** An unlit window at [nightGlow]: the day's glass going to [UNLIT_GLASS_NIGHT]. */
        fun unlitWindowGlassColor(nightGlow: Float): Int =
            SceneColour.blendArgb(WINDOW_GLASS_DAY, UNLIT_GLASS_NIGHT, nightGlow.coerceIn(0f, 1f))

        /**
         * A business's glass at [nightGlow], [openness] open ([SceneCustomization.opennessFor]): lit
         * while open, the dark glass of an unlit window while shut, blended across the doorway's two
         * seconds ([WindowWalk.Doorway.glassAt]) -- dark at night when closed, the day's glass by day
         * (`DESIGN_NOTES.md` decision 58). Fully open, and wherever the two ends are one colour, it
         * returns that colour to the bit, never a blend of it with itself rounded a step down.
         */
        fun businessGlassColor(nightGlow: Float, openness: Float): Int {
            val lit = windowGlassColor(nightGlow)
            if (openness >= 1f) return lit
            val unlit = unlitWindowGlassColor(nightGlow)
            return if (unlit == lit) lit else SceneColour.blendArgb(unlit, lit, openness.coerceIn(0f, 1f))
        }

        /**
         * How far inside its pane a house's lit window is laid over the dark glass, in piece units: the
         * glass mask's cut edge wobbles by up to 0.3 of a unit, and the light must not cross it.
         */
        const val LIT_PANE_INSET = 0.4f

        /** A building's own seed for who stands at its windows: its place along the tile, fixed for life. */
        fun buildingSeedOf(spec: StaticSceneObject): Int = (spec.tileFractionX * 100_003f).toInt()

        /**
         * The pieces of [storefront] for its [identity], into [into]: by the identity's own silhouette
         * where it stood in its own row (the bar keeps the figure its place was dealt, [SilhouetteDeal]),
         * from its position otherwise. Never from the catalogue of the place it stands in, which could
         * deal the bar's two figures to a building of one.
         */
        internal fun dealStorefront(
            family: BuildingFamily,
            storefront: Storefront,
            identity: StaticSceneObject,
            into: NeighbourhoodComposer.Deal,
        ) {
            if (storefront.stoodThere && variantFor(identity) == storefront.home) {
                NeighbourhoodComposer.deal(family, identity, into)
            } else {
                NeighbourhoodComposer.deal(family, identity.tileFractionX, identity.depthFraction, into)
            }
        }

        /**
         * A 22-unit occupant box, read today only by `UnitFrameTest` and `SpriteDrawScaleTest`;
         * the renderer sizes each bust from its window's own declared width ([drawWindowOccupant]).
         */
        const val OCCUPANT_BOX_UNITS = 22f

        /** Half a walk sprite's own width, in local units, for the wrap-tile cull. */
        const val PERSON_HALF_WIDTH_UNITS = 19.5f

        /**
         * Half a walker's width for the cull while an umbrella is up: the 48-unit canopy hangs on the hand,
         * 12 units ahead of an adult's centre ([drawUmbrella]), so it reaches 36. Cut at the sprite's 19.5, a
         * canopy would pop in up to 7 px wide on the BV6600, and a change made only off screen could show.
         */
        const val PERSON_CARRYING_HALF_WIDTH_UNITS = 36f

        /** Half the width the wrap-tile cull gives a walker: the walk sprite's, or the umbrella's reach while one is up. */
        internal fun personCullHalfWidthUnits(carrying: Boolean): Float =
            if (carrying) PERSON_CARRYING_HALF_WIDTH_UNITS else PERSON_HALF_WIDTH_UNITS

        /**
         * The hand's centre on the 39x84 sprite canvas, **per family** (`kindIndex`), from
         * `tools/assets/concepts/people/carry/hands.json`: pose P1 holds the forearm still, so the hand is
         * in one place on all three walk frames (the generator asserts it). The adults share (31.5, 24.5),
         * 59.5 units above the feet, higher than a whole child, so the children hold on their own raised
         * forearm, 34.9 units up; the girl's is a unit lower than the boy's, her hair standing 2.5 units
         * above the head oval against his cap's 1.5 (`CHILD_SUMMER_RISE`).
         */
        internal val carryHandX = floatArrayOf(31.5f, 31.5f, 28.0f, 28.0f)
        internal val carryHandY = floatArrayOf(24.5f, 24.5f, 49.09f, 50.09f)

        /**
         * How big the canopy is drawn for each family: a child under the full 48 units (on a figure 54
         * tall) holds *its father's* umbrella, at 70 % its own. **1.0 is the absence of a scale**:
         * [drawUmbrella] blits the adults' untouched, so an adults-only golden shows the adults unmoved.
         */
        internal val carryCanopyScale = floatArrayOf(1f, 1f, 0.7f, 0.7f)

        /**
         * Where a walking person's sprite origin goes, so its content bottom-centre lands on the
         * pavement line. Every walk sprite (drawings, layers, masks) is 117x252 px = 39x84 units with its
         * content on the canvas's bottom edge, so one pair covers the set.
         */
        const val PERSON_ANCHOR_X_UNITS = -19.5f
        const val PERSON_ANCHOR_Y_UNITS = -84f

        // ---- The people behind a windscreen ---------------------------------------------
        //
        // A pedestrian's head is 30% of their height ([PERSON_HEAD_SPRITE_UNITS] of 80), the
        // proportion the whole scene is drawn in, and the people in a vehicle are too: sized off the
        // height table, not to fit the glass, where on a plane nearer than the pavement they would
        // read as children (`VehiclePedestrianScaleTest`, `VehicleScalePixelTest`). The vehicle's
        // height is right and has no room to grow (`PIXELS_PER_METRE_AT_REFERENCE`): the glass is
        // sized for the people instead ([CAR_GLASS_TOP_Y_UNITS]).

        /** Half of `firetruck_body`'s 100-unit canvas: the widest vehicle the road carries. */
        const val FIRE_TRUCK_HALF_WIDTH_UNITS = 50f

        /**
         * Where a vehicle's wheels touch the road, in [drawCar]'s local space: the artwork puts the
         * wheel bottom at y=37 (25 + a car's 12-unit radius, 26 + the fire engine's 11), so the sprites
         * keep their authored coordinates and **y=0 is the beltline, not the ground**: anything on the
         * road, a ground shadow first, has to say so.
         */
        const val VEHICLE_GROUND_Y_UNITS = 37f

        /**
         * The per-body numbers (origins, roofs, wheels, glass, lamps, shadows) are [CarShell]'s; here
         * is what the three bodies share: the cabin's vertical layout, the seats, the wheels, the liveries.
         */

        /** The fire engine's ground shadow; each car body carries its own, see [CarShell]. */
        const val FIRE_TRUCK_SHADOW_HALF_LENGTH_UNITS = 44f

        const val FIRE_TRUCK_WHEEL_X_UNITS = 34f

        /**
         * The inner wheel of the rear pair. A twin rear axle is the one silhouette cue that says
         * heavy vehicle and nothing else at scene scale. 9.325 against the rear wheel at 34 puts the
         * centres 24.675 apart on 22-unit wheels, 1.12 diameters with a 2.7-unit gap: overlapping,
         * the inner wheel would read as a crescent of tyre with its hub eaten. `TwinAxleSpacingTest`
         * measures the spacing off the rendered PNG.
         */
        const val FIRE_TRUCK_INNER_WHEEL_X_UNITS = 9.325f

        /**
         * The tyre and its hub: a whole disc of card glued on the body, no arch cut over it
         * (`VehicleAndShopFrontTest`), its centre one radius above [VEHICLE_GROUND_Y_UNITS] whatever
         * the radius. The appliance's wheel is bigger than the saloon's, so it does not read as a large
         * car; the hub is 0.34 of the tyre on both, a filled disc 7.2 px across in the near lane, where
         * a ring would be a stroke under a pixel.
         */
        const val CAR_WHEEL_RADIUS_UNITS = 12f
        const val FIRE_TRUCK_WHEEL_RADIUS_UNITS = 11f
        const val WHEEL_HUB_RATIO = 0.34f

        /** The paper the discs are cut from: ink for the tyre, a light card for the hub. */
        val WHEEL_TYRE_COLOUR = 0xFF2B2A33.toInt()
        val WHEEL_HUB_COLOUR = 0xFFC9C3BA.toInt()

        /**
         * The livery band on the doors, for the police car (the saloon) and the taxi (the compact):
         * 40 units, so it lies on the shell for its whole width on both, which `VehicleAndShopFrontTest`
         * checks on the pixels.
         */
        const val CAR_LIVERY_WIDTH_UNITS = 40f
        const val CAR_LIVERY_X_UNITS = -CAR_LIVERY_WIDTH_UNITS / 2f

        /**
         * `taxi_sign` is 42x18 px: 14 x 6 local units, standing centred on the roof of [CarShell.COMPACT],
         * the taxi's body. The roof accessories are why the liveried types keep one body ([CarShell.forCar]).
         */
        const val TAXI_SIGN_WIDTH_UNITS = 14f
        val TAXI_SIGN_X_UNITS =
            (CarShell.COMPACT.roofFrontXUnits + CarShell.COMPACT.roofRearXUnits - TAXI_SIGN_WIDTH_UNITS) / 2f
        const val TAXI_SIGN_HEIGHT_UNITS = 6f
        val TAXI_SIGN_Y_UNITS = -CarShell.COMPACT.unitsTall + VEHICLE_GROUND_Y_UNITS - TAXI_SIGN_HEIGHT_UNITS

        /**
         * The lit parts of a vehicle, inside shapes drawn at every hour (the police bar's halves, the
         * taxi sign's box): one rectangle each at night, nothing by day.
         */
        val TAXI_SIGN_BOX_LEFT_X = TAXI_SIGN_X_UNITS + 1.4f
        val TAXI_SIGN_BOX_RIGHT_X = TAXI_SIGN_X_UNITS + 12.6f
        val TAXI_SIGN_BOX_TOP_Y = TAXI_SIGN_Y_UNITS + 0.4f
        val TAXI_SIGN_BOX_BOTTOM_Y = TAXI_SIGN_Y_UNITS + 4.4f

        /**
         * The fire engine's lamp seats. It takes the *same two lenses* the cars do -- one amber
         * sprite and one red sprite for the whole fleet -- so it reads as one set with them, and its
         * lamps say which way it is going the way every car's do.
         */
        const val FIRE_TRUCK_LAMP_FRONT_X_UNITS = -49f
        const val FIRE_TRUCK_LAMP_FRONT_Y_UNITS = 2f
        const val FIRE_TRUCK_LAMP_REAR_X_UNITS = 45.5f
        const val FIRE_TRUCK_LAMP_REAR_Y_UNITS = 4f

        /** What a lamp is when it is lit. Warm for the taxi's sign, saturated for a beacon. */
        const val TAXI_SIGN_LIT = 0xFFFFE9A8.toInt()
        val BEACON_RED_LIT = 0xFFFF6A57.toInt()
        val BEACON_BLUE_LIT = 0xFF6E9BFF.toInt()

        /** `police_lightbar` is 60x18 px: 20 x 6 local units. */
        const val POLICE_LIGHTBAR_WIDTH_UNITS = 20f

        /** Centred on the roof of the body a police car actually is, [CarShell.SALOON]. */
        val POLICE_LIGHTBAR_X_UNITS =
            (CarShell.SALOON.roofFrontXUnits + CarShell.SALOON.roofRearXUnits - POLICE_LIGHTBAR_WIDTH_UNITS) / 2f

        /** The light bar's own height, so its base sits on the roof line rather than through it. */
        const val POLICE_LIGHTBAR_HEIGHT_UNITS = 6f
        val POLICE_LIGHTBAR_Y_UNITS =
            -CarShell.SALOON.unitsTall + VEHICLE_GROUND_Y_UNITS - POLICE_LIGHTBAR_HEIGHT_UNITS

        /**
         * The lit beacons, derived from the bar's own blit origin and from where
         * `police_lightbar.svg` paints its two domes (red 0..9.6, blue 10.4..20, both 0..4.5 in
         * the bar's 20x6 local units) -- inset ~0.8 so the dark surround survives at night. Derived,
         * not absolute, so the lamps follow the bar wherever the roof puts it.
         */
        val POLICE_LAMP_TOP_Y_UNITS = POLICE_LIGHTBAR_Y_UNITS + 0.7f
        val POLICE_LAMP_BOTTOM_Y_UNITS = POLICE_LIGHTBAR_Y_UNITS + 3.8f
        val POLICE_LAMP_RED_LEFT_X = POLICE_LIGHTBAR_X_UNITS + 0.8f
        val POLICE_LAMP_RED_RIGHT_X = POLICE_LIGHTBAR_X_UNITS + 8.8f
        val POLICE_LAMP_BLUE_LEFT_X = POLICE_LIGHTBAR_X_UNITS + 11.2f
        val POLICE_LAMP_BLUE_RIGHT_X = POLICE_LIGHTBAR_X_UNITS + 19.2f

        /**
         * The floor under [vehicleEdgeMarginPx]: at the reference viewport the derived margin is
         * smaller than this, so this is the margin used there.
         */
        const val LEGACY_EDGE_MARGIN_PX = 120f

        /**
         * The divisor `drawWindowOccupant` scales a bust by: a tuned value, **not** the canvas width
         * (49 units), so the bust is a little narrower than the nominal 85% of the pane, as the artwork
         * was judged. Never re-derive it from the canvas, which a crop changes without changing the
         * picture: the share that means anything is the head's, [OCCUPANT_HEAD_PANE_SHARE]
         * (`OneOccupantRuleTest`, `SpriteMeasurementClaimTest`).
         */
        const val WINDOW_OCCUPANT_DIVISOR_UNITS = 60f

        /**
         * The window busts' CONTENT_BOTTOM_CENTRE anchor, in their own 49x57-unit canvas: the
         * midpoint of the eight heads' declared x, and the canvas bottom. Buildings only; the
         * vehicles' seated family carries its own anchor below. A crop of the canvas moves it, or it
         * moves the sprite.
         */
        const val WINDOW_HEAD_ANCHOR_X_UNITS = 23.8f
        const val WINDOW_HEAD_ANCHOR_Y_UNITS = 57f

        /**
         * **The cabin's vertical layout, identical on all three bodies**, so an occupant is one size in
         * every car. The pane is 25 units, -12 to the sill at 13, the bodies drawn around it: a
         * table-sized bust (about 22.1 units drawn at [CAR_OCCUPANT_SCALE]) leaves about **12% of air**,
         * inside the 10-25% band; the seats and the head-fit criteria are measured from it. The glass
         * sprite is larger than the pane: «Cut-out» lays the glass *behind* the body paper, grown half a
         * unit past the hole (or a seam opens) plus half a unit of canvas, blitted at -13, 27 tall.
         */
        const val CAR_GLASS_SPRITE_Y_UNITS = -13f
        const val CAR_GLASS_SPRITE_HEIGHT_UNITS = 27f
        const val CAR_GLASS_TOP_Y_UNITS = -12f
        const val CAR_GLASS_HEIGHT_UNITS = 25f

        /** The window sill: the bottom edge of the drawn glass, and where a bust stands. */
        const val CAR_SILL_Y_UNITS = CAR_GLASS_TOP_Y_UNITS + CAR_GLASS_HEIGHT_UNITS

        /**
         * The head part of a window bust, in its own local units: hair crown down to the neck.
         * What the house, shop and tower occupants are proportioned by; the vehicles' people are
         * sized off the height table instead (see below).
         */
        const val WINDOW_HEAD_HEAD_UNITS = 110f / SpriteBlitter.SPRITE_PIXELS_PER_UNIT

        /**
         * The share of a building's pane a head takes, read back out of the expression
         * `drawWindowOccupant` draws house, shop and tower occupants with (`0.85 * 36.667 / 60` of
         * the pane = 51.9%). Buildings only: the vehicles' people are sized off the height table.
         */
        const val OCCUPANT_HEAD_PANE_SHARE =
            0.85f * WINDOW_HEAD_HEAD_UNITS / WINDOW_OCCUPANT_DIVISOR_UNITS

        // ---- The occupants, sized off the height table -------------------------------------
        //
        // Every human in the scene is sized by the height table -- PERSON_METRES_TALL times the
        // projection at their ground line -- the people in the vehicles included: sized as a share
        // of the glass behind them, a driver's head would come out smaller than a child pedestrian's.

        /**
         * A pedestrian's head, measured off `person_man_summer_walk0`: crown at row 5, jaw at row 77 of
         * 252, 73 px = 24.3 of the figure's 80 units (`OccupantHeadFitTest` re-measures it). The jaw is
         * found by width, where the skin narrows into the neck: the neck is drawn in skin, so the first
         * run of skin rows would end below the collar.
         */
        const val PERSON_HEAD_SPRITE_UNITS = 24.3f

        /** That head in scene metres: the one size every head in the scene derives from. */
        const val OCCUPANT_HEAD_METRES =
            SceneSpace.PERSON_METRES_TALL * PERSON_HEAD_SPRITE_UNITS / SceneSpace.PERSON_SPRITE_UNITS_TALL

        /**
         * How much of the standing head a seated occupant is drawn at: 97%, for the roof. At 100%
         * the tallest winter hat needs a 24.8-unit pane, short of the 10% of air the 10-25% band asks
         * of the 25-unit one; 0.97 keeps every seasonal bust inside the band, and well inside the
         * +/-10% the occupant-vs-pedestrian criterion allows.
         */
        const val OCCUPANT_SEATED_FIT = 0.97f

        /**
         * The head on the seated bust artwork: the adults of `person_*_head_car` carry it as **32.33 to
         * 33.00** of the shared 38x42-unit canvas, the children as 28.67 to 29.00, the size table's 0.9;
         * the scales divide by the adult head, so a child comes out 10% shorter. Measured by the
         * pedestrians' rule ([PERSON_HEAD_SPRITE_UNITS]), the two being compared (`OccupantHeadFitTest`).
         * There is no clip in [SceneCanvas]: a bust anchored on the sill fits the pane by its authored
         * geometry alone.
         */
        const val HEAD_CAR_HEAD_UNITS = 32.5f

        /** The seated family's CONTENT_BOTTOM_CENTRE anchor on its shared 38x42-unit canvas: x between
         * the eyes, where all eight are authored (a content-centred seat would follow the woman's
         * side-swept hair), y the canvas bottom, as every person family (SpriteGeometryTest). */
        const val HEAD_CAR_ANCHOR_X_UNITS = 19f
        const val HEAD_CAR_ANCHOR_Y_UNITS = 42f

        /**
         * One scale per vehicle family, each the same rule: the table head, seat-fitted, in the
         * vehicle's own units, over the artwork's head. Not a share of the pane: the pane was
         * sized to fit the head (see [CAR_GLASS_TOP_Y_UNITS]), not the head to fit the pane.
         */
        val CAR_OCCUPANT_SCALE: Float
            get() = OCCUPANT_SEATED_FIT * OCCUPANT_HEAD_METRES /
                SceneSpace.CAR_UNIT_METRES / HEAD_CAR_HEAD_UNITS
        val FIRE_TRUCK_OCCUPANT_SCALE: Float
            get() = OCCUPANT_SEATED_FIT * OCCUPANT_HEAD_METRES /
                (SceneSpace.FIRE_TRUCK_METRES_TALL / SceneSpace.FIRE_TRUCK_SPRITE_UNITS_TALL) /
                HEAD_CAR_HEAD_UNITS

        /**
         * The two seats, **shared by all three bodies**: driver at -7.75, passenger at 13.75, a
         * pitch of **21.5** units about a cabin centre of 3.
         *
         * Two 17-unit heads sit 4.5 units apart: wider, an outer head loses its pillar light on the
         * saloons; narrower, two heads read as one mass (worst on the winter girl's bunches). Both seats
         * move together, so the pair keeps its centre between the panes.
         *
         * Checked on the rendered artwork for every body, pairing and season, crown-to-chin: no occupant
         * pixel outside the glass, pillar light at least **15%** of the head
         * (`everyOccupantClearsItsPillarsByFifteenPercentOfItsHead`), a head gap above 12%, the glass
         * over half filled. The floors are of the head, from legibility: in the far lane (~1.44 px a
         * unit) glass under ~3 px reads as a seam, 2.1 units, 12% of a head. Below the chin the busts
         * meet: the shoulders' overlap is the depth cue between the seats.
         */
        const val CAR_HEAD_X_UNITS = -7.75f
        const val CAR_HEAD_Y_UNITS = CAR_SILL_Y_UNITS
        const val CAR_PASSENGER_X_UNITS = 13.75f
        const val CAR_PASSENGER_Y_UNITS = CAR_SILL_Y_UNITS

        /**
         * No seat back is drawn: «Cut-out» cuts a car's glass as *two* holes with a 3.5-unit pillar of
         * body paper between them (x 0.5 to 4), where a seat back would stand, so the mark between the
         * seats is the artwork's and the shoulders close over its lower half.
         */

        /**
         * The fire engine's cab glass, painted into `firetruck_body`: 19 units from -13 to the sill
         * at 6, following the upright windscreen. The cab is long enough for a table-sized head to keep
         * daylight to the A-pillar, and at 19 units a bust of at most 15.6 units leaves about 18% of
         * air, inside the same 10-25% band the cars use.
         */
        const val FIRE_TRUCK_GLASS_HEIGHT_UNITS = 19f
        const val FIRE_TRUCK_SILL_Y_UNITS = 6f
        const val FIRE_TRUCK_HEAD_X_UNITS = -21f
        const val FIRE_TRUCK_HEAD_Y_UNITS = FIRE_TRUCK_SILL_Y_UNITS

        /**
         * The fire engine in the cars' «Cut-out» language, its cab roof eight units below the body roof
         * (the line that says truck, not a large car), its windscreen upright with room for a table-sized
         * head. The canvas, trimmed to the ink, is blitted at its top-left (-50,-25).
         */
        const val FIRE_TRUCK_BODY_X_UNITS = -50f
        const val FIRE_TRUCK_BODY_Y_UNITS = -25f
        const val FIRE_TRUCK_CAB_ROOF_Y_UNITS = -16f
        const val FIRE_TRUCK_LADDER_X_UNITS = -2f
        const val FIRE_TRUCK_LADDER_Y_UNITS = -31f

        /** The two beacons on the cab roof, in the cab's own coordinates. */
        const val FIRE_TRUCK_BEACON_TOP_Y_UNITS = -19.4f
        const val FIRE_TRUCK_BEACON_RED_LEFT_X = -24f
        const val FIRE_TRUCK_BEACON_RED_RIGHT_X = -16f
        const val FIRE_TRUCK_BEACON_BLUE_LEFT_X = -14f
        const val FIRE_TRUCK_BEACON_BLUE_RIGHT_X = -6f

        /** Radius of one blinking light on a winter tree, in local units. */
        const val CHRISTMAS_LIGHT_RADIUS_UNITS = 2.6f

        /**
         * Where a tree's lights sit, in a unit disc ([drawChristmasLights]); internal so the gallery card's
         * leafy tree hangs its own at the same points (`ThemePreviewScenes`, *Lights on the trees*).
         */
        internal val CHRISTMAS_LIGHT_X = floatArrayOf(-0.80f, -0.34f, 0.06f, 0.40f, 0.78f, -0.06f)
        internal val CHRISTMAS_LIGHT_Y = floatArrayOf(0.10f, -0.52f, 0.46f, -0.52f, 0.14f, -0.08f)

        /** The leafy tree's foliage the lights are scattered across, in the canopy's lifted space ([drawTree]). */
        const val TREE_LIGHTS_CENTRE_Y = -47f
        const val TREE_LIGHTS_RADIUS_X = 38f
        const val TREE_LIGHTS_RADIUS_Y = 24f

        /**
         * Upper bound on how far any static object reaches either side of its origin, in local units
         * before scaling, for the cull: the widest, the large house's mansard roof, reaches about 77, so
         * 96 leaves a quarter of headroom. Too large costs a few draws at the edges, too small clips: raise
         * it if anything is drawn wider.
         */
        const val MAX_OBJECT_HALF_WIDTH_UNITS = 96f

        /**
         * How many canopies a frame can offer the falling leaves: a fixed ceiling, since the arrays are
         * refilled every frame and must not grow, and the leaves need only somewhere to come from.
         */
        const val MAX_LEAF_SOURCES = 24

        /**
         * Screen pixels of crown half-width per shed leaf: a near crown of ~85 px half-width sheds
         * 12, inside the 3 to 13 a crown of `DESIGN_NOTES.md` decision 10.
         */
        const val LEAF_SOURCE_PX_PER_LEAF = 7f

        /** The presents under a fir, in the tree lights' own palette. */
        val GIFT_COLOURS = intArrayOf(0xFFE8564F.toInt(), 0xFF4F8FBF.toInt(), 0xFF6FCF6F.toInt())

        /** How many flower clumps the ground carries on each screen of the street, and where they may stand. */
        const val FLOWER_CLUMP_COUNT = 22
        const val FLOWER_DEPTH_MIN = 0.06f
        const val FLOWER_DEPTH_MAX = 0.92f
        const val FLOWER_METRES_TALL = 0.55f
        const val FLOWER_SPRITE_UNITS_TALL = 12f

        /**
         * Which clump the ground carries: in bloom, or gone to seed under the autumn or the winter
         * palette, which decides; `flowersEnabled` says only whether (`DESIGN_NOTES.md` decision 35).
         * Read by [drawGroundFlowers] and `ThemePreviewScene`, so the card cannot show another clump.
         */
        fun groundFlowerSprite(customization: SceneCustomization): Int =
            if (customization.fallColorsEnabled || customization.winterColorsEnabled) {
                R.drawable.ground_flowers_dry
            } else {
                R.drawable.ground_flowers_bloom
            }

        /**
         * The most drifts or heaps a screen of the street carries at 100%, and how big one is: fewer
         * than [FLOWER_CLUMP_COUNT], a drift being chunkier, or the floor reads as covered.
         */
        const val PILE_MAX_COUNT = 18
        const val PILE_METRES_TALL = 0.42f
        const val PILE_SPRITE_UNITS_TALL = 7f

        /** Distinct hash salts, so a drift, a heap and a clump of flowers never coincide. */
        const val PILE_SALT_SNOW = 733
        const val PILE_SALT_LEAF = 1471

        /**
         * How many piles a slider position draws. Truncating rather than rounding keeps any
         * density under 1/18 at zero piles, where rounding would draw one lonely drift from 2.8%;
         * at **0% nothing is drawn**, so a scene with the piles at 0% pays nothing for them.
         */
        fun pileCount(density: Float): Int = (density.coerceIn(0f, 1f) * PILE_MAX_COUNT).toInt()

        /**
         * The identity of one tile copy of one tree, for its leaves ([leafSourceId]): the runtime index
         * and [copy], the physical copy [copyOf] numbers, which keeps its number across the wrap. Without the
         * copy two visible copies would shed the same leaves; with a number that moves while the copy is on
         * screen, its leaves would be dealt again there, mid-fall.
         * A `Long`; it can be negative (the copy to the left of the start is -1).
         */
        internal fun leafCopyId(runtimeIndex: Int, copy: Int): Long = runtimeIndex.toLong() * LEAF_COPY_STRIDE + copy

        /** How far apart two trees' identities lie ([leafCopyId]): room for a million copies of each. */
        const val LEAF_COPY_STRIDE = 1_000_003L

        /**
         * How many things a scatter of [perScreen] a screen puts on a tile [tileWidth] wide: as many on
         * every screen of it (the street's tile is two), [perScreen] before the surface is sized. Thing `i`
         * stands in the `i`-th slice of a screen ([scatterSlice]).
         */
        internal fun scatterCountOnTile(perScreen: Int, tileWidth: Float, screenWidth: Float): Int =
            if (tileWidth > 0f && screenWidth > 0f) perScreen * Math.round(tileWidth / screenWidth) else perScreen

        /** The width of the slice each thing of a scatter laid [perScreen] to a screen stands in, jittered inside it. */
        internal fun scatterSlice(screenWidth: Float, perScreen: Int): Float = screenWidth / perScreen

        /**
         * Whether an object at [x], [halfWidth] either side, can touch a viewport [screenWidth] wide:
         * the real viewport, at any size. Pure, so both edges are unit tested.
         */
        fun isHorizontallyVisible(x: Float, halfWidth: Float, screenWidth: Float): Boolean =
            x + halfWidth >= 0f && x - halfWidth <= screenWidth

        /**
         * The first tile index `k` worth scanning for the on-screen copies of an object at [x], which
         * also stands at every `x + k * tileWidth`: **at or before** the first visible copy, for any
         * object extent and tiling period. `floor`, not the exact `ceil`, because float rounding at a
         * tile boundary can move the quotient either way: one tile early costs a rejected iteration,
         * one tile late would drop a copy, a pop at the edge. Pure, so tested directly; [tileWidth]
         * must be positive.
         */
        fun firstVisibleTileOffset(x: Float, halfWidth: Float, tileWidth: Float): Int =
            kotlin.math.floor((-halfWidth - x) / tileWidth).toInt()

        /**
         * One past the last tile index worth considering, the first copy whose *left* edge has passed
         * the right of the screen: with [firstVisibleTileOffset], the range [draw] walks, as pure
         * functions the tests exercise rather than a loop condition behind a `Canvas`. A copy touching
         * the right edge is inside, as in [isHorizontallyVisible]'s inclusive `<=`, which still decides
         * each copy. [tileWidth] must be positive.
         */
        fun tileOffsetLimit(x: Float, halfWidth: Float, tileWidth: Float, screenWidth: Float): Int =
            kotlin.math.floor((screenWidth + halfWidth - x) / tileWidth).toInt() + 1

        /** [anchorX] for an object standing at [tileFractionX] of the tile: pure, for the tests of the street. */
        internal fun anchorXOf(tileFractionX: Float, geom: GroundGeometry): Float {
            var x = geom.shiftXWrapped + tileFractionX * geom.tileWidth
            if (x < -geom.tileWidth * 0.5f) x += geom.tileWidth
            return x
        }

        /**
         * The physical copy [anchorXOf]'s representative belongs to (the copy at `x + tileIndex *
         * tileWidth` is `representativeCopy + tileIndex`), by [anchorXOf]'s own expressions, with the
         * scroll's whole tiles ([GroundGeometry.scrollTileBias]) taken out: a copy keeps its number while
         * the wrap moves on. [StorefrontStreet] deals a place's building by it.
         */
        internal fun representativeCopy(tileFractionX: Float, geom: GroundGeometry): Int {
            val wrapped = geom.shiftXWrapped + tileFractionX * geom.tileWidth < -geom.tileWidth * 0.5f
            return (if (wrapped) 1 else 0) - geom.scrollTileBias
        }

        /**
         * The physical copy drawn at `anchorXOf(tileFractionX, geom) + tileIndex * tileWidth`: what the
         * draw loop hands [drawStaticObject] for every copy it draws, and what `StorefrontStreetTest` scrolls
         * the twelve streets with -- one expression for both, so a copy that comes in from the right is
         * numbered as the one it becomes when the wrap moves on.
         */
        internal fun copyOf(tileFractionX: Float, geom: GroundGeometry, tileIndex: Int): Int =
            representativeCopy(tileFractionX, geom) + tileIndex
    }

    /**
     * The two accent colours tinted at run time, belonging to no sprite: the parasol's pole (a
     * `drawRect`) and the penguin's belly (a mask). Every other accent is its sprite's own colour, and a
     * tint over finished art would compound a second one (`SpriteTintClassTest`).
     */
    private val parasolPoleColor = 0xFFEFE0CE.toInt()
    private val penguinBellyColor = PENGUIN_BELLY_COLOR

    // Road (drawn under any cars the theme has); its asphalt is [ROAD_COLOR_DAY]/[ROAD_COLOR_NIGHT]
    private val roadEdgeColor = 0xFF3D3A33.toInt()
    private val roadLineColor = 0xFFF3E6D0.toInt()

    /**
     * Advances the traffic and keeps its membership in step with the hour: the day and night
     * densities crossfade on the scene's own [dayBlend] (`CarSelection.densityAt`), so at dusk the
     * road empties car by car, each finishing its pass, since only a car off the visible span is
     * flipped. Nothing allocated: each runtime compares the count with its stored rank.
     */
    fun update(deltaSeconds: Float, dayBlend: Float = 1f) {
        val targetCount = CarSelection.countFor(
            CarSelection.densityAt(customization.cars.density, customization.carsNightDensity, dayBlend),
            layout.cars.size,
        )
        for (carIndex in carRuntimes.indices) {
            val c = carRuntimes[carIndex]
            // Every runtime advances, active or retired. A retired slot that stopped ticking
            // would re-enter at an arbitrary phase against the cars that kept driving, and the
            // even spacing the generator laid out would be gone the first time a count went up.
            c.progress += deltaSeconds * c.spec.speedFraction
            // Wrap by subtracting the span, never by assigning a fixed value: subtracting keeps the
            // head start a car has over the one behind it, so a queue laid out evenly at generation
            // stays evenly spaced indefinitely instead of closing into a pack on its first lap.
            if (c.progress > 1.3f) c.progress -= SceneObjectCatalog.CAR_LOOP_SPAN
            // Membership sync: a count change -- a slider, or the dusk crossfade above -- takes
            // effect per car, and only while that car is off the visible span: an addition
            // drives in from its loop entry, a removal finishes the pass it is on. On screen,
            // nothing ever pops.
            c.targetActive = CarSelection.isKept(c.selectionRank, targetCount)
            if (c.active != c.targetActive && CarSelection.offScreen(c.progress)) {
                c.active = c.targetActive
            }
        }
    }

    /**
     * How far off each edge a vehicle starts and finishes, in canvas pixels: the widest vehicle at
     * the nearest lane plus a tenth for its shadow and stroke, so it scales with the viewport (a
     * fixed margin would pop a vehicle at the edge of a tall screen). [LEGACY_EDGE_MARGIN_PX] is the
     * floor; the derived value, about 114 px at 2400, takes over from about 2520 px of height.
     */
    private fun vehicleEdgeMarginPx(screenHeight: Float): Float {
        val widest = FIRE_TRUCK_HALF_WIDTH_UNITS * SceneSpace.FIRE_TRUCK_BASE_SCALE
        val scaled = widest *
            SceneSpace.perspectiveScaleAt(SceneSpace.ROAD_LANE_NEAR_Y_FRACTION) *
            SceneSpace.sceneScale(screenHeight)
        return maxOf(LEGACY_EDGE_MARGIN_PX, scaled * 1.1f)
    }

    /**
     * A soft translucent ground-shadow ellipse beneath an object's base, drawn in the object's own
     * local coordinate space, so it sits exactly at that object's y=0 whatever the caller's scale or
     * translate: one helper for every ground-anchored object, so none reads as floating
     * (`DESIGN_NOTES.md` decision 6). One oval fill.
     */
    private fun drawGroundShadow(canvas: SceneCanvas, halfWidth: Float, halfHeight: Float = 5f) {
        fillPaint.color = 0x2E000000
        canvas.drawOval(-halfWidth, -halfHeight, halfWidth, halfHeight, fillPaint)
    }

    /**
     * Blits a sprite tinted to [tintColor], its pixel (0,0) at [originXUnits]/[originYUnits] in local
     * units. Binds [SpriteScale.SCENE_UNITS], every sprite of this class's convention, in one place; a
     * sprite authored at screen-pixel size goes to [SpriteBlitter.drawTinted] with
     * [SpriteScale.CANVAS_PIXELS], or it renders [SpriteBlitter.SPRITE_PIXELS_PER_UNIT] times too small.
     */
    private fun drawTintedSprite(canvas: SceneCanvas, resId: Int, originXUnits: Float, originYUnits: Float, tintColor: Int) {
        sprites.drawTinted(canvas, resId, originXUnits, originYUnits, SpriteScale.SCENE_UNITS, tintColor)
    }

    /** Same as [drawTintedSprite], but blits the bitmap's own baked-in colors as-is (no
     * `PorterDuffColorFilter` at all, see [TintFilterCache]) -- for sprites like the tree
     * trunk that use a fixed, non-user-customizable color baked directly into the PNG at
     * generation time rather than a white/alpha mask meant to be tinted. */
    private fun drawSprite(canvas: SceneCanvas, resId: Int, originXUnits: Float, originYUnits: Float) {
        sprites.draw(canvas, resId, originXUnits, originYUnits, SpriteScale.SCENE_UNITS)
    }

    /**
     * Same as [drawSprite], under [shade], a neutral grey for how lit it is: fixed art has no tint to
     * interpolate at dusk, and a shade is **not** a tint ([SpriteBlitter.draw],
     * `DESIGN_NOTES.md` decision 36). Used by [drawPalmTree] and [drawFir]'s green; the rest of the fixed art is drawn
     * as authored at every hour, by the same decision.
     */
    private fun drawShadedSprite(canvas: SceneCanvas, resId: Int, originXUnits: Float, originYUnits: Float, shade: Int) {
        sprites.draw(canvas, resId, originXUnits, originYUnits, SpriteScale.SCENE_UNITS, shade = shade)
    }

    /**
     * Same as [drawSprite], faded to [alpha]: the lit lamps laid over their day lenses
     * ([drawVehicleLamps]), fixed art, so the crossfade is an alpha, not a tint.
     */
    private fun drawSpriteFaded(canvas: SceneCanvas, resId: Int, originXUnits: Float, originYUnits: Float, alpha: Int) {
        if (alpha <= 0) return
        sprites.draw(canvas, resId, originXUnits, originYUnits, SpriteScale.SCENE_UNITS, alpha)
    }

    /**
     * A person: the fixed art, then each region's mask multiplied by the colour it is wearing.
     *
     * [slots] is a row of [PeopleLayerTable] -- `[fixed, skin, head, top, bottom]`, `0` where the
     * shape has no such region -- and [colours] is the matching row of colours, read only where the
     * slot is non-zero.
     *
     * **The masks are added, not laid over**: source-over (`a + b(1-a)`) is not linear, so layers
     * [SpriteDetailLevel] halves separately would stop recomposing and halo at every edge (up to 63
     * levels of 255; [SceneCanvas.drawSprite]). Each mask is one quad in the same batch -- one atlas,
     * the additive flag carried in the vertex alpha's sign -- so a five-layer figure is one draw call.
     */
    private fun drawPersonLayers(
        canvas: SceneCanvas,
        slots: IntArray,
        x: Float,
        y: Float,
        colours: IntArray,
    ) {
        drawSprite(canvas, slots[PeopleLayerTable.FIXED], x, y)
        for (region in PeopleLayerTable.SKIN until PeopleLayerTable.SLOTS) {
            val resId = slots[region]
            if (resId == 0) continue
            sprites.drawTintedAdded(canvas, resId, x, y, SpriteScale.SCENE_UNITS, colours[region])
        }
    }

    /**
     * [drawPersonLayers] inside the clip box, in [x]'s units: a person walking out of a window or into
     * it, cut to its pane ([WindowWalk]). **Every layer by the same box**: a mask left whole beside a
     * cut fixed layer would add the shirt's colour over the wall.
     */
    private fun drawPersonLayersClipped(
        canvas: SceneCanvas,
        slots: IntArray,
        x: Float,
        y: Float,
        colours: IntArray,
        clipLeft: Float,
        clipTop: Float,
        clipRight: Float,
        clipBottom: Float,
    ) {
        sprites.drawClipped(
            canvas, slots[PeopleLayerTable.FIXED], x, y, SpriteScale.SCENE_UNITS, SpriteBlitter.UNTINTED,
            additive = false, clipLeft = clipLeft, clipTop = clipTop, clipRight = clipRight, clipBottom = clipBottom,
        )
        for (region in PeopleLayerTable.SKIN until PeopleLayerTable.SLOTS) {
            val resId = slots[region]
            if (resId == 0) continue
            sprites.drawClipped(
                canvas, resId, x, y, SpriteScale.SCENE_UNITS, colours[region],
                additive = true, clipLeft = clipLeft, clipTop = clipTop, clipRight = clipRight, clipBottom = clipBottom,
            )
        }
    }

    /**
     * Scratch for one figure's colours, by [PeopleLayerTable]'s slot numbers (slot 0, the fixed art,
     * unread): a field, so the per-figure path, run every frame, allocates nothing. **One
     * figure at a time**: [coloursFor] overwrites it, so each result is drawn before the next asks.
     */
    private val personColours = IntArray(PeopleLayerTable.SLOTS)

    /**
     * Fills [personColours] for one figure and hands it back. [skinIndex] comes already chosen by the
     * system that draws the figure (the street per crossing, [WindowOccupants] per pane,
     * [SeatedOccupants] per seat), each tested: re-deriving it here would replace all three.
     */
    private fun coloursFor(
        address: Int,
        crossing: Int,
        kind: Int,
        season: Int,
        outfit: Int,
        skinIndex: Int,
        headChannel: Int = PeopleColours.CH_HEAD,
    ): IntArray {
        personColours[PeopleLayerTable.SKIN] = PeopleColours.skin(skinIndex)
        personColours[PeopleLayerTable.HEAD] = PeopleColours.head(
            themeId.hashCode(), address, crossing, PeopleLayerTable.HEAD_WORN[kind][season], headChannel,
        )
        personColours[PeopleLayerTable.TOP] = PeopleColours.top(outfit)
        personColours[PeopleLayerTable.BOTTOM] = PeopleColours.bottom(outfit)
        return personColours
    }

    /**
     * An object's horizontal anchor: `tileFractionX` across one tile of the shared [GroundGeometry],
     * which scrolls by the hills' own values (`PaperRenderer.drawHillLayers`), so objects never
     * desync from their ground. One representative copy: [draw] finds the visible ones
     * ([firstVisibleTileOffset]). Y is [SceneSpace.groundYFraction]'s. A bare `Float`, so nothing is
     * boxed on this per-frame path: a boxed value is an allocation.
     */
    private fun anchorX(spec: StaticSceneObject, geom: GroundGeometry): Float = anchorXOf(spec.tileFractionX, geom)

    /**
     * Where this frame's canopies are, in screen pixels, for the falling leaves `PaperRenderer` draws:
     * filled here, the only place that knows a tree's depth, ground line, scale and copy. Parallel
     * arrays and a count, refilled every frame without allocating.
     */
    val leafSourceX = FloatArray(MAX_LEAF_SOURCES)
    val leafSourceY = FloatArray(MAX_LEAF_SOURCES)
    val leafSourceHalfWidth = FloatArray(MAX_LEAF_SOURCES)

    /**
     * The ground line of each crown's tree, where its leaves land: at the foot of their own tree,
     * never on one line for the whole screen that would carry a hillside tree's leaves onto the road.
     */
    val leafSourceGroundY = FloatArray(MAX_LEAF_SOURCES)

    /**
     * Each crown's tree, as [leafCopyId] of its index in [staticRuntimes] and its copy: a falling leaf
     * belongs to its tree, not to a slot of these arrays, whose indices shuffle as trees cross the
     * screen's edges. A `Long`: a theme's 2 149th object is past `Int.MAX_VALUE`, its leaves far sooner.
     */
    val leafSourceId = LongArray(MAX_LEAF_SOURCES)

    /** How many leaves each recorded crown sheds: its drawn half-width over [LEAF_SOURCE_PX_PER_LEAF]. */
    val leafSourceLeafCount = IntArray(MAX_LEAF_SOURCES)
    var leafSourceCount = 0
        private set

    /**
     * Records the crown of a tree drawn at [x] for the falling leaves, if it has one: a leafy tree's or a palm's, not a
     * fir's ([standsAsFir]) and not a crown *Halloween* has stripped to bare limbs or dead fronds ([shedsLeaves]).
     */
    private fun recordLeafSource(r: StaticRuntime, id: Long, x: Float, groundY: Float, scale: Float) {
        if (shedsLeaves(r)) recordLeafSource(variantFor(r.spec), id, x, groundY, scale)
    }

    /**
     * Whether this tree is drawn with a crown of leaves to shed: not a fir ([drawFir]), and not under
     * *Halloween*, when the leafy tree is bare limbs and the palm dead fronds.
     */
    private fun shedsLeaves(r: StaticRuntime): Boolean = when (variantFor(r.spec)) {
        SceneSpace.SceneVariant.TREE -> !customization.halloweenEnabled && !standsAsFir(r)
        SceneSpace.SceneVariant.PALM_TREE -> !customization.halloweenEnabled
        else -> false
    }

    private fun recordLeafSource(variant: SceneSpace.SceneVariant, id: Long, x: Float, groundY: Float, scale: Float) {
        if (leafSourceCount >= MAX_LEAF_SOURCES) return
        // The crown as each is actually blitted, derived from the call sites and the sprites, so a
        // change to either moves the leaves with it.
        val centreUnits: Float
        val halfHeightUnits: Float
        val halfWidthUnits: Float
        // How far the crown's centre sits from the trunk: the palm's leans.
        val centreXUnits: Float
        when (variant) {
            // `tree_canopy`'s 101x66 u of content, blitted at (-50,-80) inside the -38 lift: x -50..51,
            // y -118..-52, so centre -85, half-height 33, half-width 51 (the wider side).
            SceneSpace.SceneVariant.TREE -> {
                centreUnits = -85f; halfHeightUnits = 33f; halfWidthUnits = 51f; centreXUnits = 0f
            }
            // A 56x48-unit canvas (drawing and transparent guard) blitted at (-21,-82): x -21..35,
            // y -82..-34, so centre (7,-58), half-height 24, half-width 28 ([PalmSpriteLayout]).
            SceneSpace.SceneVariant.PALM_TREE -> {
                centreUnits = PalmSpriteLayout.CROWN_CENTRE_Y
                halfHeightUnits = PalmSpriteLayout.CROWN_HALF_HEIGHT
                halfWidthUnits = PalmSpriteLayout.CROWN_HALF_WIDTH
                centreXUnits = PalmSpriteLayout.CROWN_CENTRE_X
            }
            else -> return
        }
        leafSourceX[leafSourceCount] = x + centreXUnits * scale
        // A leaf detaches below the crown's *bottom edge*, never inside the canopy (a dark blot on the
        // foliage): four units for the canvas's outline slack, eight pixels for the unscaled rotated
        // oval, so no first frame touches the crown's box (FallingLeafContinuityTest).
        leafSourceY[leafSourceCount] = groundY + (centreUnits + halfHeightUnits + 4f) * scale + 8f
        leafSourceHalfWidth[leafSourceCount] = halfWidthUnits * scale
        leafSourceGroundY[leafSourceCount] = groundY
        leafSourceId[leafSourceCount] = id
        // Leaves proportional to the crown's drawn size, so the total tracks how much tree is on
        // screen; constant while it is there (scale is depth, not scroll), so nothing pops in a swipe.
        leafSourceLeafCount[leafSourceCount] =
            (halfWidthUnits * scale / LEAF_SOURCE_PX_PER_LEAF).toInt().coerceIn(3, 13)
        leafSourceCount++
    }

    /**
     * Wildflowers on the open ground, drawn before anything that stands on it ([groundFlowerSprite]),
     * on the ground line and perspective of everything else. Placed by a fixed hash of the clump index:
     * nothing allocated, the same scatter every frame. Stratified across the width (a slice each,
     * jittered inside it; a uniform draw clusters into bald patches), free in depth. **Across the
     * whole tile, [FLOWER_CLUMP_COUNT] a screen** ([scatterCountOnTile]): the street is two screens
     * wide, and one screen's scatter would leave the meadow bare half of the scroll.
     */
    private fun drawGroundFlowers(canvas: SceneCanvas, geom: GroundGeometry, screenWidth: Float, screenHeight: Float) {
        if (!customization.flowersEnabled) return
        // From [groundFlowerSprite], as the gallery card, so the two cannot disagree.
        val dry = groundFlowerSprite(customization) == R.drawable.ground_flowers_dry
        val sceneScale = SceneSpace.sceneScale(screenHeight)
        val clumps = scatterCountOnTile(FLOWER_CLUMP_COUNT, geom.tileWidth, screenWidth)
        for (i in 0 until clumps) {
            val h = (i * 2654435761L.toInt()) xor (i shl 7)
            val jitterX = ((h ushr 3) and 0xFF) / 255f
            val jitterDepth = ((h ushr 13) and 0xFF) / 255f
            // **Stratified across the width, free in depth**: banding depth by the clump index too
            // would correlate depth with x and lay every clump on one straight diagonal.
            val depth = FLOWER_DEPTH_MIN + (FLOWER_DEPTH_MAX - FLOWER_DEPTH_MIN) * jitterDepth
            val groundY = screenHeight * SceneSpace.groundYFraction(depth)
            val scale = SceneSpace.scaleForHeight(FLOWER_METRES_TALL, FLOWER_SPRITE_UNITS_TALL) *
                SceneSpace.depthScale(depth) * sceneScale
            val slice = scatterSlice(screenWidth, FLOWER_CLUMP_COUNT)
            val baseX = (i + jitterX) * slice
            val tile = if (geom.tileWidth > 0f) geom.tileWidth else screenWidth
            var x = (baseX + geom.shiftXWrapped) % tile
            // Walked from the copy a tile to the left whenever that copy can still reach the screen: a thing laid in the
            // tile's last slice straddles the left edge through it.
            if (x > -slice) x -= tile
            while (x < screenWidth + slice) {
                if (x > -slice) {
                    canvas.save()
                    canvas.translate(x, groundY)
                    canvas.scale(scale, scale)
                    // **Two literal blits**, not one of a chosen resource: `tools/assets` checks a
                    // call site's scale, tint and origin by its `R.drawable.<name>` literal, and the
                    // pair's shared (-18, -12) is what lets the two be swapped.
                    if (dry) {
                        drawSprite(canvas, R.drawable.ground_flowers_dry, -18f, -12f)
                    } else {
                        drawSprite(canvas, R.drawable.ground_flowers_bloom, -18f, -12f)
                    }
                    canvas.restore()
                }
                x += tile
            }
        }
    }

    /**
     * Drifts of snow and heaps of leaves: [drawGroundFlowers]'s scatter, salted so a drift and a
     * clump never coincide. The count is the slider truncated ([pileCount]): 0% runs no loop, 100% is
     * [PILE_MAX_COUNT]. Each is gated on its own season, not a switch of its own: snow on a summer
     * lawn and leaves under a snow-capped tree are both wrong.
     */
    private fun drawGroundPiles(canvas: SceneCanvas, geom: GroundGeometry, screenWidth: Float, screenHeight: Float) {
        if (customization.winterColorsEnabled) {
            scatterPiles(canvas, geom, screenWidth, screenHeight, customization.snowPiles, R.drawable.snow_pile, PILE_SALT_SNOW)
        }
        if (customization.fallColorsEnabled) {
            scatterPiles(canvas, geom, screenWidth, screenHeight, customization.leafPiles, R.drawable.leaf_pile, PILE_SALT_LEAF)
        }
    }

    private fun scatterPiles(
        canvas: SceneCanvas,
        geom: GroundGeometry,
        screenWidth: Float,
        screenHeight: Float,
        density: Float,
        drawable: Int,
        salt: Int,
    ) {
        val count = pileCount(density)
        if (count <= 0) return
        val sceneScale = SceneSpace.sceneScale(screenHeight)
        // [count] a screen, across the whole tile, as the flowers ([drawGroundFlowers]).
        val piles = scatterCountOnTile(count, geom.tileWidth, screenWidth)
        for (i in 0 until piles) {
            val h = ((i + salt) * 2654435761L.toInt()) xor ((i + salt) shl 7)
            val jitterX = ((h ushr 3) and 0xFF) / 255f
            val jitterDepth = ((h ushr 13) and 0xFF) / 255f
            val depth = FLOWER_DEPTH_MIN + (FLOWER_DEPTH_MAX - FLOWER_DEPTH_MIN) * jitterDepth
            val groundY = screenHeight * SceneSpace.groundYFraction(depth)
            val scale = SceneSpace.scaleForHeight(PILE_METRES_TALL, PILE_SPRITE_UNITS_TALL) *
                SceneSpace.depthScale(depth) * sceneScale
            // A slice of a screen each whatever the count, so the slider thins every screen evenly.
            val slice = scatterSlice(screenWidth, count)
            val baseX = (i + jitterX) * slice
            val tile = if (geom.tileWidth > 0f) geom.tileWidth else screenWidth
            var x = (baseX + geom.shiftXWrapped) % tile
            // Walked from the copy a tile to the left whenever that copy can still reach the screen: a thing laid in the
            // tile's last slice straddles the left edge through it.
            if (x > -slice) x -= tile
            while (x < screenWidth + slice) {
                if (x > -slice) {
                    canvas.save()
                    canvas.translate(x, groundY)
                    canvas.scale(scale, scale)
                    // The pile canvases are 36x7 units and every lobe stands on their bottom edge,
                    // so this origin puts a drift on the ground line the same way the flowers'
                    // -18,-12 does for their own 36x12.
                    drawSprite(canvas, drawable, -18f, -7f)
                    canvas.restore()
                }
                x += tile
            }
        }
    }

    /**
     * Draws the ground flowers and piles, every static object, the road, the people and the cars
     * (the pavement is behind the carriageway: see the call to [drawPeople]). Every copy of an object
     * that lands on screen is drawn, or the wrap seam shows a gap: scale and cull extent once per
     * object, the copies from [firstVisibleTileOffset] to [tileOffsetLimit], each decided by
     * [isHorizontallyVisible].
     *
     * [hour24] is `DayPhase.hour24`, the hour that moved the sun this frame, never a clock of this
     * class's own: the opening hours' [frameHour] and the people's colour clock ([clockSeconds]).
     * Noon by default, for the tests that draw a frame directly.
     */
    fun draw(canvas: SceneCanvas, geom: GroundGeometry, dayBlend: Float, elapsedSeconds: SceneTime, screenWidth: Float, screenHeight: Float, hour24: Float = 12f) {
        // The people's colours are dealt per crossing off this clock, not `elapsedSeconds`, which
        // restarts with the process and would deal the same hand every time ([PeopleColours]).
        clockSeconds = PeopleColours.clockSeconds(hour24)
        frameHour = hour24
        advanceDoorways(elapsedSeconds)
        leafSourceCount = 0
        drawGroundFlowers(canvas, geom, screenWidth, screenHeight)
        drawGroundPiles(canvas, geom, screenWidth, screenHeight)
        // Already depth-sorted. The index is each object's identity for its leaves; an indexed loop,
        // so the hot path allocates no iterator.
        for (runtimeIndex in staticRuntimes.indices) {
            val r = staticRuntimes[runtimeIndex]
            val groundY = screenHeight * SceneSpace.groundYFraction(r.drawDepth)
            // Properties of the object, once here rather than per copy, at its drawn depth.
            val effectiveScale = effectiveScaleFor(r.spec, r.drawDepth, screenHeight)
            val halfWidth = MAX_OBJECT_HALF_WIDTH_UNITS * effectiveScale
            val x = anchorX(r.spec, geom)
            // Each copy drawn is numbered ([copyOf]): a place draws the building the street deals that copy, and a
            // tree sheds that copy's leaves.

            if (geom.tileWidth <= 0f) {
                // No tiling period before the surface is sized (tileWidth 0): draw the object once
                // if visible. A correctness guard: the enumeration below divides by tileWidth.
                if (isHorizontallyVisible(x, halfWidth, screenWidth)) {
                    drawStaticObject(canvas, r, x, groundY, effectiveScale, dayBlend, elapsedSeconds, copyOf(r.spec.tileFractionX, geom, 0))
                    recordLeafSource(r, leafCopyId(runtimeIndex, copyOf(r.spec.tileFractionX, geom, 0)), x, groundY, effectiveScale)
                }
                continue
            }

            // Each copy's x is recomputed from its tile index rather than accumulated by adding
            // tileWidth, which would drift by a few ULP a step.
            val firstTile = firstVisibleTileOffset(x, halfWidth, geom.tileWidth)
            val tileLimit = tileOffsetLimit(x, halfWidth, geom.tileWidth, screenWidth)
            for (tileIndex in firstTile until tileLimit) {
                val copyX = x + tileIndex * geom.tileWidth
                if (isHorizontallyVisible(copyX, halfWidth, screenWidth)) {
                    drawStaticObject(canvas, r, copyX, groundY, effectiveScale, dayBlend, elapsedSeconds, copyOf(r.spec.tileFractionX, geom, tileIndex))
                    recordLeafSource(r, leafCopyId(runtimeIndex, copyOf(r.spec.tileFractionX, geom, tileIndex)), x = copyX, groundY = groundY, scale = effectiveScale)
                }
            }
        }

        drawRoad(canvas, dayBlend, screenWidth, screenHeight, geom.shiftXWrapped, geom.tileWidth)

        // **People before traffic: every pedestrian is farther away than every car.** The pavement
        // rows (0.795, 0.807, plus at most [PedestrianPopulation.MEMBER_ROW_SPREAD]) stand behind the
        // lanes (0.834, 0.862, where `SceneObjectCatalog` snaps every stored lane,
        // `PersistedThemeGeometryTest`), so no walker is in front of a vehicle (`PeopleTrafficDepthTest`).
        // Drawn after the cars, the deepest figure, below a far-lane roof line, would paint its shoes
        // across the roof. `drawPeople` draws the walkers only, so this order decides nothing else.
        drawPeople(canvas, geom, screenWidth, screenHeight, elapsedSeconds, dayBlend)

        for (carIndex in carRuntimes.indices) {
            val c = carRuntimes[carIndex]
            if (!c.active) continue
            // The span CarSelection.offScreen reads: outside it a car is not drawn, so may change.
            if (c.progress < CarSelection.ON_SCREEN_MIN_PROGRESS ||
                c.progress > CarSelection.ON_SCREEN_MAX_PROGRESS
            ) {
                continue
            }
            drawCar(canvas, c, screenWidth, screenHeight, dayBlend)
        }
    }

    /**
     * Every building that keeps hours, its doorway moved on to this frame ([WindowWalk.Doorway]),
     * seen or not, so a building scrolled into view is where its clock says. Nothing allocated.
     */
    private fun advanceDoorways(elapsed: SceneTime) {
        // A cut (the hour jumped, or the opening hours changed): the doorways settle at once.
        val settle = doorwaysSettle || WindowWalk.hourJumped(doorwayHour, frameHour)
        doorwaysSettle = false
        doorwayHour = frameHour
        for (index in staticRuntimes.indices) {
            val r = staticRuntimes[index]
            val doorway = r.doorway ?: continue
            val roster = r.roster ?: continue
            val layout = r.windowLayout ?: continue
            val variant = variantFor(r.spec)
            doorway.advance(
                roster, layout, elapsed,
                present = customization.peopleFor(variant, roster.people, frameHour),
                open = customization.opennessFor(variant, frameHour) >= 1f,
                settle = settle,
            )
        }
        // The six buildings among the houses too, whether a place shows them or not.
        val buildings = storefronts ?: return
        for (index in buildings.indices) {
            val r = buildings[index]
            val doorway = r.doorway ?: continue
            val roster = r.roster ?: continue
            val layout = r.windowLayout ?: continue
            val storefront = Storefront.ALL[index]
            doorway.advance(
                roster, layout, elapsed,
                present = customization.peopleFor(storefront, roster.people, frameHour),
                open = customization.opennessFor(storefront, frameHour) >= 1f,
                settle = settle,
            )
        }
    }

    /** The hour the doorways last moved on at ([advanceDoorways]); NaN before the first frame. */
    private var doorwayHour = Float.NaN

    /** Set when the opening hours change: the next frame is a cut for the doorways ([advanceDoorways]). */
    private var doorwaysSettle = false

    private val personKinds = arrayOf("man", "woman", "boy", "girl")

    /**
     * The season column the outdoor artwork reads: winter clothing where the theme asks for it.
     * Only the street and the cars ask: the window busts have one season, because the hat belongs to
     * the street, not to the room behind the pane.
     */
    private fun outdoorSeasonIndex(): Int = if (customization.winterColorsEnabled) 1 else 0

    /** The crowd [populationAt] last built, the groups it holds and the street it was built for. */
    private var crowd: List<Pedestrian> = emptyList()
    private var crowdGroups = -1
    private var crowdSeed = 0

    /**
     * The street's crowd at [density] ([PedestrianPopulation.build]), built again only when the groups
     * on the street ([PedestrianPopulation.presentMask]) or the street change, since building allocates.
     */
    private fun populationAt(seed: Int, density: Float): List<Pedestrian> {
        val groups = PedestrianPopulation.presentMask(density)
        if (groups != crowdGroups || seed != crowdSeed) {
            crowd = PedestrianPopulation.build(
                seed = seed,
                density = density,
                nearRowYFraction = SceneSpace.PAVEMENT_NEAR_Y_FRACTION,
                farRowYFraction = SceneSpace.PAVEMENT_FAR_Y_FRACTION,
            )
            crowdGroups = groups
            crowdSeed = seed
        }
        return crowd
    }

    /**
     * The pedestrians, walking along the ground: the walk advances a position on the tiling ground,
     * and [GroundGeometry] scrolls it with everything standing there, so a swipe moves the people with
     * the village and the walk is not a treadmill. Tiled as static objects are, or a walker at the seam
     * pops.
     */
    private fun drawPeople(
        canvas: SceneCanvas,
        geom: GroundGeometry,
        screenWidth: Float,
        screenHeight: Float,
        elapsedSeconds: SceneTime,
        dayBlend: Float,
    ) {
        if (geom.tileWidth <= 0f) return
        val config = customization.people
        if (!config.visible) return
        val seasonIdx = outdoorSeasonIndex()
        val sceneScale = SceneSpace.sceneScale(screenHeight)
        // Density thins the pool by a stable per-group threshold ([CandidateThreshold]), so lowering it
        // removes particular people and leaves the rest where they were; day and night crossfade on the
        // scene's dayBlend, so the street empties over the dusk.
        val density = PeopleDensity.at(
            dayDensity = config.density,
            nightDensity = customization.peopleNightDensity,
            dayBlend = dayBlend,
        )
        // The population arrives sorted far-to-near by each figure's own baseline
        // ([PedestrianPopulation]), so drawing it in order puts the nearest figure on top.
        val population = populationAt(themeId.hashCode(), density)
        // Which addresses are on the street at this density: [PeopleColours.keepingSpread] keeps the
        // tones spread over the walkers actually present, not over the whole pool.
        var presentMask = 0
        for (i in population.indices) {
            val person = population[i]
            presentMask = presentMask or (1 shl (person.groupIndex * PedestrianPopulation.MAX_GROUP_SIZE + person.memberIndex))
        }
        for (i in population.indices) {
            val person = population[i]
            // Row and speed from [SceneSpace]: the near row larger and faster, by the ratio of the two
            // ground lines -- inside the projection, like everything on the ground.
            val rowYFraction = person.rowYFraction
            val nearer = rowYFraction > (SceneSpace.PAVEMENT_NEAR_Y_FRACTION + SceneSpace.PAVEMENT_FAR_Y_FRACTION) / 2f
            val speed = if (nearer) SceneSpace.PEDESTRIAN_SPEED_NEAR else SceneSpace.PEDESTRIAN_SPEED_FAR
            val y = screenHeight * rowYFraction
            val dir = person.direction
            val s = SceneSpace.PERSON_BASE_SCALE * SceneSpace.perspectiveScaleAt(rowYFraction) * sceneScale

            // The walk on the ground: `cycle` is 0..1 over a tile of walking, the start offset spreads them.
            val walk = elapsedSeconds.cycle(speed, person.phase)
            var tileFraction = (person.startFraction + dir * walk) % 1f
            if (tileFraction < 0f) tileFraction += 1f

            var x = geom.shiftXWrapped + tileFraction * geom.tileWidth
            if (x < -geom.tileWidth * 0.5f) x += geom.tileWidth

            // The walk frame is staggered by the figure's own address, not its place in the depth-sorted
            // list, which one walker more or less would shift, re-animating the others.
            val walkStagger = person.groupIndex * PedestrianPopulation.MAX_GROUP_SIZE + person.memberIndex
            val frame = elapsedSeconds.frameIndex(3.2f, walkStagger.toFloat(), 4)
            // The umbrella: the sprite follows the state the walker is *already* in, which moves at the
            // bottom of the loop only when no copy was drawn ([PedestrianCarry]). In the rain everyone who
            // can hold one does; `canHold` reads `PeopleLayerTable.CARRY`'s length.
            val carrying = umbrellaCarrying[walkStagger] && PedestrianCarry.canHold(person.kindIndex)
            val slots = if (carrying) {
                PeopleLayerTable.CARRY[person.kindIndex][seasonIdx][frame]
            } else {
                PeopleLayerTable.WALK[person.kindIndex][seasonIdx][frame]
            }
            // The colours dealt for the crossing the walker is on, held between frames as the umbrella
            // is; the first sight deals at once, or a fresh start would draw everybody in crossing zero's.
            val wantedCrossing =
                PeopleColours.crossingOf(clockSeconds, speed, person.phase, person.startFraction, dir)
            if (personCrossing[walkStagger] == PeopleColours.UNDEALT) {
                personCrossing[walkStagger] = wantedCrossing
                // First sight: a tone of nobody else's, or raising the density would recolour people
                // already there (`PeopleOcclusionTest`). Later deals, off screen, are [dealTone]'s.
                personTone[walkStagger] = PeopleColours.firstSightTone(person)
            }
            val crossing = personCrossing[walkStagger]
            val colours = coloursFor(
                walkStagger, crossing, person.kindIndex, seasonIdx,
                PeopleColours.outfit(themeId.hashCode(), walkStagger, crossing),
                // The tone, rotated by the crossing and held with it ([dealTone]).
                personTone[walkStagger],
            )
            // Cut wide while an umbrella is up or wanted, so `onScreen`, which gates every change made out
            // of sight, covers the canopy: an umbrella never opens where it would show.
            val wantsUmbrella = PedestrianCarry.wantsUmbrella(
                raining = rainingNow,
                canHold = PedestrianCarry.canHold(person.kindIndex),
            )
            val halfWidth = personCullHalfWidthUnits(carrying || wantsUmbrella) * s

            val firstTile = firstVisibleTileOffset(x, halfWidth, geom.tileWidth)
            val tileLimit = tileOffsetLimit(x, halfWidth, geom.tileWidth, screenWidth)
            var onScreen = false
            for (tileIndex in firstTile until tileLimit) {
                val copyX = x + tileIndex * geom.tileWidth
                if (!isHorizontallyVisible(copyX, halfWidth, screenWidth)) continue
                onScreen = true
                canvas.save()
                canvas.translate(copyX, y)
                canvas.scale(dir * s, s)
                // Feet on the ground line, figure centred ([PERSON_ANCHOR_X_UNITS]).
                drawPersonLayers(canvas, slots, PERSON_ANCHOR_X_UNITS, PERSON_ANCHOR_Y_UNITS, colours)
                if (carrying) drawUmbrella(canvas, walkStagger, crossing, person.kindIndex)
                canvas.restore()
            }
            umbrellaCarrying[walkStagger] = PedestrianCarry.nextCarrying(
                current = carrying,
                wanted = wantsUmbrella,
                onScreen = onScreen,
            )
            // The colours change under the same gate, `onScreen` from the cull that just ran: the
            // crossing counter alone is not enough, since a wrap of `tileFraction` hands the figure to
            // the next copy where the scroll may show it (`PedestrianTileWrapTest`).
            if (!onScreen && personCrossing[walkStagger] != wantedCrossing) {
                personCrossing[walkStagger] = wantedCrossing
                personTone[walkStagger] = dealTone(person, walkStagger, wantedCrossing, presentMask)
            }
        }
    }

    /**
     * The tone [person] takes on [crossing]: its stratified base rotated by the crossing
     * ([PeopleColours.toneIndex]), kept from emptying its stratum of a tone
     * ([PeopleColours.keepingSpread]). **Held in [personTone]**, because it depends on what the rest
     * of the stratum wears when it is dealt: the rotation alone loses the spread, every walker
     * crossing on its own clock. Every deal after the first ([PeopleColours.firstSightTone]).
     */
    private fun dealTone(person: Pedestrian, address: Int, crossing: Int, presentMask: Int): Int =
        PeopleColours.keepingSpread(
            PeopleColours.toneIndex(person.skinIndex, themeId.hashCode(), address, crossing),
            address, personTone, presentMask,
        )

    // ---- The umbrella in the hand --------------------------------------------------------------
    /** Set once per frame by [PaperRenderer]: exactly the predicate `drawPrecipitation` rains on. */
    var rainingNow = false

    /**
     * Which walkers have one up, by the population's address: the state the off-screen rule protects,
     * so it survives between frames.
     */
    private val umbrellaCarrying =
        BooleanArray(PedestrianPopulation.GROUP_COUNT * PedestrianPopulation.MAX_GROUP_SIZE)

    /**
     * Which crossing each walker's colours were dealt for, held as [umbrellaCarrying] is;
     * [PeopleColours.UNDEALT] until the figure is first drawn.
     */
    private val personCrossing =
        IntArray(PedestrianPopulation.GROUP_COUNT * PedestrianPopulation.MAX_GROUP_SIZE) {
            PeopleColours.UNDEALT
        }

    /** The tone each walker was dealt with its held crossing ([dealTone]); held under the same rule. */
    private val personTone =
        IntArray(PedestrianPopulation.GROUP_COUNT * PedestrianPopulation.MAX_GROUP_SIZE) {
            PeopleColours.NO_TONE
        }

    private val umbrellaHandlePaint = Paint().apply { color = 0xFF5B4A3E.toInt(); style = Paint.Style.FILL }

    /**
     * Where the top of the handle sits on the walk canvas, per family (as [carryHandX]): 0.7 units
     * above the family's summer ink (the children's reaches 30.0 and 30.333). The adults' 1.0 is the
     * approved artwork, **frozen** (`build_carry_sprites.SHIPPED_ADULT_CROWN`).
     */
    private val carryCrownY = floatArrayOf(1f, 1f, 29.3f, 29.63f)

    /**
     * Drawn inside the walker's own transform (feet at the origin, +x the way it walks), so it mirrors
     * with the figure. The handle is a rectangle from the hand to [carryCrownY]; only the canopy is
     * artwork, as with the parasol's pole.
     */
    private fun drawUmbrella(canvas: SceneCanvas, addr: Int, crossing: Int, kindIndex: Int) {
        val hx = carryHandX[kindIndex] + PERSON_ANCHOR_X_UNITS
        val hy = carryHandY[kindIndex] + PERSON_ANCHOR_Y_UNITS
        // Dealt per crossing like the four colours of the person holding it; the crossing is
        // already in hand.
        val colour = PedestrianCarry.canopyColour(
            CandidateNoise.value(
                PeopleColours.crossingSeed(themeId.hashCode(), crossing),
                addr,
                PedestrianCarry.CH_UMBRELLA_COLOUR,
            ),
        )
        val crownY = PERSON_ANCHOR_Y_UNITS + carryCrownY[kindIndex]
        canvas.drawRect(hx - 1.2f, crownY, hx + 1.2f, hy, umbrellaHandlePaint)
        val k = carryCanopyScale[kindIndex]
        if (k == 1f) {
            drawTintedSprite(canvas, R.drawable.umbrella_canopy, hx - 24f, crownY - 22f, colour)
        } else {
            // One more factor in the blit's transform, not a resample: still a single filtered
            // downscale. The origin is divided back out, being given in the scaled space.
            canvas.save()
            canvas.scale(k, k)
            drawTintedSprite(
                canvas, R.drawable.umbrella_canopy,
                (hx - 24f * k) / k, (crownY - 22f * k) / k, colour,
            )
            canvas.restore()
        }
    }

    /**
     * The two-lane road band, full width at the lanes' height, fixed against the parallax; its dashes
     * advance by [shiftXWrapped], the shift the nearest row scrolls by, so they flow with everything.
     */
    private fun drawRoad(canvas: SceneCanvas, dayBlend: Float, screenWidth: Float, screenHeight: Float, shiftXWrapped: Float, tileWidth: Float) {
        // Density is not consulted: the road is terrain, and a slider does not move terrain.
        if (!drawsRoad(layout, customization)) return

        // The layout's lane span ([roadLaneMinFraction]), never the density-filtered [carRuntimes].
        val minLaneY = roadLaneMinFraction * screenHeight
        val maxLaneY = roadLaneMaxFraction * screenHeight
        // Edges from the lanes plus a shoulder in lane spacings, so a saved lane pair keeps its road in
        // proportion ([SceneSpace.roadTopYFraction] for the canonical lanes). A far-lane roof rises
        // above the far edge: the strip is the road, not a box for the cars.
        val margin = screenHeight * SceneSpace.roadEdgeMarginFraction(roadLaneMinFraction, roadLaneMaxFraction)
        val top = minLaneY - margin
        val bottom = maxLaneY + margin
        val sceneScale = SceneSpace.sceneScale(screenHeight)

        fillPaint.color = roadColor(dayBlend)
        canvas.drawRect(0f, top, screenWidth, bottom, fillPaint)

        strokePaint.style = Paint.Style.STROKE
        strokePaint.color = roadEdgeColor
        strokePaint.strokeWidth = SceneSpace.ROAD_EDGE_STROKE_PX * sceneScale
        canvas.drawLine(0f, top, screenWidth, top, strokePaint)
        canvas.drawLine(0f, bottom, screenWidth, bottom, strokePaint)

        // The dashed centre line, halfway between the layout's lanes, offset by the nearest row's
        // shift. A near car painting over it is right: it stands between the viewer and the road.
        val midY = (minLaneY + maxLaneY) / 2f
        strokePaint.color = roadLineColor
        strokePaint.strokeWidth = SceneSpace.ROAD_CENTRE_LINE_STROKE_PX * sceneScale
        val dashLen = SceneSpace.ROAD_DASH_LENGTH_PX * sceneScale
        val gapLen = SceneSpace.ROAD_DASH_GAP_PX * sceneScale
        val period = dashLen + gapLen
        var offset = shiftXWrapped % period
        if (offset < 0f) offset += period
        var x = -period + offset
        while (x < screenWidth) {
            val segStart = x.coerceAtLeast(0f)
            val segEnd = (x + dashLen).coerceIn(0f, screenWidth)
            if (segEnd > segStart) canvas.drawLine(segStart, midY, segEnd, midY, strokePaint)
            x += period
        }
    }

    /**
     * Paints one tile copy of an object at [x]/[y], at [effectiveScale]. No culling here: [draw] culls
     * with the extent of this same scale, or an object would be culled at one size and drawn at another.
     */
    private fun drawStaticObject(
        canvas: SceneCanvas,
        r: StaticRuntime,
        x: Float,
        y: Float,
        effectiveScale: Float,
        dayBlend: Float,
        elapsed: SceneTime,
        copy: Int,
    ) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(effectiveScale, effectiveScale)

        // A place draws, at its own size, the building the street deals this copy ([StorefrontStreet]),
        // with that building's own runtime ([storefronts]).
        val buildings = storefronts
        if (r.place >= 0 && buildings != null) {
            val storefront = StorefrontStreet.storefrontAt(
                themeSeed, StorefrontStreet.placeIndex(copy, r.place, places.size), streetDeckA, streetDeckB,
            )
            drawNeighbourhoodBuilding(canvas, buildings[storefront.ordinal], elapsed, dayBlend, variantFor(r.spec), storefront)
            canvas.restore()
            return
        }

        // Dispatched on the same variant the scale was derived from, so an object can never be
        // sized as one drawing and painted as another.
        when (variantFor(r.spec)) {
            SceneSpace.SceneVariant.HOUSE_SMALL ->
                if (r.spec.type == SceneObjectType.HOUSE) {
                    drawNeighbourhoodBuilding(canvas, r, elapsed, dayBlend, SceneSpace.SceneVariant.HOUSE_SMALL)
                }
            SceneSpace.SceneVariant.HOUSE_LARGE ->
                drawNeighbourhoodBuilding(canvas, r, elapsed, dayBlend, SceneSpace.SceneVariant.HOUSE_LARGE)
            // Never dispatched: a fir is a state of a TREE ([standsAsFir]), in the variant table only
            // for its height.
            SceneSpace.SceneVariant.FIR -> Unit
            SceneSpace.SceneVariant.TOWER ->
                drawNeighbourhoodBuilding(canvas, r, elapsed, dayBlend, SceneSpace.SceneVariant.TOWER)
            SceneSpace.SceneVariant.RESTAURANT ->
                drawNeighbourhoodBuilding(canvas, r, elapsed, dayBlend, SceneSpace.SceneVariant.RESTAURANT)
            SceneSpace.SceneVariant.BAR ->
                drawNeighbourhoodBuilding(canvas, r, elapsed, dayBlend, SceneSpace.SceneVariant.BAR)
            SceneSpace.SceneVariant.SCHOOL ->
                drawNeighbourhoodBuilding(canvas, r, elapsed, dayBlend, SceneSpace.SceneVariant.SCHOOL)
            SceneSpace.SceneVariant.TREE -> drawTree(canvas, r, elapsed, dayBlend)
            SceneSpace.SceneVariant.PALM_TREE -> drawPalmTree(canvas, r, elapsed, dayBlend)
            SceneSpace.SceneVariant.PARASOL -> drawParasol(canvas, r, elapsed, dayBlend)
            SceneSpace.SceneVariant.SNOWMAN -> drawSnowman(canvas, r, elapsed, dayBlend)
            SceneSpace.SceneVariant.GIFT -> drawGift(canvas, r, dayBlend)
            SceneSpace.SceneVariant.PENGUIN -> drawPenguin(canvas, r, elapsed, dayBlend)
            SceneSpace.SceneVariant.BUNNY -> drawBunny(canvas, r, elapsed, dayBlend)
            SceneSpace.SceneVariant.EASTER_EGG -> drawEasterEgg(canvas, r, dayBlend)
            SceneSpace.SceneVariant.PUMPKIN -> drawPumpkin(canvas, r, dayBlend)
            SceneSpace.SceneVariant.SCARECROW -> drawScarecrow(canvas, r, elapsed, dayBlend)
        }
        canvas.restore()
    }

    /**
     * A string of Christmas lights under a window: a slack two-segment cord between two points of the
     * sill and four bulbs hung off it ([ChristmasDecorations.forEachStringBulb], the gallery card's too),
     * in the tree lights' colours, so a house and its tree agree. Bulbs are discs ([drawBulb]).
     */
    private fun drawWindowLights(canvas: SceneCanvas, r: StaticRuntime, elapsed: SceneTime, x: Float, sillY: Float, width: Float) {
        val left = x
        val right = x + width
        val sag = width * ChristmasDecorations.STRING_SAG
        strokePaint.color = ChristmasDecorations.STRING_CORD_COLOUR
        strokePaint.strokeWidth = ChristmasDecorations.STRING_CORD_WIDTH
        canvas.drawLine(left, sillY, (left + right) / 2f, sillY + sag, strokePaint)
        canvas.drawLine((left + right) / 2f, sillY + sag, right, sillY, strokePaint)
        ChristmasDecorations.forEachStringBulb(x, sillY, width) { bulb, bx, by ->
            val blink = sillBlink.at(r, r.idleSeed, elapsed, bulb)
            drawBulb(canvas, bx, by, CHRISTMAS_LIGHT_BULB_RADIUS_UNITS, christmasLightColors[bulb % christmasLightColors.size], (255 * blink).toInt().coerceIn(70, 255))
        }
    }

    private val sillBlink = SillBlink()

    /**
     * The Christmas decorations of a building (`DESIGN_NOTES.md` decision 67), where
     * [ChristmasDecorations] hangs them, as on the gallery card: bulbs along the top piece's roofline
     * every 6 units ([BuildingPiece.roofX]), the wreath on the front door, a star on a tower's top. Lit
     * open or shut. **Every part 2 px or more where the building stands farthest on the BV6600**: a
     * piece unit is 0.81 px at the farthest house, 0.89 at the farthest place and 0.55 at the farthest
     * tower, whose decorations are half as large again ([decorationScale]).
     */
    private fun drawDecorations(
        canvas: SceneCanvas,
        r: StaticRuntime,
        elapsed: SceneTime,
        deal: NeighbourhoodComposer.Deal,
        kind: WindowBuildingKind,
    ) {
        ChristmasDecorations.forEachRoofBulb(deal, kind) { i, x, y, radius ->
            val phase = r.idleSeed * 3.1f + i * 1.7f
            val blink = 0.55f + 0.45f * elapsed.sinAt(1.6f, phase)
            drawBulb(canvas, x, y, radius, christmasLightColors[i % christmasLightColors.size], (255 * blink).toInt().coerceIn(70, 255))
        }
        ChristmasDecorations.star(deal, kind) { cx, cy, outer, inner -> drawStar(canvas, cx, cy, outer, inner) }
        ChristmasDecorations.forEachWreath(deal, kind) { cx, cy, k -> drawWreath(canvas, cx, cy, k) }
    }

    /**
     * The Halloween decorations of a house or a shop ([HalloweenDecorations], `DESIGN_NOTES.md` decisions
     * 83 and 85), in the building's piece units: a cobweb in the roof corner its drawing chooses (the right
     * one mirrored), a mask tinted by [HalloweenDecorations.webTintAt] from the walls' day and night
     * colours at [dayBlend]; and a jack-o'-lantern by the front door, fixed art, its candle's alpha
     * [night]. Every band 2 px or more where they stand farthest on the BV6600 ([HalloweenDecorationTable]).
     */
    private fun drawHalloweenDecorations(
        canvas: SceneCanvas,
        deal: NeighbourhoodComposer.Deal,
        night: Float,
        dayWall: Int,
        nightWall: Int,
        dayBlend: Float,
    ) {
        val web = HalloweenDecorations.webTintAt(dayWall, nightWall, dayBlend)
        HalloweenDecorations.forEachCobweb(deal) { x, y, mirror ->
            canvas.save()
            canvas.translate(x, y)
            if (mirror < 0f) canvas.scale(-1f, 1f)
            sprites.drawTinted(
                canvas, HalloweenDecorationTable.WEB_RES, HalloweenDecorationTable.WEB_X, HalloweenDecorationTable.WEB_Y,
                SpriteScale.SCENE_UNITS, web,
            )
            canvas.restore()
        }
        val lit = (night * 255f).toInt().coerceIn(0, 255)
        HalloweenDecorations.forEachDoorPumpkin(deal) { x, y ->
            drawSprite(canvas, HalloweenDecorationTable.PUMPKIN_RES, x + HalloweenDecorationTable.PUMPKIN_X, y + HalloweenDecorationTable.PUMPKIN_Y)
            if (lit > 0) {
                sprites.draw(
                    canvas, HalloweenDecorationTable.PUMPKIN_LIGHT_RES,
                    x + HalloweenDecorationTable.PUMPKIN_LIGHT_X, y + HalloweenDecorationTable.PUMPKIN_LIGHT_Y,
                    SpriteScale.SCENE_UNITS, lit,
                )
            }
        }
    }

    /**
     * The wall colour of [spec] at [dayBlend]: one of the six among the houses in its group's colours,
     * any other in the pair of what it is drawn as.
     */
    private fun wallAt(spec: StaticSceneObject, variant: SceneSpace.SceneVariant, storefront: Storefront?, dayBlend: Float): Int =
        if (storefront != null) customization.storefrontWall(spec, storefront, dayBlend) else customization.wallColourFor(spec, variant, dayBlend)

    /** How much larger a decoration, a blind's rail and a shutter's slats are drawn on a building of [kind]: see [drawDecorations]. */
    private fun decorationScale(kind: WindowBuildingKind): Float = ChristmasDecorations.scaleFor(kind)

    /**
     * The wreath on a front door at ([cx], [cy]), [k] times its size: one blit of `deco_wreath`, 6
     * vertices, where shapes would be 36 and a street of doors would overflow the batch (`GlDrawCallTest`).
     */
    private fun drawWreath(canvas: SceneCanvas, cx: Float, cy: Float, k: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        if (k != 1f) canvas.scale(k, k)
        drawSprite(canvas, R.drawable.deco_wreath, -WREATH_HALF, -WREATH_HALF)
        canvas.restore()
    }

    /**
     * A five-pointed star at ([cx], [cy]) in the fir's gold: a pentagon, then five triangles, since the GL
     * path fans a shape from its first vertex and a star is not convex from any of them.
     */
    private fun drawStar(canvas: SceneCanvas, cx: Float, cy: Float, outer: Float, inner: Float) {
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) outer else inner
            starX[i] = cx + radius * STAR_COS[i]
            starY[i] = cy + radius * STAR_SIN[i]
        }
        fillPaint.color = STAR_GOLD
        decorationShape.moveTo(starX[1], starY[1])
        var v = 3
        while (v < 10) {
            decorationShape.lineTo(starX[v], starY[v])
            v += 2
        }
        decorationShape.close()
        canvas.drawShape(decorationShape, fillPaint)
        var point = 0
        while (point < 10) {
            decorationShape.moveTo(starX[(point + 9) % 10], starY[(point + 9) % 10])
            decorationShape.lineTo(starX[point], starY[point])
            decorationShape.lineTo(starX[point + 1], starY[point + 1])
            decorationShape.close()
            canvas.drawShape(decorationShape, fillPaint)
            point += 2
        }
    }

    /**
     * A shut building (`DESIGN_NOTES.md` decision 69), [shut] 0..1 on the glass's two seconds
     * ([WindowWalk.Doorway.glassAt]): a blind coming down to 70 % of every window a person may stand at (a
     * tower's hall only), and the shutter rolling down over the front door, by day and by night. **The
     * shutter is the door's own card** ([BuildingDoor.outlineX]) over the door's offset shadow, so nothing
     * of the door shows and nothing of the shutter stands outside it; a tower's glass door has no shadow.
     */
    private fun drawShut(canvas: SceneCanvas, deal: NeighbourhoodComposer.Deal, kind: WindowBuildingKind, shut: Float, night: Float) {
        if (shut <= 0f) return
        val k = decorationScale(kind)
        val blind = SceneColour.blendArgb(BLIND_DAY, BLIND_NIGHT, night)
        val rail = SceneColour.blendArgb(BLIND_RAIL_DAY, BLIND_RAIL_NIGHT, night)
        for (index in 0 until deal.size) {
            val placed = deal[index]
            val windows = placed.piece.windows
            for (i in windows.indices) {
                val w = windows[i]
                val h = w.h * BLIND_SHARE * shut
                if (h <= 0f) continue
                val top = placed.baseY + w.y + LIT_PANE_INSET
                val left = w.x + LIT_PANE_INSET
                val right = w.x + w.w - LIT_PANE_INSET
                fillPaint.color = blind
                canvas.drawRect(left, top, right, top + h, fillPaint)
                fillPaint.color = rail
                canvas.drawRect(left, top + h - minOf(BLIND_RAIL * k, h), right, top + h, fillPaint)
            }
        }
        val ground = deal[0]
        val door = ground.piece.door ?: return
        val under = SceneColour.blendArgb(SHUTTER_UNDER_DAY, SHUTTER_UNDER_NIGHT, night)
        val face = SceneColour.blendArgb(SHUTTER_DAY, SHUTTER_NIGHT, night)
        val slat = SceneColour.blendArgb(SHUTTER_SLAT_DAY, SHUTTER_SLAT_NIGHT, night)
        DoorShutter.forEachBand(door, ground.baseY, shut, k) { paper, bandTop, bandBottom, dx, dy ->
            fillPaint.color = when (paper) {
                DoorShutter.Paper.UNDER -> under
                DoorShutter.Paper.FACE -> face
                DoorShutter.Paper.SLAT -> slat
            }
            drawDoorBand(canvas, door, ground.baseY, bandTop, bandBottom, dx, dy)
        }
    }

    /** [door]'s band [bandTop] .. [bandBottom] moved by ([dx], [dy]) ([DoorShutter.band]), filled in [fillPaint]. */
    private fun drawDoorBand(canvas: SceneCanvas, door: BuildingDoor, baseY: Float, bandTop: Float, bandBottom: Float, dx: Float, dy: Float) {
        val n = DoorShutter.band(door, baseY, bandTop, bandBottom, dx, dy, bandX, bandY, scratchX, scratchY)
        if (n == 0) return
        decorationShape.moveTo(bandX[0], bandY[0])
        for (i in 1 until n) decorationShape.lineTo(bandX[i], bandY[i])
        decorationShape.close()
        canvas.drawShape(decorationShape, fillPaint)
    }

    /** Reused by the star, the shutter and its bands: one shape, rewritten for each. */
    private val decorationShape = SceneShape(16)
    private val starX = FloatArray(10)
    private val starY = FloatArray(10)
    private val bandX = FloatArray(32)
    private val bandY = FloatArray(32)
    private val scratchX = FloatArray(32)
    private val scratchY = FloatArray(32)

    /**
     * A building of the neighbourhood: the pieces [NeighbourhoodTable] deals for this instance (the
     * silhouette the street dealt, [SilhouetteDeal]), stacked, tinted from the category's colour, with
     * the behaviours hung on the coordinates the pieces declare. One function for every family, so its
     * rules are stated once: snow is a layer, a business's glass keeps hours and a house's never does,
     * a bust stands at a window. Parts come in the order the generator gives them (`Piece.ordered`).
     */
    private fun drawNeighbourhoodBuilding(
        canvas: SceneCanvas,
        r: StaticRuntime,
        elapsed: SceneTime,
        dayBlend: Float,
        variant: SceneSpace.SceneVariant,
        storefront: Storefront? = null,
    ) {
        // The place's row of the size table, and what stands in it: its own family, or the building
        // the street deals a place among the houses ([r] that building's runtime).
        val placeFamily = NeighbourhoodTable.FAMILIES[variant] ?: return
        val family = storefront?.family ?: placeFamily
        val spec = r.spec
        // Every wall surface descends from this one user colour; the pieces carry roofs and cornices as
        // weights in their wall mask, so nothing here invents a colour ([NeighbourhoodTable]).
        val wallColor = wallAt(spec, variant, storefront, dayBlend)
        val night = (1f - dayBlend).coerceIn(0f, 1f)
        // A house lights half its windows at night ([WindowRoster]); a business lights them while open:
        // `businessOpenness` is its group's hours, eased over the doorway's two seconds
        // ([WindowWalk.Doorway.glassAt]), 1 with the switch off. Shut, its glass is dark at night
        // ([businessGlassColor]), its lamp, lit on `glassNight`, is dark, its blinds down ([drawShut]).
        val doorway = r.doorway
        val businessOpenness = if (doorway != null) {
            doorway.glassAt(elapsed.seconds)
        } else if (storefront != null) {
            customization.opennessFor(storefront, frameHour)
        } else {
            customization.opennessFor(variant, frameHour)
        }
        val glassNight = if (family.kind == WindowBuildingKind.HOUSE) night else night * businessOpenness
        val glassColor = if (family.kind == WindowBuildingKind.HOUSE) windowGlassColor(glassNight) else businessGlassColor(night, businessOpenness)
        // The variant says how tall the family's declared height is on screen: the size table, never a per-asset constant.
        // **The place's family, not the building's**: every piece is one metre a unit (`core.M_PER_UNIT`),
        // so any of the six stands at its own height in any place.
        val scale = variant.spriteUnitsTall / placeFamily.unitsTall
        val deal = neighbourhoodDeal
        if (storefront != null) dealStorefront(family, storefront, spec, deal) else NeighbourhoodComposer.deal(family, spec, deal)
        // Who is at the windows now, and in a house which are lit, once for the building's frame.
        val roster = r.roster
        val layout = r.windowLayout
        if (roster != null && layout != null) {
            WindowWalk.figuresAt(roster, layout, elapsed, r.doorway, windowFigures)
        } else {
            windowFigures.count = 0
        }
        // A house at night: dark glass, the lit panes laid over it.
        val houseLights = roster != null && layout != null && family.kind == WindowBuildingKind.HOUSE && glassNight > 0f
        val unlitGlass = if (houseLights) unlitWindowGlassColor(glassNight) else glassColor

        drawGroundShadow(canvas, family.shadowHalf * scale)
        canvas.save()
        canvas.scale(scale, scale)
        val winter = customization.winterColorsEnabled
        // Indexed, not iterated: a `List` iterator would allocate for every part of every building
        // on every frame.
        for (index in 0 until deal.size) {
            val placed = deal[index]
            val piece = placed.piece
            val footY = placed.baseY
            val parts = piece.parts
            for (partIndex in parts.indices) {
                val part = parts[partIndex]
                when (part.role) {
                    PartRole.WALL_MASK ->
                        sprites.drawTintedAdded(canvas, part.res, part.x, footY + part.y, SpriteScale.SCENE_UNITS, wallColor)
                    PartRole.GLASS_MASK -> {
                        sprites.drawTintedAdded(canvas, part.res, part.x, footY + part.y, SpriteScale.SCENE_UNITS, unlitGlass)
                        if (houseLights) drawLitPanes(canvas, roster, layout, placed, unlitGlass, glassColor)
                    }
                    PartRole.FIXED -> drawSprite(canvas, part.res, part.x, footY + part.y)
                    // A layer *on* the roof, cut to its outline, never the roof tinted white, which
                    // the winter palette's own override would be indistinguishable from.
                    PartRole.SNOW -> if (winter) drawSprite(canvas, part.res, part.x, footY + part.y)
                    // The lamp lights with the glass beside it (`DESIGN_NOTES.md` decision 50): on the
                    // same `glassNight`, so a shop's or a tower's stays dark while it is closed.
                    PartRole.LAMP -> drawPorchLight(canvas, x = part.x, y = footY + part.y, lit = glassNight)
                    PartRole.OCCUPANTS -> if (roster != null && layout != null) {
                        val windows = piece.windows
                        for (windowIndex in windows.indices) {
                            val window = windows[windowIndex]
                            drawWindowPeople(
                                canvas, r, roster, layout, family.kind, placed.firstWindow + windowIndex,
                                window.x, footY + window.y, window.w, window.h,
                            )
                        }
                    }
                }
            }
        }
        // Shut: blinds and shutter come down with the glass ([drawShut]); a house is never shut.
        if (family.kind != WindowBuildingKind.HOUSE && businessOpenness < 1f) {
            drawShut(canvas, deal, family.kind, 1f - businessOpenness, night)
        }
        // The Christmas decorations (*Lights on the buildings*), open or shut ([drawDecorations]).
        if (customization.christmasBuildingLightsEnabled) drawDecorations(canvas, r, elapsed, deal, family.kind)
        // Sill strings over the whole building, not one piece, so a tall house is not lit on its
        // ground floor alone ([ChristmasDecorations.forEachSillString], the gallery card's too).
        if (customization.christmasBuildingLightsEnabled) {
            ChristmasDecorations.forEachSillString(deal, r.idleSeed) { x, sillY, width ->
                drawWindowLights(canvas, r, elapsed, x, sillY, width)
            }
        }
        // The Halloween decorations, on a house and the three shops, open or shut; never with the
        // Christmas ones (`DecorationExclusion`). The cobweb takes the walls' day and night colours.
        if (customization.halloweenDecorationsEnabled && HalloweenDecorations.decorates(family.kind, storefront)) {
            drawHalloweenDecorations(canvas, deal, night, wallAt(spec, variant, storefront, 1f), wallAt(spec, variant, storefront, 0f), dayBlend)
        }
        val smokes = ChimneySmoke.smokes(customization)
        for (index in 0 until deal.size) {
            val placed = deal[index]
            val piece = placed.piece
            if (smokes && family.kind == WindowBuildingKind.HOUSE && piece.smokeY != 0f) {
                drawChimneySmoke(canvas, r, elapsed, dayBlend, x = piece.smokeX, topY = placed.baseY + piece.smokeY)
            }
            if (family.kind == WindowBuildingKind.SKYSCRAPER && piece.beaconY != 0f) {
                fillPaint.color = 0xFFE85D4A.toInt()
                canvas.drawCircle(piece.beaconX, placed.baseY + piece.beaconY, 2.2f, fillPaint)
            }
        }
        canvas.restore()
    }

    /**
     * The lit windows of one piece of a house at night: each pane its roster lights
     * ([NeighbourhoodComposer.Placed.firstPane]) as a card the pane's size less [LIT_PANE_INSET], faded
     * across a change ([WindowWalk.litAmount]: a window goes dark once its person is out of it). A rect
     * a lit pane; nothing allocated.
     */
    private fun drawLitPanes(
        canvas: SceneCanvas,
        roster: WindowRoster.Plan,
        layout: WindowWalk.Layout,
        placed: NeighbourhoodComposer.Placed,
        unlitGlass: Int,
        litGlass: Int,
    ) {
        val panes = placed.piece.panes
        if (panes.isEmpty()) return
        for (k in panes.indices) {
            val pane = placed.firstPane + k
            if (pane >= roster.panes) break
            val amount = WindowWalk.litAmount(roster, layout, windowFigures, pane)
            if (amount <= 0f) continue
            val box = panes[k]
            fillPaint.color = if (amount >= 1f) litGlass else SceneColour.blendArgb(unlitGlass, litGlass, amount)
            canvas.drawRect(
                box.x + LIT_PANE_INSET, placed.baseY + box.y + LIT_PANE_INSET,
                box.x + box.w - LIT_PANE_INSET, placed.baseY + box.y + box.h - LIT_PANE_INSET,
                fillPaint,
            )
        }
    }

    /** Reused across buildings and frames ([NeighbourhoodComposer.Deal]). */
    private val neighbourhoodDeal = NeighbourhoodComposer.Deal()

    /**
     * The lamp beside a door, paper by day, lit with the windows (`DESIGN_NOTES.md` decision 50): [lit]
     * is the building's `glassNight`, so a closed business's lamp is dark with its glass. The bulb goes
     * from [LAMP_UNLIT] to [LAMP_LIT], its glow appearing as it lights.
     */
    private fun drawPorchLight(canvas: SceneCanvas, x: Float, y: Float, lit: Float) {
        val glow = lampGlowAlpha(lit)
        if (glow > 0) {
            fillPaint.color = LAMP_LIT
            fillPaint.setAlphaWithoutAllocating(glow)
            canvas.drawCircle(x, y, 6f, fillPaint)
        }
        fillPaint.color = lampBulbColour(lit)
        canvas.drawCircle(x, y, 2.6f, fillPaint)
    }

    /**
     * Everybody seen at one window this frame ([windowFigures]): whoever stands at it, then whoever
     * walks out of it, into it or past it, cut to the pane, in front ([WindowWalk]). The hours thin the
     * people by walking them out ([WindowWalk.Doorway]), never by fading: a half-transparent person
     * reads as a rendering artefact.
     */
    private fun drawWindowPeople(
        canvas: SceneCanvas,
        r: StaticRuntime,
        roster: WindowRoster.Plan,
        layout: WindowWalk.Layout,
        kind: WindowBuildingKind,
        window: Int,
        winX: Float,
        winY: Float,
        winW: Float,
        winH: Float,
    ) {
        val figures = windowFigures
        for (f in 0 until figures.count) {
            if (figures.still[f] != window) continue
            drawWindowOccupant(canvas, r, roster.origin(figures.person[f]), kind, winX + winW / 2f, winY + winH, winW, facing = 1)
        }
        val paneLeft = winX + WindowWalk.PANE_INSET
        val paneRight = winX + winW - WindowWalk.PANE_INSET
        for (f in 0 until figures.count) {
            if (figures.still[f] >= 0 || !figures.seenThrough(f, window, layout)) continue
            val ref = figures.window[f]
            val refWidth = layout.width[ref]
            val x = figures.x[f]
            val facing = figures.facing[f]
            if (WindowWalk.bustRight(x, facing, refWidth) <= paneLeft || WindowWalk.bustLeft(x, facing, refWidth) >= paneRight) continue
            // Cut at the pane's sides only: a walker never leaves it upward or downward.
            drawWindowOccupant(
                canvas, r, roster.origin(figures.person[f]), kind, x, layout.bottom(ref), refWidth, facing,
                clipped = true, clipLeft = paneLeft, clipTop = winY - 1f, clipRight = paneRight, clipBottom = winY + winH + 1f,
            )
        }
    }

    /** One building's people this frame: see [drawWindowPeople]. Reused from building to building. */
    private val windowFigures = WindowWalk.Figures()

    /**
     * One person at a window: the roster's person whose first window was [origin] (their look, at every
     * window), on a sill at [cx], [cy], sized by a pane [winW] wide, [facing] +1 as drawn or -1 mirrored.
     * [clipped]: only the part inside the clip box, in the building's units -- a walker cut to its pane.
     */
    private fun drawWindowOccupant(
        canvas: SceneCanvas,
        r: StaticRuntime,
        origin: Int,
        kind: WindowBuildingKind,
        cx: Float,
        cy: Float,
        winW: Float,
        facing: Int,
        clipped: Boolean = false,
        clipLeft: Float = 0f,
        clipTop: Float = 0f,
        clipRight: Float = 0f,
        clipBottom: Float = 0f,
    ) {
        // The building's identity, fixed for the life of the scene: nobody moves house.
        val buildingSeed = buildingSeedOf(r.spec)
        val seed = themeId.hashCode()
        // Indoors there is no season: [PeopleLayerTable.WINDOW] has one column. Two integers, not a
        // [WindowOccupant], so no object is made per lit window per frame.
        val occupantKind = WindowOccupants.occupantKindIndexAt(seed, buildingSeed, origin, kind)
        val occupantSkin = WindowOccupants.occupantSkinIndexAt(seed, buildingSeed, origin)
        val slots = PeopleLayerTable.WINDOW[occupantKind]
        // A figure at a window crosses nothing: its colours come from its first window's address.
        val address = WindowOccupants.address(buildingSeed, origin)
        // On channels of their own: 41 and 42, the pane's presence rank and its occupant's age at this
        // address, would tie the outfit to the age and the hair to the lit pane ([PeopleColours.CH_WINDOW_HEAD]).
        val colours = coloursFor(
            address, 0, occupantKind, SUMMER_SEASON,
            PeopleColours.outfit(seed, address, 0, PeopleColours.CH_WINDOW_OUTFIT), occupantSkin,
            PeopleColours.CH_WINDOW_HEAD,
        )
        // From the declared CONTENT_BOTTOM_CENTRE anchor of the 49x57 units canvas, so the bust stands
        // on the sill. `OneOccupantRuleTest` reads the 0.85 and the divisor back out of this source and
        // `WindowWalkTest` holds [WindowWalk.bustScale] to the same expression.
        canvas.save()
        canvas.translate(cx, cy)
        // The tuned divisor, not the canvas width: [WINDOW_OCCUPANT_DIVISOR_UNITS].
        val s = (winW * 0.85f) / WINDOW_OCCUPANT_DIVISOR_UNITS
        canvas.scale(facing * s, s)
        if (!clipped) {
            drawPersonLayers(canvas, slots, -WINDOW_HEAD_ANCHOR_X_UNITS, -WINDOW_HEAD_ANCHOR_Y_UNITS, colours)
        } else {
            // The box, from the building's units into the bust's own: the inverse of the translate and
            // the scale above, a mirrored bust's sides swapped.
            val a = (clipLeft - cx) / (facing * s)
            val b = (clipRight - cx) / (facing * s)
            drawPersonLayersClipped(
                canvas, slots, -WINDOW_HEAD_ANCHOR_X_UNITS, -WINDOW_HEAD_ANCHOR_Y_UNITS, colours,
                minOf(a, b), (clipTop - cy) / s, maxOf(a, b), (clipBottom - cy) / s,
            )
        }
        canvas.restore()
    }

    /**
     * The smoke over a house's chimney, from the cap's middle ([x], [topY]): [ChimneySmoke]'s puffs, a
     * circle each, each chimney on its own beat. Nothing allocated.
     */
    private fun drawChimneySmoke(canvas: SceneCanvas, r: StaticRuntime, elapsed: SceneTime, dayBlend: Float, x: Float, topY: Float) {
        fillPaint.color = ChimneySmoke.colour(dayBlend)
        val beat = ChimneySmoke.beatOf(r.idleSeed)
        for (puff in 0 until ChimneySmoke.PUFFS) {
            val life = ChimneySmoke.life(elapsed, beat, puff)
            val alpha = ChimneySmoke.alpha(life)
            if (alpha < 1f) continue
            fillPaint.setAlphaWithoutAllocating(alpha.toInt())
            canvas.drawCircle(x + ChimneySmoke.dx(life), topY + ChimneySmoke.dy(life), ChimneySmoke.radius(life), fillPaint)
        }
        fillPaint.setAlphaWithoutAllocating(255)
    }

    /**
     * Whether this tree stands as a Christmas fir: the companion's [standsAsFir] rule, on the runtime's
     * seed (called only from [drawTree], so the slot is a tree).
     */
    private fun standsAsFir(r: StaticRuntime): Boolean = drawsFirs(customization) && isFirSeed(r.idleSeed)

    /**
     * A fir in the leafy tree's place (`DESIGN_NOTES.md` decision 66): four zigzag tiers, snow with the
     * winter palette, a star, ten lights and three presents, every number from [FirTable] (written with
     * the drawing by `tools/assets/fir/build_fir.py`). Each piece is trimmed and blits at its own origin:
     * `tree_fir` is 210x360 px = 70x120 u, `tree_fir_snow` is 192x291 px = 64x97 u and `tree_fir_star` is
     * 48x45 px = 16x15 u. Only the green takes the trees' night shade ([SceneCustomization.nightShadeFor],
     * decision 36); snow, star and lights do not. The lights are discs ([drawBulb]).
     */
    private fun drawFir(canvas: SceneCanvas, r: StaticRuntime, elapsed: SceneTime, dayBlend: Float) {
        drawGroundShadow(canvas, FirTable.WIDTH * FIR_SHADOW_SHARE)
        drawShadedSprite(canvas, R.drawable.tree_fir, FirTable.FIR_X, FirTable.FIR_Y, customization.nightShadeFor(r.spec, dayBlend))
        if (customization.winterColorsEnabled) {
            drawSprite(canvas, R.drawable.tree_fir_snow, FirTable.SNOW_X, FirTable.SNOW_Y)
        }
        drawSprite(canvas, R.drawable.tree_fir_star, FirTable.STAR_X, FirTable.STAR_Y)
        // Blinking as a leafy tree's lights do ([drawChristmasLights]).
        val xs = FirTable.LIGHT_X
        val ys = FirTable.LIGHT_Y
        for (i in xs.indices) {
            val phase = ((r.idleSeed + i * 0.37f) * 10f) % 6.283f
            val blink = (elapsed.sinAt(2.4f, phase) * 0.5f + 0.5f)
            if (blink < 0.35f) continue // off phase of the blink cycle
            drawBulb(canvas, xs[i], ys[i], CHRISTMAS_LIGHT_RADIUS_UNITS, christmasLightColors[i % christmasLightColors.size], 255)
        }
        // Three presents sized against the fir (126 units) rather than the scene (the gift is 30).
        val gifts = FirTable.GIFT_X
        val sizes = FIR_GIFT_SCALES
        for (i in gifts.indices) {
            canvas.save()
            canvas.translate(gifts[i], 0f)
            canvas.scale(sizes[i], sizes[i])
            drawTintedSprite(canvas, R.drawable.gift_box, -20f, -30f, GIFT_COLOURS[i % GIFT_COLOURS.size])
            drawSprite(canvas, R.drawable.gift_ribbon, -20f, -40f)
            canvas.restore()
        }
    }

    /**
     * One Christmas bulb at ([cx], [cy]): a disc ([SpriteBlitter.drawDisc]), one quad of the atlas on
     * the GPU where a drawn fan is 24 vertices or more, a circle on a Canvas.
     */
    private fun drawBulb(canvas: SceneCanvas, cx: Float, cy: Float, radius: Float, colour: Int, alpha: Int) {
        sprites.drawDisc(canvas, cx, cy, radius, colour, alpha)
    }

    /**
     * A leafy tree, or a fir in its place. Fall Colors and Winter Colors are palette overrides
     * ([SceneCustomization.fallColorsEnabled]), mutually exclusive (WallpaperPrefs clears one when the
     * other is set).
     */
    private fun drawTree(canvas: SceneCanvas, r: StaticRuntime, elapsed: SceneTime, dayBlend: Float = 1f) {
        if (standsAsFir(r)) {
            drawFir(canvas, r, elapsed, dayBlend)
            return
        }
        val sway = elapsed.sinAt(1.1f, r.idleSeed) * 4f
        drawGroundShadow(canvas, 26f)
        // `tree_trunk` is 32x62 units, blitted at the layout's trunk origin (-16,-62) so its foot
        // is on the ground; the canopy's content bottom lands at -52, ten units below the stem's
        // top, so the crown overlaps it.
        drawSprite(canvas, R.drawable.tree_trunk, TreeSpriteLayout.TRUNK_X, TreeSpriteLayout.TRUNK_Y)
        val leafColor = when {
            customization.fallColorsEnabled -> fallLeafColorFor(r)
            else -> customization.colorFor(r.spec, dayBlend)
        }
        canvas.save()
        canvas.translate(0f, -38f)
        canvas.rotate(sway)
        // **Halloween strips the crown**: bare limbs on the canopy's own canvas and origin, so they meet
        // the trunk and sway as the leaves do, and nothing that decorates a canopy (snow cap, lights).
        if (customization.halloweenEnabled) {
            drawSprite(
                canvas, R.drawable.tree_dead_branches,
                TreeSpriteLayout.DEAD_BRANCHES_X, TreeSpriteLayout.DEAD_BRANCHES_Y,
            )
            canvas.restore()
            return
        }
        // canopy: local bbox (-50,-80)-(51,-14) in the lifted space, attached at local y=0 so it
        // sits flush on the trunk whatever the sway.
        drawTintedSprite(
            canvas, R.drawable.tree_canopy,
            TreeSpriteLayout.CANOPY_X, TreeSpriteLayout.CANOPY_Y, leafColor,
        )
        if (customization.winterColorsEnabled) {
            // The cap's source clips its snow against `tree_canopy`'s own circles, so no green rim
            // shows above it; same origin ([TreeSpriteLayout]): a canopy redraw re-derives both.
            drawSprite(
                canvas, R.drawable.tree_canopy_snowcap,
                TreeSpriteLayout.SNOWCAP_X, TreeSpriteLayout.SNOWCAP_Y,
            )
        }
        // Across the canopy's measured content: `tree_canopy` is 303x198 px with content filling it,
        // centre (0.5,-47), half extents 50.5 x 33, the radii inset for the scalloped rim. A Christmas
        // decoration with its own switch, *Lights on the trees*, not a winter one.
        if (customization.christmasTreeLightsEnabled) {
            drawChristmasLights(canvas, r, elapsed, centerY = TREE_LIGHTS_CENTRE_Y, radiusX = TREE_LIGHTS_RADIUS_X, radiusY = TREE_LIGHTS_RADIUS_Y)
        }
        canvas.restore()
    }

    /** The tree's autumn tone, from its seed ([fallToneOf]): flat day and night on purpose, as the
     * falling leaves ([PaperRenderer.drawFallingLeaves]). */
    private fun fallLeafColorFor(r: StaticRuntime): Int = FALL_LEAF_TREE_PALETTE[fallToneOf(r.idleSeed)]

    /**
     * The light colours and, below, the six lights' positions: built once, not per tree per frame,
     * as parallel `FloatArray`s, so nothing is boxed.
     */
    private val christmasLightColors = CHRISTMAS_LIGHT_COLOURS

    /**
     * Where the lights sit in a **unit disc** (all within 0.81): scaled by a crown's measured half
     * extents, they fall inside it by construction.
     */
    private val christmasLightX = CHRISTMAS_LIGHT_X
    private val christmasLightY = CHRISTMAS_LIGHT_Y

    /**
     * Scatters blinking lights across an ellipse of foliage, inside the plant's own sway transform so
     * they lean with the branches. [centerX] is the trunk for a leafy tree; a palm's leaning crown is
     * seven units off it.
     */
    private fun drawChristmasLights(
        canvas: SceneCanvas,
        r: StaticRuntime,
        elapsed: SceneTime,
        centerY: Float,
        radiusX: Float,
        radiusY: Float,
        centerX: Float = 0f,
    ) {
        // The lights are discs ([drawBulb]).
        canvas.save()
        canvas.translate(centerX, centerY)
        for (i in christmasLightX.indices) {
            val phase = ((r.idleSeed + i * 0.37f) * 10f) % 6.283f
            val blink = (elapsed.sinAt(2.4f, phase) * 0.5f + 0.5f)
            if (blink < 0.35f) continue // off phase of the blink cycle
            // Scaled here, only for the lights drawn this frame.
            val lx = christmasLightX[i] * radiusX
            val ly = christmasLightY[i] * radiusY
            drawBulb(canvas, lx, ly, CHRISTMAS_LIGHT_RADIUS_UNITS, christmasLightColors[i % christmasLightColors.size], 255)
        }
        canvas.restore()
    }

    /**
     * The snowman: `snowman_body` (local bbox (-19,-74)-(19,0)) tinted to the user's colour, nose and
     * scarf fixed-colour accents, twig arms two stroked lines.
     */
    private fun drawSnowman(canvas: SceneCanvas, r: StaticRuntime, elapsed: SceneTime, dayBlend: Float) {
        val wobble = elapsed.sinAt(1.4f, r.idleSeed) * 2f
        drawGroundShadow(canvas, 22f)
        canvas.save()
        canvas.rotate(wobble)
        val snow = customization.colorFor(r.spec, dayBlend)
        // -74: the content bottom is the canvas bottom, on the ground.
        drawTintedSprite(canvas, R.drawable.snowman_body, -19f, -74f, snow)
        // Against the body's measured landmarks: hat brim -63, head -61..-39, neck -38.
        drawSprite(canvas, R.drawable.snowman_nose, 4f, -51f)
        drawSprite(canvas, R.drawable.snowman_scarf, -12f, -40f)
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 3f
        strokePaint.color = 0xFF7A4B2E.toInt()
        // Twig arms from the torso, where the lower sphere reaches +/-15.7 at -30, not the head.
        canvas.drawLine(-14f, -30f, -32f, -40f, strokePaint)
        canvas.drawLine(14f, -30f, 30f, -36f, strokePaint)
        strokePaint.strokeWidth = 2.5f
        canvas.restore()
    }

    /**
     * A scarecrow on its pole (`DESIGN_NOTES.md` decision 80, `tools/assets/scarecrow/build_scarecrow.py`):
     * fixed art, drawn as authored at every hour (decision 36), swaying as the snowman does, its candle's
     * alpha the night; [SCARECROW_ASIDE_METRES] to the right where the snowman stands too.
     */
    private fun drawScarecrow(canvas: SceneCanvas, r: StaticRuntime, elapsed: SceneTime, dayBlend: Float) {
        if (r.scarecrowAside) canvas.translate(SCARECROW_ASIDE_UNITS, 0f)
        drawGroundShadow(canvas, SCARECROW_SHADOW_HALF_UNITS)
        canvas.save()
        canvas.rotate(elapsed.sinAt(1.4f, r.idleSeed) * 2f)
        drawSprite(canvas, ScarecrowTable.RES, ScarecrowTable.X, ScarecrowTable.Y)
        val lit = ((1f - dayBlend).coerceIn(0f, 1f) * 255f).toInt()
        if (lit > 0) sprites.draw(canvas, ScarecrowTable.LIGHT_RES, ScarecrowTable.LIGHT_X, ScarecrowTable.LIGHT_Y, SpriteScale.SCENE_UNITS, lit)
        canvas.restore()
    }

    /**
     * The box tinted to the user's colour (`gift_box`, local bbox (-20,-30)-(20,0)), the ribbon in its
     * own gold, untinted.
     */
    private fun drawGift(canvas: SceneCanvas, r: StaticRuntime, dayBlend: Float) {
        val color = customization.colorFor(r.spec, dayBlend)
        drawGroundShadow(canvas, 22f)
        drawTintedSprite(canvas, R.drawable.gift_box, -20f, -30f, color)
        drawSprite(canvas, R.drawable.gift_ribbon, -20f, -40f)
    }

    /**
     * A palm: trunk and crown, fixed art, swaying as one rigid body about its base. Its green is the
     * drawing's, so Fall Colors does not reach it (an orange over finished art would compound two
     * colours); winter and *Halloween* have whole crowns of their own.
     */
    private fun drawPalmTree(canvas: SceneCanvas, r: StaticRuntime, elapsed: SceneTime, dayBlend: Float = 1f) {
        val sway = elapsed.sinAt(0.9f, r.idleSeed) * 6f
        drawGroundShadow(canvas, 22f)
        // How lit it is: a shade, not a tint ([drawShadedSprite]).
        val light = customization.nightShadeFor(r.spec, dayBlend)

        canvas.save()
        canvas.rotate(sway) // whole tree leans as one rigid body, pivoted at its base (0,0)
        // trunk: 63x174 px -- 21x58 local units -- stood on its **foot** at x=8, origin (-8,-58):
        // the trunk leans, and `CONTENT_BOTTOM_CENTRE` (10.33) would stand it beside its own shadow.
        drawShadedSprite(canvas, R.drawable.palmtree_trunk, PalmSpriteLayout.TRUNK_X, PalmSpriteLayout.TRUNK_Y, light)
        // The 56x48-unit crown canvas declares its attachment at (28,26) units, where the blades
        // converge, placed at (7,-56), two units into the leaning trunk's top: origin (-21,-82). Three
        // crowns share canvas and attachment, one blitted: live, frosted (snow as a whole drawing, as
        // on a roof) or *Halloween*'s dead blades (a grey crown would read as bad light). Three literal
        // blits, so `paperscrape-assets validate` checks each one's scale, tint class and origin.
        val crownX = PalmSpriteLayout.CROWN_X
        val crownY = PalmSpriteLayout.CROWN_Y
        when {
            customization.halloweenEnabled ->
                drawShadedSprite(canvas, R.drawable.palmtree_fronds_dead, crownX, crownY, light)
            customization.winterColorsEnabled ->
                drawShadedSprite(canvas, R.drawable.palmtree_fronds_frost, crownX, crownY, light)
            else ->
                drawShadedSprite(canvas, R.drawable.palmtree_fronds, crownX, crownY, light)
        }
        if (customization.christmasTreeLightsEnabled) {
            // From the 56x48-unit crown: centre (7,-58), half extents 28 x 24, inset because a crown
            // of blades is mostly gaps.
            drawChristmasLights(
                canvas, r, elapsed,
                centerX = PalmSpriteLayout.CROWN_CENTRE_X, centerY = PalmSpriteLayout.CROWN_CENTRE_Y,
                radiusX = 18f, radiusY = 13f,
            )
        }
        canvas.restore()
    }

    private fun drawParasol(canvas: SceneCanvas, r: StaticRuntime, elapsed: SceneTime, dayBlend: Float) {
        // The canopy sways by a rotation about the pole tip (0, -50), never a vertical bob, which
        // would open a gap at the mount and read as hovering.
        fillPaint.color = 0x55000000
        canvas.drawOval(-22f, -4f, 22f, 4f, fillPaint)
        fillPaint.color = parasolPoleColor
        canvas.drawRect(-2.5f, -50f, 2.5f, 0f, fillPaint)
        val sway = elapsed.sinAt(1.6f, r.idleSeed) * 2.5f
        canvas.save()
        canvas.translate(0f, -50f)
        canvas.rotate(sway)
        val sweep = 36
        for (i in 0 until 5) {
            fillPaint.color = customization.parasolStripeColor(i, dayBlend)
            // A sector per stripe: a primitive both backends draw directly.
            canvas.drawWedge(0f, 0f, 34f, 180f + i * sweep, sweep.toFloat(), fillPaint)
        }
        canvas.restore()
    }

    /** Body and belly are tintable masks; the beak and the feet carry their own orange and are
     * blitted untinted. The waddle is one `canvas.rotate` around the whole group. */
    private fun drawPenguin(canvas: SceneCanvas, r: StaticRuntime, elapsed: SceneTime, dayBlend: Float) {
        val waddle = elapsed.sinAt(4f, r.idleSeed) * 4f
        drawGroundShadow(canvas, 16f)
        canvas.save()
        canvas.rotate(waddle * 0.5f)
        val body = customization.colorFor(r.spec, dayBlend)
        drawTintedSprite(canvas, R.drawable.penguin_body, -14f, -45f, body)
        drawTintedSprite(canvas, R.drawable.penguin_belly, -9f, -38f, penguinBellyColor)
        // -45 is the very top of the body, above the eyes; the face sits around -37.
        drawSprite(canvas, R.drawable.penguin_beak, -6f, -37f)
        drawSprite(canvas, R.drawable.penguin_feet, -10f, 0f)
        canvas.restore()
    }

    /** The shell is a tintable mask so it follows the user's colour; the decorative stripe and
     * dots are fixed art blitted over it. */
    private fun drawEasterEgg(canvas: SceneCanvas, r: StaticRuntime, dayBlend: Float) {
        val shell = customization.colorFor(r.spec, dayBlend)
        drawGroundShadow(canvas, 15f)
        drawTintedSprite(canvas, R.drawable.easteregg_shell, -16f, -40f, shell)
        drawSprite(canvas, R.drawable.easteregg_pattern, -16f, -25f)
    }

    /** Body, head and both ears are one tintable sprite (`bunny_body`, drawn already-assembled so
     * the ears cannot drift from the head); the inner ear and the tail are fixed art blitted on
     * top. The ear wiggle rotates the whole bunny slightly, since the ears are in the body's bitmap. */
    private fun drawBunny(canvas: SceneCanvas, r: StaticRuntime, elapsed: SceneTime, dayBlend: Float) {
        val earWiggle = elapsed.sinAt(3f, r.idleSeed) * 2.5f
        val body = customization.colorFor(r.spec, dayBlend)
        drawGroundShadow(canvas, 18f)
        canvas.save()
        canvas.rotate(earWiggle * 0.4f)
        // -61 stands the body's content bottom on the ground, as the snowman's -74 does. The
        // horizontal origin is deliberately not the content centre -- see the ear note below.
        drawTintedSprite(canvas, R.drawable.bunny_body, -14f, -61f, body)
        // The ears occupy x -9.3..15.3, so their centre is at 3 and the two-ear inner patch --
        // 14.7 units of content -- centres on it from here.
        drawSprite(canvas, R.drawable.bunny_innerear, -4f, -57f)
        drawSprite(canvas, R.drawable.bunny_tail, -21f, -10f)
        canvas.restore()
    }

    /** The 3-lobe body is one tinted sprite (`pumpkin_body`); the stem and leaf are a fixed-colour
     * accent sprite on top. */
    private fun drawPumpkin(canvas: SceneCanvas, r: StaticRuntime, dayBlend: Float) {
        val base = customization.colorFor(r.spec, dayBlend)
        drawGroundShadow(canvas, 16f)
        drawTintedSprite(canvas, R.drawable.pumpkin_body, -19f, -30f, base)
        // **The switch that strips the tree crowns carves the pumpkins.** The face is drawn on the
        // body's own canvas at the body's own origin, so it registers with the fruit, and it is
        // fixed art: a carved hole is not a colour a theme gets to pick.
        if (customization.halloweenEnabled) {
            drawSprite(canvas, R.drawable.pumpkin_face, -19f, -30f)
        }
        drawSprite(canvas, R.drawable.pumpkin_stem, 2f, -42f)
    }

    /**
     * The fire truck in [drawCar]'s local space: ground at y=37, wheel centres (-34, 26), (9.325, 26),
     * (34, 26). `firetruck_body` is **300x159 px = 100x53 units**, its own outline, not a sedan's.
     * The ladder is drawn **first**, so the roof line paints over its lower rail and it rides on the
     * roof; the two warning lights stand on the cab roof, ahead of it.
     */
    private fun drawFireTruck(canvas: SceneCanvas, nightGlow: Float) {
        drawSprite(canvas, R.drawable.firetruck_ladder, FIRE_TRUCK_LADDER_X_UNITS, FIRE_TRUCK_LADDER_Y_UNITS)
        drawSprite(canvas, R.drawable.firetruck_body, FIRE_TRUCK_BODY_X_UNITS, FIRE_TRUCK_BODY_Y_UNITS)
        // The beacons brighten toward night: a colour already painted costs nothing to change.
        fillPaint.color = ColorUtils.blendARGB(0xFFD6362E.toInt(), BEACON_RED_LIT, nightGlow)
        canvas.drawRect(
            FIRE_TRUCK_BEACON_RED_LEFT_X, FIRE_TRUCK_BEACON_TOP_Y_UNITS,
            FIRE_TRUCK_BEACON_RED_RIGHT_X, FIRE_TRUCK_CAB_ROOF_Y_UNITS, fillPaint,
        )
        fillPaint.color = ColorUtils.blendARGB(0xFF2B5FCB.toInt(), BEACON_BLUE_LIT, nightGlow)
        canvas.drawRect(
            FIRE_TRUCK_BEACON_BLUE_LEFT_X, FIRE_TRUCK_BEACON_TOP_Y_UNITS,
            FIRE_TRUCK_BEACON_BLUE_RIGHT_X, FIRE_TRUCK_CAB_ROOF_Y_UNITS, fillPaint,
        )
        // The same two lenses every car carries, at the appliance's own seats.
        drawVehicleLamps(
            canvas, nightGlow,
            FIRE_TRUCK_LAMP_FRONT_X_UNITS, FIRE_TRUCK_LAMP_FRONT_Y_UNITS,
            FIRE_TRUCK_LAMP_REAR_X_UNITS, FIRE_TRUCK_LAMP_REAR_Y_UNITS,
        )
    }

    /**
     * The amber-forward, red-aft lamps that say which way a vehicle goes, from the fleet's four
     * sprites: the day pair always, fixed art, so amber stays amber on a red car (a lens baked into
     * the tinted shell would take the paint); the lit pair fades in over it after dark.
     */
    private fun drawVehicleLamps(
        canvas: SceneCanvas, nightGlow: Float,
        frontX: Float, frontY: Float, rearX: Float, rearY: Float,
    ) {
        drawSprite(canvas, R.drawable.car_lamp_front, frontX, frontY)
        drawSprite(canvas, R.drawable.car_lamp_rear, rearX, rearY)
        val lit = litVehicleAlpha(nightGlow)
        if (lit > 0) {
            drawSpriteFaded(canvas, R.drawable.car_lamp_front_lit, frontX, frontY, lit)
            drawSpriteFaded(canvas, R.drawable.car_lamp_rear_lit, rearX, rearY, lit)
        }
    }

    /**
     * One seated bust on the sill at [x], at the vehicle's occupant scale (a method, not a lambda: the
     * draw path allocates nothing). **Always mirrored**: the seated artwork faces +x, and +x in the
     * vehicle's frame is its **rear** (the vehicle art faces left, the driver at the negative
     * [CAR_HEAD_X_UNITS]); `drawCar`'s `scale(dir, 1)` then turns the whole vehicle with its direction.
     * Mirrored about the anchor, so the eye axis stays where the seat put it.
     */
    private fun drawSeatedOccupant(
        canvas: SceneCanvas,
        x: Float,
        y: Float,
        scale: Float,
        slots: IntArray,
        colours: IntArray,
    ) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(-scale, scale)
        drawPersonLayers(canvas, slots, -HEAD_CAR_ANCHOR_X_UNITS, -HEAD_CAR_ANCHOR_Y_UNITS, colours)
        canvas.restore()
    }

    /**
     * The four colours one seat wears: a seated figure crosses nothing, so they are a function of
     * the car's candidate identity and the seat, stable on the road. The [outfit] is dealt per seat
     * ([SeatedOccupants.driverOutfit]), so two occupants never match and all five outfits ride.
     */
    private fun carColours(
        spec: CarObject,
        kind: Int,
        season: Int,
        seat: Int,
        outfit: Int,
        skinIndex: Int,
    ): IntArray = coloursFor(
        SceneObjectCatalog.candidateIndexOf(spec) * SEAT_ADDRESS_STRIDE + seat,
        0,
        kind,
        season,
        outfit,
        skinIndex,
    )


    /**
     * A vehicle ([CarType]): its [CarShell]'s body and glass, the plain car in the user's colour, the
     * police car and the taxi in fixed colours with their accessories, or the fire truck's own body
     * ([drawFireTruck]); wheels are discs ([drawWheel]) at each body's wheelbase.
     */
    private fun drawCar(canvas: SceneCanvas, c: CarRuntime, screenWidth: Float, screenHeight: Float, dayBlend: Float) {
        val margin = vehicleEdgeMarginPx(screenHeight)
        val travel = screenWidth + margin * 2f
        val rawX = c.progress * travel - margin
        val x = if (c.spec.reverse) screenWidth - rawX else rawX
        val y = c.spec.laneYFraction * screenHeight
        // **The vehicle artwork faces left** (the shells' long bonnet and the glass's raked edge are
        // at their left end), so the unflipped blit drives leftward.
        val dir = if (c.spec.reverse) 1f else -1f

        // Resolved once with the runtime ([CarShell.forCar]), never here.
        val shell = c.shell
        val isFireTruck = c.spec.type == CarType.FIRE_TRUCK

        // Sized by the projection at its own lane, as everything on the ground; the three car bodies
        // share one base scale, being one metre-per-unit ([SceneSpace.CAR_UNIT_METRES]).
        val vehicleScale = (
            if (isFireTruck) SceneSpace.FIRE_TRUCK_BASE_SCALE else SceneSpace.CAR_BASE_SCALE
            ) * SceneSpace.perspectiveScaleAt(c.spec.laneYFraction) * SceneSpace.sceneScale(screenHeight)

        val nightGlow = (1f - dayBlend).coerceIn(0f, 1f)

        canvas.save()
        canvas.translate(x, y)
        canvas.scale(dir * vehicleScale, vehicleScale)
        // The wheel-bottom is at local y=37 ([VEHICLE_GROUND_Y_UNITS]): lifted here, not renumbered.
        canvas.translate(0f, -VEHICLE_GROUND_Y_UNITS)

        // On the road, not on the bonnet. See [VEHICLE_GROUND_Y_UNITS].
        canvas.save()
        canvas.translate(0f, VEHICLE_GROUND_Y_UNITS)
        // Each body's own footprint: an estate is 32 units longer than a compact.
        drawGroundShadow(
            canvas,
            if (isFireTruck) FIRE_TRUCK_SHADOW_HALF_LENGTH_UNITS else shell.shadowHalfLengthUnits,
            4f,
        )
        canvas.restore()

        if (isFireTruck) {
            drawFireTruck(canvas, nightGlow)
        } else {
            val bodyColor = when (c.spec.type) {
                CarType.POLICE -> 0xFFF0F0F2.toInt()
                CarType.TAXI -> 0xFFFFC61A.toInt()
                else -> customization.colorFor(c.spec, dayBlend)
            }
            // Body and glass from the same [shell].
            drawTintedSprite(canvas, shell.bodyRes, shell.bodyXUnits, shell.bodyYUnits, bodyColor)
            drawSprite(canvas, shell.glassRes, shell.glassSpriteXUnits, CAR_GLASS_SPRITE_Y_UNITS)

            when (c.spec.type) {
                CarType.POLICE -> {
                    // The livery stripe runs along the doors, standing on the sill.
                    drawSprite(canvas, R.drawable.police_stripe, CAR_LIVERY_X_UNITS, CAR_SILL_Y_UNITS)
                    drawSprite(
                        canvas, R.drawable.police_lightbar,
                        POLICE_LIGHTBAR_X_UNITS, POLICE_LIGHTBAR_Y_UNITS,
                    )
                    val lit = litVehicleAlpha(nightGlow)
                    if (lit > 0) {
                        // Colour, then alpha: the alpha lives in the colour, and set before it would be lost,
                        // the beacons coming on whole instead of with the other lamps ([litVehicleAlpha]).
                        fillPaint.color = BEACON_RED_LIT
                        fillPaint.setAlphaWithoutAllocating(lit)
                        canvas.drawRect(
                            POLICE_LAMP_RED_LEFT_X, POLICE_LAMP_TOP_Y_UNITS,
                            POLICE_LAMP_RED_RIGHT_X, POLICE_LAMP_BOTTOM_Y_UNITS, fillPaint,
                        )
                        fillPaint.color = BEACON_BLUE_LIT
                        fillPaint.setAlphaWithoutAllocating(lit)
                        canvas.drawRect(
                            POLICE_LAMP_BLUE_LEFT_X, POLICE_LAMP_TOP_Y_UNITS,
                            POLICE_LAMP_BLUE_RIGHT_X, POLICE_LAMP_BOTTOM_Y_UNITS, fillPaint,
                        )
                        fillPaint.setAlphaWithoutAllocating(255)
                    }
                }
                // The chequer bands the doors, as the police stripe does.
                CarType.TAXI -> {
                    drawSprite(canvas, R.drawable.taxi_checker, CAR_LIVERY_X_UNITS, CAR_SILL_Y_UNITS)
                    // The roof sign, centred on the compact's roof as the light bar on the saloon's.
                    drawSprite(canvas, R.drawable.taxi_sign, TAXI_SIGN_X_UNITS, TAXI_SIGN_Y_UNITS)
                    val lit = litVehicleAlpha(nightGlow)
                    if (lit > 0) {
                        // The "for hire" light.
                        fillPaint.color = TAXI_SIGN_LIT
                        fillPaint.setAlphaWithoutAllocating(lit)
                        canvas.drawRect(
                            TAXI_SIGN_BOX_LEFT_X, TAXI_SIGN_BOX_TOP_Y,
                            TAXI_SIGN_BOX_RIGHT_X, TAXI_SIGN_BOX_BOTTOM_Y, fillPaint,
                        )
                        fillPaint.setAlphaWithoutAllocating(255)
                    }
                }
                else -> {}
            }
            // The fleet's lamps, at this body's own seats.
            drawVehicleLamps(
                canvas, nightGlow,
                shell.lampFrontXUnits, shell.lampFrontYUnits,
                shell.lampRearXUnits, shell.lampRearYUnits,
            )
        }

        // The occupants ([PeopleLayerTable.CAR]), sized off the height table ([CAR_OCCUPANT_SCALE]) and
        // coloured at the blit like every figure. The driver is an adult, the passenger any family:
        // the cabins and the pitch are measured on the widest bust, the winter girl's ([CAR_HEAD_X_UNITS]).
        // The passenger is drawn first, so the one in front would occlude (their ink keeps 2.06 units
        // apart today). After the livery, so no paint covers a person (`VehicleDrawOrderTest`). Who
        // rides is dealt, not hashed, from the candidate's immutable slot ([SeatedOccupants]).
        val driverKindIdx = SeatedOccupants.driverKind(c.spec)
        val outfitIdx = SeatedOccupants.driverOutfit(c.spec)
        val seasonIdx = outdoorSeasonIndex()
        val occupantScale = if (isFireTruck) FIRE_TRUCK_OCCUPANT_SCALE else CAR_OCCUPANT_SCALE
        if (isFireTruck) {
            drawSeatedOccupant(
                canvas, FIRE_TRUCK_HEAD_X_UNITS, FIRE_TRUCK_HEAD_Y_UNITS, occupantScale,
                PeopleLayerTable.CAR[driverKindIdx][seasonIdx],
                carColours(c.spec, driverKindIdx, seasonIdx, 0, outfitIdx, SeatedOccupants.driverSkin(c.spec)),
            )
        } else {
            if (c.spec.type.seatsTwo) {
                // **Never the driver's family, never the driver's outfit**: either would read as one
                // person drawn twice ([SeatedOccupants]).
                drawSeatedOccupant(
                    canvas, CAR_PASSENGER_X_UNITS + shell.seatOffsetXUnits, CAR_PASSENGER_Y_UNITS,
                    occupantScale,
                    PeopleLayerTable.CAR[SeatedOccupants.passengerKind(c.spec)][seasonIdx],
                    carColours(
                        c.spec, SeatedOccupants.passengerKind(c.spec), seasonIdx, 1,
                        SeatedOccupants.passengerOutfit(c.spec),
                        SeatedOccupants.passengerSkin(c.spec),
                    ),
                )
            }
            // The pair moves together, by this body's offset ([CarShell.seatOffsetXUnits]): the pitch is shared.
            drawSeatedOccupant(
                canvas, CAR_HEAD_X_UNITS + shell.seatOffsetXUnits, CAR_HEAD_Y_UNITS, occupantScale,
                PeopleLayerTable.CAR[driverKindIdx][seasonIdx],
                carColours(c.spec, driverKindIdx, seasonIdx, 0, outfitIdx, SeatedOccupants.driverSkin(c.spec)),
            )
        }

        // Wheels **after** the body and the occupants: in «Cut-out» a wheel is a whole disc glued on
        // the paper. Each body has its own wheelbase (the estate's -42 and +40, a long load bay).
        val wheelFrontX = if (isFireTruck) -FIRE_TRUCK_WHEEL_X_UNITS else shell.wheelFrontXUnits
        val wheelRearX = if (isFireTruck) FIRE_TRUCK_WHEEL_X_UNITS else shell.wheelRearXUnits
        val wheelRadius = if (isFireTruck) FIRE_TRUCK_WHEEL_RADIUS_UNITS else CAR_WHEEL_RADIUS_UNITS
        val wheelY = VEHICLE_GROUND_Y_UNITS - wheelRadius
        val hubRadius = wheelRadius * WHEEL_HUB_RATIO
        if (isFireTruck) {
            // The rear pair's inner wheel, under the outer one. See FIRE_TRUCK_INNER_WHEEL_X_UNITS.
            drawWheel(canvas, FIRE_TRUCK_INNER_WHEEL_X_UNITS, wheelY, wheelRadius, hubRadius)
        }
        drawWheel(canvas, wheelFrontX, wheelY, wheelRadius, hubRadius)
        drawWheel(canvas, wheelRearX, wheelY, wheelRadius, hubRadius)

        canvas.restore()
    }

    /**
     * One wheel: a disc of ink and a smaller disc of light card, two fills and no stroke (a ring at
     * 0.88 px a unit would be a grey pixel each side, noise).
     */
    private fun drawWheel(canvas: SceneCanvas, cx: Float, cy: Float, radius: Float, hubRadius: Float) {
        fillPaint.color = WHEEL_TYRE_COLOUR
        canvas.drawCircle(cx, cy, radius, fillPaint)
        fillPaint.color = WHEEL_HUB_COLOUR
        canvas.drawCircle(cx, cy, hubRadius, fillPaint)
    }
}
