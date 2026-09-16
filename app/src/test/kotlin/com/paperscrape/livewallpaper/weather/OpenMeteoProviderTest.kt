package com.paperscrape.livewallpaper.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Open-Meteo's response, parsed.
 *
 * The bodies here are the endpoint's real shape for Florence (43.77925, 11.24626, Europe/Rome).
 * They are what makes this a test of the parser rather than of a mock: the field names, the
 * nesting under `current`, and the `units` block that must be ignored are all the provider's own.
 */
class OpenMeteoProviderTest {

    private val showerResponse = """
        {
          "latitude": 43.77,
          "longitude": 11.25,
          "generationtime_ms": 0.0421,
          "utc_offset_seconds": 7200,
          "timezone": "Europe/Rome",
          "timezone_abbreviation": "GMT+2",
          "elevation": 65.0,
          "current_units": {
            "time": "iso8601",
            "interval": "seconds",
            "temperature_2m": "°C",
            "precipitation": "mm",
            "rain": "mm",
            "showers": "mm",
            "snowfall": "cm",
            "weather_code": "wmo code",
            "cloud_cover": "%"
          },
          "current": {
            "time": "2026-08-21T13:00",
            "interval": 900,
            "temperature_2m": 24.3,
            "precipitation": 1.0,
            "rain": 0.0,
            "showers": 1.0,
            "snowfall": 0.0,
            "weather_code": 80,
            "cloud_cover": 100
          }
        }
    """.trimIndent()

    @Test
    fun `every current field is read`() {
        val observation = OpenMeteoProvider.parse(showerResponse)!!
        assertEquals(24.3, observation.temperatureCelsius!!, 0.0001)
        assertEquals(100, observation.cloudCoverPercent)
        assertEquals(1.0, observation.precipitationMm!!, 0.0001)
        assertEquals(0.0, observation.rainMm!!, 0.0001)
        assertEquals(1.0, observation.showersMm!!, 0.0001)
        assertEquals(0.0, observation.snowfallCm!!, 0.0001)
        assertEquals(WeatherCondition.SHOWERS, observation.condition)
        assertEquals(WeatherProviderId.OPEN_METEO, observation.source)
    }

    /**
     * A `rain` of exactly 0.0 alongside a positive `showers` is the Florence reading, and the
     * whole reason the two are separate fields. Zero must survive as zero and not become null.
     */
    @Test
    fun `a reported zero is a reading, not an absence`() {
        val observation = OpenMeteoProvider.parse(showerResponse)!!
        assertEquals(0.0, observation.rainMm)
        assertNotNull(observation.rainMm)
    }

    /**
     * The customer endpoint can return `current` without the sub-fields. Those must come back
     * null -- not 0.0 -- because the mapper falls back to the total in that case and would
     * otherwise conclude that nothing is falling.
     */
    @Test
    fun `omitted sub-fields are null rather than zero`() {
        val body = """
            {"current":{"time":"2026-08-21T13:00","precipitation":0.6,"weather_code":3,"cloud_cover":100}}
        """.trimIndent()
        val observation = OpenMeteoProvider.parse(body)!!
        assertEquals(0.6, observation.precipitationMm!!, 0.0001)
        assertNull(observation.rainMm)
        assertNull(observation.showersMm)
        assertNull(observation.snowfallCm)
        assertNull(observation.temperatureCelsius)
    }

    @Test
    fun `an explicit json null is also an absence`() {
        val body = """{"current":{"precipitation":null,"cloud_cover":80,"weather_code":3}}"""
        val observation = OpenMeteoProvider.parse(body)!!
        assertNull(observation.precipitationMm)
        assertEquals(80, observation.cloudCoverPercent)
    }

    @Test
    fun `a body with no current block is not an observation`() {
        assertNull(OpenMeteoProvider.parse("""{"latitude":43.77,"longitude":11.25}"""))
    }

    @Test
    fun `a body that is not json at all is not an observation`() {
        assertNull(OpenMeteoProvider.parse("<html><body>502 Bad Gateway</body></html>"))
        assertNull(OpenMeteoProvider.parse(""))
    }

    // -- WMO codes --------------------------------------------------------------------------------

