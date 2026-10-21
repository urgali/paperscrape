package com.paperscrape.livewallpaper.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **The update check every three hours, with the screen off and whatever the wallpaper** (v5.15, the maintainer's
 * *«S sì»* of 2026-10-19; every three hours since his decision of 2026-09-30, *«3 - voglio B»*): a job of the phone's
 * own scheduler ([UpdateCheckJobService]), with the rules in [UpdateCheckSchedule]. Until v5.15 the check rode the
 * wallpaper engine's loop, which parks while the wallpaper is not on screen.
 *
 * What the phone does with the job inside its period is the phone's; what is the app's is held here: the period and its
 * window, the network, the job kept across a restart, the back-off of a check that found nothing out, the one run after
 * GitHub's limit, when the job is scheduled, kept, left and cancelled, the check at once, what a run reads before it asks
 * -- and, on the sources, that the job is built and run with them, that each fact asks GitHub once (inventory I-956,
 * I-967) and that the engine's loop no longer checks.
 */
class UpdateCheckCadenceTest {

    private val minute = 60 * 1000L
    private val hour = 60 * minute

    @Test
    fun `every three hours, the cadence of 2026-09-30`() {
        assertEquals(3 * hour, UpdateCheckSchedule.PERIOD_MILLIS)
        assertEquals(UpdateNotificationPolicy.UPDATE_CHECK_INTERVAL_MILLIS, UpdateCheckSchedule.PERIOD_MILLIS)
    }

    @Test
    fun `the job exists while both switches are on, and only then`() {
        assertTrue(UpdateCheckSchedule.wanted(automaticCheckEnabled = true, notificationsEnabled = true))
        assertFalse(UpdateCheckSchedule.wanted(automaticCheckEnabled = true, notificationsEnabled = false))
        assertFalse(UpdateCheckSchedule.wanted(automaticCheckEnabled = false, notificationsEnabled = true))
        assertFalse(UpdateCheckSchedule.wanted(automaticCheckEnabled = false, notificationsEnabled = false))
    }

    @Test
    fun `due in the last quarter hour of each period, so first a period after it is scheduled`() {
        assertEquals(15 * minute, UpdateCheckSchedule.FLEX_MILLIS)
        // Android raises a window under 5 % of the period or five minutes: this one is kept as it is.
        assertTrue(UpdateCheckSchedule.FLEX_MILLIS >= maxOf(UpdateCheckSchedule.PERIOD_MILLIS * 5 / 100, 5 * minute))
        // `setPeriodic(interval)` alone is a window of the whole period, open the moment the job is scheduled (I-956):
        // the first run is due at the period less the window, hours away.
        assertTrue(UpdateCheckSchedule.PERIOD_MILLIS - UpdateCheckSchedule.FLEX_MILLIS >= 2 * hour)
    }

    @Test
    fun `scheduled when missing, kept when there, left when unread, cancelled when not wanted`() {
        val p = UpdateCheckSchedule.PERIOD_MILLIS
        val f = UpdateCheckSchedule.FLEX_MILLIS
        assertEquals(UpdateCheckSchedule.Reconcile.SCHEDULE, UpdateCheckSchedule.reconcile(wanted = true, pendingPeriodMillis = null, pendingFlexMillis = null))
        assertEquals(UpdateCheckSchedule.Reconcile.KEEP, UpdateCheckSchedule.reconcile(wanted = true, pendingPeriodMillis = p, pendingFlexMillis = f))
        assertEquals("a job with another period is replaced", UpdateCheckSchedule.Reconcile.SCHEDULE, UpdateCheckSchedule.reconcile(true, p / 2, f))
        assertEquals(
            "a job with the whole period as its window (until v5.15C2) is replaced",
            UpdateCheckSchedule.Reconcile.SCHEDULE,
            UpdateCheckSchedule.reconcile(true, p, p),
        )
        assertEquals(UpdateCheckSchedule.Reconcile.CANCEL, UpdateCheckSchedule.reconcile(wanted = false, pendingPeriodMillis = p, pendingFlexMillis = f))
        assertEquals(UpdateCheckSchedule.Reconcile.NOTHING, UpdateCheckSchedule.reconcile(wanted = false, pendingPeriodMillis = null, pendingFlexMillis = null))
        // The switches not read (a passing read error, I-968): the job as it is, pending or not, whatever it is.
        for (period in listOf(null, p, p / 2)) {
            assertEquals("unread, pending $period", UpdateCheckSchedule.Reconcile.LEAVE, UpdateCheckSchedule.reconcile(null, period, f))
        }
    }

