package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **The Live Weather status is written by the wallpaper last on screen** (`DESIGN_NOTES.md` §12, decision 108, the
 * maintainer's «Sì a tutte» of 2026-11-15 to the row that proposed it): with two engines at once -- the home and the lock
 * screen apart on Android 14 and later, or a second screen -- each with its own answer, the one key the settings screen
 * reads holds the answer of the engine the user last saw. Not provable on the BV6600 (Android 10, one engine): every order of
 * two engines' showings, answers and endings is played here against [WeatherStatusWriter], with the engine's own rule (write
 * an answer that changed if it may; write its answer when it becomes the writer), and the engine is read to hold that rule.
 */
class WeatherStatusWriterTest {

    /** Two engines, as `PaperWallpaperService.PaperEngine` uses the writer, over one key. */
    private class Phone {
        val writer = WeatherStatusWriter()
        val answer = arrayOf("a0", "b0")
        val alive = booleanArrayOf(true, true)
        var key: String? = null
        var lastShown = -1

        fun show(e: Int) {
            if (!alive[e]) return
            lastShown = e
            if (writer.shown(e)) write(e)
        }

        fun answer(e: Int, value: String) {
            if (!alive[e] || answer[e] == value) return
            answer[e] = value
            write(e)
        }

        fun end(e: Int) {
            if (!alive[e]) return
            alive[e] = false
            writer.gone(e)
            if (lastShown == e) lastShown = -1
        }

        private fun write(e: Int) {
            if (writer.mayWrite(e)) key = answer[e]
        }
    }

    @Test
    fun `in every order of two engines, the key holds the answer of the one last on screen`() {
        val events = listOf("showA", "showB", "answerA", "answerB", "endA", "endB")
        var played = 0
        fun play(sequence: List<String>) {
            val phone = Phone()
            var n = 0
            for (event in sequence) {
                n++
                when (event) {
                    "showA" -> phone.show(0)
                    "showB" -> phone.show(1)
                    "answerA" -> phone.answer(0, "a$n")
                    "answerB" -> phone.answer(1, "b$n")
                    "endA" -> phone.end(0)
                    "endB" -> phone.end(1)
                }
                val shown = phone.lastShown
                if (shown >= 0) assertEquals("$sequence after $event", phone.answer[shown], phone.key)
            }
            played++
        }
        fun all(prefix: List<String>, depth: Int) {
            play(prefix)
            if (depth == 0) return
            for (e in events) all(prefix + e, depth - 1)
        }
        all(emptyList(), 7)
        assertTrue("orders played: $played", played > 300_000)
    }

    @Test
    fun `the writer's rule, step by step`() {
        val w = WeatherStatusWriter()
        assertTrue("before any showing anyone may write", w.mayWrite(0) && w.mayWrite(1))
        assertTrue("shown first: the writer, write now", w.shown(0))
        assertFalse("shown again: already the writer", w.shown(0))
        assertTrue(w.mayWrite(0))
        assertFalse("the other is not", w.mayWrite(1))
        assertTrue("the other shown: the writer, write now", w.shown(1))
        assertFalse(w.mayWrite(0))
        w.gone(0)
        assertTrue("a gone engine that was not the writer changes nothing", w.mayWrite(1) && !w.mayWrite(0))
        w.gone(1)
        assertTrue("the writer gone: anyone again", w.mayWrite(0) && w.mayWrite(2))
    }

    /** The engine holds the rule: it writes through the writer, becomes it on coming on screen, and lets it go when destroyed. */
    @Test
    fun `the wallpaper's engine writes only as the writer says`() {
        val service = source("PaperWallpaperService.kt").lines().map { it.trim() }
        for (line in listOf(
            "if (!isPreview && LiveWeatherStatusWriter.shown(engineNumber)) writeWeatherStatus()",
            "if (!isPreview) LiveWeatherStatusWriter.gone(engineNumber)",
            "if (isPreview || !LiveWeatherStatusWriter.mayWrite(engineNumber)) return",
            "scope.launch { prefs.setLiveWeatherStatus(status) }",
        )) assertTrue("PaperWallpaperService.kt has no line: $line", line in service)
        assertEquals("one place writes the status", 1, service.count { it.contains("prefs.setLiveWeatherStatus(") })
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
