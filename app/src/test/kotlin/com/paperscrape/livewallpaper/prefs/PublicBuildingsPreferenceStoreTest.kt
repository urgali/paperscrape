package com.paperscrape.livewallpaper.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.paperscrape.livewallpaper.engine.DEFAULT_BUSINESS_CLOSE_HOUR
import com.paperscrape.livewallpaper.engine.DEFAULT_BUSINESS_OPEN_HOUR
import com.paperscrape.livewallpaper.engine.ObjectVariantConfig
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import com.paperscrape.livewallpaper.engine.toJson
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **A preference store written by the 5.12, read by v5.13**: the school wore the shops' colours and kept
 * the shops' hours, and since v5.13 the public buildings -- the school, the fire station and the police
 * station -- have their own. The maintainer's *«il resto accetto i consigliati»* of 2026-10-11, to the
 * rows that said: **whoever had changed the shops' colours by hand finds them on the school and the two
 * stations too**, whoever had not gets the theme's new pair; and **whoever had the shops' hours on finds
 * them on in the shops and in the public buildings, at the same hours** -- in the live edit, in every
 * theme's archive, and from the first edit on in keys of their own. The keys are planted by their names
 * on disk, the contract with an install that updates, as `ShopsPreferenceStoreTest` and
 * `OpeningHoursPreferenceStoreTest` do. Saved themes, theme files and backups are JSON:
 * `PublicBuildingsMigrationTest` holds those.
 */
class PublicBuildingsPreferenceStoreTest {

    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var prefs: WallpaperPrefs

