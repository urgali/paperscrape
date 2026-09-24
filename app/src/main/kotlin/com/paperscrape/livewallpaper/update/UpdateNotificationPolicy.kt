package com.paperscrape.livewallpaper.update

import com.paperscrape.livewallpaper.engine.WEATHER_REFRESH_INTERVAL_MS

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
     * How long the engine's loop waits between two update checks: **24 hours**, derived.
     *
     * Written as a multiple of [WEATHER_REFRESH_INTERVAL_MS] for the same reason
     * [com.paperscrape.livewallpaper.weather.LiveWeatherSchedule.SNAPSHOT_MAX_AGE_MILLIS] is — the
     * policy is the declaration, and a reader can see what it is a multiple *of*. The two schedules
     * share a loop, and this one being expressed in the other's unit is what says "much rarer than
     * the weather" in the source rather than in a comment.
     *
     * **Why 24 and not the weather's 1.** Weather is hours-scale and the sky on screen is wrong the
     * moment it changes. A release list changes a few times a year, and a user who hears about a
     * release a day late has lost nothing — while an hourly check would mean 24 requests a day to
     * GitHub, for every user, forever, to learn the same answer 24 times. Once a day is the
     * coarsest cadence that still feels like "it told me", and the finest that is defensible for a
     * process the user did not ask to have running.
     *
     * **What this is not.** It is not a promise of one check per day: the loop parks while the
     * wallpaper is invisible (ARC-02), so a device whose wallpaper is rarely on screen checks
     * rarely, and a user who has the app installed but another wallpaper set is never checked at
     * all. That limit was stated to the maintainer before the decision and accepted with it; the
     * manual button in *Advanced & about* is what those users have.
     */
    const val UPDATE_CHECK_INTERVAL_MILLIS = 24 * WEATHER_REFRESH_INTERVAL_MS

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
