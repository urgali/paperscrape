package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every built-in theme has to present a scene that is internally consistent.
 *
 * These are not style opinions. Each assertion below names a combination that was actually
 * shipping and that nobody would defend if asked: beach umbrellas standing in snow, dolphins in
 * the Arctic, an Autumn theme with midsummer foliage, a winter village whose roofs are bare and
 * whose people are in shorts because the seasonal presentation flag defaulted to off.
 *
 * The point of pinning them is that every one is a *default*, and a default is invisible until
 * someone installs the app fresh — there is no test a running build fails.
 */
class BuiltInThemeCoherenceTest {

    private val winterThemes = listOf("winter", "christmas", "tundra")
    private val warmThemes = listOf("sunset", "beach", "desert", "easter", "city")

    private fun defaults(id: String) = defaultCustomizationFor(id)

    @Test
    fun `every built-in theme resolves and has defaults`() {
        assertTrue("the catalogue should not be empty", ThemeCatalog.ALL.isNotEmpty())
        ThemeCatalog.ALL.forEach { theme ->
            assertNotNull("${theme.id} has no defaults", defaults(theme.id))
            assertEquals("byId must round-trip ${theme.id}", theme.id, ThemeCatalog.byId(theme.id).id)
        }
    }

    @Test
    fun `the winter themes actually present as winter`() {
        // Drives tree snow caps, roof snow and winter clothing. Off, a "winter" theme is a
        // summer scene standing on white ground.
        winterThemes.forEach { id ->
            assertTrue("$id must enable the winter presentation", defaults(id).winterColorsEnabled)
        }
    }

    // --- winter and christmas are two flags, not one -------------------------------------------

    @Test
    fun `winter is the season and christmas is the decoration`() {
        // The whole point of the split. `winter` says snow settled and people dressed for it;
        // `christmas` says lights went up. They were one flag, which made a plain snowy January
        // impossible -- every winter tree came with fairy lights -- and made Christmas cost a
        // full winter presentation whether you wanted one or not.
        val winter = defaults("winter")
        assertTrue("Winter must present as winter", winter.winterColorsEnabled)
        assertFalse("Winter must not be Christmas", winter.christmasDecorationsEnabled)

        val christmas = defaults("christmas")
        assertTrue("Christmas is in winter", christmas.winterColorsEnabled)
        assertTrue("Christmas must have its decorations", christmas.christmasDecorationsEnabled)

        val newYear = defaults("new_year")
        assertTrue("New Year is in winter", newYear.winterColorsEnabled)
        assertFalse("New Year is not a second Christmas", newYear.christmasDecorationsEnabled)
    }

    @Test
    fun `neither flag implies the other`() {
        // All four combinations have to be reachable, because each is a scene somebody might
        // want. Asserted on the data class rather than through a theme, since the themes only
        // happen to use three of the four.
        val none = SceneCustomization.DEFAULT.copy(winterColorsEnabled = false, christmasDecorationsEnabled = false)
        val winterOnly = SceneCustomization.DEFAULT.copy(winterColorsEnabled = true, christmasDecorationsEnabled = false)
        val christmasOnly = SceneCustomization.DEFAULT.copy(winterColorsEnabled = false, christmasDecorationsEnabled = true)
        val both = SceneCustomization.DEFAULT.copy(winterColorsEnabled = true, christmasDecorationsEnabled = true)

        assertFalse(none.winterColorsEnabled); assertFalse(none.christmasDecorationsEnabled)
        assertTrue(winterOnly.winterColorsEnabled); assertFalse(winterOnly.christmasDecorationsEnabled)
        assertFalse("Christmas must not switch winter on", christmasOnly.winterColorsEnabled)
        assertTrue(christmasOnly.christmasDecorationsEnabled)
        assertTrue(both.winterColorsEnabled); assertTrue(both.christmasDecorationsEnabled)
    }

    @Test
    fun `the christmas layer is independent of both seasonal palettes`() {
        // Fall and Winter Colors are two readings of the same leaves and exclude each other.
        // Lights are hung on top of whatever the trees look like, so they exclude nothing --
        // including autumn, which is unusual but not contradictory.
        val autumnWithLights = SceneCustomization.DEFAULT.copy(
            fallColorsEnabled = true,
            christmasDecorationsEnabled = true,
        )
        assertTrue(autumnWithLights.fallColorsEnabled)
        assertTrue(autumnWithLights.christmasDecorationsEnabled)
    }

    @Test
    fun `christmas decorations stay out of the themes that are not christmas`() {
        ThemeCatalog.ALL.filter { it.id != "christmas" }.forEach { theme ->
            assertFalse(
                "${theme.id} should not hang Christmas lights",
                defaults(theme.id).christmasDecorationsEnabled,
            )
        }
    }

    @Test
    fun `no theme enables two seasonal palettes at once`() {
        // A tree cannot be shedding red leaves and be snow-dusted at the same time. The
        // preference setters enforce this for user edits; the defaults must not violate it
        // before the user has touched anything.
        ThemeCatalog.ALL.forEach { theme ->
            val c = defaults(theme.id)
            assertFalse(
                "${theme.id} enables both Fall Colors and Winter Colors",
                c.fallColorsEnabled && c.winterColorsEnabled,
            )
        }
    }

    @Test
    fun `autumn presents as autumn`() {
        val autumn = defaults("autumn")
        assertTrue("autumn must enable Fall Colors", autumn.fallColorsEnabled)
        assertTrue("autumn should carry its own decoration", autumn.pumpkins.visible)
        assertFalse("autumn must not present as winter", autumn.winterColorsEnabled)
    }

