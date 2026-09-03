package com.paperscrape.livewallpaper.engine

import kotlin.random.Random

/** The kind of object placed in the scene. Drives both drawing shape and reaction behavior. */
enum class SceneObjectType {
    CAR, HOUSE, TREE,
    // Seasonal / festive additions (step 2)
    SNOWMAN, GIFT, PALM_TREE, PARASOL, SKYSCRAPER, PENGUIN,
    // Easter theme additions (auto-theme-by-date feature)
    EASTER_EGG, BUNNY,
    // Standalone seasonal decorations, independent of any theme -- user-toggleable on any
    // theme via the "Seasonal Decorations" screen (see SceneCustomization + SettingsScreen).
    PUMPKIN,
}

/**
 * A stationary (but animated-in-place) object anchored to the hill's ground line at a continuous
 * depth.
 *
 * [depthFraction] (0..1, 0=farthest/smallest, 1=nearest/largest) replaces what used to be a
 * discrete `layer: Int` row index (0..8). Placing every object at its own continuous
 * fraction (`(index - rangeStart) / (rangeEnd - rangeStart)`, within whatever index sub-range
 * that category was given) rather than snapping to one of a fixed number of discrete slots, and
 * scale each one's size continuously too (`index / totalCount` there). PaperScrape's version
 * uses the *same* fraction for both -- the reference computes those as two independent fractions,
 * but for objects placed within one narrow category range they move together closely enough that
 * using one simplifies the whole placement pipeline without a visible difference. See
 * [SceneSpace.groundYFraction] and [SceneSpace.depthScale] for how this fraction becomes an
 * actual screen position and size.
 *
 * [tileFractionX] is the object's horizontal position expressed as a fraction (0..1) of the
 * object layer's own tiling width (screen width -- see [PaperRenderer.drawHillLayers] for why
 * this is deliberately narrower than the hill silhouette's own tile).
 *
 * [scale] is a **relative size variation around 1**, not a size. How big a house is supposed to be
 * is a property of houses and lives in [SceneSpace.SceneVariant]; this field only says whether
 * this particular one is a little larger or smaller than its neighbours. It used to carry the
 * category's entire base size as well, which meant two unrelated decisions shared one number and
 * neither could be read without the other -- and made two categories' sizes impossible to compare,
 * since each was expressed against its own sprite's arbitrary internal scale. Payloads written
 * before the split are converted by the schema 1 -> 2 migration in `CustomThemeData.kt`.
 */
data class StaticSceneObject(
    val type: SceneObjectType,
    val depthFraction: Float,
    val tileFractionX: Float,
    val scale: Float = 1f,
)

/** The 4 vehicle types that can appear on the road: plain car, police car, taxi, fire truck.
 * Only [PLAIN] is user-recolorable via the "Cars" category color pickers -- the other 3 use fixed,
 * real-world-associated colors, because a police car isn't "your theme's accent color", it's
 * black-and-white. Their sprites are drawn as finished, non-tintable art for that reason. */
/**
 * The vehicles on the road.
 *
 * [seatsTwo] is a property of the type rather than a list of exclusions at the call site, so a
 * vehicle added later gets the conservative answer by default rather than by somebody
 * remembering. rc2 called it `carriesPassengers` and it excluded the police car, because the
 * passenger could then be a child and a child in the back of a service vehicle reads as
 * something being wrong. rc5 seats two **adults** or nobody -- a child's frontal bust is too
 * wide across the shoulders to keep its pillar light, see
 * [SceneObjectRenderer.CAR_PASSENGER_X_UNITS] -- so the reason to exclude the police car is
 * gone: two officers in a patrol car is what a patrol car looks like. The fire engine keeps its
 * single seat, because its cab glass is 25 units and holds one head, not because of who is in
 * it.
 */
enum class CarType(val seatsTwo: Boolean) {
    PLAIN(true),
    TAXI(true),
    POLICE(true),
    FIRE_TRUCK(false),
}

/**
 * A car that drives continuously across the screen in its own independent loop,
 * unaffected by home-screen parallax scrolling (it's "alive" on the road, not part of the
 * static background) — this matches the classic "watch the cars drive by" wallpaper feel.
 */
data class CarObject(
    val laneYFraction: Float, // vertical position as a fraction of screen height
    val speedFraction: Float, // screen-widths per second
    val startDelaySeconds: Float,
    val color: Int,
    val reverse: Boolean = false,
    /** Defaults to PLAIN so existing saved custom themes (JSON predating this field) and every
     * other call site that doesn't care about vehicle variety keep working unchanged. */
    val type: CarType = CarType.PLAIN,
)

/** The full set of interactive/decorative objects that belong to one theme's scene. */
data class SceneObjectLayout(
    val staticObjects: List<StaticSceneObject>,
    val cars: List<CarObject>,
)

