package com.paperscrape.livewallpaper.prefs

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.engine.BeachRoadRepairTest
import com.paperscrape.livewallpaper.engine.CustomThemeEntry
import com.paperscrape.livewallpaper.engine.SceneObjectCatalog
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.JUnitCore
import org.junit.runner.Request
import org.junit.runner.RunWith

/**
 * **The tests that write the app's real stores leave the phone's settings and saved themes as they found them.**
 *
 * The suite runs on the `.debug` the photographs and the measurements are taken with. A saved theme of the user's and
 * a setting of theirs are planted in the real stores, every test that writes them is run -- the four classes of
 * saved-theme and settings tests and `BeachRoadRepairTest`'s reset, which empties the saved themes too -- and both
 * files must come back byte for byte. Each of those tests restores the files with a [RealPrefsGuard] of its own; a
 * test that writes one without it leaves the planted theme or setting gone, and this fails. Whatever happens, the
 * files the phone had before this test are put back at its end.
 *
 * The tests named here run a second time inside this one, through their own runners: what is checked is what each of
 * them leaves on disk, and the only way to see that is to run it. The list is written here, where no source can be read;
 * `RealStoreGuardListTest` holds it, on the JVM, against the instrumented classes that open a real store.
 */
@RunWith(AndroidJUnit4::class)
class RealStoreGuardTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun storeFile(name: String) = File(context.filesDir, "datastore/$name.preferences_pb")

    @Test
    fun theStoreTestsLeaveThePhonesSettingsAndSavedThemesAsTheyFoundThem() {
        val stores = listOf(WALLPAPER_PREFS_STORE_NAME, CUSTOM_THEME_STORE_NAME)
        val phones = stores.associateWith { name -> storeFile(name).takeIf { it.isFile }?.readBytes() }
        try {
            runBlocking {
                CustomThemeStore(context).upsertCustomTheme(planted())
                WallpaperPrefs(context).setScrollSpeed(0.37f)
            }
            val before = stores.associateWith { storeFile(it).readBytes() }

            val requests = listOf(
                "ThemeCustomizationPersistenceTest" to Request.aClass(ThemeCustomizationPersistenceTest::class.java),
                "SavedThemeRenameStoreTest" to Request.aClass(SavedThemeRenameStoreTest::class.java),
                "UnreadableSavedThemeStoreTest" to Request.aClass(UnreadableSavedThemeStoreTest::class.java),
                "BackupRepositoryTest" to Request.aClass(BackupRepositoryTest::class.java),
                "BeachRoadRepairTest#resetStillRemovesBuiltinOverride" to
                    Request.method(BeachRoadRepairTest::class.java, "resetStillRemovesBuiltinOverride"),
            )
            val failures = ArrayList<String>()
            for ((label, request) in requests) {
                val result = JUnitCore().run(request)
                assertTrue("$label: no test ran", result.runCount > 0)
                result.failures.mapTo(failures) { "${it.description}: ${it.message}" }
            }
            assertTrue("the store tests must pass to say anything: $failures", failures.isEmpty())

            for (name in stores) {
                assertArrayEquals(
                    "$name.preferences_pb is not as the store tests found it: a test wrote the phone's real store " +
                        "without a RealPrefsGuard($name)",
                    before.getValue(name),
                    storeFile(name).readBytes(),
                )
            }
        } finally {
            for ((name, bytes) in phones) putBack(storeFile(name), bytes)
        }
    }

    /** A saved theme of the user's: a Beach saved under a name of their own. */
    private fun planted(): CustomThemeEntry {
        val beach = ThemeCatalog.byId("beach")
        return CustomThemeEntry(
            id = "custom:guard-test",
            name = "Kept by the guard",
            theme = beach.copy(id = "custom:guard-test", displayName = "Kept by the guard"),
            layout = SceneObjectCatalog.layoutFor("beach", beach.accentColor),
            customization = defaultCustomizationFor("beach"),
        )
    }

    /** The phone's own file back as it was, or gone if there was none: written aside, then renamed over. */
    private fun putBack(file: File, bytes: ByteArray?) {
        if (bytes == null) {
            file.delete()
            return
        }
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".guard-tmp")
        tmp.writeBytes(bytes)
        check(tmp.renameTo(file)) { "could not put back ${file.name}" }
    }
}
