package com.paperscrape.livewallpaper.engine

import java.io.File
import org.junit.Assert.assertFalse
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

    // ---------------------------------------------------------------- the lights, per business

    @Test
    fun `the restaurant's night is scaled by the business openness`() {
        assertTrue(
            drawSource("drawRestaurantBuilding")
                .contains("val nightGlow = (1f - dayBlend).coerceIn(0f, 1f) * businessOpenness"),
        )
    }

    @Test
    fun `the bar's glass follows the hours while its joinery follows the sky`() {
        val bar = drawSource("drawBarBuilding")
        assertTrue("the glass night must be the business-scaled one",
            bar.contains("val barGlassNight = barNight * businessOpenness"))
        assertTrue("the painted front must keep the sky's own night",
            bar.contains("blendARGB(BAR_FRONT_DAY, BAR_FRONT_NIGHT, barNight)"))
        assertTrue("the upper windows light through the business night",
            bar.contains("litWindowAlpha(barGlassNight)"))
    }

    @Test
    fun `the tower's window grid is scaled by the business openness`() {
        assertTrue(
            drawSource("drawSkyscraperBuilding")
                .contains("val nightGlow = (1f - dayBlend).coerceIn(0f, 1f) * businessOpenness"),
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

    // ---------------------------------------------------------------- the homes stay out

    @Test
    fun `the houses' windows never consult the business hours`() {
        for (house in listOf("drawSmallHouse", "drawLargeHouse")) {
            val body = drawSource(house)
            assertTrue("$house must still light its windows on the sky's night",
                body.contains("litWindowAlpha(nightGlow)"))
            assertFalse("$house must not read the business openness -- a home is not a business",
                body.contains("businessOpenness"))
        }
    }

    // ---------------------------------------------------------------- source plumbing

    private fun drawSource(function: String): String =
        rendererSource().readText()
            .substringAfter("private fun $function(")
            .substringBefore("\n    private fun ")

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
