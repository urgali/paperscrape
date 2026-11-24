package com.paperscrape.livewallpaper.engine

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The fireworks over the night sky: the show's program and its bursts, moved frame by frame. [FireworkDrawer] draws them.
 * The *Fireworks* switch is per theme and they go off only at night (decision 103): `PaperRenderer` hands both in as
 * [update]'s `enabled`. The show itself is decision 118.
 *
 * **The program.** Single bursts at an unhurried rhythm: the first [FIRST_SINGLE_AT] s after the
 * fireworks start, then one every [SINGLE_GAP_MIN] to [SINGLE_GAP_MIN] + [SINGLE_GAP_SPREAD] s, across [X_MIN] to
 * [X_MIN] + [X_SPAN] of the screen's width and [Y_MIN] to [Y_MIN] + [Y_SPAN] of its height. Every burst has its rocket,
 * launched [RISE] s before it, so the burst goes off at its instant. After every [SALVO_EVERY] single bursts a salvo: two
 * more of the same kind, each at a place of its own in the same band of sky, [SALVO_STEP] s apart. Every [FINALE_PERIOD] s
 * a finale of [FINALE_BURSTS] bursts across
 * the sky, [FINALE_STEP] s apart, the first at [FIRST_FINALE_AT] s. The show's clock ([show]) runs only while the
 * fireworks are on: out of the night it stands, and a burst that was due is dropped.
 *
 * **Four styles, one at random for each burst, never the same as the burst before**: [STYLE_FLOWER], a paper flower of
 * three layers of petals that opens, turns and breaks into confetti fluttering down; [STYLE_PAPER], round pieces of paper
 * that fly out, slow in the air and fall with their trails; [STYLE_SPARKS], sparks with their streaks that crackle at the
 * end; [STYLE_LIGHT], the sparks with a halo round the burst and the sky lit for a moment. The three made of pieces burst
 * in one of five kinds ([KIND_PEONY], [KIND_TWO_COLOURS], [KIND_RING], [KIND_WILLOW], [KIND_PALM]).
 *
 * **Colours**: each burst picks one of the six paper colours ([PAPER]) at random, and a second, different one; a
 * salvo's two take theirs from the burst they follow, a finale's second is two along from its first. The second colour is
 * worn by the flower's middle layer, the two-colour burst's inner round, the ring's centre and the palm's heart. The
 * willow is always gold ([GOLD]), whatever the burst picked; the peony's heart and the flower's inner layer are cream
 * ([CREAM]). They are night colours only, with no day pair: the fireworks go off only at night (decision 118).
 *
 * **One random source**: everything random here -- when, where, which kind, which colours, which style, the scatter of
 * the pieces -- is drawn from [SceneRandom.source], the generator the sky's visitors share, read at every [update], so a
 * test that seeds it sees the same show twice.
 *
 * **Nothing is allocated after construction**: the bursts and their pieces live in fixed arrays ([BURST_CAP] bursts of at
 * most [PIECES] pieces each, piece `k` of burst `b` at `b * PIECES + k`), the bursts still to launch in a fixed table of
 * [EVENT_CAP]. A burst due while every slot is in the sky waits for one to clear.
 */
internal class FireworkEffect {

    /**
     * Told of every launch, for the tests: the slot, the burst's instant on the show's clock, its place (fractions of the
     * screen), where it came from ([EVENT_SINGLE], [EVENT_SALVO], [EVENT_FINALE]), its style, kind and two colours (indices
     * into [PAPER]). A `fun interface` with primitive parameters, so a call boxes nothing; null in the wallpaper.
     */
    fun interface LaunchWatcher {
        @Suppress("LongParameterList")
        fun launched(slot: Int, at: Double, x: Float, y: Float, source: Int, style: Int, kind: Int, colour: Int, colour2: Int)
    }

    internal var watcher: LaunchWatcher? = null

    // --- The program -----------------------------------------------------------------------------

    private var rnd: Random = SceneRandom.source

    /** Seconds of fireworks: runs only while they are on. */
    internal var show = 0.0
        private set

    /** Pixels a size here is worth on this screen: the sizes are pixels of a screen [REFERENCE_WIDTH_PX] wide. */
    internal var unit = 1f
        private set

