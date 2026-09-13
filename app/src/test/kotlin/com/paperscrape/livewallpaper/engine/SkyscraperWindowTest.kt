package com.paperscrape.livewallpaper.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A window is cool glass by day and warm light at night, and there is one place that decides it.
 *
 * ### Why this test reads the source
 *
 * Mutation testing put it here. Two mutations of the v4.12 window work -- swapping the day and
 * night ends of the crossfade, and reverting the tower to the untinted faded blit it used before --
 * were both **missed by the whole JVM suite**. `SpriteTintClassTest` did not catch them because its
 * notion of "tinted" is a hand-written list, not the call sites; and the goldens that do catch them
 * are instrumented, so on a machine with no device the rule was unprotected.
 *
 * The rule is a *coupling* -- two call sites must go through one function, in one order -- and a
 * coupling between call sites is what the source states. `tools/assets`' own `validate` already
 * checks blit call sites this way, so this is the project's existing idea applied one level in.
 *
 * ### What v5.0 changed about it
 *
 * The two call sites the mutations attacked -- `skyscraper_wall_lit` and `restaurant_window` --
 * are gone with the flat facades. Every window in the neighbourhood is now a `GLASS_MASK` part,
 * and the composer blits all of them with one colour computed once per building, so the coupling
 * this test was written to protect is no longer *between* call sites: there is **one**. That makes
 * the rule easier to state and cheaper to break in exactly one way, which is what is checked
 * below -- that the one call site still asks [SceneObjectRenderer.windowGlassColor], that the
 * gallery asks the same function rather than growing a second crossfade of its own, and that a
 * house's glass is not scaled by opening hours while a shop's is.
 */
class SkyscraperWindowTest {

    private val renderer: String by lazy { rendererSource().readText() }

    @Test
    fun `every window in the neighbourhood is tinted by windowGlassColor, at one call site`() {
        // The mutation this catches, in its v5.0 shape: blitting a glass mask untinted, or with
        // the wall colour, which is what made the tower's daytime windows take the colour of the
        // bricks around them -- a window the colour of its own wall, which no other building in
        // the scene has.
        val branch = Regex("""PartRole\.GLASS_MASK ->\s*\n?[^\n]*\n?[^\n]*""")
            .find(renderer)?.value
            ?: error("no GLASS_MASK branch in SceneObjectRenderer.kt")
        assertTrue(
            "a glass mask must be an added tint, not an untinted blit; found:\n$branch",
            branch.contains("drawTintedAdded"),
        )
        assertTrue(
            "and it must carry glassColor, not the wall; found:\n$branch",
            branch.contains("glassColor") && !branch.contains("wallColor"),
        )
        val source = Regex("""val glassColor = [^\n]*""").find(renderer)?.value
            ?: error("glassColor is not computed in SceneObjectRenderer.kt")
        assertTrue(
            "glassColor must come from windowGlassColor, not from a colour of its own; found:\n$source",
            source.contains("windowGlassColor("),
        )
        assertEquals(
            "there must be exactly one glass colour in the renderer: a second is how two ideas " +
                "of \"warm\" drift apart",
            1,
            Regex("""val glassColor = """).findAll(renderer).count(),
        )
    }

    @Test
    fun `the gallery asks the same function what a window looks like`() {
        // One authority across the two things that draw a building. The preview used to have no
        // window colour at all -- it blitted the lit artwork as authored -- and giving it one in
        // v5.0 is exactly the moment a second crossfade could have appeared.
        val preview = previewSource().readText()
        assertTrue(
            "the gallery preview must read windowGlassColor rather than blend two colours itself",
            preview.contains("SceneObjectRenderer.windowGlassColor("),
        )
        assertTrue(
            "and it must not name the two ends itself",
            !preview.contains("WINDOW_GLASS_DAY") && !preview.contains("WINDOW_GLASS_NIGHT"),
        )
    }

