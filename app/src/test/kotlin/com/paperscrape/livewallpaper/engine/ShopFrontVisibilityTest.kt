package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A shop front must be visible, whole, and one of a kind: the rc2 criterion as corrected by rc3,
 * across every built-in theme's generated layout.
 *
 * On the delivered rc1 Autumn frame a house covered nine tenths of the trattoria; the rc2 pass
 * fixed that but measured only the worst single occluder against the frontage's lower half, and
 * the delivered rc2 day frame answered with a pub numerically at 40% and visually cut in two by
 * a tree trunk planted over its door -- and with two identical trattorias in the same screen.
 * `SceneObjectCatalog.separateShopFrontages` now measures the union of everything nearer over
 * the shop's ENTIRE front, rejects any trunk or pole across it, and the catalogue emits at most
 * one shop per storefront per tile. This test re-measures all twelve built-in themes.
 *
 * The 40% ceiling is the acceptance criterion's own number. The measurement is at the reference
 * viewport (1080x2340), which is where every visual judgement in this project is made.
 *
 * Deliberately re-derived here rather than calling the catalogue's own private geometry -- and
 * deliberately grid-sampled where the catalogue sweeps exact rectangle unions -- so a bug in the
 * separation pass and a bug in this measurement have to agree to hide a covered shop.
 *
 * **v5.2 moved half of that re-derivation and says so here rather than leaving it to be found.**
 * The three crown rectangles used to be hand-typed literals in this file, a second copy of the
 * catalogue's. They are now derived from [SpriteOccluderTable], which is measured off the shipped
 * artwork, by arithmetic written for this file alone -- see `crowns`. The geometry is still
 * written twice; the *measurement* is written once, because it is a fact about a PNG and not a
 * choice, and because the copy that existed could not survive a redraw: v5.1 moved the palm's
 * crown canvas from 40x40 units to 56x48 by hand in two files and nothing would have failed if
 * only one of them had been edited.
 */
class ShopFrontVisibilityTest {

    private val themes = listOf(
        "sunset", "autumn", "winter", "desert", "christmas", "new_year",
        "beach", "city", "tundra", "easter", "halloween", "spring",
    )

    private val refW = 1080f
    private val refH = 2340f
    private val tile = refW * 2f

    @Test
    fun `no shop front is covered beyond forty percent of its whole area on any built-in theme`() {
        var shopsChecked = 0
        for (themeId in themes) {
            val objects = SceneObjectCatalog.layoutFor(themeId, 0xFF8899AA.toInt()).staticObjects
            for (shop in objects.filter { isShop(it) }) {
                shopsChecked++
                val coverage = sampledFrontCoverage(objects, shop)
                assertTrue(
                    "$themeId: the ${SceneObjectRenderer.variantFor(shop)} at x=${shop.tileFractionX}, " +
                        "depth=${shop.depthFraction} has ${"%.0f".format(coverage * 100)}% of its front covered",
                    coverage <= 0.40f,
                )
            }
        }
        assertTrue("expected a shop pair per theme, found $shopsChecked", shopsChecked >= 2 * themes.size)
    }

    @Test
    fun `no trunk or pole crosses any shop front on any built-in theme`() {
        for (themeId in themes) {
            val objects = SceneObjectCatalog.layoutFor(themeId, 0xFF8899AA.toInt()).staticObjects
            for (shop in objects.filter { isShop(it) }) {
                val f = frontRect(shop)
                val cx = (f[0] + f[2]) / 2f
                val crossers = objects
                    .filter { it !== shop && it.depthFraction > shop.depthFraction }
                    .mapNotNull { o -> verticalMemberBox(o)?.let { o to wrapBox(it, cx) } }
                    .filter { (_, b) -> b[2] > f[0] && b[0] < f[2] && b[3] > f[1] && b[1] < f[3] }
                assertTrue(
                    "$themeId: the ${SceneObjectRenderer.variantFor(shop)} at x=${shop.tileFractionX} has " +
                        crossers.joinToString { (o, _) ->
                            "a ${SceneObjectRenderer.variantFor(o)} member at x=${o.tileFractionX}"
                        } + " across its front",
                    crossers.isEmpty(),
                )
            }
        }
    }

