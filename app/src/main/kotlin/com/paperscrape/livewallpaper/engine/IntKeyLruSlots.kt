package com.paperscrape.livewallpaper.engine

/**
 * [IntLruSlots] for a key that needs more than one `Int`.
 *
 * A gradient is identified by four or five numbers, not one, and there is no way to fold them into
 * a single `Int` that is both collision-free and cheap. This keeps them side by side in one flat
 * `IntArray` and compares all [KEY_WIDTH] components, so a hit is an exact match rather than a hash
 * agreement — a cache that occasionally handed back the wrong gradient would be a rendering bug,
 * and the only way to be sure it cannot is not to hash at all.
 *
 * **Why not generalise [IntLruSlots] instead.** That class is the one [TintFilterCache] runs on, in
 * the hottest loop in the renderer, with a single-`Int` key it compares in one instruction. Widening
 * it would make every tint lookup pay for four comparisons it does not need. Two small classes cost
 * less than one general one here.
 *
 * Everything else matches [IntLruSlots] exactly: fixed capacity, exact LRU eviction, a linear scan
 * rather than a hash probe, and no allocation on any path. Keys are passed as scalars for the same
 * reason — an `IntArray` parameter would allocate on every lookup, which is the cost this exists to
 * remove.
 *
 * Not thread-safe. Callers use it from one thread only.
 *
 * Deliberately free of Android types so it can be unit tested directly; the cache built on top of
 * it holds `android.graphics` objects and therefore cannot be.
 */
internal class IntKeyLruSlots(val capacity: Int) {

    init {
        require(capacity > 0) { "capacity must be positive, was $capacity" }
    }

    /** [KEY_WIDTH] components per slot, laid out contiguously. */
    private val keys = IntArray(capacity * KEY_WIDTH)

    /** Slot indices, most-recently-used first. Only the first [size] entries are meaningful. */
    private val order = IntArray(capacity)

    var size: Int = 0
        private set

    /**
     * Returns the slot index holding this key, marking it most-recently-used, or `-1` if absent.
     */
    fun find(k0: Int, k1: Int, k2: Int, k3: Int, k4: Int): Int {
        for (position in 0 until size) {
            val slot = order[position]
            val base = slot * KEY_WIDTH
            if (keys[base] == k0 &&
                keys[base + 1] == k1 &&
                keys[base + 2] == k2 &&
                keys[base + 3] == k3 &&
                keys[base + 4] == k4
            ) {
                moveToFront(position)
                return slot
            }
        }
        return -1
    }

    /**
     * Reserves a slot for this key and returns its index, evicting the least-recently-used entry if
     * the cache is already full. The caller is responsible for writing the value into that slot.
     *
     * Calling this for a key that is already present will store it a second time; call [find] first.
     */
    fun reserve(k0: Int, k1: Int, k2: Int, k3: Int, k4: Int): Int {
        val slot: Int
        val position: Int
        if (size < capacity) {
            slot = size
            position = size
            order[position] = slot
            size++
        } else {
            position = capacity - 1
            slot = order[position]
        }
        val base = slot * KEY_WIDTH
        keys[base] = k0
        keys[base + 1] = k1
        keys[base + 2] = k2
        keys[base + 3] = k3
        keys[base + 4] = k4
        moveToFront(position)
        return slot
    }

    fun clear() {
        size = 0
    }

    private fun moveToFront(position: Int) {
        if (position == 0) return
        val slot = order[position]
        for (i in position downTo 1) order[i] = order[i - 1]
        order[0] = slot
    }

    companion object {
        /**
         * Five, because the widest key here is a radial gradient's: centre x, centre y, radius,
         * colour and centre alpha. A linear gradient uses four and leaves the fifth zero.
         */
        const val KEY_WIDTH = 5
    }
}
