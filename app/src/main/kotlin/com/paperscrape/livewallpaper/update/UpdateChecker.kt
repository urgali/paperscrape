package com.paperscrape.livewallpaper.update

import com.paperscrape.livewallpaper.MAX_HTTP_BODY_CHARS
import com.paperscrape.livewallpaper.readAtMost
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * An app version as the release scheme states it: `MAJOR.MINOR`, matching `versionName` and the
 * Git tag that names it. **Not `versionCode`**, Android's install counter, which answers "is this
 * newer than what is installed" rather than "which release is this".
 */
data class AppVersion(val major: Int, val minor: Int) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int =
        if (major != other.major) major.compareTo(other.major) else minor.compareTo(other.minor)

    override fun toString(): String = "$major.$minor"

    companion object {
        /**
         * `MAJOR.MINOR`, and nothing else, with an optional `v` so it reads a Git tag and a
         * `versionName` alike. Strict because the repository also holds bare integer tags (`v73`,
         * `v74`) older than the release scheme: read as major 73 they would be "newer" than every
         * release, so anything that is not two numbers is invisible.
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
    val releaseNotes: String?, // "what's new" of *every* release newer than the user's, whole, newest first
    // The two files the release workflow publishes, when both are there. Null: this release cannot
    // be installed from inside the app, and the user is sent to the release page.
    val apkAsset: ReleaseAsset? = null,
    val checksumAsset: ReleaseAsset? = null,
) {
    /** Whether the in-app download/verify/install path is available for this release. */
    val isInstallable: Boolean get() = apkAsset != null && checksumAsset != null
}

/**
 * What a check for updates found out: three answers, not two. "Nothing newer" and "not answered"
 * (offline, a timeout, a refusal, unexpected JSON) are alike to the silent check, but not to the
 * button the user just pressed: "You're up to date" in aeroplane mode would be a claim without basis.
 */
sealed interface UpdateCheckResult {

    /** A newer release exists. */
    data class Available(val info: UpdateInfo) : UpdateCheckResult

    /** GitHub answered, and nothing there is newer than what is installed. */
    data object UpToDate : UpdateCheckResult

    /**
     * The check did not complete, so nothing at all is known about whether an update exists.
     *
     * [reason] lets the explicit path say *which* wall it hit ("no connection" and "GitHub is turning
     * this network away" send the user to different places); the automatic path ignores them all.
     * [limitedUntilMillis] comes with [Reason.RATE_LIMITED] alone: the wall-clock moment GitHub said
     * this address may ask again, or 0 when it did not say.
     */
    data class Unreachable(val reason: Reason, val limitedUntilMillis: Long = 0L) : UpdateCheckResult {

        enum class Reason {
            /** No network, DNS failure, connection refused, or a timeout. */
            NO_CONNECTION,

            /**
             * A reply arrived and it was not 200 (a renamed repo, an outage, a refusal that is not the
             * rate limit), or it was too long to read.
             */
            SERVER_ERROR,

            /**
             * GitHub refused because this address has spent its requests: a 429, or a 403 with
             * `Retry-After` or `X-RateLimit-Remaining: 0`. Without credentials the limit is 60 an hour
             * **for the network's public address**, shared with every device behind it, so others may
             * have spent it.
             */
            RATE_LIMITED,

            /**
             * A 200 whose body was not the JSON this expects, or an installed version name that
             * cannot be compared.
             */
            UNREADABLE_RESPONSE,
        }

        /** One sentence for the settings row, in the app's own voice, the time in the phone's own format. */
        val message: String
            get() = message { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) }

        /** [message] with the time written by [time] (the tests give a fixed format). */
        fun message(time: (Long) -> String): String = when (reason) {
            Reason.NO_CONNECTION ->
                "Couldn't check - no connection. Your version may or may not be current."
            Reason.SERVER_ERROR ->
                "Couldn't check - GitHub didn't answer. Try again in a few minutes."
            Reason.RATE_LIMITED -> if (limitedUntilMillis > 0L) {
                "Couldn't check - GitHub is turning away checks from your network for now. Try again after " +
                    "${time(limitedUntilMillis)}."
            } else {
                "Couldn't check - GitHub is turning away checks from your network for now. Try again later."
            }
            Reason.UNREADABLE_RESPONSE ->
                "Couldn't check - the reply from GitHub wasn't readable."
        }
    }
}

