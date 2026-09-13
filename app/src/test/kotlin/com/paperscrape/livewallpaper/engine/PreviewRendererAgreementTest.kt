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

    // -- the neighbourhood (v5.0) -------------------------------------------------------------

    /**
     * The three tests this replaces, and why one replaces them.
     *
     * v3.8 and v4.19 found three copies drifting -- the tower's lit facade six units off its own
     * wall, its roof snow carrying a folded sum instead of its terms, and both shops' winter
     * drifts left at their pre-cornice origins for two releases -- and closed each by hoisting the
     * offending offsets into constants both sides read. That was the right size of fix for a flat
     * facade: a handful of literals, shared.
     *
     * v5.0 removed the thing those fixes were guarding. A building is no longer a list of
     * literals in two places; it is a stack dealt from one table by one composer, and the preview
     * calls that composer. So the property is no longer "these particular numbers match" but the
     * stronger one underneath it: **for the same identity the two sides produce the same parts at
     * the same coordinates**, every part, every family, every deal -- which is what this asserts.
     * There is no copy left to drift, and if somebody writes one, this fails.
     */
    private fun expectedParts(
        variant: SceneSpace.SceneVariant,
        tileX: Float,
        depth: Float,
        winter: Boolean,
    ): List<Triple<Int, Float, Float>> {
        val family = NeighbourhoodTable.FAMILIES.getValue(variant)
        val deal = NeighbourhoodComposer.Deal()
        NeighbourhoodComposer.deal(family, tileX, depth, deal)
        val out = mutableListOf<Triple<Int, Float, Float>>()
        for (i in 0 until deal.size) {
            val placed = deal[i]
            for (part in placed.piece.parts) {
                val keep = when (part.role) {
                    PartRole.FIXED, PartRole.WALL_MASK, PartRole.GLASS_MASK -> true
                    PartRole.SNOW -> winter
                    PartRole.LAMP, PartRole.OCCUPANTS -> false
                }
                if (keep) out += Triple(part.res, part.x, placed.baseY + part.y)
            }
        }
        return out
    }

    /** The identity each preview slot declares, paired with the family it draws. */
    private val previewSlots: List<Triple<SceneSpace.SceneVariant, Float, Float>> = buildList {
        for (i in 0 until 4) {
            add(Triple(
                SceneSpace.SceneVariant.TOWER,
                ThemePreviewScenes.PreviewIdentity.TOWER_X[i],
                ThemePreviewScenes.PreviewIdentity.TOWER_DEPTH[i],
            ))
        }
        add(Triple(SceneSpace.SceneVariant.RESTAURANT,
            ThemePreviewScenes.PreviewIdentity.RESTAURANT_X, ThemePreviewScenes.PreviewIdentity.RESTAURANT_DEPTH))
        add(Triple(SceneSpace.SceneVariant.BAR,
            ThemePreviewScenes.PreviewIdentity.BAR_X, ThemePreviewScenes.PreviewIdentity.BAR_DEPTH))
        add(Triple(SceneSpace.SceneVariant.HOUSE_LARGE,
            ThemePreviewScenes.PreviewIdentity.HOUSE_LARGE_X, ThemePreviewScenes.PreviewIdentity.HOUSE_LARGE_DEPTH))
        add(Triple(SceneSpace.SceneVariant.HOUSE_SMALL,
            ThemePreviewScenes.PreviewIdentity.HOUSE_SMALL_X, ThemePreviewScenes.PreviewIdentity.HOUSE_SMALL_DEPTH))
    }

    /** Every sprite any family of the neighbourhood can place. */
    private val buildingSprites: Set<Int> = NeighbourhoodTable.FAMILIES.values
        .flatMap { it.slots }.flatMap { it.options }
        .flatMap { piece -> piece.parts.filter { it.res != 0 }.map { it.res } }
        .toSet()

    @Test
    fun `every building the gallery draws is the deal the wallpaper would deal`() {
        var checked = 0
        for (theme in ThemeCatalog.ALL) {
            val customization = defaultCustomizationFor(theme.id)
            val scene = ThemePreviewScenes.forTheme(ThemeCatalog.byId(theme.id), customization)
            val winter = customization.winterColorsEnabled
            for (item in scene.items) {
                val parts = item.parts.filter { it.resId in buildingSprites }
                if (parts.isEmpty()) continue
                val actual = parts.map { Triple(it.resId, it.ox, it.oy) }
                val match = previewSlots.any { (variant, tileX, depth) ->
                    expectedParts(variant, tileX, depth, winter) == actual
                }
                assertTrue(
                    "theme ${theme.id}: a preview building's parts are not any deal the composer " +
                        "would produce for a declared identity -- ${actual.size} parts starting " +
                        "${actual.firstOrNull()}",
                    match,
                )
                checked++
            }
        }
        assertTrue("expected to have checked some buildings, checked $checked", checked > 0)
        println("v5.0: $checked preview buildings checked against the composer's own deal")
    }

    /**
     * A preview building wears one of its category's two colours, and the one the wallpaper would
     * give it.
     *
     * The preview used to invent its own: a tower 15 % towards white, a restaurant 30 %, a bar
     * 15 % towards black -- so a gallery card showed the user a colour no building of theirs
     * could ever be, and the eight editable colours were not what the card was showing. The wall
     * masks now carry `colorFor` exactly, which is also what makes the card react to an edit.
     */
    @Test
    fun `a preview building wears one of its category's two colours`() {
        var checked = 0
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id)
            val scene = ThemePreviewScenes.forTheme(ThemeCatalog.byId(theme.id), c)
            val night = c.horrorSkyEnabled || ThemeCatalog.byId(theme.id).hasFireworks
            val dayBlend = if (night) 0f else 1f
            val allowed = listOf(
                SceneObjectType.HOUSE to c.houses,
                SceneObjectType.SKYSCRAPER to c.buildings,
            ).flatMap { (_, config) ->
                listOf(
                    SceneColour.blendArgb(config.colorNight1, config.colorDay1, dayBlend),
                    SceneColour.blendArgb(config.colorNight2, config.colorDay2, dayBlend),
                )
            }.toSet()
            for (item in scene.items) {
                for (part in item.parts) {
                    if (part.resId !in buildingSprites || !part.added) continue
                    val tint = part.tint ?: continue
                    // The glass masks carry the window ramp, not the wall; they are the two
                    // constants every window in the scene reads.
                    if (tint == SceneObjectRenderer.windowGlassColor(if (night) 1f else 0f)) continue
                    assertTrue(
                        "theme ${theme.id}: a wall mask is tinted ${Integer.toHexString(tint)}, " +
                            "which is neither of its category's two colours",
                        tint in allowed,
                    )
                    checked++
                }
            }
        }
        assertTrue("expected to have checked some wall masks, checked $checked", checked > 0)
    }

    /**
     * A weight mask is **summed**, in the gallery as in the scene.
     *
     * The masks are weights over a fixed layer -- the people's system since v4.30 -- and drawing
     * one with a plain tint paints the wall colour over the ink it is meant to be added to. The
     * preview had no notion of an added blit until v5.0; this is what says it still has one.
     */
    @Test
    fun `every mask the gallery draws is an added blit and every fixed layer is not`() {
        val masks = NeighbourhoodTable.FAMILIES.values.flatMap { it.slots }.flatMap { it.options }
            .flatMap { piece ->
                piece.parts.filter { it.role == PartRole.WALL_MASK || it.role == PartRole.GLASS_MASK }
            }.map { it.res }.toSet()
        var checked = 0
        for (theme in ThemeCatalog.ALL) {
            for (part in previewParts(theme.id)) {
                if (part.resId !in buildingSprites) continue
                if (part.resId in masks) {
                    assertTrue("a mask must be an added blit", part.added)
                    assertTrue("a mask must carry a tint", part.tint != null)
                } else {
                    assertTrue("fixed art must not be an added blit", !part.added)
                    assertTrue("fixed art must not be tinted", part.tint == null)
                }
                checked++
            }
        }
        assertTrue("expected to have checked some parts, checked $checked", checked > 0)
    }

    /**
     * **The boundary of this work, asserted.** The tree is still shared through constants; the
     * neighbourhood is shared through the table and the composer. Everything else in the preview
     * agrees today as plain literals and is deliberately left alone.
     *
     * Stated as a test so that "extend it to everything" is a decision somebody has to take
     * knowingly rather than a drift in the other direction.
     */
    @Test
    fun `only the groups with demonstrated risk are shared`() {
        val sharedByConstant = setOf(
            R.drawable.tree_trunk, R.drawable.tree_canopy,
            R.drawable.tree_canopy_snowcap, R.drawable.tree_dead_branches,
        )
        assertEquals("the tree's four", 4, sharedByConstant.size)
        assertEquals(
            "the five families of the neighbourhood, shared through the table",
            5, NeighbourhoodTable.FAMILIES.size,
        )
        val used = ThemeCatalog.ALL.flatMap { previewParts(it.id) }.map { it.resId }.toSet()
        assertTrue("the tree's shared sprites should all actually be drawn", sharedByConstant.count { it in used } >= 2)
        assertTrue("the neighbourhood's sprites should actually be drawn", buildingSprites.count { it in used } >= 8)
    }
}
