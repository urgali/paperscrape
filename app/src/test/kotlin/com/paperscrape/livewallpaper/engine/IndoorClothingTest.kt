package com.paperscrape.livewallpaper.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Winter clothing stops at the window, because the window artwork has no winter.
 *
 * A person leaning out of their own window is indoors: they are in a room, and the room is not
 * having weather. A season column chosen from the *scene*
 * (`if (customization.winterColorsEnabled) 1 else 0`) would put a hat on a figure the scene's
 * weather cannot reach.
 *
 * `PeopleLayerTable.WINDOW` has one season, so a window bust has no season to choose. What this
 * asserts is the *absence* -- nothing brings a season choice back to a window -- plus the half that
 * is a real choice: the street and the cars read the outdoor column, through one function.
 *
 * ### Why this reads the source
 *
 * The thing being asserted is a **coupling between call sites**: the places that choose a season
 * column must all go through one function, and a window must not be one of them. A coupling between
 * call sites is what the source states and what a unit test on any one of them cannot see -- the
 * same reasoning `SkyscraperWindowTest` and `InternetInventoryTest` are built on, and the same
 * reasoning `tools/assets`' `validate` uses for blit call sites. The `Exposure` enum is asserted
 * absent: indoors is not a column that exists.
 */
class IndoorClothingTest {

    private val source: String by lazy { rendererSource().readText() }

    @Test
    fun `nothing picks a season column by hand any more`() {
        val handRolled = Regex("""if \(customization\.winterColorsEnabled\) 1 else 0""")
            .findAll(source).count()
        assertEquals(
            "a call site is choosing its own season column instead of going through " +
                "outdoorSeasonIndex",
            1,
            handRolled,
        )
        assertTrue(
            "and the one place it appears must be outdoorSeasonIndex itself",
            Regex(
                """fun outdoorSeasonIndex\(\): Int = if \(customization\.winterColorsEnabled\) 1 else 0""",
            ).containsMatchIn(source),
        )
    }

    @Test
    fun `the window occupant has no season to choose`() {
        val body = bodyOf("drawWindowOccupant")
        assertTrue(
            "drawWindowOccupant must not choose a season column -- a person at their window is " +
                "indoors, and the window artwork has one season (PeopleLayerTable.WINDOW):\n$body",
            !body.contains("outdoorSeasonIndex") && !body.contains("winterColorsEnabled"),
        )
        assertTrue(
            "and the table it reads must have no season axis either",
            Regex("""PeopleLayerTable\.WINDOW\[[^\]]+\](?!\[)""").containsMatchIn(body),
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
                body.contains("outdoorSeasonIndex()"),
            )
        }
    }

    @Test
    fun `there is exactly one place that turns the season into a column`() {
        assertEquals(
            "outdoorSeasonIndex must be declared once and only once",
            1,
            Regex("""private fun outdoorSeasonIndex\(""").findAll(source).count(),
        )
        assertEquals(
            "and the Exposure enum must be gone: indoors is no longer a column that exists",
            0,
            Regex("""Exposure\.(INDOORS|OUTDOORS)""").findAll(source).count(),
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
