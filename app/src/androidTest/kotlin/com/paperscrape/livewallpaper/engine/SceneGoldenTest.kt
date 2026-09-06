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
 * -- rather than to be exhaustive. It is deliberately a suite that runs in seconds and that someone
 * will actually look at when one of them fails, which is a property of its size rather than of any
 * particular count: the number of Canvas goldens is the number of assertions in this class plus
 * `PeopleGoldenTest`'s, and is not restated here because a hand-maintained count goes stale in
 * exactly the way v4.21 found three of them had.
 *
 * See [SceneGolden] for how a frame is made reproducible and how to regenerate one honestly.
 */
@RunWith(AndroidJUnit4::class)
class SceneGoldenTest {

    // -- Time of day ------------------------------------------------------------------------

    // Some scenes are defined in [SharedGoldenScenes] rather than here. Three of those --
    // `day`, `lakeBusy` and `thunderstorm` -- are rendered by [GlSceneGoldenTest] through the GL
    // backend and compared against these same PNGs, which is why they must be defined once rather
    // than copied; the traffic pair lives there for the same reason against `TrafficGoldenTest`.
    // See that object.
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

    /**
     * The built city at night, which is the one theme frame this class still owns.
     *
     * **`theme-winter` and `theme-desert` used to stand here and were removed in v4.21.** Neither
     * was a scene of its own: `theme-winter` described the same inputs as
     * `PeopleGoldenTest.people-mixed` and `theme-desert` the same as
     * `PeopleGoldenTest.people-commercial`, because both of those build their scene from the
     * theme's defaults and the `customise` block they pass is a no-op at density 1. The four PNGs
     * came out as two, byte-identical in pairs, and a second PNG of one scene has no way to fail
     * that the first does not already have — see `GoldenUniquenessTest` for that derivation.
     *
     * Nothing stopped being asserted. Those two frames are still compared whole-frame against the
     * same pixels, under the `people-*` names, and there they additionally carry a focus rectangle;
     * the winter-dressing and desert-palette claims moved with them. What went is one of each pair
     * of identical PNGs and the assertion that carried no information of its own.
     *
     * `theme-city` stays because it is genuinely a different frame: `night()` rather than `day()`,
     * and no other golden renders the built city after dark.
     */
    @Test
    fun themeCity() = SceneGolden.assertMatches(
        GoldenScene(name = "theme-city", dayPhase = GoldenScene.night(), themeId = "city"),
    )

    // -- Seasonal decoration --------------------------------------------------------------------

    /**
     * **The carved moon, with realistic phases switched on** -- the one celestial sprite no
     * committed frame drew until v4.23 (`BACKLOG_v4_23.md` item 41).
     *
     * ### Why both flags are on, and why one of them would be worthless
     *
     * `PaperRenderer.drawMoonWithPhase` blits `moon_jack_o_lantern`, always full, and **returns
     * before it reads `moon.realisticPhases`** while `halloweenEnabled` is on; its own comment
     * carries the reason (a face that waxed and waned would be a lit fraction of a grin, which
     * reads as a rendering fault). That early return is the invariant, and a golden with Halloween
     * on and phases *off* could not pin it: with nothing to ignore, the frame would come out
     * identical whether the guard stood or fell. **Both flags on is what gives this frame a way to
     * fail** -- if the phase path ever leaked through, the disc drawn here would become one of the
     * four phase silhouettes in the moon's own colour instead of the orange lantern, which is the
     * whole 79-pixel disc moving. It is the same requirement `SettingsGateScenesTest` derives its
     * gates from: a proof needs a frame that could fail.
     *
     * Both are written down here rather than inherited. `realisticPhases` already defaults to true
     * and the `halloween` theme already presets `halloweenEnabled`, but a scene is a description of
     * its inputs ([GoldenScene]'s own doc), and an invariant that depends on two defaults staying
     * put is not written down at all.
     *
     * ### Why the `halloween` theme
     *
     * It is the one theme that presets the flag, so this frame is a configuration a user actually
     * reaches by choosing a theme rather than a pair of switches assembled onto an unrelated
     * scene. It also brings the horror sky and the pumpkins with it, which is what makes the frame
     * carry information no other golden does -- `night` and `theme-city` are the only other deep-
     * night frames and neither is this sky, this ground or this moon.
     *
     * ### The clouds are off, and that is the whole of item 40 applied to the moon
     *
     * **Measured, not assumed: the first capture of this scene did not contain the pumpkin at
     * all.** With the theme's default cloud band the moon is behind it — `CloudBand` puts the band
     * at `800 * (0.06 + (0.6 - 0.42) * 0.5) = 120`, which is the top of the disc, and the band is
     * drawn after the celestial body — so the frame came out with a faint glow above the cloud
     * line and nothing else. That is `BACKLOG_v4_23.md` item 40's finding happening again on the
     * other body: eight of the existing goldens are byte-blind to the sky for exactly this reason.
     * A golden that cannot see the sprite it is named after asserts nothing about it, so the
     * clouds are turned off here. Nothing else in the scene is arranged around the moon.
     *
     * ### The focus, derived
     *
     * At [GoldenScene.night]'s hour 1, with sunrise 6 and sunset 20, `arcT = wrap24(1 - 20) / 10 =
     * 0.5`, so `celestialX = 0.5` and `celestialY = sin(0.5*PI) = 1`: the moon is at the apex of
     * its arc, centred. With [PaperRenderer.CELESTIAL_MARGIN_FRACTION] 0.12 and the default
     * `sunCloudHeight` 0.42 that puts it at
     * `cx = 0.12*360 + 0.5*(360 - 2*0.12*360) = 180`, `cy = 0.62*800 - 0.42*800 = 160`, with
     * `radius = 360 * 0.055 * 2 = 39.6`. The sprite is blitted at `CELESTIAL_DISC_ORIGIN_UNITS`
     * (-120) scaled by `radius / 120`, so it covers exactly the disc's own 79.2 px square; the
     * rectangle below is that square rounded outward.
     *
     * It carries the shared [SceneGolden.MAX_FOCUS_DIFFERING_FRACTION], not a derived gate: this
     * golden pins a sprite, not a settings gate, and no tolerance is moved for it. What it adds
     * over the whole-frame rule is the face -- the eyes, nose and grin are cut *through* the paper,
     * so they are a few hundred pixels of sky showing through, and the frame's own budget of 576
     * would forgive the face closing up while the disc stayed put.
     */
    @Test
    fun halloweenMoon() = SceneGolden.assertMatches(
        GoldenScene(
            name = "halloween-moon",
            dayPhase = GoldenScene.night(),
            themeId = "halloween",
            customise = {
                it.copy(
                    halloweenEnabled = true,
                    moon = it.moon.copy(visible = true, realisticPhases = true),
                    clouds = it.clouds.copy(visible = false),
                )
            },
            focus = listOf(
                GoldenFocus(140, 120, 220, 200, "the carved disc, phases on and overridden"),
            ),
        ),
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
