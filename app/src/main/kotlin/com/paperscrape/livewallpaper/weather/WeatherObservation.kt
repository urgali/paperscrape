package com.paperscrape.livewallpaper.weather

/**
 * What a provider says the sky is doing, in this app's own vocabulary rather than any provider's.
 *
 * This is the normalisation boundary: Open-Meteo's WMO integers and WeatherAPI.com's numeric
 * condition codes both arrive here, and nothing downstream ever sees either. Every field
 * is nullable because "the provider did not report this" and "the provider reported zero" are
 * different facts and the mapping in [WeatherSnapshotMapper] depends on telling them apart -- a
 * provider that omits `showers` must not be read as one reporting no showers.
 */
data class WeatherObservation(
    /** Degrees Celsius. Null when the provider did not report it. */
    val temperatureCelsius: Double? = null,
    /** 0..100. Null when not reported. */
    val cloudCoverPercent: Int? = null,
    /** Total precipitation in millimetres, however it is made up. Null when not reported. */
    val precipitationMm: Double? = null,
    /** The rain part of [precipitationMm], where the provider splits it out. */
    val rainMm: Double? = null,
    /**
     * The showers part, where the provider reports showers as a category of their own.
     *
     * Open-Meteo does; WeatherAPI.com does not, and leaves this null rather than zero. A shower
     * that reports `rain: 0.0` with the millimetres in `showers` is the exact case that made a
     * Florence downpour render as a dry hour before v2.13, so the distinction is load-bearing.
     */
    val showersMm: Double? = null,
    /** Snowfall in centimetres. Null when not reported. */
    val snowfallCm: Double? = null,
    /** The provider's own summary of conditions, normalised. */
    val condition: WeatherCondition = WeatherCondition.UNKNOWN,
    /** When the provider says these conditions were observed, in epoch millis. */
    val observedAtMillis: Long,
    /** Which provider produced this. Carried so a failure or an oddity can be attributed. */
    val source: WeatherProviderId,
)

/**
 * A provider's summary code, normalised.
 *
 * Deliberately coarse: it exists to say *what kind of weather* when no measurement does, and the
 * renderer only distinguishes rain, snow and thunder. Anything finer would be a distinction the
 * scene cannot draw. [UNKNOWN] is a real answer -- a provider that reports only measurements is
 * not thereby reporting clear skies.
 */
enum class WeatherCondition {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    SHOWERS,
    FREEZING_RAIN,
    SLEET,
    SNOW,
    SNOW_SHOWERS,
    HAIL,
    THUNDERSTORM,
    UNKNOWN,
    ;

    /** True for the codes that mean rain is falling, showers and drizzle included. */
    val isRainy: Boolean
        get() = this == DRIZZLE || this == RAIN || this == SHOWERS || this == FREEZING_RAIN ||
            this == THUNDERSTORM

    /** True for the codes that mean frozen precipitation, sleet and hail included. */
    val isSnowy: Boolean
        get() = this == SNOW || this == SNOW_SHOWERS || this == SLEET || this == HAIL
}
