package com.paperscrape.livewallpaper.engine

/**
 * Where a palm's two parts sit, stated once for both the things that draw a palm.
 *
 * ### Why this exists
 *
 * The same reason [TreeSpriteLayout] does: the renderer and the gallery preview both blit a palm,
 * and an origin written as a literal on each side drifts the first time the artwork moves -- a
 * crown blitted at the old origin stands beside its trunk. Stated once here, the two cannot
 * disagree.
 *
 * ### The numbers
 *
 * Every value is derived from the artwork's own canvas and declared anchor, stated beside it. The palm has **no lift**: unlike a leafy tree, whose crown sways about the point where the
 * leaves meet the trunk, a palm sways as one rigid body pivoted at its foot, so the preview needs
 * no flattened pair and these offsets are what both sides blit directly.
 */
internal object PalmSpriteLayout {

    /**
     * The trunk, blitted in the object's own space.
     *
     * `palmtree_trunk` is 63x174 px = 21x58 u, and the point that stands on the ground is the
     * centre of its flared foot at x=8 u — **not** the horizontal centre of what is drawn, which
     * is 10.33: the spindle leans 7 units to the right, so its content spans x 0.67..20 and the
     * two are different numbers for the first time. Blitting on the content centre would stand the
     * palm more than two units beside its own ground shadow and pivot the sway off its base,
     * which is why the registry declares the foot (`DECLARED_ATTACHMENT` at (24, 174) px) instead
     * of deriving it, and why this is that declaration negated.
     *
     * The canvas is what `paperscrape-assets normalize` leaves, which is the ink plus two pixels
     * of guard on each side rather than the ink: a `SCENE_UNITS` blit is filtered, so the column
     * beside the drawing is the one the sampler reads at the edge, and cropping onto the ink makes
     * it clamp and read the ink column twice (measured on the lake sprites).
     */
    const val TRUNK_X = -8f
    const val TRUNK_Y = -58f

    /**
     * The crown, in the same space: no lift, for the reason this object's doc gives.
     *
     * All three crowns — live, Halloween's dead one, the winter palette's frosted one — are drawn
     * on one 168x144 px = 56x48 u canvas around one `DECLARED_ATTACHMENT` at (84, 78) px =
     * (28, 26) u, which is where the seven blades converge. The attachment is placed two units
     * below the top of the trunk so the crown overlaps it rather than balancing on it, and the
     * trunk leans, so that point is at (7, -56) in object space and this origin is (7, -56) minus
     * (28, 26).
     *
     * **One origin for all three, which is what makes the winter crown a choice and not a layer**:
     * they are three whole drawings, and a `when` picks one; nothing is laid over another crown
     * that would have to cover it pixel for pixel.
     */
    const val CROWN_X = -21f
    const val CROWN_Y = -82f

    /**
     * The crown's canvas in object space, which is where everything hung on it is placed.
     *
     * The canvas is the drawing plus a transparent guard, so blitted at ([CROWN_X], [CROWN_Y]) the
     * canvas occupies x -21..35 and y -82..-34: centre (7, -58), half extents 28 x 24 (the live
     * crown's ink is -18.3..34 by -78.3..-38.7, see `SpriteOccluderTable.PALM_CROWN`). **The
     * centre is not over the trunk**, and that is the consequence of the lean that reaches
     * furthest — the Christmas lights and the falling-leaf source read it, and the layout pass's
     * reach reads [CROWN_HALF_WIDTH]; the occlusion box reads the measured ink in
     * `SpriteOccluderTable.PALM_CROWN` instead. A crown centred on the pivot would have put the
     * lights and the leaves half a crown to the left of the fronds.
     */
    const val CROWN_CENTRE_X = 7f
    const val CROWN_CENTRE_Y = -58f
    const val CROWN_HALF_WIDTH = 28f
    const val CROWN_HALF_HEIGHT = 24f
}
