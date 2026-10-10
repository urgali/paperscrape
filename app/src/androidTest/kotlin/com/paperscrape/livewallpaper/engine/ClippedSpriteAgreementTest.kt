package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * **A sprite cut to a box draws the same thing on both backends** (v5.12, [SceneCanvas.drawSpriteClipped]),
 * the way the scene's other agreement checks hold the two to one picture.
 *
 * The subject is the one the operation exists for: a window bust, every layer of it -- the fixed art
 * and the three masks summed over it -- drawn large (five pixels a unit, so a cut edge is a column among
 * hundreds), plain and mirrored, by [SpriteBlitter] as the renderer draws it,
 * once whole and once cut. Four claims, each on each backend:
 *
 *  1. **a box round the whole bust draws the whole bust, to the bit** -- so a person standing still,
 *     whom the renderer draws whole, and the first frame of their walk, which it draws cut, are one
 *     picture;
 *  2. **outside the box nothing is drawn**, not by the fixed art nor by any mask -- no halo of a shirt
 *     where the person is not;
 *  3. **inside the box the bust is the whole bust's pixels**, a pixel clear of the cut, within the few
 *     levels each backend's own rounding moves a cut sprite by (measured, [GL_CUT_LEVELS],
 *     [CANVAS_CUT_LEVELS]);
 *  4. **inside the box the two backends differ no more than they do on the whole bust**, give or take
 *     the few levels of claim 3 (measured: no more at all); at the cut edge itself they may differ by
 *     the sub-pixel the operation's own note accepts.
 */
