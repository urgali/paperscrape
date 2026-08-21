package com.paperscrape.livewallpaper.weather

import com.paperscrape.livewallpaper.engine.PrecipitationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How an Open-Meteo `current` block becomes weather in the scene.
 *
 * The readings below are the provider's real shapes, taken from live responses for Florence
 * (43.77, 11.26, Europe/Rome, elevation 65 m). The one that matters most is the shower:
 * Open-Meteo splits precipitation into `rain` (large-scale) and `showers` (convective), and a
 * Florence shower comes back as **`rain: 0.0`** with the millimetres in `showers`. Anything that
 * looked only at `rain` would read a downpour as a dry hour.
 */
class WeatherMappingTest {

    private fun snapshot(
        code: Int,
        precipitation: Double = 0.0,
        cloud: Int = 0,
        rain: Double = 0.0,
        showers: Double = 0.0,
        snowfall: Double = 0.0,
    ) = WeatherRepository.weatherCodeToSnapshot(code, precipitation, cloud, rain, showers, snowfall)

    // -- the cases the Florence report turned on ------------------------------------------------

    /** 2026-08-21T13:00 Florence: code 80, precipitation 1.0, rain 0.0, showers 1.0. */
    @Test
    fun `a shower reported entirely in showers is rain`() {
        val s = snapshot(code = 80, precipitation = 1.0, cloud = 100, rain = 0.0, showers = 1.0)
        assertEquals(PrecipitationType.RAIN, s.precipitationType)
        assertTrue(s.precipitationIntensity > 0f)
    }

    @Test
    fun `ordinary rain reported in rain is rain`() {
        val s = snapshot(code = 63, precipitation = 2.4, cloud = 100, rain = 2.4)
        assertEquals(PrecipitationType.RAIN, s.precipitationType)
    }

    /**
     * The mapping hole v2.12 had: millimetres are falling but the interpreted code is "overcast".
     * Happens when a shower has just stopped within the reporting hour, or sits a grid cell away.
     * The measurement wins -- it is an observation, the code is an interpretation.
     */
    @Test
    fun `measured precipitation under a non-precipitation code still rains`() {
        val s = snapshot(code = 3, precipitation = 0.4, cloud = 100, showers = 0.4)
        assertEquals(PrecipitationType.RAIN, s.precipitationType)
    }

    @Test
    fun `a positive total with no breakdown still rains`() {
        val s = snapshot(code = 3, precipitation = 0.3, cloud = 100)
        assertEquals(PrecipitationType.RAIN, s.precipitationType)
    }

    /** 2026-08-21T09:00 Florence: code 3, cloud 100, every precipitation field 0. */
    @Test
    fun `fully overcast with nothing falling stays dry`() {
        val s = snapshot(code = 3, precipitation = 0.0, cloud = 100)
        assertNull(s.precipitationType)
        assertEquals(0f, s.precipitationIntensity, 0.0001f)
        assertEquals(1f, s.cloudCoverFraction, 0.0001f)
    }

    @Test
    fun `clear sky is clear`() {
        val s = snapshot(code = 0, precipitation = 0.0, cloud = 4)
        assertNull(s.precipitationType)
        assertEquals(0.04f, s.cloudCoverFraction, 0.001f)
        assertTrue(!s.isThunderstorm)
    }

    // -- snow -----------------------------------------------------------------------------------

    @Test
    fun `a snow code is snow`() {
        assertEquals(
            PrecipitationType.SNOW,
            snapshot(code = 73, precipitation = 1.2, cloud = 100).precipitationType,
        )
    }

    /** `snowfall` is centimetres, and is what distinguishes snow from rain when both could apply. */
    @Test
    fun `measured snowfall is snow even under a rain code`() {
        val s = snapshot(code = 61, precipitation = 0.7, cloud = 100, rain = 0.0, snowfall = 0.5)
        assertEquals(PrecipitationType.SNOW, s.precipitationType)
    }

    @Test
    fun `freezing rain stays rain`() {
        val s = snapshot(code = 66, precipitation = 0.8, cloud = 100, rain = 0.8)
        assertEquals(PrecipitationType.RAIN, s.precipitationType)
    }

    // -- thunderstorms --------------------------------------------------------------------------

    @Test
    fun `a thunderstorm rains and is flagged as one`() {
        val s = snapshot(code = 95, precipitation = 6.0, cloud = 100, showers = 6.0)
        assertEquals(PrecipitationType.RAIN, s.precipitationType)
        assertTrue(s.isThunderstorm)
    }

    @Test
    fun `rain without thunder is not flagged as a storm`() {
        assertTrue(!snapshot(code = 61, precipitation = 1.0, cloud = 90, rain = 1.0).isThunderstorm)
    }

    // -- intensity -------------------------------------------------------------------------------

    @Test
    fun `intensity follows the millimetres and never disappears while it is raining`() {
        val drizzle = snapshot(code = 51, precipitation = 0.05, cloud = 90, rain = 0.05)
        val downpour = snapshot(code = 82, precipitation = 12.0, cloud = 100, showers = 12.0)
        assertTrue(drizzle.precipitationIntensity >= 0.15f) // visible, not a phantom
        assertEquals(1f, downpour.precipitationIntensity, 0.0001f)
        assertTrue(downpour.precipitationIntensity > drizzle.precipitationIntensity)
    }

    /** The sum is preferred when present; the parts stand in when it is not. */
    @Test
    fun `intensity uses the parts when the total is missing`() {
        val s = snapshot(code = 80, precipitation = 0.0, cloud = 100, showers = 4.0)
        assertEquals(0.5f, s.precipitationIntensity, 0.0001f)
    }

    @Test
    fun `a dry reading carries no intensity`() {
        assertEquals(0f, snapshot(code = 1, precipitation = 0.0, cloud = 20).precipitationIntensity, 0.0001f)
    }

    // -- staleness ------------------------------------------------------------------------------

    /**
     * A snapshot is stamped when it is built, which is what bounds how old the scene's weather can
     * be. The service refreshes at most hourly, so a reading can legitimately be up to an hour
     * behind the sky outside -- the timestamp is what makes that measurable rather than invisible.
     */
    @Test
    fun `a snapshot records when it was taken`() {
        val before = System.currentTimeMillis()
        val s = snapshot(code = 61, precipitation = 1.0, cloud = 100, rain = 1.0)
        val after = System.currentTimeMillis()
        assertTrue(s.fetchedAtMillis in before..after)
    }
}
