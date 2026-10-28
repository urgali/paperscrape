package com.paperscrape.livewallpaper.prefs

import com.paperscrape.livewallpaper.engine.CustomThemeData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A backup gives back the place name and the weather keys the app kept, however long a real one can be.**
 *
 * The place's name and the three API keys are text fields the preferences keep up to [AppBackup.MAX_TEXT_SETTING_CHARS]
 * characters ([keptText]): a name of 300 characters is the user's as much as one of 30. An import that leaves a text
 * longer than a few words at the value the setting had loses such a name, and says nothing; it fails the first test
 * here. The import keeps the same ceiling, against a file padded to megabytes, and holds it at its own length: the
 * second.
 */
class BackupLongTextSettingsTest {

    /** [length] characters made of [words], not one letter repeated: a text the app could hold. */
    private fun text(words: String, length: Int): String =
        buildString { while (this.length < length) append(words) }.take(length)

    /** [mine]'s settings written to a backup and read back over [over]. */
    private fun restored(mine: WallpaperSettings, over: WallpaperSettings = WallpaperSettings()): AppBackup.BackupSettings {
        val parsed = parseAppBackup(AppBackup.from(mine, CustomThemeData.EMPTY, "5.17", 0L).toJsonString(), over)
        assertTrue("$parsed", parsed is BackupParseResult.Ok)
        return (parsed as BackupParseResult.Ok).backup.settings
    }

    @Test
    fun `a place name and keys of 300 characters come back from a backup`() {
        val place = text("Llanfairpwllgwyngyll, Isle of Anglesey, Wales, United Kingdom; ", 300)
        val key = text("0123456789abcdef", 300)
        val back = restored(
            WallpaperSettings(
                customLocationLabel = place,
                liveWeatherApiKey = key,
                weatherApiComApiKey = key,
                openWeatherApiKey = key,
            ),
        )
        assertEquals("the place name", place, back.customLocationLabel)
        assertEquals("the Open-Meteo key", key, back.liveWeatherApiKey)
        assertEquals("the WeatherAPI.com key", key, back.weatherApiComApiKey)
        assertEquals("the OpenWeather key", key, back.openWeatherApiKey)
    }

    @Test
    fun `a text as long as the ceiling comes back, and one a character longer leaves the setting as it was`() {
        val atCeiling = text("Somewhere far away ", AppBackup.MAX_TEXT_SETTING_CHARS)
        assertEquals(atCeiling, restored(WallpaperSettings(customLocationLabel = atCeiling)).customLocationLabel)
        val longer = restored(
            WallpaperSettings(customLocationLabel = atCeiling + "x"),
            over = WallpaperSettings(customLocationLabel = "Florence"),
        )
        assertEquals("Florence", longer.customLocationLabel)
    }
}
