package com.paperscrape.livewallpaper.engine

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
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
 * the scene is only ever touched by the thread that draws it. `SpriteCache`'s deliberate lack of
 * synchronisation therefore stays sound, for the same reason it was sound before — one thread
 * touches it — rather than by accident.
 *
 * ## Pacing
 *
 * The loop targets [FRAME_INTERVAL_MS] and subtracts the frame's own measured cost before sleeping,
 * matching what the `Canvas` loop did. It deliberately does **not** free-run at the display's
 * refresh rate: `eglSwapBuffers` blocks on vsync, so an unpaced loop would render at 60, 90 or 120 Hz
 * and do two to four times the work of the renderer it replaces.
 *
 * ## Failure
 *
 * Every EGL step is checked, and any failure reports [Callbacks.onGlUnavailable] exactly once and
 * parks the thread. The engine then falls back to the `Canvas` path, so a device that cannot give
 * this process a GL context still renders a wallpaper.
 */
internal class GlRenderThread(
    private val callbacks: Callbacks,
) : Thread("PaperScrapeGlThread") {

    interface Callbacks {
        /** Called on the render thread once the surface has a size, before the first frame. */
        fun onGlSurfaceChanged(width: Int, height: Int)

        /** Called on the render thread once per frame, between `beginFrame` and `endFrame`. */
        fun onGlDrawFrame(target: SceneCanvas, deltaSeconds: Float)

        /** Called on the render thread when GL cannot be used at all. */
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

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var eglConfig: EGLConfig? = null

    private var currentWidth = 0
    private var currentHeight = 0
    private var lastFrameNanos = 0L

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
        try {
            loop()
        } finally {
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
                destroyEglSurface()
                idle()
                continue
            }
            if (!visible || unavailableReported) {
                idle()
                continue
            }

            val frameStart = System.nanoTime()
            if (!prepareFrame(currentHolder, pendingWidth, pendingHeight)) {
                if (holder == null) continue
                reportUnavailable()
                continue
            }
            drawFrame()
            pace(frameStart)
        }
    }

    private fun idle() {
        // Time no longer accumulates while parked, so the first frame after resuming must not be
        // handed the whole idle period as its delta.
        lastFrameNanos = 0L
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

    private fun drawFrame() {
        val now = System.nanoTime()
        val delta = if (lastFrameNanos == 0L) 0f else ((now - lastFrameNanos) / 1_000_000_000f)
        lastFrameNanos = now
        target.beginFrame()
        callbacks.onGlDrawFrame(target, delta.coerceIn(0f, 0.5f))
        target.endFrame()
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

    private fun pace(frameStartNanos: Long) {
        val costMs = (System.nanoTime() - frameStartNanos) / 1_000_000L
        val sleepMs = FRAME_INTERVAL_MS - costMs
        if (sleepMs > 0) {
            try {
                sleep(sleepMs)
            } catch (_: InterruptedException) {
                currentThread().interrupt()
            }
        }
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
        if (eglSurface == EGL14.EGL_NO_SURFACE) return
        EGL14.eglMakeCurrent(
            eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT,
        )
        EGL14.eglDestroySurface(eglDisplay, eglSurface)
        eglSurface = EGL14.EGL_NO_SURFACE
        currentWidth = 0
        currentHeight = 0
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
        /** ~30 fps, the cadence the scene was tuned at. */
        const val FRAME_INTERVAL_MS = 33L

        /** How long an idle render thread parks before re-checking its inputs. */
        const val IDLE_WAIT_MS = 200L
        const val MSAA_SAMPLES = 4
    }
}
