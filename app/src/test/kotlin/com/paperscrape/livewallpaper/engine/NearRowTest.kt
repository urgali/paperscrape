package com.paperscrape.livewallpaper.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import com.paperscrape.livewallpaper.ui.snapshotEntry
import org.junit.Test
import java.io.File

/**
 * **The scene lifted and the near row below the road** (`DESIGN_NOTES.md` §12, decision 96): every line of the ground 0.08
 * of the screen higher, the projection's ratios unchanged, and under the road the street seen a second time -- its houses,
 * trees, parasols and decorations and the shops' group, at a fixed size, with a pavement of its own -- planned from the
 * whole layout ([SceneObjectCatalog.nearRowOf]) and dealt its shops two in a row never alike ([SceneObjectCatalog.nearShopAt]).
 */
class NearRowTest {

    @Before
    fun clearRegistry() = CustomThemeRegistry.update(CustomThemeData.EMPTY)

    @After
    fun clearRegistryAfter() = CustomThemeRegistry.update(CustomThemeData.EMPTY)

    /** The lift of the whole ground, 0.08 of the screen (decision 96); each line below is held against the one it is lifted from. */
    private val lift = 0.08f

    @Test
    fun `every line of the ground stands 0 08 of the screen higher and the projection is the same`() {
        val before = mapOf(
            "horizon" to (0.655f to SceneSpace.HORIZON_Y_FRACTION),
            "band bottom" to (0.790f to SceneSpace.OBJECT_BAND_BOTTOM_Y_FRACTION),
            "far lane" to (0.834f to SceneSpace.ROAD_LANE_FAR_Y_FRACTION),
            "near lane" to (0.862f to SceneSpace.ROAD_LANE_NEAR_Y_FRACTION),
            "far pavement" to (0.795f to SceneSpace.PAVEMENT_FAR_Y_FRACTION),
            "near pavement" to (0.807f to SceneSpace.PAVEMENT_NEAR_Y_FRACTION),
            "reference" to (0.846f to SceneSpace.REFERENCE_Y_FRACTION),
            "distant house floor" to (0.6722f to SceneSpace.DISTANT_HOUSE_FLOOR_Y_FRACTION),
            "hill top" to (0.60f to SceneSpace.HILL_LAYER_TOP_FRACTION),
            "band top" to (0.704f to SceneSpace.OBJECT_BAND_TOP_Y_FRACTION),
            "arc's horizon" to (0.62f to PaperRenderer.HORIZON_Y_FRACTION_OF_ARC),
        )
        for ((name, pair) in before) assertEquals(name, pair.first - lift, pair.second, 1e-5f)
        assertEquals("the crest's band is as tall", 0.40f, SceneSpace.HILL_LAYER_HEIGHT_FRACTION, 0f)
        // The projection of every line is what it was: nothing changes size.
        for (depth in listOf(0f, 0.28f, 0.5f, 0.8f, 1f)) {
            val old = ((0.704f + depth * (0.790f - 0.704f)) - 0.655f) / (0.846f - 0.655f)
            assertEquals("depth $depth", old, SceneSpace.depthScale(depth), 2e-5f)
        }
        assertEquals((0.807f - 0.655f) / (0.846f - 0.655f), SceneSpace.perspectiveScaleAt(SceneSpace.PAVEMENT_NEAR_Y_FRACTION), 2e-5f)
        assertEquals((0.834f - 0.655f) / (0.846f - 0.655f), SceneSpace.perspectiveScaleAt(SceneSpace.ROAD_LANE_FAR_Y_FRACTION), 2e-5f)
        // The road 0.738 to 0.798 of the screen: 291 px under it on the BV6600's 1 440.
        assertEquals(0.7378f, SceneSpace.roadTopYFraction(), 5e-4f)
        assertEquals(0.7982f, SceneSpace.roadBottomYFraction(), 5e-4f)
        assertEquals(291f, 1440f * (1f - SceneSpace.roadBottomYFraction()), 1f)
    }

