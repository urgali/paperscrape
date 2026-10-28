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
 * particular count. How many Canvas goldens there are is a count of the `SceneGolden.assertMatches`
 * calls under `src/androidTest`, not a number kept by hand here.
 *
 * See [SceneGolden] for how a frame is made reproducible and how to regenerate one honestly.
 */
@RunWith(AndroidJUnit4::class)
class SceneGoldenTest {

    // -- Time of day ------------------------------------------------------------------------

    // Some scenes are defined in [SharedGoldenScenes] rather than here. Three of those --
    // `day`, `lakeBusy` and `thunderstorm` -- are rendered by [GlSceneGoldenTest] through the GL
    // backend and compared against these same PNGs, which is why they are defined once rather
    // than copied; the traffic pair lives there for the same reason against `TrafficGoldenTest`.
    @Test
    fun day() = SceneGolden.assertMatches(SharedGoldenScenes.day())

    @Test
    fun night() = SceneGolden.assertMatches(
        GoldenScene(name = "night", dayPhase = GoldenScene.night()),
    )

    /** The blend between the two, which has its own continuity to keep: no step between day and night. */
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
     * and the faded sun -- against a bright midday theme, where a storm that fails to darken shows
     * most. The flash itself is on a timer that only advances with `deltaSeconds`, and
     * a golden drawn at `deltaSeconds = 0` never sees it: pinning it would pin a random number, not
     * a picture.
     */
    @Test
    fun thunderstorm() = SceneGolden.assertMatches(SharedGoldenScenes.thunderstorm())

    // -- Traffic ------------------------------------------------------------------------------

    /**
     * **Two scenes with cars actually on the road.**
     *
     * A single frame drawn with `deltaSeconds = 0` holds no vehicle: a car's `progress` starts at
     * `-startDelaySeconds`, negative and off-screen, and only advances inside
     * `SceneObjectRenderer.update(deltaSeconds)`. [GoldenScene.warmUpFrames] runs the scene first, so
     * the road carries traffic.
     *
     * **Why 390 frames.** Measured, not guessed: the vehicle coverage of the road band read off frame
     * counts from 0 to 600. 0 and 150 give no vehicles at all, and 390 -- thirteen seconds at the
     * render loop's own 30 fps -- puts **four** of them in the band, none touching either edge of the
     * frame. A clipped vehicle is a poor regression surface, because half of "it moved" is invisible
     * off the side.
     *
     * **Why these are deterministic.** Each warm-up frame advances the scene clock and the frame
     * delta by exactly the same amount, and both are pure inputs. Neither scene is a storm, and the
     * theme sends no visitor across the sky: the lightning timer and the visitors -- the sleigh, the
     * fireworks, the Horseman -- are what the renderer draws from an unseeded random source, and
     * `updateLightning` leaves the timer alone unless a storm is active. The star field is
     * `Random(42)`.
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
     * A crowded lake: every candidate present in both categories means every lane is occupied, so
     * this frame is where a lane-assignment change or a depth-ordering change shows up immediately --
     * boats sharing a lane would paint over each other by index, which [LakeLanes] prevents.
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
     * The frame the lake's depth order exists for, and one `lake-busy` cannot show: that scene is a
     * single frame at `sceneSeconds = 120.0`, and no dolphin happens to be high enough in it to
     * reach a sail. The numbers here are not a lucky draw either -- they are solved for, from the
     * same candidate noise the renderer uses, and every one of them matters:
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
     * Both dolphins are one lane *nearer* than the boat they cross, so an order by lane alone paints
     * them last and they fly through the sail. Ordering on the rendered base (`LakeLanes.depthOf`)
     * puts each of them behind the boat whose waterline it has climbed above, and this PNG is what
     * says so.
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
            // The two crossings, named. Each is a few dozen pixels of the sail it crosses, and an
            // order by lane alone moves them and nothing else; the whole frame admits no differing
            // pixel ([SceneGolden.MAX_DIFFERING_FRACTION]) and fails on them, and the rectangles say
            // which pixels the depth order is about.
            focus = listOf(
                GoldenFocus(4, 424, 40, 446, "dolphin 0 at its apex, inside sailboat 0's sail"),
                GoldenFocus(300, 454, 336, 476, "dolphin 2 mid-climb, inside sailboat 2's sail"),
            ),
        ),
    )

    // -- Themes -------------------------------------------------------------------------------

    /**
     * The built city at night, the one theme frame this class owns.
     *
     * Winter and Desert by day are not here: `PeopleGoldenTest`'s `people-mixed` and
     * `people-commercial` build their scenes from those themes' defaults, so a theme frame here
     * would be the same inputs and the same PNG, and a second PNG of one scene has no way to fail
     * that the first does not already have (`GoldenUniquenessTest`). `theme-city` is a different
     * frame: `night()` rather than `day()`, and no other golden renders the built city after dark.
     */
    @Test
    fun themeCity() = SceneGolden.assertMatches(
        GoldenScene(name = "theme-city", dayPhase = GoldenScene.night(), themeId = "city"),
    )

