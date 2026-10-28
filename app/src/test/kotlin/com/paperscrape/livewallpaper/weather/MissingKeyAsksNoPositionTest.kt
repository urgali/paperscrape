package com.paperscrape.livewallpaper.weather

import com.paperscrape.livewallpaper.engine.SolarDaySchedule
import com.paperscrape.livewallpaper.engine.WEATHER_CHECK_INTERVAL_MS
import com.paperscrape.livewallpaper.engine.WEATHER_REFRESH_INTERVAL_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **No search for a position every hour for a weather provider that wants a key nobody entered.**
 *
 * With Live Weather on, a refresh that falls due is a reason to ask the phone where it is ([SolarDaySchedule.onTick])
 * only if the refresh would be sent: the loop hands `onTick` [LiveWeatherSchedule.wouldFetch]. With WeatherAPI.com or
 * OpenWeather chosen and no key the refresh sends nothing ([WeatherFetchResult.MissingApiKey]), and asking anyway
 * would have the GPS search for up to 20 s an hour with the screen on, for a sunrise the position held already gives.
 * `ServiceWiringTest` holds the service to it on its source.
 */
class MissingKeyAsksNoPositionTest {

    @Test
    fun `a refresh is sent only by a provider that has the key it needs`() {
        assertTrue("Open-Meteo needs none", LiveWeatherSchedule.wouldFetch(true, WeatherProviderId.OPEN_METEO, ""))
        assertTrue(LiveWeatherSchedule.wouldFetch(true, WeatherProviderId.OPEN_METEO, "own-key"))
        for (provider in listOf(WeatherProviderId.WEATHER_API_COM, WeatherProviderId.OPEN_WEATHER)) {
            assertFalse("$provider without a key", LiveWeatherSchedule.wouldFetch(true, provider, ""))
            assertFalse("$provider with blanks, which the provider trims to nothing", LiveWeatherSchedule.wouldFetch(true, provider, "   "))
            assertTrue("$provider with one", LiveWeatherSchedule.wouldFetch(true, provider, "0123456789abcdef"))
        }
        for (provider in WeatherProviderId.entries) {
            assertFalse("$provider: Live Weather not running", LiveWeatherSchedule.wouldFetch(false, provider, "0123456789abcdef"))
        }
    }

    /** The provider's own answer agrees: nothing is sent without the key. */
    @Test
    fun `the rule is the providers' own`() {
        for (provider in WeatherProviderId.entries) {
            assertEquals(
                provider.name,
                !WeatherRepository.providerFor(provider).requiresApiKey,
                LiveWeatherSchedule.wouldFetch(true, provider, ""),
            )
        }
    }

    /**
     * Three hours of the loop's two-minute passes with the phone's position held, the day current and a refresh due on
     * the hour: the phone is asked on each hour with a key, and never without one.
     */
    @Test
    fun `three hours with the key missing ask the phone nothing`() {
        fun asks(provider: WeatherProviderId, key: String): Int {
            var asked = 0
            var t = 0L
            while (t < 3 * WEATHER_REFRESH_INTERVAL_MS) {
                val due = t % WEATHER_REFRESH_INTERVAL_MS == 0L
                val action = SolarDaySchedule.onTick(
                    followRealTime = true,
                    liveWeatherEnabled = LiveWeatherSchedule.wouldFetch(true, provider, key),
                    deviceSource = true,
                    devicePositionUsable = true,
                    weatherAttemptDue = due,
                    hasPosition = true,
                    holdsDevicePosition = true,
                    dayIsStale = false,
                )
                if (action == SolarDaySchedule.Action.ASK_DEVICE) asked++
                t += WEATHER_CHECK_INTERVAL_MS
            }
            return asked
        }
        assertEquals("WeatherAPI.com with its key", 3, asks(WeatherProviderId.WEATHER_API_COM, "0123456789abcdef"))
        assertEquals("WeatherAPI.com without", 0, asks(WeatherProviderId.WEATHER_API_COM, ""))
        assertEquals("OpenWeather without", 0, asks(WeatherProviderId.OPEN_WEATHER, ""))
        assertEquals("Open-Meteo, which needs none", 3, asks(WeatherProviderId.OPEN_METEO, ""))
    }
}
