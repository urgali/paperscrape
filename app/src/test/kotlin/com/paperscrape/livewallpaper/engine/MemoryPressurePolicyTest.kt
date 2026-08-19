package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the memory-pressure policy and the sprite cache's bookkeeping.
 *
 * [SpriteCache] itself holds `android.graphics.Bitmap`, so it cannot run under a local unit test —
 * which is exactly why the two parts with real logic ([MemoryPressurePolicy] and
 * [SpriteCacheIndex]) are separate, Android-free classes.
 */
class MemoryPressurePolicyTest {

    // --- The trap: levels are not ordered by severity ------------------------------------------

    @Test
    fun `UI_HIDDEN never evicts even though its value is above RUNNING_CRITICAL`() {
        // TRIM_MEMORY_UI_HIDDEN is 20; TRIM_MEMORY_RUNNING_CRITICAL is 15. A `level >= CRITICAL`
        // threshold would throw the whole cache away every time the settings screen closes, while
        // the wallpaper carries on drawing. This is the single most important case here.
        assertTrue(MemoryPressurePolicy.TRIM_MEMORY_UI_HIDDEN > MemoryPressurePolicy.TRIM_MEMORY_RUNNING_CRITICAL)
        assertEquals(
            TrimAction.KEEP_ALL,
            MemoryPressurePolicy.actionFor(MemoryPressurePolicy.TRIM_MEMORY_UI_HIDDEN, anyEngineVisible = true),
        )
        assertEquals(
            TrimAction.KEEP_ALL,
            MemoryPressurePolicy.actionFor(MemoryPressurePolicy.TRIM_MEMORY_UI_HIDDEN, anyEngineVisible = false),
        )
    }

    @Test
    fun `UI_HIDDEN is milder than the numerically smaller RUNNING_CRITICAL`() {
        val uiHidden = MemoryPressurePolicy.actionFor(MemoryPressurePolicy.TRIM_MEMORY_UI_HIDDEN, true)
        val critical = MemoryPressurePolicy.actionFor(MemoryPressurePolicy.TRIM_MEMORY_RUNNING_CRITICAL, true)
        assertNotEquals("severity must not follow the numeric order", uiHidden, critical)
    }

    // --- Per-level mapping ----------------------------------------------------------------------

    @Test
    fun `running moderate keeps everything`() {
        for (visible in listOf(true, false)) {
            assertEquals(
                TrimAction.KEEP_ALL,
                MemoryPressurePolicy.actionFor(MemoryPressurePolicy.TRIM_MEMORY_RUNNING_MODERATE, visible),
            )
        }
    }

    @Test
    fun `running low trims while visible and releases while not drawing`() {
        assertEquals(
            TrimAction.TRIM_TO_HALF,
            MemoryPressurePolicy.actionFor(MemoryPressurePolicy.TRIM_MEMORY_RUNNING_LOW, anyEngineVisible = true),
        )
        assertEquals(
            TrimAction.RELEASE_ALL,
            MemoryPressurePolicy.actionFor(MemoryPressurePolicy.TRIM_MEMORY_RUNNING_LOW, anyEngineVisible = false),
        )
    }

    @Test
    fun `running critical trims harder while visible`() {
        assertEquals(
            TrimAction.TRIM_TO_QUARTER,
            MemoryPressurePolicy.actionFor(MemoryPressurePolicy.TRIM_MEMORY_RUNNING_CRITICAL, anyEngineVisible = true),
        )
        assertEquals(
            TrimAction.RELEASE_ALL,
            MemoryPressurePolicy.actionFor(MemoryPressurePolicy.TRIM_MEMORY_RUNNING_CRITICAL, anyEngineVisible = false),
        )
    }

    @Test
    fun `kill-candidate levels always release everything`() {
        for (level in listOf(
            MemoryPressurePolicy.TRIM_MEMORY_BACKGROUND,
            MemoryPressurePolicy.TRIM_MEMORY_MODERATE,
            MemoryPressurePolicy.TRIM_MEMORY_COMPLETE,
        )) {
            for (visible in listOf(true, false)) {
                assertEquals(
                    "level $level visible=$visible",
                    TrimAction.RELEASE_ALL,
                    MemoryPressurePolicy.actionFor(level, visible),
                )
            }
        }
    }

    @Test
    fun `visibility only ever makes the response more aggressive, never less`() {
        val severity = mapOf(
            TrimAction.KEEP_ALL to 0,
            TrimAction.TRIM_TO_HALF to 1,
            TrimAction.TRIM_TO_QUARTER to 2,
            TrimAction.RELEASE_ALL to 3,
        )
        for (level in 0..100) {
            val whileVisible = severity.getValue(MemoryPressurePolicy.actionFor(level, true))
            val whileHidden = severity.getValue(MemoryPressurePolicy.actionFor(level, false))
            assertTrue(
                "not drawing should never be gentler than drawing (level $level)",
                whileHidden >= whileVisible,
            )
        }
    }

