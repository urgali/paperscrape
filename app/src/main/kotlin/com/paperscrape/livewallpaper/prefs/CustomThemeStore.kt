package com.paperscrape.livewallpaper.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.paperscrape.livewallpaper.prefs.PrefsRecovery.recoveringFromReadErrors
import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.CustomThemeEntry
import com.paperscrape.livewallpaper.engine.customThemeDataFromJsonString
import com.paperscrape.livewallpaper.engine.customThemeDataOrNull
import com.paperscrape.livewallpaper.engine.toJsonString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Shared with the instrumented recovery test, which corrupts this exact file. */
internal const val CUSTOM_THEME_STORE_NAME = "paperscrape_custom_themes"

// Its own file and its own handler, so a corrupt theme blob costs the user their saved themes and
// leaves every other store untouched. See [PrefsRecovery].
private val Context.customThemeDataStore by preferencesDataStore(
    name = CUSTOM_THEME_STORE_NAME,
    corruptionHandler = PrefsRecovery.replacingCorruptFile(),
)

/**
 * Persists two things, both editable from the "Manage Themes" screen:
 *  - overrides: a user-saved replacement for one of the built-in themes (e.g. their own
 *    "christmas"), which [com.paperscrape.livewallpaper.engine.ThemeCatalog.byId] prefers over
 *    the hardcoded default. Removing an override is exactly "Reset to default".
 *  - customThemes: fully independent, user-created themes with their own id/name.
 *
 * Stored as a single JSON blob under one DataStore key -- the data is small (a handful of
 * themes at most) and always read/written as a whole, so this is simpler than modeling each
 * theme as its own preference key.
 */
open class CustomThemeStore(private val context: Context) {

    private object Keys {
        val DATA_JSON = stringPreferencesKey("custom_theme_data_json")
    }

    val dataFlow: Flow<CustomThemeData> = context.customThemeDataStore.data
        .recoveringFromReadErrors()
        .map { prefs ->
            customThemeDataFromJsonString(prefs[Keys.DATA_JSON])
        }

    /**
     * Applies [transform] to the stored data — **unless the stored data cannot be read**.
     *
     * ### BCK-05: the read-modify-write that ate the themes
     *
     * [customThemeDataFromJsonString] answers `EMPTY` for a blob it cannot parse, which is right for
     * *reading*: a corrupt store shows no custom themes rather than crashing the wallpaper. It was
     * badly wrong here. This is a read-modify-write, so the next preference the user touched read
     * `EMPTY`, applied the change to it, and wrote the result back over bytes that were still on
     * disk — turning "the app cannot read your themes" into "your themes are gone", with no step in
     * between and nothing to recover from.
     *
     * The distinction the fix needs is one the reader deliberately does not make: **absent** and
     * **unreadable** are different. Absent is the normal empty store and must be transformed as
     * usual. Unreadable means the only copy of the user's themes is the blob already there, and the
     * one thing that must not happen to it is being replaced by a document derived from `EMPTY`.
     *
     * So an unreadable blob is left exactly as it is and the edit is dropped. That costs the user
     * the tap they just made, and the UI already shows them an empty theme list, so the state is
     * visible rather than silent. It buys them a file that still contains their themes.
     */
    private suspend fun update(transform: (CustomThemeData) -> CustomThemeData) {
        context.customThemeDataStore.edit { prefs ->
            val raw = prefs[Keys.DATA_JSON]
            val current = customThemeDataOrNull(raw)
            if (current == null) return@edit
            prefs[Keys.DATA_JSON] = transform(current).toJsonString()
        }
    }

    /** Saves (or replaces) an override for a built-in theme id, e.g. the user's own "christmas". */
    suspend fun setOverride(builtinId: String, entry: CustomThemeEntry) = update { data ->
        data.copy(overrides = data.overrides + (builtinId to entry))
    }

    /** "Reset to default": removes the override, falling back to the hardcoded built-in theme. */
    suspend fun clearOverride(builtinId: String) = update { data ->
        data.copy(overrides = data.overrides - builtinId)
    }

    /**
     * Clears *every* built-in override at once. Useful because an override permanently freezes
     * a snapshot of a theme's objects from whenever it was saved — if a later app update adds
     * more houses/buildings/cars/etc. to that theme's built-in definition, an overridden theme
     * never sees them (it keeps rendering its old, frozen layout forever). This is the one-tap
     * way to guarantee every built-in theme is showing its current, up-to-date definition.
     */
    suspend fun clearAllOverrides() = update { data -> data.copy(overrides = emptyMap()) }

    /** Saves a brand-new independent custom theme (or replaces one with the same id). */
    suspend fun upsertCustomTheme(entry: CustomThemeEntry) = update { data ->
        val withoutExisting = data.customThemes.filterNot { it.id == entry.id }
        data.copy(customThemes = withoutExisting + entry)
    }

    suspend fun renameCustomTheme(id: String, newName: String) = update { data ->
        data.copy(customThemes = data.customThemes.map { if (it.id == id) it.copy(name = newName) else it })
    }

    suspend fun deleteCustomTheme(id: String) = update { data ->
        data.copy(customThemes = data.customThemes.filterNot { it.id == id })
    }

    /**
     * Replaces every saved theme at once -- both overrides and standalone themes.
     *
     * Backup import's half of the two-store write, and its own rollback. The whole blob is one
     * DataStore value, so this is a single atomic replacement by construction.
     *
     * **`open`, and this one method only** (v4.6): [BackupRepository]'s rollback path is only
     * reachable when one of the two stores fails, and there is no honest way to make a real
     * DataStore fail on demand. The instrumented test overrides this to throw. The class is `open`
     * for the same reason and for nothing else -- it is not an extension point, and the app has
     * exactly one implementation.
     */
    open suspend fun replaceAll(data: CustomThemeData) {
        replaceAllJson(data.toJsonString())
    }

    /**
     * Writes a saved-themes document verbatim.
     *
     * The import path uses this rather than [replaceAll] so the bytes applied here are **exactly**
     * the bytes staged in the preference store, not a second serialisation of a parsed copy. That is
     * what makes completing an interrupted import idempotent: re-applying the pending document is
     * bit-for-bit the same write, whether it happens now or on the next start. See
     * `WallpaperPrefs.Keys.PENDING_IMPORT_THEMES` (BCK-06).
     */
    open suspend fun replaceAllJson(json: String) {
        context.customThemeDataStore.edit { prefs ->
            prefs[Keys.DATA_JSON] = json
        }
    }

    companion object {
        /** New unique id for a fully independent custom theme. */
        fun newCustomThemeId(): String = "custom:${System.currentTimeMillis()}"
    }
}
