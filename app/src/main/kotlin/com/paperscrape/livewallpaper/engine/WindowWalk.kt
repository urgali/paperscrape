package com.paperscrape.livewallpaper.engine

/**
 * How a person at a window walks out of it, and into another (v5.12).
 *
 * ### What it promises
 *
 * The maintainer's words of 2026-10-09, shown how the people changed windows: *«questo è sbagliato,
 * le persone devono "camminare" e uscire dalla visuale finestre, non sparire»*, and *«va fatto anche
 * per la chiusura, non solo per finestre accese/spente»*. So **nobody at a window appears or
 * disappears from one frame to the next, anywhere**:
 *
 * - **a person who changes window** -- in a house, and in a shop, the school or a bar while it is open
 *   ([WindowRoster]) -- walks sideways out of the window they stand at, hidden by the wall beside it,
 *   and walks into the other one from its side;
 * - **at closing** the people of a shop, the school, a bar and a tower walk out one at a time, and **at
 *   opening** they walk in one at a time ([Doorway]).
 *
 * Nothing fades and nothing shrinks in place of the walk: a person is drawn whole, at full strength,
 * and only the part of them inside the glass is drawn ([SceneCanvas.drawSpriteClipped]).
 *
 * ### The walk
 *
 * At [SPEED] units of the building a second (on the BV6600 a house's window is about ten pixels and a
 * unit three quarters of one), getting going over [EASE_SECONDS] and stopping over as long: from
 * standing, a person is out of a house's window in 2.4 to 2.6 seconds, and out of any window of the
 * streets in 2.1 to 2.9 (`WindowWalkTest`) -- `DESIGN_NOTES.md` §1, "Nothing moves quickly". A person
 * faces the way they walk (the busts are drawn looking toward +x, so walking toward -x they are drawn
 * mirrored) and is drawn as the artwork faces once they stand still.
 *
 * - **Along one floor** the walk is one stretch from one window's middle to the other's.
 * - **To another floor** it is three: out of the window, [STAIRS_SECONDS] out of sight, and into the
 *   other from its side.
 * - **In a house every window is its own room**, so a walker is seen through the window they leave and
 *   the one they come to, and nowhere between: across a three-window floor they pass behind the middle
 *   room. Toward the other window, or, straight above or below it, toward the nearer end of the house.
 * - **A shop, the school, a bar and a tower are one room a floor**, most of their windows side by side
 *   behind posts a few units wide -- narrower than a person, who crosses one seen in two windows at once
 *   -- so a walker is seen through every window of the floor they pass, as somebody walking behind a
 *   pillar is (behind a door or a wider wall, like the school's, they are out of sight a while). To
 *   another floor, or out and in at the hours, they go by the nearer end of the floor: the end on the
 *   side of the building's middle the window is on.
 *
 * ### The lights
 *
 * Where somebody stands, the window is lit, and **nobody is ever seen behind a window that is not**: a
 * house whose walker takes the light along lights the window they go to first -- over the two seconds
 * every light takes ([WindowRoster.FADE_SECONDS]), finished before they come into it -- and puts out
 * the one they left only once they are out of it ([litAmount]). A business's glass is lit all together
 * while it is open, and at closing its people set off before it is half lit ([WindowRoster.presentCount]);
 * with an opening span of half an hour or more the last of them is out of sight before it is, and with
 * the shortest the sliders give, a quarter of an hour, a doorway that waited for the roster's own walker
 * can leave them in sight down to 40 % (measured over every business of the twelve streets, v5.12C).
 *
 * ### What it costs
 *
 * A building's windows are measured once, when its roster is made ([Layout]); a roster change is a
 * function of the scene's time, as [WindowRoster]'s states are, so a walk is the same at the same
 * moment on every device and every golden. The walks at the hours are the one thing that remembers: the
 * hours move with the clock, a minute at a time, and a walk has to take seconds, so a building's
 * [Doorway] holds who is walking and since when -- a few numbers, kept with the building, carried over
 * when the list of objects is rebuilt. Where the hour jumps or the opening hours are changed, the scene is
 * cut rather than walked: the doorways take their numbers at once ([Doorway.advance]). A frame computes a
 * few positions into arrays it owns ([Figures]) and allocates nothing; it reads the roster's clock once for the
 * building, its lights included ([litAmount] with the [Figures]), and once the building's longest move is over
 * ([Layout.longestMove]) -- most of the minutes between two changes -- it does not look for who walked (v5.12C).
 */
