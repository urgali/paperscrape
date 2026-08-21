package com.paperscrape.livewallpaper.weather

import com.paperscrape.livewallpaper.location.DeviceLocationKind
import com.paperscrape.livewallpaper.location.LocationSource
import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a change has to produce a request **now** rather than at the next hourly refresh, and what
 * the settings screen is told about the outcome.
 *
 * The guarantee: turning Live Weather on with a location already available must not wait. On the
 * device it does not -- measured on a Pixel 9, the preference write at 11:45:12.166 produced a
 * request at 11:45:12.183, 17 ms later -- and what this pins is the rule that makes it so, which
 * v2.13 had but stated as a comparison of two fields that a second provider and a second key have
 * since made incomplete.
 */
class LiveWeatherRefreshTest {

    private val withLocation = WallpaperSettings(
        useCustomLocation = true,
        customLocationLatitude = 43.77925f,
        customLocationLongitude = 11.24626f,
    )

    // -- what forces an immediate fetch --------------------------------------------------------------

    @Test
    fun `switching Live Weather on forces a fetch`() {
        val off = withLocation.copy(liveWeatherEnabled = false)
        assertTrue(LiveWeatherInputs.changed(off, off.copy(liveWeatherEnabled = true)))
    }

    @Test
    fun `switching Live Weather off is also a change`() {
        val on = withLocation.copy(liveWeatherEnabled = true)
        assertTrue(LiveWeatherInputs.changed(on, on.copy(liveWeatherEnabled = false)))
    }

    /** The conditions on screen came from a service the user has just stopped using. */
    @Test
    fun `switching provider forces a fetch`() {
        val on = withLocation.copy(liveWeatherEnabled = true)
        assertTrue(
            LiveWeatherInputs.changed(on, on.copy(weatherProviderId = WeatherProviderId.VISUAL_CROSSING.storageId)),
        )
    }

    /**
     * The regression this exists to prevent. v2.13 compared the toggle and Open-Meteo's key, which
     * was complete then; entering the Visual Crossing key -- the one thing that turns "no requests
     * are being made" into "requests can be made" -- would have been missed.
     */
    @Test
    fun `entering either provider's key forces a fetch`() {
        val on = withLocation.copy(liveWeatherEnabled = true)
        assertTrue(LiveWeatherInputs.changed(on, on.copy(liveWeatherApiKey = "k")))
        assertTrue(LiveWeatherInputs.changed(on, on.copy(visualCrossingApiKey = "k")))
    }

    @Test
    fun `nothing else forces a fetch`() {
        val on = withLocation.copy(liveWeatherEnabled = true)
        assertFalse(LiveWeatherInputs.changed(on, on))
        assertFalse(LiveWeatherInputs.changed(on, on.copy(themeId = "beach")))
        assertFalse(LiveWeatherInputs.changed(on, on.copy(scrollSpeed = 0.5f)))
        // A moved location is handled by the loop's own comparison against the coordinates the
        // last fetch was made for, not here -- otherwise a fetch would be forced twice.
        assertFalse(LiveWeatherInputs.changed(on, on.copy(customLocationLatitude = 45.46f)))
        // Published by the service itself. Treating it as an input would make every status write
        // force another fetch, which would write another status.
        assertFalse(LiveWeatherInputs.changed(on, on.copy(liveWeatherStatus = LiveWeatherStatus.OK.storageId)))
    }

    // -- location source ----------------------------------------------------------------------------

