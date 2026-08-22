package com.paperscrape.livewallpaper.weather

import com.paperscrape.livewallpaper.engine.PrecipitationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Florence report, pinned end to end.
 *
 * **What was observed.** On a clean Android 17 emulator with Custom Location = Florence
 * (43.77925, 11.24626), Live Weather on and Open-Meteo selected, the wallpaper drew rain from a
 * sky the forecast said was completely dry. The captured request and response, from the running
 * app at 13:15 local (Europe/Rome, +7200 s):
 *
 * ```
 * GET https://api.open-meteo.com/v1/forecast?latitude=43.77925109863281&longitude=11.246259689331055
 *     &current=temperature_2m,precipitation,rain,showers,snowfall,weather_code,cloud_cover&timezone=auto
 * -> 200
 * {"current":{"time":"2026-08-21T13:15","temperature_2m":25.3,"precipitation":0.00,"rain":0.00,
 *             "showers":0.00,"snowfall":0.00,"weather_code":80,"cloud_cover":100}}
 * ```
 *
 * Four measurements at zero, and a `weather_code` of 80 -- "slight rain showers". Fifteen minutes
 * earlier the same coordinates returned `weather_code: 3` with the same four zeroes, so the code
 * was flipping between "overcast" and "showers" across a dry hour while the measurements never
 * moved. v2.13's mapper let the code overrule all four, and the scene rained.
 *
 * These tests run the real captured body through the real parser and the real mapper. Nothing here
 * is a hand-built observation: if the parser stops reading a field, or the mapper starts trusting
 * the code again, this fails.
 */
class DryCodeRegressionTest {

    /** Verbatim, as logged from the device. */
    private val florenceDryUnderShowerCode = """
        {"latitude":43.78,"longitude":11.24,"generationtime_ms":0.14734268188476562,
         "utc_offset_seconds":7200,"timezone":"Europe/Rome","timezone_abbreviation":"GMT+2",
         "elevation":48.0,
         "current_units":{"time":"iso8601","interval":"seconds","temperature_2m":"°C",
                          "precipitation":"mm","rain":"mm","showers":"mm","snowfall":"cm",
                          "weather_code":"wmo code","cloud_cover":"%"},
         "current":{"time":"2026-08-21T13:15","interval":900,"temperature_2m":25.3,
                    "precipitation":0.00,"rain":0.00,"showers":0.00,"snowfall":0.00,
                    "weather_code":80,"cloud_cover":100}}
    """.trimIndent()

    private fun snapshotOf(body: String) =
        WeatherSnapshotMapper.toSnapshot(OpenMeteoProvider.parse(body)!!)

    // -- the reported bug ---------------------------------------------------------------------------

    /**
     * The whole report in one assertion: this exact response must produce a fully clouded sky with
     * nothing falling from it.
     */
    @Test
    fun `the Florence response draws full cloud and no rain`() {
        val snapshot = snapshotOf(florenceDryUnderShowerCode)
        assertNull("code 80 with every measurement at zero must not rain", snapshot.precipitationType)
        assertEquals(0f, snapshot.precipitationIntensity, 0.0001f)
        assertEquals(1f, snapshot.cloudCoverFraction, 0.0001f)
        assertTrue(!snapshot.isThunderstorm)
    }

    /** The parser's half: every field arrives, and the zeroes arrive as zeroes rather than as nulls. */
    @Test
    fun `the Florence response parses with all four measurements reported as zero`() {
        val observation = OpenMeteoProvider.parse(florenceDryUnderShowerCode)!!
        assertEquals(25.3, observation.temperatureCelsius!!, 0.0001)
        assertEquals(100, observation.cloudCoverPercent)
        assertNotNull(observation.precipitationMm)
        assertNotNull(observation.rainMm)
        assertNotNull(observation.showersMm)
        assertNotNull(observation.snowfallCm)
        assertEquals(0.0, observation.precipitationMm!!, 0.0001)
        assertEquals(0.0, observation.rainMm!!, 0.0001)
        assertEquals(0.0, observation.showersMm!!, 0.0001)
        assertEquals(0.0, observation.snowfallCm!!, 0.0001)
        // The code is still carried. It is read, it simply does not get to outvote the numbers.
        assertEquals(WeatherCondition.SHOWERS, observation.condition)
    }

