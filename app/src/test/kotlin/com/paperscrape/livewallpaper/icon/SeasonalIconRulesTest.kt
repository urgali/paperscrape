package com.paperscrape.livewallpaper.icon

import com.paperscrape.livewallpaper.engine.CalendarSpan
import com.paperscrape.livewallpaper.engine.CalendarTier
import com.paperscrape.livewallpaper.engine.CalendarWindow
import com.paperscrape.livewallpaper.engine.SeasonalCalendar
import com.paperscrape.livewallpaper.engine.SeasonalThemeRules
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The launcher icon is a function of the date and of the user's calendar, and this is that
 * function.
 *
 * Eight windows, five seasonal icons and a neutral one. Everything worth getting wrong is in the
 * join: the three occasions with no icon of their own have to come out as the season they land
 * in, and they have to keep doing so when the user has dragged that season somewhere else --
 * which is the whole reason the fallback is derived from the calendar instead of written into a
 * table beside it.
 */
class SeasonalIconRulesTest {

    private fun iconOn(month: Int, day: Int, calendar: SeasonalCalendar = SeasonalCalendar.DEFAULT) =
        SeasonalIconRules.iconForDate(LocalDate.of(2026, month, day), calendar)

    // --- the four seasons, factory calendar --------------------------------------------------

    @Test
    fun `each season shows its own icon`() {
        assertEquals(SeasonalIcon.WINTER, iconOn(2, 10))
        assertEquals(SeasonalIcon.SPRING, iconOn(5, 10))
        assertEquals(SeasonalIcon.SUMMER, iconOn(7, 10))
        assertEquals(SeasonalIcon.AUTUMN, iconOn(9, 10))
    }

    @Test
    fun `Christmas is the one occasion with an icon of its own`() {
        assertEquals(SeasonalIcon.CHRISTMAS, iconOn(12, 1))
        assertEquals(SeasonalIcon.CHRISTMAS, iconOn(12, 26))
        // 27 December is New Year's window, which has no icon: it falls back to winter, the
        // season underneath it -- and *not* to Christmas, though the day before was Christmas.
        assertEquals(SeasonalIcon.WINTER, iconOn(12, 27))
    }

    // --- the three occasions with no icon ----------------------------------------------------

    @Test
    fun `an occasion with no icon takes the season it falls in`() {
        // Halloween -> autumn, New Year -> winter, Easter -> spring, with the factory calendar.
        assertEquals(CalendarWindow.HALLOWEEN, SeasonalThemeRules.windowForDate(LocalDate.of(2026, 10, 31)))
        assertEquals(SeasonalIcon.AUTUMN, iconOn(10, 31))

        assertEquals(CalendarWindow.NEW_YEAR, SeasonalThemeRules.windowForDate(LocalDate.of(2026, 1, 3)))
        assertEquals(SeasonalIcon.WINTER, iconOn(1, 3))

        val easter = SeasonalThemeRules.computeEasterSunday(2026)
        assertEquals(CalendarWindow.EASTER, SeasonalThemeRules.windowForDate(easter))
        assertEquals(SeasonalIcon.SPRING, SeasonalIconRules.iconForDate(easter))
    }

    @Test
    fun `moving a season moves the icon of the occasion that sits on it`() {
        // The user drags autumn out of October and lets summer run through it. Halloween has not
        // moved and still wins the theme -- but the icon under it is now the summer one, because
        // the icon asks the calendar which season covers the day rather than remembering that
        // Halloween "is autumn". This is the case a lookup table would get wrong.
        val stretched = SeasonalCalendar.DEFAULT
            .withSpan(CalendarWindow.SUMMER, CalendarSpan(6, 1, 11, 30))
            .withSpan(CalendarWindow.AUTUMN, CalendarSpan(11, 1, 11, 30))
        assertEquals(CalendarWindow.HALLOWEEN, SeasonalThemeRules.windowForDate(LocalDate.of(2026, 10, 31), stretched))
        assertEquals(SeasonalIcon.SUMMER, iconOn(10, 31, stretched))
    }

    @Test
    fun `moving winter moves the icon with it`() {
        // The promise made to the user on the Seasons screen: the dates they set are the dates
        // the app uses, icon included.
        val early = SeasonalCalendar.DEFAULT.withSpan(CalendarWindow.WINTER, CalendarSpan(11, 1, 2, 29))
        assertEquals(SeasonalIcon.AUTUMN, iconOn(11, 15))
        assertEquals(SeasonalIcon.WINTER, iconOn(11, 15, early))
    }

    // --- the edges -----------------------------------------------------------------------------

    @Test
    fun `a gap in the calendar shows the icon that belongs to no season`() {
        // A user can drag a season off part of the year. Nothing is in season then, and the
        // answer is the neutral icon rather than whatever happened to be on the home screen.
        val holed = SeasonalCalendar.DEFAULT.withSpan(CalendarWindow.SUMMER, CalendarSpan(6, 1, 7, 31))
        assertEquals(SeasonalIcon.DEFAULT, iconOn(8, 15, holed))
        assertEquals(SeasonalIcon.SUMMER, iconOn(8, 15))
    }

    @Test
    fun `every day of the year resolves to a seasonal icon on the factory calendar`() {
        // The factory seasons partition the year (SeasonalCalendarIdentityTest owns that claim);
        // the consequence for the icon is that DEFAULT is never reached, on any of 366 days.
        for (md in CalendarSpan.ALL_DAYS) {
            val date = LocalDate.of(2024, md.monthValue, md.dayOfMonth) // 2024 is a leap year
            val icon = SeasonalIconRules.iconForDate(date)
            assertTrue("$date resolved to $icon", icon != SeasonalIcon.DEFAULT)
        }
    }

    @Test
    fun `the five seasonal icons are all reachable`() {
        val seen = CalendarSpan.ALL_DAYS
            .map { SeasonalIconRules.iconForDate(LocalDate.of(2024, it.monthValue, it.dayOfMonth)) }
            .toSet()
        assertEquals(SeasonalIcon.entries.toSet() - SeasonalIcon.DEFAULT, seen)
    }

    // --- the wiring between enum and calendar -------------------------------------------------

    @Test
    fun `every window either has an icon or has a season underneath it every day it covers`() {
        // Said the other way round: no window may be reachable and iconless without a season, or
        // some date would show DEFAULT for no reason a user could explain.
        for (window in CalendarWindow.entries) {
            if (SeasonalIcon.forWindow(window) != null) continue
            assertTrue(
                "$window has no icon, so it must be an occasion that sits on a season",
                window.tier == CalendarTier.OCCASION,
            )
        }
        for (season in CalendarWindow.SEASONS) {
            assertNotNull("season $season must have an icon of its own", SeasonalIcon.forWindow(season))
        }
    }

    @Test
    fun `alias class names are distinct and live under the app package`() {
        val names = SeasonalIcon.entries.map { it.aliasClassName }
        assertEquals(names.size, names.toSet().size)
        for (name in names) {
            assertTrue(name, name.startsWith("com.paperscrape.livewallpaper.ui."))
        }
    }

    @Test
    fun `exactly one icon is the one the manifest enables`() {
        assertEquals(listOf(SeasonalIcon.DEFAULT), SeasonalIcon.entries.filter { it.enabledInManifest })
    }
}
