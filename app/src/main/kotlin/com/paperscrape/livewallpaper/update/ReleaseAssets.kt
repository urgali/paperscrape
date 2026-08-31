package com.paperscrape.livewallpaper.update

/** One file attached to a GitHub release. */
data class ReleaseAsset(val name: String, val downloadUrl: String, val sizeBytes: Long = 0L)

/**
 * Which of a release's attached files is the APK to install, and which is its checksum.
 *
 * Pure, and separate from the network call, because getting this wrong is silent and expensive: an
 * update flow that grabs "whatever ends in .apk" will happily install the debug artifact, a
 * mapping file's neighbour, or a leftover from an older tag. The CI publishes exactly two files
 * per release (`.github/workflows/android-build.yml`), named after the tag, and this matches those
 * names and nothing else.
 */
object ReleaseAssets {

    /** The name the release workflow gives the APK: `PaperScrape-v2.11.apk`. */
    fun apkNameFor(tagName: String): String = "PaperScrape-$tagName.apk"

    /** The name the workflow gives the checksum: the APK's name with `.sha256` appended. */
    fun checksumNameFor(tagName: String): String = "${apkNameFor(tagName)}.sha256"

    /**
     * The APK for [tagName], by exact name.
     *
     * Deliberately not "the first asset ending in .apk". Gradle's own output is `app-release.apk`
     * and the workflow renames it precisely so that a downloads folder holding several releases
     * stays legible; accepting that name here would undo the rename's purpose and would also match
     * an asset from a different tag if one were ever attached by hand.
     */
    fun findApk(tagName: String, assets: List<ReleaseAsset>): ReleaseAsset? {
        val expected = apkNameFor(tagName)
        return assets.firstOrNull { it.name == expected }
    }

    /**
     * The checksum for [tagName]'s APK.
     *
     * Only the exact companion file counts. A `.sha256` belonging to some other asset would verify
     * the wrong thing, and "some checksum was present" is not the property that makes an install
     * safe.
     */
    fun findChecksum(tagName: String, assets: List<ReleaseAsset>): ReleaseAsset? {
        val expected = checksumNameFor(tagName)
        return assets.firstOrNull { it.name == expected }
    }
}

/**
 * Reads the hash out of a `sha256sum` file.
 *
 * The workflow writes `sha256sum "$ARTIFACT" > "$ARTIFACT.sha256"`, which produces
 * `<64 hex chars>  <filename>`. A bare hash with no filename is accepted too, since that is the
 * other common shape and refusing it would mean refusing to update over a formatting difference.
 */
object ChecksumFile {

    private val HEX_64 = Regex("^[0-9a-fA-F]{64}$")

    /**
     * The hash, lowercased, or null if the file holds nothing that is one.
     *
     * Null is a hard stop for the caller, never a reason to skip verification: an unverifiable
     * download is not installed.
     */
    fun parse(contents: String): String? {
        for (line in contents.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val candidate = trimmed.substringBefore(' ').substringBefore('\t').removePrefix("\\")
            if (HEX_64.matches(candidate)) return candidate.lowercase()
        }
        return null
    }

    /**
     * Whether a computed digest matches an expected one.
     *
     * Case-insensitive because `sha256sum` writes lowercase and other tools write upper, and
     * length-checked because comparing a truncated or empty string would otherwise be a way for a
     * malformed file to "match".
     */
    fun matches(expected: String?, actual: String?): Boolean {
        if (expected.isNullOrBlank() || actual.isNullOrBlank()) return false
        if (!HEX_64.matches(expected.trim()) || !HEX_64.matches(actual.trim())) return false
        return expected.trim().equals(actual.trim(), ignoreCase = true)
    }
}

/** What a downloaded APK says about itself, read from the file before anything is installed. */
data class ApkIdentity(val packageName: String, val versionCode: Long, val versionName: String?)

/** Whether a downloaded APK may be handed to the system installer. */
sealed interface InstallVerdict {
    data object Allowed : InstallVerdict

    /** The file could not be read as an APK at all. */
    data object Unreadable : InstallVerdict

    /** A different app. Installing it would not be an update of this one. */
    data class WrongPackage(val found: String, val expected: String) : InstallVerdict

    /** Same or older than what is installed. Android would reject it, and it is not an update. */
    data class NotNewer(val found: Long, val installed: Long) : InstallVerdict

    /**
     * Correctly named and newer, but not built by whoever built the copy on this phone.
     *
     * SEC-01. The SHA-256 the download is checked against comes from the same release the file does,
     * so it proves integrity in transit and says nothing about origin. This is the check that does.
     */
    data object WrongSignature : InstallVerdict
}

/**
 * The last check before the installer is invoked.
 *
 * The version comparison used to choose *whether to offer* an update is made against release tags;
 * this one is made against the bytes that were actually downloaded, which is a different claim and
 * the one that matters at install time. A release mis-tagged, an asset attached to the wrong
 * release, or a redirect to some other project's file all fail here rather than at the system
 * prompt.
 */
object ApkSafety {

    /**
     * @param signedByThisApp whether the download carries the certificate the installed app does,
     *   or `null` if that could not be read. **SEC-01.** The download was verified only against a
     *   SHA-256 published on the same channel it came from, which proves the file was not altered
     *   in transit and nothing at all about who built it: a compromised repository or CI serves a
     *   matching pair and reaches the user's install prompt described as verified. Android will
     *   refuse to *install* a differently-signed update, so the practical outcome was a confusing
     *   failure at the last step rather than a silent compromise -- but "the OS will catch it" is
     *   not the same as checking, and the check costs one `PackageManager` call.
     */
    fun verdict(
        expectedPackage: String,
        installedVersionCode: Long,
        downloaded: ApkIdentity?,
        signedByThisApp: Boolean?,
    ): InstallVerdict = when {
        downloaded == null -> InstallVerdict.Unreadable
        downloaded.packageName != expectedPackage ->
            InstallVerdict.WrongPackage(downloaded.packageName, expectedPackage)
        downloaded.versionCode <= installedVersionCode ->
            InstallVerdict.NotNewer(downloaded.versionCode, installedVersionCode)
        // Ordered last of the rejections deliberately: package and version are things a user can
        // understand and act on, and a signature mismatch on a correctly named, newer build is the
        // one that means something is wrong upstream rather than with the file they picked.
        signedByThisApp != true -> InstallVerdict.WrongSignature
        else -> InstallVerdict.Allowed
    }
}
