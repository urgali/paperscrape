package com.paperscrape.livewallpaper.engine

import kotlin.random.Random

/**
 * The kind of object placed in the scene. Drives its drawing, its size and which customization
 * category governs it.
 */
enum class SceneObjectType {
    CAR, HOUSE, TREE,
    // Seasonal / festive additions (step 2)
    SNOWMAN, GIFT, PALM_TREE, PARASOL, SKYSCRAPER, PENGUIN,
    // Easter theme additions (auto-theme-by-date feature)
    EASTER_EGG, BUNNY,
    // Standalone seasonal decorations, independent of any theme -- user-toggleable on any
    // theme via the "Seasons & decorations" screen (see SceneCustomization + SeasonsScreen).
    PUMPKIN,
}

/**
 * A stationary (but animated-in-place) object anchored to the hill's ground line at a continuous
 * depth.
 *
 * [depthFraction] (0..1, 0=farthest/smallest, 1=nearest/largest) replaces what used to be a
 * discrete `layer: Int` row index (0..8). Every object stands at its own continuous fraction
 * (`(index - rangeStart) / (rangeEnd - rangeStart)`, within whatever index sub-range that category
 * was given) rather than snapping to one of a fixed number of discrete slots, and the *same*
 * fraction decides both where it stands and how big it is drawn. Two independent fractions, one
 * for the position and one for the size, would differ only in how the category's range is cut, and
 * for objects placed within one narrow category range they move together closely enough that one
 * fraction simplifies the whole placement pipeline without a visible difference. See
 * [SceneSpace.groundYFraction] and [SceneSpace.depthScale] for how this fraction becomes an
 * actual screen position and size.
 *
 * [tileFractionX] is the object's horizontal position expressed as a fraction (0..1) of the
 * object layer's own tiling width -- twice the screen width, the same period the hill silhouette
 * tiles on (see [PaperRenderer.drawHillLayers]).
 *
 * [scale] is a **relative size variation around 1**, not a size. How big a house is supposed to be
 * is a property of houses and lives in [SceneSpace.SceneVariant]; this field only says whether
 * this particular one is a little larger or smaller than its neighbours. It used to carry the
 * category's entire base size as well, which meant two unrelated decisions shared one number and
 * neither could be read without the other -- and made two categories' sizes impossible to compare,
 * since each was expressed against its own sprite's arbitrary internal scale. Payloads written
 * before the split are converted by the schema 1 -> 2 migration in `CustomThemeData.kt`.
 *
 * [silhouette] is **which of its category's drawings this slot was dealt** -- see
 * [SilhouetteDeal], which also explains why it is recorded here rather than recomputed by
 * whoever draws. Defaults to [UNDEALT], which is what a spec nobody dealt carries: a custom
 * theme saved before v5.5, a random-theme layout, or one of the many tests that construct a bare
 * candidate. Those keep the pre-v5.5 behaviour exactly -- the drawing is hashed from the position,
 * as it always was -- so this field adds a path and changes none.
 */
data class StaticSceneObject(
    val type: SceneObjectType,
    val depthFraction: Float,
    val tileFractionX: Float,
    val scale: Float = 1f,
    val silhouette: Int = UNDEALT,
) {
    companion object {
        /** No deal recorded: the drawing is hashed from the position, as before v5.5. */
        const val UNDEALT = -1
    }
}

/**
 * The 4 vehicle types that can appear on the road: plain car, police car, taxi, fire truck.
 *
 * Only [PLAIN] is user-recolorable via the "Cars" category color pickers -- the other 3 use fixed,
 * real-world-associated colors, because a police car isn't "your theme's accent color", it's
 * black-and-white. The police car and the taxi are the same tintable shells PLAIN uses, tinted
 * with fixed constants in `drawCar` instead of the category colour; only the fire engine is
 * finished, non-tintable art.
 *
 * [seatsTwo] is a property of the type rather than a list of exclusions at the call site, so a
 * vehicle added later gets the conservative answer by default rather than by somebody
 * remembering. rc2 called it `carriesPassengers` and it excluded the police car, because the
 * passenger could then be a child and a child in the back of a service vehicle reads as
 * something being wrong. rc5 seated two **adults** or nobody, so the reason to exclude the police
 * car went: two officers in a patrol car is what a patrol car looks like. Since v4.19 the
 * passenger is any of the four families, children included (see [SeatedOccupants] and
 * [SceneObjectRenderer.CAR_PASSENGER_X_UNITS]), and the police car still seats two. The fire
 * engine keeps its single seat, because its cab glass is 19 units and holds one head, not because
 * of who is in it.
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
    /**
     * How [staticObjects] are thinned by density: [DENSITY_SCHEME_FRACTION] for every generated
     * layout, and for a saved one whatever it was saved with (v5.8C; see [DENSITY_SCHEME_GRID]).
     */
    val densityScheme: Int = DENSITY_SCHEME_FRACTION,
    /**
     * A saved street's places among the houses that the loader moved, each to the place as the theme stores
     * it (v5.13B2, [SceneObjectCatalog.placesClearedOnSavedStreet]); empty where nothing moved. The six
     * buildings take their identity -- their colours and their people -- from the places as stored
     * ([StorefrontStreet.identities]), and a save writes them back as stored ([toJson]): moving a place
     * changes where it stands and nothing else, on this load and on every one after.
     */
    val placesAsSaved: Map<StaticSceneObject, StaticSceneObject> = emptyMap(),
)

