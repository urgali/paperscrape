package com.paperscrape.livewallpaper.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.CustomThemeRegistry
import com.paperscrape.livewallpaper.engine.DecorationExclusion
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.engine.customThemeDataFromJsonString
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **Who updates from the 5.15** (v5.16): the files a 5.15 user has, written by the 5.15's own code -- a backup, the
 * saved-theme store, a shared theme (`resources/update/`, written on 2026-10-21 by a probe run in the published 5.15's
 * tree, `ScrittiDalla515Probe`, kept outside the repository) -- and a preference store as the 5.15 left it, read by this
 * release.
 *
 * What the 5.16 changes for them: *Christmas lights* became three switches -- the buildings', the trees', the firs' --
 * and **whoever had it on finds the three on** (the two new ones read its stored value where they have none); the five
 * Halloween switches v5.16 added, and the scarecrows, are **on in the Halloween theme and off in every other**, as a fresh install has them
 * (the maintainer, 2026-10-21: *«il resto, si a tutte»*, with the night by day *«il nuovo default del tema halloween»*) --
 * except the Halloween decorations of a theme the 5.15 lit for Christmas, which stay off: the Christmas ones were on
 * first, and the two are never on together (`DecorationExclusion`).
 */
class UpdateFromV515Test {

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader!!.getResource("update/$name")) { "update/$name" }.readText()

    private fun allChristmas(c: SceneCustomization) =
        c.christmasBuildingLightsEnabled && c.christmasTreeLightsEnabled && c.christmasFirsEnabled

    private fun newHalloween(c: SceneCustomization) = listOf(
        c.laughingMoonEnabled, c.nightSkyByDayEnabled, c.halloweenDecorationsEnabled, c.bloodRainEnabled,
        c.headlessHorsemanEnabled, c.scarecrows.visible,
    )

    @Test
    fun `a backup the 5_15 wrote comes back with its Christmas lights as three switches and Halloween's new ones as the themes ship them`() {
        val parsed = parseAppBackup(resource("backup-written-by-v5.15.json"))
        assertTrue("$parsed", parsed is BackupParseResult.Ok)
        val backup = (parsed as BackupParseResult.Ok).backup
        assertEquals("autumn", backup.settings.themeId)
        assertTrue(backup.settings.automaticUpdateCheckEnabled)
        assertTrue(backup.settings.updateNotificationsEnabled)
        val autumn = backup.themeCustomizations.getValue("autumn")
        assertTrue("Autumn lit for Christmas in 5.15: the three on", allChristmas(autumn))
        assertTrue(autumn.halloweenEnabled)
        assertEquals("nothing new of Halloween's in Autumn", List(6) { false }, newHalloween(autumn))
        val christmas = backup.themeCustomizations.getValue("christmas")
        assertTrue(allChristmas(christmas))
        assertEquals("its snowmen as the 5.15 kept them", 0.8f, christmas.snowmen.density, 0f)
        assertEquals(List(6) { false }, newHalloween(christmas))
        val halloween = backup.themeCustomizations.getValue("halloween")
        assertFalse(DecorationExclusion.christmasOn(halloween))
        assertEquals("an edited Halloween gets its theme's new switches on", List(6) { true }, newHalloween(halloween))
        assertEquals("the scarecrows at the theme's 50 %", 0.5f, halloween.scarecrows.density, 0f)
        assertEquals("its pumpkins as the 5.15 kept them", 0.7f, halloween.pumpkins.density, 0f)
        // A theme of the user's own from Halloween, lit for Christmas in 5.15: the lights stay, and since it is not the
        // Halloween theme none of the new Halloween switches is on -- the decorations, which could not be with the lights.
        val mine = backup.customThemeData.customThemes.single { it.id == "custom:1" }.customization
        assertTrue(allChristmas(mine))
        assertTrue(mine.halloweenEnabled)
        assertEquals(List(6) { false }, newHalloween(mine))
        val myChristmas = backup.customThemeData.customThemes.single { it.id == "custom:2" }.customization
        assertTrue(allChristmas(myChristmas))
    }

    @Test
    fun `the store and the shared theme the 5_15 wrote read back with the lights on`() {
        val store = customThemeDataFromJsonString(resource("store-saved-by-v5.15.json"))
        assertTrue(allChristmas(store.overrides.getValue("autumn").customization))
        assertTrue(allChristmas(store.customThemes.single { it.id == "custom:2" }.customization))
        val shared = parseThemeShare(resource("theme-shared-by-v5.15.json"))
        assertTrue("$shared", shared is ThemeParseResult.Ok)
        val c = (shared as ThemeParseResult.Ok).share.customization
        assertTrue(allChristmas(c))
        assertEquals(List(6) { false }, newHalloween(c))
    }

    @Test
    fun `a backup written now carries every new switch, and reads back as written`() {
        val backup = (parseAppBackup(resource("backup-written-by-v5.15.json")) as BackupParseResult.Ok).backup
        val again = (parseAppBackup(backup.toJsonString()) as BackupParseResult.Ok).backup
        for (id in listOf("autumn", "christmas", "halloween")) {
            assertEquals(id, backup.themeCustomizations.getValue(id), again.themeCustomizations.getValue(id))
        }
        val json = org.json.JSONObject(backup.toJsonString()).getJSONObject("themeCustomizations").getJSONObject("halloween")
        for (key in listOf(
            "christmasDecorationsEnabled", "christmasTreeLightsEnabled", "christmasFirsEnabled", "laughingMoonEnabled",
            "nightSkyByDayEnabled", "halloweenDecorationsEnabled", "bloodRainEnabled", "headlessHorsemanEnabled", "scarecrows",
        )) {
            assertTrue("$key written", json.has(key))
        }
    }

    /**
     * **The preference store as the 5.15 left it**: the live edit in the flat keys -- *Christmas lights* stored on for
     * Autumn, no key of the 5.16's -- and the Halloween theme's archived edit. Read by this release's own reader.
     */
    @Test
    fun `a preference store as the 5_15 left it reads the three Christmas switches from its one key`() = runBlocking {
        val dir = kotlin.io.path.createTempDirectory("from515").toFile()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            CustomThemeRegistry.update(CustomThemeData.EMPTY)
            val store = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "prefs.preferences_pb") }
            store.edit {
                it[stringPreferencesKey("theme_id")] = "autumn"
                it[stringPreferencesKey("pending_customization_theme_id")] = "autumn"
                it[booleanPreferencesKey("christmas_decorations_enabled")] = true
                it[booleanPreferencesKey("halloween_enabled")] = true
                // Halloween's archived edit as the 5.15 wrote it: its own JSON, with the lights on.
                it[stringPreferencesKey(THEME_CUSTOMIZATION_KEY_PREFIX + "halloween")] =
                    org.json.JSONObject(resource("backup-written-by-v5.15.json"))
                        .getJSONObject("themeCustomizations").getJSONObject("halloween")
                        .put("christmasDecorationsEnabled", true).toString()
            }
            val s = WallpaperPrefs(store).settingsFlow.first()
            val autumn = s.pendingCustomization
            assertEquals("autumn", s.pendingCustomizationThemeId)
            assertTrue("the one key read by all three", allChristmas(autumn))
            assertEquals(List(6) { false }, newHalloween(autumn))
            val halloween = s.themeCustomizations.getValue("halloween")
            assertTrue(allChristmas(halloween))
            assertFalse("lit for Christmas first: no Halloween decorations", halloween.halloweenDecorationsEnabled)
            // The rest of Halloween's new switches as the theme ships them: all on but the decorations.
            assertEquals("the rest as the theme ships them", List(6) { it != 2 }, newHalloween(halloween))
            // And a fresh install's Halloween, which no 5.15 store holds: everything new on.
            assertEquals(List(6) { true }, newHalloween(defaultCustomizationFor("halloween")))
            assertTrue(ThemeCatalog.ALL.filter { it.id != "halloween" }.all { newHalloween(defaultCustomizationFor(it.id)).none { on -> on } })
        } finally {
            scope.cancel()
            dir.deleteRecursively()
        }
    }
}
