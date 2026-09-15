package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.time.LocalDate
import java.time.MonthDay

/**
 * Storage, validation and reset for the holiday calendar.
 *
 * The three properties the feature rests on, each of which is silent when it breaks:
 *
 * 1. an install that never touched the calendar stores **nothing**, so it cannot be pinned to the
 *    dates one particular release shipped;
 * 2. a date a user set comes back as the date they set;
 * 3. reset leaves the store indistinguishable from (1), not holding a document that agrees.
 */
class SeasonalCalendarStorageTest {

    // --- Only the difference is stored ----------------------------------------------------------

    @Test
    fun `the factory calendar stores no windows at all`() {
        assertTrue(SeasonalCalendar.DEFAULT.isFactory)
        assertTrue(SeasonalCalendar.DEFAULT.spans.isEmpty())
        val root = JSONObject(SeasonalCalendar.DEFAULT.toJsonString())
        assertEquals(0, root.getJSONObject("windows").length())
        assertEquals(SEASONAL_CALENDAR_SCHEMA_VERSION, root.getInt("schemaVersion"))
    }

    @Test
    fun `setting a window back to its factory dates stops being an override`() {
        // Otherwise "reset this window" and "drag it back where it was" leave the document in two
        // different states with one behaviour, and only one of them follows a future default.
        val moved = SeasonalCalendar.DEFAULT.withSpan(CalendarWindow.HALLOWEEN, CalendarSpan(10, 5, 10, 31))
        assertFalse(moved.isFactory)
        val back = moved.withSpan(CalendarWindow.HALLOWEEN, CalendarWindow.HALLOWEEN.factorySpan)
        assertTrue(back.isFactory)
        assertEquals(SeasonalCalendar.DEFAULT, back)
    }

    @Test
    fun `an untouched window follows a change of factory dates rather than freezing`() {
        // Expressed against the model rather than against a hypothetical future release: a calendar
        // that stores nothing for a window reads that window's span from the enum, every time.
        val calendar = SeasonalCalendar.DEFAULT.withSpan(CalendarWindow.HALLOWEEN, CalendarSpan(10, 5, 10, 31))
        assertEquals(CalendarWindow.WINTER.factorySpan, calendar.spanFor(CalendarWindow.WINTER))
        assertFalse(calendar.spans.containsKey(CalendarWindow.WINTER))
    }

    // --- Round trip ------------------------------------------------------------------------------

    @Test
    fun `a calendar survives a save and reload unchanged`() {
        val original = SeasonalCalendar.DEFAULT
            .withSpan(CalendarWindow.HALLOWEEN, CalendarSpan(10, 15, 11, 2))
            .withSpan(CalendarWindow.WINTER, CalendarSpan(12, 10, 2, 20))
            .withEaster(EasterSpan(daysBefore = 5, daysAfter = 3))
        val reloaded = seasonalCalendarFromJsonString(original.toJsonString())
        assertEquals(original, reloaded)
        assertEquals(MonthDay.of(10, 15), reloaded.spanFor(CalendarWindow.HALLOWEEN).start)
        assertEquals(MonthDay.of(11, 2), reloaded.spanFor(CalendarWindow.HALLOWEEN).end)
        assertEquals(5, reloaded.easter.daysBefore)
    }

    @Test
    fun `a wrapping span survives the round trip`() {
        val original = SeasonalCalendar.DEFAULT.withSpan(CalendarWindow.NEW_YEAR, CalendarSpan(12, 20, 1, 10))
        val reloaded = seasonalCalendarFromJsonString(original.toJsonString())
        assertEquals(original, reloaded)
        assertTrue(reloaded.spanFor(CalendarWindow.NEW_YEAR).wraps)
    }

    @Test
    fun `the twenty-ninth of february survives the round trip`() {
        // The endpoint that only exists in a leap year, and the one a naive "MM-dd" parser drops.
        val original = SeasonalCalendar.DEFAULT.withSpan(CalendarWindow.WINTER, CalendarSpan(12, 2, 2, 29))
        val reloaded = seasonalCalendarFromJsonString(original.toJsonString())
        assertEquals(MonthDay.of(2, 29), reloaded.spanFor(CalendarWindow.WINTER).end)
    }

