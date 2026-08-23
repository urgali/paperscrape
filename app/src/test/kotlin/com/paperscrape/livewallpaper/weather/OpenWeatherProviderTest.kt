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
 * [OpenWeatherProvider], the third provider, added in v3.8.
 *
 * ### How this suite differs from the other two providers'
 *
 * OpenWeather does not publish its condition table as machine-readable JSON — it is an HTML table —
 * so the committed fixture at `src/test/resources/weather/openweather-conditions.json` is a
 * *transcription*, and a transcription can be wrong in a way a download cannot. This suite
 * therefore does not trust it on its own. It checks the mapping two independent ways:
 *
 *  1. **Against the fixture**, every id, the same completeness walk the WeatherAPI suite does.
 *  2. **Structurally**, over the whole numeric space including ids that do not exist yet, because
 *     OpenWeather's ids are grouped by their hundreds digit and the mapping is written as a
 *     `when` over that. If the group rule is right, an id nobody has transcribed still lands
 *     correctly — and [everyIdInAGroupFollowsItsGroup] is what says the rule is right.
 *
 * No test here touches the network. Every response body is a literal, and no real API key appears
 * anywhere in this file.
 */
class OpenWeatherProviderTest {

    private fun publishedConditions(): List<Triple<Int, String, String>> {
        val stream = javaClass.classLoader!!.getResourceAsStream("weather/openweather-conditions.json")
        assertNotNull("the committed conditions fixture is missing", stream)
        val doc = JSONObject(stream!!.bufferedReader().use { it.readText() })
        val array = doc.getJSONArray("conditions")
        return (0 until array.length()).map {
            val e = array.getJSONObject(it)
            Triple(e.getInt("id"), e.getString("main"), e.getString("description"))
        }
    }

    // -- the published vocabulary --------------------------------------------------------------

    @Test
    fun `the committed vocabulary covers every documented group`() {
        val conditions = publishedConditions()
        assertEquals(55, conditions.size)
        assertEquals(
            setOf(2, 3, 5, 6, 7, 8),
            conditions.map { it.first / 100 }.toSet(),
        )
        assertTrue(conditions.any { it.first == 800 })
        assertTrue(conditions.any { it.first == 804 })
    }

    @Test
    fun `every published condition id is mapped`() {
        val unmapped = publishedConditions()
            .filter { OpenWeatherProvider.condition(it.first) == WeatherCondition.UNKNOWN }
        assertTrue("unmapped published ids: ${unmapped.map { it.first }}", unmapped.isEmpty())
    }

    /**
     * The mapping must agree with what the published description says, not merely be non-null.
     *
     * `main` is OpenWeather's own group name, which is the strongest cross-check available: a code
     * whose `main` is `Snow` must not resolve to something liquid, and vice versa. The two
     * deliberate crossings — freezing rain filed under Rain, sleet filed under Snow — are named,
     * because they are exactly the cases a group-only mapping gets wrong.
     */
    @Test
    fun `each id agrees with its published group and description`() {
        for ((id, main, description) in publishedConditions()) {
            val resolved = OpenWeatherProvider.condition(id)
            when {
                id == 511 ->
                    assertEquals("$id ($description)", WeatherCondition.FREEZING_RAIN, resolved)
                id in 611..616 ->
                    assertEquals("$id ($description)", WeatherCondition.SLEET, resolved)
                main == "Thunderstorm" ->
                    assertEquals("$id ($description)", WeatherCondition.THUNDERSTORM, resolved)
                main == "Drizzle" ->
                    assertTrue("$id ($description) -> $resolved", resolved.isRainy)
                main == "Rain" ->
                    assertTrue("$id ($description) -> $resolved", resolved.isRainy)
                main == "Snow" ->
                    assertTrue("$id ($description) -> $resolved", resolved.isSnowy)
                main == "Clear" ->
                    assertEquals("$id ($description)", WeatherCondition.CLEAR, resolved)
                main == "Clouds" ->
                    assertTrue(
                        "$id ($description) -> $resolved",
                        resolved == WeatherCondition.CLOUDY || resolved == WeatherCondition.PARTLY_CLOUDY,
                    )
                else ->
                    // The whole 7xx atmosphere family: mist, smoke, haze, dust, fog, sand, ash,
                    // squall, tornado. All obscure rather than fall.
                    assertEquals("$id ($description)", WeatherCondition.FOG, resolved)
            }
        }
    }

