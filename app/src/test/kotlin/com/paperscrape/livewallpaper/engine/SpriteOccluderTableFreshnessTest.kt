package com.paperscrape.livewallpaper.engine

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SpriteOccluderTable] must still describe the artwork that ships.
 *
 * ### Why this is a test and not a comment in the generator
 *
 * The table is written by `tools/assets/build_occluder_table.py`, and Gradle never runs that
 * tooling -- by design (`tools/assets/README.md`). So the table is a generated file that nothing
 * regenerates: exactly the shape of thing that goes stale the first time somebody redraws a
 * sprite and does not know it exists. That is not a hypothetical failure mode, it is the one this
 * project has already had twice. `reports/runtime-inventory.json` described a palm crown at
 * 120x120 px for the whole of v5.1 and into v5.2 while the shipped drawing was 168x144, and
 * `reports/fidelity.json` said the same thing; both were found by an audit that went looking,
 * not by anything failing.
 *
 * **A generated number that nothing checks is a number that is eventually wrong**, and the
 * occluder table is worse than a report in that respect, because the layout pass acts on it.
 * So it is re-measured here, in Kotlin, straight from the PNG, without the generator -- and it is
 * re-measured in CI, which runs `./gradlew test` on every push.
 *
 * ### What makes this an independent measurement
 *
 * The generator uses Pillow and numpy and reads the alpha channel as an array; this decodes with
 * `ImageIO` and walks pixels. Neither can borrow the other's answer. What they share is the PNG,
 * which is the point: the object of the measurement is the artwork, and the two routes to it are
 * written separately so that agreeing means something.
 *
 * The origins are read from [PalmSpriteLayout] and [TreeSpriteLayout] here, not copied from the
 * table, so a crown whose blit origin moves without the table being regenerated fails here too --
 * which is the v5.1 palm redraw, replayed.
 *
 * ### The decode
 *
 * `ImageIO` rather than the IHDR-only reader `SpriteGeometryTest` and `SkySpriteAnchoringTest`
 * share: those two need width and height, and this needs every pixel's alpha. It is a plain JVM
 * unit test, so `java.desktop` is on the classpath; nothing Android is touched.
 */
class SpriteOccluderTableFreshnessTest {

    /** The families, paired with the drawings and the object-space origin they are stated at. */
    private val families = listOf(
        Triple(
            SpriteOccluderTable.PALM_CROWN,
            listOf("palmtree_fronds", "palmtree_fronds_dead", "palmtree_fronds_frost"),
            PalmSpriteLayout.CROWN_X to PalmSpriteLayout.CROWN_Y,
        ),
        Triple(
            SpriteOccluderTable.TREE_CROWN,
            listOf("tree_canopy", "tree_dead_branches"),
            // The crown is blitted at CANOPY_* inside `drawTree`'s -38 lift, and the occlusion
            // geometry is stated in the object's own space, which is the flattened pair.
            TreeSpriteLayout.FLAT_CANOPY_X to TreeSpriteLayout.FLAT_CANOPY_Y,
        ),
    )

    @Test
    fun `every declared crown still matches the drawing it was measured from`() {
        for ((declared, sprites, origin) in families) {
            assertEquals(
                "the table declares ${declared.size} drawings for a family the renderer draws " +
                    "${sprites.size} of -- regenerate with tools/assets/build_occluder_table.py",
                sprites.size,
                declared.size,
            )
            for ((index, name) in sprites.withIndex()) {
                val measured = measure(name, origin.first, origin.second)
                val entry = declared[index]
                assertBox(name, entry, measured)
            }
        }
    }

    /**
     * The parasol's fan is not a PNG, so its entry is checked against the shape's own arithmetic.
     *
     * A filled half-disc touches its circumscribed rectangle along the full diameter and along the
     * full radius, so **both factors are exactly 1** and this model leaves it alone -- which is
     * the property that separates it from the two models it was chosen over, and is worth a test
     * of its own rather than a sentence. The box is `drawParasol`'s own: radius 34 about the pole,
     * from `g-84` to `g-50`.
     */
    @Test
    fun `the parasol fan declares the rectangle its own geometry circumscribes`() {
        val fan = SpriteOccluderTable.PARASOL_FAN.single()
        assertEquals("the fan's left edge", -34f, fan.contentLeft, 1e-4f)
        assertEquals("the fan's right edge", 34f, fan.contentRight, 1e-4f)
        assertEquals("the fan's top edge", -84f, fan.contentTop, 1e-4f)
        assertEquals("the fan's bottom edge", -50f, fan.contentBottom, 1e-4f)
        assertEquals("a filled half-disc has one full row", 1f, fan.rowMax, 1e-3f)
        assertEquals("a filled half-disc has one full column", 1f, fan.columnMax, 1e-3f)
    }

