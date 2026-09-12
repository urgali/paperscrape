package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The deal the street's colours come from, and the defect it was built to cure.
 *
 * ### The defect, in one line
 *
 * The colour was `CandidateNoise.value(themeId.hashCode(), address, channel)` — a **constant per
 * theme**. Nothing about the deal was unfair; what was wrong is that it was dealt once and never
 * re-dealt, so on `city` the three men were all fair-skinned and the three children all dark for as
 * long as the theme was on. Three figures over three tones agree by chance one time in nine, and
 * one time in nine *forever* is not the same thing as one time in nine.
 *
 * So what these assertions are about is not "is the deal uniform" — it was — but **does it move,
 * and does it move to the right distribution**.
 */
class PeopleColoursTest {

    /** Enough crossings that a share is a share rather than a run of luck. */
    private val crossings = 4_000

    /** A few real theme seeds, since the seed is where the crossing is folded in. */
    private val seeds = listOf("sunset", "city", "winter", "spring", "halloween").map { it.hashCode() }

    @Test
    fun `a tone is dealt to each figure about a third of the time`() {
        for (seed in seeds) {
            for (address in listOf(0, 1, 5, 7, 11)) {
                val counts = IntArray(PeopleColours.SKIN.size)
                for (crossing in 0 until crossings) {
                    counts[PeopleColours.toneIndex(base = 0, seed, address, crossing)]++
                }
                for (tone in counts.indices) {
                    val share = counts[tone].toDouble() / crossings
                    assertTrue(
                        "seed $seed address $address deals tone $tone on ${"%.3f".format(share)} " +
                            "of its crossings",
                        share in 0.30..0.37,
                    )
                }
            }
        }
    }

    /**
     * **Three figures of one family agree about one crossing in nine, and then stop agreeing.**
     *
     * This is the whole cure, stated as the thing the maintainer actually reported. Before, a
     * family that happened to come out all one tone stayed that way; now the same event has the
     * frequency chance gives it, which means the street un-collapses by itself.
     */
    @Test
    fun `a family of three stops being one colour after a crossing or so`() {
        for (seed in seeds) {
            // Three addresses of one family, as `walkStagger` would hand them out.
            val trio = listOf(0, 1, 2)
            var allSame = 0
            var longestRun = 0
            var run = 0
            for (crossing in 0 until crossings) {
                // Base 0 for all three: the harshest start there is, a family the population
                // dealt one tone. If the rotation moves them apart, it moves anything apart.
                val tones = trio.map { PeopleColours.toneIndex(base = 0, seed, it, crossing) }
                if (tones.distinct().size == 1) {
                    allSame++
                    run++
                    if (run > longestRun) longestRun = run
                } else {
                    run = 0
                }
            }
            val share = allSame.toDouble() / crossings
            assertTrue(
                "seed $seed: the three agree on ${"%.3f".format(share)} of crossings, where chance " +
                    "over three tones gives 1/9 = 0.111",
                share in 0.08..0.14,
            )
            assertTrue(
                "seed $seed: they stayed one colour for $longestRun crossings in a row. The defect " +
                    "this file exists for is a family that is one colour and stays that way",
                longestRun <= 4,
            )
        }
    }

    /**
     * **A rotation keeps the deal underneath it.** This is the half of the design that is not about
     * movement: `PedestrianPopulation` deals tones as a stratified rank so that the members of one
     * stratum carry all three between them, and a re-roll would throw that away for nothing.
     * Checked as the property that makes it true -- on one crossing the map from dealt tone to worn
     * tone is a bijection -- rather than by re-measuring the population.
     */
    @Test
    fun `on one crossing the rotation is a bijection over the tones`() {
        for (seed in seeds) {
            for (address in 0 until 12) {
                for (crossing in listOf(0, 3, 97, 5_000)) {
                    val worn = PeopleColours.SKIN.indices.map {
                        PeopleColours.toneIndex(it, seed, address, crossing)
                    }
                    assertEquals(
                        "three dealt tones must come out as three worn tones, not two",
                        PeopleColours.SKIN.indices.toSet(),
                        worn.toSet(),
                    )
                }
            }
        }
    }

