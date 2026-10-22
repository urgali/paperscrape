package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R

/**
 * The Halloween decorations of the houses and the shops (v5.16), in the building's piece units: where each piece blits
 * from the point it hangs from or stands on ([HalloweenDecorations] says where those are). **Generated** by
 * `tools/assets/halloween/build_halloween_deco.py` from the geometry it draws the SVGs with; edit that script, not this
 * file.
 */
internal object HalloweenDecorationTable {
    /** The cobweb in the left corner of a roof, from the corner it hangs in (mirrored at the right corner). */
    val WEB_RES = R.drawable.deco_cobweb
    const val WEB_X = -2.00f
    const val WEB_Y = -2.00f

    /** The bat, from the point of the roofline it hangs from by its feet. */
    val BAT_RES = R.drawable.deco_bat
    const val BAT_X = -10.00f
    const val BAT_Y = -1.00f

    /** The jack-o'-lantern by a front door, from its bottom's middle, where it stands. */
    val PUMPKIN_RES = R.drawable.deco_jack_o_lantern
    const val PUMPKIN_X = -8.00f
    const val PUMPKIN_Y = -15.00f

    /** The candle's light in its cuts, laid over it at night, from the same point. */
    val PUMPKIN_LIGHT_RES = R.drawable.deco_jack_o_lantern_light
    const val PUMPKIN_LIGHT_X = -5.00f
    const val PUMPKIN_LIGHT_Y = -10.00f

    /** The narrowest designed band, in units; the card sizes its copy on it. */
    const val DECORATION_BAND_UNITS = 2.59f
}
