package com.paperscrape.livewallpaper.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ARC-08: an update download survives the things that used to kill it.
 *
 * The download is launched into `SettingsScreen`'s `rememberCoroutineScope()`, and it died twice
 * over. **The Activity was recreated by every configuration change** -- measured on a OnePlus 6T,
 * two rotations produced two `finishDrawing of relaunch` entries for `SettingsActivity` -- which
 * cancels that scope. And **the state it reported to lived one level further down**, `remember`ed
 * inside `AdvancedScreen`, so walking back to the settings home mid-transfer left the job running
 * with nowhere to report and returning showed `Idle` for a download already sitting in the cache.
 *
 * Both halves are fixed locally and neither introduces a lifetime the app did not already have:
 *
 *  - `SettingsActivity` declares the configuration changes it handles itself, which is what a
 *    Compose screen is built to do. After the change the same two rotations, a light/dark switch and
 *    a font-scale change produced **zero** relaunches. The scope is not cancelled because nothing
 *    is destroyed.
 *  - The state moved up to the composable that owns the scope, so the job and the thing it writes
 *    to have one lifetime instead of two.
 *
 * What is deliberately *not* done: a `Service`, or a scope that outlives the Activity. Leaving the
 * settings screen for real still destroys it and still cancels the download, which is the behaviour
 * asked for -- a user who backs out of an update has abandoned it.
 *
 * ### Why this reads the manifest and the sources
 *
 * The subject is a lifetime relationship between three declarations: an Activity's configuration
 * handling, where a scope is created, and where the state it feeds is remembered. No unit test of
 * any one of them can see it, and the project has no Compose UI suite (TST-03). Same reasoning as
 * `IndoorClothingTest` and `WeatherLoopVisibilityTest`.
 */
class UpdateDownloadLifetimeTest {

    private val manifest: String by lazy { walkUp("src/main/AndroidManifest.xml").readText() }
    private val settingsScreen: String by lazy {
        walkUp("src/main/kotlin/com/paperscrape/livewallpaper/ui/SettingsScreen.kt").readText()
    }
    private val advancedScreen: String by lazy {
        walkUp("src/main/kotlin/com/paperscrape/livewallpaper/ui/AdvancedScreen.kt").readText()
    }

    @Test
    fun `the settings activity handles the configuration changes that used to recreate it`() {
        val declaration = manifest.substring(
            manifest.indexOf("android:name=\".ui.SettingsActivity\""),
        ).substringBefore(">")
        val configChanges = Regex("""android:configChanges="([^"]*)"""")
            .find(declaration)
            ?.groupValues
            ?.get(1)
            ?: ""
        val handled = configChanges.split("|").map { it.trim() }.toSet()
        // Every one of these recreates an Activity that does not claim it, and every one of them is
        // something a user does with a phone in their hand while a download runs.
        for (change in listOf("orientation", "screenSize", "uiMode", "fontScale", "density", "locale")) {
            assertTrue(
                "SettingsActivity must handle '$change' itself, or a download dies when it happens; " +
                    "declared: $configChanges",
                change in handled,
            )
        }
    }

    @Test
    fun `the download state is owned by the composable that owns the scope`() {
        assertTrue(
            "SettingsScreen must hold the update state beside the scope it launches into",
            Regex("""val updateState = remember \{ mutableStateOf<UpdateUiState>""")
                .containsMatchIn(settingsScreen),
        )
        assertTrue(
            "and must pass it down",
            settingsScreen.contains("updateState = updateState"),
        )
        assertEquals(
            "AdvancedScreen must not remember an update state of its own -- that is the lifetime bug",
            0,
            Regex("""remember \{ mutableStateOf<UpdateUiState>""").findAll(advancedScreen).count(),
        )
        assertTrue(
            "AdvancedScreen must take it as a parameter",
            advancedScreen.contains("updateState: MutableState<UpdateUiState>"),
        )
    }

    @Test
    fun `the scope is still the screen's, not a process-wide one`() {
        // The constraint on the fix: nothing may outlive the Activity. A download the user has
        // genuinely walked away from must still be cancelled.
        assertTrue(
            "SettingsScreen must still use rememberCoroutineScope",
            settingsScreen.contains("val scope = rememberCoroutineScope()"),
        )
        for (forbidden in listOf("GlobalScope", "ProcessLifecycleOwner", "CoroutineScope(SupervisorJob")) {
            assertEquals(
                "$forbidden would give the download a lifetime longer than the screen",
                0,
                Regex(Regex.escape(forbidden)).findAll(settingsScreen + advancedScreen).count(),
            )
        }
    }

    @Test
    fun `a cancelled download still leaves the screen somewhere the user can act`() {
        // The half that was already right and must stay right: whatever cancels the job, the state
        // goes back to something with a button on it rather than a frozen "Downloading...".
        val body = advancedScreen.substring(
            advancedScreen.indexOf("suspend fun runDownload("),
            advancedScreen.indexOf("\n    /**", advancedScreen.indexOf("suspend fun runDownload(")),
        )
        assertTrue("runDownload must catch cancellation:\n" + body, body.contains("CancellationException"))
        assertTrue("and restore a usable state", body.contains("UpdateUiState.Available(info)"))
        assertTrue("and rethrow it", body.contains("throw cancellation"))
    }

    private fun walkUp(suffix: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = File(dir, prefix + suffix)
                if (candidate.isFile) return candidate
            }
            dir = dir.parentFile
        }
        error("could not locate " + suffix)
    }
}
