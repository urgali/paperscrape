package com.paperscrape.livewallpaper.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shops' own colours (v5.11, inventory I-403; the maintainer's *«voglio che i negozi abbiano
 * colore a se, aggiungiamolo»* of 2026-10-06): where they start, what the drawing reads, and what a
 * theme stored before v5.11 is read with -- the Buildings colours its shops were drawn in.
 */
class ShopColoursTest {

    private val houses = SceneCustomization.DEFAULT.houses

    @Test
    fun `a built-in's shops start from the houses' pair, but where the ground would swallow it`() {
        for (theme in ThemeCatalog.ALL) {
            val shops = defaultCustomizationFor(theme.id).shops
            when (theme.id) {
                "winter", "christmas", "tundra", "beach" ->
                    assertNotEquals("${theme.id}: corrected off the ground", houses.colorDay1, shops.colorDay1)
                else -> {
                    assertEquals(theme.id, houses.colorDay1, shops.colorDay1)
                    assertEquals(theme.id, houses.colorNight1, shops.colorNight1)
                    assertEquals(theme.id, houses.colorDay2, shops.colorDay2)
                    assertEquals(theme.id, houses.colorNight2, shops.colorNight2)
                }
            }
        }
    }

    @Test
    fun `a theme nobody built in keeps the slate its shops always had`() {
        assertEquals(SceneCustomization.DEFAULT.buildings.colorDay1, SceneCustomization.DEFAULT.shops.colorDay1)
        assertEquals(SceneCustomization.DEFAULT.buildings.colorNight2, SceneCustomization.DEFAULT.shops.colorNight2)
        // A random or saved theme's id is no built-in's (a random theme's colours need Android's
        // `Color`, so the lookup is asked directly): it starts from that slate.
        assertEquals(null, builtInShopColours("random:42"))
        assertEquals(null, builtInTowerColours("custom:1"))
    }

    /**
     * Which pair each building is drawn in: by what it is, never by the category alone -- a tower the
     * towers', and each of the six among the houses its group's (v5.13), in whichever place it stands.
     */
    @Test
    fun `a shop wears the shops' pair, a public building the public buildings' and a tower the towers'`() {
        val c = defaultCustomizationFor("autumn").let {
            it.copy(
                buildings = it.buildings.copy(colorDay1 = 0xFF010101.toInt(), colorDay2 = 0xFF010101.toInt()),
                shops = it.shops.copy(colorDay1 = 0xFF020202.toInt(), colorDay2 = 0xFF020202.toInt()),
                publics = it.publics.copy(colorDay1 = 0xFF030303.toInt(), colorDay2 = 0xFF030303.toInt()),
            )
        }
        for (depth in listOf(0.05f, 0.2f, 0.29f)) {
            val tower = StaticSceneObject(SceneObjectType.SKYSCRAPER, depthFraction = depth, tileFractionX = 0.3f)
            assertEquals(0xFF010101.toInt(), c.colorFor(tower, 1f))
        }
        for (depth in listOf(0.356f, 0.444f, 0.62f, 0.8f)) {
            val place = StaticSceneObject(SceneObjectType.SKYSCRAPER, depthFraction = depth, tileFractionX = 0.3f)
            for (b in Storefront.ALL) {
                val want = if (b.group == StorefrontGroup.SHOPS) 0xFF020202.toInt() else 0xFF030303.toInt()
                assertEquals("$b at $depth", want, c.storefrontWall(place, b, 1f))
            }
        }
        // The card's towers stand at depths the street keeps for its places; asked by the drawing, they are towers.
        val cardTower = StaticSceneObject(SceneObjectType.SKYSCRAPER, depthFraction = 0.66f, tileFractionX = 0.44f)
        assertEquals(0xFF010101.toInt(), c.wallColourFor(cardTower, SceneSpace.SceneVariant.TOWER, 1f))
    }