    @Test
    fun `the near row stands under the road, its people in front of it, its meadow from the road to the bottom`() {
        val roadBottom = SceneSpace.roadBottomYFraction()
        assertTrue(SceneSpace.NEAR_MEADOW_TOP_Y_FRACTION > roadBottom)
        assertTrue(SceneSpace.NEAR_ROW_FEET_TOP_Y_FRACTION > SceneSpace.NEAR_MEADOW_TOP_Y_FRACTION)
        assertEquals(0.885f, SceneSpace.NEAR_ROW_FEET_TOP_Y_FRACTION, 0f)
        assertEquals(0.915f, SceneSpace.NEAR_ROW_FEET_BOTTOM_Y_FRACTION, 0f)
        assertEquals(0.885f, SceneSpace.nearRowFeetYFraction(0f), 0f)
        assertEquals(0.915f, SceneSpace.nearRowFeetYFraction(1f), 1e-6f)
        assertEquals(0.900f, SceneSpace.nearRowFeetYFraction(0.5f), 1e-6f)
        assertEquals(0.930f, SceneSpace.NEAR_PAVEMENT_FAR_Y_FRACTION, 0f)
        // The near pavement's rows are the pavement's, moved down: its nearest walker still on the screen.
        val deepest = SceneSpace.PAVEMENT_NEAR_Y_FRACTION + 0.012f + SceneSpace.NEAR_PAVEMENT_SHIFT_FRACTION
        assertTrue("the near pavement's deepest walker at $deepest", deepest < 1f)
        assertTrue(SceneSpace.NEAR_PAVEMENT_FAR_Y_FRACTION > SceneSpace.NEAR_ROW_FEET_BOTTOM_Y_FRACTION)
        assertTrue(SceneSpace.NEAR_MEADOW_BOTTOM_Y_FRACTION <= 1f)
        assertEquals(0.72f, SceneSpace.NEAR_ROW_SIZE, 0f)
        assertEquals(0.9f, SceneSpace.NEAR_PEOPLE_SIZE, 0f)
    }

    @Test
    fun `a near-row object is the size table's at the reference line times the near row's size, wherever it stands`() {
        val house = StaticSceneObject(SceneObjectType.HOUSE, depthFraction = 0.9f, tileFractionX = 0.3f, scale = 1.04f)
        val tree = StaticSceneObject(SceneObjectType.TREE, depthFraction = 0.2f, tileFractionX = 0.6f)
        for (spec in listOf(house, tree)) {
            val v = SceneObjectRenderer.variantFor(spec)
            assertEquals(v.baseScale * spec.scale * 0.72f * 0.6f, SceneObjectRenderer.nearScaleFor(spec, 1440f), 1e-6f)
        }
        assertEquals(
            "a depth farther or nearer does not change it",
            SceneObjectRenderer.nearScaleFor(tree, 1440f), SceneObjectRenderer.nearScaleFor(tree.copy(depthFraction = 0.95f), 1440f), 0f,
        )
    }

    private fun builtIn(id: String) = SceneObjectCatalog.layoutFor(id, ThemeCatalog.byId(id).accentColor)

    @Test
    fun `the near row repeats the street a third of a tile on, without its towers and places`() {
        for (theme in ThemeCatalog.ALL) {
            val layout = builtIn(theme.id)
            val plan = SceneObjectCatalog.nearRowOf(layout)
            assertTrue(theme.id, plan.objects.isNotEmpty())
            for (e in plan.objects) {
                assertTrue(theme.id, e.source in layout.staticObjects)
                assertFalse("${theme.id}: a tower or a place in the near row", e.spec.type == SceneObjectType.SKYSCRAPER)
                assertEquals(theme.id, (e.source.tileFractionX + SceneObjectCatalog.NEAR_ROW_SHIFT) % 1f, e.spec.tileFractionX, 0f)
                assertEquals(theme.id, e.source.depthFraction, e.spec.depthFraction, 0f)
                assertEquals(theme.id, e.source.type, e.spec.type)
                if (e.spec.type == SceneObjectType.HOUSE) assertEquals(StaticSceneObject.UNDEALT, e.spec.silhouette)
            }
            // Every kind the street has, but the buildings, is in it.
            val kinds = layout.staticObjects.map { it.type }.toSet() - SceneObjectType.SKYSCRAPER
            assertEquals(theme.id, kinds, plan.objects.map { it.spec.type }.toSet())
        }
    }

    @Test
    fun `two shops of the street's places, nearest half a tile apart, and only the shops' group stands there`() {
        for (theme in ThemeCatalog.ALL) {
            val layout = builtIn(theme.id)
            val places = StorefrontStreet.places(layout)
            val plan = SceneObjectCatalog.nearRowOf(layout)
            assertEquals(theme.id, 2, plan.shops.size)
            assertTrue(theme.id, plan.shopSources.all { it in places })
            val d = kotlin.math.abs(plan.shopSources[1].tileFractionX - plan.shopSources[0].tileFractionX)
            val gap = minOf(d, 1f - d)
            for (i in places.indices) for (j in i + 1 until places.size) {
                val dd = places[j].tileFractionX - places[i].tileFractionX
                assertTrue("${theme.id}: a pair nearer half a tile", minOf(dd, 1f - dd) <= gap + 1e-6f)
            }
            for (k in plan.shops.indices) {
                assertEquals((plan.shopSources[k].tileFractionX + SceneObjectCatalog.NEAR_ROW_SHIFT) % 1f, plan.shops[k].tileFractionX, 0f)
            }
        }
        val scratch = IntArray(3)
        for (seed in listOf(0, 1, -7, "autumn".hashCode(), "city".hashCode(), 123456789)) {
            var previous: Storefront? = null
            for (place in -60..600) {
                val shop = SceneObjectCatalog.nearShopAt(seed, place, scratch)
                assertEquals("seed $seed place $place", StorefrontGroup.SHOPS, shop.group)
                assertTrue("seed $seed: twice in a row at $place", shop != previous)
                assertEquals("the same place, the same shop", shop, SceneObjectCatalog.nearShopAt(seed, place, IntArray(3)))
                previous = shop
            }
            // Each deck of three holds all three.
            for (deck in -5..50) {
                assertEquals(3, (0 until 3).map { SceneObjectCatalog.nearShopAt(seed, deck * 3 + it, scratch) }.toSet().size)
            }
        }
    }

