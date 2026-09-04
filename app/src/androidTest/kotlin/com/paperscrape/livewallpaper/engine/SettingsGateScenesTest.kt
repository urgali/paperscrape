package com.paperscrape.livewallpaper.engine

import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The frames on which this release's settings can fail, and the gates that stand on them
 * (v4.22 Fase 5, closing `BACKLOG_v4_21.md` item 29 for the traffic and the shops).
 *
 * ### Why these scenes exist
 *
 * Before this class, no committed frame exercised the car count at any value other than ten, the
 * night density at all, or the business hours at all — every golden renders the default
 * customization, which is why "no golden moved" was the honest phase-2 verdict: nothing *could*
 * move. A regression gate needs a frame on which the regression produces pixels. Each scene here
 * is that frame for one setting, and its inputs are derived, not habitual:
 *
 *  - [trafficDaySparse]: the day count at **35%** — the slider position the maintainer's original
 *    report was about, the one the curve was chosen at, and under curve A a count of 4 against
 *    the default's 10, so "the slider does nothing" has six cars' worth of pixels to show.
 *  - [trafficNightQuiet]: day density 1, night density 0, at deep night — the two ends as far
 *    apart as the sliders reach, so "the night density does nothing" is the difference between
 *    one car and ten.
 *  - [shopsClosedNight]: the `desert` theme (the one whose commercial occupancy the
 *    `people-commercial` golden already proves) at deep night, closed 09:00–20:00 — so "the
 *    hours do nothing" is every commercial window lit that should be dark, plus the occupants.
 *
 * ### How each gate was derived
 *
 * Per the rule there are no shortcuts to: the weakest regression that must fail was **measured**
 * on this device ([theGatesStandBetweenFloorAndSignal] logs every number on every run), the noise
 * floor was **measured** on the same rectangles (byte-identical PNGs across separate instrumented
 * runs and a device reboot — 0.0000%, reconfirmed for these scenes in the v4.22 report), and the
 * gate sits midway between the two. The gates live in [SettingsGates] with both numbers written
 * beside them.
 */
class SettingsGateScenesTest {

    companion object {

        /**
         * The road band, derived from the lane geometry (the same derivation the v4.22 Fase 1
         * report carries): the tarmac strip spans the two lanes plus the shoulder margin --
         * `(0.834 - 0.01624) * 800 = 654` down to `(0.862 + 0.01624) * 800 = 703` -- and the
         * band opens upward to clear the tallest vehicle, the 2.9 m fire engine at the far
         * lane's perspective: `2.9 * 15 px/m * 0.937 = 40.8 px` above the far ground line at
         * `0.834 * 800 = 667`, so the top is 626.
         */
        fun roadBand(maxDifferingFraction: Double) = GoldenFocus(
            left = 0, top = 626, right = SceneGolden.WIDTH, bottom = 703,
            label = "road band", maxDifferingFraction = maxDifferingFraction,
        )

        /**
         * The storeys between the roofline and the pavement, where every commercial window in
         * the frame lives -- the same derivation as `PeopleGoldenTest.FACADES`:
         * `int(0.795 * 800) = 636` up 260 px to 376.
         */
        fun facadesBand(maxDifferingFraction: Double) = GoldenFocus(
            left = 0, top = 376, right = SceneGolden.WIDTH, bottom = 636,
            label = "facades band", maxDifferingFraction = maxDifferingFraction,
        )

        fun trafficDaySparse() = GoldenScene(
            name = "traffic-day-sparse",
            dayPhase = GoldenScene.day(),
            warmUpFrames = SharedGoldenScenes.TRAFFIC_WARM_UP_FRAMES,
            customise = { it.copy(cars = it.cars.copy(density = 0.35f), carsNightDensity = 0.35f) },
            focus = listOf(roadBand(SettingsGates.CAR_COUNT_GATE)),
        )

        fun trafficNightQuiet() = GoldenScene(
            name = "traffic-night-quiet",
            dayPhase = GoldenScene.night(),
            warmUpFrames = SharedGoldenScenes.TRAFFIC_WARM_UP_FRAMES,
            customise = { it.copy(carsNightDensity = 0f) },
            focus = listOf(roadBand(SettingsGates.CAR_NIGHT_GATE)),
        )

        fun shopsClosedNight() = GoldenScene(
            name = "shops-closed-night",
            dayPhase = GoldenScene.night(),
            themeId = "desert",
            customise = {
                it.copy(
                    businessHoursEnabled = true,
                    businessOpenHour = DEFAULT_BUSINESS_OPEN_HOUR,
                    businessCloseHour = DEFAULT_BUSINESS_CLOSE_HOUR,
                )
            },
            focus = listOf(facadesBand(SettingsGates.BUSINESS_HOURS_GATE)),
        )

        private const val TAG = "GATEDERIVE"
    }

