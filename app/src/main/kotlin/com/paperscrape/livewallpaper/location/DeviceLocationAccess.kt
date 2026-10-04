package com.paperscrape.livewallpaper.location

import android.Manifest

/**
 * What the phone lets PaperScrape do with its position, for one [DeviceLocationKind] -- read from the
 * phone, never from the preferences (v5.10D, the maintainer's decision of 2026-09-30 on row 5 of the
 * v5.10A table, inventory I-206).
 *
 * Until v5.10D the GPS and Network choices came from the preferences alone: a permission taken away in
 * the phone's settings, the phone's location switched off, or an app backup restored on a new phone left
 * "GPS" chosen and working-looking, and the wallpaper went on using the last position it had saved --
 * for the sunrise, and for every weather request it sent -- for good. Now the settings screen reads this
 * every time the Weather & time page comes to the front, and the wallpaper on every pass of its loop.
 *
 * Pure, so every combination is asserted on the JVM (`DeviceLocationAccessTest`): the project's phone is
 * Android 10, and taking a permission away from it is a change to the phone's settings that the rounds
 * do not make.
 */
enum class DeviceLocationAccess {

    /** The permission the kind needs is held and the phone's location is on. */
    ALLOWED,

    /** Neither location permission is held: refused, taken away, or never asked on this phone. */
    NOT_ALLOWED,

    /**
     * GPS chosen, and only the approximate location allowed. From Android 12 the permission dialog
     * offers "Approximate" beside "Precise", and the phone's settings can take the precise one away;
     * the GPS receiver needs the precise one. (Network needs only the approximate one, so it is never
     * in this state.)
     */
    APPROXIMATE_ONLY,

    /** The permission is held, and the phone's own location switch is off. */
    LOCATION_OFF,
    ;

    /**
     * Whether the wallpaper may use a position from the phone at all -- a fresh one, or the last one it
     * saved.
     *
     * **Not while the permission is missing**: a user who took it away said no to PaperScrape knowing
     * where the phone is, and until v5.10D the last saved position went on being sent to the weather
     * service every hour. **Yes while the location switch is off**: that is the phone not giving
     * positions right now -- to any app, often to save battery -- the same as no signal, and the last
     * one it gave is still the town the phone is in. The settings screen says which
     * (`SettingsUiModel.deviceLocationRow`).
     */
    val mayUsePosition: Boolean
        get() = this == ALLOWED || this == LOCATION_OFF

