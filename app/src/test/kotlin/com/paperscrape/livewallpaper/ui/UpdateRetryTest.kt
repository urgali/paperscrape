package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.update.AppVersion
import com.paperscrape.livewallpaper.update.UpdateCheckResult
import com.paperscrape.livewallpaper.update.UpdateInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two dead ends of the update flow found by the v5.8B comment audit, closed in v5.8C.
 *
 * 1. After a failed download, "Try again" re-opened the update dialog and its "Install update"
 *    only closed it: `AdvancedScreen` ignored any second start for the same release.
 * 2. A tapped update notification without a connection opened the settings and showed nothing:
 *    the check's failure was dropped there as it is for a check nobody asked for.
 */
class UpdateRetryTest {

    private val info = UpdateInfo(
        tagName = "v5.9",
        version = AppVersion.parse("5.9")!!,
        releasePageUrl = "https://github.com/urgali/paperscrape/releases/tag/v5.9",
        releaseNotes = null,
    )
    private val apk = java.io.File("x.apk")

    @Test
    fun `a failed download of a release can be started again`() {
        val failed = UpdateUiState.Error("The download did not finish.")
        assertTrue("after an error the same release starts again", mayStartDownload(failed, "v5.9", "v5.9"))
        assertTrue("after Try again found it again", mayStartDownload(UpdateUiState.Available(info), "v5.9", "v5.9"))
        assertTrue("a release never started starts", mayStartDownload(UpdateUiState.Idle, null, "v5.9"))
    }

    @Test
    fun `a download under way or done is not started twice`() {
        for (state in listOf(
            UpdateUiState.Downloading(40), UpdateUiState.Verifying,
            UpdateUiState.ReadyToInstall(apk), UpdateUiState.NeedsPermission(apk),
        )) {
            assertFalse("$state", mayStartDownload(state, "v5.9", "v5.9"))
        }
    }

    @Test
    fun `a tapped notification shows what the check found, failure included`() {
        assertEquals(UpdateUiState.Available(info), updateStateFor(UpdateCheckResult.Available(info)))
        assertEquals(UpdateUiState.UpToDate, updateStateFor(UpdateCheckResult.UpToDate))
        val reason = UpdateCheckResult.Unreachable.Reason.NO_CONNECTION
        assertEquals(UpdateUiState.CheckFailed(reason), updateStateFor(UpdateCheckResult.Unreachable(reason)))
    }
}
