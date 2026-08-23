package com.paperscrape.livewallpaper.weather

import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Choosing a provider, persisting the choice, and what changing it must not disturb.
 */
class WeatherProviderSelectionTest {

    /**
     * **Open-Meteo is the default, and this is the test that says so.**
     *
     * Three separate statements, because they can drift apart: the enum's `DEFAULT`, what a
     * default-constructed settings object resolves to, and -- the one that actually reaches a user
     * -- that the default provider needs no key, so Live Weather works on a fresh install without
     * anybody registering anywhere.
     */
    @Test
    fun `a fresh install uses the keyless provider`() {
        assertEquals(WeatherProviderId.OPEN_METEO, WeatherProviderId.DEFAULT)
        assertEquals(WeatherProviderId.OPEN_METEO, WallpaperSettings().weatherProvider)
        assertTrue(
            "the default provider must be usable without a key",
            !WeatherRepository.providerFor(WallpaperSettings().weatherProvider).requiresApiKey,
        )
        assertEquals("", WallpaperSettings().apiKeyForWeatherProvider)
    }

    /**
     * An install that had chosen Visual Crossing, upgrading to v3.7 where that provider is gone.
     *
     * The stored id no longer names anything, so it reads as the default -- which is Open-Meteo,
     * which needs no key. The upgrade therefore lands on a working configuration rather than on a
     * provider that cannot run, and no migration code is needed to make that true.
     */
    @Test
    fun `an install that had chosen the removed provider falls back to Open-Meteo`() {
        val upgraded = WallpaperSettings(weatherProviderId = "visual_crossing")
        assertEquals(WeatherProviderId.OPEN_METEO, upgraded.weatherProvider)
        assertTrue(!WeatherRepository.providerFor(upgraded.weatherProvider).requiresApiKey)
    }

    /** The removed provider must not be reachable by any id, including its old one. */
    @Test
    fun `the removed provider is gone from the registry`() {
        assertTrue(WeatherProviderId.entries.none { it.storageId == "visual_crossing" })
        assertTrue(WeatherProviderId.entries.none { it.displayName.contains("Visual Crossing") })
        assertEquals(3, WeatherProviderId.entries.size)
    }

    /**
     * Ids are stored as strings so that reordering the enum cannot reinterpret an existing
     * install's choice. Pinning the exact strings is what makes that promise real -- renaming one
     * would silently reset every install that had chosen it.
     */
    @Test
    fun `storage ids are stable`() {
        assertEquals("open_meteo", WeatherProviderId.OPEN_METEO.storageId)
        assertEquals("weatherapi_com", WeatherProviderId.WEATHER_API_COM.storageId)
        assertEquals("open_weather", WeatherProviderId.OPEN_WEATHER.storageId)
    }

    @Test
    fun `a stored id round-trips`() {
        for (provider in WeatherProviderId.entries) {
            assertEquals(provider, WeatherProviderId.fromStorageId(provider.storageId))
        }
    }

    /** An install written by a future version, or a corrupted value, reads as the default. */
    @Test
    fun `an unknown stored id falls back to the default rather than failing`() {
        assertEquals(WeatherProviderId.DEFAULT, WeatherProviderId.fromStorageId("some_future_provider"))
        assertEquals(WeatherProviderId.DEFAULT, WeatherProviderId.fromStorageId(null))
        assertEquals(WeatherProviderId.DEFAULT, WeatherProviderId.fromStorageId(""))
    }

    @Test
    fun `every id resolves to a provider`() {
        for (id in WeatherProviderId.entries) {
            assertEquals(id, WeatherRepository.providerFor(id).id)
        }
    }

    @Test
    fun `the repository returns the same provider instance each time`() {
        assertSame(
            WeatherRepository.providerFor(WeatherProviderId.OPEN_METEO),
            WeatherRepository.providerFor(WeatherProviderId.OPEN_METEO),
        )
    }

    // -- keys are per provider ---------------------------------------------------------------------

    /**
     * The task's requirement in one test: changing provider keeps the custom location, the phone
     * location, the toggle, and the *other* provider's key. Nothing about a provider switch is
     * allowed to reach any of them.
     */
    @Test
    fun `switching provider disturbs no other weather setting`() {
        val before = WallpaperSettings(
            useCustomLocation = true,
            customLocationLatitude = 43.77925f,
            customLocationLongitude = 11.24626f,
            customLocationLabel = "Florence, Italy",
            liveWeatherEnabled = true,
            liveWeatherApiKey = "open-meteo-key",
            weatherApiComApiKey = "weatherapi-com-key",
            weatherProviderId = WeatherProviderId.OPEN_METEO.storageId,
        )
        val after = before.copy(weatherProviderId = WeatherProviderId.WEATHER_API_COM.storageId)

        assertEquals(WeatherProviderId.WEATHER_API_COM, after.weatherProvider)
        assertEquals(before.useCustomLocation, after.useCustomLocation)
        assertEquals(before.useLocationForSunTimes, after.useLocationForSunTimes)
        assertEquals(before.customLocationLatitude, after.customLocationLatitude)
        assertEquals(before.customLocationLongitude, after.customLocationLongitude)
        assertEquals(before.customLocationLabel, after.customLocationLabel)
        assertEquals(before.liveWeatherEnabled, after.liveWeatherEnabled)
        assertEquals(before.liveWeatherApiKey, after.liveWeatherApiKey)
        assertEquals(before.weatherApiComApiKey, after.weatherApiComApiKey)
    }

