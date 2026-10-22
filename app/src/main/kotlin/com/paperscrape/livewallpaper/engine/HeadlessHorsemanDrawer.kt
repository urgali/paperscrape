package com.paperscrape.livewallpaper.engine

import android.graphics.Paint
import kotlin.math.min

/**
 * How large the Headless Horseman is drawn (v5.16): **in proportion to the people**, by the sleigh's rule
 * ([SantaSleighScale]) -- the rider, what is seen of him from the top of his collar to the bottom of his boot,
 * [HorsemanTable.RIDER_TO_PEDESTRIAN] times an adult walking on the near row of the pavement, as Santa is. Never drawn
 * larger than its sprites' own detail, [SantaSleighScale.MAX_PX_PER_UNIT].
 */
internal object HeadlessHorsemanScale {

    /** The rider's height in the group's units: the top of his collar to the bottom of his boot, what is seen of him. */
    val RIDER_UNITS_TALL: Float get() = HorsemanTable.RIDER_BOTTOM - HorsemanTable.RIDER_TOP

    /** Pixels a unit of the group on a screen [screenHeightPx] tall. */
    fun pxPerUnit(screenHeightPx: Float): Float =
        min(
            HorsemanTable.RIDER_TO_PEDESTRIAN * SantaSleighScale.pedestrianPx(screenHeightPx) / RIDER_UNITS_TALL,
            SantaSleighScale.MAX_PX_PER_UNIT,
        )

    /**
     * The Horseman's scale against the sleigh's, on any screen where neither is held at the sprites' detail: both follow
     * the pedestrian, so the ratio is a constant -- what the gallery card draws him at against its sleigh. Read on the
     * BV6600's height, where neither is held.
     */
    val toSleighScale: Float
        get() = pxPerUnit(RATIO_SCREEN_PX) / SantaSleighScale.pxPerUnit(RATIO_SCREEN_PX)

    /** A screen's height on which neither group is held at its sprites' detail: the BV6600's. */
    private const val RATIO_SCREEN_PX = 1440f
}

/**
 * Draws the Headless Horseman (v5.16, the drawing B of v5.16A, "the rider of Sleepy Hollow") from its pieces: a
 * slate-black horse galloping in four poses, its legs drawn behind its body, the headless rider in a dark coat with a
 * high cream collar where his head should be, his black cape lined in red swinging about his shoulders, the carved
 * pumpkin lit and held up high, and the reins from his hand to the horse's bit. The pieces and where they stand are
 * [HorsemanTable], written by `tools/assets/horseman/build_horseman.py`; the flight, the fade and the flaming pumpkins
 * that fall are [HeadlessHorsemanEffect]'s.
 *
 * - **The gallop**: four poses, [POSES_PER_SECOND] -- the sleigh's reindeer's beat --; the horse rises and falls with
 *   its stride and the rider sits it the other way, half as far ([HORSE_BOB_UNITS], [RIDER_BOB_UNITS]); the cape swings
 *   [CAPE_SWING_DEGREES] either way about the shoulders on the same stride.
 * - **By night as by day**, as the sleigh (decision 73): no shade on the group.
 * - **The order** is the cape, the legs, the horse, the reins, the rider -- what `tools/assets/tests/test_horseman.py`
 *   reads off this function to compose the group as it is drawn, and holds to 2 px on the BV6600 in every pose.
 *
 * Nothing is allocated while drawing: the paint is built once, the table is read by index, the alpha is set with
 * [setAlphaWithoutAllocating] (`Paint.alpha =` allocates on Android 10).
 */
internal class HeadlessHorsemanDrawer(private val sprites: SpriteBlitter) {

