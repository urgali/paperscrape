package com.paperscrape.livewallpaper.engine

import org.junit.Test

/**
 * The frames on which the traffic and opening-hours settings can fail.
 *
 * ### Why these scenes exist
 *
 * The other goldens render the default customization, so none of them exercises the car count at
 * any value but ten, the night density or the business hours: a setting that stopped working would
 * move none of them. A regression check needs a frame on which the regression produces pixels. Each
 * scene here is that frame for one setting, and its inputs are derived, not habitual:
 *
 *  - [trafficDaySparse]: the day count at **35%**, a count of 4 against the default's 10
 *    ([CarSelection.countFor]), so "the slider does nothing" has six cars' worth of pixels to show.
 *  - [trafficNightQuiet]: day density 1, night density 0, at deep night — the two ends as far
 *    apart as the sliders reach, so "the night density does nothing" is the difference between
 *    one car and ten.
 *  - [shopsClosedNight]: the `desert` theme (the one whose commercial occupancy the
 *    `people-commercial` golden already proves) at deep night, closed 09:00–20:00 — so "the
 *    hours do nothing" is every commercial window lit that should be dark, plus the occupants.
 *
 * ### What fails, and on what
 *
 * Each frame is compared whole against its committed PNG, and the whole frame admits no differing
 * pixel ([SceneGolden.MAX_DIFFERING_FRACTION]). A setting that stopped working draws its frame as
 * the default would: cars, lit windows and occupants that the committed frame does not have. The
 * road band and the facades band are kept as each scene's [GoldenScene.focus]: they name what the
 * frame is about, and a failing frame says how much of the change fell inside them.
 */
class SettingsGateScenesTest {

    companion object {

        /**
         * The road band, derived from the lane geometry: the tarmac strip spans the two lanes plus
         * the shoulder margin --
         * `(0.834 - 0.01624) * 800 = 654` down to `(0.862 + 0.01624) * 800 = 703` -- and the
         * band opens upward to clear the tallest vehicle, the 2.9 m fire engine at the far
         * lane's perspective: `2.9 * 15 px/m * 0.937 = 40.8 px` above the far ground line at
         * `0.834 * 800 = 667`, so the top is 626.
         */
        fun roadBand() = GoldenFocus(
            left = 0, top = 626, right = SceneGolden.WIDTH, bottom = 703,
            label = "road band",
        )

        /**
         * The storeys between the roofline and the pavement, where every commercial window in
         * the frame lives -- the same derivation as `PeopleGoldenTest.FACADES`:
         * `int(0.795 * 800) = 636` up 260 px to 376.
         */
        fun facadesBand() = GoldenFocus(
            left = 0, top = 376, right = SceneGolden.WIDTH, bottom = 636,
            label = "facades band",
        )

        fun trafficDaySparse() = GoldenScene(
            name = "traffic-day-sparse",
            dayPhase = GoldenScene.day(),
            warmUpFrames = SharedGoldenScenes.TRAFFIC_WARM_UP_FRAMES,
            customise = { it.copy(cars = it.cars.copy(density = 0.35f), carsNightDensity = 0.35f) },
            focus = listOf(roadBand()),
        )

        fun trafficNightQuiet() = GoldenScene(
            name = "traffic-night-quiet",
            dayPhase = GoldenScene.night(),
            warmUpFrames = SharedGoldenScenes.TRAFFIC_WARM_UP_FRAMES,
            customise = { it.copy(carsNightDensity = 0f) },
            focus = listOf(roadBand()),
        )

        fun shopsClosedNight() = GoldenScene(
            name = "shops-closed-night",
            dayPhase = GoldenScene.night(),
            themeId = "desert",
            customise = {
                // Both groups of hours on at their default hours, the shops' and the towers', so every
                // commercial window of the frame keeps hours.
                it.copy(
                    shopHoursEnabled = true,
                    shopOpenHour = DEFAULT_BUSINESS_OPEN_HOUR,
                    shopCloseHour = DEFAULT_BUSINESS_CLOSE_HOUR,
                    towerHoursEnabled = true,
                    towerOpenHour = DEFAULT_BUSINESS_OPEN_HOUR,
                    towerCloseHour = DEFAULT_BUSINESS_CLOSE_HOUR,
                )
            },
            focus = listOf(facadesBand()),
        )
    }

    // ---------------------------------------------------------------- the goldens

    @Test
    fun trafficDaySparseMatchesItsGolden() = SceneGolden.assertMatches(trafficDaySparse())

    @Test
    fun trafficNightQuietMatchesItsGolden() = SceneGolden.assertMatches(trafficNightQuiet())

    @Test
    fun shopsClosedNightMatchesItsGolden() = SceneGolden.assertMatches(shopsClosedNight())
}
