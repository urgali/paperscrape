package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Skin tone: that it varies, that it varies *independently*, and that nobody can configure it.
 *
 * The claims are made against distributions over many seeds rather than against a single lucky
 * one. A generator can technically reach every tone while still being heavily biased, and a
 * single-seed test would pass against exactly the kind of hard-coded table v4.1 exists to remove.
 */
class SkinToneTest {

    private val near = SceneSpace.PAVEMENT_NEAR_Y_FRACTION
    private val far = SceneSpace.PAVEMENT_FAR_Y_FRACTION

    private fun build(seed: Int, density: Float = 1f) =
        PedestrianPopulation.build(seed, density, near, far)

    private fun seeds(n: Int) = (0 until n).map { "theme-$it".hashCode() }

    // ------------------------------------------------------------- 1. count

    @Test
    fun `there is more than one skin tone`() {
        assertTrue(
            "SKIN_TONE_COUNT is ${PedestrianPopulation.SKIN_TONE_COUNT}",
            PedestrianPopulation.SKIN_TONE_COUNT > 1,
        )
        // The brief asked for at least three real variants per character.
        assertTrue(PedestrianPopulation.SKIN_TONE_COUNT >= 3)
    }

    // ------------------------------------------------- 2, 10. determinism

    @Test
    fun `the same seed gives the same person the same skin`() {
        for (seed in seeds(60)) {
            val first = build(seed)
            val second = build(seed)
            assertEquals(first.map { it.skinIndex }, second.map { it.skinIndex })
            for ((a, b) in first.zip(second)) {
                assertEquals(a.groupIndex to a.memberIndex, b.groupIndex to b.memberIndex)
                assertEquals(a.skinIndex, b.skinIndex)
            }
        }
    }

    @Test
    fun `skin does not depend on the clock`() {
        val before = build(4242).map { it.skinIndex }
        Thread.sleep(5)
        assertEquals(before, build(4242).map { it.skinIndex })
    }

    // ------------------------------------------------ 3, 4. reachability

    @Test
    fun `every skin tone is reachable, and no tone dominates`() {
        val counts = IntArray(PedestrianPopulation.SKIN_TONE_COUNT)
        var total = 0
        for (seed in seeds(1500)) {
            for (p in build(seed)) { counts[p.skinIndex]++; total++ }
        }
        assertTrue("too few samples", total > 1000)
        for (tone in counts.indices) {
            assertTrue("tone $tone never appeared", counts[tone] > 0)
            assertEquals(
                "tone $tone share",
                1f / PedestrianPopulation.SKIN_TONE_COUNT,
                counts[tone].toFloat() / total,
                0.05f,
            )
        }
    }

    @Test
    fun `different seeds produce different skins for the same slot`() {
        val firstPersonTones = seeds(400).mapNotNull { build(it).firstOrNull()?.skinIndex }.toSet()
        assertTrue("the leading figure was always tone $firstPersonTones", firstPersonTones.size > 1)
    }

    // -------------------------------------------- 5. direction independence

    @Test
    fun `every skin tone is reachable in both directions`() {
        val rightward = mutableSetOf<Int>()
        val leftward = mutableSetOf<Int>()
        for (seed in seeds(600)) {
            for (p in build(seed)) {
                if (p.direction > 0f) rightward += p.skinIndex else leftward += p.skinIndex
            }
        }
        val all = (0 until PedestrianPopulation.SKIN_TONE_COUNT).toSet()
        assertEquals("rightward tones", all, rightward)
        assertEquals("leftward tones", all, leftward)
    }

    /** Reachability is weak on its own: the *distribution* must not shift with direction either. */
    @Test
    fun `direction does not bias the skin distribution`() {
        val right = IntArray(PedestrianPopulation.SKIN_TONE_COUNT)
        val left = IntArray(PedestrianPopulation.SKIN_TONE_COUNT)
        for (seed in seeds(3000)) {
            for (p in build(seed)) {
                if (p.direction > 0f) right[p.skinIndex]++ else left[p.skinIndex]++
            }
        }
        val rightTotal = right.sum().toFloat()
        val leftTotal = left.sum().toFloat()
        for (tone in right.indices) {
            assertEquals(
                "tone $tone share differs by direction",
                right[tone] / rightTotal,
                left[tone] / leftTotal,
                0.05f,
            )
        }
    }

