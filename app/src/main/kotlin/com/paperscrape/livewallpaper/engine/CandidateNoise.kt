package com.paperscrape.livewallpaper.engine

/**
 * Deterministic, index-addressed randomness for scene effect candidates.
 *
 * ### What this replaces
 *
 * Every effect used to build a fresh `Random(theme.id.hashCode() + salt)` **on every frame** and
 * read from it *sequentially*, one `nextFloat()` at a time, as it walked its candidate list. That
 * had three separate consequences:
 *
 *  1. an object allocation per effect per frame, on the draw path;
 *  2. a candidate's attributes depended on **how many earlier candidates had been skipped**,
 *     because a skipped candidate did not consume from the stream — so lowering the density slider
 *     by one notch gave every surviving cloud, raindrop and bird different values and the whole
 *     field jumped;
 *  3. the same jump on every hourly live-weather refresh, since weather writes the same density
 *     and intensity inputs.
 *
 * Here a candidate's values are **addressed** rather than **consumed**: `value(seed, index,
 * channel)` is a pure function, so candidate 17's drift speed is the same number whether it is the
 * only survivor or one of ninety, and whether it is read once or not at all.
 *
 * ### Determinism
 *
 * `seed` is derived from `theme.id.hashCode()`, and `String.hashCode` is specified exactly by the
 * Java language, so a given theme produces the same scene on every device and every run. Nothing
 * here consults the clock, the platform or any mutable state.
 *
 * ### Cost
 *
 * A handful of integer operations — comparable to the `nextFloat()` it replaces, minus the
 * allocation. No state, so nothing to invalidate when the theme, the customization or the screen
 * size changes: the next frame simply computes different values.
 */
internal object CandidateNoise {

    // Channel ids. One per attribute, so that adding an attribute to an effect cannot disturb the
    // values of the attributes already in use -- which is exactly the failure mode of a
    // sequentially-consumed stream.
    const val CH_X = 1
    const val CH_Y = 2
    const val CH_SCALE = 3
    const val CH_SPEED = 4
    const val CH_PHASE = 5
    const val CH_LENGTH = 6
    const val CH_WIDTH = 7
    const val CH_HEIGHT = 8
    const val CH_VARIANCE = 9

    /**
     * A stable pseudo-random value in `[0, 1)` for one attribute of one candidate.
     *
     * Uses the MurmurHash3 32-bit finalizer, which is the standard avalanche function for exactly
     * this job: cheap, allocation-free, and thorough enough that adjacent indices and adjacent
     * channels produce uncorrelated results. That matters because the inputs here are extremely
     * regular -- consecutive small integers -- and a weaker mix would leave visible structure in
     * the scene.
     */
    fun value(seed: Int, index: Int, channel: Int): Float {
        var h = seed xor (index * -0x61c88647) xor (channel * -0x7a143595)
        h = h xor (h ushr 16)
        h *= -0x7a143595
        h = h xor (h ushr 13)
        h *= -0x3d4d51cb
        h = h xor (h ushr 16)
        // Top 24 bits give a uniform value in [0,1) with no modulo bias.
        return (h ushr 8) / 16_777_216f
    }

    /** [value] mapped onto `[min, max)`. */
    fun range(seed: Int, index: Int, channel: Int, min: Float, max: Float): Float =
        min + value(seed, index, channel) * (max - min)
}

/**
 * Decides which candidates of a fixed pool are present at a given density.
 *
 * ### The rule
 *
 * Each candidate has a fixed threshold in `[0, 1)`; it is present when `threshold < density`.
 * Because the threshold depends only on the candidate's index and the effect, density behaves the
 * way its name promises:
 *
 *  - **linear** — density `d` keeps about `d * poolSize` candidates;
 *  - **monotone** — raising density only ever adds; lowering it only ever removes;
 *  - **stable** — a candidate that stays keeps every one of its attributes, because none of them
 *    is derived from the density or from the number of survivors.
 *
 * ### Why a golden-ratio sequence and not a hash
 *
 * A hashed threshold would be independent per candidate, which for a small pool clumps badly: with
 * four candidates at 50% you would often get one or three rather than two. `frac(i * φ + offset)`
 * is the canonical low-discrepancy sequence in one dimension — by the three-distance theorem the
 * surviving indices stay evenly spread at every density and every pool size.
 *
 * It also replaces a subtly broken predecessor. The old threshold was
 * `((i * 7919 + salt * 131) % 1000) / 1000`, and `salt * 131 % 1000` collapses to the same value
 * for any two salts differing by a multiple of 1000 — which the salts in use did. Birds (7001),
 * clouds (8001), precipitation (9001) and sailboats (6001) therefore shared one identical threshold
 * sequence and selected the same candidate indices at the same density. [offsetFor] derives the
 * offset from the effect id instead, so effects genuinely decorrelate.
 */
