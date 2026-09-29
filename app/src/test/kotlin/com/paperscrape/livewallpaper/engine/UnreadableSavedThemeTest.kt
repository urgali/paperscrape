package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.prefs.AppBackup
import com.paperscrape.livewallpaper.prefs.BackupParseResult
import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import com.paperscrape.livewallpaper.prefs.parseAppBackup
import com.paperscrape.livewallpaper.prefs.toJsonString
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 18: one saved theme the app cannot read must not cost the user the others (v5.9A).
 *
 * Until v5.9A `customThemeDataFromJsonString` read the whole document inside one `catch`, so one
 * damaged entry read as `CustomThemeData.EMPTY`: every saved theme left the Themes screen, a
 * wallpaper set to one of them fell back to Sunset, and `CustomThemeStore.update` -- which refuses to
 * write over a document it cannot read (BCK-05) -- dropped every later edit without a word. The
 * three properties the repair owes, each with its own test here:
 *
 *  1. **the others are seen** -- [one unreadable theme no longer hides the others];
 *  2. **an edit is applied, not dropped** -- [an edit is applied rather than thrown away];
 *  3. **the unreadable theme survives a write byte for byte** -- [the unreadable theme survives
 *     every write byte for byte].
 *
 * **Written against the reader's public surface only** (`customThemeDataFromJsonString`,
 * `customThemeDataOrNull`, `toJsonString`), which v5.8 already had, so the same file runs red on the
 * v5.8 tree: that run is `consegna_v5_9a/registri/` of the round. `update` below is
 * `CustomThemeStore.update`'s body without the DataStore around it; `CorruptThemeStoreTest` holds the
 * store to that shape, and the instrumented `UnreadableSavedThemeStoreTest` runs the real store.
 *
 * The damage used is the one a real release could cause: a static object of a type this build does
 * not have (`FOUNTAIN`), which is what removing `SceneObjectType.DOG` did to every theme saved with
 * a dog in v29 (`CHANGELOG.md`, "Dogs -- fully deleted"), and what a future type would do after a
 * downgrade.
 */
class UnreadableSavedThemeTest {

    @Test
    fun `one unreadable theme no longer hides the others`() {
        val read = customThemeDataFromJsonString(document())

        assertEquals("the healthy overrides", setOf("christmas", "beach"), read.overrides.keys)
        assertEquals("the healthy standalone themes", listOf(MINE.id, OTHER.id), read.customThemes.map { it.id })
        assertEquals("My autumn", read.customThemes.first().name)
        assertEquals(
            "a saved theme set as the wallpaper must still resolve to itself, not to Sunset",
            listOf(MINE.id, OTHER.id), read.customThemes.map { it.theme.id },
        )
    }

    @Test
    fun `an edit is applied rather than thrown away`() {
        val written = update(document()) { data ->
            data.copy(customThemes = data.customThemes.map { if (it.id == OTHER.id) it.copy(name = "Renamed") else it })
        }
        assertNotNull("the edit was dropped: the store refused to write", written)

        val back = customThemeDataFromJsonString(written)
        assertEquals(listOf("My autumn", "Renamed"), back.customThemes.map { it.name })
        assertEquals(setOf("christmas", "beach"), back.overrides.keys)
    }

    @Test
    fun `the unreadable theme survives every write byte for byte`() {
        // The whole-store rewrite first: a failed backup import rolls back by writing what it read
        // (`BackupRepository.import` -> `CustomThemeStore.replaceAll`). On v5.8 that read was EMPTY.
        val rolledBack = customThemeDataFromJsonString(document()).toJsonString()
        assertKept("the rollback's rewrite", rolledBack)

        // Then the edit path, three times over, so a kept entry is shown to be stable and not
        // re-wrapped by each write.
        var doc = document()
        for (round in 1..3) {
            doc = update(doc) { data ->
                data.copy(customThemes = data.customThemes.map { if (it.id == OTHER.id) it.copy(name = "Round $round") else it })
            } ?: error("round $round: the edit was dropped")
            assertKept("after edit $round", doc)
        }
        assertEquals("Round 3", customThemeDataFromJsonString(doc).customThemes.last().name)
    }

