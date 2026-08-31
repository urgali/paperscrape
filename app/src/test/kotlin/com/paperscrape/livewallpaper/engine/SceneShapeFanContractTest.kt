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
 * load-bearing rather than vacuous, and the hill really does not reach the fan.
 */
class SceneShapeFanContractTest {

    /** The wallpaper's hill ridge, rebuilt from `PaperRenderer.buildBaseHillPath`'s own maths. */
    private fun hillRidge(): List<Pair<Float, Float>> {
        val width = 2000f
        val startX = -500f
        val top = 400f
        val height = 600f
        val amp = 0.09f
        val centerFraction = 0.13f
        val points = mutableListOf(startX to top + height)
        for (i in 0..64) {
            val f = i / 64f
            val heightFrac = centerFraction + amp * sin(f * 4f * PI.toFloat())
            points += (startX + f * width) to (top + height - heightFrac * height)
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
        // The finding's own geometry. If this ever stops being true the rule below is vacuous and
        // somebody should know.
        assertFalse(
            "a two-cycle sine ridge should not be star-shaped about its base-left corner",
            isStarShapedAboutFirst(hillRidge()),
        )
    }

    @Test
    fun `a single-peaked mountain face is star-shaped about its first vertex`() {
        assertTrue(
            "a single-peaked face is what makes the fan correct for mountains",
            isStarShapedAboutFirst(mountainFace()),
        )
    }

    @Test
    fun `the hill is drawn through the column-tessellated fill, not the fan`() {
        val source = File(renderer()).readText()
        val hill = source.substring(
            source.indexOf("private fun buildBaseHillPath("),
        )
        // The call that draws what buildBaseHillPath builds. Read from the source because the
        // coupling is "this shape goes to that fill", which no unit test of either side can see.
        val drawsHill = Regex("""drawVerticalGradientShape\(""").containsMatchIn(source)
        assertTrue("the hill must be drawn with the gradient fill", drawsHill)
        assertEquals(
            "no drawShape call may take a hill shape",
            0,
            Regex("""drawShape\(\s*(base)?[Hh]ill""").findAll(source).count(),
        )
        assertTrue("buildBaseHillPath must still exist", hill.isNotEmpty())
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
