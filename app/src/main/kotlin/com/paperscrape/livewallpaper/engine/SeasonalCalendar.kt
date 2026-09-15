package com.paperscrape.livewallpaper.engine

import org.json.JSONObject
import java.time.MonthDay

/**
 * The user-adjustable shape of the automatic-theme calendar.
 *
 * [SeasonalThemeRules] used to hold its dates inside the lambdas that tested them, which made them
 * unreachable from anywhere else — there was nothing to read, nothing to write and nothing to show.
 * The dates live here now, as data; the rules read them.
 *
 * ### What the user can and cannot move
 *
 * **Only the dates.** Which windows exist, what each is called, which theme each selects and the
 * order they are checked in are all [CalendarWindow], which is code. A window cannot be added,
 * removed, renamed or re-pointed at a different theme, and the precedence is not a preference.
 *
 * ### Only the difference is stored
 *
 * The factory calendar is [DEFAULT], in code. A persisted document carries **only the windows the
 * user actually moved**, so an install that has never opened the calendar screen has no key at all
 * and resolves every date exactly as a build without this feature would. That is what makes
 * `SeasonalCalendarIdentityTest` a meaningful check rather than a tautology: it compares the
 * factory calendar against the dates v5.0 shipped, day by day.
 *
 * It also means a future release that moves a factory boundary moves it for everybody who has not
 * overridden that particular window, which is the behaviour a stored full snapshot would quietly
 * take away.
 */
data class SeasonalCalendar(
    /** Windows whose span the user has moved. Anything absent takes [CalendarWindow.factorySpan]. */
    val spans: Map<CalendarWindow, CalendarSpan> = emptyMap(),
    /** Easter's length, which is two offsets rather than a span. Absent means [EasterSpan.FACTORY]. */
    val easter: EasterSpan = EasterSpan.FACTORY,
) {

    /** The span in force for [window] — the user's, or the factory one. */
    fun spanFor(window: CalendarWindow): CalendarSpan = spans[window] ?: window.factorySpan

    /** True when nothing has been moved, i.e. this is the factory calendar. */
    val isFactory: Boolean get() = spans.isEmpty() && easter == EasterSpan.FACTORY

    fun withSpan(window: CalendarWindow, span: CalendarSpan): SeasonalCalendar {
        require(window != CalendarWindow.EASTER) { "Easter has no span; use withEaster()" }
        // An edit that lands back on the factory value stops being an override rather than being
        // stored as one that happens to agree. Otherwise "reset this window" and "drag it back
        // where it was" would leave the document in two different states with one behaviour.
        return copy(spans = if (span == window.factorySpan) spans - window else spans + (window to span))
    }

    fun withEaster(span: EasterSpan): SeasonalCalendar = copy(easter = span)

    companion object {
        /** The factory calendar: every window at its code-declared span. */
        val DEFAULT = SeasonalCalendar()
    }
}

/** Occasions sit above seasons; the two tiers overlap freely and neither overlaps itself. */
enum class CalendarTier { OCCASION, SEASON }

/**
 * A window of the calendar, in precedence order.
 *
 * **Declaration order is the precedence order** — the first match wins, occasions before seasons.
 * That is the same order [SeasonalThemeRules] documented in prose before this enum existed, and
 * moving an entry here moves the behaviour, so do not reorder for tidiness.
 *
 * There are eight of them. The shipped rules had nine entries because autumn was written twice,
 * split around Halloween; it is one continuous window now (see [factorySpan]) and Halloween passes
 * over it, which is what the tiers are for.
 */
