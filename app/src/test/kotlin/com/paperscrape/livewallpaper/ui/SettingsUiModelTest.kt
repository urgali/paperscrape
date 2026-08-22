package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.location.DeviceLocationKind

import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import com.paperscrape.livewallpaper.weather.LiveWeatherStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The v2.9 settings UI presents two pairs of mutually exclusive booleans as one choice each --
 * location source and seasonal palette. Nothing about how either is stored changed, and these
 * tests are what says so: every flag combination reads back as exactly one mode, every mode
 * writes back exactly the flags the two switches used to write, and both round-trip.
 *
 * The ordinal assertions are not decoration. The segmented controls index their options by
 * `enum.ordinal` and read the tap back through `entries[index]`, so reordering either enum would
 * silently swap two settings; these pin the order to the labels shown on screen.
 */
class SettingsUiModelTest {

    // -- Location -----------------------------------------------------------------------------

    @Test
    fun `no location flag set reads as off`() {
        assertEquals(
            LocationMode.OFF,
            SettingsUiModel.locationMode(useDeviceLocation = false, useCustomLocation = false),
        )
    }

    /**
     * An install from before v3.0 stored the device flag and nothing about *which* system it
     * meant. It must read as Network, not GPS: the old single mode asked the network provider
     * first and only reached for GPS if it was disabled, so Network is the behaviour -- and the
     * permission -- those users already had.
     */
    @Test
    fun `a device flag with no stored kind reads as network, not GPS`() {
        assertEquals(
            LocationMode.NETWORK,
            SettingsUiModel.locationMode(useDeviceLocation = true, useCustomLocation = false),
        )
    }

    @Test
    fun `each device kind reads as its own mode`() {
        assertEquals(
            LocationMode.GPS,
            SettingsUiModel.locationMode(true, false, DeviceLocationKind.GPS),
        )
        assertEquals(
            LocationMode.NETWORK,
            SettingsUiModel.locationMode(true, false, DeviceLocationKind.NETWORK),
        )
    }

    @Test
    fun `only the two device modes name a positioning system`() {
        assertEquals(DeviceLocationKind.GPS, SettingsUiModel.deviceKindFor(LocationMode.GPS))
        assertEquals(DeviceLocationKind.NETWORK, SettingsUiModel.deviceKindFor(LocationMode.NETWORK))
        assertEquals(null, SettingsUiModel.deviceKindFor(LocationMode.OFF))
        assertEquals(
            "Custom must never reach a positioning system: it needs no permission at all",
            null,
            SettingsUiModel.deviceKindFor(LocationMode.CUSTOM),
        )
    }

    @Test
    fun `custom flag reads as custom`() {
        assertEquals(
            LocationMode.CUSTOM,
            SettingsUiModel.locationMode(useDeviceLocation = false, useCustomLocation = true),
        )
    }

    /**
     * A state the preferences layer never produces -- both setters clear the other flag -- but a
     * reader still has to resolve it to something rather than throw. The device fix wins because
     * it is the one the wallpaper service actually resolves coordinates from.
     */
    @Test
    fun `both location flags set resolve to a device mode rather than throwing`() {
        assertEquals(
            LocationMode.NETWORK,
            SettingsUiModel.locationMode(useDeviceLocation = true, useCustomLocation = true),
        )
    }

    @Test
    fun `location modes write the same flag pairs the two switches wrote`() {
        assertEquals(false to false, SettingsUiModel.locationFlags(LocationMode.OFF))
        assertEquals(true to false, SettingsUiModel.locationFlags(LocationMode.GPS))
        assertEquals(true to false, SettingsUiModel.locationFlags(LocationMode.NETWORK))
        assertEquals(false to true, SettingsUiModel.locationFlags(LocationMode.CUSTOM))
    }

