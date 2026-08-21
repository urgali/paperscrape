package com.paperscrape.livewallpaper.weather

import com.paperscrape.livewallpaper.engine.PrecipitationType

/**
 * A fetched snapshot of real conditions at one location, in the renderer's vocabulary.
 *
 * [com.paperscrape.livewallpaper.engine.PaperRenderer] is the only consumer and never sees a
 * provider's response shape, a WMO code or an icon slug -- the same separation
 * [com.paperscrape.livewallpaper.location.DeviceLocationFix] keeps between "where is the device"
 * and what that is used for.
 */
data class LiveWeatherSnapshot(
    val precipitationType: PrecipitationType?, // null = no precipitation right now
    val precipitationIntensity: Float, // 0f..1f, only meaningful when precipitationType != null
    val cloudCoverFraction: Float, // 0f..1f
    val isThunderstorm: Boolean,
    val fetchedAtMillis: Long,
)

/**
 * The one place a [WeatherObservation] becomes something the scene can draw.
 *
 * **Measurements first, summary code second**, which is v2.13's rule and is preserved exactly:
 *
 * 1. `snowfall > 0` -> snow;
 * 2. `rain > 0 || showers > 0` -> rain;
 * 3. something is measurably falling but no field said which kind: the code picks between snow and
 *    rain, defaulting to rain;
 * 4. **every measurement the provider reported is zero: nothing is falling.** The code does not get
 *    a vote here, and that is the whole of the v2.14 fix -- see below;
 * 5. the provider reported no measurements at all: only then does the code decide whether anything
 *    falls.
 *
 * The principle is one sentence: **a measurement, where one exists, is the answer.** A code is an
 * interpretation and millimetres are an observation. They disagree in both directions and each
 * direction has been shipped as a bug:
 *
 * - v2.12 read only the code, so a shower that had just stopped -- millimetres still above zero
 *   under an overcast code -- drew a dry, fully clouded sky. Steps 1-3 are that fix.
 * - v2.13 added the measurements but left the code as an *unconditional* fallback, so the reverse
 *   became possible and was measured on a clean emulator at 13:15 on 2026-08-21 for Florence
 *   (43.77925, 11.24626): Open-Meteo returned `weather_code: 80` ("slight rain showers") with
 *   `precipitation: 0.00, rain: 0.00, showers: 0.00, snowfall: 0.00` and `cloud_cover: 100`. Four
 *   measurements said nothing was falling, the code outvoted all four, and the wallpaper rained.
 *   Fifteen minutes earlier the same coordinates had returned `weather_code: 3` with the same four
 *   zeroes -- the code flips between "overcast" and "showers" over a dry hour, which is exactly
 *   why it cannot be allowed to overrule a measurement.
 *
 * Step 3 guesses rain rather than snow because snow always arrives with either a snow code or a
 * snowfall reading, and a wallpaper that snows in August on a rounding artefact is worse than one
 * that rains.
 *
 * Two assumptions are explicitly **not** made, and both are testable here: cloud cover above zero
 * does not imply precipitation, and `rain == 0` does not imply nothing is falling -- a provider
 * that reports showers separately can have rain at zero during a downpour.
 */
object WeatherSnapshotMapper {

    /**
     * Millimetres per hour that map to full intensity.
     *
     * 8 mm/h already reads as a heavy downpour; capping here rather than at some much higher
     * "extreme storm" figure keeps ordinary readings spread across the slider's range instead of
     * clustered near zero.
     */
    private const val FULL_INTENSITY_MM = 8.0

    /** Any precipitation at all is drawn at least this heavily, so it is visible as weather. */
    private const val MINIMUM_VISIBLE_INTENSITY = 0.15f

    fun toSnapshot(observation: WeatherObservation): LiveWeatherSnapshot {
        val snowfall = observation.snowfallCm ?: 0.0
        val rain = observation.rainMm ?: 0.0
        val showers = observation.showersMm ?: 0.0
        val total = observation.precipitationMm ?: 0.0

        val hasMeasuredSnow = snowfall > 0.0
        val hasMeasuredRain = rain > 0.0 || showers > 0.0
        val hasMeasuredPrecipitation = total > 0.0 || hasMeasuredRain || hasMeasuredSnow

        // Whether the provider said anything at all about how much is falling. Null means "not
        // reported" and 0.0 means "reported, and it is none" -- the distinction the whole model is
        // built to preserve, and the one this decision turns on.
        val reportedAnyMeasurement = observation.precipitationMm != null ||
            observation.rainMm != null ||
            observation.showersMm != null ||
            observation.snowfallCm != null

        val precipitationType = when {
            hasMeasuredSnow -> PrecipitationType.SNOW
            hasMeasuredRain -> PrecipitationType.RAIN
            // Something is falling and no sub-field said which kind. Here the code is the only
            // thing that can answer the *kind* question, so here it is asked.
            hasMeasuredPrecipitation -> if (observation.condition.isSnowy) {
                PrecipitationType.SNOW
            } else {
                PrecipitationType.RAIN
            }
            // Measurements exist and every one of them is zero. Nothing is falling, whatever the
            // summary code claims. This branch is the fix.
            reportedAnyMeasurement -> null
            // Nothing was measured at all, so the code is all there is.
            observation.condition.isSnowy -> PrecipitationType.SNOW
            observation.condition.isRainy -> PrecipitationType.RAIN
            else -> null
        }

        // The sum when it is there, the parts when it is not. `precipitation` is rain + showers +
        // snowfall's water equivalent, so it is the right figure whenever the provider reports it.
        val measuredMm = if (total > 0.0) total else rain + showers
        val intensity = (measuredMm / FULL_INTENSITY_MM).coerceIn(0.0, 1.0).toFloat()

        return LiveWeatherSnapshot(
            precipitationType = precipitationType,
            precipitationIntensity = if (precipitationType != null) {
                intensity.coerceAtLeast(MINIMUM_VISIBLE_INTENSITY)
            } else {
                0f
            },
            cloudCoverFraction = ((observation.cloudCoverPercent ?: 0) / 100f).coerceIn(0f, 1f),
            isThunderstorm = observation.condition == WeatherCondition.THUNDERSTORM,
            fetchedAtMillis = observation.observedAtMillis,
        )
    }
}
