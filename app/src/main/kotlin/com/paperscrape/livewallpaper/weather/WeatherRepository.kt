package com.paperscrape.livewallpaper.weather

/**
 * The single door between the wallpaper engine and whichever weather service is selected.
 *
 * The pipeline is `provider -> normalised WeatherObservation -> repository -> scene`, and this is
 * the only step that knows a provider was chosen at all. Providers know nothing about
 * preferences, scheduling or the renderer; the engine knows nothing about endpoints or response
 * shapes.
 *
 * **No silent fallback between providers.** If the selected provider fails, the result says so and
 * the selection stands. Quietly answering with a different service would make "which provider am I
 * using" unanswerable, and the existing behaviour on failure -- keep the last good snapshot, and
 * otherwise let the theme's own weather run -- is already the right one.
 */
object WeatherRepository {

    private val providers: Map<WeatherProviderId, WeatherProvider> = mapOf(
        WeatherProviderId.OPEN_METEO to OpenMeteoProvider,
        WeatherProviderId.WEATHER_API_COM to WeatherApiComProvider,
    )

    fun providerFor(id: WeatherProviderId): WeatherProvider = providers.getValue(id)

    /**
     * Fetches from [providerId] and translates the answer into the renderer's vocabulary.
     *
     * Returns the full [WeatherFetchResult] rather than a nullable snapshot so the caller can tell
     * "no key configured" from "the network dropped" -- one is a state for the settings screen to
     * explain and the other is a transient the loop should simply try again after.
     */
    suspend fun fetchCurrentConditions(
        providerId: WeatherProviderId,
        latitude: Double,
        longitude: Double,
        apiKey: String,
    ): WeatherFetchResult = providerFor(providerId).fetch(latitude, longitude, apiKey)

    /** [WeatherSnapshotMapper] applied to a successful fetch; null for anything else. */
    fun snapshotOf(result: WeatherFetchResult): LiveWeatherSnapshot? =
        (result as? WeatherFetchResult.Success)?.let { WeatherSnapshotMapper.toSnapshot(it.observation) }
}
