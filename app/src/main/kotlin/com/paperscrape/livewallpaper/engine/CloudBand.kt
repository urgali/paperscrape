package com.paperscrape.livewallpaper.engine

/**
 * Where the cloud band sits, and what hangs off it.
 *
 * **Why this is its own object.** Three things in the sky have to agree about where the clouds are:
 * the clouds themselves, the rain that falls from them, and the lightning that comes out of them.
 * Two of them read the band from the same arithmetic; the third had a constant of its own -- a bolt
 * began at a flat 8 % of screen height while the band, at the default arc, starts at 15 % and is
 * 16 % tall. So every bolt was born roughly half a band *above* the cloud it was supposed to come
 * out of, hanging from the top edge of the sky with nothing over it, and at 26-40 % of screen height
 * it was also taller than the entire cloud layer. aa reported both halves of that from a live
 * render: "i fulmini sono giganti e escono dalla cima del cielo".
 *
 * Duplicating the band arithmetic in three call sites is what let one of them drift, so it is
 * written once here and the bolt's origin is *derived* from it rather than sitting beside it. Pure
 * float arithmetic on primitives -- no allocation, no Android type, and testable, which is the other
 * half of why it moved out of the renderer.
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
     * The top of the band, in pixels.
     *
     * The clouds follow the sun: a low arc puts them low in the sky, a high one lifts them toward
     * the top. v2.11 computed `0.08 + (1 - height) * 0.15`, which over the whole slider moved the
     * band by 0.075 of screen height -- about 7 % -- and read on a device as a control that did
     * nothing. The band now spans a range the eye can actually see, while landing within a few
     * pixels of the old position at the default height (0.42), so existing scenes are not
     * rearranged by the fix.
     *
     * Stays clear of the horizon at every setting: the lowest band top is 0.31 and the band is
     * 0.16 tall, ending at 0.47 against a horizon at 0.62.
     */
    fun topFor(screenHeight: Int, sunCloudHeight: Float): Float {
        val height = sunCloudHeight.coerceIn(SUN_CLOUD_HEIGHT_MIN, SUN_CLOUD_HEIGHT_MAX)
        return screenHeight * (0.06f + (SUN_CLOUD_HEIGHT_MAX - height) * 0.5f)
    }

    /** How tall the band is, in pixels. */
    fun heightFor(screenHeight: Int): Float = screenHeight * HEIGHT_FRACTION

    /** Where precipitation starts falling: the band's middle, the same line the clouds are hung on. */
    fun precipitationOriginY(screenHeight: Int, sunCloudHeight: Float): Float =
        topFor(screenHeight, sunCloudHeight) + heightFor(screenHeight) * 0.5f

    /** Where a bolt's top edge sits: inside the band, past its middle. */
    fun lightningOriginY(screenHeight: Int, sunCloudHeight: Float): Float =
        topFor(screenHeight, sunCloudHeight) + heightFor(screenHeight) * LIGHTNING_DEPTH_FRACTION
}
