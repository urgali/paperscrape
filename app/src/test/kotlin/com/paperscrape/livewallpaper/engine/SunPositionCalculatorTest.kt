package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Unit tests for [SunPositionCalculator].
 *
 * This class is pure JVM logic with no Android dependencies, so it runs as a plain local unit
 * test. The assertions here deliberately pin *behavioural contracts* the renderer relies on
 * (a `dayBlend` that reaches both extremes, a `progress` that stays in `[0,1)`, an arc that
 * peaks in the middle) rather than exact magic numbers, so that tuning the curve does not
 * produce spurious failures while an actual regression still does.
 */
class SunPositionCalculatorTest {

    private val tolerance = 0.0001f

    // --- compute(): day/night classification ---------------------------------------------

    @Test
    fun `midday is day and midnight is night`() {
        assertTrue(SunPositionCalculator.compute(13f).isSunVisible)
        assertFalse(SunPositionCalculator.compute(0f).isSunVisible)
        assertFalse(SunPositionCalculator.compute(23f).isSunVisible)
    }

    @Test
    fun `sunrise and sunset boundaries count as day`() {
        assertTrue(SunPositionCalculator.compute(6f, 6f, 20f).isSunVisible)
        assertTrue(SunPositionCalculator.compute(20f, 6f, 20f).isSunVisible)
    }

    // --- compute(): progress contract ------------------------------------------------------

    @Test
    fun `progress stays within zero to one for every hour of the day`() {
        var hour = 0f
        while (hour < 24f) {
            val progress = SunPositionCalculator.compute(hour).progress
            assertTrue(
                "progress out of range at hour=$hour: $progress",
                progress >= 0f && progress < 1f,
            )
            hour += 0.25f
        }
    }

    @Test
    fun `progress is 0_25 at sunrise, 0_5 at solar noon and 0_75 at sunset`() {
        // The doc contract on DayPhase.progress: 0 = solar midnight, 0.25 = sunrise,
        // 0.5 = solar noon, 0.75 = sunset.
        assertEquals(0.25f, SunPositionCalculator.compute(6f, 6f, 20f).progress, tolerance)
        assertEquals(0.50f, SunPositionCalculator.compute(13f, 6f, 20f).progress, tolerance)
        assertEquals(0.75f, SunPositionCalculator.compute(20f, 6f, 20f).progress, tolerance)
    }

    @Test
    fun `progress advances monotonically through the daylight arc`() {
        var previous = -1f
        var hour = 6f
        while (hour <= 20f) {
            val progress = SunPositionCalculator.compute(hour, 6f, 20f).progress
            assertTrue("progress went backwards at hour=$hour", progress > previous)
            previous = progress
            hour += 0.5f
        }
    }

    // --- compute(): dayBlend contract -------------------------------------------------------

    @Test
    fun `dayBlend is fully lit in the middle of the day and fully dark deep in the night`() {
        assertEquals(1f, SunPositionCalculator.compute(13f, 6f, 20f).dayBlend, tolerance)
        // Deep night: roughly halfway through the night arc from a 20:00 sunset.
        assertEquals(0f, SunPositionCalculator.compute(1f, 6f, 20f).dayBlend, tolerance)
    }

    @Test
    fun `dayBlend ramps rather than snapping at sunrise`() {
        val atSunrise = SunPositionCalculator.compute(6f, 6f, 20f).dayBlend
        val shortlyAfter = SunPositionCalculator.compute(6.5f, 6f, 20f).dayBlend
        val wellAfter = SunPositionCalculator.compute(9f, 6f, 20f).dayBlend

        assertEquals("sunrise should start from full dark", 0f, atSunrise, tolerance)
        assertTrue("blend should have started rising", shortlyAfter > atSunrise)
        assertTrue("blend should still be rising", wellAfter > shortlyAfter)
        assertEquals("blend should reach full day", 1f, wellAfter, tolerance)
    }

    @Test
    fun `dayBlend never leaves zero to one for any hour or any daylight length`() {
        val sunriseSunsetPairs = listOf(
            6f to 20f,      // ordinary day
            4f to 22f,      // long summer day
            9f to 15f,      // short winter day
            0f to 24f,      // polar day
            11.9f to 12.1f, // near-degenerate arc
        )
        for ((sunrise, sunset) in sunriseSunsetPairs) {
            var hour = 0f
            while (hour < 24f) {
                val blend = SunPositionCalculator.compute(hour, sunrise, sunset).dayBlend
                assertTrue(
                    "dayBlend out of range at hour=$hour sunrise=$sunrise sunset=$sunset: $blend",
                    blend in 0f..1f,
                )
                hour += 0.5f
            }
        }
    }

    // --- compute(): celestial arc ------------------------------------------------------------

