package com.paperscrape.livewallpaper.engine

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter

/**
 * Reuses [PorterDuffColorFilter] instances instead of constructing one per sprite per frame.
 *
 * Before this cache, every tinted sprite blit allocated a fresh filter. With every house, tree,
 * building, cloud and decoration drawn three times per frame (one copy per wrap tile), that was
 * on the order of hundreds of short-lived native-backed objects per second, feeding a steady
 * stream of garbage into a loop that is supposed to be doing nothing but blitting.
 *
 * **Why this is bounded, and why a plain map would not be.** Tint colours are not the handful of
 * palette values the user picked: they are day/night blends
 * (`blendColor(night, day, dayBlend)`), so in principle a new colour can appear on any frame. An
 * unbounded `HashMap<Int, PorterDuffColorFilter>` would therefore grow for as long as the
 * wallpaper runs. [IntLruSlots] caps the cache at [CAPACITY] entries with exact LRU eviction, so
 * memory is constant regardless of uptime, theme switching or how many colours the user cycles
 * through.
 *
 * **Why the hit rate is nonetheless high.** `dayBlend` is pinned at exactly `1f` through the
 * middle of the day and exactly `0f` through the middle of the night (see
 * `SunPositionCalculator.smoothEdge`), so for most of the cycle every tint colour is stable
 * frame to frame and every lookup hits. Only during the dawn and dusk ramps does the blend move,
 * and even then the *quantised 8-bit result* changes only every few hundred frames per colour.
 * Misses are therefore rare and, when they happen, cost exactly what the old code paid on every
 * single call.
 *
 * The mode is fixed to `MULTIPLY`, matching the single tint convention used throughout the
 * renderer (see [SpriteBlitter]'s own doc comment for why that mode was chosen over `SRC_IN`).
 * If a second mode is ever needed, it must become part of the cache key — a filter is defined by
 * colour *and* mode, and silently reusing one across modes would be a rendering bug.
 *
 * Thread-safe, and it has to be: this is a process-wide object and a process can host two engines
 * with a render thread each. ARC-12: this used to say "not thread-safe, used only from the render
 * thread", which was false in both halves -- every entry point below is `@Synchronized`, and
 * [GradientShaderCache]'s own comment already said so from the other side. Unlike [SpriteCache], whose contents are
 * immutable and safe to share.
 */
object TintFilterCache {

    /**
     * Comfortably above the number of distinct tint colours a single frame uses (roughly 30-60
     * across every object category, part and colour variant), so a full frame never evicts an
     * entry it is still going to need. Constant memory: 64 filters plus two `IntArray(64)`.
     */
    private const val CAPACITY = 64

    private val slots = IntLruSlots(CAPACITY)
    private val filters = arrayOfNulls<PorterDuffColorFilter>(CAPACITY)

    /**
     * Returns a `MULTIPLY` colour filter for [color], reusing a cached instance when possible.
     *
     * The returned filter must be treated as immutable and must not be retained by the caller
     * beyond the current draw call — it may be handed to a different colour after eviction.
     * Assigning it to a `Paint` for the duration of one blit, which is how the renderer uses it,
     * is safe.
     */
    @Synchronized
    fun get(color: Int): PorterDuffColorFilter {
        val existing = slots.find(color)
        if (existing >= 0) {
            // A slot found by `find` always has its filter populated, but fall through to
            // creation rather than asserting: a null here would mean a bug in this class, and
            // silently rendering correctly beats crashing the wallpaper.
            filters[existing]?.let { return it }
        }
        val slot = if (existing >= 0) existing else slots.reserve(color)
        val filter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
        filters[slot] = filter
        return filter
    }

    /** Drops every cached filter. Intended for memory-pressure handling. */
    @Synchronized
    fun clear() {
        slots.clear()
        filters.fill(null)
    }
}
