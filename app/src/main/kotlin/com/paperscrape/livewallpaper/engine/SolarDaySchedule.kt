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
 * Live Weather is on. If a position is held and [stamp] says the day or the UTC offset has moved
 * since the times were worked out, it recomputes from the position already held. A town does not
 * move overnight, so no new fix is needed for that, and nothing new wakes the positioning stack.
 *
 * It asks the device for three reasons: the one the loop already had -- a weather refresh is due and
 * the position comes from the phone; a fresh fix works the day out again as a side effect -- and,
 * since v5.10D, a phone source with **no position held at all**, Live Weather on or off; and, since
 * v5.10E, **a try that is due after a request that found nothing**, whatever is held (the maintainer,
 * 2026-10-04: *«riprovi dopo 5, 15 e 30 minuti - anche se ha già una posizione»*). Asking is cheap:
 * [com.paperscrape.livewallpaper.location.DeviceLocationProvider] answers from the system's own cached
 * position, or the one it saved, and makes a real request only when
 * [com.paperscrape.livewallpaper.location.LocationRequestThrottle] lets it -- an hour after one that
 * brought a position; 5, 15 and 30 minutes after the first, second and third in a row that did not,
 * then the hour. Until v5.10D the loop's every pass without a position was a request.
 *
 * **Two things it does not do since v5.10D** (the maintainer's decisions of 2026-09-30 on rows 5 and
 * 6 of the v5.10A table):
 *
 *  - **with a fixed hour, nothing is asked**: the scene then uses the default 6:00 and 20:00
 *    ([sceneDay]) and no position counts, so the settings screen's grey Location row, "Available
 *    while the scene follows real time", is true;
 *  - **without the phone's permission, no position from the phone is kept**: one held is forgotten
 *    ([Action.FORGET_DEVICE_POSITION]), and none is asked for until the permission is back
 *    ([com.paperscrape.livewallpaper.location.DeviceLocationAccess.mayUsePosition]).
 *
 * The loop parks while the wallpaper is not visible and runs again the moment it is, so an engine
 * that sleeps through midnight recomputes on its first visible pass, and one left on screen within
 * one two-minute tick.
 */
internal object SolarDaySchedule {

    enum class Action {
        /** The times held are today's, or there is no position to work them out from. */
        NOTHING,

        /** Ask the device where it is; a fresh fix, or the one it saved, recomputes. */
        ASK_DEVICE,

        /** Work today's times out again from the position already held. */
        RECOMPUTE_FROM_HELD_POSITION,

        /**
         * The phone no longer lets PaperScrape use its position (v5.10D): drop the one held from it,
         * with the sunrise, the sunset and the weather worked out from it.
         */
        FORGET_DEVICE_POSITION,
    }

    /**
     * What one pass of the weather loop does about the position and the sunrise and sunset.
     *
     * @param followRealTime whether the scene follows the clock; with a fixed hour no position counts.
     * @param liveWeatherEnabled whether Live Weather runs (`LiveWeatherSchedule.runs`).
     * @param deviceSource whether the position comes from the phone (GPS or network) rather than
     *   from a Custom position or from nowhere.
     * @param devicePositionUsable whether the phone lets PaperScrape use a position from it
     *   (`DeviceLocationAccess.mayUsePosition`); ignored without [deviceSource].
     * @param weatherAttemptDue whether a weather refresh is due on this pass.
     * @param hasPosition whether the times held came from a position (`SolarDay.hasFix`).
     * @param holdsDevicePosition whether a position from the phone is held, fresh or saved.
     * @param dayIsStale whether [stamp] now differs from the stamp the times were worked out under.
     * @param userAskPending whether the user has chosen GPS or Network and no real request has been
     *   made for that choice yet ([userChoseSource]): asked until one is, so a choice made with the
     *   location switched off is answered once it is on, not at the next engine.
     * @param deviceRetryDue whether the last real request found nothing and its next try is due
     *   (`LocationRequestThrottle.retryDue`, v5.10E): asked with a position held too, and with Live
     *   Weather off -- the maintainer's 5, 15 and 30 minutes do not wait for a weather refresh.
     */
    fun onTick(
        followRealTime: Boolean,
        liveWeatherEnabled: Boolean,
        deviceSource: Boolean,
        devicePositionUsable: Boolean,
        weatherAttemptDue: Boolean,
        hasPosition: Boolean,
        holdsDevicePosition: Boolean,
        dayIsStale: Boolean,
        userAskPending: Boolean = false,
        deviceRetryDue: Boolean = false,
    ): Action = when {
        deviceSource && !devicePositionUsable && holdsDevicePosition -> Action.FORGET_DEVICE_POSITION
        !followRealTime -> Action.NOTHING
        deviceSource && devicePositionUsable &&
            (!hasPosition || userAskPending || deviceRetryDue || (liveWeatherEnabled && weatherAttemptDue)) -> Action.ASK_DEVICE
        hasPosition && dayIsStale -> Action.RECOMPUTE_FROM_HELD_POSITION
        else -> Action.NOTHING
    }

    /**
     * How long the loop waits before its next pass (v5.10E): its usual [checkIntervalMillis], or less
     * when the phone is this engine's source and a try after a request that found nothing falls due
     * sooner ([retryPassDelayMillis], `LocationRequestThrottle.passDelay`) -- so the try is made 5
     * minutes after, as the maintainer asked, and not at the first two-minute pass after that.
     */
    fun nextPassDelayMillis(checkIntervalMillis: Long, deviceSource: Boolean, retryPassDelayMillis: Long): Long =
        if (deviceSource) minOf(checkIntervalMillis, retryPassDelayMillis) else checkIntervalMillis

    /**
     * Whether a settings change is the user choosing GPS or Network -- the choice whose first request
     * passes the wait (`LocationRequestThrottle`, `force`).
     *
     * **Not the engine's first settings** ([appliedBefore] false): an engine starts with no source
     * held, so its first settings always "change" it, and until the read-only review of v5.10D found
     * it every engine start -- every rebind, and every picker preview -- made a forced request, the
     * case the hour exists to stop. Nor a change to Custom or Off, which ask the phone nothing.
     */
    fun userChoseSource(
        appliedBefore: Boolean,
        previous: com.paperscrape.livewallpaper.location.LocationSource,
        requested: com.paperscrape.livewallpaper.location.LocationSource,
    ): Boolean = appliedBefore && requested != previous && requested.deviceKind != null

    /**
     * The sunrise and sunset the scene is drawn with: the ones worked out from the position while the
     * scene follows real time, and the default 6:00 and 20:00 ([SolarDay.NONE]) at a fixed hour.
     *
     * **The maintainer's decision of 2026-09-30, row 6 of the v5.10A table, *«6 - voglio B»*** (the PM
     * had recommended the other way, a Location row usable at a fixed hour). Until v5.10D a fixed hour
     * drew the sky of the place saved in Location -- which the settings screen shows grey, "Available
     * while the scene follows real time" -- so 19:00 at Milan in October was a sunset, and the same
     * 19:00 with no location was daylight. He was told that a fixed-hour wallpaper with a saved
     * position changes; the gallery cards already drew their hours on the default day.
     */
    fun sceneDay(followRealTime: Boolean, located: SolarDay): SolarDay =
        if (followRealTime) located else SolarDay.NONE

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
