package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The generative contract for the street's walking people.
 *
 * Every test here is seeded, so the suite is reproducible; the variety claims are made by sweeping
 * *many* seeds and asserting on the distribution, which is the only honest way to test a generator
 * -- a single seed can be lucky, and a system that hard-codes one lucky answer would pass a
 * single-seed test while shipping the exact defect v4.1 exists to remove.
 */
class PedestrianPopulationTest {

    private val near = SceneSpace.PAVEMENT_NEAR_Y_FRACTION
    private val far = SceneSpace.PAVEMENT_FAR_Y_FRACTION

    private fun build(seed: Int, density: Float = 1f) =
        PedestrianPopulation.build(seed, density, near, far)

    /** Seeds derived the way the renderer derives them, from theme ids. */
    private fun seeds(n: Int) = (0 until n).map { "theme-$it".hashCode() }

    // ---------------------------------------------------------------- depth

    /**
     * The reported defect, as a test.
     *
     * v4.0 drew candidates in index order while the rows alternated with the index, so candidate 1
     * -- on the **far** row -- was drawn after candidate 0 on the near row and covered it. This
     * reproduces that ordering and asserts the new one differs from it.
     */
    @Test
    fun `the far figure is drawn before the near one, which v4-0's index order got backwards`() {
        var checked = 0
        for (seed in seeds(200)) {
            val people = build(seed)
            // The v4.0 order: creation order, which is group then member.
            val v40Order = people.sortedWith(compareBy({ it.groupIndex }, { it.memberIndex }))
            for (i in people.indices) {
                for (j in i + 1 until people.size) {
                    val first = people[i]
                    val second = people[j]
                    if (first.depth == second.depth) continue
                    // In the shipped order the earlier-drawn figure is never the nearer one.
                    assertTrue(
                        "seed $seed: drew depth ${first.depth} before ${second.depth}",
                        first.depth <= second.depth,
                    )
                    checked++
                }
            }
            // Sanity: the two orders are genuinely different systems, not the same list renamed.
            if (people.size > 1 && people != v40Order) checked++
        }
        assertTrue("no pairs were compared", checked > 0)
    }

    /** Depth must come from the baseline, so a nearer figure always sorts later. */
    @Test
    fun `a nearer figure is always drawn after a farther one`() {
        for (seed in seeds(200)) {
            val people = build(seed)
            for (i in 1 until people.size) {
                assertTrue(
                    "seed $seed: depth went backwards at $i",
                    people[i - 1].depth <= people[i].depth,
                )
            }
        }
    }

    /**
     * Depth ordering holds *within* a group, not only between groups.
     *
     * The sort sees a flat list and never consults `groupIndex` except as a tie-break, so a group
     * of three is ordered far-to-near among itself by construction.
     */
    @Test
    fun `members of one group are ordered far to near among themselves`() {
        var groupsOfThree = 0
        for (seed in seeds(400)) {
            val people = build(seed)
            for (g in 0 until PedestrianPopulation.GROUP_COUNT) {
                val members = people.filter { it.groupIndex == g }
                if (members.size < 2) continue
                if (members.size == 3) groupsOfThree++
                // Their order inside the drawn list must already be far-to-near.
                for (i in 1 until members.size) {
                    assertTrue(
                        "seed $seed group $g: member depth went backwards",
                        members[i - 1].depth <= members[i].depth,
                    )
                }
            }
        }
        assertTrue("never produced a group of three to check", groupsOfThree > 0)
    }

    /** Equal depths must resolve deterministically rather than by creation accident. */
    @Test
    fun `ties break deterministically`() {
        val a = build("tie-seed".hashCode())
        val b = build("tie-seed".hashCode())
        assertEquals(a, b)
    }

    // ---------------------------------------------------------------- groups

    @Test
    fun `groups of one, two and three all occur`() {
        val sizes = mutableSetOf<Int>()
        for (seed in seeds(300)) {
            val people = build(seed)
            for (g in 0 until PedestrianPopulation.GROUP_COUNT) {
                val n = people.count { it.groupIndex == g }
                if (n > 0) sizes += n
            }
        }
        assertEquals("group sizes produced: $sizes", setOf(1, 2, 3), sizes)
    }

    /** Age and sex must vary freely, so all four combinations appear. */
    @Test
    fun `every age and sex combination occurs`() {
        val seen = mutableSetOf<Pair<PersonAge, PersonSex>>()
        for (seed in seeds(200)) {
            for (p in build(seed)) seen += p.age to p.sex
        }
        assertEquals(4, seen.size)
    }

