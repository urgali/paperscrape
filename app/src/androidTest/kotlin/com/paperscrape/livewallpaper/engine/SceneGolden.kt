package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.fail
import java.io.File
import kotlin.math.abs

/**
 * Renders one frame of a named scene and compares it against a committed PNG.
 *
 * **Why this runs on a device and not on the JVM.** [SceneCanvas] passes `android.graphics.Paint`
 * through rather than decomposing it, which is the right call for the renderers -- but it means a
 * unit test would be reading colours off the mockable `android.jar`'s stub Paint, which throws.
 * The alternative was a second, JVM-only implementation of the drawing surface, which is the
 * parallel renderer this project explicitly does not want: a golden produced by different drawing
 * code proves nothing about the drawing code that ships. So the goldens are produced by
 * [CanvasSceneTarget] -- the same backend that draws the settings preview and the EGL fallback --
 * writing into a real `Bitmap`.
 *
 * **What makes a frame reproducible.** [PaperRenderer] is deterministic given a fixed size, theme,
 * customisation, day phase and scene time: every candidate system is seeded from the theme id (see
 * `PaperRenderer.seedFor`), and the two `Random` uses are a seeded one and the lightning timer,
 * which only advances with `deltaSeconds`. Every scene here is drawn as a single frame with
 * `deltaSeconds = 0`, so the timer never fires and no frame depends on when it was taken.
 *
 * The lightning *bolt* is therefore deliberately outside these goldens -- it is a timed event, and
 * pinning it would mean pinning a random number generator rather than a picture. What the storm
 * golden does pin is everything `StormAtmosphere` drives: the darkened sky, the darkened cloud
 * band and the attenuated sun.
 *
 * **Updating a golden.** Never blindly. Run with `-e updateGoldens true`, which writes the new PNGs
 * to the device's external files dir instead of comparing, then look at what changed and say why in
 * the commit. A golden that changed without a reason in the diff is a regression that has just been
 * blessed.
 */
object SceneGolden {

    /**
     * A phone-shaped frame, fixed so a golden does not depend on the emulator it was taken on.
     *
     * Small on purpose: a third of a real screen is still hundreds of thousands of pixels, catches
     * every structural regression, and keeps the committed PNGs a few kilobytes each rather than a
     * megabyte.
     */
    const val WIDTH = 360
    const val HEIGHT = 800

    /**
     * How far two frames may differ and still be the same picture.
     *
     * Not zero, and the reason is anti-aliasing: the same scene rendered on two Android versions,
     * or on hardware with a different Skia build, differs by a greyscale level or two along glyph
     * and sprite edges. A per-pixel channel tolerance of [CHANNEL_TOLERANCE] absorbs that; the
     * fraction of pixels allowed to exceed even that is [MAX_DIFFERING_FRACTION], which is small
     * enough that a sprite moving by one pixel fails.
     */
    const val CHANNEL_TOLERANCE = 8
    const val MAX_DIFFERING_FRACTION = 0.002

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun updating(): Boolean =
        InstrumentationRegistry.getArguments().getString("updateGoldens")?.toBoolean() == true

    /** Where a rejected or regenerated frame is written, for a human to look at. */
    private fun outputDir(): File =
        File(instrumentation.targetContext.getExternalFilesDir(null), "golden-output")
            .apply { mkdirs() }

    /**
     * Draws [scene] into a bitmap through [CanvasSceneTarget], the real Canvas backend.
     *
     * The renderer is built fresh for every scene so nothing -- scroll accumulation, lightning
     * timer, cached paths -- carries from one golden into the next.
     */
    fun render(scene: GoldenScene): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val target = CanvasSceneTarget()
        target.bind(Canvas(bitmap))
        val renderer = PaperRenderer(WIDTH, HEIGHT, instrumentation.targetContext)
        scene.configure(renderer)
        renderer.draw(target, scene.dayPhase, SceneTime(scene.sceneSeconds), 0f)
        target.unbind()
        return bitmap
    }

    /** Renders [scene] and asserts it still looks like its committed golden. */
    fun assertMatches(scene: GoldenScene) {
        val actual = render(scene)
        if (updating()) {
            write(actual, File(outputDir(), "${scene.name}.png"))
            return
        }

        val expected = readGolden(scene.name)
        if (expected == null) {
            write(actual, File(outputDir(), "${scene.name}.png"))
            fail(
                "No golden committed for '${scene.name}'. A frame was written to " +
                    "${outputDir()}/${scene.name}.png -- look at it, and if it is right, commit it " +
                    "to app/src/androidTest/assets/golden/.",
            )
            return
        }

        if (expected.width != actual.width || expected.height != actual.height) {
            write(actual, File(outputDir(), "${scene.name}-actual.png"))
            fail(
                "Golden '${scene.name}' is ${expected.width}x${expected.height} but the scene " +
                    "rendered ${actual.width}x${actual.height}.",
            )
            return
        }

        val differing = countDiffering(expected, actual)
        val fraction = differing.toDouble() / (WIDTH * HEIGHT)
        if (fraction > MAX_DIFFERING_FRACTION) {
            write(actual, File(outputDir(), "${scene.name}-actual.png"))
            write(diffImage(expected, actual), File(outputDir(), "${scene.name}-diff.png"))
            fail(
                "Golden '${scene.name}' changed: $differing pixels differ by more than " +
                    "$CHANNEL_TOLERANCE per channel (${"%.3f".format(fraction * 100)}%, limit " +
                    "${"%.3f".format(MAX_DIFFERING_FRACTION * 100)}%). The rendered frame and a " +
                    "diff are in ${outputDir()}. If the change was intended, say why before " +
                    "regenerating the golden.",
            )
        }
    }

    private fun countDiffering(expected: Bitmap, actual: Bitmap): Int {
        val row = IntArray(WIDTH)
        val otherRow = IntArray(WIDTH)
        var differing = 0
        for (y in 0 until HEIGHT) {
            expected.getPixels(row, 0, WIDTH, 0, y, WIDTH, 1)
            actual.getPixels(otherRow, 0, WIDTH, 0, y, WIDTH, 1)
            for (x in 0 until WIDTH) {
                if (!closeEnough(row[x], otherRow[x])) differing++
            }
        }
        return differing
    }

    private fun closeEnough(a: Int, b: Int): Boolean {
        if (a == b) return true
        return abs(((a shr 24) and 0xFF) - ((b shr 24) and 0xFF)) <= CHANNEL_TOLERANCE &&
            abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) <= CHANNEL_TOLERANCE &&
            abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) <= CHANNEL_TOLERANCE &&
            abs((a and 0xFF) - (b and 0xFF)) <= CHANNEL_TOLERANCE
    }

    /** White where the two frames disagree, black where they do not. */
    private fun diffImage(expected: Bitmap, actual: Bitmap): Bitmap {
        val diff = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH) {
                val same = closeEnough(expected.getPixel(x, y), actual.getPixel(x, y))
                diff.setPixel(x, y, if (same) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        return diff
    }

    private fun readGolden(name: String): Bitmap? = try {
        instrumentation.context.assets.open("golden/$name.png").use {
            android.graphics.BitmapFactory.decodeStream(it)
        }
    } catch (_: Exception) {
        null
    }

    private fun write(bitmap: Bitmap, file: File) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
