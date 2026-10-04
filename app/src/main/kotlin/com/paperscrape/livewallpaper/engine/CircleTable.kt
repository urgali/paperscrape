package com.paperscrape.livewallpaper.engine

import kotlin.math.cos
import kotlin.math.sin

/**
 * The cosine and sine of every angle a tessellated circle is cut at, worked out once per process.
 *
 * [GlSceneTarget] cuts a circle, an oval, a ring or a glow into `segments` slices, `segments` between
 * [MIN_SEGMENTS] and [MAX_SEGMENTS], and each slice ends at the angle `(TWO_PI / segments) * i`. Those
 * angles depend on nothing but the two integers, so the `cos` and `sin` of each one are the same every
 * frame: until v5.10B they were computed again for every slice of every circle of every frame, about
 * 7 % of the render thread on the BV6600 (`simpleperf`, v5.10A). The table holds, for every segment
 * count, the values the loop used to compute.
 *
 * **Bit for bit the loop's values, not an approximation of them.** Each entry is built with the very
 * expression the loop evaluated -- `step = TWO_PI / segments` as a `Float`, then `cos(step * i)` and
 * `sin(step * i)` through `kotlin.math` -- so a vertex made from the table is the float the loop would
 * have made, and the frame is the same frame. `CircleTableTest` compares every entry's bits with that
 * expression; the golden frames compare the pixels.
 *
 * Wedges (`drawWedge`) start at an arbitrary angle and cannot use it; they still call `cos`/`sin`.
 *
 * 57 segment counts, 2 109 floats in each table: about 17 KB for the two.
 */
internal object CircleTable {

    /** The fewest slices any tessellated circle is cut into. */
    const val MIN_SEGMENTS = 8

    /** The most slices any tessellated circle is cut into. */
    const val MAX_SEGMENTS = 64

    /** The full turn the slices divide, as the `Float` the tessellation always used. */
    const val TWO_PI = (2.0 * Math.PI).toFloat()

    private val COSINES: Array<FloatArray> = Array(MAX_SEGMENTS + 1) { segments ->
        if (segments < MIN_SEGMENTS) {
            FloatArray(0)
        } else {
            val step = TWO_PI / segments
            FloatArray(segments + 1) { i -> cos(step * i) }
        }
    }

    private val SINES: Array<FloatArray> = Array(MAX_SEGMENTS + 1) { segments ->
        if (segments < MIN_SEGMENTS) {
            FloatArray(0)
        } else {
            val step = TWO_PI / segments
            FloatArray(segments + 1) { i -> sin(step * i) }
        }
    }

    /**
     * `cos((TWO_PI / segments) * i)` for `i` in `0..segments`. The array is shared: read it, never
     * write it.
     */
    fun cosines(segments: Int): FloatArray = COSINES[segments]

    /** `sin((TWO_PI / segments) * i)` for `i` in `0..segments`. Shared, like [cosines]. */
    fun sines(segments: Int): FloatArray = SINES[segments]
}
