package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import org.junit.Test

/**
 * The two properties the shipped sprite set has to hold as a whole: every canvas sits on the
 * authoring grid, and the set as a whole stays inside its decoded-memory budget.
 *
 * **There is no padding check here, because margin can be load-bearing.** Each sprite declares a
 * `contentBox` and an anchor rule, and its drawing is placed inside a canvas sized on the grid:
 * `palmtree_fronds` hangs its fan above a declared attachment point, `sun_body` and the moons are
 * centred in canvases their artwork deliberately does not fill, and cropping any of them would
 * move the sprite rather than save anything (`SpriteCanvasConventionTest` lists which margins are
 * declared). Padding that is not load-bearing is `paperscrape-assets normalize`'s to remove, and
 * `tools/assets/tests/test_normalize.py` fails a shipped canvas that still carries it. What a
 * padding rule would protect -- that the set cannot quietly grow -- is stated directly below as a
 * byte budget.
 *
 * The grid check is the other half. `SPRITE_PIXELS_PER_UNIT` is baked into every `SCENE_UNITS`
 * sprite at authoring time, and a canvas that is not a whole multiple of it cannot be divided
 * back down to an integral number of local units, which is how a sprite ends up on a fractional
 * origin and gets resampled by the blit's own `FILTER_BITMAP_FLAG`.
 */
class SpriteGeometryTest {

    /**
     * Decoded ARGB_8888 bytes the whole sprite set may occupy: width x height x 4 over every
     * shipped PNG, 47 MiB (`DESIGN_NOTES.md` §12, decision 64).
     *
     * **What it bounds.** The `Canvas` path holds the authored bitmap of every sprite it has drawn:
     * `SpriteCache` decodes on demand at the size the artwork was drawn at, has no standing size
     * cap and evicts only on a trim callback. That path is the settings and gallery previews on
     * **every** device -- `ThemePreview.kt` builds a `CanvasSceneTarget` unconditionally -- and the
     * whole wallpaper once EGL has failed `GlLifecyclePolicy.MAX_CONTEXT_REBUILDS` times. Beside
     * that this bounds the APK and the per-sprite transient decode peak, and nothing else measures
     * those three. It is a ceiling no single frame reaches: sprites decode on demand.
     *
     * **What it does not bound: GL texture memory.** `GlTextureCache` uploads
     * `reduce(bitmap, SpriteDetailLevel.levelFor(scale))`, sized against the scale the sprite is
     * drawn at, and crops the transparent border after the reduction; `SpriteBlitter.onSpriteUploaded`
     * then releases the decoded bitmap, so on the GPU path authored size is a transient. That limit
     * is `SpriteDrawScaleTest.uploadedTexelBudget`, whose comment carries the table of which limit
     * protects what: move neither without reading it. The two can move in opposite directions. A
     * region mask (decision 27) is a full canvas here and a few tenths of one in texels, because the
     * GPU is charged for the ink and this limit for the file. Cropping the PNG instead is not
     * available: `SpriteDetailLevel.reduced` truncates, so a crop of a 117-wide canvas reduces on a
     * different grid from the canvas and the layers drift apart across the sprite.
     *
     * No line of shipped code reads either limit; they are build gates. What the GPU path really has
     * is one 2048x2048 atlas page, 16 MiB of RGBA (`GlAtlasOccupancyTest`: 583 rows of 2048 occupied
     * by a walk of all twelve themes in one process, measured on the BV6600, 2026-09-27).
     *
     * The sprites declared `usage: "orphan"` in the registry cost this budget and the `Canvas`
     * path's decode peak, and nothing in the APK: resource shrinking leaves them out of the release
     * build.
     *
     * **A grid of 2 is not a way under it.** Redrawing the set on a grid of 2 would *magnify* every
     * sprite whose headroom is below 1.5. `SpriteDrawScaleTest` prints the headroom of every sprite;
     * read it before reopening that road.
     *
     * **The ceiling sits just above the measured set**, not at a round figure, so a change that adds
     * artwork meets it and has to come here and argue: what it adds, what was looked for first, and
     * what it costs on the three things this limit bounds. Moving it is the maintainer's decision,
     * not a test fix.
     */
    private val decodedByteBudget = 47L * 1024L * 1024L

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
        // **Printed on every run, passing or failing.** A gate that speaks only when it is
        // breached cannot show growth, and a figure copied elsewhere goes stale unnoticed: this
        // line is the standing figure and its margin, on every build.
        println(
            "decodedByteBudget: %d B of %d (%.3f MiB of %d MiB), %d B of margin, %d PNGs".format(
                total, decodedByteBudget, total / 1048576.0,
                decodedByteBudget / 1048576L, decodedByteBudget - total, spriteNames().size,
            ),
        )
        // **And the neighbourhood's share of it**, read off the table the engine deals buildings
        // from rather than off a list of name prefixes. The building generator keeps no ceiling of
        // its own for these PNGs: a second number would be this gate's margin frozen, read by
        // nothing. This line is the figure, on every build.
        val neighbourhood = NeighbourhoodTable.FAMILIES.values
            .flatMap { family -> family.slots.flatMap { slot -> (slot.options + slot.heights).flatMap { piece -> piece.parts } } }
            .map { it.res }.filter { it != 0 }.toSet()
            .map { drawableName(it) }
        val neighbourhoodBytes = neighbourhood.sumOf { name -> pngSize(name).let { (w, h) -> w.toLong() * h * 4L } }
        println(
            "of which the neighbourhood (six building families): %d B in %d PNGs, %.1f %% of the set".format(
                neighbourhoodBytes, neighbourhood.size, 100.0 * neighbourhoodBytes / total,
            ),
        )
        assertTrue(
            "the sprite set decodes to $total bytes, past the $decodedByteBudget budget. Raising " +
                "the budget is a decision about what the `Canvas` path holds resident and about " +
                "the APK, not a test fix -- and it is NOT a decision about GL texture memory or " +
                "atlas sizing, which is `SpriteDrawScaleTest.uploadedTexelBudget`.",
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
     * of that rule is here: `drawPersonLayers` blits whichever member the lookup picked -- for the
     * walker, for `drawWindowOccupant` and for `drawSeatedOccupant` -- through one `*_ANCHOR_Y_UNITS`, and that constant is the
     * canvas height in local units -- it is what puts the sprite's bottom edge on the caller's
     * y=0.
     *
     * Two failures follow from breaking it, and neither is visible in a build:
     *
     * - **A family whose canvases disagree.** The shared anchor is then right for some members and
     *   wrong for the rest, which is exactly what a per-sprite constant would be invented to paper
     *   over -- and a per-asset constant over a shared fault is never the fix.
     * - **A canvas that grew without its anchor.** Every member of that family is drawn one unit
     *   into the ground, or one unit above it, and nothing else in the suite fails: a pass that
     *   widens a family's canvases and leaves its anchor behind is caught only here.
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
            Family("person_head_car", SceneObjectRenderer.HEAD_CAR_ANCHOR_Y_UNITS) {
                it.contains("_head_car")
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

    /** The drawable's file name, from its generated `R` id. */
    private fun drawableName(res: Int): String =
        com.paperscrape.livewallpaper.R.drawable::class.java.fields
            .first { it.type == Int::class.javaPrimitiveType && it.getInt(null) == res }.name

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
