package com.paperscrape.livewallpaper.engine

/**
 * The smoke over a house's chimney, as numbers: where each puff is, how large and how strong, at a
 * moment of the scene's clock. Drawn by `SceneObjectRenderer.drawChimneySmoke`; pure, so the JVM suite
 * holds the rules.
 *
 * **A column that rises slowly and fades** (v5.12, version B of the proposals photographed in round A,
 * the maintainer's *«il resto si a tutte»* of 2026-10-09; his own words on the smoke of before: *«deve
 * essere spostato sopra il camino perché ora parte da dentro il camino, non dalla fine come dovrebbe
 * essere»*). [PUFFS] puffs share one life of [PERIOD_SECONDS], each a quarter of it behind the one
 * before: a puff is born on the chimney's cap, rises [SMOKE_RISE_UNITS] while the wind bends it
 * [SMOKE_DRIFT_UNITS] to the right -- slowly at first, more as it climbs -- grows from [RADIUS_BORN] to
 * [RADIUS_GONE], appears over the first [FADE_IN] of its life and fades over the rest of it, to nothing
 * at its end. So no puff pops in or out: it is transparent where it is born and where it dies, and the next one takes
 * its place at the cap. Each chimney runs its own beat ([beatOf], from the house's own seed), so a
 * street does not puff in step. About 3 units a second, 2.5-3.5 px on the BV6600: as slow as the
 * scene's motion is meant to be (`DESIGN_NOTES.md` §1, "Nothing moves quickly").
 *
 * All units are the house piece's own, the frame the chimney's point is declared in
 * (`BuildingPiece.smokeX/smokeY`, the middle of the cap).
 */
internal object ChimneySmoke {

    /** How many puffs are in the air over one chimney at once. */
    const val PUFFS = 4

    /** One puff's life, in seconds of the scene's clock. */
    const val PERIOD_SECONDS = 8f

    /** How far a puff rises over its life. */
    const val SMOKE_RISE_UNITS = 26f

    /** How far the wind bends it to the right over its life. */
    const val SMOKE_DRIFT_UNITS = 10f

    /** A puff's radius when it is born on the cap, and when it is gone. */
    const val RADIUS_BORN = 2.6f
    const val RADIUS_GONE = 6.5f

    /** The scale of a puff's alpha (of 255): [alpha] reaches about 167 of it as the puff finishes appearing. */
    const val ALPHA = 190f

    /** The share of a life a puff takes to appear. */
    const val FADE_IN = 0.12f

    /**
     * How high above the cap's middle a newborn puff's centre stands, as a share of its radius: its
     * lower edge just inside the cap, so the smoke comes out of the top of the chimney and never
     * covers its side, which is what it did until v5.12.
     */
    const val BORN_LIFT = 0.8f

    /** The scale of a chimney's beat over its house's seed: any value that spreads 0..2π past a few cycles. */
    private const val BEAT_SPREAD = 7.13f

    /** The phase a chimney's puffs run at, from its house's `idleSeed` (0..2π). */
    fun beatOf(idleSeed: Float): Float = idleSeed * BEAT_SPREAD

    /** Where puff [puff] of a chimney on [beat] is in its life at [elapsed], 0 (born) .. 1 (gone). */
    fun life(elapsed: SceneTime, beat: Float, puff: Int): Float =
        elapsed.cycle(1f / PERIOD_SECONDS, beat + puff.toFloat() / PUFFS)

    /** How far right of the cap's middle a puff at [life] is: the wind takes it more as it climbs. */
    fun dx(life: Float): Float = SMOKE_DRIFT_UNITS * life * (0.35f + 0.65f * life)

    /** How far above the cap's middle a puff at [life] is (negative is up). */
    fun dy(life: Float): Float = -RADIUS_BORN * BORN_LIFT - SMOKE_RISE_UNITS * life

    /**
     * Whether the chimneys smoke at all: **only where the autumn or the winter palette is on** (v5.12,
     * the maintainer's *«il resto si a tutte»* of 2026-10-09 to the row that said so) -- the palette the
     * user picks in *Seasons & decorations*, and the one the project already reads as "it is cold"
     * (the meadow's dry stems, the snow on the roofs, the coats: `DESIGN_NOTES.md` decision 35). So
     * Autumn, Winter, Christmas, New Year's Eve and Tundra smoke as they ship, and any theme where the user
     * turns either palette on; Beach, Desert, Spring, Easter, Sunset, Big City and Halloween do not.
     * No switch of its own: the palette is the switch.
     */
    fun smokes(c: SceneCustomization): Boolean = c.fallColorsEnabled || c.winterColorsEnabled

    /** A puff's radius at [life]. */
    fun radius(life: Float): Float = RADIUS_BORN + (RADIUS_GONE - RADIUS_BORN) * life

    /** A puff's alpha at [life], 0..[ALPHA]: none when born, none when gone. */
    fun alpha(life: Float): Float = ALPHA * (life / FADE_IN).coerceAtMost(1f) * (1f - life)

    /** The smoke's colour by day: the light grey every chimney has always had, kept (version A of the proposals). */
    const val DAY_COLOUR = 0xFFE4E4DC.toInt()

    /**
     * The smoke's colour at night: a middle grey that reads against the dark hills without shining.
     * Until v5.12 the smoke kept its daytime grey at midnight and stood out against the night as if it
     * were lit (CIE76 ΔE up to 46 against Beach's hills, measured in round A); the darker grey photographed
     * beside this one disappeared into them (3.7-6.0).
     */
    const val NIGHT_COLOUR = 0xFF9AA0AA.toInt()

    /** The smoke's colour at [dayBlend] (1 noon, 0 night): the two on the scene's own ramp, as every scene colour is. */
    fun colour(dayBlend: Float): Int = SceneColour.blendArgb(NIGHT_COLOUR, DAY_COLOUR, dayBlend.coerceIn(0f, 1f))
}
