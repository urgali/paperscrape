package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The texture atlas's placement arithmetic.
 *
 * Every failure mode here is silent. Two entries handed overlapping rectangles do not throw — one
 * sprite simply renders with another's pixels somewhere inside it, and only in whichever scene draws
 * that pair. A placement that forgets its padding produces a one-pixel fringe of the neighbouring
 * sprite along an edge, which is subtle enough to survive a casual look at the wallpaper. So the
 * central assertion is the overlap check: it re-derives every reserved rectangle and compares them
 * all against each other, rather than trusting the shelf bookkeeping to be self-consistent.
 */
class ShelfPackerTest {

    private val padding = 1

    /** Reserved rectangle (including padding) of the placement just made. */
    private fun reserved(packer: ShelfPacker, w: Int, h: Int) = intArrayOf(
        packer.contentX - padding,
        packer.contentY - padding,
        packer.contentX + w + padding,
        packer.contentY + h + padding,
    )

    private fun overlaps(a: IntArray, b: IntArray): Boolean =
        a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3]

    @Test
    fun `the first placement sits at the padding offset`() {
        val packer = ShelfPacker(64, 64, padding)
        assertTrue(packer.place(10, 10))
        assertEquals(padding, packer.contentX)
        assertEquals(padding, packer.contentY)
    }

    @Test
    fun `entries advance along a shelf leaving padding between them`() {
        val packer = ShelfPacker(64, 64, padding)
        packer.place(10, 10)
        val first = reserved(packer, 10, 10)
        packer.place(10, 10)
        val second = reserved(packer, 10, 10)
        assertFalse(overlaps(first, second))
        // At least two texels of separation: one border each.
        assertTrue(second[0] - first[2] >= 0)
        assertTrue(packer.contentX - (first[2] - padding) >= 2 * padding)
    }

    @Test
    fun `a full row opens a new shelf below the tallest entry in it`() {
        val packer = ShelfPacker(32, 64, padding)
        packer.place(12, 20) // padded 14x22
        packer.place(12, 8) // padded 14x10, same shelf
        val tallest = 22
        assertEquals(padding, packer.contentY)
        assertTrue(packer.place(12, 5)) // 14 more would exceed 32, so a new shelf opens
        assertEquals(tallest + padding, packer.contentY)
        assertEquals(padding, packer.contentX)
    }

    @Test
    fun `no two placements ever overlap across many mixed sizes`() {
        // The real sprite set is a wide spread of shapes arriving in scene order, which is exactly
        // the case shelf packing is weakest at. Sizes chosen to force repeated row breaks.
        val packer = ShelfPacker(256, 256, padding)
        val rects = mutableListOf<IntArray>()
        var w = 7
        var h = 3
        repeat(60) {
            if (packer.place(w, h)) rects.add(reserved(packer, w, h))
            w = (w * 5 + 11) % 61 + 1
            h = (h * 7 + 13) % 47 + 1
        }
        assertTrue("expected several placements to succeed", rects.size > 20)
        for (i in rects.indices) {
            for (j in i + 1 until rects.size) {
                assertFalse("entries $i and $j overlap", overlaps(rects[i], rects[j]))
            }
        }
    }

    @Test
    fun `every placement stays inside the atlas bounds`() {
        val size = 128
        val packer = ShelfPacker(size, size, padding)
        var w = 5
        var h = 9
        repeat(80) {
            if (packer.place(w, h)) {
                val r = reserved(packer, w, h)
                assertTrue("left edge", r[0] >= 0)
                assertTrue("top edge", r[1] >= 0)
                assertTrue("right edge $r[2]", r[2] <= size)
                assertTrue("bottom edge", r[3] <= size)
            }
            w = (w * 3 + 17) % 40 + 1
            h = (h * 3 + 5) % 30 + 1
        }
    }

    @Test
    fun `an entry whose padding is what overflows the row still opens a new shelf`() {
        // The row-break test has to be made against the *padded* width, not the content width.
        // Randomised size sweeps miss this: it needs an entry that fits by content and overflows
        // only once its border is counted, and getting that by chance is unlikely. Mutation testing
        // found the gap — a packer comparing content widths placed this entry with its right border
        // hanging outside the atlas.
        val width = 32
        val packer = ShelfPacker(width, 64, padding)
        packer.place(10, 10) // occupies 0..11 including padding
        assertTrue(packer.place(19, 10)) // content fits in 32, content + 2 padding does not
        val r = reserved(packer, 19, 10)
        assertTrue("reserved rect must not overflow the atlas: ${r[2]} > $width", r[2] <= width)
        assertEquals("must have moved to a new shelf", padding, packer.contentX)
    }

    @Test
    fun `a rejected placement leaves the packer untouched`() {
        // A sprite that is merely too tall for what remains must not close a shelf or consume
        // width, or the entries after it would be pushed out of an atlas that still had room.
        val packer = ShelfPacker(64, 64, padding)
        packer.place(10, 10)
        val xBefore = packer.contentX
        val countBefore = packer.placedCount
        assertFalse(packer.place(10, 10_000))
        assertEquals(xBefore, packer.contentX)
        assertEquals(countBefore, packer.placedCount)
        assertTrue(packer.place(10, 10))
        assertEquals(countBefore + 1, packer.placedCount)
    }

    @Test
    fun `an entry too large for the atlas is rejected rather than clipped`() {
        val packer = ShelfPacker(64, 64, padding)
        assertFalse(packer.fitsAtAll(64, 10)) // 64 + 2 padding exceeds the width
        assertFalse(packer.place(64, 10))
        assertTrue(packer.fitsAtAll(62, 62))
        assertFalse(packer.fitsAtAll(0, 10))
        assertFalse(packer.fitsAtAll(10, 0))
    }

    @Test
    fun `the atlas eventually fills and refuses further entries`() {
        val packer = ShelfPacker(16, 16, padding)
        var placed = 0
        repeat(50) { if (packer.place(6, 6)) placed++ }
        // 16x16 with 8x8 padded cells fits four, and nothing after that.
        assertEquals(4, placed)
        assertEquals(4, packer.placedCount)
        assertFalse(packer.place(6, 6))
    }

    @Test
    fun `reset returns the packer to empty`() {
        val packer = ShelfPacker(32, 32, padding)
        repeat(5) { packer.place(6, 6) }
        packer.reset()
        assertEquals(0, packer.placedCount)
        assertTrue(packer.place(6, 6))
        assertEquals(padding, packer.contentX)
        assertEquals(padding, packer.contentY)
    }

    @Test
    fun `zero padding still packs without overlap`() {
        val packer = ShelfPacker(32, 32, padding = 0)
        assertTrue(packer.place(16, 16))
        assertEquals(0, packer.contentX)
        assertTrue(packer.place(16, 16))
        assertEquals(16, packer.contentX)
    }
}
