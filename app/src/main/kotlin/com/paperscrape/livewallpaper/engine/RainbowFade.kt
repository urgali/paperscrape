package com.paperscrape.livewallpaper.engine

/**
 * **The rainbow comes and goes with Live Weather the way a cloud does: over [CloudCoverFade.FADE_SECONDS], never in a
 * frame** (`DESIGN_NOTES.md` §12, decision 110).
 *
 * The forecast takes the rainbow away while it brings rain, snow or a thunderstorm
 * ([LiveWeatherSceneRules.rainbowUnderForecast], decision 108), and gives it back when that stops or when the forecast
 * leaves the scene (Live Weather switched off, its inputs changed, the conditions aged out). Each of those is a change the
 * user does not make in front of the scene, and each is eased here: [levelToward] is how much of the rainbow the weather
 * lets through this frame, from 0 to 1, moving linearly toward its target at one full swing per
 * [CloudCoverFade.FADE_SECONDS] -- the same constant, and the same linear ease, as each cloud's opacity
 * ([CloudCoverFade.opacityOf]), so the rainbow and the clouds that arrive or leave with it fade together.
 *
 * **Only the weather's permission is eased.** The rainbow's own switch and opacity, and the day/night fade
 * (`dayBlend`), are applied by `PaperRenderer.drawRainbow` as they are, on top of this level. A change of direction halfway
 * starts from where the level is, with no jump. Precipitation does not ease, so a rainbow leaving as the
 * forecast's rain begins is seen, fading, over the rain's first [CloudCoverFade.FADE_SECONDS].
 *
 * **The first frame snaps**, as [CloudCoverFade]'s first observation does: a fresh engine, and every golden, draws the
 * rainbow its first frame is handed, rather than fading into it from nothing. **The clock is the scene's** (the
 * `deltaSeconds` a frame is drawn with), so the fade stops while the wallpaper is hidden and plays on when it is next
 * on screen, as the clouds' does.
 *
 * **Linear, so it arrives**: once settled the level is exactly 1 or 0, and a settled scene is the same to the pixel as one
 * drawn with no fade. One float, no allocation.
 */
internal class RainbowFade {

    /** The level drawn last frame, or [UNSET] before the first frame. */
    private var level: Float = UNSET

    /**
     * The level to draw this frame, easing toward 1 when the weather lets the rainbow show ([allowed]) and toward 0 when
     * it does not. Called every frame, whether or not the rainbow is drawn, so the level always follows the weather.
     */
    fun levelToward(allowed: Boolean, deltaSeconds: Float): Float {
        val wanted = if (allowed) 1f else 0f
        val now = level
        val settled = if (now == UNSET) wanted else approach(now, wanted, deltaSeconds / CloudCoverFade.FADE_SECONDS)
        level = settled
        return settled
    }

    /** Moves [from] toward [to] by at most [step], landing exactly on [to]. */
    private fun approach(from: Float, to: Float, step: Float): Float {
        if (step <= 0f) return from
        val gap = to - from
        if (gap <= step && gap >= -step) return to
        return if (gap > 0f) from + step else from - step
    }

    private companion object {
        /** Not a level: "no frame has been drawn yet". Negative, so no real level collides. */
        const val UNSET = -1f
    }
}
