package com.paperscrape.livewallpaper.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Winter clothing stops at the window, and the rule that says so has one home.
 *
 * A person leaning out of their own window is indoors. They are in a room, and the room is not
 * having weather. Until this test they put on a hat whenever the scene turned wintry, because every
 * call site that reads a person sprite picked its season column with the same
 * `if (customization.winterColorsEnabled) 1 else 0` — a rule about the *scene* applied to a figure
 * the scene's weather cannot reach.
 *
 * ### Why this reads the source
 *
 * The thing being asserted is a **coupling between call sites**: three places choose a season
 * column, they must all go through one function, and each must pass the exposure that matches where
 * its figure stands. A coupling between call sites is what the source states and what a unit test
 * on any one of them cannot see — the same reasoning `SkyscraperWindowTest` and
 * `InternetInventoryTest` are built on, and the same reasoning `tools/assets`' `validate` uses for
 * blit call sites.
 *
 * The alternative — an `if` at the window call site — is what this is written to prevent. It would
 * pass a behavioural test and leave the next person-drawing call site to guess again.
 */
class IndoorClothingTest {

    private val source: String by lazy { rendererSource().readText() }

    @Test
    fun `nothing picks a season column by hand any more`() {
        val handRolled = Regex("""if \(customization\.winterColorsEnabled\) 1 else 0""")
            .findAll(source).count()
        assertEquals(
            "a call site is choosing its own season column instead of going through seasonIndexFor",
            0,
            handRolled,
        )
    }

    @Test
    fun `the window occupant is indoors`() {
        assertTrue(
            "drawWindowOccupant must read the indoor column:\n${bodyOf("drawWindowOccupant")}",
            bodyOf("drawWindowOccupant").contains("seasonIndexFor(Exposure.INDOORS)"),
        )
    }

    @Test
    fun `pedestrians and people in cars are outdoors`() {
        // A car is a coat, not a house: its occupants keep dressing for the weather. Stated here so
        // that "indoors" cannot quietly spread to anything behind glass.
        for (function in listOf("drawPeople", "drawCar")) {
            val body = bodyOf(function)
            assertTrue(
                "$function must read the outdoor column:\n$body",
                body.contains("seasonIndexFor(Exposure.OUTDOORS)"),
            )
        }
    }

    @Test
    fun `there is exactly one place that turns exposure into a column`() {
        assertEquals(
            "seasonIndexFor must be declared once and only once",
            1,
            Regex("""private fun seasonIndexFor\(""").findAll(source).count(),
        )
        assertTrue(
            "and INDOORS must be the summer column",
            Regex("""Exposure\.OUTDOORS && customization\.winterColorsEnabled\) 1 else 0""")
                .containsMatchIn(source),
        )
    }

    /** The text of one function, from its declaration to the next one at the same indent. */
    private fun bodyOf(name: String): String {
        val at = source.indexOf("fun $name(")
        require(at > 0) { "$name is not declared in SceneObjectRenderer.kt" }
        val next = source.indexOf("\n    private fun ", at + 1).let { if (it < 0) source.length else it }
        return source.substring(at, next)
    }

    private fun rendererSource(): File {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/engine/SceneObjectRenderer.kt"
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = File(dir, "$prefix$suffix")
                if (candidate.isFile) return candidate
            }
            dir = dir.parentFile
        }
        error("could not locate $suffix from ${File(".").absolutePath}")
    }
}
