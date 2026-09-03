package com.paperscrape.livewallpaper.engine

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Themes saved before their generator was fixed, repaired once on the way in.
 *
 * Two defects, one migration, and the same story behind both: a generator was corrected and the
 * themes already on disk were not.
 *
 * - **Duplicate storefronts**, item 9 of `BACKLOG_v4_19.md`. Pass six made the catalogue emit
 *   exactly one restaurant and one bar per tile and added the dedup; a theme saved before it can
 *   still carry two of the same shop on one tile, and has been able to for four releases.
 * - **Duplicate special vehicles**, items 11 and 14. v4.20 capped the types where they are rolled,
 *   which does nothing for a theme rolled earlier -- and eight of the twelve *shipped* themes were
 *   in that state, so a user's saved copies of them are too.
 *
 * ### Why these are migrations and the lane fix was not
 *
 * `SceneObjectCatalog.canonicaliseTraffic` runs on **every** load, deliberately, because a stored
 * lane coordinate is a stale copy of a constant and can never be believed. These two are the
 * opposite case: what a theme contains -- how many buildings, which vehicles -- genuinely belongs
 * to the theme, and rewriting it on every read would be the app overruling the user forever.
 * Rewriting it once, for a defect the app itself shipped, is a repair.
 *
 * ### And the one that must not happen
 *
 * `customThemeDataFromJsonString` turns *any* exception out of the migration into
 * `CustomThemeData.EMPTY` -- which is every theme the user has ever saved. The DataStore
 * corruption fixed in v3.1 is what that looks like from the outside. So the last tests here feed
 * the migration payloads it has no business understanding and require it to leave them alone
 * rather than to throw.
 */
class PrePassSixThemeMigrationTest {

    // ------------------------------------------------------------------ storefronts

    /** Two restaurants and two bars on one tile: the shape a pre-pass-six save can hold. */
    @Test
    fun `a pre-pass-six theme keeps one shop per variant and the surplus becomes towers`() {
        val stored = prePassSix(
            entry(
                shops = listOf(0.36f, 0.44f, 0.62f, 0.78f),
                cars = listOf(CarType.PLAIN, CarType.PLAIN),
            ),
        )
        val shops = shopDepths(load(stored))

        assertEquals("one shop per variant half-band, and no more", 2, shops.size)
        assertEquals(
            "one below the variant split and one above it",
            listOf(1, 1),
            listOf(
                shops.count { it < SceneSpace.SHOP_VARIANT_DEPTH_SPLIT },
                shops.count { it >= SceneSpace.SHOP_VARIANT_DEPTH_SPLIT },
            ),
        )
    }

    /** The surplus is moved, not deleted: the theme still has every building it had. */
    @Test
    fun `the demoted shops keep their place in the scene and only change depth`() {
        val before = entry(shops = listOf(0.36f, 0.44f, 0.62f, 0.78f), cars = listOf(CarType.PLAIN))
        val after = load(prePassSix(before))

        val originals = before.layout.staticObjects
        val migrated = after.customThemes.single().layout.staticObjects
        assertEquals("the migration must not add or remove buildings", originals.size, migrated.size)
        for ((original, moved) in originals.zip(migrated)) {
            assertEquals("a building changed category", original.type, moved.type)
            assertEquals("a building moved sideways", original.tileFractionX, moved.tileFractionX, 1e-6f)
            assertEquals("a building changed size", original.scale, moved.scale, 1e-6f)
        }
        val demoted = migrated.filter {
            it.type == SceneObjectType.SKYSCRAPER && it.depthFraction < SceneSpace.BUILDING_TOWER_MAX_DEPTH
        }
        assertEquals("two of the four commercial buildings should have become towers", 2, demoted.size)
    }

    /** A theme that was already correct is not touched. */
    @Test
    fun `a theme with one shop per variant comes back unchanged`() {
        val before = entry(shops = listOf(0.40f, 0.70f), cars = listOf(CarType.PLAIN))
        val after = load(prePassSix(before)).customThemes.single()
        assertEquals(before.layout.staticObjects, after.layout.staticObjects)
    }

    // ------------------------------------------------------------------ special vehicles

    @Test
    fun `a pre-cap theme keeps one of each special type and demotes the rest`() {
        val before = entry(
            shops = listOf(0.40f),
            cars = listOf(
                CarType.FIRE_TRUCK, CarType.PLAIN, CarType.FIRE_TRUCK,
                CarType.TAXI, CarType.TAXI, CarType.POLICE,
            ),
        )
        val after = load(prePassSix(before)).customThemes.single().layout.cars

        assertEquals("the migration must not add or remove cars", 6, after.size)
        assertEquals(
            mapOf(
                CarType.FIRE_TRUCK to 1, CarType.TAXI to 1,
                CarType.POLICE to 1, CarType.PLAIN to 3,
            ),
            after.groupingBy { it.type }.eachCount(),
        )
        assertEquals(
            "the first of each type is the one kept, as the generator does",
            listOf(
                CarType.FIRE_TRUCK, CarType.PLAIN, CarType.PLAIN,
                CarType.TAXI, CarType.PLAIN, CarType.POLICE,
            ),
            after.map { it.type },
        )
        assertEquals(
            "a demoted vehicle keeps its colour",
            before.layout.cars.map { it.color }, after.map { it.color },
        )
    }

