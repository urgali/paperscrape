package com.paperscrape.livewallpaper.engine

/**
 * The lifecycle rules the GL backend's correctness rests on -- which thread a surface goes to, when
 * a context is rebuilt, when a trim may run and when an EGL surface may be reused -- kept here so
 * they can be tested.
 *
 * Spelled out inline in `PaperEngine`'s surface callbacks and in
 * [GlRenderThread]'s frame loop, nothing could reach them, and each can be wrong in a way
 * that only shows up on a device that happens to destroy and recreate a wallpaper surface or lose
 * a GL context -- which is exactly the kind of rule that stays wrong. Same reason
 * `LiveWeatherInputs` and `LiveWeatherSchedule` are pure objects rather than code inside the
 * service.
 */
internal object GlLifecyclePolicy {

    /**
     * What the engine must do when a surface arrives.
     *
     * **One render thread per engine, not per surface.** [GlRenderThread] outlives a
     * surface -- it takes `onSurfaceCreated`/`onSurfaceDestroyed`, and its idle branch documents
     * keeping the EGL context "so coming back does not have to re-upload every texture". An
     * `onSurfaceCreated` that built a new thread every time, with an
     * `onSurfaceDestroyed` that only cleared the thread's holder, would abandon at each destroy/create
     * cycle a live thread that kept its EGL context, its [GlSceneTarget] and every texture in it for the
     * rest of the process, and the keep-context branch would be unreachable; [REUSE_THREAD] is what makes
     * it the real path.
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
     * Zero means "no context has ever worked". Latching the engine into the Canvas fallback for the
     * rest of its life at any failure at all is
     * right for "this device cannot do EGL" and wrong for everything else -- a GPU driver reset, or
     * the native window dying in the instant before the main thread nulls the holder -- where one
     * unlucky frame would silently downgrade a working GL engine to the software path until the user
     * re-selected the wallpaper.
     */
    const val MAX_CONTEXT_REBUILDS = 3

    /**
     * Whether a failed frame should rebuild EGL and try again, or report GL unavailable.
     *
     * Bounded on purpose: an endless retry would spin the render loop against a GPU that is not
     * coming back, which on a live wallpaper is the worst of both worlds -- no picture and a busy
     * thread. After [MAX_CONTEXT_REBUILDS] rebuilds, each with fewer than [FRAMES_THAT_RESTORE_THE_BUDGET] frames swapped
     * since the one before ([RebuildBudget]), the next failure sends the engine to the Canvas fallback.
     */
    fun shouldRebuildContext(hadWorkingContext: Boolean, rebuildsSoFar: Int): Boolean =
        hadWorkingContext && rebuildsSoFar < MAX_CONTEXT_REBUILDS

    /**
     * How many frames swapped since the last rebuild give the budget back: about ten seconds at the loop's 30.7 frames a
     * second.
     *
     * The bound is for a GPU that fails again as soon as it is rebuilt, and such a GPU never draws this many frames in a
     * row. An engine lives as long as the wallpaper stays set -- days -- with one render thread
     * ([SurfaceAction.REUSE_THREAD]: a rotation, an unlock or the picker does not build another), so a budget never given
     * back would make the fourth incident of its whole life the end of GL, and the wallpaper would draw on the Canvas
     * fallback, which costs more, until it was set again.
     */
    const val FRAMES_THAT_RESTORE_THE_BUDGET = 300

    /**
     * One render thread's rebuild budget: [rebuilds] counts the rebuilds since the budget was last given back, and
     * [FRAMES_THAT_RESTORE_THE_BUDGET] frames swapped in a row since the last rebuild give it back. A class rather than two
     * fields of the thread so the rule can be driven here, frame by frame, without EGL; one per thread, nothing per frame.
     */
    class RebuildBudget {
        var rebuilds = 0
            private set
        private var framesSinceRebuild = 0

        /** A prepared frame failed: whether to rebuild EGL and try again ([shouldRebuildContext]), counting it if so. */
        fun rebuildAfterFailure(hadWorkingContext: Boolean): Boolean {
            if (!shouldRebuildContext(hadWorkingContext, rebuilds)) return false
            rebuilds++
            framesSinceRebuild = 0
            return true
        }

