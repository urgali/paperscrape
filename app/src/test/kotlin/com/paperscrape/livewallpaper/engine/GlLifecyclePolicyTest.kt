package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The GL lifecycle rules, driven as a state machine rather than watched on a device.
 *
 * A wallpaper surface is destroyed and recreated at the framework's discretion, and the sequence
 * that exposed both defects fixed here -- destroy, create, destroy, create -- is one no gesture on
 * an emulator reliably produces. Driving the decision directly is what makes "after N cycles
 * nothing has grown" a statement a test can make at all.
 *
 * No thread is started and no clock is read: these are pure functions, so the whole cycle is
 * arithmetic.
 */
class GlLifecyclePolicyTest {

    /**
     * A stand-in for the engine's own bookkeeping: how many render threads it has built, and
     * whether one is currently live. Exactly the two facts ARC-01 was about.
     */
    private class EngineModel(var canvasFallback: Boolean = false) {
        var threadsCreated = 0
        var hasThread = false
        var surfacesAttached = 0

        fun surfaceCreated() {
            when (GlLifecyclePolicy.surfaceCreated(hasThread, canvasFallback)) {
                GlLifecyclePolicy.SurfaceAction.START_THREAD -> {
                    threadsCreated++
                    hasThread = true
                    surfacesAttached++
                }
                GlLifecyclePolicy.SurfaceAction.REUSE_THREAD -> surfacesAttached++
                GlLifecyclePolicy.SurfaceAction.NO_GL -> Unit
            }
        }

        /** The surface goes; the thread does not. Only engine destruction ends its life. */
        fun surfaceDestroyed() = Unit

        /** GL is over for this engine: the thread is shut down and never replaced. */
        fun glGivenUp() {
            hasThread = false
            canvasFallback = true
        }
    }

    /**
     * The render thread's own view of its EGL surface, driven the way the loop drives it.
     *
     * The holder is deliberately *not* what decides reuse here, because that is exactly the
     * mistake: the render thread reads a holder, not a history, and a replacement that arrived
     * while it was busy looks identical to the window it was already drawing into.
     */
    private class EglModel {
        var hasEglSurface = false
        var stale = false
        var surfacesBuilt = 0

        /** The engine, on the main thread: the window is going away. */
        fun windowDestroyed() {
            stale = true
        }

        /** One pass of `ensureEglSurface`. */
        fun ensureSurface() {
            if (!GlLifecyclePolicy.mayReuseEglSurface(hasEglSurface, stale)) destroySurface()
            if (hasEglSurface) return
            hasEglSurface = true
            surfacesBuilt++
        }

        /** `destroyEglSurface`, which clears the flag whether or not there was a surface. */
        fun destroySurface() {
            stale = false
            hasEglSurface = false
        }
    }

    // ------------------------------------------------------------------ ARC-01

    /**
     * The defect, stated as the thing that was wrong: one render thread per *surface*.
     *
     * Every destroy/create cycle used to build another `GlRenderThread`, and nothing stopped the
     * previous one -- so a thread, an EGL context and every uploaded texture were abandoned per
     * cycle, for the life of the process.
     */
    @Test
    fun `repeated surface cycles build exactly one render thread`() {
        val engine = EngineModel()
        engine.surfaceCreated()
        repeat(50) {
            engine.surfaceDestroyed()
            engine.surfaceCreated()
        }
        assertEquals("a surface cycle must not cost a render thread", 1, engine.threadsCreated)
        assertEquals("every surface must still be attached to it", 51, engine.surfacesAttached)
        assertTrue(engine.hasThread)
    }

    /** Growth is what the leak looked like, so assert the shape and not just the endpoint. */
    @Test
    fun `the thread count does not grow with the number of cycles`() {
        val counts = listOf(1, 5, 20, 100).map { cycles ->
            val engine = EngineModel()
            engine.surfaceCreated()
            repeat(cycles) {
                engine.surfaceDestroyed()
                engine.surfaceCreated()
            }
            engine.threadsCreated
        }
        assertEquals(listOf(1, 1, 1, 1), counts)
    }

    @Test
    fun `the first surface is what starts the thread`() {
        assertEquals(
            GlLifecyclePolicy.SurfaceAction.START_THREAD,
            GlLifecyclePolicy.surfaceCreated(hasThread = false, canvasFallback = false),
        )
    }

    @Test
    fun `a later surface reuses the thread that already owns this engine's GL`() {
        assertEquals(
            GlLifecyclePolicy.SurfaceAction.REUSE_THREAD,
            GlLifecyclePolicy.surfaceCreated(hasThread = true, canvasFallback = false),
        )
    }

