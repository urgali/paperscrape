package com.paperscrape.livewallpaper.engine

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REN-07: the numbers written about sprites are the numbers the sprites have.
 *
 * The audit found several load-bearing comments describing artwork that no longer ships -- a bird
 * "90x42" that is 90x24, a dolphin "360x225" that is 345x174, a canopy "270x252" that is 246x222,
 * and a window-head canvas "60 units wide" that is 53. The live constants were mostly right; the
 * comments were what a future edit would be derived from, which is the whole risk.
 *
 * This reads the PNGs so the corrected numbers cannot rot again, and separates the two kinds of
 * claim: a **measurement** must match the artwork, and a **tuned value** must not pretend to be one.
 */
class SpriteMeasurementClaimTest {

    @Test
    fun `the sprites are the sizes the comments now say`() {
        val expected = mapOf(
            "bird_body" to (90 to 24),
            "dolphin_body" to (345 to 174),
            "tree_canopy" to (246 to 222),
            "person_man_summer_head_window" to (159 to 171),
        )
        for ((name, size) in expected) {
            val image = ImageIO.read(File(drawableDir(), "$name.png"))
            assertEquals("$name width", size.first, image.width)
            assertEquals("$name height", size.second, image.height)
        }
    }

    @Test
    fun `the window occupant divisor is a tuned value, not the canvas width`() {
        // The distinction the comment used to get wrong. 60 is deliberate and 53 is the canvas;
        // asserting both is what stops somebody "fixing" one into the other.
        val canvasUnits = ImageIO.read(File(drawableDir(), "person_man_summer_head_window.png")).width /
            SpriteBlitter.SPRITE_PIXELS_PER_UNIT
        assertEquals("the canvas really is 53 units", 53f, canvasUnits, 0.001f)
        assertEquals(
            "the divisor is 60 and is not the canvas width",
            60f,
            SceneObjectRenderer.WINDOW_OCCUPANT_DIVISOR_UNITS,
            0.001f,
        )
        assertTrue(
            "so an occupant is drawn narrower than the nominal 85% of its pane",
            canvasUnits / SceneObjectRenderer.WINDOW_OCCUPANT_DIVISOR_UNITS < 1f,
        )
    }

    @Test
    fun `a size attributed to a named sprite is that sprite's size`() {
        // The precise form of the rule. A comment may quote a size that no longer ships -- the
        // cloud origin explains its own defect by naming the 768x510 canvas that never existed --
        // so what is checked is a size *attributed to a sprite*: `name` ... `NxM px`. That is the
        // shape every stale measurement in REN-07 had, and the shape a new one would have.
        val claim = Regex("""`([a-z0-9_]+)(?:\.png)?`[^`\n]{0,40}?is (\d{2,4})x(\d{2,4}) px""")
        var checked = 0
        for (name in listOf("SceneObjectRenderer.kt", "PaperRenderer.kt")) {
            val source = File(sourceDir(), name).readText()
            for (match in claim.findAll(source)) {
                val sprite = File(drawableDir(), match.groupValues[1] + ".png")
                if (!sprite.isFile) continue
                val image = ImageIO.read(sprite)
                assertEquals(
                    name + " says " + match.groupValues[1] + " is " +
                        match.groupValues[2] + "x" + match.groupValues[3],
                    image.width.toString() + "x" + image.height,
                    match.groupValues[2] + "x" + match.groupValues[3],
                )
                checked++
            }
        }
        assertTrue("the pattern matched nothing, so this test proves nothing", checked > 0)
    }

    private fun drawableDir(): File = walkUp("src/main/res/drawable-nodpi")

    private fun sourceDir(): File = walkUp("src/main/kotlin/com/paperscrape/livewallpaper/engine")

    private fun walkUp(suffix: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = File(dir, prefix + suffix)
                if (candidate.isDirectory) return candidate
            }
            dir = dir.parentFile
        }
        error("could not locate " + suffix)
    }
}
