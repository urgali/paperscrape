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
 * Since v4.29 all 332 sprites live in a single atlas and there are no standalone textures left, so
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
