package com.paperscrape.livewallpaper.update

/**
 * When the update check runs with the screen off: the rules of the job the phone's own scheduler runs
 * ([UpdateCheckJobService]), with no Android in them, so the JVM holds them (`UpdateCheckCadenceTest`).
 *
 * ### Why a job of the phone's scheduler (v5.15)
 *
 * The maintainer, 2026-10-18: *«check update app anche a schermo spento»*; shown the three ways it could
 * be done, *«S sì»* (2026-10-19, the table of the v5.15A round), the phone's job scheduler. Until v5.15 the
 * check rode the wallpaper engine's own loop (his decision of 2026-09-23, kept on 2026-09-30 every three
 * hours), which parks while the wallpaper is not on screen: with the screen off it never ran, with
 * another wallpaper set never at all, and a check that failed was tried again only while the screen
 * stayed on -- a check that failed at 13:22 on 2026-10-17 (GitHub's limit or the phone's network: the
 * v5.15A round could not tell which) left the notice for that evening. `JobScheduler` is part
 * of Android since API 21 (this app starts at 26), so nothing is added to the build.
 *
 * - **One periodic job, every [PERIOD_MILLIS]**, with a network (`JobInfo.NETWORK_TYPE_ANY`) and
 *   persisted across a restart of the phone (`RECEIVE_BOOT_COMPLETED` in the manifest is what that
 *   needs), **due in the last [FLEX_MILLIS] of each period** -- so its first run is due a period after it
 *   is scheduled, not at once: `setPeriodic(interval)` alone is a window of the whole period, which opens
 *   the moment the job is scheduled, and a caller that checks by itself and puts the job back asked GitHub
 *   twice in one instant (inventory I-956, proved on the BV6600 by v5.15C). The phone chooses when: with
 *   the screen off for long, in Doze's maintenance windows, and later still for an app it judges rarely
 *   used (on Android 10 up to 8 hours in the "frequent" bucket, 24 in "rare"); and a run made more than
 *   half an hour after its window counts for the next window too (Android's rule for a window shorter
 *   than the period, `JobSchedulerService.getRescheduleJobForPeriodic`), so with the phone asleep for
 *   long the checks come further apart. Scheduled while both update switches are on, cancelled when
 *   either goes off ([reconcile]).
 * - **A check that found nothing out** -- no connection, GitHub not answering, a reply not readable --
 *   is tried again on the scheduler's own back-off, from [RETRY_BACKOFF_MILLIS] doubling (Android caps
 *   it at five hours), with a network. The engine's loop doubled from two minutes too, up to three hours,
 *   and a screen turned off stopped it.
 * - **GitHub's limit**: a check turned away by it ([UpdateCheckResult.Unreachable.Reason.RATE_LIMITED])
 *   is not tried again on the back-off -- every try would be refused too, and GitHub's documentation warns
 *   that an address that keeps asking may be blocked -- but **once**, [AFTER_LIMIT_MARGIN_MILLIS] after
 *   the moment GitHub gave ([afterLimitDelayMillis]); and no check nobody asked for is made before it
 *   ([UpdateChecker.checkForUpdate]'s `honourLimit`).
 * - **One run at a time** (inventory I-945): the periodic job and the one run can be started together
 *   (two did, on 2026-10-19 at 13:21:38, in the v5.15A prototype); the second finds the first still
 *   checking and ends at once. And one request for one moment, whoever asks ([UpdateChecker.checkForUpdate]):
 *   a run that finds the screen's check on the network, or answered less than a minute ago, takes its answer.
 * - **Nothing asked that could not be shown** (inventory I-970): a run asks GitHub only while the phone would
 *   show the notification -- the permission held on Android 13+, PaperScrape's notifications and the channel
 *   on in the phone's settings ([beforeCheck]); the settings screen's check (while its switch is on) and the
 *   button still ask, as before.
 * - **The switches read, not guessed** (inventory I-968): a read of the store that fails for a passing reason
 *   leaves the job as it is ([Reconcile.LEAVE]) and a run tries again on the back-off; until v5.15C2 it read
 *   both switches off, as a fresh install's, and cancelled the job.
 * - **A force stop cancels the job**, the phone's own or the user's (`am force-stop`, "Force stop", and
 *   on the BV6600 its firmware at every restart of the phone and after half an hour of an idle app): it
 *   comes back when PaperScrape starts again -- the wallpaper's service or the settings screen
 *   ([reconcile] finds it missing) -- and then a check runs at once, as a fresh engine's did: the one run of
 *   the job for the wallpaper's service ([runSoon]), the screen's own for the settings.
 */
object UpdateCheckSchedule {

    /** The periodic job's id. Stable: a job is known to the scheduler by its id, across updates of the app. */
    const val PERIODIC_JOB_ID = 1515

    /** The one-off run's id: the check at once, or the one after GitHub's limit -- one replaces the other. */
    const val ONE_RUN_JOB_ID = 1516

    /** Three hours: the cadence the maintainer chose on 2026-09-30, kept ([UpdateNotificationPolicy.UPDATE_CHECK_INTERVAL_MILLIS]). */
    const val PERIOD_MILLIS = UpdateNotificationPolicy.UPDATE_CHECK_INTERVAL_MILLIS

    /**
     * The window at the end of each period in which the periodic job is due (`setPeriodic(PERIOD_MILLIS,
     * FLEX_MILLIS)`): a quarter of an hour, so the job is first due a period after it is scheduled, less that.
     * Android raises a window under 5 % of the period or five minutes; this is above both.
     */
    const val FLEX_MILLIS = 15 * 60 * 1000L

    /** The first retry of a check that found nothing out: two minutes, doubled each time by the scheduler. */
    const val RETRY_BACKOFF_MILLIS = 2 * 60 * 1000L

