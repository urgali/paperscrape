package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **The flowers and the piles cover the whole tile of the street, a screen's worth on each screen**
 * (`DESIGN_NOTES.md` §12, decision 88).
 *
 * The street is two screens wide and repeats: the flowers, the drifts of snow and the heaps of leaves laid over its first
 * screen alone would leave the second bare. Laid over the tile at [SceneObjectRenderer.FLOWER_CLUMP_COUNT] clumps and
 * [SceneObjectRenderer.pileCount] piles a screen, the tile carries twice as many and every screen as many; thing `i`
 * stands in the `i`-th slice of a screen's width, so the first screen's things stand where one screen's worth stands.
 * `GroundScatterAcrossTheTileTest` counts them on the frames the phone draws.
 */
class GroundScatterTileTest {

    private val screenWidth = 720f

    @Test
    fun `the tile carries a screen's worth on each of its screens`() {
        for (perScreen in listOf(SceneObjectRenderer.FLOWER_CLUMP_COUNT, SceneObjectRenderer.pileCount(1f), SceneObjectRenderer.pileCount(0.5f), 1)) {
            val count = SceneObjectRenderer.scatterCountOnTile(perScreen, 2f * screenWidth, screenWidth)
            assertEquals("$perScreen a screen", 2 * perScreen, count)
            val slice = SceneObjectRenderer.scatterSlice(screenWidth, perScreen)
            // Each thing stands somewhere in its slice: its middle says which screen holds it.
            val onTheFirst = (0 until count).count { (it + 0.5f) * slice < screenWidth }
            assertEquals("$perScreen a screen, on the first", perScreen, onTheFirst)
            assertEquals("$perScreen a screen, on the second", perScreen, count - onTheFirst)
        }
    }

    /** A surface not sized yet has no tile, and a screen's worth. */
    @Test
    fun `with no tile a scatter is a screen's worth`() {
        assertEquals(SceneObjectRenderer.FLOWER_CLUMP_COUNT, SceneObjectRenderer.scatterCountOnTile(SceneObjectRenderer.FLOWER_CLUMP_COUNT, 0f, screenWidth))
        assertEquals(SceneObjectRenderer.FLOWER_CLUMP_COUNT, SceneObjectRenderer.scatterCountOnTile(SceneObjectRenderer.FLOWER_CLUMP_COUNT, 0f, 0f))
    }
}
