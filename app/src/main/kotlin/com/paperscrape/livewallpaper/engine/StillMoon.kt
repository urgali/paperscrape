package com.paperscrape.livewallpaper.engine

/**
 * The still Halloween moon (v5.17, *Moon stays in place*; the maintainer's *«aggiungi toggle per halloween di mettere la luna
 * zucca in posizione fixed in cielo senza farle fare il giro di sole e luna»* of 2026-10-23 and, of the three places
 * photographed, *«L3: bassa sui tetti»*, 2026-10-24): while it is in force ([SceneCustomization.stillMoonActive]) and it is
 * the moon that is up, the carved moon stands in the middle of the screen, low over the roofs under the clouds, instead of
 * crossing the sky -- the phase the renderer draws the sky with ([of]), on the arc's own terms, so the parallax
 * (`PaperRenderer.celestialParallaxOffset`, decision 19) and the lake read it as they read the moon on its arc.
 *
 * - **With the night by day** in force ([SceneCustomization.nightOverDayActive]) it stands there at every hour.
 * - **Without**, the sun keeps the day: where the sun is up the phase is left as it is, and the moon comes out where it
 *   stands as the night comes and fades before the day ([alpha]: `1 - dayBlend / 0.5`) -- in its twilight a little
 *   transparent, the stars seen through it.
 * - **Its place follows *Sun/Cloud Height***, as the clouds do ([centreY]): its middle [UNDER_CLOUDS_RADII] of its radius
 *   under the cloud band's foot ([CloudBand]), 0.37 of the screen's height on the BV6600 with the slider as it comes (64 %,
 *   the arc's 0.42).
 *
 * Pure, as [NightOverDay]: the renderer keeps one instance a phase it is given.
 */
internal object StillMoon {

    /** In the middle of the screen across, on the arc's 0..1. */
    const val X = 0.5f

    /** How far under the cloud band's foot its middle stands, in its own radii: its top just clears the band. */
    const val UNDER_CLOUDS_RADII = 1.1f

    /** Without the night by day it is all out at a `dayBlend` of 0, gone from this one up, and partly out in between. */
    const val OUT_BY_DAY_BLEND = 0.5f

    /** Where its middle stands, px from the top of a [screenWidth] x [screenHeight] screen, the slider at [sunCloudHeight]. */
    fun centreY(screenWidth: Int, screenHeight: Int, sunCloudHeight: Float): Float =
        CloudBand.topFor(screenHeight, sunCloudHeight) + CloudBand.heightFor(screenHeight) +
            screenWidth * PaperRenderer.CELESTIAL_RADIUS_FRACTION * 2f * UNDER_CLOUDS_RADII

    /** The same place as the arc's height (0 the horizon), as `PaperRenderer.drawCelestialBody` turns it back into px. */
    fun arcY(screenWidth: Int, screenHeight: Int, sunCloudHeight: Float): Float {
        val rise = sunCloudHeight.coerceIn(SUN_CLOUD_HEIGHT_MIN, SUN_CLOUD_HEIGHT_MAX)
        return (screenHeight * PaperRenderer.HORIZON_Y_FRACTION_OF_ARC - centreY(screenWidth, screenHeight, sunCloudHeight)) /
            (rise * screenHeight)
    }

    /** How much of it is out in [phase], 0..1: all of it with the night by day ([nightOverDay]), else by the night's own light. */
    fun alpha(phase: SunPositionCalculator.DayPhase, nightOverDay: Boolean): Float =
        if (nightOverDay) 1f else (1f - phase.dayBlend / OUT_BY_DAY_BLEND).coerceIn(0f, 1f)

    /** Whether it is it that is up in [phase]: not where the sun is (with the night by day the sun never is). */
    fun up(phase: SunPositionCalculator.DayPhase): Boolean = !phase.isSunVisible

    /** [phase] with the moon at its place ([arcY]): the hour, the day and the light left as they are. */
    fun of(phase: SunPositionCalculator.DayPhase, arcY: Float): SunPositionCalculator.DayPhase =
        SunPositionCalculator.DayPhase(
            progress = phase.progress, dayBlend = phase.dayBlend, celestialX = X, celestialY = arcY,
            isSunVisible = false, hour24 = phase.hour24, moonPhase = phase.moonPhase,
        )
}

/**
 * The still moon of one renderer, frame by frame (`PaperRenderer.stillMoon`): the phase a frame is drawn with and how much of
 * the moon is out ([alpha], which the moon's sprites, its glow and its light on the lake are drawn with). One phase made for
 * each phase the engine hands it, and made again when the place moves -- the slider or the screen changed --, so a frame
 * makes none; out of force, or with the sun up, the frame's own phase and the moon whole, so turning the switch off at dusk
 * leaves no moon half out. Pure, so the JVM suite runs it (`HalloweenStillMoonTest`).
 */
internal class StillMoonFrame {
    /** How much of the moon is out in the last phase given, 0..1: 1 whenever the still moon is not what is drawn. */
    var alpha = 1f
        private set
    private var source: SunPositionCalculator.DayPhase? = null
    private var made: SunPositionCalculator.DayPhase? = null

    /**
     * The phase to draw [phase] with: at the still moon's place while it is in force ([active], the settings' rule
     * `SceneCustomization.stillMoonActive`) and it is the moon that is up ([StillMoon.up]), out as far as [nightOverDay] and
     * the light say ([StillMoon.alpha]); [phase] itself otherwise.
     */
    fun phaseFor(
        phase: SunPositionCalculator.DayPhase,
        active: Boolean,
        nightOverDay: Boolean,
        screenWidth: Int,
        screenHeight: Int,
        sunCloudHeight: Float,
    ): SunPositionCalculator.DayPhase {
        alpha = 1f
        if (!active || !StillMoon.up(phase)) return phase
        alpha = StillMoon.alpha(phase, nightOverDay)
        val y = StillMoon.arcY(screenWidth, screenHeight, sunCloudHeight)
        val cached = made
        if (cached != null && source === phase && cached.celestialY == y) {
            cached.moonPhase = phase.moonPhase
            return cached
        }
        val still = StillMoon.of(phase, y)
        source = phase
        made = still
        return still
    }
}