    /** How long after the moment GitHub gave the one run after its limit is made. */
    const val AFTER_LIMIT_MARGIN_MILLIS = 30 * 1000L

    /** How long to wait when GitHub refused with its limit and gave no moment: its window is an hour. */
    const val UNKNOWN_LIMIT_WAIT_MILLIS = 60 * 60 * 1000L

    /** Whether the job should exist: the notification reports on the automatic check, so it takes both switches. */
    fun wanted(automaticCheckEnabled: Boolean, notificationsEnabled: Boolean): Boolean =
        automaticCheckEnabled && notificationsEnabled

    /** What [reconcile] does to the periodic job. */
    enum class Reconcile {
        /** The switches could not be read (a passing read error): the job is left as it is, and nothing runs at once. */
        LEAVE,

        /** Wanted and not pending (the first time, or after a force stop took it away), or pending with another period or window: schedule it. */
        SCHEDULE,

        /** Pending with the right period and window: leave it, so its next run keeps its place. */
        KEEP,

        /** Pending and not wanted any more: cancel it, and the one run with it. */
        CANCEL,

        /** Not pending and not wanted. */
        NOTHING,
    }

    /**
     * What to do with the periodic job, from whether it is [wanted] (null: the switches could not be read)
     * and the period and window of the one pending, if any ([pendingPeriodMillis], null for none). A pending
     * job with another period or another window -- a job scheduled with the whole period as its window, as
     * until v5.15C2 -- is scheduled again, which replaces it.
     */
    fun reconcile(wanted: Boolean?, pendingPeriodMillis: Long?, pendingFlexMillis: Long?): Reconcile = when {
        wanted == null -> Reconcile.LEAVE
        !wanted -> if (pendingPeriodMillis != null) Reconcile.CANCEL else Reconcile.NOTHING
        pendingPeriodMillis == PERIOD_MILLIS && pendingFlexMillis == FLEX_MILLIS -> Reconcile.KEEP
        else -> Reconcile.SCHEDULE
    }

    /**
     * Whether the job runs once at once, besides its periodic runs (whose first is a period away): when the
     * user has just turned *Notify me* on ([userTurnedOn], D of v5.15: the switches turned on make a check at
     * once), or when the job had to be scheduled again ([Reconcile.SCHEDULE]: a first start, or a force stop
     * that took it away), as a fresh engine's loop checked at once -- unless the caller makes that check
     * itself ([checkAtOnce] false: the settings screen, which checks when it opens and when *Check for
     * updates when I open PaperScrape* comes on), so GitHub is not asked twice in a moment.
     */
    fun runSoon(wanted: Boolean?, action: Reconcile, userTurnedOn: Boolean, checkAtOnce: Boolean = true): Boolean =
        wanted == true && action != Reconcile.LEAVE && (userTurnedOn || (checkAtOnce && action == Reconcile.SCHEDULE))

    /** What a run of the job does before it asks GitHub ([beforeCheck]). */
    enum class Before {
        /** Ask GitHub. */
        ASK,

        /** The switches could not be read: no request, the job left as it is, again on the back-off ([Next.RETRY]). */
        UNREAD,

        /** A switch is off: no request, and the job is reconciled away. */
        NOT_WANTED,

        /**
         * The phone would not show the notification -- the permission refused on Android 13+, or PaperScrape's
         * notifications or the channel turned off in the phone's settings: no request, the next period.
         */
        CANNOT_SHOW,
    }

    /**
     * Whether a run asks GitHub: [wanted] as the switches were read (null: they could not be), [mayPost] the
     * permission's answer ([UpdateNotificationPolicy.mayPost]), [blockedByPhone] the phone's settings'
     * ([UpdateNotifier.PhoneState.blocks] with the switch on). A check whose result could not be shown to
     * anybody is a request made for nothing (inventory I-970; until v5.15C2 only the permission was read here,
     * and the channel or the app's notifications turned off were found only when posting).
     */
    fun beforeCheck(wanted: Boolean?, mayPost: Boolean, blockedByPhone: Boolean): Before = when {
        wanted == null -> Before.UNREAD
        !wanted -> Before.NOT_WANTED
        !mayPost || blockedByPhone -> Before.CANNOT_SHOW
        else -> Before.ASK
    }

    /** What a finished check asks of the scheduler. */
    enum class Next {
        /** Answered, or nothing to ask: the next period. */
        DONE,

        /** Nothing found out: again on the back-off, with a network. */
        RETRY,

        /** Turned away by GitHub's limit: one run after it ([afterLimitDelayMillis]), no back-off. */
        AFTER_LIMIT,
    }

    /** The decision for [result]; null is "no check was made" ([Next.DONE]). */
    fun afterCheck(result: UpdateCheckResult?): Next = when {
        result is UpdateCheckResult.Unreachable && result.reason == UpdateCheckResult.Unreachable.Reason.RATE_LIMITED -> Next.AFTER_LIMIT
        result is UpdateCheckResult.Unreachable -> Next.RETRY
        else -> Next.DONE
    }

    /**
     * How long from [nowMillis] the one run after GitHub's limit waits: until [limitedUntilMillis] and
     * [AFTER_LIMIT_MARGIN_MILLIS] more, or [UNKNOWN_LIMIT_WAIT_MILLIS] when GitHub gave no moment (0).
     */
    fun afterLimitDelayMillis(limitedUntilMillis: Long, nowMillis: Long): Long =
        if (limitedUntilMillis <= 0L) UNKNOWN_LIMIT_WAIT_MILLIS
        else (limitedUntilMillis - nowMillis).coerceAtLeast(0L) + AFTER_LIMIT_MARGIN_MILLIS
}
