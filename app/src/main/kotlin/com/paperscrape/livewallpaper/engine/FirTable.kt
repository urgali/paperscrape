package com.paperscrape.livewallpaper.engine

/**
 * The Christmas fir (v5.14), in units from the foot of its trunk: where its three pieces blit, where its lights
 * hang and its presents stand, and where the gallery card hangs its baubles. **Generated** by
 * `tools/assets/fir/build_fir.py` from the same geometry it draws the SVGs with; edit that script, not this file.
 * The fir stands 72 units wide and 126 tall, star included.
 */
internal object FirTable {
    /** The fir's width and height, star included, in units. */
    const val WIDTH = 72f
    const val HEIGHT = 126f

    /** Where `tree_fir` blits: the trunk, the under-papers and the tiers. */
    const val FIR_X = -35f
    const val FIR_Y = -120f

    /** Where `tree_fir_snow` blits, trimmed to its content. */
    const val SNOW_X = -32f
    const val SNOW_Y = -116f

    /** Where `tree_fir_star` blits, trimmed to its content. */
    const val STAR_X = -8f
    const val STAR_Y = -127f

    /** The centres of the ten lights, 3.5 units above the tips of the hems' inner teeth. */
    val LIGHT_X = floatArrayOf(21.0f, 7.0f, -7.0f, -21.0f, 14.25f, 0.0f, -14.25f, 7.17f, -7.17f, 0.0f)
    val LIGHT_Y = floatArrayOf(-18.5f, -18.5f, -18.5f, -18.5f, -41.5f, -41.5f, -41.5f, -65.5f, -65.5f, -89.5f)

    /** Where the three presents stand at the foot. */
    val GIFT_X = floatArrayOf(-17.0f, -1.0f, 14.0f)

    /** The centres of the gallery card's three baubles, over the hems of the upper three tiers, top first. */
    val BAUBLE_X = floatArrayOf(0.0f, 14.0f, -12.0f)
    val BAUBLE_Y = floatArrayOf(-84.0f, -58.0f, -32.0f)
}
