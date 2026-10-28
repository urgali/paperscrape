package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.CustomThemeRegistry
import com.paperscrape.livewallpaper.engine.DecorationExclusion
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.SceneObjectCatalog
import com.paperscrape.livewallpaper.engine.SceneObjectLayout
import com.paperscrape.livewallpaper.engine.SceneObjectRenderer
import com.paperscrape.livewallpaper.engine.SceneObjectType
import com.paperscrape.livewallpaper.engine.StorefrontGroup
import com.paperscrape.livewallpaper.engine.StorefrontStreet
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import com.paperscrape.livewallpaper.engine.standsAny
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **A switch of the street on a theme whose street has none of its things reads off and locked, says so, and a tap
 * writes nothing** (`DESIGN_NOTES.md` §12, decision 40). A random theme rolls three to six
 * objects and a saved theme keeps only what
 * stood when it was saved, so its street can have no tree, house, tower or parasol at any amount. There *Show Trees*
 * (and Houses, Parasols) reads off whatever is stored, its line says "This theme has no trees.", and a tap writes neither
 * the flag nor the amount: the stored choice stays. The rows of World & scene say the same, *Tower opening hours* and the
 * *Towers & offices* row say there is no tower, and *Palms* that there is no tree to stand in. **The buildings too**: on a
 * street with no place among the houses the shops and the public buildings never stand, so their hours, rows and pages
 * say the theme has none; with no building at all *Show Buildings* is locked the same way; and the lines of the Towers page
 * name only what the street has.
 *
 * On the twelve built-in themes every street has all five and its places, so no row changes state or line there; and
 * every street has all seven seasonal decorations, so their switches need no such line.
 *
 * The streets are the catalogue's: `random:<n>` built on the JVM with no accent colour (a second car's colour is Android's
 * HSV, so the ones that need it are passed over), and a theme saved from Autumn by the gallery's own `snapshotEntry`.
 */
class SwitchesOverAThemeWithNoneTest {

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

    private fun SceneObjectLayout.hasAny(types: Set<SceneObjectType>, places: Boolean = false) =
        staticObjects.any { it.type in types && StorefrontStreet.isPlace(it) == places }

    private fun builtIn(id: String): SceneObjectLayout = SceneObjectCatalog.layoutFor(id, ThemeCatalog.byId(id).accentColor)

    /** Whether applying [tap] writes anything: the screens' own writer, with two counters for the store. */
    private fun writes(tap: AmountTap): Int {
        var writes = 0
        runBlocking { applyAmountTap(tap, setVisible = { writes++ }, setAmount = { writes++ }).join() }
        return writes
    }

    private val amounts = listOf(0f, 0.04f, 0.5f, 1f)

    @Test
    fun `on a random theme with no tree, Show Trees reads off and locked, says why, and a tap writes nothing`() {
        val (id, street) = firstRandom("without a tree") { !it.hasAny(DecorationExclusion.TREE_TYPES) }
        println("$id: no tree on its street")
        for (visible in listOf(true, false)) for (amount in amounts) {
            val c = SceneCustomization.DEFAULT.copy(trees = SceneCustomization.DEFAULT.trees.copy(visible = visible, density = amount))
            val standing = SettingsUiModel.standing(street, c)
            val what = "$id, Show Trees ${if (visible) "on" else "off"} at $amount"
            assertFalse("$what: the street has trees", standing.themeHasTrees)
            assertFalse(what, standing.trees)
            val shown = SettingsUiModel.amountSwitch(c.trees.visible, c.trees.density, standing.trees, standing.themeHasTrees)
            assertEquals(what, AmountSwitchUiState(shownOn = false, noneAtZero = false, noneAtThisAmount = false, noneOnThisTheme = true), shown)
            for (wanted in listOf(true, false)) {
                val tap = SettingsUiModel.amountTap(wanted, c.trees.density, 0.65f, standing.trees, standing.themeHasTrees)
                assertEquals("$what, a tap to ${if (wanted) "on" else "off"}: it promised", AmountTap.NoneOnThisTheme, tap)
                assertEquals("$what: the tap wrote the flag or the amount", 0, writes(tap))
            }
            // The palms take the trees' places: none either.
            val palms = SettingsUiModel.palms(c.copy(palmsInsteadOfTrees = true), layoutPlantsPalms = false, onlyFirsWouldStand = false, treesStand = standing.trees)
            assertFalse(what, palms.shownOn)
            assertFalse(what, palms.interactive)
        }
        assertEquals("This theme has no trees.", SettingsUiModel.noneOnThisThemeLine("trees"))
        assertEquals("This theme has no trees", SettingsUiModel.noneOnThisThemeSummary("trees"))
        assertEquals("This theme has no trees, and palms stand where the trees do.", SettingsUiModel.PALMS_NO_TREES_ON_THIS_THEME_LINE)
    }

