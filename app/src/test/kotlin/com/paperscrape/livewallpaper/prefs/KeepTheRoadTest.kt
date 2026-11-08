package com.paperscrape.livewallpaper.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.paperscrape.livewallpaper.engine.CarSelection
import com.paperscrape.livewallpaper.engine.RandomSceneGenerator
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.SceneObjectCatalog
import com.paperscrape.livewallpaper.engine.SceneObjectRenderer
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import com.paperscrape.livewallpaper.engine.sceneCustomizationFromJson
import com.paperscrape.livewallpaper.engine.toJson
import com.paperscrape.livewallpaper.ui.AmountTap
import com.paperscrape.livewallpaper.ui.SettingsUiModel
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
 * **0 % is no car, and *Keep the road* decides the road while no car drives** (`DESIGN_NOTES.md` §12, decision 104): the
 * maintainer's *«per la strada mettiamo un interruttore che, se le auto sono impostate a 0%, l'utente può decidere se
 * rimuovere la strada o lasciarla ma con 0 auto»* and *«Sì, 0 % = zero»* (2026-11-07).
 *
 * The rule ([SceneObjectRenderer.drawsRoad]) in every case; the lines of the Cars page; *Show Cars* reading off with both
 * densities at 0 %; a random theme born without cars given the built-in streets' cars, off, and no road, as it was drawn;
 * and who updates: a store, a theme or a backup written before the switch reads it as the road it drew, so the road stays
 * empty where both densities were at 0 %, and the first edit writes that reading down.
 */
class KeepTheRoadTest {

    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var prefs: WallpaperPrefs