@RunWith(AndroidJUnit4::class)
class ClippedSpriteAgreementTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val width = GlGolden.WIDTH
    private val height = GlGolden.HEIGHT

    /** The bust's foot and its scale on the frame. */
    private val footX = 180f
    private val footY = 600f
    private val scale = 5f

    /** Region colours: a skin, a hair, a shirt -- what `coloursFor` hands the masks. */
    private val colours = intArrayOf(0, 0xFFC68642.toInt(), 0xFF3B2A1A.toInt(), 0xFF2E7D9A.toInt(), 0)

    /** The woman's window bust, every layer. */
    private val slots = PeopleLayerTable.WINDOW[1]

    /** Draws the bust onto [canvas], facing [facing], cut to [box] (in the bust's own units) when given. */
    private fun drawBust(canvas: SceneCanvas, blitter: SpriteBlitter, facing: Int, box: FloatArray?) {
        canvas.save()
        canvas.translate(footX, footY)
        canvas.scale(facing * scale, scale)
        val x = -SceneObjectRenderer.WINDOW_HEAD_ANCHOR_X_UNITS
        val y = -SceneObjectRenderer.WINDOW_HEAD_ANCHOR_Y_UNITS
        for (region in 0 until PeopleLayerTable.SLOTS) {
            val res = slots[region]
            if (res == 0) continue
            val tint = if (region == PeopleLayerTable.FIXED) SpriteBlitter.UNTINTED else colours[region]
            val additive = region != PeopleLayerTable.FIXED
            if (box == null) {
                if (additive) blitter.drawTintedAdded(canvas, res, x, y, SpriteScale.SCENE_UNITS, tint)
                else blitter.draw(canvas, res, x, y, SpriteScale.SCENE_UNITS)
            } else {
                blitter.drawClipped(canvas, res, x, y, SpriteScale.SCENE_UNITS, tint, additive, box[0], box[1], box[2], box[3])
            }
        }
        canvas.restore()
    }

    private fun onCanvas(facing: Int, box: FloatArray?): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF000000.toInt())
        val target = CanvasSceneTarget()
        target.bind(Canvas(bitmap))
        drawBust(target, SpriteBlitter(context), facing, box)
        target.unbind()
        return bitmap
    }

    private fun onGl(facing: Int, box: FloatArray?): Bitmap = GlGolden.drawOnce { drawBust(it, SpriteBlitter(context), facing, box) }

    private fun pixels(b: Bitmap): IntArray = IntArray(width * height).also { b.getPixels(it, 0, width, 0, 0, width, height) }

    private fun channelDiff(a: Int, b: Int): Int =
        maxOf(abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)), abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)), abs((a and 0xFF) - (b and 0xFF)))

    /** The box's device-space x range for a bust facing [facing]: the bust's units through the transform. */
    private fun deviceX(unitsX: Float, facing: Int): Float = footX + facing * scale * unitsX

    @Test
    fun aCutBustIsTheSamePictureOnBothBackends() {
        var compared = 0
        for (facing in intArrayOf(1, -1)) {
            val wholeCanvas = pixels(onCanvas(facing, null))
            val wholeGl = pixels(onGl(facing, null))
            // 1. A box round the whole bust.
            val all = floatArrayOf(-100f, -100f, 100f, 100f)
            assertTrue("facing $facing: Canvas, a box round the bust", wholeCanvas.contentEquals(pixels(onCanvas(facing, all))))
            assertTrue("facing $facing: GL, a box round the bust", wholeGl.contentEquals(pixels(onGl(facing, all))))

            // The bust's own worst disagreement between the backends, which the cut must not add to.
            var baseline = 0
            for (i in wholeCanvas.indices) baseline = maxOf(baseline, channelDiff(wholeCanvas[i], wholeGl[i]))

            // 2-4. Cut at several places across the bust, as a walker is at a window's edge.
            for (cut in floatArrayOf(-12.3f, -4f, 0.4f, 7.7f, 15.25f)) {
                for (keepLeft in listOf(true, false)) {
                    val box = if (keepLeft) floatArrayOf(-100f, -100f, cut, 100f) else floatArrayOf(cut, -100f, 100f, 100f)
                    val cutCanvas = pixels(onCanvas(facing, box))
                    val cutGl = pixels(onGl(facing, box))
                    val edge = deviceX(cut, facing)
                    // Which device side of the cut is kept: the box's side through the (possibly mirrored) transform.
                    val keptBelowEdge = (keepLeft && facing > 0) || (!keepLeft && facing < 0)
                    var worstInside = 0
                    var worstGlCut = 0
                    var worstCanvasCut = 0
                    for (y in 0 until height) for (x in 0 until width) {
                        val i = y * width + x
                        val px = x + 0.5f
                        if (abs(px - edge) < 1.5f) continue // the cut edge itself: a sub-pixel apart, accepted
                        val inside = if (keptBelowEdge) px < edge else px > edge
                        if (!inside) {
                            assertEquals("facing $facing cut $cut: Canvas drew outside the box at $x,$y", 0xFF000000.toInt(), cutCanvas[i])
                            assertEquals("facing $facing cut $cut: GL drew outside the box at $x,$y", 0xFF000000.toInt(), cutGl[i])
                        } else {
                            worstCanvasCut = maxOf(worstCanvasCut, channelDiff(wholeCanvas[i], cutCanvas[i]))
                            worstGlCut = maxOf(worstGlCut, channelDiff(wholeGl[i], cutGl[i]))
                            worstInside = maxOf(worstInside, channelDiff(cutCanvas[i], cutGl[i]))
                            compared++
                        }
                    }
                    println("ClippedSpriteAgreementTest facing $facing cut $cut keepLeft $keepLeft: Canvas cut vs whole $worstCanvasCut, GL cut vs whole $worstGlCut, Canvas vs GL inside $worstInside, whole bust $baseline")
                    assertTrue(
                        "facing $facing cut $cut: Canvas inside the box differs from the whole bust by $worstCanvasCut",
                        worstCanvasCut <= CANVAS_CUT_LEVELS,
                    )
                    assertTrue(
                        "facing $facing cut $cut: GL inside the box differs from the whole bust by $worstGlCut",
                        worstGlCut <= GL_CUT_LEVELS,
                    )
                    assertTrue(
                        "facing $facing cut $cut: inside the box the backends differ by $worstInside, the whole bust by $baseline",
                        worstInside <= baseline + GL_CUT_LEVELS,
                    )
                }
            }
        }
        assertTrue("compared $compared pixels", compared > 100_000)
    }

    private companion object {
        /**
         * How far a pixel inside the box may move when the bust is cut, out of 255 a channel: measured
         * first on the BV6600 (2026-10-09, twenty cuts, plain and mirrored) and set at what was seen. GL
         * reads the cut quad's texture coordinates through its interpolators, whose precision moves a
         * sample by a fraction of a texel: 2 to 3 levels. `Canvas` draws a cut bust to the bit, except
         * mirrored, where Skia's clipped blit rounds one channel 2 levels apart in a few pixels.
         */
        const val GL_CUT_LEVELS = 3
        const val CANVAS_CUT_LEVELS = 2
    }

    /** Nothing at all is drawn for a box beside the bust: not a quad, not a pixel. */
    @Test
    fun aBoxBesideTheBustDrawsNothing() {
        val beside = floatArrayOf(60f, -100f, 120f, 100f)
        for (facing in intArrayOf(1, -1)) {
            assertTrue(pixels(onCanvas(facing, beside)).all { it == 0xFF000000.toInt() })
            assertTrue(pixels(onGl(facing, beside)).all { it == 0xFF000000.toInt() })
        }
    }
}
