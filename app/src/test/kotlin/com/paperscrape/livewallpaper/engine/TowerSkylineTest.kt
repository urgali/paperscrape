package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The towers of v5.11: as many as before on every theme, three heights by a coin of their own under
 * the crowns they had, and the ones whose foot stood on the hill's crest drawn a little forward
 * (inventory I-402 and I-404; the maintainer's choices of 2026-10-06).
 */
class TowerSkylineTest {

    private fun towersOf(themeId: String): List<StaticSceneObject> {
        val theme = ThemeCatalog.byId(themeId)
        val layout = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor)
        val c = defaultCustomizationFor(theme.id)
        return layout.staticObjects.filter {
            it.type == SceneObjectType.SKYSCRAPER && it.depthFraction < SceneSpace.BUILDING_TOWER_MAX_DEPTH &&
                c.keepCandidate(it, layout.densityScheme)
        }
    }

    /**
     * **The number of towers is the maintainer's, not this round's to move** (2026-10-06, *quantità
     * intoccabili*): counted on every built-in at its factory settings when the proposals were
     * photographed, and nothing here may change one.
     */
    @Test
    fun `every built-in stands the towers it stood`() {
        val expected = mapOf(
            "sunset" to 4, "autumn" to 6, "winter" to 4, "desert" to 7, "christmas" to 5, "new_year" to 6,
            "beach" to 5, "city" to 7, "tundra" to 5, "easter" to 5, "halloween" to 4, "spring" to 4,
        )
        val counted = ThemeCatalog.ALL.associate { it.id to towersOf(it.id).size }
        assertEquals(expected, counted)
        assertEquals(62, counted.values.sum())
    }

    @Test
    fun `the deal still sees one body, so the crowns are the ones they were`() {
        assertEquals("two tower silhouettes: one body under two crowns", 2, SilhouetteDeal.TOWERS.size)
        val tower = NeighbourhoodTable.FAMILIES.getValue(SceneSpace.SceneVariant.TOWER)
        assertEquals("the body the deal sees", 1, tower.slots[0].options.size)
        assertEquals("three heights beside it", 3, tower.slots[0].heights.size)
        assertTrue("the middle height is the dealt body", tower.slots[0].heights[1] === tower.slots[0].options[0])
    }

    @Test
    fun `the three heights are short, the old one and tall, and every one stands somewhere`() {
        val tower = NeighbourhoodTable.FAMILIES.getValue(SceneSpace.SceneVariant.TOWER)
        val heights = tower.slots[0].heights.map { it.height }
        assertEquals(listOf(148f, 188f, 224f), heights)
        val drawn = IntArray(3)
        for (theme in ThemeCatalog.ALL) {
            for (t in towersOf(theme.id)) drawn[NeighbourhoodComposer.heightIndex(t.tileFractionX, t.depthFraction, 3)]++
        }
        println("built-in towers by height, short/old/tall: ${drawn.toList()}")
        for (count in drawn) assertTrue("each height stands on the built-ins: ${drawn.toList()}", count >= 10)
    }

    /**
     * A tower's height is the same whichever way it is dealt -- from the silhouette the generator
     * recorded, or from its position (the gallery card, a theme saved before v5.5) -- because both read
     * the building's own identity.
     */
    @Test
    fun `a tower is one height whether it is dealt by silhouette or by position`() {
        val family = NeighbourhoodTable.FAMILIES.getValue(SceneSpace.SceneVariant.TOWER)
        val bySilhouette = NeighbourhoodComposer.Deal()
        val byPosition = NeighbourhoodComposer.Deal()
        for (theme in ThemeCatalog.ALL) {
            for (t in towersOf(theme.id)) {
                NeighbourhoodComposer.deal(family, t, bySilhouette)
                NeighbourhoodComposer.deal(family, t.tileFractionX, t.depthFraction, byPosition)
                assertTrue(bySilhouette[0].piece === byPosition[0].piece)
                assertEquals(bySilhouette[1].baseY, byPosition[1].baseY, 0f)
            }
        }
    }

    /**
     * **The crest rule** ([SceneSpace.towerDrawnDepth]): only a tower whose foot stands within the
     * clearance of the crest is drawn forward, never past 0.29, never back; the stored depth -- what the
     * density, the coin, the deal and the shop plan read -- is untouched.
     */
    @Test
    fun `only the towers on the crest are drawn forward, and not past the shops`() {
        val moved = mutableMapOf<String, Int>()
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id)
            val phase = SceneSpace.hillCrestPhase(theme.id)
            var n = 0
            for (t in towersOf(theme.id)) {
                val drawn = SceneSpace.towerDrawnDepth(t.depthFraction, t.tileFractionX, c.hillsVariation, phase)
                assertTrue("${theme.id}: drawn behind where it stands", drawn >= t.depthFraction)
                assertTrue("${theme.id}: drawn in front of the shops' band", drawn <= SceneSpace.TOWER_PLANTED_MAX_DEPTH)
                val crest = SceneSpace.hillCrestYFraction(t.tileFractionX, c.hillsVariation, phase)
                val foot = SceneSpace.groundYFraction(t.depthFraction)
                if (drawn > t.depthFraction) {
                    n++
                    assertTrue("${theme.id}: moved a tower that stood clear", foot - crest < SceneSpace.TOWER_CREST_CLEARANCE_FRACTION)
                    val newFoot = SceneSpace.groundYFraction(drawn)
                    assertTrue(
                        "${theme.id}: brought forward and still on the crest",
                        newFoot - crest >= SceneSpace.TOWER_CREST_CLEARANCE_FRACTION - 1e-4f ||
                            drawn == SceneSpace.TOWER_PLANTED_MAX_DEPTH,
                    )
                } else {
                    assertTrue("${theme.id}: left a tower on the crest", foot - crest >= SceneSpace.TOWER_CREST_CLEARANCE_FRACTION - 1e-4f)
                }
            }
            moved[theme.id] = n
        }
        // As photographed in the proposals: one to three a theme.
        assertEquals(
            mapOf(
                "sunset" to 1, "autumn" to 1, "winter" to 1, "desert" to 2, "christmas" to 1, "new_year" to 2,
                "beach" to 2, "city" to 3, "tundra" to 2, "easter" to 1, "halloween" to 3, "spring" to 1,
            ),
            moved,
        )
    }

    /**
     * Under the crest's lowest point, too -- 0.688 of the screen at full variation, where a tower would
     * be wanted at 0.337, past the shops' band: what stands from [SceneSpace.BUILDING_TOWER_MAX_DEPTH]
     * on is drawn where it stands, and the cap stays behind every shop.
     */
    @Test
    fun `a shop or anything else is drawn where it stands`() {
        val underTheLowestCrest = 0.875f
        assertEquals(0.688f, SceneSpace.hillCrestYFraction(underTheLowestCrest, 1f, 0f), 1e-4f)
        for (depth in listOf(0.30f, 0.356f, 0.5f, 0.8f)) {
            assertEquals(depth, SceneSpace.towerDrawnDepth(depth, 0.3f, 1f, 0f), 0f)
            assertEquals(depth, SceneSpace.towerDrawnDepth(depth, underTheLowestCrest, 1f, 0f), 0f)
        }
        assertTrue(
            "the cap stands in front of every tower and behind every shop",
            SceneSpace.TOWER_PLANTED_MAX_DEPTH < SceneSpace.BUILDING_TOWER_MAX_DEPTH,
        )
    }

    /** One crest: the hills are drawn with the function the towers are planted by. */
    @Test
    fun `the hills are drawn with the crest the towers are planted by`() {
        val renderer = File(sourceRoot(), "engine/PaperRenderer.kt").readText()
        val path = renderer.substring(renderer.indexOf("private fun buildBaseHillPath("))
        assertTrue(path.contains("SceneSpace.hillCrestFraction(f, hillsVariation, phase)"))
        assertTrue(path.contains("SceneSpace.hillCrestPhase(theme.id, layer)"))
        // And the crest is the sine it always was: 0.13 +/- 0.09 of the layer, two waves a tile.
        assertEquals(0.13f, SceneSpace.hillCrestFraction(0f, 1f, 0f), 1e-6f)
        assertEquals(0.22f, SceneSpace.hillCrestFraction(0.125f, 1f, 0f), 1e-5f)
        assertEquals(0.13f, SceneSpace.hillCrestFraction(0.125f, 0f, 0f), 1e-6f)
    }

    private fun sourceRoot(): File {
        val candidates = listOf(
            File("src/main/kotlin/com/paperscrape/livewallpaper"),
            File("app/src/main/kotlin/com/paperscrape/livewallpaper"),
        )
        return candidates.first { it.isDirectory }
    }
}
