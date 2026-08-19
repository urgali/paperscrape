package com.paperscrape.livewallpaper.engine

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip and schema-version tests for the custom theme persistence format.
 *
 * These matter more than most: this is the only place the app stores data the user *created*.
 * A silent serialisation regression does not crash anything — it quietly discards saved themes.
 *
 * Note on assertions: [SceneTheme] overrides `equals` to compare **id only**, so
 * `assertEquals(theme, parsed)` would pass even if every colour were lost in the round trip.
 * Every theme assertion below therefore compares fields explicitly. Do not "simplify" these
 * back to a single equality check.
 */
class CustomThemeDataJsonTest {

    // --- Fixtures ---------------------------------------------------------------------------

    private fun sampleTheme(id: String = "christmas") = SceneTheme(
        id = id,
        displayName = "Sample $id",
        skyNight = intArrayOf(0xFF0B1026.toInt(), 0xFF1B2240.toInt()),
        skyDawn = intArrayOf(0xFF6A4E77.toInt(), 0xFFE0885A.toInt()),
        skyDay = intArrayOf(0xFF4FA3D1.toInt(), 0xFFBFE6F5.toInt()),
        skyDusk = intArrayOf(0xFF3A2E5C.toInt(), 0xFFD97742.toInt()),
        hillColorsDay = intArrayOf(0xFF6FA36B.toInt(), 0xFF4E7A4C.toInt(), 0xFF35563A.toInt()),
        hillColorsNight = intArrayOf(0xFF1E2A2E.toInt(), 0xFF16211F.toInt()),
        sunColor = 0xFFFFD166.toInt(),
        moonColor = 0xFFF4F1DE.toInt(),
        starColor = 0xFFFFFFFF.toInt(),
        accentColor = 0xFFE07A5F.toInt(),
        hasFireworks = true,
        hasSantaSleigh = true,
    )

    private fun sampleLayout() = SceneObjectLayout(
        staticObjects = listOf(
            StaticSceneObject(SceneObjectType.HOUSE, depthFraction = 0.12f, tileFractionX = 0.2f, scale = 0.8f),
            StaticSceneObject(SceneObjectType.TREE, depthFraction = 0.65f, tileFractionX = 0.71f, scale = 1.15f),
            StaticSceneObject(SceneObjectType.SKYSCRAPER, depthFraction = 0.03f, tileFractionX = 0.94f),
        ),
        cars = listOf(
            CarObject(
                laneYFraction = 0.82f,
                speedFraction = 0.14f,
                startDelaySeconds = 3.5f,
                color = 0xFFCC3344.toInt(),
                reverse = true,
                type = CarType.TAXI,
            ),
            CarObject(
                laneYFraction = 0.86f,
                speedFraction = 0.09f,
                startDelaySeconds = 0f,
                color = 0xFF2277AA.toInt(),
            ),
        ),
    )

    private fun sampleEntry(id: String = "christmas", name: String = "My Christmas") = CustomThemeEntry(
        id = id,
        name = name,
        theme = sampleTheme(id),
        layout = sampleLayout(),
        customization = SceneCustomization.DEFAULT,
    )

    private fun sampleData() = CustomThemeData(
        overrides = mapOf("christmas" to sampleEntry("christmas", "My Christmas")),
        customThemes = listOf(
            sampleEntry("custom:abc123", "Midnight Village"),
            sampleEntry("custom:def456", "Quiet Coast"),
        ),
    )

    private fun assertThemesMatch(expected: SceneTheme, actual: SceneTheme) {
        assertEquals("id", expected.id, actual.id)
        assertEquals("displayName", expected.displayName, actual.displayName)
        assertArrayEquals("skyNight", expected.skyNight, actual.skyNight)
        assertArrayEquals("skyDawn", expected.skyDawn, actual.skyDawn)
        assertArrayEquals("skyDay", expected.skyDay, actual.skyDay)
        assertArrayEquals("skyDusk", expected.skyDusk, actual.skyDusk)
        assertArrayEquals("hillColorsDay", expected.hillColorsDay, actual.hillColorsDay)
        assertArrayEquals("hillColorsNight", expected.hillColorsNight, actual.hillColorsNight)
        assertEquals("sunColor", expected.sunColor, actual.sunColor)
        assertEquals("moonColor", expected.moonColor, actual.moonColor)
        assertEquals("starColor", expected.starColor, actual.starColor)
        assertEquals("accentColor", expected.accentColor, actual.accentColor)
        assertEquals("hasFireworks", expected.hasFireworks, actual.hasFireworks)
        assertEquals("hasSantaSleigh", expected.hasSantaSleigh, actual.hasSantaSleigh)
    }

