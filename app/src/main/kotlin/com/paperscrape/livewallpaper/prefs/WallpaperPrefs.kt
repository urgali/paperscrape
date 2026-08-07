package com.paperscrape.livewallpaper.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "paperscrape_prefs")

/** Immutable snapshot of all user-configurable wallpaper settings. */
data class WallpaperSettings(
    val themeId: String = "sunset",
    val syncWithRealTime: Boolean = true,
    val useLocationForSunTimes: Boolean = false,
    val fixedHour: Float = 18f, // used only when syncWithRealTime == false
    val touchEffectsEnabled: Boolean = true,
    val parallaxStrength: Float = 1f, // 0.5 .. 2.0
    val autoThemeByDate: Boolean = false, // opt-in: overrides themeId during known seasonal windows
)

class WallpaperPrefs(private val context: Context) {

    private object Keys {
        val THEME_ID = stringPreferencesKey("theme_id")
        val SYNC_REAL_TIME = booleanPreferencesKey("sync_real_time")
        val USE_LOCATION = booleanPreferencesKey("use_location")
        val FIXED_HOUR = floatPreferencesKey("fixed_hour")
        val TOUCH_EFFECTS = booleanPreferencesKey("touch_effects")
        val PARALLAX_STRENGTH = floatPreferencesKey("parallax_strength")
        val AUTO_THEME_BY_DATE = booleanPreferencesKey("auto_theme_by_date")
    }

    val settingsFlow: Flow<WallpaperSettings> = context.dataStore.data.map { prefs ->
        WallpaperSettings(
            themeId = prefs[Keys.THEME_ID] ?: "sunset",
            syncWithRealTime = prefs[Keys.SYNC_REAL_TIME] ?: true,
            useLocationForSunTimes = prefs[Keys.USE_LOCATION] ?: false,
            fixedHour = prefs[Keys.FIXED_HOUR] ?: 18f,
            touchEffectsEnabled = prefs[Keys.TOUCH_EFFECTS] ?: true,
            parallaxStrength = prefs[Keys.PARALLAX_STRENGTH] ?: 1f,
            autoThemeByDate = prefs[Keys.AUTO_THEME_BY_DATE] ?: false,
        )
    }

    suspend fun setTheme(themeId: String) = context.dataStore.edit { it[Keys.THEME_ID] = themeId }

    suspend fun setSyncWithRealTime(enabled: Boolean) =
        context.dataStore.edit { it[Keys.SYNC_REAL_TIME] = enabled }

    suspend fun setUseLocation(enabled: Boolean) =
        context.dataStore.edit { it[Keys.USE_LOCATION] = enabled }

    suspend fun setFixedHour(hour: Float) = context.dataStore.edit { it[Keys.FIXED_HOUR] = hour }

    suspend fun setTouchEffects(enabled: Boolean) =
        context.dataStore.edit { it[Keys.TOUCH_EFFECTS] = enabled }

    suspend fun setParallaxStrength(strength: Float) =
        context.dataStore.edit { it[Keys.PARALLAX_STRENGTH] = strength }

    suspend fun setAutoThemeByDate(enabled: Boolean) =
        context.dataStore.edit { it[Keys.AUTO_THEME_BY_DATE] = enabled }
}
