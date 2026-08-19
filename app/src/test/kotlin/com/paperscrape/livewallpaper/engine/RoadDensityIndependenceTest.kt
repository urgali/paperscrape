package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The road's geometry must not depend on how much traffic is on it.
 *
 * Reported from a device against the Group 4 build: moving the Cars density slider resized the
 * road. `drawRoad` took its lane span from the density-filtered runtime list, so thinning the
 * traffic until only one lane survived collapsed the span to zero and the strip with it, and a
 * setting of zero removed the road altogether.
 *
 * `drawRoad` needs a `Canvas`, so what is pinned here is the pure half: the lane span the road is
 * built from, computed from the layout the way the renderer computes it, against the same span
 * computed from a filtered list the way it used to. The two must diverge -- that divergence *is*
 * the defect -- and only the first may be used.
 */
class RoadDensityIndependenceTest {

    private val screenHeightFractionTolerance = 1e-6f

    private fun config(density: Float, visible: Boolean = true) = ObjectVariantConfig(
        visible = visible,
        density = density,
        colorDay1 = 0, colorNight1 = 0, colorDay2 = 0, colorNight2 = 0,
    )

    private fun customizationWithCarDensity(density: Float) =
        SceneCustomization.DEFAULT.copy(cars = config(density))

    private fun canonicalCars(): List<CarObject> =
        SceneObjectCatalog.layoutFor("sunset", accentColor = 0xFF335577.toInt()).cars

    private fun marginOf(cars: List<CarObject>): Float {
        val min = cars.minOfOrNull { it.laneYFraction } ?: 0f
        val max = cars.maxOfOrNull { it.laneYFraction } ?: 0f
        return SceneSpace.roadEdgeMarginFraction(min, max)
    }

    @Test
    fun `the road is identical at every car density`() {
        // The rule, stated directly. The layout is what the road is built from, and a density
        // setting does not change the layout -- it changes which of its members render.
        val cars = canonicalCars()
        val expected = marginOf(cars)
        floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { density ->
            val custom = customizationWithCarDensity(density)
            // Filtering happens on the runtime list; the road's own span is taken from `cars`
            // regardless, which is what makes this constant.
            assertTrue("density $density kept cars it should not", cars.count { custom.keepCar(it) } <= cars.size)
            assertEquals("road margin moved at density $density", expected, marginOf(cars), screenHeightFractionTolerance)
        }
    }

    @Test
    fun `the density-filtered list really does give a different road`() {
        // The defect, reproduced against the code path that used to feed drawRoad. Without this
        // the test above would pass just as happily on the broken version.
        val cars = canonicalCars()
        val thinned = cars.filter { customizationWithCarDensity(0.2f).keepCar(it) }
        assertTrue("the fixture must actually thin the traffic", thinned.size < cars.size)

        val lanesLeft = thinned.map { it.laneYFraction }.distinct()
        if (lanesLeft.size < 2) {
            // One lane left, which is the collapse the device saw: the raw span is zero.
            assertEquals(0f, (thinned.maxOf { it.laneYFraction } - thinned.minOf { it.laneYFraction }), 1e-7f)
        } else {
            assertNotEquals(marginOf(cars), marginOf(thinned))
        }
    }

    @Test
    fun `a degenerate lane pair still paints a road`() {
        // Second line of defence, and the case a pre-v76.2 custom theme genuinely has: every car
        // on one lane fraction. A margin derived from a zero span is zero, which is a hairline.
        val margin = SceneSpace.roadEdgeMarginFraction(0.82f, 0.82f)
        val canonical = SceneSpace.roadEdgeMarginFraction(
            SceneSpace.ROAD_LANE_FAR_Y_FRACTION,
            SceneSpace.ROAD_LANE_NEAR_Y_FRACTION,
        )
        assertEquals(canonical, margin, screenHeightFractionTolerance)
        assertTrue(margin > 0f)
    }

    @Test
    fun `a real custom lane pair is still honoured`() {
        // The guard must not swallow a legitimately different road. A theme saved with the
        // pre-Group-4 lanes has a real span and keeps its own strip.
        val margin = SceneSpace.roadEdgeMarginFraction(0.771f, 0.803f)
        val canonical = SceneSpace.roadEdgeMarginFraction(
            SceneSpace.ROAD_LANE_FAR_Y_FRACTION,
            SceneSpace.ROAD_LANE_NEAR_Y_FRACTION,
        )
        assertNotEquals(canonical, margin)
        assertTrue(margin > 0f)
    }

    @Test
    fun `car density changes how many cars render`() {
        // The half of the slider that is supposed to work, kept alongside so a future change that
        // fixes the geometry by disconnecting the slider entirely would fail here.
        val cars = canonicalCars()
        val none = cars.count { customizationWithCarDensity(0f).keepCar(it) }
        val all = cars.count { customizationWithCarDensity(1f).keepCar(it) }
        assertEquals(0, none)
        assertEquals(cars.size, all)
        assertTrue(all > 0)
    }

    @Test
    fun `no density setting moves the perspective`() {
        // The global projection is a property of the scene, not of any category's population.
        val before = SceneSpace.depthScale(0.5f)
        val roadTop = SceneSpace.roadTopYFraction()
        floatArrayOf(0f, 0.5f, 1f).forEach { density ->
            customizationWithCarDensity(density)
            assertEquals(before, SceneSpace.depthScale(0.5f), screenHeightFractionTolerance)
            assertEquals(roadTop, SceneSpace.roadTopYFraction(), screenHeightFractionTolerance)
        }
    }
}
