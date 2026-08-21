package com.paperscrape.livewallpaper.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.paperscrape.livewallpaper.engine.ObjectVariantConfig
import com.paperscrape.livewallpaper.engine.PeopleDensity
import com.paperscrape.livewallpaper.engine.MountainLayerConfig
import com.paperscrape.livewallpaper.engine.LakeConfig
import com.paperscrape.livewallpaper.engine.BirdsConfig
import com.paperscrape.livewallpaper.engine.BirdColorWeight
import com.paperscrape.livewallpaper.engine.StarsConfig
import com.paperscrape.livewallpaper.engine.SkyConfig
import com.paperscrape.livewallpaper.engine.SunConfig
import com.paperscrape.livewallpaper.engine.MoonConfig
import com.paperscrape.livewallpaper.engine.CloudsConfig
import com.paperscrape.livewallpaper.engine.PrecipitationConfig
import com.paperscrape.livewallpaper.engine.PrecipitationType
import com.paperscrape.livewallpaper.engine.RainbowConfig
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import com.paperscrape.livewallpaper.weather.LiveWeatherStatus
import com.paperscrape.livewallpaper.weather.WeatherProviderId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "paperscrape_prefs")

/** Immutable snapshot of all user-configurable wallpaper settings. */
data class WallpaperSettings(
    val themeId: String = "sunset",
    val syncWithRealTime: Boolean = true,
    val useLocationForSunTimes: Boolean = false,
    // Mutually exclusive with useLocationForSunTimes above -- a manually-entered fixed
    // coordinate instead of the phone's real GPS/network fix. Same downstream consumers (sunrise/
    // sunset now, Live Weather once point 6 lands) read whichever of the two is actually active;
    // see WallpaperPrefs.setUseCustomLocation/setUseLocation for how the exclusivity is enforced.
    val useCustomLocation: Boolean = false,
    val customLocationLatitude: Float = 45.4642f, // Milan -- an arbitrary but real default so a
    val customLocationLongitude: Float = 9.19f, // freshly-enabled toggle isn't at (0,0) in the ocean
    val customLocationLabel: String = "",
    // Live Weather: global (not per-theme, like useLocationForSunTimes/useCustomLocation above)
    // since it needs one of those two location toggles active to know where to fetch conditions
    // for. liveWeatherApiKey is the user's own Open-Meteo key, if they entered one -- always
    // takes priority over the build's own baked-in key (see WeatherRepository.resolveApiKey);
    // blank means "use the app's own key, or the free keyless tier if that's blank too".
    val liveWeatherEnabled: Boolean = false,
    val liveWeatherApiKey: String = "",
    /**
     * Which service Live Weather fetches from, stored by
     * [com.paperscrape.livewallpaper.weather.WeatherProviderId.storageId].
     *
     * A string rather than the enum so that reordering the enum cannot reinterpret an existing
     * install's choice, and an unrecognised value reads as the default instead of crashing
     * forward. Separate from every other weather setting on purpose: changing provider must not
     * disturb the location, the toggle, or the other provider's key.
     */
    val weatherProviderId: String = WeatherProviderId.DEFAULT.storageId,
    /**
     * The user's Visual Crossing key. Never compiled in, never logged, never sent anywhere but
     * Visual Crossing -- unlike Open-Meteo's, whose free tier makes a shipped key sensible.
     *
     * Kept apart from [liveWeatherApiKey] so that switching provider and back does not lose
     * either one.
     */
    val visualCrossingApiKey: String = "",
    /**
     * What Live Weather is actually doing, as a [LiveWeatherStatus.storageId].
     *
     * v2.13 had a boolean here that could only say "running on the theme's own weather". With a
     * provider that can require an API key there are now reasons the settings screen must tell
     * apart -- a missing key is one tap from fixed, a dropped request is not -- so this carries
     * the reason rather than the symptom.
     *
     * **Written by the wallpaper service, read by the settings screen** -- the only direction any
     * of these flow, and the reason it is here rather than in a separate store: the settings flow
     * already reaches the UI without a restart, so a status published through it appears the
     * moment it changes. It is not a user preference and nothing in the UI sets it.
     */
    val liveWeatherStatus: String = LiveWeatherStatus.OFF.storageId,
    /**
     * Whether opening the settings screen may check GitHub for a new release.
     *
     * Off by default, and deliberately: the check used to run on every open, which is a network
     * request the user never asked for. The manual button is always available, so opting out costs
     * nothing but the reminder.
     */
    val automaticUpdateCheckEnabled: Boolean = false,
    // The GPS-derived coordinates PaperWallpaperService actually resolved (written by it via
    // WallpaperPrefs.setResolvedGpsLocation whenever a fix arrives) -- separate from
    // customLocationLatitude/Longitude above, which the *user* entered directly and therefore
    // never needs a round trip through the wallpaper service to know. Settings reverse-geocodes
    // whichever of the two is actually active into a city label (see SettingsScreen's
    // LocationLabel composable) -- aa's own explicit ask was that both toggles show *which place*
    // they resolved to, not just raw coordinates. Null until the very first GPS fix arrives.
    val resolvedGpsLatitude: Float? = null,
    val resolvedGpsLongitude: Float? = null,
    val fixedHour: Float = 18f, // used only when syncWithRealTime == false
    val parallaxStrength: Float = 1f, // 0.5 .. 2.0 -- also labeled "Scroll Speed" in the UI's
    // Scrolling section: this is the same underlying mechanism (how much the scenery shifts per
    // unit of home-screen swipe) a reference app's own decompiled source uses for its own
    // "Scroll Speed" setting, so it's reused here rather than adding a second, redundant slider.
    // Scroll behavior below is deliberately global (not per-theme), matching that same reference
    // app's convention observed for at least Swipe Scroll (`saveWithTheme = false` in its
    // decompiled source) -- these are interaction/engine preferences, not part of a theme's
    // visual identity the way hill colors or which decorations are visible are.
    val scrollBackground: Boolean = false, // whether sun/moon/sky scroll with the parallax hills
    val swipeScroll: Boolean = true, // whether swiping between home screens scrolls the wallpaper at all
    // Continuous auto-scroll, independent of swiping entirely -- confirmed against a reference
    // app's decompiled source: its `scrollSpeed` multiplies a per-frame *time delta*
    // (`onUpdate(float f)`, a classic game-loop pattern), not a swipe offset -- a genuinely
    // different mechanism from PaperScrape's existing `parallaxStrength` (which scales how far
    // layers move *relative to swiping*). Defaults to a slow constant drift (matching that
    // reference's own "Very Slow" default), not off -- this is an engine-level behavior in the
    // same spirit as parallaxStrength already defaulting to on, not an opt-in decorative extra.
    val scrollSpeed: Float = 0.15f,
    val autoThemeByDate: Boolean = false, // opt-in: overrides themeId during known seasonal windows
    /** In-progress (not yet saved) scene-object edits, and which theme they belong to. Only
     * applied when [pendingCustomizationThemeId] matches the theme actually being rendered --
     * see [com.paperscrape.livewallpaper.engine.CustomThemeRegistry.resolveActiveCustomization].
     * Switching to a different theme does *not* clear these (so switching back mid-edit resumes
     * where you left off), but they simply won't apply anywhere except that one theme. */
    val pendingCustomization: SceneCustomization = SceneCustomization.DEFAULT,
    val pendingCustomizationThemeId: String? = null,
) {

    /** [weatherProviderId] resolved; an unrecognised stored id reads as the default. */
    val weatherProvider: WeatherProviderId
        get() = WeatherProviderId.fromStorageId(weatherProviderId)

    /**
     * The key the **selected** provider should be called with.
     *
     * Each provider keeps its own, so switching back and forth loses neither. Open-Meteo's may be
     * blank, which its free keyless tier accepts; Visual Crossing's may not, and a blank one there
     * is what produces [com.paperscrape.livewallpaper.weather.WeatherFetchResult.MissingApiKey]
     * instead of a request.
     */
    val apiKeyForWeatherProvider: String
        get() = when (weatherProvider) {
            WeatherProviderId.OPEN_METEO -> liveWeatherApiKey
            WeatherProviderId.VISUAL_CROSSING -> visualCrossingApiKey
        }

    /** [liveWeatherStatus] resolved. */
    val liveWeather: LiveWeatherStatus
        get() = LiveWeatherStatus.fromStorageId(liveWeatherStatus)
}

