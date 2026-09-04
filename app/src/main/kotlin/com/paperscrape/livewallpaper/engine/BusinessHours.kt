package com.paperscrape.livewallpaper.engine

/**
 * How open the commercial buildings are at a given hour of the scene.
 *
 * ### What it governs, and what it does not
 *
 * The shops, the bar and the towers ([WindowBuildingKind.COMMERCIAL] and
 * [WindowBuildingKind.SKYSCRAPER]): inside their hours they behave exactly as they always have —
 * occupants at the glass, windows lit at night. Outside them nobody stands at a window and the
 * glass stays in its unlit daytime colour whatever the hour. Houses are deliberately not
 * businesses: their windows glowing at night are the effect that keeps the scene alive, and
 * [WindowBuildingKind.HOUSE] never consults this. The pedestrians on the pavement are untouched —
 * the hours govern windows, not the street.
 *
 * ### The clock
 *
 * The hour that comes in here is `DayPhase.hour24` — **the hour that moved the sun**, whether it
 * came from the real clock or from the user's `fixedHour`. Nothing in this file reads a clock of
 * its own: `System.currentTimeMillis()` is the wrong clock twice over — it ignores `fixedHour`,
 * and moving the device clock backwards would run a schedule negative.
 *
 * ### The fade, derived rather than chosen
 *
 * At the boundary there is a crossfade, not a switch — the doctrine every population change in
 * this scene already follows ([PeopleDensity], `CarSelection.densityAt`). Its duration is not a
 * new number: the opening span is treated as an arc exactly the way the solar day is, and eased
 * by the same [SunPositionCalculator.smoothEdge] that makes dusk — lights come up over the first
 * [SunPositionCalculator.TWILIGHT_EDGE_FRACTION] (12%) of the span and go down over the last 12%,
 * as daylight does over its arc. A long business day fades slowly like a long summer dusk; a
 * short one briskly like a winter's.
 *
 * ### The two boundary meanings
 *
 * `open == close` is **always open** — the state every scene was in before this existed, and what
 * the toggle-off default must render identically to. "Always closed" is not an hour: that wish is
 * the buildings' own visibility switch. And the span wraps: 09:00–02:00 is a valid business day,
 * run through the same wrap arithmetic ([SunPositionCalculator.dayLengthHours]) the solar day
 * uses for a sunset after midnight.
 */
object BusinessHours {

    /**
     * The openness in force at [hour24], 0 (closed) .. 1 (fully open).
     *
     * With [enabled] false, or with a degenerate span, this is constantly 1 — bitwise the
     * behaviour the scene shipped with, which is what lets the toggle default to off without
     * moving a single golden pixel.
     */
    fun opennessAt(enabled: Boolean, openHour: Float, closeHour: Float, hour24: Float): Float {
        if (!enabled) return 1f
        if (openHour == closeHour) return 1f
        val span = SunPositionCalculator.dayLengthHours(
            SunPositionCalculator.wrap24(openHour),
            SunPositionCalculator.wrap24(closeHour),
        )
        if (span <= 0f) return 1f
        val sinceOpen = SunPositionCalculator.wrap24(hour24 - openHour)
        if (sinceOpen > span) return 0f
        return SunPositionCalculator.smoothEdge(sinceOpen / span).coerceIn(0f, 1f)
    }
}