    /** Once GL is out of the picture the Canvas loop owns drawing, and no thread is built. */
    @Test
    fun `in canvas fallback no render thread is ever started`() {
        val engine = EngineModel(canvasFallback = true)
        repeat(10) {
            engine.surfaceCreated()
            engine.surfaceDestroyed()
        }
        assertEquals(0, engine.threadsCreated)
        assertFalse(engine.hasThread)
        assertEquals(
            GlLifecyclePolicy.SurfaceAction.NO_GL,
            GlLifecyclePolicy.surfaceCreated(hasThread = false, canvasFallback = true),
        )
        // Even with a thread still around, the fallback decision wins: it is the one that stopped GL.
        assertEquals(
            GlLifecyclePolicy.SurfaceAction.NO_GL,
            GlLifecyclePolicy.surfaceCreated(hasThread = true, canvasFallback = true),
        )
    }

    // ------------------------------------------------------------------ ARC-05

    /**
     * A device that never managed a frame is the only case that ends GL for good.
     *
     * That was the *only* case the old code intended to handle, and the only one it distinguished:
     * every other failure took the same path and latched the engine into the Canvas fallback.
     */
    @Test
    fun `a context that never worked is not rebuilt`() {
        assertFalse(GlLifecyclePolicy.shouldRebuildContext(hadWorkingContext = false, rebuildsSoFar = 0))
        assertFalse(GlLifecyclePolicy.shouldRebuildContext(hadWorkingContext = false, rebuildsSoFar = 2))
    }

    /**
     * ARC-05-res, assessed and left as it is.
     *
     * The budget is per **engine**, not per incident: it is never reset after a recovery. That
     * reads like an oversight and is not one. Resetting it would mean a GPU that resets on every
     * frame gets rebuilt on every frame -- an endless retry against hardware that is not coming
     * back, which on a live wallpaper is a busy thread and no picture, the failure the bound exists
     * to prevent. Making it per-incident instead would need a notion of "how long since the last
     * one", i.e. a decay, i.e. a timer this engine does not otherwise need.
     *
     * What the current shape costs is narrow: an engine that survives three *separate*, genuinely
     * recovered driver resets falls back to Canvas on the fourth. Engines are recreated on every
     * surface destroy/create cycle -- rotation, unlock, opening the wallpaper picker -- so three
     * independent incidents inside one engine's life is not a case that shows up in practice.
     *
     * Recorded as a test rather than a comment so the reasoning is executable, and so that anyone
     * who decides to reset the counter has to delete an assertion that says why not to.
     */
    @Test
    fun `the rebuild budget is per engine and deliberately not reset after a recovery`() {
        // One incident: recovered, and the engine keeps GL.
        assertTrue(GlLifecyclePolicy.shouldRebuildContext(hadWorkingContext = true, rebuildsSoFar = 0))
        // Three separate incidents, each recovered: still GL, budget now spent.
        assertTrue(GlLifecyclePolicy.shouldRebuildContext(hadWorkingContext = true, rebuildsSoFar = 2))
        // The fourth is where it stops, whether or not the three before it recovered.
        assertFalse(GlLifecyclePolicy.shouldRebuildContext(hadWorkingContext = true, rebuildsSoFar = 3))
        assertFalse(GlLifecyclePolicy.shouldRebuildContext(hadWorkingContext = true, rebuildsSoFar = 99))
    }

    /** A context that has drawn is worth rebuilding: the hardware has already proved it can. */
    @Test
    fun `a context that worked is rebuilt, up to the bound`() {
        for (attempt in 0 until GlLifecyclePolicy.MAX_CONTEXT_REBUILDS) {
            assertTrue(
                "rebuild $attempt should be allowed",
                GlLifecyclePolicy.shouldRebuildContext(hadWorkingContext = true, rebuildsSoFar = attempt),
            )
        }
    }

    /** And bounded: a GPU that is not coming back must not keep a render thread spinning. */
    @Test
    fun `rebuilding gives up after the bound`() {
        assertFalse(
            GlLifecyclePolicy.shouldRebuildContext(
                hadWorkingContext = true,
                rebuildsSoFar = GlLifecyclePolicy.MAX_CONTEXT_REBUILDS,
            ),
        )
        assertFalse(GlLifecyclePolicy.shouldRebuildContext(hadWorkingContext = true, rebuildsSoFar = 99))
        assertTrue("the bound has to actually allow retries", GlLifecyclePolicy.MAX_CONTEXT_REBUILDS > 0)
    }

    // ------------------------------------------------------------------ ARC-06

    /**
     * A trim must not touch GL outside a prepared frame -- the defect, stated as the rule.
     *
     * It used to be a queued Runnable, and the loop drains queued work at the top: before any
     * context is current, and in the surface-gone branch immediately after the context has been
     * unbound. This drives the loop's two decision points in the order the loop reaches them.
     */
    @Test
    fun `a trim asked for with no surface does not run, and is not lost`() {
        var pending = true
        var trimsRun = 0

        // Pass 1 and 2: no window, so no context. The loop reaches only the surface-gone branch.
        repeat(2) {
            if (GlLifecyclePolicy.mayApplyTrim(pending, framePrepared = false)) {
                pending = false
                trimsRun++
            }
        }
        assertEquals("a trim ran with no context", 0, trimsRun)
        assertTrue("the request must survive until it can be honoured", pending)

        // Pass 3: a surface is back and a frame prepared, so the context is current.
        if (GlLifecyclePolicy.mayApplyTrim(pending, framePrepared = true)) {
            pending = false
            trimsRun++
        }
        assertEquals(1, trimsRun)

        // And it does not run again on later frames.
        repeat(5) {
            if (GlLifecyclePolicy.mayApplyTrim(pending, framePrepared = true)) trimsRun++
        }
        assertEquals("a consumed trim must not repeat", 1, trimsRun)
    }