enum class CalendarWindow(
    /** Stable key for persistence. Never derived from [name] or [label], both of which may change. */
    val key: String,
    val label: String,
    val themeId: String,
    val tier: CalendarTier,
    /**
     * The span this window has unless the user moves it. Easter has none — it is computed — and
     * reading this for [EASTER] throws rather than returning a lie.
     */
    private val declaredSpan: CalendarSpan?,
) {
    EASTER("easter", "Easter", "easter", CalendarTier.OCCASION, null),
    HALLOWEEN("halloween", "Halloween", "halloween", CalendarTier.OCCASION, CalendarSpan(10, 1, 10, 31)),
    CHRISTMAS("christmas", "Christmas", "christmas", CalendarTier.OCCASION, CalendarSpan(12, 1, 12, 26)),
    NEW_YEAR("new_year", "New Year", "new_year", CalendarTier.OCCASION, CalendarSpan(12, 27, 1, 7)),

    // The seasons are a continuous ribbon: meteorological boundaries, each starting on the first of
    // its month. v5.0 shipped them covering 296 days of the year and leaving 69 — the whole of
    // October, the whole of December and 1–7 January — with no season underneath at all. It worked
    // only because an occasion happened to cover every one of those days, so the moment a user
    // shortened Halloween there would have been nothing beneath it. Winter ends on the 29th rather
    // than the 28th because `MonthDay.of(2, 29)` exists and a leap day must not fall out of the
    // ribbon; in a common year no date can equal it, so it costs nothing.
    WINTER("winter", "Winter", "winter", CalendarTier.SEASON, CalendarSpan(12, 1, 2, 29)),
    SPRING("spring", "Spring", "spring", CalendarTier.SEASON, CalendarSpan(3, 1, 5, 31)),
    SUMMER("summer", "Summer", "beach", CalendarTier.SEASON, CalendarSpan(6, 1, 8, 31)),
    AUTUMN("autumn", "Autumn", "autumn", CalendarTier.SEASON, CalendarSpan(9, 1, 11, 30)),
    ;

    val factorySpan: CalendarSpan
        get() = declaredSpan ?: error("$key is computed and has no span")

    /** True for the one window whose dates are arithmetic rather than a span. */
    val isComputed: Boolean get() = declaredSpan == null

    companion object {
        val OCCASIONS: List<CalendarWindow> = entries.filter { it.tier == CalendarTier.OCCASION }
        val SEASONS: List<CalendarWindow> = entries.filter { it.tier == CalendarTier.SEASON }
        /** Every window the user edits as a pair of dates — i.e. all but Easter. */
        val DATED: List<CalendarWindow> = entries.filter { !it.isComputed }

        fun byKey(key: String): CalendarWindow? = entries.firstOrNull { it.key == key }
    }
}

/**
 * An inclusive month-and-day span, which may wrap across the year end (New Year, Winter).
 *
 * Year-free on purpose: the window is "the whole of October", not "October 2026". [MonthDay]
 * carries no year, so 29 February is a representable endpoint that simply never matches in a
 * common year.
 */
data class CalendarSpan(val start: MonthDay, val end: MonthDay) {

    constructor(startMonth: Int, startDay: Int, endMonth: Int, endDay: Int) :
        this(MonthDay.of(startMonth, startDay), MonthDay.of(endMonth, endDay))

    /** True when the span runs through 31 December into the new year. */
    val wraps: Boolean get() = start > end

    operator fun contains(day: MonthDay): Boolean =
        if (!wraps) day >= start && day <= end else day >= start || day <= end

    /** Inclusive length in days, counting 29 February. */
    val lengthInDays: Int get() = ALL_DAYS.count { it in this }

    companion object {
        /**
         * Every month-and-day that exists, 29 February included — 366 of them.
         *
         * Coverage is decided by walking these rather than by comparing endpoints, because endpoint
         * arithmetic on wrapping spans is exactly the kind of reasoning that produced the overlap
         * v4.x shipped, where Christmas and New Year both claimed 30 and 31 December and ordering
         * alone decided it.
         */
        val ALL_DAYS: List<MonthDay> = buildList {
            for (month in 1..12) {
                val days = java.time.Month.of(month).maxLength()
                for (day in 1..days) add(MonthDay.of(month, day))
            }
        }
    }
}

/**
 * Easter's window, as two offsets from the computed Sunday.
 *
 * The factory window is Good Friday to Easter Monday: −2 to +1. The user adjusts the two lengths,
 * never a date — there is no date to adjust, and presenting one would be a lie about a window that
 * moves by up to a month between years.
 */
data class EasterSpan(val daysBefore: Int, val daysAfter: Int) {

    init {
        require(daysBefore >= 0 && daysAfter >= 0) { "offsets are lengths, not signed days" }
        require(daysBefore <= MAX_BEFORE && daysAfter <= MAX_AFTER) { "Easter window out of range" }
    }

    /** True when [daysFromSunday] (negative before, positive after) falls inside the window. */
    fun covers(daysFromSunday: Long): Boolean = daysFromSunday in -daysBefore.toLong()..daysAfter.toLong()

