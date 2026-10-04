package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.engine.SolarDaySchedule.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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

    /**
     * One pass, with the arguments a real-time scene with a usable phone position passes unless a test
     * says otherwise (v5.10D added the fixed hour, the phone's permission and the held phone position).
     */
    private fun tick(
        liveWeatherEnabled: Boolean,
        deviceSource: Boolean,
        weatherAttemptDue: Boolean,
        hasPosition: Boolean,
        dayIsStale: Boolean,
        followRealTime: Boolean = true,
        devicePositionUsable: Boolean = true,
        holdsDevicePosition: Boolean = deviceSource && hasPosition,
        userAskPending: Boolean = false,
        deviceRetryDue: Boolean = false,
    ) = SolarDaySchedule.onTick(
        followRealTime = followRealTime,
        liveWeatherEnabled = liveWeatherEnabled,
        deviceSource = deviceSource,
        devicePositionUsable = devicePositionUsable,
        weatherAttemptDue = weatherAttemptDue,
        hasPosition = hasPosition,
        holdsDevicePosition = holdsDevicePosition,
        dayIsStale = dayIsStale,
        userAskPending = userAskPending,
        deviceRetryDue = deviceRetryDue,
    )

    @Test
    fun `a Custom position recomputes a stale day, with Live Weather off`() {
        // The case nothing reached before v5.8.
        assertEquals(
            Action.RECOMPUTE_FROM_HELD_POSITION,
            tick(liveWeatherEnabled = false, deviceSource = false, weatherAttemptDue = false, hasPosition = true, dayIsStale = true),
        )
    }

    @Test
    fun `a Custom position recomputes a stale day, with Live Weather on`() {
        // Custom never went through refreshDeviceFix, so Live Weather on did not help it either.
        for (due in listOf(false, true)) {
            assertEquals(
                "weatherAttemptDue=$due",
                Action.RECOMPUTE_FROM_HELD_POSITION,
                tick(liveWeatherEnabled = true, deviceSource = false, weatherAttemptDue = due, hasPosition = true, dayIsStale = true),
            )
        }
    }

    @Test
    fun `the phone's position recomputes a stale day, with Live Weather off`() {
        assertEquals(
            Action.RECOMPUTE_FROM_HELD_POSITION,
            tick(liveWeatherEnabled = false, deviceSource = true, weatherAttemptDue = true, hasPosition = true, dayIsStale = true),
        )
    }

    @Test
    fun `the phone's position with Live Weather on still asks the device when a refresh is due`() {
        // The one path that worked, unchanged: a due refresh asks for a fix, and the fix recomputes.
        for (stale in listOf(false, true)) {
            assertEquals(
                "dayIsStale=$stale",
                Action.ASK_DEVICE,
                tick(liveWeatherEnabled = true, deviceSource = true, weatherAttemptDue = true, hasPosition = true, dayIsStale = stale),
            )
        }
        // And between refreshes a stale day is recomputed from the fix already held.
        assertEquals(
            Action.RECOMPUTE_FROM_HELD_POSITION,
            tick(liveWeatherEnabled = true, deviceSource = true, weatherAttemptDue = false, hasPosition = true, dayIsStale = true),
        )
    }

    @Test
    fun `nothing is recomputed while the day is current, or without a position`() {
        for (weather in listOf(false, true)) for (device in listOf(false, true)) {
            assertEquals(
                "today's times are recomputed for nothing (weather=$weather, device=$device)",
                Action.NOTHING,
                tick(weather, device, weatherAttemptDue = false, hasPosition = true, dayIsStale = false),
            )
        }
        // Without a position: nothing to work out from a Custom or no source. A phone source asks
        // instead since v5.10D (`a phone source with no position asks, Live Weather on or off`).
        for (weather in listOf(false, true)) {
            assertEquals(
                "no position, nothing to work out (weather=$weather)",
                Action.NOTHING,
                tick(weather, deviceSource = false, weatherAttemptDue = false, hasPosition = false, dayIsStale = true),
            )
        }
    }

    // ------------------------------------------------------------- v5.10D: rows 5 and 6

    @Test
    fun `a phone source with no position asks, Live Weather on or off`() {
        // The ask costs a read of the system's cached position; a real request is the throttle's to
        // allow, once an hour (`LocationRequestRhythmTest`). Before v5.10D a phone source with Live
        // Weather off and no position was asked only by a settings change.
        for (weather in listOf(false, true)) for (due in listOf(false, true)) for (stale in listOf(false, true)) {
            assertEquals(
                "weather=$weather due=$due stale=$stale",
                Action.ASK_DEVICE,
                tick(weather, deviceSource = true, weatherAttemptDue = due, hasPosition = false, dayIsStale = stale),
            )
        }
    }

    @Test
    fun `at a fixed hour nothing is asked and nothing is recomputed`() {
        // The maintainer's row 6: at a fixed hour no position counts (the scene draws the default day).
        for (weather in listOf(false, true)) for (device in listOf(false, true)) for (due in listOf(false, true))
            for (has in listOf(false, true)) for (stale in listOf(false, true)) {
                assertEquals(
                    "weather=$weather device=$device due=$due has=$has stale=$stale",
                    Action.NOTHING,
                    tick(weather, device, due, has, stale, followRealTime = false),
                )
            }
    }

    @Test
    fun `without the phone's permission a held phone position is forgotten, at any hour`() {
        for (followRealTime in listOf(true, false)) for (weather in listOf(false, true)) for (due in listOf(false, true)) {
            assertEquals(
                "followRealTime=$followRealTime weather=$weather due=$due",
                Action.FORGET_DEVICE_POSITION,
                tick(
                    weather, deviceSource = true, weatherAttemptDue = due, hasPosition = true, dayIsStale = true,
                    followRealTime = followRealTime, devicePositionUsable = false, holdsDevicePosition = true,
                ),
            )
        }
    }

    @Test
    fun `without the phone's permission and nothing held, the phone is not asked`() {
        for (weather in listOf(false, true)) for (due in listOf(false, true)) {
            assertEquals(
                Action.NOTHING,
                tick(
                    weather, deviceSource = true, weatherAttemptDue = due, hasPosition = false, dayIsStale = true,
                    devicePositionUsable = false, holdsDevicePosition = false,
                ),
            )
        }
    }

    @Test
    fun `a Custom position is never the phone's to forget`() {
        // `devicePositionUsable` is about the phone; a Custom position recomputes whatever it says.
        assertEquals(
            Action.RECOMPUTE_FROM_HELD_POSITION,
            tick(
                liveWeatherEnabled = true, deviceSource = false, weatherAttemptDue = true, hasPosition = true, dayIsStale = true,
                devicePositionUsable = false, holdsDevicePosition = false,
            ),
        )
    }

    @Test
    fun `a choice the phone has not answered yet is asked on every pass, a held position or not`() {
        // GPS chosen with the location off: the saved position stands in, and the choice is asked
        // again until the phone is really consulted -- once the location is on (LocationRequestRhythmTest).
        for (weather in listOf(false, true)) for (has in listOf(false, true)) {
            assertEquals(
                Action.ASK_DEVICE,
                tick(weather, deviceSource = true, weatherAttemptDue = false, hasPosition = has, dayIsStale = false, userAskPending = true),
            )
        }
        // Not at a fixed hour, and not without the permission.
        assertEquals(Action.NOTHING, tick(true, true, false, true, false, followRealTime = false, userAskPending = true))
        assertEquals(
            Action.NOTHING,
            tick(true, true, false, false, false, devicePositionUsable = false, holdsDevicePosition = false, userAskPending = true),
        )
    }

    /**
     * **«anche se ha già una posizione»** (the maintainer, 2026-10-04): a try that is due after a search
     * that found nothing asks the phone with a position held too, and with Live Weather off -- where
     * nothing else would have asked (v5.10E, `LocationRequestThrottle.retryDue`).
     */
    @Test
    fun `a try that is due asks the phone, with a position held and Live Weather off too`() {
        for (weather in listOf(false, true)) for (has in listOf(false, true)) {
            assertEquals(
                "Live Weather $weather, position held $has",
                Action.ASK_DEVICE,
                tick(weather, deviceSource = true, weatherAttemptDue = false, hasPosition = has, dayIsStale = false, deviceRetryDue = true),
            )
        }
        // Without it, a held position and Live Weather off ask nothing: the case v5.10D left still.
        assertEquals(Action.NOTHING, tick(false, true, false, true, false, deviceRetryDue = false))
    }

    @Test
    fun `a try that is due waits at a fixed hour, without the permission, and for a Custom position`() {
        assertEquals("fixed hour", Action.NOTHING, tick(true, true, false, true, false, followRealTime = false, deviceRetryDue = true))
        assertEquals(
            "no permission",
            Action.NOTHING,
            tick(true, true, false, false, false, devicePositionUsable = false, holdsDevicePosition = false, deviceRetryDue = true),
        )
        assertEquals("Custom", Action.NOTHING, tick(true, deviceSource = false, weatherAttemptDue = false, hasPosition = true, dayIsStale = false, deviceRetryDue = true))
    }

    @Test
    fun `only a change the user makes is a choice, never the engine's first settings`() {
        val none = com.paperscrape.livewallpaper.location.LocationSource.NONE
        val gps = com.paperscrape.livewallpaper.location.LocationSource.GPS
        val network = com.paperscrape.livewallpaper.location.LocationSource.NETWORK
        val custom = com.paperscrape.livewallpaper.location.LocationSource.CUSTOM
        // The read-only review of v5.10D: every engine start "changed" the source from none to GPS.
        assertFalse("an engine's start is not a choice", SolarDaySchedule.userChoseSource(appliedBefore = false, previous = none, requested = gps))
        assertTrue(SolarDaySchedule.userChoseSource(appliedBefore = true, previous = none, requested = gps))
        assertTrue(SolarDaySchedule.userChoseSource(appliedBefore = true, previous = custom, requested = network))
        assertTrue(SolarDaySchedule.userChoseSource(appliedBefore = true, previous = network, requested = gps))
        assertFalse("the same source again", SolarDaySchedule.userChoseSource(appliedBefore = true, previous = gps, requested = gps))
        assertFalse("Custom asks the phone nothing", SolarDaySchedule.userChoseSource(appliedBefore = true, previous = gps, requested = custom))
        assertFalse(SolarDaySchedule.userChoseSource(appliedBefore = true, previous = gps, requested = none))
    }

    @Test
    fun `the scene draws the default day at a fixed hour, and the located one in real time`() {
        val milan = SolarDay.located(sunriseHour = 7.4f, sunsetHour = 19.0f)
        assertSame(milan, SolarDaySchedule.sceneDay(followRealTime = true, located = milan))
        val fixed = SolarDaySchedule.sceneDay(followRealTime = false, located = milan)
        assertEquals(6f, fixed.sunriseHour)
        assertEquals(20f, fixed.sunsetHour)
        assertFalse(fixed.hasFix)
        assertSame(SolarDay.NONE, SolarDaySchedule.sceneDay(followRealTime = false, located = SolarDay.NONE))
    }

    @Test
    fun `at 19 00 on 3 October a fixed hour at Milan was dusk and is daylight now`() {
        // The photograph of row 6, as numbers: the same 19:00, the located day against the default.
        val (rise, set) = SunPositionCalculator.approximateSunriseSunset(45.4642, 9.19, dayOfYear = 276, utcOffsetHours = 2.0)
        val located = SunPositionCalculator.compute(hour24 = 19f, sunriseHour = rise, sunsetHour = set)
        val default = SunPositionCalculator.compute(hour24 = 19f)
        assertTrue("Milan's sunset that day is about 19:00 (got $set)", abs(set - 19f) < 0.25f)
        assertTrue(
            "the fixed 19:00 must be brighter on the default day (${default.dayBlend} vs ${located.dayBlend})",
            default.dayBlend > located.dayBlend + 0.2f,
        )
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
