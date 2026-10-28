package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Renders a [GoldenScene] through the **shipped** [GlSceneTarget], on an offscreen EGL pbuffer.
 *
 * ## Why this exists
 *
 * Every Canvas golden goes through [CanvasSceneTarget], because a golden produced by drawing code
 * the app does not ship proves nothing about the code it does. That leaves [GlSceneTarget] -- hand
 * tessellation, batching, atlas UVs and premultiplied blending, and the backend that draws the
 * wallpaper on every device where EGL works -- for this harness to pin.
 *
 * ## What this is not
 *
 * **Not a second renderer.** Nothing here draws anything. It builds an EGL context, hands the real
 * `GlSceneTarget` to the real [PaperRenderer] through the same [SceneCanvas] seam the wallpaper
 * uses, and reads the framebuffer back. Every pixel is produced by shipped code.
 *
 * ## Why a pbuffer, and why this config
 *
 * A `SurfaceHolder` needs a window, and an instrumented test has no wallpaper surface to borrow. A
 * pbuffer is the same GL, drawn into an offscreen buffer instead. The config is otherwise chosen
 * exactly as [GlRenderThread.chooseConfig] chooses it — 8888, no depth, no stencil, 4x MSAA with a
 * fall back to none — because the tessellated edges that MSAA smooths are precisely what the
 * comparison is sensitive to. A test that quietly ran without multisampling would be measuring a
 * different picture from the one the wallpaper draws.
 */
object GlGolden {

    /** Same frame as the Canvas goldens, so the two are directly comparable. */
    const val WIDTH = SceneGolden.WIDTH
    const val HEIGHT = SceneGolden.HEIGHT

    /** Mirrors `GlRenderThread.MSAA_SAMPLES`. */
    private const val MSAA_SAMPLES = 4

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    /** Where a rejected frame and its diff are written, alongside the Canvas goldens' own. */
    fun outputDir(): File =
        File(instrumentation.targetContext.getExternalFilesDir(null), "golden-output")
            .apply { mkdirs() }

    /** What the harness managed to obtain, for the report a failure prints. */
    data class Result(val bitmap: Bitmap, val multisampled: Boolean, val renderer: String)

    /**
     * The two comparisons a GL frame has to pass, and what sets their limits.
     *
     * Every limit sits above the disagreement of two correct GL drivers drawing the same frame and
     * below what a deliberately broken backend moves. Two correct implementations differ simply
     * because they are different rasterisers, and nothing under that floor is detectable by a pixel
     * test allowed to run on more than one machine. A percentage is the share of the 360x800 frame
     * whose largest channel delta reaches the stated level; every run logs its own readings under
     * `CANVASCROSS` and `GLREGION`.
     *
     * ### [GlTarget]: GL still draws what GL drew
     *
     * Against a committed `gl-<name>.png`. The strict one, and it can afford to be: at `>=16` two
     * correct drivers differ by a fraction of its 0.50 %, and a broken blend crosses that limit on
     * all three scenes.
     *
     * ### [CanvasCross]: GL still draws what Canvas draws
     *
     * Against the Canvas golden the rest of the suite uses. Far looser, necessarily: the two
     * backends approximate the same picture through an analytic rasteriser on one side and hand
     * tessellation on the other, and a healthy frame already differs at `>=32` by more than the
     * whole [GlTarget] limit. What it buys is the assertion the GL golden cannot make -- that the
     * backend which ships has not drifted away from the one every other golden pins -- and it
     * survives a driver change, which the GL golden may not. Two gates, because a structural
     * regression moves a region of pixels a long way while a subtle one moves a great many pixels a
     * moderate amount.
     *
     * ### What neither can see
     *
     * A change smaller than the driver floor. The sun's glow is drawn at a centre alpha of at most 90
     * over a bright sky, so reducing `drawRadialGlow`'s fan to a single triangle -- destroying the
     * glow's shape -- moves no pixel by more than 15/255, and at `>=8` it reaches less of the frame
     * than two correct drivers already differ by. That is a property of the effect, not a gap to be
     * closed by lowering a threshold: a limit under the driver floor fails on the next machine
     * instead of on the next bug. [Region] is the gate that sees it.
     */
    object Tolerance {
        /** Against the committed GL golden: same backend, possibly a different GL driver. */
        object GlTarget {
            const val CHANNEL = 16
            const val MAX_FRACTION = 0.005
        }

        /**
         * Against the committed GL golden, **inside a named region only**: what the whole-frame
         * gates cannot do, for a reason of arithmetic rather than tuning.
         *
         * Driver-to-driver disagreement is *spread*: it is anti-aliased edges, and there are edges
         * everywhere, so it lands at a similar rate in any patch of the frame. A regression in one
         * effect is *concentrated*: it moves a large share of one small region and nothing outside
         * it. Divided by the whole frame, a broken glow is indistinguishable from driver noise;
         * divided by the glow's own bounding box, the same regression is an order of magnitude
         * above it.
         *
         * [CHANNEL] is **4**, a quarter of the 16 the whole-frame gate uses, because the effect it
         * watches, the sun's glow, moves no pixel by more than 15/255 when its shape is destroyed:
         * at `>=16` that regression is worth no pixel at all, and no whole-frame limit at any
         * fraction could see it. A channel of 4 over the whole frame would sit far under the
         * driver floor; over the glow's 254x254 region it does not. [MAX_FRACTION] is **0.50%**:
         * inside the glow, at channel 4, a second driver's disagreement sits far below it, and the
         * glow at half intensity or with its fan reduced to a triangle several times above it.
         * Each region's reading is logged under `GLREGION`, pass or fail.
         */
        object Region {
            const val CHANNEL = 4
            const val MAX_FRACTION = 0.005
        }

