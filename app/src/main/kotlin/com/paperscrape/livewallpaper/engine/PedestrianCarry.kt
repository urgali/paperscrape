package com.paperscrape.livewallpaper.engine

/**
 * Which walkers have an umbrella up, and when that may change (v4.28, rewritten v5.4).
 *
 * ### The rule
 *
 * **It is raining, so every walker who can hold an umbrella is holding one.** No share, no draw, no
 * deal: [wantsUmbrella] is `raining && canHold`, and that is the whole of it.
 *
 * "Raining" is not decided here and is not decided twice. There is exactly one definition of it in
 * the engine -- `PaperRenderer.updateWeatherPredicates`, evaluated once at the top of the frame,
 * which reads the live forecast when there is one and the theme's own precipitation when there is
 * not -- and it arrives here through `SceneObjectRenderer.rainingNow`. It is the same predicate
 * `drawPrecipitation` paints rain on, which is what makes **snow not rain**: nothing is carried
 * through snow, because the one definition says snow is not rain and everything downstream of it
 * agrees by construction rather than by each draw path remembering to. Light rain, heavy rain and
 * a thunderstorm are all rain; that, too, is settled in that one place.
 *
 * ### Why "everybody", and what it replaces (v5.4, the maintainer's call)
 *
 * Two deliberate decisions were reversed here, by the maintainer, having looked at the street:
 *
 *  - v4.28 put umbrellas in **adult hands only**, and the phase-2 photographs were approved on that
 *    reading;
 *  - a `SHARE` of two adults in three was a **chosen** number, held under 1.0 so that a street
 *    would not read as a uniform. v5.4's first pass dealt that share over the street instead of
 *    rolling it per walker, which fixed a street that had *no* umbrella at all.
 *
 * Both are gone. The instruction is that in the rain every pedestrian who walks carries one, and
 * the uniform is accepted: *"l'ombrello, in caso di pioggia (sia fixed che in live weather) lo
 * devono avere tutti i pedoni che camminano"*. They were decisions and not defects, which is why
 * they are recorded here rather than argued with -- but nothing below still implements them, and
 * this paragraph is the only place they are still described.
 *
 * ### The children, who carry since v5.4H
 *
 * They did not until v5.4H, and **the reason was artwork, not rule**: [wantsUmbrella] would have
 * handed a child an umbrella the moment [canHold] said it could, and what stopped it was that
 * there was nothing to draw. `PeopleLayerTable.CARRY` had two families, so a child's `kindIndex`
 * of 2 or 3 indexed a pose that did not exist; the carry pose is what *raises the arm to the
 * handle*, and without it both of a child's arms hang at its sides; and the engine's single grip
 * point (31.5, 24.5) was **59.5 units above the feet**, higher than a whole child, whose head-top
 * measures 53.7 to 58.0 against an adult's 82.0 to 83.3.
 *
 * All three are gone. The pose is drawn for both child families
 * (`build_carry_sprites.child_walker`, the adults' P1 at a child's proportions), `CARRY` has four
 * families, and `SceneObjectRenderer` hangs the canopy from a grip, a crown and a canopy scale that
 * are **per family** -- the children's grip is 34.9 units up and their canopy is drawn at 70 %, so
 * it is an umbrella their size and not their father's.
 *
 * **Not one line of this file changed for that**, and that is the point of how [canHold] is
 * written: it reads `PeopleLayerTable.CARRY`'s own length, so the day the artwork existed the rule
 * was already true about it. The three tests that pinned "two families, 46 of 94, no child carries
 * anything" went red on the artwork alone, which is what they were for.
 *
 * ### The trap the off-screen gate exists for, which the new rule does not remove
 *
 * "It is raining, so put the umbrellas up" still cannot be read per frame: the predicate changes
 * the instant the weather does, and every pedestrian on the pavement would grow an umbrella between
 * two frames, in front of the viewer. v4.22 met the same problem with the cars and answered it with
 * [CarSelection.offScreen]: **membership changes only while nothing of the thing is on screen.**
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
 * **The new rule makes that stability strictly easier to hold, and it is worth saying why.** Under
 * the share, what a walker *wanted* depended on the rest of the street: it was a rank among the
 * adults present, so the People slider moving, or dusk swapping the day population for the night
 * one, could change a walker's wanted value with the weather perfectly constant. The off-screen
 * gate absorbed that, but it had to. Now `wanted` is a function of the weather and of the walker's
 * own family and nothing else, so the **only** thing that can move it is the weather changing --
 * which is exactly the one case the gate was written for.
 */
internal object PedestrianCarry {

    /** Which canopy colour a walker carries, from its own address on a channel of its own. */
    const val CH_UMBRELLA_COLOUR = 34

    /**
     * Whether this family has a carry pose to put an umbrella in, at all.
     *
     * **The whole of "who is bare-headed in the rain", in one predicate.** It is a fact about the
     * artwork -- `PeopleLayerTable.CARRY` is indexed by `kindIndex` -- and not a rule about who
     * deserves one, so it is written as the artwork's own limit.
     *
     * Reading it off [PeopleLayerTable.CARRY]'s own length rather than from a copy of the number is
     * what made v5.4H's artwork sufficient on its own: the table went from two families to four and
     * this returned true for the children without being touched. **It is true of every walker the
     * shipped set draws today**, and it stays here rather than becoming `true` because the next
     * family drawn is the next family that needs a pose before it needs a rule.
     */
    fun canHold(kindIndex: Int): Boolean = kindIndex in PeopleLayerTable.CARRY.indices

    /**
     * Whether this walker would have an umbrella up right now, if it were free to change.
     *
     * [raining] is the renderer's single rain predicate -- exactly what `drawPrecipitation` paints
     * rain on -- so **snow is not rain** and nobody carries anything through it. [canHold] is the
     * artwork's limit and the only reason a walker in the rain would not be carrying.
     */
    fun wantsUmbrella(raining: Boolean, canHold: Boolean): Boolean = raining && canHold

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
     *
     * **They carry more weight now than they did.** While only two adults in three carried, a
     * street's umbrellas were sparse and their colours were decoration; with every walker holding
     * one, these five are the only thing between the maintainer's rule and a row of identical
     * canopies. See `V5_4E_REPORT.md` for the photograph that was taken to check it.
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