    /**
     * **On the screen, never the same shop twice in a row** (decision 96): copy after copy along the scroll, each near place
     * drawing what the renderer deals it -- `nearShopAt(seed, placeIndex(copy, rank, places))` at `copy + shops[rank].tileFractionX`
     * --, read in the order the places stand, on the twelve built-in streets and on random ones. A place moved past the
     * tile's end stands first in its copy.
     */
    @Test
    fun `the near shops on the screen are never the same twice in a row`() {
        // A random theme with more than one car needs Android's HSV on the JVM: those are skipped, and the ones read counted.
        val random = (1..600).mapNotNull { n -> runCatching { "random:$n" to SceneObjectCatalog.layoutFor("random:$n", 0) }.getOrNull() }
        assertTrue("random streets read: ${random.size} of 600", random.size >= 300)
        val streets = ThemeCatalog.ALL.map { it.id to builtIn(it.id) } + random
        val scratch = IntArray(3)
        var checked = 0
        for ((id, layout) in streets) {
            val plan = SceneObjectCatalog.nearRowOf(layout)
            val n = plan.shops.size
            if (n < 2) continue
            checked++
            val shown = (-3 until 40).flatMap { copy -> (0 until n).map { rank -> copy to rank } }
                .sortedBy { (copy, rank) -> copy + plan.shops[rank].tileFractionX.toDouble() }
                .map { (copy, rank) -> SceneObjectCatalog.nearShopAt(id.hashCode(), StorefrontStreet.placeIndex(copy, rank, n), scratch) }
            for (i in 1 until shown.size) assertTrue("$id: ${shown[i]} twice in a row on the screen", shown[i] != shown[i - 1])
        }
        assertTrue("streets with two near shops checked: $checked, of them random ${checked - 12}", checked >= 12 + 10)
    }

    /** No shop's front is crossed in the near row, and no two houses stand one over the other, measured as the plan does. */
    @Test
    fun `a near shop's front is clear and no two near houses cover half of each other`() {
        val streets = ThemeCatalog.ALL.map { it.id to builtIn(it.id) } +
            (1..300).mapNotNull { n -> runCatching { "random:$n" to SceneObjectCatalog.layoutFor("random:$n", 0) }.getOrNull() }
        for ((id, layout) in streets) {
            val plan = SceneObjectCatalog.nearRowOf(layout)
            for (shop in plan.shops) {
                val front = extent(shop)
                for (e in plan.objects) {
                    val reach = extent(e.spec)
                    assertEquals("$id: ${e.spec.type} at ${e.spec.tileFractionX} across the shop at ${shop.tileFractionX}", 0f, overlap(e.spec, reach, shop, front), 0.01f)
                }
            }
            val houses = plan.objects.filter { it.spec.type == SceneObjectType.HOUSE }
            for (i in houses.indices) for (j in i + 1 until houses.size) {
                val a = extent(houses[i].spec)
                val b = extent(houses[j].spec)
                val narrower = minOf(a[1] - a[0], b[1] - b[0])
                assertTrue("$id: two near houses over each other", overlap(houses[i].spec, a, houses[j].spec, b) <= narrower / 2f + 0.01f)
            }
        }
    }

    @Test
    fun `a saved street's near row is planned from its own layout, and one with no place has no shop`() {
        val entry = snapshotEntry("custom:near", "Autumn saved", "autumn", defaultCustomizationFor("autumn"), "autumn")
        CustomThemeRegistry.update(CustomThemeData(customThemes = listOf(entry)))
        val saved = SceneObjectCatalog.layoutFor(entry.id, 0)
        val plan = SceneObjectCatalog.nearRowOf(saved)
        assertTrue(plan.objects.all { it.source in saved.staticObjects })
        assertTrue(plan.objects.isNotEmpty())
        val noPlaces = saved.copy(staticObjects = saved.staticObjects.filter { !StorefrontStreet.isPlace(it) })
        assertTrue(SceneObjectCatalog.nearRowOf(noPlaces).shops.isEmpty())
        val one = saved.copy(staticObjects = saved.staticObjects.filter { !StorefrontStreet.isPlace(it) } + StorefrontStreet.places(saved).take(1))
        assertEquals(1, SceneObjectCatalog.nearRowOf(one).shops.size)
    }

