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
 * The driver gap is characterised, and this is what keeps it that way.
 *
 * The three GL goldens are authored on the emulator's reference driver. On the OnePlus 6T's Adreno
 * 630 the same build renders the same scene with its edges in slightly different places -- 1.18 /
 * 1.07 / 0.92% of the outline when [GlGolden.EdgeDisplacement] was derived, 1.2-1.4% when v4.19
 * re-measured it, against a 3% gate. It passes on both environments and it is not a test failure on
 * either.
 *
 * That is item 1 of `BACKLOG_v4_19.md`, and v4.20 closes it **as a decision, not as a fix**: the
 * two ways to actually remove it are per-driver golden sets, which double the maintenance and make
 * "the golden" ambiguous, or a shader change that takes away the driver's freedom at an edge. Both
 * cost more than a gap that is comfortably under the gate.
 *
 * What was missing was not a fix but a *number nobody was watching*. The gap has been re-measured
 * by hand three times across three releases because nothing recorded it. This test records it, on
 * whatever driver it runs on, and fails if it grows past
 * [GlGolden.EdgeDisplacement.CHARACTERISED_MAX_DISPLACED_FRACTION] -- while the real gate still has
 * a third of its headroom left, so there is time to look before a golden run starts failing.
 *
 * On the emulator it measures ~0, which is correct and not a reason to skip it: a run that reported
 * nothing would be indistinguishable from a run that did not happen.
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
                    "where the golden itself starts failing). Measured this run: $report. This is " +
                    "not necessarily a regression -- look at whether the driver changed before " +
                    "assuming the scene did.",
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
