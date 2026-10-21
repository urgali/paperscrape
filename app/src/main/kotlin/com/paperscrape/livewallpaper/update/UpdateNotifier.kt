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
 * A live wallpaper that interrupts its user is a live wallpaper that gets uninstalled, so it makes
 * no sound -- and since v5.15 it is seen (the maintainer's *«A – icona in alto, senza suono
 * (consigliata)»*, 2026-10-19):
 *
 * - **[NotificationManagerCompat.IMPORTANCE_DEFAULT] with the sound and the vibration taken off the
 *   channel** ([CHANNEL_ID]) — its icon in the status bar, the notification among the normal ones in
 *   the shade and on the lock screen; no sound, no vibration, no heads-up banner (that is
 *   [NotificationManagerCompat.IMPORTANCE_HIGH]'s). Until v5.15 the channel was
 *   [NotificationManagerCompat.IMPORTANCE_LOW]: on the BV6600 a silent notification at the bottom of
 *   the shade, and in the status bar the launcher's silhouette at 17 dp, which nobody could read. On
 *   API 26+ importance is a property of the *channel* and cannot be raised once the channel exists,
 *   so the louder one is a new channel and the old one is deleted ([ensureChannel]);
 * - **its own small icon** ([R.drawable.ic_stat_update]): a house with an arrow cut out of it, drawn
 *   for 24 dp by `tools/assets/notification/build_glyph.py`, every gap 2 px or more where Android 10
 *   draws a status-bar icon (17 dp). It replaces the launcher's silhouette in the shade too, beside the
 *   app's name;
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

    /**
     * The channel since v5.15: [NotificationManagerCompat.IMPORTANCE_DEFAULT], no sound, no vibration.
     * Stable from here on: renaming it would orphan every user's notification setting for it.
     */
    const val CHANNEL_ID = "update_new_release"

    /**
     * The channel until v5.14, [NotificationManagerCompat.IMPORTANCE_LOW]. Deleted by [ensureChannel];
     * a user who had turned it off finds [CHANNEL_ID] off as well. The phone's notification settings then
     * say "1 category deleted", which Android writes for every channel an app deletes.
     */
    const val OLD_CHANNEL_ID = "update_available"

    /** The app's own orange on the notification's header: `PaperOrangeDark`, `DESIGN_NOTES.md` §7. */
    const val NOTIFICATION_COLOUR = 0xFFB5651D.toInt()

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

    /**
     * The phone's side of [PhoneState], read now -- after [ensureChannel], so a user who had turned the
     * channel of v5.14 off reads off before anything is posted (v5.15).
     */
    fun phoneState(context: Context): PhoneState {
        ensureChannel(context)
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

    /** The phone's page for the *Update available* channel ([CHANNEL_ID]) alone, for when only that is turned off. */
    fun channelSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)

    /**
     * Creates the channel if it is not there, and deletes the one of v5.14 -- safe to call as often as
     * one likes ([phoneState] and [post] both do).
     *
     * **A choice the user made in the phone's settings is carried over** (v5.15, inventory I-923): the
     * new channel is created off ([NotificationManagerCompat.IMPORTANCE_NONE]) where the old one had been
     * turned off, and at the lowest importance where it had been turned down to that; otherwise at the
     * default importance with no sound and no vibration ([channelImportance]). Once it exists the
     * platform treats a repeat creation as a no-op -- it updates the name and the description and nothing
     * the user chose -- and it will not raise its importance: a user who turns it down stays turned down.
     */
    fun ensureChannel(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        if (manager.getNotificationChannelCompat(CHANNEL_ID) == null) {
            val old = manager.getNotificationChannelCompat(OLD_CHANNEL_ID)?.importance
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL_ID, channelImportance(old))
                    .setName(context.getString(R.string.update_channel_name))
                    .setDescription(context.getString(R.string.update_channel_description))
                    .setSound(null, null)
                    .setVibrationEnabled(false)
                    .setLightsEnabled(false)
                    .build(),
            )
        } else {
            // The name and the description only: the platform keeps everything else as the user set it.
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.update_channel_name))
                    .setDescription(context.getString(R.string.update_channel_description))
                    .build(),
            )
        }
        if (manager.getNotificationChannelCompat(OLD_CHANNEL_ID) != null) manager.deleteNotificationChannel(OLD_CHANNEL_ID)
    }

    /**
     * The new channel's importance from the old one's, or null where there was none (v5.15): off stays
     * off, the lowest stays the lowest, and anything else -- the old channel as it shipped, or no old
     * channel -- is [NotificationManagerCompat.IMPORTANCE_DEFAULT]. Pure: `UpdateChannelTest`.
     */
    fun channelImportance(oldImportance: Int?): Int = when (oldImportance) {
        NotificationManagerCompat.IMPORTANCE_NONE -> NotificationManagerCompat.IMPORTANCE_NONE
        NotificationManagerCompat.IMPORTANCE_MIN -> NotificationManagerCompat.IMPORTANCE_MIN
        else -> NotificationManagerCompat.IMPORTANCE_DEFAULT
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
        // The channel is made (and the old one's "off" carried over) before the phone is asked about it.
        val phone = phoneState(context)
        if (!UpdateNotificationPolicy.mayPost(phone.permission)) return false
        // Only ever called with *Notify me about new versions* on (the update job checks it first).
        if (phone.blocks(notifySwitchOn = true)) return false

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
            // A glyph drawn for the status bar (v5.15): the launcher's silhouette, which was the icon
            // until then, is 0.71 px at its narrowest at 24 dp and about half a pixel at the bar's 17 dp
            // (the registered exception of 2026-09-23, which leaned on the app's name written beside it
            // in the shade -- the bar has no name).
            .setSmallIcon(R.drawable.ic_stat_update)
            .setColor(NOTIFICATION_COLOUR)
            .setContentTitle(context.getString(R.string.update_notification_title, tagName))
            .setContentText(context.getString(R.string.update_notification_body, currentVersionName))
            // Read below API 26 only, which this app does not reach: the channel decides.
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setOnlyAlertOnce(true)
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
