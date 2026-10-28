package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.CustomThemeRegistry
import com.paperscrape.livewallpaper.engine.DecorationExclusion
import com.paperscrape.livewallpaper.engine.DistantHousesConfig
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.SceneObjectCatalog
import com.paperscrape.livewallpaper.engine.SceneObjectLayout
import com.paperscrape.livewallpaper.engine.SceneObjectType
import com.paperscrape.livewallpaper.engine.StorefrontStreet
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **A decoration that hangs on something the theme's street has none of says so, reads off, and its line names only the
 * ways that would help there** (`DESIGN_NOTES.md` §12, decision 40). A random theme rolls three
 * to six objects and a saved theme keeps only what stood when it was saved, so a street can have no tree, no house, no
 * place among the houses (where the shops stand) or no building at all, at any amount. Then *Lights on the trees* and
 * *Christmas firs* -- on the trees alone -- say the theme has none; *Halloween decorations* -- on the houses or the shops --
 * and *Lights on the buildings* -- on the houses, the buildings or the houses on the mountains -- read off whatever *Show
 * Buildings* says where nothing of theirs stands, and their line names the ways the street has and what it lacks, never a
 * switch or an amount that would not help. The home screen's count follows the switches.
 *
 * Only what the settings already offer is called, the lines compared as written, so the class reads the same code
 * the rows draw from. The streets are the catalogue's: `random:<n>` built on the JVM with no accent colour (a second car's
 * colour is Android's HSV, so the ones that need it are passed over), and a theme saved from Autumn by the gallery's own
 * `snapshotEntry`.
 */
class DecorationsOverAThemeWithNoneTest {

    @Before
    fun clearRegistry() = CustomThemeRegistry.update(CustomThemeData.EMPTY)

    @After
    fun clearRegistryAfter() = CustomThemeRegistry.update(CustomThemeData.EMPTY)

    /** The street of `random:<n>` as the catalogue lays it, or null where the JVM cannot build it. */
    private fun randomStreet(n: Int): SceneObjectLayout? = runCatching { SceneObjectCatalog.layoutFor("random:$n", 0) }.getOrNull()

    /** The first random theme whose street is [wanted], with its street. */
    private fun firstRandom(what: String, wanted: (SceneObjectLayout) -> Boolean): Pair<String, SceneObjectLayout> {
        for (n in 1..2000) {
            val street = randomStreet(n) ?: continue
            if (wanted(street)) return "random:$n" to street
        }
        throw AssertionError("no random theme up to random:2000 $what")
    }

    private fun SceneObjectLayout.hasAny(types: Set<SceneObjectType>) = staticObjects.any { it.type in types }
    private fun SceneObjectLayout.hasPlace() = staticObjects.any { StorefrontStreet.isPlace(it) }
    private fun SceneObjectLayout.hasTower() = staticObjects.any { it.type == SceneObjectType.SKYSCRAPER && !StorefrontStreet.isPlace(it) }

    /** Nothing on but what each test turns on: no decoration, no house on the mountains. */
    private val none = SceneCustomization.DEFAULT

    private fun houses(visible: Boolean, density: Float) = none.houses.copy(visible = visible, density = density)
    private fun buildings(visible: Boolean, density: Float = 0.65f) = none.buildings.copy(visible = visible, density = density)

    /** *Lights on the buildings* stored on in [c]: off and locked, with [line]. */
    private fun assertLights(what: String, line: String, c: SceneCustomization, street: SceneObjectLayout) {
        val row = SettingsUiModel.christmasBuildingDecoration(true, c.copy(christmasBuildingLightsEnabled = true), street)
        assertFalse("$what: Lights on the buildings reads on over nothing", row.shownOn)
        assertFalse("$what: takes a tap", row.interactive)
        assertEquals(what, line, row.needsLine)
    }

