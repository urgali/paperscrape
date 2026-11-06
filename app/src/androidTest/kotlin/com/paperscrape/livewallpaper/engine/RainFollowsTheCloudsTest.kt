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
 * **The rain falls only from the clouds drawn, comes and goes with them, and clouds come into a clear sky fading in.**
 *
 * There is no rain beyond the clouds (`DESIGN_NOTES.md` §12, decision 98): with the cloud layer off or at 0 % nothing
 * falls, and the rain under a cloud comes and goes with the cloud's presence ([CloudCoverage.rainField]). So the theme's
 * clouds turned off take their rain with them: it lasts their [CloudCoverFade.FADE_SECONDS] fade, never spreading past
 * them, and stops in the frame the last of them goes ([LiveWeatherSceneRules.cloudlessSky]). Turned on over a clear sky,
 * the rain comes with the first cloud drawn. A forecast of rain under a clear sky is drawn with
 * [LiveWeatherSceneRules.FALLING_MIN_COVER] of clouds, so it rains from them and never stops for a clear sky. And a cloud
 * seen for the first time after the first frame fades in ([CloudCoverFade.opacityOf]): its first blits are drawn below
 * full alpha.
 *
 * The theme's clouds stand at 10 %.
 */
@RunWith(AndroidJUnit4::class)
class RainFollowsTheCloudsTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val noon = SunPositionCalculator.compute(hour24 = 12f)

    @Test
    fun theRainStopsWithTheLastOfTheThemesClouds() {
        val r = renderer(themeClouds = true)
        val frames = framesOf(r, before = 3f, after = 5f) { r.sceneCustomization = r.sceneCustomization.withClouds(false) }
        val before = frames.first()
        assertTrue("no rain under the theme's clouds before the change: $before", before.drops > 0 && before.clouds > 0)
        val stop = (1 until frames.size).firstOrNull { frames[it].drops == 0 }
        assertTrue("the rain never stopped after the theme's clouds were turned off: ${frames.map { it.drops }}", stop != null)
        stop!!
        val fadeFrames = CloudCoverFade.FADE_SECONDS / FRAME
        assertTrue(
            "the rain stopped at frame $stop, the clouds' fade is $fadeFrames frames: ${frames.map { it.drops }}",
            stop >= fadeFrames - 2 && stop <= fadeFrames + 3,
        )
        for (f in stop until frames.size) {
            assertTrue("frame $f after the rain stopped: ${frames[f]}", frames[f].drops == 0 && frames[f].clouds == 0)
        }
        for (f in 1 until stop) {
            assertTrue("frame $f: a cloud drawn, ${frames[f]}", frames[f].clouds > 0)
            assertTrue(
                "frame $f: ${frames[f].drops} drops, the rain spread past the clouds (${before.drops} before the change)",
                frames[f].drops * 2 <= before.drops * 3,
            )
        }
    }

    @Test
    fun theRainComesWithTheThemesClouds() {
        val r = renderer(themeClouds = false)
        val frames = framesOf(r, before = 3f, after = 5f) { r.sceneCustomization = r.sceneCustomization.withClouds(true) }
        assertTrue("rain under a sky with no clouds: ${frames.first()}", frames.first().drops == 0)
        val firstRain = (1 until frames.size).firstOrNull { frames[it].drops > 0 }
        assertTrue("no rain after the theme's clouds were turned on: ${frames.map { it.drops }}", firstRain != null)
        val firstCloud = (1 until frames.size).first { frames[it].clouds > 0 }
        assertTrue("the rain began at frame $firstRain, the first cloud at frame $firstCloud", firstRain == firstCloud)
        assertTrue("no rain at the end: ${frames.last()}", frames.last().drops > 0)
    }

    @Test
    fun aForecastOfRainUnderAClearSkyRainsFromAFewClouds() {
        val r = renderer(themeClouds = false)
        r.liveWeatherOverride = forecast(cover = 0.4f)
        // The forecast's cover eases at `CloudCoverFade.COVER_UNITS_PER_SECOND`: from 0.4 down to the floor in four seconds.
        val frames = framesOf(r, before = 25f, after = 10f) { r.liveWeatherOverride = forecast(cover = 0f) }
        for (f in frames.indices) {
            assertTrue("frame $f of a forecast of rain clearing: ${frames[f]}", frames[f].drops > 0 && frames[f].clouds > 0)
        }
        assertTrue(
            "the cover did not come down to the floor: ${frames.first()} to ${frames.last()}",
            frames.last().drops < frames.first().drops,
        )
    }

    @Test
    fun cloudsComingIntoAClearSkyFadeIn() {
        // The theme's switch turned on, and a clear forecast turning cloudy.
        for (fromForecast in listOf(false, true)) {
            val r = renderer(themeClouds = false)
            if (fromForecast) r.liveWeatherOverride = forecast(cover = 0f, falling = false)
            val recorder = Recorder()
            var clock = SceneTime(60.0)
            repeat(30) {
                clock += FRAME
                r.draw(recorder, noon, clock, FRAME)
            }
            if (fromForecast) r.liveWeatherOverride = forecast(cover = 0.4f, falling = false) else r.sceneCustomization = r.sceneCustomization.withClouds(true)
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

    /** What one frame drew: its drops of rain and its clouds. */
    private data class Frame(val drops: Int, val clouds: Int)

    /**
     * Each frame's drops and clouds: [before] seconds settled, then [change], then [after] seconds. The first element is the
     * last settled frame's.
     */
    private fun framesOf(r: PaperRenderer, before: Float, after: Float, change: () -> Unit): List<Frame> {
        val recorder = Recorder()
        var clock = SceneTime(60.0)
        repeat((before / FRAME).toInt()) {
            clock += FRAME
            r.draw(recorder, noon, clock, FRAME)
        }
        val frames = ArrayList<Frame>()
        fun drawOne() {
            clock += FRAME
            recorder.drops = 0
            recorder.cloudAlphas.clear()
            r.draw(recorder, noon, clock, FRAME)
            frames += Frame(recorder.drops, recorder.cloudAlphas.size)
        }
        drawOne()
        change()
        repeat((after / FRAME).toInt()) { drawOne() }
        return frames
    }

    private fun forecast(cover: Float, falling: Boolean = true) = LiveWeatherSnapshot(
        precipitationType = if (falling) PrecipitationType.RAIN else null,
        precipitationIntensity = if (falling) 0.5f else 0f,
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

        /** A frame's worth of a 1.5 s fade, and some: a cloud that snaps in is drawn at 255. */
        const val FIRST_FRAME_ALPHA_AT_MOST = 64
    }
}
