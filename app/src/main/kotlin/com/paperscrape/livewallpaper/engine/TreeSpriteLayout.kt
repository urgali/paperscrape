package com.paperscrape.livewallpaper.engine

/**
 * Where a tree's parts sit, stated once for both the things that draw a tree (v3.7 Filone C).
 *
 * ### Why this exists
 *
 * `ThemePreviewScenes` builds its objects out of the same sprites, at the same offsets, as
 * `SceneObjectRenderer` — by hand. Its own doc says so: the offsets are *copied from
 * `SceneObjectRenderer`'s own draw functions*. That duplication is deliberate and mostly harmless,
 * because the preview is a flat 320x240 data description with no perspective, no candidate system
 * and no scroll, and rendering it through the wallpaper's renderer would mean giving it all three.
 *
 * It is only harmless while the copies agree. **A v3.7 audit compared all 71 preview offsets
 * against the renderer's and found 59 shared sprites, 56 of them in exact agreement** once the
 * renderer's nested transforms are applied — and one that had drifted: the winter tree's snow cap,
 * which the preview drew 3 units right and 2 units down from where the wallpaper draws it. The
 * renderer's origin had been corrected at some point and the preview's copy had not moved with it.
 *
 * Rather than correct the copy and leave the next drift to chance, the tree's offsets are named
 * here and both callers read them. This is deliberately **only the tree**: it is the one place
 * drift was demonstrated, and hoisting all sixty-odd sprites would be a refactor of
 * `SceneObjectRenderer` that nothing has asked for and no evidence supports.
 *
 * ### The numbers were the renderer's, unchanged — until v4.21 redrew the tree
 *
 * From v3.7 to v4.20 every value below was what `drawTree` already blitted, so the wallpaper drew
 * exactly what it drew before and no golden was regenerated; what moved was only the preview.
 *
 * **v4.21 is the first release that moves them**, because the artwork itself was redrawn: the
 * "Quercia larga" replaces an octagon on a straight rod with a scalloped cushion on a stocky
 * forked trunk. Every offset here is re-derived from the new sprites' own content boxes, stated
 * below beside each one, and the goldens moved with them — attributed region by region rather
 * than regenerated on faith. The point of the object is unchanged and is now load-bearing in a
 * way it was not before: the preview and the renderer read *these* numbers, so a redraw moves
 * both or neither.
 */
internal object TreeSpriteLayout {

    /**
     * The trunk, blitted in the object's own space.
     *
     * v4.21: `tree_trunk` is 96x186 px = 32x62 u with content filling its canvas, so the blit at
     * (-16,-62) puts the foot on the ground line and the fork's shoulders at -62. The v4.20 rod
     * was 10x44 u at (-5,-44) — a third as wide and 18 units shorter.
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
     * v4.21: `tree_canopy` is 303x198 px = 101x66 u, content filling its canvas (the source's
     * viewBox starts at x=2 because that is where the drawing starts, so the canvas *is* the
     * content). Blitted here and lifted by [CANOPY_LIFT_Y] the crown occupies object x -50..51,
     * y -118..-52 — 8 units of overlap onto the fork's arms, which end at -60 and -62, so no sway
     * angle the tree reaches can open a seam between foliage and trunk.
     *
     * The crown top stays at -118, which is what keeps `SceneSpace.SceneVariant.TREE`'s declared
     * 118 units true across the redraw: the artwork got wider, not taller.
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
