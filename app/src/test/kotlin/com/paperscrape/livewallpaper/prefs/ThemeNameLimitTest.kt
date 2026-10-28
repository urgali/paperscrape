package com.paperscrape.livewallpaper.prefs

import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A theme file's name is held to the limit of a name typed in the app.**
 *
 * The name field of "Save current look as..." and "Rename theme" takes at most [ThemeShare.MAX_NAME_CHARS] characters
 * ([cutToName]). Without a limit a theme file padded to megabytes in its name would put them into the saved themes,
 * read again at every change of one. An import reads a longer name cut to the limit, as the field would have cut it, and
 * imports. A [parseThemeShare] that keeps the name as written fails the first test here.
 */
class ThemeNameLimitTest {

    private fun fileNamed(name: String): String {
        val root = JSONObject(ThemeShare.of("beach", "Seaside", defaultCustomizationFor("beach"), "5.17", 0L).toJsonString())
        return root.put("name", name).toString()
    }

    private fun importedName(name: String): String {
        val parsed = parseThemeShare(fileNamed(name))
        assertTrue("$parsed", parsed is ThemeParseResult.Ok)
        return (parsed as ThemeParseResult.Ok).share.name
    }

    @Test
    fun `a name longer than the limit is cut to it, and the theme imports`() {
        val long = "Seaside at dusk ".repeat(1_000)
        assertEquals(long.take(ThemeShare.MAX_NAME_CHARS), importedName(long))
    }

    @Test
    fun `a name at the limit comes through whole`() {
        val atLimit = "S".repeat(ThemeShare.MAX_NAME_CHARS)
        assertEquals(atLimit, importedName(atLimit))
    }

    @Test
    fun `a cut never ends in half an emoji`() {
        val name = "S".repeat(ThemeShare.MAX_NAME_CHARS - 1) + "\uD83C\uDF0A" + "after"
        val cut = cutToName(name)
        assertEquals("S".repeat(ThemeShare.MAX_NAME_CHARS - 1), cut)
        assertTrue(cut.none { Character.isSurrogate(it) })
    }
}
