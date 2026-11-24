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
 * for some eighteen minutes out of every thirty-seven. The meadow below the road carries as many again, laid the same way
 * (decision 96). Counted on the blits the scene hands its canvas, row by row, at three positions of the scroll.
 *
 * **Below the road each is the size of the line it stands on** (decision 119): smaller near the road, larger below,
 * [SceneSpace.nearRowSizeAt] of its ground line times its size-table size, as the near row's houses and trees are.
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
                val rows = blitsOf(drawable, customization, shift = -screens * WIDTH)
                for ((row, drawn) in listOf("street" to rows.street, "meadow below the road" to rows.near)) {
                    assertTrue(
                        "$name, $row: $drawn on the screen $screens screens along, where one screen carries $perScreen",
                        drawn >= perScreen,
                    )
                    // A slice beyond each edge is drawn too, so a thing straddling it is not cut: never more than two besides.
                    assertTrue(
                        "$name, $row: $drawn drawn $screens screens along, more than a screen's $perScreen and its edges",
                        drawn <= perScreen + 2,
                    )
                }
            }
            // The scroll a whole tile on is the same street: the thing laid in the tile's last slice, straddling the left
            // edge, is drawn on both sides of the wrap and does not vanish as the scroll wraps.
            assertEquals(
                "$name: the same street a tile apart",
                blitsOf(drawable, customization, shift = -2f * WIDTH + 0.001f), blitsOf(drawable, customization, shift = 0f),
            )
        }
    }

    @Test
    fun theMeadowBelowTheRoadTakesTheSizeOfTheLineEachStandsOn() {
        val spring = defaultCustomizationFor("spring")
        val winter = defaultCustomizationFor("winter").let { it.copy(snowPiles = 1f) }
        val autumn = defaultCustomizationFor("autumn").let { it.copy(leafPiles = 1f) }
        val flower = SceneSpace.scaleForHeight(SceneObjectRenderer.FLOWER_METRES_TALL, SceneObjectRenderer.FLOWER_SPRITE_UNITS_TALL)
        val pile = SceneSpace.scaleForHeight(SceneObjectRenderer.PILE_METRES_TALL, SceneObjectRenderer.PILE_SPRITE_UNITS_TALL)
        val cases = listOf(
            Triple("spring's flowers", spring, R.drawable.ground_flowers_bloom) to flower,
            Triple("winter's drifts", winter, R.drawable.snow_pile) to pile,
            Triple("autumn's heaps", autumn, R.drawable.leaf_pile) to pile,
        )
        val sceneScale = SceneSpace.sceneScale(HEIGHT)
        for ((case, base) in cases) {
            val (name, customization, drawable) = case
            val recorder = BlitCounter(drawable)
            draw(recorder, customization, shift = 0f)
            assertTrue("$name: nothing drawn below the road", recorder.nearLines.isNotEmpty())
            for (k in recorder.nearLines.indices) {
                val line = recorder.nearLines[k] / HEIGHT
                // The blit's own scale ([SpriteBlitter], a third for the scene's units) taken back out.
                val size = recorder.nearScales[k] * SpriteBlitter.SPRITE_PIXELS_PER_UNIT / (base * sceneScale)
                assertEquals("$name at $line of the screen", SceneSpace.nearRowSizeAt(line), size, 1e-4f)
            }
            // Smaller near the road, larger below: the drawn sizes differ, the nearest to the road the smallest.
            val smallest = recorder.nearScales.min()
            val largest = recorder.nearScales.max()
            assertTrue("$name: every one the same size ($smallest)", largest > smallest)
            val top = recorder.nearLines.indices.minBy { recorder.nearLines[it] }
            val bottom = recorder.nearLines.indices.maxBy { recorder.nearLines[it] }
            assertTrue("$name: the one nearest the road not smaller than the lowest", recorder.nearScales[top] < recorder.nearScales[bottom])
        }
    }

    /** How many times one frame blits [drawable] in each row: the street's ground, and the meadow below the road. */
    private data class Rows(val street: Int, val near: Int)

    /** How many times one frame blits [drawable] with the street scrolled by [shift] pixels, row by row. */
    private fun blitsOf(drawable: Int, customization: SceneCustomization, shift: Float): Rows {
        val recorder = BlitCounter(drawable)
        draw(recorder, customization, shift)
        return Rows(recorder.street, recorder.near)
    }

    /** One frame of a street with nothing standing on it, scrolled by [shift] pixels, into [recorder]. */
    private fun draw(recorder: BlitCounter, customization: SceneCustomization, shift: Float) {
        val renderer = SceneObjectRenderer(SceneObjectLayout(staticObjects = emptyList(), cars = emptyList()), customization, context, "sunset")
        renderer.draw(
            recorder, GroundGeometry(shift, 2f * WIDTH), dayBlend = 1f,
            elapsedSeconds = SceneTime(120.0), screenWidth = WIDTH, screenHeight = HEIGHT,
        )
    }

    /**
     * Counts the blits of one drawable, by the row its ground line falls in (the translation it is blitted under: above the
     * meadow below the road, or in it), and ignores everything else; below the road, each blit's ground line and the
     * whole scale it is blitted at.
     */
    private class BlitCounter(private val drawable: Int) : SceneCanvas {
        var street = 0
        var near = 0
        val nearLines = ArrayList<Float>()
        val nearScales = ArrayList<Float>()
        private var y = 0f
        private var s = 1f
        private val saved = ArrayDeque<Float>()
        private val savedScale = ArrayDeque<Float>()
        override fun save() {
            saved.addLast(y)
            savedScale.addLast(s)
        }
        override fun restore() {
            y = saved.removeLast()
            s = savedScale.removeLast()
        }
        override fun translate(dx: Float, dy: Float) {
            y += dy * s
        }
        override fun scale(sx: Float, sy: Float) {
            s *= sy
        }
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
            if (resId != drawable) return
            if (y < SceneSpace.NEAR_MEADOW_TOP_Y_FRACTION * HEIGHT) {
                street++
            } else {
                near++
                nearLines += y
                nearScales += s
            }
        }
    }

    private companion object {
        const val WIDTH = 720f
        const val HEIGHT = 1440f
    }
}
