package com.paperscrape.livewallpaper.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The two groups of opening hours on *World & scene*'s Cities page** (v5.12, the maintainer's
 * *«vorrei inoltre aggiungere uno slide per orari solo grattacieli e solo negozi»* of 2026-10-09): each
 * with its switch and its two hours -- the towers' in Buildings, the shops' in Shops -- and each switch
 * on only while there is a building of its group to keep the hours (`AI_PROJECT_RULES.md` 8.7, the
 * maintainer's rule that a switch tells the truth), the stored choice kept while it reads off.
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
    fun `the towers' switch reads on only while some tower stands`() {
        assertEquals(DependentSwitchUiState(true, true), SettingsUiModel.towerHours(true, buildingsVisible = true, towersAmount = 0.6f))
        assertEquals(DependentSwitchUiState(false, false), SettingsUiModel.towerHours(true, buildingsVisible = false, towersAmount = 0.6f))
        // The Towers amount at 0 % stands no tower (the shops stay): locked too.
        assertEquals(DependentSwitchUiState(false, false), SettingsUiModel.towerHours(true, buildingsVisible = true, towersAmount = 0f))
        // And the shops' switch does not care about the towers' amount: the shops stand at 0 %.
        assertTrue(SettingsUiModel.shopHours(true, buildingsVisible = true).shownOn)
    }

    @Test
    fun `the page draws both groups, each wired to its own settings, each in its own section`() {
        val screen = source("ui/WorldSceneScreen.kt")
        val tower = screen.substringAfter("title = \"Tower opening hours\"").substringBefore("OpeningHoursGroup(")
        assertTrue(tower.contains("SettingsUiModel.towerHours("))
        assertTrue(tower.contains("customization.towerHoursEnabled, customization.buildings.visible, customization.buildings.density"))
        for (setter in listOf("setTowerHoursEnabled", "setTowerOpenHour", "setTowerCloseHour")) assertTrue(setter, tower.contains(setter))
        assertFalse("the towers' group sets nothing of the shops'", tower.contains("setShop"))
        val shop = screen.substringAfter("title = \"Shop opening hours\"").substringBefore("    }\n}")
        assertTrue(shop.contains("SettingsUiModel.shopHours(customization.shopHoursEnabled, customization.buildings.visible)"))
        for (setter in listOf("setShopHoursEnabled", "setShopOpenHour", "setShopCloseHour")) assertTrue(setter, shop.contains(setter))
        assertFalse("the shops' group sets nothing of the towers'", shop.contains("setTower"))
        // In Buildings, under the Towers amount; in Shops, through its own slot.
        assertTrue(screen.indexOf("densityLabel = \"Towers\"") < screen.indexOf("title = \"Tower opening hours\""))
        assertTrue(screen.substringAfter("ShopsSection(").substringBefore("editingTarget?.let").contains("openingHours = {"))
        // One group: a switch, and the two hours only while it reads on.
        val group = screen.substringAfter("private fun OpeningHoursGroup(").substringBefore("\n}\n")
        assertTrue(group.contains("checked = state.shownOn"))
        assertTrue(group.contains("enabled = state.interactive"))
        assertTrue(group.contains("if (state.shownOn) {"))
        assertFalse("no raw Slider on the settings screen (CLAUDE.md section 7)", group.contains(" Slider("))
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
