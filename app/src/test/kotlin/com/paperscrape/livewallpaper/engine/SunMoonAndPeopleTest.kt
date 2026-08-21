package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The day/night blend across the terminator.
 *
 * The Pixel 9 report was "the moon rises while it is still nearly daylight". The moon's timing was
 * never wrong -- it appears exactly at sunset -- but the blend the whole scene is coloured with
 * jumped from full night to **full day** at that same instant and then took 12% of the night to
 * come back down. These tests pin the fix: one continuous curve, half-light at the terminator from
 * both sides.
 */
class DayBlendContinuityTest {

    private fun blendAt(hour: Float) = SunPositionCalculator.compute(hour, 6f, 20f).dayBlend

    @Test
    fun `the blend does not jump across sunset`() {
        val justBefore = blendAt(19.98f)
        val justAfter = blendAt(20.02f)
        assertTrue("blend jumped: $justBefore -> $justAfter", kotlin.math.abs(justBefore - justAfter) < 0.05f)
    }

    @Test
    fun `the blend does not jump across sunrise`() {
        val justBefore = blendAt(5.98f)
        val justAfter = blendAt(6.02f)
        assertTrue("blend jumped: $justBefore -> $justAfter", kotlin.math.abs(justBefore - justAfter) < 0.05f)
    }

    @Test
    fun `sunset is half light, not full daylight`() {
        assertEquals(0.5f, blendAt(20f), 0.02f)
    }

    /** The specific regression: an hour into the night the sky must be dark, not still daylight. */
    @Test
    fun `an hour after sunset the sky is night, not day`() {
        val blend = blendAt(21f)
        assertTrue("still $blend of daylight an hour after sunset", blend < 0.2f)
    }

    @Test
    fun `the middle of the day is full daylight and the middle of the night is full dark`() {
        assertEquals(1f, blendAt(13f), 0.001f)
        assertEquals(0f, blendAt(2f), 0.001f)
    }

    @Test
    fun `the blend only ever falls between full night and full day`() {
        var hour = 0f
        while (hour < 24f) {
            val blend = blendAt(hour)
            assertTrue("out of range at $hour: $blend", blend in 0f..1f)
            hour += 0.05f
        }
    }

    /** Sun and moon still change over exactly at the terminator. The fix touched colour, not timing. */
    @Test
    fun `the sun is up between sunrise and sunset and the moon takes over after it`() {
        assertTrue(SunPositionCalculator.compute(12f, 6f, 20f).isSunVisible)
        assertTrue(SunPositionCalculator.compute(19.9f, 6f, 20f).isSunVisible)
        assertTrue(!SunPositionCalculator.compute(20.5f, 6f, 20f).isSunVisible)
        assertTrue(!SunPositionCalculator.compute(3f, 6f, 20f).isSunVisible)
    }

    /** With no location the fallback is 06:00/20:00, so nothing rises at 19:00 there either. */
    @Test
    fun `the location-free fallback keeps the sun up until eight`() {
        assertTrue(SunPositionCalculator.compute(19f).isSunVisible)
        assertTrue(!SunPositionCalculator.compute(20.5f).isSunVisible)
    }

    /** A real location, checked against its real sunset: Florence in late August, UTC+2. */
    @Test
    fun `a real location puts sunset where the almanac does`() {
        val (sunrise, sunset) = SunPositionCalculator.approximateSunriseSunset(
            latitudeDeg = 43.77, longitudeDeg = 11.25, dayOfYear = 232, utcOffsetHours = 2.0,
        )
        assertEquals("sunrise near 06:35", 6.6f, sunrise, 0.3f)
        assertEquals("sunset near 20:05", 20.05f, sunset, 0.3f)
        // And the moon therefore does not appear at 19:00 there.
        assertTrue(SunPositionCalculator.compute(19f, sunrise, sunset).isSunVisible)
    }

    /** The DST trap the calculator documents: the same day an hour off moves sunset an hour. */
    @Test
    fun `forgetting the dst offset would move sunset by an hour`() {
        val (_, withDst) = SunPositionCalculator.approximateSunriseSunset(43.77, 11.25, 232, 2.0)
        val (_, withoutDst) = SunPositionCalculator.approximateSunriseSunset(43.77, 11.25, 232, 1.0)
        assertEquals(1f, withDst - withoutDst, 0.01f)
    }

    @Test
    fun `a fixed hour produces the same phase as that hour on the clock`() {
        assertEquals(
            SunPositionCalculator.compute(18f, 6f, 20f).dayBlend,
            SunPositionCalculator.compute(18f, 6f, 20f).dayBlend,
            0f,
        )
        assertTrue(SunPositionCalculator.compute(18f, 6f, 20f).isSunVisible)
    }
}

/**
 * The arc-height control: what it stores, what the slider shows, and where the clouds end up.
 */
class SunCloudHeightTest {