    // ------------------------------------------------------------------ the properties

    /**
     * Running it twice changes nothing more than running it once.
     *
     * Not academic: a payload is migrated on **every load** until the user next saves it, so a step
     * that moved something each time would walk a theme's buildings down the tower band one read at
     * a time.
     */
    @Test
    fun `the migration is idempotent`() {
        val once = load(prePassSix(entry(shops = listOf(0.36f, 0.44f, 0.62f, 0.78f), cars = ALL_TYPES)))
        val twice = load(prePassSix(once.customThemes.single()))
        assertEquals(once.customThemes.single().layout, twice.customThemes.single().layout)
    }

    /** A payload already at the current version is left exactly as it is. */
    @Test
    fun `a current payload is not migrated again`() {
        val duplicated = entry(shops = listOf(0.36f, 0.44f), cars = listOf(CarType.TAXI, CarType.TAXI))
        val current = CustomThemeData(customThemes = listOf(duplicated)).toJsonString()
        assertEquals(
            "a version ${CUSTOM_THEME_SCHEMA_VERSION} payload must be believed, defects and all",
            duplicated.layout.cars.map { it.type },
            customThemeDataFromJsonString(current).customThemes.single().layout.cars.map { it.type },
        )
    }

    // ------------------------------------------------------------------ it cannot lose the store

    /**
     * Malformed content inside a theme costs that theme its repair and nothing else.
     *
     * Asserted on the migration itself rather than through
     * [customThemeDataFromJsonString], and the distinction is the point. The reader wraps
     * everything in one `catch` that returns `CustomThemeData.EMPTY`, so **anything** thrown from
     * here is every saved theme the user has -- the v3.1 DataStore corruption seen from the
     * outside. This is the boundary that has to hold, so this is where it is tested: each case is
     * a shape the migration has no business understanding, and each must leave that entry as it
     * found it while still repairing the healthy theme beside it.
     *
     * That the *reader* is separately all-or-nothing about a malformed entry is true, pre-existing,
     * and not silently repaired here: making a partial read succeed would let the next save drop
     * the damaged theme for good. It is recorded in `BACKLOG_v4_20.md` instead.
     */
    @Test
    fun `a malformed entry does not stop the migration repairing the rest`() {
        val healthy = entry(shops = listOf(0.36f, 0.44f), cars = listOf(CarType.TAXI, CarType.TAXI))
        for ((name, damage) in DAMAGE) {
            val root = JSONObject(CustomThemeData(customThemes = listOf(entry(), healthy)).toJsonString())
            root.put("schemaVersion", 3)
            val themes = root.getJSONArray("customThemes")
            damage(themes.getJSONObject(0))

            // Must not throw: the reader turns anything thrown here into "no saved themes at all".
            migrateEmbeddedCustomThemes(root, 3)

            val repaired = themes.getJSONObject(1).getJSONObject("layout").getJSONArray("cars")
            val types = (0 until repaired.length()).map { repaired.getJSONObject(it).getString("type") }
            assertEquals(
                "$name: the healthy theme beside the damaged one was not repaired",
                listOf(CarType.TAXI.name, CarType.PLAIN.name), types,
            )
            assertEquals(
                "$name: the migration must still stamp the version it brought the document to",
                CUSTOM_THEME_SCHEMA_VERSION, root.getInt("schemaVersion"),
            )
        }
    }

    /**
     * A whole document of the wrong shape is survivable too, and stays "nothing saved".
     *
     * The end-to-end half of the same property, at the only granularity the reader offers.
     */
    @Test
    fun `a damaged entry leaves the store readable as empty rather than crashing`() {
        for ((name, damage) in DAMAGE) {
            val root = JSONObject(CustomThemeData(customThemes = listOf(entry())).toJsonString())
            root.put("schemaVersion", 3)
            damage(root.getJSONArray("customThemes").getJSONObject(0))
            val loaded = customThemeDataFromJsonString(root.toString())
            assertTrue(
                "$name: reading a damaged store must degrade, never throw",
                loaded == CustomThemeData.EMPTY || loaded.customThemes.size == 1,
            )
        }
    }

