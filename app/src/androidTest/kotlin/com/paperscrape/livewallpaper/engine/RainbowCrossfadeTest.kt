package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.weather.LiveWeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Live Weather may not take the rainbow away, or give it back, between two frames** (`DESIGN_NOTES.md` §12, decision
 * 110): it leaves and comes back over [CloudCoverFade.FADE_SECONDS], as a cloud does ([RainbowFade]).
 *
 * `CloudCoverCrossfadeTest`'s shape: the real [PaperRenderer] on the Canvas, driven through a change the user does not
 * make, every frame read back. Four changes, each way the forecast moves the rainbow: the forecast starting rain, the
 * forecast stopping it, the forecast leaving the scene with the rain still on (Live Weather off, its inputs changed, the
 * conditions aged out: the override goes back to none), and a forecast of rain arriving over the theme's own weather.
 *
 * The rain is a forecast of rain **at intensity 0**: the rule reads the kind ([LiveWeatherSceneRules.rainbowUnderForecast]
 * takes `precipitationType != null`), and at 0 nothing falls and no cloud is added to fall from, so the only thing in the
 * band that changes is the rainbow. The clouds and the birds are off and the scroll is pinned, so a settled band does not
 * move at all ([settledBandDoesNotMove] holds that, or the measure below would be measuring something else).
 *
 * What is measured is the most any pixel of the rainbow's band moves in one frame, against how far the rainbow moves it
 * in all: whole, the rainbow arrives or leaves in one frame and the two are equal; faded over 1.5 s at 15 frames a second,
 * no frame moves a pixel more than a tenth of it. And once the fade is over the band is the band of a scene that was
 * handed the same weather outright ([aSettledRainbowIsUnchanged]): the fade is a transient, which is what lets every
 * golden stand.
 */
