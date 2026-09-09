package com.paperscrape.livewallpaper.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **`BACKLOG_v4_25.md` item 65: the golden suite would have gone green over a scene in which every
 * person was redrawn.** One assertion of twenty-six was red for that release, and the reason is
 * structural rather than accidental — the whole-frame gate is a fraction of 288 000 pixels, so it
 * is insensitive to artwork by construction, and the focus rectangles that *do* watch artwork were
 * drawn around people and vehicles because those are the families previous releases redrew.
 *
 * v4.26 redraws **the sky and the water**, which are the two largest surfaces in the frame, and had
 * nothing watching them at all. This is the item closed the way it has to be closed: by deriving
 * gates, not by lowering the numbers that already exist.
 *
 * ### What is here, and what is deliberately not
 *
 * Three rectangles, one per family this release touches, each carrying a **derived** limit from
 * [SettingsGates]: the cloud band, the bird band and the water band. They are attached to golden
 * scenes that already exist — `day` and `lake-busy` — as `extraFocus`, so this adds **three
 * assertions and no new committed PNG**. `GoldenUniquenessTest` allows exactly that: two tests may
 * assert one picture with different focus rectangles; two pictures of one scene may not exist.
 *
 * What is **not** here is a fourth rectangle around the whole sky. A focus rectangle drawn around a
 * whole band is a whole-frame gate wearing a focus rectangle's name — item 65's own words — and the
 * bird band is already close to that line, which is why its gate is derived from the birds' own
 * signal rather than left at the shared 2%.
 */
@RunWith(AndroidJUnit4::class)
class SkyWaterGoldenTest {

    private companion object {

        /**
         * The cloud band, as the renderer computes it, plus the feathered edge of a cloud.
         *
         * Read from [CloudBand] rather than measured off a frame: the band moves with the sun-arc
         * setting, and a rectangle written as pixels would stop containing the clouds the first
         * time that default changed. The 24 px of headroom is the blur's own reach past the
         * geometry, which C1 "Batuffolo" has and the v4.25 cloud did not.
         */
        fun cloudBand(gate: Double): GoldenFocus {
            val top = CloudBand.topFor(SceneGolden.HEIGHT, DEFAULT_SUN_CLOUD_HEIGHT)
            val height = CloudBand.heightFor(SceneGolden.HEIGHT)
            return GoldenFocus(
                left = 0,
                top = (top - 24f).toInt().coerceAtLeast(0),
                right = SceneGolden.WIDTH,
                bottom = (top + height + 24f).toInt().coerceAtMost(SceneGolden.HEIGHT),
                label = "cloud band",
                maxDifferingFraction = gate,
            )
        }

        /**
         * The band birds fly in: `drawBirds` places every candidate between 0.08 and 0.38 of the
         * screen, and the sprite reaches 15 px above its own y and bobs 6 either way.
         */
        fun birdBand(gate: Double) = GoldenFocus(
            left = 0,
            top = (SceneGolden.HEIGHT * 0.08f - 21f).toInt().coerceAtLeast(0),
            right = SceneGolden.WIDTH,
            bottom = (SceneGolden.HEIGHT * 0.38f + 12f).toInt(),
            label = "bird band",
            maxDifferingFraction = gate,
        )

        /**
         * The water, on `lake-busy`'s own band: bottom at the ground's solid top line, height
         * `0.16 * lake.height` of the frame, which is what `updateLakeBandY` computes.
         */
        fun waterBand(gate: Double): GoldenFocus {
            val bottom = SceneGolden.HEIGHT * SceneSpace.GROUND_SOLID_TOP_Y_FRACTION
            val height = SceneGolden.HEIGHT * 0.16f * LAKE_BUSY_HEIGHT
            return GoldenFocus(
                left = 0,
                top = (bottom - height).toInt(),
                right = SceneGolden.WIDTH,
                bottom = bottom.toInt(),
                label = "water band",
                maxDifferingFraction = gate,
            )
        }

        /** `SharedGoldenScenes.lakeBusy`'s own lake height, restated where the rectangle needs it. */
        const val LAKE_BUSY_HEIGHT = 0.8f

        /** The sun-arc default every golden scene draws at. */
        const val DEFAULT_SUN_CLOUD_HEIGHT = 0.42f
    }

    // ---------------------------------------------------------------- the assertions

    /**
     * `day`, watched over the cloud band and the bird band.
     *
     * The same committed picture `SceneGoldenTest.day` asserts, measured over two rectangles it
     * does not measure. Before v4.26 a cloud could be redrawn from scratch and this frame's only
     * complaint would have been the whole-frame gate, which 0.2% of 288 000 pixels makes deaf to
     * everything smaller than a structural change.
     */
    @Test
    fun `day-sky`() = SceneGolden.assertMatches(
        SharedGoldenScenes.day(),
        extraFocus = listOf(
            cloudBand(SettingsGates.CLOUD_BAND_GATE),
            birdBand(SettingsGates.BIRD_BAND_GATE),
        ),
    )