    @Test
    fun `a check at once when the switches have just come on, or when a force stop took the job away`() {
        for (action in UpdateCheckSchedule.Reconcile.entries) {
            assertFalse("not wanted: never ($action)", UpdateCheckSchedule.runSoon(wanted = false, action = action, userTurnedOn = true))
            // LEAVE comes of switches not read, never of switches wanted: below.
            if (action == UpdateCheckSchedule.Reconcile.LEAVE) continue
            assertTrue("turned on: always ($action)", UpdateCheckSchedule.runSoon(wanted = true, action = action, userTurnedOn = true))
        }
        assertTrue("put back after a force stop", UpdateCheckSchedule.runSoon(true, UpdateCheckSchedule.Reconcile.SCHEDULE, userTurnedOn = false))
        assertFalse(
            "a job already pending keeps its place: the settings opening or the wallpaper restarting is no run of the job",
            UpdateCheckSchedule.runSoon(true, UpdateCheckSchedule.Reconcile.KEEP, userTurnedOn = false),
        )
        assertFalse(
            "put back by a caller that checks itself (the settings screen opening): GitHub is not asked twice",
            UpdateCheckSchedule.runSoon(true, UpdateCheckSchedule.Reconcile.SCHEDULE, userTurnedOn = false, checkAtOnce = false),
        )
        assertTrue(
            "turned on is a check at once whoever reconciles",
            UpdateCheckSchedule.runSoon(true, UpdateCheckSchedule.Reconcile.SCHEDULE, userTurnedOn = true, checkAtOnce = false),
        )
        for (turnedOn in listOf(true, false)) for (atOnce in listOf(true, false)) {
            assertFalse(
                "the switches unread: no run at once (turnedOn=$turnedOn, checkAtOnce=$atOnce)",
                UpdateCheckSchedule.runSoon(null, UpdateCheckSchedule.Reconcile.LEAVE, turnedOn, atOnce),
            )
            assertFalse(UpdateCheckSchedule.runSoon(true, UpdateCheckSchedule.Reconcile.LEAVE, turnedOn, atOnce))
        }
    }

    @Test
    fun `a run asks GitHub only with the switches read and on, and a notification the phone would show`() {
        val bools = listOf(true, false)
        for (mayPost in bools) for (blocked in bools) {
            assertEquals("unread", UpdateCheckSchedule.Before.UNREAD, UpdateCheckSchedule.beforeCheck(null, mayPost, blocked))
            assertEquals("not wanted", UpdateCheckSchedule.Before.NOT_WANTED, UpdateCheckSchedule.beforeCheck(false, mayPost, blocked))
            val expected = if (mayPost && !blocked) UpdateCheckSchedule.Before.ASK else UpdateCheckSchedule.Before.CANNOT_SHOW
            assertEquals("mayPost=$mayPost blocked=$blocked", expected, UpdateCheckSchedule.beforeCheck(true, mayPost, blocked))
        }
    }

    @Test
    fun `what a finished check asks of the scheduler`() {
        val reasons = UpdateCheckResult.Unreachable.Reason.entries
        for (reason in reasons) {
            val expected = if (reason == UpdateCheckResult.Unreachable.Reason.RATE_LIMITED) UpdateCheckSchedule.Next.AFTER_LIMIT else UpdateCheckSchedule.Next.RETRY
            assertEquals(reason.name, expected, UpdateCheckSchedule.afterCheck(UpdateCheckResult.Unreachable(reason)))
        }
        assertEquals(UpdateCheckSchedule.Next.DONE, UpdateCheckSchedule.afterCheck(UpdateCheckResult.UpToDate))
        assertEquals(UpdateCheckSchedule.Next.DONE, UpdateCheckSchedule.afterCheck(null))
    }

    @Test
    fun `after GitHub's limit, one run just after the moment it gave`() {
        val now = 1_000_000_000L
        assertEquals(10 * minute + UpdateCheckSchedule.AFTER_LIMIT_MARGIN_MILLIS, UpdateCheckSchedule.afterLimitDelayMillis(now + 10 * minute, now))
        assertEquals("a moment already past: at once, with the margin", UpdateCheckSchedule.AFTER_LIMIT_MARGIN_MILLIS, UpdateCheckSchedule.afterLimitDelayMillis(now - minute, now))
        assertEquals("no moment given: GitHub's hour", hour, UpdateCheckSchedule.afterLimitDelayMillis(0L, now))
    }