internal object WindowWalk {

    /** Walking pace, in the building's own units a second. */
    const val SPEED = 5f

    /** Seconds to get going from standing still, and to come to a stop. */
    const val EASE_SECONDS = 0.6f

    /** Seconds out of sight between two floors: the stairs. */
    const val STAIRS_SECONDS = 2.5f

    /** Seconds between two people walking out, or in, one after the other at the hours. */
    const val DOORWAY_GAP_SECONDS = 1f

    /**
     * How far the scene's hour may move between two frames and still be the clock going on, in hours:
     * five minutes. The real clock moves it a minute at a time; a move of more than five minutes is a cut
     * ([Doorway.advance]).
     */
    const val HOUR_JUMP = 5f / 60f

    /** Whether the hour went from [before] to [now] by a jump rather than by the clock: see [HOUR_JUMP]. */
    fun hourJumped(before: Float, now: Float): Boolean {
        if (before.isNaN()) return true
        val d = kotlin.math.abs(now - before) % 24f
        return minOf(d, 24f - d) > HOUR_JUMP
    }

    /**
     * How far inside its pane a walker is cut, in the building's units: the pane less the inset its
     * light is laid at ([SceneObjectRenderer.LIT_PANE_INSET]), for the same reason -- the glass mask's
     * cut edge wobbles by up to 0.3 of a unit, and a person must not cross onto the frame.
     */
    const val PANE_INSET = SceneObjectRenderer.LIT_PANE_INSET

    /**
     * A window bust's share of its pane, `drawWindowOccupant`'s 0.85: the bust is drawn at
     * `pane x 0.85 / 60` ([SceneObjectRenderer.WINDOW_OCCUPANT_DIVISOR_UNITS]). `WindowWalkTest` reads
     * the renderer's own expression and holds the two together.
     */
    const val BUST_PANE_SHARE = 0.85f

    /** A window bust's canvas width in its own units (147 px at three to the unit). */
    const val WINDOW_HEAD_CANVAS_UNITS = 49f

    /** The scale a bust is drawn at in a pane [paneWidth] wide. */
    fun bustScale(paneWidth: Float): Float = paneWidth * BUST_PANE_SHARE / SceneObjectRenderer.WINDOW_OCCUPANT_DIVISOR_UNITS

    /**
     * How far a walker's drawing reaches **behind** them, from where they stand: the anchor's side of the
     * canvas, whichever way they face -- facing +x the canvas runs from `-anchor` to `canvas - anchor`,
     * and mirrored from `-(canvas - anchor)` to `anchor`.
     */
    fun trailing(paneWidth: Float): Float = SceneObjectRenderer.WINDOW_HEAD_ANCHOR_X_UNITS * bustScale(paneWidth)

    /** How far a walker's drawing reaches ahead of them. */
    fun leading(paneWidth: Float): Float = (WINDOW_HEAD_CANVAS_UNITS - SceneObjectRenderer.WINDOW_HEAD_ANCHOR_X_UNITS) * bustScale(paneWidth)

    /** The left edge of a bust standing at [x] facing [facing] (+1 or -1) in a pane [paneWidth] wide. */
    fun bustLeft(x: Float, facing: Int, paneWidth: Float): Float = if (facing > 0) x - trailing(paneWidth) else x - leading(paneWidth)

    /** The right edge of the same bust. */
    fun bustRight(x: Float, facing: Int, paneWidth: Float): Float = if (facing > 0) x + leading(paneWidth) else x + trailing(paneWidth)

    // ------------------------------------------------------------------------------- the windows