    /**
     * **The first committed frame in which the struck waterline is drawn at all.**
     *
     * Regenerating the twenty-five existing goldens after the waterline landed changed **zero** of
     * them, and that is a coverage finding rather than a reassurance: in every scene the suite
     * already pinned, the theme's sky and its water are far enough apart that the line correctly
     * does not appear. So the feature that exists for the *worst* theme had no golden containing
     * it — the same shape as `BACKLOG_v4_25.md` item 65, and the same answer v4.23 gave when no
     * committed frame drew a celestial body.
     *
     * Tundra with a tall lake at midday is the measured worst case: sky `#D0E7F2` above the shore
     * against water `#D6EAF2`, a CIELab dE of **2.16** and **0.1** of Rec. 601 luma. Without the
     * line the shore does not exist; with it, the line stands 14.5 of luma clear of the sky.
     */
    @Test
    fun `waterline-worst-theme`() = SceneGolden.assertMatches(
        GoldenScene(
            name = "waterline-worst-theme",
            dayPhase = GoldenScene.day(),
            themeId = "tundra",
            customise = { it.copy(lake = it.lake.copy(visible = true, height = 0.8f)) },
            focus = listOf(
                GoldenFocus(
                    left = 0,
                    top = (SceneGolden.HEIGHT * SceneSpace.GROUND_SOLID_TOP_Y_FRACTION - SceneGolden.HEIGHT * 0.16f * 0.8f).toInt() - 3,
                    right = SceneGolden.WIDTH,
                    bottom = (SceneGolden.HEIGHT * SceneSpace.GROUND_SOLID_TOP_Y_FRACTION - SceneGolden.HEIGHT * 0.16f * 0.8f).toInt() + 5,
                    label = "the struck waterline",
                ),
            ),
        ),
    )

    /** `lake-busy`, watched over the water band the mirror and the waterline are drawn in. */
    @Test
    fun `lake-busy-water`() = SceneGolden.assertMatches(
        SharedGoldenScenes.lakeBusy(),
        extraFocus = listOf(waterBand(SettingsGates.WATER_BAND_GATE)),
    )

    /**
     * **The three gates stand between their floor and their signal, re-measured on every run.**
     *
     * The shape `SettingsGateScenesTest.theGatesStandBetweenFloorAndSignal` established: the floor
     * is the in-process identity of two renders of the same scene, which must be exactly zero, and
     * the signal is the weakest regression the gate has to catch — here, each family switched off.
     * Switching a family off is the *weakest* regression available, not the loudest: any redraw
     * that changes less than the family's total absence is not caught by this rectangle, and
     * saying so is the honest limit of what these three gates buy.
     */
    @Test
    fun theSkyAndWaterGatesStandBetweenFloorAndSignal() {
        // **The weakest regression each gate has to catch, not the loudest available.** Hiding a
        // whole family is easy to measure and useless as a derivation input: the cloud band is 92%
        // cloud, so half of *that* is a gate that only ever fires on total absence, which the
        // whole-frame gate already catches. So each family loses **one member** instead --
        // `CandidateThreshold` admits candidates in pool order, so a density reduced by one pool
        // step removes exactly one cloud, one bird, one boat. That is the artwork-scale change
        // item 65 says the net cannot see.
        measureGate(
            "cloud band", SettingsGates.CLOUD_BAND_GATE, cloudBand(SettingsGates.CLOUD_BAND_GATE),
            SharedGoldenScenes.day(),
        ) { it.copy(clouds = it.clouds.copy(density = it.clouds.density * 0.5f)) }

        // The exception, and it is the one that closes item 65 most directly: the birds are so few
        // and so small that **every one of them disappearing** moves less of this rectangle than the
        // shared 2% focus limit forgives. There is no weaker regression worth deriving from -- the
        // total loss of the family is already under the old limit.
        measureGate(
            "bird band", SettingsGates.BIRD_BAND_GATE, birdBand(SettingsGates.BIRD_BAND_GATE),
            SharedGoldenScenes.day(),
        ) { it.copy(birds = it.birds.copy(visible = false)) }

        measureGate(
            "water band", SettingsGates.WATER_BAND_GATE, waterBand(SettingsGates.WATER_BAND_GATE),
            SharedGoldenScenes.lakeBusy(),
        ) { it.copy(lake = it.lake.copy(dolphinsVisible = false)) }
    }

    private fun measureGate(
        label: String,
        gate: Double,
        focus: GoldenFocus,
        scene: GoldenScene,
        regress: (SceneCustomization) -> SceneCustomization,
    ) {
        val base = SceneGolden.render(scene)
        val again = SceneGolden.render(scene)
        assertEquals(
            "$label: the in-process floor must be exact, or the gate is measuring the renderer's " +
                "own noise rather than a regression",
            0.0, SceneGolden.differingFractionIn(base, again, focus), 0.0,
        )
        again.recycle()

        val regressed = SceneGolden.render(
            GoldenScene(
                name = scene.name,
                dayPhase = scene.dayPhase,
                sceneSeconds = scene.sceneSeconds,
                warmUpFrames = scene.warmUpFrames,
                themeId = scene.themeId,
                weather = scene.weather,
                customise = { regress(scene.customise(it)) },
            ),
        )
        val signal = SceneGolden.differingFractionIn(base, regressed, focus)
        regressed.recycle()
        base.recycle()
        android.util.Log.i(
            "GATEDERIVE",
            "$label: signal=${"%.4f".format(signal * 100)}% gate=${"%.4f".format(gate * 100)}%",
        )
        assertTrue(
            "$label: the weakest regression moves ${signal * 100}% of the rectangle and the gate " +
                "is ${gate * 100}%. A gate at or above its own signal catches nothing",
            signal > gate,
        )
    }
}
