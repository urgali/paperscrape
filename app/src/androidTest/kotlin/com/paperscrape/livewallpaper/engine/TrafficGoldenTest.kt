package com.paperscrape.livewallpaper.engine

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * That the traffic goldens actually contain traffic, and that they would notice if it changed.
 *
 * ### Why a golden alone is not enough
 *
 * `SceneGoldenTest.trafficDay` compares `traffic-day.png` pixel by pixel, which pins the frame —
 * but it would pin an empty road just as happily. A comparison against a committed file cannot
 * tell you *what is in* the file.
 *
 * So this suite asserts the property the golden is supposed to have, independently of the golden:
 * [VehiclePresence] reads the finished frame and counts how much of the road band is not tarmac.
 * It shares no arithmetic with the renderer, so it cannot agree with a bug by inheriting it.
 *
 * ### What is checked
 *
 * The near row below the road (decision 96) reaches over the road's lower edge and into the near lane,
 * and [VehiclePresence] reads that as a vehicle standing still, so the vehicles are counted on a copy of
 * the traffic scenes with the trees, houses, places and parasols off -- the same warm-up, the same cars --
 * and against that copy with its cars off too. `traffic-day` and `traffic-night` hold at least three
 * vehicle runs and less than 85 % uniform tarmac, and `traffic-day` both lanes occupied and neither with the cars off;
 * `day`, without a warm-up, holds no run its car-less copy does not. Every reading is logged under `TRAFFIC`.
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

    /**
     * [scene] with only its cars over the road band: the trees, the houses, the places and the parasols off, so the near
     * row below the road (decision 96) puts nothing there -- with them on, a near crown reaches the near lane's body and the
     * road's lower edge and reads as a vehicle standing still. The warm-up is the scene's, so the same cars are there.
     */
    private fun carsOnly(scene: GoldenScene) = GoldenScene(
        name = "${scene.name}-cars-only",
        dayPhase = scene.dayPhase,
        sceneSeconds = scene.sceneSeconds,
        warmUpFrames = scene.warmUpFrames,
        warmUpDeltaSeconds = scene.warmUpDeltaSeconds,
        themeId = scene.themeId,
        weather = scene.weather,
        customise = {
            scene.customise(it).let { c ->
                c.copy(
                    trees = c.trees.copy(visible = false),
                    houses = c.houses.copy(visible = false),
                    buildings = c.buildings.copy(visible = false),
                    shopsVisible = false,
                    publicsVisible = false,
                    parasols = c.parasols.copy(visible = false),
                )
            }
        },
    )

    /** [scene] with its cars turned off: no road is painted, and what covers the road band then is not a vehicle. */
    private fun withoutCars(scene: GoldenScene) = GoldenScene(
        name = "${scene.name}-no-cars",
        dayPhase = scene.dayPhase,
        sceneSeconds = scene.sceneSeconds,
        themeId = scene.themeId,
        weather = scene.weather,
        customise = { scene.customise(it).let { c -> c.copy(cars = c.cars.copy(visible = false)) } },
    )

    /**
     * The runs of [scene]'s road band clear of everything the same scene shows there with its cars off: the near row below
     * the road (decision 96) reaches over the road's lower edge here and there -- a tree's crown, a turret's roof -- and
     * [VehiclePresence] reads that as a vehicle standing still.
     */
    private fun vehicles(scene: GoldenScene, result: VehiclePresence.Result): List<IntRange> {
        val standing = measure(withoutCars(scene)).runs
        return result.runs.filter { run -> standing.none { it.first <= run.last && it.last >= run.first } }
    }

    // -- the vehicles are really there ------------------------------------------------------------

    @Test
    fun trafficDayHasVehiclesOnTheRoad() {
        val scene = carsOnly(SharedGoldenScenes.trafficDay())
        val result = measure(scene)
        val vehicles = vehicles(scene, result)
        assertTrue(
            "expected several vehicles on the road, found ${vehicles.size} (${result.runs.size} runs)",
            vehicles.size >= 3,
        )
        assertTrue(
            "the road band is ${"%.1f".format(result.tarmacFraction * 100)}% uniform tarmac, " +
                "which is what an empty road looks like",
            result.tarmacFraction < 0.85,
        )
    }

    @Test
    fun trafficNightHasVehiclesOnTheRoad() {
        val scene = carsOnly(SharedGoldenScenes.trafficNight())
        val result = measure(scene)
        val vehicles = vehicles(scene, result)
        assertTrue("expected several vehicles, found ${vehicles.size} (${result.runs.size} runs)", vehicles.size >= 3)
        assertTrue(result.tarmacFraction < 0.85)
    }

    /**
     * **The empty road of the other scenes.** The golden scenes without a warm-up have an empty
     * road, and that is not a criticism of them — it is why the traffic scenes are scenes of their
     * own rather than an existing one reused.
     */
    @Test
    fun theScenesWithoutWarmUpStillHaveAnEmptyRoad() {
        // Decisions 96 and 105 of `DESIGN_NOTES.md` §12: the near row's tree crowns below the road reach over its lower edge
        // here and there, so the road band of `day` is read against the same scene with no car on the road kept empty (both
        // densities at 0 %, *Keep the road* on, decision 104): the same tarmac under the same crowns, so what covers the band
        // in both is not a vehicle, and beyond it `day` adds nothing.
        val day = measure(SharedGoldenScenes.day())
        val noCars = measure(
            GoldenScene(
                name = "day-no-cars",
                dayPhase = GoldenScene.day(),
                customise = { it.copy(cars = it.cars.copy(density = 0f), carsNightDensity = 0f, keepRoad = true) },
            ),
        )
        assertEquals("day should have no vehicles at all", noCars.runs, day.runs)
        assertTrue(
            "an empty road is tarmac and its dashes: ${day.nonTarmacFraction} not tarmac, ${noCars.nonTarmacFraction} with the cars off",
            day.nonTarmacFraction < noCars.nonTarmacFraction + 0.05,
        )
    }

    /**
     * Both lanes, so a regression confined to one of them cannot hide; and neither reads occupied with the cars off, so
     * what fills them is a vehicle and not the near row reaching over the road.
     */
    @Test
    fun bothLanesAreOccupied() {
        val scene = carsOnly(SharedGoldenScenes.trafficDay())
        val empty = SceneGolden.render(withoutCars(scene))
        val emptyLanes = VehiclePresence.occupiedLanes(empty)
        empty.recycle()
        Log.i(TAG, "traffic-day with its cars off, occupied lanes: $emptyLanes")
        assertTrue("the far lane reads occupied with the cars off", !emptyLanes.first)
        assertTrue("the near lane reads occupied with the cars off", !emptyLanes.second)
        val bitmap = SceneGolden.render(scene)
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
     * [LOOSE_FRAME_BUDGET], a loose budget of 0.002 of the frame.
     *
     * These are perturbations of the *scene*, not of the renderer: a mutated renderer cannot be
     * expressed from a test. What they show is the sensitivity the frame has to the traffic in it,
     * which is the property in question.
     *
     * **The bar is a loose budget, not the gate's.** [SceneGolden.MAX_DIFFERING_FRACTION] is zero,
     * and measuring a regression against zero demonstrates only that it moves one pixel, which is
     * not a demonstration. So the comparison below is at **0.002** -- 576 pixels of a 360x800
     * frame -- and says that each of these defects would be caught even by a loose gate. The gate
     * at zero can only make that more true, never less, which is why the loose number here is a
     * floor under the claim rather than a copy of a constant.
     */
    @Test
    fun theGoldenWouldNoticeIfTheTrafficChanged() {
        val reference = SceneGolden.render(SharedGoldenScenes.trafficDay())
        val budget = LOOSE_FRAME_BUDGET

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

        /**
         * A loose whole-frame budget: 0.002 of a 360x800 frame, i.e. 576 pixels.
         *
         * The bar for the regression demonstration above, deliberately **not** read from
         * [SceneGolden.MAX_DIFFERING_FRACTION], which is zero. See that test's doc.
         */
        const val LOOSE_FRAME_BUDGET = 0.002
    }
}
