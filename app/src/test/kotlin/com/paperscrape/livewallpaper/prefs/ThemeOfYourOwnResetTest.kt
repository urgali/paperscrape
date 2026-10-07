package com.paperscrape.livewallpaper.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.paperscrape.livewallpaper.engine.BuildingGroundContrast
import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.CustomThemeEntry
import com.paperscrape.livewallpaper.engine.CustomThemeRegistry
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.SceneObjectCatalog
import com.paperscrape.livewallpaper.engine.SceneSpace
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **The question's case, on the app's own store** (v5.11, inventory I-419; the maintainer's
 * *«Ripararlo prima di pubblicare»* of 2026-10-07): a theme of the user's own copied from Big City and
 * saved with the slate towers and shops every theme had before 5.11, then "Reset Buildings to default" and
 * "Reset Shops to default" from its Cities page. Until v5.11C they brought the slate back -- the towers
 * 3.8 from Big City's hills by day; now they bring Big City's own pairs. Read back as the wallpaper and
 * the gallery read it ([CustomThemeRegistry.resolveActiveCustomization]).
 */
class ThemeOfYourOwnResetTest {

    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var prefs: WallpaperPrefs

    @Before
    fun open() {
        dir = kotlin.io.path.createTempDirectory("own-theme-reset").toFile()
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

    private suspend fun customizationOf(themeId: String): SceneCustomization {
        val s = prefs.settingsFlow.first()
        return CustomThemeRegistry.resolveActiveCustomization(
            themeId, s.pendingCustomization, s.pendingCustomizationThemeId, s.themeCustomizations,
        )
    }

    @Test
    fun `resetting a copy of Big City brings Big City's towers and shops, not the slate`() = runBlocking {
        val city = ThemeCatalog.byId("city")
        val slate = SceneCustomization.DEFAULT.buildings
        // Saved before 5.11: the towers in the slate, and no shops of their own (read as the Buildings ones).
        val look = defaultCustomizationFor("city").let { it.copy(buildings = slate.copy(density = it.buildings.density), shops = SceneCustomization.DEFAULT.shops) }
        val id = "custom:town"
        val town = CustomThemeEntry(id, "Town", city.copy(id = id, displayName = "Town"), SceneObjectCatalog.layoutFor("city", city.accentColor), look)
        CustomThemeRegistry.update(CustomThemeData(customThemes = listOf(town)))
        assertEquals("the saved look is drawn as saved", slate.colorDay1, customizationOf(id).buildings.colorDay1)

        prefs.resetCategory(ObjectCategory.BUILDINGS, id)
        prefs.resetCategory(ObjectCategory.SHOPS, id)
        val after = customizationOf(id)
        val cityShipped = defaultCustomizationFor("city")
        assertEquals("towers, day 1", cityShipped.buildings.colorDay1, after.buildings.colorDay1)
        assertEquals("towers, night 2", cityShipped.buildings.colorNight2, after.buildings.colorNight2)
        assertEquals("shops, day 1", cityShipped.shops.colorDay1, after.shops.colorDay1)
        assertEquals("shops, night 2", cityShipped.shops.colorNight2, after.shops.colorNight2)
        // Against its own ground as saved: Big City's hills, the mountains off.
        assertTrue(BuildingGroundContrast.passes(after, SceneSpace.SceneVariant.TOWER))
        assertTrue(BuildingGroundContrast.passes(after, SceneSpace.SceneVariant.RESTAURANT))
        // The rest of the saved look is untouched by the two resets.
        assertEquals(look.houses, after.houses)
        assertEquals(look.hillsColorDay, after.hillsColorDay)
    }
}
