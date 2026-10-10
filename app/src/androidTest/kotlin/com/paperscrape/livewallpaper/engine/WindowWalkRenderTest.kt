package com.paperscrape.livewallpaper.engine

import android.graphics.BitmapFactory
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * **On the real renderer, nobody at a window appears or disappears from one frame to the next** (v5.12,
 * [WindowWalk]; the maintainer's *«le persone devono "camminare" e uscire dalla visuale finestre, non
 * sparire»* and *«va fatto anche per la chiusura»*, 2026-10-09).
 *
 * `WindowWalkTest` holds the model to it on the JVM; this holds the drawing: the scene drawn frame after
 * frame through a canvas that keeps the transform, and every window bust's fixed layer measured as it
 * reaches the screen -- its quad, cut to its box where it is cut -- so the **area of people in sight**
 * is read off the frame itself. A person who pops costs a whole bust in one frame; a walker, a sliver.
 * Across a roster's walk (Autumn at night, started where somebody walks) and across a closing (the
 * hour moved on a minute at a time as the real clock moves it, if many times faster), the area may change
 * by no more than half the smallest bust seen, frame to frame -- several walkers at once included, as at a
 * closing every shop and tower of a street keeps the same hours (measured on the BV6600: up to 10 px2 a
 * frame, the smallest bust 27 px2); and a walk is seen to happen (cut blits counted).
 */
