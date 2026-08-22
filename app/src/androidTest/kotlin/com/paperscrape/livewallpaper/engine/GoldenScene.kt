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
class GoldenFocus(val left: Int, val top: Int, val right: Int, val bottom: Int, val label: String) {
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
