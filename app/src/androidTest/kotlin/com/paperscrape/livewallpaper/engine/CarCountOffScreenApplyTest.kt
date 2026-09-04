package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A car count change never touches a car that is on screen (v4.22).
 *
 * `CarSelectionTest` pins the pure half -- membership may only flip outside the drawn span of
 * the loop. This is the end-to-end half, on rendered pixels through the real renderer: dragging
 * the density down leaves the very next frame identical (nothing vanishes from the middle of the
 * road), the traffic then drains as cars finish their pass, and dragging it back up pops nothing
 * in -- the returning cars drive in from the edges.
 *
 * The scene is the traffic golden's own: `sunset` at midday, warmed the
 * [SharedGoldenScenes.TRAFFIC_WARM_UP_FRAMES] that put four vehicles on the road.
 */
class CarCountOffScreenApplyTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private class LiveScene {
        val bitmap: Bitmap = Bitmap.createBitmap(SceneGolden.WIDTH, SceneGolden.HEIGHT, Bitmap.Config.ARGB_8888)
        private val target = CanvasSceneTarget()
        val renderer = PaperRenderer(
            SceneGolden.WIDTH,
            SceneGolden.HEIGHT,
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
        private val scene = SharedGoldenScenes.trafficDay()
        private var clock = SceneTime(scene.sceneSeconds)

        init {
            scene.configure(renderer)
            target.bind(Canvas(bitmap))
            repeat(scene.warmUpFrames) { frame(1f / 30f) }
        }

        fun frame(deltaSeconds: Float) {
            clock += deltaSeconds
            renderer.draw(target, scene.dayPhase, clock, deltaSeconds)
        }

        fun snapshot(): Bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)

        fun setCarDensity(density: Float) {
            renderer.sceneCustomization = renderer.sceneCustomization.let {
                it.copy(cars = it.cars.copy(density = density))
            }
        }
    }

    /** One full loop at the far lane's slower speed, in 30 fps frames, plus a little slack --
     * long enough for every retired car to finish its pass and leave the screen. The far speed
     * is the near one scaled by the two lanes' perspective ratio (see [SceneSpace.CAR_SPEED_FAR]);
     * a whole loop is 1.6 of progress. */
    private val drainFrames: Int = run {
        val farSpeed = SceneSpace.CAR_SPEED_FAR
        (SceneObjectCatalog.CAR_LOOP_SPAN / farSpeed * 30f * 1.2f).toInt()
    }

    @Test
    fun loweringTheCountRemovesNoCarFromTheScreen() {
        val scene = LiveScene()
        val before = scene.snapshot()

        scene.setCarDensity(0f)
        scene.frame(0f) // the frame the slider commit lands on: same clock, new target
        val after = scene.snapshot()

        val fraction = SceneGolden.differingFraction(before, after)
        assertEquals(
            "the frame after a density change differs from the one before it -- a car was " +
                "added or removed on screen",
            0.0, fraction, 0.0,
        )
        before.recycle()
        after.recycle()
    }

    @Test
    fun theTrafficDrainsToOneCarAsPassesComplete() {
        val scene = LiveScene()
        scene.setCarDensity(0f)
        repeat(drainFrames) { scene.frame(1f / 30f) }

        // Across one further full loop, no frame may show more than the one kept car, and it
        // must actually be seen driving through.
        var seen = 0
        var maxRuns = 0
        repeat(drainFrames) {
            scene.frame(1f / 30f)
            if (it % 10 == 0) {
                val result = VehiclePresence.measure(scene.bitmap)
                maxRuns = maxOf(maxRuns, result.runs.size)
                if (result.runs.isNotEmpty()) seen++
            }
        }
        assertTrue("more than one car on screen after draining to count 1 (max $maxRuns)", maxRuns <= 1)
        assertTrue("the one kept car never crossed the screen", seen > 0)
    }

    @Test
    fun raisingTheCountPopsNoCarIntoTheRoad() {
        val scene = LiveScene()
        scene.setCarDensity(0f)
        repeat(drainFrames) { scene.frame(1f / 30f) }

        val before = scene.snapshot()
        scene.setCarDensity(1f)
        scene.frame(0f)
        val after = scene.snapshot()
        assertEquals(
            "raising the density changed the frame it landed on -- a car materialised on screen",
            0.0, SceneGolden.differingFraction(before, after), 0.0,
        )
        before.recycle()
        after.recycle()

        // And the traffic comes back: the golden scene shows four vehicles at full density.
        repeat(drainFrames) { scene.frame(1f / 30f) }
        var maxRuns = 0
        repeat(drainFrames) {
            scene.frame(1f / 30f)
            if (it % 10 == 0) maxRuns = maxOf(maxRuns, VehiclePresence.measure(scene.bitmap).runs.size)
        }
        assertTrue("the traffic did not return after raising the count (max $maxRuns runs)", maxRuns >= 3)
    }
}
