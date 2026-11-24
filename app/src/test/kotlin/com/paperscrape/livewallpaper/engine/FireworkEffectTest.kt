package com.paperscrape.livewallpaper.engine

import java.io.File
import java.lang.management.ManagementFactory
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The fireworks' program** ([FireworkEffect], decision 118): single bursts at the wallpaper's rhythm, each with its
 * rocket a second before; a salvo after every five; a finale of seven every three minutes, the first at 40 s; at most
 * twelve bursts in the sky; one of the four styles each, never the one before; one of the six paper colours each; all of it
 * from `SceneRandom.source`, and nothing allocated frame after frame.
 *
 * Run on the BV6600's 720 x 1440 at the wallpaper's 30 frames a second, the fireworks on throughout, with the source seeded.
 */
class FireworkEffectTest {

    private class Launch(
        val slot: Int,
        val at: Double,
        val x: Float,
        val y: Float,
        val source: Int,
        val style: Int,
        val kind: Int,
        val colour: Int,
        val colour2: Int,
        /** The show's clock when the rocket left. */
        val leftAt: Double,
    )

    private class Show(val launches: List<Launch>, val explodedLate: List<String>, val mostAlive: Int, val mostPieces: Int)

    /** [seconds] of fireworks with `SceneRandom.source` = `Random(seed)`, every launch and every burst's instant written down. */
    private fun run(seed: Int, seconds: Int): Show {
        val saved = SceneRandom.source
        SceneRandom.source = Random(seed)
        try {
            val effect = FireworkEffect()
            val launches = ArrayList<Launch>()
            effect.watcher = FireworkEffect.LaunchWatcher { slot, at, x, y, source, style, kind, colour, colour2 ->
                launches += Launch(slot, at, x, y, source, style, kind, colour, colour2, effect.show)
            }
            val awaiting = DoubleArray(FireworkEffect.BURST_CAP) { -1.0 }
            val late = ArrayList<String>()
            var mostAlive = 0
            var mostPieces = 0
            var seen = 0
            repeat(seconds * 30) {
                effect.update(FRAME, true, W, H)
                // A rocket that has become its burst: the burst goes off at its instant, within a frame.
                for (b in 0 until FireworkEffect.BURST_CAP) {
                    if (awaiting[b] >= 0.0 && !effect.burstRocket[b]) {
                        val at = awaiting[b]
                        if (effect.show < at - EPS || effect.show > at + FRAME + EPS) late += "burst due at %.3f went off at %.3f".format(at, effect.show)
                        awaiting[b] = -1.0
                    }
                }
                while (seen < launches.size) {
                    awaiting[launches[seen].slot] = launches[seen].at
                    seen++
                }
                mostAlive = maxOf(mostAlive, effect.liveBursts())
                for (b in 0 until FireworkEffect.BURST_CAP) if (effect.burstLive[b]) mostPieces = maxOf(mostPieces, effect.burstCount[b])
            }
            return Show(launches, late, mostAlive, mostPieces)
        } finally {
            SceneRandom.source = saved
        }
    }

    @Test
    fun `single bursts keep the wallpaper's rhythm, each with its rocket a second before`() {
        val show = run(seed = 2112, seconds = 600)
        val singles = show.launches.filter { it.source == FireworkEffect.EVENT_SINGLE }
        assertTrue("too few single bursts in ten minutes: ${singles.size}", singles.size >= 80)
        assertEquals("the first burst, two seconds into the fireworks", FireworkEffect.FIRST_SINGLE_AT, singles.first().at, 0.0)
        for (i in 1 until singles.size) {
            val gap = singles[i].at - singles[i - 1].at
            assertTrue("a gap of %.3f s between two single bursts".format(gap), gap >= 3.5 - EPS && gap <= 7.5 + EPS)
        }
        for (s in singles) {
            assertTrue("x ${s.x}", s.x in 0.15f..0.85f)
            assertTrue("y ${s.y}", s.y in 0.12f..0.40f)
        }
        for (l in show.launches) {
            val early = l.leftAt - (l.at - FireworkEffect.RISE)
            assertTrue("a rocket for %.3f left at %.3f".format(l.at, l.leftAt), early >= -EPS && early <= FRAME + EPS)
        }
        assertEquals("bursts not at their instant: ${show.explodedLate.take(5)}", 0, show.explodedLate.size)
    }

