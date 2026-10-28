package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A shop front must be visible and whole, across every built-in theme's generated layout: no more
 * than 40 % of its ENTIRE front covered by the union of everything nearer, and no trunk or pole
 * across it -- a front can read 40 % covered and still be cut in two by a trunk over its door.
 * `SceneObjectCatalog.separateShopFrontages` places the shops by that rule; this test re-measures
 * all twelve built-in themes.
 *
 * The measurement is at the reference viewport (1080x2340), which is where every visual judgement
 * in this project is made.
 *
 * Deliberately re-derived here rather than calling the catalogue's own private geometry -- and
 * deliberately grid-sampled where the catalogue sweeps exact rectangle unions -- so a bug in the
 * separation pass and a bug in this measurement have to agree to hide a covered shop. The crown
 * rectangles are the one input measured once: they come from [SpriteOccluderTable], measured off
 * the shipped artwork, through arithmetic written for this file alone (`crowns`), because a crown's
 * extent is a fact about a PNG and not a choice, and a hand-typed copy in two files would not
 * survive a redraw that moves a crown's canvas.
 *
 * **A place among the houses holds any of six buildings**, dealt by the street as it scrolls
 * ([StorefrontStreet]), and this file measures each place with the family of its row of the size
 * table: the restaurant in the two far places, the school in the middle one, the bar in the two near
 * ones. `StorefrontFrontVisibilityTest` measures all six in all five places (the plan is laid out
 * for all six, `SceneObjectCatalog.frontRects`), and `StorefrontStreetTest` holds that no two alike
 * stand on screen.
 */
class ShopFrontVisibilityTest {

    private val themes = listOf(
        "sunset", "autumn", "winter", "desert", "christmas", "new_year",
        "beach", "city", "tundra", "easter", "halloween", "spring",
    )

    private val refW = 1080f
    private val refH = 2340f
    private val tile = refW * 2f

    /**
     * The worst ink-measured shop-front coverage over the twelve built-in layouts: **38.2 %,
     * `easter`'s school**, on five places a street planned along the whole tile and each house as
     * tall as it draws by the renderer's deal -- inside the criterion, with 1.8 points of margin.
     *
     * A drift tripwire, and one that fires on a layout change is doing its job: which slots hold
     * which house, where the plan stands a place and how wide a front draws all move it while the
     * criterion stays where it is. The change is attributed shop by shop and the number re-pinned,
     * so the reading is a consequence of the layout rather than a shop getting quietly more covered.
     */
    private val INK_WORST = 0.3816f

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

