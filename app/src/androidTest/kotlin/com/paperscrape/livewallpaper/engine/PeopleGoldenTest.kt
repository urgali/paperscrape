package com.paperscrape.livewallpaper.engine

import org.junit.Test

/**
 * Visual regression for the people system.
 *
 * ### These were `@Ignore`d, and are not any more
 *
 * v4.1 wrote these scenes and parked every assertion behind `@Ignore`, because a golden is an
 * assertion against a *committed PNG* and the only honest way to produce one is to render on a
 * device -- which that session had none of. v4.2 was done with an emulator, so **every PNG in
 * `androidTest/assets/golden/people-*.png` was rendered by this code on that emulator and looked
 * at before being committed.** The class-level note about how to take them still applies to
 * whoever regenerates them next; what has changed is that there is now something to regenerate
 * *from*.
 *
 * ### What the eight scenes pin
 *
 *  - **people-single / people-group** -- that density selects *groups*, so a low setting yields a
 *    lone figure or a small cluster and a high one fills the pavement.
 *  - **people-overlap** -- the reported depth defect, as a picture. `PeopleOcclusionTest` measures
 *    the ordering; this is the frame a human looks at to see that it reads correctly.
 *  - **people-mixed** -- adults and children of both sexes in one frame, which the v4.0 index-locked
 *    table could not produce and which v4.1 produced only on some themes.
 *  - **people-window / people-commercial / people-skyscraper** -- busts at house, shopfront and
 *    tower windows. `people-commercial` is the one v4.1 could not have passed: its theme's
 *    commercial frontage had no occupant call site at all.
 *  - **people-skin** -- that more than one tone reaches one frame. The only one of the eight a
 *    numeric test cannot stand in for, because what it pins is a colour rather than a position.
 *
 * ### The pavement band
 *
 * [PAVEMENT] is the strip the walking figures occupy, derived from the two ground lines
 * [SceneSpace.PAVEMENT_FAR_Y_FRACTION] and [SceneSpace.PAVEMENT_NEAR_Y_FRACTION] at
 * [SceneGolden.HEIGHT], opened upward far enough to contain a standing figure's full height. A
 * whole-frame tolerance is blind to a person: [SceneGolden.MAX_DIFFERING_FRACTION] of a 360x800
 * frame is 576 pixels, and a pedestrian at this frame size covers rather fewer. Measured against
 * this band instead, a figure that moves, vanishes or swaps places with another one fails.
 *
 * ### Regenerating
 *
 * ```
 * ./gradlew :app:installDebug :app:installDebugAndroidTest
 * adb shell am instrument -w \
 *     -e class com.paperscrape.livewallpaper.engine.PeopleGoldenTest \
 *     -e updateGoldens true \
 *     com.paperscrape.livewallpaper.debug.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * The frames are also emitted to logcat under the `GOLDENPNG` tag, base64 in chunks, because the
 * app's external files directory is not readable by the `shell` user -- see [SceneGolden] for why
 * that path exists. **Look at every frame** before committing it: a golden that changed without a
 * reason in the diff is a regression that has just been blessed.
 */
class PeopleGoldenTest {