    @Before
    fun open() {
        dir = kotlin.io.path.createTempDirectory("road-store").toFile()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "prefs.preferences_pb") }
        prefs = WallpaperPrefs(store)
    }

    @After
    fun close() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private val city = SceneObjectCatalog.layoutFor("city", ThemeCatalog.byId("city").accentColor)

    private fun cars(visible: Boolean, day: Float, night: Float, keep: Boolean): SceneCustomization =
        defaultCustomizationFor("city").let { it.copy(cars = it.cars.copy(visible = visible, density = day), carsNightDensity = night, keepRoad = keep) }

    @Test
    fun `the road is there while a car drives at some hour, and with none Keep the road decides`() {
        for (visible in listOf(true, false)) for (day in listOf(0f, 0.4f)) for (night in listOf(0f, 0.7f)) for (keep in listOf(true, false)) {
            val c = cars(visible, day, night, keep)
            val drives = visible && (day > 0f || night > 0f)
            val label = "visible $visible day $day night $night keep $keep"
            assertEquals(label, drives, SceneObjectRenderer.carsDriveAtSomeHour(c))
            assertEquals(label, drives || keep, SceneObjectRenderer.drawsRoad(city, c))
            assertEquals(
                label,
                when { drives -> SettingsUiModel.ROAD_WITH_CARS_LINE; keep -> SettingsUiModel.ROAD_KEPT_LINE; else -> SettingsUiModel.ROAD_TAKEN_LINE },
                SettingsUiModel.keepRoadLine(c),
            )
            // Show Cars reads on only while a car drives at some hour; off and said with both at 0 %.
            val shown = SettingsUiModel.cars(visible, day, night)
            assertEquals(label, drives, shown.shownOn)
            assertEquals(label, day <= 0f && night <= 0f, shown.noneAtZero)
        }
        // Cars by day and none at night: the road stays all day, settled by the settings, not by the hour.
        val dayOnly = cars(true, 0.5f, 0f, keep = false)
        assertTrue(SceneObjectRenderer.drawsRoad(city, dayOnly))
        assertEquals(0, CarSelection.countFor(CarSelection.densityAt(0.5f, 0f, 0f), 10))
        // Off as every theme ships.
        for (theme in ThemeCatalog.ALL) assertFalse(theme.id, defaultCustomizationFor(theme.id).keepRoad)
    }

    @Test
    fun `a tap on Show Cars with both at 0 percent brings the day density back`() {
        val tap = SettingsUiModel.carsTap(true, 0f, 0f, defaultDayDensity = 0.65f)
        assertTrue("$tap", tap is AmountTap.Restore && tap.amount == 0.65f)
        assertTrue(SettingsUiModel.carsTap(false, 0f, 0f, 0.65f) is AmountTap.SetVisible)
        assertTrue(SettingsUiModel.carsTap(true, 0f, 0.3f, 0.65f) is AmountTap.SetVisible)
    }

    /**
     * A random theme's starting settings with a built-in's colours standing in for its own, which need Android's HSV:
     * the switches read the street it was born with, not its colours.
     */
    private fun randomDefaults(id: String) = defaultCustomizationFor(id, ThemeCatalog.byId("autumn").copy(id = id))

    /** The first random theme of the ids the catalogue reads on the JVM that rolled [cars] cars. */
    private fun randomWith(cars: Int): String =
        (1..400).map { "random:$it" }.first { RandomSceneGenerator.born(it).carCount == cars }

    @Test
    fun `a random theme born without cars is given the built-in streets' cars, off, and has no road as it ships`() {
        val id = randomWith(0)
        val layout = SceneObjectCatalog.layoutFor(id, 0)
        assertEquals("$id: the built-in streets' ten", 10, layout.cars.size)
        val c = randomDefaults(id)
        assertFalse("$id: the cars off", c.cars.visible)
        assertFalse("$id: no road, as the theme was born", SceneObjectRenderer.drawsRoad(layout, c))
        // Turned on, they drive and the road comes.
        assertTrue(SceneObjectRenderer.drawsRoad(layout, c.copy(cars = c.cars.copy(visible = true))))
        // Born with cars: on, as it was born.
        val withCars = randomWith(1)
        assertTrue(randomDefaults(withCars).cars.visible)
        assertEquals(1, SceneObjectCatalog.layoutFor(withCars, 0).cars.size)
    }

    @Test
    fun `a theme or a backup written before the switch keeps the road it drew`() {
        val autumn = ThemeCatalog.byId("autumn")
        for ((day, night, kept) in listOf(Triple(0f, 0f, true), Triple(0.4f, 0f, false), Triple(0f, 0.3f, false), Triple(0.65f, 1f, false))) {
            val written = defaultCustomizationFor("autumn").let { it.copy(cars = it.cars.copy(density = day), carsNightDensity = night) }
                .toJson().apply { remove("keepRoad") }
            val read = sceneCustomizationFromJson(written, autumn)
            assertEquals("day $day night $night", kept, read.keepRoad)
            assertTrue("day $day night $night: the road as it was", SceneObjectRenderer.drawsRoad(SceneObjectCatalog.layoutFor("autumn", 0), read))
        }
        // Written with the switch, it reads as written.
        val off = defaultCustomizationFor("autumn").let { it.copy(cars = it.cars.copy(density = 0f), carsNightDensity = 0f) }
        assertFalse(sceneCustomizationFromJson(off.toJson(), autumn).keepRoad)
        assertTrue(sceneCustomizationFromJson(off.copy(keepRoad = true).toJson(), autumn).keepRoad)
        // A random theme born without cars, written with them on and at 0 %: no road then, none now, the cars off.
        val id = randomWith(0)
        val random = randomDefaults(id).let { it.copy(cars = it.cars.copy(visible = true, density = 0f), carsNightDensity = 0f) }
            .toJson().apply { remove("keepRoad") }
        val read = sceneCustomizationFromJson(random, ThemeCatalog.byId("autumn").copy(id = id))
        assertFalse("$id: the cars it never had stay off", read.cars.visible)
        assertFalse("$id: no road, as it drew", SceneObjectRenderer.drawsRoad(SceneObjectCatalog.layoutFor(id, 0), read))
    }

    @Test
    fun `a store written with the cars at 0 percent keeps the road, and the first edit writes it down`() = runBlocking {
        store.edit {
            it[stringPreferencesKey("theme_id")] = "autumn"
            it[stringPreferencesKey("pending_customization_theme_id")] = "autumn"
            it[floatPreferencesKey("obj_CARS_density")] = 0f
            it[floatPreferencesKey("cars_night_density")] = 0f
        }
        val before = prefs.settingsFlow.first().themeCustomizations.getValue("autumn")
        assertTrue("the road it drew, kept", before.keepRoad)
        assertEquals("no car at 0 %", 0, CarSelection.countFor(before.cars.density, 10))
        // An edit of anything: the reading is written, and stays when the densities move.
        prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.5f, "autumn")
        assertEquals(true, store.data.first()[booleanPreferencesKey("keep_road")])
        prefs.setCategoryDensity(ObjectCategory.CARS, 0.5f, "autumn")
        prefs.setCategoryDensity(ObjectCategory.CARS, 0f, "autumn")
        assertTrue(prefs.settingsFlow.first().themeCustomizations.getValue("autumn").keepRoad)
    }

    @Test
    fun `a store that never held the switch, taken to 0 percent, takes the road away as the switch ships`() = runBlocking {
        store.edit { it[stringPreferencesKey("theme_id")] = "autumn" }
        prefs.setCategoryDensity(ObjectCategory.CARS, 0f, "autumn")
        prefs.setCarsNightDensity(0f, "autumn")
        val c = prefs.settingsFlow.first().themeCustomizations.getValue("autumn")
        assertFalse(c.keepRoad)
        assertFalse(SceneObjectRenderer.drawsRoad(SceneObjectCatalog.layoutFor("autumn", 0), c))
        prefs.setKeepRoad(true, "autumn")
        assertTrue(SceneObjectRenderer.drawsRoad(SceneObjectCatalog.layoutFor("autumn", 0), prefs.settingsFlow.first().themeCustomizations.getValue("autumn")))
    }
}
