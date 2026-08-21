package com.paperscrape.livewallpaper.weather

import com.paperscrape.livewallpaper.engine.LiveWeatherSceneRules
import com.paperscrape.livewallpaper.engine.PrecipitationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Thunderstorms, from the provider's code to whether the sky flashes.
 *
 * The lightning system itself is not new and is not duplicated here: `PaperRenderer` has had
 * `updateLightning`/`drawLightningFlash` -- a veil plus the `lightning_bolt` sprite on a 4-12 s
 * randomised timer -- since long before Live Weather existed, and Live Weather drives *that*. What
 * these tests pin is the wiring and the gate, which is where the defect was.
 */
class ThunderstormTest {

    /** The shape a real storm arrives in: measured through `showers`, `rain` at zero. */
    private val realStorm = """
        {"latitude":10.0,"longitude":-90.0,"timezone":"America/Costa_Rica","utc_offset_seconds":-21600,
         "current":{"time":"2026-08-21T06:00","temperature_2m":27.9,"precipitation":0.40,
                    "rain":0.00,"showers":0.40,"snowfall":0.00,"weather_code":95,"cloud_cover":98}}
    """.trimIndent()

    private fun snapshotOf(body: String) =
        WeatherSnapshotMapper.toSnapshot(OpenMeteoProvider.parse(body)!!)

    // -- the code reaches the scene -------------------------------------------------------------

    @Test
    fun `every WMO thunderstorm code normalises to a thunderstorm`() {
        for (code in listOf(95, 96, 99)) {
            assertEquals("code $code", WeatherCondition.THUNDERSTORM, OpenMeteoProvider.conditionForWmoCode(code))
        }
    }

    /**
     * Visual Crossing has no thunder value in its free icon set, so the `conditions` text carries
     * it. Both fields are lowercased by `parse` before they reach this function -- that is its
     * contract, and these calls honour it exactly as the parser does.
     */
    @Test
    fun `the other provider reaches the same condition`() {
        assertEquals(
            WeatherCondition.THUNDERSTORM,
            VisualCrossingProvider.condition("rain", "rain, thunderstorm, overcast", listOf("rain")),
        )
        assertEquals(
            WeatherCondition.THUNDERSTORM,
            VisualCrossingProvider.condition("thunder-showers-day", "", listOf("rain")),
        )
    }

    /** End to end through the parser, which is what does the lowercasing in production. */
    @Test
    fun `a Visual Crossing thunderstorm storms and rains`() {
        val body = "{\"currentConditions\":{\"temp\":22.0,\"precip\":6.0,\"preciptype\":[\"rain\"]," +
            "\"cloudcover\":100.0,\"conditions\":\"Rain, Thunderstorm, Overcast\",\"icon\":\"rain\"}}"
        val snapshot = WeatherSnapshotMapper.toSnapshot(VisualCrossingProvider.parse(body)!!)
        assertTrue(snapshot.isThunderstorm)
        assertEquals(PrecipitationType.RAIN, snapshot.precipitationType)
    }

    /** A real storm: rain falls, and the sky is flagged to flash. */
    @Test
    fun `a real thunderstorm rains and storms`() {
        val snapshot = snapshotOf(realStorm)
        assertEquals(PrecipitationType.RAIN, snapshot.precipitationType)
        assertTrue(snapshot.isThunderstorm)
        assertTrue(snapshot.precipitationIntensity > 0f)
        assertEquals(0.98f, snapshot.cloudCoverFraction, 0.0001f)
    }

    /** Intensity still tracks the millimetres; a storm is not a fixed level. */
    @Test
    fun `storm intensity follows the measurement`() {
        val light = snapshotOf(realStorm)
        val heavy = snapshotOf(
            realStorm.replace("\"precipitation\":0.40", "\"precipitation\":8.00")
                .replace("\"showers\":0.40", "\"showers\":8.00"),
        )
        assertTrue(heavy.precipitationIntensity > light.precipitationIntensity)
        assertEquals(1f, heavy.precipitationIntensity, 0.0001f)
        assertTrue(heavy.isThunderstorm)
    }

    // -- the gate ---------------------------------------------------------------------------------

