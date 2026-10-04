package com.paperscrape.livewallpaper.engine

/**
 * The engine's [SunPositionCalculator.DayPhase], recomputed only when what it is computed from
 * changes.
 *
 * The wallpaper asks for the day phase once a frame. Its inputs are the scene's hour, which
 * [SunPositionCalculator.currentHour24] holds for a whole minute, today's sunrise and sunset, which
 * change a few times a day, and the moon's phase, which as a `Float` moves several times a second.
 * Until v5.10B every frame computed a new [SunPositionCalculator.DayPhase] from scratch: one object a
 * frame on the render thread (v5.10A). Here a change of the hour or of the sun's times computes a new
 * one, exactly as [SunPositionCalculator.compute] would, and a change of the moon alone is written
 * into the phase already held ([SunPositionCalculator.DayPhase.moonPhase] is a `var` for this reason
 * only). Either way what is returned equals `compute(hour24, sunriseHour, sunsetHour, moonPhase)`
 * field for field; `DayPhaseCacheTest` holds it to that over random sequences of inputs.
 *
 * **The instance it returns is rewritten on a later call**, so a caller must not keep it past the
 * frame it asked for. The renderer does not: it reads the phase while drawing and holds no
 * reference to it. One cache per engine, used from the one thread that draws that engine.
 */
internal class DayPhaseCache {

    private var phase: SunPositionCalculator.DayPhase? = null
    private var hourBits = 0
    private var sunriseBits = 0
    private var sunsetBits = 0

    /** The day phase for these inputs; see the class comment for what may be kept of it. */
    fun at(hour24: Float, sunriseHour: Float, sunsetHour: Float, moonPhase: Float): SunPositionCalculator.DayPhase {
        val held = phase
        if (held != null && hour24.toRawBits() == hourBits && sunriseHour.toRawBits() == sunriseBits &&
            sunsetHour.toRawBits() == sunsetBits
        ) {
            held.moonPhase = moonPhase
            return held
        }
        val fresh = SunPositionCalculator.compute(
            hour24 = hour24,
            sunriseHour = sunriseHour,
            sunsetHour = sunsetHour,
            moonPhase = moonPhase,
        )
        phase = fresh
        hourBits = hour24.toRawBits()
        sunriseBits = sunriseHour.toRawBits()
        sunsetBits = sunsetHour.toRawBits()
        return fresh
    }
}
