package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.engine.SolarDaySchedule.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * **A day or an offset the sunrise and sunset were not worked out for asks the phone again, when the position is the
 * phone's** -- it is not worked out again from the position held.
 *
 * With the position from the phone and Live Weather off nothing else asks the phone once a position is held, and an
 * offset that moves while the day does not is most often a phone that moved. After a flight from Milan to New York on
 * 26 October, Milan's position worked out under New York's clock gives a sunrise at 02:19 and a sunset at 12:27 --
 * night over the scene from half past noon -- where New York's are 07:44 and 18:09. Asking the phone costs its cached
 * position first, the saved one, or a request where the throttle lets one through
 * ([com.paperscrape.livewallpaper.location.LocationRequestThrottle]).
 *
 * Held on [SolarDaySchedule.onTick]: a schedule that works the day out again from the position held fails here on its
 * assertion, with `RECOMPUTE_FROM_HELD_POSITION`.
 */
class StaleDayAsksThePhoneTest {

    private fun tick(liveWeatherEnabled: Boolean, weatherAttemptDue: Boolean, deviceSource: Boolean = true) = SolarDaySchedule.onTick(
        followRealTime = true,
        liveWeatherEnabled = liveWeatherEnabled,
        deviceSource = deviceSource,
        devicePositionUsable = true,
        weatherAttemptDue = weatherAttemptDue,
        hasPosition = true,
        holdsDevicePosition = deviceSource,
        dayIsStale = true,
    )

    @Test
    fun `a new offset with the phone's position asks the phone, Live Weather on or off`() {
        for (weather in listOf(false, true)) for (due in listOf(false, true)) {
            assertEquals("Live Weather $weather, refresh due $due", Action.ASK_DEVICE, tick(weather, due))
        }
    }

    @Test
    fun `a Custom position is still worked out again where it is`() {
        // The user's position does not move with the phone.
        for (weather in listOf(false, true)) for (due in listOf(false, true)) {
            assertEquals(Action.RECOMPUTE_FROM_HELD_POSITION, tick(weather, due, deviceSource = false))
        }
    }

    /** What the position held would have given: Milan (45.46 N, 9.19 E) under New York's -4 h on 26 October, day 299. */
    @Test
    fun `Milan's position under New York's clock is a night from half past noon`() {
        val (rise, set) = SunPositionCalculator.approximateSunriseSunset(45.46, 9.19, dayOfYear = 299, utcOffsetHours = -4.0)
        assertTrue("sunrise $rise, about 02:19", abs(rise - (2f + 19f / 60f)) < 0.05f)
        assertTrue("sunset $set, about 12:27", abs(set - (12f + 27f / 60f)) < 0.05f)
        // New York's own position (40.71 N, 74.01 W) on the same day and clock.
        val (nyRise, nySet) = SunPositionCalculator.approximateSunriseSunset(40.71, -74.01, dayOfYear = 299, utcOffsetHours = -4.0)
        assertTrue("New York's sunrise $nyRise, about 07:44", abs(nyRise - (7f + 44f / 60f)) < 0.1f)
        assertTrue("New York's sunset $nySet, about 18:09", abs(nySet - (18f + 9f / 60f)) < 0.1f)
    }
}