    // -- Seasonal decoration --------------------------------------------------------------------

    /**
     * **The carved moon, with realistic phases switched on**.
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
     * four phase silhouettes, without the carved face and the light inside it (the carved moon wears the moon's own colour
     * too, decision 74), which is the whole 79-pixel disc moving. It is the same requirement `SettingsGateScenesTest`
     * chooses its scenes by: a proof needs a frame that could fail.
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
     * scene. It also brings the horror sky and the pumpkins with it, so the frame carries the
     * theme's own night, which no golden of another theme draws.
     *
     * ### The clouds are off
     *
     * The cloud band is drawn after the celestial body, so a cloud in front of the disc hides the
     * sprite this golden is named after, and a golden that cannot see that sprite asserts nothing
     * about it. The clouds are turned off here; nothing else in the scene is arranged around the
     * moon.
     *
     * ### The focus, derived
     *
     * The Halloween theme ships *Moon stays in place* on (decision 86), so at [GoldenScene.night]'s hour 1 the carved moon
     * is the still moon ([StillMoon]): in the middle across, `cx = 0.5 * 360 = 180`, and its middle 1.1 of its radii under
     * the cloud band's foot -- with the default `sunCloudHeight` 0.42, `cy = 800 * (0.06 + (0.6 - 0.42) * 0.5) + 0.16 * 800
     * + 1.1 * 39.6 = 291.56` --, with `radius = 360 * 0.055 * 2 = 39.6`. The sprite is blitted at
     * `CELESTIAL_DISC_ORIGIN_UNITS` (-120) scaled by `radius / 120`, so it covers exactly the disc's own 79.2 px square; the
     * rectangle below is that square rounded outward.
     *
     * What it names is the face -- the eyes, nose and grin are cut *through* the paper and lit from inside by
     * `moon_jack_o_lantern_light`, so they are a few hundred pixels of the candle's light, and the face closing up while the
     * disc stayed put moves those pixels, which the whole frame, admitting none ([SceneGolden.MAX_DIFFERING_FRACTION]),
     * fails on. The rectangle names the part of the picture this golden exists for.
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
                GoldenFocus(140, 251, 220, 332, "the carved disc, phases on and overridden"),
            ),
        ),
    )

    /**
     * **Halloween as it ships, at night, laughing and under the blood rain** (decisions 78, 81, 83 and 85): the cobwebs,
     * one a roof, and the jack-o'-lanterns on the houses and the shops (the scarecrows, at the theme's 50 %, stand none in
     * this stretch of the street: [halloweenScarecrowsBesideTheSnowmen] holds them), the moon at the moment of a laugh --
     * the scene's clock at 66.5 s, half a second into the laugh that starts at 66 s ([LaughingMoon]) -- and the rain red
     * and thick. The clouds off, as in [halloweenMoon], so the moon is in the frame; the rain from the theme's own weather
     * turned on. The Headless Horseman is not in it: a flight's height and direction are drawn from an unseeded random
     * source, as the sleigh's, so a frame with him would not be a function of its inputs; his drawing is held where it can
     * be -- `GlAtlasOccupancyTest`, `GlDrawCallTest`, `FrameAllocationTest` and `tools/assets/tests/test_horseman.py`.
     */
    @Test
    fun halloweenAsItShipsAtNight() = SceneGolden.assertMatches(
        GoldenScene(
            name = "halloween-as-it-ships-night",
            dayPhase = GoldenScene.night(),
            themeId = "halloween",
            sceneSeconds = 66.5,
            customise = {
                it.copy(
                    clouds = it.clouds.copy(visible = false),
                    precipitation = it.precipitation.copy(visible = true, type = PrecipitationType.RAIN, intensity = 0.6f),
                )
            },
        ),
    )

