package com.paperscrape.livewallpaper.update

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.paperscrape.livewallpaper.prefs.PrefsRecovery
import com.paperscrape.livewallpaper.prefs.PrefsRecovery.recoveringFromReadErrors
import kotlinx.coroutines.flow.first

/**
 * Shared with the instrumented recovery test, which corrupts a scratch file named after it
 * (`<name>-recoverytest`), never the real store.
 */
internal const val UPDATE_PREFS_STORE_NAME = "paperscrape_update_prefs"

/** The file of GitHub's last reply (v5.10D); shared with the same recovery test. */
internal const val UPDATE_REPLY_STORE_NAME = "paperscrape_update_reply"

/**
 * GitHub's last reply to a check, in a file of its own (v5.10D, [SavedUpdateReply]): it carries the
 * notes of every release newer than the installed one, which for a user far behind is a couple of
 * hundred kilobytes, and the snooze and the notified tag -- read on every check -- should not have to
 * parse them. Losing it costs one full download.
 */
private val Context.updateReplyDataStore by preferencesDataStore(
    name = UPDATE_REPLY_STORE_NAME,
    corruptionHandler = PrefsRecovery.replacingCorruptFile(),
)

// Its own file and its own handler. A corrupt snooze file is among the cheapest to lose -- it costs
// one "remind me later" (the reply's file above, one full download) -- but it used to be just as fatal
// as the other two stores, because the crash was in the read, not in the value. See [PrefsRecovery].
private val Context.updateDataStore by preferencesDataStore(
    name = UPDATE_PREFS_STORE_NAME,
    corruptionHandler = PrefsRecovery.replacingCorruptFile(),
)

/**
 * Persists what has already been decided about each release: the "remind me later" snooze and the
 * last version notified about -- and, since v5.10D, GitHub's last reply to a check ([readReply]).
 * Read on demand, not as a reactive Flow: by the settings screen
 * before it shows the prompt, and by the wallpaper engine's loop before it posts a notification.
 *
 * Snoozing is tied to the *specific version* that was snoozed: if a newer release comes out
 * during the snooze period, the prompt reappears immediately for that newer version instead of
 * staying silent until the original snooze expires — a month-old "remind me later" shouldn't
 * suppress news of a completely different, newer update.
 */
class UpdatePrefs(private val context: Context) : UpdateReplyStore {

    private object Keys {
        val SNOOZE_UNTIL_MILLIS = longPreferencesKey("update_snooze_until_millis")
        val SNOOZED_VERSION_TAG = stringPreferencesKey("update_snoozed_version_tag")

        /**
         * The release a notification has already been posted about (v5.7D).
         *
         * Deliberately in this file and not in [com.paperscrape.livewallpaper.prefs.WallpaperPrefs]:
         * it is not a user preference, it is the same kind of "what has already been said about
         * which version" bookkeeping the two keys above are, keyed the same way and losable at the
         * same cost. A corrupt file here costs one repeated notification, which is why this store
         * is among the cheapest to lose (the reply's file, v5.10D, costs one full download).
         */
        val NOTIFIED_VERSION_TAG = stringPreferencesKey("update_notified_version_tag")

        /**
         * The last reply GitHub gave to a check, with its `ETag` (v5.10D, [SavedUpdateReply]): what
         * lets the next check ask "has anything changed?" and download nothing when it has not. One
         * key, so the tag and the answer it vouches for are written together; in a file of its own
         * (`updateReplyDataStore`). Bookkeeping of the same kind as the two keys above: losing it costs
         * one full download, nothing else.
         */
        val LAST_REPLY = stringPreferencesKey("update_last_reply")
    }

    data class SnoozeState(val untilMillis: Long, val versionTag: String?)

    suspend fun readSnoozeState(): SnoozeState {
        // `first()` on a flow that can throw is the one read in the app that has no collector to
        // fall back on, so the recovery goes on the flow before the terminal operator rather than
        // around the call site.
        val prefs = context.updateDataStore.data.recoveringFromReadErrors().first()
        return SnoozeState(
            untilMillis = prefs[Keys.SNOOZE_UNTIL_MILLIS] ?: 0L,
            versionTag = prefs[Keys.SNOOZED_VERSION_TAG],
        )
    }

    /**
     * The newest release a notification has already gone out for, or null if none ever has.
     *
     * Read by the wallpaper engine's loop before posting, so that **one notification per version
     * tag** survives the process being killed and restarted -- which for a live wallpaper is a
     * routine event, not an exception. Without it, every rebind would be a fresh chance to raise
     * the same release again.
     */
    suspend fun readNotifiedTag(): String? =
        context.updateDataStore.data.recoveringFromReadErrors().first()[Keys.NOTIFIED_VERSION_TAG]

    /**
     * Records that [versionTag] has been notified about.
     *
     * Written **only after a post that actually went out**: recording a notification the platform
     * dropped -- a denied permission, a blocked channel -- would silence that release permanently
     * for the one user who never saw it. See [com.paperscrape.livewallpaper.update.UpdateNotifier.post],
     * which returns whether it posted for exactly this reason.
     */
    suspend fun setNotifiedTag(versionTag: String) {
        context.updateDataStore.edit { prefs -> prefs[Keys.NOTIFIED_VERSION_TAG] = versionTag }
    }

    override suspend fun readReply(): SavedUpdateReply? =
        SavedUpdateReply.fromJson(context.updateReplyDataStore.data.recoveringFromReadErrors().first()[Keys.LAST_REPLY])

    override suspend fun writeReply(reply: SavedUpdateReply) {
        context.updateReplyDataStore.edit { prefs -> prefs[Keys.LAST_REPLY] = reply.toJson() }
    }

    /** "Remind me later" -> "In a month": suppress the prompt for this specific version for ~30 days. */
    suspend fun snoozeForOneMonth(versionTag: String) {
        val oneMonthMillis = 30L * 24 * 60 * 60 * 1000
        context.updateDataStore.edit { prefs ->
            prefs[Keys.SNOOZE_UNTIL_MILLIS] = System.currentTimeMillis() + oneMonthMillis
            prefs[Keys.SNOOZED_VERSION_TAG] = versionTag
        }
    }

    /**
     * "Remind me later" -> "Not now": nothing to persist, so simply not snoozing is the whole
     * implementation. Kept as an explicit function anyway so the intent is clear at the call site
     * rather than a silent no-op.
     *
     * **The label this sits behind is no longer "Next app launch", and that is the repair** (A4,
     * v5.7D). The old label promised a schedule the app does not keep: with the automatic check off
     * -- the default -- a user who reached the prompt from Advanced and about's button was never
     * asked again next launch, because nothing asks. v5.7C measured that and carried it as a
     * decision; the maintainer took the first of the three options offered, so the button now says
     * **"Not now"**, which is true in both configurations and promises nothing.
     *
     * The function stays a no-op and stays a function: "not now" means exactly "do not write a
     * snooze", and saying so at the call site is worth more than an empty branch.
     */
    suspend fun dismissWithoutSnoozing() {
        // Intentionally a no-op: see doc comment above.
    }
}
