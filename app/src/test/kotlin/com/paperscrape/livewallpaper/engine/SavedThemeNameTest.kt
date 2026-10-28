package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.prefs.ThemeShare
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **"Rename" changes the name the user sees.**
 *
 * A saved theme carries its name twice: `CustomThemeEntry.name`, which "Rename" writes, and
 * `theme.displayName`, which every screen reads -- the gallery card through the entry's theme, the
 * preview at the top of the settings, the Theme row, Seasons and World & scene through
 * [ThemeCatalog.byId]. A rename made by a build up to v5.9A wrote the first and not the second, so
 * a store saved by one says "Night walk 2" in the entry and "Night walk" in its theme: it is read
 * with the entry's name in both, or every screen would go on saying the old name.
 */
class SavedThemeNameTest {

    @After fun forget() = CustomThemeRegistry.update(CustomThemeData.EMPTY)

    /** What the store holds after a rename made by any build up to v5.9A: `name` moved, the theme's copy did not. */
    private fun renamedBeforeTheRepair(): String {
        val saved = CustomThemeData(customThemes = listOf(entry(ID, "Night walk")))
        val renamed = saved.copy(customThemes = saved.customThemes.map { it.copy(name = "Night walk 2") })
        val doc = renamed.toJsonString()
        val onDisk = JSONObject(doc).getJSONArray("customThemes").getJSONObject(0)
        check(onDisk.getString("name") == "Night walk 2" && onDisk.getJSONObject("theme").getString("displayName") == "Night walk") {
            "the fixture is not the state a pre-repair rename leaves"
        }
        return doc
    }

    @Test
    fun `a theme renamed before the repair shows the name the user wrote last`() {
        val read = customThemeDataFromJsonString(renamedBeforeTheRepair()).customThemes.single()
        assertEquals("the name the user wrote", "Night walk 2", read.name)
        assertEquals(
            "the gallery card reads the entry's theme, and it still says the old name",
            "Night walk 2",
            read.theme.displayName,
        )
    }

    @Test
    fun `every screen that looks the theme up gets that name`() {
        CustomThemeRegistry.update(customThemeDataFromJsonString(renamedBeforeTheRepair()))
        assertEquals(
            "the settings preview, the Theme row, Seasons and World & scene all read ThemeCatalog.byId",
            "Night walk 2",
            ThemeCatalog.byId(ID).displayName,
        )
    }

    @Test
    fun `the next write puts the name in both places`() {
        val rewritten = customThemeDataFromJsonString(renamedBeforeTheRepair()).toJsonString()
        val onDisk = JSONObject(rewritten).getJSONArray("customThemes").getJSONObject(0)
        assertEquals("Night walk 2", onDisk.getString("name"))
        assertEquals("the file still carries the old name inside the theme", "Night walk 2", onDisk.getJSONObject("theme").getString("displayName"))
    }

    @Test
    fun `an exported theme file carries the new name inside the theme too`() {
        CustomThemeRegistry.update(customThemeDataFromJsonString(renamedBeforeTheRepair()))
        val share = ThemeShare.of(ID, "Night walk 2", defaultCustomizationFor("autumn"), "5.8", 0L)
        assertEquals("Night walk 2", share.theme.displayName)
    }

    /**
     * The guard: a theme nobody renamed is read and written back unchanged, byte for byte.
     * Standalone themes only -- a saved built-in with no cars is repaired on read, which is a
     * different reason for the bytes to move.
     */
    @Test
    fun `a theme never renamed reads and writes exactly as before`() {
        val doc = CustomThemeData(
            customThemes = listOf(entry(ID, "Night walk"), entry("custom:1700000000124", "My autumn")),
        ).toJsonString()
        val read = customThemeDataFromJsonString(doc)
        assertEquals(listOf("Night walk", "My autumn"), read.customThemes.map { it.theme.displayName })
        assertEquals(doc, read.toJsonString())
    }

    /** And the one kind of saved theme "Rename" cannot reach keeps the built-in's name. */
    @Test
    fun `a saved version of a built-in keeps the built-in's name`() {
        val doc = CustomThemeData(overrides = mapOf("christmas" to entry("christmas", "Christmas"))).toJsonString()
        CustomThemeRegistry.update(customThemeDataFromJsonString(doc))
        assertEquals("Christmas", ThemeCatalog.byId("christmas").displayName)
    }

    private fun entry(id: String, name: String) = CustomThemeEntry(
        id = id,
        name = name,
        theme = ThemeCatalog.ALL.first { it.id == "autumn" }.copy(id = id, displayName = name),
        layout = SceneObjectLayout(
            staticObjects = listOf(StaticSceneObject(SceneObjectType.HOUSE, depthFraction = 0.12f, tileFractionX = 0.2f)),
            cars = emptyList(),
        ),
        customization = defaultCustomizationFor("autumn"),
    )

    private companion object {
        const val ID = "custom:1700000000123"
    }
}
