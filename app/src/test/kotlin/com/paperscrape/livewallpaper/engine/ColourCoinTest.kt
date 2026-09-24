package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 134 (v5.7F): the menu's "Each one randomly uses Color 1 or Color 2" is a fair coin, and
 * making it one changed which colour objects wear and nothing about which objects stand.
 *
 * Read through [colorFor] -- the function the renderer and the gallery card both call -- rather
 * than through the private coin, by giving the category two colours that cannot be confused and
 * asking which one comes back. So this measures what the user sees, not the arithmetic under it.
 *
 * Measured on the twelve shipped themes at their defaults, the way `RealThemeSilhouetteDistributionTest`
 * measures the buildings: a coin that is fair over millions of positions and clumps on the few
 * hundred the app actually draws would pass a synthetic sweep and still show one colour.
 */
class ColourCoinTest {

    private val one = 0xFF111111.toInt()
    private val two = 0xFF222222.toInt()

    /** [c] with every paired category wearing [one] as Color 1 and [two] as Color 2, day and night. */
    private fun marked(c: SceneCustomization): SceneCustomization {
        fun ObjectVariantConfig.m() = copy(colorDay1 = one, colorNight1 = one, colorDay2 = two, colorNight2 = two)
        return c.copy(
            houses = c.houses.m(), buildings = c.buildings.m(), trees = c.trees.m(), cars = c.cars.m(),
            snowmen = c.snowmen.m(), gifts = c.gifts.m(), penguins = c.penguins.m(), bunnies = c.bunnies.m(),
            easterEggs = c.easterEggs.m(), pumpkins = c.pumpkins.m(),
        )
    }

    private val paired = setOf(
        SceneObjectType.HOUSE, SceneObjectType.SKYSCRAPER, SceneObjectType.TREE, SceneObjectType.PALM_TREE,
        SceneObjectType.SNOWMAN, SceneObjectType.GIFT, SceneObjectType.PENGUIN, SceneObjectType.BUNNY,
        SceneObjectType.EASTER_EGG, SceneObjectType.PUMPKIN,
    )

    private fun standing(themeId: String): List<StaticSceneObject> {
        val c = defaultCustomizationFor(themeId)
        return SceneObjectCatalog.layoutFor(themeId, ThemeCatalog.byId(themeId).accentColor).staticObjects
            .filter { it.type in paired && c.keepCandidate(it) }
    }

    @Test
    fun `the two colours come out about even over every object the twelve themes stand`() {
        var first = 0
        var second = 0
        val byType = HashMap<SceneObjectType, IntArray>()
        for (theme in ThemeCatalog.ALL) {
            val c = marked(defaultCustomizationFor(theme.id))
            for (o in standing(theme.id)) {
                val colour = c.colorFor(o, 1f)
                assertTrue("${o.type} wore neither colour", colour == one || colour == two)
                val slot = byType.getOrPut(if (o.type == SceneObjectType.PALM_TREE) SceneObjectType.TREE else o.type) { IntArray(2) }
                if (colour == one) { first++; slot[0]++ } else { second++; slot[1]++ }
            }
        }
        val total = first + second
        // Before v5.7F: 94 to 206 (31 %). The bound is a fair coin's, not a target: two standard
        // deviations of 300 flips is +/- 5.8 points around half.
        assertTrue("Color 1 on $first of $total objects", first in (total * 0.44).toInt()..(total * 0.56).toInt())
        for ((type, n) in byType) {
            val sum = n[0] + n[1]
            if (sum < 40) continue
            assertTrue("$type: Color 1 on ${n[0]} of $sum", n[0] in (sum * 0.38).toInt()..(sum * 0.62).toInt())
        }
    }

    @Test
    fun `the plain cars come out about even too`() {
        var first = 0
        var total = 0
        for (theme in ThemeCatalog.ALL) {
            val c = marked(defaultCustomizationFor(theme.id))
            val cars = c.keptCars(SceneObjectCatalog.layoutFor(theme.id, theme.accentColor).cars, theme.id.hashCode())
            for (car in cars.filter { it.type == CarType.PLAIN }) {
                total++
                if (c.colorFor(car, 1f) == one) first++
            }
        }
        // Before v5.7F: 16 of 84.
        assertTrue("Color 1 on $first of $total plain cars", first in (total * 0.38).toInt()..(total * 0.62).toInt())
    }

    /**
     * The twelve gallery cards keep the colours they were approved with (v5.7A), pixel for pixel.
     *
     * The card asks the coin about seven fixed positions (`PreviewIdentity`, the same on every
     * card), chosen so each row shows both of the user's colours, and neither the cards nor those
     * positions are to be touched. The coin's channel was chosen among the three of 1..400 that
     * answer exactly as the pre-v5.7F coin did at all of them; this pins the answers, so a channel
     * that re-coloured a card fails here. **No other test would**: measured by mutation, channel
     * 119 re-colours cards and left `ThemePreviewSceneTest`, `ThemePreviewTruthTest` and
     * `PreviewRendererAgreementTest` green.
     */
    @Test
    fun `the gallery card positions wear the colours the cards were approved with`() {
        val id = ThemePreviewScenes.PreviewIdentity
        val c = marked(defaultCustomizationFor("sunset"))
        fun wears(type: SceneObjectType, x: Float, d: Float) =
            if (c.colorFor(StaticSceneObject(type, depthFraction = d, tileFractionX = x), 1f) == one) 1 else 2
        val sky = SceneObjectType.SKYSCRAPER
        assertEquals(
            // As v5.7A approved them, read off the pre-v5.7F coin: towers 2 1 1 2, restaurant 1,
            // school 1, bar 2, large house 1, small house 2.
            listOf(2, 1, 1, 2, 1, 1, 2, 1, 2),
            listOf(
                wears(sky, id.TOWER_X[0], id.TOWER_DEPTH[0]), wears(sky, id.TOWER_X[1], id.TOWER_DEPTH[1]),
                wears(sky, id.TOWER_X[2], id.TOWER_DEPTH[2]), wears(sky, id.TOWER_X[3], id.TOWER_DEPTH[3]),
                wears(sky, id.RESTAURANT_X, id.RESTAURANT_DEPTH), wears(sky, id.SCHOOL_X, id.SCHOOL_DEPTH),
                wears(sky, id.BAR_X, id.BAR_DEPTH),
                wears(SceneObjectType.HOUSE, id.HOUSE_LARGE_X, id.HOUSE_LARGE_DEPTH),
                wears(SceneObjectType.HOUSE, id.HOUSE_SMALL_X, id.HOUSE_SMALL_DEPTH),
            ),
        )
    }

    /**
     * The whole of what the maintainer authorised: the colour moves, the objects do not. Stated
     * structurally, because it is structural -- the coin is not an input to [keepCandidate] -- and
     * pinned as the count v5.7F measured with the old coin and the new one alike (300, Christmas's
     * fifth tower included), so a later change that did couple them would move it.
     */
    @Test
    fun `the coin does not decide which objects stand`() {
        val total = ThemeCatalog.ALL.sumOf { standing(it.id).size }
        assertEquals("objects wearing a colour pair that stand at the twelve defaults", 300, total)
        // And recolouring every category leaves the same set standing, object for object.
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id)
            val layout = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor).staticObjects
            assertEquals(theme.id, layout.filter { c.keepCandidate(it) }, layout.filter { marked(c).keepCandidate(it) })
        }
    }
}