    /**
     * A building's windows, in its own units (the coordinates its pieces are drawn in, each pane's top
     * with its piece's foot added), in the building's numbering -- window `k` of the roster is window `k`
     * here. Measured once, when the building's roster is made.
     */
    class Layout internal constructor(
        /** How many windows. */
        val windows: Int,
        /** Whether a walker is seen through every window of the floor ([WindowWalk]: one room a floor). */
        val open: Boolean,
    ) {
        val left = FloatArray(windows)
        val top = FloatArray(windows)
        val width = FloatArray(windows)
        val height = FloatArray(windows)

        /** The floor each window is on: windows of one piece at one height share it. */
        val row = IntArray(windows)

        /** The leftmost and the rightmost visible edge of each window's floor. */
        val rowLeft = FloatArray(windows)
        val rowRight = FloatArray(windows)

        /**
         * The longest of the roster's moves in this building, from any window to any other ([moveTime]),
         * in seconds: once this long has passed since the roster's last change, its walker -- if it moved
         * one -- is standing, and [figuresAt] does not ask who it was (v5.12C).
         */
        var longestMove = 0.0
            internal set

        fun centre(window: Int): Float = left[window] + width[window] * 0.5f

        fun bottom(window: Int): Float = top[window] + height[window]

        /** The visible edge of [window] on side [direction]: its pane less [PANE_INSET]. */
        fun edge(window: Int, direction: Int): Float =
            if (direction > 0) left[window] + width[window] - PANE_INSET else left[window] + PANE_INSET

        /** The end of [window]'s floor on side [direction]. */
        fun rowEdge(window: Int, direction: Int): Float = if (direction > 0) rowRight[window] else rowLeft[window]

        /** Where a walker leaving or entering [window] on side [direction] is out of sight: its pane, or its floor. */
        fun limit(window: Int, direction: Int): Float = if (open) rowEdge(window, direction) else edge(window, direction)

        /**
         * The way out of [window] by the nearer end of its floor: toward the side of the building's
         * middle (x = 0) the window is on; a window on the middle by its index, so the two of a pair
         * go opposite ways.
         */
        fun outward(window: Int): Int {
            val c = centre(window)
            return when {
                c > 0.5f -> 1
                c < -0.5f -> -1
                window % 2 == 0 -> -1
                else -> 1
            }
        }
    }

    /** [deal]'s windows, measured: see [Layout]. [open]: a business, one room a floor. */
    fun layoutOf(deal: NeighbourhoodComposer.Deal, open: Boolean): Layout {
        val layout = Layout(deal.windowCount.coerceAtMost(WindowRoster.MAX_PANES), open)
        var rows = 0
        for (index in 0 until deal.size) {
            val placed = deal[index]
            val windows = placed.piece.windows
            for (k in windows.indices) {
                val w = placed.firstWindow + k
                if (w >= layout.windows) break
                val box = windows[k]
                layout.left[w] = box.x
                layout.top[w] = placed.baseY + box.y
                layout.width[w] = box.w
                layout.height[w] = box.h
                var row = -1
                for (other in placed.firstWindow until w) {
                    if (kotlin.math.abs(layout.top[other] - layout.top[w]) < 0.5f && kotlin.math.abs(layout.height[other] - layout.height[w]) < 0.5f) {
                        row = layout.row[other]
                        break
                    }
                }
                layout.row[w] = if (row >= 0) row else rows++
            }
        }
        for (w in 0 until layout.windows) {
            var lo = Float.MAX_VALUE
            var hi = -Float.MAX_VALUE
            for (other in 0 until layout.windows) {
                if (layout.row[other] != layout.row[w]) continue
                lo = minOf(lo, layout.edge(other, -1))
                hi = maxOf(hi, layout.edge(other, 1))
            }
            layout.rowLeft[w] = lo
            layout.rowRight[w] = hi
        }
        var longest = 0.0
        for (from in 0 until layout.windows) {
            for (to in 0 until layout.windows) {
                if (from != to) longest = maxOf(longest, moveTime(layout, from, to))
            }
        }
        layout.longestMove = longest
        return layout
    }

    // ------------------------------------------------------------------------------- the pace

    /**
     * How far a walker who set off from standing has gone after [t] seconds: getting going over
     * [EASE_SECONDS], then at [SPEED].
     */
    fun setOff(t: Double): Float {
        if (t <= 0.0) return 0f
        val ease = EASE_SECONDS.toDouble()
        return if (t < ease) (0.5 * SPEED / ease * t * t).toFloat() else (0.5 * SPEED * ease + SPEED * (t - ease)).toFloat()
    }

