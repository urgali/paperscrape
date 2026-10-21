package com.paperscrape.livewallpaper.engine

/**
 * The lifecycle rules the GL backend's correctness rests on -- which thread a surface goes to, when
 * a context is rebuilt, when a trim may run and when an EGL surface may be reused -- extracted so
 * they can be tested.
 *
 * They used to be spelled out inline in `PaperEngine`'s surface callbacks and in
 * [GlRenderThread]'s frame loop, where nothing could reach them, and each was wrong in a way
 * that only shows up on a device that happens to destroy and recreate a wallpaper surface or lose
 * a GL context -- which is exactly the kind of rule that stays wrong. Same reason
 * `LiveWeatherInputs` and `LiveWeatherSchedule` are pure objects rather than code inside the
 * service.
 */
internal object GlLifecyclePolicy {

    /**
     * What the engine must do when a surface arrives.
     *
     * **One render thread per engine, not per surface.** [GlRenderThread] was written to outlive a
     * surface -- it takes `onSurfaceCreated`/`onSurfaceDestroyed`, and its idle branch documents
     * keeping the EGL context "so coming back does not have to re-upload every texture" -- but the
     * engine never gave it a second surface. `onSurfaceCreated` built a new thread every time and
     * `onSurfaceDestroyed` only cleared the thread's holder, so each destroy/create cycle abandoned
     * a live thread that kept its EGL context, its [GlSceneTarget] and every texture in it for the
     * rest of the process. The keep-context branch was unreachable; [REUSE_THREAD] is what makes it
     * the real path.
     */
    enum class SurfaceAction {
        /** No thread yet: build one, hand it the surface, and it owns GL from here. */
        START_THREAD,

        /** A thread already owns this engine's GL: give it the new surface, do not build a second. */
        REUSE_THREAD,

        /** GL is not in use at all -- the Canvas fallback owns drawing. */
        NO_GL,
    }

    fun surfaceCreated(hasThread: Boolean, canvasFallback: Boolean): SurfaceAction = when {
        canvasFallback -> SurfaceAction.NO_GL
        hasThread -> SurfaceAction.REUSE_THREAD
        else -> SurfaceAction.START_THREAD
    }

    /**
     * How many times a *working* GL context may be rebuilt before the engine gives up on GL.
     *
     * Zero means "no context has ever worked", which is the only case the old code handled: any
     * failure at all latched the engine into the Canvas fallback for the rest of its life. That is
     * right for "this device cannot do EGL" and wrong for everything else -- a GPU driver reset, or
     * the native window dying in the instant before the main thread nulls the holder -- where one
     * unlucky frame silently downgraded a working GL engine to the software path until the user
     * re-selected the wallpaper.
     */
    const val MAX_CONTEXT_REBUILDS = 3

    /**
     * Whether a failed frame should rebuild EGL and try again, or report GL unavailable.
     *
     * Bounded on purpose: an endless retry would spin the render loop against a GPU that is not
     * coming back, which on a live wallpaper is the worst of both worlds -- no picture and a busy
     * thread. After [MAX_CONTEXT_REBUILDS] the engine falls back to Canvas exactly as before.
     */
    fun shouldRebuildContext(hadWorkingContext: Boolean, rebuildsSoFar: Int): Boolean =
        hadWorkingContext && rebuildsSoFar < MAX_CONTEXT_REBUILDS

    /**
     * Whether a memory trim asked for from another thread may run **now**.
     *
     * GL calls need a current context, and the render loop has exactly one moment that guarantees
     * one: after `prepareFrame` has returned true. The trim used to be a queued Runnable, and
     * queued work is drained at the top of the loop -- before any context is made current, and in
     * the surface-gone branch *after* the context has been explicitly unbound. Run there,
     * `glDeleteTextures` silently no-ops while the target forgets the handles anyway, and repacking
     * the atlas' white pixel fails, leaving a target that believes it is usable with nothing to
     * draw flat fills from.
     *
     * The request survives until a frame can honour it, rather than being dropped: a trim asked for
     * while the wallpaper has no window is still worth doing when one comes back, which is exactly
     * when memory pressure tends to arrive.
     */
    fun mayApplyTrim(trimRequested: Boolean, framePrepared: Boolean): Boolean =
        trimRequested && framePrepared

    /**
     * Whether the EGL surface currently held may be drawn into, or has to be built again.
     *
     * An `EGLSurface` belongs to **one** native window. The loop releases it in the branch it takes
     * when the holder is null -- but that branch is only reached if the render thread happens to
     * observe the gap. It usually does not: the engine delivers `onSurfaceDestroyed` and
     * `onSurfaceCreated` back to back, while the render thread is asleep in `pace` (up to one frame)
     * or parked in `idle` (up to a fifth of a second), so by the time it looks, the holder
     * is already the *new* one and the gap is invisible. It would then keep drawing into the window
     * that has gone away, and -- because `ensureEglSurface` only asks "do I have a surface?", never
     * "whose?" -- would never notice.
     *
     * This is a consequence of the render thread outliving the surface: before that, a thread only
     * ever saw one window, so "have one" and "have the right one" were the same question.
     */
    fun mayReuseEglSurface(hasEglSurface: Boolean, surfaceStale: Boolean): Boolean =
        hasEglSurface && !surfaceStale

    /**
     * Whether the `Canvas` loop may run on an engine: only one that has given up on GL
     * ([canvasFallback]), holds a surface the system has not taken back ([hasSurface]), has no GL
     * thread and is not destroyed (v5.15).
     *
     * **"No GL thread" is not "the Canvas fallback".** Before its first surface an engine has no GL
     * thread either, and an engine the system keeps visible may never get a surface: the picker's
     * preview of a package that was stopped, left spinning in the picker's task, is bound once the
     * package can start again and attached to a window nobody shows. The engine's visibility and the
     * first-frame gate used to start the loop on [PaperWallpaperService]'s `drawing` alone, so that
     * preview called `lockCanvas` on a surface that did not exist ten times a second while the screen
     * was on -- each call held by Android up to 100 ms on the main thread, about 4 % of a core
     * (measured on the BV6600, 2026-10-19). Starting the loop from these four facts, and stopping it
     * in the frame when any of them goes, is the repair; a loop started before GL took the surface
     * goes with the same rule.
     */
    fun canvasLoopMayRun(canvasFallback: Boolean, hasSurface: Boolean, hasGlThread: Boolean, destroyed: Boolean): Boolean =
        canvasFallback && hasSurface && !hasGlThread && !destroyed

    /**
     * Whether a switch to the `Canvas` fallback reported from the render thread may still be made: not
     * twice, and not after the engine is destroyed (v5.15). The switch is posted to the main thread and
     * can land after `onDestroy`, which takes an engine off the census of `Canvas` engines only if it
     * already was one -- so a switch applied after it counted an engine nobody would take off again.
     */
    fun mayFallBackToCanvas(alreadyCanvas: Boolean, destroyed: Boolean): Boolean =
        !alreadyCanvas && !destroyed

    /**
     * Whether an engine counts as on screen for the memory policy and for its weather loop: shown by
     * the system **and** holding a surface (v5.15, inventory I-918). An engine with no surface draws
     * nothing whatever the system says of its visibility: counted as visible it kept the memory policy
     * believing something was drawing, and its weather loop fetched for a picture nobody sees.
     */
    fun countsAsVisible(visible: Boolean, hasSurface: Boolean): Boolean =
        visible && hasSurface
}
