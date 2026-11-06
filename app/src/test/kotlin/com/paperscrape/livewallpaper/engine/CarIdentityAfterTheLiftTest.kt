package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Lifting the road moves where the cars drive, never which car is which** (`DESIGN_NOTES.md` §12, decision 96): a car's
 * colour coin and the frozen selection an older save path filtered with hash its lane's height, and they read it as the
 * lanes stood before the lift, 0.862 and 0.834 ([SceneObjectCatalog.identityLaneOf]). Every car of every built-in and
 * random street wears the colour it wore, computed here from the old heights by the coin's own rule.
 */
class CarIdentityAfterTheLiftTest {

    /** The colour coin as it was computed on the old lanes: Color 1 or Color 2. */
    private fun oldCoin(car: CarObject): Int {
        val near = car.laneYFraction == SceneSpace.ROAD_LANE_NEAR_Y_FRACTION
        val lane = if (near) 0.862f else 0.834f
        return if (CandidateNoise.value(lane.toRawBits(), car.startDelaySeconds.toRawBits(), 118) < 0.5f) 0 else 1
    }

    @Test
    fun `every car wears the colour it wore before the road was lifted`() {
        val base = SceneCustomization.DEFAULT
        val c = base.copy(cars = base.cars.copy(colorDay1 = 0xFF111111.toInt(), colorDay2 = 0xFF222222.toInt()))
        var counted = 0
        val layouts = ThemeCatalog.ALL.map { SceneObjectCatalog.layoutFor(it.id, it.accentColor) } +
            (1..50).mapNotNull { runCatching { SceneObjectCatalog.layoutFor("random:$it", 0) }.getOrNull() }
        for (layout in layouts) for (car in layout.cars) {
            assertTrue(car.laneYFraction == SceneSpace.ROAD_LANE_NEAR_Y_FRACTION || car.laneYFraction == SceneSpace.ROAD_LANE_FAR_Y_FRACTION)
            val expected = if (oldCoin(car) == 0) 0xFF111111.toInt() else 0xFF222222.toInt()
            assertEquals("a car at ${car.laneYFraction}, ${car.startDelaySeconds}", expected, c.colorFor(car, 1f))
            counted++
        }
        assertTrue(counted > 100)
    }

    @Test
    fun `a car's lane as its identity reads it is the old lane's height`() {
        val near = CarObject(laneYFraction = SceneSpace.ROAD_LANE_NEAR_Y_FRACTION, speedFraction = 0.05f, startDelaySeconds = 0.3f, color = 0)
        val far = near.copy(laneYFraction = SceneSpace.ROAD_LANE_FAR_Y_FRACTION)
        assertEquals(0.862f, SceneObjectCatalog.identityLaneOf(near), 0f)
        assertEquals(0.834f, SceneObjectCatalog.identityLaneOf(far), 0f)
    }
}
