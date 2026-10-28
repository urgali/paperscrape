package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.prefs.ThemeParseResult
import com.paperscrape.livewallpaper.prefs.ThemeShare
import com.paperscrape.livewallpaper.prefs.parseThemeShare
import com.paperscrape.livewallpaper.prefs.toJsonString
import com.paperscrape.livewallpaper.ui.snapshotEntry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A saved theme stands its seasonal decorations as the theme it was saved from, once** (`DESIGN_NOTES.md` §12, decision
 * 89). A layout saved from a theme carries that theme's snowmen, gifts, penguins, bunnies, Easter eggs and
 * pumpkins (`asSaved` leaves out the scarecrows alone), and the catalogue lays under it only the kinds it does not carry,
 * the scarecrows on its snowmen (`SceneObjectCatalog.layoutFor`). A catalogue that laid every kind again would draw a
 * theme saved over itself twice in one place, and one saved under a new name with more decorations than the original.
 * The stores in `resources/themes/christmas-saved-by-*.json`, written by published builds' own code, read the same.
 */
class SavedThemeDecorationsTest {

    @After
    fun clearRegistry() = CustomThemeRegistry.update(CustomThemeData.EMPTY)

    private val christmas = ThemeCatalog.byId("christmas")

    private val decorations = setOf(
        SceneObjectType.SNOWMAN, SceneObjectType.GIFT, SceneObjectType.PENGUIN, SceneObjectType.BUNNY,
        SceneObjectType.EASTER_EGG, SceneObjectType.PUMPKIN, SceneObjectType.SCARECROW,
    )

    /** [c] with every decoration on at 100 % besides: every candidate stands. */
    private fun everyDecoration(c: SceneCustomization): SceneCustomization = c.copy(
        snowmen = c.snowmen.copy(visible = true, density = 1f),
        gifts = c.gifts.copy(visible = true, density = 1f),
        penguins = c.penguins.copy(visible = true, density = 1f),
        bunnies = c.bunnies.copy(visible = true, density = 1f),
        easterEggs = c.easterEggs.copy(visible = true, density = 1f),
        pumpkins = c.pumpkins.copy(visible = true, density = 1f),
        scarecrows = c.scarecrows.copy(visible = true, density = 1f),
    )

    /** The decorations [id] stands under [c], in the order the catalogue lays them. */
    private fun standing(id: String, c: SceneCustomization): List<StaticSceneObject> {
        val layout = SceneObjectCatalog.layoutFor(id, ThemeCatalog.byId(id).accentColor)
        return layout.staticObjects.filter { it.type in decorations && c.keepCandidate(it, layout.densityScheme) }
    }

    /** How many of each decoration's candidates [id]'s layout holds. */
    private fun candidatesOf(id: String): Map<SceneObjectType, Int> =
        SceneObjectCatalog.layoutFor(id, ThemeCatalog.byId(id).accentColor).staticObjects
            .filter { it.type in decorations }.groupingBy { it.type }.eachCount()

    private val tenOfEach: Map<SceneObjectType, Int> = decorations.associateWith { SceneObjectCatalog.CANDIDATES_PER_CATEGORY }

    @Test
    fun `Christmas saved over itself stands its decorations once, where Christmas stands them`() {
        val shipped = defaultCustomizationFor("christmas")
        val before = standing("christmas", shipped)
        assertTrue("Christmas stands snowmen and gifts as it ships", before.isNotEmpty())
        val entry = snapshotEntry("christmas", "Christmas", "christmas", shipped, "christmas")
        CustomThemeRegistry.update(CustomThemeData(overrides = mapOf("christmas" to entry)))
        val after = standing("christmas", entry.customization)
        println("Christmas: ${before.size} decorations standing; saved over itself: ${after.size}, ${after.size - after.toSet().size} twice in one place")
        assertEquals("no decoration twice in one place", after.toSet().size, after.size)
        assertEquals("the decorations Christmas stands, where it stands them", before, after)
        assertEquals("ten candidates of each, as the built-in", tenOfEach, candidatesOf("christmas"))
    }

