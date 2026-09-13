package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The driver gap was characterised, and this is what used to keep it that way.
 *
 * ## What this test measures, and what it reads
 *
 * It measures the edge displacement between the driver that is **running** and the references that
 * are **committed**. That was a real cross-driver reading for as long as those were two different
 * drivers. **They have been the same driver since v4.26, and it reads 0.00%.**
 *
 * **This KDoc used to say the references were authored on "the OnePlus 6T's Adreno 630 since
 * v4.21".** That stopped being true in v4.26 and nobody updated the sentence, so it stood for six
 * releases and was still being quoted as the reason not to re-author them. It is corrected here,
 * with what the files actually are.
 *
 * ## Where the references have actually been authored
 *
 * | authored on | when | what forced it |
 * |---|---|---|
 * | the emulator's reference software GL | until v4.21 | — |
 * | OnePlus 6T, Adreno 630 | v4.21 | the tree redraw moved all three by 8.8% of outline |
 * | **Blackview BV6600, PowerVR Rogue GE8320** | **v4.26** | the sky and water redraw; the three read 6.31 / 9.51 / 3.192% against 3.00 / 3.00 / 0.500%. `BACKLOG_v4_26.md` item 71, ratified by the maintainer in v4.27 |
 * | the same BV6600 (`gl-day` only) | v4.28 | the bird redrawn through the glow region |
 * | **the same BV6600, all three** | **v5.0, 2026-09-13** | the neighbourhood redrawn from scratch; the three read **21.21 / 19.49 / 21.64%** against a 3% gate |
 *
 * ## Where the historical cross-driver numbers came from, since this is the only place they survive
 *
 * The same build rendered the same scene with its edges in slightly different places on the other
 * driver. All of these are against a 3% gate, and none was ever a test failure on either
 * environment:
 *
 * | measured | on | when |
 * |---|---|---|
 * | 1.18 / 1.07 / 0.92% | OnePlus 6T, Adreno 630, against emulator-captured references | when [GlGolden.EdgeDisplacement] was derived; carried in v4.18's release notes (prepared 2026-09-01) |
 * | 1.2-1.4% | OnePlus 6T, Adreno 630 | v4.19's re-measurement (prepared 2026-09-03) |
 * | **0.00 / 0.00 / 0.00%** | BV6600, PowerVR Rogue GE8320, Android 10 | v5.0 (2026-09-13) |
 *
 * ## Why it reads zero, and for how long
 *
 * **The cross-driver reading was given up in v4.26, not here.** The OnePlus 6T is gone; the
 * committed file and the running driver have been the same machine for six releases, so this test
 * has been measuring a difference with no source since then. `BACKLOG_v4_27.md` item 78 says so in
 * as many words, and it stays open: it is a condition, and only a second GPU vendor closes it.
 *
 * **It will keep reading ~0 until such a device exists, and none is coming from here.** The
 * maintainer's personal phone is where an update is proved, not a test device. A reading other than
 * ~0 on this device is therefore not a driver gap at all — it is the scene having moved, and the
 * reference itself will be failing beside it. That is what v5.0's 21.21 / 19.49 / 21.64% was.
 *
 * **The test is kept rather than deleted**, unchanged and with its 2% limit untouched, for two
 * reasons: a run that reported nothing would be indistinguishable from a run that did not happen,
 * and on the day a second driver does appear this is the number that says whether it sits inside
 * the band the Adreno one did.
 *
 * ## The decision this records
 *
 * That is item 1 of `BACKLOG_v4_19.md`, and v4.20 closed it **as a decision, not as a fix**: the
 * two ways to actually remove the gap are per-driver reference sets, which double the maintenance
 * and make "the reference" ambiguous, or a shader change that takes away the driver's freedom at an
 * edge. Both cost more than a gap that is comfortably under the gate.
 *
 * **Item 56 of `BACKLOG_v5_0.md` closes in the same form**, and with a correction: it was held open
 * for three releases to protect a cross-driver measurement that had already been given up in v4.26.
 * See [GlGolden.EdgeDisplacement]'s v5.0 section.
 */
@RunWith(AndroidJUnit4::class)
class GlDriverGapGuardTest {

    @Test
    fun theDriverGapIsStillTheOneThatWasCharacterised() {
        val limit = GlGolden.EdgeDisplacement.CHARACTERISED_MAX_DISPLACED_FRACTION
        val measured = LinkedHashMap<String, Double>()
        for (scene in listOf(SharedGoldenScenes.day(), SharedGoldenScenes.lakeBusy(), SharedGoldenScenes.thunderstorm())) {
            val golden = committedGolden("gl-${scene.name}")
            val result = GlGolden.render(scene)
            try {
                measured[scene.name] = GlGolden.edgeDisplacement(golden, result.bitmap)
            } finally {
                result.bitmap.recycle()
            }
            golden.recycle()
        }
        val report = measured.entries.joinToString { "${it.key} ${"%.2f".format(it.value * 100)}%" }
        Log.i("GLDRIVERGAP", "edge displacement against the committed goldens: $report")
        for ((name, displaced) in measured) {
            assertTrue(
                "the driver gap on '$name' has grown to ${"%.2f".format(displaced * 100)}% of the " +
                    "outline, past the ${"%.0f".format(limit * 100)}% it was characterised within " +
                    "(${"%.0f".format(GlGolden.EdgeDisplacement.MAX_DISPLACED_FRACTION * 100)}% is " +
                    "where the golden itself starts failing). Measured this run: $report. Since " +
                    "v5.0 the goldens are authored on the driver this runs on, so the expected " +
                    "reading is ~0 and there is no second driver for this to be: unless you are " +
                    "on a device the goldens were not authored on, the scene moved.",
                displaced <= limit,
            )
        }
    }

    private fun committedGolden(name: String): Bitmap {
        val context = InstrumentationRegistry.getInstrumentation().context
        return context.assets.open("golden/$name.png").use { BitmapFactory.decodeStream(it) }
            .copy(Bitmap.Config.ARGB_8888, true)
    }
}
