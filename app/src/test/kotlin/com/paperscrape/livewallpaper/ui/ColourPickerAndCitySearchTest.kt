package com.paperscrape.livewallpaper.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two rules of the settings' controls:
 *
 *  - **the colour picker after a rotation**: the Activity keeps the dialog across a rotation, so the square and the hue
 *    strip read a touch against the size the area has when it is touched -- the input scope's own `size`
 *    ([saturationAt], [brightnessAt], [hueAt]). Read against the size of the composition their handler started in, on
 *    the strip widened from 280 to 400 dp a touch at 90 % would pick 360 degrees, not 324;
 *  - **the city search asks once, the newest**: the typing's search, 500 ms after the last letter, and the keyboard's
 *    search key both ask, so "Milano" and the key within half a second would be two requests, and the older answer could
 *    land over a newer one. One search at a time, the newest; the typing's is not asked when the key asked the same text.
 *
 * Gestures and coroutines of a Compose screen do not run on the JVM: the rules are asserted, and that the screens read
 * them on their source, as the project does (`MoonPhaseControlTest`).
 */
class ColourPickerAndCitySearchTest {

    @Test
    fun `a touch is read against the size the picker has when it is touched`() {
        assertEquals(324f, hueAt(0.9f * 400f, 400f), 0.001f)
        assertEquals("against the width before the rotation, the same touch was 360", 360f, hueAt(0.9f * 400f, 280f), 0.001f)
        assertEquals(0.5f, saturationAt(150f, 300f), 0f)
        assertEquals(1f, brightnessAt(0f, 200f), 0f)
        assertEquals(0f, brightnessAt(250f, 200f), 0f)
        assertEquals("an area not measured yet picks nothing out of range", 0f, hueAt(10f, 0f), 0f)
        val code = code("ui/SettingsComponents.kt")
        assertEquals("every handler reads the scope's size", 6, Regex("""report\((offset|change\.position)(\.x)?, size\)""").findAll(code).count())
        assertFalse("a size kept from the composition", code.contains("fun reportFromOffset(") || code.contains("fun reportFromX("))
        assertTrue(code.contains("currentOnChange(saturationAt(offset.x, size.width.toFloat()), brightnessAt(offset.y, size.height.toFloat()))"))
        assertTrue(code.contains("fun report(x: Float, size: IntSize) = currentOnChange(hueAt(x, size.width.toFloat()))"))
        assertEquals(2, Regex("""val currentOnChange by rememberUpdatedState\(onChange\)""").findAll(code).count())
    }

    @Test
    fun `the city search asks once, the newest`() {
        val code = code("ui/WeatherTimeScreen.kt")
        assertTrue(code.contains("searchJob?.cancel()"))
        assertTrue(code.contains("searchJob = scope.launch { runSearch(text) }"))
        val settled = code.substringAfter("delay(SEARCH_DEBOUNCE_MS)").substringBefore("}")
        assertTrue("the typing's search asks what the key asked", settled.contains("if (trimmed == lastSearched) return@LaunchedEffect"))
        assertTrue(settled.contains("startSearch(trimmed)"))
        assertTrue(code.contains("startSearch(query)"))
        assertTrue(code.contains("startSearch(lastSearched)"))
        assertEquals("a search launched beside the one running", 1, Regex("""scope\.launch \{ runSearch\(""").findAll(code).count())
    }

    private fun code(path: String): String {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/$path"
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = java.io.File(dir, "$prefix$suffix")
                if (f.isFile) return f.readLines().filterNot { it.trim().startsWith("//") || it.trim().startsWith("*") }.joinToString("\n")
            }
            dir = dir.parentFile
        }
        error("no $suffix")
    }
}
