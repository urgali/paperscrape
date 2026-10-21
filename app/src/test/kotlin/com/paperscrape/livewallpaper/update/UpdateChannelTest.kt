package com.paperscrape.livewallpaper.update

import androidx.core.app.NotificationManagerCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **The notification of v5.15: its icon in the status bar, among the normal ones in the shade, on the lock screen,
 * and no sound** -- the maintainer's *«A – icona in alto, senza suono (consigliata)»* (2026-10-19, inventory I-919 to
 * I-925).
 *
 * A channel's importance cannot be raised once it exists, so the louder notification is a new channel and the one of
 * v5.14 is deleted; **a user who had turned the old one off finds the new one off** (I-923) -- [UpdateNotifier.channelImportance],
 * pure. The rest is the notifier's wiring, held on its source: the channel's importance, its sound and vibration taken
 * off, the old channel deleted, the glyph drawn for the status bar as the small icon, the app's orange on the header.
 * The phone's half -- the channel turned off on the BV6600 and found off after the update -- is the round's photograph.
 */
class UpdateChannelTest {

    @Test
    fun `a choice made in the phone's settings is carried over`() {
        assertEquals("off stays off", NotificationManagerCompat.IMPORTANCE_NONE, UpdateNotifier.channelImportance(NotificationManagerCompat.IMPORTANCE_NONE))
        assertEquals("the lowest stays the lowest", NotificationManagerCompat.IMPORTANCE_MIN, UpdateNotifier.channelImportance(NotificationManagerCompat.IMPORTANCE_MIN))
        for (old in listOf(null, NotificationManagerCompat.IMPORTANCE_LOW, NotificationManagerCompat.IMPORTANCE_DEFAULT, NotificationManagerCompat.IMPORTANCE_HIGH)) {
            assertEquals("old=$old", NotificationManagerCompat.IMPORTANCE_DEFAULT, UpdateNotifier.channelImportance(old))
        }
    }

    @Test
    fun `a new channel, and the old one goes`() {
        assertEquals("update_new_release", UpdateNotifier.CHANNEL_ID)
        assertEquals("update_available", UpdateNotifier.OLD_CHANNEL_ID)
        val notifier = source("kotlin/com/paperscrape/livewallpaper/update/UpdateNotifier.kt")
        val ensure = notifier.substring(notifier.indexOf("fun ensureChannel(context: Context) {")).substringBefore("\n    }\n")
        assertTrue(ensure.contains("NotificationChannelCompat.Builder(CHANNEL_ID, channelImportance(old))"))
        assertTrue("no sound", ensure.contains(".setSound(null, null)"))
        assertTrue("no vibration", ensure.contains(".setVibrationEnabled(false)"))
        assertTrue("the old channel deleted", ensure.contains("manager.deleteNotificationChannel(OLD_CHANNEL_ID)"))
        // Read at all, then before the delete: an index of -1 is "before" anything (v5.15C2, inventory I-975).
        val read = ensure.indexOf("getNotificationChannelCompat(OLD_CHANNEL_ID)?.importance")
        assertTrue("the old channel's importance is not read", read >= 0)
        assertTrue("its importance read before it goes", read < ensure.indexOf("deleteNotificationChannel"))
        // The phone is asked about the channel only after it exists, so an old "off" reads off at once.
        val phone = notifier.substring(notifier.indexOf("fun phoneState(context: Context): PhoneState {")).substringBefore("\n    }\n")
        assertTrue(phone.trimStart().startsWith("fun phoneState(context: Context): PhoneState {\n        ensureChannel(context)"))
        // And with nothing opened: the job's reconciling makes it first, which the wallpaper's service does when an
        // update restarts it -- so after an update from v5.14 the old channel is gone without a screen opened.
        val jobs = source("kotlin/com/paperscrape/livewallpaper/update/UpdateCheckJobService.kt")
        val reconcile = jobs.substring(jobs.indexOf("internal suspend fun reconcileNow(")).substringBefore("\n    }\n")
        assertTrue(reconcile.substringAfter("{").trimStart().startsWith("UpdateNotifier.ensureChannel(context)"))
    }

    @Test
    fun `the glyph for the status bar, the app's orange, and one notification per version`() {
        val notifier = source("kotlin/com/paperscrape/livewallpaper/update/UpdateNotifier.kt")
        val post = notifier.substring(notifier.indexOf("fun post(context: Context, tagName: String, currentVersionName: String): Boolean {"))
        assertTrue(post.contains(".setSmallIcon(R.drawable.ic_stat_update)"))
        assertFalse("the launcher's silhouette is no longer the small icon", post.contains("ic_launcher_monochrome"))
        assertTrue(post.contains(".setColor(NOTIFICATION_COLOUR)"))
        assertEquals("PaperOrangeDark", 0xFFB5651D.toInt(), UpdateNotifier.NOTIFICATION_COLOUR)
        assertTrue("the tag is the release's: one notification per version", post.contains("notify(tagName, NOTIFICATION_ID, notification)"))
        assertTrue(source("res/drawable/ic_stat_update.xml").contains("android:fillType=\"evenOdd\""))
    }

    @Test
    fun `the lines say what the notification and the check now do`() {
        val strings = source("res/values/strings.xml")
        val notify = Regex("""<string name="settings_update_notify_subtitle">([^<]*)</string>""").find(strings)!!.groupValues[1]
        assertTrue(notify.contains("small house icon at the top of the screen"))
        assertTrue(notify.contains("No sound and no pop-up"))
        assertTrue(notify.contains("with the screen off and with another wallpaper too"))
        val channel = Regex("""<string name="update_channel_description">([^<]*)</string>""").find(strings)!!.groupValues[1]
        assertTrue(channel.contains("small house icon in the status bar"))
        assertTrue(channel.contains("no sound"))
        assertTrue(channel.contains("with the screen off too"))
        assertFalse("no line says the check needs the wallpaper on screen", strings.contains("while it is your wallpaper and on screen"))
        assertFalse(strings.contains("while the wallpaper is on screen"))
    }

    private fun source(path: String): String {
        val suffix = "src/main/$path"
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = File(dir, "$prefix$suffix")
                if (f.isFile) return f.readText()
            }
            dir = dir.parentFile
        }
        error("source not found: $suffix")
    }
}
