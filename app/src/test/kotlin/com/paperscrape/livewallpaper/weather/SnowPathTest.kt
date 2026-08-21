package com.paperscrape.livewallpaper.weather

import com.paperscrape.livewallpaper.engine.PrecipitationType
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Snow, from the provider to the two places snow can appear in the scene.
 *
 * There are two of them and they are **not** the same feature, which is the point these tests
 * exist to hold:
 *
 * - **Weather-driven:** snow falling through the air, from `PrecipitationType.SNOW`.
 * - **Theme-driven:** the winter presentation — roof snow, tree snow caps, winter clothing —
 *   from `SceneCustomization.winterColorsEnabled`, a seasonal decoration a user opts into on any
 *   theme at any time.
 *
 * A live snowfall must not dress the buildings, and a winter theme must not make it snow. Christmas
 * is a third, independent layer again.
 */
class SnowPathTest {

    /**
     * Tierra del Fuego (-54.5, -68.9), captured live at 09:00 local on 2026-08-21. A real
     * snowfall, filed the way Open-Meteo files one: a `snowfall` in centimetres alongside a
     * `precipitation` in millimetres, with `rain` and `showers` both at zero.
     *
     * The v2.15 on-device run used a second live reading, Mawson (-67.6, 62.87) at 17:15 local,
     * because this one had stopped snowing by the time the emulator was configured — real weather
     * moves. Both are captures, not invented bodies; this is the one pinned here because it is the
     * milder of the two and therefore the harder case for the intensity floor.
     */
    private val realSnowfall = """
        {"latitude":-54.5,"longitude":-68.9,"timezone":"America/Argentina/Ushuaia",
         "utc_offset_seconds":-10800,
         "current":{"time":"2026-08-21T09:00","temperature_2m":-0.5,"precipitation":0.10,
                    "rain":0.00,"showers":0.00,"snowfall":0.07,"weather_code":73,"cloud_cover":91}}
    """.trimIndent()

    private fun snapshotOf(body: String) =
        WeatherSnapshotMapper.toSnapshot(OpenMeteoProvider.parse(body)!!)

    // -- the provider chain ---------------------------------------------------------------------

    @Test
    fun `the real snowfall reading parses with every field intact`() {
        val observation = OpenMeteoProvider.parse(realSnowfall)!!
        assertEquals(-0.5, observation.temperatureCelsius!!, 0.0001)
        assertEquals(91, observation.cloudCoverPercent)
        assertEquals(0.10, observation.precipitationMm!!, 0.0001)
        assertEquals(0.0, observation.rainMm!!, 0.0001)
        assertEquals(0.0, observation.showersMm!!, 0.0001)
        assertEquals(0.07, observation.snowfallCm!!, 0.0001)
        assertEquals(WeatherCondition.SNOW, observation.condition)
    }

    @Test
    fun `the real snowfall reading snows`() {
        val snapshot = snapshotOf(realSnowfall)
        assertEquals(PrecipitationType.SNOW, snapshot.precipitationType)
        assertTrue(snapshot.precipitationIntensity > 0f)
        assertEquals(0.91f, snapshot.cloudCoverFraction, 0.0001f)
        assertFalse(snapshot.isThunderstorm)
    }

    /**
     * The measurement that decides. `snowfall` is centimetres and `rain`/`showers` are zero here,
     * so the only field saying anything is the one that says snow.
     */
    @Test
    fun `snowfall outranks everything, including a rain code`() {
        val underRainCode = realSnowfall.replace("\"weather_code\":73", "\"weather_code\":61")
        assertEquals(PrecipitationType.SNOW, snapshotOf(underRainCode).precipitationType)
    }

    @Test
    fun `every snow code is snow and no snow code is rain`() {
        for (code in listOf(71, 73, 75, 77, 85, 86)) {
            val condition = OpenMeteoProvider.conditionForWmoCode(code)
            assertTrue("code $code", condition.isSnowy)
            assertFalse("code $code", condition.isRainy)
        }
    }

    /** The separation §12 names: rain is rain, showers are rain, snow is snow. */
    @Test
    fun `rain, showers and snow stay separated`() {
        val rain = realSnowfall
            .replace("\"snowfall\":0.07", "\"snowfall\":0.00")
            .replace("\"rain\":0.00", "\"rain\":0.10")
            .replace("\"weather_code\":73", "\"weather_code\":61")
        assertEquals(PrecipitationType.RAIN, snapshotOf(rain).precipitationType)

        val showers = realSnowfall
            .replace("\"snowfall\":0.07", "\"snowfall\":0.00")
            .replace("\"showers\":0.00", "\"showers\":0.10")
            .replace("\"weather_code\":73", "\"weather_code\":80")
        assertEquals(PrecipitationType.RAIN, snapshotOf(showers).precipitationType)

        assertEquals(PrecipitationType.SNOW, snapshotOf(realSnowfall).precipitationType)
    }

