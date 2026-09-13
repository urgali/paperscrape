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
 * | **`RESTAURANT`** | **96 u = 8.20 m** | **56.0 u** | **4.78 m** |
 * | **`BAR`** | **92 u = 7.70 m** | **54.1 – 74.5 u** | **4.53 – 6.24 m** |
 *
 * The three that vary, vary around their declaration. **The two shops do not**: the restaurant
 * draws 58 % of what it declares on every deal it has, and the bar between 59 % and 81 %. That is
 * not a bug in this code and it is not fixable here — the pieces are drawn at the common metre
 * (the restaurant's own door is 20 units, 1.71 m, which is a door), so the shops are simply
 * *shorter buildings* than the facades they replace: single-storey pavilions where the shipped
 * artwork drew two storeys. Scaling them up to their declaration would give the restaurant a
 * 2.9 m door.
 *
 * It is recorded here, with the numbers, rather than silently accepted or silently corrected:
 * `BACKLOG_v5_0.md` carries it as a question about the drawing, which is the maintainer's, and
 * the bounds below fail if either shop moves further from its declaration than it is today.
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
    fun `the houses and the tower vary around what they declare`() {
        // Within a quarter either way for the houses, whose slots add and drop a storey; the
        // tower's two deals differ only by a crown, so it barely varies at all.
        val bounds = mapOf(
            SceneSpace.SceneVariant.HOUSE_SMALL to (0.85f to 1.25f),
            SceneSpace.SceneVariant.HOUSE_LARGE to (0.90f to 1.45f),
            SceneSpace.SceneVariant.TOWER to (1.05f to 1.15f),
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
     * The two shops draw well under what they declare, and this pins how far under.
     *
     * A test that merely allowed it would be a test that stopped noticing. These are the measured
     * fractions of the shipped drawing; a redraw that brings a shop back towards its declaration
     * fails here and should, because the declaration is what has to move with it.
     */
    @Test
    fun `the two shops draw a little over half what they declare, and it is recorded`() {
        val restaurant = drawnExtents(SceneSpace.SceneVariant.RESTAURANT)
        assertEquals("the restaurant has one deal", 1, restaurant.size)
        assertEquals(
            "the restaurant draws 56 units of the 96 it declares",
            56f, restaurant.single(), 0.01f,
        )
        val bar = drawnExtents(SceneSpace.SceneVariant.BAR)
        assertEquals("the bar has two figures", 2, bar.size)
        assertEquals("the bar's shorter figure", 54.09f, bar.min(), 0.01f)
        assertEquals("the bar's taller figure", 74.50f, bar.max(), 0.01f)
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
     */
    @Test
    fun `all five families draw at the common metre`() {
        for (variant in listOf(
            SceneSpace.SceneVariant.HOUSE_SMALL, SceneSpace.SceneVariant.HOUSE_LARGE,
            SceneSpace.SceneVariant.TOWER, SceneSpace.SceneVariant.RESTAURANT,
            SceneSpace.SceneVariant.BAR,
        )) {
            val metrePerPieceUnit =
                variant.metresTall / variant.spriteUnitsTall * variant.spriteUnitsTall / family(variant).unitsTall
            assertEquals("$variant metres per piece unit", 0.085417f, metrePerPieceUnit, 0.00005f)
        }
    }
}
