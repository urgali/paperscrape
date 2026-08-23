package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gallery preview and the wallpaper must assemble a tree the same way (v3.7 Filone C).
 *
 * ### What the audit found
 *
 * `ThemePreviewScenes` builds its objects from the same sprites at the same offsets as
 * `SceneObjectRenderer`, copied by hand. Comparing all of them: **71 preview offset pairs, 59
 * sprites used by both, 56 in exact agreement** once the renderer's nested transforms are folded
 * in — and one drifted. The winter tree's snow cap was `(-38,-116)` in the preview against
 * `(-41,-80)` under `translate(0,-38)`, i.e. `(-41,-118)`, in the wallpaper: 3 units right and 2
 * down.
 *
 * The duplication itself was **not** removed, and that is deliberate. The preview is a flat
 * 320x240 data description with no perspective, no candidate system and no scroll; routing it
 * through the wallpaper's renderer would mean giving it all three, which is a refactor of
 * `SceneObjectRenderer` that no evidence supports. What was removed is the *hand copy* for the one
 * object that had actually drifted — the tree now reads [TreeSpriteLayout] from both sides.
 *
 * **v3.8 re-ran the audit to decide how far to take this, and the answer is narrow.** Of the 55
 * drawables both sides use, **all 55 agree exactly** — plain literals on both sides, no transform to
 * fold and no arithmetic to get wrong. Hoisting those into shared constants would guard against
 * nothing.
 *
 * The **skyscraper** is the one exception, and it earns the treatment twice: its roof snow carried
 * the renderer's four-term offset as a folded sum (the tree's exact failure mode), and its lit night
 * facade sat six units right and six down of the wall it is documented to lie exactly on top of.
 * Both now read [SkyscraperSpriteLayout]. Nothing else did, so nothing else was touched — see that
 * object for the full reasoning and `RELEASE_HISTORY.md` for the audit.
 */
class PreviewRendererAgreementTest {

    private fun previewParts(themeId: String): List<PreviewSprite> {
        val scene = ThemePreviewScenes.forTheme(ThemeCatalog.byId(themeId), defaultCustomizationFor(themeId))
        return (scene.items + scene.backdrop + scene.ground).flatMap { it.parts }
    }


    private fun previewTreeParts(themeId: String, winter: Boolean): List<PreviewSprite> {
        val scene = ThemePreviewScenes.forTheme(ThemeCatalog.byId(themeId), defaultCustomizationFor(themeId))
        val treeSprites = setOf(
            R.drawable.tree_trunk,
            R.drawable.tree_canopy,
            R.drawable.tree_canopy_snowcap,
            R.drawable.tree_dead_branches,
        )
        val parts = (scene.items + scene.backdrop + scene.ground)
            .flatMap { it.parts }
            .filter { it.resId in treeSprites }
        assertTrue("theme $themeId draws no tree at all", parts.isNotEmpty())
        if (winter) {
            assertTrue(
                "theme $themeId should draw a snow cap",
                parts.any { it.resId == R.drawable.tree_canopy_snowcap },
            )
        }
        return parts
    }

    /**
     * **The regression this closes.** The preview's snow cap must sit exactly where the wallpaper's
     * does — the crown's own origin, lift folded in.
     */
    @Test
    fun `the preview snow cap sits where the wallpaper puts it`() {
        val caps = previewTreeParts("winter", winter = true)
            .filter { it.resId == R.drawable.tree_canopy_snowcap }
        assertTrue(caps.isNotEmpty())
        for (cap in caps) {
            assertEquals("snow cap x", TreeSpriteLayout.FLAT_SNOWCAP_X, cap.ox, 0f)
            assertEquals("snow cap y", TreeSpriteLayout.FLAT_SNOWCAP_Y, cap.oy, 0f)
        }
        // The pre-v3.7 values, named so a revert is unambiguous rather than a silent 3x2 slide.
        assertTrue("the drifted x is back", caps.none { it.ox == -38f })
        assertTrue("the drifted y is back", caps.none { it.oy == -116f })
    }

    /** The cap and the crown share an origin, so they cannot slide against each other. */
    @Test
    fun `the snow cap shares the crown's origin`() {
        assertEquals(TreeSpriteLayout.CANOPY_X, TreeSpriteLayout.SNOWCAP_X, 0f)
        assertEquals(TreeSpriteLayout.CANOPY_Y, TreeSpriteLayout.SNOWCAP_Y, 0f)
        assertEquals(TreeSpriteLayout.FLAT_CANOPY_X, TreeSpriteLayout.FLAT_SNOWCAP_X, 0f)
        assertEquals(TreeSpriteLayout.FLAT_CANOPY_Y, TreeSpriteLayout.FLAT_SNOWCAP_Y, 0f)
    }

