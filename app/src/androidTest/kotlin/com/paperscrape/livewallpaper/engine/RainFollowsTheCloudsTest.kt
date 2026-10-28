package com.paperscrape.livewallpaper.engine

import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.R
import com.paperscrape.livewallpaper.weather.LiveWeatherSnapshot
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * **The rain follows the clouds as they come and go, never in a frame, and clouds come into a clear sky fading in.**
 *
 * Rain falls under the clouds drawn and everywhere with none ([CloudCoverage.rainField]). While the last clouds leave a
 * clearing sky -- the theme's cloud switch turned off, or a forecast's cover easing down to clear -- the rain spreads
 * from under them to the whole sky over their fade; while the first clouds come into a clear sky it narrows under
 * them as they fade in ([CloudCoverFade.rainEverywhereToward]). Frame by frame, the drops drawn never move in the change's
 * direction -- more while the rain spreads, fewer while it narrows -- by more than [MOST_OF_THE_CHANGE] of the whole change in
 * one frame. The other direction is not measured: while a forecast's cover eases down, the clouds leave one at a time, and
 * the rain under one goes with it in the frame its fade ends. And a cloud seen for the first time after the first frame fades
 * in ([CloudCoverFade.opacityOf]): its first blits are drawn below full alpha.
 *
 * Where the theme's switch turns its clouds off or on, the change also takes the clouds' own fade: from the frame the drops
 * have made a tenth of it to the frame they have made nine tenths, at least half of [CloudCoverFade.FADE_SECONDS] in frames
 * ([SPREAD_OVER_AT_LEAST]). The rain beyond the clouds eases linearly over the fade, so the drops make those eight tenths
 * over some 36 of its 45 frames; eased twice a frame they make them over 18, in five frames over 4. Not where a forecast
 * clears: there the ease starts two seconds after the change, and the clouds whose fade ends inside it take their rain
 * with them, so where the tenths fall depends on which cloud leaves when.
 *
 * The theme's clouds stand at 10 %, where the rain under them is a fraction of the rain everywhere.
 */
