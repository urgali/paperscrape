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
 * This test is the guard on that. It cannot check the other 55 agreements, because their nested
 * transforms live inside the renderer's draw functions and only a person can read them off; the
 * audit that did so is recorded in `RELEASE_HISTORY.md` for v3.7.
 */
class PreviewRendererAgreementTest {

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
}
