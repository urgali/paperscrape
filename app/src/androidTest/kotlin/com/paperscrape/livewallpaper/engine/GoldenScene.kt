package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.weather.LiveWeatherSnapshot

/**
 * A rectangle of a golden frame that the golden is specifically *about*, in the frame's own pixels.
 *
 * The whole-frame tolerance is a fraction of the whole frame, and that makes it blind to a small
 * sprite: a dolphin at this frame size covers about 160 px, while [SceneGolden.MAX_DIFFERING_FRACTION]
 * of a 360x800 frame is 576. A scene whose whole point is where one small sprite is painted
 * therefore has to be measured over the patch it is painted in, or it passes whatever happens
 * there. Naming that patch is [GoldenScene.focus].
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
     * storm is active — so a warmed-up scene must not be a storm, which
     * [SceneGolden.assertMatches] has no way to check and [SharedGoldenScenes] therefore does not
     * do. Everything else that moves is seeded (`Random(42)` for the star field) or is a pure
     * function of the clock.
     */
    val warmUpFrames: Int = 0,
    /** One frame at the render loop's own 30 fps cadence, which is what the wallpaper runs at. */
    val warmUpDeltaSeconds: Float = 1f / 30f,
    private val themeId: String = "sunset",
    private val weather: LiveWeatherSnapshot? = null,
    private val customise: (SceneCustomization) -> SceneCustomization = { it },
    /**
     * Patches of the frame checked a second time, on their own much smaller area, *in addition* to
     * the whole-frame comparison every golden gets. Empty for a golden about the whole picture.
     */
    val focus: List<GoldenFocus> = emptyList(),
) {

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
