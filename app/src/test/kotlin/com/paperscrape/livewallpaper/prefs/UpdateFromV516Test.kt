package com.paperscrape.livewallpaper.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.CustomThemeRegistry
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
 * **Who updates from the 5.16** (v5.17): the files a 5.16 user has, written by the 5.16's own code -- a backup, the
 * saved-theme store, a shared theme (`resources/update/`, written on 2026-10-25 by a probe run in the published 5.16's tree,
 * `ScrittiDalla516Probe`, kept outside the repository) -- and a preference store as the 5.16 left it, read by this release.
 *
 * What the 5.17 changes for them: the switch *Moon stays in place* (`stillMoonEnabled`), which none of them holds, reads
 * as its theme ships it -- **on in the Halloween theme, the edited Halloween and its saved version included, off in every
 * other** -- and a copy of the Halloween theme saved under a name of its own is a theme of its own, which starts with it
 * off, as v5.16's switches did (inventory I-1057). The maintainer, to the v5.17A table's row 14: *«Sì a tutte»*
 * (2026-10-24). Nothing else they hold moves: the 5.16's switches these files set away from their themes' own -- the night by
 * day turned off and the pumpkins at 70 % in Halloween, Halloween and the laughing moon on in Autumn, the seven of a copy
 * saved under its own name, the two update switches -- read back as they were written.
 */
