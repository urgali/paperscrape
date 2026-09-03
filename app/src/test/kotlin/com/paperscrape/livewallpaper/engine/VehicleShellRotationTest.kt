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
