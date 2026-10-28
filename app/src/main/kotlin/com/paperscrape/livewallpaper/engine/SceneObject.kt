package com.paperscrape.livewallpaper.engine

import kotlin.random.Random

/**
 * The kind of object placed in the scene. Drives its drawing, its size and which customization
 * category governs it.
 */
enum class SceneObjectType {
    CAR, HOUSE, TREE,
    SNOWMAN, GIFT, PALM_TREE, PARASOL, SKYSCRAPER, PENGUIN,
    EASTER_EGG, BUNNY,
    PUMPKIN,
    // Laid by the catalogue on the snowmen's places and never saved in a layout (`asSaved`): a build that
    // does not know the type would refuse the whole layout.
    SCARECROW,
}

/**
 * A stationary (but animated-in-place) object anchored to the hill's ground line at a continuous depth.
 *
 * [depthFraction] (0 farthest and smallest, 1 nearest and largest) decides both where it stands and how big it is
 * drawn ([SceneSpace.groundYFraction], [SceneSpace.depthScale]): within a category's narrow range a second fraction
 * for the size would move with the first. [tileFractionX] is its position along the object layer's tile, twice the
 * screen width, the period the hills tile on ([PaperRenderer.drawHillLayers]).
 *
 * [scale] is a **relative size variation around 1**, not a size: how big a house is belongs to houses
 * ([SceneSpace.SceneVariant]). A payload that stored the base size here is converted by the schema 1 -> 2 migration
 * in `CustomThemeData.kt`.
 *
 * [silhouette] is **which of its category's drawings this slot was dealt** ([SilhouetteDeal] says why it is
 * recorded). [UNDEALT] -- a random-theme layout, a theme saved without deals, a bare test candidate -- hashes the
 * drawing from the position.
 */
data class StaticSceneObject(
    val type: SceneObjectType,
    val depthFraction: Float,
    val tileFractionX: Float,
    val scale: Float = 1f,
    val silhouette: Int = UNDEALT,
) {
    companion object {
        /** No deal recorded: the drawing is hashed from the position. */
        const val UNDEALT = -1
    }
}

/**
 * The 4 vehicle types of the road. Only [PLAIN] takes the "Cars" category colours: the police car and the taxi are
 * the same tintable shells tinted with fixed constants in `drawCar`, the fire engine finished, non-tintable art.
 *
 * [seatsTwo] is declared with the type, so a vehicle added later gets its answer here rather than from an exclusion
 * at a call site. The passenger is any of the four families, children included ([SeatedOccupants]); the fire engine
 * seats one because its cab glass is 19 units and holds one head.
 */
enum class CarType(val seatsTwo: Boolean) {
    PLAIN(true),
    TAXI(true),
    POLICE(true),
    FIRE_TRUCK(false),
}

/**
 * A car that drives across the screen in its own loop, unaffected by home-screen parallax
 * scrolling: it lives on the road, not in the static background.
 */
data class CarObject(
    val laneYFraction: Float, // vertical position as a fraction of screen height
    val speedFraction: Float, // screen-widths per second
    val startDelaySeconds: Float,
    val color: Int,
    val reverse: Boolean = false,
    /** PLAIN for every call site that does not choose a vehicle. */
    val type: CarType = CarType.PLAIN,
)

/** The full set of interactive/decorative objects that belong to one theme's scene. */
data class SceneObjectLayout(
    val staticObjects: List<StaticSceneObject>,
    val cars: List<CarObject>,
    /**
     * How [staticObjects] are thinned by density: [DENSITY_SCHEME_FRACTION] for every generated
     * layout, and for a saved one whatever it was saved with (see [DENSITY_SCHEME_GRID]).
     */
    val densityScheme: Int = DENSITY_SCHEME_FRACTION,
    /**
     * A saved street's places among the houses that the loader moved, each to the place as the theme stores it
     * ([SceneObjectCatalog.placesClearedOnSavedStreet]); empty where nothing moved. The six buildings take their
     * colours and people from the places as stored ([StorefrontStreet.identities]) and a save writes them back as
     * stored ([toJson]), so moving a place changes where it stands and nothing else.
     */
    val placesAsSaved: Map<StaticSceneObject, StaticSceneObject> = emptyMap(),
)

/**
 * Default per-theme object layouts. Every theme offers the same range for the 5 structural, user-editable categories
 * (houses, buildings, cars, umbrellas, trees): [CANDIDATES_PER_CATEGORY] slots each, generated rather than
 * hand-authored, so a quiet village or a dense city is the user's density sliders, not the theme. The seasonal
 * decorations are a second, independent set ([seasonalDecorationCandidates]), laid onto every theme by [layoutFor].
 */
object SceneObjectCatalog {

    /**
     * Forces a saved theme's traffic onto the lane pair the scene currently uses.
     *
     * **Lane position, speed, direction and loop slot are scene geometry, not theme data**: nothing in the app puts a
     * car anywhere but the canonical lanes ([SceneSpace]), so a stored lane coordinate is at best a stale copy of a
     * constant, and believed it would drag the painted road ([SceneObjectRenderer] derives it from the layout's lanes)
     * over the pavement. Moving a lane constant changes no schema shape, so this runs on **every** load. Kept: how many
     * cars, their colours and types. A car's lane comes from where it sat in its own theme's spread, so a two-lane
     * theme keeps its lanes; cars that all share one lane are alternated.
     */
    fun canonicaliseTraffic(cars: List<CarObject>): List<CarObject> {
        if (cars.isEmpty()) return cars
        val minLane = cars.minOf { it.laneYFraction }
        val maxLane = cars.maxOf { it.laneYFraction }
        val degenerate = maxLane - minLane < SceneSpace.MIN_ROAD_LANE_SPACING_FRACTION
        val midpoint = (minLane + maxLane) / 2f
        var nearCount = 0
        var farCount = 0
        return cars.mapIndexed { index, car ->
            val near = if (degenerate) index % 2 == 0 else car.laneYFraction >= midpoint
            val slot = if (near) nearCount++ else farCount++
            car.copy(
                laneYFraction = if (near) SceneSpace.ROAD_LANE_NEAR_Y_FRACTION else SceneSpace.ROAD_LANE_FAR_Y_FRACTION,
                speedFraction = if (near) SceneSpace.CAR_SPEED_NEAR else SceneSpace.CAR_SPEED_FAR,
                startDelaySeconds = CAR_LOOP_ENTRY_PROGRESS + CAR_LOOP_SPAN * slot / CAR_SLOTS_PER_LANE,
                // Direction follows the lane, so no two cars can meet head-on inside one.
                reverse = !near,
            )
        }
    }

    /**
     * A car candidate's own slot in the ten the road has: `queue slot * 2 + (near lane ? 0 : 1)`.
     *
     * **There are exactly ten in every theme**, the fact behind both [CarShell]'s body deal and [SeatedOccupants]'s:
     * anything hashed from a candidate's lane (one of two constants) and start delay (one of [CAR_SLOTS_PER_LANE]
     * points) chooses from ten values and can come out lopsided. This inverts the expression the candidate was
     * generated from, on the grid [canonicaliseTraffic] puts every stored theme on; a theme carrying more cars than
     * one loop holds wraps (the modulo) rather than piling the surplus onto one answer.
     */
    fun candidateIndexOf(spec: CarObject): Int {
        val slots = CAR_SLOTS_PER_LANE
        val raw = Math.round((spec.startDelaySeconds - CAR_LOOP_ENTRY_PROGRESS) * slots / CAR_LOOP_SPAN)
        val slot = ((raw % slots) + slots) % slots
        val near = spec.laneYFraction >= LANE_MIDPOINT
        return slot * 2 + if (near) 0 else 1
    }

    /** Halfway between the two lane constants: which side of it a car sits decides its lane. */
    private const val LANE_MIDPOINT =
        (SceneSpace.ROAD_LANE_NEAR_Y_FRACTION + SceneSpace.ROAD_LANE_FAR_Y_FRACTION) / 2f

    /** Same for every theme and every one of the 5 structural customizable categories -- see the
     * class doc. [seasonalDecorationCandidates] uses this same constant for its own categories. */
    const val CANDIDATES_PER_CATEGORY = 10

    /**
     * How far to the side of its house a parasol stands, as a fraction of the ground tile. Houses and parasols share
     * the slot grid (`(i + 0.5) / n`, jittered ±0.035) and the depth bands, so a parasol generated on its own would
     * stand in its house's doorway, and half a slot of phase still sits inside the jitters' sum: placed from its
     * house, its separation is one no jitter undoes (`GroundPilesAndParasolsTest`).
     */
    const val PARASOL_SIDE_OFFSET = 0.055f

    /**
     * The two lanes cars drive in, as fractions of screen height at the wheels' ground line: [SceneSpace]'s, since the
     * road, its lanes, the houses' ground and the pavement are one geometry with one owner. They sit about a car's
     * drawn height apart ([SceneSpace.CANONICAL_LANE_SPACING_FRACTION]), so the road reads as a road.
     */
    const val CAR_LANE_FAR_Y_FRACTION = SceneSpace.ROAD_LANE_FAR_Y_FRACTION
    const val CAR_LANE_NEAR_Y_FRACTION = SceneSpace.ROAD_LANE_NEAR_Y_FRACTION

    /**
     * How the cars of one lane are spaced around their loop. `progress` runs from -0.3 to 1.3 and wraps by subtracting
     * that 1.6 span, so a head start survives the wrap; the nth of a lane's [CAR_SLOTS_PER_LANE] cars starts one nth of
     * the span behind the one ahead, several car lengths at any screen size. `startDelaySeconds`, despite its name, is
     * this progress offset, subtracted from `progress` at construction.
     */
    const val CAR_LOOP_ENTRY_PROGRESS = 0.3f
    const val CAR_LOOP_SPAN = 1.6f
    const val CAR_SLOTS_PER_LANE = CANDIDATES_PER_CATEGORY / 2

    fun layoutFor(themeId: String, accentColor: Int): SceneObjectLayout {
        if (RandomSceneGenerator.isRandomThemeId(themeId)) {
            return RandomSceneGenerator.generateLayout(themeId, accentColor) + seasonalDecorationCandidates(themeId)
        }
        CustomThemeRegistry.overrideLayoutFor(themeId)?.let { return withSeasonalDecorations(it, themeId) }
        val builtinLayout = builtinLayoutFor(themeId, accentColor)
        if (builtinLayout != null) return builtinLayout + seasonalDecorationCandidates(themeId)
        CustomThemeRegistry.customEntry(themeId)?.let { return withSeasonalDecorations(it.layout, themeId) }
        return SceneObjectLayout(staticObjects = emptyList(), cars = emptyList())
    }

    /**
     * The cars the **built-in** [themeId] defines, ignoring any saved override: what [repairBuiltInOverrides] compares
     * a damaged override with. Empty for an id that is not a built-in, so a standalone custom theme is never repaired.
     */
    internal fun builtinCarsFor(themeId: String, accentColor: Int): List<CarObject> =
        builtinLayoutFor(themeId, accentColor)?.cars.orEmpty()

    // --- Uniform candidate generation for the 5 structural categories ----------------------

