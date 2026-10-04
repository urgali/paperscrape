package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R
import com.paperscrape.livewallpaper.prefs.AppBackup
import com.paperscrape.livewallpaper.prefs.BackupParseResult
import com.paperscrape.livewallpaper.prefs.ThemeParseResult
import com.paperscrape.livewallpaper.prefs.ThemeShare
import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import com.paperscrape.livewallpaper.prefs.parseAppBackup
import com.paperscrape.livewallpaper.prefs.parseThemeShare
import com.paperscrape.livewallpaper.prefs.toJsonString
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The Palms switch on every theme** (v5.10C), the maintainer's decision of 2026-09-30: *«le palme
 * devono essere attivabili in qualsiasi tema: l'utente deve essere libero di avere anche natale con
 * palme»*. On a theme that plants no palm of its own, the switch puts palms where its trees stand;
 * it starts off there; Beach and Desert are what they were.
 *
 * ### The trap, which is most of this file
 *
 * `SceneCustomization.palmsEnabled` is `true` by default on every theme, so every theme a user has
 * edited, saved or backed up holds `true` -- written by the app, not chosen, and inert until now. Had
 * that field become the switch on Autumn or Christmas, every such user would have found palms there
 * the day they updated. So those themes read a field no build before v5.10 wrote,
 * `palmsInsteadOfTrees`, and absent means off. The three ways an old `true` reaches a running app are
 * tested here with what such a build wrote: **a saved theme** (the real store a v5.5 build wrote,
 * `themes/store-saved-by-v5.5.json`, whose theme stands on Autumn's broadleaf trees with
 * `palmsEnabled: true`), **a shared theme file** (the same entry as v5.5 exported it) and **a whole-app
 * backup** (one written today, with the new field taken out: the customization format of 5.9 is
 * today's less that one field -- `registri/` of the v5.10C round has the diff). The fourth, **the
 * preference store**, is `PalmsPreferenceStoreTest`, over the app's own reader.
 */
class PalmsOnEveryThemeTest {

    private val black = 0xFF000000.toInt()

    /** The ten built-ins whose layout plants no palm, every one of them. */
    private val palmless = ThemeCatalog.ALL.filter { !SceneObjectCatalog.layoutFor(it.id, it.accentColor).hasPalmSlots() }

    private fun drawn(layout: SceneObjectLayout, c: SceneCustomization) =
        layout.staticObjects.map { c.palmSpeciesApplied(it, layout.hasPalmSlots()) }

    @Test
    fun `there are ten such themes, so every one below is counted`() {
        assertEquals(10, palmless.size)
    }

    @Test
    fun `the switch starts off on every theme without palms of its own`() {
        assertFalse("the field must default off", SceneCustomization.DEFAULT.palmsInsteadOfTrees)
        for (theme in palmless) {
            val c = defaultCustomizationFor(theme.id)
            assertFalse("${theme.id} shows the switch on out of the box", c.palmsShown(layoutPlantsPalms = false))
            val layout = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor)
            assertFalse("${theme.id} draws a palm out of the box", drawn(layout, c).any { it.type == SceneObjectType.PALM_TREE })
        }
    }

    @Test
    fun `a true the app wrote before v5_10 plants nothing`() {
        for (theme in palmless) {
            val written = defaultCustomizationFor(theme.id).copy(palmsEnabled = true, palmsInsteadOfTrees = false)
            val layout = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor)
            assertFalse(written.palmsShown(layoutPlantsPalms = false))
            assertEquals("${theme.id}: an old true changed the scene", layout.staticObjects, drawn(layout, written))
        }
    }

    @Test
    fun `on, every tree of the theme is a palm, in the same place, and nothing else moves`() {
        // Without the Christmas layer, whose firs stay firs among the palms since v5.10C2: that half is
        // `FirsAmongThePalmsTest`. (Christmas ships with the layer on.)
        for (theme in palmless) {
            val layout = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor)
            val trees = layout.staticObjects.count { it.type == SceneObjectType.TREE }
            assertTrue("${theme.id} should have trees to begin with", trees > 0)
            val on = drawn(layout, defaultCustomizationFor(theme.id).copy(palmsInsteadOfTrees = true, christmasDecorationsEnabled = false))
            assertEquals("${theme.id} kept a broadleaf tree", 0, on.count { it.type == SceneObjectType.TREE })
            assertEquals("${theme.id}: one palm for every tree", trees, on.count { it.type == SceneObjectType.PALM_TREE })
            for ((before, after) in layout.staticObjects.zip(on)) {
                if (before.type == SceneObjectType.TREE) {
                    assertEquals("${theme.id}: a palm moved or was resized", before.copy(type = SceneObjectType.PALM_TREE), after)
                } else {
                    assertEquals("${theme.id}: a ${before.type} changed with the palms", before, after)
                }
            }
        }
    }

    @Test
    fun `Beach and Desert read only their own field, as before`() {
        for (id in listOf("beach", "desert")) {
            val layout = SceneObjectCatalog.layoutFor(id, black)
            val base = defaultCustomizationFor(id)
            assertTrue("$id should start with palms", base.palmsShown(layoutPlantsPalms = true))
            for (other in listOf(false, true)) {
                assertEquals(
                    "$id moved with the other themes' field",
                    drawn(layout, base),
                    drawn(layout, base.copy(palmsInsteadOfTrees = other)),
                )
            }
            assertFalse(
                "$id still holds a palm with its own field off",
                drawn(layout, base.copy(palmsEnabled = false, palmsInsteadOfTrees = true)).any { it.type == SceneObjectType.PALM_TREE },
            )
        }
    }

    @Test
    fun `a layout that deals palms and trees both keeps its trees, whatever the new field says`() {
        // A shuffled theme draws from a pool holding both (`RandomSceneGenerator.OBJECT_POOL`): it
        // plants palms of its own, so it reads `palmsEnabled` as it did through v5.9, and its tree
        // slots stay trees -- the scene of a user who shuffled one before v5.10 does not change.
        val tree = StaticSceneObject(SceneObjectType.TREE, depthFraction = 0.4f, tileFractionX = 0.2f)
        val palm = StaticSceneObject(SceneObjectType.PALM_TREE, depthFraction = 0.6f, tileFractionX = 0.7f)
        val mixed = SceneObjectLayout(staticObjects = listOf(tree, palm), cars = emptyList())
        assertTrue(mixed.hasPalmSlots())
        val c = SceneCustomization.DEFAULT.copy(palmsInsteadOfTrees = true)
        assertEquals(listOf(tree, palm), drawn(mixed, c))
        assertEquals(listOf(tree, palm.copy(type = SceneObjectType.TREE)), drawn(mixed, c.copy(palmsEnabled = false)))
    }

    @Test
    fun `the gallery card shows the palms where the switch put them, and only there`() {
        for (id in listOf("christmas", "autumn", "winter")) {
            val theme = ThemeCatalog.byId(id)
            val base = defaultCustomizationFor(id)
            val off = ThemePreviewScenes.forTheme(theme, base)
            assertFalse("$id card has a palm with the switch off", off.contains(R.drawable.palmtree_trunk))
            val on = ThemePreviewScenes.forTheme(theme, base.copy(palmsInsteadOfTrees = true))
            assertTrue("$id card has no palm with the switch on", on.contains(R.drawable.palmtree_trunk))
            assertFalse("$id card kept a broadleaf canopy", on.contains(R.drawable.tree_canopy))
            // An old true is not the switch on the card either.
            assertEquals(off.items, ThemePreviewScenes.forTheme(theme, base.copy(palmsEnabled = true)).items)
        }
    }

    // ------------------------------------------------------------------ the three roads, as written then

    @Test
    fun `a theme saved before v5_10 comes back with its trees`() {
        val store = customThemeDataFromJsonString(fixture().toString())
        val entry = store.customThemes.single()
        assertTrue("the fixture must carry the old default for this to mean anything", entry.customization.palmsEnabled)
        assertFalse("the fixture must plant no palm of its own", entry.layout.hasPalmSlots())
        assertFalse("a saved theme came back with the switch on", entry.customization.palmsShown(entry.layout.hasPalmSlots()))
        assertFalse(drawn(entry.layout, entry.customization).any { it.type == SceneObjectType.PALM_TREE })
        // And one saved now, with the switch on, keeps it.
        val now = entry.copy(customization = entry.customization.copy(palmsInsteadOfTrees = true))
        val back = customThemeEntryFromJson(JSONObject(now.toJson().toString()))
        assertTrue(back.customization.palmsShown(back.layout.hasPalmSlots()))
    }

    @Test
    fun `a theme file shared before v5_10 opens with its trees`() {
        val entry = fixture().getJSONArray("customThemes").getJSONObject(0)
        val file = JSONObject().apply {
            put("kind", ThemeShare.DOCUMENT_KIND)
            put("schemaVersion", 1)
            put("name", entry.getString("name"))
            put("theme", JSONObject(entry.getJSONObject("theme").toString()))
            put("layout", JSONObject(entry.getJSONObject("layout").toString()))
            put("customization", JSONObject(entry.getJSONObject("customization").toString()))
        }.toString()
        val share = (parseThemeShare(file) as ThemeParseResult.Ok).share
        assertTrue(share.customization.palmsEnabled)
        assertFalse("a shared file opened with palms", share.customization.palmsShown(share.layout.hasPalmSlots()))
    }

    @Test
    fun `a backup written before v5_10 restores every theme with its trees`() {
        val edited = mapOf(
            "autumn" to defaultCustomizationFor("autumn"),
            "christmas" to defaultCustomizationFor("christmas"),
            "beach" to defaultCustomizationFor("beach"),
        )
        val saved = CustomThemeData(customThemes = customThemeDataFromJsonString(fixture().toString()).customThemes)
        val written = JSONObject(AppBackup.from(WallpaperSettings(themeCustomizations = edited), saved, "5.9", 1_727_000_000_000L).toJsonString())
        val removed = withoutField(written, "palmsInsteadOfTrees")
        assertTrue("the backup must have carried the field for its removal to mean anything", removed > 0)
        val back = (parseAppBackup(written.toString()) as BackupParseResult.Ok).backup
        for (id in listOf("autumn", "christmas")) {
            val c = back.themeCustomizations.getValue(id)
            assertTrue("$id: the old default is in the backup", c.palmsEnabled)
            assertFalse("$id came back from a 5.9 backup with palms", c.palmsShown(layoutPlantsPalms = false))
        }
        assertTrue("Beach came back without its palms", back.themeCustomizations.getValue("beach").palmsShown(layoutPlantsPalms = true))
        val entry = back.customThemeData.customThemes.single()
        assertFalse("a saved theme in the backup came back with palms", entry.customization.palmsShown(entry.layout.hasPalmSlots()))
    }

    @Test
    fun `a backup written now keeps the switch, both ways`() {
        for (on in listOf(true, false)) {
            val c = defaultCustomizationFor("christmas").copy(palmsInsteadOfTrees = on, palmsEnabled = on)
            val written = AppBackup.from(WallpaperSettings(themeCustomizations = mapOf("christmas" to c)), CustomThemeData.EMPTY, "5.10", 0L)
            val back = (parseAppBackup(written.toJsonString()) as BackupParseResult.Ok).backup
            assertEquals(on, back.themeCustomizations.getValue("christmas").palmsShown(layoutPlantsPalms = false))
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun fixture(): JSONObject {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("themes/store-saved-by-v5.5.json")) {
            "test resource themes/store-saved-by-v5.5.json"
        }
        return JSONObject(stream.bufferedReader().readText())
    }

    /** Takes [field] out of every object under [node], as a build that never wrote it; returns how many. */
    private fun withoutField(node: Any?, field: String): Int = when (node) {
        is JSONObject -> {
            var n = if (node.has(field)) 1 else 0
            node.remove(field)
            for (key in node.keys().asSequence().toList()) n += withoutField(node.opt(key), field)
            n
        }
        is JSONArray -> (0 until node.length()).sumOf { withoutField(node.opt(it), field) }
        else -> 0
    }

    private fun ThemePreviewScene.contains(resId: Int): Boolean =
        (backdrop + items + cars + ground).any { item -> item.parts.any { it.resId == resId } }
}
