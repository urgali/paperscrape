package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The business hours, on rendered pixels (v4.22 Phase 4).
 *
 * The scene is the desert theme -- the one whose commercial frontage the `people-commercial`
 * golden proves populated -- rendered at a chosen scene hour. Three claims:
 *
 *  1. **Off is bitwise off.** With the toggle off the frame is identical to the default one,
 *     whatever the two hour fields hold. This is the property behind condition C: the default
 *     cannot move a golden because the default renders the same bytes.
 *  2. **Closed by day takes the people from the glass** -- by day the window colours carry no
 *     night to scale, so what can change is the occupants; the test asserts that the frame changes.
 *  3. **Closed by night darkens the businesses** -- their glass loses its night light, and since
 *     v5.12 the shut glass is the dark glass of a house's unlit window, not the day's pale glass
 *     (decision 58); the test asserts that the frame changes, that more of it is the dark glass and
 *     none more the pale.
 *
 * Which of the two call systems each difference comes from is pinned by
 * `BusinessHoursWiringTest`, which reads the call sites; here the systems are seen responding.
 */
class BusinessHoursRenderTest {

    /** Both groups at once -- the shops' and the towers' hours (v5.12) -- or, with [shops] / [towers], one. */
    private fun render(
        hour: Float,
        enabled: Boolean,
        open: Float = 9f,
        close: Float = 20f,
        shops: Boolean = enabled,
        towers: Boolean = enabled,
    ): Bitmap = SceneGolden.render(
        GoldenScene(
            name = "business-hours-probe",
            dayPhase = if (hour in 6f..20f) GoldenScene.day(hour) else GoldenScene.night(hour),
            themeId = "desert",
            customise = {
                it.copy(
                    shopHoursEnabled = shops,
                    shopOpenHour = open,
                    shopCloseHour = close,
                    towerHoursEnabled = towers,
                    towerOpenHour = open,
                    towerCloseHour = close,
                )
            },
        ),
    )

    /**
     * **Each group shuts only its own buildings** (v5.12): at 01:00 with the shops' hours alone the
     * frame differs from the open one, and with the towers' alone too, and the two differ from each
     * other -- one closes the shops' glass, the other the towers'; and the two together are the frame
     * both closed.
     */
    @Test
    fun eachGroupShutsItsOwnBuildings() {
        val open = render(hour = 1f, enabled = false)
        val shopsShut = render(hour = 1f, enabled = false, shops = true)
        val towersShut = render(hour = 1f, enabled = false, towers = true)
        val bothShut = render(hour = 1f, enabled = true)
        assertTrue("the shops' hours alone must shut something", SceneGolden.differingFraction(open, shopsShut) > 0.0)
        assertTrue("the towers' hours alone must shut something", SceneGolden.differingFraction(open, towersShut) > 0.0)
        assertTrue("and not the same buildings", SceneGolden.differingFraction(shopsShut, towersShut) > 0.0)
        assertTrue("the shops' alone leave the towers lit", SceneGolden.differingFraction(shopsShut, bothShut) > 0.0)
        assertTrue("the towers' alone leave the shops lit", SceneGolden.differingFraction(towersShut, bothShut) > 0.0)
        open.recycle(); shopsShut.recycle(); towersShut.recycle(); bothShut.recycle()
    }

    @Test
    fun withTheToggleOffTheHoursAreInertAndTheFrameIsUntouched() {
        val default = SceneGolden.render(
            GoldenScene(name = "business-hours-probe", dayPhase = GoldenScene.night(1f), themeId = "desert"),
        )
        val absurd = render(hour = 1f, enabled = false, open = 3f, close = 3.25f)
        assertEquals(
            "with the toggle off the frame must be bitwise the pre-feature one",
            0.0, SceneGolden.differingFraction(default, absurd), 0.0,
        )
        default.recycle(); absurd.recycle()
    }

    @Test
    fun closedByDayTheOccupantsLeaveTheGlass() {
        // 13:00 against a 15:00-20:00 business day: closed, in full daylight. By day the glass
        // colour has no night to lose, so what changes is the commercial occupants.
        val open = render(hour = 13f, enabled = false)
        val closed = render(hour = 13f, enabled = true, open = 15f, close = 20f)
        val differing = SceneGolden.differingFraction(open, closed)
        assertTrue(
            "closing by day must remove somebody from the commercial glass (differing " +
                "fraction $differing)",
            differing > 0.0,
        )
        open.recycle(); closed.recycle()
    }

    @Test
    fun closedByNightTheBusinessWindowsGoDark() {
        val open = render(hour = 1f, enabled = false)
        val closed = render(hour = 1f, enabled = true, open = 9f, close = 20f)
        val differing = SceneGolden.differingFraction(open, closed)
        assertTrue(
            "closing by night must darken the business windows (differing fraction $differing)",
            differing > 0.0,
        )
        // The maintainer's «Scuri, come le case (consigliato)» (2026-10-09): shut at night, the glass
        // is the houses' dark glass -- more of it in the frame -- and never the day's pale glass.
        val dark = SceneObjectRenderer.UNLIT_GLASS_NIGHT
        val pale = SceneObjectRenderer.WINDOW_GLASS_DAY
        assertTrue(
            "shut at night, the business glass must be the dark glass (${near(open, dark)} -> ${near(closed, dark)} pixels)",
            near(closed, dark) > near(open, dark),
        )
        assertTrue(
            "and not the day's pale glass (${near(open, pale)} -> ${near(closed, pale)} pixels)",
            near(closed, pale) <= near(open, pale),
        )
        open.recycle(); closed.recycle()
    }

    /** How many pixels of [frame] are [colour] within 6 per channel. */
    private fun near(frame: Bitmap, colour: Int): Int {
        val pixels = IntArray(frame.width * frame.height)
        frame.getPixels(pixels, 0, frame.width, 0, 0, frame.width, frame.height)
        fun close(p: Int, shift: Int) = kotlin.math.abs((p ushr shift and 0xFF) - (colour ushr shift and 0xFF)) <= 6
        return pixels.count { p -> close(p, 16) && close(p, 8) && close(p, 0) }
    }

    /** `open == close` is always open: the frame must be the toggle-off frame, day and night. */
    @Test
    fun openEqualsCloseMeansAlwaysOpen() {
        for (hour in floatArrayOf(1f, 13f)) {
            val off = render(hour = hour, enabled = false)
            val degenerate = render(hour = hour, enabled = true, open = 7f, close = 7f)
            assertEquals(
                "open == close at hour $hour must render as always open",
                0.0, SceneGolden.differingFraction(off, degenerate), 0.0,
            )
            off.recycle(); degenerate.recycle()
        }
    }
}
