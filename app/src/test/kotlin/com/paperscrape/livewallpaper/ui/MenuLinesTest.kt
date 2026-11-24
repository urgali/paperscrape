package com.paperscrape.livewallpaper.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The menu's lines that a rule decides (decision 113): the line under each holiday on Seasons & decorations
 * ([SettingsUiModel.seasonSummary]) and when a row's supporting line is collapsed to three lines with "More"
 * ([SupportingLine]).
 */
class MenuLinesTest {

    private val halloween = listOf(
        "Halloween", "Laughing moon", "Night sky by day", "Moon stays in place", "Horror sky",
        "Halloween decorations", "Pumpkins", "Scarecrows", "Headless Horseman", "Blood rain",
    )

    private fun on(vararg names: String) = halloween.map { it to (it in names) }

    @Test
    fun `none on says how many there are to choose from`() {
        assertEquals("All off - 10 to choose from", SettingsUiModel.seasonSummary(on()))
        assertEquals("All off - 2 to choose from", SettingsUiModel.seasonSummary(listOf("Snowmen" to false, "Penguins" to false)))
    }

    @Test
    fun `one or two on are named, and nothing that is off`() {
        assertEquals("Pumpkins on", SettingsUiModel.seasonSummary(on("Pumpkins")))
        assertEquals("Halloween and Pumpkins on", SettingsUiModel.seasonSummary(on("Pumpkins", "Halloween")))
    }

    @Test
    fun `more than two name the first two in the row's order and count the rest`() {
        assertEquals("Halloween, Pumpkins and 1 more on", SettingsUiModel.seasonSummary(on("Scarecrows", "Pumpkins", "Halloween")))
        assertEquals("Laughing moon, Horror sky and 3 more on",
            SettingsUiModel.seasonSummary(on("Laughing moon", "Horror sky", "Pumpkins", "Scarecrows", "Blood rain")))
    }

    @Test
    fun `all on names two and counts the others`() {
        assertEquals("Halloween, Laughing moon and 8 more on", SettingsUiModel.seasonSummary(halloween.map { it to true }))
        assertEquals("Snowmen and Penguins on", SettingsUiModel.seasonSummary(listOf("Snowmen" to true, "Penguins" to true)))
        assertEquals("Fireworks on", SettingsUiModel.seasonSummary(listOf("Fireworks" to true)))
    }

    @Test
    fun `an accent line stays whole and every other line collapses`() {
        assertTrue("a plain line", SupportingLine.collapses(isAccent = false))
        assertFalse("an accent line, as the app ships (decision 113)", SupportingLine.collapses(isAccent = true))
        assertTrue("an accent line when accent lines are collapsed too", SupportingLine.collapses(isAccent = true, clampAccentLines = true))
        assertTrue(SupportingLine.collapses(isAccent = false, clampAccentLines = false))
    }

    @Test
    fun `a collapsed line shows three lines, an expanded one all of them`() {
        assertEquals(3, SupportingLine.COLLAPSED_LINES)
        assertEquals(3, SupportingLine.maxLines(expanded = false))
        assertEquals(Int.MAX_VALUE, SupportingLine.maxLines(expanded = true))
    }

    @Test
    fun `the link shows only for a line longer than three lines, both ways`() {
        assertTrue("collapsed, cut", SupportingLine.longerThanCollapsed(expanded = false, lineCount = 3, exceededMaxLines = true))
        assertFalse("collapsed, three lines exactly", SupportingLine.longerThanCollapsed(expanded = false, lineCount = 3, exceededMaxLines = false))
        assertTrue("expanded, four lines", SupportingLine.longerThanCollapsed(expanded = true, lineCount = 4, exceededMaxLines = false))
        assertFalse("expanded, the line became short", SupportingLine.longerThanCollapsed(expanded = true, lineCount = 3, exceededMaxLines = false))
        assertEquals("More", SupportingLine.linkLabel(expanded = false))
        assertEquals("Less", SupportingLine.linkLabel(expanded = true))
    }

    /**
     * Seasons & decorations draws each holiday's line from the rule: its row's supporting line is
     * [SettingsUiModel.seasonSummary] of the row's decorations, in the accent colour while one is on. Whole lines of
     * `SeasonsScreen.kt`'s `SeasonRow`, the comments taken off, as the project does for Compose it cannot run on the JVM.
     */
    @Test
    fun `each holiday's row on Seasons and decorations shows the summary the rule gives`() {
        val lines = codeLines(source("ui/SeasonsScreen.kt"))
        val row = lines.indexOf("private fun SeasonRow(")
        assertTrue("SeasonsScreen's SeasonRow", row >= 0)
        val expected = listOf(
            "SettingsNavigationRow(",
            "title = season.title,",
            "supporting = SettingsUiModel.seasonSummary(contents),",
            "icon = icon,",
            "supportingIsAccent = contents.any { it.second },",
            "onClick = onClick,",
            ")",
        )
        val body = lines.subList(row, lines.size)
        val at = body.indexOf(expected.first())
        assertTrue("SeasonRow draws a navigation row", at >= 0)
        assertEquals(expected, body.subList(at, minOf(body.size, at + expected.size)))
        assertEquals("the rule is asked in one place, the row", 1, lines.count { "SettingsUiModel.seasonSummary(" in it })
    }

    /** [text]'s lines, trimmed, with comment lines, trailing `//` comments and blank lines left out. */
    private fun codeLines(text: String): List<String> =
        text.lines().map { it.trim() }
            .filterNot { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }
            .map { it.substringBefore(" //").trim() }
            .filter { it.isNotEmpty() }

    private fun source(path: String): String {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/$path"
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = java.io.File(dir, "$prefix$suffix")
                if (f.isFile) return f.readText()
            }
            dir = dir.parentFile
        }
        error("source not found: $suffix")
    }
}
