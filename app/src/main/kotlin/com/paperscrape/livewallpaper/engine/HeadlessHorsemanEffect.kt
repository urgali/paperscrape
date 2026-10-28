package com.paperscrape.livewallpaper.engine

import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Headless Horseman's flight (`DESIGN_NOTES.md` §12, decision 79): about once a minute of wallpaper on screen he
 * gallops across the upper sky, as Santa's sleigh does ([SantaSleighEffect]), and throws the carved pumpkin lit in his
 * hand, which falls in flames to the pavement and goes out there (decision 84).
 *
 * **The rhythm is the sleigh's**: the first flight 8 s after the switch is on, a flight 9 to 13 s long, at 10 to 26 % of
 * the screen's height, the next one 25 to 60 s after the last ended -- a pass every 34 to 73 s of wallpaper on screen,
 * later when a turn waits for the sleigh (below): with *Santa* on too, about 59 passes an hour instead of 68; the line
 * under *Headless Horseman* says "about once a minute". The group fades in and out over the first and last 8 %
 * of its crossing, and is drawn by the caller through [draw]'s `riderDraw` ([HeadlessHorsemanDrawer]); the pumpkins in
 * the air are drawn here, their pumpkin through `lanternDraw`.
 *
 * **He keeps out of Santa's sleigh's way** (decision 79): he sets off only when the sky is his for the whole
 * of his crossing -- the sleigh neither in the sky nor due within [HorsemanFlight.SKY_NEEDED_SECONDS] -- and a turn that
 * comes otherwise waits until the sleigh has flown and gone, then sets off [HorsemanFlight.AFTER_SLEIGH_SECONDS] after it.
 * So the two are never in the sky together, and the sky is empty at least that long between them. The sleigh's rhythm is
 * its own and does not wait for him. The one way left for them to meet: *Santa* turned on while he is in flight, when the
 * sleigh's countdown is whatever it was left at.
 *
 * **He throws them from his hand, with the arm's gesture** (`DESIGN_NOTES.md` §12, decision 84): for each pumpkin his
 * near arm winds back, swings forward and lets the carved pumpkin it holds go ([HorsemanFlight.armDegrees]); thrown, it
 * is the pumpkin he held, whole and lit from the moment it leaves his hand, which goes on with the group's speed and a
 * push forward, rises a few pixels and falls to the pavement, turning, its three flames upright on it, and goes out
 * there; drawn in front of the group, so in its first half second it passes in front of the horse's neck (the arm is
 * the near one).
 * In his empty hand a new one grows from a third of its size ([HorsemanFlight.handPumpkinScale]).
 *
 * **The pumpkins in the air come from a fixed pool** ([HorsemanFlight.POOL_SIZE] slots of primitive arrays, filled and emptied
 * in place): a throw is a slot taken, not an object made, so a frame with the Horseman in flight allocates nothing, a throw
 * included (the sleigh's gifts are one object each, an event). One is thrown every 1.1 to 1.9 s while the
 * group is between 12 and 88 % of its crossing, and lands 1.6 to 2.1 s after, so at most two are in the air: a throw that
 * found every slot taken would not be made.
 */
class HeadlessHorsemanEffect {

    /**
     * Draws the group: its middle, its facing (+1/-1), its fade alpha, how far his near arm is turned (degrees, clockwise in
     * the drawings' frame: towards the tail) and how much of the pumpkin is in his hand (0 none, 1 whole). A
     * `fun interface`, as [SantaSleighEffect.SleighBlit].
     */
    fun interface RiderBlit {
        fun blit(x: Float, y: Float, dir: Float, alpha: Float, armDegrees: Float, handPumpkin: Float)
    }

    /** Draws a thrown pumpkin: its middle, the group's facing (+1/-1) when it was thrown, its turn (degrees) and its alpha 0..255. */
    fun interface LanternBlit {
        fun blit(x: Float, y: Float, dir: Float, degrees: Float, alpha: Int)
    }

    /** The flight and the pool of pumpkins: arithmetic of their own, which the JVM tests run (`HorsemanFlightTest`). */
    internal val flight = HorsemanFlight()

    /** How many pumpkins are in the air now (for the tests that count a frame of the Horseman in flight). */
    internal val dropsFalling: Int get() = flight.dropsFalling

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val flameShape = SceneShape(4)

    /** See [HorsemanFlight.setGroupGeometry]. */
    fun setGroupGeometry(flameHalf: Float, groupHalfWidth: Float, pxPerUnit: Float) =
        flight.setGroupGeometry(flameHalf, groupHalfWidth, pxPerUnit)

    /**
     * See [HorsemanFlight.update]; [sleighDueIn] is how soon Santa's sleigh is in the sky this frame
     * ([SantaSleighEffect.secondsToFlight], infinite with *Santa* off).
     */
    fun update(deltaSeconds: Float, enabled: Boolean, sleighDueIn: Float, screenWidth: Float, screenHeight: Float) =
        flight.update(deltaSeconds, enabled, sleighDueIn, screenWidth, screenHeight)

    /**
     * While he is in flight and not faded out, the group through [riderDraw], with its middle, its facing (+1 to the
     * left, the drawings' way), its fade, his arm's turn and the pumpkin in his hand; then the pumpkins in the air, in front
     * of it, their pumpkin through [lanternDraw].
     */
    fun draw(canvas: SceneCanvas, elapsedSeconds: SceneTime, screenWidth: Float, riderDraw: RiderBlit, lanternDraw: LanternBlit) {
        val f = flight
        if (f.flying) {
            val bob = elapsedSeconds.sinAt(FLIGHT_BOB_RATE) * FLIGHT_BOB_PX
            f.bobPx = bob
            val fade = f.edgeFadeAlpha()
            if (fade > 0.01f) {
                riderDraw.blit(f.currentX(screenWidth), f.flightY + bob, -SantaSleighEffect.motion(f.reverse), fade, f.armDegrees(), f.handPumpkinScale())
            }
        }
        for (i in 0 until HorsemanFlight.POOL_SIZE) if (f.dropActive[i]) drawDrop(canvas, i, lanternDraw)
    }

    /**
     * One pumpkin in the air: its three flames first, upright whatever its turn and flickering (it falls in flames:
     * `DESIGN_NOTES.md` §12, decision 79), orange round a yellow core, standing on its top; then the pumpkin,
     * turning. Whole as it leaves his hand, it fades out over the last part of its fall.
     */
    private fun drawDrop(canvas: SceneCanvas, i: Int, lanternDraw: LanternBlit) {
        val f = flight
        val age = f.dropAge[i]
        val p = (age / f.dropDuration[i]).coerceIn(0f, 1f)
        val fade = if (p > 0.85f) ((1f - p) / 0.15f).coerceIn(0f, 1f) else 1f
        val a = (fade * 255).toInt()
        val h = f.flameHalfPx
        val x = f.dropXNow(i)
        val y = f.dropYNow(i)
        canvas.save()
        canvas.translate(x, y)
        val base = -f.lanternRadiusPx * FLAME_BASE_RADII
        for (k in 0 until 3) {
            val fx = (k - 1) * h * 0.5f
            val tall = h * (1.25f + 0.45f * sin(age * 17f + k * 2.1f)) * (if (k == 1) 1.3f else 1f)
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
        canvas.restore()
        lanternDraw.blit(x, y, f.dropDir[i], f.dropDegrees(i), a)
    }

    companion object {
        /** The group's slow bob along its flight line: 2.2 rad/s, 3 px. */
        const val FLIGHT_BOB_RATE = 2.2f
        const val FLIGHT_BOB_PX = 3f

        /**
         * How fast a pumpkin turns as it falls, in degrees a second, clockwise in the drawings' frame, from the angle his arm let
         * it go at ([HorsemanFlight.dropDegrees]).
         */
        const val DROP_TURN_DEGREES_PER_SECOND = 50f

        /** Where the flames stand on a thrown pumpkin: this many of its radii above its middle, on its top. */
        const val FLAME_BASE_RADII = 0.62f

        /** The flames' orange round a yellow core. */
        const val FLAME_OUTER = 0xFFF07A1A.toInt()
        const val FLAME_INNER = 0xFFFFC23C.toInt()
    }
}

/**
 * The Horseman's flight, his arm's throw and his pool of pumpkins in the air, as arithmetic: no Android type, so the JVM
 * suite runs it (`HorsemanFlightTest`) -- the rhythm, the crossing, the throw and the pumpkin's fall, and that the pool never
 * grows. [HeadlessHorsemanEffect] draws what this holds. Random without a seed in the app, as the sleigh's ([random] is
 * [SceneRandom.source], `Random.Default`): a flight's length, height and way, the gap before the next, and each pumpkin's
 * moment, landing and fall differ from one run to the next. A test hands it a seeded [random] to repeat a run.
 */
internal class HorsemanFlight(private val random: Random = SceneRandom.source) {
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

    /** Seconds since his hand last let a pumpkin go: the arm comes back and a new one grows in his hand ([armDegrees]). */
    var sinceThrow = NEVER_THROWN
        private set

    // The pool: slot i is in the air while [dropActive] i, for [dropDuration] i seconds of [dropAge] i.
    val dropActive = BooleanArray(POOL_SIZE)
    val dropX = FloatArray(POOL_SIZE)
    val dropStartY = FloatArray(POOL_SIZE)
    val dropVx = FloatArray(POOL_SIZE)
    val dropVy = FloatArray(POOL_SIZE)
    val dropTargetY = FloatArray(POOL_SIZE)
    val dropDuration = FloatArray(POOL_SIZE)
    val dropAge = FloatArray(POOL_SIZE)
    /** The group's facing when it was thrown (+1 to the left, the drawings' way), which the pumpkin keeps as it falls. */
    val dropDir = FloatArray(POOL_SIZE)

    /** How many pumpkins are in the air. */
    val dropsFalling: Int get() {
        var n = 0
        for (i in 0 until POOL_SIZE) if (dropActive[i]) n++
        return n
    }

    /** Half the width of the flames a thrown pumpkin carries, px. */
    var flameHalfPx = 5f
        private set
    var marginPx = SantaSleighEffect.MIN_MARGIN_PX
        private set
    /** The group's scale, px a unit ([HeadlessHorsemanScale.pxPerUnit]): the throw's points and speeds are the group's units. */
    var pxPerUnit = 1f
        private set

    /** The group's bob along its flight line as last drawn, px, so a pumpkin leaves his hand where it was drawn. */
    var bobPx = 0f

    /**
     * Where he sits on the saddle as last drawn, in the group's units ([HorsemanTable.SEAT_X] of the pose, its `SEAT_Y` with
     * the rider's bob), so a pumpkin leaves his hand where it was drawn: the renderer sets it as it draws him; until then the
     * four poses' middle.
     */
    var seatX = (HorsemanTable.SEAT_X.sum() / HorsemanTable.SEAT_X.size)
    var seatY = (HorsemanTable.SEAT_Y.sum() / HorsemanTable.SEAT_Y.size)

    /** A thrown pumpkin's radius across, px: where its flames stand. */
    val lanternRadiusPx: Float get() = HorsemanTable.RIDER_PUMPKIN_RADIUS_UNITS * pxPerUnit

    /**
     * For `HorsemanFlightTest`: from 0 to 1, the flight held at that point of its crossing at every update; negative, he
     * flies as he does on the wallpaper. Held, he throws nothing. Nothing in the app sets it; the instrumented tests
     * start a flight instead ([startForTest]).
     */
    var heldProgress = -1f

    /**
     * The group's size on this screen, from the renderer whenever the screen's size changes: half the width of the flames
     * a thrown pumpkin carries ([flameHalf] px), the group's half width ([groupHalfWidth] px), which keeps a flight starting
     * and ending out of sight, and its scale ([pxPerUnit]).
     */
    fun setGroupGeometry(flameHalf: Float, groupHalfWidth: Float, pxPerUnit: Float) {
        flameHalfPx = flameHalf
        marginPx = maxOf(SantaSleighEffect.MIN_MARGIN_PX, groupHalfWidth)
        this.pxPerUnit = pxPerUnit
    }

    /**
     * A frame of [deltaSeconds]. [sleighDueIn]: the seconds until Santa's sleigh is in the sky this frame -- 0 while it flies,
     * infinite with *Santa* off (the renderer updates the sleigh first) --; a turn of his waits while it is
     * [SKY_NEEDED_SECONDS] or less ([AFTER_SLEIGH_SECONDS]).
     */
    fun update(deltaSeconds: Float, enabled: Boolean, sleighDueIn: Float, screenWidth: Float, screenHeight: Float) {
        sinceThrow = minOf(sinceThrow + deltaSeconds, NEVER_THROWN)
        // The pumpkins already in the air keep falling if the switch goes off mid-fall, as the sleigh's gifts do.
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
            // Due, in the part of the crossing he throws in: the moment it fell due ([secondsToThrow]'s rule, so the arm
            // that wound up for it always throws), `flightProgress + dropTimer / flightDuration`, no later than DROP_UNTIL.
            if (dropTimer <= 0f && flightProgress >= DROP_FROM && flightProgress + dropTimer / flightDuration <= DROP_UNTIL) {
                throwPumpkin(screenWidth, screenHeight)
                dropTimer = DROP_EVERY_MIN + random.nextFloat() * DROP_EVERY_SPREAD
            }
            if (flightProgress >= 1f) {
                flying = false
                timeUntilNextFlight = GAP_MIN_SECONDS + random.nextFloat() * GAP_SPREAD_SECONDS
            }
        } else if (sleighDueIn <= SKY_NEEDED_SECONDS && timeUntilNextFlight <= AFTER_SLEIGH_SECONDS) {
            // His turn comes with the sleigh up or due before he would be gone: held, he sets off [AFTER_SLEIGH_SECONDS]
            // after it has flown and gone.
            timeUntilNextFlight = AFTER_SLEIGH_SECONDS
        } else {
            timeUntilNextFlight -= deltaSeconds
            if (timeUntilNextFlight <= 0f) startFlight(screenHeight)
        }
    }

    /**
     * For the tests that count a frame of the Horseman throwing his pumpkins: a flight set off now, already at the
     * part of its crossing pumpkins are thrown in, its first throw due at once. Nothing in the app calls it.
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
        sinceThrow = NEVER_THROWN
    }

    /**
     * Seconds until his next throw in this flight: when the countdown and the part of the crossing he throws in agree --
     * the first one 1.4 s in, or at [DROP_FROM] of the crossing where that comes later --; negative or past [DROP_UNTIL]
     * when none is coming. The arm winds up for it ([armDegrees]).
     */
    fun secondsToThrow(): Float {
        if (!flying || heldProgress >= 0f) return -1f
        val toWindow = (DROP_FROM - flightProgress) * flightDuration
        val ahead = maxOf(dropTimer, toWindow)
        return if (flightProgress + ahead / flightDuration <= DROP_UNTIL) ahead else -1f
    }

    /**
     * The pumpkin in his hand thrown: it leaves his hand where the arm lets it go ([RELEASE_DEGREES], [handX], [handY]) with
     * the group's speed and a push forward ([THROW_FORWARD_UNITS_PER_SECOND]) and up ([THROW_UP_UNITS_PER_SECOND]), and falls
     * to the pavement in 1.6 to 2.1 s: a slot of the pool.
     */
    private fun throwPumpkin(screenWidth: Float, screenHeight: Float) {
        var slot = -1
        for (i in 0 until POOL_SIZE) if (!dropActive[i]) { slot = i; break }
        if (slot < 0) return
        val m = SantaSleighEffect.motion(reverse)
        val speed = (screenWidth + marginPx * 2f) / flightDuration * m
        dropActive[slot] = true
        dropX[slot] = handX(screenWidth, RELEASE_DEGREES)
        dropStartY[slot] = handY(RELEASE_DEGREES)
        dropVx[slot] = speed + m * THROW_FORWARD_UNITS_PER_SECOND * pxPerUnit
        dropVy[slot] = THROW_UP_UNITS_PER_SECOND * pxPerUnit
        dropTargetY[slot] = screenHeight * (
            SceneSpace.PAVEMENT_FAR_Y_FRACTION +
                random.nextFloat() * (SceneSpace.PAVEMENT_NEAR_Y_FRACTION - SceneSpace.PAVEMENT_FAR_Y_FRACTION)
            )
        dropDuration[slot] = FALL_MIN_SECONDS + random.nextFloat() * FALL_SPREAD_SECONDS
        dropAge[slot] = 0f
        dropDir[slot] = -m
        sinceThrow = 0f
    }

    /** The middle of the pumpkin in his hand on the screen, x, with his arm turned [degrees]: from the seat as last drawn. */
    fun handX(screenWidth: Float, degrees: Float): Float {
        val u = seatX + HorsemanTable.ARM_PIVOT_X + turnedX(HAND_FROM_PIVOT_X, HAND_FROM_PIVOT_Y, degrees)
        return currentX(screenWidth) + (u - GROUP_MIDDLE) * pxPerUnit * (-SantaSleighEffect.motion(reverse))
    }

    /** And y, the group's bob as last drawn included. */
    fun handY(degrees: Float): Float {
        val v = seatY + HorsemanTable.ARM_PIVOT_Y + turnedY(HAND_FROM_PIVOT_X, HAND_FROM_PIVOT_Y, degrees)
        return flightY + bobPx + v * pxPerUnit
    }

    /**
     * How far his near arm is turned now, degrees, clockwise in the drawings' frame (towards the tail): at rest 0, holding
     * the pumpkin up; for a throw it winds back to [WIND_BACK_DEGREES] over the throw's first [WIND_SECONDS] less
     * [SWING_SECONDS], swings forward to [RELEASE_DEGREES] in the last [SWING_SECONDS], where the pumpkin leaves it, and comes
     * back to 0 in [ARM_BACK_SECONDS]. Each part eased, as a hand's moves are.
     */
    fun armDegrees(): Float {
        if (!flying) return 0f
        if (sinceThrow < ARM_BACK_SECONDS) return RELEASE_DEGREES * (1f - ease(sinceThrow / ARM_BACK_SECONDS))
        val ahead = secondsToThrow()
        if (ahead < 0f || ahead > WIND_SECONDS) return 0f
        val into = WIND_SECONDS - ahead
        val windUp = WIND_SECONDS - SWING_SECONDS
        return if (into < windUp) {
            WIND_BACK_DEGREES * ease(into / windUp)
        } else {
            WIND_BACK_DEGREES + (RELEASE_DEGREES - WIND_BACK_DEGREES) * ease((into - windUp) / SWING_SECONDS)
        }
    }

    /**
     * How far pumpkin [i] is turned as it falls, degrees, clockwise in the drawings' frame: from [RELEASE_DEGREES], the angle
     * his arm held it at as it let it go -- so it leaves his hand as he held it: started upright, it would jump some 30
     * degrees in the frame of the release --, turning
     * [HeadlessHorsemanEffect.DROP_TURN_DEGREES_PER_SECOND] as it falls.
     */
    fun dropDegrees(i: Int): Float = RELEASE_DEGREES + dropAge[i] * HeadlessHorsemanEffect.DROP_TURN_DEGREES_PER_SECOND

    /**
     * How much of the pumpkin is in his hand, as a scale of it: 1 whole; 0 none, for [EMPTY_SECONDS] after he has thrown
     * one; then a new one, lit, growing from [GROWS_FROM] of its size to the whole in [GROW_SECONDS].
     */
    fun handPumpkinScale(): Float {
        if (sinceThrow < EMPTY_SECONDS) return 0f
        val grown = ((sinceThrow - EMPTY_SECONDS) / GROW_SECONDS).coerceIn(0f, 1f)
        return GROWS_FROM + (1f - GROWS_FROM) * grown
    }

    /** Where the group's middle is across the screen. */
    fun currentX(screenWidth: Float): Float {
        val travel = screenWidth + marginPx * 2f
        val raw = flightProgress * travel - marginPx
        return if (reverse) screenWidth - raw else raw
    }

    /** Where pumpkin [i] is: the speed it left with, lost by the end of its fall, as the gifts' ([SantaSleighEffect]). */
    fun dropXNow(i: Int): Float {
        val a = dropAge[i].coerceIn(0f, dropDuration[i])
        return dropX[i] + dropVx[i] * a * (1f - a / (2f * dropDuration[i]))
    }

    /**
     * And its height: thrown up at [dropVy] i, falling under a constant pull that lands it on the pavement at the end of its
     * fall, so it rises `vy² / 2g` -- a few pixels -- and comes down.
     */
    fun dropYNow(i: Int): Float {
        val d = dropDuration[i]
        val t = dropAge[i].coerceIn(0f, d)
        val vy = dropVy[i]
        val g = 2f * (dropTargetY[i] - dropStartY[i] + vy * d) / (d * d)
        return dropStartY[i] - vy * t + 0.5f * g * t * t
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
        /** How many pumpkins can be in the air at once: one thrown every 1.1 s at the quickest, in the air 2.1 s at the longest. */
        const val POOL_SIZE = 4

        /**
         * Seconds from the switch turned on (or the wallpaper's start) to his first turn, the sleigh's 8 -- held, as every
         * turn, while the sleigh is in the sky or due ([SKY_NEEDED_SECONDS]).
         */
        const val FIRST_FLIGHT_SECONDS = 8f

        /**
         * How long after Santa's sleigh has gone he sets off, when his turn came with it in the sky or due. The sleigh is
         * out of sight for the last twentieth of its crossing and he is for the first ninth of his, so on the BV6600 the
         * sky stays empty about four and a half to five seconds between them: two passes, never one group turning into
         * the other at the edge, and the sleigh's last gift has landed before his first pumpkin is thrown.
         */
        const val AFTER_SLEIGH_SECONDS = 3f

        /** A flight's length and the gap before the next, as the sleigh's: 9 to 13 s, then 25 to 60 s. */
        const val FLIGHT_MIN_SECONDS = 9f
        const val FLIGHT_SPREAD_SECONDS = 4f

        /**
         * The sky he needs clear of Santa's sleigh to set off: his longest flight and
         * [AFTER_SLEIGH_SECONDS] more, so the sleigh, due no sooner, sets off that long after he has gone.
         */
        const val SKY_NEEDED_SECONDS = FLIGHT_MIN_SECONDS + FLIGHT_SPREAD_SECONDS + AFTER_SLEIGH_SECONDS
        const val GAP_MIN_SECONDS = 25f
        const val GAP_SPREAD_SECONDS = 35f

        /** How high a flight goes, as a share of the screen's height: 10 to 26 %, the sleigh's band. */
        const val FLIGHT_Y_MIN = 0.10f
        const val FLIGHT_Y_SPREAD = 0.16f

        /**
         * The part of the crossing pumpkins are thrown in, the first one 1.4 s in -- or at [DROP_FROM] of the crossing where
         * that comes later, on a flight longer than 11.7 s --, then one every 1.1 to 1.9 s.
         */
        const val DROP_FROM = 0.12f
        const val DROP_UNTIL = 0.88f
        const val FIRST_DROP_SECONDS = 1.4f
        const val DROP_EVERY_MIN = 1.1f
        const val DROP_EVERY_SPREAD = 0.8f

        /** How long a pumpkin takes to reach the pavement: 1.6 to 2.1 s, the gifts'. */
        const val FALL_MIN_SECONDS = 1.6f
        const val FALL_SPREAD_SECONDS = 0.5f

        /**
         * The throw's push as the pumpkin leaves his hand, in the group's units a second: forward, beside the group's own
         * speed, and up -- 70 and 110 px a second on the BV6600 (1.235 px a unit), which its fall loses: it rises about 9 px
         * there before it comes down.
         */
        const val THROW_FORWARD_UNITS_PER_SECOND = 56.7f
        const val THROW_UP_UNITS_PER_SECOND = 89.0f

        /**
         * The arm's throw (`DESIGN_NOTES.md` §12, decision 84): [WIND_SECONDS] before the pumpkin leaves, the
         * arm starts winding back, to [WIND_BACK_DEGREES] (towards the tail) over [WIND_SECONDS] less [SWING_SECONDS]; it
         * swings forward to [RELEASE_DEGREES] in the last [SWING_SECONDS], where it lets the pumpkin go, and comes back to
         * rest in [ARM_BACK_SECONDS].
         */
        const val WIND_SECONDS = 0.4f
        const val SWING_SECONDS = 0.15f
        const val WIND_BACK_DEGREES = 28f
        const val RELEASE_DEGREES = -32f
        const val ARM_BACK_SECONDS = 0.45f

        /** His hand empty for [EMPTY_SECONDS] after a throw; then a new pumpkin grows in it from [GROWS_FROM] of its size in [GROW_SECONDS]. */
        const val EMPTY_SECONDS = 0.25f
        const val GROW_SECONDS = 0.35f
        const val GROWS_FROM = 1f / 3f

        /** [sinceThrow] before a flight's first throw: long ago, his hand full and his arm at rest. */
        const val NEVER_THROWN = 99f

        /** The middle of the pumpkin in his hand from the arm's pivot, in the group's units, the arm at rest. */
        private const val HAND_FROM_PIVOT_X = HorsemanTable.PUMPKIN_X - HorsemanTable.ARM_PIVOT_X
        private const val HAND_FROM_PIVOT_Y = HorsemanTable.PUMPKIN_Y - HorsemanTable.ARM_PIVOT_Y

        /** The group's middle, in its units: the flight point. */
        private const val GROUP_MIDDLE = (HorsemanTable.LEFT + HorsemanTable.RIGHT) * 0.5f

        private fun ease(t: Float): Float {
            val x = t.coerceIn(0f, 1f)
            return x * x * (3f - 2f * x)
        }

        /** (x, y) turned [degrees] clockwise on the screen (y down), as the canvas turns: x, then y. */
        fun turnedX(x: Float, y: Float, degrees: Float): Float {
            val r = Math.toRadians(degrees.toDouble())
            return (x * cos(r) - y * sin(r)).toFloat()
        }

        fun turnedY(x: Float, y: Float, degrees: Float): Float {
            val r = Math.toRadians(degrees.toDouble())
            return (x * sin(r) + y * cos(r)).toFloat()
        }
    }
}
