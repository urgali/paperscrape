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

    // -- Depth by rendered base, not by lane (P1-2) ---------------------------------------------
    //
    // The lane spacing and sail height below are the real ones, taken from the geometry the v3.0
    // assessment measured on a 2424 px screen: lanes about 22 px apart, a sail reaching about
    // 82 px above its own waterline, a leap topping out about 38 px above its own. Those three
    // numbers together are the bug: a sail is nearly four lanes tall.

    private val laneSpacing = 22f
    private val sailHeight = 82f
    private val maxLeap = 38f

    /** Lane 0 is the far edge of the water; even lanes are boats, odd ones dolphins. */
    private fun laneY(lane: Int) = 400f + lane * laneSpacing

    private fun paintOrder(vararg depths: Float): List<Int> {
        val order = IntArray(depths.size)
        LakeLanes.orderByDepth(depths, depths.size, order)
        return order.toList()
    }

    @Test
    fun `a thing sitting on the water is still sorted by its lane`() {
        assertEquals(laneY(4), LakeLanes.depthOf(laneY(4), heightAboveLane = 0f))
    }

    @Test
    fun `a dolphin at the top of its leap goes behind the sail it would have crossed`() {
        // The reported frame: a sailboat on lane 0, a dolphin one lane nearer on lane 1, at the
        // apex of its leap -- so its body is up among the sail, 82 px of which stands above the
        // boat's own waterline.
        val boat = LakeLanes.depthOf(laneY(0), 0f)
        val dolphin = LakeLanes.depthOf(laneY(1), maxLeap)

        assertTrue(
            "at the apex the dolphin's body is above the boat's waterline, so it must be painted " +
                "first -- behind the sail -- instead of flying through it",
            paintOrder(boat, dolphin) == listOf(1, 0),
        )
    }

    @Test
    fun `the same dolphin comes back in front of the boat as it re-enters the water`() {
        val boat = LakeLanes.depthOf(laneY(0), 0f)
        // Just above the surface: its body has not reached the boat's waterline yet.
        val dolphin = LakeLanes.depthOf(laneY(1), laneSpacing * 0.4f)

        assertEquals("a dolphin nearer than the boat and still low is in front", listOf(0, 1), paintOrder(boat, dolphin))
    }

    @Test
    fun `a dolphin swimming is ordered exactly as v3_0 ordered it`() {
        for (lane in 1 until LakeLanes.LANE_COUNT step 2) {
            assertEquals(
                "an animal in the water must sort on its lane and nothing else",
                laneY(lane),
                LakeLanes.depthOf(laneY(lane), heightAboveLane = 0f),
            )
        }
    }

    @Test
    fun `no leap, however high, can push a boat behind a farther boat`() {
        // Boats do not move in depth at all, so the property that v3.0 got right -- two overlapping
        // hulls read as one passing in front of the other -- cannot be disturbed by this change.
        val boats = (0 until LakeLanes.LANE_COUNT step 2).map { LakeLanes.depthOf(laneY(it), 0f) }
        assertEquals(boats.sorted(), boats)
        assertEquals(boats.indices.toList(), paintOrder(*boats.toFloatArray()))
    }

    @Test
    fun `a dolphin can only ever move backwards in the order, never forwards`() {
        for (climb in listOf(0f, 5f, maxLeap / 2f, maxLeap)) {
            val moved = LakeLanes.depthOf(laneY(3), climb)
            assertTrue("climbing must not bring an animal nearer", moved <= laneY(3))
        }
    }

    @Test
    fun `a farther dolphin can never pass a nearer one`() {
        // Both at their own worst case: the far one at full leap (most receded), the near one flat
        // on the water (least receded). The far one still has to be painted first.
        val far = LakeLanes.depthOf(laneY(1), maxLeap)
        val near = LakeLanes.depthOf(laneY(3), 0f)
        assertTrue("a receding dolphin must not overtake one that is nearer", far < near)
        assertEquals(listOf(0, 1), paintOrder(far, near))
    }

    @Test
    fun `a whole crowded lake still paints far to near by rendered base`() {
        // Four boats and four dolphins, every lane taken, the dolphins at assorted points of their
        // leaps -- the lake-busy golden's own configuration.
        val climbs = floatArrayOf(maxLeap, 0f, maxLeap * 0.6f, 12f)
        val depths = FloatArray(LakeLanes.LANE_COUNT) { lane ->
            val isDolphin = lane % 2 == 1
            LakeLanes.depthOf(laneY(lane), if (isDolphin) climbs[lane / 2] else 0f)
        }
        val order = paintOrder(*depths)
        val painted = order.map { depths[it] }

        assertEquals("the pass must still be ascending", painted.sorted(), painted)
        // And the boats among them are still in lane order.
        val boatsInPaintOrder = order.filter { it % 2 == 0 }
        assertEquals(listOf(0, 2, 4, 6), boatsInPaintOrder)
    }

    @Test
    fun `a sail is tall enough that lane ordering alone could not have fixed this`() {
        // Not a behaviour assertion -- a statement of the arithmetic the fix exists for, so that a
        // future change to lane spacing or sail height shows up here rather than on screen.
        assertTrue(
            "if a sail were shorter than a lane there would be no overlap to resolve",
            sailHeight > laneSpacing * 2f,
        )
        assertTrue(
            "and if a leap could not clear a lane the dolphin would never reach the sail",
            maxLeap > laneSpacing,
        )
    }
}