    private var nextSingleAt = FIRST_SINGLE_AT
    private var singles = 0
    private var nextFinaleAt = FIRST_FINALE_AT
    private var lastStyle = 0

    private val eventLive = BooleanArray(EVENT_CAP)
    private val eventAt = DoubleArray(EVENT_CAP)
    private val eventX = FloatArray(EVENT_CAP)
    private val eventY = FloatArray(EVENT_CAP)
    private val eventKind = IntArray(EVENT_CAP)
    private val eventColour = IntArray(EVENT_CAP)
    private val eventColour2 = IntArray(EVENT_CAP)
    private val eventSource = IntArray(EVENT_CAP)

    // --- The bursts, read by the drawer ------------------------------------------------------------

    internal val burstLive = BooleanArray(BURST_CAP)

    /** Still rising: [burstAge] counts the rocket's seconds until [RISE], then the burst's from 0. */
    internal val burstRocket = BooleanArray(BURST_CAP)
    internal val burstX = FloatArray(BURST_CAP)
    internal val burstY = FloatArray(BURST_CAP)

    /** Where the rocket leaves from, below the burst. */
    internal val burstStartY = FloatArray(BURST_CAP)
    internal val burstAge = FloatArray(BURST_CAP)
    internal val burstStyle = IntArray(BURST_CAP)
    internal val burstKind = IntArray(BURST_CAP)
    internal val burstColour = IntArray(BURST_CAP)
    internal val burstColour2 = IntArray(BURST_CAP)

    /** How many of the burst's piece slots are in use. */
    internal val burstCount = IntArray(BURST_CAP)
    internal val burstGravity = FloatArray(BURST_CAP)
    internal val burstRotation = FloatArray(BURST_CAP)

    /** How far the burst reaches: the pieces' open distance, the flower's outer petals' tips. */
    internal val burstReach = FloatArray(BURST_CAP)

    /** The flower has broken into confetti. */
    internal val burstBroken = BooleanArray(BURST_CAP)
    private val burstLife = FloatArray(BURST_CAP)
    private val burstDrag = FloatArray(BURST_CAP)

    // --- The pieces, read by the drawer ------------------------------------------------------------

    internal val pieceX = FloatArray(BURST_CAP * PIECES)
    internal val pieceY = FloatArray(BURST_CAP * PIECES)
    internal val pieceVx = FloatArray(BURST_CAP * PIECES)
    internal val pieceVy = FloatArray(BURST_CAP * PIECES)
    internal val pieceLife = FloatArray(BURST_CAP * PIECES)
    internal val pieceSize = FloatArray(BURST_CAP * PIECES)

    /** An index into the palette: [PAPER]'s six, [CREAM] or [GOLD]. */
    internal val pieceColour = IntArray(BURST_CAP * PIECES)

    /** How many trail discs (paper) or how long a streak (sparks). */
    internal val pieceTrail = IntArray(BURST_CAP * PIECES)
    internal val pieceAngle = FloatArray(BURST_CAP * PIECES)
    internal val pieceSpin = FloatArray(BURST_CAP * PIECES)
    internal val piecePhase = FloatArray(BURST_CAP * PIECES)
    internal val pieceLength = FloatArray(BURST_CAP * PIECES)
    internal val pieceWidth = FloatArray(BURST_CAP * PIECES)

    fun update(deltaSeconds: Float, enabled: Boolean, screenWidth: Float, screenHeight: Float) {
        rnd = SceneRandom.source
        unit = screenWidth / REFERENCE_WIDTH_PX
        for (b in 0 until BURST_CAP) if (burstLive[b]) age(b, deltaSeconds)
        if (enabled) {
            show += deltaSeconds
            schedule()
            launchDue(screenWidth, screenHeight)
        } else {
            // Out of the night the show's clock stands: what was waiting to go off is dropped, the rest waits.
            for (e in 0 until EVENT_CAP) if (eventLive[e] && eventAt[e] <= show) eventLive[e] = false
        }
    }

