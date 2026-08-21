package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two rules that decide whether two things on the lake can look wrong together: they must not
 * be placed on the same line, and whichever is nearer must be painted last.
 */
class LakeLanesTest {

    private val pool = PaperRenderer.LAKE_DECORATION_POOL_SIZE

    @Test
    fun `every candidate of every category gets a lane of its own`() {
        val lanes = mutableListOf<Int>()
        for (i in 0 until pool) {
            lanes += LakeLanes.laneIndex(i, isDolphin = false)
            lanes += LakeLanes.laneIndex(i, isDolphin = true)
        }
        assertEquals(
            "two things sharing a lane travel the same line at different speeds, which is the " +
                "defect this replaced",
            lanes.size,
            lanes.toSet().size,
        )
    }

    @Test
    fun `no boat ever shares a lane with another boat`() {
        val boatLanes = (0 until pool).map { LakeLanes.laneIndex(it, isDolphin = false) }
        assertEquals(boatLanes.size, boatLanes.toSet().size)
    }

    @Test
    fun `boats and dolphins never share a lane`() {
        val boats = (0 until pool).map { LakeLanes.laneIndex(it, isDolphin = false) }.toSet()
        val dolphins = (0 until pool).map { LakeLanes.laneIndex(it, isDolphin = true) }.toSet()
        assertTrue("categories must interleave, not collide", boats.intersect(dolphins).isEmpty())
    }

    @Test
    fun `the lane count is exactly what the two pools need, so nothing folds`() {
        val used = (0 until pool).flatMap {
            listOf(LakeLanes.laneIndex(it, false), LakeLanes.laneIndex(it, true))
        }
        assertEquals("no lane may be left unused", LakeLanes.LANE_COUNT, used.toSet().size)
        assertTrue(
            "no candidate may be placed outside the band",
            used.all { it in 0 until LakeLanes.LANE_COUNT },
        )
    }

    @Test
    fun `both categories reach the near edge and the far edge of the water`() {
        val boats = (0 until pool).map { LakeLanes.laneIndex(it, false) }
        val dolphins = (0 until pool).map { LakeLanes.laneIndex(it, true) }
        val last = LakeLanes.LANE_COUNT - 1
        assertTrue("boats must reach the far edge", boats.min() <= 1)
        assertTrue("boats must reach the near edge", boats.max() >= last - 1)
        assertTrue("dolphins must reach the far edge", dolphins.min() <= 1)
        assertTrue("dolphins must reach the near edge", dolphins.max() >= last - 1)
    }

    @Test
    fun `the draw order runs from the far edge to the near one`() {
        val depths = floatArrayOf(900f, 100f, 500f, 300f)
        val order = IntArray(4)
        LakeLanes.orderByDepth(depths, 4, order)
        assertEquals(listOf(1, 3, 2, 0), order.toList())
    }

    @Test
    fun `a nearer boat is always painted after a farther one`() {
        val depths = floatArrayOf(410f, 402f, 700f, 120f, 405f)
        val order = IntArray(depths.size)
        LakeLanes.orderByDepth(depths, depths.size, order)
        val painted = order.map { depths[it] }
        assertEquals("depths must come out ascending", painted.sorted(), painted)
    }

    @Test
    fun `equal depths keep a fixed order rather than flickering between frames`() {
        val depths = floatArrayOf(300f, 300f, 300f)
        val first = IntArray(3).also { LakeLanes.orderByDepth(depths, 3, it) }
        val second = IntArray(3).also { LakeLanes.orderByDepth(depths, 3, it) }
        assertEquals(listOf(0, 1, 2), first.toList())
        assertEquals(first.toList(), second.toList())
    }

    @Test
    fun `ordering ignores slots past the count, so a short frame reads no stale data`() {
        val depths = floatArrayOf(50f, 10f, -999f, -999f)
        val order = IntArray(4) { -1 }
        LakeLanes.orderByDepth(depths, 2, order)
        assertEquals(listOf(1, 0), order.take(2))
        assertTrue("slots beyond the count must not be touched", order.drop(2).all { it == -1 })
    }

    @Test
    fun `an empty lake orders nothing`() {
        val order = IntArray(4) { -1 }
        LakeLanes.orderByDepth(FloatArray(4), 0, order)
        assertTrue(order.all { it == -1 })
    }
}