/**
 * Default per-theme object layouts.
 *
 * Design principle: every theme offers the *same* maximum customization range for the 5
 * structural, user-editable categories (houses, buildings, cars, umbrellas, trees) -- exactly
 * [CANDIDATES_PER_CATEGORY] candidate slots each, generated uniformly rather than hand-authored
 * per theme. Whether a theme ends up looking like a quiet village or a dense city is entirely
 * up to the user's density sliders in "Scene Objects", never baked into the theme itself.
 *
 * Seasonal decorations (snowmen, gifts, penguins, bunnies, Easter eggs, pumpkins) are
 * a *second*, independent customization surface -- "Seasonal Decorations", edited via
 * [SceneCustomization]'s snowmen/gifts/etc. fields -- generated the same uniform way by
 * [seasonalDecorationCandidates] but layered onto *every* theme in [layoutFor], not tied to any
 * one theme's identity. They default to off (see [SceneCustomization.DEFAULT]) so nothing
 * changes until a user opts in, but once they do, it's their choice which theme(s) it shows up
 * on -- pumpkins at Christmas or snowmen on the beach are both just a toggle away, not something
 * baked into which theme "owns" that decoration.
 */
object SceneObjectCatalog {

    /**
     * Forces a saved theme's traffic onto the lane pair the scene currently uses.
     *
     * **Lane position, speed, direction and loop slot are scene geometry, not theme
     * data.** Nothing in the app produces a car anywhere but the canonical lanes --
     * this catalog and `RandomSceneGenerator` both read [SceneSpace] -- so a stored
     * lane coordinate can only ever be a stale copy of a constant that has since
     * moved. It moved three times: v76.5 wrote 0.820/0.855, v76.6 0.818/0.846, v76.7
     * 0.834/0.862. A theme saved under any of those and reloaded now would drag the
     * road back to where it was, because [SceneObjectRenderer] derives the painted
     * strip from the layout's own lanes -- and a theme saved on v76.5 or v76.6 puts
     * that strip straight over the pavement, so the pedestrians walk on tarmac.
     *
     * The schema version cannot be what guards this. It records a change of *shape*,
     * and nothing about the shape changed: the field is still a float and still
     * parses. Bumping it catches the payloads written before the bump and nothing
     * after, so the next time a lane constant moves the same defect returns. Applying
     * the canonicalisation on **every** load removes the class of bug rather than the
     * instance: a stored lane coordinate can no longer be believed at all.
     *
     * What is preserved is what genuinely belongs to the theme -- how many cars there
     * are, their colours and their types. What is recomputed is everything the road
     * decides.
     *
     * Which lane a car goes to comes from where it sat within its *own* theme's
     * spread, so a two-lane theme keeps its two lanes. A theme whose cars all shared
     * one lane -- the shape every pre-v76.2 theme has -- is alternated instead, which
     * fills both lanes rather than stacking the traffic in one.
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
     * **There are exactly ten, in every theme the app ships**, and that is the single fact behind
     * both [CarShell]'s body deal and [SeatedOccupants]'s. A candidate's lane is one of two
     * constants and its start delay is one of [CAR_SLOTS_PER_LANE] points on an arithmetic
     * progression, so anything derived from those two fields -- however well mixed -- is choosing
     * from ten values, not sampling a distribution. Hashing them produced 5/3/2 bodies and a driver
     * who was a woman in all ten.
     *
     * This is not a hash and deliberately reads back the very numbers the candidate was generated
     * from: `startDelaySeconds` is `CAR_LOOP_ENTRY_PROGRESS + CAR_LOOP_SPAN * slot /
     * CAR_SLOTS_PER_LANE`, so inverting that expression returns the slot, and `laneYFraction` is
     * one of the two lane constants. [canonicaliseTraffic] forces every stored theme onto that same
     * grid on **every** load, so there is no such thing here as a candidate off it; a theme
     * carrying more cars than one loop holds wraps, which is what the modulo is for rather than a
     * clamp that would pile the surplus onto one answer.
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
     * How far to the side of its house a parasol stands, as a fraction of the ground tile.
     *
     * Houses and parasols were generated by the *same* call with the same slot arithmetic and the
     * same two depth bands, differing only in their seed. Slot `i` is `(i + 0.5) / n` for both, so
     * parasol `i` landed within `0.07` of a tile of house `i` -- **76 px of 1080** -- and
     * `depthFraction` came out *identical*, because it is derived from `i` and not from the seed.
     * A house's door is at its own centre, so the parasol was in it.
     *
     * **Half a slot of phase was tried first and is not enough**: the two jitters are ±0.035 each
     * and a 0.05 phase sits inside their sum, so the worst pairing still closed to 0.0013 of a
     * tile. `GroundPilesAndParasolsTest` measured that on the generated layout and rejected it.
     *
     * So a parasol is placed *from its house* rather than independently: it stands this far to one
     * side of it, alternating sides so they do not all lean the same way. The separation is then a
     * property of the placement instead of something the jitter can undo, and a garden umbrella
     * beside a house is what one looks like anyway.
     */
    const val PARASOL_SIDE_OFFSET = 0.055f

