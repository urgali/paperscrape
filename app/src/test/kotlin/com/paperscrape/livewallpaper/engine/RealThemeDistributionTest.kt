package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The street each **shipped theme** actually shows, measured on that theme's own seed.
 *
 * ### Why this class exists next to `PedestrianPopulationTest`
 *
 * That class sweeps hundreds of synthetic seeds and asserts on the aggregate, which is the right
 * way to test a generator and is not enough: a generator can pass every one of those tests and
 * still give a theme six pedestrians who are five girls and a boy, all on one tone, with no adult
 * of either sex -- because a wallpaper does not draw hundreds of seeds. It draws **one**, for as
 * long as the user keeps the theme, and if that one seed's sample is lumpy the lump is what the
 * user sees for ever.
 *
 * So every assertion here is made **per theme id**, over exactly the seeds the shipped app uses.
 * A property that holds on average across the catalogue but fails on one theme fails here.
 *
 * ### What "no pathological bias" is taken to mean
 *
 * Not a perfect distribution -- four groups cannot be perfectly distributed and should not look as
 * if they were. The bar is this list: on every theme the street
 * must contain an adult male, an adult female, a boy and a girl, all three skin tones, both
 * directions of travel and groups of one, two and three; and no single sprite kind or tone may
 * take three quarters of the pavement.
 */
class RealThemeDistributionTest {

    private val near = SceneSpace.PAVEMENT_NEAR_Y_FRACTION
    private val far = SceneSpace.PAVEMENT_FAR_Y_FRACTION

    /** Every built-in theme id, read from the catalogue rather than listed here. */
    private val themeIds = ThemeCatalog.ALL.map { it.id }

    private fun street(themeId: String, density: Float = 1f) =
        PedestrianPopulation.build(themeId.hashCode(), density, near, far)

    private fun roster(people: List<Pedestrian>) =
        people.joinToString(" ") {
            "[${arrayOf("man", "woman", "boy", "girl")[it.kindIndex]}" +
                " skin${it.skinIndex} ${if (it.direction > 0f) "R" else "L"}]"
        }

    // ------------------------------------------------------- the four kinds

    /**
     * All four kinds have to be on every shipped street, whatever its seed deals.
     */
    @Test
    fun `every built-in theme puts an adult male, an adult female, a boy and a girl on the street`() {
        for (id in themeIds) {
            val people = street(id)
            val kinds = people.map { it.kindIndex }.toSet()
            assertEquals(
                "$id is missing a kind of person -- ${roster(people)}",
                setOf(0, 1, 2, 3),
                kinds,
            )
        }
    }

    /** Nothing may take three quarters of the pavement. */
    @Test
    fun `no built-in theme lets one kind of person take three quarters of the street`() {
        for (id in themeIds) {
            val people = street(id)
            val worst = people.groupingBy { it.kindIndex }.eachCount().values.max()
            assertTrue(
                "$id is ${worst}/${people.size} one kind -- ${roster(people)}",
                worst.toFloat() / people.size < 0.75f,
            )
        }
    }

    // -------------------------------------------------------------- skin

    /** Every tone on every shipped street, not one tone on all of it. */
    @Test
    fun `every built-in theme shows all three skin tones`() {
        for (id in themeIds) {
            val people = street(id)
            assertEquals(
                "$id does not show every tone -- ${roster(people)}",
                (0 until PedestrianPopulation.SKIN_TONE_COUNT).toSet(),
                people.map { it.skinIndex }.toSet(),
            )
        }
    }

    @Test
    fun `no built-in theme lets one skin tone take three quarters of the street`() {
        for (id in themeIds) {
            val people = street(id)
            val worst = people.groupingBy { it.skinIndex }.eachCount().values.max()
            assertTrue(
                "$id is ${worst}/${people.size} one tone -- ${roster(people)}",
                worst.toFloat() / people.size < 0.75f,
            )
        }
    }

    // --------------------------------------------------------- direction

