package com.paperscrape.livewallpaper.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A car's body is a property of the car, not of the frame it is drawn in.
 *
 * v4.19 gives the street three civilian bodies and lets a plain car pick one. The requirement the
 * pass was given is not that the pick is varied -- that is easy -- but that it is **stable**: a
 * vehicle must not change model while it crosses the screen, while the home screen is swiped, or
 * when another vehicle enters or leaves the frame.
 *
 * That is exactly the class of defect v4.17's falling leaves had, where the colour came from
 * `i % visibleCount` over a list the visibility pass rebuilt every frame, so a leaf changed colour
 * whenever a *different* leaf appeared. The shape of the fix is that the body is a pure function
 * of the vehicle's own immutable identity and is resolved once, at runtime construction, where no
 * per-frame state can reach it.
 *
 * So this file attacks the property from three directions: the function is pure (same spec, same
 * body, every time), nothing per-frame is in the expression (read out of the renderer's own
 * source), and the membership of the runtime list -- the thing density actually changes -- cannot
 * move a body.
 */
class VehicleShellRotationTest {

    private fun everyShippedCar(): List<CarObject> = ThemeCatalog.ALL
        .flatMap { SceneObjectCatalog.layoutFor(it.id, it.accentColor).cars }

    @Test
    fun `the body is a pure function of the vehicle's own identity`() {
        val cars = everyShippedCar()
        assertTrue("no traffic to check", cars.isNotEmpty())
        for (spec in cars) {
            val first = CarShell.forCar(spec)
            repeat(64) {
                assertEquals("$spec changed body between calls", first, CarShell.forCar(spec))
            }
            // A copy carrying the same identity fields must land on the same body: this is what
            // makes a rebuilt runtime list return the same street.
            val copy = spec.copy(color = spec.color.inv())
            assertEquals("the body must not depend on a car's colour", first, CarShell.forCar(copy))
        }
    }