    /** Autumn saved with its houses off keeps none (`snapshotEntry`): turned on again in the saved theme, none comes. */
    @Test
    fun `on a theme saved with no house, Show Houses reads off and locked, and the stored choice stays`() {
        val autumn = defaultCustomizationFor("autumn")
        val entry = snapshotEntry("custom:no-houses", "Autumn without houses", "autumn", autumn.copy(houses = autumn.houses.copy(visible = false)), "autumn")
        CustomThemeRegistry.update(CustomThemeData(customThemes = listOf(entry)))
        val street = SceneObjectCatalog.layoutFor(entry.id, 0)
        assertFalse("the saved street keeps no house", street.hasAny(DecorationExclusion.HOUSE_TYPES))
        for (visible in listOf(false, true)) for (amount in amounts) {
            val c = entry.customization.copy(houses = entry.customization.houses.copy(visible = visible, density = amount))
            val standing = SettingsUiModel.standing(street, c)
            val what = "Show Houses ${if (visible) "on" else "off"} at $amount"
            assertFalse(what, standing.themeHasHouses)
            assertTrue("$what: the trees are there", standing.themeHasTrees)
            val shown = SettingsUiModel.amountSwitch(c.houses.visible, c.houses.density, standing.houses, standing.themeHasHouses)
            assertFalse(what, shown.shownOn)
            assertTrue(what, shown.noneOnThisTheme)
            assertFalse("$what: a line naming an amount", shown.noneAtZero || shown.noneAtThisAmount)
            val tap = SettingsUiModel.amountTap(true, c.houses.density, autumn.houses.density, standing.houses, standing.themeHasHouses)
            assertEquals(what, AmountTap.NoneOnThisTheme, tap)
            assertEquals(what, 0, writes(tap))
        }
        assertFalse("the saved theme stores its houses off", entry.customization.houses.visible)
        assertEquals("This theme has no houses.", SettingsUiModel.noneOnThisThemeLine("houses"))
        assertEquals("This theme has no houses", SettingsUiModel.noneOnThisThemeSummary("houses"))
    }

    @Test
    fun `on a random theme with no tower, the Towers row and Tower opening hours say so`() {
        val (id, street) = firstRandom("without a tower") { !it.hasAny(setOf(SceneObjectType.SKYSCRAPER)) }
        for (amount in amounts) {
            val c = SceneCustomization.DEFAULT.copy(buildings = SceneCustomization.DEFAULT.buildings.copy(visible = true, density = amount))
            val standing = SettingsUiModel.standing(street, c)
            assertFalse(id, standing.themeHasTowers)
            assertEquals("$id at $amount", "This theme has no towers",
                SettingsUiModel.towersRowSummary(true, amount, true, 9f, 20f, standing.towers, standing.themeHasTowers, placesOnTheme = true))
            assertEquals("the switch is off: the row says so", "Off",
                SettingsUiModel.towersRowSummary(false, amount, true, 9f, 20f, standing.towers, standing.themeHasTowers, placesOnTheme = true))
            val hours = SettingsUiModel.towerHours(true, buildingsVisible = true, towersAmount = amount, towersStand = standing.towers)
            assertFalse("$id at $amount: the hours read on over no tower", hours.shownOn)
            assertFalse(hours.interactive)
        }
        assertEquals("This theme has no towers.", SettingsUiModel.noneOnThisThemeLine("towers"))
    }

