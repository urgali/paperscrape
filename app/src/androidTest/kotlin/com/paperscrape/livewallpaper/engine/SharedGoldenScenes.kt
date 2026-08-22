package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.weather.LiveWeatherSnapshot

/**
 * The three scenes both golden suites render.
 *
 * [SceneGoldenTest] draws them through `CanvasSceneTarget` and compares each with its committed
 * PNG; [GlSceneGoldenTest] draws the *same objects* through the shipped `GlSceneTarget` and
 * compares that against the same PNG. The second comparison only means anything if both suites
 * describe an identical scene — two copies of "a busy lake" that drifted apart by one density
 * value would turn a real GL regression into a scene difference nobody could tell it from.
 * Defining them once is what makes the GL suite's claim ("the two backends draw the same picture")
 * checkable at all.
 *
 * Only these three are shared, because only these three are the ones P1-4 selected: between them
 * they cover the geometries the two backends build by genuinely different routes — a radial
 * gradient against a triangle fan, a linear gradient against stepped columns, and analytic arcs
 * against tessellated ones. The other eleven Canvas goldens stay where they are.
 */
object SharedGoldenScenes {

    /**
     * The sun's glow, named as a region so the GL suite can measure it on its own (v3.7 Filone E).
     *
     * `drawRadialGlow` is the one effect the whole-frame gates provably cannot police: it is drawn
     * at alpha 90 over a bright sky, so destroying its shape entirely moves no pixel by more than
     * 15/255 and reaches only 0.47% of the frame at `>=8` -- under the 0.88% two correct drivers
     * already differ by. Measured against the glow's own bounding box instead of the frame's, the
     * same regression is an order of magnitude clear of the noise.
     *
     * The rectangle is the disc the renderer actually draws, read off the call the scene makes:
     * centre (180,160) with radius 127 at this frame size, clamped to the frame.
     */
    private val SUN_GLOW = GoldenFocus(left = 53, top = 33, right = 307, bottom = 287, label = "sun glow")

    fun day() = GoldenScene(
        name = "day",
        dayPhase = GoldenScene.day(),
        focus = listOf(SUN_GLOW),
    )

    /** Every lane occupied, which is where depth ordering and batching are both under load. */
    fun lakeBusy() = GoldenScene(
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
    )

    /** The storm minus the bolt: darkened sky, darkened cloud band, attenuated sun, full rain. */
    fun thunderstorm() = GoldenScene(
        name = "thunderstorm",
        dayPhase = GoldenScene.day(),
        weather = LiveWeatherSnapshot(
            precipitationType = PrecipitationType.RAIN,
            precipitationIntensity = 1f,
            cloudCoverFraction = 1f,
            isThunderstorm = true,
            fetchedAtMillis = 0L,
        ),
    )
}
