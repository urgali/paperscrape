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
    // These hold `LakeLanes.depthOf`, the climb: a leaping animal recedes as it rises. Since v5.9B
    // the renderer does not sort on it bare -- `visibleWaterline` adds each kind's own drop from its
    // placement point to where its drawing meets the water (see the section after this one), and
    // the climb is still what moves a dolphin back.
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

    // -- Every kind keyed by where its drawing meets the water (v5.9B, I-02) ---------------------
    //
    // The three kinds are placed from three different points. A sailboat from its *placement point*,
    // with `sailboat_hull` blitted at +8 units and 17 units tall, so the hull meets the water **25
    // boat units below** it (`PaperRenderer.SAILBOAT_HULL_WATERLINE_UNITS`). A dolphin from its lane,
    // with its body centred on the leap point, so in the air its belly is **29 of its own units**
    // below the point (`-DOLPHIN_ORIGIN_Y_UNITS`); under water only its splash shows, standing on
    // the lane. A wave from its base, which *is* its waterline.
    //
    // v4.28 put wave and hull on one convention by lifting the wave, and left the dolphin on its
    // own: 16.9 px apart on the BV6600's 1440 px surface. v5.9B's probe saw that on screen at the
    // factory settings (Beach, thunderstorm), so [LakeLanes.visibleWaterline] now keys all three the
    // same way. The numbers below are the real ones, at the BV6600's height.
    //
    // **v4.28's finding still holds and is why these are arithmetic and not a golden**: `wave-storm`
    // portrays a wave and a nearer hull and survived the mutation that broke their order -- one frame
    // samples one configuration; the property is about all of them.

    private val screen = 1440f
    private val scale = SceneSpace.sceneScale(screen)
    private val hullDrop = PaperRenderer.SAILBOAT_HULL_WATERLINE_UNITS * SceneSpace.SAILBOAT_BASE_SCALE * scale
    private val belly = -PaperRenderer.DOLPHIN_ORIGIN_Y_UNITS * SceneSpace.DOLPHIN_BASE_SCALE * scale

    private fun boat(y: Float) = LakeLanes.visibleWaterline(LakeLanes.Kind.SAILBOAT, y, 0f, screen)
    private fun dolphin(y: Float, climb: Float) = LakeLanes.visibleWaterline(LakeLanes.Kind.DOLPHIN, y, climb, screen)
    private fun wave(base: Float) = LakeLanes.visibleWaterline(LakeLanes.Kind.WAVE, base, 0f, screen)

    @Test
    fun `the hull offset is the artwork's own, not a number somebody liked`() {
        assertEquals(
            "sailboat_hull is blitted at +8 units and is 17 units tall, so its waterline is 25 " +
                "units under the placement point. If the artwork or the blit moves, this is the " +
                "first thing that has to move with it",
            25f, PaperRenderer.SAILBOAT_HULL_WATERLINE_UNITS,
        )
        assertEquals("the dolphin's body is centred on its leap point, half of 57 units tall", -29f, PaperRenderer.DOLPHIN_ORIGIN_Y_UNITS)
    }

    @Test
    fun `on the BV6600 the two old conventions were 16_9 px apart`() {
        assertEquals(16.9f, hullDrop - belly, 0.05f)
    }

    @Test
    fun `a wave is keyed by its base, a swimming dolphin by its lane, a boat by its hull`() {
        assertEquals(500f, wave(500f))
        assertEquals("under water only the splash shows, and it stands on the lane", 500f, dolphin(500f, 0f))
        assertEquals(500f + hullDrop, boat(500f))
        assertEquals("in the air, the belly", 500f - 10f + belly, dolphin(500f, 10f))
    }

    @Test
    fun `a wave whose waterline is behind a hull is painted behind it`() {
        val boatY = 500f
        val waveBase = boatY + hullDrop * 0.5f
        assertEquals(
            "the boat's hull is nearer than the wave's waterline, so the boat is painted last",
            listOf(1, 0), paintOrder(boat(boatY), wave(waveBase)),
        )
    }

    @Test
    fun `a wave nearer than the hull is still painted in front of it`() {
        val boatY = 500f
        assertEquals(listOf(0, 1), paintOrder(boat(boatY), wave(boatY + hullDrop * 2f)))
    }

    /**
     * **The pair v4.28 left behind, and the probe's frame.** A wave whose base is below a leaping
     * dolphin's belly -- nearer -- by less than the 16.9 px the conventions disagreed by. v5.9A drew
     * the dolphin over it.
     */
    @Test
    fun `a wave nearer than a leaping dolphin's belly is painted in front of it`() {
        val laneY = 500f
        val climb = 12f
        val waveBase = laneY - climb + belly + 6f
        assertEquals(listOf(0, 1), paintOrder(dolphin(laneY, climb), wave(waveBase)))

        // The mutation, stated: v5.9A's keys -- the dolphin by its lane less its climb, the wave by
        // its base lifted by the hull's offset -- put this wave behind the dolphin.
        val oldDolphin = LakeLanes.depthOf(laneY, climb)
        val oldWave = waveBase - hullDrop
        assertEquals("v5.9A's keys must get this pair wrong, or the test tests nothing", listOf(1, 0), paintOrder(oldDolphin, oldWave))
    }

    @Test
    fun `a wave behind a leaping dolphin's belly stays behind it`() {
        val laneY = 500f
        val climb = 12f
        assertEquals(listOf(1, 0), paintOrder(dolphin(laneY, climb), wave(laneY - climb + belly - 3f)))
    }

    /** The same arithmetic against a boat: the dolphin crossing the deck of a boat whose hull is nearer. */
    @Test
    fun `a leaping dolphin whose belly is above a hull goes behind that boat`() {
        val boatY = 500f
        val dolphinLane = boatY + hullDrop - belly + 4f   // a lane below the boat's placement point
        val climb = 6f
        assertTrue("the case must be one v5.9A painted the other way", LakeLanes.depthOf(dolphinLane, climb) > boatY)
        assertEquals(listOf(1, 0), paintOrder(boat(boatY), dolphin(dolphinLane, climb)))
    }

    @Test
    fun `boats among themselves and waves against boats keep the order they had`() {
        val boats = (0 until LakeLanes.LANE_COUNT step 2).map { laneY(it) }
        val waves = listOf(laneY(1), laneY(5))
        fun order(keys: List<Float>) = paintOrder(*keys.toFloatArray())
        val before = order(boats + waves.map { it - hullDrop })
        val after = order(boats.map { boat(it) } + waves.map { wave(it) })
        assertEquals("moving every boat down to its hull must not reorder a boat against a wave", before, after)
    }

    @Test
    fun `the climb still only ever moves a dolphin backwards`() {
        val keys = listOf(0.5f, 5f, maxLeap / 2f, maxLeap).map { dolphin(laneY(3), it) }
        assertEquals("a higher leap must key the animal farther, never nearer", keys.sortedDescending(), keys)
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
