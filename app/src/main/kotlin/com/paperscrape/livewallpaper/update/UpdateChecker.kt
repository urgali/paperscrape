package com.paperscrape.livewallpaper.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
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
    // The two files the release workflow publishes, when they are both there. Null means this
    // release cannot be installed from inside the app -- the user is sent to the release page
    // instead, which is where every update went before v2.11.
    val apkAsset: ReleaseAsset? = null,
    val checksumAsset: ReleaseAsset? = null,
) {
    /** Whether the in-app download/verify/install path is available for this release. */
    val isInstallable: Boolean get() = apkAsset != null && checksumAsset != null
}

/**
 * What a check for updates actually found out, which is three answers and not two.
 *
 * Until v3.1 this was a nullable [UpdateInfo], and null meant both "there is nothing newer" and
 * "the question was never answered" -- offline, DNS failure, timeout, 403, unexpected JSON. For
 * the silent check at launch those collapse correctly: neither is a reason to interrupt anybody.
 * For the button the user has just pressed they do not, and the screen said **"You're up to
 * date"** in aeroplane mode, which is a claim the app had no basis for.
 */
sealed interface UpdateCheckResult {

    /** A newer release exists. */
    data class Available(val info: UpdateInfo) : UpdateCheckResult

    /** GitHub answered, and nothing there is newer than what is installed. */
    data object UpToDate : UpdateCheckResult

    /**
     * The check did not complete, so nothing at all is known about whether an update exists.
     *
     * [reason] is carried so the explicit path can say *which* wall it hit -- "no connection" and
     * "GitHub answered 403" send the user to different places -- while the automatic path can go
     * on ignoring all of them equally.
     */
    data class Unreachable(val reason: Reason) : UpdateCheckResult {

        enum class Reason {
            /** No network, DNS failure, connection refused, or a timeout. */
            NO_CONNECTION,

            /** A reply arrived and it was not 200: rate limiting, a renamed repo, an outage. */
            SERVER_ERROR,

            /** A 200 whose body was not the JSON this expects. */
            UNREADABLE_RESPONSE,
        }

        /** One sentence for the settings row, in the app's own voice. */
        val message: String
            get() = when (reason) {
                Reason.NO_CONNECTION ->
                    "Couldn't check - no connection. Your version may or may not be current."
                Reason.SERVER_ERROR ->
                    "Couldn't check - GitHub didn't answer. Try again in a few minutes."
                Reason.UNREADABLE_RESPONSE ->
                    "Couldn't check - the reply from GitHub wasn't readable."
            }
    }
}

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
     * Asks GitHub which is the newest release, and says which of the three things happened.
     *
     * Never throws: every failure is an [UpdateCheckResult.Unreachable] with a reason, so a caller
     * that wants to stay silent can, and a caller that has to answer the user can say something
     * true. The two callers do exactly that -- `SettingsScreen`'s launch check acts on
     * [UpdateCheckResult.Available] and ignores everything else, `AdvancedScreen`'s button reports
     * all three.
     */
    suspend fun checkForUpdate(
        currentVersionName: String,
        /**
         * Overridden only by `UpdateCheckOutcomeTest`, which stands a `HttpServer` on a loopback
         * port so the three outcomes can be produced for real -- a 200 with releases, a 403, a
         * body that is not JSON, and a port with nothing listening -- rather than asserted about
         * a mock of this function. Every caller in the app uses the default.
         */
        apiUrl: String = API_URL,
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        // An unparsable installed version is not a network problem and not "up to date": there is
        // nothing to compare against, so no update can be offered and none can be ruled out.
        val current = AppVersion.parse(currentVersionName)
            ?: return@withContext UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.UNREADABLE_RESPONSE)
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                // GitHub's API rejects requests with no User-Agent (403), and this is the
                // documented Accept header for the REST API's stable response format.
                setRequestProperty("User-Agent", "PaperScrape-UpdateChecker")
                setRequestProperty("Accept", "application/vnd.github+json")
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.SERVER_ERROR)
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val releases = JSONArray(body)
            val fallbackReleasePageUrl = "https://github.com/$OWNER/$REPO/releases"

            // Parse every release into (version, tagName, htmlUrl, notes), skipping any entry
            // whose tag is not `vMAJOR.MINOR` -- a hand-created release, or one of the bare
            // integer tags this project used before the semver scheme, is ignored rather than
            // misread. See [AppVersion.parse].
            data class ParsedRelease(
                val version: AppVersion,
                val tagName: String,
                val htmlUrl: String,
                val notes: String?,
                val assets: List<ReleaseAsset>,
            )
            val parsed = (0 until releases.length()).mapNotNull { i ->
                val entry = releases.getJSONObject(i)
                val tagName = entry.optString("tag_name", "").ifBlank { return@mapNotNull null }
                val version = AppVersion.parse(tagName) ?: return@mapNotNull null
                val htmlUrl = sanitizeGitHubUrl(entry.optString("html_url", fallbackReleasePageUrl)) ?: fallbackReleasePageUrl
                val notes = entry.optString("body", "").trim().ifBlank { null }
                val assets = entry.optJSONArray("assets")?.let { array ->
                    (0 until array.length()).mapNotNull { index ->
                        val asset = array.optJSONObject(index) ?: return@mapNotNull null
                        val name = asset.optString("name", "").ifBlank { return@mapNotNull null }
                        val url = sanitizeGitHubUrl(asset.optString("browser_download_url", ""))
                            ?: return@mapNotNull null
                        ReleaseAsset(name, url, asset.optLong("size", 0L))
                    }
                }.orEmpty()
                ParsedRelease(version, tagName, htmlUrl, notes, assets)
            }
            // A 200 with no release this parser recognises is not an error and not an update:
            // it is a repository with nothing published under the `vMAJOR.MINOR` scheme, which is
            // exactly "there is nothing newer than what you have".
            if (parsed.isEmpty()) return@withContext UpdateCheckResult.UpToDate

            val latest = parsed.maxByOrNull { it.version } ?: return@withContext UpdateCheckResult.UpToDate
            if (latest.version <= current) return@withContext UpdateCheckResult.UpToDate

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

            UpdateCheckResult.Available(
                UpdateInfo(
                    tagName = latest.tagName,
                    version = latest.version,
                    releasePageUrl = latest.htmlUrl,
                    releaseNotes = combinedNotes,
                    apkAsset = ReleaseAssets.findApk(latest.tagName, latest.assets),
                    checksumAsset = ReleaseAssets.findChecksum(latest.tagName, latest.assets),
                ),
            )
        } catch (_: IOException) {
            // No network, DNS failure, connection refused, a socket timeout: the request never
            // got an answer. Kept apart from the parse failure below because it is the one the
            // user can do something about, and the one aeroplane mode produces.
            UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.NO_CONNECTION)
        } catch (_: Exception) {
            // A reply arrived and could not be read -- unexpected JSON shape, a truncated body.
            // Still "nothing is known", still never a crash, but not the user's connection.
            UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.UNREADABLE_RESPONSE)
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