    companion object {

        /**
         * What the phone allows for [kind]. [fineGranted] and [coarseGranted] are the two location
         * permissions as the phone holds them; [locationOn] is the phone's location switch
         * (`LocationManagerCompat.isLocationEnabled`). The permission is said first, because it is put
         * right on the settings screen itself (the dialog), and the location switch only in the phone's
         * settings.
         */
        fun of(
            kind: DeviceLocationKind,
            fineGranted: Boolean,
            coarseGranted: Boolean,
            locationOn: Boolean,
        ): DeviceLocationAccess {
            val permitted = when (kind) {
                DeviceLocationKind.GPS -> fineGranted
                DeviceLocationKind.NETWORK -> coarseGranted || fineGranted
            }
            return when {
                permitted -> if (locationOn) ALLOWED else LOCATION_OFF
                kind == DeviceLocationKind.GPS && coarseGranted -> APPROXIMATE_ONLY
                else -> NOT_ALLOWED
            }
        }

        /**
         * The permissions a choice of [kind] asks for, in one request.
         *
         * **GPS asks for the precise and the approximate location together.** From Android 12 an app
         * that targets 31 or later and asks for `ACCESS_FINE_LOCATION` alone has the request ignored --
         * the system logs that the precise location must be requested with the approximate one, and
         * grants neither. PaperScrape targets 37 and asked for the precise one alone until v5.10D
         * ([DeviceLocationKind.GPS]'s permission), so on Android 12 and later choosing GPS could not
         * succeed. Below Android 12 asking for both shows the same single dialog. Network asks for the
         * approximate one only, as it always has.
         */
        fun permissionsToRequest(kind: DeviceLocationKind): List<String> = when (kind) {
            DeviceLocationKind.GPS ->
                listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            DeviceLocationKind.NETWORK -> listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
}

/**
 * When the wallpaper may make a real request for the phone's position -- the radio, up to
 * [DeviceLocationProvider.REQUEST_TIMEOUT_MS] of search -- whoever asks.
 *
 * **One an hour while the phone answers** (v5.10D, inventory I-207). "Checked at most once an hour"
 * was the line under GPS and Network, and until v5.10D it was not true: the weather loop asked the
 * phone on every weather attempt -- every 2, 4, 8 minutes during a weather outage, and **every two
 * minutes, with up to 20 s of search each time, while no position had ever arrived** -- because a
 * request followed whenever the system's cached position was older than fifteen minutes.
 *
 * **And after a search that finds nothing, again after 5, 15 and 30 minutes** (v5.10E). The
 * maintainer, 2026-10-04, told that a failed request then waited a whole hour: *«voglio che in ogni
 * caso, se la richiesta posizione fallisce, riprovi dopo 5, 15 e 30 minuti - anche se ha già una
 * posizione»*, and *«non deve più sfociare nel chiedere ogni 20 secondi ma nemmeno può rimanere in
 * immobilità per 60 minuti sempre»*. So a real request that ends **without a position** -- the 20 s
 * search run out, the provider answering nothing -- is tried again 5 minutes after it was made; if
 * that one finds nothing too, 15 minutes after it; then 30; then the hour
 * ([RETRY_LADDER_MILLIS]). The hour's request failing is a new failure, and starts the 5, 15, 30
 * again: with a GPS that never answers, the requests go out at 0, 5, 20, 50, 110, 115, 130, 160 ...
 * minutes (`LocationRetryLadderTest` counts them on a clock it moves). A request that brings a
 * position, or a fresh position the system already has ([positionArrived]), puts it back on the hour
 * at once.
 *
 * **Whether a position is held does not matter**: the last one, or the saved one, stands in while the
 * retries go on, and the wallpaper's loop asks for a retry that is due whatever else it holds
 * (`SolarDaySchedule.onTick`, [retryDue]). **Nor is anything here a failure that is not a request**:
 * without the permission, or with the phone's location off, the phone is not asked at all
 * ([DeviceLocationProvider.currentFix] stops before this), the settings screen says what to do, and
 * no try is counted.
 *
 * One for the whole process ([shared]), because the two engines a wallpaper process can run -- the home
 * screen's and the picker's preview -- and a wallpaper service recreated by a rebind share one radio.
 * A new process (a phone restart, an app update) may ask at once: a wallpaper that has just started
 * needs a position, and the times are counted on [now], the monotonic clock, from then.
 *
 * [tryAcquire]'s `force` is a choice the user has just made -- GPS or Network picked on the settings
 * screen -- which is answered at once, whatever the wait says, and starts the tries afresh.
 */
class LocationRequestThrottle(
    private val now: () -> Long,
    val minIntervalMillis: Long = MIN_INTERVAL_MILLIS,
) {
    private var lastRequestAt = Long.MIN_VALUE / 4

    /** Real requests in a row that ended without a position, since the last one that brought one. */
    private var failures = 0

    /** How long after [lastRequestAt] the next real request may be made. */
    private fun waitAfterLast(): Long =
        if (failures == 0) minIntervalMillis else RETRY_LADDER_MILLIS[(failures - 1) % RETRY_LADDER_MILLIS.size]

    /** Whether a real request may be made now; if so, it is counted from now. */
    @Synchronized
    fun tryAcquire(force: Boolean = false): Boolean {
        val t = now()
        val elapsed = t - lastRequestAt
        if (!force && elapsed in 0 until waitAfterLast()) return false
        // A choice the user has just made is a new start: if it finds nothing, the next try is the
        // first of the ladder, not whichever rung the old source had reached.
        if (force) failures = 0
        lastRequestAt = t
        return true
    }

    /** The real request [tryAcquire] let through has ended, with a position or without one. */
    @Synchronized
    fun requestEnded(gotPosition: Boolean) {
        failures = if (gotPosition) 0 else failures + 1
    }

    /**
     * Makes the real request [tryAcquire] let through and tells this throttle how it ended ([requestEnded])
     * -- **also when it is cancelled part-way**, an engine destroyed during the 20 s search: that is a
     * request made that brought no position, a failure, so the next try is 5 minutes away and not an
     * hour (the read-only review of v5.10E, D3).
     */
    suspend fun <T : Any> counted(request: suspend () -> T?): T? {
        var got: T? = null
        try {
            got = request()
            return got
        } finally {
            requestEnded(gotPosition = got != null)
        }
    }

    /**
     * A position fresh enough to stand for a request arrived without one -- the system's own, which
     * another app or an earlier request left younger than [DeviceLocationProvider.FRESH_ENOUGH_MS].
     * That is what the tries were for, so they stop; the hour still counts from the last real request.
     */
    @Synchronized
    fun positionArrived() {
        failures = 0
    }

    /** Whether the last real request found nothing and the time for trying again has come. */
    @Synchronized
    fun retryDue(): Boolean {
        if (failures == 0) return false
        val elapsed = now() - lastRequestAt
        return elapsed < 0 || elapsed >= waitAfterLast()
    }

    /**
     * How long the wallpaper's loop may wait before its next pass, given its usual [checkIntervalMillis]:
     * shorter only when a try falls due before then, so it is made on time -- 5 minutes after, not at
     * the next two-minute pass after that. Never zero: a try that is due and not taken (the location
     * switched off, a fixed hour) leaves the loop on its usual interval, not spinning.
     */
    @Synchronized
    fun passDelay(checkIntervalMillis: Long): Long {
        if (failures == 0) return checkIntervalMillis
        val untilRetry = waitAfterLast() - (now() - lastRequestAt)
        return if (untilRetry in 1 until checkIntervalMillis) untilRetry else checkIntervalMillis
    }

    companion object {
        /** One hour: the weather's own refresh, and the wait after a request that brought a position. */
        const val MIN_INTERVAL_MILLIS = 60 * 60 * 1000L

        /**
         * The waits after the first, second, third and fourth failed request in a row: 5, 15 and 30
         * minutes, then the hour (the maintainer's numbers, 2026-10-04). A fifth failure is the hour's
         * try failing, and waits 5 minutes again: the ladder goes round.
         */
        val RETRY_LADDER_MILLIS = longArrayOf(5 * 60 * 1000L, 15 * 60 * 1000L, 30 * 60 * 1000L, MIN_INTERVAL_MILLIS)

        /** The process's one throttle, on `SystemClock.elapsedRealtime`. */
        val shared = LocationRequestThrottle({ android.os.SystemClock.elapsedRealtime() })
    }
}
