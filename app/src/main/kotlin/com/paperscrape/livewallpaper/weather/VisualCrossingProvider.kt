package com.paperscrape.livewallpaper.weather

import org.json.JSONObject

/**
 * Visual Crossing's Timeline Weather API
 * (https://www.visualcrossing.com/resources/documentation/weather-api/timeline-weather-api/).
 *
 * The second provider. Its free plan covers current conditions, forecast and history at up to
 * 1,000 records a day and **requires an API key** -- there is no anonymous tier, which is the one
 * behavioural difference from Open-Meteo that reaches the UI: without a key the provider reports
 * [WeatherFetchResult.MissingApiKey] and no request is made at all.
 *
 * **No key is compiled into the app for this provider.** Open-Meteo ships one because its terms
 * make a shared key sensible; Visual Crossing's is per-account and metered, so it is the user's to
 * enter and lives only in their own DataStore.
 *
 * **What it reports differently.** Visual Crossing gives one `precip` figure plus a `preciptype`
 * array saying what kind it is, where Open-Meteo splits the millimetres three ways. So
 * [WeatherObservation.showersMm] is left **null** here rather than zero -- this provider does not
 * report showers as a category, and null is what says so. `rainMm` is only filled in when
 * `preciptype` actually names rain; a `precip` of 0.4 with `preciptype: ["snow"]` must not become
 * 0.4 mm of rain.
 */
object VisualCrossingProvider : WeatherProvider {

    override val id: WeatherProviderId = WeatherProviderId.VISUAL_CROSSING

    override val requiresApiKey: Boolean = true

    internal fun requestUrl(latitude: Double, longitude: Double, apiKey: String): String =
        "https://weather.visualcrossing.com/VisualCrossingWebServices/rest/services/timeline/" +
            "$latitude,$longitude?unitGroup=metric&include=current&contentType=json&key=$apiKey"

    override suspend fun fetch(latitude: Double, longitude: Double, apiKey: String): WeatherFetchResult {
        val key = apiKey.trim()
        // Nothing is attempted without a key. A request that is known to be rejected is worse than
        // no request: it costs a round trip and reports back as a network problem.
        if (key.isEmpty()) return WeatherFetchResult.MissingApiKey
        return when (val outcome = WeatherHttp.getJson(requestUrl(latitude, longitude, key))) {
            is HttpOutcome.Error -> WeatherFetchResult.Failed(outcome.failure, id)
            is HttpOutcome.Body -> parse(outcome.json)
                ?.let { WeatherFetchResult.Success(it) }
                ?: WeatherFetchResult.Failed(WeatherFailure.MALFORMED_RESPONSE, id)
        }
    }

    /**
     * Pure, and the only place this provider's response shape is known.
     *
     * `unitGroup=metric` puts `temp` in Celsius, `precip` in millimetres and `snow` in
     * centimetres, matching [WeatherObservation]'s units without conversion.
     */
    internal fun parse(json: String): WeatherObservation? = try {
        val current = JSONObject(json).optJSONObject("currentConditions")
        if (current == null) {
            null
        } else {
            val precipitationMm = current.optionalDouble("precip")
            val types = precipitationTypes(current)
            WeatherObservation(
                temperatureCelsius = current.optionalDouble("temp"),
                cloudCoverPercent = current.optionalInt("cloudcover"),
                precipitationMm = precipitationMm,
                // Attributed, not assumed: only when the provider says rain is what is falling.
                rainMm = if (types.any { it == "rain" || it == "freezingrain" }) precipitationMm else null,
                // Not a category this provider reports. Null, not zero -- see the class comment.
                showersMm = null,
                snowfallCm = current.optionalDouble("snow"),
                condition = condition(
                    icon = current.optString("icon", "").lowercase(),
                    conditions = current.optString("conditions", "").lowercase(),
                    precipitationTypes = types,
                ),
                observedAtMillis = System.currentTimeMillis(),
                source = WeatherProviderId.VISUAL_CROSSING,
            )
        }
    } catch (_: Exception) {
        null
    }

    /** `preciptype` is an array of "rain" / "snow" / "freezingrain" / "ice", or absent. */
    internal fun precipitationTypes(current: JSONObject): List<String> {
        val array = current.optJSONArray("preciptype") ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it, null)?.lowercase() }
    }

    /**
     * Icon slug first, `conditions` text second, `preciptype` last.
     *
     * The icon is the machine-readable field and is what the API documents; the free `icons1` set
     * has no thunder value at all, so the human-readable `conditions` string is the only place a
     * thunderstorm appears unless the caller opted into `icons2`. Reading both is what makes the
     * mapping work on either icon set. `preciptype` is the final say on frozen-versus-liquid,
     * because it is a statement about what is falling rather than a summary.
     */
    internal fun condition(
        icon: String,
        conditions: String,
        precipitationTypes: List<String>,
    ): WeatherCondition = when {
        icon.contains("thunder") || conditions.contains("thunder") -> WeatherCondition.THUNDERSTORM
        precipitationTypes.contains("ice") -> WeatherCondition.HAIL
        precipitationTypes.contains("freezingrain") -> WeatherCondition.FREEZING_RAIN
        // Both kinds falling at once. Named rather than collapsed to one of them, because a scene
        // that has to pick should pick from the measurements, not from a code that hid the mix.
        precipitationTypes.contains("snow") && precipitationTypes.contains("rain") -> WeatherCondition.SLEET
        icon.contains("snow-showers") -> WeatherCondition.SNOW_SHOWERS
        icon.contains("snow") || precipitationTypes.contains("snow") -> WeatherCondition.SNOW
        icon.contains("showers") -> WeatherCondition.SHOWERS
        icon.contains("rain") || precipitationTypes.contains("rain") -> WeatherCondition.RAIN
        icon.contains("fog") -> WeatherCondition.FOG
        icon.contains("partly-cloudy") -> WeatherCondition.PARTLY_CLOUDY
        icon.contains("cloudy") -> WeatherCondition.CLOUDY
        icon.contains("clear") -> WeatherCondition.CLEAR
        // "wind" and anything unrecognised. Not clear skies: this provider is silent on cloud
        // cover in the icon when wind is the headline, and cloudCoverPercent still carries it.
        else -> WeatherCondition.UNKNOWN
    }
}
