package com.paperscrape.livewallpaper.update

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.paperscrape.livewallpaper.R
import com.paperscrape.livewallpaper.ui.SettingsActivity

/**
 * Posts the one notification this app has: **a new release is out**.
 *
 * ### What it is allowed to be
 *
 * A live wallpaper that interrupts its user is a live wallpaper that gets uninstalled, so every
 * choice here is the quiet one and each is deliberate:
 *
 * - **[NotificationManagerCompat.IMPORTANCE_LOW]** — it appears in the shade and on the lock screen
 *   and does nothing else. No sound, no vibration, no heads-up banner. On API 26+ importance is a
 *   property of the *channel* and cannot be raised per notification, which is the point: a future
 *   edit cannot make this louder without changing the channel, and the user can always make it
 *   quieter still from system settings;
 * - **`setAutoCancel(true)`** — tapping it takes it away, so the shade does not accumulate;
 * - **one notification per version tag**, which is enforced twice over. The platform tag is the
 *   release tag, so two posts about `v5.7` replace each other rather than stack; and
 *   [UpdateNotificationPolicy.shouldPost] refuses a second post for a tag already recorded in
 *   [UpdatePrefs.readNotifiedTag], so a version the user has already been shown and left alone is
 *   not raised again tomorrow.
 *
 * ### The channel's two strings are user-visible
 *
 * [R.string.update_channel_name] is the heading in *System settings → Apps → PaperScrape →
 * Notifications*, and its description is the line under it. They are the only account of this
 * feature a user gets from outside the app, so they say what it does and how often.
 *
 * ### What is not verifiable on this project's phone
 *
 * The BV6600 is Android 10 / API 29 and has no `POST_NOTIFICATIONS`, so [notificationPermission]
 * returns [NotificationPermission.NOT_REQUIRED] there and every call below takes the branch a real
 * Android 13+ user never takes. The channel, the posting, the importance and the tap were measured
 * on it; the permission request was not and could not be. [UpdateNotificationPolicy] carries the
 * decision that could be asserted instead.
 */
object UpdateNotifier {

    /** Stable across releases: renaming it would orphan every user's notification setting for it. */
    const val CHANNEL_ID = "update_available"

    /**
     * One id for every update notification, because the *tag* is what distinguishes them.
     *
     * `notify(tag, id)` keys on the pair, so a fixed id with the release tag as tag gives exactly
     * one notification per release and lets [cancel] take back the one for a specific release
     * without touching anything else.
     */
    private const val NOTIFICATION_ID = 1

    /**
     * Names the release the notification is about, so the settings screen can open straight onto
     * the dialog for it.
     *
     * Read by `SettingsActivity`. The value is carried rather than merely a flag so the screen can
     * tell "the user tapped a notification about v5.7" from "the user opened settings", which is
     * what lets the dialog appear even when the automatic check is off — the tap *is* the request.
     */
    const val EXTRA_SHOW_UPDATE_TAG = "com.paperscrape.livewallpaper.SHOW_UPDATE_TAG"

    /**
     * What the notification permission is on this device right now.
     *
     * The two facts [UpdateNotificationPolicy.permissionFor] needs, read in the one place that is
     * allowed to touch `android.*`. Below API 33 the grant flag is not consulted at all — see that
     * function for why reading it there would be a bug rather than a nicety.
     *
     * `InlinedApi` is suppressed for the same reason: `POST_NOTIFICATIONS` is a String constant the
     * compiler copies in, so reading it below API 33 cannot fail, and there its answer is unread.
     */
    @SuppressLint("InlinedApi")
    fun notificationPermission(context: Context): NotificationPermission =
        UpdateNotificationPolicy.permissionFor(
            sdkInt = Build.VERSION.SDK_INT,
            granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )

