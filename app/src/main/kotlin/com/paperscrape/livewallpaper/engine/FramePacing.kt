package com.paperscrape.livewallpaper.engine

/**
 * When the render loop starts its next frame: the arithmetic of [GlRenderThread]'s pacing, kept
 * free of Android so a test can run it over any refresh rate.
 *
 * ## What it replaced, and why that stuttered
 *
 * The loop used to sleep `33 ms - cost` after every frame. The display does not refresh on that
 * clock: a frame reaches the screen on a refresh tick, and two ticks on the BV6600 are **32.5 ms**
 * (the panel runs at 61.45 Hz by SurfaceFlinger's own vsync model, `--dispsync`, while the
 * platform reports 60). A loop a little slower than two ticks slides
 * one tick later every few frames, and the frame it slides on stays on screen for three ticks
 * instead of two: about one frame in ten held for ~49 ms, which is what SurfaceFlinger counted in
 * every build from v5.3 to v5.7.
 *
 * ## What it does instead
 *
 * Every frame starts on a refresh tick of the display's own grid -- [VsyncGrid] measures where
 * the ticks fall and how far apart they are -- and the next frame [ticksPerFrame] ticks later:
 * two at 60 Hz, three at 90, four at 120. About 30 frames a second on any of them, and **the same
 * number of ticks between every pair of frames**, which is the whole point. The loop sleeps to the
 * predicted tick rather than being woken on it, so the display's clock costs one wake-up a frame,
 * the same as the sleep it replaced.
 *
 * The cost of the same number of ticks every time is that the rate is whatever that number of
 * ticks gives: about 30.7 frames a second on this panel, 4 % more frames than the sliding loop.
 */
internal object FramePacing {

    /** The cadence the scene was tuned at: 30 frames a second. */
    const val FRAME_INTERVAL_NANOS = 1_000_000_000L / 30

    /** What the loop sleeps when it has no grid to pace against: the old `33 ms - cost`. */
    const val FALLBACK_INTERVAL_MS = 33L

    /** How many refresh ticks one frame lasts: the whole number nearest 30 frames a second. */
    fun ticksPerFrame(periodNanos: Long): Int =
        ((FRAME_INTERVAL_NANOS + periodNanos / 2) / periodNanos).toInt().coerceAtLeast(1)

    /** The tick of the grid ([gridTickNanos], [periodNanos]) nearest to [t]. */
    fun nearestTick(t: Long, gridTickNanos: Long, periodNanos: Long): Long =
        gridTickNanos + Math.floorDiv(t - gridTickNanos + periodNanos / 2, periodNanos) * periodNanos

    /** The first tick of the grid at or after [t]. */
    fun tickAtOrAfter(t: Long, gridTickNanos: Long, periodNanos: Long): Long =
        gridTickNanos + Math.floorDiv(t - gridTickNanos + periodNanos - 1, periodNanos) * periodNanos

    /**
     * The tick the next frame starts on.
     *
     * [anchorTickNanos] is the tick the frame just drawn was started on, or 0 when it was not
     * started on one -- the first frame after the loop parked, or one paced without a grid. With an
     * anchor, the next frame is [ticksPerFrame] ticks after it, the anchor first snapped onto the
     * grid as last measured so that a period measured a microsecond short cannot accumulate into a
     * drift. Without one, the next frame is the tick nearest a frame interval after
     * [frameStartNanos], and from there it stays on the grid. A frame that ran past the tick it was
     * due to hand over on ([nowNanos] later than it) starts the next one on the first tick after
     * it finished: one tick lost, once, and still on the grid.
     */
    /**
     * What the render loop does after a frame: how long to sleep, and the tick the next frame is
     * anchored on (0 when it is not on one). With a [grid] it sleeps to [nextTick]; **with none**
     * -- the first second, the first second after parking, or a device whose `Choreographer`
     * never answers -- it sleeps what is left of [FALLBACK_INTERVAL_MS] after this frame's cost,
     * exactly as the loop did before v5.8C, and anchors nothing.
     */
    fun plan(grid: VsyncGrid.Grid?, anchorTickNanos: Long, frameStartNanos: Long, nowNanos: Long): Plan {
        if (grid == null) {
            val costMs = (nowNanos - frameStartNanos) / 1_000_000L
            return Plan((FALLBACK_INTERVAL_MS - costMs) * 1_000_000L, 0L)
        }
        val next = nextTick(anchorTickNanos, frameStartNanos, nowNanos, grid.tickNanos, grid.periodNanos)
        return Plan(next - nowNanos, next)
    }

