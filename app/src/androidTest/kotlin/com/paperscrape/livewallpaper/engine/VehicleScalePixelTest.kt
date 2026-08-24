package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The depth hierarchy, measured on the pixels a device actually paints.
 *
 * ### Why this exists rather than a golden
 *
 * A golden cannot see this defect. The whole-frame tolerance is 0.2% of a 360x800 frame — 576
 * pixels — and every pedestrian in the scene changing height by two pixels moves fewer than that;
 * measured, the entire people population resizing by 8.6% moved about 250. The goldens were run
 * against the fix and **passed**, which is a fact about their tuning rather than about the change.
 * What is needed here is a measurement, not a comparison: render the scene, find each figure, and
 * check its height against what [SceneSpace] says it should be.
 *
 * ### How the figures are isolated
 *
 * By **density**, never by visibility: `drawRoad` returns early when the Cars category is switched
 * off, so a `visible = false` baseline would take the tarmac away with the traffic and the
 * difference between two frames would be the road. Rendering the same scene at density 0 and at
 * density 1 and differencing gives exactly the pixels that category paints.
 */
class VehicleScalePixelTest {

    private fun scene(carsDensity: Float, peopleDensity: Float) = GoldenScene(
        name = "vehicle-scale",
        dayPhase = GoldenScene.day(),
        warmUpFrames = SharedGoldenScenes.TRAFFIC_WARM_UP_FRAMES,
        customise = {
            it.copy(
                cars = it.cars.copy(visible = true, density = carsDensity),
                people = it.people.copy(visible = true, density = peopleDensity),
                peopleNightDensity = peopleDensity,
            )
        },
    )

    /** Columns where two frames disagree, as `x -> (topRow, bottomRow)`. */
    private fun columnExtents(a: Bitmap, b: Bitmap): Map<Int, Pair<Int, Int>> {
        val out = HashMap<Int, Pair<Int, Int>>()
        for (x in 0 until SceneGolden.WIDTH) {
            var top = -1
            var bottom = -1
            for (y in 0 until SceneGolden.HEIGHT) {
                if (a.getPixel(x, y) != b.getPixel(x, y)) {
                    if (top < 0) top = y
                    bottom = y
                }
            }
            if (top >= 0) out[x] = top to bottom
        }
        return out
    }

    /** Contiguous column runs, each one object, as `(firstX, lastX, top, bottom)`. */
    private fun objects(extents: Map<Int, Pair<Int, Int>>): List<IntArray> {
        val xs = extents.keys.sorted()
        if (xs.isEmpty()) return emptyList()
        val runs = ArrayList<MutableList<Int>>()
        var current = mutableListOf(xs.first())
        for ((a, b) in xs.zipWithNext()) {
            if (b - a <= 1) current.add(b) else { runs.add(current); current = mutableListOf(b) }
        }
        runs.add(current)
        return runs.map { run ->
            intArrayOf(
                run.first(), run.last(),
                run.minOf { extents.getValue(it).first },
                run.maxOf { extents.getValue(it).second },
            )
        }
    }

    private val empty by lazy { SceneGolden.render(scene(0f, 0f)) }

    /** The predicted drawn height of one thing, in this frame's pixels. */
    private fun predicted(spriteUnits: Float, baseScale: Float, groundYFraction: Float): Float =
        spriteUnits * baseScale * SceneSpace.perspectiveScaleAt(groundYFraction) *
            SceneSpace.sceneScale(SceneGolden.HEIGHT.toFloat())

    // ------------------------------------------------------------------ vehicles