    @Test
    fun `a new saved version of a built-in whose old one cannot be read is saved, and the old bytes stay`() {
        val written = update(document()) { data ->
            data.copy(overrides = data.overrides + ("winter" to entry("winter", "My winter")))
        }
        assertNotNull("the save was dropped", written)
        val back = customThemeDataFromJsonString(written)
        assertEquals("the user's new save is the one that is used", "My winter", back.overrides.getValue("winter").name)
        assertTrue("the unreadable old winter is still in the document", written!!.contains(BROKEN_OVERRIDE))
    }

    @Test
    fun `a document with nothing unreadable in it is written exactly as before`() {
        val data = CustomThemeData(
            overrides = mapOf("christmas" to CHRISTMAS, "beach" to BEACH),
            customThemes = listOf(MINE, OTHER),
        )
        // What `toJsonString` wrote until v5.9A, line for line.
        val before = JSONObject().apply {
            put("schemaVersion", CUSTOM_THEME_SCHEMA_VERSION)
            put("overrides", JSONObject().apply { data.overrides.forEach { (k, v) -> put(k, v.toJson()) } })
            put("customThemes", JSONArray(data.customThemes.map { it.toJson() }))
        }.toString()
        assertEquals(before, data.toJsonString())
        assertTrue(!data.toJsonString().contains("unreadable"))
    }

    @Test
    fun `a malformed container is kept rather than written over`() {
        val doc = """{"schemaVersion":5,"overrides":"$GARBAGE","customThemes":[${OTHER.toJson()}]}"""
        val written = update(doc) { data -> data.copy(customThemes = data.customThemes + MINE) }
        assertNotNull(written)
        assertEquals(listOf(OTHER.id, MINE.id), customThemeDataFromJsonString(written).customThemes.map { it.id })
        assertTrue("the malformed overrides were written over: $written", written!!.contains("\"$GARBAGE\""))
    }

    @Test
    fun `a kept theme that a later build can read comes back`() {
        // What a later build meets: the section an earlier one wrote, holding entries that now read.
        val doc = """{"schemaVersion":5,"overrides":{"christmas":${CHRISTMAS.toJson()}},"customThemes":[${MINE.toJson()}],""" +
            """"unreadableEntries":[{"from":"customThemes","schemaVersion":5,"entry":${OTHER.toJson()}},""" +
            """{"from":"overrides","key":"beach","schemaVersion":5,"entry":${BEACH.toJson()}}]}"""
        val read = customThemeDataFromJsonString(doc)
        assertEquals(listOf(MINE.id, OTHER.id), read.customThemes.map { it.id })
        assertEquals(setOf("christmas", "beach"), read.overrides.keys)
        assertTrue("a recovered theme leaves the section", !read.toJsonString().contains("unreadableEntries"))
    }

    @Test
    fun `a kept theme comes back migrated from the schema its bytes were written in`() {
        // Schema 1 stored a building's whole base size in `scale`; 1 -> 2 divides it back out.
        val base = SceneSpace.legacyBaseScaleFor(SceneObjectType.HOUSE)
        val legacy = OTHER.copy(
            layout = OTHER.layout.copy(staticObjects = listOf(StaticSceneObject(SceneObjectType.HOUSE, 0.2f, 0.3f, scale = base * 1.1f))),
        )
        val doc = """{"schemaVersion":5,"overrides":{},"customThemes":[],""" +
            """"unreadableEntries":[{"from":"customThemes","schemaVersion":1,"entry":${legacy.toJson()}}]}"""
        val scale = customThemeDataFromJsonString(doc).customThemes.single().layout.staticObjects.single().scale
        assertEquals(1.1f, scale, 1e-4f)
    }

    @Test
    fun `a kept override does not come back over a newer one`() {
        val older = entry("christmas", "Old christmas")
        val doc = """{"schemaVersion":5,"overrides":{"christmas":${CHRISTMAS.toJson()}},"customThemes":[],""" +
            """"unreadableEntries":[{"from":"overrides","key":"christmas","schemaVersion":5,"entry":${older.toJson()}}]}"""
        val read = customThemeDataFromJsonString(doc)
        assertEquals("the one saved since wins", CHRISTMAS.name, read.overrides.getValue("christmas").name)
        assertTrue("and the older one stays aside", read.toJsonString().contains("Old christmas"))
    }

