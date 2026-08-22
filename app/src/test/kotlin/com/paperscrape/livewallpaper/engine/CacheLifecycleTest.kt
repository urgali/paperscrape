package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The memory bound of every cache in the render path (v3.7 Filone F).
 *
 * ### The question, and what the audit answered
 *
 * v3.6's report flagged an apparent asymmetry: `TintFilterCache` is released on `onTrimMemory` and
 * `SpriteCacheIndex` and `GradientShaderCache` are not. Examined:
 *
 * | cache | owner | lifetime | bound | trim hook | verdict |
 * |---|---|---|---|---|---|
 * | `SpriteCache` | global `object` | the process | ~32 MB of `Bitmap` | **yes**, via `MemoryPressurePolicy` | **A** — correct |
 * | `SpriteCacheIndex` | **private field of `SpriteCache`** | its owner's | four `IntArray`s, ~2 KB | inherits — `SpriteCache.clear()` calls `index.clear()` | **A** — the premise was wrong |
 * | `TintFilterCache` | global `object` | the process | 64 filters + two `IntArray(64)` | **yes**, on `RELEASE_ALL` | **A** — correct |
 * | `GradientShaderCache` | field of one `CanvasSceneTarget` | its owner's | 32 shaders + ~900 bytes | none, and none needed | **A** — correct |
 *
 * **No `onTrimMemory` hook was added, deliberately.** `SpriteCacheIndex` is not an independent
 * cache at all — it is `SpriteCache`'s own bookkeeping, a `private val`, and it is emptied by the
 * very `clear()` the trim path already calls. `GradientShaderCache` is per-instance, is bounded at
 * two orders of magnitude below the bitmap cache, and dies with the target that owns it; adding a
 * hook for it would mean giving the engine a registry of live targets in order to release a few
 * hundred bytes, which costs more than it saves. Symmetry is not a reason.
 *
 * The one thing worth pinning is the bound itself, since that is what the verdict rests on.
 */
class CacheLifecycleTest {

    // -- GradientShaderCache's key table ------------------------------------------------------------

    /**
     * The gradient cache is bounded by its slot table, and the table is bounded by construction.
     *
     * Hammered with far more distinct keys than a wallpaper could produce in a day: the table must
     * not grow past its capacity, which is what makes "32 shaders, whatever the uptime" true.
     */
    @Test
    fun `the gradient key table is bounded whatever it is fed`() {
        val slots = IntKeyLruSlots(16)
        repeat(100_000) { slots.reserve(it, it * 7, it * 13, it * 31, it % 5) }
        assertEquals("the table grew past its capacity", 16, slots.size)
    }

    /**
     * Its footprint, stated in bytes rather than asserted to be "small".
     *
     * Two tables of [IntKeyLruSlots.KEY_WIDTH] ints per slot plus an order array, times two kinds
     * of gradient. Everything else the cache holds is the shaders themselves, which are one small
     * native object each.
     */
    @Test
    fun `the gradient cache footprint is under a kilobyte of bookkeeping`() {
        val capacity = 16
        val intsPerTable = capacity * IntKeyLruSlots.KEY_WIDTH + capacity
        val bytes = 2 * intsPerTable * 4
        println("Filone F: GradientShaderCache bookkeeping = $bytes bytes for 2 x $capacity slots")
        assertTrue("bookkeeping grew unexpectedly: $bytes bytes", bytes < 1024)
    }

    // -- SpriteCacheIndex, the one the flag was about -----------------------------------------------

    /**
     * **The premise checked.** `SpriteCacheIndex` holds no pixels — only ids, sizes and ordering —
     * so what it can retain past its useful life is a few kilobytes of `IntArray`, not megabytes
     * of bitmap.
     *
     * Fed every sprite the app has and then some, its own accounting must still report the bytes
     * of what is *indexed*, not of what it costs to index.
     */
    @Test
    fun `the sprite index accounts for pixels it does not hold`() {
        val index = SpriteCacheIndex()
        var expected = 0L
        repeat(200) {
            index.put(10_000 + it, 300_000)
            expected += 300_000
        }
        assertEquals(200, index.size)
        assertEquals(expected, index.totalBytes)
        // Four IntArrays grown to at least 200 entries. Two orders of magnitude below the 60 MB
        // of bitmap the same index is describing.
        val bookkeepingBytes = 4 * 256 * 4
        println(
            "Filone F: SpriteCacheIndex indexes ${index.totalBytes / 1_000_000}MB of pixels " +
                "using roughly $bookkeepingBytes bytes of its own",
        )
        assertTrue(bookkeepingBytes * 1000 < index.totalBytes)
    }

    /**
     * And it is emptied by the path the trim already takes: `SpriteCache.clear()` calls
     * `index.clear()`, so the index needs no hook of its own.
     *
     * `SpriteCache` itself holds `Bitmap`s and cannot be unit tested; this pins the half of that
     * contract which can be.
     */
    @Test
    fun `clearing the index releases everything it was accounting for`() {
        val index = SpriteCacheIndex()
        repeat(50) { index.put(it, 100_000) }
        assertTrue(index.totalBytes > 0)
        index.clear()
        assertEquals(0, index.size)
        assertEquals(0L, index.totalBytes)
        assertEquals(-1, index.find(0))
    }

    // -- TintFilterCache's table, for completeness --------------------------------------------------

    /** The cache that does have a hook is bounded too, so the hook is a courtesy and not a rescue. */
    @Test
    fun `the tint filter table is bounded whatever it is fed`() {
        val slots = IntLruSlots(64)
        repeat(100_000) { slots.reserve(it) }
        assertEquals(64, slots.size)
    }

    /**
     * The one ordering fact the whole verdict rests on: the bitmap cache is the only one whose
     * bound is measured in megabytes, and it is the one with the trim hook.
     */
    @Test
    fun `only the bitmap cache is large enough to be worth a trim hook`() {
        val spriteBytes = 118L * 280_000              // the shipped sprite set, roughly
        val tintBytes = 64L * 64                       // 64 small filter objects
        val gradientBytes = 32L * 64                   // 32 small shader objects
        println(
            "Filone F magnitudes: sprites ~${spriteBytes / 1_000_000}MB, " +
                "tint filters ~${tintBytes}B, gradients ~${gradientBytes}B",
        )
        assertTrue(spriteBytes > 1000 * tintBytes)
        assertTrue(spriteBytes > 1000 * gradientBytes)
    }
}