@RunWith(AndroidJUnit4::class)
class RainFollowsTheCloudsTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val noon = SunPositionCalculator.compute(hour24 = 12f)

    @Test
    fun theRainSpreadsToTheWholeSkyAsTheThemesCloudsGo() {
        val r = renderer(themeClouds = true)
        val counts = framesOf(r, before = 3f, after = 5f) { r.sceneCustomization = r.sceneCustomization.withClouds(false) }
        assertGradual("the theme's clouds turned off", counts, spreads = true)
        assertOverTheFade("the theme's clouds turned off", counts, spreads = true)
    }

    @Test
    fun theRainNarrowsUnderTheThemesCloudsAsTheyCome() {
        val r = renderer(themeClouds = false)
        val counts = framesOf(r, before = 3f, after = 5f) { r.sceneCustomization = r.sceneCustomization.withClouds(true) }
        assertGradual("the theme's clouds turned on", counts, spreads = false)
        assertOverTheFade("the theme's clouds turned on", counts, spreads = false)
    }

    @Test
    fun theRainSpreadsToTheWholeSkyAsTheForecastClears() {
        val r = renderer(themeClouds = false)
        r.liveWeatherOverride = forecast(cover = 0.1f)
        // The forecast's cover eases to 0 at `CloudCoverFade.COVER_UNITS_PER_SECOND`, two seconds from 0.1, then the last
        // clouds fade out.
        val counts = framesOf(r, before = 25f, after = 8f) { r.liveWeatherOverride = forecast(cover = 0f) }
        assertGradual("the forecast's cover going to clear", counts, spreads = true)
    }

    @Test
    fun cloudsComingIntoAClearSkyFadeIn() {
        // The theme's switch turned on, and a clear forecast turning cloudy.
        for (fromForecast in listOf(false, true)) {
            val r = renderer(themeClouds = false)
            if (fromForecast) r.liveWeatherOverride = forecast(cover = 0f)
            val recorder = Recorder()
            var clock = SceneTime(60.0)
            repeat(30) {
                clock += FRAME
                r.draw(recorder, noon, clock, FRAME)
            }
            if (fromForecast) r.liveWeatherOverride = forecast(cover = 0.4f) else r.sceneCustomization = r.sceneCustomization.withClouds(true)
            val firstAlphas = ArrayList<Int>()
            var frames = 0
            while (firstAlphas.isEmpty() && frames < 30 * 10) {
                clock += FRAME
                recorder.cloudAlphas.clear()
                r.draw(recorder, noon, clock, FRAME)
                firstAlphas += recorder.cloudAlphas
                frames++
            }
            val how = if (fromForecast) "a clear forecast turning cloudy" else "the theme's clouds turned on"
            assertTrue("$how: no cloud drawn in $frames frames", firstAlphas.isNotEmpty())
            assertTrue("$how: the first clouds drawn at alpha $firstAlphas", firstAlphas.all { it < FIRST_FRAME_ALPHA_AT_MOST })
            repeat((CloudCoverFade.FADE_SECONDS / FRAME).toInt() + 2) {
                clock += FRAME
                r.draw(recorder, noon, clock, FRAME)
            }
            recorder.cloudAlphas.clear()
            clock += FRAME
            r.draw(recorder, noon, clock, FRAME)
            assertTrue("$how: a cloud still faint after its fade, ${recorder.cloudAlphas}", recorder.cloudAlphas.contains(255))
        }
    }

    /**
     * The drops drawn on each frame: [before] seconds settled, then [change], then [after] seconds. The first element is
     * the last settled frame's.
     */
    private fun framesOf(r: PaperRenderer, before: Float, after: Float, change: () -> Unit): IntArray {
        val recorder = Recorder()
        var clock = SceneTime(60.0)
        repeat((before / FRAME).toInt()) {
            clock += FRAME
            r.draw(recorder, noon, clock, FRAME)
        }
        val frames = (after / FRAME).toInt()
        val counts = IntArray(frames + 1)
        recorder.drops = 0
        clock += FRAME
        r.draw(recorder, noon, clock, FRAME)
        counts[0] = recorder.drops
        change()
        for (f in 1..frames) {
            clock += FRAME
            recorder.drops = 0
            r.draw(recorder, noon, clock, FRAME)
            counts[f] = recorder.drops
        }
        return counts
    }

    private fun assertGradual(what: String, counts: IntArray, spreads: Boolean) {
        val from = counts.first()
        val to = counts.last()
        val change = to - from
        assertTrue(
            "$what: the rain went from $from to $to drops, the clouds did not ${if (spreads) "narrow" else "widen"} it",
            if (spreads) to > from * 3 / 2 else from > to * 3 / 2,
        )
        var worst = 0
        var at = 0
        for (f in 1 until counts.size) {
            // A step in the change's own direction: more drops while it spreads, fewer while it narrows.
            val step = (counts[f] - counts[f - 1]) * (if (spreads) 1 else -1)
            if (step > worst) {
                worst = step
                at = f
            }
        }
        assertTrue(
            "$what: $worst drops in one frame (frame $at) of a change of $change, from $from to $to",
            worst <= abs(change) * MOST_OF_THE_CHANGE,
        )
    }

    /**
     * The frames from the first whose drops have made a tenth of the change, in its direction, to the first that has made nine
     * tenths: at least [SPREAD_OVER_AT_LEAST] of the clouds' fade.
     */
    private fun assertOverTheFade(what: String, counts: IntArray, spreads: Boolean) {
        val from = counts.first()
        val change = abs(counts.last() - from)
        val made = IntArray(counts.size) { (counts[it] - from) * (if (spreads) 1 else -1) }
        val tenth = (1 until counts.size).first { made[it] * 10 >= change }
        val nineTenths = (1 until counts.size).first { made[it] * 10 >= change * 9 }
        val fadeFrames = CloudCoverFade.FADE_SECONDS / FRAME
        assertTrue(
            "$what: the change of $change drops went from a tenth to nine tenths of it in ${nineTenths - tenth} frames " +
                "(frames $tenth to $nineTenths), under ${SPREAD_OVER_AT_LEAST * fadeFrames} of the clouds' $fadeFrames-frame fade",
            nineTenths - tenth >= SPREAD_OVER_AT_LEAST * fadeFrames,
        )
    }

    private fun forecast(cover: Float) = LiveWeatherSnapshot(
        precipitationType = PrecipitationType.RAIN,
        precipitationIntensity = 0.5f,
        cloudCoverFraction = cover,
        isThunderstorm = false,
        fetchedAtMillis = 0L,
    )

    private fun SceneCustomization.withClouds(visible: Boolean) = copy(clouds = clouds.copy(visible = visible))

    /** Sunset with its rain at half, its cloud layer off or on at 10 %; no people, no lake: nothing else the rain changes. */
    private fun renderer(themeClouds: Boolean): PaperRenderer {
        val defaults = defaultCustomizationFor("sunset")
        return PaperRenderer(720, 1440, context).apply {
            theme = ThemeCatalog.byId("sunset")
            sceneCustomization = defaults.copy(
                clouds = defaults.clouds.copy(visible = themeClouds, density = 0.1f),
                people = defaults.people.copy(visible = false),
                lake = defaults.lake.copy(visible = false),
                precipitation = defaults.precipitation.copy(visible = true, type = PrecipitationType.RAIN, intensity = 0.5f),
            )
            lightningStrikesEnabled = false
            scrollSpeed = 0f
        }
    }

    /** Counts the drops of rain (the lines that run a quarter of their fall to the left) and keeps the clouds' alphas. */
    private class Recorder : SceneCanvas {
        var drops = 0
        val cloudAlphas = ArrayList<Int>()
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
            val fall = stopY - startY
            if (fall > 0f && abs((stopX - startX) + 0.25f * fall) <= 0.01f) drops++
        }
        override fun save() = Unit
        override fun restore() = Unit
        override fun translate(dx: Float, dy: Float) = Unit
        override fun scale(sx: Float, sy: Float) = Unit
        override fun rotate(degrees: Float) = Unit
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) = Unit
        override fun drawDisc(resId: Int, source: SpriteSource, cx: Float, cy: Float, radius: Float, colour: Int, alpha: Int) = Unit
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
        override fun drawSprite(
            resId: Int,
            source: SpriteSource,
            left: Float,
            top: Float,
            tintColor: Int,
            alpha: Int,
            additive: Boolean,
        ) {
            if (resId == R.drawable.cloud_body) cloudAlphas += alpha
        }
    }

    private companion object {
        const val FRAME = 1f / 30f

        /** A quarter: over the clouds' 1.5 s fade the change is spread over some 45 frames. */
        const val MOST_OF_THE_CHANGE = 0.25f

        /** Half the clouds' fade: the drops' eight middle tenths take some 36 of its 45 frames, eased twice as fast 18. */
        const val SPREAD_OVER_AT_LEAST = 0.5f

        /** A frame's worth of a 1.5 s fade, and some: a cloud that snaps in is drawn at 255. */
        const val FIRST_FRAME_ALPHA_AT_MOST = 64
    }
}