    private fun assertLayoutsMatch(expected: SceneObjectLayout, actual: SceneObjectLayout) {
        assertEquals("staticObjects size", expected.staticObjects.size, actual.staticObjects.size)
        expected.staticObjects.forEachIndexed { i, e ->
            val a = actual.staticObjects[i]
            assertEquals("staticObjects[$i].type", e.type, a.type)
            assertEquals("staticObjects[$i].depthFraction", e.depthFraction, a.depthFraction, 0.0001f)
            assertEquals("staticObjects[$i].tileFractionX", e.tileFractionX, a.tileFractionX, 0.0001f)
            assertEquals("staticObjects[$i].scale", e.scale, a.scale, 0.0001f)
        }
        assertEquals("cars size", expected.cars.size, actual.cars.size)
        expected.cars.forEachIndexed { i, e ->
            val a = actual.cars[i]
            assertEquals("cars[$i].laneYFraction", e.laneYFraction, a.laneYFraction, 0.0001f)
            assertEquals("cars[$i].speedFraction", e.speedFraction, a.speedFraction, 0.0001f)
            assertEquals("cars[$i].startDelaySeconds", e.startDelaySeconds, a.startDelaySeconds, 0.0001f)
            assertEquals("cars[$i].color", e.color, a.color)
            assertEquals("cars[$i].reverse", e.reverse, a.reverse)
            assertEquals("cars[$i].type", e.type, a.type)
        }
    }

    // --- Round trip ---------------------------------------------------------------------------

    @Test
    fun `empty data round trips`() {
        val restored = customThemeDataFromJsonString(CustomThemeData.EMPTY.toJsonString())
        assertTrue(restored.overrides.isEmpty())
        assertTrue(restored.customThemes.isEmpty())
    }

    @Test
    fun `full data round trips preserving structure`() {
        val original = sampleData()
        val restored = customThemeDataFromJsonString(original.toJsonString())

        assertEquals(original.overrides.keys, restored.overrides.keys)
        assertEquals(original.customThemes.size, restored.customThemes.size)
        assertEquals(
            original.customThemes.map { it.id },
            restored.customThemes.map { it.id },
        )
    }

    @Test
    fun `theme colours survive the round trip`() {
        val original = sampleData()
        val restored = customThemeDataFromJsonString(original.toJsonString())

        assertThemesMatch(
            original.overrides.getValue("christmas").theme,
            restored.overrides.getValue("christmas").theme,
        )
        original.customThemes.forEachIndexed { i, entry ->
            assertThemesMatch(entry.theme, restored.customThemes[i].theme)
        }
    }

    @Test
    fun `layout objects and cars survive the round trip`() {
        // Cars survive as *vehicles*, not as coordinates. Lane, speed, direction and loop
        // slot are recomputed on every load because they are scene geometry rather than
        // theme data -- see SceneObjectCatalog.canonicaliseTraffic. What has to come back
        // unchanged is everything the theme genuinely owns.
        val original = sampleData()
        val restored = customThemeDataFromJsonString(original.toJsonString())

        val expected = original.overrides.getValue("christmas").layout
        val actual = restored.overrides.getValue("christmas").layout
        assertEquals(expected.staticObjects, actual.staticObjects)
        assertEquals(expected.cars.map { it.color }, actual.cars.map { it.color })
        assertEquals(expected.cars.map { it.type }, actual.cars.map { it.type })
        original.customThemes.forEachIndexed { i, entry ->
            assertEquals(entry.layout.staticObjects, restored.customThemes[i].layout.staticObjects)
            assertEquals(entry.layout.cars.map { it.color }, restored.customThemes[i].layout.cars.map { it.color })
        }
    }

