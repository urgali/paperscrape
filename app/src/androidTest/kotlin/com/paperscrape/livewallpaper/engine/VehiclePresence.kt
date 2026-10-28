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
    const val VEHICLE_COLUMN_DEPTH_SHARE = 0.12

    /**
     * The road band's tarmac (its commonest colour), how much of the band is not tarmac, and the runs
     * of columns a vehicle fills.
     *
     * The band is read in one `getPixels` and its colours counted on a sorted copy, with nothing boxed:
     * `CarNightCrossfadeTest`, the suite's slowest test, measures some four thousand frames, and a
     * `getPixel` and a `HashMap<Int, Int>` entry per pixel would cost each of them some 35 000 calls
     * through JNI and some 17 000 boxed counts. The decisions are the ones of a pixel-by-pixel read
     * into that map, to the pixel (`VehiclePresenceTest` holds them against it), down to a tie for the
     * commonest colour, which goes to the colour the map's own order would have met first
     * ([commonestAsTheMapOrdersIt]).
     */
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

        // The band, row after row: pixel (x, y) is band[(y - top) * w + x].
        val rows = bottom - top + 1
        val band = IntArray(rows * w)
        bitmap.getPixels(band, 0, w, 0, top, w, rows)

        val sorted = IntArray(band.size) { band[it] or OPAQUE }
        sorted.sort()
        var tarmacCount = 0
        var tarmacColour = 0
        var tied = false
        var i = 0
        while (i < sorted.size) {
            var j = i + 1
            while (j < sorted.size && sorted[j] == sorted[i]) j++
            val count = j - i
            if (count > tarmacCount) {
                tarmacCount = count
                tarmacColour = sorted[i]
                tied = false
            } else if (count == tarmacCount) {
                tied = true
            }
            i = j
        }
        if (tied) tarmacColour = commonestAsTheMapOrdersIt(band)
        val bandPixels = rows * w

        var nonTarmac = 0
        val columnHit = BooleanArray(w)
        for (x in 0 until w) {
            var hits = 0
            var k = x
            for (y in top..bottom) {
                if (delta(band[k], tarmacColour) > 90) {
                    nonTarmac++
                    hits++
                }
                k += w
            }
            // A lane marking is a thin dash; a vehicle fills a good part of the band's depth, and the
            // share sits midway between the weakest column that must count and the strongest thing
            // that must not. The weakest is a car at dusk: as the night crossfade darkens it, its
            // body's delta from the tarmac falls under the threshold and only its darkest slice still
            // qualifies. Measured over the eight `CarNightCrossfadeTest` frames around it, the
            // far-lane car's columns carry a median 0.208 of the band and a tenth percentile of
            // 0.167, a lane dash carries 0.062, and there is nothing in between -- at 0.16 and below
            // the car's run is 47 to 54 columns in every frame, at 0.20 it is nine to twenty-six,
            // under the twelve a run needs. An eighth keeps the car on the road in every frame.
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

        return Result(top, bottom, tarmacCount / bandPixels.toDouble(), nonTarmac, bandPixels, runs)
    }

    /**
     * Of the colours tied for the commonest in [band], the one a `HashMap<Int, Int>` filled in the
     * band's order meets first: what `maxByOrNull` over that map picks, which keeps the first of equal
     * counts. Only a tie comes here, so the boxing is paid on a frame that has one.
     */
    private fun commonestAsTheMapOrdersIt(band: IntArray): Int {
        val counts = HashMap<Int, Int>()
        for (pixel in band) {
            val p = pixel or OPAQUE
            counts[p] = (counts[p] ?: 0) + 1
        }
        return counts.maxByOrNull { it.value }!!.key
    }

    private const val OPAQUE = 0xFF shl 24

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
        // Nine rows, 4 to 12 px above the wheel line: the body, clear of the road markings that
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