    @Test
    fun `on a random theme with no tree, Lights on the trees and Christmas firs read off and say the theme has none`() {
        val (id, street) = firstRandom("without a tree") { !it.hasAny(DecorationExclusion.TREE_TYPES) }
        println("$id: no tree on its street")
        val trees = listOf(
            none.trees.copy(visible = true, density = 0.5f),
            none.trees.copy(visible = true, density = 1f),
            none.trees.copy(visible = true, density = 0f),
            none.trees.copy(visible = false),
        )
        for (t in trees) for (stored in listOf(true, false)) {
            val c = none.copy(trees = t, christmasTreeLightsEnabled = stored, christmasFirsEnabled = stored)
            // Each row as the Seasons screen draws it: the firs' through their own rule.
            for (row in listOf(
                SettingsUiModel.christmasTreeDecoration(c.christmasTreeLightsEnabled, c, street),
                SettingsUiModel.christmasFirs(c.christmasFirsEnabled, c, street),
            )) {
                assertFalse("$id, $t: reads on over no tree", row.shownOn)
                assertFalse("$id, $t: takes a tap", row.interactive)
                assertEquals("$id, $t", "This theme has no trees.", row.needsLine)
            }
        }
        // Not counted on the home screen either.
        val lit = none.copy(trees = trees[1], christmasTreeLightsEnabled = true, christmasFirsEnabled = true)
        assertEquals(0, SettingsUiModel.decorationsOn(lit, street))
    }

    /** A street that has trees but keeps none at 1 %: the amount is why, and the line still says to raise it. */
    @Test
    fun `a random theme with trees that keeps none at a low amount keeps the line that says to raise it`() {
        val c = none.copy(trees = none.trees.copy(visible = true, density = 0.01f), christmasTreeLightsEnabled = true)
        val (id, street) = firstRandom("with a tree that keeps none at 1 %") {
            it.hasAny(DecorationExclusion.TREE_TYPES) && !SettingsUiModel.standing(it, c).trees
        }
        val row = SettingsUiModel.christmasTreeDecoration(true, c, street)
        assertFalse(id, row.shownOn)
        assertEquals(
            id,
            "No tree stands at this density on this theme. Raise Trees in World & scene; your choice is kept until then.",
            row.needsLine,
        )
        assertEquals(id, "Needs Show Trees on in World & scene. Your choice is kept until then.",
            SettingsUiModel.christmasTreeDecoration(true, c.copy(trees = c.trees.copy(visible = false)), street).needsLine)
    }

    @Test
    fun `on a random theme with no house and no place, the Halloween decorations read off whatever Show Buildings says`() {
        val (id, street) = firstRandom("without a house or a place") { !it.hasAny(DecorationExclusion.HOUSE_TYPES) && !it.hasPlace() }
        println("$id: no house and no place on its street")
        for (buildingsOn in listOf(true, false)) for (housesOn in listOf(true, false)) {
            val c = none.copy(houses = houses(housesOn, 0.65f), buildings = buildings(buildingsOn), halloweenDecorationsEnabled = true)
            val row = SettingsUiModel.halloweenDecorations(c, street)
            val what = "$id, houses ${if (housesOn) "on" else "off"}, buildings ${if (buildingsOn) "on" else "off"}"
            assertFalse("$what: reads on over nothing to hang them on", row.shownOn)
            assertFalse("$what: takes a tap", row.interactive)
            assertEquals(what, "This theme has no houses and no shops.", row.needsLine)
            assertEquals("$what: counted on the home screen", 0, SettingsUiModel.decorationsOn(c, street))
        }
    }

    /** Houses and no place: the shops cannot carry them, so the line names the houses alone, and says why. */
    @Test
    fun `on a random theme with houses and no place, the Halloween decorations name the houses alone`() {
        val low = none.copy(houses = houses(true, 0.01f))
        val (id, street) = firstRandom("with a house, no place, and no house standing at 1 %") {
            it.hasAny(DecorationExclusion.HOUSE_TYPES) && !it.hasPlace() && !SettingsUiModel.standing(it, low).houses
        }
        for (buildingsOn in listOf(true, false)) {
            val what = "$id, buildings ${if (buildingsOn) "on" else "off"}"
            val raise = low.copy(buildings = buildings(buildingsOn), halloweenDecorationsEnabled = true)
            val row = SettingsUiModel.halloweenDecorations(raise, street)
            assertFalse("$what: reads on over nothing to hang them on", row.shownOn)
            assertEquals(
                what,
                "No house stands at this density on this theme, and it has no shops. Raise Houses in World & scene; your " +
                    "choice is kept until then.",
                row.needsLine,
            )
            val off = raise.copy(houses = houses(false, 0.65f))
            assertEquals(what, "Needs the houses on in World & scene: this theme has no shops. Your choice is kept until then.",
                SettingsUiModel.halloweenDecorations(off, street).needsLine)
            assertEquals("$what: counted on the home screen", 0, SettingsUiModel.decorationsOn(raise, street))
        }
    }

