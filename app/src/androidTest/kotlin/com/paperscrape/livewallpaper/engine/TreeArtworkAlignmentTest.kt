package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Where the winter tree's snow cap actually lands on the crown (v3.8 Filone 5).
 *
 * ### Why this test exists, and what it disproves
 *
 * v3.7 reported that the wallpaper draws the snow cap 3 units off-centre: the cap is 76 scene units
 * wide, the crown is 82, and both are blitted at the crown's origin, so on width arithmetic alone
 * the cap looks left-aligned rather than centred. That reasoning was **wrong**, and the mistake was
 * comparing *canvas widths* instead of measuring *content*.
 *
 * Measured here, from the shipped PNGs: at the offset the renderer uses, **not one of the cap's
 * 17 182 opaque pixels falls outside the crown**. The two sprites were authored on a shared origin —
 * their top rows are pixel-identical — and the cap is a narrower canvas only because it is 37 units
 * tall and stops before the crown reaches its widest point. Moving it to "centre" it would push
 * 442 pixels of snow off the crown into open sky.
 *
 * So nothing was changed in v3.8, and this test is what stops the same wrong inference being drawn
 * again from the same tempting arithmetic. **It is an assertion about the artwork**, which nothing
 * else in the suite checks: a redrawn crown or cap that no longer agree would fail here rather than
 * in a golden, where a few hundred stray pixels of snow could sit under the whole-frame tolerance.
 */
@RunWith(AndroidJUnit4::class)
class TreeArtworkAlignmentTest {

    private fun decode(resId: Int): Bitmap {
        val options = BitmapFactory.Options().apply { inScaled = false }
        return BitmapFactory.decodeResource(
            InstrumentationRegistry.getInstrumentation().targetContext.resources,
            resId,
            options,
        )
    }

    private fun opaque(bitmap: Bitmap, x: Int, y: Int): Boolean =
        x in 0 until bitmap.width && y in 0 until bitmap.height && (bitmap.getPixel(x, y) ushr 24) > 8

    /** Cap pixels that miss the crown, when the cap is offset by [dx], [dy] sprite pixels. */
    private fun capPixelsOffCrown(crown: Bitmap, cap: Bitmap, dx: Int, dy: Int): Pair<Int, Int> {
        var off = 0
        var total = 0
        for (y in 0 until cap.height) {
            for (x in 0 until cap.width) {
                if (!opaque(cap, x, y)) continue
                total++
                if (!opaque(crown, x + dx, y + dy)) off++
            }
        }
        return off to total
    }

    /**
     * **The measurement.** At the shared origin the renderer uses, the cap is entirely on the crown.
     */
    @Test
    fun theSnowCapLandsEntirelyOnTheCrown() {
        val crown = decode(R.drawable.tree_canopy)
        val cap = decode(R.drawable.tree_canopy_snowcap)
        val (off, total) = capPixelsOffCrown(crown, cap, dx = 0, dy = 0)
        android.util.Log.i(
            "SNOWCAP",
            "at the renderer's offset: $off of $total cap pixels fall off the crown " +
                "(${"%.2f".format(off * 100.0 / total)}%)",
        )
        assertTrue("expected a substantial cap to measure, got $total pixels", total > 10_000)
        assertEquals("the snow cap must sit entirely on the crown", 0, off)
    }

    /**
     * The v3.7 hypothesis, kept as a test so the claim stays disproved rather than merely
     * corrected in a document: centring the cap by its canvas width pushes snow into the sky.
     *
     * Three scene units is nine sprite pixels — the sprites are authored at 3x.
     */
    @Test
    fun centringTheCapByItsCanvasWidthWouldPushSnowOffTheCrown() {
        val crown = decode(R.drawable.tree_canopy)
        val cap = decode(R.drawable.tree_canopy_snowcap)
        val (off, total) = capPixelsOffCrown(crown, cap, dx = 3 * 3, dy = 0)
        android.util.Log.i("SNOWCAP", "centred by canvas width: $off of $total cap pixels off the crown")
        assertTrue("the 'centred' offset should be visibly worse, was $off", off > 100)
    }

    /** And the offset the preview carried before v3.7 corrected it, for the same reason. */
    @Test
    fun theOldPreviewOffsetWouldAlsoPushSnowOffTheCrown() {
        val crown = decode(R.drawable.tree_canopy)
        val cap = decode(R.drawable.tree_canopy_snowcap)
        val (off, _) = capPixelsOffCrown(crown, cap, dx = 3 * 3, dy = 2 * 3)
        android.util.Log.i("SNOWCAP", "old preview offset: $off cap pixels off the crown")
        assertTrue("the pre-v3.7 preview offset should be worse than the renderer's, was $off", off > 0)
    }

    /**
     * Why the two agree: they were authored on one canvas. The top row of each is the same
     * silhouette, which is what `drawTree`'s own comment claims and nothing had ever checked.
     */
    @Test
    fun theCapRepeatsTheCrownsOwnUpperSilhouette() {
        val crown = decode(R.drawable.tree_canopy)
        val cap = decode(R.drawable.tree_canopy_snowcap)
        for (y in intArrayOf(0, 3, 6, 12)) {
            val crownSpan = (0 until crown.width).filter { opaque(crown, it, y) }
            val capSpan = (0 until cap.width).filter { opaque(cap, it, y) }
            assertTrue("crown row $y is empty", crownSpan.isNotEmpty())
            assertTrue("cap row $y is empty", capSpan.isNotEmpty())
            assertEquals("row $y left edge", crownSpan.first(), capSpan.first())
            assertEquals("row $y right edge", crownSpan.last(), capSpan.last())
        }
    }

    /** The offsets the renderer and the preview both read, asserted to be the same one. */
    @Test
    fun bothCallersUseTheCrownsOrigin() {
        assertEquals(TreeSpriteLayout.CANOPY_X, TreeSpriteLayout.SNOWCAP_X, 0f)
        assertEquals(TreeSpriteLayout.CANOPY_Y, TreeSpriteLayout.SNOWCAP_Y, 0f)
    }
}
