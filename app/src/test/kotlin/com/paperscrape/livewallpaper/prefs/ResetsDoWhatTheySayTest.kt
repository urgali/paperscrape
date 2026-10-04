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
import com.paperscrape.livewallpaper.engine.palmsShown
import com.paperscrape.livewallpaper.ui.SettingsUiModel
import com.paperscrape.livewallpaper.ui.resetBuiltinToDefault
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **Each reset does what it says** (v5.10E, inventory I-213, I-214, I-215 and I-294; the maintainer's
 * *sì* of 2026-09-30 to row 10 of the v5.10A table, *«oggi … reset che azzerano meno di quello che
 * dicono … dopo ognuno fa quello che dice, e chiede prima di farlo»*, and his *«3 - si»* of 2026-10-04 to
 * I-294: the palms come back with "Reset Trees to default").
 *
 * On the app's own store, over a scratch file ([WallpaperPrefs]'s internal constructor), read back the
 * way the wallpaper and the gallery read it ([CustomThemeRegistry.resolveActiveCustomization]). The
 * questions each reset now asks are [SettingsUiModel]'s, and their words are held here against what the
 * reset takes.
 */
class ResetsDoWhatTheySayTest {

    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var prefs: WallpaperPrefs

    @Before
    fun open() {
        dir = kotlin.io.path.createTempDirectory("resets").toFile()
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

    /** Everything Seasons & decorations shows, moved away from Christmas's own. */
    private suspend fun dressChristmas() {
        prefs.setFallColorsEnabled(true, "christmas")
        prefs.setSnowPiles(0.9f, "christmas")
        prefs.setLeafPiles(0.8f, "christmas")
        prefs.setChristmasDecorationsEnabled(false, "christmas")
        prefs.setSantaEnabled(false, "christmas")
        prefs.setFlowersEnabled(true, "christmas")
        prefs.setHalloweenEnabled(true, "christmas")
        prefs.setHorrorSkyEnabled(true, "christmas")
        for (category in ObjectCategory.DECORATIONS) {
            prefs.setCategoryVisible(category, true, "christmas")
            prefs.setCategoryDensity(category, 0.77f, "christmas")
            prefs.setCategoryColorDay1(category, 0xFF123456.toInt(), "christmas")
            prefs.setCategoryColorNight2(category, 0xFF654321.toInt(), "christmas")
            prefs.setCategoryAutoMode1(category, com.paperscrape.livewallpaper.engine.AutoColorMode.FROM_DAY, "christmas")
        }
    }

    // ------------------------------------------------------------ "Reset decorations to defaults"

    @Test
    fun `the decorations reset puts every decoration back, the snow and leaf piles included`() = runBlocking {
        val factory = defaultCustomizationFor("christmas")
        dressChristmas()
        assertEquals(0.9f, customizationOf("christmas").snowPiles)
        prefs.resetDecorations("christmas")
        val after = customizationOf("christmas")
        assertEquals("the snow piles stayed where they were dragged", factory.snowPiles, after.snowPiles)
        assertEquals("the leaf piles too", factory.leafPiles, after.leafPiles)
        assertEquals(
            "anything else the screen shows",
            factory,
            after,
        )
    }

    /** I-294: the palms' switch is on the Trees page, and the decorations' reset no longer takes it. */
    @Test
    fun `the decorations reset leaves the palms, the trees and the rest of the scene`() = runBlocking {
        prefs.setPalmsEnabled(true, "christmas")
        prefs.setCategoryDensity(ObjectCategory.TREES, 0.42f, "christmas")
        prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.31f, "christmas")
        prefs.setScrollSpeed(0.6f)
        dressChristmas()
        prefs.resetDecorations("christmas")
        val after = customizationOf("christmas")
        assertTrue("the palms went with the decorations", after.palmsShown(layoutPlantsPalms = false))
        assertEquals(0.42f, after.trees.density)
        assertEquals(0.31f, after.houses.density)
        assertEquals("and a setting of every theme", 0.6f, prefs.settingsFlow.first().scrollSpeed)
    }

