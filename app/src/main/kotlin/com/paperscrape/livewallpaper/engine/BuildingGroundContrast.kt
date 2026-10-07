package com.paperscrape.livewallpaper.engine

/**
 * How far a building's wall stands from the ground behind it, and how far it has to (v5.11).
 *
 * The maintainer chose theme colours for the towers and a colour of their own for the shops on
 * 2026-10-06, with one condition: *«che i colori non siano troppo uguali al terreno altrimenti non si
 * capisce niente»*. This is that condition as a number, so it is checked rather than looked at.
 *
 * ### The measure
 *
 * **CIE76 dE**, in the scene's own Lab ([DayNightColor.toLab]), between a wall colour and each colour
 * the wall stands against: the hills -- which are also the ground in front, the scene having one
 * ground paper -- and, for a tower, which rises past the hill's crest, every mountain layer the
 * theme shows. dE because a wall is a large flat area: the project's decision 33 keeps luma for
 * hairlines, where the eye has lightness and nothing else, and dE for anything wider than a few
 * pixels, where a hue difference at the same lightness still reads (peach on green does).
 *
 * ### The gates, and the cases they come from
 *
 * - **By day, 20.** Big City as it was until v5.11 shows the two ends: its second slate, `#5C6A78` on
 *   hills of `#5B6270`, is 3.8 and vanished; its first, `#454B57`, is 9.7 and read only through its
 *   window grid (inventory I-410, photographed in v5.11A). The slate is still
 *   [SceneCustomization.DEFAULT]'s, the start of a theme that is not built in. Among the colours the maintainer chose from the
 *   photographs, Autumn's first brick `#B9714A` on the orange hill (12.9) melted into it, and Big
 *   City's steel `#8A96AA` (20.6) stood clear. The gate is that last one: the weakest pair the
 *   photographs showed reading by itself.
 * - **By night, 10.** At night every tower's and shop's windows are lit, and the lit panes draw the
 *   building's outline whatever the wall does (photographed at midnight in v5.11A): what the wall
 *   still has to do is stay a paper of its own. Big City's first slate at night, `#262A31` on
 *   `#1B1D26`, is 6.3 and its body was gone between the lit rows; its second, `#303842`, is 12.5 and
 *   its edge was there. The gate sits between them.
 *
 * Measured at the two ends of the day blend, where the pairs are; between them both sides of the
 * comparison move together along their own pairs.
 */
internal object BuildingGroundContrast {

    const val DAY_GATE = 20f
    const val NIGHT_GATE = 10f

    /** CIE76 colour difference between two opaque colours. */
    fun deltaE(a: Int, b: Int): Float {
        val (l1, a1, b1) = DayNightColor.toLab(a)
        val (l2, a2, b2) = DayNightColor.toLab(b)
        return kotlin.math.sqrt((l1 - l2) * (l1 - l2) + (a1 - a2) * (a1 - a2) + (b1 - b2) * (b1 - b2))
    }

    /**
     * The ground a building of [variant] stands against in [c], at one end of the day: the hills,
     * and for a tower every mountain layer that is shown. A shop stands below the hill's solid line
     * with its roof below the crest, so only the hills are behind it.
     */
    fun groundBehind(c: SceneCustomization, variant: SceneSpace.SceneVariant, day: Boolean): IntArray {
        val hills = if (day) c.hillsColorDay else c.hillsColorNight
        if (variant != SceneSpace.SceneVariant.TOWER) return intArrayOf(hills)
        val out = ArrayList<Int>(3)
        out += hills
        if (c.mountainsFront.visible) out += if (day) c.mountainsFront.colorDay else c.mountainsFront.colorNight
        if (c.mountainsBack.visible) out += if (day) c.mountainsBack.colorDay else c.mountainsBack.colorNight
        return out.toIntArray()
    }

    /**
     * The smallest dE between either of the two colours [variant] wears in [c] and the ground behind
     * it, at one end of the day -- the number the gate is compared with.
     */
    fun worstDeltaE(c: SceneCustomization, variant: SceneSpace.SceneVariant, day: Boolean): Float {
        val pair = c.buildingColoursFor(variant) ?: return Float.MAX_VALUE
        return worstDeltaE(c, variant, day, intArrayOf(pair.colorDay1, pair.colorNight1, pair.colorDay2, pair.colorNight2))
    }

    /** The same for a [pair] given as day 1, night 1, day 2, night 2, worn or not. */
    fun worstDeltaE(c: SceneCustomization, variant: SceneSpace.SceneVariant, day: Boolean, pair: IntArray): Float {
        val walls = if (day) intArrayOf(pair[0], pair[2]) else intArrayOf(pair[1], pair[3])
        var worst = Float.MAX_VALUE
        for (wall in walls) for (ground in groundBehind(c, variant, day)) worst = minOf(worst, deltaE(wall, ground))
        return worst
    }

    /**
     * How far [pair] (day 1, night 1, day 2, night 2) stands from [c]'s ground for [variant]: the
     * smaller of its day dE over [DAY_GATE] and its night dE over [NIGHT_GATE]. 1 or more clears both;
     * how a theme that is not built in chooses its starting colours (`withStartingBuildingColours`).
     */
    fun margin(c: SceneCustomization, variant: SceneSpace.SceneVariant, pair: IntArray): Float =
        minOf(worstDeltaE(c, variant, true, pair) / DAY_GATE, worstDeltaE(c, variant, false, pair) / NIGHT_GATE)

    /** Whether [variant]'s two colours in [c] clear both gates. */
    fun passes(c: SceneCustomization, variant: SceneSpace.SceneVariant): Boolean =
        worstDeltaE(c, variant, day = true) >= DAY_GATE && worstDeltaE(c, variant, day = false) >= NIGHT_GATE
}
