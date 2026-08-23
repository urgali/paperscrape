package com.paperscrape.livewallpaper.weather

import org.json.JSONObject

/**
 * OpenWeather's **Current Weather Data** API
 * (https://docs.openweather.co.uk/current, `/data/2.5/weather`).
 *
 * The third provider, added in v3.8 alongside [WeatherApiComProvider]. Like it, it **requires an API
 * key** — there is no anonymous tier — so without one it reports [WeatherFetchResult.MissingApiKey]
 * and no request is made. **No key is compiled into the app**: it is per-account and metered, so it
 * is the user's to enter and lives only in their own DataStore.
 *
 * ### Why `/data/2.5/weather` and not One Call
 *
 * This app needs one thing: the conditions at one point, right now. `/data/2.5/weather` is exactly
 * that — one call, one small object — and it is on the plain free tier, which registers with an
 * email address and no payment card. **One Call requires a payment card on file** even to use its
 * free daily allowance, which is not a reasonable thing to ask of a wallpaper's users, and
 * everything it adds over this endpoint (minutely, hourly, daily, alerts, history) is data this
 * scene has no use for. That was the reason OpenWeather was rejected outright in v3.7; using the
 * simpler endpoint is what makes it viable now.
 *
 * ### Why its condition ids are the easiest of the three to map
 *
 * They are **structured**, not enumerated. The hundreds digit is the category —
 * 2xx thunderstorm, 3xx drizzle, 5xx rain, 6xx snow, 7xx atmosphere, 800 clear, 80x clouds — so the
 * mapping below is a `when` over `id / 100` with a handful of named exceptions, and an id
 * OpenWeather adds later still lands in the right group instead of falling through to
 * [WeatherCondition.UNKNOWN]. WeatherAPI's flat 60-value table has no such property, which is why
 * that provider needs a per-code list and this one does not.
 *
 * The exceptions are real and are what a group-only mapping would get wrong:
 *
 *  - **511** is `freezing rain` and lives in the 5xx *Rain* group, not with the frozen codes.
 *  - **611–613** are sleet and **615–616** are rain-and-snow, all inside the 6xx *Snow* group;
 *    collapsing them to snow would lose the mix the scene can draw.
 *  - **520–531** and **620–622** are the shower forms of rain and snow, which this project keeps
 *    apart from steady precipitation exactly as Open-Meteo's own codes do.
 *
 * ### Units, and the one trap in them
 *
 * `units=metric` puts `main.temp` in Celsius. **`rain.1h` and `snow.1h` are documented as always in
 * mm/h regardless of `units`** — so the snow figure is millimetres, while
 * [WeatherObservation.snowfallCm] is centimetres. It is divided by ten here. Reading it straight
 * through would make every snowfall ten times deeper than reported, which is the sort of error that
 * looks like a rendering bug for a long time before anyone suspects the parser.
 */
object OpenWeatherProvider : WeatherProvider {

    override val id: WeatherProviderId = WeatherProviderId.OPEN_WEATHER

    override val requiresApiKey: Boolean = true

    internal fun requestUrl(latitude: Double, longitude: Double, apiKey: String): String =
        "https://api.openweathermap.org/data/2.5/weather" +
            "?lat=$latitude&lon=$longitude&units=metric&appid=$apiKey"

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
     * A body without a usable `weather[0].id` is malformed rather than "unknown conditions": this
     * endpoint answers errors with the same 200-shaped envelope carrying `cod` and `message`, and
     * treating one of those as a clear sky would put a sunny scene on screen because the key was
     * rejected.
     */
    internal fun parse(json: String): WeatherObservation? = try {
        val root = JSONObject(json)
        val weather = root.optJSONArray("weather")?.optJSONObject(0)
        val code = weather?.optionalInt("id")
        if (code == null) {
            null
        } else {
            val main = root.optJSONObject("main")
            // Absent objects, not zeroed ones: no `rain` key means "not reported", which is a
            // different fact from 0.0 and the one the mapper depends on.
            val rainMm = root.optJSONObject("rain")?.optionalDouble("1h")
            val snowMm = root.optJSONObject("snow")?.optionalDouble("1h")
            val resolved = condition(code)
            WeatherObservation(
                temperatureCelsius = main?.optionalDouble("temp"),
                cloudCoverPercent = root.optJSONObject("clouds")?.optionalInt("all"),
                // The total, from whichever of the two the provider reported. Both absent stays
                // null rather than becoming zero.
                precipitationMm = if (rainMm == null && snowMm == null) null else (rainMm ?: 0.0) + (snowMm ?: 0.0),
                rainMm = rainMm,
                // Not a measured category here: the shower forms are a condition id, not a separate
                // figure. Null, not zero -- the same shape the other keyed provider has.
                showersMm = null,
                // **mm -> cm.** `snow.1h` is millimetres whatever `units` says; see the class comment.
                snowfallCm = snowMm?.let { it / 10.0 },
                condition = resolved,
                observedAtMillis = System.currentTimeMillis(),
                source = WeatherProviderId.OPEN_WEATHER,
            )
        }
    } catch (_: Exception) {
        null
    }

    /**
     * The published condition id, normalised.
     *
     * Grouped by the hundreds digit with the exceptions named first, so the ordering *is* the
     * specification: anything the exceptions do not claim falls to its group, and an id in no known
     * group is [WeatherCondition.UNKNOWN] rather than a guess.
     */
    internal fun condition(code: Int?): WeatherCondition = when {
        code == null -> WeatherCondition.UNKNOWN

        // -- exceptions, which a group-only mapping would get wrong ---------------------------

        // Freezing rain sits in the Rain group, not with the frozen codes.
        code == 511 -> WeatherCondition.FREEZING_RAIN

        // Sleet and rain-and-snow sit in the Snow group and are neither plain snow nor plain rain.
        code in 611..616 -> WeatherCondition.SLEET

        // The shower forms of each, kept apart from steady precipitation.
        code in 520..531 -> WeatherCondition.SHOWERS
        code in 620..622 -> WeatherCondition.SNOW_SHOWERS

        // -- groups -----------------------------------------------------------------------------

        code / 100 == 2 -> WeatherCondition.THUNDERSTORM
        code / 100 == 3 -> WeatherCondition.DRIZZLE
        code / 100 == 5 -> WeatherCondition.RAIN
        code / 100 == 6 -> WeatherCondition.SNOW

        // Mist, fog, haze, smoke, dust, sand, ash, squall, tornado. All obscure the air rather
        // than falling through it, which is the only distinction this scene draws.
        code / 100 == 7 -> WeatherCondition.FOG

        code == 800 -> WeatherCondition.CLEAR

        // 801 is "few clouds: 11-25%", which reads as a mostly clear sky with something in it;
        // 802-804 are progressively solid. `cloudCoverPercent` carries the exact figure either way.
        code == 801 -> WeatherCondition.PARTLY_CLOUDY
        code in 802..804 -> WeatherCondition.CLOUDY

        else -> WeatherCondition.UNKNOWN
    }
}
