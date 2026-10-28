package com.paperscrape.livewallpaper.weather

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The transport's status mapping, which is the part of the network layer worth testing without a
 * socket: it decides whether the settings screen says "your key was rejected", "the daily budget
 * is spent", or "try again later", and those are three different things to tell a user.
 */
class WeatherHttpTest {

    @Test
    fun `a rejected key is not a transient error`() {
        assertEquals(WeatherFailure.UNAUTHORIZED, WeatherHttp.statusToFailure(401))
        assertEquals(WeatherFailure.UNAUTHORIZED, WeatherHttp.statusToFailure(403))
    }

    /** WeatherAPI.com's free plan is 100,000 calls a month; 429 is what running out looks like. */
    @Test
    fun `a spent request budget is its own failure`() {
        assertEquals(WeatherFailure.RATE_LIMITED, WeatherHttp.statusToFailure(429))
    }

    /**
     * Open-Meteo answers a key it does not know with a 400 and the reason in its body: that is the key refused, so the
     * settings say the key was rejected, not that Open-Meteo could not be reached. Any other 400 of its stays a refusal.
     */
    @Test
    fun `open meteo's 400 about the key is a rejected key`() {
        assertEquals(
            WeatherFailure.UNAUTHORIZED,
            WeatherHttp.statusToFailure(400, """{"error":true,"reason":"The supplied API key is invalid."}"""),
        )
        assertEquals(
            WeatherFailure.REQUEST_REFUSED,
            WeatherHttp.statusToFailure(400, """{"reason":"Latitude must be in range of -90 to 90°. Given: 143.77.","error":true}"""),
        )
        assertEquals(WeatherFailure.REQUEST_REFUSED, WeatherHttp.statusToFailure(400, null))
        assertEquals(WeatherFailure.REQUEST_REFUSED, WeatherHttp.statusToFailure(400, "not json"))
    }

    /**
     * A 4xx that is not about the key or the limit refuses the request itself: Open-Meteo's 400 for a parameter it
     * cannot read, WeatherAPI.com's for a place it cannot find, OpenWeather's 400 and 404.
     */
    @Test
    fun `a refusal of the request is its own failure`() {
        for (status in listOf(400, 404, 405, 410, 422)) {
            assertEquals("HTTP $status", WeatherFailure.REQUEST_REFUSED, WeatherHttp.statusToFailure(status))
        }
    }

    @Test
    fun `anything else is a plain http error`() {
        assertEquals(WeatherFailure.HTTP_ERROR, WeatherHttp.statusToFailure(500))
        assertEquals(WeatherFailure.HTTP_ERROR, WeatherHttp.statusToFailure(503))
        assertEquals("a request that took too long", WeatherFailure.HTTP_ERROR, WeatherHttp.statusToFailure(408))
        assertEquals(WeatherFailure.HTTP_ERROR, WeatherHttp.statusToFailure(302))
    }
}
