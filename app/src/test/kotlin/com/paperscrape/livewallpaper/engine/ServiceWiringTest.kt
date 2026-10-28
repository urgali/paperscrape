package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **The wallpaper's service and the update job call the rules below**, held on their sources.
 *
 * The rules are pure and tested on their own (`SamePlaceFetchTest`, `MissingKeyAsksNoPositionTest`,
 * `MoonAcrossTheClockChangeTest`, `UpdateJobStopTest`); what is held here is that the service's weather loop, its
 * frame, its date receiver and the update job reach them. The engine is a `WallpaperService` inner class and the job a
 * `JobService`, which the JVM cannot build -- the reasoning `WeatherLoopVisibilityTest` and
 * `FixedHourIgnoresLocationTest` give. Reads only the sources: each test fails on its assertion when a line it forbids
 * is there, or a line it needs is not.
 */
class ServiceWiringTest {

    private val engine: String by lazy { source("engine/PaperWallpaperService.kt") }

    /** The body of [declaration], to the closing brace at its own indentation. */
    private fun body(text: String, declaration: String): String {
        val start = text.indexOf(declaration)
        require(start >= 0) { "$declaration is gone" }
        val indent = text.substring(text.lastIndexOf('\n', start) + 1).takeWhile { it == ' ' }
        val end = text.indexOf("\n$indent}", start)
        return text.substring(start, if (end < 0) text.length else end)
    }

    /**
     * A request outside the hour only for another place, not for any fix that differs by a bit; and each of the two
     * reasons asks alone: the hour up at the same place, or another place within the hour. Whole lines, comments taken
     * off.
     */
    @Test
    fun `the loop asks for new conditions only for another place`() {
        val lines = codeLines(engine)
        assertFalse("the bit-for-bit comparison", engine.contains("fix != lastWeatherFetchLocation"))
        assertTrue("val movedSinceLastFetch = LiveWeatherSchedule.movedToAnotherPlace(fix, lastWeatherFetchLocation)" in lines)
        assertTrue(
            "val timerExpired = LiveWeatherSchedule.isAttemptDue(SystemClock.elapsedRealtime() - lastWeatherFetchElapsed, attemptDelay)" in lines,
        )
        val ask = lines.indexOf("if (liveWeatherRuns && fix != null && (movedSinceLastFetch || timerExpired)) {")
        assertTrue("the request waits for both reasons, or for neither", ask >= 0)
        assertEquals(
            "what the request is asked under",
            listOf("lastWeatherFetchElapsed = SystemClock.elapsedRealtime()", "lastWeatherFetchLocation = fix", "result = WeatherRepository.fetchCurrentConditions("),
            lines.subList(ask + 1, ask + 4),
        )
        assertEquals("one request in the loop", 1, lines.count { it.contains("WeatherRepository.fetchCurrentConditions(") })
    }

    /** A refresh is a reason to ask the phone only if it would be sent. */
    @Test
    fun `the loop asks the phone for a refresh only if the refresh would be sent`() {
        assertFalse("Live Weather running alone", engine.contains("liveWeatherEnabled = liveWeatherRuns,"))
        assertTrue(
            engine.contains(
                "liveWeatherEnabled = LiveWeatherSchedule.wouldFetch(liveWeatherRuns, settings.weatherProvider, settings.apiKeyForWeatherProvider),",
            ),
        )
    }

    /**
     * The frame's hour is read on the offset of the times it draws with, from the same read of them, and the
     * turn of a minute onto another day or offset wakes the loop.
     */
    @Test
    fun `the frame reads its hour on the offset of its sun times`() {
        val render = body(engine, "private fun renderScene(")
        assertTrue(render.contains("val held = sunTimes"))
        assertTrue(render.contains("sceneHourNow(held)"))
        val hour = body(engine, "private fun sceneHourNow(")
        assertTrue(hour.contains("SolarDaySchedule.sceneHour(now, held, zone)"))
        assertTrue(hour.contains("if (SolarDaySchedule.workedOutForAnotherDay(held, now, zone)) weatherWakeUp.trySend(Unit)"))
        assertTrue("again at once when new times are published", hour.contains("held !== sceneHourHeld"))
    }

