package com.paperscrape.livewallpaper.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import com.paperscrape.livewallpaper.engine.palmsShown
import com.paperscrape.livewallpaper.engine.toJson
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **The fourth road an old `true` of the palms takes, the preference store** (v5.10C), read by the
 * app's own reader. The other three -- a saved theme, a theme file, a backup -- are
 * `PalmsOnEveryThemeTest`; why they matter is said there.
 *
 * [WallpaperPrefs] is handed a store over a scratch file (its internal constructor), and the store is
 * planted with what a 5.9 build kept: the keys by their names on disk -- the contract with an install
 * that updates -- and the archive of a theme edited before as 5.9 wrote it, `palmsEnabled: true` and
 * no `palmsInsteadOfTrees`. Two shapes, because the live edit and the others are kept apart: the theme
 * under edit in the flat keys (`palms_enabled` written `true` when its archive was restored, as every
 * restore since v5.8 writes every field), every other theme in its own JSON archive.
 */
class PalmsPreferenceStoreTest {

    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var prefs: WallpaperPrefs

    @Before
    fun open() {
        dir = createTempDirectory()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "prefs.preferences_pb") }
        prefs = WallpaperPrefs(store)
    }

    @After
    fun close() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun createTempDirectory(): File = kotlin.io.path.createTempDirectory("palms-store").toFile()

    /** A 5.9 archive: today's customization JSON with the field that build did not have taken out. */
    private fun archivedBy59(c: SceneCustomization): String =
        c.toJson().apply { remove("palmsInsteadOfTrees") }.toString()

    private suspend fun plantAsWrittenBy59() = store.edit {
        // Autumn under live edit: its archive restored into the flat keys, palms_enabled among them.
        it[stringPreferencesKey("pending_customization_theme_id")] = "autumn"
        it[booleanPreferencesKey("palms_enabled")] = true
        it[floatPreferencesKey("obj_HOUSES_density")] = 0.4f
        // Christmas and Beach edited earlier, each in its own archive.
        it[stringPreferencesKey("theme_customization_christmas")] = archivedBy59(defaultCustomizationFor("christmas"))
        it[stringPreferencesKey("theme_customization_beach")] = archivedBy59(defaultCustomizationFor("beach"))
    }

    @Test
    fun `a store written by 5_9 puts no palm on a theme that plants none, and keeps Beach's`() = runBlocking {
        plantAsWrittenBy59()
        val read = prefs.settingsFlow.first()
        val autumn = read.themeCustomizations.getValue("autumn")
        assertTrue("the flat keys must carry the old true for this to mean anything", autumn.palmsEnabled)
        assertFalse("Autumn came back from a 5.9 store with palms", autumn.palmsShown(layoutPlantsPalms = false))
        assertFalse("and the live edit too", read.pendingCustomization.palmsShown(layoutPlantsPalms = false))
        val christmas = read.themeCustomizations.getValue("christmas")
        assertTrue(christmas.palmsEnabled)
        assertFalse("Christmas came back from a 5.9 store with palms", christmas.palmsShown(layoutPlantsPalms = false))
        assertTrue("Beach came back without its palms", read.themeCustomizations.getValue("beach").palmsShown(layoutPlantsPalms = true))
    }

    @Test
    fun `the switch turned on after the update stays on, through the archive and back`() = runBlocking {
        plantAsWrittenBy59()
        prefs.setPalmsEnabled(true, "christmas")
        assertTrue(prefs.settingsFlow.first().themeCustomizations.getValue("christmas").palmsShown(layoutPlantsPalms = false))
        // An edit on another theme archives Christmas; one on Christmas restores it.
        prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.3f, "winter")
        assertTrue("archived", prefs.settingsFlow.first().themeCustomizations.getValue("christmas").palmsShown(layoutPlantsPalms = false))
        prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.6f, "christmas")
        assertTrue("restored", prefs.settingsFlow.first().themeCustomizations.getValue("christmas").palmsShown(layoutPlantsPalms = false))
        assertFalse("and Winter, edited in between, has none", prefs.settingsFlow.first().themeCustomizations.getValue("winter").palmsShown(layoutPlantsPalms = false))
        // "Reset Trees to default" takes them away again: off is Christmas's own default. (The
        // decorations' reset did until v5.10E, inventory I-294: the switch is on the Trees page.)
        prefs.resetCategory(ObjectCategory.TREES, "christmas")
        assertFalse(prefs.settingsFlow.first().themeCustomizations.getValue("christmas").palmsShown(layoutPlantsPalms = false))
    }

    @Test
    fun `turning palms off on Beach still turns Beach's palms off`() = runBlocking {
        plantAsWrittenBy59()
        prefs.setPalmsEnabled(false, "beach")
        assertFalse(prefs.settingsFlow.first().themeCustomizations.getValue("beach").palmsShown(layoutPlantsPalms = true))
        prefs.resetCategory(ObjectCategory.TREES, "beach")
        assertTrue("the Trees reset gives Beach its palms back", prefs.settingsFlow.first().themeCustomizations.getValue("beach").palmsShown(layoutPlantsPalms = true))
    }
}
