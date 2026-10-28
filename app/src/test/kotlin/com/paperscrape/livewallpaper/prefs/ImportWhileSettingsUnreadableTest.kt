package com.paperscrape.livewallpaper.prefs

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.paperscrape.livewallpaper.engine.CustomThemeData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * **A backup is not imported while this install's settings, or its saved themes, cannot be read.**
 * [WallpaperPrefs.settingsFlow] answers a read that fails for an I/O reason with a fresh install's settings: an import
 * read through it would take a backup's absent fields from those, and a write that failed half way would be put back to
 * them, over the user's own. The saved themes read so ([CustomThemeStore.readOnce] null) are no themes at all: the
 * import would write the backup's over the themes this build cannot read, which stay only when they are read, and a
 * failed write would put back none. So the import is refused before anything is written, and the settings screen says
 * why ([BackupImportError.Unreadable]).
 *
 * The saved themes' store is built as `BackupWhileSettingsUnreadableTest` builds it, over a folder nothing writes; the
 * settings store counts its writes, and fails each one, so an import that went ahead would be seen here whatever it
 * wrote first.
 */
class ImportWhileSettingsUnreadableTest {

    private class Store(override val data: Flow<Preferences>) : DataStore<Preferences> {
        var writes = 0
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            writes++
            throw IOException("the store cannot be written either")
        }
    }

    private class FilesDirContext(private val dir: File) : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = dir
    }

    private companion object {
        val savedThemesContext: Context =
            FilesDirContext(File(System.getProperty("java.io.tmpdir"), "paperscrape-no-saved-themes-import-${System.nanoTime()}"))
    }

    @Test
    fun `an import while the settings cannot be read is refused and writes nothing`() = runBlocking {
        val store = Store(flow { throw IOException("storage briefly unavailable") })
        val repository = BackupRepository(WallpaperPrefs(store), CustomThemeStore(savedThemesContext), "5.17", now = { 0L })
        // A whole, valid backup: refused for the store, not for the document.
        val document = AppBackup.from(WallpaperSettings(themeId = "autumn"), CustomThemeData.EMPTY, "5.17", 0L).toJsonString()
        val result = repository.import(document)
        assertEquals(
            "an import was attempted from a fresh install's settings",
            BackupRepository.ImportResult.Refused(BackupImportError.Unreadable),
            result,
        )
        assertEquals("the settings store was written", 0, store.writes)
    }

    @Test
    fun `an import while the saved themes cannot be read is refused and writes nothing`() = runBlocking {
        val store = Store(flowOf(mutablePreferencesOf(stringPreferencesKey("theme_id") to "autumn")))
        val themesUnreadable = object : CustomThemeStore(savedThemesContext) {
            override suspend fun readOnce(): CustomThemeData? = null
        }
        val repository = BackupRepository(WallpaperPrefs(store), themesUnreadable, "5.17", now = { 0L })
        val document = AppBackup.from(WallpaperSettings(themeId = "winter"), CustomThemeData.EMPTY, "5.17", 0L).toJsonString()
        val result = repository.import(document)
        assertEquals(
            "an import was attempted over saved themes that were not read",
            BackupRepository.ImportResult.Refused(BackupImportError.Unreadable),
            result,
        )
        assertEquals("the settings store was written", 0, store.writes)
    }
}
