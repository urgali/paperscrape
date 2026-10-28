package com.paperscrape.livewallpaper.weather

import com.paperscrape.livewallpaper.engine.WEATHER_CHECK_INTERVAL_MS
import com.paperscrape.livewallpaper.engine.WEATHER_REFRESH_INTERVAL_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * **A request the provider refuses is asked again at the hour, not two minutes later.**
 *
 * A 400 is the provider's answer about the request itself -- Open-Meteo's for a parameter it cannot read,
 * WeatherAPI.com's for a request it cannot place, OpenWeather's for coordinates out of range -- and the same request
 * two minutes later gets the same answer. So it keeps the hourly refresh, as a refused key (Open-Meteo's 400 for a key
 * it does not know among them) and a provider's limit do, instead of the retries of a request that did not get through
 * (2, 4, 8, 16 and 32 minutes after the one before). Driven through the status mapping, the retry rule and the loop's
 * two-minute passes as the wallpaper service chains them, with the wallpaper on screen for the whole hour and every
 * request answered alike.
 *
 * What the settings screen says: the provider answered and refused, [LiveWeatherStatus.REFUSED] with nothing to draw and
 * [LiveWeatherStatus.REFUSED_STALE] with this place's last conditions drawn -- not that it could not be reached, the
 * line of [LiveWeatherStatus.FAILED] and [LiveWeatherStatus.STALE].
 */
class RefusedRequestTest {

    private val minute = 60_000L

    /**
     * The minutes, from the first request, at which the loop sends one in the first hour when every answer is HTTP
     * [status]: a pass every [WEATHER_CHECK_INTERVAL_MS], each sending a request once the wait the last answers earned
     * ([LiveWeatherSchedule.nextAttemptDelayMillis]) has passed, and a failure counted only when it is transient.
     */
    private fun requestsInTheFirstHour(status: Int): List<Long> {
        val sent = mutableListOf<Long>()
        var transientFailures = 0
        var lastRequest = Long.MIN_VALUE / 4
        var now = 0L
        while (now < WEATHER_REFRESH_INTERVAL_MS) {
            val wait = LiveWeatherSchedule.nextAttemptDelayMillis(transientFailures, WEATHER_REFRESH_INTERVAL_MS)
            if (LiveWeatherSchedule.isAttemptDue(now - lastRequest, wait)) {
                lastRequest = now
                sent += now / minute
                val answer = WeatherFetchResult.Failed(WeatherHttp.statusToFailure(status), WeatherProviderId.OPEN_METEO)
                transientFailures = if (LiveWeatherSchedule.isTransient(answer)) transientFailures + 1 else 0
            }
            now += WEATHER_CHECK_INTERVAL_MS
        }
        return sent
    }

    @Test
    fun `a 400 is sent once in the first hour`() {
        assertFalse(
            "a 400 is the provider's answer, not an accident",
            LiveWeatherSchedule.isTransient(WeatherFetchResult.Failed(WeatherHttp.statusToFailure(400), WeatherProviderId.OPEN_METEO)),
        )
        assertEquals("the minutes a request is sent at", listOf(0L), requestsInTheFirstHour(400))
    }

    /** Every answer about the request, the key or the limit waits the hour; a timeout and a server's error do not. */
    @Test
    fun `the answers wait the hour, the accidents are tried again within it`() {
        for (status in listOf(400, 401, 403, 404, 405, 410, 422, 429)) {
            assertEquals("HTTP $status", listOf(0L), requestsInTheFirstHour(status))
        }
        for (status in listOf(408, 500, 502, 503)) {
            assertEquals("HTTP $status", listOf(0L, 2L, 6L, 14L, 30L), requestsInTheFirstHour(status))
        }
    }

    @Test
    fun `a refused request is said to be refused, not unreachable`() {
        for (provider in WeatherProviderId.entries) {
            val refused = WeatherFetchResult.Failed(WeatherHttp.statusToFailure(400), provider)
            // By name, so the reading of a status that does not exist is a failure here, not a missing symbol.
            assertEquals(
                provider.name,
                "REFUSED",
                LiveWeatherStatus.of(true, true, refused, hasSnapshotInEffect = false, previous = LiveWeatherStatus.OK).name,
            )
            assertEquals(
                provider.name,
                "REFUSED_STALE",
                LiveWeatherStatus.of(true, true, refused, hasSnapshotInEffect = true, previous = LiveWeatherStatus.OK).name,
            )
        }
    }
}
