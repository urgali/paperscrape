package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The invariants of the deterministic candidate system: the same theme is the same scene, a density
 * adds or removes candidates without moving the others, and effects do not share their selection.
 *
 * Written against the pure primitives ([CandidateNoise], [CandidateThreshold]) and the renderer's own
 * seed, [PaperRenderer.seedFor], rather than against the renderer, which needs a `Canvas` and cannot
 * run as a local unit test — the renderer's job is only to feed a candidate index into these
 * functions: that the clouds feed their own pool index is read off its source, and pinning the
 * functions pins the rest.
 */
class CandidateSystemTest {

    private val cloudOffset = CandidateThreshold.offsetFor(EffectId.CLOUDS)
    private val pool = 41

    /** The seed the renderer gives [effect]'s candidates in the theme [themeId]: its own function, not a copy. */
    private fun seedOf(themeId: String, effect: Int): Int =
        PaperRenderer.seedFor(ThemeCatalog.ALL.first { it.id == themeId }, effect)

    private fun survivors(density: Float, poolSize: Int = pool, offset: Float = cloudOffset): List<Int> {
        val fallback = CandidateThreshold.fallbackIndexFor(density, poolSize, offset)
        return (0 until poolSize).filter { CandidateThreshold.isPresent(it, density, offset, fallback) }
    }

    /** All the attributes one cloud candidate receives: the five calls `PaperRenderer.drawClouds` makes, read below. */
    private fun attributes(seed: Int, index: Int): List<Float> = listOf(
        CandidateNoise.value(seed, index, CandidateNoise.CH_X),
        CandidateNoise.value(seed, index, CandidateNoise.CH_Y),
        CandidateNoise.range(seed, index, CandidateNoise.CH_SCALE, 0.85f, 1.25f),
        CandidateNoise.range(seed, index, CandidateNoise.CH_SPEED, 0.004f, 0.008f),
        CandidateNoise.value(seed, index, CandidateNoise.CH_PHASE),
    )

    // --- Same theme + size + time -> same scene -------------------------------------------------

    /**
     * The noise is fixed arithmetic, so its values are the same on every device and in every run, and the committed
     * goldens drawn with it stay valid: the first five of one seed's x channel, as fractions of 2^24, which is all the
     * noise keeps (24 bits, so each is exact in a `Float`).
     */
    @Test
    fun `noise is fully deterministic for the same inputs`() {
        val pinned = intArrayOf(4_728_967, 3_412_132, 1_175_928, 10_290_183, 6_453_549)
        for ((i, top24) in pinned.withIndex()) {
            assertEquals("candidate $i", top24 / 16_777_216f, CandidateNoise.value(12345, i, CandidateNoise.CH_X), 0f)
        }
    }

    /**
     * **The same theme id always produces the same seed**, read off the renderer's own [PaperRenderer.seedFor]:
     * `String.hashCode` is specified exactly by the Java language and the spread is fixed arithmetic, so these are the
     * seeds of `sunset`'s and `christmas`'s nine effects on every device and every run, as the committed goldens were
     * drawn with them -- and with the noise pinned above, the same scene.
     */
    @Test
    fun `the same theme id always produces the same seed and the same scene`() {
        assertEquals(
            listOf(-891172202, 1423356719, -158385692, 273131453, -1304679310, -571168245, 2142048448, -1721393511, 995755870),
            (0 until EffectId.COUNT).map { seedOf("sunset", it) },
        )
        assertEquals(
            listOf(-768650366, 1276652091, -297688848, 143212201, -1426934426, -986041569, 1734576596, -2119497843, 596942410),
            (0 until EffectId.COUNT).map { seedOf("christmas", it) },
        )
    }

    /**
     * **Every two effects of a theme have seeds that differ across the whole word**, not only in its low bits: the
     * effects' ordinals are small consecutive numbers, and the renderer spreads them with a large odd multiplier before
     * they meet the theme's hash, so that two effects stay uncorrelated in their attributes as well as in their
     * selection. With a small multiplier -- 31 -- every pair differs in the low 16 bits alone.
     */
    @Test
    fun `a theme's effects get seeds that differ in their high bits`() {
        for (theme in ThemeCatalog.ALL) {
            for (a in 0 until EffectId.COUNT) {
                for (b in a + 1 until EffectId.COUNT) {
                    val apart = PaperRenderer.seedFor(theme, a) xor PaperRenderer.seedFor(theme, b)
                    assertTrue(
                        "${theme.id}: effects $a and $b differ only in their low 16 bits (%08x)".format(apart),
                        apart ushr 16 != 0,
                    )
                }
            }
        }
    }

