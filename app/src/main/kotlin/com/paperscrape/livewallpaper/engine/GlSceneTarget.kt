package com.paperscrape.livewallpaper.engine

import android.graphics.Color
import android.graphics.Paint
import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The OpenGL ES 2.0 backend: the same [SceneCanvas] operations, turned into triangles.
 *
 * **Coordinates** are the `Canvas` ones -- pixels, top-left origin, Y down -- so every coordinate, sprite
 * origin and depth constant of the scene means the same on both backends. **The transform**
 * (`save`/`translate`/`scale`/`rotate`, [SceneTransform]) is applied on the CPU as vertices are written: a
 * matrix uniform would end the batch at every `save()`, and the scene changes transform far more often than
 * texture.
 *
 * **The batch**: vertices go into one interleaved buffer, each triangle names its three corners by index, and
 * one `glDrawElements` draws it. A quad's and a circle fan's shared points are written once ([putVertex],
 * [tri]): a quad is 6 corners and 4 vertices, a fan of n slices 3n and n + 2; a wedge, a shape and the
 * gradient rectangle write a vertex per corner. The batch is cut on the corners ([MAX_VERTICES],
 * [ensureRoom]), which the written vertices never exceed, and flushed when the bound texture changes. Every
 * sprite and the flat-fill white pixel share one [GlTextureAtlas], so sprites and solid shapes interleave in
 * one draw; only a standalone texture ends a batch.
 *
 * **Curves** get a segment count from their radius in device pixels, so a shape inside a `scale(1/3)` sprite
 * transform is not tessellated as if three times larger. **Nothing allocates per frame.** A shared point is
 * mapped once, [CircleTable] holds the loop's own `cos` and `sin` bit for bit, and a hill column off the
 * surface is not emitted: the same floats, or no sample, so the same frame.
 */
internal class GlSceneTarget : SceneCanvas {

    private val program = GlSpriteProgram()
    private val textures = GlTextureCache()

    /** What the texture cache holds, for the measurements; never read by the draw path. */
    val uploadedEntries: Int get() = textures.size

    val standaloneTextures: Int get() = textures.standaloneCount

    val atlasRowsUsed: Int get() = textures.atlasRowsUsed

    /** Whether the texture cache holds [resId] at some level, for the GL sweeps. */
    fun holdsSprite(resId: Int): Boolean = textures.holds(resId)

    private val vertexData = FloatArray(MAX_VERTICES * GlSpriteProgram.FLOATS_PER_VERTEX)
    private val vertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(vertexData.size * GlSpriteProgram.BYTES_PER_FLOAT)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    /** Vertices written into [vertexData] since the last flush; [writeIndex] is where the next one goes. */
    private var vertexCount = 0
    private var writeIndex = 0

    /** The batch's triangles, three vertex indices each; [indexCount] counts the corners. Under [MAX_VERTICES], a short. */
    private val indexData = ShortArray(MAX_VERTICES)
    private var indexCount = 0
    private val indexBuffer: ShortBuffer = ByteBuffer
        .allocateDirect(MAX_VERTICES * 2)
        .order(ByteOrder.nativeOrder())
        .asShortBuffer()
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

    /**
     * True once the program and the white pixel are built; false otherwise, and the render thread then
     * rebuilds, falling back only when that fails, rather than drawing nothing forever.
     */
    var isUsable = false
        private set

    // --- Lifecycle ---------------------------------------------------------------------------

    /** Builds every GL resource. Must run on the render thread with the context current. */
    fun onContextCreated(): Boolean {
        whitePixelOwed = false
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
        // Premultiplied source; GlSpriteProgram says why the GL_SRC_ALPHA form would not do.
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
        whitePixelOwed = false
        program.invalidate()
        textures.invalidate()
        whiteIndex = -1
        whiteTexture = 0
        boundTexture = 0
        vertexCount = 0
        writeIndex = 0
        indexCount = 0
        isUsable = false
    }

    /** Releases GL objects while the context is still current. */
    fun release() {
        whitePixelOwed = false
        textures.clear()
        program.release()
        whiteIndex = -1
        whiteTexture = 0
        boundTexture = 0
        isUsable = false
    }

