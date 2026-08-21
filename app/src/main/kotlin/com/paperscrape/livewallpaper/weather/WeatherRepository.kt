package com.paperscrape.livewallpaper.weather

import com.paperscrape.livewallpaper.BuildConfig
import com.paperscrape.livewallpaper.engine.PrecipitationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * A fetched snapshot of real conditions at one location, already translated into this app's own
 * rendering vocabulary ([PrecipitationType], a 0f..1f intensity/cloud-cover fraction) rather than
 * Open-Meteo's raw WMO weather codes -- so [com.paperscrape.livewallpaper.engine.PaperRenderer]
 * (the only consumer) never needs to know anything about the provider's response shape, the same
 * separation [com.paperscrape.livewallpaper.location.DeviceLocationFix] keeps between "where is
 * the device" and what that's used for.
 */
data class LiveWeatherSnapshot(
    val precipitationType: PrecipitationType?, // null = no precipitation right now
    val precipitationIntensity: Float, // 0f..1f, only meaningful when precipitationType != null
    val cloudCoverFraction: Float, // 0f..1f
    val isThunderstorm: Boolean,
    val fetchedAtMillis: Long,
)

/**
 * Fetches current conditions from Open-Meteo (https://open-meteo.com) -- chosen for Live Weather
 * because its free tier needs no API key at all for non-commercial use, which is what actually
 * makes [resolveApiKey]'s "hardcoded key is optional, keyless still works" design possible; most
 * other providers (OpenWeatherMap, WeatherAPI.com) hard-require a key with no anonymous tier.
 *
 * Uses `java.net.HttpURLConnection` rather than adding an OkHttp/Retrofit dependency, matching
 * [com.paperscrape.livewallpaper.update.UpdateChecker]'s own existing pattern -- this is the only
 * other network call anywhere in the app, so reusing its exact style (timeouts, try/catch-everything-
 * returns-null-on-failure, no new library) keeps the app's footprint from growing for a single
 * lightweight JSON GET.
 */
object WeatherRepository {

    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 8000

    /**
     * A user-entered key (from Settings, always wins if present) or the build's own baked-in key
     * (from [BuildConfig.OPENMETEO_API_KEY], see app/build.gradle.kts's own comment) -- either
     * routes to Open-Meteo's higher-limit `customer-api.open-meteo.com` endpoint instead of the
     * free `api.open-meteo.com` one. Returns null when neither is set, meaning "use the free
     * keyless endpoint" -- never a hard failure, since Open-Meteo's whole free tier exists
     * precisely so this app works out of the box with zero setup.
     */
    private fun resolveApiKey(userApiKey: String): String? =
        userApiKey.trim().ifBlank { BuildConfig.OPENMETEO_API_KEY }.trim().ifBlank { null }