    /**
     * The two lanes cars drive in, as fractions of screen height, measured at the wheels'
     * ground line.
     *
     * Both come from [SceneSpace] rather than being defined here: the road, its lanes, the ground
     * the houses stand on and the pavement in front of it are one geometry and cannot have two
     * owners. The pair sits 0.035 of screen height apart, which is more than a car's own drawn
     * height, so the two rows read as separate lanes instead of one band of overlapping vehicles.
     */
    const val CAR_LANE_FAR_Y_FRACTION = SceneSpace.ROAD_LANE_FAR_Y_FRACTION
    const val CAR_LANE_NEAR_Y_FRACTION = SceneSpace.ROAD_LANE_NEAR_Y_FRACTION

    /**
     * How the cars of one lane are spaced around their loop.
     *
     * `progress` runs from -0.3 to 1.3 and wraps by subtracting that 1.6 span, so a car's phase
     * is preserved across the wrap and a fixed head start stays a fixed head start. Each lane has
     * [CAR_SLOTS_PER_LANE] slots and the nth car starts one nth of the span behind the one ahead
     * of it, which is a gap of about a third of the travel -- several car lengths at any screen
     * size.
     *
     * `startDelaySeconds` is subtracted from `progress` at construction, so despite its name it
     * carries a progress offset rather than seconds. That is pre-existing and left alone here.
     */
    const val CAR_LOOP_ENTRY_PROGRESS = 0.3f
    const val CAR_LOOP_SPAN = 1.6f
    const val CAR_SLOTS_PER_LANE = CANDIDATES_PER_CATEGORY / 2

    fun layoutFor(themeId: String, accentColor: Int): SceneObjectLayout {
        if (RandomSceneGenerator.isRandomThemeId(themeId)) {
            return RandomSceneGenerator.generateLayout(themeId, accentColor) + seasonalDecorationCandidates(themeId)
        }
        CustomThemeRegistry.overrideLayoutFor(themeId)?.let { return it + seasonalDecorationCandidates(themeId) }
        val builtinLayout = builtinLayoutFor(themeId, accentColor)
        if (builtinLayout != null) return builtinLayout + seasonalDecorationCandidates(themeId)
        CustomThemeRegistry.customEntry(themeId)?.let { return it.layout + seasonalDecorationCandidates(themeId) }
        return SceneObjectLayout(staticObjects = emptyList(), cars = emptyList())
    }

    /**
     * The cars the **built-in** [themeId] defines, ignoring any override the user has saved.
     *
     * [layoutFor] deliberately prefers a saved override, which is right for rendering and wrong
     * for repair: a damaged override is exactly what needs comparing against the original. This
     * is the only way to ask "what did this theme ship with", and it exists for
     * [repairBuiltInOverrides] and for nothing else.
     *
     * Empty for an id that is not a built-in, which is what makes the repair's guard trivially
     * safe: a standalone custom theme has no canonical layout to fall back to and so is never
     * touched.
     */
    internal fun builtinCarsFor(themeId: String, accentColor: Int): List<CarObject> =
        builtinLayoutFor(themeId, accentColor)?.cars.orEmpty()

    // --- Uniform candidate generation for the 6 customizable categories --------------------

    /**
     * Generates [CANDIDATES_PER_CATEGORY] candidate slots for one customizable category, spread
     * across the screen with a little position jitter (so they don't look like a rigid grid).
     * Deterministic per (seed) so the same theme always generates the same candidate layout --
     * never `Random()` without a fixed seed.
     *
     * [depthRange] is a continuous sub-range of the overall 0..1 depth band (see
     * [StaticSceneObject.depthFraction]'s own doc comment) -- candidate `i` of [n] gets
     * `depthRange.start + (i/(n-1)) * (depthRange.end - depthRange.start)` -- a continuous
     * within-range placement fraction rather than snapping to a fixed number of discrete rows.
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
            val depthFraction = depthRange.start + (i / (n - 1).toFloat().coerceAtLeast(1f)) * (depthRange.endInclusive - depthRange.start)
            StaticSceneObject(type, depthFraction = depthFraction, tileFractionX = tileFractionX, scale = sizeVariation(rnd))
        }
    }

    /**
     * One candidate's size variation around whatever size its category is supposed to be.
     *
     * Every category draws from the same spread, because "some houses are bigger than others" is
     * the same statement whatever the category, and a per-category jitter width was one more
     * number that could not be compared with any other. How big the category itself is comes from
     * [SceneSpace.SceneVariant], never from here.
     */
    private fun sizeVariation(rnd: Random): Float {
        val spread = SceneSpace.SIZE_VARIATION_SPREAD
        return (1f - spread / 2f + rnd.nextFloat() * spread).coerceAtLeast(SceneSpace.MIN_SIZE_VARIATION)
    }

