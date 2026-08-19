package com.paperscrape.livewallpaper.engine

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The OpenGL ES 2.0 backend: the same [SceneCanvas] operations, turned into triangles.
 *
 * ## Coordinate space
 *
 * The projection is an orthographic matrix mapping `(0,0)` to the top-left of the surface and
 * `(width, height)` to the bottom-right — pixels, with Y increasing downwards, exactly the space
 * `Canvas` works in. That is the single most load-bearing decision in this class: it means every
 * coordinate, sprite origin, depth constant and historical divisor in the scene keeps its existing
 * value and its existing meaning. A world space in normalised units would have required rescaling
 * all of them, and a sprite whose origin is only correct together with its scale convention is
 * precisely how defect D-1 happened.
 *
 * ## Transform stack
 *
 * `save`/`restore`/`translate`/`scale`/`rotate` maintain a 2x3 affine transform in a flat
 * `FloatArray` stack, and vertices are transformed on the CPU as they are emitted. The alternative —
 * a model matrix uniform per transform change — would end a batch at every `save()`, and the scene
 * changes transform far more often than it changes texture.
 *
 * ## Batching
 *
 * Vertices accumulate into one interleaved buffer and are flushed when the bound texture changes or
 * the buffer fills. Because flat fills sample a 1x1 white texture rather than using a second shader,
 * a run of solid shapes between two sprites does not split the batch — and a long run of the same
 * sprite (the star field, the rain) collapses into a single draw call.
 *
 * Batching by texture is as far as this goes without an atlas: a scene object alternating sprites
 * and flat parts still flushes between them. That is a known ceiling, not an oversight.
 *
 * ## Curve tessellation
 *
 * Circles, ovals, arcs and wedges are tessellated at a segment count derived from their radius *in
 * device pixels* — the local radius scaled by the current transform — so a shape drawn inside a
 * `canvas.scale(1/3)` sprite transform is not tessellated as though it were three times larger.
 *
 * ## Allocation
 *
 * Nothing here allocates per frame. The vertex buffer, the transform stack and every matrix are
 * allocated once in the constructor; primitive generation writes into them in place.
 */
internal class GlSceneTarget : SceneCanvas {

    private val program = GlSpriteProgram()
    private val textures = GlTextureCache()

    private val vertexData = FloatArray(MAX_VERTICES * GlSpriteProgram.FLOATS_PER_VERTEX)
    private val vertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(vertexData.size * GlSpriteProgram.BYTES_PER_FLOAT)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private var vertexCount = 0
    private var writeIndex = 0
    private var boundTexture = 0

    /** Registry index of the 1x1 white pixel, or -1 before it has been packed. */
    private var whiteIndex = -1
    private var whiteTexture = 0
    private var whiteU = 0f
    private var whiteV = 0f

    private val projectionMatrix = FloatArray(16)

    /** The `Canvas` transform semantics, as arithmetic. See [SceneTransform]. */
    private val transform = SceneTransform()

    private var surfaceWidth = 0
    private var surfaceHeight = 0

    /** Set when the program failed to build; the caller falls back rather than drawing nothing forever. */
    var isUsable = false
        private set

    // --- Lifecycle ---------------------------------------------------------------------------