    @Test
    fun `celestial arc starts and ends at the horizon and peaks at the midpoint`() {
        val atSunrise = SunPositionCalculator.compute(6f, 6f, 20f)
        val atNoon = SunPositionCalculator.compute(13f, 6f, 20f)
        val atSunset = SunPositionCalculator.compute(20f, 6f, 20f)

        assertEquals("should rise from the horizon", 0f, atSunrise.celestialY, 0.001f)
        assertEquals("should peak at the zenith", 1f, atNoon.celestialY, 0.001f)
        assertEquals("should set back to the horizon", 0f, atSunset.celestialY, 0.001f)

        assertEquals(0f, atSunrise.celestialX, tolerance)
        assertEquals(1f, atSunset.celestialX, tolerance)
    }

    @Test
    fun `degenerate daylight length does not produce NaN or infinity`() {
        // dayLength is floored at 1f inside compute(); this guards that floor.
        val phase = SunPositionCalculator.compute(12f, 12f, 12f)
        assertFalse(phase.progress.isNaN())
        assertFalse(phase.dayBlend.isNaN())
        assertFalse(phase.celestialX.isNaN())
        assertFalse(phase.celestialY.isNaN())
        assertTrue(phase.celestialY.isFinite())
    }

    // --- approximateSunriseSunset() -----------------------------------------------------------

    @Test
    fun `sunrise precedes sunset at a mid latitude`() {
        // Rome, ~41.9N 12.5E, spring equinox (day 80), UTC+1.
        val (sunrise, sunset) = SunPositionCalculator.approximateSunriseSunset(41.9, 12.5, 80, 1.0)
        assertTrue("sunrise $sunrise should precede sunset $sunset", sunrise < sunset)
        assertTrue("sunrise should be in the morning, was $sunrise", sunrise in 4f..9f)
        assertTrue("sunset should be in the evening, was $sunset", sunset in 16f..21f)
    }

    @Test
    fun `equinox day length is close to twelve hours`() {
        // At either equinox the day is ~12h everywhere, which is the strongest sanity check
        // available for the declination term without importing an ephemeris.
        for (latitude in listOf(-45.0, -20.0, 0.0, 20.0, 45.0, 60.0)) {
            val (sunrise, sunset) = SunPositionCalculator.approximateSunriseSunset(latitude, 0.0, 80, 0.0)
            val dayLength = sunset - sunrise
            assertEquals(
                "equinox day length wrong at latitude $latitude",
                12f,
                dayLength,
                0.5f,
            )
        }
    }

    @Test
    fun `summer days are longer in the north and shorter in the south`() {
        // Day 172 is around the June solstice.
        val (northRise, northSet) = SunPositionCalculator.approximateSunriseSunset(55.0, 0.0, 172, 0.0)
        val (southRise, southSet) = SunPositionCalculator.approximateSunriseSunset(-55.0, 0.0, 172, 0.0)
        assertTrue(
            "June should be long in the northern hemisphere",
            (northSet - northRise) > 15f,
        )
        assertTrue(
            "June should be short in the southern hemisphere",
            (southSet - southRise) < 9f,
        )
    }

    @Test
    fun `polar night and polar day clamp instead of producing NaN`() {
        // acos() of an out-of-range value would be NaN; the implementation clamps first.
        val polarNight = SunPositionCalculator.approximateSunriseSunset(80.0, 0.0, 355, 0.0)
        val polarDay = SunPositionCalculator.approximateSunriseSunset(80.0, 0.0, 172, 0.0)

        assertFalse("polar night sunrise is NaN", polarNight.first.isNaN())
        assertFalse("polar night sunset is NaN", polarNight.second.isNaN())
        assertFalse("polar day sunrise is NaN", polarDay.first.isNaN())
        assertFalse("polar day sunset is NaN", polarDay.second.isNaN())

        assertTrue("polar night should collapse to a near-zero day", polarNight.second - polarNight.first < 1f)
        assertTrue("polar day should stretch to a near-full day", polarDay.second - polarDay.first > 20f)
    }

    @Test
    fun `longitude within a timezone shifts solar noon`() {
        // Two points sharing UTC+1 but 15 degrees of longitude apart sit an hour apart in solar
        // time. This is the specific behaviour the implementation notes was previously broken by
        // discarding the utcOffset term, so it is worth pinning.
        val (eastRise, _) = SunPositionCalculator.approximateSunriseSunset(45.0, 15.0, 80, 1.0)
        val (westRise, _) = SunPositionCalculator.approximateSunriseSunset(45.0, 0.0, 80, 1.0)
        assertEquals(
            "15 degrees of longitude should move sunrise by about an hour",
            1f,
            westRise - eastRise,
            0.1f,
        )
    }

    @Test
    fun `results stay inside a valid clock range`() {
        for (latitude in listOf(-89.0, -45.0, 0.0, 45.0, 89.0)) {
            for (dayOfYear in listOf(1, 80, 172, 266, 355)) {
                val (sunrise, sunset) = SunPositionCalculator.approximateSunriseSunset(
                    latitude, 0.0, dayOfYear, 0.0,
                )
                assertTrue("sunrise $sunrise out of clock range", sunrise in 0f..24f)
                assertTrue("sunset $sunset out of clock range", sunset in 0f..24f)
            }
        }
    }

