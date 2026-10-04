package com.paperscrape.livewallpaper.update

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The downloaded APK of an update that is already installed is deleted at the next start, and one
 * that may still be waiting for the installer is kept (v5.10B, [ApkDownloader.pruneInstalled]).
 */
class ApkCachePruneTest {

    private fun folderWith(vararg names: String) = Files.createTempDirectory("updates").toFile().apply {
        for (name in names) resolve(name).writeText(name)
    }

    @Test
    fun `the installed release and older ones go, a newer one stays`() {
        val dir = folderWith(
            "PaperScrape-v5.8.apk",
            "PaperScrape-v5.9.apk",
            "PaperScrape-v5.10.apk",
            "PaperScrape-v6.0.apk",
        )
        val deleted = ApkDownloader.pruneInstalledIn(dir, installedVersionName = "5.9")
        assertEquals(2, deleted)
        assertEquals(setOf("PaperScrape-v5.10.apk", "PaperScrape-v6.0.apk"), dir.list()!!.toSet())
        dir.deleteRecursively()
    }

    @Test
    fun `anything in the folder that is not a release's APK goes too`() {
        val dir = folderWith("PaperScrape-v5.9.apk.part", "notes.txt", "PaperScrape-v73.apk", "PaperScrape-.apk", "PaperScrape-v5.11.apk")
        ApkDownloader.pruneInstalledIn(dir, installedVersionName = "5.10")
        // v73 is a pre-release integer tag the updater never offers (`AppVersion.parse` refuses it).
        assertEquals(setOf("PaperScrape-v5.11.apk"), dir.list()!!.toSet())
        dir.deleteRecursively()
    }

    @Test
    fun `an unreadable running version deletes nothing, and the names round-trip`() {
        val dir = folderWith("PaperScrape-v5.8.apk")
        assertEquals(0, ApkDownloader.pruneInstalledIn(dir, installedVersionName = "debug"))
        assertEquals(1, dir.list()!!.size)
        dir.deleteRecursively()
        assertEquals("v5.10", ReleaseAssets.tagOfApkName(ReleaseAssets.apkNameFor("v5.10")))
        assertNull(ReleaseAssets.tagOfApkName("app-release.apk"))
    }
}
