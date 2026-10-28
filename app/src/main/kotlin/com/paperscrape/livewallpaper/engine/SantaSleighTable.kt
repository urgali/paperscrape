package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R

/**
 * Santa's sleigh, in units of the group (the flight line at y 0, the reindeer to the left; the drawer puts the
 * group's middle, halfway between [LEFT] and [RIGHT], on the flight point): where each piece blits, where the cords and the gifts start, and how large the group is drawn. **Generated**
 * by `tools/assets/sleigh/build_sleigh.py` from the geometry it draws the SVGs with; edit that script, not this file.
 */
internal object SantaSleighTable {
    /** The sleigh with Santa and the presents, and where it blits. */
    val SLEIGH_RES = R.drawable.santa_sleigh
    const val SLEIGH_X = -11.00f
    const val SLEIGH_Y = -50.00f

    /** The reindeer's four poses, the same series for both; where each blits from the reindeer's middle. */
    val DEER_RES = intArrayOf(R.drawable.santa_reindeer_0, R.drawable.santa_reindeer_1, R.drawable.santa_reindeer_2, R.drawable.santa_reindeer_3)
    val DEER_X = floatArrayOf(-28.00f, -27.00f, -26.00f, -27.00f)
    val DEER_Y = floatArrayOf(-28.00f, -30.00f, -31.00f, -29.00f)

    /** The two reindeer's middles: Rudolph leading at the left, the second behind him. */
    const val LEAD_X = -74.00f
    const val LEAD_Y = 4.00f
    const val NEAR_X = -32.00f
    const val NEAR_Y = 4.00f

    /** In each pose, from the reindeer's middle: the collar (where the trace from the one in front ends), the collar's front (where the rein from Santa's hand ends), the girth (where its trace to the one behind, or to the sleigh, starts), the nose. */
    val COLLAR_X = floatArrayOf(-9.64f, -9.52f, -9.45f, -9.54f)
    val COLLAR_Y = floatArrayOf(-2.07f, -3.58f, -4.62f, -3.09f)
    val REIN_X = floatArrayOf(-11.64f, -11.52f, -11.44f, -11.54f)
    val REIN_Y = floatArrayOf(-2.01f, -3.61f, -4.71f, -3.11f)
    val GIRTH_X = floatArrayOf(10.84f, 10.89f, 10.91f, 10.89f)
    val GIRTH_Y = floatArrayOf(-1.24f, -1.67f, -2.18f, -1.37f)
    val NOSE_X = floatArrayOf(-24.69f, -23.83f, -23.11f, -23.79f)
    val NOSE_Y = floatArrayOf(-8.54f, -11.65f, -13.87f, -11.32f)

    /** Rudolph's nose: the radius of the red disc drawn over the reindeer's own, and its colour, by day and by night. */
    const val RUDOLPH_NOSE_RADIUS = 2.90f
    const val RUDOLPH_NOSE_COLOUR = 0xFFE33A2E.toInt()

    /** Where the reins leave Santa's fist, and the front of the sleigh where the second reindeer's trace ends. */
    const val HAND_X = 5.00f
    const val HAND_Y = -16.60f
    const val FRONT_X = -2.00f
    const val FRONT_Y = 2.00f

    /** Where a gift leaves the pile behind Santa. */
    const val SACK_X = 48.00f
    const val SACK_Y = -20.00f

    /** The group's extent across, its middle drawn on the flight point. */
    const val LEFT = -102.00f
    const val RIGHT = 70.00f

    /** What is seen of Santa: his hat's top to the sleigh's rim. */
    const val SANTA_TOP = -49.40f
    const val SANTA_BOTTOM = -10.20f

    /**
     * How tall Santa is drawn against an adult walking on the near row of the pavement
     * (`DESIGN_NOTES.md` §12, decision 73).
     */
    const val SANTA_TO_PEDESTRIAN = 1.30f

    /** The narrowest band of the drawing, in units: 2 px on the BV6600 at that size, and a quarter more. */
    const val SANTA_MIN_BAND_UNITS = 2.00f

    /** The cords, in units: the reins and the traces, never narrower than [SANTA_MIN_BAND_UNITS]. */
    const val SANTA_REIN_UNITS = 2.00f
    const val SANTA_TRACE_UNITS = 2.00f
    const val REIN_COLOUR = 0xFF3A2416.toInt()
    const val TRACE_COLOUR = 0xFF962820.toInt()
}
