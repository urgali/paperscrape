package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The distributed car count that replaced the per-candidate threshold (v4.22).
 *
 * Three claims are pinned, each with its derivation in the test that asserts it:
 * the endpoints (0 -> one car, 1 -> every slot), the two selection properties (maximum spacing
 * at every count, nesting between counts), and the seed rule (lane and slot of the sporadic car
 * vary by theme, never by anything derived from a candidate's own ten-valued identity).
 */
class CarSelectionTest {

    /** Seeds worth exercising: every shipped theme's real seed, the test default, and edges. */
    private val seeds =
        ThemeCatalog.ALL.map { it.id.hashCode() } + listOf("".hashCode(), 0, 1, -1, Int.MIN_VALUE, Int.MAX_VALUE)

    private fun cars(themeId: String = "sunset") =
        SceneObjectCatalog.layoutFor(themeId, accentColor = 0xFF335577.toInt()).cars

    private fun config(density: Float, visible: Boolean = true) =
        SceneCustomization.DEFAULT.let { it.copy(cars = it.cars.copy(visible = visible, density = density)) }

    // ---------------------------------------------------------------- the endpoints

    @Test
    fun `density zero keeps exactly one car and density one keeps every slot`() {
        for (available in 1..10) {
            assertEquals("0f on $available cars", 1, CarSelection.countFor(0f, available))
            assertEquals("1f on $available cars", available, CarSelection.countFor(1f, available))
        }
        assertEquals("an empty inventory has nothing to keep", 0, CarSelection.countFor(0.5f, 0))
    }

    /**
     * The linear curve, written out: `1 + round(d * 9)` on the full inventory. The table is the
     * formula evaluated by hand, so a curve change fails here with the numbers side by side.
     */
    @Test
    fun `the count is the linear formula on the full inventory`() {
        val expected = mapOf(0f to 1, 0.1f to 2, 0.35f to 4, 0.5f to 6, 0.65f to 7, 1f to 10)
        for ((density, count) in expected) {
            assertEquals("at $density", count, CarSelection.countFor(density, 10))
        }
    }

    @Test
    fun `the count never decreases as the slider rises`() {
        for (available in 1..10) {
            var previous = 0
            for (step in 0..100) {
                val count = CarSelection.countFor(step / 100f, available)
                assertTrue("count fell from $previous at step $step", count >= previous)
                assertTrue(count in 1..available)
                previous = count
            }
        }
    }

    // ---------------------------------------------------------------- the two properties

    /**
     * **Nesting.** The kept set at any density contains the kept set at any lower density, so
     * dragging the slider up adds cars and never reshuffles the road. Asserted on the mask
     * itself, across every seed and a full sweep of the slider.
     */
    @Test
    fun `a higher density keeps a superset of a lower one`() {
        val inventory = cars()
        for (seed in seeds) {
            var previous = BooleanArray(inventory.size)
            for (step in 0..100) {
                val mask = CarSelection.keptMask(inventory, step / 100f, seed)
                for (i in inventory.indices) {
                    assertFalse(
                        "seed $seed step $step dropped car $i that a lower density kept",
                        previous[i] && !mask[i],
                    )
                }
                previous = mask
            }
        }
    }

    /**
     * **Maximum spacing.** At every count, each lane's chosen slots have the largest minimum
     * circular gap that many slots can have on a five-slot loop.
     *
     * Derivation of the ceiling: k slots on a 5-cycle split it into k gaps summing to 5, so the
     * smallest gap is at most `5 / k` rounded down — 2 for two slots, 1 for three or four, and
     * the full lane leaves gaps of exactly 1. One slot has no gap to measure.
     */
    @Test
    fun `every prefix of the selection order spaces each lane as far apart as five slots allow`() {
        val bestMinGap = intArrayOf(0, 0, 2, 1, 1, 1) // index = slots in the lane
        for (seed in seeds) {
            val order = CarSelection.selectionOrder(seed)
            assertEquals("a permutation of the ten candidate indices", (0..9).toList(), order.sorted())
            for (n in 1..order.size) {
                val prefix = order.take(n)
                for (nearBit in 0..1) {
                    val slots = prefix.filter { it % 2 == nearBit }.map { it / 2 }.sorted()
                    if (slots.size < 2) continue
                    val gaps = slots.indices.map { i ->
                        val next = slots[(i + 1) % slots.size]
                        Math.floorMod(next - slots[i], 5)
                    }
                    assertEquals(
                        "seed $seed, count $n, lane bit $nearBit: slots $slots",
                        bestMinGap[slots.size],
                        gaps.min(),
                    )
                }
            }
        }
    }

    /** Lanes fill alternately, so their counts never differ by more than one car. */
    @Test
    fun `no count leaves the lanes more than one car apart`() {
        for (seed in seeds) {
            val order = CarSelection.selectionOrder(seed)
            for (n in 1..order.size) {
                val near = order.take(n).count { it % 2 == 0 }
                assertTrue("seed $seed count $n: $near near of $n", Math.abs(2 * near - n) <= 1)
            }
        }
    }

    // ---------------------------------------------------------------- the seed rule

    /**
     * The sporadic car's lane and slot come from the theme, not from a constant: across the
     * shipped themes both lanes occur, and more than one queue slot does. One theme, meanwhile,
     * always shows the same car — determinism is asserted separately below.
     *
     * This is the guard against the fixed-hand trap: a constant first pick would empty the same
     * lane in every theme the app ships, which is exactly the shape of defect the ten-valued
     * candidate identity produced twice before (`BACKLOG_v4_20.md`).
     */
    @Test
    fun `different shipped themes put the single car in different lanes and slots`() {
        val firstPicks = ThemeCatalog.ALL.map { theme ->
            CarSelection.selectionOrder(theme.id.hashCode()).first()
        }
        assertTrue("every shipped theme starts in the near lane", firstPicks.any { it % 2 == 1 })
        assertTrue("every shipped theme starts in the far lane", firstPicks.any { it % 2 == 0 })
        assertTrue(
            "every shipped theme starts on the same queue slot",
            firstPicks.map { it / 2 }.distinct().size > 1,
        )
    }

    @Test
    fun `the selection is deterministic per seed`() {
        for (seed in seeds) {
            assertEquals(
                CarSelection.selectionOrder(seed).toList(),
                CarSelection.selectionOrder(seed).toList(),
            )
            assertEquals(
                CarSelection.keptMask(cars(), 0.35f, seed).toList(),
                CarSelection.keptMask(cars(), 0.35f, seed).toList(),
            )
        }
    }

    // ---------------------------------------------------------------- inventories and the config

    /** A custom theme saved by an older build can carry fewer than ten cars; the endpoints and
     * the nesting mean the same thing on its own inventory. */
    @Test
    fun `a partial inventory keeps one car at zero and all of itself at one`() {
        val partial = SceneObjectCatalog.canonicaliseTraffic(cars().take(6))
        for (seed in seeds) {
            assertEquals(1, CarSelection.keptMask(partial, 0f, seed).count { it })
            assertEquals(6, CarSelection.keptMask(partial, 1f, seed).count { it })
            var previous = BooleanArray(partial.size)
            for (step in 0..20) {
                val mask = CarSelection.keptMask(partial, step / 20f, seed)
                for (i in partial.indices) {
                    assertFalse("nesting broke on the partial inventory", previous[i] && !mask[i])
                }
                previous = mask
            }
        }
    }

    @Test
    fun `keptCars honours the visibility switch and inventory order`() {
        val inventory = cars()
        assertTrue(config(1f, visible = false).keptCars(inventory, 0).isEmpty())
        val kept = config(1f).keptCars(inventory, 0)
        assertEquals("full density keeps the whole inventory, in inventory order", inventory, kept)
        assertEquals(1, config(0f).keptCars(inventory, 0).size)
    }

    // ---------------------------------------------------------------- the off-screen rule

    /** The bounds are the draw cull's own: a car at exactly -0.05 or 1.05 is still drawn, so its
     * membership must not change there. */
    @Test
    fun `membership may change only outside the drawn span of the loop`() {
        assertFalse(CarSelection.offScreen(-0.05f))
        assertFalse(CarSelection.offScreen(0f))
        assertFalse(CarSelection.offScreen(1.05f))
        assertTrue(CarSelection.offScreen(-0.051f))
        assertTrue(CarSelection.offScreen(1.051f))
        assertTrue(CarSelection.offScreen(-0.3f))
        assertTrue(CarSelection.offScreen(1.3f))
    }
}