    companion object {
        /** Good Friday through Easter Monday — the long weekend, and what v5.0 shipped. */
        val FACTORY = EasterSpan(daysBefore = 2, daysAfter = 1)

        // Holy Week reaches back seven days; beyond that the window stops meaning Easter. The
        // limits exist so the screen has a range to offer and a stored document has something to be
        // clamped to, not because anything downstream breaks.
        const val MAX_BEFORE = 7
        const val MAX_AFTER = 7
    }
}

// --- JSON ---------------------------------------------------------------------------------
// Hand-rolled with org.json, like CustomThemeData: the shape is small, and the app already
// carries no serialization dependency.

/**
 * Current schema version written by [SeasonalCalendar.toJsonString].
 *
 * Versioned from the first release that writes it, so that the question "what shape is this
 * document" never has to be answered by guessing from its contents — which is the position
 * `CustomThemeData` was left in by its first four versions.
 */
const val SEASONAL_CALENDAR_SCHEMA_VERSION = 1

private const val KEY_VERSION = "schemaVersion"
private const val KEY_WINDOWS = "windows"
private const val KEY_START = "start"
private const val KEY_END = "end"
private const val KEY_BEFORE = "before"
private const val KEY_AFTER = "after"

private fun MonthDay.toStorage(): String = "%02d-%02d".format(monthValue, dayOfMonth)

private fun monthDayOrNull(raw: String?): MonthDay? {
    if (raw.isNullOrEmpty()) return null
    val parts = raw.split('-')
    if (parts.size != 2) return null
    val month = parts[0].toIntOrNull() ?: return null
    val day = parts[1].toIntOrNull() ?: return null
    return runCatching { MonthDay.of(month, day) }.getOrNull()
}

fun SeasonalCalendar.toJsonString(): String = JSONObject().apply {
    put(KEY_VERSION, SEASONAL_CALENDAR_SCHEMA_VERSION)
    val windows = JSONObject()
    for ((window, span) in spans) {
        windows.put(
            window.key,
            JSONObject()
                .put(KEY_START, span.start.toStorage())
                .put(KEY_END, span.end.toStorage()),
        )
    }
    if (easter != EasterSpan.FACTORY) {
        windows.put(
            CalendarWindow.EASTER.key,
            JSONObject()
                .put(KEY_BEFORE, easter.daysBefore)
                .put(KEY_AFTER, easter.daysAfter),
        )
    }
    put(KEY_WINDOWS, windows)
}.toString()

/**
 * Reads a persisted calendar. **Anything unreadable reads as the factory calendar**, never as a
 * partial one.
 *
 * A malformed document here costs the user their date edits and leaves the wallpaper showing the
 * calendar it shipped with, which is a state they can see and redo. The alternative — keeping the
 * half of the document that parsed — would produce a calendar nobody chose, with gaps or overlaps
 * nothing else in the app expects.
 *
 * An unknown window key is skipped rather than refused: a document written by a newer build that
 * has a window this one does not must still yield its other windows.
 */
fun seasonalCalendarFromJsonString(raw: String?): SeasonalCalendar {
    if (raw.isNullOrBlank()) return SeasonalCalendar.DEFAULT
    val root = runCatching { JSONObject(raw) }.getOrNull() ?: return SeasonalCalendar.DEFAULT
    val windows = root.optJSONObject(KEY_WINDOWS) ?: return SeasonalCalendar.DEFAULT

    val spans = HashMap<CalendarWindow, CalendarSpan>()
    var easter = EasterSpan.FACTORY
    for (key in windows.keys()) {
        val window = CalendarWindow.byKey(key) ?: continue
        val entry = windows.optJSONObject(key) ?: continue
        if (window.isComputed) {
            val before = entry.optInt(KEY_BEFORE, EasterSpan.FACTORY.daysBefore)
            val after = entry.optInt(KEY_AFTER, EasterSpan.FACTORY.daysAfter)
            easter = runCatching {
                EasterSpan(
                    before.coerceIn(0, EasterSpan.MAX_BEFORE),
                    after.coerceIn(0, EasterSpan.MAX_AFTER),
                )
            }.getOrDefault(EasterSpan.FACTORY)
        } else {
            // `optString(name, null)` is what the rest of this project reaches for and it costs a
            // platform-type warning every time; the empty default says the same thing and does not.
            val start = monthDayOrNull(entry.optString(KEY_START, "")) ?: continue
            val end = monthDayOrNull(entry.optString(KEY_END, "")) ?: continue
            val span = CalendarSpan(start, end)
            if (span != window.factorySpan) spans[window] = span
        }
    }
    return SeasonalCalendar(spans = spans, easter = easter)
}
