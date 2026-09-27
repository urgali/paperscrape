package com.paperscrape.livewallpaper.engine

/**
 * The sky's two colours at a moment of the day: the one rule both the wallpaper and the gallery
 * card paint their sky with.
 *
 * `PaperRenderer.drawSky` computed these inline and `ThemePreviewScenes` computed its own from the
 * theme's old `skyDay`/`skyDusk`/`skyNight` arrays, which the wallpaper stopped reading when the
 * six editable colours arrived. The two had drifted to the point where Sunset's card was coral at
 * the top and the wallpaper never is, at any hour, and a sky colour the user edited never reached
 * the card at all. Both now call this, with the customization they draw and the [dayBlend] and
 * [progress] of `SunPositionCalculator.DayPhase` for the moment they show.
 *
 * Built on [SceneColour.blendArgb], which is `ColorUtils.blendARGB` cast for cast
 * (`SceneColourBlendTest`), so moving the arithmetic here moves no pixel of the wallpaper.
 */
internal object SkyGradient {

    /** The top of the sky: day and night blended directly, because the upper sky barely shifts. */
    fun top(sky: SkyConfig, dayBlend: Float): Int =
        SceneColour.blendArgb(sky.colorNightHigh, sky.colorDayHigh, dayBlend.coerceIn(0f, 1f))

    /**
     * The horizon: night, through the sunrise or sunset colour, to day, with the twilight colour
     * weighted most at the terminator ([progress] before noon picks sunrise, after it sunset).
     */
    fun bottom(sky: SkyConfig, dayBlend: Float, progress: Float): Int {
        val blend = dayBlend.coerceIn(0f, 1f)
        val twilightWeight = (1f - kotlin.math.abs(dayBlend * 2f - 1f)).coerceIn(0f, 1f)
        val twilightLow = if (progress < 0.5f) sky.colorSunriseLow else sky.colorSunsetLow
        val nightToTwilight = SceneColour.blendArgb(sky.colorNightLow, twilightLow, blend)
        return SceneColour.blendArgb(nightToTwilight, sky.colorDayLow, (dayBlend - twilightWeight * 0.3f).coerceIn(0f, 1f))
    }

    /** The horror sky's top, which replaces [top] while `horrorSkyEnabled` is on. */
    fun horrorTop(dayBlend: Float): Int = SceneColour.blendArgb(
        PaperRenderer.HORROR_SKY_TOP_NIGHT, PaperRenderer.HORROR_SKY_TOP_DAY, dayBlend.coerceIn(0f, 1f),
    )

    /** The horror sky's horizon, which replaces [bottom] while `horrorSkyEnabled` is on. */
    fun horrorBottom(dayBlend: Float): Int = SceneColour.blendArgb(
        PaperRenderer.HORROR_SKY_LOW_NIGHT, PaperRenderer.HORROR_SKY_LOW_DAY, dayBlend.coerceIn(0f, 1f),
    )
}
