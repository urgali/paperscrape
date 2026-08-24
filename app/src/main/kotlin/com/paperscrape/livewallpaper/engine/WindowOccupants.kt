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
        buildingSeed * 31 + windowIndex * 7 + 13

    /**
     * Whether this window has someone at it.
     *
     * Never consults the clock, so an occupant does not flicker in and out between frames the way
     * a lit-window flicker legitimately can.
     */
    fun isOccupied(
        seed: Int,
        buildingSeed: Int,
        windowIndex: Int,
        kind: WindowBuildingKind,
    ): Boolean = CandidateNoise.value(seed, address(buildingSeed, windowIndex), CH_PRESENT) < rateFor(kind)

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
