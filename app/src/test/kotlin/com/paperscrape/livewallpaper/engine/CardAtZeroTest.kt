package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The gallery card at 0 % shows what the wallpaper shows: none** (v5.10E, inventory I-297, found on the
 * way -- the v5.10C2 round saw it for the trees -- and repaired as small, the rule of I-293 and of v5.10C's
 * row 8 carried to the card). The card read the switches alone, so a density, an intensity or a number at
 * 0 -- where the wallpaper draws none and the switch reads off -- left trees, houses, the six decorations,
 * clouds, birds, stars, rain and boats on the card. The invariant: at 0 % the card is the card with the
 * switch off. The buildings (the shops stay), the cars (one drives) and the people keep their switch alone.
 */
class CardAtZeroTest {

    private fun card(themeId: String, c: SceneCustomization, night: Boolean? = null) =
        ThemePreviewScenes.forTheme(ThemeCatalog.byId(themeId), c, forceNight = night)

    @Test
    fun `at zero the card is the card with the switch off, for everything the wallpaper draws none of`() {
        val cases: List<Triple<String, String, (SceneCustomization, Boolean) -> SceneCustomization>> = listOf(
            Triple("trees", "autumn") { c, off -> c.copy(trees = if (off) c.trees.copy(visible = false) else c.trees.copy(density = 0f)) },
            Triple("houses", "autumn") { c, off -> c.copy(houses = if (off) c.houses.copy(visible = false) else c.houses.copy(density = 0f)) },
            Triple("snowmen", "winter") { c, off -> c.copy(snowmen = if (off) c.snowmen.copy(visible = false) else c.snowmen.copy(visible = true, density = 0f)) },
            Triple("gifts", "christmas") { c, off -> c.copy(gifts = if (off) c.gifts.copy(visible = false) else c.gifts.copy(visible = true, density = 0f)) },
            Triple("penguins", "winter") { c, off -> c.copy(penguins = if (off) c.penguins.copy(visible = false) else c.penguins.copy(visible = true, density = 0f)) },
            Triple("bunnies", "easter") { c, off -> c.copy(bunnies = if (off) c.bunnies.copy(visible = false) else c.bunnies.copy(visible = true, density = 0f)) },
            Triple("eggs", "easter") { c, off -> c.copy(easterEggs = if (off) c.easterEggs.copy(visible = false) else c.easterEggs.copy(visible = true, density = 0f)) },
            Triple("pumpkins", "autumn") { c, off -> c.copy(pumpkins = if (off) c.pumpkins.copy(visible = false) else c.pumpkins.copy(visible = true, density = 0f)) },
            Triple("clouds", "spring") { c, off -> c.copy(clouds = if (off) c.clouds.copy(visible = false) else c.clouds.copy(visible = true, density = 0f)) },
            Triple("birds", "spring") { c, off -> c.copy(birds = if (off) c.birds.copy(visible = false) else c.birds.copy(visible = true, density = 0f)) },
            Triple("rain", "autumn") { c, off -> c.copy(precipitation = if (off) c.precipitation.copy(visible = false) else c.precipitation.copy(visible = true, intensity = 0f)) },
            Triple("sailboats", "beach") { c, off -> c.copy(lake = if (off) c.lake.copy(sailboatsVisible = false) else c.lake.copy(sailboatsVisible = true, sailboatsDensity = 0f)) },
            Triple("dolphins", "beach") { c, off -> c.copy(lake = if (off) c.lake.copy(dolphinsVisible = false) else c.lake.copy(dolphinsVisible = true, dolphinsDensity = 0f)) },
        )
        for ((what, themeId, set) in cases) {
            val base = defaultCustomizationFor(themeId)
            val on = card(themeId, set(base, false))
            val off = card(themeId, set(base, true))
            assertEquals("$what at 0 % on the $themeId card", off, on)
            // And the thing is on the card above 0 %, so the comparison means something.
            assertNotEquals("$what does not appear on the $themeId card at all", off, card(themeId, set(base, false).let { reraise(what, it) }))
        }
        // The stars, on a night card.
        val night = defaultCustomizationFor("halloween")
        assertEquals(
            card("halloween", night.copy(stars = night.stars.copy(visible = false)), night = true),
            card("halloween", night.copy(stars = night.stars.copy(visible = true, density = 0f)), night = true),
        )
    }

    /** The same customization with the amount set back above 0, for the "it appears at all" check. */
    private fun reraise(what: String, c: SceneCustomization): SceneCustomization = when (what) {
        "trees" -> c.copy(trees = c.trees.copy(visible = true, density = 0.6f))
        "houses" -> c.copy(houses = c.houses.copy(visible = true, density = 0.6f))
        "snowmen" -> c.copy(snowmen = c.snowmen.copy(density = 0.6f))
        "gifts" -> c.copy(gifts = c.gifts.copy(density = 0.6f))
        "penguins" -> c.copy(penguins = c.penguins.copy(density = 0.6f))
        "bunnies" -> c.copy(bunnies = c.bunnies.copy(density = 0.6f))
        "eggs" -> c.copy(easterEggs = c.easterEggs.copy(density = 0.6f))
        "pumpkins" -> c.copy(pumpkins = c.pumpkins.copy(density = 0.6f))
        "clouds" -> c.copy(clouds = c.clouds.copy(density = 0.6f))
        "birds" -> c.copy(birds = c.birds.copy(density = 0.6f))
        "rain" -> c.copy(precipitation = c.precipitation.copy(intensity = 0.6f))
        "sailboats" -> c.copy(lake = c.lake.copy(sailboatsDensity = 0.6f))
        "dolphins" -> c.copy(lake = c.lake.copy(dolphinsDensity = 0.6f))
        else -> error(what)
    }

    @Test
    fun `the shops and a car stay on the card at zero, as on the wallpaper`() {
        val c = defaultCustomizationFor("city")
        val zero = card("city", c.copy(buildings = c.buildings.copy(density = 0f), cars = c.cars.copy(density = 0f)))
        val none = card("city", c.copy(buildings = c.buildings.copy(visible = false), cars = c.cars.copy(visible = false)))
        assertTrue("the shops at 0 %", zero.items.size > none.items.size)
        assertTrue("a car at 0 %", zero.cars.isNotEmpty() && none.cars.isEmpty())
    }
}
