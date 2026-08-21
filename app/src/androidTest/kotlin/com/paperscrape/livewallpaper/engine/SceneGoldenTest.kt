package com.paperscrape.livewallpaper.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.paperscrape.livewallpaper.weather.LiveWeatherSnapshot
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Visual regression tests: known scenes, drawn through the real Canvas backend, compared with
 * committed PNGs.
 *
 * The set is chosen to cover the axes that can break independently of each other -- time of day,
 * cloud cover, precipitation kind, storm strength, the lake and its traffic, and a spread of themes
 * -- rather than to be exhaustive. Twelve frames is a suite that runs in seconds and that someone
 * will actually look at when one of them fails.
 *
 * See [SceneGolden] for how a frame is made reproducible and how to regenerate one honestly.
 */
@RunWith(AndroidJUnit4::class)
class SceneGoldenTest {

    // -- Time of day ------------------------------------------------------------------------

    @Test
    fun day() = SceneGolden.assertMatches(
        GoldenScene(name = "day", dayPhase = GoldenScene.day()),
    )

    @Test
    fun night() = SceneGolden.assertMatches(
        GoldenScene(name = "night", dayPhase = GoldenScene.night()),
    )

    /** The blend between the two, which has had its own bugs (v2.12's continuity fix). */
    @Test
    fun dusk() = SceneGolden.assertMatches(
        GoldenScene(name = "dusk", dayPhase = SunPositionCalculator.compute(hour24 = 19.5f)),
    )

    // -- Weather ----------------------------------------------------------------------------

    @Test
    fun overcast() = SceneGolden.assertMatches(
        GoldenScene(
            name = "overcast",
            dayPhase = GoldenScene.day(),
            weather = weather(cloud = 1f, type = null, intensity = 0f, storm = false),
        ),
    )

    @Test
    fun rain() = SceneGolden.assertMatches(
        GoldenScene(
            name = "rain",
            dayPhase = GoldenScene.day(),
            weather = weather(cloud = 0.9f, type = PrecipitationType.RAIN, intensity = 0.6f, storm = false),
        ),
    )

    @Test
    fun snow() = SceneGolden.assertMatches(
        GoldenScene(
            name = "snow",
            dayPhase = GoldenScene.day(),
            themeId = "winter",
            weather = weather(cloud = 0.85f, type = PrecipitationType.SNOW, intensity = 0.5f, storm = false),
        ),
    )

    /**
     * The storm, minus the bolt.
     *
     * What this pins is [StormAtmosphere]'s whole output -- the darkened sky, the darker cloud band
     * and the faded sun -- against a bright midday theme, which is where v2.15's regression would
     * have been visible. The flash itself is on a timer that only advances with `deltaSeconds`, and
     * a golden drawn at `deltaSeconds = 0` never sees it: pinning it would pin a random number, not
     * a picture.
     */
    @Test
    fun thunderstorm() = SceneGolden.assertMatches(
        GoldenScene(
            name = "thunderstorm",
            dayPhase = GoldenScene.day(),
            weather = weather(cloud = 1f, type = PrecipitationType.RAIN, intensity = 1f, storm = true),
        ),
    )

    // -- The lake -----------------------------------------------------------------------------

    @Test
    fun lakeEmpty() = SceneGolden.assertMatches(
        GoldenScene(
            name = "lake-empty",
            dayPhase = GoldenScene.day(),
            customise = { it.copy(lake = it.lake.copy(visible = true, sailboatsVisible = false, dolphinsVisible = false)) },
        ),
    )

    /**
     * A crowded lake, which is the state the v3.0 fix is about.
     *
     * Every candidate present in both categories means every lane is occupied, so this frame is
     * where a lane-assignment change or a depth-ordering change shows up immediately -- the boats
     * were sharing lanes and painting over each other by index before [LakeLanes] existed.
     */
    @Test
    fun lakeBusy() = SceneGolden.assertMatches(
        GoldenScene(
            name = "lake-busy",
            dayPhase = GoldenScene.day(),
            customise = {
                it.copy(
                    lake = it.lake.copy(
                        visible = true,
                        height = 0.8f,
                        sailboatsVisible = true,
                        sailboatsDensity = 1f,
                        dolphinsVisible = true,
                        dolphinsDensity = 1f,
                    ),
                )
            },
        ),
    )

    /** Boats alone, at a scene time where several are close together across the water. */
    @Test
    fun lakeBoats() = SceneGolden.assertMatches(
        GoldenScene(
            name = "lake-boats",
            dayPhase = GoldenScene.day(),
            sceneSeconds = 47.0,
            customise = {
                it.copy(
                    lake = it.lake.copy(
                        visible = true,
                        height = 0.8f,
                        sailboatsVisible = true,
                        sailboatsDensity = 1f,
                        dolphinsVisible = false,
                    ),
                )
            },
        ),
    )

    // -- Themes -------------------------------------------------------------------------------

    /** Three themes that exercise different parts of the scene: winter dressing, a desert
     * palette with palms and no grass, and the built city. */
    @Test
    fun themeWinter() = SceneGolden.assertMatches(
        GoldenScene(name = "theme-winter", dayPhase = GoldenScene.day(), themeId = "winter"),
    )

    @Test
    fun themeDesert() = SceneGolden.assertMatches(
        GoldenScene(name = "theme-desert", dayPhase = GoldenScene.day(), themeId = "desert"),
    )

    @Test
    fun themeCity() = SceneGolden.assertMatches(
        GoldenScene(name = "theme-city", dayPhase = GoldenScene.night(), themeId = "city"),
    )

    private fun weather(cloud: Float, type: PrecipitationType?, intensity: Float, storm: Boolean) =
        LiveWeatherSnapshot(
            precipitationType = type,
            precipitationIntensity = intensity,
            cloudCoverFraction = cloud,
            isThunderstorm = storm,
            // Fixed, so nothing in the frame can depend on when the test ran.
            fetchedAtMillis = 0L,
        )
}