        /** Against the Canvas golden: same picture, deliberately different arithmetic. */
        object CanvasCross {
            const val COARSE_CHANNEL = 64
            const val COARSE_MAX_FRACTION = 0.010
            const val FINE_CHANNEL = 32
            const val FINE_MAX_FRACTION = 0.020
        }
    }

    /**
     * What one frame cost the batcher: see [GlSceneTarget.drawCalls]. [vertices] are the triangles' corners
     * ([GlSceneTarget.drawnVertices]), [written] the distinct vertices written for them ([GlSceneTarget.writtenVertices]).
     */
    data class DrawCount(val drawCalls: Int, val vertices: Long, val written: Long, val sprites: Int)

    /**
     * Renders [scene] and reports what the final frame cost the batcher.
     *
     * The warm-up frames are drawn and discarded exactly as [render] draws them, and the count is
     * taken from the **last** frame alone: the first frame of a scene uploads its sprites, and an
     * upload is not something a steady frame pays for.
     */
    fun countDrawCalls(scene: GoldenScene): DrawCount = withContext { gl ->
        val renderer = PaperRenderer(WIDTH, HEIGHT, instrumentation.targetContext)
        scene.configure(renderer)
        var clock = SceneTime(scene.sceneSeconds)
        repeat(scene.warmUpFrames) {
            clock += scene.warmUpDeltaSeconds
            gl.beginFrame()
            renderer.draw(gl, scene.dayPhase, clock, scene.warmUpDeltaSeconds)
            gl.endFrame()
        }
        gl.beginFrame()
        renderer.draw(gl, scene.dayPhase, clock, 0f)
        gl.endFrame()
        GLES20.glFinish()
        DrawCount(gl.drawCalls, gl.drawnVertices, gl.writtenVertices, gl.spriteBlits)
    }

    /** What [draw] costs the batcher, drawn alone in a fresh frame: one primitive's triangles and vertices. */
    fun countPrimitive(draw: (SceneCanvas) -> Unit): DrawCount = withContext { gl ->
        gl.beginFrame()
        draw(gl)
        gl.endFrame()
        GLES20.glFinish()
        DrawCount(gl.drawCalls, gl.drawnVertices, gl.writtenVertices, gl.spriteBlits)
    }

    /**
     * The draw calls of one frame of [scene] at each swipe offset of [offsets], in turn, in one context: the
     * street swept across its copies, so the places leave the screen and come back with other buildings. And the
     * vertices each frame drew, so a sweep says how near the batch's end its fullest frame came. Without a visitor
     * in flight the scene's clock stands still and only the offset moves. With [sleighInFlight] Santa's sleigh is
     * held in the middle of its crossing at every frame, with [horsemanInFlight] the Headless Horseman sets off,
     * and the scene's clock moves [SLEIGH_SWEEP_STEP_SECONDS] a frame: the reindeer or the horse go through their
     * four poses and the gifts or the pumpkins fall, as on the phone. With the clock still a sweep would draw two
     * poses and nothing falling.
     */
    fun countDrawCallsAcross(
        scene: GoldenScene,
        offsets: FloatArray,
        sleighInFlight: Boolean = false,
        horsemanInFlight: Boolean = false,
    ): Sweep = withContext { gl ->
        val renderer = PaperRenderer(WIDTH, HEIGHT, instrumentation.targetContext)
        scene.configure(renderer)
        if (sleighInFlight) renderer.holdSantaSleighForTest(0.5f)
        // The Headless Horseman set off throwing his first pumpkin, the clock moving a pose a frame, so his four poses go by
        // and his pumpkins on fire fall.
        if (horsemanInFlight) renderer.startHeadlessHorsemanFlightForTest()
        renderer.swipeScrollEnabled = true
        val step = if (sleighInFlight || horsemanInFlight) SLEIGH_SWEEP_STEP_SECONDS else 0f
        var clock = SceneTime(scene.sceneSeconds)
        val vertices = LongArray(offsets.size)
        val clocks = DoubleArray(offsets.size)
        val gifts = IntArray(offsets.size)
        val pumpkins = IntArray(offsets.size)
        val calls = IntArray(offsets.size) { i ->
            renderer.homeScreenOffset = offsets[i]
            if (i > 0) clock += step
            clocks[i] = clock.seconds
            gl.beginFrame()
            renderer.draw(gl, scene.dayPhase, clock, if (i > 0) step else 0f)
            gl.endFrame()
            GLES20.glFinish()
            vertices[i] = gl.drawnVertices
            gifts[i] = renderer.santaGiftsFallingForTest()
            pumpkins[i] = renderer.horsemanPumpkinsFallingForTest()
            gl.drawCalls
        }
        Sweep(calls, vertices, clocks, gifts, pumpkins)
    }

    /** A visitor sweep's step: one pose of the gallop, six a second, so consecutive frames go through the four poses in turn. */
    const val SLEIGH_SWEEP_STEP_SECONDS = 1f / 6f

    /**
     * What a sweep counted, a frame an offset: its draw calls, its vertices, the scene's clock and the gifts falling. The
     * clock as the frame was given it, a `Double` as [SceneTime] keeps it: a pose worked out from a `Float` copy of it
     * differs from the drawn one for some frames.
     */
    class Sweep(
        val calls: IntArray,
        val vertices: LongArray,
        val clocks: DoubleArray,
        val giftsFalling: IntArray,
        /** The Horseman's pumpkins in the air at each frame, thrown from his hand. */
        val pumpkinsFalling: IntArray = IntArray(calls.size),
    )