    /**
     * **The count guard is not decoration.**
     *
     * Every assertion in this method lives inside `for (shop in objects.filter { isShop(it) })`.
     * If [isShop] stopped selecting anything -- it reads
     * `SceneSpace.BUILDING_TOWER_MAX_DEPTH`, a constant another file owns and may move -- the loop
     * would run zero times and this test would report success over nothing: a check run where its
     * condition is true by construction.
     * The sibling coverage test above has its own guard, `shopsChecked >= 2 * themes.size`, and a
     * guard in a neighbouring method guards nothing.
     */
    @Test
    fun `no trunk or pole crosses any shop front on any built-in theme`() {
        var shopsChecked = 0
        for (themeId in themes) {
            val objects = SceneObjectCatalog.layoutFor(themeId, 0xFF8899AA.toInt()).staticObjects
            for (shop in objects.filter { isShop(it) }) {
                shopsChecked++
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
        assertEquals("this test asserted over no shop at all", 5 * themes.size, shopsChecked)
    }

    /**
     * The school a saved street is given obeys the frontage rules, and never
     * stands in front of the shops the street already had.
     *
     * Measured with this class's own model rather than the one that placed it, which is the point
     * of the model being written twice. The streets are the twelve built-ins as saved at their
     * defaults (`keepCandidate`-filtered, which is what a saved theme stores) with their school
     * taken out -- four places, two in the restaurant's row and two in the bar's, and no school; a
     * theme saved before v5.6 has one of each of those two rows and no school.
     */
    @Test
    fun `a school added to a saved street clears its own front and leaves the saved shops as they were`() {
        var added = 0
        for (themeId in themes) {
            val c = defaultCustomizationFor(themeId)
            val street = SceneObjectCatalog.layoutFor(themeId, 0xFF8899AA.toInt()).staticObjects
                .filter { c.keepCandidate(it) }
                .filterNot { it.type == SceneObjectType.SKYSCRAPER && SceneObjectRenderer.variantFor(it) == SceneSpace.SceneVariant.SCHOOL }
            val school = SceneObjectCatalog.missingSchoolFor(street)
                ?: throw AssertionError("$themeId: a street with no school was given none")
            added++
            assertEquals(SceneSpace.SceneVariant.SCHOOL, SceneObjectRenderer.variantFor(school))
            val after = street + school
            val covered = sampledFrontCoverage(after, school)
            assertTrue("$themeId: the added school has ${"%.0f".format(covered * 100)}% of its front covered", covered <= 0.40f)
            val f = frontRect(school)
            val cx = (f[0] + f[2]) / 2f
            val crossers = street.filter { it.depthFraction > school.depthFraction }
                .mapNotNull { verticalMemberBox(it)?.let { b -> wrapBox(b, cx) } }
                .filter { b -> b[2] > f[0] && b[0] < f[2] && b[3] > f[1] && b[1] < f[3] }
            assertTrue("$themeId: a trunk or pole crosses the added school's front", crossers.isEmpty())
            for (shop in street.filter { isShop(it) }) {
                assertEquals(
                    "$themeId: the saved ${SceneObjectRenderer.variantFor(shop)} is more covered once the school stands",
                    sampledFrontCoverage(street, shop), sampledFrontCoverage(after, shop), 0f,
                )
            }
        }
        assertEquals("this test asserted over no street at all", themes.size, added)
    }

    /**
     * The same criterion, measured where the shop's ink actually stops.
     *
     * The 40 % ceiling above divides by [frontRect], whose top edge is the declared
     * `spriteUnitsTall`. A declaration taller than the drawing puts empty sky in the rectangle, and
     * a crown covering most of the actual frontage is divided by an area part of which no shop is
     * drawn in, and reads as passing: a criterion is only as good as the rectangle it divides by.
     * So this asserts `<= 0.40` again, on the measurement that is what a viewer sees.
     *
     * It is measured **per instance**, over the drawn extent of that instance's own deal, with the
     * neighbouring buildings occluding at their drawn extent too -- both sides together. The
     * declaration is a family's, and the bar has two deals; measuring on ink is what keeps the
     * shorter of them honest.
     */
    @Test
    fun `measured where the ink stops, every shop front is inside the forty percent criterion`() {
        var shopsChecked = 0
        var worst = 0f
        var worstShop = ""
        val overCeiling = sortedSetOf<String>()
        for (themeId in themes) {
            val objects = SceneObjectCatalog.layoutFor(themeId, 0xFF8899AA.toInt()).staticObjects
            for (shop in objects.filter { isShop(it) }) {
                shopsChecked++
                val coverage = sampledFrontCoverage(objects, shop, onInk = true)
                if (coverage > worst) {
                    worst = coverage
                    worstShop = "$themeId/${SceneObjectRenderer.variantFor(shop)}"
                }
                if (coverage > 0.40f) {
                    overCeiling += "$themeId/${SceneObjectRenderer.variantFor(shop)} at " +
                        "${"%.1f".format(coverage * 100)}%"
                }
            }
        }
        assertEquals("this test asserted over no shop at all", 5 * themes.size, shopsChecked)
        assertTrue(
            "measured on ink, these shop fronts are over the criterion: $overCeiling",
            overCeiling.isEmpty(),
        )
        // The worst reading, named, so that a layout drifting towards the ceiling shows up as a
        // changed number here before it shows up as a failure.
        assertEquals("the worst ink-measured coverage is $worstShop", INK_WORST, worst, 0.005f)
    }

    /**
     * **Palms in the trees' places keep every front clear.** The ten streets without palms of their
     * own are laid out around broadleaf trees -- the shop plan runs on the layout, before any
     * switch -- and the Palms switch can turn every one of those trees into a palm, where it stands
     * (`palmSpeciesApplied`). So the fronts are measured again with the palms in place, by both of
     * this class's rules and on ink: no front over the ceiling, no trunk across a front. The worst
     * reading is printed.
     */
    @Test
    fun `with palms in place of the trees, every shop front still keeps both rules`() {
        val palms = SceneCustomization.DEFAULT.copy(palmsInsteadOfTrees = true)
        var shopsChecked = 0
        var worst = 0f
        var worstShop = ""
        val failures = mutableListOf<String>()
        for (themeId in themes) {
            val layout = SceneObjectCatalog.layoutFor(themeId, 0xFF8899AA.toInt())
            if (layout.hasPalmSlots()) continue
            val objects = layout.staticObjects.map { palms.palmSpeciesApplied(it, layoutPlantsPalms = false) }
            assertTrue("$themeId: the switch planted no palm", objects.any { it.type == SceneObjectType.PALM_TREE })
            for (shop in objects.filter { isShop(it) }) {
                shopsChecked++
                val name = "$themeId/${SceneObjectRenderer.variantFor(shop)}"
                for (onInk in listOf(false, true)) {
                    val coverage = sampledFrontCoverage(objects, shop, onInk = onInk)
                    if (onInk && coverage > worst) { worst = coverage; worstShop = name }
                    if (coverage > 0.40f) failures += "$name ${if (onInk) "on ink" else "declared"} ${"%.1f".format(coverage * 100)}%"
                }
                val f = frontRect(shop)
                val cx = (f[0] + f[2]) / 2f
                val crossers = objects
                    .filter { it !== shop && it.depthFraction > shop.depthFraction }
                    .mapNotNull { o -> verticalMemberBox(o)?.let { o to wrapBox(it, cx) } }
                    .filter { (_, b) -> b[2] > f[0] && b[0] < f[2] && b[3] > f[1] && b[1] < f[3] }
                if (crossers.isNotEmpty()) {
                    failures += "$name crossed by " + crossers.joinToString { (o, _) -> "a ${SceneObjectRenderer.variantFor(o)} at x=${o.tileFractionX}" }
                }
            }
        }
        println("PALMS_SHOP_FRONTS worst on ink with palms: ${"%.3f".format(worst)} at $worstShop, $shopsChecked shops")
        assertEquals("this test asserted over no shop at all", 5 * 10, shopsChecked)
        assertTrue("with palms in place of the trees: $failures", failures.isEmpty())
    }

    /**
     * The drawn extent of one building instance's own deal, in the variant's units.
     *
     * The deal is the renderer's -- [NeighbourhoodComposer.deal] of the object, which reads the silhouette
     * the street dealt it ([SilhouetteDeal]) -- so this is the building that will actually be blitted at
     * this spot, not a family average. The deal by position is what only an undealt object draws: read
     * here, a house dealt a storey more would stand a storey lower than it draws. Snow, porch lamps and
     * window occupants are excluded on the same reading `BuildingHeightDeclarationTest` uses: a drift is
     * weather, not building.
     */
    private fun drawnUnits(o: StaticSceneObject): Float {
        val v = SceneObjectRenderer.variantFor(o)
        val f = NeighbourhoodTable.FAMILIES[v] ?: return v.spriteUnitsTall
        val deal = NeighbourhoodComposer.Deal()
        NeighbourhoodComposer.deal(f, o, deal)
        var top = 0f
        for (i in 0 until deal.size) {
            val placed = deal[i]
            for (part in placed.piece.parts) {
                if (part.role != PartRole.SNOW && part.role != PartRole.LAMP &&
                    part.role != PartRole.OCCUPANTS
                ) {
                    top = maxOf(top, -(placed.baseY + part.y))
                }
            }
        }
        return top * (v.spriteUnitsTall / f.unitsTall)
    }

    // ---- independent geometry --------------------------------------------------------------

    private fun isShop(o: StaticSceneObject) =
        o.type == SceneObjectType.SKYSCRAPER && o.depthFraction >= SceneSpace.BUILDING_TOWER_MAX_DEPTH

    private fun halfWidthPx(o: StaticSceneObject): Float {
        val v = SceneObjectRenderer.variantFor(o)
        val units = when (v) {
            // The six buildings are measured off their PNGs here, not typed: two typed copies of a
            // wrong width agree with each other, and a covered front passes on both sides. The
            // catalogue keeps literals; this measures the ink.
            SceneSpace.SceneVariant.HOUSE_SMALL, SceneSpace.SceneVariant.HOUSE_LARGE,
            SceneSpace.SceneVariant.RESTAURANT, SceneSpace.SceneVariant.BAR,
            SceneSpace.SceneVariant.TOWER, SceneSpace.SceneVariant.SCHOOL,
            -> inkHalfWidth(v)
            // `tree_canopy` at its blit origin: 101 units of content at -50 spans x -50..51. Read
            // from the artwork here as it is read from the artwork in the catalogue, and
            // deliberately not imported from it: the whole point of this file is that a wrong
            // number has to be typed twice to hide a covered shop.
            SceneSpace.SceneVariant.TREE -> 51f
            SceneSpace.SceneVariant.PALM_TREE -> 28f
            SceneSpace.SceneVariant.PARASOL -> 34f
            else -> 0f
        }
        return units * SceneObjectRenderer.effectiveScaleFor(o, refH)
    }

    /**
     * The wider side of the ink a building family draws, in its variant's units: every part of every
     * alternative of every slot (snow, lamps and window busts excluded, as for the height), its PNG's
     * alpha bounding box at 3 px a piece unit, put through the part's own x, then scaled by
     * `spriteUnitsTall / unitsTall` as the renderer scales a piece.
     */
    private fun inkHalfWidth(v: SceneSpace.SceneVariant): Float = INK_HALF.getOrPut(v) {
        val f = NeighbourhoodTable.FAMILIES.getValue(v)
        var left = 0f
        var right = 0f
        for (slot in f.slots) for (piece in slot.options + slot.heights) for (part in piece.parts) {
            if (part.res == 0 || part.role == PartRole.SNOW || part.role == PartRole.LAMP || part.role == PartRole.OCCUPANTS) continue
            val (l, r) = inkColumns(part.res) ?: continue
            left = minOf(left, part.x + l / 3f)
            right = maxOf(right, part.x + r / 3f)
        }
        maxOf(-left, right) * (v.spriteUnitsTall / f.unitsTall)
    }

    private fun frontRect(shop: StaticSceneObject, onInk: Boolean = false): FloatArray {
        val s = SceneObjectRenderer.effectiveScaleFor(shop, refH)
        val g = refH * SceneSpace.groundYFraction(shop.depthFraction)
        val x = shop.tileFractionX * tile
        val tall = if (onInk) drawnUnits(shop) else SceneObjectRenderer.variantFor(shop).spriteUnitsTall
        return floatArrayOf(x - halfWidthPx(shop), g - tall * s, x + halfWidthPx(shop), g)
    }

    /** Everything one nearer object puts in front of a shop: bodies, crowns, trunks, canopies,
     * poles -- the whole silhouette, boxed. Same artwork measurements as the renderer's blits. A place
     * among the houses is boxed as the building of its row, the school's row included, or a nearer place
     * of that row would cover nothing here; the envelope of all six buildings in every place is
     * `StorefrontFrontVisibilityTest`'s. */
    private fun occluderBoxes(o: StaticSceneObject, onInk: Boolean = false): List<FloatArray> {
        val v = SceneObjectRenderer.variantFor(o)
        val s = SceneObjectRenderer.effectiveScaleFor(o, refH)
        val g = refH * SceneSpace.groundYFraction(o.depthFraction)
        val x = o.tileFractionX * tile
        return when (v) {
            // The oak's stem, 32x62 u at (-16,-62); its crown is `crowns` below.
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
            SceneSpace.SceneVariant.RESTAURANT, SceneSpace.SceneVariant.SCHOOL, SceneSpace.SceneVariant.BAR,
            SceneSpace.SceneVariant.TOWER,
            -> listOf(
                floatArrayOf(
                    x - halfWidthPx(o),
                    g - (if (onInk) drawnUnits(o) else v.spriteUnitsTall) * s,
                    x + halfWidthPx(o),
                    g,
                ),
            )
            else -> emptyList()
        }
    }

    /**
     * The crown rectangles, re-derived here: this is where the independence of this file lives for
     * the crowns.
     *
     * Two hand-typed copies of the same literal rectangles, one on each side, guard only against a
     * typo -- the reason this file keeps its own, stated above: a wrong number has to be typed twice
     * to hide a covered shop. A *correct* transcription of a canvas that is two thirds air is typed
     * identically on both sides and wrong on both, and a redraw that moves a crown's canvas would
     * have to be copied into two files by hand.
     *
     * So the number is typed on neither side. It is measured off the shipped PNG by
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
     * without the generator, and that the frame was looked at.
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
    private fun sampledFrontCoverage(
        objects: List<StaticSceneObject>,
        shop: StaticSceneObject,
        onInk: Boolean = false,
    ): Float {
        val f = frontRect(shop, onInk)
        val cx = (f[0] + f[2]) / 2f
        val boxes = objects
            .filter { it !== shop && it.depthFraction > shop.depthFraction }
            .flatMap { occluderBoxes(it, onInk) }
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

    /**
     * **No more than four built-in themes may put a shop in the same spot of the street**, so the
     * twelve streets do not all stand a building of one kind in one place. "The same spot" is one
     * point of the tile that the most themes' fronts of one row of the size table all cover, at the
     * reference viewport and at the drawn width measured above; a theme with two places in a row
     * (two in the restaurant's, two in the bar's) counts once.
     */
    @Test
    fun `no more than four built-in themes put a shop in the same spot of the street`() {
        val fronts = themes.flatMap { themeId ->
            SceneObjectCatalog.layoutFor(themeId, 0xFF8899AA.toInt()).staticObjects.filter { isShop(it) }
                .map { Triple(SceneObjectRenderer.variantFor(it), themeId, it.tileFractionX to halfWidthPx(it) / tile) }
        }
        for ((variant, perTheme) in listOf(SceneSpace.SceneVariant.RESTAURANT to 2, SceneSpace.SceneVariant.SCHOOL to 1, SceneSpace.SceneVariant.BAR to 2)) {
            val mine = fronts.filter { it.first == variant }
            assertEquals("$perTheme $variant places a theme", perTheme * themes.size, mine.size)
            var worst = 0
            var where = ""
            for (k in 0 until 2000) {
                val p = k / 2000f
                val over = mine.filter { (_, _, xw) ->
                    val d = (p - xw.first).mod(1f)
                    minOf(d, 1f - d) < xw.second
                }.map { it.second }.toSet()
                if (over.size > worst) {
                    worst = over.size
                    where = "at %.3f: ".format(p) + over.joinToString()
                }
            }
            println("SHOP_SPOTS the most themes with a $variant place over one spot: $worst ($where)")
            assertTrue("$worst themes put the $variant in one spot ($where)", worst <= 4)
        }
    }

    private companion object {
        private val INK_HALF = HashMap<SceneSpace.SceneVariant, Float>()

        private val NAMES: Map<Int, String> = R.drawable::class.java.fields
            .filter { it.type == Int::class.javaPrimitiveType }
            .associate { it.getInt(null) to it.name }

        /** First and one-past-last column with any ink, in PNG pixels. */
        fun inkColumns(resId: Int): Pair<Int, Int>? {
            val file = File(DRAWABLES, "${NAMES.getValue(resId)}.png")
            val image = ImageIO.read(file) ?: return null
            var left = Int.MAX_VALUE
            var right = Int.MIN_VALUE
            for (y in 0 until image.height) for (x in 0 until image.width) {
                if ((image.getRGB(x, y) ushr 24) == 0) continue
                if (x < left) left = x
                if (x > right) right = x
            }
            return if (left > right) null else left to right + 1
        }

        val DRAWABLES: File by lazy {
            var dir: File? = File(".").absoluteFile
            while (dir != null) {
                for (prefix in listOf("", "app/")) {
                    val candidate = File(dir, "${prefix}src/main/res/drawable-nodpi")
                    if (candidate.isDirectory) return@lazy candidate
                }
                dir = dir.parentFile
            }
            error("could not locate src/main/res/drawable-nodpi")
        }
    }
}