    /** How long [setOff] takes to cover [distance]. */
    fun setOffTime(distance: Float): Double {
        if (distance <= 0f) return 0.0
        val ease = EASE_SECONDS.toDouble()
        val eased = 0.5 * SPEED * ease
        return if (distance < eased) kotlin.math.sqrt(2.0 * distance * ease / SPEED) else ease + (distance - eased) / SPEED
    }

    /** How long a walk of [distance] takes from standing to standing. */
    fun walkTime(distance: Float): Double {
        if (distance <= 0f) return 0.0
        // Long enough to reach the walking pace; the shortest walk here is a pane's width, which is.
        val ease = EASE_SECONDS.toDouble()
        val cruise = distance - SPEED * ease
        return if (cruise >= 0f) ease * 2 + cruise / SPEED else 2.0 * kotlin.math.sqrt(distance * ease / SPEED)
    }

    /** How far along a walk of [distance], from standing to standing, the walker is after [t] seconds. */
    fun walked(distance: Float, t: Double): Float {
        val total = walkTime(distance)
        if (t <= 0.0) return 0f
        if (t >= total) return distance
        val ease = EASE_SECONDS.toDouble()
        if (distance < SPEED * ease) {
            // Too short to reach the pace: speed up for half, slow down for half.
            val half = total / 2
            val a = distance / (half * half)
            return if (t < half) (0.5 * a * t * t).toFloat() else (distance - 0.5 * a * (total - t) * (total - t)).toFloat()
        }
        if (t > total - ease) {
            val left = total - t
            return (distance - 0.5 * SPEED / ease * left * left).toFloat()
        }
        return setOff(t)
    }

    /** When a walk of [distance] has covered [covered] of it: the inverse of [walked]. */
    fun walkedTime(distance: Float, covered: Float): Double {
        val total = walkTime(distance)
        if (covered <= 0f) return 0.0
        if (covered >= distance) return total
        val ease = EASE_SECONDS.toDouble()
        if (distance < SPEED * ease) {
            val half = total / 2
            val a = distance / (half * half)
            return if (covered <= distance / 2) kotlin.math.sqrt(2.0 * covered / a) else total - kotlin.math.sqrt(2.0 * (distance - covered) / a)
        }
        val tail = 0.5 * SPEED * ease
        if (covered > distance - tail) return total - kotlin.math.sqrt(2.0 * (distance - covered) * ease / SPEED)
        return setOffTime(covered)
    }

    /** How far a walker who sets off from [window] toward [direction] goes until nothing of them is in sight. */
    fun exitDistance(layout: Layout, window: Int, direction: Int): Float =
        direction * (layout.limit(window, direction) - layout.centre(window)) + trailing(layout.width[window])

    /** How far a walker comes, toward [direction], from out of sight to standing at [window]. */
    fun entryDistance(layout: Layout, window: Int, direction: Int): Float =
        direction * (layout.centre(window) - layout.limit(window, -direction)) + leading(layout.width[window])

    /** Seconds from standing at [window] to out of sight toward [direction]. */
    fun exitTime(layout: Layout, window: Int, direction: Int): Double = setOffTime(exitDistance(layout, window, direction))

    /** Seconds from out of sight to standing at [window], walking toward [direction]. */
    fun entryTime(layout: Layout, window: Int, direction: Int): Double = setOffTime(entryDistance(layout, window, direction))

    // ------------------------------------------------------------------------------- a move

    /** Which way a person walks out of [from] when the roster sends them to [to]. */
    fun exitDirection(layout: Layout, from: Int, to: Int): Int {
        if (layout.row[from] == layout.row[to]) return if (layout.centre(to) >= layout.centre(from)) 1 else -1
        if (layout.open) return layout.outward(from)
        val dx = layout.centre(to) - layout.centre(from)
        return if (dx >= 1f) 1 else if (dx <= -1f) -1 else layout.outward(from)
    }

    /** Which way a person walks into [to] when the roster sends them there from [from]. */
    fun entryDirection(layout: Layout, from: Int, to: Int): Int {
        if (layout.row[from] == layout.row[to]) return exitDirection(layout, from, to)
        if (layout.open) return -layout.outward(to)
        return exitDirection(layout, from, to)
    }

