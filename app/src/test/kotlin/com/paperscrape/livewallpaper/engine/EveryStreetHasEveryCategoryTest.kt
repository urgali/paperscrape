package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.ui.SettingsUiModel
import com.paperscrape.livewallpaper.ui.snapshotEntry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **No switch is locked because of the theme** (`DESIGN_NOTES.md` §12, decision 102): the maintainer's *«non devono esserci
 * limitazioni legate ai temi default»* (2026-11-06) and *«Resta spento (consigliata)»* (2026-11-07). Every street the app
 * lays has houses, trees, parasols, towers and places among the houses ([SceneObjectCatalog.withEveryCategory]); on a random
 * theme what its street was born without starts off ([offWhereBornWithout]), so the theme looks as it was born; a saved
 * theme keeps every candidate and its switches.
 *
 * So the lines of a theme that lacks a category ("This theme has no ...", decision 40's table) are reached by no street the
 * app lays: they stay in the code only as the reading of such a street, a guard their own tests still hold on streets
 * made by hand.
 */
class EveryStreetHasEveryCategoryTest {

    @Before
    fun clear() = CustomThemeRegistry.update(CustomThemeData.EMPTY)

    @After
    fun clearAfter() = CustomThemeRegistry.update(CustomThemeData.EMPTY)

    /** A random theme's starting settings, a built-in's colours standing in for its own (they need Android's HSV). */
    private fun randomDefaults(id: String) = defaultCustomizationFor(id, ThemeCatalog.byId("autumn").copy(id = id))

    private fun randomStreets(): List<Pair<String, SceneObjectLayout>> =
        (1..2000).mapNotNull { n -> runCatching { "random:$n" to SceneObjectCatalog.layoutFor("random:$n", 0) }.getOrNull() }

    private fun assertEvery(label: String, street: SceneObjectLayout) {
        val s = SettingsUiModel.standing(street, SceneCustomization.DEFAULT)
        assertTrue("$label: houses", s.themeHasHouses)
        assertTrue("$label: towers", s.themeHasTowers)
        assertTrue("$label: trees", s.themeHasTrees)
        assertTrue("$label: parasols", s.themeHasParasols)
        assertTrue("$label: places", s.themeHasPlaces)
        assertTrue("$label: cars", street.cars.isNotEmpty())
    }

    @Test
    fun `every street the app lays has every category, so no line says the theme has none`() {
        for (theme in ThemeCatalog.ALL) assertEvery(theme.id, SceneObjectCatalog.layoutFor(theme.id, theme.accentColor))
        val random = randomStreets()
        assertTrue("random streets read: ${random.size} of 2000", random.size >= 1000)
        for ((id, street) in random) assertEvery(id, street)
        // Saved: from a built-in with everything off, from a random theme, and stored with only what stood when saved.
        val autumn = defaultCustomizationFor("autumn")
        val bare = autumn.copy(
            houses = autumn.houses.copy(visible = false), trees = autumn.trees.copy(visible = false),
            parasols = autumn.parasols.copy(visible = false), buildings = autumn.buildings.copy(visible = false),
            cars = autumn.cars.copy(visible = false),
        )
        val fromBuiltIn = snapshotEntry("custom:bare", "Bare", "autumn", bare, "autumn")
        val randomId = random.first { (id, _) -> RandomSceneGenerator.born(id).staticObjects.none { it.type == SceneObjectType.HOUSE } }.first
        // As a save of it would be, the theme's colours standing in (they need Android's HSV on the JVM).
        val fromRandom = CustomThemeEntry(
            id = "custom:random", name = "Random", theme = ThemeCatalog.byId("autumn").copy(id = "custom:random"),
            layout = SceneObjectCatalog.layoutFor(randomId, 0), customization = randomDefaults(randomId),
        )
        val asBefore = listOf(fromBuiltIn, fromRandom).map { e ->
            e.copy(
                id = e.id + "-before",
                layout = e.layout.copy(staticObjects = e.layout.staticObjects.filter {
                    SceneObjectCatalog.isSeasonalDecorationCandidate(it) || e.customization.keepCandidate(it, e.layout.densityScheme)
                }),
            )
        }
        CustomThemeRegistry.update(CustomThemeData(customThemes = listOf(fromBuiltIn, fromRandom) + asBefore))
        for (e in listOf(fromBuiltIn, fromRandom) + asBefore) assertEvery(e.id, SceneObjectCatalog.layoutFor(e.id, 0))
        assertEquals(
            "a save keeps every candidate (the scarecrows, laid again on the snowmen, aside)",
            SceneObjectCatalog.layoutFor("autumn", 0).staticObjects.count { it.type != SceneObjectType.SCARECROW },
            fromBuiltIn.layout.staticObjects.size,
        )
    }

    /**
     * **On a random theme nothing appears by itself**: what stands at its starting settings is what stood on the street it
     * was born with -- on the themes born with towers and no place among the houses too, whose places wait for *Show shops
     * and public buildings* (the maintainer, 2026-11-08: *«Aggiungili, ma spenti: dai a negozi ed edifici pubblici un
     * interruttore loro, come le altre cose.»*, *«Uno solo (consigliata)»*) -- but for the trees, houses and parasols a
     * random theme whose own keep none at 65 % is given (decision 97; *«D2 no: correggiamo i numeri come per gli alberi»*,
     * 2026-11-08), and only those. Turned on, the switch brings the four places.
     */
    @Test
    fun `on a random theme what stands as it ships is what it was born with`() {
        var read = 0
        var towersNoPlace = 0
        var treesGiven = 0
        var givenThemes = 0
        for ((id, street) in randomStreets().filter { it.first.removePrefix("random:").toInt() <= 400 }) {
            read++
            val born = RandomSceneGenerator.born(id)
            val bornStreet = SceneObjectCatalog.withTrees(RandomSceneGenerator.generateLayout(id, 0), id)
            val c = randomDefaults(id)
            fun standing(l: SceneObjectLayout, k: SceneCustomization) =
                l.staticObjects.filter { it.type != SceneObjectType.TREE && it.type != SceneObjectType.PALM_TREE && k.keepCandidate(it, l.densityScheme) }.toSet()
            // The houses and parasols given where the theme's own keep none at the factory amount, and nothing else.
            fun ownKeepNone(type: SceneObjectType): Boolean {
                val own = born.staticObjects.filter { it.type == type }
                return own.isNotEmpty() && own.none { SceneCustomization.DEFAULT.keepCandidate(it, street.densityScheme) }
            }
            val given = setOf(SceneObjectType.HOUSE, SceneObjectType.PARASOL).filter { ownKeepNone(it) }.toSet()
            if (given.isNotEmpty()) givenThemes++
            val bornStanding = standing(bornStreet, c)
            val shipped = standing(street, c)
            assertTrue("$id: what stood still stands", shipped.containsAll(bornStanding))
            assertEquals("$id: nothing appears by itself but what is given", emptySet<StaticSceneObject>(), (shipped - bornStanding).filter { it.type !in given }.toSet())
            val places = born.staticObjects.any { StorefrontStreet.isPlace(it) }
            assertEquals("$id: Show Shops as born", places, c.shopsVisible)
            assertEquals("$id: Show Public buildings as born", places, c.publicsVisible)
            if (!places && born.staticObjects.any { it.type == SceneObjectType.SKYSCRAPER }) {
                towersNoPlace++
                val on = c.copy(shopsVisible = true, publicsVisible = true)
                assertEquals("$id: turned on, the four places", 4, (standing(street, on) - standing(bornStreet, c)).count { StorefrontStreet.isPlace(it) })
            }
            val ownTrees = bornStreet.staticObjects.count { it.type == SceneObjectType.TREE || it.type == SceneObjectType.PALM_TREE }
            if (street.staticObjects.count { it.type == SceneObjectType.TREE || it.type == SceneObjectType.PALM_TREE } > ownTrees) treesGiven++
            assertEquals("$id: the cars as born", born.carCount > 0, c.cars.visible)
        }
        assertTrue("random themes read: $read", read >= 250)
        assertEquals("random themes born with towers and no place (of those the JVM reads)", 31, towersNoPlace)
        assertTrue("random themes given trees: $treesGiven", treesGiven > 50)
        assertTrue("random themes whose own houses or parasols keep none, counted on the born data: $givenThemes", givenThemes >= 40)
    }

    /**
     * *Show Shops* and *Show Public buildings* hold the six buildings on the street and on the gallery card, stored and read
     * back (below the road: `NearRowTest`; each group alone and who updates: `PlaceSwitchesTest`).
     */
    @Test
    fun `the six buildings' switches hold the places everywhere they stand`() {
        val autumn = SceneObjectCatalog.layoutFor("autumn", 0)
        val on = defaultCustomizationFor("autumn")
        val off = on.copy(shopsVisible = false, publicsVisible = false)
        val places = StorefrontStreet.places(autumn)
        assertTrue(places.all { on.keepCandidate(it, autumn.densityScheme) })
        assertTrue(places.none { off.keepCandidate(it, autumn.densityScheme) })
        assertTrue("the towers stay", autumn.staticObjects.any { !StorefrontStreet.isPlace(it) && it.type == SceneObjectType.SKYSCRAPER && off.keepCandidate(it, autumn.densityScheme) })
        assertTrue("with Show Towers off, all", places.all { on.copy(buildings = on.buildings.copy(visible = false)).keepCandidate(it, autumn.densityScheme) })
        assertTrue("a change rebuilds the street", !on.staticStructurallyEquals(off))
        assertTrue("each switch rebuilds it", !on.staticStructurallyEquals(on.copy(shopsVisible = false)) && !on.staticStructurallyEquals(on.copy(publicsVisible = false)))
        val theme = ThemeCatalog.byId("autumn")
        val cardOn = ThemePreviewScenes.forTheme(theme, on, layout = autumn)
        val cardOff = ThemePreviewScenes.forTheme(theme, off, layout = autumn)
        assertTrue("the card leaves out the three buildings", cardOff.items.size == cardOn.items.size - 3)
        // Stored and read back, in a theme file and in the settings' writer.
        assertEquals(false, sceneCustomizationFromJson(off.toJson(), theme).placesVisible)
        assertEquals(true, sceneCustomizationFromJson(on.toJson().apply { remove("placesVisible"); remove("shopsVisible"); remove("publicsVisible") }, theme).placesVisible)
    }

    /** A place added to a random street stands with its front clear, as the built-in streets' plan keeps them (decision 60). */
    @Test
    fun `a place added to a random street has its front clear`() {
        var checked = 0
        var alone = 0
        for ((id, street) in randomStreets()) {
            if (RandomSceneGenerator.born(id).staticObjects.any { StorefrontStreet.isPlace(it) }) continue
            for (place in StorefrontStreet.places(street)) {
                checked++
                val nearer = street.staticObjects.filter { it !== place && it.depthFraction > place.depthFraction }
                val covered = SceneObjectCatalog.frontRects(place).maxOf { f ->
                    SceneObjectCatalog.coveredShare(f, nearer.flatMap { SceneObjectCatalog.occluderBoxes(it) })
                }
                if (StorefrontStreet.places(street).size > 1) {
                    assertTrue("$id: a place at ${place.tileFractionX} covered %.2f".format(covered), covered <= 0.40f + 1e-4f)
                } else {
                    alone++
                }
            }
        }
        assertTrue("added places checked: $checked", checked > 1000)
        println("random streets where no spot is clear and the clearest place stays alone: $alone")
    }

    /**
     * The streets users saved from the 5.12 and the 5.13B, every candidate standing, are found as the built-ins they were
     * saved from, or else keep what they carry: nothing is added to them.
     */
    @Test
    fun `a street saved by the 5_12 or the 5_13B gets nothing it did not have`() {
        val found = StringBuilder()
        for (version in listOf("v5.12", "v5.13B")) {
            val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("themes/streets-saved-by-$version.json")) { version }
            val saved = org.json.JSONObject(stream.bufferedReader().readText())
            for (themeId in saved.keys()) {
                val layout = sceneObjectLayoutFromJson(saved.getJSONObject(themeId))
                val completed = SceneObjectCatalog.withEveryCategory(layout, "custom:$themeId", 0)
                val source = SceneObjectCatalog.builtInSourceOf(layout)
                found.append("$version $themeId -> $source\n")
                assertEquals("$version $themeId: nothing added", layout.staticObjects.size, completed.staticObjects.size)
            }
        }
        println(found)
    }

    @Test
    fun `a random street's own id deals what it is given, the same every time`() {
        val id = "random:7"
        assertEquals(SceneObjectCatalog.layoutFor(id, 0), SceneObjectCatalog.layoutFor(id, 0))
        assertNull("a random street is from no built-in", SceneObjectCatalog.builtInSourceOf(SceneObjectCatalog.layoutFor(id, 0)))
    }
}
