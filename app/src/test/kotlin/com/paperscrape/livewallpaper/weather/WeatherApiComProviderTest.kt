package com.paperscrape.livewallpaper.weather

import com.paperscrape.livewallpaper.engine.PrecipitationType
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [WeatherApiComProvider], the provider that replaced Visual Crossing in v3.7.
 *
 * **What makes this suite different from the one it replaces.** Visual Crossing's parser was tested
 * against fixtures assembled by hand from prose documentation, with no way to check the mapping was
 * complete or correct — deferred item **D8**. WeatherAPI.com publishes its condition vocabulary as
 * machine-readable JSON, that file is committed verbatim at
 * `src/test/resources/weather/weatherapi-conditions.json`, and
 * [everyPublishedConditionCodeIsMapped] walks all 60 entries. A code the provider adds, or one this
 * table forgets, is a test failure rather than a silent `UNKNOWN` on somebody's wallpaper.
 *
 * No test here touches the network. Every response body is a literal.
 */
class WeatherApiComProviderTest {

    private fun publishedConditions(): List<Pair<Int, String>> {
        val stream = javaClass.classLoader!!.getResourceAsStream("weather/weatherapi-conditions.json")
        assertNotNull("the committed conditions fixture is missing", stream)
        val doc = JSONObject(stream!!.bufferedReader().use { it.readText() })
        val array = doc.getJSONArray("conditions")
        return (0 until array.length()).map {
            val entry = array.getJSONObject(it)
            entry.getInt("code") to entry.getString("day")
        }
    }

    // -- the published vocabulary ----------------------------------------------------------------

    /** A guard on the fixture itself: a truncated download would quietly weaken every test below. */
    @Test
    fun `the committed vocabulary is the whole published one`() {
        val conditions = publishedConditions()
        assertEquals("expected the full published table", 60, conditions.size)
        assertEquals(1000, conditions.first().first)
        assertEquals(1282, conditions.last().first)
        assertEquals("Sunny", conditions.first().second)
    }

    /**
     * **The completeness check.** Every published code must resolve to something, and only codes
     * whose text describes no sky this scene can draw may resolve to `UNKNOWN`.
     *
     * There are none of those today, so the assertion is simply that nothing is unmapped — but it
     * is written as a list rather than a blanket `!= UNKNOWN` so that adding a genuinely
     * undrawable code later is a deliberate edit here and not an accident.
     */
    @Test
    fun `every published condition code is mapped`() {
        val deliberatelyUnknown = emptySet<Int>()
        val unmapped = publishedConditions()
            .filter { (code, _) -> WeatherApiComProvider.condition(code) == WeatherCondition.UNKNOWN }
            .filterNot { (code, _) -> code in deliberatelyUnknown }
        assertTrue("unmapped published codes: $unmapped", unmapped.isEmpty())
    }

    /**
     * The mapping must agree with what the published text actually says, not merely be non-null.
     *
     * Checked by reading the official English description of each code: anything whose text names
     * snow, sleet, blizzard or ice must land in a frozen category, and anything naming rain or
     * drizzle in a liquid one. Thunder wins over both, deliberately, so it is excluded here and
     * pinned separately.
     */
    @Test
    fun `frozen and liquid codes land on the right side`() {
        for ((code, text) in publishedConditions()) {
            val resolved = WeatherApiComProvider.condition(code)
            val lower = text.lowercase()
            if (lower.contains("thunder")) {
                assertEquals("code $code ($text)", WeatherCondition.THUNDERSTORM, resolved)
                continue
            }
            val namesFrozen = listOf("snow", "sleet", "blizzard", "ice pellets").any { lower.contains(it) }
            val namesLiquid = listOf("rain", "drizzle").any { lower.contains(it) } && !namesFrozen
            // "Freezing fog" is an obscuration, not something falling and freezing on contact --
            // which is why this needs the liquid to be named too, and why the crude
            // `contains("freezing")` this test first used was wrong about code 1147.
            val namesFreezing = lower.contains("freezing") && namesLiquid
            when {
                namesFreezing && !namesFrozen ->
                    assertEquals("code $code ($text)", WeatherCondition.FREEZING_RAIN, resolved)
                namesFrozen ->
                    assertTrue(
                        "code $code ($text) resolved to $resolved, which is not frozen",
                        resolved.isSnowy,
                    )
                namesLiquid ->
                    assertTrue(
                        "code $code ($text) resolved to $resolved, which is not rainy",
                        resolved.isRainy,
                    )
            }
        }
    }

