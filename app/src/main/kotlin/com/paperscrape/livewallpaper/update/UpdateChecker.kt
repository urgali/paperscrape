package com.paperscrape.livewallpaper.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A newer version found on GitHub, ready to show in the update prompt. */
data class UpdateInfo(
    val tagName: String,      // e.g. "v13"
    val versionCode: Int,     // parsed from tagName, e.g. 13
    val releasePageUrl: String, // GitHub release page — where "Update now" sends the user
)

/**
 * Checks the public GitHub Releases API for a newer version than the one currently installed.
 *
 * IMPORTANT: [OWNER]/[REPO] must match your actual GitHub repository, or this will either find
 * nothing (wrong repo = 404, fails silently) or compare against the wrong project entirely.
 * Double check these two constants after forking/renaming the repo.
 */
object UpdateChecker {

    private const val OWNER = "urgali"
    private const val REPO = "paperscrape"
    private const val API_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 8000

    /**
     * Returns [UpdateInfo] if a newer version is available, or null if not (including on any
     * network/parsing failure — this must never crash or interrupt app startup, so every failure
     * mode just means "no update prompt this time" rather than an error shown to the user).
     */
    suspend fun checkForUpdate(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                // GitHub's API rejects requests with no User-Agent (403), and this is the
                // documented Accept header for the REST API's stable response format.
                setRequestProperty("User-Agent", "PaperScrape-UpdateChecker")
                setRequestProperty("Accept", "application/vnd.github+json")
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "").ifBlank { return@withContext null }
            val fallbackReleasePageUrl = "https://github.com/$OWNER/$REPO/releases"
            // html_url comes from GitHub's response body, not from us -- treat it as untrusted
            // remote data. It's only ever used to open a browser (never fetched, never rendered
            // in a WebView), but an unvalidated scheme/host could still be abused to trigger an
            // unexpected app via ACTION_VIEW (e.g. a non-http(s) URI scheme). Accept it only if
            // it's exactly what we expect a GitHub release page to look like; otherwise fall back
            // to a URL we constructed ourselves.
            val releasePageUrl = sanitizeGitHubUrl(json.optString("html_url", fallbackReleasePageUrl))
                ?: fallbackReleasePageUrl
            val latestVersionCode = tagName.removePrefix("v").toIntOrNull() ?: return@withContext null

            if (latestVersionCode > currentVersionCode) {
                UpdateInfo(tagName = tagName, versionCode = latestVersionCode, releasePageUrl = releasePageUrl)
            } else {
                null
            }
        } catch (_: Exception) {
            // No internet, DNS failure, GitHub down, unexpected JSON shape, etc. -- all treated
            // the same: skip the prompt this launch, try again next time.
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Returns [url] unchanged if it's a plain `https://github.com/...` (or `www.github.com`)
     * URL, or null otherwise. This is deliberately strict -- no subdomains, no other schemes --
     * since the only legitimate use is opening a GitHub release page in a browser via
     * `Intent.ACTION_VIEW`. GitHub's own API response is the input here; scoping this tightly
     * means a compromised or malicious response (or a fork pointed at the wrong repo) can't
     * smuggle an unexpected URI scheme into that Intent.
     */
    private fun sanitizeGitHubUrl(url: String): String? {
        val uri = try {
            java.net.URI(url)
        } catch (_: Exception) {
            return null
        }
        val host = uri.host?.lowercase() ?: return null
        val isHttps = uri.scheme?.lowercase() == "https"
        val isGitHubHost = host == "github.com" || host == "www.github.com"
        return if (isHttps && isGitHubHost) url else null
    }
}
