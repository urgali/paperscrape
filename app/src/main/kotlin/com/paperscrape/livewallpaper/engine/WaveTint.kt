package com.paperscrape.livewallpaper.engine

/**
 * Where a wave's two papers sit in luma, given the water under them.
 *
 * A wave (v4.28, concept WA3 "Tubo") is two tintable masks laid over the mirror: a **body**, the
 * face under the curling lip, and the **foam** that curls forward off it. Neither carries a colour
 * of its own -- both are derived per frame from the surface they lie on, the way the waterline of
 * v4.26 and the rain of v4.27 are, because the water is a mirror of the sky and any fixed pair of
 * colours is wrong at some hour of some theme.
 *
 * Everything here is pure arithmetic on luma so that `WaveContrastTest` can sweep it over every
 * theme, every five minutes of the clock and all three weathers that draw waves, and measure the
 * same numbers the renderer draws.
 *
 * ### The direction is the cheaper carry, not a fixed side
 *
 * From a surface of luma `l`, reaching `l - gap` costs `gap / l` of the way to black and reaching
 * `l + gap` costs `gap / (255 - l)` of the way to white; below 127.5 white is the shorter road.
 * This is exactly `PaperRenderer.standOffFromSky`'s rule for the rain, and it is here for the same
 * reason: the phase-2 proposal carried the body toward black always, and
 * `WaveContrastTest` measures that this puts the body under 40 of luma in **901 of 2 592
 * situations** -- every one of them at night, where a dark body on dark water stops being a wave
 * and becomes a rock.
 *
 * The foam is always the lighter paper. It sits `foamGap` above the surface and never less than
 * `foamGap` above the body, so on a dark sea where the body itself went light the foam goes
 * lighter still rather than sliding under it and trading places with it.
 *
 * ### Where the surface is so pale that there is no white left
 *
 * [inverted] is the one case the sweep found that the rule above cannot serve: Tundra's ice-water
 * at dawn, where the surface passes `255 - foamGap` and foam above it does not exist. There the
 * papers invert -- the foam goes dark and the body darker still -- which keeps both gaps and keeps
 * their order, and is the only arrangement that does. See `DESIGN_NOTES` for what it looks like.
 */
internal object WaveTint {

    /** Whether the body's cheaper carry from a surface of luma [surfaceLuma] is toward black. */
    fun bodyTowardBlack(surfaceLuma: Float, bodyGap: Float): Boolean =
        bodyGap / surfaceLuma.coerceAtLeast(1f) <= bodyGap / (255f - surfaceLuma).coerceAtLeast(1f)

    /**
     * Whether the papers are inverted: the surface is so pale that foam [foamGap] above it does
     * not exist, so the foam goes dark and the body darker still (Tundra's ice-water at dawn).
     */
    fun inverted(surfaceLuma: Float, foamGap: Float): Boolean = surfaceLuma + foamGap > 255f

    fun bodyLuma(surfaceLuma: Float, bodyGap: Float, foamGap: Float): Float {
        if (inverted(surfaceLuma, foamGap)) return (surfaceLuma - foamGap - bodyGap).coerceAtLeast(0f)
        if (!bodyTowardBlack(surfaceLuma, bodyGap)) {
            val body = surfaceLuma + bodyGap
            // the foam has to fit above the body; if white runs out, the body goes dark instead
            if (body + foamGap <= 255f) return body
        }
        return (surfaceLuma - bodyGap).coerceAtLeast(0f)
    }

    fun foamLuma(surfaceLuma: Float, bodyGap: Float, foamGap: Float): Float {
        if (inverted(surfaceLuma, foamGap)) return surfaceLuma - foamGap
        val body = bodyLuma(surfaceLuma, bodyGap, foamGap)
        return kotlin.math.max(surfaceLuma + foamGap, body + foamGap).coerceAtMost(255f)
    }

    /**
     * The gap actually aimed for at [dayBlend], between the night target and the day one.
     *
     * **The derived gate is a floor, not a target, and this is where the two stop being the same
     * number.** `WaveContrastTest` derives the gate by the v4.22 rule -- halfway between a measured
     * floor (the luma the mirror's own gradient already varies by under one wave) and a measured
     * signal (the gaps of the phase-2 frame the maintainer read as a wave). Halfway is the least
     * that can be told apart from the gradient; it is not the level that was judged to read as a
     * wave, and the phase-3 photographs show the difference: by day, low sun, rain and storm the
     * gate was enough, and at night the three shapes came out "discreet" -- visible, but weaker
     * than a moving sea should be.
     *
     * So the night target is **the signal itself** -- the other end of the derivation, the gap the
     * maintainer actually read as a wave -- and the day target stays at the gate the photographs
     * approved. Nothing here is a new number: both ends were already measured, and what changed is
     * which end the renderer aims at when. The crossfade is [dayBlend], the same value the scene's
     * colours, the car count and the pedestrian count already cross-fade on, so the sea firms up
     * over the length of dusk instead of stepping between two frames.
     *
     * The floor still holds everywhere: the target is never below the gate at any [dayBlend],
     * because the gate is one end of the interpolation.
     */
    fun gapAt(dayBlend: Float, dayGap: Float, nightGap: Float): Float {
        val d = dayBlend.coerceIn(0f, 1f)
        return nightGap + (dayGap - nightGap) * d
    }

    /** `colour` carried toward black or white until its luma reaches `target`. */
    fun carryTo(colour: Int, colourLuma: Float, target: Float): Int {
        if (target <= colourLuma) {
            val t = if (colourLuma <= 0f) 1f else ((colourLuma - target) / colourLuma).coerceIn(0f, 1f)
            return androidx.core.graphics.ColorUtils.blendARGB(colour, 0xFF000000.toInt(), t)
        }
        val t = if (colourLuma >= 255f) 0f else ((target - colourLuma) / (255f - colourLuma)).coerceIn(0f, 1f)
        return androidx.core.graphics.ColorUtils.blendARGB(colour, 0xFFFFFFFF.toInt(), t)
    }
}
