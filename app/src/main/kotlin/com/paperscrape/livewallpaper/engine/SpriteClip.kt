package com.paperscrape.livewallpaper.engine

/**
 * The arithmetic of a sprite blit cut to a box ([SceneCanvas.drawSpriteClipped]) on the GPU backend:
 * a textured quad, and the part of it inside the box, with the texture rectangle cut in the same
 * proportion (v5.12).
 *
 * Here rather than inside [GlSceneTarget] because it is pure, and a mistake in it is silent: a texture
 * coordinate taken from the wrong side squeezes or slides the picture inside the quad, and no frame
 * says why. `SpriteClipTest` holds it on the JVM.
 *
 * Done in the sprite's own coordinates -- before the transform -- where both the quad and the box are
 * axis-aligned rectangles, so the cut is exact under any transform the scene uses, a mirrored one
 * included, and the quad stays a quad.
 */
internal object SpriteClip {

    /** Where [clip] writes: the cut quad and its texture rectangle. */
    const val LEFT = 0
    const val TOP = 1
    const val RIGHT = 2
    const val BOTTOM = 3
    const val U0 = 4
    const val V0 = 5
    const val U1 = 6
    const val V1 = 7
    const val SIZE = 8

    /**
     * The quad [left]..[right] x [top]..[bottom], textured [u0]..[u1] x [v0]..[v1], cut to the box
     * [clipLeft]..[clipRight] x [clipTop]..[clipBottom], into [out] (at [LEFT] .. [V1]); false, and
     * [out] untouched, when nothing of it is inside. A side the box does not cut keeps its own
     * coordinate and its own texture coordinate, to the bit, so a box round the whole quad gives back
     * the quad it was handed.
     */
    @Suppress("LongParameterList")
    fun clip(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
        clipLeft: Float,
        clipTop: Float,
        clipRight: Float,
        clipBottom: Float,
        out: FloatArray,
    ): Boolean {
        val l = if (clipLeft > left) clipLeft else left
        val r = if (clipRight < right) clipRight else right
        val t = if (clipTop > top) clipTop else top
        val b = if (clipBottom < bottom) clipBottom else bottom
        if (l >= r || t >= b) return false
        val width = right - left
        val height = bottom - top
        out[LEFT] = l
        out[TOP] = t
        out[RIGHT] = r
        out[BOTTOM] = b
        out[U0] = if (l == left) u0 else u0 + (u1 - u0) * ((l - left) / width)
        out[U1] = if (r == right) u1 else u0 + (u1 - u0) * ((r - left) / width)
        out[V0] = if (t == top) v0 else v0 + (v1 - v0) * ((t - top) / height)
        out[V1] = if (b == bottom) v1 else v0 + (v1 - v0) * ((b - top) / height)
        return true
    }
}
