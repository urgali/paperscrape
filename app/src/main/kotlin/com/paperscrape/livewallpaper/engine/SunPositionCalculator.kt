package com.paperscrape.livewallpaper.engine

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

    /**
     * The local civil hour as a decimal, quantised to the minute -- `HOUR_OF_DAY + MINUTE / 60`.
     *
     * Called once per frame by the render loop, which is why it does not use `Calendar`. Both
     * `Calendar.getInstance(zone)` and the `TimeZone.getDefault()` that fed it allocate -- the
     * latter hands back a defensive clone -- so at roughly 30 frames a second this was two
     * objects a frame produced for a value that only changes 1,440 times a day.
     *
     * The result is cached for the minute it belongs to and recomputed when the minute rolls
     * over, so the zone is still re-read every minute and a DST transition or a change of device
     * time zone is picked up as promptly as it was before. `TimeZone.getOffset(instant)` -- not
     * `rawOffset` -- is what makes that true, for the same reason the sunrise/sunset caller
     * documents.
     *
     * Synchronised because the two cache fields are only meaningful together. The GPU renderer
     * gave each wallpaper engine its own render thread, and a process can host two engines at
     * once, so this is genuinely called from more than one thread; an interleaved read could
     * otherwise pair one caller's stamp with another's hour and hold a stale time for a whole
     * minute. It runs once per frame, so the monitor is uncontended in every realistic case.
     */
    @Synchronized
    fun currentHour24(): Float {
        val nowMillis = System.currentTimeMillis()
        // Offsets are a whole number of minutes, so a UTC minute boundary is a local one too and
        // this stamp changes exactly when the returned value can.
        val minuteStamp = Math.floorDiv(nowMillis, MILLIS_PER_MINUTE)
        if (minuteStamp != cachedMinuteStamp) {
            cachedHour24 = hourAt(nowMillis, TimeZone.getDefault())
            cachedMinuteStamp = minuteStamp
        }
        return cachedHour24
    }

    /**
     * The same value [currentHour24] returns, for an explicit instant and zone and with no cache.
     *
     * Split out so the arithmetic can be tested directly against `Calendar`, which is the
     * property that matters: this replaced a `Calendar` and has to agree with it exactly, at
     * every instant, in every zone, across DST transitions.
     */
    fun hourAt(epochMillis: Long, timeZone: TimeZone): Float {
        val localMillis = epochMillis + timeZone.getOffset(epochMillis)
        val minuteOfDay = Math.floorMod(Math.floorDiv(localMillis, MILLIS_PER_MINUTE), MINUTES_PER_DAY).toInt()
        // Kept as `hour + minute / 60f`, the exact expression the Calendar version returned, so
        // the float result is bit-identical rather than merely close.
        return minuteOfDay / 60 + (minuteOfDay % 60) / 60f
    }

    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MINUTES_PER_DAY = 1_440L

    private var cachedMinuteStamp = Long.MIN_VALUE
    private var cachedHour24 = 0f

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
