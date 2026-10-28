package com.paperscrape.livewallpaper.ui

import androidx.lifecycle.Lifecycle.Event
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **Coming back to the settings from a page they started is not an opening; every other coming to the front is**
 * ([SettingsOpenings]). *Check for updates when I open PaperScrape* checks again at each opening: counted as one, the
 * return from the browser of *Open release page*, from the file picker or from the installer, a minute or more after
 * GitHub's last answer, asks GitHub again and puts the update dialog up again in the middle of what the user was doing.
 *
 * The events are the ones Android sends the settings' Activity, in its order: the screen comes up with ON_CREATE,
 * ON_START and ON_RESUME (the composition's observer is handed the three at once), a full-screen page or Home stops
 * the settings, a page shaped as a dialog only pauses them, and the answer of a page or of a permission dialog is
 * delivered before ON_RESUME.
 */
class ReturnFromAPageIsNotAnOpeningTest {

    private val comeUp = listOf(Event.ON_CREATE, Event.ON_START, Event.ON_RESUME)
    private val leave = listOf(Event.ON_PAUSE, Event.ON_STOP)
    private val comeBack = listOf(Event.ON_START, Event.ON_RESUME)

    /** Feeds [events] in order; for each ON_START among them, whether it was an opening. */
    private fun SettingsOpenings.feed(events: List<Event>): List<Boolean> =
        events.mapNotNull { event -> isOpening(event).takeIf { event == Event.ON_START } }

    @Test
    fun `the screen coming up is not counted, and each coming back from Home or the recent apps is an opening`() {
        val openings = SettingsOpenings()
        assertEquals(listOf(false), openings.feed(comeUp))
        assertEquals(listOf(true), openings.feed(leave + comeBack))
        assertEquals(listOf(true), openings.feed(leave + comeBack))
    }

    @Test
    fun `the screen turned off and on with the settings in front is not an opening, and the next coming back is`() {
        val openings = SettingsOpenings()
        openings.feed(comeUp)
        // The screen goes off: the settings pause and stop with it.
        openings.isOpening(Event.ON_PAUSE, screenOn = false)
        openings.isOpening(Event.ON_STOP, screenOn = false)
        assertEquals("the screen on again", listOf(false), openings.feed(comeBack))
        assertEquals("then Home, and back from the launcher", listOf(true), openings.feed(leave + comeBack))
    }

    @Test
    fun `the return from a page the settings started is not an opening, and the next coming back is`() {
        val openings = SettingsOpenings()
        openings.feed(comeUp)
        openings.pageStarting()
        assertEquals("back from the browser", listOf(false), openings.feed(leave + comeBack))
        assertEquals("then Home, and back from the launcher", listOf(true), openings.feed(leave + comeBack))
    }

    @Test
    fun `a chooser that hands over to a full page is a visit too`() {
        // "Open with": the sheet pauses the settings, the browser chosen in it stops them.
        val openings = SettingsOpenings()
        openings.feed(comeUp)
        openings.pageStarting()
        assertEquals(emptyList<Boolean>(), openings.feed(listOf(Event.ON_PAUSE)))
        assertEquals(listOf(false), openings.feed(listOf(Event.ON_STOP) + comeBack))
    }

    @Test
    fun `a page that only paused the settings leaves the next leaving an ordinary one`() {
        // A dialog-shaped installer, cancelled: the settings were paused and resumed, never stopped.
        val openings = SettingsOpenings()
        openings.feed(comeUp)
        openings.pageStarting()
        assertEquals(emptyList<Boolean>(), openings.feed(listOf(Event.ON_PAUSE, Event.ON_RESUME)))
        assertEquals("then Home, and back", listOf(true), openings.feed(leave + comeBack))
    }

    @Test
    fun `a page no app could start leaves the next leaving an ordinary one`() {
        val openings = SettingsOpenings()
        openings.feed(comeUp)
        openings.pageStarting()
        openings.pageNotStarted()
        assertEquals(listOf(true), openings.feed(leave + comeBack))
    }

    @Test
    fun `a page started before the settings are resumed is a visit too`() {
        val openings = SettingsOpenings()
        openings.feed(comeUp)
        // The page that allows installs; back from it, its answer starts the installer after ON_START and before the
        // ON_RESUME, which comes before the installer does.
        openings.pageStarting()
        assertEquals("back from the permission page", listOf(false), openings.feed(leave + Event.ON_START))
        openings.pageStarting()
        assertEquals(emptyList<Boolean>(), openings.feed(listOf(Event.ON_RESUME)))
        assertEquals("back from the installer", listOf(false), openings.feed(leave + comeBack))
        // A permission dialog only pauses the settings; a refusal's answer opens the phone's page before the ON_RESUME.
        openings.feed(listOf(Event.ON_PAUSE))
        openings.pageStarting()
        assertEquals(emptyList<Boolean>(), openings.feed(listOf(Event.ON_RESUME)))
        assertEquals("back from the phone's page", listOf(false), openings.feed(leave + comeBack))
        assertEquals("then Home, and back", listOf(true), openings.feed(leave + comeBack))
    }
}
