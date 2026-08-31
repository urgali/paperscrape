package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BCK-05: a blob the app cannot read is not a blob the app may overwrite.
 *
 * `customThemeDataFromJsonString` answers `EMPTY` for anything it cannot parse, which is the right
 * answer for a reader -- a corrupt store shows no custom themes instead of crashing the wallpaper.
 * `CustomThemeStore.update` is a read-modify-write and used the same function, so the next
 * preference the user touched read `EMPTY`, transformed it, and wrote the result back over bytes
 * that still held their themes. "Cannot read your themes" became "your themes are gone" in one tap.
 *
 * [customThemeDataOrNull] is the distinction the write path needs: absent is `EMPTY`, unreadable is
 * `null`, and `null` means leave the file alone.
 */
class CorruptThemeStoreTest {

    @Test
    fun `an absent store reads as empty and may be written`() {
        assertEquals(CustomThemeData.EMPTY, customThemeDataOrNull(null))
        assertEquals(CustomThemeData.EMPTY, customThemeDataOrNull(""))
        assertEquals(CustomThemeData.EMPTY, customThemeDataOrNull("   "))
    }

    @Test
    fun `a document that legitimately holds no themes may be written`() {
        // Not the same as unreadable: this is what the store looks like after the user deletes
        // their last saved theme, and an edit after that must still work.
        val empty = """{"schemaVersion":1,"overrides":{},"customThemes":[]}"""
        assertEquals(CustomThemeData.EMPTY, customThemeDataOrNull(empty))
    }

    @Test
    fun `an unreadable blob refuses to be overwritten`() {
        for (corrupt in listOf("this is not json at all", "{ truncated", """{"overrides":""")) {
            assertNull("'$corrupt' must not read as an empty store", customThemeDataOrNull(corrupt))
        }
    }

    @Test
    fun `a readable blob written by this app reads back`() {
        val theme = ThemeCatalog.byId("sunset")
        val entry = CustomThemeEntry(
            id = "sunset",
            name = theme.displayName,
            theme = theme,
            layout = SceneObjectCatalog.layoutFor("sunset", theme.accentColor),
            customization = defaultCustomizationFor("sunset"),
        )
        val data = CustomThemeData(overrides = mapOf("sunset" to entry), customThemes = emptyList())
        val round = customThemeDataOrNull(data.toJsonString())
        assertNotNull("a document this app wrote must read back", round)
        assertEquals(setOf("sunset"), round!!.overrides.keys)
    }

    @Test
    fun `the store's write path refuses an unreadable blob`() {
        // The coupling, read from the source: `update` must go through customThemeDataOrNull and
        // return early on null. A future edit that goes back to the lenient reader restores the
        // defect, and this is what stands in the way.
        val source = storeSource().readText()
        val start = source.indexOf("private suspend fun update(")
        val body = source.substring(start, source.indexOf("fun setOverride", start))
        assertEquals(
            "update must not use the lenient reader:" + body,
            0,
            Regex("customThemeDataFromJsonString").findAll(body).count(),
        )
        assertTrue("update must use customThemeDataOrNull", body.contains("customThemeDataOrNull"))
        assertTrue("update must leave an unreadable blob alone", body.contains("return@edit"))
    }

    /** Walks up for the module root, the way `SkyscraperWindowTest` finds the renderer. */
    private fun storeSource(): java.io.File {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/prefs/CustomThemeStore.kt"
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = java.io.File(dir, prefix + suffix)
                if (candidate.isFile) return candidate
            }
            dir = dir.parentFile
        }
        error("could not locate " + suffix)
    }
}
