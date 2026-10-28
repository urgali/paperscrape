package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.CustomThemeRegistry
import com.paperscrape.livewallpaper.engine.DecorationExclusion
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.SceneObjectCatalog
import com.paperscrape.livewallpaper.engine.SceneObjectLayout
import com.paperscrape.livewallpaper.engine.SceneObjectType
import com.paperscrape.livewallpaper.engine.StorefrontStreet
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import com.paperscrape.livewallpaper.engine.keepCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **A switch of the street reads on only where one of its objects stands** (`DESIGN_NOTES.md` §12, decision 40).
 * The houses, towers, trees, parasols and
 * decorations stand where `keepCandidate` keeps one, and a candidate's fraction has no minimum: above 0 % a theme can keep
 * none -- the towers at 10 %, the trees at 4 %. There the switch reads off, its line says the amount is why, the stored
 * visibility and amount are kept, and a tap brings them back; the lights on the trees and the Halloween decorations, which
 * hang on the trees and the houses, read off with their reason too, and a switch that reads off holds the other side off
 * neither. *Show people* with nobody at any hour -- both densities at 0 % -- reads off too.
 *
 * The rule is `SettingsUiModel` and `DecorationExclusion` given the street (`standing`, `standsAny`); without the street
 * they read on wherever the amount is above 0 % (`amountSwitch(visible, amount)`, `treesStand(c)`); `towerHours` and the
 * *Towers & offices* row take the street's answer, with no default. The facts the tests start from -- a theme that keeps
 * no tree at 4 % -- are `keepCandidate`'s.
 */
class SwitchesOverAStreetThatKeepsNoneTest {

    @Before
    fun clearRegistry() = CustomThemeRegistry.update(CustomThemeData.EMPTY)

    private fun layout(id: String): SceneObjectLayout = SceneObjectCatalog.layoutFor(id, ThemeCatalog.byId(id).accentColor)

    /** Whether [layout] keeps one of [types] under [c] -- `keepCandidate` itself, written out here. */
    private fun keepsAny(layout: SceneObjectLayout, c: SceneCustomization, types: Set<SceneObjectType>): Boolean =
        layout.staticObjects.any { it.type in types && !StorefrontStreet.isPlace(it) && c.keepCandidate(it, layout.densityScheme) }

    /** The built-in themes whose street keeps none of [types] under [c] (and which keep some at 100 %). */
    private fun themesKeepingNone(types: Set<SceneObjectType>, c: (String) -> SceneCustomization, full: (String) -> SceneCustomization) =
        ThemeCatalog.ALL.map { it.id }.filter { id ->
            !keepsAny(layout(id), c(id), types) && keepsAny(layout(id), full(id), types)
        }

    private fun trees(id: String, d: Float) = defaultCustomizationFor(id).let { it.copy(trees = it.trees.copy(visible = true, density = d)) }
    private fun towers(id: String, d: Float) = defaultCustomizationFor(id).let { it.copy(buildings = it.buildings.copy(visible = true, density = d)) }

    @Test
    fun `trees at 4 percent that keep none read off, say so, and a tap brings the theme's own amount back`() {
        val bare = themesKeepingNone(DecorationExclusion.TREE_TYPES, { trees(it, 0.04f) }, { trees(it, 1f) })
        println("themes keeping no tree at 4 %: $bare")
        assertTrue("no built-in theme keeps no tree at 4 %: the case is not exercised", bare.isNotEmpty())
        for (id in bare) {
            val c = trees(id, 0.04f)
            val standing = SettingsUiModel.standing(layout(id), c)
            assertFalse("$id: a tree stands", standing.trees)
            val shown = SettingsUiModel.amountSwitch(c.trees.visible, c.trees.density, standing.trees)
            assertFalse("$id: Show Trees reads on over no tree", shown.shownOn)
            assertTrue("$id: and says why", shown.noneAtThisAmount)
            assertFalse(shown.noneAtZero)
            assertEquals("None at 4% density on this theme - tap to bring them back", SettingsUiModel.noneAtThisAmountLine(c.trees.density))
            val default = defaultCustomizationFor(id).trees.density
            assertEquals(AmountTap.Restore(default), SettingsUiModel.amountTap(true, c.trees.density, default, standing.trees))
            assertTrue("$id: the theme's own amount keeps a tree", SettingsUiModel.standing(layout(id), trees(id, default)).trees)
            // The palms stand in the trees' places: none either.
            assertFalse(SettingsUiModel.palms(c.copy(palmsInsteadOfTrees = true), false, false, standing.trees).shownOn)
        }
        // Where a tree does stand at the amount, the switch reads on.
        val kept = ThemeCatalog.ALL.map { it.id }.first { SettingsUiModel.standing(layout(it), trees(it, 0.5f)).trees }
        assertTrue(SettingsUiModel.amountSwitch(true, 0.5f, SettingsUiModel.standing(layout(kept), trees(kept, 0.5f)).trees).shownOn)
    }