    /**
     * [CANDIDATES_PER_CATEGORY] candidate slots for one category, spread along the tile with a little jitter so they
     * do not read as a grid, the same for the same seed; candidate `i` stands at [candidateDepth] in [depthRange].
     */
    private fun generateStaticCandidates(
        type: SceneObjectType,
        seed: Int,
        depthRange: ClosedFloatingPointRange<Float>,
    ): List<StaticSceneObject> {
        val rnd = Random(seed)
        val n = CANDIDATES_PER_CATEGORY
        return (0 until n).map { i ->
            val slot = (i + 0.5f) / n
            val jitter = (rnd.nextFloat() - 0.5f) * (1f / n) * 0.7f
            val tileFractionX = (slot + jitter).coerceIn(0.015f, 0.985f)
            StaticSceneObject(type, depthFraction = candidateDepth(i, n, depthRange), tileFractionX = tileFractionX, scale = sizeVariation(rnd))
        }
    }

    /** The depth of candidate [i] of [n] in [depthRange]: [generateStaticCandidates]'s, and [isSeasonalDecorationCandidate]'s. */
    private fun candidateDepth(i: Int, n: Int, depthRange: ClosedFloatingPointRange<Float>): Float =
        depthRange.start + (i / (n - 1).toFloat().coerceAtLeast(1f)) * (depthRange.endInclusive - depthRange.start)

    /**
     * One candidate's size variation, from one spread for every category ("some houses are bigger than others" says the
     * same whatever the category); how big the category is comes from [SceneSpace.SceneVariant].
     */
    private fun sizeVariation(rnd: Random): Float {
        val spread = SceneSpace.SIZE_VARIATION_SPREAD
        return (1f - spread / 2f + rnd.nextFloat() * spread).coerceAtLeast(SceneSpace.MIN_SIZE_VARIATION)
    }

    /**
     * One parasol per house, [PARASOL_SIDE_OFFSET] to its side, sides alternating by index so a street does not lean
     * one way; only the size roll is the parasol's own.
     */
    private fun parasolsBeside(houses: List<StaticSceneObject>, seed: Int): List<StaticSceneObject> {
        val rnd = Random(seed)
        return houses.mapIndexed { i, house ->
            val side = if (i % 2 == 0) 1f else -1f
            val x = (house.tileFractionX + side * PARASOL_SIDE_OFFSET).coerceIn(0.015f, 0.985f)
            StaticSceneObject(
                SceneObjectType.PARASOL,
                depthFraction = house.depthFraction,
                tileFractionX = x,
                scale = sizeVariation(rnd),
            )
        }
    }

    /**
     * [generateStaticCandidates] in two depth bands: even candidates in [backRange], near the hill crest, odd ones in
     * [frontRange], near the road, since one narrow band reads as flat. Density thinning keeps each candidate by its own
     * hash ([SceneCustomization.keepCandidate]), so both bands drain at the same expected rate.
     */
    private fun generateSplitStaticCandidates(
        type: SceneObjectType,
        seed: Int,
        backRange: ClosedFloatingPointRange<Float>,
        frontRange: ClosedFloatingPointRange<Float>,
    ): List<StaticSceneObject> {
        val rnd = Random(seed)
        val n = CANDIDATES_PER_CATEGORY
        val halfCount = (n / 2).coerceAtLeast(1)
        return (0 until n).map { i ->
            val slot = (i + 0.5f) / n
            val jitter = (rnd.nextFloat() - 0.5f) * (1f / n) * 0.7f
            val tileFractionX = (slot + jitter).coerceIn(0.015f, 0.985f)
            val range = if (i % 2 == 0) backRange else frontRange
            val withinBand = (i / 2) / (halfCount - 1).toFloat().coerceAtLeast(1f)
            val depthFraction = range.start + withinBand * (range.endInclusive - range.start)
            StaticSceneObject(type, depthFraction = depthFraction, tileFractionX = tileFractionX, scale = sizeVariation(rnd))
        }
    }

    /** The car candidates: five a lane, uniformly spaced around the loop, the lane by the candidate's index so every
     * theme offers both. **A car's lane decides its direction** (the near lane runs right), so no two cars meet
     * head-on in one lane; whether both lanes are populated is the density's ([CarSelection]: its bottom is one car).
     * The type is dealt per index from [CAR_TYPE_DEAL], so a candidate always renders the same type. */
    internal fun generateCarCandidates(seed: Int, accentColor: Int): List<CarObject> {
        val types = capSpecialsToOnePerType(dealtCarTypes(seed, CANDIDATES_PER_CATEGORY))
        return (0 until CANDIDATES_PER_CATEGORY).map { i ->
            val nearLane = i % 2 == 0
            val slot = i / 2 // position of this car within its own lane's queue
            CarObject(
                laneYFraction = if (nearLane) CAR_LANE_NEAR_Y_FRACTION else CAR_LANE_FAR_Y_FRACTION,
                // One speed per lane, so nothing in a queue drives through the car ahead; the far
                // lane's is derived from its depth.
                speedFraction = if (nearLane) SceneSpace.CAR_SPEED_NEAR else SceneSpace.CAR_SPEED_FAR,
                // Evenly spaced, and kept so by the wrap ([CarRuntime], `SceneObjectRenderer.update`).
                startDelaySeconds = CAR_LOOP_ENTRY_PROGRESS + CAR_LOOP_SPAN * slot / CAR_SLOTS_PER_LANE,
                color = accentColor, // live-recolored by SceneCustomization anyway (PLAIN only)
                // The near lane drives right; `reverse` sends a car left.
                reverse = !nearLane,
                type = types[i],
            )
        }
    }

    /**
     * At most one vehicle of each special type in a candidate set: two fire engines on one road read as a defect.
     * [CAR_TYPE_DEAL] holds one of each, so on a built-in theme this is a tested guarantee with nothing to do;
     * `RandomSceneGenerator` rolls its types independently and calls it, and a saved custom theme is capped on load
     * (`CustomThemeData`'s `migrateDuplicateSpecialVehicles`). Capping the *set* at generation is stronger than capping
     * the screen and needs no per-frame state. A surplus special becomes [CarType.PLAIN] and keeps its slot, lane, speed
     * and colour; the first occurrence is kept.
     */
    internal fun capSpecialsToOnePerType(rolled: List<CarType>): List<CarType> {
        val seen = HashSet<CarType>()
        return rolled.map { type ->
            when {
                type == CarType.PLAIN -> type
                seen.add(type) -> type
                else -> CarType.PLAIN
            }
        }
    }

    /**
     * The ten types one theme's road carries, dealt across its ten slots: **the declared weights, dealt exactly
     * rather than rolled**. A 70/10/10/10 roll per candidate is a hand of ten that leaves some themes without two of
     * the types; seven plain and one of each special is 70/10/10/10 on ten slots ([SeededBalance.drawCount]'s
     * argument, where the fraction comes out whole), so every theme shows all four.
     */
    private val CAR_TYPE_DEAL: List<CarType> = listOf(
        CarType.PLAIN, CarType.PLAIN, CarType.PLAIN, CarType.PLAIN,
        CarType.PLAIN, CarType.PLAIN, CarType.PLAIN,
        CarType.POLICE, CarType.TAXI, CarType.FIRE_TRUCK,
    )

    /** Which entry of [CAR_TYPE_DEAL] each of [slotCount] candidates gets. See [SilhouetteDeal]
     *  for why the expression is `(rotation + rank) % size` and not a rank alone. */
    private fun dealtCarTypes(seed: Int, slotCount: Int): List<CarType> {
        val size = CAR_TYPE_DEAL.size
        val rotation = (CandidateNoise.value(seed, 0, CH_CAR_TYPE_ROTATION) * size)
            .toInt().coerceIn(0, size - 1)
        return (0 until slotCount).map { slot ->
            val rank = SeededBalance.rankOf(
                seed, CH_CAR_TYPE, slot, slotCount, addressStride = 1, addressOffset = 0,
            )
            CAR_TYPE_DEAL[(rotation + rank) % size]
        }
    }

    /** Which slot gets which vehicle type. */
    private const val CH_CAR_TYPE = 52

    /** Where the type multiset starts, so a shorter inventory still varies by theme. */
    private const val CH_CAR_TYPE_ROTATION = 53

    /** The 5-category candidate set of a built-in street; [treeType] plants palms on Beach and Desert under the same
     * "Trees" settings ([SceneCustomization]'s `configFor`). The depth ranges use the **whole** 0..1 band, the road
     * standing below it by construction ([SceneSpace]):
     * - SKYSCRAPER: 0.0-0.80, towers below [SceneSpace.BUILDING_TOWER_MAX_DEPTH] and shop fronts above it;
     * - HOUSE: 0.28-0.48 and 0.62-0.95 ([generateSplitStaticCandidates]), each PARASOL at its house's depth;
     * - treeType: 0.18-1.0, past both house bands, so trees interleave with houses at every depth.
     */
    private fun uniformCandidates(
        themeId: String,
        accentColor: Int,
        treeType: SceneObjectType = SceneObjectType.TREE,
    ): SceneObjectLayout {
        val staticObjects = SHOP_PLAN[themeId] ?: separateShopFrontages(streetBeforeShops(themeId, treeType))
        val cars = generateCarCandidates(themeId.hashCode() + 6, accentColor)
        return SceneObjectLayout(staticObjects = staticObjects, cars = cars)
    }

    /**
     * Every static object of a generated street, the shops still at their generated slots. Silhouettes are dealt
     * here, where the seed and the slot index are both in hand ([SilhouetteDeal]); the buildings' in
     * [buildingCandidates], after it settles which is a tower and which a place, since the catalogue follows from
     * that. The plan moves only `tileFractionX`, so it cannot disturb a deal.
     */
    internal fun streetBeforeShops(themeId: String, treeType: SceneObjectType): List<StaticSceneObject> {
        val seed = themeId.hashCode()
        val houses = SilhouetteDeal.dealtAcross(
            generateSplitStaticCandidates(SceneObjectType.HOUSE, seed + 1, 0.28f..0.48f, 0.62f..0.95f),
            seed,
        )
        return buildingCandidates(seed) +
            houses +
            generateStaticCandidates(treeType, seed + 5, 0.18f..1.0f) +
            parasolsBeside(houses, seed + 4)
    }

    // ---- Shop-front visibility ----------------------------------------------------------------------------------
    //
    // A frontage nobody can see is artwork nobody gets, and slot arithmetic per category keeps nothing nearer off a
    // shop: this pass does, at layout time, by moving the *shop* sideways (never its depth, which is what makes it a
    // shop). It measures the union of everything nearer over the ENTIRE front, crowns, trunks, poles and canopies, and
    // no vertical member may cross the front: a single occluder's share, or the lower half alone, passes a trunk
    // planted over a door. Twin storefronts are not a matter of spacing (an object is on screen for more than half
    // the tile's scroll): which building stands in a place is the street's ([StorefrontStreet]).

    /** The reference viewport the occlusion geometry is evaluated at, 1080x2340. The overlap
     * *fractions* barely move with aspect ratio, but they are not exactly invariant, so the
     * number in the rule names its frame. */
    private const val REF_SCREEN_W = 1080f
    private const val REF_SCREEN_H = 2340f

    /** Occlusion of a shop's WHOLE front (the union of every nearer box) above which it is moved, and the margin a
     * move aims under. */
    private const val SHOP_MAX_OCCLUSION = 0.40f
    private const val SHOP_TARGET_OCCLUSION = 0.32f

