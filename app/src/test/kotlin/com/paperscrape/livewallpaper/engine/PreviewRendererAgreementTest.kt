package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gallery preview and the wallpaper must assemble a tree, a palm and a building the same way.
 *
 * `ThemePreviewScenes` builds some of its objects from the same sprites at the same offsets as
 * `SceneObjectRenderer`, as plain literals on both sides, with no transform to fold and no arithmetic
 * to get wrong. Where an offset is a sum of the renderer's nested transforms it is where a hand copy
 * drifts -- the winter tree's snow cap at `(-38,-116)` on the card against `(-41,-80)` under
 * `translate(0,-38)`, i.e. `(-41,-118)`, on the wallpaper, 3 units right and 2 down -- so those are
 * read from one place by both sides: the tree from [TreeSpriteLayout], the palm from
 * [PalmSpriteLayout].
 *
 * The preview stays its own flat 320x240 data description, with no perspective, no candidate system
 * and no scroll: routing it through the wallpaper's renderer would mean giving it all three. No
 * building is copied by hand: both sides deal from [NeighbourhoodComposer], and the building case
 * below asserts the same parts at the same coordinates.
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
        // The drifted hand copy's values, named so a revert is unambiguous rather than a silent 3x2 slide.
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
     * The palm's two origins are read from [PalmSpriteLayout] by both sides.
     *
     * Neither is a plain number a hand copy keeps easily: the crown sits on a larger canvas built
     * around its declared attachment, the trunk on a foot that is not its content's centre because
     * the spindle leans. Two hand copies of such numbers are how the tree's snow cap slid three
     * units right, so there is one copy.
     */
    @Test
    fun `the preview palm is blitted at the renderer's own two origins`() {
        for (themeId in listOf("beach", "desert")) {
            val parts = previewParts(themeId)
            val trunks = parts.filter { it.resId == R.drawable.palmtree_trunk }
            val crowns = parts.filter { it.resId == R.drawable.palmtree_fronds }
            assertTrue("$themeId draws no palm trunk", trunks.isNotEmpty())
            assertTrue("$themeId draws no palm crown", crowns.isNotEmpty())
            for (trunk in trunks) {
                assertEquals("$themeId trunk x", PalmSpriteLayout.TRUNK_X, trunk.ox, 0f)
                assertEquals("$themeId trunk y", PalmSpriteLayout.TRUNK_Y, trunk.oy, 0f)
            }
            for (crown in crowns) {
                assertEquals("$themeId crown x", PalmSpriteLayout.CROWN_X, crown.ox, 0f)
                assertEquals("$themeId crown y", PalmSpriteLayout.CROWN_Y, crown.oy, 0f)
            }
            // The palm's older pair, the crown at (-20,-90.33) over a trunk at (-6,-58), named so a
            // half-finished revert is unambiguous rather than a silent slide.
            assertTrue("$themeId is back on the older trunk origin", trunks.none { it.ox == -6f })
            assertTrue("$themeId is back on the older crown origin", crowns.none { it.oy == -90.33f })
        }
    }

    /**
     * All three palm crowns blit at one origin, which is what makes the winter one a choice rather
     * than a layer.
     *
     * The crowns are three whole drawings on one canvas, and the renderer picks one: the frost is not
     * laid over the live crown. If a future redraw gave any of them its own origin, the palm would start
     * changing shape when the season did, and it would do it silently.
     */
    @Test
    fun `the three palm crowns share one origin on both sides`() {
        val theme = ThemeCatalog.byId("beach")
        val base = defaultCustomizationFor("beach")
        val origins = listOf(
            base to R.drawable.palmtree_fronds,
            base.copy(winterColorsEnabled = true) to R.drawable.palmtree_fronds_frost,
            base.copy(halloweenEnabled = true) to R.drawable.palmtree_fronds_dead,
        ).map { (customization, resId) ->
            val scene = ThemePreviewScenes.forTheme(theme, customization)
            val parts = (scene.items + scene.backdrop + scene.ground).flatMap { it.parts }
                .filter { it.resId == resId }
            assertTrue("no crown drawn for $resId", parts.isNotEmpty())
            parts.map { it.ox to it.oy }.distinct()
        }
        for (crown in origins) {
            assertEquals("a crown is drawn at more than one origin", 1, crown.size)
            assertEquals(PalmSpriteLayout.CROWN_X to PalmSpriteLayout.CROWN_Y, crown.single())
        }
        // And the winter one is drawn *instead of* the live one, not on top of it.
        val winter = ThemePreviewScenes.forTheme(theme, base.copy(winterColorsEnabled = true))
        val winterParts = (winter.items + winter.backdrop + winter.ground).flatMap { it.parts }
        assertTrue(
            "the frosted crown is still being stacked on the live one",
            winterParts.none { it.resId == R.drawable.palmtree_fronds },
        )
    }

    /**
     * The crown's centre is off the trunk, and everything hung on it reads the same number.
     *
     * This is the consequence of the lean that reaches furthest: the Christmas lights, the falling
     * leaves and the occlusion box are all placed on the crown's content, which is not centred on
     * the pivot. Stating the centre once is what stops any of them assuming it is.
     */
    @Test
    fun `the palm crown's centre is derived from its own blit`() {
        assertEquals(
            PalmSpriteLayout.CROWN_X + PalmSpriteLayout.CROWN_HALF_WIDTH,
            PalmSpriteLayout.CROWN_CENTRE_X,
            0f,
        )
        assertEquals(
            PalmSpriteLayout.CROWN_Y + PalmSpriteLayout.CROWN_HALF_HEIGHT,
            PalmSpriteLayout.CROWN_CENTRE_Y,
            0f,
        )
        assertTrue(
            "a palm crown is not centred on its own trunk",
            PalmSpriteLayout.CROWN_CENTRE_X != 0f,
        )
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
        println("Trees: $checked tree sprite placements checked across ${ThemeCatalog.ALL.size} themes")
    }

    // -- the neighbourhood ---------------------------------------------------------------------

    /**
     * The parts the composer deals for a building of [family] at ([tileX], [depth]).
     *
     * A building is not a list of literals in two places; it is a stack dealt from one table by one
     * composer, and the preview calls that composer. So the property is not "these particular
     * numbers match" but the one underneath it: **every building on the card is a stack the
     * composer deals for the identity the card declares**, every part, every family -- which is what
     * this asserts. There is no copy of the stacking to drift, and if somebody writes one, this fails.
     *
     * **What it does not show**: "the deal the wallpaper would deal". The wallpaper deals a building
     * from the silhouette the generator recorded on the object
     * (`NeighbourhoodComposer.deal(family, spec, ...)`), and deals from the
     * position only for an undealt one; the card always deals from a position it picks for a
     * picture (`PreviewIdentity`, left alone by decision). So the card's stacks are real stacks of
     * the table, built the wallpaper's way, and not the particular stacks any theme's street shows.
     */
    private fun expectedParts(
        family: BuildingFamily,
        tileX: Float,
        depth: Float,
        winter: Boolean,
    ): List<Triple<Int, Float, Float>> {
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

    /**
     * The identity each preview slot declares, paired with a family it may draw: a tower and a house
     * their own, and each of the card's three rows among the houses any of the six buildings
     * ([Storefront]) -- which of them a theme's card draws is the street's first stretch.
     */
    private val previewSlots: List<Triple<BuildingFamily, Float, Float>> = buildList {
        for (i in 0 until 4) {
            add(Triple(
                NeighbourhoodTable.FAMILIES.getValue(SceneSpace.SceneVariant.TOWER),
                ThemePreviewScenes.PreviewIdentity.TOWER_X[i],
                ThemePreviewScenes.PreviewIdentity.TOWER_DEPTH[i],
            ))
        }
        // The school's row among the three: every theme builds one, and its absence from this list would
        // go unnoticed, since the assertion below only checks buildings the card actually draws.
        for (row in listOf(
            ThemePreviewScenes.PreviewIdentity.RESTAURANT_X to ThemePreviewScenes.PreviewIdentity.RESTAURANT_DEPTH,
            ThemePreviewScenes.PreviewIdentity.SCHOOL_X to ThemePreviewScenes.PreviewIdentity.SCHOOL_DEPTH,
            ThemePreviewScenes.PreviewIdentity.BAR_X to ThemePreviewScenes.PreviewIdentity.BAR_DEPTH,
        )) {
            for (b in Storefront.ALL) add(Triple(b.family, row.first, row.second))
        }
        add(Triple(NeighbourhoodTable.FAMILIES.getValue(SceneSpace.SceneVariant.HOUSE_LARGE),
            ThemePreviewScenes.PreviewIdentity.HOUSE_LARGE_X, ThemePreviewScenes.PreviewIdentity.HOUSE_LARGE_DEPTH))
        add(Triple(NeighbourhoodTable.FAMILIES.getValue(SceneSpace.SceneVariant.HOUSE_SMALL),
            ThemePreviewScenes.PreviewIdentity.HOUSE_SMALL_X, ThemePreviewScenes.PreviewIdentity.HOUSE_SMALL_DEPTH))
    }

    /** Every family a card building may be drawn as, by name: the houses', the tower's, the six's. */
    private val drawableFamilies: Map<String, BuildingFamily> =
        NeighbourhoodTable.FAMILIES.entries.associate { it.key.name to it.value } +
            Storefront.ALL.associate { it.name to it.family }

    /** Every sprite any family of the neighbourhood can place. */
    private val buildingSprites: Set<Int> = drawableFamilies.values
        .flatMap { it.slots }.flatMap { it.options + it.heights }
        .flatMap { piece -> piece.parts.filter { it.res != 0 }.map { it.res } }
        .toSet()

    /** The families each building sprite belongs to: what a card building is drawn as, read off its parts. */
    private val familiesOfSprite: Map<Int, Set<String>> = HashMap<Int, MutableSet<String>>().also { out ->
        for ((name, family) in drawableFamilies) {
            for (piece in family.slots.flatMap { it.options + it.heights }) {
                for (part in piece.parts) if (part.res != 0) out.getOrPut(part.res) { mutableSetOf() } += name
            }
        }
    }

    /** The pair a building drawn as [name] wears: a house's or a tower's, or one of the six's group's. */
    private fun pairOf(c: SceneCustomization, name: String): ObjectVariantConfig =
        Storefront.ALL.firstOrNull { it.name == name }?.let { c.storefrontColours(it) }
            ?: c.buildingColoursFor(SceneSpace.SceneVariant.valueOf(name))!!

    @Test
    fun `every building the gallery draws is a deal the composer makes for its declared identity`() {
        var checked = 0
        for (theme in ThemeCatalog.ALL) {
            val customization = defaultCustomizationFor(theme.id)
            val scene = ThemePreviewScenes.forTheme(ThemeCatalog.byId(theme.id), customization)
            val winter = customization.winterColorsEnabled
            for (item in scene.items) {
                val parts = item.parts.filter { it.resId in buildingSprites }
                if (parts.isEmpty()) continue
                val actual = parts.map { Triple(it.resId, it.ox, it.oy) }
                val match = previewSlots.any { (family, tileX, depth) ->
                    expectedParts(family, tileX, depth, winter) == actual
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
     * No colour of the card's own -- a tower some way towards white, a bar towards black -- which
     * would show the user a colour no building of theirs could ever be. The wall masks carry
     * `colorFor` exactly, which is also what makes the card react to an edit.
     *
     * **The pair of what it is drawn as**: a tower the towers' pair, the restaurant, the
     * bar and the shop the shops', the school and the two stations the public buildings', a house the
     * houses' -- the family read off the building's own sprites, since the card stands its towers at
     * depths the scene keeps for the street's places. On every built-in the three groups' pairs differ,
     * so a building coloured by the wrong one is caught.
     */
    @Test
    fun `a preview building wears one of its category's two colours`() {
        var checked = 0
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id)
            val scene = ThemePreviewScenes.forTheme(ThemeCatalog.byId(theme.id), c)
            val night = c.horrorSkyEnabled || ThemeCatalog.byId(theme.id).hasFireworks
            // The card's own moment of the day, a real one: noon, midnight,
            // or Sunset's hour before sunset -- where a wall is between its two
            // colours exactly as the wallpaper's is at that minute.
            val dayBlend = ThemePreviewScenes.cardPhase(ThemeCatalog.byId(theme.id), night).dayBlend
            assertTrue("${theme.id}: towers and shops share a colour", c.buildings.colorDay1 != c.shops.colorDay1)
            assertTrue("${theme.id}: shops and public buildings share a colour", c.publics.colorDay1 != c.shops.colorDay1)
            for (item in scene.items) {
                val drawnAs = item.parts.mapNotNull { familiesOfSprite[it.resId] }.reduceOrNull { a, b -> a intersect b } ?: continue
                assertEquals("theme ${theme.id}: a card building of no single family: $drawnAs", 1, drawnAs.size)
                val config = pairOf(c, drawnAs.single())
                val allowed = setOf(
                    SceneColour.blendArgb(config.colorNight1, config.colorDay1, dayBlend),
                    SceneColour.blendArgb(config.colorNight2, config.colorDay2, dayBlend),
                )
                for (part in item.parts) {
                    if (part.resId !in buildingSprites || !part.added) continue
                    val tint = part.tint ?: continue
                    // The glass masks carry the window ramp, not the wall; they are the two
                    // constants every window in the scene reads.
                    if (tint == SceneObjectRenderer.windowGlassColor(1f - dayBlend)) continue
                    // And a house's glass at night is the dark glass of its unlit windows, its lit
                    // ones flat cards over it: neither is a wall.
                    if (tint == SceneObjectRenderer.unlitWindowGlassColor(1f - dayBlend) || part.rectWidth > 0f) continue
                    assertTrue(
                        "theme ${theme.id}: a ${drawnAs.single()}'s wall mask is tinted ${Integer.toHexString(tint)}, " +
                            "which is neither of its pair's two colours",
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
     * The masks are weights over a fixed layer -- the people's system too -- and drawing one with a
     * plain tint paints the wall colour over the ink it is meant to be added to. This is what says
     * the preview draws them as added blits.
     */
    @Test
    fun `every mask the gallery draws is an added blit and every fixed layer is not`() {
        val masks = drawableFamilies.values.flatMap { it.slots }.flatMap { it.options + it.heights }
            .flatMap { piece ->
                piece.parts.filter { it.role == PartRole.WALL_MASK || it.role == PartRole.GLASS_MASK }
            }.map { it.res }.toSet()
        val roofSnow = drawableFamilies.values.flatMap { it.slots }.flatMap { it.options + it.heights }
            .flatMap { piece -> piece.parts.filter { it.role == PartRole.SNOW } }.map { it.res }.toSet()
        var checked = 0
        var snowChecked = 0
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id)
            val night = c.horrorSkyEnabled || ThemeCatalog.byId(theme.id).hasFireworks || c.nightOverDayActive()
            val snow = GroundSnow.snowAt(c, ThemePreviewScenes.cardPhase(ThemeCatalog.byId(theme.id), night).dayBlend)
            for (part in previewParts(theme.id)) {
                if (part.resId !in buildingSprites) continue
                if (part.resId in masks) {
                    assertTrue("a mask must be an added blit", part.added)
                    assertTrue("a mask must carry a tint", part.tint != null)
                } else if (part.resId in roofSnow) {
                    // The roof's snow is fixed art multiplied by the card's snow colour, the wallpaper's at the card's
                    // moment, and drawn only under the winter palette ([GroundSnow], decision 117).
                    assertTrue("roof snow must not be an added blit", !part.added)
                    assertTrue("roof snow only under the winter palette", c.winterColorsEnabled)
                    assertEquals("${theme.id}: roof snow in the snow's colour", snow, part.tint)
                    snowChecked++
                } else {
                    assertTrue("fixed art must not be an added blit", !part.added)
                    assertTrue("fixed art must not be tinted", part.tint == null)
                }
                checked++
            }
        }
        assertTrue("expected to have checked some roof snow, checked $snowChecked", snowChecked > 0)
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
            // Six: the two houses, the tower, the two shops and the school -- the families with a row of
            // the size table; the shop and the two stations are three more, in
            // `NeighbourhoodTable.STOREFRONTS` (`drawableFamilies` above reads both).
            "the six families with a row of the size table, shared through the table",
            6, NeighbourhoodTable.FAMILIES.size,
        )
        val used = ThemeCatalog.ALL.flatMap { previewParts(it.id) }.map { it.resId }.toSet()
        assertTrue("the tree's shared sprites should all actually be drawn", sharedByConstant.count { it in used } >= 2)
        assertTrue("the neighbourhood's sprites should actually be drawn", buildingSprites.count { it in used } >= 8)
    }
}
