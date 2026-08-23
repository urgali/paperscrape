package com.paperscrape.livewallpaper.engine

/**
 * Where a skyscraper's parts sit, stated once for both the things that draw one (**v3.8 Filone 4**).
 *
 * ### Why this group, and only this group
 *
 * v3.7 gave the tree the same treatment after finding one drifted offset, and left open whether to
 * extend it. v3.8 audited every shared sprite to decide, and the answer is narrow:
 *
 * **55 drawables are used by both the preview and the renderer, and 55 agree exactly.** They are
 * plain literals on both sides — `house_small_roof_snow` at `(-34, -114)`, `bar_sign` at
 * `(-12, -84)`, and so on — with no transform to fold and no arithmetic to get wrong. Hoisting them
 * into shared constants would add a layer of indirection that guards against nothing, which is the
 * artificial unification this work was told not to do.
 *
 * **The skyscraper is the exception, twice over**, and it is the only one:
 *
 *  1. **A folded expression.** The renderer places the roof snow at
 *     `-height - 32f + 6f - 8f + 3f`, spelling out where the setback's own block starts and how far
 *     the cap reaches above the roofline it is cut for. The preview carried the *sum*, `-height - 31f`.
 *     Numerically equal today; silently wrong the moment anyone edits one of those four terms. This
 *     is exactly the shape of the tree's drift.
 *  2. **A real divergence.** The lit night facade sat at `(-39, -height + 6)` in the preview against
 *     `(-width/2, -height)` in the wallpaper — six units right and six down. `drawSkyscraperBuilding`
 *     states the intent in as many words: the night grid is *"laid over it at the same origin"*, and
 *     the Christmas window-light grid beside it confirms the arithmetic, hanging its lights at
 *     `-width/2 + 5` where the lit sprite's own content begins 5 units into its canvas. The
 *     renderer is right and the preview was the copy that drifted.
 *
 * These are the only two offsets in `SceneObjectRenderer` stated as arithmetic rather than as a
 * literal, which is not a coincidence: an expression is what a copy flattens, and a flattened copy
 * is what stops tracking the original.
 *
 * ### The numbers are the renderer's, unchanged
 *
 * Every value below is what `drawSkyscraperBuilding` already blitted, so **the wallpaper draws
 * exactly what it drew before** — the goldens are the proof and none was regenerated for this. What
 * moved is the preview's lit facade.
 *
 * ### Why the vertical offsets are relative
 *
 * The renderer draws one tower height (150 units); the preview draws several, because a gallery card
 * needs a skyline rather than a row of identical blocks. So the shared values are the **deltas from
 * the tower's own top**, which is what both sides actually mean, rather than absolute Y positions,
 * which would only be true for one of them.
 */
internal object SkyscraperSpriteLayout {

    /** The renderer's tower. The preview varies its own height and shares only the deltas below. */
    const val WIDTH = 90f
    const val HEIGHT = 150f

    /** The plinth at street level, straddling the ground line. Absolute: it does not follow height. */
    const val CANOPY_X = -55f
    const val CANOPY_Y = -6f

    /** The facade, centred on the tower. */
    const val WALL_X = -WIDTH / 2f

    /**
     * The night facade, at the **same origin as the wall** — the whole point of the drawing, and the
     * thing the preview had wrong.
     */
    const val WALL_LIT_X = WALL_X
    const val WALL_LIT_DY = 0f

    /** The entrance, on the ground rather than on the tower. Absolute. */
    const val ENTRANCE_X = -16f
    const val ENTRANCE_Y = -32f

    /** The setback, above the tower's top. */
    const val SETBACK_X = -30f
    const val SETBACK_DY = -32f

    /**
     * The snow on the setback's roof, the only horizontal surface of a tower a viewer sees.
     *
     * Kept as the sum of its parts rather than as `-31f`: the setback's own block starts 6 units
     * down its canvas, and the cap carries 8 units above the roofline it is cut for, and 3 more
     * seats it. Someone changing the setback has to see those terms.
     */
    const val ROOF_SNOW_X = -28f
    const val ROOF_SNOW_DY = SETBACK_DY + 6f - 8f + 3f
}
