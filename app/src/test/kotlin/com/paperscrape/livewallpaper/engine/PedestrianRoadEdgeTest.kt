package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REN-08: nobody walks on the road.
 *
 * The near pavement row is 0.807 of screen height and members jitter by up to +-0.012, so a figure
 * could stand at 0.819 while the road's own painted top edge is 0.8178 -- about three pixels of
 * foot on the tarmac at 2340 px, on the nearest row, where the figures are drawn largest and it
 * shows most. The jitter was clamped to the band the *rows* define, which knows nothing about where
 * the road is.
 */
class PedestrianRoadEdgeTest {

    // The rows the renderer passes in, read from the same place it reads them.
    private val NEAR_ROW = SceneSpace.PAVEMENT_NEAR_Y_FRACTION
    private val FAR_ROW = SceneSpace.PAVEMENT_FAR_Y_FRACTION
    private val SPREAD = 0.012f

    @Test
    fun `the near row plus its full jitter would have crossed the road edge`() {
        // The finding, as the arithmetic that found it. This is the condition the clamp exists for;
        // if the rows or the road move so that it stops holding, the clamp is no longer load-bearing
        // and this test says so by failing.
        val worst = NEAR_ROW + SPREAD
        assertTrue(
            "the unclamped worst case $worst should reach past the road top " +
                "${SceneSpace.roadTopYFraction()}",
            worst > SceneSpace.roadTopYFraction(),
        )
    }

    @Test
    fun `no pedestrian in any seeded population stands past the road edge`() {
        val road = SceneSpace.roadTopYFraction()
        for (seed in listOf(0, 1, 42, 7919, -1, Int.MAX_VALUE)) {
            val people = PedestrianPopulation.build(
                seed = seed,
                density = 1f,
                nearRowYFraction = NEAR_ROW,
                farRowYFraction = FAR_ROW,
            )
            assertTrue("seed $seed produced nobody to check", people.isNotEmpty())
            for (p in people) {
                assertTrue(
                    "seed $seed put a pedestrian at ${p.rowYFraction}, past the road top $road",
                    p.rowYFraction <= road + 1e-6f,
                )
            }
        }
    }
}
