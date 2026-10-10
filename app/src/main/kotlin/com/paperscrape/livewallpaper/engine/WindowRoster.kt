package com.paperscrape.livewallpaper.engine

/**
 * Who stands at a building's windows, and -- in a house -- which of its windows are lit at night, at
 * each moment of the scene's clock (v5.12).
 *
 * ### What it promises
 *
 * The maintainer's decisions of 2026-10-09, to the table that said each of these (his words to it:
 * *«il resto si a tutto»*; and before it, *«le persone non devono rimanere alle stesse identiche
 * finestre ma devono poter affacciarsi anche alle nuove»*, *«farei accese 2 su 4 come ratio; inoltre
 * [...] non devono essere accese sempre le stesse ma in modo randomico»*):
 *
 * - **A house lights half its windows at night** -- [litCount]: half its panes, a half rounded up,
 *   and every window somebody stands at among them, so a house whose people are more than half its
 *   windows lights all of theirs. The others stay dark glass (`SceneObjectRenderer.UNLIT_GLASS_NIGHT`).
 * - **The number of people at a building is the one it always had** (`WindowOccupants.occupantCount`
 *   over `NeighbourhoodComposer.Deal.peopleWindows`), and **they may stand at any of its windows**.
 * - **Both change with time, one thing at a time, while you watch, and no light ever blinks.** Every
 *   [TICK_MIN_SECONDS] to [TICK_MAX_SECONDS] -- a building's own interval, from its seed -- one thing
 *   changes in it: a lit window goes dark as another lights (over [FADE_SECONDS], a fade and not a
 *   flicker), or a person leaves a window for another; a house alternates the two, so its people move
 *   every other change. Never two changes at once in one building, and where there is a person the
 *   window is lit. **A person who leaves a window walks out of it, and walks into the next one**
 *   ([WindowWalk]: the maintainer's *«le persone devono "camminare" e uscire dalla visuale finestre,
 *   non sparire»*, 2026-10-09) -- the same person, who keeps their look from window to window. So with
 *   a dozen houses on the screen something changes somewhere every ten seconds or so, and any one house
 *   every couple of minutes: as slow as the scene's motion is meant to be (`DESIGN_NOTES.md` §1,
 *   "Nothing moves quickly"). A shop and the school move a person at each change while they are open
 *   (their glass all lights together, as it always has); at closing they walk out one at a time, and
 *   at opening they walk in ([WindowWalk.Doorway]). A tower's people stay where they are, and walk out
 *   and in with its hours too.
 *
 * ### How, and what it costs
 *
 * A building's whole future is worked out **once**, when the scene's objects are built ([plan]): its
 * first state -- the people placed by the rule of before v5.12 (the same seed, channel and address,
 * ranked over all the windows the building has now), the lights by a seeded order -- and then [STEPS]
 * states, each one change from the one before, chosen by the building's own seed. The scene's clock
 * picks which state shows ([stateAt]): a tick every [Plan.period] seconds from [Plan.phase], walking the
 * states forward and then back again, so that after [STEPS] changes the building retraces them one by
 * one -- forty minutes to an hour each way -- and the walk never ends and never jumps. So the picture is
 * a function of the scene's time and the seeds and of nothing else: the same moment is the same image
 * on every device, every run, every golden; a rebuilt list of objects picks up exactly where the old
 * one was; and a frame reads a few numbers from arrays and allocates nothing (`FrameAllocationTest`).
 *
 * A state is one `Long`: the lit panes in the low 32 bits (a house's only), the occupied windows in
 * the high 32. A house's window `k` is its pane `k` (`BuildingPiece.panes`). **Who** stands at each
 * occupied window is [Plan.seat]: person `p` is the one whose window ranked `p` in the first state --
 * the order of before, in which the last leave first at closing -- and keeps the look dealt at that
 * first window ([Plan.origin]) wherever they go.
 */
internal object WindowRoster {

    /** The shortest and the longest time between two changes in one building, in seconds. */
    const val TICK_MIN_SECONDS = 100f
    const val TICK_MAX_SECONDS = 160f

    /** How many changes a building makes before it walks them back, one by one. */
    const val STEPS = 24

    /** How long a window takes to go dark, or to light, in seconds of the scene's clock. */
    const val FADE_SECONDS = 2f

