package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path

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
 * The one cut it does admit is a box on a single sprite blit ([drawSpriteClipped], v5.12), which both
 * backends draw the same way and which reaches nothing else.
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

    /** A rectangle filled with a vertical two-stop gradient. The sky, and the lake band. */
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
     * [radius]. The sun/moon's ambient glow, and its reflection on the lake.
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
     * point: the GPU backend needs them only the first time it draws a sprite at a given detail
     * level (and again after a trim or a lost context), because after the upload the texture knows
     * the sprite's own dimensions. Passing a decoded `Bitmap` here would force a synchronised cache
     * lookup on every blit of every frame to recover a width and a height that had not changed
     * since the first one.
     */
    fun drawSprite(
        resId: Int,
        source: SpriteSource,
        left: Float,
        top: Float,
        tintColor: Int,
        alpha: Int,
        /**
         * Whether the contribution is **summed** into the frame instead of laid over it (v4.30).
         *
         * A person is drawn as fixed art plus one weight mask per colourable region, and the masks
         * have to sum. Two source-over layers split the pixel's coverage between them, and
         * `a + b(1-a)` is not linear -- so once [SpriteDetailLevel] halves each layer separately
         * they no longer recompose, and what is left is a halo at the figure's edge, measured at up
         * to 63 levels of coverage out of 255. A sum is linear: halving and compositing commute,
         * and the edge is exact by construction.
         *
         * The scene's own background is opaque -- the sky is painted under everything -- so
         * "summed" and "laid over with zero alpha" are the same operation here, and both backends
         * can express it without a second blend state. See the two implementations for how.
         */
        additive: Boolean,
    )

    /**
     * [drawSprite], keeping only the part of the sprite inside the box [clipLeft]..[clipRight] x
     * [clipTop]..[clipBottom], given in the same coordinates as [left] and [top] -- the coordinates
     * the current transform maps (v5.12). The rest of the sprite is not drawn at all; the part inside
     * is drawn as [drawSprite] draws it, within the few levels of rounding each backend's cut adds (2 to
     * 3 of 255, measured by `ClippedSpriteAgreementTest`), and a box that holds the whole sprite draws the
     * whole sprite to the bit.
     *
     * **A box on one blit, not a clip.** The interface admits no clip state, for the reason in this
     * interface's own note: a clip the GPU backend could not honour on every primitive would let a call
     * site compile and draw two different pictures. A box handed to one sprite blit is something both
     * backends express exactly, and nothing else is affected by it:
     *
     * - **GL** cuts the sprite's quad to the box in the sprite's own coordinates and the texture
     *   rectangle with it, in proportion ([SpriteClip]) -- a smaller quad from the same atlas, in the
     *   same batch, with no state changed: a cut sprite costs what a whole one does;
     * - **Canvas** intersects its clip with the box for the one `drawBitmap`, inside a `save`/`restore`.
     *
     * The two differ only at the cut edge, by less than a pixel -- `Canvas` clips a box to whole device
     * pixels, GL rasterises the cut edge with its 4x multisampling -- the kind of difference §10 rule 9
     * of `DESIGN_NOTES.md` accepts at the edges of circles and lines, and names for a cut sprite.
     *
     * What it is for: a person walking out of a window, or into one, is drawn whole and at full
     * strength, and only the part of them inside the pane is seen ([WindowWalk]); every layer of the
     * person is cut by the same box, so the summed masks still recompose ([additive]).
     */
    @Suppress("LongParameterList")
    fun drawSpriteClipped(
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
    )
}

/**
 * Supplies a sprite's decoded pixels on demand.
 *
 * Deliberately narrow, and deliberately *pull*-shaped. Backends differ in how often they need the
 * pixels at all: the `Canvas` backend needs them for every blit, the GPU backend needs them once
 * per sprite and detail level, until a trim or a lost context empties the atlas. A push-shaped
 * interface — handing a `Bitmap` to `drawSprite` — would make the more expensive of those two the
 * cost everyone pays.
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
 * The renderers build four shapes: the hill silhouette (a sine ridge over a flat base), a
 * mountain's two faces (a parabolic face over a base), the lake's glitter diamonds, and the
 * sleigh's falling-gift bow triangles.
 *
 * **The GPU backend has two fills, and only one of them needs the star-shaped property** (REN-02).
 * [SceneCanvas.drawShape] fills a fan from vertex 0, which is correct precisely when the polygon is
 * star-shaped about that vertex; [SceneCanvas.drawVerticalGradientShape] tessellates columns down to
 * the base line and needs nothing of the sort. The audit read the sentence that used to be here --
 * "true for all three by construction" -- and correctly called it false: the hill's ridge is two
 * full sine cycles, so a fan from its base-left corner runs above the crest where the wave dips.
 *
 * The hill goes through the gradient path, which is column-tessellated for a different reason of
 * its own (see that method), and since v5.8C it is the only way the hill is drawn. Until then the
 * hill loop also drew a *shadow* with `drawShape(path, shadowPaint)` -- black at alpha 30, 6 px
 * lower -- which reached the fan: on the GPU, where the ridge dips, the fan spilled thin dark
 * slivers above the hill (up to 3.5 px on a 1080x2424 phone, on Beach and Sunset; under a pixel on
 * the BV6600), found by the v5.8B comment audit and photographed on the phone's GPU in v5.8C. The
 * shadow was otherwise never seen on either backend: drawn 6 px below the ridge and then covered
 * by the opaque fill, every pixel of it that could show was the spill. So it was removed rather
 * than moved to the column fill, which would have drawn an invisible polygon at the cost of a
 * visible one. The shapes that still reach the fan are a mountain face, a triangle and the lake's
 * glitter diamond, each star-shaped about its first vertex; the preview's own multi-peaked ridge
 * in `ThemePreview` is typed to `CanvasSceneTarget` and cannot reach a GPU fan at all.
 * `SceneShapeFanContractTest` pins it: the hill is not star-shaped on the themes the audit named,
 * and no `drawShape` in the hill loop takes the hill's shape.
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
     * Only the `Canvas` backend calls this. `rewind()`, not `reset()`: both empty the path, but
     * `reset()` also releases its native storage, so a shape rebuilt per frame re-allocated its
     * native path here until v5.8C (v5.8B comment audit); `rewind()` keeps it for the next build.
     */
    internal fun asPath(): Path {
        if (!pathValid) {
            path.rewind()
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