    @Test
    fun `the back-off starts at two minutes, the engine's old ladder`() {
        assertEquals(2 * minute, UpdateCheckSchedule.RETRY_BACKOFF_MILLIS)
        assertEquals(30 * 1000L, UpdateCheckSchedule.AFTER_LIMIT_MARGIN_MILLIS)
    }

    // ------------------------------------------------------------------ the wiring, on the sources

    @Test
    fun `the job is built with the period and its window, a network, a restart of the phone and the back-off`() {
        val job = source("update/UpdateCheckJobService.kt")
        val periodic = job.substring(job.indexOf("UpdateCheckSchedule.Reconcile.SCHEDULE -> scheduler.schedule(")).substringBefore(".build()")
        assertTrue(periodic.contains("UpdateCheckSchedule.PERIODIC_JOB_ID"))
        assertTrue(periodic.contains(".setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)"))
        assertTrue(periodic.contains(".setPersisted(true)"))
        assertTrue("the window, not the whole period (I-956)", periodic.contains(".setPeriodic(UpdateCheckSchedule.PERIOD_MILLIS, UpdateCheckSchedule.FLEX_MILLIS)"))
        assertTrue(periodic.contains(".setBackoffCriteria(UpdateCheckSchedule.RETRY_BACKOFF_MILLIS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)"))
        assertTrue(
            "the pending job's window is read, so a job of the whole period is replaced",
            job.contains("UpdateCheckSchedule.reconcile(wanted, pending?.intervalMillis, pending?.flexMillis)"),
        )
        val once = job.substring(job.indexOf("private fun scheduleOneRun(")).substringBefore(".build()")
        assertTrue(once.contains("UpdateCheckSchedule.ONE_RUN_JOB_ID"))
        assertTrue(once.contains(".setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)"))
        assertTrue(once.contains(".setMinimumLatency(delayMillis)"))
        // One run at a time (inventory I-945): the second finds the first checking and ends.
        assertTrue(job.contains("if (!checking.tryLock()) return UpdateCheckSchedule.Next.DONE"))
    }

    @Test
    fun `the job uses the rules - the run at once, the cancel, the retry, the run after the limit`() {
        val job = source("update/UpdateCheckJobService.kt")
        // The run at once when the rule says so, and only then.
        assertTrue(job.contains("if (UpdateCheckSchedule.runSoon(wanted, action, userTurnedOn, checkAtOnce)) scheduleOneRun(context, delayMillis = 0L)"))
        // Both ids cancelled: the one run would otherwise check after a switch went off.
        val cancel = job.substring(job.indexOf("UpdateCheckSchedule.Reconcile.CANCEL -> {")).substringBefore("}")
        assertTrue(cancel.contains("scheduler.cancel(UpdateCheckSchedule.PERIODIC_JOB_ID)"))
        assertTrue(cancel.contains("scheduler.cancel(UpdateCheckSchedule.ONE_RUN_JOB_ID)"))
        // The scheduler's back-off only for a check that found nothing out, never for GitHub's limit.
        assertTrue(job.contains("jobFinished(params, next == UpdateCheckSchedule.Next.RETRY)"))
        // After the limit, one run at the moment GitHub gave.
        val limit = job.substring(job.indexOf("if (next == UpdateCheckSchedule.Next.AFTER_LIMIT) {")).substringBefore("}")
        assertTrue(limit.contains("scheduleOneRun(context, UpdateCheckSchedule.afterLimitDelayMillis(until, System.currentTimeMillis()))"))
    }

    @Test
    fun `a run reads the switches and the phone before it asks, and takes an answer of the same moment`() {
        val job = source("update/UpdateCheckJobService.kt")
        val run = job.substring(job.indexOf("internal suspend fun runOnce(")).substringBefore("UpdateChecker.checkForUpdate(")
        assertTrue(run.contains("val phone = UpdateNotifier.phoneState(context)"))
        assertTrue(run.contains("wanted = switches?.let { UpdateCheckSchedule.wanted(it.automaticCheck, it.notifications) },"))
        assertTrue(run.contains("mayPost = UpdateNotificationPolicy.mayPost(phone.permission),"))
        assertTrue("the channel and the app's notifications (I-970)", run.contains("blockedByPhone = phone.blocks(notifySwitchOn = true),"))
        assertTrue("unread: again on the back-off (I-968)", run.contains("UpdateCheckSchedule.Before.UNREAD -> return UpdateCheckSchedule.Next.RETRY"))
        assertTrue(run.contains("UpdateCheckSchedule.Before.CANNOT_SHOW -> return UpdateCheckSchedule.Next.DONE"))
        assertTrue(job.contains("UpdateChecker.checkForUpdate(BuildConfig.VERSION_NAME, updatePrefs, takeRecentAnswer = true)"))
        // The switches read as they are: a read error is null, never a fresh install's both off (I-968).
        val prefs = source("prefs/WallpaperPrefs.kt")
        val read = prefs.substring(prefs.indexOf("suspend fun readUpdateSwitches(): UpdateSwitches? {")).substringBefore("\n    }\n")
        assertFalse(read.contains("recoveringFromReadErrors"))
        assertTrue(read.contains("} catch (_: IOException) {\n            return null"))
    }