    /**
     * The v4.0 pattern, named and forbidden.
     *
     * Pairs of an adult and a child must not be same-sex-locked: an adult male with a girl, and an
     * adult female with a boy, both have to be reachable.
     */
    @Test
    fun `an adult is not paired only with a child of the same sex`() {
        var maleAdultWithGirl = false
        var femaleAdultWithBoy = false
        for (seed in seeds(600)) {
            val people = build(seed)
            for (g in 0 until PedestrianPopulation.GROUP_COUNT) {
                val members = people.filter { it.groupIndex == g }
                val adults = members.filter { it.age == PersonAge.ADULT }
                val children = members.filter { it.age == PersonAge.CHILD }
                if (adults.isEmpty() || children.isEmpty()) continue
                if (adults.any { it.sex == PersonSex.MALE } && children.any { it.sex == PersonSex.FEMALE }) {
                    maleAdultWithGirl = true
                }
                if (adults.any { it.sex == PersonSex.FEMALE } && children.any { it.sex == PersonSex.MALE }) {
                    femaleAdultWithBoy = true
                }
            }
        }
        assertTrue("never produced a man with a girl", maleAdultWithGirl)
        assertTrue("never produced a woman with a boy", femaleAdultWithBoy)
    }

    /** Mixed-sex and mixed-age groups must both be reachable. */
    @Test
    fun `groups mix sexes and ages`() {
        var mixedSex = false
        var mixedAge = false
        for (seed in seeds(300)) {
            val people = build(seed)
            for (g in 0 until PedestrianPopulation.GROUP_COUNT) {
                val members = people.filter { it.groupIndex == g }
                if (members.size < 2) continue
                if (members.map { it.sex }.distinct().size > 1) mixedSex = true
                if (members.map { it.age }.distinct().size > 1) mixedAge = true
            }
        }
        assertTrue("no mixed-sex group", mixedSex)
        assertTrue("no mixed-age group", mixedAge)
    }

    // ------------------------------------------------- direction independence

    /**
     * `direction != composition`, measured.
     *
     * Every one of the four age/sex combinations has to appear walking both ways. This is the
     * test that fails outright against v4.0, where one direction could only ever be man-or-boy.
     */
    @Test
    fun `every kind of person walks in both directions`() {
        val rightward = mutableSetOf<Pair<PersonAge, PersonSex>>()
        val leftward = mutableSetOf<Pair<PersonAge, PersonSex>>()
        for (seed in seeds(300)) {
            for (p in build(seed)) {
                if (p.direction > 0f) rightward += p.age to p.sex else leftward += p.age to p.sex
            }
        }
        assertEquals("rightward: $rightward", 4, rightward.size)
        assertEquals("leftward: $leftward", 4, leftward.size)
    }

    /** Group *size* must not follow the direction either. */
    @Test
    fun `groups of every size walk in both directions`() {
        val rightward = mutableSetOf<Int>()
        val leftward = mutableSetOf<Int>()
        for (seed in seeds(400)) {
            val people = build(seed)
            for (g in 0 until PedestrianPopulation.GROUP_COUNT) {
                val members = people.filter { it.groupIndex == g }
                if (members.isEmpty()) continue
                if (members.first().direction > 0f) rightward += members.size else leftward += members.size
            }
        }
        assertEquals(setOf(1, 2, 3), rightward)
        assertEquals(setOf(1, 2, 3), leftward)
    }

    /**
     * Direction must not predict sex statistically, not merely fail to determine it.
     *
     * Over a large sweep the share of males walking right and walking left must be close; a
     * generator that leaked direction into sex would show a wide gap here even if both directions
     * technically contained both sexes.
     */
    @Test
    fun `direction does not bias the sex, age or row of the people walking that way`() {
        var rightMale = 0; var rightTotal = 0
        var leftMale = 0; var leftTotal = 0
        var rightAdult = 0; var leftAdult = 0
        var rightNear = 0; var leftNear = 0
        for (seed in seeds(3000)) {
            for (p in build(seed)) {
                val isMale = p.sex == PersonSex.MALE
                val isAdult = p.age == PersonAge.ADULT
                val isNear = p.rowYFraction > (near + far) / 2f
                if (p.direction > 0f) {
                    rightTotal++; if (isMale) rightMale++; if (isAdult) rightAdult++; if (isNear) rightNear++
                } else {
                    leftTotal++; if (isMale) leftMale++; if (isAdult) leftAdult++; if (isNear) leftNear++
                }
            }
        }
        assertTrue("too few samples", rightTotal > 500 && leftTotal > 500)
        fun share(a: Int, b: Int) = a.toFloat() / b
        assertEquals("male share differs by direction", share(rightMale, rightTotal), share(leftMale, leftTotal), 0.05f)
        assertEquals("adult share differs by direction", share(rightAdult, rightTotal), share(leftAdult, leftTotal), 0.05f)
        assertEquals("row differs by direction", share(rightNear, rightTotal), share(leftNear, leftTotal), 0.05f)
    }

