package com.paperscrape.livewallpaper.update

import com.paperscrape.livewallpaper.MAX_HTTP_BODY_CHARS
import com.paperscrape.livewallpaper.readAtMost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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
    val releasePageUrl: String, // GitHub release page: the two "Open release page" buttons
    val releaseNotes: String?, // "what's new" of *every* release newer than the user's, whole, newest first (v5.10D: no longer cut)
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

            /**
             * A reply arrived and it was not 200 (rate limiting, a renamed repo, an outage), or it
             * was too long to read.
             */
            SERVER_ERROR,

            /**
             * A 200 whose body was not the JSON this expects, or an installed version name that
             * cannot be compared.
             */
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
 * What the last check GitHub answered left behind, kept so that the next one can ask GitHub "has
 * anything changed?" instead of downloading the list again (v5.10D, the maintainer's decision of
 * 2026-09-30 on row 16 of the v5.10A table).
 *
 * [etag] is the `ETag` of the list's first page. Sent back as `If-None-Match`, it makes GitHub answer
 * **304 with no body** while the page is the same, and then [result] -- what this app worked out from
 * that page for [forVersionName] -- is still the answer: no newer release has appeared, and none has
 * been edited. It is kept only for the installed version it was worked out for, because "nothing
 * newer than 5.8" stops being true the day 5.9 is installed, and only for the address it came from.
 *
 * [result] is [UpdateCheckResult.UpToDate] or [UpdateCheckResult.Available], never an unreachable:
 * a check that was not answered leaves nothing to keep.
 */
data class SavedUpdateReply(
    val etag: String,
    val forVersionName: String,
    val apiUrl: String,
    val result: UpdateCheckResult,
) {

    /** One string, for one preference: the reply and what it is about are written together. */
    fun toJson(): String = JSONObject().apply {
        put("etag", etag)
        put("forVersionName", forVersionName)
        put("apiUrl", apiUrl)
        when (result) {
            is UpdateCheckResult.Available -> put("available", result.info.toJson())
            else -> put("upToDate", true)
        }
    }.toString()

    companion object {

        /** The reply [toJson] wrote, or null for anything else -- which only costs a full download. */
        fun fromJson(text: String?): SavedUpdateReply? = runCatching {
            val json = JSONObject(text ?: return null)
            val etag = json.optString("etag").ifBlank { return null }
            val result: UpdateCheckResult = when {
                json.has("available") -> UpdateCheckResult.Available(updateInfoFromJson(json.getJSONObject("available")) ?: return null)
                json.optBoolean("upToDate", false) -> UpdateCheckResult.UpToDate
                else -> return null
            }
            SavedUpdateReply(etag, json.getString("forVersionName"), json.getString("apiUrl"), result)
        }.getOrNull()

        private fun UpdateInfo.toJson(): JSONObject = JSONObject().apply {
            put("tagName", tagName)
            put("releasePageUrl", releasePageUrl)
            releaseNotes?.let { put("releaseNotes", it) }
            apkAsset?.let { put("apk", it.toJson()) }
            checksumAsset?.let { put("checksum", it.toJson()) }
        }

        private fun ReleaseAsset.toJson(): JSONObject =
            JSONObject().put("name", name).put("url", downloadUrl).put("size", sizeBytes)

        private fun updateInfoFromJson(json: JSONObject): UpdateInfo? {
            val tagName = json.getString("tagName")
            val version = AppVersion.parse(tagName) ?: return null
            return UpdateInfo(
                tagName = tagName,
                version = version,
                releasePageUrl = json.getString("releasePageUrl"),
                releaseNotes = if (json.has("releaseNotes")) json.getString("releaseNotes") else null,
                apkAsset = json.optJSONObject("apk")?.let(::assetFromJson),
                checksumAsset = json.optJSONObject("checksum")?.let(::assetFromJson),
            )
        }

        private fun assetFromJson(json: JSONObject): ReleaseAsset =
            ReleaseAsset(json.getString("name"), json.getString("url"), json.optLong("size", 0L))
    }
}

