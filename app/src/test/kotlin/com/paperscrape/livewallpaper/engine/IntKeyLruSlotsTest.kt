package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [IntKeyLruSlots], the bounded, allocation-free multi-component key-to-slot mapping that
 * [GradientShaderCache] runs on (**P2-5**).
 *
 * Two properties carry the weight.
 *
 * **Exactness.** A cache that returned a hit for a key that only *nearly* matched would hand the
 * renderer somebody else's gradient — a silent wrong-colour bug on a path with no assertion in it.
 * Every component is therefore checked, including the ones a naive implementation would be most
 * likely to drop: the last one, and the ones that are zero.
 *
 * **The bound.** Gradient colours are day/night blends, so new keys can keep arriving for as long
 * as the wallpaper runs. If eviction were wrong the render loop would leak native `Shader` objects
 * indefinitely, which is the opposite of what P2-5 is for.
 *
 * [GradientShaderCache] itself cannot be unit tested — it holds `android.graphics` types, which are
 * stubbed under local unit tests — which is precisely why this logic lives in its own Android-free
 * class.
 */
class IntKeyLruSlotsTest {

    @Test
    fun `starts empty`() {
        val slots = IntKeyLruSlots(4)
        assertEquals(0, slots.size)
        assertEquals(-1, slots.find(1, 2, 3, 4, 5))
    }

    @Test
    fun `reserve then find returns the same slot`() {
        val slots = IntKeyLruSlots(4)
        val slot = slots.reserve(1, 2, 3, 4, 5)
        assertEquals(slot, slots.find(1, 2, 3, 4, 5))
        assertEquals(1, slots.size)
    }

    @Test
    fun `distinct keys get distinct slots`() {
        val slots = IntKeyLruSlots(4)
        val a = slots.reserve(1, 2, 3, 4, 5)
        val b = slots.reserve(9, 9, 9, 9, 9)
        assertNotEquals(a, b)
        assertEquals(a, slots.find(1, 2, 3, 4, 5))
        assertEquals(b, slots.find(9, 9, 9, 9, 9))
    }

    /**
     * The one that matters: a key differing in a single component, in *any* position, must miss.
     *
     * Run over all five positions rather than one, because "compares the first four and forgets the
     * fifth" is exactly the shape of bug this class could have and nothing else would catch.
     */
    @Test
    fun `a difference in any single component is a miss`() {
        val base = intArrayOf(11, 22, 33, 44, 55)
        for (position in 0 until IntKeyLruSlots.KEY_WIDTH) {
            val slots = IntKeyLruSlots(4)
            slots.reserve(base[0], base[1], base[2], base[3], base[4])
            val probe = base.copyOf()
            probe[position] = probe[position] + 1
            assertEquals(
                "component $position was not compared",
                -1,
                slots.find(probe[0], probe[1], probe[2], probe[3], probe[4]),
            )
        }
    }

    /**
     * Zero is the padding a four-component key leaves in the fifth slot, so it has to be a real
     * value and not a wildcard.
     */
    @Test
    fun `zero components are compared like any other value`() {
        val slots = IntKeyLruSlots(4)
        val padded = slots.reserve(1, 2, 3, 4, 0)
        assertEquals(padded, slots.find(1, 2, 3, 4, 0))
        assertEquals(-1, slots.find(1, 2, 3, 4, 7))

        val other = slots.reserve(1, 2, 3, 4, 7)
        assertNotEquals(padded, other)
        // The padded entry must survive the arrival of its near-neighbour.
        assertEquals(padded, slots.find(1, 2, 3, 4, 0))
    }

    @Test
    fun `negative components are compared correctly`() {
        val slots = IntKeyLruSlots(4)
        val slot = slots.reserve(-1, Int.MIN_VALUE, 0, Int.MAX_VALUE, -7)
        assertEquals(slot, slots.find(-1, Int.MIN_VALUE, 0, Int.MAX_VALUE, -7))
        assertEquals(-1, slots.find(-1, Int.MIN_VALUE, 0, Int.MAX_VALUE, -8))
    }

    @Test
    fun `size never exceeds capacity`() {
        val slots = IntKeyLruSlots(3)
        repeat(50) { slots.reserve(it, it, it, it, it) }
        assertEquals(3, slots.size)
    }

    @Test
    fun `the least recently used entry is the one evicted`() {
        val slots = IntKeyLruSlots(3)
        slots.reserve(1, 0, 0, 0, 0)
        slots.reserve(2, 0, 0, 0, 0)
        slots.reserve(3, 0, 0, 0, 0)

        // Touch 1, making 2 the least recently used.
        assertTrue(slots.find(1, 0, 0, 0, 0) >= 0)
        slots.reserve(4, 0, 0, 0, 0)

        assertEquals("2 was the least recently used and should be gone", -1, slots.find(2, 0, 0, 0, 0))
        assertTrue("1 was touched and should survive", slots.find(1, 0, 0, 0, 0) >= 0)
        assertTrue(slots.find(3, 0, 0, 0, 0) >= 0)
        assertTrue(slots.find(4, 0, 0, 0, 0) >= 0)
    }

    @Test
    fun `an evicted slot is reused rather than a new one allocated`() {
        val slots = IntKeyLruSlots(2)
        val a = slots.reserve(1, 0, 0, 0, 0)
        val b = slots.reserve(2, 0, 0, 0, 0)
        val c = slots.reserve(3, 0, 0, 0, 0)
        assertTrue("a full table must recycle a slot index", c == a || c == b)
        assertEquals(2, slots.size)
    }

    /**
     * The wallpaper's own access pattern: the same small set of gradients, over and over, with the
     * occasional new one as a colour ramp moves. Nothing in the working set may be evicted.
     */
    @Test
    fun `a repeated working set smaller than capacity never evicts`() {
        val slots = IntKeyLruSlots(8)
        val working = listOf(
            intArrayOf(1, 1, 1, 1, 0),
            intArrayOf(2, 2, 2, 2, 0),
            intArrayOf(3, 3, 3, 3, 3),
        )
        working.forEach { slots.reserve(it[0], it[1], it[2], it[3], it[4]) }
        repeat(1000) {
            working.forEach { k ->
                assertTrue(
                    "a member of the working set was evicted",
                    slots.find(k[0], k[1], k[2], k[3], k[4]) >= 0,
                )
            }
        }
        assertEquals(3, slots.size)
    }

    @Test
    fun `clear empties the table`() {
        val slots = IntKeyLruSlots(4)
        slots.reserve(1, 2, 3, 4, 5)
        slots.clear()
        assertEquals(0, slots.size)
        assertEquals(-1, slots.find(1, 2, 3, 4, 5))
    }

    @Test
    fun `capacity must be positive`() {
        val error = runCatching { IntKeyLruSlots(0) }.exceptionOrNull()
        assertTrue("expected IllegalArgumentException, got $error", error is IllegalArgumentException)
    }

    /**
     * `Float.toRawBits` is how [GradientShaderCache] turns coordinates into key components, so the
     * table has to distinguish two floats that differ by one ULP.
     */
    @Test
    fun `float bit patterns one ulp apart are distinct keys`() {
        val slots = IntKeyLruSlots(4)
        val a = 100f
        val b = Math.nextUp(a)
        val slotA = slots.reserve(a.toRawBits(), 0, 0, 0, 0)
        assertEquals(-1, slots.find(b.toRawBits(), 0, 0, 0, 0))
        val slotB = slots.reserve(b.toRawBits(), 0, 0, 0, 0)
        assertNotEquals(slotA, slotB)
    }
}