    @Test
    fun `the manifest declares the job and what keeps it across a restart`() {
        val manifest = repoFile("app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("<uses-permission android:name=\"android.permission.RECEIVE_BOOT_COMPLETED\" />"))
        val service = manifest.substring(manifest.indexOf("android:name=\".update.UpdateCheckJobService\"")).substringBefore("/>")
        assertTrue(service.contains("android:exported=\"false\""))
        assertTrue(service.contains("android:permission=\"android.permission.BIND_JOB_SERVICE\""))
    }

    @Test
    fun `the engine's loop no longer checks, and the service puts the job back when it starts`() {
        val engine = source("engine/PaperWallpaperService.kt")
        assertFalse("the engine still checks", engine.contains("UpdateChecker.checkForUpdate"))
        assertFalse(engine.contains("maybeCheckForUpdate"))
        val onCreate = engine.substring(engine.indexOf("override fun onCreate() {")).substringBefore("override fun onDestroy()")
        assertTrue(onCreate.contains("UpdateCheckJobs.reconcile(applicationContext)"))
        val settings = source("ui/SettingsScreen.kt")
        assertTrue(
            "the settings screen puts the job back when it opens and when the switches change",
            settings.contains("LaunchedEffect(settings.automaticUpdateCheckEnabled, settings.updateNotificationsEnabled) {\n        UpdateCheckJobs.reconcile(screenContext, checkAtOnce = false)"),
        )
        // The screen checks as it opens, so the job it puts back does not check at once too; the wallpaper's service,
        // which does not check, asks for the check at once (the default).
        assertTrue(onCreate.contains("UpdateCheckJobs.reconcile(applicationContext)\n"))
        // And a tapped notification's check does not run twice when the stored settings arrive (two requests until v5.15).
        assertTrue(settings.contains("LaunchedEffect(settings.automaticUpdateCheckEnabled.takeIf { openUpdateForTag == null }, openUpdateForTag) {"))
        // The screen's check takes an answer of the same moment unless a tapped notification asked.
        assertTrue(settings.contains("honourLimit = !askedByNotification,\n            takeRecentAnswer = !askedByNotification,"))
    }

    @Test
    fun `each switch turned on is one check - the screen's for the first, the job's for Notify me`() {
        val advanced = source("ui/AdvancedScreen.kt")
        // The screen's keyed check runs when *Check for updates when I open PaperScrape* comes on, so that switch asks the job
        // for no run of its own: two requests in one instant with *Notify me* already on until v5.15C2 (I-967).
        val first = advanced.substring(advanced.indexOf("prefs.setAutomaticUpdateCheckEnabled(on)")).substringBefore("}")
        assertTrue(first.contains("UpdateCheckJobs.reconcile(context, checkAtOnce = false)"))
        assertFalse(first.contains("userTurnedOn"))
        val settings = source("ui/SettingsScreen.kt")
        assertTrue(settings.contains("LaunchedEffect(settings.automaticUpdateCheckEnabled.takeIf { openUpdateForTag == null }, openUpdateForTag) {"))
        // *Notify me* does not move that key, so its check at once is the job's.
        val notify = advanced.substring(advanced.indexOf("fun storeNotify(on: Boolean)")).substringBefore("SettingsSwitchRow(")
        assertTrue(notify.contains("UpdateCheckJobs.reconcile(context, userTurnedOn = on)"))
    }

    private fun source(path: String): String = repoFile("app/src/main/kotlin/com/paperscrape/livewallpaper/$path").readText()

    private fun repoFile(path: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, path)
            if (f.isFile) return f
            dir = dir.parentFile
        }
        error("not found: $path")
    }
}