class RainbowCrossfadeTest {

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
            GoldenScene(name = "rainbow-crossfade", dayPhase = GoldenScene.day()).configure(renderer)
            renderer.sceneCustomization = renderer.sceneCustomization.let {
                it.copy(
                    rainbow = it.rainbow.copy(visible = true, opacity = 1f),
                    clouds = it.clouds.copy(visible = false),
                    birds = it.birds.copy(visible = false),
                    precipitation = it.precipitation.copy(visible = false),
                )
            }
            target.bind(Canvas(bitmap))
        }

        /** No forecast drawn: the theme's own weather, and its rainbow as the switch says. */
        fun noForecast() {
            renderer.liveWeatherOverride = null
            renderer.liveWeatherLapsed = false
        }

        /** A clear forecast: the rainbow may show. */
        fun clear() {
            renderer.liveWeatherOverride = forecast(null)
        }

        /** A forecast of rain, at intensity 0: the rainbow may not show, and nothing falls. */
        fun rain() {
            renderer.liveWeatherOverride = forecast(PrecipitationType.RAIN)
        }

        private fun forecast(kind: PrecipitationType?) = LiveWeatherSnapshot(
            precipitationType = kind,
            precipitationIntensity = 0f,
            cloudCoverFraction = 0f,
            isThunderstorm = false,
            fetchedAtMillis = 0L,
        )

        fun frame() {
            clock += FRAME_SECONDS
            renderer.draw(target, GoldenScene.day(), clock, FRAME_SECONDS)
        }

        /** The rainbow's band above the hills, as ARGB ([BAND_TOP], [BAND_BOTTOM]). */
        fun band(): IntArray {
            val pixels = IntArray(SceneGolden.WIDTH * (BAND_BOTTOM - BAND_TOP))
            bitmap.getPixels(pixels, 0, SceneGolden.WIDTH, 0, BAND_TOP, SceneGolden.WIDTH, BAND_BOTTOM - BAND_TOP)
            return pixels
        }
    }

    /** The most any pixel moves on any channel between two readings, in levels of 255. */
    private fun largestMove(a: IntArray, b: IntArray): Int {
        var worst = 0
        for (i in a.indices) {
            val x = a[i]
            val y = b[i]
            if (x == y) continue
            val dr = kotlin.math.abs(((x shr 16) and 0xFF) - ((y shr 16) and 0xFF))
            val dg = kotlin.math.abs(((x shr 8) and 0xFF) - ((y shr 8) and 0xFF))
            val db = kotlin.math.abs((x and 0xFF) - (y and 0xFF))
            worst = maxOf(worst, dr, dg, db)
        }
        return worst
    }

    /**
     * Drives a sky from [before] to [after] and returns the largest single-frame move of the band during the change, and
     * the whole move from the band before to the band after.
     */
    private fun transition(before: (Sky) -> Unit, after: (Sky) -> Unit): Pair<Int, Int> {
        val sky = Sky()
        before(sky)
        repeat(WARM_UP_FRAMES) { sky.frame() }
        val start = sky.band()
        after(sky)
        var previous = start
        var worst = 0
        repeat(TRANSITION_FRAMES) {
            sky.frame()
            val now = sky.band()
            worst = maxOf(worst, largestMove(previous, now))
            previous = now
        }
        return worst to largestMove(start, previous)
    }

    @Test
    fun settledBandDoesNotMove() {
        for (state in listOf<(Sky) -> Unit>({ it.noForecast() }, { it.clear() }, { it.rain() })) {
            val sky = Sky()
            state(sky)
            repeat(WARM_UP_FRAMES) { sky.frame() }
            var previous = sky.band()
            repeat(TRANSITION_FRAMES) {
                sky.frame()
                val now = sky.band()
                assertEquals("a settled band moved: the measure would count something other than the rainbow", 0, largestMove(previous, now))
                previous = now
            }
        }
    }

    @Test
    fun noSingleFrameTakesOrGivesTheRainbow() {
        val changes = listOf<Triple<String, (Sky) -> Unit, (Sky) -> Unit>>(
            Triple("the forecast starts rain", { it.clear() }, { it.rain() }),
            Triple("the forecast stops rain", { it.rain() }, { it.clear() }),
            Triple("the forecast leaves the scene with the rain on", { it.rain() }, { it.noForecast() }),
            Triple("a forecast of rain arrives over the theme's weather", { it.noForecast() }, { it.rain() }),
        )
        for ((what, before, after) in changes) {
            val (worst, whole) = transition(before, after)
            android.util.Log.i(LOG_TAG, "RAINBOW-CROSSFADE $what: worst single-frame move $worst levels of a whole $whole")
            assertTrue("$what: the rainbow moved the band by only $whole levels, too little to measure a fade", whole >= MIN_WHOLE_LEVELS)
            assertTrue(
                "$what: one frame moved the rainbow's band by $worst levels of the $whole the whole change moves -- the " +
                    "rainbow is arriving or leaving in one frame again (RainbowFade)",
                worst * FRACTION_DENOMINATOR <= whole,
            )
        }
    }

    @Test
    fun aSettledRainbowIsUnchanged() {
        // Same number of frames, so the two clocks agree to the bit; the only difference is how each got to its weather.
        for ((end, start) in listOf<Pair<(Sky) -> Unit, (Sky) -> Unit>>({ s: Sky -> s.rain() } to { s: Sky -> s.clear() }, { s: Sky -> s.clear() } to { s: Sky -> s.rain() })) {
            val direct = Sky()
            end(direct)
            repeat(WARM_UP_FRAMES + TRANSITION_FRAMES) { direct.frame() }
            val eased = Sky()
            start(eased)
            repeat(WARM_UP_FRAMES) { eased.frame() }
            end(eased)
            repeat(TRANSITION_FRAMES) { eased.frame() }
            assertEquals("a rainbow that eased to its weather is not the one handed it outright", 0, largestMove(direct.band(), eased.band()))
        }
    }

    private companion object {
        /** 15 frames a second, as `CloudCoverFadeTest`'s: the fade is driven by `deltaSeconds`, so 22 frames make it. */
        const val FRAME_SECONDS = 1f / 15f
        const val LOG_TAG = "RainbowCrossfade"
        const val WARM_UP_FRAMES = 15
        /** Three seconds: the fade's 1.5 s twice over. */
        const val TRANSITION_FRAMES = 45

        /**
         * No frame may move a pixel by more than a tenth of what the whole change moves it. Faded over 22 frames a frame
         * moves it about a twenty-second; whole, one frame moves it all of it.
         */
        const val FRACTION_DENOMINATOR = 10

        /** The rainbow drawn at full opacity by day moves its band by far more than this; less, and nothing is measured. */
        const val MIN_WHOLE_LEVELS = 40

        /**
         * The rows of the arc above the hills: its outer radius is 0.62 of the width over the mountains' base
         * (`drawRainbow`), and the band stops at the hill layer's top. Below it the lake and the street move in a settled
         * scene (measured: rows 462 to 498 of 800), which [settledBandDoesNotMove] would catch.
         */
        val BAND_BOTTOM = (SceneGolden.HEIGHT * SceneSpace.HILL_LAYER_TOP_FRACTION).toInt()
        val BAND_TOP = maxOf(0, (SceneGolden.HEIGHT * SceneSpace.GROUND_SOLID_TOP_Y_FRACTION).toInt() - (SceneGolden.WIDTH * 0.66f).toInt())
    }
}
