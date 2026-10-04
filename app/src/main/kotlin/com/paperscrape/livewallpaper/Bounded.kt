package com.paperscrape.livewallpaper

import java.io.Reader

/**
 * Reads at most [limit] characters, or returns `null` if the source has more.
 *
 * **The shared helper for how much of somebody else's data this app will hold.** Five callers
 * used `readText()` with no bound at all: the two document importers on a `Uri` the user picks from
 * any provider on the device (BCK-04), and the three HTTP bodies — the release list, the geocoder
 * and the weather providers (SEC-03). None of them is ever legitimately large, and none of them
 * checked. A wrong file picked from a downloads folder, or a server answering with something other
 * than JSON, is an `OutOfMemoryError` on the settings screen or in the wallpaper process.
 * Two reads bypassed it until v5.8C (found by the v5.8B comment audit): the backup Restore step,
 * which re-opened the file and read it again unbounded -- it now imports the text the preview
 * read -- and `ApkDownloader.fetchText` for the release checksum file, now capped like the rest.
 *
 * Returning `null` rather than truncating is deliberate: a truncated JSON document is a *parse*
 * failure whose message would blame the syntax, and every caller already has a "this is not
 * something I can read" path. Over-long means refused, not partially believed.
 *
 * Reads never hold more than [limit] characters: the check happens before each chunk is appended.
 */
internal fun Reader.readAtMost(limit: Int): String? {
    val buffer = CharArray(8 * 1024)
    val out = StringBuilder()
    while (true) {
        val read = read(buffer)
        if (read < 0) return out.toString()
        if (out.length + read > limit) return null
        out.appendRange(buffer, 0, read)
    }
}

/**
 * The cap for the HTTP responses this app reads into memory through [readAtMost] (SEC-03).
 *
 * The three bodies are a GitHub releases page, a geocoder's few candidate places, and a weather
 * provider's current conditions. The largest is the releases page: GitHub's thirty releases a page
 * read 252 KB on 2026-10-03 (v5.10D; this line said "tens of kilobytes"), and a release adds about
 * eight. A megabyte is past any of them and far below a problem; the update check reads the list a
 * page at a time, and a page that ever outgrew it would read as "GitHub didn't answer", not as a
 * crash.
 *
 * A fourth body, the release checksum file (`ApkDownloader.fetchText`), reads through the same
 * cap since v5.8C.
 */
internal const val MAX_HTTP_BODY_CHARS = 1_000_000
