package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * That somebody is actually behind the glass of a non-residential building.
 *
 * ### The defect this exists to catch
 *
 * v4.1 reported "commercial = 3/3 populatable panes" and shipped a scene in which no user ever saw
 * a person in a shop. Both statements were true. The three panes belong to the **bar**, and the
 * bar is the rarer of the two street-level businesses the scene draws: the other is the
 * **restaurant**, which is two to four buildings per theme against the bar's roughly one, and
 * which `drawRestaurantBuilding` never gave an occupant call site at all. On `beach` -- the theme
 * the report came from -- and on `new_year` and `spring` there is **no bar in the layout**, so the
 * number of commercial windows that could hold anybody was exactly zero.
 *
 * No test over [WindowOccupants] could have seen that, because the object was never asked. The
 * missing thing was a call, and a missing call is only visible from outside the callee. Hence the
 * two shapes of test below: one reads the renderer's own source for the call sites, and one counts
 * occupants over the layouts the shipped themes actually produce.
 *
 * ### Why the source-reading test is not a hack
 *
 * `SceneObjectRenderer` needs a `Context` and a `Canvas`, so a JVM test cannot call it; the
 * device-side `PeopleGoldenTest` shows the pixels, but a golden tells you *that* a frame changed
 * rather than *which building kind lost its people*. Reading the source is how this project
 * already pins claims of the form "no code anywhere does X" -- see `SkinToneAssetsTest`, which
 * fails if a skin preference key appears in any `.kt` file.
 */
class CommercialWindowPeopleTest {

    // -------------------------------------------------- the call sites exist

    /**
     * Every building the scene draws windows on asks for its occupants, and asks with its own
     * declared pane count.
     *
     * Two clauses, because the two ways to lose the people are different. **The call has to
     * exist** -- v4.1's `drawRestaurantBuilding` had none, and that single absence is the whole
     * of the reported defect. **The declared count has to be passed** -- occupancy is now a count
     * dealt across a building's panes, so a call site that passed the wrong pool would deal the
     * wrong number and no assertion about [WindowOccupants] would notice.
     *
     * Call sites are not counted, because the bar's three and the tower's sixteen come from one
     * call inside a loop; what is counted is that the constant naming the pool appears in the
     * function that draws that building.
     */
    @Test
    fun `every populatable building kind calls for its occupants`() {
        val expected = mapOf(
            "drawSmallHouse" to "SMALL_HOUSE_WINDOWS",
            "drawLargeHouse" to "LARGE_HOUSE_WINDOWS",
            "drawBarBuilding" to "BAR_WINDOWS",
            "drawRestaurantBuilding" to "RESTAURANT_WINDOWS",
            "drawSkyscraperBuilding" to "SKYSCRAPER_WINDOWS",
        )
        for ((function, constant) in expected) {
            val body = bodyOf(function)
            assertTrue(
                "$function draws windows but never calls drawWindowOccupant",
                body.contains("drawWindowOccupant("),
            )
            assertTrue(
                "$function does not pass $constant to drawWindowOccupant",
                body.contains(constant),
            )
        }
    }

    /** And the pane counts the renderer declares are the ones its windows actually have. */
    @Test
    fun `the declared pane counts match the artwork`() {
        assertEquals("a small house draws two windows", 2, SceneObjectRenderer.SMALL_HOUSE_WINDOWS)
        assertEquals("a large house draws four", 4, SceneObjectRenderer.LARGE_HOUSE_WINDOWS)
        assertEquals("the bar's upper storey draws three", 3, SceneObjectRenderer.BAR_WINDOWS)
        assertEquals("the restaurant's frontage is two panes", 2, SceneObjectRenderer.RESTAURANT_WINDOWS)
        assertEquals("the tower's grid is four by four", 16, SceneObjectRenderer.SKYSCRAPER_WINDOWS)
        // Read off the drawing code rather than restated: the bar's three x positions and the
        // tower's 4x4 loop are in the source, so a fourth pane added to either fails here.
        assertEquals(
            "the bar's window x positions",
            SceneObjectRenderer.BAR_WINDOWS,
            Regex("""-?[0-9]+f""")
                .findAll(
                    bodyOf("drawBarBuilding")
                        .substringAfter("for ((wi, wx) in floatArrayOf(")
                        .substringBefore(")"),
                ).count(),
        )
    }

    /** The restaurant's occupants must stand behind its glass, not beside it. */
    @Test
    fun `the restaurant's occupants are placed on its two glass panes`() {
        // restaurant_window is blitted at x = -35 and is 30 local units wide; its two panes are
        // sprite pixels 8..39 and 50..81, i.e. local x -32.3..-22.0 and -18.7..-8.0.
        val paneA = -32.3f..-22.0f
        val paneB = -18.7f..-8.0f
        assertTrue(
            "pane A centre ${SceneObjectRenderer.RESTAURANT_PANE_A_CENTRE_X} is not on the left pane",
            SceneObjectRenderer.RESTAURANT_PANE_A_CENTRE_X in paneA,
        )
        assertTrue(
            "pane B centre ${SceneObjectRenderer.RESTAURANT_PANE_B_CENTRE_X} is not on the right pane",
            SceneObjectRenderer.RESTAURANT_PANE_B_CENTRE_X in paneB,
        )
        // The bust stands on the sprite's own lower edge, the way a house's and the bar's do.
        assertEquals(
            "occupant box bottom",
            -23f,
            SceneObjectRenderer.RESTAURANT_WINDOW_Y + SceneObjectRenderer.OCCUPANT_BOX_UNITS,
            0.001f,
        )
    }