    /**
     * The model's own contract, stated where it can fail: a declared factor is a fraction of a
     * box, so it is in `(0, 1]`, and a factor of 1 means the drawing reaches that extreme.
     *
     * This is what makes the table safe to read without re-deriving it: whatever the artwork, the
     * rectangle the catalogue builds is inside the content box and no smaller than nothing.
     */
    @Test
    fun `every declared factor is a fraction of its box`() {
        val all = SpriteOccluderTable.PALM_CROWN +
            SpriteOccluderTable.TREE_CROWN +
            SpriteOccluderTable.PARASOL_FAN
        for (ink in all) {
            assertTrue("a content box with no width: ${ink.contentLeft}..${ink.contentRight}",
                ink.contentRight > ink.contentLeft)
            assertTrue("a content box with no height: ${ink.contentTop}..${ink.contentBottom}",
                ink.contentBottom > ink.contentTop)
            assertTrue("rowMax ${ink.rowMax} is not a fraction", ink.rowMax > 0f && ink.rowMax <= 1f)
            assertTrue("columnMax ${ink.columnMax} is not a fraction",
                ink.columnMax > 0f && ink.columnMax <= 1f)
        }
    }

    // ---- the measurement, written here rather than borrowed ---------------------------------

    private class Measured(
        val left: Float, val top: Float, val right: Float, val bottom: Float,
        val rowMax: Float, val columnMax: Float,
    )

    private fun measure(name: String, originX: Float, originY: Float): Measured {
        val file = File(drawableDir, "$name.png")
        assertTrue("${file.path} does not exist", file.isFile)
        val image = ImageIO.read(file) ?: throw AssertionError("${file.name} would not decode")
        val width = image.width
        val height = image.height

        // Ink is any alpha above zero, the same reading the generator states and argues for: an
        // antialiased edge stops part of what is behind it, and declaring a shop unhidden when it
        // is hidden is the failure that matters.
        val ink = Array(height) { y -> BooleanArray(width) { x -> (image.getRGB(x, y) ushr 24) != 0 } }

        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!ink[y][x]) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        assertTrue("$name is entirely transparent", right >= left && bottom >= top)
        val contentW = right - left + 1
        val contentH = bottom - top + 1

        var widestRow = 0
        for (y in top..bottom) {
            var count = 0
            for (x in left..right) if (ink[y][x]) count++
            if (count > widestRow) widestRow = count
        }
        var tallestColumn = 0
        for (x in left..right) {
            var count = 0
            for (y in top..bottom) if (ink[y][x]) count++
            if (count > tallestColumn) tallestColumn = count
        }

        // Canvas pixels to object units, by this file's own arithmetic: the authoring oversample
        // is the same constant the blitter divides by.
        val unit = SpriteBlitter.SPRITE_PIXELS_PER_UNIT
        return Measured(
            left = originX + left / unit,
            top = originY + top / unit,
            right = originX + (right + 1) / unit,
            bottom = originY + (bottom + 1) / unit,
            rowMax = widestRow.toFloat() / contentW,
            columnMax = tallestColumn.toFloat() / contentH,
        )
    }

    private fun assertBox(name: String, entry: SpriteOccluderTable.InkBox, measured: Measured) {
        val fix = "-- regenerate with tools/assets/build_occluder_table.py"
        assertEquals("$name: the declared left edge $fix", measured.left, entry.contentLeft, 1e-3f)
        assertEquals("$name: the declared top edge $fix", measured.top, entry.contentTop, 1e-3f)
        assertEquals("$name: the declared right edge $fix", measured.right, entry.contentRight, 1e-3f)
        assertEquals("$name: the declared bottom edge $fix", measured.bottom, entry.contentBottom, 1e-3f)
        assertEquals("$name: the declared widest row $fix", measured.rowMax, entry.rowMax, 1e-5f)
        assertEquals("$name: the declared tallest column $fix", measured.columnMax, entry.columnMax, 1e-5f)
    }

    private companion object {
        /** The same walk-up `SpriteGeometryTest` uses: the working directory is a default, not a
         * guarantee. */
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
}
