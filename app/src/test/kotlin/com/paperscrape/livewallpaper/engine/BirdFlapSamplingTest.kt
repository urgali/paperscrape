package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wing-flap is nowhere near a zero crossing at any clock a golden is taken at.
 *
 * ### Why this exists
 *
 * `BACKLOG_v4_30.md` item 98 recorded six Canvas goldens carrying device drift, `lake-dolphin-leap`
 * at 67 % of its budget, and attributed it to this:
 *
 * > A bird's wing-flap is a vertical mirror switched by `sin(seconds * 9 + phase * 6.28)`, and at
 * > that scene's `sceneSeconds = 200` the argument is about 1800 radians. The frame the golden was
 * > authored from caught the sine on one side of a zero crossing and this device catches it on the
 * > other.
 *
 * **Measured in v4.31: that cannot happen, and did not.** The cause was a redrawn `bird_body.png`
 * that six goldens were never re-authored against -- proved by mutation, `BACKLOG_v4_31.md` item
 * 104. This test is the arithmetic half of that refutation, kept as a command rather than as a
 * sentence in a report, because `AI_PROJECT_RULES.md` 14.11 is about exactly that.
 *
 * ### What it measures
 *
 * Everything the flap depends on is deterministic: `SceneTime.seconds` is a `Double` a golden sets
 * exactly, `phase` is `CandidateNoise.value`, which is integer arithmetic, and the seed is
 * `String.hashCode`, which the Java language specifies. So the only way two runs can disagree about
 * the sign is if `sin` lands close enough to zero for the last bits of a ~1800-radian argument to
 * decide it. A `Double` holds 1800 radians to about `4e-13`.
 *
 * The sweep below is every built-in theme x every scene clock a committed golden uses x every bird
 * in the pool. The closest approach it finds is the number in [NEAREST_APPROACH_TO_A_CROSSING].
 */
class BirdFlapSamplingTest {

    /**
     * The smallest `|sin(flap)|` over the whole sweep, measured 2026-09-12.
     *
     * `new_year` at `sceneSeconds = 60`, bird 1. The gate below is an order of magnitude under it
     * so that a new theme or a new golden clock does not fail this by coming a little closer; what
     * must fail is the sweep approaching the scale where the sign is actually in doubt.
     */
    private val nearestApproachMeasured = 0.0038

    /** The absolute precision of a ~1800-radian argument in a `Double`: `1800 * 2^-52`. */
    private val doublePrecisionAt1800Radians = 4.0e-13

    /** Every `sceneSeconds` a committed golden is taken at. */
    private val goldenClocks = listOf(40.0, 60.0, 90.0, 120.0, 150.0, 200.0, 240.0, 300.0)

    private fun seedFor(themeId: String, effectOrdinal: Int): Int =
        themeId.hashCode() xor (effectOrdinal * -0x61c88647)

    @Test
    fun `no bird's flap is close enough to a zero crossing for a sign to be in doubt`() {
        var worstValue = Double.MAX_VALUE
        var worstWhere = ""
        for (theme in ThemeCatalog.ALL) {
            val seed = seedFor(theme.id, EffectId.BIRDS)
            for (seconds in goldenClocks) {
                for (i in 0 until PaperRenderer.BIRD_POOL_SIZE) {
                    // The renderer's own expression: `elapsedSeconds.sinAt(9f, phase * 6.28f)`,
                    // with `phase * 6.28f` narrowed to Float exactly as the call site narrows it.
                    val phase = CandidateNoise.value(seed, i, CandidateNoise.CH_PHASE)
                    val value = kotlin.math.abs(kotlin.math.sin(seconds * 9f + phase * 6.28f))
                    if (value < worstValue) {
                        worstValue = value
                        worstWhere = "${theme.id} at ${seconds}s, bird $i"
                    }
                }
            }
        }
        assertTrue(
            "the closest any flap comes to a zero crossing is |sin| = $worstValue ($worstWhere), " +
                "against a recorded $nearestApproachMeasured -- if this has fallen by an order of " +
                "magnitude, re-measure before believing any story about a knife edge",
            worstValue > nearestApproachMeasured / 10.0,
        )
        assertTrue(
            "|sin| = $worstValue at $worstWhere is $worstValue / $doublePrecisionAt1800Radians = " +
                "${worstValue / doublePrecisionAt1800Radians} times the precision of the argument " +
                "itself, so no run can disagree with another about which side of zero it is on",
            worstValue > doublePrecisionAt1800Radians * 1.0e6,
        )
    }
}