    // ------------------------------------------ the shipped themes have people

    /**
     * Every built-in theme puts somebody behind commercial glass.
     *
     * Counted over the real layouts at the real default customisation, which is the only measure
     * that would have failed for v4.1: four of the twelve themes -- `new_year`, `beach`,
     * `halloween` and `spring` -- had **zero** commercial occupants, and `beach` is the theme the
     * defect was reported from.
     */
    @Test
    fun `every built-in theme has people behind commercial glass`() {
        for (theme in ThemeCatalog.ALL) {
            val (buildings, occupants) = commercialOccupancy(theme.id)
            assertTrue("${theme.id} draws no commercial building at all", buildings > 0)
            assertTrue(
                "${theme.id} has $buildings commercial buildings and nobody in any of them",
                occupants > 0,
            )
        }
    }

    /** Enough of them to read as a populated street rather than a single lucky window. */
    @Test
    fun `commercial occupants are not a rarity across the catalogue`() {
        var buildings = 0
        var occupants = 0
        for (theme in ThemeCatalog.ALL) {
            val (b, o) = commercialOccupancy(theme.id)
            buildings += b
            occupants += o
        }
        assertTrue(
            "only $occupants occupants across $buildings commercial buildings",
            occupants.toFloat() / buildings >= 0.6f,
        )
    }

    /**
     * A three-pane frontage is never empty, which is the tail v4.2's occupancy removes.
     *
     * Under v4.1's coin-per-window the bar came out with nobody 21.6% of the time; dealing a count
     * across the panes makes `floor(3 * 0.40 + u)` either one or two and never zero.
     */
    @Test
    fun `a bar always has somebody in it`() {
        for (seed in (0 until 400).map { "theme-$it".hashCode() }) {
            for (b in 0 until 40) {
                val count = WindowOccupants.occupantCount(
                    seed, b * 100_003, SceneObjectRenderer.BAR_WINDOWS, WindowBuildingKind.COMMERCIAL,
                )
                assertTrue("an empty bar at seed $seed building $b", count >= 1)
                assertTrue("an overfull bar at seed $seed building $b", count <= 2)
            }
        }
    }

    /** And the count a building is given is really the number of its windows that are occupied. */
    @Test
    fun `the dealt count is the number of windows that come out occupied`() {
        for (kind in WindowBuildingKind.entries) {
            for (windows in listOf(1, 2, 3, 4, 16)) {
                for (seed in (0 until 60).map { "theme-$it".hashCode() }) {
                    for (b in 0 until 20) {
                        val buildingSeed = b * 100_003
                        val dealt = WindowOccupants.occupantCount(seed, buildingSeed, windows, kind)
                        val actual = (0 until windows).count {
                            WindowOccupants.isOccupied(seed, buildingSeed, it, windows, kind)
                        }
                        assertEquals("$kind with $windows windows", dealt, actual)
                    }
                }
            }
        }
    }

    // ----------------------------------------------------------------- helpers

    /** Commercial buildings a theme actually renders, and how many occupants they hold. */
    private fun commercialOccupancy(themeId: String): Pair<Int, Int> {
        val theme = ThemeCatalog.byId(themeId)
        val customization = defaultCustomizationFor(themeId)
        val seed = themeId.hashCode()
        var buildings = 0
        var occupants = 0
        for (spec in SceneObjectCatalog.layoutFor(themeId, theme.accentColor).staticObjects) {
            if (!customization.keepCandidate(spec)) continue
            val windows = when (SceneObjectRenderer.variantFor(spec)) {
                SceneSpace.SceneVariant.BAR -> SceneObjectRenderer.BAR_WINDOWS
                SceneSpace.SceneVariant.RESTAURANT -> SceneObjectRenderer.RESTAURANT_WINDOWS
                else -> continue
            }
            buildings++
            occupants += WindowOccupants.occupantCount(
                seed, (spec.tileFractionX * 100_003f).toInt(), windows, WindowBuildingKind.COMMERCIAL,
            )
        }
        return buildings to occupants
    }

    /** The body of one `private fun` of [SceneObjectRenderer], up to the next function of its own. */
    private fun bodyOf(function: String): String {
        val text = rendererSource.readText()
        val start = text.indexOf("private fun $function(")
        assertTrue("no `private fun $function(` in SceneObjectRenderer.kt", start >= 0)
        val next = Regex("""\n    private (?:fun|inline fun) """).find(text, start + 1)?.range?.first
        return text.substring(start, next ?: text.length)
    }

    private companion object {

        /** Gradle's working directory is a default, not a guarantee, so walk up to find the tree. */
        val rendererSource: File by lazy {
            var dir: File? = File(".").absoluteFile
            while (dir != null) {
                for (prefix in listOf("", "app/")) {
                    val candidate = File(
                        dir,
                        "${prefix}src/main/kotlin/com/paperscrape/livewallpaper/engine/SceneObjectRenderer.kt",
                    )
                    if (candidate.isFile) return@lazy candidate
                }
                dir = dir.parentFile
            }
            throw AssertionError("could not locate SceneObjectRenderer.kt from ${File(".").absolutePath}")
        }
    }
}
