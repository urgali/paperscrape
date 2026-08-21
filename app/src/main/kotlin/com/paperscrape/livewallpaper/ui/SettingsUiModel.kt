package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.location.DeviceLocationKind

/**
 * Which location source is in use, as one choice instead of two switches.
 *
 * **Presentation only.** Nothing is stored under this name. The wallpaper has always had two
 * independent, mutually exclusive boolean preferences -- `useLocationForSunTimes` and
 * `useCustomLocation` -- and `WallpaperPrefs.setUseLocation` / `setUseCustomLocation` are what
 * enforce that only one of them is ever true. Three booleans-worth of states were expressed as
 * two switches whose subtitles each had to explain the other; this enum names the three states
 * the pair can actually be in, and [SettingsUiModel.locationFlags] maps back to exactly the same
 * pair of writes the two switches performed.
 */
enum class LocationMode { OFF, GPS, NETWORK, CUSTOM }

/**
 * Which seasonal palette the current theme is wearing, as one choice instead of two switches.
 *
 * **Presentation only**, for the same reason as [LocationMode]: `fallColorsEnabled` and
 * `winterColorsEnabled` are two readings of the same leaves and cannot both be true (see
 * `WallpaperPrefs.setFallColorsEnabled` / `setWinterColorsEnabled`, each of which clears the
 * other). Christmas lights are deliberately *not* part of this choice -- they hang on top of
 * whatever the trees look like and stay an independent switch.
 */
enum class SeasonalPalette { NONE, AUTUMN, WINTER }

/**
 * The translation layer between the settings UI's grouped choices and the preference flags that
 * have always backed them.
 *
 * Deliberately free of Compose and Android imports so the mapping is unit-testable on the JVM:
 * the reason the two segmented controls introduced in v2.9 cannot silently change behaviour is
 * that both directions of both mappings are pinned by `SettingsUiModelTest`.
 */
object SettingsUiModel {

    /**
     * Reads the stored flags and the stored positioning kind as one mode.
     *
     * The device flag is checked first only as a tie-break for a state the preferences layer does
     * not produce (both true); it cannot arise through either setter.
     *
     * An install from before v3.0 has no stored kind, and `WallpaperSettings` defaults it to
     * `NETWORK` -- which is what the single old "Phone" mode already used in practice, so those
     * users find the control on Network rather than on a mode they never chose.
     */
    fun locationMode(
        useDeviceLocation: Boolean,
        useCustomLocation: Boolean,
        deviceKind: DeviceLocationKind = DeviceLocationKind.NETWORK,
    ): LocationMode = when {
        useDeviceLocation -> when (deviceKind) {
            DeviceLocationKind.GPS -> LocationMode.GPS
            DeviceLocationKind.NETWORK -> LocationMode.NETWORK
        }
        useCustomLocation -> LocationMode.CUSTOM
        else -> LocationMode.OFF
    }

    /**
     * The flag pair a given mode means: `(useDeviceLocation, useCustomLocation)`.
     *
     * Both device modes set the same device flag -- which of the two systems it means is
     * [deviceKindFor], stored alongside rather than folded into this pair. `OFF` clears both,
     * which is the state a fresh install starts in.
     */
    fun locationFlags(mode: LocationMode): Pair<Boolean, Boolean> = when (mode) {
        LocationMode.OFF -> false to false
        LocationMode.GPS, LocationMode.NETWORK -> true to false
        LocationMode.CUSTOM -> false to true
    }

    /** The positioning system a mode means, or `null` for the two that use no device sensor. */
    fun deviceKindFor(mode: LocationMode): DeviceLocationKind? = when (mode) {
        LocationMode.GPS -> DeviceLocationKind.GPS
        LocationMode.NETWORK -> DeviceLocationKind.NETWORK
        LocationMode.OFF, LocationMode.CUSTOM -> null
    }

    /** Reads the pair of stored palette flags as one choice. */
    fun seasonalPalette(fallColorsEnabled: Boolean, winterColorsEnabled: Boolean): SeasonalPalette = when {
        winterColorsEnabled -> SeasonalPalette.WINTER
        fallColorsEnabled -> SeasonalPalette.AUTUMN
        else -> SeasonalPalette.NONE
    }

    /** The flag pair a given palette means: `(fallColorsEnabled, winterColorsEnabled)`. */
    fun seasonalPaletteFlags(palette: SeasonalPalette): Pair<Boolean, Boolean> = when (palette) {
        SeasonalPalette.NONE -> false to false
        SeasonalPalette.AUTUMN -> true to false
        SeasonalPalette.WINTER -> false to true
    }
}
