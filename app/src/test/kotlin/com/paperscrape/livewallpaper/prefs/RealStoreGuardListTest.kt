package com.paperscrape.livewallpaper.prefs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **`RealStoreGuardTest` runs every instrumented test class that opens the app's real stores, and no other.** It runs on
 * the phone, where no source can be read, so its list is written in it; here the list is read off its source and held
 * against the instrumented sources: a class whose code builds `WallpaperPrefs` or `CustomThemeStore` over a context
 * writes the phone's settings or saved themes, and has to be one the guard runs. A store test left off the list would
 * leave the guard proving nothing about it -- its writes without a `RealPrefsGuard` would go unseen. Whole lines with the
 * comments taken off, so a class that only names a store in its documentation is not counted.
 */
class RealStoreGuardListTest {

    @Test
    fun `the guard runs every instrumented class that opens a real store`() {
        val root = androidTestKotlin()
        val sources = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("no instrumented source under ${root.path}", sources.isNotEmpty())
        val opensAStore = Regex("""\b(WallpaperPrefs|CustomThemeStore)\([\w.]*[cC]ontext\)""")
        val writers = sources
            .filter { it.name != "RealStoreGuardTest.kt" }
            .filter { file -> codeLines(file).any { opensAStore.containsMatchIn(it) } }
            .map { it.nameWithoutExtension }
            .sorted()
        val guard = sources.single { it.name == "RealStoreGuardTest.kt" }
        val run = Regex("""Request\.(?:aClass|method)\((\w+)::class\.java""")
            .findAll(codeLines(guard).joinToString("\n"))
            .map { it.groupValues[1] }
            .toList()
            .sorted()
        assertEquals("the classes RealStoreGuardTest runs, against those that open a real store", writers, run)
    }

    /** [file]'s lines, trimmed, without comments: a line comment cut off, a KDoc or block comment line dropped. */
    private fun codeLines(file: File): List<String> =
        file.readLines().map { it.trim() }
            .filterNot { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }
            .map { it.substringBefore(" //").trim() }
            .filter { it.isNotEmpty() }

    /** `app/src/androidTest/kotlin`, found the way the other source-reading tests find theirs. */
    private fun androidTestKotlin(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = File(dir, "${prefix}src/androidTest/kotlin")
                if (f.isDirectory) return f
            }
            dir = dir.parentFile
        }
        error("src/androidTest/kotlin not found")
    }
}
