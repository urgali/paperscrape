package com.paperscrape.livewallpaper.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 141 (v5.7F): a street saved before v5.6 is given the school it never had, once, on the way
 * in -- and nothing it already had moves.
 *
 * ### Why it can only be an addition
 *
 * A saved theme stores what was **standing** when it was saved (`snapshotEntry` filters the
 * layout through `keepCandidate`), so a pre-v5.6 street holds a restaurant at 0.4444, a bar at
 * 0.7111 and the towers its density kept -- nothing in the school's third, and no tower the user
 * cannot see. Making one of them the school would take a building off the user's skyline to put
 * it on their street. So the migration adds one ([SceneObjectCatalog.missingSchoolFor]), and the
 * property this class exists for is the one the maintainer's decision turns on: **every object
 * the user saved comes back identical**, and exactly one object is new.
 *
 * Where the school stands is asserted in `ShopFrontVisibilityTest`, with that class's independent
 * model of the frontage rules.
 */
class SavedThemeSchoolMigrationTest {

    private val themes = ThemeCatalog.ALL.map { it.id }

    /** A built-in's street as a user saves it at its defaults, school taken out: the pre-v5.6 shape. */
    private fun streetWithoutSchool(themeId: String): List<StaticSceneObject> {
        val c = defaultCustomizationFor(themeId)
        return SceneObjectCatalog.layoutFor(themeId, 0xFF8899AA.toInt()).staticObjects
            .filter { c.keepCandidate(it) }
            .filterNot { isSchool(it) }
    }

    private fun isSchool(o: StaticSceneObject) =
        o.type == SceneObjectType.SKYSCRAPER && SceneObjectRenderer.variantFor(o) == SceneSpace.SceneVariant.SCHOOL

    private fun entry(objects: List<StaticSceneObject>) = CustomThemeEntry(
        id = "custom:saved-before-the-school",
        name = "Saved Before The School",
        theme = ThemeCatalog.byId("sunset").copy(id = "custom:saved-before-the-school"),
        layout = SceneObjectLayout(staticObjects = objects, cars = emptyList()),
        customization = SceneCustomization.DEFAULT,
    )

    /** [objects] written by this build and stamped back to schema 4, the version v5.5 and v5.6 wrote. */
    private fun savedAtFour(objects: List<StaticSceneObject>, asOverride: Boolean = false): String {
        val data = if (asOverride) {
            CustomThemeData(overrides = mapOf("sunset" to entry(objects)))
        } else {
            CustomThemeData(customThemes = listOf(entry(objects)))
        }
        return JSONObject(data.toJsonString()).apply { put("schemaVersion", 4) }.toString()
    }

    private fun loaded(raw: String, asOverride: Boolean = false): List<StaticSceneObject> {
        val data = customThemeDataFromJsonString(raw)
        return if (asOverride) data.overrides.getValue("sunset").layout.staticObjects
        else data.customThemes.single().layout.staticObjects
    }

    @Test
    fun `a street saved without a school gets exactly one and keeps everything it had`() {
        for (themeId in themes) {
            for (asOverride in listOf(false, true)) {
                val before = streetWithoutSchool(themeId)
                val after = loaded(savedAtFour(before, asOverride), asOverride)
                assertEquals("$themeId: one object and one only is added", before.size + 1, after.size)
                assertEquals("$themeId: every saved object comes back identical, in order", before, after.dropLast(1))
                val school = after.last()
                assertTrue("$themeId: the added object is not a school", isSchool(school))
                assertEquals(SceneObjectCatalog.GENERATED_SCHOOL_DEPTH, school.depthFraction, 0f)
                // And it stands in the row a generated school stands in, on every theme.
                val generated = SceneObjectCatalog.layoutFor(themeId, 0xFF8899AA.toInt()).staticObjects.single { isSchool(it) }
                assertEquals("$themeId: not the generated school's row", generated.depthFraction, school.depthFraction, 0f)
            }
        }
    }

    @Test
    fun `a street that already has its school is left exactly as it was`() {
        for (themeId in themes) {
            val c = defaultCustomizationFor(themeId)
            val street = SceneObjectCatalog.layoutFor(themeId, 0xFF8899AA.toInt()).staticObjects.filter { c.keepCandidate(it) }
            assertEquals("$themeId: a v5.6 street was changed", street, loaded(savedAtFour(street)))
        }
    }

    @Test
    fun `a street saved with its buildings switched off is not given a lone school`() {
        val noBuildings = streetWithoutSchool("sunset").filterNot { it.type == SceneObjectType.SKYSCRAPER }
        assertNull(SceneObjectCatalog.missingSchoolFor(noBuildings))
        assertEquals(noBuildings, loaded(savedAtFour(noBuildings)))
    }

