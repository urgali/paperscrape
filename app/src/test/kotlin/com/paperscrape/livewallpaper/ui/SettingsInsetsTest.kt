package com.paperscrape.livewallpaper.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that keeps the last row of a scrolled settings screen reachable.
 *
 * The measurement behind it is in [SettingsInsets]'s own documentation: on a Pixel 9 the dialog
 * that hosts every settings destination has a window frame of `[0,142][1079,2361]` -- 2219 px --
 * while its content is measured against the 2424 px display, so 204 px of every screen was laid
 * out outside the window and clipped. What is pinned here is the arithmetic that sizes the content
 * to the window instead, and the fact that the trailing spacer is no longer carrying the inset.
 */
class SettingsInsetsTest {

    /** The Pixel 9 figures, in dp: 2424 px / 2.625 = 923 dp, 142 px = 54 dp, 63 px = 24 dp. */
    @Test
    fun `the measured Pixel 9 case reproduces the window frame`() {
        val height = SettingsInsets.safeAreaHeight(
            displayHeight = 923.dp,
            topInset = 54.dp,
            bottomInset = 24.dp,
        )
        assertEquals(845.dp, height)
    }

    @Test
    fun `both insets are subtracted`() {
        assertEquals(700.dp, SettingsInsets.safeAreaHeight(800.dp, topInset = 60.dp, bottomInset = 40.dp))
    }

    /**
     * The first composition pass reports nothing, because the activity's window has not been
     * measured yet. Collapsing the screen would be worse than one frame of the pre-fix layout, so
     * the display height is used unchanged.
     */
    @Test
    fun `insets that are not known yet leave the height alone`() {
        assertEquals(923.dp, SettingsInsets.safeAreaHeight(923.dp, topInset = 0.dp, bottomInset = 0.dp))
    }

    @Test
    fun `a negative inset is ignored rather than added back`() {
        assertEquals(900.dp, SettingsInsets.safeAreaHeight(923.dp, topInset = (-10).dp, bottomInset = 23.dp))
    }

    /** Insets larger than the display cannot happen, but a negative height must not escape. */
    @Test
    fun `an impossible pair never produces a negative height`() {
        val height = SettingsInsets.safeAreaHeight(100.dp, topInset = 200.dp, bottomInset = 200.dp)
        assertTrue(height >= 0.dp)
    }

    @Test
    fun `the height never exceeds the display`() {
        for (top in 0..120 step 8) {
            for (bottom in 0..120 step 8) {
                val height = SettingsInsets.safeAreaHeight(923.dp, top.dp, bottom.dp)
                assertTrue("$top/$bottom", height <= 923.dp)
            }
        }
    }

    /** More inset, less content. Stated as a property because both edges feed the same subtraction. */
    @Test
    fun `the height never grows as an inset grows`() {
        var previous = SettingsInsets.safeAreaHeight(923.dp, 0.dp, 24.dp)
        for (top in 1..120) {
            val height = SettingsInsets.safeAreaHeight(923.dp, top.dp, 24.dp)
            assertTrue("top $top", height <= previous)
            previous = height
        }
    }

    /**
     * Breathing room is a constant now. The regression this guards against is it drifting back
     * into being the inset's substitute, which is what it was in v2.10 and v2.12: it must stay
     * small enough that it is obviously not one.
     */
    @Test
    fun `breathing room is a small constant, not a stand-in for the inset`() {
        assertEquals(24.dp, SettingsInsets.BOTTOM_BREATHING_ROOM)
        assertTrue(SettingsInsets.BOTTOM_BREATHING_ROOM > 0.dp)
        assertTrue(SettingsInsets.BOTTOM_BREATHING_ROOM < 48.dp)
    }
}
