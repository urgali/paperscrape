package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **A tree's falling leaves are its copy's for as long as that copy is on screen.** A leaf is dealt from its tree's
 * identity ([SceneObjectRenderer.leafCopyId]): the tree's index and the copy of the tile it stands in. That copy is the
 * physical one [SceneObjectRenderer.copyOf] numbers -- what a place deals its building by -- so the scroll can carry a
 * tree across the screen, and the wrap move on under it, and no leaf of its is dealt again. Numbered by the loop's tile
 * index less the scroll's whole tiles, the copy took another number while on screen, once a tile of scroll for each
 * tree: for every tree in the left half of the tile (`tileFractionX` under 0.5) at the moment the shift wraps, all
 * together, and their leaves were dealt again mid-fall, in new places, phases and colours.
 *
 * The scroll is swept as `StorefrontStreetTest` sweeps it, on the JVM: the identity of every copy the draw loop would
 * draw against the copy's physical number, worked out from the unwrapped scroll. The loop itself draws on a canvas the
 * JVM does not have, so which number it hands the leaves is read in the renderer's source, whole lines, comments off.
 */
class FallingLeafCopyTest {

    /** Trees across the tile, either side of its middle and at its two ends. */
    private val fractions = listOf(0.03f, 0.25f, 0.49f, 0.5f, 0.75f, 0.97f)

    @Test
    fun `a tree's leaves keep their identity while its copy is on screen, across the wrap`() {
        for ((screenW, screenH) in listOf(720f to 1440f, 1080f to 2340f)) {
            val tileW = screenW * 2f
            val geom = GroundGeometry(0f, tileW, 0)
            val held = HashMap<Pair<Int, Int>, Long>()
            var renumbered = 0
            // Four tiles of drift, a fortieth of a screen a step, the way the scene moves (leftward).
            for (step in 0 until 4 * 80) {
                val unwrapped = -step * (screenW / 40.0)
                var wrapped = unwrapped % tileW
                if (wrapped > 0.0) wrapped -= tileW
                geom.shiftXWrapped = wrapped.toFloat()
                geom.scrollTileBias = Math.round((unwrapped - wrapped) / tileW).toInt()
                for ((tree, fraction) in fractions.withIndex()) {
                    val spec = StaticSceneObject(SceneObjectType.TREE, depthFraction = 0.6f, tileFractionX = fraction)
                    val cull = SceneObjectRenderer.MAX_OBJECT_HALF_WIDTH_UNITS * SceneObjectRenderer.effectiveScaleFor(spec, screenH)
                    val x = SceneObjectRenderer.anchorXOf(fraction, geom)
                    val first = SceneObjectRenderer.firstVisibleTileOffset(x, cull, tileW)
                    val limit = SceneObjectRenderer.tileOffsetLimit(x, cull, tileW, screenW)
                    for (tileIndex in first until limit) {
                        val copyX = x + tileIndex * tileW
                        if (!SceneObjectRenderer.isHorizontallyVisible(copyX, cull, screenW)) continue
                        val copy = SceneObjectRenderer.copyOf(fraction, geom, tileIndex)
                        val id = SceneObjectRenderer.leafCopyId(tree, copy)
                        // The physical copy, from the unwrapped scroll: copy c of the tree stands at unwrapped + (fraction + c) * tile.
                        val physical = Math.round((copyX - unwrapped) / tileW - fraction).toInt()
                        assertEquals("tree at $fraction, copy at x=$copyX, step $step", SceneObjectRenderer.leafCopyId(tree, physical), id)
                        val before = held.put(tree to physical, id)
                        if (before != null) assertEquals("tree at $fraction: copy $physical changed identity at step $step", before, id)
                        if (copy != tileIndex - geom.scrollTileBias) renumbered++
                    }
                }
            }
            assertTrue("the premise: the sweep reaches copies the loop's index less the whole tiles numbers otherwise", renumbered > 0)
        }
    }

    @Test
    fun `the draw loop deals each tree's leaves from the copy it draws`() {
        val lines = codeLines(File(mainSources(), "engine/SceneObjectRenderer.kt").readText())
        assertEquals(
            "the leaves' identity in the draw loop",
            listOf(
                "recordLeafSource(r, leafCopyId(runtimeIndex, copyOf(r.spec.tileFractionX, geom, 0)), x, groundY, effectiveScale)",
                "recordLeafSource(r, leafCopyId(runtimeIndex, copyOf(r.spec.tileFractionX, geom, tileIndex)), x = copyX, groundY = groundY, scale = effectiveScale)",
            ),
            lines.filter { it.startsWith("recordLeafSource(r, ") },
        )
    }

    /** [text]'s lines, trimmed, without comments: a line comment cut off, a KDoc or block comment line dropped. */
    private fun codeLines(text: String): List<String> =
        text.lines().map { it.trim() }
            .filterNot { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }
            .map { it.substringBefore(" //").trim() }
            .filter { it.isNotEmpty() }

    /** The app's Kotlin sources, `src/main/kotlin/com/paperscrape/livewallpaper`. */
    private fun mainSources(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = File(dir, "${prefix}src/main/kotlin/com/paperscrape/livewallpaper")
                if (File(f, "engine").isDirectory) return f
            }
            dir = dir.parentFile
        }
        error("src/main/kotlin")
    }
}
