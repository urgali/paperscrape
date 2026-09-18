package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What each building declares it is tall, against what it actually draws.
 *
 * ### What this used to check, and why the question changed shape
 *
 * `SceneSpace.SceneVariant` states a real height in metres and a drawn height in local units, and
 * every test that reasons about the scene's proportions divides one by the other. Until v5.0 a
 * building was one flat facade, so "what it draws" was a single number read off one blit, and this
 * test asserted the two were equal — which is how the tower was found declaring **196** units for
 * a building that measured **182**, reading 7.1 % short next to houses that read exactly what they
 * declared. That correction shipped in v4.30 and is pinned below, because it is still true.
 *
 * **v5.0 made "what it draws" a range.** A building is now a stack of pieces dealt per instance,
 * so a small house with a storey is taller than one without and a large house with a turret is
 * taller than one with a gable. That variation is the entire point of the redraw — the
 * maintainer's reference is a street where no two buildings share an outline — so a test demanding
 * one number would be demanding the redraw back out.
 *
 * What survives is the useful half: **`spriteUnitsTall` is the family's reference extent, and the
 * deals have to sit around it rather than somewhere else.** A family whose every deal is half its
 * declaration is not varying around it; it is a different building wearing the declaration of the
 * one it replaced, and every proportion drawn from that declaration — a person beside it, a car in
 * front of it — is wrong by the same factor.
 *
 * ### What that measures, on the set this ships
 *
 * Drawn extent is the topmost ink of the tallest piece in a deal, over every deal a family can
 * produce; snow is excluded, because a drift is weather and not building. Times the family's own
 * `spriteUnitsTall / unitsTall`, which is what the renderer scales by.
 *
 * | family | declares | draws | as metres |
 * |---|---|---|---|
 * | `HOUSE_SMALL` | 110 u = 5.76 m | 96.2 – 135.4 u | 5.04 – 7.09 m |
 * | `HOUSE_LARGE` | 145 u = 7.60 m | 136.9 – 207.0 u | 7.17 – 10.85 m |
 * | `TOWER` | 182 u = 15.60 m | 197.3 – 202.3 u | 16.91 – 17.34 m |
 * | `RESTAURANT` | **56 u = 4.78 m** | 56.0 u | 4.78 m |
 * | `BAR` | **74.5 u = 6.24 m** | 54.1 – 74.5 u | 4.53 – 6.24 m |
 *
 * ### v5.4 closed item 113, and the two shop rows above are what moved
 *
 * They read **96 u = 8.20 m** and **92 u = 7.70 m** from v5.0 to v5.3, and this file said in as
 * many words that the restaurant drew 58 % of that on every deal it had. The maintainer's ruling
 * was that the **declaration** is the wrong half: the drawing is what was chosen from the phase-4
 * photographs and ratified in phase 5, and the 96 described *"a shop front with a residential
 * storey over it"* which the redraw took away.
 *
 * All three numbers that described the old facade moved together and by the same factor -- the
 * variant's `metresTall` and `spriteUnitsTall`, and the family's own `unitsTall` -- so the
 * renderer's `metresTall / unitsTall`, which is the only quantity a blit's size depends on, is
 * **0.085417 m per piece unit before and after**. `all five families draw at the common metre`
 * pins that, and `no shop is drawn at a different size than it was in v5.3` pins the corollary.
 * Nothing in either building moved; what moved is nine shops on eight themes, because the
 * separation pass places a shop by its declared height.
 *
 * **What the table now says, and did not before:** all five families vary around their
 * declaration, which is what makes the declaration usable by everything that divides by it --
 * starting with the shop-front criterion, whose 40 % ceiling was being measured over a rectangle
 * 42 % of which was sky.
 *
 * **What it also says, and is nobody's to fix here:** the shops are shorter than the houses. See
 * `SceneContentV27Test` -- the v2.7 decision that a parade of shops must not read as outbuildings
 * behind the houses is not satisfied by the shipped artwork, and correcting the declaration is
 * what made that visible rather than what caused it.
 */
class BuildingHeightDeclarationTest {

    private fun family(variant: SceneSpace.SceneVariant) = NeighbourhoodTable.FAMILIES.getValue(variant)

