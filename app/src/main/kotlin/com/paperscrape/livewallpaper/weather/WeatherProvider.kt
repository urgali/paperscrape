package com.paperscrape.livewallpaper.weather

/**
 * Which weather service a fetch goes to.
 *
 * Stored by its [storageId] rather than by ordinal, so reordering or inserting a provider cannot
 * silently reinterpret an existing install's preference.
 */
enum class WeatherProviderId(val storageId: String, val displayName: String) {
    OPEN_METEO("open_meteo", "Open-Meteo"),
    WEATHER_API_COM("weatherapi_com", "WeatherAPI.com"),
    ;

    companion object {

        /**
         * What a fresh install uses: the keyless provider, so Live Weather works out of the box.
         *
         * **Open-Meteo is the default and stays the default.** It is the only candidate that needs
         * no key at all, which means no credential ships in the app, none is asked of the user, and
         * Live Weather works the moment a location exists. Everything else here is an alternative,
         * never a replacement for that.
         */
        val DEFAULT = OPEN_METEO

        /** Unknown or absent ids read as [DEFAULT] rather than crashing an install forward. */
        fun fromStorageId(id: String?): WeatherProviderId =
            entries.firstOrNull { it.storageId == id } ?: DEFAULT
    }
}

/**
 * One weather service.
 *
 * A provider's whole job is "coordinates in, [WeatherObservation] out". It owns its endpoint, its
 * query parameters and its response shape, and nothing else: not the schedule, not the cache, not
 * the preferences, not the renderer. [WeatherRepository] is what knows which one is selected and
 * what to do with the answer.
 */
interface WeatherProvider {

    val id: WeatherProviderId

    /**
     * Whether this provider cannot be called at all without a key.
     *
     * Open-Meteo can (its free tier is keyless, and a key only upgrades the endpoint),
     * WeatherAPI.com cannot. The difference is why [WeatherFetchResult.MissingApiKey] exists
     * instead of a provider quietly returning a failure that looks like a network problem.
     */
    val requiresApiKey: Boolean

    /** Never throws. Failures come back as [WeatherFetchResult.Failed]. */
    suspend fun fetch(latitude: Double, longitude: Double, apiKey: String): WeatherFetchResult
}

/** The outcome of one fetch. */
sealed interface WeatherFetchResult {

    data class Success(val observation: WeatherObservation) : WeatherFetchResult

    /**
     * The provider needs a key and none is configured.
     *
     * Distinct from a failure on purpose: nothing was attempted, so there is nothing to retry, and
     * the settings screen has something specific to say. No request is made in this state -- the
     * app never calls a provider it knows will reject it.
     */
    data object MissingApiKey : WeatherFetchResult

    data class Failed(val reason: WeatherFailure, val provider: WeatherProviderId) : WeatherFetchResult
}

/** Why a fetch did not produce an observation. */
enum class WeatherFailure {
    /** The request never completed: no connectivity, DNS, timeout. */
    NETWORK,

    /** The key was rejected. Distinguished from [NETWORK] because retrying will not help. */
    UNAUTHORIZED,

    /** The plan's request budget is spent. Also not worth retrying soon. */
    RATE_LIMITED,

    /** Any other non-200. */
    HTTP_ERROR,

    /** A 200 whose body was not the shape this provider's parser expects. */
    MALFORMED_RESPONSE,
}
