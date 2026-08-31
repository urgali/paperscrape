package com.paperscrape.livewallpaper.update

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The manifest's INTERNET inventory names every host the code actually contacts.
 *
 * ### Why this is a test and not a comment
 *
 * That comment is a **privacy disclosure**: it is the answer anyone reading this repo gets to
 * "where does my data go". WEA-09(b) found it wrong -- it claimed "no other network calls exist
 * anywhere in the app" while [com.paperscrape.livewallpaper.location.CityGeocoder] had been
 * sending user-typed city names to a third host for several releases, and it still named one
 * weather provider when three had shipped. Nobody noticed because nothing could notice: a comment
 * about the whole codebase drifts the moment any one file changes.
 *
 * So the inventory is checked the way `tools/assets`' `validate` checks blit call sites and the
 * way [com.paperscrape.livewallpaper.engine.SkyscraperWindowTest] checks the window crossfade --
 * by reading the source. Adding a host without listing it now fails here.
 *
 * ### What counts as a host
 *
 * Host-shaped literals on lines that are not comments. Doc links live in KDoc (` * `) and `//`
 * lines and are skipped, which is the distinction that matters: `open-meteo.com/en/docs` in a
 * KDoc is a reference, `"api.open-meteo.com"` in an expression is a request. The check is
 * deliberately one-directional -- every host in the code must be in the manifest, but the
 * manifest may name a host the code reaches only through a variable.
 */
class InternetInventoryTest {

    @Test
    fun `every host the code contacts is named in the manifest inventory`() {
        val manifest = moduleFile("src/main/AndroidManifest.xml").readText()
        val inventory = manifest.substringAfter("The complete list of hosts").substringBefore("-->")
        val missing = hostsInSource().filterNot { inventory.contains(it.first) }
        assertTrue(
            "these hosts are contacted but not listed in the manifest's INTERNET inventory:\n" +
                missing.joinToString("\n") { "  ${it.first}  (${it.second})" },
            missing.isEmpty(),
        )
    }

    @Test
    fun `the inventory is not empty, so the test cannot pass by finding nothing`() {
        // A refactor that moves every URL behind a builder would otherwise leave this test green
        // and meaningless. Five is the count at the time of writing minus room to consolidate.
        assertTrue("the extractor found no hosts at all", hostsInSource().size >= 5)
    }

    /** Host-shaped string literals on non-comment lines, with the file that holds each. */
    private fun hostsInSource(): List<Pair<String, String>> {
        val host = Regex("""["/]([a-z0-9-]+(?:\.[a-z0-9-]+)+\.(?:com|org|net|io))""")
        return moduleFile("src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .map { it.trim() }
                    .filterNot { it.startsWith("*") || it.startsWith("//") || it.startsWith("/*") }
                    .flatMap { line -> host.findAll(line).map { it.groupValues[1] to file.name } }
            }
            .distinct()
            .sortedBy { it.first }
            .toList()
    }

    /** Walks up for the module root, the way `SkyscraperWindowTest` finds the renderer. */
    private fun moduleFile(suffix: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = File(dir, "$prefix$suffix")
                if (candidate.exists()) return candidate
            }
            dir = dir.parentFile
        }
        error("could not locate $suffix from ${File(".").absolutePath}")
    }
}