    /**
     * Every deal a family can produce, as the drawn extent above the ground line in the variant's
     * own units.
     *
     * Enumerated rather than sampled: the deals are a product of the slots' options and repeat
     * counts, and every family's product is small enough to walk (4, 6, 2, 1 and 2 of them).
     */
    private fun drawnExtents(variant: SceneSpace.SceneVariant): List<Float> {
        val f = family(variant)
        val scale = variant.spriteUnitsTall / f.unitsTall
        var deals = listOf<List<Pair<BuildingPiece, Int>>>(emptyList())
        for (slot in f.slots) {
            val next = mutableListOf<List<Pair<BuildingPiece, Int>>>()
            for (prefix in deals) {
                for (option in slot.options) {
                    for (repeats in slot.repeatMin..slot.repeatMax) {
                        next += prefix + (option to repeats)
                    }
                }
            }
            deals = next
        }
        return deals.map { deal ->
            var foot = 0f
            var top = 0f
            for ((piece, repeats) in deal) {
                repeat(repeats) {
                    for (part in piece.parts) {
                        // `y` is the sprite's top edge; snow is weather, not building.
                        if (part.role != PartRole.SNOW && part.role != PartRole.LAMP &&
                            part.role != PartRole.OCCUPANTS
                        ) {
                            top = maxOf(top, -(foot + part.y))
                        }
                    }
                    foot -= piece.height
                }
            }
            top * scale
        }
    }

    @Test
    fun `every family varies around what it declares`() {
        // Within a quarter either way for the houses, whose slots add and drop a storey; the
        // tower's two deals differ only by a crown, so it barely varies at all. **The two shops
        // are in this list since v5.4** -- they used to have a method of their own recording that
        // they drew a little over half their declaration, which is the defect item 113 closed.
        // The restaurant has one deal and hits its declaration exactly; the bar declares its
        // taller deal, so its shorter one sits at 0.73.
        val bounds = mapOf(
            SceneSpace.SceneVariant.HOUSE_SMALL to (0.85f to 1.25f),
            SceneSpace.SceneVariant.HOUSE_LARGE to (0.90f to 1.45f),
            SceneSpace.SceneVariant.TOWER to (1.05f to 1.15f),
            SceneSpace.SceneVariant.RESTAURANT to (1.00f to 1.00f),
            SceneSpace.SceneVariant.BAR to (0.70f to 1.00f),
        )
        for ((variant, range) in bounds) {
            val ratios = drawnExtents(variant).map { it / variant.spriteUnitsTall }
            val (low, high) = range
            assertTrue(
                "$variant draws ${ratios.min()}..${ratios.max()} of its declaration, wanted $low..$high",
                ratios.min() >= low && ratios.max() <= high,
            )
        }
    }

    /**
     * What the two shops draw, to the unit, unchanged by v5.4's correction of what they declare.
     *
     * These three figures are the *drawing*, and the whole claim of item 113's repair is that the
     * drawing did not move: the same 56, 54.09 and 74.50 the shipped v5.3 produced, now equal to
     * or under a declaration that matches them instead of 42 % above.
     */
    @Test
    fun `the shops draw what they draw, and it did not move`() {
        val restaurant = drawnExtents(SceneSpace.SceneVariant.RESTAURANT)
        assertEquals("the restaurant has one deal", 1, restaurant.size)
        assertEquals(
            "the restaurant draws 56 units, and declares 56 since v5.4",
            56f, restaurant.single(), 0.01f,
        )
        val bar = drawnExtents(SceneSpace.SceneVariant.BAR)
        assertEquals("the bar has two figures", 2, bar.size)
        assertEquals("the bar's shorter figure", 54.09f, bar.min(), 0.01f)
        assertEquals("the bar's taller figure", 74.50f, bar.max(), 0.01f)
    }