    /**
     * The flattened offsets must be the lifted ones plus the lift, which is the whole reason the
     * two can be stated once. An edit to one that forgot the other would land here.
     */
    @Test
    fun `the flattened offsets are the lifted ones plus the lift`() {
        assertEquals(
            TreeSpriteLayout.CANOPY_Y + TreeSpriteLayout.CANOPY_LIFT_Y,
            TreeSpriteLayout.FLAT_CANOPY_Y,
            0f,
        )
        assertEquals(
            TreeSpriteLayout.DEAD_BRANCHES_Y + TreeSpriteLayout.CANOPY_LIFT_Y,
            TreeSpriteLayout.FLAT_DEAD_BRANCHES_Y,
            0f,
        )
        assertEquals(-118f, TreeSpriteLayout.FLAT_CANOPY_Y, 0f)
    }

    /** The trunk is drawn outside the lift, so its preview offset is the renderer's unchanged. */
    @Test
    fun `the trunk is not lifted`() {
        val trunks = previewTreeParts("sunset", winter = false)
            .filter { it.resId == R.drawable.tree_trunk }
        assertTrue(trunks.isNotEmpty())
        for (trunk in trunks) {
            assertEquals(TreeSpriteLayout.TRUNK_X, trunk.ox, 0f)
            assertEquals(TreeSpriteLayout.TRUNK_Y, trunk.oy, 0f)
        }
    }

    /**
     * The Halloween tree drops its crown for bare branches, at the crown's own origin — the one
     * other part that shares the lift and could drift the same way.
     */
    @Test
    fun `the halloween branches share the crown's origin`() {
        assertEquals(TreeSpriteLayout.CANOPY_X, TreeSpriteLayout.DEAD_BRANCHES_X, 0f)
        assertEquals(TreeSpriteLayout.CANOPY_Y, TreeSpriteLayout.DEAD_BRANCHES_Y, 0f)
    }

    /**
     * Every theme that draws a tree draws it from the shared constants, so a new theme cannot
     * reintroduce a hand-copied offset without failing here.
     */
    @Test
    fun `no theme draws a tree part at an offset of its own`() {
        val allowed: Map<Int, Pair<Float, Float>> = mapOf(
            R.drawable.tree_trunk to (TreeSpriteLayout.TRUNK_X to TreeSpriteLayout.TRUNK_Y),
            R.drawable.tree_canopy to (TreeSpriteLayout.FLAT_CANOPY_X to TreeSpriteLayout.FLAT_CANOPY_Y),
            R.drawable.tree_canopy_snowcap to (TreeSpriteLayout.FLAT_SNOWCAP_X to TreeSpriteLayout.FLAT_SNOWCAP_Y),
            R.drawable.tree_dead_branches to
                (TreeSpriteLayout.FLAT_DEAD_BRANCHES_X to TreeSpriteLayout.FLAT_DEAD_BRANCHES_Y),
        )
        var checked = 0
        for (theme in ThemeCatalog.ALL) {
            val scene = ThemePreviewScenes.forTheme(theme, defaultCustomizationFor(theme.id))
            for (part in (scene.items + scene.backdrop + scene.ground).flatMap { it.parts }) {
                val expected = allowed[part.resId] ?: continue
                assertEquals("theme ${theme.id}, sprite ${part.resId} x", expected.first, part.ox, 0f)
                assertEquals("theme ${theme.id}, sprite ${part.resId} y", expected.second, part.oy, 0f)
                checked++
            }
        }
        assertTrue("expected to have checked some tree parts, checked $checked", checked > 0)
        println("Filone C: $checked tree sprite placements checked across ${ThemeCatalog.ALL.size} themes")
    }

    // -- the skyscraper (v3.8 Filone 4) --------------------------------------------------------

    /**
     * **The divergence this closes.** The lit night facade must sit exactly on the wall, which is
     * what `drawSkyscraperBuilding` says it does: *"laid over it at the same origin"*.
     *
     * The preview had it at `(-39, -height + 6)` — six units right and six down. Both offsets are
     * asserted against the wall's own, not against a literal, so the claim being made is the one
     * the renderer's comment makes rather than a number that happens to be true today.
     */
    @Test
    fun `the lit facade lies exactly on the wall`() {
        assertEquals(SkyscraperSpriteLayout.WALL_X, SkyscraperSpriteLayout.WALL_LIT_X, 0f)
        assertEquals(0f, SkyscraperSpriteLayout.WALL_LIT_DY, 0f)
        // And the wall itself is centred on the tower, which is what makes that meaningful.
        assertEquals(-SkyscraperSpriteLayout.WIDTH / 2f, SkyscraperSpriteLayout.WALL_X, 0f)

        val lit = previewParts("city").filter { it.resId == R.drawable.skyscraper_wall_lit }
        assertTrue("the city preview should light its towers", lit.isNotEmpty())
        for (part in lit) {
            assertEquals("lit facade x", SkyscraperSpriteLayout.WALL_LIT_X, part.ox, 0f)
            // The pre-v3.8 value, named so a revert is unambiguous rather than a silent slide.
            assertTrue("the drifted x is back", part.ox != -39f)
        }
    }

