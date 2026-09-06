package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * The star field is drawn two ways -- a blitted sparkle for one star in
 * [PaperRenderer.STAR_SPARKLE_EVERY], a filled circle for the rest -- and
 * [PaperRenderer.STAR_POINT_COLOR]'s whole job is to make those two the same star. It is a hex
 * literal in Kotlin and the sparkle's colour lives in a PNG, so nothing connected them.
 *
 * **They were not the same, and had not been for four releases.** The constant was `#FFF6DC`; the
 * shipped `star_sparkle.png` is, and always was, `#FBF4E6`. The gap is 4/2/10 levels -- invisible
 * on a two-pixel dot, which is exactly why it survived: a claim that cannot be seen to be false
 * cannot be caught by looking at the device, only by comparing the two artefacts that make it.
 * v4.23's redraw of the sparkle is what made the pair worth reconciling, and this test is what
 * stops it drifting apart again: the next redraw either keeps the cream or comes back here.
 *
 * The colour is read from the PNG rather than restated, for the same reason `SkySpriteAnchoringTest`
 * reads the PNG's header rather than restating its size -- a test that states both sides of an
 * equality agrees only with itself.
 */
class StarFieldColourTest {

    @Test
    fun `the point stars are drawn in the sparkle artwork's own cream`() {
        val counts = mutableMapOf<Int, Int>()
        val image = ImageIO.read(File(drawableDir(), "star_sparkle.png"))
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val argb = image.getRGB(x, y)
                // Fully opaque only: an antialiased edge pixel is the cream blended towards the
                // sky behind it, and averaging those in would invent a colour the artwork does
                // not contain.
                if ((argb ushr 24) and 0xFF == 0xFF) {
                    counts[argb] = (counts[argb] ?: 0) + 1
                }
            }
        }

        assertTrue("star_sparkle.png has no fully opaque pixels at all", counts.isNotEmpty())
        assertEquals(
            "star_sparkle.png is drawn in ${counts.keys.size} fully opaque colours " +
                "(${counts.keys.joinToString { "#%08X".format(it) }}), so \"the cream the " +
                "sparkle art is drawn in\" no longer names one colour and STAR_POINT_COLOR " +
                "cannot be checked against it",
            1, counts.keys.size,
        )
        assertEquals(
            "STAR_POINT_COLOR is #%08X but the sparkle is drawn in #%08X, so a point star and a "
                .format(PaperRenderer.STAR_POINT_COLOR, counts.keys.first()) +
                "sparkle are not the same star",
            counts.keys.first(),
            PaperRenderer.STAR_POINT_COLOR,
        )
    }

    private fun drawableDir(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = File(dir, prefix + "src/main/res/drawable-nodpi")
                if (candidate.isDirectory) return candidate
            }
            dir = dir.parentFile
        }
        error("could not locate src/main/res/drawable-nodpi")
    }
}
