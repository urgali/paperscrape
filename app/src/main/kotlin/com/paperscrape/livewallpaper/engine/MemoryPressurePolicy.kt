package com.paperscrape.livewallpaper.engine

/**
 * What the sprite cache should do in response to one memory-pressure signal.
 *
 * Expressed as a target rather than an operation: the cache decides how to reach it, and the
 * policy stays a pure function that can be reasoned about and tested on its own.
 */
internal enum class TrimAction {
    /** Keep everything. The signal was routine, or dropping bitmaps would cost more than it saves. */
    KEEP_ALL,

    /** Evict least-recently-used sprites until roughly half the current bytes remain. */
    TRIM_TO_HALF,

    /** Evict least-recently-used sprites until roughly a quarter of the current bytes remain. */
    TRIM_TO_QUARTER,

    /** Drop every cached sprite. Everything is re-decodable from resources. */
    RELEASE_ALL,
}

/**
 * Decides how aggressively to release cached sprites for a given `onTrimMemory` level.
 *
 * Pure and free of Android types so the mapping — which is where the real subtleties are — can be
 * unit tested directly. The level constants below mirror `android.content.ComponentCallbacks2`
 * and were checked against the API 36 `android.jar`, not copied from memory.
 *
 * ### Why the platform constants are not referenced directly
 *
 * As of API 36, `TRIM_MEMORY_RUNNING_MODERATE`, `TRIM_MEMORY_RUNNING_LOW`,
 * `TRIM_MEMORY_RUNNING_CRITICAL`, `TRIM_MEMORY_MODERATE` and `TRIM_MEMORY_COMPLETE` are all
 * **deprecated**, along with `ComponentCallbacks.onLowMemory()`. Only `TRIM_MEMORY_UI_HIDDEN`,
 * `TRIM_MEMORY_BACKGROUND` and `onTrimMemory` itself are not.
 *
 * They still have to be handled: `minSdk` is 26, and devices below API 36 continue to deliver the
 * deprecated levels. Mirroring the values here keeps that handling without importing deprecation
 * warnings into the build, and keeps this class Android-free.
 *
 * ### The trap this class exists to avoid
 *
 * **The levels are not ordered by severity.** `TRIM_MEMORY_UI_HIDDEN` is `20`, which is
 * numerically *above* `TRIM_MEMORY_RUNNING_CRITICAL` at `15`, but it is the mildest signal of the
 * set — it means "your UI is no longer visible", not "memory is short".
 *
 * A naive `if (level >= TRIM_MEMORY_RUNNING_CRITICAL)` would therefore throw away the whole cache
 * every time the user closes the settings screen. For a live wallpaper that is routine and
 * frequent, and the wallpaper carries on drawing throughout — so the next frame the user sees
 * would stall while the sprites it draws are decoded again. Hence an explicit mapping per level
 * rather than a threshold comparison.
 */
internal object MemoryPressurePolicy {

    // Mirrors ComponentCallbacks2; verified against the API 36 platform jar.
    const val TRIM_MEMORY_RUNNING_MODERATE = 5
    const val TRIM_MEMORY_RUNNING_LOW = 10
    const val TRIM_MEMORY_RUNNING_CRITICAL = 15
    const val TRIM_MEMORY_UI_HIDDEN = 20
    const val TRIM_MEMORY_BACKGROUND = 40
    const val TRIM_MEMORY_MODERATE = 60
    const val TRIM_MEMORY_COMPLETE = 80

    /**
     * Whether a [TrimAction] should also drop the GPU copies — which is all or nothing.
     *
     * **ARC-11.** The call site said the GPU textures "follow the same policy" as the bitmaps, and
     * they cannot: `GlSceneTarget.trimTextures` clears the whole atlas, because there is no LRU over
     * it to evict half of. So a [TrimAction.TRIM_TO_HALF] on a *visible* engine dropped every
     * uploaded texture and re-uploaded them on the very next frame — a re-upload spike in exchange
     * for memory the process gives back a frame later, which is the opposite of what the hint asked
     * for.
     *
     * The distinction is what the pressure level means. `RUNNING_LOW` is "give some back if it is
     * cheap", and for a visible engine's GPU copies it is not, so the atlas stays (with nothing
     * visible it maps to `RELEASE_ALL` and goes). `RUNNING_CRITICAL` and everything above it mean
     * the process is a kill candidate; there the re-upload is worth paying and the atlas goes.
     * Nothing is visible in the `RELEASE_ALL` cases, and since v5.8C the render thread honours the
     * drop while parked, on the context and surface it already holds ([GlRenderThread]'s hidden
     * branch). Until then it waited for the next prepared frame, which only a visible engine
     * reaches: the atlas stayed allocated all the while the process was a kill candidate and was
     * dropped on the first visible frame, which paid the re-upload (v5.8B comment audit).
     */
    fun dropsGpuTextures(action: TrimAction): Boolean = when (action) {
        TrimAction.KEEP_ALL, TrimAction.TRIM_TO_HALF -> false
        TrimAction.TRIM_TO_QUARTER, TrimAction.RELEASE_ALL -> true
    }

    /**
     * @param level the value passed to `onTrimMemory`.
     * @param anyEngineVisible whether any wallpaper engine is currently visible, i.e. whether a
     *   frame is about to be drawn. The settings screen's previews draw from the same cache in this
     *   process and are not counted, so when no engine is visible a full release costs the
     *   wallpaper nothing until it is shown again (the previews re-decode) — and by then the system
     *   has usually recovered the memory it was asking for.
     */
    fun actionFor(level: Int, anyEngineVisible: Boolean): TrimAction = when (level) {
        // "Your UI went away." For a wallpaper the UI is the settings screen; the wallpaper
        // itself keeps drawing. Not a memory signal at all -- see the class doc.
        TRIM_MEMORY_UI_HIDDEN -> TrimAction.KEEP_ALL

        // Process is running and healthy; the system is only hinting. Re-decoding would cost
        // more than the hint is worth.
        TRIM_MEMORY_RUNNING_MODERATE -> TrimAction.KEEP_ALL

        // Running but the device is getting low. Give back a meaningful amount while staying
        // able to draw the next frame from what is left.
        TRIM_MEMORY_RUNNING_LOW -> if (anyEngineVisible) TrimAction.TRIM_TO_HALF else TrimAction.RELEASE_ALL

        // Running and the system is about to start killing background processes. If nothing is
        // being drawn there is no reason to hold anything at all.
        TRIM_MEMORY_RUNNING_CRITICAL ->
            if (anyEngineVisible) TrimAction.TRIM_TO_QUARTER else TrimAction.RELEASE_ALL

        // The process is on the LRU kill list. Holding re-decodable bitmaps here -- up to the whole
        // ~42 MiB decoded set on the `Canvas` path -- is exactly what makes a live wallpaper a
        // preferred victim.
        TRIM_MEMORY_BACKGROUND, TRIM_MEMORY_MODERATE, TRIM_MEMORY_COMPLETE -> TrimAction.RELEASE_ALL

        // Unknown or future level. Anything at or beyond BACKGROUND severity means the process is
        // a kill candidate; below that, stay conservative rather than guessing. UI_HIDDEN is
        // already handled above, so it cannot fall through to the release branch.
        else -> if (level >= TRIM_MEMORY_BACKGROUND) TrimAction.RELEASE_ALL else TrimAction.KEEP_ALL
    }
}
