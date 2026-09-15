package com.paperscrape.livewallpaper.engine

/**
 * Where a palm's two parts sit, stated once for both the things that draw a palm.
 *
 * ### Why this exists, and why now
 *
 * The same reason [TreeSpriteLayout] does, arrived at from the other direction. That object was
 * created because a drift had already happened: the preview's snow cap had been left behind when
 * the renderer's origin moved, and the copy was removed for the one object where it had
 * demonstrably gone wrong. The v3.8 audit then looked at the other 55 shared drawables, found all
 * 55 in exact agreement as plain literals on both sides, and deliberately stopped — hoisting
 * numbers that had never drifted would have guarded against nothing.
 *
 * The palm was two of those 55, and it was safe for exactly as long as nobody moved it. **v5.1
 * moved it**: the crown went from a 40x40-unit canvas to a 56x48 one and the trunk gained a lean,
 * so both origins changed, by hand, in two files, in one pass — which is the state the tree was in
 * the release before it drifted. So the palm joins the tree here rather than being copied
 * correctly one more time and left to chance.
 *
 * ### The numbers
 *
 * Every value is re-derived from the v5.1 artwork's own canvas and declared anchor, stated beside
 * it. The palm has **no lift**: unlike a leafy tree, whose crown sways about the point where the
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
     * it clamp and read the ink column twice (v4.31, measured on the lake sprites).
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
     * **One origin for all three, which is what makes the winter crown a choice and not a layer.**
     * Until v5.1 the frost was white caps blitted on top of the live crown and the two had to
     * cover each other pixel for pixel; they are three whole drawings now, and a `when` picks one.
     */
    const val CROWN_X = -21f
    const val CROWN_Y = -82f

    /**
     * The crown's own content in object space, which is where everything hung on it is placed.
     *
     * The canvas is filled by the drawing, so blitted at ([CROWN_X], [CROWN_Y]) it occupies
     * x -21..35 and y -82..-34: centre (7, -58), half extents 28 x 24. **The centre is not over
     * the trunk**, and that is the consequence of the lean that reaches furthest — the Christmas
     * lights, the falling-leaf source and the occlusion box all read it, and a crown centred on
     * the pivot would have put all three half a crown to the left of the leaves.
     */
    const val CROWN_CENTRE_X = 7f
    const val CROWN_CENTRE_Y = -58f
    const val CROWN_HALF_WIDTH = 28f
    const val CROWN_HALF_HEIGHT = 24f
}