    @Test
    fun `on a random theme with no parasol, Show Parasols reads off and locked`() {
        val (id, street) = firstRandom("without a parasol") { !it.hasAny(setOf(SceneObjectType.PARASOL)) }
        val c = SceneCustomization.DEFAULT.copy(parasols = SceneCustomization.DEFAULT.parasols.copy(visible = true, density = 1f))
        val standing = SettingsUiModel.standing(street, c)
        assertFalse(id, standing.themeHasParasols)
        val shown = SettingsUiModel.amountSwitch(true, 1f, standing.parasols, standing.themeHasParasols)
        assertFalse(id, shown.shownOn)
        assertTrue(id, shown.noneOnThisTheme)
        assertEquals(AmountTap.NoneOnThisTheme, SettingsUiModel.amountTap(true, 1f, 0.65f, standing.parasols, standing.themeHasParasols))
        assertEquals("This theme has no parasols.", SettingsUiModel.noneOnThisThemeLine("parasols"))
    }

    @Test
    fun `on a random street with no building, Show Buildings and the shops' and public buildings' rows say so`() {
        val (id, street) = firstRandom("without a building") { !it.hasAny(setOf(SceneObjectType.SKYSCRAPER)) && !it.hasAny(setOf(SceneObjectType.SKYSCRAPER), places = true) }
        for (visible in listOf(true, false)) {
            val c = SceneCustomization.DEFAULT.copy(buildings = SceneCustomization.DEFAULT.buildings.copy(visible = visible))
            val s = SettingsUiModel.standing(street, c)
            val what = "$id, Show Buildings ${if (visible) "on" else "off"}"
            assertFalse(what, s.themeHasPlaces)
            assertFalse(what, s.themeHasTowers)
            // Show Buildings: the section's switch, given the street has neither.
            val shown = SettingsUiModel.amountSwitch(c.buildings.visible, c.buildings.density, s.towers, s.themeHasTowers || s.themeHasPlaces)
            assertTrue(what, shown.noneOnThisTheme)
            assertFalse(what, shown.shownOn)
            assertEquals(what, "This theme has no buildings",
                SettingsUiModel.towersRowSummary(visible, 0.65f, true, 9f, 20f, s.towers, s.themeHasTowers, s.themeHasPlaces))
            for (stored in listOf(true, false)) {
                assertEquals(what, DependentSwitchUiState(false, false), SettingsUiModel.shopHours(stored, visible, s.themeHasPlaces))
                assertEquals(what, DependentSwitchUiState(false, false), SettingsUiModel.publicHours(stored, visible, s.themeHasPlaces))
            }
            assertEquals(what, "This theme has no shops",
                SettingsUiModel.storefrontRowSummary(StorefrontGroup.SHOPS, visible, true, 9f, 20f, s.themeHasPlaces))
            assertEquals(what, "This theme has no public buildings",
                SettingsUiModel.storefrontRowSummary(StorefrontGroup.PUBLIC, visible, true, 9f, 20f, s.themeHasPlaces))
            assertEquals(what, "This theme has no shops.", SettingsUiModel.storefrontGroupLine(StorefrontGroup.SHOPS, visible, s.themeHasPlaces))
            assertEquals(what, "This theme has no public buildings.", SettingsUiModel.storefrontGroupLine(StorefrontGroup.PUBLIC, visible, s.themeHasPlaces))
        }
        assertEquals("This theme has no buildings.", SettingsUiModel.noneOnThisThemeLine("buildings"))
        assertEquals("This theme has no towers.", buildingsDensityLine(placesOnTheme = false, towersOnTheme = false))
        // The hours' locked line on the group's page.
        assertEquals("This theme has no shops.", SettingsUiModel.noneOnThisThemeLine(SettingsUiModel.groupThings(StorefrontGroup.SHOPS)))
        assertEquals("This theme has no public buildings.", SettingsUiModel.noneOnThisThemeLine(SettingsUiModel.groupThings(StorefrontGroup.PUBLIC)))
    }