    private companion object {

        /** The walking band: both pavement rows, plus headroom for a standing adult. */
        val PAVEMENT = GoldenFocus(
            left = 0,
            top = (SceneSpace.PAVEMENT_FAR_Y_FRACTION * SceneGolden.HEIGHT).toInt() - 90,
            right = SceneGolden.WIDTH,
            bottom = (SceneSpace.PAVEMENT_NEAR_Y_FRACTION * SceneGolden.HEIGHT).toInt() + 10,
            label = "pavement",
        )

        /** The band the buildings' windows sit in, above the pavement and below the roofline. */
        val FACADES = GoldenFocus(
            left = 0,
            top = (SceneSpace.PAVEMENT_FAR_Y_FRACTION * SceneGolden.HEIGHT).toInt() - 260,
            right = SceneGolden.WIDTH,
            bottom = (SceneSpace.PAVEMENT_FAR_Y_FRACTION * SceneGolden.HEIGHT).toInt(),
            label = "facades",
        )

        /**
         * The `desert` restaurant's shopfront, and nothing else.
         *
         * Measured off the rendered frame: the pane sits at x 291..302, y 565..578, and the bust
         * behind it covers roughly 70 of this rectangle's 360 pixels. That ratio is the point.
         * [SceneGolden.MAX_FOCUS_DIFFERING_FRACTION] of a rectangle this small is seven pixels, so
         * the occupant vanishing fails by an order of magnitude -- where the same loss measured
         * against the whole facade band, let alone the whole frame, would pass comfortably. This
         * is the assertion v4.1 had no way to make: on every theme its restaurants were empty.
         */
        val RESTAURANT_FRONT = GoldenFocus(
            left = 288, top = 562, right = 306, bottom = 582, label = "restaurant frontage",
        )

        /** The two `city` towers whose window grids hold busts, one rectangle each. */
        val TOWER_LEFT = GoldenFocus(left = 154, top = 506, right = 184, bottom = 532, label = "left tower windows")
        val TOWER_RIGHT = GoldenFocus(left = 218, top = 536, right = 234, bottom = 562, label = "right tower windows")

        fun people(density: Float, themeId: String = "sunset", name: String, focus: List<GoldenFocus> = listOf(PAVEMENT)) =
            GoldenScene(
                name = name,
                dayPhase = GoldenScene.day(),
                themeId = themeId,
                customise = { base ->
                    base.copy(
                        people = base.people.copy(visible = true, density = density),
                        peopleNightDensity = density,
                    )
                },
                focus = focus,
            )
    }

    /** A thinned street. Density this low selects a single group. */
    @Test
    fun `people-single`() = SceneGolden.assertMatches(people(0.2f, name = "people-single"))

    /**
     * A full street, on the same theme as `people-single` — **and it is the `day` frame.**
     *
     * ### Why this asserts `day.png` rather than a `people-group.png`
     *
     * There was a `people-group` golden until v4.21, built as `people(1f, …)`. That block is a
     * **no-op at density 1**: `SceneCustomization.DEFAULT` already carries `people(visible = true,
     * density = 1f)` and `DEFAULT_PEOPLE_NIGHT_DENSITY = 1f`, and `sunset` overrides neither. So it
     * described `SharedGoldenScenes.day()`'s inputs exactly and its PNG came out byte-identical to
     * `day.png` — **measured across all forty `SceneCustomization` fields, exactly two differed
     * between it and `people-single`, both of them the people density.**
     *
     * A second PNG of one scene has no way to fail that the first does not already have, so the
     * PNG went and this assertion moved onto `day.png`, carrying [PAVEMENT] as an extra focus at
     * the assertion site. `SharedGoldenScenes.day()` is untouched: the GL suite iterates
     * `scene.focus`, so adding a rectangle there would have changed what `GlSceneGoldenTest`
     * measures. See `SceneGolden.assertMatches`'s own note and `GoldenUniquenessTest`.
     *
     * ### What the pair with `people-single` is, and what it is not
     *
     * It is two frames of one theme differing **only** in the people density — that much is
     * measured, field by field. Their committed PNGs genuinely differ, so the setting demonstrably
     * changes the picture, and the two frames side by side are what a human looks at to see how.
     *
     * **It is not, at today's tolerances, an assertion that the density still works**, and the
     * previous wording here — "the difference between them *is* what the setting does" — claimed
     * more than the gates deliver. Measured on this device, rendering `people-single` as if the
     * density were ignored entirely (0.2 drawn as 1) moves **279 px: 0.711% of the pavement band
     * against a 2% limit, and 0.097% of the frame against a 0.2% limit. Both gates pass.** Hiding
     * *every* pedestrian moves 390 px on this frame and 111 px on `people-single`'s, and passes
     * too. The pavement focus is a guard against a figure being drawn in the wrong *place*, which
     * moves two rectangles' worth of pixels; it is not a guard against there being fewer of them.
     *
     * That gap is older than this change and is unaffected by it — it is the same on either PNG —
     * so it is recorded as item 29 of `BACKLOG_v4_21.md` rather than closed here by moving a
     * tolerance, which would be tuning a metric to reach a verdict.
     */
    @Test
    fun `people-at-full-density`() =
        SceneGolden.assertMatches(SharedGoldenScenes.day(), extraFocus = listOf(PAVEMENT))

