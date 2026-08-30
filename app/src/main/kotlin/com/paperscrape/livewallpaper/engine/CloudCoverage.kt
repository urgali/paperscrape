package com.paperscrape.livewallpaper.engine

/**
 * Where the sky is covered by cloud, as a value in `[0, 1]` sampled across the screen width.
 *
 * ### Why this exists
 *
 * Precipitation used to read only `precipitation.intensity`, so the same number of drops fell at
 * every cloud density. On a device that reads as rain falling out of a wide stretch of completely
 * clear sky — obvious once Phase 2.1/2.2 made cloud density behave predictably enough to set it
 * low and actually see the gap.
 *
 * The fix keeps the Phase 2.1/2.2 candidate model exactly as it is and changes one thing: the
 * density a precipitation candidate is tested against becomes **local to its position** rather
 * than a single number for the whole sky. `CandidateThreshold.isPresent` is untouched; it is
 * simply handed `intensity × coverage(x)` instead of `intensity`. A drop therefore keeps its x,
 * its phase and its speed no matter what the clouds do — only whether it exists at all changes.
 *
 * ### Filled from the clouds that were actually drawn
 *
 * `drawClouds` runs before `drawPrecipitation` in the frame, so this is populated from the real
 * on-screen cloud positions — after parallax, per-cloud drift, tile wrapping and off-screen
 * culling. That ordering is load-bearing: reversing it would leave precipitation reading a
 * one-frame-stale field.
 *
 * ### Soft edges without a diffuse floor
 *
 * Each cloud contributes a smooth `1 - d²` falloff over a span wider than the cloud itself
 * ([RAIN_SPREAD_FACTOR]), and contributions combine by maximum rather than by sum so overlapping
 * clouds cannot push coverage above 1. Coverage therefore *thins* toward a cloud's edge instead
 * of stopping at it, which softens the transition without ever lifting clear sky above zero.
 * Coverage zero means zero precipitation, with no floor anywhere.
 *
 * ### Cost
 *
 * One `FloatArray` allocated once and refilled in place; no allocation on the draw path. Filling
 * is O(clouds × columns-per-cloud) and sampling is a single array index, so the pair costs
 * O(clouds + drops) rather than the O(clouds × drops) an exact per-drop overlap test would.
 *
 * Not thread-safe: written and read within one frame on the render thread.
 */
internal class CloudCoverage(private val columnCount: Int = DEFAULT_COLUMNS) {

    private val columns = FloatArray(columnCount)

    /**
     * When set, [at] reports full coverage everywhere regardless of [columns].
     *
     * Used when the cloud layer is switched off entirely: turning clouds off must not also turn
     * precipitation off, so the sky is treated as uniformly covered and intensity governs alone,
     * exactly as it did before this class existed.
     */
    private var uniform = false

    /** Clears the field for a new frame. Coverage starts at zero everywhere. */
    fun beginFrame() {
        java.util.Arrays.fill(columns, 0f)
        uniform = false
    }

    /**
     * Declares the whole sky covered, for the frames where the cloud layer is not drawn at all.
     */
    fun setUniform() {
        uniform = true
    }

    /** Whether the field is in its uniform-fallback state. */
    fun isUniform(): Boolean = uniform

    /**
     * Adds one on-screen cloud.
     *
     * The kernel has a **flat top**: coverage is exactly 1 within the cloud's own silhouette and
     * falls smoothly to 0 across the widened margin beyond it. That shape matters for two
     * reasons. Physically, the sky directly under a cloud is fully covered, so only the margin
     * should be partial. Practically, it means an overcast sky saturates to exactly 1 everywhere
     * — a peaked kernel never quite reaches 1 between cloud centres, which would have quietly
     * thinned the rain at 100% cloud cover compared with the previous build.
     *
     * @param centerX the cloud's horizontal centre in screen pixels.
     * @param coreHalfWidth half the cloud's own visible width, in screen pixels.
     * @param spreadHalfWidth half the width over which it should still produce some rain; must be
     *   at least [coreHalfWidth].
     * @param screenWidth the viewport width the columns span.
     */
    fun addCloud(centerX: Float, coreHalfWidth: Float, spreadHalfWidth: Float, screenWidth: Float) {
        if (screenWidth <= 0f || spreadHalfWidth <= 0f) return
        val core = coreHalfWidth.coerceIn(0f, spreadHalfWidth)
        val columnWidth = screenWidth / columnCount
        val first = ((centerX - spreadHalfWidth) / columnWidth).toInt() - 1
        val last = ((centerX + spreadHalfWidth) / columnWidth).toInt() + 1
        for (column in first..last) {
            if (column < 0 || column >= columnCount) continue
            val columnCentre = (column + 0.5f) * columnWidth
            val distance = kotlin.math.abs(columnCentre - centerX)
            if (distance >= spreadHalfWidth) continue
            val value = if (distance <= core) {
                1f
            } else {
                val t = (distance - core) / (spreadHalfWidth - core)
                1f - t * t
            }
            if (value > columns[column]) columns[column] = value
        }
    }

    /** Coverage at a horizontal screen position, in `[0, 1]`. */
    fun at(x: Float, screenWidth: Float): Float {
        if (uniform) return 1f
        if (screenWidth <= 0f) return 0f
        val column = (x / screenWidth * columnCount).toInt()
        return columns[column.coerceIn(0, columnCount - 1)]
    }

    /** Test/diagnostic helper. */
    internal fun columnValue(column: Int): Float = columns[column]

    /** Test/diagnostic helper. */
    internal fun columns(): Int = columnCount

    companion object {
        /**
         * 64 columns over a 1080 px screen is about 17 px each — finer than the softening span of
         * any cloud, so the sampled edge is smooth rather than stepped.
         */
        const val DEFAULT_COLUMNS = 64

        /**
         * How far past its own silhouette a cloud is treated as producing rain.
         *
         * Rain drifts and spreads as it falls, so a hard match to the sprite outline would read as
         * a stencil. 1.6 widens the falloff enough to look like weather while leaving genuinely
         * open sky at zero.
         */
        const val RAIN_SPREAD_FACTOR = 1.6f

        /**
         * Half the visible width of `cloud_body.png` in local units: the sprite is 798 px wide with
         * content filling the canvas (alpha bounding box 0,0-798,396) and sprites are authored at
         * [SpriteBlitter.SPRITE_PIXELS_PER_UNIT], so 798 / 3 / 2.
         *
         * **Re-measured.** This said 145.5 and cited a content box "873 px wide ... measured from
         * the asset, not guessed" -- but 873 px matches no shipped file, and the cloud that ships
         * is 798 px. The coverage kernel was therefore treating every cloud as 9 % wider than the
         * one being drawn, so the "coverage is exactly 1 inside the silhouette" property was
         * slightly false at the edges, where rain fell a little beyond the cloud making it.
         *
         * Re-derive this from the shipped PNG if the art changes; do not carry a number over.
         */
        const val CLOUD_CONTENT_HALF_UNITS = 133f

        /**
         * Half the visible *height* of `cloud_body.png` in local units: 396 / 3 / 2.
         *
         * Measured from the same shipped file and in the same way as
         * [CLOUD_CONTENT_HALF_UNITS], and it exists so the blit can be centred from the asset
         * rather than from a remembered canvas -- see `PaperRenderer.CLOUD_BLIT_Y`.
         */
        const val CLOUD_CONTENT_HALF_HEIGHT_UNITS = 66f
    }
}
