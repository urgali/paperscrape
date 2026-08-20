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
 * ### The calendar covers the whole year
 *
 * It used to cover four windows and return `null` for the rest, leaving the caller on whatever the
 * user last picked by hand. That made "automatic" mean "automatic in December, at Easter and over
 * the summer", which is not a setting anybody can predict the behaviour of. **Every date now
 * resolves.**
 *
 * ### Precedence is a list, not an accident
 *
 * The old table relied on ordering alone, with a comment asking the next editor to keep narrow
 * windows above broad ones — and its two December windows overlapped in a way that made Christmas
 * unreachable on 30 and 31 December. The occasions are now a separate ordered list checked before
 * the seasons, and the seasons partition what is left and cannot overlap each other:
 *
 * 1. **Easter** — moves every year, so it wins wherever it lands.
 * 2. **Halloween**
 * 3. **Christmas**
 * 4. **New Year**
 * 5. **Spring / Winter / Autumn / Beach** — the seasonal fallback.
 *
 * Putting Easter above Halloween and Christmas is a statement of intent rather than of dates: the
 * three cannot collide in the Gregorian calendar, and stating the order anyway means the answer
 * does not depend on that continuing to be true of the arithmetic.
 */
object SeasonalThemeRules {

    private data class Window(val label: String, val themeId: String, val matches: (LocalDate) -> Boolean)

    /** Between [from] and [to] inclusive on the month-and-day, wrapping across the year end. */
    private fun between(date: LocalDate, from: MonthDay, to: MonthDay): Boolean {
        val md = MonthDay.from(date)
        return if (from <= to) md >= from && md <= to else md >= from || md <= to
    }

    /**
     * The dated occasions, in precedence order. Checked before [seasons].
     *
     * Halloween, Christmas and New Year are fixed. Easter is not, and deliberately so: pinning it
     * to a fixed week would put it in the wrong month most years.
     */
    private val occasions: List<Window> = listOf(
        Window("Easter", "easter") { date ->
            // Good Friday through Easter Monday: the long weekend, not a week either side of it.
            // The old rule spanned -3..+3, which reached back into Holy Week and forward past the
            // point anyone is still decorating.
            val daysFromEaster = ChronoUnit.DAYS.between(computeEasterSunday(date.year), date)
            daysFromEaster in -2..1
        },
        Window("Halloween", "halloween") { date ->
            between(date, MonthDay.of(10, 1), MonthDay.of(10, 31))
        },
        Window("Christmas", "christmas") { date ->
            between(date, MonthDay.of(12, 1), MonthDay.of(12, 26))
        },
        Window("New Year", "new_year") { date ->
            between(date, MonthDay.of(12, 27), MonthDay.of(1, 7))
        },
    )

    /**
     * The seasons, which cover the rest of the year exactly once each.
     *
     * Northern-hemisphere. A future refinement could flip it on the device's latitude; until then
     * a stated hemisphere is better than an ambiguous one.
     */
    private val seasons: List<Window> = listOf(
        Window("Winter", "winter") { date -> between(date, MonthDay.of(1, 8), MonthDay.of(3, 1)) },
        Window("Spring", "spring") { date -> between(date, MonthDay.of(3, 2), MonthDay.of(5, 31)) },
        Window("Summer", "beach") { date -> between(date, MonthDay.of(6, 1), MonthDay.of(8, 31)) },
        Window("Autumn", "autumn") { date -> between(date, MonthDay.of(9, 1), MonthDay.of(9, 30)) },
        Window("Autumn", "autumn") { date -> between(date, MonthDay.of(11, 1), MonthDay.of(11, 30)) },
    )

    private fun windowFor(date: LocalDate): Window? =
        occasions.firstOrNull { it.matches(date) } ?: seasons.firstOrNull { it.matches(date) }

    /**
     * The themeId for [date], which is **the device's local date** by default.
     *
     * `LocalDate.now()` reads the default time zone, so the theme turns over at local midnight
     * rather than at some hour determined by an offset from UTC. The same local date always
     * produces the same theme: nothing here reads a clock time, a zone or anything else that could
     * make the answer depend on when within the day it was asked.
     */
    fun themeForDate(date: LocalDate = LocalDate.now()): String? = windowFor(date)?.themeId

    /** Same as [themeForDate], but the window's label, for the settings screen to display. */
    fun labelForDate(date: LocalDate = LocalDate.now()): String? = windowFor(date)?.label

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
