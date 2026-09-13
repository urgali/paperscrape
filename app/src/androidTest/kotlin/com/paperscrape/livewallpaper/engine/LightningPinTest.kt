package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.weather.LiveWeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **The rule that says a golden may not warm a scene up through a storm, and the switch that lets
 * the one that does satisfy it.** `BACKLOG_v4_31.md` item 112.
 *
 * Two halves, and neither is worth much without the other:
 *
 *  - [GoldenScene.requireDeterministicLightning] is the guard. It is new, it runs on every golden
 *    assertion in both harnesses, and a guard nobody has watched reject anything is a guard nobody
 *    knows the shape of — so the first four tests hand it the scenes it exists to catch and the
 *    neighbouring ones it must not;
 *  - [PaperRenderer.lightningStrikesEnabled] is the switch the one caught scene uses to comply.
 *    The last test is the two-sided demonstration that it switches: with the storm running the way
 *    the wallpaper runs it, the sky flashes; with the strike timer pinned, it does not, and
 *    everything else about the frame is unchanged.
 *
 * **Nothing here is probabilistic.** The shipped interval is `4 + U(0, 8)` s and the *first* one is
 * `4 + U(0, 6)` s, so both are at most 12 s: [STORM_SECONDS] of storm clock contains at least one
 * strike whatever the unseeded `Random` produces. That is why this can assert "it flashed" rather
 * than "it usually flashes", which is the distinction item 112 is about in the first place.
 */
@RunWith(AndroidJUnit4::class)
class LightningPinTest {

    private companion object {
        /** Comfortably longer than the 12 s that is the longest the shipped roll can defer a strike. */
        const val STORM_SECONDS = 20f
        const val DELTA = 0.25f
        const val STORM_FRAMES = (STORM_SECONDS / DELTA).toInt()

        /** Half the predicted lift of a strike frame (item 112: 21.8 predicted, 21.9 measured). */
        const val FLASH_LIFT_THRESHOLD = 10.0
    }

    private val liveStorm = LiveWeatherSnapshot(
        precipitationType = PrecipitationType.RAIN,
        precipitationIntensity = 1f,
        cloudCoverFraction = 1f,
        isThunderstorm = true,
        fetchedAtMillis = 0L,
    )

    // -- The guard ----------------------------------------------------------------------------

    /** The shape of `wave-storm` between v4.28 and v5.0: warmed up, storming, nothing pinned. */
    @Test
    fun aWarmedUpLiveStormWithoutAPinIsRejected() {
        val scene = GoldenScene(
            name = "guard-live-storm",
            dayPhase = GoldenScene.day(),
            themeId = "beach",
            warmUpFrames = STORM_FRAMES,
            warmUpDeltaSeconds = DELTA,
            weather = liveStorm,
        )
        assertTrue("the scene under test must actually be a warmed-up storm", scene.warmsUpThroughAStorm)
        // Through the harness, not through the guard directly: what is being shown is that
        // `assertMatches` runs it, and that it runs *before* the render or it protects nothing.
        val error = harnessRejectionFor(scene)
        if (error == null) fail("a warmed-up storm with no pin was accepted by the golden harness")
        assertTrue(
            "the rejection must say which scene and what to do about it, not just that something " +
                "failed: '${error?.message}'",
            error?.message.orEmpty().contains("guard-live-storm") &&
                error?.message.orEmpty().contains("pinLightning"),
        )
    }

    /**
     * The same rule through the *theme's own* storm toggle, which is the other way a storm starts.
     *
     * `LiveWeatherSceneRules.stormActive` falls back to the customisation when no forecast is
     * overriding it, so a guard that only read [GoldenScene.weather] would have a hole exactly the
     * width of a scene that switches the storm on by hand.
     */
    @Test
    fun aWarmedUpThemeStormWithoutAPinIsRejected() {
        val scene = GoldenScene(
            name = "guard-theme-storm",
            dayPhase = GoldenScene.day(),
            warmUpFrames = STORM_FRAMES,
            warmUpDeltaSeconds = DELTA,
            customise = {
                it.copy(
                    precipitation = it.precipitation.copy(
                        visible = true,
                        type = PrecipitationType.RAIN,
                        intensity = 1f,
                        thunderstorm = true,
                    ),
                )
            },
        )
        assertTrue("the scene under test must actually be a warmed-up storm", scene.warmsUpThroughAStorm)
        val error = harnessRejectionFor(scene)
        if (error == null) fail("a warmed-up storm declared through the theme's own toggle was accepted")
        assertTrue(
            "the rejection must name the scene: '${error?.message}'",
            error?.message.orEmpty().contains("guard-theme-storm"),
        )
    }