    /**
     * **The scarecrows in the snowmen's places, and aside where a snowman stands too** (decision 80): Halloween at night,
     * the snowmen and the scarecrows both at 100 %, so every place of the tile holds both -- the scarecrow 1.27 m to the
     * snowman's right, the pumpkin of the place at its feet. As it ships, at 50 % each, no scarecrow stands in the golden's
     * stretch of the street, so this one is where their drawing, size and place are held.
     */
    @Test
    fun halloweenScarecrowsBesideTheSnowmen() = SceneGolden.assertMatches(
        GoldenScene(
            name = "halloween-scarecrows-and-snowmen",
            dayPhase = GoldenScene.night(),
            themeId = "halloween",
            customise = {
                it.copy(
                    clouds = it.clouds.copy(visible = false),
                    snowmen = it.snowmen.copy(visible = true, density = 1f),
                    scarecrows = it.scarecrows.copy(visible = true, density = 1f),
                )
            },
        ),
    )

    /**
     * **And at noon, which is night on the Halloween theme** (the night sky by day, on as it ships, decision 82: *«deve
     * diventare il nuovo default del tema halloween»*, 2026-10-21): the whole scene at night, the carved moon standing still
     * in the middle, low over the roofs, as the theme ships (*Moon stays in place*, decision 86), the windows lit, and the
     * opening hours the clock's noon. The clouds off so the moon is in the frame; the moon not laughing (120 s is between
     * two laughs).
     */
    @Test
    fun halloweenAtNoonIsNight() = SceneGolden.assertMatches(
        GoldenScene(
            name = "halloween-night-by-day",
            dayPhase = GoldenScene.day(12f),
            themeId = "halloween",
            customise = { it.copy(clouds = it.clouds.copy(visible = false)) },
        ),
    )

    /**
     * **One bird, drawn the way the shipped build draws it, big enough to read which way it faces.**
     *
     * A sprite with a facing is judged in the directions **the shipped build reaches**, and a bird
     * reaches exactly one: `drawBirds` moves every candidate from left to right and applies no
     * horizontal mirror at all.
     *
     * So this is a frame with a bird in it, on clean sky, at the size it ships, with the focus
     * rectangle on the animal itself. It cannot say whether the drawing is good -- no test can --
     * but it makes any change to the bird's shape or its facing fail a check and put the frame in
     * front of somebody.
     *
     * **Why these numbers.** Solved from the same candidate noise the renderer uses rather than
     * chosen: at `sceneSeconds = 0.3` on this theme's seed, bird 0 sits inside the focus rectangle --
     * clear of the cloud band, clear of both edges -- with its flap term positive, which is the pose
     * the sprite is authored in rather than its vertical mirror. The clouds are switched off so the
     * frame is about the bird and nothing else.
     */
    @Test
    fun birdFacing() = SceneGolden.assertMatches(
        GoldenScene(
            name = "bird-facing",
            dayPhase = GoldenScene.day(),
            sceneSeconds = 0.3,
            customise = {
                it.copy(
                    birds = it.birds.copy(visible = true, density = 1f),
                    clouds = it.clouds.copy(visible = false),
                )
            },
            focus = listOf(
                GoldenFocus(225, 268, 292, 302, "the bird, at the size it ships, facing its own travel"),
            ),
        ),
    )

    /**
     * **A sea that moves, sorted against the boats on it.**
     *
     * The waves are the one thing the lake draws that is neither a boat nor a dolphin, and a
     * feature with no committed frame is a feature the golden net cannot see change. Beach under a
     * thunderstorm with the boats and the dolphins at full density, so the frame portrays **both**
     * halves of it: that a wave is drawn at all in bad weather, and that it sorts into the same
     * far-to-near pass as everything else on the water.
     *
     * **Why the warm-up.** A wave slot's membership may change only while it is off screen -- the
     * car rule -- so a scene rendered cold has no waves in it by construction, whatever the weather
     * says. The scene is run for 80 seconds of its own clock before the frame is taken, which is
     * more than the 38-55 s a slot takes to cross, so every slot has had its chance to turn on and
     * to drift back into view. Both warm-up numbers are pure inputs, so the frame is reproducible.
     *
     * The focus rectangle is the water and nothing else: the sky above it carries the storm veil
     * and the lightning, which have their own frames. The whole frame, admitting no differing pixel,
     * also holds the pavement in front, where Beach's adults carry umbrellas in the rain.
     *
     * **This scene is a warmed-up thunderstorm, which is why it pins the lightning.** Eighty
     * seconds of simulated storm are eighty seconds of an unseeded random source choosing when the
     * sky flashes, and one frame per strike carries the veil over the whole frame, so an unpinned
     * golden fails at random. [GoldenScene.pinLightning] takes the strike timer out of *this
     * render* and out of nothing else; the wallpaper's lightning is untouched, and the committed
     * frame has no bolt in it. [GoldenScene.requireDeterministicLightning] makes it a check, so a
     * scene that warms a storm up without the pin fails on its first run.
     */
    @Test
    fun waveStorm() = SceneGolden.assertMatches(
        GoldenScene(
            name = "wave-storm",
            dayPhase = GoldenScene.day(),
            themeId = "beach",
            warmUpFrames = 320,
            warmUpDeltaSeconds = 0.25f,
            weather = LiveWeatherSnapshot(
                precipitationType = PrecipitationType.RAIN,
                precipitationIntensity = 1f,
                cloudCoverFraction = 1f,
                isThunderstorm = true,
                fetchedAtMillis = 0L,
            ),
            customise = {
                it.copy(
                    lake = it.lake.copy(
                        visible = true,
                        sailboatsVisible = true,
                        sailboatsDensity = 1f,
                        dolphinsVisible = true,
                        dolphinsDensity = 1f,
                    ),
                )
            },
            focus = listOf(
                GoldenFocus(0, 435, 360, 545, "the water: waves, and what is in front of what"),
            ),
            pinLightning = true,
        ),
    )

