package com.paperscrape.livewallpaper.engine

/**
 * Which kinds of building can have someone looking out of a window.
 *
 * A building type is listed here only because its windows are already drawn at a size a bust reads
 * at. [WindowOccupants] never changes how a window looks -- see it for the boundary this system
 * deliberately does not cross -- but the renderer does read the kind:
 * `SceneObjectRenderer.drawNeighbourhoodBuilding` lights a non-house's glass only while it is open
 * (see [BusinessHours]), and gives chimney smoke only to houses and a beacon only to towers.
 */
internal enum class WindowBuildingKind {
    /** The small and large houses, the only two v4.0 ever populated. */
    HOUSE,

    /** Shopfronts and the bar -- street-level glass, so an occupant reads as staff or a customer. */
    COMMERCIAL,

    /** Tower blocks: since v5.11 their people stand at the six windows of the entrance storey. */
    SKYSCRAPER,

    /**
     * The school, and the only kind whose windows say anything about *who* is behind them.
     *
     * It is street-level glass like the restaurant and the bar, so its hours are [COMMERCIAL]'s:
     * it keeps business hours, its panes go dark when it closes, and its occupants walk out one at a
     * time over the first half of the closing fade. A value of its own is about the two rules that read the kind
     * -- [WindowOccupants.occupantAt] makes its occupants children and [WindowOccupants.rateFor]
     * gives it [WindowOccupants.SCHOOL_RATE] -- and about keeping the restaurant and the bar out
     * of both. A flag on [BuildingFamily] would have been a second axis for a question this one
     * already answers.
     */
    SCHOOL,
}

/**
 * One person seen at a window.
 *
 * Deliberately the same age/sex/skin vocabulary the street uses, so "the people at the windows are
 * always the same" and "the people on the pavement are always the same" are one question with one
 * answer rather than two systems drifting apart.
 */
internal data class WindowOccupant(
    val age: PersonAge,
    val sex: PersonSex,
    val skinIndex: Int,
) {
    /** Index into the four shipped bust sprites, `[man, woman, boy, girl]`. */
    val kindIndex: Int
        get() = when {
            age == PersonAge.ADULT && sex == PersonSex.MALE -> 0
            age == PersonAge.ADULT -> 1
            sex == PersonSex.MALE -> 2
            else -> 3
        }
}

/**
 * Decides who, if anyone, is at a given window.
 *
 * ### The v4.0 defect this replaces
 *
 * ```
 * val seed = abs((r.spec.tileFractionX * 9973f + winX).toInt())
 * if (seed % 3 != 0) return
 * val kindIdx = seed % personKinds.size
 * ```
 *
 * Two problems, both of a kind [CandidateNoise] already exists to prevent:
 *
 *  1. **One seed, two questions.** Presence (`% 3`) and identity (`% 4`) were read from the same
 *     number with no channel separation, so who appears was a function of whether anyone appears.
 *     Only seeds divisible by three survive the gate, and `3k mod 4` cycles `0,3,2,1` -- the
 *     variety that remained was an artefact of two coprime moduli rather than anything designed.
 *  2. **Almost no entropy.** `winX` is a compile-time constant per building type, so the only
 *     varying input was one truncated float, and the truncation threw away most of what it had.
 *
 * Presence and identity now come from separate channels of the shared noise, addressed by building
 * and window, so adding an attribute cannot disturb the ones already chosen.
 *
 * ### The boundary this does not cross, and the one place it does
 *
 * This object decides **who stands at a window**. It has no opinion about the window: not its
 * sprite, its size, its position, its colour, nor whether it is drawn at all. Those remain entirely
 * the building drawing code's business, untouched by v4.1. **Since v5.12 a house's window with
 * somebody at it is lit at night** -- a house lights half its windows, and theirs among them
 * ([WindowRoster], which places every building's people from this object's count and rank, moves a
 * house's, a shop's and the school's with the clock, and walks them from window to window and out and
 * in at the hours, [WindowWalk]); so who stands at a window now decides that it is lit, and nothing
 * here decides the rest of a window's look.
 *
 * Since v5.6 the building [WindowBuildingKind] reaches [occupantAt] for **one** purpose: a school
 * shows children. [WindowBuildingKind.SCHOOL] forces the age to [PersonAge.CHILD] and nothing
 * else -- sex and skin still come from their own channels at the same address, so the child at a
 * school window is the same child that address would have produced anywhere. For every other kind
 * the answer is a function of the address alone, as it has been since v4.1: a skyscraper is no
 * more likely to hold an adult than a house is, and a restaurant and a bar are exactly what they
 * were. `WindowOccupantsTest` pins both halves -- that the three old kinds still agree with each
 * other and with the pre-v5.6 signature, and that no adult ever appears at a school.
 */
