package com.paperscrape.livewallpaper.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.SceneObjectCatalog
import com.paperscrape.livewallpaper.engine.SceneObjectType
import com.paperscrape.livewallpaper.engine.SceneSpace
import com.paperscrape.livewallpaper.engine.StorefrontGroup
import com.paperscrape.livewallpaper.engine.StorefrontStreet
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.engine.ThemePreviewScenes
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import com.paperscrape.livewallpaper.engine.keepCandidate
import com.paperscrape.livewallpaper.engine.offWhereStreetLacks
import com.paperscrape.livewallpaper.engine.sceneCustomizationFromJson
import com.paperscrape.livewallpaper.engine.toJson
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
import java.io.File

/**
 * ***Show Shops* and *Show Public buildings*, a switch each, and *Show Towers*** (`DESIGN_NOTES.md` §12, decision 109):
 * the maintainer's *«Due, uno per pagina (consigliata)»* (2026-11-15). Each group's switch holds its own turns among the
 * houses, below the road too, whatever *Show Towers* says; a group switched off leaves its turns empty, on the wallpaper
 * and on the gallery card; *Show Towers* holds the towers alone. **Who updates sees the scene the 5.19 drew**: a store, a
 * saved or archived theme, a theme file or a backup without the two switches reads each as the one switch of before and
 * *Show Buildings* both on ([SceneCustomization.placeSwitchAsDrawnBefore]), and the first edit writes that down. The
 * resets: each group's page puts back its own switch, the towers' page no longer touches them.
 */
class PlaceSwitchesTest {

    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var prefs: WallpaperPrefs