    /**
     * The Custom -> Phone bug, as a rule. Measured on the Pixel 9: selecting Phone kept fetching
     * the custom location's coordinates, because a fix was held and nothing recorded which source
     * it belonged to.
     */
    @Test
    fun `each location choice has its own source`() {
        assertEquals(LocationSource.NONE, LocationSource.of(WallpaperSettings()))
        assertEquals(
            "a stored device flag with no kind is the pre-v3.0 shape, and means Network",
            LocationSource.NETWORK,
            LocationSource.of(WallpaperSettings(useLocationForSunTimes = true)),
        )
        assertEquals(
            LocationSource.GPS,
            LocationSource.of(
                WallpaperSettings(useLocationForSunTimes = true, deviceLocationKind = DeviceLocationKind.GPS),
            ),
        )
        assertEquals(
            LocationSource.CUSTOM,
            LocationSource.of(WallpaperSettings(useCustomLocation = true)),
        )
    }

    /**
     * GPS and Network are different sources, so switching between them has to invalidate the held
     * fix exactly as switching to or from Custom does -- otherwise picking Network after GPS would
     * keep serving the GNSS coordinates the user just opted out of.
     */
    @Test
    fun `switching between the two device systems is a change of source`() {
        val gps = LocationSource.of(
            WallpaperSettings(useLocationForSunTimes = true, deviceLocationKind = DeviceLocationKind.GPS),
        )
        val network = LocationSource.of(
            WallpaperSettings(useLocationForSunTimes = true, deviceLocationKind = DeviceLocationKind.NETWORK),
        )
        assertNotEquals(gps, network)
    }

    @Test
    fun `only the device sources reach a positioning system`() {
        assertEquals(DeviceLocationKind.GPS, LocationSource.GPS.deviceKind)
        assertEquals(DeviceLocationKind.NETWORK, LocationSource.NETWORK.deviceKind)
        assertEquals(null, LocationSource.CUSTOM.deviceKind)
        assertEquals(null, LocationSource.NONE.deviceKind)
    }

    /**
     * The whole point of the Network mode: a user who picks it has said no to the GNSS receiver,
     * and no code path may substitute it.
     */
    @Test
    fun `the network mode never names the GPS provider`() {
        assertEquals(android.location.LocationManager.NETWORK_PROVIDER, DeviceLocationKind.NETWORK.providerName)
        assertNotEquals(android.location.LocationManager.GPS_PROVIDER, DeviceLocationKind.NETWORK.providerName)
        assertEquals(
            "Network mode must ask for the coarse permission, never the precise one",
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            DeviceLocationKind.NETWORK.permission,
        )
    }

    @Test
    fun `a stored positioning kind round-trips, and anything else falls back to network`() {
        for (kind in DeviceLocationKind.entries) {
            assertEquals(kind, DeviceLocationKind.fromStorageId(kind.storageId))
        }
        assertEquals(DeviceLocationKind.NETWORK, DeviceLocationKind.fromStorageId(null))
        assertEquals(DeviceLocationKind.NETWORK, DeviceLocationKind.fromStorageId("something else"))
    }

    @Test
    fun `switching between the two sources is a change of source`() {
        val custom = LocationSource.of(WallpaperSettings(useCustomLocation = true))
        val phone = LocationSource.of(WallpaperSettings(useLocationForSunTimes = true))
        assertTrue(custom != phone)
    }

    /** Both flags set is a state prefs prevents, but an old install's pair must not crash. */
    @Test
    fun `an impossible pair resolves to the explicit choice`() {
        assertEquals(
            LocationSource.CUSTOM,
            LocationSource.of(WallpaperSettings(useCustomLocation = true, useLocationForSunTimes = true)),
        )
    }

    // -- status --------------------------------------------------------------------------------------

    @Test
    fun `off is off whatever else is true`() {
        assertEquals(
            LiveWeatherStatus.OFF,
            LiveWeatherStatus.of(
                enabled = false,
                hasLocation = true,
                result = WeatherFetchResult.MissingApiKey,
                hasSnapshotInEffect = true,
                previous = LiveWeatherStatus.OK,
            ),
        )
    }

    @Test
    fun `on with nowhere to check says so`() {
        assertEquals(
            LiveWeatherStatus.NO_LOCATION,
            LiveWeatherStatus.of(true, hasLocation = false, result = null, hasSnapshotInEffect = false, previous = LiveWeatherStatus.OFF),
        )
    }

