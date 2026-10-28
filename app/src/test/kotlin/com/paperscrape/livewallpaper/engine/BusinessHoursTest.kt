package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The opening hours, at the minute, as pure arithmetic.
 *
 * The claims pinned here are the maintainer's decisions, each in its own test: off means constantly open (and
 * therefore bitwise the scene without opening hours), `open == close` means always open, the span wraps midnight; and
 * (`DESIGN_NOTES.md` §12, decision 68, 2026-10-14: *«verificare orari negozi - ho l'impressione che non vengano rispettati»*)
 * **a building is open from its opening minute and shut at its closing
 * minute, and its people are out in the minute before** -- open 09:00-20:00: open at 09:00 with everybody in,
 * nobody in from 19:59, shut at 20:00. Everything runs on the hour the scene computed, which callers take from
 * `DayPhase.hour24` (`SunPositionCalculatorTest` covers that field; nothing here reads a clock). The two seconds
 * the glass takes, and the walks, are the doorway's (`WindowWalkTest`).
 */
class BusinessHoursTest {

    private fun at(hour: Float, open: Float = 9f, close: Float = 20f, enabled: Boolean = true) =
        BusinessHours.opennessAt(enabled, open, close, hour)

    private fun people(hour: Float, open: Float = 9f, close: Float = 20f, enabled: Boolean = true, n: Int = 3) =
        BusinessHours.peopleAt(n, enabled, open, close, hour)

    /** [h] hours and [m] minutes, as `SolarDaySchedule.sceneHour` writes it. */
    private fun hm(h: Int, m: Int): Float = h + m / 60f

    @Test
    fun `disabled is constantly open, with everybody in, whatever the hours say`() {
        var h = 0f
        while (h < 24f) {
            assertEquals("disabled at $h", 1f, at(h, enabled = false), 0f)
            assertEquals("disabled with absurd hours at $h", 1f, BusinessHours.opennessAt(false, 3f, 3.5f, h), 0f)
            assertEquals("disabled at $h", 3, people(h, enabled = false))
            h += 0.25f
        }
    }

    @Test
    fun `open equals close means always open`() {
        // 00:00 until 24:00 is the same minute of the clock twice: always open too.
        var m = 0f
        while (m < 24f) {
            assertEquals("00:00-24:00 at $m", 1f, at(m, open = 0f, close = 24f), 0f)
            assertEquals("00:00-24:00 at $m", 3, people(m, open = 0f, close = 24f))
            m += 1f / 60f
        }
        for (boundary in floatArrayOf(0f, 9f, 20f, 24f)) {
            var h = 0f
            while (h < 24f) {
                assertEquals("open==close==$boundary at $h", 1f, at(h, open = boundary, close = boundary), 0f)
                assertEquals("open==close==$boundary at $h", 3, people(h, open = boundary, close = boundary))
                h += 0.5f
            }
        }
    }

    /**
     * **At the minute**: the minute before the opening shut and empty, the opening minute open with everybody in;
     * the minute before the closing open and empty -- they walk out in it -- and the closing minute shut. Every
     * minute of the day for 09:00-20:00, by the real clock's minutes and by a fixed hour on the hour.
     */
    @Test
    fun `open from the opening minute, empty from the minute before the closing, shut at the closing minute`() {
        assertEquals(0f, at(hm(8, 59)), 0f)
        assertEquals(0, people(hm(8, 59)))
        assertEquals(1f, at(hm(9, 0)), 0f)
        assertEquals(3, people(hm(9, 0)))
        assertEquals(1f, at(hm(19, 58)), 0f)
        assertEquals(3, people(hm(19, 58)))
        assertEquals(1f, at(hm(19, 59)), 0f)
        assertEquals(0, people(hm(19, 59)))
        assertEquals(0f, at(hm(20, 0)), 0f)
        assertEquals(0, people(hm(20, 0)))
        for (minute in 0 until 24 * 60) {
            val hour = minute / 60f
            val open = minute in 9 * 60 until 20 * 60
            val inside = minute in 9 * 60 until 20 * 60 - 1
            assertEquals("09-20 at $minute min", if (open) 1f else 0f, at(hour), 0f)
            assertEquals("09-20 at $minute min", if (inside) 3 else 0, people(hour))
        }
        // A fixed hour at the opening hour is open, at the closing hour shut.
        assertTrue(BusinessHours.isOpenAt(true, 9f, 20f, 9f))
        assertFalse(BusinessHours.isOpenAt(true, 9f, 20f, 20f))
    }

    /**
     * Four spans: 09-20, 07-23, the school's 08-14 and a bar's 18-02 across midnight --
     * the first minute open, the last minute empty, the closing minute shut.
     */
    @Test
    fun `every span opens at its minute, empties a minute before its closing and shuts at it`() {
        for ((open, close) in listOf(9f to 20f, 7f to 23f, 8f to 14f, 18f to 2f, 22f to 6f, 0f to 23.75f, 9.25f to 9.5f)) {
            val span = Math.round(SunPositionCalculator.dayLengthHours(open, close) * 60f)
            val first = Math.round(open * 60f)
            for (k in 0 until 24 * 60) {
                val hour = Math.floorMod(first + k, 24 * 60) / 60f
                assertEquals("$open-$close, $k min after opening", if (k < span) 1f else 0f, at(hour, open, close), 0f)
                assertEquals("$open-$close, $k min after opening", if (k < span - 1) 3 else 0, people(hour, open, close))
            }
        }
    }

    /**
     * The wraparound day the decision names: 09:00–02:00 is a 17-hour span crossing midnight, run through the same
     * wrap arithmetic the solar day uses for a post-midnight sunset.
     */
    @Test
    fun `a span across midnight is open at midnight and shut in the small hours`() {
        assertEquals(1f, at(23f, open = 9f, close = 2f), 0f)
        assertEquals(1f, at(0.5f, open = 9f, close = 2f), 0f)
        assertEquals(1f, at(hm(1, 58), open = 9f, close = 2f), 0f)
        assertEquals(0, people(hm(1, 59), open = 9f, close = 2f))
        assertEquals(0f, at(2f, open = 9f, close = 2f), 0f)
        assertEquals(0f, at(3f, open = 9f, close = 2f), 0f)
        assertEquals(0f, at(8f, open = 9f, close = 2f), 0f)
        assertEquals(1f, at(12f, open = 9f, close = 2f), 0f)
    }

    /**
     * An hour that comes out of `Float` arithmetic a hair under a whole minute is that minute: 09:00 written as
     * `9 + 0 / 60f` and then taken back from the opening is not always 0 to the bit.
     */
    @Test
    fun `an hour a hair under a whole minute is that minute`() {
        for (h in 0 until 24) for (m in 0 until 60) {
            val hour = h + m / 60f
            for (eps in floatArrayOf(-2e-6f, 0f, 2e-6f)) {
                val open = hour - eps
                assertEquals("opening at $h:$m (eps $eps)", 1f, at(hour, open = open, close = open + 1f), 0f)
            }
        }
    }
}