    /** For the tests: [frames] frames of [FRAME_SECONDS] with the fireworks on, as the wallpaper runs them. */
    internal fun advanceForTest(frames: Int, screenWidth: Float, screenHeight: Float) {
        repeat(frames) { update(FRAME_SECONDS, true, screenWidth, screenHeight) }
    }

    /** How many bursts are in the sky, rockets included. */
    internal fun liveBursts(): Int {
        var n = 0
        for (b in 0 until BURST_CAP) if (burstLive[b]) n++
        return n
    }

    /** Writes down the bursts due within [LOOKAHEAD] s: the single bursts with their salvos, and the finale. */
    private fun schedule() {
        while (nextSingleAt < show + LOOKAHEAD) {
            val at = nextSingleAt
            val x = X_MIN + rnd.nextFloat() * X_SPAN
            val y = Y_MIN + rnd.nextFloat() * Y_SPAN
            val kind = rnd.nextInt(KINDS)
            val colour = rnd.nextInt(PALETTE)
            var colour2 = rnd.nextInt(PALETTE - 1)
            if (colour2 >= colour) colour2++
            push(at, x, y, EVENT_SINGLE, kind, colour, colour2)
            nextSingleAt = at + SINGLE_GAP_MIN + rnd.nextFloat() * SINGLE_GAP_SPREAD
            if (singles % SALVO_EVERY == SALVO_EVERY - 1) {
                // A salvo: two more of the same kind, in colours taken from this one's, a moment apart, each at a
                // place of its own in the burst band.
                for (k in 1..SALVO_EXTRA) {
                    val c = (colour + k * 2) % PALETTE
                    val sx = X_MIN + rnd.nextFloat() * X_SPAN
                    val sy = Y_MIN + rnd.nextFloat() * Y_SPAN
                    push(at + SALVO_STEP * k, sx, sy, EVENT_SALVO, kind, c, (c + 3) % PALETTE)
                }
            }
            singles++
        }
        if (nextFinaleAt < show + LOOKAHEAD) {
            // The finale: the five kinds in turn across the sky, left to right.
            for (k in 0 until FINALE_BURSTS) {
                val x = FINALE_X_MIN + (k / (FINALE_BURSTS - 1f)) * FINALE_X_SPAN + (rnd.nextFloat() - 0.5f) * FINALE_X_JITTER
                val c = rnd.nextInt(PALETTE)
                val y = Y_MIN + rnd.nextFloat() * FINALE_Y_SPAN
                push(nextFinaleAt + k * FINALE_STEP, x, y, EVENT_FINALE, k % KINDS, c, (c + 2) % PALETTE)
            }
            nextFinaleAt += FINALE_PERIOD
        }
    }

    @Suppress("LongParameterList")
    private fun push(at: Double, x: Float, y: Float, source: Int, kind: Int, colour: Int, colour2: Int) {
        for (e in 0 until EVENT_CAP) {
            if (eventLive[e]) continue
            eventLive[e] = true
            eventAt[e] = at
            eventX[e] = x
            eventY[e] = y
            eventSource[e] = source
            eventKind[e] = kind
            eventColour[e] = colour
            eventColour2[e] = colour2
            return
        }
    }

    private fun launchDue(screenWidth: Float, screenHeight: Float) {
        for (e in 0 until EVENT_CAP) {
            if (!eventLive[e] || eventAt[e] - RISE > show) continue
            val b = freeSlot()
            if (b < 0) return // every slot is in the sky: the rest wait for one to clear
            eventLive[e] = false
            launch(b, e, screenWidth, screenHeight)
        }
    }

    private fun freeSlot(): Int {
        for (b in 0 until BURST_CAP) if (!burstLive[b]) return b
        return -1
    }

