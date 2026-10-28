package com.paperscrape.livewallpaper.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.engine.AutoColorMode
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A customization the user has made survives everything except a reset.
 *
 * A per-theme edit lives in one flat set of DataStore keys, tagged by `pending_customization_theme_id`
 * with the theme it belongs to. Every per-theme setter first archives the outgoing theme's keys under
 * that theme's own key and restores the incoming theme's (`ensureFreshPendingTheme`). A setter that
 * wiped them instead would make "customise `beach`, then move one slider on `winter`" delete
 * everything done to `beach`, from disk, silently.
 *
 * What is compared is not "a key exists in the DataStore" but the whole reconstructed
 * [SceneCustomization], field by field against what was written, for three built-in themes, a saved
 * version of a built-in theme and a saved theme of the user's own, across a theme switch, a fresh read
 * of the store and an install-over-install.
 */
@RunWith(AndroidJUnit4::class)
class ThemeCustomizationPersistenceTest {

    // Restores the real preference store around every test: these tests write the app's own
    // DataStore on purpose, and without this the phone would keep their last theme. See RealPrefsGuard.
    @get:org.junit.Rule
    val realPrefs = RealPrefsGuard()

    // And the real saved themes: [wipe] deletes every saved theme and saved version on the phone.
    @get:org.junit.Rule
    val realSavedThemes = RealPrefsGuard(CUSTOM_THEME_STORE_NAME)

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = WallpaperPrefs(context)
    private val store get() = CustomThemeStore(context)

    @Before
    fun clean() = runBlocking { wipe() }

    @After
    fun tidy() = runBlocking { wipe() }

    /**
     * Every built-in theme's edits, not only the ones these tests write: the store is the phone's own `.debug` store, and
     * an edit left there by anything else -- a theme planted for a photograph -- would read as an edit these tests had made.
     */
    private suspend fun wipe() {
        for (id in (ThemeCatalog.ALL.map { it.id } + THEMES + CUSTOM_ID).distinct()) prefs.resetAllCategories(id)
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

    // ------------------------------------------------------------------ the two resets

    /**
     * **The two reset rows of *Advanced & about*, on the real store**: each takes only what it names
     * (`DESIGN_NOTES.md` §12, decision 42). Edits made from the menus light the edits row and a saved
     * version lights the saved row; a control moved and moved back lights nothing; each reset leaves
     * the other kind, and saved new themes, alone.
     */
    @Test
    fun theTwoResetsEachTakeOnlyWhatTheyName() = runBlocking {
        customise("beach", 1)
        customise("winter", 2)
        // Touched and put back: a slider moved away from the default and returned to it.
        val autumnDefault = defaultCustomizationFor("autumn").houses.density
        prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.9f, "autumn")
        prefs.setCategoryDensity(ObjectCategory.HOUSES, autumnDefault, "autumn")
        // A saved version of Desert, as "Replace with current" makes it.
        val desert = CustomThemeEntry(
            id = "desert", name = "My desert", theme = ThemeCatalog.byId("desert"),
            layout = SceneObjectCatalog.layoutFor("desert", ThemeCatalog.byId("desert").accentColor),
            customization = defaultCustomizationFor("desert").copy(hillsVariation = 0.3f),
        )
        store.setOverride("desert", desert)
        CustomThemeRegistry.update(store.dataFlow.first())

        fun state(s: WallpaperSettings, data: com.paperscrape.livewallpaper.engine.CustomThemeData) =
            com.paperscrape.livewallpaper.ui.SettingsUiModel.themeResetState(ThemeCatalog.ALL, data.overrides, s.themeCustomizations)

        val before = state(prefs.settingsFlow.first(), store.dataFlow.first())
        assertEquals(listOf("Desert"), before.savedVersions)
        assertEquals("a slider moved back must not count as an edit", listOf("winter", "beach"), before.currentEditIds)

        // Reset the current edits: Beach and Winter go back to their defaults, Desert keeps its saved version.
        prefs.resetCustomizations(before.currentEditIds.toSet())
        assertEquals(defaultCustomizationFor("beach"), customizationOf("beach"))
        assertEquals(defaultCustomizationFor("winter"), customizationOf("winter"))
        val afterEdits = state(prefs.settingsFlow.first(), store.dataFlow.first())
        assertEquals(emptyList<String>(), afterEdits.currentEdits)
        assertEquals("the edits reset must not touch a saved version", listOf("Desert"), afterEdits.savedVersions)

        // Edits again, then the saved-versions reset: the saved version goes, the edits stay.
        customise("beach", 3)
        val beachEdited = customizationOf("beach")
        store.clearAllOverrides()
        CustomThemeRegistry.update(store.dataFlow.first())
        val afterSaved = state(prefs.settingsFlow.first(), store.dataFlow.first())
        assertEquals(emptyList<String>(), afterSaved.savedVersions)
        assertEquals("the saved reset must not touch the edits", listOf("beach"), afterSaved.currentEditIds)
        assertEquals(beachEdited, customizationOf("beach"))
    }

