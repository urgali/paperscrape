package com.paperscrape.livewallpaper.engine

import android.os.Handler
import android.os.HandlerThread
import android.view.Choreographer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Where the display's refresh ticks fall: one tick's time and the period between them, measured
 * from `Choreographer` about once a second, for a render loop that paces itself by sleeping.
 *
 * `Choreographer` only calls back on a `Looper`, and [GlRenderThread] runs its own loop, so the
 * measuring happens on a small thread of its own, `PaperScrapeVsync`. It asks for **three
 * consecutive** frame callbacks, which give a tick and two periods -- the shorter one is the
 * period, because a callback this thread was too late for makes one gap two ticks long -- publishes
 * them, and asks again a second later. The render thread never waits on this thread: it reads the last
 * measurement ([current]) and sleeps to the next predicted tick ([FramePacing.nextTick]).
 *
 * **Why not a callback on every frame.** That was the first version, and on the BV6600 it cost
 * **4 % of a core** by itself -- a wake-up for the delayed request and another for the callback,
 * about 94 a second -- which is more than the frames it paced. Three callbacks a second cost
 * nothing measurable, and a period re-measured every second cannot drift: the render thread snaps its
 * own schedule back onto the latest grid every frame.
 *
 * It measures only while somebody reads it: a grid nobody has asked for in [IDLE_AFTER_NANOS]
 * stops being re-measured, so a wallpaper that is not drawing costs no callbacks, and the next
 * [current] starts it again. A grid older than [STALE_AFTER_NANOS] is not returned at all -- after
 * the loop has been parked, the first frames pace the old way until a fresh one arrives.
 */
internal class VsyncGrid : Choreographer.FrameCallback {

    /** One measurement: a tick of the display, and the period that follows it. */
    class Grid(val tickNanos: Long, val periodNanos: Long)

    private val thread = HandlerThread("PaperScrapeVsync")
    @Volatile private var handler: Handler? = null

    /** Created on [thread] by the first sample, because `Choreographer` is per `Looper`. */
    private var choreographer: Choreographer? = null

    /** The ticks of the triple being measured so far. Touched only on [thread]. */
    private val ticks = LongArray(3)
    private var tickCount = 0

    @Volatile private var grid: Grid? = null
    @Volatile private var lastReadNanos = 0L
    private val sampling = AtomicBoolean(false)

    private val sample = Runnable {
        val c = choreographer ?: Choreographer.getInstance().also { choreographer = it }
        tickCount = 0
        c.postFrameCallback(this)
    }

    fun start() {
        thread.start()
        handler = Handler(thread.looper)
    }

    fun quit() {
        handler?.removeCallbacksAndMessages(null)
        handler = null
        thread.quitSafely()
    }

    override fun doFrame(frameTimeNanos: Long) {
        ticks[tickCount++] = frameTimeNanos
        if (tickCount < ticks.size) {
            choreographer?.postFrameCallback(this)
            return
        }
        tickCount = 0
        measure(ticks[0], ticks[1], ticks[2])?.let { grid = it }
        if (System.nanoTime() - lastReadNanos > IDLE_AFTER_NANOS) {
            sampling.set(false)
            return
        }
        handler?.postDelayed(sample, SAMPLE_EVERY_MS)
    }

    /**
     * The last grid measured, or null when there is none younger than [STALE_AFTER_NANOS].
     * Reading it keeps the measuring going, and starts it again if it had stopped.
     */
    fun current(nowNanos: Long): Grid? {
        lastReadNanos = nowNanos
        if (sampling.compareAndSet(false, true)) handler?.post(sample)
        return fresh(grid, nowNanos)
    }

    internal companion object {
        const val SAMPLE_EVERY_MS = 1_000L
        const val IDLE_AFTER_NANOS = 2_000_000_000L
        const val STALE_AFTER_NANOS = 3_000_000_000L

        /** 500 Hz and 20 Hz: a period outside them is not a display's. */
        const val MIN_PERIOD_NANOS = 2_000_000L
        const val MAX_PERIOD_NANOS = 50_000_000L

        /**
         * The grid three consecutive frame callbacks give, or null when they do not describe a
         * display: the shorter of the two gaps is the period (a callback this thread was late for
         * makes one gap two ticks long), and it must lie between 20 and 500 Hz. Nothing here knows
         * which phone it runs on -- the period is whatever `Choreographer` reports.
         */
        fun measure(t0: Long, t1: Long, t2: Long): Grid? {
            val period = minOf(t1 - t0, t2 - t1)
            return if (period in MIN_PERIOD_NANOS..MAX_PERIOD_NANOS) Grid(t2, period) else null
        }

        /** [grid] if it is younger than [STALE_AFTER_NANOS] at [nowNanos], otherwise null. */
        fun fresh(grid: Grid?, nowNanos: Long): Grid? =
            if (grid != null && nowNanos - grid.tickNanos <= STALE_AFTER_NANOS) grid else null
    }
}
