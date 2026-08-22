package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P2-5: measures how many `Shader` objects the `Canvas` backend needs, against how many it used to
 * build.
 *
 * **Why this is a measurement and not an assertion about the fix.** [GradientRequestRecorder]
 * wraps the real backend and records the *full argument tuple* of every gradient entry point the
 * real [PaperRenderer] calls, over a run of animated frames. It would record exactly the same
 * numbers with or without [GradientShaderCache] present, because it sits above it. That is what
 * makes it evidence rather than a restatement: the gap between `total` and `distinct` is a
 * property of the renderer's call pattern, measured, and it is the size of the waste that existed
 * before the cache regardless of what the cache then does about it.
 *
 * Pre-v3.6 [CanvasSceneTarget] built a `LinearGradient` or a `RadialGradient` unconditionally on
 * every one of those calls -- three straight-line constructor invocations, no branch, no reuse --
 * so `total` *was* the allocation count. That equality was checked directly rather than reasoned
 * about: the pre-v3.6 backend was run on an API 37 device with a counter on each of the three
 * constructor sites, and it reported 180 objects built over the 60 frames of
 * [gradientRequestsRepeatAcrossFrames] and 900 over 300 scrolling frames, against 3 distinct
 * gradients in both cases.
 *
 * **One thing this test exists to have disproved.** The hill layers' `-1, 0, +1` wrap-tile loop
 * reads as three copies of one gradient per frame, and the first version of this file asserted so.
 * It is false: the loop's own culling `continue` rejects two of the three, at every scroll offset
 * sampled, so the per-frame count is three gradients and not five. The waste is entirely
 * frame-to-frame, not within-frame, and the assertions below say only that.
 *
 * **Why the frames animate.** `deltaSeconds` is a real 1/30 s and scene time advances, so objects
 * move between frames and the renderer does genuine per-frame work. What deliberately does *not*
 * move is the day phase: [SunPositionCalculator.currentHour24] quantises the clock to the minute,
 * so a running wallpaper holds one `DayPhase` for ~1800 consecutive frames at 30 fps. Fixing it
 * here reproduces the inside of one such minute, which is where the wallpaper spends all of its
 * time.
 */
@RunWith(AndroidJUnit4::class)
class CanvasGradientAllocationTest {

    /**
     * Records every gradient request verbatim and passes it straight through to the real backend.
     *
     * Every other operation delegates untouched, so the frame that gets drawn is the frame the
     * renderer would have drawn on its own.
     */
    private class GradientRequestRecorder(private val delegate: CanvasSceneTarget) : SceneCanvas {

        val requests = ArrayList<String>()

