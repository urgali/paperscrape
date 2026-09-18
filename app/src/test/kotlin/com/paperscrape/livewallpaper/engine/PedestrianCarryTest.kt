package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The umbrella appears and disappears out of sight, and in the rain everyone who can hold one does.
 *
 * The interesting one is the first. v4.22 gave the cars a rule -- membership changes only while
 * nothing of the thing is on screen -- and [CarSelection.offScreen]'s own doc says in as many words
 * that pedestrians were left out of it. An object appearing *in the hand of a figure already
 * walking* is the case that rule was written for, so v4.28 takes it over; these tests are what say
 * it was taken over rather than approximated.
 *
 * **v5.4 replaced the other half, who carries, on the maintainer's instruction.** There was a share
 * -- two adults in three, first rolled per walker and then dealt over the street -- and there is
 * none now: in the rain, everybody who has a carry pose to be drawn in is carrying. What is left to
 * test is therefore small, and deliberately so; the arithmetic these tests used to check no longer
 * exists. `PedestrianCarryPopulationTest` checks the rule on the streets the app actually builds
 * and `PedestrianUmbrellaSceneTest` checks that it reaches the pixels.
 */
class PedestrianCarryTest {

    // ---------------------------------------------------------------- who carries, and when

    @Test
    fun `nobody carries anything when it is not raining`() {
        for (canHold in listOf(true, false)) {
            assertFalse(
                "an umbrella in a clear sky is the defect, not the feature",
                PedestrianCarry.wantsUmbrella(raining = false, canHold = canHold),
            )
        }
    }

    /** **The maintainer's rule, in one assertion**: rain, a pose to hold it in, and that is all. */
    @Test
    fun `everyone who can hold one carries it in the rain`() {
        assertTrue(
            "in the rain every walker who can hold an umbrella holds one",
            PedestrianCarry.wantsUmbrella(raining = true, canHold = true),
        )
    }

    /**
     * **`canHold` is the artwork's length and nothing else, and v5.4H is the proof of it.**
     *
     * Until v5.4H this asserted the opposite: `PeopleLayerTable.CARRY` had a pose for `man` and
     * `woman` and for nobody else, so `CARRY[kindIndex]` for a child would have been an index out
     * of bounds, and the two `assertFalse`s below were `true` for the children. The test's own
     * comment said *"the day a child carry pose is drawn, it goes green on its own and the rule
     * needs no edit"* -- and that is exactly what happened: the artwork landed, this file went red
     * at the number, and `PedestrianCarry.kt` was not touched.
     *
     * So what it pins now is the same property from the other side: **every family the walk table
     * has, the carry table has**. There is no family the scene can put on the pavement that the
     * rain has no pose for, and a redraw that adds a fifth family without its carry pose fails here
     * rather than at an index out of bounds in a rainy frame on a device.
     */
    @Test
    fun `canHold is the artwork's own limit, not a rule about age`() {
        assertEquals(
            "a carry pose is shipped for every walking family: man, woman, boy, girl",
            PeopleLayerTable.WALK.size, PeopleLayerTable.CARRY.size,
        )
        assertEquals("which is four", 4, PeopleLayerTable.CARRY.size)
        assertTrue("the man has a pose", PedestrianCarry.canHold(0))
        assertTrue("the woman has a pose", PedestrianCarry.canHold(1))
        assertTrue("the boy has one since v5.4H", PedestrianCarry.canHold(2))
        assertTrue("and so does the girl", PedestrianCarry.canHold(3))
        assertFalse(
            "and a family the artwork does not have still cannot be drawn holding one",
            PedestrianCarry.canHold(PeopleLayerTable.CARRY.size),
        )
    }

    /**
     * A walker with no pose carries nothing however hard it is raining -- the other direction of
     * the rule, and the one that keeps `CARRY[kindIndex]` in bounds.
     */
    @Test
    fun `a walker with no carry pose never carries, in any weather`() {
        for (raining in listOf(true, false)) {
            assertFalse(
                "there is no pose to draw this walker holding one",
                PedestrianCarry.wantsUmbrella(raining = raining, canHold = false),
            )
        }
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
