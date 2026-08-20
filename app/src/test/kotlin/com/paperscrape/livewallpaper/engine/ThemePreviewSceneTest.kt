package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What each built-in theme's gallery preview actually contains.
 *
 * The point of the v2.9 preview is that a user can tell twelve themes apart by looking at them, and
 * the way it goes wrong is silently: a preview that shows an object the theme does not have, or
 * misses the one object the theme is about. Both are checked here against the real
 * [defaultCustomizationFor] rather than against a copy of it, so a change to a theme's defaults
 * either shows up in its preview or fails a test.
 */
class ThemePreviewSceneTest {

    private fun sceneFor(id: String): ThemePreviewScene =
        ThemePreviewScenes.forTheme(ThemeCatalog.byId(id), defaultCustomizationFor(id))

    private fun ThemePreviewScene.allSprites(): List<Int> =
        (backdrop + items + cars + ground).flatMap { item -> item.parts.map { it.resId } }

    private fun ThemePreviewScene.contains(resId: Int) = allSprites().contains(resId)

    @Test
    fun `every built-in theme produces a scene with objects in it`() {
        for (theme in ThemeCatalog.ALL) {
            val scene = ThemePreviewScenes.forTheme(theme, defaultCustomizationFor(theme.id))
            assertTrue("${theme.id} has no objects", scene.allSprites().size >= 8)
        }
    }

    // -- the characteristic object of each theme ------------------------------------------------

    @Test
    fun `winter shows snow on the roofs, snow-capped trees and a snowman`() {
        val scene = sceneFor("winter")
        assertTrue(scene.contains(R.drawable.house_large_roof_snow))
        assertTrue(scene.contains(R.drawable.tree_canopy_snowcap))
        assertTrue(scene.contains(R.drawable.snowman_body))
    }

    @Test
    fun `winter is not christmas - no gifts and no fairy lights`() {
        val scene = sceneFor("winter")
        assertFalse(scene.contains(R.drawable.gift_box))
        assertFalse(scene.contains(R.drawable.star_sparkle))
    }

    @Test
    fun `christmas shows firs with lights and gifts on top of the winter presentation`() {
        val scene = sceneFor("christmas")
        assertTrue(scene.contains(R.drawable.tree_fir))
        assertTrue(scene.contains(R.drawable.star_sparkle))
        assertTrue(scene.contains(R.drawable.gift_box))
        assertTrue(scene.contains(R.drawable.house_large_roof_snow))
    }

    @Test
    fun `halloween strips the trees, carves the moon and keeps the horror sky`() {
        val scene = sceneFor("halloween")
        assertTrue(scene.contains(R.drawable.tree_dead_branches))
        assertFalse(scene.contains(R.drawable.tree_canopy))
        assertTrue(scene.contains(R.drawable.moon_jack_o_lantern))
        assertTrue(scene.contains(R.drawable.pumpkin_body))
        // HORROR_SKY_TOP_DAY / HORROR_SKY_LOW_NIGHT, not the theme's own sky.
        assertEquals(0xFF07060A.toInt(), scene.skyTop)
        assertEquals(0xFFB03A06.toInt(), scene.skyBottom)
    }

    @Test
    fun `the carved moon is tinted orange rather than with the theme's moon colour`() {
        val moon = sceneFor("halloween").backdrop
            .flatMap { it.parts }
            .first { it.resId == R.drawable.moon_jack_o_lantern }
        assertEquals(0xFFFF8C2A.toInt(), moon.tint) // PaperRenderer.HALLOWEEN_MOON_COLOUR
    }

    @Test
    fun `autumn turns the leaves and carries its pumpkins`() {
        val scene = sceneFor("autumn")
        assertTrue(scene.contains(R.drawable.tree_canopy))
        assertTrue(scene.contains(R.drawable.pumpkin_body))
        // The canopies wear the renderer's own fall palette, not the trees' configured colour.
        val canopyTints = scene.items.flatMap { it.parts }.filter { it.resId == R.drawable.tree_canopy }.map { it.tint }
        assertTrue(canopyTints.contains(0xFFD2691E.toInt()))
    }

    @Test
    fun `beach has water with boats and dolphins, palms, and no mountains`() {
        val scene = sceneFor("beach")
        assertTrue(scene.hasLake)
        assertTrue(scene.contains(R.drawable.sailboat_sail))
        assertTrue(scene.contains(R.drawable.dolphin_body))
        assertTrue(scene.contains(R.drawable.palmtree_fronds))
        assertTrue(scene.peaks.isEmpty())
    }

    @Test
    fun `desert has palms and dunes but no water`() {
        val scene = sceneFor("desert")
        assertFalse(scene.hasLake)
        assertTrue(scene.contains(R.drawable.palmtree_fronds))
        assertTrue(scene.peaks.isNotEmpty())
        assertTrue("desert's horizon must be dunes", scene.peaks.all { it.dune })
    }