    /** Midnight with the wallpaper on screen chooses the theme by date again. */
    @Test
    fun `the date changing chooses the theme again on every engine`() {
        val onCreate = body(engine, "    override fun onCreate() {")
        assertTrue(onCreate.contains("dateReceiver"))
        for (action in listOf("Intent.ACTION_DATE_CHANGED", "Intent.ACTION_TIME_CHANGED", "Intent.ACTION_TIMEZONE_CHANGED")) {
            assertTrue("$action is not listened to", onCreate.contains("addAction($action)"))
        }
        assertTrue(engine.contains("for (engine in engines) engine.onDateChanged()"))
        val onDateChanged = body(engine, "fun onDateChanged()")
        assertTrue(onDateChanged.contains("onRenderThread { if (applyEffectiveTheme()) requestRedraw() }"))
        assertTrue("and the sunrise and sunset are worked out for the new day", onDateChanged.contains("weatherWakeUp.trySend(Unit)"))
        assertTrue("and let go with the service", body(engine, "    override fun onDestroy() {").contains("unregisterReceiver(dateReceiver)"))
    }

    /**
     * **One receiver for the date in the process, and it changes the launcher icon and every engine's theme
     * together.** The icon's controller is made in the service's `onCreate`, where the receiver is registered, and
     * stopped in its `onDestroy`, where the receiver is let go of: the two live alike, so a receiver of the
     * controller's own would only be a second one for the same broadcasts. Counted over every main source, its
     * comments taken off, whole lines compared.
     */
    @Test
    fun `one receiver for the date changes the icon and every engine's theme`() {
        val registrations = mainSources().sumOf { file -> codeLines(file.readText()).count { it == "addAction(Intent.ACTION_DATE_CHANGED)" } }
        assertEquals("receivers for the date in the app", 1, registrations)
        val onReceive = codeLines(body(engine, "private val dateReceiver = object : BroadcastReceiver() {"))
        assertTrue("the icon is not put right when the date moves", "iconController?.refresh()" in onReceive)
        assertTrue("the theme is not chosen again when the date moves", "for (engine in engines) engine.onDateChanged()" in onReceive)
        assertTrue(codeLines(body(engine, "    override fun onCreate() {")).contains("iconController = SeasonalIconController(applicationContext)"))
        assertTrue(codeLines(body(engine, "    override fun onDestroy() {")).contains("iconController?.stop()"))
    }

    /** The day, the offset and the stamp of a working-out come from one reading of the clock. */
    @Test
    fun `the sunrise and sunset are worked out for one reading of the clock`() {
        val update = body(engine, "private fun updateSunTimesFromLocation(")
        assertEquals("one reading", 1, Regex("""System\.currentTimeMillis\(\)""").findAll(update).count())
        assertFalse(update.contains("Calendar.getInstance("))
        assertFalse("the stamp read again after the times", update.contains("currentSolarStamp()"))
        assertTrue(update.contains("SolarDaySchedule.civilDayAt(System.currentTimeMillis(), TimeZone.getDefault())"))
    }

    /** The phone stopping one job cancels that job's run, not the last one started. */
    @Test
    fun `the update job stops the run of the job the phone stops`() {
        val job = source("update/UpdateCheckJobService.kt")
        assertFalse("one run for the whole service", job.contains("running?.cancel()"))
        assertTrue(body(job, "override fun onStopJob(").contains("running.stop(params.jobId)"))
        assertTrue(body(job, "override fun onStartJob(").contains("running.started(jobId, run)"))
    }

    /** [text]'s lines, trimmed, without comments: a line comment cut off, a KDoc or block comment line dropped. */
    private fun codeLines(text: String): List<String> =
        text.lines().map { it.trim() }
            .filterNot { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }
            .map { it.substringBefore(" //").trim() }
            .filter { it.isNotEmpty() }

    /** Every Kotlin file of the app's main sources. */
    private fun mainSources(): List<File> {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper"
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val root = File(dir, "$prefix$suffix")
                if (root.isDirectory) return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
            }
            dir = dir.parentFile
        }
        error("main sources not found: $suffix")
    }

    private fun source(path: String): String {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/$path"
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = File(dir, "$prefix$suffix")
                if (f.isFile) return f.readText()
            }
            dir = dir.parentFile
        }
        error("source not found: $suffix")
    }
}
