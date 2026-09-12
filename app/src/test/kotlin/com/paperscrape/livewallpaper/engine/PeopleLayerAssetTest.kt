package com.paperscrape.livewallpaper.engine

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The people ship as fixed art plus one weight mask per colourable region (v4.30), and this is what
 * checks the files really are that.
 *
 * ### The claim
 *
 * The engine draws a figure as `fixed + sum over regions of (mask * colour)`, with the sum done by
 * the blend rather than by the shader so that both backends express it
 * (`SceneObjectRenderer.drawPersonLayers`). The claim that makes it legitimate is that this is a
 * **decomposition of the drawing and not an approximation of it**: at the colours the artwork was
 * painted in, the sum is the drawing.
 *
 * ### Why it is checked without a palette
 *
 * The obvious test recomposes at the four colours each shape was drawn in and compares with the
 * drawing. That would mean a second copy of the generator's palette living here, which is the
 * duplication `PeopleLayerTable` is generated to avoid.
 *
 * So the colours are **recovered from the files instead**. For each region there are pixels where
 * that region alone carries weight and carries all of it; the difference between the drawing and
 * the fixed layer at such a pixel *is* that region's colour. Recovering it from one pixel and then
 * requiring it to close the sum over every pixel of the sprite is a strictly stronger statement
 * than being handed the right answer: a mask that claimed a pixel it does not own, or a fixed layer
 * that kept a share it should have given up, fails it.
 *
 * The 28 shapes that still ship an un-suffixed base are the ones that can be checked this way, and
 * they are the walkers and the window busts. The other 20 have no base to compare against -- the
 * seated busts' were retired in v4.19 and v4.20, the carrying pose never shipped one -- so for them
 * the structural invariants below are what holds, and the generator's own end-to-end check
 * (`tools/generate_people_layers.py`, which refuses to write a set that recomposes worse than two
 * levels) is what covers the rest.
 */
class PeopleLayerAssetTest {

    /** The shapes that still ship the drawing itself, so the sum can be checked against it. */
    private fun shapesWithBase(): List<String> =
        drawableDir.listFiles { f -> f.name.endsWith("_fx.png") }
            .orEmpty()
            .map { it.name.removeSuffix("_fx.png") }
            .filter { File(drawableDir, "$it.png").isFile }
            .sorted()

    private fun masksOf(shape: String): List<Pair<String, BufferedImage>> =
        listOf("ms", "mh", "mt", "mb").mapNotNull { suffix ->
            val file = File(drawableDir, "${shape}_$suffix.png")
            if (file.isFile) suffix to ImageIO.read(file) else null
        }

    @Test
    fun `there is a shape to check`() {
        assertEquals(
            "the walkers, the summer window busts and the two girl seated busts are the shapes " +
                "that still keep a base drawing",
            30,
            shapesWithBase().size,
        )
    }

    @Test
    fun `the layers add up to the drawing at the colours it was painted in`() {
        val worst = mutableListOf<String>()
        for (shape in shapesWithBase()) {
            val drawing = ImageIO.read(File(drawableDir, "$shape.png"))
            val fixed = ImageIO.read(File(drawableDir, "${shape}_fx.png"))
            val masks = masksOf(shape)
            assertTrue("$shape has no masks at all", masks.isNotEmpty())

            // Each region's colour, read off a pixel it owns outright.
            val colours = masks.map { (suffix, mask) ->
                suffix to recoverColour(shape, suffix, drawing, fixed, masks)
            }.toMap()

            // Summed 255 times larger than a channel and divided once at the end. Dividing each
            // region's contribution as it is added throws away up to a level per region, which on
            // four regions is four levels of error the files do not have -- the arithmetic of the
            // check, not of the artwork.
            var maxError = 0
            for (y in 0 until drawing.height) {
                for (x in 0 until drawing.width) {
                    val want = premultiplied(drawing.getRGB(x, y))
                    val fix = premultiplied(fixed.getRGB(x, y))
                    val got = IntArray(3) { fix[it] * 255 }
                    for ((suffix, mask) in masks) {
                        val weight = mask.getRGB(x, y) ushr 24
                        if (weight == 0) continue
                        val colour = colours.getValue(suffix)
                        for (c in 0 until 3) got[c] += weight * colour[c]
                    }
                    for (c in 0 until 3) {
                        val error = kotlin.math.abs(want[c] * 255 - got[c])
                        if (error > maxError) maxError = error
                    }
                }
            }
            maxError = (maxError + 254) / 255
            // Two levels of one channel. The rasteriser itself composites in 8-bit premultiplied
            // and hands back a fully covered patch of the man's own skin as 219 where his paint is
            // 220, so the drawing is not exact to itself either; the masks quantise once more on
            // top of that. A mis-tagged piece does not miss by two levels, it misses by tens.
            if (maxError > 2) worst += "$shape: $maxError"
        }
        assertTrue("these shapes do not recompose to their own drawing: $worst", worst.isEmpty())
    }

