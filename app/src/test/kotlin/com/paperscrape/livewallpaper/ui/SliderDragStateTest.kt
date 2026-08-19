package com.paperscrape.livewallpaper.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [SliderDragState], the handover logic between a slider's locally held in-flight value
 * and the persisted value coming back through the preferences flow.
 *
 * The behaviours worth pinning are the ones that were previously wrong or are easy to break:
 * the thumb must follow the finger without a storage round trip, exactly one write must happen
 * per drag, and the local value must not be released so early that the thumb snaps back to a
 * stale value before the write lands.
 */
class SliderDragStateTest {

    // --- displayValue -------------------------------------------------------------------------

    @Test
    fun `shows the persisted value when nothing is in flight`() {
        assertEquals(0.4f, SliderDragState.displayValue(0.4f, inFlight = null, awaitingCommit = null), 0f)
    }

    @Test
    fun `shows the in-flight value while dragging`() {
        // The whole point: the thumb tracks the finger, not the stored value.
        assertEquals(0.9f, SliderDragState.displayValue(0.4f, inFlight = 0.9f, awaitingCommit = null), 0f)
    }

    @Test
    fun `shows the committed value while the write is still in flight`() {
        // Between the finger lifting and the flow emitting, the persisted value is still stale.
        assertEquals(0.9f, SliderDragState.displayValue(0.4f, inFlight = null, awaitingCommit = 0.9f), 0f)
    }

    @Test
    fun `an active drag takes priority over a pending commit`() {
        // Second drag started before the first write came back.
        assertEquals(0.2f, SliderDragState.displayValue(0.4f, inFlight = 0.2f, awaitingCommit = 0.9f), 0f)
    }

    @Test
    fun `display value follows the drag exactly with no smoothing or clamping`() {
        var v = 0f
        while (v <= 1f) {
            assertEquals(v, SliderDragState.displayValue(0.5f, inFlight = v, awaitingCommit = null), 0f)
            v += 0.01f
        }
    }

    // --- shouldCommit -------------------------------------------------------------------------

    @Test
    fun `commits when the drag ended on a different value`() {
        assertTrue(SliderDragState.shouldCommit(persisted = 0.4f, inFlight = 0.9f))
    }

    @Test
    fun `does not commit when the drag ended where it started`() {
        // A tap on the thumb, or a drag returned to its origin, must not produce a write, a flow
        // emission, or a scene update.
        assertFalse(SliderDragState.shouldCommit(persisted = 0.4f, inFlight = 0.4f))
    }

    @Test
    fun `does not commit when there was no drag at all`() {
        assertFalse(SliderDragState.shouldCommit(persisted = 0.4f, inFlight = null))
    }

    @Test
    fun `commits at both ends of the range`() {
        assertTrue(SliderDragState.shouldCommit(persisted = 0.5f, inFlight = 0f))
        assertTrue(SliderDragState.shouldCommit(persisted = 0.5f, inFlight = 1f))
    }

    // --- shouldReleaseLocalValue --------------------------------------------------------------

    @Test
    fun `releases once the persisted value matches what was committed`() {
        assertTrue(SliderDragState.shouldReleaseLocalValue(persisted = 0.9f, awaitingCommit = 0.9f))
    }

    @Test
    fun `holds on while the persisted value is still stale`() {
        // Releasing here is what would make the thumb snap back for a few frames.
        assertFalse(SliderDragState.shouldReleaseLocalValue(persisted = 0.4f, awaitingCommit = 0.9f))
    }

    @Test
    fun `releases immediately when nothing is pending`() {
        // External changes -- theme switch, reset, random theme -- must reach the slider.
        assertTrue(SliderDragState.shouldReleaseLocalValue(persisted = 0.4f, awaitingCommit = null))
    }

    @Test
    fun `holds the newer commit when an older one arrives first`() {
        // Two quick drags: 0.9 committed, then 0.2 committed, then 0.9 arrives. Showing 0.9 now
        // would be a visible flicker backwards.
        assertFalse(SliderDragState.shouldReleaseLocalValue(persisted = 0.9f, awaitingCommit = 0.2f))
        assertTrue(SliderDragState.shouldReleaseLocalValue(persisted = 0.2f, awaitingCommit = 0.2f))
    }

    // --- Whole interaction ----------------------------------------------------------------------

    @Test
    fun `a full drag produces exactly one commit`() {
        // Walks the real sequence: many onValueChange ticks, one onValueChangeFinished.
        var persisted = 0.30f
        var inFlight: Float? = null
        var awaitingCommit: Float? = null
        var writes = 0

        val dragTicks = listOf(0.31f, 0.35f, 0.42f, 0.51f, 0.63f, 0.70f, 0.74f, 0.75f)
        for (tick in dragTicks) {
            inFlight = tick
            assertEquals(
                "thumb must follow the finger during the drag",
                tick,
                SliderDragState.displayValue(persisted, inFlight, awaitingCommit),
                0f,
            )
        }
        assertEquals("no write may happen mid-drag", 0, writes)

        // Finger lifts.
        if (SliderDragState.shouldCommit(persisted, inFlight)) {
            awaitingCommit = inFlight
            writes++
        }
        inFlight = null

        assertEquals("exactly one write per drag", 1, writes)
        assertEquals(
            "thumb must not snap back while the write is in flight",
            0.75f,
            SliderDragState.displayValue(persisted, inFlight, awaitingCommit),
            0f,
        )

        // The write lands and comes back through the flow.
        persisted = 0.75f
        assertTrue(SliderDragState.shouldReleaseLocalValue(persisted, awaitingCommit))
        awaitingCommit = null
        assertEquals(0.75f, SliderDragState.displayValue(persisted, inFlight, awaitingCommit), 0f)
    }

    @Test
    fun `a drag that returns to its starting value writes nothing`() {
        val persisted = 0.30f
        var inFlight: Float? = null
        var writes = 0
        for (tick in listOf(0.35f, 0.48f, 0.40f, 0.30f)) inFlight = tick
        if (SliderDragState.shouldCommit(persisted, inFlight)) writes++
        assertEquals(0, writes)
    }

    @Test
    fun `an external change while idle is shown immediately`() {
        // e.g. "Generate random theme" while the user is not touching the slider.
        val inFlight: Float? = null
        var awaitingCommit: Float? = null
        assertTrue(SliderDragState.shouldReleaseLocalValue(0.11f, awaitingCommit))
        awaitingCommit = null
        assertEquals(0.11f, SliderDragState.displayValue(0.11f, inFlight, awaitingCommit), 0f)
    }
}
