package com.paperscrape.livewallpaper.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The backend that actually ships, under visual test.
 *
 * Every one of the fourteen Canvas goldens renders through `CanvasSceneTarget`. That is the right
 * choice and is not what this changes — a golden drawn by code the app does not ship proves nothing
 * about the code it does. But `GlSceneTarget` is what draws the wallpaper on every device where
 * EGL works, it approximates the same picture by completely different arithmetic, and until v3.2
 * nothing pinned a single pixel of it. That gap is P1-4 in the v3.0 assessment and this suite is
 * the answer to it.
 *
 * **Three scenes, chosen for their geometry rather than to make a number look better.** Between
 * them they exercise the three shapes the two backends build by genuinely different routes:
 *
 * - `day` — `drawRadialGlow` is a `RadialGradient` on one side and a fan of triangles on the other,
 *   and `drawVerticalGradientShape` is a `LinearGradient` over a `Path` against stepped columns.
 * - `lake-busy` — every lane occupied: sprite batching, atlas UVs and premultiplied blending all
 *   under load, plus the arcs and ovals that one side rasterises analytically and the other
 *   tessellates.
 * - `thunderstorm` — `StormAtmosphere`'s whole output over a bright theme, which is the largest
 *   area of blended, darkened colour the scene ever produces.
 *
 * Adding a fourth would cost emulator time and pin nothing new. See [SharedGoldenScenes] for why
 * the scene definitions live outside both test classes, and [GlGolden.Tolerance] for where the
 * thresholds come from.
 */
@RunWith(AndroidJUnit4::class)
class GlSceneGoldenTest {

    @Test
    fun day() = GlGolden.assertGlBackendUnchanged(SharedGoldenScenes.day())

    @Test
    fun lakeBusy() = GlGolden.assertGlBackendUnchanged(SharedGoldenScenes.lakeBusy())

    @Test
    fun thunderstorm() = GlGolden.assertGlBackendUnchanged(SharedGoldenScenes.thunderstorm())
}