    @Test
    fun `the WMO code table covers each category`() {
        assertEquals(WeatherCondition.CLEAR, OpenMeteoProvider.conditionForWmoCode(0))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, OpenMeteoProvider.conditionForWmoCode(2))
        assertEquals(WeatherCondition.CLOUDY, OpenMeteoProvider.conditionForWmoCode(3))
        assertEquals(WeatherCondition.FOG, OpenMeteoProvider.conditionForWmoCode(45))
        assertEquals(WeatherCondition.DRIZZLE, OpenMeteoProvider.conditionForWmoCode(53))
        assertEquals(WeatherCondition.FREEZING_RAIN, OpenMeteoProvider.conditionForWmoCode(66))
        assertEquals(WeatherCondition.RAIN, OpenMeteoProvider.conditionForWmoCode(63))
        assertEquals(WeatherCondition.SNOW, OpenMeteoProvider.conditionForWmoCode(73))
        assertEquals(WeatherCondition.SHOWERS, OpenMeteoProvider.conditionForWmoCode(81))
        assertEquals(WeatherCondition.SNOW_SHOWERS, OpenMeteoProvider.conditionForWmoCode(86))
        assertEquals(WeatherCondition.THUNDERSTORM, OpenMeteoProvider.conditionForWmoCode(96))
    }

    /** An unrecognised code says so rather than claiming clear skies. */
    @Test
    fun `an unknown code is unknown, not clear`() {
        assertEquals(WeatherCondition.UNKNOWN, OpenMeteoProvider.conditionForWmoCode(42))
        assertEquals(WeatherCondition.UNKNOWN, OpenMeteoProvider.conditionForWmoCode(-1))
    }

    /** Every code the table names has to classify as rainy or snowy, or the mapper's fallback misses it. */
    @Test
    fun `every precipitation code is classified`() {
        val precipitationCodes = listOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99)
        for (code in precipitationCodes) {
            val condition = OpenMeteoProvider.conditionForWmoCode(code)
            assertTrue("code $code", condition.isRainy || condition.isSnowy)
        }
    }

    @Test
    fun `no cloud code is classified as precipitation`() {
        for (code in listOf(0, 1, 2, 3, 45, 48)) {
            val condition = OpenMeteoProvider.conditionForWmoCode(code)
            assertFalse("code $code", condition.isRainy || condition.isSnowy)
        }
    }

    // -- the request ------------------------------------------------------------------------------

    /**
     * Keyless is the free tier and is not a failure. A key only changes which host is called, and
     * that is the whole difference between the two endpoints.
     */
    @Test
    fun `no key uses the free endpoint and a key uses the customer one`() {
        val keyless = OpenMeteoProvider.requestUrl(43.77925, 11.24626, userApiKey = "")
        assertTrue(keyless.startsWith("https://api.open-meteo.com/"))
        assertFalse(keyless.contains("apikey"))

        val keyed = OpenMeteoProvider.requestUrl(43.77925, 11.24626, userApiKey = "abc123")
        assertTrue(keyed.startsWith("https://customer-api.open-meteo.com/"))
        assertTrue(keyed.contains("apikey=abc123"))
    }

    @Test
    fun `the request asks for every field the observation carries`() {
        val url = OpenMeteoProvider.requestUrl(43.77925, 11.24626, userApiKey = "")
        for (field in listOf("temperature_2m", "precipitation", "rain", "showers", "snowfall", "weather_code", "cloud_cover")) {
            assertTrue(field, url.contains(field))
        }
        // Rounded to two decimals on the way out -- SEC-05, see WeatherRequest.coordinate.
        assertTrue(url.contains("latitude=43.78"))
        assertTrue(url.contains("longitude=11.25"))
    }

    @Test
    fun `a blank or whitespace key is no key`() {
        assertNull(OpenMeteoProvider.resolveApiKey("   "))
        assertNull(OpenMeteoProvider.resolveApiKey(""))
    }

    /**
     * **The user's key still does exactly what it did, and that is the half v5.3 had to not break.**
     *
     * Removing the app's baked-in key (v5.3B audit, S1: a `buildConfigField` String is a plain
     * constant in the dex, so the maintainer's key was readable in every published APK) changed
     * what a *blank* key means -- it used to fall through to the app's key, and now means the
     * keyless free tier. It must not have changed what a *set* key means, which is: trimmed, used
     * verbatim, and routed to the higher-limit customer endpoint.
     *
     * The test above it is the other half, and it is worth knowing that it used to be *ambiguous*:
     * `resolveApiKey("   ")` returned null only because the env var happened to be unset on the
     * machine running the test. A CI release build with the secret configured would have made it
     * fail. There is no environment left to depend on.
     */
    @Test
    fun `a user key is used verbatim and routes to the customer endpoint`() {
        assertEquals("my-own-key", OpenMeteoProvider.resolveApiKey("my-own-key"))
        assertEquals("my-own-key", OpenMeteoProvider.resolveApiKey("  my-own-key  "))
        val url = OpenMeteoProvider.requestUrl(43.77925, 11.24626, userApiKey = "my-own-key")
        assertTrue(url.startsWith("https://customer-api.open-meteo.com/"))
        assertTrue(url.contains("apikey=my-own-key"))
    }

    @Test
    fun `this provider never requires a key`() {
        assertFalse(OpenMeteoProvider.requiresApiKey)
        assertEquals(WeatherProviderId.OPEN_METEO, OpenMeteoProvider.id)
    }
}
