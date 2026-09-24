package com.paperscrape.livewallpaper.engine

/**
 * How the sky gets from one cloud cover to the next **without the change being a single frame**.
 *
 * ### The report this exists for
 *
 * > *"quando riaccendo il cellulare da deep sleep, con live weather vedo un 'reset' delle nuvole:
 * > c'e' un piccolo scatto e scompaiono o appaiono dal nulla nuvole, poi e' tutto fluido finche'
 * > non rispengo lo schermo e riaccendo dopo vari minuti"*
 *
 * The scene time was not the cause and the clouds do not move: measured on a BV6600, scene time is
 * identical to the last bit across 62.8 s and 302.8 s of screen-off, because it is a per-frame
 * accumulator and `GlRenderThread.idle()` clears `lastFrameNanos` on the way into the park. What
 * actually happens is upstream of the renderer. The Live Weather loop **parks while the engine is
 * invisible** and re-enters its body on the first `onVisibilityChanged(true)`, so an hourly refresh
 * that fell due behind a dark screen is fetched *at the instant the screen comes back*. A new
 * `cloudCoverFraction` lands, and because the candidate pool admits a cloud by the plain test
 * `CandidateThreshold.of(i) < density`, every candidate whose threshold lies between the old cover
 * and the new one is created or destroyed **in that one frame**. On the measured device a Milan
 * refresh moved 0.87 -> 0.42 and 19 clouds left the sky between two consecutive frames.
 *
 * The fixed candidate pool was built so that a density change would not *relocate* the clouds
 * already in the sky (see `PaperRenderer.drawClouds`), and it does exactly that -- every cloud that
 * survives the change keeps its position to the pixel. It was never meant to govern how the ones
 * at the margin arrive and leave, and that is the half this class adds.
 *
 * ### Two eases, because there are two ways to notice the same event
 *
 *  - **The cover itself** moves at [COVER_UNITS_PER_SECOND] rather than jumping, so the candidates
 *    between the two covers cross their thresholds one at a time instead of together.
 *  - **Each candidate's opacity** then eases over [FADE_SECONDS] rather than switching, so the one
 *    that does cross arrives as a cloud thickening rather than as a cloud appearing.
 *
 * Either alone leaves half the report standing: the ramp alone still pops each cloud, and the fade
 * alone dissolves the whole margin of the sky at once.
 *
 * ### Why both eases are linear, and why the first observation snaps
 *
 * **Linear, so they arrive.** An exponential ease is never finished, and "never finished" here
 * would mean the drawn cover is forever a hair off the reported one and every cloud forever a hair
 * off opaque -- a permanent, invisible, untestable difference from the scene that ships today. Both
 * eases below reach their target exactly and then stop, so a settled scene is bit-identical to the
 * one this release started from. That is the property the instrumented crossfade test and the 33
 * goldens both rest on.
 *
 * **The first observation snaps.** A fresh engine, and every golden, must draw the sky it was
 * handed on its very first frame rather than ease into it from nothing: easing there would mean a
 * wallpaper that boots to the wrong sky, and 33 goldens that depend on which frame they were
 * captured at. [UNSET] is what distinguishes "has never drawn" from "has drawn 0".
 *
 * ### The expiry eases too (item 135, v5.7F)
 *
 * The third way the sky could change in a frame. When the snapshot ages out --
 * `LiveWeatherSchedule.SNAPSHOT_MAX_AGE_MILLIS` after the last good reading, with the retries
 * spent -- the scene falls back to the theme's own weather. Until v5.7F that went through
 * [coverToward]'s null branch, which resets the ramp: measured, the drawn cover went from 0.87 to
 * the theme's density **in one frame** where the same move as a forecast change takes 270, and
 * the next good reading then snapped again because the ramp had been reset. The maintainer's
 * decision of 2026-09-23 is that "we no longer know what sky it is" deserves the same dissolve as
 * "the sky has changed". [coverLapsingTo] is that path: the same ramp and the same rate, towards
 * the theme's cover instead of the forecast's, and the ramp is kept afterwards so the next reading
 * eases in from where the sky actually is.
 *
 * ### What it does not touch
 *
 * The theme's own sky, while nothing has lapsed. Handing [coverToward] a null target -- Live
 * Weather switched off, or off the whole time, or the location or the provider changed so it
 * cannot run -- resets the ramp and returns null, so the theme's own cloud switch and slider reach
 * `LiveWeatherSceneRules.cloudDensity` exactly as they do today. Those are deliberate acts with a
 * settings screen open in front of them, which is not the event that was reported, and a sky that
 * answers at once is how the user sees the switch worked. Once a lapse has settled the same is
 * true: the theme's slider is followed exactly, frame by frame.
 *
 * Nor precipitation or the storm, on any path: they have never eased, a forecast that stops the
 * rain stops it in a frame, and giving the expiry a rain fade the forecast does not have would make
 * the rarer event the smoother one.
 *
 * No allocation and no new artwork: one float, one [FloatArray] sized to the pool, and an alpha
 * argument `SpriteBlitter.drawTinted` has always taken. Neither sprite budget can move, because
 * both count the *set of PNGs* and this adds none -- `SpriteGeometryTest.decodedByteBudget` and
 * `SpriteDrawScaleTest.uploadedTexelBudget` are untouched by construction. **The two tests carry
 * their own ceilings and the measurements behind them; this paragraph deliberately does not
 * re-type either figure.** It used to, and both went stale the release after it was written --
 * the same rot `GlTextureAtlas` and `GlTextureCache` were rewritten to escape.
 */