    private val rein = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = HorsemanTable.REIN_COLOUR
    }

    /** Half the group's width in px at [pxPerUnit], for the effect's margins off the screen's edges. */
    fun halfWidthPx(pxPerUnit: Float): Float = (HorsemanTable.RIGHT - HorsemanTable.LEFT) * 0.5f * pxPerUnit

    /**
     * The group with its middle at ([x], [y]), facing [dir] (+1 to the left, the drawings' own way; -1 mirrored, to
     * the right), at [alpha] (the edge fade), at [pxPerUnit] ([HeadlessHorsemanScale.pxPerUnit]).
     */
    fun draw(canvas: SceneCanvas, x: Float, y: Float, dir: Float, alpha: Float, t: SceneTime, pxPerUnit: Float) {
        val sc = pxPerUnit
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        val pose = pose(t)
        val stride = t.sinAt(STRIDE_RATE)
        val horseBob = stride * HORSE_BOB_UNITS
        val riderBob = -stride * RIDER_BOB_UNITS
        val seatX = HorsemanTable.SEAT_X[pose]
        val seatY = HorsemanTable.SEAT_Y[pose] + riderBob
        // 1. the cape, swinging about the rider's shoulders
        canvas.save()
        canvas.translate(px(x, seatX + HorsemanTable.SHOULDER_X, dir, sc), y + (seatY + HorsemanTable.SHOULDER_Y) * sc)
        canvas.scale(dirScale(dir) * sc, sc)
        canvas.rotate(t.sinAt(STRIDE_RATE, CAPE_PHASE) * CAPE_SWING_DEGREES)
        sprites.draw(canvas, HorsemanTable.CAPE_RES, HorsemanTable.CAPE_X, HorsemanTable.CAPE_Y, SpriteScale.SCENE_UNITS, a)
        canvas.restore()
        // 2. the legs, behind the body, and 3. the horse
        piece(canvas, x, y, dir, sc, a, HorsemanTable.LEGS_RES[pose], HorsemanTable.LEGS_X[pose], HorsemanTable.LEGS_Y[pose] + horseBob)
        piece(canvas, x, y, dir, sc, a, HorsemanTable.HORSE_RES[pose], HorsemanTable.HORSE_X[pose], HorsemanTable.HORSE_Y[pose] + horseBob)
        // 4. the reins, from the rider's hand to the bit
        rein.strokeWidth = HorsemanTable.REIN_UNITS * sc
        rein.setAlphaWithoutAllocating(a)
        canvas.drawLine(
            px(x, seatX + HorsemanTable.HAND_X, dir, sc), y + (seatY + HorsemanTable.HAND_Y) * sc,
            px(x, HorsemanTable.BIT_X[pose], dir, sc), y + (HorsemanTable.BIT_Y[pose] + horseBob) * sc,
            rein,
        )
        // 5. the rider, with his pumpkin
        piece(canvas, x, y, dir, sc, a, HorsemanTable.RIDER_RES, seatX + HorsemanTable.RIDER_X, seatY + HorsemanTable.RIDER_Y)
    }

    private fun piece(canvas: SceneCanvas, x: Float, y: Float, dir: Float, sc: Float, a: Int, res: Int, u: Float, v: Float) {
        canvas.save()
        canvas.translate(px(x, u, dir, sc), y + v * sc)
        canvas.scale(dirScale(dir) * sc, sc)
        sprites.draw(canvas, res, 0f, 0f, SpriteScale.SCENE_UNITS, a)
        canvas.restore()
    }

    /** +1 keeps the drawings (they face left: the group flies left), -1 mirrors them. */
    private fun dirScale(dir: Float) = if (dir < 0f) -1f else 1f

    private fun px(x: Float, u: Float, dir: Float, sc: Float) = screenX(x, u, dir, sc)

    /** Which of the four poses the horse is in. */
    internal fun pose(t: SceneTime) = t.frameIndex(POSES_PER_SECOND, 0f, 4)

    companion object {
        /** The group's middle, in its units: the flight point is drawn there. */
        val MIDDLE: Float = (HorsemanTable.LEFT + HorsemanTable.RIGHT) * 0.5f

        /** A group unit [u]'s x on the screen at [sc] px a unit: the group's middle on [x], mirrored when [dir] is -1 (flying right). */
        fun screenX(x: Float, u: Float, dir: Float, sc: Float): Float = x + (u - MIDDLE) * sc * (if (dir < 0f) -1f else 1f)

        /** How fast the gallop goes, in poses a second: a stride of four poses in two thirds of a second, the reindeer's. */
        const val POSES_PER_SECOND = 6f

        /** The stride's rate: once a stride (6 poses a second over 4 is 1.5 strides, 9.42 rad/s), as the reindeer's bob. */
        const val STRIDE_RATE = 9.424778f

        /** How far the horse rises and falls with its stride, and the rider the other way, in units. */
        const val HORSE_BOB_UNITS = 1.2f
        const val RIDER_BOB_UNITS = 0.6f

        /** How far the cape swings either way about the shoulders, in degrees, a little behind the stride. */
        const val CAPE_SWING_DEGREES = 4f
        const val CAPE_PHASE = 1.2f
    }
}