    /**
     * One frame of [draw] on the GPU backend, read back: for a test that sets one operation on both
     * backends side by side (`ClippedSpriteAgreementTest`). The frame starts cleared to opaque black,
     * as every frame of the wallpaper does ([GlSceneTarget.beginFrame]), in the same pbuffer context
     * [render] builds.
     */
    fun drawOnce(draw: (SceneCanvas) -> Unit): Bitmap = withContext { gl ->
        gl.beginFrame()
        draw(gl)
        gl.endFrame()
        GLES20.glFinish()
        readFramebuffer()
    }

    /** What one context ended up holding after a sweep, and which of the sleigh's five pieces were among it. */
    data class Occupancy(
        val entries: Int,
        val standalone: Int,
        val rowsUsed: Int,
        val sleighPiecesHeld: Int = 0,
        /** Of the drawings of Halloween's things -- [HALLOWEEN_PIECES] -- how many the sweep's atlas holds. */
        val halloweenPiecesHeld: Int = 0,
    )

    /**
     * The drawings of Halloween's things, nineteen: the Horseman's twelve pieces (the horse and its legs in four poses, the
     * rider's body, his arm, the pumpkin in his hand, the cape), the laughing face and its light, the scarecrow and its
     * light, the cobweb, the door's jack-o'-lantern and its light.
     */
    val HALLOWEEN_PIECES: IntArray
        get() = HorsemanTable.HORSE_RES + HorsemanTable.LEGS_RES +
            intArrayOf(
                HorsemanTable.BODY_RES, HorsemanTable.ARM_RES, HorsemanTable.PUMPKIN_RES, HorsemanTable.CAPE_RES,
                com.paperscrape.livewallpaper.R.drawable.moon_jack_o_lantern_laugh,
                com.paperscrape.livewallpaper.R.drawable.moon_jack_o_lantern_laugh_light,
                ScarecrowTable.RES, ScarecrowTable.LIGHT_RES,
                HalloweenDecorationTable.WEB_RES,
                HalloweenDecorationTable.PUMPKIN_RES, HalloweenDecorationTable.PUMPKIN_LIGHT_RES,
            )

    /**
     * Draws one crowded frame of every theme in [themeIds] **in a single context**, and reports what
     * the texture cache ended up holding.
     *
     * One context on purpose: the atlas is a per-process allocation and the question is what a
     * wallpaper left running accumulates, not what one scene needs.
     */
    fun sweepThemes(themeIds: List<String>): Occupancy = withContext { gl ->
        // Every theme by day, and then what a theme by day does not draw: Santa's sleigh in flight, by day and at
        // night, and the Halloween moon with its light at night.
        val sweep = themeIds.map { themeId -> Triple(themeId, GoldenScene.day(), false) } +
            listOf(Triple("christmas", GoldenScene.day(12f), true), Triple("christmas", GoldenScene.night(1f), true), Triple("halloween", GoldenScene.night(22f), false))
        for ((themeId, dayPhase, sleighInFlight) in sweep) {
            val scene = GoldenScene(
                name = "sweep-$themeId",
                dayPhase = dayPhase,
                themeId = themeId,
                customise = { base ->
                    // The people at their fullest, and the distant houses at 100 % on both mountain
                    // layers of every theme, whatever the theme ships: the most a wallpaper left
                    // running can have drawn.
                    base.copy(
                        people = base.people.copy(visible = true, density = 1f),
                        peopleNightDensity = 1f,
                        distantHouses = DistantHousesConfig(visible = true, density = 1f),
                        mountainsFront = base.mountainsFront.copy(visible = true, density = 1f),
                        mountainsBack = base.mountainsBack.copy(visible = true, density = 1f),
                        santaEnabled = base.santaEnabled || sleighInFlight,
                    )
                },
            )
            val renderer = PaperRenderer(WIDTH, HEIGHT, instrumentation.targetContext)
            scene.configure(renderer)
            if (sleighInFlight) renderer.holdSantaSleighForTest(0.5f)
            var clock = SceneTime(scene.sceneSeconds)
            repeat(scene.warmUpFrames) {
                clock += scene.warmUpDeltaSeconds
                gl.beginFrame()
                renderer.draw(gl, scene.dayPhase, clock, scene.warmUpDeltaSeconds)
                gl.endFrame()
            }
            gl.beginFrame()
            renderer.draw(gl, scene.dayPhase, clock, 0f)
            gl.endFrame()
            // The sleigh a pose later too: its two reindeer stand two poses apart, so two frames a pose apart put all four
            // poses in the atlas, where one frame holds two of them.
            if (sleighInFlight) {
                clock += SLEIGH_SWEEP_STEP_SECONDS
                gl.beginFrame()
                renderer.draw(gl, scene.dayPhase, clock, SLEIGH_SWEEP_STEP_SECONDS)
                gl.endFrame()
            }
        }
        // Halloween as it ships, at night and at noon with the night by day: the scarecrows at 100 %, the decorations, the
        // moon laughing (the scene's clock at 66.5 s, in a laugh) and the Horseman in flight, four frames a pose apart so
        // his horse's four poses are all in the atlas.
        for (hour in listOf(22f, 12f)) {
            val scene = GoldenScene(
                name = "sweep-halloween-everything-${hour.toInt()}",
                dayPhase = if (hour < 18f) GoldenScene.day(hour) else GoldenScene.night(hour),
                themeId = "halloween",
                sceneSeconds = 66.5,
                customise = { base ->
                    base.copy(
                        people = base.people.copy(visible = true, density = 1f),
                        peopleNightDensity = 1f,
                        scarecrows = base.scarecrows.copy(visible = true, density = 1f),
                    )
                },
            )
            val renderer = PaperRenderer(WIDTH, HEIGHT, instrumentation.targetContext)
            scene.configure(renderer)
            renderer.startHeadlessHorsemanFlightForTest()
            var clock = SceneTime(scene.sceneSeconds)
            repeat(4) { frame ->
                if (frame > 0) clock += SLEIGH_SWEEP_STEP_SECONDS
                gl.beginFrame()
                renderer.draw(gl, scene.dayPhase, clock, if (frame > 0) SLEIGH_SWEEP_STEP_SECONDS else 0f)
                gl.endFrame()
            }
        }
        GLES20.glFinish()
        val sleighPieces = intArrayOf(SantaSleighTable.SLEIGH_RES) + SantaSleighTable.DEER_RES
        Occupancy(
            gl.uploadedEntries, gl.standaloneTextures, gl.atlasRowsUsed,
            sleighPieces.count { gl.holdsSprite(it) },
            HALLOWEEN_PIECES.count { gl.holdsSprite(it) },
        )
    }