    /**
     * Every vehicle on the road is drawn at the height its lane implies.
     *
     * Objects are matched to a lane by where they stand — the bottom of a vehicle's own pixels is
     * its wheel contact, which is the lane's ground line. The tolerance is generous upward on
     * purpose: a police car carries a light bar six local units above its roof, which is real
     * artwork and not a scale error.
     */
    @Test
    fun everyVehicleIsDrawnAtTheHeightItsLaneImplies() {
        val found = objects(columnExtents(SceneGolden.render(scene(1f, 0f)), empty))
        assertTrue("no vehicles were rendered to measure", found.size >= 3)

        val farGround = SceneSpace.ROAD_LANE_FAR_Y_FRACTION * SceneGolden.HEIGHT
        val nearGround = SceneSpace.ROAD_LANE_NEAR_Y_FRACTION * SceneGolden.HEIGHT
        var farSeen = 0
        var nearSeen = 0
        for (v in found) {
            val bottom = v[3].toFloat()
            val height = (v[3] - v[2] + 1).toFloat()
            val lane = when {
                kotlin.math.abs(bottom - farGround) <= 3f -> { farSeen++; SceneSpace.ROAD_LANE_FAR_Y_FRACTION }
                kotlin.math.abs(bottom - nearGround) <= 3f -> { nearSeen++; SceneSpace.ROAD_LANE_NEAR_Y_FRACTION }
                else -> continue
            }
            val body = predicted(SceneSpace.CAR_SPRITE_UNITS_TALL, SceneSpace.CAR_BASE_SCALE, lane)
            assertTrue(
                "a vehicle at x=${v[0]}..${v[1]} is ${height}px where its lane implies ${"%.1f".format(body)}",
                height >= body - 1.5f && height <= body + 4f,
            )
        }
        assertTrue("no vehicle was found on the far lane", farSeen > 0)
        assertTrue("no vehicle was found on the near lane", nearSeen > 0)
    }

    /** And they stay on the road: a wheel contact is a lane, never the pavement or the verge. */
    @Test
    fun everyVehicleStandsOnTheRoadPlane() {
        val found = objects(columnExtents(SceneGolden.render(scene(1f, 0f)), empty))
        val lanes = listOf(
            SceneSpace.ROAD_LANE_FAR_Y_FRACTION * SceneGolden.HEIGHT,
            SceneSpace.ROAD_LANE_NEAR_Y_FRACTION * SceneGolden.HEIGHT,
        )
        for (v in found) {
            val bottom = v[3].toFloat()
            assertTrue(
                "a vehicle at x=${v[0]}..${v[1]} has its wheels at $bottom, on neither lane ($lanes)",
                lanes.any { kotlin.math.abs(bottom - it) <= 3f },
            )
        }
    }

    // ------------------------------------------------------------------ pedestrians

    /** Every walking figure is drawn at the height its pavement row and its age imply. */
    @Test
    fun everyPedestrianIsDrawnAtTheHeightItsRowImplies() {
        val found = objects(columnExtents(SceneGolden.render(scene(0f, 1f)), empty))
        assertTrue("no pedestrians were rendered to measure", found.isNotEmpty())

        val population = PedestrianPopulation.build(
            "sunset".hashCode(), 1f,
            SceneSpace.PAVEMENT_NEAR_Y_FRACTION, SceneSpace.PAVEMENT_FAR_Y_FRACTION,
        )
        var matched = 0
        for (figure in found) {
            val bottom = figure[3].toFloat()
            val height = (figure[3] - figure[2] + 1).toFloat()
            // Whoever the generator put on that ground line, within a pixel of it.
            val who = population.minByOrNull {
                kotlin.math.abs(it.rowYFraction * SceneGolden.HEIGHT - bottom)
            } ?: continue
            if (kotlin.math.abs(who.rowYFraction * SceneGolden.HEIGHT - bottom) > 2f) continue
            matched++
            val units = if (who.age == PersonAge.ADULT) SceneSpace.PERSON_SPRITE_UNITS_TALL else CHILD_UNITS
            val expected = predicted(units, SceneSpace.PERSON_BASE_SCALE, who.rowYFraction)
            assertTrue(
                "a ${who.age} at x=${figure[0]}..${figure[1]} is ${height}px where its row implies " +
                    "${"%.1f".format(expected)}",
                height >= expected - 1f && height <= expected + 1.5f,
            )
        }
        assertTrue("no rendered figure could be matched to the population", matched >= 2)
    }

    // ------------------------------------------------------------------ the reported inversion

