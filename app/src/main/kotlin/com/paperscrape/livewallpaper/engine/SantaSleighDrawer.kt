package com.paperscrape.livewallpaper.engine

import android.graphics.Paint
import kotlin.math.min

/**
 * How large Santa's sleigh is drawn (v5.15): **in proportion to the people**, so on every phone Santa stands in the
 * same relation to an adult walking near the road.
 *
 * The maintainer, 2026-10-19, shown the new group at the size v5.15A photographed it (Santa about twice a pedestrian's height):
 * *«fallo più piccolo, così è gigante rispetto ai pedoni»*; then, from the sizes shown beside the people,
 * [SantaSleighTable.SANTA_TO_PEDESTRIAN]. A pedestrian's height follows the screen's height (`SceneSpace.sceneScale`),
 * so the group does too; until v5.14 it was 297 px on every phone. Never drawn larger than its sprites' own detail,
 * [MAX_PX_PER_UNIT].
 */
internal object SantaSleighScale {

    /** The sprites' own detail: 3 px a unit (`SpriteBlitter.SPRITE_PIXELS_PER_UNIT`), never exceeded. */
    const val MAX_PX_PER_UNIT = 3f

    /** An adult walking on the near row of the pavement, in px, on a screen [screenHeightPx] tall. */
    fun pedestrianPx(screenHeightPx: Float): Float =
        SceneSpace.PERSON_METRES_TALL * SceneSpace.pixelsPerMetre(screenHeightPx) *
            SceneSpace.perspectiveScaleAt(SceneSpace.PAVEMENT_NEAR_Y_FRACTION)

    /** Santa's height in the group's units: his hat's top to the sleigh's rim, what is seen of him. */
    val SANTA_UNITS_TALL: Float get() = SantaSleighTable.SANTA_BOTTOM - SantaSleighTable.SANTA_TOP

    /** Pixels a unit of the group on a screen [screenHeightPx] tall. */
    fun pxPerUnit(screenHeightPx: Float): Float =
        min(SantaSleighTable.SANTA_TO_PEDESTRIAN * pedestrianPx(screenHeightPx) / SANTA_UNITS_TALL, MAX_PX_PER_UNIT)
}

/**
 * Draws Santa's sleigh group (v5.15) from its pieces: two reindeer galloping in four poses, the cords, the sleigh with
 * Santa and the presents, and Rudolph's red nose. The pieces and where they stand are [SantaSleighTable], written by
 * `tools/assets/sleigh/build_sleigh.py`; the flight, the fade and the gifts are [SantaSleighEffect]'s.
 *
 * - **The reindeer gallop**: four poses each, [POSES_PER_SECOND], each on its own beat (the second half a stride
 *   behind), each bobbing with its stride; the sleigh sways a little behind them; the cords follow.
 * - **The cords keep every detail whole** (v5.15C2): the traces behind everything; the reins over the sleigh and behind
 *   the reindeer, from the top of Santa's fist to the front of each collar. The sleigh is drawn before the reindeer for
 *   that -- the two do not overlap. Until v5.15C2 the reins were drawn over everything and cut the glove, the sleigh's
 *   front scroll, Rudolph's bell and the second reindeer's head into pieces under 2 px on the BV6600.
 * - **By night as by day** (the maintainer, 2026-10-19: *«se ora di notte non è scuro, lascialo così»*): no shade on
 *   the group, as the sleigh before it; Rudolph's nose is the same red at every hour.
 * - **Rudolph is the second reindeer's drawing with a red nose over it** -- a disc of the atlas, by day and by night
 *   -- so the group has one series of poses (inventory I-940).
 *
 * Nothing is allocated while drawing: the paints are built once, the table is read by index, the alpha is set with
 * [setAlphaWithoutAllocating] (`Paint.alpha =` allocates on Android 10, inventory I-942).
 */
internal class SantaSleighDrawer(private val sprites: SpriteBlitter) {