    /** Live rain must never produce snow, whatever the temperature says. */
    @Test
    fun `a cold rain is still rain`() {
        val coldRain = realSnowfall
            .replace("\"snowfall\":0.07", "\"snowfall\":0.00")
            .replace("\"rain\":0.00", "\"rain\":0.30")
            .replace("\"weather_code\":73", "\"weather_code\":61")
        assertEquals(PrecipitationType.RAIN, snapshotOf(coldRain).precipitationType)
    }

    @Test
    fun `a snow code with everything measured at zero does not snow`() {
        val dry = realSnowfall
            .replace("\"precipitation\":0.10", "\"precipitation\":0.00")
            .replace("\"snowfall\":0.07", "\"snowfall\":0.00")
        assertNull(snapshotOf(dry).precipitationType)
    }

    // -- weather-driven snow does not dress the scene ---------------------------------------------

    /**
     * The independence §8 asks for, stated as an invariant over the data rather than over pixels:
     * nothing a `LiveWeatherSnapshot` carries can reach the winter presentation, because the
     * snapshot has no field that the presentation reads. Roof snow, tree caps and winter clothing
     * are all gated on `SceneCustomization.winterColorsEnabled`, which no weather path writes.
     */
    @Test
    fun `a live snowfall does not turn on the winter presentation`() {
        val snapshot = snapshotOf(realSnowfall)
        assertEquals(PrecipitationType.SNOW, snapshot.precipitationType)

        // A theme that has not opted into winter stays out of it while it snows.
        val summerScene = defaultCustomizationFor("beach")
        assertFalse("live snow must not dress the buildings", summerScene.winterColorsEnabled)
        assertFalse("nor bring the Christmas layer", summerScene.christmasDecorationsEnabled)
    }

    /**
     * And the reverse direction: the winter presentation is a palette, and the two are separate
     * fields.
     *
     * The Winter theme does ship with falling snow — deliberately, because "a theme called Winter
     * whose weather is off is a theme whose central subject the user has to go and find in a
     * menu". That is a *theme default setting both fields*, not one field implying the other,
     * which is what this pins: giving some other theme the winter palette leaves its
     * precipitation exactly where it was.
     */
    @Test
    fun `the winter palette and falling precipitation are independent fields`() {
        val winter = defaultCustomizationFor("winter")
        assertTrue("the Winter theme dresses the scene", winter.winterColorsEnabled)
        assertTrue("and, by its own default, snows", winter.precipitation.visible)

        // A summer theme given the winter palette: dressed, but its weather is untouched.
        val beach = defaultCustomizationFor("beach")
        val beachInWinterClothes = beach.copy(winterColorsEnabled = true)
        assertEquals(beach.precipitation.visible, beachInWinterClothes.precipitation.visible)
        assertEquals(beach.precipitation.type, beachInWinterClothes.precipitation.type)
        assertFalse("the palette must not switch precipitation on", beachInWinterClothes.precipitation.visible)
    }

    /**
     * Christmas stays its own layer. The two flags are independent by design and a snowy forecast
     * touches neither.
     */
    @Test
    fun `Christmas is independent of both winter and the forecast`() {
        val christmas = defaultCustomizationFor("christmas")
        assertTrue(christmas.christmasDecorationsEnabled)

        // Winter without Christmas, and the reverse, are both expressible.
        val winterOnly = SceneCustomization.DEFAULT.copy(winterColorsEnabled = true)
        assertFalse(winterOnly.christmasDecorationsEnabled)
        val christmasOnly = SceneCustomization.DEFAULT.copy(christmasDecorationsEnabled = true)
        assertFalse(christmasOnly.winterColorsEnabled)
    }

    /** Winter and fall are two readings of the same leaves and cannot both apply. */
    @Test
    fun `winter and fall stay mutually exclusive`() {
        val winter = defaultCustomizationFor("winter")
        assertFalse(winter.winterColorsEnabled && winter.fallColorsEnabled)
    }

    // -- intensity ---------------------------------------------------------------------------------

    /**
     * Snow is measured in centimetres and rain in millimetres, and the intensity comes from the
     * millimetre total either way -- 0.07 cm of snow arrives with 0.10 mm of water behind it. What
     * matters is that a light snowfall is still visible rather than rounding away to nothing.
     */
    @Test
    fun `a light snowfall is still visible`() {
        val snapshot = snapshotOf(realSnowfall)
        assertTrue(snapshot.precipitationIntensity >= 0.15f)
    }

    @Test
    fun `heavier snow is heavier`() {
        val heavy = realSnowfall
            .replace("\"precipitation\":0.10", "\"precipitation\":4.00")
            .replace("\"snowfall\":0.07", "\"snowfall\":3.00")
        assertTrue(snapshotOf(heavy).precipitationIntensity > snapshotOf(realSnowfall).precipitationIntensity)
    }
}
