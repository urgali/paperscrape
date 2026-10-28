package com.paperscrape.livewallpaper.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The three groups of opening hours, each on its own page of *World & scene***, and the four rows of
 * *Landscape* (`DESIGN_NOTES.md` §12, decisions 62 and 63): each group with its switch and its two hours --
 * the towers' on *Towers & offices*, the shops' on *Shops*, the public buildings' on *Public buildings* --
 * and each switch on only while there is a building of its group to keep the hours (decision 40: a switch
 * reads on only while what it promises happens), the stored choice kept while it reads off.
 */
class OpeningHoursRowsTest {

    @Test
    fun `the shops' switch reads on only while the shops stand`() {
        assertEquals(DependentSwitchUiState(shownOn = true, interactive = true), SettingsUiModel.shopHours(true, buildingsVisible = true, placesOnTheme = true))
        assertEquals(DependentSwitchUiState(shownOn = false, interactive = true), SettingsUiModel.shopHours(false, buildingsVisible = true, placesOnTheme = true))
        // Show Buildings off: no shop stands, so no shop keeps hours -- off and locked, the choice kept.
        assertEquals(DependentSwitchUiState(shownOn = false, interactive = false), SettingsUiModel.shopHours(true, buildingsVisible = false, placesOnTheme = true))
    }

    @Test
    fun `the public buildings' switch reads on only while they stand`() {
        assertEquals(DependentSwitchUiState(shownOn = true, interactive = true), SettingsUiModel.publicHours(true, buildingsVisible = true, placesOnTheme = true))
        assertEquals(DependentSwitchUiState(shownOn = false, interactive = true), SettingsUiModel.publicHours(false, buildingsVisible = true, placesOnTheme = true))
        assertEquals(DependentSwitchUiState(shownOn = false, interactive = false), SettingsUiModel.publicHours(true, buildingsVisible = false, placesOnTheme = true))
    }

    @Test
    fun `the towers' switch reads on only while some tower stands`() {
        assertEquals(DependentSwitchUiState(true, true), SettingsUiModel.towerHours(true, buildingsVisible = true, towersAmount = 0.6f, towersStand = true))
        assertEquals(DependentSwitchUiState(false, false), SettingsUiModel.towerHours(true, buildingsVisible = false, towersAmount = 0.6f, towersStand = true))
        // The Towers amount at 0 % stands no tower (the shops stay): locked too.
        assertEquals(DependentSwitchUiState(false, false), SettingsUiModel.towerHours(true, buildingsVisible = true, towersAmount = 0f, towersStand = false))
        // And the shops' switch does not care about the towers' amount: the shops stand at 0 %.
        assertTrue(SettingsUiModel.shopHours(true, buildingsVisible = true, placesOnTheme = true).shownOn)
    }

