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

    @Test
    fun `anything else is a plain http error`() {
        assertEquals(WeatherFailure.HTTP_ERROR, WeatherHttp.statusToFailure(500))
        assertEquals(WeatherFailure.HTTP_ERROR, WeatherHttp.statusToFailure(404))
        assertEquals(WeatherFailure.HTTP_ERROR, WeatherHttp.statusToFailure(302))
    }
}
