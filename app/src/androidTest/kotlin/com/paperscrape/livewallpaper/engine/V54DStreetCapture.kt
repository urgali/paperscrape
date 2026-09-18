package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Base64
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.weather.LiveWeatherSnapshot
import org.junit.Test

/**
 * **Capture only — renders the four frames v5.4 lavoro D asks the maintainer to look at, and
 * asserts nothing.**
 *
 * Two questions, two pairs, and each pair is *the same scene rendered by two builds*:
 *
 *  - **the street** (`shop-sunset`, `shop-desert`). Correcting the two shops' declaration moves
 *    nine shops on eight themes, `sunset`'s restaurant by 0.18 of a tile. These are the theme that
 *    moves furthest and one of the two whose front was over the 40 % criterion on ink.
 *  - **the rain** (`rain-spring`, `rain-autumn`), warmed up through 55 s of scene clock so the
 *    off-screen rule has let the umbrellas up. `spring` is the theme that had none at all.
 *
 * A golden cannot answer either: a golden compares one build against a committed file, and what
 * has to be looked at here is one build against *another build*. So this is a camera, not a gate —
 * it is committed for the reason [V428TundraCapture] is, that evidence whose recipe was deleted is
 * a picture taken on trust. The recipe: install the build, run this class, read the frames off
 * logcat under the `V54DCAP` tag (the base64 chunking `SceneGolden.emitToLogcat` uses, because the
 * app's own output directory is not retrievable on every Android version).
 */
class V54DStreetCapture {

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
            .let { it.copy(people = it.people.copy(visible = true)) }
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

    /** The two shop cases: the one that moves furthest, and the one that was over the ceiling. */
    @Test
    fun shops() {
        capture("shop-sunset", "sunset", rain = false, warmUpSteps = 1)
        capture("shop-desert", "desert", rain = false, warmUpSteps = 1)
    }

    /** The rainy street, warmed up past the off-screen rule so the umbrellas can be up. */
    @Test
    fun rainyStreets() {
        capture("rain-spring", "spring", rain = true, warmUpSteps = 110)
        capture("rain-autumn", "autumn", rain = true, warmUpSteps = 110)
    }

    private companion object {
        const val TAG = "V54DCAP"
    }
}
