package com.paperscrape.livewallpaper.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A tap that needs another app the phone does not have says so, and the settings stay open.** *Open release page* and
 * *Source code on GitHub* (a browser), *Import app backup* and *Import theme* (a file picker), *Export app backup* and
 * *Export theme* (a place to save), the update's permission (the phone's page for it), *Install* (the system's
 * installer, started by `ApkInstaller.launchInstall`) and the wallpaper picker *Set as wallpaper* falls back on: without
 * the app, a bare start throws `ActivityNotFoundException` out of the tap, uncaught, and the settings close.
 *
 * Asserted on the source, as the project does for Compose code: no screen, and not the installer's start, starts another
 * app's screen bare -- each goes through `startActivityOrSay` or `launchOrSay` ([MissingApp]), or a `runCatching` of its
 * own.
 */
class MissingAppTest {

    @Test
    fun `no tap starts another app's screen without a way out`() {
        val offenders = mutableListOf<String>()
        for (file in listOf(
            "ui/SettingsScreen.kt", "ui/AdvancedScreen.kt", "ui/ThemeGalleryScreen.kt", "ui/WeatherTimeScreen.kt", "ui/SettingsActivity.kt",
            "update/ApkDownloader.kt",
        )) {
            code(file).lines().forEachIndexed { i, line ->
                val bareStart = Regex("""(?<![A-Za-z])startActivity\(""").containsMatchIn(line) && !line.contains("runCatching {")
                // The system's own permission dialogs are not another app's screen that a phone can lack.
                val bareLaunch = Regex("""[A-Za-z]+Launcher\.launch\(""").containsMatchIn(line) &&
                    !line.contains("POST_NOTIFICATIONS") && !line.contains("permissionsToRequest")
                if (bareStart || bareLaunch) offenders += "${file.substringAfterLast('/')}:${i + 1}: ${line.trim()}"
            }
        }
        // The one bare start left is the direct way to the wallpaper's own preview, inside its try: its fallback says so.
        assertEquals(
            listOf("SettingsActivity.kt: startActivity(intent)"),
            offenders.map { it.substringBefore(":") + ": " + it.substringAfter(": ") },
        )
        val activity = code("ui/SettingsActivity.kt")
        assertTrue(activity.contains("startActivityOrSay(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER), MissingApp.WALLPAPER_PICKER)"))
        val advanced = code("ui/AdvancedScreen.kt")
        assertTrue(advanced.contains("context.startActivityOrSay(Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri()), MissingApp.BROWSER)"))
        assertTrue(advanced.contains("importLauncher.launchOrSay(arrayOf(\"application/json\", \"text/plain\", \"*/*\"), context, MissingApp.FILE_PICKER)"))
        assertTrue(advanced.contains("exportLauncher.launchOrSay(backupRepository.suggestedFileName(), context, MissingApp.FILE_SAVER)"))
        assertTrue(code("ui/SettingsScreen.kt").contains("context.startActivityOrSay(Intent(Intent.ACTION_VIEW, update.releasePageUrl.toUri()), MissingApp.BROWSER)"))
        // The installer, whichever of its two taps starts it (Install, and the return from the permission page).
        assertTrue(code("update/ApkDownloader.kt").lines().map { it.trim() }.contains("context.startActivityOrSay(intent, MissingApp.INSTALLER)"))
    }

    /** Every missing app, by its name, and its line, whole: one more missing app is one more row here. */
    @Test
    fun `each missing app has its line`() {
        assertEquals(
            mapOf(
                "BROWSER" to "There is no app on this phone to open web pages with.",
                "FILE_PICKER" to "There is no app on this phone to choose a file with. Nothing changed.",
                "FILE_SAVER" to "There is no app on this phone to save a file with. Nothing changed.",
                "INSTALL_PERMISSION_PAGE" to
                    "This phone has no page to allow PaperScrape to install apps, so the update cannot be installed from here.",
                "INSTALLER" to "This phone has no app to install the update with, so it cannot be installed from here.",
                "WALLPAPER_PICKER" to "There is no wallpaper picker on this phone to set PaperScrape with.",
            ),
            MissingApp.entries.associate { it.name to it.line },
        )
    }

    /** A file's code (its path under the app's package), its comment lines taken out. */
    private fun code(name: String): String {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/$name"
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = java.io.File(dir, "$prefix$suffix")
                if (f.isFile) return f.readLines().joinToString("\n") { l -> if (l.trim().startsWith("//") || l.trim().startsWith("*")) "" else l }
            }
            dir = dir.parentFile
        }
        error("no $suffix")
    }
}
