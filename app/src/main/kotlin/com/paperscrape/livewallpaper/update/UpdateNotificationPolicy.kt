package com.paperscrape.livewallpaper.update

import com.paperscrape.livewallpaper.engine.WEATHER_REFRESH_INTERVAL_MS
import com.paperscrape.livewallpaper.weather.LiveWeatherSchedule

/**
 * Whether this device needs the notification permission asked for, and what the answer was.
 *
 * Three values and not a `Boolean` because "granted" and "there is nothing to grant" are different
 * facts that happen to allow the same action, and collapsing them is how a permission check ends up
 * asking on a platform that has no such permission. [NOT_REQUIRED] is every device below API 33.
 */
enum class NotificationPermission {
    /** Below API 33: notifications are granted at install time and there is nothing to request. */
    NOT_REQUIRED,

    /** API 33+, and the user has allowed notifications. */
    GRANTED,

    /** API 33+, and the user has not allowed notifications — so nothing posted would be seen. */
    DENIED,
}

/**
 * The rules behind the update notification, with no Android in them.
 *
 * ### Why this exists as a pure object
 *
 * The same shape as [com.paperscrape.livewallpaper.ui.SettingsUiModel]'s `moonPhases` and
 * `lakeContents`, and for a sharper reason: **the half of this feature that matters most to real
 * users cannot be exercised on the project's test phone at all.** The BV6600 is Android 10 / API 29
 * and `android.permission.POST_NOTIFICATIONS` does not exist there — `pm list permissions` returns
 * eighteen notification permissions and that is not one of them. So the branch where a user on
 * Android 13+ is asked, and says no, is unreachable on the only device this project has.
 *
 * What *can* be pinned is the decision itself, and that is what lives here: given an SDK level and
 * a grant flag, may this app post, and must it ask? Asserted from the JVM at 26, 29, 32, **33**, 34
 * and 37, for granted and denied, so the boundary is at 33 and cannot drift to 32 or 34 unnoticed.
 *
 * **A green test here is evidence about a decision, not about a phone.** It does not show that the
 * request is made, that the system dialog appears, or that the result lands back in the right
 * state. See `UpdateNotificationPolicyTest`, which says the same thing next to the assertions.
 */
object UpdateNotificationPolicy {

    /**
     * The API level at which notifications became a runtime permission (Android 13, TIRAMISU).
     *
     * A named constant rather than `Build.VERSION_CODES.TIRAMISU` because this file must stay free
     * of `android.*` to run on the JVM, and rather than a bare `33` because the number is the whole
     * rule: [permissionFor]'s only job is to put the boundary in one place.
     */
    const val RUNTIME_PERMISSION_SDK = 33

    /**
     * The normal wait between two update checks in the engine's loop: **3 hours**, derived.
     *
     * Written as a multiple of [WEATHER_REFRESH_INTERVAL_MS] for the same reason
     * [com.paperscrape.livewallpaper.weather.LiveWeatherSchedule.SNAPSHOT_MAX_AGE_MILLIS] is — the
     * policy is the declaration, and a reader can see what it is a multiple *of*. The two schedules
     * share a loop.
     *
     * **Why 3, the maintainer's decision of 2026-09-30** (row 3 of the v5.10A table, *«3 - voglio B»*,
     * which replaces his choice of 2026-09-23, once a day). It was 24 until v5.10D, and the v5.9
     * release reached him without a notification: published at 20:54, it waited for a check that
     * came up to a day after his wallpaper's last one, and he had installed it by then. Every three
     * hours, still inside the wallpaper and with no library added, the notice arrives within a few
     * hours. What it costs is eight requests a day to GitHub instead of one, and only with both
     * update switches on; and since the same round a check that finds the list unchanged downloads
     * nothing (`UpdateChecker`, `If-None-Match`: GitHub answers 304 with no body). The list also
     * changes when somebody downloads a release's file (it carries the download counts), and then the
     * check downloads it again, once.
     *
     * **What this is not.** It is not a promise of eight checks a day. It can be more: every rebind
     * of the wallpaper checks at once, and a failed check is retried from two minutes, doubling up
     * to this. It can be less: the loop parks while the wallpaper is invisible (ARC-02), so a
     * device whose wallpaper is rarely on screen checks rarely, and a user who has another
     * wallpaper set is never checked by this loop (only by the settings screen's own check, when
     * they open it). That limit was stated to the maintainer before the decision of 2026-09-23 and
     * accepted with it, and row 3 kept it; the manual button in *Advanced & about* is what those
     * users have.
     */
    const val UPDATE_CHECK_INTERVAL_MILLIS = 3 * WEATHER_REFRESH_INTERVAL_MS

