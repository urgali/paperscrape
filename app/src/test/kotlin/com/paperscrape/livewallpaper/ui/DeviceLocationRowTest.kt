package com.paperscrape.livewallpaper.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.SolarDaySchedule
import com.paperscrape.livewallpaper.location.DeviceLocationAccess
import com.paperscrape.livewallpaper.location.DeviceLocationKind
import com.paperscrape.livewallpaper.location.LocationSource
import com.paperscrape.livewallpaper.prefs.AppBackup
import com.paperscrape.livewallpaper.prefs.BackupParseResult
import com.paperscrape.livewallpaper.prefs.WallpaperPrefs
import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import com.paperscrape.livewallpaper.prefs.parseAppBackup
import com.paperscrape.livewallpaper.prefs.toJsonString
import com.paperscrape.livewallpaper.weather.LiveWeatherStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **The location from the phone looks at the phone** (v5.10D, the maintainer's *sì* of 2026-09-30 to
 * row 5 of the v5.10A table, inventory I-206 and the location half of I-223): "GPS - tap to allow",
 * whose tap goes where it is put right; refused for good, PaperScrape's page; an app backup restored on
 * a phone without the permission does not bring GPS back to work.
 *
 * The rules are pure functions of [SettingsUiModel], asserted here over every state the phone can be
 * in; that the Weather & time page draws from them is asserted on its source, as the project does for
 * Compose it cannot run on the JVM. **None of these states was produced on the phone**: they need a
 * permission taken away, the location switched off or an Android 12 dialog, and the phone's settings
 * are not changed by these rounds. The one the phone can show -- GPS chosen in the stored settings, no
 * permission ever given -- is photographed (`consegna_v5_10d/foto/`).
 */
class DeviceLocationRowTest {

    @Test
    fun `GPS and Network show working only while the phone gives the position`() {
        for (mode in listOf(LocationMode.GPS, LocationMode.NETWORK)) {
            val working = SettingsUiModel.deviceLocationRow(mode, DeviceLocationAccess.ALLOWED)!!
            assertTrue(working.working)
            assertEquals(DeviceLocationTap.NOTHING, working.tap)
            for (access in DeviceLocationAccess.entries - DeviceLocationAccess.ALLOWED) {
                val row = SettingsUiModel.deviceLocationRow(mode, access)!!
                assertFalse("$mode $access read as working", row.working)
                assertTrue("$mode $access: a tap must lead somewhere", row.tap != DeviceLocationTap.NOTHING)
            }
        }
    }

    @Test
    fun `each reason has its line and its tap`() {
        val gps = { a: DeviceLocationAccess -> SettingsUiModel.deviceLocationRow(LocationMode.GPS, a)!! }
        assertEquals(DeviceLocationLine.NOT_ALLOWED, gps(DeviceLocationAccess.NOT_ALLOWED).line)
        assertEquals("the permission dialog", DeviceLocationTap.ASK_PERMISSION, gps(DeviceLocationAccess.NOT_ALLOWED).tap)
        assertEquals(DeviceLocationLine.APPROXIMATE_ONLY, gps(DeviceLocationAccess.APPROXIMATE_ONLY).line)
        assertEquals("the dialog again: from Android 12 it offers the precise one", DeviceLocationTap.ASK_PERMISSION, gps(DeviceLocationAccess.APPROXIMATE_ONLY).tap)
        assertEquals(DeviceLocationLine.LOCATION_OFF, gps(DeviceLocationAccess.LOCATION_OFF).line)
        assertEquals("the phone's location page", DeviceLocationTap.OPEN_LOCATION_SETTINGS, gps(DeviceLocationAccess.LOCATION_OFF).tap)
    }

    @Test
    fun `the row says whether the scene keeps the last position or uses none`() {
        val gps = { a: DeviceLocationAccess -> SettingsUiModel.deviceLocationRow(LocationMode.GPS, a)!! }
        assertTrue(gps(DeviceLocationAccess.ALLOWED).usesLastPosition)
        assertTrue("location off: the last one the phone gave, as with no signal", gps(DeviceLocationAccess.LOCATION_OFF).usesLastPosition)
        assertFalse("no permission: none", gps(DeviceLocationAccess.NOT_ALLOWED).usesLastPosition)
        assertFalse(gps(DeviceLocationAccess.APPROXIMATE_ONLY).usesLastPosition)
        // And it agrees with what the wallpaper does (DeviceLocationAccess.mayUsePosition).
        for (a in DeviceLocationAccess.entries) assertEquals("$a", a.mayUsePosition, gps(a).usesLastPosition)
    }

