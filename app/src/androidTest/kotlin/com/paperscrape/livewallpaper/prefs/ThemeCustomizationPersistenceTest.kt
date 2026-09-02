package com.paperscrape.livewallpaper.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.engine.CustomThemeEntry
import com.paperscrape.livewallpaper.engine.CustomThemeRegistry
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.SceneObjectCatalog
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import com.paperscrape.livewallpaper.engine.sceneCustomizationFromJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A customization the user has made must survive everything except the user deleting it.
 *
 * ### The defect, reproduced on a device before it was fixed
 *
 * Until v4.3 the only storage a per-theme edit reached was a **single flat set of DataStore keys**
 * with one `pending_customization_theme_id` marker naming whichever theme they currently belonged
 * to. `ensureFreshPendingTheme` — added in v2.12 to stop a *different* bug, one theme's values
 * leaking into another — wiped every one of those keys the moment a setter fired for a different
 * theme. So the sequence "customise `beach`, then move one slider on `winter`" deleted everything
 * the user had done to `beach`, from disk, silently and permanently:
 *
 * ```
 * after beach edits:     pendingThemeId=beach   houses=0.21  hillsDay=ff112233
 * after ONE winter edit: pendingThemeId=winter  houses=0.93  hillsDay=ffe9f1f7
 * beach resolves to:     houses=0.65  parasols=true  hillsDay=ffefd9a3   equalsDefault=TRUE
 * ```
 *
 * The maintainer reported it as "an update overwrote my beach settings". The update was a
 * coincidence: nothing in an install-over-install touches DataStore, `android:allowBackup` is
 * `false` so no cloud restore can either, and no startup path writes a per-theme setter. What an
 * update reliably *does* cause is a user opening the app and browsing their themes, which is all
 * it took.
 *
 * ### What these tests pin
 *
 * Not "a key exists in the DataStore" — the whole reconstructed [SceneCustomization], compared
 * field by field against what was written, for three built-in themes including `beach`, for a
 * built-in override and for a standalone custom theme, across a theme switch, a fresh read of the
 * store, and an install-over-install.
 */
@RunWith(AndroidJUnit4::class)
class ThemeCustomizationPersistenceTest {

    // Restores the real preference store around every test: these tests write the app's own
    // DataStore on purpose, and without this the phone kept their last theme. See RealPrefsGuard.
    @get:org.junit.Rule
    val realPrefs = RealPrefsGuard()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = WallpaperPrefs(context)
    private val store get() = CustomThemeStore(context)

    @Before
    fun clean() = runBlocking { wipe() }

    @After
    fun tidy() = runBlocking { wipe() }

    private suspend fun wipe() {
        for (id in THEMES + listOf("sunset", CUSTOM_ID)) prefs.resetAllCategories(id)
        store.clearAllOverrides()
        for (entry in store.dataFlow.first().customThemes) store.deleteCustomTheme(entry.id)
        CustomThemeRegistry.update(store.dataFlow.first())
    }

