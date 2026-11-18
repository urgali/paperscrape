package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **When Live Weather's inputs change the scene drops the old forecast at once** (`DESIGN_NOTES.md` §12, decision 108, the
 * maintainer's «Sì a tutte» of 2026-11-15 to version A): the switch, the provider, the selected provider's key ([com.paperscrape.livewallpaper.weather.LiveWeatherInputs.changed])
 * or the location source changed, the engine forgets the snapshot it held and hands the renderer the theme's own weather in
 * the same call that writes "waiting" ([LiveWeatherStatus.OFF]), on the main thread where the settings collector runs; a
 * pass that began before the change asks nothing once it has seen it, and keeps, draws and publishes nothing of a request
 * in flight; the phone's answer for a source left during the wait is not kept. A written place moved is not a change of
 * inputs (decision 101): its branch forgets nothing. The engine is an Android service, so it is read here, whole lines with
 * the comments taken off: a mutation of any of these lines makes a test here fail.
 */
class InputsChangedDropTheForecastTest {

    private val code: List<String> by lazy {
        source("PaperWallpaperService.kt").lines().map { it.substringBefore("//").trim() }.filter { it.isNotEmpty() && !it.startsWith("*") && !it.startsWith("/*") }
    }

    private fun function(start: String): List<String> {
        val at = code.indexOfFirst { it.startsWith(start) }
        assertTrue("$start is gone", at >= 0)
        var depth = 0
        val out = ArrayList<String>()
        for (line in code.drop(at)) {
            out += line
            depth += line.count { it == '{' } - line.count { it == '}' }
            if (depth == 0 && out.size > 1) break
        }
        return out
    }

    @Test
    fun `forgetting the status forgets the forecast and hands the scene the theme's weather in the same call`() {
        val forget = function("private fun forgetPublishedWeatherStatus()")
        val steps = listOf(
            "weatherInputsGeneration++",
            "lastWeatherSnapshot = null",
            "lastWeatherSnapshotLocation = null",
            "applyLiveWeather(null, lapsed = false)",
            "publishedWeatherStatus = null",
            "publishWeatherStatus(LiveWeatherStatus.OFF)",
        )
        val at = steps.map { step -> forget.indexOf(step).also { assertTrue("missing: $step", it >= 0) } }
        assertEquals("in that order", at.sorted(), at)
    }

    @Test
    fun `it is called on every change of inputs, on the settings collector, and not when a written place moves`() {
        val calls = code.withIndex().filter { it.value == "forgetPublishedWeatherStatus()" }.map { it.index }
        assertEquals("two callers: the inputs and the location source", 2, calls.size)
        val inputs = code.indexOf("if (LiveWeatherInputs.changed(previousSettings, newSettings)) {")
        val source = code.indexOf("if (requestedSource != locationSource) {")
        assertTrue(inputs in 0 until calls[0] && calls[0] - inputs <= 3)
        assertTrue(source in 0 until calls[1] && calls[1] - source <= 12)
        // The written place: the sun times and the loop's own move check, no forgetting.
        val custom = code.indexOf("if (newSettings.useCustomLocation) {")
        assertTrue(custom > calls[1])
        val customBranch = code.subList(custom, code.indexOf("settingsApplied = true"))
        assertTrue("the written place forgets nothing", customBranch.none { "forget" in it || "applyLiveWeather" in it })
        // On the settings collector, whose scope is the main thread's.
        assertTrue(code.contains("private val scope = CoroutineScope(Dispatchers.Main + engineJob + engineExceptionHandler)"))
        assertTrue(code.indexOf("prefs.settingsFlow.collect { newSettings ->") in 0 until inputs)
    }

    @Test
    fun `a pass whose request was in flight keeps, draws and publishes nothing of it`() {
        val keep = code.indexOf("if (generationAtStart == weatherInputsGeneration) {")
        assertTrue(keep >= 0)
        assertTrue("the snapshot kept only for the inputs still in force",
            code[keep + 1].startsWith("val (held, heldFor) = LiveWeatherSchedule.heldAfterFetch("))
        val last = code.lastIndexOf("if (generationAtStart == weatherInputsGeneration) {")
        assertTrue(last > keep)
        assertEquals("applyLiveWeather(decision.snapshotForScene, decision.lapsed)", code[last + 1])
        assertEquals("publishWeatherStatus(decision.status)", code[last + 2])
        assertEquals("the scene is told nowhere else but the forgetting", 2, code.count { it.startsWith("applyLiveWeather(") })
    }

    /**
     * The pass numbers its inputs before it reads any of them, and asks nothing once they have changed: a switch turned off
     * while it waits for the phone's position (`refreshDeviceFix`) sends no request with the position after the user said
     * no, and hands the scene nothing.
     */
    @Test
    fun `a pass whose inputs changed while it waited for the position asks nothing`() {
        val numbered = code.indexOf("val generationAtStart = weatherInputsGeneration")
        val runs = code.indexOfFirst { it.startsWith("val liveWeatherRuns = ") }
        val wait = code.indexOf("SolarDaySchedule.Action.ASK_DEVICE -> refreshDeviceFix(source)")
        assertTrue("numbered before the switch is read and before the wait", numbered in 0 until runs && runs < wait)
        assertEquals("numbered once", 1, code.count { it == "val generationAtStart = weatherInputsGeneration" })
        val unchanged = code.indexOf("val inputsUnchanged = generationAtStart == weatherInputsGeneration")
        assertTrue("checked after the wait", unchanged > wait)
        assertEquals(
            "if (liveWeatherRuns && inputsUnchanged && fix != null && (movedSinceLastFetch || timerExpired || forShowing)) {",
            code[unchanged + 1],
        )
        // The phone's answer for a source left while it was asked is not kept: the line right after the request.
        val refresh = function("private suspend fun refreshDeviceFix(source: LocationSource) {")
        val ask = refresh.indexOf("val answer = deviceFix(kind, force = userChoseDeviceSource)")
        assertTrue("the request", ask > 0)
        assertEquals("if (LocationSource.of(settings) != source) return", refresh[ask + 1])
    }

    private fun source(name: String): String {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/engine/$name"
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = File(dir, "$prefix$suffix")
                if (f.isFile) return f.readText()
            }
            dir = dir.parentFile
        }
        error("$suffix not found")
    }
}
