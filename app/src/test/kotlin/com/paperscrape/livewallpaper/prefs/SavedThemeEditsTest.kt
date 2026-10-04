package com.paperscrape.livewallpaper.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.CustomThemeEntry
import com.paperscrape.livewallpaper.engine.CustomThemeRegistry
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.SceneObjectCatalog
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import com.paperscrape.livewallpaper.ui.SettingsUiModel
import com.paperscrape.livewallpaper.ui.replaceWithCurrent
import com.paperscrape.livewallpaper.ui.snapshotEntry
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **A theme saved in the gallery keeps its look when it is edited from the menus** (v5.10E, inventory
 * I-295, found by the read-only review of the round and confirmed on the phone; the maintainer,
 * 2026-10-04: *«Ripararlo (consigliato)»*), and **"Replace with current" is not hidden by the replaced
 * theme's own edits** (the review's D1, inside I-215).
 *
 * Until v5.10E the first touch of any control on a saved theme -- the moon turned off and on again --
 * started the edit from the theme's factory values, and an edit wins over a saved version, so the theme
 * went back to how it ships. On the app's own store, read back as the wallpaper and the gallery read it.
 */
class SavedThemeEditsTest {

    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var prefs: WallpaperPrefs
    private var data = CustomThemeData.EMPTY