    /** A distinctive, entirely non-default look, different for every theme. */
    private suspend fun customise(themeId: String, seed: Int) {
        prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.11f * seed, themeId)
        prefs.setCategoryVisible(ObjectCategory.PARASOLS, seed % 2 == 0, themeId)
        prefs.setCategoryColorDay1(ObjectCategory.TREES, 0x00110000 * seed or 0xFF000000.toInt(), themeId)
        prefs.setHillsColorDay(0x00220000 * seed or 0xFF000000.toInt(), themeId)
        prefs.setLakeHeight(0.07f * seed, themeId)
        prefs.setPrecipitationIntensity(0.09f * seed, themeId)
        prefs.setWinterColorsEnabled(seed % 2 == 1, themeId)
        prefs.setPeopleNightDensity(0.05f * seed, themeId)
    }

    private suspend fun customizationOf(themeId: String): SceneCustomization {
        val s = prefs.settingsFlow.first()
        return CustomThemeRegistry.resolveActiveCustomization(
            themeId, s.pendingCustomization, s.pendingCustomizationThemeId, s.themeCustomizations,
        )
    }

    // ------------------------------------------------------------------ the reported defect

    /** The exact sequence that used to destroy `beach`. */
    @Test
    fun editingASecondThemeDoesNotDestroyTheFirst() = runBlocking {
        customise("beach", 1)
        val beach = customizationOf("beach")
        assertNotEquals("the test customised nothing", defaultCustomizationFor("beach"), beach)

        prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.93f, "winter")

        assertEquals("beach lost its customization", beach, customizationOf("beach"))
    }

    /** Three themes at once, each keeping its own look, compared in full. */
    @Test
    fun everyThemeKeepsItsOwnCustomization() = runBlocking {
        val expected = HashMap<String, SceneCustomization>()
        for ((index, id) in THEMES.withIndex()) {
            customise(id, index + 1)
            expected[id] = customizationOf(id)
        }
        // Round again, so every theme has been left and returned to.
        for ((index, id) in THEMES.withIndex()) {
            assertEquals("$id changed while other themes were edited", expected[id], customizationOf(id))
            assertNotEquals("$id was never actually customised", defaultCustomizationFor(id), expected[id])
        }
        // And no two themes ended up sharing one look, which is what a single flat slot produces.
        assertEquals("themes share a customization", THEMES.size, expected.values.distinct().size)
    }

    /** Returning to a theme resumes its own look rather than starting from defaults. */
    @Test
    fun reEditingAThemeResumesFromItsOwnCustomization() = runBlocking {
        customise("beach", 1)
        val beach = customizationOf("beach")
        prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.93f, "winter")
        // Touch beach again: one field changes, everything else must still be beach's.
        prefs.setCategoryDensity(ObjectCategory.BUILDINGS, 0.37f, "beach")
        val after = customizationOf("beach")
        assertEquals(
            "re-editing beach reset the rest of its look",
            beach.copy(buildings = beach.buildings.copy(density = 0.37f)),
            after,
        )
    }

    // ------------------------------------------------------------------ across a restart

    /**
     * What the next process sees.
     *
     * `preferencesDataStore` caches per process, so re-reading through the same instance proves
     * nothing about the bytes — the same reason `PrefsCorruptionRecoveryTest` opens its own store.
     * This opens a fresh `DataStore` over the production file, which is exactly what a restarted
     * app, and therefore an install-over-install, gets.
     */
    @Test
    fun customizationSurvivesAFreshProcessAndAnInstallOverInstall() = runBlocking {
        for ((index, id) in THEMES.withIndex()) customise(id, index + 1)
        val before = THEMES.associateWith { customizationOf(it) }

        val file = File(context.filesDir, "datastore/$WALLPAPER_PREFS_STORE_NAME.preferences_pb")
        assertTrue("the production store file is not where the test expects it", file.exists())

        // An install-over-install replaces the APK and leaves `files/` alone, so what the next
        // process reads is these exact bytes. DataStore refuses two live instances on one file, so
        // the bytes are copied and a fresh store is opened over the copy -- same content, same
        // reader, no shared cache to hide behind.
        val copy = File(context.cacheDir, "upgrade-sim.preferences_pb")
        file.copyTo(copy, overwrite = true)
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val fresh = PreferenceDataStoreFactory.create(
                corruptionHandler = PrefsRecovery.replacingCorruptFile(),
                scope = scope,
            ) { copy }
            val reread = fresh.data.first()
            val pending = prefs.settingsFlow.first().pendingCustomizationThemeId

            for (id in THEMES) {
                // Every theme but the one under live edit carries its own archive. Reconstruct it
                // from the bytes and compare the whole customization, field for field.
                if (id == pending) continue
                val stored = reread[stringKeyFor(id)]
                assertTrue("$id has no persisted customization on disk", stored != null)
                assertEquals(
                    "$id did not survive the upgrade byte-for-byte",
                    before[id],
                    sceneCustomizationFromJson(JSONObject(stored!!)),
                )
            }
            // The live theme's own state is in the same file's flat keys; the app's reader folds
            // it back in, which is what the user sees after the upgrade.
            assertEquals("the theme under edit did not survive", before[pending], customizationOf(pending!!))
        } finally {
            scope.cancel()
            copy.delete()
        }

        // And nothing moved in the real store either.
        for (id in THEMES) assertEquals("$id changed across the restart", before[id], customizationOf(id))
    }

    /**
     * The property behind the "the road disappeared on beach" report, stated as persistence.
     *
     * Diagnosed against v4.2: with `cars.density` at 100% the road can only be absent if
     * `cars.visible` is false or the theme's layout carries no cars at all. The persistence defect
     * this class exists for could not cause either — it wipes a theme back to its *defaults*, and
     * `beach`'s default is cars on at 100%, so if anything it put the road back. This pins the
     * half that is this release's business: whatever the user set for `beach`'s cars survives
     * editing another theme.
     *
     * It deliberately asserts on the customization rather than on `drawRoad`, because the contract
     * that belongs here is "beach keeps what the user chose", not "the renderer draws a strip".
     */
    @Test
    fun beachKeepsItsCarSettingsWhenAnotherThemeIsEdited() = runBlocking {
        prefs.setCategoryVisible(ObjectCategory.CARS, true, "beach")
        prefs.setCategoryDensity(ObjectCategory.CARS, 1f, "beach")
        val beach = customizationOf("beach")
        assertTrue("cars were not left visible", beach.cars.visible)
        assertEquals("cars were not left at full density", 1f, beach.cars.density, 0.0001f)

        prefs.setCategoryDensity(ObjectCategory.CARS, 0.2f, "winter")
        prefs.setCategoryVisible(ObjectCategory.CARS, false, "city")

        val after = customizationOf("beach")
        assertTrue("beach lost `Show Cars`", after.cars.visible)
        assertEquals("beach lost its car density", 1f, after.cars.density, 0.0001f)
        assertEquals("beach's cars changed in any other way", beach.cars, after.cars)
        // and the two themes that were edited kept their own, different, settings
        assertEquals("winter did not keep its own density", 0.2f, customizationOf("winter").cars.density, 0.0001f)
        assertTrue("city did not keep its own visibility", !customizationOf("city").cars.visible)
    }

    // ------------------------------------------------------------------ saved themes

    @Test
    fun aBuiltInOverrideKeepsItsOwnCustomization() = runBlocking {
        customise("beach", 1)
        val beach = customizationOf("beach")
        store.setOverride("beach", entryFor("beach", "beach", "Beach", beach))
        CustomThemeRegistry.update(store.dataFlow.first())

        customise("city", 2)
        prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.93f, "winter")

        assertEquals("the override lost its look", beach, customizationOf("beach"))
        assertEquals(
            "the override is not on disk",
            beach,
            CustomThemeStore(context).dataFlow.first().overrides["beach"]?.customization,
        )
    }

    @Test
    fun aStandaloneCustomThemeKeepsItsOwnCustomization() = runBlocking {
        customise("beach", 3)
        val look = customizationOf("beach")
        store.upsertCustomTheme(entryFor(CUSTOM_ID, "beach", "My beach", look))
        CustomThemeRegistry.update(store.dataFlow.first())

        customise("winter", 4)
        assertEquals("the custom theme lost its look", look, customizationOf(CUSTOM_ID))
        assertEquals(
            "the custom theme is not on disk",
            look,
            CustomThemeStore(context).dataFlow.first().customThemes.first { it.id == CUSTOM_ID }.customization,
        )
    }

    // ------------------------------------------------------------------ reset is the only loss

    @Test
    fun resetIsTheOnlyThingThatRemovesACustomization() = runBlocking {
        customise("beach", 1)
        customise("city", 2)
        val city = customizationOf("city")

        prefs.resetAllCategories("beach")

        assertEquals("resetting beach did not reset beach", defaultCustomizationFor("beach"), customizationOf("beach"))
        assertEquals("resetting beach touched city", city, customizationOf("city"))
        assertNull(
            "beach's archive outlived its reset",
            prefs.settingsFlow.first().themeCustomizations["beach"],
        )
    }

    // ------------------------------------------------- reset reaches the theme on screen, only it

    /**
     * "Reset X to default" must reach the theme the user is looking at, and no other.
     *
     * The defect, reproduced on a device against v4.7: the flat scratch keys belong to whichever
     * theme `PENDING_CUSTOMIZATION_THEME_ID` names, and that is the last theme *edited* -- opening
     * a different theme does not move it, because `setTheme` writes `THEME_ID` alone. Every
     * per-theme setter therefore takes a `forThemeId` and calls `ensureFreshPendingTheme` first;
     * `resetCategory` was the one that took neither, so it removed the keys of whatever theme was
     * last edited. Pressing "Reset Houses to default" as the first action after switching themes
     * left the theme on screen untouched (the button appeared dead) and silently destroyed the
     * previously edited theme's houses -- permanently, once the next `ensureFreshPendingTheme`
     * archived the damaged scratch.
     *
     * The sequence below is that exact state: `city` customised, then `beach`, so the tag is on
     * `beach` while the user resets a category on `city`.
     */
    @Test
    fun resettingOneCategoryResetsTheThemeOnScreenAndNotTheLastEditedOne() = runBlocking {
        customise("city", 2)
        customise("beach", 1)
        val beach = customizationOf("beach")
        val city = customizationOf("city")
        assertNotEquals("the test customised nothing on city", defaultCustomizationFor("city"), city)
        assertNotEquals("the test customised nothing on beach", defaultCustomizationFor("beach"), beach)

        // What the Cities screen does when "Reset Houses to default" is tapped while `city` shows.
        prefs.resetCategory(ObjectCategory.HOUSES, "city")

        // The whole customization, so that resetting one category cannot quietly move another.
        assertEquals(
            "resetting houses on city did not reset city's houses",
            city.copy(houses = defaultCustomizationFor("city").houses),
            customizationOf("city"),
        )
        assertEquals("resetting city's houses reached beach instead", beach, customizationOf("beach"))
    }

    /** The same, through the Seasons screen's loop over the six decoration categories. */
    @Test
    fun resettingTheSeasonalCategoriesLeavesTheLastEditedThemeAlone() = runBlocking {
        customise("city", 2)
        customise("beach", 1)
        val beach = customizationOf("beach")
        val city = customizationOf("city")

        for (category in listOf(
            ObjectCategory.SNOWMEN, ObjectCategory.GIFTS, ObjectCategory.PENGUINS,
            ObjectCategory.BUNNIES, ObjectCategory.EASTER_EGGS, ObjectCategory.PUMPKINS,
        )) {
            prefs.resetCategory(category, "city")
        }
        prefs.resetSeasonalPalettes("city")

        assertEquals("resetting city's decorations reached beach instead", beach, customizationOf("beach"))
        // city keeps everything the reset does not name -- houses, trees, hills, lake, weather.
        val afterCity = customizationOf("city")
        assertEquals("city lost a structural category to a decorations reset", city.houses, afterCity.houses)
        assertEquals("city lost its trees to a decorations reset", city.trees, afterCity.trees)
        assertEquals("city lost its hills to a decorations reset", city.hillsColorDay, afterCity.hillsColorDay)
    }

    // ------------------------------------------------------- the night pedestrian density is state

    /**
     * `people_night_density` is per-theme scratch state and must be wiped like the rest of it.
     *
     * It was absent from `clearAllThemeCustomizationKeys`, the single wipe shared by
     * `ensureFreshPendingTheme` (theme switch), `resetAllCategories` ("reset everything") and
     * `replaceAll` (backup restore) -- so it survived all three. This pins the reset half: the
     * leftover is invisible immediately after the reset, because that also drops the tag and the
     * archive and the resolver falls back to defaults, and reappears on the next edit of the theme.
     */
    @Test
    fun resettingEverythingAlsoClearsTheNightPedestrianDensity() = runBlocking {
        customise("beach", 1)
        assertNotEquals("the test customised nothing", defaultCustomizationFor("beach"), customizationOf("beach"))

        prefs.resetAllCategories("beach")
        assertEquals("resetting beach did not reset beach", defaultCustomizationFor("beach"), customizationOf("beach"))

        // One unrelated edit re-tags the theme, which is where a surviving flat key comes back.
        prefs.setCategoryDensity(ObjectCategory.BUILDINGS, 0.31f, "beach")
        val expected = defaultCustomizationFor("beach").let {
            it.copy(buildings = it.buildings.copy(density = 0.31f))
        }
        assertEquals("a reset field came back on the next edit", expected, customizationOf("beach"))
    }

    /** And the leak half: one theme's night crowd must not follow the user into the next theme. */
    @Test
    fun theNightPedestrianDensityDoesNotLeakIntoAnotherTheme() = runBlocking {
        prefs.setPeopleNightDensity(0.2f, "beach")

        // `city` has never been customised: one unrelated edit must leave it at its own defaults
        // plus that edit.
        prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.4f, "city")

        val expected = defaultCustomizationFor("city").let {
            it.copy(houses = it.houses.copy(density = 0.4f))
        }
        assertEquals("beach's night density leaked into city", expected, customizationOf("city"))
    }

    // ------------------------------------------------------------------ the writer is complete

    /**
     * Every field of a customization survives the flat-key round trip.
     *
     * The archive/restore path writes a whole [SceneCustomization] into the flat scratch keys and
     * reads it back; a field added to the data class and forgotten in `writeFlatCustomization`
     * would silently fall back to the theme's default. Rather than reading the two lists against
     * each other, this writes a fully non-default value and asserts the reconstruction matches.
     */
    @Test
    fun everyCustomizationFieldSurvivesTheArchiveRoundTrip() = runBlocking {
        customise("beach", 1)
        // Also move the fields `customise` does not touch, so the comparison is over everything.
        prefs.setHillsVariation(0.42f, "beach")
        prefs.setStarsDensity(0.13f, "beach")
        prefs.setMoonRealisticPhases(true, "beach")
        prefs.setBirdsNight(true, "beach")
        prefs.setRainbowOpacity(0.77f, "beach")
        prefs.setSantaEnabled(true, "beach")
        val beach = customizationOf("beach")

        // Force the archive/restore cycle: leave beach, come back to it.
        prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.93f, "winter")
        prefs.setCategoryDensity(ObjectCategory.HOUSES, beach.houses.density, "beach")

        assertEquals("a field was lost archiving and restoring beach", beach, customizationOf("beach"))
    }

    private fun entryFor(id: String, sourceThemeId: String, name: String, customization: SceneCustomization) =
        CustomThemeEntry(
            id = id,
            name = name,
            theme = ThemeCatalog.byId(sourceThemeId).copy(id = id, displayName = name),
            layout = SceneObjectCatalog.layoutFor(sourceThemeId, ThemeCatalog.byId(sourceThemeId).accentColor),
            customization = customization,
        )

    private fun stringKeyFor(themeId: String) =
        androidx.datastore.preferences.core.stringPreferencesKey("$THEME_CUSTOMIZATION_KEY_PREFIX$themeId")

    private companion object {
        /** `beach` because it is the theme the defect was reported from, plus two more. */
        val THEMES = listOf("beach", "winter", "city")
        const val CUSTOM_ID = "custom:persistence-test"
    }
}