    /**
     * A **cold** storm is exactly what the rule has always allowed, and `thunderstorm` — the scene
     * the Canvas suite and the GL suite both pin — is one. The timer cannot fire in a single frame
     * drawn at `deltaSeconds = 0`, so there is nothing to pin and nothing to forgive.
     */
    @Test
    fun aColdStormIsAccepted() {
        SharedGoldenScenes.thunderstorm().requireDeterministicLightning()
        assertTrue(
            "a scene with no warm-up cannot advance the strike timer",
            !SharedGoldenScenes.thunderstorm().warmsUpThroughAStorm,
        )
    }

    /**
     * And the rule in the other direction: a pin on a scene that cannot flash anyway is rejected.
     *
     * The failure mode this closes is not a flake, it is erosion. One pin on the scene that needs
     * it is a guard that keeps working; a pin copied onto every storm scene for safety is a guard
     * that never fires again, and a suite that has quietly decided no golden may ever show a
     * lightning flash.
     */
    @Test
    fun aPinOnASceneThatCannotFlashIsRejected() {
        val scene = GoldenScene(
            name = "guard-pointless-pin",
            dayPhase = GoldenScene.day(),
            weather = liveStorm,
            pinLightning = true,
        )
        assertTrue("a cold storm cannot advance the timer", !scene.warmsUpThroughAStorm)
        val error = harnessRejectionFor(scene)
        if (error == null) fail("a pin on a scene with no warm-up was accepted")
        assertTrue(
            "the rejection must say the pin is unnecessary, not that the scene is wrong: " +
                "'${error?.message}'",
            error?.message.orEmpty().contains("does not need to"),
        )
    }

    /** And a warm-up in weather that is not a storm, which is `umbrella-rain` and the traffic pair. */
    @Test
    fun aWarmedUpSceneInPlainRainIsAccepted() {
        val scene = GoldenScene(
            name = "guard-plain-rain",
            dayPhase = GoldenScene.day(),
            warmUpFrames = STORM_FRAMES,
            warmUpDeltaSeconds = DELTA,
            weather = LiveWeatherSnapshot(
                precipitationType = PrecipitationType.RAIN,
                precipitationIntensity = 0.6f,
                cloudCoverFraction = 0.9f,
                isThunderstorm = false,
                fetchedAtMillis = 0L,
            ),
        )
        assertTrue("rain is not a storm", !scene.warmsUpThroughAStorm)
        scene.requireDeterministicLightning()
    }

    /**
     * Runs [scene] through the Canvas golden harness and hands back what it threw, or null.
     *
     * **The only `SceneGolden.assertMatches` call in this file, on purpose.** The Canvas golden
     * count is taken by grepping for that call (`CLAUDE.md` §5), so every extra one here would be a
     * golden that does not exist; there is one, it is this, and it is subtracted where the count is
     * written down. Caught outside the `try` as well: `fail` throws an `AssertionError` of its own,
     * and a catch that swallows it is a test that cannot report what it is testing for.
     */
    private fun harnessRejectionFor(scene: GoldenScene): AssertionError? = try {
        SceneGolden.assertMatches(scene)
        null
    } catch (expected: AssertionError) {
        expected
    }

    // -- The switch ---------------------------------------------------------------------------