    /**
     * What the phone's settings say about PaperScrape's notifications right now: the three facts
     * [UpdateNotificationPolicy.blockedInPhoneSettings] decides from, besides the app's own switch.
     * Read by the settings screen whenever it comes to the front -- the way to change any of them is
     * to leave for the phone's settings and return -- and by [post].
     */
    data class PhoneState(
        val permission: NotificationPermission,
        val appNotificationsEnabled: Boolean,
        val channelTurnedOff: Boolean,
    ) {
        /** See [UpdateNotificationPolicy.blockedInPhoneSettings]; [notifySwitchOn] is the switch as saved. */
        fun blocks(notifySwitchOn: Boolean): Boolean = UpdateNotificationPolicy.blockedInPhoneSettings(
            permission, appNotificationsEnabled, channelTurnedOff, notifySwitchOn,
        )
    }

    /** The phone's side of [PhoneState], read now. */
    fun phoneState(context: Context): PhoneState {
        val manager = NotificationManagerCompat.from(context)
        return PhoneState(
            permission = notificationPermission(context),
            appNotificationsEnabled = manager.areNotificationsEnabled(),
            channelTurnedOff = manager.getNotificationChannelCompat(CHANNEL_ID)?.importance ==
                NotificationManagerCompat.IMPORTANCE_NONE,
        )
    }

    /**
     * The phone's page for PaperScrape's notifications -- where a user who has switched them off, or
     * on Android 13+ has refused them for good, can allow them (v5.10C, the tap on *Notify me about
     * new versions* when the phone is what blocks it). `ACTION_APP_NOTIFICATION_SETTINGS` exists from
     * API 26, the app's `minSdk`.
     */
    fun appNotificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** The app's details page, which every Android has: the fallback when a notification page will not open. */
    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))

    /** The phone's page for the *Update available* channel alone, for when only that is turned off. */
    fun channelSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)

    /**
     * Creates the channel if it is not there, which is safe to call as often as one likes.
     *
     * Called before every post rather than once at startup: posting happens only in the wallpaper
     * engine's loop, and nothing else creates the channel. The platform treats a repeat creation
     * as a no-op, and it will not raise an existing channel's importance — a user who has turned
     * this down stays turned down.
     */
    fun ensureChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.update_channel_name))
            .setDescription(context.getString(R.string.update_channel_description))
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /**
     * Posts the notification for [tagName], and says whether it actually went out.
     *
     * The return value is the whole reason this is not a `Unit` function: the caller records the tag
     * as notified, and recording a tag whose notification was dropped would silence that release
     * forever. `false` means nothing was posted — the permission is denied, the phone's settings
     * have PaperScrape's notifications or this channel switched off ([phoneState]), or
     * the platform refused. All are the same outcome for the caller and none throws. Until v5.8B a
     * switched-off app or channel returned `true`: the platform drops such a post silently, so the
     * release was recorded as notified and never shown when the user switched them back on.
     *
     * [currentVersionName] goes in the body rather than the title because the title has to read on
     * a lock screen in one glance, and "which version am I on" is the second question, not the first.
     */
    fun post(context: Context, tagName: String, currentVersionName: String): Boolean {
        val phone = phoneState(context)
        if (!UpdateNotificationPolicy.mayPost(phone.permission)) return false
        // Only ever called with *Notify me about new versions* on (the engine's loop checks it first).
        if (phone.blocks(notifySwitchOn = true)) return false
        ensureChannel(context)

        // FLAG_IMMUTABLE is mandatory from API 31 and available from 23, so it is unconditional
        // rather than version-gated. Nothing here needs to fill the intent in later.
        val intent = Intent(context, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_SHOW_UPDATE_TAG, tagName)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            /* requestCode = */ tagName.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            // The themed-icon silhouette the launcher already ships. A notification small icon is
            // drawn as a tinted mask, which is exactly what this drawable was drawn to be, so the
            // feature costs no new artwork and no atlas row.
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.update_notification_title, tagName))
            .setContentText(context.getString(R.string.update_notification_body, currentVersionName))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(tagName, NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            // Defensive. A post without POST_NOTIFICATIONS is normally dropped silently rather
            // than thrown (see UpdateNotificationPolicy.mayPost), so this is not the expected path;
            // should the platform throw here, it counts as "not posted".
            false
        }
    }

    /** Takes back the notification for one release, if it is still in the shade. */
    fun cancel(context: Context, tagName: String) {
        NotificationManagerCompat.from(context).cancel(tagName, NOTIFICATION_ID)
    }
}
