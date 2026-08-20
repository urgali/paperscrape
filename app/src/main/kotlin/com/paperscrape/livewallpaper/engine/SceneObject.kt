package com.paperscrape.livewallpaper.engine

import kotlin.random.Random

/** The kind of object placed in the scene. Drives both drawing shape and reaction behavior. */
enum class SceneObjectType {
    CAR, HOUSE, TREE,
    // Seasonal / festive additions (step 2)
    SNOWMAN, GIFT, PALM_TREE, PARASOL, SKYSCRAPER, PENGUIN, BALLOON,
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
 * discrete `layer: Int` row index (0..8) -- the reference app's own decompiled `Scene.
 * createBuildingsRanged`/`createTreesRanged` place every single object at its own continuous
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

/** The 4 vehicle types that can appear on the road, matching the reference's own real vehicle
 * sprites (plain car, police car, taxi, fire truck). Only [PLAIN] is user-recolorable via the
 * "Cars" category color pickers -- the other 3 use fixed, real-world-associated colors (a police
 * car isn't "your theme's accent color", it's black-and-white) exactly like the reference's own
 * taxi/fire-truck/police-car sprites are fixed-color, non-tintable art (see the reference app's sprite
 * export's own MANIFEST.md, `road1.png` entry, for the source this was checked against). */
/**
 * The vehicles on the road.
 *
 * [carriesPassengers] is a property of the type rather than a list of exclusions at the call site:
 * a police car and a fire engine are crewed, not travelled in, and a child in the back of either
 * reads as something being wrong. Stated this way, a service vehicle added later is excluded by
 * default rather than by somebody remembering to exclude it.
 */
enum class CarType(val carriesPassengers: Boolean) {
    PLAIN(true),
    TAXI(true),
    POLICE(false),
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
 * Seasonal decorations (snowmen, gifts, balloons, penguins, bunnies, Easter eggs, pumpkins) are
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

    /** Same for every theme and every one of the 5 structural customizable categories -- see the
     * class doc. [seasonalDecorationCandidates] uses this same constant for its own categories. */
    const val CANDIDATES_PER_CATEGORY = 10

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

    // --- Uniform candidate generation for the 6 customizable categories --------------------

    /**
     * Generates [CANDIDATES_PER_CATEGORY] candidate slots for one customizable category, spread
     * across the screen with a little position jitter (so they don't look like a rigid grid).
     * Deterministic per (seed) so the same theme always generates the same candidate layout --
     * never `Random()` without a fixed seed.
     *
     * [depthRange] is a continuous sub-range of the overall 0..1 depth band (see
     * [StaticSceneObject.depthFraction]'s own doc comment) -- candidate `i` of [n] gets
     * `depthRange.start + (i/(n-1)) * (depthRange.end - depthRange.start)`, matching the
     * reference's own continuous within-range placement fraction rather than snapping to a fixed
     * number of discrete rows.
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
     * This mirrors the reference app's own decompiled placement mechanism for houses/buildings:
     * `Scene.createBuildingsRanged` is called twice per theme, once with `RangeType.Top` (a band
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
    private fun generateCarCandidates(seed: Int, accentColor: Int): List<CarObject> {
        val rnd = Random(seed)
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
                type = pickCarType(rnd),
            )
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
        val staticObjects =
            generateStaticCandidates(SceneObjectType.SKYSCRAPER, seed + 2, 0.0f..0.80f) +
                generateSplitStaticCandidates(SceneObjectType.HOUSE, seed + 1, houseBack, houseFront) +
                generateStaticCandidates(treeType, seed + 5, 0.18f..1.0f) +
                generateSplitStaticCandidates(SceneObjectType.PARASOL, seed + 4, houseBack, houseFront)
        val cars = generateCarCandidates(seed + 6, accentColor)
        return SceneObjectLayout(staticObjects = staticObjects, cars = cars)
    }

    /**
     * Standalone seasonal decorations (snowmen, gifts, balloons, penguins, bunnies, Easter eggs,
     * pumpkins), generated for *every* theme regardless of which one it "traditionally" belongs
     * to -- these used to be hardcoded per theme (Christmas got snowmen+gifts, Easter got
     * eggs+bunnies, etc.) with no way to turn them off or use them anywhere else. They're now
     * exactly like the 5 uniform categories above: a fixed set of candidate slots per type, with
     * visibility/density/color entirely controlled by the user via the "Seasonal Decorations"
     * screen (separate from "Scene Objects", since these are opt-in extras rather than a
     * structural part of every scene) -- see [SceneCustomization]. All default to *invisible*
     * (matching the reference app's own default-unchecked convention), so nothing changes for a
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
            generateStaticCandidates(SceneObjectType.BALLOON, seed + 103, 0.05f..0.45f) +
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
        else -> null
    }
}
