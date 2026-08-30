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
 */
class SkyscraperWindowTest {

    private val renderer: String by lazy { rendererSource().readText() }

    @Test
    fun `the tower's window grid is tinted, not laid over untinted`() {
        // The mutation this catches: going back to `drawSpriteFaded(..., litWindowAlpha(...))`,
        // which is what made the tower's daytime windows take the wall's own colour -- a window
        // the colour of the bricks around it, which no other building in the scene has.
        val call = blitOf("R.drawable.skyscraper_wall_lit")
        assertTrue(
            "the tower's window grid must be tinted, not blitted as authored; found:\n$call",
            call.contains("drawTintedSprite"),
        )
        assertTrue(
            "and its colour must come from windowGlassColor, not from a colour of its own; found:\n$call",
            call.contains("windowGlassColor("),
        )
    }

    @Test
    fun `the tower and the restaurant ask the same function what a window looks like`() {
        // One authority, checked rather than asserted in a comment. A second crossfade beside this
        // one is how the two would drift into two different ideas of "warm".
        val tower = blitOf("R.drawable.skyscraper_wall_lit")
        val restaurant = blitOf("R.drawable.restaurant_window")
        assertTrue("the restaurant window must go through windowGlassColor too:\n$restaurant",
            restaurant.contains("windowGlassColor("))
        assertTrue("and so must the tower:\n$tower", tower.contains("windowGlassColor("))
    }

    @Test
    fun `the crossfade runs from day to night and not the other way round`() {
        // The mutation this catches: blendARGB(NIGHT, DAY, nightGlow), which lights the windows at
        // noon and cools them at midnight. Every golden catches it; nothing on the JVM did.
        val body = Regex("""private fun windowGlassColor\([^)]*\)[^\n]*\n?[^\n]*""")
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

    /** Walks up for the module root, the way `SpriteTintClassTest` finds `drawable-nodpi`. */
    private fun rendererSource(): File {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/engine/SceneObjectRenderer.kt"
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
