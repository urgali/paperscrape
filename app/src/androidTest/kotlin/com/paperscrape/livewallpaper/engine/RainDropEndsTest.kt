package com.paperscrape.livewallpaper.engine

import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * **A drop of rain has square ends, as long as round ones, and on the GPU it is one quad: six corners, no fan.**
 *
 * With round ends a drop would be a quad and a fan at each end, eight slices at the least however thin the drop: 54
 * corners, 48 of them the two ends, and heavy rain alone more than a batch. A square end is straight and reaches half a
 * stroke past the drop's point, as far as a round one, so the drop keeps its length (`DESIGN_NOTES.md` §12, decision 87).
 *
 * Asserted three ways: on the strokes the scene hands its canvas; on the corners a frame of heavy rain costs the GPU
 * target over the same frame without rain -- six for every drop drawn; and on the pixels, a drop's length along its fall
 * the same on the GPU as on the Canvas, and on the Canvas the same with square ends as with round ones.
 */
@RunWith(AndroidJUnit4::class)
class RainDropEndsTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aDropOfRainHasSquareEnds() {
        for (blood in listOf(false, true)) {
            val recorder = RainRecorder()
            renderer(720, 1440, rain = true, blood = blood).draw(recorder, NOON, CLOCK, 0f)
            assertTrue("only ${recorder.caps.size} drops drawn under heavy rain", recorder.caps.size > 100)
            assertTrue(
                "drops drawn with ${recorder.caps.toSet()} ends${if (blood) " under the blood rain" else ""}",
                recorder.caps.all { it == Paint.Cap.SQUARE },
            )
        }
    }

    @Test
    fun heavyRainIsOneQuadADropOnTheGpu() {
        val recorder = RainRecorder()
        renderer(GlGolden.WIDTH, GlGolden.HEIGHT, rain = true).draw(recorder, NOON, CLOCK, 0f)
        val drops = recorder.drawn
        assertTrue("only $drops drops drawn under heavy rain", drops > 100)
        var dry = 0L
        var wet = 0L
        GlGolden.drawOnce { canvas ->
            val gl = canvas as GlSceneTarget
            gl.beginFrame()
            renderer(GlGolden.WIDTH, GlGolden.HEIGHT, rain = false).draw(gl, NOON, CLOCK, 0f)
            gl.endFrame()
            dry = gl.drawnVertices
            gl.beginFrame()
            renderer(GlGolden.WIDTH, GlGolden.HEIGHT, rain = true).draw(gl, NOON, CLOCK, 0f)
            gl.endFrame()
            wet = gl.drawnVertices
        }
        assertEquals("the corners heavy rain adds to a frame, for $drops drops", 6L * drops, wet - dry)
    }

    /**
     * A drop's length along its fall -- the reach along the line of the pixels it lights at least half, end to end -- at
     * the widths the scene draws a drop at from 1.2 px up (the blood rain's on the GL tests' frame, the rain's and the
     * blood rain's on the BV6600's) and at a thick line, where a cap is several pixels: the GPU's square ends as long as
     * the Canvas's, and the Canvas's square ends as long as its round ones. Without the GPU's half a stroke at each end a
     * drop is a whole stroke shorter. The rain's own drop on the GL tests' frame, 0.66 px, lights no pixel half: its
     * ends are the same code, and [heavyRainIsOneQuadADropOnTheGpu] counts its corners.
     */
    @Test
    fun aDropIsAsLongOnTheGpuAsOnTheCanvasAndAsWithRoundEnds() {
        val smallMetre = SceneSpace.pixelsPerMetre(GlGolden.HEIGHT.toFloat())
        val phoneMetre = SceneSpace.pixelsPerMetre(1440f)
        val widths = floatArrayOf(
            PaperRenderer.RAIN_STROKE_WIDTH_METRES * smallMetre * PaperRenderer.BLOOD_RAIN_THICKER,
            PaperRenderer.RAIN_STROKE_WIDTH_METRES * phoneMetre,
            PaperRenderer.RAIN_STROKE_WIDTH_METRES * phoneMetre * PaperRenderer.BLOOD_RAIN_THICKER,
            8f,
        )
        val lines = ArrayList<FloatArray>()
        for ((row, w) in widths.withIndex()) {
            for (k in 0 until LINES_A_WIDTH) {
                val x = 30f + (k % 4) * 85f + k * 0.37f
                val y = 20f + row * 190f + (k / 4) * 85f + k * 0.29f
                val len = 20f + (k % 4) * 9f + (k / 4) * 4.5f
                // As a drop falls: a quarter of its fall to the left.
                lines += floatArrayOf(x, y, x - len * 0.25f, y + len, w)
            }
        }
        fun draw(canvas: SceneCanvas, cap: Paint.Cap) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                style = Paint.Style.STROKE
                strokeCap = cap
            }
            for (l in lines) {
                paint.strokeWidth = l[4]
                canvas.drawLine(l[0], l[1], l[2], l[3], paint)
            }
        }
        val gpu = GlGolden.drawOnce { draw(it, Paint.Cap.SQUARE) }
        val square = onCanvas { draw(it, Paint.Cap.SQUARE) }
        val round = onCanvas { draw(it, Paint.Cap.ROUND) }
        val reaches = lines.map { l -> floatArrayOf(kotlin.math.hypot(l[2] - l[0], l[3] - l[1]), reach(gpu, l), reach(square, l), reach(round, l)) }
        val seen = lines.indices.map { k ->
            "w=%.2f len=%.2f gpu=%.2f square=%.2f round=%.2f".format(lines[k][4], reaches[k][0], reaches[k][1], reaches[k][2], reaches[k][3])
        }
        println("RainDropEndsTest reach: " + seen.joinToString(" | "))
        java.io.File(GlGolden.outputDir(), "drop_reach.txt").writeText(seen.joinToString("\n") + "\n")
        for (k in lines.indices) assertTrue("${seen[k]}: the drop lights less than its own length", reaches[k][2] >= reaches[k][0])
        // A thin line's reach steps by a pixel as a tip's pixel crosses half: the drops of one width are compared on average.
        for (row in widths.indices) {
            val rows = reaches.subList(row * LINES_A_WIDTH, (row + 1) * LINES_A_WIDTH)
            val g = rows.map { it[1] }.average()
            val s = rows.map { it[2] }.average()
            val r = rows.map { it[3] }.average()
            val at = "w=%.2f: on average gpu=%.2f square=%.2f round=%.2f".format(widths[row], g, s, r)
            assertTrue("$at: the GPU's drop is not as long as the Canvas's", abs(g - s) <= REACH_TOLERANCE_PX)
            assertTrue("$at: square ends are not as long as round ones", abs(s - r) <= REACH_TOLERANCE_PX)
        }
    }

    private fun onCanvas(draw: (SceneCanvas) -> Unit): android.graphics.Bitmap {
        val bitmap = android.graphics.Bitmap.createBitmap(GlGolden.WIDTH, GlGolden.HEIGHT, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF000000.toInt())
        val target = CanvasSceneTarget()
        target.bind(android.graphics.Canvas(bitmap))
        draw(target)
        target.unbind()
        return bitmap
    }

    /**
     * How far the pixels lit at least half by the line [l] (x0, y0, x1, y1, width) reach along it: the extreme projections
     * on its direction of the centres of the pixels within its box, plus one pixel.
     */
    private fun reach(frame: android.graphics.Bitmap, l: FloatArray): Float {
        val dx = l[2] - l[0]
        val dy = l[3] - l[1]
        val len = kotlin.math.hypot(dx, dy)
        val ux = dx / len
        val uy = dy / len
        val pad = l[4] + 3f
        val left = (minOf(l[0], l[2]) - pad).toInt().coerceAtLeast(0)
        val right = (maxOf(l[0], l[2]) + pad).toInt().coerceAtMost(frame.width - 1)
        val top = (minOf(l[1], l[3]) - pad).toInt().coerceAtLeast(0)
        val bottom = (maxOf(l[1], l[3]) + pad).toInt().coerceAtMost(frame.height - 1)
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for (y in top..bottom) for (x in left..right) {
            if ((frame.getPixel(x, y) shr 8 and 0xFF) < 128) continue
            val t = (x + 0.5f - l[0]) * ux + (y + 0.5f - l[1]) * uy
            if (t < lo) lo = t
            if (t > hi) hi = t
        }
        return if (hi < lo) 0f else hi - lo + 1f
    }

    /**
     * Sunset at noon with heavy rain or none, and nothing else the rain changes: no clouds (the rain falls everywhere), no
     * people (they would open umbrellas), no lake (it would raise waves).
     */
    private fun renderer(width: Int, height: Int, rain: Boolean, blood: Boolean = false): PaperRenderer {
        val defaults = defaultCustomizationFor("sunset")
        return PaperRenderer(width, height, context).apply {
            theme = ThemeCatalog.byId("sunset")
            sceneCustomization = defaults.copy(
                clouds = defaults.clouds.copy(visible = false),
                people = defaults.people.copy(visible = false),
                lake = defaults.lake.copy(visible = false),
                bloodRainEnabled = blood,
                precipitation = defaults.precipitation.copy(visible = rain, type = PrecipitationType.RAIN, intensity = 1f),
            )
            lightningStrikesEnabled = false
            scrollSpeed = 0f
        }
    }

    /**
     * Records the drops of rain: the lines that run a quarter of their fall to the left, as every drop does. [caps] holds
     * each one's ends, [drawn] counts the ones with any alpha -- the ones a backend draws.
     */
    private class RainRecorder : SceneCanvas {
        val caps = ArrayList<Paint.Cap>()
        var drawn = 0
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
            val fall = stopY - startY
            if (fall <= 0f || abs((stopX - startX) + 0.25f * fall) > 0.01f) return
            caps += paint.strokeCap
            if (paint.color ushr 24 != 0) drawn++
        }
        override fun save() = Unit
        override fun restore() = Unit
        override fun translate(dx: Float, dy: Float) = Unit
        override fun scale(sx: Float, sy: Float) = Unit
        override fun rotate(degrees: Float) = Unit
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
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
        ) = Unit
        override fun drawSprite(
            resId: Int,
            source: SpriteSource,
            left: Float,
            top: Float,
            tintColor: Int,
            alpha: Int,
            additive: Boolean,
        ) = Unit
    }

    private companion object {
        val NOON = SunPositionCalculator.compute(hour24 = 12f)
        val CLOCK = SceneTime(60.0)

        /**
         * Under the thinnest drop's width: the two rasterisers' edges, the Canvas's analytic and the GPU's multisampled,
         * part on a tip's pixel; a GPU drop without its square ends is a whole width shorter.
         */
        const val REACH_TOLERANCE_PX = 0.75

        /** Drops of one width, at as many lengths and sub-pixel phases. */
        const val LINES_A_WIDTH = 8
    }
}