/** Object categories that can be individually customized (visibility, density, 2x day/night
 * colors). The first 5 are structural (houses/buildings/cars/parasols/trees); the rest are
 * seasonal decorations (snowmen, gifts, etc.) -- both groups are edited the same way, per-theme,
 * via [WallpaperSettings.pendingCustomization], just from two different settings screens ("Scene
 * Objects" and "Seasonal Decorations" respectively). The only real difference between the two
 * groups is their *default*: structural categories share one flat default everywhere (see
 * [SceneCustomization.DEFAULT]), while seasonal categories get a theme-specific starting point
 * (see [defaultCustomizationFor]) so e.g. Christmas still has snowmen out of the box. */
enum class ObjectCategory {
    HOUSES, BUILDINGS, CARS, PARASOLS, TREES,
    // People are a category for visibility and density only. They have no colour: their artwork
    // is finished, four kinds across two seasons, and a tint over it is the mistake
    // `DESIGN_NOTES.md` decision 25 exists to prevent. The four colour keys below exist for every
    // category because the storage is generic; nothing reads People's.
    PEOPLE,
    SNOWMEN, GIFTS, PENGUINS, BUNNIES, EASTER_EGGS, PUMPKINS,
}

class WallpaperPrefs(private val context: Context) {

    private object Keys {
        /**
         * Night-time pedestrian density. Absent for every install that predates v2.12, which is
         * what [PeopleDensity.resolveNightDensity] reads as "use the daytime value" -- see its own
         * comment on why that, and not a fresh default, is the right upgrade.
         */
        val PEOPLE_NIGHT_DENSITY = floatPreferencesKey("people_night_density")

        val THEME_ID = stringPreferencesKey("theme_id")
        val SYNC_REAL_TIME = booleanPreferencesKey("sync_real_time")
        val USE_LOCATION = booleanPreferencesKey("use_location")
        val USE_CUSTOM_LOCATION = booleanPreferencesKey("use_custom_location")
        val CUSTOM_LOCATION_LAT = floatPreferencesKey("custom_location_lat")
        val CUSTOM_LOCATION_LON = floatPreferencesKey("custom_location_lon")
        val CUSTOM_LOCATION_LABEL = stringPreferencesKey("custom_location_label")
        val LIVE_WEATHER_ENABLED = booleanPreferencesKey("live_weather_enabled")
        val LIVE_WEATHER_API_KEY = stringPreferencesKey("live_weather_api_key")
        val WEATHER_PROVIDER = stringPreferencesKey("weather_provider")
        val VISUAL_CROSSING_API_KEY = stringPreferencesKey("visual_crossing_api_key")
        val LIVE_WEATHER_STATUS = stringPreferencesKey("live_weather_status")
        val AUTOMATIC_UPDATE_CHECK = booleanPreferencesKey("automatic_update_check")
        val RESOLVED_GPS_LAT = floatPreferencesKey("resolved_gps_lat")
        val RESOLVED_GPS_LON = floatPreferencesKey("resolved_gps_lon")
        val FIXED_HOUR = floatPreferencesKey("fixed_hour")
        val PARALLAX_STRENGTH = floatPreferencesKey("parallax_strength")
        val AUTO_THEME_BY_DATE = booleanPreferencesKey("auto_theme_by_date")
        val PENDING_CUSTOMIZATION_THEME_ID = stringPreferencesKey("pending_customization_theme_id")

