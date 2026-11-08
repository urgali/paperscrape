package com.paperscrape.livewallpaper.engine

/**
 * Where the cloud band sits, and what hangs off it.
 *
 * **Why this is its own object.** Three things in the sky have to agree about where the clouds are:
 * the clouds themselves, the rain that falls from them, and the lightning that comes out of them.
 * A bolt given a constant of its own -- a flat share of the screen's height -- is born above the
 * cloud it should come out of, hanging from the top edge of the sky, and taller than the whole
 * cloud layer.
 *
 * Duplicating the band arithmetic in three call sites lets one of them drift, so the band's
 * top and height are written once here and the bolt's origin is *derived* from them rather than
 * sitting beside them, and so is the rain's ([precipitationOriginY], which
 * `PaperRenderer.drawPrecipitation` starts its drops on). Pure float arithmetic on primitives
 * -- no allocation, no Android type, and testable, which is the other half of why it moved out of
 * the renderer.
 */
object CloudBand {

    /** How tall the band is, as a fraction of screen height. */
    private const val HEIGHT_FRACTION = 0.16f

    /**
     * How deep into the band a bolt is born, as a fraction of the band's own height.
     *
     * Past the midpoint, so the bolt's head is inside the cloud mass and only the fork below it is
     * seen against open sky: the bolt reads as coming out of a cloud rather than as being pasted
     * over one. Being a fraction of the band rather than of the screen, it holds at every arc
     * height instead of only at the default one.
     */
    const val LIGHTNING_DEPTH_FRACTION = 0.6f

    /**
     * The top of the band, in pixels, on a [screenWidth] x [screenHeight] screen.
     *
     * **The clouds follow the sun** (`DESIGN_NOTES.md` §12, decision 106): a low arc puts them low in the sky, a high one
     * lifts them toward the top, and at every value of the slider the band stands under the top of the sun's arc
     * ([SunArc.rise]) as far as it stood under it at that value with the arc rising as far as the stored value:
     * `0.36 + 0.5 * stored - rise` of the screen: the band's top 0.03 of the screen under the sun's middle at the top of
     * the arc as the slider comes (the stored 0.42), 0.12 under it at the slider's top, and 0.13 above it at its foot,
     * where the band's top is at 0.31 of the screen.
     *
     * Stays clear of the horizon at every setting and on every screen: the lowest band top is 0.31 ([LOWEST_TOP]) and the
     * band is 0.16 tall, ending at 0.47 against the arc's horizon at 0.54.
     */
    fun topFor(screenWidth: Int, screenHeight: Int, sunCloudHeight: Float): Float {
        val stored = sunCloudHeight.coerceIn(SUN_CLOUD_HEIGHT_MIN, SUN_CLOUD_HEIGHT_MAX)
        return screenHeight * (0.36f + 0.5f * stored - SunArc.rise(stored, screenWidth, screenHeight)).coerceAtMost(LOWEST_TOP)
    }

    /**
     * The lowest the band's top goes, a share of the screen's height: where it stands at the slider's foot on every screen.
     * On a screen much wider than tall the arc has little room ([SunArc.highest]) and the rule above would take the band
     * lower as the slider rises, under the arc's horizon; there it stops here, its foot 0.07 above that horizon.
     */
    private const val LOWEST_TOP = 0.31f

    /** How tall the band is, in pixels. */
    fun heightFor(screenHeight: Int): Float = screenHeight * HEIGHT_FRACTION

    /** Where precipitation starts falling: the band's middle, the same line the clouds are hung on. */
    fun precipitationOriginY(screenWidth: Int, screenHeight: Int, sunCloudHeight: Float): Float =
        topFor(screenWidth, screenHeight, sunCloudHeight) + heightFor(screenHeight) * 0.5f

    /** Where a bolt's top edge sits: inside the band, past its middle. */
    fun lightningOriginY(screenWidth: Int, screenHeight: Int, sunCloudHeight: Float): Float =
        topFor(screenWidth, screenHeight, sunCloudHeight) + heightFor(screenHeight) * LIGHTNING_DEPTH_FRACTION
}