    /** A copy of Christmas, under four custom ids of different shapes, stands what Christmas stands, where it stands it. */
    @Test
    fun `Christmas saved under a new name stands the decorations Christmas stands, where it stands them`() {
        val shipped = defaultCustomizationFor("christmas")
        val source = standing("christmas", shipped)
        for (id in listOf("custom:my-christmas", "custom:v-five", "custom:1761552000000", "custom:1761700000000")) {
            val entry = snapshotEntry(id, "My Christmas", "christmas", shipped, "christmas")
            CustomThemeRegistry.update(CustomThemeData(customThemes = listOf(entry)))
            val saved = standing(entry.id, entry.customization)
            println("snowmen and gifts standing: Christmas ${source.size}, $id ${saved.size}")
            assertEquals("$id: the decorations Christmas stands, where it stands them", source, saved)
            assertEquals("$id: ten candidates of each, as a built-in", tenOfEach, candidatesOf(entry.id))
            CustomThemeRegistry.update(CustomThemeData.EMPTY)
        }
    }

    /** Every theme with everything on: the scarecrows too stand on the snowmen of the theme a copy was saved from. */
    @Test
    fun `every built-in saved under a new name stands every decoration where the built-in does`() {
        for (theme in listOf("sunset", "autumn", "winter", "spring", "desert", "christmas", "new_year", "beach", "city",
            "tundra", "easter", "halloween")) {
            val c = everyDecoration(defaultCustomizationFor(theme))
            val source = standing(theme, c)
            val entry = snapshotEntry("custom:copy-of-$theme", "Copy", theme, c, theme)
            CustomThemeRegistry.update(CustomThemeData(customThemes = listOf(entry)))
            assertEquals("$theme: a copy under a new name", source, standing(entry.id, entry.customization))
            CustomThemeRegistry.update(CustomThemeData.EMPTY)
        }
    }

    @Test
    fun `a saved theme's file carries its decorations and no scarecrow, and reads back the same`() {
        val c = everyDecoration(defaultCustomizationFor("christmas"))
        val entry = snapshotEntry("custom:file", "File", "christmas", c, "christmas")
        assertTrue("no scarecrow saved", entry.layout.staticObjects.none { it.type == SceneObjectType.SCARECROW })
        val carried = entry.layout.staticObjects.filter { it.type in decorations }
        assertEquals("the six dealt kinds, ten each", 60, carried.size)
        val back = customThemeDataFromJsonString(CustomThemeData(customThemes = listOf(entry)).toJsonString()).customThemes.single()
        assertEquals("read back as written", carried, back.layout.staticObjects.filter { it.type in decorations })
        CustomThemeRegistry.update(CustomThemeData(customThemes = listOf(back)))
        assertEquals("ten candidates of each, none twice", tenOfEach, candidatesOf(back.id))
    }

    @Test
    fun `a layout saved without the decorations gets them from its own id`() {
        val c = everyDecoration(defaultCustomizationFor("christmas"))
        val entry = snapshotEntry("custom:old", "Old", "christmas", c, "christmas")
        val bare = entry.copy(layout = entry.layout.copy(staticObjects = entry.layout.staticObjects.filter { it.type !in decorations }))
        CustomThemeRegistry.update(CustomThemeData(customThemes = listOf(bare)))
        assertEquals("ten candidates of each, from its id", tenOfEach, candidatesOf(bare.id))
    }

    /**
     * A theme saved while none of its decorations stood -- Sunset as it ships -- keeps every candidate all the same, so a
     * slider raised later brings them where the original would stand them.
     */
    @Test
    fun `a copy saved while no decoration stood brings them where the original would`() {
        val shipped = defaultCustomizationFor("sunset")
        assertTrue("Sunset ships no decoration", standing("sunset", shipped).isEmpty())
        val entry = snapshotEntry("custom:my-sunset", "My Sunset", "sunset", shipped, "sunset")
        CustomThemeRegistry.update(CustomThemeData(customThemes = listOf(entry)))
        val raised = everyDecoration(shipped)
        assertEquals("every decoration, where Sunset stands it", standing("sunset", raised), standing(entry.id, raised))
    }