    /** How far past the shop's front edge a stepped-aside tree is pushed, in reference px. */
    private const val TREE_STEP_ASIDE_MARGIN_PX = 4f

    /**
     * The building candidates of a generated street, each with its silhouette dealt: **five places among the houses
     * and five towers** (`DESIGN_NOTES.md` decision 60). Six of the ten stand in the shop band (0.356 to 0.800);
     * [singleShopPerVariant] keeps the middle one of each third (0.444, 0.622, 0.800) and lays the other three on the
     * skyline at 0.05, 0.15 and 0.25, and [placesReturned] stands two of those back (0.356, 0.711). The school
     * third's 0.533 stays the tower at 0.15, the middle, whose depth is the same whether one or three interleave. The
     * crowns are dealt over the seven tower slots of the three-place street, so every tower has the height, colour
     * and crown it has there: the same skyline less two towers.
     *
     * A place's own silhouette, dealt over the five from its row's catalogue ([SilhouetteDeal.catalogueFor]), is what
     * a saved copy keeps; the built-in street takes its six buildings' identity from [StorefrontStreet.identities].
     */
    private fun buildingCandidates(seed: Int): List<StaticSceneObject> {
        val generated = generateStaticCandidates(SceneObjectType.SKYSCRAPER, seed + 2, 0.0f..0.80f)
        val threePlaces = singleShopPerVariant(generated)
        // The crowns, dealt over the seven tower slots of the three-place street.
        val crowns = SilhouetteDeal.dealtAcross(threePlaces, seed)
        val fivePlaces = placesReturned(generated, threePlaces)
        val places = SilhouetteDeal.dealtAcross(fivePlaces.filter { isShop(it) }, seed)
        var next = 0
        return fivePlaces.mapIndexed { i, o -> if (isShop(o)) places[next++] else crowns[i] }
    }

    /**
     * [threePlaces] with the candidates it laid on the skyline back where they were generated, all but the middle
     * one, which keeps its tower depth. See [buildingCandidates].
     */
    private fun placesReturned(generated: List<StaticSceneObject>, threePlaces: List<StaticSceneObject>): List<StaticSceneObject> {
        val laidOnSkyline = generated.indices.filter { isShop(generated[it]) && !isShop(threePlaces[it]) }
        val staysTower = laidOnSkyline.getOrNull(laidOnSkyline.size / 2)
        return threePlaces.mapIndexed { i, o -> if (i in laidOnSkyline && i != staysTower) generated[i] else o }
    }

    /**
     * Keeps one shop-band candidate per row of the size table, the first step of [buildingCandidates]: the
     * depth-middle one of each row's band, since a band's extremes are the smallest and largest the street offers and
     * a shop should read as an ordinary building of its row. The others keep slot, x, size roll and category (so the
     * sliders govern the same ten candidates) and move to tower depths, interleaved between the generated ones.
     * **A row with no band here is never drawn**, and a census of the scene does not show its absence.
     */
    private fun singleShopPerVariant(candidates: List<StaticSceneObject>): List<StaticSceneObject> {
        val shopIndices = candidates.indices.filter {
            candidates[it].depthFraction >= SceneSpace.BUILDING_TOWER_MAX_DEPTH
        }
        fun middleByDepth(indices: List<Int>): Int? =
            indices.sortedBy { candidates[it].depthFraction }.let { if (it.isEmpty()) null else it[it.size / 2] }
        fun inBand(from: Float, to: Float): Int? = middleByDepth(
            shopIndices.filter { candidates[it].depthFraction >= from && candidates[it].depthFraction < to },
        )
        val kept = setOfNotNull(
            inBand(SceneSpace.BUILDING_TOWER_MAX_DEPTH, SceneSpace.RESTAURANT_MAX_DEPTH),
            inBand(SceneSpace.RESTAURANT_MAX_DEPTH, SceneSpace.SCHOOL_MAX_DEPTH),
            inBand(SceneSpace.SCHOOL_MAX_DEPTH, Float.POSITIVE_INFINITY),
        )
        val demotedCount = shopIndices.size - kept.size
        var rank = 0
        return candidates.mapIndexed { i, c ->
            if (i !in shopIndices || i in kept) c
            else c.copy(
                depthFraction = SceneSpace.BUILDING_TOWER_MAX_DEPTH * (2 * rank++ + 1) /
                    (2f * demotedCount.coerceAtLeast(1)),
            )
        }
    }

    /**
     * Half-width of a variant, in its own sprite units, as the occlusion pass models it: for the houses and the tower
     * the wider side of the ink over every piece of every slot, since a narrower model accepts fronts more covered
     * than it computes.
     */
    private fun halfWidthUnits(variant: SceneSpace.SceneVariant): Float = when (variant) {
        SceneSpace.SceneVariant.HOUSE_SMALL -> 58.7f
        SceneSpace.SceneVariant.HOUSE_LARGE -> 77.1f
        SceneSpace.SceneVariant.TOWER -> 35.9f
        // **A place is as wide as the widest of the six that may stand in it** ([StorefrontStreet]), at the
        // place's metre; [frontRects] keeps the six apart where the covered share is measured.
        SceneSpace.SceneVariant.RESTAURANT, SceneSpace.SceneVariant.SCHOOL, SceneSpace.SceneVariant.BAR ->
            Storefront.ALL.maxOf { it.halfWidthUnits } * placeMetre(variant)
        // `tree_canopy`'s ink, blitted at -50, spans x -50..51: the wider side governs a symmetric reach.
        SceneSpace.SceneVariant.TREE -> 51f
        // The palm leans: `CROWN_HALF_WIDTH` (28) is its crown's half-width about the crown's own centre, 7 units
        // right of the foot, and the lean side's ink reaches 34 units right of it (SpriteOccluderTable.PALM_CROWN).
        // A palm the step-aside parked to a shop's LEFT would overhang the front by up to 6 units less the 4 px
        // margin; the step-aside runs on no built-in layout and no saved theme reaches it, so model the lean here
        // before a layout makes it run.
        SceneSpace.SceneVariant.PALM_TREE -> PalmSpriteLayout.CROWN_HALF_WIDTH
        else -> 0f
    }

    internal fun isShop(o: StaticSceneObject) =
        o.type == SceneObjectType.SKYSCRAPER && o.depthFraction >= SceneSpace.BUILDING_TOWER_MAX_DEPTH

    /**
     * A place's metre: `spriteUnitsTall / unitsTall` of its own family, the scale any building standing in it is
     * drawn at (every piece of the neighbourhood is one metre a unit).
     */
    private fun placeMetre(variant: SceneSpace.SceneVariant): Float =
        variant.spriteUnitsTall / NeighbourhoodTable.FAMILIES.getValue(variant).unitsTall

    /**
     * Moves every shop to the nearest position where its front is neither covered past [SHOP_MAX_OCCLUSION] by the
     * union of every nearer box ([occluderBoxes]) nor crossed by a trunk or pole, which splits the door however
     * little area it costs. Extents come from [SceneObjectRenderer.effectiveScaleFor], as the renderer's do.
     *  - **Nearest shop first**: a shop settles against what is nearer, so the deeper ones route around it.
     *  - **Positions are scanned, not nudged**: probes step out from the shop's slot a hundredth of the tile at a
     *    time, alternating sides, and the first uncrossed one under [SHOP_TARGET_OCCLUSION] wins (nudging away from
     *    the worst occluder oscillates between two houses); failing that, the nearest under the ceiling.
     *  - **Trees step aside as a last resort**: the shop parks at its least-covered probe and every tree touching
     *    its front moves clear. Houses never move, and parasols belong to their houses, so the probes route around
     *    their poles. Up to five sweeps, until one moves nothing.
     */
    private fun separateShopFrontages(objects: List<StaticSceneObject>): List<StaticSceneObject> {
        val out = objects.toMutableList()
        val byDepthNearestFirst = out.indices.sortedByDescending { out[it].depthFraction }
        repeat(5) {
            var moved = false
            for (i in byDepthNearestFirst) {
                val shop = out[i]
                if (!isShop(shop)) continue
                if (frontCoverage(out, shop) <= SHOP_MAX_OCCLUSION && !frontCrossed(out, shop)) continue
                var best = shop.tileFractionX
                var bestCoverage = Float.MAX_VALUE
                var found = false
                var ceilingSlot: Float? = null
                for (step in 1..50) {
                    for (sign in intArrayOf(1, -1)) {
                        val candidate = shop.copy(
                            tileFractionX = (shop.tileFractionX + sign * step * 0.01f).mod(1f),
                        )
                        if (frontCrossed(out, candidate)) continue
                        val covered = frontCoverage(out, candidate)
                        if (covered <= SHOP_TARGET_OCCLUSION) {
                            out[i] = candidate
                            found = true
                            break
                        }
                        if (covered <= SHOP_MAX_OCCLUSION && ceilingSlot == null) {
                            ceilingSlot = candidate.tileFractionX
                        }
                        if (covered < bestCoverage) {
                            bestCoverage = covered
                            best = candidate.tileFractionX
                        }
                    }
                    if (found) break
                }
                if (!found && ceilingSlot != null) {
                    out[i] = shop.copy(tileFractionX = ceilingSlot)
                    found = true
                }
                if (!found) {
                    out[i] = shop.copy(tileFractionX = best)
                    val parked = out[i]
                    val f = frontRect(parked)
                    val cx = (f[0] + f[2]) / 2f
                    for (j in out.indices) {
                        val o = out[j]
                        if (o.type != SceneObjectType.TREE && o.type != SceneObjectType.PALM_TREE) continue
                        if (o.depthFraction <= parked.depthFraction) continue
                        val touches = occluderBoxes(o).any { box ->
                            val b = wrapBoxToward(box, cx)
                            b[2] > f[0] && b[0] < f[2] && b[3] > f[1] && b[1] < f[3]
                        }
                        if (!touches) continue
                        val tile = REF_SCREEN_W * 2f
                        val reach = halfWidthUnits(SceneObjectRenderer.variantFor(o)) *
                            SceneObjectRenderer.effectiveScaleFor(o, REF_SCREEN_H)
                        val rawDx = (o.tileFractionX * tile - cx).mod(tile)
                        val dx = if (rawDx > tile / 2f) rawDx - tile else rawDx
                        val target = if (dx >= 0f) f[2] + reach + TREE_STEP_ASIDE_MARGIN_PX
                        else f[0] - reach - TREE_STEP_ASIDE_MARGIN_PX
                        out[j] = o.copy(tileFractionX = (target / tile).mod(1f))
                    }
                }
                moved = true
            }
            if (!moved) return out
        }
        return out
    }

