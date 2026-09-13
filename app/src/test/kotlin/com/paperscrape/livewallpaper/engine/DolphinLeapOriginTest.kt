package com.paperscrape.livewallpaper.engine

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The dolphin's drawn centre sits on the point its leap arc is computed for.
 *
 * ### Why this is a test and not a comment
 *
 * `DOLPHIN_ORIGIN_X_UNITS` and `_Y_UNITS` place the sprite's pixel (0,0), and what they are
 * *for* is landing the animal on its leap point. Nothing enforced that, and the sentence that
 * justified the pair went wrong twice over four releases:
 *
 * - v4.26 redrew the dolphin inside the same canvas, moving the ink off the edges. The comment
 *   still said *"filled edge to edge"*, from which the old pair followed, so the animal drifted
 *   **(+0.87, +1.0) units** off its leap point and the prose said it had not.
 * - v4.31 measured that drift and corrected the prose — and its replacement derivation stated a
 *   `342x168` canvas with ink from row 0 and a centre at `(57.167, 28.0)`, which gives a y
 *   displacement of **zero**, contradicting the `+1.0` in its own next sentence. The PNG is
 *   `342x171` with ink from row 3.
 *
 * Twice, a correct conclusion was carried by arithmetic that was wrong, and twice nothing failed.
 * A comment cannot be checked; a content box can. This reads the **alpha channel of the shipped
 * PNG**, which is the artwork itself rather than any declaration of it, and is the same rule
 * `SpriteMeasurementClaimTest` applies to the prose: re-measure, do not re-type.
 *
 * ### What is asserted
 *
 * That the origin is the negative of the content centre, in local units — so if the dolphin is
 * redrawn again, inside this canvas or another one, this fails until the pair follows the ink.
 */
class DolphinLeapOriginTest {

    @Test
    fun `the dolphin's drawn centre lands on its leap point`() {
        val image = ImageIO.read(File(drawableDir, "dolphin_body.png"))
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var bottom = Int.MIN_VALUE
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if ((image.getRGB(x, y) ushr 24) == 0) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        // The content box the registry declares, re-measured: [1, 3, 342, 171].
        assertEquals("content left", 1, left)
        assertEquals("content top", 3, top)
        assertEquals("content right (exclusive)", 342, right + 1)
        assertEquals("content bottom (exclusive)", 171, bottom + 1)

        val unit = SpriteBlitter.SPRITE_PIXELS_PER_UNIT
        val centreX = (left + right + 1) / 2f / unit
        val centreY = (top + bottom + 1) / 2f / unit
        assertEquals("the drawing's centre in local units, x", 57.166668f, centreX, 0.0005f)
        assertEquals("the drawing's centre in local units, y", 29f, centreY, 0.0005f)

        // The whole claim: the blit origin is the negative of that centre, so the ink's middle
        // lands on (0,0) -- the leap point [drawDolphin] rotates and arcs about.
        assertEquals(
            "the dolphin is off its leap point in x",
            0f,
            PaperRenderer.DOLPHIN_ORIGIN_X_UNITS + centreX,
            0.0005f,
        )
        assertEquals(
            "the dolphin is off its leap point in y",
            0f,
            PaperRenderer.DOLPHIN_ORIGIN_Y_UNITS + centreY,
            0.0005f,
        )
        // The pair it moved from, named so a revert is unambiguous rather than a silent slide.
        assertEquals("the drifted x is gone", false, PaperRenderer.DOLPHIN_ORIGIN_X_UNITS == -56.3f)
        assertEquals("the drifted y is gone", false, PaperRenderer.DOLPHIN_ORIGIN_Y_UNITS == -28f)
    }

    private val drawableDir: File by lazy {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = File(dir, "${prefix}src/main/res/drawable-nodpi")
                if (candidate.isDirectory) return@lazy candidate
            }
            dir = dir.parentFile
        }
        error("could not locate src/main/res/drawable-nodpi")
    }
}