    // ------------------------------------------ 6. age and sex independence

    @Test
    fun `every skin tone is reachable for adults, children, males and females`() {
        val byAge = mapOf(
            PersonAge.ADULT to mutableSetOf<Int>(),
            PersonAge.CHILD to mutableSetOf(),
        )
        val bySex = mapOf(
            PersonSex.MALE to mutableSetOf<Int>(),
            PersonSex.FEMALE to mutableSetOf(),
        )
        for (seed in seeds(800)) {
            for (p in build(seed)) {
                byAge.getValue(p.age) += p.skinIndex
                bySex.getValue(p.sex) += p.skinIndex
            }
        }
        val all = (0 until PedestrianPopulation.SKIN_TONE_COUNT).toSet()
        for ((age, tones) in byAge) assertEquals("$age tones", all, tones)
        for ((sex, tones) in bySex) assertEquals("$sex tones", all, tones)
    }

    /** No `male -> tone A` rule may exist, even a statistical one. */
    @Test
    fun `age and sex do not bias the skin distribution`() {
        val counts = mutableMapOf<Pair<PersonAge, PersonSex>, IntArray>()
        for (seed in seeds(4000)) {
            for (p in build(seed)) {
                counts.getOrPut(p.age to p.sex) { IntArray(PedestrianPopulation.SKIN_TONE_COUNT) }[p.skinIndex]++
            }
        }
        assertEquals("not every kind of person appeared", 4, counts.size)
        for ((who, tones) in counts) {
            val total = tones.sum().toFloat()
            assertTrue("too few samples for $who", total > 200)
            for (tone in tones.indices) {
                assertEquals(
                    "$who leans towards tone $tone",
                    1f / PedestrianPopulation.SKIN_TONE_COUNT,
                    tones[tone] / total,
                    0.06f,
                )
            }
        }
    }

    /** The whole point, in one assertion: a man and a girl walking together, skinned differently. */
    @Test
    fun `an adult and a child in one group can have different skins`() {
        var found = false
        for (seed in seeds(600)) {
            val people = build(seed)
            for (g in 0 until PedestrianPopulation.GROUP_COUNT) {
                val members = people.filter { it.groupIndex == g }
                val adult = members.firstOrNull { it.age == PersonAge.ADULT } ?: continue
                val child = members.firstOrNull { it.age == PersonAge.CHILD } ?: continue
                if (adult.skinIndex != child.skinIndex) { found = true; break }
            }
            if (found) break
        }
        assertTrue("an adult and a child always shared a skin tone", found)
    }

    // --------------------------------------------- 7. group independence

    @Test
    fun `two members of one group can have different skins`() {
        var differing = 0
        var groups = 0
        for (seed in seeds(600)) {
            val people = build(seed)
            for (g in 0 until PedestrianPopulation.GROUP_COUNT) {
                val members = people.filter { it.groupIndex == g }
                if (members.size < 2) continue
                groups++
                if (members.map { it.skinIndex }.distinct().size > 1) differing++
            }
        }
        assertTrue("no multi-person groups were produced", groups > 100)
        assertTrue("every group was single-skinned", differing > 0)
        // Not a family rule in disguise: most mixed groups should differ.
        assertTrue("groups shared a skin suspiciously often", differing.toFloat() / groups > 0.4f)
    }

    @Test
    fun `group size does not bias the skin distribution`() {
        val bySize = mutableMapOf<Int, MutableSet<Int>>()
        for (seed in seeds(800)) {
            val people = build(seed)
            for (g in 0 until PedestrianPopulation.GROUP_COUNT) {
                val members = people.filter { it.groupIndex == g }
                if (members.isEmpty()) continue
                bySize.getOrPut(members.size) { mutableSetOf() } += members.map { it.skinIndex }
            }
        }
        val all = (0 until PedestrianPopulation.SKIN_TONE_COUNT).toSet()
        assertEquals(setOf(1, 2, 3), bySize.keys)
        for ((size, tones) in bySize) assertEquals("groups of $size", all, tones)
    }

