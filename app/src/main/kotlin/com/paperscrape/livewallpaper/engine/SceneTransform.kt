package com.paperscrape.livewallpaper.engine

import kotlin.math.cos
import kotlin.math.sin

/**
 * The `save`/`restore`/`translate`/`scale`/`rotate` stack, as plain arithmetic.
 *
 * The GPU backend transforms vertices on the CPU as it emits them, so it needs its own copy of what
 * `Canvas` does to a point. Keeping that arithmetic here rather than inside [GlSceneTarget] is what
 * makes it testable at all: [GlSceneTarget] cannot be instantiated without a GL context, and this
 * class has no Android dependency of any kind.
 *
 * That matters more than it looks. Every one of these operations is silent when it is wrong: a sign
 * error in [rotate] mirrors the waning moon instead of turning it, an operand order error in
 * [translate] applies a sprite's offset in the parent's space instead of its own, and nothing throws
 * in either case — the scene simply renders subtly wrong somewhere that no test observes.
 *
 * ## Convention
 *
 * A 2x3 affine `[a c tx ; b d ty]`, so `x' = a*x + c*y + tx` and `y' = b*x + d*y + ty`. Each
 * operation **post**-multiplies, matching `Canvas`: a transform applied later acts in the coordinate
 * space established by the transforms before it. Rotation is clockwise on screen, which with Y
 * pointing down is the positive direction `Canvas.rotate` uses.
 */
class SceneTransform(private val maxDepth: Int = DEFAULT_DEPTH) {

    private val stack = FloatArray(maxDepth * 6)
    private var depth = 0

    var a = 1f
        private set
    var b = 0f
        private set
    var c = 0f
        private set
    var d = 1f
        private set
    var tx = 0f
        private set
    var ty = 0f
        private set

    /** Current stack depth, i.e. how many `save`s are outstanding. */
    val saveDepth: Int get() = depth

    fun reset() {
        depth = 0
        a = 1f; b = 0f; c = 0f; d = 1f; tx = 0f; ty = 0f
    }

    /**
     * Pushes the current transform.
     *
     * Overflowing the stack is ignored rather than thrown: an unbalanced `save` in a draw path would
     * otherwise take down the wallpaper, and the depth is far above what the scene actually nests.
     * [restore] counts pushes it dropped, so the pairing stays correct either way.
     */
    fun save() {
        if (depth >= maxDepth) {
            droppedSaves++
            return
        }
        val base = depth * 6
        stack[base] = a
        stack[base + 1] = b
        stack[base + 2] = c
        stack[base + 3] = d
        stack[base + 4] = tx
        stack[base + 5] = ty
        depth++
    }

    fun restore() {
        if (droppedSaves > 0) {
            droppedSaves--
            return
        }
        if (depth == 0) return
        depth--
        val base = depth * 6
        a = stack[base]
        b = stack[base + 1]
        c = stack[base + 2]
        d = stack[base + 3]
        tx = stack[base + 4]
        ty = stack[base + 5]
    }

    private var droppedSaves = 0

    fun translate(dx: Float, dy: Float) {
        tx += a * dx + c * dy
        ty += b * dx + d * dy
    }

    fun scale(sx: Float, sy: Float) {
        a *= sx
        b *= sx
        c *= sy
        d *= sy
    }

    fun rotate(degrees: Float) {
        val rad = degrees * DEG_TO_RAD
        val cs = cos(rad)
        val sn = sin(rad)
        val na = a * cs + c * sn
        val nb = b * cs + d * sn
        val nc = c * cs - a * sn
        val nd = d * cs - b * sn
        a = na; b = nb; c = nc; d = nd
    }

    fun mapX(x: Float, y: Float): Float = a * x + c * y + tx

    fun mapY(x: Float, y: Float): Float = b * x + d * y + ty

    /**
     * The uniform scale factor this transform applies, as the square root of the absolute
     * determinant.
     *
     * Used to decide how finely to tessellate a curve: a circle drawn inside a `scale(1/3)` sprite
     * transform covers a third of the screen distance and needs a third of the segments, and taking
     * that from the axis factors alone would be wrong as soon as a rotation is in the stack.
     */
    fun uniformScale(): Float {
        val determinant = a * d - b * c
        val magnitude = if (determinant < 0f) -determinant else determinant
        return kotlin.math.sqrt(magnitude)
    }

    companion object {
        const val DEFAULT_DEPTH = 32
        private const val DEG_TO_RAD = (Math.PI / 180.0).toFloat()
    }
}