    @Test
    fun `different themes produce different scenes`() {
        val sunset = seedOf("sunset", EffectId.CLOUDS)
        val christmas = seedOf("christmas", EffectId.CLOUDS)
        val differing = (0 until pool).count { attributes(sunset, it) != attributes(christmas, it) }
        assertTrue("themes should not share a layout ($differing of $pool differ)", differing > pool * 0.9)
    }

    // --- Attributes are independent of density --------------------------------------------------

    /**
     * **A cloud's attributes are its pool index's, whatever the density**, so a density that adds or removes clouds moves
     * none of the others: `PaperRenderer.drawClouds` walks the whole pool by its index and asks [CandidateNoise] for the five
     * attributes [attributes] asks for, keyed by `seed, i`. A noise keyed by a count of the clouds drawn so far -- what a
     * shared sequential random number generator does -- would shift every cloud after a skipped one. Read off the source,
     * whole lines with the comments taken off: [attributes] takes no density, so only the renderer can make one count.
     */
    @Test
    fun `candidate attributes do not depend on density`() {
        val clouds = codeLines(rendererFunction("drawClouds"))
        assertTrue("drawClouds seeds its clouds as seedOf does", clouds.contains("val seed = seedFor(EffectId.CLOUDS)"))
        assertTrue("drawClouds walks its whole pool by index", clouds.contains("for (i in 0 until CLOUD_POOL_SIZE) {"))
        assertEquals(
            listOf(
                "CandidateNoise.value(seed, i, CandidateNoise.CH_X)",
                "CandidateNoise.value(seed, i, CandidateNoise.CH_Y)",
                "CandidateNoise.range(seed, i, CandidateNoise.CH_SCALE, 0.85f, 1.25f)",
                "CandidateNoise.range(seed, i, CandidateNoise.CH_SPEED, 0.004f, 0.008f)",
                "CandidateNoise.value(seed, i, CandidateNoise.CH_PHASE)",
            ),
            Regex("""CandidateNoise\.(value|range)\([^()]*\)""").findAll(clouds.joinToString("\n")).map { it.value }.toList(),
        )
    }

    // --- Monotonicity in both directions, and linearity --------------------------------------------

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
    fun `lowering density only removes candidates`() {
        var previous = survivors(1f).toSet()
        for (step in 99 downTo 1) {
            val current = survivors(step / 100f).toSet()
            assertTrue(
                "density ${step / 100f} gained candidates ${current - previous}",
                previous.containsAll(current),
            )
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

    // --- Effects do not share a threshold sequence -------------------------------------------------

    @Test
    fun `effect threshold offsets are distinct and well separated`() {
        // Offsets spaced evenly by construction: a salted offset, (salt * 131) % 1000, is identical
        // for any two salts differing by a multiple of 1000, and effects salted that way share one
        // threshold sequence.
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
        for (a in 0 until EffectId.COUNT) {
            for (b in a + 1 until EffectId.COUNT) {
                val seedA = seedOf("sunset", a)
                val seedB = seedOf("sunset", b)
                assertNotEquals(
                    "effects $a and $b would place their candidate 0 identically",
                    attributes(seedA, 0), attributes(seedB, 0),
                )
            }
        }
    }

    @Test
    fun `the old salt collapse is gone`() {
        // The salted arithmetic gives salts 8001 and 9001 one offset; clouds and precipitation must not share one.
        assertEquals((8001 * 131) % 1000, (9001 * 131) % 1000)
        assertNotEquals(
            CandidateThreshold.offsetFor(EffectId.CLOUDS),
            CandidateThreshold.offsetFor(EffectId.PRECIPITATION),
        )
    }

    // --- Small pools keep at least one element -----------------------------------------------------

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

    /** [name]'s declaration and body in `PaperRenderer.kt`, up to the next private function. */
    private fun rendererFunction(name: String): String {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/engine/PaperRenderer.kt"
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = java.io.File(dir, "$prefix$suffix")
                if (f.isFile) {
                    val text = f.readText()
                    assertTrue("$name is gone from PaperRenderer.kt", text.contains("private fun $name("))
                    return text.substringAfter("private fun $name(").substringBefore("\n    private fun ")
                }
            }
            dir = dir.parentFile
        }
        error("source not found: $suffix")
    }

    /** [text]'s lines, trimmed, without comments: a line comment cut off, a KDoc or block comment line dropped. */
    private fun codeLines(text: String): List<String> =
        text.lines().map { it.trim() }
            .filterNot { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }
            .map { it.substringBefore(" //").trim() }
            .filter { it.isNotEmpty() }
}
