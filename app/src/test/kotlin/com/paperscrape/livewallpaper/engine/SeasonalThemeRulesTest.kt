package com.paperscrape.livewallpaper.engine

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [SeasonalThemeRules].
 *
 * Two distinct concerns are covered separately on purpose:
 *
 *  - the Computus (Easter date) implementation, checked against published Easter Sunday dates;
 *  - the window matching table, checked for boundaries, ordering and gaps.
 *
 * Testing Computus only through [SeasonalThemeRules.themeForDate] would hide an off-by-one,
 * because the Easter window spans three days either side of Easter Sunday and would still match.
 */
class SeasonalThemeRulesTest {

    // --- Computus ---------------------------------------------------------------------------

    /**
     * Published Gregorian Easter Sunday dates. Chosen to span the full possible range
     * (22 March to 25 April), several centuries, and both the earliest and latest cases
     * available in a reasonable window.
     */
    private val knownEasterSundays = mapOf(
        1900 to LocalDate.of(1900, 4, 15),
        1918 to LocalDate.of(1918, 3, 31),
        1943 to LocalDate.of(1943, 4, 25), // latest possible date
        1961 to LocalDate.of(1961, 4, 2),
        1980 to LocalDate.of(1980, 4, 6),
        1999 to LocalDate.of(1999, 4, 4),
        2000 to LocalDate.of(2000, 4, 23),
        2005 to LocalDate.of(2005, 3, 27),
        2008 to LocalDate.of(2008, 3, 23), // near-earliest date
        2011 to LocalDate.of(2011, 4, 24),
        2016 to LocalDate.of(2016, 3, 27),
        2018 to LocalDate.of(2018, 4, 1),
        2019 to LocalDate.of(2019, 4, 21),
        2020 to LocalDate.of(2020, 4, 12),
        2021 to LocalDate.of(2021, 4, 4),
        2022 to LocalDate.of(2022, 4, 17),
        2023 to LocalDate.of(2023, 4, 9),
        2024 to LocalDate.of(2024, 3, 31),
        2025 to LocalDate.of(2025, 4, 20),
        2026 to LocalDate.of(2026, 4, 5),
        2027 to LocalDate.of(2027, 3, 28),
        2028 to LocalDate.of(2028, 4, 16),
        2029 to LocalDate.of(2029, 4, 1),
        2030 to LocalDate.of(2030, 4, 21),
        2038 to LocalDate.of(2038, 4, 25), // latest possible date again
        2049 to LocalDate.of(2049, 4, 18),
        2050 to LocalDate.of(2050, 4, 10),
        2100 to LocalDate.of(2100, 3, 28),
    )


    /*
     * **The window tests that used to live here are gone, not disabled.** v2.5 replaced the
     * calendar wholesale -- every date resolves now, the December windows no longer overlap, and
     * Easter is the long weekend rather than a week either side -- so assertions pinning the old
     * windows were describing a calendar that no longer exists. `SeasonalCalendarTest` covers the
     * new one boundary by boundary, on both sides of each. What stays here is the Computus
     * arithmetic and the invariants that hold whatever the windows are.
     */

    @Test
    fun `computus matches published Easter Sunday dates`() {
        for ((year, expected) in knownEasterSundays) {
            assertEquals(
                "Easter Sunday wrong for $year",
                expected,
                SeasonalThemeRules.computeEasterSunday(year),
            )
        }
    }

    @Test
    fun `computus always lands on a Sunday`() {
        for (year in 1900..2200) {
            val easter = SeasonalThemeRules.computeEasterSunday(year)
            assertEquals(
                "Easter $year fell on ${easter.dayOfWeek}",
                java.time.DayOfWeek.SUNDAY,
                easter.dayOfWeek,
            )
        }
    }

    @Test
    fun `computus always lands between 22 March and 25 April`() {
        for (year in 1900..2200) {
            val easter = SeasonalThemeRules.computeEasterSunday(year)
            val earliest = LocalDate.of(year, 3, 22)
            val latest = LocalDate.of(year, 4, 25)
            assertEquals(
                "Easter $year ($easter) outside the canonical range",
                true,
                !easter.isBefore(earliest) && !easter.isAfter(latest),
            )
        }
    }

    // --- Window matching --------------------------------------------------------------------

    
    
    
    
    
    
    @Test
    fun `every returned theme id exists in the theme catalog`() {
        // Guards against a rule pointing at an id that was renamed or removed from ThemeCatalog,
        // which would silently fall back to a default theme at runtime rather than failing.
        val catalogIds = ThemeCatalog.ALL.map { it.id }.toSet()
        var date = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2026, 12, 31)
        while (!date.isAfter(end)) {
            val themeId = SeasonalThemeRules.themeForDate(date)
            if (themeId != null) {
                assertEquals(
                    "themeForDate($date) returned '$themeId', which is not in ThemeCatalog",
                    true,
                    catalogIds.contains(themeId),
                )
            }
            date = date.plusDays(1)
        }
    }

    @Test
    fun `label is present exactly when a theme is present`() {
        var date = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2026, 12, 31)
        while (!date.isAfter(end)) {
            val themeId = SeasonalThemeRules.themeForDate(date)
            val label = SeasonalThemeRules.labelForDate(date)
            if (themeId == null) {
                assertNull("label should be null when no theme matches on $date", label)
            } else {
                assertNotNull("label should be present when a theme matches on $date", label)
            }
            date = date.plusDays(1)
        }
    }

    @Test
    fun `rules never throw for any date across many years`() {
        // Leap days, century boundaries and the year-wrapping windows all in one sweep.
        var date = LocalDate.of(2020, 1, 1)
        val end = LocalDate.of(2035, 12, 31)
        while (!date.isAfter(end)) {
            SeasonalThemeRules.themeForDate(date)
            SeasonalThemeRules.labelForDate(date)
            date = date.plusDays(1)
        }
    }
}
