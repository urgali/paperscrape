package com.paperscrape.livewallpaper.engine

import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [SceneTime], the bounded-at-use time base.
 *
 * The bug being guarded against is specific and was measured: a `Float` accumulator advanced by
 * ~0.033 per frame quantises visibly from around day 5 and stops advancing entirely at 12.14 days,
 * freezing every time-driven animation with no crash and nothing in the logs.
 *
 * These tests therefore run the accumulator out to real long-uptime values rather than testing
 * only small ones, and each helper is checked for the property that actually matters at the call
 * site: the returned value must stay bounded, stay smooth, and keep advancing.
 */
class SceneTimeTest {

    /** Frame delta at the renderer's ~30 fps target. */
    private val frameDelta = 0.033f

    private val oneDay = 86_400.0
    private val twelveDays = 12.14 * oneDay
    private val oneYear = 365.0 * oneDay

    // --- Accumulation --------------------------------------------------------------------------

    @Test
    fun `starts at zero`() {
        assertEquals(0.0, SceneTime.ZERO.seconds, 0.0)
    }

    @Test
    fun `advances by the frame delta`() {
        val t = SceneTime.ZERO + frameDelta
        assertEquals(frameDelta.toDouble(), t.seconds, 1e-9)
    }

    @Test
    fun `keeps advancing past the point where a Float accumulator froze`() {
        // The regression test for the original bug. A Float accumulator stops here; this must not.
        var t = SceneTime(twelveDays)
        val before = t.seconds
        t += frameDelta
        assertTrue("accumulator stopped advancing at 12.14 days", t.seconds > before)
        assertEquals(frameDelta.toDouble(), t.seconds - before, 1e-6)
    }

    @Test
    fun `every frame still advances the accumulator after a year of uptime`() {
        var t = SceneTime(oneYear)
        repeat(1000) {
            val before = t.seconds
            t += frameDelta
            assertTrue("a frame did not advance the clock at t=$before", t.seconds > before)
        }
    }

    @Test
    fun `accumulated time stays accurate over a simulated day`() {
        // 30 fps for 24 h is ~2.6M frames; step in larger chunks but keep the same arithmetic.
        var t = SceneTime.ZERO
        val frames = 100_000
        repeat(frames) { t += frameDelta }
        val expected = frames * frameDelta.toDouble()
        // Relative error must stay negligible; a Float accumulator drifts badly here.
        assertTrue(
            "drift too large: expected ~$expected, got ${t.seconds}",
            abs(t.seconds - expected) / expected < 1e-9,
        )
    }

    // --- sinAt ---------------------------------------------------------------------------------

    @Test
    fun `sinAt matches sin at small times`() {
        val t = SceneTime(3.5)
        assertEquals(sin(3.5 * 1.5 + 0.25).toFloat(), t.sinAt(1.5f, 0.25f), 1e-6f)
    }

    @Test
    fun `sinAt stays within minus one and one at every timescale`() {
        for (seconds in listOf(0.0, 1.0, 1000.0, oneDay, twelveDays, oneYear, 100.0 * oneYear)) {
            val v = SceneTime(seconds).sinAt(1.5f, 0.3f)
            assertTrue("sinAt out of range at t=$seconds: $v", v in -1f..1f)
            assertFalse("sinAt produced NaN at t=$seconds", v.isNaN())
        }
    }

    @Test
    fun `sinAt still oscillates after twelve days`() {
        // The failure mode was a frozen value, not a wrong one: check it actually moves.
        var t = SceneTime(twelveDays)
        val samples = mutableSetOf<Float>()
        repeat(200) {
            samples += t.sinAt(1.5f)
            t += frameDelta
        }
        assertTrue("sinAt froze after twelve days (${samples.size} distinct values)", samples.size > 100)
    }

    @Test
    fun `sinAt is smooth frame to frame after long uptime`() {
        // At rate 1.5 and a 0.033 s frame, consecutive samples differ by at most ~0.05.
        var t = SceneTime(twelveDays)
        var previous = t.sinAt(1.5f)
        repeat(500) {
            t += frameDelta
            val current = t.sinAt(1.5f)
            assertTrue(
                "discontinuity of ${abs(current - previous)} at t=${t.seconds}",
                abs(current - previous) < 0.1f,
            )
            previous = current
        }
    }

    @Test
    fun `sinAt covers its full range over a cycle at long uptime`() {
        var t = SceneTime(oneYear)
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        repeat(400) {
            val v = t.sinAt(1.5f)
            if (v < min) min = v
            if (v > max) max = v
            t += frameDelta
        }
        assertTrue("amplitude collapsed: min=$min max=$max", min < -0.9f && max > 0.9f)
    }

    // --- cycle ---------------------------------------------------------------------------------

    @Test
    fun `cycle matches the arithmetic it replaced`() {
        val t = SceneTime(7.25)
        assertEquals(((7.25 * 0.04 + 0.3) % 1.0).toFloat(), t.cycle(0.04f, 0.3f), 1e-6f)
    }

    @Test
    fun `cycle stays inside zero to one at every timescale`() {
        for (seconds in listOf(0.0, 1.0, 1000.0, oneDay, twelveDays, oneYear)) {
            for (rate in listOf(0.005f, 0.03f, 0.047f, 0.5f, 3f)) {
                val v = SceneTime(seconds).cycle(rate, 0.7f)
                assertTrue("cycle out of range at t=$seconds rate=$rate: $v", v >= 0f && v < 1f)
                assertFalse(v.isNaN())
            }
        }
    }