    /** And the whole document being nonsense still degrades to "nothing saved" rather than a crash. */
    @Test
    fun `an unparseable document is still just an empty store`() {
        assertEquals(CustomThemeData.EMPTY, customThemeDataFromJsonString("{\"schemaVersion\": 3, "))
        assertEquals(CustomThemeData.EMPTY, customThemeDataFromJsonString("not json at all"))
    }

    // ------------------------------------------------------------------ fixtures

    private fun load(entry: CustomThemeEntry) = load(prePassSix(entry))

    private fun load(raw: String) = customThemeDataFromJsonString(raw)

    /** [entry] serialised and then stamped back to the version that predates this migration. */
    private fun prePassSix(entry: CustomThemeEntry): String =
        JSONObject(CustomThemeData(customThemes = listOf(entry)).toJsonString())
            .apply { put("schemaVersion", 3) }
            .toString()

    private fun entry(
        shops: List<Float> = listOf(0.40f),
        cars: List<CarType> = listOf(CarType.PLAIN),
    ) = CustomThemeEntry(
        id = "custom:pre-pass-six",
        name = "Saved Long Ago",
        theme = theme(),
        layout = SceneObjectLayout(
            staticObjects = listOf(
                StaticSceneObject(SceneObjectType.HOUSE, depthFraction = 0.12f, tileFractionX = 0.2f, scale = 0.8f),
            ) + shops.mapIndexed { i, depth ->
                StaticSceneObject(
                    SceneObjectType.SKYSCRAPER,
                    depthFraction = depth,
                    tileFractionX = 0.1f + 0.2f * i,
                    scale = 1f + 0.05f * i,
                )
            },
            cars = cars.mapIndexed { i, type ->
                CarObject(
                    laneYFraction = if (i % 2 == 0) SceneSpace.ROAD_LANE_NEAR_Y_FRACTION else SceneSpace.ROAD_LANE_FAR_Y_FRACTION,
                    speedFraction = 0.1f,
                    startDelaySeconds = 0.3f + 0.32f * (i / 2),
                    color = 0xFF102030.toInt() + i,
                    reverse = i % 2 != 0,
                    type = type,
                )
            },
        ),
        customization = SceneCustomization.DEFAULT,
    )

    private fun theme() = SceneTheme(
        id = "custom:pre-pass-six",
        displayName = "Saved Long Ago",
        skyNight = intArrayOf(0xFF0B1026.toInt(), 0xFF1B2240.toInt()),
        skyDawn = intArrayOf(0xFF6A4E77.toInt(), 0xFFE0885A.toInt()),
        skyDay = intArrayOf(0xFF4FA3D1.toInt(), 0xFFBFE6F5.toInt()),
        skyDusk = intArrayOf(0xFF3A2E5C.toInt(), 0xFFD97742.toInt()),
        hillColorsDay = intArrayOf(0xFF6FA36B.toInt(), 0xFF4E7A4C.toInt()),
        hillColorsNight = intArrayOf(0xFF1E2A2E.toInt(), 0xFF16211F.toInt()),
        sunColor = 0xFFFFD166.toInt(),
        moonColor = 0xFFF4F1DE.toInt(),
        starColor = 0xFFFFFFFF.toInt(),
        accentColor = 0xFFE07A5F.toInt(),
    )

    private fun shopDepths(data: CustomThemeData): List<Float> =
        data.customThemes.single().layout.staticObjects
            .filter { it.type == SceneObjectType.SKYSCRAPER && it.depthFraction >= SceneSpace.BUILDING_TOWER_MAX_DEPTH }
            .map { it.depthFraction }

    private companion object {
        val ALL_TYPES = listOf(
            CarType.FIRE_TRUCK, CarType.FIRE_TRUCK, CarType.TAXI,
            CarType.TAXI, CarType.POLICE, CarType.POLICE,
        )

        /** Ways a stored theme can be shaped that this migration was never told about. */
        val DAMAGE: List<Pair<String, (JSONObject) -> Unit>> = listOf(
            "layout is a string" to { e -> e.put("layout", "gone") },
            "cars is an object" to { e -> e.getJSONObject("layout").put("cars", JSONObject()) },
            "a car is a number" to { e ->
                e.getJSONObject("layout").getJSONArray("cars").put(0, 7)
            },
            "a car type is unknown" to { e ->
                e.getJSONObject("layout").getJSONArray("cars").getJSONObject(0).put("type", "HOVERCRAFT")
            },
            "a building has no depth" to { e ->
                e.getJSONObject("layout").getJSONArray("staticObjects").getJSONObject(0).remove("depthFraction")
            },
            "a building depth is not a number" to { e ->
                e.getJSONObject("layout").getJSONArray("staticObjects").getJSONObject(0)
                    .put("depthFraction", "far away")
            },
            "staticObjects holds a null" to { e ->
                e.getJSONObject("layout").put("staticObjects", JSONArray().put(JSONObject.NULL))
            },
        )
    }
}