/**
 * Where a [SavedUpdateReply] is kept between checks: `UpdatePrefs` in the app, a field in the tests.
 * Both calls may fail; a reply that cannot be read or written only costs the next check a full
 * download.
 */
interface UpdateReplyStore {
    suspend fun readReply(): SavedUpdateReply?
    suspend fun writeReply(reply: SavedUpdateReply)
}

/**
 * Checks the public GitHub Releases API for a newer version than the one currently installed.
 *
 * IMPORTANT: [OWNER]/[REPO] must match your actual GitHub repository, or this will either find
 * nothing (wrong repo = 404: silent on the automatic checks, reported by the button as "GitHub
 * didn't answer") or compare against the wrong project entirely.
 * Double check these two constants after forking/renaming the repo.
 */
object UpdateChecker {

    private const val OWNER = "urgali"
    private const val REPO = "paperscrape"
    // The *list* endpoint (not /releases/latest) -- deliberately, so a user several versions
    // behind sees what changed in *every* release between theirs and the newest, not just the
    // newest one's own notes (e.g. updating from v36 to v38 should also show what v37 changed).
    // GitHub gives it in pages of 30, newest first; [checkForUpdate] follows the pages back to the
    // installed version (v5.10D).
    private const val API_URL = "https://api.github.com/repos/$OWNER/$REPO/releases"

    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 8000

    /**
     * How many pages of the list one check may read: the first, and the older ones it needs to
     * reach the installed version. 69 releases were three pages of 30 on 2026-10-03, so ten is 300
     * releases -- a bound against a reply that keeps naming a next page, not a limit anyone meets.
     */
    internal const val MAX_PAGES = 10