    @Test
    fun `on a random street with towers and no place, Show Buildings shows the towers and the shops' hours read off`() {
        val (id, street) = firstRandom("with a tower and no place") {
            it.hasAny(setOf(SceneObjectType.SKYSCRAPER)) && !it.hasAny(setOf(SceneObjectType.SKYSCRAPER), places = true)
        }
        val c = SceneCustomization.DEFAULT.copy(buildings = SceneCustomization.DEFAULT.buildings.copy(visible = true, density = 1f))
        val s = SettingsUiModel.standing(street, c)
        assertTrue(id, s.themeHasTowers)
        assertFalse(id, s.themeHasPlaces)
        val shown = SettingsUiModel.amountSwitch(true, 1f, s.towers, s.themeHasTowers || s.themeHasPlaces)
        assertFalse("$id: Show Buildings locked though towers stand", shown.noneOnThisTheme)
        assertEquals("The towers: this theme has no shops and no public buildings", buildingsSwitchLine(s.themeHasPlaces, s.themeHasTowers))
        assertEquals("Thins out the towers behind the houses.", buildingsDensityLine(s.themeHasPlaces, s.themeHasTowers))
        assertEquals("100%, always open", SettingsUiModel.towersRowSummary(true, 1f, false, 9f, 20f, s.towers, s.themeHasTowers, s.themeHasPlaces))
        assertFalse(SettingsUiModel.shopHours(true, buildingsVisible = true, placesOnTheme = s.themeHasPlaces).shownOn)
        assertFalse(SettingsUiModel.publicHours(true, buildingsVisible = true, placesOnTheme = s.themeHasPlaces).interactive)
    }

    @Test
    fun `on a random street with places and no tower, the Towers page says there is no tower`() {
        val (id, street) = firstRandom("with a place and no tower") {
            !it.hasAny(setOf(SceneObjectType.SKYSCRAPER)) && it.hasAny(setOf(SceneObjectType.SKYSCRAPER), places = true)
        }
        val s = SettingsUiModel.standing(street, SceneCustomization.DEFAULT)
        assertTrue(id, s.themeHasPlaces)
        assertFalse(id, s.themeHasTowers)
        assertEquals("The shops and the public buildings: this theme has no towers", buildingsSwitchLine(s.themeHasPlaces, s.themeHasTowers))
        assertEquals(
            "This theme has no towers. The shops and the public buildings among the houses always stay while Show Buildings is on.",
            buildingsDensityLine(s.themeHasPlaces, s.themeHasTowers),
        )
        assertEquals("This theme has no towers", SettingsUiModel.towersRowSummary(true, 0.65f, true, 9f, 20f, s.towers, s.themeHasTowers, s.themeHasPlaces))
        assertTrue("the shops' hours as stored", SettingsUiModel.shopHours(true, buildingsVisible = true, placesOnTheme = s.themeHasPlaces).shownOn)
    }

    /**
     * Beach and Desert as they ship, and a random street of palms alone: with the Palms switch on no fir grows, so
     * *Christmas firs* reads off and locked and says the Palms switch is the way; while no tree stands, the trees' own line
     * comes first (the Palms switch is locked then too); with Palms off the firs read as stored.
     */
    @Test
    fun `on a street of palms alone with Palms on, Christmas firs reads off and names the Palms switch`() {
        val streets = listOf("beach", "desert").map { it to builtIn(it) } +
            firstRandom("of palms alone") {
                it.hasAny(setOf(SceneObjectType.PALM_TREE)) && !it.hasAny(setOf(SceneObjectType.TREE))
            }
        for ((id, street) in streets) {
            val c = SceneCustomization.DEFAULT.copy(
                trees = SceneCustomization.DEFAULT.trees.copy(visible = true, density = 1f), palmsEnabled = true, christmasFirsEnabled = true,
            )
            val firs = SettingsUiModel.christmasFirs(true, c, street)
            assertFalse("$id: Christmas firs reads on among palms", firs.shownOn)
            assertFalse(id, firs.interactive)
            assertEquals(
                id,
                "The palms are on, and the firs grow only among the leafy trees. Turn Palms off in World & scene, Trees; your " +
                    "choice is kept until then.",
                firs.needsLine,
            )
            // The way it names is open: the Palms switch takes a tap there.
            assertTrue("$id: the Palms switch is locked", SettingsUiModel.palms(c, layoutPlantsPalms = true, onlyFirsWouldStand = false, treesStand = true).interactive)
            // Lights on the trees hang on palms too: not touched.
            assertTrue(id, SettingsUiModel.christmasTreeDecoration(true, c.copy(christmasTreeLightsEnabled = true), street).shownOn)
            assertTrue("$id: with Palms off the firs read as stored", SettingsUiModel.christmasFirs(true, c.copy(palmsEnabled = false), street).shownOn)
            val bare = c.copy(trees = c.trees.copy(visible = false))
            assertEquals("$id: no tree standing, the trees' line first", SettingsUiModel.NEEDS_TREES_LINE, SettingsUiModel.christmasFirs(true, bare, street).needsLine)
        }
    }

