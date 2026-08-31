package com.paperscrape.livewallpaper.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scene has two kinds of window with people behind them, and one rule for how big a head is.
 *
 * ### Why this test exists
 *
 * It did not have one rule. `drawWindowOccupant` has sized house, shop and tower occupants by
 * `winW * 0.85 / 60` since v4.2, which puts a **head** at 51.9% of its pane and leaves the rest
 * glass. `drawCar` sized its busts by `glass / content`, which puts the bust at 100% of the pane
 * and the head at 72.6% -- so every head in a vehicle touched the roof line of its own window by
 * construction, on every vehicle and at every depth. Measured against the vehicle instead of the
 * pane, the same head sprite was 31.3% of a sedan and 14.9% of a fire engine.
 *
 * The two numbers had never been compared, because nothing compared them. That is this file.
 *
 * ### Why the head and not the bust
 *
 * The two bust families do not carry the same amount of head: 106 px of 146 for a driving head,
 * 110 of 169 for a window one. Matching *busts* would leave the driver's head 12% larger than the
 * passenger's beside them in the same car. Matching *heads* is what makes a driver, a passenger, a
 * fire engine's crew and somebody at an upstairs window read at one proportion, which is the
 * coherence the scene is judged on.
 *
 * ### What it pins
 *
 * [SceneObjectRenderer.OCCUPANT_HEAD_PANE_SHARE] is *derived* from the window rule rather than
 * typed, so the two cannot drift apart by editing the constant. What could still drift is the
 * window rule itself: someone changing `0.85` or the divisor inside `drawWindowOccupant` would move
 * the house occupants and leave the vehicles behind. So the expression is read back out of the
 * source, the way `VehiclePedestrianScaleTest` reads the door accessories' blit.
 */
class OneOccupantRuleTest {

    @Test
    fun `the vehicle glass and the house window give a head the same share of the pane`() {
        val (paneFraction, divisor) = windowOccupantRuleFromSource()
        assertEquals("drawWindowOccupant's pane fraction", 0.85f, paneFraction, 0.0001f)
        assertEquals(
            "drawWindowOccupant's divisor",
            SceneObjectRenderer.WINDOW_OCCUPANT_DIVISOR_UNITS,
            divisor,
            0.0001f,
        )
        assertEquals(
            "the vehicles' share must be the window's share, read off the window's own expression",
            paneFraction * SceneObjectRenderer.WINDOW_HEAD_HEAD_UNITS / divisor,
            SceneObjectRenderer.OCCUPANT_HEAD_PANE_SHARE,
            0.0001f,
        )
        assertEquals("and that share is 51.9%", 0.5194f, SceneObjectRenderer.OCCUPANT_HEAD_PANE_SHARE, 0.0005f)
    }

    /**
     * Every occupant in the scene, whatever they sit behind, is drawn to that one share.
     *
     * This is the assertion the complaint about v4.15 reduces to, and it is stated for all four
     * vehicles at once because "coherent between taxi, police, fire engine and saloon" is the
     * requirement. The taxi, the police car and the saloon share `car_window`, so they are one
     * case; the fire engine's cab is painted into its own body sprite and is the second.
     */
    @Test
    fun `every occupant's head takes that share of the pane it is behind`() {
        val share = SceneObjectRenderer.OCCUPANT_HEAD_PANE_SHARE
        for ((who, headUnits, scale, pane) in listOf(
            Quad("sedan driver", SceneObjectRenderer.CAR_HEAD_HEAD_UNITS, SceneObjectRenderer.CAR_HEAD_SCALE, SceneObjectRenderer.CAR_GLASS_HEIGHT_UNITS),
            Quad("sedan passenger", SceneObjectRenderer.WINDOW_HEAD_HEAD_UNITS, SceneObjectRenderer.CAR_PASSENGER_SCALE, SceneObjectRenderer.CAR_GLASS_HEIGHT_UNITS),
            Quad("fire engine driver", SceneObjectRenderer.CAR_HEAD_HEAD_UNITS, SceneObjectRenderer.FIRE_TRUCK_HEAD_SCALE, SceneObjectRenderer.FIRE_TRUCK_GLASS_HEIGHT_UNITS),
        )) {
            assertEquals("$who head over pane", share, headUnits * scale / pane, 0.0005f)
        }
        val houseScale = HOUSE_PANE_UNITS * 0.85f / SceneObjectRenderer.WINDOW_OCCUPANT_DIVISOR_UNITS
        assertEquals(
            "a house occupant, which is where the share comes from",
            share,
            SceneObjectRenderer.WINDOW_HEAD_HEAD_UNITS * houseScale / HOUSE_PANE_UNITS,
            0.0005f,
        )
    }

    /** Every pane the renderer hands `drawWindowOccupant` is the square one this rests on. */
    @Test
    fun `the house panes really are the size this test assumes`() {
        val calls = Regex("drawWindowOccupant\\(\\s*canvas,\\s*r,\\s*[-0-9.f]+,\\s*[-0-9.f]+,\\s*([0-9.]+)f,\\s*([0-9.]+)f")
            .findAll(rendererSource().readText())
            .map { it.groupValues[1].toFloat() to it.groupValues[2].toFloat() }
            .toList()
        assertTrue("no house occupant call sites found", calls.isNotEmpty())
        for ((w, h) in calls) {
            assertEquals("a house pane is square", w, h, 0.0001f)
            assertEquals("a house pane is $HOUSE_PANE_UNITS units", HOUSE_PANE_UNITS, w, 0.0001f)
        }
    }

    private data class Quad(val who: String, val headUnits: Float, val scale: Float, val pane: Float)

    private companion object {
        const val HOUSE_PANE_UNITS = 22f

        /** `val s = (winW * 0.85f) / WINDOW_OCCUPANT_DIVISOR_UNITS`, read from the renderer. */
        fun windowOccupantRuleFromSource(): Pair<Float, Float> {
            val source = rendererSource().readText()
            val match = Regex("val s = \\(winW \\* ([0-9.]+)f\\) / ([A-Za-z_]+)").find(source)
                ?: error("drawWindowOccupant's scale expression has changed shape")
            val divisor = when (val name = match.groupValues[2]) {
                "WINDOW_OCCUPANT_DIVISOR_UNITS" -> SceneObjectRenderer.WINDOW_OCCUPANT_DIVISOR_UNITS
                else -> error("unexpected divisor $name")
            }
            return match.groupValues[1].toFloat() to divisor
        }

        fun rendererSource(): File {
            val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/engine/SceneObjectRenderer.kt"
            var dir: File? = File(".").absoluteFile
            while (dir != null) {
                for (prefix in listOf("", "app/")) {
                    val candidate = File(dir, "$prefix$suffix")
                    if (candidate.isFile) return candidate
                }
                dir = dir.parentFile
            }
            error("could not locate $suffix")
        }
    }
}
