package com.paperscrape.livewallpaper.update

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.paperscrape.livewallpaper.BuildConfig
import com.paperscrape.livewallpaper.prefs.WallpaperPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * The update check the phone's own job scheduler runs, every three hours, with the screen off and
 * whatever the wallpaper (v5.15, the maintainer's *«S sì»* of 2026-10-19). The rules are
 * [UpdateCheckSchedule]'s; this is the Android half: the job itself, and [UpdateCheckJobs], which
 * schedules and cancels it.
 *
 * A run reads the two update switches and whether the phone would show the notification, asks GitHub
 * ([UpdateChecker.checkForUpdate], with the reply kept from the last check, so an unchanged list costs
 * no download) and posts the one notification a release gets ([UpdateNotifier.post]) -- what the
 * wallpaper engine's loop did until v5.15, and nothing more.
 */
class UpdateCheckJobService : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running = scope.launch {
            // A run the phone took back ([onStopJob]) ends here, with no `jobFinished`: the phone has it already.
            val next = try {
                UpdateCheckJobs.runOnce(applicationContext)
            } catch (stopped: CancellationException) {
                throw stopped
            } catch (_: Exception) {
                UpdateCheckSchedule.Next.RETRY
            }
            jobFinished(params, next == UpdateCheckSchedule.Next.RETRY)
        }
        return true
    }

    /** The phone takes the job back (the network went, or its window closed): try again on the back-off. */
    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return true
    }
}

/** Schedules, cancels and runs [UpdateCheckJobService]'s job; the decisions are [UpdateCheckSchedule]'s. */
object UpdateCheckJobs {

    /** One check at a time in this process (inventory I-945): a second run ends at once. */
    private val checking = Mutex()

