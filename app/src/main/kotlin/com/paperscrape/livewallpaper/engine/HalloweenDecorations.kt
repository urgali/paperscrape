package com.paperscrape.livewallpaper.engine

/**
 * Where a building's Halloween decorations hang (v5.16, the set C of v5.16A: the maintainer's *«C ragnatele e
 * pipistrelli»*, 2026-10-21), **one sequence for the wallpaper and the gallery card**, as [ChristmasDecorations] is for
 * the Christmas ones: `SceneObjectRenderer.drawHalloweenDecorations` draws what this yields at the scene's moment -- the
 * bats swinging, the jack-o'-lantern lit by night -- and `ThemePreviewScenes` lays the same points out on the card's
 * buildings, still and lit. Every place is one the buildings' generator wrote into `NeighbourhoodTable.kt`, in the
 * building's own piece units from its feet, so none is a number typed in the renderer:
 *
 * - a cobweb at each end of the top piece's roofline ([BuildingPiece.roofX]): the drawing's own at the left end, mirrored
 *   at the right;
 * - a bat every [BAT_EVERY] points of the roofline (every 18 units), from the third point to the third from the end,
 *   hanging under it;
 * - a jack-o'-lantern on the ground by every front door ([BuildingDoor]), its middle [DOOR_ASIDE_UNITS] to the door's left.
 *
 * **Only on the houses and the three shops** ([decorates]; *«il resto, si a tutte»* to the table of v5.16A, 2026-10-21:
 * on a tower the two cobwebs of its top met in one white patch and the door's pumpkin hung in front of it), lit whether a
 * shop is open or shut. Each function that takes a block is `inline`, so the draw path allocates nothing for it.
 */
internal object HalloweenDecorations {

    /** How many points of the roofline apart the bats hang: every 18 units. */
    const val BAT_EVERY = 3

    /** How far to the left of a front door's left edge the jack-o'-lantern stands, in piece units. */
    const val DOOR_ASIDE_UNITS = 7f

    /** Whether a building wears them: a house, or one of the three shops among the houses. */
    fun decorates(kind: WindowBuildingKind, storefront: Storefront?): Boolean =
        kind == WindowBuildingKind.HOUSE ||
            storefront == Storefront.RESTAURANT || storefront == Storefront.BAR || storefront == Storefront.SHOP

    /** The two cobwebs: [action] gets each one's corner of the roof and +1 for the drawing as it is, -1 mirrored. */
    inline fun forEachCobweb(deal: NeighbourhoodComposer.Deal, action: (x: Float, y: Float, mirror: Float) -> Unit) {
        if (deal.size == 0) return
        val top = deal[deal.size - 1]
        val xs = top.piece.roofX
        val ys = top.piece.roofY
        if (xs.size < 2) return
        val last = xs.size - 1
        action(xs[0], top.baseY + ys[0], 1f)
        action(xs[last], top.baseY + ys[last], -1f)
    }

    /** The bats: [action] gets each one's index along the roofline and the point it hangs from. */
    inline fun forEachBat(deal: NeighbourhoodComposer.Deal, action: (index: Int, x: Float, y: Float) -> Unit) {
        if (deal.size == 0) return
        val top = deal[deal.size - 1]
        val xs = top.piece.roofX
        val ys = top.piece.roofY
        var i = 2
        while (i <= xs.size - 3) {
            action(i, xs[i], top.baseY + ys[i])
            i += BAT_EVERY
        }
    }

    /** The jack-o'-lantern by every front door: [action] gets the bottom middle it stands on. */
    inline fun forEachDoorPumpkin(deal: NeighbourhoodComposer.Deal, action: (x: Float, y: Float) -> Unit) {
        for (index in 0 until deal.size) {
            val placed = deal[index]
            val door = placed.piece.door ?: continue
            action(door.x - DOOR_ASIDE_UNITS, placed.baseY + door.y + door.h)
        }
    }
}