    // ---------------------------------------------------------------- the goldens

    @Test
    fun trafficDaySparseMatchesItsGolden() = SceneGolden.assertMatches(trafficDaySparse())

    @Test
    fun trafficNightQuietMatchesItsGolden() = SceneGolden.assertMatches(trafficNightQuiet())

    @Test
    fun shopsClosedNightMatchesItsGolden() = SceneGolden.assertMatches(shopsClosedNight())

    // ---------------------------------------------------------------- the derivations, live

    /**
     * Every gate stands between its floor and its weakest regression, measured on every run.
     *
     * Each case renders the gate's own scene and the regression it must catch -- the setting
     * ignored, expressed as the scene with the setting's effect undone -- and asserts the
     * difference on the gate's rectangle exceeds the gate. The floor side is the cross-run
     * byte-identity of the committed PNGs (measured separately; in-process determinism is
     * asserted here as its lower bound). The numbers are logged so any run re-derives them.
     */
    @Test
    fun theGatesStandBetweenFloorAndSignal() {
        data class Case(
            val label: String,
            val base: GoldenScene,
            val regression: GoldenScene,
            val focus: GoldenFocus,
            val gate: Double,
        )
        val cases = listOf(
            Case(
                "car count ignored (35% drawn as 100%)",
                trafficDaySparse(),
                GoldenScene(
                    name = "traffic-day-sparse",
                    dayPhase = GoldenScene.day(),
                    warmUpFrames = SharedGoldenScenes.TRAFFIC_WARM_UP_FRAMES,
                ),
                roadBand(SettingsGates.CAR_COUNT_GATE),
                SettingsGates.CAR_COUNT_GATE,
            ),
            Case(
                "night car density ignored (night drawn with the day count)",
                trafficNightQuiet(),
                GoldenScene(
                    name = "traffic-night-quiet",
                    dayPhase = GoldenScene.night(),
                    warmUpFrames = SharedGoldenScenes.TRAFFIC_WARM_UP_FRAMES,
                ),
                roadBand(SettingsGates.CAR_NIGHT_GATE),
                SettingsGates.CAR_NIGHT_GATE,
            ),
            Case(
                "business hours ignored (closed night drawn open)",
                shopsClosedNight(),
                GoldenScene(
                    name = "shops-closed-night",
                    dayPhase = GoldenScene.night(),
                    themeId = "desert",
                ),
                facadesBand(SettingsGates.BUSINESS_HOURS_GATE),
                SettingsGates.BUSINESS_HOURS_GATE,
            ),
        )
        // Measure and log every case before asserting any, so one failed gate cannot hide the
        // numbers of the others -- the log is the derivation record whatever the verdict.
        data class Measured(val case: Case, val floor: Double, val signal: Double)
        val measured = cases.map { case ->
            val base = SceneGolden.render(case.base)
            val again = SceneGolden.render(case.base)
            val floor = SceneGolden.differingFractionIn(base, again, case.focus)
            again.recycle()
            val mutated = SceneGolden.render(case.regression)
            val signal = SceneGolden.differingFractionIn(base, mutated, case.focus)
            mutated.recycle()
            base.recycle()
            Log.i(TAG, "${case.label}: floor=${"%.4f".format(floor * 100)}% " +
                "signal=${"%.4f".format(signal * 100)}% gate=${"%.4f".format(case.gate * 100)}%")
            Measured(case, floor, signal)
        }
        for ((case, floor, signal) in measured) {
            assertEquals("${case.label}: in-process floor must be exact", 0.0, floor, 0.0)
            assertTrue(
                "${case.label}: the regression (${signal * 100}%) must clear the gate " +
                    "(${case.gate * 100}%)",
                signal > case.gate,
            )
            assertTrue(
                "${case.label}: the gate (${case.gate * 100}%) must sit above the floor",
                case.gate > floor,
            )
        }
    }
}