    /** The most windows or panes a building can have here: one bit each in half a `Long`. */
    const val MAX_PANES = 32

    // Channels of [CandidateNoise] of their own, beside the distant houses' 512-515.
    private const val CH_LIT_ORDER = 520
    private const val CH_TICK_PERIOD = 521
    private const val CH_TICK_PHASE = 522
    private const val CH_STEP_FROM = 523
    private const val CH_STEP_TO = 524

    /** A building's roster: its counts, its clock and the [STEPS] + 1 states it walks through. */
    class Plan internal constructor(
        /** The windows a person may stand at; in a house, panes `0 until windows`. */
        val windows: Int,
        /** A house's panes of glass, every one a window its night lights or leaves dark; 0 for the others. */
        val panes: Int,
        /** How many people stand at the windows. */
        val people: Int,
        /** A house: how many of its panes are lit at night ([litCount]); 0 for the others. */
        val lit: Int,
        /** Seconds between two changes. */
        val period: Double,
        /** Where in its first interval the building's clock starts, in seconds. */
        val phase: Double,
        /** Each window's place in the order of before ([WindowOccupants.rankOf]): at closing the last leave first. */
        internal val ranks: IntArray,
        /** The states, [STEPS] + 1 of them; see [WindowRoster]. */
        internal val states: LongArray,
        /** The window person `p` stands at in state `s`: `seats[s * people + p]`. */
        internal val seats: ByteArray,
        /** The window person `p` stood at in the first state, whose address deals their look. */
        internal val origins: IntArray,
    ) {
        /** The window person [person] stands at in state [stateIndex]. */
        fun seat(stateIndex: Int, person: Int): Int = seats[stateIndex * people + person].toInt()

        /** The window whose address deals [person]'s look: where they stood in the first state. */
        fun origin(person: Int): Int = origins[person]
    }

    /**
     * How many of a house's [panes] are lit at night: half, a half rounded up -- 2 of 4, 3 of 5 -- and
     * never fewer than its [people], every one of whom stands at a lit window.
     */
    fun litCount(panes: Int, people: Int): Int = maxOf((panes + 1) / 2, people).coerceAtMost(panes)

    /**
     * The roster of one building: [windows] a person may stand at, [panes] of glass (a house's; 0 for
     * any other building, whose glass lights all together), [people] at the windows, and whether it is
     * a [house] -- which alone lights some panes and not others. [seed] and [buildingSeed] are the
     * occupants' own (the theme's and the building's, `WindowOccupants`). With [moves] false nobody
     * moves -- a tower's people stay where they stand; every state is the first.
     */
    fun plan(seed: Int, buildingSeed: Int, windows: Int, panes: Int, people: Int, house: Boolean, moves: Boolean = true): Plan {
        val w = windows.coerceIn(0, MAX_PANES)
        val p = if (house) panes.coerceIn(w, MAX_PANES) else 0
        val d = people.coerceIn(0, w)
        val lit = if (house) litCount(p, d) else 0
        val ranks = IntArray(w) { WindowOccupants.rankOf(seed, buildingSeed, it, w) }
        val states = LongArray(STEPS + 1)
        val seats = ByteArray((STEPS + 1) * d)
        val origins = IntArray(d)
        for (window in 0 until w) if (ranks[window] < d) origins[ranks[window]] = window
        var state = initialState(seed, buildingSeed, w, p, d, lit, ranks, house)
        states[0] = state
        for (person in 0 until d) seats[person] = origins[person].toByte()
        for (step in 1..STEPS) {
            val next = if (moves) next(state, step, seed, buildingSeed, w, p, house) else state
            // The person who left a window is the one now at the window that came: carry them over.
            val left = peopleMask(state) and peopleMask(next).inv()
            val came = peopleMask(next) and peopleMask(state).inv()
            for (person in 0 until d) {
                val before = seats[(step - 1) * d + person]
                seats[step * d + person] =
                    if (left != 0 && before.toInt() == Integer.numberOfTrailingZeros(left)) Integer.numberOfTrailingZeros(came).toByte() else before
            }
            state = next
            states[step] = state
        }
        val period = TICK_MIN_SECONDS + (TICK_MAX_SECONDS - TICK_MIN_SECONDS) *
            CandidateNoise.value(seed, buildingSeed, CH_TICK_PERIOD).toDouble()
        val phase = period * CandidateNoise.value(seed, buildingSeed, CH_TICK_PHASE).toDouble()
        return Plan(w, p, d, lit, period, phase, ranks, states, seats, origins)
    }