    /** The four codes the whole cloud ramp is built on. */
    @Test
    fun `the clear-to-overcast ladder is in order`() {
        assertEquals(WeatherCondition.CLEAR, WeatherApiComProvider.condition(1000))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, WeatherApiComProvider.condition(1003))
        assertEquals(WeatherCondition.CLOUDY, WeatherApiComProvider.condition(1006))
        assertEquals(WeatherCondition.CLOUDY, WeatherApiComProvider.condition(1009))
    }

    /** Mist, fog and the dust/smoke family all obscure rather than fall. */
    @Test
    fun `obscuring conditions are fog and not precipitation`() {
        for (code in listOf(1030, 1135, 1147, 1012, 1033, 1048)) {
            assertEquals("code $code", WeatherCondition.FOG, WeatherApiComProvider.condition(code))
        }
    }

    /** Showers stay apart from steady precipitation, matching Open-Meteo's own split. */
    @Test
    fun `showers are distinguished from steady precipitation`() {
        assertEquals(WeatherCondition.SHOWERS, WeatherApiComProvider.condition(1240))
        assertEquals(WeatherCondition.RAIN, WeatherApiComProvider.condition(1189))
        assertEquals(WeatherCondition.SNOW_SHOWERS, WeatherApiComProvider.condition(1255))
        assertEquals(WeatherCondition.SNOW, WeatherApiComProvider.condition(1219))
    }

    /** An id outside the vocabulary is not clear skies. */
    @Test
    fun `an unknown code is UNKNOWN and not CLEAR`() {
        assertEquals(WeatherCondition.UNKNOWN, WeatherApiComProvider.condition(9999))
        assertEquals(WeatherCondition.UNKNOWN, WeatherApiComProvider.condition(null))
    }

    // -- parsing ---------------------------------------------------------------------------------

    /** The documented shape of a `current.json` answer, with every field this app reads. */
    private val fullBody = """
        {"location":{"name":"Florence","region":"Toscana","country":"Italy",
                     "lat":43.7793,"lon":11.2463,"localtime":"2026-08-22 21:00"},
         "current":{"last_updated_epoch":1755896400,"temp_c":25.3,"is_day":0,
                    "condition":{"text":"Light rain","code":1183},
                    "wind_kph":11.2,"humidity":71,"cloud":75,
                    "precip_mm":1.4,"precip_in":0.06,"feelslike_c":25.9}}
    """.trimIndent()

    @Test
    fun `a full response maps every field this app reads`() {
        val observation = WeatherApiComProvider.parse(fullBody)!!
        assertEquals(25.3, observation.temperatureCelsius!!, 0.0001)
        assertEquals(75, observation.cloudCoverPercent)
        assertEquals(1.4, observation.precipitationMm!!, 0.0001)
        assertEquals(WeatherCondition.RAIN, observation.condition)
        assertEquals(WeatherProviderId.WEATHER_API_COM, observation.source)
    }

    /**
     * `rainMm` is attributed, never assumed. The provider reports one `precip_mm` for whatever is
     * falling, so millimetres under a snow code must not be read as rain.
     */
    @Test
    fun `precipitation is only rain when the code says it is rain`() {
        val rain = WeatherApiComProvider.parse(fullBody)!!
        assertEquals(1.4, rain.rainMm!!, 0.0001)

        val snowBody = fullBody.replace("\"code\":1183", "\"code\":1219")
        val snow = WeatherApiComProvider.parse(snowBody)!!
        assertEquals("the millimetres are still reported", 1.4, snow.precipitationMm!!, 0.0001)
        assertNull("but they are not rain", snow.rainMm)
    }

    /**
     * Two fields this provider genuinely does not report, both left null rather than zero.
     *
     * `showers` is not a measured category here, and `snow_cm` exists only in the forecast and
     * history elements. Null is what tells [WeatherSnapshotMapper] "not reported", which is a
     * different fact from "zero" and the distinction the mapper depends on.
     */
    @Test
    fun `unreported categories are null and not zero`() {
        val observation = WeatherApiComProvider.parse(fullBody)!!
        assertNull(observation.showersMm)
        assertNull(observation.snowfallCm)
    }

    @Test
    fun `a snow code still renders as snow through the mapper`() {
        val body = fullBody.replace("\"code\":1183", "\"code\":1219").replace("\"precip_mm\":1.4", "\"precip_mm\":2.0")
        val snapshot = WeatherSnapshotMapper.toSnapshot(WeatherApiComProvider.parse(body)!!)
        assertEquals(PrecipitationType.SNOW, snapshot.precipitationType)
    }

    @Test
    fun `missing optional fields do not fail the parse`() {
        val sparse = """{"current":{"condition":{"code":1000}}}"""
        val observation = WeatherApiComProvider.parse(sparse)!!
        assertNull(observation.temperatureCelsius)
        assertNull(observation.cloudCoverPercent)
        assertNull(observation.precipitationMm)
        assertEquals(WeatherCondition.CLEAR, observation.condition)
    }

    @Test
    fun `a body without a current object is malformed`() {
        assertNull(WeatherApiComProvider.parse("""{"location":{"name":"Florence"}}"""))
        assertNull(WeatherApiComProvider.parse("not json at all"))
        assertNull(WeatherApiComProvider.parse(""))
    }

    /** An error body is a 200-shaped answer that carries no observation; it must not parse. */
    @Test
    fun `an API error body does not parse as weather`() {
        val error = """{"error":{"code":2006,"message":"API key is invalid."}}"""
        assertNull(WeatherApiComProvider.parse(error))
    }

    // -- key handling ------------------------------------------------------------------------------

    @Test
    fun `the provider declares that it needs a key`() {
        assertTrue(WeatherApiComProvider.requiresApiKey)
        assertEquals(WeatherProviderId.WEATHER_API_COM, WeatherApiComProvider.id)
    }

    /** No key means no request at all, not a request that is known to be rejected. */
    @Test
    fun `a blank key reports MissingApiKey without calling out`() = runBlocking {
        assertEquals(WeatherFetchResult.MissingApiKey, WeatherApiComProvider.fetch(43.77, 11.24, ""))
        assertEquals(WeatherFetchResult.MissingApiKey, WeatherApiComProvider.fetch(43.77, 11.24, "   "))
    }

    /** The key travels in the query string the API documents, and the coordinates with it. */
    @Test
    fun `the request url is the documented one`() {
        val url = WeatherApiComProvider.requestUrl(43.77925, 11.24626, "KEY")
        assertTrue(url, url.startsWith("https://api.weatherapi.com/v1/current.json?"))
        assertTrue(url, url.contains("key=KEY"))
        assertTrue(url, url.contains("q=43.77925,11.24626"))
        // Air quality is extra payload the scene has no use for.
        assertTrue(url, url.contains("aqi=no"))
    }

    /** Nothing about this provider may be compiled into the app. */
    @Test
    fun `no api key is baked in for this provider`() {
        val url = WeatherApiComProvider.requestUrl(0.0, 0.0, "")
        assertTrue("an empty key must produce an empty key parameter", url.contains("key=&"))
    }
}