    /** See [plan]. A non-positive [sleepNanos] means "start the next frame at once". */
    class Plan(val sleepNanos: Long, val anchorTickNanos: Long)

    /**
     * When the frame started on [frameTickNanos] should reach the screen, for
     * `eglPresentationTimeANDROID`; [PRESENT_AUTO] ("as soon as it is ready") with no grid or no
     * tick.
     *
     * **Why a time at all (measured on the BV6600, v5.8C/D).** Sleeping to the tick fixes when a
     * frame *starts*, not which refresh shows it. Without a time SurfaceFlinger takes the frame for
     * the first refresh after it is queued -- on this phone ~4 ms after the queue, before the GPU has
     * finished it -- and the display shows it on that refresh only if the GPU finishes ~1.5 ms before
     * it. The usual margin was 2-4 ms (`--timestats` acquire2present, median 3 ms), so a frame a
     * little slower than usual waited a whole refresh more: 48 ms then 16 ms on screen. With a time
     * every frame is taken for the later refresh, which its GPU work makes with ~18 ms to spare; the
     * Android frame-pacing library (Swappy) does the same for the same reason. The price is one
     * refresh more between a frame's start and the screen (queue to screen 27 ms against 11,
     * medians), which only a launcher that sends wallpaper offsets could show.
     *
     * **Measured (v5.8D, perf build, Autumn, three alternated pairs of 3 x 60 s): intervals of 40 ms
     * or more 6.7 % without the time and 2.9 % with it, at 45.4 -> 46.3 % of a core.**
     *
     * **The slips that remain are not the GPU** (v5.8D, system traces,
     * `consegna_v5_8d/registri/30_*`, `32_*`). Those frames were ready ~35 ms early and taken for
     * composition on time; what is late is SurfaceFlinger's call into the display's composer (HWC).
     * When that call ends more than ~5.5 ms after the frame is taken, about 1.8 ms before the refresh,
     * the composition goes out one refresh later. With this time the call runs while the loop starts
     * the next frame on the same cluster of cores, which makes both a little slower (the call 3.6 ms
     * against 3.3; the loop +0.3 ms of CPU a frame, nearly all of the time's CPU cost). A time one
     * refresh later, measured and not shipped, removes the overlap and that CPU and leaves 2.0 % of
     * slips (4.2 % with this time in the same session), at one more refresh of latency: not zero,
     * because why the composer's call is sometimes ~5 ms long is not known. The model in
     * `FramePacingTest` has no composer time, which is why it predicts none.
     *
     * **The time**: the next frame's tick less a quarter period. Not the tick itself: where the
     * vsync timestamps coincide with the panel's refresh, a request exactly on one could land
     * either side of it frame by frame. A quarter period before it keeps every refresh at least
     * three quarters of a period after the frame's tick out of reach -- the one the frame would
     * otherwise jump to when it is quick -- and nothing here knows the phone: the period and the
     * tick are the measured grid's.
     */
    fun presentAt(grid: VsyncGrid.Grid?, frameTickNanos: Long): Long {
        if (grid == null || frameTickNanos == 0L) return PRESENT_AUTO
        val period = grid.periodNanos
        return frameTickNanos + ticksPerFrame(period) * period - period / 4
    }

    /** `NATIVE_WINDOW_TIMESTAMP_AUTO`: the compositor shows the frame as soon as it is ready. */
    const val PRESENT_AUTO = Long.MIN_VALUE

    fun nextTick(
        anchorTickNanos: Long,
        frameStartNanos: Long,
        nowNanos: Long,
        gridTickNanos: Long,
        periodNanos: Long,
    ): Long {
        val next = if (anchorTickNanos != 0L) {
            nearestTick(anchorTickNanos, gridTickNanos, periodNanos) + ticksPerFrame(periodNanos) * periodNanos
        } else {
            nearestTick(frameStartNanos + FRAME_INTERVAL_NANOS, gridTickNanos, periodNanos)
        }
        return if (next >= nowNanos) next else tickAtOrAfter(nowNanos, gridTickNanos, periodNanos)
    }
}
