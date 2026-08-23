package com.paperscrape.livewallpaper.engine

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * That the traffic goldens actually contain traffic, and that they would notice if it changed
 * (**v3.8 Filone 3**).
 *
 * ### Why a golden alone was not enough
 *
 * `SceneGoldenTest.trafficDay` compares `traffic-day.png` pixel by pixel, which pins the frame —
 * but it would pin an empty road just as happily, and for seventeen releases that is exactly what
 * every golden did. A comparison against a committed file cannot tell you *what is in* the file.
 *
 * So this suite asserts the property the golden is supposed to have, independently of the golden:
 * [VehiclePresence] reads the finished frame and counts how much of the road band is not tarmac.
 * It shares no arithmetic with the renderer, so it cannot agree with a bug by inheriting it.
 *
 * ### What is measured
 *
 * | scene | tarmac uniformity | vehicle runs |
 * |---|---|---|
 * | `day` (no warm-up) | 92.2% | **0** |
 * | `night` (no warm-up) | 92.2% | **0** |
 * | `traffic-day` | 79.0% | **4** |
 * | `traffic-night` | 79.0% | **4** |
 *
 * The first two rows are the gap this closes, still measurable because those scenes are unchanged.
 */
@RunWith(AndroidJUnit4::class)
class TrafficGoldenTest {

    private fun measure(scene: GoldenScene): VehiclePresence.Result {
        val bitmap = SceneGolden.render(scene)
        val result = VehiclePresence.measure(bitmap)
        bitmap.recycle()
        Log.i(TAG, "${scene.name}: $result")
        return result
    }

    // -- the vehicles are really there ------------------------------------------------------------

    @Test
    fun trafficDayHasVehiclesOnTheRoad() {
        val result = measure(SharedGoldenScenes.trafficDay())
        assertTrue(
            "expected several vehicles on the road, found ${result.runs.size}",
            result.runs.size >= 3,
        )
        assertTrue(
            "the road band is ${"%.1f".format(result.tarmacFraction * 100)}% uniform tarmac, " +
                "which is what an empty road looks like",
            result.tarmacFraction < 0.85,
        )
    }

    @Test
    fun trafficNightHasVehiclesOnTheRoad() {
        val result = measure(SharedGoldenScenes.trafficNight())
        assertTrue("expected several vehicles, found ${result.runs.size}", result.runs.size >= 3)
        assertTrue(result.tarmacFraction < 0.85)
    }

    /**
     * **The gap, still measurable.** The scenes that existed before v3.8 have an empty road, and
     * that is not a criticism of them — it is why the traffic scenes had to be added rather than
     * an existing one reused.
     */
    @Test
    fun theScenesWithoutWarmUpStillHaveAnEmptyRoad() {
        val day = measure(SharedGoldenScenes.day())
        assertEquals("day should have no vehicles at all", 0, day.runs.size)
        assertTrue("an empty road is near-uniform tarmac", day.tarmacFraction > 0.9)
    }

    /** Both lanes, so a regression confined to one of them cannot hide. */
    @Test
    fun bothLanesAreOccupied() {
        val bitmap = SceneGolden.render(SharedGoldenScenes.trafficDay())
        val lanes = VehiclePresence.occupiedLanes(bitmap)
        bitmap.recycle()
        Log.i(TAG, "traffic-day occupied lanes: $lanes")
        assertTrue("the far lane has no vehicle", lanes.first)
        assertTrue("the near lane has no vehicle", lanes.second)
    }

    // -- the golden would notice a change ----------------------------------------------------------

    /**
     * **Regression value, demonstrated rather than asserted.**
     *
     * A golden is only worth its bytes if a plausible defect changes enough pixels to trip it. Each
     * case below perturbs the scene the way a real regression would and measures the difference
     * against the committed frame, as a fraction of the whole frame — against
     * [SceneGolden.MAX_DIFFERING_FRACTION], the budget the golden actually uses.
     *
     * These are perturbations of the *scene*, not of the renderer: a mutated renderer cannot be
     * expressed from a test. What they show is the sensitivity the frame has to the traffic in it,
     * which is the property in question.
     */
    @Test
    fun theGoldenWouldNoticeIfTheTrafficChanged() {
        val reference = SceneGolden.render(SharedGoldenScenes.trafficDay())
        val budget = SceneGolden.MAX_DIFFERING_FRACTION

        data class Case(val label: String, val scene: GoldenScene)
        val cases = listOf(
            // A car one frame further along: the smallest movement the system can produce.
            Case(
                "traffic advanced by one frame",
                GoldenScene(
                    name = "traffic-day",
                    dayPhase = GoldenScene.day(),
                    warmUpFrames = SharedGoldenScenes.TRAFFIC_WARM_UP_FRAMES + 1,
                ),
            ),
            // Every vehicle gone, which is what a broken car pipeline looks like.
            Case(
                "cars switched off",
                GoldenScene(
                    name = "traffic-day",
                    dayPhase = GoldenScene.day(),
                    warmUpFrames = SharedGoldenScenes.TRAFFIC_WARM_UP_FRAMES,
                    customise = { it.copy(cars = it.cars.copy(visible = false)) },
                ),
            ),
            // Half the traffic thinned out: a density regression.
            Case(
                "car density halved",
                GoldenScene(
                    name = "traffic-day",
                    dayPhase = GoldenScene.day(),
                    warmUpFrames = SharedGoldenScenes.TRAFFIC_WARM_UP_FRAMES,
                    customise = { it.copy(cars = it.cars.copy(density = 0.5f)) },
                ),
            ),
        )

        for (case in cases) {
            val mutated = SceneGolden.render(case.scene)
            val fraction = SceneGolden.differingFraction(reference, mutated)
            mutated.recycle()
            Log.i(
                TAG,
                "regression '${case.label}': ${"%.3f".format(fraction * 100)}% of the frame differs " +
                    "(golden budget ${"%.3f".format(budget * 100)}%)",
            )
            assertTrue(
                "'${case.label}' moved only ${"%.3f".format(fraction * 100)}% of the frame, " +
                    "which is inside the golden's own budget of ${"%.3f".format(budget * 100)}% " +
                    "-- the golden would not catch it",
                fraction > budget,
            )
        }
        reference.recycle()
    }

    /**
     * And the counterpart: the same scene rendered twice is bit-identical, so the warm-up has not
     * made the frame depend on anything but its inputs.
     *
     * This is the determinism claim, and it is the one that could quietly stop being true — the
     * lightning timer draws from an unseeded `Random`, and only stays out of the way because
     * neither traffic scene is a storm.
     */
    @Test
    fun theWarmedUpFrameIsDeterministic() {
        val first = SceneGolden.render(SharedGoldenScenes.trafficDay())
        val second = SceneGolden.render(SharedGoldenScenes.trafficDay())
        val fraction = SceneGolden.differingFraction(first, second)
        first.recycle()
        second.recycle()
        assertEquals("two renders of the same warmed-up scene must be identical", 0.0, fraction, 0.0)
    }

    private companion object {
        const val TAG = "TRAFFIC"
    }
}
