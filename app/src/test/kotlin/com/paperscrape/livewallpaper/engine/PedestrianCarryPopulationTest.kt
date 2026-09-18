package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The rule, on the streets this app actually builds.**
 *
 * [PedestrianCarryTest] checks [PedestrianCarry]'s predicates on arguments it invents. This checks
 * the rule on the twelve built-in themes' own populations, which is what the maintainer looks at.
 *
 * ### What this file used to assert, and why it does not any more
 *
 * It was written for v5.4's first pass, against a **share**: two adults in three, and the two
 * assertions at its centre were *"no street is bare-headed"* and *"no street is in uniform"*. The
 * second of those is now exactly what the maintainer asked for. The instruction is *"in the rain,
 * every pedestrian who walks has an umbrella"*, so the uniform is the specification and the test
 * that forbade it has been turned around rather than deleted: `every walking figure carries` below
 * is the same sweep over the same twelve themes and five densities, asserting the opposite.
 *
 * The share's own measurements are gone with it. They are kept in one place only -- `V5_4E_REPORT.md`
 * -- because a table of what two thirds gave on each theme is now history and not a contract.
 *
 * ### Why the host, and what it cannot say
 *
 * Who carries is arithmetic over the population, so every theme at every density costs milliseconds
 * here instead of half an hour of phone. What it cannot say is whether a canopy becomes pixels:
 * that is `PedestrianUmbrellaSceneTest`, on the device, counting umbrellas against heads.
 */
class PedestrianCarryPopulationTest {

    private val themes = listOf(
        "sunset", "autumn", "winter", "desert", "christmas", "new_year",
        "beach", "city", "tundra", "easter", "halloween", "spring",
    )

    private val densities = listOf(1f, 0.75f, 0.5f, 0.25f)

    /** One street: how many walkers it has, and how many of them the rule puts an umbrella on. */
    private class Street(val walkers: Int, val carrying: Int, val canHold: Int)

    private fun street(themeId: String, density: Float): Street {
        val population = PedestrianPopulation.build(
            seed = themeId.hashCode(),
            density = density,
            nearRowYFraction = SceneSpace.PAVEMENT_NEAR_Y_FRACTION,
            farRowYFraction = SceneSpace.PAVEMENT_FAR_Y_FRACTION,
        )
        var walkers = 0
        var carrying = 0
        var canHold = 0
        for (person in population) {
            walkers++
            val holds = PedestrianCarry.canHold(person.kindIndex)
            if (holds) canHold++
            // Exactly the expression `drawPeople` evaluates for `wanted`, in the rain.
            if (PedestrianCarry.wantsUmbrella(raining = true, canHold = holds)) carrying++
        }
        return Street(walkers, carrying, canHold)
    }

    private fun everyStreet(block: (themeId: String, density: Float, street: Street) -> Unit) {
        for (themeId in themes) {
            val own = defaultCustomizationFor(themeId).people.density
            for (density in listOf(own) + densities) block(themeId, density, street(themeId, density))
        }
    }

    /**
     * **The maintainer's rule as an assertion: in the rain, every walking figure that can hold an
     * umbrella is holding one.** No theme, at any density, leaves one out.
     *
     * This is the assertion the share failed. It is stated over *walkers who can hold one* rather
     * than over all walkers because that is the shape of the rule -- and since v5.4H drew the two
     * child poses, `canHold` is true of **every** family the scene walks, so the two counts are the
     * same street. The next test is the one that pins that, so this file cannot quietly satisfy
     * itself by counting a smaller street.
     */
    @Test
    fun `every walking figure that can hold one carries it, on every theme and every density`() {
        val missed = mutableListOf<String>()
        everyStreet { themeId, density, s ->
            if (s.carrying != s.canHold) {
                missed += "$themeId at density $density: ${s.canHold} could carry, ${s.carrying} do"
            }
        }
        assertTrue("a walker in the rain with no umbrella: $missed", missed.isEmpty())
    }

