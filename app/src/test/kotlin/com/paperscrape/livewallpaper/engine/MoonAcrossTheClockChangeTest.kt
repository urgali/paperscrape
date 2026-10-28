package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

/**
 * **The moon does not jump at the change of the clocks with the wallpaper on screen**, and **a sunrise and sunset are
 * worked out for one reading of the clock**.
 *
 * Rome, 25 October 2026: at 03:00 CEST the clocks go back to 02:00 CET. The sunrise and sunset are worked out again only
 * at the weather loop's next pass, up to two minutes after the turn of the minute: an hour that followed the zone would
 * be CET's against times of CEST's for those minutes, and the moon would stand 0.072 of its arc to one side -- some
 * 40 px on the BV6600 -- then go back. The scene reads its hour on the offset its times were worked out for
 * ([SolarDaySchedule.sceneHour]), so it goes on from where it was, and the hour and the times change together when the
 * times are worked out again; the frame wakes the loop for that at the turn of the minute
 * ([SolarDaySchedule.workedOutForAnotherDay]).
 *
 * The jump itself is shown by the clock's own hour, as `Calendar` reads it in the zone, against the same times.
 */
class MoonAcrossTheClockChangeTest {

    private val rome = TimeZone.getTimeZone("Europe/Rome")

    private fun at(iso: String): Long = ZonedDateTime.parse(iso).toInstant().toEpochMilli()

    /** `HOUR_OF_DAY + MINUTE / 60` at [millis] in [zone], as `Calendar` reads it. */
    private fun calendarHour(millis: Long, zone: TimeZone): Float {
        val calendar = Calendar.getInstance(zone)
        calendar.timeInMillis = millis
        return calendar.get(Calendar.HOUR_OF_DAY) + calendar.get(Calendar.MINUTE) / 60f
    }

    /** Rome's sunrise and sunset, worked out at [instant] as the wallpaper does. */
    private fun heldAt(instant: Long): HeldSunTimes {
        val day = SolarDaySchedule.civilDayAt(instant, rome)
        val (rise, set) = SunPositionCalculator.approximateSunriseSunset(41.9, 12.5, day.dayOfYear, day.offsetMillis / 3_600_000.0)
        return HeldSunTimes(SolarDay.located(rise, set), day)
    }

    private fun moonX(hour: Float, held: HeldSunTimes): Float =
        SunPositionCalculator.compute(hour, held.day.sunriseHour, held.day.sunsetHour).celestialX

    private val before = at("2026-10-25T02:59:30+02:00")
    private val after = at("2026-10-25T02:00:30+01:00")

    @Test
    fun `the two instants are a minute apart, either side of the change`() {
        assertEquals(60_000L, after - before)
        assertEquals(2 * 3_600_000, rome.getOffset(before))
        assertEquals(3_600_000, rome.getOffset(after))
    }

    @Test
    fun `the hour stays on the offset of the times held until they are worked out again`() {
        val held = heldAt(before)
        assertEquals(2f + 59f / 60f, SolarDaySchedule.sceneHour(before, held, rome), 1e-5f)
        assertEquals("03:00 on the times' offset, not the clock's 02:00", 3f, SolarDaySchedule.sceneHour(after, held, rome), 1e-5f)
        assertTrue("and the frame wakes the loop to work them out again", SolarDaySchedule.workedOutForAnotherDay(held, after, rome))
        assertFalse(SolarDaySchedule.workedOutForAnotherDay(held, before, rome))
    }

    @Test
    fun `the moon goes on from where it was, and does not move when the times are worked out again`() {
        val held = heldAt(before)
        val x0 = moonX(SolarDaySchedule.sceneHour(before, held, rome), held)
        val x1 = moonX(SolarDaySchedule.sceneHour(after, held, rome), held)
        assertTrue("a minute of the moon's arc, not a jump: $x0 -> $x1", abs(x1 - x0) < 0.005f)

        val reworked = heldAt(after)
        assertFalse(SolarDaySchedule.workedOutForAnotherDay(reworked, after, rome))
        assertEquals("the hour is the clock's once the times are", 2f, SolarDaySchedule.sceneHour(after, reworked, rome), 1e-5f)
        val x2 = moonX(SolarDaySchedule.sceneHour(after, reworked, rome), reworked)
        assertTrue("the hour and the times change together: $x1 -> $x2", abs(x2 - x1) < 0.005f)
    }

    @Test
    fun `the clock's own hour against the times held is the jump`() {
        // The hour from the zone, against the times worked out the minute before.
        val held = heldAt(before)
        val x0 = moonX(calendarHour(before, rome), held)
        val x1 = moonX(calendarHour(after, rome), held)
        assertTrue("the jump this repairs, about 0.072: $x0 -> $x1", abs(x1 - x0) > 0.06f)
    }

    @Test
    fun `without a position the hour is the clock's own, in every zone, across the changes`() {
        val zones = listOf("Europe/Rome", "America/New_York", "Australia/Lord_Howe", "Asia/Kathmandu", "UTC").map { TimeZone.getTimeZone(it) }
        val instants = listOf(
            "2026-10-25T00:59:00Z", "2026-10-25T01:00:00Z", "2026-10-25T01:01:00Z",
            "2026-03-29T00:59:00Z", "2026-03-29T01:00:00Z", "2026-11-01T06:00:00Z", "2026-12-31T23:59:59Z",
        ).map { ZonedDateTime.parse(it).toInstant().toEpochMilli() }
        for (zone in zones) for (t in instants) {
            assertEquals("${zone.id} at $t", calendarHour(t, zone), SolarDaySchedule.sceneHour(t, HeldSunTimes.NONE, zone), 0f)
            assertFalse(SolarDaySchedule.workedOutForAnotherDay(HeldSunTimes.NONE, t, zone))
        }
    }

    // ------------------------------------------------------------------ one reading of the clock

    @Test
    fun `the day, the offset and the stamp worked out for are those of one instant`() {
        for (iso in listOf(
            "2026-10-25T02:59:59.999+02:00", "2026-10-25T02:00:00+01:00",
            "2026-10-24T23:59:59.999+02:00", "2026-10-25T00:00:00+02:00",
            "2026-12-31T23:59:59.999+01:00", "2027-01-01T00:00:00+01:00",
        )) {
            val t = at(iso)
            val day = SolarDaySchedule.civilDayAt(t, rome)
            val calendar = Calendar.getInstance(rome).apply { timeInMillis = t }
            assertEquals(iso, calendar.get(Calendar.DAY_OF_YEAR), day.dayOfYear)
            assertEquals(iso, rome.getOffset(t), day.offsetMillis)
            assertEquals(iso, SolarDaySchedule.stamp(t, rome), day.stamp)
        }
    }
}