    /**
     * Same as [generateStaticCandidates], but alternates candidates between two depth sub-bands
     * instead of spreading all of them across one continuous range.
     *
     * Houses and buildings are placed the same way, from two bands rather than one: once with a
     * far band (`RangeType.Top`, a band
     * *behind* the tree zone, closer to the hill crest) and once with `RangeType.Bottom` (a band
     * *in front of* the tree zone, closer to the road) -- `Scene.onSceneSizeChanged` computes
     * `buildingRangeTop`/`buildingRangeBottom` as genuinely separate vertical spans that sandwich
     * `treeRangeTop`/`treeRangeBottom` between them. The previous single-range version placed
     * every house/building candidate in one narrow band, which read as flat and clustered instead
     * of the layered "some houses behind the trees, some in front" depth the reference has.
     *
     * [backRange] is used for even indices, [frontRange] for odd -- so with density thinning
     * (which keeps/drops each candidate independently by its own stable per-slot hash, see
     * [SceneCustomization.keepCandidate]) both bands stay populated at any density setting rather
     * than one draining before the other.
     */
    /**
     * One parasol per house, standing [PARASOL_SIDE_OFFSET] to its side rather than in its doorway.
     *
     * Derived from the houses instead of generated beside them, which is the whole point: the two
     * used to be independent draws that happened to share a slot grid, and no amount of phase
     * survives their jitters. Sides alternate by index so a street does not lean one way, and the
     * size still varies per parasol -- that roll is the only thing left that is its own.
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

    /** Same idea as [generateStaticCandidates] but for cars, which aren't hill-layer-anchored.
     *
     * **The road has two lanes, and a car's lane decides which way it drives.** Both of those used
     * to be independent rolls of the same `Random`: every candidate landed in one 1.5 %-of-screen
     * band -- 36 px on a 2400 px screen, against a car 78 px tall -- and took its direction from a
     * coin flip. So the whole fleet shared a single lane, oncoming traffic drove straight through
     * it, and three or four cars could stack into what looked like a continuous pile-up. The
     * dashed centre line sat above all of it, because there was nothing on the far side to
     * separate.
     *
     * Lane comes from the candidate index rather than from the seed, so both lanes are always
     * populated whatever the density and whatever the theme; direction follows from the lane, so
     * the near lane runs right and the far lane runs left and no two cars meet head-on in the same
     * lane. `startDelaySeconds` already staggers same-lane candidates by ~3.6 s, which is what
     * keeps them apart once they are no longer sharing one lane with everything else.
     *
     * Vehicle type is a stable weighted pick per candidate index (not `Random()` per frame --
     * every candidate must always render the same type, the same way its lane/speed are fixed at
     * generation time), mostly [CarType.PLAIN] with a minority of each special type so they read
     * as an occasional sighting rather than every third car being a police car. */
    internal fun generateCarCandidates(seed: Int, accentColor: Int): List<CarObject> {
        val rnd = Random(seed)
        val types = capSpecialsToOnePerType((0 until CANDIDATES_PER_CATEGORY).map { pickCarType(rnd) })
        return (0 until CANDIDATES_PER_CATEGORY).map { i ->
            val nearLane = i % 2 == 0
            val slot = i / 2 // position of this car within its own lane's queue
            CarObject(
                laneYFraction = if (nearLane) CAR_LANE_NEAR_Y_FRACTION else CAR_LANE_FAR_Y_FRACTION,
                // One speed per lane, not one per car -- and the far lane's is derived from its
                // own depth rather than picked, since two cars moving at the same real speed cross
                // the screen at rates their distances decide. Cars in a lane that travel at
                // different speeds inevitably close on each other and drive through one another;
                // the only way a queue stays a queue is if nothing in it overtakes.
                speedFraction = if (nearLane) SceneSpace.CAR_SPEED_NEAR else SceneSpace.CAR_SPEED_FAR,
                // Evenly spaced around the loop rather than randomly delayed, so the lane's cars
                // enter at fixed intervals and hold that spacing forever (see [CarRuntime] and
                // `SceneObjectRenderer.update` for the wrap that preserves it).
                startDelaySeconds = CAR_LOOP_ENTRY_PROGRESS + CAR_LOOP_SPAN * slot / CAR_SLOTS_PER_LANE,
                color = accentColor, // live-recolored by SceneCustomization anyway (PLAIN only)
                // The near lane is the one nearer the viewer, so it carries the traffic that
                // drives to the right; `reverse` is what sends a car leftward.
                reverse = !nearLane,
                type = types[i],
            )
        }
    }