    private fun launch(b: Int, e: Int, screenWidth: Float, screenHeight: Float) {
        burstLive[b] = true
        burstRocket[b] = true
        burstX[b] = screenWidth * eventX[e]
        burstY[b] = screenHeight * eventY[e]
        burstKind[b] = eventKind[e]
        burstColour[b] = eventColour[e]
        burstColour2[b] = eventColour2[e]
        // From the instant the rocket was due: a frame's share late at most, more if it waited for a slot.
        burstAge[b] = (show - (eventAt[e] - RISE)).toFloat()
        burstStyle[b] = dealStyle()
        burstStartY[b] = (burstY[b] + screenHeight * ROCKET_RISE_FRACTION)
            .coerceAtMost(screenHeight * ROCKET_START_MAX_FRACTION)
            .coerceAtLeast(burstY[b] + ROCKET_MIN_RISE * unit)
        burstBroken[b] = false
        burstCount[b] = 0
        burstRotation[b] = rnd.nextFloat() * TWO_PI
        watcher?.launched(b, eventAt[e], eventX[e], eventY[e], eventSource[e], burstStyle[b], eventKind[e], eventColour[e], eventColour2[e])
    }

    /** One of the four styles, evenly among the three that are not the last burst's; the first may be any of the four. */
    private fun dealStyle(): Int {
        val style = if (lastStyle == 0) {
            1 + rnd.nextInt(STYLES)
        } else {
            val s = 1 + rnd.nextInt(STYLES - 1)
            if (s >= lastStyle) s + 1 else s
        }
        lastStyle = style
        return style
    }

    private fun age(b: Int, dt: Float) {
        burstAge[b] += dt
        if (burstRocket[b]) {
            if (burstAge[b] >= RISE) {
                burstRocket[b] = false
                burstAge[b] -= RISE
                explode(b)
            }
            return
        }
        if (burstStyle[b] == STYLE_FLOWER) {
            if (!burstBroken[b] && burstAge[b] >= FLOWER_HOLD) breakFlower(b)
            if (burstBroken[b]) moveConfetti(b, dt)
        } else {
            movePieces(b, dt)
        }
        if (burstAge[b] > burstLife[b]) burstLive[b] = false
    }

    // --- Bursts of pieces: paper, sparks, sparks with light ------------------------------------------

    private fun explode(b: Int) {
        if (burstStyle[b] == STYLE_FLOWER) {
            burstReach[b] = FLOWER_REACH * unit
            burstLife[b] = FLOWER_HOLD + CONFETTI_LIFE + FLOWER_TAIL
            return
        }
        val reach = PIECE_REACH * unit
        burstReach[b] = reach
        var drag = DRAG
        var gravity = GRAVITY * unit
        val base = b * PIECES
        fillBase = base
        fillN = 0
        fillX = burstX[b]
        fillY = burstY[b]
        val sparks = burstStyle[b] != STYLE_PAPER
        val colour = burstColour[b]
        val colour2 = burstColour2[b]
        val v0 = reach * drag
        when (burstKind[b]) {
            KIND_PEONY -> {
                val m = if (sparks) 48 else 42
                for (k in 0 until m) piece(TWO_PI * k / m + jitter(0.07f), v0 * (0.9f + rnd.nextFloat() * 0.18f), colour, 1f, 1.7f + rnd.nextFloat() * 0.5f, 2)
                for (k in 0 until 8) piece(TWO_PI * k / 8 + 0.4f, v0 * 0.3f, CREAM, 0.8f, 1.1f, 0)
            }
            KIND_TWO_COLOURS -> {
                val m = if (sparks) 32 else 28
                for (k in 0 until m) piece(TWO_PI * k / m + jitter(0.06f), v0 * (0.95f + rnd.nextFloat() * 0.1f), colour, 1f, 1.8f + rnd.nextFloat() * 0.4f, 2)
                for (k in 0 until 16) piece(TWO_PI * k / 16 + jitter(0.1f), v0 * (0.5f + rnd.nextFloat() * 0.08f), colour2, 0.95f, 1.6f + rnd.nextFloat() * 0.3f, 1)
            }
            KIND_RING -> {
                // A ring seen at a slant: one speed, squashed on one axis and turned.
                val squash = 0.42f + rnd.nextFloat() * 0.4f
                val rot = burstRotation[b]
                val m = 30
                for (k in 0 until m) {
                    val a = TWO_PI * k / m
                    val ex = cos(a)
                    val ey = sin(a) * squash
                    val rx = ex * cos(rot) - ey * sin(rot)
                    val ry = ex * sin(rot) + ey * cos(rot)
                    piece(atan2(ry, rx), v0 * sqrt(rx * rx + ry * ry), colour, 1f, 1.9f, 1)
                }
                piece(0f, 0f, colour2, 1.5f, 0.9f, 0) // the centre, in the second colour
            }
            KIND_WILLOW -> {
                // Gold, slow to open, long to fall: the willow's branches hang.
                drag = 2.4f
                gravity = 92f * unit
                val m = if (sparks) 40 else 32
                val v = reach * drag * 0.78f
                for (k in 0 until m) piece(TWO_PI * k / m + jitter(0.08f), v * (0.88f + rnd.nextFloat() * 0.24f), GOLD, 0.85f, 2.9f + rnd.nextFloat() * 0.6f, 4)
            }
            else -> { // KIND_PALM: a few heavy comets with long tails, and a small heart in the second colour.
                drag = 2.6f
                gravity = 78f * unit
                val v = reach * drag * 1.05f
                for (k in 0 until 8) piece(TWO_PI * k / 8 + jitter(0.12f), v * (0.92f + rnd.nextFloat() * 0.16f), colour, 1.55f, 2.1f + rnd.nextFloat() * 0.3f, 6)
                for (k in 0 until 10) piece(TWO_PI * k / 10 + jitter(0.2f), v0 * 0.34f, colour2, 0.9f, 1.3f, 1)
            }
        }
        val n = fillN
        burstCount[b] = n
        burstDrag[b] = drag
        burstGravity[b] = gravity
        var life = 0f
        for (k in 0 until n) if (pieceLife[base + k] > life) life = pieceLife[base + k]
        burstLife[b] = life
    }

