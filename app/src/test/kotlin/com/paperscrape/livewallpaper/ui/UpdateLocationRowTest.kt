package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.location.DeviceLocationAccess
import com.paperscrape.livewallpaper.location.DeviceLocationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **The rows the Location section gained, as rules** (decision 40's way: what a row shows is a pure function of
 * [SettingsUiModel], asserted here over every state; that the *Weather & time* page draws from them is asserted on its
 * source, as the project does for Compose it cannot run on the JVM):
 *
 * - *Update location now*, under the position, only while GPS or Network works ([SettingsUiModel.updateLocationRow]);
 * - *Update weather now*, under a Custom place, only with Live Weather on ([SettingsUiModel.updateWeatherLine]);
 * - *GPS needs your precise location*, after GPS was refused because the phone gave only the approximate location
 *   ([SettingsUiModel.refusedForApproximateOnly], [SettingsUiModel.gpsNeedsPreciseLine]);
 * - the position's age ([SettingsUiModel.positionAge], [SettingsUiModel.devicePositionLine]).
 */
class UpdateLocationRowTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    private fun row(mode: LocationMode, access: DeviceLocationAccess) = SettingsUiModel.deviceLocationRow(mode, access)

    // ------------------------------------------------------------------ Update location now

    @Test
    fun `Update location now shows only while GPS or Network works, with Live Weather on or off`() {
        for (mode in LocationMode.entries) {
            for (access in DeviceLocationAccess.entries) {
                val deviceRow = row(mode, access)
                for (liveWeatherOn in listOf(true, false)) {
                    val shown = SettingsUiModel.updateLocationRow(deviceRow, DeviceLocationKind.GPS, UpdateLocationTap.IDLE, liveWeatherOn, hasPosition = true)
                    val works = (mode == LocationMode.GPS || mode == LocationMode.NETWORK) && access == DeviceLocationAccess.ALLOWED
                    assertEquals("$mode $access Live Weather $liveWeatherOn", works, shown != null)
                }
            }
        }
        // Not working: the Location row already says what is missing (decision 40), whatever the tap's state.
        for (tap in UpdateLocationTap.entries) {
            assertNull(SettingsUiModel.updateLocationRow(row(LocationMode.GPS, DeviceLocationAccess.LOCATION_OFF), DeviceLocationKind.GPS, tap, true, hasPosition = true))
            assertNull(SettingsUiModel.updateLocationRow(null, DeviceLocationKind.NETWORK, tap, true, hasPosition = true))
        }
    }

    @Test
    fun `each state of the tap has its title and its line, word for word`() {
        val gpsRow = row(LocationMode.GPS, DeviceLocationAccess.ALLOWED)
        val networkRow = row(LocationMode.NETWORK, DeviceLocationAccess.ALLOWED)
        fun gps(tap: UpdateLocationTap, live: Boolean = true, saved: Boolean = true) =
            SettingsUiModel.updateLocationRow(gpsRow, DeviceLocationKind.GPS, tap, live, hasPosition = saved)!!
        fun network(tap: UpdateLocationTap, live: Boolean = true, saved: Boolean = true) =
            SettingsUiModel.updateLocationRow(networkRow, DeviceLocationKind.NETWORK, tap, live, hasPosition = saved)!!

        for (idle in listOf(gps(UpdateLocationTap.IDLE), network(UpdateLocationTap.IDLE), gps(UpdateLocationTap.IDLE, live = false))) {
            assertEquals("Update location now", idle.title)
            assertEquals("Asks your phone where it is now, instead of waiting for the next hourly check.", idle.line)
            assertFalse(idle.lineIsAccent)
            assertFalse("idle takes a tap", idle.searching)
        }

        val searchingGps = gps(UpdateLocationTap.SEARCHING)
        assertEquals("Searching for your position...", searchingGps.title)
        assertEquals("Up to a minute. Indoors the GPS receiver may not find one.", searchingGps.line)
        assertTrue("while searching the row takes no tap", searchingGps.searching)
        assertFalse(searchingGps.lineIsAccent)
        val searchingNetwork = network(UpdateLocationTap.SEARCHING)
        assertEquals("Searching for your position...", searchingNetwork.title)
        assertEquals("Up to a minute.", searchingNetwork.line)
        assertTrue(searchingNetwork.searching)

        for (found in listOf(gps(UpdateLocationTap.FOUND), network(UpdateLocationTap.FOUND))) {
            assertEquals("Update location now", found.title)
            assertEquals(
                "Updated just now. The weather for this place is asked as soon as you are back on the home screen.",
                found.line,
            )
            assertFalse(found.lineIsAccent)
            assertFalse(found.searching)
        }
        assertEquals("Updated just now: sunrise and sunset follow it.", gps(UpdateLocationTap.FOUND, live = false).line)
        assertEquals("Updated just now: sunrise and sunset follow it.", network(UpdateLocationTap.FOUND, live = false).line)

        val notFoundGps = gps(UpdateLocationTap.NOT_FOUND)
        assertEquals("Update location now", notFoundGps.title)
        assertEquals(
            "No position found in a minute - indoors the GPS receiver often cannot see the satellites. The last position " +
                "stays. Tap to try again.",
            notFoundGps.line,
        )
        assertTrue("nothing found is said in the accent colour", notFoundGps.lineIsAccent)
        assertFalse("and the row takes a tap again", notFoundGps.searching)
        val notFoundNetwork = network(UpdateLocationTap.NOT_FOUND)
        assertEquals("No position found in a minute. The last position stays. Tap to try again.", notFoundNetwork.line)
        assertTrue(notFoundNetwork.lineIsAccent)
        // Live Weather does not change what nothing found says.
        assertEquals(notFoundGps, gps(UpdateLocationTap.NOT_FOUND, live = false))
        // With no position ever saved there is no last one to keep, and the line does not say there is.
        assertEquals(
            "No position found in a minute - indoors the GPS receiver often cannot see the satellites. Tap to try again.",
            gps(UpdateLocationTap.NOT_FOUND, saved = false).line,
        )
        assertEquals("No position found in a minute. Tap to try again.", network(UpdateLocationTap.NOT_FOUND, saved = false).line)
        assertTrue(gps(UpdateLocationTap.NOT_FOUND, saved = false).lineIsAccent)
        // Only nothing found depends on it.
        for (tap in listOf(UpdateLocationTap.IDLE, UpdateLocationTap.SEARCHING, UpdateLocationTap.FOUND)) {
            assertEquals(gps(tap), gps(tap, saved = false))
        }
    }

    // ------------------------------------------------------------------ Update weather now

    @Test
    fun `Update weather now shows only with Custom and Live Weather on, and says when it was asked`() {
        for (mode in LocationMode.entries) {
            for (live in listOf(true, false)) {
                for (asked in listOf(true, false)) {
                    val line = SettingsUiModel.updateWeatherLine(mode, live, asked)
                    assertEquals("$mode Live Weather $live", mode == LocationMode.CUSTOM && live, line != null)
                }
            }
        }
        assertEquals(
            "Asks the weather for this place at once, instead of at the next hourly check.",
            SettingsUiModel.updateWeatherLine(LocationMode.CUSTOM, liveWeatherOn = true, asked = false),
        )
        assertEquals(
            "Asked: the scene shows this place's new weather as soon as you are back on the home screen.",
            SettingsUiModel.updateWeatherLine(LocationMode.CUSTOM, liveWeatherOn = true, asked = true),
        )
    }

    // ------------------------------------------------------------------ GPS needs your precise location

    @Test
    fun `only a GPS refused for the approximate location, and not already stored, is that refusal`() {
        // fine, coarse, location on
        assertTrue(SettingsUiModel.refusedForApproximateOnly(DeviceLocationKind.GPS, false, true, true, gpsAlreadyStored = false))
        assertTrue("location off changes nothing: the precise one is missing", SettingsUiModel.refusedForApproximateOnly(DeviceLocationKind.GPS, false, true, false, gpsAlreadyStored = false))
        assertFalse("GPS stored: the Location row says it", SettingsUiModel.refusedForApproximateOnly(DeviceLocationKind.GPS, false, true, true, gpsAlreadyStored = true))
        assertFalse("nothing given is another refusal", SettingsUiModel.refusedForApproximateOnly(DeviceLocationKind.GPS, false, false, true, gpsAlreadyStored = false))
        assertFalse("the precise one given", SettingsUiModel.refusedForApproximateOnly(DeviceLocationKind.GPS, true, true, true, gpsAlreadyStored = false))
        assertFalse("Network works with the approximate one", SettingsUiModel.refusedForApproximateOnly(DeviceLocationKind.NETWORK, false, true, true, gpsAlreadyStored = false))
    }

    @Test
    fun `the precise-location row names the choice still in use and goes once GPS or Network is stored`() {
        assertEquals(
            "Your phone gave PaperScrape only your approximate location, and the GPS receiver needs the precise one, so " +
                "Custom is still in use. Tap to allow the precise location, or choose Network: it works with the approximate one.",
            SettingsUiModel.gpsNeedsPreciseLine(refusedForApproximateOnly = true, mode = LocationMode.CUSTOM),
        )
        assertEquals(
            "Your phone gave PaperScrape only your approximate location, and the GPS receiver needs the precise one, so " +
                "the previous choice is still in use. Tap to allow the precise location, or choose Network: it works with " +
                "the approximate one.",
            SettingsUiModel.gpsNeedsPreciseLine(refusedForApproximateOnly = true, mode = LocationMode.OFF),
        )
        assertNull(SettingsUiModel.gpsNeedsPreciseLine(refusedForApproximateOnly = true, mode = LocationMode.GPS))
        assertNull(SettingsUiModel.gpsNeedsPreciseLine(refusedForApproximateOnly = true, mode = LocationMode.NETWORK))
        for (mode in LocationMode.entries) assertNull("no refusal, no row", SettingsUiModel.gpsNeedsPreciseLine(false, mode))
    }

    // ------------------------------------------------------------------ the position's age

    @Test
    fun `the age is said in minutes, hours or days, at the edges`() {
        val now = 1_800_000_000_000L
        fun age(ago: Long) = SettingsUiModel.positionAge(now - ago, now)
        assertEquals("just now", age(0))
        assertEquals("just now", age(minute - 1))
        assertEquals("1 min ago", age(minute))
        assertEquals("2 min ago", age(2 * minute + 59_000))
        assertEquals("59 min ago", age(hour - 1))
        assertEquals("1 h ago", age(hour))
        assertEquals("3 h ago", age(3 * hour))
        assertEquals("47 h ago", age(48 * hour - 1))
        assertEquals("2 days ago", age(48 * hour))
        assertEquals("3 days ago", age(3 * day + 5 * hour))
        assertEquals("a time ahead of the clock reads as just taken", "just now", age(-2 * hour))
        assertNull("unknown: no age", SettingsUiModel.positionAge(0L, now))
        assertNull(SettingsUiModel.positionAge(-5L, now))
    }

    @Test
    fun `the position line says where the position came from and, when known, how old it is`() {
        val now = 1_800_000_000_000L
        assertEquals(
            "Resolved from the GPS receiver, 3 h ago",
            SettingsUiModel.devicePositionLine(working = true, mode = LocationMode.GPS, takenAtMillis = now - 3 * hour, nowMillis = now),
        )
        assertEquals(
            "Resolved from the GPS receiver, just now",
            SettingsUiModel.devicePositionLine(working = true, mode = LocationMode.GPS, takenAtMillis = now, nowMillis = now),
        )
        assertEquals(
            "Approximate, from cell towers and Wi-Fi, 2 min ago",
            SettingsUiModel.devicePositionLine(working = true, mode = LocationMode.NETWORK, takenAtMillis = now - 2 * minute, nowMillis = now),
        )
        assertEquals(
            "Last position from your phone, 3 days ago",
            SettingsUiModel.devicePositionLine(working = false, mode = LocationMode.GPS, takenAtMillis = now - 3 * day, nowMillis = now),
        )
        // A position saved with its save time alone (no time it was taken): the line with no age.
        assertEquals("Resolved from the GPS receiver", SettingsUiModel.devicePositionLine(true, LocationMode.GPS, 0L, now))
        assertEquals("Approximate, from cell towers and Wi-Fi", SettingsUiModel.devicePositionLine(true, LocationMode.NETWORK, 0L, now))
        assertEquals("Last position from your phone", SettingsUiModel.devicePositionLine(false, LocationMode.NETWORK, 0L, now))
    }

    // ------------------------------------------------------------------ the wiring

    @Test
    fun `the Weather and time page draws the new rows from the rules`() {
        val page = codeLines(source("ui/WeatherTimeScreen.kt"))
        val wired = listOf(
            "val update = SettingsUiModel.updateLocationRow(deviceRow, kind, updateTap, liveWeatherOn = liveWeather.shownOn, hasPosition = hasSaved)",
            "val hasSaved = settings.resolvedGpsLatitude != null && settings.resolvedGpsLongitude != null",
            "onClick = if (update.searching) null else ({ updateLocationNow(kind) }),",
            "supportingIsAccent = update.lineIsAccent,",
            "icon = Icons.Filled.Refresh,",
            // The page asks the phone itself, and a position found is saved with its age and the ask in one edit.
            "val found = DeviceLocationProvider(context.applicationContext).positionNow(kind)",
            "prefs.setDevicePositionFoundNow(found.fix.latitude.toFloat(), found.fix.longitude.toFloat(), found.ageMillis)",
            // In the screen's own scope, so leaving the page cancels the search.
            "val updateScope = rememberCoroutineScope()",
            "updateSearch = updateScope.launch {",
            "val updateWeather = SettingsUiModel.updateWeatherLine(locationMode, liveWeatherOn = liveWeather.shownOn, asked = weatherAsked)",
            "scope.launch { prefs.askRefreshNow() }",
            "val gpsNeedsPrecise = SettingsUiModel.gpsNeedsPreciseLine(gpsRefusedForApproximate, locationMode)",
            "if (SettingsUiModel.refusedForApproximateOnly(",
            "title = \"GPS needs your precise location\",",
            "supporting = SettingsUiModel.devicePositionLine(",
            "takenAtMillis = settings.deviceFixTakenAtMillis,",
        )
        for (line in wired) assertTrue("the page's line `$line`", line in page)
        assertTrue("the save time is not the age", page.none { "deviceFixTimestampMillis" in it })
        assertTrue("the line's words are the rule's", page.none { "\"Resolved from the GPS receiver\"" in it })
    }

    @Test
    fun `No position from your phone yet says its whole sentence, the tries while PaperScrape is on screen`() {
        val page = codeLines(source("ui/WeatherTimeScreen.kt"))
        val title = page.indexOf("title = \"No position from your phone yet\",")
        assertTrue("the row's title", title >= 0)
        val end = page.indexOf("icon = Icons.Filled.LocationOn,", title)
        assertTrue("the row's icon closes it", end > title)
        // The words as the screen shows them: the row's string literals joined, whatever the line breaks.
        val shown = page.subList(title + 1, end).joinToString("") { line ->
            Regex(""""((?:[^"\\]|\\.)*)"""").findAll(line).joinToString("") { it.groupValues[1] }
        }
        assertEquals(
            "PaperScrape asks your phone again after 5, 15 and 30 minutes while PaperScrape is on screen, then after an " +
                "hour, and the same again until a position arrives. Until then, sunrise and sunset are 6:00 and 20:00.",
            shown,
        )
    }

    /** [text]'s lines, trimmed, with comment lines, trailing `//` comments and blank lines left out. */
    private fun codeLines(text: String): List<String> =
        text.lines().map { it.trim() }
            .filterNot { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }
            .map { it.substringBefore(" //").trim() }
            .filter { it.isNotEmpty() }

    /** The index of [element] at or after [from], or -1. */
    private fun List<String>.indexOf(element: String, from: Int): Int {
        for (i in from until size) if (this[i] == element) return i
        return -1
    }

    private fun source(path: String): String {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/$path"
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = File(dir, "$prefix$suffix")
                if (f.isFile) return f.readText()
            }
            dir = dir.parentFile
        }
        error("source not found: $suffix")
    }
}