    /**
     * The membership of the drawn list is what a density change moves, and it must move nothing
     * else. Simulated the way `SceneObjectRenderer.buildCarRuntimes` does it: filter, then sort.
     */
    @Test
    fun `thinning the traffic never changes a surviving car's body`() {
        for (theme in ThemeCatalog.ALL) {
            val all = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor).cars
            val expected = all.associateWith { CarShell.forCar(it) }
            // Every prefix of the candidate list stands in for a density setting, and the sort by
            // lane reorders what survives -- both of which changed a leaf's colour in v4.17.
            for (keep in 1..all.size) {
                val visible = all.take(keep).sortedBy { it.laneYFraction }
                for (spec in visible) {
                    assertEquals(
                        "${theme.id}: a car changed body when the traffic was thinned to $keep",
                        expected[spec], CarShell.forCar(spec),
                    )
                }
            }
        }
    }

    /**
     * Nothing per-frame may reach the choice.
     *
     * `drawCar` receives the scroll-dependent geometry (`progress`, `screenWidth`, `dayBlend`) and
     * must not have the body anywhere near them: it reads what the runtime resolved once. Read out
     * of the source because *where the call is* is the whole property.
     */
    @Test
    fun `drawCar reads the body off the runtime and never picks one itself`() {
        val source = rendererSource().readText()
        val drawCar = source.substringAfter("private fun drawCar(").substringBefore("\n    private fun ")
        assertTrue(
            "drawCar must take the body from the runtime",
            drawCar.contains("val shell = c.shell"),
        )
        assertTrue(
            "and must not resolve one itself, which would put the choice in the draw path",
            !drawCar.contains("CarShell.forCar("),
        )
        assertTrue(
            "the runtime must resolve it once, at construction",
            source.contains("val shell: CarShell = CarShell.forCar(spec)"),
        )
    }

    /**
     * The rotation is not vacuous: all three bodies actually occur in the shipped catalogue, and
     * no one of them takes the whole road.
     */
    @Test
    fun `all three bodies occur across the shipped themes, and none dominates`() {
        val plain = everyShippedCar().filter { it.type == CarType.PLAIN }
        assertTrue("no plain cars to rotate", plain.isNotEmpty())
        val counts = plain.groupingBy { CarShell.forCar(it) }.eachCount()
        for (shell in CarShell.entries) {
            assertTrue("$shell never appears on any theme's road", (counts[shell] ?: 0) > 0)
        }
        val share = counts.values.max().toFloat() / plain.size
        assertTrue(
            "one body takes ${"%.0f".format(share * 100)}% of the traffic, which is not a rotation",
            share < 0.60f,
        )
    }

    // ---------------------------------------------------------------- the deal, and its balance

    /**
     * **The whole identity space is ten items, and this enumerates all of it.**
     *
     * A civilian car's body is a function of its lane and its queue slot, and there are two lanes
     * and [SceneObjectCatalog.CAR_SLOTS_PER_LANE] slots -- for every theme the app ships, because
     * both are constants. So "the distribution" is not something to sample: it is a hand of ten
     * cards, and this deals it out and counts it.
     *
     * v4.19's avalanche hash dealt **5 saloons, 3 estates, 2 compacts**, which is where the
     * shipped 51 / 31 / 19 came from. Four-three-three is the most even deal ten items admit, and
     * the estate takes the fourth because the two liveried types already pin a compact and a
     * saloon onto the road -- see [CarShell.forCar].
     */
    @Test
    fun `the ten candidate identities are dealt four-three-three`() {
        val counts = everyIdentity().values.flatten().groupingBy { it }.eachCount()
        assertEquals(
            "the deal is not 4/3/3, so it is not the most even one ten slots allow",
            mapOf(CarShell.ESTATE to 4, CarShell.SALOON to 3, CarShell.COMPACT to 3),
            counts,
        )
    }

    /**
     * The order of the deal, which is what keeps it from reading as a repeating cycle.
     *
     * Balance alone would be satisfied by A/B/C/A/B/C..., and that was the objection v4.19 raised
     * against a plain modulo -- rightly. These are the three properties that answer it, asserted
     * rather than eyeballed: a lane never shows the same body twice running, each lane carries all
     * three, and the two lanes never hold the same body at the same queue position.
     */
    @Test
    fun `the deal reads as a mixture rather than as a cycle`() {
        val byLane = everyIdentity()
        for ((lane, queue) in byLane) {
            for (i in 1 until queue.size) {
                assertTrue(
                    "the $lane lane repeats ${queue[i]} at queue positions ${i - 1} and $i: $queue",
                    queue[i] != queue[i - 1],
                )
            }
            assertEquals("the $lane lane does not carry all three bodies: $queue", 3, queue.toSet().size)
        }
        val near = byLane.getValue("near")
        val far = byLane.getValue("far")
        for (slot in near.indices) {
            assertTrue(
                "both lanes hold a ${near[slot]} at queue position $slot",
                near[slot] != far[slot],
            )
        }
    }

    /**
     * The maintainer's criterion, on the population it is about: **no body under 25% or over 40%
     * of the civilian cars**, over a sample of at least a thousand.
     *
     * The ten identities are the same in every theme; what differs from theme to theme is which of
     * them come up as a taxi, a patrol car or a fire engine and so leave the civilian population.
     * That is the only source of variation there is, so the sample is drawn over generator seeds,
     * which is exactly the axis that moves.
     */
    @Test
    fun `no body is under a quarter or over two fifths of the civilian traffic`() {
        val civilian = (0 until 250).flatMap { seed ->
            SceneObjectCatalog.generateCarCandidates(seed, accentColor = 0xFF804020.toInt())
        }.filter { it.type == CarType.PLAIN }
        assertTrue("sample of ${civilian.size} civilian cars is under the required 1000", civilian.size >= 1000)

        val counts = civilian.groupingBy { CarShell.forCar(it) }.eachCount()
        val report = CarShell.entries.joinToString {
            "$it ${"%.1f".format(100f * (counts[it] ?: 0) / civilian.size)}%"
        }
        for (shell in CarShell.entries) {
            val share = (counts[shell] ?: 0).toFloat() / civilian.size
            assertTrue("over ${civilian.size} civilian cars: $report", share in 0.25f..0.40f)
        }
    }

    /** The ten identities, laid out as the two lanes' queues -- the deal, read back from `forCar`. */
    private fun everyIdentity(): Map<String, List<CarShell>> = mapOf(
        "near" to SceneSpace.ROAD_LANE_NEAR_Y_FRACTION,
        "far" to SceneSpace.ROAD_LANE_FAR_Y_FRACTION,
    ).mapValues { (_, lane) ->
        (0 until SceneObjectCatalog.CAR_SLOTS_PER_LANE).map { slot ->
            CarShell.forCar(
                CarObject(
                    laneYFraction = lane,
                    speedFraction = 0f,
                    startDelaySeconds = SceneObjectCatalog.CAR_LOOP_ENTRY_PROGRESS +
                        SceneObjectCatalog.CAR_LOOP_SPAN * slot / SceneObjectCatalog.CAR_SLOTS_PER_LANE,
                    color = 0,
                    type = CarType.PLAIN,
                ),
            )
        }
    }

    /** A taxi is always the compact and a police car always the saloon, whatever the seed. */
    @Test
    fun `the liveried types never rotate their body`() {
        for (spec in everyShippedCar()) {
            when (spec.type) {
                CarType.TAXI -> assertEquals("a taxi is always the compact", CarShell.COMPACT, CarShell.forCar(spec))
                CarType.POLICE -> assertEquals("a police car is always the saloon", CarShell.SALOON, CarShell.forCar(spec))
                else -> Unit
            }
        }
    }

    private fun rendererSource(): File {
        var dir = File(".").absoluteFile
        while (dir.parentFile != null) {
            val candidate = File(dir, "src/main/kotlin/com/paperscrape/livewallpaper/engine/SceneObjectRenderer.kt")
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error("SceneObjectRenderer.kt not found")
    }
}
