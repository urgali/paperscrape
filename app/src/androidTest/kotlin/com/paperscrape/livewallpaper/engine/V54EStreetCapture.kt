package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Base64
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.weather.LiveWeatherSnapshot
import org.junit.Test

/**
 * **Capture only — renders the rainy streets v5.4 lavoro E asks the maintainer to look at, and
 * asserts nothing.**
 *
 * The question is the one the share existed to avoid and that the maintainer has now overruled:
 * **what does a street look like when every walker who can hold an umbrella is holding one.** A
 * golden cannot answer it — a golden compares one build against a committed file, and what has to
 * be looked at here is one build against *another build*, the same scene before and after the rule
 * changed.
 *
 * Three streets, chosen rather than convenient:
 *
 *  - `city` — **the busiest of the twelve**, five adults among eight figures. If the rule reads as
 *    a uniform anywhere, it reads as one here, so this is the frame the decision should be made on;
 *  - `winter` — five adults among nine, and the theme in which the *rolled* share of v5.3 already
 *    put an umbrella in every adult hand. It is the closest thing to a before-and-after control:
 *    what changes here is only the children being visibly left out;
 *  - `spring` — two adults among seven, the smallest street and the one the previous round
 *    photographed as the defect. It is also the worst case for the artwork gap: five of its seven
 *    figures stay bare-headed whatever the rule says.
 *
 * Warmed up through 55 s of scene clock so [PedestrianCarry.nextCarrying]'s off-screen rule has let
 * the umbrellas up; at scene time zero nobody carries anything however hard it is raining.
 *
 * Committed for the reason [V428TundraCapture] is: evidence whose recipe was deleted is a picture
 * taken on trust. The recipe: install the build, run this class, read the frames off logcat under
 * the `V54ECAP` tag (the base64 chunking `SceneGolden.emitToLogcat` uses).
 */
class V54EStreetCapture {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun emit(name: String, bitmap: Bitmap) {
        val bytes = java.io.ByteArrayOutputStream()
            .also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            .toByteArray()
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val chunk = 3000
        val chunks = (encoded.length + chunk - 1) / chunk
        Log.i(TAG, "BEGIN $name ${bytes.size} $chunks")
        for (i in 0 until chunks) {
            Log.i(TAG, "$name $i ${encoded.substring(i * chunk, minOf((i + 1) * chunk, encoded.length))}")
        }
        Log.i(TAG, "END $name")
    }

    private fun capture(
        name: String,
        themeId: String,
        rain: Boolean,
        warmUpSteps: Int,
        width: Int = 720,
        height: Int = 1600,
    ) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val target = CanvasSceneTarget()
        val renderer = PaperRenderer(width, height, context)
        renderer.theme = ThemeCatalog.byId(themeId)
        renderer.sceneCustomization = defaultCustomizationFor(themeId)
            .let { it.copy(people = it.people.copy(visible = true, density = 1f)) }
        renderer.liveWeatherOverride = if (rain) {
            LiveWeatherSnapshot(PrecipitationType.RAIN, 0.6f, 1f, false, 0L)
        } else {
            null
        }
        renderer.homeScreenOffset = 0f
        renderer.swipeScrollEnabled = false
        renderer.scrollSpeed = 0f
        renderer.parallaxStrength = 1f
        renderer.lightningStrikesEnabled = false
        val phase = GoldenScene.day()
        var seconds = 120.0
        repeat(warmUpSteps) {
            target.bind(Canvas(bitmap))
            renderer.draw(target, phase, SceneTime(seconds), 0.5f)
            target.unbind()
            seconds += 0.5
        }
        emit(name, bitmap)
    }

    /** The three rainy streets, warmed up past the off-screen rule so the umbrellas can be up. */
    @Test
    fun rainyStreets() {
        capture("e-rain-city", "city", rain = true, warmUpSteps = 110)
        capture("e-rain-winter", "winter", rain = true, warmUpSteps = 110)
        capture("e-rain-spring", "spring", rain = true, warmUpSteps = 110)
    }

    /** The same three streets dry, which is what must not have moved. */
    @Test
    fun dryStreets() {
        capture("e-dry-city", "city", rain = false, warmUpSteps = 110)
        capture("e-dry-winter", "winter", rain = false, warmUpSteps = 110)
        capture("e-dry-spring", "spring", rain = false, warmUpSteps = 110)
    }

    private companion object {
        const val TAG = "V54ECAP"
    }
}
