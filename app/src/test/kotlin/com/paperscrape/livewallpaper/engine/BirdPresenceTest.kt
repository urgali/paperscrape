package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the flock is on screen, and — the part that was wrong — how solid it is while it is.
 *
 * The reported symptom was birds going progressively transparent toward sunset with night birds
 * switched off. The cause was in the call site: it multiplied their alpha by `dayBlend`, which
 * slides from 1 down to 0.5 across the last 12% of the daylight arc. "Fade out toward night" was
 * the intent; "half transparent while the sun is still up" was the behaviour. Measured at a fixed
 * 20:00 on a OnePlus 6T: alpha 0.47-0.53 before, 1.00-1.02 after.
 */
class BirdPresenceTest {

    private fun flock(night: Boolean) = BirdsConfig(
        visible = true, density = 0.5f, nightBirds = night,
        colors = listOf(BirdColorWeight(0xFF000000.toInt(), 1f)),
    )

    private val day = flock(night = false)
    private val nightToo = flock(night = true)

    @Test
    fun `the flock is fully solid for the whole time the sun is up`() {
        // dayBlend holds at 1.0 across the middle of the arc and eases to 0.5 at the horizon.
        // Every one of these is daylight, and a paper bird in daylight is opaque paper.
        for (blend in listOf(1.0f, 0.9f, 0.75f, 0.6f, 0.5f)) {
            assertEquals("dayBlend $blend is daylight", 1f, day.presenceAt(blend), 1e-6f)
        }
    }

    @Test
    fun `the golden hour is not a fade to half`() {
        // The regression this pins, stated as the numbers that were wrong. On the default
        // 06:00/20:00 arc these are 19:00, 19:20 and sunset itself: the sun is still up at all
        // three, and each used to hand back its own blend as the alpha.
        assertEquals("19:00", 1f, day.presenceAt(0.8f), 1e-6f)
        assertEquals("19:20", 1f, day.presenceAt(0.7f), 1e-6f)
        assertEquals("sunset", 1f, day.presenceAt(0.5f), 1e-6f)
    }

    @Test
    fun `the flock leaves after the sun does, and is gone before full dark`() {
        assertTrue("just below the horizon it is still mostly there", day.presenceAt(0.45f) > 0.5f)
        assertTrue("halfway down it has gone", day.presenceAt(0.25f) <= 0f)
        assertEquals("and stays gone", 0f, day.presenceAt(0.0f), 1e-6f)
    }

    @Test
    fun `the fade is monotone`() {
        var previous = -1f
        for (step in 0..20) {
            val value = day.presenceAt(step / 20f)
            assertTrue("presence must not fall as the sky brightens", value >= previous)
            previous = value
        }
    }

    @Test
    fun `night birds are solid at every hour`() {
        for (step in 0..20) {
            assertEquals(1f, nightToo.presenceAt(step / 20f), 1e-6f)
        }
    }

    @Test
    fun `presence is clamped to a usable alpha`() {
        for (blend in listOf(-1f, 0f, 0.3f, 0.5f, 2f)) {
            val value = day.presenceAt(blend)
            assertTrue("out of range: $value", value in 0f..1f)
        }
    }
}
