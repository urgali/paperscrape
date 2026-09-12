package com.paperscrape.livewallpaper.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Winter clothing stops at the window, and since v4.30 it stops because the artwork stops.
 *
 * A person leaning out of their own window is indoors. They are in a room, and the room is not
 * having weather. Until v4.2 they put on a hat whenever the scene turned wintry, because every call
 * site that read a person sprite picked its season column with the same
 * `if (customization.winterColorsEnabled) 1 else 0` — a rule about the *scene* applied to a figure
 * the scene's weather cannot reach.
 *
 * ### What v4.30 changed, and why the test changed with it
 *
 * The fix was an `Exposure` enum with an `INDOORS` case that resolved to the summer column. It
 * worked, and it hid something: indoors the answer was **always** 0, so the winter column of the
 * window table named twelve recolours no draw path could ever select, and the enum made that look
 * like a choice being made rather than a column that could not be reached. That is
 * `BACKLOG_v4_25.md` item 57, open since v4.25 and reported as a defect twice by two readers.
 *
 * v4.30 retired the four winter window shapes and the column with them: `PeopleLayerTable.WINDOW`
 * has one season, so the question is no longer asked of a window bust at all. What is left to
 * assert is therefore the *absence* -- that nothing reintroduces a season choice at a window -- plus
 * the half that is still a real choice: the street and the cars.
 *
 * ### Why this reads the source
 *
 * The thing being asserted is a **coupling between call sites**: the places that choose a season
 * column must all go through one function, and a window must not be one of them. A coupling between
 * call sites is what the source states and what a unit test on any one of them cannot see — the
 * same reasoning `SkyscraperWindowTest` and `InternetInventoryTest` are built on, and the same
 * reasoning `tools/assets`' `validate` uses for blit call sites.
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
            "drawWindowOccupant must not choose a season column -- the window artwork has one " +
                "season since v4.30 (BACKLOG_v4_25.md item 57):\n$body",
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