    /** A street whose every tree is a fir under *Christmas firs* even at 100 %: raising the density puts no palm anywhere. */
    @Test
    fun `where every tree is a Christmas fir even at 100 percent, the Palms line names the firs alone`() {
        val firs = SceneCustomization.DEFAULT.copy(christmasFirsEnabled = true, palmsInsteadOfTrees = true)
        val (id, street) = firstRandom("whose every tree is a fir") { s ->
            val trees = s.staticObjects.filter { it.type == SceneObjectType.TREE }
            trees.isNotEmpty() && s.staticObjects.none { it.type == SceneObjectType.PALM_TREE } &&
                trees.all { SceneObjectRenderer.standsAsFir(it, firs) }
        }
        for (amount in listOf(0.3f, 1f)) {
            val c = firs.copy(trees = firs.trees.copy(visible = true, density = amount))
            assertTrue("$id at $amount", SettingsUiModel.standing(street, c).onlyFirsEvenAtFull)
        }
        assertEquals(
            "Every tree on this theme is a Christmas fir, and the firs stay. Turn the Christmas firs off; your choice is kept " +
                "until then.",
            SettingsUiModel.PALMS_ONLY_FIRS_ON_THIS_THEME_LINE,
        )
        // Without the firs, it is not said.
        assertFalse(SettingsUiModel.standing(street, firs.copy(christmasFirsEnabled = false)).onlyFirsEvenAtFull)
    }

