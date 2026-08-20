package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the structural/cosmetic split that decides whether a configuration change has to
 * rebuild scene runtime state or can be applied in place.
 *
 * This is the guarantee that stops an unrelated slider from restarting every car: only
 * `visible`/`density` can change which objects exist, so everything else must compare as
 * structurally equal.
 *
 * A false "structurally equal" would leave the scene showing objects that should have gone (a
 * visible bug), so the per-category tests below are exhaustive rather than a sample.
 */
class SceneCustomizationStructureTest {

    private val base = SceneCustomization.DEFAULT

    /** Every category, with a mutation that changes only its density. */
    private val densityMutations: List<Pair<String, (SceneCustomization) -> SceneCustomization>> = listOf(
        "houses" to { c -> c.copy(houses = c.houses.copy(density = c.houses.density / 2f + 0.1f)) },
        "buildings" to { c -> c.copy(buildings = c.buildings.copy(density = c.buildings.density / 2f + 0.1f)) },
        "cars" to { c -> c.copy(cars = c.cars.copy(density = c.cars.density / 2f + 0.1f)) },
        "parasols" to { c -> c.copy(parasols = c.parasols.copy(density = c.parasols.density / 2f + 0.1f)) },
        // People joined the categories in v76.12, for visibility and density only.
        "people" to { c -> c.copy(people = c.people.copy(density = c.people.density / 2f + 0.1f)) },
        "trees" to { c -> c.copy(trees = c.trees.copy(density = c.trees.density / 2f + 0.1f)) },
        "snowmen" to { c -> c.copy(snowmen = c.snowmen.copy(density = c.snowmen.density / 2f + 0.1f)) },
        "gifts" to { c -> c.copy(gifts = c.gifts.copy(density = c.gifts.density / 2f + 0.1f)) },
        "penguins" to { c -> c.copy(penguins = c.penguins.copy(density = c.penguins.density / 2f + 0.1f)) },
        "bunnies" to { c -> c.copy(bunnies = c.bunnies.copy(density = c.bunnies.density / 2f + 0.1f)) },
        "easterEggs" to { c -> c.copy(easterEggs = c.easterEggs.copy(density = c.easterEggs.density / 2f + 0.1f)) },
        "pumpkins" to { c -> c.copy(pumpkins = c.pumpkins.copy(density = c.pumpkins.density / 2f + 0.1f)) },
    )

    /** Every category, with a mutation that flips only its visibility. */
    private val visibilityMutations: List<Pair<String, (SceneCustomization) -> SceneCustomization>> = listOf(
        "houses" to { c -> c.copy(houses = c.houses.copy(visible = !c.houses.visible)) },
        "buildings" to { c -> c.copy(buildings = c.buildings.copy(visible = !c.buildings.visible)) },
        "cars" to { c -> c.copy(cars = c.cars.copy(visible = !c.cars.visible)) },
        "parasols" to { c -> c.copy(parasols = c.parasols.copy(visible = !c.parasols.visible)) },
        "people" to { c -> c.copy(people = c.people.copy(visible = !c.people.visible)) },
        "trees" to { c -> c.copy(trees = c.trees.copy(visible = !c.trees.visible)) },
        "snowmen" to { c -> c.copy(snowmen = c.snowmen.copy(visible = !c.snowmen.visible)) },
        "gifts" to { c -> c.copy(gifts = c.gifts.copy(visible = !c.gifts.visible)) },
        "penguins" to { c -> c.copy(penguins = c.penguins.copy(visible = !c.penguins.visible)) },
        "bunnies" to { c -> c.copy(bunnies = c.bunnies.copy(visible = !c.bunnies.visible)) },
        "easterEggs" to { c -> c.copy(easterEggs = c.easterEggs.copy(visible = !c.easterEggs.visible)) },
        "pumpkins" to { c -> c.copy(pumpkins = c.pumpkins.copy(visible = !c.pumpkins.visible)) },
    )

