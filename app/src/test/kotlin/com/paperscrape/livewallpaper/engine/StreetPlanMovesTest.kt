package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **What the plan of the five places moves, and what it may not** (v5.13B2): the places along the whole tile,
 * and where a place needed it a tree or a parasol out of its way, never their number -- the instruction the
 * round was given. Every built-in street as the table lays it out against the street as generated, object by
 * object.
 *
 * A moved tree is the tree it was: the same kind, depth, size and silhouette, standing at its theme's density
 * as it did (and within a fiftieth of its density draw, so at any other density as it did unless the slider
 * sits right on it), the same one of its two colours, the same autumn tone with the fall colours on (Autumn's
 * default; [SceneObjectRenderer.fallToneOf], read from the position like the others), and a fir under the
 * Christmas layer exactly when it was.
 * A moved parasol stands on the other side of its own house, at the side offset give or take a few
 * thousandths, the same way. Nothing else moves: houses, towers and decorations stand where the generator put
 * them. And the number of each standing at every theme's defaults is the number it was.
 */
class StreetPlanMovesTest {

    private val themes = listOf(
        "sunset", "autumn", "winter", "desert", "christmas", "new_year",
        "beach", "city", "tundra", "easter", "halloween", "spring",
    )

    @Test
    fun `only places, trees and parasols move, and a moved tree or parasol is the one it was`() {
        val report = StringBuilder("STREET_MOVES theme: what moved, from -> to (share of the tile), in screens\n")
        var movedTrees = 0
        var movedParasols = 0
        for ((id, tree) in SceneObjectCatalog.BUILT_IN_STREETS) {
            val c = defaultCustomizationFor(id)
            val generated = SceneObjectCatalog.streetBeforeShops(id, tree)
            val laidOut = SceneObjectCatalog.layoutFor(id, 0xFF8899AA.toInt()).staticObjects.subList(0, generated.size)
            val houses = generated.filter { it.type == SceneObjectType.HOUSE }
            val parasols = generated.filter { it.type == SceneObjectType.PARASOL }
            for (i in generated.indices) {
                val a = generated[i]
                val b = laidOut[i]
                if (a == b) continue
                assertEquals("$id #$i: only the position moves", a.copy(tileFractionX = b.tileFractionX), b)
                when (a.type) {
                    SceneObjectType.SKYSCRAPER -> {
                        assertTrue("$id #$i: a tower moved", StorefrontStreet.isPlace(a))
                        continue
                    }
                    SceneObjectType.TREE, SceneObjectType.PALM_TREE -> {
                        movedTrees++
                        assertEquals(
                            "$id #$i: a fir is a fir",
                            SceneObjectRenderer.isFirSeed(SceneObjectRenderer.idleSeedOf(a)), SceneObjectRenderer.isFirSeed(SceneObjectRenderer.idleSeedOf(b)),
                        )
                        assertEquals(
                            "$id #$i: its autumn tone",
                            SceneObjectRenderer.fallToneOf(SceneObjectRenderer.idleSeedOf(a)), SceneObjectRenderer.fallToneOf(SceneObjectRenderer.idleSeedOf(b)),
                        )
                    }
                    SceneObjectType.PARASOL -> {
                        movedParasols++
                        val house = houses[parasols.indexOf(a)]
                        val before = (a.tileFractionX - house.tileFractionX).mod(1f).let { if (it > 0.5f) it - 1f else it }
                        val after = (b.tileFractionX - house.tileFractionX).mod(1f).let { if (it > 0.5f) it - 1f else it }
                        assertTrue("$id #$i: the parasol stands on the other side of its house", before * after < 0f)
                        assertEquals("$id #$i: beside its house", SceneObjectCatalog.PARASOL_SIDE_OFFSET, kotlin.math.abs(after), 0.0041f)
                    }
                    else -> throw AssertionError("$id #$i: a ${a.type} moved")
                }
                assertEquals("$id #$i: stands at its density as it did", c.keepCandidate(a), c.keepCandidate(b))
                assertTrue("$id #$i: its density draw", kotlin.math.abs(densityFraction(a) - densityFraction(b)) <= 0.02)
                assertEquals("$id #$i: its colour", colourCoinOf(a), colourCoinOf(b))
                val d = (b.tileFractionX - a.tileFractionX).mod(1f).let { if (it > 0.5f) it - 1f else it }
                report.append("STREET_MOVES %-10s %s %.4f -> %.4f (%+.3f of the tile, %+.2f screens)%s\n".format(
                    id, a.type, a.tileFractionX, b.tileFractionX, d, d * 2f, if (c.keepCandidate(a)) "" else ", not standing at the theme's density",
                ))
            }
            // The number of each kind standing at the theme's defaults is the number it was.
            for (type in listOf(SceneObjectType.TREE, SceneObjectType.PALM_TREE, SceneObjectType.PARASOL, SceneObjectType.HOUSE)) {
                assertEquals("$id: ${type}s standing", generated.count { it.type == type && c.keepCandidate(it) }, laidOut.count { it.type == type && c.keepCandidate(it) })
            }
        }
        println(report)
        println("STREET_MOVES $movedTrees trees and $movedParasols parasols moved on the twelve streets")
        assertTrue("the plan moved nothing: this test saw nothing", movedTrees + movedParasols > 0)
    }

