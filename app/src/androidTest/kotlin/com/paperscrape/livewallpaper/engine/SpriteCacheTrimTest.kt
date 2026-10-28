package com.paperscrape.livewallpaper.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.R
import java.lang.ref.WeakReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **A memory trim lets go of the least-recently-drawn sprites, down to what [MemoryPressurePolicy] asks, and a sprite
 * let go is memory given back** -- on the real [SpriteCache], with real decoded sprites, through
 * [SpriteCache.onTrimMemory] as the service calls it.
 *
 * For every level the system sends, with the wallpaper drawing and without, the cache is filled with eight sprites in
 * a known order, the first of them drawn again so it is the most recent, and trimmed. Then:
 *
 *  - what it still counts ([SpriteCache.cachedBytes], [SpriteCache.cachedCount]) is what the policy's action leaves:
 *    everything, at most a half or a quarter of the bytes -- taken from the least-recently-drawn end -- or nothing;
 *  - **the cache does not hold the bitmap of any sprite it has stopped counting.** The count lives in the cache's index
 *    and the bitmap in an array beside it, and an index that forgot a sprite while the array kept its bitmap would read
 *    as memory given back and give none. The array is read as it is (a field of the cache): whether the collector has
 *    run yet is the runtime's business, not the cache's;
 *  - every sprite it still counts is the same bitmap it decoded, not decoded again.
 */
@RunWith(AndroidJUnit4::class)
class SpriteCacheTrimTest {

    private class Held(val name: String, val resId: Int, val bytes: Int, val bitmap: WeakReference<android.graphics.Bitmap>)

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun startEmpty() = SpriteCache.clear()

    @After fun leaveEmpty() = SpriteCache.clear()

    @Test
    fun eachTrimLeavesWhatThePolicyAsksAndTheRestIsCollected() {
        var evictedByBudget = 0
        for (visible in listOf(true, false)) for (level in LEVELS) {
            val case = "level $level, ${if (visible) "drawing" else "not drawing"}"
            val action = MemoryPressurePolicy.actionFor(level, visible)
            SpriteCache.clear()
            val held = fill()
            val total = held.sumOf { it.bytes.toLong() }
            assertEquals("$case: what was decoded is counted", total, SpriteCache.cachedBytes)

            SpriteCache.onTrimMemory(level, visible)

            // What the action leaves, least-recently drawn going first: the order the sprites were drawn in, the first
            // of them last since it was drawn again.
            val leastRecentFirst = held.drop(1) + held.first()
            val evicted = ArrayList<Held>()
            var remaining = total
            when (action) {
                TrimAction.KEEP_ALL -> Unit
                TrimAction.RELEASE_ALL -> { evicted += leastRecentFirst; remaining = 0L }
                TrimAction.TRIM_TO_HALF, TrimAction.TRIM_TO_QUARTER -> {
                    val budget = if (action == TrimAction.TRIM_TO_HALF) total / 2 else total / 4
                    for (h in leastRecentFirst) {
                        if (remaining <= budget) break
                        remaining -= h.bytes
                        evicted += h
                    }
                    evictedByBudget += evicted.size
                }
            }
            val kept = held.filter { it !in evicted }
            assertEquals("$case ($action): the bytes left", remaining, SpriteCache.cachedBytes)
            assertEquals("$case ($action): the sprites left", kept.size, SpriteCache.cachedCount)

            val inTheCache = heldByTheCache()
            val stillHeld = evicted.filter { h -> h.bitmap.get().let { b -> b != null && inTheCache.any { it === b } } }.map { it.name }
            assertTrue(
                "$case ($action): the cache let go of ${evicted.map { it.name }} by its count, and still holds " +
                    "$stillHeld: their memory is not given back",
                stillHeld.isEmpty(),
            )
            for (h in kept) {
                assertSame("$case ($action): ${h.name} kept is the bitmap decoded", h.bitmap.get(), SpriteCache.get(context, h.resId))
            }
        }
        assertTrue("no level trimmed the cache to a budget, so no eviction was looked at", evictedByBudget > 0)
    }

    /**
     * Decodes [SPRITES] in order through [SpriteCache.get] and draws the first again, keeping only weak references:
     * nothing in the test may hold a bitmap the cache lets go of.
     */
    private fun fill(): List<Held> {
        val held = SPRITES.map { (name, resId) ->
            val bitmap = SpriteCache.get(context, resId)
            Held(name, resId, bitmap.allocationByteCount, WeakReference(bitmap))
        }
        SpriteCache.get(context, SPRITES.first().second)
        return held
    }

    /** The bitmaps the cache's own array holds now. */
    private fun heldByTheCache(): List<android.graphics.Bitmap> {
        val field = SpriteCache::class.java.getDeclaredField("bitmaps").apply { isAccessible = true }
        return (field.get(null) as Array<*>).filterIsInstance<android.graphics.Bitmap>()
    }

    private companion object {
        /** Every level `onTrimMemory` is sent with ([MemoryPressurePolicy] mirrors them). */
        val LEVELS = listOf(
            MemoryPressurePolicy.TRIM_MEMORY_RUNNING_MODERATE,
            MemoryPressurePolicy.TRIM_MEMORY_RUNNING_LOW,
            MemoryPressurePolicy.TRIM_MEMORY_RUNNING_CRITICAL,
            MemoryPressurePolicy.TRIM_MEMORY_UI_HIDDEN,
            MemoryPressurePolicy.TRIM_MEMORY_BACKGROUND,
            MemoryPressurePolicy.TRIM_MEMORY_MODERATE,
            MemoryPressurePolicy.TRIM_MEMORY_COMPLETE,
        )

        /** Eight shipped sprites of different sizes, from a bird to a car's body. */
        val SPRITES = listOf(
            "car_body_saloon" to R.drawable.car_body_saloon,
            "bird_body" to R.drawable.bird_body,
            "car_body_estate" to R.drawable.car_body_estate,
            "cloud_body" to R.drawable.cloud_body,
            "firetruck_body" to R.drawable.firetruck_body,
            "dolphin_body" to R.drawable.dolphin_body,
            "car_body_compact" to R.drawable.car_body_compact,
            "bunny_body" to R.drawable.bunny_body,
        )
    }
}
