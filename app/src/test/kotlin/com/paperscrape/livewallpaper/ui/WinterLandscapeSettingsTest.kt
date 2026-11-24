package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.engine.GroundSnow
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Hills and the Mountains pages under the winter palette ([SettingsUiModel.winterLandscape], decision 117):
 * while it is on, the colours shown are the snow the scene draws, read-only, with the line under them; with it off, the
 * stored colours, editable as always. The store is never written for it (`WinterPaletteStoreTest`).
 */
class WinterLandscapeSettingsTest {

    @Test
    fun `the winter palette owns the colours only while it is on, in the read-only version`() {
        assertTrue(SettingsUiModel.winterOwnsLandscapeColours(winterColorsEnabled = true, readOnly = true))
        assertFalse(SettingsUiModel.winterOwnsLandscapeColours(winterColorsEnabled = false, readOnly = true))
        assertFalse("the editable version leaves them the user's", SettingsUiModel.winterOwnsLandscapeColours(winterColorsEnabled = true, readOnly = false))
        assertFalse(SettingsUiModel.winterOwnsLandscapeColours(winterColorsEnabled = false, readOnly = false))
        assertTrue("the read-only version is the one that ships", SettingsUiModel.WINTER_LANDSCAPE_READ_ONLY)
    }

    @Test
    fun `on a snowy theme the pages show its own snow`() {
        val c = defaultCustomizationFor("winter")
        val shown = SettingsUiModel.winterLandscape(c)
        assertNotNull(shown)
        val state = shown!!
        assertEquals(c.hillsColorDay, state.hillsDay)
        assertEquals(c.hillsColorNight, state.hillsNight)
        // The mountains: the pair a quarter of the way to white, what the scene draws at noon and at midnight.
        assertEquals(GroundSnow.snowAt(c, 1f), state.mountainsFrontDay)
        assertEquals(GroundSnow.snowAt(c, 0f), state.mountainsFrontNight)
        assertEquals(0xFFEEF4F9.toInt(), state.mountainsFrontDay)
        assertEquals(0xFF5D6877.toInt(), state.mountainsFrontNight)
        // The back layer as the scene draws it, 12 % darker.
        assertEquals(GroundSnow.backMountains(state.mountainsFrontDay), state.mountainsBackDay)
        assertEquals(GroundSnow.backMountains(state.mountainsFrontNight), state.mountainsBackNight)
        assertEquals(SettingsUiModel.WINTER_LANDSCAPE_LINE, state.line)
    }

    @Test
    fun `a theme with no snowy ground shows Winter's snow, not its stored colours`() {
        val c = defaultCustomizationFor("autumn").copy(fallColorsEnabled = false, winterColorsEnabled = true)
        val state = SettingsUiModel.winterLandscape(c)!!
        assertEquals(GroundSnow.WINTER_GROUND_DAY, state.hillsDay)
        assertEquals(GroundSnow.WINTER_GROUND_NIGHT, state.hillsNight)
        assertTrue("not the stored hills", state.hillsDay != c.hillsColorDay)
        assertTrue("not the stored mountains", state.mountainsFrontDay != c.mountainsFront.colorDay)
    }

    @Test
    fun `with the palette off, or in the editable version, the pages show the stored colours`() {
        val autumn = defaultCustomizationFor("autumn")
        assertFalse(autumn.winterColorsEnabled)
        assertNull(SettingsUiModel.winterLandscape(autumn))
        val winterOff = defaultCustomizationFor("winter").copy(winterColorsEnabled = false)
        assertNull(SettingsUiModel.winterLandscape(winterOff))
        assertNull(SettingsUiModel.winterLandscape(defaultCustomizationFor("winter"), readOnly = false))
    }

    @Test
    fun `the line names the palette as the settings call it`() {
        assertEquals(
            "Snow while the Winter palette is on: your colours come back when you turn it off.",
            SettingsUiModel.WINTER_LANDSCAPE_LINE,
        )
    }

    /**
     * The Hills and the Mountains pages draw from the rule: each asks [SettingsUiModel.winterLandscape], and while it
     * answers shows the snow read-only ([WinterOwnedColorPair], swatches that take no tap) in place of the editable pair.
     * Whole lines of `WorldSceneScreen.kt` and `SceneCategorySections.kt`, the comments taken off, as the project does
     * for Compose it cannot run on the JVM.
     */
    @Test
    fun `the Hills and Mountains pages show the winter-owned rows from the rule`() {
        val world = codeLines(source("ui/WorldSceneScreen.kt"))
        val hills = functionBody(world, "private fun HillsSubScreen(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {")
        assertInARow(
            "Hills",
            hills,
            "val winter = SettingsUiModel.winterLandscape(customization)",
            "SettingsFormSubScreen(\"Hills\", onBack) {",
            "if (winter != null) {",
            "WinterOwnedColorPair(dayColor = winter.hillsDay, nightColor = winter.hillsNight, line = winter.line)",
            "} else {",
            "DayNightColorPair(",
        )
        val mountains = functionBody(world, "private fun MountainsSubScreen(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {")
        assertTrue("Mountains asks the rule", "val winter = SettingsUiModel.winterLandscape(customization)" in mountains)
        assertEquals("both layers are handed the answer", 2, mountains.count { it == "winter = winter," })
        val sections = codeLines(source("ui/SceneCategorySections.kt"))
        val layer = functionBody(sections, "internal fun MountainLayerSection(")
        assertTrue("winter: WinterLandscapeUiState? = null," in layer)
        assertInARow(
            "a mountain layer",
            layer,
            "if (winter != null) {",
            "WinterOwnedColorPair(",
            "dayColor = if (front) winter.mountainsFrontDay else winter.mountainsBackDay,",
            "nightColor = if (front) winter.mountainsFrontNight else winter.mountainsBackNight,",
            "line = winter.line,",
            ")",
            "} else {",
            "DayNightColorPair(",
        )
        assertInARow(
            "the winter-owned pair: two swatches that take no tap, and the line",
            functionBody(sections, "internal fun WinterOwnedColorPair(dayColor: Int, nightColor: Int, line: String) {"),
            "ColorSwatchRow(\"Day Color\", dayColor, enabled = false, onClick = {})",
            "ColorSwatchRow(\"Night Color\", nightColor, enabled = false, onClick = {})",
            "Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)",
        )
    }

    /** [lines] from [head] to the next function or `@Composable` after it. */
    private fun functionBody(lines: List<String>, head: String): List<String> {
        val start = lines.indexOf(head)
        assertTrue("no line `$head`", start >= 0)
        val end = (start + 1 until lines.size).firstOrNull { i ->
            val l = lines[i]
            l == "@Composable" || l.startsWith("fun ") || l.startsWith("private fun ") || l.startsWith("internal fun ")
        } ?: lines.size
        return lines.subList(start, end)
    }

    /** [lines] holds [expected] one after the other. */
    private fun assertInARow(message: String, lines: List<String>, vararg expected: String) {
        val at = lines.indexOf(expected.first())
        assertTrue("$message: no line `${expected.first()}`", at >= 0)
        assertEquals(message, expected.toList(), lines.subList(at, minOf(lines.size, at + expected.size)))
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