    /**
     * **The structural check, and the reason a transcribed fixture is safe here.**
     *
     * Walks every id from 200 to 899 — most of which OpenWeather has never issued — and asserts the
     * group rule holds for all of them. An id added to the 5xx range in future is rain whether or
     * not anyone updates the fixture.
     */
    @Test
    fun `every id in a group follows its group`() {
        var checked = 0
        for (id in 200..899) {
            val resolved = OpenWeatherProvider.condition(id)
            val expected = when {
                id == 511 -> WeatherCondition.FREEZING_RAIN
                id in 520..531 -> WeatherCondition.SHOWERS
                id in 611..616 -> WeatherCondition.SLEET
                id in 620..622 -> WeatherCondition.SNOW_SHOWERS
                id / 100 == 2 -> WeatherCondition.THUNDERSTORM
                id / 100 == 3 -> WeatherCondition.DRIZZLE
                id / 100 == 5 -> WeatherCondition.RAIN
                id / 100 == 6 -> WeatherCondition.SNOW
                id / 100 == 7 -> WeatherCondition.FOG
                id == 800 -> WeatherCondition.CLEAR
                id == 801 -> WeatherCondition.PARTLY_CLOUDY
                id in 802..804 -> WeatherCondition.CLOUDY
                else -> WeatherCondition.UNKNOWN
            }
            assertEquals("id $id", expected, resolved)
            checked++
        }
        println("Filone 1: $checked OpenWeather ids checked structurally")
    }

    /** The four exceptions, called out individually so a regression names itself. */
    @Test
    fun `the exceptions a group-only mapping would get wrong`() {
        assertEquals("511 is freezing rain inside the Rain group", WeatherCondition.FREEZING_RAIN, OpenWeatherProvider.condition(511))
        assertEquals("611 is sleet inside the Snow group", WeatherCondition.SLEET, OpenWeatherProvider.condition(611))
        assertEquals("616 is rain and snow together", WeatherCondition.SLEET, OpenWeatherProvider.condition(616))
        assertEquals("521 is a rain shower", WeatherCondition.SHOWERS, OpenWeatherProvider.condition(521))
        assertEquals("621 is a snow shower", WeatherCondition.SNOW_SHOWERS, OpenWeatherProvider.condition(621))
        // ...and the neighbours they must not swallow.
        assertEquals(WeatherCondition.RAIN, OpenWeatherProvider.condition(501))
        assertEquals(WeatherCondition.SNOW, OpenWeatherProvider.condition(601))
    }

    @Test
    fun `an id outside every group is UNKNOWN and not CLEAR`() {
        assertEquals(WeatherCondition.UNKNOWN, OpenWeatherProvider.condition(100))
        assertEquals(WeatherCondition.UNKNOWN, OpenWeatherProvider.condition(999))
        assertEquals(WeatherCondition.UNKNOWN, OpenWeatherProvider.condition(null))
    }

    // -- parsing --------------------------------------------------------------------------------

    /** A representative documented response, with every field this app reads. */
    private val rainBody = """
        {"coord":{"lon":11.2463,"lat":43.7793},
         "weather":[{"id":501,"main":"Rain","description":"moderate rain","icon":"10n"}],
         "base":"stations",
         "main":{"temp":18.4,"feels_like":18.6,"temp_min":17.2,"temp_max":19.9,
                 "pressure":1012,"humidity":88},
         "visibility":8000,
         "wind":{"speed":3.6,"deg":210},
         "clouds":{"all":75},
         "rain":{"1h":2.4},
         "dt":1755896400,
         "sys":{"country":"IT","sunrise":1755840000,"sunset":1755889000},
         "timezone":7200,"id":3176959,"name":"Florence","cod":200}
    """.trimIndent()

    @Test
    fun `a full response maps every field this app reads`() {
        val o = OpenWeatherProvider.parse(rainBody)!!
        assertEquals(18.4, o.temperatureCelsius!!, 0.0001)
        assertEquals(75, o.cloudCoverPercent)
        assertEquals(2.4, o.rainMm!!, 0.0001)
        assertEquals(2.4, o.precipitationMm!!, 0.0001)
        assertEquals(WeatherCondition.RAIN, o.condition)
        assertEquals(WeatherProviderId.OPEN_WEATHER, o.source)
    }

