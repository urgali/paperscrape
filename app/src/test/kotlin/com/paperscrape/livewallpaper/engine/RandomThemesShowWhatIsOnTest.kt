package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **On a random theme a switch on shows its thing as the theme ships** (`DESIGN_NOTES.md` §12, decisions 97 and 102): the
 * maintainer's *«12 - pochi alberi non può significare zero; correggiamo i numeri»* (2026-11-07) for the trees, and for the
 * houses and the parasols *«D2 no: correggiamo i numeri come per gli alberi»* (2026-11-08). On every random theme the JVM
 * builds, at its starting amounts, with *Show Houses*, *Show Parasols* or *Show Trees* on -- as it ships, or turned on where
 * the theme was born without them and starts them off -- at least one of them stands on a tile of the street, after the
 * rules that keep a place's front clear ([SceneObjectCatalog.withEveryCategory]).
 *
 * Counted as the wallpaper keeps them ([SceneCustomization.keepCandidate] over the laid street), not as candidates: a
 * category above 0 % can keep none of its candidates.
 */
class RandomThemesShowWhatIsOnTest {

    /** A random theme's starting settings, a built-in's colours standing in for its own (they need Android's HSV). */
    private fun randomDefaults(id: String) = defaultCustomizationFor(id, ThemeCatalog.byId("autumn").copy(id = id))

    private fun SceneObjectLayout.standing(c: SceneCustomization, vararg types: SceneObjectType): Int =
        staticObjects.count { it.type in types && c.keepCandidate(it, densityScheme) }

    @Test
    fun `a switch on shows at least one on every tile, houses, parasols and trees`() {
        var read = 0
        var housesOn = 0
        var parasolsOn = 0
        for (n in 1..400) {
            val id = "random:$n"
            val street = runCatching { SceneObjectCatalog.layoutFor(id, 0) }.getOrNull() ?: continue
            read++
            val c = randomDefaults(id)
            if (c.houses.visible) housesOn++
            if (c.parasols.visible) parasolsOn++
            val on = c.copy(houses = c.houses.copy(visible = true), parasols = c.parasols.copy(visible = true))
            assertTrue("$id: Show Houses on, no house stands", street.standing(on, SceneObjectType.HOUSE) >= 1)
            assertTrue("$id: Show Parasols on, no parasol stands", street.standing(on, SceneObjectType.PARASOL) >= 1)
            assertTrue("random themes start with Show Trees on", c.trees.visible)
            assertTrue("$id: Show Trees on, no tree stands", street.standing(c, SceneObjectType.TREE, SceneObjectType.PALM_TREE) >= 1)
        }
        assertTrue("random themes read: $read", read >= 250)
        // A theme whose own trees and the ten it is given keep none: another seed's ten (decision 97).
        val rare = SceneObjectCatalog.layoutFor("random:3993", 0)
        assertTrue("random:3993: Show Trees on, no tree stands", rare.standing(randomDefaults("random:3993"), SceneObjectType.TREE, SceneObjectType.PALM_TREE) >= 1)
        assertTrue("themes with Show Houses on: $housesOn", housesOn >= 80)
        assertTrue("themes with Show Parasols on: $parasolsOn", parasolsOn >= 80)
    }

    /**
     * Only a theme whose own keep none at the factory amount is given more, and what it was born with stays: the others'
     * houses and parasols are their own.
     */
    @Test
    fun `only the themes whose own keep none are given houses or parasols`() {
        var given = 0
        for (n in 1..400) {
            val id = "random:$n"
            val street = runCatching { SceneObjectCatalog.layoutFor(id, 0) }.getOrNull() ?: continue
            val born = RandomSceneGenerator.born(id).staticObjects
            assertTrue("$id: its own houses and parasols stay", street.staticObjects.containsAll(born.filter { it.type == SceneObjectType.HOUSE || it.type == SceneObjectType.PARASOL }))
            for (type in listOf(SceneObjectType.HOUSE, SceneObjectType.PARASOL)) {
                val own = born.filter { it.type == type }
                if (own.isEmpty()) continue
                val laid = street.staticObjects.count { it.type == type }
                val ownStand = own.any { SceneCustomization.DEFAULT.keepCandidate(it, street.densityScheme) }
                if (ownStand) assertTrue("$id: its own $type stand, none given", laid == own.size) else if (laid > own.size) given++
            }
        }
        assertTrue("themes given houses or parasols: $given", given >= 40)
    }
}