    @Test
    fun `Tower opening hours reads off where the Towers amount keeps no tower, and the row says so`() {
        val bare = themesKeepingNone(setOf(SceneObjectType.SKYSCRAPER), { towers(it, 0.10f) }, { towers(it, 1f) })
        println("themes keeping no tower at 10 %: $bare")
        assertTrue("no built-in theme keeps no tower at 10 %: the case is not exercised", bare.isNotEmpty())
        for (id in bare) {
            val c = towers(id, 0.10f)
            val towersStand = SettingsUiModel.standing(layout(id), c).towers
            assertFalse(towersStand)
            val hours = SettingsUiModel.towerHours(true, buildingsVisible = true, towersAmount = 0.10f, towersStand = towersStand)
            assertFalse("$id: the hours read on over no tower", hours.shownOn)
            assertFalse(hours.interactive)
            assertEquals("10%, no tower at this density on this theme", SettingsUiModel.towersRowSummary(true, 0.10f, true, 9f, 20f, towersStand, towersOnTheme = true, placesOnTheme = true))
        }
        assertEquals("10%, open 09:00-20:00", SettingsUiModel.towersRowSummary(true, 0.10f, true, 9f, 20f, towersStand = true, towersOnTheme = true, placesOnTheme = true))
    }

    /**
     * Christmas with its trees thinned to an amount that keeps none: *Lights on the trees* and *Christmas firs* read off with
     * the reason, and -- reading off -- hold the Halloween decorations off neither (decision 40: *«grigi con la frase»*).
     */
    @Test
    fun `lights on trees that keep none read off, and do not hold the Halloween decorations off`() {
        val bare = themesKeepingNone(DecorationExclusion.TREE_TYPES, { trees(it, 0.04f) }, { trees(it, 1f) })
        assertTrue(bare.isNotEmpty())
        val id = bare.first()
        val street = layout(id)
        val c = trees(id, 0.04f).copy(
            christmasTreeLightsEnabled = true, christmasFirsEnabled = true, christmasBuildingLightsEnabled = false,
            halloweenDecorationsEnabled = false, houses = defaultCustomizationFor(id).houses.copy(visible = true, density = 1f),
        )
        val lights = SettingsUiModel.christmasTreeDecoration(c.christmasTreeLightsEnabled, c, street)
        assertFalse("$id: Lights on the trees reads on over no tree", lights.shownOn)
        assertFalse(lights.interactive)
        assertEquals(SettingsUiModel.NO_TREE_AT_THIS_DENSITY_LINE, lights.needsLine)
        // Christmas firs as the Seasons screen draws it, through its own rule: off, locked, the trees' line.
        val firs = SettingsUiModel.christmasFirs(c.christmasFirsEnabled, c, street)
        assertFalse("$id: Christmas firs reads on over no tree", firs.shownOn)
        assertFalse(firs.interactive)
        assertEquals(SettingsUiModel.NO_TREE_AT_THIS_DENSITY_LINE, firs.needsLine)
        val halloween = SettingsUiModel.halloweenDecorations(c, street)
        assertTrue("$id: the Halloween decorations held off by lights that read off", halloween.interactive)
        assertFalse(halloween.blockedByTheOther)
        assertTrue(DecorationExclusion.halloweenAllowed(c, street))
        // Judged by the amounts alone, as without the street, the trees above 0 % hold it off: what the street changes.
        assertFalse(DecorationExclusion.halloweenAllowed(c))
        // Both stored on while no tree stands: read as written, nothing to resolve.
        val both = c.copy(halloweenDecorationsEnabled = true)
        assertEquals(both, DecorationExclusion.resolved(both, defaultCustomizationFor(id), street))
        // The trees back: both hang, and the reading turns one side off, as for any data that holds both.
        val back = both.copy(trees = both.trees.copy(density = 1f))
        assertFalse(DecorationExclusion.resolved(back, defaultCustomizationFor(id), street).let { it.halloweenDecorationsEnabled && it.christmasTreeLightsEnabled })
    }

