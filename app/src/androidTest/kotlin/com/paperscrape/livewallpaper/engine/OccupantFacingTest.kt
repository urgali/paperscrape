package com.paperscrape.livewallpaper.engine

import android.graphics.BitmapFactory
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin

/**
 * **Every occupant looks the way their vehicle is going** (v5.9B, inventory row I-42).
 *
 * `BACKLOG_v4_25.md` item 62: the seated artwork is drawn three-quarter with the hair toward -x,
 * so the face looks toward +x of its canvas, and +x in a vehicle's local frame is its rear. Every
 * occupant of every car rode facing the boot, in both directions equally, until v4.25 mirrored the
 * bust (`SceneObjectRenderer.drawSeatedOccupant`, `scale(-scale, scale)`). The item recorded what
 * guarded that line afterwards: one pillar-clearance test that failed by accident, and **nothing
 * that asserted which way a person is looking**. On a cabin with symmetric clearance the reversal
 * would have been invisible to every test and every golden.
 *
 * This asserts it end to end, from the three things that decide it and nothing else:
 *
 * - **which way the vehicle travels**, read off two frames of the real renderer a second apart;
 * - **which way the artwork faces**, read off the adult seated heads' own hair masks -- the hair
 *   mass behind the eye axis is the back of the head;
 * - **which way the bust is blitted**, read off the transform in force when the renderer draws it,
 *   recorded through a `SceneCanvas` that keeps the matrix.
 *
 * Reversing v4.25's mirror turns every one of the eight vehicles below red.
 */
@RunWith(AndroidJUnit4::class)
class OccupantFacingTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everyOccupantLooksTheWayItsVehicleIsGoing() {
        val artFacing = artworkFacing()
        for (type in listOf(CarType.PLAIN, CarType.TAXI, CarType.POLICE, CarType.FIRE_TRUCK)) {
            val travels = mutableListOf<Float>()
            for (reverse in listOf(false, true)) {
                val seen = observe(type, reverse)
                val label = "$type, ${if (reverse) "reverse" else "forward"}"
                assertTrue("$label: the vehicle did not move, so its direction cannot be read", seen.travel != 0f)
                travels += seen.travel
                assertEquals("$label: the amber lamp is not at the front of the way it travels", seen.travel, seen.lampsFront)
                assertTrue("$label: no occupant was drawn", seen.bustScaleSigns.isNotEmpty())
                for (s in seen.bustScaleSigns) {
                    assertEquals("$label: an occupant looks the other way from the one the vehicle is going", seen.travel, s * artFacing)
                }
            }
            // The renderer draws the direction it is asked for. `VehicleOccupantAbCapture` carried
            // this as its one assertion (item 8: its harness had hardcoded `reverse = true`) until
            // v5.9C removed the capture; here it is read off the real renderer's own frames.
            assertEquals("$type: forward and reverse travel the same way, so the flag is ignored", -travels[0], travels[1])
        }
    }

    /**
     * +1 when the seated artwork's face looks toward +x of its canvas, -1 toward -x: the side of the
     * eye axis opposite the hair mass, measured on the two adults' summer heads, where the hair is
     * unambiguous (the children's caps and the winter hats sit nearly on the axis).
     */
    private fun artworkFacing(): Float {
        val eyeAxisPx = SceneObjectRenderer.HEAD_CAR_ANCHOR_X_UNITS * SpriteBlitter.SPRITE_PIXELS_PER_UNIT
        val sides = listOf(R.drawable.person_man_summer_head_car_mh, R.drawable.person_woman_summer_head_car_mh).map { res ->
            val options = BitmapFactory.Options().apply { inScaled = false }
            val bitmap = BitmapFactory.decodeResource(context.resources, res, options)
            var weight = 0.0
            var sum = 0.0
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                val a = (bitmap.getPixel(x, y) ushr 24).toDouble()
                weight += a; sum += a * x
            }
            val hairCentre = sum / weight
            assertTrue("the hair mass is on the eye axis ($hairCentre px against $eyeAxisPx), so it says nothing", kotlin.math.abs(hairCentre - eyeAxisPx) > 5.0)
            -sign(hairCentre - eyeAxisPx).toFloat()
        }
        assertEquals("the two adults are drawn facing different ways", sides[0], sides[1])
        return sides[0]
    }

    private class Seen(val travel: Float, val lampsFront: Float, val bustScaleSigns: List<Float>)

    private fun observe(type: CarType, reverse: Boolean): Seen {
        val defaults = defaultCustomizationFor("sunset")
        val customization = defaults.copy(
            cars = defaults.cars.copy(visible = true, density = 1f),
            people = defaults.people.copy(visible = false),
        )
        val layout = SceneObjectLayout(
            staticObjects = emptyList(),
            cars = listOf(
                CarObject(
                    laneYFraction = SceneSpace.ROAD_LANE_NEAR_Y_FRACTION,
                    speedFraction = 0.05f,
                    startDelaySeconds = -0.5f,
                    color = 0xFFB4513C.toInt(),
                    reverse = reverse,
                    type = type,
                ),
            ),
        )
        val renderer = SceneObjectRenderer(layout, customization, context, "sunset")
        val first = MatrixRecorder()
        renderer.draw(first, GroundGeometry(0f, 1080f), dayBlend = 1f, elapsedSeconds = SceneTime(120.0), screenWidth = 1080f, screenHeight = 2400f)
        renderer.update(1f)
        val second = MatrixRecorder()
        renderer.draw(second, GroundGeometry(0f, 1080f), dayBlend = 1f, elapsedSeconds = SceneTime(121.0), screenWidth = 1080f, screenHeight = 2400f)
        val frontBefore = first.blits.first { it.res == R.drawable.car_lamp_front }
        val frontAfter = second.blits.first { it.res == R.drawable.car_lamp_front }
        val rear = first.blits.first { it.res == R.drawable.car_lamp_rear }
        return Seen(
            travel = sign(frontAfter.x - frontBefore.x),
            lampsFront = sign(frontBefore.x - rear.x),
            bustScaleSigns = first.blits.filter { it.res in OCCUPANT_LAYERS }.map { sign(it.scaleX) },
        )
    }

    private class Blit(val res: Int, val x: Float, val scaleX: Float)

    /** Keeps the affine transform the renderer builds, and records each sprite with it. */
    private class MatrixRecorder : SceneCanvas {
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
            val cs = cos(r).toFloat(); val sn = sin(r).toFloat()
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
            blits += Blit(resId, m[0] * left + m[2] * top + m[4], m[0])
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
        /** Every drawable a seated occupant is made of, read off the engine's own table. */
        val OCCUPANT_LAYERS: Set<Int> = PeopleLayerTable.CAR
            .flatMap { season -> season.flatMap { shape -> shape.filter { it != 0 } } }
            .toSet()
    }
}
