package com.paperscrape.livewallpaper.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A crowded frame is **one** `glDrawArrays`, and a person drawn in five layers does not change that.
 *
 * ### The claim, and why it is counted rather than reasoned about
 *
 * v4.30 draws a person as fixed art plus up to four weight masks. The plan it was written from
 * predicted the obvious consequence -- *"a pedestrian is one draw today; in layers it becomes three,
 * and with twelve people that is twenty-four more draw calls a frame"* -- and that prediction is
 * wrong, for a reason that only became true in v4.29.
 *
 * [GlSceneTarget] accumulates vertices in one batch and empties it **only when the texture changes**.
 * Since v4.29 every sprite the scene draws lives in a single atlas (332 sprites then;
 * `GlAtlasOccupancyTest` keeps it so) and there are no standalone textures left, so
 * the texture never changes; a flat fill samples a white texel from that same atlas. A layer more is
 * therefore six vertices in the batch that was already open, and no draw call at all.
 *
 * Two things make that true rather than lucky, and both are deliberate:
 *
 *  - the additive contribution travels in the **sign of the vertex alpha**, not in a blend state.
 *    `glBlendFunc(GL_ONE, GL_ONE)` would have been the obvious way to add, and it would have emptied
 *    the batch at every layer -- two draws per pedestrian, which is exactly the cost the plan feared;
 *  - the masks are in the atlas like everything else, because they are ordinary sprites.
 *
 * The arithmetic has been wrong twice on this project, so this counts on the device instead
 * ([GlSceneTarget.drawCalls]).
 */
@RunWith(AndroidJUnit4::class)
class GlDrawCallTest {

    @Test
    fun aCrowdedFrameIsASingleDrawCall() {
        val worst = mutableListOf<String>()
        for (scene in crowded()) {
            val counted = GlGolden.countDrawCalls(scene)
            worst += "${scene.name}: ${counted.drawCalls} draw(s), ${counted.vertices} vertices, " +
                "${counted.sprites} sprite blits"
            assertEquals(
                "${scene.name} took ${counted.drawCalls} draw calls. A person is drawn in layers " +
                    "and the batch must still survive them: see this class's own doc for the two " +
                    "things that keep that true",
                1,
                counted.drawCalls,
            )
        }
        // Printed whether it passes or not: the number is the measurement the release reports.
        println("GlDrawCallTest: " + worst.joinToString(" | "))
        assertTrue("no crowded scene was rendered", worst.isNotEmpty())
    }

