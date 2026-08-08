package com.paperscrape.livewallpaper.engine

import java.util.Calendar
import java.util.TimeZone
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
    )

    fun compute(
        hour24: Float,
        sunriseHour: Float = 6f,
        sunsetHour: Float = 20f,
    ): DayPhase {
        val dayLength = (sunsetHour - sunriseHour).coerceAtLeast(1f)
        val nightLength = 24f - dayLength

        val isDay = hour24 in sunriseHour..sunsetHour
        val progress: Float
        val dayBlend: Float
        val arcT: Float // 0..1 across the visible arc (day or night)

        if (isDay) {
            arcT = (hour24 - sunriseHour) / dayLength
            progress = 0.25f + arcT * 0.5f
            // Smooth blend near the edges so dawn/dusk have a gradient, not a hard cut.
            dayBlend = smoothEdge(arcT)
        } else {
            val hourSinceSunset = if (hour24 > sunsetHour) hour24 - sunsetHour else hour24 + (24f - sunsetHour)
            arcT = hourSinceSunset / nightLength
            progress = (0.75f + arcT * 0.5f) % 1f
            dayBlend = 1f - smoothEdge(arcT)
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
        )
    }

    /** Eases the first/last 12% of the arc so twilight fades smoothly instead of snapping. */
    private fun smoothEdge(t: Float): Float {
        val edge = 0.12f
        return when {
            t < edge -> t / edge
            t > 1f - edge -> (1f - t) / edge
            else -> 1f
        }
    }

    fun currentHour24(timeZone: TimeZone = TimeZone.getDefault()): Float {
        val cal = Calendar.getInstance(timeZone)
        return cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f
    }

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
        // zero-length day arc. Both collapse gracefully into [compute]'s existing
        // `dayLength.coerceAtLeast(1f)` floor rather than needing separate handling here.
        val clamped = cosHourAngle.coerceIn(-1.0, 1.0)
        val hourAngle = acos(clamped) // radians

        val hourAngleHours = Math.toDegrees(hourAngle) / 15.0
        // Local solar noon, expressed in this location's *civil clock* hours: local solar time
        // runs ahead of UTC by longitudeDeg/15 hours (east positive), and civil time runs ahead
        // of UTC by utcOffsetHours -- so civil clock time lags solar time by exactly the
        // difference between the two. This is what actually uses longitude and utcOffsetHours;
        // the previous `- utcOffsetHours * 0.0` discarded the offset entirely and pinned every
        // location to a fixed 12:00 solar noon regardless of where in its timezone it sits.
        val solarNoon = 12.0 - longitudeDeg / 15.0 + utcOffsetHours
        val sunrise = (solarNoon - hourAngleHours).coerceIn(0.0, 23.98)
        val sunset = (solarNoon + hourAngleHours).coerceIn(0.02, 24.0)
        return sunrise.toFloat() to sunset.toFloat()
    }

    private const val SYNODIC_MONTH_DAYS = 29.530588853
    // A known new moon: 2000-01-06 18:14 UTC. Any correct reference new moon works equally well
    // since only the *fractional position* within the ~29.53-day cycle matters here.
    private const val REFERENCE_NEW_MOON_EPOCH_MILLIS = 947182440000L

    /**
     * Real lunar phase for the given moment, as a fraction of the ~29.53-day synodic month:
     * 0 = new moon, 0.25 = first quarter, 0.5 = full moon, 0.75 = last quarter, cycling back to
     * 1 = new moon again. Good to within a few hours, which is more than enough for a wallpaper.
     */
    fun moonPhase(epochMillis: Long = System.currentTimeMillis()): Float {
        val daysSinceReference = (epochMillis - REFERENCE_NEW_MOON_EPOCH_MILLIS) / 86_400_000.0
        val cycles = daysSinceReference / SYNODIC_MONTH_DAYS
        val phase = cycles - kotlin.math.floor(cycles)
        return phase.toFloat()
    }
}