    /**
     * The trim of a wallpaper that is drawing ([MemoryPressurePolicy.gpuTrimFor]): the standalone textures go (uploaded
     * again from [SpriteCache] on next use); the atlas page stays, since the next frame would re-create it at once,
     * upload every sprite again and give nothing back. Render thread only, context current.
     */
    fun trimTextures() {
        flush()
        textures.clearStandalone()
        boundTexture = 0
        // The entries are renumbered, so the white pixel is looked up, and packed back if gone (a flat fill without it
        // draws black); a failure leaves the target unusable, so the next prepared frame rebuilds it.
        whiteIndex = textures.find(WHITE_PIXEL_KEY, 0)
        if (whiteIndex < 0 && !registerWhitePixel()) isUsable = false
    }

    /**
     * The trim of a wallpaper that is not drawing ([MemoryPressurePolicy.gpuTrimFor]): every texture goes, the atlas
     * and the white pixel included, and the white pixel is packed again only when the next frame is prepared
     * ([prepareForFrame]). Packing even one texel re-creates the whole 2048x2048 atlas page, so a white pixel packed
     * straight back would give nothing back (owed instead, the trim gives back about 16 MB on the BV6600).
     */
    fun trimTexturesWhileHidden() {
        flush()
        textures.clear()
        boundTexture = 0
        whiteIndex = -1
        whiteTexture = 0
        whitePixelOwed = true
    }

    /** Set by [trimTexturesWhileHidden]: the white pixel has to be packed before anything is drawn. */
    private var whitePixelOwed = false

    /**
     * Packs the white pixel [trimTexturesWhileHidden] left owed, before [beginFrame] of every prepared frame. A failure
     * leaves [isUsable] false, so the render thread rebuilds the target before drawing.
     */
    fun prepareForFrame() {
        if (!whitePixelOwed) return
        whitePixelOwed = false
        if (!registerWhitePixel()) isUsable = false
    }

    /**
     * Packs the flat-fill white pixel and caches its UV: the entry's *centre*, since at its corner bilinear
     * filtering would mix in the transparent padding and make every flat fill half-alpha.
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
        indexCount = 0
        drawCalls = 0
        drawnVertices = 0L
        writtenVertices = 0L
        spriteBlits = 0
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

    /**
     * The draws (`glDrawElements`) issued since [beginFrame], and their triangle corners ([drawnVertices], what
     * [MAX_VERTICES] cuts on): counted, not predicted from the batching rules, so `GlDrawCallTest` reads them off a
     * real crowded frame on the device.
     */
    var drawCalls = 0
        private set

    var drawnVertices = 0L
        private set

    /** How many distinct vertices those draws wrote: a quad's four corners once, a fan's centre once. */
    var writtenVertices = 0L
        private set

    /** How many sprite blits went into those draws, so a layer's six vertices can be seen. */
    var spriteBlits = 0
        private set