    @Test
    fun `every location mode round-trips through its flags and kind`() {
        for (mode in LocationMode.entries) {
            val (device, custom) = SettingsUiModel.locationFlags(mode)
            val kind = SettingsUiModel.deviceKindFor(mode) ?: DeviceLocationKind.NETWORK
            assertEquals(mode, SettingsUiModel.locationMode(device, custom, kind))
        }
    }

    /**
     * The segmented control indexes its options by [LocationMode.ordinal], so the enum's order
     * *is* the on-screen order. Reordering the enum without reordering the labels would silently
     * put the user on a different mode from the one they tapped.
     */
    @Test
    fun `location option order matches the segmented control labels`() {
        assertEquals(0, LocationMode.OFF.ordinal) // "Off"
        assertEquals(1, LocationMode.GPS.ordinal) // "GPS"
        assertEquals(2, LocationMode.NETWORK.ordinal) // "Network"
        assertEquals(3, LocationMode.CUSTOM.ordinal) // "Custom"
        assertEquals(4, LocationMode.entries.size)
    }

    @Test
    fun `a fresh install starts with no location source`() {
        val defaults = WallpaperSettings()
        assertEquals(
            LocationMode.OFF,
            SettingsUiModel.locationMode(defaults.useLocationForSunTimes, defaults.useCustomLocation),
        )
    }

    // -- Seasonal palette ---------------------------------------------------------------------

    @Test
    fun `no palette flag set reads as none`() {
        assertEquals(
            SeasonalPalette.NONE,
            SettingsUiModel.seasonalPalette(fallColorsEnabled = false, winterColorsEnabled = false),
        )
    }

    @Test
    fun `fall flag reads as autumn`() {
        assertEquals(
            SeasonalPalette.AUTUMN,
            SettingsUiModel.seasonalPalette(fallColorsEnabled = true, winterColorsEnabled = false),
        )
    }

    @Test
    fun `winter flag reads as winter`() {
        assertEquals(
            SeasonalPalette.WINTER,
            SettingsUiModel.seasonalPalette(fallColorsEnabled = false, winterColorsEnabled = true),
        )
    }

    @Test
    fun `both palette flags set resolve to winter rather than throwing`() {
        assertEquals(
            SeasonalPalette.WINTER,
            SettingsUiModel.seasonalPalette(fallColorsEnabled = true, winterColorsEnabled = true),
        )
    }

    @Test
    fun `palettes write the same flag pairs the two switches wrote`() {
        assertEquals(false to false, SettingsUiModel.seasonalPaletteFlags(SeasonalPalette.NONE))
        assertEquals(true to false, SettingsUiModel.seasonalPaletteFlags(SeasonalPalette.AUTUMN))
        assertEquals(false to true, SettingsUiModel.seasonalPaletteFlags(SeasonalPalette.WINTER))
    }

    @Test
    fun `every palette round-trips through its flags`() {
        for (palette in SeasonalPalette.entries) {
            val (fall, winter) = SettingsUiModel.seasonalPaletteFlags(palette)
            assertEquals(palette, SettingsUiModel.seasonalPalette(fall, winter))
        }
    }

    @Test
    fun `palette option order matches the segmented control labels`() {
        assertEquals(0, SeasonalPalette.NONE.ordinal) // "None"
        assertEquals(1, SeasonalPalette.AUTUMN.ordinal) // "Autumn"
        assertEquals(2, SeasonalPalette.WINTER.ordinal) // "Winter"
    }

    @Test
    fun `the default customization wears no seasonal palette`() {
        val defaults = SceneCustomization.DEFAULT
        assertEquals(
            SeasonalPalette.NONE,
            SettingsUiModel.seasonalPalette(defaults.fallColorsEnabled, defaults.winterColorsEnabled),
        )
    }

    // -- Live Weather (P1-1) -------------------------------------------------------------------
    //
    // The bug these pin was reachable and persistent: Live Weather on, then Location set to Off
    // (or "Follow real time" switched off). The switch went disabled while reading on, World &
    // scene locked clouds and precipitation behind it, and every instruction on screen told the
    // user to do the one thing the UI would not let them do.

