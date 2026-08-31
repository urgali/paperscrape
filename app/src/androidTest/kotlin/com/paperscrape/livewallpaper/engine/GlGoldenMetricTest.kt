package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The GL comparison ignores where a driver puts an edge, and nothing else.
 *
 * [GlGolden.InteriorMask] exists because the whole-frame count was measuring how much outline a
 * scene contains rather than whether the scene changed: on an Adreno 630, against goldens captured
 * on the reference emulator, 99.8% / 98.2% / 86.4% of the over-threshold pixels sat within one pixel
 * of an edge, and the frames were identical between commits. Masking those pixels is only defensible
 * if the mask cannot also hide a real regression, so this test tries to hide five.
 *
 * Each case takes a committed golden, damages it the way a real bug would, and requires the metric
 * to still call it different. The last case does the opposite: it reproduces the driver difference
 * itself -- every silhouette displaced by one pixel -- and requires the metric to accept it.
 */
@RunWith(AndroidJUnit4::class)
class GlGoldenMetricTest {

    private val channel = GlGolden.Tolerance.GlTarget.CHANNEL
    private val limit = GlGolden.Tolerance.GlTarget.MAX_FRACTION

    private fun golden(name: String): Bitmap {
        val context = InstrumentationRegistry.getInstrumentation().context
        return context.assets.open("golden/$name.png").use {
            android.graphics.BitmapFactory.decodeStream(it)
        }.copy(Bitmap.Config.ARGB_8888, true)
    }

    /** The metric's verdict: the fraction of judged pixels that differ, and how much was masked. */
    private fun verdict(a: Bitmap, b: Bitmap): Pair<Double, Double> {
        val (differing, masked) = GlGolden.countAtLeastInInteriors(a, b, channel)
        val judged = (GlGolden.WIDTH * GlGolden.HEIGHT) * (1.0 - masked)
        return differing / judged to masked
    }

    /**
     * Caught by **either** half of the check: interiors that changed, or outlines that moved.
     *
     * Both are needed and neither is enough. A tint shift moves no edge and is invisible to the
     * displacement measure; a band slid three pixels maps flat colour onto the same flat colour and
     * is nearly invisible to the interior measure (0.075%, inside the limit). The pair is the check.
     */
    private fun assertCaught(name: String, a: Bitmap, b: Bitmap) {
        val (fraction, masked) = verdict(a, b)
        val displaced = GlGolden.edgeDisplacement(a, b)
        assertTrue(
            "$name was not caught: ${"%.4f".format(fraction * 100)}% of interiors differ " +
                "(limit ${"%.3f".format(limit * 100)}%, ${"%.1f".format(masked * 100)}% masked) and " +
                "${"%.2f".format(displaced * 100)}% of the outline moved (limit " +
                "${"%.2f".format(GlGolden.EdgeDisplacement.MAX_DISPLACED_FRACTION * 100)}%)",
            fraction > limit || displaced > GlGolden.EdgeDisplacement.MAX_DISPLACED_FRACTION,
        )
    }

    @Test
    fun anObjectMovedByThreePixelsIsCaught() {
        val a = golden("gl-day")
        val b = a.copy(Bitmap.Config.ARGB_8888, true)
        // Slide a band of the skyline sideways: the buildings are still there, just not where the
        // golden puts them. This is the "a sprite drifted" regression.
        val w = GlGolden.WIDTH
        val row = IntArray(w)
        for (y in 470 until 620) {
            a.getPixels(row, 0, w, 0, y, w, 1)
            val moved = IntArray(w) { row[(it + w - 3) % w] }
            b.setPixels(moved, 0, w, 0, y, w, 1)
        }
        assertCaught("a three-pixel drift", a, b)
    }

    @Test
    fun aFlatAreaPaintedTheWrongColourIsCaught() {
        val a = golden("gl-day")
        val b = a.copy(Bitmap.Config.ARGB_8888, true)
        // A wall tinted wrong: interiors, which is precisely what the mask keeps.
        for (y in 300 until 380) {
            for (x in 40 until 320) {
                val p = a.getPixel(x, y)
                b.setPixel(x, y, (p and 0xFF00FFFF.toInt()) or (((p shr 8 and 0xFF) / 2) shl 8))
            }
        }
        assertCaught("a mistinted area", a, b)
    }

    @Test
    fun anObjectErasedIsCaught() {
        val a = golden("gl-day")
        val b = a.copy(Bitmap.Config.ARGB_8888, true)
        // Paint a block of the scene over with the colour just above it: a sprite that stopped
        // being drawn leaves exactly this.
        val fill = a.getPixel(180, 300)
        for (y in 500 until 600) for (x in 120 until 260) b.setPixel(x, y, fill)
        assertCaught("an erased object", a, b)
    }