    /**
     * A region's colour, from a pixel where that region carries the whole of the coverage.
     *
     * Such a pixel is the inside of the region -- a cheek, the middle of a sleeve -- where the mask
     * is at full weight and the fixed layer holds only the dark half of the shading. Any pixel where
     * a second region also has weight would give a colour mixed with that one, so those are skipped.
     */
    private fun recoverColour(
        shape: String,
        suffix: String,
        drawing: BufferedImage,
        fixed: BufferedImage,
        masks: List<Pair<String, BufferedImage>>,
    ): IntArray {
        val mine = masks.first { it.first == suffix }.second
        val samples = List(3) { mutableListOf<Int>() }
        for (y in 0 until drawing.height) {
            for (x in 0 until drawing.width) {
                // Full weight, sole owner, fully covered: anything less mixes in a neighbour or a
                // rounding of the coverage and would recover a colour that is nearly right, which
                // is worse than no sample at all because it is then required to close every pixel.
                if (mine.getRGB(x, y) ushr 24 != 255) continue
                if (masks.any { it.first != suffix && (it.second.getRGB(x, y) ushr 24) > 0 }) continue
                if (drawing.getRGB(x, y) ushr 24 != 255) continue
                val want = premultiplied(drawing.getRGB(x, y))
                val rest = premultiplied(fixed.getRGB(x, y))
                for (c in 0 until 3) samples[c] += (want[c] - rest[c]).coerceIn(0, 255)
            }
        }
        assertTrue(
            "$shape has no pixel where '$suffix' owns the coverage outright, so its colour " +
                "cannot be recovered and the region may be one nothing can see",
            samples[0].isNotEmpty(),
        )
        // The median rather than one pixel or the mean: the rasteriser rounds a fully covered pixel
        // of flat paint to one level either side often enough that a single sample is off by one,
        // and one level of colour is one level on every pixel that colour touches.
        return IntArray(3) { c -> samples[c].sorted()[samples[c].size / 2] }
    }

    /**
     * A mask may never claim more of a pixel than the figure has.
     *
     * `fixed + sum(mask)` is a partition of the figure's own coverage: each mask carries the share
     * of the pixel that follows its colour, and the fixed layer keeps the rest. If the masks
     * together ever asked for more than the drawing covers, the sum would brighten past the drawing
     * wherever they overlap -- which is exactly what a piece tagged into two regions would do.
     */
    @Test
    fun `the masks never claim more of a pixel than the figure covers`() {
        val offenders = mutableListOf<String>()
        for (shape in shapesWithBase()) {
            val fixed = ImageIO.read(File(drawableDir, "${shape}_fx.png"))
            val masks = masksOf(shape)
            for (y in 0 until fixed.height) {
                for (x in 0 until fixed.width) {
                    val cover = fixed.getRGB(x, y) ushr 24
                    val claimed = masks.sumOf { it.second.getRGB(x, y) ushr 24 }
                    if (claimed > cover) {
                        offenders += "$shape at ($x,$y): $claimed claimed of $cover covered"
                    }
                }
            }
        }
        assertTrue("masks claiming coverage the figure does not have: ${offenders.take(8)}", offenders.isEmpty())
    }

    /**
     * A mask carries its weight in the alpha channel and nothing in the colour channels.
     *
     * `BitmapFactory` premultiplies on load, so what the texture holds is `rgb * alpha / 255`; with
     * `rgb` white that is the weight through **one** rounding instead of two. The invariant is
     * worth asserting rather than assuming, because a mask that carried a grey would be quietly
     * darker than the colour it is asked to add.
     */
    @Test
    fun `a mask is white with its weight in the alpha`() {
        val offenders = mutableListOf<String>()
        for (file in drawableDir.listFiles { f -> f.name.matches(Regex("""person_.*_m[shtb]\.png""")) }.orEmpty()) {
            val image = ImageIO.read(file)
            outer@ for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    val argb = image.getRGB(x, y)
                    if (argb and 0xFFFFFF != 0xFFFFFF) {
                        offenders += "${file.name} at ($x,$y)"
                        break@outer
                    }
                }
            }
        }
        assertTrue("these masks carry colour instead of pure weight: $offenders", offenders.isEmpty())
    }

    /** Every layer the engine's table names must exist and share its shape's canvas. */
    @Test
    fun `every layer shares the canvas of the shape it belongs to`() {
        val offenders = mutableListOf<String>()
        for (file in drawableDir.listFiles { f -> f.name.startsWith("person_") }.orEmpty()) {
            val name = file.name.removeSuffix(".png")
            val suffix = listOf("_fx", "_ms", "_mh", "_mt", "_mb").firstOrNull { name.endsWith(it) }
                ?: continue
            val shape = name.removeSuffix(suffix)
            val base = File(drawableDir, "$shape.png").takeIf { it.isFile }
                ?: File(drawableDir, "${shape}_fx.png")
            val a = ImageIO.read(file)
            val b = ImageIO.read(base)
            if (a.width != b.width || a.height != b.height) {
                offenders += "${file.name} is ${a.width}x${a.height} against ${b.width}x${b.height}"
            }
        }
        assertTrue(
            "a layer on a different canvas from its shape would slide against the others: $offenders",
            offenders.isEmpty(),
        )
    }

    /** `[r, g, b]` of one ARGB pixel, multiplied by its own coverage. */
    private fun premultiplied(argb: Int): IntArray {
        val alpha = argb ushr 24
        return IntArray(3) { c -> ((argb ushr (16 - c * 8)) and 0xFF) * alpha / 255 }
    }

    private companion object {
        val drawableDir: File by lazy {
            var dir: File? = File(".").absoluteFile
            while (dir != null) {
                for (prefix in listOf("", "app/")) {
                    val candidate = File(dir, "${prefix}src/main/res/drawable-nodpi")
                    if (candidate.isDirectory) return@lazy candidate
                }
                dir = dir.parentFile
            }
            throw AssertionError("could not locate res/drawable-nodpi from ${File(".").absolutePath}")
        }
    }
}
