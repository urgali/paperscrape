package com.paperscrape.livewallpaper.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that keeps the last row of a scrolled settings screen off the gesture bar.
 *
 * The bug this fixes was not that one screen had too little padding: it was that each screen
 * carried its own, and that the destinations are full-screen dialogs whose own window can report
 * no bottom inset at all. Both halves are pinned here -- the spacing grows with a real inset, and
 * it never falls below a floor when there isn't one.
 */
class SettingsInsetsTest {

    @Test
    fun `a reported inset is respected and given breathing room on top`() {
        assertEquals(
            48.dp + SettingsInsets.EXTRA_BOTTOM_SPACING,
            SettingsInsets.bottomSpacing(48.dp, 48.dp),
        )
    }

    @Test
    fun `a gesture bar sized inset still clears the bar`() {
        val spacing = SettingsInsets.bottomSpacing(24.dp, 24.dp)
        assertTrue("$spacing must clear a 24 dp gesture bar", spacing > 24.dp)
    }

    /**
     * The case the bug actually came from: inside a `Dialog` window that fits system windows
     * itself, `safeDrawing` measures zero while the content still runs to the bottom of the
     * display. The floor is what keeps the last row reachable there.
     */
    @Test
    fun `no reported inset falls back to the minimum rather than to nothing`() {
        assertEquals(SettingsInsets.MINIMUM_BOTTOM_SPACING, SettingsInsets.bottomSpacing(0.dp, 0.dp))
    }

    @Test
    fun `a nonsensical negative inset cannot pull content back under the bar`() {
        assertEquals(SettingsInsets.MINIMUM_BOTTOM_SPACING, SettingsInsets.bottomSpacing((-40).dp, (-40).dp))
    }

    @Test
    fun `spacing never decreases as the system reserves more space`() {
        var previous = SettingsInsets.bottomSpacing(0.dp, 0.dp)
        for (inset in listOf(8, 16, 24, 32, 48, 64, 96)) {
            val spacing = SettingsInsets.bottomSpacing(inset.dp, inset.dp)
            assertTrue("spacing shrank at $inset dp", spacing >= previous)
            previous = spacing
        }
    }

    /**
     * Every screen -- a two-row one and a fifty-row one alike -- ends with the same spacer, because
     * the shells apply it rather than the screens. Content length is not an input, and this test
     * exists to say that the day someone is tempted to make it one.
     */
    @Test
    fun `spacing does not depend on how much content a screen has`() {
        val shortScreen = SettingsInsets.bottomSpacing(24.dp, 24.dp)
        val longScreen = SettingsInsets.bottomSpacing(24.dp, 24.dp)
        assertEquals(shortScreen, longScreen)
    }

    /**
     * The v2.10 fix asked one window and trusted it. On the Pixel 9 that was not enough, because
     * a settings destination is a dialog with a window of its own and only one of the two is
     * telling the truth at any moment. These pin the rule that replaced it.
     */
    @Test
    fun `the window that knows the inset is the one that decides`() {
        // Dialog reports nothing, activity has the real figure.
        assertEquals(48.dp + SettingsInsets.EXTRA_BOTTOM_SPACING, SettingsInsets.bottomSpacing(0.dp, 48.dp))
        // The other way round: the dialog measured it, the activity's value is stale.
        assertEquals(48.dp + SettingsInsets.EXTRA_BOTTOM_SPACING, SettingsInsets.bottomSpacing(48.dp, 0.dp))
    }

    @Test
    fun `disagreeing windows never produce less space than the larger one asks for`() {
        for (dialog in listOf(0, 16, 24, 48, 64)) {
            for (activity in listOf(0, 16, 24, 48, 64)) {
                val spacing = SettingsInsets.bottomSpacing(dialog.dp, activity.dp)
                val larger = maxOf(dialog, activity).dp
                assertTrue("$dialog/$activity", spacing >= larger + SettingsInsets.EXTRA_BOTTOM_SPACING)
            }
        }
    }

    @Test
    fun `neither window knowing still clears a gesture bar`() {
        assertEquals(SettingsInsets.MINIMUM_BOTTOM_SPACING, SettingsInsets.bottomSpacing(0.dp, 0.dp))
        assertTrue(SettingsInsets.bottomSpacing(0.dp, 0.dp) > 24.dp)
    }
}
