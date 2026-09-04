package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The commercial opening hours (v4.22 Fase 4), as pure arithmetic.
 *
 * The claims pinned here are the maintainer's decisions, each in its own test: off means
 * constantly open (and therefore bitwise the pre-feature scene), `open == close` means always
 * open, the span wraps midnight, the boundary is a crossfade whose duration is **derived** —
 * the same [SunPositionCalculator.smoothEdge] twilight, over the same fraction of the arc — and
 * the whole thing runs on the hour the scene computed, which callers must take from
 * `DayPhase.hour24` (`SunPositionCalculatorTest` covers that field; nothing here reads a clock).
 */
class BusinessHoursTest {

    private fun at(hour: Float, open: Float = 9f, close: Float = 20f, enabled: Boolean = true) =
        BusinessHours.opennessAt(enabled, open, close, hour)

    @Test
    fun `disabled is constantly open, whatever the hours say`() {
        var h = 0f
        while (h < 24f) {
            assertEquals("disabled at $h", 1f, at(h, enabled = false), 0f)
            assertEquals("disabled with absurd hours at $h", 1f, BusinessHours.opennessAt(false, 3f, 3.5f, h), 0f)
            h += 0.25f
        }
    }

    @Test
    fun `open equals close means always open`() {
        for (boundary in floatArrayOf(0f, 9f, 20f, 24f)) {
            var h = 0f
            while (h < 24f) {
                assertEquals("open==close==$boundary at $h", 1f, at(h, open = boundary, close = boundary), 0f)
                h += 0.5f
            }
        }
    }

    @Test
    fun `fully open in the middle of the day and fully closed at night`() {
        assertEquals(1f, at(14f), 0f)
        assertEquals(0f, at(23f), 0f)
        assertEquals(0f, at(3f), 0f)
        // The boundaries themselves are the closed ends of the crossfade.
        assertEquals(0f, at(9f), 0f)
        assertEquals(0f, at(20f), 0f)
    }

    /**
     * The wraparound day the decision names: 09:00–02:00 is a 17-hour span crossing midnight,
     * run through the same wrap arithmetic the solar day uses for a post-midnight sunset.
     */
    @Test
    fun `a span across midnight is open at midnight and closed in the small hours`() {
        // 09:00-02:00 is a 17-hour arc, so its twilight edge is 0.12 * 17 = 2.04 h: the closing
        // ramp runs from 23:57.6 to 02:00. Midnight-and-a-half is therefore *fading*, not full --
        // (1 - 15.5/17) / 0.12 = 0.7353 -- and the fully-open check belongs before the ramp.
        assertEquals(1f, at(23f, open = 9f, close = 2f), 0f)
        assertEquals(0.7353f, at(0.5f, open = 9f, close = 2f), 1e-3f)
        assertTrue("past midnight the shop is still open, if dimming", at(0.5f, open = 9f, close = 2f) > 0f)
        assertEquals(0f, at(3f, open = 9f, close = 2f), 0f)
        assertEquals(0f, at(8f, open = 9f, close = 2f), 0f)
        assertEquals(1f, at(12f, open = 9f, close = 2f), 0f)
    }

    /**
     * The fade and its derived duration. The open span is an arc and the easing is
     * [SunPositionCalculator.smoothEdge], so the ramp occupies exactly
     * [SunPositionCalculator.TWILIGHT_EDGE_FRACTION] of the span at each end — for 09:00–20:00
     * (11 h) that is 1.32 h. Checked at the derivable points: half-way up the ramp at half the
     * edge, fully open exactly where the twilight ends, and symmetric at closing.
     */
    @Test
    fun `the boundary is the scene's own twilight over the opening span`() {
        val span = 11f
        val edgeHours = SunPositionCalculator.TWILIGHT_EDGE_FRACTION * span
        assertEquals("half-open half-way up the opening ramp", 0.5f, at(9f + edgeHours / 2f), 1e-4f)
        assertEquals("fully open where the ramp ends", 1f, at(9f + edgeHours), 1e-4f)
        assertTrue("still fading just before the ramp ends", at(9f + edgeHours * 0.99f) < 1f)
        assertEquals("half-open half-way down the closing ramp", 0.5f, at(20f - edgeHours / 2f), 1e-4f)
        assertEquals("fully open where the closing ramp begins", 1f, at(20f - edgeHours), 1e-4f)
    }

    /** No hour, on either shape of span, may step the openness: the fade is a fade. */
    @Test
    fun `openness is continuous around the clock`() {
        for ((open, close) in listOf(9f to 20f, 9f to 2f, 22f to 6f)) {
            var h = 0f
            var previous = at(0f, open, close)
            // The largest slope in the model is the ramp: 1 over (edge * span) hours. A step of
            // 0.01 h may therefore move the openness at most 0.01 / (0.12 * span), plus rounding.
            val span = SunPositionCalculator.dayLengthHours(open, close)
            val maxStep = 0.01f / (SunPositionCalculator.TWILIGHT_EDGE_FRACTION * span) + 1e-3f
            while (h < 24f) {
                h += 0.01f
                val now = at(h, open, close)
                assertTrue(
                    "openness jumped ${previous} -> $now at $h for $open-$close",
                    kotlin.math.abs(now - previous) <= maxStep,
                )
                previous = now
            }
        }
    }
}