    /**
     * Puts the job where the two switches say -- scheduled, kept, or cancelled -- and starts one check at
     * once where [UpdateCheckSchedule.runSoon] says so. Called by the wallpaper's service when it starts,
     * by the settings screen when it opens and whenever the switches change ([userTurnedOn] when the user
     * has just turned *Notify me* on; [checkAtOnce] false where the screen checks by itself). Reads the
     * switches from the store, never from a screen's state: the screen's first value is a fresh install's,
     * with both off; and a read that fails leaves the job as it is. Safe to call as often as one likes.
     *
     * It also makes the notification channel ([UpdateNotifier.ensureChannel]): an update from v5.14 restarts
     * the wallpaper, and the channel of before goes -- its "off" carried over -- with nothing opened.
     */
    fun reconcile(context: Context, userTurnedOn: Boolean = false, checkAtOnce: Boolean = true) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch { runCatching { reconcileNow(app, userTurnedOn, checkAtOnce) } }
    }

    internal suspend fun reconcileNow(context: Context, userTurnedOn: Boolean, checkAtOnce: Boolean = true): UpdateCheckSchedule.Reconcile {
        UpdateNotifier.ensureChannel(context)
        // Null for a read that failed for a passing reason (inventory I-968): the job is left as it is.
        val switches = WallpaperPrefs(context).readUpdateSwitches()
        val wanted = switches?.let { UpdateCheckSchedule.wanted(it.automaticCheck, it.notifications) }
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return UpdateCheckSchedule.Reconcile.NOTHING
        val pending = scheduler.getPendingJob(UpdateCheckSchedule.PERIODIC_JOB_ID)
        val action = UpdateCheckSchedule.reconcile(wanted, pending?.intervalMillis, pending?.flexMillis)
        when (action) {
            // Due in the last quarter hour of each period, so first a period from now: never at once (I-956).
            UpdateCheckSchedule.Reconcile.SCHEDULE -> scheduler.schedule(
                JobInfo.Builder(UpdateCheckSchedule.PERIODIC_JOB_ID, ComponentName(context, UpdateCheckJobService::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .setPeriodic(UpdateCheckSchedule.PERIOD_MILLIS, UpdateCheckSchedule.FLEX_MILLIS)
                    .setBackoffCriteria(UpdateCheckSchedule.RETRY_BACKOFF_MILLIS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                    .build(),
            )
            UpdateCheckSchedule.Reconcile.CANCEL -> {
                scheduler.cancel(UpdateCheckSchedule.PERIODIC_JOB_ID)
                scheduler.cancel(UpdateCheckSchedule.ONE_RUN_JOB_ID)
            }
            UpdateCheckSchedule.Reconcile.KEEP, UpdateCheckSchedule.Reconcile.NOTHING, UpdateCheckSchedule.Reconcile.LEAVE -> Unit
        }
        if (UpdateCheckSchedule.runSoon(wanted, action, userTurnedOn, checkAtOnce)) scheduleOneRun(context, delayMillis = 0L)
        return action
    }

    /**
     * One run of the job, with a network, [delayMillis] from now: at once, or after GitHub's limit. It
     * replaces the one run pending, if any: a check at once made before the limit lifts finds the limit
     * and schedules this again.
     */
    private fun scheduleOneRun(context: Context, delayMillis: Long) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        scheduler.schedule(
            JobInfo.Builder(UpdateCheckSchedule.ONE_RUN_JOB_ID, ComponentName(context, UpdateCheckJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(delayMillis)
                .setBackoffCriteria(UpdateCheckSchedule.RETRY_BACKOFF_MILLIS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build(),
        )
    }

    /**
     * One check, as the wallpaper engine's loop made it until v5.15: what [UpdateCheckSchedule.afterCheck]
     * asks of the scheduler next. Nothing here throws but the store's write, which is caught.
     *
     * - **the two switches**, read from the store at every run, so turning either off stops the next one
     *   (and the job is cancelled with it); a read that fails leaves the job and tries again on the back-off;
     * - **whether the phone would show the notification** ([UpdateCheckSchedule.beforeCheck]), before the
     *   network call: the permission, PaperScrape's notifications and the channel ([UpdateNotifier.phoneState]).
     *   A check whose result could not be shown to anybody is a request made for nothing;
     * - **the snooze** and **one notification per version** ([UpdateNotificationPolicy.shouldPost]).
     *
     * The check takes the answer of one the settings screen or the button has on the network, or made less
     * than a minute ago (`takeRecentAnswer`): one request for one moment.
     */
    internal suspend fun runOnce(context: Context): UpdateCheckSchedule.Next {
        if (!checking.tryLock()) return UpdateCheckSchedule.Next.DONE
        try {
            val switches = WallpaperPrefs(context).readUpdateSwitches()
            val phone = UpdateNotifier.phoneState(context)
            val before = UpdateCheckSchedule.beforeCheck(
                wanted = switches?.let { UpdateCheckSchedule.wanted(it.automaticCheck, it.notifications) },
                mayPost = UpdateNotificationPolicy.mayPost(phone.permission),
                blockedByPhone = phone.blocks(notifySwitchOn = true),
            )
            when (before) {
                UpdateCheckSchedule.Before.UNREAD -> return UpdateCheckSchedule.Next.RETRY
                UpdateCheckSchedule.Before.NOT_WANTED -> {
                    reconcileNow(context, userTurnedOn = false)
                    return UpdateCheckSchedule.Next.DONE
                }
                UpdateCheckSchedule.Before.CANNOT_SHOW -> return UpdateCheckSchedule.Next.DONE
                UpdateCheckSchedule.Before.ASK -> Unit
            }
            val updatePrefs = UpdatePrefs(context)
            val result = UpdateChecker.checkForUpdate(BuildConfig.VERSION_NAME, updatePrefs, takeRecentAnswer = true)
            val next = UpdateCheckSchedule.afterCheck(result)
            if (next == UpdateCheckSchedule.Next.AFTER_LIMIT) {
                val until = (result as UpdateCheckResult.Unreachable).limitedUntilMillis
                scheduleOneRun(context, UpdateCheckSchedule.afterLimitDelayMillis(until, System.currentTimeMillis()))
            }
            val info = (result as? UpdateCheckResult.Available)?.info ?: return next

            // **The snooze is honoured, not routed around.** "Remind me later -> In a month" is keyed to
            // the version tag, so a month on v5.6 does not hide v5.7 -- and a notification that ignored it
            // would be the app answering a question the user has already answered.
            val snooze = updatePrefs.readSnoozeState()
            val post = UpdateNotificationPolicy.shouldPost(
                notificationsEnabled = switches?.notifications == true,
                permission = UpdateNotifier.notificationPermission(context),
                tagName = info.tagName,
                alreadyNotifiedTag = updatePrefs.readNotifiedTag(),
                snoozedTag = snooze.versionTag,
                snoozeUntilMillis = snooze.untilMillis,
                nowMillis = System.currentTimeMillis(),
            )
            // Recorded only if it actually went out: a tag written for a notification the platform
            // dropped would silence that release for the one user who never saw it. A failed write
            // costs at most a second notification for the same release on a later run.
            if (post && UpdateNotifier.post(context, info.tagName, BuildConfig.VERSION_NAME)) {
                runCatching { updatePrefs.setNotifiedTag(info.tagName) }
            }
            return UpdateCheckSchedule.Next.DONE
        } finally {
            checking.unlock()
        }
    }
}