/**
 * What the last answered check left behind, so the next one asks GitHub "has anything changed?"
 * instead of downloading the list again.
 *
 * [etag] is the `ETag` of the list's first page. Sent back as `If-None-Match`, GitHub answers **304
 * with no body** while the page is the same, and [result] -- worked out from that page for
 * [forVersionName] -- is still the answer. It holds only for the installed version it was worked out
 * for ("nothing newer than 5.8" is false once 5.9 is installed) and for the address it came from.
 * [result] is [UpdateCheckResult.UpToDate] or [UpdateCheckResult.Available]: an unanswered check
 * leaves nothing to keep.
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
 * download. It also keeps until when GitHub said this network may not ask ([readLimitedUntil], wall
 * clock, 0 for none), before which a check nobody asked for is not made
 * ([UpdateChecker.checkForUpdate]'s `honourLimit`); the defaults keep no limit.
 */
interface UpdateReplyStore {
    suspend fun readReply(): SavedUpdateReply?
    suspend fun writeReply(reply: SavedUpdateReply)
    suspend fun readLimitedUntil(): Long = 0L
    suspend fun writeLimitedUntil(untilMillis: Long) {}
}

/**
 * The one line every update check writes into the phone's log (the maintainer, 2026-10-19: *«D sì,
 * con la riga»*): what came of it, GitHub's HTTP code and how many requests GitHub says this network
 * has left -- nothing else: no address, no version list, no identifier. Written by
 * [UpdateChecker.checkForUpdate] for every caller, the release build included, so a notification that
 * did not arrive can be explained afterwards (`adb logcat -s PaperScrapeUpdate`). [sink] is the
 * platform log; on the JVM, where `android.util.Log` is not implemented, nowhere unless a test listens.
 */
object UpdateCheckLog {
    const val TAG = "PaperScrapeUpdate"

    @Volatile
    var sink: (String) -> Unit = { line -> runCatching { Log.i(TAG, line) } }

    /** The line for a check: [outcome], the last reply's [httpCode] (null: no reply) and [remaining] as GitHub sent it. */
    fun line(outcome: String, httpCode: Int?, remaining: String?): String =
        "update check: $outcome, HTTP ${httpCode ?: "-"}, ${remaining ?: "?"} requests left"

    /** What came of a check, in the log's words; [clock] writes a moment of the wall clock. */
    fun outcome(result: UpdateCheckResult, notModified: Boolean, clock: (Long) -> String = ::clockTime): String = when (result) {
        is UpdateCheckResult.Available -> "${result.info.tagName} available${if (notModified) " (unchanged)" else ""}"
        UpdateCheckResult.UpToDate -> "up to date${if (notModified) " (unchanged)" else ""}"
        is UpdateCheckResult.Unreachable -> when (result.reason) {
            UpdateCheckResult.Unreachable.Reason.NO_CONNECTION -> "no connection"
            UpdateCheckResult.Unreachable.Reason.SERVER_ERROR -> "GitHub did not answer"
            UpdateCheckResult.Unreachable.Reason.RATE_LIMITED ->
                if (result.limitedUntilMillis > 0L) "turned away by GitHub's limit until ${clock(result.limitedUntilMillis)}"
                else "turned away by GitHub's limit"
            UpdateCheckResult.Unreachable.Reason.UNREADABLE_RESPONSE -> "reply not readable"
        }
    }

    internal fun clockTime(millis: Long): String = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date(millis))
}

/**
 * Checks the public GitHub Releases API for a newer version than the one installed.
 *
 * [OWNER]/[REPO] must name the real repository: a wrong one is a 404 (silent on the automatic checks,
 * "GitHub didn't answer" on the button) or another project's releases.
 */
object UpdateChecker {

    private const val OWNER = "urgali"
    private const val REPO = "paperscrape"
    // The *list* endpoint, not /releases/latest, so a user several versions behind sees the notes of
    // every release between theirs and the newest. GitHub gives it in pages of 30, newest first;
    // [checkForUpdate] follows the pages back to the installed version.
    private const val API_URL = "https://api.github.com/repos/$OWNER/$REPO/releases"

    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 8000

    /**
     * How many pages of the list one check may read: the first, and the older ones it needs to
     * reach the installed version. Ten pages are 300 releases: a bound against a reply that keeps
     * naming a next page, not a limit anyone meets.
     */
    internal const val MAX_PAGES = 10

