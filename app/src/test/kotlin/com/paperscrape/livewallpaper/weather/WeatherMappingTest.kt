package com.paperscrape.livewallpaper.weather

import com.paperscrape.livewallpaper.engine.PrecipitationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a normalised observation becomes weather in the scene.
 *
 * These are v2.13's assertions, unchanged in intent and re-pointed at [WeatherSnapshotMapper] now
 * that the provider-specific parsing has moved out from under them. The readings are Open-Meteo's
 * real shapes, taken from live responses for Florence (43.77, 11.26, Europe/Rome, elevation 65 m).
 * The one that matters most is the shower: Open-Meteo splits precipitation into `rain`
 * (large-scale) and `showers` (convective), and a Florence shower comes back as **`rain: 0.0`**
 * with the millimetres in `showers`. Anything that looked only at `rain` would read a downpour as
 * a dry hour.
 */
class WeatherMappingTest {

    private fun snapshot(
        condition: WeatherCondition,
        precipitation: Double? = 0.0,
        cloud: Int = 0,
        rain: Double? = 0.0,
        showers: Double? = 0.0,
        snowfall: Double? = 0.0,
    ) = WeatherSnapshotMapper.toSnapshot(
        WeatherObservation(
            cloudCoverPercent = cloud,
            precipitationMm = precipitation,
            rainMm = rain,
            showersMm = showers,
            snowfallCm = snowfall,
            condition = condition,
            observedAtMillis = System.currentTimeMillis(),
            source = WeatherProviderId.OPEN_METEO,
        ),
    )

    // -- the cases the Florence report turned on ------------------------------------------------

    /** 2026-08-21T13:00 Florence: code 80, precipitation 1.0, rain 0.0, showers 1.0. */
    @Test
    fun `a shower reported entirely in showers is rain`() {
        val s = snapshot(WeatherCondition.SHOWERS, precipitation = 1.0, cloud = 100, rain = 0.0, showers = 1.0)
        assertEquals(PrecipitationType.RAIN, s.precipitationType)
        assertTrue(s.precipitationIntensity > 0f)
    }

    @Test
    fun `ordinary rain reported in rain is rain`() {
        val s = snapshot(WeatherCondition.RAIN, precipitation = 2.4, cloud = 100, rain = 2.4)
        assertEquals(PrecipitationType.RAIN, s.precipitationType)
    }

    /**
     * The mapping hole v2.12 had: millimetres are falling but the interpreted code is "overcast".
     * Happens when a shower has just stopped within the reporting hour, or sits a grid cell away.
     * The measurement wins -- it is an observation, the code is an interpretation.
     */
    @Test
    fun `measured precipitation under a non-precipitation code still rains`() {
        val s = snapshot(WeatherCondition.CLOUDY, precipitation = 0.4, cloud = 100, showers = 0.4)
        assertEquals(PrecipitationType.RAIN, s.precipitationType)
    }

    @Test
    fun `a positive total with no breakdown still rains`() {
        val s = snapshot(WeatherCondition.CLOUDY, precipitation = 0.3, cloud = 100)
        assertEquals(PrecipitationType.RAIN, s.precipitationType)
    }

    /**
     * The assumption the task named explicitly, pinned: cloud cover is not precipitation. A fully
     * overcast sky with every measurement at zero and a cloud code is dry.
     */
    @Test
    fun `fully overcast with nothing falling stays dry`() {
        val s = snapshot(WeatherCondition.CLOUDY, precipitation = 0.0, cloud = 100)
        assertNull(s.precipitationType)
        assertEquals(0f, s.precipitationIntensity, 0.0001f)
        assertEquals(1f, s.cloudCoverFraction, 0.0001f)
    }

    /**
     * And its mirror image: `rain == 0` is not "nothing is falling". This is the same reading as
     * the Florence shower, stated as the assumption rather than as the case.
     */
    @Test
    fun `zero rain does not mean no precipitation`() {
        val s = snapshot(WeatherCondition.CLOUDY, precipitation = 1.0, cloud = 100, rain = 0.0, showers = 1.0)
        assertEquals(PrecipitationType.RAIN, s.precipitationType)
    }

    @Test
    fun `clear sky is clear`() {
        val s = snapshot(WeatherCondition.CLEAR, precipitation = 0.0, cloud = 4)
        assertNull(s.precipitationType)
        assertEquals(0.04f, s.cloudCoverFraction, 0.001f)
        assertTrue(!s.isThunderstorm)
    }

    // -- snow -----------------------------------------------------------------------------------

    @Test
    fun `a snow code is snow`() {
        assertEquals(
            PrecipitationType.SNOW,
            snapshot(WeatherCondition.SNOW, precipitation = 1.2, cloud = 100).precipitationType,
        )
    }