    // --- moonPhase() ---------------------------------------------------------------------------

    @Test
    fun `moon phase is zero at the reference new moon`() {
        // 2000-01-06 18:14 UTC, the reference epoch baked into the implementation.
        assertEquals(0f, SunPositionCalculator.moonPhase(947182440000L), 0.001f)
    }

    @Test
    fun `moon phase reaches full at roughly half a synodic month`() {
        val halfCycleMillis = (29.530588853 / 2.0 * 86_400_000.0).toLong()
        val phase = SunPositionCalculator.moonPhase(947182440000L + halfCycleMillis)
        assertEquals(0.5f, phase, 0.01f)
    }

    @Test
    fun `moon phase wraps back to new after a full synodic month`() {
        val fullCycleMillis = (29.530588853 * 86_400_000.0).toLong()
        val phase = SunPositionCalculator.moonPhase(947182440000L + fullCycleMillis)
        // Either just under 1 or just over 0 — both mean "back to new moon".
        assertTrue("expected a new moon, got $phase", phase < 0.01f || phase > 0.99f)
    }

    @Test
    fun `moon phase stays within zero to one before and after the reference epoch`() {
        val dayMillis = 86_400_000L
        for (dayOffset in -800..800 step 7) {
            val phase = SunPositionCalculator.moonPhase(947182440000L + dayOffset * dayMillis)
            assertTrue("moon phase out of range at offset $dayOffset: $phase", phase in 0f..1f)
        }
    }

    // --- The clock reading that replaced a per-frame Calendar ------------------------------

    /**
     * [SunPositionCalculator.hourAt] is the arithmetic that replaced a `Calendar` on the render
     * path, so the property that matters is not that it looks right but that it *agrees with the
     * Calendar it replaced* -- at every instant, in every zone. Anything less and the wallpaper's
     * whole day/night cycle could sit an hour out somewhere without any test noticing.
     *
     * Zones chosen for the cases that break naive arithmetic: a half-hour offset, a
     * three-quarter-hour one, a southern-hemisphere DST schedule, and a zone with no DST at all.
     */
    @Test
    fun `the clock reading agrees with the Calendar it replaced`() {
        val zones = listOf(
            "UTC", "Europe/Rome", "America/New_York", "Australia/Lord_Howe",
            "Asia/Kolkata", "Asia/Kathmandu", "Pacific/Chatham", "Africa/Nairobi",
        )
        // A year of samples at a stride that is not a whole number of hours, so the sweep lands
        // inside DST transitions rather than stepping over them.
        val start = 1_700_000_000_000L
        val stride = 37 * 60_000L + 13_000L
        for (zoneId in zones) {
            val zone = TimeZone.getTimeZone(zoneId)
            var millis = start
            while (millis < start + 365L * 86_400_000L) {
                val calendar = Calendar.getInstance(zone)
                calendar.timeInMillis = millis
                val expected = calendar.get(Calendar.HOUR_OF_DAY) + calendar.get(Calendar.MINUTE) / 60f
                assertEquals(
                    "$zoneId at $millis",
                    expected, SunPositionCalculator.hourAt(millis, zone), 0f,
                )
                millis += stride
            }
        }
    }

    @Test
    fun `the clock reading stays inside a day and lands on minute boundaries`() {
        val zone = TimeZone.getTimeZone("Europe/Rome")
        var millis = 1_700_000_000_000L
        repeat(2000) {
            val hour = SunPositionCalculator.hourAt(millis, zone)
            assertTrue("out of range: $hour", hour >= 0f && hour < 24f)
            // Every representable value is some whole minute of the day.
            val minuteOfDay = Math.round(hour * 60f)
            assertEquals(hour, minuteOfDay / 60 + (minuteOfDay % 60) / 60f, 0f)
            millis += 61_000L
        }
    }

    @Test
    fun `the clock reading is correct before the epoch too`() {
        // floorDiv/floorMod rather than / and %: a negative epochMillis would otherwise round
        // toward zero and put the hour a day out.
        val zone = TimeZone.getTimeZone("UTC")
        val calendar = Calendar.getInstance(zone)
        for (offsetDays in -400L..-1L step 17L) {
            // Deliberately not a whole minute past midnight: at an exact minute boundary a
            // truncating division and a flooring one agree even for negative values, so a test
            // that only sampled those would pass against arithmetic that is a day out for every
            // other instant before 1970.
            val millis = offsetDays * 86_400_000L + 5_400_000L + 37_123L
            calendar.timeInMillis = millis
            val expected = calendar.get(Calendar.HOUR_OF_DAY) + calendar.get(Calendar.MINUTE) / 60f
            assertEquals(expected, SunPositionCalculator.hourAt(millis, zone), 0f)
        }
    }

    @Test
    fun `the cached reading matches an uncached one for the current minute`() {
        val now = System.currentTimeMillis()
        assertEquals(
            SunPositionCalculator.hourAt(now, TimeZone.getDefault()),
            SunPositionCalculator.currentHour24(),
            0f,
        )
    }
}