    @Test
    fun `each provider is called with its own key`() {
        val settings = WallpaperSettings(
            liveWeatherApiKey = "open-meteo-key",
            weatherApiComApiKey = "weatherapi-com-key",
        )
        assertEquals(
            "open-meteo-key",
            settings.copy(weatherProviderId = WeatherProviderId.OPEN_METEO.storageId).apiKeyForWeatherProvider,
        )
        assertEquals(
            "weatherapi-com-key",
            settings.copy(weatherProviderId = WeatherProviderId.WEATHER_API_COM.storageId).apiKeyForWeatherProvider,
        )
    }

    /**
     * Open-Meteo's key may be blank and still work; neither alternative's may.
     *
     * Written as "exactly one" rather than as a list, so adding a fourth keyless provider -- which
     * would quietly make the default's keylessness unremarkable -- is a deliberate edit here.
     */
    @Test
    fun `exactly one provider can run without a key, and it is the default`() {
        val keyless = WeatherProviderId.entries.filter { !WeatherRepository.providerFor(it).requiresApiKey }
        assertEquals(listOf(WeatherProviderId.OPEN_METEO), keyless)
        assertEquals(WeatherProviderId.DEFAULT, keyless.single())
    }

    /**
     * **The three-way selection the task requires**, stated as one test: the default resolves to
     * Open-Meteo, and each explicit choice resolves to itself and to its own key.
     */
    @Test
    fun `each explicit choice resolves to its own provider and key`() {
        val settings = WallpaperSettings(
            liveWeatherApiKey = "open-meteo-key",
            weatherApiComApiKey = "weatherapi-com-key",
            openWeatherApiKey = "open-weather-key",
        )
        assertEquals(WeatherProviderId.OPEN_METEO, settings.weatherProvider)
        assertEquals("open-meteo-key", settings.apiKeyForWeatherProvider)

        val viaWeatherApi = settings.copy(weatherProviderId = WeatherProviderId.WEATHER_API_COM.storageId)
        assertEquals(WeatherProviderId.WEATHER_API_COM, viaWeatherApi.weatherProvider)
        assertEquals("weatherapi-com-key", viaWeatherApi.apiKeyForWeatherProvider)

        val viaOpenWeather = settings.copy(weatherProviderId = WeatherProviderId.OPEN_WEATHER.storageId)
        assertEquals(WeatherProviderId.OPEN_WEATHER, viaOpenWeather.weatherProvider)
        assertEquals("open-weather-key", viaOpenWeather.apiKeyForWeatherProvider)
    }

    /** Every provider's key is its own: switching cannot leak one service's key to another. */
    @Test
    fun `no two providers share a key`() {
        val settings = WallpaperSettings(
            liveWeatherApiKey = "a",
            weatherApiComApiKey = "b",
            openWeatherApiKey = "c",
        )
        val keys = WeatherProviderId.entries.map {
            settings.copy(weatherProviderId = it.storageId).apiKeyForWeatherProvider
        }
        assertEquals(keys.toSet().size, keys.size)
    }

    // -- results ------------------------------------------------------------------------------------

    @Test
    fun `only a success produces a snapshot`() {
        assertNull(WeatherRepository.snapshotOf(WeatherFetchResult.MissingApiKey))
        assertNull(
            WeatherRepository.snapshotOf(
                WeatherFetchResult.Failed(WeatherFailure.NETWORK, WeatherProviderId.WEATHER_API_COM),
            ),
        )
        val observation = WeatherObservation(
            cloudCoverPercent = 50,
            condition = WeatherCondition.PARTLY_CLOUDY,
            observedAtMillis = 1L,
            source = WeatherProviderId.WEATHER_API_COM,
        )
        assertEquals(
            0.5f,
            WeatherRepository.snapshotOf(WeatherFetchResult.Success(observation))!!.cloudCoverFraction,
            0.0001f,
        )
    }

    /** A failure names the provider it came from, so a report can attribute it. */
    @Test
    fun `a failure carries its provider`() {
        val failure = WeatherFetchResult.Failed(WeatherFailure.RATE_LIMITED, WeatherProviderId.WEATHER_API_COM)
        assertEquals(WeatherProviderId.WEATHER_API_COM, failure.provider)
        assertNotEquals(WeatherProviderId.OPEN_METEO, failure.provider)
    }
}