    /**
     * Once is once. A payload is migrated on every load until the user next saves, and a saved
     * payload is at the current version -- so both routes must end with one school, not two.
     */
    @Test
    fun `the repair is idempotent whether the payload is saved in between or not`() {
        val raw = savedAtFour(streetWithoutSchool("winter"))
        val once = loaded(raw)
        assertEquals("loaded twice from the same bytes", once, loaded(raw))
        val resaved = CustomThemeData(customThemes = listOf(entry(once))).toJsonString()
        assertEquals("saved and loaded again", once, loaded(resaved))
        val restamped = JSONObject(resaved).apply { put("schemaVersion", 4) }.toString()
        assertEquals("saved, restamped to 4, loaded again", once, loaded(restamped))
        assertEquals(1, once.count { isSchool(it) })
    }

    @Test
    fun `a current payload is believed and not repaired again`() {
        val street = streetWithoutSchool("autumn")
        val current = CustomThemeData(customThemes = listOf(entry(street))).toJsonString()
        assertEquals(CUSTOM_THEME_SCHEMA_VERSION, JSONObject(current).getInt("schemaVersion"))
        assertEquals("a version $CUSTOM_THEME_SCHEMA_VERSION payload must be believed", street, loaded(current))
    }

    // ------------------------------------------------------------------ where it may not stand
    //
    // No built-in street makes these two rules decide anything -- measured by mutation: switching
    // either off leaves every test above green -- so each has a street built for it here, where
    // the rule is the only thing between the school and the building it protects. The geometry is
    // written out a second time on purpose (the half-widths are the artwork's, see
    // `SceneObjectCatalog.halfWidthUnits`), so the placement is not checked with its own arithmetic.

    private fun halfPx(o: StaticSceneObject, halfUnits: Float) =
        halfUnits * SceneObjectRenderer.effectiveScaleFor(o, 2340f)

    /** Horizontal distance between two objects' centres at the reference tile, the short way round. */
    private fun apartPx(a: StaticSceneObject, b: StaticSceneObject): Float {
        val d = kotlin.math.abs(a.tileFractionX - b.tileFractionX).let { minOf(it, 1f - it) }
        return d * 2160f
    }

    private fun shop(depth: Float, x: Float) =
        StaticSceneObject(SceneObjectType.SKYSCRAPER, depthFraction = depth, tileFractionX = x, scale = 1f)

    @Test
    fun `the school never stands in front of the saved restaurant, even where that is nearest`() {
        // Restaurant and bar a tenth of a tile apart: the midpoint is in front of the restaurant,
        // and with nothing else on the street every probe there is otherwise perfect.
        val restaurant = shop(0.4444f, 0.30f)
        val bar = shop(0.7111f, 0.40f)
        val school = SceneObjectCatalog.missingSchoolFor(listOf(restaurant, bar))!!
        val clear = halfPx(school, 56f) + halfPx(restaurant, 34f)
        assertTrue(
            "the school at x=${school.tileFractionX} is ${apartPx(school, restaurant)} px from the restaurant, " +
                "inside the $clear px at which its body covers the restaurant's front",
            apartPx(school, restaurant) >= clear,
        )
    }

    @Test
    fun `the school does not stand over a saved house when a clear spot exists`() {
        // The midpoint of a restaurant at 0.1 and a bar at 0.9 is 0.0, and a small house stands
        // there just behind the school's row: two buildings of one row drawn into each other.
        val house = StaticSceneObject(SceneObjectType.HOUSE, depthFraction = 0.60f, tileFractionX = 0.0f, scale = 1f)
        val street = listOf(house, shop(0.4444f, 0.1f), shop(0.7111f, 0.9f))
        assertEquals(SceneSpace.SceneVariant.HOUSE_SMALL, SceneObjectRenderer.variantFor(house))
        val school = SceneObjectCatalog.missingSchoolFor(street)!!
        val clear = halfPx(school, 56f) + halfPx(house, 48f)
        assertTrue(
            "the school at x=${school.tileFractionX} is ${apartPx(school, house)} px from the house, inside $clear px",
            apartPx(school, house) >= clear,
        )
    }

    /**
     * The same repair reaches a whole-app backup, which carries the same entries under its own
     * schema field and is migrated from the version it records (`migrateEmbeddedCustomThemes`).
     */
    @Test
    fun `a backup written before the school gets it on restore`() {
        val root = JSONObject(savedAtFour(streetWithoutSchool("tundra")))
        migrateEmbeddedCustomThemes(root, 4)
        val objects = root.getJSONArray("customThemes").getJSONObject(0).getJSONObject("layout").getJSONArray("staticObjects")
        val parsed = (0 until objects.length()).map { staticSceneObjectFromJson(objects.getJSONObject(it)) }
        assertEquals(1, parsed.count { isSchool(it) })
        assertEquals(CUSTOM_THEME_SCHEMA_VERSION, root.getInt("schemaVersion"))
    }
}