    /** Builds every GL resource. Must run on the render thread with the context current. */
    fun onContextCreated(): Boolean {
        textures.invalidate()
        isUsable = program.compile()
        if (!isUsable) return false
        if (!registerWhitePixel()) {
            isUsable = false
            return false
        }
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_BLEND)
        // Premultiplied source. See GlSpriteProgram for why this pairing is not interchangeable
        // with the GL_SRC_ALPHA form.
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        return true
    }

    fun onSurfaceSizeChanged(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        GLES20.glViewport(0, 0, width, height)
        // Top-left origin, Y down: the Canvas convention, preserved so scene coordinates do not move.
        Matrix.orthoM(projectionMatrix, 0, 0f, width.toFloat(), height.toFloat(), 0f, -1f, 1f)
    }

    /** Drops GL objects without touching a context that is already gone. */
    fun onContextLost() {
        program.invalidate()
        textures.invalidate()
        whiteIndex = -1
        whiteTexture = 0
        boundTexture = 0
        vertexCount = 0
        writeIndex = 0
        isUsable = false
    }

    /** Releases GL objects while the context is still current. */
    fun release() {
        textures.clear()
        program.release()
        whiteIndex = -1
        whiteTexture = 0
        boundTexture = 0
        isUsable = false
    }

    /**
     * Drops every uploaded sprite texture in response to memory pressure.
     *
     * Each is re-uploaded from [SpriteCache] on next use, so this costs a re-upload and never a
     * missing sprite. Called only from the render thread.
     */
    fun trimTextures() {
        flush()
        textures.clear()
        boundTexture = 0
        // The white pixel lives in the atlas, so it goes with everything else and has to be the
        // first thing packed back in — both because flat geometry cannot be drawn without it, and
        // because being first is what keeps it in the atlas rather than pushed out to a texture of
        // its own once the shelves fill.
        registerWhitePixel()
    }

    /**
     * Packs the flat-fill white pixel and caches the coordinates every solid vertex uses.
     *
     * The UV is taken from the *centre* of the entry rather than its corner. A 1x1 entry is a single
     * texel with a transparent border, and sampling at its corner would sit exactly on the boundary
     * between the texel and that border — where bilinear filtering would mix in the transparency and
     * make every flat fill half-alpha. The centre is the only sampling point that is unambiguous.
     */
    private fun registerWhitePixel(): Boolean {
        whiteIndex = textures.registerWhitePixel(WHITE_PIXEL_KEY)
        if (whiteIndex < 0) {
            whiteTexture = 0
            return false
        }
        whiteTexture = textures.handleAt(whiteIndex)
        whiteU = (textures.u0At(whiteIndex) + textures.u1At(whiteIndex)) * 0.5f
        whiteV = (textures.v0At(whiteIndex) + textures.v1At(whiteIndex)) * 0.5f
        return true
    }

    // --- Frame -------------------------------------------------------------------------------

    fun beginFrame() {
        vertexCount = 0
        writeIndex = 0
        boundTexture = 0
        transform.reset()
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        program.use()
        program.setMvpMatrix(projectionMatrix)
    }

    fun endFrame() {
        flush()
    }

    private fun flush() {
        if (vertexCount == 0) return
        vertexBuffer.clear()
        vertexBuffer.put(vertexData, 0, writeIndex)
        vertexBuffer.position(0)
        program.bindVertexData(vertexBuffer)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, boundTexture)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
        vertexCount = 0
        writeIndex = 0
    }

    /** Makes [texture] the batch's texture, flushing first if that changes it. */
    private fun useTexture(texture: Int) {
        if (texture != boundTexture) {
            flush()
            boundTexture = texture
        }
    }

    /** Flushes if [needed] more vertices would not fit. */
    private fun ensureRoom(needed: Int) {
        if (vertexCount + needed > MAX_VERTICES) flush()
    }

    // --- Transform stack ---------------------------------------------------------------------

    override fun save() = transform.save()

    override fun restore() = transform.restore()

    override fun translate(dx: Float, dy: Float) = transform.translate(dx, dy)

    override fun scale(sx: Float, sy: Float) = transform.scale(sx, sy)

    override fun rotate(degrees: Float) = transform.rotate(degrees)

    private fun segmentsFor(localRadius: Float): Int {
        val deviceRadius = localRadius * transform.uniformScale()
        return ceil(deviceRadius / DEVICE_PIXELS_PER_SEGMENT).toInt().coerceIn(MIN_SEGMENTS, MAX_SEGMENTS)
    }

    // --- Vertex emission ---------------------------------------------------------------------

    private fun vertex(x: Float, y: Float, u: Float, v: Float, r: Float, g: Float, bl: Float, al: Float) {
        var i = writeIndex
        vertexData[i++] = transform.mapX(x, y)
        vertexData[i++] = transform.mapY(x, y)
        vertexData[i++] = u
        vertexData[i++] = v
        vertexData[i++] = r
        vertexData[i++] = g
        vertexData[i++] = bl
        vertexData[i++] = al
        writeIndex = i
        vertexCount++
    }

    private fun solidVertex(x: Float, y: Float) {
        vertex(x, y, whiteU, whiteV, colR, colG, colB, colA)
    }

    private fun solidVertex(x: Float, y: Float, alpha: Float) {
        vertex(x, y, whiteU, whiteV, colR, colG, colB, alpha)
    }

    /** Current flat colour, unpacked once per primitive rather than per vertex. */
    private var colR = 1f
    private var colG = 1f
    private var colB = 1f
    private var colA = 1f

    private fun setColor(color: Int) {
        colR = Color.red(color) * INV_255
        colG = Color.green(color) * INV_255
        colB = Color.blue(color) * INV_255
        colA = Color.alpha(color) * INV_255
    }

    /**
     * Prepares a flat fill, returning false when the paint is fully transparent.
     *
     * Under premultiplied blending a zero-alpha primitive contributes exactly nothing, so emitting
     * its geometry is pure cost. The scene fades a lot of things through zero — precipitation,
     * leaves, star twinkle, the sleigh's edge fade — and those frames get the saving for free.
     */
    private fun beginSolid(paint: Paint): Boolean {
        val color = paint.color
        if (color ushr 24 == 0) return false
        useTexture(whiteTexture)
        setColor(color)
        return true
    }

    private fun triangle(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
        solidVertex(x0, y0)
        solidVertex(x1, y1)
        solidVertex(x2, y2)
    }

    private fun quad(
        x0: Float, y0: Float,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        x3: Float, y3: Float,
    ) {
        triangle(x0, y0, x1, y1, x2, y2)
        triangle(x0, y0, x2, y2, x3, y3)
    }

    // --- Primitives --------------------------------------------------------------------------

    override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        if (paint.style == Paint.Style.STROKE) {
            val h = paint.strokeWidth * 0.5f
            if (!beginSolid(paint)) return
            ensureRoom(24)
            quad(left - h, top - h, right + h, top - h, right + h, top + h, left - h, top + h)
            quad(left - h, bottom - h, right + h, bottom - h, right + h, bottom + h, left - h, bottom + h)
            quad(left - h, top + h, left + h, top + h, left + h, bottom - h, left - h, bottom - h)
            quad(right - h, top + h, right + h, top + h, right + h, bottom - h, right - h, bottom - h)
            return
        }
        if (!beginSolid(paint)) return
        ensureRoom(6)
        quad(left, top, right, top, right, bottom, left, bottom)
    }

    override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
        val dx = stopX - startX
        val dy = stopY - startY
        val len = hypot(dx, dy)
        if (len <= 0f) return
        val half = paint.strokeWidth * 0.5f
        val nx = -dy / len * half
        val ny = dx / len * half
        if (!beginSolid(paint)) return
        ensureRoom(6)
        quad(startX + nx, startY + ny, stopX + nx, stopY + ny, stopX - nx, stopY - ny, startX - nx, startY - ny)
        if (paint.strokeCap == Paint.Cap.ROUND) {
            fillDisc(startX, startY, half)
            fillDisc(stopX, stopY, half)
        }
    }

    override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
        if (!beginSolid(paint)) return
        if (paint.style == Paint.Style.STROKE) {
            fillRing(cx, cy, radius - paint.strokeWidth * 0.5f, radius + paint.strokeWidth * 0.5f)
        } else {
            fillDisc(cx, cy, radius)
        }
    }

    override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        val cx = (left + right) * 0.5f
        val cy = (top + bottom) * 0.5f
        val rx = (right - left) * 0.5f
        val ry = (bottom - top) * 0.5f
        if (!beginSolid(paint)) return
        val segments = segmentsFor(if (rx > ry) rx else ry)
        ensureRoom(segments * 3)
        val step = TWO_PI / segments
        var prevX = cx + rx
        var prevY = cy
        for (i in 1..segments) {
            val angle = step * i
            val x = cx + rx * cos(angle)
            val y = cy + ry * sin(angle)
            triangle(cx, cy, prevX, prevY, x, y)
            prevX = x
            prevY = y
        }
    }

    override fun drawArc(oval: RectF, startAngle: Float, sweepAngle: Float, paint: Paint) {
        val cx = oval.centerX()
        val cy = oval.centerY()
        val rx = oval.width() * 0.5f
        val ry = oval.height() * 0.5f
        val half = paint.strokeWidth * 0.5f
        if (!beginSolid(paint)) return
        val segments = segmentsFor(if (rx > ry) rx else ry)
        ensureRoom(segments * 6)
        val start = startAngle * DEG_TO_RAD
        val sweep = sweepAngle * DEG_TO_RAD
        val step = sweep / segments
        var prevOuterX = cx + (rx + half) * cos(start)
        var prevOuterY = cy + (ry + half) * sin(start)
        var prevInnerX = cx + (rx - half) * cos(start)
        var prevInnerY = cy + (ry - half) * sin(start)
        for (i in 1..segments) {
            val angle = start + step * i
            val cs = cos(angle)
            val sn = sin(angle)
            val outerX = cx + (rx + half) * cs
            val outerY = cy + (ry + half) * sn
            val innerX = cx + (rx - half) * cs
            val innerY = cy + (ry - half) * sn
            quad(prevInnerX, prevInnerY, prevOuterX, prevOuterY, outerX, outerY, innerX, innerY)
            prevOuterX = outerX
            prevOuterY = outerY
            prevInnerX = innerX
            prevInnerY = innerY
        }
    }

    override fun drawWedge(
        cx: Float,
        cy: Float,
        radius: Float,
        startAngle: Float,
        sweepAngle: Float,
        paint: Paint,
    ) {
        if (!beginSolid(paint)) return
        val segments = segmentsFor(radius)
        ensureRoom(segments * 3)
        val start = startAngle * DEG_TO_RAD
        val step = sweepAngle * DEG_TO_RAD / segments
        var prevX = cx + radius * cos(start)
        var prevY = cy + radius * sin(start)
        for (i in 1..segments) {
            val angle = start + step * i
            val x = cx + radius * cos(angle)
            val y = cy + radius * sin(angle)
            triangle(cx, cy, prevX, prevY, x, y)
            prevX = x
            prevY = y
        }
    }

    override fun drawShape(shape: SceneShape, paint: Paint) {
        val n = shape.pointCount
        if (n < 3) return
        if (!beginSolid(paint)) return
        ensureRoom((n - 2) * 3)
        val x0 = shape.xAt(0)
        val y0 = shape.yAt(0)
        for (i in 1 until n - 1) {
            triangle(x0, y0, shape.xAt(i), shape.yAt(i), shape.xAt(i + 1), shape.yAt(i + 1))
        }
    }

    override fun drawVerticalGradientShape(
        shape: SceneShape,
        gradientTopY: Float,
        gradientBottomY: Float,
        topColor: Int,
        bottomColor: Int,
        alpha: Int,
    ) {
        val n = shape.pointCount
        if (n < 3 || alpha <= 0) return
        useTexture(whiteTexture)
        val span = gradientBottomY - gradientTopY
        val alphaScale = alpha * INV_255

        // Tessellated as vertical columns down to the shape's base line, **not** as a fan from one
        // vertex, and each column is split at the gradient's lower stop.
        //
        // The reason is fidelity rather than tidiness. A two-stop ramp is linear inside its band and
        // flat outside it, and a triangle interpolates linearly across its whole area — so a fan
        // whose apex sits on the base line would carry the highlight all the way down every
        // triangle instead of letting it stop at the lower stop, turning a highlight on the top
        // third of the hill into a wash over the whole of it. Splitting each column at that stop
        // puts a real vertex on the boundary, which is what makes the flat region flat.
        val baseY = shape.yAt(0)
        for (i in 0 until n - 1) {
            val xL = shape.xAt(i)
            val yL = shape.yAt(i)
            val xR = shape.xAt(i + 1)
            val yR = shape.yAt(i + 1)
            val topMost = if (yL > yR) yL else yR
            if (gradientBottomY > topMost && gradientBottomY < baseY) {
                ensureRoom(12)
                gradientQuad(
                    xL, yL, xR, yR, xR, gradientBottomY, xL, gradientBottomY,
                    gradientTopY, span, topColor, bottomColor, alphaScale,
                )
                gradientQuad(
                    xL, gradientBottomY, xR, gradientBottomY, xR, baseY, xL, baseY,
                    gradientTopY, span, topColor, bottomColor, alphaScale,
                )
            } else {
                ensureRoom(6)
                gradientQuad(
                    xL, yL, xR, yR, xR, baseY, xL, baseY,
                    gradientTopY, span, topColor, bottomColor, alphaScale,
                )
            }
        }
    }

    @Suppress("LongParameterList")
    private fun gradientQuad(
        x0: Float, y0: Float,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        x3: Float, y3: Float,
        gradientTopY: Float,
        span: Float,
        topColor: Int,
        bottomColor: Int,
        alphaScale: Float,
    ) {
        gradientVertex(x0, y0, gradientTopY, span, topColor, bottomColor, alphaScale)
        gradientVertex(x1, y1, gradientTopY, span, topColor, bottomColor, alphaScale)
        gradientVertex(x2, y2, gradientTopY, span, topColor, bottomColor, alphaScale)
        gradientVertex(x0, y0, gradientTopY, span, topColor, bottomColor, alphaScale)
        gradientVertex(x2, y2, gradientTopY, span, topColor, bottomColor, alphaScale)
        gradientVertex(x3, y3, gradientTopY, span, topColor, bottomColor, alphaScale)
    }

    private fun gradientVertex(
        x: Float,
        y: Float,
        gradientTopY: Float,
        span: Float,
        topColor: Int,
        bottomColor: Int,
        alphaScale: Float,
    ) {
        val t = if (span == 0f) 0f else ((y - gradientTopY) / span).coerceIn(0f, 1f)
        val r = (Color.red(topColor) + (Color.red(bottomColor) - Color.red(topColor)) * t) * INV_255
        val g = (Color.green(topColor) + (Color.green(bottomColor) - Color.green(topColor)) * t) * INV_255
        val bl = (Color.blue(topColor) + (Color.blue(bottomColor) - Color.blue(topColor)) * t) * INV_255
        val al = (Color.alpha(topColor) + (Color.alpha(bottomColor) - Color.alpha(topColor)) * t) * INV_255
        vertex(x, y, whiteU, whiteV, r, g, bl, al * alphaScale)
    }

    override fun drawVerticalGradientRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        topColor: Int,
        bottomColor: Int,
    ) {
        useTexture(whiteTexture)
        ensureRoom(6)
        val span = bottom - top
        // Four vertices for a full-screen gradient. The Canvas backend rasterises the same gradient
        // one pixel at a time.
        gradientVertex(left, top, top, span, topColor, bottomColor, 1f)
        gradientVertex(right, top, top, span, topColor, bottomColor, 1f)
        gradientVertex(right, bottom, top, span, topColor, bottomColor, 1f)
        gradientVertex(left, top, top, span, topColor, bottomColor, 1f)
        gradientVertex(right, bottom, top, span, topColor, bottomColor, 1f)
        gradientVertex(left, bottom, top, span, topColor, bottomColor, 1f)
    }

    override fun drawRadialGlow(cx: Float, cy: Float, radius: Float, color: Int, centerAlpha: Int) {
        if (centerAlpha <= 0 || radius <= 0f) return
        useTexture(whiteTexture)
        setColor(color)
        val segments = segmentsFor(radius)
        ensureRoom(segments * 3)
        val inner = centerAlpha * INV_255
        val step = TWO_PI / segments
        var prevX = cx + radius
        var prevY = cy
        for (i in 1..segments) {
            val angle = step * i
            val x = cx + radius * cos(angle)
            val y = cy + radius * sin(angle)
            // A two-stop radial gradient is linear in radius, and so is interpolation from the fan's
            // centre vertex to its rim, so the falloff matches rather than approximates.
            solidVertex(cx, cy, inner)
            solidVertex(prevX, prevY, 0f)
            solidVertex(x, y, 0f)
            prevX = x
            prevY = y
        }
    }

    override fun drawSprite(
        resId: Int,
        source: SpriteSource,
        left: Float,
        top: Float,
        tintColor: Int,
        alpha: Int,
    ) {
        // A fully transparent blit contributes nothing under this blend function, and the scene
        // produces plenty of them: every fading raindrop, leaf and twinkling star passes through
        // zero. Skipping them here costs one comparison and saves six vertices plus the geometry
        // that would have been rasterised behind them.
        if (alpha <= 0) return

        // The common case: the sprite is already on the GPU, so the pixels are never asked for and
        // its size comes from the registry. This is what keeps a per-frame blit off SpriteCache's
        // synchronised lookup entirely.
        var index = textures.find(resId)
        if (index < 0) {
            index = textures.register(resId, source.bitmapFor(resId))
            if (index < 0) return
            source.onSpriteUploaded(resId)
        }

        useTexture(textures.handleAt(index))
        ensureRoom(6)
        val right = left + textures.widthAt(index)
        val bottom = top + textures.heightAt(index)
        val u0 = textures.u0At(index)
        val v0 = textures.v0At(index)
        val u1 = textures.u1At(index)
        val v1 = textures.v1At(index)
        val r = Color.red(tintColor) * INV_255
        val g = Color.green(tintColor) * INV_255
        val bl = Color.blue(tintColor) * INV_255
        // The tint's own alpha is deliberately ignored: MULTIPLY tinting on the Canvas backend takes
        // opacity from the paint, not from the filter colour, and several call sites pass a colour
        // whose alpha byte is incidental.
        val al = alpha * INV_255
        vertex(left, top, u0, v0, r, g, bl, al)
        vertex(right, top, u1, v0, r, g, bl, al)
        vertex(right, bottom, u1, v1, r, g, bl, al)
        vertex(left, top, u0, v0, r, g, bl, al)
        vertex(right, bottom, u1, v1, r, g, bl, al)
        vertex(left, bottom, u0, v1, r, g, bl, al)
    }

    // --- Shared tessellation -----------------------------------------------------------------

    private fun fillDisc(cx: Float, cy: Float, radius: Float) {
        if (radius <= 0f) return
        val segments = segmentsFor(radius)
        ensureRoom(segments * 3)
        val step = TWO_PI / segments
        var prevX = cx + radius
        var prevY = cy
        for (i in 1..segments) {
            val angle = step * i
            val x = cx + radius * cos(angle)
            val y = cy + radius * sin(angle)
            triangle(cx, cy, prevX, prevY, x, y)
            prevX = x
            prevY = y
        }
    }

    private fun fillRing(cx: Float, cy: Float, innerRadius: Float, outerRadius: Float) {
        if (outerRadius <= 0f) return
        val inner = if (innerRadius < 0f) 0f else innerRadius
        val segments = segmentsFor(outerRadius)
        ensureRoom(segments * 6)
        val step = TWO_PI / segments
        var prevOuterX = cx + outerRadius
        var prevOuterY = cy
        var prevInnerX = cx + inner
        var prevInnerY = cy
        for (i in 1..segments) {
            val angle = step * i
            val cs = cos(angle)
            val sn = sin(angle)
            val outerX = cx + outerRadius * cs
            val outerY = cy + outerRadius * sn
            val innerX = cx + inner * cs
            val innerY = cy + inner * sn
            quad(prevInnerX, prevInnerY, prevOuterX, prevOuterY, outerX, outerY, innerX, innerY)
            prevOuterX = outerX
            prevOuterY = outerY
            prevInnerX = innerX
            prevInnerY = innerY
        }
    }

    private companion object {
        /**
         * Vertex budget per batch. Sized so the densest single primitive run in the scene — the
         * precipitation pool at 90 candidates, each a capped line — fits without an intermediate
         * flush; beyond it a flush is a correctness mechanism, not a failure.
         */
        const val MAX_VERTICES = 12288
        const val INV_255 = 1f / 255f
        const val TWO_PI = (2.0 * Math.PI).toFloat()
        const val DEG_TO_RAD = (Math.PI / 180.0).toFloat()

        /** One segment per this many device pixels of radius: the tessellation/vertex-count trade. */
        const val DEVICE_PIXELS_PER_SEGMENT = 3f
        const val MIN_SEGMENTS = 8
        const val MAX_SEGMENTS = 64

        /**
         * Registry key for the flat-fill white pixel.
         *
         * Negative so it can never collide with a real `R.drawable` id, which are always positive.
         */
        const val WHITE_PIXEL_KEY = -1
    }
}
