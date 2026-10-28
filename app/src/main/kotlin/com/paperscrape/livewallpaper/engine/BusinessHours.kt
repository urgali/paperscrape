package com.paperscrape.livewallpaper.engine

/**
 * When the buildings that keep opening hours are open, at a given hour of the scene.
 *
 * ### What it governs, and what it does not
 *
 * Every building that is not a house ([WindowBuildingKind.COMMERCIAL], [WindowBuildingKind.SCHOOL],
 * [WindowBuildingKind.STATION] and [WindowBuildingKind.SKYSCRAPER]), in three groups since v5.13 (two in
 * v5.12), each with its switch and its hours -- the towers', the shops' (the restaurant, the bar, the
 * shop) and the public buildings' (the school's: the fire station and the police station never close,
 * `Storefront.keepsHours`) -- a building by what it is (`SceneCustomization.opennessFor`): inside their
 * hours they behave as they always have -- people at the glass, windows lit at night. Outside them nobody
 * stands at a window, the glass is unlit -- the day's glass by day, at night the dark glass of a house's
 * unlit window (`SceneObjectRenderer.businessGlassColor`) -- and the building shows it is shut, by day as
 * by night: a blind down in every window a person may stand at and the shutter down over the front door
 * (`SceneObjectRenderer.drawShut`, v5.14). Houses are deliberately not businesses: their windows lit at
 * night -- half of them since v5.12 (`WindowRoster`) -- are the effect that keeps the scene alive, and
 * [WindowBuildingKind.HOUSE] never consults this. The pedestrians on the pavement are untouched -- the
 * hours govern windows, not the street.
 *
 * ### At the minute (v5.14)
 *
 * **A building opens at its opening minute and is shut at its closing minute.** Asked for by the maintainer,
 * *«verificare orari negozi - ho l'impressione che non vengano rispettati»* (2026-10-14), and chosen with
 * *«si a tutte»* (2026-10-15) to the table of photographed proposals that recommended this way, A:
 * open 09:00-20:00, at 09:00 the glass lights over two seconds and the people walk in at once, one after
 * the other; in the minute before the closing, 19:59, they walk out; at 20:00 the glass goes dark over two
 * seconds. So [isOpenAt] is a step at whole minutes and [peopleAt] the same step a minute earlier at the
 * closing; the two seconds, and the walks, are the building's doorway's (`WindowWalk.Doorway`): the glass
 * eases on the scene's clock, nobody walks in before it is lit, and it does not go dark while anybody is
 * still in. Until v5.14 the glass faded over 12 % of the opening span, inside it, and the people stood
 * only over its lit half: open 09:00-20:00, the first person came in about 10:00 and the last left about
 * 19:01.
 *
 * ### The clock
 *
 * The hour that comes in here is `DayPhase.hour24` -- **the hour that moved the sun**, whether it came
 * from the real clock, quantised to the minute (`PaperWallpaperService.sceneHourNow`), or from the user's
 * `fixedHour`. Nothing in this file reads a clock of its own: `System.currentTimeMillis()` is the wrong
 * clock twice over -- it ignores `fixedHour`, and moving the device clock backwards would run a schedule
 * negative. A fixed hour at the opening hour is open; at the closing hour, shut.
 *
 * ### The two boundary meanings
 *
 * `open == close` is **always open** -- the state every scene was in before this existed, and what the
 * toggle-off default must render identically to. "Always closed" is not an hour: that wish is the
 * buildings' own visibility switch. And the span wraps: 09:00-02:00 is a valid business day, run through
 * the same wrap arithmetic ([SunPositionCalculator.dayLengthHours]) the solar day uses for a sunset after
 * midnight.
 */
object BusinessHours {

    /** Minutes in a day: the clock the opening hours are counted on. */
    private const val MINUTES_PER_DAY = 1_440

    /**
     * How far under a whole minute an hour may fall and still be that minute: a `Float` hour of 09:00 can
     * come out of `hour24 - openHour` a few millionths of a minute short of it.
     */
    private const val MINUTE_SLACK = 1e-3f

    /**
     * Whether the building is open at [hour24]: from the opening minute up to, not including, the closing
     * minute. With [enabled] false, or with a degenerate span, always -- bitwise the behaviour the scene
     * shipped with, which is what lets the switch default to off without moving a single golden pixel.
     */
    fun isOpenAt(enabled: Boolean, openHour: Float, closeHour: Float, hour24: Float): Boolean {
        val span = spanMinutes(enabled, openHour, closeHour)
        return span <= 0 || minutesSinceOpening(openHour, hour24) < span
    }

    /**
     * The openness in force at [hour24]: 1 open, 0 shut ([isOpenAt]) -- what the glass is going to. The
     * two seconds it takes to get there are the doorway's (`WindowWalk.Doorway.glassAt`); the gallery card,
     * which is one moment, draws this.
     */
    fun opennessAt(enabled: Boolean, openHour: Float, closeHour: Float, hour24: Float): Float =
        if (isOpenAt(enabled, openHour, closeHour, hour24)) 1f else 0f

    /**
     * How many of a building's [people] are at its windows at [hour24]: all of them while it is open but for
     * its last minute, none in that minute -- they walk out in it, so the last is out before the glass goes
     * dark at the closing minute -- and none while it is shut.
     */
    fun peopleAt(people: Int, enabled: Boolean, openHour: Float, closeHour: Float, hour24: Float): Int {
        val span = spanMinutes(enabled, openHour, closeHour)
        if (span <= 0) return people
        return if (minutesSinceOpening(openHour, hour24) < span - 1) people else 0
    }

    /** The opening span in whole minutes, or 0 for a building that never closes. */
    private fun spanMinutes(enabled: Boolean, openHour: Float, closeHour: Float): Int {
        if (!enabled || openHour == closeHour) return 0
        val span = SunPositionCalculator.dayLengthHours(
            SunPositionCalculator.wrap24(openHour),
            SunPositionCalculator.wrap24(closeHour),
        )
        return Math.round(span * 60f)
    }

    /** The whole minutes from the opening to [hour24], 0 .. 1 439, wrapping at midnight. */
    private fun minutesSinceOpening(openHour: Float, hour24: Float): Int {
        val since = SunPositionCalculator.wrap24(hour24 - openHour) * 60f
        return Math.floorMod(kotlin.math.floor(since + MINUTE_SLACK).toInt(), MINUTES_PER_DAY)
    }
}
