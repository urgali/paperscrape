package com.paperscrape.livewallpaper.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A backup or a theme read from a file, or written to one, is read, parsed and written off the main thread.** The
 * settings' coroutines run on the main thread (`rememberCoroutineScope`), and a file the user picks on Google Drive is
 * downloaded inside the read: on the main thread the screen would stand still for the whole download, and Android could
 * say the app is not responding. (The check of a downloaded update is off it in `ApkInstaller.verdict`.)
 *
 * Compose code does not run on the JVM, so this is asserted on the screens' source, as the project does
 * (`MoonPhaseControlTest`): every call that reads, parses or writes such a file stands between the braces of a
 * `withContext` of another dispatcher -- inside its block, not after it in the same coroutine, where the call would be
 * back on the main thread.
 */
class ImportsOffTheMainThreadTest {

    private val calls = mapOf(
        "ui/AdvancedScreen.kt" to listOf(
            "BoundedImport.readText(", "backupRepository.preview(", "backupRepository.import(", "backupRepository.export()",
            "openOutputStream(",
        ),
        "ui/ThemeGalleryScreen.kt" to listOf("BoundedImport.readText(", "parseThemeShare(", "openOutputStream(", ".toJsonString()"),
    )

    @Test
    fun `every read, parse and write of a picked file runs off the main thread`() {
        val offenders = mutableListOf<String>()
        var seen = 0
        for ((file, needles) in calls) {
            val code = codeOf(file)
            val offMain = offMainBlocks(code)
            for (needle in needles) {
                var at = code.indexOf(needle)
                while (at >= 0) {
                    seen++
                    if (offMain.none { at in it }) offenders += "$file: $needle at ${code.substring(0, at).count { it == '\n' } + 1}"
                    at = code.indexOf(needle, at + needle.length)
                }
            }
        }
        assertEquals("every call found once", 9, seen)
        assertEquals("on the main thread", emptyList<String>(), offenders)
    }

    /**
     * Where [code] runs off the main thread: from the opening brace of each `withContext(Dispatchers.IO)` or
     * `withContext(Dispatchers.Default)` block to the brace that closes it.
     */
    private fun offMainBlocks(code: String): List<IntRange> =
        Regex("""withContext\(Dispatchers\.(IO|Default)\)\s*\{""").findAll(code).map { opened ->
            val open = opened.range.last
            var depth = 0
            var close = -1
            for (i in open until code.length) {
                val c = code.substring(i, i + 1)
                if (c == "{") depth++
                if (c == "}") depth--
                if (depth == 0) {
                    close = i
                    break
                }
            }
            assertTrue("a withContext block that never closes, at ${code.substring(0, open).count { it == '\n' } + 1}", close > open)
            open..close
        }.toList()

    /** The file's code, its comment lines taken out, and a comment at the end of a line cut off. */
    private fun codeOf(path: String): String {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/$path"
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = java.io.File(dir, "$prefix$suffix")
                if (f.isFile) {
                    return f.readLines().filterNot { it.trim().startsWith("*") || it.trim().startsWith("//") || it.trim().startsWith("/**") }
                        .joinToString("\n") { it.substringBefore(" //") }
                }
            }
            dir = dir.parentFile
        }
        error("no $suffix")
    }
}
