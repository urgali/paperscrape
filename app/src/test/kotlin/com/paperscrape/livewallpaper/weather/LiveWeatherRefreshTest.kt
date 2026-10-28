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
 * The guarantee: turning Live Weather on with a location already available must not wait for the
 * hourly refresh. What this pins is the rule that makes it so, [LiveWeatherInputs.changed]: every
 * setting that decides whether and where a request can be made, across every provider and key.
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
            LiveWeatherInputs.changed(on, on.copy(weatherProviderId = WeatherProviderId.WEATHER_API_COM.storageId)),
        )
    }

    /**
     * Every provider's key counts: entering the WeatherAPI.com key is the one thing that turns "no
     * requests are being made" into "requests can be made", and a rule that compares only the
     * toggle and Open-Meteo's key misses it.
     */
    @Test
    fun `entering either provider's key forces a fetch`() {
        val on = withLocation.copy(liveWeatherEnabled = true)
        assertTrue(LiveWeatherInputs.changed(on, on.copy(liveWeatherApiKey = "k")))
        assertTrue(LiveWeatherInputs.changed(on, on.copy(weatherApiComApiKey = "k")))
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
     * Each choice is its own source, so a held fix is known to belong to one: without it, selecting
     * Phone after Custom keeps fetching the custom location's coordinates.
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

    /**
     * **A rejected key is not an unreachable service.**
     *
     * The banner of [LiveWeatherStatus.FAILED] and [STALE] says the provider "could not be reached".
     * An HTTP 401 is a service that answered promptly and refused the credential -- OpenWeather
     * answers an invalid key with `401 {"cod":401, "message": "Invalid API key..."}` -- so folded into
     * those states it would blame the network for a wrong key.
     */
    @Test
    fun `a rejected key is its own state, not an unreachable provider`() {
        val rejected = WeatherFetchResult.Failed(WeatherFailure.UNAUTHORIZED, WeatherProviderId.OPEN_WEATHER)

        // Whether an earlier observation happens to still be on screen does not change what the
        // user has to do, so -- exactly like MISSING_API_KEY -- it does not change the state.
        assertEquals(
            LiveWeatherStatus.REJECTED_API_KEY,
            LiveWeatherStatus.of(true, true, rejected, hasSnapshotInEffect = false, previous = LiveWeatherStatus.OK),
        )
        assertEquals(
            LiveWeatherStatus.REJECTED_API_KEY,
            LiveWeatherStatus.of(true, true, rejected, hasSnapshotInEffect = true, previous = LiveWeatherStatus.OK),
        )

        // And it is emphatically neither of the two states whose banner claims unreachability.
        assertNotEquals(LiveWeatherStatus.FAILED, LiveWeatherStatus.of(true, true, rejected, false, LiveWeatherStatus.OK))
        assertNotEquals(LiveWeatherStatus.STALE, LiveWeatherStatus.of(true, true, rejected, true, LiveWeatherStatus.OK))
    }

    /**
     * 403 is the same fact as 401 -- the key will not work -- so it must reach the same state, and
     * it does because [WeatherHttp.statusToFailure] maps both to [WeatherFailure.UNAUTHORIZED].
     */
    @Test
    fun `both rejection statuses reach the rejected-key state`() {
        for (status in intArrayOf(401, 403)) {
            val result = WeatherFetchResult.Failed(WeatherHttp.statusToFailure(status), WeatherProviderId.OPEN_WEATHER)
            assertEquals(
                "HTTP $status",
                LiveWeatherStatus.REJECTED_API_KEY,
                LiveWeatherStatus.of(true, true, result, hasSnapshotInEffect = false, previous = LiveWeatherStatus.OK),
            )
        }
    }

    /**
     * The failures where "could not be reached" is true stay stale or failed. A rejected key, a
     * provider's limit and a refused request are the three where it is false: the provider answered
     * (`ProviderLimitMessageTest`, `RefusedRequestStatusTest`).
     */
    @Test
    fun `every other failure still reports as a failure`() {
        val untouched = listOf(
            WeatherFailure.NETWORK,
            WeatherFailure.HTTP_ERROR,
            WeatherFailure.MALFORMED_RESPONSE,
        )
        for (reason in untouched) {
            val result = WeatherFetchResult.Failed(reason, WeatherProviderId.OPEN_WEATHER)
            assertEquals(
                reason.name,
                LiveWeatherStatus.STALE,
                LiveWeatherStatus.of(true, true, result, hasSnapshotInEffect = true, previous = LiveWeatherStatus.OK),
            )
            assertEquals(
                reason.name,
                LiveWeatherStatus.FAILED,
                LiveWeatherStatus.of(true, true, result, hasSnapshotInEffect = false, previous = LiveWeatherStatus.OK),
            )
        }
    }

    /** The scene is not being driven by a provider that is refusing to answer. */
    @Test
    fun `a rejected key is not driving the scene`() {
        assertFalse(LiveWeatherStatus.REJECTED_API_KEY.isDrivingTheScene)
        assertTrue(LiveWeatherStatus.REJECTED_API_KEY.isRunningOnThemeWeather)
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
     * separate mechanism: what makes OFF -> ON prompt is that the cached timer is ignored once, not
     * that the timer is smaller.
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
