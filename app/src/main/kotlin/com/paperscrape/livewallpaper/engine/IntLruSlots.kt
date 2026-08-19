package com.paperscrape.livewallpaper.engine

/**
 * A fixed-capacity, allocation-free mapping from an `Int` key to a slot index, with
 * least-recently-used eviction.
 *
 * This exists to let a cache in the render loop be *bounded*. The obvious alternative --
 * `HashMap<Int, T>` -- fails twice over in a draw path: it boxes the `Int` key on every single
 * lookup (an allocation per call, which is exactly what the cache is meant to remove), and it
 * grows without limit.
 *
 * The lookup is a linear scan rather than a hash probe. That is deliberate: at the capacities
 * this is used with, a scan over a contiguous `IntArray` is a handful of predictable comparisons
 * over one or two cache lines, which is far cheaper than the native object allocation it
 * replaces, and it keeps eviction order exact instead of approximate.
 *
 * Not thread-safe. Callers use it from the render thread only.
 *
 * Deliberately free of Android types so it can be unit tested directly -- the cache built on top
 * of it holds `android.graphics` objects and therefore cannot be.
 */
internal class IntLruSlots(val capacity: Int) {

    init {
        require(capacity > 0) { "capacity must be positive, was $capacity" }
    }

    /** Key stored in each slot. Only meaningful for slots currently in [order]. */
    private val keys = IntArray(capacity)

    /** Slot indices, most-recently-used first. Only the first [size] entries are meaningful. */
    private val order = IntArray(capacity)

    var size: Int = 0
        private set

    /**
     * Returns the slot index holding [key], marking it most-recently-used, or `-1` if absent.
     */
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
     * Reserves a slot for [key] and returns its index, evicting the least-recently-used entry if
     * the cache is already full. The caller is responsible for writing the value into that slot.
     *
     * Calling this for a key that is already present will store it a second time; call [find]
     * first.
     */
    fun reserve(key: Int): Int {
        val slot: Int
        val position: Int
        if (size < capacity) {
            slot = size
            position = size
            order[position] = slot
            size++
        } else {
            // Evict the least-recently-used entry, which sits at the end of the order list.
            position = capacity - 1
            slot = order[position]
        }
        keys[slot] = key
        moveToFront(position)
        return slot
    }

    /** Drops every entry. The backing arrays are kept, so this allocates nothing. */
    fun clear() {
        size = 0
    }

    /** Test/diagnostic helper: the key currently held in [slot]. */
    internal fun keyAt(slot: Int): Int = keys[slot]

    /** Test/diagnostic helper: the slot at [position] in most-recently-used order. */
    internal fun slotAtOrder(position: Int): Int = order[position]

    private fun moveToFront(position: Int) {
        if (position == 0) return
        val slot = order[position]
        System.arraycopy(order, 0, order, 1, position)
        order[0] = slot
    }
}
