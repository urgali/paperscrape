package com.paperscrape.livewallpaper.engine

/**
 * Which layer's settings win when Live Weather is active.
 *
 * Pure, and separated from [PaperRenderer], because the fault it prevents is not visible in either
 * layer on its own: it is the two layers answering the same question differently. A
 * `drawPrecipitation` that ignored the theme's own precipitation switch while Live Weather is on,
 * beside a `drawClouds` that returned on the theme's own cloud switch before it looked at the
 * forecast, would give a user who had switched clouds off rain from the forecast and no clouds from
 * it, which reads as rain falling out of a clear sky.
 *
 * The settings screen states the contract: *"Real current conditions replace each theme's manual
 * rain/snow/cloud settings automatically, … While the forecast drives the sky, this theme's own Clouds
 * and Rain and snow screens leave those to it"*. Both layers, or neither.
 *
 * **Nothing falls from a sky with no cloud** (`DESIGN_NOTES.md` §12, decision 98): the theme's rain,
 * snow and storm need its clouds ([cloudlessSky]), and a forecast that reports something falling under
 * a clear sky is drawn with [FALLING_MIN_COVER] of clouds to fall from ([forecastCover]).
 */
object LiveWeatherSceneRules {

    /**
     * The cloud density to draw with, or `NaN` for "place no clouds".
     *
     * **`NaN` rather than `null`, both ways**: the draw path calls this every frame, and a
     * `Float?` in or out is a boxed `Float` -- one allocation a frame on the render thread with Live
     * Weather off and the clouds on, two with it on.
     *
     * @param liveCloudCover the 0..1 cover Live Weather is drawing (the forecast's, eased by
     *   `CloudCoverFade`, or during a lapse easing back to the theme's), or `NaN` when Live Weather
     *   is not driving the sky.
     * @param themeCloudsVisible the theme's own cloud switch.
     * @param themeCloudDensity the theme's own cloud slider.
     */
    fun cloudDensity(
        liveCloudCover: Float,
        themeCloudsVisible: Boolean,
        themeCloudDensity: Float,
    ): Float = when {
        // Live Weather off: the theme decides, switch first.
        liveCloudCover.isNaN() -> if (themeCloudsVisible) themeCloudDensity.coerceIn(0f, 1f) else Float.NaN
        // Live Weather on and the forecast says clear: no clouds, whatever the theme's switch says.
        liveCloudCover <= 0f -> Float.NaN
        // Live Weather on: the forecast decides, and the theme's switch does not get a vote --
        // the same rule precipitation follows.
        else -> liveCloudCover.coerceIn(0f, 1f)
    }

    /**
     * The least cover drawn while the forecast reports rain or snow falling: a fifth of the sky, so what falls has clouds
     * to fall from (`DESIGN_NOTES.md` §12, decision 98, *«Poche nuvole (consigliata)»*).
     */
    const val FALLING_MIN_COVER = 0.2f

    /**
     * The cover to draw for a forecast reporting [cover], `NaN` for none: at least [FALLING_MIN_COVER] while
     * [somethingFalling], the forecast's own otherwise.
     */
    fun forecastCover(cover: Float, somethingFalling: Boolean): Float = when {
        cover.isNaN() -> Float.NaN
        somethingFalling && cover < FALLING_MIN_COVER -> FALLING_MIN_COVER
        else -> cover
    }

    /**
     * Whether this frame's sky has no cloud to rain, snow or flash from, so nothing falls and no storm flashes
     * (`DESIGN_NOTES.md` §12, decision 98): after a lapse of the forecast, when the theme's own clouds place none
     * ([themeDensity] `NaN` or 0 -- *Show Clouds* off, or *# of Clouds* at 0 %); otherwise when none is placed
     * ([placedDensity]) and none is still fading out ([cloudsShowing]), so the rain stops with the last cloud.
     */
    fun cloudlessSky(lapsed: Boolean, themeDensity: Float, placedDensity: Float, cloudsShowing: Boolean): Boolean =
        if (lapsed) CloudCoverage.placesNone(themeDensity) else CloudCoverage.placesNone(placedDensity) && !cloudsShowing

    /**
     * Whether the lightning flash should be running.
     *
     * The third layer the precedence question applies to. The theme's storm toggle is gated on
     * *Show Rain/Snow* being on with Rain chosen -- **not on the
     * intensity**: at 0 % no drop falls and the flashes go on, a thunderstorm without rain, and that
     * is meant (`DESIGN_NOTES.md` §12, decision 44). So the *Thunderstorm*
     * switch reads on at 0 % too (`SettingsUiModel.thunderstorm`), because the flashes are there. The
     * forecast-driven path has its own rule: [liveIsThunderstorm] is the forecast's thunderstorm with
     * rain or snow measured (see `WeatherSnapshotMapper`), so this only has to pick which source is
     * in charge.
     *
     * @param liveIsThunderstorm the forecast's verdict, or null when Live Weather is not active.
     */
    fun stormActive(
        liveIsThunderstorm: Boolean?,
        themePrecipitationVisible: Boolean,
        themePrecipitationIsRain: Boolean,
        themeThunderstorm: Boolean,
    ): Boolean = liveIsThunderstorm
        ?: (themePrecipitationVisible && themePrecipitationIsRain && themeThunderstorm)

    /**
     * Whether the lake raises waves: in rain, and in a storm **unless snow is what is falling**.
     * A live thunderstorm can report measured snowfall (thundersnow); the lightning is right there
     * and stays, but the lake stays calm under falling snow: snow is not rain, for the waves as for
     * the umbrellas.
     */
    fun wavesOnLake(raining: Boolean, stormActive: Boolean, snowing: Boolean): Boolean =
        raining || (stormActive && !snowing)
}
