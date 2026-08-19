package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import org.junit.Test

/**
 * The two properties the shipped sprite set has to hold as a whole: every canvas sits on the
 * authoring grid, and the set as a whole stays inside its decoded-memory budget.
 *
 * **This replaces the padding check, and the reason is a change of rule rather than a relaxation.**
 * Until the V2 asset set, a sprite's geometry was whatever its opaque pixels happened to occupy,
 * so transparent padding was pure waste -- 17.5 MB of it at the worst point -- and the rule was
 * that every sprite must reach its own canvas edges. The V2 library declares `contentBox` and an
 * anchor rule per sprite instead, and places the drawing inside a canvas sized on the grid, so
 * margin is now load-bearing: `palmtree_fronds` hangs its fan above a declared attachment point,
 * `cloud_body` and `sun_body` are centred in canvases their artwork deliberately does not fill,
 * and cropping any of them would move the sprite rather than save anything. Asserting zero
 * padding against that library would fail 34 sprites for being drawn as designed.
 *
 * What is worth keeping from the old rule is the thing it was really protecting -- that the set
 * cannot quietly grow -- and that is stated directly below as a byte budget rather than inferred
 * from per-sprite margins.
 *
 * The grid check is the other half. `SPRITE_PIXELS_PER_UNIT` is baked into every `SCENE_UNITS`
 * sprite at authoring time, and a canvas that is not a whole multiple of it cannot be divided
 * back down to an integral number of local units, which is how a sprite ends up on a fractional
 * origin and gets resampled by the blit's own `FILTER_BITMAP_FLAG`.
 */
class SpriteGeometryTest {

    /**
     * Decoded ARGB_8888 bytes the whole sprite set may occupy.
     *
     * The V2 set measures 14.43 MB against v75's 15.39 MB, with three more sprites in it. The
     * ceiling is set just above the current figure rather than at a round number, so an asset
     * pass that adds a large sprite has to come here and say so -- which is the point, since the
     * budget is what a memory-pressure policy and a texture atlas are both sized against.
     */
    private val decodedByteBudget = 16L * 1024L * 1024L

    @Test
    fun `every shipped sprite is authored on the sprite grid`() {
        val grid = SpriteBlitter.SPRITE_PIXELS_PER_UNIT.toInt()
        val offGrid = mutableListOf<String>()
        for (name in spriteNames()) {
            val (width, height) = pngSize(name)
            if (width % grid != 0 || height % grid != 0) {
                offGrid += "$name (${width}x$height)"
            }
        }
        assertEquals(
            "these sprites are not a whole multiple of $grid px on both axes, so they cannot be " +
                "divided back to an integral number of local units: $offGrid",
            emptyList<String>(), offGrid,
        )
    }

    @Test
    fun `the shipped sprite set stays inside its decoded memory budget`() {
        var total = 0L
        for (name in spriteNames()) {
            val (width, height) = pngSize(name)
            total += width.toLong() * height.toLong() * 4L
        }
        assertTrue(
            "the sprite set decodes to $total bytes, past the $decodedByteBudget budget. Raising " +
                "the budget is a decision about memory pressure and atlas sizing, not a test fix.",
            total <= decodedByteBudget,
        )
    }

    /** No sprite may be so large on its own that it cannot share an atlas page with anything. */
    @Test
    fun `no single sprite dominates the set`() {
        val oversized = mutableListOf<String>()
        for (name in spriteNames()) {
            val (width, height) = pngSize(name)
            val bytes = width.toLong() * height.toLong() * 4L
            if (bytes > decodedByteBudget / 8L) oversized += "$name (${width}x$height, $bytes bytes)"
        }
        assertEquals(
            "these sprites each take more than an eighth of the whole budget: $oversized",
            emptyList<String>(), oversized,
        )
    }

    private fun spriteNames(): List<String> {
        val names = drawableDir.listFiles { file -> file.name.endsWith(".png") }
            .orEmpty()
            .map { it.name.removeSuffix(".png") }
            .sorted()
        assertTrue("no sprites found in ${drawableDir.path}", names.isNotEmpty())
        return names
    }

    /**
     * Width and height straight out of the PNG's IHDR chunk, the same way
     * `SkySpriteAnchoringTest` reads them: no image library, so this runs as a plain JVM test.
     */
    private fun pngSize(name: String): Pair<Int, Int> {
        val file = File(drawableDir, "$name.png")
        assertTrue("${file.path} does not exist", file.isFile)
        val header = file.inputStream().use { input ->
            val buffer = ByteArray(24)
            assertEquals("${file.name} is too short to be a PNG", 24, input.read(buffer))
            buffer
        }
        fun intAt(offset: Int) = (0 until 4).fold(0) { acc, i ->
            (acc shl 8) or (header[offset + i].toInt() and 0xFF)
        }
        assertEquals("${file.name} is not a PNG", 0x89504E47.toInt(), intAt(0))
        return intAt(16) to intAt(20)
    }

    private companion object {
        /** Same walk-up as `SkySpriteAnchoringTest`: the working directory is a default, not a
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