    /**
     * **Correcting the two shops' declaration moved no pixel of either shop**, which is the one
     * thing item 113's repair had to be able to say.
     *
     * The renderer multiplies a piece unit by `SceneVariant.baseScale * (spriteUnitsTall /
     * unitsTall)`, and `baseScale` is `metresTall * pixelsPerMetre / spriteUnitsTall`, so the whole
     * product reduces to **`metresTall * pixelsPerMetre / unitsTall`** -- `spriteUnitsTall`
     * cancels. The v5.3 values are hard-coded here rather than derived, because a derivation from
     * the current table would pass however the table moves; these are the two numbers a v5.3
     * extraction produces.
     *
     * It is the same guard `correcting the tower moved no pixel` gives the tower, and it is here
     * for the same reason: the correction that came with it also changed a declaration by 42 %.
     */
    @Test
    fun `correcting the two shops moved no pixel of either`() {
        val shipped = mapOf(
            SceneSpace.SceneVariant.RESTAURANT to 8.2f / 96.0f,
            SceneSpace.SceneVariant.BAR to 7.7f / 90.146f,
        )
        for ((variant, metresPerPieceUnit) in shipped) {
            assertEquals(
                "$variant's metres per piece unit, which is what a blit is scaled by",
                metresPerPieceUnit,
                variant.metresTall / family(variant).unitsTall,
                0.0000005f,
            )
        }
        // And the other half of the product, so that a pair of compensating edits cannot pass:
        // the family's own ratio is what turns a piece unit into a variant unit.
        assertEquals(
            "the restaurant's variant units per piece unit",
            96f / 96.0f,
            SceneSpace.SceneVariant.RESTAURANT.spriteUnitsTall /
                family(SceneSpace.SceneVariant.RESTAURANT).unitsTall,
            0.000001f,
        )
        assertEquals(
            "the bar's variant units per piece unit",
            92f / 90.146f,
            SceneSpace.SceneVariant.BAR.spriteUnitsTall / family(SceneSpace.SceneVariant.BAR).unitsTall,
            0.000001f,
        )
    }

    /**
     * The correction v4.30 made to the tower, still standing.
     *
     * The scale a variant is drawn at is `metres * pixelsPerMetre / spriteUnitsTall`; 15.6/182 and
     * the old 16.8/196 are the same metres-per-unit, so the same 3.857 px per unit comes out. If a
     * later edit moves one of the two numbers without the other, this fails.
     */
    @Test
    fun `correcting the tower moved no pixel`() {
        assertEquals(
            "the tower's pixels-per-unit",
            3.857142f,
            SceneSpace.SceneVariant.TOWER.metresTall * SceneSpace.PIXELS_PER_METRE_AT_REFERENCE /
                SceneSpace.SceneVariant.TOWER.spriteUnitsTall,
            0.0001f,
        )
    }

    /**
     * Every family draws at the one metre the scene measures everything else in.
     *
     * This is what makes the table above readable at all: `unitsTall` is derived from the drawing's
     * own metre (`8.2 m / 96 u = 0.08542`), so a piece unit is the same length in all five
     * families and a door drawn 20 units tall is 1.71 m whichever building it is in. A family that
     * drifted off it would put its windows and doors out of scale with its own people, which is a
     * different and worse fault than being a short building.
     *
     * **The expression used to be written `metresTall / spriteUnitsTall * spriteUnitsTall /
     * unitsTall`, and v5.4's sweep of `BACKLOG_v5_1.md` item 117 is why it is not any more.**
     * Written that way it reads as the scene's scale times the family's, and so as though it
     * constrained `spriteUnitsTall` — which it does not: the factor cancels, and the test passes
     * unchanged with `RESTAURANT`'s 96 set to 56, the exact edit item 113 is about. Measured, not
     * argued: that mutation was run and this method stayed green while
     * `the two shops draw a little over half what they declare` went red at 32.67 against 56.
     *
     * The assertion was never wrong — `metresTall / unitsTall` *is* the common metre — so it is
     * written as that and nothing else. What guards `spriteUnitsTall` is elsewhere and is named
     * here so nobody looks for it in this method: the tower by
     * `correcting the tower moved no pixel`, the two shops by
     * `correcting the two shops moved no pixel of either` and by `every family varies around what
     * it declares`, and every variant's own scale by `SceneSpaceTest`.
     */
    @Test
    fun `all five families draw at the common metre`() {
        for (variant in listOf(
            SceneSpace.SceneVariant.HOUSE_SMALL, SceneSpace.SceneVariant.HOUSE_LARGE,
            SceneSpace.SceneVariant.TOWER, SceneSpace.SceneVariant.RESTAURANT,
            SceneSpace.SceneVariant.BAR,
        )) {
            val metrePerPieceUnit = variant.metresTall / family(variant).unitsTall
            assertEquals("$variant metres per piece unit", 0.085417f, metrePerPieceUnit, 0.00005f)
        }
    }
}
