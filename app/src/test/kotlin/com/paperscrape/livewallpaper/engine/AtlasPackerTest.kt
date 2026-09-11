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
 * sprite along an edge, which is subtle enough to survive a casual look at the wallpaper.
 *
 * So the central assertion is not about the skyline's bookkeeping at all: it is an **occupancy
 * grid** the test keeps itself, stamped after every placement. A skyline that loses a segment, or
 * merges two that were at different heights, or trims the wrong one, shows up as a placement onto
 * texels that are already taken — and one that strands area shows up as [AtlasPacker.occupiedHeight]
 * disagreeing with the grid. Neither check trusts the packer to describe itself.
 */
class AtlasPackerTest {

    private val padding = 1

    /** Reserved rectangle (including padding) of the placement just made. */
    private fun reserved(packer: AtlasPacker, w: Int, h: Int) = intArrayOf(
        packer.contentX - padding,
        packer.contentY - padding,
        packer.contentX + w + padding,
        packer.contentY + h + padding,
    )

    private fun overlaps(a: IntArray, b: IntArray): Boolean =
        a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3]

    /**
     * Stamps a reserved rectangle into [grid], failing if any texel of it was already taken or
     * falls outside the atlas.
     */
    private fun stamp(grid: Array<BooleanArray>, r: IntArray, size: Int, label: String) {
        assertTrue("$label: left edge ${r[0]}", r[0] >= 0)
        assertTrue("$label: top edge ${r[1]}", r[1] >= 0)
        assertTrue("$label: right edge ${r[2]} > $size", r[2] <= size)
        assertTrue("$label: bottom edge ${r[3]} > $size", r[3] <= size)
        for (y in r[1] until r[3]) {
            for (x in r[0] until r[2]) {
                assertFalse("$label: texel ($x,$y) was already occupied", grid[y][x])
                grid[y][x] = true
            }
        }
    }

    @Test
    fun `the first placement sits at the padding offset`() {
        val packer = AtlasPacker(64, 64, padding)
        assertTrue(packer.place(10, 10))
        assertEquals(padding, packer.contentX)
        assertEquals(padding, packer.contentY)
    }

    @Test
    fun `entries advance along the bottom row leaving padding between them`() {
        val packer = AtlasPacker(64, 64, padding)
        packer.place(10, 10)
        val first = reserved(packer, 10, 10)
        packer.place(10, 10)
        val second = reserved(packer, 10, 10)
        assertFalse(overlaps(first, second))
        assertEquals("the second entry belongs beside the first, not above it", padding, packer.contentY)
        // At least two texels of separation: one border each.
        assertTrue(packer.contentX - (first[2] - padding) >= 2 * padding)
    }

    /**
     * The defect v4.29 replaced the shelf packer to fix, reduced to four entries.
     *
     * A shelf packer sets a row's height to the **tallest** entry in it and opens the next row below
     * that, so the space above every shorter entry in the row is lost for good and nothing ever goes
     * back for it.
     *
     * Here the atlas is 68 wide, which is exactly three padded 22s. The bottom row takes a tall
     * entry, a short one, and a tall one, so it stands 22 high with a 22x14 hole above the short
     * entry in the middle of it. The fourth entry is 20x12 — padded 22x14, the hole's exact size.
     *
     * A shelf packer puts it on a new row at y=22 and finishes 36 texels tall. The skyline puts it
     * in the hole and finishes at 22, the height the two tall entries already needed. Asserted as
     * an exact position and an exact height, because the point is that the reclaimed space is the
     * *specific* space a shelf strands, not merely that less was used.
     */
    @Test
    fun `the space above a short entry is reused instead of being stranded`() {
        val packer = AtlasPacker(68, 68, padding)
        assertTrue(packer.place(20, 20))
        assertTrue(packer.place(20, 6))
        assertTrue(packer.place(20, 20))
        assertEquals("three padded 22s fill a 68-wide atlas", 22, packer.occupiedHeight)

        assertTrue(packer.place(20, 12))
        assertEquals("must land in the hole above the short entry", 22 + padding, packer.contentX)
        assertEquals("must land at the short entry's own top edge", 8 + padding, packer.contentY)
        assertEquals(
            "a shelf packer would have opened a row at 22 and finished 36 tall",
            22, packer.occupiedHeight,
        )
    }

    @Test
    fun `no two placements ever overlap across many mixed sizes`() {
        // The real sprite set is a wide spread of shapes arriving in scene order, which is exactly
        // the case a naive packer is weakest at. Sizes chosen to force repeated height changes.
        val size = 256
        val packer = AtlasPacker(size, size, padding)
        val grid = Array(size) { BooleanArray(size) }
        val rects = mutableListOf<IntArray>()
        var w = 7
        var h = 3
        repeat(60) { i ->
            if (packer.place(w, h)) {
                val r = reserved(packer, w, h)
                stamp(grid, r, size, "entry $i (${w}x$h)")
                rects.add(r)
            }
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

    /**
     * The skyline's own account of how much of the atlas it has consumed must match what was
     * actually stamped.
     *
     * `occupiedHeight` is the number v4.29 replaced content-area fill with, so it has to be checked
     * against something that is not the skyline: a packer that lost a raised segment would report a
     * lower consumed height than it had really used, which is precisely the direction that would
     * make the atlas look roomier than it is — the same error, in kind, as the "73 % empty" reading
     * this packer was written to answer.
     */
    @Test
    fun `the reported consumed height matches the texels actually reserved`() {
        val size = 128
        val packer = AtlasPacker(size, size, padding)
        val grid = Array(size) { BooleanArray(size) }
        var w = 5
        var h = 9
        repeat(80) { i ->
            if (packer.place(w, h)) {
                stamp(grid, reserved(packer, w, h), size, "entry $i")
            }
            var highest = 0
            for (y in 0 until size) {
                if (grid[y].any { it }) highest = y + 1
            }
            assertEquals(
                "after entry $i the packer and the grid disagree about the consumed height",
                highest, packer.occupiedHeight,
            )
            w = (w * 3 + 17) % 40 + 1
            h = (h * 3 + 5) % 30 + 1
        }
    }

    @Test
    fun `an entry whose padding is what overflows the row still moves down`() {
        // The fit test has to be made against the *padded* width, not the content width.
        // Randomised size sweeps miss this: it needs an entry that fits by content and overflows
        // only once its border is counted, and getting that by chance is unlikely. Mutation testing
        // found the gap in the shelf packer — one comparing content widths placed this entry with
        // its right border hanging outside the atlas — and the same mutation is available here.
        val width = 32
        val packer = AtlasPacker(width, 64, padding)
        packer.place(10, 10) // occupies 0..11 including padding
        assertTrue(packer.place(19, 10)) // content fits in 32, content + 2 padding does not
        val r = reserved(packer, 19, 10)
        assertTrue("reserved rect must not overflow the atlas: ${r[2]} > $width", r[2] <= width)
        assertEquals("must have moved below the first entry", padding, packer.contentX)
        assertEquals(12 + padding, packer.contentY)
    }

    @Test
    fun `a rejected placement leaves the packer untouched`() {
        // A sprite that is merely too tall for what remains must not consume space, or the entries
        // after it would be pushed out of an atlas that still had room.
        val packer = AtlasPacker(64, 64, padding)
        packer.place(10, 10)
        val xBefore = packer.contentX
        val countBefore = packer.placedCount
        val heightBefore = packer.occupiedHeight
        assertFalse(packer.place(10, 10_000))
        assertEquals(xBefore, packer.contentX)
        assertEquals(countBefore, packer.placedCount)
        assertEquals(heightBefore, packer.occupiedHeight)
        assertTrue(packer.place(10, 10))
        assertEquals(countBefore + 1, packer.placedCount)
    }

    @Test
    fun `an entry too large for the atlas is rejected rather than clipped`() {
        val packer = AtlasPacker(64, 64, padding)
        assertFalse(packer.fitsAtAll(64, 10)) // 64 + 2 padding exceeds the width
        assertFalse(packer.place(64, 10))
        assertTrue(packer.fitsAtAll(62, 62))
        assertFalse(packer.fitsAtAll(0, 10))
        assertFalse(packer.fitsAtAll(10, 0))
    }

    @Test
    fun `the atlas eventually fills and refuses further entries`() {
        val packer = AtlasPacker(16, 16, padding)
        var placed = 0
        repeat(50) { if (packer.place(6, 6)) placed++ }
        // 16x16 with 8x8 padded cells fits four, and nothing after that.
        assertEquals(4, placed)
        assertEquals(4, packer.placedCount)
        assertFalse(packer.place(6, 6))
    }

    @Test
    fun `reset returns the packer to empty`() {
        val packer = AtlasPacker(32, 32, padding)
        repeat(5) { packer.place(6, 6) }
        packer.reset()
        assertEquals(0, packer.placedCount)
        assertEquals(0, packer.occupiedHeight)
        assertTrue(packer.place(6, 6))
        assertEquals(padding, packer.contentX)
        assertEquals(padding, packer.contentY)
    }

    @Test
    fun `zero padding still packs without overlap`() {
        val packer = AtlasPacker(32, 32, padding = 0)
        assertTrue(packer.place(16, 16))
        assertEquals(0, packer.contentX)
        assertTrue(packer.place(16, 16))
        assertEquals(16, packer.contentX)
        assertEquals(16, packer.occupiedHeight)
    }

    // ------------------------------------------------- the recorded walk

    /**
     * An insertion sequence recorded off the device, so the packing improvement is a standing test
     * rather than a figure in a report nobody re-runs.
     *
     * **Where it comes from.** A CAPTURE-ONLY build logs every `GlTextureAtlas.add(w, h)` call in
     * order, tagged with the atlas's identity so the wallpaper-picker's short-lived preview engine
     * can be told apart from the live one. The walk driver put each of the twelve themes on screen
     * through `files/capture.txt` for 45 s, at 13:00 and again at 23:00, in one process, **with
     * every category visible and every density at 1, raining and in storm** -- the fullest scene
     * the app can draw, which is what an atlas has to be sized against. The leading `1x1` is the
     * flat-fill white pixel `GlTextureCache.registerWhitePixel` packs as every target's first entry.
     *
     * **What it is not.** It is *a* recorded sequence and not a complete census: `logcat` drops
     * lines under burst, and this run recorded 304 of the 317 entries the probe's own counter
     * reported. That does not weaken what is asserted below -- a packer that fits 304 of these
     * where the shipped one spilled 117 is being measured either way -- but it is why this is not
     * an inventory of the sprite set. `SpriteGeometryTest` and `SpriteDrawScaleTest` are those.
     *
     * **Regenerating it** is a device run, described in `release-verification/V4_29_REPORT.md`
     * section 1.3. Do not hand-edit it: its value is that nobody chose it.
     */
    private val recordedWalk = """
        1x1 45x45 22x22 240x240 399x198 798x396 105x90 126x25 86x43 27x9
        54x18 66x9 135x225 118x150 48x48 9x10 9x10 90x48 9x10 9x10
        18x21 18x21 48x93 151x99 18x21 18x21 72x52 79x30 79x3 9x22
        16x15 16x16 18x21 19x4 15x28 144x105 159x60 159x6 18x45 33x31
        33x33 39x9 30x57 300x288 330x36 66x63 66x66 90x66 36x42 276x27
        78x18 54x84 276x39 28x55 9x3 18x4 30x22 30x30 21x33 13x25
        9x4 15x3 12x22 5x5 3x3 24x30 24x14 28x22 4x9 96x186
        303x198 18x21 18x21 270x276 300x48 60x84 246x30 18x30 29x63 29x63
        29x63 9x10 9x10 18x21 18x21 210x142 225x75 225x9 19x52 33x67
        18x21 18x21 36x42 29x63 29x63 29x63 29x63 29x63 29x63 29x63
        139x76 93x37 60x13 21x9 9x6 6x6 9x6 6x6 28x31 28x31
        163x75 88x37 60x13 30x9 28x31 28x31 396x396 240x240 594x297 51x21
        40x13 14x27 4x1 9x2 25x45 11x11 7x7 40x13 28x31 28x31
        36x42 36x42 90x252 180x66 180x66 187x78 121x37 28x31 29x63 29x63
        29x63 150x21 297x174 18x12 12x12 28x31 29x63 36x18 29x63 29x63
        28x31 28x31 29x63 29x63 29x63 29x63 28x31 28x31 28x31 29x63
        29x63 29x63 54x10 27x5 108x21 18x21 36x42 36x42 29x63 29x63
        29x63 29x63 29x63 29x63 29x63 28x31 29x63 29x63 29x63 54x10
        27x5 9x10 84x13 147x57 105x71 112x37 75x29 112x4 9x26 16x33
        150x58 156x24 102x49 294x114 300x18 36x42 29x63 29x63 29x63 29x63
        29x63 29x63 29x63 29x63 29x63 28x31 28x31 29x63 29x63 28x31
        28x31 29x63 29x63 29x63 29x63 28x31 28x31 28x31 28x31 28x31
        28x31 28x31 28x31 29x63 29x63 29x63 28x31 28x31 29x63 29x63
        29x63 9x10 9x10 16x87 60x60 150x144 165x18 45x33 138x13 27x42
        138x19 33x174 120x120 29x63 29x63 29x63 29x63 29x63 29x63 29x63
        29x63 29x63 29x63 9x10 9x10 120x183 84x117 15x11 15x15 36x42
        240x366 168x234 29x63 29x63 29x63 29x63 29x63 29x63 29x63 594x123
        594x123 29x63 29x63 29x63 29x63 29x63 29x63 29x63 29x63 78x12
        36x42 29x63 29x63 29x63 29x63 29x63 29x63 29x63 29x63 29x63
        36x42 29x63 29x63 29x63 141x99 282x198 28x22 36x42 18x12 12x12
        60x60 120x120 240x240 240x240
    """.trimIndent().split(Regex("\\s+")).filter { it.isNotEmpty() }.map {
        val (w, h) = it.split("x")
        w.toInt() to h.toInt()
    }

    /**
     * The acceptance criterion of v4.29, as a test.
     *
     * The shipped shelf packer put **117 of these 304 entries outside the atlas** -- each one a
     * texture of its own and a potential batch break in every frame that draws it, which is exactly
     * what the atlas exists to prevent. Zero is the bar, and it is asserted as zero rather than as
     * "fewer than before" because anything above zero is the same defect at a smaller scale.
     *
     * The occupancy floor is the second half. A packer could pass the first assertion and still be
     * wasteful on a set that merely happens to fit, so the content actually placed is held above
     * half the atlas -- measured at 58.4 % here, against the shelf packer's 39.7 % on this same
     * sequence. 50 % is a floor with room under the measurement, not a target: if a future artwork
     * pass pushes it down, this is where that becomes visible.
     */
    @Test
    fun `every entry of the recorded twelve-theme walk fits in the atlas`() {
        val size = GlTextureAtlas.DEFAULT_SIZE
        val padding = GlTextureAtlas.PADDING
        val packer = AtlasPacker(size, size, padding)
        var placedArea = 0L
        val spilled = mutableListOf<String>()
        for ((w, h) in recordedWalk) {
            if (packer.place(w, h)) {
                placedArea += (w + 2L * padding) * (h + 2L * padding)
            } else {
                spilled += "${w}x$h"
            }
        }
        assertEquals(
            "these entries of the recorded walk did not fit and would become standalone textures, " +
                "one batch break each: $spilled",
            emptyList<String>(), spilled,
        )
        val fillPercent = 100.0 * placedArea / (size.toLong() * size)
        assertTrue(
            "the walk's content fills only $fillPercent % of the atlas. The skyline packer measured " +
                "58.4 % and the shelf packer it replaced managed 39.7 % on this same sequence, so a " +
                "drop back towards that is the v4.29 defect returning.",
            fillPercent >= 50.0,
        )
    }

    @Test
    fun `a non-square atlas is packed to its own bounds`() {
        // `GlTextureAtlas` passes width and height separately and v4.29 measured a non-square atlas
        // as refused rather than impossible, so the packer has to keep the two apart. A packer that
        // compared against one dimension twice would pass every square test above.
        val packer = AtlasPacker(64, 16, padding)
        assertTrue(packer.fitsAtAll(60, 12))
        assertFalse("12 wide fits, 12 tall plus padding does not leave room for two", packer.fitsAtAll(60, 16))
        assertTrue(packer.place(60, 12))
        assertFalse(packer.place(60, 12))
    }
}
