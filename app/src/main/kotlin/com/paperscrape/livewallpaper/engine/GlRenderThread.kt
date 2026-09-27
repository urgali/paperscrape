package com.paperscrape.livewallpaper.engine

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.view.SurfaceHolder

/**
 * Owns the EGL context, the GL surface and the render loop for one wallpaper engine.
 *
 * ## Why a thread at all
 *
 * `Canvas` rendering ran on the main looper because `lockCanvas` allows it. A GL context is bound to
 * exactly one thread, so the render loop has to leave the main thread. That has a consequence the
 * rest of the engine has to respect: **scene state is now mutated from a different thread than it is
 * read from.** The answer here is not a lock around the renderer but [queueEvent]: preference,
 * theme, weather and offset changes arrive as runnables executed on this thread between frames, so
 * the scene is only ever touched by the thread that draws it. `SpriteCache` is the exception: it is
 * shared by every engine's render thread and by the main thread's memory trim, so it takes its own
 * lock (see its own doc).
 *
 * ## Pacing
 *
 * Every frame starts on one of the display's refresh ticks, and the next one the same number of
 * ticks later: the loop sleeps to the tick [FramePacing.nextTick] predicts from the grid
 * [VsyncGrid] measures, about 30 frames a second. It used to sleep `33 ms - cost`, which is not
 * the display's clock, and every few frames it slid one tick late and that frame stayed on screen
 * half as long again -- see [FramePacing]. Until a grid has been measured (the first second, and
 * the first second after the loop parked) it paces that old way. It deliberately does **not**
 * free-run at the display's refresh rate, which would render at 60, 90 or 120 Hz and do two to
 * four times the work for motion this slow.
 *
 * A frame's time is its tick's, not the moment the thread woke up: the scene then advances by
 * exactly the interval the display will show it for. Each swap also says when the frame should be
 * shown ([FramePacing.presentAt], `eglPresentationTimeANDROID`, where the display offers it), so
 * the compositor shows every frame on the same refresh relative to its tick whatever it cost.
 *
 * ## Failure
 *
 * A failed frame is first answered by rebuilding the EGL state -- up to
 * [GlLifecyclePolicy.MAX_CONTEXT_REBUILDS] times, and only if a frame has ever been drawn -- and a
 * failed swap rebuilds or drops the surface; only when [GlLifecyclePolicy.shouldRebuildContext] says
 * no does the thread report [Callbacks.onGlUnavailable], exactly once, and park. The engine then
 * falls back to the `Canvas` path, so a device that cannot give this process a GL context still
 * renders a wallpaper.
 */
