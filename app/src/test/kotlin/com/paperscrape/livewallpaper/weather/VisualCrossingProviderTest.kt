package com.paperscrape.livewallpaper.weather

import com.paperscrape.livewallpaper.engine.PrecipitationType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Visual Crossing's Timeline response, parsed.
 *
 * **These are fixtures, not live responses.** No Visual Crossing account was available while this
 * was written, so the bodies below are built from the API's published `currentConditions` field
 * list and value sets rather than captured from the wire, and nothing here should be read as
 * end-to-end verification of the provider. What it does verify is the part that would be wrong
 * silently: that this provider's very different report shape -- one `precip` figure plus a
 * `preciptype` array, no showers category, an icon slug instead of a numeric code -- normalises
 * into the same [WeatherObservation] Open-Meteo produces.
 */
class VisualCrossingProviderTest {

    private fun body(current: String) = """
        {
          "queryCost": 1,
          "latitude": 43.77925,
          "longitude": 11.24626,
          "resolvedAddress": "43.77925,11.24626",
          "timezone": "Europe/Rome",
          "tzoffset": 2.0,
          "currentConditions": $current
        }
    """.trimIndent()

    // -- the shapes that must normalise ------------------------------------------------------------

    @Test
    fun `plain rain normalises to the same observation Open-Meteo would produce`() {
        val observation = VisualCrossingProvider.parse(
            body(
                """
                {
                  "datetime": "13:00:00",
                  "datetimeEpoch": 1787310000,
                  "temp": 24.3,
                  "precip": 1.0,
                  "precipprob": 100.0,
                  "preciptype": ["rain"],
                  "snow": 0.0,
                  "snowdepth": 0.0,
                  "cloudcover": 100.0,
                  "conditions": "Rain, Overcast",
                  "icon": "rain"
                }
                """.trimIndent(),
            ),
        )!!
        assertEquals(24.3, observation.temperatureCelsius!!, 0.0001)
        assertEquals(100, observation.cloudCoverPercent)
        assertEquals(1.0, observation.precipitationMm!!, 0.0001)
        assertEquals(1.0, observation.rainMm!!, 0.0001)
        assertEquals(WeatherCondition.RAIN, observation.condition)
        assertEquals(WeatherProviderId.VISUAL_CROSSING, observation.source)

        val snapshot = WeatherSnapshotMapper.toSnapshot(observation)
        assertEquals(PrecipitationType.RAIN, snapshot.precipitationType)
        assertEquals(1f, snapshot.cloudCoverFraction, 0.0001f)
    }

    /**
     * The field this provider does not have. Null says "not reported"; zero would say "no showers
     * are falling", and the mapper treats those differently.
     */
    @Test
    fun `showers are absent rather than zero`() {
        val observation = VisualCrossingProvider.parse(
            body("""{"temp":20.0,"precip":1.0,"preciptype":["rain"],"cloudcover":90.0,"icon":"rain"}"""),
        )!!
        assertNull(observation.showersMm)
    }

    /**
     * `precip` is the total whatever is falling, so it must only be attributed to rain when the
     * provider says rain is what is falling. Snow measured as 0.4 mm of `precip` is not 0.4 mm of
     * rain.
     */
    @Test
    fun `precipitation is not attributed to rain when the type says snow`() {
        val observation = VisualCrossingProvider.parse(
            body(
                """
                {"temp":-1.0,"precip":0.4,"preciptype":["snow"],"snow":0.6,"cloudcover":100.0,"icon":"snow"}
                """.trimIndent(),
            ),
        )!!
        assertNull(observation.rainMm)
        assertEquals(0.4, observation.precipitationMm!!, 0.0001)
        assertEquals(0.6, observation.snowfallCm!!, 0.0001)
        assertEquals(WeatherCondition.SNOW, observation.condition)
        assertEquals(PrecipitationType.SNOW, WeatherSnapshotMapper.toSnapshot(observation).precipitationType)
    }

    @Test
    fun `cloud only is dry`() {
        val observation = VisualCrossingProvider.parse(
            body("""{"temp":19.0,"precip":0.0,"cloudcover":96.0,"conditions":"Overcast","icon":"cloudy"}"""),
        )!!
        assertEquals(WeatherCondition.CLOUDY, observation.condition)
        val snapshot = WeatherSnapshotMapper.toSnapshot(observation)
        assertNull(snapshot.precipitationType)
        assertEquals(0.96f, snapshot.cloudCoverFraction, 0.001f)
    }

