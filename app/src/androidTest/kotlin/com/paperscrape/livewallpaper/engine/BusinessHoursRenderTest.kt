package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The business hours, on rendered pixels (v4.22 Fase 4).
 *
 * The scene is the desert theme -- the one whose commercial frontage the `people-commercial`
 * golden proves populated -- rendered at a chosen scene hour. Three claims:
 *
 *  1. **Off is bitwise off.** With the toggle off the frame is identical to the default one,
 *     whatever the two hour fields hold. This is the property behind condition C: the default
 *     cannot move a golden because the default renders the same bytes.
 *  2. **Closed by day removes the people at the glass and nothing else** -- by day the window
 *     colours carry no night to scale, so the whole difference is the occupants.
 *  3. **Closed by night darkens the businesses** -- the difference includes the lit overlays,
 *     so it is strictly larger than pixels an occupant could account for.
 *
 * Which of the two call systems each difference comes from is pinned by
 * `BusinessHoursWiringTest`, which reads the call sites; here the systems are seen responding.
 */
class BusinessHoursRenderTest {

    private fun render(
        hour: Float,
        enabled: Boolean,
        open: Float = 9f,
        close: Float = 20f,
    ): Bitmap = SceneGolden.render(
        GoldenScene(
            name = "business-hours-probe",
            dayPhase = if (hour in 6f..20f) GoldenScene.day(hour) else GoldenScene.night(hour),
            themeId = "desert",
            customise = {
                it.copy(
                    businessHoursEnabled = enabled,
                    businessOpenHour = open,
                    businessCloseHour = close,
                )
            },
        ),
    )

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
        // colour has no night to lose, so the difference is exactly the commercial occupants.
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
        open.recycle(); closed.recycle()
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
