package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **No part that says what a building is falls under two pixels, in any of the places** (v5.13; five a
 * built-in street since v5.13B2).
 *
 * Each of the six buildings among the houses can stand in each place of the street since v5.13
 * ([StorefrontStreet]), drawn at the place's metre -- one metre a unit for every piece -- and its depth:
 * the far place is the smallest, since v5.13B2 the one at 0.356, a ninth farther than the far place of
 * before (2.14 px for 2.4 units at the smallest, Spring's, against 2.36 at New Year's in v5.13B). The generator draws every identity detail of the shop and the two
 * stations at 2.4 units or more (`tools/assets/buildings/civic.py`, `DETAIL`; the bag's handle and the gap
 * inside it, the doors' panel lines and the bars between their panes, the engine's lamps, bumper and light
 * bar, the light bar's base, the star's arms), and the school's eraser and the bar's red bar at 2.4 too.
 * This is the other half: what a unit is worth on the BV6600's 1440-pixel-tall screen in every place of
 * every built-in street, by the renderer's own scale ([SceneObjectRenderer.effectiveScaleFor] -- the
 * place's size roll included -- times the place's metre), so that 2.4 units are 2 px or more.
 *
 * And the random themes, whose places stand at any depth and roll their size wider: there the renderer's
 * floor ([SceneObjectRenderer.placeScaleFloor]) keeps a piece unit at 1/1 728 of the screen's height, and
 * on the built-in streets it never binds -- they are drawn as the projection and the roll make them.
 *
 * What it does not cover, said here: the shared vocabulary every building of the street draws at every
 * depth -- window frames (1.5 units), door handles (1.8), the lantern's bracket (1.6), the school's flag
 * pole (1.2) and clock hands (1.0) -- which are lines, were under two pixels in the places their buildings
 * stood in before v5.13, and stay as they are by the maintainer's decision of 2026-10-12: he accepted the
 * recommendation that they are the drawing of every house in the town, which thickening them would change
 * (inventory I-633).
 */
class StorefrontDetailTest {

    private val themes = listOf(
        "sunset", "autumn", "winter", "desert", "christmas", "new_year",
        "beach", "city", "tundra", "easter", "halloween", "spring",
    )

    /** The generator's identity-detail floor, in piece units (`civic.DETAIL`). */
    private val detailUnits = 2.4f

    /** The BV6600's height in pixels. */
    private val screenHeight = 1440f

    private fun pxPerUnit(place: StaticSceneObject): Float {
        val v = SceneObjectRenderer.variantFor(place)
        val metre = v.spriteUnitsTall / NeighbourhoodTable.FAMILIES.getValue(v).unitsTall
        return SceneObjectRenderer.effectiveScaleFor(place, screenHeight) * metre
    }

    @Test
    fun `every place of every built-in street draws a detail of 2_4 units at two pixels or more`() {
        var smallest = Float.MAX_VALUE
        var where = ""
        val report = StringBuilder()
        for (themeId in themes) {
            for (place in StorefrontStreet.places(SceneObjectCatalog.layoutFor(themeId, 0xFF8899AA.toInt()))) {
                val px = pxPerUnit(place)
                report.append("%-10s %-10s depth %.4f roll %.3f: %.3f px a unit, 2.4 units = %.2f px\n"
                    .format(themeId, SceneObjectRenderer.variantFor(place), place.depthFraction, place.scale, px, px * detailUnits))
                if (px < smallest) { smallest = px; where = "$themeId/${SceneObjectRenderer.variantFor(place)}" }
            }
        }
        println(report)
        println("STOREFRONT_DETAIL smallest: %.3f px a unit at %s, 2.4 units = %.2f px".format(smallest, where, smallest * detailUnits))
        assertTrue("at $where a 2.4-unit detail is ${smallest * detailUnits} px", smallest * detailUnits >= 2f)
    }

    /** The scale [place] is drawn at by the projection and its roll alone, without the floor. */
    private fun projected(place: StaticSceneObject): Float =
        SceneObjectRenderer.variantFor(place).baseScale * place.scale *
            SceneSpace.depthScale(place.depthFraction) * SceneSpace.sceneScale(screenHeight)

