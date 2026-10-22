package com.paperscrape.livewallpaper.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The Halloween moon laughing (v5.16, the proposal 2 of v5.16A, the maintainer's *«2 risata (consigliata)»*,
 * 2026-10-21): every [EVERY_SECONDS] of the scene's clock, for [LAUGH_SECONDS], the carved face throws itself open --
 * `moon_jack_o_lantern_laugh`, its eyes two arches and its grin wide, with its own light -- and the moon shakes
 * [SHAKE_DEGREES] either way [SHAKES] times, dying down, and hops a little ([HOP_OF_RADIUS] of its radius). The first
 * laugh is [FIRST_SECONDS] into the scene. On the scene's clock, which stops while the wallpaper is hidden, so a laugh
 * is never missed half-way; drawn only while *Laughing moon* is on with the carved moon up
 * ([SceneCustomization.laughingMoonActive]). The gallery card keeps the moon still.
 *
 * Pure arithmetic on the clock: nothing is stored and nothing allocated.
 */
internal object LaughingMoon {
    const val EVERY_SECONDS = 60.0
    const val LAUGH_SECONDS = 2.0
    const val FIRST_SECONDS = 6.0
    const val SHAKE_DEGREES = 4f
    const val SHAKES = 6f
    const val HOP_OF_RADIUS = 0.04f

    /** The share of the laugh the open face is shown for: it opens a moment after the shaking starts and closes a moment before it ends. */
    const val FACE_FROM = 0.06f
    const val FACE_UNTIL = 0.94f

    /** Where in a laugh [t] falls, 0..1, or -1 between two laughs. */
    fun progress(t: SceneTime): Float {
        val inCycle = (t.seconds - FIRST_SECONDS).mod(EVERY_SECONDS)
        return if (inCycle < LAUGH_SECONDS) (inCycle / LAUGH_SECONDS).toFloat() else -1f
    }

    /** Whether the moon is laughing at [t]: shaking and hopping. */
    fun laughing(t: SceneTime): Boolean = progress(t) >= 0f

    /** Whether the open face is shown at [t]. */
    fun faceOpen(t: SceneTime): Boolean = progress(t).let { it >= FACE_FROM && it <= FACE_UNTIL }

    /** How far the moon is turned at [t], in degrees: the shaking, dying down over the laugh. 0 between laughs. */
    fun shakeDegrees(t: SceneTime): Float {
        val u = progress(t)
        if (u < 0f) return 0f
        return SHAKE_DEGREES * sin(u * 2f * PI.toFloat() * SHAKES) * (1f - u * 0.4f)
    }

    /** How far the moon is lifted at [t], as a share of its radius, negative upwards: three little hops. 0 between laughs. */
    fun hop(t: SceneTime): Float {
        val u = progress(t)
        if (u < 0f) return 0f
        return -HOP_OF_RADIUS * abs(sin(u * 2f * PI.toFloat() * 3f))
    }
}
