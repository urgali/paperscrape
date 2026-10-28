package com.paperscrape.livewallpaper.engine

/**
 * Where each thing on the lake sits across the water, and in what order the water is painted.
 *
 * Two separate questions, answered in the same place.
 *
 * **Which line does a candidate travel along?** The band is cut into lanes and the two categories
 * take alternate ones, so a boat and a dolphin can never be placed on the same line, and there is
 * one lane per candidate per category, [LANE_COUNT] lanes. With fewer, a modulo would fold a
 * candidate back onto the lane of another of the *same* category -- and since every candidate
 * picks its own speed, the two would spend their time sliding through each other.
 *
 * **Which one is in front?** On a flat scene with a horizon, distance *is* height: the thing
 * lower down the band is nearer the viewer and has to be painted last. [orderByDepth] is that
 * rule, and it is what makes an overlap read as one boat passing in front of another rather than
 * as one boat sailing over another. Drawn in candidate order instead, whichever had the higher
 * index would cover the other regardless of where each sits on the water.
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
     * return the same lane -- which is the whole point, and what a `% LANE_COUNT` could not
     * promise.
     */
    fun laneIndex(candidate: Int, isDolphin: Boolean): Int = candidate * 2 + if (isDolphin) 1 else 0

    /**
     * The value [orderByDepth] sorts on: how far down the screen a thing's own base is drawn.
     *
     * For everything that stays on the water that is just its lane, and [heightAboveLane] is zero.
     * It is not zero for a dolphin in mid-leap: sorting an airborne animal by the lane it left
     * reads its depth from a point its body is no longer at. A sail stands several lane widths
     * above its own waterline (roughly seven to nine with the lake's geometry) while lanes are one
     * lane width apart, so a dolphin a single lane nearer than a sailboat -- painted after it,
     * correctly, by lane -- would cross the sail in mid-air.
     *
     * Ordering by the base makes one rule cover both: a dolphin recedes as it rises, drops
     * behind the boat whose waterline it has climbed past, and comes back in front as it lands.
     * Boats keep their lane, so two overlapping hulls read far to near, and because
     * [heightAboveLane] is never negative nothing is ever pulled *forward* of where its lane put it.
     */
    fun depthOf(laneY: Float, heightAboveLane: Float): Float = laneY - heightAboveLane

    /** The three kinds of thing on the water, for [visibleWaterline]. */
    enum class Kind { SAILBOAT, DOLPHIN, WAVE }

    /**
     * **Where a thing's drawing meets the water, which is the one value [orderByDepth] may compare**
     *
     * The three kinds are placed from three different points: a sailboat by its placement point,
     * which `drawSailboat` hangs the hull
     * [PaperRenderer.SAILBOAT_HULL_WATERLINE_UNITS] below; a dolphin by its lane, with its body
     * drawn centred on the leap point, so its belly is half the body -- `-DOLPHIN_ORIGIN_Y_UNITS` of
     * its own units -- below; a wave by its base, which is its waterline. Keyed by those points, a
     * leaping dolphin would be off against both of the others by the difference, 16.9 px on the
     * BV6600's 1440 px surface, and paint across a wave and the sail of a boat whose waterlines are
     * both nearer than its own (at the factory settings, on Beach in a thunderstorm, 63 times in
     * fifteen minutes of scene).
     *
     * So every kind answers the same question in the same frame: **how far down the screen does
     * its drawing meet the water right now.**
     *
     * - a sailboat: its placement point plus the hull's offset, at the boat's own scale;
     * - a dolphin **in the air** ([heightAboveLane] > 0): its lane, minus the climb, plus the half
     *   body under the leap point, at the dolphin's own scale; **under water** only the splash is
     *   drawn, and the splash stands on the lane (`SPLASH_ORIGIN_Y_UNITS` is its whole height), so
     *   the waterline is the lane itself;
     * - a wave: its base.
     *
     * Two properties hold by construction: boats among themselves, and waves against boats, are
     * compared waterline to waterline; and the climb only ever moves a dolphin backwards ([depthOf]).
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
