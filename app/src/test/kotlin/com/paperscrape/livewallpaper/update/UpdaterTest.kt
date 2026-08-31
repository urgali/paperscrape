package com.paperscrape.livewallpaper.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Choosing what to download out of a release's attachments.
 *
 * Every case here is one where a looser rule would install the wrong file silently, which is why
 * the matching is by exact name rather than by extension.
 */
class ReleaseAssetsTest {

    private val release = listOf(
        ReleaseAsset("PaperScrape-v2.11.apk", "https://example.test/PaperScrape-v2.11.apk", 19_000_000),
        ReleaseAsset("PaperScrape-v2.11.apk.sha256", "https://example.test/PaperScrape-v2.11.apk.sha256", 96),
    )

    @Test
    fun `the apk is named after the tag`() {
        assertEquals("PaperScrape-v2.11.apk", ReleaseAssets.apkNameFor("v2.11"))
        assertEquals("PaperScrape-v2.11.apk.sha256", ReleaseAssets.checksumNameFor("v2.11"))
    }

    @Test
    fun `a release's own apk and checksum are found`() {
        assertEquals(release[0], ReleaseAssets.findApk("v2.11", release))
        assertEquals(release[1], ReleaseAssets.findChecksum("v2.11", release))
    }

    /** Gradle's own output name. The workflow renames it precisely so releases stay legible. */
    @Test
    fun `the gradle default name is not accepted as the apk`() {
        val assets = listOf(ReleaseAsset("app-release.apk", "https://example.test/app-release.apk"))
        assertNull(ReleaseAssets.findApk("v2.11", assets))
    }

    @Test
    fun `an apk belonging to another tag is not accepted`() {
        assertNull(ReleaseAssets.findApk("v2.12", release))
        assertNull(ReleaseAssets.findChecksum("v2.12", release))
    }

    @Test
    fun `a checksum for some other file is not accepted`() {
        val assets = listOf(
            ReleaseAsset("PaperScrape-v2.11.apk", "https://example.test/a.apk"),
            ReleaseAsset("mapping.txt.sha256", "https://example.test/mapping.txt.sha256"),
        )
        assertNull(ReleaseAssets.findChecksum("v2.11", assets))
    }

    @Test
    fun `a release with no attachments yields nothing rather than failing`() {
        assertNull(ReleaseAssets.findApk("v2.11", emptyList()))
        assertNull(ReleaseAssets.findChecksum("v2.11", emptyList()))
    }
}

/** Reading and comparing the release's SHA-256. */
class ChecksumFileTest {

    private val hash = "9f2c1b7e4a6d8c0f1e3a5b7d9c2e4f60a8b1d3f5079b2c4e6a8d0f2b4c6e8a01"

    @Test
    fun `the sha256sum format is read`() {
        assertEquals(hash, ChecksumFile.parse("$hash  PaperScrape-v2.11.apk\n"))
    }

    @Test
    fun `a bare hash is read`() {
        assertEquals(hash, ChecksumFile.parse("$hash\n"))
    }

    @Test
    fun `an uppercase hash is normalised`() {
        assertEquals(hash, ChecksumFile.parse("${hash.uppercase()}  PaperScrape-v2.11.apk"))
    }

    @Test
    fun `a file with nothing hash-shaped in it yields null`() {
        assertNull(ChecksumFile.parse(""))
        assertNull(ChecksumFile.parse("404: Not Found"))
        assertNull(ChecksumFile.parse("abc123  PaperScrape-v2.11.apk")) // too short to be sha-256
    }

    @Test
    fun `a matching digest is accepted, whatever its case`() {
        assertTrue(ChecksumFile.matches(hash, hash))
        assertTrue(ChecksumFile.matches(hash.uppercase(), hash))
        assertTrue(ChecksumFile.matches(hash, hash.uppercase()))
    }

    @Test
    fun `a differing digest is rejected`() {
        val other = hash.dropLast(1) + "2"
        assertTrue(!ChecksumFile.matches(hash, other))
    }

    /**
     * The cases that matter most: a missing or malformed expectation must never *pass*. An install
     * that proceeds because verification was skipped is worse than an install that does not
     * happen.
     */
    @Test
    fun `a missing or malformed digest never matches`() {
        assertTrue(!ChecksumFile.matches(null, hash))
        assertTrue(!ChecksumFile.matches(hash, null))
        assertTrue(!ChecksumFile.matches("", ""))
        assertTrue(!ChecksumFile.matches("   ", hash))
        assertTrue(!ChecksumFile.matches(hash.take(32), hash.take(32)))
    }
}

/** The last check before the system installer is opened. */
class ApkSafetyTest {

    private val app = "com.paperscrape.livewallpaper"

    @Test
    fun `a newer build of this app is allowed`() {
        val verdict = ApkSafety.verdict(app, 14, ApkIdentity(app, 15, "2.11"), signedByThisApp = true)
        assertSame(InstallVerdict.Allowed, verdict)
    }

    @Test
    fun `another app is refused, however well it verified`() {
        val verdict = ApkSafety.verdict(app, 14, ApkIdentity("com.example.other", 99, "9.9"), signedByThisApp = true)
        assertTrue(verdict is InstallVerdict.WrongPackage)
        assertEquals(app, (verdict as InstallVerdict.WrongPackage).expected)
    }

    @Test
    fun `an older build is refused`() {
        val verdict = ApkSafety.verdict(app, 14, ApkIdentity(app, 13, "2.9"), signedByThisApp = true)
        assertTrue(verdict is InstallVerdict.NotNewer)
        assertEquals(13L, (verdict as InstallVerdict.NotNewer).found)
    }

