package com.paperscrape.livewallpaper.engine

/**
 * Where a tree's parts sit, stated once for both the things that draw a tree.
 *
 * ### Why this exists
 *
 * `ThemePreviewScenes` builds its objects out of the same sprites, at the same offsets, as
 * `SceneObjectRenderer` — by hand where it has no constant to read. Its own doc says so: the
 * offsets *come from `SceneObjectRenderer`'s own draw functions -- read from its constants where it
 * has them, copied where it does not*. That duplication is deliberate and mostly harmless,
 * because the preview is a flat 320x240 data description with no perspective, no candidate system
 * and no scroll, and rendering it through the wallpaper's renderer would mean giving it all three.
 *
 * It is only harmless while the copies agree, and a copy drifts when the renderer's origin moves and
 * the preview's does not: a winter tree's snow cap drawn a few units off its crown on the card. So
 * the tree's offsets are named here and both callers read them, as the palm's are in
 * [PalmSpriteLayout]; the other shared sprites, whose literals agree on both sides, stay literals.
 *
 * ### The numbers
 *
 * Every offset is derived from the sprites' own content boxes, stated below beside each one. The
 * preview and the renderer read *these* numbers, so a redraw of the tree moves both or neither.
 */
internal object TreeSpriteLayout {

    /**
     * The trunk, blitted in the object's own space.
     *
     * `tree_trunk` is 96x186 px = 32x62 u with content filling its canvas, so the blit at
     * (-16,-62) puts the foot on the ground line and the fork's shoulders at -62.
     */
    const val TRUNK_X = -16f
    const val TRUNK_Y = -62f

    /**
     * The lift `drawTree` applies (`canvas.translate(0f, -38f)`) before drawing the crown, so the
     * canopy and everything on it sway about the point where the leaves meet the trunk.
     *
     * The preview has no transform stack of its own, so it adds this into the offsets instead —
     * which is exactly the flattening that let the copies drift apart.
     */
    const val CANOPY_LIFT_Y = -38f

    /**
     * The crown, in the lifted space.
     *
     * `tree_canopy` is 303x198 px = 101x66 u, content filling its canvas (the source's
     * viewBox starts at x=2 because that is where the drawing starts, so the canvas *is* the
     * content). Blitted here and lifted by [CANOPY_LIFT_Y] the crown occupies object x -50..51,
     * y -118..-52 — 8 units of overlap onto the fork's arms, which end at -60 and -62, so no sway
     * angle the tree reaches can open a seam between foliage and trunk.
     *
     * The crown top stays at -118, which is what keeps `SceneSpace.SceneVariant.TREE`'s declared
     * 118 units true.
     */
    const val CANOPY_X = -50f
    const val CANOPY_Y = -80f

    /**
     * The snow cap and the bare Halloween branches, both in the lifted space and both sharing the
     * crown's origin so they cannot slide against it.
     */
    const val SNOWCAP_X = CANOPY_X
    const val SNOWCAP_Y = CANOPY_Y
    const val DEAD_BRANCHES_X = CANOPY_X
    const val DEAD_BRANCHES_Y = CANOPY_Y

    /** The crown's offsets as the preview needs them: the lift already folded in. */
    const val FLAT_CANOPY_X = CANOPY_X
    const val FLAT_CANOPY_Y = CANOPY_Y + CANOPY_LIFT_Y
    const val FLAT_SNOWCAP_X = SNOWCAP_X
    const val FLAT_SNOWCAP_Y = SNOWCAP_Y + CANOPY_LIFT_Y
    const val FLAT_DEAD_BRANCHES_X = DEAD_BRANCHES_X
    const val FLAT_DEAD_BRANCHES_Y = DEAD_BRANCHES_Y + CANOPY_LIFT_Y
}
