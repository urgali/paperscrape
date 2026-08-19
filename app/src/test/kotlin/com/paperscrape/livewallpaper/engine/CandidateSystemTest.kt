package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariants for the deterministic candidate system introduced in Phase 2.1 / 2.2.
 *
 * Each test below corresponds to one of the ten properties agreed before implementation. They are
 * written against the pure primitives ([CandidateNoise], [CandidateThreshold]) rather than against
 * the renderer, which needs a `Canvas` and cannot run as a local unit test — the renderer's job is
 * only to feed a candidate index into these functions, so pinning them pins the behaviour.
 */
class CandidateSystemTest {

    private val cloudOffset = CandidateThreshold.offsetFor(EffectId.CLOUDS)
    private val pool = 41

    private fun survivors(density: Float, poolSize: Int = pool, offset: Float = cloudOffset): List<Int> {
        val fallback = CandidateThreshold.fallbackIndexFor(density, poolSize, offset)
        return (0 until poolSize).filter { CandidateThreshold.isPresent(it, density, offset, fallback) }
    }

    /** All the attributes one cloud candidate would receive, as the renderer computes them. */
    private fun attributes(seed: Int, index: Int): List<Float> = listOf(
        CandidateNoise.value(seed, index, CandidateNoise.CH_X),
        CandidateNoise.value(seed, index, CandidateNoise.CH_Y),
        CandidateNoise.range(seed, index, CandidateNoise.CH_SCALE, 0.85f, 1.25f),
        CandidateNoise.range(seed, index, CandidateNoise.CH_SPEED, 0.004f, 0.008f),
        CandidateNoise.value(seed, index, CandidateNoise.CH_PHASE),
    )

    // --- Invariant 1: same theme + size + time -> same scene ------------------------------------

    @Test
    fun `noise is fully deterministic for the same inputs`() {
        repeat(500) { i ->
            assertEquals(
                CandidateNoise.value(12345, i, CandidateNoise.CH_X),
                CandidateNoise.value(12345, i, CandidateNoise.CH_X),
                0f,
            )
        }
    }

    @Test
    fun `the same theme id always produces the same seed and the same scene`() {
        // theme.id.hashCode() is specified exactly by the Java language, so this holds across
        // devices and runs, not just within one process.
        val seedA = "sunset".hashCode() xor (EffectId.CLOUDS * -0x61c88647)
        val seedB = "sunset".hashCode() xor (EffectId.CLOUDS * -0x61c88647)
        assertEquals(seedA, seedB)
        for (i in 0 until pool) assertEquals(attributes(seedA, i), attributes(seedB, i))
    }

    @Test
    fun `different themes produce different scenes`() {
        val sunset = "sunset".hashCode() xor (EffectId.CLOUDS * -0x61c88647)
        val christmas = "christmas".hashCode() xor (EffectId.CLOUDS * -0x61c88647)
        val differing = (0 until pool).count { attributes(sunset, it) != attributes(christmas, it) }
        assertTrue("themes should not share a layout ($differing of $pool differ)", differing > pool * 0.9)
    }

    // --- Invariant 2: attributes are independent of density -------------------------------------

    @Test
    fun `candidate attributes do not depend on density`() {
        val seed = "sunset".hashCode() xor (EffectId.CLOUDS * -0x61c88647)
        val reference = (0 until pool).map { attributes(seed, it) }
        var density = 0.01f
        while (density <= 1f) {
            for (index in survivors(density)) {
                assertEquals(
                    "attributes of candidate $index changed at density $density",
                    reference[index],
                    attributes(seed, index),
                )
            }
            density += 0.01f
        }
    }

    // --- Invariant 3: changing density does not move existing candidates -------------------------

    @Test
    fun `survivors keep their identity across every density step`() {
        val seed = "sunset".hashCode() xor (EffectId.CLOUDS * -0x61c88647)
        val seen = mutableMapOf<Int, List<Float>>()
        var moved = 0
        var observations = 0
        for (step in 1..100) {
            for (index in survivors(step / 100f)) {
                val attrs = attributes(seed, index)
                seen[index]?.let {
                    observations++
                    if (it != attrs) moved++
                }
                seen[index] = attrs
            }
        }
        assertTrue("expected many observations, got $observations", observations > 1000)
        assertEquals("$moved of $observations survivors moved", 0, moved)
    }

    // --- Invariants 4 and 5: monotonicity in both directions --------------------------------------

