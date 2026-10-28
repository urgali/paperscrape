package com.paperscrape.livewallpaper.engine

import android.graphics.Paint
import kotlin.math.sin

/**
 * About once a minute of wallpaper on screen, Santa's sleigh -- pulled by two reindeer -- glides across the upper
 * sky, dropping a little trail of falling gifts as it goes. Purely decorative and self-contained, following the same
 * "periodic event" pattern as [FireworkEffect].
 *
 * **The rhythm** is `DESIGN_NOTES.md` §12, decision 73's: the next flight sets off 25 to 60 s after the last one
 * ends, and a flight lasts 9 to 13 s, so a pass comes every 34 to 73 s of wallpaper on screen, 49 to 106 an hour; the
 * line under *Santa* says "about once a minute".
 *
 * The group itself is drawn by the caller through [draw]'s `spriteDraw` ([SantaSleighDrawer], from its pieces); only
 * the falling gifts are drawn here. They leave from the presents behind Santa and keep some of the
 * sleigh's speed as they fall, at the group's scale ([setGroupGeometry]).
 */
class SantaSleighEffect {

    /**
     * Blits the sleigh: its centre, its facing (+1/-1) and its fade alpha. A `fun interface` with
     * `Float` parameters rather than a function type, which would box all four floats on every
     * call (see [FireworkEffect.BurstBlit]).
     */
    fun interface SleighBlit {
        fun blit(x: Float, y: Float, dir: Float, alpha: Float)
    }

    private data class FallingGift(
        val x: Float,
        val startY: Float,
        /** The sleigh's speed across it keeps for a moment, in px a second, slowing to a stop as it lands. */
        val vx: Float,
        /** Half its side, in px: at the group's scale. */
        val half: Float,
        val targetY: Float,
        val fallDuration: Float,
        var age: Float = 0f,
        val colorIndex: Int,
    ) {
        /** Eased (progress²) fall -- starts slow right after being tossed, accelerates like
         * gravity, and lands exactly on [targetY] after [fallDuration], regardless of screen
         * size (both Y values are already absolute pixel positions computed from the actual
         * screen height at spawn time). */
        val y: Float
            get() {
                val progress = (age / fallDuration).coerceIn(0f, 1f)
                return startY + (targetY - startY) * progress * progress
            }

        /** Where it is across: the speed it left the sleigh with, falling to nothing by the end of the fall. */
        val xNow: Float
            get() {
                val a = age.coerceIn(0f, fallDuration)
                return x + vx * a * (1f - a / (2f * fallDuration))
            }
    }

    private var flying = false
    private var flightProgress = 0f // 0..1 across the screen
    private var flightDuration = 10f
    private var flightY = 0f
    private var reverse = false
    private var timeUntilNextFlight = 8f
    private var giftDropTimer = 0f
    private val fallingGifts = mutableListOf<FallingGift>()

    /** How many gifts are falling now (`PaperRenderer.santaGiftsFallingForTest`). */
    internal val giftsFalling: Int get() = fallingGifts.size

    /**
     * Seconds until the sleigh is in the sky: 0 from the moment it sets off to the end of its crossing, out of sight past
     * the edge of the screen; its countdown to the next flight while it waits. What the Headless Horseman reads to keep out of its way
     * (`HorsemanFlight.SKY_NEEDED_SECONDS`); read only: the sleigh's rhythm is its own and waits for nobody.
     */
    internal val secondsToFlight: Float get() = if (flying) 0f else maxOf(0f, timeUntilNextFlight)

    /**
     * The group's geometry on this screen, in px ([setGroupGeometry]): where a gift leaves the presents behind
     * Santa from the flight point (for a group flying left), the gifts' half side, and how far off each edge of the
     * screen a flight starts and ends -- the group's half width or [MIN_MARGIN_PX], whichever is more, so a larger
     * group on a larger screen still sets off and lands out of sight.
     */
    private var sackDxPx = 0f
    private var sackDyPx = 10f
    private var giftHalfPx = 11f
    private var marginPx = MIN_MARGIN_PX

    /**
     * For the tests that count what a frame of the sleigh in flight allocates and packs into the atlas: from 0 to 1,
     * the flight held at that point of its crossing at every update; negative, the sleigh flies as it does on the
     * wallpaper. Nothing in the app sets it.
     */
    internal var heldProgress = -1f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val giftBowShape = SceneShape(8)

    private val giftBoxColors = intArrayOf(0xFFC1443B.toInt(), 0xFF3D5A9E.toInt(), 0xFF3F7A5C.toInt())
    private val giftRibbonColor = 0xFFF2C230.toInt()

    /**
     * The group's size on this screen, from the renderer whenever the screen's size changes: where a gift
     * leaves the presents ([sackDx], [sackDy] px from the flight point, for a group flying left), its half side
     * ([giftHalf] px), and the group's half width ([groupHalfWidth] px), which keeps a flight starting and ending out
     * of sight.
     */
    fun setGroupGeometry(sackDx: Float, sackDy: Float, giftHalf: Float, groupHalfWidth: Float) {
        sackDxPx = sackDx
        sackDyPx = sackDy
        giftHalfPx = giftHalf
        marginPx = maxOf(MIN_MARGIN_PX, groupHalfWidth)
    }

