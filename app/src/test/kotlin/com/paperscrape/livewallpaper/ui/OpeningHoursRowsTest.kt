package com.paperscrape.livewallpaper.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The three groups of opening hours, each on its own page of *World & scene*** (two since v5.12, the
 * maintainer's *«vorrei inoltre aggiungere uno slide per orari solo grattacieli e solo negozi»* of
 * 2026-10-09; three and the four rows of *Landscape* since v5.13, his *«il resto accetto i consigliati»*
 * of 2026-10-11): each with its switch and its two hours -- the towers' on *Towers & offices*, the shops'
 * on *Shops*, the public buildings' on *Public buildings* -- and each switch on only while there is a
 * building of its group to keep the hours (`AI_PROJECT_RULES.md` 8.7, the maintainer's rule that a switch
 * tells the truth), the stored choice kept while it reads off.
 */
class OpeningHoursRowsTest {

    @Test
    fun `the shops' switch reads on only while the shops stand`() {
        assertEquals(DependentSwitchUiState(shownOn = true, interactive = true), SettingsUiModel.shopHours(true, buildingsVisible = true))
        assertEquals(DependentSwitchUiState(shownOn = false, interactive = true), SettingsUiModel.shopHours(false, buildingsVisible = true))
        // Show Buildings off: no shop stands, so no shop keeps hours -- off and locked, the choice kept.
        assertEquals(DependentSwitchUiState(shownOn = false, interactive = false), SettingsUiModel.shopHours(true, buildingsVisible = false))
    }

    @Test
    fun `the public buildings' switch reads on only while they stand`() {
        assertEquals(DependentSwitchUiState(shownOn = true, interactive = true), SettingsUiModel.publicHours(true, buildingsVisible = true))
        assertEquals(DependentSwitchUiState(shownOn = false, interactive = true), SettingsUiModel.publicHours(false, buildingsVisible = true))
        assertEquals(DependentSwitchUiState(shownOn = false, interactive = false), SettingsUiModel.publicHours(true, buildingsVisible = false))
    }

    @Test
    fun `the towers' switch reads on only while some tower stands`() {
        assertEquals(DependentSwitchUiState(true, true), SettingsUiModel.towerHours(true, buildingsVisible = true, towersAmount = 0.6f))
        assertEquals(DependentSwitchUiState(false, false), SettingsUiModel.towerHours(true, buildingsVisible = false, towersAmount = 0.6f))
        // The Towers amount at 0 % stands no tower (the shops stay): locked too.
        assertEquals(DependentSwitchUiState(false, false), SettingsUiModel.towerHours(true, buildingsVisible = true, towersAmount = 0f))
        // And the shops' switch does not care about the towers' amount: the shops stand at 0 %.
        assertTrue(SettingsUiModel.shopHours(true, buildingsVisible = true).shownOn)
    }

    @Test
    fun `each page draws its group, wired to its own settings`() {
        val screen = source("ui/WorldSceneScreen.kt")
        val towers = screen.substringAfter("private fun TowersSubScreen(").substringBefore("\n}\n")
        val tower = towers.substringAfter("title = \"Tower opening hours\"")
        assertTrue(tower.contains("SettingsUiModel.towerHours("))
        assertTrue(tower.contains("customization.towerHoursEnabled, customization.buildings.visible, customization.buildings.density"))
        for (setter in listOf("setTowerHoursEnabled", "setTowerOpenHour", "setTowerCloseHour")) assertTrue(setter, tower.contains(setter))
        assertFalse("the towers' group sets nothing of the shops'", tower.contains("setShop"))
        assertFalse("nor of the public buildings'", tower.contains("setPublic"))
        // Under the Towers amount.
        assertTrue(towers.indexOf("densityLabel = \"Towers\"") < towers.indexOf("title = \"Tower opening hours\""))
        // The shops' and the public buildings' page: one composable, each group's own rule and setters.
        val groups = screen.substringAfter("private fun StorefrontGroupSubScreen(").substringBefore("\n}\n")
        assertTrue(groups.contains("if (shops) \"Shop opening hours\" else \"Public building opening hours\""))
        assertTrue(groups.contains("SettingsUiModel.shopHours(customization.shopHoursEnabled, customization.buildings.visible)"))
        assertTrue(groups.contains("SettingsUiModel.publicHours(customization.publicHoursEnabled, customization.buildings.visible)"))
        for (pair in listOf("setShopHoursEnabled" to "setPublicHoursEnabled", "setShopOpenHour" to "setPublicOpenHour", "setShopCloseHour" to "setPublicCloseHour")) {
            assertTrue("each switch and hour picks its group's setter: $pair", groups.contains("if (shops) prefs.${pair.first}(it, forThemeId) else prefs.${pair.second}(it, forThemeId)"))
        }
        assertFalse("neither sets the towers'", groups.contains("setTower"))
        assertTrue("through the section's own slot", groups.substringAfter("StorefrontGroupSection(").contains("openingHours = {"))
        // The four rows of Landscape, each opening its page.
        for (page in listOf("\"houses\"", "\"towers\"", "\"shops\"", "\"publics\"")) {
            assertTrue("a row opens $page", screen.contains("onClick = { activeSection = $page }"))
        }
        assertFalse("the Cities row is gone: the houses have their own", screen.contains("title = \"Cities\""))
        // One group: a switch, and the two hours only while it reads on.
        val group = screen.substringAfter("private fun OpeningHoursGroup(").substringBefore("\n}\n")
        assertTrue(group.contains("checked = state.shownOn"))
        assertTrue(group.contains("enabled = state.interactive"))
        assertTrue(group.contains("if (state.shownOn) {"))
        assertFalse("no raw Slider on the settings screen (CLAUDE.md section 7)", group.contains(" Slider("))
    }

    /**
     * **The page says the hours are the school's** (the maintainer, 2026-10-12: *«L'interruttore e il "dalle /
     * alle" degli edifici pubblici valgono solo per la scuola (e per edifici pubblici futuri), e la pagina deve
     * dirlo»*), and the row of *Landscape* says the two stations are always open.
     */
    @Test
    fun `the public buildings' page and row say the hours are the school's, the stations never close`() {
        val screen = source("ui/WorldSceneScreen.kt")
        val line = screen.substringAfter("private const val PUBLIC_HOURS_LINE =").substringBefore("\n\n")
        assertTrue(line, line.contains("These hours are the school's"))
        assertTrue(line, line.contains("The fire station and the police station never close"))
        assertEquals(
            "School open 09:00-20:00, fire and police station always open",
            SettingsUiModel.storefrontRowSummary(com.paperscrape.livewallpaper.engine.StorefrontGroup.PUBLIC, true, true, 9f, 20f),
        )
        assertEquals(
            "School, fire and police station, always open",
            SettingsUiModel.storefrontRowSummary(com.paperscrape.livewallpaper.engine.StorefrontGroup.PUBLIC, true, false, 9f, 20f),
        )
        assertEquals(
            "Restaurant, bar and shop, open 09:00-20:00",
            SettingsUiModel.storefrontRowSummary(com.paperscrape.livewallpaper.engine.StorefrontGroup.SHOPS, true, true, 9f, 20f),
        )
    }

    private fun source(path: String): String {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/$path"
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = java.io.File(dir, "$prefix$suffix")
                if (f.isFile) return f.readText()
            }
            dir = dir.parentFile
        }
        error("source not found: $suffix")
    }
}