    /** Colour-only mutations, which must never count as structural. */
    private val colourMutations: List<Pair<String, (SceneCustomization) -> SceneCustomization>> = listOf(
        "houses" to { c -> c.copy(houses = c.houses.copy(colorDay1 = c.houses.colorDay1 xor 0x00FFFFFF)) },
        "buildings" to { c -> c.copy(buildings = c.buildings.copy(colorNight1 = c.buildings.colorNight1 xor 0x00FFFFFF)) },
        "cars" to { c -> c.copy(cars = c.cars.copy(colorDay2 = c.cars.colorDay2 xor 0x00FFFFFF)) },
        "parasols" to { c -> c.copy(parasols = c.parasols.copy(colorNight2 = c.parasols.colorNight2 xor 0x00FFFFFF)) },
        "trees" to { c -> c.copy(trees = c.trees.copy(colorDay1 = c.trees.colorDay1 xor 0x00FFFFFF)) },
        "snowmen" to { c -> c.copy(snowmen = c.snowmen.copy(colorDay1 = c.snowmen.colorDay1 xor 0x00FFFFFF)) },
        "gifts" to { c -> c.copy(gifts = c.gifts.copy(colorDay1 = c.gifts.colorDay1 xor 0x00FFFFFF)) },
        "penguins" to { c -> c.copy(penguins = c.penguins.copy(colorDay1 = c.penguins.colorDay1 xor 0x00FFFFFF)) },
        "bunnies" to { c -> c.copy(bunnies = c.bunnies.copy(colorDay1 = c.bunnies.colorDay1 xor 0x00FFFFFF)) },
        "easterEggs" to { c -> c.copy(easterEggs = c.easterEggs.copy(colorDay1 = c.easterEggs.colorDay1 xor 0x00FFFFFF)) },
        "pumpkins" to { c -> c.copy(pumpkins = c.pumpkins.copy(colorDay1 = c.pumpkins.colorDay1 xor 0x00FFFFFF)) },
    )

    // --- Identity -------------------------------------------------------------------------------

    @Test
    fun `a config is structurally equal to itself`() {
        assertTrue(base.staticStructurallyEquals(base))
        assertTrue(base.carsStructurallyEquals(base))
    }

    @Test
    fun `an unrelated copy is structurally equal`() {
        val copy = base.copy()
        assertTrue(base.staticStructurallyEquals(copy))
        assertTrue(base.carsStructurallyEquals(copy))
    }

    // --- Structural changes are detected --------------------------------------------------------

    @Test
    fun `changing any static category density is structural`() {
        for ((name, mutate) in densityMutations) {
            if (name == "cars") continue
            assertFalse(
                "changing $name density must be detected as structural",
                base.staticStructurallyEquals(mutate(base)),
            )
        }
    }

    @Test
    fun `changing any static category visibility is structural`() {
        for ((name, mutate) in visibilityMutations) {
            if (name == "cars") continue
            assertFalse(
                "changing $name visibility must be detected as structural",
                base.staticStructurallyEquals(mutate(base)),
            )
        }
    }

    @Test
    fun `changing car density or visibility is structural for cars`() {
        val densityChanged = densityMutations.first { it.first == "cars" }.second(base)
        val visibilityChanged = visibilityMutations.first { it.first == "cars" }.second(base)
        assertFalse(base.carsStructurallyEquals(densityChanged))
        assertFalse(base.carsStructurallyEquals(visibilityChanged))
    }

    // --- The separation that protects running cars ----------------------------------------------

    @Test
    fun `changing a static category does not look structural to cars`() {
        // This is what keeps cars running when an unrelated slider moves: the car list is only
        // rebuilt when the cars' own config changed.
        for ((name, mutate) in densityMutations + visibilityMutations) {
            if (name == "cars") continue
            assertTrue(
                "changing $name must not restart cars",
                base.carsStructurallyEquals(mutate(base)),
            )
        }
    }

    @Test
    fun `changing the car config does not rebuild static objects`() {
        val densityChanged = densityMutations.first { it.first == "cars" }.second(base)
        val visibilityChanged = visibilityMutations.first { it.first == "cars" }.second(base)
        assertTrue(base.staticStructurallyEquals(densityChanged))
        assertTrue(base.staticStructurallyEquals(visibilityChanged))
    }