    /**
     * Read off `WorldSceneScreen.kt`, whole lines with the comments taken off, a page's in its own function: the
     * towers' group under their amount on their page, the shops' and the public buildings' in the one composable of their
     * page, each with its own rule and setters, and one group drawn as a switch that reads its state and two hours shown
     * only while it reads on.
     */
    @Test
    fun `each page draws its group, wired to its own settings`() {
        val screen = codeLines("ui/WorldSceneScreen.kt")
        val towers = functionLines(screen, "private fun TowersSubScreen(")
        val tower = towers.dropWhile { it != "title = \"Tower opening hours\"," }
        assertTrue("the towers' group is drawn on their page", tower.isNotEmpty())
        assertTrue(tower.contains("state = SettingsUiModel.towerHours("))
        assertTrue(tower.contains("customization.towerHoursEnabled, customization.buildings.visible, customization.buildings.density,"))
        for ((slot, setter) in listOf("onEnabled" to "setTowerHoursEnabled", "onOpen" to "setTowerOpenHour", "onClose" to "setTowerCloseHour")) {
            assertTrue(setter, tower.contains("$slot = { scope.launch { prefs.$setter(it, forThemeId) } },"))
        }
        assertFalse("the towers' group sets nothing of the shops'", tower.any { "setShop" in it })
        assertFalse("nor of the public buildings'", tower.any { "setPublic" in it })
        // Under the Towers amount.
        assertTrue(towers.indexOf("densityLabel = \"Towers\",") in 0 until towers.indexOf("title = \"Tower opening hours\","))
        // The shops' and the public buildings' page: one composable, each group's own rule and setters.
        val groups = functionLines(screen, "private fun StorefrontGroupSubScreen(")
        assertTrue(groups.contains("title = if (shops) \"Shop opening hours\" else \"Public building opening hours\","))
        assertTrue(groups.contains("SettingsUiModel.shopHours(customization.shopHoursEnabled, customization.buildings.visible, placesOnTheme)"))
        assertTrue(groups.contains("SettingsUiModel.publicHours(customization.publicHoursEnabled, customization.buildings.visible, placesOnTheme)"))
        for ((slot, pair) in listOf(
            "onEnabled" to ("setShopHoursEnabled" to "setPublicHoursEnabled"),
            "onOpen" to ("setShopOpenHour" to "setPublicOpenHour"),
            "onClose" to ("setShopCloseHour" to "setPublicCloseHour"),
        )) {
            assertTrue(
                "each switch and hour picks its group's setter: $pair",
                groups.contains("$slot = { scope.launch { if (shops) prefs.${pair.first}(it, forThemeId) else prefs.${pair.second}(it, forThemeId) } },"),
            )
        }
        assertFalse("neither sets the towers'", groups.any { "setTower" in it })
        assertTrue("through the section's own slot", groups.dropWhile { it != "StorefrontGroupSection(" }.contains("openingHours = {"))
        // The four rows of Landscape, each opening its page.
        for (page in listOf("\"houses\"", "\"towers\"", "\"shops\"", "\"publics\"")) {
            assertTrue("a row opens $page", screen.contains("onClick = { activeSection = $page },"))
        }
        assertFalse("the Cities row is gone: the houses have their own", screen.any { "title = \"Cities\"" in it })
        // One group: a switch, and the two hours only while it reads on.
        val group = functionLines(screen, "private fun OpeningHoursGroup(")
        assertTrue(group.contains("checked = state.shownOn,"))
        assertTrue(group.contains("enabled = state.interactive,"))
        assertTrue(group.contains("if (state.shownOn) {"))
        assertFalse(
            "no raw Slider on the settings screen (PreferenceSlider commits once on drag end)",
            group.any { it.startsWith("Slider(") || " Slider(" in it },
        )
    }

    /**
     * **The page says the hours are the school's** (`DESIGN_NOTES.md` §12, decision 62: the fire station and
     * the police station never close), and the row of *Landscape* says the two stations are always open.
     */
    @Test
    fun `the public buildings' page and row say the hours are the school's, the stations never close`() {
        val screen = source("ui/WorldSceneScreen.kt")
        val line = screen.substringAfter("private const val PUBLIC_HOURS_LINE =").substringBefore("\n\n")
        assertTrue(line, line.contains("These hours are the school's"))
        assertTrue(line, line.contains("The fire station and the police station never close"))
        assertEquals(
            "School open 09:00-20:00, fire and police station always open",
            SettingsUiModel.storefrontRowSummary(com.paperscrape.livewallpaper.engine.StorefrontGroup.PUBLIC, true, true, 9f, 20f, placesOnTheme = true),
        )
        assertEquals(
            "School, fire and police station, always open",
            SettingsUiModel.storefrontRowSummary(com.paperscrape.livewallpaper.engine.StorefrontGroup.PUBLIC, true, false, 9f, 20f, placesOnTheme = true),
        )
        assertEquals(
            "Restaurant, bar and shop, open 09:00-20:00",
            SettingsUiModel.storefrontRowSummary(com.paperscrape.livewallpaper.engine.StorefrontGroup.SHOPS, true, true, 9f, 20f, placesOnTheme = true),
        )
    }

    /** The app's source file [path], its lines trimmed, without comments: a line comment cut off, a comment line dropped. */
    private fun codeLines(path: String): List<String> =
        source(path).lines().map { it.trim() }
            .filterNot { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }
            .map { it.substringBefore(" //").trim() }
            .filter { it.isNotEmpty() }

    /**
     * [lines] from the one that starts with [declaration] to the brace that closes the function's body: the first brace
     * left open, a parameter's default `{}` closing on its own line.
     */
    private fun functionLines(lines: List<String>, declaration: String): List<String> {
        val start = lines.indexOfFirst { it.startsWith(declaration) }
        assertTrue("$declaration is gone", start >= 0)
        var depth = 0
        var opened = false
        for (i in start until lines.size) {
            depth += lines[i].split("{").size - lines[i].split("}").size
            if (depth > 0) opened = true
            if (opened && depth <= 0) return lines.subList(start, i + 1)
        }
        return lines.subList(start, lines.size)
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
