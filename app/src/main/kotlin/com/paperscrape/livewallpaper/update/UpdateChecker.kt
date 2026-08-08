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
            val releasePageUrl = json.optString("html_url", "https://github.com/$OWNER/$REPO/releases")
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
}
