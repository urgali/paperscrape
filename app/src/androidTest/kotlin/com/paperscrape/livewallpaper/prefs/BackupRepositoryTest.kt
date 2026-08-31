package com.paperscrape.livewallpaper.prefs

import com.paperscrape.livewallpaper.engine.toJsonString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.engine.CustomThemeEntry
import com.paperscrape.livewallpaper.engine.CustomThemeRegistry
import com.paperscrape.livewallpaper.engine.SceneObjectCatalog
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    // ------------------------------------------------------------------ cancellation (v4.6)

    /**
     * **An import cancelled between its two writes still finishes both of them.**
     *
     * The staging in [BackupRepository] guards against one write throwing. It did not guard against
     * the *caller* going away, and the caller is `rememberCoroutineScope()` — the settings screen's
     * composition, which Compose cancels on a rotation, a back press, or the system reclaiming the
     * Activity. A cancellation landing between the two `replaceAll`s left the preferences new and
     * the saved themes old, and then skipped the rollback as well: the `catch` caught the
     * `CancellationException`, the rollback suspended on a job that was already cancelled, threw
     * immediately, and the whole thing reported `Broken` to a UI that no longer existed.
     *
     * The cancellation is injected **exactly where it hurts** rather than raced for: the theme
     * store cancels the importing job on its way into the second write. Cancelling from outside and
     * hoping to hit a window a few milliseconds wide would pass by landing before the transaction
     * even started, which proves nothing at all.
     */
    @Test
    fun anImportCancelledBetweenTheTwoWritesStillFinishesBoth() = runBlocking {
        makeItInteresting()
        val file = repo().export()

        // A state visibly different from the file's, in *both* stores, so neither assertion below
        // can pass by the import having done nothing.
        prefs.setTheme("winter")
        prefs.setScrollSpeed(0.11f)
        store.replaceAll(com.paperscrape.livewallpaper.engine.CustomThemeData.EMPTY)

        val scope = CoroutineScope(Dispatchers.Default + Job())
        var importing: Job? = null
        val cancelling = CancellingThemeStore(context) { importing?.cancel() }
        importing = scope.launch { BackupRepository(prefs, cancelling, "test").import(file) }
        importing.join()
        scope.cancel()

        assertTrue(
            "the second store's write did not complete: the cancellation reached inside the " +
                "transaction and took it down, which is the defect NonCancellable exists to stop",
            cancelling.wrote,
        )
        val after = repo().snapshot()
        assertEquals("the preferences did not come from the file", "beach", after.settings.themeId)
        assertEquals(0.42f, after.settings.scrollSpeed, 0.0001f)
        assertEquals(
            "the second store was left behind -- this is the half-old, half-new state",
            listOf("custom:backup-test"),
            after.customThemeData.customThemes.map { it.id },
        )
        assertEquals(
            "and the built-in override with it",
            setOf("christmas"),
            after.customThemeData.overrides.keys,
        )
    }

    /**
     * A write that fails for a real reason still rolls both stores back.
     *
     * The point of the change was to stop cancellation skipping the rollback, so the rollback
     * itself has to keep working — checked by handing the repository a store whose *first* write
     * throws and whose second, the rollback's own, does not. Nothing the user had may be lost.
     */
    @Test
    fun anExplicitFailureOnTheSecondStoreRollsTheFirstOneBack() = runBlocking {
        makeItInteresting()
        val file = repo().export()

        prefs.setTheme("winter")
        prefs.setScrollSpeed(0.11f)
        val before = repo().snapshot()

        val poisoned = BackupRepository(prefs, FailOnceThemeStore(context), "test")
        val result = poisoned.import(file)

        assertTrue("expected a rollback, got $result", result is BackupRepository.ImportResult.RolledBack)
        val after = repo().snapshot()
        assertEquals("the preferences were not put back", "winter", after.settings.themeId)
        assertEquals(0.11f, after.settings.scrollSpeed, 0.0001f)
        assertEquals(
            "the themes were left changed by a transaction that failed",
            before.customThemeData.customThemes.map { it.id },
            after.customThemeData.customThemes.map { it.id },
        )
    }

    /**
     * A [CustomThemeStore] that cancels the importing job on its way into the write.
     *
     * `replaceAll` is `open` for this and for [FailOnceThemeStore] and for nothing else — see its
     * own doc comment. There is no honest way to make a real DataStore fail, or to be cancelled at
     * a chosen instant, from outside.
     */
    // ------------------------------------------------------------------ process kill (v4.15)

    /**
     * **BCK-06, against the real stores: a kill between the two writes is recovered.**
     *
     * `NonCancellable` protects the pair from the *caller* going away. It cannot protect it from the
     * process being killed, and between the two writes the preferences were new while the saved
     * themes were still old -- with nothing anywhere recording that.
     *
     * The kill is injected the way the cancellation above is, at the only moment it matters: a store
     * that throws a fatal-looking error on its way into the second write, leaving exactly the state
     * a `SIGKILL` there would leave. Then [BackupRepository.finishPendingImport] runs, which is what
     * the wallpaper service and the settings screen do at every start, and both stores must agree.
     */
    @Test
    fun aKillBetweenTheTwoWritesIsFinishedAtTheNextStart() = runBlocking {
        makeItInteresting()
        val file = repo().export()

        prefs.setTheme("winter")
        prefs.setScrollSpeed(0.11f)
        store.replaceAll(com.paperscrape.livewallpaper.engine.CustomThemeData.EMPTY)

        // The import dies after the preferences (and the staged document) have landed and before
        // the themes have.
        val dying = FailOnceThemeStore(context)
        val result = BackupRepository(prefs, dying, "test").import(file)
        assertTrue("the import should have reported a rollback attempt, got $result", result != null)

        // A kill is not a rollback: put the half-applied state back the way a killed process leaves
        // it, with the staged document still on disk.
        val fromFile = (parseAppBackup(file) as BackupParseResult.Ok).backup
        prefs.replaceAllStagingThemes(
            fromFile.settings,
            fromFile.themeCustomizations,
            fromFile.customThemeData.toJsonString(),
        )
        store.replaceAll(com.paperscrape.livewallpaper.engine.CustomThemeData.EMPTY)
        assertTrue("the staged document must be on disk", prefs.pendingImportThemes() != null)

        val finished = BackupRepository(prefs, store, "test").finishPendingImport()
        assertTrue("the pending import must have been completed", finished)
        assertEquals("nothing may be left pending", null, prefs.pendingImportThemes())

        val after = repo().snapshot()
        assertEquals("the preferences came from the file", "beach", after.settings.themeId)
        assertEquals(0.42f, after.settings.scrollSpeed, 0.0001f)
        assertEquals(
            "and so did the saved themes -- this is the half that used to be lost",
            fromFile.customThemeData.overrides.keys,
            after.customThemeData.overrides.keys,
        )
    }

    @Test
    fun aCompletedImportLeavesNothingPending() = runBlocking {
        makeItInteresting()
        val file = repo().export()
        repo().import(file)
        assertEquals(
            "a clean import must clear its own staging",
            null,
            prefs.pendingImportThemes(),
        )
    }

    @Test
    fun finishingWithNothingPendingDoesNothing() = runBlocking {
        assertEquals(null, prefs.pendingImportThemes())
        assertTrue("there was nothing to finish", !repo().finishPendingImport())
    }

    private class CancellingThemeStore(
        context: android.content.Context,
        private val cancel: () -> Unit,
    ) : CustomThemeStore(context) {
        var wrote = false
            private set

        // **`replaceAllJson`, not `replaceAll`.** v4.15's atomic import (BCK-06) writes this store
        // from the exact string staged in the preference store rather than re-serialising a parsed
        // copy, so that is the seam the import actually goes through now. `replaceAll` delegates
        // here, so overriding this one intercepts both callers and this double keeps working for
        // whichever the code under test uses.
        override suspend fun replaceAllJson(json: String) {
            cancel()
            super.replaceAllJson(json)
            wrote = true
        }
    }

    /** Throws on the first write and behaves on every one after it, so the rollback can complete. */
    private class FailOnceThemeStore(context: android.content.Context) : CustomThemeStore(context) {
        private var failed = false

        // See CancellingThemeStore: the import's seam is `replaceAllJson` since v4.15, and
        // `replaceAll` delegates to it, so one override covers both.
        override suspend fun replaceAllJson(json: String) {
            if (!failed) {
                failed = true
                throw IllegalStateException("simulated store failure")
            }
            super.replaceAllJson(json)
        }
    }
}
