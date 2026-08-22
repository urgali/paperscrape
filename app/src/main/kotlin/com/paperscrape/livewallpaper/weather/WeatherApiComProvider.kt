package com.paperscrape.livewallpaper.weather

import org.json.JSONObject

/**
 * WeatherAPI.com's Realtime API (https://www.weatherapi.com/docs/, `/v1/current.json`).
 *
 * The second provider, replacing Visual Crossing in v3.7. Like it, it **requires an API key** —
 * there is no anonymous tier — so without one it reports [WeatherFetchResult.MissingApiKey] and no
 * request is made at all. **No key is compiled into the app for this provider**: it is per-account
 * and metered, so it is the user's to enter and lives only in their own DataStore.
 *
 * ### Why this one, and why it replaces Visual Crossing
 *
 * The deciding property is that **its condition vocabulary is published as machine-readable JSON**
 * (`https://www.weatherapi.com/docs/conditions.json`, 60 entries). That file is committed as a test
 * fixture, and [WeatherApiComProviderTest] checks this class's table against every code in it. The
 * provider it replaces had no such thing: its icon slugs were mapped from prose documentation and
 * the mapping was never checkable against anything, which is what deferred item **D8** was really
 * about. A parser that cannot be checked without an account is a parser nobody can maintain.
 *
 * Also, from the official pages at the time of writing: signup needs an email and **no payment
 * card**; the free plan allows **100 000 calls a month** and, unlike Open-Meteo's free tier,
 * **permits commercial use** with attribution. Over quota it simply stops returning data. That
 * commercial-use difference is the substantive reason a second provider exists at all — Open-Meteo's
 * free service is licensed for non-commercial use only, and this is the compliant way out for
 * anyone who needs one.
 *
 * OpenWeather was assessed and rejected: its current product line routes current conditions through
 * One Call 3.0, which requires a **credit card** on file even to use the free daily allowance.
 * Requiring a wallpaper's users to register a payment card is not a reasonable ask.
 *
 * ### What it reports differently from Open-Meteo
 *
 * One `precip_mm` figure and no split, so [WeatherObservation.showersMm] is left **null** rather
 * than zero — this provider does not report showers as a measured category, and null is what says
 * so. There is also **no snow measurement in the realtime object** (`snow_cm` exists only in the
 * forecast and history hourly elements), so [WeatherObservation.snowfallCm] stays null and frozen
 * precipitation is carried by the condition code plus `precip_mm`. [WeatherSnapshotMapper] already
 * handles a provider that measures without categorising, which is the same shape Visual Crossing
 * had.
 */
object WeatherApiComProvider : WeatherProvider {

    override val id: WeatherProviderId = WeatherProviderId.WEATHER_API_COM

    override val requiresApiKey: Boolean = true

    internal fun requestUrl(latitude: Double, longitude: Double, apiKey: String): String =
        "https://api.weatherapi.com/v1/current.json?key=$apiKey&q=$latitude,$longitude&aqi=no"

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
     * The API answers in metric and imperial at once, so `temp_c` and `precip_mm` are read directly
     * and match [WeatherObservation]'s units without conversion.
     */
    internal fun parse(json: String): WeatherObservation? = try {
        val current = JSONObject(json).optJSONObject("current")
        if (current == null) {
            null
        } else {
            val code = current.optJSONObject("condition")?.optionalInt("code")
            val resolved = condition(code)
            val precipitationMm = current.optionalDouble("precip_mm")
            WeatherObservation(
                temperatureCelsius = current.optionalDouble("temp_c"),
                cloudCoverPercent = current.optionalInt("cloud"),
                precipitationMm = precipitationMm,
                // Attributed, not assumed: `precip_mm` counts whatever is falling, so it is only
                // rain when the code says the falling thing is rain. 0.4 mm under a snow code must
                // not become 0.4 mm of rain.
                rainMm = if (resolved.isRainy) precipitationMm else null,
                // Not a measured category here; see the class comment. Null, not zero.
                showersMm = null,
                // The realtime object carries no snow depth at all -- `snow_cm` is forecast/history
                // only. Null says "not reported", which is what the mapper needs to hear.
                snowfallCm = null,
                condition = resolved,
                observedAtMillis = System.currentTimeMillis(),
                source = WeatherProviderId.WEATHER_API_COM,
            )
        }
    } catch (_: Exception) {
        null
    }

    /**
     * The published condition code, normalised.
     *
     * **Every branch below is derived from `conditions.json`, not from memory**, and
     * `WeatherApiComProviderTest` walks the committed copy of that file and asserts this function
     * answers something defensible for all 60 codes — no code may fall through to
     * [WeatherCondition.UNKNOWN] unless it genuinely describes no sky state this scene can draw.
     *
     * Ordering matters. Thunder codes (1087, 1273..1282) win outright, including the two that also
     * name snow: the scene draws one thing at a time and a thunderstorm is the headline. Frozen
     * precipitation is then resolved before liquid, so "sleet" does not fall through to rain.
     */
    internal fun condition(code: Int?): WeatherCondition = when (code) {
        null -> WeatherCondition.UNKNOWN

        // Thunder, in every form the vocabulary has.
        1087, 1273, 1276, 1279, 1282 -> WeatherCondition.THUNDERSTORM

        // Ice pellets are this vocabulary's hail.
        1237, 1261, 1264 -> WeatherCondition.HAIL

        // Freezing drizzle and freezing rain: liquid that freezes on contact, its own category.
        1072, 1168, 1171, 1198, 1201 -> WeatherCondition.FREEZING_RAIN

        // Sleet -- rain and snow together, which is exactly what the mixed category is for.
        1069, 1204, 1207, 1249, 1252 -> WeatherCondition.SLEET

        // Snow showers, kept apart from steady snow the way Open-Meteo's codes are.
        1255, 1258 -> WeatherCondition.SNOW_SHOWERS

        // Steady or patchy snow, plus the two wind-driven forms.
        1066, 1114, 1117, 1210, 1213, 1216, 1219, 1222, 1225 -> WeatherCondition.SNOW

        // Rain showers.
        1240, 1243, 1246 -> WeatherCondition.SHOWERS

        // Drizzle, distinct from rain: the scene draws it lighter.
        1150, 1153 -> WeatherCondition.DRIZZLE

        // Rain, patchy through heavy. 1063 is "patchy rain nearby", which is still rain in view.
        1063, 1180, 1183, 1186, 1189, 1192, 1195 -> WeatherCondition.RAIN

        // Anything that obscures the air rather than falling through it. Mist, fog and the whole
        // dust/smoke/haze family read the same way to this scene: visibility, not precipitation.
        1030, 1135, 1147, 1012, 1015, 1018, 1021, 1024, 1027, 1033, 1036, 1039, 1042, 1045, 1048 ->
            WeatherCondition.FOG

        1009 -> WeatherCondition.CLOUDY
        1006 -> WeatherCondition.CLOUDY
        1003 -> WeatherCondition.PARTLY_CLOUDY
        1000 -> WeatherCondition.CLEAR

        // A code this vocabulary does not contain. Not clear skies -- `cloud` still carries the
        // cover, and the mapper reads it.
        else -> WeatherCondition.UNKNOWN
    }
}
