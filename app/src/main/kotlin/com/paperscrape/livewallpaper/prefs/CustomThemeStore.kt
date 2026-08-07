package com.paperscrape.livewallpaper.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.CustomThemeEntry
import com.paperscrape.livewallpaper.engine.customThemeDataFromJsonString
import com.paperscrape.livewallpaper.engine.toJsonString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.customThemeDataStore by preferencesDataStore(name = "paperscrape_custom_themes")

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

    val dataFlow: Flow<CustomThemeData> = context.customThemeDataStore.data.map { prefs ->
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

    companion object {
        /** New unique id for a fully independent custom theme. */
        fun newCustomThemeId(): String = "custom:${System.currentTimeMillis()}"
    }
}