    @Test
    fun `entry names survive the round trip`() {
        val restored = customThemeDataFromJsonString(sampleData().toJsonString())
        assertEquals("My Christmas", restored.overrides.getValue("christmas").name)
        assertEquals("Midnight Village", restored.customThemes[0].name)
        assertEquals("Quiet Coast", restored.customThemes[1].name)
    }

    @Test
    fun `customization survives the round trip`() {
        val restored = customThemeDataFromJsonString(sampleData().toJsonString())
        assertEquals(
            SceneCustomization.DEFAULT,
            restored.overrides.getValue("christmas").customization,
        )
    }

    @Test
    fun `round trip is stable when repeated`() {
        // Serialise -> parse -> serialise again. The second payload must parse to the same thing,
        // which catches an asymmetric writer/reader pair that loses a field only on re-save.
        val once = sampleData().toJsonString()
        val twice = customThemeDataFromJsonString(once).toJsonString()
        val a = customThemeDataFromJsonString(once)
        val b = customThemeDataFromJsonString(twice)

        assertEquals(a.overrides.keys, b.overrides.keys)
        assertEquals(a.customThemes.map { it.id }, b.customThemes.map { it.id })
        assertThemesMatch(a.overrides.getValue("christmas").theme, b.overrides.getValue("christmas").theme)
        assertLayoutsMatch(a.overrides.getValue("christmas").layout, b.overrides.getValue("christmas").layout)
    }