    @Test
    fun `Off and Custom have no such row`() {
        for (a in DeviceLocationAccess.entries) {
            assertNull(SettingsUiModel.deviceLocationRow(LocationMode.OFF, a))
            assertNull(SettingsUiModel.deviceLocationRow(LocationMode.CUSTOM, a))
        }
    }

    // ------------------------------------------------------------------ the answer to the dialog

    private fun after(
        kind: DeviceLocationKind = DeviceLocationKind.GPS,
        fine: Boolean = false,
        coarse: Boolean = false,
        on: Boolean = true,
        askAgain: Boolean = true,
        refusalSeen: Boolean = false,
        stored: Boolean = false,
    ) = SettingsUiModel.afterLocationRequest(kind, fine, coarse, on, askAgain, refusalSeen, stored)

    @Test
    fun `granted is stored, and with the location off the location page opens too`() {
        assertEquals(LocationRequestOutcome.STORE, after(fine = true, coarse = true))
        assertEquals(LocationRequestOutcome.STORE, after(kind = DeviceLocationKind.NETWORK, coarse = true))
        assertEquals(LocationRequestOutcome.STORE_AND_OPEN_LOCATION_SETTINGS, after(fine = true, coarse = true, on = false))
    }

    @Test
    fun `refused keeps the previous choice, as it always did, while the system would ask again`() {
        assertEquals(LocationRequestOutcome.KEEP_PREVIOUS, after())
        assertEquals("Approximate answered for GPS is a refusal of GPS", LocationRequestOutcome.KEEP_PREVIOUS, after(coarse = true))
        assertEquals(LocationRequestOutcome.KEEP_PREVIOUS, after(askAgain = true, refusalSeen = true, stored = true))
    }

    @Test
    fun `refused for good opens PaperScrape's page, where until v5_10D the tap did nothing`() {
        // After a refusal already seen on this screen, or for a choice already stored ("tap to allow").
        // Nothing is written: a working Custom or Off is not replaced by a GPS that cannot work.
        assertEquals(LocationRequestOutcome.OPEN_APP_PAGE, after(askAgain = false, refusalSeen = true))
        assertEquals(LocationRequestOutcome.OPEN_APP_PAGE, after(askAgain = false, stored = true))
        assertEquals(LocationRequestOutcome.OPEN_APP_PAGE, after(kind = DeviceLocationKind.NETWORK, askAgain = false, stored = true))
        // A first dialog closed with no answer says "would not ask" too: not the app's page yet.
        assertEquals(LocationRequestOutcome.KEEP_PREVIOUS, after(askAgain = false))
    }

    // ------------------------------------------------------------------ Live Weather

    @Test
    fun `Live Weather reads off at once when the phone does not give the position`() {
        for (mode in listOf(LocationMode.GPS, LocationMode.NETWORK)) {
            val state = SettingsUiModel.liveWeather(
                liveWeatherEnabled = true, followRealTime = true, locationMode = mode, devicePositionUsable = false,
                keyMissing = false, isTheWallpaper = true, status = LiveWeatherStatus.OK,
            )
            assertFalse(state.shownOn)
            assertEquals(LiveWeatherBlocker.LOCATION_NOT_ALLOWED, state.blocker)
            assertEquals("the tap goes to the Location choice", LiveWeatherTap.SHOW_LOCATION, SettingsUiModel.liveWeatherTap(state))
            assertFalse("and nothing reads as driving the scene", state.drivingTheScene)
        }
        // Custom uses nothing of the phone's: what the phone says does not hold it.
        assertTrue(
            SettingsUiModel.liveWeather(true, true, LocationMode.CUSTOM, devicePositionUsable = false, keyMissing = false, isTheWallpaper = true, status = LiveWeatherStatus.OK).shownOn,
        )
    }

    // ------------------------------------------------------------------ an app backup on a new phone

