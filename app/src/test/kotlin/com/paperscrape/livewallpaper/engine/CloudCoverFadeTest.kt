package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic under [CloudCoverFade], in two seconds instead of the hour the scene test costs.
 *
 * `CloudCoverCrossfadeTest` is the test that would actually catch the reported defect coming back,
 * because it renders. This one pins the three properties that test cannot distinguish from each
 * other when it fails — arrival, exactness and the first-observation snap — so that a failure there
 * arrives already diagnosed.
 */
class CloudCoverFadeTest {

    private val dt = 1f / 30f

    @Test
    fun theFirstCoverIsDrawnAtOnce() {
        // A fresh engine, and every golden, draws the sky it was handed on its first frame. Easing
        // here would mean a wallpaper that boots to the wrong weather.
        val fade = CloudCoverFade(41)
        assertEquals(0.87f, fade.coverToward(0.87f, dt)!!, 0f)
    }

    @Test
    fun aLaterCoverIsApproachedAndReachedExactly() {
        val fade = CloudCoverFade(41)
        fade.coverToward(0.87f, dt)

        // No single step may exceed the rate, which is the whole of what stops the sky arriving in
        // one frame.
        var previous = 0.87f
        var steps = 0
        var value = previous
        while (value != 0.42f) {
            value = fade.coverToward(0.42f, dt)!!
            val moved = kotlin.math.abs(value - previous)
            assertTrue(
                "the cover moved $moved in one frame, past the ${CloudCoverFade.COVER_UNITS_PER_SECOND * dt} a frame allows",
                moved <= CloudCoverFade.COVER_UNITS_PER_SECOND * dt + 1e-6f,
            )
            previous = value
            steps++
            assertTrue("the cover never arrived at 0.42 in $steps frames", steps < 10_000)
        }

        // **Exactly**, not asymptotically: a settled scene has to be the scene that shipped.
        assertEquals(0.42f, value, 0f)
        // And the time it takes is the declared rate, not something emergent. Within a frame or
        // two either way: the step is accumulated in `Float`, so the last step lands a rounding
        // error short of the target and costs one more iteration to close. That is a property of
        // the arithmetic, not a looseness in the claim.
        val expected = ((0.87f - 0.42f) / CloudCoverFade.COVER_UNITS_PER_SECOND / dt).toInt()
        assertTrue("took $steps frames, expected about $expected", steps in (expected - 2)..(expected + 2))
    }

    @Test
    fun liveWeatherGoingAwayHandsTheThemeBackImmediately() {
        // Switching the feature off is a deliberate act with a settings screen open in front of it,
        // and the theme's own slider must take the scene back on the next frame rather than fade
        // into it. A null target also has to clear the ramp, so switching it on again snaps.
        val fade = CloudCoverFade(41)
        fade.coverToward(0.87f, dt)
        assertNull(fade.coverToward(null, dt))
        assertEquals(0.10f, fade.coverToward(0.10f, dt)!!, 0f)
    }

    @Test
    fun anOpacityStartsSettledAndThenOnlyEases() {
        val fade = CloudCoverFade(41)
        // First observation of this candidate: fully present, at once.
        assertEquals(1f, fade.opacityOf(7, present = true, deltaSeconds = dt), 0f)

        // Told to go, it leaves over FADE_SECONDS and lands exactly on zero.
        var steps = 0
        var value = 1f
        while (value != 0f) {
            value = fade.opacityOf(7, present = false, deltaSeconds = dt)
            steps++
            assertTrue("the opacity never reached zero in $steps frames", steps < 10_000)
        }
        assertEquals(0f, value, 0f)
        // Same Float-accumulation slack as the cover ramp above, and for the same reason: measured
        // at 46 frames against the 45 the declared duration implies.
        val expected = (CloudCoverFade.FADE_SECONDS / dt).toInt()
        assertTrue("took $steps frames, expected about $expected", steps in (expected - 2)..(expected + 2))
    }

    @Test
    fun aCandidateStillFadingCountsAsVisible() {
        // What keeps `drawClouds` from returning early — and cutting the last step off the fade —
        // on the frames after the cover has already reached zero.
        val fade = CloudCoverFade(41)
        assertFalse("nothing has been drawn yet", fade.anythingVisible())
        fade.opacityOf(3, present = true, deltaSeconds = dt)
        assertTrue(fade.anythingVisible())
        repeat((CloudCoverFade.FADE_SECONDS / dt).toInt() + 2) {
            fade.opacityOf(3, present = false, deltaSeconds = dt)
        }
        assertFalse("the last cloud has finished leaving", fade.anythingVisible())
    }

    @Test
    fun candidatesFadeIndependently() {
        // The pool is a pool: one candidate arriving must not disturb its neighbour's state, which
        // is the bug a single shared opacity would have.
        val fade = CloudCoverFade(41)
        fade.opacityOf(0, present = true, deltaSeconds = dt)
        fade.opacityOf(1, present = false, deltaSeconds = dt)
        assertEquals(1f, fade.opacityOf(0, present = true, deltaSeconds = dt), 0f)
        assertEquals(0f, fade.opacityOf(1, present = false, deltaSeconds = dt), 0f)
    }
}
