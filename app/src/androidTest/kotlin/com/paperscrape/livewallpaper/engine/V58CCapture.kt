package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **Capture only -- writes the frames the v5.8C round is judged on, and asserts nothing.**
 *
 * Two sets, both rendered by the shipped [PaperRenderer] on this phone, so a "before" and an
 * "after" built from two trees are the same scene to the pixel and differ only by the change:
 *
 * - [scenesAtPhoneSize]: the twelve themes at the BV6600's own 720x1440, midday and night, on the
 *   `Canvas` backend with the traffic warmed up onto the road. What the density repair (item 4) and
 *   the visible defects of item 3 are looked at on, side by side.
 * - [hillShadowFan]: Beach and Sunset with the hill tile scrolled so the stretch of ridge where the
 *   shadow's GPU fan can leave the hill is on screen, drawn by **both** backends -- GL on this
 *   phone's PowerVR at the phone's size and at a 1080x2424 phone's, `Canvas` beside it as the exact
 *   fill. Item 5: whatever GL paints that `Canvas` does not, above the ridge, is the fan.
 *
 * Kept in the tree for the reason `V51PalmCapture` is: evidence whose recipe was deleted is a
 * picture taken on trust. Run with `am instrument -e class ...V58CCapture` and pull
 * `files/golden-output/cap-v58c-*` with `run-as`.
 */
@RunWith(AndroidJUnit4::class)
class V58CCapture {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun write(name: String, bitmap: Bitmap) {
        val dir = File(context.filesDir, "golden-output")
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun configure(renderer: PaperRenderer, themeId: String, swipe: Float) {
        renderer.theme = ThemeCatalog.byId(themeId)
        renderer.sceneCustomization = defaultCustomizationFor(themeId)
        renderer.liveWeatherOverride = null
        renderer.homeScreenOffset = swipe
        renderer.swipeScrollEnabled = true
        renderer.scrollSpeed = 0f
        renderer.parallaxStrength = 1f
        renderer.lightningStrikesEnabled = false
    }

    /** Warm-up frames at the wallpaper's cadence, so cars and people are out on the street. */
    private fun drawWarm(renderer: PaperRenderer, target: SceneCanvas, phase: SunPositionCalculator.DayPhase, frames: Int, begin: () -> Unit = {}, end: () -> Unit = {}) {
        var clock = SceneTime(120.0)
        val dt = 1f / 30f
        repeat(frames) {
            clock += dt
            begin(); renderer.draw(target, phase, clock, dt); end()
        }
        begin(); renderer.draw(target, phase, clock, 0f); end()
    }

    private fun renderCanvas(themeId: String, width: Int, height: Int, phase: SunPositionCalculator.DayPhase, swipe: Float, frames: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val target = CanvasSceneTarget()
        target.bind(Canvas(bitmap))
        val renderer = PaperRenderer(width, height, context)
        configure(renderer, themeId, swipe)
        drawWarm(renderer, target, phase, frames)
        target.unbind()
        return bitmap
    }

    private fun renderGl(themeId: String, width: Int, height: Int, phase: SunPositionCalculator.DayPhase, swipe: Float, frames: Int): Bitmap {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1))
        val config = chooseConfig(display, true) ?: chooseConfig(display, false) ?: error("no EGL config")
        var context: EGLContext = EGL14.EGL_NO_CONTEXT
        var surface: EGLSurface = EGL14.EGL_NO_SURFACE
        var gl: GlSceneTarget? = null
        try {
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            surface = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE), 0)
            check(EGL14.eglMakeCurrent(display, surface, surface, context))
            val target = GlSceneTarget().also { gl = it }
            check(target.onContextCreated())
            target.onSurfaceSizeChanged(width, height)
            val renderer = PaperRenderer(width, height, this.context)
            configure(renderer, themeId, swipe)
            drawWarm(renderer, target, phase, frames, begin = { target.beginFrame() }, end = { target.endFrame() })
            GLES20.glFinish()
            return readFramebuffer(width, height)
        } finally {
            if (gl != null && surface != EGL14.EGL_NO_SURFACE) gl!!.release()
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }

    @Test
    fun scenesAtPhoneSize() {
        for (themeId in ThemeCatalog.ALL.map { it.id }) {
            for ((label, phase) in listOf("day" to GoldenScene.day(12f), "night" to GoldenScene.night())) {
                write("cap-v58c-scene-$themeId-$label", renderCanvas(themeId, 720, 1440, phase, 0f, WARM_FRAMES))
            }
        }
    }

    /**
     * The other half of the scene: the ground is two screens wide, and at rest only the first is
     * on screen. Swiped so the hill tile (and the objects standing on it) is one screen along --
     * where most of what item 4 removes stands.
     */
    @Test
    fun secondHalfOfScene() {
        val swipe = 1f / PaperRenderer.HILL_PARALLAX
        for (themeId in listOf("autumn", "winter", "desert", "christmas")) {
            write("cap-v58c-half2-$themeId-day", renderCanvas(themeId, 720, 1440, GoldenScene.day(12f), swipe, WARM_FRAMES))
        }
    }

    @Test
    fun hillShadowFan() {
        for ((width, height) in listOf(720 to 1440, 1080 to 2424)) {
            for ((themeId, localX) in FAN_STRETCH) {
                // The hill tile's local x runs over [-0.5, 1.5] screen widths; the swipe that puts
                // local x = localX * width at the middle of the screen, at the hills' parallax.
                val swipe = ((localX - 0.5f) * width) / (width * PaperRenderer.HILL_PARALLAX)
                val phase = GoldenScene.day(12f)
                val name = "cap-v58c-hill-$themeId-${width}x$height"
                write("$name-gl", renderGl(themeId, width, height, phase, swipe, 0))
                write("$name-canvas", renderCanvas(themeId, width, height, phase, swipe, 0))
            }
        }
    }

    private fun chooseConfig(display: EGLDisplay, multisample: Boolean): EGLConfig? {
        val base = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 0, EGL14.EGL_STENCIL_SIZE, 0,
        )
        val attribs = if (multisample) base + intArrayOf(EGL14.EGL_SAMPLE_BUFFERS, 1, EGL14.EGL_SAMPLES, 4, EGL14.EGL_NONE) else base + intArrayOf(EGL14.EGL_NONE)
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        return if (EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) && count[0] > 0) configs[0] else null
    }

    private fun readFramebuffer(width: Int, height: Int): Bitmap {
        val buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
        buffer.rewind()
        val pixels = IntArray(width * height)
        val row = ByteArray(width * 4)
        for (y in 0 until height) {
            buffer.get(row)
            val dest = (height - 1 - y) * width
            for (x in 0 until width) {
                val i = x * 4
                pixels[dest + x] = ((row[i + 3].toInt() and 0xFF) shl 24) or ((row[i].toInt() and 0xFF) shl 16) or
                    ((row[i + 1].toInt() and 0xFF) shl 8) or (row[i + 2].toInt() and 0xFF)
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private companion object {
        /** Five seconds of traffic at 30 fps: enough for the first cars to be on the road. */
        const val WARM_FRAMES = 150

        /**
         * Where on the hill tile the fan leaves the ridge, in screen widths of local x, from the
         * host replay (`registri/strumenti/ventaglio_colline.py`): Beach 1401..1563 of 1080 and
         * Sunset 1437..1564 of 1080, i.e. about 1.37 and 1.39 screen widths.
         */
        val FAN_STRETCH = listOf("beach" to 1.37f, "sunset" to 1.39f)
    }
}
