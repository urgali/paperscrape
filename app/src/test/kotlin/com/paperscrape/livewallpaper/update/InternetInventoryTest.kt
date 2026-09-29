package com.paperscrape.livewallpaper.update

import java.io.File
import org.junit.Assert.assertEquals
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
 * KDoc is a reference, `"api.open-meteo.com"` in an expression is a request.
 *
 * ### Both directions (v5.9B, inventory I-44)
 *
 * Until v5.9B the check ran one way: every host in the code had to be in the manifest, and a host
 * the manifest named that nothing contacted any more went unnoticed -- a disclosure that claims a
 * data flow that no longer exists is wrong too, and it is the half that drifts when a provider is
 * removed. Now every host **row** of the inventory has to be a host the code names, or be one of
 * [reachedWithoutBeingNamed], each with its reason; that exception is checked both ways as well.
 *
 * ### A host is a row, and it may have two parts (v5.9C, inventory I-47)
 *
 * Until v5.9C the direct check read only hosts of three or more parts and looked for each one
 * anywhere in the inventory's text. So `github.com` -- the host the update download actually
 * requests, since the Releases list gives `https://github.com/...` addresses -- was invisible twice
 * over: it has two parts, and "github.com" is inside "www.github.com", so a substring search would
 * have found it listed even though no row was its own. Now hosts of two parts are read in the
 * source and in the rows alike, and a host counts as listed only when a row starts with it.
 */
class InternetInventoryTest {

    @Test
    fun `every host the code contacts is named in the manifest inventory`() {
        val rows = hostsInInventory()
        val missing = hostsInSource().filterNot { it.first in rows }
        assertTrue(
            "these hosts are contacted but have no row of their own in the manifest's INTERNET inventory:\n" +
                missing.joinToString("\n") { "  ${it.first}  (${it.second})" },
            missing.isEmpty(),
        )
    }

    /** The two-part host the check could not see until v5.9C, read on both sides. */
    @Test
    fun `a host of two parts is read, in the source and in the rows`() {
        assertTrue("github.com is not read out of the source", hostsInSource().any { it.first == "github.com" })
        assertTrue("github.com is not read as a row", "github.com" in hostsInInventory())
    }

    /** A host written only inside another row -- as github.com was, inside www.github.com's -- is not listed. */
    @Test
    fun `a host that appears only inside another row is not a row`() {
        val inventory = """
             www.github.com               update.ApkDownloader. Only a github.com or www.github.com
                                          address is ever requested.
        """.trimIndent().prependIndent("         ")
        val rows = hostsInInventory(inventory)
        assertEquals(setOf("www.github.com"), rows)
        assertTrue("github.com would pass as listed", "github.com" !in rows)
    }

    @Test
    fun `the inventory is not empty, so the test cannot pass by finding nothing`() {
        // A refactor that moves every URL behind a builder would otherwise leave this test green
        // and meaningless. Five is the count at the time of writing minus room to consolidate.
        assertTrue("the extractor found no hosts at all", hostsInSource().size >= 5)
    }

    @Test
    fun `every host the manifest lists is one the code contacts`() {
        val inSource = hostsInSource().map { it.first }.toSet()
        val stale = hostsInInventory().filterNot { it in inSource || it in reachedWithoutBeingNamed }
        assertTrue(
            "the manifest's INTERNET inventory lists these hosts and no line of the source contacts " +
                "them any more -- remove the row, or, if the app reaches the host without naming it, " +
                "say why in InternetInventoryTest.reachedWithoutBeingNamed:\n" +
                stale.joinToString("\n") { "  $it" },
            stale.isEmpty(),
        )
    }

    /** Checked in both directions, so the exception cannot quietly become an allowlist. */
    @Test
    fun `every host reached without being named is still listed, still unnamed, and says so`() {
        val inventory = inventoryText()
        val inSource = hostsInSource().map { it.first }.toSet()
        for ((host, reason) in reachedWithoutBeingNamed) {
            assertTrue("$host is excused but no longer listed: remove the excuse", host in hostsInInventory())
            assertTrue("$host is excused but the code names it now: remove the excuse", host !in inSource)
            assertTrue("$host's reason is too short to be a reason", reason.length > 60)
            val row = inventory.substringAfter(host).substringBefore("\n\n")
            assertTrue("the manifest's own row for $host must say it is a redirect", row.contains("redirect"))
        }
    }

    /** The reverse check reads the inventory row by row, so it cannot pass by reading no rows. */
    @Test
    fun `the inventory's rows are read, so the reverse check cannot pass by finding none`() {
        val rows = hostsInInventory()
        assertTrue("the row reader found only $rows", rows.size >= 8)
        for (host in listOf("api.github.com", "api.open-meteo.com", "geocoding-api.open-meteo.com")) {
            assertTrue("$host is not read as a row", host in rows)
        }
    }

    /**
     * Hosts the app reaches without any line of its source naming them, and why. Each must still be
     * a row of the inventory, must still be absent from the source, and its row must say "redirect".
     */
    private val reachedWithoutBeingNamed = mapOf(
        "release-assets.githubusercontent.com" to
            "the host GitHub's release download redirects to. update.ApkDownloader requests github.com " +
            "and follows the redirect without checking where it goes, so the file is verified and the " +
            "host is not -- which is why no line of the source names it",
    )

    private fun inventoryText(): String =
        moduleFile("src/main/AndroidManifest.xml").readText()
            .substringAfter("The complete list of hosts").substringBefore("-->")

    /**
     * The inventory's rows: a host at the start of a line, which is how every row is written (the
     * rows' continuation lines and the prose around them start with words, not with a host). Two
     * parts are enough to be a host (`github.com`).
     */
    private fun hostsInInventory(inventory: String = inventoryText()): Set<String> {
        val row = Regex("""^\s+([a-z0-9-]+(?:\.[a-z0-9-]+)*\.(?:com|org|net|io))(?:\s|$)""")
        return inventory.lines().mapNotNull { row.find(it)?.groupValues?.get(1) }.toSet()
    }

    /** Host-shaped string literals on non-comment lines, with the file that holds each; two parts are enough. */
    private fun hostsInSource(): List<Pair<String, String>> {
        val host = Regex("""["/]([a-z0-9-]+(?:\.[a-z0-9-]+)*\.(?:com|org|net|io))""")
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