    /** How long the roster's move from window [from] to window [to] takes, from its tick. */
    fun moveTime(layout: Layout, from: Int, to: Int): Double {
        if (layout.row[from] == layout.row[to]) return walkTime(kotlin.math.abs(layout.centre(to) - layout.centre(from)))
        return exitTime(layout, from, exitDirection(layout, from, to)) + STAIRS_SECONDS +
            entryTime(layout, to, entryDirection(layout, from, to))
    }

    /**
     * When, after its tick, the walker of a move from [from] to [to] is out of sight of [from] -- the
     * moment the window they left may go dark.
     */
    fun clearOfTime(layout: Layout, from: Int, to: Int): Double {
        val direction = exitDirection(layout, from, to)
        if (layout.row[from] != layout.row[to]) return exitTime(layout, from, direction)
        val distance = kotlin.math.abs(layout.centre(to) - layout.centre(from))
        val clear = direction * (layout.edge(from, direction) - layout.centre(from)) + trailing(layout.width[from])
        return walkedTime(distance, clear)
    }

    /** When, after its tick, the walker of a move from [from] to [to] first comes into sight of [to]. */
    fun inSightOfTime(layout: Layout, from: Int, to: Int): Double {
        val direction = entryDirection(layout, from, to)
        if (layout.row[from] != layout.row[to]) return exitTime(layout, from, exitDirection(layout, from, to)) + STAIRS_SECONDS
        val distance = kotlin.math.abs(layout.centre(to) - layout.centre(from))
        val reach = distance - (direction * (layout.centre(to) - layout.edge(to, -direction)) + leading(layout.width[to]))
        return walkedTime(distance, reach)
    }

    // ------------------------------------------------------------------------------- the lights

    /**
     * How lit a house's pane [pane] is at [elapsed], 0 .. 1 -- [WindowRoster.litAmount] with each fade
     * started when it has to: a light that comes on, at the change; a light put out by a walker who
     * takes it along, once they are out of that window; any other light put out, at the change.
     */
    fun litAmount(plan: WindowRoster.Plan, layout: Layout, elapsed: SceneTime, pane: Int): Float {
        val tick = WindowRoster.tickAt(plan, elapsed)
        val now = WindowRoster.stateIndex(tick)
        val before = if (tick > 0L) WindowRoster.stateIndex(tick - 1) else now
        return litAmount(plan, layout, tick, now, before, elapsed.seconds - WindowRoster.tickTime(plan, tick), pane)
    }

    /**
     * [litAmount] at the moment [figures] was filled for ([figuresAt], the same building): the roster's
     * clock read once for the building rather than once a pane -- the renderer's form (v5.12C), the same
     * numbers.
     */
    fun litAmount(plan: WindowRoster.Plan, layout: Layout, figures: Figures, pane: Int): Float =
        litAmount(plan, layout, figures.tick, figures.stateNow, figures.stateBefore, figures.since, pane)

    private fun litAmount(plan: WindowRoster.Plan, layout: Layout, tick: Long, now: Int, before: Int, since: Double, pane: Int): Float {
        val state = plan.states[now]
        if (tick == 0L) return if (WindowRoster.litMask(state) and (1 shl pane) != 0) 1f else 0f
        val previous = plan.states[before]
        val wasLit = WindowRoster.litMask(previous) and (1 shl pane) != 0
        val isLit = WindowRoster.litMask(state) and (1 shl pane) != 0
        if (wasLit && !isLit) {
            val mover = WindowRoster.moverBetween(plan, before, now)
            if (mover >= 0 && plan.seat(before, mover) == pane) {
                val clear = clearOfTime(layout, pane, plan.seat(now, mover))
                return WindowRoster.litAmount(state, previous, WindowRoster.eased(since - clear), pane)
            }
        }
        return WindowRoster.litAmount(state, previous, WindowRoster.eased(since), pane)
    }

    // ------------------------------------------------------------------------------- the hours

    /**
     * Who of a business is in at its hours, and who is walking out or in: the one part of the windows
     * that remembers (see [WindowWalk]). Kept with the building, made fresh with it, and handed on when
     * the list of objects is rebuilt ([copyFrom]).
     */
    class Doorway {
        /** How many people are in: the first [shown] by their order ([WindowRoster.Plan.seat]); -1 not yet seen. */
        var shown = -1
            private set

        /** The person walking out or in, or -1. */
        var walker = -1
            private set

