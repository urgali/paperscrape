package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.weather.LiveWeatherSnapshot
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **Capture only — writes frames for the v4.26 report and asserts nothing.**
 *
 * Committed for the same reason `VehicleOccupantAbCapture` is: the images in a release report are
 * evidence, and evidence whose recipe was deleted is a picture somebody has to take on trust. These
 * four frames are the cases v4.26's two derivations were made for, and no committed golden covers
 * three of them:
 *
 * - **`cap-beach-night` and `cap-beach-storm`** — the animal on a night sea and in a storm, which is
 *   the case `LakeContrastTest`'s dolphin gate exists for. The v4.25 artwork measured CIELab dE 1.53
 *   from the water at its worst and was not visible in either.
 * - **`cap-tundra-day`** — the measured worst sky-against-water pair over the twelve themes, dE 2.16
 *   and 0.1 of Rec. 601 luma, which is the case the struck waterline exists for. This one *is*
 *   pinned, by the `waterline-worst-theme` golden; the capture is the full frame around it.
 * - **`cap-beach-dawn`** — the same measurement at the other end of the day, where the sky and the
 *   water are far enough apart that the line correctly does not appear.
 *
 * Frames land in the golden output directory and are pulled with `adb pull`. Nothing here compares
 * anything, so it cannot fail and cannot be a gate; it is a camera.
 */
@RunWith(AndroidJUnit4::class)
class V426CaptureTest {

    private val storm = LiveWeatherSnapshot(
        precipitationType = PrecipitationType.RAIN,
        precipitationIntensity = 0.6f,
        cloudCoverFraction = 1f,
        isThunderstorm = true,
        fetchedAtMillis = 0L,
    )

    /** The lake turned up and populated, so the animals and the shore are both in frame. */
    private fun lake(themeId: String) = { c: SceneCustomization ->
        c.copy(
            lake = c.lake.copy(
                visible = true,
                height = if (themeId == "beach") 0.9f else 0.8f,
                sailboatsVisible = true,
                sailboatsDensity = 1f,
                dolphinsVisible = true,
                dolphinsDensity = 1f,
            ),
        )
    }

    private fun write(name: String, bitmap: Bitmap) {
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "golden-output",
        )
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test
    fun captureTheFramesTheReportNeeds() {
        write(
            "cap-beach-night",
            SceneGolden.render(
                GoldenScene(
                    name = "cap-beach-night", dayPhase = GoldenScene.night(),
                    themeId = "beach", customise = lake("beach"),
                ),
            ),
        )
        write(
            "cap-beach-storm",
            SceneGolden.render(
                GoldenScene(
                    name = "cap-beach-storm", dayPhase = GoldenScene.day(),
                    themeId = "beach", weather = storm, customise = lake("beach"),
                ),
            ),
        )
        write(
            "cap-beach-dawn",
            SceneGolden.render(
                GoldenScene(
                    name = "cap-beach-dawn", dayPhase = GoldenScene.day(hour = 6.6f),
                    themeId = "beach", customise = lake("beach"),
                ),
            ),
        )
        write(
            "cap-tundra-day",
            SceneGolden.render(
                GoldenScene(
                    name = "cap-tundra-day", dayPhase = GoldenScene.day(),
                    themeId = "tundra", customise = lake("tundra"),
                ),
            ),
        )
    }
}