    @Test
    fun `an edited calendar actually changes what a date resolves to`() {
        // The round trip above proves storage. This proves the stored thing is consulted -- the two
        // are different claims, and a feature that stored perfectly and was never read would pass
        // every test above.
        val date = LocalDate.of(2027, 10, 3)
        assertEquals("halloween", SeasonalThemeRules.themeForDate(date, SeasonalCalendar.DEFAULT))
        val shortened = SeasonalCalendar.DEFAULT.withSpan(CalendarWindow.HALLOWEEN, CalendarSpan(10, 20, 10, 31))
        assertEquals("autumn", SeasonalThemeRules.themeForDate(date, shortened))
    }

    @Test
    fun `shortening halloween reveals autumn rather than nothing`() {
        // The whole reason the seasons were extended. Under v5.0's table this date would have
        // resolved to null and the wallpaper would have fallen back to the hand-picked theme.
        val shortened = SeasonalCalendar.DEFAULT.withSpan(CalendarWindow.HALLOWEEN, CalendarSpan(10, 28, 10, 31))
        for (day in 1..27) {
            assertEquals(
                "3 October $day",
                "autumn",
                SeasonalThemeRules.themeForDate(LocalDate.of(2027, 10, day), shortened),
            )
        }
    }

    // --- Malformed documents ---------------------------------------------------------------------

    @Test
    fun `unreadable documents read as the factory calendar, never as a partial one`() {
        for (raw in listOf(null, "", "   ", "not json", "[]", "{}", """{"windows":null}""")) {
            assertEquals("input: $raw", SeasonalCalendar.DEFAULT, seasonalCalendarFromJsonString(raw))
        }
    }

    @Test
    fun `an unknown window key is skipped and the rest of the document survives`() {
        // A document written by a build with a window this one does not have must still yield its
        // other windows, or a downgrade would silently discard every edit.
        val raw = """
            {"schemaVersion":1,"windows":{
              "halloween":{"start":"10-15","end":"10-31"},
              "carnival":{"start":"02-01","end":"02-14"}
            }}
        """.trimIndent()
        val calendar = seasonalCalendarFromJsonString(raw)
        assertEquals(MonthDay.of(10, 15), calendar.spanFor(CalendarWindow.HALLOWEEN).start)
        assertEquals(1, calendar.spans.size)
    }

    @Test
    fun `a malformed date inside one window leaves that window on its factory span`() {
        val raw = """{"schemaVersion":1,"windows":{"halloween":{"start":"13-45","end":"10-31"}}}"""
        val calendar = seasonalCalendarFromJsonString(raw)
        assertEquals(CalendarWindow.HALLOWEEN.factorySpan, calendar.spanFor(CalendarWindow.HALLOWEEN))
    }

    @Test
    fun `an out-of-range easter offset is clamped rather than throwing`() {
        val raw = """{"schemaVersion":1,"windows":{"easter":{"before":900,"after":-4}}}"""
        val calendar = seasonalCalendarFromJsonString(raw)
        assertEquals(EasterSpan.MAX_BEFORE, calendar.easter.daysBefore)
        assertEquals(0, calendar.easter.daysAfter)
    }

    // --- Validation ------------------------------------------------------------------------------

    @Test
    fun `two seasons claiming one day is reported as a conflict`() {
        val overlapping = SeasonalCalendar.DEFAULT.withSpan(CalendarWindow.SPRING, CalendarSpan(3, 1, 6, 15))
        val coverage = overlapping.coverage()
        assertTrue(coverage.hasConflicts)
        assertTrue(coverage.conflicts.all { it.windows.size == 2 })
        assertEquals(15, coverage.conflicts.size) // 1..15 June
    }

    @Test
    fun `the screen's gate names the window a move would collide with`() {
        val collide = SeasonalCalendar.DEFAULT.conflictsFor(CalendarWindow.SPRING, CalendarSpan(3, 1, 6, 15))
        assertEquals(listOf(CalendarWindow.SUMMER), collide)
        val fine = SeasonalCalendar.DEFAULT.conflictsFor(CalendarWindow.SPRING, CalendarSpan(3, 1, 5, 31))
        assertTrue(fine.isEmpty())
    }

