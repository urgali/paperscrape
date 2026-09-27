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

    /**
     * **The stratified deal reaches the street** (v5.8C): after every deal, the walkers of a
     * stratum wear as many distinct tones as they can.
     *
     * `PedestrianPopulation` deals the base tones so that the m-th members of the groups carry all
     * three between them; v4.30's per-walker rotation threw that away on the street (the v5.8B
     * audit measured the four group leaders missing a tone 57 % of the time, the rate of
     * independent rolls). Simulated here the way the renderer deals: walkers are re-dealt one at a
     * time, on their own crossings, in an order the test does not control, through
     * [PeopleColours.keepingSpread] -- after a first sight in each walker's arrival tone
     * ([PeopleColours.firstSightTone]), as in the renderer. The spread must hold at first sight and
     * after every deal, at every density `PeopleOcclusionTest` steps through. The old rule is run
     * beside it on the same events, to show that this test would have caught the defect.
     */
    @Test
    fun `a stratum keeps its tones spread while each walker changes colour on its own crossing`() {
        val slots = PedestrianPopulation.GROUP_COUNT * PedestrianPopulation.MAX_GROUP_SIZE
        var oldRuleBreaks = 0
        var deals = 0
        var changed = 0
        var frozen = 0
        val tally = IntArray(PeopleColours.SKIN.size)
        for (theme in ThemeCatalog.ALL) {
            val seed = theme.id.hashCode()
            for (density in listOf(1f, 0.8f, 0.75f, 0.5f, 0.4f, 0.2f)) {
                val people = PedestrianPopulation.build(
                    seed, density, SceneSpace.PAVEMENT_NEAR_Y_FRACTION, SceneSpace.PAVEMENT_FAR_Y_FRACTION,
                )
                var present = 0
                for (p in people) present = present or (1 shl (p.groupIndex * PedestrianPopulation.MAX_GROUP_SIZE + p.memberIndex))
                val held = IntArray(slots) { PeopleColours.NO_TONE }
                val old = IntArray(slots) { PeopleColours.NO_TONE }
                val crossing = IntArray(slots)
                val order = kotlin.random.Random(seed + (density * 100).toInt())
                // First sight: every walker wears its arrival tone, as the renderer deals it -- a
                // function of nobody else on the street, so a density change recolours nobody
                // (PeopleOcclusionTest), and spread at every density.
                for (p in people) {
                    val a = p.groupIndex * PedestrianPopulation.MAX_GROUP_SIZE + p.memberIndex
                    held[a] = PeopleColours.firstSightTone(p)
                    old[a] = PeopleColours.toneIndex(p.skinIndex, seed, a, 0)
                }
                assertSpread(theme.id, people, held, "first sight at density $density")
                val changes = IntArray(slots)
                repeat(3_000) { step ->
                    val p = people[order.nextInt(people.size)]
                    val a = p.groupIndex * PedestrianPopulation.MAX_GROUP_SIZE + p.memberIndex
                    // A walker's crossing only ever moves one way: forward for a walker going right,
                    // backward for one going left (`crossingOf` multiplies by the direction).
                    crossing[a] += if (p.direction > 0f) 1 else -1
                    val before = held[a]
                    held[a] = PeopleColours.keepingSpread(
                        PeopleColours.toneIndex(p.skinIndex, seed, a, crossing[a]), a, held, present,
                    )
                    old[a] = PeopleColours.toneIndex(p.skinIndex, seed, a, crossing[a])
                    deals++
                    if (held[a] != before) { changed++; changes[a]++ }
                    tally[held[a]]++
                    assertSpread(theme.id, people, held, "deal $step")
                    if (!spreadHolds(people, old)) oldRuleBreaks++
                }
                // No walker may be frozen in one colour: each must move a fair share of its deals.
                for (p in people) {
                    if (changes[p.groupIndex * PedestrianPopulation.MAX_GROUP_SIZE + p.memberIndex] < 3_000 / people.size / 5) frozen++
                }
            }
        }
        assertTrue("the old per-walker rotation must break the spread here, or this test proves nothing", oldRuleBreaks > deals / 10)
        assertEquals("walkers that almost never change colour", 0, frozen)
        assertTrue("walkers must still change colour on a good share of crossings: $changed of $deals", changed > deals / 3)
        for ((tone, n) in tally.withIndex()) {
            assertEquals("tone $tone share of $deals deals", 1.0 / 3, n.toDouble() / deals, 0.03)
        }
    }

    /**
     * The first-sight tone is the walker's own: the same at every density it is present at, over
     * every theme. What `PeopleOcclusionTest` needs on the device -- more people must not recolour
     * the ones already there -- checked here for every density step, not only the ones it renders.
     */
    @Test
    fun `a walker is first seen in the same tone at every density`() {
        var compared = 0
        for (theme in ThemeCatalog.ALL) {
            val seed = theme.id.hashCode()
            val first = HashMap<Int, Int>()
            for (step in 1..20) {
                for (p in PedestrianPopulation.build(seed, step / 20f, SceneSpace.PAVEMENT_NEAR_Y_FRACTION, SceneSpace.PAVEMENT_FAR_Y_FRACTION)) {
                    val a = p.groupIndex * PedestrianPopulation.MAX_GROUP_SIZE + p.memberIndex
                    val tone = PeopleColours.firstSightTone(p)
                    val seen = first.getOrPut(a) { tone }
                    assertEquals("${theme.id} walker $a at density ${step / 20f}", seen, tone)
                    compared++
                }
            }
        }
        assertTrue("walkers compared: $compared", compared > 500)
    }

    private fun spreadHolds(people: List<Pedestrian>, held: IntArray): Boolean {
        for (m in 0 until PedestrianPopulation.MAX_GROUP_SIZE) {
            val stratum = people.filter { it.memberIndex == m }
            val tones = stratum.map { held[it.groupIndex * PedestrianPopulation.MAX_GROUP_SIZE + it.memberIndex] }.toSet()
            // All three among the four leaders of a full street; never one tone among two or three.
            val need = if (stratum.size >= PedestrianPopulation.GROUP_COUNT) 3 else minOf(2, stratum.size)
            if (tones.size < need) return false
        }
        return true
    }

    private fun assertSpread(theme: String, people: List<Pedestrian>, held: IntArray, what: String) {
        assertTrue("$theme, $what: a stratum lost a tone it could carry", spreadHolds(people, held))
    }

    /** And the renderer deals through it, holding the tone with the crossing. */
    @Test
    fun `the renderer passes every tone through keepingSpread`() {
        val src = rendererSource()
        assertTrue(src.contains("PeopleColours.keepingSpread("))
        // ...except the first sight, which is the walker's own arrival tone (PeopleOcclusionTest).
        val firstSight = src.substringAfter("if (personCrossing[walkStagger] == PeopleColours.UNDEALT) {").substringBefore("\n            }")
        assertTrue("first sight must not read the street: $firstSight", firstSight.contains("PeopleColours.firstSightTone(person)") && !firstSight.contains("dealTone("))
        val drawn = src.substringAfter("val colours = coloursFor(\n                walkStagger").substringBefore(")\n")
        assertTrue("the walker must be drawn in its held tone: $drawn", drawn.contains("personTone[walkStagger]"))
    }

    private fun rendererSource(): String {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/engine/SceneObjectRenderer.kt"
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = java.io.File(dir, "$prefix$suffix")
                if (candidate.isFile) return candidate.readText()
            }
            dir = dir.parentFile
        }
        error("could not locate $suffix")
    }
}