    /**
     * Whether the engine's loop checks on this pass: [UPDATE_CHECK_INTERVAL_MILLIS] since the last
     * check, or the retry ladder's shorter wait while checks come back
     * [UpdateCheckResult.Unreachable] -- the *same* ladder the weather loop uses
     * ([com.paperscrape.livewallpaper.weather.LiveWeatherSchedule.nextAttemptDelayMillis], from two
     * minutes doubling up to the interval), reused rather than re-derived, so there is one
     * bounded-backoff rule in this app and not two that can drift.
     *
     * [elapsedSinceLastCheckMillis] is on the monotonic clock (`SystemClock.elapsedRealtime`); a
     * fresh engine's sentinel reads as long ago, so its first pass checks. Pure, so the cadence is
     * asserted over a day of passes on the JVM (`UpdateCheckCadenceTest`) rather than read off a
     * constant.
     */
    fun checkDue(elapsedSinceLastCheckMillis: Long, consecutiveUnreachable: Int): Boolean =
        LiveWeatherSchedule.isAttemptDue(
            elapsedSinceLastCheckMillis,
            LiveWeatherSchedule.nextAttemptDelayMillis(consecutiveUnreachable, UPDATE_CHECK_INTERVAL_MILLIS),
        )

    /**
     * What the notification permission means on this device, from the two facts that decide it.
     *
     * [granted] is only consulted at [RUNTIME_PERMISSION_SDK] and above. Below it the platform
     * grants notifications at install time, so a `false` from a permission checker there means
     * "this permission is not in the manifest's runtime set", not "the user refused" — reading it
     * as a refusal is what would make the feature silently dead on every Android 8 to 12 device.
     */
    fun permissionFor(sdkInt: Int, granted: Boolean): NotificationPermission = when {
        sdkInt < RUNTIME_PERMISSION_SDK -> NotificationPermission.NOT_REQUIRED
        granted -> NotificationPermission.GRANTED
        else -> NotificationPermission.DENIED
    }

    /**
     * Whether posting is worth doing at all.
     *
     * [NotificationPermission.DENIED] is the only no. Posting into a denied state is not an error
     * and does not throw — the platform simply drops it — which is exactly why this is checked:
     * a dropped notification would otherwise be recorded as "already told them about this version"
     * and the user would never hear about it again. See [UpdateNotifier].
     */
    fun mayPost(permission: NotificationPermission): Boolean =
        permission != NotificationPermission.DENIED

    /**
     * Whether to put the system's permission dialog in front of the user.
     *
     * Only when there is a permission to ask for and it is not held. [NotificationPermission.NOT_REQUIRED]
     * must never ask: `ActivityResultContracts.RequestPermission` on a permission the platform does
     * not define returns immediately, so the caller would see a result with no dialog and could
     * reasonably decide the user had refused.
     */
    fun mustAsk(permission: NotificationPermission): Boolean =
        permission == NotificationPermission.DENIED

    /**
     * Whether the phone's own settings are what stops PaperScrape's notifications: the app's
     * notifications switched off, or the *Update available* channel turned off.
     *
     * [appNotificationsEnabled] is `NotificationManagerCompat.areNotificationsEnabled`. Below API 33,
     * and on 33+ while the permission is granted, false is exactly the user's switch in the app's
     * notification page. **On 33+ with the permission [NotificationPermission.DENIED] it means one
     * of two things, and [notifySwitchOn] -- *Notify me about new versions* as saved -- tells them
     * apart:**
     *
     *  - **not asked yet.** A new install, or a user who never turned the switch on: the switch is
     *    off, because the row asks for the permission *before* it stores "on", so a saved "on" and a
     *    permission never granted cannot come from this app. Nothing is switched off in the phone's
     *    settings, and turning the switch on asks. Not blocked;
     *  - **taken away.** From Android 13, switching an app's notifications off in the phone's
     *    settings revokes `POST_NOTIFICATIONS` (AOSP `NotificationManagerService`
     *    `setNotificationsEnabledForPackage` -> `PermissionHelper.setNotificationPermission`, and
     *    `areNotificationsEnabled` is `hasPermission`), so the switch stays on while the permission
     *    becomes DENIED. Nothing would appear, and the row must say so. Blocked.
     *
     * Until v5.9F a DENIED permission never counted, so the second case read the normal line and its
     * promise of a notification: inventory I-54, the defect row A13 had repaired below API 33 and
     * that the maintainer confirmed on Android 16 on 2026-09-28.
     */
    fun blockedInPhoneSettings(
        permission: NotificationPermission,
        appNotificationsEnabled: Boolean,
        channelTurnedOff: Boolean,
        notifySwitchOn: Boolean,
    ): Boolean = channelTurnedOff ||
        (!appNotificationsEnabled && (permission != NotificationPermission.DENIED || notifySwitchOn))

