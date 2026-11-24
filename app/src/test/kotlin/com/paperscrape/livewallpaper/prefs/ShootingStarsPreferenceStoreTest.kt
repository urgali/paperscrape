package com.paperscrape.livewallpaper.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.CustomThemeRegistry
import com.paperscrape.livewallpaper.engine.customThemeDataFromJsonString
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **The *Shooting stars* switch is kept wherever *Show Stars* is**: the flat key `shooting_stars_visible` of the theme under edit, the
 * archive of every other theme, a saved theme, a shared theme and a backup -- and **read on wherever it is absent**: a store,
 * a saved theme, a theme file or a backup written before the switch shows them, as every theme ships it.
 *
 * [WallpaperPrefs] is handed a store over a scratch file, planted by the keys' names on disk, the contract with an install
 * that updates. The files written before the switch are the ones under `resources/update/`.
 */
class ShootingStarsPreferenceStoreTest {

    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var prefs: WallpaperPrefs

    @Before
    fun open() {
        dir = kotlin.io.path.createTempDirectory("shooting-stars-store").toFile()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "prefs.preferences_pb") }
        prefs = WallpaperPrefs(store)
        CustomThemeRegistry.update(CustomThemeData.EMPTY)
    }

    @After
    fun close() {
        scope.cancel()
        dir.deleteRecursively()
        CustomThemeRegistry.update(CustomThemeData.EMPTY)
    }

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader!!.getResource("update/$name")) { "update/$name" }.readText()

    /** A store as a release without the switch left it: Autumn edited live, Christmas archived, no shooting stars key. */
    private suspend fun plantWithoutTheKey() = store.edit {
        it[stringPreferencesKey("theme_id")] = "autumn"
        it[stringPreferencesKey("pending_customization_theme_id")] = "autumn"
        it[booleanPreferencesKey("stars_visible")] = true
        it[floatPreferencesKey("stars_density")] = 0.6f
        val archived = defaultCustomizationFor("christmas").toJson().apply { getJSONObject("stars").remove("shooting") }
        it[stringPreferencesKey(THEME_CUSTOMIZATION_KEY_PREFIX + "christmas")] = archived.toString()
    }

    @Test
    fun `a store without the key reads shooting stars on, live edit and archive alike`() = runBlocking {
        plantWithoutTheKey()
        val read = prefs.settingsFlow.first()
        assertEquals("the planted live edit is the one read", 0.6f, read.pendingCustomization.stars.density, 0f)
        assertTrue("the live edit", read.pendingCustomization.stars.shooting)
        assertTrue(read.themeCustomizations.getValue("autumn").stars.shooting)
        assertTrue("the archived Christmas", read.themeCustomizations.getValue("christmas").stars.shooting)
    }

    @Test
    fun `turned off it is written under its key and read back off, through the archive and back`() = runBlocking {
        plantWithoutTheKey()
        prefs.setShootingStarsVisible(false, "autumn")
        assertEquals(false, store.data.first()[booleanPreferencesKey("shooting_stars_visible")])
        val read = prefs.settingsFlow.first()
        assertEquals("autumn", read.pendingCustomizationThemeId)
        assertFalse(read.pendingCustomization.stars.shooting)
        assertTrue("Show Stars untouched", read.pendingCustomization.stars.visible)
        assertEquals("the stars' density untouched", 0.6f, read.pendingCustomization.stars.density, 0f)
        // An edit on another theme archives Autumn; one on Autumn restores it.
        prefs.setStarsVisible(false, "winter")
        val archived = prefs.settingsFlow.first()
        assertFalse("archived", archived.themeCustomizations.getValue("autumn").stars.shooting)
        assertTrue("and Winter's, edited in between, as it ships", archived.themeCustomizations.getValue("winter").stars.shooting)
        assertFalse(archived.themeCustomizations.getValue("winter").stars.visible)
        prefs.setStarsDensity(0.4f, "autumn")
        assertFalse("restored", prefs.settingsFlow.first().pendingCustomization.stars.shooting)
        // Show Stars turned off and on again leaves the switch as it was.
        prefs.setStarsVisible(false, "autumn")
        prefs.setStarsVisible(true, "autumn")
        assertFalse(prefs.settingsFlow.first().pendingCustomization.stars.shooting)
        // And on again by hand.
        prefs.setShootingStarsVisible(true, "autumn")
        assertTrue(prefs.settingsFlow.first().pendingCustomization.stars.shooting)
    }

    @Test
    fun `resetting the theme's edits gives it back on`() = runBlocking {
        prefs.setShootingStarsVisible(false, "autumn")
        assertFalse(prefs.settingsFlow.first().themeCustomizations.getValue("autumn").stars.shooting)
        prefs.resetAllCategories("autumn")
        val autumn = prefs.settingsFlow.first().themeCustomizations["autumn"] ?: defaultCustomizationFor("autumn")
        assertTrue(autumn.stars.shooting)
    }

    @Test
    fun `files written before the switch read it on - a saved theme, a shared theme, a backup`() {
        for (name in listOf("store-saved-by-v5.16.json", "theme-shared-by-v5.16.json", "backup-written-by-v5.16.json")) {
            assertFalse("$name: written before the switch", resource(name).contains("shooting"))
        }
        val saved = customThemeDataFromJsonString(resource("store-saved-by-v5.16.json"))
        val savedLooks = saved.overrides.values.map { it.customization } + saved.customThemes.map { it.customization }
        assertTrue(savedLooks.isNotEmpty())
        assertTrue("a saved theme", savedLooks.all { it.stars.shooting })
        val shared = parseThemeShare(resource("theme-shared-by-v5.16.json")) as ThemeParseResult.Ok
        assertTrue("a shared theme", shared.share.customization.stars.shooting)
        val backup = (parseAppBackup(resource("backup-written-by-v5.16.json")) as BackupParseResult.Ok).backup
        assertTrue(backup.themeCustomizations.isNotEmpty())
        assertTrue("a backup", backup.themeCustomizations.values.all { it.stars.shooting })
    }

    @Test
    fun `turned off, it stays off through a shared theme and a backup`() {
        val off = defaultCustomizationFor("autumn").let { it.copy(stars = it.stars.copy(shooting = false)) }
        val share = ThemeShare.of("autumn", "Quiet sky", off, "test", 0L)
        val back = parseThemeShare(share.toJsonString()) as ThemeParseResult.Ok
        assertFalse("a shared theme", back.share.customization.stars.shooting)
        val backup = (parseAppBackup(resource("backup-written-by-v5.16.json")) as BackupParseResult.Ok).backup
        val written = backup.copy(themeCustomizations = backup.themeCustomizations + ("autumn" to off))
        val read = (parseAppBackup(written.toJsonString()) as BackupParseResult.Ok).backup
        assertFalse("a backup", read.themeCustomizations.getValue("autumn").stars.shooting)
    }
}
