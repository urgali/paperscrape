package com.paperscrape.livewallpaper.engine

/**
 * Where the ink is, in every drawing an occlusion box has to speak for. **Generated** by
 * `tools/assets/build_occluder_table.py` from the shipped PNGs; edit that script, not this file.
 *
 * ### Why a generated table and not two literals
 *
 * An occlusion box says *nothing behind this rectangle can be seen*, and for a palm crown that was
 * the whole 56x48-unit canvas of a drawing which is 51% ink -- so the layout pass moved a shop out
 * from behind a fan it was plainly visible through (`BACKLOG_v5_1.md` item 124, and the audit's
 * photograph of the desert bar). The rectangle was written down twice, here in the catalogue's
 * [SceneObjectCatalog] geometry and again in `ShopFrontVisibilityTest`, the second copy deliberate
 * so that a wrong number has to be typed twice to hide a covered shop.
 *
 * Tightening one of the two turns the test red, correctly. Tightening both by hand would make the
 * test a comparison of two transcriptions instead of an independent measurement. So the number is
 * no longer typed on either side: it is **measured off the artwork** and both sides read it and
 * derive their own rectangle from it. What each side still writes for itself is the *model* -- how
 * a coverage becomes a smaller rectangle -- because that is a judgement and not a measurement, and
 * it is the half of the independence worth keeping.
 *
 * `SpriteOccluderTableFreshnessTest` re-measures every figure below straight from the PNG, in
 * Kotlin, without the generator -- so this table cannot fall behind a redraw in silence. That is
 * not a hypothetical: `tools/assets/reports/runtime-inventory.json` carried the pre-v5.1 palm for
 * the whole of v5.1 and nothing said so.
 *
 * ### The units
 *
 * [contentLeft], [contentTop], [contentRight], [contentBottom] are the drawing's own ink bounding
 * box in **object units at the blit origin** -- the canvas's transparent guard margin excluded,
 * which is the first thing the old box got wrong. [coverage] is the share of that box carrying
 * ink, counting a pixel as ink at any alpha above zero.
 */
internal object SpriteOccluderTable {

    /** One drawing's ink: its box in object units, and how much of that box it fills. */
    internal class InkBox(
        val contentLeft: Float,
        val contentTop: Float,
        val contentRight: Float,
        val contentBottom: Float,
        /**
         * The fullest single row, as a share of the box's width, and the fullest single column,
         * as a share of its height. **These two are the whole of the chosen model** and the
         * reason it is these two rather than the box's overall coverage is in
         * `SceneObjectCatalog.crownBoxes`. The overall coverage, the two-band profile and the
         * ink centroids were measured too and are in `tools/assets/reports/runtime-inventory.json`
         * for anyone re-opening the choice; they are not here because nothing reads them, and a
         * generated number nothing reads is a number nothing checks.
         */
        val rowMax: Float,
        val columnMax: Float,
    )

    /** The palm's fan, in its three drawings: live, Halloween's dead one, winter's frost. Stated at (PalmSpriteLayout.CROWN_X, PalmSpriteLayout.CROWN_Y). */
    val PALM_CROWN: List<InkBox> = listOf(
        // palmtree_fronds
        // coverage 0.5104 of the box, for the record; the model reads the two below
        InkBox(
            -18.333333f, -78.333333f, 34.000000f, -38.666667f,
            rowMax = 0.898089f, columnMax = 0.756303f,
        ),
        // palmtree_fronds_dead
        // coverage 0.4767 of the box, for the record; the model reads the two below
        InkBox(
            -12.333333f, -67.333333f, 29.000000f, -34.000000f,
            rowMax = 0.774194f, columnMax = 0.750000f,
        ),
        // palmtree_fronds_frost
        // coverage 0.5090 of the box, for the record; the model reads the two below
        InkBox(
            -18.333333f, -78.666667f, 34.333333f, -39.000000f,
            rowMax = 0.898734f, columnMax = 0.773109f,
        ),
    )

    /** The oak's crown, in its two drawings: the five-lobe canopy and Halloween's bare branches. Stated at (TreeSpriteLayout.FLAT_CANOPY_X, TreeSpriteLayout.FLAT_CANOPY_Y). */
    val TREE_CROWN: List<InkBox> = listOf(
        // tree_canopy
        // coverage 0.7512 of the box, for the record; the model reads the two below
        InkBox(
            -50.000000f, -118.000000f, 51.000000f, -52.000000f,
            rowMax = 1.000000f, columnMax = 1.000000f,
        ),
        // tree_dead_branches
        // coverage 0.2056 of the box, for the record; the model reads the two below
        InkBox(
            -39.000000f, -118.000000f, 43.666667f, -52.000000f,
            rowMax = 0.487903f, columnMax = 0.797980f,
        ),
    )

    /**
     * The parasol's fan. **Not a sprite**: `drawParasol` sweeps five 36-degree wedges of
     * radius 34 from 180 degrees, so it is a filled half-disc with no PNG and no
     * transparent pixel inside it. Measured the same way all the same -- rasterised from
     * that declared geometry and put through the identical profile code -- so the table
     * holds one rule and not two. The generator refuses to write this line unless the
     * rasterised figure agrees with the exact pi/4 a half-disc's area gives, which is the
     * check that the profile code itself is measuring what it claims to.
     */
    val PARASOL_FAN: List<InkBox> = listOf(
        // the wedge fan
        // coverage 0.7853 of the box, for the record; the model reads the two below
        InkBox(
            -34.000000f, -84.000000f, 34.000000f, -50.000000f,
            rowMax = 1.000000f, columnMax = 1.000000f,
        ),
    )
}
