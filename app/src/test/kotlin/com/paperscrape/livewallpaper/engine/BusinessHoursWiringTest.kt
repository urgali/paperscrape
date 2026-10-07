package com.paperscrape.livewallpaper.engine

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The business hours reach **both** window systems, and only in the buildings that are businesses.
 *
 * Lights and occupants are two separate call paths -- the lit overlays ride `nightGlow`, the
 * figures come from `drawWindowOccupant` -- and one schedule must govern both: touch one and the
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

    @Test
    fun `every commercial occupant goes through the openness and every house occupant does not`() {
        val occupant = drawSource("drawWindowOccupant")
        assertTrue(
            "the occupant path must gate on the building kind, houses exempt",
            occupant.contains("if (kind == WindowBuildingKind.HOUSE) 1f else businessOpenness"),
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