        fun visible(category: ObjectCategory) = booleanPreferencesKey("obj_${category.name}_visible")
        fun density(category: ObjectCategory) = floatPreferencesKey("obj_${category.name}_density")
        fun colorDay1(category: ObjectCategory) = intPreferencesKey("obj_${category.name}_color_day_1")
        fun colorNight1(category: ObjectCategory) = intPreferencesKey("obj_${category.name}_color_night_1")
        fun colorDay2(category: ObjectCategory) = intPreferencesKey("obj_${category.name}_color_day_2")
        fun colorNight2(category: ObjectCategory) = intPreferencesKey("obj_${category.name}_color_night_2")
        val HILLS_VARIATION = floatPreferencesKey("hills_variation")
        val HILLS_COLOR_DAY = intPreferencesKey("hills_color_day")
        val HILLS_COLOR_NIGHT = intPreferencesKey("hills_color_night")
        val SCROLL_BACKGROUND = booleanPreferencesKey("scroll_background")
        val SWIPE_SCROLL = booleanPreferencesKey("swipe_scroll")
        val SCROLL_SPEED = floatPreferencesKey("scroll_speed")
        fun mountainVisible(front: Boolean) = booleanPreferencesKey("mountain_${if (front) "front" else "back"}_visible")
        fun mountainDensity(front: Boolean) = floatPreferencesKey("mountain_${if (front) "front" else "back"}_density")
        fun mountainColorDay(front: Boolean) = intPreferencesKey("mountain_${if (front) "front" else "back"}_color_day")
        fun mountainColorNight(front: Boolean) = intPreferencesKey("mountain_${if (front) "front" else "back"}_color_night")
        val LAKE_VISIBLE = booleanPreferencesKey("lake_visible")
        val LAKE_COLOR_DAY = intPreferencesKey("lake_color_day")
        val LAKE_COLOR_NIGHT = intPreferencesKey("lake_color_night")
        val LAKE_HEIGHT = floatPreferencesKey("lake_height")
        val LAKE_SAILBOATS_VISIBLE = booleanPreferencesKey("lake_sailboats_visible")
        val LAKE_SAILBOATS_DENSITY = floatPreferencesKey("lake_sailboats_density")
        val LAKE_DOLPHINS_VISIBLE = booleanPreferencesKey("lake_dolphins_visible")
        val LAKE_DOLPHINS_DENSITY = floatPreferencesKey("lake_dolphins_density")
        val STARS_VISIBLE = booleanPreferencesKey("stars_visible")
        val STARS_DENSITY = floatPreferencesKey("stars_density")
        val SKY_COLOR_DAY_HIGH = intPreferencesKey("sky_color_day_high")
        val SKY_COLOR_DAY_LOW = intPreferencesKey("sky_color_day_low")
        val SKY_COLOR_NIGHT_HIGH = intPreferencesKey("sky_color_night_high")
        val SKY_COLOR_NIGHT_LOW = intPreferencesKey("sky_color_night_low")
        val SKY_COLOR_SUNRISE_LOW = intPreferencesKey("sky_color_sunrise_low")
        val SKY_COLOR_SUNSET_LOW = intPreferencesKey("sky_color_sunset_low")
        val SKY_SUN_CLOUD_HEIGHT = floatPreferencesKey("sky_sun_cloud_height")
        val SUN_VISIBLE = booleanPreferencesKey("sun_visible")
        val SUN_COLOR = intPreferencesKey("sun_color")
        val MOON_VISIBLE = booleanPreferencesKey("moon_visible")
        val MOON_COLOR = intPreferencesKey("moon_color")
        val MOON_REALISTIC_PHASES = booleanPreferencesKey("moon_realistic_phases")
        val CLOUDS_VISIBLE = booleanPreferencesKey("clouds_visible")
        val CLOUDS_DENSITY = floatPreferencesKey("clouds_density")
        val CLOUDS_COLOR_DAY = intPreferencesKey("clouds_color_day")
        val CLOUDS_COLOR_NIGHT = intPreferencesKey("clouds_color_night")
        val BIRDS_VISIBLE = booleanPreferencesKey("birds_visible")
        val BIRDS_DENSITY = floatPreferencesKey("birds_density")
        val BIRDS_NIGHT = booleanPreferencesKey("birds_night")
        fun birdColor(index: Int) = intPreferencesKey("birds_color_$index")
        fun birdWeight(index: Int) = floatPreferencesKey("birds_weight_$index")
        val PRECIPITATION_VISIBLE = booleanPreferencesKey("precipitation_visible")
        val PRECIPITATION_TYPE = stringPreferencesKey("precipitation_type")
        val PRECIPITATION_INTENSITY = floatPreferencesKey("precipitation_intensity")
        val PRECIPITATION_RAIN_COLOR_DAY = intPreferencesKey("precipitation_rain_color_day")
        val PRECIPITATION_RAIN_COLOR_NIGHT = intPreferencesKey("precipitation_rain_color_night")
        val PRECIPITATION_SNOW_COLOR_DAY = intPreferencesKey("precipitation_snow_color_day")
        val PRECIPITATION_SNOW_COLOR_NIGHT = intPreferencesKey("precipitation_snow_color_night")
        val PRECIPITATION_THUNDERSTORM = booleanPreferencesKey("precipitation_thunderstorm")
        val RAINBOW_VISIBLE = booleanPreferencesKey("rainbow_visible")
        val RAINBOW_OPACITY = floatPreferencesKey("rainbow_opacity")
        val FALL_COLORS_ENABLED = booleanPreferencesKey("fall_colors_enabled")
        val WINTER_COLORS_ENABLED = booleanPreferencesKey("winter_colors_enabled")
        val CHRISTMAS_DECORATIONS_ENABLED = booleanPreferencesKey("christmas_decorations_enabled")
        val FLOWERS_ENABLED = booleanPreferencesKey("flowers_enabled")
        val HALLOWEEN_ENABLED = booleanPreferencesKey("halloween_enabled")
        val HORROR_SKY_ENABLED = booleanPreferencesKey("horror_sky_enabled")
        val SANTA_ENABLED = booleanPreferencesKey("santa_enabled")
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
        // Falls back to whichever theme's pending edit is currently tagged (see
        // PENDING_CUSTOMIZATION_THEME_ID) -- crucial for seasonal categories specifically:
        // editing just one (e.g. turning snowmen off for Christmas) must NOT silently reset every
        // *other* untouched category (gifts, houses, ...) back to a flat, theme-agnostic default;
        // they should keep reading as Christmas's own defaults until the user explicitly changes
        // them too. Falls back to "sunset" (an all-off theme) when nothing is being edited right
        // now, which is safe since pendingCustomization is only ever consulted by
        // CustomThemeRegistry.resolveActiveCustomization when its own themeId actually matches
        // this tag.
        val defaults = defaultCustomizationFor(prefs[Keys.PENDING_CUSTOMIZATION_THEME_ID] ?: "sunset")
        WallpaperSettings(
            themeId = prefs[Keys.THEME_ID] ?: "sunset",
            syncWithRealTime = prefs[Keys.SYNC_REAL_TIME] ?: true,
            useLocationForSunTimes = prefs[Keys.USE_LOCATION] ?: false,
            useCustomLocation = prefs[Keys.USE_CUSTOM_LOCATION] ?: false,
            customLocationLatitude = prefs[Keys.CUSTOM_LOCATION_LAT] ?: 45.4642f,
            customLocationLongitude = prefs[Keys.CUSTOM_LOCATION_LON] ?: 9.19f,
            customLocationLabel = prefs[Keys.CUSTOM_LOCATION_LABEL] ?: "",
            liveWeatherEnabled = prefs[Keys.LIVE_WEATHER_ENABLED] ?: false,
            liveWeatherApiKey = prefs[Keys.LIVE_WEATHER_API_KEY] ?: "",
            weatherProviderId = prefs[Keys.WEATHER_PROVIDER] ?: WeatherProviderId.DEFAULT.storageId,
            visualCrossingApiKey = prefs[Keys.VISUAL_CROSSING_API_KEY] ?: "",
            liveWeatherStatus = prefs[Keys.LIVE_WEATHER_STATUS] ?: LiveWeatherStatus.OFF.storageId,
            automaticUpdateCheckEnabled = prefs[Keys.AUTOMATIC_UPDATE_CHECK] ?: false,
            resolvedGpsLatitude = prefs[Keys.RESOLVED_GPS_LAT],
            resolvedGpsLongitude = prefs[Keys.RESOLVED_GPS_LON],
            fixedHour = prefs[Keys.FIXED_HOUR] ?: 18f,
            parallaxStrength = prefs[Keys.PARALLAX_STRENGTH] ?: 1f,
            scrollBackground = prefs[Keys.SCROLL_BACKGROUND] ?: false,
            swipeScroll = prefs[Keys.SWIPE_SCROLL] ?: true,
            scrollSpeed = prefs[Keys.SCROLL_SPEED] ?: 0.15f,
            autoThemeByDate = prefs[Keys.AUTO_THEME_BY_DATE] ?: false,
            pendingCustomizationThemeId = prefs[Keys.PENDING_CUSTOMIZATION_THEME_ID],
            pendingCustomization = SceneCustomization(
                houses = readVariantConfig(prefs, ObjectCategory.HOUSES, defaults.houses),
                buildings = readVariantConfig(prefs, ObjectCategory.BUILDINGS, defaults.buildings),
                cars = readVariantConfig(prefs, ObjectCategory.CARS, defaults.cars),
                parasols = readVariantConfig(prefs, ObjectCategory.PARASOLS, defaults.parasols),
            people = readVariantConfig(prefs, ObjectCategory.PEOPLE, defaults.people),
            peopleNightDensity = PeopleDensity.resolveNightDensity(
                stored = prefs[Keys.PEOPLE_NIGHT_DENSITY],
                dayDensity = prefs[Keys.density(ObjectCategory.PEOPLE)] ?: defaults.people.density,
            ),
                trees = readVariantConfig(prefs, ObjectCategory.TREES, defaults.trees),
                snowmen = readVariantConfig(prefs, ObjectCategory.SNOWMEN, defaults.snowmen),
                gifts = readVariantConfig(prefs, ObjectCategory.GIFTS, defaults.gifts),
                penguins = readVariantConfig(prefs, ObjectCategory.PENGUINS, defaults.penguins),
                bunnies = readVariantConfig(prefs, ObjectCategory.BUNNIES, defaults.bunnies),
                easterEggs = readVariantConfig(prefs, ObjectCategory.EASTER_EGGS, defaults.easterEggs),
                pumpkins = readVariantConfig(prefs, ObjectCategory.PUMPKINS, defaults.pumpkins),
                hillsVariation = prefs[Keys.HILLS_VARIATION] ?: defaults.hillsVariation,
                hillsColorDay = prefs[Keys.HILLS_COLOR_DAY] ?: defaults.hillsColorDay,
                hillsColorNight = prefs[Keys.HILLS_COLOR_NIGHT] ?: defaults.hillsColorNight,
                mountainsFront = MountainLayerConfig(
                    visible = prefs[Keys.mountainVisible(true)] ?: defaults.mountainsFront.visible,
                    density = prefs[Keys.mountainDensity(true)] ?: defaults.mountainsFront.density,
                    colorDay = prefs[Keys.mountainColorDay(true)] ?: defaults.mountainsFront.colorDay,
                    colorNight = prefs[Keys.mountainColorNight(true)] ?: defaults.mountainsFront.colorNight,
                ),
                mountainsBack = MountainLayerConfig(
                    visible = prefs[Keys.mountainVisible(false)] ?: defaults.mountainsBack.visible,
                    density = prefs[Keys.mountainDensity(false)] ?: defaults.mountainsBack.density,
                    colorDay = prefs[Keys.mountainColorDay(false)] ?: defaults.mountainsBack.colorDay,
                    colorNight = prefs[Keys.mountainColorNight(false)] ?: defaults.mountainsBack.colorNight,
                ),
                lake = LakeConfig(
                    visible = prefs[Keys.LAKE_VISIBLE] ?: defaults.lake.visible,
                    colorDay = prefs[Keys.LAKE_COLOR_DAY] ?: defaults.lake.colorDay,
                    colorNight = prefs[Keys.LAKE_COLOR_NIGHT] ?: defaults.lake.colorNight,
                    height = prefs[Keys.LAKE_HEIGHT] ?: defaults.lake.height,
                    sailboatsVisible = prefs[Keys.LAKE_SAILBOATS_VISIBLE] ?: defaults.lake.sailboatsVisible,
                    sailboatsDensity = prefs[Keys.LAKE_SAILBOATS_DENSITY] ?: defaults.lake.sailboatsDensity,
                    dolphinsVisible = prefs[Keys.LAKE_DOLPHINS_VISIBLE] ?: defaults.lake.dolphinsVisible,
                    dolphinsDensity = prefs[Keys.LAKE_DOLPHINS_DENSITY] ?: defaults.lake.dolphinsDensity,
                ),
                stars = StarsConfig(
                    visible = prefs[Keys.STARS_VISIBLE] ?: defaults.stars.visible,
                    density = prefs[Keys.STARS_DENSITY] ?: defaults.stars.density,
                ),
                sky = SkyConfig(
                    colorDayHigh = prefs[Keys.SKY_COLOR_DAY_HIGH] ?: defaults.sky.colorDayHigh,
                    colorDayLow = prefs[Keys.SKY_COLOR_DAY_LOW] ?: defaults.sky.colorDayLow,
                    colorNightHigh = prefs[Keys.SKY_COLOR_NIGHT_HIGH] ?: defaults.sky.colorNightHigh,
                    colorNightLow = prefs[Keys.SKY_COLOR_NIGHT_LOW] ?: defaults.sky.colorNightLow,
                    colorSunriseLow = prefs[Keys.SKY_COLOR_SUNRISE_LOW] ?: defaults.sky.colorSunriseLow,
                    colorSunsetLow = prefs[Keys.SKY_COLOR_SUNSET_LOW] ?: defaults.sky.colorSunsetLow,
                    sunCloudHeight = prefs[Keys.SKY_SUN_CLOUD_HEIGHT] ?: defaults.sky.sunCloudHeight,
                ),
                sun = SunConfig(
                    visible = prefs[Keys.SUN_VISIBLE] ?: defaults.sun.visible,
                    color = prefs[Keys.SUN_COLOR] ?: defaults.sun.color,
                ),
                moon = MoonConfig(
                    visible = prefs[Keys.MOON_VISIBLE] ?: defaults.moon.visible,
                    color = prefs[Keys.MOON_COLOR] ?: defaults.moon.color,
                    realisticPhases = prefs[Keys.MOON_REALISTIC_PHASES] ?: defaults.moon.realisticPhases,
                ),
                clouds = CloudsConfig(
                    visible = prefs[Keys.CLOUDS_VISIBLE] ?: defaults.clouds.visible,
                    density = prefs[Keys.CLOUDS_DENSITY] ?: defaults.clouds.density,
                    colorDay = prefs[Keys.CLOUDS_COLOR_DAY] ?: defaults.clouds.colorDay,
                    colorNight = prefs[Keys.CLOUDS_COLOR_NIGHT] ?: defaults.clouds.colorNight,
                ),
                birds = BirdsConfig(
                    visible = prefs[Keys.BIRDS_VISIBLE] ?: defaults.birds.visible,
                    density = prefs[Keys.BIRDS_DENSITY] ?: defaults.birds.density,
                    nightBirds = prefs[Keys.BIRDS_NIGHT] ?: defaults.birds.nightBirds,
                    colors = defaults.birds.colors.mapIndexed { index, default ->
                        BirdColorWeight(
                            color = prefs[Keys.birdColor(index)] ?: default.color,
                            weight = prefs[Keys.birdWeight(index)] ?: default.weight,
                        )
                    },
                ),
                precipitation = PrecipitationConfig(
                    visible = prefs[Keys.PRECIPITATION_VISIBLE] ?: defaults.precipitation.visible,
                    type = prefs[Keys.PRECIPITATION_TYPE]?.let { runCatching { PrecipitationType.valueOf(it) }.getOrNull() }
                        ?: defaults.precipitation.type,
                    intensity = prefs[Keys.PRECIPITATION_INTENSITY] ?: defaults.precipitation.intensity,
                    rainColorDay = prefs[Keys.PRECIPITATION_RAIN_COLOR_DAY] ?: defaults.precipitation.rainColorDay,
                    rainColorNight = prefs[Keys.PRECIPITATION_RAIN_COLOR_NIGHT] ?: defaults.precipitation.rainColorNight,
                    snowColorDay = prefs[Keys.PRECIPITATION_SNOW_COLOR_DAY] ?: defaults.precipitation.snowColorDay,
                    snowColorNight = prefs[Keys.PRECIPITATION_SNOW_COLOR_NIGHT] ?: defaults.precipitation.snowColorNight,
                    thunderstorm = prefs[Keys.PRECIPITATION_THUNDERSTORM] ?: defaults.precipitation.thunderstorm,
                ),
                rainbow = RainbowConfig(
                    visible = prefs[Keys.RAINBOW_VISIBLE] ?: defaults.rainbow.visible,
                    opacity = prefs[Keys.RAINBOW_OPACITY] ?: defaults.rainbow.opacity,
                ),
                fallColorsEnabled = prefs[Keys.FALL_COLORS_ENABLED] ?: defaults.fallColorsEnabled,
                winterColorsEnabled = prefs[Keys.WINTER_COLORS_ENABLED] ?: defaults.winterColorsEnabled,
                christmasDecorationsEnabled = prefs[Keys.CHRISTMAS_DECORATIONS_ENABLED] ?: defaults.christmasDecorationsEnabled,
                flowersEnabled = prefs[Keys.FLOWERS_ENABLED] ?: defaults.flowersEnabled,
                halloweenEnabled = prefs[Keys.HALLOWEEN_ENABLED] ?: defaults.halloweenEnabled,
                horrorSkyEnabled = prefs[Keys.HORROR_SKY_ENABLED] ?: defaults.horrorSkyEnabled,
                santaEnabled = prefs[Keys.SANTA_ENABLED] ?: defaults.santaEnabled,
            ),
        )
    }

