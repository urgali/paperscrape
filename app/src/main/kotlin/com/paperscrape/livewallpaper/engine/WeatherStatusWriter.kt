package com.paperscrape.livewallpaper.engine

/**
 * **Which wallpaper engine writes the Live Weather status the settings screen reads** (`live_weather_status`,
 * `DESIGN_NOTES.md` §12, decision 108): the one last on screen, of those that are not a picker's preview.
 *
 * A phone can run two engines of the wallpaper at once -- the home screen's and the lock screen's set apart (Android 14
 * and later), or one on a second screen -- each with its own weather loop, its own fetches and its own answer, and one
 * key to write it into. Each writing whenever its own answer changed, the key held whichever wrote last, about a scene
 * the user may not be looking at. So the engine the user last saw is the writer, and an engine coming on screen
 * becomes the writer and writes its own answer at once ([shown]): the settings screen, opened from the home screen,
 * reads the home screen's. Before any engine has been on screen any may write ([mayWrite]): there is nothing else to
 * say. An engine gone stops being the writer ([gone]).
 *
 * Main thread only, as the engines' callbacks are: no lock. Pure, so every order of two engines is a JVM test
 * (`WeatherStatusWriterTest`).
 */
internal class WeatherStatusWriter {
    private var writer = NONE
    private var nextEngine = 0

    /** A number for a new engine, never one an engine of this process had: the writer is the process's, not a service's. */
    fun numberForNewEngine(): Int = nextEngine++

    /**
     * Engine [engine] came on screen. True when it has just become the writer -- it was not -- and must write its own
     * answer now, whatever it wrote before: another engine may have written over it.
     */
    fun shown(engine: Int): Boolean {
        if (writer == engine) return false
        writer = engine
        return true
    }

    /** Whether engine [engine] may write the status: it is the writer, or no engine has been on screen yet. */
    fun mayWrite(engine: Int): Boolean = writer == NONE || writer == engine

    /** Engine [engine] is gone: if it was the writer, the next engine on screen will be. */
    fun gone(engine: Int) {
        if (writer == engine) writer = NONE
    }

    private companion object {
        const val NONE = -1
    }
}

/** The one writer of this process: [PaperWallpaperService]'s engines that are not previews ask it and tell it. */
internal val LiveWeatherStatusWriter = WeatherStatusWriter()
