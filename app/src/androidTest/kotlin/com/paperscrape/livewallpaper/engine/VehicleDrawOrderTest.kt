package com.paperscrape.livewallpaper.engine

import android.graphics.Paint
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import com.paperscrape.livewallpaper.R

/**
 * The livery is painted before the people: rc2 criterion, asserted as a draw sequence.
 *
 * The rc1 frames showed a police driver's shirt meeting the blue stripe on the door -- the shirt
 * was outside the glass (fixed by the fit tests) and the encounter raised the ordering question:
 * if anything of an occupant and the livery ever share pixels, the person must win. The renderer
 * already drew livery first; what was missing was anything that would *fail* if a refactor
 * reordered the two, which is exactly the kind of regression a picture-free suite never sees.
 * The scene is driven through a recording canvas and the order is read back off the actual call
 * sequence, not off the source text.
 */
@RunWith(AndroidJUnit4::class)
class VehicleDrawOrderTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theLiveryIsDrawnBeforeTheOccupants() {
        for ((type, liveryRes, liveryName) in listOf(
            Triple(CarType.POLICE, R.drawable.police_stripe, "police_stripe"),
            Triple(CarType.TAXI, R.drawable.taxi_checker, "taxi_checker"),
        )) {
            val order = recordedSpriteOrder(type)
            val livery = order.indexOfFirst { it == liveryRes }
            val head = order.indexOfFirst { it in OCCUPANT_HEADS }
            assertTrue("$liveryName was never drawn", livery >= 0)
            assertTrue("$type drew no occupant", head >= 0)
            assertTrue(
                "$liveryName (at $livery) must be drawn before the occupant (at $head)",
                livery < head,
            )
        }
    }

    private fun recordedSpriteOrder(type: CarType): List<Int> {
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
        val recorder = SpriteOrderRecorder()
        renderer.draw(
            recorder, GroundGeometry(0f, 1080f), dayBlend = 1f,
            elapsedSeconds = SceneTime(120.0), screenWidth = 1080f, screenHeight = 2400f,
        )
        return recorder.order
    }

    /** Records the sequence of sprite blits and ignores everything else. */
    private class SpriteOrderRecorder : SceneCanvas {
        val order = mutableListOf<Int>()
        override fun save() = Unit
        override fun restore() = Unit
        override fun translate(dx: Float, dy: Float) = Unit
        override fun scale(sx: Float, sy: Float) = Unit
        override fun rotate(degrees: Float) = Unit
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) = Unit
        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) = Unit
        override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
        override fun drawArc(oval: RectF, startAngle: Float, sweepAngle: Float, paint: Paint) = Unit
        override fun drawWedge(
            cx: Float,
            cy: Float,
            radius: Float,
            startAngle: Float,
            sweepAngle: Float,
            paint: Paint,
        ) = Unit
        override fun drawShape(shape: SceneShape, paint: Paint) = Unit
        override fun drawVerticalGradientShape(
            shape: SceneShape,
            gradientTopY: Float,
            gradientBottomY: Float,
            topColor: Int,
            bottomColor: Int,
            alpha: Int,
        ) = Unit
        override fun drawVerticalGradientRect(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            topColor: Int,
            bottomColor: Int,
        ) = Unit
        override fun drawRadialGlow(cx: Float, cy: Float, radius: Float, color: Int, centerAlpha: Int) = Unit
        override fun drawSprite(
            resId: Int,
            source: SpriteSource,
            left: Float,
            top: Float,
            tintColor: Int,
            alpha: Int,
        ) {
            order.add(resId)
        }
    }

    private companion object {
        /** rc4: the occupant is a `head_car` skin drawable -- any adult member, either season,
         * any tone, since the driver's identity is a per-candidate roll. */
        /**
         * Every sprite a seated occupant can be.
         *
         * It used to list the twelve adult first-outfit tones, which was every occupant there was.
         * v4.20 doubled the adult rows with the clothing axis and v4.19 had already made a child a
         * possible passenger, so a list of twelve stopped being "an occupant" and became "some
         * occupants" -- and the failure mode is silent in the wrong direction: the test reports
         * *no occupant drawn* and blames the renderer. It did exactly that on the police car, whose
         * candidate slot happens to deal the second outfit.
         */
        val OCCUPANT_HEADS = setOf(
            R.drawable.person_man_summer_head_car_skin0, R.drawable.person_man_summer_head_car_skin1, R.drawable.person_man_summer_head_car_skin2,
            R.drawable.person_man_winter_head_car_skin0, R.drawable.person_man_winter_head_car_skin1, R.drawable.person_man_winter_head_car_skin2,
            R.drawable.person_woman_summer_head_car_skin0, R.drawable.person_woman_summer_head_car_skin1, R.drawable.person_woman_summer_head_car_skin2,
            R.drawable.person_woman_winter_head_car_skin0, R.drawable.person_woman_winter_head_car_skin1, R.drawable.person_woman_winter_head_car_skin2,
            R.drawable.person_man_summer_head_car_alt_skin0, R.drawable.person_man_summer_head_car_alt_skin1, R.drawable.person_man_summer_head_car_alt_skin2,
            R.drawable.person_man_winter_head_car_alt_skin0, R.drawable.person_man_winter_head_car_alt_skin1, R.drawable.person_man_winter_head_car_alt_skin2,
            R.drawable.person_woman_summer_head_car_alt_skin0, R.drawable.person_woman_summer_head_car_alt_skin1, R.drawable.person_woman_summer_head_car_alt_skin2,
            R.drawable.person_woman_winter_head_car_alt_skin0, R.drawable.person_woman_winter_head_car_alt_skin1, R.drawable.person_woman_winter_head_car_alt_skin2,
            R.drawable.person_boy_summer_head_car_skin0, R.drawable.person_boy_summer_head_car_skin1, R.drawable.person_boy_summer_head_car_skin2,
            R.drawable.person_boy_winter_head_car_skin0, R.drawable.person_boy_winter_head_car_skin1, R.drawable.person_boy_winter_head_car_skin2,
            R.drawable.person_girl_summer_head_car_skin0, R.drawable.person_girl_summer_head_car_skin1, R.drawable.person_girl_summer_head_car_skin2,
            R.drawable.person_girl_winter_head_car_skin0, R.drawable.person_girl_winter_head_car_skin1, R.drawable.person_girl_winter_head_car_skin2,
        )
    }
}
