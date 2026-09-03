package com.paperscrape.livewallpaper.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The building windows' occupant rule, pinned at its source.
 *
 * rc2 narrowed this file to buildings. Until then it also asserted that vehicle occupants shared
 * the window rule -- and that equality, elegant as it was, is what made the people in the cars
 * 22% smaller than a child pedestrian at the same depth: a share of the glass and the height
 * table cannot agree by construction. The vehicles are sized off the table now (see
 * [SceneObjectRenderer.CAR_OCCUPANT_SCALE] and OccupantHeadFitTest); the buildings keep the v4.2
 * share, and this file keeps it honest.
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
    fun `the window rule in the source is the share the constant declares`() {
        val (paneFraction, divisor) = windowOccupantRuleFromSource()
        assertEquals("drawWindowOccupant's pane fraction", 0.85f, paneFraction, 0.0001f)
        assertEquals(
            "drawWindowOccupant's divisor",
            SceneObjectRenderer.WINDOW_OCCUPANT_DIVISOR_UNITS,
            divisor,
            0.0001f,
        )
        assertEquals(
            "the buildings' share, read off the window's own expression",
            paneFraction * SceneObjectRenderer.WINDOW_HEAD_HEAD_UNITS / divisor,
            SceneObjectRenderer.OCCUPANT_HEAD_PANE_SHARE,
            0.0001f,
        )
        assertEquals("and that share is 51.9%", 0.5194f, SceneObjectRenderer.OCCUPANT_HEAD_PANE_SHARE, 0.0005f)
    }

    @Test
    fun `a building occupant's head takes the window share of its pane`() {
        // rc2: the share is a rule about buildings only. The vehicles used to inherit it -- and
        // their people came out 22% smaller than a child pedestrian at the same depth, because a
        // share of the glass and the height table cannot agree by construction. Vehicle occupant
        // sizing now lives in [SceneObjectRenderer.CAR_OCCUPANT_SCALE] and is tested against the
        // artwork and the table in OccupantHeadFitTest; what this test keeps is the building rule
        // it was always really about.
        val share = SceneObjectRenderer.OCCUPANT_HEAD_PANE_SHARE
        val houseScale = HOUSE_PANE_UNITS * 0.85f / SceneObjectRenderer.WINDOW_OCCUPANT_DIVISOR_UNITS
        assertEquals(
            "a house occupant's head over its pane",
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

    /**
     * **A car never carries the same person twice.**
     *
     * The rc5 defect this pins: the passenger's family came from its own seed channel and only
     * the *tone* was forced apart when family and tone both collided, so two women — or two men —
     * could share a car. In this artwork a family carries its hairstyle **and** its clothing
     * (every woman bust is the red top with the yellow band, every man bust the blue one), so
     * two same-family occupants are identical in all three of family, hair and clothing whatever
     * the tone does.
     *
     * Read out of `drawCar` rather than restated, the way this file reads the window rule -- but
     * v4.19 makes the property checkable directly instead, because children now ride and the
     * passenger is no longer simply "the other adult". The rule is that the passenger's family is
     * never the driver's, chosen from the remaining three; the expression is exercised over the
     * whole seed space rather than grepped for, which is stronger than the string match this used
     * to do. The tone is asserted to still come from a seed channel, because forcing the family
     * apart must not quietly make every car the same pair.
     */
    @Test
    fun `the two occupants of a car are never the same family`() {
        val source = drawCarSource()
        // The shipped expression, exercised over every seed rather than matched as text.
        var sawChild = false
        for (seed in 0 until 100_000) {
            val driverKindIdx = seed % 2
            val passengerKindIdx = (driverKindIdx + 1 + seed / 7 % 3) % 4
            assertTrue(
                "seed $seed seats two of the same family",
                passengerKindIdx != driverKindIdx,
            )
            assertTrue("seed $seed picks a family outside the four", passengerKindIdx in 0..3)
            assertTrue("the driver is always an adult", driverKindIdx in 0..1)
            if (passengerKindIdx >= 2) sawChild = true
        }
        assertTrue("children must actually be reachable as passengers", sawChild)
        assertTrue(
            "and the shipped call site must be that expression",
            Regex("""val passengerKindIdx = \(driverKindIdx \+ 1 \+ driverSeed / 7 % 3\) % 4""")
                .containsMatchIn(source),
        )
        assertTrue(
            "the driver's family must still vary with the seed",
            Regex("""val driverKindIdx = driverSeed % 2""").containsMatchIn(source),
        )
        assertTrue(
            "both seats must still draw their skin tone from a seed channel",
            Regex("""val driverSkinIdx = driverSeed / \d+ % 3""").containsMatchIn(source) &&
                Regex("""val passengerSkinIdx = driverSeed / \d+ % 3""").containsMatchIn(source),
        )
        // And the table the two indices address really is [kind][season][skin] with the two
        // adult families first, so "the complement" means the other adult and not a child.
        val table = rendererSource().readText()
            .substringAfter("private val personCarHeadSkinDrawables = arrayOf(")
            .substringBefore("\n    )")
        val families = Regex("""person_(man|woman|boy|girl)_summer_head_car_skin0""")
            .findAll(table).map { it.groupValues[1] }.toList()
        assertEquals(
            "the first two rows of the occupant table must be the two adults",
            listOf("man", "woman"), families.take(2),
        )
    }

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

        /** `drawCar`'s body, so a rule can be read where it is written. */
        fun drawCarSource(): String = rendererSource().readText()
            .substringAfter("private fun drawCar(")
            .substringBefore("\n    private fun ")

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
