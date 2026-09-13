package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.weather.LiveWeatherSnapshot

/**
 * A rectangle of a golden frame that the golden is specifically *about*, in the frame's own pixels.
 *
 * The whole-frame tolerance used to be a fraction of the whole frame, and that made it blind to a
 * small sprite: a dolphin at this frame size covers about 160 px, while
 * [SceneGolden.MAX_DIFFERING_FRACTION] of a 360x800 frame was **576**. A scene whose whole point is
 * where one small sprite is painted therefore had to be measured over the patch it is painted in,
 * or it passed whatever happened there. Naming that patch is [GoldenScene.focus].
 *
 * **v4.31 took that whole-frame budget to zero**, after measuring what 576 had actually forgiven --
 * a redrawn bird, for two releases. So a focus rectangle is no longer what *catches* a moved
 * sprite; the whole-frame rule now does. It is still what says **what the golden is about**, and
 * the derived per-focus gates of v4.22 are still the demonstrations that each one would catch its
 * own regression. Neither is removed: a golden that cannot name its subject is a golden nobody can
 * re-author.
 */
class GoldenFocus(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val label: String,
    /**
     * The fraction of this rectangle allowed to differ, defaulting to the shared
     * [SceneGolden.MAX_FOCUS_DIFFERING_FRACTION] every focus has always used.
     *
     * A focus may carry a **tighter, derived** limit instead (v4.22 Fase 5): the settings gates
     * place theirs between the measured noise floor and the weakest regression that must fail,
     * with both numbers written at the declaration. The default is not a tolerance change --
     * every pre-existing focus keeps the limit it always had -- and a derived limit is only ever
     * *below* the shared one, so nothing an old focus rejected is newly forgiven.
     */
    val maxDifferingFraction: Double = SceneGolden.MAX_FOCUS_DIFFERING_FRACTION,
) {
    val area: Int get() = (right - left) * (bottom - top)
}

/**
 * One reproducible frame: a name, everything the renderer needs to draw it, and nothing else.
 *
 * A scene is a data description rather than a lambda over a live renderer so the *inputs* to a
 * golden are readable in one place. When a golden changes, the first question is "did the scene
 * change or did the drawing change", and that is only answerable if the scene is written down.
 */