    /**
     * **On the twelve built-in themes no row changes**: each street has houses, towers, trees, parasols and places, so
     * every switch reads as it does with the theme's flags true (the amounts' own defaults, where they have one) and
     * the lines that say a theme has none never come. Swept over the amounts, each switch on and off, each decoration
     * stored on and off, Show Buildings on and off.
     */
    @Test
    fun `on the twelve built-in themes no switch changes state or line`() {
        val newLines = setOf(
            "This theme has no trees.", "This theme has no houses.", "This theme has no towers.", "This theme has no parasols.",
            "This theme has no houses and no shops.", "This theme has no trees, and palms stand where the trees do.",
        )
        assertEquals(12, ThemeCatalog.ALL.size)
        for (theme in ThemeCatalog.ALL) {
            val street = builtIn(theme.id)
            assertTrue("${theme.id} has places among its houses", street.staticObjects.any { StorefrontStreet.isPlace(it) })
            val base = defaultCustomizationFor(theme.id)
            for (visible in listOf(true, false)) for (amount in amounts) for (stored in listOf(true, false)) for (buildingsOn in listOf(true, false)) {
                val c = base.copy(
                    houses = base.houses.copy(visible = visible, density = amount),
                    buildings = base.buildings.copy(visible = buildingsOn, density = amount),
                    trees = base.trees.copy(visible = visible, density = amount),
                    parasols = base.parasols.copy(visible = visible, density = amount),
                    christmasTreeLightsEnabled = stored, christmasFirsEnabled = stored,
                    christmasBuildingLightsEnabled = stored && !buildingsOn, halloweenDecorationsEnabled = !stored,
                )
                val s = SettingsUiModel.standing(street, c)
                val what = "${theme.id}, ${if (visible) "on" else "off"} at $amount, buildings ${if (buildingsOn) "on" else "off"}"
                assertTrue("$what: a category the street lacks",
                    s.themeHasHouses && s.themeHasTowers && s.themeHasTrees && s.themeHasParasols && s.themeHasPlaces)
                assertFalse("$what: every tree a fir", s.onlyFirsEvenAtFull)
                // The buildings' rows and lines, as without the street.
                assertEquals(what, BUILDINGS_SWITCH_LINE, buildingsSwitchLine(s.themeHasPlaces, s.themeHasTowers))
                assertEquals(what, BUILDINGS_DENSITY_LINE, buildingsDensityLine(s.themeHasPlaces, s.themeHasTowers))
                assertFalse(what, SettingsUiModel.amountSwitch(c.buildings.visible, amount, s.towers, s.themeHasTowers || s.themeHasPlaces).noneOnThisTheme)
                for (group in StorefrontGroup.entries) {
                    assertEquals(what, SettingsUiModel.storefrontGroupLine(group, buildingsOn, true), SettingsUiModel.storefrontGroupLine(group, buildingsOn, s.themeHasPlaces))
                    assertEquals(what, SettingsUiModel.storefrontRowSummary(group, buildingsOn, stored, 9f, 20f, true),
                        SettingsUiModel.storefrontRowSummary(group, buildingsOn, stored, 9f, 20f, s.themeHasPlaces))
                }
                assertEquals(what, SettingsUiModel.shopHours(stored, buildingsOn, true), SettingsUiModel.shopHours(stored, buildingsOn, s.themeHasPlaces))
                assertEquals(what, SettingsUiModel.publicHours(stored, buildingsOn, true), SettingsUiModel.publicHours(stored, buildingsOn, s.themeHasPlaces))
                for ((cfg, pair) in listOf(c.houses to (s.houses to s.themeHasHouses), c.trees to (s.trees to s.themeHasTrees), c.parasols to (s.parasols to s.themeHasParasols))) {
                    val (stands, onTheme) = pair
                    assertEquals(what, SettingsUiModel.amountSwitch(cfg.visible, cfg.density, stands), SettingsUiModel.amountSwitch(cfg.visible, cfg.density, stands, onTheme))
                    for (wanted in listOf(true, false)) {
                        assertEquals(what, SettingsUiModel.amountTap(wanted, cfg.density, 0.65f, stands), SettingsUiModel.amountTap(wanted, cfg.density, 0.65f, stands, onTheme))
                    }
                }
                assertEquals(what,
                    SettingsUiModel.towersRowSummary(buildingsOn, amount, true, 9f, 20f, s.towers, true, true),
                    SettingsUiModel.towersRowSummary(buildingsOn, amount, true, 9f, 20f, s.towers, s.themeHasTowers, s.themeHasPlaces))
                for (row in listOf(
                    SettingsUiModel.christmasTreeDecoration(c.christmasTreeLightsEnabled, c, street),
                    SettingsUiModel.christmasTreeDecoration(c.christmasFirsEnabled, c, street),
                )) {
                    assertFalse("$what: ${row.needsLine}", newLines.any { it == row.needsLine })
                }
                // Christmas firs: as the trees' rule, but on Beach and Desert -- palms alone, Palms on as they ship -- where a
                // tree stands it reads off and names the Palms switch.
                val firs = SettingsUiModel.christmasFirs(c.christmasFirsEnabled, c, street)
                val treesRow = SettingsUiModel.christmasTreeDecoration(c.christmasFirsEnabled, c, street)
                if (theme.id in listOf("beach", "desert") && !treesRow.needsSomethingToHangOn) {
                    assertEquals(what, SettingsUiModel.PALMS_KEEP_NO_FIRS_LINE, firs.needsLine)
                    assertFalse(what, firs.shownOn)
                } else {
                    assertEquals(what, treesRow, firs)
                }
                // The two that hang on the buildings say what they said: one of their two lines, or none.
                val lights = SettingsUiModel.christmasBuildingDecoration(c.christmasBuildingLightsEnabled, c, street).needsLine
                assertTrue("$what: $lights", lights == null || lights == SettingsUiModel.NO_HOUSE_FOR_LIGHTS_LINE || lights == SettingsUiModel.NEEDS_BUILDINGS_LINE)
                val halloween = SettingsUiModel.halloweenDecorations(c, street).needsLine
                assertTrue("$what: $halloween", halloween == null || halloween == SettingsUiModel.NO_HOUSE_FOR_HALLOWEEN_LINE || halloween == SettingsUiModel.NEEDS_HOUSES_LINE)
            }
        }
    }