    suspend fun setTheme(themeId: String) = context.dataStore.edit { it[Keys.THEME_ID] = themeId }

    suspend fun setSyncWithRealTime(enabled: Boolean) =
        context.dataStore.edit { it[Keys.SYNC_REAL_TIME] = enabled }

    /** Mutually exclusive with [setUseCustomLocation] -- enabling phone-location mode always
     * turns custom location off in the same edit. */
    suspend fun setUseLocation(enabled: Boolean) =
        context.dataStore.edit {
            it[Keys.USE_LOCATION] = enabled
            if (enabled) it[Keys.USE_CUSTOM_LOCATION] = false
        }

    /** Mutually exclusive with [setUseLocation] -- see that function's own doc comment. */
    suspend fun setUseCustomLocation(enabled: Boolean) =
        context.dataStore.edit {
            it[Keys.USE_CUSTOM_LOCATION] = enabled
            if (enabled) it[Keys.USE_LOCATION] = false
        }

    suspend fun setCustomLocation(latitude: Float, longitude: Float, label: String) =
        context.dataStore.edit {
            it[Keys.CUSTOM_LOCATION_LAT] = latitude
            it[Keys.CUSTOM_LOCATION_LON] = longitude
            it[Keys.CUSTOM_LOCATION_LABEL] = label
        }