    @Test
    fun `the head follows the drawing rather than the palette`() {
        // A bare head is dealt hair; a covered one is dealt a cap. Nobody's headwear comes off.
        for (seed in seeds) {
            for (crossing in 0 until 200) {
                assertTrue(
                    "a bare head must be dealt a hair colour",
                    PeopleColours.head(seed, 3, crossing, worn = false) in PeopleColours.HAIR.toList(),
                )
                assertTrue(
                    "a covered head must be dealt a cap colour",
                    PeopleColours.head(seed, 3, crossing, worn = true) in PeopleColours.CAP.toList(),
                )
            }
        }
    }

    @Test
    fun `an outfit is a pair and every pair is reachable`() {
        assertEquals(
            "OUTFITS is flattened as top, bottom, top, bottom",
            0,
            PeopleColours.OUTFITS.size % 2,
        )
        val seen = mutableSetOf<Int>()
        for (seed in seeds) {
            for (crossing in 0 until 500) {
                seen += PeopleColours.outfit(seed, 4, crossing)
            }
        }
        assertEquals(
            "every outfit must be reachable, or the palette is smaller than it says",
            (0 until PeopleColours.OUTFIT_COUNT).toSet(),
            seen,
        )
        // A shirt and its trousers travel together: the pair is the unit dealt, so reading one
        // without the other cannot happen by construction. Checked as the arithmetic that makes it
        // so, because that arithmetic is the only thing holding it.
        for (index in 0 until PeopleColours.OUTFIT_COUNT) {
            assertEquals(PeopleColours.OUTFITS[index * 2], PeopleColours.top(index))
            assertEquals(PeopleColours.OUTFITS[index * 2 + 1], PeopleColours.bottom(index))
        }
    }

    /**
     * Every dealt colour is opaque.
     *
     * `TintOpacityTest` asserts that no tint in this app carries a non-opaque alpha, and
     * `GlSceneTarget` ignores a tint's alpha byte on that basis. A palette entry written without
     * `0xFF` would be a silently black region on one backend and not the other.
     */
    @Test
    fun `every colour a person can be dealt is opaque`() {
        val all = PeopleColours.SKIN + PeopleColours.HAIR + PeopleColours.CAP + PeopleColours.OUTFITS
        for (colour in all) {
            assertEquals(
                "#%08X is not opaque".format(colour),
                0xFF,
                colour ushr 24,
            )
        }
    }

    /** At a given instant a given figure is a given colour -- which is what the goldens rest on. */
    @Test
    fun `the deal is a pure function of theme, person and crossing`() {
        for (seed in seeds) {
            for (address in 0 until 12) {
                for (crossing in listOf(0, 1, 17, -3, 5_000)) {
                    val once = PeopleColours.toneIndex(address % 3, seed, address, crossing)
                    val twice = PeopleColours.toneIndex(address % 3, seed, address, crossing)
                    assertEquals(once, twice)
                    assertTrue("a tone index must index the palette", once in PeopleColours.SKIN.indices)
                }
            }
        }
    }

    /**
     * The crossing counter steps once per crossing, and it comes off the clock.
     *
     * `PedestrianTileWrapTest` measures the other half of this -- that the moment it steps is *not*
     * out of sight by itself, which is why the renderer holds it until its own cull says so.
     */
    @Test
    fun `the clock drives the crossing and an uptime of zero is not crossing zero`() {
        assertEquals("13:30 is 48 600 s into the day", 48_600.0, PeopleColours.clockSeconds(13.5f), 0.001)
        val atDawn = PeopleColours.crossingOf(PeopleColours.clockSeconds(6f), 0.02f, 0.3f, 0.4f, 1f)
        val atDusk = PeopleColours.crossingOf(PeopleColours.clockSeconds(18f), 0.02f, 0.3f, 0.4f, 1f)
        assertTrue(
            "a walker must have crossed many times between dawn and dusk, not restarted at zero",
            atDusk - atDawn > 500,
        )
    }
}
