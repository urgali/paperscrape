package com.paperscrape.livewallpaper.prefs

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource
import java.io.File

/**
 * Puts one of the app's real DataStore files back to exactly what it held before each test: the settings
 * ([WALLPAPER_PREFS_STORE_NAME], the default) or the saved themes and saved versions ([CUSTOM_THEME_STORE_NAME]).
 *
 * ### Why this exists
 *
 * The prefs and saved-theme test classes exercise [WallpaperPrefs] and [CustomThemeStore] against the app's own
 * DataStore files -- deliberately, because what they defend (durability, corruption recovery, renames, backup
 * round-trips) is a property of those stores and not of a fixture. But they run on the phone the photographs and the
 * measurements are taken with, in its `.debug`: without a restore every `setTheme("beach")` a test makes **stays
 * made** -- a full suite run would leave the phone's selected theme changed to Beach -- and every test that wipes the
 * saved themes would leave the `.debug` with none.
 *
 * ### How it restores
 *
 * A byte snapshot of `datastore/<store>.preferences_pb`, taken before each test (before its `@Before`) and written
 * back after it (after its `@After`), as a `@get:Rule`. Bytes rather than a key-by-key replay through the API,
 * because the corruption tests deliberately leave states a replay could not reproduce (including "the file does not
 * exist").
 *
 * The write goes behind the instrumentation process's own DataStore singleton, which keeps the contents it last
 * wrote in its cache and does not read the file again: after a restore, that cache and the file disagree until the
 * next write through the store. The file is still right at the end of the run because **every test that writes a
 * real store restores it with this rule**: the snapshot of the next one is read from the file, never from the cache,
 * and the last write to the file is always a restore. A class that writes a real store without this rule breaks
 * that, and `RealStoreGuardTest` is what finds it. DataStore's own writes are durable and atomic, so the bytes on
 * disk at a snapshot are a complete store.
 */
class RealPrefsGuard(private val storeName: String = WALLPAPER_PREFS_STORE_NAME) : ExternalResource() {

    private var saved: ByteArray? = null
    private var existed = false

    private val file: File
        get() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            return File(context.filesDir, "datastore/$storeName.preferences_pb")
        }

    override fun before() {
        existed = file.exists()
        saved = if (existed) file.readBytes() else null
    }

    override fun after() {
        val f = file
        if (existed) {
            f.parentFile?.mkdirs()
            // Atomic the same way DataStore itself writes: temp file, then rename over.
            val tmp = File(f.parentFile, f.name + ".guard-tmp")
            tmp.writeBytes(saved!!)
            check(tmp.renameTo(f)) { "could not restore ${f.name}" }
        } else {
            f.delete()
        }
    }
}