    // --- Cosmetic changes are not structural -----------------------------------------------------

    @Test
    fun `changing any category colour is not structural`() {
        for ((name, mutate) in colourMutations) {
            val mutated = mutate(base)
            assertTrue("changing $name colour must not rebuild static objects", base.staticStructurallyEquals(mutated))
            assertTrue("changing $name colour must not restart cars", base.carsStructurallyEquals(mutated))
        }
    }

    @Test
    fun `changing seasonal palette flags is not structural`() {
        for (mutated in listOf(
            base.copy(fallColorsEnabled = !base.fallColorsEnabled),
            base.copy(winterColorsEnabled = !base.winterColorsEnabled),
            base.copy(santaEnabled = !base.santaEnabled),
        )) {
            assertTrue(base.staticStructurallyEquals(mutated))
            assertTrue(base.carsStructurallyEquals(mutated))
        }
    }

    @Test
    fun `changing hills variation is not structural`() {
        val mutated = base.copy(hillsVariation = base.hillsVariation / 2f + 0.25f)
        assertTrue(base.staticStructurallyEquals(mutated))
        assertTrue(base.carsStructurallyEquals(mutated))
    }

    @Test
    fun `changing sections this renderer does not draw is not structural`() {
        // Clouds, precipitation, stars, birds, mountains, the lake and the rainbow are drawn by
        // PaperRenderer. Their sliders used to rebuild the whole scene object renderer anyway.
        for (mutated in listOf(
            base.copy(clouds = base.clouds.copy(density = 0.123f)),
            base.copy(stars = base.stars.copy(density = 0.123f)),
            base.copy(birds = base.birds.copy(density = 0.123f)),
            base.copy(lake = base.lake.copy(height = 0.123f)),
            base.copy(precipitation = base.precipitation.copy(intensity = 0.123f)),
            base.copy(rainbow = base.rainbow.copy(opacity = 0.123f)),
            base.copy(sky = base.sky.copy(sunCloudHeight = 0.321f)),
        )) {
            assertTrue(
                "a section drawn elsewhere must not rebuild scene objects",
                base.staticStructurallyEquals(mutated),
            )
            assertTrue(
                "a section drawn elsewhere must not restart cars",
                base.carsStructurallyEquals(mutated),
            )
        }
    }

    // --- Guard against future drift ----------------------------------------------------------------

    @Test
    fun `every ObjectVariantConfig field is accounted for`() {
        // If a new category is added to SceneCustomization without being added to
        // staticStructurallyEquals/carsStructurallyEquals, changing its density would silently
        // fail to rebuild the scene. Counting the fields by reflection makes that impossible to
        // miss: this test fails until the comparison and the mutation lists above are updated.
        val configFields = SceneCustomization::class.java.declaredFields
            .filter { it.type == ObjectVariantConfig::class.java }
            .map { it.name }
        assertEquals(
            "SceneCustomization has ObjectVariantConfig fields not covered here: $configFields",
            // 13 until v2.7 removed the balloons outright -- category, sprites, toggle and all.
            12,
            configFields.size,
        )
        assertEquals(
            "every category needs a density mutation",
            configFields.size,
            densityMutations.size,
        )
        assertEquals(
            "every category needs a visibility mutation",
            configFields.size,
            visibilityMutations.size,
        )
    }

    @Test
    fun `structural comparison agrees with keepCandidate and keepCar`() {
        // The comparison is only meaningful if it predicts the filters it stands in for.
        val layout = SceneObjectCatalog.layoutFor("christmas", 0xFFE07A5F.toInt())
        for ((name, mutate) in colourMutations) {
            val mutated = mutate(base)
            assertEquals(
                "colour change to $name must not change which static objects render",
                layout.staticObjects.filter { base.keepCandidate(it) },
                layout.staticObjects.filter { mutated.keepCandidate(it) },
            )
            assertEquals(
                "colour change to $name must not change which cars render",
                layout.cars.filter { base.keepCar(it) },
                layout.cars.filter { mutated.keepCar(it) },
            )
        }
    }
}
