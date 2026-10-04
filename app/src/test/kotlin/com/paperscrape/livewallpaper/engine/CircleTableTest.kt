package com.paperscrape.livewallpaper.engine

import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [CircleTable] holds, for every segment count, the very floats the tessellation loop used to
 * compute -- not values close to them. A vertex built from the table is then the vertex the loop
 * built, which is what keeps every circle, ring, oval and glow of the GL frame the same pixels.
 */
class CircleTableTest {

    /** The expression `GlSceneTarget`'s loops evaluated per slice until v5.10B, verbatim. */
    private fun loopCos(segments: Int, i: Int): Float {
        val step = (2.0 * Math.PI).toFloat() / segments
        val angle = step * i
        return cos(angle)
    }

    private fun loopSin(segments: Int, i: Int): Float {
        val step = (2.0 * Math.PI).toFloat() / segments
        val angle = step * i
        return sin(angle)
    }

    @Test
    fun `every entry is the loop's own cosine and sine, bit for bit, for every segment count`() {
        var compared = 0
        for (segments in CircleTable.MIN_SEGMENTS..CircleTable.MAX_SEGMENTS) {
            val cosines = CircleTable.cosines(segments)
            val sines = CircleTable.sines(segments)
            for (i in 0..segments) {
                assertEquals("cos, $segments segments, slice $i", loopCos(segments, i).toRawBits(), cosines[i].toRawBits())
                assertEquals("sin, $segments segments, slice $i", loopSin(segments, i).toRawBits(), sines[i].toRawBits())
                compared++
            }
        }
        assertEquals("57 segment counts, 2 109 slices", 2_109, compared)
    }

    @Test
    fun `each segment count has one entry per slice boundary, from 0 to the full turn`() {
        for (segments in CircleTable.MIN_SEGMENTS..CircleTable.MAX_SEGMENTS) {
            assertEquals(segments + 1, CircleTable.cosines(segments).size)
            assertEquals(segments + 1, CircleTable.sines(segments).size)
        }
        // The bounds the target clamps its segment count to are the table's.
        assertEquals(8, CircleTable.MIN_SEGMENTS)
        assertEquals(64, CircleTable.MAX_SEGMENTS)
    }
}
