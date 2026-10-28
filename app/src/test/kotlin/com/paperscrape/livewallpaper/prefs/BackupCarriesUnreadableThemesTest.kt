package com.paperscrape.livewallpaper.prefs

import com.paperscrape.livewallpaper.engine.CUSTOM_THEME_SCHEMA_VERSION
import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.customThemeDataFromJsonString
import com.paperscrape.livewallpaper.engine.toJsonString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A backup carries the saved themes this build cannot read.**
 *
 * A saved theme written by a newer build -- an object of a type this one does not have -- is kept in the store byte for
 * byte, "so that no write can lose them" ([CustomThemeData.unreadable]); a backup with only the themes it can read
 * would leave it off a new phone, and nothing would say so. The backup carries each one as the text the store keeps,
 * and the store it is restored into keeps it again, still byte for byte, for a build that can read it. A backup that
 * leaves it out fails here on its assertion: the theme's text is not in what the backup restores. That a restore keeps
 * the phone's own such themes is `RestoreKeepsUnreadableThemesTest`.
 */
class BackupCarriesUnreadableThemesTest {

    /**
     * A saved theme with a building of a type this build does not have, written with the characters org.json rewrites
     * when it re-serialises: an escaped slash, a quote, a number in exponent form and one with a trailing zero.
     */
    private val newer = """{"id":"custom:newer","name":"A \/ B é \"q\"","theme":{"id":"custom:newer"},""" +
        """"layout":{"staticObjects":[{"type":"FOUNTAIN","depthFraction":1.0E-1,"tileFractionX":0.50}],"cars":[]}}"""

    private val store = """{"schemaVersion":$CUSTOM_THEME_SCHEMA_VERSION,"overrides":{},"customThemes":[$newer]}"""

    private fun restored(data: CustomThemeData): CustomThemeData {
        val text = AppBackup.from(WallpaperSettings(), data, "5.17", 0L).toJsonString()
        val parsed = parseAppBackup(text)
        assertTrue("the backup must import: $parsed", parsed is BackupParseResult.Ok)
        return (parsed as BackupParseResult.Ok).backup.customThemeData
    }

    @Test
    fun `the store keeps the theme it cannot read`() {
        val data = customThemeDataFromJsonString(store)
        assertEquals(1, data.unreadable.size)
        assertTrue(data.toJsonString().contains(newer))
    }

    @Test
    fun `a backup carries it, and the restored store holds its bytes`() {
        val written = restored(customThemeDataFromJsonString(store)).toJsonString()
        assertTrue("the theme a newer build saved is not in what the backup restores: $written", written.contains(newer))
    }

    @Test
    fun `restored, it is kept by every write after`() {
        var written = restored(customThemeDataFromJsonString(store)).toJsonString()
        for (round in 1..3) {
            val read = customThemeDataFromJsonString(written)
            assertEquals("round $round", 1, read.unreadable.size)
            written = read.toJsonString()
            assertTrue("round $round: $written", written.contains(newer))
        }
    }

    /**
     * **A backup's unreadable entry that is not a JSON object is dropped**: kept, it would be pasted into the store's document
     * as it is, and a document that is not JSON any more loses every saved theme.
     */
    @Test
    fun `an unreadable entry that is not a JSON object leaves the store readable`() {
        val good = AppBackup.from(WallpaperSettings(), customThemeDataFromJsonString(store), "5.17", 0L).toJsonString()
        val root = org.json.JSONObject(good)
        root.getJSONArray("unreadableThemes").put("{").put("not json at all").put("{}]").put("{\"a\":1} x")
        val parsed = parseAppBackup(root.toString())
        assertTrue("the backup must import: $parsed", parsed is BackupParseResult.Ok)
        val backup = (parsed as BackupParseResult.Ok).backup
        val written = backup.themesWrittenOver(CustomThemeData.EMPTY).toJsonString()
        val read = com.paperscrape.livewallpaper.engine.customThemeDataOrNull(written)
        assertTrue("the store's document is not readable any more: $written", read != null)
        assertEquals("the theme it could not read, and only it", 1, read!!.unreadable.size)
    }

    @Test
    fun `a backup with nothing it cannot read is written as it was`() {
        val text = AppBackup.from(WallpaperSettings(), CustomThemeData.EMPTY, "5.17", 0L).toJsonString()
        assertFalse(text.contains("unreadable"))
    }
}