    /** Autumn saved with its houses and its buildings off: the saved street keeps neither (`snapshotEntry`). */
    @Test
    fun `on a theme saved with no house and no building, the Halloween decorations say the theme has neither`() {
        val autumn = defaultCustomizationFor("autumn")
        val bare = autumn.copy(houses = autumn.houses.copy(visible = false), buildings = autumn.buildings.copy(visible = false))
        val entry = snapshotEntry("custom:no-houses-no-buildings", "Bare Autumn", "autumn", bare, "autumn")
        CustomThemeRegistry.update(CustomThemeData(customThemes = listOf(entry)))
        val street = SceneObjectCatalog.layoutFor(entry.id, 0)
        assertFalse("the saved street keeps no house", street.hasAny(DecorationExclusion.HOUSE_TYPES))
        assertFalse("nor a building", street.hasAny(setOf(SceneObjectType.SKYSCRAPER)))
        assertTrue("its trees are there", street.hasAny(DecorationExclusion.TREE_TYPES))
        // The user turns the houses and the buildings back on in the saved theme, and the decorations.
        val c = entry.customization.copy(
            houses = entry.customization.houses.copy(visible = true, density = 1f),
            buildings = entry.customization.buildings.copy(visible = true),
            distantHouses = entry.customization.distantHouses.copy(visible = false),
            halloweenDecorationsEnabled = true,
            christmasBuildingLightsEnabled = false, christmasTreeLightsEnabled = false, christmasFirsEnabled = false,
        )
        val row = SettingsUiModel.halloweenDecorations(c, street)
        assertFalse(row.shownOn)
        assertFalse(row.interactive)
        assertEquals("This theme has no houses and no shops.", row.needsLine)
        val counted = c.copy(halloweenDecorationsEnabled = false)
        assertEquals("the decorations not counted on", SettingsUiModel.decorationsOn(counted, street), SettingsUiModel.decorationsOn(c, street))
        // The lights on the buildings: only the houses on the mountains are left to carry them.
        assertLights("lights", "Needs the distant houses on in World & scene: this theme has no houses and no buildings. Your " +
            "choice is kept until then.", c, street)
    }

    /**
     * Autumn saved with its houses off and its buildings on keeps its places: the shops can carry the decorations, so with
     * the buildings off the line says to turn them on, and that the theme has no houses; with them on the switch reads as
     * stored.
     */
    @Test
    fun `on a theme saved with no house but with its places, the line names the buildings alone`() {
        val autumn = defaultCustomizationFor("autumn")
        val noHouses = autumn.copy(houses = autumn.houses.copy(visible = false))
        val entry = snapshotEntry("custom:no-houses", "Autumn without houses", "autumn", noHouses, "autumn")
        CustomThemeRegistry.update(CustomThemeData(customThemes = listOf(entry)))
        val street = SceneObjectCatalog.layoutFor(entry.id, 0)
        assertFalse(street.hasAny(DecorationExclusion.HOUSE_TYPES))
        assertTrue("the places are saved", street.hasPlace())
        for (housesOn in listOf(true, false)) {
            val c = entry.customization.copy(
                houses = entry.customization.houses.copy(visible = housesOn, density = 0.5f),
                buildings = entry.customization.buildings.copy(visible = false),
                distantHouses = entry.customization.distantHouses.copy(visible = false),
                halloweenDecorationsEnabled = true,
                christmasBuildingLightsEnabled = false, christmasTreeLightsEnabled = false, christmasFirsEnabled = false,
            )
            val off = SettingsUiModel.halloweenDecorations(c, street)
            assertFalse(off.shownOn)
            assertEquals(
                "This theme has no houses, and the buildings are off. Turn the buildings on in World & scene; your choice is " +
                    "kept until then.",
                off.needsLine,
            )
            assertLights("lights, houses ${if (housesOn) "on" else "off"}", "Needs the buildings or the distant houses on in " +
                "World & scene: this theme has no houses. Your choice is kept until then.", c, street)
            val shops = SettingsUiModel.halloweenDecorations(c.copy(buildings = c.buildings.copy(visible = true)), street)
            assertTrue("the shops carry them", shops.shownOn)
            assertNull(shops.needsLine)
        }
    }