    @Test
    fun `an unreadable theme's text is kept exactly, escapes and all`() {
        // The characters org.json rewrites when it re-serialises: an escaped slash, a \u escape, a
        // quote, a number in exponent form and one with a trailing zero.
        val odd = """{"id":"custom:odd","name":"A \/ B é \"q\"","theme":{"id":"custom:odd"},""" +
            """"layout":{"staticObjects":[{"type":"FOUNTAIN","depthFraction":1.0E-1,"tileFractionX":0.50}],"cars":[]}}"""
        val doc = """{"schemaVersion":5,"overrides":{},"customThemes":[${MINE.toJson()}, $odd ]}"""
        val written = update(doc) { data -> data.copy(customThemes = data.customThemes.map { it.copy(name = "Edited") }) }
        assertNotNull(written)
        assertTrue("the entry's own text is not in the document any more: $written", written!!.contains(odd))
    }

    @Test
    fun `a backup made while one theme cannot be read carries the others and can be restored`() {
        val text = AppBackup.from(WallpaperSettings(), customThemeDataFromJsonString(document()), "5.9", 0L).toJsonString()
        val parsed = parseAppBackup(text)
        assertTrue("the backup must import: $parsed", parsed is BackupParseResult.Ok)
        val themes = (parsed as BackupParseResult.Ok).backup.customThemeData
        assertEquals(setOf("christmas", "beach"), themes.overrides.keys)
        assertEquals(listOf(MINE.id, OTHER.id), themes.customThemes.map { it.id })
    }

    // ------------------------------------------------------------------ fixtures

    /** `CustomThemeStore.update`'s body: read with the strict reader, refuse on `null`, transform, write. */
    private fun update(raw: String, transform: (CustomThemeData) -> CustomThemeData): String? =
        customThemeDataOrNull(raw)?.let { transform(it).toJsonString() }

    private fun assertKept(where: String, written: String) {
        assertTrue("$where: the unreadable standalone theme's bytes are gone: $written", written.contains(BROKEN_CUSTOM))
        assertTrue("$where: the unreadable override's bytes are gone: $written", written.contains(BROKEN_OVERRIDE))
    }

    /**
     * Two healthy overrides, two healthy standalone themes, and one of each kind that cannot be
     * read, laid out as `toJsonString` lays a document out, with the damaged entries between the
     * healthy ones.
     */
    private fun document(): String =
        """{"schemaVersion":$CUSTOM_THEME_SCHEMA_VERSION,""" +
            """"overrides":{"christmas":${CHRISTMAS.toJson()},"winter":$BROKEN_OVERRIDE,"beach":${BEACH.toJson()}},""" +
            """"customThemes":[${MINE.toJson()},$BROKEN_CUSTOM,${OTHER.toJson()}]}"""

    private companion object {
        const val GARBAGE = "not an object"

        fun entry(id: String, name: String): CustomThemeEntry {
            val builtinId = if (id.startsWith("custom:")) "autumn" else id
            val theme = ThemeCatalog.ALL.first { it.id == builtinId }
            return CustomThemeEntry(
                id = id,
                name = name,
                theme = theme.copy(id = id, displayName = name),
                layout = SceneObjectLayout(
                    staticObjects = listOf(
                        StaticSceneObject(SceneObjectType.HOUSE, depthFraction = 0.12f, tileFractionX = 0.2f, scale = 0.9f),
                        StaticSceneObject(SceneObjectType.TREE, depthFraction = 0.30f, tileFractionX = 0.6f),
                    ),
                    cars = emptyList(),
                ),
                customization = defaultCustomizationFor(builtinId),
            )
        }

        /** An entry whose one building is of a type this build does not have. */
        fun broken(id: String, name: String): String {
            val text = entry(id, name).toJson().toString()
            val damaged = text.replaceFirst("\"HOUSE\"", "\"FOUNTAIN\"")
            check(damaged != text) { "the fixture did not damage the entry" }
            return damaged
        }

        val CHRISTMAS = entry("christmas", "My christmas")
        val BEACH = entry("beach", "My beach")
        val MINE = entry("custom:1001", "My autumn")
        val OTHER = entry("custom:1002", "Night walk")
        val BROKEN_CUSTOM = broken("custom:1003", "Old fountain")
        val BROKEN_OVERRIDE = broken("winter", "Old winter")
    }
}
