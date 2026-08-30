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
     *
     * **Raised from 16 MB to 26 MB by v4.1's skin-tone batch, and this is the decision the old
     * comment demanded be made here rather than waved through.** Three real skin tones for four
     * characters across two seasons and four sprite slots is 96 variant PNGs, and a variant is
     * the same canvas as its source: the whole set goes from 14.79 MB to 25.67 MB. No choice of
     * tone count fits under the old ceiling -- even two tones would clear it -- so the growth is
     * inherent to shipping real variant artwork rather than recolouring at runtime. The ceiling
     * is set just above the measured figure, as before, so the next asset pass has to come here
     * and say so too.
     *
     * A fourth tone was generated, measured at 29.29 MB, and then dropped -- on how it looked
     * rather than on what it cost. See `PedestrianPopulation.SKIN_TONE_COUNT`.
     *
     * **What this costs.** A live wallpaper runs in a process the system kills freely under
     * pressure, and this doubles the worst case that sizing assumes. It is a worst case rather
     * than a resident figure -- sprites decode on demand, and no frame draws four tones of the
     * same character -- but the budget deliberately measures the ceiling, and the ceiling moved.
     *
     * **The alternative, for whoever revisits this.** Recolouring one flat colour into a cached
     * bitmap at load time would give the same tones with zero growth here, at the cost of a
     * one-off per-pixel pass per tone actually used. It was not taken because the batch that
     * requested this asked for real variant PNGs; it remains the cheaper answer if memory
     * pressure is ever measured to be a problem on real devices.
     *
     * **SCL-01 grew the set inside the ceiling rather than moving it, and says so here because
     * the paragraph above asks the next asset pass to.** Widening the three co-registered person
     * families by one to three units of canvas each -- 132 sprites, to recover winter headwear the
     * old viewBox cut flat -- took the set from 26.76 MB to 27.09 MB decoded. That is 25.84 MiB
     * against this 26 MiB ceiling: it fits, with 169 kB to spare. The ceiling is deliberately not
     * raised. Whoever needs the next canvas has almost none of it left and should expect to make
     * the memory-pressure argument, not to nudge the number.
     */
    private val decodedByteBudget = 26L * 1024L * 1024L

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

    /**
     * Each co-registered sprite family shares one canvas, and the single anchor that serves the
     * family is that canvas's own height.
     *
     * `tools/assets/paperscrape_assets/normalize.py` defines these three families and refuses a
     * set whose "members must share a canvas", because one origin serves them all. The Kotlin side
     * of that rule is here: `drawPerson`, `drawWindowOccupant` and `drawCarDriver` each blit
     * whichever member the lookup picked through one `*_ANCHOR_Y_UNITS`, and that constant is the
     * canvas height in local units -- it is what puts the sprite's bottom edge on the caller's
     * y=0.
     *
     * Two failures follow from breaking it, and neither is visible in a build:
     *
     * - **A family whose canvases disagree.** The shared anchor is then right for some members and
     *   wrong for the rest, which is exactly what a per-sprite constant would be invented to paper
     *   over -- and `CLAUDE.md` says not to build one.
     * - **A canvas that grew without its anchor.** Every member of that family is drawn one unit
     *   into the ground, or one unit above it. This is the case that went unnoticed: the SCL-01
     *   pass widened all three canvases, and nothing in the suite would have caught leaving an
     *   anchor behind.
     *
     * Read from the shipped PNGs rather than declared here, so the assertion follows the artwork
     * instead of restating it.
     */
    @Test
    fun `each co-registered family shares one canvas, and its anchor is that canvas`() {
        data class Family(val key: String, val anchorUnits: Float, val member: (String) -> Boolean)

        val families = listOf(
            Family("person_walk", -SceneObjectRenderer.PERSON_ANCHOR_Y_UNITS) {
                it.startsWith("person_") && it.contains("_walk")
            },
            Family("person_head_window", SceneObjectRenderer.WINDOW_HEAD_ANCHOR_Y_UNITS) {
                it.contains("_head_window")
            },
            Family("person_head_car", SceneObjectRenderer.CAR_HEAD_ANCHOR_Y_UNITS) {
                it.endsWith("_head_car")
            },
        )
        val grid = SpriteBlitter.SPRITE_PIXELS_PER_UNIT

        for (family in families) {
            val members = spriteNames().filter(family.member)
            assertTrue("${family.key}: no members found", members.isNotEmpty())

            val canvases = members.map { pngSize(it) }.toSet()
            assertEquals(
                "${family.key}: one origin serves every member, so they must share a canvas -- " +
                    "found ${canvases.sortedBy { it.second }}",
                1, canvases.size,
            )

            val (_, height) = canvases.first()
            assertEquals(
                "${family.key}: the shared anchor is ${family.anchorUnits} units, which is " +
                    "${family.anchorUnits * grid} px, but the family's canvas is $height px tall. " +
                    "A canvas that moves without its anchor moves every member of the family.",
                height.toFloat(), family.anchorUnits * grid, 0.001f,
            )
        }
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
