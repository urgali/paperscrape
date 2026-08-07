package com.paperscrape.livewallpaper.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.paperscrape.livewallpaper.engine.HouseBuildingConfig
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
    val houseBuildingConfig: HouseBuildingConfig = HouseBuildingConfig.DEFAULT,
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

        val SHOW_HOUSES = booleanPreferencesKey("show_houses")
        val SHOW_BUILDINGS = booleanPreferencesKey("show_buildings")
        val HOUSE_BUILDING_DENSITY = floatPreferencesKey("house_building_density")
        val HOUSE_COLOR_DAY_1 = intPreferencesKey("house_color_day_1")
        val HOUSE_COLOR_NIGHT_1 = intPreferencesKey("house_color_night_1")
        val HOUSE_COLOR_DAY_2 = intPreferencesKey("house_color_day_2")
        val HOUSE_COLOR_NIGHT_2 = intPreferencesKey("house_color_night_2")
        val BUILDING_COLOR_DAY_1 = intPreferencesKey("building_color_day_1")
        val BUILDING_COLOR_NIGHT_1 = intPreferencesKey("building_color_night_1")
        val BUILDING_COLOR_DAY_2 = intPreferencesKey("building_color_day_2")
        val BUILDING_COLOR_NIGHT_2 = intPreferencesKey("building_color_night_2")
    }

    val settingsFlow: Flow<WallpaperSettings> = context.dataStore.data.map { prefs ->
        val defaults = HouseBuildingConfig.DEFAULT
        WallpaperSettings(
            themeId = prefs[Keys.THEME_ID] ?: "sunset",
            syncWithRealTime = prefs[Keys.SYNC_REAL_TIME] ?: true,
            useLocationForSunTimes = prefs[Keys.USE_LOCATION] ?: false,
            fixedHour = prefs[Keys.FIXED_HOUR] ?: 18f,
            touchEffectsEnabled = prefs[Keys.TOUCH_EFFECTS] ?: true,
            parallaxStrength = prefs[Keys.PARALLAX_STRENGTH] ?: 1f,
            autoThemeByDate = prefs[Keys.AUTO_THEME_BY_DATE] ?: false,
            houseBuildingConfig = HouseBuildingConfig(
                showHouses = prefs[Keys.SHOW_HOUSES] ?: defaults.showHouses,
                showBuildings = prefs[Keys.SHOW_BUILDINGS] ?: defaults.showBuildings,
                density = prefs[Keys.HOUSE_BUILDING_DENSITY] ?: defaults.density,
                houseColorDay1 = prefs[Keys.HOUSE_COLOR_DAY_1] ?: defaults.houseColorDay1,
                houseColorNight1 = prefs[Keys.HOUSE_COLOR_NIGHT_1] ?: defaults.houseColorNight1,
                houseColorDay2 = prefs[Keys.HOUSE_COLOR_DAY_2] ?: defaults.houseColorDay2,
                houseColorNight2 = prefs[Keys.HOUSE_COLOR_NIGHT_2] ?: defaults.houseColorNight2,
                buildingColorDay1 = prefs[Keys.BUILDING_COLOR_DAY_1] ?: defaults.buildingColorDay1,
                buildingColorNight1 = prefs[Keys.BUILDING_COLOR_NIGHT_1] ?: defaults.buildingColorNight1,
                buildingColorDay2 = prefs[Keys.BUILDING_COLOR_DAY_2] ?: defaults.buildingColorDay2,
                buildingColorNight2 = prefs[Keys.BUILDING_COLOR_NIGHT_2] ?: defaults.buildingColorNight2,
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

    suspend fun setShowHouses(enabled: Boolean) =
        context.dataStore.edit { it[Keys.SHOW_HOUSES] = enabled }

    suspend fun setShowBuildings(enabled: Boolean) =
        context.dataStore.edit { it[Keys.SHOW_BUILDINGS] = enabled }

    suspend fun setHouseBuildingDensity(density: Float) =
        context.dataStore.edit { it[Keys.HOUSE_BUILDING_DENSITY] = density }

    suspend fun setHouseColorDay1(color: Int) = context.dataStore.edit { it[Keys.HOUSE_COLOR_DAY_1] = color }
    suspend fun setHouseColorNight1(color: Int) = context.dataStore.edit { it[Keys.HOUSE_COLOR_NIGHT_1] = color }
    suspend fun setHouseColorDay2(color: Int) = context.dataStore.edit { it[Keys.HOUSE_COLOR_DAY_2] = color }
    suspend fun setHouseColorNight2(color: Int) = context.dataStore.edit { it[Keys.HOUSE_COLOR_NIGHT_2] = color }
    suspend fun setBuildingColorDay1(color: Int) = context.dataStore.edit { it[Keys.BUILDING_COLOR_DAY_1] = color }
    suspend fun setBuildingColorNight1(color: Int) = context.dataStore.edit { it[Keys.BUILDING_COLOR_NIGHT_1] = color }
    suspend fun setBuildingColorDay2(color: Int) = context.dataStore.edit { it[Keys.BUILDING_COLOR_DAY_2] = color }
    suspend fun setBuildingColorNight2(color: Int) = context.dataStore.edit { it[Keys.BUILDING_COLOR_NIGHT_2] = color }

    /** Resets all house/building settings (visibility, density, colors) back to defaults. */
    suspend fun resetHouseBuildingConfig() = context.dataStore.edit { prefs ->
        prefs.remove(Keys.SHOW_HOUSES)
        prefs.remove(Keys.SHOW_BUILDINGS)
        prefs.remove(Keys.HOUSE_BUILDING_DENSITY)
        prefs.remove(Keys.HOUSE_COLOR_DAY_1)
        prefs.remove(Keys.HOUSE_COLOR_NIGHT_1)
        prefs.remove(Keys.HOUSE_COLOR_DAY_2)
        prefs.remove(Keys.HOUSE_COLOR_NIGHT_2)
        prefs.remove(Keys.BUILDING_COLOR_DAY_1)
        prefs.remove(Keys.BUILDING_COLOR_NIGHT_1)
        prefs.remove(Keys.BUILDING_COLOR_DAY_2)
        prefs.remove(Keys.BUILDING_COLOR_NIGHT_2)
    }
}