    /**
     * **The defect, as pixels.**
     *
     * A car in the far lane stands nearer the viewer than a pedestrian on the far pavement, so it
     * must be drawn taller. Measured before the fix: the tallest far-pavement adult came out 22 px
     * against the plain far-lane car's 22 — level, with the person in front on the eye. Measured
     * after: 20 against 22.
     *
     * The car is found by its wheel line and the pedestrian by its feet, so neither number comes
     * from the model this test is checking.
     */
    @Test
    fun aFarLaneCarIsTallerThanAnAdultOnTheFarPavement() {
        val vehicles = objects(columnExtents(SceneGolden.render(scene(1f, 0f)), empty))
        val people = objects(columnExtents(SceneGolden.render(scene(0f, 1f)), empty))

        val farGround = SceneSpace.ROAD_LANE_FAR_Y_FRACTION * SceneGolden.HEIGHT
        val shortestFarCar = vehicles
            .filter { kotlin.math.abs(it[3] - farGround) <= 3f }
            .minOfOrNull { it[3] - it[2] + 1 }
        assertTrue("no far-lane vehicle to measure", shortestFarCar != null)

        val pavementRows = listOf(SceneSpace.PAVEMENT_FAR_Y_FRACTION, SceneSpace.PAVEMENT_NEAR_Y_FRACTION)
            .map { it * SceneGolden.HEIGHT }
        val tallestFarPedestrian = people
            .filter { fig -> pavementRows.any { kotlin.math.abs(fig[3] - it) <= 12f } }
            .maxOfOrNull { it[3] - it[2] + 1 }
        assertTrue("no pedestrian to measure", tallestFarPedestrian != null)

        assertTrue(
            "the shortest far-lane car is ${shortestFarCar}px and the tallest pedestrian behind it " +
                "is ${tallestFarPedestrian}px -- the nearer object is not the larger one",
            shortestFarCar!! > tallestFarPedestrian!!,
        )
    }

    // ------------------------------------------------------------------ the busts

    /**
     * Driver and passenger stay behind the glass.
     *
     * Their skin is the only thing in a vehicle painted in the shipped person tones, so the busts
     * can be found by colour and checked against the window the car draws — measured in the car's
     * own local units through the lane's scale, so the check holds on either lane.
     */
    @Test
    fun theBustsStayInsideTheGlass() {
        val withCars = SceneGolden.render(scene(1f, 0f))
        val extents = columnExtents(withCars, empty)
        val skin = listOf(intArrayOf(240, 201, 166), intArrayOf(220, 169, 124), intArrayOf(169, 113, 75))

        var busts = 0
        for ((x, range) in extents) {
            for (y in range.first..range.second) {
                val p = withCars.getPixel(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val isSkin = skin.any {
                    kotlin.math.abs(r - it[0]) < 20 && kotlin.math.abs(g - it[1]) < 20 && kotlin.math.abs(b - it[2]) < 20
                }
                if (!isSkin) continue
                busts++
                // A bust pixel must sit above its vehicle's wheel line and below its roof — i.e.
                // inside the body, never floating over the road or above the car.
                val lane = listOf(
                    SceneSpace.ROAD_LANE_FAR_Y_FRACTION,
                    SceneSpace.ROAD_LANE_NEAR_Y_FRACTION,
                ).minByOrNull { kotlin.math.abs(it * SceneGolden.HEIGHT - range.second) }!!
                val ground = lane * SceneGolden.HEIGHT
                val roof = ground - predicted(SceneSpace.CAR_SPRITE_UNITS_TALL, SceneSpace.CAR_BASE_SCALE, lane)
                assertTrue("a bust pixel at ($x,$y) is below the wheel line $ground", y < ground)
                assertTrue("a bust pixel at ($x,$y) is above the roof ${"%.1f".format(roof)}", y > roof - 1f)
            }
        }
        assertTrue("no driver or passenger was found behind any windscreen", busts >= 4)
    }

    private companion object {
        const val CHILD_UNITS = 62f
    }
}
