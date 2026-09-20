package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.weather.LiveWeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A new forecast may not redraw the sky between two frames** (v5.5B).
 *
 * The report: *"quando riaccendo il cellulare da deep sleep, con live weather vedo un 'reset' delle
 * nuvole: c'e' un piccolo scatto e scompaiono o appaiono dal nulla nuvole, poi e' tutto fluido"*.
 * Measured on the BV6600: the Live Weather loop parks while the engine is invisible and re-enters
 * its body on `onVisibilityChanged(true)`, so an hourly refresh that fell due behind a dark screen
 * lands **on the frame the screen comes back**; the candidate pool then admits or drops every cloud
 * between the old cover and the new one in that single frame.
 *
 * `CarNightCrossfadeTest` is the same rule for the road, and the shape of this test is deliberately
 * its shape: drive the scene through the change the user does not touch, read every frame back, and
 * require the change to be *spread out*. What differs is the measure. A car is a run of pixels on a
 * road and can be tracked; 41 heavily overlapping clouds are one silhouette, so what is measured is
 * how much of the band changed between consecutive frames — which is the quantity the report is
 * actually about.
 *
 * ### Why two assertions and not one
 *
 * The defect has two halves that need different evidence:
 *
 *  - [noSingleFrameRedrawsTheBand] is the regression gate. It fails if any one frame of the
 *    transition changes more of the cloud band than a settled frame's own drift does by a wide
 *    margin. Both eases in [CloudCoverFade] are needed to pass it: with the cover ramp alone each
 *    crossing is still a whole cloud arriving in one frame, and with the per-candidate fade alone
 *    the whole margin of the sky dissolves at once.
 *  - [aSettledSkyIsUnchanged] is the other half of the bargain, and the one the goldens rest on:
 *    once the transition is over, the frames are **byte-identical** to the ones the same scene drew
 *    before the fade existed. A fade that left the sky permanently a hair off would pass the first
 *    assertion and be a worse bug than the one being fixed.
 *
 * ### The budget, and where it comes from
 *
 * Not a guess and not a tolerance: [driftChurn] measures what a settled frame changes at this frame
 * rate on this scene, and the gate is that multiple of it. The transition must not be distinguishable
 * from ordinary drift by more than [CHURN_MULTIPLE]x. A single popped cloud is far past that — one
 * cloud is thousands of band pixels against a drift of a few hundred — which is what makes the
 * mutation proof unambiguous rather than marginal.
 */
class CloudCoverCrossfadeTest {

    private class Sky {
        val bitmap: Bitmap = Bitmap.createBitmap(SceneGolden.WIDTH, SceneGolden.HEIGHT, Bitmap.Config.ARGB_8888)
        private val target = CanvasSceneTarget()
        private val renderer = PaperRenderer(
            SceneGolden.WIDTH,
            SceneGolden.HEIGHT,
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
        private var clock = SceneTime(120.0)

        init {
            GoldenScene(name = "cloud-crossfade", dayPhase = GoldenScene.day()).configure(renderer)
            renderer.sceneCustomization = renderer.sceneCustomization.let {
                // Clouds on, and **the birds off**. They cross the same band and would land in
                // both the drift floor and the transition, which cancels in the ratio but raises
                // the floor -- and a floor raised by something the test is not about is a gate
                // that has been quietly loosened. Nothing else in the band moves: `configure`
                // pins both scroll inputs, the sun is fixed by the day phase, and the stars are
                // invisible at hour 13.
                it.copy(
                    clouds = it.clouds.copy(visible = true),
                    birds = it.birds.copy(visible = false),
                )
            }
            target.bind(Canvas(bitmap))
        }

        /** Hands the renderer a forecast with this cover and nothing falling out of it. */
        fun cover(fraction: Float) {
            renderer.liveWeatherOverride = LiveWeatherSnapshot(
                precipitationType = null,
                precipitationIntensity = 0f,
                cloudCoverFraction = fraction,
                isThunderstorm = false,
                fetchedAtMillis = 0L,
            )
        }

        fun frame() {
            clock += FRAME_SECONDS
            renderer.draw(target, GoldenScene.day(), clock, FRAME_SECONDS)
        }

        /** The cloud band's pixels, as ARGB, for the row range the band can occupy. */
        fun band(): IntArray {
            val pixels = IntArray(SceneGolden.WIDTH * (BAND_BOTTOM - BAND_TOP))
            bitmap.getPixels(pixels, 0, SceneGolden.WIDTH, 0, BAND_TOP, SceneGolden.WIDTH, BAND_BOTTOM - BAND_TOP)
            return pixels
        }
    }