    @Test
    fun `a missing key is its own state and not a failure`() {
        val status = LiveWeatherStatus.of(
            enabled = true,
            hasLocation = true,
            result = WeatherFetchResult.MissingApiKey,
            hasSnapshotInEffect = false,
            previous = LiveWeatherStatus.OFF,
        )
        assertEquals(LiveWeatherStatus.MISSING_API_KEY, status)
        assertTrue(status.isRunningOnThemeWeather)
    }

    /**
     * The distinction that keeps a dropped request from flickering the scene back to the theme's
     * own weather: with a snapshot still in effect the state is stale, not failed.
     */
    @Test
    fun `a failure with data still showing is stale, without it is failed`() {
        val failure = WeatherFetchResult.Failed(WeatherFailure.NETWORK, WeatherProviderId.OPEN_METEO)
        assertEquals(
            LiveWeatherStatus.STALE,
            LiveWeatherStatus.of(true, true, failure, hasSnapshotInEffect = true, previous = LiveWeatherStatus.OK),
        )
        assertEquals(
            LiveWeatherStatus.FAILED,
            LiveWeatherStatus.of(true, true, failure, hasSnapshotInEffect = false, previous = LiveWeatherStatus.OK),
        )
        assertFalse(LiveWeatherStatus.STALE.isRunningOnThemeWeather)
        assertTrue(LiveWeatherStatus.FAILED.isRunningOnThemeWeather)
    }

    @Test
    fun `a success is ok`() {
        val success = WeatherFetchResult.Success(
            WeatherObservation(condition = WeatherCondition.CLEAR, observedAtMillis = 1L, source = WeatherProviderId.OPEN_METEO),
        )
        assertEquals(
            LiveWeatherStatus.OK,
            LiveWeatherStatus.of(true, true, success, hasSnapshotInEffect = true, previous = LiveWeatherStatus.FAILED),
        )
    }

    /**
     * A tick where no fetch was due knows nothing new, so it must not overwrite what the last one
     * found. Recomputing from the snapshot alone would turn a remembered failure into an OK.
     */
    @Test
    fun `a tick with no fetch keeps the previous state`() {
        assertEquals(
            LiveWeatherStatus.MISSING_API_KEY,
            LiveWeatherStatus.of(true, true, result = null, hasSnapshotInEffect = false, previous = LiveWeatherStatus.MISSING_API_KEY),
        )
        assertEquals(
            LiveWeatherStatus.STALE,
            LiveWeatherStatus.of(true, true, result = null, hasSnapshotInEffect = true, previous = LiveWeatherStatus.STALE),
        )
    }

    @Test
    fun `status ids round-trip and an unknown one reads as off`() {
        for (status in LiveWeatherStatus.entries) {
            assertEquals(status, LiveWeatherStatus.fromStorageId(status.storageId))
        }
        assertEquals(LiveWeatherStatus.OFF, LiveWeatherStatus.fromStorageId("something_else"))
        assertEquals(LiveWeatherStatus.OFF, LiveWeatherStatus.fromStorageId(null))
    }

    // -- the schedule ---------------------------------------------------------------------------------

    /**
     * The immediate fetch must not become a shorter polling interval. The hourly refresh is a
     * separate mechanism and this batch does not touch it: what makes OFF -> ON prompt is that the
     * cached timer is ignored once, not that the timer got smaller.
     */
    @Test
    fun `forcing a fetch is a one-off, not a faster schedule`() {
        val on = withLocation.copy(liveWeatherEnabled = true)
        val afterToggle = on.copy(liveWeatherEnabled = false).let { off -> LiveWeatherInputs.changed(off, on) }
        assertTrue(afterToggle)
        // The very next evaluation, with nothing changed, forces nothing.
        assertFalse(LiveWeatherInputs.changed(on, on))
    }
}