internal class GlRenderThread(
    private val callbacks: Callbacks,
) : Thread("PaperScrapeGlThread") {

    interface Callbacks {
        /** Called on the render thread once the surface has a size, before the first frame. */
        fun onGlSurfaceChanged(width: Int, height: Int)

        /** Called on the render thread once per frame, between `beginFrame` and `endFrame`. */
        fun onGlDrawFrame(target: SceneCanvas, deltaSeconds: Float)

        /**
         * Called on the render thread when GL is given up on: it never initialised, or it stopped
         * working and could not be rebuilt.
         */
        fun onGlUnavailable()
    }

    val target = GlSceneTarget()

    private val lock = Object()
    private val eventQueue = ArrayDeque<Runnable>()

    @Volatile private var holder: SurfaceHolder? = null
    @Volatile private var visible = false
    @Volatile private var exitRequested = false
    @Volatile private var pendingWidth = 0
    @Volatile private var pendingHeight = 0
    @Volatile private var unavailableReported = false

    /**
     * A memory trim asked for from the main thread, consumed on the render thread **after** the
     * context is current.
     *
     * It used to be a queued Runnable, and [drainEvents] runs at the top of the loop -- before
     * `prepareFrame` makes the context current, and in the surface-gone branch after
     * [destroyEglSurface] has explicitly unbound it. `glDeleteTextures` with no current context is
     * a silent no-op that still forgets the handles, and re-packing the atlas' white pixel fails,
     * so the target was left believing it was usable with no white pixel to draw flat fills with.
     * A flag cannot run at the wrong moment; a queued GL call could.
     */
    @Volatile private var trimRequested = false

    /**
     * Set when the window the current [eglSurface] was built from has gone away.
     *
     * Not derivable from [holder]: the engine publishes the replacement immediately after the
     * destroy, so a render thread that was mid-frame or parked never sees the null in between and
     * cannot tell the new window from the old one. See [GlLifecyclePolicy.mayReuseEglSurface].
     */
    @Volatile private var eglSurfaceStale = false

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var eglConfig: EGLConfig? = null

    /**
     * Whether a frame has ever been prepared successfully on this thread.
     *
     * The difference between "this device cannot do EGL" and "EGL was working a moment ago" -- the
     * two cases the old code could not tell apart, so both ended the engine's GL for good.
     */
    private var hadWorkingContext = false
    private var contextRebuilds = 0

    private var currentWidth = 0
    private var currentHeight = 0
    private var lastFrameNanos = 0L

    private val vsync = VsyncGrid()

    /** The tick the frame being drawn was started on; 0 when it was not started on one. */
    private var anchorTickNanos = 0L

    /** Whether this display offers `EGL_ANDROID_presentation_time`; read once, when EGL starts. */
    private var presentationTimeSupported = false

    // --- Calls from the main thread ----------------------------------------------------------

    fun onSurfaceCreated(holder: SurfaceHolder) {
        this.holder = holder
        wake()
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        pendingWidth = width
        pendingHeight = height
        wake()
    }

    fun onSurfaceDestroyed() {
        // Written before the holder, so a render thread that later reads a non-null holder is
        // guaranteed to also see this: both are volatile, and these writes are not reordered.
        eglSurfaceStale = true
        holder = null
        wake()
    }

    fun setVisible(visible: Boolean) {
        this.visible = visible
        wake()
    }

    /**
     * Runs [action] on the render thread before one of the next frames.
     *
     * This is the only supported way to touch scene state from another thread.
     */
    fun queueEvent(action: Runnable) {
        synchronized(lock) { eventQueue.addLast(action) }
        wake()
    }

    /**
     * Asks for the GPU textures to be dropped at the next safe moment.
     *
     * Deliberately not a [queueEvent]: queued actions are scene state, which is safe to touch at
     * any point in the loop, while this is GL work and is only safe with a current context.
     */
    fun requestTrim() {
        trimRequested = true
        wake()
    }

    fun shutdown() {
        exitRequested = true
        wake()
        // Not joined: the wallpaper engine is torn down on the main thread and a join here would
        // block it behind a frame that is already in flight. The thread observes the flag, releases
        // its own EGL resources and exits on its own.
    }

    private fun wake() {
        synchronized(lock) { lock.notifyAll() }
    }

    // --- Render thread -----------------------------------------------------------------------

    override fun run() {
        vsync.start()
        try {
            loop()
        } finally {
            vsync.quit()
            releaseEgl()
        }
    }

    /**
     * The render loop.
     *
     * Idle waits use a timeout rather than relying on a signal alone. Every input this loop reacts
     * to is a volatile field or a queued runnable, so a timed wait cannot miss one: at worst it
     * observes it a fraction of a second late while the wallpaper is not visible anyway. A
     * signal-only wait would have to hold the lock across the whole state check to be race-free,
     * which would put a main-thread callback behind a frame.
     */
    private fun loop() {
        while (!exitRequested) {
            drainEvents()
            if (exitRequested) return

            val currentHolder = holder
            if (currentHolder == null) {
                // The window is gone. Release the surface but keep the context, so coming back does
                // not have to re-upload every texture.
                //
                // A trim asked for in this state is deliberately left pending: there is no context
                // to honour it with (destroyEglSurface unbinds the one there was), and this is the
                // branch in which the old queued-Runnable trim did its damage.
                if (GlLifecyclePolicy.mayApplyTrim(trimRequested, framePrepared = false)) {
                    trimRequested = false
                    target.trimTextures()
                }
                destroyEglSurface()
                idle()
                continue
            }
            if (!visible || unavailableReported) {
                // A trim asked for while hidden is honoured now, not on the next visible frame
                // (v5.8C). Memory pressure arrives exactly while nothing is drawing -- screen off,
                // an app in front -- and until then the atlas page and every standalone texture
                // stayed allocated for as long as the process was a kill candidate, to be dropped
                // on the first visible frame, where the re-upload is the one cost nobody wanted
                // (v5.8B comment audit). The context is made current on the surface it already
                // has; nothing is created for the purpose.
                if (!unavailableReported && trimRequested && makeCurrentWhileHidden(currentHolder) &&
                    GlLifecyclePolicy.mayApplyTrim(trimRequested, framePrepared = true)
                ) {
                    trimRequested = false
                    target.trimTextures()
                }
                idle()
                continue
            }

            val frameStart = System.nanoTime()
            if (!prepareFrame(currentHolder, pendingWidth, pendingHeight)) {
                if (holder == null) continue
                if (!recoverFromFrameFailure()) reportUnavailable()
                continue
            }
            // From here the context is current, which is the only point in this loop where GL work
            // asked for from another thread may run -- see GlLifecyclePolicy.mayApplyTrim.
            if (GlLifecyclePolicy.mayApplyTrim(trimRequested, framePrepared = true)) {
                trimRequested = false
                target.trimTextures()
            }
            hadWorkingContext = true
            drawFrame(if (anchorTickNanos != 0L) anchorTickNanos else frameStart, anchorTickNanos)
            pace(frameStart)
        }
    }

    /**
     * Makes the context current for a trim while the wallpaper is hidden. Without a context or a
     * usable target nothing was ever uploaded, so there is nothing to give back and the request
     * is dropped; otherwise the surface this window already has is used.
     */
    private fun makeCurrentWhileHidden(holder: SurfaceHolder): Boolean {
        if (eglContext == EGL14.EGL_NO_CONTEXT || !target.isUsable) {
            trimRequested = false
            return false
        }
        return ensureEglSurface(holder) && EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
    }

    private fun idle() {
        // Time no longer accumulates while parked, so the first frame after resuming must not be
        // handed the whole idle period as its delta -- nor start on a tick from before it parked.
        lastFrameNanos = 0L
        anchorTickNanos = 0L
        synchronized(lock) {
            if (!exitRequested && eventQueue.isEmpty()) {
                try {
                    lock.wait(IDLE_WAIT_MS)
                } catch (_: InterruptedException) {
                    currentThread().interrupt()
                }
            }
        }
    }

    private fun drainEvents() {
        while (true) {
            val action = synchronized(lock) {
                if (eventQueue.isEmpty()) null else eventQueue.removeFirst()
            } ?: return
            try {
                action.run()
            } catch (_: RuntimeException) {
                // A misbehaving state update must not take the render thread down with it; the
                // frame that follows simply uses whatever state did land.
            }
        }
    }

    private fun prepareFrame(holder: SurfaceHolder, width: Int, height: Int): Boolean {
        if (!ensureEglContext()) return false
        if (!ensureEglSurface(holder)) return false
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) return false
        if (!target.isUsable && !target.onContextCreated()) return false
        if (width > 0 && height > 0 && (width != currentWidth || height != currentHeight)) {
            currentWidth = width
            currentHeight = height
            target.onSurfaceSizeChanged(width, height)
            callbacks.onGlSurfaceChanged(width, height)
        }
        return currentWidth > 0 && currentHeight > 0
    }

    private fun drawFrame(frameTimeNanos: Long, frameTickNanos: Long) {
        val delta = if (lastFrameNanos == 0L) 0f else ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f)
        lastFrameNanos = frameTimeNanos
        target.beginFrame()
        callbacks.onGlDrawFrame(target, delta.coerceIn(0f, 0.5f))
        target.endFrame()
        // Every frame, the time it should reach the screen -- or "as soon as it is ready" when there
        // is no grid: the request stays on the surface until replaced (FramePacing.presentAt).
        if (presentationTimeSupported) {
            EGLExt.eglPresentationTimeANDROID(
                eglDisplay, eglSurface, FramePacing.presentAt(vsync.current(System.nanoTime()), frameTickNanos),
            )
        }
        if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
            when (EGL14.eglGetError()) {
                EGL14.EGL_CONTEXT_LOST -> {
                    // The context and everything in it is gone. Drop the handles without GL calls,
                    // then rebuild from scratch on the next pass.
                    target.onContextLost()
                    releaseEgl()
                }
                EGL14.EGL_BAD_NATIVE_WINDOW, EGL14.EGL_BAD_SURFACE -> destroyEglSurface()
            }
        }
    }

    /**
     * Sleeps to the tick the next frame starts on, or -- with no grid measured yet -- for what is
     * left of [FramePacing.FALLBACK_INTERVAL_MS] after this frame's cost, as the loop always did.
     */
    private fun pace(frameStartNanos: Long) {
        val now = System.nanoTime()
        val plan = FramePacing.plan(vsync.current(now), anchorTickNanos, frameStartNanos, now)
        // On a grid, sleep to the tick itself (the clock has moved while planning); without one,
        // the old `33 ms - cost`, unchanged.
        sleepNanos(if (plan.anchorTickNanos != 0L) plan.anchorTickNanos - System.nanoTime() else plan.sleepNanos)
        anchorTickNanos = plan.anchorTickNanos
    }

    private fun sleepNanos(nanos: Long) {
        if (nanos <= 0L) return
        try {
            sleep(nanos / 1_000_000L, (nanos % 1_000_000L).toInt())
        } catch (_: InterruptedException) {
            currentThread().interrupt()
        }
    }

    /**
     * A prepared frame failed while the surface is still there. Rebuild EGL, or give up.
     *
     * Everything that can fail in `prepareFrame` is treated identically by EGL itself: a context
     * lost to a GPU driver reset only announces itself at `eglSwapBuffers`, so an `eglMakeCurrent`
     * that has quietly started failing is indistinguishable from one that never worked. Telling
     * them apart by *history* is what this does -- if a frame has ever been drawn, the hardware can
     * clearly do GL, so the honest response is to throw the EGL state away and build it again
     * rather than demote the engine to software for the rest of its life.
     *
     * Bounded by [GlLifecyclePolicy.MAX_CONTEXT_REBUILDS]: a GPU that is not coming back must not
     * keep a render thread spinning on a live wallpaper.
     */
    private fun recoverFromFrameFailure(): Boolean {
        if (!GlLifecyclePolicy.shouldRebuildContext(hadWorkingContext, contextRebuilds)) return false
        contextRebuilds++
        // The same teardown the context-lost path at swap time uses: the target forgets its handles
        // without touching GL, because there is no context to touch it with.
        target.onContextLost()
        destroyEglSurface()
        releaseEglContextOnly()
        return true
    }

    private fun reportUnavailable() {
        if (unavailableReported) return
        unavailableReported = true
        callbacks.onGlUnavailable()
    }

    // --- EGL -------------------------------------------------------------------------------

    private fun ensureEglContext(): Boolean {
        if (eglContext != EGL14.EGL_NO_CONTEXT) return true

        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) return false
        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            eglDisplay = EGL14.EGL_NO_DISPLAY
            return false
        }
        presentationTimeSupported =
            EGL14.eglQueryString(eglDisplay, EGL14.EGL_EXTENSIONS)?.contains("EGL_ANDROID_presentation_time") == true

        // Multisampling first, then the same config without it. The scene draws circles, arcs and
        // thin strokes that `Canvas` antialiases analytically and GL does not, so MSAA is what keeps
        // those edges comparable; a device that cannot supply it still gets a wallpaper.
        eglConfig = chooseConfig(multisample = true) ?: chooseConfig(multisample = false)
        val config = eglConfig ?: return false

        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) return false
        return true
    }

    private fun chooseConfig(multisample: Boolean): EGLConfig? {
        val attribs = if (multisample) {
            intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 0,
                EGL14.EGL_STENCIL_SIZE, 0,
                EGL14.EGL_SAMPLE_BUFFERS, 1,
                EGL14.EGL_SAMPLES, MSAA_SAMPLES,
                EGL14.EGL_NONE,
            )
        } else {
            intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 0,
                EGL14.EGL_STENCIL_SIZE, 0,
                EGL14.EGL_NONE,
            )
        }
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val ok = EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, count, 0)
        if (!ok || count[0] == 0) return null
        return configs[0]
    }

    private fun ensureEglSurface(holder: SurfaceHolder): Boolean {
        if (!GlLifecyclePolicy.mayReuseEglSurface(
                hasEglSurface = eglSurface != EGL14.EGL_NO_SURFACE,
                surfaceStale = eglSurfaceStale,
            )
        ) {
            // Either there is nothing to reuse, or what there is belongs to a window that is gone.
            // Releasing here is what the loop's surface-gone branch would have done, for the case
            // where the replacement arrived before the render thread could look.
            destroyEglSurface()
        }
        if (eglSurface != EGL14.EGL_NO_SURFACE) return true
        val config = eglConfig ?: return false
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = try {
            EGL14.eglCreateWindowSurface(eglDisplay, config, holder, surfaceAttribs, 0)
        } catch (_: IllegalArgumentException) {
            // Thrown when the native window has already gone away between the callback and here.
            EGL14.EGL_NO_SURFACE
        }
        if (eglSurface == EGL14.EGL_NO_SURFACE) return false
        // The size the target was configured for belongs to the previous surface.
        currentWidth = 0
        currentHeight = 0
        return true
    }

    private fun destroyEglSurface() {
        eglSurfaceStale = false
        if (eglSurface == EGL14.EGL_NO_SURFACE) return
        EGL14.eglMakeCurrent(
            eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT,
        )
        EGL14.eglDestroySurface(eglDisplay, eglSurface)
        eglSurface = EGL14.EGL_NO_SURFACE
        currentWidth = 0
        currentHeight = 0
    }

    /**
     * Drops the context and config but keeps the display, so the next frame builds both again.
     *
     * Separate from [releaseEgl], which is the thread's own teardown and also releases the display.
     */
    private fun releaseEglContextOnly() {
        if (eglContext != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            eglContext = EGL14.EGL_NO_CONTEXT
        }
        eglConfig = null
    }

    private fun releaseEgl() {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) return
        // Releasing GL objects needs the context current; if it has been lost, the target has
        // already forgotten its handles and this is a no-op.
        if (eglSurface != EGL14.EGL_NO_SURFACE && eglContext != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
            target.release()
        }
        EGL14.eglMakeCurrent(
            eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT,
        )
        if (eglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            eglSurface = EGL14.EGL_NO_SURFACE
        }
        if (eglContext != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            eglContext = EGL14.EGL_NO_CONTEXT
        }
        EGL14.eglTerminate(eglDisplay)
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglConfig = null
        currentWidth = 0
        currentHeight = 0
    }

    private companion object {
        /** How long an idle render thread parks before re-checking its inputs. */
        const val IDLE_WAIT_MS = 200L
        const val MSAA_SAMPLES = 4
    }
}
