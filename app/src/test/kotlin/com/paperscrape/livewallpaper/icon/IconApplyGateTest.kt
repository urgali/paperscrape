package com.paperscrape.livewallpaper.icon

import com.paperscrape.livewallpaper.engine.SeasonalCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **No icon before the user's calendar is known** (v5.8C). The controller used to apply the
 * factory calendar's icon at every service start, so a user whose moved window gives today a
 * different icon watched it flip to the factory answer and back a moment later (v5.8B audit).
 */
class IconApplyGateTest {

    @Test
    fun `nothing is applied until the engine reports a calendar`() {
        val gate = IconApplyGate()
        assertFalse("a refresh at service start must not apply the factory calendar", gate.onRefresh())
        assertTrue("the first report applies, even if it is the factory calendar", gate.onCalendar(SeasonalCalendar.DEFAULT))
        assertEquals(SeasonalCalendar.DEFAULT, gate.calendar)
        assertTrue("after that every refresh applies", gate.onRefresh())
    }

    @Test
    fun `the same calendar again applies nothing, a different one does`() {
        val gate = IconApplyGate()
        gate.onCalendar(SeasonalCalendar.DEFAULT)
        assertFalse(gate.onCalendar(SeasonalCalendar.DEFAULT))
        // A copy is equal: equality, not identity, decides.
        assertFalse(gate.onCalendar(SeasonalCalendar.DEFAULT.copy()))
        val moved = SeasonalCalendar.DEFAULT.withSpan(
            com.paperscrape.livewallpaper.engine.CalendarWindow.HALLOWEEN,
            com.paperscrape.livewallpaper.engine.CalendarSpan(10, 15, 11, 2),
        )
        assertTrue("a moved window applies", gate.onCalendar(moved))
        assertEquals(moved, gate.calendar)
    }
}
