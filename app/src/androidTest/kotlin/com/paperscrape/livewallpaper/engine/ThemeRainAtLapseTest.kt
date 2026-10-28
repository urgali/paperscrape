package com.paperscrape.livewallpaper.engine

import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.weather.LiveWeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * **When the forecast lapses, the theme's rain is the theme's from that frame**, whatever the forecast's clouds still do.
 *
 * The rain and the storm change in a frame when a forecast lapses, as on every change of the forecast, while the
 * forecast's clouds fade out over up to twenty seconds. Gated by those clouds, the theme's rain would shrink with them
 * under a sky going clear and come back everywhere in a frame when the last had gone. Here a renderer that had a cloudy
 * forecast lapses, and frame by frame for 25 seconds it draws the same drops as a renderer of the same theme that never
 * had one: with the theme's cloud layer off (the rain everywhere) and on at 10 % (the rain under the theme's own
 * clouds). The density is low on purpose: at 40 % the theme's clouds already cover where the forecast's fading ones are,
 * and rain under either falls alike.
 */
@RunWith(AndroidJUnit4::class)
class ThemeRainAtLapseTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theThemesRainFallsAsTheThemeDrawsItFromTheFrameTheForecastLapses() {
        for (themeClouds in listOf(false, true)) {
            val lapsing = renderer(themeClouds)
            val theme = renderer(themeClouds)
            val noon = SunPositionCalculator.compute(hour24 = 12f)
            val drops = RainCounter()
            var clock = SceneTime(60.0)
            // A cloudy, rainy forecast, drawn long enough for its clouds to have come in whole (they ease over up to
            // twenty seconds): so after the lapse the clouds drawn are the forecast's fading out, and rain gated by them
            // would differ from the theme's.
            lapsing.liveWeatherOverride = LiveWeatherSnapshot(
                precipitationType = PrecipitationType.RAIN,
                precipitationIntensity = 1f,
                cloudCoverFraction = 0.8f,
                isThunderstorm = false,
                fetchedAtMillis = 0L,
            )
            repeat(25 * 30) {
                clock += FRAME
                lapsing.draw(drops, noon, clock, FRAME)
                theme.draw(drops, noon, clock, FRAME)
            }
            // It lapses: no forecast, and the one there was has aged out.
            lapsing.liveWeatherOverride = null
            lapsing.liveWeatherLapsed = true
            var seen = 0
            repeat(25 * 30) { frame ->
                clock += FRAME
                drops.count = 0
                lapsing.draw(drops, noon, clock, FRAME)
                val afterLapse = drops.count
                drops.count = 0
                theme.draw(drops, noon, clock, FRAME)
                val themeAlone = drops.count
                assertEquals(
                    "theme clouds ${if (themeClouds) "on" else "off"}, frame $frame after the lapse: drops",
                    themeAlone, afterLapse,
                )
                seen = maxOf(seen, themeAlone)
            }
            assertTrue("the theme's rain drew ${seen} drops at most: the comparison above is about nothing", seen > 20)
        }
    }

    /** Sunset with its rain at half, its cloud layer off or at 10 %; no people, no lake: nothing else the rain changes. */
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

    /** Counts the drops of rain: the lines that run a quarter of their fall to the left. */
    private class RainCounter : SceneCanvas {
        var count = 0
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
            val fall = stopY - startY
            if (fall > 0f && abs((stopX - startX) + 0.25f * fall) <= 0.01f) count++
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
        ) = Unit
    }

    private companion object {
        const val FRAME = 1f / 30f
    }
}