    // ---- The plan of the places ---------------------------------------------------------------------------------
    //
    // Every built-in street has one skeleton: candidate i at slot (i + 0.5) / 10 and at a depth growing with i, so the
    // near trees, houses and parasols stand at the tile's right, where a trunk or a pole crosses a place's front at
    // nearly every spot. Places put only where the street leaves them clear would leave two thirds of a screen with
    // no building among the houses. So the plan lays the five **along the whole tile**, one in each fifth, at a phase
    // each street chooses and within [SLOT_REACHES] of its slot, and moves the trees and parasols in a place's way --
    // **never their number, colour, autumn tone, kind or whether they stand at the theme's density** ([relocated]); a
    // parasol goes to the other side of its house, and houses never move. A street moves as few as its places allow.
    //
    // The twelve streets share out the frontage: no more than four themes put a place of one row of the size table
    // in one spot. Themes take turns, each choosing its best against the others' choices, until none moves
    // (`StorefrontFrontVisibilityTest`, `ShopFrontVisibilityTest`).
    //
    // **The plan is computed when the table is generated, not at start**: it depends only on constants of this code,
    // and would hold the main thread for hundreds of milliseconds. [ShopPlanTable] holds its answer; `ShopPlanTableTest`
    // runs [planStreets] on the JVM, fails unless the table is its output to the bit, and writes the table it should be.

    /** Every built-in street with the plan applied ([ShopPlanTable]): theme id to its static objects. */
    private val SHOP_PLAN: Map<String, List<StaticSceneObject>> by lazy { shopPlanFromTable() }

    private fun shopPlanFromTable(): Map<String, List<StaticSceneObject>> =
        BUILT_IN_STREETS.associate { (id, tree) -> id to streetPlanned(streetBeforeShops(id, tree), ShopPlanTable.planFor(id)) }

    /**
     * What the plan does to one built-in street: where its places stand, deepest first ([placeX]), and where each tree
     * or parasol it moved stands ([movedIndex] in the street as [streetBeforeShops] generates it, [movedX]).
     */
    internal class StreetPlan(val placeX: FloatArray, val movedIndex: IntArray, val movedX: FloatArray)

    /**
     * [objects] with the plan applied, or with its places parked by [separateShopFrontages] when [plan] is
     * null: a street the plan could not place, which the table leaves out.
     */
    private fun streetPlanned(objects: List<StaticSceneObject>, plan: StreetPlan?): List<StaticSceneObject> {
        if (plan == null) return separateShopFrontages(objects)
        val shops = shopIndicesDeepestFirst(objects)
        check(shops.size == plan.placeX.size) { "ShopPlanTable has ${plan.placeX.size} places for a street with ${shops.size}" }
        return objects.mapIndexed { i, o ->
            val j = shops.indexOf(i)
            val m = plan.movedIndex.indexOf(i)
            when {
                j >= 0 -> o.copy(tileFractionX = plan.placeX[j])
                m >= 0 -> o.copy(tileFractionX = plan.movedX[m])
                else -> o
            }
        }
    }

    /** The streets [planStreets] would give, applied: what the app draws from the table. For `ShopPlanTableTest`. */
    internal fun plannedStreets(): Map<String, List<StaticSceneObject>> {
        val plans = planStreets()
        return BUILT_IN_STREETS.associate { (id, tree) -> id to streetPlanned(streetBeforeShops(id, tree), plans[id]) }
    }

    /** The indices of [objects]' places, deepest first: the order the plan places them in. */
    private fun shopIndicesDeepestFirst(objects: List<StaticSceneObject>): List<Int> =
        objects.indices.filter { isShop(objects[it]) }.sortedBy { objects[it].depthFraction }

    /** A place's half-width as a share of the tile, at the reference viewport: the widest of the six. */
    internal fun halfTile(o: StaticSceneObject): Float =
        halfWidthUnits(SceneObjectRenderer.variantFor(o)) * SceneObjectRenderer.effectiveScaleFor(o, REF_SCREEN_H) / (REF_SCREEN_W * 2f)

    private fun circularGap(a: Float, b: Float): Float {
        val d = (a - b).mod(1f)
        return minOf(d, 1f - d)
    }

    /** The grid the plan places on: a two-hundredth of the tile, 7 px of the BV6600's 1 440. */
    private const val PLAN_STEPS = 200

    /**
     * The most of a front the plan lets be covered: a point under [SHOP_MAX_OCCLUSION], so a front stays inside the
     * rule however another measurement samples it (`StorefrontFrontVisibilityTest`'s grid reads a few tenths more).
     */
    private const val PLAN_MAX_OCCLUSION = SHOP_MAX_OCCLUSION - 0.01f

    /**
     * How far from its slot a place may stand, in [PLAN_STEPS]: 0.04 of the tile either side, wider for a street whose
     * places cannot all stand that near (New Year's two large front houses close its right third to every place).
     */
    private val SLOT_REACHES = intArrayOf(8, 12, 16, 20)

    /** The most streets whose places of one row of the size table may cover one spot: the twelve share out the frontage. */
    private const val MAX_STREETS_A_SPOT = 4

    /**
     * The tallest screen, height over width, the street is planned for: 21:9 and a little more. The scene scales with
     * the screen's height and the tile is two screens wide, so a building's share of the tile grows with this.
     */
    private const val TALLEST_SCREEN = 2.4f

    /** How far the plan looks for a moved tree's new spot, in steps of [MOVE_STEP] either side: 0.3 of the tile. */
    private const val MOVE_REACH = 3000
    private const val MOVE_STEP = 0.0001f

    /**
     * The period of a candidate's density draw along the tile ([densityFraction] is the fraction of `x * 7919` plus the
     * depth's term): an object moved by whole periods keeps its draw. A moved tree's spot is fine-tuned by them, at most
     * [TONE_REACH] either side, to keep its autumn tone ([SceneObjectRenderer.fallToneOf]).
     */
    private const val DENSITY_PERIOD = 1f / 7919f
    private const val TONE_REACH = 200

    /**
     * How far a moved tree's or parasol's density draw ([densityFraction]) may go from its own: at the theme's density
     * it stands as it did, and at any other unless the slider is within this of its draw.
     */
    private const val MOVE_DENSITY_SLACK = 0.02

    /** How many of its best solutions a street keeps at each phase and order to answer the others with. */
    private const val SHORTLIST = 2

    /** How many spots each place tries at each slot: its best, by moves, tier and distance. */
    private const val SLOT_OPTIONS = 3

    private fun isMovable(o: StaticSceneObject): Boolean =
        o.type == SceneObjectType.TREE || o.type == SceneObjectType.PALM_TREE || o.type == SceneObjectType.PARASOL

    private fun overlaps(b: FloatArray, f: FloatArray): Boolean = b[2] > f[0] && b[0] < f[2] && b[3] > f[1] && b[1] < f[3]

    /**
     * Both species a street's tree slots can stand as, the one it plants and the one the Palms switch puts in their
     * places (`DESIGN_NOTES.md` decisions 37 and 39): a place is planned clear of both.
     */
    private fun speciesOf(street: List<StaticSceneObject>, tree: SceneObjectType): List<List<StaticSceneObject>> {
        val other = if (tree == SceneObjectType.TREE) SceneObjectType.PALM_TREE else SceneObjectType.TREE
        return listOf(street, street.map { if (it.type == tree) it.copy(type = other) else it })
    }

    /**
     * What one object puts in front of a place, for the plan: [occluderBoxes], but a house as tall as the renderer's
     * deal draws it ([NeighbourhoodComposer.deal]), since a house dealt a storey more covers more of what is behind.
     */
    private fun planOccluderBoxes(o: StaticSceneObject): List<FloatArray> {
        if (o.type != SceneObjectType.HOUSE) return occluderBoxes(o)
        val v = SceneObjectRenderer.variantFor(o)
        val family = NeighbourhoodTable.FAMILIES.getValue(v)
        val deal = NeighbourhoodComposer.Deal()
        NeighbourhoodComposer.deal(family, o, deal)
        var top = 0f
        for (i in 0 until deal.size) {
            val placed = deal[i]
            for (part in placed.piece.parts) {
                if (part.res == 0 || part.role == PartRole.SNOW || part.role == PartRole.LAMP || part.role == PartRole.OCCUPANTS) continue
                top = maxOf(top, -(placed.baseY + part.y))
            }
        }
        val s = SceneObjectRenderer.effectiveScaleFor(o, REF_SCREEN_H)
        val g = REF_SCREEN_H * SceneSpace.groundYFraction(o.depthFraction)
        val x = o.tileFractionX * REF_SCREEN_W * 2f
        val tall = maxOf(v.spriteUnitsTall, top * v.spriteUnitsTall / family.unitsTall)
        return listOf(floatArrayOf(x - halfWidthUnits(v) * s, g - tall * s, x + halfWidthUnits(v) * s, g))
    }

    /**
     * Whether place [place], standing at [x], keeps both rules for all six of its fronts in each of [species] ([speciesOf]
     * the street, each object where the plan has it, [absent] left out): no trunk or pole across it, at most [limit]
     * covered by everything nearer. [limit] is [PLAN_MAX_OCCLUSION] unless the caller asks for the rule itself.
     */
    private fun placeClear(
        species: List<List<StaticSceneObject>>,
        place: Int,
        x: Float,
        absent: Set<Int> = emptySet(),
        limit: Float = PLAN_MAX_OCCLUSION,
    ): Boolean {
        for (v in species) {
            val shop = v[place].copy(tileFractionX = x)
            val env = frontRect(shop)
            val cx = (env[0] + env[2]) / 2f
            val boxes = ArrayList<FloatArray>()
            for (i in v.indices) {
                val o = v[i]
                if (i == place || i in absent || o.depthFraction <= shop.depthFraction) continue
                val m = verticalMemberBox(o)
                if (m != null && overlaps(wrapBoxToward(m, cx), env)) return false
                boxes += planOccluderBoxes(o)
            }
            if (frontRects(shop).maxOf { coveredShare(it, boxes) } > limit) return false
        }
        return true
    }

    /**
     * Place [place] of a street at grid step [k], against the street alone (no other place): null where the houses
     * alone cover a front past [PLAN_MAX_OCCLUSION]; otherwise the trees and parasols that would have to move -- every
     * trunk or pole across its front in either species, then, while a front is covered past [SHOP_TARGET_OCCLUSION],
     * the one covering most of it, down to the target only with [toTarget] -- and whether the front is left in the
     * ceiling tier (covered between the two).
     */
    private fun spotFor(species: List<List<StaticSceneObject>>, place: Int, k: Int, toTarget: Boolean): Spot? {
        val x = k / PLAN_STEPS.toFloat()
        val blockers = java.util.TreeSet<Int>()
        for (v in species) {
            val shop = v[place].copy(tileFractionX = x)
            val env = frontRect(shop)
            val cx = (env[0] + env[2]) / 2f
            for (i in v.indices) {
                val o = v[i]
                if (!isMovable(o) || o.depthFraction <= shop.depthFraction) continue
                val m = verticalMemberBox(o) ?: continue
                if (overlaps(wrapBoxToward(m, cx), env)) blockers += i
            }
        }
        var ceiling = false
        for (v in species) {
            val shop = v[place].copy(tileFractionX = x)
            val fronts = frontRects(shop)
            val nearer = v.indices.filter { !isShop(v[it]) && v[it].depthFraction > shop.depthFraction }
            val fixed = nearer.filter { !isMovable(v[it]) }.flatMap { planOccluderBoxes(v[it]) }
            if (fronts.maxOf { coveredShare(it, fixed) } > PLAN_MAX_OCCLUSION) return null
            while (true) {
                val stay = nearer.filter { isMovable(v[it]) && it !in blockers }
                val boxes = fixed + stay.flatMap { planOccluderBoxes(v[it]) }
                val worst = fronts.maxBy { coveredShare(it, boxes) }
                val covered = coveredShare(worst, boxes)
                if (covered <= SHOP_TARGET_OCCLUSION) break
                if (!toTarget) {
                    if (covered <= PLAN_MAX_OCCLUSION) { ceiling = true; break }
                }
                val next = stay.maxByOrNull { coveredShare(worst, planOccluderBoxes(v[it])) }
                if (next == null || coveredShare(worst, planOccluderBoxes(v[next])) == 0f) {
                    if (covered <= PLAN_MAX_OCCLUSION) { ceiling = true; break }
                    return null
                }
                blockers += next
            }
        }
        return Spot(k, blockers.toIntArray(), ceiling)
    }