    private fun liveWeather(
        enabled: Boolean = true,
        followRealTime: Boolean = true,
        mode: LocationMode = LocationMode.GPS,
        status: LiveWeatherStatus = LiveWeatherStatus.OK,
    ) = SettingsUiModel.liveWeather(enabled, followRealTime, mode, status)

    @Test
    fun `a Live Weather switch that is on can always be switched off`() {
        // Every way the prerequisites can fail, with the setting already on. There is no
        // combination in which the user is locked in.
        for (followRealTime in listOf(true, false)) {
            for (mode in LocationMode.entries) {
                for (status in LiveWeatherStatus.entries) {
                    assertTrue(
                        "on + followRealTime=$followRealTime, $mode, $status must stay switchable",
                        liveWeather(enabled = true, followRealTime = followRealTime, mode = mode, status = status)
                            .switchIsInteractive,
                    )
                }
            }
        }
    }

    @Test
    fun `case A - location switched off with Live Weather on`() {
        val state = liveWeather(enabled = true, mode = LocationMode.OFF, status = LiveWeatherStatus.NO_LOCATION)

        assertFalse("nothing to fetch for", state.canBeTurnedOn)
        assertTrue("but the way out has to stay open", state.switchIsInteractive)
        assertFalse("and the scene is on the theme's own weather", state.drivingTheScene)
    }

    @Test
    fun `case B - follow real time switched off with Live Weather on`() {
        val state = liveWeather(enabled = true, followRealTime = false, status = LiveWeatherStatus.OK)

        assertFalse(state.canBeTurnedOn)
        assertTrue(state.switchIsInteractive)
    }

    @Test
    fun `a Live Weather switch that is off stays gated on its prerequisites`() {
        assertFalse(
            "turning it on with no location would produce exactly the state we just made escapable",
            liveWeather(enabled = false, mode = LocationMode.OFF, status = LiveWeatherStatus.OFF).switchIsInteractive,
        )
        assertFalse(
            liveWeather(enabled = false, followRealTime = false, status = LiveWeatherStatus.OFF).switchIsInteractive,
        )
        assertTrue(
            liveWeather(enabled = false, mode = LocationMode.CUSTOM, status = LiveWeatherStatus.OFF).switchIsInteractive,
        )
    }

    @Test
    fun `only a forecast actually in effect may claim to be driving the scene`() {
        // OK and STALE are the two states with a snapshot behind them; the rest are the states in
        // which LiveWeatherStatus itself already says the theme's weather is showing.
        assertTrue(liveWeather(status = LiveWeatherStatus.OK).drivingTheScene)
        assertTrue(liveWeather(status = LiveWeatherStatus.STALE).drivingTheScene)
        for (status in listOf(
            LiveWeatherStatus.OFF,
            LiveWeatherStatus.NO_LOCATION,
            LiveWeatherStatus.MISSING_API_KEY,
            LiveWeatherStatus.FAILED,
        )) {
            assertFalse("$status is not a forecast", liveWeather(status = status).drivingTheScene)
        }
    }

    @Test
    fun `driving the scene and running on the theme's weather are exact opposites while on`() {
        // The contradiction the two screens used to show at once: World & scene said "Driven by
        // Live Weather" while Weather & time showed the fallback banner. They now read one fact.
        for (status in LiveWeatherStatus.entries) {
            val driving = liveWeather(status = status).drivingTheScene
            if (status == LiveWeatherStatus.OFF) continue // "not reported yet", claimed by neither
            assertEquals("$status", status.isRunningOnThemeWeather, !driving)
        }
    }

    @Test
    fun `the switch being off means nothing is driving the scene, whatever the last status said`() {
        for (status in LiveWeatherStatus.entries) {
            assertFalse(liveWeather(enabled = false, status = status).drivingTheScene)
        }
    }
}
