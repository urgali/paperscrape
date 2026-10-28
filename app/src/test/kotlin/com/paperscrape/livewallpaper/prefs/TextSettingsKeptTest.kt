package com.paperscrape.livewallpaper.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **The app keeps a place's name and a key no longer than a backup gives back.** A backup restores a text setting of
 * at most [AppBackup.MAX_TEXT_SETTING_CHARS] characters, so the settings keep that many of whatever is typed or pasted;
 * a character made of two surrogates is not cut in half.
 */
class TextSettingsKeptTest {

    private class Store : DataStore<Preferences> {
        val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> get() = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    @Test
    fun `a pasted text longer than a backup restores is kept as long as a backup restores`() = runBlocking {
        val long = "x".repeat(AppBackup.MAX_TEXT_SETTING_CHARS + 500)
        val prefs = WallpaperPrefs(Store(), savedLookFor = { null })
        prefs.setCustomLocation(45.46f, 9.19f, long)
        prefs.setLiveWeatherApiKey(long)
        prefs.setWeatherApiComApiKey(long)
        prefs.setOpenWeatherApiKey(long)
        val settings = prefs.settingsFlow.first()
        val kept = "x".repeat(AppBackup.MAX_TEXT_SETTING_CHARS)
        assertEquals("the place's name", kept, settings.customLocationLabel)
        assertEquals("Open-Meteo's key", kept, settings.liveWeatherApiKey)
        assertEquals("WeatherAPI.com's key", kept, settings.weatherApiComApiKey)
        assertEquals("OpenWeather's key", kept, settings.openWeatherApiKey)
    }

    @Test
    fun `a text the ceiling would cut inside a character keeps the character whole or not at all`() = runBlocking {
        val prefs = WallpaperPrefs(Store(), savedLookFor = { null })
        prefs.setLiveWeatherApiKey("short")
        assertEquals("short", prefs.settingsFlow.first().liveWeatherApiKey)
        val exact = "y".repeat(AppBackup.MAX_TEXT_SETTING_CHARS)
        prefs.setLiveWeatherApiKey(exact)
        assertEquals(exact, prefs.settingsFlow.first().liveWeatherApiKey)
        val straddling = "y".repeat(AppBackup.MAX_TEXT_SETTING_CHARS - 1) + "🎃"
        prefs.setLiveWeatherApiKey(straddling)
        assertEquals("y".repeat(AppBackup.MAX_TEXT_SETTING_CHARS - 1), prefs.settingsFlow.first().liveWeatherApiKey)
    }
}