    @Test
    fun aGlobalTintShiftIsCaught() {
        val a = golden("gl-day")
        val b = a.copy(Bitmap.Config.ARGB_8888, true)
        // The whole frame 20 levels darker: no edge moves at all, so this is the case a
        // purely edge-based excuse would be most tempted to forgive.
        val w = GlGolden.WIDTH
        val row = IntArray(w)
        for (y in 0 until GlGolden.HEIGHT) {
            a.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val p = row[x]
                fun dim(shift: Int) = maxOf(0, ((p shr shift) and 0xFF) - 20)
                row[x] = (0xFF shl 24) or (dim(16) shl 16) or (dim(8) shl 8) or dim(0)
            }
            b.setPixels(row, 0, w, 0, y, w, 1)
        }
        assertCaught("a global tint shift", a, b)
    }

    /**
     * A sprite drawn at the wrong size, which is neither a drift nor a tint.
     *
     * The whole frame is scaled 3% about its centre: every flat interior keeps its colour, so the
     * interior half sees very little, and every outline moves by an amount that grows with the
     * distance from the centre. This is the shape a scale-table mistake takes -- v4.15's tower
     * declaration would have looked like this had it not been pixel-neutral -- and it is the case
     * the pair of measures exists to cover between them.
     */
    @Test
    fun anObjectDrawnAtTheWrongScaleIsCaught() {
        val a = golden("gl-day")
        val b = a.copy(Bitmap.Config.ARGB_8888, true)
        val w = GlGolden.WIDTH
        val h = GlGolden.HEIGHT
        val src = IntArray(w * h)
        a.getPixels(src, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        val cx = w / 2f
        val cy = h / 2f
        for (y in 0 until h) {
            for (x in 0 until w) {
                val sx = ((x - cx) / 1.03f + cx).toInt().coerceIn(0, w - 1)
                val sy = ((y - cy) / 1.03f + cy).toInt().coerceIn(0, h - 1)
                out[y * w + x] = src[sy * w + sx]
            }
        }
        b.setPixels(out, 0, w, 0, 0, w, h)
        assertCaught("a three-percent scale error", a, b)
    }

    /**
     * The composition changed without anything being added, removed or recoloured.
     *
     * A block of the scene is mirrored in place: the same pixels, the same colours, the same
     * histogram, arranged differently. A check that counted colours rather than positions would
     * see nothing at all here.
     */
    @Test
    fun aRearrangedCompositionIsCaught() {
        val a = golden("gl-day")
        val b = a.copy(Bitmap.Config.ARGB_8888, true)
        val left = 60
        val right = 300
        for (y in 440 until 620) {
            for (x in left until right) {
                b.setPixel(x, y, a.getPixel(right - 1 - (x - left), y))
            }
        }
        assertCaught("a mirrored block", a, b)
    }

    @Test
    fun aOnePixelEdgeDisplacementIsAccepted() {
        // The driver difference itself, reproduced: shift the whole frame by one pixel, which moves
        // every silhouette by one pixel and nothing else. On the phone this is 1.1-1.7% of the frame
        // by the old whole-frame count and must not fail here.
        val a = golden("gl-day")
        val b = a.copy(Bitmap.Config.ARGB_8888, true)
        val w = GlGolden.WIDTH
        val row = IntArray(w)
        for (y in 1 until GlGolden.HEIGHT) {
            a.getPixels(row, 0, w, 0, y - 1, w, 1)
            b.setPixels(row, 0, w, 0, y, w, 1)
        }
        val (fraction, masked) = verdict(a, b)
        val displaced = GlGolden.edgeDisplacement(a, b)
        assertTrue(
            "a one-pixel displacement was rejected by the interior check: " +
                "${"%.4f".format(fraction * 100)}% differ (${"%.1f".format(masked * 100)}% masked)",
            fraction <= limit,
        )
        assertTrue(
            "a one-pixel displacement was rejected by the outline check: " +
                "${"%.2f".format(displaced * 100)}% moved",
            displaced <= GlGolden.EdgeDisplacement.MAX_DISPLACED_FRACTION,
        )
    }

    @Test
    fun theGoldensLeaveEnoughInteriorToJudgeThemBy() {
        for (name in listOf("gl-day", "gl-lake-busy", "gl-thunderstorm")) {
            val a = golden(name)
            val (_, masked) = verdict(a, a)
            assertTrue(
                "$name is ${"%.1f".format(masked * 100)}% edge, over the " +
                    "${"%.0f".format(GlGolden.InteriorMask.MAX_MASKED_FRACTION * 100)}% guard",
                masked <= GlGolden.InteriorMask.MAX_MASKED_FRACTION,
            )
        }
    }
}
