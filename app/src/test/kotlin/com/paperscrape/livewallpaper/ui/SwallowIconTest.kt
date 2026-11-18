package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.ui.icons.SWALLOW_SPAN
import com.paperscrape.livewallpaper.ui.icons.SWALLOW_TURN_DEGREES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ***Birds*' icon is the scene's own swallow** (`DESIGN_NOTES.md` §9, "Icons", and §12, decision 107, the maintainer's
 * «Sì a tutte (consigliata)» of 2026-11-15): the points of `ui/icons/Swallow.kt` are those of
 * `tools/assets/sources/svg/bird_body.svg`, its four papers in their order, turned [SWALLOW_TURN_DEGREES] nose up, fitted
 * [SWALLOW_SPAN] wide from x = 1 and centred on y = 12 -- worked out again here, so a redraw of the bird that leaves the icon
 * behind fails. And it stands inside the 24 x 24 of the Material icons beside it.
 */
class SwallowIconTest {

    @Test
    fun `the icon is the scene's swallow, turned and fitted, to three decimals`() {
        val svg = repoFile("tools/assets/sources/svg/bird_body.svg").readText()
        val papers = Regex("""points="([^"]+)"""").findAll(svg).map { m ->
            m.groupValues[1].trim().split(Regex("\\s+")).map { p -> p.split(",").let { it[0].toDouble() to it[1].toDouble() } }
        }.toList()
        assertEquals("the swallow's four papers", 4, papers.size)
        val a = Math.toRadians(-SWALLOW_TURN_DEGREES.toDouble())
        val turned = papers.map { paper -> paper.map { (x, y) -> (x * Math.cos(a) - y * Math.sin(a)) to (x * Math.sin(a) + y * Math.cos(a)) } }
        val xs = turned.flatten().map { it.first }
        val ys = turned.flatten().map { it.second }
        val k = SWALLOW_SPAN / (xs.max() - xs.min())
        val cy = (ys.max() + ys.min()) / 2
        val expected = turned.map { paper -> paper.map { (x, y) -> (x - xs.min()) * k + 1 to (y - cy) * k + 12 } }

        val kotlin = repoFile("app/src/main/kotlin/com/paperscrape/livewallpaper/ui/icons/Swallow.kt").readText()
        val drawn = kotlin.split("materialPath {").drop(1).map { path ->
            Regex("""(moveTo|lineTo)\(([-0-9.]+)f, ([-0-9.]+)f\)""").findAll(path).map { it.groupValues[2].toDouble() to it.groupValues[3].toDouble() }.toList()
        }
        assertEquals("one path a paper", expected.size, drawn.size)
        for (i in expected.indices) {
            assertEquals("paper $i: its points", expected[i].size, drawn[i].size)
            for (j in expected[i].indices) {
                assertEquals("paper $i point $j x", expected[i][j].first, drawn[i][j].first, 0.0006)
                assertEquals("paper $i point $j y", expected[i][j].second, drawn[i][j].second, 0.0006)
            }
        }
        for ((x, y) in drawn.flatten()) assertTrue("($x, $y) inside the icon", x in 0.0..24.0 && y in 0.0..24.0)
        assertTrue("each path closed", drawn.size == Regex("close\\(\\)").findAll(kotlin).count())
    }

    /** *Birds*' row draws it. */
    @Test
    fun `the Birds row draws the swallow`() {
        val world = repoFile("app/src/main/kotlin/com/paperscrape/livewallpaper/ui/WorldSceneScreen.kt").readText()
        val birds = world.substring(world.indexOf("onOffSummary(false, \"Birds\")"), world.indexOf("onClick = { activeSection = \"birds\" },"))
        // A whole line, the comment taken off: a line commented out does not count.
        assertTrue(birds.lines().map { it.substringBefore("//").trim() }.contains("icon = Swallow,"))
    }

    private fun repoFile(path: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, path)
            if (f.isFile) return f
            dir = dir.parentFile
        }
        error("not found: $path")
    }
}
