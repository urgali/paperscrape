package com.paperscrape.livewallpaper.weather

import com.paperscrape.livewallpaper.MAX_HTTP_BODY_CHARS
import com.paperscrape.livewallpaper.readAtMost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * The one JSON GET both providers make.
 *
 * Still `java.net.HttpURLConnection` rather than OkHttp/Retrofit, matching
 * [com.paperscrape.livewallpaper.update.UpdateChecker]: these two plus the updater are the only
 * network calls in the app, and adding a client library for them would grow the footprint for no
 * behaviour. What is shared here is only the transport -- status mapping to [WeatherFailure], the
 * timeouts, and closing the connection -- so that a second provider did not mean a second copy of
 * the same twenty lines.
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
                // Bounded: current conditions are a few kilobytes (SEC-03). An over-long body
                // reads as a malformed response, which is the outcome it deserves.
                val body = connection.inputStream.bufferedReader().use { it.readAtMost(MAX_HTTP_BODY_CHARS) }
                if (body == null) HttpOutcome.Error(WeatherFailure.MALFORMED_RESPONSE)
                else HttpOutcome.Body(body)
            } else {
                HttpOutcome.Error(statusToFailure(code))
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
     * 401 and 403 both mean "this key will not work", which is worth separating from a transient
     * error because the settings screen can say so and the loop need not keep trying. 429 is the
     * free plan's daily budget.
     */
    fun statusToFailure(status: Int): WeatherFailure = when (status) {
        HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> WeatherFailure.UNAUTHORIZED
        429 -> WeatherFailure.RATE_LIMITED
        else -> WeatherFailure.HTTP_ERROR
    }
}

/** What [WeatherHttp.getJson] came back with. */
internal sealed interface HttpOutcome {
    data class Body(val json: String) : HttpOutcome
    data class Error(val failure: WeatherFailure) : HttpOutcome
}
