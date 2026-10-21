package com.paperscrape.livewallpaper.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **"Checking..." while the button checks, and no second tap** (v5.15C2, inventory I-969). [UpdateUiState.Checking] was
 * declared and, since v5.14 at least, never set: the row never said "Checking...", stayed enabled, and every tap during a
 * check was another request to GitHub, inside GitHub's limit too (the button asks anyway). Now *Check for updates* and
 * *Try again* go through `startCheck`, which sets it before the check starts and refuses a tap while it holds.
 *
 * The rule is [UpdateUiState.allowsChecking]; the wiring is held on `AdvancedScreen`'s source (no Compose UI test runs
 * here), and the row's look is the round's photograph on the BV6600.
 */
class CheckingStateTest {

    @Test
    fun `a check is refused only while one is in flight, or a download`() {
        val apk = File("x.apk")
        val inFlight = listOf(UpdateUiState.Checking, UpdateUiState.Downloading(40), UpdateUiState.Verifying)
        for (state in inFlight) assertFalse("$state", state.allowsChecking)
        val free = listOf(
            UpdateUiState.Idle, UpdateUiState.UpToDate,
            UpdateUiState.CheckFailed(com.paperscrape.livewallpaper.update.UpdateCheckResult.Unreachable.Reason.NO_CONNECTION),
            UpdateUiState.ReadyToInstall(apk), UpdateUiState.NeedsPermission(apk), UpdateUiState.Error("x"),
            UpdateUiState.Available(
                com.paperscrape.livewallpaper.update.UpdateInfo(
                    tagName = "v5.16",
                    version = com.paperscrape.livewallpaper.update.AppVersion.parse("5.16")!!,
                    releasePageUrl = "https://github.com/urgali/paperscrape/releases/tag/v5.16",
                    releaseNotes = null,
                ),
            ),
        )
        for (state in free) assertTrue("$state", state.allowsChecking)
    }

    @Test
    fun `the button and Try again set Checking before the check, and the row says so and takes no tap`() {
        val screen = source("ui/AdvancedScreen.kt")
        val start = screen.substring(screen.indexOf("fun startCheck() {")).substringBefore("\n    }\n")
        val guard = start.indexOf("if (!updateState.allowsChecking) return")
        val set = start.indexOf("updateState = UpdateUiState.Checking")
        val launch = start.indexOf("scope.launch {")
        assertTrue("a tap while checking is refused", guard >= 0)
        assertTrue("Checking is set, before the coroutine starts", set > guard && launch > set)
        assertTrue(start.substring(launch).contains("finished = checkForUpdate(updateReplyStore, onUpdateFound)"))
        assertTrue("the check's outcome replaces it, a cut short check too", start.contains("} finally {\n                // A check cut short (the screen gone) leaves no \"Checking...\" behind.\n                updateState = finished"))
        // Both doors go through it; no other line starts a check of the button's.
        assertTrue(screen.contains("onClick = { startCheck() },"))
        assertTrue(screen.contains("onRetry = { startCheck() },"))
        assertEquals("one place calls the button's check", 1, Regex("""= checkForUpdate\(updateReplyStore, onUpdateFound\)""").findAll(screen).count())
        // The row: its title while checking, and no tap.
        val row = screen.substring(screen.indexOf("is UpdateUiState.Checking -> \"Checking...\"") - 200).substringBefore("onClick = { startCheck() },")
        assertTrue(row.contains("enabled = updateState.allowsChecking,"))
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
