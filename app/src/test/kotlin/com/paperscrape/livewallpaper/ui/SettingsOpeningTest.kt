package com.paperscrape.livewallpaper.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * **What an opening of the settings does, it does at every opening.** The settings' Activity outlives an opening -- Home
 * leaves it in the background, and from Android 12 Back does too -- in the wallpaper's process, which lives for days, so
 * a composition is not an opening:
 *
 *  - the date the screen reads (the calendar's theme of today, "Today: ...", the Holiday calendar's window of today,
 *    the theme Seasons and World & scene edit) is read again each time the settings come to the front and at
 *    midnight: read once, settings left open over the midnight of the 31st of October would say Halloween, and their
 *    edits would go to Halloween while the wallpaper shows Autumn; the Holiday calendar, open over that midnight, would
 *    still draw Halloween as today's window;
 *  - *Check for updates when I open PaperScrape* checks at each opening, as its line says ("each time you open these
 *    settings"), an answer of less than a minute standing for a check: each time they come to the front, but the
 *    return from a page of another app they started themselves -- the browser, the file picker, the installer -- which
 *    would put the update dialog up again in the middle of what the user was doing ([SettingsOpenings], whose rule
 *    `ReturnFromAPageIsNotAnOpeningTest` runs).
 *
 * The rule of the midnight is [SettingsUiModel.millisUntilNextDay]; that the screens read it, the date and the
 * openings, at each ON_START, and that the Activity marks every page it starts, is asserted on their source, as the
 * project does for Compose code (`MoonPhaseControlTest`), whole lines with the comments taken off.
 */
class SettingsOpeningTest {

    private val rome = ZoneId.of("Europe/Rome")

    @Test
    fun `the date is read again at the next midnight, a day of 25 or 23 hours included`() {
        assertEquals(60_000L, SettingsUiModel.millisUntilNextDay(ZonedDateTime.of(2026, 10, 31, 23, 59, 0, 0, rome)))
        assertEquals(24 * 3_600_000L, SettingsUiModel.millisUntilNextDay(ZonedDateTime.of(2026, 10, 31, 0, 0, 0, 0, rome)))
        // The clocks go back in the night of 25 October 2026: that day has 25 hours.
        assertEquals(25 * 3_600_000L, SettingsUiModel.millisUntilNextDay(ZonedDateTime.of(2026, 10, 25, 0, 0, 0, 0, rome)))
        // And forward on 28 March 2027: 23 hours.
        assertEquals(23 * 3_600_000L, SettingsUiModel.millisUntilNextDay(ZonedDateTime.of(2027, 3, 28, 0, 0, 0, 0, rome)))
        // A moment before midnight: never 0, which would wake the wait for nothing.
        assertEquals(1L, SettingsUiModel.millisUntilNextDay(ZonedDateTime.of(2026, 10, 31, 23, 59, 59, 999_999_999, rome)))
    }

    @Test
    fun `the screen reads the date each time it comes to the front, and checks for updates at each opening`() {
        val homeLines = lines("ui/SettingsScreen.kt")
        // The date: a state read at each ON_START and at midnight, and the one the calendar's theme and label are read for.
        assertTrue(homeLines.contains("var today by remember { mutableStateOf(LocalDate.now()) }"))
        assertTrue(homeLines.contains("if (event == Lifecycle.Event.ON_START) today = LocalDate.now()"))
        // The openings: counted where the rule says, and nowhere else.
        assertTrue(homeLines.contains("if (settingsOpenings.isOpening(event, screenOn = power?.isInteractive != false)) openings++"))
        assertEquals(1, homeLines.count { "openings++" in it })
        assertTrue(homeLines.contains("while (LocalDate.now() == today) delay(SettingsUiModel.millisUntilNextDay(ZonedDateTime.now()))"))
        assertEquals(
            "the calendar's theme and label are read for the screen's date",
            listOf(
                "SeasonalThemeRules.themeForDate(date = today, calendar = settings.seasonalCalendar)",
                "val label = SeasonalThemeRules.labelForDate(date = today, calendar = settings.seasonalCalendar)",
            ),
            homeLines.filter { "SeasonalThemeRules.themeForDate(" in it || "SeasonalThemeRules.labelForDate(" in it },
        )
        // The check: run again at each opening, as an opening -- a tapped notification asked once.
        assertTrue(homeLines.contains("key(openings) { LaunchedEffect(settings.automaticUpdateCheckEnabled.takeIf { openUpdateForTag == null }, openUpdateForTag) {"))
        assertTrue(homeLines.contains("val askedByNotification = openUpdateForTag != null && openings == 0"))
        assertTrue(homeLines.contains("takeRecentAnswer = !askedByNotification,"))
    }

    @Test
    fun `every page the settings start is marked before it starts, and unmarked when it cannot`() {
        val activity = lines("ui/SettingsActivity.kt")
        val at = activity.indexOf("override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {")
        assertTrue("the Activity's one way out to another app's page", at > 0)
        assertEquals(
            listOf(
                "settingsOpenings.pageStarting()",
                "try {",
                "super.startActivityForResult(intent, requestCode, options)",
                "} catch (failure: Throwable) {",
                "settingsOpenings.pageNotStarted()",
                "throw failure",
                "}",
                "}",
            ),
            activity.subList(at + 1, at + 9),
        )
        assertTrue("the screen is handed the same rule", activity.contains("settingsOpenings = settingsOpenings,"))
    }

    @Test
    fun `the holiday calendar draws the window of the screen's date, not of a clock it read once`() {
        val calendar = lines("ui/HolidayCalendarScreen.kt")
        assertEquals("the page reads no clock of its own", emptyList<String>(), calendar.filter { "LocalDate.now()" in it })
        assertTrue(calendar.contains("today: LocalDate,"))
        assertTrue(calendar.contains("val activeWindow = SeasonalThemeRules.windowForDate(today, calendar)"))
        assertTrue("Easter's year is the date's", calendar.contains("year = today.year,"))
        // And the screen hands it the date it reads again at midnight and at each ON_START.
        val home = lines("ui/SettingsScreen.kt")
        val at = home.indexOf("SettingsDestination.CALENDAR -> HolidayCalendarScreen(")
        assertTrue(at > 0)
        assertTrue(home.drop(at).takeWhile { it != ")" }.contains("today = today,"))
    }

    /**
     * The launcher icon an opening puts right is chosen by the stored calendar, read once, and not at all while the store
     * cannot be read: the settings' flow answers such a read with a fresh install's calendar, which would put on the icon
     * of a season the user's calendar does not have.
     */
    @Test
    fun `an opening chooses the icon by the calendar as stored, and leaves it while the store cannot be read`() {
        val activity = lines("ui/SettingsActivity.kt")
        val at = activity.indexOf("val settings = prefs.readSettings() ?: return@runCatching")
        assertTrue("the icon's read at an opening", at > 0)
        assertEquals("LauncherIconSwitch.applyForDate(applicationContext, calendar = settings.seasonalCalendar)", activity[at + 1])
        assertEquals("no read of the settings' flow, whose first value stands in for one that failed", emptyList<String>(),
            activity.filter { "settingsFlow.first()" in it })
    }

    /** The file's code lines, trimmed: its comment lines taken out, and a comment at the end of a line cut off. */
    private fun lines(path: String): List<String> {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/$path"
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = java.io.File(dir, "$prefix$suffix")
                if (f.isFile) {
                    return f.readLines().map { it.trim() }
                        .filterNot { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") }
                        .map { it.substringBefore(" //").trim() }
                }
            }
            dir = dir.parentFile
        }
        error("no $suffix")
    }
}
