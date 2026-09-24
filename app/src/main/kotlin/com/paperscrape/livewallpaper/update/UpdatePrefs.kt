package com.paperscrape.livewallpaper.update

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.paperscrape.livewallpaper.prefs.PrefsRecovery
import com.paperscrape.livewallpaper.prefs.PrefsRecovery.recoveringFromReadErrors
import kotlinx.coroutines.flow.first

/** Shared with the instrumented recovery test, which corrupts this exact file. */
internal const val UPDATE_PREFS_STORE_NAME = "paperscrape_update_prefs"

// Its own file and its own handler. A corrupt snooze file is the cheapest of the three to lose --
// it costs one "remind me later" -- but it used to be just as fatal as the other two, because the
// crash was in the read, not in the value. See [PrefsRecovery].
private val Context.updateDataStore by preferencesDataStore(
    name = UPDATE_PREFS_STORE_NAME,
    corruptionHandler = PrefsRecovery.replacingCorruptFile(),
)

/**
 * Persists the "remind me later" choice for the update prompt. Read once per app launch (not a
 * reactive Flow) since this only matters at startup, before the prompt is shown.
 *
 * Snoozing is tied to the *specific version* that was snoozed: if a newer release comes out
 * during the snooze period, the prompt reappears immediately for that newer version instead of
 * staying silent until the original snooze expires — a month-old "remind me later" shouldn't
 * suppress news of a completely different, newer update.
 */
class UpdatePrefs(private val context: Context) {

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
         * is the one with the cheapest recovery of the three.
         */
        val NOTIFIED_VERSION_TAG = stringPreferencesKey("update_notified_version_tag")
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