    /**
     * Asks GitHub which is the newest release, and says which of the three things happened.
     *
     * Never throws: every failure is an [UpdateCheckResult.Unreachable] with a reason, so a caller
     * that wants to stay silent can, and a caller that has to answer the user can say something
     * true. The three callers do exactly that -- `SettingsScreen`'s check and the wallpaper
     * engine's check every three hours act on [UpdateCheckResult.Available] and ignore the rest (the
     * engine only retries sooner after [UpdateCheckResult.Unreachable]), `AdvancedScreen`'s button
     * reports all three.
     *
     * ### "Has anything changed?" (v5.10D, row 16)
     *
     * With a [store], the first page is asked for with the `ETag` of the last reply kept for this
     * installed version (`If-None-Match`). While nothing has changed GitHub answers **304 with no
     * body**, and the kept answer is returned: nothing is downloaded. Until v5.10D every check
     * downloaded the whole first page -- 252 KB to read, ~51 KB on the wire, measured 2026-10-03 --
     * and the engine checks every three hours since the same round. A new release, an edited one,
     * or a download counted on one of the thirty releases of the first page (the list carries each
     * file's download count) changes the page, and the answer is worked out again.
     *
     * ### Every release between the installed one and the newest, whole (v5.10D, inventory I-290)
     *
     * The maintainer's words of 2026-09-30: *«deve scaricare tutto il changelog di diff tra la versione
     * installata e l'ultima disponibile come fix, non solo l'ultima»*. The notes of every release newer
     * than the installed one, newest first, each as its release publishes it. Until v5.10D they were
     * cut at 6 000 characters -- `release-notes/v5.8.md` alone is 7 186, so a user on 5.7 read 5.8's
     * cut in the middle -- and only the first page was read, so a user more than 29 releases behind
     * never saw the oldest of theirs. Now the pages are followed until one names the installed
     * version or an older one (at most [MAX_PAGES]), and nothing is cut: the dialog scrolls.
     */
    suspend fun checkForUpdate(
        currentVersionName: String,
        /** Where the last answered reply is kept; null asks without a condition and keeps nothing. */
        store: UpdateReplyStore? = null,
        /**
         * Overridden only by the tests (`UpdateCheckOutcomeTest`, `UpdateCheckConditionalTest`,
         * `UpdateNotesWholeTest`), which stand a `HttpServer` on a loopback port so the outcomes can be
         * produced for real -- a 200 with releases, a 304, a 403, a body that is not JSON, and a port
         * with nothing listening -- rather than asserted about a mock of this function. Every caller
         * in the app uses the default.
         */
        apiUrl: String = API_URL,
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        // An unparsable installed version is not a network problem and not "up to date": there is
        // nothing to compare against, so no update can be offered and none can be ruled out.
        val current = AppVersion.parse(currentVersionName)
            ?: return@withContext UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.UNREADABLE_RESPONSE)
        val saved = runCatching { store?.readReply() }.getOrNull()
            ?.takeIf { it.forVersionName == currentVersionName && it.apiUrl == apiUrl }
        try {
            val parsed = mutableListOf<ParsedRelease>()
            var etag: String? = null
            var url: String? = apiUrl
            var pages = 0
            while (url != null && pages < MAX_PAGES) {
                val page = fetchPage(url, ifNoneMatch = if (pages == 0) saved?.etag else null)
                when (page) {
                    // Only the first page is asked conditionally, so only there can this arrive -- and
                    // only with a reply kept to answer for it. A 304 to an unconditional request is a
                    // reply this app cannot read.
                    PageReply.NotModified -> return@withContext saved?.result
                        ?: UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.SERVER_ERROR)
                    is PageReply.Failed -> return@withContext UpdateCheckResult.Unreachable(page.reason)
                    is PageReply.Releases -> {
                        if (pages == 0) etag = page.etag
                        parsed += page.releases
                        // Older pages are needed only until one names the installed version or an
                        // older one: from there on every release is one the user already has.
                        url = if (page.releases.any { it.version <= current }) null else page.nextUrl?.takeIf { sameHost(it, apiUrl) }
                    }
                }
                pages++
            }
            val result = answerFor(parsed, current)
            if (store != null && etag != null) {
                runCatching { store.writeReply(SavedUpdateReply(etag, currentVersionName, apiUrl, result)) }
            }
            result
        } catch (_: IOException) {
            // No network, DNS failure, connection refused, a socket timeout: the request never
            // got an answer. Kept apart from the parse failure below because it is the one the
            // user can do something about, and the one aeroplane mode produces.
            UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.NO_CONNECTION)
        } catch (_: Exception) {
            // A reply arrived and could not be read -- unexpected JSON shape, a truncated body.
            // Still "nothing is known", still never a crash, but not the user's connection.
            UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.UNREADABLE_RESPONSE)
        }
    }

    /** One release of the list, as far as this app reads it. */
    internal data class ParsedRelease(
        val version: AppVersion,
        val tagName: String,
        val htmlUrl: String,
        val notes: String?,
        val assets: List<ReleaseAsset>,
    )

    private sealed interface PageReply {
        data class Releases(val releases: List<ParsedRelease>, val etag: String?, val nextUrl: String?) : PageReply
        data object NotModified : PageReply
        data class Failed(val reason: UpdateCheckResult.Unreachable.Reason) : PageReply
    }

    private fun fetchPage(url: String, ifNoneMatch: String?): PageReply {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                // GitHub's API rejects requests with no User-Agent (403), and this is the
                // documented Accept header for the REST API's stable response format.
                setRequestProperty("User-Agent", "PaperScrape-UpdateChecker")
                setRequestProperty("Accept", "application/vnd.github+json")
                if (ifNoneMatch != null) setRequestProperty("If-None-Match", ifNoneMatch)
            }
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED && ifNoneMatch != null) return PageReply.NotModified
            if (code != HttpURLConnection.HTTP_OK) return PageReply.Failed(UpdateCheckResult.Unreachable.Reason.SERVER_ERROR)
            // Bounded: one page of 30 releases read 252 KB on 2026-10-03, and an unbounded read is
            // not (SEC-03).
            val body = connection.inputStream.bufferedReader().use { it.readAtMost(MAX_HTTP_BODY_CHARS) }
                ?: return PageReply.Failed(UpdateCheckResult.Unreachable.Reason.SERVER_ERROR)
            return PageReply.Releases(
                releases = parseReleases(body),
                etag = connection.getHeaderField("ETag")?.ifBlank { null },
                nextUrl = nextPageUrl(connection.getHeaderField("Link")),
            )
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Every release of one page, skipping any entry whose tag is not `vMAJOR.MINOR` -- a hand-created
     * release, or one of the bare integer tags this project used before the semver scheme, is
     * ignored rather than misread. See [AppVersion.parse]. Throws on a body that is not the JSON
     * array GitHub sends, which the caller reads as an unreadable reply.
     */
    internal fun parseReleases(body: String): List<ParsedRelease> {
        val releases = JSONArray(body)
        val fallbackReleasePageUrl = "https://github.com/$OWNER/$REPO/releases"
        return (0 until releases.length()).mapNotNull { i ->
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
    }

    /**
     * What the releases read say to a user on [current].
     *
     * No release under the scheme is not an error and not an update: it is a repository with nothing
     * published under `vMAJOR.MINOR`, which is exactly "there is nothing newer than what you have".
     */
    internal fun answerFor(parsed: List<ParsedRelease>, current: AppVersion): UpdateCheckResult {
        val latest = parsed.maxByOrNull { it.version } ?: return UpdateCheckResult.UpToDate
        if (latest.version <= current) return UpdateCheckResult.UpToDate
        return UpdateCheckResult.Available(
            UpdateInfo(
                tagName = latest.tagName,
                version = latest.version,
                releasePageUrl = latest.htmlUrl,
                releaseNotes = combinedNotes(parsed, current),
                apkAsset = ReleaseAssets.findApk(latest.tagName, latest.assets),
                checksumAsset = ReleaseAssets.findChecksum(latest.tagName, latest.assets),
            ),
        )
    }

    /**
     * The notes of every release strictly newer than [current], newest first, each under its tag
     * and **whole**: its own release body -- release-notes/vMAJOR.MINOR.md (or a generic line when
     * that file is missing) plus the workflow's short verification footer (see
     * .github/workflows/android-build.yml), so it is plain-language "what's new for you" text, not
     * the technical CHANGELOG.md. A release listed twice (a page boundary moving under the reader)
     * is shown once.
     */
    internal fun combinedNotes(parsed: List<ParsedRelease>, current: AppVersion): String? =
        parsed.filter { it.version > current }
            .distinctBy { it.version }
            .sortedByDescending { it.version }
            .mapNotNull { r -> r.notes?.let { "${r.tagName}\n$it" } }
            .joinToString("\n\n")
            .ifBlank { null }

    /**
     * [combinedNotes] cut back into one block per release, for the update dialog's lazy list
     * (`SettingsScreen`, v5.10D): each block starts at a line that is a release's tag alone, which is
     * how [combinedNotes] joins them. Lossless -- the blocks joined with a blank line are the notes --
     * so the dialog shows exactly what was combined, whole.
     */
    internal fun notesByRelease(combined: String): List<String> =
        combined.split(Regex("""\n\n(?=v\d+\.\d+\n)"""))

    /**
     * The `rel="next"` address of a GitHub `Link` header -- `<https://...?page=2>; rel="next", <...>;
     * rel="last"` -- or null on the last page, or with no header.
     */
    internal fun nextPageUrl(link: String?): String? =
        link?.let { Regex("""<([^>]+)>\s*;\s*rel="next"""").find(it)?.groupValues?.get(1) }

    /** Only a next page on the host the first one came from is followed. */
    private fun sameHost(next: String, first: String): Boolean = runCatching {
        val a = java.net.URI(next)
        val b = java.net.URI(first)
        a.scheme.equals(b.scheme, ignoreCase = true) && a.host.equals(b.host, ignoreCase = true) && a.port == b.port
    }.getOrDefault(false)

    /**
     * Returns [url] unchanged if it's a plain `https://github.com/...` (or `www.github.com`)
     * URL, or null otherwise. This is deliberately strict -- no subdomains, no other schemes --
     * since its outputs are only ever opened in a browser via `Intent.ACTION_VIEW` (release pages)
     * or fetched by [ApkDownloader] (asset URLs, whose redirect off GitHub is followed and not
     * checked). GitHub's own API response is the input here; scoping this tightly
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