    /**
     * **Both sides of it in one run: the storm that flashes, and the same storm pinned twice.**
     *
     * The unpinned half is the production configuration — nothing in `src/main` ever writes
     * [PaperRenderer.lightningStrikesEnabled], so this is the sky a phone draws — and it must
     * still flash, or the fix would have quietly taken the lightning out of the wallpaper. The
     * pinned half is what a golden renders: it must never flash, and **two runs of it must be the
     * same frame to the pixel**, which is the property item 112 says `wave-storm` did not have.
     *
     * **Why the two pinned runs rather than pinned against unpinned.** The obvious comparison —
     * "the pinned picture is the unpinned picture minus a veil" — is the coin itself: the measured
     * frame is drawn at `deltaSeconds = 0`, so it inherits whatever alpha the last warm-up frame
     * left, and about one unpinned run in 32 ends on a strike. A test built on that assertion would
     * have been a new flake written to close an old one. What the unpinned picture equals is
     * settled where it belongs instead: the committed `wave-storm.png` was captured before any of
     * this existed, and the pinned scene renders it at **0 differing pixels**.
     */
    @Test
    fun theStrikeTimerFiresUnpinnedAndIsSilentAndRepeatablePinned() {
        val unpinned = runStorm(pinned = false)
        val pinned = runStorm(pinned = true)
        val pinnedAgain = runStorm(pinned = true)
        val repeatDiff = differingPixels(pinned.finalFrame, pinnedAgain.finalFrame)

        android.util.Log.i(
            "LIGHTNINGPIN",
            "frames=$STORM_FRAMES unpinnedFlashes=${unpinned.flashes} pinnedFlashes=${pinned.flashes} " +
                "unpinnedMaxLift=${"%.2f".format(unpinned.maxLift)} " +
                "pinnedMaxLift=${"%.2f".format(pinned.maxLift)} " +
                "pinnedRepeatDiffPixels=$repeatDiff",
        )

        assertTrue(
            "a storm rendered the way the wallpaper renders it did not flash once in " +
                "$STORM_SECONDS s. The shipped roll defers a strike by at most 12 s, so this is " +
                "not luck: production lightning has been switched off.",
            unpinned.flashes > 0,
        )
        assertEquals(
            "a pinned storm flashed, so the golden it protects is still a coin flip",
            0, pinned.flashes,
        )
        assertEquals(
            "two renders of the same pinned storm differ, so pinning the strike timer is not the " +
                "whole of what made this scene unrepeatable",
            0, repeatDiff,
        )
    }

    private class Run(val flashes: Int, val maxLift: Double, val finalFrame: Bitmap)

    private fun differingPixels(a: Bitmap, b: Bitmap): Int =
        Math.round(SceneGolden.differingFraction(a, b) * SceneGolden.WIDTH * SceneGolden.HEIGHT).toInt()

    /**
     * Draws [STORM_FRAMES] frames of a storm and reports what the sky did.
     *
     * The last frame is drawn at `deltaSeconds = 0`, exactly as a golden's measured frame is, so
     * the two runs' final frames are comparable to a golden's own standard. A flash is detected as
     * a spike rather than a step, because the veil lives on exactly one frame.
     */
    private fun runStorm(pinned: Boolean): Run {
        val bitmap = Bitmap.createBitmap(SceneGolden.WIDTH, SceneGolden.HEIGHT, Bitmap.Config.ARGB_8888)
        val target = CanvasSceneTarget()
        target.bind(Canvas(bitmap))
        val renderer = PaperRenderer(
            SceneGolden.WIDTH,
            SceneGolden.HEIGHT,
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
        val scene = GoldenScene(
            name = "lightning-pin-$pinned",
            dayPhase = GoldenScene.day(),
            themeId = "beach",
            warmUpFrames = STORM_FRAMES,
            warmUpDeltaSeconds = DELTA,
            weather = liveStorm,
            pinLightning = pinned,
        )
        scene.configure(renderer)

        var clock = SceneTime(scene.sceneSeconds)
        val means = DoubleArray(STORM_FRAMES)
        repeat(STORM_FRAMES) { frame ->
            clock += DELTA
            renderer.draw(target, scene.dayPhase, clock, DELTA)
            means[frame] = meanChannelOf(bitmap)
        }
        // The measured frame of a golden: the clock where the warm-up left it, no time passing.
        renderer.draw(target, scene.dayPhase, clock, 0f)
        target.unbind()

        var flashes = 0
        var maxLift = 0.0
        for (frame in 1 until STORM_FRAMES - 1) {
            val lift = means[frame] - (means[frame - 1] + means[frame + 1]) / 2.0
            if (lift > FLASH_LIFT_THRESHOLD) flashes++
            if (lift > maxLift) maxLift = lift
        }
        return Run(flashes, maxLift, bitmap)
    }
}
