package com.paperscrape.livewallpaper.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** How far a download has got, or how it ended. */
sealed interface UpdateDownloadResult {

    /** Downloaded, hashed, and the hash matched the release's own checksum file. */
    data class Verified(val apk: File) : UpdateDownloadResult

    /** The release has no APK named the way the workflow names them. */
    data object NoApkAsset : UpdateDownloadResult

    /**
     * The APK is there but its checksum file is not.
     *
     * A hard stop, not a warning. The alternative is installing a file whose integrity nothing
     * established, which is the one thing this whole path exists to avoid.
     */
    data object NoChecksumAsset : UpdateDownloadResult

    /** The bytes that arrived are not the bytes the release published. */
    data class ChecksumMismatch(val expected: String, val actual: String) : UpdateDownloadResult

    /** Offline, timed out, an error response, a truncated transfer. */
    data object Failed : UpdateDownloadResult
}

/**
 * Downloads a release's APK and proves it is the file the release published, before anything is
 * offered to the system installer.
 *
 * Uses `HttpURLConnection` and no new library, the same as [UpdateChecker] and
 * [com.paperscrape.livewallpaper.weather.WeatherRepository]: the app still has no HTTP client
 * dependency. The download goes to the app's own cache directory, which means the system can
 * reclaim it and a failed or abandoned attempt costs nothing permanent.
 */
object ApkDownloader {

    private const val CONNECT_TIMEOUT_MS = 15000
    private const val READ_TIMEOUT_MS = 30000
    private const val BUFFER_BYTES = 64 * 1024

    /** Where downloads live. One file, replaced each time, so old attempts cannot accumulate. */
    fun apkFileFor(context: Context, tagName: String): File =
        File(updateCacheDir(context), ReleaseAssets.apkNameFor(tagName))

    private fun updateCacheDir(context: Context): File =
        File(context.cacheDir, "updates").apply { mkdirs() }

    /** Clears any previously downloaded APK. Called before a download and after an install hand-off. */
    fun clearCache(context: Context) {
        updateCacheDir(context).listFiles()?.forEach { it.delete() }
    }

    /**
     * Fetches the checksum first, then the APK, then compares.
     *
     * The checksum comes first deliberately: if it is missing there is no point spending a user's
     * data on an APK that could not be installed anyway.
     *
     * [onProgress] receives 0..100, or -1 while the total size is unknown (the server sent no
     * `Content-Length`). It is called from a background dispatcher; callers marshal to the UI.
     */
    suspend fun downloadAndVerify(
        context: Context,
        info: UpdateInfo,
        onProgress: (Int) -> Unit,
    ): UpdateDownloadResult = withContext(Dispatchers.IO) {
        val apkAsset = info.apkAsset ?: return@withContext UpdateDownloadResult.NoApkAsset
        val checksumAsset = info.checksumAsset ?: return@withContext UpdateDownloadResult.NoChecksumAsset

        val checksumText = fetchText(checksumAsset.downloadUrl) ?: return@withContext UpdateDownloadResult.Failed
        val expected = ChecksumFile.parse(checksumText) ?: return@withContext UpdateDownloadResult.NoChecksumAsset

        clearCache(context)
        val target = apkFileFor(context, info.tagName)
        val actual = downloadHashing(apkAsset.downloadUrl, target, onProgress)
            ?: return@withContext UpdateDownloadResult.Failed

        if (!ChecksumFile.matches(expected, actual)) {
            // A file that failed verification is deleted rather than left on disk: nothing should
            // be able to reach the installer by pointing at a leftover.
            target.delete()
            return@withContext UpdateDownloadResult.ChecksumMismatch(expected, actual)
        }
        UpdateDownloadResult.Verified(target)
    }

    private fun fetchText(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = open(url)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Streams the download to [target] while hashing it in the same pass, and returns the digest.
     *
     * Hashing as the bytes arrive rather than re-reading the finished file keeps a ~19 MB APK from
     * being read twice, and means the digest describes exactly what was written.
     */
    private suspend fun downloadHashing(url: String, target: File, onProgress: (Int) -> Unit): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = open(url)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val total = connection.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            var lastReported = -1

            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        // Cancelling the screen cancels the transfer; a partial file is deleted by
                        // the caller's next attempt, which clears the cache first.
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        val percent = if (total > 0) ((downloaded * 100) / total).toInt() else -1
                        if (percent != lastReported) {
                            lastReported = percent
                            onProgress(percent)
                        }
                    }
                }
            }
            // A transfer that ended early still produces a digest, and it will not match -- but
            // catching it here says "download failed" rather than "the release is corrupt".
            if (total > 0 && downloaded != total) {
                target.delete()
                return null
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            target.delete()
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PaperScrape-Updater")
            setRequestProperty("Accept", "application/octet-stream")
        }
}

/**
 * Hands a verified APK to Android's own installer.
 *
 * **Nothing here installs anything.** Android shows its own confirmation, the user accepts or
 * declines it, and declining is a normal outcome that leaves the app exactly as it was. There is
 * no silent-install path and no attempt to find one: an app that sideloads its own updates without
 * the user seeing the system prompt is malware behaviour regardless of intent.
 */
object ApkInstaller {

    /**
     * Whether this app is currently allowed to ask the system to install a package.
     *
     * Since API 26 the "unknown sources" decision is per-app rather than global, so it is a
     * question with an answer here rather than a device-wide setting to send the user hunting for.
     */
    fun canRequestInstalls(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** The settings page where the user grants that permission, scoped to this app. */
    fun installPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).setData("package:${context.packageName}".toUri())

    /** What the downloaded file claims to be, read without installing it. */
    fun identify(context: Context, apk: File): ApkIdentity? {
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0) ?: return null
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        return ApkIdentity(info.packageName, versionCode, info.versionName)
    }

    /** This app's own installed version code, to compare a download against. */
    fun installedVersionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    /**
     * Opens the system installer for [apk].
     *
     * The file lives in the app's cache, which no other process can read by path, so it is shared
     * through a `FileProvider` content URI with a read grant attached to the intent -- the only
     * way to pass a file to the installer since `file://` URIs were banned in API 24.
     */
    fun launchInstall(context: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun String.toUri(): Uri = Uri.parse(this)
}
