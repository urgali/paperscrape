package com.paperscrape.livewallpaper.weather

import org.json.JSONObject

/**
 * Open-Meteo (https://open-meteo.com).
 *
 * The default Live Weather provider, because its free tier needs no key at
 * all for non-commercial use -- which is what makes "works out of the box, a key is an optional
 * upgrade" possible. A key routes to the higher-limit `customer-api.open-meteo.com` endpoint; no
 * key uses the free `api.open-meteo.com` one. Neither is a failure state.
 *
 * It is also the only one of the three that splits precipitation into rain, showers and snowfall,
 * which is why [WeatherObservation] has room for the split at all.
 */
object OpenMeteoProvider : WeatherProvider {

    override val id: WeatherProviderId = WeatherProviderId.OPEN_METEO

    override val requiresApiKey: Boolean = false

    /**
     * The user's key from Settings, or null for "use the free keyless endpoint" -- which is never
     * a hard failure for this provider.
     *
     * **There is no second source.** A blank user key is the free endpoint, never a key baked in
     * at build time: a key in the build ships as a plain string in `classes.dex` of every
     * published APK, and the app neither ships one nor proxies one (app/build.gradle.kts says the
     * whole of why). A user who enters their own key reaches the higher-limit endpoint.
     */
    internal fun resolveApiKey(userApiKey: String): String? =
        userApiKey.trim().ifBlank { null }

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
            val perHour = perHourFactor(current)
            WeatherObservation(
                temperatureCelsius = current.optionalDouble("temperature_2m"),
                cloudCoverPercent = current.optionalInt("cloud_cover"),
                precipitationMm = current.optionalDouble("precipitation")?.times(perHour),
                rainMm = current.optionalDouble("rain")?.times(perHour),
                showersMm = current.optionalDouble("showers")?.times(perHour),
                snowfallCm = current.optionalDouble("snowfall")?.times(perHour),
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
     * What turns `current`'s amounts into **amounts per hour**, the unit [WeatherObservation] and
     * [WeatherSnapshotMapper] read: 3600 over the block's `interval` in seconds.
     *
     * Open-Meteo's `current` block is 15-minutely, `"interval": 900`, and its `precipitation`, `rain`,
     * `showers` and `snowfall` are the totals of those fifteen minutes (open-meteo.com/en/docs:
     * *"aggregated metrics such as precipitation reflect the total from the previous 15 minutes"*), while
     * WeatherAPI.com's `precip_mm` and OpenWeather's `rain.1h` are an hour's: read as an hour's, the same rain
     * from Open-Meteo would draw about four times lighter. A block that states no interval, or one outside a
     * second to an hour, is read as an hour's.
     */
    private fun perHourFactor(current: JSONObject): Double {
        val seconds = current.optionalDouble("interval") ?: return 1.0
        return if (seconds in 1.0..SECONDS_PER_HOUR) SECONDS_PER_HOUR / seconds else 1.0
    }

    private const val SECONDS_PER_HOUR = 3600.0

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