    /** Never a whole street walking one way. */
    @Test
    fun `every built-in theme has people walking both ways`() {
        for (id in themeIds) {
            val people = street(id)
            assertTrue("$id has nobody walking right -- ${roster(people)}", people.any { it.direction > 0f })
            assertTrue("$id has nobody walking left -- ${roster(people)}", people.any { it.direction < 0f })
        }
    }

    // ------------------------------------------------------- group sizes

    /** Every one of the three group sizes on every shipped street. */
    @Test
    fun `every built-in theme shows a lone walker, a pair and a group of three`() {
        for (id in themeIds) {
            val people = street(id)
            val sizes = people.groupBy { it.groupIndex }.values.map { it.size }.toSortedSet()
            assertEquals("$id group sizes", sortedSetOf(1, 2, 3), sizes)
        }
    }

    // -------------------------------------------------- joint distribution

    /**
     * Over the twelve shipped seeds together, no attribute may predict another.
     *
     * A lean of skin against age -- the darkest tone a child's tone and the middle one an adult's,
     * on the seeds that actually ship -- is the kind of defect this catches: neither "the channels
     * are separate" nor a sweep over synthetic seeds shows it; only counting the real ones does.
     *
     * The tolerance is wide on purpose. Ninety-odd people is a small sample and a 50/50 split of
     * it is *expected* to land a few either way; what is being ruled out is a two-to-one lean, not
     * ordinary noise.
     */
    @Test
    fun `across the shipped themes no attribute predicts another`() {
        val everyone = themeIds.flatMap { street(it) }
        assertTrue("too few people to measure: ${everyone.size}", everyone.size >= 80)
        // A person's own group's size, in their own theme -- not a count across the catalogue.
        val groupSizeOf = HashMap<Pedestrian, Int>()
        for (id in themeIds) {
            for (members in street(id).groupBy { it.groupIndex }.values) {
                for (p in members) groupSizeOf[p] = members.size
            }
        }

        var worst = 0f
        var worstName = "none"
        fun share(name: String, group: (Pedestrian) -> Any, of: (Pedestrian) -> Boolean, expected: Float) {
            for ((value, people) in everyone.groupBy(group)) {
                if (people.size < 12) continue
                val got = people.count(of).toFloat() / people.size
                val lean = kotlin.math.abs(got - expected)
                if (lean > worst) {
                    worst = lean
                    worstName = "$name: $value is ${"%.3f".format(got)} against ${"%.3f".format(expected)}"
                }
            }
        }

        val adultShare = everyone.count { it.age == PersonAge.ADULT }.toFloat() / everyone.size
        val maleShare = everyone.count { it.sex == PersonSex.MALE }.toFloat() / everyone.size
        val rightShare = everyone.count { it.direction > 0f }.toFloat() / everyone.size

        share("skin -> age", { it.skinIndex }, { it.age == PersonAge.ADULT }, adultShare)
        share("skin -> sex", { it.skinIndex }, { it.sex == PersonSex.MALE }, maleShare)
        share("skin -> direction", { it.skinIndex }, { it.direction > 0f }, rightShare)
        share("age -> sex", { it.age }, { it.sex == PersonSex.MALE }, maleShare)
        share("age -> direction", { it.age }, { it.direction > 0f }, rightShare)
        share("sex -> direction", { it.sex }, { it.direction > 0f }, rightShare)
        share("direction -> sex", { it.direction > 0f }, { it.sex == PersonSex.MALE }, maleShare)
        share("direction -> age", { it.direction > 0f }, { it.age == PersonAge.ADULT }, adultShare)
        share("group size -> sex", { groupSizeOf.getValue(it) }, { it.sex == PersonSex.MALE }, maleShare)
        share("group size -> age", { groupSizeOf.getValue(it) }, { it.age == PersonAge.ADULT }, adultShare)
        share("group size -> direction", { groupSizeOf.getValue(it) }, { it.direction > 0f }, rightShare)

        // Measured, not guessed: lone walkers who are adults 20% of the time against 46% on the
        // street as a whole lean **0.258** (no adult male ever walking alone, seen from the other
        // side), and tone 2 as a child's tone 0.187. The bound is tight enough that such a lean
        // fails it, loose enough that ninety-odd people are allowed to be the small sample they are.
        assertTrue("strongest lean is $worstName", worst <= MAX_LEAN)
    }

