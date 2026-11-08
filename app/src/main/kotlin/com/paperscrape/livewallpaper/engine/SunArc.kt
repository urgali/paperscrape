package com.paperscrape.livewallpaper.engine

/**
 * How high the sun's and the moon's arc rises for a *Sun/Cloud Height* (`DESIGN_NOTES.md` §12, decision 106): the whole
 * slider spread from the arc's lowest, [SUN_CLOUD_HEIGHT_MIN] of the screen above its horizon
 * (`PaperRenderer.HORIZON_Y_FRACTION_OF_ARC`), to its highest on that screen ([highest]), where the disc and the sun's ring
 * stay whole under the status bar -- on every screen, so 100 % is the top and nothing at any value leaves the screen. The
 * stored value is kept as it is, [SUN_CLOUD_HEIGHT_MIN]..[SUN_CLOUD_HEIGHT_MAX]; every reader turns it into a rise here:
 * the sun and the moon (`PaperRenderer.drawCelestialBody`), the still moon's arc height ([StillMoon.arcY]) and the clouds
 * that follow the sun ([CloudBand.topFor]). Pure float arithmetic, no allocation.
 */
object SunArc {

    /** How far the sun's ring (`sun_glow`, 166 of the disc's 120 units) stands out, in the disc's radii: what must stay on. */
    const val RING_RADII = 1.4f

    /** The room left above the ring for the status bar, a share of the screen's height (48 px of the BV6600's 1 440, and a little). */
    const val TOP_ROOM = 0.04f

    /** The disc's radius on a screen [screenWidth] wide, as `PaperRenderer.drawCelestialBody` draws it. */
    fun radiusPx(screenWidth: Int): Float = screenWidth * PaperRenderer.CELESTIAL_RADIUS_FRACTION * 2f

    /**
     * The highest the arc rises on a [screenWidth] x [screenHeight] screen, a share of its height: the ring's top
     * [TOP_ROOM] under the screen's top. 0.423 on the BV6600 (720 x 1440), never under the arc's lowest.
     */
    fun highest(screenWidth: Int, screenHeight: Int): Float =
        (PaperRenderer.HORIZON_Y_FRACTION_OF_ARC - RING_RADII * radiusPx(screenWidth) / screenHeight - TOP_ROOM)
            .coerceAtLeast(SUN_CLOUD_HEIGHT_MIN)

    /** The arc's rise, a share of the screen's height, for the slider stored at [sunCloudHeight]: in line from the lowest to [highest]. */
    fun rise(sunCloudHeight: Float, screenWidth: Int, screenHeight: Int): Float {
        val stored = sunCloudHeight.coerceIn(SUN_CLOUD_HEIGHT_MIN, SUN_CLOUD_HEIGHT_MAX)
        val along = (stored - SUN_CLOUD_HEIGHT_MIN) / (SUN_CLOUD_HEIGHT_MAX - SUN_CLOUD_HEIGHT_MIN)
        return SUN_CLOUD_HEIGHT_MIN + along * (highest(screenWidth, screenHeight) - SUN_CLOUD_HEIGHT_MIN)
    }
}
