package com.paperscrape.livewallpaper.engine

/**
 * Which walkers have an umbrella up, and when that may change (v4.28).
 *
 * ### The rule, and the trap it exists for
 *
 * "It is raining, so the adults put umbrellas up" cannot simply be read per frame, because the
 * predicate changes the instant the weather does and every pedestrian on the pavement would grow
 * an umbrella between two frames, in front of the viewer. v4.22 met the same problem with the cars
 * and answered it with [CarSelection.offScreen]: **membership changes only while nothing of the
 * thing is on screen.**
 *
 * That answer was never extended to people. [CarSelection.offScreen]'s own doc says so out loud --
 * "a pedestrian materialising mid-pavement is forgiven; a car materialising in the middle of the
 * road is not" -- and it is right about a pedestrian appearing, which happens at the frame edge
 * anyway. It is not right about an object appearing *in the hand of a figure already walking*, and
 * that is what this is.
 *
 * So the same rule is taken over here, with the pedestrians' own geometry supplying "off screen".
 * The scene tiles every `tileWidth` -- twice the screen width -- so a walker has a stretch of
 * roughly one screen width per tile with no copy visible at all, and [nextCarrying] is allowed to
 * move only in it. The cost is the delay: a walker that is on screen when the rain starts finishes
 * its crossing bare-headed, which on the reference device is up to about 75 seconds. That is the
 * price of nothing appearing out of nothing, and it is the right way round.
 *
 * ### Who carries
 *
 * Adults only -- children walk through the rain, which is the reading the phase-2 photographs were
 * approved on -- and of the adults, [SHARE]. That fraction is a **chosen** number rather than a
 * derived one, and it is chosen rather than set to 1.0 because a street where every single adult
 * carries the same object reads as a uniform; two in three reads as weather. It is drawn from the
 * walker's own address on its own noise channel, so it is fixed for a figure across a whole
 * shower and does not reshuffle when the People slider moves.
 */
internal object PedestrianCarry {

    /** Whether this walker is one of the ones that carries, from its own address. */
    const val CH_UMBRELLA = 33

    /** Which canopy colour it carries, from the same address on a channel of its own. */
    const val CH_UMBRELLA_COLOUR = 34

    /** The share of adults who carry one in the rain. See the class doc: chosen, not derived. */
    const val SHARE = 0.67f

    /**
     * Whether this walker would have an umbrella up right now, if it were free to change.
     *
     * [raining] is the renderer's single rain predicate -- exactly what `drawPrecipitation` paints
     * rain on -- so **snow is not rain** and nobody carries anything through it.
     */
    fun wantsUmbrella(raining: Boolean, isAdult: Boolean, noise: Float): Boolean =
        raining && isAdult && noise < SHARE

    /**
     * What a walker carries next frame, given what it carries now.
     *
     * The whole rule in one line: while any copy of it is on screen, nothing changes. [onScreen]
     * comes from the draw pass's own cull rather than from a second copy of the geometry, so the
     * moment this permits a change is exactly a moment nothing was drawn.
     */
    fun nextCarrying(current: Boolean, wanted: Boolean, onScreen: Boolean): Boolean =
        if (onScreen) current else wanted

    /**
     * The canopies, in the scene's own paint.
     *
     * Five colours a paper wallpaper already contains -- the parasol's red, the gift ribbon's
     * yellow, the lake's blue, the outline's near-black and the hedge's green -- so a street of
     * umbrellas cannot drift out of the palette the rest of the frame is drawn in.
     */
    val PALETTE = intArrayOf(
        0xFFE4623E.toInt(),
        0xFFF2C230.toInt(),
        0xFF4E9FB5.toInt(),
        0xFF2B2A33.toInt(),
        0xFF6D8F4F.toInt(),
    )

    /** The canopy colour for a walker, from [noise] in 0..1. */
    fun canopyColour(noise: Float): Int =
        PALETTE[(noise * PALETTE.size).toInt().coerceIn(0, PALETTE.size - 1)]
}