    // ------------------------------------------------------------------ editing another theme

    /** Customise `beach`, then move one slider on `winter`: `beach` keeps its whole look. */
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
        // A second pass, so every theme has been left and returned to.
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
     * Whatever the user set for `beach`'s cars -- shown, at full density -- survives edits to the cars
     * of two other themes, and those themes keep their own.
     *
     * It asserts on the customization rather than on `drawRoad`: the contract that belongs here is
     * "beach keeps what the user chose", not "the renderer draws a strip".
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
     * "Reset X to default" reaches the theme the user is looking at, and no other.
     *
     * The flat scratch keys belong to whichever theme `PENDING_CUSTOMIZATION_THEME_ID` names, and that
     * is the last theme *edited*: opening a different theme does not move it, because `setTheme` writes
     * `THEME_ID` alone. So `resetCategory`, like every per-theme setter, takes a `forThemeId` and calls
     * `ensureFreshPendingTheme` first. A reset without them would remove the keys of the last edited
     * theme: "Reset Houses to default" pressed first after switching themes would leave the theme on
     * screen untouched (the button would seem dead) and lose the other theme's houses, for good once
     * the next switch archived the damaged scratch.
     *
     * The sequence below is that state: `city` customised, then `beach`, so the tag is on `beach` while
     * the user resets a category on `city`.
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

    /**
     * The same, through the Seasons screen's "Reset decorations to defaults", which is one write
     * (`resetDecorations`).
     */
    @Test
    fun resettingTheSeasonalCategoriesLeavesTheLastEditedThemeAlone() = runBlocking {
        customise("city", 2)
        customise("beach", 1)
        val beach = customizationOf("beach")
        val city = customizationOf("city")

        prefs.resetDecorations("city")

        assertEquals("resetting city's decorations reached beach instead", beach, customizationOf("beach"))
        // city keeps everything the reset does not name -- houses, trees, hills, lake, weather.
        val afterCity = customizationOf("city")
        assertEquals("city lost a structural category to a decorations reset", city.houses, afterCity.houses)
        assertEquals("city lost its trees to a decorations reset", city.trees, afterCity.trees)
        assertEquals("city lost its hills to a decorations reset", city.hillsColorDay, afterCity.hillsColorDay)
    }

    // ------------------------------------------------------- the night pedestrian density is state

    /**
     * `people_night_density` is per-theme scratch state and is wiped like the rest of it, by
     * `clearAllThemeCustomizationKeys`, the one wipe shared by `ensureFreshPendingTheme` (theme switch),
     * `resetAllCategories` ("reset everything") and `replaceAll` (backup restore). This holds the reset
     * half. A key left behind would not show right after the reset, which also drops the tag and the
     * archive so the resolver falls back to defaults; it would come back on the next edit of the theme,
     * which is where this looks.
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

    /** And the leak half: one theme's night crowd does not follow the user into the next theme. */
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

    // ------------------------------------------------------------ the palms switch is state too

    /**
     * Palms off on Beach, then one unrelated edit on Desert, never touched before: Desert keeps its
     * palms. `PALMS_ENABLED` is a per-theme key that `clearAllThemeCustomizationKeys` removes; left in
     * the flat keys, Beach's "off" would become Desert's when Desert became the theme under edit.
     */
    @Test
    fun thePalmsSwitchDoesNotLeakIntoAnotherTheme() = runBlocking {
        prefs.setPalmsEnabled(false, "beach")
        assertFalse("the test turned nothing off", customizationOf("beach").palmsEnabled)

        prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.4f, "desert")