    /**
     * **The towers left are the towers that stood**: every tower candidate of today's twelve streets is one of
     * v5.13B's, field for field -- where it stands, how deep, its size and the crown it was dealt -- read from
     * the streets the 5.13B wrote with its own code (`themes/streets-saved-by-v5.13B.json`). Two candidates of
     * each street are places now; the crowns of the others were dealt over the seven tower slots of before
     * (`SceneObjectCatalog.buildingCandidates`), so no tower that stays changes its crown.
     */
    @Test
    fun `the towers left are v5_13B's towers, crowns included`() {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("themes/streets-saved-by-v5.13B.json"))
        val before = org.json.JSONObject(stream.bufferedReader().readText())
        for ((id, _) in SceneObjectCatalog.BUILT_IN_STREETS) {
            val old = sceneObjectLayoutFromJson(before.getJSONObject(id)).staticObjects
                .filter { it.type == SceneObjectType.SKYSCRAPER && !StorefrontStreet.isPlace(it) }
            val now = SceneObjectCatalog.layoutFor(id, 0xFF8899AA.toInt()).staticObjects
                .filter { it.type == SceneObjectType.SKYSCRAPER && !StorefrontStreet.isPlace(it) }
            assertEquals("$id: seven tower candidates before", 7, old.size)
            assertEquals("$id: five now", 5, now.size)
            for (t in now) assertTrue("$id: a tower that did not stand in v5.13B: $t", t in old)
        }
    }

    /**
     * **A place's fronts are the six buildings', each as tall as its family declares and as tall as it
     * draws** ([SceneObjectCatalog.frontRects]): twelve rectangles, each of the six widths twice, the heights
     * the families' and the inks' ([Storefront.inkTallUnits]). On the built-in streets the declared front is
     * the binding one (the plan gives the same table without the ink's), so this pins the ink's directly.
     */
    @Test
    fun `a place is measured over every building's declared and drawn front`() {
        val place = StaticSceneObject(SceneObjectType.SKYSCRAPER, depthFraction = 0.622f, tileFractionX = 0.5f)
        val fronts = SceneObjectCatalog.frontRects(place)
        assertEquals(12, fronts.size)
        val v = SceneObjectRenderer.variantFor(place)
        val metre = v.spriteUnitsTall / NeighbourhoodTable.FAMILIES.getValue(v).unitsTall
        val s = SceneObjectRenderer.effectiveScaleFor(place, 2340f)
        for (b in Storefront.ALL) {
            for (tall in listOf(b.family.unitsTall, b.inkTallUnits)) {
                val h = tall * metre * s
                assertTrue("$b at $tall units", fronts.any { kotlin.math.abs((it[3] - it[1]) - h) < 1e-3f && kotlin.math.abs((it[2] - it[0]) - 2 * b.halfWidthUnits * metre * s) < 1e-3f })
            }
        }
    }

    /**
     * The plan never puts five places on one screen ([SceneObjectCatalog.fiveOnAScreen]), which is what lets
     * the street's rule of four in a row keep two alike off the screen: five places a fifth of the tile apart
     * cannot share a screen; five within 0.45 of the tile can; and a gap a hair under what lets the other four
     * stand within one screen, buildings included, can.
     */
    @Test
    fun `five places on one screen are what the plan refuses`() {
        val half = FloatArray(5) { 0.05f }
        assertTrue(!SceneObjectCatalog.fiveOnAScreen(intArrayOf(0, 40, 80, 120, 160), half))
        assertTrue(SceneObjectCatalog.fiveOnAScreen(intArrayOf(10, 30, 50, 70, 90), half))
        // One gap of 0.36 and four of 0.16: the other four span 0.64, less two halves widened for the tallest
        // screen (2 x 0.05 x 2.4 / 2.1667 = 0.111) is 0.53, over half the tile; 0.40 makes it 0.49, under.
        assertTrue(!SceneObjectCatalog.fiveOnAScreen(intArrayOf(0, 32, 64, 96, 128), half))
        assertTrue(SceneObjectCatalog.fiveOnAScreen(intArrayOf(0, 30, 60, 90, 120), half))
    }
}
