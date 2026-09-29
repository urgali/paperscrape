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
     * The value [orderByDepth] sorts on: how far down the screen a thing's own base is drawn.
     *
     * For everything that stays on the water that is just its lane, and [heightAboveLane] is zero.
     * It is not zero for a dolphin in mid-leap, and that is the whole of the v3.1 fix: sorting an
     * airborne animal by the lane it left reads its depth from a point its body is no longer at.
     * A sail stands several lane widths above its own waterline (about four when v3.1 measured
     * it, roughly seven to nine with today's lake geometry) while lanes are one lane width
     * apart, so a dolphin a single lane nearer than a sailboat -- painted after it, correctly, by
     * lane -- crossed the sail in mid-air.
     *
     * Ordering by the base instead makes one rule cover both: a dolphin recedes as it rises, drops
     * behind the boat whose waterline it has climbed past, and comes back in front as it lands.
     * Nothing else changes -- boats keep the lane they always had, so the far-to-near reading of
     * two overlapping hulls that v3.0 established is untouched, and because [heightAboveLane] is
     * never negative nothing is ever pulled *forward* of where its lane put it.
     */
    fun depthOf(laneY: Float, heightAboveLane: Float): Float = laneY - heightAboveLane

    /** The three kinds of thing on the water, for [visibleWaterline]. */
    enum class Kind { SAILBOAT, DOLPHIN, WAVE }

    /**
     * **Where a thing's drawing meets the water, which is the one value [orderByDepth] may compare**
     * (v5.9B, inventory I-02 -- the real fix `BACKLOG_v4_28.md` item 84 named and did not take).
     *
     * The three kinds are placed from three different points, and until v5.9B each was keyed by its
     * own: a sailboat by its placement point, which `drawSailboat` hangs the hull
     * [PaperRenderer.SAILBOAT_HULL_WATERLINE_UNITS] below; a dolphin by its lane, with its body
     * drawn centred on the leap point, so its belly is half the body -- `-DOLPHIN_ORIGIN_Y_UNITS` of
     * its own units -- below; a wave by its base, which is its waterline. v4.28 lifted the wave by the
     * hull's offset so that wave and hull were compared waterline to waterline, and left the dolphin
     * alone: against both of the others it was still off by the difference, 16.9 px on the BV6600's
     * 1440 px surface. The probe of v5.9B found that difference on screen at the factory settings --
     * on Beach in a thunderstorm, 63 times in fifteen minutes of scene, a leaping dolphin painted
     * across a wave and the sail of a boat whose waterlines were both nearer than its own
     * (`consegna_v5_9b/registri/i02_sonda/`).
     *
     * So every kind now answers the same question in the same frame: **how far down the screen does
     * its drawing meet the water right now.**
     *
     * - a sailboat: its placement point plus the hull's offset, at the boat's own scale;
     * - a dolphin **in the air** ([heightAboveLane] > 0): its lane, minus the climb, plus the half
     *   body under the leap point, at the dolphin's own scale; **under water** only the splash is
     *   drawn, and the splash stands on the lane (`SPLASH_ORIGIN_Y_UNITS` is its whole height), so
     *   the waterline is the lane itself;
     * - a wave: its base.
     *
     * Two properties survive by construction. Boats among themselves and waves against boats are
     * ordered exactly as before, because each moved by the same amount the other did; what changes
     * is the dolphin against both. The climb still only ever moves a dolphin backwards (v3.1).
     */
    fun visibleWaterline(kind: Kind, placementY: Float, heightAboveLane: Float, screenHeight: Float): Float {
        val scale = SceneSpace.sceneScale(screenHeight)
        return when (kind) {
            Kind.SAILBOAT -> placementY + PaperRenderer.SAILBOAT_HULL_WATERLINE_UNITS * SceneSpace.SAILBOAT_BASE_SCALE * scale
            Kind.DOLPHIN -> if (heightAboveLane > 0f) {
                depthOf(placementY, heightAboveLane) - PaperRenderer.DOLPHIN_ORIGIN_Y_UNITS * SceneSpace.DOLPHIN_BASE_SCALE * scale
            } else {
                placementY
            }
            Kind.WAVE -> placementY
        }
    }

    /**
     * Fills [order] with `0 until count`, sorted so that the smallest [depths] value comes first:
     * far to near, which is the order the water has to be painted in.
     *
     * Insertion sort over at most [LANE_COUNT] + [PaperRenderer.WAVE_POOL] entries (boats,
     * dolphins and waves), writing into arrays the renderer owns, so a frame costs no allocation
     * -- this runs inside the draw path. It is stable, so two things at exactly the same height
     * keep a fixed order rather than flickering between frames.
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
