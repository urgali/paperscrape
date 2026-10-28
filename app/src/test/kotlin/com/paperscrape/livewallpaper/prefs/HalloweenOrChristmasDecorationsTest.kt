package com.paperscrape.livewallpaper.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.CustomThemeRegistry
import com.paperscrape.livewallpaper.engine.DecorationExclusion
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.SceneObjectType
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.engine.customThemeEntryFromJson
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import com.paperscrape.livewallpaper.engine.sceneCustomizationFromJson
import com.paperscrape.livewallpaper.engine.toJson
import com.paperscrape.livewallpaper.engine.withChristmasLights
import com.paperscrape.livewallpaper.ui.SettingsUiModel
import com.paperscrape.livewallpaper.ui.snapshotEntry
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
 * **The Halloween decorations and the Christmas ones are never on together in a theme, and the first turned on wins**
 * (decision 77, 2026-10-21: *«addobbi visibili: solo halloween o natale; vince il primo scelto e il secondo deve
 * avere il toggle non attivabile con errore "è già acceso natale" o "è già acceso halloween"»*).
 *
 * In every order and on every way in: Christmas first, Halloween first, per theme, after a change of theme, from a theme
 * file, a backup and a saved theme that hold both -- which the app never writes, and a file written by hand can --, on
 * the app's own store over a scratch file and the readers the app reads with. And the switches as the settings draw them:
 * the side that is off greyed, with the line that says the other's are on.
 */
class HalloweenOrChristmasDecorationsTest {

    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var prefs: WallpaperPrefs

