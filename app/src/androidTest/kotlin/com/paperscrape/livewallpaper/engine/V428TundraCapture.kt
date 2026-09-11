package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **Capture only — writes the one frame `WaveContrastTest` names and asserts nothing.**
 *
 * Tundra's water is ice (`#BFE3EE`), and around dawn its surface passes the point where foam above
 * it does not exist, so the wave's two papers invert: the foam goes dark and the body darker still,
 * which is the only arrangement that keeps both luma gaps **and** their order. The sweep counts
 * **139 of 2 592** situations in that state, every one of them on Tundra, and places the palest at
 * **07:45 with the sky undimmed, surface luma 233.93**.
 *
 * **How a user reaches it, which is not obvious and is why this scene is configured as it is.**
 * Tundra snows by default, so no wave is ever drawn there out of the box. The case belongs to a
 * user who switches Tundra's own precipitation to rain: that draws rain *without* the storm dimming
 * a live-weather override brings, and `WaveContrastTest` asserts that **none** of the 139 occurs
 * with the sky dimmed — a storm darkens the surface and the inversion stops happening. So this is
 * theme rain, not live rain, and it is the worst case rather than a worst case.
 *
 * It is a camera, not a gate: nothing here compares anything, so it cannot fail. Committed for the
 * reason `V426CaptureTest` is — evidence whose recipe was deleted is a picture taken on trust.
 */
@RunWith(AndroidJUnit4::class)
class V428TundraCapture {

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
    fun tundraInvertedPapers() {
        write(
            "cap-v428-tundra-inverted",
            SceneGolden.render(
                GoldenScene(
                    name = "cap-v428-tundra-inverted",
                    dayPhase = SunPositionCalculator.compute(hour24 = 7.75f),
                    themeId = "tundra",
                    warmUpFrames = 320,
                    warmUpDeltaSeconds = 0.25f,
                    customise = { c ->
                        c.copy(
                            // Height 0.8 as `V426CaptureTest` uses for this theme: Tundra's own
                            // 0.25 puts the whole band behind the snow hills, and a wave the hills
                            // cover is not a wave anybody can look at. The tint does not depend on
                            // the height -- the surface is the lake colour blended with the sky at
                            // LAKE_MIRROR_SKY_SHARE -- so this changes what is visible, not what is
                            // being judged.
                            lake = c.lake.copy(visible = true, height = 0.8f),
                            precipitation = c.precipitation.copy(
                                visible = true,
                                type = PrecipitationType.RAIN,
                                intensity = 0.6f,
                                thunderstorm = false,
                            ),
                        )
                    },
                ),
            ),
        )
    }
}