    /** One grid step a place may take: the trees and parasols it moves, and whether it is left in the ceiling tier. */
    private class Spot(val k: Int, val moves: IntArray, val ceiling: Boolean)

    /** Every way place [place] can stand at each grid step: with the fewest moves, and moved down to the target tier. */
    private fun placeSpots(species: List<List<StaticSceneObject>>, place: Int): Array<List<Spot>> =
        Array(PLAN_STEPS) { k ->
            val few = spotFor(species, place, k, toTarget = false)
            val clear = spotFor(species, place, k, toTarget = true)
            listOfNotNull(few, clear.takeIf { it != null && (few == null || !it.moves.contentEquals(few.moves)) })
        }

    /** One street's solution: its places' grid steps, deepest first, and what it moves and how. */
    private class Solution(val k: IntArray, val moves: IntArray, val ceilings: Int, val widestGap: Int, val offSlot: Int)

    /**
     * The plan itself, computed from scratch (see the section's comment): for each built-in street where its places
     * stand and which trees and parasols move where, or null for a street it could not place, whose places
     * [separateShopFrontages] then parks. Not called by the app, which reads its output from [ShopPlanTable].
     */
    internal fun planStreets(): Map<String, StreetPlan?> {
        class Street(
            val id: String,
            val tree: SceneObjectType,
            val objects: List<StaticSceneObject>,
            val places: List<Int>,
            val rows: List<SceneSpace.SceneVariant>,
            val half: FloatArray,
            val shortlist: List<Solution>,
        )
        val streets = BUILT_IN_STREETS.map { (id, tree) ->
            val objects = streetBeforeShops(id, tree)
            val places = shopIndicesDeepestFirst(objects)
            val species = speciesOf(objects, tree)
            val spots = places.map { placeSpots(species, it) }
            val half = FloatArray(places.size) { halfTile(objects[places[it]]) }
            // The slots' reach widens only for a street whose places cannot all stand within the narrowest.
            val shortlist = SLOT_REACHES.asSequence().map { shortlistFor(objects, places, half, spots, it) }.firstOrNull { it.isNotEmpty() }.orEmpty()
            Street(id, tree, objects, places, places.map { SceneObjectRenderer.variantFor(objects[it]) }, half, shortlist)
        }
        val n = streets.size
        /**
         * The most streets, this one included and the others at their choices [chosen], with a place of [row] over one
         * point of the front from [x] - [half] to [x] + [half]: what [MAX_STREETS_A_SPOT] bounds.
         */
        fun crowd(chosen: Array<Solution?>, t: Int, row: SceneSpace.SceneVariant, x: Float, half: Float): Int {
            val edges = ArrayList<Pair<Float, Int>>()
            for (u in 0 until n) {
                if (u == t) continue
                val s = chosen[u] ?: continue
                val st = streets[u]
                for (j in st.places.indices) {
                    if (st.rows[j] != row) continue
                    val raw = (s.k[j] / PLAN_STEPS.toFloat() - x).mod(1f)
                    val dx = if (raw > 0.5f) raw - 1f else raw
                    val lo = maxOf(dx - st.half[j], -half)
                    val hi = minOf(dx + st.half[j], half)
                    if (hi > lo) { edges += lo to 1; edges += hi to -1 }
                }
            }
            edges.sortWith(compareBy({ it.first }, { it.second }))
            var depth = 0
            var most = 0
            for ((_, d) in edges) { depth += d; most = maxOf(most, depth) }
            return most + 1
        }
        fun crowdOf(chosen: Array<Solution?>, t: Int, s: Solution): Int =
            streets[t].places.indices.maxOf { j -> crowd(chosen, t, streets[t].rows[j], s.k[j] / PLAN_STEPS.toFloat(), streets[t].half[j]) }
        // A solution's moves are tried only when it is about to be chosen, and the answer kept.
        val plans = Array(n) { HashMap<Solution, StreetPlan?>() }
        fun planOf(t: Int, s: Solution): StreetPlan? = plans[t].getOrPut(s) {
            val st = streets[t]
            relocated(st.id, st.tree, st.objects, st.places, FloatArray(st.places.size) { s.k[it] / PLAN_STEPS.toFloat() }, s.moves)
        }
        /**
         * The streets choosing in [order]: each in turn takes the first of its shortlist whose moves can be made and
         * that keeps every spot within [MAX_STREETS_A_SPOT], or else the least crowded, in rounds until nobody changes
         * (at most nine). Who chooses first gets the cheapest spots, so the plan tries several orders and keeps the one
         * that moves the fewest in all.
         */
        fun choose(order: List<Int>): Array<Solution?> {
            val chosen = arrayOfNulls<Solution>(n)
            for (round in 0..8) {
                var changed = 0
                for (t in order) {
                    var best: Solution? = null
                    var bestCrowd = Int.MAX_VALUE
                    for (s in streets[t].shortlist) {
                        val crowd = crowdOf(chosen, t, s)
                        if (crowd >= bestCrowd || planOf(t, s) == null) continue
                        best = s
                        bestCrowd = crowd
                        if (crowd <= MAX_STREETS_A_SPOT) break
                    }
                    if (best !== chosen[t]) { changed++; chosen[t] = best }
                }
                if (changed == 0) break
            }
            return chosen
        }
        fun cost(chosen: Array<Solution?>): IntArray = intArrayOf(
            chosen.count { it == null },
            (0 until n).maxOf { t -> chosen[t]?.let { crowdOf(chosen, t, it) } ?: 0 }.coerceAtLeast(MAX_STREETS_A_SPOT),
            chosen.sumOf { it?.moves?.size ?: 0 },
            chosen.sumOf { it?.ceilings ?: 0 },
        )
        val forward = (0 until n).toList()
        val orders = (0 until n).flatMap { r -> listOf(forward.drop(r) + forward.take(r), (forward.drop(r) + forward.take(r)).reversed()) }
        var best: Array<Solution?>? = null
        var bestCost: IntArray? = null
        for (order in orders) {
            val chosen = choose(order)
            val c = cost(chosen)
            val old = bestCost
            if (old == null || (0 until c.size).firstOrNull { c[it] != old[it] }?.let { c[it] < old[it] } == true) {
                best = chosen
                bestCost = c
            }
        }
        return streets.mapIndexed { t, st -> st.id to best!![t]?.let { planOf(t, it) } }.toMap()
    }

    /**
     * A street's best solutions, best first: the five slots a fifth of the tile apart at every phase, every
     * assignment of the places to them, each place within [reach] of its slot, clear of the nearer places'
     * bodies, and never five of them on one screen ([fiveOnAScreen]). Best: fewest trees and parasols moved,
     * then fewest fronts left in the ceiling tier, then the narrowest widest gap between two places, then
     * nearest the slots. [SHORTLIST] of each phase and each order of the places along the tile, so that a
     * street can answer the others' choices from anywhere along it.
     */
    private fun shortlistFor(
        objects: List<StaticSceneObject>,
        places: List<Int>,
        half: FloatArray,
        spots: List<Array<List<Spot>>>,
        reach: Int,
    ): List<Solution> {
        val p = places.size
        val slotStep = PLAN_STEPS / p
        val stacked = Array(p) { j -> BooleanArray(p) { i -> overlapsFrontAt(objects[places[j]], objects[places[i]]) } }
        val order = Comparator<Solution> { a, b -> if (better(a, b)) -1 else if (better(b, a)) 1 else 0 }
        val perms = permutations(p)
        val kept = Array(slotStep * perms.size) { java.util.TreeSet(order) }
        val chosenK = IntArray(p)
        val chosenSpot = arrayOfNulls<Spot>(p)
        // Each place's options at each slot: its [SLOT_OPTIONS] best spots within reach, fewest moves first.
        val options = Array(p) { j ->
            Array(PLAN_STEPS) { slot ->
                (-reach..reach)
                    .flatMap { d -> spots[j][Math.floorMod(slot + d, PLAN_STEPS)].map { it to kotlin.math.abs(d) } }
                    .sortedWith(compareBy({ it.first.moves.size }, { if (it.first.ceiling) 1 else 0 }, { it.second }, { it.first.k }))
                    .take(SLOT_OPTIONS)
                    .map { it.first }
            }
        }
        for (phase in 0 until slotStep) for ((pi, perm) in perms.withIndex()) {
            val bucket = kept[phase * perms.size + pi]
            fun extend(j: Int) {
                if (j == p) {
                    if (fiveOnAScreen(chosenK, half)) return
                    val moves = java.util.TreeSet<Int>()
                    for (s in chosenSpot) for (m in s!!.moves) moves += m
                    val sorted = chosenK.sorted()
                    var widest = 0
                    for (a in sorted.indices) {
                        val gap = if (a + 1 < p) sorted[a + 1] - sorted[a] else sorted[0] + PLAN_STEPS - sorted[a]
                        widest = maxOf(widest, gap)
                    }
                    var off = 0
                    for (i in 0 until p) {
                        val slot = (phase + perm[i] * slotStep) % PLAN_STEPS
                        val d = Math.floorMod(chosenK[i] - slot, PLAN_STEPS)
                        off += minOf(d, PLAN_STEPS - d)
                    }
                    bucket += Solution(chosenK.copyOf(), moves.toIntArray(), chosenSpot.count { it!!.ceiling }, widest, off)
                    if (bucket.size > SHORTLIST) bucket.pollLast()
                    return
                }
                val slot = phase + perm[j] * slotStep
                for (spot in options[j][slot % PLAN_STEPS]) {
                    val k = spot.k
                    var fits = true
                    for (i in 0 until j) {
                        if (chosenK[i] == k) { fits = false; break }
                        val gap = circularGap(k / PLAN_STEPS.toFloat(), chosenK[i] / PLAN_STEPS.toFloat())
                        if ((stacked[j][i] || stacked[i][j]) && gap < half[j] + half[i]) { fits = false; break }
                    }
                    if (!fits) continue
                    chosenK[j] = k
                    chosenSpot[j] = spot
                    extend(j + 1)
                }
            }
            extend(0)
        }
        return kept.flatMap { it }.sortedWith(order)
    }

    /**
     * Whether a screen can show all of a street's places at once (grid steps [k], half-widths [half]): whether some
     * gap between two neighbours, with their widest halves beside it, leaves the rest within half the tile on the
     * [TALLEST_SCREEN]. The street never puts the same building twice in four places in a row ([StorefrontStreet]),
     * so a screen must never show five.
     */
    internal fun fiveOnAScreen(k: IntArray, half: FloatArray): Boolean {
        val stretch = TALLEST_SCREEN / (REF_SCREEN_H / REF_SCREEN_W)
        val order = k.indices.sortedBy { k[it] }
        for (a in order.indices) {
            val left = order[a]
            val right = order[(a + 1) % order.size]
            val gap = Math.floorMod(k[right] - k[left], PLAN_STEPS) / PLAN_STEPS.toFloat()
            // The others, from [right] round to [left], span the tile less this gap.
            if (1f - gap - (half[left] + half[right]) * stretch < 0.5f) return true
        }
        return false
    }

