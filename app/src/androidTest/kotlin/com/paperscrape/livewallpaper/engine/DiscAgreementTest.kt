package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * **A disc is the same disc on both backends** (v5.14, [SceneCanvas.drawDisc]): the quad of the atlas the GPU draws
 * and the circle a Canvas draws are one dot -- as large, as bright, in the same place, with the same soft edge.
 *
 * The subjects are the dots the scene draws by the hundred, at the sizes it draws them, through [SpriteBlitter.drawDisc]
 * as the renderer calls it: the point stars (1.3 to 3.1 px of radius), the snowflakes (1.75 to 4.05 px on the BV6600,
 * to 9 on a 3200-pixel screen), the bulbs (1.2 px on the farthest house to 10 on a near tower of such a screen), each
 * at four sub-pixel phases, in white at full strength and in the snow's alpha. For each the measure is the light it
 * puts on the frame -- its coverage, the sum of a channel over its cell, which is its area for a disc -- the radius
 * that coverage stands for, its centre of light, and the worst pixel apart:
 *
 *  1. **the same size**: the GPU disc's coverage within [LARGE_SHARE] of the circle's from [LARGE_PX] of radius up, within
 *     [SMALL_SHARE] below it, and averaged over the four phases within [SMALL_MEAN_SHARE];
 *  2. **in the same place**: its centre of light within [CENTRE_PX] of the circle's;
 *  3. **the same edge**: no pixel more than [EDGE_LEVELS] apart, out of 255 -- the two rasterise the rim their own
 *     way, the Canvas analytically, the GPU by filtering the disc's texels and multisampling the quad.
 *
 * **Why the small ones are allowed more** (measured on the BV6600, 2026-10-15): a disc of a few pixels is sampled from a
 * reduced copy of a few texels ([SpriteDetailLevel]), and the fragment shader reads its texture coordinates at medium
 * precision (`GlSpriteProgram`), which on this GPU moves a sample by a fraction of a texel -- a fraction of the whole disc
 * at these sizes. So a dot of 1.3 to 4 px puts up to 13.5 % less light than the Canvas circle on average over its phases,
 * up to 19 % at one phase; from 5 px up it is within 1.6 %. The Canvas circle is itself not the ideal disc (Skia's is
 * up to 4 % off pi r^2 at these sizes). And the GPU's fan, printed beside each -- what the dots were until v5.14 -- is an
 * inscribed polygon of 8 sides at these sizes, 0.90 of the circle's area: under the circle by 6 to 16 % from 1.6 px up,
 * where the disc is nearer it, but within 1 % at 1.32 px, where the disc is 13 % under -- the smallest stars are dimmer by
 * that. In a frame of the wallpaper (720x1440, Winter at night, the same moment on both builds) the median flake or star
 * puts 3.5 to 4.7 % more light than the fan did, 0.04 px wider, the tenth dimmest 3 to 4 % less (the round's report).
 */
@RunWith(AndroidJUnit4::class)
class DiscAgreementTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val width = GlGolden.WIDTH
    private val height = GlGolden.HEIGHT

    private class Dot(val cx: Float, val cy: Float, val radius: Float, val colour: Int, val alpha: Int)

    /** Every radius at every phase, in a grid of cells [CELL] px wide, in white at full strength and in the snow's alpha. */
    private val dots: List<Dot> = buildList {
        var cell = 0
        val perRow = width / CELL
        for ((colour, alpha) in listOf(0xFFFFFFFF.toInt() to 255, 0xFFFBF4E6.toInt() to PaperRenderer.SNOW_ALPHA)) {
            for (radius in RADII) {
                for (phase in PHASES) {
                    val col = cell % perRow
                    val row = cell / perRow
                    add(Dot(col * CELL + CELL / 2f + phase, row * CELL + CELL / 2f + phase * 0.6f, radius, colour, alpha))
                    cell++
                }
            }
        }
    }

    private fun drawDots(canvas: SceneCanvas, blitter: SpriteBlitter) {
        for (d in dots) blitter.drawDisc(canvas, d.cx, d.cy, d.radius, d.colour, d.alpha)
    }

    private val fanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private fun drawFans(canvas: SceneCanvas) {
        for (d in dots) {
            fanPaint.color = d.colour
            fanPaint.setAlphaWithoutAllocating(d.alpha)
            canvas.drawCircle(d.cx, d.cy, d.radius, fanPaint)
        }
    }

    private fun onCanvas(): IntArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF000000.toInt())
        val target = CanvasSceneTarget()
        target.bind(Canvas(bitmap))
        drawDots(target, SpriteBlitter(context))
        target.unbind()
        return pixels(bitmap)
    }

    private fun pixels(b: Bitmap): IntArray = IntArray(width * height).also { b.getPixels(it, 0, width, 0, 0, width, height) }

    /** The light one dot's cell holds: its coverage (a channel's sum over 255) and its centre of light. */
    private class Light(val coverage: Double, val x: Double, val y: Double)

    private fun lightOf(px: IntArray, d: Dot): Light {
        val left = (d.cx - CELL / 2f).toInt().coerceAtLeast(0)
        val top = (d.cy - CELL / 2f).toInt().coerceAtLeast(0)
        var sum = 0.0
        var sx = 0.0
        var sy = 0.0
        for (y in top until minOf(top + CELL, height)) for (x in left until minOf(left + CELL, width)) {
            val g = (px[y * width + x] shr 8 and 0xFF) / 255.0
            sum += g
            sx += g * (x + 0.5)
            sy += g * (y + 0.5)
        }
        return if (sum > 0.0) Light(sum, sx / sum, sy / sum) else Light(0.0, d.cx.toDouble(), d.cy.toDouble())
    }

    private fun worstPixel(a: IntArray, b: IntArray, d: Dot): Int {
        val left = (d.cx - CELL / 2f).toInt().coerceAtLeast(0)
        val top = (d.cy - CELL / 2f).toInt().coerceAtLeast(0)
        var worst = 0
        for (y in top until minOf(top + CELL, height)) for (x in left until minOf(left + CELL, width)) {
            val i = y * width + x
            for (shift in intArrayOf(0, 8, 16)) worst = maxOf(worst, abs((a[i] shr shift and 0xFF) - (b[i] shr shift and 0xFF)))
        }
        return worst
    }

    @Test
    fun aDiscIsTheSameDotOnBothBackends() {
        assertTrue("the dots do not fit the frame", dots.last().cy + CELL / 2f <= height)
        val circle = onCanvas()
        val quad = pixels(GlGolden.drawOnce { drawDots(it, SpriteBlitter(context)) })
        val fan = pixels(GlGolden.drawOnce { drawFans(it) })
        var worstShare = 0.0
        var worstLarge = 0.0
        var brightest = 0.0
        var worstCentre = 0.0
        var worstEdge = 0
        val shares = HashMap<Pair<Float, Int>, MutableList<Double>>()
        for (d in dots) {
            val c = lightOf(circle, d)
            val q = lightOf(quad, d)
            val f = lightOf(fan, d)
            val share = q.coverage / c.coverage - 1.0
            val centre = sqrt((q.x - c.x) * (q.x - c.x) + (q.y - c.y) * (q.y - c.y))
            val edge = worstPixel(circle, quad, d)
            println(
                "DiscAgreementTest r=%.2f at (%.2f, %.2f) alpha %d: circle %.2f px of light (radius %.3f), quad %+.1f %% (radius %.3f), fan %+.1f %% (radius %.3f); centre %.3f px apart; worst pixel %d"
                    .format(
                        d.radius, d.cx, d.cy, d.alpha, c.coverage, radiusOf(c, d), 100 * share, radiusOf(q, d),
                        100 * (f.coverage / c.coverage - 1.0), radiusOf(f, d), centre, edge,
                    ),
            )
            worstShare = maxOf(worstShare, abs(share))
            if (d.radius >= LARGE_PX) worstLarge = maxOf(worstLarge, abs(share)) else brightest = maxOf(brightest, share)
            shares.getOrPut(d.radius to d.alpha) { mutableListOf() } += share
            worstCentre = maxOf(worstCentre, centre)
            worstEdge = maxOf(worstEdge, edge)
        }
        val worstMean = shares.values.maxOf { abs(it.average()) }
        val brightestMean = shares.filterKeys { it.first < LARGE_PX }.values.maxOf { it.average() }
        println("DiscAgreementTest worst: coverage %.2f %% (from %.0f px: %.2f %%; mean over the phases %.2f %%), centre %.3f px, pixel %d"
            .format(100 * worstShare, LARGE_PX, 100 * worstLarge, 100 * worstMean, worstCentre, worstEdge))
        assertTrue("a GPU disc's light is %.2f %% off the circle's".format(100 * worstShare), worstShare <= SMALL_SHARE)
        assertTrue("a GPU disc of %.0f px or more is %.2f %% off the circle's".format(LARGE_PX, 100 * worstLarge), worstLarge <= LARGE_SHARE)
        assertTrue("a GPU disc's light, over its phases, is %.2f %% off the circle's".format(100 * worstMean), worstMean <= SMALL_MEAN_SHARE)
        // Under 5 px the quad was measured darker than the circle, never brighter by more than 1 %: a disc drawn larger
        // than it should be there brings more light than any phase does, and fails (v5.14C).
        assertTrue("a GPU disc under %.0f px carries %.2f %% more light than the circle".format(LARGE_PX, 100 * brightest), brightest <= SMALL_BRIGHTER)
        assertTrue("a GPU disc under %.0f px carries, over its phases, %.2f %% more light than the circle".format(LARGE_PX, 100 * brightestMean), brightestMean <= SMALL_MEAN_BRIGHTER)
        assertTrue("a GPU disc's centre is %.3f px off the circle's".format(worstCentre), worstCentre <= CENTRE_PX)
        assertTrue("a GPU disc's pixel is $worstEdge levels off the circle's", worstEdge <= EDGE_LEVELS)
    }

    /** Counts the circles and the discs a frame asks for, and draws nothing. */
    private class DotCounter : SceneCanvas {
        var circles = 0
        var discs = 0
        override fun save() = Unit
        override fun restore() = Unit
        override fun translate(dx: Float, dy: Float) = Unit
        override fun scale(sx: Float, sy: Float) = Unit
        override fun rotate(degrees: Float) = Unit
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) = Unit
        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) { circles++ }
        override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
        override fun drawWedge(cx: Float, cy: Float, radius: Float, startAngle: Float, sweepAngle: Float, paint: Paint) = Unit
        override fun drawShape(shape: SceneShape, paint: Paint) = Unit
        override fun drawVerticalGradientShape(shape: SceneShape, gradientTopY: Float, gradientBottomY: Float, topColor: Int, bottomColor: Int, alpha: Int) = Unit
        override fun drawVerticalGradientRect(left: Float, top: Float, right: Float, bottom: Float, topColor: Int, bottomColor: Int) = Unit
        override fun drawRadialGlow(cx: Float, cy: Float, radius: Float, color: Int, centerAlpha: Int) = Unit
        override fun drawSprite(resId: Int, source: SpriteSource, left: Float, top: Float, tintColor: Int, alpha: Int, additive: Boolean) = Unit
        override fun drawSpriteClipped(
            resId: Int, source: SpriteSource, left: Float, top: Float, tintColor: Int, alpha: Int, additive: Boolean,
            clipLeft: Float, clipTop: Float, clipRight: Float, clipBottom: Float,
        ) = Unit
        override fun drawDisc(resId: Int, source: SpriteSource, cx: Float, cy: Float, radius: Float, colour: Int, alpha: Int) { discs++ }
    }

    private fun count(themeId: String, change: (SceneCustomization) -> SceneCustomization): DotCounter {
        val scene = GoldenScene(name = "dots", dayPhase = GoldenScene.night(1f), themeId = themeId, customise = change)
        val renderer = PaperRenderer(width, height, context)
        scene.configure(renderer)
        return DotCounter().also { renderer.draw(it, scene.dayPhase, SceneTime(scene.sceneSeconds), 0f) }
    }

    /**
     * **And the snow, the stars and the lights are discs**: Winter at one in the morning with its snow, its stars, the
     * *Christmas lights* and the distant houses on (with their three bulbs each), then with each of the four off in turn --
     * each one off takes discs out of the frame and not a single circle, so none of them is drawn as a circle any more (a
     * fan of 24 vertices or more on the GPU), and each draws some (v5.14C: the distant houses, off as Winter ships, were
     * not in it).
     */
    @Test
    fun theSnowTheStarsAndTheLightsAreDiscs() {
        val houses = { c: SceneCustomization -> c.copy(distantHouses = c.distantHouses.copy(visible = true, density = 1f)) }
        val all = count("winter") { houses(it).copy(christmasDecorationsEnabled = true) }
        val noSnow = count("winter") { houses(it).copy(christmasDecorationsEnabled = true, precipitation = it.precipitation.copy(visible = false)) }
        val noStars = count("winter") { houses(it).copy(christmasDecorationsEnabled = true, stars = it.stars.copy(visible = false)) }
        val noLights = count("winter") { houses(it).copy(christmasDecorationsEnabled = false) }
        val noHouses = count("winter") { it.copy(christmasDecorationsEnabled = true) }
        println(
            "DiscAgreementTest Winter at 1:00: ${all.discs} discs and ${all.circles} circles; without the snow ${noSnow.discs} / ${noSnow.circles}, " +
                "without the stars ${noStars.discs} / ${noStars.circles}, without the lights ${noLights.discs} / ${noLights.circles}, " +
                "without the distant houses ${noHouses.discs} / ${noHouses.circles}",
        )
        for ((what, frame) in listOf("snow" to noSnow, "stars" to noStars, "Christmas lights" to noLights, "distant houses' bulbs" to noHouses)) {
            assertTrue("the $what drew no disc", frame.discs < all.discs)
            assertTrue("the $what drew ${all.circles - frame.circles} circle(s)", frame.circles == all.circles)
        }
    }

    /** The radius a dot's light stands for: the disc of that coverage at the dot's strength. */
    private fun radiusOf(l: Light, d: Dot): Double = sqrt(l.coverage / (Math.PI * d.alpha / 255.0))

    private companion object {
        const val CELL = 28
        val RADII = floatArrayOf(1.2f, 1.32f, 1.6f, 1.75f, 2.2f, 2.6f, 3.08f, 3.5f, 4.05f, 5f, 6.5f, 9f, 10.5f)
        val PHASES = floatArrayOf(0f, 0.25f, 0.5f, 0.75f)

        /**
         * How far apart the two may be: measured first on the BV6600 (2026-10-15) and set at what was seen, with a margin
         * -- from 5 px of radius up at most 1.6 % of light apart, below it 18.9 % at the worst phase and 13.5 % over the
         * four, the centres 0.16 px apart at most, a pixel 86 levels; see the class comment for why the small ones differ.
         * A disc drawn 5 % too wide or half a pixel aside fails all the same: under 5 px it is never more than 0.9 % brighter
         * than the circle at a phase and is darker on average over the four, so it may be 5 % brighter at a phase and
         * 3 % on average and no more (v5.14C: until then a small disc could be some 20 % larger than today's and pass).
         */
        const val LARGE_PX = 5f
        const val LARGE_SHARE = 0.03
        const val SMALL_SHARE = 0.22
        const val SMALL_MEAN_SHARE = 0.15
        const val SMALL_BRIGHTER = 0.05
        const val SMALL_MEAN_BRIGHTER = 0.03
        const val CENTRE_PX = 0.25
        const val EDGE_LEVELS = 100
    }
}