    /** A street whose decorations come from no built-in -- a random theme's -- keeps them, the scarecrows on its own snowmen. */
    @Test
    fun `a copy of a random theme keeps its decorations, and the scarecrows stand on its snowmen`() {
        val full = SceneObjectCatalog.layoutFor("random:7", 0)
        val carried = full.staticObjects.filter { SceneObjectCatalog.isSeasonalDecorationCandidate(it) && it.type != SceneObjectType.SCARECROW }
        assertTrue("random:7 deals the decorations' candidates", carried.isNotEmpty())
        val entry = CustomThemeEntry(
            id = "custom:from-random",
            name = "From random",
            theme = christmas.copy(id = "custom:from-random", displayName = "From random"),
            layout = full.asSaved(),
            customization = defaultCustomizationFor("christmas"),
        )
        CustomThemeRegistry.update(CustomThemeData(customThemes = listOf(entry)))
        val back = SceneObjectCatalog.layoutFor(entry.id, 0).staticObjects
        assertEquals("its own decorations, once", carried, back.filter { SceneObjectCatalog.isSeasonalDecorationCandidate(it) && it.type != SceneObjectType.SCARECROW })
        assertEquals(
            "the scarecrows on its snowmen",
            carried.filter { it.type == SceneObjectType.SNOWMAN }.map { it.copy(type = SceneObjectType.SCARECROW) },
            back.filter { it.type == SceneObjectType.SCARECROW },
        )
    }

    @Test
    fun `a theme file shared from Christmas carries its decorations, and its import stands them where Christmas does`() {
        val shipped = defaultCustomizationFor("christmas")
        val share = ThemeShare.of("christmas", "Shared Christmas", shipped, "5.17", 0L)
        // A shared file carries the whole street, as the catalogue lays it, the scarecrows aside.
        assertEquals(
            "the shared street carries Christmas's decorations' candidates, no scarecrow",
            SceneObjectCatalog.layoutFor("christmas", christmas.accentColor).staticObjects
                .filter { it.type in decorations && it.type != SceneObjectType.SCARECROW },
            share.layout.staticObjects.filter { it.type in decorations },
        )
        val back = (parseThemeShare(share.toJsonString()) as ThemeParseResult.Ok).share
        val imported = back.asNewCustomTheme("custom:shared")
        CustomThemeRegistry.update(CustomThemeData(customThemes = listOf(imported)))
        assertEquals("ten candidates of each, none twice", tenOfEach, candidatesOf(imported.id))
        assertEquals("the decorations Christmas stands", standing("christmas", shipped), standing(imported.id, imported.customization))
    }

    @Test
    fun `a random theme saved keeps the decorations it rolled of its own`() {
        // A random street rolls snowmen, gifts, penguins, bunnies and eggs as part of itself, at depths of its own.
        val id = (1..500).map { "random:$it" }.first { rid ->
            RandomSceneGenerator.generateLayout(rid, 0).staticObjects.any { it.type in decorations }
        }
        // The street is the layout alone: a random theme's colours need Android's HSV, which the JVM does not have, and
        // the accent colours the street without moving it.
        val layout = RandomSceneGenerator.generateLayout(id, 0)
        val rolled = layout.staticObjects.filter { it.type in decorations }
        assertTrue("$id: none of what it rolled is taken for a decoration's candidate",
            rolled.none { SceneObjectCatalog.isSeasonalDecorationCandidate(it) })
        assertEquals("$id: what it rolled is saved with it", rolled, layout.asSaved().staticObjects.filter { it.type in decorations })
        val json = JSONObject().put("staticObjects", JSONArray(layout.staticObjects.map { it.toJson() })).put("cars", JSONArray())
        assertEquals("$id: and read back", rolled, sceneObjectLayoutFromJson(json).staticObjects.filter { it.type in decorations })
    }

    /** The store as the published builds wrote it, after saving Christmas over itself and as "My Christmas". */
    @Test
    fun `the Christmas the 5_16 and the 5_17 saved stands its decorations once, as Christmas`() {
        for (version in listOf("v5.16", "v5.17")) {
            val name = "themes/christmas-saved-by-$version.json"
            val raw = checkNotNull(javaClass.classLoader?.getResource(name)) { name }.readText()
            assertTrue("$version: the file holds the decorations saved with it", raw.contains("\"SNOWMAN\"") && raw.contains("\"GIFT\""))
            val data = customThemeDataFromJsonString(raw)
            val over = data.overrides.getValue("christmas")
            val mine = data.customThemes.single { it.name == "My Christmas" }
            val builtIn = standing("christmas", over.customization)
            CustomThemeRegistry.update(data)
            assertEquals("$version: Christmas saved over itself, as Christmas", builtIn, standing("christmas", over.customization))
            assertEquals("$version: My Christmas, as Christmas", builtIn, standing(mine.id, mine.customization))
            assertEquals("$version: ten candidates of each", tenOfEach, candidatesOf("christmas"))
            assertEquals("$version: My Christmas, ten candidates of each", tenOfEach, candidatesOf(mine.id))
            CustomThemeRegistry.update(CustomThemeData.EMPTY)
        }
    }
}
