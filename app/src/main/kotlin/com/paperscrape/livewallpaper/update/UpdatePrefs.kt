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

    /** "Remind me later" -> "In a month": suppress the prompt for this specific version for ~30 days. */
    suspend fun snoozeForOneMonth(versionTag: String) {
        val oneMonthMillis = 30L * 24 * 60 * 60 * 1000
        context.updateDataStore.edit { prefs ->
            prefs[Keys.SNOOZE_UNTIL_MILLIS] = System.currentTimeMillis() + oneMonthMillis
            prefs[Keys.SNOOZED_VERSION_TAG] = versionTag
        }
    }

    /** "Remind me later" -> "Next app launch": nothing to persist -- the check already runs
     * every launch by design, so simply not snoozing achieves exactly this. Kept as an explicit
     * function anyway so the intent is clear at the call site rather than a silent no-op. */
    suspend fun snoozeUntilNextLaunch() {
        // Intentionally a no-op: see doc comment above.
    }
}