    @Before
    fun open() {
        dir = kotlin.io.path.createTempDirectory("places-store").toFile()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "prefs.preferences_pb") }
        prefs = WallpaperPrefs(store)
    }

    @After
    fun close() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private val shops = booleanPreferencesKey("shops_visible")
    private val publics = booleanPreferencesKey("publics_visible")
    private val places = booleanPreferencesKey("places_visible")
    private val buildings = booleanPreferencesKey("obj_BUILDINGS_visible")

    /** A store as the 5.19 left it: *Keep the road* written, so it is not read as one older still. */
    private suspend fun plant519(placesVisible: Boolean?, buildingsVisible: Boolean?) {
        store.edit {
            it.clear()
            it[stringPreferencesKey("theme_id")] = "autumn"
            it[stringPreferencesKey("pending_customization_theme_id")] = "autumn"
            it[booleanPreferencesKey("keep_road")] = false
            if (placesVisible != null) it[places] = placesVisible
            if (buildingsVisible != null) it[buildings] = buildingsVisible
        }
    }

    private suspend fun autumn(): SceneCustomization = prefs.settingsFlow.first().themeCustomizations.getValue("autumn")

    @Test
    fun `a store the 5_19 wrote reads as the scene it drew, in every case, and the first edit writes that down`() = runBlocking {
        for (placesVisible in listOf(null, true, false)) for (buildingsVisible in listOf(null, true, false)) {
            val what = "places_visible $placesVisible, Show Buildings $buildingsVisible"
            plant519(placesVisible, buildingsVisible)
            val drew = (placesVisible ?: true) && (buildingsVisible ?: true)
            val read = autumn()
            assertEquals(what, drew, read.shopsVisible)
            assertEquals(what, drew, read.publicsVisible)
            assertEquals("$what: the towers as they were", buildingsVisible ?: true, read.buildings.visible)
            // An edit of anything else: the reading is written, and Show Towers then moves the towers alone.
            prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.5f, "autumn")
            assertEquals(what, drew, store.data.first()[shops])
            assertEquals(what, drew, store.data.first()[publics])
            prefs.setCategoryVisible(ObjectCategory.BUILDINGS, !(buildingsVisible ?: true), "autumn")
            assertEquals("$what: Show Towers flipped", drew, autumn().shopsVisible)
            assertEquals("$what: Show Towers flipped", drew, autumn().publicsVisible)
        }
    }

    @Test
    fun `a store written now reads its two switches whatever Show Towers says`() = runBlocking {
        for (s in listOf(true, false)) for (p in listOf(true, false)) for (towers in listOf(true, false)) {
            store.edit { it.clear(); it[stringPreferencesKey("theme_id")] = "autumn" }
            prefs.setShopsVisible(s, "autumn")
            prefs.setPublicsVisible(p, "autumn")
            prefs.setCategoryVisible(ObjectCategory.BUILDINGS, towers, "autumn")
            val read = autumn()
            assertEquals(s, read.shopsVisible)
            assertEquals(p, read.publicsVisible)
            assertEquals(towers, read.buildings.visible)
        }
    }

    @Test
    fun `each group's reset puts back its switch, and the towers' reset leaves them`() = runBlocking {
        store.edit { it.clear(); it[stringPreferencesKey("theme_id")] = "autumn" }
        prefs.setShopsVisible(false, "autumn")
        prefs.setPublicsVisible(false, "autumn")
        prefs.setCategoryVisible(ObjectCategory.BUILDINGS, false, "autumn")
        prefs.resetCategory(ObjectCategory.BUILDINGS, "autumn")
        assertTrue("Reset Towers: the towers back", autumn().buildings.visible)
        assertFalse("Reset Towers leaves the shops", autumn().shopsVisible)
        assertFalse("Reset Towers leaves the public buildings", autumn().publicsVisible)
        prefs.resetCategory(ObjectCategory.SHOPS, "autumn")
        assertTrue("Reset Shops: Show Shops back", autumn().shopsVisible)
        assertFalse(autumn().publicsVisible)
        prefs.resetCategory(ObjectCategory.PUBLICS, "autumn")
        assertTrue("Reset Public buildings: Show Public buildings back", autumn().publicsVisible)
        // Written, not removed: a removed key would read the one switch of before -- with Show Towers off, off.
        assertEquals(true, store.data.first()[shops])
        assertEquals(true, store.data.first()[publics])
        prefs.setCategoryVisible(ObjectCategory.BUILDINGS, false, "autumn")
        prefs.setShopsVisible(false, "autumn")
        prefs.setPublicsVisible(false, "autumn")
        prefs.resetCategory(ObjectCategory.SHOPS, "autumn")
        assertEquals("Reset Shops with Show Towers off", true, store.data.first()[shops])
        assertTrue(autumn().shopsVisible)
        prefs.resetCategory(ObjectCategory.PUBLICS, "autumn")
        assertEquals("Reset Public buildings with Show Towers off", true, store.data.first()[publics])
        assertTrue(autumn().publicsVisible)
        assertFalse("the towers as they were", autumn().buildings.visible)
    }

    @Test
    fun `a theme file, a saved theme or a backup the 5_19 wrote reads as the scene it drew, and one written now carries the one switch too`() {
        val theme = ThemeCatalog.byId("autumn")
        val base = defaultCustomizationFor("autumn")
        for (placesVisible in listOf(null, true, false)) for (buildingsVisible in listOf(true, false)) {
            val json = base.copy(buildings = base.buildings.copy(visible = buildingsVisible)).toJson().apply {
                remove("shopsVisible"); remove("publicsVisible")
                if (placesVisible == null) remove("placesVisible") else put("placesVisible", placesVisible)
            }
            val read = sceneCustomizationFromJson(json, theme)
            val drew = (placesVisible ?: base.shopsVisible) && buildingsVisible
            assertEquals("placesVisible $placesVisible, buildings $buildingsVisible", drew, read.shopsVisible)
            assertEquals("placesVisible $placesVisible, buildings $buildingsVisible", drew, read.publicsVisible)
        }
        for (s in listOf(true, false)) for (p in listOf(true, false)) {
            val written = base.copy(shopsVisible = s, publicsVisible = p).toJson()
            assertEquals("for the 5.19: either group", s || p, written.getBoolean("placesVisible"))
            val read = sceneCustomizationFromJson(written, theme)
            assertEquals(s, read.shopsVisible)
            assertEquals(p, read.publicsVisible)
        }
    }

    @Test
    fun `a place stands while either group is on, whatever Show Towers says, and a tower by Show Towers alone`() {
        val layout = SceneObjectCatalog.layoutFor("autumn", ThemeCatalog.byId("autumn").accentColor)
        val placesOf = layout.staticObjects.filter { StorefrontStreet.isPlace(it) }
        val towersOf = layout.staticObjects.filter { it.type == SceneObjectType.SKYSCRAPER && it.depthFraction < SceneSpace.BUILDING_TOWER_MAX_DEPTH }
        assertTrue(placesOf.isNotEmpty() && towersOf.isNotEmpty())
        val base = defaultCustomizationFor("autumn")
        for (s in listOf(true, false)) for (p in listOf(true, false)) for (towers in listOf(true, false)) {
            val c = base.copy(shopsVisible = s, publicsVisible = p, buildings = base.buildings.copy(visible = towers, density = 1f))
            val what = "shops $s publics $p towers $towers"
            assertTrue(what, placesOf.all { c.keepCandidate(it, layout.densityScheme) == (s || p) })
            assertTrue(what, towersOf.all { c.keepCandidate(it, layout.densityScheme) == towers })
            assertEquals(what, s, c.groupStands(StorefrontGroup.SHOPS))
            assertEquals(what, p, c.groupStands(StorefrontGroup.PUBLIC))
        }
    }

    @Test
    fun `a street born with no place starts with both switches off`() {
        val base = defaultCustomizationFor("autumn")
        val street = SceneObjectCatalog.layoutFor("autumn", 0).staticObjects
        val without = base.offWhereStreetLacks(street.filter { !StorefrontStreet.isPlace(it) }, 1)
        assertFalse(without.shopsVisible)
        assertFalse(without.publicsVisible)
        val with = base.offWhereStreetLacks(street, 1)
        assertTrue(with.shopsVisible && with.publicsVisible)
    }

    /**
     * **The wallpaper leaves a group's turns empty**, above the road and below it: the renderer draws the building a copy
     * deals in a place only while its group stands, read off `SceneObjectRenderer.drawStaticObject`, whole lines.
     */
    @Test
    fun `the wallpaper draws a place's building only while its group stands, above and below the road`() {
        var dir: File? = File(".").absoluteFile
        var text: String? = null
        while (dir != null && text == null) {
            for (prefix in listOf("", "app/")) {
                val f = File(dir, "${prefix}src/main/kotlin/com/paperscrape/livewallpaper/engine/SceneObjectRenderer.kt")
                if (f.isFile) text = f.readText()
            }
            dir = dir.parentFile
        }
        val body = text!!.substringAfter("    private fun drawStaticObject(").substringBefore("        // Dispatched on the same variant")
        val lines = body.lines().map { it.trim() }
        val near = lines.indexOf("val shop = SceneObjectCatalog.nearBuilding(themeSeed, nearPlan, copy, r.place, streetDeal, nearShopDeck)")
        assertEquals("if (customization.groupStands(shop.group)) {", lines[near + 1])
        assertTrue(lines[near + 2].startsWith("drawNeighbourhoodBuilding(canvas, nearBuildings[shop.ordinal]"))
        val street = lines.indexOf("if (customization.groupStands(storefront.group)) {")
        assertTrue(street > near)
        assertTrue(lines[street + 1].startsWith("drawNeighbourhoodBuilding(canvas, buildings[storefront.ordinal]"))
        assertEquals("two places draw buildings, both by their group", 2, lines.count { it.startsWith("drawNeighbourhoodBuilding(canvas, ") && ("shop" in it || "storefront" in it) })
    }

    /**
     * **The gallery card leaves a group's places empty too** (decision 109): its three buildings among the houses are each
     * of one group, so a group switched off takes away exactly its own, both off all three; *Show Towers* takes none of them.
     */
    @Test
    fun `the gallery card draws a place while its group is on, whatever Show Towers says`() {
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id)
            fun count(x: SceneCustomization) = ThemePreviewScenes.forTheme(theme, x).items.size
            val all = count(c)
            val noShops = all - count(c.copy(shopsVisible = false))
            val noPublics = all - count(c.copy(publicsVisible = false))
            assertEquals("${theme.id}: the three places, each of one group", 3, noShops + noPublics)
            assertEquals("${theme.id}: both off", 3, all - count(c.copy(shopsVisible = false, publicsVisible = false)))
            val towersOff = c.copy(buildings = c.buildings.copy(visible = false))
            assertEquals(
                "${theme.id}: Show Towers off takes the towers, not the places",
                count(towersOff) - 3, count(towersOff.copy(shopsVisible = false, publicsVisible = false)),
            )
        }
    }
}