    /**
     * Draws [scene] through a real `GlSceneTarget` and returns the framebuffer.
     *
     * Throws rather than returning null when EGL cannot be brought up: a silently skipped GL test
     * is worse than none, because the suite goes green while covering nothing.
     */
    fun render(scene: GoldenScene): Result {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "no EGL display" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }

        var multisampled = true
        var config = chooseConfig(display, multisample = true)
        if (config == null) {
            multisampled = false
            config = chooseConfig(display, multisample = false)
        }
        checkNotNull(config) { "no pbuffer-capable RGBA8888 EGL config" }

        var context: EGLContext = EGL14.EGL_NO_CONTEXT
        var surface: EGLSurface = EGL14.EGL_NO_SURFACE
        var target: GlSceneTarget? = null
        try {
            context = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
            )
            check(context != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed: ${EGL14.eglGetError()}" }

            surface = EGL14.eglCreatePbufferSurface(
                display, config,
                intArrayOf(EGL14.EGL_WIDTH, WIDTH, EGL14.EGL_HEIGHT, HEIGHT, EGL14.EGL_NONE), 0,
            )
            check(surface != EGL14.EGL_NO_SURFACE) { "eglCreatePbufferSurface failed: ${EGL14.eglGetError()}" }
            check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "eglMakeCurrent failed" }

            val gl = GlSceneTarget()
            target = gl
            check(gl.onContextCreated()) { "GlSceneTarget could not build its program" }
            gl.onSurfaceSizeChanged(WIDTH, HEIGHT)

            // The same renderer, configured by the same GoldenScene, as the Canvas golden. Built
            // fresh so nothing carries between scenes.
            val renderer = PaperRenderer(WIDTH, HEIGHT, instrumentation.targetContext)
            scene.configure(renderer)

            // Warmed up for the scene's own `warmUpFrames`, as the Canvas harness warms it, so a scene that needs
            // frames to bring its traffic on screen gets the same number of them on both backends.
            // Each warm-up frame is a real begin/draw/end, because a half-frame is not a state the
            // wallpaper is ever in.
            var clock = SceneTime(scene.sceneSeconds)
            repeat(scene.warmUpFrames) {
                clock += scene.warmUpDeltaSeconds
                gl.beginFrame()
                renderer.draw(gl, scene.dayPhase, clock, scene.warmUpDeltaSeconds)
                gl.endFrame()
            }

            gl.beginFrame()
            renderer.draw(gl, scene.dayPhase, clock, 0f)
            gl.endFrame()
            GLES20.glFinish()

