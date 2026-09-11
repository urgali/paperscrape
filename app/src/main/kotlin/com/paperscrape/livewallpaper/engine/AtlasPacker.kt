package com.paperscrape.livewallpaper.engine

/**
 * Decides where each sprite sits inside the texture atlas. Pure arithmetic, no GL.
 *
 * Kept separate from [GlTextureAtlas] for the same reason [SceneTransform] is kept separate from
 * [GlSceneTarget]: the atlas cannot be instantiated without a GL context, and a packing bug is
 * silent. Two entries given overlapping rectangles do not throw — one sprite simply renders with
 * another's pixels inside it, in whichever scene happens to draw them in that order.
 *
 * ## Algorithm: a skyline, and why it replaced shelves
 *
 * Placement is bottom-left over a **skyline** — a run-length description of the lowest free y at
 * every column. Each entry is put at the position with the lowest resulting top edge, ties broken
 * leftwards, and the skyline is raised over the columns it covers.
 *
 * Until v4.29 this was a **shelf** packer: entries filled a row left to right until one did not
 * fit, then a new row opened below the tallest entry of the row just closed. Its own comment called
 * the extra waste "acceptable here and neither is worth the code it would take to fix", on the
 * grounds that "the set is small". **That was measured in v4.29 and it was wrong, by more than a
 * factor of two.** A shelf packer loses two things and reclaims neither:
 *
 * - **the tail of every closed row**, because only one shelf is ever open, and
 * - **the slack above every entry shorter than the tallest one in its row**.
 *
 * Walking the twelve themes at their own defaults, clear weather, the shipped shelf packer consumed
 * **99 % of the atlas's 2 048 rows while holding 42 % of its area as content**, saturated on the
 * *fifth* theme, and spilled 25 sprites into standalone textures — each one a batch break per
 * frame, which is precisely what the atlas exists to prevent. At full density it spilled 34. The
 * census is in `release-verification/V4_29_REPORT.md`; `AtlasPackerTest` replays the recorded
 * insertion sequence so the improvement cannot regress unnoticed.
 *
 * The skyline is what the shelf packer's comment assumed was not worth writing: about eighty lines,
 * no sorting, and no deferred upload. It is **online** — it takes entries in whatever order the
 * scenes ask for them, which matters because nothing here knows the working set in advance. Height
 * sorting would pack better still and is not available: the first upload of a sprite happens in the
 * frame that first draws it.
 *
 * **Cost.** Each placement scans the skyline once per segment, so it is quadratic in the number of
 * segments — at the ~300 entries a long session reaches, tens of thousands of integer comparisons.
 * It runs once per sprite *and level*, never per frame, in the same call that allocates a padded
 * bitmap and does a `texSubImage2D`; both of those dominate it.
 *
 * ## Padding
 *
 * Each placement reserves [padding] texels on all four sides, so neighbouring entries are separated
 * by at least twice that. This is what stops a bilinear sample near one sprite's edge from picking
 * up the sprite next to it. [contentX]/[contentY] report where the sprite's own top-left pixel goes;
 * the reserved rectangle around it is the packer's business.
 *
 * ## What it still does not do
 *
 * It cannot reuse the space of an entry that is no longer wanted, and that is unchanged from the
 * shelf packer: [GlTextureCache] never evicts a single entry, and the whole atlas is discarded in
 * one go under memory pressure. A packer that could free one rectangle would have nothing to free.
 */
