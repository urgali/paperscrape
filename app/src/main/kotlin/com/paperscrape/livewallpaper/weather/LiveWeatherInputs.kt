package com.paperscrape.livewallpaper.weather

import com.paperscrape.livewallpaper.prefs.WallpaperSettings

/**
 * Which settings a Live Weather fetch actually depends on.
 *
 * Pure, and in one place, because "does this change require a fetch now, rather than at the next hourly tick" is the rule
 * the immediate-refresh guarantee rests on: listing the inputs here, with a test over them, is what keeps one from being
 * forgotten when a provider or a key is added.
 *
 * The inputs are the switch, the provider and **the selected provider's key**
 * ([WallpaperSettings.apiKeyForWeatherProvider]), the one a request is made with. The key of a provider not selected is
 * not an input: typing it changes no request, so it neither forces a fetch nor drops the forecast on screen (decision
 * 108). The two are tied by that one property, which the request reads too, so the key compared is always the key sent.
 *
 * The location is deliberately **not** here. A change of location is handled by the loop's own
 * "these are the wrong conditions entirely" comparison against the coordinates the last fetch was
 * made for, which catches a moved custom location and an arriving GPS fix alike.
 */
object LiveWeatherInputs {

    /**
     * True when a setting the next fetch depends on has changed -- the switch, the provider, or the key of the provider
     * selected in [next] -- so the cached hourly timer must be ignored and a request made now.
     *
     * Switching the feature on is the obvious case. Switching provider is the same thing by another route -- the
     * conditions on screen came from a service the user has just stopped using -- and is a change whatever the keys
     * say. Entering the selected provider's key is too: that provider may have been unable to send anything at all
     * until that moment. With the provider unchanged, [previous] and [next] name the same provider, so the comparison is
     * of that provider's key alone.
     */
    fun changed(previous: WallpaperSettings, next: WallpaperSettings): Boolean =
        previous.liveWeatherEnabled != next.liveWeatherEnabled ||
            previous.weatherProviderId != next.weatherProviderId ||
            previous.apiKeyForWeatherProvider != next.apiKeyForWeatherProvider
}
