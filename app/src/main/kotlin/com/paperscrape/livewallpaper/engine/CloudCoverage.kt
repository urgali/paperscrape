package com.paperscrape.livewallpaper.engine

/**
 * Where the sky is covered by cloud, as a value in `[0, 1]` sampled across the screen width.
 *
 * ### Why this exists
 *
 * Read from `precipitation.intensity` alone, the same number of drops would fall at every cloud
 * density. On a device that reads as rain falling out of a wide stretch of completely clear sky,
 * plain to see wherever the cloud density is set low.
 *
 * The candidate model stays exactly as it is but for one thing: the density a precipitation
 * candidate is tested against is **local to its position** rather than a single number for the
 * whole sky. `CandidateThreshold.isPresent` is the same; it is
 * simply handed `intensity × coverage(x)` instead of `intensity`. A drop therefore keeps its x,
 * its phase and its speed no matter what the clouds do — only whether it exists at all changes.
 *
 * ### Filled from the clouds that were actually drawn
 *
 * `drawClouds` runs before `drawPrecipitation` in the frame, so this is populated from the real
 * on-screen cloud positions — after parallax, per-cloud drift, tile wrapping and off-screen
 * culling. That ordering is load-bearing: reversing it would leave precipitation reading a
 * one-frame-stale field. Which clouds go in, or whether the field is uniform, is [rainField]'s:
 * after a lapse of the forecast, the theme's own clouds at their places rather than the ones drawn.
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
     * When set, [at] reports full coverage everywhere regardless of [columns]: the frames whose rain falls
     * [RAIN_EVERYWHERE] ([rainField]).
     */
    private var uniform = false

    /** The coverage everywhere beneath which [at] never reads ([setFloor]); 0 from [beginFrame]. */
    private var floor = 0f

    /**
     * Clears the field for a new frame whose rain falls as [field] says ([rainField]): [RAIN_EVERYWHERE] makes it
     * uniform at once; under clouds, coverage starts at zero everywhere and the clouds the rain falls from are added.
     */
    fun beginFrame(field: Int = RAIN_UNDER_DRAWN_CLOUDS) {
        java.util.Arrays.fill(columns, 0f)
        uniform = field == RAIN_EVERYWHERE
        floor = 0f
    }

    /**
     * Raises the whole sky's coverage to at least [value] for this frame: the rain spreading beyond the clouds while they
     * leave a clearing sky, or narrowing under them while they come into a clear one (`CloudCoverFade.rainEverywhereToward`).
     * At 1 the field reads as [RAIN_EVERYWHERE] does; at 0 it is the clouds' alone.
     */
    fun setFloor(value: Float) {
        floor = value.coerceIn(0f, 1f)
    }

    /** Declares the whole sky covered, as [beginFrame] does for [RAIN_EVERYWHERE]. */
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
     * — a peaked kernel never quite reaches 1 between cloud centres, which would quietly
     * thin the rain at 100% cloud cover.
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
        val value = columns[column.coerceIn(0, columnCount - 1)]
        return if (value > floor) value else floor
    }

    /** Test/diagnostic helper. */
    internal fun columnValue(column: Int): Float = columns[column]

    /** Test/diagnostic helper. */
    internal fun columns(): Int = columnCount

    companion object {
        /** The rain falls under the clouds drawn this frame, a cloud fading in or out counting whole. */
        const val RAIN_UNDER_DRAWN_CLOUDS = 0

        /** The rain falls everywhere: the field is uniform and the intensity alone decides. */
        const val RAIN_EVERYWHERE = 1

        /** The rain falls under the clouds the theme's own density places, whatever is drawn. */
        const val RAIN_UNDER_THEME_CLOUDS = 2

        /**
         * Where this frame's rain may fall: [RAIN_UNDER_DRAWN_CLOUDS], [RAIN_EVERYWHERE] or [RAIN_UNDER_THEME_CLOUDS]. What
         * `PaperRenderer.drawClouds` hands [beginFrame] before it places a cloud.
         *
         * - **Nothing placed and nothing showing** -- the cloud layer off or at 0 %, or a forecast that reports a clear
         *   sky --: the rain falls everywhere. Turning clouds off must not also turn precipitation off, and with no cloud to derive a
         *   field from, an empty one would cancel rain the forecast does report.
         * - **The forecast has lapsed** ([lapsed]: no snapshot, and the last one aged out): the rain is the theme's, from
         *   the frame of the lapse, and it falls as the theme draws it -- under the theme's own clouds, or everywhere when
         *   its cloud layer is off or at 0 % ([themeDensity] `NaN` or 0). The forecast's clouds fade out over seconds (`CloudCoverFade`);
         *   falling only under them, the theme's rain would shrink with them and come back everywhere when the last went.
         * - Otherwise under the clouds drawn, the ones fading in or out included, and beyond them as far as [setFloor]
         *   says while the clouds leave a clearing sky or come into a clear one.
         *
         * @param placedDensity the density the clouds are placed at this frame, `NaN` for none; 0 places none either.
         * @param cloudsShowing whether a cloud is still drawn, fully or fading (`CloudCoverFade.anythingVisible`).
         * @param themeDensity the density of the theme's own clouds, `NaN` when its cloud layer is off.
         */
        fun rainField(placedDensity: Float, cloudsShowing: Boolean, lapsed: Boolean, themeDensity: Float): Int = when {
            lapsed -> if (placesNone(themeDensity)) RAIN_EVERYWHERE else RAIN_UNDER_THEME_CLOUDS
            placesNone(placedDensity) && !cloudsShowing -> RAIN_EVERYWHERE
            else -> RAIN_UNDER_DRAWN_CLOUDS
        }

        /** Whether a cloud density places no cloud at all: none (`NaN`), or 0 %. */
        fun placesNone(density: Float): Boolean = density.isNaN() || density <= 0f

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
         * **Measured off the shipped file.** A width from any other drawing makes the coverage
         * kernel treat every cloud as wider or narrower than the one being drawn (a box of 873 px
         * makes it 9 % wider), so the "coverage is exactly 1 inside the silhouette" property is
         * false at the edges, where rain falls a little beyond the cloud making it.
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