    /** `snowfall` is centimetres, and is what distinguishes snow from rain when both could apply. */
    @Test
    fun `measured snowfall is snow even under a rain code`() {
        val s = snapshot(WeatherCondition.RAIN, precipitation = 0.7, cloud = 100, rain = 0.0, snowfall = 0.5)
        assertEquals(PrecipitationType.SNOW, s.precipitationType)
    }

    /**
     * Mixed precipitation: both measurements positive. Snow wins, because the scene has to draw
     * one and frozen precipitation is the more visually distinctive of the two -- but the
     * observation kept both figures, so nothing was lost on the way in.
     */
    @Test
    fun `rain and snow falling together render as snow`() {
        val s = snapshot(WeatherCondition.SLEET, precipitation = 1.4, cloud = 100, rain = 0.9, snowfall = 0.5)
        assertEquals(PrecipitationType.SNOW, s.precipitationType)
    }

    @Test
    fun `freezing rain stays rain`() {
        val s = snapshot(WeatherCondition.FREEZING_RAIN, precipitation = 0.8, cloud = 100, rain = 0.8)
        assertEquals(PrecipitationType.RAIN, s.precipitationType)
    }

    // -- thunderstorms --------------------------------------------------------------------------

    @Test
    fun `a thunderstorm rains and is flagged as one`() {
        val s = snapshot(WeatherCondition.THUNDERSTORM, precipitation = 6.0, cloud = 100, showers = 6.0)
        assertEquals(PrecipitationType.RAIN, s.precipitationType)
        assertTrue(s.isThunderstorm)
    }

    @Test
    fun `rain without thunder is not flagged as a storm`() {
        assertTrue(!snapshot(WeatherCondition.RAIN, precipitation = 1.0, cloud = 90, rain = 1.0).isThunderstorm)
    }

    // -- absent versus zero ----------------------------------------------------------------------

    /**
     * A provider that does not report showers at all leaves the field null, and null must not be
     * read as "no showers". WeatherAPI.com is that provider; the only thing that keeps its
     * readings correct is that null and 0.0 stay distinguishable all the way to here.
     */
    @Test
    fun `an unreported showers field is not a reading of zero`() {
        val s = snapshot(WeatherCondition.RAIN, precipitation = 2.0, cloud = 100, rain = 2.0, showers = null)
        assertEquals(PrecipitationType.RAIN, s.precipitationType)
        assertEquals(0.25f, s.precipitationIntensity, 0.0001f)
    }

    @Test
    fun `an observation with no measurements at all falls back to its code`() {
        val s = snapshot(
            WeatherCondition.SNOW,
            precipitation = null,
            cloud = 100,
            rain = null,
            showers = null,
            snowfall = null,
        )
        assertEquals(PrecipitationType.SNOW, s.precipitationType)
    }

    @Test
    fun `an unknown code with no measurements is dry`() {
        val s = snapshot(
            WeatherCondition.UNKNOWN,
            precipitation = null,
            cloud = 40,
            rain = null,
            showers = null,
            snowfall = null,
        )
        assertNull(s.precipitationType)
        assertEquals(0.4f, s.cloudCoverFraction, 0.0001f)
    }

    // -- intensity -------------------------------------------------------------------------------

    @Test
    fun `intensity follows the millimetres and never disappears while it is raining`() {
        val drizzle = snapshot(WeatherCondition.DRIZZLE, precipitation = 0.05, cloud = 90, rain = 0.05)
        val downpour = snapshot(WeatherCondition.SHOWERS, precipitation = 12.0, cloud = 100, showers = 12.0)
        assertTrue(drizzle.precipitationIntensity >= 0.15f) // visible, not a phantom
        assertEquals(1f, downpour.precipitationIntensity, 0.0001f)
        assertTrue(downpour.precipitationIntensity > drizzle.precipitationIntensity)
    }

    /** The sum is preferred when present; the parts stand in when it is not. */
    @Test
    fun `intensity uses the parts when the total is missing`() {
        val s = snapshot(WeatherCondition.SHOWERS, precipitation = 0.0, cloud = 100, showers = 4.0)
        assertEquals(0.5f, s.precipitationIntensity, 0.0001f)
    }

    @Test
    fun `a dry reading carries no intensity`() {
        assertEquals(
            0f,
            snapshot(WeatherCondition.PARTLY_CLOUDY, precipitation = 0.0, cloud = 20).precipitationIntensity,
            0.0001f,
        )
    }

    // -- staleness ------------------------------------------------------------------------------

    /**
     * A snapshot carries the observation's own timestamp, which is what bounds how old the scene's
     * weather can be. The service refreshes at most hourly, so a reading can legitimately be up to
     * an hour behind the sky outside -- the timestamp is what makes that measurable.
     */
    @Test
    fun `a snapshot carries the observation's timestamp`() {
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
}
