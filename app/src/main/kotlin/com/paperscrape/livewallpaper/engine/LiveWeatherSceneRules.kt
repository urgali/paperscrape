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
     * The cloud density to draw with, or null for "place no clouds".
     *
     * @param liveCloudCover the forecast's 0..1 cover, or null when Live Weather is not active.
     * @param themeCloudsVisible the theme's own cloud switch.
     * @param themeCloudDensity the theme's own cloud slider.
     */
    fun cloudDensity(
        liveCloudCover: Float?,
        themeCloudsVisible: Boolean,
        themeCloudDensity: Float,
    ): Float? = when {
        // Live Weather off: the theme decides, switch first.
        liveCloudCover == null -> if (themeCloudsVisible) themeCloudDensity.coerceIn(0f, 1f) else null
        // Live Weather on and the forecast says clear: no clouds, whatever the theme's switch says.
        liveCloudCover <= 0f -> null
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
}
