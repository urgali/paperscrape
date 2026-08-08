package com.paperscrape.livewallpaper.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.paperscrape.livewallpaper.engine.ObjectVariantConfig
import com.paperscrape.livewallpaper.engine.SceneCustomization
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
    val sceneCustomization: SceneCustomization = SceneCustomization.DEFAULT,
)

/** Object categories that can be individually customized (visibility, density, 2x day/night colors). */
enum class ObjectCategory { HOUSES, BUILDINGS, DOGS, CARS, PARASOLS, TREES }

class WallpaperPrefs(private val context: Context) {

    private object Keys {
        val THEME_ID = stringPreferencesKey("theme_id")
        val SYNC_REAL_TIME = booleanPreferencesKey("sync_real_time")
        val USE_LOCATION = booleanPreferencesKey("use_location")
        val FIXED_HOUR = floatPreferencesKey("fixed_hour")
        val TOUCH_EFFECTS = booleanPreferencesKey("touch_effects")
        val PARALLAX_STRENGTH = floatPreferencesKey("parallax_strength")
        val AUTO_THEME_BY_DATE = booleanPreferencesKey("auto_theme_by_date")

        fun visible(category: ObjectCategory) = booleanPreferencesKey("obj_${category.name}_visible")
        fun density(category: ObjectCategory) = floatPreferencesKey("obj_${category.name}_density")
        fun colorDay1(category: ObjectCategory) = intPreferencesKey("obj_${category.name}_color_day_1")
        fun colorNight1(category: ObjectCategory) = intPreferencesKey("obj_${category.name}_color_night_1")
        fun colorDay2(category: ObjectCategory) = intPreferencesKey("obj_${category.name}_color_day_2")
        fun colorNight2(category: ObjectCategory) = intPreferencesKey("obj_${category.name}_color_night_2")
    }

    private fun readVariantConfig(prefs: Preferences, category: ObjectCategory, default: ObjectVariantConfig): ObjectVariantConfig =
        ObjectVariantConfig(
            visible = prefs[Keys.visible(category)] ?: default.visible,
            density = prefs[Keys.density(category)] ?: default.density,
            colorDay1 = prefs[Keys.colorDay1(category)] ?: default.colorDay1,
            colorNight1 = prefs[Keys.colorNight1(category)] ?: default.colorNight1,
            colorDay2 = prefs[Keys.colorDay2(category)] ?: default.colorDay2,
            colorNight2 = prefs[Keys.colorNight2(category)] ?: default.colorNight2,
        )

    val settingsFlow: Flow<WallpaperSettings> = context.dataStore.data.map { prefs ->
        val defaults = SceneCustomization.DEFAULT
        WallpaperSettings(
            themeId = prefs[Keys.THEME_ID] ?: "sunset",
            syncWithRealTime = prefs[Keys.SYNC_REAL_TIME] ?: true,
            useLocationForSunTimes = prefs[Keys.USE_LOCATION] ?: false,
            fixedHour = prefs[Keys.FIXED_HOUR] ?: 18f,
            touchEffectsEnabled = prefs[Keys.TOUCH_EFFECTS] ?: true,
            parallaxStrength = prefs[Keys.PARALLAX_STRENGTH] ?: 1f,
            autoThemeByDate = prefs[Keys.AUTO_THEME_BY_DATE] ?: false,
            sceneCustomization = SceneCustomization(
                houses = readVariantConfig(prefs, ObjectCategory.HOUSES, defaults.houses),
                buildings = readVariantConfig(prefs, ObjectCategory.BUILDINGS, defaults.buildings),
                dogs = readVariantConfig(prefs, ObjectCategory.DOGS, defaults.dogs),
                cars = readVariantConfig(prefs, ObjectCategory.CARS, defaults.cars),
                parasols = readVariantConfig(prefs, ObjectCategory.PARASOLS, defaults.parasols),
                trees = readVariantConfig(prefs, ObjectCategory.TREES, defaults.trees),
            ),
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

    suspend fun setCategoryVisible(category: ObjectCategory, visible: Boolean) =
        context.dataStore.edit { it[Keys.visible(category)] = visible }

    suspend fun setCategoryDensity(category: ObjectCategory, density: Float) =
        context.dataStore.edit { it[Keys.density(category)] = density }

    suspend fun setCategoryColorDay1(category: ObjectCategory, color: Int) =
        context.dataStore.edit { it[Keys.colorDay1(category)] = color }

    suspend fun setCategoryColorNight1(category: ObjectCategory, color: Int) =
        context.dataStore.edit { it[Keys.colorNight1(category)] = color }

    suspend fun setCategoryColorDay2(category: ObjectCategory, color: Int) =
        context.dataStore.edit { it[Keys.colorDay2(category)] = color }

    suspend fun setCategoryColorNight2(category: ObjectCategory, color: Int) =
        context.dataStore.edit { it[Keys.colorNight2(category)] = color }

    /** Resets one category's visibility/density/colors back to defaults. */
    suspend fun resetCategory(category: ObjectCategory) = context.dataStore.edit { prefs ->
        prefs.remove(Keys.visible(category))
        prefs.remove(Keys.density(category))
        prefs.remove(Keys.colorDay1(category))
        prefs.remove(Keys.colorNight1(category))
        prefs.remove(Keys.colorDay2(category))
        prefs.remove(Keys.colorNight2(category))
    }

    /** Resets every object category back to defaults in one go. */
    suspend fun resetAllCategories() = context.dataStore.edit { prefs ->
        for (category in ObjectCategory.entries) {
            prefs.remove(Keys.visible(category))
            prefs.remove(Keys.density(category))
            prefs.remove(Keys.colorDay1(category))
            prefs.remove(Keys.colorNight1(category))
            prefs.remove(Keys.colorDay2(category))
            prefs.remove(Keys.colorNight2(category))
        }
    }
}