    fun update(deltaSeconds: Float, enabled: Boolean, screenWidth: Float, screenHeight: Float) {
        // Falling gifts keep animating even if the flight itself is disabled mid-drop. By index,
        // from the end so a removal does not skip the next gift: an iterator here would be an object
        // every frame, gifts or none. `removeAt` keeps the others in order.
        for (i in fallingGifts.size - 1 downTo 0) {
            val g = fallingGifts[i]
            g.age += deltaSeconds
            if (g.age >= g.fallDuration) fallingGifts.removeAt(i)
        }

        if (!enabled) {
            flying = false
            return
        }
        if (heldProgress >= 0f) {
            if (!flying) startFlight(screenHeight)
            flightProgress = heldProgress
        }

        if (flying) {
            flightProgress += deltaSeconds / flightDuration
            giftDropTimer -= deltaSeconds
            if (giftDropTimer <= 0f && flightProgress in 0.1f..0.9f) {
                spawnFallingGift(screenWidth, screenHeight)
                giftDropTimer = 0.9f + SceneRandom.source.nextFloat() * 0.6f
            }
            if (heldProgress >= 0f) {
                flightProgress = heldProgress
            } else if (flightProgress >= 1f) {
                flying = false
                timeUntilNextFlight = 25f + SceneRandom.source.nextFloat() * 35f // next flight in ~25-60s
            }
        } else {
            timeUntilNextFlight -= deltaSeconds
            if (timeUntilNextFlight <= 0f) {
                startFlight(screenHeight)
            }
        }
    }

    private fun startFlight(screenHeight: Float) {
        flying = true
        flightProgress = 0f
        flightDuration = 9f + SceneRandom.source.nextFloat() * 4f
        flightY = screenHeight * (0.10f + SceneRandom.source.nextFloat() * 0.16f)
        reverse = SceneRandom.source.nextBoolean()
        giftDropTimer = 1.2f
    }

    private fun spawnFallingGift(screenWidth: Float, screenHeight: Float) {
        val x = currentX(screenWidth)
        // Lands on the pavement, the band derived from PAVEMENT_FAR/NEAR rather than restated, so it
        // moves with the road. Houses stop at OBJECT_BAND_BOTTOM_Y_FRACTION (0.790), the painted
        // carriageway runs 0.8178..0.8782 and its two wheel lines sit at 0.834 and 0.862; this effect
        // draws after the cars, so a gift aimed at the road would land over a car roof whenever a
        // drop met traffic.
        val targetY = screenHeight * (
            SceneSpace.PAVEMENT_FAR_Y_FRACTION +
                SceneRandom.source.nextFloat() *
                (SceneSpace.PAVEMENT_NEAR_Y_FRACTION - SceneSpace.PAVEMENT_FAR_Y_FRACTION)
            )
        // From the presents behind Santa ([giftStartX]), and with half the sleigh's speed across, the way it flies,
        // which it loses as it falls.
        val speed = (screenWidth + marginPx * 2f) / flightDuration * motion(reverse)
        fallingGifts.add(
            FallingGift(
                x = giftStartX(x, sackDxPx, reverse),
                startY = flightY + sackDyPx,
                vx = speed * GIFT_KEEPS_SPEED,
                half = giftHalfPx,
                targetY = targetY,
                fallDuration = 1.6f + SceneRandom.source.nextFloat() * 0.5f,
                colorIndex = SceneRandom.source.nextInt(giftBoxColors.size),
            ),
        )
    }

    private fun currentX(screenWidth: Float): Float {
        val travel = screenWidth + marginPx * 2f
        val raw = flightProgress * travel - marginPx
        return if (reverse) screenWidth - raw else raw
    }

    /** Fades in over the first ~8% of the flight and back out over the last ~8%, so the group
     * does not just vanish the instant it crosses off-canvas. */
    private fun edgeFadeAlpha(progress: Float): Float {
        val fadeZone = 0.08f
        return when {
            progress < fadeZone -> (progress / fadeZone).coerceIn(0f, 1f)
            progress > 1f - fadeZone -> ((1f - progress) / fadeZone).coerceIn(0f, 1f)
            else -> 1f
        }
    }

    /** Combines a base ARGB color with an extra alpha multiplier, for the falling gifts' fade.
     * Used instead of `Canvas.saveLayer`, which allocates an offscreen buffer on every call and
     * is expensive enough to cause visible stutter when done every frame. Multiplying each
     * paint's own alpha is essentially free by comparison. The sleigh's own edge fade reaches
     * the caller as the blit alpha. */
    private fun fadeColor(color: Int, alpha: Float): Int {
        val baseAlpha = (color ushr 24) and 0xFF
        val newAlpha = (baseAlpha * alpha).toInt().coerceIn(0, 255)
        return (newAlpha shl 24) or (color and 0x00FFFFFF)
    }