    /**
     * **How many walkers the rule leaves bare-headed: none, and that is the whole of v5.4H.**
     *
     * v5.4E shipped this as *"the walkers left bare-headed are exactly the children, and this is
     * how many"*: **94** figures on the twelve built-in pavements at their own default densities,
     * of whom **46** had a carry pose and **48** did not, so the maintainer's rule reached **49 %**
     * of the street. That number was the cost of the artwork gap, asserted rather than described
     * precisely so that it could not change quietly -- and it has changed, so this went red and was
     * rewritten rather than edited.
     *
     * It now reads **94 of 94**. The children are still 48 of the 94 and still the majority of the
     * street; what changed is that they have a pose, so `canHold` is true of them. `spring`, the
     * theme v5.4D photographed and the sparsest of the twelve, goes from **2 umbrellas over 7
     * figures** to **7 over 7**.
     *
     * The children are still counted separately and still asserted at 48. They are not the
     * bare-headed any more, but they are the half of the street this pass was about, and a redraw
     * that changed how many children the population deals should say so here.
     */
    @Test
    fun `nobody is left bare-headed any more, and the children are still half the street`() {
        var walkers = 0
        var canHold = 0
        var children = 0
        for (themeId in themes) {
            val s = street(themeId, defaultCustomizationFor(themeId).people.density)
            walkers += s.walkers
            canHold += s.canHold
            val population = PedestrianPopulation.build(
                seed = themeId.hashCode(),
                density = defaultCustomizationFor(themeId).people.density,
                nearRowYFraction = SceneSpace.PAVEMENT_NEAR_Y_FRACTION,
                farRowYFraction = SceneSpace.PAVEMENT_FAR_Y_FRACTION,
            )
            children += population.count { it.age == PersonAge.CHILD }
        }
        assertEquals("figures on the twelve built-in pavements", 94, walkers)
        assertEquals("every one of whom has a carry pose to hold an umbrella in", 94, canHold)
        assertEquals("so nobody is left out by the artwork", 0, walkers - canHold)
        assertEquals("and the children are still this many of them", 48, children)
    }

    /**
     * Nobody carries anything when it is not raining, over the same sweep -- the direction a rule
     * that simply painted canopies on everybody would break, and the one the maintainer did not
     * ask to change.
     */
    @Test
    fun `a dry street carries nothing, on every theme and every density`() {
        val wrong = mutableListOf<String>()
        everyStreet { themeId, density, _ ->
            val population = PedestrianPopulation.build(
                seed = themeId.hashCode(),
                density = density,
                nearRowYFraction = SceneSpace.PAVEMENT_NEAR_Y_FRACTION,
                farRowYFraction = SceneSpace.PAVEMENT_FAR_Y_FRACTION,
            )
            val carrying = population.count {
                PedestrianCarry.wantsUmbrella(
                    raining = false,
                    canHold = PedestrianCarry.canHold(it.kindIndex),
                )
            }
            if (carrying != 0) wrong += "$themeId at density $density: $carrying in a clear sky"
        }
        assertTrue("an umbrella with no rain: $wrong", wrong.isEmpty())
    }

    /**
     * **Thinning the street never takes an umbrella off a walker who stays.**
     *
     * Under the share this needed an argument -- the share was a rank among the adults present, so
     * moving the People slider moved a boundary through the street. It is now trivially true, and
     * that is worth pinning rather than assuming: a walker's umbrella depends on the weather and on
     * its own family, and on nothing about who else is on the pavement.
     */
    @Test
    fun `thinning the street never disarms a walker who stays`() {
        for (themeId in themes) {
            val full = PedestrianPopulation.build(
                seed = themeId.hashCode(),
                density = 1f,
                nearRowYFraction = SceneSpace.PAVEMENT_NEAR_Y_FRACTION,
                farRowYFraction = SceneSpace.PAVEMENT_FAR_Y_FRACTION,
            ).associate { p ->
                (p.groupIndex * PedestrianPopulation.MAX_GROUP_SIZE + p.memberIndex) to
                    PedestrianCarry.wantsUmbrella(true, PedestrianCarry.canHold(p.kindIndex))
            }
            for (density in densities) {
                PedestrianPopulation.build(
                    seed = themeId.hashCode(),
                    density = density,
                    nearRowYFraction = SceneSpace.PAVEMENT_NEAR_Y_FRACTION,
                    farRowYFraction = SceneSpace.PAVEMENT_FAR_Y_FRACTION,
                ).forEach { p ->
                    val address = p.groupIndex * PedestrianPopulation.MAX_GROUP_SIZE + p.memberIndex
                    val thinned =
                        PedestrianCarry.wantsUmbrella(true, PedestrianCarry.canHold(p.kindIndex))
                    assertEquals(
                        "$themeId: walker $address changed hands when the slider moved to $density",
                        full[address], thinned,
                    )
                }
            }
        }
    }
}
