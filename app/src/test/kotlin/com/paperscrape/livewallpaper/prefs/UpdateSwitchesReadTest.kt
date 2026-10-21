package com.paperscrape.livewallpaper.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

/**
 * **A passing read error of the settings does not stop the update checks** (v5.15C2, inventory I-968).
 *
 * The update job reads the two update switches at every run and whenever it is reconciled
 * ([WallpaperPrefs.readUpdateSwitches]). Until v5.15C2 the read went through [PrefsRecovery.recoveringFromReadErrors],
 * which answers an `IOException` with a fresh install's preferences "for this emission only" -- right for a flow that
 * will read again, wrong for one read that is acted on: both switches read off, and the job was cancelled until the
 * next start of the wallpaper or of the settings. Now the read says it could not read (null), and the job is left as
 * it is (`UpdateCheckSchedule.Reconcile.LEAVE`, `Before.UNREAD`).
 */
class UpdateSwitchesReadTest {

    private class Store(override val data: Flow<Preferences>) : DataStore<Preferences> {
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw AssertionError("not written")
    }

    private val check = booleanPreferencesKey("automatic_update_check")
    private val notify = booleanPreferencesKey("update_notifications")

    private fun read(data: Flow<Preferences>) = runBlocking { WallpaperPrefs(Store(data)).readUpdateSwitches() }

    @Test
    fun `the switches as stored`() {
        assertEquals(WallpaperPrefs.UpdateSwitches(true, true), read(flowOf(mutablePreferencesOf(check to true, notify to true))))
        assertEquals(WallpaperPrefs.UpdateSwitches(true, false), read(flowOf(mutablePreferencesOf(check to true))))
        assertEquals("a fresh install: both off", WallpaperPrefs.UpdateSwitches(false, false), read(flowOf(emptyPreferences())))
    }

    @Test
    fun `a read that fails for a passing reason is not read as both off`() {
        assertNull(read(flow { throw IOException("storage briefly unavailable") }))
        assertNull("the subclasses DataStore reports", read(flow { throw FileNotFoundException("no such file") }))
    }

    @Test
    fun `an error nobody predicted still propagates`() {
        try {
            read(flow { throw IllegalStateException("a bug") })
            fail("swallowed")
        } catch (_: IllegalStateException) {
        }
    }
}
