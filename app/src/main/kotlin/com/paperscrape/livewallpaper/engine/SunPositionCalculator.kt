package com.paperscrape.livewallpaper.engine

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Produces a normalized "day progress" value in [0, 1) representing where we are
 * between one solar midnight and the next, plus a sky-phase blend used by the renderer.
 *
 * Two modes:
 *  - Simple: sunrise fixed at 06:00, sunset at 20:00 (used when no location is available).
 *  - Location-aware: approximate NOAA sunrise/sunset equation from latitude/longitude/date.
 *
 * This is intentionally a lightweight approximation (not astronomical-grade precision) —
 * plenty accurate for a wallpaper's lighting mood.
 */
object SunPositionCalculator {

    data class DayPhase(
        /** 0 = solar midnight, 0.25 = sunrise, 0.5 = solar noon, 0.75 = sunset */
        val progress: Float,
        /** 0 = fully night, 1 = fully day. Used to blend sky/hill colors. */
        val dayBlend: Float,
        /** Horizontal position of sun/moon across the screen, 0..1 */
        val celestialX: Float,
        /** Vertical position (arc height), 0 = horizon, 1 = zenith */
        val celestialY: Float,
        val isSunVisible: Boolean,
        /**
         * The clock hour this phase was computed for, wrapped onto 0..24.
         *
         * Carried so that anything scheduled on the clock -- the business hours among them --
         * reads **the hour that moved the sun**, whether that came from the real clock or from
         * the user's `fixedHour`. Reading a clock of its own is exactly the defect this field
         * exists to make unnecessary: a scene frozen at 23:00 whose shops keep daytime hours
         * because the phone says 14:00.
         */
        val hour24: Float,
        /**
         * Where the moon is in its ~29.53-day cycle for this frame, 0 = new, 0.5 = full.
         *
         * Carried for exactly the reason [hour24] is, and it is the same defect one field over:
         * **anything the scene draws must arrive as an input to the frame, not be fetched from a
         * clock while the frame is being painted.** A `PaperRenderer.drawMoonWithPhase` that
         * called [moonPhase] itself, with its `System.currentTimeMillis()` default, would draw a
         * moon chosen by the phone in a scene whose hour, theme, customisation and scene clock
         * are all pinned. Rendered three hours apart on one build, `night` would move by 17
         * pixels and `shops-closed-night` by 39: two goldens green at 19:02 would be red at
         * 22:25, because the phase crosses a sprite boundary while the code does not change at
         * all.
         *
         * The live wallpaper passes the real phase every frame (see [PaperWallpaperService]); a
         * caller that does not name one gets [FIXED_MOON_PHASE], so its frame is a function of
         * what it wrote down.
         *
         * **A `var`, and only for [DayPhaseCache]**. The engine keeps one phase and
         * recomputes it when the hour or the sun's times change -- once a minute -- but the moon's
         * phase, as a `Float`, moves several times a second, and near a new moon every frame; a new
         * object each time would be an allocation a frame on the render thread. The cache writes the new
         * value into its own instance before the frame is drawn, so the frame still receives the
         * moon as an input. Nothing else writes it but the night by day's own copy (`PaperRenderer.nightOverDay`)
         * and the still moon's (`PaperRenderer.stillMoon`), which take the frame's value the same way.
         */
        var moonPhase: Float,
    )

    /**
     * How long the light lasts, given the two clock times that bound it.
     *
     * **A day is an arc on a circle, not an interval on a line.** `sunset - sunrise` is only the
     * day length when the two happen to fall on the same civil date; where they do not, it is a
     * negative number, which would propagate straight into the arc arithmetic if it were used as
     * the length. Sunset after midnight is not exotic — [approximateSunriseSunset] puts
     * solar noon at `12 - longitude/15 + utcOffset`, so anywhere sitting west inside its timezone
     * with a long summer day produces it, Iceland and northern Alaska among them.
     *
     * Two boundary cases carry meaning and are preserved exactly:
     *
     * - `sunrise == sunset` is **polar night**, a day of zero length. It is not a full day, and
     *   reading `(sunset - sunrise) mod 24` as 0 gets that right by luck rather than by intent, so
     *   it is written out.
     * - `sunset - sunrise == 24` is **polar day**, which [approximateSunriseSunset] emits as the
     *   literal pair `(0, 24)` precisely so that it stays distinguishable from the above after any
     *   amount of arithmetic.
     */
    fun dayLengthHours(sunriseHour: Float, sunsetHour: Float): Float {
        val span = sunsetHour - sunriseHour
        return when {
            span > 0f -> span.coerceAtMost(24f)
            span < 0f -> span + 24f
            else -> 0f
        }
    }