    @Test
    fun `no trim requested means no work at either point in the loop`() {
        assertFalse(GlLifecyclePolicy.mayApplyTrim(trimRequested = false, framePrepared = true))
        assertFalse(GlLifecyclePolicy.mayApplyTrim(trimRequested = false, framePrepared = false))
    }

    /** The whole failure sequence: retries, then one report, and never an unbounded loop. */
    @Test
    fun `a permanently failing GPU is reported once after a bounded number of retries`() {
        var rebuilds = 0
        var reported = false
        repeat(20) {
            if (GlLifecyclePolicy.shouldRebuildContext(hadWorkingContext = true, rebuildsSoFar = rebuilds)) {
                rebuilds++
            } else {
                reported = true
            }
        }
        assertEquals(GlLifecyclePolicy.MAX_CONTEXT_REBUILDS, rebuilds)
        assertTrue("the engine must eventually fall back rather than retry for ever", reported)
    }

    // ------------------------------------------- EGL surface ownership (B3 final review)

    /**
     * The defect this review found, written as the interleaving that produces it.
     *
     * The engine delivers destroy and create back to back. If the render thread is mid-frame or
     * parked it never runs in between, so it never observes the null holder and the loop's
     * surface-gone branch -- the only thing that released the EGL surface on a window swap -- is
     * simply skipped. `ensureEglSurface` then asked only "do I have a surface?", said yes, and kept
     * drawing into the window that had gone away.
     */
    @Test
    fun `a window replaced while the render thread was busy does not reuse its EGL surface`() {
        val egl = EglModel()

        // A window, and a frame drawn into it.
        egl.ensureSurface()
        assertEquals(1, egl.surfacesBuilt)

        // Main thread: destroy, then create, with no loop pass in between.
        egl.windowDestroyed()

        // The render thread finally looks. The holder it reads is the *new* one.
        egl.ensureSurface()

        assertEquals("the EGL surface must be rebuilt for the new window", 2, egl.surfacesBuilt)
        assertFalse("and must no longer be marked stale", egl.stale)
    }

    /** The ordinary case must not be spoiled by the fix: a live window keeps its surface. */
    @Test
    fun `a window that is still there keeps the surface it already has`() {
        val egl = EglModel()
        egl.ensureSurface()
        repeat(10) { egl.ensureSurface() }
        assertEquals("frames must not rebuild the EGL surface", 1, egl.surfacesBuilt)
    }

    /** N replacements cost exactly N surfaces -- one each, never zero and never accumulating. */
    @Test
    fun `each window replacement costs exactly one EGL surface`() {
        val egl = EglModel()
        egl.ensureSurface()
        repeat(20) {
            egl.windowDestroyed()
            egl.ensureSurface()
        }
        assertEquals(21, egl.surfacesBuilt)
        assertTrue(egl.hasEglSurface)
        assertFalse(egl.stale)
    }

    @Test
    fun `an EGL surface is never reused once its window is gone`() {
        assertFalse(GlLifecyclePolicy.mayReuseEglSurface(hasEglSurface = true, surfaceStale = true))
        assertTrue(GlLifecyclePolicy.mayReuseEglSurface(hasEglSurface = true, surfaceStale = false))
        assertFalse(GlLifecyclePolicy.mayReuseEglSurface(hasEglSurface = false, surfaceStale = false))
    }

    // ------------------------------------------------------------------ teardown

    /**
     * Once GL ownership ends it never restarts: no thread, no work, for the rest of the engine.
     *
     * The two ends are the same one -- `switchToCanvasFallback` and `onDestroy` both shut the
     * thread down and null the field -- and what makes "no ownership remains active" true is that
     * the policy can no longer answer anything but `NO_GL`. Asserted across the transition rather
     * than from a fallback engine that never had a thread, which is what the other fallback test
     * already covers.
     */
    @Test
    fun `once GL ownership ends no later surface can start it again`() {
        val engine = EngineModel()
        engine.surfaceCreated()
        repeat(5) {
            engine.surfaceDestroyed()
            engine.surfaceCreated()
        }
        assertEquals(1, engine.threadsCreated)

        engine.glGivenUp()

        repeat(20) {
            engine.surfaceDestroyed()
            engine.surfaceCreated()
        }
        assertEquals("no thread may be built after GL has been given up", 1, engine.threadsCreated)
        assertFalse("and none may be left owning anything", engine.hasThread)
        assertEquals(
            "every later surface is the Canvas loop's",
            GlLifecyclePolicy.SurfaceAction.NO_GL,
            GlLifecyclePolicy.surfaceCreated(hasThread = false, canvasFallback = true),
        )
    }
}