    // --- Unknown levels ---------------------------------------------------------------------------

    @Test
    fun `unknown levels below background are treated conservatively`() {
        for (level in listOf(-5, 0, 1, 7, 12, 18, 25, 39)) {
            assertEquals(
                "unknown level $level should not release while drawing",
                TrimAction.KEEP_ALL,
                MemoryPressurePolicy.actionFor(level, anyEngineVisible = true),
            )
        }
    }

    @Test
    fun `unknown levels at or beyond background release everything`() {
        for (level in listOf(41, 55, 70, 90, 1000)) {
            assertEquals(
                "unknown level $level",
                TrimAction.RELEASE_ALL,
                MemoryPressurePolicy.actionFor(level, anyEngineVisible = true),
            )
        }
    }

    @Test
    fun `no level ever throws`() {
        for (level in -1000..1000) {
            MemoryPressurePolicy.actionFor(level, true)
            MemoryPressurePolicy.actionFor(level, false)
        }
    }

    @Test
    fun `mirrored constants match the platform values`() {
        // Checked against the API 36 android.jar rather than recalled. If a future platform
        // renumbers these, the mapping above silently becomes wrong.
        assertEquals(5, MemoryPressurePolicy.TRIM_MEMORY_RUNNING_MODERATE)
        assertEquals(10, MemoryPressurePolicy.TRIM_MEMORY_RUNNING_LOW)
        assertEquals(15, MemoryPressurePolicy.TRIM_MEMORY_RUNNING_CRITICAL)
        assertEquals(20, MemoryPressurePolicy.TRIM_MEMORY_UI_HIDDEN)
        assertEquals(40, MemoryPressurePolicy.TRIM_MEMORY_BACKGROUND)
        assertEquals(60, MemoryPressurePolicy.TRIM_MEMORY_MODERATE)
        assertEquals(80, MemoryPressurePolicy.TRIM_MEMORY_COMPLETE)
    }
}

/**
 * Tests for [SpriteCacheIndex] — the cache's key/size/LRU bookkeeping.
 */
class SpriteCacheIndexTest {

    private fun index() = SpriteCacheIndex(initialCapacity = 4)

    @Test
    fun `starts empty`() {
        val i = index()
        assertEquals(0, i.size)
        assertEquals(0L, i.totalBytes)
        assertEquals(-1, i.find(0x7f010001))
    }

    @Test
    fun `put then find returns the same slot`() {
        val i = index()
        val slot = i.put(0x7f010001, 1024)
        assertEquals(slot, i.find(0x7f010001))
        assertEquals(1, i.size)
        assertEquals(1024L, i.totalBytes)
    }

    @Test
    fun `bytes accumulate across entries`() {
        val i = index()
        i.put(1, 100); i.put(2, 250); i.put(3, 650)
        assertEquals(1000L, i.totalBytes)
        assertEquals(3, i.size)
    }

    @Test
    fun `grows beyond its initial capacity`() {
        val i = index()
        for (key in 1..200) i.put(key, 10)
        assertEquals(200, i.size)
        assertEquals(2000L, i.totalBytes)
        for (key in 1..200) assertTrue("key $key lost after growth", i.find(key) >= 0)
    }

    @Test
    fun `eviction removes the least recently used entry`() {
        val i = index()
        i.put(1, 100); i.put(2, 100); i.put(3, 100)
        i.find(1) // touch 1 and 3, leaving 2 as least recently used
        i.find(3)
        i.evictLeastRecentlyUsed()
        assertEquals("2 should have been evicted", -1, i.find(2))
        assertTrue(i.find(1) >= 0)
        assertTrue(i.find(3) >= 0)
    }

    @Test
    fun `eviction subtracts the evicted entry's bytes`() {
        val i = index()
        i.put(1, 400); i.put(2, 600)
        i.find(2) // 1 is now least recently used
        i.evictLeastRecentlyUsed()
        assertEquals(600L, i.totalBytes)
        assertEquals(1, i.size)
    }

    @Test
    fun `eviction on an empty index reports nothing to free`() {
        assertEquals(-1, index().evictLeastRecentlyUsed())
    }

    @Test
    fun `evicting to a byte budget frees enough and no more`() {
        // Mirrors what SpriteCache.evictTo does.
        val i = index()
        for (key in 1..10) i.put(key, 100) // 1000 bytes total
        val budget = 1000L / 2
        while (i.totalBytes > budget) {
            if (i.evictLeastRecentlyUsed() < 0) break
        }
        assertTrue("should be at or under budget, was ${i.totalBytes}", i.totalBytes <= budget)
        assertTrue("should not have over-evicted, was ${i.totalBytes}", i.totalBytes > budget - 100)
    }