        val expected = defaultCustomizationFor("desert").let {
            it.copy(houses = it.houses.copy(density = 0.4f))
        }
        assertTrue("the test assumes Desert has palms by default", expected.palmsEnabled)
        assertEquals("Beach's palms switch leaked into Desert", expected, customizationOf("desert"))
        assertFalse("Beach lost its own choice", customizationOf("beach").palmsEnabled)
    }

    /**
     * Resetting the scene takes the palms switch with it, and it stays gone after the next edit. Right
     * after the reset a surviving key would not show -- the tag and the archive go, so the resolver
     * falls back to defaults -- so the check is made after one more edit.
     */
    @Test
    fun resettingTheSceneAlsoClearsThePalmsSwitch() = runBlocking {
        prefs.setPalmsEnabled(false, "beach")
        prefs.resetAllCategories("beach")
        assertEquals("resetting beach did not reset beach", defaultCustomizationFor("beach"), customizationOf("beach"))

        prefs.setCategoryDensity(ObjectCategory.BUILDINGS, 0.31f, "beach")
        val expected = defaultCustomizationFor("beach").let {
            it.copy(buildings = it.buildings.copy(density = 0.31f))
        }
        assertEquals("the palms came back off on the next edit", expected, customizationOf("beach"))
    }

    /**
     * **"Reset Trees to default" -- the call the Trees page makes -- turns Beach's palms back on**, and
     * "Reset decorations to defaults" leaves them alone (`DESIGN_NOTES.md` §12, decision 42): the Palms
     * switch is on the Trees page, so the reset of that page is the one that takes it.
     */
    @Test
    fun resettingTheTreesBringsThePalmsBack() = runBlocking {
        prefs.setPalmsEnabled(false, "beach")
        prefs.resetDecorations("beach")
        assertFalse("the decorations reset took the palms", customizationOf("beach").palmsEnabled)

        prefs.resetCategory(ObjectCategory.TREES, "beach")
        assertTrue("the Trees reset left the palms off", customizationOf("beach").palmsEnabled)
        assertEquals("the Trees reset left something else behind", defaultCustomizationFor("beach"), customizationOf("beach"))
    }

    // ------------------------------------------------- the automatic colour modes survive a return

    /**
     * Autumn's hills on "Day sets night", one edit on Winter, back to Autumn and one unrelated edit
     * there: the hills are still on "Day sets night". The restore from the archive writes the mode; a
     * restore without it would bring the hills back as "Both" -- MANUAL.
     */
    @Test
    fun anAutomaticColourModeSurvivesEditingAnotherThemeAndComingBack() = runBlocking {
        prefs.setHillsAutoMode(AutoColorMode.FROM_DAY, "autumn")
        assertEquals(AutoColorMode.FROM_DAY, customizationOf("autumn").hillsAutoMode)

        prefs.setStarsVisible(false, "winter")
        prefs.setStarsVisible(true, "winter")
        assertEquals("the archive itself lost the mode", AutoColorMode.FROM_DAY, customizationOf("autumn").hillsAutoMode)

        val stars = customizationOf("autumn").stars.visible
        prefs.setStarsVisible(!stars, "autumn")
        prefs.setStarsVisible(stars, "autumn")

        assertEquals("returning to Autumn lost \"Day sets night\"", AutoColorMode.FROM_DAY, customizationOf("autumn").hillsAutoMode)
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
        // The palms switch and every automatic day/night mode: both halves of each pair, on every
        // category, and the nine pairs outside the category loop. A mode left at its default here
        // could be lost by `writeFlatCustomization` without this test seeing it go.
        prefs.setPalmsEnabled(false, "beach")
        for (category in ObjectCategory.entries) {
            prefs.setCategoryAutoMode1(category, AutoColorMode.FROM_DAY, "beach")
            prefs.setCategoryAutoMode2(category, AutoColorMode.FROM_NIGHT, "beach")
        }
        prefs.setHillsAutoMode(AutoColorMode.FROM_DAY, "beach")
        prefs.setMountainAutoMode(true, AutoColorMode.FROM_NIGHT, "beach")
        prefs.setMountainAutoMode(false, AutoColorMode.FROM_DAY, "beach")
        prefs.setLakeAutoMode(AutoColorMode.FROM_NIGHT, "beach")
        prefs.setSkyAutoModeHigh(AutoColorMode.FROM_DAY, "beach")
        prefs.setSkyAutoModeLow(AutoColorMode.FROM_NIGHT, "beach")
        prefs.setCloudsAutoMode(AutoColorMode.FROM_DAY, "beach")
        prefs.setPrecipitationRainAutoMode(AutoColorMode.FROM_NIGHT, "beach")
        prefs.setPrecipitationSnowAutoMode(AutoColorMode.FROM_DAY, "beach")
        // The seasonal fields, each off its default: the three *Christmas lights* switches, the Halloween switches the
        // decorations' rule does not lock, the scarecrows; the Halloween decorations themselves on another theme below,
        // as they and the Christmas ones exclude each other (`DESIGN_NOTES.md` §12, decision 77).
        prefs.setChristmasBuildingLightsEnabled(true, "beach")
        prefs.setChristmasTreeLightsEnabled(true, "beach")
        prefs.setChristmasFirsEnabled(true, "beach")
        prefs.setLaughingMoonEnabled(true, "beach")
        prefs.setNightSkyByDayEnabled(true, "beach")
        prefs.setStillMoonEnabled(true, "beach")
        prefs.setBloodRainEnabled(true, "beach")
        prefs.setHeadlessHorsemanEnabled(true, "beach")
        prefs.setCategoryVisible(ObjectCategory.SCARECROWS, true, "beach")
        prefs.setCategoryDensity(ObjectCategory.SCARECROWS, 0.31f, "beach")
        val beach = customizationOf("beach")
        // Not vacuous: the values being compared really are the non-default ones.
        assertFalse("the palms switch was never set", beach.palmsEnabled)
        assertEquals("the hills' mode was never set", AutoColorMode.FROM_DAY, beach.hillsAutoMode)
        assertEquals("a category's mode was never set", AutoColorMode.FROM_NIGHT, beach.houses.autoMode2)
        assertEquals("the snow's mode was never set", AutoColorMode.FROM_DAY, beach.precipitation.snowAutoMode)
        assertTrue("v5.16's switches were never set", beach.christmasBuildingLightsEnabled && beach.christmasTreeLightsEnabled && beach.christmasFirsEnabled &&
            beach.laughingMoonEnabled && beach.nightSkyByDayEnabled && beach.bloodRainEnabled && beach.headlessHorsemanEnabled &&
            beach.stillMoonEnabled)
        assertEquals("the scarecrows were never set", 0.31f, beach.scarecrows.density, 0f)
        assertTrue(beach.scarecrows.visible)

        // Force the archive/restore cycle: leave beach, come back to it.
        prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.93f, "winter")
        prefs.setCategoryDensity(ObjectCategory.HOUSES, beach.houses.density, "beach")

        assertEquals("a field was lost archiving and restoring beach", beach, customizationOf("beach"))

        // The other half of the Palms switch, which a palm theme never moves off its
        // default: palms in the trees' places on a theme that plants none, through the same cycle.
        prefs.setPalmsEnabled(true, "city")
        val city = customizationOf("city")
        assertTrue("the palms in place of the trees were never set", city.palmsInsteadOfTrees)
        prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.91f, "winter")
        prefs.setCategoryDensity(ObjectCategory.HOUSES, city.houses.density, "city")
        assertEquals("a field was lost archiving and restoring city", city, customizationOf("city"))

        // The Halloween decorations, on a theme where no Christmas switch is on, through the same cycle.
        prefs.setHalloweenDecorationsEnabled(true, "spring")
        val spring = customizationOf("spring")
        assertTrue("the Halloween decorations were never set", spring.halloweenDecorationsEnabled)
        prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.89f, "winter")
        prefs.setCategoryDensity(ObjectCategory.HOUSES, spring.houses.density, "spring")
        assertEquals("a field was lost archiving and restoring spring", spring, customizationOf("spring"))
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
        /** Three built-in themes, each customised differently and compared in turn. */
        val THEMES = listOf("beach", "winter", "city")
        const val CUSTOM_ID = "custom:persistence-test"
    }
}