    /**
     * **A payload written before v5.11** -- a saved theme, a theme's archive, a backup's theme, a theme
     * file: all four go through `sceneCustomizationFromJson` -- has no shops, and is read with the
     * Buildings colours stored beside them, modes included: the look it had.
     */
    @Test
    fun `a payload without shops is read with the Buildings colours it carries`() {
        val stored = defaultCustomizationFor("easter").copy(
            buildings = ObjectVariantConfig(
                visible = true, density = 0.4f,
                colorDay1 = 0xFF123456.toInt(), colorNight1 = 0xFF0A0B0C.toInt(),
                colorDay2 = 0xFF654321.toInt(), colorNight2 = 0xFF0C0B0A.toInt(),
                autoMode1 = AutoColorMode.FROM_DAY, autoMode2 = AutoColorMode.MANUAL,
            ),
        )
        val before511 = JSONObject(stored.toJson().toString()).apply { remove("shops") }
        val read = sceneCustomizationFromJson(before511)
        assertEquals(stored.buildings.colorDay1, read.shops.colorDay1)
        assertEquals(stored.buildings.colorNight1, read.shops.colorNight1)
        assertEquals(stored.buildings.colorDay2, read.shops.colorDay2)
        assertEquals(stored.buildings.colorNight2, read.shops.colorNight2)
        assertEquals(AutoColorMode.FROM_DAY, read.shops.autoMode1)
        // And the towers are what was stored too: nothing of a stored look moves.
        assertEquals(stored.buildings, read.buildings)
    }

    @Test
    fun `a payload with neither block reads the slate both were`() {
        val read = sceneCustomizationFromJson(JSONObject().put("hillsVariation", 0.5))
        assertEquals(SceneCustomization.DEFAULT.buildings.colorDay1, read.shops.colorDay1)
        assertEquals(SceneCustomization.DEFAULT.buildings.colorDay1, read.buildings.colorDay1)
    }

    @Test
    fun `the shops' colours survive the round trip, apart from the Buildings'`() {
        val c = defaultCustomizationFor("spring").let {
            it.copy(shops = it.shops.copy(colorDay1 = 0xFFABCDEF.toInt(), autoMode2 = AutoColorMode.FROM_NIGHT))
        }
        val read = sceneCustomizationFromJson(JSONObject(c.toJson().toString()))
        assertEquals(c.shops, read.shops)
        assertEquals(c.buildings, read.buildings)
    }

    /**
     * Both drawings ask by what they draw: a house or a tower by the variant it is dispatched on, one of
     * the six among the houses by what it is (v5.13). Pinned by their source, since no JVM test runs the
     * renderer; the card's result is `PreviewRendererAgreementTest`'s.
     */
    @Test
    fun `the wallpaper and the card colour a building by what they draw it as`() {
        val renderer = source("engine/SceneObjectRenderer.kt")
        assertTrue(renderer.contains("customization.storefrontWall(spec, storefront, dayBlend)"))
        assertTrue(renderer.contains("customization.wallColourFor(spec, variant, dayBlend)"))
        val card = source("engine/ThemePreviewScene.kt")
        assertTrue(card.contains("if (storefront != null) c.storefrontWall(spec, storefront, dayBlend) else c.wallColourFor(spec, variant, dayBlend)"))
    }

    private fun source(path: String): String {
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            val f = java.io.File(dir, "app/src/main/kotlin/com/paperscrape/livewallpaper/$path")
            if (f.isFile) return f.readText()
            val g = java.io.File(dir, "src/main/kotlin/com/paperscrape/livewallpaper/$path")
            if (g.isFile) return g.readText()
            dir = dir.parentFile
        }
        error("source not found: $path")
    }

    @Test
    fun `the automatic modes reach the shops' pair too`() {
        val c = defaultCustomizationFor("sunset").let {
            it.copy(shops = it.shops.copy(colorDay1 = 0xFFE0C0A0.toInt(), colorNight1 = 0xFF000000.toInt(), autoMode1 = AutoColorMode.FROM_DAY))
        }
        val resolved = c.withResolvedDayNightColors()
        assertEquals(DayNightColor.resolve(0xFFE0C0A0.toInt(), 0xFF000000.toInt(), AutoColorMode.FROM_DAY).second, resolved.shops.colorNight1)
    }
}
