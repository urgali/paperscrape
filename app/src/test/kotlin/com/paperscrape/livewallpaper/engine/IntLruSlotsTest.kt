package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [IntLruSlots], the bounded, allocation-free key-to-slot mapping that keeps
 * [TintFilterCache] from growing without limit.
 *
 * The property that matters most here is the bound: tint colours are day/night blends, so new
 * keys can keep arriving for as long as the wallpaper runs. If eviction were wrong, the render
 * loop would leak native objects indefinitely.
 *
 * [TintFilterCache] itself cannot be unit tested — it holds `android.graphics` types, which are
 * stubbed under local unit tests — which is precisely why this logic lives in its own
 * Android-free class.
 */
class IntLruSlotsTest {

    @Test
    fun `starts empty`() {
        val slots = IntLruSlots(4)
        assertEquals(0, slots.size)
        assertEquals(-1, slots.find(123))
    }

    @Test
    fun `reserve then find returns the same slot`() {
        val slots = IntLruSlots(4)
        val slot = slots.reserve(0xFF112233.toInt())
        assertEquals(slot, slots.find(0xFF112233.toInt()))
        assertEquals(1, slots.size)
    }

    @Test
    fun `distinct keys get distinct slots`() {
        val slots = IntLruSlots(4)
        val a = slots.reserve(10)
        val b = slots.reserve(20)
        val c = slots.reserve(30)
        assertNotEquals(a, b)
        assertNotEquals(b, c)
        assertNotEquals(a, c)
        assertEquals(3, slots.size)
    }

    @Test
    fun `find misses for a key that was never reserved`() {
        val slots = IntLruSlots(4)
        slots.reserve(10)
        slots.reserve(20)
        assertEquals(-1, slots.find(30))
    }

    @Test
    fun `size never exceeds capacity no matter how many keys arrive`() {
        // The core bound. This is the scenario the cache actually faces: a continuous stream of
        // new colours as the day/night blend advances.
        val capacity = 8
        val slots = IntLruSlots(capacity)
        for (key in 0 until 100_000) {
            if (slots.find(key) < 0) slots.reserve(key)
            assertTrue("size ${slots.size} exceeded capacity $capacity", slots.size <= capacity)
        }
        assertEquals(capacity, slots.size)
    }

    @Test
    fun `slot indices always stay inside the backing arrays`() {
        val capacity = 8
        val slots = IntLruSlots(capacity)
        for (key in 0 until 1_000) {
            val slot = if (slots.find(key) >= 0) slots.find(key) else slots.reserve(key)
            assertTrue("slot $slot out of bounds", slot in 0 until capacity)
        }
    }

    @Test
    fun `least recently used key is the one evicted`() {
        val slots = IntLruSlots(3)
        slots.reserve(1)
        slots.reserve(2)
        slots.reserve(3)

        // Touch 1 and 3, leaving 2 as least recently used.
        assertTrue(slots.find(1) >= 0)
        assertTrue(slots.find(3) >= 0)

        slots.reserve(4) // must evict 2

        assertEquals("2 should have been evicted", -1, slots.find(2))
        assertTrue("1 should have survived", slots.find(1) >= 0)
        assertTrue("3 should have survived", slots.find(3) >= 0)
        assertTrue("4 should be present", slots.find(4) >= 0)
    }

    @Test
    fun `evicted slot is reused rather than a new one allocated`() {
        val slots = IntLruSlots(2)
        val slotA = slots.reserve(1)
        slots.reserve(2)
        val slotC = slots.reserve(3) // evicts 1, the least recently used
        assertEquals("the evicted entry's slot should be recycled", slotA, slotC)
        assertEquals(3, slots.keyAt(slotC))
    }

    @Test
    fun `find marks an entry as most recently used`() {
        val slots = IntLruSlots(3)
        slots.reserve(1)
        slots.reserve(2)
        slots.reserve(3)
        slots.find(1)
        assertEquals("most recently used should be at the front", 1, slots.keyAt(slots.slotAtOrder(0)))
    }

    @Test
    fun `reserve marks the new entry as most recently used`() {
        val slots = IntLruSlots(3)
        slots.reserve(1)
        slots.reserve(2)
        assertEquals(2, slots.keyAt(slots.slotAtOrder(0)))
    }

    @Test
    fun `a repeatedly used key is never evicted by a stream of one-off keys`() {
        // The realistic hot/cold mix: one colour used every frame, plus churn around it.
        val hotKey = 0xFFABCDEF.toInt()
        val slots = IntLruSlots(4)
        slots.reserve(hotKey)
        for (cold in 1..1_000) {
            assertTrue("hot key was evicted at cold=$cold", slots.find(hotKey) >= 0)
            if (slots.find(cold) < 0) slots.reserve(cold)
        }
        assertTrue(slots.find(hotKey) >= 0)
    }

    @Test
    fun `a working set that fits in capacity produces no evictions`() {
        // Matches the intended steady state: a frame's distinct colours all stay resident, so
        // every lookup after the first frame is a hit.
        val workingSet = listOf(11, 22, 33, 44, 55, 66)
        val slots = IntLruSlots(64)
        for (key in workingSet) slots.reserve(key)

        repeat(500) {
            for (key in workingSet) {
                assertTrue("key $key was evicted", slots.find(key) >= 0)
            }
        }
        assertEquals(workingSet.size, slots.size)
    }

    @Test
    fun `clear empties the mapping`() {
        val slots = IntLruSlots(4)
        slots.reserve(1)
        slots.reserve(2)
        slots.clear()
        assertEquals(0, slots.size)
        assertEquals(-1, slots.find(1))
        assertEquals(-1, slots.find(2))
    }

    @Test
    fun `capacity of one degrades to a single entry without breaking`() {
        val slots = IntLruSlots(1)
        slots.reserve(1)
        assertTrue(slots.find(1) >= 0)
        slots.reserve(2)
        assertEquals(-1, slots.find(1))
        assertTrue(slots.find(2) >= 0)
        assertEquals(1, slots.size)
    }

    @Test
    fun `negative and extreme keys are handled like any other`() {
        // Tint colours are ARGB ints, so the sign bit is set for every non-transparent colour.
        val slots = IntLruSlots(4)
        val keys = intArrayOf(Int.MIN_VALUE, -1, 0, Int.MAX_VALUE)
        for (key in keys) slots.reserve(key)
        for (key in keys) assertTrue("key $key not found", slots.find(key) >= 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero capacity is rejected`() {
        IntLruSlots(0)
    }
}