    /** Which line *Notify me about new versions* shows under its title. */
    enum class NotifyRowLine {
        /** "Needs \"Check for updates when I open PaperScrape\" above": the row is greyed and this is why. */
        NEEDS_AUTOMATIC_CHECK,

        /** The phone's settings stop PaperScrape's notifications, and a tap opens the page where they are allowed. */
        BLOCKED,

        /**
         * Android 13 or later, and the permission is not held while the switch is saved on: taken away
         * by switching notifications off in the phone's settings, or never given on this phone (an app
         * backup restored on a new one). A tap asks; where the system will not ask again, it opens the page.
         */
        BLOCKED_ASK,

        /** The permission dialog was just refused; a tap asks again. */
        REFUSED,

        /** PaperScrape is not the phone's wallpaper, and the check runs inside it; a tap sets it. */
        NOT_THE_WALLPAPER,

        /** What the switch does. */
        DESCRIPTION,
    }

    /** What a tap on *Notify me about new versions* does (v5.10C). */
    enum class NotifyTap {
        /** The row is greyed: nothing. */
        NOTHING,

        /** Shown on: the user turns it off, and only the saved choice changes. */
        TURN_OFF,

        /** Everything is in place but the saved choice: store "on". */
        TURN_ON,

        /**
         * Ask for `POST_NOTIFICATIONS`, and store "on" only once it is granted -- a saved "on" with the
         * permission refused would read as [NotifyRowLine.BLOCKED_ASK]. Granted and PaperScrape not the
         * wallpaper, carry on to [SET_AS_WALLPAPER]; refused where the system will not show the dialog
         * again, store "on" and carry on to [OPEN_APP_NOTIFICATIONS].
         */
        ASK_PERMISSION,

        /** Store "on", and open the phone's page for PaperScrape's notifications. */
        OPEN_APP_NOTIFICATIONS,

        /** Store "on", and open the phone's page for the *Update available* channel. */
        OPEN_CHANNEL,

        /** Store "on", and put up the system's preview, where PaperScrape is set as the wallpaper. */
        SET_AS_WALLPAPER,
    }

    /**
     * How *Notify me about new versions* is drawn and what a tap on it does.
     *
     * @property shownOn what the switch shows: **on only if a notification can really arrive**
     *   (v5.10C, the maintainer's rule of 2026-09-29: *«l'utente normale non legge, vede il toggle e si
     *   arrabbia perché non funziona»*). The saved choice is not changed to make it so; it comes back
     *   by itself as soon as what was missing is in place.
     * @property enabled whether the row accepts a tap: only the automatic check above greys it.
     */
    data class NotifyRowState(
        val shownOn: Boolean,
        val enabled: Boolean,
        val line: NotifyRowLine,
        val tap: NotifyTap,
    )

    /**
     * The row, from what the settings screen knows when it draws.
     *
     * **A notification can arrive only if all of these hold**, and the switch shows on only then:
     * the automatic check is on (the engine checks only with both switches on,
     * `PaperWallpaperService.maybeCheckForUpdate`); the phone lets PaperScrape post -- the permission
     * on Android 13+ ([mayPost]), the app's notifications and the *Update available* channel left on
     * ([blockedInPhoneSettings] judged as if the switch were on, which is how [UpdateNotifier.post]
     * judges); and PaperScrape is the phone's wallpaper, because the check runs inside it and nowhere
     * else ([isTheWallpaper], `WallpaperEngineCensus`). Until v5.10C the switch read the saved choice
     * and the automatic check alone, and said the rest in the line under it -- the defect the
     * maintainer met on Android 16 (inventory I-201).
     *
     * When something is missing, the line names the first one, in the order a user can fix them
     * without leaving the app first: the automatic check, the phone's settings, the wallpaper.
     *
     * [notifySwitchOn] is the switch as saved; [requestRefused] is whether the permission dialog was
     * refused a moment ago on this screen.
     */
    fun notifyRow(
        automaticCheckEnabled: Boolean,
        notifySwitchOn: Boolean,
        permission: NotificationPermission,
        appNotificationsEnabled: Boolean,
        channelTurnedOff: Boolean,
        isTheWallpaper: Boolean,
        requestRefused: Boolean,
    ): NotifyRowState {
        if (!automaticCheckEnabled) {
            return NotifyRowState(false, enabled = false, NotifyRowLine.NEEDS_AUTOMATIC_CHECK, NotifyTap.NOTHING)
        }
        val phoneLetsItPost = mayPost(permission) &&
            !blockedInPhoneSettings(permission, appNotificationsEnabled, channelTurnedOff, notifySwitchOn = true)
        if (notifySwitchOn && phoneLetsItPost && isTheWallpaper) {
            return NotifyRowState(true, enabled = true, NotifyRowLine.DESCRIPTION, NotifyTap.TURN_OFF)
        }
        val (line, tap) = when {
            channelTurnedOff -> NotifyRowLine.BLOCKED to NotifyTap.OPEN_CHANNEL
            mustAsk(permission) -> when {
                notifySwitchOn -> NotifyRowLine.BLOCKED_ASK
                requestRefused -> NotifyRowLine.REFUSED
                !isTheWallpaper -> NotifyRowLine.NOT_THE_WALLPAPER
                else -> NotifyRowLine.DESCRIPTION
            } to NotifyTap.ASK_PERMISSION
            !appNotificationsEnabled -> NotifyRowLine.BLOCKED to NotifyTap.OPEN_APP_NOTIFICATIONS
            !isTheWallpaper -> NotifyRowLine.NOT_THE_WALLPAPER to NotifyTap.SET_AS_WALLPAPER
            else -> NotifyRowLine.DESCRIPTION to NotifyTap.TURN_ON
        }
        return NotifyRowState(false, enabled = true, line, tap)
    }