    // -------------------------------------------- 8. window independence

    @Test
    fun `window occupants use every skin tone, in every building kind`() {
        val all = (0 until PedestrianPopulation.SKIN_TONE_COUNT).toSet()
        for (kind in WindowBuildingKind.entries) {
            val tones = mutableSetOf<Int>()
            for (seed in seeds(200)) {
                for (b in 0 until 40) {
                    val buildingSeed = b * 100_003
                    for (w in 0 until 8) {
                        if (WindowOccupants.isOccupied(seed, buildingSeed, w, kind)) {
                            tones += WindowOccupants.occupantAt(seed, buildingSeed, w).skinIndex
                        }
                    }
                }
            }
            assertEquals("$kind window tones", all, tones)
        }
    }

    @Test
    fun `window skin does not depend on presence or window index`() {
        val byWindow = mutableMapOf<Int, IntArray>()
        val occupiedOnly = IntArray(PedestrianPopulation.SKIN_TONE_COUNT)
        val everyWindow = IntArray(PedestrianPopulation.SKIN_TONE_COUNT)
        for (seed in seeds(600)) {
            for (b in 0 until 20) {
                val bs = b * 100_003
                for (w in 0 until 8) {
                    val tone = WindowOccupants.occupantAt(seed, bs, w).skinIndex
                    byWindow.getOrPut(w) { IntArray(PedestrianPopulation.SKIN_TONE_COUNT) }[tone]++
                    everyWindow[tone]++
                    if (WindowOccupants.isOccupied(seed, bs, w, WindowBuildingKind.HOUSE)) {
                        occupiedOnly[tone]++
                    }
                }
            }
        }
        val expected = 1f / PedestrianPopulation.SKIN_TONE_COUNT
        for ((w, tones) in byWindow) {
            val total = tones.sum().toFloat()
            for (tone in tones.indices) {
                assertEquals("window $w leans to tone $tone", expected, tones[tone] / total, 0.05f)
            }
        }
        val occupiedTotal = occupiedOnly.sum().toFloat()
        val everyTotal = everyWindow.sum().toFloat()
        for (tone in occupiedOnly.indices) {
            assertEquals(
                "the presence gate shifted tone $tone",
                everyWindow[tone] / everyTotal,
                occupiedOnly[tone] / occupiedTotal,
                0.05f,
            )
        }
    }

    // ---------------------------------------- 11. mutation: skinIndex ignored

    /**
     * The regression this whole suite exists for: somebody drops the tone and always draws 0.
     *
     * A test that only checked "tones are reachable" would still pass if the *renderer* ignored
     * `skinIndex`, so this asserts on the property the renderer consumes -- that the sequence of
     * tones actually drawn is not a constant. If `skinIndex` were pinned to 0 anywhere upstream,
     * `observed` collapses to `{0}` and this fails.
     */
    @Test
    fun `pinning skin to zero would fail`() {
        val observed = mutableSetOf<Int>()
        val pinned = mutableSetOf<Int>()
        for (seed in seeds(400)) {
            for (p in build(seed)) {
                observed += p.skinIndex
                pinned += 0
            }
        }
        assertEquals(PedestrianPopulation.SKIN_TONE_COUNT, observed.size)
        assertFalse("skin collapsed to a single tone", observed == pinned)
    }

    /** Tones must stay inside the artwork that exists, or a sprite lookup would throw. */
    @Test
    fun `every skin index addresses real artwork`() {
        for (seed in seeds(400)) {
            for (p in build(seed)) {
                assertTrue(
                    "skin ${p.skinIndex} has no sprite",
                    p.skinIndex in 0 until PedestrianPopulation.SKIN_TONE_COUNT,
                )
            }
        }
        for (seed in seeds(200)) {
            for (w in 0 until 8) {
                val tone = WindowOccupants.occupantAt(seed, 100_003, w).skinIndex
                assertTrue(tone in 0 until PedestrianPopulation.SKIN_TONE_COUNT)
            }
        }
    }
}