    private fun flush() {
        if (indexCount == 0) return
        drawCalls++
        drawnVertices += indexCount
        writtenVertices += vertexCount
        vertexBuffer.clear()
        vertexBuffer.put(vertexData, 0, writeIndex)
        vertexBuffer.position(0)
        program.bindVertexData(vertexBuffer)
        indexBuffer.clear()
        indexBuffer.put(indexData, 0, indexCount)
        indexBuffer.position(0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, boundTexture)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indexBuffer)
        vertexCount = 0
        writeIndex = 0
        indexCount = 0
    }

    /** Makes [texture] the batch's texture, flushing first if that changes it. */
    private fun useTexture(texture: Int) {
        if (texture != boundTexture) {
            flush()
            boundTexture = texture
        }
    }

    /** Flushes if [needed] more triangle corners would not fit; the vertices they write are never more. */
    private fun ensureRoom(needed: Int) {
        if (indexCount + needed > MAX_VERTICES) flush()
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
        mappedVertex(transform.mapX(x, y), transform.mapY(x, y), u, v, r, g, bl, al)
    }

    /** A vertex already mapped through [transform], for one triangle corner: written, and its index put in the batch. */
    private fun mappedVertex(mx: Float, my: Float, u: Float, v: Float, r: Float, g: Float, bl: Float, al: Float) {
        indexData[indexCount++] = putVertex(mx, my, u, v, r, g, bl, al).toShort()
    }

    /** A vertex written once, for the triangles that name it ([tri]); returns its index in the batch. */
    private fun putVertex(mx: Float, my: Float, u: Float, v: Float, r: Float, g: Float, bl: Float, al: Float): Int {
        var i = writeIndex
        vertexData[i++] = mx
        vertexData[i++] = my
        vertexData[i++] = u
        vertexData[i++] = v
        vertexData[i++] = r
        vertexData[i++] = g
        vertexData[i++] = bl
        vertexData[i++] = al
        writeIndex = i
        return vertexCount++
    }

    /** One triangle, by its three vertices' indices. */
    private fun tri(a: Int, b: Int, c: Int) {
        indexData[indexCount++] = a.toShort()
        indexData[indexCount++] = b.toShort()
        indexData[indexCount++] = c.toShort()
    }

    /** A quad's four corners written once and its two triangles, (0, 1, 2) and (0, 2, 3), by index. */
    @Suppress("LongParameterList")
    private fun indexedQuad(
        m0x: Float, m0y: Float, m1x: Float, m1y: Float, m2x: Float, m2y: Float, m3x: Float, m3y: Float,
        u0: Float, v0: Float, u1: Float, v1: Float, r: Float, g: Float, bl: Float, al: Float,
    ) {
        val a = putVertex(m0x, m0y, u0, v0, r, g, bl, al)
        val b = putVertex(m1x, m1y, u1, v0, r, g, bl, al)
        val c = putVertex(m2x, m2y, u1, v1, r, g, bl, al)
        val d = putVertex(m3x, m3y, u0, v1, r, g, bl, al)
        tri(a, b, c)
        tri(a, c, d)
    }

    private fun solidVertex(x: Float, y: Float) {
        vertex(x, y, whiteU, whiteV, colR, colG, colB, colA)
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
     * Prepares a flat fill, returning false when the paint is fully transparent: under premultiplied blending a
     * zero-alpha primitive adds nothing, and the scene fades many things through zero.
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
        // Four corners mapped and written once, two triangles by index: the diagonal's two ends are shared.
        val m0x = transform.mapX(x0, y0)
        val m0y = transform.mapY(x0, y0)
        val m1x = transform.mapX(x1, y1)
        val m1y = transform.mapY(x1, y1)
        val m2x = transform.mapX(x2, y2)
        val m2y = transform.mapY(x2, y2)
        val m3x = transform.mapX(x3, y3)
        val m3y = transform.mapY(x3, y3)
        indexedQuad(m0x, m0y, m1x, m1y, m2x, m2y, m3x, m3y, whiteU, whiteV, whiteU, whiteV, colR, colG, colB, colA)
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
        // A square cap is the Canvas's `SQUARE`: the quad reaches half the width past each point, along the line, and
        // stays one quad -- a raindrop's ends (`DESIGN_NOTES.md` §12, decision 87). `BUTT` and `ROUND` end the quad at
        // the two points.
        val reach = if (paint.strokeCap == Paint.Cap.SQUARE) half / len else 0f
        val ax = startX - dx * reach
        val ay = startY - dy * reach
        val bx = stopX + dx * reach
        val by = stopY + dy * reach
        quad(ax + nx, ay + ny, bx + nx, by + ny, bx - nx, by - ny, ax - nx, ay - ny)
        // A round cap is a fan at each end, eight slices at the least however thin the line ([segmentsFor]): 48 of the
        // 54 corners of a thin line.
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
        val cosines = CircleTable.cosines(segments)
        val sines = CircleTable.sines(segments)
        val mcx = transform.mapX(cx, cy)
        val mcy = transform.mapY(cx, cy)
        val c = putVertex(mcx, mcy, whiteU, whiteV, colR, colG, colB, colA)
        var p = putVertex(transform.mapX(cx + rx, cy), transform.mapY(cx + rx, cy), whiteU, whiteV, colR, colG, colB, colA)
        for (i in 1..segments) {
            val x = cx + rx * cosines[i]
            val y = cy + ry * sines[i]
            val n = putVertex(transform.mapX(x, y), transform.mapY(x, y), whiteU, whiteV, colR, colG, colB, colA)
            tri(c, p, n)
            p = n
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

        // Vertical columns down to the shape's base line, each split at the gradient's lower stop, not a fan:
        // a two-stop ramp is flat below its band, and a triangle interpolates across its whole area, so a fan
        // would carry the highlight down the whole hill. The split puts a vertex on the stop.
        val baseY = shape.yAt(0)
        for (i in 0 until n - 1) {
            val xL = shape.xAt(i)
            val yL = shape.yAt(i)
            val xR = shape.xAt(i + 1)
            val yR = shape.yAt(i + 1)
            // A column wholly left or right of the surface covers no sample and is not emitted; one touching
            // an edge is. The hills hand over a shape two screens wide per tile copy, so about half are off.
            if (columnOffSurface(xL, yL, xR, yR, baseY)) continue
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

    /**
     * Whether the column from ([xL], [yL]) to ([xR], [yR]) down to [baseY] lies wholly left or right of the
     * surface once mapped; only x is tested, since the hills scroll sideways and never leave it vertically.
     */
    private fun columnOffSurface(xL: Float, yL: Float, xR: Float, yR: Float, baseY: Float): Boolean {
        if (surfaceWidth <= 0) return false
        val a = transform.mapX(xL, yL)
        val b = transform.mapX(xL, baseY)
        val c = transform.mapX(xR, yR)
        val d = transform.mapX(xR, baseY)
        val min = minOf(minOf(a, b), minOf(c, d))
        val max = maxOf(maxOf(a, b), maxOf(c, d))
        return max < 0f || min > surfaceWidth.toFloat()
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
        // Each corner worked out and written once for both triangles, by [gradientVertex]'s expressions.
        gradientCorner(0, x0, y0, gradientTopY, span, topColor, bottomColor, alphaScale)
        gradientCorner(1, x1, y1, gradientTopY, span, topColor, bottomColor, alphaScale)
        gradientCorner(2, x2, y2, gradientTopY, span, topColor, bottomColor, alphaScale)
        gradientCorner(3, x3, y3, gradientTopY, span, topColor, bottomColor, alphaScale)
        val a = putCorner(0)
        val b = putCorner(1)
        val c = putCorner(2)
        val d = putCorner(3)
        tri(a, b, c)
        tri(a, c, d)
    }

    /** `[mx, my, r, g, b, a]` for each corner of the gradient quad being emitted. */
    private val corners = FloatArray(4 * 6)

    @Suppress("LongParameterList")
    private fun gradientCorner(
        k: Int,
        x: Float,
        y: Float,
        gradientTopY: Float,
        span: Float,
        topColor: Int,
        bottomColor: Int,
        alphaScale: Float,
    ) {
        val t = if (span == 0f) 0f else ((y - gradientTopY) / span).coerceIn(0f, 1f)
        val o = k * 6
        corners[o] = transform.mapX(x, y)
        corners[o + 1] = transform.mapY(x, y)
        corners[o + 2] = (Color.red(topColor) + (Color.red(bottomColor) - Color.red(topColor)) * t) * INV_255
        corners[o + 3] = (Color.green(topColor) + (Color.green(bottomColor) - Color.green(topColor)) * t) * INV_255
        corners[o + 4] = (Color.blue(topColor) + (Color.blue(bottomColor) - Color.blue(topColor)) * t) * INV_255
        val al = (Color.alpha(topColor) + (Color.alpha(bottomColor) - Color.alpha(topColor)) * t) * INV_255
        corners[o + 5] = al * alphaScale
    }

    private fun putCorner(k: Int): Int {
        val o = k * 6
        return putVertex(corners[o], corners[o + 1], whiteU, whiteV, corners[o + 2], corners[o + 3], corners[o + 4], corners[o + 5])
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
        // Two triangles, a vertex for each of their six corners, for a full-screen gradient.
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
        val cosines = CircleTable.cosines(segments)
        val sines = CircleTable.sines(segments)
        val mcx = transform.mapX(cx, cy)
        val mcy = transform.mapY(cx, cy)
        // A two-stop radial gradient is linear in radius, as is the fan's interpolation: the falloff is exact.
        val c = putVertex(mcx, mcy, whiteU, whiteV, colR, colG, colB, inner)
        var p = putVertex(transform.mapX(cx + radius, cy), transform.mapY(cx + radius, cy), whiteU, whiteV, colR, colG, colB, 0f)
        for (i in 1..segments) {
            val x = cx + radius * cosines[i]
            val y = cy + radius * sines[i]
            val n = putVertex(transform.mapX(x, y), transform.mapY(x, y), whiteU, whiteV, colR, colG, colB, 0f)
            tri(c, p, n)
            p = n
        }
    }

    /**
     * One sprite, whole: the call the frame makes most.
     *
     * **It keeps its own body, sharing none with [drawSpriteClipped].** One function with the cut's four extra
     * arguments and a branch grows past what ART inlines into, and the three small calls this body makes --
     * [SceneTransform.uniformScale], [useTexture], [ensureRoom] -- become real calls on every sprite: 5 % more
     * cycles on the sprite path at midnight and 21 % at noon (`simpleperf`, the BV6600's `perf` build, Autumn).
     */
    override fun drawSprite(
        resId: Int,
        source: SpriteSource,
        left: Float,
        top: Float,
        tintColor: Int,
        alpha: Int,
        additive: Boolean,
    ) {
        // A fully transparent blit adds nothing under this blend, and the stars' sparkles, the rainbow, the
        // fireworks and the lightning all pass through zero.
        if (alpha <= 0) return

        // How far this draw shrinks the sprite picks the pre-reduced copy to sample, so the GPU filters at about
        // 1:1 ([SpriteDetailLevel]: why not GL mipmaps).
        val level = SpriteDetailLevel.levelFor(transform.uniformScale())

        // Usually already uploaded at this level, so a per-frame blit never touches SpriteCache's synchronised lookup.
        var index = textures.find(resId, level)
        if (index < 0) {
            index = textures.register(resId, level, source.bitmapFor(resId))
            if (index < 0) return
            // Released though another level may want it: the cache re-decodes on demand, a sprite settles into one or
            // two levels within a frame or two, and a held bitmap costs heap for the process's life.
            source.onSpriteUploaded(resId)
        }

        spriteBlits++
        useTexture(textures.handleAt(index))
        ensureRoom(6)
        // **The quad is the sprite's content box, not its canvas**: [GlTextureCache] uploads only the texels
        // that carry ink, so the quad names that sub-rectangle, as fractions of the authored box, or a cropped
        // sprite is stretched over its whole canvas.
        val width = textures.widthAt(index)
        val height = textures.heightAt(index)
        val quadLeft = left + width * textures.contentLeftAt(index)
        val quadTop = top + height * textures.contentTopAt(index)
        val right = left + width * textures.contentRightAt(index)
        val bottom = top + height * textures.contentBottomAt(index)
        val u0 = textures.u0At(index)
        val v0 = textures.v0At(index)
        val u1 = textures.u1At(index)
        val v1 = textures.v1At(index)
        val r = Color.red(tintColor) * INV_255
        val g = Color.green(tintColor) * INV_255
        val bl = Color.blue(tintColor) * INV_255
        // The tint's own alpha is ignored. The Canvas backend's `MULTIPLY` filter does multiply it in, so a
        // day/night `SceneColour.blendArgb` that comes out at 254 (about one ratio in 26) is one level dimmer there
        // at moments of dusk and dawn: not seen, and left so; `TintOpacityTest` holds the loss to that one level, and
        // none at the blend's ends.
        // A negative vertex alpha makes the fragment shader ([GlSpriteProgram]) leave with zero alpha, which this
        // blend sums rather than lays over: the additive blit.
        val al = if (additive) -(alpha * INV_255) else alpha * INV_255
        val m0x = transform.mapX(quadLeft, quadTop)
        val m0y = transform.mapY(quadLeft, quadTop)
        val m1x = transform.mapX(right, quadTop)
        val m1y = transform.mapY(right, quadTop)
        val m2x = transform.mapX(right, bottom)
        val m2y = transform.mapY(right, bottom)
        val m3x = transform.mapX(quadLeft, bottom)
        val m3y = transform.mapY(quadLeft, bottom)
        indexedQuad(m0x, m0y, m1x, m1y, m2x, m2y, m3x, m3y, u0, v0, u1, v1, r, g, bl, al)
    }

    /**
     * The same quad as [drawSprite], cut to its box with its texture rectangle ([SpriteClip]): a smaller quad from
     * the same atlas entry, in the same batch, no GL state changed, so it costs what a whole sprite does. A body of
     * its own, as [drawSprite] says. Only a person walking out of a window or into one is drawn this way.
     */
    override fun drawSpriteClipped(
        resId: Int,
        source: SpriteSource,
        left: Float,
        top: Float,
        tintColor: Int,
        alpha: Int,
        additive: Boolean,
        clipLeft: Float,
        clipTop: Float,
        clipRight: Float,
        clipBottom: Float,
    ) {
        if (alpha <= 0) return
        val level = SpriteDetailLevel.levelFor(transform.uniformScale())
        var index = textures.find(resId, level)
        if (index < 0) {
            index = textures.register(resId, level, source.bitmapFor(resId))
            if (index < 0) return
            source.onSpriteUploaded(resId)
        }
        val width = textures.widthAt(index)
        val height = textures.heightAt(index)
        // Cut before the transform, where the quad and the box are both axis-aligned: exact under a walker's
        // mirror. A side the box does not cut keeps [drawSprite]'s numbers to the bit.
        val cut = clippedQuad
        val inside = SpriteClip.clip(
            left + width * textures.contentLeftAt(index), top + height * textures.contentTopAt(index),
            left + width * textures.contentRightAt(index), top + height * textures.contentBottomAt(index),
            textures.u0At(index), textures.v0At(index), textures.u1At(index), textures.v1At(index),
            clipLeft, clipTop, clipRight, clipBottom, cut,
        )
        if (!inside) return
        spriteBlits++
        useTexture(textures.handleAt(index))
        ensureRoom(6)
        val quadLeft = cut[SpriteClip.LEFT]
        val quadTop = cut[SpriteClip.TOP]
        val right = cut[SpriteClip.RIGHT]
        val bottom = cut[SpriteClip.BOTTOM]
        val u0 = cut[SpriteClip.U0]
        val v0 = cut[SpriteClip.V0]
        val u1 = cut[SpriteClip.U1]
        val v1 = cut[SpriteClip.V1]
        val r = Color.red(tintColor) * INV_255
        val g = Color.green(tintColor) * INV_255
        val bl = Color.blue(tintColor) * INV_255
        // The tint's alpha ignored and the sign of the vertex alpha meaning "summed", as in [drawSprite].
        val al = if (additive) -(alpha * INV_255) else alpha * INV_255
        val m0x = transform.mapX(quadLeft, quadTop)
        val m0y = transform.mapY(quadLeft, quadTop)
        val m1x = transform.mapX(right, quadTop)
        val m1y = transform.mapY(right, quadTop)
        val m2x = transform.mapX(right, bottom)
        val m2y = transform.mapY(right, bottom)
        val m3x = transform.mapX(quadLeft, bottom)
        val m3y = transform.mapY(quadLeft, bottom)
        indexedQuad(m0x, m0y, m1x, m1y, m2x, m2y, m3x, m3y, u0, v0, u1, v1, r, g, bl, al)
    }

    /** [SpriteClip.clip]'s output, reused: the cut quad and its texture rectangle. */
    private val clippedQuad = FloatArray(SpriteClip.SIZE)

    /**
     * One quad of the atlas sampling the white disc [resId] over the square [radius] either side of ([cx], [cy]),
     * tinted [colour] at [alpha]: six corners where [fillDisc]'s fan is 24 or more. The quad is [drawSprite]'s with
     * the box scaled to the disc instead of the transform, so a disc needs no `save`/`scale`/`restore`; its detail
     * level is the transform's scale times the diameter over the disc's width in texels.
     */
    override fun drawDisc(
        resId: Int,
        source: SpriteSource,
        cx: Float,
        cy: Float,
        radius: Float,
        colour: Int,
        alpha: Int,
    ) {
        if (alpha <= 0 || radius <= 0f) return
        // The disc's width in texels, read off its pixels once: the level is chosen before the entry can be found.
        if (resId != discRes) {
            discTexels = source.bitmapFor(resId).width.toFloat()
            discRes = resId
        }
        val diameter = radius + radius
        val level = SpriteDetailLevel.levelFor(transform.uniformScale() * diameter / discTexels)
        var index = textures.find(resId, level)
        if (index < 0) {
            index = textures.register(resId, level, source.bitmapFor(resId))
            if (index < 0) return
            source.onSpriteUploaded(resId)
        }
        spriteBlits++
        useTexture(textures.handleAt(index))
        ensureRoom(6)
        val boxLeft = cx - radius
        val boxTop = cy - radius
        val quadLeft = boxLeft + diameter * textures.contentLeftAt(index)
        val quadTop = boxTop + diameter * textures.contentTopAt(index)
        val right = boxLeft + diameter * textures.contentRightAt(index)
        val bottom = boxTop + diameter * textures.contentBottomAt(index)
        val u0 = textures.u0At(index)
        val v0 = textures.v0At(index)
        val u1 = textures.u1At(index)
        val v1 = textures.v1At(index)
        val r = Color.red(colour) * INV_255
        val g = Color.green(colour) * INV_255
        val bl = Color.blue(colour) * INV_255
        val al = alpha * INV_255
        val m0x = transform.mapX(quadLeft, quadTop)
        val m0y = transform.mapY(quadLeft, quadTop)
        val m1x = transform.mapX(right, quadTop)
        val m1y = transform.mapY(right, quadTop)
        val m2x = transform.mapX(right, bottom)
        val m2y = transform.mapY(right, bottom)
        val m3x = transform.mapX(quadLeft, bottom)
        val m3y = transform.mapY(quadLeft, bottom)
        indexedQuad(m0x, m0y, m1x, m1y, m2x, m2y, m3x, m3y, u0, v0, u1, v1, r, g, bl, al)
    }

    /** [drawDisc]'s drawing and its width in texels, read once. */
    private var discRes = 0
    private var discTexels = 1f

    // --- Shared tessellation -----------------------------------------------------------------

    private fun fillDisc(cx: Float, cy: Float, radius: Float) {
        if (radius <= 0f) return
        val segments = segmentsFor(radius)
        ensureRoom(segments * 3)
        val cosines = CircleTable.cosines(segments)
        val sines = CircleTable.sines(segments)
        val mcx = transform.mapX(cx, cy)
        val mcy = transform.mapY(cx, cy)
        val c = putVertex(mcx, mcy, whiteU, whiteV, colR, colG, colB, colA)
        var p = putVertex(transform.mapX(cx + radius, cy), transform.mapY(cx + radius, cy), whiteU, whiteV, colR, colG, colB, colA)
        for (i in 1..segments) {
            val x = cx + radius * cosines[i]
            val y = cy + radius * sines[i]
            val n = putVertex(transform.mapX(x, y), transform.mapY(x, y), whiteU, whiteV, colR, colG, colB, colA)
            tri(c, p, n)
            p = n
        }
    }

    private fun fillRing(cx: Float, cy: Float, innerRadius: Float, outerRadius: Float) {
        if (outerRadius <= 0f) return
        val inner = if (innerRadius < 0f) 0f else innerRadius
        val segments = segmentsFor(outerRadius)
        ensureRoom(segments * 6)
        val cosines = CircleTable.cosines(segments)
        val sines = CircleTable.sines(segments)
        var prevOuterX = cx + outerRadius
        var prevOuterY = cy
        var prevInnerX = cx + inner
        var prevInnerY = cy
        for (i in 1..segments) {
            val cs = cosines[i]
            val sn = sines[i]
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
         * The batch's size in triangle corners, what [ensureRoom] cuts on; it sizes the vertex buffer too, since the
         * written vertices are never more. A frame that fills it is drawn in two (Christmas with everything on and the
         * sleigh in flight, at a few positions of the street), which is correct, not a failure. Full rain, 240 drops of
         * six corners, takes 1 440.
         */
        const val MAX_VERTICES = 12288
        const val INV_255 = 1f / 255f
        const val DEG_TO_RAD = (Math.PI / 180.0).toFloat()

        /** One segment per this many device pixels of radius: the tessellation/vertex-count trade. */
        const val DEVICE_PIXELS_PER_SEGMENT = 3f
        const val MIN_SEGMENTS = CircleTable.MIN_SEGMENTS
        const val MAX_SEGMENTS = CircleTable.MAX_SEGMENTS

        /** Registry key of the flat-fill white pixel: negative, so it never collides with an `R.drawable` id. */
        const val WHITE_PIXEL_KEY = -1
    }
}
