package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.engine.DistantHousesConfig
import com.paperscrape.livewallpaper.engine.StorefrontGroup
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two rows of World & scene, under `DESIGN_NOTES.md` §12, decision 40 (a switch reads on only
 * while the thing happens): *Distant houses* under *Mountains*, and the *Shops* colours beside
 * *Buildings*. The rules are [SettingsUiModel]'s; their use on the screens is pinned by reading the
 * source, as every screen rule here is (no composable runs on the JVM): whole lines with the comments
 * taken off, a page's read in its own function.
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
    fun `each group's line says which buildings it is, and with Buildings off why none stands and where`() {
        val shops = SettingsUiModel.storefrontGroupLine(StorefrontGroup.SHOPS, buildingsVisible = true, placesOnTheme = true)
        assertTrue(shops.startsWith("The restaurant, the bar and the shop."))
        val publics = SettingsUiModel.storefrontGroupLine(StorefrontGroup.PUBLIC, buildingsVisible = true, placesOnTheme = true)
        assertTrue(publics.startsWith("The school, the fire station and the police station."))
        // The street's six buildings take turns in the places, which is what each page has to say -- with no
        // number: a built-in street has five, a street saved by an older version three, a random one its own.
        for (line in listOf(shops, publics)) assertTrue(line.contains("take turns in its places as the scenery scrolls"))
        for (group in StorefrontGroup.values()) {
            val off = SettingsUiModel.storefrontGroupLine(group, buildingsVisible = false, placesOnTheme = true)
            assertTrue(off.startsWith("Off"))
            assertTrue(off.contains("Show Buildings"))
            assertTrue("it says where that switch is", off.contains(SettingsUiModel.TOWERS_PAGE))
        }
    }

    /** The screens use the rules, and say where the colours come from. */
    @Test
    fun `the screens read the rules and name where the distant houses' colours come from`() {
        val world = codeLines("ui/WorldSceneScreen.kt")
        val page = functionLines(world, "private fun DistantHousesSubScreen(")
        assertTrue(page.contains("val state = SettingsUiModel.distantHouses(config.visible, config.density, mountains)"))
        assertTrue(page.contains("when (val tap = SettingsUiModel.distantHousesTap(wanted, config.visible, config.density, mountains)) {"))
        assertTrue("the row that says the colours come from Houses", page.contains("title = \"Colours: from Houses\","))
        assertTrue("and takes the user there", page.contains("onClick = onOpenHouses,"))
        val mountains = world.indexOf("title = \"Mountains\",")
        assertTrue("the row sits under Mountains", mountains >= 0 && mountains < world.indexOf("title = \"Distant houses\","))
        assertTrue(functionLines(world, "private fun TowersSubScreen(").contains("colourLine = BUILDINGS_COLOUR_LINE,"))
        assertTrue(functionLines(world, "private fun StorefrontGroupSubScreen(").contains("StorefrontGroupSection("))
        assertTrue("the houses' row opens the houses' page", world.contains("onOpenHouses = { activeSection = \"houses\" },"))
        assertTrue(BUILDINGS_COLOUR_LINE.contains("towers' colours"))
        assertTrue(HOUSES_COLOUR_LINE.contains("distant houses"))
        val shops = functionLines(codeLines("ui/SceneCategorySections.kt"), "internal fun StorefrontGroupSection(")
        assertTrue(shops.contains("SettingsUiModel.storefrontGroupLine(group, buildingsVisible, placesOnTheme),"))
        assertTrue(
            "the line leads to Buildings only where turning them on would bring the group",
            shops.contains("modifier = if (leadsToBuildings) Modifier.clickable(onClick = onGoToBuildings) else Modifier,"),
        )
        assertTrue(shops.contains("val leadsToBuildings = placesOnTheme && !buildingsVisible"))
        assertTrue(shops.contains("onClick = { scope.launch { prefs.resetCategory(category, forThemeId) } },"))
    }

    /**
     * **A mountain layer at 0 % draws no mountain** (`CandidateThreshold` keeps none of its pool), so
     * its switch reads off there and the line says why (decision 40), the distant houses read
     * "Off - needs Mountains", and the card draws neither.
     */
    @Test
    fun `a mountain layer at 0 percent reads off, and so do the houses that would stand on it`() {
        val layer = com.paperscrape.livewallpaper.engine.defaultCustomizationFor("autumn").mountainsFront
        assertFalse(layer.copy(visible = true, density = 0f).drawsAny)
        assertFalse(layer.copy(visible = false, density = 1f).drawsAny)
        assertTrue(layer.copy(visible = true, density = 0.01f).drawsAny)
        assertTrue(
            codeLines("ui/WorldSceneScreen.kt")
                .contains("private fun mountainsShown(c: SceneCustomization): Boolean = c.mountainsFront.drawsAny || c.mountainsBack.drawsAny"),
        )
        val mountain = functionLines(codeLines("ui/SceneCategorySections.kt"), "internal fun MountainLayerSection(")
        assertTrue(mountain.contains("val shown = SettingsUiModel.amountSwitch(config.visible, config.density)"))
        assertTrue(mountain.contains("checked = shown.shownOn,"))
        assertTrue(mountain.contains("SettingsUiModel.amountTap(wanted, config.density, (if (front) d.mountainsFront else d.mountainsBack).density),"))
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

    /** The app's source file [path], its lines trimmed, without comments: a line comment cut off, a comment line dropped. */
    private fun codeLines(path: String): List<String> =
        File(sourceRoot(), path).readLines().map { it.trim() }
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
