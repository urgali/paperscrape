package com.paperscrape.livewallpaper.prefs

import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.CustomThemeEntry
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.SceneObjectCatalog
import com.paperscrape.livewallpaper.engine.SceneObjectLayout
import com.paperscrape.livewallpaper.engine.SceneObjectRenderer
import com.paperscrape.livewallpaper.engine.SceneObjectType
import com.paperscrape.livewallpaper.engine.SceneSpace
import com.paperscrape.livewallpaper.engine.StaticSceneObject
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.engine.customThemeDataFromJsonString
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import com.paperscrape.livewallpaper.engine.keepCandidate
import com.paperscrape.livewallpaper.engine.toJsonString
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A theme file exported before v5.6 gets the school at import, as the same theme saved then does**
 * (inventory I-93, v5.9F).
 *
 * The maintainer decided on 2026-09-21 that streets saved before the school existed get it (*«voglio
 * che prendano la scuola»*), and v5.7F did it for the themes the store holds, in the 4 -> 5 step of
 * `migrateCustomThemeJson` (`SavedThemeSchoolMigrationTest`). A theme **file** took another road:
 * `parseThemeShare` read its street as it was, so a file exported from v5.5 and imported today came
 * in without a school however the sliders were set. On 2026-09-28 the maintainer answered that the
 * files get it too. The file cannot say it is old -- the share format's own version is 1 and has
 * never changed -- so the rule is the one the store applies, and it adds nothing to a street that
 * has a building in the school's third, which every street from v5.6 on has.
 *
 * The fixture is the store v5.7F read off the BV6600 after saving a theme with the published v5.5
 * (`consegna_v5_7f/registri/dati/tema_salvato_da_v55.json`, schema 4): a real pre-school street, 29
 * objects, and the file below is what v5.5's export wrote from it (the same five keys and the
 * format's version 1).
 */
class ThemeFileSchoolTest {

    private fun isSchool(o: StaticSceneObject) =
        o.type == SceneObjectType.SKYSCRAPER && SceneObjectRenderer.variantFor(o) == SceneSpace.SceneVariant.SCHOOL

    private fun fixture(): JSONObject {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("themes/store-saved-by-v5.5.json")) {
            "test resource themes/store-saved-by-v5.5.json"
        }
        return JSONObject(stream.bufferedReader().readText())
    }

    /** The file v5.5 exported for [entry]: kind, version 1, name, theme, layout, customization. */
    private fun fileExportedBy55(entry: JSONObject): String = JSONObject().apply {
        put("kind", ThemeShare.DOCUMENT_KIND)
        put("schemaVersion", 1)
        put("name", entry.getString("name"))
        put("theme", JSONObject(entry.getJSONObject("theme").toString()))
        put("layout", JSONObject(entry.getJSONObject("layout").toString()))
        put("customization", JSONObject(entry.getJSONObject("customization").toString()))
    }.toString()

    private fun imported(file: String): SceneObjectLayout =
        (parseThemeShare(file) as ThemeParseResult.Ok).share.layout

    @Test
    fun `the theme v5_5 saved, exported as a file and imported, gets the school the store gives it`() {
        val store = fixture()
        val entry = store.getJSONArray("customThemes").getJSONObject(0)
        val before = entry.getJSONObject("layout").getJSONArray("staticObjects")
        assertEquals("the fixture's street", 29, before.length())
        assertEquals("the fixture has no school", 0, (0 until before.length()).count {
            isSchool(com.paperscrape.livewallpaper.engine.staticSceneObjectFromJson(before.getJSONObject(it)))
        })

        val file = imported(fileExportedBy55(entry))
        val saved = customThemeDataFromJsonString(store.toString()).customThemes.single().layout

        assertEquals("one object is added, a school", 30, file.staticObjects.size)
        assertEquals("exactly one school", 1, file.staticObjects.count { isSchool(it) })
        assertEquals("the file's street is the saved theme's street, object for object", saved.staticObjects, file.staticObjects)
        assertEquals("and its road", saved.cars, file.cars)
    }

    /** Every built-in's street with the school taken out -- the pre-v5.6 shape -- round-trips to the store's answer. */
    @Test
    fun `on every built-in street an old file and an old saved theme end the same`() {
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id)
            val street = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor).staticObjects
                .filter { c.keepCandidate(it) }
                .filterNot { isSchool(it) }
            val layout = SceneObjectLayout(staticObjects = street, cars = emptyList())
            val share = ThemeShare(
                schemaVersion = 1, appVersionName = "5.5", exportedAtMillis = 0L, sourceThemeId = theme.id,
                name = "Old ${theme.displayName}", theme = theme, layout = layout, customization = c,
            )
            val file = imported(share.toJsonString())

            val entry = CustomThemeEntry(
                id = "custom:old", name = "Old", theme = theme.copy(id = "custom:old"), layout = layout,
                customization = SceneCustomization.DEFAULT,
            )
            val atFour = JSONObject(CustomThemeData(customThemes = listOf(entry)).toJsonString()).put("schemaVersion", 4)
            val saved = customThemeDataFromJsonString(atFour.toString()).customThemes.single().layout

            assertEquals("${theme.id}: one school added", street.size + 1, file.staticObjects.size)
            assertEquals("${theme.id}: what the file had comes back identical, in order", street, file.staticObjects.dropLast(1))
            assertTrue("${theme.id}: the added object is a school", isSchool(file.staticObjects.last()))
            assertEquals("${theme.id}: the same school the store adds", saved.staticObjects, file.staticObjects)
        }
    }

    /** A file from v5.6 on has its school, and imports exactly as it was written. */
    @Test
    fun `a file that has its school imports unchanged`() {
        for (theme in ThemeCatalog.ALL) {
            val share = ThemeShare.of(theme.id, theme.displayName, defaultCustomizationFor(theme.id), "5.8", 0L)
            assertEquals(theme.id, share.layout, imported(share.toJsonString()))
        }
    }
}