    /**
     * The rc3 duplication criterion: two commercial buildings of the same storefront never share
     * a screen width. The wallpaper auto-scrolls through the whole tile, so this must hold for
     * EVERY window position, and an object is on screen for screenWidth + its own width of
     * scroll -- more than half the two-screen tile -- which is why the only layout that can pass
     * is one shop per storefront per tile. Both statements are asserted: the cardinality on the
     * catalogue's output, and the criterion itself by sweeping the window across the tile with
     * partial visibility counted.
     */
    @Test
    fun `no two shops of the same storefront ever share a screen width on any built-in theme`() {
        for (themeId in themes) {
            val shops = SceneObjectCatalog.layoutFor(themeId, 0xFF8899AA.toInt())
                .staticObjects.filter { isShop(it) }
            val byVariant = shops.groupBy { SceneObjectRenderer.variantFor(it) }
            for ((variant, list) in byVariant) {
                assertTrue("$themeId: ${list.size} ${variant}s in one tile", list.size <= 1)
            }
            assertEquals(
                "$themeId: the street should offer both storefronts",
                setOf(SceneSpace.SceneVariant.RESTAURANT, SceneSpace.SceneVariant.BAR),
                byVariant.keys,
            )
            // The criterion as stated, swept: at every 4-px window start, the shops of one
            // storefront visible in [t, t + screenW] -- partially counts -- number at most one.
            var t = 0f
            while (t < tile) {
                for ((variant, list) in byVariant) {
                    val visible = list.count { shop ->
                        val half = halfWidthPx(shop)
                        val x = shop.tileFractionX * tile
                        val rawDx = (x - (t + refW / 2f)).mod(tile)
                        val dx = if (rawDx > tile / 2f) rawDx - tile else rawDx
                        kotlin.math.abs(dx) < refW / 2f + half
                    }
                    assertTrue("$themeId: $visible ${variant}s visible at window $t", visible <= 1)
                }
                t += 4f
            }
        }
    }

    // ---- independent geometry --------------------------------------------------------------

    private fun isShop(o: StaticSceneObject) =
        o.type == SceneObjectType.SKYSCRAPER && o.depthFraction >= SceneSpace.BUILDING_TOWER_MAX_DEPTH

    private fun halfWidthPx(o: StaticSceneObject): Float {
        val units = when (SceneObjectRenderer.variantFor(o)) {
            SceneSpace.SceneVariant.HOUSE_SMALL -> 48f
            SceneSpace.SceneVariant.HOUSE_LARGE -> 75f
            SceneSpace.SceneVariant.RESTAURANT, SceneSpace.SceneVariant.BAR -> 34f
            SceneSpace.SceneVariant.TOWER -> 45f
            // v4.21: re-measured off `tree_canopy` at its blit origin -- 101 units of content at
            // -50 spans x -50..51. Read from the artwork here as it is read from the artwork in
            // the catalogue, and deliberately not imported from it: the whole point of this file
            // is that a wrong number has to be typed twice to hide a covered shop.
            SceneSpace.SceneVariant.TREE -> 51f
            SceneSpace.SceneVariant.PALM_TREE -> 28f
            SceneSpace.SceneVariant.PARASOL -> 34f
            else -> 0f
        }
        return units * SceneObjectRenderer.effectiveScaleFor(o, refH)
    }

    private fun frontRect(shop: StaticSceneObject): FloatArray {
        val s = SceneObjectRenderer.effectiveScaleFor(shop, refH)
        val g = refH * SceneSpace.groundYFraction(shop.depthFraction)
        val x = shop.tileFractionX * tile
        return floatArrayOf(
            x - halfWidthPx(shop),
            g - SceneObjectRenderer.variantFor(shop).spriteUnitsTall * s,
            x + halfWidthPx(shop),
            g,
        )
    }

    /** Everything one nearer object puts in front of a shop: bodies, crowns, trunks, canopies,
     * poles -- the whole silhouette, boxed. Same artwork measurements as the renderer's blits. */
    private fun occluderBoxes(o: StaticSceneObject): List<FloatArray> {
        val v = SceneObjectRenderer.variantFor(o)
        val s = SceneObjectRenderer.effectiveScaleFor(o, refH)
        val g = refH * SceneSpace.groundYFraction(o.depthFraction)
        val x = o.tileFractionX * tile
        return when (v) {
            // v4.21 "Quercia larga": stem 32x62 u at (-16,-62). The crown is no longer a literal
            // here -- see `crowns` below.
            SceneSpace.SceneVariant.TREE ->
                crowns(SpriteOccluderTable.TREE_CROWN, x, g, s) +
                    floatArrayOf(x - 16f * s, g - 62f * s, x + 16f * s, g)
            SceneSpace.SceneVariant.PALM_TREE ->
                crowns(SpriteOccluderTable.PALM_CROWN, x, g, s) +
                    floatArrayOf(x - 8f * s, g - 58f * s, x + 13f * s, g)
            SceneSpace.SceneVariant.PARASOL ->
                crowns(SpriteOccluderTable.PARASOL_FAN, x, g, s) +
                    floatArrayOf(x - 2.5f * s, g - 50f * s, x + 2.5f * s, g)
            SceneSpace.SceneVariant.HOUSE_SMALL, SceneSpace.SceneVariant.HOUSE_LARGE,
            SceneSpace.SceneVariant.RESTAURANT, SceneSpace.SceneVariant.BAR,
            SceneSpace.SceneVariant.TOWER,
            -> listOf(floatArrayOf(x - halfWidthPx(o), g - v.spriteUnitsTall * s, x + halfWidthPx(o), g))
            else -> emptyList()
        }
    }

