package com.paperscrape.livewallpaper.prefs

import android.content.Context
import android.net.Uri
import com.paperscrape.livewallpaper.readAtMost
import java.io.Reader

/**
 * Reads a user-picked document, and stops if it turns out not to be one.
 *
 * **BCK-04.** Both import paths were `openInputStream(uri)?.bufferedReader()?.use { it.readText() }`
 * with no bound at all, on a `Uri` the user picks from any provider on the device. A backup is a few
 * hundred kilobytes of JSON; nothing stopped the app from pulling a multi-gigabyte file into a
 * `String` first and deciding it was not JSON afterwards. The failure mode is not subtle — it is an
 * `OutOfMemoryError` on the settings screen — and it needs no hostile intent, only the wrong file
 * picked from a downloads folder.
 *
 * The bound is deliberately far above anything the app writes. A full backup carries the
 * preferences plus every saved custom theme: colours, densities and a layout of on the order of a
 * hundred objects each, which measures in the low hundreds of kilobytes for a heavily used install.
 * [MAX_IMPORT_CHARS] is 4 million characters, so a real backup has more than an order of magnitude
 * of headroom and a file that exceeds it is not one.
 *
 * Refusing is the whole behaviour: an over-long file reads as `null`, which both callers already
 * treat as "this is not a document I can parse" and report to the user. Nothing new to handle.
 */
internal object BoundedImport {

    /** The largest document either importer will read. See the class comment for why this size. */
    const val MAX_IMPORT_CHARS = 4_000_000

    /**
     * The document's text, or `null` if it cannot be opened or is longer than [MAX_IMPORT_CHARS].
     *
     * Reads one character past the limit deliberately: a file of exactly the limit is accepted, and
     * one character more is refused, without ever holding more than the limit plus one.
     */
    fun readText(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { readBounded(it) }
    }.getOrNull()

    /**
     * Split out from [readText] so it can be tested without a `ContentResolver`.
     *
     * The reading itself is [com.paperscrape.livewallpaper.readAtMost], shared with the three HTTP
     * bodies that had the same defect (SEC-03): one implementation of "how much of somebody else's
     * data will this app hold", not two that can drift apart.
     */
    internal fun readBounded(reader: Reader, limit: Int = MAX_IMPORT_CHARS): String? =
        reader.readAtMost(limit)
}