        /** Whether [walker] is walking out. */
        var leaving = false
            private set

        /** The window [walker] walks out of, or into. */
        var window = -1
            private set

        /** The way [walker] walks: +1 toward +x. */
        var direction = 1
            private set

        /** When the walk began, in seconds of the scene's clock, and how long it takes. */
        var start = 0.0
            private set
        var duration = 0.0
            private set

        /** Not before this time does the next walk begin. */
        private var calm = 0.0

        fun copyFrom(other: Doorway) {
            shown = other.shown
            walker = other.walker
            leaving = other.leaving
            window = other.window
            direction = other.direction
            start = other.start
            duration = other.duration
            calm = other.calm
        }

        /**
         * The doorway at [elapsed], the building [openness] open: a walk that has ended is counted, and
         * when fewer or more people should be in than are, the next one walks -- out, the last in the
         * order first, or in, the first missing -- unless the roster is walking them, or will move them
         * before they would be in.
         *
         * **Seen for the first time, or with [settle], it takes the number it should have without
         * anybody walking**: a scene that starts is not a change, and nor is a cut -- the hour jumping
         * (the screen off for an hour, a fixed hour moved, the phone's clock set: the sky and the lights
         * jump with it) or the opening hours changed (a group's switch or its hours: its glass changes at
         * once). People walk out and in when the hours close and open the building in front of you, a
         * minute at a time; walked out after a cut, they would walk behind glass already gone dark. Any
         * other change of the settings is no cut: whoever is walking goes on.
         */
        fun advance(plan: WindowRoster.Plan, layout: Layout, elapsed: SceneTime, openness: Float, settle: Boolean = false) {
            val target = WindowRoster.presentCount(plan.people, openness)
            val t = elapsed.seconds
            if (shown < 0 || settle) {
                shown = target
                walker = -1
                calm = 0.0
                return
            }
            if (walker >= 0) {
                if (t < start + duration) return
                shown += if (leaving) -1 else 1
                walker = -1
                calm = t + DOORWAY_GAP_SECONDS
            }
            if (shown == target || t < calm) return
            val out = target < shown
            val person = if (out) shown - 1 else shown
            val tick = WindowRoster.tickAt(plan, elapsed)
            val now = WindowRoster.stateIndex(tick)
            if (tick > 0L) {
                val before = WindowRoster.stateIndex(tick - 1)
                if (WindowRoster.moverBetween(plan, before, now) == person &&
                    t - WindowRoster.tickTime(plan, tick) < moveTime(layout, plan.seat(before, person), plan.seat(now, person))
                ) {
                    return
                }
            }
            val seat = plan.seat(now, person)
            val way = if (out) layout.outward(seat) else -layout.outward(seat)
            val time = if (out) exitTime(layout, seat, way) else entryTime(layout, seat, way)
            if (!out) {
                // Walking in, they must still be at the same window when they get there.
                val next = WindowRoster.stateIndex(tick + 1)
                val nextTime = WindowRoster.tickTime(plan, tick + 1)
                if (WindowRoster.moverBetween(plan, now, next) == person && nextTime < t + time + DOORWAY_GAP_SECONDS) return
            }
            walker = person
            leaving = out
            window = seat
            direction = way
            start = t
            duration = time
        }
    }

    // ------------------------------------------------------------------------------- the frame

    /**
     * The people of one building at one moment, standing or walking: where each one is and which way
     * they face, and through which windows they may be seen. Filled by [figuresAt] into arrays owned
     * here, reused from building to building and frame to frame.
     */
    class Figures {
        var count = 0
            internal set

        /** Who: a person of the roster ([WindowRoster.Plan.origin] deals their look). */
        val person = IntArray(MAX)

        /** The window whose pane sizes the bust and whose sill it stands on. */
        val window = IntArray(MAX)

        /** Where they stand, along the building, in its units. */
        val x = FloatArray(MAX)

        /** +1 facing +x, -1 facing -x (drawn mirrored). */
        val facing = IntArray(MAX)

        /** The window they stand still at, drawn as they always were; -1 while walking. */
        val still = IntArray(MAX)

        /** In a house, the two windows a walker may be seen through (-1 for none); a business's whole floor. */
        val through = IntArray(MAX * 2)

