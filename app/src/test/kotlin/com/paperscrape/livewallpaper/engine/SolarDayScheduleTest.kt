package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.engine.SolarDaySchedule.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlin.math.abs

/**
 * The sunrise and sunset are worked out again when the day or the UTC offset moves, whatever the
 * location source -- the defect of the v5.7 assessment (M5), which a device without root cannot
 * reproduce because it cannot move its own date. These tests are that proof.
 */
class SolarDayScheduleTest {

    private val rome = TimeZone.getTimeZone("Europe/Rome")

    private fun at(iso: String): Long = ZonedDateTime.parse(iso).toInstant().toEpochMilli()

    // ---------------------------------------------------------------- the stamp moves when it must

    @Test
    fun `the stamp moves at midnight`() {
        assertNotEquals(
            SolarDaySchedule.stamp(at("2026-10-23T23:59:00+02:00"), rome),
            SolarDaySchedule.stamp(at("2026-10-24T00:01:00+02:00"), rome),
        )
    }

    @Test
    fun `the stamp moves with the DST change inside one civil day`() {
        // 25 October 2026: 03:00 CEST becomes 02:00 CET. Same date, a different offset -- the case a
        // date-only check would miss, and the one that puts the sun an hour out.
        val before = at("2026-10-25T01:30:00+02:00")
        val after = at("2026-10-25T03:30:00+01:00")
        assertEquals("the test must stay inside one day", 298, java.util.Calendar.getInstance(rome).apply { timeInMillis = before }.get(java.util.Calendar.DAY_OF_YEAR))
        assertEquals(298, java.util.Calendar.getInstance(rome).apply { timeInMillis = after }.get(java.util.Calendar.DAY_OF_YEAR))
        assertNotEquals(SolarDaySchedule.stamp(before, rome), SolarDaySchedule.stamp(after, rome))
    }

    @Test
    fun `the stamp holds still through one day at one offset`() {
        assertEquals(
            SolarDaySchedule.stamp(at("2026-10-24T00:05:00+02:00"), rome),
            SolarDaySchedule.stamp(at("2026-10-24T23:55:00+02:00"), rome),
        )
    }

    @Test
    fun `the stamp moves across the new year`() {
        assertNotEquals(
            SolarDaySchedule.stamp(at("2026-12-31T12:00:00+01:00"), rome),
            SolarDaySchedule.stamp(at("2027-01-01T12:00:00+01:00"), rome),
        )
    }

    // ------------------------------------------------ every source recomputes once the day is over

    @Test
    fun `a Custom position recomputes a stale day, with Live Weather off`() {
        // The case nothing reached before v5.8.
        assertEquals(
            Action.RECOMPUTE_FROM_HELD_POSITION,
            SolarDaySchedule.onTick(
                liveWeatherEnabled = false, deviceSource = false, weatherAttemptDue = false,
                hasPosition = true, dayIsStale = true,
            ),
        )
    }

    @Test
    fun `a Custom position recomputes a stale day, with Live Weather on`() {
        // Custom never went through refreshDeviceFix, so Live Weather on did not help it either.
        for (due in listOf(false, true)) {
            assertEquals(
                "weatherAttemptDue=$due",
                Action.RECOMPUTE_FROM_HELD_POSITION,
                SolarDaySchedule.onTick(
                    liveWeatherEnabled = true, deviceSource = false, weatherAttemptDue = due,
                    hasPosition = true, dayIsStale = true,
                ),
            )
        }
    }

    @Test
    fun `the phone's position recomputes a stale day, with Live Weather off`() {
        assertEquals(
            Action.RECOMPUTE_FROM_HELD_POSITION,
            SolarDaySchedule.onTick(
                liveWeatherEnabled = false, deviceSource = true, weatherAttemptDue = true,
                hasPosition = true, dayIsStale = true,
            ),
        )
    }

    @Test
    fun `the phone's position with Live Weather on still asks the device when a refresh is due`() {
        // The one path that worked, unchanged: a due refresh asks for a fix, and the fix recomputes.
        for (stale in listOf(false, true)) {
            assertEquals(
                "dayIsStale=$stale",
                Action.ASK_DEVICE,
                SolarDaySchedule.onTick(
                    liveWeatherEnabled = true, deviceSource = true, weatherAttemptDue = true,
                    hasPosition = true, dayIsStale = stale,
                ),
            )
        }
        // And between refreshes a stale day is recomputed from the fix already held.
        assertEquals(
            Action.RECOMPUTE_FROM_HELD_POSITION,
            SolarDaySchedule.onTick(
                liveWeatherEnabled = true, deviceSource = true, weatherAttemptDue = false,
                hasPosition = true, dayIsStale = true,
            ),
        )
    }

    @Test
    fun `nothing is recomputed while the day is current, or without a position`() {
        for (weather in listOf(false, true)) for (device in listOf(false, true)) {
            assertEquals(
                "today's times are recomputed for nothing (weather=$weather, device=$device)",
                Action.NOTHING,
                SolarDaySchedule.onTick(weather, device, weatherAttemptDue = false, hasPosition = true, dayIsStale = false),
            )
            assertEquals(
                "no position, nothing to work out (weather=$weather, device=$device)",
                Action.NOTHING,
                SolarDaySchedule.onTick(weather, device, weatherAttemptDue = false, hasPosition = false, dayIsStale = true),
            )
        }
    }

    // ------------------------------------------------------------------- what it would have cost

    @Test
    fun `across the DST change the held sunrise would be an hour out`() {
        // Milan, the app's own default Custom position. The times worked out on 24 October (UTC+2)
        // and kept past the change are an hour off the ones for 25 October (UTC+1): the size of the
        // defect on the date it bites.
        val (riseBefore, _) = SunPositionCalculator.approximateSunriseSunset(45.4642, 9.19, dayOfYear = 297, utcOffsetHours = 2.0)
        val (riseAfter, _) = SunPositionCalculator.approximateSunriseSunset(45.4642, 9.19, dayOfYear = 298, utcOffsetHours = 1.0)
        val shift = riseBefore - riseAfter
        assertTrue("the held sunrise is ${shift}h off, expected about an hour", abs(shift - 1f) < 0.1f)
    }
}
