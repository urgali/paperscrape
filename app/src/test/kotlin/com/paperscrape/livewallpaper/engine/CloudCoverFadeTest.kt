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

    /**
     * Item 135 (v5.7F): the snapshot aging out eases to the theme's cover at the forecast's rate.
     *
     * Before v5.7F this path went through `coverToward(null)` and the drawn cover left 0.87 for the
     * theme's density in **one** frame, against the 270 the same move takes as a forecast change.
     */
    @Test
    fun anExpiryEasesToTheThemesCoverAtTheForecastsRate() {
        val fade = CloudCoverFade(41)
        fade.coverToward(0.87f, dt)
        var previous = 0.87f
        var steps = 0
        while (true) {
            val value = fade.coverLapsingTo(0.25f, dt) ?: break
            val moved = kotlin.math.abs(value - previous)
            assertTrue(
                "the lapsing cover moved $moved in one frame, past the ${CloudCoverFade.COVER_UNITS_PER_SECOND * dt} a frame allows",
                moved <= CloudCoverFade.COVER_UNITS_PER_SECOND * dt + 1e-6f,
            )
            previous = value
            steps++
            assertTrue("the cover never arrived at the theme's in $steps frames", steps < 10_000)
        }
        // The frame that lands is the one that hands the sky back (null: the theme draws itself),
        // so the last eased value is one step short of it and within one step of the target.
        assertTrue("stopped at $previous, not within a step of 0.25", kotlin.math.abs(previous - 0.25f) <= CloudCoverFade.COVER_UNITS_PER_SECOND * dt + 1e-6f)
        val expected = ((0.87f - 0.25f) / CloudCoverFade.COVER_UNITS_PER_SECOND / dt).toInt()
        assertTrue("took $steps frames, expected about $expected", steps in (expected - 2)..(expected + 2))
        // And from then on the theme's own slider is followed exactly: nothing eases a setting.
        assertNull(fade.coverLapsingTo(0.60f, dt))
    }

    /**
     * The second half of the defect: the next good reading after a lapse eases in as well, from
     * the sky actually on screen, where a reset ramp made it snap as a "first observation".
     */
    @Test
    fun theReadingAfterALapseEasesInFromTheThemesCover() {
        val fade = CloudCoverFade(41)
        fade.coverToward(0.87f, dt)
        repeat(2_000) { fade.coverLapsingTo(0.25f, dt) }
        // Settled on the theme, which the user then moved: the ramp follows it silently.
        assertNull(fade.coverLapsingTo(0.30f, dt))
        val first = fade.coverToward(0.80f, dt)!!
        assertEquals(
            "the new reading must start one step from the theme's 0.30, not at 0.80",
            0.30f + CloudCoverFade.COVER_UNITS_PER_SECOND * dt, first, 1e-6f,
        )
    }

    /** With no forecast ever drawn there is nothing to ease from: the theme's sky, at once. */
    @Test
    fun aLapseWithNothingDrawnBeforeItIsTheThemeAtOnce() {
        val fade = CloudCoverFade(41)
        assertNull(fade.coverLapsingTo(0.25f, dt))
        // and the first reading after it is still a first observation
        assertEquals(0.87f, fade.coverToward(0.87f, dt)!!, 0f)
    }

    /** Switching Live Weather off in the middle of a lapse is still a switch: it snaps. */
    @Test
    fun switchingOffDuringALapseStillSnaps() {
        val fade = CloudCoverFade(41)
        fade.coverToward(0.87f, dt)
        repeat(10) { fade.coverLapsingTo(0.25f, dt) }
        assertNull(fade.coverToward(null, dt))
        assertEquals("switched on again, the first reading snaps", 0.10f, fade.coverToward(0.10f, dt)!!, 0f)
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
