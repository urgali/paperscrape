package com.paperscrape.livewallpaper.weather

/**
 * What Live Weather is actually doing right now, written by the wallpaper service and read by the
 * settings screen.
 *
 * It replaces v2.13's single `liveWeatherFallbackActive` flag, which could only say "the scene is
 * running on the theme's own weather" and not why. With a second provider there are now reasons
 * that need different answers from the UI -- a missing key is something the user can fix in one
 * tap, a dropped request is something to wait out -- and a boolean cannot carry that.
 *
 * Stored by [storageId] for the same reason [WeatherProviderId] is: an enum's order is not a
 * storage format.
 */
enum class LiveWeatherStatus(val storageId: String) {

    /** Live Weather is off. The theme's own clouds and precipitation are the scene, as designed. */
    OFF("off"),

    /** Real conditions are driving the scene. */
    OK("ok"),

    /**
     * On, but there is nowhere to check.
     *
     * Not a breakage: the scene keeps running on the theme's own weather, which is a valid scene.
     * The failure this state exists to prevent is the switch looking dead with nothing saying why.
     */
    NO_LOCATION("no_location"),

    /**
     * The selected provider needs an API key and none is configured.
     *
     * **No request is made in this state.** The provider stays selected -- switching to another
     * one behind the user's back would make "which provider am I using" unanswerable.
     */
    MISSING_API_KEY("missing_api_key"),

    /** The last fetch failed and there is no previous snapshot, so the theme's weather is showing. */
    FAILED("failed"),

    /**
     * The last fetch failed but an earlier snapshot is still driving the scene.
     *
     * Deliberately not the same as [FAILED]: a single dropped request must not momentarily revert
     * the scene to the theme's manual settings, so the last known-good conditions stay up.
     */
    STALE("stale"),
    ;

    /** True when the scene is running on the theme's own weather rather than on real conditions. */
    val isRunningOnThemeWeather: Boolean
        get() = this == NO_LOCATION || this == MISSING_API_KEY || this == FAILED

    companion object {
        fun fromStorageId(id: String?): LiveWeatherStatus =
            entries.firstOrNull { it.storageId == id } ?: OFF

        /**
         * The status a single loop pass implies.
         *
         * Pure, and separated from the service, because it is the rule that decides what the
         * settings screen says and it has six outcomes that are easy to get subtly wrong -- in
         * particular, that a failure with a snapshot still in effect is [STALE] and not [FAILED].
         */
        fun of(
            enabled: Boolean,
            hasLocation: Boolean,
            result: WeatherFetchResult?,
            hasSnapshotInEffect: Boolean,
            previous: LiveWeatherStatus,
        ): LiveWeatherStatus = when {
            !enabled -> OFF
            !hasLocation -> NO_LOCATION
            result is WeatherFetchResult.MissingApiKey -> MISSING_API_KEY
            result is WeatherFetchResult.Success -> OK
            result is WeatherFetchResult.Failed -> if (hasSnapshotInEffect) STALE else FAILED
            // No fetch was due this pass, so nothing new is known. Carrying the previous status
            // forward is the only honest answer -- recomputing one from the snapshot alone would
            // turn a remembered failure into an OK the moment the loop ticked.
            previous == OFF && hasSnapshotInEffect -> OK
            previous == OFF -> NO_LOCATION
            else -> previous
        }
    }
}
