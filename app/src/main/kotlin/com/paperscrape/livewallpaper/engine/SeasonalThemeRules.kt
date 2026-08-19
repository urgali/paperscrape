package com.paperscrape.livewallpaper.engine

import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit

/**
 * Maps the current date to a themeId, for the optional "automatic theme by date" setting.
 *
 * Design note: rules resolve to a plain [String] themeId, exactly like [WallpaperPrefs]'s
 * stored `themeId` field — the same string that [ThemeCatalog.byId] and
 * [SceneObjectCatalog.layoutFor] already know how to resolve (built-in id, `random:<seed>`,
 * or — once the custom theme editor exists — a custom-saved id). This is intentional: nothing
 * here is hardcoded to "only the 9 built-in themes", so a future theme editor can plug straight
 * into this table without changing how rules are resolved.
 *
 * Rules are checked in order; the first match wins. Keep narrow/specific windows (New Year's
 * Eve) before broader ones (Christmas) so the narrower one takes priority where they overlap.
 */
object SeasonalThemeRules {

    private data class Window(val label: String, val themeId: String, val matches: (LocalDate) -> Boolean)

    private val windows: List<Window> = listOf(
        Window("New Year's Eve", "new_year") { date ->
            val md = MonthDay.from(date)
            md >= MonthDay.of(12, 30) || md <= MonthDay.of(1, 1)
        },
        Window("Christmas", "christmas") { date ->
            val md = MonthDay.from(date)
            md >= MonthDay.of(12, 18) || md <= MonthDay.of(1, 6)
        },
        Window("Easter", "easter") { date ->
            val easterSunday = computeEasterSunday(date.year)
            val daysFromEaster = ChronoUnit.DAYS.between(easterSunday, date)
            daysFromEaster in -3..3
        },
        Window("Summer", "beach") { date ->
            // Northern-hemisphere summer window. Simple and predictable; a future refinement
            // could flip this based on the device's detected hemisphere/latitude.
            val md = MonthDay.from(date)
            md >= MonthDay.of(6, 21) && md <= MonthDay.of(9, 21)
        },
    )

    /**
     * Returns the themeId that should be active today, or null if no seasonal window currently
     * applies (in which case the caller should fall back to the user's manually selected theme).
     */
    fun themeForDate(date: LocalDate = LocalDate.now()): String? =
        windows.firstOrNull { it.matches(date) }?.themeId

    /** Same as [themeForDate], but also returns which window matched (for UI display). */
    fun labelForDate(date: LocalDate = LocalDate.now()): String? =
        windows.firstOrNull { it.matches(date) }?.label

    /**
     * Anonymous Gregorian algorithm (a.k.a. "Computus") for the date of Easter Sunday in a
     * given year. Standard, well-known algorithm — not something specific to any third party.
     *
     * `internal` rather than `private` purely so it can be unit tested directly against known
     * Easter dates. Testing it only through [themeForDate] would not catch an off-by-one, since
     * the Easter window spans a whole week either side. Not part of the public API — nothing
     * outside this module should call it.
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