    /**
     * Read off the renderer: the near row is drawn after the cars, its objects back to front then its pavement's people, the
     * meadow first; it keeps what the street's switches and amounts keep of the candidates it repeats; the gallery card and
     * the strip of *World & scene* draw none of it.
     */
    @Test
    fun `the renderer draws the near row after the cars, by the street's switches, and the card draws none`() {
        val text = source("SceneObjectRenderer.kt")
        val draw = text.substring(text.indexOf("    fun draw(canvas: SceneCanvas, geom: GroundGeometry"), text.indexOf("    private fun drawNearRow("))
        val order = listOf(
            "drawPeople(canvas, geom, screenWidth, screenHeight, elapsedSeconds, dayBlend, streetPavement)",
            "drawCar(canvas, c, screenWidth, screenHeight, dayBlend)",
            "drawGroundFlowers(canvas, geom, screenWidth, screenHeight, near = true)",
            "drawGroundPiles(canvas, geom, screenWidth, screenHeight, near = true)",
            "drawNearRow(canvas, geom, dayBlend, elapsedSeconds, screenWidth, screenHeight)",
            "drawPeople(canvas, geom, screenWidth, screenHeight, elapsedSeconds, dayBlend, nearPavement)",
        ).map { draw.indexOf(it).also { at -> assertTrue("missing: $it", at >= 0) } }
        assertEquals("the order", order.sorted(), order)
        assertTrue(text.contains(".filter { customization.keepCandidate(it.source, layout.densityScheme) }"))
        assertTrue(text.contains(".filter { k -> customization.keepCandidate(nearPlan.shopSources[k], layout.densityScheme) }"))
        assertTrue(text.contains("nearRuntimes = buildNearRuntimes()"))
        assertTrue(text.contains("val groundY = screenHeight * SceneSpace.nearRowFeetYFraction(r.spec.depthFraction)"))
        for (file in listOf("ThemePreviewScene.kt")) {
            val card = source(file)
            assertFalse("$file draws the near row", Regex("""NEAR_ROW|nearRow|NearRow|NEAR_PAVEMENT|NEAR_MEADOW""").containsMatchIn(card))
        }
    }

    /** The hills' paper reaches the screen's bottom under the near row, and the birds fly the upper sky lifted with the ground. */
    @Test
    fun `the hills reach the bottom of the screen and the birds are lifted with the ground`() {
        val text = source("PaperRenderer.kt")
        assertTrue(text.contains("private fun hillPaperBottom(top: Float, height: Float): Float = maxOf(top + height, screenHeight.toFloat())"))
        assertTrue(text.contains("path.moveTo(startX - seam, bottom)"))
        assertTrue(text.contains("path.lineTo(startX + width + seam, bottom)"))
        assertEquals("the band's bottom is above the screen's", true, SceneSpace.HILL_LAYER_TOP_FRACTION + SceneSpace.HILL_LAYER_HEIGHT_FRACTION < 1f)
        assertTrue(text.contains("val laneFraction = CandidateNoise.range(seed, i, CandidateNoise.CH_Y, 0.00f, 0.30f)"))
    }

    private fun extent(o: StaticSceneObject): FloatArray {
        val boxes = SceneObjectCatalog.occluderBoxes(o)
        if (boxes.isEmpty()) return floatArrayOf(0f, 0f)
        val x = o.tileFractionX * 1080f * 2f
        val k = SceneSpace.NEAR_ROW_SIZE / SceneSpace.depthScale(o.depthFraction)
        return floatArrayOf(boxes.minOf { it[0] - x } * k, boxes.maxOf { it[2] - x } * k)
    }

    private fun overlap(a: StaticSceneObject, ea: FloatArray, b: StaticSceneObject, eb: FloatArray): Float {
        val tile = 2160f
        var d = (b.tileFractionX - a.tileFractionX) * tile
        d -= Math.round(d / tile) * tile
        return maxOf(0f, minOf(ea[1], d + eb[1]) - maxOf(ea[0], d + eb[0]))
    }

    private fun source(name: String): String {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/engine/$name"
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = File(dir, "$prefix$suffix")
                if (f.isFile) return f.readText()
            }
            dir = dir.parentFile
        }
        error("$suffix not found")
    }
}