    /**
     * Asks GitHub which is the newest release, and says which of the three things happened.
     *
     * Never throws: every failure is an [UpdateCheckResult.Unreachable] with a reason, so a caller
     * that stays silent can, and one that answers the user can say something true. The update job
     * (`UpdateCheckJobService`, every three hours) acts on [UpdateCheckResult.Available] and ignores
     * the rest (it only tries again sooner after an unreachable, and after GitHub's limit once, when it
     * lifts); `SettingsScreen`'s check does the same when the screen is opened, and reports every
     * outcome when a tapped notification opened it; `AdvancedScreen`'s button reports them all.
     *
     * **GitHub's limit** ([rateLimitedUntil]) is kept in the [store] until the moment GitHub gives;
     * with [honourLimit] no request is made before it, the answer being the same refusal at no cost to
     * the network's requests. A check answered with the list (200, or 304) clears it.
     *
     * ### One request for one moment
     *
     * The callers can want GitHub at one instant: opening the settings after a force stop starts the
     * screen's check while the wallpaper bound again puts the update job back and runs it, and the button
     * can be pressed while the job is checking. So one check is on the network at a time in this process:
     * **a check that finds another on the network waits for it and takes its answer**, whoever asked; and
     * with [takeRecentAnswer] **an answer GitHub gave less than [RECENT_ANSWER_MILLIS] ago stands for it
     * too**. Only a check that reached GitHub (or tried to: no connection) is taken; one the limit kept
     * from asking is not.
     *
     * Every call writes [UpdateCheckLog.line]; a check not made because of the limit says so, and so
     * does one answered by another check.
     *
     * With a [store], the first page is asked for with the kept reply's `ETag` ([SavedUpdateReply]):
     * a 304 returns the kept answer and downloads nothing. A new or edited release, or a download
     * counted on the first page (the list carries each file's download count), changes the page.
     *
     * ### Every release between the installed one and the newest, whole
     *
     * The maintainer, 2026-09-30: *«deve scaricare tutto il changelog di diff tra la versione installata
     * e l'ultima disponibile come fix, non solo l'ultima»*. The notes of every release newer than the
     * installed one, newest first, each as its release publishes it and none cut (the dialog scrolls):
     * the pages are followed until one names the installed version or an older one, at most [MAX_PAGES].
     */
    suspend fun checkForUpdate(
        currentVersionName: String,
        /** Where the last answered reply is kept; null asks without a condition and keeps nothing. */
        store: UpdateReplyStore? = null,
        /**
         * Overridden only by the tests, which stand an `HttpServer` on a loopback port so every outcome
         * (200, 304, 403, GitHub's limit, a body that is not JSON, nothing listening) is produced for
         * real rather than asserted about a mock. Every caller in the app uses the default.
         */
        apiUrl: String = API_URL,
        /**
         * Whether a limit GitHub set ([UpdateReplyStore.readLimitedUntil]) stops the request: true for a
         * check nobody asked for this instant, false for the button the user has just pressed and for a
         * tapped notification.
         */
        honourLimit: Boolean = true,
        /**
         * Whether an answer GitHub gave less than [RECENT_ANSWER_MILLIS] ago, to any caller, stands for this
         * check: true for a check nobody asked for this instant (the settings screen opening, a run of the
         * update job), false for the button and a tapped notification, which ask -- and for the tests, which
         * ask one server several times in a row.
         */
        takeRecentAnswer: Boolean = false,
        /** The wall clock; the tests give their own. */
        nowMillis: () -> Long = System::currentTimeMillis,
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        // An unparsable installed version is not a network problem and not "up to date": there is
        // nothing to compare against, so no update can be offered and none can be ruled out.
        val current = AppVersion.parse(currentVersionName)
            ?: return@withContext UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.UNREADABLE_RESPONSE)
                .also { UpdateCheckLog.sink(UpdateCheckLog.line(UpdateCheckLog.outcome(it, false), null, null)) }
        // Read before waiting: a check of this list that reaches GitHub while this one waits answers for it.
        val seen = lastAsked?.serial
        onTheNetwork.withLock {
            val last = lastAsked?.takeIf { it.apiUrl == apiUrl && it.versionName == currentVersionName }
            if (last != null && (last.serial != seen || (takeRecentAnswer && nowMillis() - last.atMillis in 0 until RECENT_ANSWER_MILLIS))) {
                UpdateCheckLog.sink("update check: not made, answered by the check of ${UpdateCheckLog.clockTime(last.atMillis)}")
                return@withLock last.result
            }
            askOnce(current, currentVersionName, store, apiUrl, honourLimit, nowMillis)
        }
    }

    /** How long an answer of GitHub stands for a check nobody asked for ([checkForUpdate]'s `takeRecentAnswer`): one moment, a minute. */
    internal const val RECENT_ANSWER_MILLIS = 60 * 1000L

    /** One check on the network at a time ([checkForUpdate]). */
    private val onTheNetwork = Mutex()

    /** The last check that reached GitHub (or tried to): its list, the installed version, when, and what came of it. */
    private class AskedCheck(val apiUrl: String, val versionName: String, val serial: Long, val atMillis: Long, val result: UpdateCheckResult)

    /** Written under [onTheNetwork]; read before it too, by a check about to wait. */
    @Volatile
    private var lastAsked: AskedCheck? = null

    /** The checks that reached GitHub in this process, [AskedCheck.serial]: written under [onTheNetwork]. */
    private var asked = 0L

    /** [checkForUpdate]'s check itself, under [onTheNetwork]. */
    private suspend fun askOnce(
        current: AppVersion,
        currentVersionName: String,
        store: UpdateReplyStore?,
        apiUrl: String,
        honourLimit: Boolean,
        nowMillis: () -> Long,
    ): UpdateCheckResult {
        if (honourLimit && store != null) {
            val until = runCatching { store.readLimitedUntil() }.getOrDefault(0L)
            if (until > nowMillis()) {
                val refused = UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.RATE_LIMITED, until)
                UpdateCheckLog.sink("update check: not made, GitHub's limit until ${UpdateCheckLog.clockTime(until)}")
                return refused
            }
        }
        val saved = runCatching { store?.readReply() }.getOrNull()
            ?.takeIf { it.forVersionName == currentVersionName && it.apiUrl == apiUrl }
        var lastCode: Int? = null
        var lastRemaining: String? = null
        var notModified = false
        val result = try {
            val parsed = mutableListOf<ParsedRelease>()
            var etag: String? = null
            var url: String? = apiUrl
            var pages = 0
            var early: UpdateCheckResult? = null
            while (url != null && pages < MAX_PAGES && early == null) {
                val page = fetchPage(url, ifNoneMatch = if (pages == 0) saved?.etag else null, now = nowMillis())
                lastCode = page.httpCode
                lastRemaining = page.remaining
                when (page) {
                    // Only the first page is asked conditionally, so only there can this arrive, with a
                    // kept reply to answer for it; a 304 to an unconditional request is unreadable.
                    is PageReply.NotModified -> {
                        notModified = true
                        early = saved?.result ?: UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.SERVER_ERROR)
                    }
                    is PageReply.Failed -> early = UpdateCheckResult.Unreachable(page.reason, page.limitedUntilMillis)
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
            early ?: answerFor(parsed, current).also { answered ->
                if (store != null && etag != null) {
                    runCatching { store.writeReply(SavedUpdateReply(etag, currentVersionName, apiUrl, answered)) }
                }
            }
        } catch (_: IOException) {
            // No network, DNS failure, connection refused, a timeout: kept apart from the parse
            // failure below because it is the one the user can do something about.
            UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.NO_CONNECTION)
        } catch (_: Exception) {
            // A reply arrived and could not be read: unexpected JSON, a truncated body.
            UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.UNREADABLE_RESPONSE)
        }
        // GitHub's limit is kept while it lasts; a check answered with the list (200, or 304) clears it.
        if (store != null) {
            val limitedUntil = (result as? UpdateCheckResult.Unreachable)
                ?.takeIf { it.reason == UpdateCheckResult.Unreachable.Reason.RATE_LIMITED }?.limitedUntilMillis
            when {
                limitedUntil != null && limitedUntil > 0L -> runCatching { store.writeLimitedUntil(limitedUntil) }
                result !is UpdateCheckResult.Unreachable -> runCatching { store.writeLimitedUntil(0L) }
            }
        }
        lastAsked = AskedCheck(apiUrl, currentVersionName, ++asked, nowMillis(), result)
        UpdateCheckLog.sink(UpdateCheckLog.line(UpdateCheckLog.outcome(result, notModified), lastCode, lastRemaining))
        return result
    }

    /** One release of the list, as far as this app reads it. */
    internal data class ParsedRelease(
        val version: AppVersion,
        val tagName: String,
        val htmlUrl: String,
        val notes: String?,
        val assets: List<ReleaseAsset>,
    )

    /** One page's reply, with the two facts every reply carries for the log: its HTTP code and the requests left. */
    private sealed interface PageReply {
        val httpCode: Int
        val remaining: String?

        data class Releases(
            val releases: List<ParsedRelease>,
            val etag: String?,
            val nextUrl: String?,
            override val httpCode: Int,
            override val remaining: String?,
        ) : PageReply

        data class NotModified(override val httpCode: Int, override val remaining: String?) : PageReply

        data class Failed(
            val reason: UpdateCheckResult.Unreachable.Reason,
            override val httpCode: Int,
            override val remaining: String?,
            val limitedUntilMillis: Long = 0L,
        ) : PageReply
    }

    /**
     * Whether a refusal is GitHub's rate limit, and until when: a 403 or 429 with `Retry-After` (seconds
     * from [nowMillis]) or with `X-RateLimit-Remaining: 0` and `X-RateLimit-Reset` (seconds since 1970),
     * the two ways GitHub documents for refusing an address that asks too much. Any other 403 is not the
     * limit (null); a 429 always is. A limit with no moment given returns 0.
     */
    internal fun rateLimitedUntil(
        code: Int,
        retryAfter: String?,
        remaining: String?,
        reset: String?,
        nowMillis: Long,
    ): Long? {
        if (code != 403 && code != 429) return null
        retryAfter?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.let { return nowMillis + it * 1000L }
        if (remaining?.trim() != "0") return if (code == 429) 0L else null
        return reset?.trim()?.toLongOrNull()?.takeIf { it > 0 }?.times(1000L) ?: 0L
    }

    private fun fetchPage(url: String, ifNoneMatch: String?, now: Long): PageReply {
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
            val remaining = connection.getHeaderField("X-RateLimit-Remaining")?.trim()?.takeIf { it.length <= 6 }
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED && ifNoneMatch != null) return PageReply.NotModified(code, remaining)
            if (code != HttpURLConnection.HTTP_OK) {
                val limitedUntil = rateLimitedUntil(
                    code,
                    retryAfter = connection.getHeaderField("Retry-After"),
                    remaining = remaining,
                    reset = connection.getHeaderField("X-RateLimit-Reset"),
                    nowMillis = now,
                )
                return if (limitedUntil != null) {
                    PageReply.Failed(UpdateCheckResult.Unreachable.Reason.RATE_LIMITED, code, remaining, limitedUntil)
                } else {
                    PageReply.Failed(UpdateCheckResult.Unreachable.Reason.SERVER_ERROR, code, remaining)
                }
            }
            // Bounded: a page of 30 releases is a few hundred KB, and a reply must not be read without end.
            val body = connection.inputStream.bufferedReader().use { it.readAtMost(MAX_HTTP_BODY_CHARS) }
                ?: return PageReply.Failed(UpdateCheckResult.Unreachable.Reason.SERVER_ERROR, code, remaining)
            return PageReply.Releases(
                releases = parseReleases(body),
                etag = connection.getHeaderField("ETag")?.ifBlank { null },
                nextUrl = nextPageUrl(connection.getHeaderField("Link")),
                httpCode = code,
                remaining = remaining,
            )
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Every release of one page, skipping any whose tag is not `vMAJOR.MINOR` ([AppVersion.parse]).
     * Throws on a body that is not GitHub's JSON array: the caller reads it as an unreadable reply.
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
     * What the releases read say to a user on [current]. No release under `vMAJOR.MINOR` is "nothing
     * newer", not an error.
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
     * and **whole**: its release body, which the release workflow composes from
     * `release-notes/vMAJOR.MINOR.md` and a short verification footer
     * (`.github/workflows/android-build.yml`). A release listed twice (a page boundary moving under
     * the reader) is shown once.
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
     * (`SettingsScreen`): each block starts at a line that is a release's tag alone, as [combinedNotes]
     * joins them. Lossless: the blocks joined with a blank line are the notes.
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
