package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.weather.LiveWeatherSnapshot

/**
 * What falls in a frame and how it looks, before the contrast rule carries its colour off the sky
 * (`PaperRenderer.drawPrecipitation`, which draws by it): pure, so the JVM suite runs it on a forecast.
 *
 * - **What falls** ([kind], [intensity]): while Live Weather drives the scene ([live] non-null) the forecast's rain or snow
 *   and amount, whatever the theme's own precipitation says; otherwise the theme's, while it is visible and above 0 %.
 * - **The blood rain** ([blood], v5.16, decision 78): rain -- the theme's or the forecast's alike -- while *Blood rain* is
 *   on, red ([colour]: [PaperRenderer.BLOOD_RAIN_NIGHT] to [PaperRenderer.BLOOD_RAIN_DAY] on the day's blend) and twice as
 *   thick ([strokeFactor]); snow stays snow, in the theme's snow colours. The maintainer asked whether it rains blood under
 *   Live Weather too (2026-10-23): it does, by this one rule, and `BloodRainLiveWeatherTest` makes it rain from a forecast.
 *
 * Integers and floats only: it runs on every frame and allocates nothing.
 */
internal object PrecipitationLook {

    /** Nothing falls. */
    const val NONE = 0
    const val RAIN = 1
    const val SNOW = 2

    /** What falls: [RAIN], [SNOW] or [NONE]. */
    fun kind(precip: PrecipitationConfig, live: LiveWeatherSnapshot?): Int {
        if (live != null) {
            return when (live.precipitationType) {
                null -> NONE
                PrecipitationType.RAIN -> RAIN
                PrecipitationType.SNOW -> SNOW
            }
        }
        if (!precip.visible || precip.intensity <= 0f) return NONE
        return if (precip.type == PrecipitationType.RAIN) RAIN else SNOW
    }

    /** How much of it: the forecast's while Live Weather drives the scene, else the theme's. */
    fun intensity(precip: PrecipitationConfig, live: LiveWeatherSnapshot?): Float =
        live?.precipitationIntensity ?: precip.intensity

    /** Whether it rains blood: rain, with *Blood rain* on. */
    fun blood(kind: Int, c: SceneCustomization): Boolean = kind == RAIN && c.bloodRainEnabled

    /**
     * Its colour at [dayBlend], before the contrast rule: the blood rain's red, the theme's rain or the theme's snow -- the
     * night's blended toward the day's, by the truncating arithmetic of `ColorUtils.blendARGB` the renderer used before.
     */
    fun colour(kind: Int, blood: Boolean, precip: PrecipitationConfig, dayBlend: Float): Int = when {
        blood -> SceneColour.blendArgb(PaperRenderer.BLOOD_RAIN_NIGHT, PaperRenderer.BLOOD_RAIN_DAY, dayBlend.coerceIn(0f, 1f))
        kind == RAIN -> SceneColour.blendArgb(precip.rainColorNight, precip.rainColorDay, dayBlend.coerceIn(0f, 1f))
        else -> SceneColour.blendArgb(precip.snowColorNight, precip.snowColorDay, dayBlend.coerceIn(0f, 1f))
    }

    /** How much thicker than the rain's own a drop is drawn: [PaperRenderer.BLOOD_RAIN_THICKER] for the blood rain. */
    fun strokeFactor(blood: Boolean): Float = if (blood) PaperRenderer.BLOOD_RAIN_THICKER else 1f
}