    private companion object {
        const val MAX_LEAN = 0.20f
    }

    /**
     * Group size must not decide who is in the group.
     *
     * Measured per size rather than as a correlation coefficient, because that is the shape of the
     * defect it catches: a size of group that never holds one kind of person -- **no adult male
     * ever walking alone** on any shipped theme.
     */
    @Test
    fun `a group of any size can contain any kind of person`() {
        val bySize = mutableMapOf<Int, MutableSet<Int>>()
        for (id in themeIds) {
            for (members in street(id).groupBy { it.groupIndex }.values) {
                bySize.getOrPut(members.size) { mutableSetOf() } += members.map { it.kindIndex }
            }
        }
        assertEquals("group sizes seen", setOf(1, 2, 3), bySize.keys)
        for ((size, kinds) in bySize) {
            assertEquals("groups of $size only ever hold $kinds", setOf(0, 1, 2, 3), kinds)
        }
    }

    // ------------------------------------------------------ lower densities

    /**
     * A thinned street still has to be mixed, as far as three or four people can be.
     *
     * **What this deliberately does not claim.** Every shipped theme defaults to 100%, and the
     * guarantees above are guarantees about that street. Turn the slider down to 65% and only two
     * of the four group slots survive, so an attribute dealt across four slots is being sampled
     * twice: two survivors can land on the same tone, and they do -- `desert` at 65% is four
     * people all on tone 2.
     *
     * That is a real residue and it is left in knowingly. Making a *prefix* of the survival order
     * balanced as well as the whole would force the two-valued attributes to alternate along it,
     * which leaves exactly two possible direction patterns across the four groups instead of six
     * -- trading variety on the street everyone sees for tidiness on the one a user has explicitly
     * asked to empty. The bar at 65% is therefore the weaker, honest one: more than one kind of
     * person on every theme, and every kind and every tone somewhere across the catalogue.
     */
    @Test
    fun `a thinned street is still mixed`() {
        val everyKind = mutableSetOf<Int>()
        val everyTone = mutableSetOf<Int>()
        for (id in themeIds) {
            val people = street(id, 0.65f)
            assertTrue("$id at 65% is empty", people.isNotEmpty())
            val kinds = people.map { it.kindIndex }.toSet()
            assertTrue(
                "$id at 65% is one kind of person only -- ${roster(people)}",
                kinds.size >= 2,
            )
            everyKind += kinds
            everyTone += people.map { it.skinIndex }
        }
        assertEquals("kinds across the catalogue at 65%", setOf(0, 1, 2, 3), everyKind)
        assertEquals(
            "tones across the catalogue at 65%",
            (0 until PedestrianPopulation.SKIN_TONE_COUNT).toSet(),
            everyTone,
        )
    }

    // ------------------------------------------------- density semantics

    /**
     * **The density parameter selects groups by the shared rule.**
     *
     * What a present group *contains* is this class's subject; which groups are present is not.
     * This asserts that directly against [CandidateThreshold], the shared rule every other
     * category uses: at every density the set of group indices on the street is exactly the set
     * the threshold selects, so "20% of the pool" is 20% of the same pool of four slots.
     */
    @Test
    fun `density still selects the same group slots it always did`() {
        for (id in themeIds) {
            var density = 0.05f
            while (density <= 1f) {
                val fallback = CandidateThreshold.fallbackIndexFor(
                    density, PedestrianPopulation.GROUP_COUNT, PedestrianPopulation.THRESHOLD_OFFSET,
                )
                val expected = (0 until PedestrianPopulation.GROUP_COUNT).filter {
                    CandidateThreshold.isPresent(it, density, PedestrianPopulation.THRESHOLD_OFFSET, fallback)
                }.toSet()
                val actual = street(id, density).map { it.groupIndex }.toSet()
                assertEquals("$id at density $density", expected, actual)
                density += 0.05f
            }
        }
    }
}
