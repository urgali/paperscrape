package com.paperscrape.livewallpaper.weather

import com.paperscrape.livewallpaper.engine.PrecipitationType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **With Open-Meteo, the provider Live Weather starts with, rain is drawn as heavy as it is.**
 *
 * Open-Meteo's `current` block is 15-minutely (`"interval": 900`) and its amounts are the fifteen minutes' totals;
 * [WeatherSnapshotMapper] reads millimetres an hour, which WeatherAPI.com's `precip_mm` and OpenWeather's `rain.1h`
 * are, so the parser makes the quarter hour's amounts an hour's. Read as an hour's, the Florence shower of the fixture
 * -- `showers` 1.0 mm in fifteen minutes, 4 mm an hour -- would draw at 1.0 / 8 = 0.125, raised to the 0.15 floor; at
 * 4 mm an hour it is 4 / 8 = **0.5** of full rain. A parser that reads them as an hour's fails here on its assertion:
 * 0.15.
 */
class OpenMeteoQuarterHourTest {

    /** The endpoint's real shape for Florence (43.77925, 11.24626), the `OpenMeteoProviderTest` fixture's. */
    private val florenceShower = """
        {"latitude":43.77,"longitude":11.25,"utc_offset_seconds":7200,"timezone":"Europe/Rome",
         "current_units":{"time":"iso8601","interval":"seconds","precipitation":"mm","rain":"mm","showers":"mm","snowfall":"cm"},
         "current":{"time":"2026-08-21T13:00","interval":900,"temperature_2m":24.3,"precipitation":1.0,"rain":0.0,
                    "showers":1.0,"snowfall":0.0,"weather_code":80,"cloud_cover":100}}
    """.trimIndent()

    private fun snapshotOf(body: String) = WeatherSnapshotMapper.toSnapshot(OpenMeteoProvider.parse(body)!!)

    @Test
    fun `a quarter hour's millimetres are drawn as four times as many an hour`() {
        val snapshot = snapshotOf(florenceShower)
        assertEquals(PrecipitationType.RAIN, snapshot.precipitationType)
        assertEquals("1.0 mm in 15 minutes = 4 mm/h = half of the 8 mm/h that is full rain", 0.5f, snapshot.precipitationIntensity, 1e-6f)
    }

    @Test
    fun `the same rain reads the same from Open-Meteo and from an hourly provider`() {
        // 2.4 mm an hour: 0.6 in Open-Meteo's fifteen minutes, 2.4 in WeatherAPI.com's hour.
        val openMeteo = snapshotOf(florenceShower.replace("\"precipitation\":1.0", "\"precipitation\":0.6").replace("\"showers\":1.0", "\"showers\":0.6"))
        val weatherApi = WeatherSnapshotMapper.toSnapshot(
            WeatherApiComProvider.parse("""{"current":{"temp_c":24.3,"precip_mm":2.4,"cloud":100,"condition":{"text":"Light rain shower","code":1240}}}""")!!,
        )
        assertEquals(weatherApi.precipitationIntensity, openMeteo.precipitationIntensity, 1e-6f)
        assertEquals(0.3f, openMeteo.precipitationIntensity, 1e-6f)
    }

    @Test
    fun `the parsed amounts are an hour's, for every one of the four`() {
        val body = florenceShower.replace("\"rain\":0.0", "\"rain\":0.25").replace("\"snowfall\":0.0", "\"snowfall\":0.1")
        val observation = OpenMeteoProvider.parse(body)!!
        assertEquals(4.0, observation.precipitationMm!!, 1e-9)
        assertEquals(1.0, observation.rainMm!!, 1e-9)
        assertEquals(4.0, observation.showersMm!!, 1e-9)
        assertEquals(0.4, observation.snowfallCm!!, 1e-9)
    }

    @Test
    fun `a block that states an hour, or no interval, is read as it is`() {
        assertEquals(1.0, OpenMeteoProvider.parse(florenceShower.replace("\"interval\":900", "\"interval\":3600"))!!.precipitationMm!!, 1e-9)
        assertEquals(1.0, OpenMeteoProvider.parse(florenceShower.replace("\"interval\":900,", ""))!!.precipitationMm!!, 1e-9)
        assertEquals("nonsense is no interval", 1.0, OpenMeteoProvider.parse(florenceShower.replace("\"interval\":900", "\"interval\":0"))!!.precipitationMm!!, 1e-9)
    }
}