/**
 * Default per-theme object layouts.
 *
 * Design principle: every theme offers the *same* maximum customization range for the 5
 * structural, user-editable categories (houses, buildings, cars, umbrellas, trees) -- exactly
 * [CANDIDATES_PER_CATEGORY] candidate slots each, generated uniformly rather than hand-authored
 * per theme. Whether a theme ends up looking like a quiet village or a dense city is entirely
 * up to the user's density sliders in "World & scene", never baked into the theme itself.
 *
 * Seasonal decorations (snowmen, gifts, penguins, bunnies, Easter eggs, pumpkins) are
 * a *second*, independent customization surface -- "Seasons & decorations", edited via
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
     * owners. The pair sits 0.028 of screen height apart
     * ([SceneSpace.CANONICAL_LANE_SPACING_FRACTION]), about a car's own drawn height: two lanes a
     * vehicle apart, so the road reads as a road rather than as a band the traffic sits inside
     * (see [SceneSpace.ROAD_LANE_NEAR_Y_FRACTION]).
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

    // --- Uniform candidate generation for the 5 structural categories ----------------------

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

    /**
     * Same as [generateStaticCandidates], but alternates candidates between two depth sub-bands
     * instead of spreading all of them across one continuous range.
     *
     * Houses are placed from two bands rather than one: even candidates in [backRange], closer to
     * the hill crest, and odd ones in [frontRange], closer to the road. The previous single-range
     * version placed every house candidate in one narrow band, which read as flat and clustered
     * instead of layered in depth.
     *
     * With density thinning (which keeps/drops each candidate independently by its own stable
     * per-slot hash, see [SceneCustomization.keepCandidate]) both bands drain at the same expected
     * rate as the density falls rather than one draining before the other.
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
     * Lane comes from the candidate index rather than from the seed, so the *inventory* always
     * offers both lanes whatever the theme; direction follows from the lane, so the near lane
     * runs right and the far lane runs left and no two cars meet head-on in the same lane.
     * `startDelaySeconds` already staggers same-lane candidates by a fifth of the loop (about
     * 4.3 s in the near lane, 4.9 s in the far one), which is what keeps them apart once they are
     * no longer sharing one lane with everything else.
     *
     * This used to claim both lanes are always *populated* whatever the density, and since v4.22
     * that is deliberately no longer true: the density slider maps to an explicit car count whose
     * bottom is **one car in the whole scene**, so at low counts one lane stands empty -- which
     * lane comes from the theme's seed, not from a constant, so it is not the same lane in every
     * theme. See [CarSelection]. What this generator guarantees is unchanged: ten candidates,
     * five per lane, uniformly spaced around the loop.
     *
     * Vehicle type is dealt per candidate index rather than rolled (not `Random()` per frame --
     * every candidate must always render the same type, the same way its lane/speed are fixed at
     * generation time), mostly [CarType.PLAIN] with one of each special type so they read as an
     * occasional sighting rather than every third car being a police car. **v5.5 replaced the
     * per-candidate roll with the multiset [CAR_TYPE_DEAL]**, which is where the 70/10/10/10
     * weights now live and why every theme carries all four types -- see that property. */
    internal fun generateCarCandidates(seed: Int, accentColor: Int): List<CarObject> {
        val types = capSpecialsToOnePerType(dealtCarTypes(seed, CANDIDATES_PER_CATEGORY))
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
     * The pick this cap was written against rolled each candidate independently, so nothing stopped
     * two of them coming up the same special type: at a tenth each over ten candidates, **26.4% of
     * seeds produce two or more fire engines** and as many produce two or more patrol cars. It was
     * not a rare corner -- it was photographed three times across the v4.19 evidence, most recently
     * two fire engines in the same lane in the same night frame.
     *
     * **Since v5.5 it has nothing to do on a built-in theme**, because [CAR_TYPE_DEAL] holds one of
     * each special and a deal cannot produce a duplicate. It is kept, and kept in the call path,
     * for two reasons: `RandomSceneGenerator` rolls its types and calls it directly (a saved
     * custom theme is capped on load by `CustomThemeData`'s `migrateDuplicateSpecialVehicles`),
     * and a cap that is asserted by `capSpecialsToOnePerType`'s own test is a cheaper guarantee
     * than the reader's memory that the multiset upstream happens to be unique.
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

    /**
     * The ten types one theme's road carries, as a multiset dealt across its ten slots.
     *
     * **The declared weights, dealt exactly rather than rolled.** The pick this replaces was
     * `roll < 0.70 -> PLAIN, < 0.80 -> POLICE, < 0.90 -> TAXI, else FIRE_TRUCK` -- an independent
     * roll per candidate off `Random(seed + 6)`. Over millions of seeds that is 70/10/10/10; over
     * the ten candidates one seed produces it is a hand, and measured on the twelve shipped ids it
     * dealt **two of the four types to four of the twelve themes**: `tundra` and `spring` carried
     * no fire engine and no taxi between them, `easter` and `halloween` no patrol car and no taxi.
     * Seven plain and one of each special *is* 70/10/10/10 on ten slots, so the weights are not
     * approximated here -- they are met, and every theme shows all four types.
     *
     * This is [SeededBalance.drawCount]'s argument in the one case where the fraction happens to
     * come out whole, and [capSpecialsToOnePerType]'s cap becomes a property of the multiset rather
     * than a repair applied to a roll: the cap still runs, and now has nothing to do.
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

    /** The uniform 5-category candidate set shared by every theme. [treeType] lets themes like
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
     * - SKYSCRAPER (the buildings category): 0.0-0.80. Below 0.30 it draws towers and above it
     *   shop fronts -- see [SceneSpace.BUILDING_TOWER_MAX_DEPTH]. A four-metre bar has no
     *   business on the skyline and a twenty-metre tower none among the front gardens.
     * - HOUSE: split back/front via [generateSplitStaticCandidates] -- 0.28-0.48 and 0.62-0.95 --
     *   so houses sit at two clearly separated depths rather than at one; each PARASOL takes its
     *   own house's depth ([parasolsBeside]).
     * - treeType: 0.18-1.0, spanning across (and slightly past, at both ends) both house bands so
     *   trees genuinely interleave with houses at every depth.
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
     * Every static object of a generated street, the shops still at their generated slots.
     *
     * Each population's silhouettes are dealt across its own slots here, at generation, where the
     * seed and the slot index are both in hand -- see [SilhouetteDeal] for why that is the only
     * place it can happen. The building candidates are dealt in [buildingCandidates], *after* the
     * pass that settles which of them is a tower and which a place among the houses, because a
     * building's catalogue follows from that. The plan moves only `tileFractionX` and so cannot
     * disturb a deal.
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
    //    catalogue emitted ([singleShopPerVariant]); the surplus commercial candidates kept
    //    their slot and their category and became skyline towers instead. Since v5.13 which
    //    building stands in a place is the street's, which keeps twins off the screen itself
    //    ([StorefrontStreet]), and since v5.13B2 two of those surplus candidates are places
    //    again ([buildingCandidates]).

    /** The reference viewport the occlusion geometry is evaluated at: 1080x2340, the OnePlus 6T
     * the rule was first judged on. The overlap *fractions* barely move with aspect ratio, but
     * they are not exactly invariant, so the number in the rule names its frame. */
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
     * The building candidates of a generated street, each with its silhouette dealt: **five places among
     * the houses and five towers** (v5.13B2).
     *
     * The maintainer's request of 2026-10-12, shown on the BV6600 mid-round as a street with two towers
     * fewer and five places: *«Sì, 5 posti, in un giro a parte»*, then *«5 posti prima del sigillo»*. Of
     * the ten building candidates six stand in the shop band (0.356, 0.444, 0.533, 0.622, 0.711, 0.800 on
     * every theme); [singleShopPerVariant] keeps three of them as places -- the depth-middle one of each
     * third, 0.444, 0.622 and 0.800 -- and lays the other three on the skyline at 0.05, 0.15 and 0.25.
     * **Two of those three come back** ([placesReturned]): the restaurant third's 0.356 and the bar third's
     * 0.711. The school third's 0.533 stays the tower it was, at 0.15, because it is the middle one of the
     * three: its tower depth, interleaved over one tower instead of three, is the one it already had. So
     * every tower left stands where it stood and is the height and the colour it was (both coins read its
     * position), and its crown is the one it was dealt over the seven tower slots the street had before
     * (the deal below): the street loses two towers and nothing else on its skyline changes.
     *
     * A place's own silhouette is dealt over the five places, each from its row's catalogue
     * ([SilhouetteDeal.catalogueFor]): what a saved copy of the street keeps. The built-in street does not
     * read it -- each of the six buildings takes its identity from [StorefrontStreet.identities].
     */
    private fun buildingCandidates(seed: Int): List<StaticSceneObject> {
        val generated = generateStaticCandidates(SceneObjectType.SKYSCRAPER, seed + 2, 0.0f..0.80f)
        val threePlaces = singleShopPerVariant(generated)
        // The crowns, dealt over the seven tower slots of the street before the two came back.
        val crowns = SilhouetteDeal.dealtAcross(threePlaces, seed)
        val fivePlaces = placesReturned(generated, threePlaces)
        val places = SilhouetteDeal.dealtAcross(fivePlaces.filter { isShop(it) }, seed)
        var next = 0
        return fivePlaces.mapIndexed { i, o -> if (isShop(o)) places[next++] else crowns[i] }
    }

    /**
     * [threePlaces] (the street [singleShopPerVariant] makes of [generated]) with two of the candidates it
     * laid on the skyline standing in the shop band again, where they were generated: every one of them
     * but the middle one by the order it was laid down in, which keeps the tower depth it was given. See
     * [buildingCandidates].
     */
    private fun placesReturned(generated: List<StaticSceneObject>, threePlaces: List<StaticSceneObject>): List<StaticSceneObject> {
        val laidOnSkyline = generated.indices.filter { isShop(generated[it]) && !isShop(threePlaces[it]) }
        val staysTower = laidOnSkyline.getOrNull(laidOnSkyline.size / 2)
        return threePlaces.mapIndexed { i, o -> if (i in laidOnSkyline && i != staysTower) generated[i] else o }
    }

    /**
     * Keeps one commercial candidate per storefront and turns the rest into skyline towers: the street of
     * three places a building candidate list had until v5.13B2, and since then the first step of
     * [buildingCandidates], which brings two of the candidates this lays on the skyline back.
     *
     * Of the shop-band candidates (depth >= [SceneSpace.BUILDING_TOWER_MAX_DEPTH]), the
     * depth-middle one of each variant band (see [SceneSpace.RESTAURANT_MAX_DEPTH]) stays a shop
     * -- the middle, because the band's extremes are the smallest and largest the street offers
     * and the shop should read as an ordinary building of its row. Every other shop-band candidate
     * keeps its slot, its x, its size roll and its category (so the density slider and the colour
     * config govern exactly the same ten candidates as before) and moves to a tower depth,
     * interleaved across the tower band between the four generated tower depths rather than
     * stacked on them.
     *
     * **This pass is what decides whether a storefront is drawn at all, which is why v5.6F's third
     * band had to be added here before anywhere else.** A variant with no band of its own is never
     * kept: the school would be demoted to a tower on every theme, and a census of the scene would
     * come back *identical* to the one before it was added -- which reads as "the school costs
     * nothing" and means "the school is not there". That is measured, not hypothetical: the v5.6D
     * measurement round's first atlas census came back byte-identical to the baseline for exactly
     * this reason.
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
     * Half-width of a variant, in its own sprite units, as the occlusion pass models it. The
     * school's and the tree's are measured off the current artwork (see their notes); the palm's
     * is its crown's half-width about the crown's own centre (see its note).
     * The two houses' and the tower's are the wider side of the ink the shipped pieces draw, over
     * every alternative of every slot (v5.8E, V3-01): 58.7 (small house), 77.1 (large house) and 35.9
     * (tower), as the restaurant's 52.7 and the bar's 38.1 were until v5.13, when a place among the
     * houses became as wide as the widest of the six buildings that may stand in it (below). Until
     * v5.8E they were 48, 75,
     * 34, 34 and 45 -- the restaurant modelled 68 units wide where it draws 104 -- so the pass
     * accepted fronts more covered than it computed: four restaurants at the twelve defaults broke
     * the 40 %/no-trunk rule it believed they kept. With the true widths the restaurant stands clear
     * only in the first third of the tile on every theme, which is why the shops were placed by a plan
     * of the twelve streets together rather than each on its own ([planStreets] since v5.13B2).
     */
    private fun halfWidthUnits(variant: SceneSpace.SceneVariant): Float = when (variant) {
        SceneSpace.SceneVariant.HOUSE_SMALL -> 58.7f
        SceneSpace.SceneVariant.HOUSE_LARGE -> 77.1f
        SceneSpace.SceneVariant.TOWER -> 35.9f
        // **A place among the houses is as wide as the widest of the six that may stand in it** (v5.13):
        // the street deals any of them to any place ([StorefrontStreet]), so the reach a place has, as an
        // occluder and as the front a trunk must not cross, is the widest of their fronts, each at the
        // place's metre -- the school's 55.3 units since v5.13. Until then each place held one building and
        // was its width: 52.7 (restaurant), 38.1 (bar), 56 (school). [frontRects] keeps the six apart where
        // the covered share is measured.
        SceneSpace.SceneVariant.RESTAURANT, SceneSpace.SceneVariant.SCHOOL, SceneSpace.SceneVariant.BAR ->
            Storefront.ALL.maxOf { it.halfWidthUnits } * placeMetre(variant)
        // v4.21: 51, not 41. The "Broad Oak" crown is `tree_canopy`'s 101 units of content
        // blitted at -50, so it spans x -50..51 and the wider side governs a symmetric reach.
        SceneSpace.SceneVariant.TREE -> 51f
        // v5.1, for the "Coconut" palm: the crown is a 56-unit canvas blitted at -21, so it spans
        // x -21..35 about the foot. It leans, so `CROWN_HALF_WIDTH` (28) is its half-width about
        // the crown's own centre, 7 units right of the foot, not a reach about the foot.
        // Not modelled, and why it is left so (v5.8D/E, V3-02): the ink of the lean side reaches
        // 34 units right of the foot (SpriteOccluderTable.PALM_CROWN), so a palm the step-aside
        // parked to a shop's LEFT would overhang the front by up to 6 units less the 4 px margin.
        // The step-aside is the pass's last resort and runs on none of the twelve built-in layouts
        // (0 of 12, measured with a probe in the branch), and mixed and saved themes never reach
        // it, so no palm is ever put there. Model the lean here before a layout makes it run.
        SceneSpace.SceneVariant.PALM_TREE -> PalmSpriteLayout.CROWN_HALF_WIDTH
        else -> 0f
    }

    internal fun isShop(o: StaticSceneObject) =
        o.type == SceneObjectType.SKYSCRAPER && o.depthFraction >= SceneSpace.BUILDING_TOWER_MAX_DEPTH

    /**
     * A place's metre (v5.13): `spriteUnitsTall / unitsTall` of the place's own family, the scale the
     * renderer draws any building standing in it at -- every piece of the neighbourhood is one metre a unit.
     */
    private fun placeMetre(variant: SceneSpace.SceneVariant): Float =
        variant.spriteUnitsTall / NeighbourhoodTable.FAMILIES.getValue(variant).unitsTall

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
     *    repeat until one moves nothing, up to five; ShopFrontVisibilityTest re-measures all
     *    twelve themes independently.
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

    // ---- The plan of the places (v5.8E, V3-01; five places since v5.13B2) --------------------
    //
    // Every built-in street is built on one skeleton: each category lays candidate i at slot
    // (i + 0.5) / 10 and at a depth that grows with i, so on all twelve themes the far objects stand
    // at the left of the tile and the near trees, the near houses and their parasols at the right.
    // Measured on the five places of v5.13B2 against the street alone, every theme's right third is
    // closed to every place but at a spot or two by a trunk or a pole crossing its front, and the far places have clear
    // stretches only at the left: a place put where it is clear stands in the first two thirds of the
    // tile, and five of them leave a third of the tile -- two thirds of a screen -- with no building
    // among the houses. The proof of v5.13B
    // put them there and showed 39 fronts of 360 covered beyond the 40 % rule.
    //
    // So the plan lays the five places **along the whole tile**, one in each fifth: slots a fifth of
    // the tile apart, at a phase each street chooses, and each place within 0.04 of its slot, or within 0.06,
    // 0.08 or 0.10 where no street finds a plan nearer ([SLOT_REACHES]; New Year's needed it): two
    // neighbouring places 0.13 to 0.31 of the tile apart on the twelve streets (0.26 to 0.62 of a screen). Where a place's
    // front would be crossed by a trunk or a pole, or covered beyond the rule, the trees and parasols
    // in the way are moved -- **never their number, their colour, their autumn tone, their kind or whether
    // they stand at the theme's density**: a tree stands as near its old spot as it can where all of that holds
    // ([relocated]), a parasol on the other side of its own house. Houses never move. The places that
    // can stand where they are clear stand there, and a street moves as few trees and parasols as its
    // places allow (the instruction of v5.13B2: trees and parasols may move, never their number).
    //
    // The twelve streets still share out the frontage, the maintainer's condition of 2026-09-26 for the
    // width repair (row A18): no more than four themes put a place of one row of the size table --
    // restaurant, school, bar -- in one spot. Themes take turns, each choosing its best against the
    // others' current choice, until none moves. `StorefrontFrontVisibilityTest` and
    // `ShopFrontVisibilityTest` hold the rules and the result.
    //
    // **Since v5.10B the plan is computed when the table is generated, not when the app starts.** It
    // depends on nothing but constants of this code -- the twelve streets' seeds, the buildings' widths,
    // the occluders' boxes -- so it gives the same answer at every start, and it cost ~566 ms on the
    // BV6600's main thread when the app computed it (v5.10A). [ShopPlanTable] holds the answer: where
    // each street's places stand, and where each tree or parasol it moved stands. `ShopPlanTableTest`
    // runs [planStreets] on the JVM and fails when the table is not its output to the bit, and writes
    // the table it should be.

    /**
     * Every built-in street with its places placed: theme id to its static objects. Built once, from
     * [ShopPlanTable]: the streets as generated, each place and each tree or parasol the plan moved
     * where the plan put it.
     */
    private val SHOP_PLAN: Map<String, List<StaticSceneObject>> by lazy { shopPlanFromTable() }

    private fun shopPlanFromTable(): Map<String, List<StaticSceneObject>> =
        BUILT_IN_STREETS.associate { (id, tree) -> id to streetPlanned(streetBeforeShops(id, tree), ShopPlanTable.planFor(id)) }

    /**
     * What the plan does to one built-in street: where its places stand, deepest first ([placeX]), and
     * where each tree or parasol it moved stands now -- [movedIndex] the object's index in the street as
     * generated ([streetBeforeShops]), [movedX] its new `tileFractionX`.
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
     * The most of a front the plan lets be covered: a point under the rule's [SHOP_MAX_OCCLUSION], so that a
     * front the plan measures exactly is inside the rule however finely another measurement samples it
     * (`StorefrontFrontVisibilityTest` samples on a grid, and read 40.2 % on a front planned at 39.9 %).
     */
    private const val PLAN_MAX_OCCLUSION = SHOP_MAX_OCCLUSION - 0.01f

    /**
     * How far from its slot a place may stand, in [PLAN_STEPS]: 0.04 of the tile either side, then wider for
     * a street whose places cannot all stand that near their slots (New Year's, whose two large houses at
     * the front close its right third to every place).
     */
    private val SLOT_REACHES = intArrayOf(8, 12, 16, 20)

    /** The most streets whose places of one row of the size table may cover one spot: row A18's condition. */
    private const val MAX_STREETS_A_SPOT = 4

    /**
     * The tallest screen, height over width, the street is planned for: 21:9 and a little more. A building
     * is a share of the tile that grows with it, the tile being two screens wide and the scene scaling with
     * the screen's height.
     */
    private const val TALLEST_SCREEN = 2.4f

    /** How far the plan looks for a moved tree's new spot, in steps of [MOVE_STEP] either side: 0.3 of the tile. */
    private const val MOVE_REACH = 3000
    private const val MOVE_STEP = 0.0001f

    /**
     * The period of a candidate's density draw along the tile: [densityFraction] is the fraction of
     * `x * 7919` and the depth's term, so an object moved by a whole number of these keeps its draw, within
     * a float's rounding. A moved tree's spot is fine-tuned by them to keep its autumn tone
     * ([SceneObjectRenderer.fallToneOf]), at most [TONE_REACH] of them either side.
     */
    private const val DENSITY_PERIOD = 1f / 7919f
    private const val TONE_REACH = 200

    /**
     * How far a moved tree's or parasol's density draw may go from the one it had ([densityFraction]):
     * at the theme's own density it stands or not as it did, and at any other it does unless the slider
     * is within this of its draw.
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
     * Both species a street's tree slots can stand as -- the trees it plants and the palms the Palms
     * switch puts in their places, or on Beach and Desert the palms they plant and the trees the switch
     * puts back (decisions 37 and 39): a place is planned clear of both.
     */
    private fun speciesOf(street: List<StaticSceneObject>, tree: SceneObjectType): List<List<StaticSceneObject>> {
        val other = if (tree == SceneObjectType.TREE) SceneObjectType.PALM_TREE else SceneObjectType.TREE
        return listOf(street, street.map { if (it.type == tree) it.copy(type = other) else it })
    }

    /**
     * What one object puts in front of a place, for the plan: [occluderBoxes], but a house as tall as it
     * draws where its own deal stands it above its family's reference height -- a house dealt a storey more
     * covers more of what stands behind it ([NeighbourhoodComposer.deal], the renderer's deal, by the
     * silhouette the street dealt it).
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
     * Whether place [place], standing at [x] in [street] (each object where the plan has it), keeps both
     * rules for all six of its fronts in every species: no trunk or pole across it, at most [limit] covered by
     * everything nearer -- the other places with the widest of the six in each. [limit] is the plan's
     * [PLAN_MAX_OCCLUSION], a point inside the rule's [SHOP_MAX_OCCLUSION], unless the caller asks for the rule
     * itself. [species] is [speciesOf] the street.
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
     * Place [place] of a street at grid step [k], against the street alone (houses, trees, palms,
     * parasols; no other place): null where the houses alone cover a front past [PLAN_MAX_OCCLUSION];
     * otherwise the trees and parasols that would have to move -- every trunk or pole across its front
     * in either species, then, while a front is covered past [SHOP_TARGET_OCCLUSION], the one covering
     * most of it -- and whether the front is left in the ceiling tier (covered between the two, nothing
     * more to move), which [placeSpots] offers too with the trunks alone moved.
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
     * The plan itself, computed from scratch: for each built-in street, where its places stand and which
     * trees and parasols move where, or null for a street the plan could not place -- it then parks its
     * places with [separateShopFrontages]. See the section's comment.
     *
     * Not called by the app: [SHOP_PLAN] reads [ShopPlanTable], which is this function's output. Internal
     * for `ShopPlanTableTest`, which runs it and compares it with the table to the bit.
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
         * The most streets with a place of [row] over one point of the front from [x] - [half] to [x] + [half],
         * this one included, with the others' choices [chosen]: the condition of row A18 measured where this
         * street stands -- the most themes whose fronts of one row all cover one spot of the street.
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
         * The streets choosing in [order]: each in turn takes the first of its shortlist (best first) whose
         * trees and parasols can be moved and that puts no spot under more than [MAX_STREETS_A_SPOT] streets'
         * fronts of one row, or else the least crowded that can be moved; rounds until nobody changes (at most
         * nine). Who chooses first takes the spots that move the fewest trees, so the plan tries every order
         * of a few and keeps the one that moves the fewest in all.
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
     * Whether a screen can show all of a street's places at once, places at grid steps [k] with half-widths
     * [half] at the reference viewport: whether some gap between two neighbours, with the two buildings'
     * widest halves on either side of it, leaves the rest within one screen -- half the tile -- on the
     * tallest screen the street is planned for ([TALLEST_SCREEN], where a building is the widest share of
     * the tile). The street never puts the same building twice in four places in a row
     * ([StorefrontStreet]), so a screen must never show five.
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
     * The street [id] with its places at [placeX] and the trees and parasols [moves] moved, as a
     * [StreetPlan], or null where one of them cannot be moved or a place is left outside the rules.
     *
     * A tree moves to the nearest spot, either side, a [MOVE_STEP] at a time and at most [MOVE_REACH]
     * steps, where it is still the tree it was -- it stands at the theme's own density as it did, its
     * density draw is within [MOVE_DENSITY_SLACK] of its own, it wears the same one of its two colours and,
     * with the fall colours on, the same autumn tone ([SceneObjectRenderer.fallToneOf]), and it is a fir
     * under the Christmas layer exactly when it was ([SceneObjectRenderer.isFirSeed]) -- and
     * where no place is crossed by its trunk or covered past the rule because of it. A parasol moves to the
     * other side of its own house, at [PARASOL_SIDE_OFFSET] from it give or take a few thousandths so that
     * it keeps its density and its colour the same way. Then every place is measured again, in both species.
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
                    // And its autumn tone (v5.13B2): from there, the nearest spot a whole period of its density
                    // draw away ([DENSITY_PERIOD]) where it wears the tone it wore too -- within seven thousandths
                    // of the tile on the twelve streets, under ten pixels of the BV6600.
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

    // ---- The places of a saved street (v5.13B2) -----------------------------------------------
    //
    // A saved version of a built-in theme, a theme of the user's own, a theme file and a backup keep the
    // street as it stood when it was saved: its places where that release planned them, each for the one
    // building that stood there until v5.13. Since v5.13 any of the six stands in any place, and a wider
    // building is crossed by a trunk in 25 fronts of 432 on the streets the 5.12 saved (Autumn's, Christmas's
    // and New Year's near place; Desert's school with its palms switched off) and in one of 432 on those the
    // 5.13B saved (the same school; `StorefrontFrontVisibilityTest`). The maintainer, asked mid-round with the
    // count: *«Sposta quel posto (consigliato)»* (2026-10-13) -- the place that is crossed moves, once and only
    // as far as it must, and nothing else of the theme changes: its buildings keep their colours and people
    // ([SceneObjectLayout.placesAsSaved]).

    /** How far a saved street's place is moved at most, in [SAVED_PLACE_STEP]s either side: half the tile. */
    private const val SAVED_PLACE_REACH = 500

    /** A saved street's place moves a thousandth of the tile at a time: 1.4 px of the BV6600. */
    private const val SAVED_PLACE_STEP = 0.001f

    /**
     * [objects] -- a saved street, as it was stored -- with each place among the houses where all six
     * buildings keep both rules, with its trees and with the other species the Palms switch puts in their
     * places ([placeClear], [SHOP_MAX_OCCLUSION]): a place that already does stays where it is, which is every place of a street
     * saved since v5.13B2 and almost every one before; one that does not moves to the nearest spot either side,
     * a [SAVED_PLACE_STEP] at a time, where it does by the plan's own margin ([PLAN_MAX_OCCLUSION]) and where
     * it stands clear of every other place. The
     * nearest place first, so a deeper one is measured against the nearer ones where they end up. Nothing else
     * moves: houses, trees, parasols, towers and decorations stand as they were saved, and so does a place no
     * spot along the tile clears. The same list back when nothing moves.
     *
     * Read on every load of a saved street (`sceneObjectLayoutFromJson`), as the traffic is, and the same street
     * gives the same places every time. A place it moves remembers where the theme stored it
     * ([SceneObjectLayout.placesAsSaved]): the six buildings take their colours and people from there, and a
     * save writes it back there, so the theme as stored never changes.
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
     * The depth every generated layout stands its school at, on every theme.
     *
     * Derived from the generator's own arithmetic rather than typed: the middle, by depth, of the
     * candidates [generateStaticCandidates] spreads over `0.0..0.80` that fall in the school's
     * third -- the same choice [singleShopPerVariant] makes. With ten candidates that is candidate
     * 7, at 0.6222, and a school added to an old saved layout stands exactly where a freshly
     * generated one does, so the two cannot be told apart by their size or their row.
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
     * The school a layout saved before v5.6 never had, or null when it needs none -- item 141.
     *
     * ### Why a saved layout has no school, and why nothing in it can become one
     *
     * A saved theme freezes the objects that were **standing** when it was saved --
     * `snapshotEntry` writes `rawLayout.staticObjects.filter { keepCandidate(it) }` -- not the ten
     * candidates they came from. Before v5.6 the shop band had two halves, so a saved street
     * carries a restaurant at 0.4444, a bar at 0.7111 and the towers its density kept; nothing
     * stands in the school's third (0.4667..0.6333), and every tower that *is* there is one the
     * user can see. Promoting one would therefore always take a building off the user's skyline
     * to put it on their street. This adds instead: **one** school, and nothing that was already
     * saved moves, changes colour or changes drawing.
     *
     * ### Where it stands
     *
     * At [GENERATED_SCHOOL_DEPTH], so its row and its size are the generated school's. Its x is
     * chosen among the hundredth-of-a-tile probes around the point halfway between the saved
     * restaurant and bar (the way the generator's three shops stand in depth order along the
     * street), under the rules [separateShopFrontages] applies -- no trunk or pole across its
     * front, its front covered by at most [SHOP_MAX_OCCLUSION] -- and three that are all "it
     * moves nothing else":
     *
     *  - **no tree steps aside.** The generator's last resort moves trees; here the school parks
     *    at its least-covered probe instead, because those trees are the user's;
     *  - **it may not stand in front of a shop the user already has.** A probe whose body would
     *    cover any part of the saved restaurant's front is refused outright -- not held to the
     *    40 % ceiling the generator allows itself, because the first measurement of this rule
     *    with the ceiling let Beach's school take its trattoria from 15.5 % covered to 36.4 %,
     *    which is within the rule and is still the user's restaurant half hidden by a building
     *    they never asked for;
     *  - **of the probes left, the one that hides least and is hidden least wins**: the share of
     *    its own front the nearer objects cover, plus the largest share of any saved house's
     *    body it covers. The second term is there because the first measurement placed
     *    Halloween's school over 92 % of a small house standing 0.002 in depth behind it -- two
     *    buildings of one row drawn into each other -- and a first-acceptable scan cannot see
     *    that, because the frontage rules only ever protected shops. Ties go to the probe nearer
     *    the midpoint.
     *
     * Its silhouette is the school catalogue's only entry (0), which is what the generator deals
     * it. Its size variation is 1, the mean of the generator's roll: there is no roll to repeat,
     * and the mean is the one value that is not a choice.
     *
     * Null when the layout already has a building in the school's third (every layout saved from
     * v5.6 on, and any older one whose storefront repair left one there), or has no shop at all
     * (a street saved with its buildings switched off, where a lone school would be the only
     * commercial building in a scene that deliberately had none).
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
        // A place among the houses: as tall as the tallest of the six at its metre (v5.13; see
        // [halfWidthUnits] for the width). A trunk crosses this box exactly when it crosses the widest of
        // the six fronts -- each reaches the ground -- so the crossing rule is the six's, exactly.
        val tall = if (isShop(shop)) Storefront.ALL.maxOf { it.family.unitsTall } * placeMetre(v) else v.spriteUnitsTall
        return floatArrayOf(x - half, g - tall * s, x + half, g)
    }

    /**
     * The fronts [shop] can show: a place among the houses (v5.13) two per building the street may stand
     * in it, each its own width at the place's metre ([Storefront.halfWidthUnits]) and as tall as its
     * family declares and as tall as it draws ([Storefront.inkTallUnits], v5.13B2), so a front that is
     * clear for one building and covered for another, or clear over its declared height and covered over
     * its ink, is measured covered; anything else its one [frontRect].
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
     * What one object puts between the viewer and anything behind it, as (left, top, right,
     * bottom) boxes at the reference viewport. A building is its body; a tree, a palm and a
     * parasol are each a crown (see [crownBoxes]) AND a vertical member -- the tree's 32x62-unit
     * forked stem at TreeSpriteLayout.TRUNK_X/Y, the palm's leaning spindle, the parasol's pole.
     *
     * **v4.21 re-derived the tree's two boxes and they grew in both directions.** The crown went
     * from ±41 to ±51 and its lower edge from -44 to -52; the trunk went from a 10-unit rod to a
     * 32-unit forked stem reaching -62. Nothing here is a taste judgement -- each number is the
     * shipped sprite's content box at its blit origin -- but the consequence is real: a tree now
     * shadows a shop front from further away and its stem is three times as wide, so the
     * separation pass below has strictly more work to do than it did in v4.20. That the twelve
     * built-in layouts still settle inside the same 40% ceiling is measured, not assumed, and
     * `ShopFrontVisibilityTest` is where it is measured.
     *
     * **v5.2 took the three crowns out of this function**, because their numbers were the sprite
     * *canvases* rather than the drawings on them and one of them was two thirds air. They now
     * come from [crownBoxes], which reads an ink profile measured off the artwork itself; the
     * vertical members are untouched and stay here as literals, because a trunk box is a
     * deliberately strict reading of a shape that narrows above its foot ([verticalMemberBox])
     * and not a claim about where the ink is.
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
            // v5.1: the crown blitted at (-21,-82) on a 56x48-unit canvas, and a trunk that leans
            // -- its 21-unit canvas sits at -8, and the bark inside it spans x 0.67..20, so the
            // box is not symmetric about the foot.
            SceneSpace.SceneVariant.PALM_TREE ->
                crownBoxes(SpriteOccluderTable.PALM_CROWN, x, g, s) +
                    floatArrayOf(x - 8f * s, g - 58f * s, x + 13f * s, g)
            SceneSpace.SceneVariant.PARASOL ->
                crownBoxes(SpriteOccluderTable.PARASOL_FAN, x, g, s) +
                    floatArrayOf(x - 2.5f * s, g - 50f * s, x + 2.5f * s, g)
            SceneSpace.SceneVariant.HOUSE_SMALL, SceneSpace.SceneVariant.HOUSE_LARGE,
            SceneSpace.SceneVariant.TOWER,
            -> listOf(floatArrayOf(x - halfWidthUnits(v) * s, g - v.spriteUnitsTall * s, x + halfWidthUnits(v) * s, g))
            // A place among the houses puts in front the widest and tallest of the six (v5.13): whoever
            // stands there, nothing larger can.
            SceneSpace.SceneVariant.RESTAURANT, SceneSpace.SceneVariant.BAR, SceneSpace.SceneVariant.SCHOOL,
            -> listOf(frontRect(o))
            else -> emptyList()
        }
    }

    /**
     * The occluding rectangles of one foliage family, at the reference viewport.
     *
     * **This is the v5.2 half of item 124, and the numbers it reads are not written down here.**
     * Every box a crown declared used to be its whole sprite canvas, so a palm fan that is 51%
     * ink and 49% air declared 100% of a 56x48-unit rectangle solid -- and the separation pass
     * walked the desert's bar nineteen hundredths of a tile away from a frontage the audit's
     * photograph shows is plainly legible behind that fan. [SpriteOccluderTable] carries the ink
     * profile, measured off the shipped PNGs by `tools/assets/build_occluder_table.py`; what stays
     * here is the *model*, which is a judgement and is therefore written a second time in
     * `ShopFrontVisibilityTest` rather than imported from here.
     *
     * Two steps, and only the second is a choice:
     *
     *  1. **The box is the drawing's content, not its canvas.** `normalize` leaves two pixels of
     *     transparent guard on each side because a `SCENE_UNITS` blit is filtered and cropping
     *     onto the ink makes the sampler clamp (v4.31, measured on the lake sprites). That margin
     *     belongs in the PNG and has never belonged in an occlusion box: for the palm it was 3.67
     *     units of declared-solid air above the topmost leaf. This step also retires the oak box's
     *     one-unit-too-wide left edge without anyone typing 50 in place of 51.
     *  2. **The box is then no wider than the drawing's widest row and no taller than its tallest
     *     column**, about the content's own centre.
     *
     * **Why the extremes and not the area.** The obvious model is `k = sqrt(coverage)` about the
     * centre, which gives the rectangle the area the ink has. It was implemented and measured,
     * and so was a two-band variant that keeps full height and narrows each half to its own
     * coverage. All three were run over the same twelve generated layouts:
     *
     * | model | shops that move | themes |
     * |---|---|---|
     * | sqrt(coverage) about the centre | 7 | 6 |
     * | two bands, each narrowed to its own coverage | 9 | 7 |
     * | **widest row x tallest column** | **1** | **1 (desert)** |
     *
     * The first two shrink a *filled* shape as hard as a sparse one, because area cannot tell the
     * difference between ink that is missing and ink that is spread out: they take 13% off each
     * side of the oak, whose canopy is a solid five-lobe blob, and they take the parasol's fan in
     * by 11% although it is a filled half-disc with no hole in it at all. Everything the oak and
     * the parasol then stop covering is a shop that moves for no reason anybody looking at the
     * frame could name, and six of the seven moves are exactly that.
     *
     * This model cannot make that mistake, and the reason is worth stating as a property rather
     * than as a result: **a rectangle is only narrowed by as much as the drawing never reaches.**
     * For a shape whose bounding box is touched along a full row and a full column -- every convex
     * filled silhouette, the oak's canopy and the parasol's half-disc included -- both factors are
     * 1 and the box is unchanged. It moves only where no single row is ever full, which is the
     * definition of a raggiera, and that is the only family item 124 was ever about. It is also
     * the conservative one of the three: its box is the largest, so of the three it is the least
     * able to declare a shop visible that is not.
     *
     * A family is every drawing that may stand in that place -- three palm crowns, two oak crowns
     * -- because the layout is generated per theme while the crown is picked at draw time from
     * user-editable flags, so the pass cannot know which one it will be. Their boxes are returned
     * as a LIST rather than merged into one bounding rectangle: [frontCoverage] takes an exact
     * union, so overlapping rectangles cost nothing and a bounding box would hand back some of the
     * air this whole method exists to stop declaring.
     *
     * **What is not modelled, and was not before either**: `drawTree` rotates the crown by up to
     * four degrees of sway, and a fir stands in a leafy tree's place under this same variant with
     * a silhouette this box has never described. Both are pre-existing and neither was widened
     * here; `V5_2A_REPORT.md` section 8 carries them as found.
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
            // v4.21: the forked stem's widest point is its flared foot, 32 units across, and it
            // stands 62 tall. Modelling it as that full rectangle is deliberately the strict
            // reading -- the stem is a V and narrows above the foot -- because the rule this box
            // serves is "a door and a window stay clear for their whole height", and a box that
            // under-reports the foot would let a trunk park on a doorway.
            SceneSpace.SceneVariant.TREE -> floatArrayOf(x - 16f * s, g - 62f * s, x + 16f * s, g)
            // v5.1: the leaning trunk's own canvas, x -8..13 at this origin. The strict reading
            // the TREE entry above argues for applies here too -- the spindle narrows above its
            // flared foot, and a box that under-reported the foot would let a trunk park on a
            // doorway.
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
     * copy) clipped to it. The worst of a place's fronts is its reading (v5.13, [frontRects]).
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
     * Standalone seasonal decorations (snowmen, gifts, penguins, bunnies, Easter eggs,
     * pumpkins), generated for *every* theme regardless of which one it "traditionally" belongs
     * to -- these used to be hardcoded per theme (Christmas got snowmen+gifts, Easter got
     * eggs+bunnies, etc.) with no way to turn them off or use them anywhere else. They're now
     * exactly like the 5 uniform categories above: a fixed set of candidate slots per type, with
     * visibility/density/color entirely controlled by the user via the "Seasons & decorations"
     * screen (separate from "World & scene", since these are opt-in extras rather than a
     * structural part of every scene) -- see [SceneCustomization]. All default to *invisible*
     * so nothing changes for a
     * theme until the user explicitly turns something on -- but once turned on, it now shows up
     * on every theme, not just its old "traditional" one, which is the whole point: nothing stops
     * a user from wanting pumpkins at Christmas or snowmen on the beach.
     *
     * These are the smallest objects in the scene -- a rabbit is half a metre tall -- so they are
     * placed across the near half of the band (0.45-1.0) where the perspective still gives them
     * enough size to read as themselves.
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
    private fun builtinLayoutFor(themeId: String, accentColor: Int): SceneObjectLayout? {
        val treeType = BUILT_IN_STREETS.firstOrNull { it.first == themeId }?.second ?: return null
        return uniformCandidates(themeId, accentColor, treeType)
    }

    /**
     * The twelve built-in streets, in the order the shop plan visits them, with the tree each plants:
     * palms, not broadleaf woodland, on Desert and Beach (the tree *type* is a property of the
     * layout, not a special case in the renderer); Halloween's broadleaf woodland is stripped to
     * bare branches by its flag.
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