    /**
     * **The unit trap.** `snow.1h` is documented as millimetres per hour whatever `units` says,
     * while [WeatherObservation.snowfallCm] is centimetres. Reading it straight through would make
     * every snowfall ten times deeper than reported.
     */
    @Test
    fun `snow millimetres become centimetres`() {
        val body = rainBody
            .replace("\"id\":501,\"main\":\"Rain\",\"description\":\"moderate rain\"", "\"id\":601,\"main\":\"Snow\",\"description\":\"snow\"")
            .replace("\"rain\":{\"1h\":2.4}", "\"snow\":{\"1h\":12.0}")
        val o = OpenWeatherProvider.parse(body)!!
        assertEquals("12 mm of snow is 1.2 cm, not 12", 1.2, o.snowfallCm!!, 0.0001)
        assertEquals("the total stays in millimetres", 12.0, o.precipitationMm!!, 0.0001)
        assertNull("snow is not rain", o.rainMm)
        assertEquals(WeatherCondition.SNOW, o.condition)
    }

    /** Rain and snow reported together: the total is both, and each keeps its own attribution. */
    @Test
    fun `mixed precipitation keeps both figures`() {
        val body = rainBody
            .replace("\"id\":501,\"main\":\"Rain\",\"description\":\"moderate rain\"", "\"id\":616,\"main\":\"Snow\",\"description\":\"rain and snow\"")
            .replace("\"rain\":{\"1h\":2.4}", "\"rain\":{\"1h\":2.4},\"snow\":{\"1h\":5.0}")
        val o = OpenWeatherProvider.parse(body)!!
        assertEquals(2.4, o.rainMm!!, 0.0001)
        assertEquals(0.5, o.snowfallCm!!, 0.0001)
        assertEquals("the total is both", 7.4, o.precipitationMm!!, 0.0001)
        assertEquals(WeatherCondition.SLEET, o.condition)
    }

    /**
     * A dry sky reports no `rain` object at all, and that must stay null rather than becoming zero:
     * "not reported" and "reported zero" are different facts the mapper depends on.
     */
    @Test
    fun `absent precipitation objects stay null`() {
        // A literal rather than surgery on `rainBody`: the point of this test is a response with
        // no `rain` and no `snow` key at all, and an edit that silently failed to remove one would
        // make the test pass for the wrong reason.
        val body = """
            {"coord":{"lon":11.2463,"lat":43.7793},
             "weather":[{"id":800,"main":"Clear","description":"clear sky","icon":"01d"}],
             "main":{"temp":27.1,"humidity":40,"pressure":1015},
             "wind":{"speed":1.5,"deg":90},
             "clouds":{"all":0},
             "dt":1755896400,"timezone":7200,"id":3176959,"name":"Florence","cod":200}
        """.trimIndent()
        assertTrue("the fixture must carry no precipitation keys", !body.contains("\"rain\"") && !body.contains("\"snow\""))
        val o = OpenWeatherProvider.parse(body)!!
        assertNull(o.rainMm)
        assertNull(o.snowfallCm)
        assertNull(o.precipitationMm)
        assertEquals(WeatherCondition.CLEAR, o.condition)
    }

    /** Showers are not a measured category here, only a condition id. */
    @Test
    fun `showers are never a measurement`() {
        assertNull(OpenWeatherProvider.parse(rainBody)!!.showersMm)
        val shower = rainBody.replace("\"id\":501", "\"id\":521")
        val o = OpenWeatherProvider.parse(shower)!!
        assertEquals(WeatherCondition.SHOWERS, o.condition)
        assertNull(o.showersMm)
    }

    @Test
    fun `a thunderstorm storms and rains through the mapper`() {
        val body = rainBody.replace("\"id\":501,\"main\":\"Rain\",\"description\":\"moderate rain\"", "\"id\":202,\"main\":\"Thunderstorm\",\"description\":\"thunderstorm with heavy rain\"")
        val snapshot = WeatherSnapshotMapper.toSnapshot(OpenWeatherProvider.parse(body)!!)
        assertTrue(snapshot.isThunderstorm)
        assertEquals(PrecipitationType.RAIN, snapshot.precipitationType)
    }

