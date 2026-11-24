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
 * **The scene lifted and the near row below the road** (`DESIGN_NOTES.md` §12, decisions 96 and 105): every line of the
 * ground 0.08 of the screen higher, the projection's ratios unchanged, and under the road the street seen again -- its
 * houses, trees, parasols and decorations once, a house another covers by half left out, two of its places with the six
 * buildings, each lower and larger the nearer it is in the street (decision 119), with a pavement of its own --
 * planned from the whole layout at one reference size ([SceneObjectCatalog.nearRowOf]) and dealt its buildings two in a
 * row never alike and never the one above ([SceneObjectCatalog.nearBuildingAt]).
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
        assertEquals(0.838f, SceneSpace.NEAR_ROW_FEET_TOP_Y_FRACTION, 0f)
        assertEquals(0.922f, SceneSpace.NEAR_ROW_FEET_BOTTOM_Y_FRACTION, 0f)
        assertEquals(0.838f, SceneSpace.nearRowFeetYFraction(0f), 0f)
        assertEquals(0.922f, SceneSpace.nearRowFeetYFraction(1f), 1e-6f)
        assertEquals(0.880f, SceneSpace.nearRowFeetYFraction(0.5f), 1e-6f)
        assertEquals(0.930f, SceneSpace.NEAR_PAVEMENT_FAR_Y_FRACTION, 0f)
        // The near pavement's rows are the pavement's, moved down: its nearest walker still on the screen.
        val deepest = SceneSpace.PAVEMENT_NEAR_Y_FRACTION + 0.012f + SceneSpace.NEAR_PAVEMENT_SHIFT_FRACTION
        assertTrue("the near pavement's deepest walker at $deepest", deepest < 1f)
        // Every foot of the row above the near pavement, so its people walk in front of the whole row.
        assertTrue(SceneSpace.NEAR_PAVEMENT_FAR_Y_FRACTION > SceneSpace.NEAR_ROW_FEET_BOTTOM_Y_FRACTION)
        assertTrue(SceneSpace.NEAR_MEADOW_BOTTOM_Y_FRACTION <= 1f)
        assertEquals(0.48f, SceneSpace.NEAR_ROW_SIZE_TOP, 0f)
        assertEquals(0.74f, SceneSpace.NEAR_ROW_SIZE_BOTTOM, 0f)
        assertEquals(0.72f, SceneSpace.NEAR_ROW_PLAN_SIZE, 0f)
        assertEquals(0.9f, SceneSpace.NEAR_PEOPLE_SIZE, 0f)
        assertEquals(0.82f, SceneSpace.NEAR_MEADOW_TOP_Y_FRACTION, 0f)
        assertEquals(0.99f, SceneSpace.NEAR_MEADOW_BOTTOM_Y_FRACTION, 0f)
    }

    /**
     * **The town below the road on several levels** (decisions 96 and 119): the size of a line below the road grows
     * with the line, linear, from 0.48 at the row's top line of feet to 0.74 at its bottom one, held at those two past
     * them (the meadow above and below the row), and never larger than 0.74, the largest `SpriteDrawScaleTest` measures.
     */
    @Test
    fun `the size below the road grows with the line of feet, from 0 48 at the top line to never more than 0 74`() {
        val top = SceneSpace.NEAR_ROW_FEET_TOP_Y_FRACTION
        val bottom = SceneSpace.NEAR_ROW_FEET_BOTTOM_Y_FRACTION
        assertEquals(0.48f, SceneSpace.nearRowSizeAt(top), 0f)
        assertEquals(0.74f, SceneSpace.nearRowSizeAt(bottom), 1e-6f)
        assertEquals(0.61f, SceneSpace.nearRowSizeAt((top + bottom) / 2f), 1e-5f)
        // Linear with the line: equal steps of the line, equal steps of the size.
        for (k in 0..10) {
            val y = top + (bottom - top) * k / 10f
            assertEquals("at $y", 0.48f + 0.026f * k, SceneSpace.nearRowSizeAt(y), 1e-5f)
        }
        // Over the whole meadow below the road and past it, line by line: never smaller, never above 0.74, never under 0.48.
        var previous = 0f
        var y = 0.80f
        while (y <= 1.0f) {
            val size = SceneSpace.nearRowSizeAt(y)
            assertTrue("at $y: $size after $previous", size >= previous)
            assertTrue("at $y: $size over 0.74", size <= 0.74f)
            assertTrue("at $y: $size under 0.48", size >= 0.48f)
            previous = size
            y += 0.0005f
        }
        assertEquals("held above the top line", 0.48f, SceneSpace.nearRowSizeAt(SceneSpace.NEAR_MEADOW_TOP_Y_FRACTION), 0f)
        assertEquals("held below the bottom line", 0.74f, SceneSpace.nearRowSizeAt(SceneSpace.NEAR_MEADOW_BOTTOM_Y_FRACTION), 0f)
        // The row's objects: deeper in the street, lower and larger, a house from 0.28 at about 0.55.
        var lastFeet = 0f
        var lastSize = 0f
        for (d in 0..100) {
            val feet = SceneSpace.nearRowFeetYFraction(d / 100f)
            val size = SceneSpace.nearRowSizeAt(feet)
            assertTrue("depth ${d / 100f}", feet > lastFeet && size > lastSize)
            lastFeet = feet
            lastSize = size
        }
        assertEquals(0.5528f, SceneSpace.nearRowSizeAt(SceneSpace.nearRowFeetYFraction(0.28f)), 1e-4f)
    }

    @Test
    fun `a near-row object is the size table's at the reference line times the size of its line of feet`() {
        val house = StaticSceneObject(SceneObjectType.HOUSE, depthFraction = 0.9f, tileFractionX = 0.3f, scale = 1.04f)
        val tree = StaticSceneObject(SceneObjectType.TREE, depthFraction = 0.2f, tileFractionX = 0.6f)
        for (spec in listOf(house, tree)) {
            val v = SceneObjectRenderer.variantFor(spec)
            val size = 0.48f + (0.74f - 0.48f) * spec.depthFraction
            assertEquals(v.baseScale * spec.scale * size * 0.6f, SceneObjectRenderer.nearScaleFor(spec, 1440f), 1e-5f)
        }
        assertTrue(
            "nearer in the street, larger",
            SceneObjectRenderer.nearScaleFor(tree.copy(depthFraction = 0.95f), 1440f) > SceneObjectRenderer.nearScaleFor(tree, 1440f),
        )
        assertEquals(
            "the largest: 0.74 at depth 1",
            SceneSpace.SceneVariant.TREE.baseScale * 0.74f * 0.6f, SceneObjectRenderer.nearScaleFor(tree.copy(depthFraction = 1f), 1440f), 1e-5f,
        )
    }

    /**
     * **A place below the road is never drawn under [SceneObjectRenderer.placeScaleFloor]**, and at the shallowest depth a
     * place stands at (0.30, [SceneSpace.BUILDING_TOWER_MAX_DEPTH]) the floor is under the size the row gives it for all
     * three of the places' rows, at the smallest roll a place is generated with (a random theme's, 0.82): the floor never
     * binds on a generated street, and a building's 2.4-unit details stay 2 px or more.
     */
    @Test
    fun `a place at depth 0 30 stands above the place floor for the restaurant, the school and the bar`() {
        val depth = SceneSpace.BUILDING_TOWER_MAX_DEPTH
        val size = SceneSpace.nearRowSizeAt(SceneSpace.nearRowFeetYFraction(depth))
        assertEquals(0.558f, size, 1e-4f)
        for (height in listOf(1440f, 2340f, 3200f)) {
            for (variant in listOf(SceneSpace.SceneVariant.RESTAURANT, SceneSpace.SceneVariant.SCHOOL, SceneSpace.SceneVariant.BAR)) {
                val floor = SceneObjectRenderer.placeScaleFloor(variant, height)
                assertTrue("$variant: no floor", floor > 0f)
                for (roll in listOf(0.82f, 1f)) {
                    val drawn = variant.baseScale * roll * size * SceneSpace.sceneScale(height)
                    println("NEAR_PLACE_FLOOR $variant at ${height.toInt()} px, roll $roll: drawn / floor = ${"%.3f".format(drawn / floor)}")
                    assertTrue("$variant at ${height.toInt()} px, roll $roll: drawn $drawn under the floor $floor", drawn > floor)
                }
            }
            // A real place at that depth, a restaurant's row: drawn as the row makes it, the floor not reached.
            val place = StaticSceneObject(SceneObjectType.SKYSCRAPER, depthFraction = depth, tileFractionX = 0.4f, scale = 0.82f)
            assertEquals(SceneSpace.SceneVariant.RESTAURANT, SceneObjectRenderer.variantFor(place))
            assertEquals(
                SceneSpace.SceneVariant.RESTAURANT.baseScale * 0.82f * size * SceneSpace.sceneScale(height),
                SceneObjectRenderer.nearScaleFor(place, height), 1e-6f,
            )
        }
        // And the floor holds where a roll would take a place under it.
        val tiny = StaticSceneObject(SceneObjectType.SKYSCRAPER, depthFraction = depth, tileFractionX = 0.4f, scale = 0.1f)
        assertEquals(
            SceneObjectRenderer.placeScaleFloor(SceneSpace.SceneVariant.RESTAURANT, 1440f),
            SceneObjectRenderer.nearScaleFor(tiny, 1440f), 0f,
        )
    }

    /**
     * **The plan is measured at 0.72, not at the drawn size** (decision 119; [SceneObjectCatalog.nearRowOf]): how
     * far each object reaches, which decides what stands and which building a place deals, is its occluding boxes brought
     * to [SceneSpace.NEAR_ROW_PLAN_SIZE], so the same objects and buildings stand there on every line. Checked on objects
     * whose drawn size is far from 0.72 (a house at depth 0.28, drawn at about 0.55; one at 0.95, about 0.73; a place at
     * 0.30, about 0.56), each told apart from what the drawn size would measure.
     */
    @Test
    fun `the near row's plan is measured at the plan size 0 72, never at the size each is drawn at`() {
        val specs = listOf(
            StaticSceneObject(SceneObjectType.HOUSE, depthFraction = 0.28f, tileFractionX = 0.25f),
            StaticSceneObject(SceneObjectType.HOUSE, depthFraction = 0.95f, tileFractionX = 0.55f, scale = 1.05f),
            StaticSceneObject(SceneObjectType.TREE, depthFraction = 0.18f, tileFractionX = 0.7f),
            StaticSceneObject(SceneObjectType.SKYSCRAPER, depthFraction = 0.30f, tileFractionX = 0.4f),
        )
        for (spec in specs) {
            val measured = SceneObjectCatalog.nearExtent(spec)!!
            val atPlan = extentAt(spec, 0.72f)
            val atDrawn = extentAt(spec, SceneSpace.nearRowSizeAt(SceneSpace.nearRowFeetYFraction(spec.depthFraction)))
            assertEquals("${spec.type} at ${spec.depthFraction}: left", atPlan[0], measured[0], 1e-3f)
            assertEquals("${spec.type} at ${spec.depthFraction}: right", atPlan[1], measured[1], 1e-3f)
            assertTrue(
                "${spec.type} at ${spec.depthFraction}: the drawn size measures otherwise (${atDrawn[1] - atDrawn[0]} against ${measured[1] - measured[0]})",
                Math.abs((atDrawn[1] - atDrawn[0]) - (measured[1] - measured[0])) > 0.5f,
            )
        }
    }

    private fun builtIn(id: String) = SceneObjectCatalog.layoutFor(id, ThemeCatalog.byId(id).accentColor)

    /**
     * **The street once** (decision 105): every candidate but the towers and the places, a third of a tile on, at most once;
     * a house left out only where it would cross a place's front from in front of it, or where a house nearer in the street,
     * kept, covers more than half of the narrower of the two -- worked out here apart from the plan, nearest first.
     */
    @Test
    fun `the near row repeats the street once, a third of a tile on, and leaves out a house another covers by half`() {
        var covered = 0
        for (theme in ThemeCatalog.ALL) {
            val layout = builtIn(theme.id)
            val plan = SceneObjectCatalog.nearRowOf(layout)
            assertTrue(theme.id, plan.objects.isNotEmpty())
            for (e in plan.objects) {
                assertTrue(theme.id, e.source in layout.staticObjects)
                assertFalse("${theme.id}: a tower or a place in the near row", e.spec.type == SceneObjectType.SKYSCRAPER)
                assertEquals("${theme.id}: one pass", (e.source.tileFractionX + SceneObjectCatalog.NEAR_ROW_SHIFT) % 1f, e.spec.tileFractionX, 0f)
                assertEquals(theme.id, e.source.depthFraction, e.spec.depthFraction, 0f)
                assertEquals(theme.id, e.source.type, e.spec.type)
                if (e.spec.type == SceneObjectType.HOUSE) assertEquals(StaticSceneObject.UNDEALT, e.spec.silhouette)
            }
            assertEquals("${theme.id}: each candidate once", plan.objects.size, plan.objects.map { System.identityHashCode(it.source) }.toSet().size)
            // Every kind the street has, but the buildings, is in it.
            val kinds = layout.staticObjects.map { it.type }.toSet() - SceneObjectType.SKYSCRAPER
            assertEquals(theme.id, kinds, plan.objects.map { it.spec.type }.toSet())
            // The houses, nearest in the street first: each stands unless it crosses a place's front from in front, or a
            // house kept before it covers more than half of the narrower.
            val standing = ArrayList<StaticSceneObject>()
            for (h in layout.staticObjects.filter { it.type == SceneObjectType.HOUSE }.sortedByDescending { it.depthFraction }) {
                val spec = h.copy(tileFractionX = (h.tileFractionX + SceneObjectCatalog.NEAR_ROW_SHIFT) % 1f, silhouette = StaticSceneObject.UNDEALT)
                val acrossAPlace = plan.shops.any { shop -> spec.depthFraction > shop.depthFraction && overlap(spec, extent(spec), shop, extent(shop)) > 0f }
                if (acrossAPlace) continue
                val hidden = standing.any { o ->
                    val narrower = minOf(extent(spec).let { it[1] - it[0] }, extent(o).let { it[1] - it[0] })
                    overlap(spec, extent(spec), o, extent(o)) > narrower / 2f
                }
                if (hidden) covered++ else standing += spec
                assertEquals("${theme.id}: the house at ${h.tileFractionX}", !hidden, plan.objects.any { it.source === h })
            }
        }
        assertTrue("the rule leaves out a house somewhere among the twelve: $covered", covered > 0)
    }

    /**
     * **Two places, half a tile apart** (decision 105): of the street's places, the two standing nearest half a tile apart
     * (the one of a street with one, none of a street with none), a third of a tile on, in their order across the tile.
     */
    @Test
    fun `two places of the street stand there, half a tile apart, and the six buildings take turns, never two alike in a row`() {
        for (theme in ThemeCatalog.ALL) {
            val layout = builtIn(theme.id)
            val places = StorefrontStreet.places(layout)
            val plan = SceneObjectCatalog.nearRowOf(layout)
            assertEquals(theme.id, minOf(2, places.size), plan.shops.size)
            assertTrue(theme.id, places.containsAll(plan.shopSources))
            val gap = { a: StaticSceneObject, b: StaticSceneObject -> (b.tileFractionX - a.tileFractionX).let { d -> minOf(Math.abs(d), 1f - Math.abs(d)) } }
            val widest = places.indices.flatMap { i -> (i + 1 until places.size).map { j -> gap(places[i], places[j]) } }.max()
            assertEquals("${theme.id}: the two nearest half a tile apart", widest, gap(plan.shopSources[0], plan.shopSources[1]), 1e-6f)
            assertTrue("${theme.id}: in their order across the tile", plan.shops[0].tileFractionX < plan.shops[1].tileFractionX)
            for (k in plan.shops.indices) {
                assertEquals((plan.shopSources[k].tileFractionX + SceneObjectCatalog.NEAR_ROW_SHIFT) % 1f, plan.shops[k].tileFractionX, 0f)
            }
        }
        val scratch = IntArray(3)
        for (seed in listOf(0, 1, -7, "autumn".hashCode(), "city".hashCode(), 123456789)) {
            var previous: Storefront? = null
            val seen = HashSet<Storefront>()
            for (place in -60..600) {
                val free = SceneObjectCatalog.nearInHalf(seed, place, 0, scratch)!!
                assertTrue("seed $seed: twice in a row at $place", free != previous)
                assertEquals("the same place, the same building", free, SceneObjectCatalog.nearInHalf(seed, place, 0, IntArray(3)))
                previous = free
                seen += free
                // Whatever stands over it, never that; none only where its whole half does.
                for (over in 0 until (1 shl 6)) {
                    val dealt = SceneObjectCatalog.nearInHalf(seed, place, over, scratch)
                    if (dealt == null) {
                        // Its half: the buildings it takes when every other one stands over it.
                        val half = (0 until 6).filter { o -> SceneObjectCatalog.nearInHalf(seed, place, 63 xor (1 shl o), scratch)?.ordinal == o }
                        assertEquals("seed $seed place $place: none under $over", 3, half.count { over and (1 shl it) != 0 })
                    } else {
                        assertEquals("seed $seed place $place: $dealt under $over", 0, over and (1 shl dealt.ordinal))
                    }
                }
            }
            assertEquals("seed $seed: all six come round", Storefront.ALL.toSet(), seen)
        }
    }

    /**
     * **On the screen** (decision 105), copy after copy along the scroll: each near place drawing what the renderer deals it,
     * read in the order the places stand, on the twelve built-in streets and on random ones -- never the same building
     * twice in a row, and never the building of a street place whose drawn width shares a pixel of the screen's width
     * with it, measured here apart from the plan: the street's place at its depth, the near place at the near row's plan size.
     */
    @Test
    fun `below the road a building is never the one before it nor the one above it on the screen`() {
        // A random theme with more than one car needs Android's HSV on the JVM: those are skipped, and the ones read counted.
        val random = (1..600).mapNotNull { n -> runCatching { "random:$n" to SceneObjectCatalog.layoutFor("random:$n", 0) }.getOrNull() }
        assertTrue("random streets read: ${random.size} of 600", random.size >= 300)
        val streets = ThemeCatalog.ALL.map { it.id to builtIn(it.id) } + random
        val scratch = IntArray(3)
        var checked = 0
        var overs = 0
        var crowded = 0
        for ((id, layout) in streets) {
            val plan = SceneObjectCatalog.nearRowOf(layout)
            val places = StorefrontStreet.places(layout)
            val n = plan.shops.size
            if (n < 1) continue
            checked++
            val seed = id.hashCode()
            fun street(copy: Int, rank: Int) = StorefrontStreet.storefrontAt(seed, StorefrontStreet.placeIndex(copy, rank, places.size))
            val deal = SceneObjectCatalog.StreetDeal { copy, rank -> street(copy, rank) }
            fun near(copy: Int, rank: Int): Storefront = SceneObjectCatalog.nearBuilding(seed, plan, copy, rank, deal, scratch)
            val shown = (-3 until 40).flatMap { copy -> (0 until n).map { rank -> copy to rank } }
                .sortedBy { (copy, rank) -> copy + plan.shops[rank].tileFractionX.toDouble() }
            for (i in 1 until shown.size) {
                assertTrue("$id: twice in a row on the screen", near(shown[i].first, shown[i].second) != near(shown[i - 1].first, shown[i - 1].second))
            }
            for ((copy, rank) in shown) {
                val nearShop = plan.shops[rank]
                val nearReach = extent(nearShop)
                val dealt = near(copy, rank)
                for (j in places.indices) for (dc in -1..1) {
                    val p = places[j]
                    val boxes = SceneObjectCatalog.occluderBoxes(p)
                    val px = p.tileFractionX * 2160f
                    val d = (copy + dc + p.tileFractionX - copy - nearShop.tileFractionX) * 2160f
                    val left = maxOf(nearReach[0], d + boxes.minOf { it[0] - px })
                    val right = minOf(nearReach[1], d + boxes.maxOf { it[2] - px })
                    if (right > left) {
                        overs++
                        assertTrue("$id copy $copy rank $rank: $dealt under the same", dealt != street(copy + dc, j))
                    }
                }
            }
            if (plan.above.any { it.size > 4 }) crowded++
        }
        assertTrue("streets checked: $checked, of them random ${checked - 12}", checked >= 12 + 10)
        assertTrue("near places with a street place over them: $overs", overs > 100)
        println("streets with three street places over one near place: $crowded")
    }

    /** No place's front is crossed in the near row by what stands in front of it, measured as the plan does. */
    @Test
    fun `a near place's front is clear of what stands in front of it`() {
        val streets = ThemeCatalog.ALL.map { it.id to builtIn(it.id) } +
            (1..300).mapNotNull { n -> runCatching { "random:$n" to SceneObjectCatalog.layoutFor("random:$n", 0) }.getOrNull() }
        var behind = 0
        for ((id, layout) in streets) {
            val plan = SceneObjectCatalog.nearRowOf(layout)
            for (shop in plan.shops) {
                val front = extent(shop)
                for (e in plan.objects) {
                    val o = overlap(e.spec, extent(e.spec), shop, front)
                    if (e.spec.depthFraction > shop.depthFraction) {
                        assertEquals("$id: ${e.spec.type} at ${e.spec.tileFractionX} across the place at ${shop.tileFractionX}", 0f, o, 0.01f)
                    } else if (o > 0f) {
                        behind++
                    }
                }
            }
        }
        assertTrue("what stands behind a place stays: $behind", behind > 0)
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

    /** How far [o] reaches left and right of its foot in the near row, as the plan measures it: at the plan size, 0.72. */
    private fun extent(o: StaticSceneObject): FloatArray = extentAt(o, SceneSpace.NEAR_ROW_PLAN_SIZE)

    /** How far [o] reaches left and right of its foot, its occluding boxes brought from its depth's projection to [size]. */
    private fun extentAt(o: StaticSceneObject, size: Float): FloatArray {
        val boxes = SceneObjectCatalog.occluderBoxes(o)
        if (boxes.isEmpty()) return floatArrayOf(0f, 0f)
        val x = o.tileFractionX * 1080f * 2f
        val k = size / SceneSpace.depthScale(o.depthFraction)
        return floatArrayOf(boxes.minOf { it[0] - x } * k, boxes.maxOf { it[2] - x } * k)
    }

    private fun overlap(a: StaticSceneObject, ea: FloatArray, b: StaticSceneObject, eb: FloatArray): Float {
        val tile = 2160f
        var d = (b.tileFractionX - a.tileFractionX) * tile
        d -= Math.round(d / tile) * tile
        return maxOf(0f, minOf(ea[1], d + eb[1]) - maxOf(ea[0], d + eb[0]))
    }

    /**
     * **The meadow below the road takes the size of the line each flower or pile stands on** (decision 119): the renderer
     * sizes them with [SceneSpace.nearRowSizeAt] of the meadow line their depth puts them on, from the meadow's top to its
     * bottom, not the plan's fixed size. Read off the renderer as whole lines, its scatter being private; the instrumented
     * `GroundScatterAcrossTheTileTest` measures the blits themselves.
     */
    @Test
    fun `the meadow below the road is sized by the line each flower stands on`() {
        val lines = source("SceneObjectRenderer.kt").lines().map { it.trim() }
        assertTrue("scatterSize near", "if (near) SceneSpace.nearRowSizeAt(nearMeadowYFraction(jitterDepth))" in lines)
        assertTrue("the meadow's line", "SceneSpace.NEAR_MEADOW_TOP_Y_FRACTION +" in lines)
        assertTrue(
            "from top to bottom by depth",
            "(SceneSpace.NEAR_MEADOW_BOTTOM_Y_FRACTION - SceneSpace.NEAR_MEADOW_TOP_Y_FRACTION) * jitterDepth" in lines,
        )
        // The sizes it gives: smaller at the meadow's top (near the road), larger at its bottom, inside the row's two sizes.
        val top = SceneSpace.nearRowSizeAt(SceneSpace.NEAR_MEADOW_TOP_Y_FRACTION)
        val bottom = SceneSpace.nearRowSizeAt(SceneSpace.NEAR_MEADOW_BOTTOM_Y_FRACTION)
        assertTrue("$top < $bottom", top < bottom)
        assertTrue(top >= SceneSpace.NEAR_ROW_SIZE_TOP && bottom <= SceneSpace.NEAR_ROW_SIZE_BOTTOM)
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

    /**
     * Where the three buildings of a place's half all stand over it -- three street places over one near place -- it takes
     * one of the other half that neither neighbour holds ([SceneObjectCatalog.nearBuilding]).
     */
    @Test
    fun `a near place under its whole half takes the other half, never a neighbour's`() {
        val place = StaticSceneObject(SceneObjectType.SKYSCRAPER, depthFraction = 0.6f, tileFractionX = 0.2f)
        val shops = listOf(place, place.copy(tileFractionX = 0.5f), place.copy(tileFractionX = 0.8f))
        // Near place 0 (copy 0, rank 0) under the street's ranks 0, 1 and 2 of its own copy.
        val plan = SceneObjectCatalog.NearRowPlan(emptyList(), shops, shops, arrayOf(intArrayOf(0, 0, 1, 0, 2, 0), intArrayOf(), intArrayOf()))
        val scratch = IntArray(3)
        for (seed in listOf(0, 1, -7, 99, "autumn".hashCode())) {
            // The street deals, over near place 0, the three buildings of its half.
            val half = (0 until 6).map { Storefront.ALL[it] }.filter { b ->
                SceneObjectCatalog.nearInHalf(seed, 0, 63 xor (1 shl b.ordinal), scratch) == b
            }
            assertEquals(3, half.size)
            val deal = SceneObjectCatalog.StreetDeal { _, rank -> half[rank] }
            val dealt = SceneObjectCatalog.nearBuilding(seed, plan, 0, 0, deal, scratch)
            assertTrue("seed $seed: $dealt is over it", dealt !in half)
            val before = SceneObjectCatalog.nearBuilding(seed, plan, -1, 2, deal, scratch)
            val after = SceneObjectCatalog.nearBuilding(seed, plan, 0, 1, deal, scratch)
            assertTrue("seed $seed: $dealt beside $before and $after", dealt != before && dealt != after)
        }
    }
}