class UpdateFromV516Test {

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader!!.getResource("update/$name")) { "update/$name" }.readText()

    /** The 5.16's Halloween switches, as the 5.16 wrote them. */
    private fun halloween516(c: SceneCustomization) = listOf(
        c.halloweenEnabled, c.laughingMoonEnabled, c.nightSkyByDayEnabled, c.halloweenDecorationsEnabled, c.bloodRainEnabled,
        c.headlessHorsemanEnabled, c.scarecrows.visible,
    )

    @Test
    fun `none of the files the 5_16 wrote holds the still moon`() {
        for (name in listOf("backup-written-by-v5.16.json", "store-saved-by-v5.16.json", "theme-shared-by-v5.16.json")) {
            assertFalse("$name: written before the switch", resource(name).contains("stillMoon"))
            assertTrue("$name: written with the 5.16's switches", resource(name).contains("nightSkyByDayEnabled"))
        }
    }

    @Test
    fun `a backup the 5_16 wrote comes back with the still moon on in Halloween alone`() {
        val parsed = parseAppBackup(resource("backup-written-by-v5.16.json"))
        assertTrue("$parsed", parsed is BackupParseResult.Ok)
        val backup = (parsed as BackupParseResult.Ok).backup
        assertEquals("autumn", backup.settings.themeId)
        assertTrue(backup.settings.automaticUpdateCheckEnabled)
        assertTrue(backup.settings.updateNotificationsEnabled)
        val halloween = backup.themeCustomizations.getValue("halloween")
        assertTrue("the edited Halloween: the still moon as the theme ships it", halloween.stillMoonEnabled)
        assertFalse("its night by day as it was turned off", halloween.nightSkyByDayEnabled)
        assertEquals("its pumpkins as the 5.16 kept them", 0.7f, halloween.pumpkins.density, 0f)
        val autumn = backup.themeCustomizations.getValue("autumn")
        assertFalse("Autumn with Halloween on: no still moon", autumn.stillMoonEnabled)
        assertTrue("its Halloween and laughing moon as written", autumn.halloweenEnabled && autumn.laughingMoonEnabled)
        val data = backup.customThemeData
        assertTrue("the saved version of Halloween", data.overrides.getValue("halloween").customization.stillMoonEnabled)
        val mine = data.customThemes.single { it.id == "custom:1" }.customization
        assertEquals("a copy of Halloween saved under its own name keeps every 5.16 switch", List(7) { true }, halloween516(mine))
        assertFalse("and finds the still moon off: a theme of its own (I-1057)", mine.stillMoonEnabled)
        assertFalse(data.customThemes.single { it.id == "custom:2" }.customization.stillMoonEnabled)
    }

    @Test
    fun `the store and the shared theme the 5_16 wrote read the same way`() {
        val store = customThemeDataFromJsonString(resource("store-saved-by-v5.16.json"))
        assertTrue("the saved version of Halloween", store.overrides.getValue("halloween").customization.stillMoonEnabled)
        assertFalse("the copy under a name of its own", store.customThemes.single { it.id == "custom:1" }.customization.stillMoonEnabled)
        assertFalse(store.customThemes.single { it.id == "custom:2" }.customization.stillMoonEnabled)
        val shared = parseThemeShare(resource("theme-shared-by-v5.16.json"))
        assertTrue("$shared", shared is ThemeParseResult.Ok)
        val c = (shared as ThemeParseResult.Ok).share.customization
        assertTrue("a Halloween shared by the 5.16 reads as Halloween ships it", c.stillMoonEnabled)
        assertFalse(c.nightSkyByDayEnabled)
    }

    @Test
    fun `a backup written now carries the still moon, and reads back as written`() {
        val backup = (parseAppBackup(resource("backup-written-by-v5.16.json")) as BackupParseResult.Ok).backup
        val again = (parseAppBackup(backup.toJsonString()) as BackupParseResult.Ok).backup
        for (id in listOf("autumn", "halloween")) assertEquals(id, backup.themeCustomizations.getValue(id), again.themeCustomizations.getValue(id))
        val json = org.json.JSONObject(backup.toJsonString()).getJSONObject("themeCustomizations")
        assertTrue(json.getJSONObject("halloween").getBoolean("stillMoonEnabled"))
        assertFalse(json.getJSONObject("autumn").getBoolean("stillMoonEnabled"))
        // Turned off by hand, it stays off through a backup.
        val off = backup.themeCustomizations.getValue("halloween").copy(stillMoonEnabled = false)
        val written = backup.copy(themeCustomizations = backup.themeCustomizations + ("halloween" to off))
        val read = (parseAppBackup(written.toJsonString()) as BackupParseResult.Ok).backup
        assertFalse(read.themeCustomizations.getValue("halloween").stillMoonEnabled)
    }

    /**
     * **The preference store as the 5.16 left it**: Autumn chosen and edited live (the maintainer's way -- a pending edit in
     * the flat keys with *Halloween* on), the Halloween theme's archived edit with the 5.16's keys and no `still_moon_enabled`.
     * Read by this release's own reader: Halloween's still moon on, Autumn's off; and a store where the user had already
     * turned it off (a store of this release) keeps it off.
     */
    @Test
    fun `a preference store as the 5_16 left it reads the still moon as each theme ships it`() = runBlocking {
        val dir = kotlin.io.path.createTempDirectory("from516").toFile()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            CustomThemeRegistry.update(CustomThemeData.EMPTY)
            val store = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "prefs.preferences_pb") }
            val archived = org.json.JSONObject(resource("backup-written-by-v5.16.json")).getJSONObject("themeCustomizations")
                .getJSONObject("halloween").toString()
            store.edit {
                it[stringPreferencesKey("theme_id")] = "autumn"
                it[stringPreferencesKey("pending_customization_theme_id")] = "autumn"
                it[booleanPreferencesKey("halloween_enabled")] = true
                it[booleanPreferencesKey("laughing_moon_enabled")] = true
                it[stringPreferencesKey(THEME_CUSTOMIZATION_KEY_PREFIX + "halloween")] = archived
            }
            val s = WallpaperPrefs(store).settingsFlow.first()
            assertEquals("autumn", s.pendingCustomizationThemeId)
            assertFalse("Autumn's live edit: off", s.pendingCustomization.stillMoonEnabled)
            assertTrue(s.pendingCustomization.halloweenEnabled && s.pendingCustomization.laughingMoonEnabled)
            val halloween = s.themeCustomizations.getValue("halloween")
            assertTrue("Halloween's archived edit: on, as the theme ships it", halloween.stillMoonEnabled)
            assertFalse(halloween.nightSkyByDayEnabled)
            // The live edit of Halloween itself, its flat keys without the new one: the theme's own value.
            store.edit {
                it[stringPreferencesKey("pending_customization_theme_id")] = "halloween"
                it[booleanPreferencesKey("night_sky_by_day_enabled")] = false
            }
            val live = WallpaperPrefs(store).settingsFlow.first()
            assertEquals("halloween", live.pendingCustomizationThemeId)
            assertTrue("Halloween's live edit: on", live.pendingCustomization.stillMoonEnabled)
            // Turned off in this release, it stays off.
            WallpaperPrefs(store).setStillMoonEnabled(false, "halloween")
            assertFalse(WallpaperPrefs(store).settingsFlow.first().pendingCustomization.stillMoonEnabled)
            // A fresh install: on in Halloween alone.
            assertTrue(ThemeCatalog.ALL.all { defaultCustomizationFor(it.id).stillMoonEnabled == (it.id == "halloween") })
        } finally {
            scope.cancel()
            dir.deleteRecursively()
        }
    }
}
