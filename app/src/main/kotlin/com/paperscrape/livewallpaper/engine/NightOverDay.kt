package com.paperscrape.livewallpaper.engine

/**
 * Halloween's night sky by day (v5.16, the mode 3 of v5.16A, the maintainer's *«va bene 3 ma deve diventare il nuovo
 * default del tema halloween»*, 2026-10-21): the phase every layer of a frame is drawn with while it is in force
 * ([SceneCustomization.nightOverDayActive]) and the frame's own is not night -- full night, the moon on the sun's path:
 *
 * - `dayBlend` 0 and no sun, so the sky, the landscape and the town take their night colours, the windows light, the
 *   stars come out and the night amounts of cars and people hold, as at midnight;
 * - the sun's place on its arc ([SunPositionCalculator.DayPhase.celestialX], `celestialY`) for the moon, which the
 *   renderer draws at night: full, as the carved moon always is;
 * - the frame's own hour ([SunPositionCalculator.DayPhase.hour24]) and day ([SunPositionCalculator.DayPhase.progress]),
 *   so the opening hours of the shops and the people at the windows keep the clock's, real or fixed.
 *
 * It darkens nothing that the night does not already darken: the fixed art and the people stay as drawn at every hour
 * (item 123, the maintainer's *«le persone e la scena mi piacciono così come sono»*, 2026-09-15). Pure: the renderer keeps
 * one instance a phase it is given.
 */
internal object NightOverDay {

    /** [phase] made night, or [phase] itself where it is night already. */
    fun of(phase: SunPositionCalculator.DayPhase): SunPositionCalculator.DayPhase =
        if (phase.dayBlend <= 0f && !phase.isSunVisible) {
            phase
        } else {
            SunPositionCalculator.DayPhase(
                progress = phase.progress,
                dayBlend = 0f,
                celestialX = phase.celestialX,
                celestialY = phase.celestialY,
                isSunVisible = false,
                hour24 = phase.hour24,
                moonPhase = phase.moonPhase,
            )
        }
}