internal class CloudCoverFade(poolSize: Int) {

    /** The cover being drawn, or [UNSET] when nothing has been drawn yet. */
    private var cover: Float = UNSET

    /**
     * True once a lapse has eased all the way to the theme's cover: from then on the theme draws
     * itself and [cover] only shadows it, so the next reading has somewhere to ease in from.
     */
    private var lapseSettled = false

    /** Per-candidate opacity in `0..1`, or [UNSET] before that candidate's first frame. */
    private val opacity = FloatArray(poolSize) { UNSET }

    /**
     * The cover to draw this frame, easing toward [target].
     *
     * @param target the forecast's cover, or null when Live Weather is not driving the scene.
     * @return the cover to hand `LiveWeatherSceneRules.cloudDensity`, or null when [target] is.
     */
    fun coverToward(target: Float?, deltaSeconds: Float): Float? {
        lapseSettled = false
        if (target == null) {
            cover = UNSET
            return null
        }
        val wanted = target.coerceIn(0f, 1f)
        val settled = if (cover == UNSET) wanted else approach(cover, wanted, COVER_UNITS_PER_SECOND * deltaSeconds)
        cover = settled
        return settled
    }

    /**
     * The cover to draw this frame while the forecast has **lapsed**: the snapshot aged out and the
     * scene is going back to the theme's own weather ([themeCover], its slider or 0 when its cloud
     * switch is off).
     *
     * @return the eased cover, to hand `LiveWeatherSceneRules.cloudDensity` exactly as a forecast
     * cover is handed -- or null once it has arrived, from which frame on the theme's own switch
     * and slider draw the sky as they do with Live Weather off. Null straight away when nothing
     * has ever been drawn from a forecast, because then there is no sky to ease from: that is the
     * first observation, and it snaps for the reason the class KDoc gives.
     */
    fun coverLapsingTo(themeCover: Float, deltaSeconds: Float): Float? {
        if (cover == UNSET) return null
        val wanted = themeCover.coerceIn(0f, 1f)
        if (lapseSettled) {
            cover = wanted
            return null
        }
        val settled = approach(cover, wanted, COVER_UNITS_PER_SECOND * deltaSeconds)
        cover = settled
        if (settled == wanted) {
            lapseSettled = true
            return null
        }
        return settled
    }

    /**
     * The opacity to draw candidate [index] at, easing toward [present].
     *
     * Returns 0 for a candidate that is neither present nor still fading out; the caller skips it,
     * which is what keeps a settled frame drawing exactly the candidates it draws today.
     */
    fun opacityOf(index: Int, present: Boolean, deltaSeconds: Float): Float {
        val wanted = if (present) 1f else 0f
        val now = opacity[index]
        val settled = if (now == UNSET) wanted else approach(now, wanted, deltaSeconds / FADE_SECONDS)
        opacity[index] = settled
        return settled
    }

    /**
     * Whether any candidate is mid-fade.
     *
     * The cloud layer can only be declared absent -- which is what switches the rain's coverage
     * field to uniform -- once the last cloud of the previous sky has actually gone.
     */
    fun anythingVisible(): Boolean {
        for (value in opacity) if (value != UNSET && value > 0f) return true
        return false
    }

    /** Moves [from] toward [to] by at most [step], landing exactly on [to]. */
    private fun approach(from: Float, to: Float, step: Float): Float {
        if (step <= 0f) return from
        val gap = to - from
        if (gap <= step && gap >= -step) return to
        return if (gap > 0f) from + step else from - step
    }

    companion object {

        /** Neither a cover nor an opacity: "this has never been drawn". Negative, so no real value collides. */
        private const val UNSET = -1f

        /**
         * How fast the drawn cover follows the reported one, in cover units per second.
         *
         * A full 0 -> 1 swing therefore takes 20 seconds, and the measured Milan step of 0.45 takes
         * about 9. Weather is an hours-scale thing being redrawn after an hour of not being looked
         * at, so there is no case for hurrying: the number that matters is that the sky stops
         * changing *between two frames*, and any rate slow enough to spread the pool's 41 thresholds
         * out achieves that. 0.05 puts roughly two threshold crossings a second through the widest
         * step the forecast can make, which [FADE_SECONDS] then covers individually.
         */
        const val COVER_UNITS_PER_SECOND = 0.05f

        /**
         * How long one cloud takes to arrive or leave, in seconds.
         *
         * Long enough that a single cloud reads as thickening rather than as appearing, short
         * enough that at most a handful of the pool are part-drawn at once while the cover ramp is
         * moving. At [COVER_UNITS_PER_SECOND] the two together mean about three candidates in
         * flight at any instant of the widest step -- three extra sprite blits, against a frame
         * that already draws up to 41 of them twice over for the tile either side.
         */
        const val FADE_SECONDS = 1.5f
    }
}