internal object WindowOccupants {

    // Separate channels for the two independent questions, plus one per attribute.
    private const val CH_PRESENT = 41
    private const val CH_AGE = 42
    private const val CH_SEX = 43
    private const val CH_SKIN = 44

    /** v4.2: how many of a building's windows are occupied, as opposed to which. */
    private const val CH_PRESENT_COUNT = 45

    /**
     * Roughly how many of a house's windows have someone at them.
     *
     * v4.0's `seed % 3 != 0` gate worked out at about a third, and a house that is too populated
     * stops reading as a home, so the rate is kept where it was.
     */
    const val HOUSE_RATE = 0.34f

    /**
     * Commercial frontage is busier than a home during the day and deals its people over three
     * windows (a bar's fourth and fifth, added in v5.12, bring nobody: `BuildingPiece.people`), about as
     * many as a house, so a slightly higher rate still yields only one or two figures per building.
     */
    const val COMMERCIAL_RATE = 0.40f

    /**
     * A tower's people are at its entrance storey, behind street-level glass like a shop's, so they
     * come at a shop's rate (v5.11, inventory I-406). Until v5.11 a tower's three bust windows stood
     * among its grid of small ones, at 0.12, "or the building reads as a doll's house with a face in
     * every pane" -- and at 0.12 three panes dealt nobody most of the time. Since v5.11 the six panes
     * are the hall's -- four over the canopy, one each side of the door -- and the grid above holds
     * no bust at all, so the rate is the one street-level glass has: two or three people a tower,
     * who walk out and in at the opening hours as a shop's do ([WindowWalk]).
     */
    const val SKYSCRAPER_RATE = COMMERCIAL_RATE

    /**
     * A school's people are dealt over **four** windows -- its four ground-floor panes, the count it
     * kept when v5.12 gave it four more upstairs (`BuildingPiece.people`) -- and at the commercial rate
     * four is an empty school.
     *
     * The rate and the pane count multiply, and 0.40 was set for a three-pane frontage where one
     * or two figures is a shop with somebody in it. Measured on the shipped themes before the
     * school existed -- the twelve theme seeds against the positions a school can stand in, 24
     * samples -- four panes at 0.40 deal **one** occupant in 13 of the 24 and never more than
     * two. One child at a window of a building whose whole subject is children reads as a school
     * that is shut.
     *
     * 0.60 deals two or three of the four ([SeededBalance.drawCount] rounds `windowCount * rate`,
     * so 2.4 becomes 2 or 3 by the seed), who stand at any of its eight windows and move between
     * them ([WindowRoster]). It is still a *rate* and not a guarantee: nothing here forces a count,
     * and the business hours thin it the same way they thin a shop's, so an evening school empties
     * pane by pane exactly as the bar does.
     */
    const val SCHOOL_RATE = 0.60f

    /** The share of windows of a given building kind that get an occupant. */
    fun rateFor(kind: WindowBuildingKind): Float = when (kind) {
        WindowBuildingKind.HOUSE -> HOUSE_RATE
        WindowBuildingKind.COMMERCIAL -> COMMERCIAL_RATE
        WindowBuildingKind.SKYSCRAPER -> SKYSCRAPER_RATE
        WindowBuildingKind.SCHOOL -> SCHOOL_RATE
    }

