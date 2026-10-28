package com.paperscrape.livewallpaper.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.paperscrape.livewallpaper.prefs.PrefsRecovery.recoveringFromReadErrors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.IOException

/**
 * **After a read of the settings that fails for an I/O reason, the settings are read again.**
 *
 * [PrefsRecovery.recoveringFromReadErrors] answers an `IOException` with a fresh install's preferences for that
 * emission only, and reads the store again after a wait, so the next successful read brings the real settings back. A plain `catch` would end the flow from the store there: `settingsFlow` would live on only
 * through its `combine` with the saved themes' generation, giving the defaults again at every change of it, and the
 * wallpaper would draw Sunset with the factory settings until its engine is made again.
 *
 * Over a store whose first read fails and whose second reads Autumn, on virtual time ([WallpaperPrefs.settingsFlow]
 * and [PrefsRecovery.recoveringFromReadErrors]): where the store is not read again the second value never comes, and
 * the test fails on its assertion when the wait is over.
 */
class SettingsReadAgainAfterAReadErrorTest {

    private class Store(override val data: Flow<Preferences>) : DataStore<Preferences> {
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw AssertionError("not written")
    }

    private val themeId = stringPreferencesKey("theme_id")

    /** A store's data whose first [failures] reads fail as storage briefly unavailable, and whose next ones read Autumn. */
    private fun flaky(failures: Int): Flow<Preferences> {
        var reads = 0
        return flow {
            if (reads++ < failures) throw IOException("storage briefly unavailable")
            emit(mutablePreferencesOf(themeId to "autumn"))
        }
    }

    @Test
    fun `the wallpaper's settings come back after a read that failed`() = runTest {
        val seen = withTimeoutOrNull(10 * 60 * 1000L) {
            WallpaperPrefs(Store(flaky(failures = 1))).settingsFlow.take(2).toList()
        }
        assertNotNull("the settings were never read again: the defaults stayed", seen)
        assertEquals("a fresh install's first, while the read fails", "sunset", seen!![0].themeId)
        assertEquals("then the user's", "autumn", seen[1].themeId)
    }

    @Test
    fun `reads that go on failing are tried again, and the defaults stand in once`() = runTest {
        val seen = withTimeoutOrNull(10 * 60 * 1000L) {
            flaky(failures = 5).recoveringFromReadErrors().take(2).toList()
        }
        assertNotNull("the store was not read again", seen)
        assertEquals(listOf(emptyPreferences(), mutablePreferencesOf(themeId to "autumn")), seen)
    }
}