    /**
     * **Every street has all seven seasonal decorations**, so their switches never meet a theme with none: the catalogue
     * lays their candidates on every random street (`seasonalDecorationCandidates`) and on every saved one
     * (`withSeasonalDecorations`). If this stops being true, their switches need the line the street's categories have.
     */
    @Test
    fun `every random and saved street has every seasonal decoration`() {
        val decorations = listOf(
            SceneObjectType.SNOWMAN, SceneObjectType.GIFT, SceneObjectType.PENGUIN, SceneObjectType.BUNNY,
            SceneObjectType.EASTER_EGG, SceneObjectType.PUMPKIN, SceneObjectType.SCARECROW,
        )
        val d = SceneCustomization.DEFAULT
        val all = d.copy(
            snowmen = d.snowmen.copy(visible = true, density = 1f), gifts = d.gifts.copy(visible = true, density = 1f),
            penguins = d.penguins.copy(visible = true, density = 1f), bunnies = d.bunnies.copy(visible = true, density = 1f),
            easterEggs = d.easterEggs.copy(visible = true, density = 1f), pumpkins = d.pumpkins.copy(visible = true, density = 1f),
            scarecrows = d.scarecrows.copy(visible = true, density = 1f),
        )
        val streets = (1..60).mapNotNull { n -> randomStreet(n)?.let { "random:$n" to it } }
        assertTrue("too few random streets built on the JVM: ${streets.size}", streets.size >= 20)
        val autumn = defaultCustomizationFor("autumn")
        val saved = snapshotEntry("custom:bare", "Bare", "autumn", autumn.copy(
            houses = autumn.houses.copy(visible = false), buildings = autumn.buildings.copy(visible = false),
            trees = autumn.trees.copy(visible = false), parasols = autumn.parasols.copy(visible = false),
        ), "autumn")
        CustomThemeRegistry.update(CustomThemeData(customThemes = listOf(saved)))
        for ((id, street) in streets + (saved.id to SceneObjectCatalog.layoutFor(saved.id, 0))) {
            for (type in decorations) assertTrue("$id has no $type at 100 %", all.standsAny(street, setOf(type)))
        }
    }

