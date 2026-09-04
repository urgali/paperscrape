package com.paperscrape.livewallpaper.engine

/**
 * Which kinds of building can have someone looking out of a window.
 *
 * A building type is listed here only because its windows are already drawn at a size a bust reads
 * at. Nothing in this enum changes how a window looks -- see [WindowOccupants] for the boundary
 * this system deliberately does not cross.
 */
internal enum class WindowBuildingKind {
    /** The small and large houses, the only two v4.0 ever populated. */
    HOUSE,

    /** Shopfronts and the bar -- street-level glass, so an occupant reads as staff or a customer. */
    COMMERCIAL,

    /** Tower blocks, whose window grid v4.1 opens up for the first time. */
    SKYSCRAPER,
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
 * ### The boundary this does not cross
 *
 * This object decides **who stands at a window**. It has no opinion about the window: not its
 * sprite, its size, its position, its colour, its lit/dark state, nor whether it is drawn at all.
 * Those remain entirely the building drawing code's business, untouched by v4.1.
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
     * Commercial frontage is busier than a home during the day but has far fewer panes, so a
     * slightly higher rate still yields only one or two figures per building.
     */
    const val COMMERCIAL_RATE = 0.40f

    /**
     * Tower blocks have a large window grid, so the per-window rate has to be *low* or the
     * building reads as a doll's house with a face in every pane.
     */
    const val SKYSCRAPER_RATE = 0.12f

    /** The share of windows of a given building kind that get an occupant. */
    fun rateFor(kind: WindowBuildingKind): Float = when (kind) {
        WindowBuildingKind.HOUSE -> HOUSE_RATE
        WindowBuildingKind.COMMERCIAL -> COMMERCIAL_RATE
        WindowBuildingKind.SKYSCRAPER -> SKYSCRAPER_RATE
    }

    /**
     * A stable address for one window of one building.
     *
     * Mixes the building's own position with the window's index so that two buildings of the same
     * type at different places on the street are populated differently -- which is what v4.0's
     * constant `winX` could not express.
     */
    private fun address(buildingSeed: Int, windowIndex: Int): Int =
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
     * Whether this window has someone at it.
     *
     * The [windowCount] windows are ranked on [CH_PRESENT] and the first [occupantCount] of them
     * are occupied -- a seeded permutation of a fixed count rather than a coin per pane. Which
     * window a given seed picks is as free as it was; how many it picks is no longer left to a
     * handful of independent draws.
     *
     * Never consults the clock, so an occupant does not flicker in and out between frames the way
     * a lit-window flicker legitimately can.
     */
    fun isOccupied(
        seed: Int,
        buildingSeed: Int,
        windowIndex: Int,
        windowCount: Int,
        kind: WindowBuildingKind,
        /**
         * How open the building is, 0..1 -- [BusinessHours] for commercial kinds, constantly 1
         * for houses. Applied to the dealt count, not per window: at openness x a building shows
         * `round(count · x)` of its occupants, so across a closing fade they leave one at a time
         * in reverse deal order, and at 1 the expression is bitwise the pre-v4.22 one.
         */
        openness: Float = 1f,
    ): Boolean {
        if (windowIndex < 0 || windowIndex >= windowCount) return false
        val dealt = occupantCount(seed, buildingSeed, windowCount, kind)
        val occupied =
            if (openness >= 1f) dealt
            else Math.round(dealt * openness.coerceIn(0f, 1f))
        if (occupied <= 0) return false
        if (occupied >= windowCount) return true
        return SeededBalance.rankOf(
            seed,
            CH_PRESENT,
            windowIndex,
            windowCount,
            addressStride = WINDOW_ADDRESS_STRIDE,
            addressOffset = buildingSeed * BUILDING_ADDRESS_STRIDE + ADDRESS_BIAS,
        ) < occupied
    }

    /**
     * Who is at this window.
     *
     * Independent of [kind] and of [windowIndex] beyond addressing: a skyscraper is no more likely
     * to hold an adult than a house is, and the top floor is no more likely to hold a woman than
     * the ground floor. Independent of the pavement traffic outside entirely -- the street's
     * direction is not an input here and could not be.
     */
    fun occupantAt(seed: Int, buildingSeed: Int, windowIndex: Int): WindowOccupant {
        val addr = address(buildingSeed, windowIndex)
        return WindowOccupant(
            age = if (CandidateNoise.value(seed, addr, CH_AGE) < 0.5f) PersonAge.ADULT else PersonAge.CHILD,
            sex = if (CandidateNoise.value(seed, addr, CH_SEX) < 0.5f) PersonSex.MALE else PersonSex.FEMALE,
            skinIndex = (CandidateNoise.value(seed, addr, CH_SKIN) * PedestrianPopulation.SKIN_TONE_COUNT)
                .toInt().coerceIn(0, PedestrianPopulation.SKIN_TONE_COUNT - 1),
        )
    }
}
