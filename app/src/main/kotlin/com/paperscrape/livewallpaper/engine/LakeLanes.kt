package com.paperscrape.livewallpaper.engine

/**
 * Where each thing on the lake sits across the water, and in what order the water is painted.
 *
 * Two separate questions used to be answered badly in the same place.
 *
 * **Which line does a candidate travel along?** The band is cut into lanes and the two categories
 * take alternate ones, so a boat and a dolphin can never be placed on the same line. That part was
 * right. What was wrong was the arithmetic: with four candidates per category and six lanes, the
 * lane was `(i * 2 + category) % 6`, and `% 6` folded the fourth candidate back onto the first's
 * lane. Candidate 0 and candidate 3 of the *same* category therefore shared a line -- and since
 * every candidate picks its own speed, the two spent their time sliding through each other. One
 * lane per candidate per category is [LANE_COUNT] lanes, not six, and then nothing folds.
 *
 * **Which one is in front?** Nothing decided. The lake drew its boats in candidate order and then
 * its dolphins in candidate order, so whichever had the higher index covered the other regardless
 * of where each sat on the water. On a flat scene with a horizon, distance *is* height: the thing
 * lower down the band is nearer the viewer and has to be painted last. [orderByDepth] is that
 * rule, and it is what makes an overlap read as one boat passing in front of another rather than
 * as one boat sailing over another.
 *
 * Nothing here scales anything by depth. The scene is deliberately flat paper -- a boat further up
 * the water is further away, and it says so by being higher up and behind, not by being smaller.
 */
internal object LakeLanes {

    /**
     * One lane per candidate per category.
     *
     * [PaperRenderer.LAKE_DECORATION_POOL_SIZE] candidates for boats and the same again for
     * dolphins, interleaved, so both categories still reach the near edge and the far edge of the
     * water instead of each being given half of it.
     */
    const val LANE_COUNT = 8

    /**
     * The lane a candidate travels along.
     *
     * Even lanes are boats, odd lanes are dolphins, and no two calls with different arguments ever
     * return the same lane -- which is the whole point, and is what the `% LANE_COUNT` this
     * replaced could not promise.
     */
    fun laneIndex(candidate: Int, isDolphin: Boolean): Int = candidate * 2 + if (isDolphin) 1 else 0

    /**
     * Fills [order] with `0 until count`, sorted so that the smallest [depths] value comes first:
     * far to near, which is the order the water has to be painted in.
     *
     * Insertion sort over at most [LANE_COUNT] entries, writing into arrays the renderer owns, so
     * a frame costs no allocation -- this runs inside the draw path. It is stable, so two things
     * at exactly the same height keep a fixed order rather than flickering between frames.
     */
    fun orderByDepth(depths: FloatArray, count: Int, order: IntArray) {
        for (i in 0 until count) order[i] = i
        for (i in 1 until count) {
            val candidate = order[i]
            val depth = depths[candidate]
            var j = i - 1
            while (j >= 0 && depths[order[j]] > depth) {
                order[j + 1] = order[j]
                j--
            }
            order[j + 1] = candidate
        }
    }
}