    @Test
    fun `a salvo after every five single bursts, two more at 0_45 and 0_9 s`() {
        val show = run(seed = 7, seconds = 600)
        val singles = show.launches.filter { it.source == FireworkEffect.EVENT_SINGLE }
        val salvos = show.launches.filter { it.source == FireworkEffect.EVENT_SALVO }
        val expected = singles.filterIndexed { i, _ -> i % 5 == 4 }.flatMap { s -> listOf(s.at + 0.45, s.at + 0.9) }.filter { it < 598.0 }
        val got = salvos.map { it.at }.filter { it < 598.0 }
        assertEquals("salvo instants", expected.size, got.size)
        for ((e, g) in expected.sorted().zip(got.sorted())) assertEquals(e, g, 1e-9)
        // Of the single burst's kind, in other colours.
        for (salvo in salvos) {
            val single = singles.first { abs(salvo.at - it.at - 0.45) < 1e-9 || abs(salvo.at - it.at - 0.9) < 1e-9 }
            assertEquals("a salvo's kind", single.kind, salvo.kind)
            assertTrue("a salvo's colour", salvo.colour != single.colour)
        }
    }

    @Test
    fun `a finale of seven every three minutes, the first at 40 s`() {
        val show = run(seed = 11, seconds = 600)
        val finale = show.launches.filter { it.source == FireworkEffect.EVENT_FINALE }
        val starts = listOf(40.0, 220.0, 400.0, 580.0)
        assertEquals(starts.size * 7, finale.size)
        for ((n, start) in starts.withIndex()) {
            val these = finale.subList(n * 7, n * 7 + 7).sortedBy { it.at }
            for ((k, l) in these.withIndex()) {
                assertEquals("finale at $start, burst $k", start + k * 0.45, l.at, 1e-9)
                assertEquals("finale at $start, burst $k: the five kinds in turn", k % FireworkEffect.KINDS, l.kind)
            }
            assertEquals("seven in 2.7 s", 2.7, these.last().at - these.first().at, 1e-9)
        }
    }

    @Test
    fun `at most twelve bursts in the sky, sixty-four pieces each`() {
        assertEquals(12, FireworkEffect.BURST_CAP)
        assertEquals(64, FireworkEffect.PIECES)
        for (seed in 1..5) {
            val show = run(seed, seconds = 240)
            assertTrue("seed $seed: ${show.mostAlive} bursts at once", show.mostAlive in 7..FireworkEffect.BURST_CAP)
            assertTrue("seed $seed: ${show.mostPieces} pieces in a burst", show.mostPieces in 1..FireworkEffect.PIECES)
        }
    }

    @Test
    fun `one of the four styles each, never the same twice in a row`() {
        val counts = IntArray(5)
        for (seed in 1..10) {
            val styles = run(seed, seconds = 300).launches.map { it.style }
            for (i in styles.indices) {
                assertTrue("seed $seed: style ${styles[i]}", styles[i] in 1..4)
                if (i > 0) assertTrue("seed $seed: style ${styles[i]} twice in a row at launch $i", styles[i] != styles[i - 1])
                counts[styles[i]]++
            }
        }
        // Dealt evenly among the three that are not the last: each of the four near a quarter of the bursts.
        val total = counts.sum()
        for (s in 1..4) assertTrue("style $s: ${counts[s]} of $total", counts[s] > total / 6)
    }