    @Test
    fun `a snow response renders as snow through the mapper`() {
        val body = rainBody
            .replace("\"id\":501,\"main\":\"Rain\",\"description\":\"moderate rain\"", "\"id\":602,\"main\":\"Snow\",\"description\":\"heavy snow\"")
            .replace("\"rain\":{\"1h\":2.4}", "\"snow\":{\"1h\":8.0}")
        val snapshot = WeatherSnapshotMapper.toSnapshot(OpenWeatherProvider.parse(body)!!)
        assertEquals(PrecipitationType.SNOW, snapshot.precipitationType)
    }

    @Test
    fun `overcast reports full cloud cover`() {
        val body = rainBody
            .replace("\"id\":501,\"main\":\"Rain\",\"description\":\"moderate rain\"", "\"id\":804,\"main\":\"Clouds\",\"description\":\"overcast clouds\"")
            .replace("\"all\":75", "\"all\":100")
        val snapshot = WeatherSnapshotMapper.toSnapshot(OpenWeatherProvider.parse(body)!!)
        assertEquals(1f, snapshot.cloudCoverFraction, 0.0001f)
    }

    @Test
    fun `missing optional fields do not fail the parse`() {
        val sparse = """{"weather":[{"id":800,"main":"Clear","description":"clear sky"}],"cod":200}"""
        val o = OpenWeatherProvider.parse(sparse)!!
        assertNull(o.temperatureCelsius)
        assertNull(o.cloudCoverPercent)
        assertNull(o.precipitationMm)
        assertEquals(WeatherCondition.CLEAR, o.condition)
    }

    /**
     * **An error body must not parse as weather.** This endpoint answers failures with the same
     * 200-shaped envelope carrying `cod` and `message`; treating one as clear skies would put a
     * sunny scene on screen because the key was rejected.
     */
    @Test
    fun `an API error body does not parse as weather`() {
        assertNull(OpenWeatherProvider.parse("""{"cod":401,"message":"Invalid API key."}"""))
        assertNull(OpenWeatherProvider.parse("""{"cod":"429","message":"Your account is temporary blocked."}"""))
        assertNull(OpenWeatherProvider.parse("""{"weather":[],"cod":200}"""))
        assertNull(OpenWeatherProvider.parse("not json at all"))
        assertNull(OpenWeatherProvider.parse(""))
    }

    /** The HTTP statuses this provider is most likely to see, through the shared mapper. */
    @Test
    fun `http failures are classified`() {
        assertEquals(WeatherFailure.UNAUTHORIZED, WeatherHttp.statusToFailure(401))
        assertEquals(WeatherFailure.RATE_LIMITED, WeatherHttp.statusToFailure(429))
        assertEquals(WeatherFailure.HTTP_ERROR, WeatherHttp.statusToFailure(500))
    }

    // -- key handling -----------------------------------------------------------------------------

    @Test
    fun `the provider declares that it needs a key`() {
        assertTrue(OpenWeatherProvider.requiresApiKey)
        assertEquals(WeatherProviderId.OPEN_WEATHER, OpenWeatherProvider.id)
    }

    /** No key means no request at all, not a request that is known to be rejected. */
    @Test
    fun `a blank key reports MissingApiKey without calling out`() = runBlocking {
        assertEquals(WeatherFetchResult.MissingApiKey, OpenWeatherProvider.fetch(43.77, 11.24, ""))
        assertEquals(WeatherFetchResult.MissingApiKey, OpenWeatherProvider.fetch(43.77, 11.24, "   "))
    }

    /**
     * The documented endpoint, with `units=metric` so `temp` is Celsius.
     *
     * Deliberately **not** One Call: that product requires a payment card on file even for its free
     * allowance, and everything it adds over this endpoint is data this scene has no use for.
     */
    @Test
    fun `the request url is the documented current-weather one`() {
        val url = OpenWeatherProvider.requestUrl(43.77925, 11.24626, "KEY")
        assertTrue(url, url.startsWith("https://api.openweathermap.org/data/2.5/weather?"))
        assertTrue(url, url.contains("lat=43.77925"))
        assertTrue(url, url.contains("lon=11.24626"))
        assertTrue(url, url.contains("units=metric"))
        assertTrue(url, url.contains("appid=KEY"))
        assertTrue("One Call is deliberately not used", !url.contains("onecall"))
    }

    /** Nothing about this provider may be compiled into the app. */
    @Test
    fun `no api key is baked in for this provider`() {
        val url = OpenWeatherProvider.requestUrl(0.0, 0.0, "")
        assertTrue("an empty key must produce an empty appid", url.endsWith("appid="))
    }
}
