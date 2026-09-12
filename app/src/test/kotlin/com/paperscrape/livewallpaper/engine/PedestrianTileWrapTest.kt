package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a walker finishes a crossing, and whether anybody can see it happen.
 *
 * ### The claim this exists to check, which did not survive being checked
 *
 * The plan v4.30 was written from says the colours may be re-dealt on the crossing counter alone,
 * because *"the point at which the count passes 1 is off screen by construction, so nothing changes
 * under the eye -- the same convention the cars use"*. It is a reasonable-sounding sentence and it
 * is **not true**, and the difference between a pedestrian and a car is what makes it not true.
 *
 * A car has one body and it drives off the edge; `CarSelection.offScreen` is about that body. A
 * pedestrian is tiled: it exists at `x + k * tileWidth` for every whole `k`, and the draw pass
 * walks the copies that intersect the viewport. When `tileFraction` wraps from just under 1 to just
 * over 0, the figure does not move at all -- the copy at `k` stops being the visible one and the
 * copy at `k + 1` starts. Whether that instant is visible depends on `shiftXWrapped`, which scrolls
 * with the ground, and this measures how often it is.
 *
 * ### Why it matters, and what was done instead
 *
 * Because the answer is "about half the time", `SceneObjectRenderer` does not re-deal on the counter
 * directly. It holds the crossing number per walker and moves it only when the walker's own draw
 * pass reports that no copy of it was drawn -- the machine v4.28 built for the umbrella
 * ([PedestrianCarry.nextCarrying]), reused rather than reinvented. The guarantee then comes from
 * the cull that actually ran, which is a fact about the frame and not an argument about geometry.
 */
class PedestrianTileWrapTest {

    /**
     * The scene's own numbers: the tiling period is twice the screen width, and a walker is culled
     * on its half-width.
     */
    private val screenWidth = 1080f
    private val tileWidth = screenWidth * 2f
    private val halfWidth = 30f

    /** Exactly the predicate `drawPeople` culls with, and the range it scans. */
    private fun anyCopyVisible(x: Float): Boolean {
        val first = SceneObjectRenderer.firstVisibleTileOffset(x, halfWidth, tileWidth)
        val limit = SceneObjectRenderer.tileOffsetLimit(x, halfWidth, tileWidth, screenWidth)
        for (tile in first until limit) {
            if (SceneObjectRenderer.isHorizontallyVisible(x + tile * tileWidth, halfWidth, screenWidth)) {
                return true
            }
        }
        return false
    }

    @Test
    fun `a walker really is out of sight for about half of every crossing`() {
        // The premise the whole off-screen rule rests on: there is such a moment at all.
        var unseen = 0
        val steps = 2000
        for (i in 0 until steps) {
            val fraction = i.toFloat() / steps
            if (!anyCopyVisible(fraction * tileWidth)) unseen++
        }
        val share = unseen.toFloat() / steps
        assertTrue(
            "a walker is out of sight for ${"%.1f".format(share * 100)}% of its crossing, which " +
                "is the window every off-screen rule in this scene needs",
            share in 0.40f..0.50f,
        )
    }

    @Test
    fun `the instant the crossing counter turns over is on screen about half the time`() {
        // The counter turns over where `tileFraction` wraps, which is where `x` equals the parallax
        // shift itself. Sweeping the shift is sweeping the scroll.
        var visibleAtWrap = 0
        val steps = 2000
        for (i in 0 until steps) {
            val shift = i.toFloat() / steps * tileWidth
            if (anyCopyVisible(shift)) visibleAtWrap++
        }
        val share = visibleAtWrap.toFloat() / steps
        assertTrue(
            "the crossing counter turns over in view ${"%.1f".format(share * 100)}% of the time. " +
                "If this were ever 0 the plan's 'off screen by construction' would be right and " +
                "PeopleColours could deal straight off the counter; it is not, which is why " +
                "SceneObjectRenderer holds the number until its own cull says nothing was drawn",
            share > 0.40f,
        )
    }

    @Test
    fun `the crossing counter advances once per crossing and never backwards`() {
        // A step of the counter must be a wrap of the position, or "once per crossing" is a claim
        // about nothing. Walked forward in small steps of clock time, both directions of travel.
        for (direction in listOf(1f, -1f)) {
            val speed = 0.02f
            val phase = 0.37f
            val start = 0.61f
            var previous = PeopleColours.crossingOf(0.0, speed, phase, start, direction)
            var steps = 0
            var seconds = 0.0
            while (seconds < 600.0) {
                seconds += 0.25
                val now = PeopleColours.crossingOf(seconds, speed, phase, start, direction)
                if (now != previous) {
                    assertEquals(
                        "the counter moved by more than one crossing at $seconds s",
                        1,
                        kotlin.math.abs(now - previous),
                    )
                    steps++
                    previous = now
                }
            }
            // 600 s at 0.02 crossings per second is twelve crossings, whichever way it is walked.
            assertEquals("crossings counted walking $direction", 12, steps)
        }
    }

    @Test
    fun `the counter comes off the clock and not off the uptime`() {
        // The one property a restart of the process must not be able to reset. `clockSeconds` is a
        // pure function of the hour that moved the sun, so two runs at the same hour agree and an
        // uptime of zero is not a crossing of zero.
        assertEquals(
            "13:30 is 48 600 s into the day",
            48_600.0,
            PeopleColours.clockSeconds(13.5f),
            0.001,
        )
        assertTrue(
            "and a walker an hour into the day is not on its first crossing",
            PeopleColours.crossingOf(PeopleColours.clockSeconds(1f), 0.02f, 0f, 0f, 1f) > 0,
        )
    }
}