    suspend fun setLiveWeatherEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.LIVE_WEATHER_ENABLED] = enabled }

    suspend fun setLiveWeatherApiKey(apiKey: String) =
        context.dataStore.edit { it[Keys.LIVE_WEATHER_API_KEY] = apiKey }

    /** Writes only the provider. Nothing else about Live Weather or the location is touched. */
    suspend fun setWeatherProvider(provider: WeatherProviderId) =
        context.dataStore.edit { it[Keys.WEATHER_PROVIDER] = provider.storageId }

    suspend fun setVisualCrossingApiKey(apiKey: String) =
        context.dataStore.edit { it[Keys.VISUAL_CROSSING_API_KEY] = apiKey }

    /**
     * Records whether Live Weather has fallen back to the theme's manual weather.
     *
     * Called from the wallpaper service, never from the UI. Writing it re-emits the settings flow,
     * which is how the settings screen learns about it without polling or a restart -- so it is
     * only written when the value actually changes, or every evaluation of the weather loop would
     * wake every collector for nothing.
     */
    suspend fun setAutomaticUpdateCheckEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.AUTOMATIC_UPDATE_CHECK] = enabled }

    suspend fun setLiveWeatherStatus(status: LiveWeatherStatus) =
        context.dataStore.edit { it[Keys.LIVE_WEATHER_STATUS] = status.storageId }

    suspend fun setResolvedGpsLocation(latitude: Float, longitude: Float) =
        context.dataStore.edit {
            it[Keys.RESOLVED_GPS_LAT] = latitude
            it[Keys.RESOLVED_GPS_LON] = longitude
        }

    suspend fun setFixedHour(hour: Float) = context.dataStore.edit { it[Keys.FIXED_HOUR] = hour }

    suspend fun setParallaxStrength(strength: Float) =
        context.dataStore.edit { it[Keys.PARALLAX_STRENGTH] = strength }

    suspend fun setScrollBackground(enabled: Boolean) =
        context.dataStore.edit { it[Keys.SCROLL_BACKGROUND] = enabled }

    suspend fun setSwipeScroll(enabled: Boolean) =
        context.dataStore.edit { it[Keys.SWIPE_SCROLL] = enabled }

    suspend fun setScrollSpeed(speed: Float) =
        context.dataStore.edit { it[Keys.SCROLL_SPEED] = speed }

    suspend fun setAutoThemeByDate(enabled: Boolean) =
        context.dataStore.edit { it[Keys.AUTO_THEME_BY_DATE] = enabled }

    // Every mutator below also stamps PENDING_CUSTOMIZATION_THEME_ID = forThemeId in the same
    // atomic edit, so it's always unambiguous which theme the in-progress edit belongs to (see
    // CustomThemeRegistry.resolveActiveCustomization). This is how "scene object changes apply
    // live only to the current theme" is enforced -- other themes simply never match the tag.

    suspend fun setCategoryVisible(category: ObjectCategory, visible: Boolean, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.visible(category)] = visible
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setCategoryDensity(category: ObjectCategory, density: Float, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.density(category)] = density
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /** The night-time pedestrian density. The daytime one is `setCategoryDensity(PEOPLE, ...)`. */
    suspend fun setPeopleNightDensity(density: Float, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.PEOPLE_NIGHT_DENSITY] = density
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setCategoryColorDay1(category: ObjectCategory, color: Int, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.colorDay1(category)] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setCategoryColorNight1(category: ObjectCategory, color: Int, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.colorNight1(category)] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setCategoryColorDay2(category: ObjectCategory, color: Int, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.colorDay2(category)] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setCategoryColorNight2(category: ObjectCategory, color: Int, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.colorNight2(category)] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setHillsVariation(variation: Float, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.HILLS_VARIATION] = variation
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setHillsColorDay(color: Int, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.HILLS_COLOR_DAY] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setHillsColorNight(color: Int, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.HILLS_COLOR_NIGHT] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setMountainVisible(front: Boolean, visible: Boolean, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.mountainVisible(front)] = visible
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setMountainDensity(front: Boolean, density: Float, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.mountainDensity(front)] = density
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setMountainColorDay(front: Boolean, color: Int, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.mountainColorDay(front)] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setMountainColorNight(front: Boolean, color: Int, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.mountainColorNight(front)] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setLakeVisible(visible: Boolean, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.LAKE_VISIBLE] = visible
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setLakeColorDay(color: Int, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.LAKE_COLOR_DAY] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setLakeColorNight(color: Int, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.LAKE_COLOR_NIGHT] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setLakeHeight(height: Float, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.LAKE_HEIGHT] = height
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setLakeSailboatsVisible(visible: Boolean, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.LAKE_SAILBOATS_VISIBLE] = visible
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setLakeSailboatsDensity(density: Float, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.LAKE_SAILBOATS_DENSITY] = density
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setLakeDolphinsVisible(visible: Boolean, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.LAKE_DOLPHINS_VISIBLE] = visible
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setLakeDolphinsDensity(density: Float, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.LAKE_DOLPHINS_DENSITY] = density
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setStarsVisible(visible: Boolean, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.STARS_VISIBLE] = visible; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setStarsDensity(density: Float, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.STARS_DENSITY] = density; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setSkyColorDayHigh(color: Int, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.SKY_COLOR_DAY_HIGH] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setSkyColorDayLow(color: Int, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.SKY_COLOR_DAY_LOW] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setSkyColorNightHigh(color: Int, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.SKY_COLOR_NIGHT_HIGH] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setSkyColorNightLow(color: Int, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.SKY_COLOR_NIGHT_LOW] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setSkyColorSunriseLow(color: Int, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.SKY_COLOR_SUNRISE_LOW] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setSkyColorSunsetLow(color: Int, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.SKY_COLOR_SUNSET_LOW] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setSkySunCloudHeight(height: Float, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.SKY_SUN_CLOUD_HEIGHT] = height; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setSunVisible(visible: Boolean, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.SUN_VISIBLE] = visible; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setSunColor(color: Int, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.SUN_COLOR] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setMoonVisible(visible: Boolean, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.MOON_VISIBLE] = visible; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setMoonColor(color: Int, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.MOON_COLOR] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setMoonRealisticPhases(realistic: Boolean, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.MOON_REALISTIC_PHASES] = realistic; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setCloudsVisible(visible: Boolean, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.CLOUDS_VISIBLE] = visible; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setCloudsDensity(density: Float, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.CLOUDS_DENSITY] = density; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setCloudsColorDay(color: Int, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.CLOUDS_COLOR_DAY] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setCloudsColorNight(color: Int, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.CLOUDS_COLOR_NIGHT] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setBirdsVisible(visible: Boolean, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.BIRDS_VISIBLE] = visible
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setBirdsDensity(density: Float, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.BIRDS_DENSITY] = density
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setBirdsNight(nightBirds: Boolean, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.BIRDS_NIGHT] = nightBirds
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setBirdColor(index: Int, color: Int, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.birdColor(index)] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setBirdWeight(index: Int, weight: Float, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.birdWeight(index)] = weight
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setPrecipitationVisible(visible: Boolean, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.PRECIPITATION_VISIBLE] = visible; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setPrecipitationType(type: PrecipitationType, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.PRECIPITATION_TYPE] = type.name; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setPrecipitationIntensity(intensity: Float, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.PRECIPITATION_INTENSITY] = intensity; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setPrecipitationRainColorDay(color: Int, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.PRECIPITATION_RAIN_COLOR_DAY] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setPrecipitationRainColorNight(color: Int, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.PRECIPITATION_RAIN_COLOR_NIGHT] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setPrecipitationSnowColorDay(color: Int, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.PRECIPITATION_SNOW_COLOR_DAY] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setPrecipitationSnowColorNight(color: Int, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.PRECIPITATION_SNOW_COLOR_NIGHT] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setPrecipitationThunderstorm(thunderstorm: Boolean, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.PRECIPITATION_THUNDERSTORM] = thunderstorm; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setRainbowVisible(visible: Boolean, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.RAINBOW_VISIBLE] = visible; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setRainbowOpacity(opacity: Float, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId); it[Keys.RAINBOW_OPACITY] = opacity; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    /** Mutually exclusive with [setWinterColorsEnabled] -- turning Fall Colors on always turns
     * Winter/Christmas Colors off in the same edit, same pattern PrecipitationConfig.type already
     * uses for Rain vs Snow (see [SceneCustomization.fallColorsEnabled]'s own doc comment). */
    suspend fun setFallColorsEnabled(enabled: Boolean, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.FALL_COLORS_ENABLED] = enabled
            if (enabled) it[Keys.WINTER_COLORS_ENABLED] = false
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /**
     * The Christmas decoration layer.
     *
     * **Independent of both seasonal palettes, unlike them of each other.** Fall Colors and Winter
     * Colors are two readings of the same leaves and cannot both be true; Christmas lights are
     * hung *on top of* whatever the trees look like, so this clears nothing and nothing clears it.
     */
    suspend fun setChristmasDecorationsEnabled(enabled: Boolean, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.CHRISTMAS_DECORATIONS_ENABLED] = enabled
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /** Ground flowers on or off. Independent of every other flag. */
    suspend fun setFlowersEnabled(enabled: Boolean, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.FLOWERS_ENABLED] = enabled
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /**
     * Halloween on or off. **Clears nothing and is cleared by nothing.**
     *
     * Winter and Christmas are untouched by this in both directions, which is the whole point of
     * it being a third flag rather than a mode on either of them.
     */
    suspend fun setHalloweenEnabled(enabled: Boolean, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.HALLOWEEN_ENABLED] = enabled
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /** The horror sky, independent of [setHalloweenEnabled] in both directions. */
    suspend fun setHorrorSkyEnabled(enabled: Boolean, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.HORROR_SKY_ENABLED] = enabled
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /** Mutually exclusive with [setFallColorsEnabled] -- see that function's own doc comment. */
    suspend fun setWinterColorsEnabled(enabled: Boolean, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.WINTER_COLORS_ENABLED] = enabled
            if (enabled) it[Keys.FALL_COLORS_ENABLED] = false
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setSantaEnabled(enabled: Boolean, forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.SANTA_ENABLED] = enabled
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /** Removes the override entirely (rather than forcing a fixed `false` like
     * [setFallColorsEnabled]/[setWinterColorsEnabled]'s own reset does) so it falls back to
     * [defaultCustomizationFor]'s per-theme dynamic default (`theme.hasSantaSleigh`) -- unlike
     * Fall/Winter Colors, which are always off by default regardless of theme, Santa's default
     * genuinely varies per theme (on for Christmas, off elsewhere), so "reset" has to mean "go
     * back to whatever this theme's own default is", not "force off everywhere including
     * Christmas". */
    suspend fun resetSanta(forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it.remove(Keys.SANTA_ENABLED)
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /**
     * Clears the three seasonal presentation flags so they fall back to the theme's own defaults.
     *
     * **Removes them rather than setting them false.** The Seasonal Decorations screen's "reset
     * everything to defaults" wrote `false` into Fall and Winter Colors, which was indistinguishable
     * from a default while every theme defaulted to off — and stopped being a reset the moment
     * Winter, Christmas, New Year and Autumn started defaulting to on. A reset has to mean "forget
     * what I chose", not "choose off".
     */
    suspend fun resetSeasonalPalettes(forThemeId: String) =
        context.dataStore.edit { it.ensureFreshPendingTheme(forThemeId)
            it.remove(Keys.FALL_COLORS_ENABLED)
            it.remove(Keys.WINTER_COLORS_ENABLED)
            it.remove(Keys.CHRISTMAS_DECORATIONS_ENABLED)
            it.remove(Keys.FLOWERS_ENABLED)
            it.remove(Keys.HALLOWEEN_ENABLED)
            it.remove(Keys.HORROR_SKY_ENABLED)
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /** Resets one category's visibility/density/colors back to defaults (keeps the pending-theme tag). */
    suspend fun resetCategory(category: ObjectCategory) = context.dataStore.edit { prefs ->
        prefs.remove(Keys.visible(category))
        prefs.remove(Keys.density(category))
        prefs.remove(Keys.colorDay1(category))
        prefs.remove(Keys.colorNight1(category))
        prefs.remove(Keys.colorDay2(category))
        prefs.remove(Keys.colorNight2(category))
        // People carry a second density that lives outside the per-category keys; resetting the
        // category has to clear it too, or "reset to default" would leave the night population
        // wherever the user had dragged it.
        if (category == ObjectCategory.PEOPLE) prefs.remove(Keys.PEOPLE_NIGHT_DENSITY)
    }

    /**
     * Removes every per-theme scratch customization key -- every field that's meaningful only
     * in the context of *which theme* is currently being edited (as opposed to global settings
     * like scroll speed or Live Weather, which apply no matter which theme is active). Shared by
     * [ensureFreshPendingTheme] (called at the start of every per-theme setter, see its own doc
     * comment for why) and [resetAllCategories] (the user-facing "reset everything" button) --
     * both need the exact same "wipe every per-theme override" behavior, just triggered
     * differently (automatically on a theme mismatch vs. manually via a button).
     */
    private fun MutablePreferences.clearAllThemeCustomizationKeys() {
        for (category in ObjectCategory.entries) {
            remove(Keys.visible(category))
            remove(Keys.density(category))
            remove(Keys.colorDay1(category))
            remove(Keys.colorNight1(category))
            remove(Keys.colorDay2(category))
            remove(Keys.colorNight2(category))
        }
        remove(Keys.HILLS_VARIATION)
        remove(Keys.HILLS_COLOR_DAY)
        remove(Keys.HILLS_COLOR_NIGHT)
        for (front in listOf(true, false)) {
            remove(Keys.mountainVisible(front))
            remove(Keys.mountainDensity(front))
            remove(Keys.mountainColorDay(front))
            remove(Keys.mountainColorNight(front))
        }
        remove(Keys.LAKE_VISIBLE)
        remove(Keys.LAKE_COLOR_DAY)
        remove(Keys.LAKE_COLOR_NIGHT)
        remove(Keys.LAKE_HEIGHT)
        remove(Keys.LAKE_SAILBOATS_VISIBLE)
        remove(Keys.LAKE_SAILBOATS_DENSITY)
        remove(Keys.LAKE_DOLPHINS_VISIBLE)
        remove(Keys.LAKE_DOLPHINS_DENSITY)
        remove(Keys.STARS_VISIBLE)
        remove(Keys.STARS_DENSITY)
        remove(Keys.SKY_COLOR_DAY_HIGH)
        remove(Keys.SKY_COLOR_DAY_LOW)
        remove(Keys.SKY_COLOR_NIGHT_HIGH)
        remove(Keys.SKY_COLOR_NIGHT_LOW)
        remove(Keys.SKY_COLOR_SUNRISE_LOW)
        remove(Keys.SKY_COLOR_SUNSET_LOW)
        remove(Keys.SKY_SUN_CLOUD_HEIGHT)
        remove(Keys.SUN_VISIBLE)
        remove(Keys.SUN_COLOR)
        remove(Keys.MOON_VISIBLE)
        remove(Keys.MOON_COLOR)
        remove(Keys.MOON_REALISTIC_PHASES)
        remove(Keys.CLOUDS_VISIBLE)
        remove(Keys.CLOUDS_DENSITY)
        remove(Keys.CLOUDS_COLOR_DAY)
        remove(Keys.CLOUDS_COLOR_NIGHT)
        remove(Keys.BIRDS_VISIBLE)
        remove(Keys.BIRDS_DENSITY)
        remove(Keys.BIRDS_NIGHT)
        for (index in 0 until 4) {
            remove(Keys.birdColor(index))
            remove(Keys.birdWeight(index))
        }
        remove(Keys.PRECIPITATION_VISIBLE)
        remove(Keys.PRECIPITATION_TYPE)
        remove(Keys.PRECIPITATION_INTENSITY)
        remove(Keys.PRECIPITATION_RAIN_COLOR_DAY)
        remove(Keys.PRECIPITATION_RAIN_COLOR_NIGHT)
        remove(Keys.PRECIPITATION_SNOW_COLOR_DAY)
        remove(Keys.PRECIPITATION_SNOW_COLOR_NIGHT)
        remove(Keys.PRECIPITATION_THUNDERSTORM)
        remove(Keys.RAINBOW_VISIBLE)
        remove(Keys.RAINBOW_OPACITY)
        // Previously missing from this list entirely (added after Fall/Winter Colors and Santa
        // were introduced later than the rest) -- meant "Reset everything to defaults" silently
        // left these 3 untouched. Also exactly the 3 fields involved in aa's own bug report: a
        // stale WINTER_COLORS_ENABLED=true (or a stale HILLS_COLOR_DAY, etc.) surviving a theme
        // switch is precisely what [ensureFreshPendingTheme] below now prevents.
        remove(Keys.FALL_COLORS_ENABLED)
        remove(Keys.WINTER_COLORS_ENABLED)
        remove(Keys.CHRISTMAS_DECORATIONS_ENABLED)
        remove(Keys.FLOWERS_ENABLED)
        remove(Keys.HALLOWEEN_ENABLED)
        remove(Keys.HORROR_SKY_ENABLED)
        remove(Keys.SANTA_ENABLED)
    }

    /**
     * Called as the very first statement in every per-theme setter (`setXxxVisible`,
     * `setHillsColorDay`, etc. -- anything taking a `forThemeId` parameter), inside the same
     * DataStore edit transaction, before that setter applies its own specific field change.
     *
     * The bug this fixes: aa reported that turning on Winter/Christmas Colors while on the Beach
     * theme also turned the hills white. Root cause -- every one of this class's ~60 per-theme
     * setters writes its own one field *plus* `PENDING_CUSTOMIZATION_THEME_ID = forThemeId`, but
     * every other field (hills color, sky, precipitation, everything) is a single global flat
     * DataStore key, not namespaced per theme. [WallpaperPrefs.settingsFlow] only ever falls back
     * to a theme's defaults/saved entry while `PENDING_CUSTOMIZATION_THEME_ID` differs from the
     * theme actually being viewed; the instant *any* setter fires for a *different* theme than
     * whatever was last edited, that check starts passing again and every one of those stale
     * flat fields -- last written for a *previous* theme, e.g. hills set white while editing the
     * Christmas/Winter theme -- leaks straight into the newly "pending" theme, even though the
     * edit being made only meant to touch one unrelated field (Winter Colors, in aa's report).
     * This isn't specific to Winter Colors or to hills/Beach -- it's a general architectural gap
     * that could surface with any field, on any theme, the moment you edit a second theme after
     * customizing a first one.
     *
     * The fix: whenever a setter's own `forThemeId` doesn't match whichever theme the scratch
     * state currently belongs to, wipe every per-theme field first (via
     * [clearAllThemeCustomizationKeys]) so the setter's own change lands on that theme's true
     * baseline, not contaminated leftovers from whatever was edited previously.
     */
    private fun MutablePreferences.ensureFreshPendingTheme(forThemeId: String) {
        if (this[Keys.PENDING_CUSTOMIZATION_THEME_ID] != forThemeId) {
            clearAllThemeCustomizationKeys()
        }
    }

    /** Resets every object category (structural and seasonal alike -- both are per-theme scratch
     * state now, see the [ObjectCategory] doc comment) back to defaults and clears the
     * pending-edit tag entirely. */
    suspend fun resetAllCategories() = context.dataStore.edit { prefs ->
        prefs.clearAllThemeCustomizationKeys()
        prefs.remove(Keys.PENDING_CUSTOMIZATION_THEME_ID)
    }
}
