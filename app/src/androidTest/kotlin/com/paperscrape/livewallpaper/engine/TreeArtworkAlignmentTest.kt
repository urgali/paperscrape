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
 * opaque pixels falls outside the crown**. The two sprites were authored on a shared origin — their
 * top rows are pixel-identical — and the cap is a shorter canvas only because it stops before the
 * crown reaches its widest point. Moving it to "centre" it would push snow into open sky.
 *
 * So nothing was changed in v3.8, and this test is what stops the same wrong inference being drawn
 * again from the same tempting arithmetic. **It is an assertion about the artwork**, which nothing
 * else in the suite checks: a redrawn crown or cap that no longer agree would fail here rather than
 * in a golden, where a few hundred stray pixels of snow could sit under the whole-frame tolerance.
 *
 * ### v4.21 re-derived it against the new crown, and tightened it
 *
 * The "Quercia larga" replaced both sprites, so every count and row index this file used to name
 * was measured off artwork that no longer ships. Rather than re-tune the sample rows until they
 * passed, the assertions were restated as **properties of the pair**, each one measured from
 * whatever crown and cap are shipped:
 *
 *  - no cap pixel lies off the crown (unchanged, and still the load-bearing one);
 *  - the cap's span is a subset of the crown's on *every* row, not just the sampled ones;
 *  - the cap's span **equals** the crown's for a contiguous run of rows from its very top, which
 *    is what "reaches both shoulders and the ridge instead of sitting inside them" means.
 *
 * The run is what the old fixed rows {0,3,6,12} were sampling, expressed as the thing they were
 * evidence for. The new cap earns it by construction rather than by care: its source clips three
 * snow bands against a copy of `tree_canopy`'s own circles, so the silhouette is the crown's
 * wherever the snow covers full width. Measured on the shipped pixels the run is 94 rows of 114;
 * the floor below is 72 (24 scene units), so a cap re-cut short of the shoulders fails on its
 * first row while an honest redraw has room to breathe.
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
     * corrected in a document — **restated, because v4.21 dissolved its original arithmetic.**
     *
     * The wrong inference was "the cap's canvas is narrower than the crown's, so blitting both at
     * one origin leaves the cap left-aligned; centre it by the width difference". Since v4.21 the
     * two canvases are the same width, so that particular sum is now a no-op and could no longer
     * fail. What it was really evidence for survives and is what is asserted here: **the shared
     * origin is the only place the cap fits.** Displace it by any of the offsets a future
     * "correction" might plausibly pick — the pre-v3.7 preview's own (3,2) units among them — and
     * snow lands in open sky. Three scene units is nine sprite pixels; the artwork is authored 3x.
     *
     * **One direction is deliberately absent, and measuring said so.** Sliding the cap *down* spills
     * nothing: the crown is 198 px tall against the cap's 114, so a downward slide buries snow in
     * foliage rather than pushing it into sky. Asserting it anyway would have meant inventing a
     * number this artwork does not owe. What catches that slide is
     * [theCapRepeatsTheCrownsOwnUpperSilhouette], where a cap no longer starting at the crown's own
     * ridge stops repeating it on its first row, and [bothCallersUseTheCrownsOrigin], which pins the
     * two blits to one offset in the first place. Three tests, three different ways to be wrong.
     */
    @Test
    fun anyDisplacementFromTheCrownsOriginPushesSnowOffIt() {
        val crown = decode(R.drawable.tree_canopy)
        val cap = decode(R.drawable.tree_canopy_snowcap)
        for ((dx, dy) in listOf(9 to 0, -9 to 0, 0 to -9, 9 to 6, -6 to -6)) {
            val (off, total) = capPixelsOffCrown(crown, cap, dx, dy)
            android.util.Log.i("SNOWCAP", "offset ($dx,$dy): $off of $total cap pixels off the crown")
            assertTrue("displacing the cap by ($dx,$dy) should spill snow, spilled $off", off > 100)
        }
    }

    /**
     * Why the two agree: they were authored on one canvas, and since v4.21 the cap is literally
     * cut out of the crown's own silhouette.
     *
     * Two properties, both measured over every row rather than a hand-picked few. The cap may
     * never be wider than the crown anywhere; and from its top down it must be *exactly* as wide,
     * for long enough that "reaches both shoulders and the ridge" is not an accident. Below that
     * run the cap's own scalloped lower edge takes over, which is the drawing and not a defect.
     */
    @Test
    fun theCapRepeatsTheCrownsOwnUpperSilhouette() {
        val crown = decode(R.drawable.tree_canopy)
        val cap = decode(R.drawable.tree_canopy_snowcap)
        // The two share a left edge -- one blit origin, asserted in bothCallersUseTheCrownsOrigin
        // -- so a column index means the same thing in both bitmaps and they can be compared
        // directly. The cap's canvas is the shorter of the two: v4.21 trimmed its right side and
        // its bottom to the drawing, keeping only the leading margin that carries the shared
        // origin. It may never be the wider one, which would mean snow with no crown under it.
        assertTrue(
            "the cap's canvas (${cap.width}) must not exceed the crown's (${crown.width})",
            cap.width <= crown.width,
        )

        var run = -1
        var rowsWithCap = 0
        for (y in 0 until cap.height) {
            val crownSpan = (0 until crown.width).filter { opaque(crown, it, y) }
            val capSpan = (0 until cap.width).filter { opaque(cap, it, y) }
            if (capSpan.isEmpty()) continue
            rowsWithCap++
            assertTrue("cap row $y has snow where the crown has no leaf", crownSpan.isNotEmpty())
            // Subset on every row: the cap can stop short of the crown, never overhang it.
            assertTrue(
                "row $y: cap spans ${capSpan.first()}..${capSpan.last()} beyond the crown's " +
                    "${crownSpan.first()}..${crownSpan.last()}",
                capSpan.first() >= crownSpan.first() && capSpan.last() <= crownSpan.last(),
            )
            val identical = capSpan.first() == crownSpan.first() && capSpan.last() == crownSpan.last()
            if (run == -1 && !identical) run = y
        }
        if (run == -1) run = rowsWithCap
        android.util.Log.i(
            "SNOWCAP",
            "the cap repeats the crown for its first $run rows of $rowsWithCap",
        )
        assertTrue("the cap must carry snow at all, rows measured: $rowsWithCap", rowsWithCap > 30)
        // 72 px = 24 scene units on the 3x authoring grid. A cap cut inside the crown's shoulders
        // diverges on its very first row, so this fails at 0 rather than creeping under a margin.
        assertTrue(
            "the cap only repeats the crown for $run rows; a cap cut inside its shoulders " +
                "leaves a rim of foliage above the snow, which is the v76.1 defect",
            run >= 72,
        )
    }

    /** The offsets the renderer and the preview both read, asserted to be the same one. */
    @Test
    fun bothCallersUseTheCrownsOrigin() {
        assertEquals(TreeSpriteLayout.CANOPY_X, TreeSpriteLayout.SNOWCAP_X, 0f)
        assertEquals(TreeSpriteLayout.CANOPY_Y, TreeSpriteLayout.SNOWCAP_Y, 0f)
    }
}