    @Test
    fun `the Halloween decorations with no house standing and the buildings off read off, and say so`() {
        val houses = { id: String, d: Float -> defaultCustomizationFor(id).let { it.copy(houses = it.houses.copy(visible = true, density = d)) } }
        val bare = themesKeepingNone(DecorationExclusion.HOUSE_TYPES, { houses(it, 0.03f) }, { houses(it, 1f) })
        println("themes keeping no house at 3 %: $bare")
        assertTrue(bare.isNotEmpty())
        val id = bare.first()
        val c = houses(id, 0.03f).let { it.copy(buildings = it.buildings.copy(visible = false), halloweenDecorationsEnabled = true) }
        val state = SettingsUiModel.halloweenDecorations(c, layout(id))
        assertFalse(state.shownOn)
        assertEquals(SettingsUiModel.NO_HOUSE_FOR_HALLOWEEN_LINE, state.needsLine)
        val lights = SettingsUiModel.christmasBuildingDecoration(true, c.copy(distantHouses = c.distantHouses.copy(visible = false)), layout(id))
        assertFalse(lights.shownOn)
        assertEquals(SettingsUiModel.NO_HOUSE_FOR_LIGHTS_LINE, lights.needsLine)
    }

    @Test
    fun `the decorations counted on the home screen are the ones standing on the theme's street`() {
        val none = SceneCustomization.DEFAULT.copy(
            christmasBuildingLightsEnabled = false, christmasTreeLightsEnabled = false, christmasFirsEnabled = false, santaEnabled = false,
            halloweenEnabled = false, horrorSkyEnabled = false, flowersEnabled = false,
        )
        val snowmen = { d: Float -> none.copy(snowmen = none.snowmen.copy(visible = true, density = d)) }
        val bare = ThemeCatalog.ALL.map { it.id }.filter {
            !keepsAny(layout(it), snowmen(0.02f), setOf(SceneObjectType.SNOWMAN)) && keepsAny(layout(it), snowmen(1f), setOf(SceneObjectType.SNOWMAN))
        }
        println("themes keeping no snowman at 2 %: $bare")
        assertTrue(bare.isNotEmpty())
        for (id in bare) {
            assertEquals("$id: snowmen at 2 % that keep none counted on", 0, SettingsUiModel.decorationsOn(snowmen(0.02f), layout(id)))
            assertEquals(1, SettingsUiModel.decorationsOn(snowmen(1f), layout(id)))
        }
    }

    @Test
    fun `Show people reads off with nobody at any hour, and a tap brings the day back`() {
        assertEquals(AmountSwitchUiState(shownOn = false, noneAtZero = true), SettingsUiModel.people(true, 0f, 0f))
        assertTrue("people by night", SettingsUiModel.people(true, 0f, 0.3f).shownOn)
        assertTrue("people by day", SettingsUiModel.people(true, 0.3f, 0f).shownOn)
        assertFalse(SettingsUiModel.people(false, 0.3f, 0.3f).shownOn)
        assertEquals(AmountTap.Restore(0.6f), SettingsUiModel.peopleTap(true, 0f, 0f, 0.6f))
        assertEquals(AmountTap.SetVisible(true), SettingsUiModel.peopleTap(true, 0f, 0.3f, 0.6f))
        assertEquals(AmountTap.SetVisible(false), SettingsUiModel.peopleTap(false, 0f, 0f, 0.6f))
        assertEquals("None at 0% by day and by night - tap to bring them back", SettingsUiModel.NO_PEOPLE_AT_ZERO_LINE)
    }

    /**
     * A tap on a switch that keeps none brings them back: the theme's own amount, and -- where that keeps none either, or
     * is no higher -- all of them at the next tap. Never a tap that changes nothing.
     */
    @Test
    fun `a tap on a switch that keeps none always brings them back within two taps`() {
        val categories = listOf<Pair<Set<SceneObjectType>, (SceneCustomization, Float) -> SceneCustomization>>(
            DecorationExclusion.TREE_TYPES to { c, d -> c.copy(trees = c.trees.copy(visible = true, density = d)) },
            DecorationExclusion.HOUSE_TYPES to { c, d -> c.copy(houses = c.houses.copy(visible = true, density = d)) },
            setOf(SceneObjectType.SNOWMAN) to { c, d -> c.copy(snowmen = c.snowmen.copy(visible = true, density = d)) },
        )
        for (theme in ThemeCatalog.ALL) for ((types, at) in categories) {
            val street = layout(theme.id)
            val base = defaultCustomizationFor(theme.id)
            val own = when (types) {
                DecorationExclusion.TREE_TYPES -> base.trees.density
                DecorationExclusion.HOUSE_TYPES -> base.houses.density
                else -> base.snowmen.density
            }
            var amount = 0.01f
            var taps = 0
            while (!keepsAny(street, at(base, amount), types)) {
                assertTrue("${theme.id} $types: still none after $taps taps", taps < 2)
                val tap = SettingsUiModel.amountTap(true, amount, own, stands = false)
                assertTrue("${theme.id} $types: a tap that changes no amount", tap is AmountTap.Restore)
                amount = (tap as AmountTap.Restore).amount
                taps++
            }
        }
    }
}