    @Before
    fun open() {
        CustomThemeRegistry.update(CustomThemeData.EMPTY)
        dir = kotlin.io.path.createTempDirectory("decorations").toFile()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "prefs.preferences_pb") }
        prefs = WallpaperPrefs(store)
    }

    @After
    fun close() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private suspend fun of(themeId: String): SceneCustomization {
        val s = prefs.settingsFlow.first()
        return CustomThemeRegistry.resolveActiveCustomization(themeId, s.pendingCustomization, s.pendingCustomizationThemeId, s.themeCustomizations)
    }

    private fun christmas(c: SceneCustomization) = listOf(c.christmasBuildingLightsEnabled, c.christmasTreeLightsEnabled, c.christmasFirsEnabled)

    // ------------------------------------------------------------------ the rule, pure

    @Test
    fun `either side can go on while the other is off, and neither while the other is on`() {
        val none = defaultCustomizationFor("autumn")
        assertTrue(DecorationExclusion.halloweenAllowed(none))
        assertTrue(DecorationExclusion.christmasAllowed(none))
        for (one in listOf(
            none.copy(christmasBuildingLightsEnabled = true),
            none.copy(christmasTreeLightsEnabled = true),
            none.copy(christmasFirsEnabled = true),
        )) {
            assertTrue(DecorationExclusion.christmasOn(one))
            assertFalse("any of the three Christmas switches keeps the Halloween ones off", DecorationExclusion.halloweenAllowed(one))
            assertTrue("and the other Christmas ones free", DecorationExclusion.christmasAllowed(one))
        }
        val halloween = none.copy(halloweenDecorationsEnabled = true)
        assertFalse(DecorationExclusion.christmasAllowed(halloween))
        assertTrue(DecorationExclusion.halloweenAllowed(halloween))
    }

    @Test
    fun `data that holds both keeps the side its theme ships, and Christmas elsewhere`() {
        val both = { id: String -> defaultCustomizationFor(id).withChristmasLights(true).copy(halloweenDecorationsEnabled = true) }
        val halloween = DecorationExclusion.resolved(both("halloween"), defaultCustomizationFor("halloween"))
        assertTrue(halloween.halloweenDecorationsEnabled)
        assertEquals(listOf(false, false, false), christmas(halloween))
        for (theme in ThemeCatalog.ALL.filter { it.id != "halloween" }) {
            val c = DecorationExclusion.resolved(both(theme.id), defaultCustomizationFor(theme.id))
            assertFalse(theme.id, c.halloweenDecorationsEnabled)
            assertEquals(theme.id, listOf(true, true, true), christmas(c))
        }
        // One side alone is read as it is.
        val one = defaultCustomizationFor("autumn").copy(christmasTreeLightsEnabled = true)
        assertEquals(one, DecorationExclusion.resolved(one, defaultCustomizationFor("autumn")))
    }

    // ------------------------------------------------------------------ the store, in every order

    @Test
    fun `Christmas first, then Halloween refused, until Christmas is off -- and then the other way round`() = runBlocking {
        prefs.setChristmasBuildingLightsEnabled(true, "autumn")
        prefs.setHalloweenDecorationsEnabled(true, "autumn")
        assertFalse("refused: the lights were first", of("autumn").halloweenDecorationsEnabled)
        assertTrue(of("autumn").christmasBuildingLightsEnabled)
        prefs.setChristmasTreeLightsEnabled(true, "autumn")
        prefs.setChristmasBuildingLightsEnabled(false, "autumn")
        prefs.setHalloweenDecorationsEnabled(true, "autumn")
        assertFalse("the trees' lights are still on", of("autumn").halloweenDecorationsEnabled)
        prefs.setChristmasTreeLightsEnabled(false, "autumn")
        prefs.setHalloweenDecorationsEnabled(true, "autumn")
        assertTrue("nothing of Christmas on: they go on", of("autumn").halloweenDecorationsEnabled)
        for (set in listOf(prefs::setChristmasBuildingLightsEnabled, prefs::setChristmasTreeLightsEnabled, prefs::setChristmasFirsEnabled)) {
            set(true, "autumn")
        }
        assertEquals("each refused: Halloween's were first now", listOf(false, false, false), christmas(of("autumn")))
        // Turning one off is never refused.
        prefs.setHalloweenDecorationsEnabled(false, "autumn")
        assertFalse(of("autumn").halloweenDecorationsEnabled)
        prefs.setChristmasFirsEnabled(true, "autumn")
        assertTrue(of("autumn").christmasFirsEnabled)
    }

    /**
     * **Each of the three moves only itself**: a store without the two newer keys reads them from the first's, the one
     * *Christmas lights* has, so a writer of that key alone would move all three -- on a fresh install of Christmas, *Lights
     * on the buildings* turned off would take the trees' lights and the firs with it; on Autumn turned on it would light
     * them. Every switch, both ways, on a fresh store and on one holding only the single key -- its *Christmas lights* key
     * on for Christmas, off for Autumn. And where it matters, the Halloween theme with a store holding only *Christmas
     * lights*, on there: the last Christmas switch turned off does not turn the Halloween decorations on by themselves.
     */
    @Test
    fun `each Christmas switch moves only itself, on a fresh store and on one as the 5_15 left it`() = runBlocking {
        val setters = listOf(prefs::setChristmasBuildingLightsEnabled, prefs::setChristmasTreeLightsEnabled, prefs::setChristmasFirsEnabled)
        val lights = booleanPreferencesKey("christmas_decorations_enabled")
        val pending = stringPreferencesKey("pending_customization_theme_id")
        val theme = stringPreferencesKey("theme_id")
        for (written515 in listOf(false, true)) for ((themeId, start) in listOf("christmas" to true, "autumn" to false)) {
            for (i in setters.indices) {
                store.edit { p ->
                    p.clear()
                    if (written515) { p[theme] = themeId; p[pending] = themeId; p[lights] = start }
                }
                assertEquals("$themeId before", listOf(start, start, start), christmas(of(themeId)))
                setters[i](!start, themeId)
                val want = MutableList(3) { start }.also { it[i] = !start }
                assertEquals("$themeId${if (written515) ", a store of 5.15" else ""}: switch $i turned ${!start}", want, christmas(of(themeId)))
                assertEquals("$themeId: the Halloween decorations untouched", defaultCustomizationFor(themeId).halloweenDecorationsEnabled, of(themeId).halloweenDecorationsEnabled)
            }
        }
        // And the last Christmas switch turned off does not turn the Halloween decorations on by themselves. Where it can:
        // the Halloween theme, whose decorations without a key of their own read as the theme ships them, on -- but off
        // where the Christmas ones are on, as a store holding only *Christmas lights*, on there, has them. Each Christmas
        // setter writes the decorations' key as it reads then (`WallpaperPrefs.setChristmasDecoration`), so it stays off.
        store.edit { p -> p.clear(); p[theme] = "halloween"; p[pending] = "halloween"; p[lights] = true }
        assertEquals("the 5.15 store's lights on Halloween", listOf(true, true, true), christmas(of("halloween")))
        assertFalse("and its decorations off", of("halloween").halloweenDecorationsEnabled)
        for (i in setters.indices) setters[i](false, "halloween")
        assertEquals(listOf(false, false, false), christmas(of("halloween")))
        assertFalse("the Halloween decorations stay off until turned on", of("halloween").halloweenDecorationsEnabled)
    }

    /**
     * **A refused tap on another theme leaves both themes' looks where they were**: the scratch space becomes the tapped
     * theme's before the refusal, and is marked so; left marked as the theme edited before, it would read the tapped
     * theme's look, and the next edit of a third theme would archive that over it.
     */
    @Test
    fun `a refused tap on another theme moves no theme's look`() = runBlocking {
        prefs.setSantaEnabled(true, "autumn")
        assertTrue(of("autumn").santaEnabled)
        prefs.setChristmasFirsEnabled(true, "halloween")
        prefs.setHalloweenDecorationsEnabled(true, "christmas")
        assertTrue("Autumn keeps its Santa", of("autumn").santaEnabled)
        assertEquals("Halloween keeps its own", listOf(false, false, false), christmas(of("halloween")))
        assertTrue(of("halloween").halloweenDecorationsEnabled)
        assertFalse(of("christmas").halloweenDecorationsEnabled)
        prefs.setSantaEnabled(true, "winter")
        assertTrue("and after a third theme's edit too", of("autumn").santaEnabled)
        assertEquals(listOf(false, false, false), christmas(of("halloween")))
        assertTrue(of("halloween").halloweenDecorationsEnabled)
        assertEquals(listOf(true, true, true), christmas(of("christmas")))
    }

    @Test
    fun `each theme as it ships has one side on and refuses the other, and the rule is per theme`() = runBlocking {
        // Halloween ships its decorations on: no Christmas switch goes on there.
        prefs.setChristmasFirsEnabled(true, "halloween")
        prefs.setChristmasBuildingLightsEnabled(true, "halloween")
        assertEquals(listOf(false, false, false), christmas(of("halloween")))
        assertTrue(of("halloween").halloweenDecorationsEnabled)
        // Christmas ships its lights on: the Halloween decorations do not go on there.
        prefs.setHalloweenDecorationsEnabled(true, "christmas")
        assertFalse(of("christmas").halloweenDecorationsEnabled)
        assertEquals(listOf(true, true, true), christmas(of("christmas")))
        // Moving between themes takes nothing across: each theme keeps its own side.
        prefs.setTheme("halloween")
        prefs.setChristmasTreeLightsEnabled(true, "halloween")
        prefs.setTheme("christmas")
        prefs.setHalloweenDecorationsEnabled(true, "christmas")
        assertEquals(listOf(false, false, false), christmas(of("halloween")))
        assertFalse(of("christmas").halloweenDecorationsEnabled)
        // And Autumn, where neither is on, takes either.
        prefs.setHalloweenDecorationsEnabled(true, "autumn")
        assertTrue(of("autumn").halloweenDecorationsEnabled)
        assertTrue("Halloween's and Christmas's own untouched", of("halloween").halloweenDecorationsEnabled && of("christmas").christmasFirsEnabled)
    }

    @Test
    fun `a theme file, a backup and a saved theme that hold both read with one side off`() {
        for (id in listOf("halloween", "autumn", "christmas")) {
            val both = defaultCustomizationFor(id).withChristmasLights(true).copy(halloweenDecorationsEnabled = true)
            val json = both.toJson()
            val read = sceneCustomizationFromJson(json, ThemeCatalog.byId(id))
            val halloweenWins = id == "halloween"
            assertEquals(id, halloweenWins, read.halloweenDecorationsEnabled)
            assertEquals(id, List(3) { !halloweenWins }, christmas(read))
        }
        // Without a theme to read against (a payload whose theme is unknown): Christmas, the side kept on every theme but Halloween.
        val unknown = sceneCustomizationFromJson(SceneCustomization.DEFAULT.withChristmasLights(true).copy(halloweenDecorationsEnabled = true).toJson())
        assertFalse(unknown.halloweenDecorationsEnabled)
        // A backup: its theme customizations go through the same reader.
        val backup = AppBackup.from(
            WallpaperSettings(themeId = "halloween"),
            CustomThemeData.EMPTY,
            "5.16",
            1_761_040_000_000L,
        ).toJsonString().let { org.json.JSONObject(it) }
        backup.getJSONObject("themeCustomizations").put(
            "halloween",
            defaultCustomizationFor("halloween").withChristmasLights(true).copy(halloweenDecorationsEnabled = true).toJson(),
        )
        val restored = (parseAppBackup(backup.toString()) as BackupParseResult.Ok).backup.themeCustomizations.getValue("halloween")
        assertTrue(restored.halloweenDecorationsEnabled)
        assertEquals(listOf(false, false, false), christmas(restored))
        // A saved theme of the user's own, from Christmas, holding both: not the Halloween theme, so Christmas stays.
        val entry = org.json.JSONObject().apply {
            put("id", "custom:9")
            put("name", "Both")
            put("theme", ThemeCatalog.byId("christmas").copy(id = "custom:9", displayName = "Both").toJson())
            put("layout", com.paperscrape.livewallpaper.engine.SceneObjectCatalog.layoutFor("christmas", 0).toJson())
            put("customization", defaultCustomizationFor("christmas").copy(halloweenDecorationsEnabled = true).toJson())
        }
        val saved = customThemeEntryFromJson(entry).customization
        assertFalse(saved.halloweenDecorationsEnabled)
        assertEquals(listOf(true, true, true), christmas(saved))
    }

    @Test
    fun `a backup restored into the store reads one side on, and the settings grey the other`() = runBlocking {
        val both = defaultCustomizationFor("autumn").copy(christmasTreeLightsEnabled = true, halloweenDecorationsEnabled = true)
        val settings = (parseAppBackup(AppBackup.from(WallpaperSettings(themeId = "autumn"), CustomThemeData.EMPTY, "5.16", 1_761_040_000_000L).toJsonString())
            as BackupParseResult.Ok).backup.settings
        // Written as a restore writes it, both on: the store's reader is what resolves it.
        prefs.replaceAll(settings, mapOf("autumn" to both))
        val autumn = of("autumn")
        assertFalse("read with the Christmas side, Autumn shipping neither", autumn.halloweenDecorationsEnabled)
        assertTrue(autumn.christmasTreeLightsEnabled)
        val halloweenRow = SettingsUiModel.halloweenDecorations(autumn)
        assertFalse(halloweenRow.shownOn)
        assertFalse("greyed", halloweenRow.interactive)
        assertTrue(halloweenRow.blockedByTheOther)
        val treesRow = SettingsUiModel.christmasDecoration(autumn.christmasTreeLightsEnabled, autumn)
        assertTrue(treesRow.shownOn && treesRow.interactive && !treesRow.blockedByTheOther)
        val firsRow = SettingsUiModel.christmasDecoration(autumn.christmasFirsEnabled, autumn)
        assertTrue("another Christmas switch stays free", firsRow.interactive)
        // The other way round, on Halloween.
        val h = defaultCustomizationFor("halloween")
        for (stored in christmas(h)) {
            val row = SettingsUiModel.christmasDecoration(stored, h)
            assertFalse(row.interactive)
            assertTrue(row.blockedByTheOther)
        }
        assertEquals("Christmas decorations are already on. Turn them off in Christmas to hang these.", SettingsUiModel.CHRISTMAS_ALREADY_ON_LINE)
        assertEquals("Halloween decorations are already on. Turn them off in Halloween to use this.", SettingsUiModel.HALLOWEEN_ALREADY_ON_LINE)
    }

    /**
     * **With no tree standing, the trees' two Christmas switches read off and locked, and hold nothing** (decisions 40 and
     * 76: greyed with their line, as the palms' switch is): with *Show Trees* off or at 0 %,
     * *Lights on the trees* and *Christmas firs* read off, take no tap and say what is missing, the stored choice untouched
     * and back with the trees; *Lights on the buildings* is not the trees' and stays as it is. And a switch that reads off
     * does not hold the Halloween decorations off: they go on beside it, on the store as in the rule. The Seasons screen
     * draws the two rows, and counts them on its Christmas row, from that one rule.
     */
    @Test
    fun `with no tree standing the trees' two Christmas switches read off and locked, and hold nothing`() = runBlocking {
        val christmasTheme = defaultCustomizationFor("christmas")
        assertTrue("the Christmas theme ships its trees and the three on", DecorationExclusion.treesStand(christmasTheme) && christmas(christmasTheme).all { it })
        for (noTrees in listOf(
            christmasTheme.copy(trees = christmasTheme.trees.copy(visible = false)),
            christmasTheme.copy(trees = christmasTheme.trees.copy(density = 0f)),
        )) {
            assertFalse(DecorationExclusion.treesStand(noTrees))
            for (stored in listOf(noTrees.christmasTreeLightsEnabled, noTrees.christmasFirsEnabled)) {
                val row = SettingsUiModel.christmasTreeDecoration(stored, noTrees)
                assertFalse("reads off", row.shownOn)
                assertFalse("takes no tap", row.interactive)
                assertTrue("says what is missing", row.needsSomethingToHangOn && row.needsLine == SettingsUiModel.NEEDS_TREES_LINE)
                assertFalse(row.blockedByTheOther)
            }
            val buildings = SettingsUiModel.christmasBuildingDecoration(noTrees.christmasBuildingLightsEnabled, noTrees)
            assertTrue("the buildings' lights are not the trees'", buildings.shownOn && buildings.interactive && !buildings.needsSomethingToHangOn)
            // Only the trees' two on, with no tree: nothing of Christmas is on, so the Halloween decorations are free.
            val treesOnly = noTrees.copy(christmasBuildingLightsEnabled = false)
            assertFalse(DecorationExclusion.christmasOn(treesOnly))
            assertTrue(DecorationExclusion.halloweenAllowed(treesOnly))
            assertTrue(SettingsUiModel.halloweenDecorations(treesOnly).interactive)
        }
        // With the trees back the stored choice is back: the rows read it as before.
        val row = SettingsUiModel.christmasTreeDecoration(christmasTheme.christmasFirsEnabled, christmasTheme)
        assertTrue(row.shownOn && row.interactive && !row.needsSomethingToHangOn && row.needsLine == null)
        assertEquals("Needs Show Trees on in World & scene. Your choice is kept until then.", SettingsUiModel.NEEDS_TREES_LINE)

        // On the store: Autumn with its trees off and *Christmas firs* stored on -- the Halloween decorations go on.
        store.edit { it.clear() }
        prefs.setChristmasFirsEnabled(true, "autumn")
        assertTrue(of("autumn").christmasFirsEnabled)
        prefs.setHalloweenDecorationsEnabled(true, "autumn")
        assertFalse("refused while the firs can stand", of("autumn").halloweenDecorationsEnabled)
        store.edit { it[booleanPreferencesKey("obj_TREES_visible")] = false }
        assertFalse(DecorationExclusion.treesStand(of("autumn")))
        prefs.setHalloweenDecorationsEnabled(true, "autumn")
        val autumn = of("autumn")
        assertTrue("allowed with no tree to carry the firs", autumn.halloweenDecorationsEnabled)
        assertTrue("the firs' choice kept", autumn.christmasFirsEnabled)

        // The screen draws the two rows, and counts them, from the rule: its every line that asks it, whole, the Christmas
        // row's count and the switch's state, and the switch row shows what the state says.
        val screen = seasonsScreenLines()
        for ((rule, field, title) in listOf(
            Triple("christmasTreeDecoration", "christmasTreeLightsEnabled", "Lights on the trees"),
            Triple("christmasFirs", "christmasFirsEnabled", "Christmas firs"),
        )) {
            assertEquals(
                "both uses of $field go through its rule",
                listOf(
                    "\"$title\" to SettingsUiModel.$rule(customization.$field, customization, themeLayout).shownOn,",
                    "state = SettingsUiModel.$rule(customization.$field, customization, themeLayout),",
                ),
                screen.filter { "SettingsUiModel.$rule(" in it },
            )
        }
        for (line in listOf("state.needsLine != null -> state.needsLine", "checked = state.shownOn,", "enabled = state.interactive,")) {
            assertTrue(line, screen.contains(line))
        }
    }

    /**
     * **With no house or building standing, the buildings' lights and the Halloween decorations read off and locked, and
     * hold nothing** (decisions 40, 76 and 83: greyed with their line, as the firs' switch is):
     * with the houses and *Show Buildings* off -- the three shops are *Show Buildings*' -- *Halloween decorations* reads off,
     * takes no tap and says what to turn on, and so does *Lights on the buildings* while no house on the mountains stands
     * either (their three bulbs are its); the stored choice untouched and back with a house. And one that reads off holds the
     * other side off neither. The Seasons screen draws the two rows, and counts them, from that rule.
     */
    @Test
    fun `with no house or building standing the buildings' lights and the Halloween decorations read off and locked`() = runBlocking {
        val halloween = defaultCustomizationFor("halloween")
        val christmasTheme = defaultCustomizationFor("christmas")
        assertTrue(DecorationExclusion.halloweenDecorationsHang(halloween) && DecorationExclusion.buildingLightsHang(christmasTheme))
        fun empty(c: SceneCustomization) = c.copy(houses = c.houses.copy(visible = false), buildings = c.buildings.copy(visible = false))
        for (noHouses in listOf(empty(halloween), empty(halloween).copy(houses = halloween.houses.copy(density = 0f)))) {
            assertFalse(DecorationExclusion.halloweenDecorationsHang(noHouses))
            val row = SettingsUiModel.halloweenDecorations(noHouses)
            assertTrue("stored on", noHouses.halloweenDecorationsEnabled)
            assertFalse("reads off", row.shownOn)
            assertFalse("takes no tap", row.interactive)
            assertEquals(SettingsUiModel.NEEDS_HOUSES_LINE, row.needsLine)
            assertFalse("holds nothing", DecorationExclusion.halloweenOn(noHouses))
            assertTrue(DecorationExclusion.christmasAllowed(noHouses))
        }
        // The towers at 0 % leave the shops standing: the decorations have the shops.
        val shopsOnly = empty(halloween).copy(buildings = halloween.buildings.copy(visible = true, density = 0f))
        assertTrue(SettingsUiModel.halloweenDecorations(shopsOnly).shownOn)
        // The buildings' lights: none standing, and no house on the mountains either.
        val noBuildings = empty(christmasTheme).copy(distantHouses = christmasTheme.distantHouses.copy(visible = false))
        val lights = SettingsUiModel.christmasBuildingDecoration(noBuildings.christmasBuildingLightsEnabled, noBuildings)
        assertFalse(lights.shownOn || lights.interactive)
        assertEquals(SettingsUiModel.NEEDS_BUILDINGS_LINE, lights.needsLine)
        val lightsOnly = noBuildings.copy(christmasTreeLightsEnabled = false, christmasFirsEnabled = false)
        assertFalse("holds nothing", DecorationExclusion.christmasOn(lightsOnly))
        // A house on the mountains carries three bulbs: then the lights read as stored.
        val onTheMountains = noBuildings.copy(
            distantHouses = noBuildings.distantHouses.copy(visible = true, density = 0.5f),
            mountainsFront = noBuildings.mountainsFront.copy(visible = true, density = 1f),
        )
        assertTrue(DecorationExclusion.buildingLightsHang(onTheMountains))
        assertTrue(SettingsUiModel.christmasBuildingDecoration(true, onTheMountains).shownOn)
        assertEquals("Needs the houses or the buildings on in World & scene. Your choice is kept until then.", SettingsUiModel.NEEDS_HOUSES_LINE)
        assertEquals("Needs the houses, the buildings or the distant houses on in World & scene. Your choice is kept until then.", SettingsUiModel.NEEDS_BUILDINGS_LINE)

        // On the store: Autumn with its houses and buildings off and the buildings' lights stored on -- the Halloween
        // decorations are not held by them, and go on as soon as there is a house.
        store.edit { it.clear() }
        prefs.setChristmasBuildingLightsEnabled(true, "autumn")
        store.edit {
            it[booleanPreferencesKey("obj_HOUSES_visible")] = false
            it[booleanPreferencesKey("obj_BUILDINGS_visible")] = false
        }
        assertFalse(DecorationExclusion.christmasOn(of("autumn")))
        assertTrue(of("autumn").christmasBuildingLightsEnabled)

        // The screen draws the two rows, and counts them, from their rules: its every line that asks one, whole.
        val screen = seasonsScreenLines()
        assertEquals(
            "both uses of the buildings' lights go through their rule",
            listOf(
                "\"Lights on the buildings\" to SettingsUiModel.christmasBuildingDecoration(" +
                    "customization.christmasBuildingLightsEnabled, customization, themeLayout).shownOn,",
                "state = SettingsUiModel.christmasBuildingDecoration(customization.christmasBuildingLightsEnabled, customization, themeLayout),",
            ),
            screen.filter { "SettingsUiModel.christmasBuildingDecoration(" in it },
        )
        assertEquals(
            "both uses of the Halloween decorations go through theirs",
            listOf(
                "\"Halloween decorations\" to SettingsUiModel.halloweenDecorations(customization, themeLayout).shownOn,",
                "val decorations = SettingsUiModel.halloweenDecorations(customization, themeLayout)",
            ),
            screen.filter { "SettingsUiModel.halloweenDecorations(" in it },
        )
        for (line in listOf(
            "decorations.needsLine != null -> decorations.needsLine",
            "checked = decorations.shownOn,",
            "enabled = decorations.interactive,",
        )) {
            assertTrue(line, screen.contains(line))
        }
    }

    /**
     * **A switch the settings take reads back on, on a saved street with no place among the houses** (decisions 40 and
     * 77): the Halloween theme saved over with *Show Buildings* off keeps no tower and no place (`snapshotEntry`); then
     * *Reset Buildings to default* turns *Show Buildings* back on, the houses go off and the houses on the mountains on.
     * The Halloween decorations, stored on, hang on nothing there, so *Lights on the buildings* takes the tap, and the
     * store's reader keeps it on: the side that hangs on the street. Never both on, or the gallery card would draw the two
     * on its shops. With the houses back both hang, the Halloween theme's side stays, a switch that can be turned off, and
     * the lights wait greyed with their reason.
     */
    @Test
    fun `on a saved street with no place, Lights on the buildings taken beside decorations that hang on nothing reads on`() = runBlocking {
        val halloween = defaultCustomizationFor("halloween")
        val entry = snapshotEntry("halloween", "Halloween", "halloween", halloween.copy(buildings = halloween.buildings.copy(visible = false)), "halloween")
        CustomThemeRegistry.update(CustomThemeData(overrides = mapOf("halloween" to entry)))
        try {
            val street = DecorationExclusion.streetOf("halloween")
            assertTrue("the saved street keeps its houses", street.staticObjects.any { it.type == SceneObjectType.HOUSE })
            assertFalse("and no tower or place", street.staticObjects.any { it.type == SceneObjectType.SKYSCRAPER })
            prefs.resetCategory(ObjectCategory.BUILDINGS, "halloween")
            prefs.setCategoryVisible(ObjectCategory.HOUSES, false, "halloween")
            prefs.setDistantHousesDensity(0.5f, "halloween")
            prefs.setDistantHousesVisible(true, "halloween")
            prefs.setMountainDensity(true, 1f, "halloween")
            prefs.setMountainVisible(true, true, "halloween")
            val before = of("halloween")
            assertTrue("Show Buildings back on", before.buildings.visible)
            assertTrue("the decorations stored on", before.halloweenDecorationsEnabled)
            val free = SettingsUiModel.christmasBuildingDecoration(before.christmasBuildingLightsEnabled, before, street)
            assertTrue("Lights on the buildings takes a tap", free.interactive && !free.shownOn && !free.blockedByTheOther)

            prefs.setChristmasBuildingLightsEnabled(true, "halloween")
            val after = of("halloween")
            assertTrue("the tap reads back on", after.christmasBuildingLightsEnabled)
            assertTrue(SettingsUiModel.christmasBuildingDecoration(after.christmasBuildingLightsEnabled, after, street).shownOn)
            assertFalse("never both on: the card would draw the two on its shops", after.halloweenDecorationsEnabled)
            val decorations = SettingsUiModel.halloweenDecorations(after, street)
            assertFalse(decorations.shownOn || decorations.interactive)
            assertEquals("Needs the houses on in World & scene: this theme has no shops. Your choice is kept until then.", decorations.needsLine)

            prefs.setCategoryVisible(ObjectCategory.HOUSES, true, "halloween")
            val both = of("halloween")
            assertTrue("the Halloween theme's side", both.halloweenDecorationsEnabled)
            assertFalse(both.christmasBuildingLightsEnabled)
            val decorationsBack = SettingsUiModel.halloweenDecorations(both, street)
            assertTrue("on, and free to go off", decorationsBack.shownOn && decorationsBack.interactive)
            assertTrue(SettingsUiModel.christmasBuildingDecoration(both.christmasBuildingLightsEnabled, both, street).blockedByTheOther)
        } finally {
            CustomThemeRegistry.update(CustomThemeData.EMPTY)
        }
    }

    /** `ui/SeasonsScreen.kt`'s lines, trimmed, without comments: a line comment cut off, a KDoc or block comment line dropped. */
    private fun seasonsScreenLines(): List<String> {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = File(dir, "${prefix}src/main/kotlin/com/paperscrape/livewallpaper/ui/SeasonsScreen.kt")
                if (f.isFile) {
                    return f.readLines().map { it.trim() }
                        .filterNot { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }
                        .map { it.substringBefore(" //").trim() }
                        .filter { it.isNotEmpty() }
                }
            }
            dir = dir.parentFile
        }
        throw AssertionError("SeasonsScreen.kt not found")
    }
}
