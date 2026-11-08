package com.paperscrape.livewallpaper.engine

import kotlin.math.roundToInt

/**
 * Which of a theme's car candidates are on the road at a given density setting.
 *
 * ### Why a count and not a per-candidate threshold
 *
 * A selection by `stableFraction(spec) < density` -- an independent coin per candidate, weighted
 * by the slider -- approximates a density on a large population; on ten candidates it deals one
 * fixed hand per fraction and says nothing about *which* slots survive: at 35% it can keep
 * adjacent slots, two cars 0.32 of a loop apart that read as a queue -- few cars that look like
 * overcrowding. The slots are uniform around the loop and keep their spacing; the *choice among
 * slots* is what has to be made.
 *
 * So the density is a **count**, and the count picks slots explicitly:
 *
 *  - **1.0 keeps all ten slots.** That is what the loop's own spacing supports.
 *  - **0.0 keeps no car**, and anything above it at least one (`DESIGN_NOTES.md` decision 104):
 *    0 % on a slider is no car at that hour, as every amount at 0 % is nothing. Whether the road
 *    stays with no car on it is *Keep the road*'s ([SceneObjectRenderer.drawsRoad]).
 *
 * ### The two properties the pick must have
 *
 * 1. **Maximum spacing at every count.** For n kept slots in a lane, the largest minimum
 *    circular gap the five slots allow. [LANE_SLOT_ORDER] achieves it at every prefix — see its
 *    own doc for the derivation.
 * 2. **Nesting.** The set at count n contains the set at count n−1, so dragging the slider up
 *    *adds* a car and never reshuffles the road. Taking prefixes of one fixed order gives this
 *    by construction.
 *
 * ### Where the seed may come from, and where it must not
 *
 * Which lane carries the first car, and which slot in it, comes from the **theme id's** hash —
 * the same seed every other per-theme effect already uses — never from the candidate's own
 * fields. A candidate's identity spans exactly ten values (see
 * [SceneObjectCatalog.candidateIndexOf]), so anything derived from it is one fixed hand for
 * every theme the app ships; the theme id ranges over every string a theme can be called, which
 * is what makes "the same street on every device, a different street on another theme" possible
 * at all. One theme therefore always shows the same sporadic car, and different themes show it
 * in different lanes.
 */
object CarSelection {

    /**
     * The order one lane's five slots are filled in: each prefix of it has the largest minimum
     * circular gap that many slots can have on a five-slot loop.
     *
     * Derivation. The five slots sit uniformly on the loop, one gap unit apart (0.32 of the
     * span). For n of five, the minimum gap over the chosen set can be at most ⌊5/n⌋:
     * {0} — no gap to speak of; {0,2} — gaps 2 and 3, minimum 2 = ⌊5/2⌋; {0,2,4} — gaps
     * 2,2,1, minimum 1, and 1 is the ceiling because 3 slots with gaps ≥2 would need ≥6 slots;
     * {0,2,4,1} and the full set likewise. So every prefix of `0,2,4,1,3` is optimal, which is
     * the property that lets one fixed order serve every count and keep the sets nested.
     */
    internal val LANE_SLOT_ORDER = intArrayOf(0, 2, 4, 1, 3)

    /**
     * How the 0..1 density above 0 maps onto the way from 1 car to all of them.
     *
     * Linear — the slider's midpoint puts half the possible traffic on the road. It is the **one
     * judgement in this feature that belongs to the eye**: the maintainer chose linear over 1.5 and
     * 2.0 (progressively sparser mid-range) on the device's captures at 35%. The exponent is the
     * whole difference between those curves, so the choice lands here and nowhere else.
     */
    internal const val COUNT_CURVE_EXPONENT = 1.0f

    /**
     * How many of [available] candidates render at [density].
     *
     * 0 at d=0 (decision 104), and above it `1 + round(curve(d) · (available − 1))`: at least 1
     * just above 0 and exactly [available] at d=1, by arithmetic rather than by clamping.
     * [available] is the theme's own inventory — ten for every built-in, possibly fewer for a custom
     * theme saved by an older build — so the endpoints mean the same thing on any layout.
     */
    fun countFor(density: Float, available: Int): Int {
        if (available <= 0) return 0
        val d = density.coerceIn(0f, 1f)
        if (d <= 0f) return 0
        val curved = if (COUNT_CURVE_EXPONENT == 1.0f) d else Math.pow(d.toDouble(), COUNT_CURVE_EXPONENT.toDouble()).toFloat()
        return 1 + (curved * (available - 1)).roundToInt()
    }

    /**
     * The order the ten candidate slots are put on the road in, as candidate indices
     * (see [SceneObjectCatalog.candidateIndexOf]: `queue slot * 2 + (near ? 0 : 1)`).
     *
     * Lanes alternate from the first entry on, so the two counts never differ by more than one
     * and neither lane drains before the other. The seed decides three things, read as digits so
     * each decision is one factor and they cannot collapse into each other: bit 0 picks the lane
     * of the very first car, and the next two base-5 digits rotate each lane's fill order around
     * its loop. A rotation preserves every circular gap, so the spacing property survives it.
     */
    fun selectionOrder(themeSeed: Int): IntArray {
        val firstLaneNear = Math.floorMod(themeSeed, 2) == 0
        val rest = themeSeed / 2
        val rotationNear = Math.floorMod(rest, 5)
        val rotationFar = Math.floorMod(rest / 5, 5)
        return IntArray(SceneObjectCatalog.CANDIDATES_PER_CATEGORY) { rank ->
            val near = (rank % 2 == 0) == firstLaneNear
            val rotation = if (near) rotationNear else rotationFar
            val slot = (LANE_SLOT_ORDER[rank / 2] + rotation) % SceneObjectCatalog.CAR_SLOTS_PER_LANE
            slot * 2 + if (near) 0 else 1
        }
    }

