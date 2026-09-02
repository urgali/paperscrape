package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap

/**
 * Counts how much of the road band is covered by something that is not tarmac.
 *
 * The road is a flat, near-uniform strip, so "vehicle" and "not the modal colour of the strip" are
 * the same measurement at this frame size. Deliberately crude and deliberately independent of the
 * renderer: it looks at the finished pixels, so it cannot agree with a bug by sharing its
 * arithmetic.
 */
object VehiclePresence {

    data class Result(
        val bandTop: Int,
        val bandBottom: Int,
        val tarmacFraction: Double,
        val nonTarmacPixels: Int,
        val bandPixels: Int,
        val runs: List<IntRange>,
    ) {
        val nonTarmacFraction: Double get() = nonTarmacPixels / bandPixels.toDouble()
        override fun toString(): String =
            "band=$bandTop..$bandBottom tarmac=${"%.1f".format(tarmacFraction * 100)}% " +
                "nonTarmac=${"%.2f".format(nonTarmacFraction * 100)}% runs=${runs.size} " +
                "widths=${runs.map { it.last - it.first + 1 }}"
    }

    /** How much of the road band a column must carry before it counts as vehicle and not marking. */
    const val VEHICLE_COLUMN_DEPTH_SHARE = 0.20

    fun measure(bitmap: Bitmap): Result {
        val h = bitmap.height
        val w = bitmap.width
        val spacing = SceneSpace.CANONICAL_LANE_SPACING_FRACTION
        val margin = SceneSpace.roadEdgeMarginFraction(
            SceneSpace.ROAD_LANE_FAR_Y_FRACTION,
            SceneSpace.ROAD_LANE_NEAR_Y_FRACTION,
        )
        require(spacing > 0f)
        val top = ((SceneSpace.ROAD_LANE_FAR_Y_FRACTION - margin) * h).toInt()
        val bottom = ((SceneSpace.ROAD_LANE_NEAR_Y_FRACTION + margin) * h).toInt()

        val counts = HashMap<Int, Int>()
        for (y in top..bottom) for (x in 0 until w) {
            val p = bitmap.getPixel(x, y) or (0xFF shl 24)
            counts[p] = (counts[p] ?: 0) + 1
        }
        val tarmac = counts.maxByOrNull { it.value }!!
        val bandPixels = (bottom - top + 1) * w

        var nonTarmac = 0
        val columnHit = BooleanArray(w)
        for (x in 0 until w) {
            var hits = 0
            for (y in top..bottom) {
                if (delta(bitmap.getPixel(x, y), tarmac.key) > 90) {
                    nonTarmac++
                    hits++
                }
            }
            // A lane marking is a thin dash; a vehicle fills a good part of the band's depth.
            //
            // The share was a quarter of the band until the saloon was redrawn. A quarter of a
            // 49-pixel band is twelve pixels of vehicle in a column, and the old shell reached that
            // almost everywhere along its length because it was a slab: a straight bottom edge and
            // a rear that ramped down from the roof in one line. The redrawn shell has a lower
            // nose, a boot deck and a hole cut over each wheel, so its ends are shallower, and the
            // far-lane car's dense run fell from thirteen columns to eleven -- under the twelve
            // this counts as a vehicle, so a car that is plainly on the road stopped being one.
            //
            // A fifth of the band is ten pixels, which is still five times a lane dash's three and
            // is what the constant was always for. Measured on both the old goldens and the new:
            // at a quarter the day frame reports three vehicles before and two after, and at a
            // fifth it reports three before and three after, four at night either side.
            columnHit[x] = hits >= (bottom - top) * VEHICLE_COLUMN_DEPTH_SHARE
        }

        val runs = ArrayList<IntRange>()
        var start = -1
        for (x in 0 until w) {
            if (columnHit[x]) {
                if (start < 0) start = x
            } else if (start >= 0) {
                if (x - start >= 12) runs.add(start until x)
                start = -1
            }
        }
        if (start >= 0 && w - start >= 12) runs.add(start until w)

        return Result(top, bottom, tarmac.value / bandPixels.toDouble(), nonTarmac, bandPixels, runs)
    }

    /**
     * Whether each lane carries a vehicle, as `(far, near)`.
     *
     * A vehicle stands on its own wheel line and rises from it, so the rows just *above* a lane's
     * line are the ones it occupies. Sampling there rather than across the whole band is what
     * separates the two lanes, which overlap vertically by design.
     */
    fun occupiedLanes(bitmap: Bitmap): Pair<Boolean, Boolean> {
        val h = bitmap.height
        val far = laneOccupied(bitmap, (SceneSpace.ROAD_LANE_FAR_Y_FRACTION * h).toInt())
        val near = laneOccupied(bitmap, (SceneSpace.ROAD_LANE_NEAR_Y_FRACTION * h).toInt())
        return far to near
    }

    private fun laneOccupied(bitmap: Bitmap, wheelLineY: Int): Boolean {
        val w = bitmap.width
        // Four rows immediately above the wheel line: the body, clear of the road markings that
        // sit on the surface itself.
        val rows = (wheelLineY - 12)..(wheelLineY - 4)
        val counts = HashMap<Int, Int>()
        for (y in rows) for (x in 0 until w) {
            if (y in 0 until bitmap.height) {
                val p = bitmap.getPixel(x, y) or (0xFF shl 24)
                counts[p] = (counts[p] ?: 0) + 1
            }
        }
        val background = counts.maxByOrNull { it.value }?.key ?: return false
        var run = 0
        var longest = 0
        for (x in 0 until w) {
            val hit = rows.any { y ->
                y in 0 until bitmap.height && delta(bitmap.getPixel(x, y), background) > 90
            }
            if (hit) { run++; if (run > longest) longest = run } else run = 0
        }
        // A vehicle is tens of pixels wide at this frame size; nothing else on a lane line is.
        return longest >= 12
    }

    private fun delta(a: Int, b: Int): Int = maxOf(
        Math.abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)),
        Math.abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)),
        Math.abs((a and 0xFF) - (b and 0xFF)),
    ) * 3
}
