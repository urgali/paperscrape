package com.paperscrape.livewallpaper.engine

import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.R
import com.paperscrape.livewallpaper.weather.LiveWeatherSnapshot
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * **A leaping dolphin is painted behind the wave and the boat that are nearer than it** (v5.9B,
 * inventory row I-02, item 84).
 *
 * The frame is a real one, found by v5.9B's probe (`consegna_v5_9b/registri/i02_sonda/`): **Beach at
 * its factory settings, in a thunderstorm, on the BV6600's own 720x1440 surface, 80.37 s into the
 * scene.** A dolphin at the top of a small leap stands in front of a breaker and of a sailboat whose
 * waterlines are both lower on the screen -- nearer -- than its belly. Until v5.9B the three kinds were
 * keyed by three different points and the dolphin's was 16.9 px off the other two, so v5.9A painted it
 * last: across the wave and across the sail. `LakeLanes.visibleWaterline` keys all three where their
 * drawings meet the water.
 *
 * `LakeLanesTest` holds the arithmetic on the JVM; this holds the renderer to it, because the order is
 * decided inside `PaperRenderer`, which the JVM cannot run (B5). It reads the real draw sequence
 * through a recording `SceneCanvas` -- no pixels -- after walking the scene clock up to the frame at
 * the loop's own 30 fps, which is what gives every wave slot the membership it has on screen.
 */
@RunWith(AndroidJUnit4::class)
class LakeWaterlineOrderTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aLeapingDolphinGoesBehindTheNearerWaveAndTheNearerBoat() {
        val width = 720
        val height = 1440
        val renderer = PaperRenderer(width, height, context)
        renderer.theme = ThemeCatalog.byId("beach")
        renderer.sceneCustomization = defaultCustomizationFor("beach")
        renderer.liveWeatherOverride = LiveWeatherSnapshot(
            precipitationType = PrecipitationType.RAIN,
            precipitationIntensity = 1f,
            cloudCoverFraction = 1f,
            isThunderstorm = true,
            fetchedAtMillis = 0L,
        )
        renderer.homeScreenOffset = 0f
        renderer.swipeScrollEnabled = false
        renderer.scrollSpeed = 0f
        renderer.parallaxStrength = 1f
        renderer.lightningStrikesEnabled = false
        val dayPhase = SunPositionCalculator.compute(13f)

        val walk = Recorder()
        for (step in 0 until FRAME) renderer.draw(walk, dayPhase, SceneTime(step / 30.0), 0f)
        val frame = Recorder()
        renderer.draw(frame, dayPhase, SceneTime(FRAME / 30.0), 0f)

        val scale = SceneSpace.sceneScale(height.toFloat())
        val dolphinScale = SceneSpace.DOLPHIN_BASE_SCALE * scale
        val belly = -PaperRenderer.DOLPHIN_ORIGIN_Y_UNITS * dolphinScale
        val hullDrop = PaperRenderer.SAILBOAT_HULL_WATERLINE_UNITS * SceneSpace.SAILBOAT_BASE_SCALE * scale

        val dolphins = frame.blits.withIndex().filter { it.value.res == R.drawable.dolphin_body }
        assertTrue("no dolphin is in the air in this frame -- the scene has moved, re-find the frame with the probe", dolphins.size == 1)
        val (dIndex, dolphin) = dolphins.single()
        val dolphinWaterline = dolphin.y + belly

        // The breaker the dolphin stands in front of: its base is nearer than the dolphin's belly, by
        // less than the 16.9 px the old keys disagreed by, and it spans the animal.
        val wave = frame.blits.withIndex().filter { it.value.res == R.drawable.wave_tube_body }
            .firstOrNull { abs(it.value.x - dolphin.x) < 60f && it.value.y > dolphinWaterline && it.value.y < dolphinWaterline + (hullDrop - belly) }
        assertTrue("no wave in this frame is in the band the old keys got wrong -- re-find the frame with the probe", wave != null)
        assertTrue(
            "the wave's base (${wave!!.value.y}) is nearer than the leaping dolphin's belly ($dolphinWaterline), " +
                "so the wave must be painted after the dolphin; it was painted at ${wave.index} and the dolphin at $dIndex",
            wave.index > dIndex,
        )

        // And the boat whose sail it crosses: the hull meets the water below the dolphin's belly.
        val boat = frame.blits.withIndex().filter { it.value.res == R.drawable.sailboat_hull }
            .firstOrNull { abs(it.value.x - dolphin.x) < 45f && it.value.y + hullDrop > dolphinWaterline && it.value.y < dolphin.y }
        assertTrue("no boat in this frame stands behind the dolphin by placement and before it by hull", boat != null)
        assertTrue(
            "the boat's hull meets the water at ${boat!!.value.y + hullDrop}, nearer than the dolphin's belly " +
                "($dolphinWaterline), so the boat must be painted after the dolphin; it was painted at ${boat.index} " +
                "and the dolphin at $dIndex",
            boat.index > dIndex,
        )
    }

    private class Blit(val res: Int, val x: Float, val y: Float)

    /** Records each sprite with the point its local origin lands on; draws nothing. */
    private class Recorder : SceneCanvas {
        val blits = mutableListOf<Blit>()
        private var m = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f) // a, b, c, d, tx, ty
        private val stack = ArrayDeque<FloatArray>()
        override fun save() { stack.addLast(m.copyOf()) }
        override fun restore() { m = stack.removeLast() }
        override fun translate(dx: Float, dy: Float) {
            m[4] += m[0] * dx + m[2] * dy
            m[5] += m[1] * dx + m[3] * dy
        }
        override fun scale(sx: Float, sy: Float) {
            m[0] *= sx; m[1] *= sx; m[2] *= sy; m[3] *= sy
        }
        override fun rotate(degrees: Float) {
            val r = Math.toRadians(degrees.toDouble())
            val cs = kotlin.math.cos(r).toFloat(); val sn = kotlin.math.sin(r).toFloat()
            val a = m[0] * cs + m[2] * sn; val b = m[1] * cs + m[3] * sn
            val c = -m[0] * sn + m[2] * cs; val d = -m[1] * sn + m[3] * cs
            m[0] = a; m[1] = b; m[2] = c; m[3] = d
        }
        // A sprite cut to a box (v5.12, a person walking out of a window) is recorded as the blit it is.
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
        ) = drawSprite(resId, source, left, top, tintColor, alpha, additive)

        override fun drawSprite(resId: Int, source: SpriteSource, left: Float, top: Float, tintColor: Int, alpha: Int, additive: Boolean) {
            blits += Blit(resId, m[4], m[5])
        }
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) = Unit
        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) = Unit
        override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
        override fun drawWedge(cx: Float, cy: Float, radius: Float, startAngle: Float, sweepAngle: Float, paint: Paint) = Unit
        override fun drawShape(shape: SceneShape, paint: Paint) = Unit
        override fun drawVerticalGradientShape(shape: SceneShape, gradientTopY: Float, gradientBottomY: Float, topColor: Int, bottomColor: Int, alpha: Int) = Unit
        override fun drawVerticalGradientRect(left: Float, top: Float, right: Float, bottom: Float, topColor: Int, bottomColor: Int) = Unit
        override fun drawRadialGlow(cx: Float, cy: Float, radius: Float, color: Int, centerAlpha: Int) = Unit
    }

    private companion object {
        /** 80.367 s at 30 fps: the probe's frame (`registri/i02_sonda/report.tsv`, beach-storm). */
        const val FRAME = 2411
    }
}
