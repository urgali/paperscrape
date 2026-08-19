package com.paperscrape.livewallpaper.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * An app version as the release scheme states it: `MAJOR.MINOR`, matching `versionName` and the
 * Git tag that names it.
 *
 * **Not `versionCode`.** That is Android's install counter and answers a different question --
 * "is this newer than what is installed" -- while this answers "which release is this". They were
 * the same number until the semver tag scheme arrived, and conflating them is what left the
 * updater unable to read its own releases.
 */
data class AppVersion(val major: Int, val minor: Int) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int =
        if (major != other.major) major.compareTo(other.major) else minor.compareTo(other.minor)

    override fun toString(): String = "$major.$minor"

    companion object {
        /**
         * `MAJOR.MINOR`, and nothing else.
         *
         * Deliberately strict, and not merely for tidiness. This repository's pre-release history
         * used bare integer tags -- `v73`, `v74` -- and accepting one would read it as major 73,
         * which is *newer* than 1.0: the app would offer every user an "update" to a build that
         * predates the release scheme entirely. Rejecting anything that is not two numbers is what
         * makes an old or hand-created tag invisible rather than dangerous.
         *
         * A `v` prefix is optional, so the same parser reads a Git tag and a `versionName`.
         */
        fun parse(raw: String): AppVersion? {
            val text = raw.trim().removePrefix("v")
            val parts = text.split('.')
            if (parts.size != 2) return null
            val major = parts[0].toIntOrNull() ?: return null
            val minor = parts[1].toIntOrNull() ?: return null
            if (major < 0 || minor < 0) return null
            return AppVersion(major, minor)
        }
    }
}

/** A newer version found on GitHub, ready to show in the update prompt. */
data class UpdateInfo(
    val tagName: String,      // e.g. "v1.1" -- the *latest* release, not necessarily the only new one
    val version: AppVersion,  // parsed from tagName, e.g. 1.1
    val releasePageUrl: String, // GitHub release page — where "Update now" sends the user
    val releaseNotes: String?, // combined "what's new" across *every* release newer than the user's, not just the latest
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
    // The *list* endpoint (not /releases/latest) -- deliberately, so a user several versions
    // behind sees what changed in *every* release between theirs and the newest, not just the
    // newest one's own notes (e.g. updating from v36 to v38 should also show what v37 changed).
    private const val API_URL = "https://api.github.com/repos/$OWNER/$REPO/releases"

    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 8000

    // Defensive cap on the *combined* notes across every included release, so a user many
    // versions behind can't end up with an unbounded wall of text in the update dialog.
    private const val MAX_COMBINED_NOTES_CHARS = 6000

    /**
     * Returns [UpdateInfo] if a newer version is available, or null if not (including on any
     * network/parsing failure — this must never crash or interrupt app startup, so every failure
     * mode just means "no update prompt this time" rather than an error shown to the user).
     */
    suspend fun checkForUpdate(currentVersionName: String): UpdateInfo? = withContext(Dispatchers.IO) {
        val current = AppVersion.parse(currentVersionName) ?: return@withContext null
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
            val releases = JSONArray(body)
            val fallbackReleasePageUrl = "https://github.com/$OWNER/$REPO/releases"

            // Parse every release into (version, tagName, htmlUrl, notes), skipping any entry
            // whose tag is not `vMAJOR.MINOR` -- a hand-created release, or one of the bare
            // integer tags this project used before the semver scheme, is ignored rather than
            // misread. See [AppVersion.parse].
            data class ParsedRelease(val version: AppVersion, val tagName: String, val htmlUrl: String, val notes: String?)
            val parsed = (0 until releases.length()).mapNotNull { i ->
                val entry = releases.getJSONObject(i)
                val tagName = entry.optString("tag_name", "").ifBlank { return@mapNotNull null }
                val version = AppVersion.parse(tagName) ?: return@mapNotNull null
                val htmlUrl = sanitizeGitHubUrl(entry.optString("html_url", fallbackReleasePageUrl)) ?: fallbackReleasePageUrl
                val notes = entry.optString("body", "").trim().ifBlank { null }
                ParsedRelease(version, tagName, htmlUrl, notes)
            }
            if (parsed.isEmpty()) return@withContext null

            val latest = parsed.maxByOrNull { it.version } ?: return@withContext null
            if (latest.version <= current) return@withContext null

            // Every release strictly newer than what the user has, newest first -- each one's
            // own release-notes/vMAJOR.MINOR.md content (see .github/workflows/android-build.yml),
            // so it's plain-language "what's new for you" text, not the technical CHANGELOG.md.
            val newerReleases = parsed.filter { it.version > current }
                .sortedByDescending { it.version }
            val combinedNotes = newerReleases
                .mapNotNull { r -> r.notes?.let { "${r.tagName}\n$it" } }
                .joinToString("\n\n")
                .take(MAX_COMBINED_NOTES_CHARS)
                .ifBlank { null }

            UpdateInfo(
                tagName = latest.tagName,
                version = latest.version,
                releasePageUrl = latest.htmlUrl,
                releaseNotes = combinedNotes,
            )
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
