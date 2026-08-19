package com.paperscrape.livewallpaper.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A saved theme may not carry scene geometry.
 *
 * Blocker B9. The traffic lanes moved three times while the custom theme schema stayed put, and a
 * schema version cannot catch that: it records a change of *shape*, and a stale copy of a constant
 * parses perfectly. The defence is not a version number but a rule -- **anything a theme persists
 * that is really a `SceneSpace` constant is recomputed on load, never believed** -- and this file
 * is what stops the rule eroding.
 */
class PersistedThemeGeometryTest {

    private fun layoutWith(cars: List<CarObject>) = SceneObjectLayout(
        staticObjects = listOf(
            StaticSceneObject(SceneObjectType.HOUSE, depthFraction = 0.4f, tileFractionX = 0.2f, scale = 1f),
        ),
        cars = cars,
    )

    @Test
    fun `a persisted car carries no geometry that survives a load`() {
        // Every geometric field is overwritten from the current constants, whatever was stored.
        val absurd = listOf(
            CarObject(laneYFraction = 0.11f, speedFraction = 5f, startDelaySeconds = 99f, color = 1, reverse = true),
            CarObject(laneYFraction = 0.97f, speedFraction = 0.001f, startDelaySeconds = -4f, color = 2, reverse = true),
        )
        val restored = SceneObjectCatalog.canonicaliseTraffic(absurd)

        restored.forEach { car ->
            assertTrue(
                "a stored lane survived",
                car.laneYFraction == SceneSpace.ROAD_LANE_FAR_Y_FRACTION ||
                    car.laneYFraction == SceneSpace.ROAD_LANE_NEAR_Y_FRACTION,
            )
            assertTrue(
                "a stored speed survived",
                car.speedFraction == SceneSpace.CAR_SPEED_FAR || car.speedFraction == SceneSpace.CAR_SPEED_NEAR,
            )
            assertTrue("a stored start offset survived", car.startDelaySeconds >= 0f)
        }
        // Direction is geometry too: it follows the lane, so no two cars meet head-on in one.
        restored.forEach { car ->
            assertEquals(car.laneYFraction == SceneSpace.ROAD_LANE_FAR_Y_FRACTION, car.reverse)
        }
    }

    @Test
    fun `what a theme owns survives untouched`() {
        // The other half of the rule. Recomputing geometry must not cost the user their theme:
        // how many cars there are, what colour they are and what type stay exactly as saved.
        val saved = listOf(
            CarObject(laneYFraction = 0.5f, speedFraction = 0.5f, startDelaySeconds = 0f, color = 0xFF112233.toInt(), type = CarType.TAXI),
            CarObject(laneYFraction = 0.5f, speedFraction = 0.5f, startDelaySeconds = 0f, color = 0xFF445566.toInt(), type = CarType.FIRE_TRUCK),
            CarObject(laneYFraction = 0.5f, speedFraction = 0.5f, startDelaySeconds = 0f, color = 0xFF778899.toInt(), type = CarType.POLICE),
        )
        val restored = SceneObjectCatalog.canonicaliseTraffic(saved)

        assertEquals(saved.size, restored.size)
        assertEquals(saved.map { it.color }, restored.map { it.color })
        assertEquals(saved.map { it.type }, restored.map { it.type })
    }

    @Test
    fun `the round trip cannot reintroduce a stale lane`() {
        // End to end, through the JSON the app actually writes and reads.
        val data = CustomThemeData(
            overrides = emptyMap(),
            customThemes = listOf(
                CustomThemeEntry(
                    id = "t",
                    name = "T",
                    theme = ThemeCatalog.byId("sunset"),
                    layout = layoutWith(
                        listOf(
                            CarObject(laneYFraction = 0.820f, speedFraction = 0.062f, startDelaySeconds = 0f, color = 1),
                            CarObject(laneYFraction = 0.855f, speedFraction = 0.075f, startDelaySeconds = 0f, color = 2),
                        ),
                    ),
                ),
            ),
        )
        val json = JSONObject(data.toJsonString()).apply {
            put("schemaVersion", CUSTOM_THEME_SCHEMA_VERSION)
        }.toString()
        val cars = customThemeDataFromJsonString(json).customThemes.single().layout.cars

        cars.forEach { car ->
            assertTrue(
                "a v76.5 lane came back through a current-version payload",
                car.laneYFraction == SceneSpace.ROAD_LANE_FAR_Y_FRACTION ||
                    car.laneYFraction == SceneSpace.ROAD_LANE_NEAR_Y_FRACTION,
            )
        }
    }

    @Test
    fun `the only geometry a layout persists is placement, which is the user's own`() {
        // Static objects legitimately store depth and horizontal position -- that is *where the
        // user put the house*, not a constant. What they must not store is how large it is: that
        // comes from the size table, and `scale` is a variation around 1. This pins the boundary
        // so a future field cannot quietly cross it.
        val obj = StaticSceneObject(SceneObjectType.HOUSE, depthFraction = 0.4f, tileFractionX = 0.2f, scale = 1.05f)
        val restored = staticSceneObjectFromJson(JSONObject(obj.toJson().toString()))

        assertEquals(obj.depthFraction, restored.depthFraction, 0.0001f)
        assertEquals(obj.tileFractionX, restored.tileFractionX, 0.0001f)
        assertEquals(obj.scale, restored.scale, 0.0001f)
        assertTrue(
            "scale must stay a variation around 1, not a size",
            restored.scale > SceneSpace.MIN_SIZE_VARIATION && restored.scale < 2f,
        )
    }
}
