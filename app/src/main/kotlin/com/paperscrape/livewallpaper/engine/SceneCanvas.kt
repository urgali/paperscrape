package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * The drawing surface the scene renderers draw onto, independent of which backend actually
 * rasterises it.
 *
 * Two backends implement it: [CanvasSceneTarget], a direct delegation to `android.graphics.Canvas`,
 * and [GlSceneTarget], which turns the same calls into GPU geometry. The scene renderers know only
 * this interface, so the *what* of the scene (geometry, candidates, depth, tiling, colours) has one
 * implementation and only the *how* of rasterisation has two.
 *
 * The operation set is deliberately the exact set the renderers already used, no wider: an interface
 * that admitted arbitrary `Path`s, clips or `Xfermode`s would be one the GPU backend could not
 * honour, and a call site could then compile while producing a different picture on each backend.
 *
 * [Paint] is passed through rather than being decomposed into arguments. Reading `color`, `alpha`,
 * `style`, `strokeWidth` and `strokeCap` off a paint allocates nothing, and it keeps the renderers'
 * existing per-object paint bookkeeping untouched. Paint *shaders* are the one exception: they
 * cannot be read back, so the three gradient effects have their own explicit entry points
 * ([drawVerticalGradientRect], [drawVerticalGradientShape], [drawRadialGlow]) that carry the stops
 * as arguments.
 */
interface SceneCanvas {

    // --- Transform stack ---------------------------------------------------------------------

    fun save()

    fun restore()

    fun translate(dx: Float, dy: Float)

    fun scale(sx: Float, sy: Float)

    fun rotate(degrees: Float)

    // --- Primitives --------------------------------------------------------------------------

    fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint)

    fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint)

    fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint)

    fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint)

    /** A stroked arc, matching `Canvas.drawArc(oval, start, sweep, useCenter = false, paint)`. */
    fun drawArc(oval: RectF, startAngle: Float, sweepAngle: Float, paint: Paint)

    /**
     * A filled circular sector: the pie slice between [startAngle] and `startAngle + sweepAngle`.
     *
     * Replaces the `Path.moveTo(0,0) + arcTo(...) + close()` the parasol used. Expressing it as its
     * own operation rather than as a generic path keeps [SceneShape] free of curve support, which
     * neither backend would then implement the same way.
     */
    fun drawWedge(cx: Float, cy: Float, radius: Float, startAngle: Float, sweepAngle: Float, paint: Paint)

    /** Fills [shape] flat. See [SceneShape] for the star-shaped requirement the GPU backend relies on. */
    fun drawShape(shape: SceneShape, paint: Paint)

    /**
     * Fills [shape] with a vertical two-stop gradient running from [topColor] at [gradientTopY] to
     * [bottomColor] at [gradientBottomY], clamped outside that band.
     *
     * The hill layers' highlight. Passed as arguments because a `LinearGradient` set on a `Paint`
     * cannot be read back out of it.
     *
     * [shape] must be a *terrain* shape: a top polyline whose first and last vertices sit on a
     * common horizontal base line, which is what the hill ridge is. The GPU backend fills it as
     * vertical columns split at [gradientBottomY] so the ramp genuinely stops there, and that
     * construction needs the base line to exist.
     */
    fun drawVerticalGradientShape(
        shape: SceneShape,
        gradientTopY: Float,
        gradientBottomY: Float,
        topColor: Int,
        bottomColor: Int,
        alpha: Int,
    )

    /** A rectangle filled with a vertical two-stop gradient. The sky. */
    fun drawVerticalGradientRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        topColor: Int,
        bottomColor: Int,
    )

    /**
     * A radial falloff disc: [color] at [centerAlpha] in the middle, the same colour at alpha 0 at
     * [radius]. The sun/moon's ambient glow.
     */
    fun drawRadialGlow(cx: Float, cy: Float, radius: Float, color: Int, centerAlpha: Int)

    // --- Sprites -----------------------------------------------------------------------------

    /**
     * Blits the sprite [resId] with its own pixel (0,0) at [left]/[top], tinting it `MULTIPLY` by
     * [tintColor] at [alpha].
     *
     * A `tintColor` of white (`0xFFFFFFFF`) is the `MULTIPLY` identity and therefore means "the
     * sprite's own baked-in colours, unchanged".
     *
     * The pixels are fetched through [source] rather than passed in, and that indirection is the
     * point: the GPU backend needs them only the first time it sees a sprite, because after the
     * upload the texture knows the sprite's own dimensions. Passing a decoded `Bitmap` here would
     * force a synchronised cache lookup on every blit of every frame to recover a width and a
     * height that had not changed since the first one.
     */
    fun drawSprite(
        resId: Int,
        source: SpriteSource,
        left: Float,
        top: Float,
        tintColor: Int,
        alpha: Int,
    )
}

