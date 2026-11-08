package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.ui.SettingsUiModel
import com.paperscrape.livewallpaper.ui.snapshotEntry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **Trees can be switched on on every theme** (`DESIGN_NOTES.md` §12, decision 97): a random, saved or imported theme whose
 * street has no tree and no palm is given ten from its own seed by the built-in streets' rule
 * ([SceneObjectCatalog.withTrees]), thinned by the Trees page's *Density*, and a random theme whose own trees keep none at
 * the factory amount is given the ten beside its own, and another seed's ten where none of those stands. Held here: a bare street gets ten, and a street with trees or palms,
 * not random, is left as it is; the twelve built-in streets are left as they are, ten trees each; every random street the
 * catalogue lays has trees and keeps what it was born with; no added tree stands with its trunk across a place's front; a
 * save keeps its trees. That a random theme's trees stand as it ships is held by `RandomThemesShowWhatIsOnTest`.
 */
class TreesOnEveryThemeTest {

    @Before
    fun clearRegistry() = CustomThemeRegistry.update(CustomThemeData.EMPTY)

    @After
    fun clearRegistryAfter() = CustomThemeRegistry.update(CustomThemeData.EMPTY)

    private val trees = setOf(SceneObjectType.TREE, SceneObjectType.PALM_TREE)

    private fun SceneObjectLayout.treeCount() = staticObjects.count { it.type in trees }

    @Test
    fun `a street with no tree gets ten from its own seed, and one with trees or palms keeps its own`() {
        val bare = SceneObjectLayout(
            staticObjects = listOf(StaticSceneObject(SceneObjectType.HOUSE, depthFraction = 0.5f, tileFractionX = 0.3f)),
            cars = emptyList(),
        )
        val planted = SceneObjectCatalog.withTrees(bare, "random:42")
        assertEquals(10, planted.treeCount())
        val added = planted.staticObjects.filter { it.type == SceneObjectType.TREE }
        for ((i, t) in added.withIndex()) {
            assertEquals("one in each tenth of the tile", i, (t.tileFractionX * 10f).toInt().coerceIn(0, 9))
            assertTrue(t.depthFraction in 0.18f..1.0f)
        }
        assertEquals("the same street every time", planted, SceneObjectCatalog.withTrees(bare, "random:42"))
        assertTrue("another id, other trees", planted != SceneObjectCatalog.withTrees(bare, "random:43"))
        for (type in trees) {
            val withOne = bare.copy(staticObjects = bare.staticObjects + StaticSceneObject(type, 0.5f, 0.7f))
            assertEquals(type.name, withOne, SceneObjectCatalog.withTrees(withOne, "random:42"))
        }
    }

    @Test
    fun `the twelve built-in streets are left as they are, ten trees each`() {
        for (theme in ThemeCatalog.ALL) {
            val layout = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor)
            assertEquals(theme.id, layout, SceneObjectCatalog.withTrees(layout, theme.id))
            assertEquals(theme.id, 10, layout.treeCount())
        }
    }

    /** About four random themes in ten are born with no tree, as the generator rolls them; as the catalogue lays them, all have trees. */
    @Test
    fun `every random theme has trees, its generation untouched`() {
        var bornBare = 0
        var n = 0
        for (k in 1..600) {
            val born = runCatching { RandomSceneGenerator.generateLayout("random:$k", 0) }.getOrNull() ?: continue
            n++
            val laid = SceneObjectCatalog.layoutFor("random:$k", 0)
            assertTrue("random:$k has no tree", laid.treeCount() > 0)
            if (born.treeCount() == 0) {
                bornBare++
                assertTrue("random:$k: ten added, and another ten where none of those stands, those across a door left out", laid.treeCount() in 1..20)
                // Everything it was born with is still there.
                assertTrue(laid.staticObjects.containsAll(born.staticObjects))
            }
        }
        assertTrue("$bornBare of $n born without a tree", bornBare in (n / 5)..(n / 2))
    }

    /** No trunk across a door: an added tree in front of a place does not cross its front. */
    @Test
    fun `no added tree crosses a place's front`() {
        var checked = 0
        for (k in 1..2000) {
            val born = runCatching { RandomSceneGenerator.generateLayout("random:$k", 0) }.getOrNull() ?: continue
            if (born.treeCount() != 0) continue
            val places = born.staticObjects.filter { StorefrontStreet.isPlace(it) }
            if (places.isEmpty()) continue
            val laid = SceneObjectCatalog.withTrees(born, "random:$k")
            for (tree in laid.staticObjects.filter { it.type == SceneObjectType.TREE }) for (place in places) {
                if (tree.depthFraction <= place.depthFraction) continue
                val trunk = SceneObjectCatalog.occluderBoxes(tree).last()
                val front = SceneObjectCatalog.frontRect(place)
                for (shift in floatArrayOf(-2160f, 0f, 2160f)) {
                    assertFalse("random:$k: a trunk across a door", trunk[0] + shift < front[2] && trunk[2] + shift > front[0])
                }
                checked++
            }
        }
        assertTrue("nothing checked", checked > 50)
    }

    /**
     * A theme saved with *Show Trees* off stores its ten trees (`snapshotEntry` keeps every candidate, decision 102); one
     * stored with no tree is given trees when loaded, and the switch, off as it was saved, keeps them off until it is turned
     * on; turned on, they stand. The *Show Trees* line is a theme with trees' line.
     */
    @Test
    fun `a theme saved with trees off keeps them, and one stored with no tree gets trees, off until Show Trees is turned on`() {
        val autumn = defaultCustomizationFor("autumn")
        val saved = snapshotEntry("custom:bare", "Autumn without trees", "autumn", autumn.copy(trees = autumn.trees.copy(visible = false)), "autumn")
        assertEquals("a save keeps every candidate, the trees off among them (decision 102)", 10, saved.layout.treeCount())
        // The same theme stored with no tree.
        val entry = saved.copy(layout = saved.layout.copy(staticObjects = saved.layout.staticObjects.filter { it.type != SceneObjectType.TREE }))
        assertEquals("the entry stores no tree", 0, entry.layout.treeCount())
        CustomThemeRegistry.update(CustomThemeData(customThemes = listOf(entry)))
        val street = SceneObjectCatalog.layoutFor(entry.id, 0)
        assertTrue("up to ten, those across a door left out", street.treeCount() in 1..10)
        val off = SettingsUiModel.standing(street, entry.customization)
        assertTrue(off.themeHasTrees)
        assertFalse("off as saved", SettingsUiModel.amountSwitch(entry.customization.trees.visible, entry.customization.trees.density, off.trees, off.themeHasTrees).shownOn)
        val on = entry.customization.copy(trees = entry.customization.trees.copy(visible = true))
        val standing = SettingsUiModel.standing(street, on)
        val shown = SettingsUiModel.amountSwitch(on.trees.visible, on.trees.density, standing.trees, standing.themeHasTrees)
        assertTrue("turned on, they stand", shown.shownOn)
        assertFalse(shown.noneOnThisTheme)
        assertTrue(street.staticObjects.any { it.type == SceneObjectType.TREE && on.keepCandidate(it, street.densityScheme) })
    }
}