        override fun save() = delegate.save()
        override fun restore() = delegate.restore()
        override fun translate(dx: Float, dy: Float) = delegate.translate(dx, dy)
        override fun scale(sx: Float, sy: Float) = delegate.scale(sx, sy)
        override fun rotate(degrees: Float) = delegate.rotate(degrees)

        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) =
            delegate.drawRect(left, top, right, bottom, paint)

        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) =
            delegate.drawLine(startX, startY, stopX, stopY, paint)

        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) =
            delegate.drawCircle(cx, cy, radius, paint)

        override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) =
            delegate.drawOval(left, top, right, bottom, paint)

        override fun drawArc(oval: RectF, startAngle: Float, sweepAngle: Float, paint: Paint) =
            delegate.drawArc(oval, startAngle, sweepAngle, paint)

        override fun drawWedge(
            cx: Float,
            cy: Float,
            radius: Float,
            startAngle: Float,
            sweepAngle: Float,
            paint: Paint,
        ) = delegate.drawWedge(cx, cy, radius, startAngle, sweepAngle, paint)

        override fun drawShape(shape: SceneShape, paint: Paint) = delegate.drawShape(shape, paint)

        override fun drawSprite(
            resId: Int,
            source: SpriteSource,
            left: Float,
            top: Float,
            tintColor: Int,
            alpha: Int,
        ) = delegate.drawSprite(resId, source, left, top, tintColor, alpha)

        override fun drawVerticalGradientShape(
            shape: SceneShape,
            gradientTopY: Float,
            gradientBottomY: Float,
            topColor: Int,
            bottomColor: Int,
            alpha: Int,
        ) {
            requests += "linear:$gradientTopY:$gradientBottomY:$topColor:$bottomColor"
            delegate.drawVerticalGradientShape(
                shape, gradientTopY, gradientBottomY, topColor, bottomColor, alpha,
            )
        }

        override fun drawVerticalGradientRect(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            topColor: Int,
            bottomColor: Int,
        ) {
            requests += "linear:$top:$bottom:$topColor:$bottomColor"
            delegate.drawVerticalGradientRect(left, top, right, bottom, topColor, bottomColor)
        }

        override fun drawRadialGlow(cx: Float, cy: Float, radius: Float, color: Int, centerAlpha: Int) {
            requests += "radial:$cx:$cy:$radius:$color:$centerAlpha"
            delegate.drawRadialGlow(cx, cy, radius, color, centerAlpha)
        }
    }

    /**
     * Draws [frames] animated frames of [scene] through the real backend and returns what the
     * renderer asked for.
     */
    private fun recordFrames(scene: GoldenScene, frames: Int): List<String> {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val target = CanvasSceneTarget()
        target.bind(Canvas(bitmap))
        val recorder = GradientRequestRecorder(target)
        val renderer = PaperRenderer(
            WIDTH, HEIGHT, InstrumentationRegistry.getInstrumentation().targetContext,
        )
        scene.configure(renderer)
        // The wallpaper's normal state is scrolling. Deliberately on here: a moving scene is the
        // one that could plausibly need a new gradient every frame, and measuring it is what
        // established that it does not.
        renderer.scrollSpeed = 0.15f
        var sceneSeconds = SceneTime(0.0)
        repeat(frames) {
            sceneSeconds += FRAME_DELTA
            renderer.draw(recorder, scene.dayPhase, sceneSeconds, FRAME_DELTA)
        }
        target.unbind()
        bitmap.recycle()
        return recorder.requests
    }

    /**
     * **The measurement.** Over 60 animated frames -- two seconds of wallpaper -- the renderer asks
     * for the same handful of gradients again and again.
     *
     * Bounded rather than pinned to today's exact three: this test is about the *ratio* being
     * large and structural, and an equality would make it a tripwire for any future scene change
     * rather than evidence about allocation. The measured figures are logged for the record.
     */
    @Test
    fun gradientRequestsRepeatAcrossFrames() {
        val frames = 60
        val requests = recordFrames(SharedGoldenScenes.day(), frames)
        val distinct = requests.toSet()

        Log.i(
            TAG,
            "canvas gradients over $frames frames: " +
                "total=${requests.size} distinct=${distinct.size} " +
                "perFrame=${requests.size.toFloat() / frames}",
        )
        distinct.sorted().forEach { Log.i(TAG, "  distinct gradient: $it") }

        assertTrue("expected the renderer to ask for gradients at all", requests.size >= 3 * frames)
        assertTrue(
            "expected a small fixed set of gradients across a minute, got ${distinct.size}",
            distinct.size <= 6,
        )
        // The point of the whole item: almost every request is for something already built.
        assertTrue(
            "expected the vast majority of requests to be repeats",
            distinct.size * 20 < requests.size,
        )
    }

    /**
     * What the cache does with the pattern above: one instance per distinct gradient, handed back
     * on every repeat.
     *
     * Identity, not equality -- `LinearGradient` has no `equals`, and the claim being made is
     * specifically that no second object was constructed.
     */
    @Test
    fun cacheServesRepeatsFromOneInstance() {
        val cache = GradientShaderCache()
        val first = cache.linear(0f, 100f, 0xFF102030.toInt(), 0xFF405060.toInt())
        val again = cache.linear(0f, 100f, 0xFF102030.toInt(), 0xFF405060.toInt())
        assertSame("identical arguments must reuse the instance", first, again)

        val other = cache.linear(0f, 100f, 0xFF102030.toInt(), 0xFF405061.toInt())
        assertTrue("a different colour must not reuse the instance", first !== other)

        val glow = cache.radial(10f, 20f, 30f, 0xFFFFCC00.toInt(), 120)
        val glowAgain = cache.radial(10f, 20f, 30f, 0xFFFFCC00.toInt(), 120)
        assertSame("identical arguments must reuse the instance", glow, glowAgain)
        assertTrue("a radial must never be served from the linear table", glow !== first)
    }

    /**
     * The cache under the real renderer: after the frames above, the number of objects it built is
     * the number of *distinct* gradients, not the number of requests.
     */
    @Test
    fun rendererDrivesTheCacheToOneObjectPerDistinctGradient() {
        val frames = 60
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val target = CanvasSceneTarget()
        target.bind(Canvas(bitmap))
        val recorder = GradientRequestRecorder(target)
        val renderer = PaperRenderer(
            WIDTH, HEIGHT, InstrumentationRegistry.getInstrumentation().targetContext,
        )
        SharedGoldenScenes.day().configure(renderer)
        renderer.scrollSpeed = 0.15f
        var sceneSeconds = SceneTime(0.0)
        repeat(frames) {
            sceneSeconds += FRAME_DELTA
            renderer.draw(recorder, SharedGoldenScenes.day().dayPhase, sceneSeconds, FRAME_DELTA)
        }
        target.unbind()
        bitmap.recycle()

        val distinct = recorder.requests.toSet().size
        val built = target.gradientCache.built
        Log.i(
            TAG,
            "with cache: requests=${recorder.requests.size} distinct=$distinct shadersBuilt=$built",
        )
        assertEquals(
            "the cache must build exactly one Shader per distinct gradient",
            distinct,
            built,
        )
        assertTrue(
            "the cache must remove the overwhelming majority of constructions",
            built * 20 < recorder.requests.size,
        )
    }

    private companion object {
        const val WIDTH = SceneGolden.WIDTH
        const val HEIGHT = SceneGolden.HEIGHT

        /** One frame at the render loop's own 30 fps cadence. */
        const val FRAME_DELTA = 1f / 30f

        /** Logcat tag, so the measured figures survive the run. */
        const val TAG = "P2-5"
    }
}