    /**
     * **The screens draw the street's switches from the rule**: the theme's flag reaches the switch, its tap and its line,
     * the rows and the Trees and Towers pages. Whole lines, comments off, as the project reads Compose it cannot run.
     */
    @Test
    fun `the screens pass the theme's street to the switches`() {
        val section = lines("ui/SceneCategorySections.kt")
        for (line in listOf(
            "val shown = SettingsUiModel.amountSwitch(config.visible, config.density, stands, onTheme)",
            "val noneOnTheme = shown.noneOnThisTheme",
            "checked = if (noneOnTheme) false else if (noneAtZero) shown.shownOn else config.visible,",
            "} else if (!noneOnTheme) {",
            "SettingsUiModel.storefrontGroupLine(group, buildingsVisible, placesOnTheme),",
            "val leadsToBuildings = placesOnTheme && !buildingsVisible",
            "noneOnTheme -> SettingsUiModel.noneOnThisThemeLine(title.lowercase())",
            "enabled = !noneOnTheme,",
            "SettingsUiModel.amountTap(wanted, config.density, defaultDensityOf(category, forThemeId), stands, onTheme),",
            "AmountTap.NoneOnThisTheme -> Unit",
        )) assertTrue("SceneCategorySections.kt has no line: $line", line in section)
        val world = lines("ui/WorldSceneScreen.kt")
        for (line in listOf(
            "supporting = streetSummary(\"houses\", customization.houses.visible, customization.houses.density, standing.houses, standing.themeHasHouses),",
            "supporting = streetSummary(\"trees\", customization.trees.visible, customization.trees.density, standing.trees, standing.themeHasTrees),",
            "supporting = streetSummary(\"parasols\", customization.parasols.visible, customization.parasols.density, standing.parasols, standing.themeHasParasols),",
            "towersOnTheme = standing.themeHasTowers,",
            "placesOnTheme = standing.themeHasPlaces,",
            "\"houses\" -> HousesSubScreen(customization, standing.houses, standing.themeHasHouses, forThemeId, prefs, scope) { activeSection = null }",
            "\"towers\" -> TowersSubScreen(customization, standing.towers, standing.themeHasTowers, standing.themeHasPlaces, forThemeId, prefs, scope) { activeSection = null }",
            "\"shops\" -> StorefrontGroupSubScreen(StorefrontGroup.SHOPS, customization, standing.themeHasPlaces, forThemeId, prefs, scope,",
            "\"publics\" -> StorefrontGroupSubScreen(StorefrontGroup.PUBLIC, customization, standing.themeHasPlaces, forThemeId, prefs, scope,",
            "\"trees\" -> TreesSubScreen(customization, themeHasPalms, palmsOnlyFirs, standing.trees, standing.themeHasTrees, standing.onlyFirsEvenAtFull, forThemeId, prefs, scope) { activeSection = null }",
            "onTheme = towersOnTheme || placesOnTheme,",
            "switchSubtitle = buildingsSwitchLine(placesOnTheme, towersOnTheme),",
            "buildingsDensityLine(placesOnTheme, towersOnTheme),",
            "SettingsUiModel.shopHours(customization.shopHoursEnabled, customization.buildings.visible, placesOnTheme)",
            "SettingsUiModel.publicHours(customization.publicHoursEnabled, customization.buildings.visible, placesOnTheme)",
            "SettingsUiModel.noneOnThisThemeLine(SettingsUiModel.groupThings(group))",
            "} else if (!palms.interactive && palmsOnlyFirs && onlyFirsEvenAtFull) {",
            "SettingsUiModel.PALMS_ONLY_FIRS_ON_THIS_THEME_LINE",
            "\"parasols\" -> ParasolsSubScreen(customization, standing.parasols, standing.themeHasParasols, forThemeId, prefs, scope) { activeSection = null }",
            "lockedLine = if (!towersOnTheme) {",
            "SettingsUiModel.noneOnThisThemeLine(\"towers\")",
            "subtitle = if (!palms.interactive && !treesOnTheme) {",
            "SettingsUiModel.PALMS_NO_TREES_ON_THIS_THEME_LINE",
            // What stands, each to its own row and switch: the parameters have no default, and these say which value.
            "towersStand = standing.towers,",
            "towersStand = towersStand,",
            "forThemeId = forThemeId, prefs = prefs, scope = scope, noneAtZero = true, stands = housesStand, onTheme = housesOnTheme, showTitle = false,",
            "forThemeId = forThemeId, prefs = prefs, scope = scope, showTitle = false, stands = towersStand,",
            "forThemeId = forThemeId, prefs = prefs, scope = scope, showTitle = false, noneAtZero = true, stands = treesStand, onTheme = treesOnTheme,",
            "forThemeId = forThemeId, prefs = prefs, scope = scope, showTitle = false, noneAtZero = true, stands = parasolsStand, onTheme = parasolsOnTheme,",
            "placesOnTheme = placesOnTheme,",
        )) assertTrue("WorldSceneScreen.kt has no line: $line", line in world)
        for (onTheme in listOf("onTheme = housesOnTheme,", "onTheme = treesOnTheme,", "onTheme = parasolsOnTheme,")) {
            assertEquals("one section passes $onTheme", 1, world.count { it.contains(onTheme) })
        }
        assertEquals("the Towers & offices, Shops and Public buildings rows pass the places", 3, world.count { it == "placesOnTheme = standing.themeHasPlaces," })
        assertTrue(lines("ui/SettingsScreen.kt").contains("val standing = SettingsUiModel.standing(themeLayout, customization)"))
        val seasons = lines("ui/SeasonsScreen.kt")
        assertTrue(seasons.contains("\"Christmas firs\" to SettingsUiModel.christmasFirs(customization.christmasFirsEnabled, customization, themeLayout).shownOn,"))
        assertTrue(seasons.contains("state = SettingsUiModel.christmasFirs(customization.christmasFirsEnabled, customization, themeLayout),"))
    }

    /** [path]'s lines, trimmed, with `//` comments and blank lines left out. */
    private fun lines(path: String): List<String> {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/$path"
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = java.io.File(dir, "$prefix$suffix")
                if (f.isFile) return f.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("//") }
            }
            dir = dir.parentFile
        }
        error("source not found: $suffix")
    }
}
