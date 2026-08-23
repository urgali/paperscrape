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
 * -- rather than to be exhaustive. Fourteen frames is a suite that runs in seconds and that someone
 * will actually look at when one of them fails.
 *
 * See [SceneGolden] for how a frame is made reproducible and how to regenerate one honestly.
 */
@RunWith(AndroidJUnit4::class)
class SceneGoldenTest {

    // -- Time of day ------------------------------------------------------------------------

    // Three of the fourteen scenes are defined in [SharedGoldenScenes] rather than here, because
    // [GlSceneGoldenTest] renders those same three through the GL backend and compares them
    // against these same PNGs. See that object for why they are shared rather than copied.
    @Test
    fun day() = SceneGolden.assertMatches(SharedGoldenScenes.day())

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
    fun thunderstorm() = SceneGolden.assertMatches(SharedGoldenScenes.thunderstorm())

    // -- Traffic ------------------------------------------------------------------------------

    /**
     * **Two scenes with cars actually on the road** (v3.8 Filone 3).
     *
     * Until v3.8 **not one golden contained a vehicle**, and none could: a car's `progress` starts
     * at `-startDelaySeconds`, i.e. negative and off-screen, and only advances inside
     * `SceneObjectRenderer.update(deltaSeconds)` — while every golden drew a single frame with
     * `deltaSeconds = 0`. All seventeen frames had ~92% uniform tarmac. The v3.7 road measurement
     * found it; [GoldenScene.warmUpFrames] is what closes it.
     *
     * **Why 390 frames.** Measured, not guessed. The count was swept from 0 to 600 and the vehicle
     * coverage of the road band read off each frame: 0 and 150 give no vehicles at all, and 390 —
     * thirteen seconds at the render loop's own 30 fps — puts **four** of them in the band, none
     * touching either edge of the frame. A clipped vehicle is a poor regression surface, because
     * half of "it moved" is invisible off the side.
     *
     * **Why these are deterministic.** Each warm-up frame advances the scene clock and the frame
     * delta by exactly the same amount, and both are pure inputs. Neither scene is a storm, which
     * matters: the lightning timer is the one thing in the renderer that draws from an unseeded
     * `Random`, and `updateLightning` leaves it alone unless a storm is active. The star field is
     * `Random(42)`. Nothing else that moves is random at all.
     */
    @Test
    fun trafficDay() = SceneGolden.assertMatches(SharedGoldenScenes.trafficDay())

    /** The same traffic after dark, where the vehicles are lit shapes against a dark road. */
    @Test
    fun trafficNight() = SceneGolden.assertMatches(SharedGoldenScenes.trafficNight())

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
    fun lakeBusy() = SceneGolden.assertMatches(SharedGoldenScenes.lakeBusy())

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

    /**
     * **A dolphin at the exact top of its leap, in front of a sailboat one lane further out.**
     *
     * The frame the v3.1 lake fix exists for, and the one `lake-busy` could not catch: that scene
     * is a single frame at `sceneSeconds = 120.0`, and no dolphin happens to be high enough in it
     * to reach a sail. The numbers here are not a lucky draw either -- they were solved for, from
     * the same candidate noise the renderer uses, and every one of them matters:
     *
     * - `sceneSeconds = 200.0` puts dolphin candidate 0 at `sin = 1.000`, its apex exactly, with
     *   candidate 2 about half way up its own arc.
     * - at that instant dolphin 0 sits at x 19 and sailboat 0 at x 25 -- six pixels apart, so the
     *   animal is squarely inside the sail rather than beside it -- and dolphin 2 and sailboat 2
     *   repeat the situation on the other side of the frame.
     * - `height = 1f` is what makes the lake band tall enough for the eight lanes to be roughly
     *   6 px apart at this frame size, which is the proportion a real phone renders at its own
     *   lake settings. A shallower band packs every lane inside one sail and the frame stops
     *   testing depth at all.
     *
     * Both dolphins are one lane *nearer* than the boat they cross, so v3.0 painted them last and
     * they flew through the sail. Ordering on the rendered base (`LakeLanes.depthOf`) puts each of
     * them behind the boat whose waterline it has climbed above, and this PNG is what says so.
     */
    @Test
    fun lakeDolphinLeap() = SceneGolden.assertMatches(
        GoldenScene(
            name = "lake-dolphin-leap",
            dayPhase = GoldenScene.day(),
            sceneSeconds = 200.0,
            customise = {
                it.copy(
                    lake = it.lake.copy(
                        visible = true,
                        height = 1f,
                        sailboatsVisible = true,
                        sailboatsDensity = 1f,
                        dolphinsVisible = true,
                        dolphinsDensity = 1f,
                    ),
                )
            },
            // The two crossings, measured on their own. Without these the frame passes whatever
            // happens to the dolphins: each covers about 50 pixels of the sail it crosses, and the
            // whole-frame budget is 576. Reverting `LakeLanes.depthOf` to plain lane ordering moves
            // 99 pixels in total -- 0.03% of the frame, invisible to the whole-frame rule, and
            // three times over the budget of each patch below.
            focus = listOf(
                GoldenFocus(4, 424, 40, 446, "dolphin 0 at its apex, inside sailboat 0's sail"),
                GoldenFocus(300, 454, 336, 476, "dolphin 2 mid-climb, inside sailboat 2's sail"),
            ),
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