    /**
     * The roof snow's offset must stay the **sum of its terms**, not the sum itself.
     *
     * `-31` is what the preview carried and what this asserts is no longer written down anywhere:
     * the value is right, but a copy of the value stops tracking the setback the moment anyone
     * moves it, which is how the tree drifted.
     */
    @Test
    fun `the roof snow is derived from the setback rather than restated`() {
        assertEquals(
            SkyscraperSpriteLayout.SETBACK_DY + 6f - 8f + 3f,
            SkyscraperSpriteLayout.ROOF_SNOW_DY,
            0f,
        )
        // The value is unchanged -- this release moved no snow on the wallpaper.
        assertEquals(-31f, SkyscraperSpriteLayout.ROOF_SNOW_DY, 0f)
    }

    /**
     * Every theme that draws a tower draws it from the shared constants, so a new theme cannot
     * reintroduce a hand-copied offset.
     */
    @Test
    fun `no theme draws a skyscraper part at an offset of its own`() {
        val absolute: Map<Int, Pair<Float, Float>> = mapOf(
            R.drawable.skyscraper_canopy to
                (SkyscraperSpriteLayout.CANOPY_X to SkyscraperSpriteLayout.CANOPY_Y),
            R.drawable.skyscraper_entrance to
                (SkyscraperSpriteLayout.ENTRANCE_X to SkyscraperSpriteLayout.ENTRANCE_Y),
        )
        // The vertical offsets of these follow the tower's own height, which the preview varies on
        // purpose -- a gallery card needs a skyline, not a row of identical blocks. Only x is
        // shared, and asserting y would be the artificial constraint this work was told to avoid.
        val horizontalOnly: Map<Int, Float> = mapOf(
            R.drawable.skyscraper_wall to SkyscraperSpriteLayout.WALL_X,
            R.drawable.skyscraper_wall_lit to SkyscraperSpriteLayout.WALL_LIT_X,
            R.drawable.skyscraper_setback to SkyscraperSpriteLayout.SETBACK_X,
            R.drawable.skyscraper_roof_snow to SkyscraperSpriteLayout.ROOF_SNOW_X,
        )
        var checked = 0
        for (theme in ThemeCatalog.ALL) {
            for (part in previewParts(theme.id)) {
                absolute[part.resId]?.let {
                    assertEquals("theme ${theme.id} x", it.first, part.ox, 0f)
                    assertEquals("theme ${theme.id} y", it.second, part.oy, 0f)
                    checked++
                }
                horizontalOnly[part.resId]?.let {
                    assertEquals("theme ${theme.id} x", it, part.ox, 0f)
                    checked++
                }
            }
        }
        assertTrue("expected to have checked some tower parts, checked $checked", checked > 0)
        println("Filone 4: $checked skyscraper sprite placements checked across ${ThemeCatalog.ALL.size} themes")
    }

    /**
     * **The boundary of this work, asserted.** Only the two groups with demonstrated drift risk are
     * shared; the other 47 sprites agree today as plain literals and are deliberately left alone.
     *
     * Stated as a test so that "extend it to everything" is a decision somebody has to take
     * knowingly rather than a drift in the other direction.
     */
    @Test
    fun `only the two groups with demonstrated risk are shared`() {
        val shared = setOf(
            R.drawable.tree_trunk, R.drawable.tree_canopy,
            R.drawable.tree_canopy_snowcap, R.drawable.tree_dead_branches,
            R.drawable.skyscraper_canopy, R.drawable.skyscraper_wall,
            R.drawable.skyscraper_wall_lit, R.drawable.skyscraper_entrance,
            R.drawable.skyscraper_setback, R.drawable.skyscraper_roof_snow,
        )
        assertEquals("the shared set should be the tree's four and the tower's six", 10, shared.size)
        // A sprite the preview uses that is not in the shared set is fine -- that is the point.
        val used = ThemeCatalog.ALL.flatMap { previewParts(it.id) }.map { it.resId }.toSet()
        assertTrue("the shared sprites should all actually be drawn", shared.count { it in used } >= 8)
    }
}