    @Test
    fun `tundra has penguins and a lake with nothing sailing on it`() {
        val scene = sceneFor("tundra")
        assertTrue(scene.contains(R.drawable.penguin_body))
        assertTrue(scene.contains(R.drawable.snowman_body))
        assertTrue(scene.hasLake)
        assertFalse(scene.contains(R.drawable.sailboat_sail))
        assertFalse(scene.contains(R.drawable.dolphin_body))
    }

    @Test
    fun `easter has bunnies and eggs and no flowers`() {
        val scene = sceneFor("easter")
        assertTrue(scene.contains(R.drawable.bunny_body))
        assertTrue(scene.contains(R.drawable.easteregg_shell))
        assertFalse(scene.contains(R.drawable.ground_flowers))
    }

    @Test
    fun `spring is the theme with flowers`() {
        val scene = sceneFor("spring")
        assertTrue(scene.contains(R.drawable.ground_flowers))
        assertTrue(scene.hasLake)
        assertFalse(scene.contains(R.drawable.bunny_body))
    }

    @Test
    fun `new year is a night scene with fireworks`() {
        val scene = sceneFor("new_year")
        assertTrue(scene.contains(R.drawable.firework))
        assertTrue(scene.contains(R.drawable.moon_full))
        assertEquals(ThemeCatalog.byId("new_year").skyNight.first(), scene.skyTop)
        assertFalse("New Year is not Christmas", scene.contains(R.drawable.gift_box))
    }

    @Test
    fun `city is built rather than settled`() {
        val scene = sceneFor("city")
        val towers = scene.items.count { item -> item.parts.any { it.resId == R.drawable.skyscraper_wall } }
        assertTrue("expected a skyline, got $towers towers", towers >= 4)
        assertTrue(scene.peaks.isEmpty())
        assertFalse(scene.contains(R.drawable.house_large_wall))
    }

    @Test
    fun `sunset shows its own dusk sky`() {
        val theme = ThemeCatalog.byId("sunset")
        val scene = sceneFor("sunset")
        assertEquals(theme.skyDusk.first(), scene.skyTop)
        assertEquals(theme.skyDusk.last(), scene.skyBottom)
        assertTrue(scene.contains(R.drawable.sun_body))
    }

    // -- nothing a theme does not have ----------------------------------------------------------

    @Test
    fun `no preview draws a lake the theme has turned off`() {
        for (theme in ThemeCatalog.ALL) {
            val customization = defaultCustomizationFor(theme.id)
            val scene = ThemePreviewScenes.forTheme(theme, customization)
            assertEquals("${theme.id} lake", customization.lake.visible, scene.hasLake)
            if (!customization.lake.visible) {
                assertFalse("${theme.id} draws boats without a lake", scene.contains(R.drawable.sailboat_sail))
            }
        }
    }

    @Test
    fun `no preview draws a decoration the theme has turned off`() {
        val decorations = mapOf(
            R.drawable.snowman_body to { c: SceneCustomization -> c.snowmen.visible },
            R.drawable.gift_box to { c: SceneCustomization -> c.gifts.visible },
            R.drawable.penguin_body to { c: SceneCustomization -> c.penguins.visible },
            R.drawable.bunny_body to { c: SceneCustomization -> c.bunnies.visible },
            R.drawable.easteregg_shell to { c: SceneCustomization -> c.easterEggs.visible },
            R.drawable.pumpkin_body to { c: SceneCustomization -> c.pumpkins.visible },
            R.drawable.ground_flowers to { c: SceneCustomization -> c.flowersEnabled },
        )
        for (theme in ThemeCatalog.ALL) {
            val customization = defaultCustomizationFor(theme.id)
            val scene = ThemePreviewScenes.forTheme(theme, customization)
            for ((resId, isOn) in decorations) {
                if (!isOn(customization)) {
                    assertFalse("${theme.id} draws a decoration it has off", scene.contains(resId))
                }
            }
        }
    }

    @Test
    fun `every preview fits inside its own coordinate space`() {
        for (theme in ThemeCatalog.ALL) {
            val scene = ThemePreviewScenes.forTheme(theme, defaultCustomizationFor(theme.id))
            for (item in scene.backdrop + scene.items + scene.cars + scene.ground) {
                assertTrue("${theme.id} object off the left edge", item.x >= -10f)
                assertTrue("${theme.id} object off the right edge", item.x <= ThemePreviewScene.WIDTH_UNITS + 10f)
                assertTrue("${theme.id} object below the card", item.y <= ThemePreviewScene.HEIGHT_UNITS)
            }
        }
    }

    @Test
    fun `a customised theme's preview follows the customization, not the built-in default`() {
        val theme = ThemeCatalog.byId("sunset")
        val withPumpkins = defaultCustomizationFor("sunset").let { base ->
            base.copy(pumpkins = base.pumpkins.copy(visible = true))
        }
        val scene = ThemePreviewScenes.forTheme(theme, withPumpkins)
        assertTrue(scene.contains(R.drawable.pumpkin_body))
        assertFalse(sceneFor("sunset").contains(R.drawable.pumpkin_body))
    }
}
