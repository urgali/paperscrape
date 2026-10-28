package com.paperscrape.livewallpaper.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **A city search in flight stops when the field's words change, or the X empties it.** The search runs in the
 * screen's own scope, so a new text does not end it by itself: without the stop, "Milano" asked and the X tapped shows
 * "N results - pick one" under an empty field when the answer comes, and "Milano" asked and cut to "M" leaves
 * "Searching..." on the screen for good, since "M" is too short to ask about and nothing else follows.
 *
 * The search is coroutines inside a `@Composable`, which the JVM does not run: the rule is held on the screen's
 * source, its comments taken off and whole lines compared (`ColourPickerAndCitySearchTest` holds the rule beside it,
 * one search at a time).
 */
class CitySearchStopsTest {

    private val screen: String by lazy { source("ui/WeatherTimeScreen.kt") }

    /** The field's new words stop a search asked for other words, before anything else the change does. */
    @Test
    fun `a search in flight stops when the field holds other words`() {
        val typing = codeLines(body(screen, "    LaunchedEffect(query) {"))
        assertEquals("the first thing a new text does", "if (trimmed != searchJobText) stopSearch()", typing[2])
        assertEquals("val trimmed = query.trim()", typing[1])
        val start = codeLines(body(screen, "    fun startSearch(text: String) {"))
        assertTrue("the words of the search running are not kept", "searchJobText = text.trim()" in start)
        assertTrue(
            "the words are kept after the search is launched",
            start.indexOf("searchJobText = text.trim()") < start.indexOf("searchJob = scope.launch { runSearch(text) }"),
        )
    }

    /**
     * The X stops it at once, and a search stopped before its answer leaves nothing that waits for one; the X, and a field
     * emptied with the keyboard, forget the words searched last too, so typing the same place again asks again and shows
     * its results.
     */
    @Test
    fun `the X stops the search, and a stopped search waits for nothing`() {
        assertTrue(
            "the X does not stop the search",
            "IconButton(onClick = { query = \"\"; lastSearched = \"\"; stopSearch(); searchState = CitySearchUiState.Idle }) {" in codeLines(screen),
        )
        assertEquals(
            listOf(
                "fun stopSearch() {",
                "if (searchJob?.isActive != true) return",
                "searchJob?.cancel()",
                "if (searchState == CitySearchUiState.Searching) lastSearched = \"\"",
            ),
            codeLines(body(screen, "    fun stopSearch() {")),
        )
        val typing = codeLines(body(screen, "    LaunchedEffect(query) {"))
        assertEquals(
            "an emptied field, by the X or by the keyboard, does not forget the words searched last",
            listOf("if (trimmed.isEmpty()) {", "lastSearched = \"\"", "searchState = CitySearchUiState.Idle", "return@LaunchedEffect"),
            typing.subList(typing.indexOf("if (trimmed.isEmpty()) {"), typing.indexOf("if (trimmed.isEmpty()) {") + 4),
        )
        val tooShort = typing.indexOf("if (!CityGeocoder.isQuerySearchable(trimmed)) {")
        assertTrue("text too short to ask about has its own branch", tooShort > 0)
        assertEquals(
            "\"Searching...\" stays under text that is never asked about",
            listOf("if (searchState == CitySearchUiState.Searching) searchState = CitySearchUiState.Idle", "return@LaunchedEffect"),
            typing.subList(tooShort + 1, tooShort + 3),
        )
        assertEquals(
            "a keyboard search of text too short stops the search running and asks nothing",
            "if (!CityGeocoder.isQuerySearchable(text)) return",
            codeLines(body(screen, "    fun startSearch(text: String) {"))[1],
        )
    }

    /** The body of [declaration], to the closing brace at its own indentation. */
    private fun body(text: String, declaration: String): String {
        val start = text.indexOf(declaration)
        require(start >= 0) { "$declaration is gone" }
        val indent = declaration.takeWhile { it == ' ' }
        val end = text.indexOf("\n$indent}", start)
        return text.substring(start, if (end < 0) text.length else end)
    }

    /** [text]'s lines, trimmed, without comments: a line comment cut off, a KDoc or block comment line dropped. */
    private fun codeLines(text: String): List<String> =
        text.lines().map { it.trim() }
            .filterNot { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }
            .map { it.substringBefore(" //").trim() }
            .filter { it.isNotEmpty() }

    private fun source(path: String): String {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/$path"
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = File(dir, "$prefix$suffix")
                if (f.isFile) return f.readText()
            }
            dir = dir.parentFile
        }
        error("source not found: $suffix")
    }
}
