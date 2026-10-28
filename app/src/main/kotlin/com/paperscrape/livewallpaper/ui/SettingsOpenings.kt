package com.paperscrape.livewallpaper.ui

import androidx.lifecycle.Lifecycle

/**
 * **Which of the settings' comings to the front are openings**: the moments *Check for updates when I open PaperScrape*
 * checks again ("each time you open these settings", the line under it says).
 *
 * The settings' Activity outlives an opening -- Home leaves it in the background, and from Android 12 Back does too, in
 * the wallpaper's process that lives for days -- so an opening is an ON_START, not a composition. But the settings also
 * leave the screen for pages of other apps they start themselves: the browser (*Open release page*, *Source code on
 * GitHub*), the file picker and the place to save (the imports and exports), the system's installer and its page that
 * allows installs, the phone's own settings pages, the wallpaper picker (*Set as wallpaper*). Coming back from one of
 * them ends a visit the user started from the settings; it is not an opening. Counted as one, the check would run on
 * the way back and put the update dialog up again in the middle of what the user was doing: an install, an import,
 * reading the release's page.
 *
 * So `SettingsActivity` calls [pageStarting] just before it starts a page (every start of the Activity's goes through
 * one method there), and [pageNotStarted] when the start throws; [isOpening] is fed every lifecycle event of the
 * Activity and says which ON_START is an opening:
 *  - not the first one: that is the opening the screen comes up in, and the screen's composition checks for it itself;
 *  - not an ON_START that follows an ON_STOP the settings went into while a page was starting;
 *  - not an ON_START that follows an ON_STOP the settings went into with the screen turned off: the screen turned on
 *    again shows the settings the user left in front, it does not open them;
 *  - every other one: from the launcher icon or the recent apps after Home, after another app came to the front by
 *    itself (a call, an alarm).
 *
 * A page that covers the settings without hiding them -- a chooser, a dialog-shaped installer -- pauses them and does
 * not stop them, and closing it resumes them: an ON_RESUME after the ON_PAUSE that page caused ends the start there, so
 * the next ON_STOP (Home, say) is an ordinary leaving. Only after that ON_PAUSE: a page can be started while the
 * settings are not yet resumed -- from the answer of a page or a permission dialog, delivered before ON_RESUME (the
 * installer after its permission page, the phone's location page after a refusal) -- and the ON_RESUME that follows
 * comes before the page does.
 *
 * What it cannot tell apart: leaving a page the settings started for Home, then coming back to the settings from the
 * launcher icon or the recent apps, is the same ON_START as Back from that page, and is not an opening; the check runs
 * at the next one.
 *
 * Pure, with nothing of Compose or of the Android framework in it ([Lifecycle.Event] is a plain Kotlin enum), so the
 * JVM tests run it.
 */
class SettingsOpenings {

    /** The first ON_START has been seen: the opening the screen came up in. */
    private var cameUp = false

    /** A page has been started and has not yet stopped the settings, nor gone without stopping them. */
    private var startingPage = false

    /** The settings paused while that page was starting: an ON_RESUME now means it went without stopping them. */
    private var pausedForPage = false

    /** The settings stopped for a page they started: the next ON_START ends that visit. */
    private var awayOnPage = false

    /** The settings stopped with the screen off: the next ON_START is the screen turned on again. */
    private var stoppedAsleep = false

    /** The settings are about to start a page of another app. */
    fun pageStarting() {
        startingPage = true
    }

    /** The start of that page threw: no page came, and none will end a visit. */
    fun pageNotStarted() {
        startingPage = false
    }

    /**
     * Feeds one lifecycle event of the settings' Activity; true for an ON_START that is an opening. [screenOn] is whether
     * the screen is on as the event arrives (`PowerManager.isInteractive`), read by the caller: only an ON_STOP's is used.
     */
    fun isOpening(event: Lifecycle.Event, screenOn: Boolean = true): Boolean {
        when (event) {
            Lifecycle.Event.ON_START -> {
                val opening = cameUp && !awayOnPage && !stoppedAsleep
                cameUp = true
                awayOnPage = false
                stoppedAsleep = false
                return opening
            }
            Lifecycle.Event.ON_PAUSE -> pausedForPage = startingPage
            Lifecycle.Event.ON_RESUME -> {
                if (pausedForPage) startingPage = false
                pausedForPage = false
            }
            Lifecycle.Event.ON_STOP -> {
                awayOnPage = startingPage
                stoppedAsleep = !screenOn
                startingPage = false
                pausedForPage = false
            }
            else -> Unit
        }
        return false
    }
}
