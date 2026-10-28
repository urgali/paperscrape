package com.paperscrape.livewallpaper.engine

import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **Every screen of the street carries its flowers, its drifts and its heaps of leaves.**
 *
 * The street is two screens wide and repeats as it scrolls ([GroundGeometry.tileWidth]). The flowers of the meadow and
 * the piles of snow and leaves are laid over the whole tile at one screen's density,
 * [SceneObjectRenderer.FLOWER_CLUMP_COUNT] clumps and [SceneObjectRenderer.pileCount] piles a screen, so the screen
 * the scroll shows -- the first, the second, or across the two -- carries as many (`DESIGN_NOTES.md` §12, decision 88).
 * Laid over one screen only, the other would be bare: at the factory scroll on the BV6600 Spring would have no flower
 * for some eighteen minutes out of every thirty-seven. Counted on the blits the scene hands its canvas, at three
 * positions of the scroll.
 */
@RunWith(AndroidJUnit4::class)
class GroundScatterAcrossTheTileTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everyScreenOfTheStreetCarriesItsFlowersAndItsPiles() {
        val spring = defaultCustomizationFor("spring")
        assertTrue("Spring ships its flowers", spring.flowersEnabled)
        val winter = defaultCustomizationFor("winter").let { it.copy(snowPiles = 1f) }
        val autumn = defaultCustomizationFor("autumn").let { it.copy(leafPiles = 1f) }
        val cases = listOf(
            Triple("spring's flowers", spring, R.drawable.ground_flowers_bloom) to SceneObjectRenderer.FLOWER_CLUMP_COUNT,
            Triple("winter's drifts at 100 %", winter, R.drawable.snow_pile) to SceneObjectRenderer.pileCount(1f),
            Triple("autumn's heaps at 100 %", autumn, R.drawable.leaf_pile) to SceneObjectRenderer.pileCount(1f),
        )
        for ((case, perScreen) in cases) {
            val (name, customization, drawable) = case
            // The first screen of the tile, half way across the two, and the second.
            for (screens in floatArrayOf(0f, 0.5f, 1f)) {
                val drawn = blitsOf(drawable, customization, shift = -screens * WIDTH)
                assertTrue(
                    "$name: $drawn on the screen $screens screens along, where one screen carries $perScreen",
                    drawn >= perScreen,
                )
                // A slice beyond each edge is drawn too, so a thing straddling it is not cut: never more than two besides.
                assertTrue("$name: $drawn drawn $screens screens along, more than a screen's $perScreen and its edges", drawn <= perScreen + 2)
            }
            // The scroll a whole tile on is the same street: the thing laid in the tile's last slice, straddling the left
            // edge, is drawn on both sides of the wrap and does not vanish as the scroll wraps.
            assertEquals(
                "$name: the same street a tile apart",
                blitsOf(drawable, customization, shift = -2f * WIDTH + 0.001f), blitsOf(drawable, customization, shift = 0f),
            )
        }
    }

    /** How many times one frame blits [drawable] with the street scrolled by [shift] pixels. */
    private fun blitsOf(drawable: Int, customization: SceneCustomization, shift: Float): Int {
        val renderer = SceneObjectRenderer(SceneObjectLayout(staticObjects = emptyList(), cars = emptyList()), customization, context, "sunset")
        val recorder = BlitCounter(drawable)
        renderer.draw(
            recorder, GroundGeometry(shift, 2f * WIDTH), dayBlend = 1f,
            elapsedSeconds = SceneTime(120.0), screenWidth = WIDTH, screenHeight = HEIGHT,
        )
        return recorder.count
    }

    /** Counts the blits of one drawable and ignores everything else. */
    private class BlitCounter(private val drawable: Int) : SceneCanvas {
        var count = 0
        override fun save() = Unit
        override fun restore() = Unit
        override fun translate(dx: Float, dy: Float) = Unit
        override fun scale(sx: Float, sy: Float) = Unit
        override fun rotate(degrees: Float) = Unit
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) = Unit
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
        ) = drawSprite(resId, source, left, top, tintColor, alpha, additive)
        override fun drawSprite(
            resId: Int,
            source: SpriteSource,
            left: Float,
            top: Float,
            tintColor: Int,
            alpha: Int,
            additive: Boolean,
        ) {
            if (resId == drawable) count++
        }
    }

    private companion object {
        const val WIDTH = 720f
        const val HEIGHT = 1440f
    }
}
