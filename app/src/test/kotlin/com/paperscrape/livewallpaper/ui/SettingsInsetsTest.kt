package com.paperscrape.livewallpaper.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that keeps the last row of a scrolled settings screen reachable.
 *
 * The measurement behind it is in [SettingsInsets]'s own documentation: on a Pixel 9 the dialog
 * that hosts every settings destination has a window frame of `[0,142][1079,2361]` -- 2219 px --
 * while content sized to the display is laid out 2423 px tall, so the last 204 px of every screen
 * would fall outside the window and be clipped. What is pinned here is the arithmetic that sizes
 * the content to the window, and that the trailing spacer does not carry the inset.
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
     * measured yet. Collapsing the screen would be worse than one frame laid out against the whole
     * display, so the display height is used unchanged.
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
     * Breathing room is a constant, not the inset's substitute: it stays small enough that it is
     * obviously not one, so it cannot drift into carrying the inset.
     */
    @Test
    fun `breathing room is a small constant, not a stand-in for the inset`() {
        assertEquals(24.dp, SettingsInsets.BOTTOM_BREATHING_ROOM)
        assertTrue(SettingsInsets.BOTTOM_BREATHING_ROOM > 0.dp)
        assertTrue(SettingsInsets.BOTTOM_BREATHING_ROOM < 48.dp)
    }

    /**
     * **The BV6600 (Android 10, 720 x 1440 at 2x): the dialog fills its window.** Its window is the space between the
     * status bar (24 dp) and the navigation bar (48 dp), 648 dp; `Configuration.screenHeightDp` there is already 648 dp,
     * the bars left out, so a height taken from it and less the bars again would be 576 dp -- a dark band of 72 px above
     * and below every settings screen. From the activity's window, 720 dp, it is 648.
     */
    @Test
    fun `on Android 10 the dialog is as tall as its window, the bars taken off once`() {
        assertEquals(648.dp, SettingsInsets.dialogHeight(activityWindowHeight = 720.dp, screenHeight = 648.dp, topInset = 24.dp, bottomInset = 48.dp))
        // The Pixel 9 (Android 16), where screenHeightDp is the whole display: 845 dp from either.
        assertEquals(845.dp, SettingsInsets.dialogHeight(activityWindowHeight = 923.dp, screenHeight = 923.dp, topInset = 54.dp, bottomInset = 24.dp))
        // The window's height not known yet: screenHeightDp stands in, for that frame.
        assertEquals(845.dp, SettingsInsets.dialogHeight(activityWindowHeight = 0.dp, screenHeight = 923.dp, topInset = 54.dp, bottomInset = 24.dp))
    }

    /** The dialog reads the activity's window, not `screenHeightDp`, as the height the bars come off. */
    @Test
    fun `the dialog's height is worked out from the activity's window`() {
        var dir: java.io.File? = java.io.File(".").absoluteFile
        var source: String? = null
        while (dir != null && source == null) {
            for (prefix in listOf("", "app/")) {
                val f = java.io.File(dir, "${prefix}src/main/kotlin/com/paperscrape/livewallpaper/ui/SettingsInsets.kt")
                if (f.isFile) source = f.readText()
            }
            dir = dir.parentFile
        }
        val code = source!!.lines().filterNot { it.trim().startsWith("*") || it.trim().startsWith("//") }.joinToString("\n")
        assertTrue(code.contains("LocalSettingsWindowHeight provides LocalWindowInfo.current.containerDpSize.height,"))
        assertTrue(code.contains("activityWindowHeight = LocalSettingsWindowHeight.current,"))
        assertFalse("screenHeightDp as the height the bars come off", code.contains("displayHeight = LocalConfiguration.current.screenHeightDp.dp"))
    }
}