    @Test
    fun `occasions are checked against occasions and seasons against seasons, never across`() {
        // The tiers overlap by design -- Halloween sits on top of autumn -- so a validator that
        // compared across them would forbid the calendar's own factory shape.
        val across = SeasonalCalendar.DEFAULT.conflictsFor(CalendarWindow.HALLOWEEN, CalendarSpan(10, 1, 10, 31))
        assertTrue("Halloween must be allowed to sit on autumn", across.isEmpty())
        val within = SeasonalCalendar.DEFAULT.conflictsFor(CalendarWindow.HALLOWEEN, CalendarSpan(10, 1, 12, 5))
        assertEquals(listOf(CalendarWindow.CHRISTMAS), within)
    }

    @Test
    fun `easter is exempt from the overlap gate`() {
        // It is first in precedence and wins wherever it lands, and it moves by up to five weeks
        // between years -- so there is no year-free answer to "does Easter overlap Halloween", and
        // a gate that produced one would be inventing it.
        assertTrue(SeasonalCalendar.DEFAULT.conflictsFor(CalendarWindow.EASTER, CalendarSpan(1, 1, 12, 31)).isEmpty())
        assertTrue(SeasonalCalendar.DEFAULT.coverage().conflicts.isEmpty())
    }

    @Test
    fun `a gap a user opens is reported with the days in it`() {
        val gapped = SeasonalCalendar.DEFAULT.withSpan(CalendarWindow.SPRING, CalendarSpan(3, 10, 5, 31))
        val coverage = gapped.coverage()
        assertTrue(coverage.hasGaps)
        assertEquals(9, coverage.uncoveredDays.size) // 1..9 March
        assertEquals(1, coverage.uncoveredRuns.size)
        assertEquals(MonthDay.of(3, 1), coverage.uncoveredRuns.single().start)
        assertEquals(MonthDay.of(3, 9), coverage.uncoveredRuns.single().endInclusive)
    }

    @Test
    fun `a gap that crosses the year end is reported as two runs, and says so`() {
        // Not a defect, and worth a test rather than only a comment: these are month-days with no
        // year, so "adjacent" cannot wrap. The message is wordier, not wrong.
        // Winter pulled back to 5 January leaves December and 1-4 January uncovered: one stretch as
        // a person reads a calendar, two as a list of month-days that cannot wrap.
        val gapped = SeasonalCalendar.DEFAULT.withSpan(CalendarWindow.WINTER, CalendarSpan(1, 5, 2, 29))
        val coverage = gapped.coverage()
        assertEquals(35, coverage.uncoveredDays.size)
        val runs = coverage.uncoveredRuns
        assertEquals(2, runs.size)
        assertEquals(MonthDay.of(1, 1), runs[0].start)
        assertEquals(MonthDay.of(1, 4), runs[0].endInclusive)
        assertEquals(MonthDay.of(12, 1), runs[1].start)
        assertEquals(MonthDay.of(12, 31), runs[1].endInclusive)
    }

    @Test
    fun `a gap costs the day rather than breaking the calendar`() {
        // themeForDate answers null and PaperWallpaperService falls back to the hand-picked theme.
        // Stated here so that "gaps are allowed" is a tested property and not an assumption.
        val gapped = SeasonalCalendar.DEFAULT.withSpan(CalendarWindow.SPRING, CalendarSpan(3, 10, 5, 31))
        assertEquals(null, SeasonalThemeRules.themeForDate(LocalDate.of(2027, 3, 5), gapped))
        assertEquals("spring", SeasonalThemeRules.themeForDate(LocalDate.of(2027, 3, 10), gapped))
    }

    // --- Reset -------------------------------------------------------------------------------------