class AtlasPacker(
    private val width: Int,
    private val height: Int,
    private val padding: Int = 1,
) {

    /**
     * The skyline, as three parallel arrays holding [segmentCount] segments ordered by x.
     *
     * Segment `i` says: from `segmentX[i]` for `segmentWidth[i]` texels, the lowest free y is
     * `segmentY[i]`. The segments tile `0 until width` exactly, with no gaps and no overlaps — that
     * invariant is what makes the span scan in [place] correct. `AtlasPackerTest` checks it from
     * the outside, against an occupancy grid it maintains itself: a corrupt skyline either reports
     * a position that is already taken, which the grid catches, or strands area, which
     * [occupiedHeight] compared against the grid catches.
     *
     * Primitive arrays rather than a list of objects, for the reason the rest of this engine uses
     * them: no boxing and no per-placement allocation.
     */
    private var segmentX = IntArray(INITIAL_SEGMENTS)
    private var segmentY = IntArray(INITIAL_SEGMENTS)
    private var segmentWidth = IntArray(INITIAL_SEGMENTS)
    private var segmentCount = 1

    init {
        segmentWidth[0] = width
    }

    /** X of the last successful [place]'s content, in texels. */
    var contentX: Int = 0
        private set

    /** Y of the last successful [place]'s content, in texels. */
    var contentY: Int = 0
        private set

    /** How many entries have been placed. */
    var placedCount: Int = 0
        private set

    /**
     * The lowest y no placement has reached, in texels: the atlas's **consumed height**.
     *
     * This is the number that says whether the atlas is big enough, and it is not the one the
     * v4.28 diagnostics reported. Content area over allocated area — 27 % on the reference scene —
     * counts the padded rectangles and is blind to every texel a packer has stranded. On that same
     * scene the shelf packer had already consumed **55 %** of the rows. Reading the first as the
     * second is how v4.28 concluded the atlas was "73 % empty" when it was closer to half full, and
     * `BACKLOG_v4_29.md` item 88 is what keeps both numbers reported side by side.
     */
    val occupiedHeight: Int
        get() {
            var top = 0
            for (i in 0 until segmentCount) {
                if (segmentY[i] > top) top = segmentY[i]
            }
            return top
        }

    /**
     * Whether [w] x [h] plus its padding could ever fit, independently of what is already placed.
     *
     * Separate from [place] so a caller can reject an oversized sprite without disturbing the
     * packer's state, and so the answer does not depend on how full the atlas happens to be.
     */
    fun fitsAtAll(w: Int, h: Int): Boolean =
        w > 0 && h > 0 && w + padding * 2 <= width && h + padding * 2 <= height

    /**
     * Reserves room for a [w] x [h] entry, reporting its position through [contentX]/[contentY].
     *
     * Returns false if it does not fit, in which case nothing is reserved and the packer is left
     * exactly as it was — a failed placement must not consume space, or a sprite that was merely
     * too tall for what remains would silently shrink the atlas for the entries after it.
     */
    fun place(w: Int, h: Int): Boolean {
        if (!fitsAtAll(w, h)) return false
        val paddedWidth = w + padding * 2
        val paddedHeight = h + padding * 2

        var bestIndex = -1
        var bestY = Int.MAX_VALUE
        var bestX = Int.MAX_VALUE
        for (i in 0 until segmentCount) {
            val y = spanTop(i, paddedWidth) ?: continue
            if (y + paddedHeight > height) continue
            val x = segmentX[i]
            // Bottom-left: the lowest resting place wins, and among equals the leftmost does.
            // Every candidate has the same height, so ordering on `y` orders on the resulting top
            // edge too.
            if (y < bestY || (y == bestY && x < bestX)) {
                bestIndex = i
                bestY = y
                bestX = x
            }
        }
        if (bestIndex < 0) return false

        contentX = bestX + padding
        contentY = bestY + padding
        raise(bestX, bestY + paddedHeight, paddedWidth)
        placedCount++
        return true
    }

    /**
     * The lowest y at which [paddedWidth] texels starting at segment [from] are all free, or null
     * when the span runs off the right edge of the atlas.
     *
     * The span is the highest skyline in it: an entry laid across several segments rests on the
     * tallest of them, not on the one it starts at.
     */
    private fun spanTop(from: Int, paddedWidth: Int): Int? {
        if (segmentX[from] + paddedWidth > width) return null
        var remaining = paddedWidth
        var top = 0
        var i = from
        while (remaining > 0) {
            if (i >= segmentCount) return null
            if (segmentY[i] > top) top = segmentY[i]
            remaining -= segmentWidth[i]
            i++
        }
        return top
    }

    /**
     * Raises the skyline to [newY] over [spanWidth] texels from [x], keeping the segments ordered,
     * gapless and merged.
     *
     * The three steps are separate on purpose, because each has its own failure mode and
     * `AtlasPackerTest` checks after *every* placement rather than after the last one: insert the
     * raised segment, trim whatever it now covers, then merge neighbours that ended up at the same
     * height so the segment count cannot grow without bound.
     */
    private fun raise(x: Int, newY: Int, spanWidth: Int) {
        var index = 0
        while (index < segmentCount && segmentX[index] < x) index++

        insertSegment(index, x, newY, spanWidth)

        var i = index + 1
        val spanEnd = x + spanWidth
        while (i < segmentCount) {
            val start = segmentX[i]
            if (start >= spanEnd) break
            val end = start + segmentWidth[i]
            if (end <= spanEnd) {
                removeSegment(i)
            } else {
                segmentWidth[i] = end - spanEnd
                segmentX[i] = spanEnd
                break
            }
        }

        i = 0
        while (i < segmentCount - 1) {
            if (segmentY[i] == segmentY[i + 1]) {
                segmentWidth[i] += segmentWidth[i + 1]
                removeSegment(i + 1)
            } else {
                i++
            }
        }
    }

    private fun insertSegment(at: Int, x: Int, y: Int, w: Int) {
        if (segmentCount == segmentX.size) grow()
        for (i in segmentCount downTo at + 1) {
            segmentX[i] = segmentX[i - 1]
            segmentY[i] = segmentY[i - 1]
            segmentWidth[i] = segmentWidth[i - 1]
        }
        segmentX[at] = x
        segmentY[at] = y
        segmentWidth[at] = w
        segmentCount++
    }

    private fun removeSegment(at: Int) {
        for (i in at until segmentCount - 1) {
            segmentX[i] = segmentX[i + 1]
            segmentY[i] = segmentY[i + 1]
            segmentWidth[i] = segmentWidth[i + 1]
        }
        segmentCount--
    }

    private fun grow() {
        val capacity = segmentX.size * 2
        segmentX = segmentX.copyOf(capacity)
        segmentY = segmentY.copyOf(capacity)
        segmentWidth = segmentWidth.copyOf(capacity)
    }

    /** Forgets every placement. The atlas texture is expected to be discarded alongside. */
    fun reset() {
        segmentCount = 1
        segmentX[0] = 0
        segmentY[0] = 0
        segmentWidth[0] = width
        placedCount = 0
        contentX = 0
        contentY = 0
    }

    private companion object {
        /**
         * Segments at which the arrays start.
         *
         * A placement adds at most one segment and can remove several, and [raise] merges
         * neighbours that end up level, so the count tracks the *shape* of the skyline and not the
         * entry count. Replaying the recorded twelve-theme walk at full density — 304 entries —
         * peaks at **34** segments and settles at 12, so 64 is comfortably clear and the arrays
         * never grow in practice. They still can: this is a starting size, not a limit.
         */
        const val INITIAL_SEGMENTS = 64
    }
}
