package com.paperscrape.livewallpaper.engine

import org.junit.Ignore
import org.junit.Test

/**
 * Visual regression for the people system.
 *
 * ### Why every test here is `@Ignore`d, and what it would take to enable them
 *
 * A golden is an assertion against a *committed PNG*, and the only way to produce that PNG is to
 * render the scene on a device — [SceneGolden] says why at length: the frame has to come from the
 * shipped `CanvasSceneTarget` writing into a real `android.graphics.Bitmap`, because a golden
 * produced by a second, JVM-only drawing path would prove nothing about the drawing code that
 * ships.
 *
 * The v4.1 work was done in an environment with no emulator and no `/dev/kvm`, so **no frame of
 * this release was ever rendered.** Committing these tests without their PNGs would leave a red
 * suite; committing PNGs produced any other way would be committing a fiction. So the scenes are
 * written down — which is the part that needs judgement and review — and the assertions are parked
 * behind `@Ignore` until somebody with a device can take the pictures.
 *
 * **To enable them**, on a device or emulator:
 *
 * ```
 * ./gradlew :app:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=\
 * com.paperscrape.livewallpaper.engine.PeopleGoldenTest \
 *     -Pandroid.testInstrumentationRunnerArguments.updateGoldens=true
 * ```
 *
 * then pull `golden-output/` from the device's external files dir, **look at every frame** — that
 * is the whole point, and `SceneGolden`'s own note is blunt about it: a golden that changed
 * without a reason in the diff is a regression that has just been blessed — commit the PNGs to
 * `app/src/androidTest/assets/golden/`, and delete the `@Ignore` annotations.
 *
 * ### Why these scenes
 *
 * Each one pins a claim v4.1 makes that a unit test can only make about numbers:
 *
 *  - **people-single / people-group** — that density selects *groups*, so a low setting yields a
 *    lone figure and a high one yields clusters, rather than the four evenly-spaced walkers v4.0
 *    always drew.
 *  - **people-overlap** — the reported defect, as a picture. The focus band is the pavement, so
 *    the assertion is about the figures rather than about the sky above them.
 *  - **people-mixed** — that a frame contains adults and children of both sexes at once, which is
 *    exactly what the old index-locked table could not produce.
 *  - **people-window / people-commercial / people-skyscraper** — that busts appear at house,
 *    shopfront and tower windows, and that the windows themselves are unchanged behind them.
 *  - **people-skin** — that more than one skin tone reaches the street in a single frame. Added
 *    by the skin batch; it is the only one of these a numeric test cannot stand in for, because
 *    what it pins is a colour rather than a position.
 *
 * ### The pavement band
 *
 * [PAVEMENT] is the strip the walking figures occupy, derived from the two ground lines
 * [SceneSpace.PAVEMENT_FAR_Y_FRACTION] and [SceneSpace.PAVEMENT_NEAR_Y_FRACTION] at
 * [SceneGolden.HEIGHT], opened upward far enough to contain a standing figure's full height. A
 * whole-frame tolerance is blind to a person: [SceneGolden.MAX_DIFFERING_FRACTION] of a 360x800
 * frame is 576 pixels, and a pedestrian at this scale covers rather fewer. Measured against this
 * band instead, a figure that moves, vanishes or swaps places with another one fails.
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

        fun people(density: Float, themeId: String = "sunset", name: String) = GoldenScene(
            name = name,
            dayPhase = GoldenScene.day(),
            themeId = themeId,
            customise = { base ->
                base.copy(
                    people = base.people.copy(visible = true, density = density),
                    peopleNightDensity = density,
                )
            },
            focus = listOf(PAVEMENT),
        )
    }

    /** A thinned street. Density this low selects a single group. */
    @Ignore("No golden PNG committed -- see the class comment for how to take it.")
    @Test
    fun `people-single`() = SceneGolden.assertMatches(people(0.2f, name = "people-single"))

    /** A full street: every group slot present, so clusters of two and three appear. */
    @Ignore("No golden PNG committed -- see the class comment for how to take it.")
    @Test
    fun `people-group`() = SceneGolden.assertMatches(people(1f, name = "people-group"))

    /**
     * Two skin tones in one frame, which is the claim the v4.1 skin batch adds.
     *
     * The seed is the theme id's hash, so a theme fixes the street; `beach` at full density is
     * chosen because its population spans more than one tone -- `SkinToneTest` proves tones vary
     * across seeds, and this is the frame where that becomes visible rather than statistical.
     * The pavement focus is what makes it measurable: a tone change repaints the skin of every
     * figure in the band, far past the focus tolerance, while leaving clothes and outlines alone.
     */
    @Ignore("No golden PNG committed -- see the class comment for how to take it.")
    @Test
    fun `people-skin`() =
        SceneGolden.assertMatches(people(1f, themeId = "beach", name = "people-skin"))

    /**
     * Ages and sexes mixed in one frame.
     *
     * Distinguished from `people-group` by its theme, and therefore by its seed: the two frames
     * draw different streets from the same code, which is the claim about variety that a single
     * frame cannot make on its own.
     */
    @Ignore("No golden PNG committed -- see the class comment for how to take it.")
    @Test
    fun `people-mixed`() =
        SceneGolden.assertMatches(people(1f, themeId = "beach", name = "people-mixed"))

    /**
     * Overlap and depth, as a picture.
     *
     * At full density the street carries enough figures that some of them cross, and the pavement
     * focus is where that shows. This is the frame that would have caught the reported defect.
     */
    @Ignore("No golden PNG committed -- see the class comment for how to take it.")
    @Test
    fun `people-overlap`() =
        SceneGolden.assertMatches(people(1f, themeId = "city", name = "people-overlap"))

    /** Busts at house windows, measured on the facades rather than the whole frame. */
    @Ignore("No golden PNG committed -- see the class comment for how to take it.")
    @Test
    fun `people-window`() = SceneGolden.assertMatches(
        GoldenScene(
            name = "people-window",
            dayPhase = GoldenScene.day(),
            themeId = "sunset",
            focus = listOf(FACADES),
        ),
    )

    /** Busts at shopfront windows — a building kind that could not hold anybody before v4.1. */
    @Ignore("No golden PNG committed -- see the class comment for how to take it.")
    @Test
    fun `people-commercial`() = SceneGolden.assertMatches(
        GoldenScene(
            name = "people-commercial",
            dayPhase = GoldenScene.day(),
            themeId = "beach",
            focus = listOf(FACADES),
        ),
    )

    /** Busts in a tower's window grid — likewise new in v4.1, at a deliberately sparse rate. */
    @Ignore("No golden PNG committed -- see the class comment for how to take it.")
    @Test
    fun `people-skyscraper`() = SceneGolden.assertMatches(
        GoldenScene(
            name = "people-skyscraper",
            dayPhase = GoldenScene.day(),
            themeId = "city",
            focus = listOf(FACADES),
        ),
    )
}