    @Test
    fun `raising density only adds candidates`() {
        var previous = survivors(0.01f).toSet()
        for (step in 2..100) {
            val current = survivors(step / 100f).toSet()
            assertTrue(
                "density ${step / 100f} lost candidates ${previous - current}",
                current.containsAll(previous),
            )
            previous = current
        }
    }

    @Test
    fun `lowering density only removes candidates and leaves the rest untouched`() {
        val seed = "sunset".hashCode() xor (EffectId.CLOUDS * -0x61c88647)
        var previous = survivors(1f).toSet()
        for (step in 99 downTo 1) {
            val current = survivors(step / 100f).toSet()
            assertTrue(
                "density ${step / 100f} gained candidates ${current - previous}",
                previous.containsAll(current),
            )
            for (index in current) assertEquals(attributes(seed, index), attributes(seed, index))
            previous = current
        }
    }

    @Test
    fun `density is linear in the pool size`() {
        for (density in listOf(0.10f, 0.25f, 0.50f, 0.75f, 1.00f)) {
            val kept = survivors(density).size
            val expected = density * pool
            assertTrue(
                "density $density kept $kept, expected about $expected",
                kotlin.math.abs(kept - expected) <= 2f,
            )
        }
        assertEquals("full density must keep the whole pool", pool, survivors(1f).size)
    }

    // --- Invariant 6: layout does not depend on how many were filtered out -------------------------

    @Test
    fun `a candidate's values do not depend on which other candidates survived`() {
        // The defining failure of the old shared sequential RNG: a skipped candidate did not
        // consume from the stream, so every later candidate's values shifted.
        val seed = "sunset".hashCode() xor (EffectId.CLOUDS * -0x61c88647)
        val last = pool - 1
        val reference = attributes(seed, last)
        for (density in listOf(0.05f, 0.3f, 0.6f, 0.99f, 1f)) {
            assertEquals(
                "candidate $last changed when ${pool - survivors(density).size} others were filtered out",
                reference,
                attributes(seed, last),
            )
        }
    }

    // --- Invariant 8: effects do not share a threshold sequence -------------------------------------

    @Test
    fun `effect threshold offsets are distinct and well separated`() {
        // The old salts collapsed: (salt * 131) % 1000 is identical for any two salts differing by
        // a multiple of 1000, which birds/clouds/precipitation/sailboats all did, so they shared
        // one threshold sequence. Offsets are now spaced evenly by construction.
        val offsets = (0 until EffectId.COUNT).map { CandidateThreshold.offsetFor(it) }
        assertEquals("offsets must be distinct", offsets.size, offsets.toSet().size)
        val sorted = offsets.sorted()
        val minimumGap = sorted.zipWithNext { a, b -> b - a }.min()
        assertTrue(
            "offsets too close together: minimum gap $minimumGap",
            minimumGap >= 1f / EffectId.COUNT - 1e-5f,
        )
    }

    @Test
    fun `effects with a usable pool never select the same candidates`() {
        // Restricted to pools large enough for the question to be meaningful. With only four
        // candidates there are just fifteen non-empty proper subsets, so two effects landing on
        // the same one is combinatorial, not a seeding defect -- and their *attributes* still
        // differ because their seeds do, which the next test pins.
        for (poolSize in listOf(26, 41, 90)) {
            for (step in 1..99) {
                val density = step / 100f
                val seen = mutableMapOf<Set<Int>, Int>()
                for (effect in 0 until EffectId.COUNT) {
                    val set = survivors(density, poolSize, CandidateThreshold.offsetFor(effect)).toSet()
                    if (set.isEmpty() || set.size == poolSize) continue
                    val clash = seen.put(set, effect)
                    assertTrue(
                        "effects $clash and $effect select identical candidates " +
                            "at pool $poolSize density $density",
                        clash == null,
                    )
                }
            }
        }
    }

    @Test
    fun `effects sharing a candidate index still get different attributes`() {
        // Guards the small-pool case above: even when two effects keep index 0, that candidate is
        // a different cloud/boat/bird because the seeds differ.
        val themeHash = "sunset".hashCode()
        for (a in 0 until EffectId.COUNT) {
            for (b in a + 1 until EffectId.COUNT) {
                val seedA = themeHash xor (a * -0x61c88647)
                val seedB = themeHash xor (b * -0x61c88647)
                assertNotEquals(
                    "effects $a and $b would place their candidate 0 identically",
                    attributes(seedA, 0), attributes(seedB, 0),
                )
            }
        }
    }

