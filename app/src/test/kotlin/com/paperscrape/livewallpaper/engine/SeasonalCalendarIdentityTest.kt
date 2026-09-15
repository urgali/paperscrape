package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit

/**
 * The gate for v5.1's calendar rewrite: **the factory calendar answers what v5.0 answered**.
 *
 * The dates moved out of the lambdas that tested them and into [SeasonalCalendar], and the seasons
 * were extended from five windows covering 296 days to four covering all 366. Neither of those is
 * supposed to change what a user sees. This file is what makes that a measurement rather than a
 * claim: it reimplements the **shipped v5.0 table** independently, below, and walks both calendars
 * day by day over fifteen years.
 *
 * There is exactly one permitted difference, and it is stated as a constant rather than tolerated
 * as a range: **1 March**. v5.0's winter ran 8 January to 1 March and its spring began on the 2nd;
 * the meteorological boundary puts spring on the 1st. Every other day of every year agrees.
 *
 * ### Why the old table is copied in here instead of being imported
 *
 * Because importing it is not available — it was deleted, which is the point. A test that compared
 * the new implementation against itself would pass for any implementation. The literals below are
 * transcribed from the v5.0 source and are **not** to be updated when the factory calendar changes:
 * if a future release moves a boundary, this test is supposed to fail and the new difference is
 * supposed to be argued in the release, not absorbed.
 */
class SeasonalCalendarIdentityTest {

    // --- v5.0's table, transcribed ------------------------------------------------------------

    private fun betweenV50(date: LocalDate, from: MonthDay, to: MonthDay): Boolean {
        val md = MonthDay.from(date)
        return if (from <= to) md >= from && md <= to else md >= from || md <= to
    }

    /** Exactly the five `seasons` entries v5.0 shipped, autumn twice included. */
    private val v50Seasons = listOf(
        "winter" to (MonthDay.of(1, 8) to MonthDay.of(3, 1)),
        "spring" to (MonthDay.of(3, 2) to MonthDay.of(5, 31)),
        "beach" to (MonthDay.of(6, 1) to MonthDay.of(8, 31)),
        "autumn" to (MonthDay.of(9, 1) to MonthDay.of(9, 30)),
        "autumn" to (MonthDay.of(11, 1) to MonthDay.of(11, 30)),
    )

    private val v50Occasions = listOf(
        "halloween" to (MonthDay.of(10, 1) to MonthDay.of(10, 31)),
        "christmas" to (MonthDay.of(12, 1) to MonthDay.of(12, 26)),
        "new_year" to (MonthDay.of(12, 27) to MonthDay.of(1, 7)),
    )

    private fun v50ThemeFor(date: LocalDate): String? {
        val fromEaster = ChronoUnit.DAYS.between(SeasonalThemeRules.computeEasterSunday(date.year), date)
        if (fromEaster in -2..1) return "easter"
        v50Occasions.firstOrNull { betweenV50(date, it.second.first, it.second.second) }?.let { return it.first }
        return v50Seasons.firstOrNull { betweenV50(date, it.second.first, it.second.second) }?.first
    }

    // --- The gate ------------------------------------------------------------------------------

    @Test
    fun `the factory calendar answers exactly what v5_0 answered, except on the first of march`() {
        val differences = ArrayList<Triple<LocalDate, String?, String?>>()
        for (year in 2026..2040) {
            var date = LocalDate.of(year, 1, 1)
            while (date.year == year) {
                val was = v50ThemeFor(date)
                val now = SeasonalThemeRules.themeForDate(date, SeasonalCalendar.DEFAULT)
                if (was != now) differences += Triple(date, was, now)
                date = date.plusDays(1)
            }
        }
        val unexpected = differences.filterNot { it.first.monthValue == 3 && it.first.dayOfMonth == 1 }
        assertTrue(
            "the rewrite moved days other than 1 March: " +
                unexpected.take(10).joinToString { "${it.first} ${it.second}->${it.third}" },
            unexpected.isEmpty(),
        )
        // And the permitted one really is there, in every year, and really is that change.
        assertEquals("1 March should differ in all fifteen years", 15, differences.size)
        for ((date, was, now) in differences) {
            assertEquals("$date was", "winter", was)
            assertEquals("$date is", "spring", now)
        }
    }

