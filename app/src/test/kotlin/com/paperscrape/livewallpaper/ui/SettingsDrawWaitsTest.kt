package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.engine.FirstFrameGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **The settings draw nothing until the stored settings and the saved themes have been read**
 * (`DESIGN_NOTES.md` §12, decision 94). Drawn on the defaults-shaped first value, the screen would show a fresh install's
 * theme, *Sunset*, for a moment and then the user's, at every opening of the settings from a stopped process. The
 * logic above the drawing still starts from a fresh install's settings, as the update check's comment says; only the
 * drawing waits, and no longer than the wallpaper's own first frame waits
 * ([FirstFrameGate.WAIT_LIMIT_MS]), so a store that never answers leaves the screen usable.
 *
 * The screen is a `@Composable`, which the JVM does not run: the rule is held on its source, comments taken off and
 * whole lines compared.
 */
class SettingsDrawWaitsTest {

    private val screen: String by lazy { source("ui/SettingsScreen.kt") }

    @Test
    fun `the settings are read as null until the store answers, and the logic runs on a fresh install's meanwhile`() {
        val lines = codeLines(screen)
        assertTrue(
            "the stored settings are not collected as nullable",
            "val storedSettings by prefs.settingsFlow.collectAsState<WallpaperSettings, WallpaperSettings?>(initial = null)" in lines,
        )
        assertTrue("the logic does not fall back on a fresh install's settings", "val settings = storedSettings ?: freshInstall" in lines)
        assertTrue(
            "the saved themes are not null until the store answers",
            "val state = remember(store) { mutableStateOf<CustomThemeData?>(null, neverEqualPolicy()) }" in lines,
        )
    }

    @Test
    fun `nothing is drawn before both have arrived, or before the wait runs out`() {
        val lines = codeLines(screen)
        val gate = lines.indexOf("if (!waitedTooLong && (storedSettings == null || savedThemes.value == null)) return")
        assertTrue("the drawing does not wait for the stored settings and the saved themes", gate >= 0)
        val drawing = lines.indexOf("ProvideSettingsBottomInset {")
        assertTrue("the screen's drawing is gone", drawing >= 0)
        assertEquals("the wait is the line right before the drawing", drawing - 1, gate)
        assertEquals(
            "the wait ends as the wallpaper's first frame's does",
            listOf("LaunchedEffect(Unit) {", "delay(FirstFrameGate.WAIT_LIMIT_MS)", "waitedTooLong = true", "}"),
            lines.subList(gate - 4, gate),
        )
        assertEquals("the wallpaper's own wait", 2_000L, FirstFrameGate.WAIT_LIMIT_MS)
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
