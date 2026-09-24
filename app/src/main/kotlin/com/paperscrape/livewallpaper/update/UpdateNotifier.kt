package com.paperscrape.livewallpaper.update

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
     */
    fun notificationPermission(context: Context): NotificationPermission =
        UpdateNotificationPolicy.permissionFor(
            sdkInt = Build.VERSION.SDK_INT,
            granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )

    /**
     * Creates the channel if it is not there, which is safe to call as often as one likes.
     *
     * Called before every post rather than once at startup, deliberately: the engine and the
     * settings screen are two independent entry points and neither can assume the other ran. The
     * platform treats a repeat creation as a no-op, and it will not raise an existing channel's
     * importance — a user who has turned this down stays turned down.
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
     * forever. `false` means nothing was posted — either the permission is denied, or the platform
     * refused. Both are the same outcome for the caller and neither throws.
     *
     * [currentVersionName] goes in the body rather than the title because the title has to read on
     * a lock screen in one glance, and "which version am I on" is the second question, not the first.
     */
    fun post(context: Context, tagName: String, currentVersionName: String): Boolean {
        if (!UpdateNotificationPolicy.mayPost(notificationPermission(context))) return false
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
            // The documented throw when POST_NOTIFICATIONS is missing on API 33+. Reaching it means
            // the permission was revoked between the check above and this line, which is a race the
            // platform allows; treating it as "not posted" is the whole handling it needs.
            false
        }
    }

    /** Takes back the notification for one release, if it is still in the shade. */
    fun cancel(context: Context, tagName: String) {
        NotificationManagerCompat.from(context).cancel(tagName, NOTIFICATION_ID)
    }
}
