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
 * ### The numbers are the renderer's, unchanged
 *
 * Every value below is what `drawTree` already blitted, so **the wallpaper draws exactly what it
 * drew before** — the goldens are the proof and none was regenerated. What moved is the preview.
 */
internal object TreeSpriteLayout {

    /** The trunk, blitted in the object's own space. */
    const val TRUNK_X = -5f
    const val TRUNK_Y = -44f

    /**
     * The lift `drawTree` applies (`canvas.translate(0f, -38f)`) before drawing the crown, so the
     * canopy and everything on it sway about the point where the leaves meet the trunk.
     *
     * The preview has no transform stack of its own, so it adds this into the offsets instead —
     * which is exactly the flattening that let the copies drift apart.
     */
    const val CANOPY_LIFT_Y = -38f

    /** The crown, in the lifted space. */
    const val CANOPY_X = -41f
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