    /**
     * The crown rectangles, re-derived here, and this is where the independence of this file
     * moved to in v5.2 rather than where it was lost.
     *
     * Until v5.2 the two sides of item 124 were two hand-typed copies of the same literal
     * rectangles, and the reason this file kept its own was stated above: a wrong number has to
     * be typed twice to hide a covered shop. That defence only ever caught a typo, and the defect
     * item 124 is about was not a typo -- it was a *correct* transcription of a canvas that was
     * two thirds air, typed identically on both sides and wrong on both. It also could not
     * survive v5.1's palm redraw, which moved the crown's canvas from 40x40 units to 56x48 in two
     * files by hand.
     *
     * So the number is no longer typed on either side. It is measured off the shipped PNG by
     * `tools/assets/build_occluder_table.py` into [SpriteOccluderTable], and what each side still
     * writes for itself is the **model** -- the arithmetic below, which is deliberately not the
     * catalogue's `crownBoxes` imported, and deliberately not shaped like it either: the
     * catalogue centres a half-extent, this multiplies the edges. A bug in the model still has to
     * be made twice.
     *
     * What that leaves unguarded, said plainly: if the *model itself* is the wrong idea, both
     * sides are wrong together, and no arrangement of two copies would have caught that. What
     * guards it instead is that the model's inputs are a measurement rather than a choice, that
     * `SpriteOccluderTableFreshnessTest` re-measures every one of them straight from the PNG
     * without the generator, and that the frame was looked at (`V5_2A_REPORT.md` section 6).
     */
    private fun crowns(
        family: List<SpriteOccluderTable.InkBox>,
        x: Float,
        g: Float,
        s: Float,
    ): List<FloatArray> = family.map { ink ->
        // The widest row and the tallest column, held about the content's own centre. Written as
        // a shrink of each edge towards the centre rather than as a half-extent about it.
        val shrinkX = (ink.contentRight - ink.contentLeft) * (1f - ink.rowMax) / 2f
        val shrinkY = (ink.contentBottom - ink.contentTop) * (1f - ink.columnMax) / 2f
        floatArrayOf(
            x + (ink.contentLeft + shrinkX) * s,
            g + (ink.contentTop + shrinkY) * s,
            x + (ink.contentRight - shrinkX) * s,
            g + (ink.contentBottom - shrinkY) * s,
        )
    }

    private fun verticalMemberBox(o: StaticSceneObject): FloatArray? {
        val v = SceneObjectRenderer.variantFor(o)
        val s = SceneObjectRenderer.effectiveScaleFor(o, refH)
        val g = refH * SceneSpace.groundYFraction(o.depthFraction)
        val x = o.tileFractionX * tile
        return when (v) {
            SceneSpace.SceneVariant.TREE -> floatArrayOf(x - 16f * s, g - 62f * s, x + 16f * s, g)
            SceneSpace.SceneVariant.PALM_TREE -> floatArrayOf(x - 8f * s, g - 58f * s, x + 13f * s, g)
            SceneSpace.SceneVariant.PARASOL -> floatArrayOf(x - 2.5f * s, g - 50f * s, x + 2.5f * s, g)
            else -> null
        }
    }

    private fun wrapBox(b: FloatArray, cx: Float): FloatArray {
        val boxCx = (b[0] + b[2]) / 2f
        val rawDx = (boxCx - cx).mod(tile)
        val dx = if (rawDx > tile / 2f) rawDx - tile else rawDx
        val shift = (cx + dx) - boxCx
        return floatArrayOf(b[0] + shift, b[1], b[2] + shift, b[3])
    }

    /** Union coverage of the whole front, sampled on a 160x160 grid of the front rectangle. */
    private fun sampledFrontCoverage(objects: List<StaticSceneObject>, shop: StaticSceneObject): Float {
        val f = frontRect(shop)
        val cx = (f[0] + f[2]) / 2f
        val boxes = objects
            .filter { it !== shop && it.depthFraction > shop.depthFraction }
            .flatMap { occluderBoxes(it) }
            .map { wrapBox(it, cx) }
            .filter { it[2] > f[0] && it[0] < f[2] && it[3] > f[1] && it[1] < f[3] }
        if (boxes.isEmpty()) return 0f
        val n = 160
        var covered = 0
        for (iy in 0 until n) {
            val y = f[1] + (iy + 0.5f) / n * (f[3] - f[1])
            for (ix in 0 until n) {
                val x = f[0] + (ix + 0.5f) / n * (f[2] - f[0])
                if (boxes.any { x >= it[0] && x <= it[2] && y >= it[1] && y <= it[3] }) covered++
            }
        }
        return covered.toFloat() / (n * n)
    }
}
