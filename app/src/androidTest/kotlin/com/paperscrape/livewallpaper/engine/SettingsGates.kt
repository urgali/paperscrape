package com.paperscrape.livewallpaper.engine

/**
 * The derived gate values of v4.22 Fase 5, each between its two measured numbers.
 *
 * Every value here follows one rule and none is tunable in place: **gate = midway between the
 * measured noise floor and the measured weakest regression that must fail**, both written beside
 * it. The floor for every rectangle is 0.0000% — PNGs from separate instrumented runs of this
 * device, a process restart and a device reboot apart, are byte-identical (v4.22 Fase 1,
 * reconfirmed on these scenes in the v4.22 report) — so each gate is half its regression's
 * signal. If a renderer change legitimately moves one of these frames, the golden is regenerated
 * through the normal attribution rule; the gate itself only changes if its *derivation inputs*
 * are re-measured, and `SettingsGateScenesTest.theGatesStandBetweenFloorAndSignal` re-measures
 * the signal side on every run so the margin cannot silently rot.
 *
 * A gate placed to make a verdict come out is the move this project has spent three passes
 * refusing; these are placed by the two numbers and nothing else.
 */
object SettingsGates {

    /**
     * People density on `people-single`'s pavement band. MISURATO (device, this build): weakest
     * regression that must fail = every pedestrian hidden = 0.283% of the band; the ignored
     * density moves 0.711%. Floor 0.0000% → gate at half the weakest signal.
     */
    const val PEOPLE_DENSITY_GATE = 0.00283 / 2

    /**
     * Day car count on `traffic-day-sparse`'s road band. MISURATO (OnePlus 6T, this build,
     * `GATEDERIVE` 2026-09-04): the count ignored (35% drawn as 100%) moves **7.6082%** of the
     * band; floor 0.0000% → gate at half the signal, 3.8041%.
     */
    const val CAR_COUNT_GATE = 0.076082 / 2

    /**
     * Night car density on `traffic-night-quiet`'s road band. MISURATO (same run): the night
     * density ignored (night drawn with the day count) moves **14.0115%**; floor 0.0000% →
     * gate 7.0058%.
     */
    const val CAR_NIGHT_GATE = 0.140115 / 2

    /**
     * Business hours on `shops-closed-night`'s facades band. MISURATO (same run): the hours
     * ignored (a closed night drawn open) move **2.0075%**; floor 0.0000% → gate 1.0038%.
     * Notably *below* the shared 2% focus limit — the derived gate is what makes this
     * regression catchable at all.
     */
    const val BUSINESS_HOURS_GATE = 0.020075 / 2
}