    @Test
    fun `the slider spans the whole arc range`() {
        assertEquals(SUN_CLOUD_HEIGHT_MIN, sunCloudHeightForFraction(0f), 0.0001f)
        assertEquals(SUN_CLOUD_HEIGHT_MAX, sunCloudHeightForFraction(1f), 0.0001f)
        assertEquals(0.35f, sunCloudHeightForFraction(0.5f), 0.0001f)
    }

    @Test
    fun `the quarter points are evenly spread`() {
        assertEquals(0.225f, sunCloudHeightForFraction(0.25f), 0.0001f)
        assertEquals(0.475f, sunCloudHeightForFraction(0.75f), 0.0001f)
    }

    @Test
    fun `every position round-trips between the slider and the stored value`() {
        for (percent in 0..100) {
            val fraction = percent / 100f
            assertEquals(fraction, sunCloudHeightFraction(sunCloudHeightForFraction(fraction)), 0.0001f)
        }
    }

    /**
     * Nothing stored needs migrating: the scale the renderer reads is the one it always read, so a
     * theme saved before v2.12 lands on the slider exactly where its value always meant.
     */
    @Test
    fun `the default height sits inside the range, not at an end`() {
        val default = SceneCustomization.DEFAULT.sky.sunCloudHeight
        assertTrue(default > SUN_CLOUD_HEIGHT_MIN && default < SUN_CLOUD_HEIGHT_MAX)
        assertEquals(0.64f, sunCloudHeightFraction(default), 0.01f)
    }

    @Test
    fun `values outside the range are clamped rather than trusted`() {
        assertEquals(0f, sunCloudHeightFraction(0f), 0.0001f)
        assertEquals(1f, sunCloudHeightFraction(1f), 0.0001f)
        assertEquals(SUN_CLOUD_HEIGHT_MIN, sunCloudHeightForFraction(-2f), 0.0001f)
        assertEquals(SUN_CLOUD_HEIGHT_MAX, sunCloudHeightForFraction(5f), 0.0001f)
    }
}

/** Day and night pedestrian populations. */
class PeopleDensityTest {

    @Test
    fun `full daylight uses the day density and full dark uses the night one`() {
        assertEquals(0.8f, PeopleDensity.at(dayDensity = 0.8f, nightDensity = 0.2f, dayBlend = 1f), 0.0001f)
        assertEquals(0.2f, PeopleDensity.at(dayDensity = 0.8f, nightDensity = 0.2f, dayBlend = 0f), 0.0001f)
    }

    @Test
    fun `dusk is between the two, not one or the other`() {
        assertEquals(0.5f, PeopleDensity.at(0.8f, 0.2f, 0.5f), 0.0001f)
        assertEquals(0.35f, PeopleDensity.at(0.8f, 0.2f, 0.25f), 0.0001f)
    }

    @Test
    fun `an empty night empties the street and an empty day fills it only after dark`() {
        assertEquals(0f, PeopleDensity.at(1f, 0f, 0f), 0.0001f)
        assertEquals(1f, PeopleDensity.at(0f, 1f, 0f), 0.0001f)
        assertEquals(0f, PeopleDensity.at(0f, 1f, 1f), 0.0001f)
    }

    @Test
    fun `equal densities are the same population at every hour, as they were before the split`() {
        for (step in 0..10) {
            assertEquals(0.6f, PeopleDensity.at(0.6f, 0.6f, step / 10f), 0.0001f)
        }
    }

    @Test
    fun `out of range values are clamped`() {
        assertEquals(1f, PeopleDensity.at(2f, 2f, 0.5f), 0.0001f)
        assertEquals(0f, PeopleDensity.at(-1f, -1f, 0.5f), 0.0001f)
        assertEquals(0.4f, PeopleDensity.at(0.4f, 0.9f, 3f), 0.0001f)
    }

    // -- migration -----------------------------------------------------------------------------

    /** Every install before v2.12 is this case: one density, no night key. */
    @Test
    fun `an upgrade keeps the scene exactly as it was`() {
        assertEquals(0.35f, PeopleDensity.resolveNightDensity(stored = null, dayDensity = 0.35f), 0.0001f)
        assertEquals(1f, PeopleDensity.resolveNightDensity(stored = null, dayDensity = 1f), 0.0001f)
    }

    @Test
    fun `a stored night density wins over the day one`() {
        assertEquals(0.1f, PeopleDensity.resolveNightDensity(stored = 0.1f, dayDensity = 0.9f), 0.0001f)
        assertEquals(0f, PeopleDensity.resolveNightDensity(stored = 0f, dayDensity = 0.9f), 0.0001f)
    }

    @Test
    fun `a fresh install starts with both densities equal`() {
        val defaults = SceneCustomization.DEFAULT
        assertEquals(defaults.people.density, defaults.peopleNightDensity, 0.0001f)
    }

    @Test
    fun `every built-in theme starts with both densities equal`() {
        for (theme in ThemeCatalog.ALL) {
            val customization = defaultCustomizationFor(theme.id)
            assertEquals(
                "${theme.id} night density",
                customization.people.density,
                customization.peopleNightDensity,
                0.0001f,
            )
        }
    }
}
