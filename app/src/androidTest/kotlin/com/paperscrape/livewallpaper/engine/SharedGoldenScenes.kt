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

    fun day() = GoldenScene(name = "day", dayPhase = GoldenScene.day())

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
