package com.paperscrape.livewallpaper.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.paperscrape.livewallpaper.prefs.PrefsRecovery.recoveringFromReadErrors
import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.CustomThemeEntry
import com.paperscrape.livewallpaper.engine.customThemeDataFromJsonString
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
class CustomThemeStore(private val context: Context) {

    private object Keys {
        val DATA_JSON = stringPreferencesKey("custom_theme_data_json")
    }

    val dataFlow: Flow<CustomThemeData> = context.customThemeDataStore.data
        .recoveringFromReadErrors()
        .map { prefs ->
            customThemeDataFromJsonString(prefs[Keys.DATA_JSON])
        }

    private suspend fun update(transform: (CustomThemeData) -> CustomThemeData) {
        context.customThemeDataStore.edit { prefs ->
            val current = customThemeDataFromJsonString(prefs[Keys.DATA_JSON])
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
     */
    suspend fun replaceAll(data: CustomThemeData) = context.customThemeDataStore.edit { prefs ->
        prefs[Keys.DATA_JSON] = data.toJsonString()
    }

    companion object {
        /** New unique id for a fully independent custom theme. */
        fun newCustomThemeId(): String = "custom:${System.currentTimeMillis()}"
    }
}