    /**
     * A stable address for one window of one building.
     *
     * Mixes the building's own position with the window's index so that two buildings of the same
     * type at different places on the street are populated differently -- which is what v4.0's
     * constant `winX` could not express.
     *
     * Visible to the renderer since v4.30, which deals the occupant's four colours from it. It is
     * the same address the occupant itself is dealt from, which is the point: one figure, one
     * address, every attribute keyed off it.
     */
    fun address(buildingSeed: Int, windowIndex: Int): Int =
        buildingSeed * BUILDING_ADDRESS_STRIDE + windowIndex * WINDOW_ADDRESS_STRIDE + ADDRESS_BIAS

    // The three parts of [address], named so that [isOccupied] can hand the same arithmetic to
    // [SeededBalance.rankOf] instead of restating it. The values are v4.1's, unchanged: a window's
    // address is what it always was, and only the decision made with it has moved.
    private const val BUILDING_ADDRESS_STRIDE = 31
    private const val WINDOW_ADDRESS_STRIDE = 7
    private const val ADDRESS_BIAS = 13

    /**
     * How many of a building's [windowCount] windows are occupied.
     *
     * Separate from *which* ones, and that separation is the v4.2 fix. v4.1 rolled one coin per
     * window at [rateFor], which is unbiased on average and empty far too often in the small: a
     * three-pane frontage came out with nobody 21.6% of the time, a two-window cottage 43.6% of
     * the time, and since there is roughly one bar per theme that tail is most of the reason no
     * one was ever seen behind commercial glass. Drawing the count first and then dealing it out
     * keeps the declared rate exactly -- `windowCount * rate` on average, which is what
     * `WindowOccupantsTest` pins -- while removing the empty tail: three panes at 40% now hold one
     * or two people and never none.
     */
    fun occupantCount(seed: Int, buildingSeed: Int, windowCount: Int, kind: WindowBuildingKind): Int =
        SeededBalance.drawCount(seed, CH_PRESENT_COUNT, buildingSeed, windowCount, rateFor(kind))

    /**
     * Whether this window has someone at it, while nobody has moved and nobody is walking.
     *
     * The [windowCount] windows are ranked on [CH_PRESENT] and the first [occupantCount] of them
     * are occupied -- a seeded permutation of a fixed count rather than a coin per pane. Which
     * window a given seed picks is as free as it was; how many it picks is no longer left to a
     * handful of independent draws.
     *
     * Never consults the clock: this is the rule the scene's people start from. Since v5.12 the scene
     * reads it through [WindowRoster]'s first state -- the same count and the same ranks, over all the
     * windows a building has -- which then moves them one at a time, minutes apart, a tower's not at
     * all, and walks them out and in at the hours ([WindowWalk]); `WindowRosterTest` (houses, shops, the
     * school) and `WindowWalkTest` (the towers) hold the two to the same windows.
     */
    fun isOccupied(
        seed: Int,
        buildingSeed: Int,
        windowIndex: Int,
        windowCount: Int,
        kind: WindowBuildingKind,
        /**
         * How open the building is, 0..1 -- [BusinessHours] for commercial kinds, constantly 1
         * for houses. Applied to the dealt count, not per window: at openness x a building has
         * [WindowRoster.presentCount] of its occupants in -- one fewer at each step of the first half of
         * a closing fade, so the last is out while its glass is still half lit (v5.12; until then
         * `round(count · x)`, across the whole fade) -- leaving in reverse deal order; at 1 the
         * expression is bitwise the pre-v4.22 one.
         */
        openness: Float = 1f,
        /**
         * What the count is dealt over: the building's windows as they were when its number of
         * people was set (`NeighbourhoodComposer.Deal.peopleWindows`, v5.12), while the windows they
         * stand at are ranked over all [windowCount] of them. Equal to [windowCount] where no window
         * was added.
         */
        peopleWindows: Int = windowCount,
    ): Boolean {
        if (windowIndex < 0 || windowIndex >= windowCount) return false
        val dealt = occupantCount(seed, buildingSeed, peopleWindows, kind)
        val occupied = WindowRoster.presentCount(dealt, openness)
        if (occupied <= 0) return false
        if (occupied >= windowCount) return true
        return rankOf(seed, buildingSeed, windowIndex, windowCount) < occupied
    }