    /** Houses and no building at all: the houses and the houses on the mountains, never the buildings. */
    @Test
    fun `on a random theme with houses and no building, Lights on the buildings names the houses and the distant houses`() {
        val low = none.copy(houses = houses(true, 0.01f))
        val (id, street) = firstRandom("with a house, no building, and no house standing at 1 %") {
            it.hasAny(DecorationExclusion.HOUSE_TYPES) && !it.hasAny(setOf(SceneObjectType.SKYSCRAPER)) &&
                !SettingsUiModel.standing(it, low).houses
        }
        for (buildingsOn in listOf(true, false)) {
            val what = "$id, buildings ${if (buildingsOn) "on" else "off"}"
            assertLights(what, "No house stands at this density on this theme, it has no buildings, and the distant houses do " +
                "not carry them. Raise Houses or turn the distant houses on in World & scene; your choice is kept until then.",
                low.copy(buildings = buildings(buildingsOn)), street)
            assertLights(what, "Needs the houses or the distant houses on in World & scene: this theme has no buildings. Your " +
                "choice is kept until then.", none.copy(houses = houses(false, 0.65f), buildings = buildings(buildingsOn)), street)
        }
    }

    /** Towers and no place, Show Buildings on and every tower thinned away: raising the Towers is the way. */
    @Test
    fun `on a random theme with towers and no place, Lights on the buildings says to raise the towers`() {
        val low = none.copy(houses = houses(true, 0.01f), buildings = buildings(true, 0f))
        val (withHouses, street) = firstRandom("with a tower and a house, no place, and no house standing at 1 %") {
            it.hasTower() && !it.hasPlace() && it.hasAny(DecorationExclusion.HOUSE_TYPES) && !SettingsUiModel.standing(it, low).houses
        }
        assertLights("$withHouses, houses raised", "No house or tower stands at these densities on this theme, and the distant " +
            "houses do not carry them. Raise Houses or Towers, or turn the distant houses on in World & scene; your choice is " +
            "kept until then.", low, street)
        assertLights("$withHouses, houses off", "No tower stands at this density on this theme, and neither the houses nor the " +
            "distant houses carry them. Raise Towers or turn the houses or the distant houses on in World & scene; your choice " +
            "is kept until then.", low.copy(houses = houses(false, 0.65f)), street)
        val (noHouses, bare) = firstRandom("with a tower, no place and no house") {
            it.hasTower() && !it.hasPlace() && !it.hasAny(DecorationExclusion.HOUSE_TYPES)
        }
        assertLights(noHouses, "No tower stands at this density on this theme, it has no houses, and the distant houses do not " +
            "carry them. Raise Towers or turn the distant houses on in World & scene; your choice is kept until then.", low, bare)
    }

    /**
     * Beach and Desert as they ship, and a random street of palms alone: with the Palms switch on every tree slot stays a
     * palm, and no fir grows among palms, so *Christmas firs* has nothing to hang on -- not counted on the home screen, and
     * holding the Halloween decorations off neither. With Palms off the palm slots are leafy trees and the firs count.
     */
    @Test
    fun `on a street of palms alone with Palms on, the Christmas firs hang on nothing`() {
        val streets = listOf("beach", "desert").map { it to SceneObjectCatalog.layoutFor(it, ThemeCatalog.byId(it).accentColor) } +
            firstRandom("of palms alone") {
                it.hasAny(setOf(SceneObjectType.PALM_TREE)) && !it.hasAny(setOf(SceneObjectType.TREE))
            }
        for ((id, street) in streets) {
            val base = if (id.startsWith("random:")) none else defaultCustomizationFor(id)
            val c = base.copy(
                trees = base.trees.copy(visible = true, density = 1f), palmsEnabled = true,
                christmasFirsEnabled = true, christmasTreeLightsEnabled = false, christmasBuildingLightsEnabled = false,
                halloweenDecorationsEnabled = false,
            )
            fun firsCounted(x: SceneCustomization) =
                SettingsUiModel.decorationsOn(x, street) - SettingsUiModel.decorationsOn(x.copy(christmasFirsEnabled = false), street)
            assertEquals("$id: the firs counted on among palms", 0, firsCounted(c))
            assertTrue("$id: firs that cannot grow hold the Halloween decorations off", DecorationExclusion.halloweenAllowed(c, street))
            val leafy = c.copy(palmsEnabled = false)
            assertEquals("$id: with Palms off the firs count", 1, firsCounted(leafy))
            assertFalse("$id: and hold the Halloween decorations off", DecorationExclusion.halloweenAllowed(leafy, street))
            // Read back with both stored on, the Halloween decorations stay where no fir can grow, and go where one can.
            val both = c.copy(halloweenDecorationsEnabled = true)
            assertTrue(id, DecorationExclusion.resolved(both, base, street).halloweenDecorationsEnabled)
            assertFalse(id, DecorationExclusion.resolved(both.copy(palmsEnabled = false), base, street).halloweenDecorationsEnabled)
        }
    }

