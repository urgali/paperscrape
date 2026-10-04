package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The Palms switch is on the Trees page** (v5.10C2) -- the maintainer, 2026-10-03, on the photographs
 * of v5.10C: *«inoltre il flag non deve stare in summer ma in tree»*. The palms stand in the trees'
 * places, so the switch is set beside *Show Trees* and the density in World & scene, and Seasons &
 * decorations no longer has it -- nor the Summer season, which held nothing else.
 *
 * The rules it carries are v5.10C's and are tested there (`SwitchesTellTheTruthTest`: off and locked
 * with no tree to stand in), plus one of the same kind since the firs stay firs: off and locked where
 * every tree left would be a fir (`FirsAmongThePalmsTest` has the scene's side of it). What is held here
 * is where the screens draw it, read off their source as the project does for Compose it cannot run on
 * the JVM, and the home line that no longer counts it.
 */
class PalmsSwitchOnTheTreesPageTest {

    @Test
    fun `the Palms switch is on the Trees page, under Show Trees and the density, drawn from the rule`() {
        val world = source("ui/WorldSceneScreen.kt")
        val trees = world.substringAfter("private fun TreesSubScreen(").substringBefore("\n@Composable")
        // Inside the trees' own section, after the density slider: `afterDensity` is drawn right under it.
        val after = trees.substringAfter("afterDensity = {", missingDelimiterValue = "").substringBefore("\n            },\n        )")
        assertTrue("the switch is not under the trees' density", after.contains("title = \"Palms\""))
        for (wiring in listOf(
            "val palms = SettingsUiModel.palms(customization, themeHasPalms, onlyFirsWouldStand = palmsOnlyFirs)",
            "checked = palms.shownOn,",
            "enabled = palms.interactive,",
            "onCheckedChange = { scope.launch { prefs.setPalmsEnabled(it, forThemeId) } },",
        )) {
            assertTrue("the Trees page does not draw: $wiring", after.contains(wiring))
        }
        // And the theme's layout reaches it, from the screen that asks the layout.
        assertTrue(world.contains("\"trees\" -> TreesSubScreen(customization, themeHasPalms, palmsOnlyFirs, forThemeId, prefs, scope)"))
        val home = source("ui/SettingsScreen.kt")
        assertTrue(home.contains("WorldSceneScreen(\n            customization = customization,\n            settings = settings,\n            theme = effectiveTheme,\n            forThemeId = effectiveThemeId,\n            themeName = effectiveTheme.displayName,\n            themeHasPalms = themeHasPalms,\n            palmsOnlyFirs = palmsOnlyFirs,"))
        // The only-firs answer is the theme's own layout asked with the customization on screen (v5.10C2).
        assertTrue(home.contains("val palmsOnlyFirs = themeLayout.keepsOnlyFirsUnderPalms(customization)"))
    }

    @Test
    fun `Seasons and decorations has no Palms switch and no Summer row, and nothing else sets the palms`() {
        val seasons = source("ui/SeasonsScreen.kt")
        for (gone in listOf("setPalmsEnabled", "SettingsUiModel.palms(", "\"Palms\"", "SUMMER", "\"Summer\"", "themeHasPalms")) {
            assertFalse("Seasons & decorations still has $gone", seasons.contains(gone))
        }
        // One place in the whole app moves the switch: the Trees page.
        val callers = uiSources().filter { (_, text) -> text.contains("prefs.setPalmsEnabled(") }.map { it.first }
        assertEquals(listOf("WorldSceneScreen.kt"), callers)
    }

    @Test
    fun `the home line counts no palm as a decoration`() {
        val none = SceneCustomization.DEFAULT.copy(
            christmasDecorationsEnabled = false, santaEnabled = false, halloweenEnabled = false,
            horrorSkyEnabled = false, flowersEnabled = false,
        )
        for (c in listOf(
            none.copy(palmsInsteadOfTrees = true),
            none.copy(palmsInsteadOfTrees = true, palmsEnabled = true),
            none.copy(palmsInsteadOfTrees = false, palmsEnabled = true),
        )) {
            assertEquals("the palms counted among the decorations", 0, SettingsUiModel.decorationsOn(c))
        }
        // Christmas with palms on: its own decorations, and no more.
        val christmas = defaultCustomizationFor("christmas")
        assertEquals(SettingsUiModel.decorationsOn(christmas), SettingsUiModel.decorationsOn(christmas.copy(palmsInsteadOfTrees = true)))
        assertTrue(SettingsUiModel.palms(christmas.copy(palmsInsteadOfTrees = true), layoutPlantsPalms = false, onlyFirsWouldStand = false).shownOn)
        // And where every tree left is a fir, off and locked, the choice kept (v5.10C2).
        val onlyFirs = SettingsUiModel.palms(christmas.copy(palmsInsteadOfTrees = true), layoutPlantsPalms = false, onlyFirsWouldStand = true)
        assertFalse(onlyFirs.shownOn)
        assertFalse(onlyFirs.interactive)
    }

    // ------------------------------------------------------------------ helpers

    private fun uiDir(): java.io.File {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/ui"
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = java.io.File(dir, "$prefix$suffix")
                if (f.isDirectory) return f
            }
            dir = dir.parentFile
        }
        error("sources not found: $suffix")
    }

    private fun source(path: String): String = java.io.File(uiDir().parentFile, path).readText()

    private fun uiSources(): List<Pair<String, String>> =
        uiDir().listFiles { f -> f.name.endsWith(".kt") }!!.sortedBy { it.name }.map { it.name to it.readText() }
}
