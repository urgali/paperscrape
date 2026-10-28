package com.paperscrape.livewallpaper

import android.content.Context
import android.os.Build
import android.os.StrictMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.update.ApkDownloader
import com.paperscrape.livewallpaper.update.ApkInstaller
import com.paperscrape.livewallpaper.update.AppVersion
import com.paperscrape.livewallpaper.update.InstallVerdict
import com.paperscrape.livewallpaper.update.ReleaseAsset
import com.paperscrape.livewallpaper.update.UpdateDownloadResult
import com.paperscrape.livewallpaper.update.UpdateInfo
import java.io.File
import java.util.Collections
import java.util.concurrent.Executor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **Verifying a downloaded update reads nothing from the disk on the thread that asks.**
 *
 * The settings screen runs the download and the check that follows from its main thread. The clearing of the downloads
 * folder and the check -- two whole parses of the package and the verification of its signature over every byte -- run
 * on the IO dispatcher (`ApkDownloader.downloadAndVerify`, `ApkInstaller.verdict`): on the main thread "Verifying..."
 * would freeze, and Android could say the app is not responding.
 *
 * Each call is made from a thread of its own whose StrictMode policy records every read and write of the disk made on
 * it, as the main thread's would be. No network: the download's addresses are on this phone, refused before any byte
 * (the app allows no plain HTTP), so the download fails, which is all it is asked to do here. StrictMode's listener is
 * API 28's: below it the tests are skipped.
 */
@RunWith(AndroidJUnit4::class)
class ApkVerificationThreadTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** Runs [block] on a thread whose StrictMode policy records each disk read and write made on that thread. */
    private fun diskAccessOnTheCallersThread(block: suspend () -> Unit): List<String> {
        assumeTrue("StrictMode's listener needs API 28", Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        val seen = Collections.synchronizedList(mutableListOf<String>())
        var failure: Throwable? = null
        val caller = Thread {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyListener(Executor { it.run() }) { violation ->
                        seen += violation.javaClass.simpleName + " at " +
                            violation.stackTrace.firstOrNull { it.className.startsWith("com.paperscrape") }
                    }
                    .build(),
            )
            try {
                runBlocking { block() }
            } catch (t: Throwable) {
                failure = t
            }
        }
        caller.start()
        caller.join(60_000)
        failure?.let { throw it }
        return seen.toList()
    }

    @Test
    fun theDownloadTouchesNoDiskOnTheCallersThread() {
        val context = context
        val info = UpdateInfo(
            tagName = "v99.0",
            version = AppVersion.parse("99.0")!!,
            releasePageUrl = "http://127.0.0.1:1/release",
            releaseNotes = null,
            apkAsset = ReleaseAsset("PaperScrape-v99.0.apk", "http://127.0.0.1:1/apk"),
            checksumAsset = ReleaseAsset("PaperScrape-v99.0.apk.sha256", "http://127.0.0.1:1/sha256"),
        )
        var result: UpdateDownloadResult? = null
        val disk = diskAccessOnTheCallersThread { result = ApkDownloader.downloadAndVerify(context, info) {} }
        assertEquals("no network: the download fails", UpdateDownloadResult.Failed, result)
        assertEquals("disk reads and writes on the caller's thread", emptyList<String>(), disk)
    }

    @Test
    fun theVerdictTouchesNoDiskOnTheCallersThread() {
        val context = context
        val own = File(context.applicationInfo.sourceDir)
        var verdict: InstallVerdict? = null
        val disk = diskAccessOnTheCallersThread { verdict = ApkInstaller.verdict(context, own) }
        assertTrue("this app's own APK is not newer than itself: $verdict", verdict is InstallVerdict.NotNewer)
        assertEquals("disk reads and writes on the caller's thread", emptyList<String>(), disk)
    }
}
