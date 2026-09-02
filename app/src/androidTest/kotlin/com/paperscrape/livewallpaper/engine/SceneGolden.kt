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

    /**
     * The same rule applied to a [GoldenFocus], with a looser fraction and a far smaller area.
     *
     * Looser per pixel because a small patch is mostly edges -- a sail is a diagonal, and diagonals
     * are where anti-aliasing differs between Skia builds -- and far stricter in absolute terms:
     * 2% of a 900-pixel patch is 18 pixels, where 0.2% of the whole frame is 576. That difference
     * is the point. A dolphin covers about 160 pixels at this frame size, so a golden about which
     * side of a sail a dolphin is on cannot be measured against the whole-frame budget: the sprite
     * could move anywhere, or vanish, and the frame would still pass.
     */
    const val MAX_FOCUS_DIFFERING_FRACTION = 0.02

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
    /**
     * Runs [GoldenScene.warmUpFrames] frames into [target] and returns the scene clock they left.
     *
     * The frames are drawn rather than simulated, because "advance the renderer without drawing"
     * is not a state a running wallpaper is ever in and a golden built from one would be pinning
     * something the app never does. They land on the same bitmap and are painted over by the frame
     * that follows, the scene repainting its whole background every frame.
     *
     * Shared by both golden harnesses so a scene warms up identically whichever backend draws it —
     * the GL suite's cross-check against the Canvas golden means nothing if the two ran the scene
     * for different lengths of time.
     */
    fun warmUp(renderer: PaperRenderer, target: SceneCanvas, scene: GoldenScene): SceneTime {
        var clock = SceneTime(scene.sceneSeconds)
        repeat(scene.warmUpFrames) {
            clock += scene.warmUpDeltaSeconds
            renderer.draw(target, scene.dayPhase, clock, scene.warmUpDeltaSeconds)
        }
        return clock
    }

    fun render(scene: GoldenScene): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val target = CanvasSceneTarget()
        target.bind(Canvas(bitmap))
        val renderer = PaperRenderer(WIDTH, HEIGHT, instrumentation.targetContext)
        scene.configure(renderer)
        val clock = warmUp(renderer, target, scene)
        renderer.draw(target, scene.dayPhase, clock, 0f)
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

        val differing = countDiffering(expected, actual, 0, 0, WIDTH, HEIGHT)
        val fraction = differing.toDouble() / (WIDTH * HEIGHT)
        if (fraction > MAX_DIFFERING_FRACTION) {
            reject(scene, actual, expected)
            fail(
                "Golden '${scene.name}' changed: $differing pixels differ by more than " +
                    "$CHANNEL_TOLERANCE per channel (${"%.3f".format(fraction * 100)}%, limit " +
                    "${"%.3f".format(MAX_DIFFERING_FRACTION * 100)}%). The rendered frame and a " +
                    "diff are in ${outputDir()}. If the change was intended, say why before " +
                    "regenerating the golden.",
            )
            return
        }

        // The second, tighter pass, for scenes that are about one small part of the picture. See
        // [MAX_FOCUS_DIFFERING_FRACTION].
        for (focus in scene.focus) {
            val inFocus = countDiffering(expected, actual, focus.left, focus.top, focus.right, focus.bottom)
            val focusFraction = inFocus.toDouble() / focus.area
            if (focusFraction > MAX_FOCUS_DIFFERING_FRACTION) {
                reject(scene, actual, expected)
                fail(
                    "Golden '${scene.name}' changed inside '${focus.label}' " +
                        "(${focus.left},${focus.top})-(${focus.right},${focus.bottom}): $inFocus of " +
                        "${focus.area} pixels differ (${"%.2f".format(focusFraction * 100)}%, limit " +
                        "${"%.2f".format(MAX_FOCUS_DIFFERING_FRACTION * 100)}%). This is the part of " +
                        "the frame the golden is about; the whole frame passed. The rendered frame " +
                        "and a diff are in ${outputDir()}.",
                )
                return
            }
        }
    }

    private fun reject(scene: GoldenScene, actual: Bitmap, expected: Bitmap) {
        write(actual, File(outputDir(), "${scene.name}-actual.png"))
        write(diffImage(expected, actual), File(outputDir(), "${scene.name}-diff.png"))
    }

    /**
     * The share of the whole frame on which two renders disagree, by the same rule a golden uses.
     *
     * Exposed so `TrafficGoldenTest` can show that a plausible traffic regression moves more than
     * [MAX_DIFFERING_FRACTION] — i.e. that the golden would actually catch it — rather than
     * asserting that it would.
     */
    /**
     * Whether the only thing that happened at one pixel is a ground shadow falling on it.
     *
     * The vehicles are the one part of the scene that draws something translucent:
     * `drawGroundShadow` lays a `0x2E000000` oval on the road, which multiplies whatever is behind
     * it by (255-46)/255 and leaves the hue alone. Every other thing a vehicle draws is opaque
     * paint, whose colour has nothing to do with what was underneath.
     *
     * Measurements of the form "how tall is this vehicle" sweep a column for pixels that differ
     * from an empty frame, and they have to skip this. Until the shadow was put on the road it did
     * not matter: it was drawn 37 units up, inside the body's own outline, so a sweep of the whole
     * column returned the vehicle's height anyway. On the road the oval straddles the wheel contact
     * and reaches four units below it, and the same sweep returns the vehicle plus half a shadow --
     * which is what `VehicleOccupantScaleTest` and `VehicleScalePixelTest` started measuring.
     *
     * Recognised by its shape rather than by its position: a uniform darkening of what was there,
     * by no more than the shadow's own alpha. Partial coverage along the oval's edge darkens less
     * and is caught by the same rule. Pedestrians draw no shadow, so a people-only frame has
     * nothing this can match.
     */
    fun isGroundShadowOnly(before: Int, after: Int): Boolean {
        var minFactor = 2f
        var maxFactor = -1f
        var moved = false
        for (shift in intArrayOf(16, 8, 0)) {
            val a = (before shr shift) and 0xFF
            val b = (after shr shift) and 0xFF
            if (b > a) return false
            if (a != b) moved = true
            if (a == 0) continue
            val factor = b.toFloat() / a
            if (factor < SHADOW_FACTOR - 0.02f) return false
            if (factor < minFactor) minFactor = factor
            if (factor > maxFactor) maxFactor = factor
        }
        // One alpha over three channels, so the three factors are the same factor.
        return moved && (maxFactor < 0f || maxFactor - minFactor <= 0.05f)
    }

    /** What `drawGroundShadow`'s 0x2E000000 multiplies the background by. */
    private const val SHADOW_FACTOR = (255f - 0x2E) / 255f

    fun differingFraction(expected: Bitmap, actual: Bitmap): Double =
        countDiffering(expected, actual, 0, 0, WIDTH, HEIGHT) / (WIDTH * HEIGHT).toDouble()

    private fun countDiffering(expected: Bitmap, actual: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Int {
        val width = right - left
        val row = IntArray(width)
        val otherRow = IntArray(width)
        var differing = 0
        for (y in top until bottom) {
            expected.getPixels(row, 0, width, left, y, width, 1)
            actual.getPixels(otherRow, 0, width, left, y, width, 1)
            for (x in 0 until width) {
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
        emitToLogcat(bitmap, file.name)
    }

    /**
     * Also emits a regenerated frame to logcat, base64 in chunks (**v3.8**).
     *
     * **Because the file on the device is not retrievable.** The frames are written to the app's
     * external files directory, which since Android 11 the `shell` user cannot read — and AGP
     * uninstalls the app after `connectedAndroidTest` anyway, taking the directory with it. A
     * regenerated golden that nobody can fetch is the same failure the v3.4 diagnostics artefact
     * had, and `AI_PROJECT_RULES.md` 10.13 is about exactly that: evidence that cannot be
     * retrieved is not evidence.
     *
     * Only ever runs under `-e updateGoldens true`, so a normal run pays nothing.
     */
    private fun emitToLogcat(bitmap: Bitmap, name: String) {
        val bytes = java.io.ByteArrayOutputStream()
            .also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            .toByteArray()
        val encoded = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val chunk = 3000
        val chunks = (encoded.length + chunk - 1) / chunk
        android.util.Log.i(GOLDEN_TAG, "BEGIN $name ${bytes.size} $chunks")
        for (i in 0 until chunks) {
            val part = encoded.substring(i * chunk, minOf((i + 1) * chunk, encoded.length))
            android.util.Log.i(GOLDEN_TAG, "$name $i $part")
        }
        android.util.Log.i(GOLDEN_TAG, "END $name")
    }

    private const val GOLDEN_TAG = "GOLDENPNG"
}