    /** `x` brought onto the 24-hour clock, for any `x`, negative included. Internal because
     * [BusinessHours] runs its own clock spans through the same arithmetic — one wrap rule. */
    internal fun wrap24(x: Float): Float = ((x % 24f) + 24f) % 24f

    fun compute(
        hour24: Float,
        sunriseHour: Float = 6f,
        sunsetHour: Float = 20f,
        moonPhase: Float = FIXED_MOON_PHASE,
    ): DayPhase {
        val lightHours = dayLengthHours(sunriseHour, sunsetHour)
        // **A day or a night shorter than [MIN_ARC_HOURS] is drawn as one that long**, near the polar circles: it
        // starts when it really starts and lasts the hour, and the other arc gives up the difference at its own
        // start. The sun or the moon then crosses its whole arc and the sky meets the other arc's half-light at both
        // ends, as on any other day. Drawn on its true length over an hour's arc, a day of 35 minutes would leave the sky
        // at full day and the sun near the zenith at its sunset, and the next frame would be half-light with the moon rising.
        // The two poles keep their meaning: polar night ([lightHours] 0) is night all day, polar day (24) day.
        var rise = sunriseHour
        var set = sunsetHour
        var light = lightHours
        if (lightHours > 0f && lightHours < MIN_ARC_HOURS) {
            set = wrap24(sunriseHour + MIN_ARC_HOURS)
            light = MIN_ARC_HOURS
        } else if (lightHours < 24f && lightHours > 24f - MIN_ARC_HOURS) {
            rise = wrap24(sunsetHour + MIN_ARC_HOURS)
            light = 24f - MIN_ARC_HOURS
        }
        // Only the poles still reach these floors: a night of no length under polar day, a day of none in polar night.
        val dayLength = light.coerceAtLeast(MIN_ARC_HOURS)
        val nightLength = (24f - light).coerceAtLeast(MIN_ARC_HOURS)

        // Both offsets are measured *around the clock* from their own boundary, so neither cares
        // whether the boundary and `hour24` fall on the same civil date. For a window that does
        // not wrap this is arithmetically identical to a plain subtraction.
        val sinceSunrise = wrap24(hour24 - rise)
        val isDay = light > 0f && sinceSunrise <= light
        val progress: Float
        val dayBlend: Float
        val arcT: Float // 0..1 across the visible arc (day or night)

        if (isDay) {
            arcT = sinceSunrise / dayLength
            progress = 0.25f + arcT * 0.5f
            // Twilight is shared between the two arcs: the day side runs from half-light at the
            // terminator up to full day, and the night side from half-light back down to full
            // night. See TERMINATOR_BLEND.
            dayBlend = TERMINATOR_BLEND + (1f - TERMINATOR_BLEND) * smoothEdge(arcT)
        } else {
            arcT = wrap24(hour24 - set) / nightLength
            progress = (0.75f + arcT * 0.5f) % 1f
            dayBlend = TERMINATOR_BLEND - TERMINATOR_BLEND * smoothEdge(arcT)
        }

        val celestialX = arcT
        // Simple arc: rises from horizon, peaks at midpoint, sets back to horizon.
        val celestialY = sin(arcT.coerceIn(0f, 1f) * PI.toFloat())

        return DayPhase(
            progress = progress,
            dayBlend = dayBlend.coerceIn(0f, 1f),
            celestialX = celestialX,
            celestialY = celestialY,
            isSunVisible = isDay,
            hour24 = wrap24(hour24),
            moonPhase = moonPhase,
        )
    }

