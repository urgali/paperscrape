package com.paperscrape.livewallpaper.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.ui.LocationMode
import com.paperscrape.livewallpaper.ui.SettingsUiModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **The saved device fix says when the phone took it** (decision 112). A position is saved with the time the
 * phone took it -- the save's time less the fix's age ([fixTakenAt]) -- under a key of its own, `device_fix_taken_at`,
 * so a stale position the phone held is shown with its true age ("Resolved from the GPS receiver, 3 h ago").
 * `device_fix_at` keeps the save's time. **A store whose fix was saved with the save's time alone**
 * (no `device_fix_taken_at`) reads 0 there and the settings show the line with no age until a new fix
 * arrives; reading it writes nothing.
 *
 * *Update location now* writes its position and the refresh ask in one edit; *Update weather now* writes the ask alone.
 */
class DeviceFixTakenAtTest {

    private val minute = 60_000L
    private val hour = 60 * minute

    private lateinit var dir: File
    private lateinit var file: File
    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var prefs: WallpaperPrefs

    @Before
    fun open() {
        dir = kotlin.io.path.createTempDirectory("fix-taken-at").toFile()
        file = File(dir, "prefs.preferences_pb")
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = PreferenceDataStoreFactory.create(scope = scope) { file }
        prefs = WallpaperPrefs(store)
    }

    @After
    fun close() {
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun `a fix is saved with the time the phone took it, and the line says its age`() = runBlocking {
        val before = System.currentTimeMillis()
        prefs.setResolvedGpsLocation(45.46f, 9.19f, ageMillis = 3 * hour)
        val after = System.currentTimeMillis()
        val saved = prefs.settingsFlow.first()
        assertEquals(45.46f, saved.resolvedGpsLatitude!!, 0f)
        assertEquals(9.19f, saved.resolvedGpsLongitude!!, 0f)
        assertTrue("taken three hours before it was saved", saved.deviceFixTakenAtMillis in (before - 3 * hour)..(after - 3 * hour))
        assertTrue("saved now", saved.deviceFixTimestampMillis in before..after)
        assertEquals(
            "Resolved from the GPS receiver, 3 h ago",
            SettingsUiModel.devicePositionLine(true, LocationMode.GPS, saved.deviceFixTakenAtMillis, after),
        )
        // A fresh one replaces it, with its own age.
        prefs.setResolvedGpsLocation(45.07f, 7.69f, ageMillis = 0L)
        val fresh = prefs.settingsFlow.first()
        assertEquals(
            "Approximate, from cell towers and Wi-Fi, just now",
            SettingsUiModel.devicePositionLine(true, LocationMode.NETWORK, fresh.deviceFixTakenAtMillis, System.currentTimeMillis()),
        )
    }

    @Test
    fun `a position from the future is saved as just taken`() {
        assertEquals(1_000_000L, fixTakenAt(nowMillis = 1_000_000L, ageMillis = -2 * hour))
        assertEquals(1_000_000L - 5 * minute, fixTakenAt(nowMillis = 1_000_000L, ageMillis = 5 * minute))
        assertEquals(1_000_000L, fixTakenAt(nowMillis = 1_000_000L, ageMillis = 0L))
    }

    @Test
    fun `a store whose fix was saved with its save time alone shows no age, and reading it writes nothing`() = runBlocking {
        // A store with the save's time alone: position, save time, GPS chosen.
        store.edit {
            it[floatPreferencesKey("resolved_gps_lat")] = 41.90f
            it[floatPreferencesKey("resolved_gps_lon")] = 12.50f
            it[longPreferencesKey("device_fix_at")] = 1_700_000_000_000L
            it[booleanPreferencesKey("use_location")] = true
            it[stringPreferencesKey("device_location_kind")] = "gps"
        }
        val bytes = file.readBytes()
        val read = prefs.settingsFlow.first()
        assertEquals(41.90f, read.resolvedGpsLatitude!!, 0f)
        assertEquals("the save's time", 1_700_000_000_000L, read.deviceFixTimestampMillis)
        assertEquals("not known when the phone took it", 0L, read.deviceFixTakenAtMillis)
        assertEquals(
            "the line with no age",
            "Resolved from the GPS receiver",
            SettingsUiModel.devicePositionLine(true, LocationMode.GPS, read.deviceFixTakenAtMillis, System.currentTimeMillis()),
        )
        assertArrayEquals("the store is untouched by reading it", bytes, file.readBytes())
        // The first new fix brings the age.
        prefs.setResolvedGpsLocation(41.90f, 12.50f, ageMillis = 2 * minute)
        assertTrue(prefs.settingsFlow.first().deviceFixTakenAtMillis > 0L)
    }

    @Test
    fun `Update location now saves its position and the ask in one edit, Update weather now the ask alone`() = runBlocking {
        val start = prefs.settingsFlow.first()
        assertEquals("never asked", 0L, start.refreshAskedAtMillis)
        prefs.setDevicePositionFoundNow(45.46f, 9.19f, ageMillis = 20_000L)
        val found = prefs.settingsFlow.first()
        assertTrue(found.refreshAskedAtMillis > 0L)
        assertEquals(45.46f, found.resolvedGpsLatitude!!, 0f)
        assertEquals("taken 20 s before the save, which is the ask", found.refreshAskedAtMillis - 20_000L, found.deviceFixTakenAtMillis)
        assertEquals(found.refreshAskedAtMillis, found.deviceFixTimestampMillis)

        Thread.sleep(2)
        prefs.askRefreshNow()
        val asked = prefs.settingsFlow.first()
        assertTrue("a new ask", asked.refreshAskedAtMillis > found.refreshAskedAtMillis)
        assertEquals("the position untouched", found.resolvedGpsLatitude, asked.resolvedGpsLatitude)
        assertEquals(found.deviceFixTakenAtMillis, asked.deviceFixTakenAtMillis)
        assertEquals(found.deviceFixTimestampMillis, asked.deviceFixTimestampMillis)
    }

    @Test
    fun `a backup carries neither the time the fix was taken nor the ask, and a restore leaves them`() = runBlocking {
        prefs.setDevicePositionFoundNow(45.46f, 9.19f, ageMillis = 0L)
        val settings = prefs.settingsFlow.first()
        val json = AppBackup.from(settings, CustomThemeData.EMPTY, "9.9", 0L).toJsonString()
        for (name in listOf("device_fix_taken_at", "deviceFixTakenAt", "refresh_asked_at", "refreshAskedAt")) {
            assertFalse("the backup names $name", json.contains(name))
        }
        prefs.replaceAll(AppBackup.from(settings.copy(themeId = "winter"), CustomThemeData.EMPTY, "9.9", 0L).settings, emptyMap())
        val restored = prefs.settingsFlow.first()
        assertEquals("winter", restored.themeId)
        assertEquals(settings.deviceFixTakenAtMillis, restored.deviceFixTakenAtMillis)
        assertEquals(settings.refreshAskedAtMillis, restored.refreshAskedAtMillis)
    }
}
