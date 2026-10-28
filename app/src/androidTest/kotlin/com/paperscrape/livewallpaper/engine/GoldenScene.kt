package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.weather.LiveWeatherSnapshot

/**
 * A rectangle of a golden frame that the golden is specifically *about*, in the frame's own pixels.
 *
 * **On the Canvas goldens it names, and does not gate.** Their whole frame admits no differing pixel
 * ([SceneGolden.MAX_DIFFERING_FRACTION] is zero), so a frame that passes has none inside any focus
 * either, and a second count over the rectangle could never fail: what catches a moved or vanished
 * sprite is the whole-frame rule. The rectangle says **what the golden is about** -- a golden that
 * cannot name its subject is a golden nobody can re-author -- and a failing frame reports how many
 * of its differing pixels fall inside each one.
 *
 * **On the GL references it is a gate**: `GlGolden` measures each focus of a shared scene against
 * `gl-<name>.png` with a tolerance of its own (`GlGolden.Tolerance.Region`), because there the whole
 * frame cannot be held to zero and an effect confined to one patch would be lost in it.
 */
class GoldenFocus(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val label: String,
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
     * Frames drawn and discarded before the one that is compared; zero compares the first frame.
     *
     * **Why it exists.** A car's `progress` starts at `-startDelaySeconds`, i.e. *negative*
     * — off the left of the screen, waiting its turn — and only advances inside
     * `SceneObjectRenderer.update(deltaSeconds)`. A golden that draws one frame with
     * `deltaSeconds = 0` has no car in it, so without warm-up frames the traffic system would be
     * unpinned.
     *
     * **Why it stays deterministic.** Each warm-up frame advances the scene clock and the frame
     * delta by exactly [warmUpDeltaSeconds], both of which are pure inputs, so the same scene and
     * the same count always produce the same pixels. Two things in the renderer draw from an
     * unseeded `Random`. The lightning timer: `updateLightning` only touches it while a storm is
     * active, so a warmed-up scene must not be a storm **unless it pins the lightning**, which is
     * [pinLightning]. And the sky's visitors -- the sleigh, the fireworks, the Headless Horseman --,
     * from `SceneRandom.source`, `Random.Default` unless a test puts a seeded one there: a scene
     * warmed up until one is in its sky does not reproduce, and nothing here checks for it.
     * Everything else that moves is seeded (`Random(42)` for the star field) or is a pure function
     * of the clock.
     *
     * **The check is [requireDeterministicLightning].** A scene carries its own [warmUpFrames],
     * weather and customisation, which is everything `LiveWeatherSceneRules.stormActive` needs, so
     * the rule is checked rather than stated, on every golden assertion in both harnesses.
     */
    val warmUpFrames: Int = 0,
    /** One frame at the render loop's own 30 fps cadence, which is what the wallpaper runs at. */
    val warmUpDeltaSeconds: Float = 1f / 30f,
    // Readable, not just applied: a test that needs a scene plus one change (`GlDrawCallTest`) builds
    // it from these, rather than from a second description of it that could drift.
    val themeId: String = "sunset",
    val weather: LiveWeatherSnapshot? = null,
    val customise: (SceneCustomization) -> SceneCustomization = { it },
    /**
     * The patches of the frame this golden is about ([GoldenFocus]): named on the Canvas golden and
     * reported when it fails, measured against the GL reference of a shared scene. Empty for a golden
     * about the whole picture.
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
     * it fails a 576-pixel budget just as it fails the zero — it is a scene whose picture is not a
     * function of its inputs.
     *
     * **What it changes and what it does not.** [PaperRenderer.lightningStrikesEnabled] gates the
     * firing only, and only for the renderer this harness built: the wallpaper's lightning stays
     * untouched, random and off the clock. The scene on the other side of the switch is the storm
     * with everything `StormAtmosphere` drives — the darkened sky, the darkened cloud band, the
     * attenuated sun, the rain — without the bolt, which the committed frame does not hold either.
     *
     * **It is not a tolerance and it cannot hide a regression.** The frame it produces is the frame
     * the golden already pins, compared at the same zero; what it removes is the 3 % of renders that
     * would compare a different picture. If a strike ever becomes part of what a golden is about,
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
     * **A scene may not be warmed up through a storm unless it has pinned the lightning.**
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
                "or set pinLightning = true (see GoldenScene.pinLightning).",
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