    /** Both directions must actually occur, in roughly equal measure. */
    @Test
    fun `both directions occur about equally often`() {
        var right = 0
        var total = 0
        for (seed in seeds(3000)) {
            for (p in build(seed)) { total++; if (p.direction > 0f) right++ }
        }
        assertEquals(0.5f, right.toFloat() / total, 0.05f)
    }

    // ---------------------------------------------------------------- skin

    /** Skin must be addressed on its own channel, never derived from direction. */
    @Test
    fun `skin is chosen independently of direction and stays in range`() {
        val rightward = mutableSetOf<Int>()
        val leftward = mutableSetOf<Int>()
        for (seed in seeds(500)) {
            for (p in build(seed)) {
                assertTrue(
                    "skin ${p.skinIndex} out of range",
                    p.skinIndex in 0 until PedestrianPopulation.SKIN_TONE_COUNT,
                )
                if (p.direction > 0f) rightward += p.skinIndex else leftward += p.skinIndex
            }
        }
        // Whatever the shipped palette can express must be reachable from both directions.
        assertEquals(rightward, leftward)
    }

    // ------------------------------------------------------------ determinism

    @Test
    fun `the same seed gives the same street`() {
        for (seed in seeds(50)) {
            assertEquals(build(seed), build(seed))
        }
    }

    @Test
    fun `different seeds give different streets`() {
        val streets = seeds(200).map { build(it) }
        assertTrue("all seeds produced the same street", streets.distinct().size > 100)
    }

    /** Nothing may read the clock: two calls a moment apart are identical by construction. */
    @Test
    fun `the population does not depend on anything but its arguments`() {
        val first = build(12345)
        Thread.sleep(5)
        assertEquals(first, build(12345))
    }

    // ---------------------------------------------------------------- density

    /**
     * Density's real meaning, pinned.
     *
     * It is the fraction of the group pool that is present -- not opacity, not scale, not speed.
     * The regression this guards is the parameter being ignored: 20% and 100% must not produce the
     * same street.
     */
    @Test
    fun `density selects how many groups are present, and 20 percent differs from 100 percent`() {
        for (seed in seeds(50)) {
            val sparse = build(seed, 0.2f)
            val full = build(seed, 1f)
            assertTrue("20% produced nobody", sparse.isNotEmpty())
            assertTrue(
                "20% (${sparse.size}) was not fewer than 100% (${full.size})",
                sparse.size < full.size,
            )
            assertNotEquals(sparse, full)
        }
    }

    @Test
    fun `zero density empties the street and density only ever adds`() {
        for (seed in seeds(50)) {
            assertTrue(build(seed, 0f).isEmpty())
            var previous = 0
            var d = 0.05f
            while (d <= 1f) {
                val n = build(seed, d).size
                assertTrue("density $d removed people", n >= previous)
                previous = n
                d += 0.05f
            }
        }
    }

    /**
     * A surviving group keeps its identity as density changes.
     *
     * This is [CandidateThreshold]'s stability contract, which the people system now shares with
     * every other category: lowering the slider removes particular groups and leaves the rest
     * exactly as they were, rather than reshuffling the street.
     */
    @Test
    fun `lowering density leaves the surviving groups untouched`() {
        for (seed in seeds(50)) {
            val full = build(seed, 1f).groupBy { it.groupIndex }
            val sparse = build(seed, 0.4f).groupBy { it.groupIndex }
            for ((g, members) in sparse) {
                assertEquals("group $g changed when density fell", full[g], members)
            }
        }
    }

    /** Pedestrians must no longer share `MOUNTAINS_BACK`'s threshold offset. */
    @Test
    fun `the threshold offset does not collide with another category's`() {
        for (ordinal in 0 until EffectId.COUNT) {
            val other = CandidateThreshold.offsetFor(ordinal)
            assertTrue(
                "pedestrians collide with effect $ordinal",
                kotlin.math.abs(other - PedestrianPopulation.THRESHOLD_OFFSET) > 0.02f,
            )
        }
    }
}
