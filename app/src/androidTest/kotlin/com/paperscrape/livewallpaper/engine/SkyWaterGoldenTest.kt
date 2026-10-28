package com.paperscrape.livewallpaper.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **The sky and the water, the two largest surfaces in the frame.**
 *
 * The whole frame of every Canvas golden watches them, and admits no differing pixel
 * ([SceneGolden.MAX_DIFFERING_FRACTION]): `day` (`SceneGoldenTest.day`) holds the clouds and the
 * birds, `lake-busy` (`SceneGoldenTest.lakeBusy`) the water, the boats and the dolphins. Half the
 * clouds gone, every bird hidden or every dolphin hidden moves tens of pixels or more, where the
 * frame forgives none. There is no second assertion of those two frames with a band as its focus:
 * at a whole-frame limit of zero it would fail and pass with the first on every change, and a limit
 * of its own over the band could never act.
 *
 * This class holds the frame no other golden draws: the theme whose sky and water are closest,
 * where only the struck waterline separates them.
 */
@RunWith(AndroidJUnit4::class)
class SkyWaterGoldenTest {

    /**
     * **The committed frame in which the struck waterline is drawn.**
     *
     * In the other goldens the theme's sky and its water are far enough apart that the line
     * correctly does not appear, so the feature that exists for the *worst* theme needs a golden of
     * its own.
     *
     * Tundra with a tall lake at midday is the worst case, held by `LakeContrastTest`: the sky above
     * the shore and the water a CIELab dE of **2.16** and **0.1** of Rec. 601 luma apart. Without the
     * line the shore does not exist; with it, the line stands
     * [PaperRenderer.WATERLINE_MIN_LUMA_GAP], 14.5 of luma, clear of the sky.
     */
    @Test
    fun `waterline-worst-theme`() = SceneGolden.assertMatches(
        GoldenScene(
            name = "waterline-worst-theme",
            dayPhase = GoldenScene.day(),
            themeId = "tundra",
            customise = { it.copy(lake = it.lake.copy(visible = true, height = 0.8f)) },
            focus = listOf(
                GoldenFocus(
                    left = 0,
                    top = (SceneGolden.HEIGHT * SceneSpace.GROUND_SOLID_TOP_Y_FRACTION - SceneGolden.HEIGHT * 0.16f * 0.8f).toInt() - 3,
                    right = SceneGolden.WIDTH,
                    bottom = (SceneGolden.HEIGHT * SceneSpace.GROUND_SOLID_TOP_Y_FRACTION - SceneGolden.HEIGHT * 0.16f * 0.8f).toInt() + 5,
                    label = "the struck waterline",
                ),
            ),
        ),
    )
}
