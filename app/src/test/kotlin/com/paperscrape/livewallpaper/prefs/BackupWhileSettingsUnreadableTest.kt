package com.paperscrape.livewallpaper.prefs

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * **A backup taken while the settings cannot be read is not made.**
 *
 * [WallpaperPrefs.settingsFlow] answers a read that fails for an I/O reason with a fresh install's settings, then reads
 * again ([PrefsRecovery.recoveringFromReadErrors]): right for the wallpaper, which draws them for a moment, wrong for a
 * backup, which keeps them, says "Backup saved." and restores them later over the user's own. So
 * [BackupRepository.export] gives no document while the store cannot be read, and the settings screen writes nothing and
 * says why. An export that takes the flow's first value writes the factory settings, and fails the first test here.
 *
 * [CustomThemeStore] asks its `Context` for one thing, the folder of its file: [FilesDirContext] answers that and nothing
 * else (the unit tests' `android.jar` keeps the constructors). That store is one per process, bound to the first
 * `Context` that reads it, so every test here hands it the same one, over a folder nothing writes: the saved themes read
 * as none.
 */
class BackupWhileSettingsUnreadableTest {

    private class Store(override val data: Flow<Preferences>) : DataStore<Preferences> {
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw AssertionError("not written")
    }

    private class FilesDirContext(private val dir: File) : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = dir
    }

    private companion object {
        val savedThemesContext: Context =
            FilesDirContext(File(System.getProperty("java.io.tmpdir"), "paperscrape-no-saved-themes-${System.nanoTime()}"))
    }

    private val themeId = stringPreferencesKey("theme_id")

    private fun repository(settings: Flow<Preferences>) =
        BackupRepository(WallpaperPrefs(Store(settings)), CustomThemeStore(savedThemesContext), "5.17", now = { 0L })

    @Test
    fun `a backup taken while the settings cannot be read is not made`() = runBlocking {
        val unreadable = flow<Preferences> { throw IOException("storage briefly unavailable") }
        val exported: String? = repository(unreadable).export()
        assertNull("a backup was written from a fresh install's settings", exported)
        val snapshot: AppBackup? = repository(unreadable).snapshot()
        assertNull("a snapshot was taken of a fresh install's settings", snapshot)
    }

    @Test
    fun `a backup taken while the saved themes cannot be read is not made`() = runBlocking {
        val themesUnreadable = object : CustomThemeStore(savedThemesContext) {
            override suspend fun readOnce(): com.paperscrape.livewallpaper.engine.CustomThemeData? = null
        }
        val repository = BackupRepository(
            WallpaperPrefs(Store(flowOf(mutablePreferencesOf(themeId to "autumn")))), themesUnreadable, "5.17", now = { 0L },
        )
        assertNull("a backup was written without the saved themes", repository.export())
    }

    @Test
    fun `a backup taken while they can be read is the user's`() = runBlocking {
        val exported: String? = repository(flowOf(mutablePreferencesOf(themeId to "autumn"))).export()
        assertNotNull("no backup of settings that were read", exported)
        val parsed = parseAppBackup(exported)
        assertTrue("$parsed", parsed is BackupParseResult.Ok)
        assertEquals("autumn", (parsed as BackupParseResult.Ok).backup.settings.themeId)
    }
}