    private val rein = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = SantaSleighTable.REIN_COLOUR
    }
    private val trace = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = SantaSleighTable.TRACE_COLOUR
    }

    /** The group's middle, in its units: the flight point is drawn there. */
    val middle: Float get() = MIDDLE

    /** Half the group's width in px at [pxPerUnit], for the effect's margins off the screen's edges. */
    fun halfWidthPx(pxPerUnit: Float): Float = (SantaSleighTable.RIGHT - SantaSleighTable.LEFT) * 0.5f * pxPerUnit

    /** Where a gift leaves the presents behind Santa, from the flight point, in px, for a group flying left. */
    fun sackDxPx(pxPerUnit: Float): Float = (SantaSleighTable.SACK_X - middle) * pxPerUnit
    fun sackDyPx(pxPerUnit: Float): Float = SantaSleighTable.SACK_Y * pxPerUnit

    /**
     * The group with its middle at ([x], [y]), facing [dir] (+1 to the left, the drawings' own way; -1 mirrored, to
     * the right), at [alpha] (the edge fade), at [pxPerUnit] ([SantaSleighScale.pxPerUnit]).
     */
    fun draw(canvas: SceneCanvas, x: Float, y: Float, dir: Float, alpha: Float, t: SceneTime, pxPerUnit: Float) {
        val sc = pxPerUnit
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        val sleighBob = t.sinAt(SLEIGH_BOB_RATE) * SANTA_SLEIGH_BOB_UNITS
        val leadPose = pose(LEAD, t)
        val nearPose = pose(NEAR, t)
        val leadBob = deerBob(LEAD, t)
        val nearBob = deerBob(NEAR, t)
        // 1. the traces, behind the reindeer: Rudolph's girth to the second's collar, the second's girth to the sleigh
        trace.strokeWidth = SantaSleighTable.SANTA_TRACE_UNITS * sc
        trace.setAlphaWithoutAllocating(a)
        line(
            canvas, x, y, dir, sc, trace,
            SantaSleighTable.LEAD_X + SantaSleighTable.GIRTH_X[leadPose], SantaSleighTable.LEAD_Y + SantaSleighTable.GIRTH_Y[leadPose] + leadBob,
            SantaSleighTable.NEAR_X + SantaSleighTable.COLLAR_X[nearPose], SantaSleighTable.NEAR_Y + SantaSleighTable.COLLAR_Y[nearPose] + nearBob,
        )
        line(
            canvas, x, y, dir, sc, trace,
            SantaSleighTable.NEAR_X + SantaSleighTable.GIRTH_X[nearPose], SantaSleighTable.NEAR_Y + SantaSleighTable.GIRTH_Y[nearPose] + nearBob,
            SantaSleighTable.FRONT_X, SantaSleighTable.FRONT_Y + sleighBob,
        )
        // 2. the sleigh with Santa and the presents
        canvas.save()
        canvas.translate(px(x, 0f, dir, sc), y + sleighBob * sc)
        canvas.scale(dirScale(dir) * sc, sc)
        sprites.draw(canvas, SantaSleighTable.SLEIGH_RES, SantaSleighTable.SLEIGH_X, SantaSleighTable.SLEIGH_Y, SpriteScale.SCENE_UNITS, a)
        canvas.restore()
        // 3. the reins, from the top of Santa's fist to the front of each collar: over the sleigh, behind the reindeer
        rein.strokeWidth = SantaSleighTable.SANTA_REIN_UNITS * sc
        rein.setAlphaWithoutAllocating(a)
        val hx = SantaSleighTable.HAND_X
        val hy = SantaSleighTable.HAND_Y + sleighBob
        line(canvas, x, y, dir, sc, rein, hx, hy,
            SantaSleighTable.LEAD_X + SantaSleighTable.REIN_X[leadPose], SantaSleighTable.LEAD_Y + SantaSleighTable.REIN_Y[leadPose] + leadBob)
        line(canvas, x, y, dir, sc, rein, hx, hy,
            SantaSleighTable.NEAR_X + SantaSleighTable.REIN_X[nearPose], SantaSleighTable.NEAR_Y + SantaSleighTable.REIN_Y[nearPose] + nearBob)
        // 4. the reindeer
        drawDeer(canvas, x, y, dir, sc, a, SantaSleighTable.LEAD_X, SantaSleighTable.LEAD_Y + leadBob, leadPose)
        drawDeer(canvas, x, y, dir, sc, a, SantaSleighTable.NEAR_X, SantaSleighTable.NEAR_Y + nearBob, nearPose)
        // 5. Rudolph's nose, over his own
        sprites.drawDisc(
            canvas,
            px(x, SantaSleighTable.LEAD_X + SantaSleighTable.NOSE_X[leadPose], dir, sc),
            y + (SantaSleighTable.LEAD_Y + SantaSleighTable.NOSE_Y[leadPose] + leadBob) * sc,
            SantaSleighTable.RUDOLPH_NOSE_RADIUS * sc, SantaSleighTable.RUDOLPH_NOSE_COLOUR, a,
        )
    }

    private fun drawDeer(canvas: SceneCanvas, x: Float, y: Float, dir: Float, sc: Float, a: Int, dx: Float, dy: Float, pose: Int) {
        canvas.save()
        canvas.translate(px(x, dx, dir, sc), y + dy * sc)
        canvas.scale(dirScale(dir) * sc, sc)
        sprites.draw(canvas, SantaSleighTable.DEER_RES[pose], SantaSleighTable.DEER_X[pose], SantaSleighTable.DEER_Y[pose], SpriteScale.SCENE_UNITS, a)
        canvas.restore()
    }

    private fun line(canvas: SceneCanvas, x: Float, y: Float, dir: Float, sc: Float, paint: Paint, u0: Float, v0: Float, u1: Float, v1: Float) {
        canvas.drawLine(px(x, u0, dir, sc), y + v0 * sc, px(x, u1, dir, sc), y + v1 * sc, paint)
    }

    /** +1 keeps the drawings (they face left: the group flies left), -1 mirrors them. */
    private fun dirScale(dir: Float) = if (dir < 0f) -1f else 1f

    private fun px(x: Float, u: Float, dir: Float, sc: Float) = screenX(x, u, dir, sc)

    /** Which of the four poses a reindeer is in: [LEAD] or [NEAR]. */
    internal fun pose(deer: Int, t: SceneTime) = t.frameIndex(POSES_PER_SECOND, DEER_PHASE[deer], 4)

    private fun deerBob(deer: Int, t: SceneTime) = t.sinAt(DEER_BOB_RATE, DEER_PHASE[deer] * QUARTER_TURN) * SANTA_DEER_BOB_UNITS

    companion object {
        const val LEAD = 0
        const val NEAR = 1

        /** The group's middle, in its units. */
        val MIDDLE: Float = (SantaSleighTable.LEFT + SantaSleighTable.RIGHT) * 0.5f

        /** A group unit [u]'s x on the screen at [sc] px a unit: the group's middle on [x], mirrored when [dir] is -1 (flying right). */
        fun screenX(x: Float, u: Float, dir: Float, sc: Float): Float = x + (u - MIDDLE) * sc * (if (dir < 0f) -1f else 1f)

        /** How fast the gallop goes, in poses a second: a stride of four poses in two thirds of a second. */
        const val POSES_PER_SECOND = 6f

        /** Each reindeer's beat, in poses: the second half a stride behind Rudolph. */
        val DEER_PHASE = floatArrayOf(0f, 2f)

        /** The reindeer's bob with their stride: once a stride (6 poses a second over 4 is 1.5 strides, 9.42 rad/s), 1.4 units. */
        const val DEER_BOB_RATE = 9.424778f
        const val SANTA_DEER_BOB_UNITS = 1.4f
        const val QUARTER_TURN = 1.5707964f

        /** The sleigh's slow sway behind the team: 2.2 rad/s, one unit. */
        const val SLEIGH_BOB_RATE = 2.2f
        const val SANTA_SLEIGH_BOB_UNITS = 1.0f
    }
}
