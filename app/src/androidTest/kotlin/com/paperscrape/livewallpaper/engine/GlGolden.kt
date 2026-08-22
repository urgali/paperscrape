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
 * All 14 Canvas goldens go through [CanvasSceneTarget], which is the right choice and is not in
 * question: a golden produced by drawing code the app does not ship proves nothing about the code
 * it does. The consequence, recorded as P1-4 in the v3.0 assessment, is that [GlSceneTarget] —
 * ~690 lines of hand tessellation, batching, atlas UVs and premultiplied blending, and the backend
 * that actually draws the wallpaper on every device where EGL works — had nothing pinning its
 * output at all.
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
     * The two comparisons a GL frame has to pass, and where every number in them comes from.
     *
     * **Nothing here is guessed.** Each threshold was chosen after rendering all three scenes on an
     * Android 17 emulator under two very different GL drivers -- the host-GPU translator and
     * `swiftshader_indirect`, which is what CI uses -- and against two deliberately broken versions
     * of the shipped backend. Percentages are the share of the 360x800 frame whose maximum channel
     * delta reaches the stated level:
     *
     * | comparison | >=8 | >=16 | >=32 | >=64 |
     * |---|---|---|---|---|
     * | same code, host GPU vs swiftshader | 0.88% | 0.12% | 0.06% | 0.03% |
     * | GL vs the Canvas golden, healthy | — | 1.85% | 1.01% | 0.21% |
     * | GL vs the Canvas golden, blend broken | — | 3.14% | 4.13% | 0.55% |
     * | GL golden vs GL, blend broken | 5.03% | 3.75% | 3.17% | — |
     *
     * The first row is the floor: two correct implementations of the same backend differ by that
     * much simply because they are different rasterisers. Nothing below it is detectable by any
     * pixel test that is allowed to run on more than one machine.
     *
     * ### [GlTarget] — "GL still draws what GL drew"
     *
     * Against a committed `gl-<name>.png`. This is the strict one, and it can afford to be: the
     * driver-to-driver floor at `>=16` is 0.12%, so a limit of 0.50% carries four times that as
     * headroom and still catches the broken blend on all three scenes, by between two and seven
     * times.
     *
     * ### [CanvasCross] — "GL still draws what Canvas draws"
     *
     * Against the Canvas golden the rest of the suite uses. Far looser, necessarily: the two
     * backends approximate the same picture through an analytic rasteriser on one side and hand
     * tessellation on the other, and the healthy difference is already 1% at `>=32`. What it buys
     * is the assertion the GL golden cannot make — that the backend which ships has not drifted
     * away from the one every other golden pins — and it survives a driver change, which the GL
     * golden may not. Two gates rather than one because a structural regression moves a region of
     * pixels a long way while a subtle one moves a great many pixels a moderate amount.
     *
     * ### What neither can see
     *
     * A change smaller than the driver-to-driver floor. Reducing `drawRadialGlow`'s fan to a single
     * triangle -- destroying the shape of the sun's glow completely -- moves no pixel by more than
     * 15/255, because the glow is drawn at alpha 90 over a bright sky; at `>=8` it reaches 0.47%
     * where two correct drivers already differ by 0.88%. That is a property of the effect, not a
     * gap to be closed by lowering a threshold: a limit under the driver floor fails on the next
     * emulator instead of on the next bug.
     */
    object Tolerance {
        /** Against the committed GL golden: same backend, possibly a different GL driver. */
        object GlTarget {
            const val CHANNEL = 16
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

            gl.beginFrame()
            renderer.draw(gl, scene.dayPhase, SceneTime(scene.sceneSeconds), 0f)
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
        val result = render(scene)
        if (updating()) {
            write(result.bitmap, File(outputDir(), "gl-${scene.name}.png"))
            return
        }
        assertMatchesGlGolden(scene, result)
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
        val differing = countAtLeast(expected, result.bitmap, Tolerance.GlTarget.CHANNEL)
        val fraction = differing / (WIDTH * HEIGHT).toDouble()
        if (fraction > Tolerance.GlTarget.MAX_FRACTION) {
            reject(scene, result, expected, Tolerance.GlTarget.CHANNEL)
            throw AssertionError(
                "The GL backend's own output for '${scene.name}' changed: " +
                    "${"%.3f".format(fraction * 100)}% of pixels differ by " +
                    ">=${Tolerance.GlTarget.CHANNEL} per channel, limit " +
                    "${"%.3f".format(Tolerance.GlTarget.MAX_FRACTION * 100)}%. ${describe(result)}. " +
                    "Two correct GL drivers differ by about 0.12% at this level, so this is very " +
                    "unlikely to be a driver difference -- look at the frame and the diff in " +
                    "${outputDir()} before regenerating anything.",
            )
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
