package com.paperscrape.livewallpaper.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One fire engine, one patrol car, one taxi. Never two.
 *
 * Items 11 and 14 of `BACKLOG_v4_19.md` -- the same defect recorded twice, once from the code and
 * once from a photograph. `pickCarType` rolled each of the ten candidates independently at a tenth
 * per special type, and nothing looked at what the other nine had come up as, so a theme could
 * carry two fire engines as easily as one. It was not a corner case: **eight of the twelve shipped
 * themes carried a duplicated special type**, eleven vehicles in total (five patrol cars, three
 * taxis, three fire engines), and over 250 generator seeds **171 did**. A v4.19 night capture shows
 * two fire engines in the same lane in the same frame, which is that arithmetic photographed.
 *
 * ### Why capping the candidate set is the whole answer
 *
 * A theme's ten candidates all drive the same road, and any of them can be on screen at the same
 * time -- they are staggered around one loop, not partitioned. So "at most one per type in the set"
 * is a *stronger* statement than "at most one per type in the screen width", and it needs no
 * per-frame state to hold: it is decided once, at generation. The tests below assert it on the
 * shipped themes, across generator seeds, and under every density the slider can reach, because
 * density is the only thing that changes which candidates are drawn.
 */
class SpecialVehicleDensityTest {

    private val specials = CarType.entries.filter { it != CarType.PLAIN }

    private fun surplus(cars: List<CarObject>): Map<CarType, Int> =
        cars.groupingBy { it.type }.eachCount()
            .filterKeys { it != CarType.PLAIN }
            .filterValues { it > 1 }

    @Test
    fun `no shipped theme puts two of a special type on its road`() {
        for (theme in ThemeCatalog.ALL) {
            val cars = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor).cars
            assertTrue("${theme.id} has no traffic at all", cars.isNotEmpty())
            assertEquals(
                "${theme.id} carries more than one of a special type",
                emptyMap<CarType, Int>(), surplus(cars),
            )
        }
    }

    @Test
    fun `no generator seed produces two of a special type`() {
        for (seed in 0 until 250) {
            val cars = SceneObjectCatalog.generateCarCandidates(seed, accentColor = 0)
            assertEquals("seed $seed produced a duplicate special", emptyMap<CarType, Int>(), surplus(cars))
        }
    }

    /**
     * And it still holds at every density, which is the only control that changes what is drawn.
     *
     * Thinning can only remove candidates, so this cannot fail while the set-level cap holds -- and
     * that is exactly why it is worth asserting: it pins the *reason* the screen-level property
     * follows from the set-level one, so a future pass that moves the cap somewhere per-frame
     * breaks a test instead of quietly reopening the defect.
     */
    @Test
    fun `the cap survives every density the slider can reach`() {
        for (theme in ThemeCatalog.ALL) {
            val cars = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor).cars
            for (step in 0..20) {
                val customization = SceneCustomization.DEFAULT.let {
                    it.copy(cars = it.cars.copy(visible = true, density = step / 20f))
                }
                val visible = cars.filter { customization.keepCar(it) }
                assertEquals(
                    "${theme.id} at density ${step * 5}% shows more than one of a special type",
                    emptyMap<CarType, Int>(), surplus(visible),
                )
            }
        }
    }

    // ---------------------------------------------------------------- the cap itself

    /**
     * The cap keeps the first of each special type and demotes the rest to [CarType.PLAIN].
     *
     * Demotion rather than removal is the point: the surplus candidate keeps its slot, its lane and
     * its colour, so the road carries the same number of cars and the density slider and the shell
     * deal still govern the same ten candidates. A cap that deleted them would quietly thin the
     * traffic on eight of the twelve themes.
     */
    @Test
    fun `the cap demotes the surplus and keeps the first of each type`() {
        val rolled = listOf(
            CarType.PLAIN, CarType.POLICE, CarType.TAXI, CarType.POLICE,
            CarType.FIRE_TRUCK, CarType.TAXI, CarType.FIRE_TRUCK, CarType.POLICE,
        )
        assertEquals(
            listOf(
                CarType.PLAIN, CarType.POLICE, CarType.TAXI, CarType.PLAIN,
                CarType.FIRE_TRUCK, CarType.PLAIN, CarType.PLAIN, CarType.PLAIN,
            ),
            SceneObjectCatalog.capSpecialsToOnePerType(rolled),
        )
        for (type in specials) {
            assertEquals(
                "$type survives exactly once",
                1, SceneObjectCatalog.capSpecialsToOnePerType(List(6) { type }).count { it == type },
            )
        }
        assertEquals(
            "the cap never changes how many cars there are",
            rolled.size, SceneObjectCatalog.capSpecialsToOnePerType(rolled).size,
        )
    }

    /**
     * The chaos themes go through the same cap.
     *
     * They are generated by `RandomSceneGenerator` rather than by the catalogue, they carry at most
     * two cars, and two fire engines out of two is exactly where the defect reads worst. Asserted
     * by reading the source because the generator's own output needs the Android framework, which a
     * JVM test does not have -- what matters here is that the call is there at all.
     */
    @Test
    fun `the chaos generator caps its specials too, and after the roll`() {
        val source = File(repoRoot(), "app/src/main/kotlin/com/paperscrape/livewallpaper/engine/RandomSceneGenerator.kt")
            .readText()
        assertTrue(
            "RandomSceneGenerator must route its vehicle types through the same cap",
            source.contains("SceneObjectCatalog.capSpecialsToOnePerType("),
        )
        assertTrue(
            "the cap must be applied after the roll, or the chaos themes' colours move with it",
            source.indexOf("hsv(rnd.nextFloat()") < source.indexOf("capSpecialsToOnePerType("),
        )
    }

    private fun repoRoot(): File {
        var dir = File(".").absoluteFile
        while (dir.parentFile != null) {
            if (File(dir, "app/src/main/kotlin").isDirectory) return dir
            dir = dir.parentFile
        }
        error("repository root not found")
    }
}