class GoldenScene(
    val name: String,
    /** Fixed, so the sun and moon are always in the same place in this frame. */
    val dayPhase: SunPositionCalculator.DayPhase,
    /** Fixed scene clock. Everything that moves is a function of this and nothing else. */
    val sceneSeconds: Double = 120.0,
    /**
     * Frames drawn and discarded before the one that is compared (**v3.8**).
     *
     * Zero for every scene that existed before v3.8, which is why none of their frames moved.
     *
     * **Why this had to exist.** A car's `progress` starts at `-startDelaySeconds`, i.e. *negative*
     * — off the left of the screen, waiting its turn — and only advances inside
     * `SceneObjectRenderer.update(deltaSeconds)`. Every golden drew exactly one frame with
     * `deltaSeconds = 0`, so no car had ever entered a golden frame and the whole traffic system
     * was unpinned. The v3.7 road measurement found it; this closes it.
     *
     * **Why it stays deterministic.** Each warm-up frame advances the scene clock and the frame
     * delta by exactly [warmUpDeltaSeconds], both of which are pure inputs, so the same scene and
     * the same count always produce the same pixels. The one thing in the renderer that draws from
     * an unseeded `Random` is the lightning timer, and `updateLightning` only touches it while a
     * storm is active — so a warmed-up scene must not be a storm **unless it pins the lightning**,
     * which is [pinLightning]. Everything else that moves is seeded (`Random(42)` for the star
     * field) or is a pure function of the clock.
     *
     * **This sentence used to end at "must not be a storm", and said in the same breath that
     * [SceneGolden.assertMatches] had no way to check it.** Both halves were wrong by v4.31:
     * `wave-storm` had been a warmed-up storm since v4.28 and failed about one run in
     * thirty-two, and the check was writable all along — a scene carries its own
     * [warmUpFrames], weather and customisation, which is everything
     * `LiveWeatherSceneRules.stormActive` needs. It is [requireDeterministicLightning] now, and it
     * runs on every golden assertion in both harnesses. See `BACKLOG_v4_31.md` item 112.
     */
    val warmUpFrames: Int = 0,
    /** One frame at the render loop's own 30 fps cadence, which is what the wallpaper runs at. */
    val warmUpDeltaSeconds: Float = 1f / 30f,
    // Readable, not just applied: `SkyWaterGoldenTest` derives its gates by rendering a scene and
    // then rendering it again with one family switched off, and the second scene has to be the
    // first one plus that switch rather than a second description of it that could drift.
    val themeId: String = "sunset",
    val weather: LiveWeatherSnapshot? = null,
    val customise: (SceneCustomization) -> SceneCustomization = { it },
    /**
     * Patches of the frame checked a second time, on their own much smaller area, *in addition* to
     * the whole-frame comparison every golden gets. Empty for a golden about the whole picture.
     */
    val focus: List<GoldenFocus> = emptyList(),
    /**
     * Takes the strike timer out of this scene's render, and **may only be true for a scene that
     * needs it** — one that warms up through a thunderstorm.
     *
     * A storm is the one weather a golden cannot simply warm up: the strike interval is rolled from
     * an unseeded `Random`, exactly one frame per strike draws the veil, and the interval averages
     * 32 frames, so a warmed-up storm shows a bolt in about 3 % of its renders and the frame that
     * does is 285 858 pixels away from the one that does not. That is not a tolerance problem —
     * it failed the 576-pixel budget of v4.28 just as it fails the zero of v4.31 — it is a scene
     * whose picture is not a function of its inputs.
     *
     * **What it changes and what it does not.** [PaperRenderer.lightningStrikesEnabled] gates the
     * firing only, and only for the renderer this harness built: the wallpaper's lightning is
     * untouched, still random and still off the clock, which is the condition the maintainer put on
     * fixing this at all. The scene on the other side of the switch is the storm with everything
     * `StormAtmosphere` drives — the darkened sky, the darkened cloud band, the attenuated sun, the
     * rain — and the bolt that was never in the committed frame anyway.
     *
     * **It is not a tolerance and it cannot hide a regression.** The frame it produces is the frame
     * the golden already pins, compared at the same zero; what it removes is the 3 % of renders that
     * were comparing a different picture. If a strike ever becomes part of what a golden is about,
     * the scene it belongs in is a *cold* one, where the timer cannot fire at all.
     */
    val pinLightning: Boolean = false,
) {

    /**
     * Whether this scene runs the renderer through a live storm, which is the only state in which
     * the strike timer advances and therefore the only way an unseeded roll can reach a frame.
     *
     * Read the way [PaperRenderer] reads it, through `LiveWeatherSceneRules.stormActive` and off
     * the customisation [configure] would hand it, so the two cannot come to different answers
     * about the same scene.
     */
    val warmsUpThroughAStorm: Boolean
        get() {
            if (warmUpFrames <= 0) return false
            val precipitation = customise(defaultCustomizationFor(themeId)).precipitation
            return LiveWeatherSceneRules.stormActive(
                liveIsThunderstorm = weather?.isThunderstorm,
                themePrecipitationVisible = precipitation.visible,
                themePrecipitationIsRain = precipitation.type == PrecipitationType.RAIN,
                themeThunderstorm = precipitation.thunderstorm,
            )
        }

    /**
     * The guard [warmUpFrames]'s doc said could not be written: **a scene may not be warmed up
     * through a storm unless it has pinned the lightning.**
     *
     * Called from both golden harnesses rather than from [configure], because what it protects is a
     * *comparison* against a committed file: a capture test that warms a storm up and writes the
     * frame out is drawing a picture nobody is going to diff, and has nothing to flake against.
     *
     * It is deliberately a rule with no exemption in it. `wave-storm` is not excused from the check
     * — it satisfies it, by pinning — so the next warmed-up storm somebody writes is caught at the
     * first run instead of on whichever run in thirty-two happens to flash.
     *
     * **It runs in both directions.** [pinLightning] on a scene that is not a warmed-up storm is
     * rejected too, because the switch has exactly one job and a pin spread over scenes that do not
     * need it is how it would stop meaning anything: every scene pinned is a guard that never fires
     * again, and a golden that quietly cannot show a flash even if one day it should. The KDoc on
     * [pinLightning] says "may only be true for a scene that needs it", and this is what makes that
     * sentence a rule rather than a hope.
     */
    fun requireDeterministicLightning() {
        if (pinLightning && !warmsUpThroughAStorm) {
            throw AssertionError(
                "Golden scene '$name' pins the lightning and does not need to: it is not warmed up " +
                    "through a thunderstorm, so the strike timer cannot fire in it either way. The " +
                    "pin has one job (see GoldenScene.pinLightning) and spreading it is how it would " +
                    "stop having one -- remove it.",
            )
        }
        if (!warmsUpThroughAStorm || pinLightning) return
        throw AssertionError(
            "Golden scene '$name' is warmed up for $warmUpFrames frames with a thunderstorm " +
                "active, and has not pinned the lightning. The strike timer is the one thing in " +
                "PaperRenderer that rolls from an unseeded Random, so this frame is a coin flip: " +
                "about one render in 32 carries the veil, and that render differs from the " +
                "committed golden by the whole frame. Either take the storm out of the warm-up, " +
                "or set pinLightning = true. See BACKLOG_v4_31.md item 112.",
        )
    }

    fun configure(renderer: PaperRenderer) {
        renderer.theme = ThemeCatalog.byId(themeId)
        renderer.sceneCustomization = customise(defaultCustomizationFor(themeId))
        renderer.liveWeatherOverride = weather
        // Both scroll inputs pinned: the drift accumulates from `deltaSeconds`, which is zero for a
        // golden, and the swipe offset is an input the test must not leave to chance.
        renderer.homeScreenOffset = 0f
        renderer.swipeScrollEnabled = false
        renderer.scrollSpeed = 0f
        renderer.parallaxStrength = 1f
        // Only ever false here, and only for a scene that asked: see [pinLightning].
        renderer.lightningStrikesEnabled = !pinLightning
    }

    companion object {

        /** Midday: the sun high, full day blend. */
        fun day(hour: Float = 13f): SunPositionCalculator.DayPhase =
            SunPositionCalculator.compute(hour24 = hour)

        /** Deep night: the moon up, no day blend. */
        fun night(hour: Float = 1f): SunPositionCalculator.DayPhase =
            SunPositionCalculator.compute(hour24 = hour)
    }
}
