package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.engine.DistantHousesConfig
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two rows v5.11 adds to World & scene, under `AI_PROJECT_RULES.md` 8.7 (a switch reads on only
 * while the thing happens): *Distant houses* under *Mountains*, and the *Shops* colours beside
 * *Buildings*. The rules are [SettingsUiModel]'s; their use on the screens is pinned by reading the
 * source, as every screen rule here is (no composable runs on the JVM).
 */
class DistantHousesAndShopsRowsTest {

    @Test
    fun `on only while houses stand - switched on, above 0 percent and with a mountain to stand on`() {
        val on = SettingsUiModel.distantHouses(visible = true, density = 0.5f, mountainsShown = true)
        assertTrue(on.shownOn)
        assertEquals("On - 50%", on.summary)

        val atZero = SettingsUiModel.distantHouses(visible = true, density = 0f, mountainsShown = true)
        assertFalse(atZero.shownOn)
        assertTrue(atZero.noneAtZero)
        assertEquals("None at 0%", atZero.summary)

        val noMountains = SettingsUiModel.distantHouses(visible = true, density = 0.5f, mountainsShown = false)
        assertFalse(noMountains.shownOn)
        assertTrue(noMountains.needsMountains)
        assertEquals("Off - needs Mountains", noMountains.summary)

        val off = SettingsUiModel.distantHouses(visible = false, density = 0.5f, mountainsShown = true)
        assertFalse(off.shownOn)
        assertEquals("Off", off.summary)
        assertEquals("Off", SettingsUiModel.distantHouses(visible = false, density = 0.5f, mountainsShown = false).summary)
    }

    @Test
    fun `a tap goes where it is put right, and the stored choice is never rewritten to look right`() {
        // At 0 %: the amount back to 50 % and the switch on.
        assertEquals(
            DistantHousesTap.Restore(DistantHousesConfig.STARTING_DENSITY),
            SettingsUiModel.distantHousesTap(wanted = true, visible = true, density = 0f, mountainsShown = true),
        )
        // No mountains: to the Mountains page, storing "on" only when it was asked for and not stored.
        assertEquals(
            DistantHousesTap.OpenMountains(storeOn = false),
            SettingsUiModel.distantHousesTap(wanted = true, visible = true, density = 0.5f, mountainsShown = false),
        )
        assertEquals(
            DistantHousesTap.OpenMountains(storeOn = true),
            SettingsUiModel.distantHousesTap(wanted = true, visible = false, density = 0.5f, mountainsShown = false),
        )
        // The ordinary case: the flag.
        assertEquals(
            DistantHousesTap.SetVisible(true),
            SettingsUiModel.distantHousesTap(wanted = true, visible = false, density = 0.5f, mountainsShown = true),
        )
        assertEquals(
            DistantHousesTap.SetVisible(false),
            SettingsUiModel.distantHousesTap(wanted = false, visible = true, density = 0.5f, mountainsShown = true),
        )
        assertEquals(0.5f, DistantHousesConfig.STARTING_DENSITY, 0f)
    }

    @Test
    fun `the Shops line says what the shops are, and with Buildings off why none stands`() {
        assertTrue(SettingsUiModel.shopsLine(buildingsVisible = true).contains("Their colours are their own"))
        val off = SettingsUiModel.shopsLine(buildingsVisible = false)
        assertTrue(off.startsWith("Off"))
        assertTrue(off.contains("Show Buildings"))
    }

    /** The screens use the rules, and say where the colours come from (the maintainer's request). */
    @Test
    fun `the screens read the rules and name where the distant houses' colours come from`() {
        val world = File(sourceRoot(), "ui/WorldSceneScreen.kt").readText()
        val page = world.substring(world.indexOf("private fun DistantHousesSubScreen("))
        assertTrue(page.contains("SettingsUiModel.distantHouses(config.visible, config.density, mountains)"))
        assertTrue(page.contains("SettingsUiModel.distantHousesTap(wanted, config.visible, config.density, mountains)"))
        assertTrue("the row that says the colours come from Houses", page.contains("title = \"Colours: from Houses\""))
        assertTrue("and takes the user there", page.contains("onClick = onOpenHouses"))
        assertTrue("the row sits under Mountains", world.indexOf("title = \"Mountains\"") < world.indexOf("title = \"Distant houses\""))
        val cities = world.substring(world.indexOf("private fun CitiesSubScreen("))
        assertTrue(cities.contains("ShopsSection("))
        assertTrue(cities.contains("colourLine = BUILDINGS_COLOUR_LINE"))
        assertTrue(BUILDINGS_COLOUR_LINE.contains("towers' colours"))
        assertTrue(HOUSES_COLOUR_LINE.contains("distant houses"))
        val sections = File(sourceRoot(), "ui/SceneCategorySections.kt").readText()
        val shops = sections.substring(sections.indexOf("internal fun ShopsSection("))
        assertTrue(shops.contains("SettingsUiModel.shopsLine(buildingsVisible)"))
        assertTrue(shops.contains("Modifier.clickable(onClick = onGoToBuildings)"))
        assertTrue(shops.contains("prefs.resetCategory(category, forThemeId)"))
    }

    /**
     * **A mountain layer at 0 % draws no mountain** (`CandidateThreshold` keeps none of its pool), so
     * its switch reads off there and the line says why (decision 40, inventory I-414, found by this
     * round's review), the distant houses read "Off - needs Mountains", and the card draws neither.
     */
    @Test
    fun `a mountain layer at 0 percent reads off, and so do the houses that would stand on it`() {
        val layer = com.paperscrape.livewallpaper.engine.defaultCustomizationFor("autumn").mountainsFront
        assertFalse(layer.copy(visible = true, density = 0f).drawsAny)
        assertFalse(layer.copy(visible = false, density = 1f).drawsAny)
        assertTrue(layer.copy(visible = true, density = 0.01f).drawsAny)
        val world = File(sourceRoot(), "ui/WorldSceneScreen.kt").readText()
        assertTrue(world.contains("c.mountainsFront.drawsAny || c.mountainsBack.drawsAny"))
        val sections = File(sourceRoot(), "ui/SceneCategorySections.kt").readText()
        val mountain = sections.substring(sections.indexOf("internal fun MountainLayerSection("))
        assertTrue(mountain.contains("val shown = SettingsUiModel.amountSwitch(config.visible, config.density)"))
        assertTrue(mountain.contains("checked = shown.shownOn"))
        assertTrue(mountain.contains("SettingsUiModel.amountTap(wanted, config.density,"))
        val theme = com.paperscrape.livewallpaper.engine.ThemeCatalog.byId("autumn")
        val base = com.paperscrape.livewallpaper.engine.defaultCustomizationFor("autumn")
            .copy(distantHouses = DistantHousesConfig(visible = true, density = 1f))
        val atZero = base.copy(
            mountainsFront = base.mountainsFront.copy(visible = true, density = 0f),
            mountainsBack = base.mountainsBack.copy(visible = true, density = 0f),
        )
        assertTrue("the card draws no mountain at 0 %", com.paperscrape.livewallpaper.engine.ThemePreviewScenes.forTheme(theme, atZero).peaks.isEmpty())
        assertTrue(com.paperscrape.livewallpaper.engine.ThemePreviewScenes.forTheme(theme, base).peaks.isNotEmpty())
    }

    private fun sourceRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "app/src/main/kotlin/com/paperscrape/livewallpaper")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        error("source root not found")
    }
}