    /**
     * At most one vehicle of each special type in a candidate set -- items 11 and 14 of
     * `BACKLOG_v4_19.md`, which are the same defect counted twice.
     *
     * [pickCarType] rolls each candidate independently, so nothing stopped two of them coming up
     * the same special type: at a tenth each over ten candidates, **26.4% of seeds produce two or
     * more fire engines** and as many produce two or more patrol cars. It is not a rare corner --
     * it was photographed three times across the v4.19 evidence, most recently two fire engines in
     * the same lane in the same night frame.
     *
     * The cap belongs here rather than in the renderer because "how many of each type this theme
     * has" is decided exactly once, at generation, and every candidate of a theme shares the one
     * road: capping the *set* is therefore strictly stronger than capping what is on screen, and it
     * needs no per-frame state to enforce. A surplus special becomes a [CarType.PLAIN] -- it keeps
     * its slot, its lane, its speed and its colour, so the density slider and the shell deal still
     * govern the same ten candidates, and the road keeps the same number of cars on it.
     *
     * The first occurrence is the one kept, which makes the result a pure function of the roll
     * sequence and so as stable and as reproducible as everything else the generator decides.
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

    /** Weighted pick: mostly plain cars, each special type a minority so they read as an
     * occasional sighting. Called with the same per-candidate [Random] sequence
     * [generateCarCandidates] already draws lane/speed/reverse from, so a candidate's type is
     * just as stable/deterministic as everything else about it. */
    private fun pickCarType(rnd: Random): CarType {
        val roll = rnd.nextFloat()
        return when {
            roll < 0.70f -> CarType.PLAIN
            roll < 0.80f -> CarType.POLICE
            roll < 0.90f -> CarType.TAXI
            else -> CarType.FIRE_TRUCK
        }
    }

    /** The uniform 6-category candidate set shared by every theme. [treeType] lets themes like
     * Beach use palm trees instead of plain trees for their "trees" category slots while still
     * sharing the same density/color customization (both map to the same category, see
     * [SceneCustomization]'s `configFor`).
     *
     * Depth ranges (0=farthest/smallest, 1=nearest/largest -- see [StaticSceneObject.
     * depthFraction]'s own doc comment) now use the **whole** 0..1 band. They could not before:
     * every category was capped at 0.375 because the road was drawn over anything standing lower,
     * so the entire scene occupied a strip 111 px tall on a 2400 px screen with 1.5x between its
     * smallest and largest object. [SceneSpace] places the road below the object band by
     * construction and asserts the margin in a test, so the cap has no reason to exist and the
     * full depth range is available:
     *
     * - SKYSCRAPER (the buildings category): 0.0-0.80. Its far half draws towers and its near
     *   half shop fronts -- see [SceneSpace.BUILDING_TOWER_MAX_DEPTH]. A four-metre bar has no
     *   business on the skyline and a twenty-metre tower none among the front gardens.
     * - HOUSE/PARASOL: split back/front via [generateSplitStaticCandidates] -- 0.28-0.48 behind
     *   the tree zone, 0.62-0.95 in front of it, so houses sit at two clearly separated depths
     *   rather than at one.
     * - treeType: 0.18-1.0, spanning across (and slightly past, at both ends) both house bands so
     *   trees genuinely interleave with houses at every depth.
     */
    private fun uniformCandidates(
        themeId: String,
        accentColor: Int,
        treeType: SceneObjectType = SceneObjectType.TREE,
    ): SceneObjectLayout {
        val seed = themeId.hashCode()
        val houseBack = 0.28f..0.48f
        val houseFront = 0.62f..0.95f
        val houses = generateSplitStaticCandidates(SceneObjectType.HOUSE, seed + 1, houseBack, houseFront)
        val staticObjects = separateShopFrontages(
            singleShopPerVariant(generateStaticCandidates(SceneObjectType.SKYSCRAPER, seed + 2, 0.0f..0.80f)) +
                houses +
                generateStaticCandidates(treeType, seed + 5, 0.18f..1.0f) +
                parasolsBeside(houses, seed + 4),
        )
        val cars = generateCarCandidates(seed + 6, accentColor)
        return SceneObjectLayout(staticObjects = staticObjects, cars = cars)
    }

    // ---- Shop-front visibility and identity (rc2, remetricated + deduplicated in rc3) --------
    //
    // The trattoria and the pub were redrawn to be told apart at a glance, and on the delivered
    // Autumn frame one of them was standing behind a house that covered nine tenths of it: a
    // frontage nobody can see is artwork nobody gets. The candidates are placed by independent
    // slot arithmetic per category, so nothing ever kept a nearer building off a shop -- this
    // pass does, at layout time, by nudging the *shop* sideways (never its depth: a shop's depth
    // is what makes it a shop, see SceneSpace.BUILDING_TOWER_MAX_DEPTH).
    //
    // rc3 corrected the metric and the cardinality, both against delivered frames:
    //  - The rc2 rule measured the worst single occluder against the frontage's *lower half*,
    //    and the day scene answered with a pub numerically at 40% and visually cut in two by a
    //    tree trunk planted over its door, crown across the whole upper storey. The measure is
    //    now the union of everything nearer over the ENTIRE front (crowns, trunks, poles and
    //    parasol canopies included), and no vertical member may cross the front at all.
    //  - Two identical trattorias stood in one frame. That is not fixable by spacing: the object
    //    tile is two screen widths and an object is on screen for (screenW + its width) of
    //    scroll, which is MORE than half the tile -- so any two shops of the same storefront
    //    share a frame at some scroll position, wherever they stand. The only layout that never
    //    shows twin storefronts is one restaurant and one bar per tile, so that is what the
    //    catalogue now emits ([singleShopPerVariant]); the surplus commercial candidates keep
    //    their slot and their category and become skyline towers instead.