    @Test
    fun `a backup restored on a phone without the permission does not bring GPS back to work`() {
        val dir = kotlin.io.path.createTempDirectory("backup-gps").toFile()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val prefs = WallpaperPrefs(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "prefs.preferences_pb") })
            // The old phone: GPS chosen, a position resolved there.
            val oldPhone = WallpaperSettings(
                useLocationForSunTimes = true,
                deviceLocationKind = DeviceLocationKind.GPS,
                liveWeatherEnabled = true,
                resolvedGpsLatitude = 45.46f,
                resolvedGpsLongitude = 9.19f,
            )
            val file = AppBackup.from(oldPhone, CustomThemeData.EMPTY, "5.9", 0L).toJsonString()
            val backup = (parseAppBackup(file) as BackupParseResult.Ok).backup

            // The new phone: the import's settings write, as BackupRepository makes it.
            val restored = runBlocking {
                prefs.replaceAllStagingThemes(backup.settings, backup.themeCustomizations, "")
                prefs.settingsFlow.first()
            }

            assertEquals("the choice is the user's and comes back", LocationSource.GPS, LocationSource.of(restored))
            assertNull("the old phone's position does not", restored.resolvedGpsLatitude)
            // The new phone has not given the permission: the page reads the phone, not the backup.
            val row = SettingsUiModel.deviceLocationRow(LocationMode.GPS, DeviceLocationAccess.NOT_ALLOWED)!!
            assertFalse(row.working)
            assertEquals(DeviceLocationTap.ASK_PERMISSION, row.tap)
            assertFalse(
                SettingsUiModel.liveWeather(true, true, LocationMode.GPS, false, false, true, LiveWeatherStatus.OFF).shownOn,
            )
            // And the wallpaper neither asks the phone nor has a position to use.
            assertEquals(
                SolarDaySchedule.Action.NOTHING,
                SolarDaySchedule.onTick(
                    followRealTime = true, liveWeatherEnabled = true, deviceSource = true, devicePositionUsable = false,
                    weatherAttemptDue = true, hasPosition = false, holdsDevicePosition = false, dayIsStale = false,
                ),
            )
        } finally {
            scope.cancel()
            dir.deleteRecursively()
        }
    }

    // ------------------------------------------------------------------ the wiring

    @Test
    fun `the Weather and time page draws the row from the rules and reads the phone when it comes back`() {
        val page = source("ui/WeatherTimeScreen.kt")
        assertTrue(page.contains("val deviceAccess = rememberDeviceLocationAccess(settings)"))
        assertTrue(page.contains("SettingsUiModel.deviceLocationRow(locationMode, it)"))
        assertTrue(page.contains("devicePositionUsable = deviceAccess.positionUsable(),"))
        assertTrue(page.contains("SettingsUiModel.afterLocationRequest("))
        for (outcome in LocationRequestOutcome.entries) assertTrue("$outcome not handled", page.contains("LocationRequestOutcome.${outcome.name} ->"))
        for (tap in DeviceLocationTap.entries) assertTrue("$tap not handled", page.contains("DeviceLocationTap.${tap.name} ->"))
        assertTrue("GPS - tap to allow", page.contains("\"\$name - tap to allow\""))
        assertTrue(page.contains("Settings.ACTION_LOCATION_SOURCE_SETTINGS"))
        assertTrue("the app's page", page.contains("UpdateNotifier.appDetailsIntent(context)"))
        // The coordinates are not shown as GPS working when the phone does not give the position.
        assertTrue(page.contains("if (deviceRow.usesLastPosition) {"))
        // Working, and no position yet: the page says so (the Live Weather line sends the user here).
        assertTrue(page.contains("if (deviceRow.working && !hasSaved) {"))
        assertTrue(page.contains("\"No position from your phone yet\""))
        // The line for an engine with no position yet is not the line for a phone that does not allow it.
        assertTrue(page.contains("LiveWeatherBlocker.LOCATION_UNAVAILABLE ->\n                        \"Your phone has not given a location yet."))
        assertTrue(page.contains("LiveWeatherBlocker.LOCATION_NOT_ALLOWED ->"))
        assertFalse("a refusal for good writes nothing", page.substringAfter("LocationRequestOutcome.OPEN_APP_PAGE ->").substringBefore("\n").contains("setDeviceLocation"))
        val state = source("ui/DeviceLocationState.kt")
        assertTrue("read again on every return to the front", state.contains("if (event == Lifecycle.Event.ON_RESUME) access = read()"))
        // The home screen and World & scene ask the same question.
        assertTrue(source("ui/SettingsScreen.kt").contains("devicePositionUsable = deviceAccess.positionUsable(),"))
        assertTrue(source("ui/WorldSceneScreen.kt").contains("devicePositionUsable = deviceAccess.positionUsable(),"))
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
