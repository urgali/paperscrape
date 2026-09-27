package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A cloud is drawn once where it is, not twice** (v5.8C).
 *
 * `drawClouds` placed each cloud's copies by folding three tile offsets modulo *two* tile widths,
 * and whenever the cloud's base position was positive two of them landed on the same x: about a
 * third of the clouds on screen were blitted twice, with heavier feathered edges than the rest and
 * a fade that ran at `1-(1-a)^2` instead of `a` (v5.8B comment audit). Now the copies are the one
 * folded into the first tile and the one a tile to its left.
 */
class CloudCopiesTest {

    @Test
    fun `the two copies are a tile apart and cover every position a cloud can be seen at`() {
        val screen = 720f
        val tile = screen * 2f
        val margin = 160f * 1.25f * 1.3f   // the cull margin at the largest cloud scale, and then some
        val rnd = java.util.Random(11)
        repeat(20_000) {
            val base = (rnd.nextFloat() - 0.5f) * tile * 8f
            val folded = PaperRenderer.foldIntoTile(base, tile)
            assertTrue("folded into the first tile: $folded", folded >= 0f && folded <= tile)
            val copies = listOf(folded, folded - tile)
            // Every copy is the cloud: base plus a whole number of tiles.
            for (x in copies) {
                val k = (x - base) / tile
                assertEquals("copy $x of $base is not a whole tile away", Math.round(k).toFloat(), k, 1e-3f)
            }
            // At most one copy on screen, and where the cloud should be seen there is one.
            val visible = copies.count { it >= -margin && it <= screen + margin }
            assertTrue("two copies of one cloud on screen", visible <= 1)
            val p = ((base.toDouble() % tile) + tile) % tile
            val shouldShow = p <= screen + margin || p - tile >= -margin
            assertEquals("a cloud that should be seen is missing", shouldShow, visible == 1)
        }
    }
}