internal object CandidateThreshold {

    /** Fractional part of the golden ratio; the standard low-discrepancy step. */
    private const val GOLDEN_RATIO_FRACTION = 0.618033988749895f

    /** The threshold for candidate [index] of the effect whose offset is [effectOffset]. */
    fun of(index: Int, effectOffset: Float): Float {
        val raw = index * GOLDEN_RATIO_FRACTION + effectOffset
        return raw - kotlin.math.floor(raw)
    }

    /**
     * The threshold offset for an effect, given its ordinal from [EffectId].
     *
     * Evenly spaced rather than hashed. Hashing looked natural and was tried first, but hashed
     * offsets are independent uniforms, so with nine effects two of them landed 0.008 apart and
     * consequently selected identical candidate sets at most densities — caught by
     * `CandidateSystemTest`. Even spacing makes the minimum separation `1 / EffectId.COUNT` by
     * construction, which cannot degrade as effects are added.
     */
    fun offsetFor(effectOrdinal: Int): Float =
        (effectOrdinal + 0.5f) / EffectId.COUNT

    /**
     * Whether candidate [index] is present at [density].
     *
     * [fallbackIndex] comes from [fallbackIndexFor], computed once per effect per frame. Passing it
     * in keeps this an O(1) test rather than making every candidate rescan the pool.
     */
    fun isPresent(index: Int, density: Float, effectOffset: Float, fallbackIndex: Int): Boolean {
        if (density <= 0f) return false
        if (of(index, effectOffset) < density) return true
        return index == fallbackIndex
    }

    /**
     * The candidate that must be kept when [density] is above zero but no candidate qualifies,
     * or `-1` when at least one qualifies on its own.
     *
     * Without this rule a small pool shows *nothing* at a low but non-zero setting -- a category
     * the user has enabled and turned down would look switched off rather than sparse. It matters
     * for the four-candidate pools (sailboats, dolphins, mountains) and the six-candidate one
     * (birds); the large pools effectively never reach it.
     *
     * Returns as soon as any candidate qualifies, so in the common case this exits after a few
     * iterations and only walks the whole pool in the rare case where the rule actually applies.
     */
    fun fallbackIndexFor(density: Float, poolSize: Int, effectOffset: Float): Int {
        if (density <= 0f) return -1
        var best = 0
        var bestThreshold = Float.MAX_VALUE
        for (index in 0 until poolSize) {
            val threshold = of(index, effectOffset)
            if (threshold < density) return -1
            if (threshold < bestThreshold) {
                bestThreshold = threshold
                best = index
            }
        }
        return best
    }
}

/**
 * Stable per-effect identifiers, mixed into candidate seeds and threshold offsets.
 *
 * Small consecutive ordinals, not arbitrary constants: [CandidateThreshold.offsetFor] spaces the
 * threshold offsets evenly across them, so every effect is guaranteed to be `1 / COUNT` away from
 * every other. Renumbering one changes that effect's arrangement in every theme, so the values are
 * part of the visual contract rather than an implementation detail. Adding an effect means adding
 * an ordinal **and** bumping [COUNT].
 */
internal object EffectId {
    const val CLOUDS = 0
    const val PRECIPITATION = 1
    const val BIRDS = 2
    const val FALLING_LEAVES = 3
    const val MOUNTAINS_BACK = 4
    const val MOUNTAINS_FRONT = 5
    const val SAILBOATS = 6
    const val DOLPHINS = 7
    const val LAKE_SPARKLES = 8

    /** Must equal the number of ordinals above; [CandidateThreshold.offsetFor] divides by it. */
    const val COUNT = 9
}
