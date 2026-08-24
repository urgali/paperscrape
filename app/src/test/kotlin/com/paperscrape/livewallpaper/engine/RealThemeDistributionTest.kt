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
 * way to test a generator and is not enough. v4.1 passed every one of those tests and still shipped
 * a `beach` theme whose six pedestrians were five girls and a boy, every one of them on the darkest
 * of the three tones, with no adult of either sex -- because a wallpaper does not draw hundreds of
 * seeds. It draws **one**, for as long as the user keeps the theme, and if that one seed's sample
 * is lumpy the lump is what the user sees for ever.
 *
 * So every assertion here is made **per theme id**, over exactly the seeds the shipped app uses.
 * A property that holds on average across the catalogue but fails on `beach` fails here, which is
 * the whole point: `beach` is the theme the defect was reported from.
 *
 * ### What "no pathological bias" is taken to mean
 *
 * Not a perfect distribution -- four groups cannot be perfectly distributed and should not look as
 * if they were. The bar is the list the brief for this release set out: on every theme the street
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
     * The headline defect: `beach` had no adult of either sex, and `sunset` and `new_year` had no
     * girl. All four have to be on every shipped street.
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

    /** `beach` was five girls in six. Nothing may take three quarters of the pavement. */
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

    /** `beach` was six people on tone 2 and none on 0 or 1; `winter` had no tone 2 at all. */
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

    /** `tundra` had all ten of its people walking rightward. */
    @Test
    fun `every built-in theme has people walking both ways`() {
        for (id in themeIds) {
            val people = street(id)
            assertTrue("$id has nobody walking right -- ${roster(people)}", people.any { it.direction > 0f })
            assertTrue("$id has nobody walking left -- ${roster(people)}", people.any { it.direction < 0f })
        }
    }

    // ------------------------------------------------------- group sizes

    /** Eight of the twelve themes were missing one of the three group sizes entirely. */
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
     * v4.1 failed this on skin against age: of the 96 people the catalogue produced, tone 2 was
     * 25 children to 10 adults while tone 1 was 20 adults to 11 children -- the darkest tone was
     * a child's tone and the middle one an adult's, on the seeds that actually ship. Neither
     * "the channels are separate" nor a sweep over synthetic seeds shows that; only counting the
     * real ones does.
     *
     * The tolerance is wide on purpose. Ninety-odd people is a small sample and a 50/50 split of
     * it is *expected* to land a few either way; what is being ruled out is the two-to-one lean
     * that was there, not ordinary noise.
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

        // Measured, not guessed. v4.1's strongest lean over these twelve seeds is **0.258** --
        // a lone walker was adult 20% of the time against 46% on the street as a whole, which is
        // the "not one adult male ever walks alone" defect seen from the other side, and its
        // second-strongest is 0.187 for tone 2 being a child's tone. v4.2's strongest is
        // **0.154**. The bound sits between them with comparable room on each side: tight enough
        // that the defect fails it, loose enough that ninety-odd people are allowed to be the
        // small sample they are.
        assertTrue("strongest lean is $worstName", worst <= MAX_LEAN)
    }

    private companion object {
        const val MAX_LEAN = 0.20f
    }

    /**
     * Group size must not decide who is in the group.
     *
     * v4.1's fifteen lone walkers across the catalogue were three women, five boys and seven girls
     * -- **not one adult male ever walked alone** on any shipped theme. Measured per size rather
     * than as a correlation coefficient because that is the shape the defect had.
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
     * **The density parameter still means what it meant.**
     *
     * v4.2 changed what a present group *contains*; it must not have changed which groups are
     * present. This asserts that directly against [CandidateThreshold], the shared rule every
     * other category uses: at every density the set of group indices on the street is exactly the
     * set the threshold selects, so "20% of the pool" is still 20% of the same pool of four slots
     * and not a new meaning wearing the old name.
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
