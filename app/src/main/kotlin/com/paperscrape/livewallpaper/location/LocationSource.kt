package com.paperscrape.livewallpaper.location

import com.paperscrape.livewallpaper.prefs.WallpaperSettings

/**
 * Where the coordinates currently in use came from.
 *
 * The sources are mutually exclusive by preference, but until this existed nothing at runtime knew
 * *which* one a held fix belonged to -- only that one was held. That is what let a custom location
 * survive a switch to device location: the device path saw "a fix already exists", never started
 * the provider, and kept fetching the other place's weather. A fix is only valid for the source
 * that produced it, and this is what says so.
 *
 * [GPS] and [NETWORK] were one value until v3.0. Splitting them is not cosmetic: they cost
 * different amounts of battery, need different permissions, and a fix from one is not
 * interchangeable with a fix from the other for the purposes of "has the source changed, and must
 * everything held be thrown away".
 */
enum class LocationSource {

    /** No source is on. Sunrise/sunset fall back to their fixed defaults. */
    NONE,

    /** A coarse fix from cell towers and Wi-Fi. The GNSS receiver is never used. */
    NETWORK,

    /** A precise fix from the GNSS receiver. */
    GPS,

    /** Coordinates the user entered or picked, straight out of the settings. */
    CUSTOM,
    ;

    /** The device positioning kind this source needs, or `null` if it needs none. */
    val deviceKind: DeviceLocationKind?
        get() = when (this) {
            NETWORK -> DeviceLocationKind.NETWORK
            GPS -> DeviceLocationKind.GPS
            NONE, CUSTOM -> null
        }

    companion object {

        /**
         * Pure, so the exclusivity rule is pinned by a test rather than by reading the collector.
         *
         * Custom wins when both flags are somehow set. `WallpaperPrefs` already enforces that they
         * cannot be, but a stored pair from an older install is not worth crashing over, and
         * picking the explicit choice over the inferred one is the answer that surprises least.
         */
        fun of(settings: WallpaperSettings): LocationSource = when {
            settings.useCustomLocation -> CUSTOM
            settings.useLocationForSunTimes -> when (settings.deviceLocationKind) {
                DeviceLocationKind.GPS -> GPS
                DeviceLocationKind.NETWORK -> NETWORK
            }
            else -> NONE
        }
    }
}