    /**
     * Where window [windowIndex] falls in its building's seeded order of [windowCount] windows, 0 first:
     * the first [occupantCount] are the ones somebody stands at, and across a closing fade the last of
     * them leave first. Read by [isOccupied] and, since v5.12, by [WindowRoster], whose first state
     * places the people by it -- the same seed, channel and address, so the same windows.
     */
    fun rankOf(seed: Int, buildingSeed: Int, windowIndex: Int, windowCount: Int): Int =
        SeededBalance.rankOf(
            seed,
            CH_PRESENT,
            windowIndex,
            windowCount,
            addressStride = WINDOW_ADDRESS_STRIDE,
            addressOffset = buildingSeed * BUILDING_ADDRESS_STRIDE + ADDRESS_BIAS,
        )

    /**
     * Who is at this window.
     *
     * Independent of [windowIndex] beyond addressing -- the top floor is no more likely to hold a
     * woman than the ground floor -- and independent of the pavement traffic outside entirely:
     * the street's direction is not an input here and could not be.
     *
     * **[kind] enters in exactly one place and changes exactly one field.** A
     * [WindowBuildingKind.SCHOOL] window holds a child; every other kind gets the age its address
     * gives, which is what the three of them have always got and what keeps a tower, a house, a
     * restaurant and a bar bitwise identical to their pre-v5.6 selves. Sex and skin are read from
     * their own channels whatever the kind, so turning a building into a school swaps the age and
     * leaves the rest of the person standing there -- the same face, one age younger.
     *
     * The three-argument overload below is the pre-v5.6 signature, kept for the tests -- nothing in
     * the scene calls it -- because the test that pins "the kinds agree" needs a reading with no
     * kind in it at all.
     */
    fun occupantAt(seed: Int, buildingSeed: Int, windowIndex: Int, kind: WindowBuildingKind): WindowOccupant {
        val addr = address(buildingSeed, windowIndex)
        val dealtAge =
            if (CandidateNoise.value(seed, addr, CH_AGE) < 0.5f) PersonAge.ADULT else PersonAge.CHILD
        return WindowOccupant(
            age = if (kind == WindowBuildingKind.SCHOOL) PersonAge.CHILD else dealtAge,
            sex = if (CandidateNoise.value(seed, addr, CH_SEX) < 0.5f) PersonSex.MALE else PersonSex.FEMALE,
            skinIndex = (CandidateNoise.value(seed, addr, CH_SKIN) * PedestrianPopulation.SKIN_TONE_COUNT)
                .toInt().coerceIn(0, PedestrianPopulation.SKIN_TONE_COUNT - 1),
        )
    }

    /** The occupant of a window of a building whose kind says nothing about who stands at it. */
    fun occupantAt(seed: Int, buildingSeed: Int, windowIndex: Int): WindowOccupant =
        occupantAt(seed, buildingSeed, windowIndex, WindowBuildingKind.HOUSE)

    /**
     * [occupantAt]`(...).kindIndex`, without the object: the draw path reads a window's occupant
     * every frame for every lit pane, and a [WindowOccupant] each time was one allocation per window
     * per frame (v5.10A). The same three reads of the same channels at the same address, so the same
     * answer; `WindowOccupantsTest` holds the two readings together.
     */
    fun occupantKindIndexAt(seed: Int, buildingSeed: Int, windowIndex: Int, kind: WindowBuildingKind): Int {
        val addr = address(buildingSeed, windowIndex)
        val adult = kind != WindowBuildingKind.SCHOOL && CandidateNoise.value(seed, addr, CH_AGE) < 0.5f
        val male = CandidateNoise.value(seed, addr, CH_SEX) < 0.5f
        return when {
            adult && male -> 0
            adult -> 1
            male -> 2
            else -> 3
        }
    }

    /** [occupantAt]`(...).skinIndex`, without the object; see [occupantKindIndexAt]. Not a function of the kind. */
    fun occupantSkinIndexAt(seed: Int, buildingSeed: Int, windowIndex: Int): Int =
        (CandidateNoise.value(seed, address(buildingSeed, windowIndex), CH_SKIN) * PedestrianPopulation.SKIN_TONE_COUNT)
            .toInt().coerceIn(0, PedestrianPopulation.SKIN_TONE_COUNT - 1)
}
