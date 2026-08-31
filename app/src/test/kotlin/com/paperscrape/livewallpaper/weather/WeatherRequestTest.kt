package com.paperscrape.livewallpaper.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** SEC-04 and SEC-05: what a provider URL is allowed to carry, and how precisely. */
class WeatherRequestTest {

    @Test
    fun `a coordinate goes out at two decimals`() {
        assertEquals("45.46", WeatherRequest.coordinate(45.4642035))
        assertEquals("9.19", WeatherRequest.coordinate(9.1899805))
        assertEquals("-33.87", WeatherRequest.coordinate(-33.8688197))
    }

    @Test
    fun `rounding is to nearest, not truncation`() {
        // Truncation would bias every request southward and westward, which is a small but real
        // systematic error rather than a loss of precision.
        assertEquals("45.47", WeatherRequest.coordinate(45.4652))
        assertEquals("-45.47", WeatherRequest.coordinate(-45.4652))
    }

    @Test
    fun `a coordinate near the meridian does not go out as negative zero`() {
        assertEquals("0.0", WeatherRequest.coordinate(-0.0001))
        assertEquals("0.0", WeatherRequest.coordinate(0.0))
    }

    @Test
    fun `two decimals is inside one provider grid cell`() {
        // The claim the rounding rests on: the error it introduces is far below the resolution of
        // the answer. Two decimals is at most ~0.55 km; the coarsest grid here is ~11 km.
        val worstCaseKm = 0.005 * 111.32
        assertTrue("rounding error $worstCaseKm km is not well inside a grid cell", worstCaseKm < 1.0)
    }

    @Test
    fun `a key with URL syntax in it cannot become another parameter`() {
        // The failure this prevents: "&aqi=yes" pasted into a key ended the key parameter and
        // started a new one, so the request went out keyless and came back rejected.
        val encoded = WeatherRequest.key("abc&aqi=yes#frag")
        assertFalse("the key must not still contain a parameter separator: $encoded", encoded.contains("&"))
        assertFalse("nor a fragment marker: $encoded", encoded.contains("#"))
        assertFalse("nor a bare equals: $encoded", encoded.contains("="))
    }

    @Test
    fun `a plus sign survives as a plus sign`() {
        // The quiet one: unencoded, `+` decodes server-side as a space, so the key is wrong in a
        // way that looks like a typo rather than an encoding bug.
        assertEquals("a%2Bb", WeatherRequest.key("a+b"))
    }

    @Test
    fun `an ordinary key is left alone`() {
        assertEquals("0123456789abcdefABCDEF", WeatherRequest.key("0123456789abcdefABCDEF"))
    }

    @Test
    fun `every provider builds its URL through this`() {
        // The coupling, stated where it can fail: three providers, one place that decides what a
        // coordinate and a key look like on the wire.
        val key = "k&y"
        for (url in listOf(
            OpenMeteoProvider.requestUrl(45.4642035, 9.1899805, key),
            OpenWeatherProvider.requestUrl(45.4642035, 9.1899805, key),
            WeatherApiComProvider.requestUrl(45.4642035, 9.1899805, key),
        )) {
            assertTrue("full-precision latitude left in $url", !url.contains("45.4642035"))
            assertTrue("full-precision longitude left in $url", !url.contains("9.1899805"))
            assertTrue("unencoded key left in $url", !url.contains("k&y"))
            assertTrue("the rounded latitude is missing from $url", url.contains("45.46"))
        }
    }
}
