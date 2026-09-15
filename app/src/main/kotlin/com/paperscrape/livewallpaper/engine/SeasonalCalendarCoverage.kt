package com.paperscrape.livewallpaper.engine

import java.time.MonthDay

/**
 * What a calendar looks like when you walk every day of the year through it.
 *
 * Both the settings screen and the tests ask the same two questions — *does anything claim this day
 * twice* and *does anything claim it at all* — and they ask them the same way, by enumeration over
 * [CalendarSpan.ALL_DAYS]. Endpoint arithmetic is the other way to answer, and it is how the
 * pre-v4 table came to have Christmas and New Year both claiming 30 and 31 December with only list
 * ordering deciding between them.
 */
data class CalendarCoverage(
    /** Days no season covers. Occasions are not consulted: an occasion is not a floor. */
    val uncoveredDays: List<MonthDay>,
    /** Days claimed by more than one window of the same tier, with the windows that claim them. */
    val conflicts: List<CalendarConflict>,
) {
    val hasGaps: Boolean get() = uncoveredDays.isNotEmpty()
    val hasConflicts: Boolean get() = conflicts.isNotEmpty()
    val isSound: Boolean get() = !hasGaps && !hasConflicts

    /** Uncovered days collapsed into contiguous runs, for a sentence a person can read. */
    val uncoveredRuns: List<ClosedRange<MonthDay>> get() = runsOf(uncoveredDays)
}

data class CalendarConflict(val day: MonthDay, val windows: List<CalendarWindow>)

/**
 * Walks the year and reports gaps and same-tier overlaps.
 *
 * **Easter is excluded from the conflict check, deliberately.** It is first in precedence and wins
 * wherever it lands, and it moves by up to five weeks between years — so "does Easter overlap
 * Halloween" has no year-free answer, and a validator that produced one would be inventing it.
 * Because Easter wins anyway, a collision resolves itself: the day comes back Easter, which is what
 * the precedence says it should. Nothing is being tolerated here that could surprise anybody.
 *
 * **Gaps are reported, not forbidden.** [SeasonalThemeRules.themeForDate] answers `null` for an
 * uncovered day and the caller falls back to the theme the user picked by hand, so a gap costs the
 * automatic setting a day rather than breaking anything. Whether the screen should let a user make
 * one is a question for the screen; this function only says where they are.
 */
fun SeasonalCalendar.coverage(): CalendarCoverage {
    val uncovered = ArrayList<MonthDay>()
    val conflicts = ArrayList<CalendarConflict>()

    for (day in CalendarSpan.ALL_DAYS) {
        val seasons = CalendarWindow.SEASONS.filter { day in spanFor(it) }
        if (seasons.isEmpty()) uncovered += day
        if (seasons.size > 1) conflicts += CalendarConflict(day, seasons)

        val occasions = CalendarWindow.OCCASIONS.filter { !it.isComputed && day in spanFor(it) }
        if (occasions.size > 1) conflicts += CalendarConflict(day, occasions)
    }
    return CalendarCoverage(uncoveredDays = uncovered, conflicts = conflicts)
}

/**
 * The windows [proposed] would collide with if [window] were moved there — same tier only.
 *
 * This is the settings screen's gate: it is asked **before** an edit is committed, so the
 * overlapping state is never reachable rather than being detected and corrected afterwards.
 * Returns empty for Easter, which has no span and is exempt (see [coverage]).
 */
fun SeasonalCalendar.conflictsFor(window: CalendarWindow, proposed: CalendarSpan): List<CalendarWindow> {
    if (window.isComputed) return emptyList()
    val siblings = CalendarWindow.entries.filter {
        it != window && !it.isComputed && it.tier == window.tier
    }
    return siblings.filter { other ->
        val otherSpan = spanFor(other)
        CalendarSpan.ALL_DAYS.any { it in proposed && it in otherSpan }
    }
}

/** Days a move would leave with no season under them, for the screen to warn about. */
fun SeasonalCalendar.gapsAfter(window: CalendarWindow, proposed: CalendarSpan): List<MonthDay> =
    if (window.tier != CalendarTier.SEASON) emptyList()
    else withSpan(window, proposed).coverage().uncoveredDays

/**
 * Collapses a set of days into contiguous runs, so a message can say "1–13 March" rather than
 * thirteen dates.
 *
 * **A run that crosses 31 December is reported as two**, because these are month-days with no year
 * and "adjacent" is defined by position in [CalendarSpan.ALL_DAYS]. A gap spanning the year end
 * therefore reads as "30 December–31 December, 1 January–2 January". It is wordier than it needs to
 * be and it is not wrong; joining them would mean deciding that the list wraps, which is true for a
 * span and not for a set of days.
 */
fun runsOf(days: List<MonthDay>): List<ClosedRange<MonthDay>> {
    if (days.isEmpty()) return emptyList()
    val present = days.toHashSet()
    val out = ArrayList<ClosedRange<MonthDay>>()
    var start: MonthDay? = null
    var previous: MonthDay? = null
    for (day in CalendarSpan.ALL_DAYS) {
        if (day in present) {
            if (start == null) start = day
            previous = day
        } else if (start != null) {
            out += start..previous!!
            start = null
        }
    }
    if (start != null) out += start..previous!!
    return out
}