    /**
     * The houses on the mountains on above 0 %, and no mountain shown for them to stand on: both layers off, or both at
     * 0 % ([atZero]).
     */
    private fun noMountain(c: SceneCustomization, atZero: Boolean) = c.copy(
        distantHouses = DistantHousesConfig(visible = true, density = 0.5f),
        mountainsFront = c.mountainsFront.copy(visible = atZero, density = if (atZero) 0f else c.mountainsFront.density),
        mountainsBack = c.mountainsBack.copy(visible = atZero, density = if (atZero) 0f else c.mountainsBack.density),
    )

    /**
     * **The houses on the mountains already on, and no mountain to stand on: the line names the mountains, not them.**
     * Autumn as it ships, the houses and *Show Buildings* off, the distant houses on at 50 % and both mountain layers off
     * or at 0 %: turning the distant houses on would change nothing, turning a mountain on would. With the distant houses
     * off, mountains or not, the way is still their switch, which leads to the mountains where there are none.
     */
    @Test
    fun `with the distant houses on and no mountain shown, Lights on the buildings names the mountains`() {
        val autumn = defaultCustomizationFor("autumn")
        val street = SceneObjectCatalog.layoutFor("autumn", ThemeCatalog.byId("autumn").accentColor)
        val off = autumn.copy(houses = autumn.houses.copy(visible = false), buildings = autumn.buildings.copy(visible = false))
        for (atZero in listOf(false, true)) {
            val what = "autumn, mountains ${if (atZero) "at 0 %" else "off"}"
            assertLights(what, "Needs the houses, the buildings or the mountains on in World & scene. Your choice is kept until " +
                "then.", noMountain(off, atZero), street)
            val distantOff = noMountain(off, atZero).copy(distantHouses = DistantHousesConfig(visible = false, density = 0.5f))
            assertLights("$what, distant houses off", "Needs the houses, the buildings or the distant houses on in World & scene. " +
                "Your choice is kept until then.", distantOff, street)
        }
        assertLights("autumn, distant houses off, mountains on", "Needs the houses, the buildings or the distant houses on in " +
            "World & scene. Your choice is kept until then.", off.copy(distantHouses = DistantHousesConfig(visible = false, density = 0.5f)), street)
        // The houses on at an amount that keeps none, on a street with houses and places.
        val low = autumn.copy(houses = autumn.houses.copy(visible = true, density = 0.01f), buildings = autumn.buildings.copy(visible = false))
        val (id, keepsNone) = firstRandom("with a house and a place, and no house standing at 1 %") {
            it.hasAny(DecorationExclusion.HOUSE_TYPES) && it.hasPlace() && !SettingsUiModel.standing(it, low).houses
        }
        assertLights(id, "No house stands at this density on this theme, and neither the buildings nor the distant houses carry " +
            "them. Raise Houses or turn the buildings or the mountains on in World & scene; your choice is kept until then.",
            noMountain(low, atZero = false), keepsNone)
    }

