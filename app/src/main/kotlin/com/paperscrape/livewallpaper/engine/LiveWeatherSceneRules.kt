package com.paperscrape.livewallpaper.engine

/**
 * Which layer's settings win when Live Weather is active.
 *
 * Pure, and separated from [PaperRenderer], because the bug it exists to prevent was not visible in
 * either layer on its own -- it was that the two layers answered the same question differently.
 * `drawPrecipitation` ignored the theme's own precipitation switch while Live Weather was on;
 * `drawClouds` returned on the theme's own cloud switch before it ever looked at the forecast. So a
 * user who had switched clouds off got rain from the forecast and no clouds from it, which reads as
 * rain falling out of a clear sky and is exactly what was reported.
 *
 * The settings screen states the contract: *"Real current conditions replace each theme's manual
 * rain/snow/cloud settings automatically... this theme's own Clouds/Precipitation screens switch to
 * read-only."* Both layers, or neither.
 */
object LiveWeatherSceneRules {

    /**
     * The cloud density to draw with, or `NaN` for "place no clouds".
     *
     * **`NaN` rather than `null`, both ways** (v5.10B): the draw path calls this every frame, and a
     * `Float?` in or out is a boxed `Float` -- one allocation a frame on the render thread with Live
     * Weather off and the clouds on, two with it on (v5.10A).
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
        // the same rule precipitation has always followed.
        else -> liveCloudCover.coerceIn(0f, 1f)
    }

    /**
     * Whether the cloud-coverage field should be treated as uniform when no clouds are placed.
     *
     * Always, and for one reason: precipitation is thinned by the coverage under it, so an empty
     * field would silently suppress rain the forecast does report. A clear sky with rain at a grid
     * edge is unusual but it is what the provider said, and the honest answer is to draw it rather
     * than to let a cloud-derived field cancel it.
     */
    fun coverageIsUniformWhenNoClouds(): Boolean = true

    /**
     * Whether the lightning flash should be running.
     *
     * The third layer, and the last one that answered the precedence question on its own. The
     * theme's storm toggle is gated on *Show Rain/Snow* being on with Rain chosen -- **not on the
     * intensity**: at 0 % no drop falls and the flashes go on, a thunderstorm without rain, and that
     * is meant. The v5.10C round found it (inventory I-292) against this comment, which said a flash
     * over a dry scene "is not a storm, it is a strobe"; the maintainer kept it on 2026-10-04: *«nel
     * mondo vero può esistere un temporale senza pioggia, quindi va bene»*. So the *Thunderstorm*
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
     * and stays, but breaking waves under falling snow is what the renderer's own comment said
     * could not happen, and until v5.8C it did (v5.8B comment audit).
     */
    fun wavesOnLake(raining: Boolean, stormActive: Boolean, snowing: Boolean): Boolean =
        raining || (stormActive && !snowing)
}