    /**
     * More than one skin tone in one frame.
     *
     * `beach` is chosen deliberately: it is the theme the defect was reported from, and the theme
     * whose six pedestrians were, under v4.1, five girls and a boy on tone 2 with no adult of
     * either sex. This frame is what that street looks like now.
     */
    @Test
    fun `people-skin`() =
        SceneGolden.assertMatches(people(1f, themeId = "beach", name = "people-skin"))

    /**
     * Ages and sexes mixed in one frame.
     *
     * A different theme from `people-skin`, and therefore a different seed and a different street:
     * two frames drawn by one piece of code is the claim about variety that no single frame can
     * make. `winter` carries the evenest mix the catalogue produces -- three men, two women, two
     * boys and two girls.
     *
     * **This frame is also the winter theme's frame, and since v4.21 it is the only one.**
     * `SceneGoldenTest.themeWinter` described these same inputs — the `customise` block below is a
     * no-op at density 1, because `winter`'s defaults already carry `people(visible = true,
     * density = 1f)` — so its PNG came out byte-identical to this one and its whole-frame assertion
     * could not fail on its own. It was removed; this one keeps the pixels, adds [PAVEMENT], and
     * inherits its claim: **winter dressing on the whole frame**, coats and hats and settled snow,
     * asserted here rather than under a second name.
     */
    @Test
    fun `people-mixed`() =
        SceneGolden.assertMatches(people(1f, themeId = "winter", name = "people-mixed"))

    /**
     * Overlap and depth, as a picture.
     *
     * `new_year` at 80% is the same scene `PeopleOcclusionTest` measures: the three figures
     * arriving between 40% and 80% all stand nearer than the three already there, and they cover
     * pixels those three had painted. This is that frame, so the numeric claim and the picture are
     * about the same street rather than two different ones.
     */
    @Test
    fun `people-overlap`() =
        SceneGolden.assertMatches(people(0.8f, themeId = "new_year", name = "people-overlap"))

    /**
     * Busts at house windows, measured on the facades rather than the whole frame.
     *
     * On `spring` rather than `sunset`, which is what `people-group` already draws: two goldens of
     * one frame under two names would double the maintenance and halve the coverage, and the first
     * version of this file did exactly that -- the two PNGs came out byte-identical.
     */
    @Test
    fun `people-window`() = SceneGolden.assertMatches(
        GoldenScene(
            name = "people-window",
            dayPhase = GoldenScene.day(),
            themeId = "spring",
            focus = listOf(FACADES),
        ),
    )

    /**
     * Somebody behind commercial glass -- the frame v4.1 could not have produced.
     *
     * `desert`'s restaurant at tile fraction 0.433 holds a woman on tone 0, and the focus is her
     * pane rather than the facade band, because a golden about one bust has to be measured over
     * the bust. Against v4.1 this rectangle contains an empty window.
     *
     * **This frame is also the desert theme's frame, and since v4.21 it is the only one.** This
     * scene never used the [people] helper — it is the theme's own defaults at `day()` — which is
     * exactly what `SceneGoldenTest.themeDesert` was, so the two PNGs were byte-identical and the
     * second assertion carried nothing the first did not. It was removed; this one keeps the pixels,
     * keeps [RESTAURANT_FRONT], and inherits its claim: **the desert palette on the whole frame**,
     * palms and sand and no grass, asserted here rather than under a second name.
     */
    @Test
    fun `people-commercial`() = SceneGolden.assertMatches(
        GoldenScene(
            name = "people-commercial",
            dayPhase = GoldenScene.day(),
            themeId = "desert",
            focus = listOf(RESTAURANT_FRONT),
        ),
    )

    /** Busts in a tower's window grid, at a deliberately sparse rate: four faces over two towers. */
    @Test
    fun `people-skyscraper`() = SceneGolden.assertMatches(
        GoldenScene(
            name = "people-skyscraper",
            dayPhase = GoldenScene.day(),
            themeId = "city",
            focus = listOf(TOWER_LEFT, TOWER_RIGHT),
        ),
    )
}
