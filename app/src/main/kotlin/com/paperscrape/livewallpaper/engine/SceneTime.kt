package com.paperscrape.livewallpaper.engine

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Scene time: how long the wallpaper has been visible, in seconds.
 *
 * ### Why not a `Float`
 *
 * A `Float` accumulated with `elapsedSeconds += deltaSeconds`, advanced by ~0.033 per frame, stops
 * advancing entirely once its own ULP exceeds that increment — measured at **12.14 days** of
 * visible uptime, with motion visibly quantising into steps from around day 5. Every time-driven
 * animation in the renderer reads from it, so past that point the whole scene would freeze with no
 * crash and nothing in the logs.
 *
 * ### Why there is no wrap period
 *
 * Wrapping the accumulator at some period does not work here, for a reason that is easy to miss.
 *
 * Two families of consumer read this value:
 *
 *  - **Sinusoids**, `sin(t * rate + offset)`. A wrap at period `P` is seamless for these only if
 *    `P * rate` is a whole number of turns for every rate in use. Every rate in the renderer
 *    happens to be a multiple of `0.05`, so `P = 40π` would satisfy all of them.
 *  - **Linear cycles**, `(t * rate + offset) % 1`. These wrap seamlessly only if `P * rate` is a
 *    whole number. Their rates are **not** fixed constants: cloud drift, precipitation fall speed,
 *    bird drift and lake decoration speed all derive their rate from a per-candidate random value
 *    (for example `CandidateNoise.range(seed, i, CH_SPEED, 0.03f, 0.05f)`). For an arbitrary
 *    real rate, no `P` exists.
 *
 * So any global wrap would leave every cloud, raindrop, bird and leaf jumping to a new position at
 * the wrap instant. Per-effect wrapping would work but means one accumulator per effect, which is
 * state the stateless-candidate rendering model deliberately does not keep.
 *
 * ### What this does instead
 *
 * Accumulate in `Double` and **bound at the point of use** rather than at the accumulator.
 *
 * A `Double` accumulated by ~0.033 per frame does not stall until roughly 18 million years
 * (2^49 s), so the accumulator itself needs no bound. Every read then goes through one of the
 * helpers below, each of which performs its arithmetic in double precision and only narrows to
 * `Float` *after* the operation that bounds the result — `sin`, a modulo, or an integer frame
 * index. The value handed to the renderer is therefore always small, and its precision never
 * depends on uptime.
 *
 * `PaperRenderer.scrollProgress` follows the same rule: never narrow an unbounded accumulator to
 * `Float`; narrow the bounded result of using it.
 *
 * ### Cost
 *
 * None. `kotlin.math.sin(Float)` already widens to `Double`, calls `Math.sin`, and narrows back,
 * so computing in double precision removes conversions rather than adding them. This is a
 * `@JvmInline value class`, so it compiles to a bare `double` — no allocation, nothing new on the
 * per-frame path.
 */
@JvmInline
value class SceneTime(val seconds: Double) {

    /** Advances by one frame's delta. */
    operator fun plus(deltaSeconds: Float): SceneTime = SceneTime(seconds + deltaSeconds)

    /**
     * `sin(seconds * rate + offset)`, in `[-1, 1]`.
     *
     * Bounded by `sin` itself, so the narrowing to `Float` happens on a value that never exceeds
     * 1 regardless of how long the wallpaper has been running.
     */
    fun sinAt(rate: Float, offset: Float = 0f): Float =
        sin(seconds * rate + offset).toFloat()

    /**
     * `cos(seconds * rate + offset)`, in `[-1, 1]` -- the slope of [sinAt] at the same instant.
     *
     * Exists so a caller animating along a sine arc can orient something along it without
     * differencing two frames or keeping the previous value: the dolphin noses up on the way out
     * of the water and down on the way back in, and that is exactly the cosine of its own phase.
     */
    fun cosAt(rate: Float, offset: Float = 0f): Float =
        cos(seconds * rate + offset).toFloat()

    /**
     * `(seconds * rate + offset) % 1`, the position within a repeating 0..1 cycle.
     *
     * Above zero, which is every caller's case, it is `x - floor(x)`: the same double as `x % 1.0`
     * (below 1 the floor is 0; from 1 up the floor is within a factor two of `x`, so the subtraction
     * is exact) without the library's `fmod`, which this runs hundreds of times a frame. Zero and
     * below keep `%`, so a negative rate keeps `%`'s sign convention.
     */
    fun cycle(rate: Float, offset: Float = 0f): Float {
        val x = seconds * rate + offset
        return (if (x > 0.0) x - floor(x) else x % 1.0).toFloat()
    }

    /**
     * `(seconds * rate + offset) % period`, for cycles measured in something other than 1 —
     * degrees of rotation, for instance.
     */
    fun cycleOf(rate: Float, offset: Float, period: Float): Float =
        ((seconds * rate + offset) % period).toFloat()

    /**
     * Which frame of a [frameCount]-frame loop is showing, stepping at [rate] frames per second.
     *
     * The integer conversion happens in double precision, so the frame index stays correct long
     * after a `Float` would have stopped advancing.
     */
    fun frameIndex(rate: Float, offset: Float, frameCount: Int): Int {
        val step = (seconds * rate + offset).toLong()
        return (step % frameCount).toInt()
    }

    companion object {
        val ZERO = SceneTime(0.0)
    }
}
