package com.paperscrape.livewallpaper.engine

import java.util.Calendar
import java.util.TimeZone

/**
 * When the wallpaper works out today's sunrise and sunset again.
 *
 * A pure rule, as [com.paperscrape.livewallpaper.weather.LiveWeatherSchedule] is, so that a test
 * reaches it: inline in the service's weather loop no test could, and a mistake there would show to
 * nobody.
 *
 * ### The defect it prevents
 *
 * The times are computed once per position, so a wallpaper left running would keep yesterday's
 * sunrise for good -- a few minutes out after a week, and **an hour out across a DST change**,
 * which is when the scene's clock disagrees most visibly with the sky. So the staleness check is
 * read on every pass of the weather loop, not inside `refreshDeviceFix`: read only there, it would
 * run only with Live Weather on **and** the position coming from the phone, and with a Custom
 * position, or with Live Weather off, nothing would ever ask again and the scene would keep the
 * times of the day the user last touched a setting (`SolarDayScheduleTest` holds the rule on the
 * JVM).
 *
 * ### The rule
 *
 * [onTick] is asked on every pass of the loop, whatever the location source and whether or not
 * Live Weather is on. If a position is held and [stamp] says the day or the UTC offset has moved
 * since the times were worked out, a **Custom** position works them out again where it is: it is
 * the user's, and it does not move. A position **from the phone** is asked of the phone again
 * instead: an offset that moves while nothing else did is most often a phone that moved, and after a
 * flight from Milan to New York Milan's position under New York's clock gives a sunrise at 02:19 and
 * a sunset at 12:27. The answer is the system's cached position, the saved one, or a request where
 * the throttle lets one through, and it works the day out again whichever it is.
 *
 * It asks the device for five reasons: a weather refresh is due and Live Weather would send it (a
 * fresh fix works the day out again as a side effect); **no position held at all**, Live Weather on
 * or off; the user's choice of GPS or Network not answered yet; **a try that is due after a request
 * that found nothing**, whatever is held (`DESIGN_NOTES.md` §12, decision 41, 2026-10-04: *«riprovi dopo 5, 15 e 30
 * minuti - anche se ha già una posizione»*); and **a day or an offset the times held were not worked
 * out for**. Asking is cheap:
 * [com.paperscrape.livewallpaper.location.DeviceLocationProvider] answers from the system's own cached
 * position, or the one it saved, and makes a real request only when
 * [com.paperscrape.livewallpaper.location.LocationRequestThrottle] lets it -- an hour after one that
 * brought a position; 5, 15 and 30 minutes after the first, second and third in a row that did not,
 * then the hour. Without the throttle, every pass of the loop without a position would be a request.
 *
 * **Two things it does not do** (`DESIGN_NOTES.md` §12, decision 41):
 *
 *  - **with a fixed hour, nothing is asked**: the scene then uses the default 6:00 and 20:00
 *    ([sceneDay]) and no position counts, so the settings screen's grey Location row, "Available
 *    while the scene follows real time", is true;
 *  - **without the phone's permission, no position from the phone is kept**: one held is forgotten
 *    ([Action.FORGET_DEVICE_POSITION]), and none is asked for until the permission is back
 *    ([com.paperscrape.livewallpaper.location.DeviceLocationAccess.mayUsePosition]).
 *
 * The loop parks while the wallpaper is not visible and runs again the moment it is, so an engine
 * that sleeps through midnight recomputes on its first visible pass; one left on screen is woken by
 * its first frame of the new day or offset ([workedOutForAnotherDay], read once a minute), and draws
 * its hour on the offset of the times it holds until they are worked out again ([sceneHour]).
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
         * The phone does not let PaperScrape use its position: drop the one held from it,
         * with the sunrise, the sunset and the weather worked out from it.
         */
        FORGET_DEVICE_POSITION,
    }

    /**
     * What one pass of the weather loop does about the position and the sunrise and sunset.
     *
     * @param followRealTime whether the scene follows the clock; with a fixed hour no position counts.
     * @param liveWeatherEnabled whether Live Weather would send a request when one is due
     *   (`LiveWeatherSchedule.wouldFetch`): it runs, and the selected provider needs no key or has one.
     *   A provider waiting for its key sends nothing, and a position asked for it would be asked for nothing.
     * @param deviceSource whether the position comes from the phone (GPS or network) rather than
     *   from a Custom position or from nowhere.
     * @param devicePositionUsable whether the phone lets PaperScrape use a position from it
     *   (`DeviceLocationAccess.mayUsePosition`); ignored without [deviceSource].
     * @param weatherAttemptDue whether a weather refresh is due on this pass.
     * @param hasPosition whether the times held came from a position (`SolarDay.hasFix`).
     * @param holdsDevicePosition whether a position from the phone is held, fresh or saved.
     * @param dayIsStale whether [stamp] now differs from the stamp the times were worked out under: a
     *   Custom position works them out again, a phone source asks the phone.
     * @param userAskPending whether the user has chosen GPS or Network and no real request has been
     *   made for that choice yet ([userChoseSource]): asked until one is, so a choice made with the
     *   location switched off is answered once it is on, not at the next engine.
     * @param deviceRetryDue whether the last real request found nothing and its next try is due
     *   (`LocationRequestThrottle.retryDue`): asked with a position held too, and with Live
     *   Weather off -- the tries after 5, 15 and 30 minutes (decision 41) do not wait for a weather refresh.
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
            (!hasPosition || dayIsStale || userAskPending || deviceRetryDue || (liveWeatherEnabled && weatherAttemptDue)) -> Action.ASK_DEVICE
        hasPosition && dayIsStale -> Action.RECOMPUTE_FROM_HELD_POSITION
        else -> Action.NOTHING
    }

    /**
     * The civil day and UTC offset of [nowMillis] in [zone], and their [stamp]: everything a sunrise and sunset are
     * worked out for, from **one** reading of the clock. Three readings -- the day, the offset and the stamp -- would
     * let a working-out that falls across midnight or the change of the clocks stamp the new day with the old day's
     * sunrise, or the new offset with the old one's: an hour out for a whole day.
     *
     * The offset is `getOffset(instant)`, not `rawOffset`: the raw offset is the standard one, and summer time would
     * put every sunrise and sunset an hour out.
     */
    fun civilDayAt(nowMillis: Long, zone: TimeZone): CivilDay {
        val calendar = Calendar.getInstance(zone).apply { timeInMillis = nowMillis }
        val offsetMillis = zone.getOffset(nowMillis)
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val year = calendar.get(Calendar.YEAR)
        return CivilDay(dayOfYear, offsetMillis, stampOf(year, dayOfYear, offsetMillis))
    }

    /**
     * The hour the scene is drawn at while it follows the clock, quantised to the minute: **on the UTC offset the
     * sunrise and sunset [held] were worked out for**, while they come from a position; on [zone]'s otherwise, which
     * is the clock's own hour.
     *
     * The hour and the two times place the moon together. At the change of the clocks the zone moves at 03:00 and
     * the times are worked out again a moment later, on the loop's pass: an hour read on the new offset against times
     * of the old one would put the moon some 40 px to one side until then, and back after. On the offset of the times
     * the scene goes on from where it was, and both move together once the times are worked out again.
     */
    fun sceneHour(nowMillis: Long, held: HeldSunTimes, zone: TimeZone): Float =
        hourOnOffset(nowMillis, held.workedOutFor?.offsetMillis ?: zone.getOffset(nowMillis))

    /** `HOUR_OF_DAY + MINUTE / 60` at [nowMillis] on the UTC offset [offsetMillis]: `Calendar`'s reading, without a `Calendar`. */
    fun hourOnOffset(nowMillis: Long, offsetMillis: Int): Float {
        val minuteOfDay = Math.floorMod(Math.floorDiv(nowMillis + offsetMillis, MILLIS_PER_MINUTE), MINUTES_PER_DAY).toInt()
        return minuteOfDay / 60 + (minuteOfDay % 60) / 60f
    }

    /** Whether [held]'s times are from a position and were worked out for another day or offset than [nowMillis]'s in [zone]. */
    fun workedOutForAnotherDay(held: HeldSunTimes, nowMillis: Long, zone: TimeZone): Boolean {
        val at = held.workedOutFor ?: return false
        return at.stamp != stamp(nowMillis, zone)
    }

    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MINUTES_PER_DAY = 1_440L

    /**
     * How long the loop waits before its next pass: its usual [checkIntervalMillis], or less
     * when the phone is this engine's source and a try after a request that found nothing falls due
     * sooner ([retryPassDelayMillis], `LocationRequestThrottle.passDelay`) -- so the try is made 5
     * minutes after, as decision 41 sets, and not at the first two-minute pass after that.
     */
    fun nextPassDelayMillis(checkIntervalMillis: Long, deviceSource: Boolean, retryPassDelayMillis: Long): Long =
        if (deviceSource) minOf(checkIntervalMillis, retryPassDelayMillis) else checkIntervalMillis

    /**
     * Whether a settings change is the user choosing GPS or Network -- the choice whose first request
     * passes the wait (`LocationRequestThrottle`, `force`).
     *
     * **Not the engine's first settings** ([appliedBefore] false): an engine starts with no source
     * held, so its first settings always "change" it, and counted as a choice every engine start --
     * every rebind, and every picker preview -- would make a forced request, the case the hour exists
     * to stop. Nor a change to Custom or Off, which ask the phone nothing.
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
     * **`DESIGN_NOTES.md` §12, decision 41** (2026-09-30, *«6 - voglio B»*). At a fixed hour the
     * settings screen shows the Location row grey, "Available while the scene follows real time", so
     * the sky does not follow it: drawn from the place saved in Location, 19:00 at Milan in October
     * would be a sunset, and the same 19:00 with no location daylight. The gallery cards draw their
     * hours on the same default day.
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
        return stampOf(calendar.get(Calendar.YEAR), calendar.get(Calendar.DAY_OF_YEAR), zone.getOffset(nowMillis))
    }

    private fun stampOf(year: Int, dayOfYear: Int, offsetMillis: Int): Long =
        (year * 1000L + dayOfYear) * 10_000L + offsetMillis / 60_000L
}

/** A civil day and UTC offset, and their [SolarDaySchedule.stamp]: what a sunrise and sunset are worked out for. */
internal class CivilDay(val dayOfYear: Int, val offsetMillis: Int, val stamp: Long)

/**
 * The sunrise and sunset the wallpaper holds ([day]) with the civil day and offset they were worked out for
 * ([workedOutFor], null with no position): **one object**, for the reason [SolarDay] is one -- the frame reads the
 * times and the offset its hour is read on ([SolarDaySchedule.sceneHour]) in one read, so a working-out published
 * between two reads cannot pair the new times with the old offset for a frame.
 */
internal class HeldSunTimes(val day: SolarDay, val workedOutFor: CivilDay?) {
    companion object {
        /** No position: the default 6:00 and 20:00, and the clock's own hour. */
        val NONE = HeldSunTimes(SolarDay.NONE, null)
    }
}
