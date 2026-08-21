package com.paperscrape.livewallpaper.location

import com.paperscrape.livewallpaper.prefs.WallpaperSettings

/**
 * Where the coordinates currently in use came from.
 *
 * The two sources are mutually exclusive by preference, but until this existed nothing at runtime
 * knew *which* one a held fix belonged to -- only that one was held. That is what let a custom
 * location survive a switch to phone location: the GPS path saw "a fix already exists", never
 * started the provider, and kept fetching the other place's weather. A fix is only valid for the
 * source that produced it, and this is what says so.
 */
enum class LocationSource {

    /** Neither source is on. Sunrise/sunset fall back to their fixed defaults. */
    NONE,

    /** The device's own coarse fix, from [DeviceLocationProvider]. */
    PHONE,

    /** Coordinates the user entered or picked, straight out of the settings. */
    CUSTOM,
    ;

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
            settings.useLocationForSunTimes -> PHONE
            else -> NONE
        }
    }
}