    /**
     * How many band pixels moved by at least [minLevels] on some channel between two readings.
     *
     * **The threshold is the whole reason this is not simply `a[i] != b[i]`.** The eased cover also
     * feeds `stormStrength`, so the entire sky is being re-tinted a little every frame -- and the
     * tint is quantised to 8 bits, so at the frame where the rounding tips over, *every open-sky
     * pixel in the band changes by one level at once*. Measured: an exact comparison called that
     * 76 736 pixels of a 129 600-pixel band, which is the open sky and not a cloud. That is the
     * gradual change working, reported as though it were the instant one.
     *
     * [POP_LEVELS] is where a cloud is. Sky and cloud are ~100 levels apart here, a cloud crossing
     * its threshold inside [CloudCoverFade.FADE_SECONDS] moves about 4 levels a frame at this
     * cadence, and a quantisation tip is 1 -- so 16 separates "a cloud arrived whole" from both.
     */
    private fun churn(a: IntArray, b: IntArray, minLevels: Int): Int {
        var n = 0
        for (i in a.indices) {
            val x = a[i]
            val y = b[i]
            if (x == y) continue
            val dr = kotlin.math.abs(((x shr 16) and 0xFF) - ((y shr 16) and 0xFF))
            val dg = kotlin.math.abs(((x shr 8) and 0xFF) - ((y shr 8) and 0xFF))
            val db = kotlin.math.abs((x and 0xFF) - (y and 0xFF))
            if (maxOf(dr, maxOf(dg, db)) >= minLevels) n++
        }
        return n
    }

    /**
     * The churn a *settled* sky produces frame to frame, at the cover the transition starts from
     * and the one it ends at, taking the larger.
     *
     * Measured rather than written down, so the gate follows the artwork: redraw the cloud sprite
     * bigger and this rises with it instead of turning into a tolerance nobody re-derived.
     */
    private fun driftChurn(): Int {
        var worst = 0
        for (cover in listOf(COVER_FROM, COVER_TO)) {
            val sky = Sky()
            sky.cover(cover)
            repeat(WARM_UP_FRAMES) { sky.frame() }
            var previous = sky.band()
            repeat(SETTLED_FRAMES) {
                sky.frame()
                val now = sky.band()
                worst = maxOf(worst, churn(previous, now, POP_LEVELS))
                previous = now
            }
        }
        return worst
    }

    /** The worst single-frame churn of a transition from [from] to [to], and the frame it was on. */
    private fun worstTransitionChurn(from: Float, to: Float): Pair<Int, Int> {
        val sky = Sky()
        sky.cover(from)
        repeat(WARM_UP_FRAMES) { sky.frame() }

        // The refresh lands. Nothing else about the scene changes -- same clock cadence, same
        // theme, same frame size -- so every pixel that moves from here moves because of it.
        sky.cover(to)
        var previous = sky.band()
        var worst = 0
        var worstFrame = -1
        repeat(TRANSITION_FRAMES) { index ->
            sky.frame()
            val now = sky.band()
            val moved = churn(previous, now, POP_LEVELS)
            if (moved > worst) {
                worst = moved
                worstFrame = index
            }
            previous = now
        }
        return worst to worstFrame
    }

    @Test
    fun noSingleFrameRedrawsTheBand() {
        val drift = driftChurn()
        // Two floors, and the larger wins. The multiplicative one follows the artwork; the absolute
        // one exists because with [POP_LEVELS] applied a settled sky's drift measures **zero** on
        // this scene -- every pixel it moves, it moves by less than a cloud -- and three times zero
        // is a gate nothing can pass. See [ABSOLUTE_FLOOR] for where its value comes from.
        val budget = maxOf(drift * CHURN_MULTIPLE, ABSOLUTE_FLOOR)

        // Two transitions, because they leave `drawClouds` by different doors. The first is the one
        // measured on the device -- Milan's own hourly step. The second is the forecast reporting a
        // **clear sky**, where `LiveWeatherSceneRules.cloudDensity` returns null and the function
        // takes its early return: the last clouds have to finish leaving on the frames *after* the
        // layer has already been told to place nothing, which is its own way of losing a fade.
        for ((from, to) in listOf(COVER_FROM to COVER_TO, COVER_FROM to COVER_CLEAR)) {
            val (worst, worstFrame) = worstTransitionChurn(from, to)
            // Printed on every run, pass or fail, because these are the numbers a release has to
            // report and a number that only appears in a failure is a number nobody re-measures.
            android.util.Log.i(
                LOG_TAG,
                "CLOUD-CROSSFADE $from -> $to: worst single-frame churn $worst px " +
                    "at frame $worstFrame/$TRANSITION_FRAMES; drift floor $drift; budget $budget",
            )
            assertTrue(
                "a cover change from $from to $to redrew $worst pixels of the cloud band " +
                    "in one frame (frame $worstFrame of $TRANSITION_FRAMES), past the $budget budget. " +
                    "The sky is arriving at the new forecast in one step again -- see CloudCoverFade.",
                worst <= budget,
            )
        }
    }

    @Test
    fun aSettledSkyIsUnchanged() {
        // Two skies, same cover, one of which got there by easing from somewhere else. Once both
        // are settled they must agree pixel for pixel: the ease is a transient, not a new steady
        // state, which is what lets the 33 goldens stand untouched.
        // The two run the **same number of frames**, so their scene clocks agree to the bit and
        // every cloud that survives is in the same place in both; the only thing being compared is
        // how they got to this cover.
        val direct = Sky()
        direct.cover(COVER_TO)
        repeat(WARM_UP_FRAMES + TRANSITION_FRAMES) { direct.frame() }

        val eased = Sky()
        eased.cover(COVER_FROM)
        repeat(WARM_UP_FRAMES) { eased.frame() }
        eased.cover(COVER_TO)
        repeat(TRANSITION_FRAMES) { eased.frame() }

        assertEquals(
            "a sky that eased into $COVER_TO is not the sky that was handed $COVER_TO outright, " +
                "after both settled -- the fade has left a permanent difference.",
            0,
            churn(direct.band(), eased.band(), 1),
        )
    }

