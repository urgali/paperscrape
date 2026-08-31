package com.paperscrape.livewallpaper.weather

import com.paperscrape.livewallpaper.BuildConfig
import org.json.JSONObject

/**
 * Open-Meteo (https://open-meteo.com).
 *
 * The original Live Weather provider and still the default, because its free tier needs no key at
 * all for non-commercial use -- which is what makes "works out of the box, a key is an optional
 * upgrade" possible. A key routes to the higher-limit `customer-api.open-meteo.com` endpoint; no
 * key uses the free `api.open-meteo.com` one. Neither is a failure state.
 *
 * It is also the only one of the two that splits precipitation into rain, showers and snowfall,
 * which is why [WeatherObservation] has room for the split at all.
 */
object OpenMeteoProvider : WeatherProvider {

    override val id: WeatherProviderId = WeatherProviderId.OPEN_METEO

    override val requiresApiKey: Boolean = false

    /**
     * A user-entered key (from Settings, always wins if present) or the build's own baked-in key
     * (from [BuildConfig.OPENMETEO_API_KEY], see app/build.gradle.kts). Null means "use the free
     * keyless endpoint", which is never a hard failure for this provider.
     */
    internal fun resolveApiKey(userApiKey: String): String? =
        userApiKey.trim().ifBlank { BuildConfig.OPENMETEO_API_KEY }.trim().ifBlank { null }

    internal fun requestUrl(latitude: Double, longitude: Double, userApiKey: String): String {
        val apiKey = resolveApiKey(userApiKey)
        val host = if (apiKey != null) "customer-api.open-meteo.com" else "api.open-meteo.com"
        val keyParam = if (apiKey != null) "&apikey=${WeatherRequest.key(apiKey)}" else ""
        return "https://$host/v1/forecast" +
            "?latitude=${WeatherRequest.coordinate(latitude)}" +
            "&longitude=${WeatherRequest.coordinate(longitude)}" +
            "&current=temperature_2m,precipitation,rain,showers,snowfall,weather_code,cloud_cover" +
            "&timezone=auto$keyParam"
    }

    override suspend fun fetch(latitude: Double, longitude: Double, apiKey: String): WeatherFetchResult =
        when (val outcome = WeatherHttp.getJson(requestUrl(latitude, longitude, apiKey))) {
            is HttpOutcome.Error -> WeatherFetchResult.Failed(outcome.failure, id)
            is HttpOutcome.Body -> parse(outcome.json)
                ?.let { WeatherFetchResult.Success(it) }
                ?: WeatherFetchResult.Failed(WeatherFailure.MALFORMED_RESPONSE, id)
        }

    /**
     * Pure, so every field's handling is pinned by a test against a real response body rather than
     * by a network call.
     *
     * Absent fields stay null. `optDouble` with a NaN default is how a missing `showers` is told
     * apart from `"showers": 0.0`: the first must not be read as "no showers are falling", because
     * the customer endpoint can omit the sub-fields entirely and
     * [WeatherSnapshotMapper] falls back differently in that case.
     */
    internal fun parse(json: String): WeatherObservation? = try {
        val current = JSONObject(json).optJSONObject("current")
        if (current == null) {
            null
        } else {
            WeatherObservation(
                temperatureCelsius = current.optionalDouble("temperature_2m"),
                cloudCoverPercent = current.optionalInt("cloud_cover"),
                precipitationMm = current.optionalDouble("precipitation"),
                rainMm = current.optionalDouble("rain"),
                showersMm = current.optionalDouble("showers"),
                snowfallCm = current.optionalDouble("snowfall"),
                condition = current.optionalInt("weather_code")
                    ?.let(::conditionForWmoCode)
                    ?: WeatherCondition.UNKNOWN,
                observedAtMillis = System.currentTimeMillis(),
                source = WeatherProviderId.OPEN_METEO,
            )
        }
    } catch (_: Exception) {
        null
    }

    /**
     * WMO weather-interpretation codes (https://open-meteo.com/en/docs) to [WeatherCondition].
     *
     * Written out as ranges rather than a table because the code space is contiguous by category,
     * and a range says which category a new code would fall into. Note 56/57 (freezing drizzle)
     * and 66/67 (freezing rain) map to [WeatherCondition.FREEZING_RAIN] rather than to rain: the
     * scene draws them as rain either way, but the observation is not the place to lose that.
     */
    internal fun conditionForWmoCode(code: Int): WeatherCondition = when (code) {
        0 -> WeatherCondition.CLEAR
        1, 2 -> WeatherCondition.PARTLY_CLOUDY
        3 -> WeatherCondition.CLOUDY
        45, 48 -> WeatherCondition.FOG
        51, 53, 55 -> WeatherCondition.DRIZZLE
        56, 57, 66, 67 -> WeatherCondition.FREEZING_RAIN
        61, 63, 65 -> WeatherCondition.RAIN
        71, 73, 75 -> WeatherCondition.SNOW
        77 -> WeatherCondition.SNOW // snow grains
        80, 81, 82 -> WeatherCondition.SHOWERS
        85, 86 -> WeatherCondition.SNOW_SHOWERS
        95, 96, 99 -> WeatherCondition.THUNDERSTORM
        else -> WeatherCondition.UNKNOWN
    }
}

/** `null` when the key is absent or not a number, rather than a default that reads as a reading. */
internal fun JSONObject.optionalDouble(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    val value = optDouble(key, Double.NaN)
    return if (value.isNaN()) null else value
}

/** As [optionalDouble], for integers. */
internal fun JSONObject.optionalInt(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    val value = optDouble(key, Double.NaN)
    return if (value.isNaN()) null else value.toInt()
}
