package com.paperscrape.livewallpaper.engine

/**
 * Where a `(resId, level)` pair sits in [GlTextureCache]'s table, found without scanning it.
 *
 * The render thread asks for an uploaded sprite once per blit -- about 400 times a frame -- and until
 * v5.10B the answer came from a linear scan of the whole table, which only ever grows (one entry per
 * sprite per detail level a scene has drawn it at, 250-400 for a theme) and keeps the people and the
 * cars, registered last, at its far end: `drawSprite` and the scan were 8.5 % of the render thread
 * (`simpleperf`, v5.10A). This is an open-addressing hash over the same keys, kept at most half full,
 * with primitive arrays only: nothing is boxed and nothing is allocated on a lookup, which is why it is
 * not a `Map` (`AI_PROJECT_RULES.md` 5.11).
 *
 * **It answers exactly what the scan answered.** A pair is added once, when it is uploaded, and
 * [GlTextureCache] never adds a pair it already holds, so there is one entry per key and the index it
 * gives is the one the scan would have stopped at. `SpriteEntryIndexTest` checks it against a linear
 * search over random tables, growth and clears included.
 *
 * Pure Kotlin, no Android type, so it runs on the JVM. Not thread-safe: it belongs to one render
 * thread, like the table it indexes.
 */
internal class SpriteEntryIndex(initialSlots: Int = 256) {

    init {
        require(initialSlots >= 2 && initialSlots and (initialSlots - 1) == 0) {
            "the slot count must be a power of two, got $initialSlots"
        }
    }

    private var slotResIds = IntArray(initialSlots)
    private var slotLevels = IntArray(initialSlots)

    /** The entry each slot points at, plus one; 0 marks an empty slot. */
    private var slotEntries = IntArray(initialSlots)

    /** How many keys are indexed. */
    var size = 0
        private set

    /** The entry of `(resId, level)`, or -1 when that pair was never added since the last [clear]. */
    fun find(resId: Int, level: Int): Int {
        val mask = slotEntries.size - 1
        var s = slotOf(resId, level, mask)
        while (true) {
            val e = slotEntries[s]
            if (e == 0) return -1
            if (slotResIds[s] == resId && slotLevels[s] == level) return e - 1
            s = (s + 1) and mask
        }
    }

    /**
     * Records that `(resId, level)` is entry [entry]. The caller adds a key once: adding a key that is
     * already there would leave two slots for it, and [find] would answer with the first.
     */
    fun add(resId: Int, level: Int, entry: Int) {
        require(entry >= 0) { "an entry index is never negative, got $entry" }
        if ((size + 1) * 2 > slotEntries.size) grow()
        place(resId, level, entry + 1)
        size++
    }

    /** Forgets every key; the slots are kept for the next table. */
    fun clear() {
        slotEntries.fill(0)
        size = 0
    }

    private fun place(resId: Int, level: Int, entryPlusOne: Int) {
        val mask = slotEntries.size - 1
        var s = slotOf(resId, level, mask)
        while (slotEntries[s] != 0) s = (s + 1) and mask
        slotResIds[s] = resId
        slotLevels[s] = level
        slotEntries[s] = entryPlusOne
    }

    private fun grow() {
        val oldResIds = slotResIds
        val oldLevels = slotLevels
        val oldEntries = slotEntries
        val capacity = oldEntries.size * 2
        slotResIds = IntArray(capacity)
        slotLevels = IntArray(capacity)
        slotEntries = IntArray(capacity)
        for (s in oldEntries.indices) {
            if (oldEntries[s] != 0) place(oldResIds[s], oldLevels[s], oldEntries[s])
        }
    }

    private fun slotOf(resId: Int, level: Int, mask: Int): Int {
        // Resource ids share their high bits (`0x7f08....`), so they are mixed before the mask takes
        // the low ones: a multiplicative hash, then the high half folded down.
        var h = resId * GOLDEN_RATIO_32 + level * LEVEL_MIX
        h = h xor (h ushr 15)
        return h and mask
    }

    private companion object {
        /** `2^32 / phi`, the usual multiplier of a Fibonacci hash, as a signed int. */
        const val GOLDEN_RATIO_32 = -0x61c88647
        const val LEVEL_MIX = 0x27d4eb2d
    }
}