    /**
     * Whether "remind me later" is still suppressing this exact release.
     *
     * The same expression the settings screen's launch check has always used, moved here so the two
     * places that must agree about a snooze read it from one. The keying is on the **version tag**,
     * so a month-long snooze on `v5.6` does not hide `v5.7`: a rejected release is rejected, not the
     * idea of being told about releases.
     *
     * [untilMillis] is a wall-clock stamp, because it is the one the snooze was written with and it
     * has to survive the process dying. That makes it the wrong clock for a *schedule* — see
     * [UPDATE_CHECK_INTERVAL_MILLIS]'s caller, which uses `elapsedRealtime` — and the right one for
     * an expiry the user set in calendar terms.
     */
    fun isSnoozed(
        tagName: String,
        snoozedTag: String?,
        untilMillis: Long,
        nowMillis: Long,
    ): Boolean = snoozedTag == tagName && nowMillis < untilMillis

    /**
     * The whole decision: post a notification about [tagName], or stay quiet?
     *
     * Every reason to stay quiet is here, in the order a reader would ask them:
     *
     * 1. the user has not asked to be notified;
     * 2. notifications are denied, so anything posted would be thrown away — and, worse, would be
     *    recorded as delivered (see [mayPost]);
     * 3. this exact release has already been notified once. **One notification per version tag** is
     *    the approved behaviour: a release the user has already been shown and has left alone is not
     *    news again tomorrow. [alreadyNotifiedTag] is what the last successful post wrote, so a
     *    newer tag passes this test immediately;
     * 4. the user chose "remind me later" for this exact release, which the notification honours
     *    rather than routes around.
     *
     * Pure, and the reason it is worth being pure is (3) and (4): both are state the phone can only
     * reach after a release that does not exist yet, so the only way to assert them before shipping
     * is to hand them in as values.
     */
    fun shouldPost(
        notificationsEnabled: Boolean,
        permission: NotificationPermission,
        tagName: String,
        alreadyNotifiedTag: String?,
        snoozedTag: String?,
        snoozeUntilMillis: Long,
        nowMillis: Long,
    ): Boolean = when {
        !notificationsEnabled -> false
        !mayPost(permission) -> false
        alreadyNotifiedTag == tagName -> false
        isSnoozed(tagName, snoozedTag, snoozeUntilMillis, nowMillis) -> false
        else -> true
    }

    /**
     * Whether a finished check is worth retrying sooner than [UPDATE_CHECK_INTERVAL_MILLIS].
     *
     * The counterpart of [com.paperscrape.livewallpaper.weather.LiveWeatherSchedule.isTransient],
     * and simpler than it for a reason worth writing down: the weather providers can answer *no* —
     * a missing key, a rejected key, a spent quota — and hammering them sooner neither helps nor is
     * polite. GitHub's public Releases endpoint has no such answer for this app: it either replies
     * with the list or it does not reply usefully, and all three
     * [UpdateCheckResult.Unreachable.Reason]s mean the same thing, which is *nothing is known*.
     *
     * A null [result] is "no check was made on this pass" and is not a failure.
     */
    fun isTransient(result: UpdateCheckResult?): Boolean = result is UpdateCheckResult.Unreachable
}