    @Test
    fun `a shop's glass follows opening hours and a house's never does`() {
        // One of the six behaviours the redraw had to keep. Before v5.0 it was five separate
        // draw functions each remembering it; now it is one line, which is worth pinning because
        // one line is also all it takes to lose it.
        val line = Regex("""val glassNight = [^\n]*""").find(renderer)?.value
            ?: error("glassNight is not computed in SceneObjectRenderer.kt")
        assertTrue(
            "the house branch must not be scaled by businessOpenness; found:\n$line",
            line.contains("WindowBuildingKind.HOUSE"),
        )
        assertTrue(
            "and the other branch must be; found:\n$line",
            line.contains("businessOpenness"),
        )
        assertTrue(
            "businessOpenness must be on the else side of the house test; found:\n$line",
            line.indexOf("WindowBuildingKind.HOUSE") < line.indexOf("businessOpenness"),
        )
    }

    @Test
    fun `the crossfade runs from day to night and not the other way round`() {
        // The mutation this catches: blendARGB(NIGHT, DAY, nightGlow), which lights the windows at
        // noon and cools them at midnight. Every golden catches it; nothing on the JVM did.
        val body = Regex("""fun windowGlassColor\([^)]*\)[^\n]*\n?[^\n]*""")
            .find(renderer)?.value
            ?: error("windowGlassColor not found in SceneObjectRenderer.kt")
        val day = body.indexOf("WINDOW_GLASS_DAY")
        val night = body.indexOf("WINDOW_GLASS_NIGHT")
        assertTrue("both ends must appear in the crossfade; found:\n$body", day >= 0 && night >= 0)
        assertTrue(
            "the day colour is the `from` end of blendARGB and must come first; found:\n$body",
            day < night,
        )
        assertTrue("and it must be driven by nightGlow; found:\n$body", body.contains("nightGlow"))
    }

    @Test
    fun `the two ends are actually cool and actually warm`() {
        // Named constants can be renamed into a lie. These assert what the names claim, on the
        // channels themselves, so a "day" colour that is warm fails here rather than on a screen.
        val day = SceneObjectRenderer.WINDOW_GLASS_DAY
        val night = SceneObjectRenderer.WINDOW_GLASS_NIGHT
        assertTrue("WINDOW_GLASS_DAY must be cool: blue above red", blue(day) > red(day))
        assertTrue("WINDOW_GLASS_NIGHT must be warm: red above blue", red(night) > blue(night))
        // Both are multiplied over a white mask, so both must stay light enough to carry a colour
        // -- the same rule `SpriteTintClassTest` applies to the artwork they are multiplied by.
        assertTrue("WINDOW_GLASS_DAY is too dark to read as glass", red(day) > 128 && blue(day) > 128)
        assertTrue("WINDOW_GLASS_NIGHT is too dark to read as light", red(night) > 128 && green(night) > 128)
        assertEquals("both must be fully opaque", 0xFF, day ushr 24 and 0xFF)
        assertEquals("both must be fully opaque", 0xFF, night ushr 24 and 0xFF)
    }

    /** The whole blit call for [drawable], call site and arguments, as written. */
    private fun blitOf(drawable: String): String {
        val at = renderer.indexOf(drawable)
        require(at > 0) { "$drawable is not blitted anywhere in SceneObjectRenderer.kt" }
        val start = renderer.lastIndexOf("draw", at).coerceAtLeast(0)
        val end = renderer.indexOf(')', renderer.indexOf('\n', at + drawable.length))
        return renderer.substring(start, if (end > start) end + 1 else at + drawable.length)
    }

    private fun previewSource(): File =
        sourceFile("src/main/kotlin/com/paperscrape/livewallpaper/engine/ThemePreviewScene.kt")

    /** Walks up for the module root, the way `SpriteTintClassTest` finds `drawable-nodpi`. */
    private fun rendererSource(): File =
        sourceFile("src/main/kotlin/com/paperscrape/livewallpaper/engine/SceneObjectRenderer.kt")

    private fun sourceFile(suffix: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = File(dir, "$prefix$suffix")
                if (candidate.isFile) return candidate
            }
            dir = dir.parentFile
        }
        error("could not locate $suffix from ${File(".").absolutePath}")
    }

    private fun red(color: Int) = color shr 16 and 0xFF
    private fun green(color: Int) = color shr 8 and 0xFF
    private fun blue(color: Int) = color and 0xFF
}
