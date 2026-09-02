package com.paperscrape.livewallpaper.prefs

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource
import java.io.File

/**
 * Restores the app's real preference store to exactly what it held before a test class ran.
 *
 * ### Why this exists
 *
 * The three prefs test classes exercise [WallpaperPrefs] against the app's own DataStore file --
 * deliberately, because what they defend (durability, corruption recovery, backup round-trips)
 * is a property of that store and not of a fixture. But `am instrument` runs them in a fresh
 * process while the live wallpaper service keeps its own, so every `setTheme("beach")` a test
 * made **stayed made** on the phone: after a full suite run the maintainer's selected theme had
 * silently changed from Autumn to Beach. Test pollution of production state, found on the device.
 *
 * ### How it restores
 *
 * A byte snapshot of `datastore/paperscrape_prefs.preferences_pb`, taken before the first test in
 * the class and written back after the last one. Bytes rather than a key-by-key replay through
 * the API, because the corruption tests deliberately leave states a replay could not reproduce
 * (including "the file does not exist"). Writing behind the instrumentation process's own open
 * DataStore singleton leaves that singleton's cache stale, which is harmless: the restore is the
 * class's last write and the process exits with the run. DataStore's own writes are durable
 * (`editDurably`) and atomic, so the bytes on disk at that point are a complete store.
 *
 * Used as a `@ClassRule` so the restore happens once, after every test method and their own
 * `@After` blocks have finished.
 */
class RealPrefsGuard : ExternalResource() {

    private var saved: ByteArray? = null
    private var existed = false

    private val file: File
        get() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            return File(context.filesDir, "datastore/$WALLPAPER_PREFS_STORE_NAME.preferences_pb")
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