    /**
     * The same on the streets that lack something: wherever the houses on the mountains are on and no mountain is shown,
     * every line of *Lights on the buildings* names the mountains where it named them, and keeps the rest.
     */
    @Test
    fun `on a street that lacks houses or buildings, the line names the mountains where the distant houses are on`() {
        val low = none.copy(houses = houses(true, 0.01f))
        // Houses and no building at all.
        val (houseOnly, street) = firstRandom("with a house, no building, and no house standing at 1 %") {
            it.hasAny(DecorationExclusion.HOUSE_TYPES) && !it.hasAny(setOf(SceneObjectType.SKYSCRAPER)) &&
                !SettingsUiModel.standing(it, low).houses
        }
        for (buildingsOn in listOf(true, false)) {
            val what = "$houseOnly, buildings ${if (buildingsOn) "on" else "off"}"
            assertLights(what, "No house stands at this density on this theme, it has no buildings, and the distant houses do " +
                "not carry them. Raise Houses or turn the mountains on in World & scene; your choice is kept until then.",
                noMountain(low.copy(buildings = buildings(buildingsOn)), atZero = false), street)
            assertLights(what, "Needs the houses or the mountains on in World & scene: this theme has no buildings. Your choice is " +
                "kept until then.", noMountain(none.copy(houses = houses(false, 0.65f), buildings = buildings(buildingsOn)), atZero = true), street)
        }
        // Autumn saved without its houses (its places kept), and without its houses and its buildings.
        val autumn = defaultCustomizationFor("autumn")
        val noHouses = snapshotEntry("custom:no-houses", "Autumn without houses", "autumn", autumn.copy(houses = autumn.houses.copy(visible = false)), "autumn")
        val bare = snapshotEntry("custom:no-houses-no-buildings", "Bare Autumn", "autumn",
            autumn.copy(houses = autumn.houses.copy(visible = false), buildings = autumn.buildings.copy(visible = false)), "autumn")
        CustomThemeRegistry.update(CustomThemeData(customThemes = listOf(noHouses, bare)))
        for (housesOn in listOf(true, false)) {
            val c = noHouses.customization.copy(
                houses = noHouses.customization.houses.copy(visible = housesOn, density = 0.5f),
                buildings = noHouses.customization.buildings.copy(visible = false),
            )
            assertLights("no houses, houses ${if (housesOn) "on" else "off"}", "Needs the buildings or the mountains on in World & " +
                "scene: this theme has no houses. Your choice is kept until then.", noMountain(c, atZero = false),
                SceneObjectCatalog.layoutFor(noHouses.id, 0))
        }
        val bareOn = bare.customization.copy(
            houses = bare.customization.houses.copy(visible = true, density = 1f),
            buildings = bare.customization.buildings.copy(visible = true),
        )
        assertLights("no houses, no buildings", "Needs the mountains on in World & scene: this theme has no houses and no " +
            "buildings. Your choice is kept until then.", noMountain(bareOn, atZero = false), SceneObjectCatalog.layoutFor(bare.id, 0))
        // Towers and no place, every tower thinned away.
        val towersLow = none.copy(houses = houses(true, 0.01f), buildings = buildings(true, 0f))
        val (withHouses, towers) = firstRandom("with a tower and a house, no place, and no house standing at 1 %") {
            it.hasTower() && !it.hasPlace() && it.hasAny(DecorationExclusion.HOUSE_TYPES) && !SettingsUiModel.standing(it, towersLow).houses
        }
        assertLights("$withHouses, houses raised", "No house or tower stands at these densities on this theme, and the distant " +
            "houses do not carry them. Raise Houses or Towers, or turn the mountains on in World & scene; your choice is kept " +
            "until then.", noMountain(towersLow, atZero = false), towers)
        assertLights("$withHouses, houses off", "No tower stands at this density on this theme, and neither the houses nor the " +
            "distant houses carry them. Raise Towers or turn the houses or the mountains on in World & scene; your choice is " +
            "kept until then.", noMountain(towersLow.copy(houses = houses(false, 0.65f)), atZero = true), towers)
        val (noHouseStreet, towersAlone) = firstRandom("with a tower, no place and no house") {
            it.hasTower() && !it.hasPlace() && !it.hasAny(DecorationExclusion.HOUSE_TYPES)
        }
        assertLights(noHouseStreet, "No tower stands at this density on this theme, it has no houses, and the distant houses do " +
            "not carry them. Raise Towers or turn the mountains on in World & scene; your choice is kept until then.",
            noMountain(towersLow, atZero = false), towersAlone)
    }
}
