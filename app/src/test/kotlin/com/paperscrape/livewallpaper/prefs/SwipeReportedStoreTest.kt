package com.paperscrape.livewallpaper.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.paperscrape.livewallpaper.engine.CustomThemeData
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **"The home screen has moved the wallpaper with a swipe" is this phone's, and it stays** (v5.10E,
 * inventory I-222). The wallpaper writes it once ([WallpaperPrefs.setSwipeReported]) and *Swipe scroll*
 * reads on from then (`SettingsUiModel.swipeScroll`). It is runtime state of this phone, like the
 * resolved GPS fix: a backup does not carry it -- a backup from a phone whose home screen reports swipes,
 * restored on one whose home screen does not, would put the switch back on over nothing -- and a restore
 * does not take it away.
 */
class SwipeReportedStoreTest {

    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var prefs: WallpaperPrefs

    @Before
    fun open() {
        dir = kotlin.io.path.createTempDirectory("swipe").toFile()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "prefs.preferences_pb") }
        prefs = WallpaperPrefs(store)
    }

    @After
    fun close() {
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun `a new install has not seen a swipe, and once seen it is kept`() = runBlocking {
        assertFalse(prefs.settingsFlow.first().swipeReported)
        prefs.setSwipeReported()
        assertTrue(prefs.settingsFlow.first().swipeReported)
        prefs.setSwipeScroll(false)
        assertTrue("the switch's own value is another key", prefs.settingsFlow.first().swipeReported)
    }

    @Test
    fun `a backup does not carry it, and a restore leaves it as this phone has it`() = runBlocking {
        prefs.setSwipeReported()
        val settings = prefs.settingsFlow.first()
        val backup = AppBackup.from(settings, CustomThemeData.EMPTY, "5.10", 0L)
        val json = backup.toJsonString()
        assertFalse("the backup names it", json.contains("swipe_reported") || json.contains("swipeReported"))
        assertTrue("while it carries the switch", JSONObject(json).toString().contains("swipeScroll"))
        // Restored on this phone: the phone's own fact stands.
        prefs.replaceAll(backup.settings, emptyMap())
        assertTrue(prefs.settingsFlow.first().swipeReported)
    }
}
