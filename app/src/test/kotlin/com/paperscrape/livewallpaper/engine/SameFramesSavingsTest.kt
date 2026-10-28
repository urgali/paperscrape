package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Three savings of the frame that give, to the bit, the frame of the plain arithmetic they stand for.** Each is held
 * here to that arithmetic, written out below, so a saving that changes a pixel fails here.
 *
 * - [SceneTime.cycle] works out the fraction as `x - floor(x)` instead of `x % 1.0`;
 * - the rain and the snow skip a drop that no cloud cover can make present before they look the cover up
 *   ([CandidateThreshold.absentUpTo]);
 * - the sill strings' bulbs blink from four values worked out once a building a frame ([SillBlink]).
 */
class SameFramesSavingsTest {

    private fun bits(f: Float) = java.lang.Float.floatToRawIntBits(f)

    /** The plain remainder, `x % 1.0`, that [SceneTime.cycle] matches to the bit. */
    private fun cycleAsItWas(seconds: Double, rate: Float, offset: Float): Float = ((seconds * rate + offset) % 1.0).toFloat()

    @Test
    fun `the cycle is the float the remainder gave, for three million moments and every edge`() {
        val r = java.util.Random(20261026)
        var differing = 0
        repeat(3_000_000) {
            val seconds = r.nextDouble() * 4_000_000.0 // up to 46 days of time on screen
            val rate = r.nextFloat() * 4f
            val offset = r.nextFloat() * 6.3f
            if (bits(SceneTime(seconds).cycle(rate, offset)) != bits(cycleAsItWas(seconds, rate, offset))) differing++
        }
        assertEquals("moments whose cycle differs from the remainder's", 0, differing)
        val edges = listOf(
            0.0, -0.0, Double.MIN_VALUE, 1e-300, 0.5, 0.999999999, 1.0, 1.0000000001, 2.0, 3.5, 1e9, 4.5e15, 9e15,
            1e300, -0.25, -1.0, -3.75, Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN,
        )
        for (seconds in edges) for (rate in listOf(1f, -1f, 0f, 0.37f)) for (offset in listOf(0f, -0f, 0.25f, -0.25f)) {
            assertEquals(
                "cycle($rate, $offset) at $seconds",
                bits(cycleAsItWas(seconds, rate, offset)),
                bits(SceneTime(seconds).cycle(rate, offset)),
            )
        }
    }

    @Test
    fun `a drop skipped before the cover is read is absent under every cover`() {
        val offset = CandidateThreshold.offsetFor(EffectId.PRECIPITATION)
        val pool = 240 // PaperRenderer.PRECIPITATION_POOL_SIZE, decision 10
        // The covers a frame can give: none, full, the soft edge of a cloud (1 - t^2), and arbitrary ones.
        val covers = ArrayList<Float>()
        covers += listOf(0f, 1f, Float.MIN_VALUE, 0.5f, 0.9999999f)
        for (k in 0..200) { val t = k / 200f; covers += 1f - t * t }
        val r = java.util.Random(7)
        repeat(300) { covers += r.nextFloat() }
        val intensities = ArrayList<Float>()
        intensities += listOf(0f, -0.1f, 0.0001f, 0.001f, 0.004f, 0.01f, 0.25f, 0.5f, 0.75f, 0.999f, 1f)
        repeat(200) { intensities += r.nextFloat() }
        var skipped = 0
        var fallbackSeen = false
        for (intensity in intensities) {
            val fallback = CandidateThreshold.fallbackIndexFor(intensity, pool, offset)
            if (fallback >= 0) fallbackSeen = true
            for (i in 0 until pool) {
                if (!CandidateThreshold.absentUpTo(i, intensity, offset, fallback)) continue
                skipped++
                for (cover in covers) {
                    assertFalse(
                        "drop $i skipped at intensity $intensity, present under cover $cover",
                        CandidateThreshold.isPresent(i, intensity * cover, offset, fallback),
                    )
                }
            }
        }
        assertTrue("some drop is skipped below 100 %", skipped > 0)
        assertTrue("a low intensity keeps one drop, the fallback, and it is never skipped", fallbackSeen)
        // And the cover the renderer reads is in 0..1 for any clouds: the condition the skip stands on.
        val field = CloudCoverage(64)
        field.beginFrame()
        repeat(50) { field.addCloud(r.nextFloat() * 1200f - 100f, r.nextFloat() * 200f, 1f + r.nextFloat() * 400f, 1080f) }
        for (x in 0 until 1080 step 7) {
            val c = field.at(x.toFloat(), 1080f)
            assertTrue("cover $c at $x", c in 0f..1f)
        }
    }

    @Test
    fun `the fallback drop of a light shower is not skipped`() {
        val offset = CandidateThreshold.offsetFor(EffectId.PRECIPITATION)
        val intensity = 0.001f
        val fallback = CandidateThreshold.fallbackIndexFor(intensity, 240, offset)
        assertTrue("a fallback exists at $intensity", fallback >= 0)
        assertFalse(CandidateThreshold.absentUpTo(fallback, intensity, offset, fallback))
        assertTrue(CandidateThreshold.isPresent(fallback, intensity * 0.5f, offset, fallback))
    }

    @Test
    fun `a sill bulb blinks as it did, across buildings and frames`() {
        val blink = SillBlink()
        val buildings = listOf(Any(), Any(), Any())
        val seeds = floatArrayOf(0.13f, 0.77f, 0.5f)
        var t = SceneTime(1234.5)
        // Sills of one building, then another, then the first again in the same frame; the same over 300 frames, each frame
        // starting with the building the one before ended with (a screen with one decorated building does just that).
        repeat(300) { frame ->
            for (order in listOf(2, 0, 0, 1, 2, 1, 0, 2, 2)) {
                for (bulb in 0 until ChristmasDecorations.STRING_BULBS) {
                    val expected = 0.55f + 0.45f * t.sinAt(1.6f, seeds[order] * 3.1f + bulb * 1.7f)
                    assertEquals(
                        "frame $frame, building $order, bulb $bulb",
                        bits(expected),
                        bits(blink.at(buildings[order], seeds[order], t, bulb)),
                    )
                }
            }
            t += 1f / 30f
        }
    }
}