    // Where [piece] writes: members, not a local function, so a burst going off builds no closure.
    private var fillBase = 0
    private var fillN = 0
    private var fillX = 0f
    private var fillY = 0f

    @Suppress("LongParameterList")
    private fun piece(angle: Float, speed: Float, colour: Int, size: Float, life: Float, trail: Int) {
        if (fillN >= PIECES) return
        val i = fillBase + fillN
        pieceX[i] = fillX
        pieceY[i] = fillY
        pieceVx[i] = cos(angle) * speed
        pieceVy[i] = sin(angle) * speed
        pieceColour[i] = colour
        pieceSize[i] = size
        pieceLife[i] = life
        pieceTrail[i] = trail
        piecePhase[i] = rnd.nextFloat()
        fillN++
    }

    private fun jitter(r: Float): Float = (rnd.nextFloat() - 0.5f) * 2f * r

    /** Fast out, slowed by the air ([burstDrag]), pulled down by their weight ([burstGravity]). */
    private fun movePieces(b: Int, dt: Float) {
        val f = exp(-burstDrag[b] * dt)
        val g = burstGravity[b] * dt
        val base = b * PIECES
        for (k in 0 until burstCount[b]) {
            val i = base + k
            pieceVx[i] *= f
            pieceVy[i] = pieceVy[i] * f + g
            pieceX[i] += pieceVx[i] * dt
            pieceY[i] += pieceVy[i] * dt
        }
    }

    // --- The paper flower -------------------------------------------------------------------------

    /** At [FLOWER_HOLD] s the open flower's petals come loose where they stand and become confetti. */
    private fun breakFlower(b: Int) {
        burstBroken[b] = true
        val base = b * PIECES
        var n = 0
        val reach = burstReach[b]
        val turn = burstRotation[b] + FLOWER_TURN * FLOWER_HOLD
        for (layer in 0 until FLOWER_LAYERS) {
            val count = LAYER_PETALS[layer]
            val step = TWO_PI / count
            for (k in 0 until count) {
                if (n >= PIECES) break
                val a = turn + step * k + layer * step * 0.5f
                val rIn = reach * LAYER_IN[layer]
                val rOut = reach * LAYER_OUT[layer]
                val mid = (rIn + rOut) * 0.5f
                val i = base + n
                pieceX[i] = burstX[b] + cos(a) * mid
                pieceY[i] = burstY[b] + sin(a) * mid
                val out = (26f + rnd.nextFloat() * 22f) * unit * (mid / reach + 0.3f)
                pieceVx[i] = cos(a) * out
                pieceVy[i] = sin(a) * out - 12f * unit
                pieceAngle[i] = a
                pieceSpin[i] = (1.6f + rnd.nextFloat() * 2.6f) * (if (rnd.nextBoolean()) 1f else -1f)
                piecePhase[i] = rnd.nextFloat() * TWO_PI
                pieceLength[i] = (rOut - rIn) * 0.62f
                pieceWidth[i] = reach * LAYER_WIDTH[layer] * 0.75f
                pieceColour[i] = flowerLayerColour(burstColour[b], burstColour2[b], layer)
                pieceLife[i] = CONFETTI_LIFE * (0.8f + rnd.nextFloat() * 0.35f)
                n++
            }
        }
        burstCount[b] = n
    }

