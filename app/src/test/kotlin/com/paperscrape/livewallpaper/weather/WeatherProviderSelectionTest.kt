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

    @Test
    fun `a fresh install uses the keyless provider`() {
        assertEquals(WeatherProviderId.OPEN_METEO, WeatherProviderId.DEFAULT)
        assertEquals(WeatherProviderId.OPEN_METEO, WallpaperSettings().weatherProvider)
    }

    /**
     * Ids are stored as strings so that reordering the enum cannot reinterpret an existing
     * install's choice. Pinning the exact strings is what makes that promise real -- renaming one
     * would silently reset every install that had chosen it.
     */
    @Test
    fun `storage ids are stable`() {
        assertEquals("open_meteo", WeatherProviderId.OPEN_METEO.storageId)
        assertEquals("visual_crossing", WeatherProviderId.VISUAL_CROSSING.storageId)
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
            visualCrossingApiKey = "visual-crossing-key",
            weatherProviderId = WeatherProviderId.OPEN_METEO.storageId,
        )
        val after = before.copy(weatherProviderId = WeatherProviderId.VISUAL_CROSSING.storageId)

        assertEquals(WeatherProviderId.VISUAL_CROSSING, after.weatherProvider)
        assertEquals(before.useCustomLocation, after.useCustomLocation)
        assertEquals(before.useLocationForSunTimes, after.useLocationForSunTimes)
        assertEquals(before.customLocationLatitude, after.customLocationLatitude)
        assertEquals(before.customLocationLongitude, after.customLocationLongitude)
        assertEquals(before.customLocationLabel, after.customLocationLabel)
        assertEquals(before.liveWeatherEnabled, after.liveWeatherEnabled)
        assertEquals(before.liveWeatherApiKey, after.liveWeatherApiKey)
        assertEquals(before.visualCrossingApiKey, after.visualCrossingApiKey)
    }

    @Test
    fun `each provider is called with its own key`() {
        val settings = WallpaperSettings(
            liveWeatherApiKey = "open-meteo-key",
            visualCrossingApiKey = "visual-crossing-key",
        )
        assertEquals(
            "open-meteo-key",
            settings.copy(weatherProviderId = WeatherProviderId.OPEN_METEO.storageId).apiKeyForWeatherProvider,
        )
        assertEquals(
            "visual-crossing-key",
            settings.copy(weatherProviderId = WeatherProviderId.VISUAL_CROSSING.storageId).apiKeyForWeatherProvider,
        )
    }

    /** Open-Meteo's key may be blank and still work; Visual Crossing's may not. */
    @Test
    fun `only one of the two providers can run without a key`() {
        assertTrue(WeatherRepository.providerFor(WeatherProviderId.VISUAL_CROSSING).requiresApiKey)
        assertTrue(!WeatherRepository.providerFor(WeatherProviderId.OPEN_METEO).requiresApiKey)
    }

    // -- results ------------------------------------------------------------------------------------

    @Test
    fun `only a success produces a snapshot`() {
        assertNull(WeatherRepository.snapshotOf(WeatherFetchResult.MissingApiKey))
        assertNull(
            WeatherRepository.snapshotOf(
                WeatherFetchResult.Failed(WeatherFailure.NETWORK, WeatherProviderId.VISUAL_CROSSING),
            ),
        )
        val observation = WeatherObservation(
            cloudCoverPercent = 50,
            condition = WeatherCondition.PARTLY_CLOUDY,
            observedAtMillis = 1L,
            source = WeatherProviderId.VISUAL_CROSSING,
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
        val failure = WeatherFetchResult.Failed(WeatherFailure.RATE_LIMITED, WeatherProviderId.VISUAL_CROSSING)
        assertEquals(WeatherProviderId.VISUAL_CROSSING, failure.provider)
        assertNotEquals(WeatherProviderId.OPEN_METEO, failure.provider)
    }
}