    /**
     * **And a person walking out of a window, cut to its pane, is still in the one batch** (v5.12): a
     * cut sprite is a smaller quad from the same atlas entry ([SceneCanvas.drawSpriteClipped]), so it
     * changes no state. Autumn at night, at the first second somebody is seen walking -- found by
     * drawing the scene as the golden harness configures it, a second at a time, until a frame has a
     * cut bust -- counted on the device like the crowds above.
     */
    @Test
    fun aFrameWithSomebodyWalkingIsStillASingleDrawCall() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        fun scene(seconds: Double) = GoldenScene(name = "walking-autumn", dayPhase = GoldenScene.night(23f), themeId = "autumn", sceneSeconds = seconds)
        val counter = CutCounter()
        val renderer = PaperRenderer(GlGolden.WIDTH, GlGolden.HEIGHT, context)
        scene(0.0).configure(renderer)
        var walking = -1.0
        for (second in 1..900) {
            counter.cut = 0
            renderer.draw(counter, scene(0.0).dayPhase, SceneTime(second.toDouble()), 0f)
            if (counter.cut > 0) { walking = second + 1.0; break }
        }
        assertTrue("nobody walked in 15 minutes of Autumn", walking > 0)
        // A second on: still walking (the shortest walk the roster makes takes 3.8 s), and seen to be so --
        // the frame counted is the frame this canvas is drawn, at the same moment by the same rules.
        counter.cut = 0
        renderer.draw(counter, scene(0.0).dayPhase, SceneTime(walking), 0f)
        assertTrue("nobody was walking in the frame counted", counter.cut > 0)
        val counted = GlGolden.countDrawCalls(scene(walking))
        println("GlDrawCallTest walking at $walking s: ${counted.drawCalls} draw(s), ${counted.vertices} vertices, ${counted.sprites} sprite blits")
        assertEquals("a frame with somebody walking took ${counted.drawCalls} draw calls", 1, counted.drawCalls)
    }

    /** Counts the sprites cut to a box, and draws nothing. */
    private class CutCounter : SceneCanvas {
        var cut = 0
        override fun save() = Unit
        override fun restore() = Unit
        override fun translate(dx: Float, dy: Float) = Unit
        override fun scale(sx: Float, sy: Float) = Unit
        override fun rotate(degrees: Float) = Unit
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: android.graphics.Paint) = Unit
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: android.graphics.Paint) = Unit
        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: android.graphics.Paint) = Unit
        override fun drawDisc(resId: Int, source: SpriteSource, cx: Float, cy: Float, radius: Float, colour: Int, alpha: Int) = Unit
        override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: android.graphics.Paint) = Unit
        override fun drawWedge(cx: Float, cy: Float, radius: Float, startAngle: Float, sweepAngle: Float, paint: android.graphics.Paint) = Unit
        override fun drawShape(shape: SceneShape, paint: android.graphics.Paint) = Unit
        override fun drawVerticalGradientShape(shape: SceneShape, gradientTopY: Float, gradientBottomY: Float, topColor: Int, bottomColor: Int, alpha: Int) = Unit
        override fun drawVerticalGradientRect(left: Float, top: Float, right: Float, bottom: Float, topColor: Int, bottomColor: Int) = Unit
        override fun drawRadialGlow(cx: Float, cy: Float, radius: Float, color: Int, centerAlpha: Int) = Unit
        override fun drawSprite(resId: Int, source: SpriteSource, left: Float, top: Float, tintColor: Int, alpha: Int, additive: Boolean) = Unit
        override fun drawSpriteClipped(
            resId: Int, source: SpriteSource, left: Float, top: Float, tintColor: Int, alpha: Int, additive: Boolean,
            clipLeft: Float, clipTop: Float, clipRight: Float, clipBottom: Float,
        ) {
            cut++
        }
    }

    /**
     * Three streets at full density, on the three themes the layer investigation photographed.
     *
     * People **and** cars, because a car's seated busts are drawn in layers too and the batch has to
     * survive a person alternating with a vehicle, a building and a tree -- which is the case a
     * scene of nothing but pedestrians would not exercise.
     */
    /**
     * **And while the places among the houses come and go** (v5.13B2): each crowded scene swept by the swipe
     * offset across three tiles of the street, sixty frames, so each of the five places leaves the screen
     * and comes back as another copy, with the building the street deals that copy, three times -- every
     * frame one draw.
     */
    @Test
    fun aFrameAsThePlacesComeBackIsStillASingleDrawCall() {
        val tile = 2f / PaperRenderer.HILL_PARALLAX
        val offsets = FloatArray(60) { 3f * tile * it / 60 }
        val seen = mutableListOf<String>()
        for (scene in crowded()) {
            val sweep = GlGolden.countDrawCallsAcross(scene, offsets)
            val calls = sweep.calls
            seen += "${scene.name}: ${calls.min()}..${calls.max()} draws over ${calls.size} frames, at most ${sweep.vertices.max()} vertices"
            assertTrue("${scene.name} swept across the street: ${calls.toList()}", calls.all { it == 1 })
        }
        println("GlDrawCallTest sweep: " + seen.joinToString(" | "))
    }

    /**
     * **And a Christmas street** (v5.14): the roofs' strings, the wreaths, the towers' stars, the sill strings, the fir
     * A's lights, the trees' -- Christmas as a fresh install leaves it, and Autumn and Winter with *Christmas lights*
     * on -- by day and at one in the morning, with the theme's snow and the night's stars, swept across three tiles of
     * the street as above: one draw everywhere. And with everything on (the people at their most, the distant houses at
     * 50 % with their bulbs, the three groups' hours 13:00-20:00 so every building that keeps hours is shut, blinds and
     * shutters -- the fire and the police station never close), by day
     * and at night, in Christmas, Autumn and Winter; and at night with everything at its very most -- the snow falling
     * at 100 %, every star, the distant houses at 100 % -- in Christmas and Winter. One draw everywhere.
     *
     * Every bulb, every snowflake and every point star is a disc ([SceneCanvas.drawDisc]: a quad of the atlas sampling
     * `deco_bulb`, 6 vertices) and every wreath a quad of `deco_wreath`, not drawn shapes (24 to 36). Drawn as shapes,
     * the 5.13's sill strings alone took Christmas at night past the batch's [GlSceneTarget.MAX_VERTICES] at 16 offsets
     * of these 60, at the factory settings (13 956 vertices, measured on the BV6600 with the 5.13's own build); and with
     * everything on, the theme's snow (2 592 vertices of a frame) and the stars (1 320) still did at 4 to 6 (12 672) until
     * they were discs too (inventory I-730). The most vertices a frame takes is printed, which is what this test is for.
     */
    @Test
    fun aChristmasStreetIsStillASingleDrawCall() {
        val tile = 2f / PaperRenderer.HILL_PARALLAX
        val offsets = FloatArray(60) { 3f * tile * it / 60 }
        val seen = mutableListOf<String>()
        for (scene in christmas()) {
            val sweep = GlGolden.countDrawCallsAcross(scene, offsets)
            val calls = sweep.calls
            seen += "${scene.name}: swept ${calls.min()}..${calls.max()} draws, at most ${sweep.vertices.max()} vertices"
            assertTrue("${scene.name} swept across the street: ${calls.toList()}", calls.all { it == 1 })
        }
        println("GlDrawCallTest Christmas: " + seen.joinToString(" | "))
    }

    private fun christmas(): List<GoldenScene> {
        val asInstalled = listOf("christmas", "autumn", "winter").flatMap { theme ->
            listOf(12f to GoldenScene.day(12f), 1f to GoldenScene.night(1f)).map { (hour, phase) ->
                GoldenScene(
                    name = "christmas-lights-$theme-${hour.toInt()}",
                    dayPhase = phase,
                    themeId = theme,
                    customise = { base -> base.copy(christmasDecorationsEnabled = true) },
                )
            }
        }
        val everything = listOf("christmas", "autumn", "winter").flatMap { theme ->
            listOf(12f to GoldenScene.day(12f), 1f to GoldenScene.night(1f)).map { (hour, phase) ->
                GoldenScene(
                    name = "christmas-everything-$theme-${hour.toInt()}",
                    dayPhase = phase,
                    themeId = theme,
                    customise = { base ->
                        base.copy(
                            christmasDecorationsEnabled = true,
                            people = base.people.copy(visible = true, density = 1f),
                            peopleNightDensity = 1f,
                            distantHouses = DistantHousesConfig(visible = true, density = 0.5f),
                            shopHoursEnabled = true, shopOpenHour = 13f, shopCloseHour = 20f,
                            publicHoursEnabled = true, publicOpenHour = 13f, publicCloseHour = 20f,
                            towerHoursEnabled = true, towerOpenHour = 13f, towerCloseHour = 20f,
                        )
                    },
                )
            }
        }
        val atItsMost = listOf("christmas", "winter").map { theme ->
            GoldenScene(
                name = "christmas-most-$theme-1",
                dayPhase = GoldenScene.night(1f),
                themeId = theme,
                customise = { base ->
                    base.copy(
                        christmasDecorationsEnabled = true,
                        people = base.people.copy(visible = true, density = 1f),
                        peopleNightDensity = 1f,
                        distantHouses = DistantHousesConfig(visible = true, density = 1f),
                        precipitation = base.precipitation.copy(visible = true, type = PrecipitationType.SNOW, intensity = 1f),
                        stars = StarsConfig(visible = true, density = 1f),
                        shopHoursEnabled = true, shopOpenHour = 13f, shopCloseHour = 20f,
                        publicHoursEnabled = true, publicOpenHour = 13f, publicCloseHour = 20f,
                        towerHoursEnabled = true, towerOpenHour = 13f, towerCloseHour = 20f,
                    )
                },
            )
        }
        return asInstalled + everything + atItsMost
    }

    private fun crowded() = listOf("city", "spring", "winter").map { theme ->
        GoldenScene(
            name = "crowd-$theme",
            dayPhase = GoldenScene.day(),
            themeId = theme,
            customise = { base ->
                base.copy(
                    people = base.people.copy(visible = true, density = 1f),
                    peopleNightDensity = 1f,
                )
            },
        )
    }
}