    @Test
    fun `shade umbrellas do not stand in the snow`() {
        winterThemes.forEach { id ->
            assertFalse("$id shows beach umbrellas", defaults(id).parasols.visible)
        }
        assertFalse("new_year is a night scene", defaults("new_year").parasols.visible)
        // And they are still there where they belong, so this has not been fixed by deleting
        // the category everywhere.
        assertTrue("beach must keep its umbrellas", defaults("beach").parasols.visible)
    }

    @Test
    fun `the tundra lake carries no yachts or dolphins`() {
        val lake = defaults("tundra").lake
        assertTrue("tundra should have its meltwater", lake.visible)
        assertFalse("a sailboat in the Arctic", lake.sailboatsVisible)
        assertFalse("dolphins in the Arctic", lake.dolphinsVisible)
        // Beach is the theme these belong to, and still has them.
        val beach = defaults("beach").lake
        assertTrue(beach.sailboatsVisible)
        assertTrue(beach.dolphinsVisible)
    }

    @Test
    fun `christmas-only decorations stay in christmas`() {
        assertTrue("christmas must have Santa", defaults("christmas").santaEnabled)
        assertTrue("christmas must have presents", defaults("christmas").gifts.visible)
        ThemeCatalog.ALL.filter { it.id != "christmas" }.forEach { theme ->
            val c = defaults(theme.id)
            assertFalse("${theme.id} should not fly Santa", c.santaEnabled)
            assertFalse("${theme.id} should not scatter presents", c.gifts.visible)
        }
    }

    @Test
    fun `easter-only decorations stay in easter`() {
        val easter = defaults("easter")
        assertTrue(easter.bunnies.visible)
        assertTrue(easter.easterEggs.visible)
        ThemeCatalog.ALL.filter { it.id != "easter" }.forEach { theme ->
            val c = defaults(theme.id)
            assertFalse("${theme.id} should not have Easter eggs", c.easterEggs.visible)
            assertFalse("${theme.id} should not have Easter bunnies", c.bunnies.visible)
        }
    }

    @Test
    fun `pumpkins stay in the two themes that are about pumpkins`() {
        // Halloween joined Autumn here rather than being excused from the rule. A pumpkin is the
        // one object that says Halloween, and a theme that needs it switched on by hand in a menu
        // before it reads as Halloween is a theme that does not work. Every other theme still has
        // to leave them off.
        val pumpkinThemes = setOf("autumn", "halloween")
        ThemeCatalog.ALL.filter { it.id !in pumpkinThemes }.forEach { theme ->
            assertFalse("${theme.id} should not have pumpkins", defaults(theme.id).pumpkins.visible)
        }
        pumpkinThemes.forEach { id ->
            assertTrue("$id should have pumpkins", defaults(id).pumpkins.visible)
        }
    }

    @Test
    fun `snow decorations only appear where there is snow`() {
        // Snowmen and penguins are winter objects; a snowman on a beach is the same class of
        // error as an umbrella in the snow, in the other direction.
        warmThemes.forEach { id ->
            assertFalse("$id should not have snowmen", defaults(id).snowmen.visible)
            assertFalse("$id should not have penguins", defaults(id).penguins.visible)
        }
        winterThemes.forEach { id ->
            assertTrue("$id should have snowmen", defaults(id).snowmen.visible)
        }
    }

    @Test
    fun `the desert has no water`() {
        assertFalse("a lake in the desert", defaults("desert").lake.visible)
    }

    @Test
    fun `the beach stands on sand, not on the sea`() {
        // The ground took `hillColorsDay[0]`, which for Beach is the water colour: the shore
        // rendered teal. Only entry 0 of that array is read since the scene dropped to one hill
        // layer, so the sand tones behind it were never reachable.
        assertEquals(0xFFEFD9A3.toInt(), defaults("beach").hillsColorDay)
    }

    @Test
    fun `the palm themes get palms and nobody else does`() {
        val palmThemes = setOf("beach", "desert")
        ThemeCatalog.ALL.forEach { theme ->
            val types = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor)
                .staticObjects.map { it.type }.toSet()
            if (theme.id in palmThemes) {
                assertTrue("${theme.id} should have palms", SceneObjectType.PALM_TREE in types)
                assertFalse("${theme.id} should not mix in broadleaf trees", SceneObjectType.TREE in types)
            } else {
                assertTrue("${theme.id} should have trees", SceneObjectType.TREE in types)
                assertFalse("${theme.id} should not have palms", SceneObjectType.PALM_TREE in types)
            }
        }
    }

    @Test
    fun `the city is built rather than settled`() {
        val city = defaults("city")
        assertTrue("a city needs its buildings", city.buildings.density > city.houses.density)
    }

    @Test
    fun `every theme that presets precipitation presets snow, never rain`() {
        // Weather is opt-in almost everywhere, on the same reasoning as the lake. What must be
        // right either way is the *type* a cold theme starts from.
        (winterThemes + "new_year").forEach { id ->
            assertEquals("$id should start from Snow", PrecipitationType.SNOW, defaults(id).precipitation.type)
        }
    }

    @Test
    fun `the two snow themes actually snow`() {
        // The exception to opt-in weather, and a deliberate one: a theme called Winter whose
        // weather is off is a theme whose central subject the user has to go and find in a menu.
        listOf("winter", "christmas").forEach { id ->
            val p = defaults(id).precipitation
            assertTrue("$id should be snowing", p.visible)
            assertEquals(PrecipitationType.SNOW, p.type)
        }
        // And it stays an exception: nothing warm rains or snows out of the box.
        warmThemes.forEach { id ->
            assertFalse("$id should not have weather by default", defaults(id).precipitation.visible)
        }
    }
}