    /** The reference viewport the occlusion geometry is evaluated at: the device every visual
     * judgement in this project is made on. The overlap *fractions* barely move with aspect
     * ratio, but they are not exactly invariant, so the number in the rule names its frame. */
    private const val REF_SCREEN_W = 1080f
    private const val REF_SCREEN_H = 2340f

    /** Occlusion above which a shop is moved: the 40% ceiling with a working margin. Since rc3
     * the fraction is the covered share of the shop's WHOLE front (area union of every nearer
     * occluding box), not the worst single occluder's share of the lower band's width. */
    private const val SHOP_MAX_OCCLUSION = 0.40f
    private const val SHOP_TARGET_OCCLUSION = 0.32f

    /** How far past the shop's front edge a stepped-aside tree is pushed, in reference px. */
    private const val TREE_STEP_ASIDE_MARGIN_PX = 4f

    /**
     * Keeps one commercial candidate per storefront and turns the rest into skyline towers.
     *
     * Of the shop-band candidates (depth >= [SceneSpace.BUILDING_TOWER_MAX_DEPTH]), the
     * depth-middle one of each variant half-band (see [SceneSpace.SHOP_VARIANT_DEPTH_SPLIT])
     * stays a shop -- the middle, because the half-band's extremes are the smallest and largest
     * the street offers and the shop should read as an ordinary building of its row. Every other
     * shop-band candidate keeps its slot, its x, its size roll and its category (so the density
     * slider and the colour config govern exactly the same ten candidates as before) and moves
     * to a tower depth, interleaved across the tower band between the four generated tower
     * depths rather than stacked on them.
     */
    private fun singleShopPerVariant(candidates: List<StaticSceneObject>): List<StaticSceneObject> {
        val shopIndices = candidates.indices.filter {
            candidates[it].depthFraction >= SceneSpace.BUILDING_TOWER_MAX_DEPTH
        }
        fun middleByDepth(indices: List<Int>): Int? =
            indices.sortedBy { candidates[it].depthFraction }.let { if (it.isEmpty()) null else it[it.size / 2] }
        val kept = setOfNotNull(
            middleByDepth(shopIndices.filter { candidates[it].depthFraction < SceneSpace.SHOP_VARIANT_DEPTH_SPLIT }),
            middleByDepth(shopIndices.filter { candidates[it].depthFraction >= SceneSpace.SHOP_VARIANT_DEPTH_SPLIT }),
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

    /** Drawn half-width of a variant, in its own sprite units, measured off the artwork. */
    private fun halfWidthUnits(variant: SceneSpace.SceneVariant): Float = when (variant) {
        SceneSpace.SceneVariant.HOUSE_SMALL -> 48f
        SceneSpace.SceneVariant.HOUSE_LARGE -> 75f
        SceneSpace.SceneVariant.RESTAURANT, SceneSpace.SceneVariant.BAR -> 34f
        SceneSpace.SceneVariant.TOWER -> 45f
        SceneSpace.SceneVariant.TREE -> 41f
        SceneSpace.SceneVariant.PALM_TREE -> 20f
        else -> 0f
    }

    private fun isShop(o: StaticSceneObject) =
        o.type == SceneObjectType.SKYSCRAPER && o.depthFraction >= SceneSpace.BUILDING_TOWER_MAX_DEPTH

    /**
     * Moves every shop to the nearest position where its front is neither covered past
     * [SHOP_MAX_OCCLUSION] nor crossed by any vertical member.
     *
     * Geometry, not judgement: each object's drawn extent at the reference viewport comes from
     * the same [SceneObjectRenderer.effectiveScaleFor] pipeline the renderer draws it with, so
     * the pass and the picture cannot disagree. Two rules, both over the shop's ENTIRE front
     * (rc3 -- the rc2 lower-band single-occluder rule passed a pub visually cut in two):
     *  - **Coverage.** The union of every nearer occluding box -- house and shop bodies, tree
     *    crowns AND trunks, palm fans AND trunks, parasol canopies AND poles -- may cover at
     *    most [SHOP_MAX_OCCLUSION] of the front's area.
     *  - **No vertical member across the front.** A trunk or pole crossing the frontage splits
     *    the door and panes however little area it costs, so it is a hard reject rather than a
     *    contribution to the fraction.
     *
     * Shape, learned the hard way across the twelve built-in layouts:
     *  - **Nearest shop first.** A shop settles only against what is nearer than itself, so once
     *    the nearest has parked, the deeper ones route around it. List order oscillated on
     *    Winter's layout and left a deep shop 53% covered.
     *  - **Positions are scanned, not nudged.** Probes step outward from the shop's own slot in
     *    hundredth-of-a-tile steps, alternating sides; the first uncrossed probe under
     *    [SHOP_TARGET_OCCLUSION] wins. Single-worst-occluder nudging oscillated between the
     *    houses flanking the only clear window.
     *  - **Two acceptance tiers.** Failing the target, the nearest uncrossed probe under the
     *    ceiling itself still satisfies the rule -- some depths offer 33-40% and nothing better.
     *  - **Trees step aside as a last resort.** If no probe clears the ceiling, the shop parks
     *    at its least-covered uncrossed probe and every tree still touching the front is moved
     *    fully clear of it; houses are the street and never move, and parasols belong to their
     *    houses ([parasolsBeside]) so the probes route around their poles instead. The sweeps
     *    run to a fixed point; ShopFrontVisibilityTest re-measures all twelve themes
     *    independently.
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

    /** The shop's full drawn front at the reference viewport: (left, top, right, bottom) px. */
    private fun frontRect(shop: StaticSceneObject): FloatArray {
        val s = SceneObjectRenderer.effectiveScaleFor(shop, REF_SCREEN_H)
        val g = REF_SCREEN_H * SceneSpace.groundYFraction(shop.depthFraction)
        val x = shop.tileFractionX * REF_SCREEN_W * 2f
        val half = halfWidthUnits(SceneObjectRenderer.variantFor(shop)) * s
        return floatArrayOf(x - half, g - SceneObjectRenderer.variantFor(shop).spriteUnitsTall * s, x + half, g)
    }

    /**
     * What one object puts between the viewer and anything behind it, as (left, top, right,
     * bottom) boxes at the reference viewport. A building is its body; a tree is its crown (the
     * canopy blit's own -118..-44, see recordLeafSource's measurements of the same artwork) AND
     * its trunk (the 10x44-unit blit at TreeSpriteLayout.TRUNK_X/Y); a palm is fan and trunk; a
     * parasol is canopy and pole (drawParasol's own 34-unit wedge fan on a 5x50 pole).
     */
    private fun occluderBoxes(o: StaticSceneObject): List<FloatArray> {
        val v = SceneObjectRenderer.variantFor(o)
        val s = SceneObjectRenderer.effectiveScaleFor(o, REF_SCREEN_H)
        val g = REF_SCREEN_H * SceneSpace.groundYFraction(o.depthFraction)
        val x = o.tileFractionX * REF_SCREEN_W * 2f
        return when (v) {
            SceneSpace.SceneVariant.TREE -> listOf(
                floatArrayOf(x - 41f * s, g - 118f * s, x + 41f * s, g - 44f * s),
                floatArrayOf(x - 5f * s, g - 44f * s, x + 5f * s, g),
            )
            SceneSpace.SceneVariant.PALM_TREE -> listOf(
                floatArrayOf(x - 20f * s, g - 90.33f * s, x + 20f * s, g - 53.5f * s),
                floatArrayOf(x - 6f * s, g - 58f * s, x + 5f * s, g),
            )
            SceneSpace.SceneVariant.PARASOL -> listOf(
                floatArrayOf(x - 34f * s, g - 84f * s, x + 34f * s, g - 50f * s),
                floatArrayOf(x - 2.5f * s, g - 50f * s, x + 2.5f * s, g),
            )
            SceneSpace.SceneVariant.HOUSE_SMALL, SceneSpace.SceneVariant.HOUSE_LARGE,
            SceneSpace.SceneVariant.RESTAURANT, SceneSpace.SceneVariant.BAR,
            SceneSpace.SceneVariant.TOWER,
            -> listOf(floatArrayOf(x - halfWidthUnits(v) * s, g - v.spriteUnitsTall * s, x + halfWidthUnits(v) * s, g))
            else -> emptyList()
        }
    }

    /** The trunk or pole alone -- the box whose mere crossing of a front is the defect. */
    private fun verticalMemberBox(o: StaticSceneObject): FloatArray? {
        val v = SceneObjectRenderer.variantFor(o)
        val s = SceneObjectRenderer.effectiveScaleFor(o, REF_SCREEN_H)
        val g = REF_SCREEN_H * SceneSpace.groundYFraction(o.depthFraction)
        val x = o.tileFractionX * REF_SCREEN_W * 2f
        return when (v) {
            SceneSpace.SceneVariant.TREE -> floatArrayOf(x - 5f * s, g - 44f * s, x + 5f * s, g)
            SceneSpace.SceneVariant.PALM_TREE -> floatArrayOf(x - 6f * s, g - 58f * s, x + 5f * s, g)
            SceneSpace.SceneVariant.PARASOL -> floatArrayOf(x - 2.5f * s, g - 50f * s, x + 2.5f * s, g)
            else -> null
        }
    }

    /** The nearest wrapped copy of [box] relative to a front centred at [cx]. */
    private fun wrapBoxToward(box: FloatArray, cx: Float): FloatArray {
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
        val f = frontRect(shop)
        val cx = (f[0] + f[2]) / 2f
        val clipped = ArrayList<FloatArray>()
        for (o in objects) {
            if (o === shop || o.depthFraction <= shop.depthFraction) continue
            for (box in occluderBoxes(o)) {
                val b = wrapBoxToward(box, cx)
                val l = maxOf(b[0], f[0])
                val t = maxOf(b[1], f[1])
                val r = minOf(b[2], f[2])
                val bo = minOf(b[3], f[3])
                if (r > l && bo > t) clipped.add(floatArrayOf(l, t, r, bo))
            }
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
     * Standalone seasonal decorations (snowmen, gifts, penguins, bunnies, Easter eggs,
     * pumpkins), generated for *every* theme regardless of which one it "traditionally" belongs
     * to -- these used to be hardcoded per theme (Christmas got snowmen+gifts, Easter got
     * eggs+bunnies, etc.) with no way to turn them off or use them anywhere else. They're now
     * exactly like the 5 uniform categories above: a fixed set of candidate slots per type, with
     * visibility/density/color entirely controlled by the user via the "Seasonal Decorations"
     * screen (separate from "Scene Objects", since these are opt-in extras rather than a
     * structural part of every scene) -- see [SceneCustomization]. All default to *invisible*
     * so nothing changes for a
     * theme until the user explicitly turns something on -- but once turned on, it now shows up
     * on every theme, not just its old "traditional" one, which is the whole point: nothing stops
     * a user from wanting pumpkins at Christmas or snowmen on the beach.
     *
     * These are the smallest objects in the scene -- a rabbit is half a metre tall -- so they are
     * placed across the near half of the band (0.45-1.0) where the perspective still gives them
     * enough size to read as themselves. Balloons are the exception and sit far back (0.05-0.45),
     * because a twenty-metre envelope drawn at the front of the scene would fill the frame.
     */
    private fun seasonalDecorationCandidates(themeId: String): List<StaticSceneObject> {
        val seed = themeId.hashCode()
        return generateStaticCandidates(SceneObjectType.SNOWMAN, seed + 101, 0.45f..1.0f) +
            generateStaticCandidates(SceneObjectType.GIFT, seed + 102, 0.45f..1.0f) +
            generateStaticCandidates(SceneObjectType.PENGUIN, seed + 104, 0.45f..1.0f) +
            generateStaticCandidates(SceneObjectType.BUNNY, seed + 105, 0.45f..1.0f) +
            generateStaticCandidates(SceneObjectType.EASTER_EGG, seed + 106, 0.45f..1.0f) +
            generateStaticCandidates(SceneObjectType.PUMPKIN, seed + 107, 0.45f..1.0f)
    }

    private operator fun SceneObjectLayout.plus(flavor: List<StaticSceneObject>): SceneObjectLayout =
        copy(staticObjects = staticObjects + flavor)

    /** Returns null (rather than an empty layout) for unknown ids, so [layoutFor] can tell the
     * difference between "not a built-in id" and "a built-in id with an intentionally empty scene". */
    private fun builtinLayoutFor(themeId: String, accentColor: Int): SceneObjectLayout? = when (themeId) {
        "sunset" -> uniformCandidates(themeId, accentColor)
        "autumn" -> uniformCandidates(themeId, accentColor)
        "winter" -> uniformCandidates(themeId, accentColor)
        // Palms, not broadleaf woodland. The tree *type* is a property of the theme's layout, so
        // this is the same knob Beach already uses rather than a special case in the renderer.
        "desert" -> uniformCandidates(themeId, accentColor, treeType = SceneObjectType.PALM_TREE)
        "christmas" -> uniformCandidates(themeId, accentColor)
        "new_year" -> uniformCandidates(themeId, accentColor)
        "beach" -> uniformCandidates(themeId, accentColor, treeType = SceneObjectType.PALM_TREE)
        "city" -> uniformCandidates(themeId, accentColor)
        "tundra" -> uniformCandidates(themeId, accentColor)
        "easter" -> uniformCandidates(themeId, accentColor)
        // Broadleaf woodland, which the Halloween flag then strips to bare branches. Palms have
        // no dead variant and would go on standing in leaf through the whole presentation.
        "halloween" -> uniformCandidates(themeId, accentColor)
        "spring" -> uniformCandidates(themeId, accentColor)
        else -> null
    }
}