    @Before
    fun open() {
        dir = kotlin.io.path.createTempDirectory("saved-themes").toFile()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "prefs.preferences_pb") }
        prefs = WallpaperPrefs(store)
    }

    @After
    fun close() {
        scope.cancel()
        dir.deleteRecursively()
        CustomThemeRegistry.update(CustomThemeData.EMPTY)
    }

    private fun save(newData: CustomThemeData) {
        data = newData
        CustomThemeRegistry.update(newData)
    }

    private suspend fun customizationOf(themeId: String): SceneCustomization {
        val s = prefs.settingsFlow.first()
        return CustomThemeRegistry.resolveActiveCustomization(
            themeId, s.pendingCustomization, s.pendingCustomizationThemeId, s.themeCustomizations,
        )
    }

    /**
     * Beach saved with a look of its own: what "Replace with current" from another theme leaves. Every value
     * differs from Beach's factory one (its lake ships at 0.9), or a test could not tell the two apart.
     */
    private fun savedBeach(): CustomThemeEntry {
        val theme = ThemeCatalog.byId("beach")
        val look = defaultCustomizationFor("beach").let {
            it.copy(
                lake = it.lake.copy(height = 0.55f),
                clouds = it.clouds.copy(density = 0.1f),
                houses = it.houses.copy(density = 0.2f),
                hillsVariation = 0.12f,
            )
        }
        return CustomThemeEntry("beach", "Beach", theme, SceneObjectCatalog.layoutFor("beach", theme.accentColor), look)
    }

    @Test
    fun `a control moved and moved back leaves a saved theme as it was saved`() = runBlocking {
        val saved = savedBeach()
        save(CustomThemeData(overrides = mapOf("beach" to saved)))
        assertEquals(saved.customization, customizationOf("beach"))
        // The moon off and on again: the photograph of the question.
        prefs.setMoonVisible(false, "beach")
        prefs.setMoonVisible(true, "beach")
        assertEquals("the saved look went back to the factory one", saved.customization, customizationOf("beach"))
        // And the reset rows of Advanced & about do not count it as edited.
        val reset = SettingsUiModel.themeResetState(ThemeCatalog.ALL, data.overrides, prefs.settingsFlow.first().themeCustomizations)
        assertTrue("Beach listed as edited after a change undone", "Beach" !in reset.currentEdits)
    }

    @Test
    fun `an edit on a saved theme changes what it touches and nothing else`() = runBlocking {
        val saved = savedBeach()
        save(CustomThemeData(overrides = mapOf("beach" to saved)))
        prefs.setCategoryDensity(ObjectCategory.TREES, 0.44f, "beach")
        val after = customizationOf("beach")
        assertEquals(0.44f, after.trees.density)
        assertEquals("the rest is the saved look", saved.customization.copy(trees = saved.customization.trees.copy(density = 0.44f)), after)
    }

    @Test
    fun `a theme of your own keeps its look when edited`() = runBlocking {
        val theme = ThemeCatalog.byId("autumn").copy(id = "custom:mine", displayName = "Mine")
        val look = defaultCustomizationFor("autumn").copy(hillsVariation = 0.91f, leafPiles = 0.66f)
        val mine = CustomThemeEntry("custom:mine", "Mine", theme, SceneObjectCatalog.layoutFor("autumn", theme.accentColor), look)
        save(CustomThemeData(customThemes = listOf(mine)))
        prefs.setStarsVisible(false, "custom:mine")
        assertEquals(look.copy(stars = look.stars.copy(visible = false)), customizationOf("custom:mine"))
    }

    @Test
    fun `a page reset on a saved theme puts that page as the theme ships, and keeps the rest saved`() = runBlocking {
        val saved = savedBeach()
        save(CustomThemeData(overrides = mapOf("beach" to saved)))
        prefs.resetCategory(ObjectCategory.HOUSES, "beach")
        val after = customizationOf("beach")
        assertEquals("Reset Houses to default: the theme's own houses", defaultCustomizationFor("beach").houses, after.houses)
        assertEquals("and the lake still the saved one", 0.55f, after.lake.height)
        assertEquals("and the hills", 0.12f, after.hillsVariation)
        assertTrue("the saved values are not the factory ones", defaultCustomizationFor("beach").let { it.lake.height != 0.55f && it.hillsVariation != 0.12f })
    }

    @Test
    fun `a theme edited before keeps its edits, not the saved look`() = runBlocking {
        // The archive is the more recent of the two, as it always was.
        prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.3f, "beach")
        prefs.setStarsVisible(false, "winter") // archives Beach's edit
        save(CustomThemeData(overrides = mapOf("beach" to savedBeach())))
        prefs.setStarsVisible(false, "beach") // back to Beach: from its archive
        val after = customizationOf("beach")
        assertEquals(0.3f, after.houses.density)
        assertEquals("the archive is factory Beach plus the edit", defaultCustomizationFor("beach").lake, after.lake)
    }

    // ------------------------------------------------------------ "Replace with current" (I-215, D1)

    @Test
    fun `Replace with current is not hidden by the replaced theme's own edits`() = runBlocking {
        // Beach edited from the menus, then the user moves to Autumn and replaces Beach with it.
        prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.3f, "beach")
        prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.5f, "autumn")
        val s = prefs.settingsFlow.first()
        val snapshot = snapshotEntry("beach", "Beach", "autumn", s.pendingCustomization, s.pendingCustomizationThemeId, s.themeCustomizations)
        replaceWithCurrent(
            targetId = "beach",
            showingId = "autumn",
            writeSavedVersion = { save(data.copy(overrides = data.overrides + ("beach" to snapshot))) },
            clearEdits = { prefs.resetAllCategories(it) },
        )
        assertEquals("Beach looks like Autumn as it looked", snapshot.customization, customizationOf("beach"))
        assertEquals("and Autumn keeps its own edits", 0.5f, customizationOf("autumn").houses.density)
    }

    @Test
    fun `keeping the theme showing as it looks now keeps its edits`() = runBlocking {
        prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.5f, "autumn")
        val s = prefs.settingsFlow.first()
        val snapshot = snapshotEntry("autumn", "Autumn", "autumn", s.pendingCustomization, s.pendingCustomizationThemeId, s.themeCustomizations)
        replaceWithCurrent(
            targetId = "autumn",
            showingId = "autumn",
            writeSavedVersion = { save(data.copy(overrides = data.overrides + ("autumn" to snapshot))) },
            clearEdits = { prefs.resetAllCategories(it) },
        )
        assertEquals(0.5f, customizationOf("autumn").houses.density)
        assertNotEquals(defaultCustomizationFor("autumn"), customizationOf("autumn"))
        // Still the theme under edit: its edits were not wiped and handed to the saved copy.
        assertEquals("autumn", prefs.settingsFlow.first().pendingCustomizationThemeId)
    }
}
