package com.paperscrape.livewallpaper.engine

import java.util.Calendar
import java.util.TimeZone

/**
 * When the wallpaper works out today's sunrise and sunset again.
 *
 * Extracted and made pure for the reason [com.paperscrape.livewallpaper.weather.LiveWeatherSchedule]
 * was: the rule lived inline in the service's weather loop, where no test could reach it, and it
 * was wrong there in a way nobody could see.
 *
 * ### The defect this replaces
 *
 * The times are computed once per position, so a wallpaper left running would keep yesterday's
 * sunrise for good -- a few minutes out after a week, and **an hour out across a DST change**,
 * which is when the scene's clock disagrees most visibly with the sky. v5.x added a staleness check
 * for that, and its comment said it was read on the weather loop's two-minute tick. It was not: it
 * was read only inside `refreshDeviceFix`, and the loop called that only with Live Weather on
 * **and** the position coming from the phone. With a Custom position, or with Live Weather off,
 * nothing ever asked again, and the scene kept the times of the day the user last touched a
 * setting (assessment v5.7, M5 -- found by reading the code; a device without root cannot move its
 * date, so `SolarDayScheduleTest` is the proof).
 *
 * ### The rule
 *
 * [onTick] is asked on every pass of the loop, whatever the location source and whether or not
 * Live Weather is on. It asks the device only for the one reason the loop already had -- a weather
 * refresh is due and the position comes from the phone; a fresh fix works the day out again as a
 * side effect. Otherwise, if a position is held and [stamp] says the day or the UTC offset has
 * moved since the times were worked out, it recomputes from the position already held. A town does
 * not move overnight, so no new fix is needed for that, and nothing new wakes the positioning
 * stack.
 *
 * The loop parks while the wallpaper is not visible and runs again the moment it is, so an engine
 * that sleeps through midnight recomputes on its first visible pass, and one left on screen within
 * one two-minute tick.
 */
internal object SolarDaySchedule {

    enum class Action {
        /** The times held are today's, or there is no position to work them out from. */
        NOTHING,

        /** Ask the device where it is (the weather loop's own reason); a fresh fix recomputes. */
        ASK_DEVICE,

        /** Work today's times out again from the position already held. */
        RECOMPUTE_FROM_HELD_POSITION,
    }

    /**
     * What one pass of the weather loop does about the sunrise and sunset.
     *
     * @param deviceSource whether the position comes from the phone (GPS or network) rather than
     *   from a Custom position or from nowhere.
     * @param weatherAttemptDue whether a weather refresh is due on this pass.
     * @param hasPosition whether the times held came from a position (`SolarDay.hasFix`).
     * @param dayIsStale whether [stamp] now differs from the stamp the times were worked out under.
     */
    fun onTick(
        liveWeatherEnabled: Boolean,
        deviceSource: Boolean,
        weatherAttemptDue: Boolean,
        hasPosition: Boolean,
        dayIsStale: Boolean,
    ): Action = when {
        liveWeatherEnabled && deviceSource && weatherAttemptDue -> Action.ASK_DEVICE
        hasPosition && dayIsStale -> Action.RECOMPUTE_FROM_HELD_POSITION
        else -> Action.NOTHING
    }

    /**
     * The civil day and UTC offset a sunrise/sunset pair is worked out for, as one number.
     *
     * Both halves are needed: the date moves at midnight, and a DST change moves the offset
     * without moving the date (on 25 October 2026 in Europe/Rome, 01:30 is UTC+2 and 03:30 is
     * UTC+1, the same day). Two instants with the same stamp would be given the same sunrise.
     */
    fun stamp(nowMillis: Long, zone: TimeZone): Long {
        val calendar = Calendar.getInstance(zone).apply { timeInMillis = nowMillis }
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR).toLong()
        val year = calendar.get(Calendar.YEAR).toLong()
        val offsetMinutes = zone.getOffset(nowMillis) / 60_000L
        return (year * 1000L + dayOfYear) * 10_000L + offsetMinutes
    }
}