    /** Whether solution [a] ranks before [b]: fewer moved, fewer in the ceiling tier, narrower widest gap, nearer the slots, then by the grid steps. */
    private fun better(a: Solution, b: Solution): Boolean {
        if (a.moves.size != b.moves.size) return a.moves.size < b.moves.size
        if (a.ceilings != b.ceilings) return a.ceilings < b.ceilings
        if (a.widestGap != b.widestGap) return a.widestGap < b.widestGap
        if (a.offSlot != b.offSlot) return a.offSlot < b.offSlot
        for (i in a.k.indices) if (a.k[i] != b.k[i]) return a.k[i] < b.k[i]
        for (i in 0 until minOf(a.moves.size, b.moves.size)) if (a.moves[i] != b.moves[i]) return a.moves[i] < b.moves[i]
        return false
    }

    /** Whether the nearer place's body, standing where the deeper's front is, would overlap it: a property of the two depths. */
    private fun overlapsFrontAt(nearer: StaticSceneObject, deeper: StaticSceneObject): Boolean {
        if (nearer.depthFraction <= deeper.depthFraction) return false
        val f = frontRect(deeper.copy(tileFractionX = 0f))
        val b = frontRect(nearer.copy(tileFractionX = 0f))
        return b[3] > f[1] && b[1] < f[3]
    }

    private fun permutations(n: Int): List<IntArray> {
        val out = ArrayList<IntArray>()
        fun build(prefix: IntArray, used: BooleanArray) {
            if (prefix.size == n) { out += prefix; return }
            for (i in 0 until n) if (!used[i]) { used[i] = true; build(prefix + i, used); used[i] = false }
        }
        build(IntArray(0), BooleanArray(n))
        return out
    }

    /**
     * The street [id] with its places at [placeX] and the trees and parasols [moves] moved, as a [StreetPlan], or null
     * where one cannot be moved or a place is left outside the rules. A tree moves to the nearest spot, a [MOVE_STEP] at
     * a time up to [MOVE_REACH], where it is still the tree it was -- standing at the theme's density as it did, its
     * draw within [MOVE_DENSITY_SLACK], the same colour, autumn tone ([SceneObjectRenderer.fallToneOf]) and fir or not
     * ([SceneObjectRenderer.isFirSeed]) -- and where no place is crossed or covered past the rule because of it. A
     * parasol moves to the other side of its own house, the same way. Then every place is measured again.
     */
    private fun relocated(
        id: String,
        tree: SceneObjectType,
        objects: List<StaticSceneObject>,
        places: List<Int>,
        placeX: FloatArray,
        moves: IntArray,
    ): StreetPlan? {
        val c = defaultCustomizationFor(id)
        val street = objects.toMutableList()
        for (j in places.indices) street[places[j]] = street[places[j]].copy(tileFractionX = placeX[j])
        val houses = objects.filter { it.type == SceneObjectType.HOUSE }
        val parasols = objects.indices.filter { objects[it].type == SceneObjectType.PARASOL }
        val movedX = FloatArray(moves.size)
        for ((m, i) in moves.withIndex()) {
            val o = objects[i]
            fun sameObject(x: Float, tone: Boolean = false): Boolean {
                val moved = o.copy(tileFractionX = x)
                if (c.keepCandidate(moved) != c.keepCandidate(o)) return false
                if (kotlin.math.abs(densityFraction(moved) - densityFraction(o)) > MOVE_DENSITY_SLACK) return false
                if (colourCoinOf(moved) != colourCoinOf(o)) return false
                if (o.type != SceneObjectType.PARASOL &&
                    SceneObjectRenderer.isFirSeed(SceneObjectRenderer.idleSeedOf(moved)) != SceneObjectRenderer.isFirSeed(SceneObjectRenderer.idleSeedOf(o))
                ) return false
                if (tone && o.type != SceneObjectType.PARASOL &&
                    SceneObjectRenderer.fallToneOf(SceneObjectRenderer.idleSeedOf(moved)) != SceneObjectRenderer.fallToneOf(SceneObjectRenderer.idleSeedOf(o))
                ) return false
                return true
            }
            // The movers not yet placed are left out: each one is placed against the street as it will be.
            val pending = moves.drop(m + 1).toSet()
            fun clearOfPlaces(x: Float): Boolean {
                street[i] = o.copy(tileFractionX = x)
                val species = speciesOf(street, tree)
                return places.indices.all { j -> placeClear(species, places[j], placeX[j], pending) }
            }
            val found: Float? = if (o.type == SceneObjectType.PARASOL) {
                val house = houses[parasols.indexOf(i)]
                val side = if ((o.tileFractionX - house.tileFractionX).mod(1f) < 0.5f) -1f else 1f
                (0..40).asSequence().flatMap { e -> if (e == 0) sequenceOf(0) else sequenceOf(e, -e) }
                    .map { e -> (house.tileFractionX + side * (PARASOL_SIDE_OFFSET + e * MOVE_STEP)).mod(1f) }
                    .firstOrNull { x -> sameObject(x) && clearOfPlaces(x) }
            } else {
                (1..MOVE_REACH).asSequence().flatMap { s -> sequenceOf(s, -s) }
                    .map { s -> (o.tileFractionX + s * MOVE_STEP).mod(1f) }
                    .firstOrNull { x -> sameObject(x) && clearOfPlaces(x) }
                    // And its autumn tone: from there, the nearest spot a whole period of its density draw
                    // away ([DENSITY_PERIOD]) where it wears the tone it wore too.
                    ?.let { x ->
                        (0..TONE_REACH).asSequence().flatMap { k -> if (k == 0) sequenceOf(0) else sequenceOf(k, -k) }
                            .map { k -> (x + k * DENSITY_PERIOD).mod(1f) }
                            .firstOrNull { y -> sameObject(y, tone = true) && clearOfPlaces(y) }
                    }
            }
            if (found == null) return null
            street[i] = o.copy(tileFractionX = found)
            movedX[m] = found
        }
        val species = speciesOf(street, tree)
        if (!places.indices.all { j -> placeClear(species, places[j], placeX[j]) }) return null
        return StreetPlan(placeX, moves.copyOf(), movedX)
    }

    // ---- The places of a saved street ---------------------------------------------------------------------------
    //
    // A saved theme, a theme file and a backup keep the street as it was saved, places included; any of the six
    // buildings stands in any place, and a place stored for a narrower building can have a trunk across a wider one's
    // front (`StorefrontFrontVisibilityTest`). That place moves, only as far as it must, and nothing else of the theme
    // changes ([SceneObjectLayout.placesAsSaved]).

    /** How far a saved street's place is moved at most, in [SAVED_PLACE_STEP]s either side: half the tile. */
    private const val SAVED_PLACE_REACH = 500

    /** A saved street's place moves a thousandth of the tile at a time: 1.4 px of the BV6600. */
    private const val SAVED_PLACE_STEP = 0.001f

    /**
     * [objects], a saved street as stored, with each place among the houses where all six buildings keep both rules in
     * both species ([placeClear], [SHOP_MAX_OCCLUSION]): a place that does stays; one that does not moves to the
     * nearest spot, a [SAVED_PLACE_STEP] at a time, that does by the plan's margin ([PLAN_MAX_OCCLUSION]) clear of the
     * other places, nearest place first. Nothing else moves, nor a place no spot clears; the same list back when
     * nothing moves. Run on every load (`sceneObjectLayoutFromJson`), with the same answer every time; the layout
     * remembers where the theme stored a moved place ([SceneObjectLayout.placesAsSaved]).
     */
    internal fun placesClearedOnSavedStreet(objects: List<StaticSceneObject>): List<StaticSceneObject> {
        val places = objects.indices.filter { isShop(objects[it]) }.sortedByDescending { objects[it].depthFraction }
        if (places.isEmpty()) return objects
        val plantsPalms = objects.any { it.type == SceneObjectType.PALM_TREE }
        val street = objects.toMutableList()
        var moved = false
        for (i in places) {
            val species = listOf(
                street,
                street.map {
                    when {
                        plantsPalms && it.type == SceneObjectType.PALM_TREE -> it.copy(type = SceneObjectType.TREE)
                        !plantsPalms && it.type == SceneObjectType.TREE -> it.copy(type = SceneObjectType.PALM_TREE)
                        else -> it
                    }
                },
            )
            val x0 = street[i].tileFractionX
            // Inside the rule as it stands: left where it is. Moved: where the plan would put it, a point inside.
            if (placeClear(species, i, x0, limit = SHOP_MAX_OCCLUSION)) continue
            val half = halfTile(street[i])
            val found = (1..SAVED_PLACE_REACH).asSequence()
                .flatMap { k -> sequenceOf(k, -k) }
                .map { k -> (x0 + k * SAVED_PLACE_STEP).mod(1f) }
                .firstOrNull { x ->
                    places.none { j -> j != i && circularGap(x, street[j].tileFractionX) < half + halfTile(street[j]) } &&
                        placeClear(species, i, x)
                }
            if (found != null) {
                street[i] = street[i].copy(tileFractionX = found)
                moved = true
            }
        }
        return if (moved) street else objects
    }

    /**
     * The depth every generated layout stands its school at (candidate 7 of ten, 0.6222), derived from the
     * generator's arithmetic as [singleShopPerVariant] chooses it, so a school added by [missingSchoolFor] has a
     * generated school's size and row.
     */
    internal val GENERATED_SCHOOL_DEPTH: Float = run {
        val n = CANDIDATES_PER_CATEGORY
        val inThird = (0 until n)
            .map { i -> 0.0f + (i / (n - 1).toFloat().coerceAtLeast(1f)) * (0.80f - 0.0f) }
            .filter { it >= SceneSpace.RESTAURANT_MAX_DEPTH && it < SceneSpace.SCHOOL_MAX_DEPTH }
            .sorted()
        inThird[inThird.size / 2]
    }

