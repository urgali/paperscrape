package com.paperscrape.livewallpaper.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes

/**
 * Decodes each sprite resource once and caches the result, releasing it again only under real
 * memory pressure --
 * this is the whole point of the pilot sprite-rendering conversion aa asked for (houses,
 * buildings, trees, palm trees so far): decode the pixels once at asset-prep time (this app's own
 * `gen_sprites.py`, not committed -- only the resulting PNGs under `res/drawable-nodpi/` are),
 * cache the resulting [Bitmap] once here, and every frame after that is just `canvas.drawBitmap`
 * (a blit) instead of re-walking a `Path` with antialiasing on every single frame like the
 * previous vector-drawn version did.
 *
 * A plain object rather than something tied to a particular renderer instance's lifecycle:
 * [Bitmap]s decoded from resources are immutable and resource IDs are stable for the whole
 * process, so there's no reason to re-decode when [SceneObjectRenderer] itself gets recreated
 * (e.g. on every theme switch, see `PaperRenderer`'s own `theme` setter). A wallpaper process can
 * also host a preview engine and the live engine at the same time, and both share this cache.
 *
 * ## Memory pressure
 *
 * Every sprite here is re-decodable from resources, so nothing needs to be kept: the only cost of
 * dropping one is decoding it again the next time it is drawn. That makes the cache a good citizen
 * under pressure, and holding ~32 MB of it unconditionally made this process a preferred victim of
 * the low-memory killer -- which for a live wallpaper means the user's home screen goes black.
 *
 * [onTrimMemory] applies [MemoryPressurePolicy], evicting least-recently-drawn sprites down to a
 * fraction of current usage, or everything when the process is a kill candidate. Sprites are
 * dropped, never `recycle()`d: dropping the reference is enough for the platform to reclaim the
 * pixels (bitmap storage has been GC-tracked native memory since API 26, and `minSdk` is 26), and
 * `recycle()` would risk an `IllegalStateException` if anything still held a reference.
 *
 * ## Synchronisation
 *
 * Every mutating path is guarded by the cache's own monitor. This used to be unsynchronised, and
 * that was correct while the only caller was a render loop on the main looper, where `onTrimMemory`
 * is delivered too — a trim could not interleave with a draw. The GPU renderer moved drawing onto a
 * per-engine render thread, and a process can host two engines at once, so there are now up to three
 * threads reaching this object: two render threads decoding sprites and the main thread delivering
 * memory-pressure signals. The lock is the direct consequence of that change, taken with it rather
 * than after it.
 *
 * Locking here is cheap in the case that matters. A steady frame is all cache *hits*, and an
 * uncontended monitor on a hit costs a biased/thin-lock acquire and no allocation; the expensive
 * path is the decode, which happens once per sprite for the life of the process.
 *
 * `drawable-nodpi` (not a density-specific bucket like `drawable-xxhdpi`) is deliberate -- these
 * sprites are drawn at a fixed "sprite pixels per unit" scale via an explicit `canvas.scale()` at
 * draw time (see each `drawXxx` function's own comment), the same way the reference app's own
 * asset folder (also literally named `drawable-nodpi` in its decompiled resources) avoids
 * Android's automatic density upscaling working against that explicit scale.
 */
object SpriteCache {

    private val index = SpriteCacheIndex()
    private var bitmaps = arrayOfNulls<Bitmap>(32)

    /**
     * Reused across every decode. `BitmapFactory.Options` is not consulted after `decodeResource`
     * returns, so one shared instance avoids an allocation per cache miss.
     */
    private val decodeOptions = BitmapFactory.Options().apply { inScaled = false }

    /** Bytes currently held. Exposed for diagnostics; not used by the render path. */
    val cachedBytes: Long get() = index.totalBytes

    /** Number of sprites currently decoded. Exposed for diagnostics. */
    val cachedCount: Int get() = index.size

    @Synchronized
    fun get(context: Context, @DrawableRes resId: Int): Bitmap {
        val existing = index.find(resId)
        if (existing >= 0) {
            bitmaps[existing]?.let { return it }
        }
        val bitmap = BitmapFactory.decodeResource(context.resources, resId, decodeOptions)
            ?: error("SpriteCache: failed to decode resource $resId")
        val slot = index.put(resId, bitmap.allocationByteCount)
        if (slot >= bitmaps.size) {
            bitmaps = bitmaps.copyOf(maxOf(slot + 1, bitmaps.size * 2))
        }
        bitmaps[slot] = bitmap
        return bitmap
    }

    /**
     * Applies [MemoryPressurePolicy] for one `onTrimMemory` level.
     *
     * @param anyEngineVisible whether any wallpaper engine is currently drawing. Nothing else in
     *   this process draws, so with no visible engine a full release costs nothing until the
     *   wallpaper is shown again.
     */
    @Synchronized
    fun onTrimMemory(level: Int, anyEngineVisible: Boolean) {
        when (MemoryPressurePolicy.actionFor(level, anyEngineVisible)) {
            TrimAction.KEEP_ALL -> Unit
            TrimAction.TRIM_TO_HALF -> evictTo(index.totalBytes / 2)
            TrimAction.TRIM_TO_QUARTER -> evictTo(index.totalBytes / 4)
            TrimAction.RELEASE_ALL -> clear()
        }
    }

    /**
     * Drops the decoded pixels for [resId], which the caller has finished with.
     *
     * The GPU renderer calls this once it has uploaded a sprite into a texture: from then on the
     * pixels live on the GPU and the CPU copy is a duplicate, and holding both was costing up to
     * ~17 MB of heap for no benefit. Re-decoding is always available — that is the same property
     * that makes memory-pressure eviction safe — so the only cost of being wrong here is one
     * decode.
     *
     * Deliberately *not* folded into [get]: the `Canvas` backend, which the settings preview and
     * the EGL fallback both use, needs the bitmap on every single frame and must not release it.
     * Who releases and when is the caller's decision, not this cache's.
     */
    @Synchronized
    fun release(@DrawableRes resId: Int) {
        val slot = index.remove(resId)
        if (slot >= 0) bitmaps[slot] = null
    }

    /** Drops every cached sprite. They are re-decoded lazily the next time they are drawn. */
    @Synchronized
    fun clear() {
        java.util.Arrays.fill(bitmaps, null)
        index.clear()
    }

    /**
     * Evicts least-recently-drawn sprites until at most [budgetBytes] remain.
     *
     * Least-recently-drawn rather than largest-first on purpose: the biggest sprites are seasonal
     * set pieces such as the sleigh, which are drawn rarely and so fall out naturally, while a
     * size-first policy would evict them and then immediately re-decode them mid-effect.
     */
    private fun evictTo(budgetBytes: Long) {
        while (index.totalBytes > budgetBytes) {
            val slot = index.evictLeastRecentlyUsed()
            if (slot < 0) break
            bitmaps[slot] = null
        }
    }
}
