package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **What the water actually costs per frame, counted rather than estimated.**
 *
 * The v4.25 band was three screen-wide copies of: one fill, six drifting tone bands, four ripple
 * lines and five sparkles — **16 primitives a copy, 48 a frame**, at every scroll position,
 * arithmetic anyone can check against the removed `drawLakeBand`.
 *
 * v4.26's mirror is one gradient rect and five sparkles a copy; the waterline, the reflected glow
 * and the light's path are drawn once each outside the tile loop because none of them varies along
 * the scroll; and the copy that has scrolled entirely off the left edge is skipped. This scene sits
 * at a wrap of exactly zero, which is the **worst** case for the cull — all three copies are kept
 * there, because a sparkle at the very end of the left one still reaches a few pixels onto the
 * screen. At any other scroll position one copy fewer is drawn.
 *
 * This measures the difference on the real renderer through the real `SceneCanvas`, by counting
 * every primitive with the water on and with it off, on the same scene at the same instant. The
 * difference is the water's own cost and nothing else's.
 */
@RunWith(AndroidJUnit4::class)
class LakeDrawCallTest {

    /**
     * Counts primitives and forwards everything. Kotlin's delegation carries the transform stack
     * and the sprite plumbing through untouched, so the count is of *drawing* and not of bookkeeping.
     */
    private class Counting(private val delegate: SceneCanvas) : SceneCanvas by delegate {
        var calls = 0
            private set

        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
            calls++; delegate.drawRect(left, top, right, bottom, paint)
        }

        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
            calls++; delegate.drawLine(startX, startY, stopX, stopY, paint)
        }

        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
            calls++; delegate.drawCircle(cx, cy, radius, paint)
        }

        override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
            calls++; delegate.drawOval(left, top, right, bottom, paint)
        }

        override fun drawArc(oval: RectF, startAngle: Float, sweepAngle: Float, paint: Paint) {
            calls++; delegate.drawArc(oval, startAngle, sweepAngle, paint)
        }

        override fun drawWedge(cx: Float, cy: Float, radius: Float, startAngle: Float, sweepAngle: Float, paint: Paint) {
            calls++; delegate.drawWedge(cx, cy, radius, startAngle, sweepAngle, paint)
        }

        override fun drawShape(shape: SceneShape, paint: Paint) {
            calls++; delegate.drawShape(shape, paint)
        }

        override fun drawVerticalGradientShape(
            shape: SceneShape,
            gradientTopY: Float,
            gradientBottomY: Float,
            topColor: Int,
            bottomColor: Int,
            alpha: Int,
        ) {
            calls++
            delegate.drawVerticalGradientShape(shape, gradientTopY, gradientBottomY, topColor, bottomColor, alpha)
        }

        override fun drawVerticalGradientRect(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            topColor: Int,
            bottomColor: Int,
        ) {
            calls++; delegate.drawVerticalGradientRect(left, top, right, bottom, topColor, bottomColor)
        }

        override fun drawRadialGlow(cx: Float, cy: Float, radius: Float, color: Int, centerAlpha: Int) {
            calls++; delegate.drawRadialGlow(cx, cy, radius, color, centerAlpha)
        }

        override fun drawSprite(
            resId: Int,
            source: SpriteSource,
            left: Float,
            top: Float,
            tintColor: Int,
            alpha: Int,
            additive: Boolean,
        ) {
            calls++; delegate.drawSprite(resId, source, left, top, tintColor, alpha, additive)
        }
    }

    private fun countFor(scene: GoldenScene): Int {
        val bitmap = Bitmap.createBitmap(SceneGolden.WIDTH, SceneGolden.HEIGHT, Bitmap.Config.ARGB_8888)
        val target = CanvasSceneTarget()
        target.bind(Canvas(bitmap))
        val counting = Counting(target)
        val renderer = PaperRenderer(
            SceneGolden.WIDTH, SceneGolden.HEIGHT,
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
        scene.configure(renderer)
        renderer.draw(counting, scene.dayPhase, SceneTime(scene.sceneSeconds), 0f)
        target.unbind()
        bitmap.recycle()
        return counting.calls
    }

    private fun water(visible: Boolean) = GoldenScene(
        name = "lake-cost",
        dayPhase = GoldenScene.day(),
        customise = {
            it.copy(
                lake = it.lake.copy(
                    visible = visible,
                    height = 0.8f,
                    // Off in both arms: the animals and the boats are their own cost, and what is
                    // being measured here is the surface.
                    sailboatsVisible = false,
                    dolphinsVisible = false,
                ),
            )
        },
    )

    /**
     * The water's own per-frame primitive count, measured, and logged so the report can quote it.
     *
     * The assertion is deliberately loose in the direction that matters: it fails if the surface
     * ever costs as much as the band it replaced. Pinning the exact number would make this a
     * change-detector rather than a claim — the number that matters is in the log line and in the
     * report, and it moves legitimately with the scroll position.
     */
    @Test
    fun theMirrorCostsLessPerFrameThanTheBandItReplaced() {
        val with = countFor(water(true))
        val without = countFor(water(false))
        val cost = with - without
        android.util.Log.i(
            "LAKECOST",
            "water surface: $cost primitives per frame (frame with=$with without=$without); " +
                "the v4.25 band was 48",
        )
        assertTrue("the water has to cost something: $cost", cost > 0)
        assertTrue(
            "the mirror costs $cost primitives per frame against the 48 the v4.25 band cost. If " +
                "this has stopped being a saving, the tile cull or the un-tiled waterline has " +
                "been undone",
            cost < 48,
        )
    }
}