    /**
     * The school a saved layout lacks, or null when it needs none: when the layout has a building in the school's third
     * already, or no shop at all (a lone school would be the only commercial building of a scene that had none).
     *
     * A saved theme holds the objects that were **standing** (`snapshotEntry`), so a street saved with a shop band of
     * two halves has a restaurant at 0.4444, a bar at 0.7111 and nothing in the school's third; every tower it holds is
     * one the user sees, and promoting one would take it off their skyline. So **one** school is added, and nothing
     * saved moves, changes colour or changes drawing. It stands at [GENERATED_SCHOOL_DEPTH], silhouette 0 (the
     * school catalogue's only entry) and size 1 (the mean of the generator's roll, the one value that is not a
     * choice), at the hundredth-of-a-tile probe around the midpoint of the restaurant and the bar that keeps
     * [separateShopFrontages]' rules and:
     *  - moves no tree aside (the trees are the user's): failing every probe, it parks at its least-covered one;
     *  - covers no more of a saved shop's front than is covered already (the 40 % ceiling would let it half hide one);
     *  - hides least and is hidden least: its own front's covered share plus the most of any saved house's body it
     *    covers, since the frontage rules protect only shops. Ties go to the probe nearer the midpoint.
     */
    internal fun missingSchoolFor(objects: List<StaticSceneObject>): StaticSceneObject? {
        val commercial = objects.filter { it.type == SceneObjectType.SKYSCRAPER }
        if (commercial.any {
                it.depthFraction >= SceneSpace.RESTAURANT_MAX_DEPTH && it.depthFraction < SceneSpace.SCHOOL_MAX_DEPTH
            }
        ) {
            return null
        }
        val shops = commercial.filter { isShop(it) }
        if (shops.isEmpty()) return null
        val restaurant = shops.firstOrNull { it.depthFraction < SceneSpace.RESTAURANT_MAX_DEPTH }
        val bar = shops.firstOrNull { it.depthFraction >= SceneSpace.SCHOOL_MAX_DEPTH }
        val start = if (restaurant != null && bar != null) {
            // The midpoint of the shorter way round: the tile wraps, so 0.9 and 0.1 are 0.2 apart.
            val gap = (bar.tileFractionX - restaurant.tileFractionX).mod(1f)
            if (gap <= 0.5f) (restaurant.tileFractionX + gap / 2f).mod(1f)
            else (restaurant.tileFractionX - (1f - gap) / 2f).mod(1f)
        } else {
            ((restaurant ?: bar)!!.tileFractionX + 0.5f).mod(1f)
        }
        val base = StaticSceneObject(
            SceneObjectType.SKYSCRAPER,
            depthFraction = GENERATED_SCHOOL_DEPTH,
            tileFractionX = start,
            scale = 1f,
            silhouette = 0,
        )
        val deeperShops = shops.filter { it.depthFraction < base.depthFraction }
        val before = deeperShops.map { frontCoverage(objects, it) }
        fun sparesTheShops(candidate: StaticSceneObject): Boolean {
            val withSchool = objects + candidate
            return deeperShops.indices.all { k -> frontCoverage(withSchool, deeperShops[k]) <= before[k] + 1e-4f }
        }
        val housesBehind = objects.filter { it.type == SceneObjectType.HOUSE && it.depthFraction < base.depthFraction }
        fun housesHidden(candidate: StaticSceneObject): Float =
            housesBehind.maxOfOrNull { bodyCoveredBy(it, candidate) } ?: 0f
        var best: StaticSceneObject? = null
        var bestScore = Float.MAX_VALUE
        var leastCovered: StaticSceneObject? = null
        var leastCoverage = Float.MAX_VALUE
        for (step in 0..50) {
            for (sign in if (step == 0) intArrayOf(1) else intArrayOf(1, -1)) {
                val candidate = base.copy(tileFractionX = (start + sign * step * 0.01f).mod(1f))
                if (frontCrossed(objects, candidate) || !sparesTheShops(candidate)) continue
                val covered = frontCoverage(objects, candidate)
                if (covered < leastCoverage) {
                    leastCoverage = covered
                    leastCovered = candidate
                }
                if (covered > SHOP_MAX_OCCLUSION) continue
                // Strictly less, so the probe nearer the midpoint keeps a tie.
                val score = covered + housesHidden(candidate)
                if (score < bestScore) {
                    bestScore = score
                    best = candidate
                }
            }
        }
        return best ?: leastCovered ?: base
    }

    /** The share of [behind]'s drawn body that [front]'s body covers, nearest wrapped copy. */
    private fun bodyCoveredBy(behind: StaticSceneObject, front: StaticSceneObject): Float {
        val a = frontRect(behind)
        val b = wrapBoxToward(frontRect(front), (a[0] + a[2]) / 2f)
        val w = minOf(a[2], b[2]) - maxOf(a[0], b[0])
        val h = minOf(a[3], b[3]) - maxOf(a[1], b[1])
        if (w <= 0f || h <= 0f) return 0f
        return w * h / ((a[2] - a[0]) * (a[3] - a[1]))
    }

    /** The shop's full drawn front at the reference viewport: (left, top, right, bottom) px. */
    internal fun frontRect(shop: StaticSceneObject): FloatArray {
        val s = SceneObjectRenderer.effectiveScaleFor(shop, REF_SCREEN_H)
        val g = REF_SCREEN_H * SceneSpace.groundYFraction(shop.depthFraction)
        val x = shop.tileFractionX * REF_SCREEN_W * 2f
        val v = SceneObjectRenderer.variantFor(shop)
        val half = halfWidthUnits(v) * s
        // A place: as tall as the tallest of the six at its metre, as wide as the widest ([halfWidthUnits]).
        // Each front reaches the ground, so a trunk crosses this box exactly when it crosses one of the six.
        val tall = if (isShop(shop)) Storefront.ALL.maxOf { it.family.unitsTall } * placeMetre(v) else v.spriteUnitsTall
        return floatArrayOf(x - half, g - tall * s, x + half, g)
    }

    /**
     * The fronts [shop] can show: for a place, two per building that may stand in it, its own width at the place's
     * metre and as tall as its family declares and as it draws ([Storefront.inkTallUnits]), so a front covered for any
     * building or over its ink is measured covered; anything else its one [frontRect].
     */
    internal fun frontRects(shop: StaticSceneObject): List<FloatArray> {
        if (!isShop(shop)) return listOf(frontRect(shop))
        val s = SceneObjectRenderer.effectiveScaleFor(shop, REF_SCREEN_H)
        val g = REF_SCREEN_H * SceneSpace.groundYFraction(shop.depthFraction)
        val x = shop.tileFractionX * REF_SCREEN_W * 2f
        val metre = placeMetre(SceneObjectRenderer.variantFor(shop))
        return Storefront.ALL.flatMap { b ->
            val half = b.halfWidthUnits * metre * s
            listOf(
                floatArrayOf(x - half, g - b.family.unitsTall * metre * s, x + half, g),
                floatArrayOf(x - half, g - b.inkTallUnits * metre * s, x + half, g),
            )
        }
    }

    /**
     * What one object puts between the viewer and anything behind it, as (left, top, right, bottom) boxes at the
     * reference viewport: a building its body; a tree, a palm and a parasol a crown ([crownBoxes], an ink profile) AND
     * a vertical member -- the tree's 32x62-unit forked stem at TreeSpriteLayout.TRUNK_X/Y, the palm's leaning spindle,
     * the parasol's pole. Each number is the shipped sprite's content box at its blit origin; a trunk's is a strict
     * reading of a shape that narrows above its foot ([verticalMemberBox]), not a claim about where the ink is.
     */
    internal fun occluderBoxes(o: StaticSceneObject): List<FloatArray> {
        val v = SceneObjectRenderer.variantFor(o)
        val s = SceneObjectRenderer.effectiveScaleFor(o, REF_SCREEN_H)
        val g = REF_SCREEN_H * SceneSpace.groundYFraction(o.depthFraction)
        val x = o.tileFractionX * REF_SCREEN_W * 2f
        return when (v) {
            SceneSpace.SceneVariant.TREE ->
                crownBoxes(SpriteOccluderTable.TREE_CROWN, x, g, s) +
                    floatArrayOf(x - 16f * s, g - 62f * s, x + 16f * s, g)
            // The crown is blitted at (-21,-82) on a 56x48-unit canvas; the trunk leans (its 21-unit canvas
            // at -8, the bark inside it at x 0.67..20), so its box is not symmetric about the foot.
            SceneSpace.SceneVariant.PALM_TREE ->
                crownBoxes(SpriteOccluderTable.PALM_CROWN, x, g, s) +
                    floatArrayOf(x - 8f * s, g - 58f * s, x + 13f * s, g)
            SceneSpace.SceneVariant.PARASOL ->
                crownBoxes(SpriteOccluderTable.PARASOL_FAN, x, g, s) +
                    floatArrayOf(x - 2.5f * s, g - 50f * s, x + 2.5f * s, g)
            SceneSpace.SceneVariant.HOUSE_SMALL, SceneSpace.SceneVariant.HOUSE_LARGE,
            SceneSpace.SceneVariant.TOWER,
            -> listOf(floatArrayOf(x - halfWidthUnits(v) * s, g - v.spriteUnitsTall * s, x + halfWidthUnits(v) * s, g))
            // A place among the houses puts in front the widest and tallest of the six: whoever
            // stands there, nothing larger can.
            SceneSpace.SceneVariant.RESTAURANT, SceneSpace.SceneVariant.BAR, SceneSpace.SceneVariant.SCHOOL,
            -> listOf(frontRect(o))
            else -> emptyList()
        }
    }

    /**
     * The occluding rectangles of one foliage family, at the reference viewport.
     *
     * **A crown's canvas is not its ink**: a palm fan is about half ink, and a box of its whole 56x48-unit canvas
     * would declare the air solid and move a shop away from a frontage plainly legible behind it. [SpriteOccluderTable]
     * carries the ink profile, measured off the shipped PNGs by `tools/assets/build_occluder_table.py`; the *model* is
     * here, and written a second time in `ShopFrontVisibilityTest` rather than imported.
     *
     * The box is the drawing's content (not the two pixels of transparent guard `normalize` leaves for the filtered
     * blit), then no wider than its widest row and no taller than its tallest column, about the content's centre.
     * **The extremes, not the area**: a model by area shrinks a *filled* shape as hard as a sparse one (13 % off each
     * side of the oak's solid canopy) and moves shops for no visible reason, while a box touched along a full row and a
     * full column -- every convex filled silhouette -- is left unchanged and only a fan, whose rows are never full,
     * narrows. It is also the largest box, the least able to declare a shop visible that is not.
     *
     * A family is every drawing that may stand in that place (three palm crowns, two oak crowns), the crown being
     * picked at draw time from the user's flags; the boxes are a LIST, as [frontCoverage] takes an exact union and a
     * bounding box would hand back air. **Not modelled**: the crown's sway (up to four degrees), and a fir standing in a
     * leafy tree's place.
     */
    private fun crownBoxes(
        family: List<SpriteOccluderTable.InkBox>,
        x: Float,
        g: Float,
        s: Float,
    ): List<FloatArray> = family.map { ink ->
        val cx = (ink.contentLeft + ink.contentRight) / 2f
        val cy = (ink.contentTop + ink.contentBottom) / 2f
        val halfW = (ink.contentRight - ink.contentLeft) * ink.rowMax / 2f
        val halfH = (ink.contentBottom - ink.contentTop) * ink.columnMax / 2f
        floatArrayOf(
            x + (cx - halfW) * s,
            g + (cy - halfH) * s,
            x + (cx + halfW) * s,
            g + (cy + halfH) * s,
        )
    }

    /** The trunk or pole alone -- the box whose mere crossing of a front is the defect. */
    internal fun verticalMemberBox(o: StaticSceneObject): FloatArray? {
        val v = SceneObjectRenderer.variantFor(o)
        val s = SceneObjectRenderer.effectiveScaleFor(o, REF_SCREEN_H)
        val g = REF_SCREEN_H * SceneSpace.groundYFraction(o.depthFraction)
        val x = o.tileFractionX * REF_SCREEN_W * 2f
        return when (v) {
            // The forked stem is 32 units across at its flared foot and 62 tall: the full rectangle, though the V
            // narrows above the foot, because a box that under-reports the foot would let a trunk park on a doorway.
            SceneSpace.SceneVariant.TREE -> floatArrayOf(x - 16f * s, g - 62f * s, x + 16f * s, g)
            // The leaning trunk's own canvas, x -8..13 at this origin: the same strict reading as the TREE's.
            SceneSpace.SceneVariant.PALM_TREE -> floatArrayOf(x - 8f * s, g - 58f * s, x + 13f * s, g)
            SceneSpace.SceneVariant.PARASOL -> floatArrayOf(x - 2.5f * s, g - 50f * s, x + 2.5f * s, g)
            else -> null
        }
    }

