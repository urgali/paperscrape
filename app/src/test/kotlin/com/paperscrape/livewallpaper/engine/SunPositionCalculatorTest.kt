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

        // Half-light, not full dark. This assertion used to demand 0f at sunrise, which is the
        // shape the v2.12 fix removed: the day arc reached full night at the terminator while the
        // night arc, one instant earlier, was at full *day* -- a discontinuity that painted a
        // daylight sky over a rising moon. Both arcs now meet at 0.5 and the ramp continues from
        // there. See SunPositionCalculator.TERMINATOR_BLEND.
        assertEquals("sunrise should be half-light from both sides", 0.5f, atSunrise, tolerance)
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

    // --- Solar days that cross the device's midnight (P2-3) ------------------------------------
    //
    // `solarNoon = 12 - longitude/15 + utcOffset` is not pinned to 12:00: it moves with where a
    // place sits inside its timezone, and with how far that timezone's offset is from its
    // geography. Add a long summer day to a late solar noon and sunset genuinely falls after
    // midnight; subtract one from an early solar noon and sunrise genuinely falls before it. Until
    // v3.2 both were forced back inside `0..24` by a `coerceIn`, which does not move the day —
    // it deletes the part of it that did not fit.

    /** Ísafjörður, Iceland (66.07N 23.13W, UTC+0 year-round) in late June: sunset is after midnight. */
    @Test
    fun `a sunset after midnight is reported as after midnight, not as 24_00`() {
        val (sunrise, sunset) = SunPositionCalculator.approximateSunriseSunset(66.07, -23.13, 172, 0.0)

        assertTrue("sunrise should be in the small hours, was $sunrise", sunrise in 1f..4f)
        // The real thing sets around 00:40 the following morning. What must never happen again is
        // 24.0 -- the value a clamp produces, and the one that reads as "sets exactly at midnight".
        assertTrue("sunset should have wrapped past midnight, was $sunset", sunset < sunrise)
        assertTrue("sunset should be in the small hours, was $sunset", sunset in 0f..2f)
    }

    /**
     * The mirror case: a solar noon early enough that sunrise falls on the previous day.
     *
     * 66N at longitude 30E on UTC+0 -- a coordinate/offset pair chosen to force this branch rather
     * than a place anyone lives, because the combination needs a timezone two hours west of where
     * its geography puts it.
     */
    @Test
    fun `a sunrise before midnight is reported as before midnight, not as 00_00`() {
        val (sunrise, sunset) = SunPositionCalculator.approximateSunriseSunset(66.0, 30.0, 172, 0.0)

        assertTrue("sunrise should have wrapped back past midnight, was $sunrise", sunrise > sunset)
        assertTrue("sunrise should be late in the evening, was $sunrise", sunrise in 22f..24f)
        assertTrue("sunset should be in the evening, was $sunset", sunset in 20f..22f)
    }

    /**
     * A solar noon outside `0..24` entirely.
     *
     * Kiritimati keeps UTC+14 at 157W, so its solar noon lands near 36:30 on an unwrapped clock.
     * The clamp turned both ends into 23.98 and 24.0 -- a zero-length day at midnight, on an island
     * two degrees from the equator with twelve hours of daylight.
     */
    @Test
    fun `a solar noon past the end of the clock still yields a real day`() {
        val (sunrise, sunset) = SunPositionCalculator.approximateSunriseSunset(1.87, -157.4, 172, 14.0)

        assertTrue("sunrise should be in the morning, was $sunrise", sunrise in 5f..8f)
        assertTrue("sunset should be in the evening, was $sunset", sunset in 17f..20f)
        assertEquals(
            "an equatorial day is about twelve hours long",
            12f,
            SunPositionCalculator.dayLengthHours(sunrise, sunset),
            0.6f,
        )
    }

    /** The three reference cities, none of which wraps: the fix must not move an ordinary day. */
    @Test
    fun `ordinary locations are unchanged`() {
        data class Case(val name: String, val lat: Double, val lon: Double, val off: Double,
                        val rise: ClosedFloatingPointRange<Float>, val set: ClosedFloatingPointRange<Float>)
        // Day 172, the June solstice, each city on its own summer offset.
        for (c in listOf(
            Case("Mountain View", 37.39, -122.08, -7.0, 5.5f..6.2f, 20.1f..20.8f),
            Case("New York", 40.71, -74.01, -4.0, 5.2f..5.8f, 20.1f..20.8f),
            Case("Tokyo", 35.68, 139.69, 9.0, 4.2f..4.8f, 18.6f..19.2f),
        )) {
            val (sunrise, sunset) = SunPositionCalculator.approximateSunriseSunset(c.lat, c.lon, 172, c.off)
            assertTrue("${c.name}: sunrise $sunrise outside ${c.rise}", sunrise in c.rise)
            assertTrue("${c.name}: sunset $sunset outside ${c.set}", sunset in c.set)
            assertTrue("${c.name}: an ordinary day must not wrap", sunrise < sunset)
        }
    }

    @Test
    fun `every location and day of year produces a clock time`() {
        // Widened from the old version of this test, which only checked `0..24`. A wrapped pair is
        // still two clock times; what must never appear is a value outside the clock, or a NaN.
        for (latitude in listOf(-89.0, -66.0, -45.0, 0.0, 45.0, 66.0, 89.0)) {
            for (longitude in listOf(-179.0, -157.4, -74.0, 0.0, 30.0, 139.7, 179.0)) {
                for (offset in listOf(-11.0, -7.0, 0.0, 5.5, 9.0, 14.0)) {
                    for (dayOfYear in listOf(1, 80, 172, 266, 355)) {
                        val (sunrise, sunset) =
                            SunPositionCalculator.approximateSunriseSunset(latitude, longitude, dayOfYear, offset)
                        val where = "lat=$latitude lon=$longitude off=$offset day=$dayOfYear"
                        assertFalse("$where produced NaN", sunrise.isNaN() || sunset.isNaN())
                        assertTrue("$where sunrise $sunrise off the clock", sunrise in 0f..24f)
                        assertTrue("$where sunset $sunset off the clock", sunset in 0f..24f)
                        val length = SunPositionCalculator.dayLengthHours(sunrise, sunset)
                        assertTrue("$where day length $length is not a duration", length in 0f..24f)
                    }
                }
            }
        }
    }

    // --- dayLengthHours() and the day/night classification -------------------------------------

    @Test
    fun `day length reads a window that does not wrap`() {
        assertEquals(14f, SunPositionCalculator.dayLengthHours(6f, 20f), 1e-4f)
    }

    @Test
    fun `day length reads a window that wraps past midnight`() {
        // Sets at 00:40, rises at 02:00 the previous morning: 22h40m of light, not minus 1h20m.
        assertEquals(22.667f, SunPositionCalculator.dayLengthHours(2f, 0.667f), 1e-3f)
    }

    @Test
    fun `polar night is a zero length day and polar day is a full one`() {
        assertEquals(0f, SunPositionCalculator.dayLengthHours(11f, 11f), 1e-4f)
        assertEquals(24f, SunPositionCalculator.dayLengthHours(0f, 24f), 1e-4f)
    }

    @Test
    fun `compute keeps the sun up across midnight when the day wraps`() {
        // Light from 02:00 through to 00:40 the next morning.
        val sunrise = 2f
        val sunset = 0.667f
        for (hour in listOf(2.1f, 6f, 12f, 18f, 23f, 23.9f, 0.1f, 0.5f)) {
            assertTrue(
                "the sun should still be up at $hour",
                SunPositionCalculator.compute(hour, sunrise, sunset).isSunVisible,
            )
        }
        for (hour in listOf(0.8f, 1f, 1.5f, 1.9f)) {
            assertFalse(
                "the sun should be down at $hour",
                SunPositionCalculator.compute(hour, sunrise, sunset).isSunVisible,
            )
        }
    }

    @Test
    fun `compute keeps the sun down across midnight when the night wraps`() {
        // The ordinary case, and the one that must not have moved: light 06:00 to 20:00.
        for (hour in listOf(6f, 12f, 19.9f)) {
            assertTrue("up at $hour", SunPositionCalculator.compute(hour, 6f, 20f).isSunVisible)
        }
        for (hour in listOf(20.1f, 23f, 0f, 3f, 5.9f)) {
            assertFalse("down at $hour", SunPositionCalculator.compute(hour, 6f, 20f).isSunVisible)
        }
    }

    @Test
    fun `a wrapped day still runs the arc from horizon to horizon and back`() {
        val sunrise = 2f
        val sunset = 0.667f
        // Just after sunrise and just before sunset the sun is near the horizon; half way between
        // it is near the zenith. Nothing about the arc may depend on which side of midnight it is.
        val justUp = SunPositionCalculator.compute(2.2f, sunrise, sunset)
        val midday = SunPositionCalculator.compute(13.3f, sunrise, sunset)
        val aboutToSet = SunPositionCalculator.compute(0.5f, sunrise, sunset)

        assertTrue("just after sunrise should be low, was ${justUp.celestialY}", justUp.celestialY < 0.2f)
        assertTrue("mid-day should be high, was ${midday.celestialY}", midday.celestialY > 0.9f)
        assertTrue("just before sunset should be low, was ${aboutToSet.celestialY}", aboutToSet.celestialY < 0.2f)
        assertTrue("progress must stay on the clock", listOf(justUp, midday, aboutToSet).all { it.progress in 0f..1f })
    }

    @Test
    fun `the night of a wrapped day is short and continuous`() {
        val sunrise = 2f
        val sunset = 0.667f
        // 1h20m of night, entirely between 00:40 and 02:00. dayBlend must fall and rise again
        // rather than jumping, which is what a negative night length would have produced.
        val blends = listOf(0.7f, 1.0f, 1.3f, 1.6f, 1.9f).map {
            SunPositionCalculator.compute(it, sunrise, sunset).dayBlend
        }
        assertTrue("every night blend must be a real number", blends.all { it.isFinite() })
        assertTrue("the night should get darker before it gets lighter", blends[2] <= blends[0])
        assertTrue("and never leave 0..1", blends.all { it in 0f..1f })
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
