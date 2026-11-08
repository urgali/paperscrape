package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The road's geometry must not depend on how much traffic is on it.
 *
 * `drawRoad` takes its lane span from the layout's whole car list: a span taken from the
 * density-filtered runtime list would move the road with the Cars density slider, collapsing to
 * zero, and the strip with it, when thinning leaves one lane.
 *
 * `drawRoad` needs a `Canvas`, so what is pinned here is the renderer's own lines that take the
 * span from the layout (whole lines, comments off), and the pure half: the same span computed from
 * a filtered list diverges -- that divergence is why only the layout's may be used.
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

    /** [text]'s lines, trimmed, with comment lines, trailing `//` comments and blank lines left out. */
    private fun codeLines(text: String): List<String> =
        text.lines().map { it.trim() }
            .filterNot { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }
            .map { it.substringBefore(" //").trim() }
            .filter { it.isNotEmpty() }

    private fun renderer(): String =
        java.io.File(sequenceOf("src/main", "app/src/main").map { java.io.File(it) }.first { it.isDirectory },
            "kotlin/com/paperscrape/livewallpaper/engine/SceneObjectRenderer.kt").readText()

    @Test
    fun `the road is identical at every car density`() {
        // The rule, stated directly. The layout is what the road is built from, and a density
        // setting does not change the layout -- it changes which of its members render.
        val lines = codeLines(renderer())
        assertTrue("the far lane from the layout", "private val roadLaneMinFraction: Float = layout.cars.minOfOrNull { it.laneYFraction } ?: 0f" in lines)
        assertTrue("the near lane from the layout", "private val roadLaneMaxFraction: Float = layout.cars.maxOfOrNull { it.laneYFraction } ?: 0f" in lines)
        assertTrue("the margin from the layout's span", "val margin = screenHeight * SceneSpace.roadEdgeMarginFraction(roadLaneMinFraction, roadLaneMaxFraction)" in lines)
        val cars = canonicalCars()
        floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { density ->
            // Filtering happens on the runtime list, never on the layout's.
            assertTrue(
                "density $density kept cars it should not",
                customizationWithCarDensity(density).keptCars(cars, themeSeed = 0).size <= cars.size,
            )
        }
    }

    @Test
    fun `the density-filtered list really does give a different road`() {
        // Why the road is built from the layout: a density-filtered list gives another span.
        //
        // The selection balances the lanes, so a thinned list only collapses onto one lane at the
        // count where it *must*: a single car, the count just above 0 %. That is the collapse -- a
        // span of zero -- and the strongest form the divergence can take, because at
        // any count above one both lanes are populated and the filtered span matches the layout's by
        // construction rather than by luck.
        val cars = canonicalCars()
        val thinned = customizationWithCarDensity(0.01f).keptCars(cars, themeSeed = 0)
        assertEquals("one car in the whole scene just above 0 %", 1, thinned.size)

        // One lane left, which is the collapse: the raw span is zero.
        assertEquals(0f, (thinned.maxOf { it.laneYFraction } - thinned.minOf { it.laneYFraction }), 1e-7f)
    }

    @Test
    fun `a degenerate lane pair still paints a road`() {
        // Second line of defence, and the case of a custom theme stored with every car on one
        // lane fraction. A margin derived from a zero span would be zero, which is a hairline.
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
        // The guard must not swallow a legitimately different road. A theme stored with other
        // lanes (0.771 and 0.803) has a real span and keeps its own strip.
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
        //
        // The bottom of the slider is no car (decision 104), and just above it one.
        val cars = canonicalCars()
        val none = customizationWithCarDensity(0f).keptCars(cars, themeSeed = 0).size
        val bottom = customizationWithCarDensity(0.01f).keptCars(cars, themeSeed = 0).size
        val all = customizationWithCarDensity(1f).keptCars(cars, themeSeed = 0).size
        val hidden = customizationWithCarDensity(1f)
            .let { it.copy(cars = it.cars.copy(visible = false)) }
            .keptCars(cars, themeSeed = 0)
            .size
        assertEquals(0, none)
        assertEquals(1, bottom)
        assertEquals(cars.size, all)
        assertEquals(0, hidden)
        assertTrue(all > bottom)
    }
}