    @Before
    fun open() {
        dir = kotlin.io.path.createTempDirectory("publics-store").toFile()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "prefs.preferences_pb") }
        prefs = WallpaperPrefs(store)
    }

    @After
    fun close() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private val mine = intArrayOf(0xFF8A2BE2.toInt(), 0xFF2E1048.toInt(), 0xFF20B2AA.toInt(), 0xFF0B3C39.toInt())

    /** The live edit of [theme] as the 5.12 left it: written by a build that knew the shops, its hours [hours]. */
    private suspend fun plantLiveEditBy512(theme: String, shopColours: IntArray?, hours: Triple<Boolean, Float, Float>?) = store.edit {
        it[stringPreferencesKey("pending_customization_theme_id")] = theme
        it[booleanPreferencesKey("scratch_knows_shops")] = true
        if (shopColours != null) {
            it[intPreferencesKey("obj_SHOPS_color_day_1")] = shopColours[0]
            it[intPreferencesKey("obj_SHOPS_color_night_1")] = shopColours[1]
            it[intPreferencesKey("obj_SHOPS_color_day_2")] = shopColours[2]
            it[intPreferencesKey("obj_SHOPS_color_night_2")] = shopColours[3]
        }
        if (hours != null) {
            it[booleanPreferencesKey("shop_hours_enabled")] = hours.first
            it[floatPreferencesKey("shop_open_hour")] = hours.second
            it[floatPreferencesKey("shop_close_hour")] = hours.third
        }
    }

    private suspend fun customization(theme: String): SceneCustomization =
        prefs.settingsFlow.first().themeCustomizations[theme] ?: defaultCustomizationFor(theme)

    private fun colours(c: ObjectVariantConfig) = intArrayOf(c.colorDay1, c.colorNight1, c.colorDay2, c.colorNight2).toList()

    @Test
    fun `shops' colours changed by hand are found on the public buildings, with the shops' hours`() = runBlocking {
        plantLiveEditBy512("autumn", mine, Triple(true, 10.5f, 18.75f))
        val c = customization("autumn")
        assertEquals("the shops keep the colours chosen", mine.toList(), colours(c.shops))
        assertEquals("and the public buildings find them", mine.toList(), colours(c.publics))
        assertTrue("the shops' hours on: the public buildings' are", c.publicHoursEnabled)
        assertEquals(10.5f, c.publicOpenHour, 0f)
        assertEquals(18.75f, c.publicCloseHour, 0f)
        assertTrue(c.shopHoursEnabled)
    }

    @Test
    fun `shops' colours never changed - the public buildings start from the theme's new pair`() = runBlocking {
        plantLiveEditBy512("autumn", null, Triple(false, 9f, 20f))
        val c = customization("autumn")
        assertEquals(colours(defaultCustomizationFor("autumn").publics), colours(c.publics))
        assertNotEquals("the new pair is not the shops'", colours(c.shops), colours(c.publics))
        assertFalse(c.publicHoursEnabled)
        // A pair set by hand back to the theme's own reads as never changed: the same colours the theme starts from.
        plantLiveEditBy512("winter", colours(defaultCustomizationFor("winter").shops).toIntArray(), null)
        assertEquals(colours(defaultCustomizationFor("winter").publics), colours(customization("winter").publics))
    }

    @Test
    fun `a theme's archive written by the 5_12 is read the same way, against that theme`() = runBlocking {
        val changed = defaultCustomizationFor("christmas").let {
            it.copy(
                shops = it.shops.copy(colorDay1 = mine[0], colorNight1 = mine[1], colorDay2 = mine[2], colorNight2 = mine[3]),
                shopHoursEnabled = true, shopOpenHour = 7f, shopCloseHour = 13.5f,
            )
        }.toJson().apply {
            remove("publics"); remove("publicHoursEnabled"); remove("publicOpenHour"); remove("publicCloseHour")
        }
        val untouched = defaultCustomizationFor("beach").toJson().apply {
            remove("publics"); remove("publicHoursEnabled"); remove("publicOpenHour"); remove("publicCloseHour")
        }
        store.edit {
            it[stringPreferencesKey("theme_customization_christmas")] = changed.toString()
            it[stringPreferencesKey("theme_customization_beach")] = untouched.toString()
        }
        val christmas = customization("christmas")
        assertEquals(mine.toList(), colours(christmas.publics))
        assertTrue(christmas.publicHoursEnabled)
        assertEquals(7f, christmas.publicOpenHour, 0f)
        assertEquals(13.5f, christmas.publicCloseHour, 0f)
        assertEquals(colours(defaultCustomizationFor("beach").publics), colours(customization("beach").publics))
    }

    @Test
    fun `the first edit since gives the public buildings keys of their own, and from then on they are apart`() = runBlocking {
        plantLiveEditBy512("autumn", mine, Triple(true, 10.5f, 18.75f))
        prefs.setMoonVisible(false, "autumn") // any edit of the theme
        val raw = store.data.first()
        assertEquals("the carried colour is a key of the public buildings' own", mine[0], raw[intPreferencesKey("obj_PUBLICS_color_day_1")])
        assertEquals(true, raw[booleanPreferencesKey("public_hours_enabled")])
        assertEquals(true, raw[booleanPreferencesKey("scratch_knows_publics")])
        // Changing the shops' colours and hours now leaves the public buildings where they are.
        prefs.setCategoryColorDay1(ObjectCategory.SHOPS, 0xFF101010.toInt(), "autumn")
        prefs.setShopHoursEnabled(false, "autumn")
        var c = customization("autumn")
        assertEquals(mine[0], c.publics.colorDay1)
        assertTrue(c.publicHoursEnabled)
        // And each reset puts back its own page alone.
        prefs.resetCategory(ObjectCategory.SHOPS, "autumn")
        c = customization("autumn")
        assertEquals("Reset Shops leaves the public buildings' colours", mine[0], c.publics.colorDay1)
        assertTrue("and their hours", c.publicHoursEnabled)
        prefs.resetCategory(ObjectCategory.PUBLICS, "autumn")
        c = customization("autumn")
        assertEquals("Reset Public buildings: the theme's own pair", colours(defaultCustomizationFor("autumn").publics), colours(c.publics))
        assertFalse("and its hours off", c.publicHoursEnabled)
        assertEquals(DEFAULT_BUSINESS_OPEN_HOUR, c.publicOpenHour, 0f)
        assertEquals(DEFAULT_BUSINESS_CLOSE_HOUR, c.publicCloseHour, 0f)
    }

    @Test
    fun `an edit to the shops straight away keeps what the public buildings had`() = runBlocking {
        plantLiveEditBy512("autumn", mine, Triple(true, 10.5f, 18.75f))
        prefs.setShopCloseHour(22f, "autumn")
        prefs.setCategoryColorNight2(ObjectCategory.SHOPS, 0xFF000001.toInt(), "autumn")
        val c = customization("autumn")
        assertEquals("the edit", 22f, c.shopCloseHour, 0f)
        assertEquals("the public buildings keep the hours of before", 18.75f, c.publicCloseHour, 0f)
        assertEquals("and the colours of before", mine[3], c.publics.colorNight2)
    }

    /**
     * **A theme of the user's own is read again once the saved themes arrive** (v5.13, the read-only review
     * of v5.13B): its archive is read against that theme, which only [CustomThemeRegistry] knows, and the
     * registry is filled by a collector of its own in no fixed order with the preferences. Read before it,
     * a 5.12 archive whose shops were never changed came out with the school and the stations in the shops'
     * colours -- and stayed so until some other preference was written.
     */
    @Test
    fun `a theme of the user's own read before the saved themes arrive is read again when they do`() = runBlocking {
        val id = "custom:copy-of-autumn"
        val autumn = com.paperscrape.livewallpaper.engine.ThemeCatalog.byId("autumn")
        val untouched = defaultCustomizationFor("autumn").toJson().apply {
            remove("publics"); remove("publicHoursEnabled"); remove("publicOpenHour"); remove("publicCloseHour")
        }
        com.paperscrape.livewallpaper.engine.CustomThemeRegistry.update(com.paperscrape.livewallpaper.engine.CustomThemeData.EMPTY)
        try {
            store.edit { it[stringPreferencesKey("theme_customization_$id")] = untouched.toString() }
            // One collector that lives across the arrival, as the wallpaper's and the settings screen's do: a
            // fresh `first()` would read the store again and see the registry as it is by then.
            val latest = kotlinx.coroutines.flow.MutableStateFlow<WallpaperSettings?>(null)
            val collector = scope.launch { prefs.settingsFlow.collect { latest.value = it } }
            val before = kotlinx.coroutines.withTimeout(5_000) { latest.first { it != null }!! }.themeCustomizations.getValue(id)
            assertEquals("not known yet: the shops' colours, the look the school had", colours(before.shops), colours(before.publics))
            com.paperscrape.livewallpaper.engine.CustomThemeRegistry.update(
                com.paperscrape.livewallpaper.engine.CustomThemeData(
                    customThemes = listOf(
                        com.paperscrape.livewallpaper.engine.CustomThemeEntry(
                            id, "Copy", autumn.copy(id = id, displayName = "Copy"),
                            com.paperscrape.livewallpaper.engine.SceneObjectCatalog.layoutFor("autumn", autumn.accentColor),
                            defaultCustomizationFor("autumn"),
                        ),
                    ),
                ),
            )
            val wanted = colours(defaultCustomizationFor("autumn").publics)
            val after = kotlinx.coroutines.withTimeout(5_000) {
                latest.first { it != null && colours(it.themeCustomizations.getValue(id).publics) == wanted }!!
            }
            collector.cancel()
            assertEquals("known: shops never changed, so the theme's new pair", wanted, colours(after.themeCustomizations.getValue(id).publics))
        } finally {
            com.paperscrape.livewallpaper.engine.CustomThemeRegistry.update(com.paperscrape.livewallpaper.engine.CustomThemeData.EMPTY)
        }
    }

    @Test
    fun `a store written before v5_12 opens the public buildings at its one setting's hours`() = runBlocking {
        store.edit {
            it[stringPreferencesKey("pending_customization_theme_id")] = "autumn"
            it[booleanPreferencesKey("business_hours_enabled")] = true
            it[floatPreferencesKey("business_open_hour")] = 8.25f
            it[floatPreferencesKey("business_close_hour")] = 1.75f
        }
        var c = customization("autumn")
        assertTrue(c.publicHoursEnabled && c.shopHoursEnabled && c.towerHoursEnabled)
        assertEquals(8.25f, c.publicOpenHour, 0f)
        assertEquals(1.75f, c.publicCloseHour, 0f)
        // And still after the first edit, which writes every group's keys.
        prefs.setMoonVisible(false, "autumn")
        c = customization("autumn")
        assertTrue(c.publicHoursEnabled)
        assertEquals(1.75f, c.publicCloseHour, 0f)
    }

    @Test
    fun `a store written before v5_11 with the Buildings colours chosen finds them on the shops and the public buildings`() = runBlocking {
        store.edit {
            it[stringPreferencesKey("pending_customization_theme_id")] = "spring"
            it[intPreferencesKey("obj_BUILDINGS_color_day_1")] = mine[0]
        }
        val c = customization("spring")
        assertEquals("the shops were drawn in the Buildings colours", mine[0], c.shops.colorDay1)
        assertEquals("and so was the school", mine[0], c.publics.colorDay1)
        assertEquals(c.shops.colorNight2, c.publics.colorNight2)
    }
}
