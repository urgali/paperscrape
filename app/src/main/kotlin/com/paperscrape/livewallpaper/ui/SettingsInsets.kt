package com.paperscrape.livewallpaper.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How a settings destination is sized so that its last row is reachable.
 *
 * **What was actually wrong.** Not padding, and not an inset that arrived too small. Every settings
 * destination is a full-screen `Dialog`, and `dumpsys window` on the Pixel 9 (Android 16, gesture
 * navigation, 1080x2424 at 2.625x) reported this for it:
 *
 * ```
 * mAttrs={(0,0)(1079x2423) gr=CENTER ... fitTypes=statusBars navigationBars captionBar systemOverlays}
 * Frames: frame=[0,142][1079,2361]
 * ```
 *
 * The window's *frame* is 2219 px tall -- the display minus the status bar (142 px) and the gesture
 * bar (63 px), which is exactly right. But its layout parameters ask for 2423 px, because with
 * `usePlatformDefaultWidth = false` Compose measures the dialog's content against the **display**
 * rather than against that frame. So `Modifier.fillMaxSize()` inside the dialog laid the content
 * out 2423 px tall in a 2219 px window, and the last **204 px of every settings screen were
 * positioned outside the window** and clipped by it.
 *
 * That is why the previous two attempts could not work. The last rows were never under the gesture
 * bar; they were outside the window, and no trailing spacer inside the content can move something
 * back into a window it has overflowed. It also explains the symptom exactly: scrolling reached the
 * end of the content and the end of the content was still off-window.
 *
 * The measurement that makes this unambiguous: inside the dialog, `WindowInsets.safeDrawing`
 * reports 0 on every edge -- which is *correct*, since the window already fits the system bars --
 * while the same UI hosted by the activity reports 63 px at the bottom. Two different windows, two
 * correct answers, and content sized for the wrong one.
 *
 * **The fix** is to size the dialog's content to the area its window actually occupies:
 * [safeAreaHeight], the display height less the insets the *activity* measured. The activity is
 * the right source because its window does cover the display and does report them. The scaffold
 * inside then reserves the dialog's own insets -- zero here, non-zero on any device whose dialog
 * window is full-bleed instead -- so both window arrangements are handled without asking which one
 * this is.
 *
 * The trailing spacer goes back to being what its name says: breathing room, a constant.
 */
object SettingsInsets {

    /**
     * Empty space under the last row.
     *
     * A constant now, not a function of the inset. The inset is accounted for by the height the
     * content is given; this only stops the last row sitting flush against the bottom edge.
     */
    val BOTTOM_BREATHING_ROOM: Dp = 24.dp

    /**
     * The height a full-screen settings dialog's content should be given.
     *
     * Pure, and kept out of the Compose layer, because it is the whole fix in one line and the
     * arithmetic is what has to be right: the display less what the system bars take at each end.
     *
     * Returns [displayHeight] unchanged when the insets are not known yet (both zero, which is
     * what the first composition pass reports before the activity's window has been measured):
     * that is the pre-fix behaviour for one frame, and it is better than collapsing the screen to
     * nothing. Never returns a negative height, whatever is passed in.
     */
    fun safeAreaHeight(displayHeight: Dp, topInset: Dp, bottomInset: Dp): Dp {
        val top = if (topInset > 0.dp) topInset else 0.dp
        val bottom = if (bottomInset > 0.dp) bottomInset else 0.dp
        val height = displayHeight - top - bottom
        return if (height > 0.dp) height else displayHeight.coerceAtLeast(0.dp)
    }
}

/**
 * The insets as measured by the **activity's** window, made available to the settings dialogs.
 *
 * The dialogs cannot measure them for themselves -- their window fits the system bars and so
 * correctly reports none -- and they need the figures anyway in order to know how tall that window
 * is. See [SettingsInsets]. Zero by default, which [SettingsInsets.safeAreaHeight] reads as "not
 * known yet".
 */
val LocalSettingsBottomInset: ProvidableCompositionLocal<Dp> = compositionLocalOf { 0.dp }

/** The activity's top inset, for the same reason as [LocalSettingsBottomInset]. */
val LocalSettingsTopInset: ProvidableCompositionLocal<Dp> = compositionLocalOf { 0.dp }

/** Reads the real window insets and provides them to everything below, dialogs included. */
@Composable
fun ProvideSettingsBottomInset(content: @Composable () -> Unit) {
    val padding = WindowInsets.safeDrawing.asPaddingValues()
    CompositionLocalProvider(
        LocalSettingsBottomInset provides padding.calculateBottomPadding(),
        LocalSettingsTopInset provides padding.calculateTopPadding(),
        content = content,
    )
}

/**
 * The height to give a settings dialog's content: [SettingsInsets.safeAreaHeight] applied to this
 * display and to the insets the activity measured.
 *
 * `Configuration.screenHeightDp` is the display's own height on an edge-to-edge-enforced target
 * (923 dp of the Pixel 9's 2424 px at 2.625x), which is what the dialog's content would otherwise
 * expand to fill.
 */
@Composable
internal fun settingsDialogHeight(): Dp = SettingsInsets.safeAreaHeight(
    displayHeight = LocalConfiguration.current.screenHeightDp.dp,
    topInset = LocalSettingsTopInset.current,
    bottomInset = LocalSettingsBottomInset.current,
)
