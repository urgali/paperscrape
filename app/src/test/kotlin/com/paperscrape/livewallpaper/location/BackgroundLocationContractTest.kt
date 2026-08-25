package com.paperscrape.livewallpaper.location

import android.Manifest
import android.location.LocationManager
import com.paperscrape.livewallpaper.engine.WEATHER_CHECK_INTERVAL_MS
import com.paperscrape.livewallpaper.engine.WEATHER_REFRESH_INTERVAL_MS
import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The decision not to ask for background location, written down so it cannot drift.**
 *
 * ### What was measured, and what it means
 *
 * A live wallpaper keeps running with the app's UI closed and the screen off, and the forecast it
 * draws depends on where the phone is. The obvious reading of Android's rules says that costs
 * either `ACCESS_BACKGROUND_LOCATION` -- a separate "Allow all the time" prompt, its own Play
 * policy declaration -- or a foreground service of type `location`, with a permanent notification.
 *
 * Neither is true here, and it was measured rather than reasoned. On a Pixel 9 / Android 17
 * emulator, with the settings Activity **never opened since boot** and only "while in use"
 * permission granted (`appop mode=foreground`), the wallpaper process sits at
 * `PROCESS_STATE_BOUND_FOREGROUND_SERVICE` with `PROCESS_CAPABILITY_FOREGROUND_LOCATION`, asks the
 * GPS provider once, is given a fix, and publishes `live_weather_status = ok`. With the screen
 * off it drops to `PROCESS_STATE_IMPORTANT_FOREGROUND` and **keeps** that capability; `appops`
 * records the access as `[bg-s]` under `FINE_LOCATION (allow)`. The active-wallpaper binding is
 * what pays for it, and the app already has everything it needs.
 *
 * So the fix for P4 is that there is nothing to fix -- and the risk is that a later release adds
 * one of the two anyway, for a capability the system already grants, costing the user a prompt or
 * a notification for nothing. That is what this class exists to prevent. Its instrumented half,
 * `BackgroundLocationManifestTest`, asserts the manifest; this half asserts the behaviour around
 * it, which is the part that decides how often a position is even wanted.
 *
 * **Not verified: the Network provider.** The emulator's network location provider is disabled
 * (`enabled=false, allowed=false`, no Wi-Fi or cell infrastructure behind it), so the runtime proof
 * covers GPS only. The permission and the code path are shared -- [DeviceLocationProvider] treats
 * the two kinds identically apart from which provider name it asks -- but that is an inference,
 * and the report says so.
 */
class BackgroundLocationContractTest {

    // ------------------------------------------------------- exactly one permission per mode

    /**
     * Each mode asks for the weakest permission that can serve it, and nothing asks for more.
     *
     * `NETWORK` is coarse because a forecast is resolved to a grid cell measured in kilometres, so
     * a metre-accurate fix would be spent battery buying the scene nothing. `GPS` is fine because
     * that is what the GNSS receiver is.
     */
    @Test
    fun `each positioning kind asks for the weakest permission that serves it`() {
        assertEquals(Manifest.permission.ACCESS_COARSE_LOCATION, DeviceLocationKind.NETWORK.permission)
        assertEquals(Manifest.permission.ACCESS_FINE_LOCATION, DeviceLocationKind.GPS.permission)
        assertEquals(LocationManager.NETWORK_PROVIDER, DeviceLocationKind.NETWORK.providerName)
        assertEquals(LocationManager.GPS_PROVIDER, DeviceLocationKind.GPS.providerName)
    }

    /**
     * **No code path anywhere names background location.**
     *
     * Asserted over the enum that owns every permission the feature knows about, so adding a third
     * kind that asks for `ACCESS_BACKGROUND_LOCATION` fails here as well as in the manifest test.
     */
    @Test
    fun `no positioning kind asks for background location`() {
        for (kind in DeviceLocationKind.entries) {
            assertTrue(
                "${kind.name} asks for ${kind.permission}",
                kind.permission == Manifest.permission.ACCESS_COARSE_LOCATION ||
                    kind.permission == Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }
    }

    // ------------------------------------------------------- when a position is wanted at all

    /** The two modes that use no device sensor ask the positioning stack for nothing. */
    @Test
    fun `custom and off need no device positioning`() {
        assertNull(LocationSource.CUSTOM.deviceKind)
        assertNull(LocationSource.NONE.deviceKind)
        assertNotNull(LocationSource.GPS.deviceKind)
        assertNotNull(LocationSource.NETWORK.deviceKind)
    }

    /** And a settings state maps onto exactly one of them, so a fix belongs to a known source. */
    @Test
    fun `the settings decide the source, and only one at a time`() {
        val off = WallpaperSettings()
        assertEquals(LocationSource.NONE, LocationSource.of(off))
        assertEquals(
            LocationSource.NETWORK,
            LocationSource.of(off.copy(useLocationForSunTimes = true, deviceLocationKind = DeviceLocationKind.NETWORK)),
        )
        assertEquals(
            LocationSource.GPS,
            LocationSource.of(off.copy(useLocationForSunTimes = true, deviceLocationKind = DeviceLocationKind.GPS)),
        )
        assertEquals(LocationSource.CUSTOM, LocationSource.of(off.copy(useCustomLocation = true)))
    }

    // ------------------------------------------------------- how often, which is the battery half

    /**
     * The cadence that makes the whole arrangement affordable.
     *
     * A background app that polls its position is a battery complaint waiting to happen, and the
     * reason this design needs no special permission is the same reason it needs no special
     * budget: it asks **once an hour at most**, only when a weather refresh is actually due, and it
     * takes the system's own cached answer whenever that answer is younger than a quarter of an
     * hour. The two-minute tick is a check of the clock, not a request.
     */
    @Test
    fun `a position is asked for at most once an hour`() {
        assertEquals("the weather refresh interval", 60 * 60 * 1000L, WEATHER_REFRESH_INTERVAL_MS)
        assertEquals("the tick that checks whether one is due", 2 * 60 * 1000L, WEATHER_CHECK_INTERVAL_MS)
        assertTrue(
            "the check must be far shorter than the refresh, or a freshly enabled toggle waits an hour",
            WEATHER_CHECK_INTERVAL_MS < WEATHER_REFRESH_INTERVAL_MS / 10,
        )
    }

    /**
     * A cached fix is preferred to a new one, and the window is generous on purpose.
     *
     * Fifteen minutes against an hourly consumer means most refreshes cost no radio at all: the
     * system already has a position from somebody else's request, and the wallpaper takes it.
     */
    @Test
    fun `the provider prefers a cached fix and always terminates`() {
        assertEquals(15 * 60 * 1000L, DeviceLocationProvider.FRESH_ENOUGH_MS)
        assertEquals(20_000L, DeviceLocationProvider.REQUEST_TIMEOUT_MS)
        assertTrue(
            "a single request must not be able to outlive the tick that started it",
            DeviceLocationProvider.REQUEST_TIMEOUT_MS < WEATHER_CHECK_INTERVAL_MS,
        )
        assertTrue(
            "and the cache window must be shorter than the refresh it serves",
            DeviceLocationProvider.FRESH_ENOUGH_MS < WEATHER_REFRESH_INTERVAL_MS,
        )
    }
}
