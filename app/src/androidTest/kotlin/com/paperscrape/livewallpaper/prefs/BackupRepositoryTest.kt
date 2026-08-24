package com.paperscrape.livewallpaper.prefs

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.engine.CustomThemeEntry
import com.paperscrape.livewallpaper.engine.CustomThemeRegistry
import com.paperscrape.livewallpaper.engine.SceneObjectCatalog
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Backup export and import against the real stores.
 *
 * The format itself is covered on the JVM by `BackupAndThemeShareTest`; what needs a device is the
 * part that touches two DataStores — that an export reads the real state, that an import replaces
 * it whole, and above all that **a refused import changes nothing at all**.
 *
 * No assertion in this file prints an API key.
 */
@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = WallpaperPrefs(context)
    private val store get() = CustomThemeStore(context)
    private fun repo() = BackupRepository(prefs, store, "test")

    @Before fun clean() = runBlocking { wipe() }
    @After fun tidy() = runBlocking { wipe() }

    private suspend fun wipe() {
        for (id in listOf("beach", "winter", "city", "sunset")) prefs.resetAllCategories(id)
        store.replaceAll(com.paperscrape.livewallpaper.engine.CustomThemeData.EMPTY)
        prefs.setLiveWeatherApiKey("")
        prefs.setCustomLocation(45.4642f, 9.19f, "")
        CustomThemeRegistry.update(store.dataFlow.first())
    }

    private fun entry(id: String, source: String, name: String) = CustomThemeEntry(
        id = id,
        name = name,
        theme = ThemeCatalog.byId(source).copy(id = id, displayName = name),
        layout = SceneObjectCatalog.layoutFor(source, ThemeCatalog.byId(source).accentColor),
        customization = defaultCustomizationFor(source).copy(hillsVariation = 0.31f),
    )

    /** A state distinctive enough that "it restored the defaults" cannot pass for success. */
    private suspend fun makeItInteresting() {
        prefs.setTheme("beach")
        prefs.setScrollSpeed(0.42f)
        prefs.setSwipeScroll(false)
        prefs.setCustomLocation(41.9028f, 12.4964f, "Rome")
        prefs.setLiveWeatherApiKey("fake-key-for-test")
        prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.21f, "beach")
        prefs.setHillsColorDay(0x11223344, "beach")
        prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.87f, "winter")
        store.setOverride("christmas", entry("christmas", "christmas", "My Christmas"))
        store.upsertCustomTheme(entry("custom:backup-test", "beach", "Seaside"))
        CustomThemeRegistry.update(store.dataFlow.first())
    }

    // ------------------------------------------------------------------ round trip

    @Test
    fun anExportedBackupRestoresTheStateItWasTakenFrom() = runBlocking {
        makeItInteresting()
        val before = repo().snapshot()
        val file = repo().export()

        // Wipe everything the way a fresh install would look, then restore.
        wipe()
        val cleared = repo().snapshot()
        assertNotEquals("the wipe did nothing, so the restore proves nothing", before.settings, cleared.settings)

        val result = repo().import(file)
        assertTrue("import was refused: $result", result is BackupRepository.ImportResult.Applied)

        val after = repo().snapshot()
        assertEquals("global settings", before.settings, after.settings)
        assertEquals("theme customizations", before.themeCustomizations, after.themeCustomizations)
        assertEquals("overrides", before.customThemeData.overrides.keys, after.customThemeData.overrides.keys)
        assertEquals(
            "standalone themes",
            before.customThemeData.customThemes.map { it.id }.sorted(),
            after.customThemeData.customThemes.map { it.id }.sorted(),
        )
        assertEquals(
            "a saved theme's look",
            before.customThemeData.customThemes.first().customization,
            after.customThemeData.customThemes.first().customization,
        )
    }

    /** The per-theme customizations are the ones the persistence fix added; they must travel too. */
    @Test
    fun everyThemesOwnCustomizationSurvivesTheRoundTrip() = runBlocking {
        makeItInteresting()
        val before = repo().snapshot()
        assertTrue("nothing was customised to test", before.themeCustomizations.size >= 2)
        val file = repo().export()
        wipe()
        repo().import(file)
        for ((id, customization) in before.themeCustomizations) {
            assertEquals("$id did not come back", customization, repo().snapshot().themeCustomizations[id])
        }
    }

    // ------------------------------------------------------------------ refusals change nothing

    @Test
    fun aRefusedImportLeavesEveryStoreExactlyAsItWas() = runBlocking {
        makeItInteresting()
        val before = repo().snapshot()

        val rubbish = listOf(
            null,
            "",
            "{ not json",
            """{"kind":"paperscrape-theme","schemaVersion":1,"name":"x"}""",
            """{"kind":"paperscrape-app-backup","schemaVersion":99,"settings":{}}""",
            """{"kind":"paperscrape-app-backup","schemaVersion":1}""",
        )
        for (raw in rubbish) {
            val result = repo().import(raw)
            assertTrue("a bad file was accepted: $raw", result is BackupRepository.ImportResult.Refused)
            val after = repo().snapshot()
            assertEquals("settings changed after refusing $raw", before.settings, after.settings)
            assertEquals("themes changed after refusing $raw", before.themeCustomizations, after.themeCustomizations)
            assertEquals(
                "saved themes changed after refusing $raw",
                before.customThemeData.customThemes.map { it.id },
                after.customThemeData.customThemes.map { it.id },
            )
        }
    }

    /** A backup written by a future build must be refused, not partially applied. */
    @Test
    fun aNewerBackupIsRefusedAndNothingIsApplied() = runBlocking {
        makeItInteresting()
        val before = repo().snapshot()
        val newer = org.json.JSONObject(repo().export())
            .put("schemaVersion", AppBackup.SCHEMA_VERSION + 1).toString()
        val result = repo().import(newer)
        assertTrue(result is BackupRepository.ImportResult.Refused)
        assertEquals(before.settings, repo().snapshot().settings)
    }

    // ------------------------------------------------------------------ theme files

    @Test
    fun anImportedThemeIsIndependentOfTheBuiltInItCameFrom() = runBlocking {
        prefs.setCategoryDensity(ObjectCategory.HOUSES, 0.17f, "beach")
        val look = CustomThemeRegistry.resolveActiveCustomization(
            "beach",
            prefs.settingsFlow.first().pendingCustomization,
            prefs.settingsFlow.first().pendingCustomizationThemeId,
            prefs.settingsFlow.first().themeCustomizations,
        )
        val file = ThemeShare.of("beach", "Seaside", look, "test", 0L).toJsonString()

        val share = (parseThemeShare(file) as ThemeParseResult.Ok).share
        val imported = share.asNewCustomTheme(CustomThemeStore.newCustomThemeId())
        store.upsertCustomTheme(imported)
        CustomThemeRegistry.update(store.dataFlow.first())

        val saved = store.dataFlow.first().customThemes.first { it.id == imported.id }
        assertEquals("the imported look", look, saved.customization)
        assertNotEquals("the imported theme reused the built-in id", "beach", saved.id)
        // Its scene came from the file, not from a lookup of "beach" at read time.
        assertEquals(share.theme.skyDay.toList(), saved.theme.skyDay.toList())
        assertEquals(share.layout.staticObjects.size, saved.layout.staticObjects.size)
    }

    @Test
    fun aThemeFileIsRefusedAsABackupAndABackupAsAThemeFile() = runBlocking {
        makeItInteresting()
        val backupFile = repo().export()
        val themeFile = ThemeShare
            .of("beach", "Seaside", defaultCustomizationFor("beach"), "test", 0L).toJsonString()

        assertTrue(repo().import(themeFile) is BackupRepository.ImportResult.Refused)
        assertTrue(parseThemeShare(backupFile) is ThemeParseResult.Failed)
    }
}
