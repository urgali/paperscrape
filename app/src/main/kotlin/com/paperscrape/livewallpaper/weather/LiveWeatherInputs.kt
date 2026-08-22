package com.paperscrape.livewallpaper.weather

import com.paperscrape.livewallpaper.prefs.WallpaperSettings

/**
 * Which settings a Live Weather fetch actually depends on.
 *
 * Extracted and made pure because "does this change require a fetch now, rather than at the next
 * hourly tick" is the rule the immediate-refresh guarantee rests on, and it is the kind of rule
 * that quietly rots: v2.13's version compared the toggle and the Open-Meteo key, which was
 * complete at the time and stopped being complete the moment a second provider and a second key
 * existed. Listing the inputs in one place, with a test over it, is what keeps the next one from
 * being forgotten.
 *
 * The location is deliberately **not** here. A change of location is handled by the loop's own
 * "these are the wrong conditions entirely" comparison against the coordinates the last fetch was
 * made for, which catches a moved custom location and an arriving GPS fix alike.
 */
object LiveWeatherInputs {

    /**
     * True when the next fetch would use different inputs from the last one, so the cached
     * hourly timer must be ignored and a request made now.
     *
     * Switching the feature on is the obvious case. Switching provider is the same thing by
     * another route -- the conditions on screen came from a service the user has just stopped
     * using. Entering a key is too: the selected provider may have been unable to send anything
     * at all until that moment.
     */
    fun changed(previous: WallpaperSettings, next: WallpaperSettings): Boolean =
        previous.liveWeatherEnabled != next.liveWeatherEnabled ||
            previous.weatherProviderId != next.weatherProviderId ||
            previous.liveWeatherApiKey != next.liveWeatherApiKey ||
            previous.weatherApiComApiKey != next.weatherApiComApiKey
}
