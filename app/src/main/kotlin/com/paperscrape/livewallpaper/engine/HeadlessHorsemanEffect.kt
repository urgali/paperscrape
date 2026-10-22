package com.paperscrape.livewallpaper.engine

import android.graphics.Paint
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Headless Horseman's flight (v5.16): about once a minute of wallpaper on screen he gallops across the upper sky,
 * as Santa's sleigh does ([SantaSleighEffect]), and small pumpkins on fire fall from him to the pavement and go out.
 *
 * **The rhythm is the sleigh's**: the first flight 8 s after the switch is on, a flight 9 to 13 s long, at 10 to 26 % of
 * the screen's height, the next one 25 to 60 s after the last ended -- a pass every 34 to 73 s of wallpaper on screen,
 * later when a turn waits for the sleigh (below): with *Santa* on too, about 59 passes an hour instead of 68; the line
 * under *Headless Horseman* says "about once a minute". The group fades in and out over the first and last 8 %
 * of its crossing, and is drawn by the caller through [draw]'s `riderDraw` ([HeadlessHorsemanDrawer]); the falling
 * pumpkins are drawn here.
 *
 * **He keeps out of Santa's sleigh's way** (inventory I-1048 and I-1068): he sets off only when the sky is his for the whole
 * of his crossing -- the sleigh neither in the sky nor due within [HorsemanFlight.SKY_NEEDED_SECONDS] -- and a turn that
 * comes otherwise waits until the sleigh has flown and gone, then sets off [HorsemanFlight.AFTER_SLEIGH_SECONDS] after it.
 * So the two are never in the sky together, and the sky is empty at least that long between them. The sleigh's rhythm is
 * its own and does not wait for him. The one way left for them to meet: *Santa* turned on while he is in flight, when the
 * sleigh's countdown is whatever it was left at.
 *
 * **The flaming pumpkins come from a fixed pool** ([HorsemanFlight.POOL_SIZE] slots of primitive arrays, filled and emptied in place):
 * a drop is a slot taken, not an object made, so a frame with the Horseman in flight allocates nothing, a drop included
 * (inventory I-1025; the sleigh's gifts are one object each, an event). A pumpkin falls in 1.6 to 2.1 s with half the
 * group's speed across it, which it loses as it lands; it turns as it falls, and three tongues of flame stand on it
 * upright whatever its turn, flickering. A drop is let fall every 1.1 to 1.9 s while the group is between 12 and 88 %
 * of its crossing, so at most two are in the air: a drop that finds every slot taken is not let fall.
 */
class HeadlessHorsemanEffect {

    /** Draws the group: its middle, its facing (+1/-1) and its fade alpha. A `fun interface`, as [SantaSleighEffect.SleighBlit]. */
    fun interface RiderBlit {
        fun blit(x: Float, y: Float, dir: Float, alpha: Float)
    }

    /** The flight and the pool of pumpkins: arithmetic of their own, which the JVM tests run (`HorsemanFlightTest`). */
    internal val flight = HorsemanFlight()

    /** How many pumpkins are falling now (for the tests that count a frame of the Horseman in flight). */
    internal val dropsFalling: Int get() = flight.dropsFalling

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val flameShape = SceneShape(4)

    /** See [HorsemanFlight.setGroupGeometry]. */
    fun setGroupGeometry(dropHalf: Float, groupHalfWidth: Float) = flight.setGroupGeometry(dropHalf, groupHalfWidth)

    /**
     * See [HorsemanFlight.update]; [sleighDueIn] is how soon Santa's sleigh is in the sky this frame
     * ([SantaSleighEffect.secondsToFlight], infinite with *Santa* off).
     */
    fun update(deltaSeconds: Float, enabled: Boolean, sleighDueIn: Float, screenWidth: Float, screenHeight: Float) =
        flight.update(deltaSeconds, enabled, sleighDueIn, screenWidth, screenHeight)

    /**
     * The falling pumpkins, then -- while he is in flight and not faded out -- the group through [riderDraw], with its
     * middle, its facing (+1 to the left, the drawings' way) and its fade.
     */
    fun draw(canvas: SceneCanvas, elapsedSeconds: SceneTime, screenWidth: Float, riderDraw: RiderBlit) {
        val f = flight
        for (i in 0 until HorsemanFlight.POOL_SIZE) if (f.dropActive[i]) drawDrop(canvas, i)
        if (!f.flying) return
        val x = f.currentX(screenWidth)
        val dir = -SantaSleighEffect.motion(f.reverse)
        val bob = elapsedSeconds.sinAt(FLIGHT_BOB_RATE) * FLIGHT_BOB_PX
        val fade = f.edgeFadeAlpha()
        if (fade <= 0.01f) return
        riderDraw.blit(x, f.flightY + bob, dir, fade)
    }

    /**
     * One pumpkin of the pool: its three flames first, upright whatever its turn and flickering (the maintainer's
     * *«le zucche che cadono devono essere in fiamme»*, 2026-10-21), orange round a yellow core; then the pumpkin, turning,
     * and its stem. It fades in as it leaves and out over the last part of its fall.
     */
    private fun drawDrop(canvas: SceneCanvas, i: Int) {
        val f = flight
        val age = f.dropAge[i]
        val dur = f.dropDuration[i]
        val p = (age / dur).coerceIn(0f, 1f)
        val fade = when {
            p < 0.08f -> p / 0.08f
            p > 0.85f -> ((1f - p) / 0.15f).coerceIn(0f, 1f)
            else -> 1f
        }
        val a = (fade * 255).toInt()
        val h = f.dropHalfPx
        canvas.save()
        canvas.translate(f.dropXNow(i), f.dropYNow(i))
        for (k in 0 until 3) {
            val fx = (k - 1) * h * 0.5f
            val tall = h * (1.25f + 0.45f * sin(age * 17f + k * 2.1f)) * (if (k == 1) 1.3f else 1f)
            val base = -h * 0.55f
            fillPaint.color = FLAME_OUTER
            fillPaint.setAlphaWithoutAllocating(a)
            flameShape.moveTo(fx - h * 0.42f, base)
            flameShape.lineTo(fx + h * 0.42f, base)
            flameShape.lineTo(fx + h * 0.08f * sin(age * 11f + k), base - tall)
            flameShape.close()
            canvas.drawShape(flameShape, fillPaint)
            fillPaint.color = FLAME_INNER
            fillPaint.setAlphaWithoutAllocating(a)
            flameShape.moveTo(fx - h * 0.22f, base)
            flameShape.lineTo(fx + h * 0.22f, base)
            flameShape.lineTo(fx, base - tall * 0.55f)
            flameShape.close()
            canvas.drawShape(flameShape, fillPaint)
        }
        canvas.rotate(age * DROP_TURN_DEGREES_PER_SECOND)
        fillPaint.color = PUMPKIN_COLOURS[f.dropColour[i]]
        fillPaint.setAlphaWithoutAllocating(a)
        canvas.drawOval(-h, -h * 0.84f, h, h * 0.84f, fillPaint)
        fillPaint.color = STEM_COLOUR
        fillPaint.setAlphaWithoutAllocating(a)
        canvas.drawRect(-h * 0.22f, -h * 1.3f, h * 0.22f, -h * 0.7f, fillPaint)
        canvas.restore()
    }

    companion object {
        /** The group's slow bob along its flight line: 2.2 rad/s, 3 px. */
        const val FLIGHT_BOB_RATE = 2.2f
        const val FLIGHT_BOB_PX = 3f

        /** How fast a pumpkin turns as it falls, in degrees a second. */
        const val DROP_TURN_DEGREES_PER_SECOND = 50f

        /** The pumpkins' three oranges, their stem, and the flames' orange round a yellow core. */
        val PUMPKIN_COLOURS = intArrayOf(0xFFEE8A2C.toInt(), 0xFFE07A2E.toInt(), 0xFFF2A03C.toInt())
        const val STEM_COLOUR = 0xFF4E7A3A.toInt()
        const val FLAME_OUTER = 0xFFF07A1A.toInt()
        const val FLAME_INNER = 0xFFFFC23C.toInt()
    }
}

/**
 * The Horseman's flight and his pool of falling pumpkins, as arithmetic: no Android type, so the JVM suite runs it
 * (`HorsemanFlightTest`) -- the rhythm, the crossing, the drops and that the pool never grows. [HeadlessHorsemanEffect]
 * draws what this holds. Random without a seed in the app, as the sleigh's ([random] is `Random.Default`): a flight's length,
 * height and way, the gap before the next, and each pumpkin's moment, landing, fall and colour differ from one run to the
 * next. A test hands it a seeded [random] to repeat a run.
 */
internal class HorsemanFlight(private val random: Random = Random.Default) {
    var flying = false
        private set
    var flightProgress = 0f
        private set
    var flightDuration = 10f
        private set
    var flightY = 0f
        private set
    var reverse = false
        private set
    var timeUntilNextFlight = FIRST_FLIGHT_SECONDS
        private set
    private var dropTimer = 0f

    // The pool: slot i is in the air while [dropActive] i, for [dropDuration] i seconds of [dropAge] i.
    val dropActive = BooleanArray(POOL_SIZE)
    val dropX = FloatArray(POOL_SIZE)
    val dropStartY = FloatArray(POOL_SIZE)
    val dropVx = FloatArray(POOL_SIZE)
    val dropTargetY = FloatArray(POOL_SIZE)
    val dropDuration = FloatArray(POOL_SIZE)
    val dropAge = FloatArray(POOL_SIZE)
    val dropColour = IntArray(POOL_SIZE)

    /** How many pumpkins are in the air. */
    val dropsFalling: Int get() {
        var n = 0
        for (i in 0 until POOL_SIZE) if (dropActive[i]) n++
        return n
    }

    var dropHalfPx = 5f
        private set
    var marginPx = SantaSleighEffect.MIN_MARGIN_PX
        private set

    /**
     * For `HorsemanFlightTest`: from 0 to 1, the flight held at that point of its crossing at every update; negative, he
     * flies as he does on the wallpaper. Held, he lets no pumpkin fall. Nothing in the app sets it; the instrumented tests
     * start a flight instead ([startForTest]).
     */
    var heldProgress = -1f

    /**
     * The group's size on this screen, from the renderer whenever the screen's size changes: a falling pumpkin's half
     * width ([dropHalf] px) and the group's half width ([groupHalfWidth] px), which keeps a flight starting and ending out
     * of sight.
     */
    fun setGroupGeometry(dropHalf: Float, groupHalfWidth: Float) {
        dropHalfPx = dropHalf
        marginPx = maxOf(SantaSleighEffect.MIN_MARGIN_PX, groupHalfWidth)
    }

    /**
     * A frame of [deltaSeconds]. [sleighDueIn]: the seconds until Santa's sleigh is in the sky this frame -- 0 while it flies,
     * infinite with *Santa* off (the renderer updates the sleigh first) --; a turn of his waits while it is
     * [SKY_NEEDED_SECONDS] or less ([AFTER_SLEIGH_SECONDS]).
     */
    fun update(deltaSeconds: Float, enabled: Boolean, sleighDueIn: Float, screenWidth: Float, screenHeight: Float) {
        // The pumpkins already falling keep falling if the switch goes off mid-drop, as the sleigh's gifts do.
        for (i in 0 until POOL_SIZE) {
            if (!dropActive[i]) continue
            dropAge[i] += deltaSeconds
            if (dropAge[i] >= dropDuration[i]) dropActive[i] = false
        }
        if (!enabled) {
            flying = false
            timeUntilNextFlight = FIRST_FLIGHT_SECONDS
            return
        }
        if (heldProgress >= 0f) {
            if (!flying) startFlight(screenHeight)
            flightProgress = heldProgress
            return
        }
        if (flying) {
            flightProgress += deltaSeconds / flightDuration
            dropTimer -= deltaSeconds
            if (dropTimer <= 0f && flightProgress in DROP_FROM..DROP_UNTIL) {
                letFall(screenWidth, screenHeight)
                dropTimer = DROP_EVERY_MIN + random.nextFloat() * DROP_EVERY_SPREAD
            }
            if (flightProgress >= 1f) {
                flying = false
                timeUntilNextFlight = GAP_MIN_SECONDS + random.nextFloat() * GAP_SPREAD_SECONDS
            }
        } else if (sleighDueIn <= SKY_NEEDED_SECONDS && timeUntilNextFlight <= AFTER_SLEIGH_SECONDS) {
            // His turn comes with the sleigh up or due before he would be gone: held, he sets off [AFTER_SLEIGH_SECONDS]
            // after it has flown and gone (I-1048, I-1068).
            timeUntilNextFlight = AFTER_SLEIGH_SECONDS
        } else {
            timeUntilNextFlight -= deltaSeconds
            if (timeUntilNextFlight <= 0f) startFlight(screenHeight)
        }
    }

    /**
     * For the tests that count a frame of the Horseman dropping his pumpkins (v5.16): a flight set off now, already at the
     * part of its crossing pumpkins fall from, its first one due at once. Nothing in the app calls it.
     */
    fun startForTest(screenHeight: Float) {
        heldProgress = -1f
        startFlight(screenHeight)
        flightProgress = DROP_FROM
        dropTimer = 0f
    }

    private fun startFlight(screenHeight: Float) {
        flying = true
        flightProgress = 0f
        flightDuration = FLIGHT_MIN_SECONDS + random.nextFloat() * FLIGHT_SPREAD_SECONDS
        flightY = screenHeight * (FLIGHT_Y_MIN + random.nextFloat() * FLIGHT_Y_SPREAD)
        reverse = random.nextBoolean()
        dropTimer = FIRST_DROP_SECONDS
    }

    /** A pumpkin let fall from the group's middle, a little above its flight line, to the pavement: a slot of the pool. */
    private fun letFall(screenWidth: Float, screenHeight: Float) {
        var slot = -1
        for (i in 0 until POOL_SIZE) if (!dropActive[i]) { slot = i; break }
        if (slot < 0) return
        val speed = (screenWidth + marginPx * 2f) / flightDuration * SantaSleighEffect.motion(reverse)
        dropActive[slot] = true
        dropX[slot] = currentX(screenWidth)
        dropStartY[slot] = flightY - dropHalfPx
        dropVx[slot] = speed * SantaSleighEffect.GIFT_KEEPS_SPEED
        dropTargetY[slot] = screenHeight * (
            SceneSpace.PAVEMENT_FAR_Y_FRACTION +
                random.nextFloat() * (SceneSpace.PAVEMENT_NEAR_Y_FRACTION - SceneSpace.PAVEMENT_FAR_Y_FRACTION)
            )
        dropDuration[slot] = FALL_MIN_SECONDS + random.nextFloat() * FALL_SPREAD_SECONDS
        dropAge[slot] = 0f
        dropColour[slot] = random.nextInt(HeadlessHorsemanEffect.PUMPKIN_COLOURS.size)
    }

    /** Where the group's middle is across the screen. */
    fun currentX(screenWidth: Float): Float {
        val travel = screenWidth + marginPx * 2f
        val raw = flightProgress * travel - marginPx
        return if (reverse) screenWidth - raw else raw
    }

    /** Where pumpkin [i] is: the speed it left with, lost by the end of its fall; eased down as the gifts are. */
    fun dropXNow(i: Int): Float {
        val a = dropAge[i].coerceIn(0f, dropDuration[i])
        return dropX[i] + dropVx[i] * a * (1f - a / (2f * dropDuration[i]))
    }

    fun dropYNow(i: Int): Float {
        val p = (dropAge[i] / dropDuration[i]).coerceIn(0f, 1f)
        return dropStartY[i] + (dropTargetY[i] - dropStartY[i]) * p * p
    }

    /** Fades in over the first 8 % of the crossing and out over the last 8 %, as the sleigh. */
    fun edgeFadeAlpha(): Float {
        val fadeZone = 0.08f
        return when {
            flightProgress < fadeZone -> (flightProgress / fadeZone).coerceIn(0f, 1f)
            flightProgress > 1f - fadeZone -> ((1f - flightProgress) / fadeZone).coerceIn(0f, 1f)
            else -> 1f
        }
    }

    companion object {
        /** How many pumpkins can be in the air at once: a drop every 1.1 s at the quickest, in the air 2.1 s at the longest. */
        const val POOL_SIZE = 4

        /**
         * Seconds from the switch turned on (or the wallpaper's start) to his first turn, the sleigh's 8 -- held, as every
         * turn, while the sleigh is in the sky or due ([SKY_NEEDED_SECONDS]).
         */
        const val FIRST_FLIGHT_SECONDS = 8f

        /**
         * How long after Santa's sleigh has gone he sets off, when his turn came with it in the sky or due (inventory
         * I-1048). The sleigh is out of sight for the last twentieth of its crossing and he is for the first ninth of his,
         * so on the BV6600 the sky stays empty about four and a half to five seconds between them: two passes, never one
         * group turning into the other at the edge, and the sleigh's last gift has landed before his first pumpkin falls.
         */
        const val AFTER_SLEIGH_SECONDS = 3f

        /** A flight's length and the gap before the next, as the sleigh's: 9 to 13 s, then 25 to 60 s. */
        const val FLIGHT_MIN_SECONDS = 9f
        const val FLIGHT_SPREAD_SECONDS = 4f

        /**
         * The sky he needs clear of Santa's sleigh to set off (inventory I-1068): his longest flight and
         * [AFTER_SLEIGH_SECONDS] more, so the sleigh, due no sooner, sets off that long after he has gone.
         */
        const val SKY_NEEDED_SECONDS = FLIGHT_MIN_SECONDS + FLIGHT_SPREAD_SECONDS + AFTER_SLEIGH_SECONDS
        const val GAP_MIN_SECONDS = 25f
        const val GAP_SPREAD_SECONDS = 35f

        /** How high a flight goes, as a share of the screen's height: 10 to 26 %, the sleigh's band. */
        const val FLIGHT_Y_MIN = 0.10f
        const val FLIGHT_Y_SPREAD = 0.16f

        /**
         * The part of the crossing pumpkins fall from, the first one 1.4 s in -- or at [DROP_FROM] of the crossing where that
         * comes later, on a flight longer than 11.7 s --, then one every 1.1 to 1.9 s.
         */
        const val DROP_FROM = 0.12f
        const val DROP_UNTIL = 0.88f
        const val FIRST_DROP_SECONDS = 1.4f
        const val DROP_EVERY_MIN = 1.1f
        const val DROP_EVERY_SPREAD = 0.8f

        /** How long a pumpkin takes to reach the pavement: 1.6 to 2.1 s, the gifts'. */
        const val FALL_MIN_SECONDS = 1.6f
        const val FALL_SPREAD_SECONDS = 0.5f
    }
}