    @Test
    fun `the same build is refused - it is not an update`() {
        assertTrue(ApkSafety.verdict(app, 14, ApkIdentity(app, 14, "2.10"), signedByThisApp = true) is InstallVerdict.NotNewer)
    }

    @Test
    fun `a file that is not a readable package is refused`() {
        assertSame(InstallVerdict.Unreadable, ApkSafety.verdict(app, 14, null, signedByThisApp = true))
    }

    /**
     * The debug build has its own application id, so a release APK is a different package to it.
     * Refusing that is correct: it would be a new install, not an update.
     */
    @Test
    fun `a release apk is not an update to the debug build`() {
        val verdict = ApkSafety.verdict("$app.debug", 14, ApkIdentity(app, 15, "2.11"), signedByThisApp = true)
        assertTrue(verdict is InstallVerdict.WrongPackage)
    }
    // ------------------------------------------------------------------ SEC-01: signature

    /**
     * A correctly named, newer build that somebody else signed is refused before the prompt.
     *
     * The SHA-256 the download is checked against is published on the same release the file comes
     * from, so it proves the bytes arrived intact and nothing about who produced them. A repository
     * or CI in the wrong hands serves a matching pair and reached the user's install prompt with the
     * app calling it verified. Android refuses a differently-signed update at install time, so the
     * outcome was a confusing failure rather than a silent compromise -- but the app was the one
     * making the claim, and now the app is the one checking it.
     */
    @Test
    fun `a differently signed build is refused`() {
        val verdict = ApkSafety.verdict(app, 14, ApkIdentity(app, 15, "2.11"), signedByThisApp = false)
        assertSame(InstallVerdict.WrongSignature, verdict)
    }

    @Test
    fun `a signature that cannot be read is refused, not waved through`() {
        // `null` is "could not verify", and the one thing not to do with that is treat it as a pass.
        val verdict = ApkSafety.verdict(app, 14, ApkIdentity(app, 15, "2.11"), signedByThisApp = null)
        assertSame(InstallVerdict.WrongSignature, verdict)
    }

    @Test
    fun `the wrong package still wins over the signature`() {
        // Ordering: a user can understand "that is a different app" and act on it. A signature
        // mismatch on an otherwise correct build means something is wrong upstream, so it is the
        // last thing checked and the last thing reported.
        val verdict = ApkSafety.verdict(app, 14, ApkIdentity("com.example.other", 99, "9.9"), signedByThisApp = false)
        assertTrue(verdict is InstallVerdict.WrongPackage)
    }

    @Test
    fun `an older build still wins over the signature`() {
        val verdict = ApkSafety.verdict(app, 14, ApkIdentity(app, 13, "2.9"), signedByThisApp = false)
        assertTrue(verdict is InstallVerdict.NotNewer)
    }
}

/** Version comparison, at the point where it decides whether to offer an update at all. */
class UpdateVersionComparisonTest {

    private fun newer(latest: String, current: String): Boolean {
        val a = AppVersion.parse(latest) ?: return false
        val b = AppVersion.parse(current) ?: return false
        return a > b
    }

    @Test
    fun `a newer minor is offered`() {
        assertTrue(newer("v2.11", "2.10"))
        assertTrue(newer("v2.10", "2.9"))
    }

    @Test
    fun `a newer major is offered`() {
        assertTrue(newer("v3.0", "2.11"))
    }

    @Test
    fun `the same version is not an update`() {
        assertTrue(!newer("v2.11", "2.11"))
    }

    @Test
    fun `an older release is not an update`() {
        assertTrue(!newer("v2.9", "2.11"))
    }

    /** The pre-release tags. Read as major 73, one of these would "update" every user backwards. */
    @Test
    fun `legacy integer tags are unreadable and therefore invisible`() {
        assertNull(AppVersion.parse("v73"))
        assertNull(AppVersion.parse("74"))
        assertTrue(!newer("v73", "2.11"))
    }

    @Test
    fun `a malformed tag is unreadable`() {
        assertNull(AppVersion.parse("v2.11.3"))
        assertNull(AppVersion.parse("release-2.11"))
        assertNull(AppVersion.parse("v2.x"))
        assertNull(AppVersion.parse(""))
    }
}

/** Whether a release can be installed from inside the app at all. */
class UpdateInfoInstallabilityTest {

    private val version = AppVersion.parse("2.11")!!
    private val apk = ReleaseAsset("PaperScrape-v2.11.apk", "https://example.test/a.apk")
    private val checksum = ReleaseAsset("PaperScrape-v2.11.apk.sha256", "https://example.test/a.sha256")

    private fun info(apk: ReleaseAsset?, checksum: ReleaseAsset?) = UpdateInfo(
        tagName = "v2.11",
        version = version,
        releasePageUrl = "https://github.com/urgali/paperscrape/releases/tag/v2.11",
        releaseNotes = null,
        apkAsset = apk,
        checksumAsset = checksum,
    )

    @Test
    fun `both files present means the in-app flow is available`() {
        assertTrue(info(apk, checksum).isInstallable)
    }

    /** Without a checksum there is nothing to verify against, so the flow is not offered. */
    @Test
    fun `a missing checksum makes a release manual-download only`() {
        assertTrue(!info(apk, null).isInstallable)
    }

    @Test
    fun `a missing apk makes a release manual-download only`() {
        assertTrue(!info(null, checksum).isInstallable)
        assertTrue(!info(null, null).isInstallable)
    }

}