    @Test
    fun `the continuous autumn behaves exactly like v5_0's split autumn`() {
        // The claim the rewrite rests on: autumn can run 1 September to 30 November in one piece
        // because Halloween is checked first and takes October back. Plausible is not measured --
        // this walks every day of the year and compares the two shapes with everything else equal.
        var octoberDays = 0
        for (year in 2026..2035) {
            var date = LocalDate.of(year, 1, 1)
            while (date.year == year) {
                val continuous = SeasonalThemeRules.themeForDate(date, SeasonalCalendar.DEFAULT)
                val split = splitAutumnShape(date)
                assertEquals("$date disagrees between split and continuous autumn", split, continuous)
                if (date.monthValue == 10) octoberDays++
                date = date.plusDays(1)
            }
        }
        // October is the whole question -- it is the only stretch where the two shapes disagree
        // about what the *season* is -- so assert the walk actually went through it rather than
        // trusting that it did.
        assertEquals("the walk did not cover October", 310, octoberDays)
    }

    /**
     * v5.0's **split** autumn with v5.1's other seasons, isolating the one variable.
     *
     * October is deliberately absent from the season list, exactly as v5.0 had it: the split shape
     * has no season there at all. If the two shapes agree anyway, it is because Halloween is
     * checked first — which is the claim.
     */
    private fun splitAutumnShape(date: LocalDate): String? {
        val fromEaster = ChronoUnit.DAYS.between(SeasonalThemeRules.computeEasterSunday(date.year), date)
        if (fromEaster in -2..1) return "easter"
        v50Occasions.firstOrNull { betweenV50(date, it.second.first, it.second.second) }?.let { return it.first }
        val seasons = listOf(
            "winter" to (MonthDay.of(12, 1) to MonthDay.of(2, 29)),
            "spring" to (MonthDay.of(3, 1) to MonthDay.of(5, 31)),
            "beach" to (MonthDay.of(6, 1) to MonthDay.of(8, 31)),
            "autumn" to (MonthDay.of(9, 1) to MonthDay.of(9, 30)),
            "autumn" to (MonthDay.of(11, 1) to MonthDay.of(11, 30)),
        )
        return seasons.firstOrNull { betweenV50(date, it.second.first, it.second.second) }?.first
    }

    // --- The ribbon ----------------------------------------------------------------------------

    @Test
    fun `the four seasons cover every day of the year exactly once`() {
        val coverage = SeasonalCalendar.DEFAULT.coverage()
        assertTrue("uncovered: ${coverage.uncoveredRuns}", coverage.uncoveredDays.isEmpty())
        assertTrue("overlapping: ${coverage.conflicts.take(5)}", coverage.conflicts.isEmpty())
        assertEquals("29 February included", 366, CalendarSpan.ALL_DAYS.size)
    }

    @Test
    fun `every real date in a common year and a leap year has a season under it`() {
        // coverage() walks month-days; this walks actual dates, so a leap year is exercised as a
        // year rather than as one extra entry in a list.
        for (year in listOf(2027, 2028)) {
            var date = LocalDate.of(year, 1, 1)
            while (date.year == year) {
                val md = MonthDay.from(date)
                val seasons = CalendarWindow.SEASONS.filter { md in SeasonalCalendar.DEFAULT.spanFor(it) }
                assertEquals("$date is covered by ${seasons.map { it.label }}", 1, seasons.size)
                date = date.plusDays(1)
            }
        }
    }

    @Test
    fun `v5_0's seasons left sixty-nine days with nothing under them`() {
        // The measurement that justified extending them. Kept as a test so the next person to
        // wonder why the seasons are shaped this way gets the number rather than the intention.
        var uncovered = 0
        var date = LocalDate.of(2027, 1, 1)
        while (date.year == 2027) {
            if (v50Seasons.none { betweenV50(date, it.second.first, it.second.second) }) uncovered++
            date = date.plusDays(1)
        }
        assertEquals(69, uncovered)
    }

    @Test
    fun `no occasion window is left without a season beneath it`() {
        // The property the extension bought: shortening an occasion can no longer expose a hole,
        // because every day an occasion covers also belongs to a season.
        for (window in CalendarWindow.OCCASIONS.filter { !it.isComputed }) {
            val span = SeasonalCalendar.DEFAULT.spanFor(window)
            for (day in CalendarSpan.ALL_DAYS.filter { it in span }) {
                val seasons = CalendarWindow.SEASONS.filter { day in SeasonalCalendar.DEFAULT.spanFor(it) }
                assertEquals("$day under ${window.label} has $seasons", 1, seasons.size)
            }
        }
    }
}
