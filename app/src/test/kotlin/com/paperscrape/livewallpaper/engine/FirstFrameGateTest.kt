package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An engine draws nothing until the user's settings and saved themes have both arrived, or until it
 * has waited [FirstFrameGate.WAIT_LIMIT_MS] for them; and it is told once (v5.10B).
 */
class FirstFrameGateTest {

    @Test
    fun `the gate opens when both have arrived, in either order, and not before`() {
        for (settingsFirst in listOf(true, false)) {
            var opened = 0
            val gate = FirstFrameGate { opened++ }
            assertFalse(gate.isOpen)
            if (settingsFirst) gate.settingsArrived() else gate.savedThemesArrived()
            assertFalse("one of the two is not enough: the scene would still be the default one", gate.isOpen)
            assertEquals(0, opened)
            if (settingsFirst) gate.savedThemesArrived() else gate.settingsArrived()
            assertTrue(gate.isOpen)
            assertEquals(1, opened)
        }
    }

    @Test
    fun `waiting too long opens it with whatever has arrived`() {
        var opened = 0
        val gate = FirstFrameGate { opened++ }
        gate.settingsArrived()
        gate.waitedTooLong()
        assertTrue(gate.isOpen)
        assertEquals(1, opened)
    }

    @Test
    fun `it opens once, and later emissions and the timeout do not start the frames again`() {
        var opened = 0
        val gate = FirstFrameGate { opened++ }
        gate.settingsArrived()
        gate.savedThemesArrived()
        gate.settingsArrived()
        gate.savedThemesArrived()
        gate.waitedTooLong()
        assertEquals(1, opened)
        assertTrue(gate.isOpen)
    }
}
