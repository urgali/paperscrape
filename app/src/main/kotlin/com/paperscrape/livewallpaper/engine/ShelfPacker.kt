package com.paperscrape.livewallpaper.engine

/**
 * Decides where each sprite sits inside the texture atlas. Pure arithmetic, no GL.
 *
 * Kept separate from [GlTextureAtlas] for the same reason [SceneTransform] is kept separate from
 * [GlSceneTarget]: the atlas cannot be instantiated without a GL context, and a packing bug is
 * silent. Two entries given overlapping rectangles do not throw — one sprite simply renders with
 * another's pixels inside it, in whichever scene happens to draw them in that order.
 *
 * ## Algorithm
 *
 * Shelf packing, first fit, in insertion order. Entries fill a row left to right until one does not
 * fit, then a new row opens below the tallest entry of the row just closed.
 *
 * It wastes more area than a real bin packer, and it cannot reuse the space of an entry that is no
 * longer wanted. Both are acceptable here and neither is worth the code it would take to fix: the
 * set is small, it is only ever added to, and the whole atlas is discarded in one go under memory
 * pressure rather than having entries removed from it.
 *
 * ## Padding
 *
 * Each placement reserves [padding] texels on all four sides, so neighbouring entries are separated
 * by at least twice that. This is what stops a bilinear sample near one sprite's edge from picking
 * up the sprite next to it. [contentX]/[contentY] report where the sprite's own top-left pixel goes;
 * the reserved rectangle around it is the packer's business.
 */
class ShelfPacker(
    private val width: Int,
    private val height: Int,
    private val padding: Int = 1,
) {

    private var shelfX = 0
    private var shelfY = 0
    private var shelfHeight = 0

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
     * Whether [w] x [h] plus its padding could ever fit, independently of what is already placed.
     *
     * Separate from [place] so a caller can reject an oversized sprite without disturbing the shelf
     * state, and so the answer does not depend on how full the atlas happens to be.
     */
    fun fitsAtAll(w: Int, h: Int): Boolean =
        w > 0 && h > 0 && w + padding * 2 <= width && h + padding * 2 <= height

    /**
     * Reserves room for a [w] x [h] entry, reporting its position through [contentX]/[contentY].
     *
     * Returns false if it does not fit, in which case nothing is reserved and the packer is left
     * exactly as it was — a failed placement must not consume space or close a shelf, or a sprite
     * that was merely too tall for the current row would silently shrink the atlas.
     */
    fun place(w: Int, h: Int): Boolean {
        if (!fitsAtAll(w, h)) return false
        val paddedWidth = w + padding * 2
        val paddedHeight = h + padding * 2

        var x = shelfX
        var y = shelfY
        var openNewShelf = false
        if (x + paddedWidth > width) {
            x = 0
            y = shelfY + shelfHeight
            openNewShelf = true
        }
        if (y + paddedHeight > height) return false

        contentX = x + padding
        contentY = y + padding

        if (openNewShelf) {
            shelfX = paddedWidth
            shelfY = y
            shelfHeight = paddedHeight
        } else {
            shelfX = x + paddedWidth
            if (paddedHeight > shelfHeight) shelfHeight = paddedHeight
        }
        placedCount++
        return true
    }

    /** Forgets every placement. The atlas texture is expected to be discarded alongside. */
    fun reset() {
        shelfX = 0
        shelfY = 0
        shelfHeight = 0
        placedCount = 0
        contentX = 0
        contentY = 0
    }
}