    /** The nearest wrapped copy of [box] relative to a front centred at [cx]. */
    internal fun wrapBoxToward(box: FloatArray, cx: Float): FloatArray {
        val tile = REF_SCREEN_W * 2f
        val boxCx = (box[0] + box[2]) / 2f
        val rawDx = (boxCx - cx).mod(tile)
        val dx = if (rawDx > tile / 2f) rawDx - tile else rawDx
        val shift = (cx + dx) - boxCx
        return floatArrayOf(box[0] + shift, box[1], box[2] + shift, box[3])
    }

    /** The covered share of the shop's whole front: exact area of the union of every nearer
     * occluding box, clipped to the front. Layout-time only -- never on the draw path. */
    private fun frontCoverage(objects: List<StaticSceneObject>, shop: StaticSceneObject): Float {
        val boxes = ArrayList<FloatArray>()
        for (o in objects) {
            if (o === shop || o.depthFraction <= shop.depthFraction) continue
            boxes.addAll(occluderBoxes(o))
        }
        return frontRects(shop).maxOf { f -> coveredShare(f, boxes) }
    }

    /**
     * The covered share of the front [f]: exact area of the union of [boxes] (each its nearest wrapped
     * copy) clipped to it. The worst of a place's fronts is its reading ([frontRects]).
     */
    internal fun coveredShare(f: FloatArray, boxes: List<FloatArray>): Float {
        val cx = (f[0] + f[2]) / 2f
        val clipped = ArrayList<FloatArray>()
        for (box in boxes) {
            val b = wrapBoxToward(box, cx)
            val l = maxOf(b[0], f[0])
            val t = maxOf(b[1], f[1])
            val r = minOf(b[2], f[2])
            val bo = minOf(b[3], f[3])
            if (r > l && bo > t) clipped.add(floatArrayOf(l, t, r, bo))
        }
        if (clipped.isEmpty()) return 0f
        return unionArea(clipped) / ((f[2] - f[0]) * (f[3] - f[1]))
    }

    /** Whether any nearer trunk or pole crosses the shop's front. */
    private fun frontCrossed(objects: List<StaticSceneObject>, shop: StaticSceneObject): Boolean {
        val f = frontRect(shop)
        val cx = (f[0] + f[2]) / 2f
        for (o in objects) {
            if (o === shop || o.depthFraction <= shop.depthFraction) continue
            val member = verticalMemberBox(o) ?: continue
            val b = wrapBoxToward(member, cx)
            if (b[2] > f[0] && b[0] < f[2] && b[3] > f[1] && b[1] < f[3]) return true
        }
        return false
    }

    /** Exact area of a union of axis-aligned boxes: x-sweep over edge slabs, y-interval merge
     * per slab. The box counts here are single digits, so O(n^2) is nothing at layout time. */
    private fun unionArea(boxes: List<FloatArray>): Float {
        val xs = boxes.flatMap { listOf(it[0], it[2]) }.distinct().sorted()
        var area = 0f
        for (k in 0 until xs.size - 1) {
            val x0 = xs[k]
            val x1 = xs[k + 1]
            if (x1 <= x0) continue
            val mid = (x0 + x1) / 2f
            val strips = boxes.filter { mid > it[0] && mid < it[2] }.sortedBy { it[1] }
            var covered = 0f
            var curTop = Float.NaN
            var curBottom = Float.NEGATIVE_INFINITY
            for (s in strips) {
                if (curTop.isNaN() || s[1] > curBottom) {
                    if (!curTop.isNaN()) covered += curBottom - curTop
                    curTop = s[1]
                    curBottom = s[3]
                } else if (s[3] > curBottom) {
                    curBottom = s[3]
                }
            }
            if (!curTop.isNaN()) covered += curBottom - curTop
            area += covered * (x1 - x0)
        }
        return area
    }

    /**
     * The seasonal decorations' candidates, generated from a theme's id like the 5 uniform categories and shown,
     * thinned and coloured by the "Seasons & decorations" settings ([SceneCustomization]), so any of them can stand in
     * any theme; the scarecrows on the snowmen's places. The smallest objects in the scene (a rabbit is half a metre
     * tall), so they stand in the near half of the band, where they are still large enough to read.
     */
    private fun seasonalDecorationCandidates(themeId: String): List<StaticSceneObject> {
        val seed = themeId.hashCode()
        val snowmen = generateStaticCandidates(SceneObjectType.SNOWMAN, seed + 101, SEASONAL_DECORATION_DEPTHS)
        return snowmen +
            generateStaticCandidates(SceneObjectType.GIFT, seed + 102, SEASONAL_DECORATION_DEPTHS) +
            generateStaticCandidates(SceneObjectType.PENGUIN, seed + 104, SEASONAL_DECORATION_DEPTHS) +
            generateStaticCandidates(SceneObjectType.BUNNY, seed + 105, SEASONAL_DECORATION_DEPTHS) +
            generateStaticCandidates(SceneObjectType.EASTER_EGG, seed + 106, SEASONAL_DECORATION_DEPTHS) +
            generateStaticCandidates(SceneObjectType.PUMPKIN, seed + 107, SEASONAL_DECORATION_DEPTHS) +
            scarecrowsAt(snowmen)
    }

    /** The band [seasonalDecorationCandidates] places the decorations across: the near half of the scene. */
    private val SEASONAL_DECORATION_DEPTHS = 0.45f..1.0f

    /** The six decorations [seasonalDecorationCandidates] deals from a theme's id; the scarecrows stand on the snowmen's. */
    private val DEALT_DECORATIONS = setOf(
        SceneObjectType.SNOWMAN, SceneObjectType.GIFT, SceneObjectType.PENGUIN,
        SceneObjectType.BUNNY, SceneObjectType.EASTER_EGG, SceneObjectType.PUMPKIN,
    )

    /**
     * **A saved layout with its seasonal decorations: those of the theme it was saved from** (`DESIGN_NOTES.md` decision
     * 89). A layout saved from a built-in theme carries some or all of the decoration candidates that built-in
     * deals, never the scarecrows ([asSaved]): the built-in is found by them, and its whole set is laid in their place --
     * so a theme saved over itself, under a new name or imported stands its decorations where the built-in does, as many,
     * the scarecrows on the same snowmen, and its sliders reach every candidate. A layout whose decorations come from no
     * built-in -- one saved from a random theme -- keeps what it carries and is given the kinds it lacks from its own id,
     * the scarecrows on its snowmen; one that carries none is given them all.
     */
    private fun withSeasonalDecorations(saved: SceneObjectLayout, themeId: String): SceneObjectLayout {
        val carried = saved.staticObjects.filter { isSeasonalDecorationCandidate(it) }
        if (carried.isEmpty()) return saved + seasonalDecorationCandidates(themeId)
        for (dealt in builtInDecorations) {
            if (dealt.containsAll(carried)) {
                return saved.copy(staticObjects = saved.staticObjects.filter { !isSeasonalDecorationCandidate(it) }) + dealt
            }
        }
        val kinds = carried.mapTo(HashSet()) { it.type }
        val missing = seasonalDecorationCandidates(themeId).filter { it.type != SceneObjectType.SCARECROW && it.type !in kinds }
        if (SceneObjectType.SCARECROW in kinds) return saved + missing
        val snowmen = (if (SceneObjectType.SNOWMAN in kinds) carried else missing).filter { it.type == SceneObjectType.SNOWMAN }
        return saved + (missing + scarecrowsAt(snowmen))
    }

    /** The seasonal decorations' candidates each built-in theme deals ([seasonalDecorationCandidates]), in its order. */
    private val builtInDecorations: List<List<StaticSceneObject>> by lazy {
        ThemeCatalog.ALL.map { seasonalDecorationCandidates(it.id) }
    }

    /**
     * **Whether [spec] is one of the seasonal decorations' candidates** [layoutFor] deals from a theme's id: a scarecrow,
     * or one of the six at one of the ten depths [seasonalDecorationCandidates] gives, to the bit. A saved layout carries
     * them, by which [withSeasonalDecorations] finds the built-in they came from. The decorations a random theme rolls
     * at depths of their own ([RandomSceneGenerator.generateLayout]) are part of its street, not candidates.
     */
    internal fun isSeasonalDecorationCandidate(spec: StaticSceneObject): Boolean {
        if (spec.type == SceneObjectType.SCARECROW) return true
        if (spec.type !in DEALT_DECORATIONS) return false
        val n = CANDIDATES_PER_CATEGORY
        for (i in 0 until n) {
            if (candidateDepth(i, n, SEASONAL_DECORATION_DEPTHS) == spec.depthFraction) return true
        }
        return false
    }

    /**
     * **The scarecrows stand in the snowmen's places** (`DESIGN_NOTES.md` decision 80): one candidate on each snowman
     * candidate, only the type changed. Which stand is their own coin ([keepCandidate]); where the place's snowman
     * stands too, the renderer draws the scarecrow to one side ([SceneObjectRenderer.SCARECROW_ASIDE_METRES]).
     */
    internal fun scarecrowsAt(snowmen: List<StaticSceneObject>): List<StaticSceneObject> =
        snowmen.map { it.copy(type = SceneObjectType.SCARECROW) }

    private operator fun SceneObjectLayout.plus(flavor: List<StaticSceneObject>): SceneObjectLayout =
        copy(staticObjects = staticObjects + flavor)

    /** Returns null (rather than an empty layout) for unknown ids, so [layoutFor] can tell the
     * difference between "not a built-in id" and "a built-in id with an intentionally empty scene". */
    private fun builtinLayoutFor(themeId: String, accentColor: Int): SceneObjectLayout? {
        val treeType = BUILT_IN_STREETS.firstOrNull { it.first == themeId }?.second ?: return null
        return uniformCandidates(themeId, accentColor, treeType)
    }

    /**
     * The twelve built-in streets, in the order the shop plan visits them, with the tree each plants: palms on Desert
     * and Beach (the type is the layout's, not the renderer's); Halloween's trees are bared by its flag.
     */
    internal val BUILT_IN_STREETS: List<Pair<String, SceneObjectType>> = listOf(
        "sunset" to SceneObjectType.TREE,
        "autumn" to SceneObjectType.TREE,
        "winter" to SceneObjectType.TREE,
        "desert" to SceneObjectType.PALM_TREE,
        "christmas" to SceneObjectType.TREE,
        "new_year" to SceneObjectType.TREE,
        "beach" to SceneObjectType.PALM_TREE,
        "city" to SceneObjectType.TREE,
        "tundra" to SceneObjectType.TREE,
        "easter" to SceneObjectType.TREE,
        "halloween" to SceneObjectType.TREE,
        "spring" to SceneObjectType.TREE,
    )
}