    /**
     * Returns null on any failure (network, parsing, unexpected shape) -- same "never crash,
     * just skip this update and try again next hour" contract as
     * [com.paperscrape.livewallpaper.update.UpdateChecker.checkForUpdate].
     */
    suspend fun fetchCurrentConditions(
        latitude: Double,
        longitude: Double,
        userApiKey: String,
    ): LiveWeatherSnapshot? = withContext(Dispatchers.IO) {
        val apiKey = resolveApiKey(userApiKey)
        val host = if (apiKey != null) "customer-api.open-meteo.com" else "api.open-meteo.com"
        val keyParam = if (apiKey != null) "&apikey=$apiKey" else ""
        val url = "https://$host/v1/forecast?latitude=$latitude&longitude=$longitude" +
            "&current=precipitation,rain,showers,snowfall,weather_code,cloud_cover&timezone=auto$keyParam"

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", "PaperScrape-LiveWeather")
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val current = JSONObject(body).optJSONObject("current") ?: return@withContext null
            val weatherCode = current.optInt("weather_code", 0)
            val precipitationMm = current.optDouble("precipitation", 0.0)
            val cloudCoverPercent = current.optInt("cloud_cover", 0)
            // Open-Meteo splits precipitation three ways and `precipitation` is their sum, so the
            // breakdown is what says *which kind* is falling. It matters here because a Florence
            // shower reports `rain: 0.0` with the millimetres in `showers` -- looking only at
            // `rain` would have read a downpour as a dry hour. See [weatherCodeToSnapshot].
            val rainMm = current.optDouble("rain", 0.0)
            val showersMm = current.optDouble("showers", 0.0)
            val snowfallCm = current.optDouble("snowfall", 0.0)

            weatherCodeToSnapshot(weatherCode, precipitationMm, cloudCoverPercent, rainMm, showersMm, snowfallCm)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Maps Open-Meteo's WMO weather-interpretation codes (https://open-meteo.com/en/docs, "WMO
     * Weather interpretation codes" table) to this app's own [PrecipitationType]/cloud-cover/
     * thunderstorm vocabulary. Intensity is derived from the actual `precipitation` (mm, the last
     * hour) rather than guessed purely from the code's "slight/moderate/heavy" wording, so two
     * "61 slight rain" readings with different real mm still produce different intensities.
     */
    internal fun weatherCodeToSnapshot(
        weatherCode: Int,
        precipitationMm: Double,
        cloudCoverPercent: Int,
        rainMm: Double = 0.0,
        showersMm: Double = 0.0,
        snowfallCm: Double = 0.0,
    ): LiveWeatherSnapshot {
        val isSnowCode = weatherCode in intArrayOf(71, 73, 75, 77, 85, 86)
        val isRainCode = weatherCode in intArrayOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99)
        val isThunderstorm = weatherCode in intArrayOf(95, 96, 99)
        // Measurements first, code second.
        //
        // The code is an interpretation and the millimetres are an observation, and they can
        // disagree: a shower that has just stopped, or one the model places a grid cell away,
        // leaves `precipitation` above zero under an "overcast" code 3. v2.12 read only the code
        // and so drew a dry, fully clouded sky in that case -- which is the shape of the Florence
        // report. Where a measurement exists it decides *that something is falling*; the split
        // between rain and snow comes from whichever of the three sub-fields carries it, falling
        // back to the code when the sum is positive but the breakdown is not (a customer endpoint
        // may omit the sub-fields).
        val hasMeasuredSnow = snowfallCm > 0.0
        val hasMeasuredRain = rainMm > 0.0 || showersMm > 0.0
        val hasMeasuredPrecipitation = precipitationMm > 0.0 || hasMeasuredRain || hasMeasuredSnow
        val precipitationType = when {
            hasMeasuredSnow -> PrecipitationType.SNOW
            hasMeasuredRain -> PrecipitationType.RAIN
            isSnowCode -> PrecipitationType.SNOW
            isRainCode -> PrecipitationType.RAIN
            // Something is measurably falling and nothing above said which kind. Rain is the
            // right guess: snow arrives with either a snow code or a snowfall reading, and a
            // wallpaper that snows in August on a rounding artefact is worse than one that rains.
            hasMeasuredPrecipitation -> PrecipitationType.RAIN
            else -> null
        }
        // 8mm/h+ already reads as a heavy downpour -- capping the intensity mapping there instead
        // of at some much higher "extreme storm" figure keeps ordinary rain/snow readings usefully
        // spread across the 0..1 slider range instead of clustering near 0.
        // The sum, or the parts when the sum is missing: `precipitation` is rain + showers +
        // snowfall's water equivalent, so it is the right figure whenever it is present.
        val measuredMm = if (precipitationMm > 0.0) precipitationMm else rainMm + showersMm
        val intensity = (measuredMm / 8.0).coerceIn(0.0, 1.0).toFloat()
        return LiveWeatherSnapshot(
            precipitationType = precipitationType,
            precipitationIntensity = if (precipitationType != null) intensity.coerceAtLeast(0.15f) else 0f,
            cloudCoverFraction = (cloudCoverPercent / 100f).coerceIn(0f, 1f),
            isThunderstorm = isThunderstorm,
            fetchedAtMillis = System.currentTimeMillis(),
        )
    }
}
