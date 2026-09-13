package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **What the shipped lightning actually does, measured rather than argued.**
 *
 * `BACKLOG_v4_31.md` item 112 is closed by giving the golden harness a way to switch the strike
 * timer off for the one scene that warms up through a storm ([GoldenScene.pinLightning]). The
 * maintainer's condition on that fix is that **the lightning the wallpaper draws does not change**:
 * same frequency, same look, still rolled from the unseeded `Random` and still off the scene clock.
 *
 * A claim like that is only worth what it is measured against, and "the default is the same object"
 * is not a measurement. So this runs the **production configuration** — a storm with nothing
 * pinned, which is what a phone renders — for [DEFAULT_FRAMES] frames of scene clock, or as many
 * as `-e lightningFrames` asks for, and reports what the sky did:
 *
 *  - how many frames carried a flash, and therefore the strike interval in frames;
 *  - how much each flash lifted the frame's mean channel.
 *
 * Both numbers are properties of `updateLightning`/`drawLightningFlash` alone, so running this on
 * the release before the change and on the release after it compares the shipped behaviour
 * directly. v5.0's report has the two columns.
 *
 * **It is also a standing gate against the opposite mistake.** The fix adds a switch that can turn
 * the strike timer off; the way to get that wrong is to leave it off for everybody. This test
 * fails if a storm rendered the way the wallpaper renders it produces no strikes at all —
 * the shipped interval is `4 + U(0, 8)` s and the first one `4 + U(0, 6)` s, so even the cheap
 * default below contains a strike whatever the `Random` produces — it cannot flake in that
 * direction.
 *
 * It deliberately asserts **nothing about the exact count**: the interval is random by design and
 * pinning a count here would be the clock-driven lightning the maintainer turned down.
 */
@RunWith(AndroidJUnit4::class)
class LightningCadenceTest {

    private companion object {
        /**
         * 120 seconds of storm at the golden warm-up cadence, which is about fifteen strikes and
         * **cannot** be zero: the longest the shipped roll defers one is 12 s.
         *
         * The release A/B of v5.0 Fase 0 ran this at `-e lightningFrames 2000` — 500 s, about
         * sixty strikes — on the release before the change and on the release after it. That is
         * what the argument is for: the suite pays for the gate, and a measurement pays for itself.
         */
        const val DEFAULT_FRAMES = 480
        const val DELTA = 0.25f

        /**
         * Half of the predicted lift of a strike frame (item 112 measured 21.9 of 255), which is
         * far above anything the scene itself moves the whole-frame mean by between two frames.
         */
        const val FLASH_LIFT_THRESHOLD = 10.0
    }

    @Test
    fun stormStrikesAtTheShippedCadence() {
        val frames = InstrumentationRegistry.getArguments()
            .getString("lightningFrames")?.toIntOrNull() ?: DEFAULT_FRAMES
        val bitmap = Bitmap.createBitmap(SceneGolden.WIDTH, SceneGolden.HEIGHT, Bitmap.Config.ARGB_8888)
        val target = CanvasSceneTarget()
        target.bind(Canvas(bitmap))
        val renderer = PaperRenderer(
            SceneGolden.WIDTH,
            SceneGolden.HEIGHT,
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
        val scene = SharedGoldenScenes.thunderstorm()
        scene.configure(renderer)

        var clock = SceneTime(scene.sceneSeconds)
        val means = DoubleArray(frames)
        repeat(frames) { frame ->
            clock += DELTA
            renderer.draw(target, scene.dayPhase, clock, DELTA)
            means[frame] = meanChannelOf(bitmap)
        }
        target.unbind()
        bitmap.recycle()

        // A flash is a frame that sits well above both of its neighbours: the veil is drawn on
        // exactly one frame per strike (alpha 1 set and decayed by 0.75 in the same call), so a
        // strike is a spike and never a step.
        val strikes = mutableListOf<Int>()
        val lifts = mutableListOf<Double>()
        for (frame in 1 until frames - 1) {
            val lift = means[frame] - (means[frame - 1] + means[frame + 1]) / 2.0
            if (lift > FLASH_LIFT_THRESHOLD) {
                strikes += frame
                lifts += lift
            }
        }

        val intervals = strikes.zipWithNext { a, b -> b - a }
        android.util.Log.i(
            TAG,
            "frames=$frames delta=$DELTA strikes=${strikes.size} " +
                "meanIntervalFrames=${"%.2f".format(intervals.average())} " +
                "minIntervalFrames=${intervals.minOrNull()} maxIntervalFrames=${intervals.maxOrNull()} " +
                "meanLift=${"%.2f".format(lifts.average())} " +
                "minLift=${"%.2f".format(lifts.minOrNull() ?: 0.0)} " +
                "maxLift=${"%.2f".format(lifts.maxOrNull() ?: 0.0)}",
        )
        android.util.Log.i(TAG, "strikeFrames=${strikes.joinToString(",")}")

        assertTrue(
            "a storm rendered the way the wallpaper renders it produced no lightning at all in " +
                "$frames frames. The shipped interval is 4 + U(0, 8) s, so this is not bad luck: " +
                "something switched the strike timer off for production.",
            strikes.isNotEmpty(),
        )
    }
}

/**
 * The frame's mean channel over a fixed subsample -- a full-screen veil needs no more.
 *
 * Shared with [LightningPinTest] so the two tests that look for a flash look for the same thing.
 */
internal fun meanChannelOf(bitmap: Bitmap): Double {
    val row = IntArray(SceneGolden.WIDTH)
    var total = 0L
    var count = 0
    var y = 0
    while (y < SceneGolden.HEIGHT) {
        bitmap.getPixels(row, 0, SceneGolden.WIDTH, 0, y, SceneGolden.WIDTH, 1)
        var x = 0
        while (x < SceneGolden.WIDTH) {
            val pixel = row[x]
            total += ((pixel shr 16) and 0xFF) + ((pixel shr 8) and 0xFF) + (pixel and 0xFF)
            count += 3
            x += 4
        }
        y += 8
    }
    return total.toDouble() / count
}

private const val TAG = "LIGHTNINGCADENCE"
