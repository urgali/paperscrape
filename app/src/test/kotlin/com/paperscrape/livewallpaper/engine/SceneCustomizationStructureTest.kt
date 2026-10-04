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
    fun `changing the palms switch is structural for static objects and not for cars`() {
        // The switch changes which species a kept tree slot is, and that is decided once, when
        // the static runtime list is built (`palmSpeciesApplied`). Until v5.8 this comparison
        // did not read it, so turning the palms off on a running wallpaper changed nothing until
        // something else rebuilt the scene -- measured on a device (assessment v5.7, M1).
        val mutated = base.copy(palmsEnabled = !base.palmsEnabled)
        assertFalse("the palms switch must rebuild the static objects", base.staticStructurallyEquals(mutated))
        assertTrue("the palms switch must not restart cars", base.carsStructurallyEquals(mutated))
        // Its other half since v5.10C, the palms in the trees' places on a theme that plants none:
        // the same rule, or turning palms on over Christmas would change nothing until the scene was
        // rebuilt for some other reason.
        val other = base.copy(palmsInsteadOfTrees = !base.palmsInsteadOfTrees)
        assertFalse("palms in place of trees must rebuild the static objects", base.staticStructurallyEquals(other))
        assertTrue("and must not restart cars", base.carsStructurallyEquals(other))
    }

    @Test
    fun `the Christmas layer is structural only while palms stand in the trees' places`() {
        // v5.10C2: with palms in the trees' places a fir stays a tree (`palmSpeciesApplied`), and which
        // slots are firs is the Christmas layer's -- so turning the lights on or off over such a scene
        // changes which slots are palms, and has to rebuild the list, or the firs would come and go only
        // when something else rebuilt it. Without those palms the fir is decided at the draw and the
        // layer must not rebuild anything: nothing changes for a scene that has no palm in a tree slot.
        for (palms in listOf(false, true)) {
            val start = base.copy(palmsInsteadOfTrees = palms)
            val lit = start.copy(christmasDecorationsEnabled = !start.christmasDecorationsEnabled)
            assertEquals("palms in place of trees $palms", !palms, start.staticStructurallyEquals(lit))
            assertEquals(!palms, lit.staticStructurallyEquals(start))
            assertTrue("the lights must not restart cars", start.carsStructurallyEquals(lit))
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
        // The comparison stands in for the filters: whenever it says "equal", the scene must keep
        // the same objects -- and draw the same species in a palm slot -- or a change reaches the
        // settings and never the running wallpaper, which is what the Palms switch did in v5.7.
        //
        // **Until v5.8B this test called neither comparison.** It ran the colour mutations through
        // `keepCandidate` and `keptCars` and asserted the filters did not move, which is true of
        // colours whatever `staticStructurallyEquals` says, so the name promised an agreement it
        // never checked -- and the palm switch, missing from the comparison, passed it. Now every
        // mutation family, on every built-in, is run through both comparisons, and each "equal" is
        // checked against what the engine really keeps: `keepCandidate` plus `palmSpeciesApplied`
        // for the static objects, and for the cars `CarSelection.countFor` at both ends of the day
        // (the engine rebuilds its cars only on a visibility flip and counts the rest per frame).
        val palms = "palms" to { c: SceneCustomization -> c.copy(palmsEnabled = !c.palmsEnabled) }
        val palmsForTrees = "palms in place of trees" to { c: SceneCustomization -> c.copy(palmsInsteadOfTrees = !c.palmsInsteadOfTrees) }
        val nightCars = "cars at night" to { c: SceneCustomization -> c.copy(carsNightDensity = c.carsNightDensity / 2f + 0.1f) }
        // v5.10C2: the Christmas layer decides which tree slots stay firs among the palms.
        val lights = "Christmas lights" to { c: SceneCustomization -> c.copy(christmasDecorationsEnabled = !c.christmasDecorationsEnabled) }
        val mutations = densityMutations + visibilityMutations + colourMutations + palms + palmsForTrees + nightCars + lights
        var equalStatic = 0
        var equalCars = 0
        // Every built-in as it ships, and since v5.10C2 with palms in its trees' places as well, where
        // the Christmas layer is structural.
        for ((theme, start) in ThemeCatalog.ALL.flatMap { theme ->
            val shipped = defaultCustomizationFor(theme.id)
            listOf(theme to shipped, theme to shipped.copy(palmsInsteadOfTrees = true))
        }) {
            val layout = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor)
            fun standing(c: SceneCustomization) =
                layout.staticObjects.filter { c.keepCandidate(it) }.map { c.palmSpeciesApplied(it, layout.hasPalmSlots()) }
            fun traffic(c: SceneCustomization) = Triple(
                c.cars.visible,
                CarSelection.countFor(c.cars.density, layout.cars.size),
                CarSelection.countFor(c.carsNightDensity, layout.cars.size),
            )
            for ((name, mutate) in mutations) {
                val mutated = mutate(start)
                if (start.staticStructurallyEquals(mutated)) {
                    equalStatic++
                    assertEquals("${theme.id}, $name: called equal, so the same objects must stand", standing(start), standing(mutated))
                }
                if (start.carsStructurallyEquals(mutated)) {
                    equalCars++
                    assertEquals("${theme.id}, $name: called equal, so the same traffic must run", traffic(start), traffic(mutated))
                }
            }
        }
        // Both comparisons must have said "equal" often enough for the check to mean something: the
        // colour mutations alone are eleven per theme.
        assertTrue("static comparisons called equal: $equalStatic", equalStatic >= 11 * ThemeCatalog.ALL.size)
        assertTrue("car comparisons called equal: $equalCars", equalCars >= 11 * ThemeCatalog.ALL.size)
    }
}
