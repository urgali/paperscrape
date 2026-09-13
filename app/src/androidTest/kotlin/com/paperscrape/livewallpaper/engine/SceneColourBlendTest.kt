package com.paperscrape.livewallpaper.engine

import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [SceneColour.blendArgb] is `ColorUtils.blendARGB`, to the bit.
 *
 * ### Why this has to be instrumented, and why it has to exist
 *
 * v5.0 replaced `ColorUtils.blendARGB` with a pure-Kotlin blend in the two functions the gallery
 * preview needs -- `SceneCustomization.colorFor` and `SceneObjectRenderer.windowGlassColor` -- so
 * that "which colour does this building wear" became a question the JVM suite can answer. That is
 * only safe if the replacement is the same function, and *the JVM suite cannot check that*: the
 * thing it is being compared against is the framework call that is not mocked there. So the
 * comparison lives here, where both sides really run.
 *
 * A sweep rather than a handful of cases, because the failure mode this guards is a rounding
 * difference: `blendARGB` truncates each channel's float sum with a C-style cast, and an
 * implementation that rounded instead would agree on most inputs and differ on about half of
 * them. Every eighth channel value against every eighth, at every twentieth of the ratio, is
 * 33 x 33 x 21 comparisons per channel pair -- enough that a rounding difference cannot hide.
 */
@RunWith(AndroidJUnit4::class)
class SceneColourBlendTest {

    @Test
    fun theSceneBlendIsTheFrameworkBlend() {
        var compared = 0
        for (from in 0..255 step 8) {
            for (to in 0..255 step 8) {
                // One opaque grey against another, and one with a non-trivial alpha, so the alpha
                // channel is exercised as well as the colour ones.
                val a = (0xFF shl 24) or (from shl 16) or (from shl 8) or from
                val b = (0x80 shl 24) or (to shl 16) or (to shl 8) or to
                for (step in 0..20) {
                    val ratio = step / 20f
                    assertEquals(
                        "blend of ${Integer.toHexString(a)} and ${Integer.toHexString(b)} at $ratio",
                        ColorUtils.blendARGB(a, b, ratio),
                        SceneColour.blendArgb(a, b, ratio),
                    )
                    compared++
                }
            }
        }
        // The colours the neighbourhood actually blends between, exactly: the window ramp's two
        // ends and the four defaults of each editable category.
        val real = listOf(
            SceneObjectRenderer.WINDOW_GLASS_DAY, SceneObjectRenderer.WINDOW_GLASS_NIGHT,
            SceneCustomization.DEFAULT.houses.colorDay1, SceneCustomization.DEFAULT.houses.colorNight1,
            SceneCustomization.DEFAULT.houses.colorDay2, SceneCustomization.DEFAULT.houses.colorNight2,
            SceneCustomization.DEFAULT.buildings.colorDay1, SceneCustomization.DEFAULT.buildings.colorNight1,
            SceneCustomization.DEFAULT.buildings.colorDay2, SceneCustomization.DEFAULT.buildings.colorNight2,
        )
        for (x in real) {
            for (y in real) {
                for (step in 0..100) {
                    val ratio = step / 100f
                    assertEquals(
                        "a real pair at $ratio",
                        ColorUtils.blendARGB(x, y, ratio),
                        SceneColour.blendArgb(x, y, ratio),
                    )
                    compared++
                }
            }
        }
        assertEquals("the sweep must actually have run", true, compared > 20_000)
    }
}
