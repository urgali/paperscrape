package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **Capture only — writes the frames the v5.1 palm is judged on and asserts nothing.**
 *
 * Fourteen frames: the redrawn palm on both themes that have one, in each of its three crowns, at
 * midday and at one in the morning, plus the two the palms switch turns into ordinary trees. They
 * are what the maintainer looks at to accept or refuse the drawing, the night shade and the switch,
 * and none of them is a gate — nothing here compares anything, so it cannot fail.
 *
 * **Night is half of what this exists for.** Until v5.1 the palms were fixed art blitted with no
 * colour at all, so a frond came out of midnight at exactly the value it came out of noon —
 * measured at (107,168,79) at both. The `-night` frames here are the before-and-after of that, and
 * they are worth looking at beside a `-day` frame of the same scene rather than on their own: what
 * is being judged is not whether the palm is dark, it is whether it is as dark as the scene around
 * it.
 *
 * Committed for the reason `V426CaptureTest` and `V428TundraCapture` are: evidence whose recipe
 * was deleted is a picture taken on trust.
 */
@RunWith(AndroidJUnit4::class)
class V51PalmCapture {

    private fun write(name: String, bitmap: Bitmap) {
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "golden-output",
        )
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun capture(
        label: String,
        themeId: String,
        night: Boolean,
        customise: (SceneCustomization) -> SceneCustomization = { it },
    ) {
        val name = "cap-v51-$label-$themeId-" + if (night) "night" else "day"
        write(
            name,
            SceneGolden.render(
                GoldenScene(
                    name = name,
                    // 13:00 and 01:00, the two hours the phase-zero photographs were taken at, so
                    // the day/night pair here is the same pair the defect was measured on.
                    dayPhase = if (night) GoldenScene.night() else GoldenScene.day(),
                    themeId = themeId,
                    customise = customise,
                ),
            ),
        )
    }

    @Test
    fun theLivingCrown() {
        for (themeId in THEMES) for (night in NIGHTS) capture("live", themeId, night)
    }

    @Test
    fun theFrostedCrown() {
        // The winter palette, which as of v5.1 draws a whole frosted crown in place of the green
        // one rather than dabbing white tips on top of it.
        for (themeId in THEMES) for (night in NIGHTS) {
            capture("frost", themeId, night) { it.copy(winterColorsEnabled = true) }
        }
    }

    @Test
    fun theDeadCrown() {
        for (themeId in THEMES) for (night in NIGHTS) {
            capture("dead", themeId, night) { it.copy(halloweenEnabled = true) }
        }
    }

    @Test
    fun thePalmsSwitchOff() {
        // What the two themes look like with the palms turned off: the same tree slots, in the same
        // places, drawn as the broadleaf tree every other theme uses. Day only -- the question this
        // frame answers is which species is standing there, and the night shade is the previous
        // test's subject.
        for (themeId in THEMES) capture("oaks", themeId, night = false) { it.copy(palmsEnabled = false) }
    }

    private companion object {
        val THEMES = listOf("desert", "beach")
        val NIGHTS = listOf(false, true)
    }
}
