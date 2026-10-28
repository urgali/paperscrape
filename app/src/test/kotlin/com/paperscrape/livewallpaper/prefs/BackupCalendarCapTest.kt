package com.paperscrape.livewallpaper.prefs

import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.seasonalCalendarFromJsonString
import com.paperscrape.livewallpaper.engine.toJsonString
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **What a backup file can put into the preferences has a ceiling.**
 *
 * The settings read the holiday calendar again at every change of a setting, on the main thread of the wallpaper and of
 * the settings screen: a valid calendar padded to a few million characters and stored as the file wrote it -- the whole
 * file's 4 M the only limit -- would make every tap seconds long, and every write of the preferences megabytes. So an
 * import reads the calendar and keeps it only as a calendar can be, and one that moves no window, however written, is
 * stored as nothing, by the rule that only the difference is stored; a place name or an API key longer than any real
 * one keeps the setting as it was. A [parseAppBackup] that lets the padded calendar or the long strings through as
 * written fails here on its assertions.
 */
class BackupCalendarCapTest {

    private fun document(settings: JSONObject.() -> Unit): String {
        val root = JSONObject(AppBackup.from(WallpaperSettings(), CustomThemeData.EMPTY, "5.17", 0L).toJsonString())
        root.getJSONObject("settings").settings()
        return root.toString()
    }

    private fun settingsOf(raw: String, defaults: WallpaperSettings = WallpaperSettings()): AppBackup.BackupSettings {
        val parsed = parseAppBackup(raw, defaults)
        assertTrue("$parsed", parsed is BackupParseResult.Ok)
        return (parsed as BackupParseResult.Ok).backup.settings
    }

    /** Halloween moved to the 20th of October to the 2nd of November, as the app writes it. */
    private val moved = """{"schemaVersion":1,"windows":{"halloween":{"start":"10-20","end":"11-02"}}}"""

    @Test
    fun `a calendar padded to megabytes is kept as the calendar it is`() {
        val padded = moved.replace("{\"halloween\"", "{" + " ".repeat(3_000_000) + "\"halloween\"")
        val stored = settingsOf(document { put("seasonalCalendar", padded) }).seasonalCalendarJson
        assertTrue("${stored.length} characters stored", stored.length <= AppBackup.MAX_CALENDAR_CHARS)
        assertEquals("and it is the same calendar", seasonalCalendarFromJsonString(padded), seasonalCalendarFromJsonString(stored))
    }

    @Test
    fun `a calendar that moves nothing is stored as nothing`() {
        for (factory in listOf("""{"windows":{}}""", """{"schemaVersion":1,"windows":{"made-up":{"start":"01-01","end":"01-02"}}}""", "not json")) {
            assertEquals(factory, "", settingsOf(document { put("seasonalCalendar", factory) }).seasonalCalendarJson)
        }
    }

    @Test
    fun `a calendar as the app writes it is kept as it is written`() {
        assertEquals(moved, settingsOf(document { put("seasonalCalendar", moved) }).seasonalCalendarJson)
    }

    @Test
    fun `a place name or a key longer than any real one keeps the setting as it was`() {
        val mine = WallpaperSettings(customLocationLabel = "Florence", openWeatherApiKey = "test-openweather-key")
        val long = "x".repeat(1_000_000)
        val settings = settingsOf(
            document {
                put("customLocationLabel", long)
                put("liveWeatherApiKey", long)
                put("weatherApiComApiKey", long)
                put("openWeatherApiKey", long)
            },
            defaults = mine,
        )
        assertEquals("Florence", settings.customLocationLabel)
        assertEquals("", settings.liveWeatherApiKey)
        assertEquals("", settings.weatherApiComApiKey)
        assertEquals("test-openweather-key", settings.openWeatherApiKey)
    }

    @Test
    fun `a real place name and real keys come through`() {
        val settings = settingsOf(
            document {
                put("customLocationLabel", "Reggio nell'Emilia, Emilia-Romagna, Italy")
                put("weatherApiComApiKey", "test-weatherapi-key")
            },
        )
        assertEquals("Reggio nell'Emilia, Emilia-Romagna, Italy", settings.customLocationLabel)
        assertEquals("test-weatherapi-key", settings.weatherApiComApiKey)
    }
}