    /**
     * **An umbrella in a hand, at the size it ships.**
     *
     * An umbrella is a sprite swap plus two drawn shapes on a figure a few dozen pixels tall, and a
     * feature with no committed frame is a feature the golden net cannot see change.
     *
     * **Why the warm-up, and it is the whole reason this frame needs one.** Who carries is state,
     * not a predicate: `PedestrianCarry.nextCarrying` may move only while no copy of the walker is
     * on screen, so at scene time zero **nobody carries anything**, however hard it is raining. A
     * walker crosses its tile in about 38 s (`SceneSpace.PEDESTRIAN_SPEED_NEAR = 0.026`) and is out
     * of sight for roughly half of it, so 80 seconds of scene clock is comfortably more than one
     * crossing and the street has had time to pick umbrellas up. A frame that showed none would be
     * pinning the rule failing, which is why the focus rectangle is on the pavement.
     *
     * In the rain everyone who can hold an umbrella carries one, so every adult on this pavement
     * does, and this frame is where the canopy is pinned as a picture. A golden is one scene, and
     * whether anyone carries across the *set* of themes is not something it can see:
     * `PedestrianCarryPopulationTest` holds that property and `PedestrianUmbrellaSceneTest` counts it
     * on real pixels.
     */
    @Test
    fun umbrellaRain() = SceneGolden.assertMatches(
        GoldenScene(
            name = "umbrella-rain",
            dayPhase = GoldenScene.day(),
            warmUpFrames = 320,
            warmUpDeltaSeconds = 0.25f,
            weather = weather(cloud = 0.9f, type = PrecipitationType.RAIN, intensity = 0.6f, storm = false),
            customise = {
                it.copy(people = it.people.copy(visible = true, density = 1f))
            },
            focus = listOf(
                GoldenFocus(0, 560, 360, 720, "the pavement: who is carrying, and what it looks like"),
            ),
        ),
    )

    /**
     * **The sky the rain could vanish into.**
     *
     * A derived colour with no committed frame of its worst case is one the golden net cannot see
     * fail. `PrecipitationContrastTest` names the worst rain-against-sky case the twelve themes can
     * paint, **Easter at 06:35 under live rain**, where the drop and the sky are 0.00 of Rec. 601
     * luma apart and differ in hue alone, and this frame draws exactly it. The test that names it
     * asserts that it is still the worst case, so the two cannot drift apart silently.
     *
     * The focus is the open sky between the cloud band and the hills, the only part of the frame
     * where drops against that sky can be seen at all.
     */
    @Test
    fun rainWorstSky() = SceneGolden.assertMatches(
        GoldenScene(
            name = "rain-worst-sky",
            dayPhase = SunPositionCalculator.compute(hour24 = 6.583f),
            themeId = "easter",
            weather = weather(cloud = 0.9f, type = PrecipitationType.RAIN, intensity = 0.6f, storm = false),
            focus = listOf(
                // The weakest regression that must fail, the contrast correction removed entirely,
                // moves a few hundred pixels of this band, and the whole frame admits none
                // ([SceneGolden.MAX_DIFFERING_FRACTION]).
                GoldenFocus(0, 250, 360, 470, "the band of open sky the rain crosses"),
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
