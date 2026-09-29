package com.paperscrape.livewallpaper.engine

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * **Nothing of the app's is copied off the phone by Android, on any phone** (inventory I-99, the
 * maintainer's decision of 2026-09-28, the option "copy nothing, on any phone").
 *
 * `android:allowBackup="false"` said so up to Android 11 (and `android:fullBackupContent` now says it in
 * that format too, which lint asks for while `minSdk` is below 31). From Android 12, for an app that
 * targets 31 or more, `allowBackup` still stops the cloud backup but, on devices from some manufacturers, not the
 * transfer to a new phone (Android 12 behaviour changes, "D2D transfer functionality changes"), so
 * a phone change copied the settings, the saved themes, the weather keys and the last location on
 * some brands and nothing on others. What governs there is `android:dataExtractionRules`, and **a
 * section it leaves out means that kind of transfer copies everything** (Android's own
 * documentation, "Control backup on Android 12 or higher"): so the rules must carry every section,
 * and every section must leave out every domain. Lint's `DataExtractionRules` warning, which v5.9C
 * declared as this very question, is gone with it.
 *
 * Read from the manifest and the resource as files: neither is Kotlin, and both are declared inputs
 * of the unit tests (`app/build.gradle.kts`, v5.9C), so an edit to either runs this.
 */
class DataExtractionRulesTest {

    private val domains = listOf(
        "root", "file", "database", "sharedpref", "external",
        "device_root", "device_file", "device_database", "device_sharedpref",
    )

    private fun module(path: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = File(dir, "$prefix$path")
                if (f.isFile) return f
            }
            dir = dir.parentFile
        }
        error("could not locate $path")
    }

    @Test
    fun `the manifest turns the backup off and points at rules for Android 12 and later`() {
        val application = module("src/main/AndroidManifest.xml").readText()
            .substringAfter("<application").substringBefore(">")
        assertTrue(application, application.contains("android:allowBackup=\"false\""))
        assertTrue(application, application.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue(application, application.contains("android:fullBackupContent=\"@xml/full_backup_content\""))
    }

    /** Android 11 and lower read the older format; it says the same, for the same domains. */
    @Test
    fun `the older format leaves out every domain too`() {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(module("src/main/res/xml/full_backup_content.xml")).documentElement
        assertEquals("full-backup-content", root.tagName)
        val rules = (0 until root.childNodes.length).map { root.childNodes.item(it) }.filterIsInstance<Element>()
        assertTrue("includes something", rules.none { it.tagName == "include" })
        assertEquals(
            domains.toSet(),
            rules.filter { it.tagName == "exclude" && it.getAttribute("path") == "." }.map { it.getAttribute("domain") }.toSet(),
        )
    }

    @Test
    fun `every kind of transfer leaves out every domain, and includes nothing`() {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(module("src/main/res/xml/data_extraction_rules.xml"))
        val root = doc.documentElement
        assertEquals("data-extraction-rules", root.tagName)
        val sections = (0 until root.childNodes.length).map { root.childNodes.item(it) }
            .filterIsInstance<Element>()
        assertEquals(
            "the three kinds of transfer Android has",
            listOf("cloud-backup", "device-transfer", "cross-platform-transfer"),
            sections.map { it.tagName },
        )
        for (section in sections) {
            val rules = (0 until section.childNodes.length).map { section.childNodes.item(it) }
                .filterIsInstance<Element>()
            assertTrue("${section.tagName} includes something", rules.none { it.tagName == "include" })
            val excluded = rules.filter { it.tagName == "exclude" && it.getAttribute("path") == "." }
                .map { it.getAttribute("domain") }.toSet()
            assertEquals("${section.tagName} leaves out every domain whole", domains.toSet(), excluded)
        }
    }
}
