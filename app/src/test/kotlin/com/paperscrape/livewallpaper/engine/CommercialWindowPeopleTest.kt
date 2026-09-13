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
     * **v5.0 moved where the answer lives.** There is no longer a draw function per building to
     * read: one composer draws all five families, and whether a building asks for occupants is a
     * property of its **pieces** -- a piece that declares windows carries an `OCCUPANTS` part, and
     * the composer hands it the building's own total. So the source half of this test asks the one
     * question the source can still lose (does the composer pass the *building's* count, or a
     * piece's?), and the coverage half is asked of the table, over every deal, which is stronger
     * than the five function bodies it replaces: a sixth family, or a new piece with a window on
     * it, is covered the day it is added.
     */
    @Test
    fun `every piece that declares windows asks for its occupants`() {
        for ((variant, family) in NeighbourhoodTable.FAMILIES) {
            for ((slotIndex, slot) in family.slots.withIndex()) {
                for (piece in slot.options) {
                    if (piece.windows.isEmpty()) continue
                    assertTrue(
                        "$variant slot $slotIndex has a piece with ${piece.windows.size} windows " +
                            "and no OCCUPANTS part, so nobody can ever stand in them",
                        piece.parts.any { it.role == PartRole.OCCUPANTS },
                    )
                }
            }
        }
    }

    @Test
    fun `every family has somebody to put somewhere`() {
        // The v4.1 defect in its v5.0 shape: a family all of whose deals have zero windows is a
        // building nobody is ever in, and the restaurant was exactly that for three releases.
        for ((variant, family) in NeighbourhoodTable.FAMILIES) {
            val counts = windowCounts(family)
            assertTrue("$variant can be dealt with no windows at all: $counts", counts.min() > 0)
        }
    }

    /**
     * The composer passes the **building's** window count, not a piece's.
     *
     * Occupancy is a count dealt across a building's panes, so handing `drawWindowOccupant` a
     * piece's own two windows instead of the building's four would deal the wrong number and no
     * assertion about [WindowOccupants] would notice. The index passed has to be the building-wide
     * one for the same reason -- two pieces both numbering their windows from zero would put the
     * same person in both.
     */
    @Test
    fun `the occupant call site is given the building's own numbering`() {
        val text = rendererSource.readText()
        val at = text.indexOf("PartRole.OCCUPANTS ->")
        assertTrue("no OCCUPANTS branch in SceneObjectRenderer.kt", at > 0)
        val branch = text.substring(at, minOf(at + 700, text.length))
        assertTrue(
            "the occupant call must be given the building's total, not a piece's; found:\n$branch",
            branch.contains("deal.windowCount"),
        )
        assertTrue(
            "and the index must be the building-wide one; found:\n$branch",
            branch.contains("placed.firstWindow"),
        )
    }

    /** The pane counts the artwork actually declares, per family and per deal. */
    @Test
    fun `the declared pane counts match the artwork`() {
        assertEquals(
            "a small house draws one or two windows",
            listOf(1, 2), windowCounts(family(SceneSpace.SceneVariant.HOUSE_SMALL)),
        )
        assertEquals(
            "a large house three or four",
            listOf(3, 4), windowCounts(family(SceneSpace.SceneVariant.HOUSE_LARGE)),
        )
        // **The tower lost thirteen panes and that is the drawing, not a bug.** The shipped facade
        // painted a 4x4 grid into `skyscraper_wall` and stood a bust at all sixteen; the redrawn
        // tower has three bay windows a person can actually be seen in, and its other windows are
        // stamped rows far too small to hold a figure. Written down because "16 -> 3" is the kind
        // of number that looks like a regression until somebody says it was chosen.
        assertEquals(
            "the tower's bays are three",
            listOf(3), windowCounts(family(SceneSpace.SceneVariant.TOWER)),
        )
        assertEquals(
            "the restaurant's frontage is three panes",
            listOf(3), windowCounts(family(SceneSpace.SceneVariant.RESTAURANT)),
        )
        assertEquals(
            "both bar figures draw three",
            listOf(3), windowCounts(family(SceneSpace.SceneVariant.BAR)),
        )
    }

    /**
     * Every window a bust may stand in is a box the piece declares, and the generator refuses to
     * render a piece whose declared box leaves the wall it is cut into (`tools/assets/tests/
     * test_neighbourhood.py`). That is where `the restaurant's occupants are placed on its two
     * glass panes` went: it asserted two hand-written pane centres against a hand-measured
     * sprite, and both sides of that comparison are now generated from one declaration.
     *
     * What is left worth checking here is that a declared box is a box somebody fits in.
     */
    @Test
    fun `every declared window is a real opening above the ground`() {
        for ((variant, family) in NeighbourhoodTable.FAMILIES) {
            for (slot in family.slots) {
                for (piece in slot.options) {
                    for (window in piece.windows) {
                        assertTrue(
                            "$variant declares a ${window.w}x${window.h} window, which is not an opening",
                            window.w > 0f && window.h > 0f,
                        )
                        // `y` is the box's top edge in the piece's own frame, where the foot is 0
                        // and up is negative, so a window whose bottom reaches the ground line is
                        // one somebody would be standing in the floor of.
                        assertTrue(
                            "$variant declares a window at y=${window.y} that reaches the ground",
                            window.y + window.h < 0f,
                        )
                    }
                }
            }
        }
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
                    seed, b * 100_003,
                    windowCounts(family(SceneSpace.SceneVariant.BAR)).single(),
                    WindowBuildingKind.COMMERCIAL,
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

    private fun family(variant: SceneSpace.SceneVariant) = NeighbourhoodTable.FAMILIES.getValue(variant)

    /** Every distinct number of windows a family can be dealt, ascending. */
    private fun windowCounts(family: BuildingFamily): List<Int> {
        var counts = listOf(0)
        for (slot in family.slots) {
            val next = mutableSetOf<Int>()
            for (sofar in counts) {
                for (option in slot.options) {
                    for (repeats in slot.repeatMin..slot.repeatMax) {
                        next += sofar + option.windows.size * repeats
                    }
                }
            }
            counts = next.toList()
        }
        return counts.sorted()
    }

    /** Commercial buildings a theme actually renders, and how many occupants they hold. */
    private fun commercialOccupancy(themeId: String): Pair<Int, Int> {
        val theme = ThemeCatalog.byId(themeId)
        val customization = defaultCustomizationFor(themeId)
        val seed = themeId.hashCode()
        var buildings = 0
        var occupants = 0
        for (spec in SceneObjectCatalog.layoutFor(themeId, theme.accentColor).staticObjects) {
            if (!customization.keepCandidate(spec)) continue
            val variant = SceneObjectRenderer.variantFor(spec)
            if (variant != SceneSpace.SceneVariant.BAR && variant != SceneSpace.SceneVariant.RESTAURANT) continue
            // The count this building is actually dealt, from its own position -- not a constant,
            // because a family's deals can differ in how many windows they have.
            val deal = NeighbourhoodComposer.Deal()
            NeighbourhoodComposer.deal(family(variant), spec.tileFractionX, spec.depthFraction, deal)
            val windows = deal.windowCount
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