    @Test
    fun `built in catalog themes round trip`() {
        // Exercises the real shipped data rather than only hand-built fixtures.
        for (theme in ThemeCatalog.ALL) {
            val entry = CustomThemeEntry(
                id = theme.id,
                name = theme.displayName,
                theme = theme,
                layout = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor),
                customization = SceneCustomization.DEFAULT,
            )
            val data = CustomThemeData(overrides = mapOf(theme.id to entry))
            val restored = customThemeDataFromJsonString(data.toJsonString())
            val restoredEntry = restored.overrides.getValue(theme.id)
            assertThemesMatch(theme, restoredEntry.theme)
            assertLayoutsMatch(entry.layout, restoredEntry.layout)
        }
    }

    // --- Schema version -----------------------------------------------------------------------

    @Test
    fun `writer stamps the current schema version`() {
        val json = JSONObject(sampleData().toJsonString())
        assertEquals(CUSTOM_THEME_SCHEMA_VERSION, json.getInt("schemaVersion"))
    }

    @Test
    fun `reader reports the legacy version for data written before versioning existed`() {
        // Exactly the shape v73 and earlier wrote: no schemaVersion key at all.
        val legacy = JSONObject(sampleData().toJsonString()).apply { remove("schemaVersion") }.toString()
        assertFalse("fixture should not contain a version", legacy.contains("schemaVersion"))
        assertEquals(CUSTOM_THEME_SCHEMA_VERSION_LEGACY, readCustomThemeSchemaVersion(legacy))
    }

    @Test
    fun `legacy unversioned payloads still load completely`() {
        // The compatibility guarantee that matters: an existing user's saved themes must survive
        // the upgrade rather than being discarded. What the scene looks like afterwards is the
        // schema 1 -> 2 migration's business and is asserted separately below; everything that
        // identifies a theme has to come back exactly.
        val original = sampleData()
        val legacy = JSONObject(original.toJsonString()).apply { remove("schemaVersion") }.toString()
        val restored = customThemeDataFromJsonString(legacy)

        assertEquals(original.overrides.keys, restored.overrides.keys)
        assertEquals(original.customThemes.map { it.id }, restored.customThemes.map { it.id })
        assertThemesMatch(
            original.overrides.getValue("christmas").theme,
            restored.overrides.getValue("christmas").theme,
        )
        val expected = original.overrides.getValue("christmas").layout
        val actual = restored.overrides.getValue("christmas").layout
        assertEquals(expected.staticObjects.size, actual.staticObjects.size)
        assertEquals(expected.cars.size, actual.cars.size)
        expected.staticObjects.forEachIndexed { i, e ->
            val a = actual.staticObjects[i]
            assertEquals("staticObjects[$i].type", e.type, a.type)
            assertEquals("staticObjects[$i].depthFraction", e.depthFraction, a.depthFraction, 0.0001f)
            assertEquals("staticObjects[$i].tileFractionX", e.tileFractionX, a.tileFractionX, 0.0001f)
        }
        expected.cars.forEachIndexed { i, e ->
            assertEquals("cars[$i].color", e.color, actual.cars[i].color)
            assertEquals("cars[$i].type", e.type, actual.cars[i].type)
        }
    }

    @Test
    fun `schema 1 to 2 converts an absolute object scale into a size variation`() {
        // A house saved at 0.8 was 0.8 of nothing in particular -- the field carried the whole
        // category size, rolled around a base of 1.5. The same object is now 0.8 / 1.5 of the size
        // a house is supposed to be, which is what that number always meant.
        val legacy = JSONObject(sampleData().toJsonString()).apply { put("schemaVersion", 1) }.toString()
        val restored = customThemeDataFromJsonString(legacy).overrides.getValue("christmas").layout

        val house = restored.staticObjects.first { it.type == SceneObjectType.HOUSE }
        assertEquals(0.8f / SceneSpace.legacyBaseScaleFor(SceneObjectType.HOUSE), house.scale, 0.0001f)
        val tree = restored.staticObjects.first { it.type == SceneObjectType.TREE }
        assertEquals(1.15f / SceneSpace.legacyBaseScaleFor(SceneObjectType.TREE), tree.scale, 0.0001f)
        // The skyscraper was saved without a scale at all, so its default of 1 has to be read as
        // "the size that category was" rather than as "1 % of it".
        val tower = restored.staticObjects.first { it.type == SceneObjectType.SKYSCRAPER }
        assertEquals(1f / SceneSpace.legacyBaseScaleFor(SceneObjectType.SKYSCRAPER), tower.scale, 0.0001f)
    }

    @Test
    fun `a theme saved at schema 2 does not drag the road back over the pavement`() {
        // The regression this exists to stop. Schema 2 canonicalised lanes as a migration
        // step, so a theme written *by* version 2 is stamped 2 and no migration ever runs on
        // it again -- while the lanes moved twice more, in v76.6 and v76.7. The painted road
        // is derived from the layout's own lanes, so such a theme pulls the carriageway back
        // to where it was saved, over the ground the pedestrians walk on.
        val saved = sampleData().let { data ->
            val entry = data.overrides.getValue("christmas")
            // The v76.5 lane pair, verbatim.
            val cars = listOf(
                CarObject(laneYFraction = 0.820f, speedFraction = 0.062f, startDelaySeconds = 0f, color = 1),
                CarObject(laneYFraction = 0.855f, speedFraction = 0.075f, startDelaySeconds = 0f, color = 2),
            )
            data.copy(overrides = mapOf("christmas" to entry.copy(layout = entry.layout.copy(cars = cars))))
        }
        val stale = JSONObject(saved.toJsonString()).apply { put("schemaVersion", 2) }.toString()
        val cars = customThemeDataFromJsonString(stale).overrides.getValue("christmas").layout.cars

        cars.forEach { car ->
            assertTrue(
                "a saved lane survived the load: ${car.laneYFraction}",
                car.laneYFraction == SceneSpace.ROAD_LANE_FAR_Y_FRACTION ||
                    car.laneYFraction == SceneSpace.ROAD_LANE_NEAR_Y_FRACTION,
            )
        }
        // The road these cars produce has to leave the pedestrian band alone.
        val minLane = cars.minOf { it.laneYFraction }
        val maxLane = cars.maxOf { it.laneYFraction }
        val roadTop = minLane - SceneSpace.roadEdgeMarginFraction(minLane, maxLane)
        assertTrue(
            "the restored road covers the pavement",
            roadTop > SceneSpace.PAVEMENT_NEAR_Y_FRACTION,
        )
    }

    @Test
    fun `no stored lane coordinate is believed, at any schema version`() {
        // The general form, and the reason this is done on every load rather than in a
        // migration step. A migration catches the payloads written before it and nothing
        // after, so the next time a lane constant moves the same defect comes back.
        listOf(0, 1, 2, CUSTOM_THEME_SCHEMA_VERSION).forEach { version ->
            val saved = sampleData().let { data ->
                val entry = data.overrides.getValue("christmas")
                val cars = listOf(
                    CarObject(laneYFraction = 0.42f, speedFraction = 0.9f, startDelaySeconds = 3f, color = 7),
                    CarObject(laneYFraction = 0.99f, speedFraction = 0.01f, startDelaySeconds = 4f, color = 8),
                )
                data.copy(overrides = mapOf("christmas" to entry.copy(layout = entry.layout.copy(cars = cars))))
            }
            val payload = JSONObject(saved.toJsonString()).apply { put("schemaVersion", version) }.toString()
            val cars = customThemeDataFromJsonString(payload).overrides.getValue("christmas").layout.cars

            cars.forEach { car ->
                assertTrue(
                    "version $version kept a stale lane",
                    car.laneYFraction == SceneSpace.ROAD_LANE_FAR_Y_FRACTION ||
                        car.laneYFraction == SceneSpace.ROAD_LANE_NEAR_Y_FRACTION,
                )
                assertTrue(
                    "version $version kept a per-car speed",
                    car.speedFraction == SceneSpace.CAR_SPEED_FAR ||
                        car.speedFraction == SceneSpace.CAR_SPEED_NEAR,
                )
            }
            // Colour and type are the theme's own and must survive.
            assertEquals(listOf(7, 8), cars.map { it.color }.sorted())
        }
    }

    @Test
    fun `loading canonicalises saved cars onto the current lanes`() {
        // The sample's two cars sit at 0.82 and 0.86, either side of their own midpoint, so one
        // belongs to each lane. Both used to carry a per-car speed, which is what makes a lane
        // collapse into a pack, and both would now be driving above a road that has moved down.
        val legacy = JSONObject(sampleData().toJsonString()).apply { put("schemaVersion", 1) }.toString()
        val cars = customThemeDataFromJsonString(legacy).overrides.getValue("christmas").layout.cars

        val far = cars.first { it.laneYFraction == SceneSpace.ROAD_LANE_FAR_Y_FRACTION }
        val near = cars.first { it.laneYFraction == SceneSpace.ROAD_LANE_NEAR_Y_FRACTION }
        assertEquals(SceneSpace.CAR_SPEED_FAR, far.speedFraction, 0.0001f)
        assertEquals(SceneSpace.CAR_SPEED_NEAR, near.speedFraction, 0.0001f)
        // Direction follows the lane, so no two cars can meet head-on inside one of them.
        assertTrue("the far lane drives left", far.reverse)
        assertFalse("the near lane drives right", near.reverse)
        // Colour and vehicle type are the theme's own and must survive untouched.
        assertEquals(CarType.TAXI, cars.first { it.color == 0xFFCC3344.toInt() }.type)
    }

    @Test
    fun `schema 1 to 2 spaces a single-lane theme evenly instead of stacking it`() {
        // Every car at one lane fraction is the shape a pre-v76.2 theme has. Splitting it on the
        // midpoint would put all of them on one side, so they all go to the near lane -- and the
        // point of the migration is that they come out evenly spaced around the loop rather than
        // keeping the random start offsets that let them bunch.
        val stacked = sampleData().let { data ->
            val entry = data.overrides.getValue("christmas")
            val cars = List(4) { i ->
                CarObject(laneYFraction = 0.79f, speedFraction = 0.05f + i * 0.03f, startDelaySeconds = i * 0.9f, color = i)
            }
            data.copy(overrides = mapOf("christmas" to entry.copy(layout = entry.layout.copy(cars = cars))))
        }
        val legacy = JSONObject(stacked.toJsonString()).apply { put("schemaVersion", 1) }.toString()
        val cars = customThemeDataFromJsonString(legacy).overrides.getValue("christmas").layout.cars

        assertEquals(4, cars.size)
        // A degenerate spread is alternated across both lanes rather than stacked into one.
        // Stacking is what the version 2 migration did, and it left a theme with a single
        // file of traffic on one side of an otherwise empty road.
        assertEquals(2, cars.count { it.laneYFraction == SceneSpace.ROAD_LANE_NEAR_Y_FRACTION })
        assertEquals(2, cars.count { it.laneYFraction == SceneSpace.ROAD_LANE_FAR_Y_FRACTION })
        // One speed per lane: within a lane nothing overtakes, so the queue keeps its spacing.
        cars.groupBy { it.laneYFraction }.forEach { (_, lane) ->
            assertTrue("a lane carries two speeds", lane.all { it.speedFraction == lane[0].speedFraction })
            val offsets = lane.map { it.startDelaySeconds }.sorted()
            assertEquals("two cars share a start offset", offsets.size, offsets.distinct().size)
        }
    }

    @Test
    fun `legacy payload is stamped with the current version when re-saved`() {
        val legacy = JSONObject(sampleData().toJsonString()).apply { remove("schemaVersion") }.toString()
        val resaved = customThemeDataFromJsonString(legacy).toJsonString()
        assertEquals(CUSTOM_THEME_SCHEMA_VERSION, readCustomThemeSchemaVersion(resaved))
    }

    @Test
    fun `payload from a newer schema is read rather than discarded`() {
        // Downgrade scenario: an older APK installed over a newer one. Losing every saved theme
        // would be far worse than ignoring fields this build does not understand.
        val future = JSONObject(sampleData().toJsonString()).apply {
            put("schemaVersion", CUSTOM_THEME_SCHEMA_VERSION + 5)
            put("somethingThisBuildHasNeverHeardOf", true)
        }.toString()

        val restored = customThemeDataFromJsonString(future)
        assertEquals(1, restored.overrides.size)
        assertEquals(2, restored.customThemes.size)
        assertThemesMatch(
            sampleData().overrides.getValue("christmas").theme,
            restored.overrides.getValue("christmas").theme,
        )
    }

    @Test
    fun `version reader returns null for absent or unparseable payloads`() {
        assertNull(readCustomThemeSchemaVersion(null))
        assertNull(readCustomThemeSchemaVersion(""))
        assertNull(readCustomThemeSchemaVersion("   "))
        assertNull(readCustomThemeSchemaVersion("not json at all"))
        assertNull(readCustomThemeSchemaVersion("{ truncated"))
    }

    // --- Defensive parsing --------------------------------------------------------------------

    @Test
    fun `blank and null input yields empty data`() {
        assertEquals(CustomThemeData.EMPTY.overrides, customThemeDataFromJsonString(null).overrides)
        assertEquals(CustomThemeData.EMPTY.overrides, customThemeDataFromJsonString("").overrides)
        assertEquals(CustomThemeData.EMPTY.overrides, customThemeDataFromJsonString("   ").overrides)
    }

    @Test
    fun `corrupt input yields empty data instead of throwing`() {
        val corruptPayloads = listOf(
            "not json at all",
            "{",
            "[]",
            """{"overrides": "this should be an object"}""",
            """{"customThemes": {"this": "should be an array"}}""",
            """{"overrides": {"christmas": {"id": "christmas"}}}""", // entry missing required keys
        )
        for (payload in corruptPayloads) {
            val restored = customThemeDataFromJsonString(payload)
            assertTrue(
                "payload should have degraded to empty: $payload",
                restored.overrides.isEmpty() && restored.customThemes.isEmpty(),
            )
        }
    }

    @Test
    fun `missing optional sections default to empty rather than failing`() {
        assertEquals(0, customThemeDataFromJsonString("""{"schemaVersion":1}""").overrides.size)
        assertEquals(0, customThemeDataFromJsonString("""{"schemaVersion":1}""").customThemes.size)
        assertEquals(
            1,
            customThemeDataFromJsonString(
                JSONObject(sampleData().toJsonString()).apply { remove("customThemes") }.toString(),
            ).overrides.size,
        )
    }

    @Test
    fun `entry missing its customization falls back to defaults`() {
        // customization was added after the first release of this format, so payloads without it
        // still exist in the wild.
        val root = JSONObject(sampleData().toJsonString())
        root.getJSONObject("overrides").getJSONObject("christmas").remove("customization")
        val restored = customThemeDataFromJsonString(root.toString())
        assertEquals(
            SceneCustomization.DEFAULT,
            restored.overrides.getValue("christmas").customization,
        )
    }
}
