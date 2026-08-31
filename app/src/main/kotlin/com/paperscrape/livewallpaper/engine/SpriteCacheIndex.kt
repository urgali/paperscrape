package com.paperscrape.livewallpaper.engine

/**
 * Bookkeeping for [SpriteCache]: which resource ids are cached, how many bytes each one costs,
 * and which one has gone longest without being drawn.
 *
 * Split out from the cache itself so the eviction logic — the part with the interesting edge
 * cases — is pure Kotlin with no Android types and can be unit tested directly. [SpriteCache]
 * keeps the actual `Bitmap`s in a parallel array indexed by the slot numbers this class hands out.
 *
 * ### Why not a map
 *
 * The previous cache was a `ConcurrentHashMap<Int, Bitmap>`. Kotlin boxes the `Int` key on every
 * single lookup, and resource ids are far outside `Integer`'s small-value cache, so **every sprite
 * blit allocated an `Integer`** — hundreds per second on the draw path. That allocation is
 * invisible at the call site (it happens inside `Integer.valueOf`, not as a `new` opcode there),
 * which is why it survived the earlier per-frame allocation audit.
 *
 * Keys live in an `IntArray` here, so lookups allocate nothing.
 *
 * ### Cost of the linear scan
 *
 * [find] scans the key array. With ~118 sprites that is a scan over a contiguous `IntArray` — a
 * couple of cache lines — against the alternative of an allocation plus a hash. Growth doubles the
 * arrays, which allocates, but only a handful of times over the life of the process and never on a
 * steady-state frame.
 *
 * Not thread-safe, **and it does not need to be**: every path into it is inside a `@Synchronized`
 * method of [SpriteCache], which is the object that owns it and the only one that holds a reference.
 *
 * ARC-12: this used to say "rendering and memory callbacks both arrive on the main looper", which
 * stopped being true when rendering moved to a per-engine render thread -- up to three threads reach
 * [SpriteCache], as its own comment says. The conclusion was right and the reason was stale, which
 * in a codebase that argues its way out of locks is the dangerous half.
 */
internal class SpriteCacheIndex(initialCapacity: Int = 32) {

    private var keys = IntArray(initialCapacity)
    private var sizes = IntArray(initialCapacity)

    /** Slot numbers, most-recently-used first. Only the first [size] entries are meaningful. */
    private var order = IntArray(initialCapacity)

    /** Slot numbers freed by eviction, available for reuse. Only the first [freeCount] matter. */
    private var freeSlots = IntArray(initialCapacity)
    private var freeCount = 0

    var size: Int = 0
        private set

    /** Total bytes of everything currently indexed. `Long` because 118 sprites is ~32 MB today. */
    var totalBytes: Long = 0L
        private set

    /** Highest [totalBytes] reached since the last [clear]. Useful for diagnostics and tests. */
    var peakBytes: Long = 0L
        private set

    /** Slot holding [key], marking it most-recently-used, or `-1` if absent. */
    fun find(key: Int): Int {
        for (position in 0 until size) {
            val slot = order[position]
            if (keys[slot] == key) {
                moveToFront(position)
                return slot
            }
        }
        return -1
    }

    /**
     * Records [key] at [sizeBytes] and returns the slot to store its bitmap in.
     *
     * The caller is expected to have called [find] first; adding a key twice would double-count
     * its bytes and leave an unreachable entry.
     */
    fun put(key: Int, sizeBytes: Int): Int {
        val slot = if (freeCount > 0) freeSlots[--freeCount] else size
        if (slot >= keys.size) grow()
        keys[slot] = key
        sizes[slot] = sizeBytes
        order[size] = slot
        size++
        totalBytes += sizeBytes
        if (totalBytes > peakBytes) peakBytes = totalBytes
        moveToFront(size - 1)
        return slot
    }

    /**
     * Drops the least-recently-used entry and returns the slot freed, or `-1` if nothing is left.
     *
     * Returns one slot per call rather than a list so that trimming to a byte budget allocates
     * nothing: the caller loops until [totalBytes] is low enough.
     */
    fun evictLeastRecentlyUsed(): Int {
        if (size == 0) return -1
        val position = size - 1
        val slot = order[position]
        totalBytes -= sizes[slot]
        sizes[slot] = 0
        keys[slot] = 0
        size--
        if (freeCount >= freeSlots.size) freeSlots = freeSlots.copyOf(freeSlots.size * 2)
        freeSlots[freeCount++] = slot
        return slot
    }

    /**
     * Drops [key] if present and returns the slot freed, or `-1` if it was not held.
     *
     * Distinct from [evictLeastRecentlyUsed] because the caller is not trimming to a budget: the
     * GPU renderer uploads a sprite to a texture and then has no further use for the decoded
     * pixels, so it releases that one entry by name regardless of how recently it was drawn.
     */
    fun remove(key: Int): Int {
        for (position in 0 until size) {
            val slot = order[position]
            if (keys[slot] != key) continue
            totalBytes -= sizes[slot]
            sizes[slot] = 0
            keys[slot] = 0
            // Close the gap in the recency order; the entry behind it moves up one place.
            System.arraycopy(order, position + 1, order, position, size - position - 1)
            size--
            if (freeCount >= freeSlots.size) freeSlots = freeSlots.copyOf(freeSlots.size * 2)
            freeSlots[freeCount++] = slot
            return slot
        }
        return -1
    }

    /** Forgets everything. Backing arrays are kept, so this allocates nothing. */
    fun clear() {
        for (position in 0 until size) {
            val slot = order[position]
            keys[slot] = 0
            sizes[slot] = 0
        }
        size = 0
        totalBytes = 0L
        freeCount = 0
        // Every slot is free again; rebuild the free list rather than tracking it incrementally.
        if (freeSlots.size < keys.size) freeSlots = IntArray(keys.size)
        for (slot in keys.indices) freeSlots[slot] = slot
        freeCount = keys.size
    }

    /** Test/diagnostic helper: the key held in [slot]. */
    internal fun keyAt(slot: Int): Int = keys[slot]

    /** Test/diagnostic helper: the slot at [position] in most-recently-used order. */
    internal fun slotAtOrder(position: Int): Int = order[position]

    private fun moveToFront(position: Int) {
        if (position == 0) return
        val slot = order[position]
        System.arraycopy(order, 0, order, 1, position)
        order[0] = slot
    }

    private fun grow() {
        val newCapacity = keys.size * 2
        keys = keys.copyOf(newCapacity)
        sizes = sizes.copyOf(newCapacity)
        order = order.copyOf(newCapacity)
    }
}