        /**
         * The roster's clock at the moment [figuresAt] filled these: its tick, the state shown and the one
         * before it, and the seconds since the tick -- what [litAmount] reads for every pane of the building.
         */
        var tick = 0L
            internal set
        var stateNow = 0
            internal set
        var stateBefore = 0
            internal set
        var since = 0.0
            internal set

        internal fun add(person: Int, window: Int, x: Float, facing: Int, still: Int, a: Int, b: Int) {
            if (count == MAX) return
            this.person[count] = person
            this.window[count] = window
            this.x[count] = x
            this.facing[count] = facing
            this.still[count] = still
            through[count * 2] = a
            through[count * 2 + 1] = b
            count++
        }

        /** Whether walker [figure] may be seen through [pane] of [layout]: its floor, and in a house one of its two windows. */
        fun seenThrough(figure: Int, pane: Int, layout: Layout): Boolean {
            if (layout.row[pane] != layout.row[window[figure]]) return false
            if (layout.open) return true
            return through[figure * 2] == pane || through[figure * 2 + 1] == pane
        }

        companion object {
            const val MAX = WindowRoster.MAX_PANES
        }
    }

    /**
     * The people of [plan]'s building at [elapsed] into [out]: everyone in ([Doorway.shown] of them, or
     * all of them without a doorway), standing at their window or walking the roster's last move, and
     * the doorway's walker out or in.
     */
    fun figuresAt(plan: WindowRoster.Plan, layout: Layout, elapsed: SceneTime, doorway: Doorway?, out: Figures) {
        out.count = 0
        val t = elapsed.seconds
        val tick = WindowRoster.tickAt(plan, elapsed)
        val now = WindowRoster.stateIndex(tick)
        val before = if (tick > 0L) WindowRoster.stateIndex(tick - 1) else now
        val since = t - WindowRoster.tickTime(plan, tick)
        out.tick = tick
        out.stateNow = now
        out.stateBefore = before
        out.since = since
        // Whoever the last change moved has arrived once the building's longest move is over: from then
        // until the next change -- most of the 100 to 160 seconds between two -- nobody is asked about.
        val mover = if (since < layout.longestMove) WindowRoster.moverBetween(plan, before, now) else -1
        val shown = if (doorway == null || doorway.shown < 0) plan.people else doorway.shown
        val walker = doorway?.walker ?: -1
        for (person in 0 until plan.people) {
            if (person == walker) {
                addDoorwayWalker(layout, doorway!!, t, out)
                continue
            }
            if (person >= shown) continue
            val seat = plan.seat(now, person)
            if (person == mover) {
                val from = plan.seat(before, person)
                if (since < moveTime(layout, from, seat)) {
                    addMover(layout, person, from, seat, since, out)
                    continue
                }
            }
            out.add(person, seat, layout.centre(seat), 1, seat, -1, -1)
        }
    }

    private fun addDoorwayWalker(layout: Layout, doorway: Doorway, t: Double, out: Figures) {
        val window = doorway.window
        val way = doorway.direction
        val walked = t - doorway.start
        val x = if (doorway.leaving) {
            layout.centre(window) + way * setOff(walked)
        } else {
            layout.centre(window) - way * setOff(doorway.duration - walked)
        }
        out.add(doorway.walker, window, x, way, -1, window, -1)
    }

    private fun addMover(layout: Layout, person: Int, from: Int, to: Int, since: Double, out: Figures) {
        if (layout.row[from] == layout.row[to]) {
            val distance = kotlin.math.abs(layout.centre(to) - layout.centre(from))
            val way = if (layout.centre(to) >= layout.centre(from)) 1 else -1
            out.add(person, from, layout.centre(from) + way * walked(distance, since), way, -1, from, to)
            return
        }
        val outWay = exitDirection(layout, from, to)
        val exit = exitTime(layout, from, outWay)
        if (since < exit) {
            out.add(person, from, layout.centre(from) + outWay * setOff(since), outWay, -1, from, -1)
            return
        }
        val inWay = entryDirection(layout, from, to)
        val entry = entryTime(layout, to, inWay)
        val left = exit + STAIRS_SECONDS + entry - since
        if (left > entry) return // on the stairs: out of sight
        out.add(person, to, layout.centre(to) - inWay * setOff(left), inWay, -1, to, -1)
    }
}