    /**
     * The moon a frame gets when its caller did not say which moon it wanted: **a waxing
     * crescent**, one eighth of the way round the cycle.
     *
     * It is a picture, not a tuning. [PaperRenderer.drawMoonWithPhase] does not draw a continuous
     * terminator: it picks one of four silhouettes on thresholds of the illuminated fraction and
     * rotates the shape 180 degrees for the waning half, so the whole of the phase axis reaches a
     * frame as **eight discrete pictures plus a dark disc**. 0.125 gives `illuminated = 0.146`,
     * comfortably inside the crescent bucket (`< 0.35`) and well clear of the new-moon floor
     * (`0.02`), so it is not near a boundary that a later change to those thresholds could push
     * it over.
     *
     * It is also the moon the committed goldens are authored under, so a golden that names no
     * phase draws the moon it always drew.
     */
    const val FIXED_MOON_PHASE = 0.125f

    /**
     * The blend at sunrise and at sunset -- half day, half night, from both sides.
     *
     * This is what makes the two arcs meet. A night arc of `1f - smoothEdge(arcT)` would be 0 in
     * the middle of the night (correct) but **1 at both of its ends**: the instant after sunset
     * the sky would be painted with full daytime colours, and it would then take 12% of the
     * night -- over an hour in summer -- to darken. With the day arc ending at 0 at that same
     * instant, the blend would jump from full night to full day across the terminator and slide
     * back down, with the moon already climbing: "the moon rises while it is still daylight",
     * which is never about the moon's timing -- the moon appears exactly at sunset, which is
     * right, into a sky told it is noon.
     *
     * With both arcs anchored here the blend is continuous: full day, easing to half at sunset,
     * easing on down to full night, and symmetrically at dawn. The sun's and the moon's own
     * timings are untouched -- [isSunVisible] still flips exactly at sunrise and sunset, but for a day
     * or a night shorter than [MIN_ARC_HOURS], which ends when its hour does.
     */
    private const val TERMINATOR_BLEND = 0.5f

    /**
     * Eases the first/last [TWILIGHT_EDGE_FRACTION] of the arc so twilight fades smoothly
     * instead of snapping. **The** twilight of this scene; the opening hours do not use it, and keep
     * a step at the minute ([BusinessHours]).
     */
    internal fun smoothEdge(t: Float): Float {
        val edge = TWILIGHT_EDGE_FRACTION
        return when {
            t < edge -> t / edge
            t > 1f - edge -> (1f - t) / edge
            else -> 1f
        }
    }

    /** The share of an arc the twilight ramp occupies, at each of its two ends. */
    internal const val TWILIGHT_EDGE_FRACTION = 0.12f

    /**
     * The shortest day and the shortest night [compute] draws, in hours: a shorter one is drawn this long, so its
     * twilight ramps take 12 % of an hour each and the sky moves at most 0.07 of its blend in a minute.
     */
    internal const val MIN_ARC_HOURS = 1f

