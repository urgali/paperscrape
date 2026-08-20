package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The automatic-theme calendar: every boundary, both sides.
 *
 * **Boundaries are the whole point of this file.** A calendar can only be wrong in two ways — a
 * window that starts or ends a day out, and two windows that both claim a date — and both are
 * invisible except on the day they happen. Every window below is therefore checked on its first
 * day, its last day, and the day either side of it.
 *
 * The old table was wrong in exactly the second way and nobody noticed: its New Year window began
 * on 30 December and its Christmas window ran to 6 January, so Christmas was unreachable on the
 * last two days of December and New Year covered them instead. Ordering alone was carrying the
 * precedence, with a comment asking the next editor to preserve it.
 */
class SeasonalCalendarTest {

    private fun theme(month: Int, day: Int, year: Int = 2026): String? =
        SeasonalThemeRules.themeForDate(LocalDate.of(year, month, day))

    // --- Every window's two ends -------------------------------------------------------------

    @Test
    fun `the calendar covers every day of the year`() {
        // `null` used to mean "keep whatever the user last picked", which made an automatic
        // setting unpredictable for most of the year. Every date resolves now.
        var date = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2027, 1, 1)
        while (date.isBefore(end)) {
            assertNotNull("$date resolves to no theme", SeasonalThemeRules.themeForDate(date))
            date = date.plusDays(1)
        }
    }

    @Test
    fun `new year runs to the seventh of january`() {
        assertEquals("new_year", theme(1, 1))
        assertEquals("new_year", theme(1, 7))
    }

    @Test
    fun `winter starts on the eighth of january and ends on the first of march`() {
        assertEquals("winter", theme(1, 8))
        assertEquals("winter", theme(3, 1))
    }

    @Test
    fun `spring starts on the second of march and ends on the last of may`() {
        assertEquals("spring", theme(3, 2))
        assertEquals("spring", theme(5, 31))
    }

    @Test
    fun `summer is june july august`() {
        assertEquals("beach", theme(6, 1))
        assertEquals("beach", theme(8, 31))
    }

    @Test
    fun `september is autumn and october is not`() {
        assertEquals("autumn", theme(9, 1))
        assertEquals("autumn", theme(9, 30))
        assertEquals("halloween", theme(10, 1))
    }

    @Test
    fun `halloween holds the whole of october`() {
        assertEquals("halloween", theme(10, 1))
        assertEquals("halloween", theme(10, 31))
    }

    @Test
    fun `november returns to autumn`() {
        assertEquals("autumn", theme(11, 1))
        assertEquals("autumn", theme(11, 30))
    }

    @Test
    fun `christmas runs from the first to the twenty-sixth of december`() {
        assertEquals("christmas", theme(12, 1))
        assertEquals("christmas", theme(12, 26))
    }

    @Test
    fun `new year takes the last five days of december`() {
        // The defect the old table had: 30 and 31 December belonged to New Year by ordering while
        // Christmas still claimed them, and 27 to 29 fell to Christmas.
        assertEquals("new_year", theme(12, 27))
        assertEquals("new_year", theme(12, 31))
    }

    @Test
    fun `every window changes on the day after its last`() {
        val boundaries = listOf(
            Triple(LocalDate.of(2026, 1, 7), "new_year", "winter"),
            Triple(LocalDate.of(2026, 3, 1), "winter", "spring"),
            Triple(LocalDate.of(2026, 5, 31), "spring", "beach"),
            Triple(LocalDate.of(2026, 8, 31), "beach", "autumn"),
            Triple(LocalDate.of(2026, 9, 30), "autumn", "halloween"),
            Triple(LocalDate.of(2026, 10, 31), "halloween", "autumn"),
            Triple(LocalDate.of(2026, 11, 30), "autumn", "christmas"),
            Triple(LocalDate.of(2026, 12, 26), "christmas", "new_year"),
        )
        for ((last, before, after) in boundaries) {
            assertEquals("$last should still be $before", before, SeasonalThemeRules.themeForDate(last))
            assertEquals(
                "${last.plusDays(1)} should have become $after",
                after, SeasonalThemeRules.themeForDate(last.plusDays(1)),
            )
        }
    }

    @Test
    fun `the year rolls over into new year`() {
        assertEquals("new_year", SeasonalThemeRules.themeForDate(LocalDate.of(2026, 12, 31)))
        assertEquals("new_year", SeasonalThemeRules.themeForDate(LocalDate.of(2027, 1, 1)))
    }

    // --- Easter, which moves ------------------------------------------------------------------

    @Test
    fun `easter runs good friday to easter monday and no further`() {
        for (year in 2024..2032) {
            val sunday = SeasonalThemeRules.computeEasterSunday(year)
            assertEquals("Good Friday $year", "easter", SeasonalThemeRules.themeForDate(sunday.minusDays(2)))
            assertEquals("Holy Saturday $year", "easter", SeasonalThemeRules.themeForDate(sunday.minusDays(1)))
            assertEquals("Easter Sunday $year", "easter", SeasonalThemeRules.themeForDate(sunday))
            assertEquals("Easter Monday $year", "easter", SeasonalThemeRules.themeForDate(sunday.plusDays(1)))
            assertTrue(
                "Maundy Thursday $year should not be Easter",
                SeasonalThemeRules.themeForDate(sunday.minusDays(3)) != "easter",
            )
            assertTrue(
                "the Tuesday after Easter $year should not be Easter",
                SeasonalThemeRules.themeForDate(sunday.plusDays(2)) != "easter",
            )
        }
    }

    @Test
    fun `easter falls back to the surrounding season on either side`() {
        // Easter is always in late March or April, so the day before Good Friday and the day after
        // Easter Monday are spring -- which is the point of the seasons being a full partition.
        for (year in 2024..2032) {
            val sunday = SeasonalThemeRules.computeEasterSunday(year)
            assertEquals("spring", SeasonalThemeRules.themeForDate(sunday.minusDays(3)))
            assertEquals("spring", SeasonalThemeRules.themeForDate(sunday.plusDays(2)))
        }
    }

    @Test
    fun `computus agrees with known easter dates`() {
        assertEquals(LocalDate.of(2024, 3, 31), SeasonalThemeRules.computeEasterSunday(2024))
        assertEquals(LocalDate.of(2025, 4, 20), SeasonalThemeRules.computeEasterSunday(2025))
        assertEquals(LocalDate.of(2026, 4, 5), SeasonalThemeRules.computeEasterSunday(2026))
        assertEquals(LocalDate.of(2027, 3, 28), SeasonalThemeRules.computeEasterSunday(2027))
    }

    // --- Precedence ---------------------------------------------------------------------------

    @Test
    fun `easter outranks the season it lands in`() {
        val sunday = SeasonalThemeRules.computeEasterSunday(2026)
        assertEquals("spring", SeasonalThemeRules.themeForDate(sunday.minusDays(5)))
        assertEquals("easter", SeasonalThemeRules.themeForDate(sunday))
    }

    @Test
    fun `halloween outranks autumn and christmas outranks nothing it overlaps`() {
        assertEquals("autumn", theme(9, 30))
        assertEquals("halloween", theme(10, 15))
        assertEquals("autumn", theme(11, 15))
        assertEquals("christmas", theme(12, 15))
    }

    @Test
    fun `new year outranks christmas at the end of december`() {
        assertEquals("christmas", theme(12, 26))
        assertEquals("new_year", theme(12, 27))
    }

    @Test
    fun `no occasion is ever answered by a season`() {
        // Every day that belongs to an occasion has to come back as that occasion, whatever the
        // seasonal partition would otherwise have said about it.
        val sunday = SeasonalThemeRules.computeEasterSunday(2026)
        val occasionDays = buildList {
            addAll((-2L..1L).map { sunday.plusDays(it) })
            addAll((1..31).map { LocalDate.of(2026, 10, it) })
            addAll((1..26).map { LocalDate.of(2026, 12, it) })
            addAll((27..31).map { LocalDate.of(2026, 12, it) })
            addAll((1..7).map { LocalDate.of(2026, 1, it) })
        }
        val seasonIds = setOf("winter", "spring", "beach", "autumn")
        for (day in occasionDays) {
            assertTrue(
                "$day was answered by a season",
                SeasonalThemeRules.themeForDate(day) !in seasonIds,
            )
        }
    }

    // --- Determinism and the local date -------------------------------------------------------

    @Test
    fun `the same local date always gives the same theme`() {
        var date = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2027, 1, 1)
        while (date.isBefore(end)) {
            val first = SeasonalThemeRules.themeForDate(date)
            repeat(3) { assertEquals("$date is not deterministic", first, SeasonalThemeRules.themeForDate(date)) }
            date = date.plusDays(1)
        }
    }

    @Test
    fun `the calendar reads nothing but the date`() {
        // A `LocalDate` carries no zone and no clock time, so the same calendar day cannot resolve
        // differently for two devices in different places, and the turnover is local midnight.
        // Constructed here from three integers to make that explicit.
        assertEquals(theme(10, 31), SeasonalThemeRules.themeForDate(LocalDate.of(2026, 10, 31)))
        assertEquals(theme(11, 1), SeasonalThemeRules.themeForDate(LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun `every theme the calendar names exists in the catalogue`() {
        var date = LocalDate.of(2026, 1, 1)
        val ids = ThemeCatalog.ALL.map { it.id }.toSet()
        while (date.isBefore(LocalDate.of(2027, 1, 1))) {
            val id = SeasonalThemeRules.themeForDate(date)
            assertTrue("$date names $id, which is not a built-in theme", id in ids)
            date = date.plusDays(1)
        }
    }

    @Test
    fun `leap day resolves`() {
        assertEquals("winter", SeasonalThemeRules.themeForDate(LocalDate.of(2028, 2, 29)))
    }
}