    /**
     * The first state: the people at the windows whose rank is below their number -- the rule that
     * placed them until v5.12 -- and, in a house, their windows lit and as many more panes as it takes
     * to reach [lit], in an order the building's own seed deals.
     */
    private fun initialState(seed: Int, buildingSeed: Int, windows: Int, panes: Int, people: Int, lit: Int, ranks: IntArray, house: Boolean): Long {
        var occupied = 0
        for (w in 0 until windows) if (ranks[w] < people) occupied = occupied or (1 shl w)
        if (!house) return pack(0, occupied)
        var lights = occupied
        var count = Integer.bitCount(lights)
        while (count < lit) {
            var best = -1
            var lowest = Float.MAX_VALUE
            for (pane in 0 until panes) {
                if (lights and (1 shl pane) != 0) continue
                val key = CandidateNoise.value(seed, WindowOccupants.address(buildingSeed, pane), CH_LIT_ORDER)
                if (key < lowest) { lowest = key; best = pane }
            }
            if (best < 0) break
            lights = lights or (1 shl best)
            count++
        }
        return pack(lights, occupied)
    }

    /**
     * One change from [state], at [step]: a light on odd steps and a person on even ones where each is
     * possible, the other where it is not, and nothing where neither is.
     *
     * - **A light**: a lit pane with nobody at it goes dark and a dark pane lights.
     * - **A person**: somebody leaves a window for a lit one nobody stands at. In a house where no lit
     *   window is free, they take the light with them -- the window they leave goes dark as the one
     *   they come to lights -- so the house still lights as many windows as it did, one person still
     *   moves, and nobody stands at dark glass. Every other building's glass is lit all together, so any free
     *   window is a lit one.
     */
    private fun next(state: Long, step: Int, seed: Int, buildingSeed: Int, windows: Int, panes: Int, house: Boolean): Long {
        val address = buildingSeed * STEP_STRIDE + step
        val from = CandidateNoise.value(seed, address, CH_STEP_FROM)
        val to = CandidateNoise.value(seed, address, CH_STEP_TO)
        if (!house) return movePerson(state, from, to, windows, house = false, carryLight = false)
        val first = if (step % 2 == 1) swapLight(state, from, to, panes) else movePerson(state, from, to, windows, house = true, carryLight = false)
        if (first != state) return first
        val second = if (step % 2 == 1) movePerson(state, from, to, windows, house = true, carryLight = false) else swapLight(state, from, to, panes)
        if (second != state) return second
        return movePerson(state, from, to, windows, house = true, carryLight = true)
    }

    private fun swapLight(state: Long, from: Float, to: Float, panes: Int): Long {
        val lights = litMask(state)
        val people = peopleMask(state)
        val all = maskOf(panes)
        val off = lights and people.inv() and all
        val on = all and lights.inv()
        if (off == 0 || on == 0) return state
        return pack(lights and pick(off, from).inv() or pick(on, to), people)
    }

    private fun movePerson(state: Long, from: Float, to: Float, windows: Int, house: Boolean, carryLight: Boolean): Long {
        val lights = litMask(state)
        val people = peopleMask(state)
        if (people == 0) return state
        val all = maskOf(windows)
        val free = when {
            !house -> all and people.inv()
            carryLight -> all and lights.inv() and people.inv()
            else -> all and people.inv() and lights
        }
        if (free == 0) return state
        val leaving = pick(people, from)
        val coming = pick(free, to)
        val movedPeople = people and leaving.inv() or coming
        val movedLights = if (carryLight) lights and leaving.inv() or coming else lights
        return pack(movedLights, movedPeople)
    }

    /** The [u]-th share of [mask]'s set bits, as a mask of that one bit. */
    private fun pick(mask: Int, u: Float): Int {
        val n = Integer.bitCount(mask)
        var k = (u * n).toInt().coerceIn(0, n - 1)
        var m = mask
        while (true) {
            val low = m and -m
            if (k == 0) return low
            m = m and low.inv()
            k--
        }
    }

