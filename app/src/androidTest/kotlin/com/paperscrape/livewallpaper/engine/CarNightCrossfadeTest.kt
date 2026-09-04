package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * At dusk the car count changes **by itself**, and still no car pops (v4.22 Fase 3).
 *
 * `CarCountOffScreenApplyTest` pins the off-screen rule for a slider the user drags.
 * This is the case the rule really exists for: with day density 1 and night density 0 the target
 * count slides from ten to one across the sunset with nobody touching anything, and a car that
 * materialised in the middle of the road while the user watched the light change would be the
 * defect. So the scene is driven through a whole dusk (and a dawn) on the renderer's real clock
 * inputs, every frame's road is read back as vehicle runs, and every run must be *accounted for*:
 * a run may appear or vanish only by driving across a screen edge, or by matching a run seen a
 * frame or three earlier (vehicle-detection flicker at the threshold is matched against a short
 * history rather than a single frame, so a detection blink is not misread as a pop).
 *
 * The clock is compressed — the hour advances faster than the 1/30 s frame delta would imply —
 * which the invariant is insensitive to: membership may flip only off screen, however fast the
 * blend moves.
 */
class CarNightCrossfadeTest {

    private class DuskScene {
        val bitmap: Bitmap = Bitmap.createBitmap(SceneGolden.WIDTH, SceneGolden.HEIGHT, Bitmap.Config.ARGB_8888)
        private val target = CanvasSceneTarget()
        private val renderer = PaperRenderer(
            SceneGolden.WIDTH,
            SceneGolden.HEIGHT,
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
        private var clock = SceneTime(120.0)

        init {
            val scene = GoldenScene(name = "dusk-crossfade", dayPhase = GoldenScene.day())
            scene.configure(renderer)
            renderer.sceneCustomization = renderer.sceneCustomization.let {
                it.copy(cars = it.cars.copy(visible = true, density = 1f), carsNightDensity = 0f)
            }
            target.bind(Canvas(bitmap))
            // The traffic golden's own warm-up, so the road starts with the full day's traffic.
            repeat(SharedGoldenScenes.TRAFFIC_WARM_UP_FRAMES) { frame(13f) }
        }

        fun frame(hour24: Float) {
            clock += 1f / 30f
            renderer.draw(target, SunPositionCalculator.compute(hour24 = hour24), clock, 1f / 30f)
        }
    }

    private data class Frame(val runs: List<IntRange>)

    /** A run is accounted for if it overlaps [slack]-expanded runs of a nearby frame, or sits
     * against a screen edge (where entering and leaving legitimately create and destroy runs). */
    private fun accounted(run: IntRange, against: List<Frame>, slack: Int): Boolean {
        if (run.first <= EDGE_PX || run.last >= SceneGolden.WIDTH - EDGE_PX) return true
        return against.any { frame ->
            frame.runs.any { other -> run.first <= other.last + slack && run.last >= other.first - slack }
        }
    }

    private fun assertNoPops(frames: List<Frame>, label: String) {
        for (k in frames.indices) {
            val backward = frames.subList(maxOf(0, k - HISTORY), k)
            val forward = frames.subList(k + 1, minOf(frames.size, k + 1 + HISTORY))
            for (run in frames[k].runs) {
                if (k > 0) {
                    assertTrue(
                        "$label: a vehicle appeared mid-road at ${run.first}..${run.last} (frame $k)",
                        accounted(run, backward, SLACK_PX),
                    )
                }
                if (k < frames.size - 1) {
                    assertTrue(
                        "$label: a vehicle vanished mid-road at ${run.first}..${run.last} (frame $k)",
                        accounted(run, forward, SLACK_PX),
                    )
                }
            }
        }
    }

    @Test
    fun duskDrainsTheRoadWithoutAnyCarPopping() {
        val scene = DuskScene()
        val frames = ArrayList<Frame>()

        // Sunset at 20:00 on the default arc; the night-side twilight ends 1.2 h later. Sweep
        // 19:00 -> 22:00 so the whole count slide is inside the window, then hold deep night for
        // two full far-lane loops so every retired car has finished its pass.
        var hour = 19f
        while (hour < 22f) {
            scene.frame(hour)
            frames.add(Frame(VehiclePresence.measure(scene.bitmap).runs))
            hour += 0.005f
        }
        repeat(DRAIN_FRAMES) {
            scene.frame(22f)
            frames.add(Frame(VehiclePresence.measure(scene.bitmap).runs))
        }
        assertNoPops(frames, "dusk")

        assertTrue("the day frames must start with several vehicles", frames.first().runs.size >= 3)
        val settled = frames.takeLast(DRAIN_FRAMES / 2)
        assertTrue(
            "after dusk the road must be down to the one night car (saw ${settled.maxOf { it.runs.size }})",
            settled.maxOf { it.runs.size } <= 1,
        )

        // And back up through dawn: the returning cars must all drive in from the edges.
        val dawn = ArrayList<Frame>()
        hour = 5f
        while (hour < 8f) {
            scene.frame(hour)
            dawn.add(Frame(VehiclePresence.measure(scene.bitmap).runs))
            hour += 0.005f
        }
        repeat(DRAIN_FRAMES) {
            scene.frame(13f)
            dawn.add(Frame(VehiclePresence.measure(scene.bitmap).runs))
        }
        assertNoPops(dawn, "dawn")
        assertTrue(
            "the day's traffic must have come back (saw ${dawn.takeLast(60).maxOf { it.runs.size }})",
            dawn.takeLast(60).maxOf { it.runs.size } >= 3,
        )
    }

    private companion object {
        /** Runs shrink below the detector's 12-column floor while crossing an edge, so anything
         * born or dying inside this margin is an ordinary entry or exit, not a pop. Derived from
         * the widest vehicle: the fire engine spans ~99 units * ~1.6 scale ~= 160 px at this
         * frame's near lane, and a run is unaccounted only if wholly clear of the margin. */
        const val EDGE_PX = 80

        /** One frame moves a near-lane car by `0.075/30` of ~600 px of travel ~= 1.5 px; three
         * history frames plus detector jitter round up generously. */
        const val SLACK_PX = 40

        const val HISTORY = 3

        /** Two full far-lane loops at 30 fps: `2 * (1.6 / CAR_SPEED_FAR) * 30`. */
        val DRAIN_FRAMES = (2f * SceneObjectCatalog.CAR_LOOP_SPAN / SceneSpace.CAR_SPEED_FAR * 30f).toInt()
    }
}
