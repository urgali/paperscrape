package com.paperscrape.livewallpaper.engine

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Every one of the six buildings among the houses keeps its front clear in every place it can stand
 * in** (v5.13; five places since v5.13B2): the 40 % rule and "no trunk or pole across",
 * [ShopFrontVisibilityTest]'s two, measured for the restaurant, the school, the bar, the shop, the fire
 * station and the police station in each of the five places of each built-in street -- since the street
 * deals any of the six to any place ([StorefrontStreet]), a front that is clear for one building and
 * covered for another is a covered front somebody will see.
 *
 * Each front is the building's own: its ink's wider side and its drawn height, in its own pieces, at the
 * place's metre (`spriteUnitsTall / unitsTall` of the place's family, the renderer's scale). In front of
 * it stand the street's houses, trees, palms and parasols -- where the plan stands them, the ones it moved
 * included (`ShopPlanTable`) -- measured as [ShopFrontVisibilityTest] measures them, and the street's nearer
 * places with the widest and tallest of the six standing in each -- whoever stands there, nothing taller or
 * wider can. Measured declared (the building's family height) and on ink (its drawn height), at the
 * reference viewport; with the trees each street plants, with the palms the Palms switch puts in their
 * places on the ten that plant none, and with the trees it puts back on Beach and Desert.
 */
class StorefrontFrontVisibilityTest {

    private val themes = listOf(
        "sunset", "autumn", "winter", "desert", "christmas", "new_year",
        "beach", "city", "tundra", "easter", "halloween", "spring",
    )

    private val refW = 1080f
    private val refH = 2340f
    private val tile = refW * 2f

    @Test
    fun `each of the six buildings keeps both rules in each place of every built-in street`() =
        checkStreets(SceneCustomization.DEFAULT, streets = { true }, expected = 12, label = "STOREFRONT_FRONTS")

    /**
     * And with the Palms switch putting palms in the trees' places (v5.10C) on the ten streets that plant
     * none: the plan runs on the layout, before any switch, so the fronts are measured again with the palms
     * standing, as [ShopFrontVisibilityTest] does for the building of each row.
     */
    @Test
    fun `with palms in place of the trees, each of the six still keeps both rules everywhere`() =
        checkStreets(
            SceneCustomization.DEFAULT.copy(palmsInsteadOfTrees = true), streets = { !it.hasPalmSlots() }, expected = 10,
            label = "STOREFRONT_FRONTS_PALMS",
        )

    /**
     * And the other way round on Beach and Desert, whose palms the switch turns back into trees (decision 37):
     * the plan places the five against both species on all twelve streets (v5.13B2).
     */
    @Test
    fun `with trees in place of the palms on Beach and Desert, each of the six still keeps both rules`() =
        checkStreets(
            SceneCustomization.DEFAULT.copy(palmsEnabled = false), streets = { it.hasPalmSlots() }, expected = 2,
            label = "STOREFRONT_FRONTS_TREES",
        )

    /**
     * **The streets users saved** (v5.13B2): a saved version of a built-in theme, or a theme of the user's own
     * copied from one, keeps the street as it stood when it was saved -- its places where that release
     * planned them, each for the one building it held until v5.13. Here the twelve built-in streets as the
     * 5.12 and the 5.13B laid them out, every candidate standing (the most a saved street can hold), written by
     * each release's own serialiser from its own tree (`themes/streets-saved-by-v5.12.json`, `-v5.13B.json`),
     * and read by today's loader, which moves a place where a building is crossed or covered past the rule
     * ([SceneObjectCatalog.placesClearedOnSavedStreet]; the maintainer's *«Sposta quel posto (consigliato)»*,
     * 2026-10-13): all six in each of their places, with the trees and with the palms, against both rules,
     * and nothing but those places moved. As stored, 26 fronts of 864 were outside the rules -- the 5.12's near
     * place on Autumn, Christmas and New Year, and Desert's school with its palms switched off (which the
     * 5.13B saved too). Printed: what moved, and by how much.
     */
    @Test
    fun `on the streets users saved from v5_12 and v5_13B, the places are moved where a building would be crossed`() {
        val report = StringBuilder()
        var fronts = 0
        var moved = 0
        val failures = ArrayList<String>()
        for (version in listOf("v5.12", "v5.13B")) {
            val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("themes/streets-saved-by-$version.json")) { version }
            val saved = org.json.JSONObject(stream.bufferedReader().readText())
            for (themeId in themes) {
                val stored = saved.getJSONObject(themeId).getJSONArray("staticObjects")
                val layout = sceneObjectLayoutFromJson(saved.getJSONObject(themeId))
                assertEquals(stored.length(), layout.staticObjects.size)
                for ((k, o) in layout.staticObjects.withIndex()) {
                    val was = staticSceneObjectFromJson(stored.getJSONObject(k))
                    if (was == o) continue
                    assertTrue("$version $themeId #$k: only a place moves", StorefrontStreet.isPlace(was))
                    assertEquals("$version $themeId #$k: only its position", was.copy(tileFractionX = o.tileFractionX), o)
                    moved++
                    val d = (o.tileFractionX - was.tileFractionX).mod(1f).let { if (it > 0.5f) it - 1f else it }
                    report.append("SAVED_MOVED $version %-10s %s place %.3f -> %.3f (%+.3f of the tile, %+.0f px of the BV6600)\n".format(
                        themeId, SceneObjectRenderer.variantFor(o), was.tileFractionX, o.tileFractionX, d, d * 1440f,
                    ))
                }
                for (species in listOf("trees", "palms")) {
                    val objects = layout.staticObjects.map {
                        when {
                            species == "trees" -> it
                            layout.hasPalmSlots() && it.type == SceneObjectType.PALM_TREE -> it.copy(type = SceneObjectType.TREE)
                            !layout.hasPalmSlots() && it.type == SceneObjectType.TREE -> it.copy(type = SceneObjectType.PALM_TREE)
                            else -> it
                        }
                    }
                    val places = objects.filter { StorefrontStreet.isPlace(it) }
                    assertEquals("$version $themeId: three places", 3, places.size)
                    for (place in places) for (storefront in Storefront.ALL) {
                        fronts++
                        val name = "$version $themeId/$species/${SceneObjectRenderer.variantFor(place)}@%.3f/$storefront".format(place.tileFractionX)
                        val coverage = maxOf(coverage(objects, place, storefront, false), coverage(objects, place, storefront, true))
                        val f = front(place, storefront, onInk = false)
                        val cx = (f[0] + f[2]) / 2f
                        val crossed = objects.filter { it.depthFraction > place.depthFraction }
                            .mapNotNull { member(it)?.let { m -> wrap(m, cx) } }
                            .any { b -> b[2] > f[0] && b[0] < f[2] && b[3] > f[1] && b[1] < f[3] }
                        if (coverage > 0.40f || crossed) failures += "$name ${"%.1f".format(coverage * 100)}%${if (crossed) " crossed" else ""}"
                    }
                }
            }
        }
        println(report)
        println("SAVED_FRONTS $fronts fronts of the saved streets, ${failures.size} outside the rules; $moved places moved")
        assertTrue("saved streets: $failures", failures.isEmpty())
        assertTrue("the loader moved nothing: this test saw nothing to repair", moved > 0)
    }

    /**
     * **A place moved on a saved street keeps its buildings' colours and people, and is saved back where the
     * theme stored it** (v5.13B2): the maintainer chose the move on the promise that the rest of the theme
     * stays as it is, colours included. The six buildings take their identity -- the object their colour coin,
     * figure and people are read from -- from the places as stored ([SceneObjectLayout.placesAsSaved],
     * [StorefrontStreet.identities]), so on the streets above they are the identities of the street as it was
     * stored; a save writes the street as it was stored, so the next load moves the same place the same way;
     * and a copy the gallery saves of that theme ([com.paperscrape.livewallpaper.ui.snapshotEntry]) carries the
     * same, through the saved version of a built-in theme the renderer draws.
     */
    @Test
    fun `a place moved on a saved street keeps its buildings' identity and is saved where it was stored`() {
        var moved = 0
        try {
            for (version in listOf("v5.12", "v5.13B")) {
                val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("themes/streets-saved-by-$version.json")) { version }
                val saved = org.json.JSONObject(stream.bufferedReader().readText())
                for (themeId in themes) {
                    val stored = saved.getJSONObject(themeId).getJSONArray("staticObjects")
                    val asStored = (0 until stored.length()).map { staticSceneObjectFromJson(stored.getJSONObject(it)) }
                    val layout = sceneObjectLayoutFromJson(saved.getJSONObject(themeId))
                    val identities = StorefrontStreet.identities(StorefrontStreet.places(SceneObjectLayout(asStored, layout.cars)))!!
                    assertTrue("$version $themeId: the identities", identities.contentEquals(StorefrontStreet.identities(layout)))
                    val written = layout.toJson().getJSONArray("staticObjects")
                    assertEquals(asStored, (0 until written.length()).map { staticSceneObjectFromJson(written.getJSONObject(it)) })
                    assertEquals("$version $themeId: loads again as it loaded", layout, sceneObjectLayoutFromJson(layout.toJson()))
                    if (layout.placesAsSaved.isEmpty()) continue
                    moved++
                    // The gallery's copy of the saved version of the theme, as the renderer then reads it.
                    val theme = ThemeCatalog.byId(themeId)
                    CustomThemeRegistry.update(CustomThemeData(overrides = mapOf(themeId to CustomThemeEntry(themeId, theme.displayName, theme, layout, defaultCustomizationFor(themeId)))))
                    val copy = com.paperscrape.livewallpaper.ui.snapshotEntry(
                        targetId = "custom_copy", targetName = "Copy", sourceThemeId = themeId,
                        pendingCustomization = defaultCustomizationFor(themeId), pendingCustomizationThemeId = themeId,
                    )
                    assertTrue("$version $themeId: the gallery's copy", identities.contentEquals(StorefrontStreet.identities(copy.layout)))
                    val copied = copy.layout.toJson().getJSONArray("staticObjects")
                    val places = (0 until copied.length()).map { staticSceneObjectFromJson(copied.getJSONObject(it)) }.filter { StorefrontStreet.isPlace(it) }
                    assertEquals("$version $themeId: the copy saves the places as stored", asStored.filter { StorefrontStreet.isPlace(it) }, places)
                }
            }
        } finally {
            CustomThemeRegistry.update(CustomThemeData())
        }
        assertTrue("no saved street had a place moved: this test saw nothing", moved > 0)
    }

    /**
     * **A built-in theme saved keeps its six buildings' identities** (v5.13B2): saved as a copy from the gallery
     * ([com.paperscrape.livewallpaper.ui.snapshotEntry]), written and read back, its street is the built-in
     * street, places included ([StorefrontStreet.builtInStreetOf]), and its six buildings keep the identities --
     * colours and people -- the built-in theme gives them, read as the renderer reads a saved theme (no theme
     * id): saving a theme changes nothing it shows. No other street is taken for a built-in one: the streets
     * the 5.12 and the 5.13B saved are none.
     */
    @Test
    fun `a built-in theme saved keeps its six buildings' identities`() {
        try {
            CustomThemeRegistry.update(CustomThemeData())
            for (themeId in themes) {
                val builtIn = StorefrontStreet.identities(SceneObjectCatalog.layoutFor(themeId, ThemeCatalog.byId(themeId).accentColor), themeId)!!
                val copy = com.paperscrape.livewallpaper.ui.snapshotEntry(
                    targetId = "custom_copy", targetName = "Copy", sourceThemeId = themeId,
                    pendingCustomization = defaultCustomizationFor(themeId), pendingCustomizationThemeId = themeId,
                )
                val loaded = sceneObjectLayoutFromJson(copy.layout.toJson())
                assertEquals(themeId, StorefrontStreet.builtInStreetOf(StorefrontStreet.places(loaded)))
                assertTrue("$themeId: the saved copy's identities", builtIn.contentEquals(StorefrontStreet.identities(loaded)))
            }
            for (version in listOf("v5.12", "v5.13B")) {
                val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("themes/streets-saved-by-$version.json")) { version }
                val saved = org.json.JSONObject(stream.bufferedReader().readText())
                for (themeId in themes) {
                    val places = StorefrontStreet.places(sceneObjectLayoutFromJson(saved.getJSONObject(themeId)))
                    assertEquals("$version $themeId taken for a built-in street", null, StorefrontStreet.builtInStreetOf(places))
                }
            }
        } finally {
            CustomThemeRegistry.update(CustomThemeData())
        }
    }

    private fun checkStreets(c: SceneCustomization, streets: (SceneObjectLayout) -> Boolean, expected: Int, label: String) {
        var checked = 0
        var worst = 0f
        var worstName = ""
        val failures = mutableListOf<String>()
        for (themeId in themes) {
            val layout = SceneObjectCatalog.layoutFor(themeId, 0xFF8899AA.toInt())
            if (!streets(layout)) continue
            val objects = layout.staticObjects.map { c.palmSpeciesApplied(it, layout.hasPalmSlots()) }
            val places = objects.filter { StorefrontStreet.isPlace(it) }
            assertEquals("$themeId: five places among the houses", 5, places.size)
            for (place in places) for (storefront in Storefront.ALL) {
                checked++
                val name = "$themeId/${SceneObjectRenderer.variantFor(place)}@%.3f/$storefront".format(place.depthFraction)
                for (onInk in listOf(false, true)) {
                    val coverage = coverage(objects, place, storefront, onInk)
                    if (onInk && coverage > worst) { worst = coverage; worstName = name }
                    if (coverage > 0.40f) failures += "$name ${if (onInk) "on ink" else "declared"} ${"%.1f".format(coverage * 100)}%"
                }
                val f = front(place, storefront, onInk = false)
                val cx = (f[0] + f[2]) / 2f
                val crossers = objects
                    .filter { it.depthFraction > place.depthFraction }
                    .mapNotNull { o -> member(o)?.let { o to wrap(it, cx) } }
                    .filter { (_, b) -> b[2] > f[0] && b[0] < f[2] && b[3] > f[1] && b[1] < f[3] }
                if (crossers.isNotEmpty()) {
                    failures += "$name crossed by " +
                        crossers.joinToString { (o, _) -> "a ${SceneObjectRenderer.variantFor(o)} at x=${o.tileFractionX}" }
                }
            }
        }
        println("$label worst on ink: ${"%.3f".format(worst)} at $worstName, $checked fronts")
        assertEquals("this test asserted over no front at all", expected * 5 * 6, checked)
        assertTrue("fronts outside the rules: $failures", failures.isEmpty())
    }

    /**
     * The widths the street's places are planned with ([Storefront.halfWidthUnits], typed in `Storefronts.kt`
     * for the planner) are the ones the shipped PNGs measure: a redraw that widens a building fails here
     * until the number moves with it, and the plan with the number ([ShopPlanTableTest]).
     */
    @Test
    fun `the half widths the planner reads are the ones the PNGs measure`() {
        for (storefront in Storefront.ALL) {
            assertEquals("$storefront: half width in piece units", inkHalf(storefront), storefront.halfWidthUnits, 0.01f)
        }
    }

    // ---- the model, written for this file ------------------------------------------------------

    /** The place's metre: how the renderer scales a piece standing in it. */
    private fun metre(place: StaticSceneObject): Float {
        val v = SceneObjectRenderer.variantFor(place)
        return v.spriteUnitsTall / NeighbourhoodTable.FAMILIES.getValue(v).unitsTall
    }

    private fun front(place: StaticSceneObject, storefront: Storefront, onInk: Boolean): FloatArray {
        val s = SceneObjectRenderer.effectiveScaleFor(place, refH) * metre(place)
        val g = refH * SceneSpace.groundYFraction(place.depthFraction)
        val x = place.tileFractionX * tile
        val half = inkHalf(storefront) * s
        val tall = (if (onInk) drawnTall(storefront) else storefront.family.unitsTall) * s
        return floatArrayOf(x - half, g - tall, x + half, g)
    }

    /** The widest and the tallest of the six in [place]: the body a nearer place puts in front, whoever stands there. */
    private fun envelope(place: StaticSceneObject, onInk: Boolean): FloatArray {
        val fronts = Storefront.ALL.map { front(place, it, onInk) }
        return floatArrayOf(fronts.minOf { it[0] }, fronts.minOf { it[1] }, fronts.maxOf { it[2] }, fronts.maxOf { it[3] })
    }

    private fun coverage(objects: List<StaticSceneObject>, place: StaticSceneObject, storefront: Storefront, onInk: Boolean): Float {
        val f = front(place, storefront, onInk)
        val cx = (f[0] + f[2]) / 2f
        val boxes = objects
            .filter { it !== place && it.depthFraction > place.depthFraction }
            .flatMap { if (StorefrontStreet.isPlace(it)) listOf(envelope(it, onInk)) else occluders(it) }
            .map { wrap(it, cx) }
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

    private fun occluders(o: StaticSceneObject): List<FloatArray> {
        val v = SceneObjectRenderer.variantFor(o)
        val s = SceneObjectRenderer.effectiveScaleFor(o, refH)
        val g = refH * SceneSpace.groundYFraction(o.depthFraction)
        val x = o.tileFractionX * tile
        return when (v) {
            SceneSpace.SceneVariant.TREE -> crowns(SpriteOccluderTable.TREE_CROWN, x, g, s) + floatArrayOf(x - 16f * s, g - 62f * s, x + 16f * s, g)
            SceneSpace.SceneVariant.PALM_TREE -> crowns(SpriteOccluderTable.PALM_CROWN, x, g, s) + floatArrayOf(x - 8f * s, g - 58f * s, x + 13f * s, g)
            SceneSpace.SceneVariant.PARASOL -> crowns(SpriteOccluderTable.PARASOL_FAN, x, g, s) + floatArrayOf(x - 2.5f * s, g - 50f * s, x + 2.5f * s, g)
            // A house as tall as its family declares, or as it draws where its own deal stands it a storey
            // above (v5.13B2): the renderer's deal, by the silhouette the street dealt it.
            SceneSpace.SceneVariant.HOUSE_SMALL, SceneSpace.SceneVariant.HOUSE_LARGE -> {
                val family = NeighbourhoodTable.FAMILIES.getValue(v)
                val half = familyInkHalf(family) * v.spriteUnitsTall / family.unitsTall
                val deal = NeighbourhoodComposer.Deal()
                NeighbourhoodComposer.deal(family, o, deal)
                var top = 0f
                for (i in 0 until deal.size) for (part in deal[i].piece.parts) {
                    if (part.res == 0 || part.role == PartRole.SNOW || part.role == PartRole.LAMP || part.role == PartRole.OCCUPANTS) continue
                    top = maxOf(top, -(deal[i].baseY + part.y))
                }
                val tall = maxOf(v.spriteUnitsTall, top * v.spriteUnitsTall / family.unitsTall)
                listOf(floatArrayOf(x - half * s, g - tall * s, x + half * s, g))
            }
            else -> emptyList()
        }
    }

    private fun crowns(family: List<SpriteOccluderTable.InkBox>, x: Float, g: Float, s: Float): List<FloatArray> = family.map { ink ->
        val shrinkX = (ink.contentRight - ink.contentLeft) * (1f - ink.rowMax) / 2f
        val shrinkY = (ink.contentBottom - ink.contentTop) * (1f - ink.columnMax) / 2f
        floatArrayOf(x + (ink.contentLeft + shrinkX) * s, g + (ink.contentTop + shrinkY) * s, x + (ink.contentRight - shrinkX) * s, g + (ink.contentBottom - shrinkY) * s)
    }

    private fun member(o: StaticSceneObject): FloatArray? {
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

    private fun wrap(b: FloatArray, cx: Float): FloatArray {
        val boxCx = (b[0] + b[2]) / 2f
        val rawDx = (boxCx - cx).mod(tile)
        val dx = if (rawDx > tile / 2f) rawDx - tile else rawDx
        val shift = (cx + dx) - boxCx
        return floatArrayOf(b[0] + shift, b[1], b[2] + shift, b[3])
    }

    /** The wider side of [storefront]'s ink in its own piece units: every part's PNG columns, snow, lamps and busts excluded. */
    private fun inkHalf(storefront: Storefront): Float = familyInkHalf(storefront.family)

    private fun familyInkHalf(f: BuildingFamily): Float {
        var left = 0f
        var right = 0f
        for (slot in f.slots) for (piece in slot.options + slot.heights) for (part in piece.parts) {
            if (part.res == 0 || part.role == PartRole.SNOW || part.role == PartRole.LAMP || part.role == PartRole.OCCUPANTS) continue
            val (l, r) = inkColumns(part.res) ?: continue
            left = minOf(left, part.x + l / 3f)
            right = maxOf(right, part.x + r / 3f)
        }
        return maxOf(-left, right)
    }

    /** [storefront]'s drawn height in its own piece units: the top of its deal's highest part, snow, lamps and busts excluded. */
    private fun drawnTall(storefront: Storefront): Float {
        var top = 0f
        for (slot in storefront.family.slots) for (piece in slot.options + slot.heights) for (part in piece.parts) {
            if (part.res == 0 || part.role == PartRole.SNOW || part.role == PartRole.LAMP || part.role == PartRole.OCCUPANTS) continue
            top = maxOf(top, -part.y)
        }
        return top
    }

    private val names: Map<Int, String> = com.paperscrape.livewallpaper.R.drawable::class.java.fields
        .filter { it.type == Int::class.javaPrimitiveType }
        .associate { it.getInt(null) to it.name }

    private val drawables: File by lazy {
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

    /** The first and one-past-last opaque columns of a sprite, in its PNG's pixels. */
    private fun inkColumns(res: Int): Pair<Int, Int>? {
        val image = ImageIO.read(File(drawables, "${names.getValue(res)}.png")) ?: return null
        var l = Int.MAX_VALUE
        var r = -1
        for (y in 0 until image.height) for (x in 0 until image.width) {
            if ((image.getRGB(x, y) ushr 24) != 0) { l = minOf(l, x); r = maxOf(r, x + 1) }
        }
        return if (r < 0) null else l to r
    }
}
