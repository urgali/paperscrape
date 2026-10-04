package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * The render loop's pacing, run against a simulated display instead of watched on one.
 *
 * The display is a grid of ticks, one every [realPeriodNanos] from an arbitrary phase. The loop
 * sees it the way [GlRenderThread] does: through a measurement ([VsyncGrid]'s) that is re-taken
 * about once a second and can be a little off. Each frame costs a random amount of time, then the
 * loop sleeps to the tick [FramePacing.nextTick] names. What must hold is the property the phone
 * showed broken: **the same number of ticks between every two frames**, whatever the cost, as long
 * as a frame fits in its interval -- and every start on a real tick, not beside one.
 */
class FramePacingTest {

    private class Run(val starts: List<Long>, val realPeriodNanos: Long, val phaseNanos: Long) {
        /** The gaps between consecutive starts, in ticks, skipping the first frame (off-grid). */
        fun gapsInTicks(): List<Double> =
            starts.zipWithNext { a, b -> (b - a).toDouble() / realPeriodNanos }.drop(1)

        /** How far the worst start is from the nearest real tick, from the second frame on. */
        fun worstOffGridNanos(): Long = starts.drop(1).maxOf { t ->
            val k = Math.floorDiv(t - phaseNanos + realPeriodNanos / 2, realPeriodNanos)
            abs(t - (phaseNanos + k * realPeriodNanos))
        }
    }

    private fun simulate(
        realPeriodNanos: Long,
        frames: Int,
        costNanos: (Int) -> Long,
        measuredPeriodError: Long = 0L,
        phaseNanos: Long = 3_141_592L,
    ): Run {
        val starts = ArrayList<Long>()
        var anchor = 0L
        var now = 1_000_000_000L
        var gridTick = 0L
        var lastMeasure: Long? = null
        repeat(frames) { i ->
            // The grid is re-measured about once a second, at a real tick, with a period that may
            // be a little off -- which is what an estimate is.
            if (lastMeasure == null || now - lastMeasure >= 1_000_000_000L) {
                val k = Math.floorDiv(now - phaseNanos, realPeriodNanos)
                gridTick = phaseNanos + k * realPeriodNanos
                lastMeasure = now
            }
            val period = realPeriodNanos + measuredPeriodError
            val frameStart = if (anchor != 0L) anchor else now
            starts += frameStart
            now = frameStart + costNanos(i)
            anchor = FramePacing.nextTick(anchor, frameStart, now, gridTick, period)
            now = anchor
        }
        return Run(starts, realPeriodNanos, phaseNanos)
    }

    private val typicalCost: (Int) -> Long = { i -> 12_000_000L + Random(i).nextLong(0L, 9_000_000L) }

    private fun assertEvenly(run: Run, ticks: Int, label: String, tolerance: Double = 0.01) {
        for ((i, gap) in run.gapsInTicks().withIndex()) {
            assertEquals("$label, gap $i", ticks.toDouble(), gap, tolerance)
        }
    }

    @Test
    fun `the BV6600 panel gets two ticks between every pair of frames`() {
        // SurfaceFlinger's model of this panel said 16 273 557 ns one evening; the frame rate
        // measured under the new loop says 16.0 ms another. Both, and the platform's 60 Hz.
        for (period in listOf(16_000_000L, 16_273_557L, 16_666_667L)) {
            val run = simulate(period, 600, typicalCost)
            assertEvenly(run, 2, "period $period")
            assertTrue("period $period: off the grid by ${run.worstOffGridNanos()} ns", run.worstOffGridNanos() < 100_000L)
        }
    }

    @Test
    fun `sixty, ninety and one hundred and twenty hertz all come out at about thirty frames a second`() {
        for ((hz, expectedTicks) in listOf(60.0 to 2, 62.5 to 2, 90.0 to 3, 120.0 to 4)) {
            val period = (1_000_000_000.0 / hz).toLong()
            val run = simulate(period, 300, typicalCost)
            assertEvenly(run, expectedTicks, "$hz Hz")
            val fps = (run.starts.size - 2) * 1e9 / (run.starts.last() - run.starts[1])
            assertTrue("$hz Hz gave $fps fps", fps in 29.5..31.5)
        }
    }

    @Test
    fun `a period measured a little off does not drift the frames off the ticks`() {
        // A microsecond out, and the loop kept to its own arithmetic, would be three quarters of a
        // tick off after twenty seconds. Snapping the anchor onto each fresh measurement is what
        // stops that: the error never outlives a second.
        for (error in listOf(-2_000L, 1_000L, 5_000L)) {
            val run = simulate(16_273_557L, 1_200, typicalCost, measuredPeriodError = error)
            // A re-measurement moves the schedule by the error the second had built up -- a third
            // of a millisecond at 5 us, far inside the tick, so the same tick still shows it.
            assertEvenly(run, 2, "error $error ns", tolerance = 0.05)
            // Within a second the error grows by one period's worth per tick: ~62 ticks of it.
            val bound = 70L * abs(error) + 50_000L
            assertTrue("error $error ns: off the grid by ${run.worstOffGridNanos()} ns", run.worstOffGridNanos() < bound)
        }
    }

    @Test
    fun `a frame that overruns costs one tick once and the grid comes back`() {
        val period = 16_666_667L
        val cost: (Int) -> Long = { i -> if (i == 50) 40_000_000L else 14_000_000L }
        val run = simulate(period, 120, cost)
        val gaps = run.starts.zipWithNext { a, b -> (b - a).toDouble() / period }
        // Frame 50 starts on its tick and takes 40 ms: the next start is the first tick after it
        // finishes, three ticks later, and from there every gap is two again -- on the grid.
        assertEquals(3.0, gaps[50], 1e-6)
        gaps.forEachIndexed { i, g -> if (i != 50 && i != 0) assertEquals("gap $i", 2.0, g, 1e-6) }
        assertTrue(run.worstOffGridNanos() < 1_000L)
    }

    @Test
    fun `the first frame after parking starts on the tick nearest a frame interval later`() {
        val period = 16_666_667L
        val grid = 7_000_000L
        val start = 5_000_000_123L
        val next = FramePacing.nextTick(0L, start, start + 14_000_000L, grid, period)
        assertEquals(FramePacing.nearestTick(start + FramePacing.FRAME_INTERVAL_NANOS, grid, period), next)
        assertEquals(0L, Math.floorMod(next - grid, period))
    }

    @Test
    fun `the number of ticks a frame lasts is the whole number nearest thirty frames a second`() {
        assertEquals(2, FramePacing.ticksPerFrame(16_666_667L)) // 60 Hz
        assertEquals(2, FramePacing.ticksPerFrame(16_000_000L)) // this panel on a fast evening
        assertEquals(3, FramePacing.ticksPerFrame(11_111_111L)) // 90 Hz
        assertEquals(4, FramePacing.ticksPerFrame(8_333_333L)) // 120 Hz
        assertEquals(2, FramePacing.ticksPerFrame(20_833_333L)) // 48 Hz: 24 fps, nearer 30 than 48
        assertEquals(1, FramePacing.ticksPerFrame(33_333_333L)) // 30 Hz
        assertEquals(1, FramePacing.ticksPerFrame(40_000_000L)) // never zero
    }

    @Test
    fun `the old sleep-based loop on the same panel really did slide`() {
        // The measurement that opened this, reproduced: sleep(33 - cost) against 16.27 ms ticks.
        // A frame reaches the screen on the first tick after it is finished, so the gap between two
        // presentations is counted in ticks.
        val period = 16_273_557L
        var t = 0L
        val presented = ArrayList<Long>()
        repeat(600) { i ->
            val cost = typicalCost(i)
            presented += Math.floorDiv(t + cost + period - 1, period)
            t += cost + (33L - cost / 1_000_000L) * 1_000_000L + 150_000L // sleep, plus its usual overshoot
        }
        val gaps = presented.zipWithNext { a, b -> b - a }
        val long = gaps.count { it >= 3L }
        assertTrue("old loop: $long of ${gaps.size} gaps were three ticks", long > gaps.size / 25)
    }

    // ---- v5.8C: what the maintainer asked -- "are we basing a fix on one phone, statically?" ----

    /**
     * **Any refresh rate, not one phone's.** The number of ticks between frames is worked out from
     * the measured period, so a 50, 75, 144, 165 or 240 Hz panel lands on the whole number of ticks
     * nearest 30 frames a second and keeps it steady, like 60, 90 and 120.
     */
    @Test
    fun `panels from fifty to two hundred and forty hertz are paced evenly near thirty frames a second`() {
        for (hz in listOf(50.0, 61.45, 75.0, 90.0, 120.0, 144.0, 165.0, 240.0)) {
            val period = (1_000_000_000.0 / hz).toLong()
            val ticks = FramePacing.ticksPerFrame(period)
            val run = simulate(period, 300, { 9_000_000L })
            assertEvenly(run, ticks, "$hz Hz")
            val fps = 1e9 / (ticks * period)
            assertTrue("$hz Hz: $ticks ticks = $fps fps, not the nearest to 30", kotlin.math.abs(fps - 30.0) <= kotlin.math.abs(1e9 / ((ticks + 1) * period) - 30.0) &&
                kotlin.math.abs(fps - 30.0) <= kotlin.math.abs(1e9 / (maxOf(1, ticks - 1) * period) - 30.0) + 1e-9)
        }
    }

    /** **A panel that changes rate** (adaptive 60/120 Hz phones): the next measurement is followed. */
    @Test
    fun `after the display switches from sixty to one hundred and twenty hertz the frames follow`() {
        val p60 = 16_666_667L
        val p120 = 8_333_333L
        var anchor = 0L
        var now = 1_000_000_000L
        val starts = ArrayList<Long>()
        repeat(120) { i ->
            val period = if (i < 60) p60 else p120
            val grid = VsyncGrid.Grid(now - Math.floorMod(now, period), period)
            val frameStart = if (anchor != 0L) anchor else now
            starts += frameStart
            now = frameStart + 9_000_000L
            anchor = FramePacing.nextAnchorTick(grid, anchor, frameStart, now)
            now = anchor
        }
        val late = starts.zipWithNext { a, b -> b - a }.drop(70)
        for (gap in late) assertEquals("at 120 Hz a frame lasts four ticks", (4 * p120).toDouble(), gap.toDouble(), 2_000.0)
    }

    /**
     * **The measurement accepts 20 to 500 Hz and nothing else**, and takes the shorter gap of its
     * three callbacks (a late callback doubles one gap).
     */
    @Test
    fun `the grid is measured from three callbacks, between twenty and five hundred hertz`() {
        assertEquals(16_273_557L, VsyncGrid.measure(0L, 16_273_557L, 2 * 16_273_557L)!!.periodNanos)
        assertEquals("a late callback doubles one gap; the shorter one is the period",
            8_333_333L, VsyncGrid.measure(0L, 2 * 8_333_333L, 3 * 8_333_333L)!!.periodNanos)
        assertEquals(50_000_000L, VsyncGrid.measure(0L, 50_000_000L, 100_000_000L)!!.periodNanos)   // 20 Hz
        assertEquals(2_000_000L, VsyncGrid.measure(0L, 2_000_000L, 4_000_000L)!!.periodNanos)       // 500 Hz
        assertEquals(null, VsyncGrid.measure(0L, 50_000_001L, 100_000_002L))                       // under 20 Hz
        assertEquals(null, VsyncGrid.measure(0L, 1_999_999L, 3_999_998L))                          // over 500 Hz
        assertEquals(null, VsyncGrid.measure(0L, 0L, 0L))                                          // no ticks at all
    }

    /**
     * **With no measurement the loop paces exactly as it did before v5.8C** -- `33 ms - cost`, no
     * anchor -- whether there never was one or the last one is older than three seconds.
     */
    @Test
    fun `without a fresh grid the old sleep comes back`() {
        assertEquals(
            "33 ms less a 14 ms cost, as the old loop computed it",
            19_000_000L,
            FramePacing.fallbackSleepNanos(1_000_000_000L, 1_000_000_000L + 14_600_000L),
        )
        assertEquals(
            "no anchor: the next frame is not on a tick",
            0L,
            FramePacing.nextAnchorTick(null, 123_456L, 1_000_000_000L, 1_000_000_000L + 14_600_000L),
        )
        val old = VsyncGrid.Grid(tickNanos = 1_000_000_000L, periodNanos = 16_666_667L)
        assertEquals(old, VsyncGrid.fresh(old, 1_000_000_000L + VsyncGrid.STALE_AFTER_NANOS))
        assertEquals(null, VsyncGrid.fresh(old, 1_000_000_000L + VsyncGrid.STALE_AFTER_NANOS + 1))
        assertEquals(null, VsyncGrid.fresh(null, 5L))
    }

    // ------------------------------------------------------------------ presentation time

    /**
     * Each frame asks to be shown a quarter period before the tick **after** the next frame's, on
     * every panel (since v5.9F; the next frame's own tick until then, `FramePacing.presentAt`):
     * consecutive requests are exactly one frame interval apart, and each falls more than three
     * quarters of a period past the frame's `k`-th tick -- the next frame's start, so the
     * composition of this one no longer overlaps the drawing of that one -- and before the tick
     * after it.
     */
    @Test
    fun `a frame asks to be shown just before the tick after the next frame's, on every panel`() {
        for (hz in listOf(50.0, 60.0, 61.45, 90.0, 120.0, 144.0, 240.0)) {
            val period = (1e9 / hz).toLong()
            val k = FramePacing.ticksPerFrame(period)
            val grid = VsyncGrid.Grid(tickNanos = 5_000_000_000L, periodNanos = period)
            var tick = grid.tickNanos
            var previous = 0L
            repeat(100) {
                val at = FramePacing.presentAt(grid, tick)
                val lead = at - tick
                assertTrue("$hz Hz: $lead ns after the tick", lead >= k * period + 3 * period / 4 && lead < (k + 1) * period)
                if (previous != 0L) assertEquals("$hz Hz: requests one frame apart", k * period, at - previous)
                previous = at
                tick += k * period
            }
        }
    }

    @Test
    fun `without a grid or a tick the frame is shown as soon as it is ready`() {
        val grid = VsyncGrid.Grid(tickNanos = 1_000_000_000L, periodNanos = 16_000_000L)
        assertEquals(FramePacing.PRESENT_AUTO, FramePacing.presentAt(null, 2_000_000_000L))
        assertEquals(FramePacing.PRESENT_AUTO, FramePacing.presentAt(grid, 0L))
        assertEquals(Long.MIN_VALUE, FramePacing.PRESENT_AUTO) // NATIVE_WINDOW_TIMESTAMP_AUTO
    }

    /**
     * **What the time is for, on a model of the compositor that knows no phone.** The panel refreshes
     * at some offset from the ticks the app is woken on, and the compositor needs a frame some lead
     * time before the refresh it is shown on; a frame with a presentation time is shown on the first
     * refresh after that time (`BufferQueueLayer::shouldPresentNow`: due when the time is before the
     * refresh's expected present), one without on the first refresh it is ready for.
     *
     * Swept over panels, offsets and lead times, with each frame costing anything from a fifth of
     * a period to what the interval leaves: with the time every interval on screen is exactly one
     * frame interval; without it, on the same frames, some are not -- the 48/16 ms pairs the
     * BV6600 shows. (The phone keeps some even with the time: see `FramePacing.presentAt`.)
     */
    @Test
    fun `with the time every frame reaches the screen one interval after the last, whatever it cost`() {
        val random = Random(58)
        var unevenWithout = 0
        for (hz in listOf(60.0, 61.45, 90.0, 120.0)) {
            val period = (1e9 / hz).toLong()
            val k = FramePacing.ticksPerFrame(period)
            val grid = VsyncGrid.Grid(tickNanos = 0L, periodNanos = period)
            for (offsetStep in 0 until 8) {
                val offset = period * offsetStep / 8
                for (leadStep in 0..4) {
                    val lead = period * leadStep / 8
                    // The latest a frame could be ready and still make the refresh the time asked for
                    // until v5.9F; the time is one refresh later now, so this keeps a refresh to spare.
                    val budget = (k * period - period / 4) - lead
                    var tick = k * period * 10
                    var lastWith = 0L
                    var lastWithout = 0L
                    repeat(200) {
                        val ready = tick + period / 5 + (random.nextDouble() * (budget - period / 5)).toLong()
                        val withTime = firstRefresh(maxOf(ready + lead, FramePacing.presentAt(grid, tick) + 1), offset, period)
                        val withoutTime = firstRefresh(ready + lead, offset, period)
                        if (lastWith != 0L) assertEquals("$hz Hz offset $offset lead $lead", k * period, withTime - lastWith)
                        if (lastWithout != 0L && withoutTime - lastWithout != k * period) unevenWithout++
                        lastWith = withTime
                        lastWithout = withoutTime
                        tick += k * period
                    }
                }
            }
        }
        assertTrue("without a time the same frames must reach the screen unevenly, or this proves nothing", unevenWithout > 1_000)
    }

    /** The first refresh at or after [t] of a panel refreshing at [offset] from the ticks. */
    private fun firstRefresh(t: Long, offset: Long, period: Long): Long =
        offset + Math.floorDiv(t - offset + period - 1, period) * period

    /** The render thread asks for the time on every frame, when the display offers it. */
    @Test
    fun `the render thread sets the presentation time on every swap`() {
        val code = source("GlRenderThread.kt")
        val swap = code.substringAfter("private fun drawFrame(").substringBefore("EGL14.eglSwapBuffers(")
        // Guarded by the extension and by nothing else (a condition added here would switch it off).
        assertTrue(swap, swap.contains("if (presentationTimeSupported) {\n            EGLExt.eglPresentationTimeANDROID(") && swap.contains("FramePacing.presentAt("))
        assertTrue(code.contains("\"EGL_ANDROID_presentation_time\""))
    }

    /**
     * **No phone's refresh rate is written into the pacing code.** Read from the three sources: no
     * literal that is a panel's period or rate (the BV6600's 16.27 ms / 61.45 Hz / 32.5 ms, or the
     * common 60/90/120 Hz periods) outside comments. The only time constants are the 30 fps target,
     * the fallback's 33 ms, the 20-500 Hz bounds and the sampling cadence.
     */
    @Test
    fun `the pacing code names no panel frequency`() {
        val forbidden = Regex("""\b(16_?27\d|16_?666|16_?667|11_?111|8_?333|61[.]4|61[.]5|62[.]5|32[.]5|60[.]0|90[.]0|120[.]0)""")
        for (name in listOf("FramePacing.kt", "VsyncGrid.kt", "GlRenderThread.kt")) {
            val code = source(name).lines().map { it.substringBefore("//") }
                .filterNot { it.trim().startsWith("*") || it.trim().startsWith("/*") }.joinToString("\n")
            assertEquals("$name names a panel frequency", emptyList<String>(), forbidden.findAll(code).map { it.value }.toList())
        }
    }

    private fun source(name: String): String {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/engine/$name"
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = java.io.File(dir, "$prefix$suffix")
                if (f.isFile) return f.readText()
            }
            dir = dir.parentFile
        }
        error("could not locate $suffix")
    }
}
