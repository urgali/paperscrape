package com.paperscrape.livewallpaper.engine

/**
 * The mountain's outline: a parabolic arch, built as two halves that meet on the vertical axis.
 *
 * `PaperRenderer.drawSoftMountain` is where the shape is explained -- why a parabola, why the
 * points are spaced along the width rather than the height, why each half closes down the axis
 * instead of along a diagonal. It lives here so that the gallery card draws its mountains with the
 * same outline: the card used to draw half-ellipses (and a row of dunes for the Desert, which the
 * wallpaper does not have), under a comment saying that was what the wallpaper drew.
 *
 * Both halves are single-peaked faces, which is what lets the GPU fill them as a fan
 * (`SceneShapeFanContractTest`).
 */
internal object MountainSilhouette {

    /** Points along each half of the curve, spaced by width. */
    const val SEGMENTS = 16

    /** The left half of a mountain [width] wide and [height] tall, peak over [cx], into [shape]. */
    fun leftHalf(shape: SceneShape, cx: Float, baseY: Float, width: Float, height: Float) {
        val halfWidth = width / 2f
        shape.reset()
        shape.moveTo(cx - halfWidth, baseY)
        for (i in SEGMENTS downTo 0) {
            val xFrac = i / SEGMENTS.toFloat() // 1=base, 0=peak -- fraction of *width*, not height
            val t = xFrac * xFrac // inverse of width=√t
            shape.lineTo(cx - halfWidth * xFrac, baseY - height * (1f - t))
        }
        shape.lineTo(cx, baseY)
        shape.close()
    }

    /**
     * The whole mountain as one polygon: the left half's curve up to the peak, the right half's
     * down to the base.
     *
     * For a backend that antialiases edges, which the gallery card's `Canvas` does: two halves
     * filled separately leave a hairline down the axis where their soft edges meet, and on the card
     * that line was the most visible thing about the mountains. The GPU cannot take this shape --
     * a fan from its first vertex runs outside the curve -- which is why the wallpaper draws halves.
     */
    fun whole(shape: SceneShape, cx: Float, baseY: Float, width: Float, height: Float) {
        val halfWidth = width / 2f
        shape.reset()
        shape.moveTo(cx - halfWidth, baseY)
        for (i in SEGMENTS downTo 0) {
            val xFrac = i / SEGMENTS.toFloat()
            shape.lineTo(cx - halfWidth * xFrac, baseY - height * (1f - xFrac * xFrac))
        }
        for (i in 1..SEGMENTS) {
            val xFrac = i / SEGMENTS.toFloat()
            shape.lineTo(cx + halfWidth * xFrac, baseY - height * (1f - xFrac * xFrac))
        }
        shape.close()
    }

    /** The right half, the mirror of [leftHalf], starting at the peak. */
    fun rightHalf(shape: SceneShape, cx: Float, baseY: Float, width: Float, height: Float) {
        val halfWidth = width / 2f
        shape.reset()
        shape.moveTo(cx, baseY - height)
        for (i in 0..SEGMENTS) {
            val xFrac = i / SEGMENTS.toFloat() // 0=peak, 1=base
            val t = xFrac * xFrac
            shape.lineTo(cx + halfWidth * xFrac, baseY - height * (1f - t))
        }
        shape.lineTo(cx, baseY)
        shape.close()
    }
}