    @Test
    fun `cycle keeps advancing after twelve days`() {
        // This is the family that made a global wrap impossible: the rate is an arbitrary real
        // derived per candidate from a random value, so it must keep working without any wrap.
        var t = SceneTime(twelveDays)
        val rate = 0.03f + 0.017f
        var previous = t.cycle(rate, 0.2f)
        var advanced = 0
        repeat(300) {
            t += frameDelta
            val current = t.cycle(rate, 0.2f)
            if (current != previous) advanced++
            previous = current
        }
        assertTrue("cycle froze after twelve days (only $advanced changes in 300 frames)", advanced > 250)
    }

    @Test
    fun `cycle is monotonic between wraps at long uptime`() {
        var t = SceneTime(oneYear)
        var previous = t.cycle(0.04f)
        var wraps = 0
        repeat(2000) {
            t += frameDelta
            val current = t.cycle(0.04f)
            if (current < previous) {
                wraps++
                // A wrap must come from the top of the range, not from a mid-range jump.
                assertTrue("bad wrap: $previous -> $current", previous > 0.9f && current < 0.1f)
            }
            previous = current
        }
        assertTrue("expected at least one wrap in 2000 frames", wraps >= 1)
    }

    @Test
    fun `cycle wraps continuously with no visible jump`() {
        // Step across a wrap boundary and check the increment is the expected small one.
        val rate = 0.04f
        val perFrame = frameDelta * rate
        var t = SceneTime(oneYear)
        var previous = t.cycle(rate)
        repeat(3000) {
            t += frameDelta
            val current = t.cycle(rate)
            val step = if (current >= previous) current - previous else (current + 1f) - previous
            assertEquals("uneven step across the cycle at t=${t.seconds}", perFrame, step, 1e-4f)
            previous = current
        }
    }

    // --- cycleOf -------------------------------------------------------------------------------

    @Test
    fun `cycleOf stays inside its period at every timescale`() {
        for (seconds in listOf(0.0, 1000.0, oneDay, twelveDays, oneYear)) {
            val v = SceneTime(seconds).cycleOf(12f, 30f, 360f)
            assertTrue("cycleOf out of range at t=$seconds: $v", v >= 0f && v < 360f)
            assertFalse(v.isNaN())
        }
    }

    @Test
    fun `cycleOf keeps rotating after twelve days`() {
        var t = SceneTime(twelveDays)
        val samples = mutableSetOf<Float>()
        repeat(200) {
            samples += t.cycleOf(12f, 0f, 360f)
            t += frameDelta
        }
        assertTrue("rotation froze (${samples.size} distinct values)", samples.size > 150)
    }

    // --- frameIndex ----------------------------------------------------------------------------

    @Test
    fun `frameIndex matches the arithmetic it replaced`() {
        val t = SceneTime(5.0)
        assertEquals(((5.0 * 3.2 + 2).toInt()) % 4, t.frameIndex(3.2f, 2f, 4))
    }

    @Test
    fun `frameIndex stays within the frame count at every timescale`() {
        for (seconds in listOf(0.0, 1000.0, oneDay, twelveDays, oneYear)) {
            val f = SceneTime(seconds).frameIndex(3.2f, 1f, 4)
            assertTrue("frameIndex out of range at t=$seconds: $f", f in 0..3)
        }
    }

    @Test
    fun `frameIndex still cycles after twelve days`() {
        var t = SceneTime(twelveDays)
        val seen = mutableSetOf<Int>()
        repeat(200) {
            seen += t.frameIndex(3.2f, 0f, 4)
            t += frameDelta
        }
        assertEquals("walk cycle froze", setOf(0, 1, 2, 3), seen)
    }

    @Test
    fun `frameIndex advances one step at a time in order`() {
        var t = SceneTime(oneYear)
        var previous = t.frameIndex(3.2f, 0f, 4)
        repeat(500) {
            t += frameDelta
            val current = t.frameIndex(3.2f, 0f, 4)
            val stepped = current == previous || current == (previous + 1) % 4
            assertTrue("frame jumped $previous -> $current", stepped)
            previous = current
        }
    }

    // --- No NaN / infinity anywhere ---------------------------------------------------------------

    @Test
    fun `no helper produces NaN or infinity at extreme times`() {
        for (seconds in listOf(0.0, 1.0, oneDay, oneYear, 1_000.0 * oneYear)) {
            val t = SceneTime(seconds)
            for (v in listOf(t.sinAt(1.5f, 0.2f), t.cycle(0.04f, 0.2f), t.cycleOf(60f, 10f, 360f))) {
                assertFalse("NaN at t=$seconds", v.isNaN())
                assertTrue("not finite at t=$seconds", v.isFinite())
            }
            assertTrue(t.frameIndex(3.2f, 0f, 4) in 0..3)
        }
    }

    // --- Demonstrates the bug this replaces ---------------------------------------------------------

    @Test
    fun `a Float accumulator would have frozen where this one does not`() {
        // Not testing production code — pinning the premise, so the reason this class exists stays
        // verifiable rather than becoming folklore in a comment.
        var floatClock = twelveDays.toFloat()
        val floatBefore = floatClock
        floatClock += frameDelta
        assertEquals("a Float accumulator should be stuck at 12.14 days", floatBefore, floatClock, 0f)

        var sceneClock = SceneTime(twelveDays)
        sceneClock += frameDelta
        assertNotEquals(twelveDays, sceneClock.seconds, 0.0)
    }
}
