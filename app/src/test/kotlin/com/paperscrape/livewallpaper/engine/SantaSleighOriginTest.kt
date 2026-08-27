package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * Pins where the sleigh's *drawing* lands, so a change to its canvas cannot move it.
 *
 * [PaperRenderer.SANTA_SLEIGH_ORIGIN_X_UNITS] and [PaperRenderer.SANTA_SLEIGH_ORIGIN_Y_UNITS]
 * address the sprite's **canvas corner**, not its content. That is a deliberate choice -- the
 * renderer does not read the asset manifest, and the sleigh is placed by a flight point of its
 * own -- but it has a consequence: the moment the canvas changes, those two numbers stop meaning
 * what they meant, and nothing about the PNG says so.
 *
 * It has already happened twice. v4.7 redrew the artwork inside an unchanged canvas and the
 * drawing's centre moved by (-3, +3) sprite pixels; that was accepted and recorded, but nothing
 * failed. Then the canvas itself was normalised -- 600x153 to 594x123, eighteen fully transparent
 * rows off the top -- which without a matching origin change would have flown the whole group six
 * local units too high, with the Python tooling still green and no golden containing the sleigh
 * to catch it.
 *
 * So the property asserted here is not "the constant equals -19.5". That would pass a crop that
 * forgot to compensate just as happily as one that did, because it would have been edited to
 * match. It is instead the thing that must not move:
 *
 *     content's position in local units  =  origin  +  alpha bounding box / pixels per unit
 *
 * Both halves are read from what actually ships -- the constants from `PaperRenderer`, the box
 * from the PNG's own pixels -- so a crop, a compensation, or an art pass that shifts the drawing
 * inside its canvas all reach this test. Only the answer is written down.
 */
class SantaSleighOriginTest {

    /**
     * Where the drawing has sat since the sprite became a `SCENE_UNITS` blit, in the local units
     * the call site works in, measured from the flight point the effect hands it.
     *
     * Written down because there is nothing left to derive it from: it is the position itself.
     * Moving the artwork on purpose means changing these and saying why -- the same bargain the
     * project makes elsewhere for a number that records a decision rather than a measurement.
     */
    private companion object {
        const val CONTENT_LEFT_UNITS = -99.67f
        const val CONTENT_TOP_UNITS = -19.1667f
        const val CONTENT_WIDTH_UNITS = 197.3333f
        const val CONTENT_HEIGHT_UNITS = 40.3333f

        /** A hundredth of a local unit is three hundredths of a sprite pixel. */
        const val TOLERANCE = 0.01f

        val FRAMES = listOf("santa_sleigh_scene", "santa_sleigh_trot")

        /**
         * Gradle runs unit tests with the module directory as the working directory, but that is
         * a default rather than a guarantee, so walk up until the drawable directory is found
         * instead of assuming a fixed depth.
         */
        val drawableDir: File by lazy {
            var dir: File? = File(".").absoluteFile
            while (dir != null) {
                for (prefix in listOf("", "app/")) {
                    val candidate = File(dir, "${prefix}src/main/res/drawable-nodpi")
                    if (candidate.isDirectory) return@lazy candidate
                }
                dir = dir.parentFile
            }
            throw AssertionError(
                "could not locate src/main/res/drawable-nodpi from ${File(".").absolutePath}",
            )
        }
    }

    /** The alpha bounding box of what the sprite actually draws, right and bottom exclusive. */
    private data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
    }

    private fun contentBox(name: String): Box {
        val file = File(drawableDir, "$name.png")
        assertTrue("${file.path} does not exist", file.isFile)
        val image = ImageIO.read(file)
        var left = image.width
        var top = image.height
        var right = 0
        var bottom = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if ((image.getRGB(x, y) ushr 24) == 0) continue
                if (x < left) left = x
                if (y < top) top = y
                if (x >= right) right = x + 1
                if (y >= bottom) bottom = y + 1
            }
        }
        assertTrue("$name draws nothing at all", right > left && bottom > top)
        return Box(left, top, right, bottom)
    }

    private fun canvasSize(name: String): Pair<Int, Int> {
        val image = ImageIO.read(File(drawableDir, "$name.png"))
        return image.width to image.height
    }

    @Test
    fun `the drawing lands where it always has, whatever the canvas around it is`() {
        val unit = SpriteBlitter.SPRITE_PIXELS_PER_UNIT
        for (name in FRAMES) {
            val box = contentBox(name)
            val left = PaperRenderer.SANTA_SLEIGH_ORIGIN_X_UNITS + box.left / unit
            val top = PaperRenderer.SANTA_SLEIGH_ORIGIN_Y_UNITS + box.top / unit
            assertEquals(
                "$name: the drawing's left edge moved -- if the canvas was cropped, " +
                    "SANTA_SLEIGH_ORIGIN_X_UNITS has to move by the same trim",
                CONTENT_LEFT_UNITS,
                left,
                TOLERANCE,
            )
            assertEquals(
                "$name: the drawing's top edge moved -- if the canvas was cropped, " +
                    "SANTA_SLEIGH_ORIGIN_Y_UNITS has to move by the same trim",
                CONTENT_TOP_UNITS,
                top,
                TOLERANCE,
            )
        }
    }

    @Test
    fun `the drawing keeps its size, so a crop never took artwork with it`() {
        val unit = SpriteBlitter.SPRITE_PIXELS_PER_UNIT
        for (name in FRAMES) {
            val box = contentBox(name)
            assertEquals("$name: content width changed", CONTENT_WIDTH_UNITS, box.width / unit, TOLERANCE)
            assertEquals("$name: content height changed", CONTENT_HEIGHT_UNITS, box.height / unit, TOLERANCE)
        }
    }

    @Test
    fun `both frames are blitted through one transform, so the animation cannot jump`() {
        // There is a single pair of origin constants, so the two frames can only stay aligned if
        // they also share a canvas and put their drawing at the same place inside it. Crop one
        // and not the other -- or crop them to different rectangles -- and the sleigh would step
        // sideways twice a second while the reindeer trot.
        val sizes = FRAMES.map { canvasSize(it) }.distinct()
        assertEquals("the two frames no longer share a canvas: $sizes", 1, sizes.size)
        val boxes = FRAMES.map { contentBox(it) }.distinct()
        assertEquals("the two frames no longer share a content box: $boxes", 1, boxes.size)
    }

    @Test
    fun `the two frames are still different drawings`() {
        // The trot exists to move the reindeer's legs. Two identical files would animate nothing
        // while every geometric assertion above still passed.
        val pixels = FRAMES.map { name ->
            val image = ImageIO.read(File(drawableDir, "$name.png"))
            IntArray(image.width * image.height).also {
                image.getRGB(0, 0, image.width, image.height, it, 0, image.width)
            }.toList()
        }
        assertNotEquals(
            "santa_sleigh_scene and santa_sleigh_trot are byte-identical, so the trot animates nothing",
            pixels[0],
            pixels[1],
        )
    }
}