    /** The same shape under the other code the endpoint alternated with. */
    @Test
    fun `the same zeroes under an overcast code are also dry`() {
        val body = florenceDryUnderShowerCode.replace("\"weather_code\":80", "\"weather_code\":3")
        val snapshot = snapshotOf(body)
        assertNull(snapshot.precipitationType)
        assertEquals(1f, snapshot.cloudCoverFraction, 0.0001f)
    }

    /** And under every other precipitation code, so the rule is not code-by-code. */
    @Test
    fun `no precipitation code can rain on four zeroes`() {
        for (code in listOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99)) {
            val body = florenceDryUnderShowerCode.replace("\"weather_code\":80", "\"weather_code\":$code")
            assertNull("code $code", snapshotOf(body).precipitationType)
        }
    }

    // -- and the reverse must still hold ------------------------------------------------------------

    /**
     * v2.12's bug, which this must not reintroduce: millimetres falling under a code that does not
     * mention them. The measurement wins in *both* directions -- that is the single rule.
     */
    @Test
    fun `measured millimetres under an overcast code still rain`() {
        val body = florenceDryUnderShowerCode
            .replace("\"weather_code\":80", "\"weather_code\":3")
            .replace("\"precipitation\":0.00", "\"precipitation\":0.40")
            .replace("\"showers\":0.00", "\"showers\":0.40")
        assertEquals(PrecipitationType.RAIN, snapshotOf(body).precipitationType)
    }

    /** The v2.13 Florence shower: rain at zero, the millimetres filed under showers. */
    @Test
    fun `cloud cover with showers rains`() {
        val body = florenceDryUnderShowerCode
            .replace("\"precipitation\":0.00", "\"precipitation\":1.00")
            .replace("\"showers\":0.00", "\"showers\":1.00")
        val snapshot = snapshotOf(body)
        assertEquals(PrecipitationType.RAIN, snapshot.precipitationType)
        assertEquals(1f, snapshot.cloudCoverFraction, 0.0001f)
        assertTrue(snapshot.precipitationIntensity > 0f)
    }

    @Test
    fun `cloud cover with rain rains`() {
        val body = florenceDryUnderShowerCode
            .replace("\"precipitation\":0.00", "\"precipitation\":2.40")
            .replace("\"rain\":0.00", "\"rain\":2.40")
        val snapshot = snapshotOf(body)
        assertEquals(PrecipitationType.RAIN, snapshot.precipitationType)
        assertEquals(0.3f, snapshot.precipitationIntensity, 0.0001f)
    }

    /** Cloud cover on its own is never precipitation, at any percentage. */
    @Test
    fun `cloud cover without any measurement never rains`() {
        for (cover in listOf(0, 25, 50, 75, 100)) {
            val body = florenceDryUnderShowerCode.replace("\"cloud_cover\":100", "\"cloud_cover\":$cover")
            val snapshot = snapshotOf(body)
            assertNull("cover $cover", snapshot.precipitationType)
            assertEquals(cover / 100f, snapshot.cloudCoverFraction, 0.0001f)
        }
    }

    /**
     * A positive total with no sub-field to explain it. The kind is unknown from the numbers, so
     * here -- and only here -- the code chooses between snow and rain.
     */
    @Test
    fun `precipitation without rain or showers falls back to the code for its kind`() {
        val rainy = florenceDryUnderShowerCode.replace("\"precipitation\":0.00", "\"precipitation\":0.30")
        assertEquals(PrecipitationType.RAIN, snapshotOf(rainy).precipitationType)

        val snowy = rainy.replace("\"weather_code\":80", "\"weather_code\":73")
        assertEquals(PrecipitationType.SNOW, snapshotOf(snowy).precipitationType)

        val neither = rainy.replace("\"weather_code\":80", "\"weather_code\":3")
        assertEquals(PrecipitationType.RAIN, snapshotOf(neither).precipitationType)
    }

    @Test
    fun `measured snowfall is snow whatever the code says`() {
        val body = florenceDryUnderShowerCode
            .replace("\"precipitation\":0.00", "\"precipitation\":0.70")
            .replace("\"snowfall\":0.00", "\"snowfall\":0.50")
        assertEquals(PrecipitationType.SNOW, snapshotOf(body).precipitationType)
    }

    /**
     * The case the code fallback exists for: a response with no measurement fields at all, which
     * Open-Meteo's customer endpoint can return. Here the code is the only evidence there is.
     */
    @Test
    fun `a response with no measurements at all still uses its code`() {
        val body = """{"current":{"time":"2026-08-21T13:15","weather_code":80,"cloud_cover":100}}"""
        val observation = OpenMeteoProvider.parse(body)!!
        assertNull(observation.precipitationMm)
        assertNull(observation.rainMm)
        assertEquals(PrecipitationType.RAIN, WeatherSnapshotMapper.toSnapshot(observation).precipitationType)
    }

    @Test
    fun `a response with no measurements and a snow code snows`() {
        val body = """{"current":{"weather_code":73,"cloud_cover":100}}"""
        assertEquals(PrecipitationType.SNOW, snapshotOf(body).precipitationType)
    }

    // -- timestamps ---------------------------------------------------------------------------------

    /**
     * The snapshot is stamped when the observation was taken, and the fetch is what bounds its age.
     * A reading is at most one refresh interval old; nothing in the pipeline may present an older
     * one as current, and the stamp is what makes that checkable rather than assumed.
     */
    @Test
    fun `a snapshot carries the observation's own timestamp, not the render time`() {
        val stamped = WeatherSnapshotMapper.toSnapshot(
            WeatherObservation(
                cloudCoverPercent = 100,
                precipitationMm = 1.0,
                rainMm = 1.0,
                condition = WeatherCondition.RAIN,
                observedAtMillis = 1_700_000_000_000L,
                source = WeatherProviderId.OPEN_METEO,
            ),
        )
        assertEquals(1_700_000_000_000L, stamped.fetchedAtMillis)
    }

    /** A freshly parsed observation is stamped now, so "current" means current. */
    @Test
    fun `a freshly parsed response is stamped at parse time`() {
        val before = System.currentTimeMillis()
        val observation = OpenMeteoProvider.parse(florenceDryUnderShowerCode)!!
        val after = System.currentTimeMillis()
        assertTrue(observation.observedAtMillis in before..after)
    }

    /** A stale reading stays distinguishable: the stamp travels with it, unmodified. */
    @Test
    fun `a stale snapshot keeps its original stamp through the mapper`() {
        val hourOld = System.currentTimeMillis() - 60 * 60 * 1000L
        val snapshot = WeatherSnapshotMapper.toSnapshot(
            WeatherObservation(
                cloudCoverPercent = 40,
                precipitationMm = 0.0,
                rainMm = 0.0,
                showersMm = 0.0,
                snowfallCm = 0.0,
                condition = WeatherCondition.PARTLY_CLOUDY,
                observedAtMillis = hourOld,
                source = WeatherProviderId.OPEN_METEO,
            ),
        )
        assertEquals(hourOld, snapshot.fetchedAtMillis)
        assertTrue(System.currentTimeMillis() - snapshot.fetchedAtMillis >= 60 * 60 * 1000L)
    }

    // -- the other provider reaches the same answer --------------------------------------------------

    /**
     * WeatherAPI.com's equivalent: a code that says rain with a `precip_mm` of zero. Same rule,
     * same result -- the normalisation is what makes that true, not two parallel implementations.
     */
    @Test
    fun `a rain code with zero precipitation is also dry`() {
        val body = """
            {"location":{"name":"Florence","lat":43.78,"lon":11.25},
             "current":{"temp_c":25.3,"precip_mm":0.0,"cloud":100,
                        "condition":{"text":"Light rain","code":1183}}}
        """.trimIndent()
        val observation = WeatherApiComProvider.parse(body)!!
        val snapshot = WeatherSnapshotMapper.toSnapshot(observation)
        assertNull(snapshot.precipitationType)
        assertEquals(1f, snapshot.cloudCoverFraction, 0.0001f)
    }
}
