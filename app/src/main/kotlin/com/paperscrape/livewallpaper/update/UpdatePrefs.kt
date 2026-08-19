package com.paperscrape.livewallpaper.update

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.updateDataStore by preferencesDataStore(name = "paperscrape_update_prefs")

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
        val prefs = context.updateDataStore.data.first()
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