    /**
     * The defect. A thunderstorm code with every measurement at zero is the same code-flapping
     * artefact that rained on a dry Florence afternoon in v2.13; left ungated it would have
     * flashed lightning instead. Measurements decide whether anything is happening, here too.
     */
    @Test
    fun `a thunderstorm code with nothing falling does not storm`() {
        val dry = realStorm
            .replace("\"precipitation\":0.40", "\"precipitation\":0.00")
            .replace("\"showers\":0.40", "\"showers\":0.00")
        val snapshot = snapshotOf(dry)
        assertEquals(null, snapshot.precipitationType)
        assertFalse("no rain, no storm", snapshot.isThunderstorm)
    }

    /** But a storm with no measurements *reported at all* still storms -- the code is all there is. */
    @Test
    fun `a thunderstorm code with no measurements reported still storms`() {
        val snapshot = snapshotOf("""{"current":{"weather_code":95,"cloud_cover":100}}""")
        assertEquals(PrecipitationType.RAIN, snapshot.precipitationType)
        assertTrue(snapshot.isThunderstorm)
    }

    /** Rain without thunder never flashes. */
    @Test
    fun `ordinary rain is not a storm`() {
        val rain = realStorm.replace("\"weather_code\":95", "\"weather_code\":80")
        val snapshot = snapshotOf(rain)
        assertEquals(PrecipitationType.RAIN, snapshot.precipitationType)
        assertFalse(snapshot.isThunderstorm)
    }

    /** Nor does snow, whatever else is true. */
    @Test
    fun `snow is not a storm`() {
        val snow = realStorm
            .replace("\"weather_code\":95", "\"weather_code\":73")
            .replace("\"snowfall\":0.00", "\"snowfall\":0.50")
        val snapshot = snapshotOf(snow)
        assertEquals(PrecipitationType.SNOW, snapshot.precipitationType)
        assertFalse(snapshot.isThunderstorm)
    }

    // -- which layer decides ------------------------------------------------------------------------

    @Test
    fun `the forecast decides while Live Weather is on`() {
        assertTrue(
            LiveWeatherSceneRules.stormActive(
                liveIsThunderstorm = true,
                themePrecipitationVisible = false,
                themePrecipitationIsRain = false,
                themeThunderstorm = false,
            ),
        )
        assertFalse(
            LiveWeatherSceneRules.stormActive(
                liveIsThunderstorm = false,
                themePrecipitationVisible = true,
                themePrecipitationIsRain = true,
                themeThunderstorm = true,
            ),
        )
    }

    /** With the forecast driving, none of the theme's three storm inputs changes the answer. */
    @Test
    fun `with the forecast driving, the theme's storm settings have no effect`() {
        for (visible in listOf(true, false)) {
            for (isRain in listOf(true, false)) {
                for (toggle in listOf(true, false)) {
                    assertTrue(LiveWeatherSceneRules.stormActive(true, visible, isRain, toggle))
                    assertFalse(LiveWeatherSceneRules.stormActive(false, visible, isRain, toggle))
                }
            }
        }
    }

    /** Live Weather off: the theme's own rule, unchanged -- visible, raining, and toggled on. */
    @Test
    fun `without the forecast the theme's own three conditions all still apply`() {
        assertTrue(LiveWeatherSceneRules.stormActive(null, true, true, true))
        assertFalse(LiveWeatherSceneRules.stormActive(null, false, true, true))
        assertFalse(LiveWeatherSceneRules.stormActive(null, true, false, true))
        assertFalse(LiveWeatherSceneRules.stormActive(null, true, true, false))
    }

    /**
     * The property the three layers now share: while the forecast is driving, the theme's own
     * switches change nothing in clouds, precipitation or lightning. One rule, three layers.
     */
    @Test
    fun `clouds and storm agree about who is in charge`() {
        val cloudsUnderForecast = LiveWeatherSceneRules.cloudDensity(1f, themeCloudsVisible = false, themeCloudDensity = 0f)
        val stormUnderForecast = LiveWeatherSceneRules.stormActive(true, false, false, false)
        assertTrue("clouds drawn from the forecast", cloudsUnderForecast != null)
        assertTrue("storm active from the forecast", stormUnderForecast)
    }
}
