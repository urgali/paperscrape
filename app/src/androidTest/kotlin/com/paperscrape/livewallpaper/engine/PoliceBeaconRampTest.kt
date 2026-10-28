package com.paperscrape.livewallpaper.engine

import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **The police car's two beacons light as the taxi's sign does: they rise with the dusk, to 80 %.**
 *
 * Every lamp of the vehicles comes up on one ramp -- dark until the night's glow reaches 0.35, 80 % of full at 0.80 --
 * and the beacons are among them. A colour set after the alpha carries its own alpha, full, over it: beacons drawn that
 * way would come on at once and whole in the frame the ramp first lights, and stay at full where every other lamp
 * stands at 80 %. The taxi's sign, drawn colour first, is the yardstick: the two cars are drawn in the same lane at the
 * same hour and their lamps read off the call the backend gets.
 */
@RunWith(AndroidJUnit4::class)
class PoliceBeaconRampTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun thePoliceBeaconsRiseWithTheDuskAsTheTaxisSignDoes() {
        // Day, dusk (the night's glow at 0.6, the ramp partway) and night.
        for (dayBlend in floatArrayOf(1f, 0.4f, 0f)) {
            val taxi = lampAlphas(CarType.TAXI, dayBlend)
            val police = lampAlphas(CarType.POLICE, dayBlend)
            val sign = taxi[SceneObjectRenderer.TAXI_SIGN_BOX_LEFT_X]
            val red = police[SceneObjectRenderer.POLICE_LAMP_RED_LEFT_X]
            val blue = police[SceneObjectRenderer.POLICE_LAMP_BLUE_LEFT_X]
            if (dayBlend == 1f) {
                assertTrue("by day nothing is lit: sign $sign, beacons $red and $blue", sign == null && red == null && blue == null)
                continue
            }
            assertTrue("the taxi's sign is not lit at dayBlend $dayBlend", sign != null)
            assertEquals("the red beacon at dayBlend $dayBlend", sign, red)
            assertEquals("the blue beacon at dayBlend $dayBlend", sign, blue)
            if (dayBlend == 0f) assertEquals("full night is 80 % of full", (255 * 0.8f).toInt(), red)
            if (dayBlend == 0.4f) assertTrue("at dusk the ramp is partway, not $red", red!! in 1 until (255 * 0.8f).toInt())
        }
    }

    /** The alpha of each rectangle [type]'s car draws at [dayBlend], keyed by its left edge in the car's units. */
    private fun lampAlphas(type: CarType, dayBlend: Float): Map<Float, Int> {
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
                    speedFraction = 0f,
                    startDelaySeconds = -0.5f,
                    color = 0xFFB4513C.toInt(),
                    reverse = true,
                    type = type,
                ),
            ),
        )
        val renderer = SceneObjectRenderer(layout, customization, context, "sunset")
        val recorder = RectRecorder()
        renderer.draw(
            recorder, GroundGeometry(0f, 1080f), dayBlend = dayBlend,
            elapsedSeconds = SceneTime(120.0), screenWidth = 1080f, screenHeight = 2400f,
        )
        assertTrue("the $type car was not drawn", recorder.sprites > 0)
        return recorder.alphas
    }

    /** Records the alpha of every rectangle by its left edge, and counts the sprites so a car is seen to be drawn. */
    private class RectRecorder : SceneCanvas {
        val alphas = HashMap<Float, Int>()
        var sprites = 0
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
            alphas[left] = paint.color ushr 24
        }
        override fun save() = Unit
        override fun restore() = Unit
        override fun translate(dx: Float, dy: Float) = Unit
        override fun scale(sx: Float, sy: Float) = Unit
        override fun rotate(degrees: Float) = Unit
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) = Unit
        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) = Unit
        override fun drawDisc(resId: Int, source: SpriteSource, cx: Float, cy: Float, radius: Float, colour: Int, alpha: Int) = Unit
        override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
        override fun drawWedge(cx: Float, cy: Float, radius: Float, startAngle: Float, sweepAngle: Float, paint: Paint) = Unit
        override fun drawShape(shape: SceneShape, paint: Paint) = Unit
        override fun drawVerticalGradientShape(
            shape: SceneShape,
            gradientTopY: Float,
            gradientBottomY: Float,
            topColor: Int,
            bottomColor: Int,
            alpha: Int,
        ) = Unit
        override fun drawVerticalGradientRect(left: Float, top: Float, right: Float, bottom: Float, topColor: Int, bottomColor: Int) = Unit
        override fun drawRadialGlow(cx: Float, cy: Float, radius: Float, color: Int, centerAlpha: Int) = Unit
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
        override fun drawSprite(
            resId: Int,
            source: SpriteSource,
            left: Float,
            top: Float,
            tintColor: Int,
            alpha: Int,
            additive: Boolean,
        ) {
            sprites++
        }
    }
}
