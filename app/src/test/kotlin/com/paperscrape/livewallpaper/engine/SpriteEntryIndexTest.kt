package com.paperscrape.livewallpaper.engine

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [SpriteEntryIndex] gives the answer the linear scan it replaced gave, whatever the table holds.
 *
 * The reference below is that scan, written as `GlTextureCache.find` had it until v5.10B, over the
 * same parallel arrays a cache fills: entries appended in registration order, each key once. The
 * keys are the shapes the app uses -- resource ids with the high bits every `R.drawable` id shares,
 * the white pixel's negative sentinel, detail levels 0-6 -- and the tables grow past several
 * resizes and are cleared and refilled, as a trim and a lost context do.
 */
class SpriteEntryIndexTest {

    private class Reference {
        val resIds = ArrayList<Int>()
        val levels = ArrayList<Int>()
        fun find(resId: Int, level: Int): Int {
            for (i in resIds.indices) if (resIds[i] == resId && levels[i] == level) return i
            return -1
        }
        fun add(resId: Int, level: Int): Int {
            resIds += resId
            levels += level
            return resIds.size - 1
        }
        fun clear() {
            resIds.clear()
            levels.clear()
        }
    }

    private fun drawableId(random: Random): Int = 0x7f080000 + random.nextInt(0, 1_200)

    @Test
    fun `lookups agree with the linear scan on random tables, through growth and clears`() {
        val random = Random(20260930)
        repeat(40) { round ->
            val index = SpriteEntryIndex(initialSlots = if (round % 2 == 0) 2 else 256)
            val reference = Reference()
            // The white pixel is the first thing packed into a fresh atlas.
            index.add(-1, 0, reference.add(-1, 0))
            val target = random.nextInt(1, 900)
            while (reference.resIds.size < target) {
                val resId = drawableId(random)
                val level = random.nextInt(0, 7)
                if (reference.find(resId, level) >= 0) continue // the cache registers a pair once
                index.add(resId, level, reference.add(resId, level))
            }
            assertEquals(reference.resIds.size, index.size)
            // Every key present, and a sample of keys absent, answer as the scan answers.
            for (i in reference.resIds.indices) {
                assertEquals(i, index.find(reference.resIds[i], reference.levels[i]))
            }
            repeat(2_000) {
                val resId = if (random.nextInt(10) == 0) -1 else drawableId(random)
                val level = random.nextInt(0, 8)
                assertEquals("round $round, ($resId, $level)", reference.find(resId, level), index.find(resId, level))
            }
            // A trim or a lost context empties the table; what is registered after is found anew.
            index.clear()
            reference.clear()
            assertEquals(-1, index.find(-1, 0))
            index.add(-1, 0, reference.add(-1, 0))
            val resId = drawableId(random)
            index.add(resId, 3, reference.add(resId, 3))
            assertEquals(1, index.find(resId, 3))
            assertEquals(-1, index.find(resId, 2))
        }
    }

    @Test
    fun `the same resource at two levels is two entries, and the same level of two resources is too`() {
        val index = SpriteEntryIndex()
        index.add(0x7f080010, 0, 0)
        index.add(0x7f080010, 2, 1)
        index.add(0x7f080011, 0, 2)
        assertEquals(0, index.find(0x7f080010, 0))
        assertEquals(1, index.find(0x7f080010, 2))
        assertEquals(2, index.find(0x7f080011, 0))
        assertEquals(-1, index.find(0x7f080011, 2))
    }
}
