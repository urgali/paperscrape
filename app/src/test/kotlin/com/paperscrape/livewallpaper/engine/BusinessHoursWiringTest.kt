package com.paperscrape.livewallpaper.engine

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The business hours reach **both** window systems, and only in the buildings that are businesses.
 *
 * Lights and occupants are two separate call paths -- the glass's light rides the building's
 * `glassNight` (its glass mask's colour, `businessGlassColor`, and its door lamp), the
 * figures come from each building's doorway (`WindowWalk.Doorway`, v5.12) -- and one schedule must govern both: touch one and the
 * other silently stays open. No pixel test can prove a *pair* of call sites is wired (a frame
 * where both respond cannot say they respond for the same reason), so this pins the call sites
 * themselves, the way [SkyscraperWindowTest] already pins the window-colour coupling by reading
 * the code. `BusinessHoursRenderTest` (instrumented) then shows each system responding on pixels.
 */
class BusinessHoursWiringTest {

    // ---------------------------------------------------------------- the lights, in one line

    /**
     * **Three tests became one, because three call sites became one.**
     *
     * Until v5.0 the restaurant, the bar and the tower each computed their own night and each had
     * to remember to scale it -- so this pinned three near-identical expressions in three draw
     * functions, and a fourth business would have needed a fourth. The composer computes it once
     * for whatever family it is drawing, so the rule is a single line and the only way to lose it
     * is to edit that line.
     *
     * Both halves are asserted, because they fail differently: dropping `businessOpenness` leaves
     * shops lit all night, and dropping the house test makes homes go dark at closing time.
     */
    @Test
    fun `a business scales its night by the opening hours and a home never does`() {
        val line = Regex("""val glassNight = [^\n]*""").find(renderer())?.value
            ?: error("glassNight is not computed in SceneObjectRenderer.kt")
        assertTrue(
            "the house branch must come first and must not be scaled; found:\n$line",
            line.contains("if (family.kind == WindowBuildingKind.HOUSE) night"),
        )
        assertTrue(
            "and the other branch must be scaled by the openness; found:\n$line",
            line.contains("else night * businessOpenness"),
        )
    }

    /**
     * **Whose hours** (v5.12): the openness a building is drawn with is its own group's -- the shops'
     * or the towers' -- asked of [SceneCustomization.opennessFor] with what it is drawn as, the rule
     * its colours follow (`OpeningHoursGroupsTest` holds the rule itself), at the frame's hour; and it
     * is that openness, not another, the occupants are thinned by.
     */
    @Test
    fun `a building keeps its own group's hours, at the frame's hour`() {
        val body = composer()
        assertTrue(
            "the openness must be the building's group's, by what it is drawn as",
            body.contains("val businessOpenness = customization.opennessFor(variant, frameHour)"),
        )
        // The occupants (v5.12): walked out and in at the hours by the building's doorway, which is
        // moved on once a frame with the same group's openness at the same hour, by the same rule --
        // what it is drawn as -- and handed to the figures the building is drawn with.
        assertTrue(
            "the doorways must be given the same openness",
            drawSource("advanceDoorways").contains(
                "doorway.advance(roster, layout, elapsed, customization.opennessFor(variantFor(r.spec), frameHour), settle)",
            ),
        )
        assertTrue(
            "and the building drawn with its doorway",
            body.contains("WindowWalk.figuresAt(roster, layout, elapsed, r.doorway, windowFigures)"),
        )
        assertTrue("the frame's hour is the hour draw() was given", renderer().contains("frameHour = hour24"))
        assertTrue(
            "the doorways are moved on after the hour is set",
            renderer().indexOf("advanceDoorways(elapsedSeconds)") > renderer().indexOf("frameHour = hour24"),
        )
    }

    /**
     * And a door's lamp lights with its own building's glass: a home's on the sky's own night, a
     * shop's and a tower's only while they are open.
     *
     * Since v5.11 (inventory I-405 and I-409, the maintainer's «sì» of 2026-10-06) the composer hands
     * the lamp `glassNight`, the number the building's glass crossfades on -- until then the lamp was
     * given the unscaled night, so a closed shop's lamp burned beside its dark glass. A home's
     * `glassNight` *is* the unscaled `1f - dayBlend` (the test above pins the house branch), which is
     * what keeps an evening street inhabited; this pins both halves of that.
     */
    @Test
    fun `a door's lamp lights with its building's glass, a home's on the sky's night`() {
        val body = composer()
        assertTrue(
            "the lamp must light on the building's own glassNight",
            body.contains("drawPorchLight(canvas, x = part.x, y = footY + part.y, lit = glassNight)"),
        )
        assertTrue(
            "the unscaled night must be what a house's glass -- and so its lamp -- is given",
            Regex("""val night = \(1f - dayBlend\)""").containsMatchIn(renderer()),
        )
    }

    // ---------------------------------------------------------------- the occupants, once

    /**
     * Since v5.12 the hours reach the occupants through a building's doorway ([WindowWalk.Doorway]),
     * which walks them out and in: every building that is not a house is given one, and a house none
     * -- without a doorway every person of the roster is in, whatever the hour.
     */
    @Test
    fun `every commercial occupant goes through the openness and every house occupant does not`() {
        val give = drawSource("giveRosters")
        assertTrue(
            "the house test must be the building's kind",
            give.contains("val house = family.kind == WindowBuildingKind.HOUSE"),
        )
        assertTrue(
            "and every building but a house must keep a doorway",
            give.contains("if (!house) r.doorway = WindowWalk.Doorway()"),
        )
    }

    // ---------------------------------------------------------------- source plumbing

    private fun renderer(): String = rendererSource().readText()

    /** The body of the one function that draws every building. */
    private fun composer(): String =
        renderer().substringAfter("private fun drawNeighbourhoodBuilding(")
            .substringBefore("\n    private fun ")

    private fun drawSource(function: String): String =
        renderer().substringAfter("private fun $function(").substringBefore("\n    private fun ")

    private fun rendererSource(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = File(dir, "${prefix}src/main/kotlin/com/paperscrape/livewallpaper/engine/SceneObjectRenderer.kt")
                if (candidate.exists()) return candidate
            }
            dir = dir.parentFile
        }
        error("SceneObjectRenderer.kt not found from ${File(".").absolutePath}")
    }
}