    /**
     * Each entry of [cars] given its selection rank: the car ranked r is the (r+1)th to join the
     * road as the count rises, so **an entry renders at count n exactly when its rank is below n**.
     *
     * This is the whole selection reduced to one comparison, and that is the point: with the
     * night crossfade the count is a function of `dayBlend` and moves on its own at dusk and dawn,
     * so the renderer re-evaluates membership **every frame**. Ranks are computed once per scene
     * build; per frame nothing allocates and nothing sorts — each runtime compares its stored rank
     * against the frame's count.
     *
     * Ranking is by each car's candidate index in [selectionOrder]; two cars on the same slot (a
     * theme carrying more cars than one loop holds wraps its indices) tie-break by list position,
     * so the result is deterministic for any inventory.
     */
    fun selectionRanks(cars: List<CarObject>, themeSeed: Int): IntArray {
        val ranks = IntArray(cars.size)
        if (cars.isEmpty()) return ranks
        val order = selectionOrder(themeSeed)
        val rank = IntArray(order.size)
        order.forEachIndexed { r, candidateIndex -> rank[candidateIndex] = r }
        cars.indices
            .sortedWith(compareBy({ rank[SceneObjectCatalog.candidateIndexOf(cars[it])] }, { it }))
            .forEachIndexed { position, index -> ranks[index] = position }
        return ranks
    }

    /**
     * Which entries of [cars] render at [density], as a mask over the list's own indices.
     *
     * A mask rather than a filtered list because the renderer keeps a runtime per *inventory*
     * entry and only toggles whether it draws — see `SceneObjectRenderer`'s car membership sync.
     * The mask is [selectionRanks] compared against [countFor] through [isKept], the one
     * rank-against-count rule, which `SceneObjectRenderer` applies to its per-inventory runtimes
     * too (`buildCarRuntimes`, and per frame in `update`): a renderer that re-stated the comparison
     * instead of calling it would make every test of the cars a density keeps a test of a copy and
     * not of the one that draws.
     */
    fun keptMask(cars: List<CarObject>, density: Float, themeSeed: Int): BooleanArray {
        val ranks = selectionRanks(cars, themeSeed)
        val n = countFor(density, cars.size)
        return BooleanArray(cars.size) { isKept(ranks[it], n) }
    }

    /** Whether the car of selection rank [rank] drives when [count] cars do: the lowest ranks do. */
    fun isKept(rank: Int, count: Int): Boolean = rank < count

    /**
     * The car density in force at [dayBlend], linearly between the day and night settings.
     *
     * Deliberately [PeopleDensity.at] and not a copy of it: the crossfade-not-threshold rule and
     * its arithmetic are one model with two users, and a duplicate would only guard against a
     * typo while letting the two drift. The blended
     * density feeds [countFor] — the count is where a car density becomes cars, and there is no
     * parallel night threshold anywhere.
     */
    fun densityAt(dayDensity: Float, nightDensity: Float, dayBlend: Float): Float =
        PeopleDensity.at(dayDensity, nightDensity, dayBlend)

    /**
     * Whether a car at [progress] is off the visible span of its loop, which is the only moment
     * its membership may change.
     *
     * The bounds are the draw cull's own (`SceneObjectRenderer` skips a car outside them), so a
     * car that is not drawn this frame is exactly a car whose appearance or disappearance cannot
     * be seen. A pedestrian materialising mid-pavement is forgiven; a car materialising in the
     * middle of the road is not — and the same rule lets the night crossfade ([densityAt]) move
     * the count without ever touching a car that is on screen.
     */
    fun offScreen(progress: Float): Boolean =
        progress < ON_SCREEN_MIN_PROGRESS || progress > ON_SCREEN_MAX_PROGRESS

    /** The visible span of the loop, shared with the draw cull so the two cannot disagree. */
    const val ON_SCREEN_MIN_PROGRESS = -0.05f
    const val ON_SCREEN_MAX_PROGRESS = 1.05f
}

/**
 * The cars of [cars] this configuration keeps at full daylight (its day density), in inventory
 * order. Only the tests call it; the renderer applies the same [CarSelection.isKept] per runtime,
 * per frame, on the day/night blend.
 *
 * The set-level replacement for the per-candidate `keepCar` threshold — see [CarSelection] for
 * why membership cannot be decided one candidate at a time. [themeSeed] is the theme id's hash,
 * the same seed the renderer's other per-theme effects use.
 */
fun SceneCustomization.keptCars(cars: List<CarObject>, themeSeed: Int): List<CarObject> {
    if (!this.cars.visible) return emptyList()
    val mask = CarSelection.keptMask(cars, this.cars.density, themeSeed)
    return cars.filterIndexed { index, _ -> mask[index] }
}