    @Test
    fun `the decorations reset touches the theme it is pressed on and no other`() = runBlocking {
        // Christmas dressed, then Winter edited last: the theme under live edit is Winter, and the reset
        // is pressed on Christmas -- the case `resetCategory` once got wrong (v5.8).
        dressChristmas()
        prefs.setSnowPiles(0.5f, "winter")
        prefs.setFlowersEnabled(true, "winter")
        val winter = customizationOf("winter")
        prefs.resetDecorations("christmas")
        assertEquals("the reset reached Winter", winter, customizationOf("winter"))
        assertEquals("and missed Christmas", defaultCustomizationFor("christmas"), customizationOf("christmas"))
    }

    // ------------------------------------------------------------ "Reset Trees to default" (I-294)

    @Test
    fun `Reset Trees to default brings Beach's palms back, and takes Christmas's away`() = runBlocking {
        prefs.setPalmsEnabled(false, "beach")
        assertFalse(customizationOf("beach").palmsShown(layoutPlantsPalms = true))
        prefs.resetCategory(ObjectCategory.TREES, "beach")
        assertTrue("Beach's palms are its own trees", customizationOf("beach").palmsShown(layoutPlantsPalms = true))
        assertEquals(defaultCustomizationFor("beach"), customizationOf("beach"))

        prefs.setPalmsEnabled(true, "christmas")
        assertTrue(customizationOf("christmas").palmsShown(layoutPlantsPalms = false))
        prefs.resetCategory(ObjectCategory.TREES, "christmas")
        assertFalse("off is Christmas's own", customizationOf("christmas").palmsShown(layoutPlantsPalms = false))
        assertEquals(defaultCustomizationFor("christmas"), customizationOf("christmas"))
    }

    @Test
    fun `Reset Trees leaves the decorations, and another category's reset leaves the palms`() = runBlocking {
        prefs.setPalmsEnabled(true, "autumn")
        prefs.setSnowPiles(0.6f, "autumn")
        prefs.resetCategory(ObjectCategory.TREES, "autumn")
        assertEquals("the trees' reset reached the decorations", 0.6f, customizationOf("autumn").snowPiles)
        prefs.setPalmsEnabled(true, "autumn")
        prefs.resetCategory(ObjectCategory.HOUSES, "autumn")
        assertTrue("the houses' reset took the palms", customizationOf("autumn").palmsShown(layoutPlantsPalms = false))
    }

    // ------------------------------------------------------------ "Reset this theme's scene" (I-213)