    /**
     * Approximate sunrise/sunset hour (local civil clock, decimal) for a given
     * latitude/longitude/day-of-year, based on the standard NOAA/Sunrise equation
     * simplification. Good to within a few minutes, which is more than enough for a
     * wallpaper's lighting.
     *
     * @param utcOffsetHours the civil clock's *current* offset from UTC, already including any
     *   DST adjustment for the date in question (see the caller in [PaperWallpaperService], which
     *   must use `TimeZone.getOffset(atThatMoment)`, not `TimeZone.rawOffset` -- `rawOffset` is
     *   explicitly the *non*-DST standard offset, so during DST it under/overshoots by an hour).
     */
    fun approximateSunriseSunset(
        latitudeDeg: Double,
        longitudeDeg: Double,
        dayOfYear: Int,
        utcOffsetHours: Double,
    ): Pair<Float, Float> {
        val lat = Math.toRadians(latitudeDeg)
        // Solar declination approximation.
        val decl = Math.toRadians(23.44) * sin(Math.toRadians((360.0 / 365.0) * (dayOfYear - 81)))

        val cosHourAngle = -tan(lat) * tan(decl)
        // Polar day/night guard: when |cosHourAngle| > 1 there is no real solution (the sun
        // never crosses the horizon that day). Clamping *before* acos is intentional and already
        // correct, not a fabricated fallback: cosHourAngle very negative (< -1) is polar
        // day (sun always up) and clamps to -1 -> acos(-1) = pi = a full 24h day arc; very
        // positive (> 1) is polar night (sun never rises) and clamps to +1 -> acos(1) = 0 = a
        // zero-length day arc. Both are then answered explicitly just below, before any clock
        // arithmetic: polar day as the literal (0, 24), polar night as (noon, noon), a day of no
        // length, which [compute] draws as night all day.
        val clamped = cosHourAngle.coerceIn(-1.0, 1.0)
        val hourAngle = acos(clamped) // radians

        val hourAngleHours = Math.toDegrees(hourAngle) / 15.0
        // The two poles, answered before any clock arithmetic can blur them. A 24-hour day and a
        // zero-hour day are both "sunrise and sunset at the same moment" once wrapped onto a
        // 24-hour clock, so the difference has to be settled here, where it is still visible. The
        // literal (0, 24) is the pair [dayLengthHours] reads back as a full day.
        if (hourAngleHours >= 12.0) return 0f to 24f
        if (hourAngleHours <= 0.0) {
            val noon = wrap24((12.0 - longitudeDeg / 15.0 + utcOffsetHours).toFloat())
            return noon to noon
        }
        // Local solar noon, expressed in this location's *civil clock* hours: local solar time
        // runs ahead of UTC by longitudeDeg/15 hours (east positive), and civil time runs ahead
        // of UTC by utcOffsetHours -- so civil clock time lags solar time by exactly the
        // difference between the two. This is what actually uses longitude and utcOffsetHours;
        // a `- utcOffsetHours * 0.0` would discard the offset entirely and pin every location to
        // a fixed 12:00 solar noon regardless of where in its timezone it sits.
        val solarNoon = 12.0 - longitudeDeg / 15.0 + utcOffsetHours
        // **Wrapped onto the clock, not clamped into it.** These are civil clock times, and a
        // civil clock is a circle: a day whose light begins at 02:00 and ends at 00:40 is an
        // ordinary Icelandic June, not an error to be squashed. A `coerceIn(0.0, 23.98)` /
        // `coerceIn(0.02, 24.0)` would not move such a day back inside the clock -- it would
        // delete whichever end did not fit, and report sunset "at midnight" for a sun that sets
        // forty minutes later. `solarNoon` itself can also fall outside 0..24 (Kiritimati keeps
        // UTC+14 at 157W, which puts its solar noon near 36:30), and the same clamp would turn
        // that into a zero-length day at midnight on the equator.
        //
        // Because `0 < hourAngleHours < 12` here -- both poles returned above -- the two results
        // are always distinct, and `dayLengthHours` recovers exactly `2 * hourAngleHours` from
        // them whichever side of midnight each lands on.
        val sunrise = wrap24((solarNoon - hourAngleHours).toFloat())
        val sunset = wrap24((solarNoon + hourAngleHours).toFloat())
        return sunrise to sunset
    }

    private const val SYNODIC_MONTH_DAYS = 29.530588853
    // A known new moon: 2000-01-06 18:14 UTC. Any correct reference new moon works equally well
    // since only the *fractional position* within the ~29.53-day cycle matters here.
    private const val REFERENCE_NEW_MOON_EPOCH_MILLIS = 947182440000L

    /**
     * Real lunar phase for the given moment, as a fraction of the ~29.53-day synodic month:
     * 0 = new moon, 0.25 = first quarter, 0.5 = full moon, 0.75 = last quarter, cycling back to
     * 1 = new moon again. Good to within a few hours, which is more than enough for a wallpaper.
     *
     * **This function is a clock, and only the wallpaper service may call it.** The default
     * argument reads the wall clock: a renderer reaching in here while painting would make the
     * frame a function of when it was painted rather than of the scene it was handed. The phase
     * arrives in [DayPhase.moonPhase], and `RenderPathReadsNoWallClockTest` is what keeps it
     * that way.
     */
    fun moonPhase(epochMillis: Long = System.currentTimeMillis()): Float {
        val daysSinceReference = (epochMillis - REFERENCE_NEW_MOON_EPOCH_MILLIS) / 86_400_000.0
        val cycles = daysSinceReference / SYNODIC_MONTH_DAYS
        val phase = cycles - kotlin.math.floor(cycles)
        return phase.toFloat()
    }
}