            return Result(readFramebuffer(), multisampled, GLES20.glGetString(GLES20.GL_RENDERER) ?: "?")
        } finally {
            // Release in the reverse order of creation, and only while the context is still
            // current -- `release()` makes GL calls.
            if (target != null && surface != EGL14.EGL_NO_SURFACE) target.release()
            EGL14.eglMakeCurrent(
                display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT,
            )
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }

    /**
     * Builds the same pbuffer context [render] builds, runs [body] on it, and tears it down.
     *
     * One context for every helper that counts, sweeps or draws once, and the config from the same
     * [chooseConfig] as [render]: every further copy of the EGL setup would be another place for the
     * config to drift from `GlRenderThread.chooseConfig`, which is the one thing about this harness
     * that must not drift.
     */
    private fun <T> withContext(body: (GlSceneTarget) -> T): T {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "no EGL display" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }
        val config = chooseConfig(display, multisample = true) ?: chooseConfig(display, multisample = false)
        checkNotNull(config) { "no pbuffer-capable RGBA8888 EGL config" }
        var context: EGLContext = EGL14.EGL_NO_CONTEXT
        var surface: EGLSurface = EGL14.EGL_NO_SURFACE
        var target: GlSceneTarget? = null
        try {
            context = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
            )
            check(context != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed: ${EGL14.eglGetError()}" }
            surface = EGL14.eglCreatePbufferSurface(
                display, config,
                intArrayOf(EGL14.EGL_WIDTH, WIDTH, EGL14.EGL_HEIGHT, HEIGHT, EGL14.EGL_NONE), 0,
            )
            check(surface != EGL14.EGL_NO_SURFACE) { "eglCreatePbufferSurface failed" }
            check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "eglMakeCurrent failed" }
            val gl = GlSceneTarget()
            target = gl
            check(gl.onContextCreated()) { "GlSceneTarget could not build its program" }
            gl.onSurfaceSizeChanged(WIDTH, HEIGHT)
            return body(gl)
        } finally {
            if (target != null && surface != EGL14.EGL_NO_SURFACE) target.release()
            EGL14.eglMakeCurrent(
                display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT,
            )
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }

    private fun chooseConfig(display: EGLDisplay, multisample: Boolean): EGLConfig? {
        val base = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            // The one deliberate difference from GlRenderThread: PBUFFER where the wallpaper asks
            // for WINDOW. Everything below is identical.
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 0,
            EGL14.EGL_STENCIL_SIZE, 0,
        )
        val attribs = if (multisample) {
            base + intArrayOf(EGL14.EGL_SAMPLE_BUFFERS, 1, EGL14.EGL_SAMPLES, MSAA_SAMPLES, EGL14.EGL_NONE)
        } else {
            base + intArrayOf(EGL14.EGL_NONE)
        }
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val ok = EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0)
        return if (ok && count[0] > 0) configs[0] else null
    }

    /**
     * `glReadPixels` into an ARGB_8888 bitmap.
     *
     * Two conversions, both mandatory and both easy to forget: GL hands back rows bottom-to-top,
     * and it hands back RGBA where `Bitmap`'s int packing is ARGB. Getting either wrong produces a
     * picture that is *nearly* right, which is the worst kind of wrong for a golden.
     */
    private fun readFramebuffer(): Bitmap {
        val buffer = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, WIDTH, HEIGHT, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
        val error = GLES20.glGetError()
        check(error == GLES20.GL_NO_ERROR) { "glReadPixels failed with 0x${error.toString(16)}" }
        buffer.rewind()

        val pixels = IntArray(WIDTH * HEIGHT)
        val row = ByteArray(WIDTH * 4)
        for (y in 0 until HEIGHT) {
            buffer.get(row)
            val destRow = (HEIGHT - 1 - y) * WIDTH
            for (x in 0 until WIDTH) {
                val i = x * 4
                val r = row[i].toInt() and 0xFF
                val g = row[i + 1].toInt() and 0xFF
                val b = row[i + 2].toInt() and 0xFF
                val a = row[i + 3].toInt() and 0xFF
                pixels[destRow + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(pixels, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    }

    /**
     * The whole check for one scene: the GL backend still draws what it drew, and still draws what
     * the Canvas backend draws.
     *
     * Both, and not either alone. The GL golden is the sensitive one but pins only this backend
     * against itself, so on the day a driver change forces it to be regenerated it would happily
     * bless a real bug at the same time; the Canvas cross-check is what stands in the way of that,
     * and it is the one that keeps meaning something when the pixels legitimately move.
     */
    fun assertGlBackendUnchanged(scene: GoldenScene) {
        // The same rule the Canvas harness runs, for the same reason: this comparison is against a
        // committed file too, and a scene that warmed a storm up would flake here as well.
        scene.requireDeterministicLightning()
        val result = render(scene)
        if (updating()) {
            write(result.bitmap, File(outputDir(), "gl-${scene.name}.png"))
            return
        }
        assertMatchesGlGolden(scene, result)
        assertRegionsUnchanged(scene, result)
        assertMatchesCanvasGolden(scene, result)
    }

    /** Strict: the shipped backend's own output, against the committed `gl-<name>.png`. */
    private fun assertMatchesGlGolden(scene: GoldenScene, result: Result) {
        val expected = readAsset("gl-${scene.name}.png")
        if (expected == null) {
            write(result.bitmap, File(outputDir(), "gl-${scene.name}.png"))
            throw AssertionError(
                "No GL golden committed for '${scene.name}'. A frame was written to " +
                    "${outputDir()}/gl-${scene.name}.png -- look at it, and if it is right, commit " +
                    "it to app/src/androidTest/assets/golden/.",
            )
        }
        val (differing, maskedFraction) = countAtLeastInInteriors(
            expected,
            result.bitmap,
            Tolerance.GlTarget.CHANNEL,
        )
        if (maskedFraction > InteriorMask.MAX_MASKED_FRACTION) {
            reject(scene, result, expected, Tolerance.GlTarget.CHANNEL)
            throw AssertionError(
                "'${scene.name}' is ${"%.1f".format(maskedFraction * 100)}% edge, so there is not " +
                    "enough flat interior left to judge it by -- the comparison would pass on " +
                    "anything. See GlGolden.InteriorMask.",
            )
        }
        val displaced = edgeDisplacement(expected, result.bitmap)
        if (displaced > EdgeDisplacement.MAX_DISPLACED_FRACTION) {
            reject(scene, result, expected, Tolerance.GlTarget.CHANNEL)
            throw AssertionError(
                "The GL backend's output for '${scene.name}' has moved: " +
                    "${"%.2f".format(displaced * 100)}% of its outline has no counterpart within " +
                    "one pixel of the golden's, limit " +
                    "${"%.2f".format(EdgeDisplacement.MAX_DISPLACED_FRACTION * 100)}%. That is not " +
                    "a driver placing an edge differently -- something is somewhere else. " +
                    "${describe(result)}. See GlGolden.EdgeDisplacement.",
            )
        }
        val judged = (WIDTH * HEIGHT) * (1.0 - maskedFraction)
        val fraction = differing / judged
        if (fraction > Tolerance.GlTarget.MAX_FRACTION) {
            reject(scene, result, expected, Tolerance.GlTarget.CHANNEL)
            throw AssertionError(
                "The GL backend's own output for '${scene.name}' changed: " +
                    "${"%.3f".format(fraction * 100)}% of its flat interiors differ by " +
                    ">=${Tolerance.GlTarget.CHANNEL} per channel, limit " +
                    "${"%.3f".format(Tolerance.GlTarget.MAX_FRACTION * 100)}% " +
                    "(${"%.1f".format(maskedFraction * 100)}% of the frame is edge and was not " +
                    "judged -- see GlGolden.InteriorMask). ${describe(result)}. " +
                    "Two correct GL drivers differ by about 0.12% at this level, so this is very " +
                    "unlikely to be a driver difference -- look at the frame and the diff in " +
                    "${outputDir()} before regenerating anything.",
            )
        }
    }

    /**
     * Region-targeted, against the committed GL golden.
     *
     * Uses the scene's own [GoldenScene.focus] list: the rectangles that name what the scene is about, which on the
     * Canvas golden only name (its whole frame admits no differing pixel) and here are a gate. A scene with no focus region is
     * simply not covered by this gate, which is correct: naming a region is a claim about what the
     * scene is *for*, and inventing one for every scene would produce assertions nobody could
     * justify.
     */
    private fun assertRegionsUnchanged(scene: GoldenScene, result: Result) {
        if (scene.focus.isEmpty()) return
        val expected = readAsset("gl-${scene.name}.png") ?: return
        for (focus in scene.focus) {
            val differing = countAtLeastIn(expected, result.bitmap, Tolerance.Region.CHANNEL, focus)
            val fraction = differing / focus.area.toDouble()
            // Always on the record, pass or fail: the headroom is the evidence the gate works.
            android.util.Log.i(
                "GLREGION",
                "${scene.name}/${focus.label}: ${"%.3f".format(fraction * 100)}% of " +
                    "${focus.area}px differ by >=${Tolerance.Region.CHANNEL} " +
                    "(limit ${"%.3f".format(Tolerance.Region.MAX_FRACTION * 100)}%)",
            )
            if (fraction > Tolerance.Region.MAX_FRACTION) {
                reject(scene, result, expected, Tolerance.Region.CHANNEL)
                throw AssertionError(
                    "The GL backend changed inside '${focus.label}' of '${scene.name}' " +
                        "(${focus.left},${focus.top})-(${focus.right},${focus.bottom}): " +
                        "$differing of ${focus.area} pixels differ by " +
                        ">=${Tolerance.Region.CHANNEL} per channel " +
                        "(${"%.3f".format(fraction * 100)}%, limit " +
                        "${"%.3f".format(Tolerance.Region.MAX_FRACTION * 100)}%). ${describe(result)}. " +
                        "This gate exists to catch a regression confined to one effect, which the " +
                        "whole-frame limits cannot see. Look at the frame and the diff in " +
                        "${outputDir()} before regenerating anything.",
                )
            }
        }
    }

    /** Loose and cross-backend: the GL frame against the Canvas golden the rest of the suite uses. */
    private fun assertMatchesCanvasGolden(scene: GoldenScene, result: Result) {
        val expected = readAsset("${scene.name}.png")
            ?: throw AssertionError(
                "No Canvas golden committed for '${scene.name}'; the GL suite cross-checks against it.",
            )
        require(expected.width == WIDTH && expected.height == HEIGHT) {
            "golden '${scene.name}' is ${expected.width}x${expected.height}, expected ${WIDTH}x$HEIGHT"
        }
        val coarse = countAtLeast(expected, result.bitmap, Tolerance.CanvasCross.COARSE_CHANNEL)
        val fine = countAtLeast(expected, result.bitmap, Tolerance.CanvasCross.FINE_CHANNEL)
        val total = (WIDTH * HEIGHT).toDouble()
        val coarseFraction = coarse / total
        val fineFraction = fine / total
        android.util.Log.i(
            "CANVASCROSS",
            "${scene.name} coarse=${"%.4f".format(coarseFraction * 100)}% " +
                "fine=${"%.4f".format(fineFraction * 100)}%",
        )

        if (coarseFraction > Tolerance.CanvasCross.COARSE_MAX_FRACTION ||
            fineFraction > Tolerance.CanvasCross.FINE_MAX_FRACTION
        ) {
            reject(scene, result, expected, Tolerance.CanvasCross.COARSE_CHANNEL)
            throw AssertionError(
                "The GL backend no longer draws '${scene.name}' the way the Canvas backend does. " +
                    "${"%.3f".format(coarseFraction * 100)}% of pixels differ by " +
                    ">=${Tolerance.CanvasCross.COARSE_CHANNEL} per channel (limit " +
                    "${"%.3f".format(Tolerance.CanvasCross.COARSE_MAX_FRACTION * 100)}%), and " +
                    "${"%.3f".format(fineFraction * 100)}% by " +
                    ">=${Tolerance.CanvasCross.FINE_CHANNEL} (limit " +
                    "${"%.3f".format(Tolerance.CanvasCross.FINE_MAX_FRACTION * 100)}%). " +
                    "${describe(result)}. The GL frame and a diff are in ${outputDir()}; the " +
                    "tolerance is measured, see GlGolden.Tolerance.",
            )
        }
    }

    private fun updating(): Boolean =
        InstrumentationRegistry.getArguments().getString("updateGoldens")?.toBoolean() == true

    private fun describe(result: Result) =
        "GL renderer '${result.renderer}', MSAA ${if (result.multisampled) "on" else "unavailable"}"

    /**
     * The pixels a cross-driver comparison may judge: everything not within one pixel of an edge.
     *
     * ### Why not the whole frame
     *
     * Two conformant rasterisers disagree about which side of a boundary a pixel falls on: each
     * silhouette is placed slightly differently, with nothing moved and nothing composed, ordered,
     * blended or shaded differently. This art style is the worst case for a whole-frame count. The
     * scene is flat colour meeting flat colour along hard edges, so half a pixel of edge placement
     * makes a *maximal* per-channel delta along every silhouette, and the frame is nothing but
     * silhouettes: counted over the whole frame, the difference between two GPUs measures how much
     * outline a scene contains.
     *
     * ### What this does instead
     *
     * The threshold stays strict; what the mask sets is which pixels it is applied to. A pixel is
     * judged unless it is within one pixel of a luminance step in either image. A real regression --
     * a sprite moved, a colour wrong, an object missing, a tint dropped, a layer reordered -- changes
     * flat interiors over large areas, which is exactly what survives the mask.
     * `GlGoldenMetricTest` mutates a frame in each of those ways and checks that each is still
     * caught.
     *
     * [MAX_MASKED_FRACTION] is the guard: if a scene were ever so full of detail that the band
     * covered most of it, the comparison would stop meaning anything, and that fails rather than
     * passing quietly.
     */
    internal object InteriorMask {

        /** A luminance step this large starts an edge. Below it, flat colour. */
        const val EDGE_STEP = 12

        /** More masked than this and the check has nothing left to judge. */
        const val MAX_MASKED_FRACTION = 0.40

        /**
         * True where the pixel may be judged: no edge within one pixel, in either image.
         *
         * One array of `WIDTH * HEIGHT` booleans, built once per comparison. The goldens are
         * 360x800, so this is 288 000 bytes on a test thread and never on the draw path.
         */
        fun of(expected: IntArray, actual: IntArray, width: Int, height: Int): BooleanArray {
            val edge = BooleanArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val i = y * width + x
                    val here = luma(expected[i])
                    val hereActual = luma(actual[i])
                    var step = 0
                    if (x > 0) {
                        step = maxOf(step, abs(here - luma(expected[i - 1])))
                        step = maxOf(step, abs(hereActual - luma(actual[i - 1])))
                    }
                    if (y > 0) {
                        step = maxOf(step, abs(here - luma(expected[i - width])))
                        step = maxOf(step, abs(hereActual - luma(actual[i - width])))
                    }
                    if (step > EDGE_STEP) edge[i] = true
                }
            }
            // Dilate by one pixel: the disagreement is *about* where the edge is, so the pixel on
            // either side of it is as much in question as the edge pixel itself.
            val judged = BooleanArray(width * height) { true }
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (!edge[y * width + x]) continue
                    for (dy in -1..1) {
                        val yy = y + dy
                        if (yy !in 0 until height) continue
                        for (dx in -1..1) {
                            val xx = x + dx
                            if (xx in 0 until width) judged[yy * width + xx] = false
                        }
                    }
                }
            }
            return judged
        }

        private fun luma(argb: Int): Int =
            (((argb shr 16) and 0xFF) * 77 + ((argb shr 8) and 0xFF) * 151 + (argb and 0xFF) * 28) shr 8
    }

    /**
     * How much of the actual frame's outline has no counterpart within one pixel of the golden's.
     *
     * [InteriorMask] deliberately ignores edges, which leaves one thing it cannot see: an object
     * that **moved**. Sliding a band of skyline three pixels sideways maps flat colour onto the same
     * flat colour almost everywhere, so only the silhouettes change -- and those are exactly the
     * pixels the mask drops: the interior check forgives that regression, so it is not the whole check.
     *
     * This is the other half, and it is structural rather than photometric: take both edge maps,
     * allow every golden edge one pixel of slack, and count the actual's edge pixels that still land
     * outside. A driver placing the same edge half a pixel differently is absorbed by the slack; an
     * object three pixels from where it belongs is not, because its whole outline is.
     *
     * [MAX_DISPLACED_FRACTION] sits at 3%: over twice the largest gap measured between two GPU
     * vendors ([CHARACTERISED_MAX_DISPLACED_FRACTION]), and under what a band slid three pixels or an
     * erased object displaces. `GlGoldenMetricTest` holds both ends of that.
     *
     * The committed `gl-*.png` are drawn on the BV6600's PowerVR Rogue GE8320, one frame per scene
     * through the shipped `GlSceneTarget`. The committed file and the freshly rendered frame come
     * from the same driver on the same machine, so this reads 0.00% on them, and
     * [GlDriverGapGuardTest] keeps reading it until a second GPU vendor exists. An outline displaced
     * past the gate means the scene moved, not the driver: the references are drawn again from the
     * new scene, and the tolerance is never raised to absorb it.
     */
    internal object EdgeDisplacement {

        /** Above this fraction, outlines are somewhere else rather than merely drawn differently. */
        const val MAX_DISPLACED_FRACTION = 0.03

        /**
         * The band the **characterised driver gap** must stay inside, kept as a guard so that a gap
         * between two GPUs is watched rather than rediscovered.
         *
         * The references and the frame are drawn by the same driver, the BV6600's PowerVR Rogue
         * GE8320, so this bounds a measurement that reads **0.00%** until a second driver exists;
         * the day one does, it is the number that says whether the new driver's edges stay inside
         * the band two GPUs were measured in.
         *
         * **Where 2% comes from.** Between a OnePlus 6T's Adreno 630 and references drawn by the
         * emulator's software GL the gap measured 0.92 to 1.4% of the outline (2026-09). Two percent
         * sits above that, far enough that ordinary variation between driver revisions does not
         * reach it, and a third below [MAX_DISPLACED_FRACTION]'s 3%, so it fires while the gate
         * still has headroom and the gap's growth can be looked at before a golden run fails
         * outright. It is a smoke alarm, not a limit: nothing is permitted or forbidden by it that
         * [MAX_DISPLACED_FRACTION] does not already decide.
         */
        const val CHARACTERISED_MAX_DISPLACED_FRACTION = 0.02

        fun of(expected: IntArray, actual: IntArray, width: Int, height: Int): Double {
            val expectedEdges = edgesOf(expected, width, height)
            val actualEdges = edgesOf(actual, width, height)
            var displaced = 0
            var total = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (!actualEdges[y * width + x]) continue
                    total++
                    if (!nearAnEdge(expectedEdges, width, height, x, y)) displaced++
                }
            }
            return if (total == 0) 0.0 else displaced / total.toDouble()
        }

        private fun nearAnEdge(edges: BooleanArray, width: Int, height: Int, x: Int, y: Int): Boolean {
            for (dy in -1..1) {
                val yy = y + dy
                if (yy !in 0 until height) continue
                for (dx in -1..1) {
                    val xx = x + dx
                    if (xx in 0 until width && edges[yy * width + xx]) return true
                }
            }
            return false
        }

        private fun edgesOf(pixels: IntArray, width: Int, height: Int): BooleanArray {
            val edges = BooleanArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val i = y * width + x
                    val here = luma(pixels[i])
                    var step = 0
                    if (x > 0) step = maxOf(step, abs(here - luma(pixels[i - 1])))
                    if (y > 0) step = maxOf(step, abs(here - luma(pixels[i - width])))
                    if (step > InteriorMask.EDGE_STEP) edges[i] = true
                }
            }
            return edges
        }

        private fun luma(argb: Int): Int =
            (((argb shr 16) and 0xFF) * 77 + ((argb shr 8) and 0xFF) * 151 + (argb and 0xFF) * 28) shr 8
    }

    /** [EdgeDisplacement.of] over two bitmaps. */
    internal fun edgeDisplacement(expected: Bitmap, actual: Bitmap): Double {
        val size = WIDTH * HEIGHT
        val expectedPixels = IntArray(size)
        val actualPixels = IntArray(size)
        expected.getPixels(expectedPixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        actual.getPixels(actualPixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        return EdgeDisplacement.of(expectedPixels, actualPixels, WIDTH, HEIGHT)
    }

    /**
     * Pixels differing by [channel] or more, counted only where [InteriorMask] allows.
     *
     * Returns the count and how much of the frame was masked, because the caller has to fail on the
     * second as well as on the first.
     */
    internal fun countAtLeastInInteriors(
        expected: Bitmap,
        actual: Bitmap,
        channel: Int,
    ): Pair<Int, Double> {
        val size = WIDTH * HEIGHT
        val expectedPixels = IntArray(size)
        val actualPixels = IntArray(size)
        expected.getPixels(expectedPixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        actual.getPixels(actualPixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        val judged = InteriorMask.of(expectedPixels, actualPixels, WIDTH, HEIGHT)
        var count = 0
        var masked = 0
        for (i in 0 until size) {
            if (!judged[i]) {
                masked++
                continue
            }
            if (channelDelta(expectedPixels[i], actualPixels[i]) >= channel) count++
        }
        return count to masked / size.toDouble()
    }

    private fun countAtLeast(expected: Bitmap, actual: Bitmap, channel: Int): Int {
        val row = IntArray(WIDTH)
        val glRow = IntArray(WIDTH)
        var count = 0
        for (y in 0 until HEIGHT) {
            expected.getPixels(row, 0, WIDTH, 0, y, WIDTH, 1)
            actual.getPixels(glRow, 0, WIDTH, 0, y, WIDTH, 1)
            for (x in 0 until WIDTH) if (channelDelta(row[x], glRow[x]) >= channel) count++
        }
        return count
    }

    /** [countAtLeast] restricted to one rectangle. */
    private fun countAtLeastIn(expected: Bitmap, actual: Bitmap, channel: Int, focus: GoldenFocus): Int {
        val width = focus.right - focus.left
        val row = IntArray(width)
        val glRow = IntArray(width)
        var count = 0
        for (y in focus.top until focus.bottom) {
            expected.getPixels(row, 0, width, focus.left, y, width, 1)
            actual.getPixels(glRow, 0, width, focus.left, y, width, 1)
            for (x in 0 until width) if (channelDelta(row[x], glRow[x]) >= channel) count++
        }
        return count
    }

    /** The largest per-channel difference, alpha excluded: both frames are opaque by construction. */
    private fun channelDelta(a: Int, b: Int): Int = maxOf(
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)),
        abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)),
        abs((a and 0xFF) - (b and 0xFF)),
    )

    private fun readAsset(name: String): Bitmap? = try {
        instrumentation.context.assets.open("golden/$name").use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) {
        null
    }

    private fun reject(scene: GoldenScene, result: Result, expected: Bitmap, channel: Int) {
        write(result.bitmap, File(outputDir(), "gl-${scene.name}-actual.png"))
        write(diffImage(expected, result.bitmap, channel), File(outputDir(), "gl-${scene.name}-diff.png"))
    }

    /** White where the two frames disagree beyond [channel], black where they agree. */
    private fun diffImage(expected: Bitmap, actual: Bitmap, channel: Int): Bitmap {
        val diff = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH) {
                val over = channelDelta(expected.getPixel(x, y), actual.getPixel(x, y)) >= channel
                diff.setPixel(x, y, if (over) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
            }
        }
        return diff
    }

    private fun write(bitmap: Bitmap, file: File) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
