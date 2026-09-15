package com.paperscrape.livewallpaper.engine

import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit

/**
 * Maps a date to a themeId, for the optional "automatic theme by date" setting.
 *
 * Rules resolve to a plain [String] themeId — the same string [WallpaperPrefs] stores,
 * [ThemeCatalog.byId] resolves and [SceneObjectCatalog.layoutFor] lays out. Nothing here is
 * hardcoded to the built-in set, so a custom theme could be scheduled the same way.
 *
 * ### The dates are data, and the user owns them
 *
 * Every window's dates used to live inside the lambda that tested them, which is why they could
 * not be shown, stored or changed. They are [SeasonalCalendar] now — a small document the user
 * edits on the Holiday calendar screen — and this object is the thing that reads it. What the user
 * may move is **only the dates**: [CalendarWindow] decides which windows exist, what they are
 * called, which theme each picks and the order they are checked in, and none of that is a
 * preference.
 *
 * ### The seasons are a continuous ribbon
 *
 * It used to cover four windows and return `null` for the rest, leaving the caller on whatever the
 * user last picked by hand. That made "automatic" mean "automatic in December, at Easter and over
 * the summer", which is not a setting anybody can predict the behaviour of. **Every date now
 * resolves.**
 *
 * v5.1 made that true of the seasons themselves rather than of the table as a whole. The shipped
 * seasons covered 296 days and left 69 — all of October, all of December, 1–7 January — with no
 * season under them at all; every one of those days happened to be inside an occasion, so the
 * calendar looked complete while resting on the occasions to be so. The moment a user shortened
 * Halloween, October would have had nothing underneath. The four seasons now partition the year on
 * meteorological boundaries, and an occasion passes *over* a season rather than standing in for one.
 *
 * ### Precedence is a list, not an accident
 *
 * The old table relied on ordering alone, with a comment asking the next editor to keep narrow
 * windows above broad ones — and its two December windows overlapped in a way that made Christmas
 * unreachable on 30 and 31 December. Occasions are a separate ordered tier checked before the
 * seasons, and the seasons partition what is left and cannot overlap each other:
 *
 * 1. **Easter** — moves every year, so it wins wherever it lands.
 * 2. **Halloween**
 * 3. **Christmas**
 * 4. **New Year**
 * 5. **Winter / Spring / Summer / Autumn** — the seasonal floor.
 *
 * Putting Easter above Halloween and Christmas is a statement of intent rather than of dates: the
 * three cannot collide in the Gregorian calendar, and stating the order anyway means the answer
 * does not depend on that continuing to be true of the arithmetic.
 */
object SeasonalThemeRules {

    /** Between [from] and [to] inclusive on the month-and-day, wrapping across the year end. */
    private fun between(date: LocalDate, from: MonthDay, to: MonthDay): Boolean {
        val md = MonthDay.from(date)
        return if (from <= to) md >= from && md <= to else md >= from || md <= to
    }

    private fun matches(date: LocalDate, window: CalendarWindow, calendar: SeasonalCalendar): Boolean =
        if (window.isComputed) {
            // Easter is the one window with no dates: it has a computed Sunday and two lengths.
            val daysFromEaster = ChronoUnit.DAYS.between(computeEasterSunday(date.year), date)
            calendar.easter.covers(daysFromEaster)
        } else {
            val span = calendar.spanFor(window)
            between(date, span.start, span.end)
        }

    private fun windowFor(date: LocalDate, calendar: SeasonalCalendar): CalendarWindow? =
        CalendarWindow.OCCASIONS.firstOrNull { matches(date, it, calendar) }
            ?: CalendarWindow.SEASONS.firstOrNull { matches(date, it, calendar) }

    /**
     * The themeId for [date], which is **the device's local date** by default.
     *
     * `LocalDate.now()` reads the default time zone, so the theme turns over at local midnight
     * rather than at some hour determined by an offset from UTC. The same local date always
     * produces the same theme: nothing here reads a clock time, a zone or anything else that could
     * make the answer depend on when within the day it was asked.
     *
     * `null` means no window covers [date]. With the factory calendar that cannot happen — the
     * seasons are a partition — but a user may move a season and open a gap, and the caller
     * already falls back to the theme they picked by hand.
     */
    fun themeForDate(
        date: LocalDate = LocalDate.now(),
        calendar: SeasonalCalendar = SeasonalCalendar.DEFAULT,
    ): String? = windowFor(date, calendar)?.themeId

    /** Same as [themeForDate], but the window's label, for the settings screen to display. */
    fun labelForDate(
        date: LocalDate = LocalDate.now(),
        calendar: SeasonalCalendar = SeasonalCalendar.DEFAULT,
    ): String? = windowFor(date, calendar)?.label

    /** The window covering [date], for a screen that needs more than its name. */
    fun windowForDate(
        date: LocalDate = LocalDate.now(),
        calendar: SeasonalCalendar = SeasonalCalendar.DEFAULT,
    ): CalendarWindow? = windowFor(date, calendar)

    /**
     * The dates Easter's window spans in [year], for display.
     *
     * The screen shows Easter as two lengths because that is what it is, but a user still wants to
     * know where it lands this year, and that is arithmetic rather than a stored date.
     */
    fun easterWindowIn(year: Int, calendar: SeasonalCalendar = SeasonalCalendar.DEFAULT): ClosedRange<LocalDate> {
        val sunday = computeEasterSunday(year)
        return sunday.minusDays(calendar.easter.daysBefore.toLong())..
            sunday.plusDays(calendar.easter.daysAfter.toLong())
    }

    /**
     * Anonymous Gregorian algorithm ("Computus") for Easter Sunday in a given year. Standard and
     * well known; not specific to any third party.
     *
     * `internal` rather than `private` purely so it can be tested directly against known Easter
     * dates. Testing it only through [themeForDate] would hide an off-by-one inside the window.
     */
    internal fun computeEasterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }
}