@RunWith(AndroidJUnit4::class)
class WindowWalkRenderTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** The fixed layer of the four window busts: one blit of it is one person seen through one pane. */
    private val bustFixed: Set<Int> = PeopleLayerTable.WINDOW.map { it[PeopleLayerTable.FIXED] }.toSet()

    /** Keeps the transform, and adds up the screen area of every bust's fixed layer, cut or whole. */
    private inner class AreaRecorder : SceneCanvas {
        private val stack = ArrayList<FloatArray>()
        private var m = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f) // a c / b d / tx ty: x' = a x + c y + tx
        private val sizes = HashMap<Int, IntArray>()
        var area = 0.0
        var smallestBust = Double.MAX_VALUE
        var cut = 0

        fun reset() { area = 0.0; cut = 0; stack.clear(); m = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f) }

        override fun save() { stack += m.copyOf() }
        override fun restore() { m = stack.removeAt(stack.size - 1) }
        override fun translate(dx: Float, dy: Float) { m[4] += m[0] * dx + m[2] * dy; m[5] += m[1] * dx + m[3] * dy }
        override fun scale(sx: Float, sy: Float) { m[0] *= sx; m[1] *= sx; m[2] *= sy; m[3] *= sy }
        override fun rotate(degrees: Float) {
            val r = Math.toRadians(degrees.toDouble()); val c = kotlin.math.cos(r).toFloat(); val s = kotlin.math.sin(r).toFloat()
            val a = m[0] * c + m[2] * s; val b = m[1] * c + m[3] * s; val cc = -m[0] * s + m[2] * c; val d = -m[1] * s + m[3] * c
            m[0] = a; m[1] = b; m[2] = cc; m[3] = d
        }
        private fun x(px: Float, py: Float) = m[0] * px + m[2] * py + m[4]
        private fun y(px: Float, py: Float) = m[1] * px + m[3] * py + m[5]

        private fun size(res: Int, source: SpriteSource): IntArray = sizes.getOrPut(res) {
            val options = BitmapFactory.Options().apply { inScaled = false }
            val b = BitmapFactory.decodeResource(context.resources, res, options)
            intArrayOf(b.width, b.height).also { b.recycle() }
        }

        private fun add(res: Int, source: SpriteSource, left: Float, top: Float, box: FloatArray?) {
            if (res !in bustFixed) return
            val (w, h) = size(res, source).let { it[0] to it[1] }
            var l = left; var t = top; var r = left + w; var b = top + h
            if (box != null) { l = max(l, box[0]); t = max(t, box[1]); r = min(r, box[2]); b = min(b, box[3]) }
            if (l >= r || t >= b) return
            val x0 = x(l, t); val x1 = x(r, b); val y0 = y(l, t); val y1 = y(r, b)
            val a = abs(x1 - x0).toDouble() * abs(y1 - y0)
            area += a
            if (box == null) smallestBust = min(smallestBust, a)
        }

        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) = Unit
        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) = Unit
        override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
        override fun drawWedge(cx: Float, cy: Float, radius: Float, startAngle: Float, sweepAngle: Float, paint: Paint) = Unit
        override fun drawShape(shape: SceneShape, paint: Paint) = Unit
        override fun drawVerticalGradientShape(shape: SceneShape, gradientTopY: Float, gradientBottomY: Float, topColor: Int, bottomColor: Int, alpha: Int) = Unit
        override fun drawVerticalGradientRect(left: Float, top: Float, right: Float, bottom: Float, topColor: Int, bottomColor: Int) = Unit
        override fun drawRadialGlow(cx: Float, cy: Float, radius: Float, color: Int, centerAlpha: Int) = Unit
        override fun drawSprite(resId: Int, source: SpriteSource, left: Float, top: Float, tintColor: Int, alpha: Int, additive: Boolean) =
            add(resId, source, left, top, null)
        override fun drawSpriteClipped(
            resId: Int, source: SpriteSource, left: Float, top: Float, tintColor: Int, alpha: Int, additive: Boolean,
            clipLeft: Float, clipTop: Float, clipRight: Float, clipBottom: Float,
        ) {
            if (resId in bustFixed) cut++
            add(resId, source, left, top, floatArrayOf(clipLeft, clipTop, clipRight, clipBottom))
        }
    }

    private fun renderer(customise: (SceneCustomization) -> SceneCustomization = { it }) = PaperRenderer(WIDTH, HEIGHT, context).apply {
        theme = ThemeCatalog.byId("autumn")
        sceneCustomization = customise(defaultCustomizationFor("autumn"))
        lightningStrikesEnabled = false
        scrollSpeed = 0f
    }

    /** Draws [frames] frames from [start], [phaseAt] the day phase of each, and checks the area in sight frame to frame. */
    private fun watch(label: String, r: PaperRenderer, start: Double, frames: Int, phaseAt: (Int) -> SunPositionCalculator.DayPhase): Int {
        val rec = AreaRecorder()
        var time = SceneTime(start)
        var previous = -1.0
        var cutFrames = 0
        var worst = 0.0
        for (i in 0 until frames) {
            time += FRAME
            rec.reset()
            r.draw(rec, phaseAt(i), time, FRAME)
            if (rec.cut > 0) cutFrames++
            if (previous >= 0) worst = max(worst, abs(rec.area - previous))
            previous = rec.area
        }
        val limit = rec.smallestBust / 2
        println("WindowWalkRenderTest $label: worst change ${"%.1f".format(worst)} px2 a frame, smallest bust ${"%.1f".format(rec.smallestBust)} px2, $cutFrames frames with a cut bust")
        assertTrue("$label: the people in sight changed by $worst px2 in one frame, a bust is ${rec.smallestBust}", worst <= limit)
        return cutFrames
    }

    @Test
    fun aRosterWalkIsSeenWholeFromWindowToWindow() {
        val night = SunPositionCalculator.compute(hour24 = 23f)
        // Where somebody walks: the first second at which a cut bust is drawn.
        val probe = AreaRecorder()
        val scout = renderer()
        var start = -1.0
        for (second in 30..900) {
            probe.reset()
            scout.draw(probe, night, SceneTime(second.toDouble()), FRAME)
            if (probe.cut > 0) { start = second.toDouble(); break }
        }
        assertTrue("nobody walked in 15 minutes", start > 0)
        val r = renderer()
        // A fresh renderer settles, then the frames run across the walk: twenty seconds from just before it.
        val cutFrames = watch("roster", r, start - 3.0, 600) { night }
        assertTrue("the walk was not drawn ($cutFrames frames)", cutFrames > 30)
    }

    @Test
    fun aClosingIsWalkedOutOneAtATime() {
        val r = renderer {
            it.copy(shopHoursEnabled = true, shopOpenHour = 9f, shopCloseHour = 20f, towerHoursEnabled = true, towerOpenHour = 9f, towerCloseHour = 20f)
        }
        // From 18:40 to 20:10, a minute every fifteen frames: the closing of every shop, school, bar and tower.
        val minutes = (0..90).map { SunPositionCalculator.compute(hour24 = 18f + (40 + it) / 60f) }
        val cutFrames = watch("closing", r, 0.0, minutes.size * 15) { minutes[it / 15] }
        assertTrue("nobody walked out ($cutFrames frames)", cutFrames > 30)
    }

    @Test
    fun anOpeningIsWalkedInOneAtATime() {
        val r = renderer {
            it.copy(shopHoursEnabled = true, shopOpenHour = 9f, shopCloseHour = 20f, towerHoursEnabled = true, towerOpenHour = 9f, towerCloseHour = 20f)
        }
        // From 08:50 to 10:30, a minute every fifteen frames.
        val minutes = (0..100).map { SunPositionCalculator.compute(hour24 = 8f + (50 + it) / 60f) }
        val cutFrames = watch("opening", r, 0.0, minutes.size * 15) { minutes[it / 15] }
        assertTrue("nobody walked in ($cutFrames frames)", cutFrames > 30)
    }

    private companion object {
        const val WIDTH = 720
        const val HEIGHT = 1440
        const val FRAME = 1f / 30f
    }
}
