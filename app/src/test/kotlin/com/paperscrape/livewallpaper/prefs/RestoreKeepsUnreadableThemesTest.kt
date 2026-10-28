package com.paperscrape.livewallpaper.prefs

import com.paperscrape.livewallpaper.engine.CUSTOM_THEME_SCHEMA_VERSION
import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.customThemeDataFromJsonString
import com.paperscrape.livewallpaper.engine.toJsonString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Restoring a backup does not delete the saved themes this build cannot read.**
 *
 * A restore writes the whole store from the backup, and a backup taken by a build that did not carry those themes --
 * yesterday's on this phone -- would write over the only copy of them. Nothing lists or draws them, so nothing the user
 * chose in restoring was about them: the restore keeps the phone's own ([AppBackup.themesWrittenOver], which
 * `BackupRepository.import` writes), once, beside any the backup carries.
 */
class RestoreKeepsUnreadableThemesTest {

    private val newer = """{"id":"custom:newer","name":"Newer","theme":{"id":"custom:newer"},""" +
        """"layout":{"staticObjects":[{"type":"FOUNTAIN","depthFraction":0.1,"tileFractionX":0.5}],"cars":[]}}"""
    private val other = newer.replace("custom:newer", "custom:other").replace("\"Newer\"", "\"Other\"")

    private fun storeWith(vararg entries: String) =
        customThemeDataFromJsonString("""{"schemaVersion":$CUSTOM_THEME_SCHEMA_VERSION,"overrides":{},"customThemes":[${entries.joinToString(",")}]}""")

    private fun backupOf(data: CustomThemeData) = (parseAppBackup(AppBackup.from(WallpaperSettings(), data, "5.17", 0L).toJsonString()) as BackupParseResult.Ok).backup

    @Test
    fun `a backup without them restored over the phone keeps the phone's`() {
        val phone = storeWith(newer)
        val yesterday = backupOf(CustomThemeData.EMPTY)
        val written = yesterday.themesWrittenOver(phone).toJsonString()
        assertTrue("the phone's theme of a newer build is gone: $written", written.contains(newer))
    }

    @Test
    fun `one the backup carries too is kept once`() {
        val phone = storeWith(newer)
        val written = backupOf(phone).themesWrittenOver(phone)
        assertEquals(1, written.unreadable.size)
    }

    @Test
    fun `the backup's and the phone's are both kept`() {
        val written = backupOf(storeWith(other)).themesWrittenOver(storeWith(newer)).toJsonString()
        assertTrue(written.contains(newer))
        assertTrue(written.contains(other))
        assertEquals(2, customThemeDataFromJsonString(written).unreadable.size)
    }

    @Test
    fun `with nothing to keep the backup's themes are written as they are`() {
        val backup = backupOf(CustomThemeData.EMPTY)
        assertSame(backup.customThemeData, backup.themesWrittenOver(CustomThemeData.EMPTY))
    }
}
