package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A night card lights the houses' windows as the wallpaper does** (v5.12, inventory I-520 and I-511,
 * the maintainer's *«il resto si a tutto»* of 2026-10-09 to the row that said so): half of each house's
 * windows, the ones its roster lights at its first moment, from the same seeds -- the theme's and the
 * house's -- the others dark glass. A day card is the day's glass, as before. The smoke stays off the
 * card (`DESIGN_NOTES.md` §9, "What the card leaves out").
 */
class CardHouseLightsTest {

    /** The lit panes the wallpaper's roster gives the card's house at its first moment, and the house's pane count. */
    private fun expected(theme: SceneTheme, variant: SceneSpace.SceneVariant, tileX: Float, depth: Float): Triple<Int, Int, Int> {
        val family = NeighbourhoodTable.FAMILIES.getValue(variant)
        val deal = NeighbourhoodComposer.Deal()
        NeighbourhoodComposer.deal(family, tileX, depth, deal)
        val seed = theme.id.hashCode()
        val buildingSeed = SceneObjectRenderer.buildingSeedOf(StaticSceneObject(SceneObjectType.HOUSE, depthFraction = depth, tileFractionX = tileX))
        val people = WindowOccupants.occupantCount(seed, buildingSeed, deal.peopleWindows, family.kind)
        val plan = WindowRoster.plan(seed, buildingSeed, deal.windowCount, deal.paneCount, people, house = true)
        return Triple(WindowRoster.litMask(plan.states[0]), deal.paneCount, plan.lit)
    }

    @Test
    fun `a night card lights half of each house's windows, the wallpaper's own, and leaves the rest dark`() {
        val identity = ThemePreviewScenes.PreviewIdentity
        var housesSeen = 0
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id)
            val scene = ThemePreviewScenes.forTheme(theme, c, forceNight = true)
            val night = 1f - ThemePreviewScenes.cardPhase(theme, true).dayBlend
            assertTrue("${theme.id}: a night card", night > 0.5f)
            val rects = scene.items.flatMap { item -> item.parts.filter { it.rectWidth > 0f }.map { item to it } }
            if (!(c.houses.visible && c.houses.density > 0f)) {
                assertEquals("${theme.id}: no houses, no lit windows", 0, rects.size)
                continue
            }
            var expectedLit = 0
            for ((variant, x, depth) in listOf(
                Triple(SceneSpace.SceneVariant.HOUSE_LARGE, identity.HOUSE_LARGE_X, identity.HOUSE_LARGE_DEPTH),
                Triple(SceneSpace.SceneVariant.HOUSE_SMALL, identity.HOUSE_SMALL_X, identity.HOUSE_SMALL_DEPTH),
            )) {
                val (lit, panes, count) = expected(theme, variant, x, depth)
                assertEquals("${theme.id} $variant: its roster's number lit", count, Integer.bitCount(lit))
                assertTrue("${theme.id} $variant: half its $panes panes or more", count >= (panes + 1) / 2)
                expectedLit += Integer.bitCount(lit)
                housesSeen++
            }
            assertEquals("${theme.id}: the card lights the panes the wallpaper's rosters light", expectedLit, rects.size)
            for ((_, rect) in rects) {
                assertEquals("${theme.id}: a lit window in the lit glass", SceneObjectRenderer.windowGlassColor(night), rect.tint)
                assertEquals(0, rect.resId)
            }
            // And the houses' glass masks are the dark glass under them.
            val houseMasks = scene.items.flatMap { it.parts }.filter { it.added && it.tint == SceneObjectRenderer.unlitWindowGlassColor(night) }
            assertTrue("${theme.id}: the houses' glass is dark glass at night", houseMasks.size >= 2)
        }
        assertTrue(housesSeen >= 20)
    }

    /** Where the card lays a house's lit windows: the panes its roster lights at its first moment, less the inset. */
    private fun expectedRects(theme: SceneTheme, variant: SceneSpace.SceneVariant, tileX: Float, depth: Float): Set<List<Float>> {
        val family = NeighbourhoodTable.FAMILIES.getValue(variant)
        val deal = NeighbourhoodComposer.Deal()
        NeighbourhoodComposer.deal(family, tileX, depth, deal)
        val lit = expected(theme, variant, tileX, depth).first
        val inset = SceneObjectRenderer.LIT_PANE_INSET
        val out = HashSet<List<Float>>()
        for (index in 0 until deal.size) {
            val placed = deal[index]
            for ((k, box) in placed.piece.panes.withIndex()) {
                if (lit and (1 shl (placed.firstPane + k)) == 0) continue
                out += listOf(box.x + inset, placed.baseY + box.y + inset, box.w - 2 * inset, box.h - 2 * inset)
            }
        }
        return out
    }

    @Test
    fun `the card lights the very windows the wallpaper's house lights at its first moment`() {
        val identity = ThemePreviewScenes.PreviewIdentity
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id)
            if (!(c.houses.visible && c.houses.density > 0f)) continue
            val scene = ThemePreviewScenes.forTheme(theme, c, forceNight = true)
            val onCard = scene.items.map { item -> item.parts.filter { it.rectWidth > 0f }.map { listOf(it.ox, it.oy, it.rectWidth, it.rectHeight) }.toSet() }
                .filter { it.isNotEmpty() }
            val large = expectedRects(theme, SceneSpace.SceneVariant.HOUSE_LARGE, identity.HOUSE_LARGE_X, identity.HOUSE_LARGE_DEPTH)
            val small = expectedRects(theme, SceneSpace.SceneVariant.HOUSE_SMALL, identity.HOUSE_SMALL_X, identity.HOUSE_SMALL_DEPTH)
            assertEquals("${theme.id}: two houses with lit windows", 2, onCard.size)
            assertTrue("${theme.id}: the large house's lit windows are its roster's: $onCard vs $large", large in onCard)
            assertTrue("${theme.id}: the small house's lit windows are its roster's: $onCard vs $small", small in onCard)
        }
    }

    @Test
    fun `a day card lights nothing, as before`() {
        for (theme in ThemeCatalog.ALL) {
            val scene = ThemePreviewScenes.forTheme(theme, defaultCustomizationFor(theme.id), forceNight = false)
            if (ThemePreviewScenes.cardPhase(theme, false).dayBlend < 1f) continue
            assertEquals("${theme.id}", 0, scene.items.sumOf { item -> item.parts.count { it.rectWidth > 0f } })
        }
    }

    @Test
    fun `the lit card is laid at the pane, inside it`() {
        val theme = ThemeCatalog.byId("new_year")
        val scene = ThemePreviewScenes.forTheme(theme, defaultCustomizationFor(theme.id), forceNight = true)
        val rects = scene.items.flatMap { it.parts }.filter { it.rectWidth > 0f }
        assertTrue(rects.isNotEmpty())
        val inset = SceneObjectRenderer.LIT_PANE_INSET
        val paneSizes = NeighbourhoodTable.FAMILIES.values.flatMap { f -> f.slots.flatMap { it.options + it.heights } }
            .flatMap { it.panes }.map { (it.w - 2 * inset) to (it.h - 2 * inset) }.toSet()
        for (r in rects) assertTrue("a pane's size less the inset: ${r.rectWidth} x ${r.rectHeight}", (r.rectWidth to r.rectHeight) in paneSizes)
    }
}