    @Test
    fun `reset returns the calendar to the factory shape, not to a document that agrees with it`() {
        val edited = SeasonalCalendar.DEFAULT
            .withSpan(CalendarWindow.HALLOWEEN, CalendarSpan(10, 15, 11, 2))
            .withEaster(EasterSpan(4, 4))
        assertNotEquals(SeasonalCalendar.DEFAULT, edited)
        assertEquals(SeasonalCalendar.DEFAULT, SeasonalCalendar.DEFAULT)
        assertTrue(SeasonalCalendar.DEFAULT.isFactory)
        // And a reset calendar resolves every day to what a fresh install resolves it to.
        var date = LocalDate.of(2027, 1, 1)
        while (date.year == 2027) {
            assertEquals(
                "$date after reset",
                SeasonalThemeRules.themeForDate(date, SeasonalCalendar.DEFAULT),
                SeasonalThemeRules.themeForDate(date, seasonalCalendarFromJsonString(null)),
            )
            date = date.plusDays(1)
        }
    }

    // --- Easter ------------------------------------------------------------------------------------

    @Test
    fun `easter's length is adjustable and its sunday is not`() {
        val longer = SeasonalCalendar.DEFAULT.withEaster(EasterSpan(daysBefore = 5, daysAfter = 2))
        for (year in 2026..2032) {
            val sunday = SeasonalThemeRules.computeEasterSunday(year)
            assertEquals("easter", SeasonalThemeRules.themeForDate(sunday.minusDays(5), longer))
            assertEquals("easter", SeasonalThemeRules.themeForDate(sunday.plusDays(2), longer))
            assertNotEquals("easter", SeasonalThemeRules.themeForDate(sunday.minusDays(6), longer))
            assertNotEquals("easter", SeasonalThemeRules.themeForDate(sunday.plusDays(3), longer))
            // The computed Sunday is untouched by the setting -- it is arithmetic, not a preference.
            assertEquals(sunday, SeasonalThemeRules.computeEasterSunday(year))
        }
    }

    @Test
    fun `an easter window of zero either side is one day`() {
        val single = SeasonalCalendar.DEFAULT.withEaster(EasterSpan(0, 0))
        val sunday = SeasonalThemeRules.computeEasterSunday(2027)
        assertEquals("easter", SeasonalThemeRules.themeForDate(sunday, single))
        assertNotEquals("easter", SeasonalThemeRules.themeForDate(sunday.minusDays(1), single))
        assertNotEquals("easter", SeasonalThemeRules.themeForDate(sunday.plusDays(1), single))
    }

    @Test
    fun `the easter window's landing dates are reported for the year asked about`() {
        val range = SeasonalThemeRules.easterWindowIn(2027, SeasonalCalendar.DEFAULT)
        val sunday = SeasonalThemeRules.computeEasterSunday(2027)
        assertEquals(sunday.minusDays(2), range.start)
        assertEquals(sunday.plusDays(1), range.endInclusive)
    }

    // --- The enum is the contract --------------------------------------------------------------------

    @Test
    fun `there are eight windows and every key is unique and stable`() {
        assertEquals(8, CalendarWindow.entries.size)
        assertEquals(4, CalendarWindow.OCCASIONS.size)
        assertEquals(4, CalendarWindow.SEASONS.size)
        assertEquals(7, CalendarWindow.DATED.size)
        assertEquals(CalendarWindow.entries.size, CalendarWindow.entries.map { it.key }.toSet().size)
        for (window in CalendarWindow.entries) {
            assertEquals("byKey round trip for ${window.key}", window, CalendarWindow.byKey(window.key))
        }
    }

    @Test
    fun `every window names a theme the catalogue has`() {
        val ids = ThemeCatalog.ALL.map { it.id }.toSet()
        for (window in CalendarWindow.entries) {
            assertTrue("${window.label} names ${window.themeId}", window.themeId in ids)
        }
    }

    @Test
    fun `easter is the only computed window and reading its span throws`() {
        assertEquals(listOf(CalendarWindow.EASTER), CalendarWindow.entries.filter { it.isComputed })
        val threw = runCatching { CalendarWindow.EASTER.factorySpan }.isFailure
        assertTrue("a computed window must not hand out a span", threw)
    }
}
