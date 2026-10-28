package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.engine.SeasonalCalendar
import com.paperscrape.livewallpaper.engine.coverage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Three lines of the settings that say no more than is true:**
 *
 *  - under *Shuffle a random theme*, "Random theme active" only while the random theme is what shows -- not over the
 *    calendar's scene, with a random theme picked and *Automatic theme by date* showing another today;
 *  - the first line of the Holiday calendar, "The calendar picks a theme for each day", only over a calendar with no
 *    day that no season covers; over one with such days, which the red line under it names, it says a holiday or a season;
 *  - under *Live Weather*, that this theme's own Clouds and Rain and snow screens leave those to the forecast only while
 *    the forecast drives the sky: with the switch on and no forecast yet or none to be had, the theme's own weather
 *    shows, and so do its controls.
 */
class ScreenLinesTellTheTruthTest {

    @Test
    fun `Random theme active only while the random theme is what shows`() {
        assertEquals("Random theme active - tap again to generate another one", SettingsUiModel.shuffleLine("random:42", calendarThemeId = null))
        assertEquals("Builds a new theme from scratch and selects it", SettingsUiModel.shuffleLine("random:42", calendarThemeId = "halloween"))
        assertEquals("Builds a new theme from scratch and selects it", SettingsUiModel.shuffleLine("autumn", calendarThemeId = null))
        assertTrue(codeLines("ui/SettingsScreen.kt").contains("supporting = SettingsUiModel.shuffleLine(settings.themeId, calendarThemeId),"))
    }

    @Test
    fun `the calendar's first line says each day only where every day is covered`() {
        assertTrue(SeasonalCalendar.DEFAULT.coverage().hasGaps.not())
        assertEquals(
            "The calendar picks a theme for each day. Holidays sit on top; the seasons underneath cover the rest of the year.",
            SettingsUiModel.calendarBanner(hasGaps = false),
        )
        assertEquals(
            "The calendar picks a theme for each day a holiday or a season covers. Holidays sit on top of the seasons; " +
                "the days no season covers are named below.",
            SettingsUiModel.calendarBanner(hasGaps = true),
        )
        assertTrue(codeLines("ui/HolidayCalendarScreen.kt").contains("SettingsBanner(SettingsUiModel.calendarBanner(coverage.hasGaps))"))
    }

    @Test
    fun `the Live Weather line leaves the screens to the forecast only while it drives the sky`() {
        val strings = repoFile("app/src/main/res/values/strings.xml").readText()
        val line = Regex("""<string name="live_weather_desc">([^<]*)</string>""").find(strings)!!.groupValues[1]
        assertFalse("while the switch is merely on", line.contains("While on, this theme"))
        assertTrue(line.contains("While the forecast drives the sky, this theme\\'s own Clouds and Rain and snow screens leave those to it"))
    }

    /**
     * The lines of the app's source file [path], trimmed, without comments: a line comment cut off, a KDoc or block comment
     * line dropped. A screen's use of a line is a whole line of it, so a comment that quotes the call, or a call that says
     * more than it, is not one.
     */
    private fun codeLines(path: String): List<String> =
        repoFile("app/src/main/kotlin/com/paperscrape/livewallpaper/$path").readLines().map { it.trim() }
            .filterNot { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }
            .map { it.substringBefore(" //").trim() }
            .filter { it.isNotEmpty() }

    private fun repoFile(path: String): java.io.File {
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "../")) {
                val f = java.io.File(dir, prefix + path)
                if (f.isFile) return f
            }
            dir = dir.parentFile
        }
        error("no $path")
    }
}
