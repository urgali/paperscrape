package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The umbrella appears and disappears out of sight, only in rain, and only for adults.
 *
 * The interesting one is the first. v4.22 gave the cars a rule -- membership changes only while
 * nothing of the thing is on screen -- and [CarSelection.offScreen]'s own doc says in as many words
 * that pedestrians were left out of it. An object appearing *in the hand of a figure already
 * walking* is the case that rule was written for, so v4.28 takes it over; these tests are what say
 * it was taken over rather than approximated.
 */
class PedestrianCarryTest {

    // ---------------------------------------------------------------- who carries, and when

    @Test
    fun `nobody carries anything when it is not raining`() {
        for (noise in listOf(0f, 0.3f, 0.66f, 0.99f)) {
            assertFalse(
                "an umbrella in a clear sky is the defect, not the feature",
                PedestrianCarry.wantsUmbrella(raining = false, isAdult = true, noise = noise),
            )
        }
    }

    @Test
    fun `children never carry, however the noise falls`() {
        for (step in 0..100) {
            assertFalse(
                "children walk through the rain -- the phase-2 photographs were approved on that",
                PedestrianCarry.wantsUmbrella(raining = true, isAdult = false, noise = step / 100f),
            )
        }
    }

    @Test
    fun `the share is the fraction of adults that carry, and it is neither none nor all`() {
        val carried = (0..999).count {
            PedestrianCarry.wantsUmbrella(raining = true, isAdult = true, noise = it / 1000f)
        }
        assertEquals("the share is read straight off the noise", (PedestrianCarry.SHARE * 1000).toInt(), carried)
        assertTrue("a street where nobody carries is the rule failing", carried > 0)
        assertTrue("a street where everybody carries reads as a uniform", carried < 1000)
    }

    // ---------------------------------------------------------------- the off-screen rule

    @Test
    fun `a walker with a copy on screen never changes what it is carrying`() {
        for (current in listOf(false, true)) {
            for (wanted in listOf(false, true)) {
                assertEquals(
                    "an umbrella may not open or close in front of the viewer",
                    current,
                    PedestrianCarry.nextCarrying(current = current, wanted = wanted, onScreen = true),
                )
            }
        }
    }

    @Test
    fun `a walker with nothing on screen takes up what the weather asks for`() {
        assertTrue(PedestrianCarry.nextCarrying(current = false, wanted = true, onScreen = false))
        assertFalse(PedestrianCarry.nextCarrying(current = true, wanted = false, onScreen = false))
        assertTrue(PedestrianCarry.nextCarrying(current = true, wanted = true, onScreen = false))
        assertFalse(PedestrianCarry.nextCarrying(current = false, wanted = false, onScreen = false))
    }

    /**
     * **The rule can actually fire**, which is the property that decides whether the feature works
     * at all rather than merely being safe.
     *
     * If a pedestrian always had some copy on screen, [PedestrianCarry.nextCarrying] would never be
     * allowed to move and no umbrella would ever go up. It does not: the scene tiles every
     * `tileWidth`, which is twice the screen width, so each walker has roughly one screen width of
     * its loop with no copy visible. This walks the loop through the draw pass's *own* cull
     * helpers, so it is the real geometry and not a restatement of it.
     */
    @Test
    fun `a pedestrian really does leave the screen, so the rule is not a deadlock`() {
        val screenWidth = 1080f
        val tileWidth = screenWidth * 2f          // SceneObject.GroundGeometry: twice the screen
        val halfWidth = 21.5f * 0.6f              // a near-row adult, generously wide
        var offScreenSteps = 0
        val steps = 400
        for (step in 0 until steps) {
            val x = step / steps.toFloat() * tileWidth
            var visible = false
            val first = SceneObjectRenderer.firstVisibleTileOffset(x, halfWidth, tileWidth)
            val limit = SceneObjectRenderer.tileOffsetLimit(x, halfWidth, tileWidth, screenWidth)
            for (tile in first until limit) {
                if (SceneObjectRenderer.isHorizontallyVisible(x + tile * tileWidth, halfWidth, screenWidth)) {
                    visible = true
                }
            }
            if (!visible) offScreenSteps++
        }
        assertTrue(
            "if a walker were never off screen the umbrella could never open: $offScreenSteps of " +
                "$steps steps had no copy on screen",
            offScreenSteps > 0,
        )
        assertTrue(
            "and it must be off screen for a useful share of its loop, or an umbrella would take " +
                "an unreasonable time to appear after the rain starts: $offScreenSteps of $steps",
            offScreenSteps > steps / 4,
        )
    }

    // ---------------------------------------------------------------- the canopy's colour

    @Test
    fun `every canopy colour is one of the palette's, whatever the noise`() {
        for (step in 0..1000) {
            val c = PedestrianCarry.canopyColour(step / 1000f)
            assertTrue("colour $c is not in the palette", PedestrianCarry.PALETTE.contains(c))
        }
        // Including the ends, where a `* size` rounds to `size` and would index past the array.
        assertTrue(PedestrianCarry.PALETTE.contains(PedestrianCarry.canopyColour(1f)))
        assertTrue(PedestrianCarry.PALETTE.contains(PedestrianCarry.canopyColour(0f)))
    }

    @Test
    fun `the palette uses every colour it declares`() {
        val used = (0..1000).map { PedestrianCarry.canopyColour(it / 1000f) }.toSet()
        assertEquals(
            "a colour nobody can be given is a colour that should not be declared",
            PedestrianCarry.PALETTE.toSet(), used,
        )
    }
}