    private fun maskOf(count: Int): Int = if (count >= 32) -1 else (1 shl count) - 1

    private fun pack(lights: Int, people: Int): Long = (people.toLong() shl 32) or (lights.toLong() and 0xFFFFFFFFL)

    /** The lit panes of [state], one bit each. */
    fun litMask(state: Long): Int = state.toInt()

    /** The occupied windows of [state], one bit each. */
    fun peopleMask(state: Long): Int = (state ushr 32).toInt()

    /** How far apart two buildings' step addresses are. */
    private const val STEP_STRIDE = 97

    // ---------------------------------------------------------------- the clock

    /** Which tick of its clock [plan] is at [elapsed]: 0 until its first change, then one more each [Plan.period]. */
    fun tickAt(plan: Plan, elapsed: SceneTime): Long =
        kotlin.math.floor((elapsed.seconds + plan.phase) / plan.period).toLong().coerceAtLeast(0L)

    /** The scene time at which tick [tick] of [plan] happens: its change starts there. */
    fun tickTime(plan: Plan, tick: Long): Double = tick * plan.period - plan.phase

    /** The state shown at tick [tick]: forward through the [STEPS] states, then back, and again. */
    fun stateIndex(tick: Long): Int {
        val m = (tick % (2L * STEPS)).toInt()
        return if (m <= STEPS) m else 2 * STEPS - m
    }

    /** The state [plan] shows at [elapsed]. */
    fun stateAt(plan: Plan, elapsed: SceneTime): Long = plan.states[stateIndex(tickAt(plan, elapsed))]

    /** The state [plan] showed before its last change: the one its lights fade from, and its walker leaves. */
    fun previousStateAt(plan: Plan, elapsed: SceneTime): Long {
        val tick = tickAt(plan, elapsed)
        return plan.states[stateIndex(if (tick > 0L) tick - 1 else 0L)]
    }

    /**
     * The person whose window differs between state [before] and state [after] -- the one a change
     * moved -- or -1 when the change moved nobody. A change moves one person at most.
     */
    fun moverBetween(plan: Plan, before: Int, after: Int): Int {
        if (before == after) return -1
        for (person in 0 until plan.people) {
            if (plan.seat(before, person) != plan.seat(after, person)) return person
        }
        return -1
    }

    /**
     * How lit pane [pane] would be across a change from [previous] to [state] that has faded [fade]
     * of the way (0 just changed .. 1 done): lit in both 1, in neither 0, and between them the fade --
     * a window that goes dark as another lights, never a blink. When each pane's fade starts is
     * [WindowWalk.litAmount]'s business: a window somebody walks out of goes dark only once they are
     * out of it.
     */
    fun litAmount(state: Long, previous: Long, fade: Float, pane: Int): Float {
        val now = litMask(state) and (1 shl pane) != 0
        val before = litMask(previous) and (1 shl pane) != 0
        return when {
            now && before -> 1f
            now -> fade
            before -> 1f - fade
            else -> 0f
        }
    }

    /** [t] seconds into a fade of [FADE_SECONDS], 0 .. 1, eased: the curve every light here follows. */
    fun eased(t: Double): Float {
        val x = (t / FADE_SECONDS).toFloat().coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    /**
     * How many of [people] are at the windows of a building [openness] open (1 for a house): all of
     * them open, none shut, and across a closing one fewer at each step of the first half of the fade
     * -- `round(people x (2 x openness - 1))` -- so the last of them sets off while the glass is still
     * at least half lit (and is out of sight before it is, but for the shortest span: see [WindowWalk],
     * "The lights"), and nobody is ever seen behind glass gone dark for the night (v5.12; the
     * maintainer's *«va fatto anche per la chiusura, non solo per finestre accese/spente»*,
     * 2026-10-09). At an opening the same steps run the other way, the first of them walking in once
     * the glass is half lit. Until v5.12 they left across the whole fade (`round(people x openness)`),
     * the last one behind glass a quarter lit in a building of two, a sixth lit in one of three.
     */
    fun presentCount(people: Int, openness: Float): Int {
        if (openness >= 1f) return people
        val share = (2f * openness - 1f).coerceIn(0f, 1f)
        return Math.round(people * share)
    }
}