    @Test
    fun `the scene reset leaves Motion, and its question says so`() = runBlocking {
        prefs.setScrollSpeed(0.7f)
        prefs.setParallaxStrength(1.8f)
        prefs.setScrollBackground(true)
        prefs.setSwipeScroll(false)
        prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.2f, "autumn")
        prefs.resetAllCategories("autumn")
        val s = prefs.settingsFlow.first()
        assertEquals(defaultCustomizationFor("autumn"), customizationOf("autumn"))
        assertEquals(0.7f, s.scrollSpeed)
        assertEquals(1.8f, s.parallaxStrength)
        assertTrue(s.scrollBackground)
        assertFalse(s.swipeScroll)
        val question = SettingsUiModel.sceneResetMessage("Autumn")
        assertTrue("the question says Motion stays", question.contains("Motion stays as it is"))
        assertFalse("and no longer claims everything on this screen", question.contains("everything on this screen"))
    }

    // ------------------------------------------------------------ the gallery's "Reset to default" (I-215)

    private fun savedVersionOf(id: String): CustomThemeEntry {
        val theme = ThemeCatalog.byId(id)
        return CustomThemeEntry(
            id = id,
            name = "My ${theme.displayName}",
            theme = theme,
            layout = SceneObjectCatalog.layoutFor(id, theme.accentColor),
            customization = defaultCustomizationFor(id).copy(hillsVariation = 0.11f),
        )
    }

    @Test
    fun `the gallery's reset takes the saved version and the edits, and the theme looks as it ships`() = runBlocking {
        var data = CustomThemeData(overrides = mapOf("beach" to savedVersionOf("beach")))
        CustomThemeRegistry.update(data)
        prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.25f, "beach")
        assertNotEquals(defaultCustomizationFor("beach"), customizationOf("beach"))

        resetBuiltinToDefault(
            "beach",
            clearSavedVersion = { id -> data = data.copy(overrides = data.overrides - id); CustomThemeRegistry.update(data) },
            clearEdits = { prefs.resetAllCategories(it) },
        )
        assertTrue(data.overrides.isEmpty())
        assertEquals("the card and the wallpaper resolve Beach to its own", defaultCustomizationFor("beach"), customizationOf("beach"))
    }

    // ------------------------------------------------------------ the questions, against what goes

    @Test
    fun `each question names what goes and what stays`() {
        val decorations = SettingsUiModel.decorationsResetMessage("Christmas")
        for (word in listOf("palette", "snow or leaf piles", "density and colours", "other themes are not affected")) {
            assertTrue("the decorations question does not say '$word'", decorations.contains(word))
        }
        assertFalse("the palms are the Trees reset's now", decorations.contains("palm", ignoreCase = true))
        val gallery = SettingsUiModel.galleryResetMessage("Beach")
        assertTrue(gallery.contains("saved version") && gallery.contains("World & scene") && gallery.contains("Seasons & decorations"))
        assertTrue(gallery.contains("looks as it ships"))

        val (title, text) = SettingsUiModel.replaceWithCurrentQuestion("Beach", "Autumn", sameTheme = false, targetIsBuiltIn = true)
        assertEquals("both themes, in the order of the copy", "Replace Beach with Autumn?", title)
        assertTrue(text.contains("will look like Autumn") && text.contains("Reset to default"))
        val (_, own) = SettingsUiModel.replaceWithCurrentQuestion("Seaside", "Autumn", sameTheme = false, targetIsBuiltIn = false)
        assertTrue("a theme of your own cannot be brought back", own.contains("cannot be brought back"))
        val (same, _) = SettingsUiModel.replaceWithCurrentQuestion("Autumn", "Autumn", sameTheme = true, targetIsBuiltIn = true)
        assertEquals("Keep Autumn as it looks now?", same)
        // By id, not by name: a theme of your own called "Beach", replaced while the built-in Beach shows.
        val (twin, _) = SettingsUiModel.replaceWithCurrentQuestion("Beach", "Beach", sameTheme = false, targetIsBuiltIn = false)
        assertEquals("Replace Beach with Beach?", twin)
    }

    /**
     * **The screens ask first, and call what the tests above run.** The Seasons button opens its
     * question and the question resets through [WallpaperPrefs.resetDecorations]; the gallery's "Replace
     * with current" no longer writes on the tap, and its "Reset to default" goes through
     * [resetBuiltinToDefault] with both stores.
     */
    @Test
    fun `the screens ask before they reset or replace`() {
        val seasons = source("ui/SeasonsScreen.kt")
        assertTrue(seasons.contains("onClick = { confirmDecorationsReset = true },"))
        assertTrue(seasons.contains("scope.launch { prefs.resetDecorations(forThemeId) }"))
        assertFalse("the eight writes are gone", seasons.contains("prefs.resetCategory(category, forThemeId)"))

        val gallery = source("ui/ThemeGalleryScreen.kt")
        assertEquals("both card kinds ask", 2, Regex("""confirmReplace = PendingReplace\(""").findAll(gallery).count())
        val replace = gallery.substringAfter("onReplaceWithCurrent = {").substringBefore("onReset =")
        assertFalse("the built-in card writes on the tap", replace.contains("scope.launch"))
        // Each of the two questions hands the edits to clear, in its own block (both write the same line).
        val resetBlock = gallery.substringAfter("confirmReset?.let { builtinId ->").substringBefore("confirmReplace?.let { pending ->")
        val replaceBlock = gallery.substringAfter("confirmReplace?.let { pending ->").substringBefore("/** A \"Replace with current\" asked about")
        assertTrue("the reset forgets the edits", resetBlock.contains("clearEdits = { prefs.resetAllCategories(it) },"))
        assertTrue(resetBlock.contains("clearSavedVersion = { customThemeStore.clearOverride(it) },"))
        assertTrue("the replace forgets the replaced theme's edits", replaceBlock.contains("clearEdits = { prefs.resetAllCategories(it) },"))

        val world = source("ui/WorldSceneScreen.kt")
        assertTrue(world.contains("text = { Text(SettingsUiModel.sceneResetMessage(themeName)) },"))
    }

    private fun source(path: String): String {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/$path"
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = File(dir, "$prefix$suffix")
                if (f.isFile) return f.readText()
            }
            dir = dir.parentFile
        }
        error("source not found: $suffix")
    }
}