    /**
     * The free `icons1` set has no thunder value, so the only place a thunderstorm appears is the
     * human-readable `conditions` string. Reading both is what makes the mapping work whichever
     * icon set the account is on.
     */
    @Test
    fun `a thunderstorm is recognised from the conditions text alone`() {
        val observation = VisualCrossingProvider.parse(
            body(
                """
                {"temp":22.0,"precip":6.0,"preciptype":["rain"],"cloudcover":100.0,
                 "conditions":"Rain, Thunderstorm, Overcast","icon":"rain"}
                """.trimIndent(),
            ),
        )!!
        assertEquals(WeatherCondition.THUNDERSTORM, observation.condition)
        assertTrue(WeatherSnapshotMapper.toSnapshot(observation).isThunderstorm)
    }

    @Test
    fun `a thunder icon is recognised too`() {
        assertEquals(
            WeatherCondition.THUNDERSTORM,
            VisualCrossingProvider.condition("thunder-rain", "", listOf("rain")),
        )
    }

    /** Both types at once. Named as sleet rather than collapsed to whichever came first. */
    @Test
    fun `rain and snow together are sleet`() {
        assertEquals(
            WeatherCondition.SLEET,
            VisualCrossingProvider.condition("snow", "Snow, Rain", listOf("rain", "snow")),
        )
    }

    @Test
    fun `freezing rain and ice are their own conditions`() {
        assertEquals(WeatherCondition.FREEZING_RAIN, VisualCrossingProvider.condition("rain", "", listOf("freezingrain")))
        assertEquals(WeatherCondition.HAIL, VisualCrossingProvider.condition("rain", "", listOf("ice")))
    }

    @Test
    fun `the icon set maps its cloud values`() {
        assertEquals(WeatherCondition.CLEAR, VisualCrossingProvider.condition("clear-day", "", emptyList()))
        assertEquals(WeatherCondition.CLEAR, VisualCrossingProvider.condition("clear-night", "", emptyList()))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, VisualCrossingProvider.condition("partly-cloudy-day", "", emptyList()))
        assertEquals(WeatherCondition.CLOUDY, VisualCrossingProvider.condition("cloudy", "", emptyList()))
        assertEquals(WeatherCondition.FOG, VisualCrossingProvider.condition("fog", "", emptyList()))
        assertEquals(WeatherCondition.SHOWERS, VisualCrossingProvider.condition("showers-day", "", emptyList()))
        assertEquals(WeatherCondition.SNOW_SHOWERS, VisualCrossingProvider.condition("snow-showers-day", "", emptyList()))
    }

    /** "wind" carries no sky information. Unknown, not clear -- cloudcover still has the answer. */
    @Test
    fun `an icon that says nothing about the sky is unknown`() {
        assertEquals(WeatherCondition.UNKNOWN, VisualCrossingProvider.condition("wind", "Windy", emptyList()))
        assertEquals(WeatherCondition.UNKNOWN, VisualCrossingProvider.condition("", "", emptyList()))
    }

    // -- malformed --------------------------------------------------------------------------------

    @Test
    fun `a body with no currentConditions is not an observation`() {
        assertNull(VisualCrossingProvider.parse("""{"latitude":43.77,"days":[]}"""))
    }

    @Test
    fun `an error page is not an observation`() {
        assertNull(VisualCrossingProvider.parse("Invalid API key"))
        assertNull(VisualCrossingProvider.parse(""))
    }

    @Test
    fun `an absent preciptype is an empty list rather than a crash`() {
        val observation = VisualCrossingProvider.parse(
            body("""{"temp":19.0,"precip":0.0,"cloudcover":10.0,"icon":"clear-day"}"""),
        )!!
        assertNull(observation.rainMm)
        assertEquals(WeatherCondition.CLEAR, observation.condition)
    }

    // -- the key ----------------------------------------------------------------------------------

    /**
     * The behaviour the task asked for by name: **no fake call**. Without a key the provider
     * reports the state and sends nothing, so this test needs no network and would fail loudly if
     * a request were ever attempted here.
     */
    @Test
    fun `no key means no request at all`() = runBlocking {
        assertEquals(WeatherFetchResult.MissingApiKey, VisualCrossingProvider.fetch(43.77, 11.24, apiKey = ""))
        assertEquals(WeatherFetchResult.MissingApiKey, VisualCrossingProvider.fetch(43.77, 11.24, apiKey = "   "))
    }

    @Test
    fun `this provider requires a key`() {
        assertTrue(VisualCrossingProvider.requiresApiKey)
        assertEquals(WeatherProviderId.VISUAL_CROSSING, VisualCrossingProvider.id)
    }

    @Test
    fun `the request is the documented timeline endpoint in metric units`() {
        val url = VisualCrossingProvider.requestUrl(43.77925, 11.24626, "KEY123")
        assertTrue(
            url.startsWith(
                "https://weather.visualcrossing.com/VisualCrossingWebServices/rest/services/timeline/43.77925,11.24626",
            ),
        )
        assertTrue(url.contains("unitGroup=metric"))
        assertTrue(url.contains("include=current"))
        assertTrue(url.contains("contentType=json"))
        assertTrue(url.contains("key=KEY123"))
    }
}
