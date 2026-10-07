package com.paperscrape.livewallpaper.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.paperscrape.livewallpaper.engine.AutoColorMode
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
import org.junit.Before
import org.junit.Test

/**
 * **A preference store written before v5.11, read by v5.11** (the shops' colours of their own and the
 * distant houses), with the app's own reader over a scratch file, as `PalmsPreferenceStoreTest` does
 * for the palms. The keys are planted by their names on disk -- the contract with an install that
 * updates.
 *
 * The shops were drawn in the Buildings colours until v5.11. So a theme's archive is read with the
 * Buildings colours stored in it (the archive is a theme's whole look, `ShopColoursTest` has the JSON
 * side); the live edit keeps only what the user touched, and where it holds a Buildings colour the
 * shops and the towers are read as the pair the old build drew them in -- that colour, the old slate
 * for the rest; where it holds none, both start from the theme's own new colours. The first edit
 * since writes that reading into the keys, so "Reset Shops to default" and "Reset Buildings to
 * default" afterwards mean the theme's own colours.
 */
class ShopsPreferenceStoreTest {

    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var prefs: WallpaperPrefs

    @Before
    fun open() {
        dir = kotlin.io.path.createTempDirectory("shops-store").toFile()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "prefs.preferences_pb") }
        prefs = WallpaperPrefs(store)
    }

    @After
    fun close() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private val mine = 0xFF0F1E2D.toInt()
    private val slate = SceneCustomization.DEFAULT.buildings

    /** A 5.10 archive: today's JSON without the fields that build did not write. */
    private fun archivedBy510(c: SceneCustomization): String =
        c.toJson().apply { remove("shops"); remove("distantHouses") }.toString()

    @Test
    fun `a live edit that changed a Buildings colour keeps its shops in the colours they were drawn in`() = runBlocking {
        store.edit {
            it[stringPreferencesKey("pending_customization_theme_id")] = "autumn"
            it[intPreferencesKey("obj_BUILDINGS_color_day_1")] = mine
        }
        val autumn = prefs.settingsFlow.first().themeCustomizations.getValue("autumn")
        assertEquals("the colour the user chose", mine, autumn.shops.colorDay1)
        assertEquals("the rest of the pair the old build drew them in", slate.colorNight1, autumn.shops.colorNight1)
        assertEquals(slate.colorDay2, autumn.shops.colorDay2)
        assertEquals(slate.colorNight2, autumn.shops.colorNight2)
        // The towers too: what the user chose, and the slate they were drawn in for the rest -- not
        // the user's colour beside the theme's new starting ones.
        assertEquals(mine, autumn.buildings.colorDay1)
        assertEquals(slate.colorNight1, autumn.buildings.colorNight1)
        assertEquals(slate.colorDay2, autumn.buildings.colorDay2)
        assertEquals(slate.colorNight2, autumn.buildings.colorNight2)
    }

    @Test
    fun `a live edit that never touched a Buildings colour gives the shops the theme's new colours`() = runBlocking {
        store.edit {
            it[stringPreferencesKey("pending_customization_theme_id")] = "autumn"
            it[intPreferencesKey("obj_HOUSES_color_day_1")] = mine
        }
        val autumn = prefs.settingsFlow.first().themeCustomizations.getValue("autumn")
        assertEquals(defaultCustomizationFor("autumn").shops, autumn.shops)
    }

    @Test
    fun `an archive written before v5_11 keeps its shops in its Buildings colours`() = runBlocking {
        val edited = defaultCustomizationFor("christmas").copy(buildings = slate.copy(colorDay1 = mine, autoMode2 = AutoColorMode.FROM_NIGHT))
        store.edit { it[stringPreferencesKey("theme_customization_christmas")] = archivedBy510(edited) }
        val christmas = prefs.settingsFlow.first().themeCustomizations.getValue("christmas")
        assertEquals(mine, christmas.shops.colorDay1)
        assertEquals(slate.colorNight1, christmas.shops.colorNight1)
        assertEquals(AutoColorMode.FROM_NIGHT, christmas.shops.autoMode2)
        assertFalse("no distant houses in data that did not know them", christmas.distantHouses.visible)
    }

    @Test
    fun `the first edit since carries the shops over, and Reset Shops then means the theme's own`() = runBlocking {
        store.edit {
            it[stringPreferencesKey("pending_customization_theme_id")] = "autumn"
            it[intPreferencesKey("obj_BUILDINGS_color_day_1")] = mine
        }
        prefs.setMoonVisible(false, "autumn")
        var autumn = prefs.settingsFlow.first().themeCustomizations.getValue("autumn")
        assertEquals("still the look it had after an unrelated edit", mine, autumn.shops.colorDay1)
        assertEquals("the towers' too", slate.colorDay2, autumn.buildings.colorDay2)
        // Resetting the towers leaves the shops alone: they are two pairs now.
        prefs.resetCategory(ObjectCategory.BUILDINGS, "autumn")
        autumn = prefs.settingsFlow.first().themeCustomizations.getValue("autumn")
        assertEquals(mine, autumn.shops.colorDay1)
        assertEquals(defaultCustomizationFor("autumn").buildings.colorDay1, autumn.buildings.colorDay1)
        // And resetting the shops gives the theme's shop colours, not the Buildings ones.
        prefs.resetCategory(ObjectCategory.SHOPS, "autumn")
        autumn = prefs.settingsFlow.first().themeCustomizations.getValue("autumn")
        assertEquals(defaultCustomizationFor("autumn").shops, autumn.shops)
    }

    @Test
    fun `leaving a theme archives its shops and distant houses, and coming back restores them`() = runBlocking {
        prefs.setCategoryColorDay2(ObjectCategory.SHOPS, mine, "beach")
        prefs.setDistantHousesVisible(true, "beach")
        prefs.setDistantHousesDensity(0.8f, "beach")
        prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.3f, "winter") // leaves Beach: archived
        prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.4f, "beach") // back: restored
        val beach = prefs.settingsFlow.first().themeCustomizations.getValue("beach")
        assertEquals(mine, beach.shops.colorDay2)
        assertEquals(true, beach.distantHouses.visible)
        assertEquals(0.8f, beach.distantHouses.density, 0f)
    }

    @Test
    fun `a store that never knew the distant houses reads them off, at 50 percent`() = runBlocking {
        store.edit { it[stringPreferencesKey("pending_customization_theme_id")] = "tundra" }
        val tundra = prefs.settingsFlow.first().themeCustomizations.getValue("tundra")
        assertFalse(tundra.distantHouses.visible)
        assertEquals(0.5f, tundra.distantHouses.density, 0f)
    }

    @Test
    fun `resetting the theme's scene takes the distant houses and the shops`() = runBlocking {
        prefs.setDistantHousesVisible(true, "spring")
        prefs.setCategoryColorDay1(ObjectCategory.SHOPS, mine, "spring")
        prefs.resetAllCategories("spring")
        // And an edit afterwards starts from the theme's own: nothing of the two was left behind.
        prefs.setMoonVisible(true, "spring")
        val spring = prefs.settingsFlow.first().themeCustomizations.getValue("spring")
        assertFalse(spring.distantHouses.visible)
        assertEquals(defaultCustomizationFor("spring").shops, spring.shops)
    }
}
