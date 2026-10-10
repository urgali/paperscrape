package com.paperscrape.livewallpaper.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.paperscrape.livewallpaper.engine.DEFAULT_BUSINESS_CLOSE_HOUR
import com.paperscrape.livewallpaper.engine.DEFAULT_BUSINESS_OPEN_HOUR
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import com.paperscrape.livewallpaper.engine.toJson
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **A preference store written before v5.12, read by v5.12**: the opening hours were one setting for
 * the shops and the towers, and are two groups since. The maintainer's decision of 2026-10-09:
 * whoever had the hours on finds them on in both groups, at the same hours -- the settings of every
 * theme, its archive, and, from then on, each group by itself. The keys are planted by their names on
 * disk, the contract with an install that updates, as `ShopsPreferenceStoreTest` does for the shops'
 * colours. Saved themes, theme files and backups are JSON, and `OpeningHoursGroupsTest` holds those.
 */
class OpeningHoursPreferenceStoreTest {

    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var prefs: WallpaperPrefs

    @Before
    fun open() {
        dir = kotlin.io.path.createTempDirectory("hours-store").toFile()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "prefs.preferences_pb") }
        prefs = WallpaperPrefs(store)
    }

    @After
    fun close() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private val legacyEnabled = booleanPreferencesKey("business_hours_enabled")
    private val legacyOpen = floatPreferencesKey("business_open_hour")
    private val legacyClose = floatPreferencesKey("business_close_hour")

    /** The live edit of [theme] as a 5.11 install left it: the hours on, at 08:15 until 01:45. */
    private suspend fun plantLiveEditBy511(theme: String) = store.edit {
        it[stringPreferencesKey("pending_customization_theme_id")] = theme
        it[legacyEnabled] = true
        it[legacyOpen] = 8.25f
        it[legacyClose] = 1.75f
    }

    /** What the wallpaper draws [theme] with: its stored customization, or -- with none -- its defaults. */
    private suspend fun customization(theme: String): SceneCustomization =
        prefs.settingsFlow.first().themeCustomizations[theme] ?: defaultCustomizationFor(theme)

    private fun assertBothGroups(c: SceneCustomization, enabled: Boolean, open: Float, close: Float) {
        assertEquals("the shops' switch", enabled, c.shopHoursEnabled)
        assertEquals("the shops open", open, c.shopOpenHour, 0f)
        assertEquals("the shops close", close, c.shopCloseHour, 0f)
        assertEquals("the towers' switch", enabled, c.towerHoursEnabled)
        assertEquals("the towers open", open, c.towerOpenHour, 0f)
        assertEquals("the towers close", close, c.towerCloseHour, 0f)
    }

    @Test
    fun `a live edit with the hours on reads them on in both groups, at its hours`() = runBlocking {
        plantLiveEditBy511("autumn")
        assertBothGroups(customization("autumn"), true, 8.25f, 1.75f)
    }

    @Test
    fun `an archive written before v5_12 reads its hours into both groups`() = runBlocking {
        val archived = defaultCustomizationFor("christmas").toJson().apply {
            for (key in listOf("shop", "tower")) {
                remove("${key}HoursEnabled"); remove("${key}OpenHour"); remove("${key}CloseHour")
            }
            put("businessHoursEnabled", true)
            put("businessOpenHour", 10.0)
            put("businessCloseHour", 23.0)
        }
        store.edit { it[stringPreferencesKey("theme_customization_christmas")] = archived.toString() }
        assertBothGroups(customization("christmas"), true, 10f, 23f)
    }

    @Test
    fun `the first edit since gives each group keys of its own, and from then on they are two`() = runBlocking {
        plantLiveEditBy511("autumn")
        prefs.setMoonVisible(false, "autumn") // any edit of the theme
        assertBothGroups(customization("autumn"), true, 8.25f, 1.75f)
        val raw = store.data.first()
        assertNull("the one setting of before is gone once carried", raw[legacyEnabled])
        assertNull(raw[legacyOpen])
        assertNull(raw[legacyClose])
        // Moving one group's hours leaves the other's where they were.
        prefs.setTowerOpenHour(10f, "autumn")
        prefs.setShopHoursEnabled(false, "autumn")
        val c = customization("autumn")
        assertEquals(10f, c.towerOpenHour, 0f)
        assertTrue(c.towerHoursEnabled)
        assertEquals(8.25f, c.shopOpenHour, 0f)
        assertFalse(c.shopHoursEnabled)
    }

    @Test
    fun `an edit to one group straight away keeps the other group's hours of before`() = runBlocking {
        plantLiveEditBy511("autumn")
        prefs.setShopCloseHour(20f, "autumn")
        val c = customization("autumn")
        assertEquals("the edit", 20f, c.shopCloseHour, 0f)
        assertTrue("the shops still on", c.shopHoursEnabled)
        assertEquals("the towers keep what they had", 1.75f, c.towerCloseHour, 0f)
        assertTrue(c.towerHoursEnabled)
    }

    @Test
    fun `each reset puts back its own group's hours and leaves the other's`() = runBlocking {
        plantLiveEditBy511("autumn")
        prefs.resetCategory(ObjectCategory.SHOPS, "autumn")
        var c = customization("autumn")
        assertFalse("Reset Shops: the shops' hours off", c.shopHoursEnabled)
        assertEquals(DEFAULT_BUSINESS_OPEN_HOUR, c.shopOpenHour, 0f)
        assertEquals(DEFAULT_BUSINESS_CLOSE_HOUR, c.shopCloseHour, 0f)
        assertTrue("and the towers' kept", c.towerHoursEnabled)
        assertEquals(8.25f, c.towerOpenHour, 0f)
        prefs.resetCategory(ObjectCategory.BUILDINGS, "autumn")
        c = customization("autumn")
        assertFalse("Reset Buildings: the towers' hours off", c.towerHoursEnabled)
        assertEquals(DEFAULT_BUSINESS_OPEN_HOUR, c.towerOpenHour, 0f)
        assertEquals(DEFAULT_BUSINESS_CLOSE_HOUR, c.towerCloseHour, 0f)
    }

    @Test
    fun `Reset Buildings first leaves the shops at the hours of before`() = runBlocking {
        plantLiveEditBy511("winter")
        prefs.resetCategory(ObjectCategory.BUILDINGS, "winter")
        val c = customization("winter")
        assertFalse(c.towerHoursEnabled)
        assertTrue(c.shopHoursEnabled)
        assertEquals(8.25f, c.shopOpenHour, 0f)
        assertEquals(1.75f, c.shopCloseHour, 0f)
    }

    @Test
    fun `leaving a theme edited by 5_11 archives both groups, and coming back restores them`() = runBlocking {
        plantLiveEditBy511("beach")
        prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.3f, "winter") // leaves Beach: archived
        assertBothGroups(customization("beach"), true, 8.25f, 1.75f)
        prefs.setTowerHoursEnabled(false, "beach") // back: restored, then one group changed
        val c = customization("beach")
        assertFalse(c.towerHoursEnabled)
        assertTrue(c.shopHoursEnabled)
        assertEquals(1.75f, c.shopCloseHour, 0f)
        assertEquals(1.75f, c.towerCloseHour, 0f)
    }

    @Test
    fun `the whole theme's reset takes both groups and the setting of before`() = runBlocking {
        plantLiveEditBy511("autumn")
        prefs.resetAllCategories("autumn")
        assertBothGroups(customization("autumn"), false, DEFAULT_BUSINESS_OPEN_HOUR, DEFAULT_BUSINESS_CLOSE_HOUR)
        assertNull(store.data.first()[legacyEnabled])
    }

    @Test
    fun `a store that never had the hours reads both groups off, as every theme ships`() = runBlocking {
        prefs.setMoonVisible(false, "spring")
        assertBothGroups(customization("spring"), false, DEFAULT_BUSINESS_OPEN_HOUR, DEFAULT_BUSINESS_CLOSE_HOUR)
    }
}
