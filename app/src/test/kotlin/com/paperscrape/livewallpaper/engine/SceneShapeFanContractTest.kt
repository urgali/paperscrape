package com.paperscrape.livewallpaper.engine

import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REN-02: the GPU fan is only used for shapes it is correct for.
 *
 * `GlSceneTarget.drawShape` fills a triangle fan from vertex 0, which is correct precisely when the
 * polygon is star-shaped about that vertex. `SceneShape`'s contract claimed that was "true for all
 * three by construction" and it is not -- the hill's ridge is two full sine cycles, so a fan from
 * its base-left corner runs above the crest where the wave dips.
 *
 * The claim was wrong about *which fill the hill uses*. It goes through
 * `drawVerticalGradientShape`, which tessellates columns to the base line and needs no such
 * property. This test holds both halves of that: the hill really is not star-shaped, so the rule is
 * load-bearing rather than vacuous, and the hill really does not reach the fan (since v5.8C not
 * even through its shadow, which was removed -- see the hill case below).
 */
class SceneShapeFanContractTest {

    /**
     * The wallpaper's hill ridge, rebuilt from `PaperRenderer.buildBaseHillPath`'s own maths: the
     * ridge **near the top** of the band (`top + height * frac`), where the renderer puts it -- the
     * old model put it near the base and so tested a different polygon (v5.8B audit) -- with the
     * theme's own phase, `(layerSeed(0) % 628) / 100`.
     */
    private fun hillRidge(themeId: String): List<Pair<Float, Float>> {
        val screenWidth = 1080f
        val width = screenWidth * 2f
        val startX = -screenWidth * 0.5f
        val top = 2424f * SceneSpace.HILL_LAYER_TOP_FRACTION
        val height = 2424f * SceneSpace.HILL_LAYER_HEIGHT_FRACTION
        val amp = 0.09f
        val centerFraction = 0.13f
        val phase = ((themeId.hashCode().toLong() * 31) % 628L) / 100f
        val points = mutableListOf(startX to top + height)
        for (i in 0..64) {
            val f = i / 64f
            val heightFrac = centerFraction + amp * sin(f * 4f * PI.toFloat() + phase)
            points += (startX + f * width) to (top + height * heightFrac)
        }
        points += (startX + width) to (top + height)
        return points
    }

    /** A single-peaked face, the shape that does reach the fan. */
    private fun mountainFace(): List<Pair<Float, Float>> {
        val points = mutableListOf(0f to 100f)
        for (i in 0..32) {
            val f = i / 32f
            points += (f * 200f) to (100f - 80f * sin(f * PI.toFloat()))
        }
        points += 200f to 100f
        return points
    }

    /** Whether every vertex is visible from vertex 0 without leaving the polygon. */
    private fun isStarShapedAboutFirst(points: List<Pair<Float, Float>>): Boolean {
        val (ax, ay) = points[0]
        // Sampling the segment from vertex 0 to each vertex and asking whether it ever rises above
        // the ridge directly beneath it. For a terrain polygon that is exactly the condition.
        for (i in 1 until points.size) {
            val (bx, by) = points[i]
            for (s in 1 until 40) {
                val t = s / 40f
                val x = ax + (bx - ax) * t
                val y = ay + (by - ay) * t
                val ridge = ridgeYAt(points, x) ?: continue
                if (y < ridge - 0.01f) return false
            }
        }
        return true
    }

    /** The ridge's y at [x], by linear interpolation between the sampled points. */
    private fun ridgeYAt(points: List<Pair<Float, Float>>, x: Float): Float? {
        for (i in 1 until points.size - 1) {
            val (x0, y0) = points[i]
            val (x1, y1) = points[i + 1]
            if (x in minOf(x0, x1)..maxOf(x0, x1) && x0 != x1) {
                return y0 + (y1 - y0) * ((x - x0) / (x1 - x0))
            }
        }
        return null
    }

    @Test
    fun `the hill ridge is not star-shaped about its first vertex`() {
        // Beach and Sunset are the two the phone's GPU was photographed spilling on (v5.8C).
        for (themeId in listOf("beach", "sunset")) {
            assertTrue(
                "$themeId: the hill ridge must not be star-shaped, or the fan rule would be vacuous for it",
                !isStarShapedAboutFirst(hillRidge(themeId)),
            )
        }
    }

    @Test
    fun `a single-peaked mountain face is star-shaped about its first vertex`() {
        assertTrue(
            "a single-peaked face is what makes the fan correct for mountains",
            isStarShapedAboutFirst(mountainFace()),
        )
    }

    /**
     * **What this checks, and what its old version could not** (v5.8B, v5.8C). It used to count
     * `drawShape(hill...)` and `drawShape(baseHill...)` and require zero -- but the renderer names
     * the hill shape `path` (`val path = baseHillShapes[layer]`), so the pattern matched nothing
     * whatever the code did, and the hill's shadow, which did reach the fan, passed. v5.8B read the
     * variable's real name and pinned the shadow as the one known exception (`ROADMAP.md` A16);
     * v5.8C removed the shadow, whose only visible pixels were the fan's spill, so now **no**
     * `drawShape` in the hill loop may take the hill's shape.
     */
    @Test
    fun `the hill is drawn only through the column-tessellated fill`() {
        val source = File(renderer()).readText()
        val start = source.indexOf("private fun drawHillLayers(")
        val body = source.substring(start, source.indexOf("\n    }\n", start))
        val hillVariable = Regex("""val (\w+) = baseHillShapes\[""").find(body)?.groupValues?.get(1)
        assertEquals("the hill shape's variable in drawHillLayers", "path", hillVariable)
        assertTrue(
            "the hill itself must be drawn with the gradient fill",
            Regex("""drawVerticalGradientShape\(\s*$hillVariable\b""").containsMatchIn(body),
        )
        val onTheFan = Regex("""drawShape\(\s*$hillVariable\s*,\s*(\w+)""").findAll(body).map { it.groupValues[1] }.toList()
        assertEquals("hill shapes filled as a fan", emptyList<String>(), onTheFan)
        assertTrue("buildBaseHillPath must still exist", source.contains("private fun buildBaseHillPath("))
    }

    @Test
    fun `the theme preview, which does build a multi-peaked ridge, cannot reach a GPU fan`() {
        val preview = File(previewSource()).readText()
        assertTrue(
            "ThemePreview must stay typed to CanvasSceneTarget",
            preview.contains("target: CanvasSceneTarget"),
        )
        assertEquals(
            "and must not mention the GL target at all",
            0,
            Regex("""GlSceneTarget""").findAll(preview).count(),
        )
    }

    private fun renderer(): String = walkUp("src/main/kotlin/com/paperscrape/livewallpaper/engine/PaperRenderer.kt")

    private fun previewSource(): String = walkUp("src/main/kotlin/com/paperscrape/livewallpaper/ui/ThemePreview.kt")

    private fun walkUp(suffix: String): String {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = File(dir, prefix + suffix)
                if (candidate.isFile) return candidate.path
            }
            dir = dir.parentFile
        }
        error("could not locate " + suffix)
    }
}