    private companion object {
        /**
         * 15 fps rather than 30, which halves what this test costs the instrumented suite.
         *
         * Both eases are driven by `deltaSeconds`, so the scene passes through exactly the same
         * states, sampled half as often. The gate is unaffected because it is a **ratio** measured
         * at this same cadence: a bigger delta moves the settled sky further per frame and raises
         * the drift floor with it. `CarNightCrossfadeTest` compresses its own clock for the same
         * reason and records the same insensitivity.
         */
        const val FRAME_SECONDS = 1f / 15f

        /** Where this test's measurements are printed. `am instrument` does not forward stdout. */
        const val LOG_TAG = "CloudCoverCrossfade"

        /**
         * The two covers. Milan's own step on the day the defect was reproduced -- 0.87 at 12:06
         * and 0.42 an hour later -- so the test exercises the size of change the provider really
         * makes rather than an extreme chosen to be easy to catch.
         */
        const val COVER_FROM = 0.87f
        const val COVER_TO = 0.42f

        /**
         * The forecast reporting a clear sky, which is a state Milan itself reached later the same
         * afternoon (5 % at 16:00). It is the transition that ends with no cloud layer at all.
         */
        const val COVER_CLEAR = 0f

        /** Rows the cloud band can occupy at any sun-cloud height: `CloudBand.topFor` spans 0.06..0.31 and the band is 0.16 tall. */
        val BAND_TOP = (SceneGolden.HEIGHT * 0.05f).toInt()
        val BAND_BOTTOM = (SceneGolden.HEIGHT * 0.50f).toInt()

        /** Enough frames that the first-observation snap is well behind us. */
        const val WARM_UP_FRAMES = 15


        /**
         * Frames the transition is watched over.
         *
         * The widest step here is 0.87 -> 0, which at [CloudCoverFade.COVER_UNITS_PER_SECOND] takes
         * 17.4 s, plus one [CloudCoverFade.FADE_SECONDS] for the last cloud to leave. 22 s at
         * [FRAME_SECONDS] covers it with room, and is also long enough to let
         * [aSettledSkyIsUnchanged] compare two skies that have both arrived.
         */
        const val TRANSITION_FRAMES = 15 * 22

        /**
         * Frames of settled sky measured for the drift floor — **the same number the transition is
         * watched over**, and that is the point.
         *
         * A floor taken over 20 frames against a maximum taken over 330 is not a floor, it is a
         * shorter lottery: a rare event that a settled sky produces once every few hundred frames
         * lands in the second sample and not the first, and gets attributed to the change under
         * test. Measured here: at 20 frames the floor read 0 and the transition's own rare frames
         * looked like a defect.
         */
        const val SETTLED_FRAMES = TRANSITION_FRAMES

        /**
         * How far past ordinary drift a single frame of the transition may go.
         *
         * Three, not one: the transition legitimately changes more than drift alone, because a
         * fading cloud alters every pixel it covers rather than only its moving edge. What it may
         * not do is change a *cloud's worth* of band in one frame, which is an order of magnitude
         * above this.
         */
        const val CHURN_MULTIPLE = 3

        /**
         * How far a pixel must move to count as **a cloud arriving whole**, rather than as one
         * easing or as the sky being re-tinted. Three measurements set it, all on this device:
         *
         * | event | worst per-frame move of one pixel |
         * |---|---|
         * | the sky's 8-bit tint tipping over as the cover eases | **1 level** — and it moves *every*
         *   open-sky pixel at once: an exact comparison called that 76 736 px of a 129 600 px band |
         * | two or three clouds easing at once **over the sun**, the highest contrast in the band |
         *   **25 levels** (measured: `(241,172,87) -> (239,164,69)`, and one cloud alone is ~7) |
         * | a cloud arriving whole | **~100 levels** over sky, **~163** over the sun |
         *
         * 48 sits about twice above the second and three times below the third. The sun is what
         * makes this a real question: over plain sky a fading cloud moves a pixel ~4 levels a frame
         * and almost any threshold would do.
         */
        const val POP_LEVELS = 48

        /**
         * The absolute ceiling on one frame's churn, in band pixels — **derived from two
         * measurements on this device, both printed by this test.**
         *
         * | what | worst single-frame churn |
         * |---|---|
         * | as shipped (both eases off) | **4 940 px**, on the very first frame after the change |
         * | with both eases | **6 px** |
         * | one cloud alone, i.e. the ease removed one at a time | (see the mutation register) |
         *
         * Placed well above the second and well below the first. It is not a tolerance on the
         * scene: it is the size of the smallest event this test has to reject, which is one cloud
         * arriving whole.
         */
        const val ABSOLUTE_FLOOR = 200
    }
}