    @Test
    fun `every burst's colour is one of the six paper colours, its second colour another`() {
        assertEquals(
            listOf(0xFFF6C445.toInt(), 0xFFEE5D52.toInt(), 0xFF5FCB8E.toInt(), 0xFF5FA6EC.toInt(), 0xFFF28AC0.toInt(), 0xFFAA8CF0.toInt()),
            FireworkEffect.PAPER.toList(),
        )
        val launches = run(seed = 3, seconds = 600).launches
        for (l in launches) {
            assertTrue("colour ${l.colour}", l.colour in 0 until 6)
            assertTrue("second colour ${l.colour2}", l.colour2 in 0 until 6)
            assertTrue("two colours the same: ${l.colour}", l.colour != l.colour2)
        }
        assertEquals("all six used", 6, launches.map { it.colour }.toSet().size)
    }

    /** Every random draw comes from `SceneRandom.source`: a source that counts its draws counts them, and a seed is a show. */
    @Test
    fun `the randomness is SceneRandom's source, seedable`() {
        class Counting(private val inner: Random) : Random() {
            var draws = 0
            override fun nextBits(bitCount: Int): Int {
                draws++
                return inner.nextBits(bitCount)
            }
        }
        val saved = SceneRandom.source
        val counting = Counting(Random(1))
        SceneRandom.source = counting
        try {
            val effect = FireworkEffect()
            effect.advanceForTest(60 * 30, W, H)
            assertTrue("no draw from SceneRandom.source", counting.draws > 100)
        } finally {
            SceneRandom.source = saved
        }
        fun trace(seed: Int) = run(seed, seconds = 120).launches.map { "%.4f %.5f %.5f %d %d %d %d".format(it.at, it.x, it.y, it.style, it.kind, it.colour, it.colour2) }
        assertEquals(trace(42), trace(42))
        assertFalse(trace(42) == trace(43))
        for (name in listOf("FireworkEffect.kt", "FireworkDrawer.kt")) {
            val code = codeLines(source(name))
            assertTrue("$name reads Random.Default", code.none { "Random.Default" in it })
            assertTrue("$name builds a generator of its own", code.none { Regex("""\bRandom\(""").containsMatchIn(it) })
        }
    }

    /** The JVM's own count of the bytes the thread allocated, over two minutes with salvos and a finale in them. */
    @Test
    fun `the program allocates nothing per frame`() {
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        @Suppress("DEPRECATION") val id = Thread.currentThread().id
        val saved = SceneRandom.source
        SceneRandom.source = Random(5)
        try {
            val effect = FireworkEffect()
            repeat(600) { effect.update(FRAME, true, W, H) } // warm: the first uses
            val before = bean.getThreadAllocatedBytes(id)
            repeat(3600) { effect.update(FRAME, true, W, H) } // 20 s to 140 s: salvos, and the finale at 40 s
            val after = bean.getThreadAllocatedBytes(id)
            assertEquals("bytes allocated over two minutes", 0L, after - before)
            // Off, as by day: the same.
            val offBefore = bean.getThreadAllocatedBytes(id)
            repeat(600) { effect.update(FRAME, false, W, H) }
            assertEquals("bytes allocated while off", 0L, bean.getThreadAllocatedBytes(id) - offBefore)
        } finally {
            SceneRandom.source = saved
        }
    }

    private fun source(name: String): String {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/engine/$name"
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            File(dir, suffix).takeIf { it.isFile }?.let { return it.readText() }
            File(dir, "app/$suffix").takeIf { it.isFile }?.let { return it.readText() }
            dir = dir.parentFile
        }
        error("no $name")
    }

    /** [text]'s lines, trimmed, with comment lines, trailing `//` comments and blank lines left out. */
    private fun codeLines(text: String): List<String> =
        text.lines().map { it.trim() }
            .filterNot { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }
            .map { it.substringBefore(" //").trim() }
            .filter { it.isNotEmpty() }

    private companion object {
        const val FRAME = FireworkEffect.FRAME_SECONDS
        const val W = 720f
        const val H = 1440f
        const val EPS = 1e-3
    }
}