        /** A frame was drawn and swapped to the surface; one whose swap failed is not reported. */
        fun frameDrawn() {
            if (rebuilds == 0) return
            framesSinceRebuild++
            if (framesSinceRebuild >= FRAMES_THAT_RESTORE_THE_BUDGET) {
                rebuilds = 0
                framesSinceRebuild = 0
            }
        }
    }

    /**
     * A surface's width and height in one `Long`, so the render thread reads both from one volatile write and never a
     * width of one size with a height of another ([surfaceWidth], [surfaceHeight]).
     */
    fun surfaceSize(width: Int, height: Int): Long = (width.toLong() shl 32) or (height.toLong() and 0xFFFFFFFFL)

    fun surfaceWidth(size: Long): Int = (size ushr 32).toInt()

    fun surfaceHeight(size: Long): Int = size.toInt()

    /**
     * Whether a memory trim asked for from another thread may run **now**.
     *
     * GL calls need a current context, and the render loop has exactly one moment that guarantees
     * one: after `prepareFrame` has returned true. A trim queued as a Runnable would run where
     * queued work is drained, at the top of the loop -- before any context is made current, and in
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
     * or parked in `idle` (until the next wake, which the destroy itself sends), so by the time it looks, the holder
     * is already the *new* one and the gap is invisible. It would then keep drawing into the window
     * that has gone away, and -- because `ensureEglSurface` only asks "do I have a surface?", never
     * "whose?" -- would never notice.
     *
     * This is a consequence of the render thread outliving the surface: a thread that only
     * ever saw one window could take "have one" and "have the right one" for the same question.
     */
    fun mayReuseEglSurface(hasEglSurface: Boolean, surfaceStale: Boolean): Boolean =
        hasEglSurface && !surfaceStale

    /**
     * Whether the `Canvas` loop may run on an engine: only one that has given up on GL
     * ([canvasFallback]), holds a surface the system has not taken back ([hasSurface]), has no GL
     * thread and is not destroyed.
     *
     * **"No GL thread" is not "the Canvas fallback".** Before its first surface an engine has no GL
     * thread either, and an engine the system keeps visible may never get a surface: the picker's
     * preview of a package that was stopped, left spinning in the picker's task, is bound once the
     * package can start again and attached to a window nobody shows. A loop the engine's visibility and
     * the first-frame gate started on [PaperWallpaperService]'s `drawing` alone would have that
     * preview call `lockCanvas` on a surface that does not exist ten times a second while the screen
     * is on -- each call held by Android up to 100 ms on the main thread, about 4 % of a core
     * (measured on the BV6600, 2026-10-19). So the loop starts from these four facts, and stops
     * in the frame when any of them goes; a loop started before GL took the surface
     * goes by the same rule.
     */
    fun canvasLoopMayRun(canvasFallback: Boolean, hasSurface: Boolean, hasGlThread: Boolean, destroyed: Boolean): Boolean =
        canvasFallback && hasSurface && !hasGlThread && !destroyed

    /**
     * Whether a switch to the `Canvas` fallback reported from the render thread may still be made: not
     * twice, and not after the engine is destroyed. The switch is posted to the main thread and
     * can land after `onDestroy`, which takes an engine off the census of `Canvas` engines only if it
     * already was one -- so a switch applied after it would count an engine nobody takes off again.
     */
    fun mayFallBackToCanvas(alreadyCanvas: Boolean, destroyed: Boolean): Boolean =
        !alreadyCanvas && !destroyed

    /**
     * Whether an engine counts as on screen for the memory policy and for its weather loop: shown by
     * the system **and** holding a surface. An engine with no surface draws
     * nothing whatever the system says of its visibility: counted as visible it would keep the memory
     * policy believing something is drawing, and its weather loop fetching for a picture nobody sees.
     */
    fun countsAsVisible(visible: Boolean, hasSurface: Boolean): Boolean =
        visible && hasSurface
}
