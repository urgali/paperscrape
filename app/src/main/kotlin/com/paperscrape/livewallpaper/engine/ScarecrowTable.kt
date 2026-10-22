package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R

/**
 * The Halloween scarecrow (v5.16), in units from the foot of its pole: where its two pieces blit, and how tall it is.
 * **Generated** by `tools/assets/scarecrow/build_scarecrow.py` from the geometry it draws the SVGs with; edit that
 * script, not this file.
 */
internal object ScarecrowTable {
    /** The scarecrow on its pole, and where it blits: its canvas is centred on the pole and ends at the ground. */
    val RES = R.drawable.scarecrow
    const val X = -25.00f
    const val Y = -57.00f

    /** The candle's light in its carved face, laid over it at night, and where it blits. */
    val LIGHT_RES = R.drawable.scarecrow_light
    const val LIGHT_X = -10.00f
    const val LIGHT_Y = -40.00f

    /** The drawing's height, the pole's foot to the hat's tip, in units, and what it reads as. */
    const val UNITS_TALL = 56.94f
    const val METRES_TALL = 2.40f

    /** The narrowest designed band, in units. The card draws its scarecrow at a snowman's height, not on this. */
    const val SCARECROW_BAND_UNITS = 4.50f
}
