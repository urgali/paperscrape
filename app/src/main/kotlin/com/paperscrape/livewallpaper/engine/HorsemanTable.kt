package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R

/**
 * The Headless Horseman (v5.16), in units of the group: the horse's body's middle at (0, 0), the group facing left (the
 * drawer mirrors it to fly right). `*_X`/`*_Y` of a piece is where its sprite's top-left blits. **Generated** by
 * `tools/assets/horseman/build_horseman.py` from the geometry it draws the SVGs with; edit that script, not this file.
 */
internal object HorsemanTable {
    /** The reins, from the rider's hand to the bit: their width in units (2.5 px or more on the BV6600) and their colour. */
    const val REIN_UNITS = 2.40f
    const val REIN_COLOUR = 0xFF2A1E18.toInt()

    /** The narrowest designed band, in units: 2 px and a quarter more on the BV6600. The card draws him at its sleigh's scale. */
    const val HORSEMAN_BAND_UNITS = 2.02f

    /** The horse without its legs, and its four legs, in the four poses of the gallop; where each blits. */
    val HORSE_RES = intArrayOf(R.drawable.horseman_horse_0, R.drawable.horseman_horse_1, R.drawable.horseman_horse_2, R.drawable.horseman_horse_3)
    val LEGS_RES = intArrayOf(R.drawable.horseman_legs_0, R.drawable.horseman_legs_1, R.drawable.horseman_legs_2, R.drawable.horseman_legs_3)
    val HORSE_X = floatArrayOf(-35.00f, -34.00f, -33.00f, -34.00f)
    val HORSE_Y = floatArrayOf(-29.00f, -32.00f, -34.00f, -31.00f)
    val LEGS_X = floatArrayOf(-34.00f, -22.00f, -19.00f, -25.00f)
    val LEGS_Y = floatArrayOf(-6.00f, -5.00f, -2.00f, -6.00f)

    /** In each pose: the bit, where the reins reach the horse's head, and the seat, where the rider sits on the saddle. */
    val BIT_X = floatArrayOf(-29.92f, -28.54f, -27.32f, -28.49f)
    val BIT_Y = floatArrayOf(-12.10f, -15.90f, -18.62f, -15.56f)
    val SEAT_X = floatArrayOf(-1.33f, -0.84f, -0.59f, -0.92f)
    val SEAT_Y = floatArrayOf(-9.36f, -10.42f, -11.23f, -10.01f)

    /** The rider with his pumpkin, and where he blits from the seat. */
    val RIDER_RES = R.drawable.horseman_rider
    const val RIDER_X = -15.00f
    const val RIDER_Y = -52.00f

    /** From the seat: the hand holding the reins, the shoulders (the cape's pivot), and the middle of the pumpkin he holds up. */
    const val HAND_X = -13.60f
    const val HAND_Y = -9.60f
    const val SHOULDER_X = 2.00f
    const val SHOULDER_Y = -21.00f
    const val PUMPKIN_X = 7.80f
    const val PUMPKIN_Y = -42.20f

    /** The cape, and where it blits from its pivot at the shoulders. */
    val CAPE_RES = R.drawable.horseman_cape
    const val CAPE_X = -4.00f
    const val CAPE_Y = -4.00f

    /** The group's extent: every piece's box in every pose, at rest. The drawer puts its middle on the flight point. */
    const val LEFT = -35.00f
    const val RIGHT = 36.00f
    const val TOP = -63.23f
    const val BOTTOM = 24.00f

    /** What is seen of the rider, from the seat: the top of his collar and the bottom of his boot. His size is set on it. */
    const val RIDER_TOP = -26.98f
    const val RIDER_BOTTOM = 12.59f

    /**
     * How tall the rider is drawn against an adult walking on the near row of the pavement: Santa's, the maintainer's
     * *«cavaliere deve diventare 1,3 come babbo natale»* (2026-10-21).
     */
    const val RIDER_TO_PEDESTRIAN = 1.30f
}
