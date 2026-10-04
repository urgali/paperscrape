package com.paperscrape.livewallpaper.engine

import android.graphics.Paint

/**
 * `Paint.setAlpha(alpha)` without the object Android 10 allocates on every call.
 *
 * On API 29 `setAlpha` unpacks the paint's colour through `Color.colorSpace`, and that goes through
 * `ColorSpace.get`, which clones the `ColorSpace.Named` enum array: one object per call, invisible
 * in this code and visible only in an allocation trace. The scene called it 27 places on the draw
 * path -- the stars, the falling leaves, the porch lights, the chimney smoke, the rain -- and it was
 * the 60 % of the ~204 objects a frame allocated at night (v5.10A, the BV6600). Later Android looks
 * the space up in a map and allocates nothing.
 *
 * Writing the alpha byte into the colour instead is the same state for the paints this app uses: an
 * sRGB colour set with `setColor(int)` (`Color.pack(int)` packs it exactly), and an alpha in
 * `0..255`. Java's colour and the native paint's colour both come out as `setAlpha` leaves them, and
 * both backends read the colour -- `GlSceneTarget.beginSolid` reads `paint.color`, and Canvas draws
 * with the native paint. `PaintAlphaEquivalenceTest` checks it on the device for all 256 values:
 * the colour, the alpha, the colour as a `long`, and the pixel drawn with it.
 *
 * The clamp only guards the byte: every call site already passes a value in `0..255`.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun Paint.setAlphaWithoutAllocating(alpha: Int) {
    color = (alpha.coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)
}
