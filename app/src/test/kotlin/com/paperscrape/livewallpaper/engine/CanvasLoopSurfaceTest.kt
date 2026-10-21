package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **The `Canvas` loop runs only on an engine that has fallen back to `Canvas` and holds a surface** (v5.15, inventory
 * I-913 to I-916; the maintainer's *«E sì»* of 2026-10-19).
 *
 * The error of 2026-10-17: the picker's preview of a stopped package, left spinning in the picker's task, was bound
 * once the package could start again, to a window nobody shows -- an engine with no surface, ever, and no GL thread,
 * since that thread is born with the first surface. Visible, it started the `Canvas` loop on `drawing` alone, and the
 * loop called `lockCanvas` on nothing ten times a second while the screen was on, each call held by Android up to 100 ms
 * on the main thread: about 4 % of a core, and every message of the main thread late by about 50 ms (measured on the
 * BV6600, round v5.15A). The rules are [GlLifecyclePolicy]'s, pure; that the engine asks them where it starts and
 * where it draws a `Canvas` frame is held on its source, because the engine is a `WallpaperService.Engine` and cannot be
 * built on the JVM.
 */
class CanvasLoopSurfaceTest {

    private val bools = listOf(true, false)

    @Test
    fun `the loop may run only with Canvas, a surface, no GL thread and the engine alive`() {
        for (canvas in bools) for (surface in bools) for (gl in bools) for (destroyed in bools) {
            assertEquals(
                "canvas=$canvas surface=$surface gl=$gl destroyed=$destroyed",
                canvas && surface && !gl && !destroyed,
                GlLifecyclePolicy.canvasLoopMayRun(canvas, surface, gl, destroyed),
            )
        }
    }

    @Test
    fun `the engine of 2026-10-17 - visible, no surface, no GL thread - runs no loop`() {
        assertFalse(GlLifecyclePolicy.canvasLoopMayRun(canvasFallback = false, hasSurface = false, hasGlThread = false, destroyed = false))
        // And it is not counted on screen, nor does its weather loop run (inventory I-918).
        assertFalse(GlLifecyclePolicy.countsAsVisible(visible = true, hasSurface = false))
        assertTrue(GlLifecyclePolicy.countsAsVisible(visible = true, hasSurface = true))
        assertFalse(GlLifecyclePolicy.countsAsVisible(visible = false, hasSurface = true))
    }

    @Test
    fun `a switch to Canvas is made once, and never after the engine is destroyed`() {
        assertTrue(GlLifecyclePolicy.mayFallBackToCanvas(alreadyCanvas = false, destroyed = false))
        assertFalse(GlLifecyclePolicy.mayFallBackToCanvas(alreadyCanvas = true, destroyed = false))
        assertFalse("a switch posted from the render thread landing after onDestroy (I-915)", GlLifecyclePolicy.mayFallBackToCanvas(alreadyCanvas = false, destroyed = true))
    }

    // ------------------------------------------------------------------ the engine asks them

    private val engine: String by lazy {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = File(dir, "${prefix}src/main/kotlin/com/paperscrape/livewallpaper/engine/PaperWallpaperService.kt")
                if (f.isFile) return@lazy f.readText()
            }
            dir = dir.parentFile
        }
        error("PaperWallpaperService.kt not found")
    }

    private fun body(signature: String): String {
        val start = engine.indexOf(signature)
        require(start > 0) { "$signature is gone" }
        return engine.substring(start, engine.indexOf("\n        }", start))
    }

    @Test
    fun `every place that starts the Canvas loop asks the rule`() {
        assertTrue(engine.contains("GlLifecyclePolicy.canvasLoopMayRun(canvasFallback, hasSurface, hasGlThread = glThread != null, destroyed = destroyed)"))
        val starts = Regex("""handler\.post\(drawRunnable\)""").findAll(engine).count()
        val guarded = listOf(
            body("private fun onFirstFrameAllowed()").contains("drawing && renderer != null && canvasLoopMayRun()"),
            body("override fun onVisibilityChanged(").contains("if (drawing && canvasLoopMayRun()) {"),
            body("private fun switchToCanvasFallback()").contains("if (drawing && canvasLoopMayRun()) handler.post(drawRunnable)"),
            body("override fun onSurfaceCreated(").contains("if (drawing && canvasLoopMayRun()) {"),
        )
        assertTrue("an unguarded start of the Canvas loop: $guarded", guarded.all { it })
        assertEquals("the four starts, and no other", 4, starts)
    }

    @Test
    fun `a Canvas frame without a surface locks nothing and schedules no next one`() {
        val draw = body("private fun drawFrame()")
        val guard = draw.indexOf("if (!canvasLoopMayRun() || holder.surface?.isValid != true) {")
        assertTrue("the guard is missing", guard > 0)
        assertTrue("the guard comes before lockCanvas", guard < draw.indexOf("holder.lockCanvas()"))
        assertTrue(draw.substring(guard, draw.indexOf("}", guard)).contains("handler.removeCallbacks(drawRunnable)"))
    }

    @Test
    fun `GL taking the surface stops a Canvas loop started before it, and the surface keeps the count`() {
        assertTrue(body("private fun startGlThread(").contains("handler.removeCallbacks(drawRunnable)"))
        assertTrue(body("override fun onSurfaceCreated(").contains("changingOnScreen { hasSurface = true }"))
        assertTrue(body("override fun onSurfaceDestroyed(").contains("changingOnScreen { hasSurface = false }"))
        assertTrue(body("private fun switchToCanvasFallback()").contains("GlLifecyclePolicy.mayFallBackToCanvas(alreadyCanvas = canvasFallback, destroyed = destroyed)"))
    }
}
