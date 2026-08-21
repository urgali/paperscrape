package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.location.DeviceLocationKind

import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import org.junit.Assert.assertEquals
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
}