    /**
     * The group itself is drawn from sprites -- its pieces, by [SantaSleighDrawer] -- and this class has
     * no [android.content.Context]/[SpriteCache] access of its own, so the drawing is delegated back to the caller
     * (which already has it) via [spriteDraw]. Falling gifts stay vector here -- they're cheap (one rect + a ribbon
     * cross + two small triangles, only while any are actually falling) and their per-gift fade/rotation state lives
     * entirely in this class.
     *
     * [spriteDraw] receives the sleigh's current center (x, y), its facing direction (+1/-1,
     * already accounting for [reverse]), and its current fade alpha (0..1) -- called once per
     * frame only while actually flying and not fully faded out.
     */
    fun draw(canvas: SceneCanvas, elapsedSeconds: SceneTime, screenWidth: Float, spriteDraw: SleighBlit) {
        // By index: a `for (g in fallingGifts)` builds an iterator every frame.
        for (i in fallingGifts.indices) {
            val g = fallingGifts[i]
            val progress = (g.age / g.fallDuration).coerceIn(0f, 1f)
            // Fades in quickly right after being thrown, stays solid through the flight, then
            // fades out over the last bit of the fall so it reads as "arriving" near the house
            // rather than blinking out.
            val fade = when {
                progress < 0.08f -> progress / 0.08f
                progress > 0.85f -> ((1f - progress) / 0.15f).coerceIn(0f, 1f)
                else -> 1f
            }
            drawFallingGift(canvas, g, fade)
        }

        if (!flying) return
        val x = currentX(screenWidth)
        // The sleigh faces left -- the reindeer are drawn at the left end of the group and pull away
        // from the sleigh -- so the unflipped group is the leftward-travelling one (`reverse`: the
        // flight runs from the right edge to the left).
        val dir = -motion(reverse)
        val bob = elapsedSeconds.sinAt(3f) * 4f
        val fadeAlpha = edgeFadeAlpha(flightProgress)
        if (fadeAlpha <= 0.01f) return // fully faded out, nothing to draw
        spriteDraw.blit(x, flightY + bob, dir, fadeAlpha)
    }

    /**
     * A gift at its half side [FallingGift.half] (the group's scale), the
     * ribbon and the bow in proportion to it -- the ribbon never narrower than [MIN_RIBBON_PX].
     */
    private fun drawFallingGift(canvas: SceneCanvas, g: FallingGift, fade: Float) {
        val h = g.half
        val r = maxOf(h * RIBBON_OF_HALF, MIN_RIBBON_PX * 0.5f)
        val bow = h * BOW_OF_HALF
        canvas.save()
        canvas.translate(g.xNow, g.y)
        canvas.rotate(g.age * GIFT_TURN_DEGREES_PER_SECOND)
        fillPaint.color = fadeColor(giftBoxColors[g.colorIndex], fade)
        canvas.drawRect(-h, -h, h, h, fillPaint)
        // Ribbon cross + a small bow on top, matching the static gift-under-the-tree look --
        // a flat, unadorned square reads as a bomb/mine rather than a wrapped present.
        fillPaint.color = fadeColor(giftRibbonColor, fade)
        canvas.drawRect(-r, -h, r, h, fillPaint)
        canvas.drawRect(-h, -r, h, r, fillPaint)
        giftBowShape.moveTo(0f, -h); giftBowShape.lineTo(-bow, -h - bow * 1.2f); giftBowShape.lineTo(-r * 0.5f, -h); giftBowShape.close()
        canvas.drawShape(giftBowShape, fillPaint)
        giftBowShape.moveTo(0f, -h); giftBowShape.lineTo(bow, -h - bow * 1.2f); giftBowShape.lineTo(r * 0.5f, -h); giftBowShape.close()
        canvas.drawShape(giftBowShape, fillPaint)
        canvas.restore()
    }

    companion object {
        /** The least distance off each edge of the screen a flight starts and ends at, in px. */
        const val MIN_MARGIN_PX = 160f

        /** Which way a flight crosses the screen: -1 to the left ([reverse], the drawings' own way), +1 to the right. */
        internal fun motion(reverse: Boolean): Float = if (reverse) -1f else 1f

        /**
         * Where a gift leaves the presents behind Santa, from the flight point [x]: [sackDxPx] to the right of it for a
         * group flying left (its drawings' own way, `SantaSleighDrawer.sackDxPx`), to the left when mirrored -- the
         * side the group comes from.
         */
        internal fun giftStartX(x: Float, sackDxPx: Float, reverse: Boolean): Float = x - sackDxPx * motion(reverse)

        /** How much of the sleigh's speed across a gift leaves with. */
        const val GIFT_KEEPS_SPEED = 0.5f

        /** How fast a gift turns as it falls, in degrees a second. */
        const val GIFT_TURN_DEGREES_PER_SECOND = 60f

        /** The ribbon's half width and the bow's size, as fractions of the gift's half side. */
        const val RIBBON_OF_HALF = 2f / 11f
        const val BOW_OF_HALF = 5f / 11f

        /** The ribbon's width is never under 2 px, the smallest detail the scene draws anywhere. */
        const val MIN_RIBBON_PX = 2f
    }

}
