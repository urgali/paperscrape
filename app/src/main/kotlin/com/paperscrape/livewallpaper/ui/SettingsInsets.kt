package com.paperscrape.livewallpaper.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How much empty space a scrolling settings screen leaves under its last row.
 *
 * Pure, and separated from the Compose layer, because the rule is worth stating exactly: it is the
 * system's own bottom inset (gesture bar or navigation bar) plus a constant of breathing room, and
 * it never drops below [MINIMUM_BOTTOM_SPACING] however small the reported inset is.
 *
 * The floor is the part that matters. Every settings destination is a full-screen `Dialog`, and a
 * dialog has its own window: unless that window is explicitly told otherwise it fits system
 * windows itself, so `WindowInsets.safeDrawing` measured *inside* it can legitimately report zero
 * while the content still runs to the bottom of the display. That is the shape of the bug this
 * exists to fix -- the last row of a scrolled-to-the-bottom screen sitting half under the gesture
 * bar -- and a floor is what makes the fix hold even where the inset does not arrive.
 */
object SettingsInsets {

    /** Breathing room under the last row, on top of whatever the system reserves. */
    val EXTRA_BOTTOM_SPACING: Dp = 24.dp

    /**
     * The least space any screen leaves, used when the reported inset is zero or implausibly
     * small. Sized to clear a gesture bar (about 24 dp) with the same 24 dp of breathing room
     * above it that a screen gets on a device that reports its insets properly.
     */
    val MINIMUM_BOTTOM_SPACING: Dp = 48.dp

    /**
     * The spacer height, given what **both** windows report.
     *
     * v2.10 asked one window and trusted the answer, with a floor for when it came back zero. On
     * the Pixel 9 that was not enough, and the reason is that neither window is reliable on its
     * own: a settings destination is a `Dialog` with a window of its own, and depending on whether
     * that window fits system windows, *either* it reports the gesture inset and the activity's
     * value is stale, *or* it reports zero while the activity holds the real figure. Taking the
     * larger of the two removes the guess: whichever window knows, its answer is used, and a floor
     * is only reached when neither does.
     *
     * That is also why the floor was not simply raised. A bigger constant would have papered over
     * the case where the inset *is* known and merely ignored, and would have left a visible gap on
     * devices that report nothing at all.
     *
     * Negative values cannot come from the platform but are clamped rather than trusted: a
     * negative spacer would pull content back under the bar, which is the failure being fixed.
     */
    fun bottomSpacing(dialogBottomInset: Dp, activityBottomInset: Dp): Dp {
        val dialog = if (dialogBottomInset < 0.dp) 0.dp else dialogBottomInset
        val activity = if (activityBottomInset < 0.dp) 0.dp else activityBottomInset
        val inset = if (dialog > activity) dialog else activity
        val requested = inset + EXTRA_BOTTOM_SPACING
        return if (requested < MINIMUM_BOTTOM_SPACING) MINIMUM_BOTTOM_SPACING else requested
    }
}

/**
 * The bottom inset as measured by the **activity's** window, made available to the settings
 * dialogs.
 *
 * The dialogs cannot measure it for themselves (see [SettingsInsets]), so it is read once where it
 * is real -- in the activity's own composition -- and passed down. Defaults to zero, which
 * [SettingsInsets.bottomSpacing] turns into the minimum rather than into nothing.
 */
val LocalSettingsBottomInset: ProvidableCompositionLocal<Dp> = compositionLocalOf { 0.dp }

/** Reads the real window inset and provides it to everything below, dialogs included. */
@Composable
fun ProvideSettingsBottomInset(content: @Composable () -> Unit) {
    val inset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    CompositionLocalProvider(LocalSettingsBottomInset provides inset, content = content)
}
