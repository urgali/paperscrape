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
