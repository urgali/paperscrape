package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rain and snow are sized against the viewport, not against the canvas.
 *
 * ### The defect
 *
 * Every size in `drawPrecipitation` was an absolute canvas pixel: a 2 px stroke, a 16-26 px
 * streak, a 2-4.5 px flake, a 14 px sway, a 40 px bottom margin. `SceneSpace` exists precisely to
 * stop that — *"object sizes used to be absolute canvas pixels while every ground line was a
 * fraction of screen height, so the composition was only correct on one device"* — and every
 * other layer had adopted it, down to `drawRoad`'s dash lengths. Precipitation had not.
 *
 * The numbers were tuned at the **golden frame's** 800 px rather than at
 * [SceneSpace.REFERENCE_SCREEN_HEIGHT_PX], so the effect was drawn at three times its intended
 * relative size in every test and roughly a third of it on a phone. Measured on a 1080x2424
 * viewport against 360x800, the whole precipitation layer fell from 0.94% of the frame to 0.11%.
 *
 * Snow survived that on contrast — a white disc still reads against a blue sky. Rain, a
 * translucent `0xFF7FB3E0` hairline against a `0xFF6EC6FF` sky, did not, which is the
 * "rain is not there, snow is" that was reported with Location and Live Weather both off.
 *
 * ### What is pinned here
 *
 * The arithmetic, exactly: that the constants are stated at the reference height, and that the
 * golden frame is a fixed point of the change so no committed golden had to move.
 * `PrecipitationPixelTest` measures the rendered result.
 */
class PrecipitationScaleTest {

    /** The frame every committed golden is drawn at. */
    private val goldenHeight = 800f

    @Test
    fun `the reference height scales to one`() {
        assertEquals(1f, SceneSpace.sceneScale(SceneSpace.REFERENCE_SCREEN_HEIGHT_PX), 1e-6f)
    }

    /**
     * **The golden frame is a fixed point.**
     *
     * `sceneScale(800) = 1/3`, and each constant is three times the value the goldens were drawn
     * with, so every size at 800 px comes back to exactly what it was. This is the assertion that
     * makes "zero golden changes" a property rather than an observation — get the rebasing wrong
     * and this fails before any golden is even rendered.
     */
    @Test
    fun `every precipitation size is unchanged at the golden frame size`() {
        val s = SceneSpace.sceneScale(goldenHeight)
        assertEquals("rain stroke width", 2f, PaperRenderer.RAIN_STROKE_WIDTH_PX * s, 1e-4f)
        assertEquals("rain length, shortest", 16f, PaperRenderer.RAIN_LENGTH_MIN_PX * s, 1e-4f)
        assertEquals("rain length, longest", 26f, PaperRenderer.RAIN_LENGTH_MAX_PX * s, 1e-4f)
        assertEquals("snow radius, smallest", 2f, PaperRenderer.SNOW_RADIUS_MIN_PX * s, 1e-4f)
        assertEquals("snow radius, largest", 4.5f, PaperRenderer.SNOW_RADIUS_MAX_PX * s, 1e-4f)
        assertEquals("snow sway", 14f, PaperRenderer.SNOW_SWAY_PX * s, 1e-4f)
        assertEquals("bottom margin", 40f, PaperRenderer.PRECIPITATION_BOTTOM_MARGIN_PX * s, 1e-4f)
    }

    /**
     * A drop is the same share of the frame on every device, which is the whole point.
     *
     * Stated as a ratio rather than as pixels so it says the property and not the numbers: on a
     * viewport three times as tall, a raindrop is three times as long. Against the old absolute
     * constants the ratio was 1.0 at every size and this fails.
     */
    @Test
    fun `a raindrop grows in proportion to the viewport`() {
        for (height in listOf(800f, 1600f, 2400f, 2424f, 3200f)) {
            val ratio = SceneSpace.sceneScale(height) / SceneSpace.sceneScale(goldenHeight)
            assertEquals(
                "a $height px viewport should scale by height/800",
                height / goldenHeight, ratio, 1e-4f,
            )
            val length = PaperRenderer.RAIN_LENGTH_MAX_PX * SceneSpace.sceneScale(height)
            assertEquals(
                "the longest streak should stay the same fraction of the frame",
                26f / goldenHeight, length / height, 1e-5f,
            )
        }
    }

    /** Snow gets exactly the same treatment; the fix is not allowed to be rain-only. */
    @Test
    fun `a snowflake grows in proportion to the viewport`() {
        for (height in listOf(800f, 1600f, 2424f)) {
            val radius = PaperRenderer.SNOW_RADIUS_MAX_PX * SceneSpace.sceneScale(height)
            assertEquals(4.5f / goldenHeight, radius / height, 1e-5f)
        }
    }

    /**
     * A phone-sized viewport really does get a bigger drop than the golden frame — the fix would
     * be vacuous if the reference height happened to equal the golden's.
     */
    @Test
    fun `a phone-sized viewport draws a visibly larger drop than the golden frame`() {
        val phone = PaperRenderer.RAIN_LENGTH_MAX_PX * SceneSpace.sceneScale(2424f)
        val golden = PaperRenderer.RAIN_LENGTH_MAX_PX * SceneSpace.sceneScale(goldenHeight)
        assertTrue("a 2424 px viewport should draw a longer streak than an 800 px one", phone > golden * 2.9f)
    }

    /** A degenerate viewport must not divide by zero or invert the scene. */
    @Test
    fun `a zero-height viewport falls back to the reference scale`() {
        assertEquals(1f, SceneSpace.sceneScale(0f), 1e-6f)
        assertEquals(1f, SceneSpace.sceneScale(-100f), 1e-6f)
    }
}