    @Test
    fun `frequently drawn sprites survive repeated trimming`() {
        // The behaviour that keeps the visible scene drawable after a trim: sprites drawn every
        // frame stay, seldom-drawn ones fall out.
        val i = index()
        val hot = listOf(1, 2, 3)
        for (key in 1..20) i.put(key, 100)
        repeat(5) {
            for (key in hot) i.find(key)
            while (i.totalBytes > 1000) {
                if (i.evictLeastRecentlyUsed() < 0) break
            }
            for (key in hot) assertTrue("hot sprite $key was evicted", i.find(key) >= 0)
            // Re-add some cold sprites, as a redraw would.
            for (key in 100..105) if (i.find(key) < 0) i.put(key, 100)
        }
    }

    @Test
    fun `slots freed by eviction are reused rather than growing the arrays`() {
        val i = index()
        val slotA = i.put(1, 100)
        i.put(2, 100)
        i.find(2)
        i.evictLeastRecentlyUsed() // evicts 1
        val slotC = i.put(3, 100)
        assertEquals("the freed slot should be recycled", slotA, slotC)
        assertEquals(3, i.keyAt(slotC))
    }

    @Test
    fun `clear empties everything and allows reuse`() {
        val i = index()
        for (key in 1..10) i.put(key, 100)
        i.clear()
        assertEquals(0, i.size)
        assertEquals(0L, i.totalBytes)
        for (key in 1..10) assertEquals(-1, i.find(key))
        // Must still be usable afterwards -- the wallpaper keeps drawing after a release.
        val slot = i.put(42, 500)
        assertTrue(slot >= 0)
        assertEquals(500L, i.totalBytes)
        assertEquals(slot, i.find(42))
    }

    @Test
    fun `repeated fill and release cycles do not leak entries or bytes`() {
        // A device under sustained pressure trims repeatedly; the index must return to a clean
        // state every time rather than drifting.
        val i = index()
        repeat(50) {
            for (key in 1..30) i.put(key, 1000)
            assertEquals(30, i.size)
            assertEquals(30_000L, i.totalBytes)
            i.clear()
            assertEquals(0, i.size)
            assertEquals(0L, i.totalBytes)
        }
    }

    @Test
    fun `peak bytes records the high-water mark`() {
        val i = index()
        i.put(1, 1000); i.put(2, 2000)
        i.find(1)
        i.evictLeastRecentlyUsed()
        assertEquals(3000L, i.peakBytes)
        assertTrue(i.totalBytes < i.peakBytes)
    }

    @Test
    fun `handles resource-id shaped keys`() {
        // Real keys are values like 0x7f080123 -- far outside Integer's small-value cache, which
        // is why this index exists instead of a boxed map.
        val i = index()
        val keys = intArrayOf(0x7f080001, 0x7f080002, 0x7f0800FF, 0x7f09ABCD)
        for (k in keys) i.put(k, 2048)
        for (k in keys) assertTrue("key ${k.toString(16)} not found", i.find(k) >= 0)
        assertEquals(keys.size.toLong() * 2048, i.totalBytes)
    }

    @Test
    fun `remove drops a named entry and frees its bytes`() {
        val index = SpriteCacheIndex(4)
        index.put(10, 100)
        index.put(20, 200)
        index.put(30, 300)
        assertEquals(600L, index.totalBytes)

        assertTrue(index.remove(20) >= 0)
        assertEquals(2, index.size)
        assertEquals(400L, index.totalBytes)
        assertEquals(-1, index.find(20))
        assertTrue(index.find(10) >= 0)
        assertTrue(index.find(30) >= 0)
    }

    @Test
    fun `remove reports absence rather than corrupting the index`() {
        val index = SpriteCacheIndex(4)
        index.put(10, 100)
        assertEquals(-1, index.remove(999))
        assertEquals(1, index.size)
        assertEquals(100L, index.totalBytes)
    }

    @Test
    fun `a removed slot is handed back out to the next put`() {
        // The GPU renderer releases each sprite right after uploading it, so this runs often enough
        // that leaking a slot per release would grow the backing arrays without bound.
        val index = SpriteCacheIndex(4)
        index.put(10, 100)
        index.put(20, 200)
        val freed = index.remove(10)
        val reused = index.put(30, 300)
        assertEquals(freed, reused)
        assertEquals(2, index.size)
        assertEquals(500L, index.totalBytes)
    }

    @Test
    fun `removing from the middle preserves the recency order of the rest`() {
        val index = SpriteCacheIndex(4)
        index.put(10, 100)
        index.put(20, 100)
        index.put(30, 100)
        index.find(10) // most-recently-used order is now 10, 30, 20
        index.remove(30)
        // 20 was the least recently used of what remains, so it must be evicted next.
        val evicted = index.evictLeastRecentlyUsed()
        assertTrue(evicted >= 0)
        assertEquals(1, index.size)
        assertTrue("10 must survive", index.find(10) >= 0)
        assertEquals(-1, index.find(20))
    }

}