/**
 * Supplies a sprite's decoded pixels on demand.
 *
 * Deliberately narrow, and deliberately *pull*-shaped. Backends differ in how often they need the
 * pixels at all: the `Canvas` backend needs them for every blit, the GPU backend needs them once
 * per sprite for the life of the GL context. A push-shaped interface — handing a `Bitmap` to
 * `drawSprite` — would make the more expensive of those two the cost everyone pays.
 */
interface SpriteSource {

    /** The decoded pixels for [resId], decoding them if necessary. Never null. */
    fun bitmapFor(resId: Int): Bitmap

    /**
     * Signals that a backend has taken a durable copy of [resId] and no longer needs the decoded
     * pixels, so the source may release them.
     *
     * Advisory. A source is free to ignore it, and a backend that calls it must still be able to
     * ask for the pixels again.
     */
    fun onSpriteUploaded(resId: Int)
}

/**
 * A closed polygon, built the way a `Path` is but retaining its vertices.
 *
 * The renderers build three shapes: the hill silhouette (a sine ridge over a flat base), a
 * mountain's two faces (a parabolic face over a base), and the sleigh's falling-gift bow triangles.
 * The GPU backend fills a shape as a triangle fan from its first vertex, which is correct precisely
 * when the polygon is star-shaped about that vertex — true for all three by construction, and the
 * reason this class does not accept arbitrary geometry.
 *
 * Vertices accumulate into a growable `FloatArray` that is reused across [reset] calls, so a shape
 * rebuilt every frame (the mountains) allocates only until it has reached its high-water mark.
 */
class SceneShape(initialCapacity: Int = 96) {

    private var xs = FloatArray(initialCapacity)
    private var ys = FloatArray(initialCapacity)

    var pointCount: Int = 0
        private set

    /** Lazily built and cached for the `Canvas` backend; invalidated whenever the vertices change. */
    private val path = Path()
    private var pathValid = false

    fun reset() {
        pointCount = 0
        pathValid = false
    }

    fun moveTo(x: Float, y: Float) {
        reset()
        addPoint(x, y)
    }

    fun lineTo(x: Float, y: Float) {
        addPoint(x, y)
    }

    /**
     * Closes the polygon.
     *
     * Nothing is stored: both backends treat the vertex list as an implicitly closed loop. It exists
     * so call sites read the way the `Path` code they replaced did.
     */
    fun close() {
        pathValid = false
    }

    fun xAt(index: Int): Float = xs[index]

    fun yAt(index: Int): Float = ys[index]

    private fun addPoint(x: Float, y: Float) {
        if (pointCount == xs.size) grow()
        xs[pointCount] = x
        ys[pointCount] = y
        pointCount++
        pathValid = false
    }

    private fun grow() {
        xs = xs.copyOf(xs.size * 2)
        ys = ys.copyOf(ys.size * 2)
    }

    /**
     * The equivalent `Path`, built once per mutation.
     *
     * Only the `Canvas` backend calls this. `Path.reset()` keeps the object's allocation, so a shape
     * rebuilt per frame still allocates nothing here after the first build.
     */
    internal fun asPath(): Path {
        if (!pathValid) {
            path.reset()
            if (pointCount > 0) {
                path.moveTo(xs[0], ys[0])
                for (i in 1 until pointCount) path.lineTo(xs[i], ys[i])
                path.close()
            }
            pathValid = true
        }
        return path
    }
}
