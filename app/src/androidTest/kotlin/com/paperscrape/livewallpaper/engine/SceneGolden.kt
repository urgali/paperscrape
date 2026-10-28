package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
 * The alternative would be a second, JVM-only implementation of the drawing surface, which is the
 * parallel renderer this project explicitly does not want: a golden produced by different drawing
 * code proves nothing about the drawing code that ships. So the goldens are produced by
 * [CanvasSceneTarget] -- the same backend that draws the settings preview and the EGL fallback --
 * writing into a real `Bitmap`.
 *
 * **What makes a frame reproducible.** [PaperRenderer] is deterministic given a fixed size, theme,
 * customisation, day phase and scene time: every candidate system is seeded from the theme id (see
 * `PaperRenderer.seedFor`), and of its `Random` uses one is seeded, while the lightning timer and the
 * sky's visitors (`SceneRandom.source`, `Random.Default` unless a test seeds it) move only with
 * `deltaSeconds`.
 *
 * A warmed-up storm advances that timer once per frame -- 320 times for `wave-storm` -- so,
 * unpinned, it compares a frame with a bolt in it in 1 render of 32 against a committed file that
 * has none. The rule that a warmed-up scene must not be an unpinned storm is a check rather than a
 * sentence — [GoldenScene.requireDeterministicLightning], run below — and the one scene it applies
 * to satisfies it with [GoldenScene.pinLightning].
 *
 * The lightning *bolt* is therefore deliberately outside these goldens -- it is a timed
 * event, and pinning it would mean pinning a random number generator rather than a picture. What
 * the storm golden does pin is everything `StormAtmosphere` drives: the darkened sky, the darkened
 * cloud band and the attenuated sun.
 *
 * **Umbrellas.** In the rain every walker who has a carry pose is carrying, but an umbrella is
 * state, not a predicate: a golden rendered at scene time zero shows none, so only the rainy scenes
 * with a warm-up, `umbrella-rain` and `wave-storm` (320 frames x 0.25 s), show them. `rain`,
 * `rain-worst-sky` and `thunderstorm` all rain and all render at zero.
 *
 * **The reference device reproduces the committed goldens exactly**, so a frame that moved, moved
 * because of the code. The three `gl-*.png` references are pulled from the device with `adb pull`
 * (Android 10 lets the shell reach the app's external files directory), never transcribed.
 *
 * **Updating a golden.** Never blindly. Run with `-e updateGoldens true`, which writes the new PNGs
 * to the device's external files dir instead of comparing, then look at what changed and say why in
 * the commit. A golden that changed without a reason in the diff is a regression that has just been
 * blessed.
 *
 * **The regeneration protocol.**
 *
 * A regeneration pass is: attribute the change region by region *before* regenerating; regenerate;
 * move no tolerance; leave the GL references alone unless the Canvas frames of their own scenes
 * changed; and count **assertions**, never files in the golden directory.
 *
 * The double regeneration -- every changed scene captured twice, in two separate instrumentation
 * runs, compared byte for byte -- exists to show that the noise floor is exactly zero, so that a
 * later difference is the scene changing and never the phone. **It is run only on the scenes whose
 * frames actually changed.** The argument for it: what the check tests is the renderer plus this
 * phone plus this Android build, none of which moves between releases. The argument it has to
 * answer: a release can introduce non-determinism of its own, and moving the check to "device
 * changed" would remove the one thing that would catch it. Restricting it to changed scenes keeps that, because **a newly
 * non-deterministic scene is a changed scene by definition** -- it cannot match its committed
 * golden -- while a scene whose bytes did not move has already demonstrated its own determinism by
 * matching. An unchanged scene is regenerated zero times, not once.
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
     * How far two frames may differ and still be the same picture: **zero pixels.**
     *
     * Anti-aliasing is absorbed **per pixel**, by [CHANNEL_TOLERANCE]: the same scene rendered on
     * two Android versions, or on hardware with a different Skia build, differs by a greyscale
     * level or two along glyph and sprite edges, and a channel tolerance of 8 covers that. This
     * constant is a *second* allowance stacked on top of it -- how many pixels may exceed even
     * that.
     *
     * ### Why zero, and why zero is not a tuning
     *
     * A gate sits between the measured noise floor and the weakest regression that must fail:
     *
     *  - **the floor is 0.** A Canvas golden that matches differs by exactly zero pixels on the
     *    reference device, in two independent sessions; every non-zero one had a cause;
     *  - **the weakest regression that must fail is 14 pixels.** A redrawn `bird_body.png` with
     *    the goldens left as they were costs `lake-dolphin-leap` 384 pixels, `lake-boats` 62 and
     *    `traffic-day` 14, attributed by mutation: the same build with the previous `bird_body.png`
     *    swapped back in renders all three at exactly **0**. A wing-flap sine caught across a zero
     *    crossing cannot be the cause: the closest any bird in any theme at any golden clock comes
     *    to that crossing is `|sin| = 0.0038`, twelve orders of magnitude above the
     *    double-precision noise in an argument of ~1800 radians.
     *
     * 0.002 of a 360x800 frame is **576 pixels**, and passes all three. Anywhere in `[0, 14)` is
     * defensible and every value but one is arbitrary, so the gate is the floor itself. A raise is
     * the direction that would let a stale golden pass.
     *
     * A golden that differs at all is stale until somebody says why, which is what a golden is for.
     *
     * ### Why there is no second pass over a [GoldenFocus]
     *
     * At zero, a frame that passes has no differing pixel anywhere, so it has none inside any patch
     * of itself: a second count over a focus rectangle, by the same per-pixel rule, could never fail
     * after this one has passed. A focus is therefore not a gate in this harness. It names the part
     * of the frame the golden is about, and when the frame fails, the message says how many of the
     * differing pixels fall inside each focus. **If this fraction is ever raised above zero**, a
     * small sprite can move or vanish inside the allowance again, and each focus has to become a
     * gate again with a limit of its own -- below the share of its rectangle the smallest regression
     * it is about moves. The three GL references answer to [GlGolden]'s own gates, where a focus is
     * a gate (`GlGolden.Tolerance.Region`), and to `GlDriverGapGuardTest`.
     */
    const val CHANNEL_TOLERANCE = 8
    const val MAX_DIFFERING_FRACTION = 0.0

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun updating(): Boolean =
        InstrumentationRegistry.getArguments().getString("updateGoldens")?.toBoolean() == true

    /** Where a rejected or regenerated frame is written, for a human to look at. */
    private fun outputDir(): File =
        File(instrumentation.targetContext.getExternalFilesDir(null), "golden-output")
            .apply { mkdirs() }

    /**
     * Runs [GoldenScene.warmUpFrames] frames of [scene] into [target] and returns the scene clock they left.
     *
     * Every frame is a whole [PaperRenderer.draw], because "advance the renderer without drawing" is
     * not a state a running wallpaper is ever in: what a warm-up skips is only the rasterising of
     * frames nobody compares. [render] hands it [BlankTarget]; the GL harness warms its scenes up on
     * its own GL target, frame by frame, because there the order the sprites reach the atlas is part
     * of the picture.
     */
    fun warmUp(renderer: PaperRenderer, target: SceneCanvas, scene: GoldenScene): SceneTime {
        var clock = SceneTime(scene.sceneSeconds)
        repeat(scene.warmUpFrames) {
            clock += scene.warmUpDeltaSeconds
            renderer.draw(target, scene.dayPhase, clock, scene.warmUpDeltaSeconds)
        }
        return clock
    }

    /**
     * Draws [scene] into a bitmap through [CanvasSceneTarget], the real Canvas backend.
     *
     * The renderer is built fresh for every scene so nothing -- scroll accumulation, lightning
     * timer, cached paths -- carries from one golden into the next.
     *
     * **The warm-up is drawn on [BlankTarget], the compared frame on the bitmap.** The state a
     * warm-up leaves is the renderer's and its objects', never the target's: every [SceneCanvas]
     * call returns nothing, so the scene cannot read anything back from where it draws, and
     * [CanvasSceneTarget] changes only its own paints and its own shader cache, never a paint the
     * scene hands it. A sprite's pixels are pulled by the target from `SpriteCache`, which decodes
     * the same pixels whenever it is asked. So the frame compared is the same frame, and only the
     * Skia work of the frames before it goes. It starts on a fresh, transparent bitmap, and that
     * gives the same pixels as starting over earlier frames: the scene paints its sky over the whole
     * frame first, opaque -- at the [GoldenScene.day] and [GoldenScene.night] hours every warmed-up
     * golden is taken at, the sky's colours are the theme's own day or night pair, blended at 1 or at
     * 0, and come out at full alpha -- and draws everything else over it.
     */
    fun render(scene: GoldenScene): Bitmap {
        val renderer = PaperRenderer(WIDTH, HEIGHT, instrumentation.targetContext)
        scene.configure(renderer)
        val clock = warmUp(renderer, BlankTarget, scene)
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val target = CanvasSceneTarget()
        target.bind(Canvas(bitmap))
        renderer.draw(target, scene.dayPhase, clock, 0f)
        target.unbind()
        return bitmap
    }

    /**
     * A [SceneCanvas] that draws nothing: what a warm-up is drawn on when only the state it leaves is
     * wanted ([render]). Every call is accepted and dropped, so the scene runs exactly as it does on
     * a real target.
     */
    object BlankTarget : SceneCanvas {
        override fun save() = Unit
        override fun restore() = Unit
        override fun translate(dx: Float, dy: Float) = Unit
        override fun scale(sx: Float, sy: Float) = Unit
        override fun rotate(degrees: Float) = Unit
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) = Unit
        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) = Unit
        override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
        override fun drawWedge(cx: Float, cy: Float, radius: Float, startAngle: Float, sweepAngle: Float, paint: Paint) = Unit
        override fun drawShape(shape: SceneShape, paint: Paint) = Unit
        override fun drawVerticalGradientShape(
            shape: SceneShape,
            gradientTopY: Float,
            gradientBottomY: Float,
            topColor: Int,
            bottomColor: Int,
            alpha: Int,
        ) = Unit
        override fun drawVerticalGradientRect(left: Float, top: Float, right: Float, bottom: Float, topColor: Int, bottomColor: Int) = Unit
        override fun drawRadialGlow(cx: Float, cy: Float, radius: Float, color: Int, centerAlpha: Int) = Unit
        override fun drawSprite(
            resId: Int,
            source: SpriteSource,
            left: Float,
            top: Float,
            tintColor: Int,
            alpha: Int,
            additive: Boolean,
        ) = Unit
        override fun drawSpriteClipped(
            resId: Int,
            source: SpriteSource,
            left: Float,
            top: Float,
            tintColor: Int,
            alpha: Int,
            additive: Boolean,
            clipLeft: Float,
            clipTop: Float,
            clipRight: Float,
            clipBottom: Float,
        ) = Unit
        override fun drawDisc(resId: Int, source: SpriteSource, cx: Float, cy: Float, radius: Float, colour: Int, alpha: Int) = Unit
    }

    /**
     * Renders [scene] and asserts it still looks like its committed golden: not one pixel beyond
     * [CHANNEL_TOLERANCE] ([MAX_DIFFERING_FRACTION]), over the whole frame.
     *
     * One assertion per committed PNG: a second assertion of the same scene against the same file,
     * at the same zero, fails and passes with the first on every change, so it carries nothing of its
     * own (see `GoldenUniquenessTest` for the files). The scene's [GoldenScene.focus] rectangles are
     * not a second pass ([MAX_DIFFERING_FRACTION] says why); a failure reports how many of the
     * differing pixels fall inside each.
     */
    fun assertMatches(scene: GoldenScene) {
        scene.requireDeterministicLightning()
        val actual = render(scene)
        assertReproducesOverTime(scene, actual)
        dumpIfAsked(scene.name, actual)
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
            // Where the change fell against what the golden is about: a report, not a second gate.
            val inFocus = scene.focus.joinToString("") { focus ->
                " Inside '${focus.label}' (${focus.left},${focus.top})-(${focus.right},${focus.bottom}): " +
                    "${countDiffering(expected, actual, focus.left, focus.top, focus.right, focus.bottom)} " +
                    "of ${focus.area}."
            }
            fail(
                "Golden '${scene.name}' changed: $differing pixels differ by more than " +
                    "$CHANNEL_TOLERANCE per channel (${"%.3f".format(fraction * 100)}%, limit " +
                    "${"%.3f".format(MAX_DIFFERING_FRACTION * 100)}%).$inFocus The rendered frame and a " +
                    "diff are in ${outputDir()}. If the change was intended, say why before " +
                    "regenerating the golden.",
            )
        }
    }

    /**
     * How far the clock is moved for [assertReproducesOverTime]: **191 days, 13 hours and 7
     * minutes.**
     *
     * Not a round number, and not a small one, for three separate reasons:
     *
     *  - **the hour of the day changes**, which is what a scene pinned to `fixedHour` must be
     *    immune to;
     *  - **the day of the year and the season change**, which is what the seasonal calendar and
     *    the sunrise/sunset approximation would be read through;
     *  - **the moon is on the other side of its cycle.** 191 days 13 h 7 min is 6.4864 synodic
     *    months, so the phase advances by 0.4864 -- and there is no starting phase for which the
     *    pair falls in the same one of the nine pictures `drawMoonWithPhase` can paint. (The two
     *    can share an *illuminated* bucket: `illuminated` is `sin^2(pi * phase)`, symmetric about
     *    the full moon, so the pair meets at one phase per half cycle. At that phase they are on
     *    opposite sides of it, which is a 180 degree rotation and a different picture. A mutation
     *    that leaks the clock into `illuminated` **and not** into `waxing` can pass.)
     *
     * That last point is why the displacement is large rather than a few hours. The leak is a
     * *threshold* crossing: a few hours moves the real moon across a boundary on two or three days
     * a month and on the rest changes nothing, so a short displacement leaves a moon-phase leak
     * green on most evenings.
     *
     * The pair is `now` and `now + this`, not two fixed instants, so it walks with the calendar:
     * a leak that this pair happens not to separate today is separated by the same check tomorrow.
     */
    const val REPRODUCIBILITY_DISPLACEMENT_MILLIS = 191L * 86_400_000L + 13L * 3_600_000L + 7L * 60_000L

    /**
     * **The frame has to be the same frame tomorrow.**
     *
     * Renders [scene] a second time with the device's wall clock moved
     * [REPRODUCIBILITY_DISPLACEMENT_MILLIS] into the future and requires the two frames to be
     * pixel-identical -- exactly, with none of [CHANNEL_TOLERANCE]'s allowance, because this is the
     * same renderer on the same device drawing the same description twice and anti-aliasing has
     * nothing to differ about (see [countDifferingExactly]).
     *
     * ### The hole it closes
     *
     * [SceneGolden]'s own notes argue that restricting the double-regeneration check to *changed*
     * scenes loses nothing, because "a newly non-deterministic scene is a changed scene by
     * definition -- it cannot match its committed golden". That sentence is true of a scene that
     * is non-deterministic **within a run** and false of one that is non-deterministic **over
     * time**: a render path that reads `System.currentTimeMillis()` -- the moon's phase is the
     * one this check is written against -- matches its golden at one hour and not at another, on a
     * build that has not changed, and no regeneration pass can notice it.
     *
     * Every Canvas golden pays for this, rather than a list of the scenes somebody thought could
     * be affected: a list of scenes that read the clock is a list nobody can maintain, which is
     * the whole argument this project makes against hand-kept counts. The GL harness is not
     * separately covered and does not need to be -- what it would be testing is
     * [PaperRenderer], which is the same object this renders through, and a clock read there
     * fails here first.
     *
     * The cost is one extra render of each scene, warm-up included, plus two `cmd alarm` calls.
     */
    fun assertReproducesOverTime(scene: GoldenScene, firstFrame: Bitmap) {
        val later = DeviceClock.displacedBy(REPRODUCIBILITY_DISPLACEMENT_MILLIS) { render(scene) }
        val differing = countDifferingExactly(later, firstFrame)
        if (differing == 0) return
        write(firstFrame, File(outputDir(), "${scene.name}-now.png"))
        write(later, File(outputDir(), "${scene.name}-later.png"))
        fail(
            "Golden scene '${scene.name}' does not reproduce over time: rendered twice on this " +
                "build, seconds apart, with nothing changed but the phone's wall clock " +
                "(+${REPRODUCIBILITY_DISPLACEMENT_MILLIS / 86_400_000L} days), the two frames " +
                "differ in $differing pixels. Something on the render path is reading the real " +
                "clock instead of taking it as an input, so this golden can go red on its own " +
                "without a line of code changing -- and green again later. Both frames are in " +
                "${outputDir()} as '${scene.name}-now.png' and '${scene.name}-later.png'. Do not " +
                "re-author the golden: find the read.",
        )
    }

    private fun reject(scene: GoldenScene, actual: Bitmap, expected: Bitmap) {
        write(actual, File(outputDir(), "${scene.name}-actual.png"))
        write(diffImage(expected, actual), File(outputDir(), "${scene.name}-diff.png"))
    }

    /**
     * Whether the only thing that happened at one pixel is a ground shadow falling on it.
     *
     * The vehicles are the one part of the scene that draws something translucent:
     * `drawGroundShadow` lays a `0x2E000000` oval on the road, which multiplies whatever is behind
     * it by (255-46)/255 and leaves the hue alone. Every other thing a vehicle draws is opaque
     * paint, whose colour has nothing to do with what was underneath.
     *
     * Measurements of the form "how tall is this vehicle" sweep a column for pixels that differ
     * from an empty frame, and they have to skip this: the oval sits on the road, straddling the
     * wheel contact and reaching four units below it, so a sweep of the whole column would return
     * the vehicle plus half a shadow, and `VehicleOccupantScaleTest` and `VehicleScalePixelTest`
     * would measure that.
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

    /**
     * The share of the whole frame on which two renders disagree, by the same rule a golden uses.
     *
     * Exposed so `TrafficGoldenTest` can show that a plausible traffic regression moves more than
     * [MAX_DIFFERING_FRACTION] — i.e. that the golden would actually catch it — rather than
     * asserting that it would.
     */
    fun differingFraction(expected: Bitmap, actual: Bitmap): Double =
        countDiffering(expected, actual, 0, 0, WIDTH, HEIGHT) / (WIDTH * HEIGHT).toDouble()

    /**
     * Pixels on which two frames disagree **at all**, with none of [CHANNEL_TOLERANCE]'s
     * allowance.
     *
     * [countDiffering]'s tolerance exists for a comparison across Skia builds and Android
     * versions; [assertReproducesOverTime] compares two frames drawn by one process on one device
     * seconds apart, where there is nothing for anti-aliasing to differ about and a single
     * greyscale level of difference is already a leak. A clock read that moves whole pixels fails
     * either way; one that only tints something fails only here.
     */
    private fun countDifferingExactly(a: Bitmap, b: Bitmap): Int {
        val row = IntArray(WIDTH)
        val otherRow = IntArray(WIDTH)
        var differing = 0
        for (y in 0 until HEIGHT) {
            a.getPixels(row, 0, WIDTH, 0, y, WIDTH, 1)
            b.getPixels(otherRow, 0, WIDTH, 0, y, WIDTH, 1)
            for (x in 0 until WIDTH) if (row[x] != otherRow[x]) differing++
        }
        return differing
    }

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

    /** With `-e dumpFrames true`, every rendered frame is written out for attribution. */
    private fun dumpIfAsked(name: String, bitmap: Bitmap) {
        val args = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        if (args.getString("dumpFrames") != "true") return
        write(bitmap, File(outputDir(), "$name-frame.png"))
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
     * Also emits a regenerated frame to logcat, base64 in chunks.
     *
     * **Because the file on the device is not retrievable.** The frames are written to the app's
     * external files directory, which since Android 11 the `shell` user cannot read — and AGP
     * uninstalls the app after `connectedAndroidTest` anyway, taking the directory with it. A
     * diagnostics step exists only to be read: a regenerated golden that nobody can fetch is
     * evidence that cannot be retrieved, which is not evidence.
     *
     * Runs for every frame [write] writes: under `-e updateGoldens true` or `-e dumpFrames true`, and for
     * a golden that is missing, fails or does not reproduce. A run whose goldens all pass, with neither
     * argument, pays nothing.
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
