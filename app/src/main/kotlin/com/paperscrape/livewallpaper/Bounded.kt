package com.paperscrape.livewallpaper

import java.io.Reader

/**
 * Reads at most [limit] characters, or returns `null` if the source has more.
 *
 * **The one place that decides how much of somebody else's data this app will hold.** Four callers
 * used `readText()` with no bound at all: the two document importers on a `Uri` the user picks from
 * any provider on the device (BCK-04), and the three HTTP bodies — the release list, the geocoder
 * and the weather providers (SEC-03). None of them is ever legitimately large, and none of them
 * checked. A wrong file picked from a downloads folder, or a server answering with something other
 * than JSON, is an `OutOfMemoryError` on the settings screen or in the wallpaper process.
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
 * The cap for an HTTP response this app reads into memory (SEC-03).
 *
 * The three bodies are a GitHub releases page, a geocoder's few candidate places, and a weather
 * provider's current conditions. The largest of them measured in the tens of kilobytes; a megabyte
 * is far past any of them and far below a problem.
 */
internal const val MAX_HTTP_BODY_CHARS = 1_000_000
