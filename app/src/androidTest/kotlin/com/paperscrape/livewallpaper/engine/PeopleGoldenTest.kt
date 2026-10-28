package com.paperscrape.livewallpaper.engine

import org.junit.Test

/**
 * Visual regression for the people system: seven frames, each against its committed PNG under
 * `androidTest/assets/golden`, rendered on a device and looked at before it was committed.
 *
 * ### What the seven scenes pin
 *
 *  - **people-single** -- that density selects *groups*, so a low setting yields a lone figure or a
 *    small cluster. The full pavement of the same theme, at density 1, is the `day` frame
 *    (`SceneGoldenTest.day`): `sunset`'s defaults draw it, so a scene of its own here would be a
 *    second PNG of one scene, and a second assertion of `day.png` fails and passes with the first.
 *  - **people-overlap** -- the depth order of the walkers, as a picture. `PeopleOcclusionTest`
 *    measures the ordering; this is the frame a human looks at to see that it reads correctly.
 *  - **people-mixed** -- adults and children of both sexes in one frame.
 *  - **people-window / people-commercial / people-skyscraper** -- busts at house, shopfront and
 *    tower windows.
 *  - **people-skin** -- that more than one tone reaches one frame. The only one of the seven a
 *    numeric test cannot stand in for, because what it pins is a colour rather than a position.
 *
 * ### The pavement band
 *
 * [PAVEMENT] is the strip the walking figures occupy, derived from the two ground lines
 * [SceneSpace.PAVEMENT_FAR_Y_FRACTION] and [SceneSpace.PAVEMENT_NEAR_Y_FRACTION] at
 * [SceneGolden.HEIGHT], opened upward far enough to contain a standing figure's full height. What
 * fails a figure that moves, vanishes or swaps places with another is the whole frame, which admits
 * no differing pixel ([SceneGolden.MAX_DIFFERING_FRACTION]); the band names the people in a frame
 * that also contains a sky, a road and a lake, and a failing frame says how much of its change fell
 * inside it.
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
         * The `desert` restaurant's shopfront, and nothing else: the pane and the bust behind it,
         * measured off the rendered frame. The occupant vanishing moves pixels the whole frame,
         * admitting none ([SceneGolden.MAX_DIFFERING_FRACTION]), fails on; this rectangle names the
         * pane the golden is about, and a failure says how much of the change fell inside it.
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

    /**
     * A thinned street. Density this low selects a single group.
     *
     * The density setting is held by this frame against its committed PNG, whole: the density
     * ignored (0.2 drawn as 1) or every pedestrian hidden moves pixels of it, and the frame admits
     * no differing pixel ([SceneGolden.MAX_DIFFERING_FRACTION]). The same theme at density 1 is the
     * `day` frame (`SceneGoldenTest.day`), so the setting is seen changing the picture between two
     * committed PNGs of one theme that differ in the people density.
     */
    @Test
    fun `people-single`() = SceneGolden.assertMatches(people(0.2f, name = "people-single"))

    /**
     * More than one skin tone in one frame, on `beach`'s full street: a deal that put every
     * pedestrian on one tone changes this frame.
     */
    @Test
    fun `people-skin`() =
        SceneGolden.assertMatches(people(1f, themeId = "beach", name = "people-skin"))

    /**
     * Ages and sexes mixed in one frame.
     *
     * A different theme from `people-skin`, and therefore a different seed and a different street:
     * two frames drawn by one piece of code is the claim about variety that no single frame can
     * make. `winter`'s street mixes men, women, boys and girls.
     *
     * **This frame is also the winter theme's frame, and the only one.** At density 1 by day the
     * `customise` block of [people] draws what `winter`'s defaults draw, the people visible at
     * density 1, so a theme golden of `winter` at `day()` would be this PNG under a second name.
     * This one carries that claim: **winter dressing on the whole frame**, coats and hats and
     * settled snow.
     */
    @Test
    fun `people-mixed`() =
        SceneGolden.assertMatches(people(1f, themeId = "winter", name = "people-mixed"))

    /**
     * Overlap and depth, as a picture.
     *
     * `new_year` at 80% is the same scene `PeopleOcclusionTest` measures: the figures arriving
     * between 40% and 80% all stand nearer than those already there, and they cover pixels those
     * had painted. This is that frame, so the numeric claim and the picture are about the same
     * street rather than two different ones.
     */
    @Test
    fun `people-overlap`() =
        SceneGolden.assertMatches(people(0.8f, themeId = "new_year", name = "people-overlap"))

    /**
     * Busts at house windows, measured on the facades rather than the whole frame.
     *
     * On `spring` rather than `sunset`, which is what `day` already draws: two goldens of one frame
     * under two names would double the maintenance and halve the coverage.
     *
     * **This golden draws the meadow's flowers in bloom**, though nothing in this file names them:
     * `spring` is the one theme whose defaults carry `flowersEnabled = true`
     * (`SceneCustomization.defaultCustomizationFor`), and `SceneGolden.render` builds its scene from
     * `defaultCustomizationFor(themeId)`. Whether a golden draws something is asked of its theme's
     * defaults, not of a grep for the flag's name in `androidTest`.
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
     * Somebody behind commercial glass.
     *
     * `desert`'s restaurant has somebody behind its glass, and the focus is that pane rather than
     * the facade band, because a golden about one bust has to be measured over the bust. A
     * restaurant drawn with an empty window fails it.
     *
     * **This frame is also the desert theme's frame, and the only one.** This scene does not use
     * the [people] helper -- it is the theme's own defaults at `day()` -- so a theme golden of
     * `desert` would be this PNG under a second name. This one carries that claim: **the desert
     * palette on the whole frame**, palms and sand and no grass.
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

    /** Busts in the window grids of two `city` towers, at a deliberately sparse rate. */
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
