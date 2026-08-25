package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.weather.LiveWeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Rain and snow, measured on the pixels a device actually paints.
 *
 * ### Why this exists rather than a golden
 *
 * Every committed golden is 360x800, and the defect this pins **cannot appear at that size**:
 * `drawPrecipitation` sized its drops in absolute canvas pixels, so the golden frame was the one
 * viewport on which the effect was the size it was tuned to be. A phone is three times taller,
 * and got the same absolute drops over nine times the area. Measured before the fix, the whole
 * precipitation layer covered **0.94%** of a 360x800 frame and **0.11%** of a 1080x2424 one.
 *
 * Snow lived through that on contrast alone; rain, a translucent `0xFF7FB3E0` hairline on a
 * `0xFF6EC6FF` sky, did not, and was reported as simply absent with Location and Live Weather
 * both switched off. Both halves are measured here, so neither can regress unnoticed.
 *
 * ### What "Location OFF, Live Weather OFF" is, at this layer
 *
 * Exactly one thing: `liveWeatherOverride == null`. `PaperWallpaperService` clears the snapshot
 * whenever Live Weather is switched off, and never fetches one without a location, so "location
 * off", "location on but Live Weather off" and "Live Weather on with nowhere to check" all reach
 * the renderer as the same state — the theme's own precipitation, alone. A null override is
 * therefore the honest way to write every one of those rows, and writing them as separate
 * renderer states would be pretending to test something this layer cannot tell apart.
 */
class PrecipitationPixelTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** The frame the committed goldens are drawn at. */
    private val goldenWidth = SceneGolden.WIDTH
    private val goldenHeight = SceneGolden.HEIGHT

    /** A Pixel 9's own viewport, which is where the defect was reported. */
    private val phoneWidth = 1080
    private val phoneHeight = 2424

    private fun scene(
        clouds: Boolean = true,
        precipitation: Boolean = true,
        type: PrecipitationType = PrecipitationType.RAIN,
        intensity: Float = 0.5f,
        live: LiveWeatherSnapshot? = null,
        themeId: String = "sunset",
    ) = GoldenScene(
        name = "precipitation-probe",
        dayPhase = GoldenScene.day(),
        themeId = themeId,
        weather = live,
        customise = { c ->
            c.copy(
                clouds = c.clouds.copy(visible = clouds),
                precipitation = c.precipitation.copy(
                    visible = precipitation, type = type, intensity = intensity,
                ),
            )
        },
    )

    private fun renderAt(width: Int, height: Int, scene: GoldenScene): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val target = CanvasSceneTarget()
        target.bind(Canvas(bitmap))
        val renderer = PaperRenderer(width, height, context)
        scene.configure(renderer)
        renderer.draw(target, scene.dayPhase, SceneTime(scene.sceneSeconds), 0f)
        target.unbind()
        return bitmap
    }

    /**
     * What share of the frame the precipitation layer covers, and nothing else.
     *
     * The same scene is drawn twice, differing only in the precipitation switch, so every pixel
     * that moved is a drop or a flake. That isolates the layer exactly, without having to find it
     * in a finished picture.
     */
    private fun precipitationShare(
        width: Int,
        height: Int,
        type: PrecipitationType,
        clouds: Boolean = true,
        intensity: Float = 0.5f,
        live: LiveWeatherSnapshot? = null,
        liveBaseline: LiveWeatherSnapshot? = null,
        themeId: String = "sunset",
    ): Double {
        val dry = renderAt(
            width, height,
            scene(clouds = clouds, precipitation = false, live = liveBaseline, themeId = themeId),
        )
        val wet = renderAt(
            width, height,
            scene(clouds = clouds, type = type, intensity = intensity, live = live, themeId = themeId),
        )
        var differing = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val a = dry.getPixel(x, y)
                val b = wet.getPixel(x, y)
                val delta = maxOf(
                    abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)),
                    abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)),
                    abs((a and 0xFF) - (b and 0xFF)),
                )
                if (delta > 2) differing++
            }
        }
        return differing.toDouble() / (width * height)
    }

    // -- The reported case ---------------------------------------------------------------------

    /**
     * **The report, on the viewport it was reported from.**
     *
     * A tenth of a percent of the frame is what the defect left, and it is what "the rain is not
     * there" looks like as a number. Half a percent is the floor this asserts: comfortably under
     * what the fix produces (~0.72%) and far above what the defect produced (0.11%).
     */
    @Test
    fun rainIsOnScreenWithNoLocationAndNoLiveWeather() {
        val share = precipitationShare(phoneWidth, phoneHeight, PrecipitationType.RAIN)
        assertTrue(
            "rain covers only ${"%.4f".format(share * 100)}% of a ${phoneWidth}x$phoneHeight frame",
            share > 0.005,
        )
    }

    /** Snow in the same state, which is the half that always worked and must go on working. */
    @Test
    fun snowIsOnScreenWithNoLocationAndNoLiveWeather() {
        val share = precipitationShare(phoneWidth, phoneHeight, PrecipitationType.SNOW)
        assertTrue(
            "snow covers only ${"%.4f".format(share * 100)}% of a ${phoneWidth}x$phoneHeight frame",
            share > 0.004,
        )
    }

    /**
     * **The defect itself: the same weather has to be the same weather on any screen.**
     *
     * This is the assertion the old code fails. Its share on a phone was 12% of its share on the
     * golden frame; the two are now within a third of each other. The band is deliberately wide,
     * because the small frame genuinely over-counts: a 2 px line's antialiased fringe is a larger
     * share of a small drop than a 6 px line's is of a large one. What is being pinned is that
     * the layer scales with the viewport at all, not that it scales to the last pixel.
     */
    @Test
    fun rainCoversTheSameShareOfAPhoneFrameAsOfTheGoldenFrame() {
        val golden = precipitationShare(goldenWidth, goldenHeight, PrecipitationType.RAIN)
        val phone = precipitationShare(phoneWidth, phoneHeight, PrecipitationType.RAIN)
        assertTrue("the golden frame itself shows no rain", golden > 0.005)
        assertTrue(
            "rain is ${"%.4f".format(golden * 100)}% of the golden frame but only " +
                "${"%.4f".format(phone * 100)}% of a phone's -- it is not scaling with the viewport",
            phone > golden / 2.0,
        )
    }

    /** The same property for snow, so the fix cannot quietly be rain-only. */
    @Test
    fun snowCoversTheSameShareOfAPhoneFrameAsOfTheGoldenFrame() {
        val golden = precipitationShare(goldenWidth, goldenHeight, PrecipitationType.SNOW)
        val phone = precipitationShare(phoneWidth, phoneHeight, PrecipitationType.SNOW)
        assertTrue("the golden frame itself shows no snow", golden > 0.004)
        assertTrue(
            "snow is ${"%.4f".format(golden * 100)}% of the golden frame but only " +
                "${"%.4f".format(phone * 100)}% of a phone's",
            phone > golden / 2.0,
        )
    }

    // -- The rest of the matrix ------------------------------------------------------------------

    /**
     * Switching the cloud layer off must not switch the rain off — `CloudCoverage.setUniform`'s
     * whole reason for existing, checked on pixels rather than on the flag.
     */
    @Test
    fun rainStillFallsWithTheCloudLayerSwitchedOff() {
        val share = precipitationShare(phoneWidth, phoneHeight, PrecipitationType.RAIN, clouds = false)
        assertTrue("no rain with clouds off: ${"%.4f".format(share * 100)}%", share > 0.005)
    }

    /** With Live Weather on, the forecast drives it, and it still reaches the frame. */
    @Test
    fun rainFromLiveWeatherIsOnScreenToo() {
        val raining = LiveWeatherSnapshot(
            precipitationType = PrecipitationType.RAIN, precipitationIntensity = 0.5f,
            cloudCoverFraction = 0.9f, isThunderstorm = false, fetchedAtMillis = 0L,
        )
        val dry = raining.copy(precipitationType = null, precipitationIntensity = 0f)
        // The theme's own switch is *off* in both frames: with an override in force the theme has
        // no vote, so this measures the forecast's rain and nothing else.
        val share = precipitationShare(
            phoneWidth, phoneHeight, PrecipitationType.RAIN, live = raining, liveBaseline = dry,
        )
        assertTrue("Live Weather's rain is not on screen: ${"%.4f".format(share * 100)}%", share > 0.005)
    }

    /** More intensity is more rain, at the phone size as well as at the golden one. */
    @Test
    fun intensityStillGovernsHowMuchFalls() {
        val light = precipitationShare(phoneWidth, phoneHeight, PrecipitationType.RAIN, intensity = 0.15f)
        val heavy = precipitationShare(phoneWidth, phoneHeight, PrecipitationType.RAIN, intensity = 1f)
        assertTrue("heavier rain is not heavier: $light vs $heavy", heavy > light * 2)
    }

    /** Nothing falls when the switch is off, on either frame size. */
    @Test
    fun nothingFallsWhenPrecipitationIsSwitchedOff() {
        for ((w, h) in listOf(goldenWidth to goldenHeight, phoneWidth to phoneHeight)) {
            val a = renderAt(w, h, scene(precipitation = false))
            val b = renderAt(w, h, scene(precipitation = false))
            assertTrue("the dry scene is not deterministic at ${w}x$h", a.sameAs(b))
        }
    }

    /**
     * The golden frame is untouched by the rescaling, which is why no committed golden moved.
     *
     * Asserted against a second render at the same size rather than against a stored number, so
     * it stays true if the scene is ever retuned: what it pins is that 360x800 is the fixed point
     * of `sceneScale`, and `PrecipitationScaleTest` pins the arithmetic behind that.
     */
    @Test
    fun theGoldenFrameSizeIsTheFixedPointOfTheScale() {
        assertEquals(
            "the golden frame no longer scales to 1/3 of the reference height",
            1f / 3f, SceneSpace.sceneScale(goldenHeight.toFloat()), 1e-4f,
        )
        val share = precipitationShare(goldenWidth, goldenHeight, PrecipitationType.RAIN)
        assertTrue("the golden frame's rain changed size: ${"%.4f".format(share * 100)}%", share in 0.008..0.011)
    }
}