    @Test
    fun `on the built-in streets the floor never binds - every place is drawn as the projection makes it`() {
        for (themeId in themes) {
            for (place in StorefrontStreet.places(SceneObjectCatalog.layoutFor(themeId, 0xFF8899AA.toInt()))) {
                val drawn = SceneObjectRenderer.effectiveScaleFor(place, screenHeight)
                assertEquals("$themeId ${SceneObjectRenderer.variantFor(place)}", projected(place), drawn, 0f)
                assertTrue(
                    "$themeId: the floor is under the place",
                    SceneObjectRenderer.placeScaleFloor(SceneObjectRenderer.variantFor(place), screenHeight) < drawn,
                )
            }
        }
    }

    /**
     * A random theme (`RandomSceneGenerator.generateLayout`, its static objects rebuilt here from the same
     * random sequence: the cars' colours call the framework's `Color.HSVToColor`, which the JVM does not
     * have): a tower in the band of the shops is a place, at any depth from 0.30, its size rolled 0.82 to
     * 1.18. Three thousand of them, and every depth and roll of that range on a grid.
     */
    @Test
    fun `on a random theme no place draws a detail of 2_4 units under two pixels`() {
        var places = 0
        var lifted = 0
        var smallestProjected = Float.MAX_VALUE
        for (k in 0 until 3000) {
            val rnd = kotlin.random.Random(k xor 0x5EED)
            val pool = listOf(
                SceneObjectType.HOUSE, SceneObjectType.TREE,
                SceneObjectType.SNOWMAN, SceneObjectType.GIFT, SceneObjectType.PALM_TREE, SceneObjectType.PARASOL,
                SceneObjectType.SKYSCRAPER, SceneObjectType.PENGUIN,
                SceneObjectType.BUNNY, SceneObjectType.EASTER_EGG,
            )
            val objects = (0 until rnd.nextInt(3, 7)).map {
                StaticSceneObject(type = pool.random(rnd), depthFraction = rnd.nextFloat(), tileFractionX = rnd.nextFloat(), scale = 0.82f + rnd.nextFloat() * 0.36f)
            }
            for (place in StorefrontStreet.places(SceneObjectLayout(staticObjects = objects, cars = emptyList()))) {
                places++
                val metre = pxPerUnit(place) / SceneObjectRenderer.effectiveScaleFor(place, screenHeight)
                smallestProjected = minOf(smallestProjected, projected(place) * metre * detailUnits)
                if (SceneObjectRenderer.effectiveScaleFor(place, screenHeight) > projected(place)) lifted++
                assertTrue("random:$k depth ${place.depthFraction} roll ${place.scale}", pxPerUnit(place) * detailUnits >= 2f - 1e-4f)
            }
        }
        for (d in 0..70) for (r in 0..36) {
            val place = StaticSceneObject(SceneObjectType.SKYSCRAPER, depthFraction = SceneSpace.BUILDING_TOWER_MAX_DEPTH + d * 0.01f, tileFractionX = 0.5f, scale = 0.82f + r * 0.01f)
            assertTrue("depth ${place.depthFraction} roll ${place.scale}", pxPerUnit(place) * detailUnits >= 2f - 1e-4f)
        }
        println("STOREFRONT_DETAIL random themes: $places places in 3000 themes, $lifted drawn at the floor; unfloored the smallest 2.4 units would be %.2f px".format(smallestProjected))
        assertTrue("no random theme had a place: the test saw nothing", places > 0)
    }

    @Test
    fun `the three rows share one metre, so the place decides the depth and nothing else`() {
        val metres = listOf(SceneSpace.SceneVariant.RESTAURANT, SceneSpace.SceneVariant.SCHOOL, SceneSpace.SceneVariant.BAR).map { v ->
            v.metresTall / NeighbourhoodTable.FAMILIES.getValue(v).unitsTall
        }
        for (m in metres) assertEquals("metres a unit", metres[0], m, 1e-5f)
    }
}