    /** Paper falls at its own slow pace and swings from side to side. */
    private fun moveConfetti(b: Int, dt: Float) {
        val f = exp(-2.2f * dt)
        val base = b * PIECES
        val fall = 58f * unit
        val t = burstAge[b] - FLOWER_HOLD
        for (k in 0 until burstCount[b]) {
            val i = base + k
            pieceVx[i] *= f
            pieceVy[i] = pieceVy[i] * f + 70f * unit * dt
            if (pieceVy[i] > fall) pieceVy[i] = fall
            pieceX[i] += (pieceVx[i] + sin(2.3f * t + piecePhase[i]) * 20f * unit) * dt
            pieceY[i] += pieceVy[i] * dt
        }
    }

    companion object {
        const val BURST_CAP = 12
        const val PIECES = 64
        const val EVENT_CAP = 24

        /** How far ahead the program writes its bursts down: more than [RISE], so a rocket is never late. */
        const val LOOKAHEAD = 2.0

        /** The rocket's climb, in seconds: it leaves this long before its burst's instant. */
        const val RISE = 1.0f

        /** One frame of the wallpaper, for [advanceForTest]. */
        const val FRAME_SECONDS = 1f / 30f

        const val FIRST_SINGLE_AT = 2.0
        const val SINGLE_GAP_MIN = 3.5
        const val SINGLE_GAP_SPREAD = 4.0
        const val X_MIN = 0.15f
        const val X_SPAN = 0.7f
        const val Y_MIN = 0.12f
        const val Y_SPAN = 0.28f

        const val SALVO_EVERY = 5
        const val SALVO_EXTRA = 2
        const val SALVO_STEP = 0.45

        const val FIRST_FINALE_AT = 40.0
        const val FINALE_PERIOD = 180.0
        const val FINALE_BURSTS = 7
        const val FINALE_STEP = 0.45
        const val FINALE_X_MIN = 0.12f
        const val FINALE_X_SPAN = 0.76f
        const val FINALE_X_JITTER = 0.08f
        const val FINALE_Y_SPAN = 0.26f

        const val EVENT_SINGLE = 0
        const val EVENT_SALVO = 1
        const val EVENT_FINALE = 2

        const val STYLES = 4
        const val STYLE_FLOWER = 1
        const val STYLE_PAPER = 2
        const val STYLE_SPARKS = 3
        const val STYLE_LIGHT = 4

        const val KINDS = 5
        const val KIND_PEONY = 0
        const val KIND_TWO_COLOURS = 1
        const val KIND_RING = 2
        const val KIND_WILLOW = 3
        const val KIND_PALM = 4

        /** The screen width the sizes below are pixels of; [unit] scales them to the screen's. */
        const val REFERENCE_WIDTH_PX = 720f

        /** How far the pieces open, before the air stops them; their drag (per second) and their weight (px/s²). */
        const val PIECE_REACH = 122f
        const val DRAG = 3.0f
        const val GRAVITY = 62f

        /** The rocket leaves from this far below its burst (a fraction of the height), never lower than [ROCKET_START_MAX_FRACTION]. */
        const val ROCKET_RISE_FRACTION = 0.22f
        const val ROCKET_START_MAX_FRACTION = 0.47f
        const val ROCKET_MIN_RISE = 60f

        /** The flower's outer petals' tips from its middle. */
        const val FLOWER_REACH = 104f

        /** How long the flower holds open before it breaks into confetti, how long the confetti flutter, and the tail. */
        const val FLOWER_HOLD = 1.05f
        const val CONFETTI_LIFE = 2.4f
        const val FLOWER_TAIL = 0.4f

        /** How fast the open flower turns, radians per second. */
        const val FLOWER_TURN = 0.22f

        const val FLOWER_LAYERS = 3

        /** Petals in each layer, outer to inner; their inner and outer ends and their width, as fractions of the reach. */
        val LAYER_PETALS = intArrayOf(12, 10, 8)
        val LAYER_IN = floatArrayOf(0.34f, 0.18f, 0.08f)
        val LAYER_OUT = floatArrayOf(1.0f, 0.7f, 0.4f)
        val LAYER_WIDTH = floatArrayOf(0.13f, 0.12f, 0.085f)

        /** Where along its length a petal is widest. */
        const val PETAL_WIDEST = 0.45f

        /** The disc in the flower's middle, its radius a fraction of the reach. */
        const val FLOWER_CENTRE = 0.09f

        const val TWO_PI = (Math.PI * 2).toFloat()

        /** The six colours of paper: gold, red, green, blue, pink, violet; off-tones, never pure (§7 rule 4). */
        val PAPER = intArrayOf(
            0xFFF6C445.toInt(), 0xFFEE5D52.toInt(), 0xFF5FCB8E.toInt(), 0xFF5FA6EC.toInt(), 0xFFF28AC0.toInt(), 0xFFAA8CF0.toInt(),
        )
        const val PALETTE = 6

        /** Palette indices past the six: the cream of the peony's heart and the flower's inner layer, and the willow's gold. */
        const val CREAM = 6
        const val GOLD = 7
        const val CREAM_COLOUR = 0xFFFFF0CF.toInt()

        /** The sparks' streaks (the six, then cream and gold) and their heads, a step lighter. */
        val SPARK = intArrayOf(
            0xFFFFCF4D.toInt(), 0xFFFF6E5C.toInt(), 0xFF6EE59E.toInt(), 0xFF6FB7FF.toInt(), 0xFFFF8FC8.toInt(), 0xFFB89CFF.toInt(),
            0xFFFFF0CF.toInt(), 0xFFFFC447.toInt(),
        )
        val SPARK_HEAD = intArrayOf(
            0xFFFFEDB0.toInt(), 0xFFFFC2B5.toInt(), 0xFFC6F7D7.toInt(), 0xFFC6E2FF.toInt(), 0xFFFFD0E8.toInt(), 0xFFE0D3FF.toInt(),
            0xFFFFF8E8.toInt(), 0xFFFFE8A8.toInt(),
        )

        /** A palette index as paper: one of the six, the cream, or the willow's gold (the six's gold). */
        fun paperColour(index: Int): Int = when (index) {
            CREAM -> CREAM_COLOUR
            GOLD -> PAPER[0]
            else -> PAPER[index]
        }

        /** The flower's layers, outer to inner: the burst's colour, its second colour, cream. */
        fun flowerLayerColour(colour: Int, colour2: Int, layer: Int): Int = when (layer) {
            0 -> colour
            1 -> colour2
            else -> CREAM
        }

        /**
         * A petal's four corners, written into [out] as x0, y0 .. x3, y3: from [rIn] to [rOut] along [angle] round
         * ([cx], [cy]), [halfWidth] either side at [PETAL_WIDEST] of its length. Convex, so a GPU fan from its first corner
         * fills it exactly. The wallpaper's flower and the gallery card's are built by this one function.
         */
        @Suppress("LongParameterList")
        fun petalCorners(cx: Float, cy: Float, angle: Float, rIn: Float, rOut: Float, halfWidth: Float, out: FloatArray) {
            val c = cos(angle)
            val s = sin(angle)
            val m = rIn + (rOut - rIn) * PETAL_WIDEST
            out[0] = cx + c * rIn
            out[1] = cy + s * rIn
            out[2] = cx + c * m - s * halfWidth
            out[3] = cy + s * m + c * halfWidth
            out[4] = cx + c * rOut
            out[5] = cy + s * rOut
            out[6] = cx + c * m + s * halfWidth
            out[7] = cy + s * m - c * halfWidth
        }
    }
}
