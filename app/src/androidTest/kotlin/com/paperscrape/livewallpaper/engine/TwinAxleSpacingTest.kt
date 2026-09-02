package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * The fire engine's twin rear axle, measured on the rendered PNG.
 *
 * rc1 shipped the pair 8.5 units apart on 20-unit wheels -- a 40% overlap, one visible wheel and
 * a crescent -- and the JVM test of the day *asserted* that overlap ("they must overlap into a
 * bogie"), which is why nothing failed. The rc2 criterion is a real tandem's geometry, and this
 * test measures it the way the criterion states it: off the drawn circles, not the constants.
 * The three wheels are found as dark tyre blobs on the contact band; the rear pair is the two
 * whose centres are nearest each other, and their spacing and the gap between the tyres are read
 * from the pixels.
 */
@RunWith(AndroidJUnit4::class)
class TwinAxleSpacingTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theRearPairIsSpacedLikeATandemWithDaylightBetweenTheTyres() {
        val lane = SceneSpace.ROAD_LANE_NEAR_Y_FRACTION
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val target = CanvasSceneTarget()
        target.bind(Canvas(bitmap))
        val defaults = defaultCustomizationFor("sunset")
        val customization = defaults.copy(
            cars = defaults.cars.copy(visible = true, density = 1f),
            people = defaults.people.copy(visible = false),
        )
        val layout = SceneObjectLayout(
            staticObjects = emptyList(),
            cars = listOf(
                CarObject(
                    laneYFraction = lane,
                    speedFraction = 0f,
                    startDelaySeconds = -0.5f,
                    color = 0xFFB4513C.toInt(),
                    reverse = true,
                    type = CarType.FIRE_TRUCK,
                ),
            ),
        )
        val renderer = SceneObjectRenderer(layout, customization, context, "sunset")
        renderer.draw(
            target, GroundGeometry(0f, WIDTH.toFloat()), dayBlend = 1f,
            elapsedSeconds = SceneTime(120.0), screenWidth = WIDTH.toFloat(), screenHeight = HEIGHT.toFloat(),
        )
        target.unbind()

        // The tyres: dark 0xFF2B2B2B circles standing on the ground line. The hub ring is a
        // grey stroke through the middle of each wheel, so the wheel centres are found on a low
        // row the ring does not reach, and each wheel's true drawn diameter is then measured
        // down its own centre column, bridging the ring's few grey pixels.
        val pixels = IntArray(WIDTH * HEIGHT)
        bitmap.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        fun isTyre(p: Int) = abs(((p shr 16) and 0xFF) - 0x2B) <= 8 &&
            abs(((p shr 8) and 0xFF) - 0x2B) <= 8 && abs((p and 0xFF) - 0x2B) <= 8
        val groundY = (lane * HEIGHT).toInt()
        val unitPx = SceneSpace.FIRE_TRUCK_BASE_SCALE *
            SceneSpace.perspectiveScaleAt(lane) * SceneSpace.sceneScale(HEIGHT.toFloat())
        val radiusPx = SceneObjectRenderer.FIRE_TRUCK_WHEEL_RADIUS_UNITS * unitPx
        val lowRow = groundY - 3

        val spans = mutableListOf<Pair<Int, Int>>()
        var runStart = -1
        for (x in 0 until WIDTH) {
            val tyre = isTyre(pixels[lowRow * WIDTH + x])
            if (tyre && runStart < 0) runStart = x
            if (!tyre && runStart >= 0) {
                if (x - runStart >= radiusPx * 0.5f) spans.add(runStart to x - 1)
                runStart = -1
            }
        }
        assertEquals("three separate tyres on the contact band, found ${spans.size}", 3, spans.size)

        val centres = spans.map { (a, b) -> (a + b) / 2f }.sorted()
        // True drawn diameter, down each wheel's own centre column, bridging the hub ring.
        val diameters = centres.map { cx ->
            var top = -1
            var bottom = -1
            var gap = 0
            for (y in (groundY - (3 * radiusPx).toInt())..groundY + 2) {
                if (y < 0 || y >= HEIGHT) continue
                if (isTyre(pixels[y * WIDTH + cx.toInt()])) {
                    if (top < 0) top = y
                    bottom = y
                    gap = 0
                } else if (top >= 0) {
                    gap++
                    if (gap > 8) break
                }
            }
            (bottom - top + 1).toFloat()
        }
        val diameter = diameters.max()
        assertEquals("a wheel's drawn diameter", 2f * radiusPx, diameter, 4f)

        // The rear pair: the two adjacent centres closest together.
        val gaps = centres.zipWithNext().map { (a, b) -> b - a }
        val spacing = gaps.min()
        assertTrue(
            "rear-pair centre spacing ${spacing / diameter} diameters must be >= 1.15 " +
                "(spacing ${spacing}px, diameter ${diameter}px)",
            spacing >= 1.15f * diameter,
        )
        assertTrue(
            "the gap between the rear tyres is ${(spacing - diameter)}px and must be visible (>= 2px)",
            spacing - diameter >= 2f,
        )
    }

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 2400
    }
}
