package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The gallery card hangs on each of its buildings the Christmas decorations the wallpaper hangs on it** (v5.14C2,
 * ROADMAP A186; the maintainer's *«Aggiungili nella scheda»*, 2026-10-16): counted and placed building by building, against
 * the points the buildings' generator wrote into `NeighbourhoodTable.kt` -- worked out here from the table, not through
 * [ChristmasDecorations] (but for which sills carry a string: the wallpaper's own rule, [ChristmasDecorations.sillStrung],
 * under the card building's seed), so a card that drifted from the rule fails here whatever the sequence says:
 *
 * - a bulb on every point of the top piece's roofline, 3 units across (a tower's half as large again), in the lights'
 *   four colours in turn;
 * - on a tower its star, its ten corners about a middle 6.5 units times a tower's scale (1.5) above its highest point;
 * - on every front door a wreath, `deco_wreath`, its middle 6.5 units (on a tower times 1.5) below the door's top;
 * - under every sill of a building with four or fewer, under three in four of a larger one's, a string's four bulbs, hung
 *   1.6 units under a cord that sags to its middle 16 % of its width (the cord itself is not drawn on the card);
 * - three bulbs on every distant house, at the first, the middle and the last point of its roofline, in three of the
 *   lights' colours following one another, at their full light.
 *
 * **And a little larger than the wallpaper's, so they read** (the maintainer's *«A: un po' più grandi (consigliata)»*,
 * 2026-10-16): every decoration of a building the same number of times its size, never less than its size, so its smallest is
 * 2 px on the BV6600's gallery card -- not less, and not more where it had to grow; the distant houses' bulbs the same way.
 *
 * The wallpaper is held to the same table on the BV6600 (`ChristmasDecorationsDrawnTest`: the wreaths, the roof's
 * bulbs, the towers' stars, how many strings a building carries and where, the distant houses' bulbs and their colours),
 * so the two hang them by the same rule at the same points of a building. (The card's buildings are dealt from positions
 * picked for the picture, `ThemePreviewScenes.PreviewIdentity`, not the wallpaper's street: the same rule, other buildings.) All twelve built-ins with *Christmas lights* on, by day and at night, open and with every building that
 * keeps hours shut (decorations are lit open or shut). None with the switch off. The factors and the sizes the decorations
 * come to on the BV6600's gallery card are printed (`CARD_DECORATION_PX`).
 */
class CardDecorationsTest {

    /** A card building's identity: its family, the position its deal is read at, and what it is drawn as. */
    private class Identity(val family: BuildingFamily, val tileX: Float, val depth: Float)

    private val identities: List<Identity> = buildList {
        for (i in 0 until 4) {
            add(Identity(NeighbourhoodTable.FAMILIES.getValue(SceneSpace.SceneVariant.TOWER),
                ThemePreviewScenes.PreviewIdentity.TOWER_X[i], ThemePreviewScenes.PreviewIdentity.TOWER_DEPTH[i]))
        }
        for (row in listOf(
            ThemePreviewScenes.PreviewIdentity.RESTAURANT_X to ThemePreviewScenes.PreviewIdentity.RESTAURANT_DEPTH,
            ThemePreviewScenes.PreviewIdentity.SCHOOL_X to ThemePreviewScenes.PreviewIdentity.SCHOOL_DEPTH,
            ThemePreviewScenes.PreviewIdentity.BAR_X to ThemePreviewScenes.PreviewIdentity.BAR_DEPTH,
        )) {
            for (b in Storefront.ALL) add(Identity(b.family, row.first, row.second))
        }
        add(Identity(NeighbourhoodTable.FAMILIES.getValue(SceneSpace.SceneVariant.HOUSE_LARGE),
            ThemePreviewScenes.PreviewIdentity.HOUSE_LARGE_X, ThemePreviewScenes.PreviewIdentity.HOUSE_LARGE_DEPTH))
        add(Identity(NeighbourhoodTable.FAMILIES.getValue(SceneSpace.SceneVariant.HOUSE_SMALL),
            ThemePreviewScenes.PreviewIdentity.HOUSE_SMALL_X, ThemePreviewScenes.PreviewIdentity.HOUSE_SMALL_DEPTH))
    }

    /**
     * The identities the card may stand on [item]'s row (`ThemePreviewScenes`' rows: the towers at 168, the restaurant's
     * row at 178, the large house at 182, the school's row at 190, the small house at 194, the bar's row at 198; a tower by
     * where it stands across the card).
     */
    private fun identitiesOn(item: PreviewItem): List<Identity> {
        val p = ThemePreviewScenes.PreviewIdentity
        fun at(x: Float, depth: Float) = identities.filter { it.tileX == x && it.depth == depth }
        return when (item.y) {
            168f -> {
                val i = mapOf(40f to 0, 108f to 1, 176f to 2, 250f to 3, 296f to 2).getValue(item.x)
                at(p.TOWER_X[i], p.TOWER_DEPTH[i])
            }
            178f -> at(p.RESTAURANT_X, p.RESTAURANT_DEPTH)
            182f -> at(p.HOUSE_LARGE_X, p.HOUSE_LARGE_DEPTH)
            190f -> at(p.SCHOOL_X, p.SCHOOL_DEPTH)
            194f -> at(p.HOUSE_SMALL_X, p.HOUSE_SMALL_DEPTH)
            198f -> at(p.BAR_X, p.BAR_DEPTH)
            else -> error("a card building on no row: y=${item.y}")
        }
    }

    /** The building's own parts as the card lays them, the paper only: what tells which deal it is. */
    private fun paper(deal: NeighbourhoodComposer.Deal, winter: Boolean): List<Triple<Int, Float, Float>> = buildList {
        for (i in 0 until deal.size) {
            val placed = deal[i]
            for (part in placed.piece.parts) {
                val keep = when (part.role) {
                    PartRole.FIXED, PartRole.WALL_MASK, PartRole.GLASS_MASK -> true
                    PartRole.SNOW -> winter
                    PartRole.LAMP, PartRole.OCCUPANTS -> false
                }
                if (keep) add(Triple(part.res, part.x, placed.baseY + part.y))
            }
        }
    }

    private val buildingSprites: Set<Int> = identities.flatMap { id -> id.family.slots.flatMap { it.options + it.heights } }
        .flatMap { piece -> piece.parts.filter { it.res != 0 }.map { it.res } }.toSet()

    /** A decoration: what it is, its numbers (where, how large), its colour. */
    private class Decoration(val what: String, val numbers: FloatArray, val colour: Int? = null) {
        fun matches(o: Decoration): Boolean = what == o.what && colour == o.colour && numbers.size == o.numbers.size &&
            numbers.indices.all { kotlin.math.abs(numbers[it] - o.numbers[it]) < 1e-3f }
        override fun toString(): String = what + numbers.joinToString(",", "(", ")") { "%.3f".format(it) } + (colour?.let { " %08X".format(it) } ?: "")
    }

    private fun line(what: String, vararg numbers: Float, colour: Int? = null) = Decoration(what, numbers, colour)

    /**
     * What the wallpaper hangs on [deal], a building of [kind], in its order, worked out from the table, [f] times its size:
     * a wreath by its middle and its scale, a string by its four bulbs.
     */
    private fun expected(deal: NeighbourhoodComposer.Deal, kind: WindowBuildingKind, f: Float, idleSeed: Float): List<Decoration> = buildList {
        val k = if (kind == WindowBuildingKind.SKYSCRAPER) 1.5f else 1f
        val colours = intArrayOf(0xFFE8564F.toInt(), 0xFFFFD54F.toInt(), 0xFF4F8FBF.toInt(), 0xFF6FCF6F.toInt())
        val top = deal[deal.size - 1]
        for (i in top.piece.roofX.indices) {
            add(line("bulb", top.piece.roofX[i], top.baseY + top.piece.roofY[i], 1.5f * k * f, colour = colours[i % 4]))
        }
        if (kind == WindowBuildingKind.SKYSCRAPER && top.piece.roofX.isNotEmpty()) {
            val cx = top.piece.topX
            val cy = top.baseY + top.piece.topY - 6.5f * k
            val corners = FloatArray(20) { n ->
                val angle = Math.toRadians(-90.0 + (n / 2) * 36.0)
                val r = (if ((n / 2) % 2 == 0) 7f * k else 3f * k) * f
                if (n % 2 == 0) cx + r * Math.cos(angle).toFloat() else cy + r * Math.sin(angle).toFloat()
            }
            add(line("star", *corners, colour = 0xFFF2C24A.toInt()))
        }
        for (i in 0 until deal.size) {
            val placed = deal[i]
            val door = placed.piece.door ?: continue
            add(line("wreath", door.x + door.w / 2f, placed.baseY + door.y + 6.5f * k, k * f))
        }
        // The strings: every sill up to four, three in four above -- which ones, the building's own choice: the wallpaper's
        // rule ([ChristmasDecorations.sillStrung]) under the card building's own seed (`idleSeedOf` its spec, as the wallpaper
        // seeds a building from its own), so a card that chose by another seed fails here; and the count, from the table.
        val sills = (0 until deal.size).flatMap { i -> deal[i].piece.lights.map { Triple(it.x, deal[i].baseY + it.y, it.w) } }
        val strings = if (sills.size <= 4) sills.size else (sills.size * 3) / 4
        val chosen = sills.filterIndexed { i, _ -> sills.size <= 4 || ChristmasDecorations.sillStrung(idleSeed, i, sills.size, strings) }
        assertEquals("the strings a building carries", strings, chosen.size)
        for ((x, y, w) in chosen) add(line("string", x, y, w, 1.5f * f))
    }

    private class Smallest(var px: Float = Float.MAX_VALUE, var where: String = "") {
        fun offer(v: Float, w: String) { if (v < px) { px = v; where = w } }
    }

    @Test
    fun `every card building carries the wallpaper's decorations, in the wallpaper's places`() {
        val sizes = linkedMapOf<String, Smallest>()
        fun size(what: String, px: Float, where: String) = sizes.getOrPut(what) { Smallest() }.offer(px, where)
        var buildings = 0
        var towers = 0
        var strings = 0
        var wreaths = 0
        var houseBulbs = 0
        val factors = sortedMapOf<String, MutableSet<Float>>()
        val pxPerUnit = GALLERY_CARD_PX / ThemePreviewScene.WIDTH_UNITS
        for (theme in ThemeCatalog.ALL) for (shut in listOf(false, true)) for (night in listOf(false, true)) {
            val base = defaultCustomizationFor(theme.id).copy(christmasDecorationsEnabled = true)
            val c0 = if (!shut) base else base.copy(
                shopHoursEnabled = true, shopOpenHour = 13f, shopCloseHour = 14f,
                publicHoursEnabled = true, publicOpenHour = 13f, publicCloseHour = 14f,
                towerHoursEnabled = true, towerOpenHour = 13f, towerCloseHour = 14f,
            )
            val c = c0.copy(distantHouses = DistantHousesConfig(true, 0.5f), mountainsBack = c0.mountainsBack.copy(visible = true, density = maxOf(0.5f, c0.mountainsBack.density)))
            val scene = ThemePreviewScenes.forTheme(theme, c, night)
            val where = "${theme.id}${if (shut) " shut" else ""}${if (night) " night" else ""}"
            for (item in scene.items) {
                val buildingParts = item.parts.filter { !it.isFlat && it.resId in buildingSprites && it.resId != R.drawable.deco_wreath }
                if (buildingParts.isEmpty()) continue
                val drawn = buildingParts.map { Triple(it.resId, it.ox, it.oy) }
                val deal = NeighbourhoodComposer.Deal()
                // Which identity, by the row the card stands it on (the same pieces can be dealt on two rows, and the
                // building's seed is its row's), then by its pieces.
                val id = identitiesOn(item).firstOrNull { id ->
                    NeighbourhoodComposer.deal(id.family, id.tileX, id.depth, deal)
                    paper(deal, c.winterColorsEnabled) == drawn
                }
                assertTrue("$where: a card building at x=${item.x} is no deal of the card's identities", id != null)
                NeighbourhoodComposer.deal(id!!.family, id.tileX, id.depth, deal)
                val kind = id.family.kind
                val k = if (kind == WindowBuildingKind.SKYSCRAPER) 1.5f else 1f
                val decorations = item.parts.filter { it.decoration }
                // How many times its size the card draws this building's decorations: its first roof bulb says.
                val f = decorations.first { it.discRadius > 0f }.discRadius / (1.5f * k)
                assertTrue("$where: decorations drawn smaller than the wallpaper's ($f)", f >= 1f - 1e-6f)
                val spec = StaticSceneObject(SceneObjectType.HOUSE, depthFraction = id.depth, tileFractionX = id.tileX)
                val want = expected(deal, kind, f, SceneObjectRenderer.idleSeedOf(spec))
                // The card's, in the same words: a string is its four bulbs.
                val got = mutableListOf<Decoration>()
                var n = 0
                val sillsOf = (0 until deal.size).flatMap { i -> deal[i].piece.lights.map { Triple(it.x, deal[i].baseY + it.y, it.w) } }
                val roofBulbs = deal[deal.size - 1].piece.roofX.size
                while (n < decorations.size) {
                    val p = decorations[n]
                    when {
                        p.resId == R.drawable.deco_wreath -> {
                            got += line("wreath", p.ox + 5f * p.scale, p.oy + 5f * p.scale, p.scale); n++
                        }
                        p.outline != null -> {
                            assertEquals("$where: a star's ten corners", 20, p.outline.size)
                            got += line("star", *p.outline.toFloatArray(), colour = p.tint); n++
                        }
                        p.discRadius > 0f && n < roofBulbs -> { got += line("bulb", p.ox, p.oy, p.discRadius, colour = p.tint); n++ }
                        p.discRadius > 0f -> {
                            // A string's four bulbs, after the roof's, the star and the wreaths. Its sill, from the first and the
                            // last: across, a fifth and four fifths of the width; down, 40 % of the sag and the drop below it.
                            val bulbs = decorations.subList(n, n + 4)
                            val width = (bulbs[3].ox - bulbs[0].ox) * 5f / 3f
                            val left = bulbs[0].ox - width / 5f
                            val sillY = bulbs[0].oy - 0.4f * 0.16f * width - 1.6f
                            assertTrue("$where: a string from ($left, $sillY) $width wide hangs on no sill of its building",
                                sillsOf.any { kotlin.math.abs(it.first - left) < 1e-3f && kotlin.math.abs(it.second - sillY) < 1e-3f && kotlin.math.abs(it.third - width) < 1e-3f })
                            for ((m, bulb) in bulbs.withIndex()) {
                                val along = (m + 1f) / 5f
                                val drop = width * 0.16f * (1f - kotlin.math.abs(along - 0.5f) * 2f)
                                assertEquals("$where: a string's bulb across", left + width * along, bulb.ox, 1e-3f)
                                assertEquals("$where: a string's bulb hung", sillY + drop + 1.6f, bulb.oy, 1e-3f)
                                assertEquals("$where: a string's bulb in the lights' colours", intArrayOf(0xFFE8564F.toInt(), 0xFFFFD54F.toInt(), 0xFF4F8FBF.toInt(), 0xFF6FCF6F.toInt())[m], bulb.tint)
                                assertEquals("$where: every string's bulb the same size", bulbs[0].discRadius, bulb.discRadius, 0f)
                            }
                            got += line("string", left, sillY, width, bulbs[0].discRadius)
                            strings++
                            n += 4
                        }
                        else -> error("$where: a decoration of no kind: $p")
                    }
                }
                assertTrue(
                    "$where: the decorations of the card's ${if (kind == WindowBuildingKind.SKYSCRAPER) "tower" else "building"} at x=${item.x}: want $want, got $got",
                    want.size == got.size && want.indices.all { want[it].matches(got[it]) },
                )
                buildings++
                if (kind == WindowBuildingKind.SKYSCRAPER) towers++
                wreaths += got.count { it.what == "wreath" }
                // The smallest decoration of the building on the gallery card: 2 px, no less, and no more where it grew.
                val px = item.scale * pxPerUnit
                val smallest = listOfNotNull(
                    3f * k * f, SceneObjectRenderer.WREATH_BAND * k * f, 2f * SceneObjectRenderer.WREATH_BOW_RADIUS * k * f,
                    if (kind == WindowBuildingKind.SKYSCRAPER) 2f * 3f * k * f else null,
                    if (got.any { it.what == "string" }) 3f * f else null,
                ).min() * px
                assertTrue("$where: a decoration of ${"%.2f".format(smallest)} px on the card's building at x=${item.x}", smallest >= 2f - 1e-3f)
                if (f > 1f + 1e-6f) assertEquals("$where: decorations grown more than they had to", 2f, smallest, 1e-3f)
                val label = if (kind == WindowBuildingKind.SKYSCRAPER) "tower" else if (kind == WindowBuildingKind.HOUSE) "house" else "building among the houses"
                size("smallest decoration, $label", smallest, "$where, ${"%.2f".format(f)} times")
                factors.getOrPut(label) { mutableSetOf() } += Math.round(f * 100) / 100f
                size("roof bulb, $label", 3f * k * f * px, where)
                size("wreath bow, $label", 2f * SceneObjectRenderer.WREATH_BOW_RADIUS * k * f * px, where)
                if (got.any { it.what == "string" }) size("string bulb, $label", 3f * f * px, where)
                if (kind == WindowBuildingKind.SKYSCRAPER) size("star's middle, tower", 2f * 3f * k * f * px, where)
            }
            for (peak in scene.peaks) for (house in peak.houses) {
                val bulbs = house.parts.filter { it.decoration }
                val fixed = house.parts.first()
                val piece = NeighbourhoodTable.DISTANT_HOUSES.first { p -> p.parts.first().res == fixed.resId }
                val xs = piece.roofX
                // The wallpaper's share of the house: 2.2 px of its 14 px on the BV6600.
                val radius = PaperRenderer.DISTANT_BULB_PX / SceneSpace.distantHousePixelsTall(SceneSpace.DISTANT_HOUSE_FLOOR_Y_FRACTION, 1440f) * piece.height / 2f
                val wantX = listOf(xs[0], xs[xs.size / 2], xs[xs.size - 1])
                val wantY = listOf(piece.roofY[0], piece.roofY[xs.size / 2], piece.roofY[xs.size - 1]).map { it + radius * 0.5f }
                assertEquals("$where: a distant house's bulbs", wantX, bulbs.map { it.ox })
                assertEquals("$where: a distant house's bulbs", wantY, bulbs.map { it.oy })
                val lights = SceneObjectRenderer.CHRISTMAS_LIGHT_COLOURS.toList()
                val first = lights.indexOf(bulbs[0].tint)
                assertTrue("$where: a distant house's bulb not in the lights' colours", first >= 0)
                assertEquals("$where: a distant house's three colours", (0 until 3).map { lights[(first + it) % lights.size] }, bulbs.map { it.tint })
                for (b in bulbs) assertEquals("$where: a distant house's bulb not at its full light", 255, b.alpha)
                val drawnPx = 2f * bulbs[0].discRadius * house.scale * pxPerUnit
                for (b in bulbs) assertEquals(bulbs[0].discRadius, b.discRadius, 0f)
                assertTrue("$where: a distant house's bulb smaller than the wallpaper's share", bulbs[0].discRadius >= radius - 1e-6f)
                assertTrue("$where: a distant house's bulb of $drawnPx px", drawnPx >= 2f - 1e-3f)
                if (bulbs[0].discRadius > radius + 1e-6f) assertEquals("$where: a distant house's bulb grown more than it had to", 2f, drawnPx, 1e-3f)
                houseBulbs += bulbs.size
                size("distant house bulb", drawnPx, "$where, ${"%.2f".format(bulbs[0].discRadius / radius)} times")
            }
        }
        println("CardDecorationsTest: $buildings card buildings ($towers towers), $wreaths wreaths, $strings strings, $houseBulbs distant houses' bulbs")
        for ((what, s) in sizes) {
            println("CARD_DECORATION_PX %-40s %.2f px on the BV6600's gallery card as the factor takes it (%.0f px of its road), the smallest (%s); %.2f px on its settings home card (%.0f px)"
                .format(what, s.px, GALLERY_CARD_PX, s.where, s.px * HOME_CARD_PX / GALLERY_CARD_PX, HOME_CARD_PX))
        }
        for ((label, f) in factors) println("CARD_DECORATION_PX factor on the card's $label: ${f.sorted()}")
        assertTrue("no tower on a card", towers > 0)
        assertTrue("no string on a card", strings > 0)
        assertTrue("no distant house's bulb on a card", houseBulbs > 0)
    }

    @Test
    fun `no card hangs a decoration while the switch is off`() {
        for (theme in ThemeCatalog.ALL) for (night in listOf(false, true)) {
            val c = defaultCustomizationFor(theme.id).let {
                it.copy(christmasDecorationsEnabled = false, distantHouses = DistantHousesConfig(true, 1f))
            }
            val scene = ThemePreviewScenes.forTheme(theme, c, night)
            val all = (scene.items + scene.backdrop + scene.ground + scene.peaks.flatMap { it.houses }).flatMap { it.parts }
            assertTrue("${theme.id}: a decoration on a card with the lights off", all.none { it.decoration || it.resId == R.drawable.deco_wreath })
        }
        // And on: Christmas as it ships draws them.
        val xmas = ThemePreviewScenes.forTheme(ThemeCatalog.byId("christmas"), defaultCustomizationFor("christmas"))
        assertTrue(xmas.items.flatMap { it.parts }.count { it.decoration } > 20)
    }

    /** Both sides take the places from the one sequence: the wallpaper's draw functions and the card's builder, read. */
    @Test
    fun `the wallpaper and the card hang them through the same sequence`() {
        val root = File(sourceRoot(), "engine")
        val objects = File(root, "SceneObjectRenderer.kt").readText()
        val decorations = objects.substringAfter("private fun drawDecorations(").substringBefore("\n    }\n")
        for (call in listOf("ChristmasDecorations.forEachRoofBulb(deal, kind)", "ChristmasDecorations.star(deal, kind)", "ChristmasDecorations.forEachWreath(deal, kind)")) {
            assertTrue("drawDecorations no longer calls $call", decorations.contains(call))
        }
        assertTrue(objects.contains("ChristmasDecorations.forEachSillString(deal, r.idleSeed)"))
        assertTrue(objects.substringAfter("private fun drawWindowLights(").substringBefore("\n    }\n").contains("ChristmasDecorations.forEachStringBulb(x, sillY, width)"))
        assertTrue(File(root, "PaperRenderer.kt").readText().contains("ChristmasDecorations.forEachDistantHouseBulb(piece, radius, slot)"))
        val card = File(root, "ThemePreviewScene.kt").readText()
        for (call in listOf(
            "ChristmasDecorations.forEachRoofBulb(deal, kind)", "ChristmasDecorations.star(deal, kind)", "ChristmasDecorations.forEachWreath(deal, kind)",
            "ChristmasDecorations.forEachSillString(deal, idleSeed)", "ChristmasDecorations.forEachStringBulb(x, sillY, width)",
            "ChristmasDecorations.forEachDistantHouseBulb(piece, radius, slot)",
        )) {
            assertTrue("the card no longer calls $call", card.contains(call))
        }
    }

    private fun sourceRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/kotlin/com/paperscrape/livewallpaper")
            if (f.isDirectory) return f
            val g = File(dir, "src/main/kotlin/com/paperscrape/livewallpaper")
            if (g.isDirectory) return g
            dir = dir.parentFile
        }
        error("sources not found")
    }

    private companion object {
        /**
         * The BV6600's gallery card as the factor takes it (v5.14C2): the 310 px its road shows on the 720-px screen in the
         * two-column gallery -- the card is 316 px across, its rounded corners and border covering the road's ends, so the
         * decorations come to 2 % more there than printed --; the settings home card 656 px (v5.14B2).
         */
        const val GALLERY_CARD_PX = 310f
        const val HOME_CARD_PX = 656f
    }
}