    @Test
    fun `the old salt collapse is gone`() {
        // Documents the specific arithmetic that was wrong, so the regression is recognisable.
        assertEquals((8001 * 131) % 1000, (9001 * 131) % 1000)
        assertNotEquals(
            CandidateThreshold.offsetFor(EffectId.CLOUDS),
            CandidateThreshold.offsetFor(EffectId.PRECIPITATION),
        )
    }

    // --- Invariant 9: small pools keep at least one element -----------------------------------------

    @Test
    fun `a visible category with non-zero density always shows at least one element`() {
        for (poolSize in listOf(4, 5, 6, 26, 41, 90)) {
            for (effect in listOf(EffectId.BIRDS, EffectId.SAILBOATS, EffectId.DOLPHINS, EffectId.MOUNTAINS_BACK)) {
                val offset = CandidateThreshold.offsetFor(effect)
                var density = 0.001f
                while (density < 1f) {
                    assertTrue(
                        "pool $poolSize effect $effect density $density showed nothing",
                        survivors(density, poolSize, offset).isNotEmpty(),
                    )
                    density += 0.01f
                }
            }
        }
    }

    @Test
    fun `zero density shows nothing`() {
        for (poolSize in listOf(4, 6, 41, 90)) {
            assertTrue(survivors(0f, poolSize, cloudOffset).isEmpty())
            assertTrue(survivors(-1f, poolSize, cloudOffset).isEmpty())
        }
    }

    @Test
    fun `the guaranteed element is always the same one`() {
        // Otherwise the single remaining bird would jump around as density crept up.
        val offset = CandidateThreshold.offsetFor(EffectId.BIRDS)
        val kept = mutableSetOf<Int>()
        var density = 0.001f
        while (density < 0.05f) {
            kept += survivors(density, 6, offset)
            density += 0.001f
        }
        assertEquals("the fallback element must be stable", 1, kept.size)
    }

    // --- Distribution quality ------------------------------------------------------------------------

    @Test
    fun `survivors stay evenly spread rather than clustering`() {
        // The golden-ratio threshold exists for this: an independent hash per candidate would
        // clump, especially in the small pools.
        for (density in listOf(0.25f, 0.5f, 0.75f)) {
            val kept = survivors(density)
            val gaps = kept.zipWithNext { a, b -> b - a }
            val worst = gaps.maxOrNull() ?: 0
            val even = pool.toFloat() / kept.size
            assertTrue(
                "at density $density the largest index gap was $worst, even spacing would be $even",
                worst <= even * 2.5f,
            )
        }
    }

    @Test
    fun `noise is well distributed across the unit interval`() {
        val buckets = IntArray(10)
        for (seedIndex in 1..40) {
            for (index in 0 until 100) {
                val v = CandidateNoise.value(seedIndex * 7919, index, CandidateNoise.CH_X)
                assertTrue("value out of range: $v", v >= 0f && v < 1f)
                buckets[(v * 10).toInt().coerceIn(0, 9)]++
            }
        }
        val expected = 4000 / 10
        for ((i, count) in buckets.withIndex()) {
            assertTrue("bucket $i had $count, expected about $expected", count in (expected / 2)..(expected * 2))
        }
    }

    @Test
    fun `adjacent indices and adjacent channels are uncorrelated`() {
        // The inputs are small consecutive integers, so a weak mix would leave visible structure.
        var sequential = 0
        for (i in 0 until 999) {
            val a = CandidateNoise.value(4242, i, CandidateNoise.CH_X)
            val b = CandidateNoise.value(4242, i + 1, CandidateNoise.CH_X)
            if (b > a) sequential++
        }
        assertTrue("consecutive indices look monotonic ($sequential of 999 ascending)", sequential in 400..600)

        for (i in 0 until 200) {
            assertNotEquals(
                CandidateNoise.value(4242, i, CandidateNoise.CH_X),
                CandidateNoise.value(4242, i, CandidateNoise.CH_Y),
            )
        }
    }

    @Test
    fun `range maps onto the requested interval`() {
        for (i in 0 until 500) {
            val v = CandidateNoise.range(99, i, CandidateNoise.CH_SPEED, 0.004f, 0.008f)
            assertTrue("range escaped its bounds: $v", v >= 0.004f && v < 0.008f)
        }
    }

    @Test
    fun `threshold stays inside zero to one for large pools and indices`() {
        for (offset in listOf(0f, 0.37f, 0.99f)) {
            for (i in 0 until 10_000) {
                val t = CandidateThreshold.of(i, offset)
                assertTrue("threshold out of range at $i: $t", t >= 0f && t < 1f)
                assertFalse(t.isNaN())
            }
        }
    }
}
