package com.paperscrape.livewallpaper.weather

import com.paperscrape.livewallpaper.MAX_HTTP_BODY_CHARS
import com.paperscrape.livewallpaper.readAtMost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The one JSON GET all three providers make.
 *
 * `java.net.HttpURLConnection` rather than OkHttp/Retrofit, matching
 * [com.paperscrape.livewallpaper.update.UpdateChecker],
 * [com.paperscrape.livewallpaper.update.ApkDownloader] and
 * [com.paperscrape.livewallpaper.location.CityGeocoder], the app's other network calls: adding a
 * client library for them would grow the footprint for no behaviour. What is shared here is only
 * the transport -- status mapping to [WeatherFailure], the timeouts, and closing the connection --
 * so that a second provider does not mean a second copy of the same twenty lines.
 */
internal object WeatherHttp {

    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 8000

    /** The body on success, or the failure that stopped it. Never throws. */
    suspend fun getJson(url: String): HttpOutcome = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", "PaperScrape-LiveWeather")
                setRequestProperty("Accept", "application/json")
            }
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                // Bounded: current conditions are a few kilobytes. An over-long body
                // reads as a malformed response, which is the outcome it deserves.
                val body = connection.inputStream.bufferedReader().use { it.readAtMost(MAX_HTTP_BODY_CHARS) }
                if (body == null) HttpOutcome.Error(WeatherFailure.MALFORMED_RESPONSE)
                else HttpOutcome.Body(body)
            } else {
                // The error's body says why where a provider says it there (a 403 of WeatherAPI.com, a 400 of
                // Open-Meteo), and only then is it read: bounded, and an unreadable one is no reason.
                val errorBody = if (code == HttpURLConnection.HTTP_FORBIDDEN || code == HttpURLConnection.HTTP_BAD_REQUEST) {
                    runCatching { connection.errorStream?.bufferedReader()?.use { it.readAtMost(MAX_ERROR_BODY_CHARS) } }.getOrNull()
                } else {
                    null
                }
                HttpOutcome.Error(statusToFailure(code, errorBody))
            }
        } catch (_: Exception) {
            HttpOutcome.Error(WeatherFailure.NETWORK)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Pure, so the status-to-failure rule can be tested without a socket.
     *
     * 401 and 403 mean "this key will not work", which is worth separating from a transient error
     * because the settings screen can say so and the loop need not keep trying -- **except a 403 whose
     * [errorBody] carries WeatherAPI.com's code 2007**, the month's quota spent: that is the provider's
     * limit, as 429 is the other two's, and the key is not wrong. (Its 2008, a key disabled, and 2009, a
     * plan without the resource, are refusals of the key.)
     *
     * **A 400 whose [errorBody] gives Open-Meteo's reason about the key** ("The supplied API key is invalid.") is the
     * key refused, as a 401 is: Open-Meteo answers a key it does not know with a 400, not a 401.
     *
     * **Every other 4xx is the provider refusing the request itself** ([WeatherFailure.REQUEST_REFUSED]): the same
     * request gets the same answer, so the loop asks again at its hourly interval, not two minutes later. What a 400
     * says differs from one provider to the next -- Open-Meteo's is a parameter it cannot read, WeatherAPI.com's a
     * request it cannot place (its codes 1003, 1005, 1006, and 9999, an error of its own it sends as a 400),
     * OpenWeather's coordinates out of range -- and for each it is an answer, not an accident. 408, a request that
     * took too long, is the one 4xx about time: it stays with the 5xx in [WeatherFailure.HTTP_ERROR], which is tried
     * again sooner.
     */
    fun statusToFailure(status: Int, errorBody: String? = null): WeatherFailure = when (status) {
        HttpURLConnection.HTTP_UNAUTHORIZED -> WeatherFailure.UNAUTHORIZED
        HttpURLConnection.HTTP_FORBIDDEN ->
            if (providerErrorCode(errorBody) == WEATHER_API_COM_QUOTA_SPENT) WeatherFailure.RATE_LIMITED else WeatherFailure.UNAUTHORIZED
        HttpURLConnection.HTTP_BAD_REQUEST ->
            if (openMeteoKeyRefused(errorBody)) WeatherFailure.UNAUTHORIZED else WeatherFailure.REQUEST_REFUSED
        429 -> WeatherFailure.RATE_LIMITED
        HttpURLConnection.HTTP_CLIENT_TIMEOUT -> WeatherFailure.HTTP_ERROR
        in 400..499 -> WeatherFailure.REQUEST_REFUSED
        else -> WeatherFailure.HTTP_ERROR
    }

    /** `error.code` of an error body shaped as WeatherAPI.com's, `{"error":{"code":2007,"message":…}}`, or null. */
    private fun providerErrorCode(errorBody: String?): Int? {
        if (errorBody.isNullOrBlank()) return null
        return runCatching { JSONObject(errorBody).optJSONObject("error")?.takeIf { it.has("code") }?.optInt("code") }.getOrNull()
    }

    /** Whether an error body shaped as Open-Meteo's, `{"error":true,"reason":…}`, gives a reason about the API key. */
    private fun openMeteoKeyRefused(errorBody: String?): Boolean {
        if (errorBody.isNullOrBlank()) return false
        val reason = runCatching { JSONObject(errorBody).optString("reason") }.getOrNull() ?: return false
        return reason.contains("API key", ignoreCase = true)
    }

    /** WeatherAPI.com's error code for "API key has exceeded calls per month quota", sent with a 403. */
    private const val WEATHER_API_COM_QUOTA_SPENT = 2007

    /** An error body is a sentence or two; anything longer is not a reason worth reading. */
    private const val MAX_ERROR_BODY_CHARS = 16_000
}

/** What [WeatherHttp.getJson] came back with. */
internal sealed interface HttpOutcome {
    data class Body(val json: String) : HttpOutcome
    data class Error(val failure: WeatherFailure) : HttpOutcome
}
