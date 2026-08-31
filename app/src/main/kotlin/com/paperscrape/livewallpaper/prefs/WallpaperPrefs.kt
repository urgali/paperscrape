package com.paperscrape.livewallpaper.prefs

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import androidx.datastore.core.DataStore
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.paperscrape.livewallpaper.prefs.PrefsRecovery.recoveringFromReadErrors
import com.paperscrape.livewallpaper.location.DeviceLocationKind
import com.paperscrape.livewallpaper.engine.AutoColorMode
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
import com.paperscrape.livewallpaper.engine.sceneCustomizationFromJson
import com.paperscrape.livewallpaper.engine.toJson
import com.paperscrape.livewallpaper.weather.LiveWeatherStatus
import com.paperscrape.livewallpaper.weather.WeatherProviderId
import org.json.JSONObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The name of this store's file, shared with the instrumented recovery test so the test corrupts
 * the same bytes the app reads rather than a path it copied by hand.
 */
internal const val WALLPAPER_PREFS_STORE_NAME = "paperscrape_prefs"

// `corruptionHandler` is the difference between a wallpaper that comes up on its defaults after a
// bad shutdown and one that kills its own process on every restart until the user clears app data.
// See [PrefsRecovery] for why corruption, I/O failure and unexpected errors get three different
// answers -- and for why replacing this file cannot cost the user their custom themes.
private val Context.dataStore by preferencesDataStore(
    name = WALLPAPER_PREFS_STORE_NAME,
    corruptionHandler = PrefsRecovery.replacingCorruptFile(),
)

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
    /**
     * Which of the device's positioning systems [useLocationForSunTimes] means.
     *
     * Stored separately from the on/off flag rather than as a third value of it, so an install
     * from before v3.0 -- which had one "Phone" setting and no key for this -- reads as
     * [DeviceLocationKind.NETWORK]. That is not an arbitrary default: the old provider asked for
     * the network provider first and only reached for GPS if it was disabled, so NETWORK is what
     * those users were already getting, and their behaviour and their permission both stay put.
     */
    val deviceLocationKind: DeviceLocationKind = DeviceLocationKind.NETWORK,
    val customLocationLatitude: Float = 45.4642f, // Milan -- an arbitrary but real default so a
    val customLocationLongitude: Float = 9.19f, // freshly-enabled toggle isn't at (0,0) in the ocean
    val customLocationLabel: String = "",
    // Live Weather: global (not per-theme, like useLocationForSunTimes/useCustomLocation above)
    // since it needs one of the location modes active to know where to fetch conditions
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
     * The user's WeatherAPI.com key. Never compiled in, never logged, never sent anywhere but
     * WeatherAPI.com -- unlike Open-Meteo's, whose free tier makes a shipped key sensible.
     *
     * Kept apart from [liveWeatherApiKey] so that switching provider and back does not lose
     * either one.
     */
    val weatherApiComApiKey: String = "",
    /**
     * The user's OpenWeather key. Same rules as [weatherApiComApiKey]: never compiled in, never
     * logged, never sent anywhere but OpenWeather, and kept apart so switching provider and back
     * loses neither.
     */
    val openWeatherApiKey: String = "",
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
    /**
     * When the saved device fix above was taken, as epoch millis; 0 when there has never been one.
     *
     * The fix is the fallback the whole device-location path leans on -- if the provider cannot
     * answer, the scene keeps using where the device last was rather than snapping to a default
     * somewhere else. Knowing *when* is what makes that honest: the settings screen can say the
     * position is an old one, and the engine can decide a saved fix is too old to be worth reusing
     * instead of quietly trusting it forever.
     */
    val deviceFixTimestampMillis: Long = 0L,
    val fixedHour: Float = 18f, // used only when syncWithRealTime == false
    val parallaxStrength: Float = 1f, // 0.5 .. 2.0 -- also labeled "Scroll Speed" in the UI's
    // Scrolling section: one mechanism (how much the scenery shifts per unit of home-screen
    // swipe) behind one slider, rather than two sliders that would fight over the same motion.
    // Scroll behavior below is deliberately global (not per-theme), matching that same reference
    // -- these are interaction/engine preferences, not part of a theme's visual identity the way
    // hill colors or which decorations are visible are.
    val scrollBackground: Boolean = false, // whether sun/moon/sky scroll with the parallax hills
    val swipeScroll: Boolean = true, // whether swiping between home screens scrolls the wallpaper at all
    // Continuous auto-scroll, independent of swiping entirely: this speed multiplies a per-frame
    // *time delta* rather than a swipe offset, which is a genuinely
    // different mechanism from PaperScrape's existing `parallaxStrength` (which scales how far
    // layers move *relative to swiping*). Defaults to a slow constant drift rather than off --
    // this is an engine-level behavior in the
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
    /**
     * Every theme that has a customization of its own on disk, keyed by theme id.
     *
     * **This is what makes a per-theme edit survive editing another theme.** Before v4.3 the only
     * per-theme storage was the single flat scratch space [pendingCustomization] reads, which
     * holds exactly one theme at a time -- so touching one control on a second theme wiped
     * everything the user had done to the first, permanently and silently. The scratch space is
     * still where a live edit lands, but it is now archived here on the way out instead of being
     * deleted, and restored from here when that theme is edited again.
     *
     * Empty for a theme the user has never customised; that theme reads its own defaults.
     */
    val themeCustomizations: Map<String, SceneCustomization> = emptyMap(),
) {

    /** [weatherProviderId] resolved; an unrecognised stored id reads as the default. */
    val weatherProvider: WeatherProviderId
        get() = WeatherProviderId.fromStorageId(weatherProviderId)

    /**
     * The key the **selected** provider should be called with.
     *
     * Each provider keeps its own, so switching back and forth loses neither. Open-Meteo's may be
     * blank, which its free keyless tier accepts; WeatherAPI.com's may not, and a blank one there
     * is what produces [com.paperscrape.livewallpaper.weather.WeatherFetchResult.MissingApiKey]
     * instead of a request.
     */
    val apiKeyForWeatherProvider: String
        get() = when (weatherProvider) {
            WeatherProviderId.OPEN_METEO -> liveWeatherApiKey
            WeatherProviderId.WEATHER_API_COM -> weatherApiComApiKey
            WeatherProviderId.OPEN_WEATHER -> openWeatherApiKey
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

/** Key-name prefix for [WallpaperPrefs]'s per-theme customization blobs. */
internal const val THEME_CUSTOMIZATION_KEY_PREFIX = "theme_customization_"

/**
 * `DataStore.edit`, but the write finishes even if the caller's scope is cancelled.
 *
 * **ARC-09.** Every preference write is launched from `rememberCoroutineScope()`, whose lifetime is
 * the composition. An Activity recreation -- a rotation, a light/dark switch, a font-size change --
 * cancels that scope, so a tap that landed in the same frame had its write cancelled halfway to
 * disk. DataStore's own write is transactional, so nothing was ever corrupted; the switch simply
 * bounced back, which reads as the app ignoring the user.
 *
 * `NonCancellable` is the same instrument `BackupRepository` uses for the two writes that must not
 * be interrupted, and for the same reason. A preference write is a handful of bytes and completes
 * in milliseconds, so making it uninterruptible costs nothing and is what the user meant by
 * touching the control.
 */
private suspend fun DataStore<Preferences>.editDurably(
    transform: suspend (MutablePreferences) -> Unit,
): Preferences = withContext(NonCancellable) { edit(transform) }

class WallpaperPrefs(private val context: Context) {

    private object Keys {
        /**
         * Night-time pedestrian density. Absent for every install that predates v2.12, which is
         * what [PeopleDensity.resolveNightDensity] reads as "use the daytime value" -- see its own
         * comment on why that, and not a fresh default, is the right upgrade.
         */
        val PEOPLE_NIGHT_DENSITY = floatPreferencesKey("people_night_density")

        /**
         * The saved-themes document an import has written here but not yet into its own store.
         *
         * **BCK-06.** An import writes two DataStores and there is no transaction spanning them, so
         * a process kill between the two left the preferences new and the saved themes old. Each
         * store's own write is atomic, so the fix is to make the *pair* recoverable rather than to
         * invent a transaction: the second store's entire payload is written **inside the first
         * store's own atomic edit**, applied, and then cleared. Whatever moment the process dies in,
         * the next start finds either no pending document (nothing to do) or the exact bytes the
         * second store was supposed to receive, and applying them again is idempotent.
         *
         * That is one key and one completion step, not a journal: there is no sequence to replay, no
         * ordering to reconstruct, and nothing to undo -- the pending value *is* the whole of the
         * remaining work.
         */
        val PENDING_IMPORT_THEMES = stringPreferencesKey("pending_import_themes")

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
        // v3.7 renamed this from `visual_crossing_api_key` when that provider was removed. A key
        // for a service the app no longer talks to is not worth migrating: it would not
        // authenticate anywhere. The old entry is simply left unread, and the stored
        // `weather_provider` value `visual_crossing` no longer matches an id, so
        // `WeatherProviderId.fromStorageId` returns the default -- see WeatherProviderSelectionTest.
        val WEATHER_API_COM_API_KEY = stringPreferencesKey("weatherapi_com_api_key")
        val OPEN_WEATHER_API_KEY = stringPreferencesKey("open_weather_api_key")
        val LIVE_WEATHER_STATUS = stringPreferencesKey("live_weather_status")
        val AUTOMATIC_UPDATE_CHECK = booleanPreferencesKey("automatic_update_check")
        val RESOLVED_GPS_LAT = floatPreferencesKey("resolved_gps_lat")
        val RESOLVED_GPS_LON = floatPreferencesKey("resolved_gps_lon")
        val DEVICE_FIX_AT = longPreferencesKey("device_fix_at")
        val DEVICE_LOCATION_KIND = stringPreferencesKey("device_location_kind")
        val FIXED_HOUR = floatPreferencesKey("fixed_hour")
        val PARALLAX_STRENGTH = floatPreferencesKey("parallax_strength")
        val AUTO_THEME_BY_DATE = booleanPreferencesKey("auto_theme_by_date")
        val PENDING_CUSTOMIZATION_THEME_ID = stringPreferencesKey("pending_customization_theme_id")

        /**
         * One theme's whole customization, as JSON, under a key named after that theme.
         *
         * Deliberately a JSON blob rather than ~60 more namespaced keys: the serialisation
         * already exists, is versioned, is round-trip tested (`CustomThemeDataJsonTest`), and is
         * the same one `CustomThemeStore` persists saved themes with -- so a saved theme and a
         * customised built-in are the same bytes in two places rather than two formats to keep in
         * step. Purely additive: an install that has never written one simply has no such key.
         */
        fun themeCustomization(themeId: String) =
            stringPreferencesKey("$THEME_CUSTOMIZATION_KEY_PREFIX$themeId")

        fun visible(category: ObjectCategory) = booleanPreferencesKey("obj_${category.name}_visible")
        fun density(category: ObjectCategory) = floatPreferencesKey("obj_${category.name}_density")
        fun colorDay1(category: ObjectCategory) = intPreferencesKey("obj_${category.name}_color_day_1")
        fun colorNight1(category: ObjectCategory) = intPreferencesKey("obj_${category.name}_color_night_1")
        fun colorDay2(category: ObjectCategory) = intPreferencesKey("obj_${category.name}_color_day_2")
        fun colorNight2(category: ObjectCategory) = intPreferencesKey("obj_${category.name}_color_night_2")

        /**
         * Which half of each pair the user owns, stored as [AutoColorMode.storageId].
         *
         * A string rather than a boolean because there are three states, not two, and because a
         * pair whose meaning depended on an enum's ordinal would repaint itself the day somebody
         * inserted a constant. Absent means [AutoColorMode.MANUAL], which is what every install
         * predating the feature reads.
         */
        fun autoMode1(category: ObjectCategory) = stringPreferencesKey("obj_${category.name}_auto_mode_1")
        fun autoMode2(category: ObjectCategory) = stringPreferencesKey("obj_${category.name}_auto_mode_2")
        val HILLS_VARIATION = floatPreferencesKey("hills_variation")
        val HILLS_COLOR_DAY = intPreferencesKey("hills_color_day")
        val HILLS_COLOR_NIGHT = intPreferencesKey("hills_color_night")
        val HILLS_AUTO_MODE = stringPreferencesKey("hills_auto_mode")
        val SCROLL_BACKGROUND = booleanPreferencesKey("scroll_background")
        val SWIPE_SCROLL = booleanPreferencesKey("swipe_scroll")
        val SCROLL_SPEED = floatPreferencesKey("scroll_speed")
        fun mountainVisible(front: Boolean) = booleanPreferencesKey("mountain_${if (front) "front" else "back"}_visible")
        fun mountainDensity(front: Boolean) = floatPreferencesKey("mountain_${if (front) "front" else "back"}_density")
        fun mountainColorDay(front: Boolean) = intPreferencesKey("mountain_${if (front) "front" else "back"}_color_day")
        fun mountainColorNight(front: Boolean) = intPreferencesKey("mountain_${if (front) "front" else "back"}_color_night")
        fun mountainAutoMode(front: Boolean) = stringPreferencesKey("mountain_${if (front) "front" else "back"}_auto_mode")
        val LAKE_VISIBLE = booleanPreferencesKey("lake_visible")
        val LAKE_COLOR_DAY = intPreferencesKey("lake_color_day")
        val LAKE_COLOR_NIGHT = intPreferencesKey("lake_color_night")
        val LAKE_AUTO_MODE = stringPreferencesKey("lake_auto_mode")
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
        val SKY_AUTO_MODE_HIGH = stringPreferencesKey("sky_auto_mode_high")
        val SKY_AUTO_MODE_LOW = stringPreferencesKey("sky_auto_mode_low")
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
        val CLOUDS_AUTO_MODE = stringPreferencesKey("clouds_auto_mode")
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
        val PRECIPITATION_RAIN_AUTO_MODE = stringPreferencesKey("precipitation_rain_auto_mode")
        val PRECIPITATION_SNOW_AUTO_MODE = stringPreferencesKey("precipitation_snow_auto_mode")
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
            autoMode1 = AutoColorMode.fromStorageId(prefs[Keys.autoMode1(category)]),
            autoMode2 = AutoColorMode.fromStorageId(prefs[Keys.autoMode2(category)]),
        )

    val settingsFlow: Flow<WallpaperSettings> = context.dataStore.data.recoveringFromReadErrors().map { prefs ->
        // Falls back to whichever theme's pending edit is currently tagged (see
        // PENDING_CUSTOMIZATION_THEME_ID) -- crucial for seasonal categories specifically:
        // editing just one (e.g. turning snowmen off for Christmas) must NOT silently reset every
        // *other* untouched category (gifts, houses, ...) back to a flat, theme-agnostic default;
        // they should keep reading as Christmas's own defaults until the user explicitly changes
        // them too. Falls back to "sunset" (an all-off theme) when nothing is being edited right
        // now, which is safe since pendingCustomization is only ever consulted by
        // CustomThemeRegistry.resolveActiveCustomization when its own themeId actually matches
        // this tag.
        val pendingThemeId = prefs[Keys.PENDING_CUSTOMIZATION_THEME_ID]
        WallpaperSettings(
            themeId = prefs[Keys.THEME_ID] ?: "sunset",
            syncWithRealTime = prefs[Keys.SYNC_REAL_TIME] ?: true,
            useLocationForSunTimes = prefs[Keys.USE_LOCATION] ?: false,
            useCustomLocation = prefs[Keys.USE_CUSTOM_LOCATION] ?: false,
            deviceLocationKind = DeviceLocationKind.fromStorageId(prefs[Keys.DEVICE_LOCATION_KIND]),
            customLocationLatitude = prefs[Keys.CUSTOM_LOCATION_LAT] ?: 45.4642f,
            customLocationLongitude = prefs[Keys.CUSTOM_LOCATION_LON] ?: 9.19f,
            customLocationLabel = prefs[Keys.CUSTOM_LOCATION_LABEL] ?: "",
            liveWeatherEnabled = prefs[Keys.LIVE_WEATHER_ENABLED] ?: false,
            liveWeatherApiKey = prefs[Keys.LIVE_WEATHER_API_KEY] ?: "",
            weatherProviderId = prefs[Keys.WEATHER_PROVIDER] ?: WeatherProviderId.DEFAULT.storageId,
            weatherApiComApiKey = prefs[Keys.WEATHER_API_COM_API_KEY] ?: "",
            openWeatherApiKey = prefs[Keys.OPEN_WEATHER_API_KEY] ?: "",
            liveWeatherStatus = prefs[Keys.LIVE_WEATHER_STATUS] ?: LiveWeatherStatus.OFF.storageId,
            automaticUpdateCheckEnabled = prefs[Keys.AUTOMATIC_UPDATE_CHECK] ?: false,
            resolvedGpsLatitude = prefs[Keys.RESOLVED_GPS_LAT],
            resolvedGpsLongitude = prefs[Keys.RESOLVED_GPS_LON],
            deviceFixTimestampMillis = prefs[Keys.DEVICE_FIX_AT] ?: 0L,
            fixedHour = prefs[Keys.FIXED_HOUR] ?: 18f,
            parallaxStrength = prefs[Keys.PARALLAX_STRENGTH] ?: 1f,
            scrollBackground = prefs[Keys.SCROLL_BACKGROUND] ?: false,
            swipeScroll = prefs[Keys.SWIPE_SCROLL] ?: true,
            scrollSpeed = prefs[Keys.SCROLL_SPEED] ?: 0.15f,
            autoThemeByDate = prefs[Keys.AUTO_THEME_BY_DATE] ?: false,
            pendingCustomizationThemeId = prefs[Keys.PENDING_CUSTOMIZATION_THEME_ID],
            pendingCustomization = readFlatCustomization(prefs, pendingThemeId ?: "sunset"),
            // The theme under live edit has its state in the flat scratch keys, not yet in its own
            // archive -- that write happens when the user leaves it. Folding it in here means a
            // reader never sees an incomplete picture, and in particular that a backup taken
            // mid-edit contains the edit.
            themeCustomizations = readThemeCustomizations(prefs).let { stored ->
                if (pendingThemeId == null) stored
                else stored + (pendingThemeId to readFlatCustomization(prefs, pendingThemeId))
            },
        )
    }

    /**
     * The customization the flat scratch keys currently hold, read against [themeId]'s defaults.
     *
     * Extracted from [settingsFlow] in v4.3 so that [switchPendingTheme] can *archive* the same
     * value it would otherwise have thrown away. Nothing about what it reads changed.
     */
    private fun readFlatCustomization(prefs: Preferences, themeId: String): SceneCustomization {
        val defaults = defaultCustomizationFor(themeId)
        return SceneCustomization(
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
            hillsAutoMode = AutoColorMode.fromStorageId(prefs[Keys.HILLS_AUTO_MODE]),
            mountainsFront = MountainLayerConfig(
                visible = prefs[Keys.mountainVisible(true)] ?: defaults.mountainsFront.visible,
                density = prefs[Keys.mountainDensity(true)] ?: defaults.mountainsFront.density,
                colorDay = prefs[Keys.mountainColorDay(true)] ?: defaults.mountainsFront.colorDay,
                colorNight = prefs[Keys.mountainColorNight(true)] ?: defaults.mountainsFront.colorNight,
                autoMode = AutoColorMode.fromStorageId(prefs[Keys.mountainAutoMode(true)]),
            ),
            mountainsBack = MountainLayerConfig(
                visible = prefs[Keys.mountainVisible(false)] ?: defaults.mountainsBack.visible,
                density = prefs[Keys.mountainDensity(false)] ?: defaults.mountainsBack.density,
                colorDay = prefs[Keys.mountainColorDay(false)] ?: defaults.mountainsBack.colorDay,
                colorNight = prefs[Keys.mountainColorNight(false)] ?: defaults.mountainsBack.colorNight,
                autoMode = AutoColorMode.fromStorageId(prefs[Keys.mountainAutoMode(false)]),
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
                autoMode = AutoColorMode.fromStorageId(prefs[Keys.LAKE_AUTO_MODE]),
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
                autoModeHigh = AutoColorMode.fromStorageId(prefs[Keys.SKY_AUTO_MODE_HIGH]),
                autoModeLow = AutoColorMode.fromStorageId(prefs[Keys.SKY_AUTO_MODE_LOW]),
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
                autoMode = AutoColorMode.fromStorageId(prefs[Keys.CLOUDS_AUTO_MODE]),
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
                rainAutoMode = AutoColorMode.fromStorageId(prefs[Keys.PRECIPITATION_RAIN_AUTO_MODE]),
                snowAutoMode = AutoColorMode.fromStorageId(prefs[Keys.PRECIPITATION_SNOW_AUTO_MODE]),
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
        )
    }

    /** Every theme that has a persisted customization of its own, keyed by theme id. */
    private fun readThemeCustomizations(prefs: Preferences): Map<String, SceneCustomization> {
        val out = HashMap<String, SceneCustomization>()
        for ((key, value) in prefs.asMap()) {
            val id = key.name.removePrefix(THEME_CUSTOMIZATION_KEY_PREFIX)
            if (id === key.name || value !is String) continue
            out[id] = sceneCustomizationFromJson(runCatching { JSONObject(value) }.getOrNull())
        }
        return out
    }


    /**
     * Writes [c] into the flat scratch keys -- the exact inverse of [readFlatCustomization].
     *
     * Its correctness is not argued from inspection: `ThemeCustomizationPersistenceTest` writes a
     * fully non-default customization through this, reads it back through [readFlatCustomization]
     * and asserts equality, so a field added to [SceneCustomization] and forgotten here fails.
     */
    private fun MutablePreferences.writeFlatCustomization(c: SceneCustomization) {
        fun variant(category: ObjectCategory, config: ObjectVariantConfig) {
            this[Keys.visible(category)] = config.visible
            this[Keys.density(category)] = config.density
            this[Keys.colorDay1(category)] = config.colorDay1
            this[Keys.colorNight1(category)] = config.colorNight1
            this[Keys.colorDay2(category)] = config.colorDay2
            this[Keys.colorNight2(category)] = config.colorNight2
        }
        variant(ObjectCategory.HOUSES, c.houses)
        variant(ObjectCategory.BUILDINGS, c.buildings)
        variant(ObjectCategory.CARS, c.cars)
        variant(ObjectCategory.PARASOLS, c.parasols)
        variant(ObjectCategory.PEOPLE, c.people)
        variant(ObjectCategory.TREES, c.trees)
        variant(ObjectCategory.SNOWMEN, c.snowmen)
        variant(ObjectCategory.GIFTS, c.gifts)
        variant(ObjectCategory.PENGUINS, c.penguins)
        variant(ObjectCategory.BUNNIES, c.bunnies)
        variant(ObjectCategory.EASTER_EGGS, c.easterEggs)
        variant(ObjectCategory.PUMPKINS, c.pumpkins)
        this[Keys.PEOPLE_NIGHT_DENSITY] = c.peopleNightDensity
        this[Keys.HILLS_VARIATION] = c.hillsVariation
        this[Keys.HILLS_COLOR_DAY] = c.hillsColorDay
        this[Keys.HILLS_COLOR_NIGHT] = c.hillsColorNight
        for ((front, m) in listOf(true to c.mountainsFront, false to c.mountainsBack)) {
            this[Keys.mountainVisible(front)] = m.visible
            this[Keys.mountainDensity(front)] = m.density
            this[Keys.mountainColorDay(front)] = m.colorDay
            this[Keys.mountainColorNight(front)] = m.colorNight
        }
        this[Keys.LAKE_VISIBLE] = c.lake.visible
        this[Keys.LAKE_COLOR_DAY] = c.lake.colorDay
        this[Keys.LAKE_COLOR_NIGHT] = c.lake.colorNight
        this[Keys.LAKE_HEIGHT] = c.lake.height
        this[Keys.LAKE_SAILBOATS_VISIBLE] = c.lake.sailboatsVisible
        this[Keys.LAKE_SAILBOATS_DENSITY] = c.lake.sailboatsDensity
        this[Keys.LAKE_DOLPHINS_VISIBLE] = c.lake.dolphinsVisible
        this[Keys.LAKE_DOLPHINS_DENSITY] = c.lake.dolphinsDensity
        this[Keys.STARS_VISIBLE] = c.stars.visible
        this[Keys.STARS_DENSITY] = c.stars.density
        this[Keys.SKY_COLOR_DAY_HIGH] = c.sky.colorDayHigh
        this[Keys.SKY_COLOR_DAY_LOW] = c.sky.colorDayLow
        this[Keys.SKY_COLOR_NIGHT_HIGH] = c.sky.colorNightHigh
        this[Keys.SKY_COLOR_NIGHT_LOW] = c.sky.colorNightLow
        this[Keys.SKY_COLOR_SUNRISE_LOW] = c.sky.colorSunriseLow
        this[Keys.SKY_COLOR_SUNSET_LOW] = c.sky.colorSunsetLow
        this[Keys.SKY_SUN_CLOUD_HEIGHT] = c.sky.sunCloudHeight
        this[Keys.SUN_VISIBLE] = c.sun.visible
        this[Keys.SUN_COLOR] = c.sun.color
        this[Keys.MOON_VISIBLE] = c.moon.visible
        this[Keys.MOON_COLOR] = c.moon.color
        this[Keys.MOON_REALISTIC_PHASES] = c.moon.realisticPhases
        this[Keys.CLOUDS_VISIBLE] = c.clouds.visible
        this[Keys.CLOUDS_DENSITY] = c.clouds.density
        this[Keys.CLOUDS_COLOR_DAY] = c.clouds.colorDay
        this[Keys.CLOUDS_COLOR_NIGHT] = c.clouds.colorNight
        this[Keys.BIRDS_VISIBLE] = c.birds.visible
        this[Keys.BIRDS_DENSITY] = c.birds.density
        this[Keys.BIRDS_NIGHT] = c.birds.nightBirds
        c.birds.colors.forEachIndexed { index, weight ->
            this[Keys.birdColor(index)] = weight.color
            this[Keys.birdWeight(index)] = weight.weight
        }
        this[Keys.PRECIPITATION_VISIBLE] = c.precipitation.visible
        this[Keys.PRECIPITATION_TYPE] = c.precipitation.type.name
        this[Keys.PRECIPITATION_INTENSITY] = c.precipitation.intensity
        this[Keys.PRECIPITATION_RAIN_COLOR_DAY] = c.precipitation.rainColorDay
        this[Keys.PRECIPITATION_RAIN_COLOR_NIGHT] = c.precipitation.rainColorNight
        this[Keys.PRECIPITATION_SNOW_COLOR_DAY] = c.precipitation.snowColorDay
        this[Keys.PRECIPITATION_SNOW_COLOR_NIGHT] = c.precipitation.snowColorNight
        this[Keys.PRECIPITATION_THUNDERSTORM] = c.precipitation.thunderstorm
        this[Keys.RAINBOW_VISIBLE] = c.rainbow.visible
        this[Keys.RAINBOW_OPACITY] = c.rainbow.opacity
        this[Keys.FALL_COLORS_ENABLED] = c.fallColorsEnabled
        this[Keys.WINTER_COLORS_ENABLED] = c.winterColorsEnabled
        this[Keys.CHRISTMAS_DECORATIONS_ENABLED] = c.christmasDecorationsEnabled
        this[Keys.FLOWERS_ENABLED] = c.flowersEnabled
        this[Keys.HALLOWEEN_ENABLED] = c.halloweenEnabled
        this[Keys.HORROR_SKY_ENABLED] = c.horrorSkyEnabled
        this[Keys.SANTA_ENABLED] = c.santaEnabled
    }


    /**
     * Replaces every global preference and every per-theme customization in one transaction.
     *
     * Used by backup import, and by its own rollback. **One `edit` block**, so a reader either
     * sees the whole previous state or the whole new one and never a mixture — which is the half
     * of "atomic import" this store can guarantee on its own; the other half, keeping it in step
     * with `CustomThemeStore`, is [BackupRepository]'s job.
     *
     * The flat scratch keys and their marker are cleared rather than restored: they are one
     * theme's in-progress edit, the restored per-theme blobs are the truth, and carrying a stale
     * scratch across a restore is exactly the confusion this release exists to remove. Runtime
     * state a backup deliberately does not carry -- the resolved GPS fix, its timestamp, the live
     * weather status line -- is left exactly as it is on this device.
     */
    /**
     * [replaceAll], plus the saved-themes document the caller is about to apply to the other store.
     *
     * One atomic edit carrying both halves of an import's first step -- see [Keys.PENDING_IMPORT_THEMES]
     * for why the second store's payload travels inside the first store's write (BCK-06).
     */
    suspend fun replaceAllStagingThemes(
        settings: AppBackup.BackupSettings,
        themeCustomizations: Map<String, SceneCustomization>,
        pendingThemesJson: String,
    ) = replaceAll(settings, themeCustomizations, pendingThemesJson)

    /** The document an interrupted import left behind, or `null` if there is none. */
    suspend fun pendingImportThemes(): String? =
        context.dataStore.data.first()[Keys.PENDING_IMPORT_THEMES]

    /** Marks the import complete. Safe to call when there is nothing pending. */
    suspend fun clearPendingImportThemes() {
        context.dataStore.editDurably { it.remove(Keys.PENDING_IMPORT_THEMES) }
    }

    suspend fun replaceAll(
        settings: AppBackup.BackupSettings,
        themeCustomizations: Map<String, SceneCustomization>,
        pendingThemesJson: String? = null,
    ) = context.dataStore.editDurably { prefs ->
        if (pendingThemesJson == null) {
            prefs.remove(Keys.PENDING_IMPORT_THEMES)
        } else {
            prefs[Keys.PENDING_IMPORT_THEMES] = pendingThemesJson
        }
        prefs[Keys.THEME_ID] = settings.themeId
        prefs[Keys.SYNC_REAL_TIME] = settings.syncWithRealTime
        prefs[Keys.USE_LOCATION] = settings.useLocationForSunTimes
        prefs[Keys.USE_CUSTOM_LOCATION] = settings.useCustomLocation
        prefs[Keys.DEVICE_LOCATION_KIND] = settings.deviceLocationKind
        prefs[Keys.CUSTOM_LOCATION_LAT] = settings.customLocationLatitude
        prefs[Keys.CUSTOM_LOCATION_LON] = settings.customLocationLongitude
        prefs[Keys.CUSTOM_LOCATION_LABEL] = settings.customLocationLabel
        prefs[Keys.LIVE_WEATHER_ENABLED] = settings.liveWeatherEnabled
        prefs[Keys.LIVE_WEATHER_API_KEY] = settings.liveWeatherApiKey
        prefs[Keys.WEATHER_PROVIDER] = settings.weatherProviderId
        prefs[Keys.WEATHER_API_COM_API_KEY] = settings.weatherApiComApiKey
        prefs[Keys.OPEN_WEATHER_API_KEY] = settings.openWeatherApiKey
        prefs[Keys.AUTOMATIC_UPDATE_CHECK] = settings.automaticUpdateCheckEnabled
        prefs[Keys.FIXED_HOUR] = settings.fixedHour
        prefs[Keys.PARALLAX_STRENGTH] = settings.parallaxStrength
        prefs[Keys.SCROLL_BACKGROUND] = settings.scrollBackground
        prefs[Keys.SWIPE_SCROLL] = settings.swipeScroll
        prefs[Keys.SCROLL_SPEED] = settings.scrollSpeed
        prefs[Keys.AUTO_THEME_BY_DATE] = settings.autoThemeByDate

        prefs.clearAllThemeCustomizationKeys()
        prefs.remove(Keys.PENDING_CUSTOMIZATION_THEME_ID)
        for (key in prefs.asMap().keys.filter { it.name.startsWith(THEME_CUSTOMIZATION_KEY_PREFIX) }) {
            prefs.remove(key)
        }
        for ((themeId, customization) in themeCustomizations) {
            prefs[Keys.themeCustomization(themeId)] = customization.toJson().toString()
        }
    }

    suspend fun setTheme(themeId: String) = context.dataStore.editDurably { it[Keys.THEME_ID] = themeId }

    suspend fun setSyncWithRealTime(enabled: Boolean) =
        context.dataStore.editDurably { it[Keys.SYNC_REAL_TIME] = enabled }

    /** Mutually exclusive with [setUseCustomLocation] -- enabling device-location mode always
     * turns custom location off in the same edit. */
    suspend fun setUseLocation(enabled: Boolean) =
        context.dataStore.editDurably {
            it[Keys.USE_LOCATION] = enabled
            if (enabled) it[Keys.USE_CUSTOM_LOCATION] = false
        }

    /**
     * Turns on device location and says which positioning system it may use, in one edit.
     *
     * One call rather than two because the pair is one decision. Written separately, a moment
     * would exist where device location is on and the kind is still the previous one, and the
     * engine watches those settings -- it would start a fix from the wrong system and then throw
     * it away. Custom location is cleared here for the same reason [setUseLocation] clears it.
     */
    suspend fun setDeviceLocation(kind: DeviceLocationKind) =
        context.dataStore.editDurably {
            it[Keys.DEVICE_LOCATION_KIND] = kind.storageId
            it[Keys.USE_LOCATION] = true
            it[Keys.USE_CUSTOM_LOCATION] = false
        }

    /** Mutually exclusive with [setUseLocation] -- see that function's own doc comment. */
    suspend fun setUseCustomLocation(enabled: Boolean) =
        context.dataStore.editDurably {
            it[Keys.USE_CUSTOM_LOCATION] = enabled
            if (enabled) it[Keys.USE_LOCATION] = false
        }

    suspend fun setCustomLocation(latitude: Float, longitude: Float, label: String) =
        context.dataStore.editDurably {
            it[Keys.CUSTOM_LOCATION_LAT] = latitude
            it[Keys.CUSTOM_LOCATION_LON] = longitude
            it[Keys.CUSTOM_LOCATION_LABEL] = label
        }

    suspend fun setLiveWeatherEnabled(enabled: Boolean) =
        context.dataStore.editDurably { it[Keys.LIVE_WEATHER_ENABLED] = enabled }

    suspend fun setLiveWeatherApiKey(apiKey: String) =
        context.dataStore.editDurably { it[Keys.LIVE_WEATHER_API_KEY] = apiKey }

    /** Writes only the provider. Nothing else about Live Weather or the location is touched. */
    suspend fun setWeatherProvider(provider: WeatherProviderId) =
        context.dataStore.editDurably { it[Keys.WEATHER_PROVIDER] = provider.storageId }

    suspend fun setWeatherApiComApiKey(apiKey: String) =
        context.dataStore.editDurably { it[Keys.WEATHER_API_COM_API_KEY] = apiKey }

    suspend fun setOpenWeatherApiKey(apiKey: String) =
        context.dataStore.editDurably { it[Keys.OPEN_WEATHER_API_KEY] = apiKey }

    /**
     * Records whether Live Weather has fallen back to the theme's manual weather.
     *
     * Called from the wallpaper service, never from the UI. Writing it re-emits the settings flow,
     * which is how the settings screen learns about it without polling or a restart -- so it is
     * only written when the value actually changes, or every evaluation of the weather loop would
     * wake every collector for nothing.
     */
    suspend fun setAutomaticUpdateCheckEnabled(enabled: Boolean) =
        context.dataStore.editDurably { it[Keys.AUTOMATIC_UPDATE_CHECK] = enabled }

    suspend fun setLiveWeatherStatus(status: LiveWeatherStatus) =
        context.dataStore.editDurably { it[Keys.LIVE_WEATHER_STATUS] = status.storageId }

    /**
     * Saves the position the device reported, with the moment it was saved.
     *
     * This is the cache the whole device-location path falls back to. It survives a reboot, a
     * revoked permission and a spell with no signal, which is the difference between a scene that
     * keeps showing the right town's weather and one that jumps somewhere else the first time the
     * provider is slow.
     */
    suspend fun setResolvedGpsLocation(latitude: Float, longitude: Float) =
        context.dataStore.editDurably {
            it[Keys.RESOLVED_GPS_LAT] = latitude
            it[Keys.RESOLVED_GPS_LON] = longitude
            it[Keys.DEVICE_FIX_AT] = System.currentTimeMillis()
        }

    suspend fun setFixedHour(hour: Float) = context.dataStore.editDurably { it[Keys.FIXED_HOUR] = hour }

    suspend fun setParallaxStrength(strength: Float) =
        context.dataStore.editDurably { it[Keys.PARALLAX_STRENGTH] = strength }

    suspend fun setScrollBackground(enabled: Boolean) =
        context.dataStore.editDurably { it[Keys.SCROLL_BACKGROUND] = enabled }

    suspend fun setSwipeScroll(enabled: Boolean) =
        context.dataStore.editDurably { it[Keys.SWIPE_SCROLL] = enabled }

    suspend fun setScrollSpeed(speed: Float) =
        context.dataStore.editDurably { it[Keys.SCROLL_SPEED] = speed }

    suspend fun setAutoThemeByDate(enabled: Boolean) =
        context.dataStore.editDurably { it[Keys.AUTO_THEME_BY_DATE] = enabled }

    // Every mutator below also stamps PENDING_CUSTOMIZATION_THEME_ID = forThemeId in the same
    // atomic edit, so it's always unambiguous which theme the in-progress edit belongs to (see
    // CustomThemeRegistry.resolveActiveCustomization). This is how "scene object changes apply
    // live only to the current theme" is enforced -- other themes simply never match the tag.

    suspend fun setCategoryVisible(category: ObjectCategory, visible: Boolean, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.visible(category)] = visible
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setCategoryDensity(category: ObjectCategory, density: Float, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.density(category)] = density
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /** The night-time pedestrian density. The daytime one is `setCategoryDensity(PEOPLE, ...)`. */
    suspend fun setPeopleNightDensity(density: Float, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.PEOPLE_NIGHT_DENSITY] = density
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setCategoryColorDay1(category: ObjectCategory, color: Int, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.colorDay1(category)] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setCategoryColorNight1(category: ObjectCategory, color: Int, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.colorNight1(category)] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setCategoryColorDay2(category: ObjectCategory, color: Int, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.colorDay2(category)] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setCategoryColorNight2(category: ObjectCategory, color: Int, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.colorNight2(category)] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setHillsVariation(variation: Float, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.HILLS_VARIATION] = variation
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setHillsColorDay(color: Int, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.HILLS_COLOR_DAY] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setHillsColorNight(color: Int, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.HILLS_COLOR_NIGHT] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setMountainVisible(front: Boolean, visible: Boolean, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.mountainVisible(front)] = visible
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setMountainDensity(front: Boolean, density: Float, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.mountainDensity(front)] = density
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setMountainColorDay(front: Boolean, color: Int, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.mountainColorDay(front)] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setMountainColorNight(front: Boolean, color: Int, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.mountainColorNight(front)] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setLakeVisible(visible: Boolean, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.LAKE_VISIBLE] = visible
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setLakeColorDay(color: Int, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.LAKE_COLOR_DAY] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setLakeColorNight(color: Int, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.LAKE_COLOR_NIGHT] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setLakeHeight(height: Float, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.LAKE_HEIGHT] = height
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setLakeSailboatsVisible(visible: Boolean, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.LAKE_SAILBOATS_VISIBLE] = visible
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setLakeSailboatsDensity(density: Float, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.LAKE_SAILBOATS_DENSITY] = density
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setLakeDolphinsVisible(visible: Boolean, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.LAKE_DOLPHINS_VISIBLE] = visible
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setLakeDolphinsDensity(density: Float, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.LAKE_DOLPHINS_DENSITY] = density
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setStarsVisible(visible: Boolean, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.STARS_VISIBLE] = visible; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setStarsDensity(density: Float, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.STARS_DENSITY] = density; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setSkyColorDayHigh(color: Int, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.SKY_COLOR_DAY_HIGH] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setSkyColorDayLow(color: Int, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.SKY_COLOR_DAY_LOW] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setSkyColorNightHigh(color: Int, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.SKY_COLOR_NIGHT_HIGH] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setSkyColorNightLow(color: Int, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.SKY_COLOR_NIGHT_LOW] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setSkyColorSunriseLow(color: Int, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.SKY_COLOR_SUNRISE_LOW] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setSkyColorSunsetLow(color: Int, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.SKY_COLOR_SUNSET_LOW] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setSkySunCloudHeight(height: Float, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.SKY_SUN_CLOUD_HEIGHT] = height; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setSunVisible(visible: Boolean, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.SUN_VISIBLE] = visible; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setSunColor(color: Int, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.SUN_COLOR] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setMoonVisible(visible: Boolean, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.MOON_VISIBLE] = visible; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setMoonColor(color: Int, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.MOON_COLOR] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setMoonRealisticPhases(realistic: Boolean, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.MOON_REALISTIC_PHASES] = realistic; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setCloudsVisible(visible: Boolean, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.CLOUDS_VISIBLE] = visible; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setCloudsDensity(density: Float, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.CLOUDS_DENSITY] = density; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setCloudsColorDay(color: Int, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.CLOUDS_COLOR_DAY] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setCloudsColorNight(color: Int, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.CLOUDS_COLOR_NIGHT] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    // ---- Automatic day/night colours -------------------------------------------------------
    //
    // One setter per colour pair, each writing nothing but the mode. **The colours themselves are
    // never touched here**, which is the whole reversibility guarantee: a pair switched to
    // automatic and back returns the two values the user last chose, because they stayed on disk
    // throughout. The derivation happens on read, once, in
    // `CustomThemeRegistry.resolveActiveCustomization`.

    suspend fun setCategoryAutoMode1(category: ObjectCategory, mode: AutoColorMode, forThemeId: String) =
        setAutoMode(Keys.autoMode1(category), mode, forThemeId)

    suspend fun setCategoryAutoMode2(category: ObjectCategory, mode: AutoColorMode, forThemeId: String) =
        setAutoMode(Keys.autoMode2(category), mode, forThemeId)

    suspend fun setHillsAutoMode(mode: AutoColorMode, forThemeId: String) =
        setAutoMode(Keys.HILLS_AUTO_MODE, mode, forThemeId)

    suspend fun setMountainAutoMode(front: Boolean, mode: AutoColorMode, forThemeId: String) =
        setAutoMode(Keys.mountainAutoMode(front), mode, forThemeId)

    suspend fun setLakeAutoMode(mode: AutoColorMode, forThemeId: String) =
        setAutoMode(Keys.LAKE_AUTO_MODE, mode, forThemeId)

    suspend fun setSkyAutoModeHigh(mode: AutoColorMode, forThemeId: String) =
        setAutoMode(Keys.SKY_AUTO_MODE_HIGH, mode, forThemeId)

    suspend fun setSkyAutoModeLow(mode: AutoColorMode, forThemeId: String) =
        setAutoMode(Keys.SKY_AUTO_MODE_LOW, mode, forThemeId)

    suspend fun setCloudsAutoMode(mode: AutoColorMode, forThemeId: String) =
        setAutoMode(Keys.CLOUDS_AUTO_MODE, mode, forThemeId)

    suspend fun setPrecipitationRainAutoMode(mode: AutoColorMode, forThemeId: String) =
        setAutoMode(Keys.PRECIPITATION_RAIN_AUTO_MODE, mode, forThemeId)

    suspend fun setPrecipitationSnowAutoMode(mode: AutoColorMode, forThemeId: String) =
        setAutoMode(Keys.PRECIPITATION_SNOW_AUTO_MODE, mode, forThemeId)

    /** The one write every mode setter above performs, so the per-theme dance is stated once. */
    private suspend fun setAutoMode(
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        mode: AutoColorMode,
        forThemeId: String,
    ) = context.dataStore.editDurably {
        it.ensureFreshPendingTheme(forThemeId)
        it[key] = mode.storageId
        it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
    }

    suspend fun setBirdsVisible(visible: Boolean, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.BIRDS_VISIBLE] = visible
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setBirdsDensity(density: Float, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.BIRDS_DENSITY] = density
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setBirdsNight(nightBirds: Boolean, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.BIRDS_NIGHT] = nightBirds
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setBirdColor(index: Int, color: Int, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.birdColor(index)] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setBirdWeight(index: Int, weight: Float, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.birdWeight(index)] = weight
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setPrecipitationVisible(visible: Boolean, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.PRECIPITATION_VISIBLE] = visible; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setPrecipitationType(type: PrecipitationType, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.PRECIPITATION_TYPE] = type.name; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setPrecipitationIntensity(intensity: Float, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.PRECIPITATION_INTENSITY] = intensity; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setPrecipitationRainColorDay(color: Int, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.PRECIPITATION_RAIN_COLOR_DAY] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setPrecipitationRainColorNight(color: Int, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.PRECIPITATION_RAIN_COLOR_NIGHT] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setPrecipitationSnowColorDay(color: Int, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.PRECIPITATION_SNOW_COLOR_DAY] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setPrecipitationSnowColorNight(color: Int, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.PRECIPITATION_SNOW_COLOR_NIGHT] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setPrecipitationThunderstorm(thunderstorm: Boolean, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.PRECIPITATION_THUNDERSTORM] = thunderstorm; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setRainbowVisible(visible: Boolean, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.RAINBOW_VISIBLE] = visible; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setRainbowOpacity(opacity: Float, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.RAINBOW_OPACITY] = opacity; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    /** Mutually exclusive with [setWinterColorsEnabled] -- turning Fall Colors on always turns
     * Winter/Christmas Colors off in the same edit, same pattern PrecipitationConfig.type already
     * uses for Rain vs Snow (see [SceneCustomization.fallColorsEnabled]'s own doc comment). */
    suspend fun setFallColorsEnabled(enabled: Boolean, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
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
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.CHRISTMAS_DECORATIONS_ENABLED] = enabled
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /** Ground flowers on or off. Independent of every other flag. */
    suspend fun setFlowersEnabled(enabled: Boolean, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
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
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.HALLOWEEN_ENABLED] = enabled
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /** The horror sky, independent of [setHalloweenEnabled] in both directions. */
    suspend fun setHorrorSkyEnabled(enabled: Boolean, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.HORROR_SKY_ENABLED] = enabled
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /** Mutually exclusive with [setFallColorsEnabled] -- see that function's own doc comment. */
    suspend fun setWinterColorsEnabled(enabled: Boolean, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.WINTER_COLORS_ENABLED] = enabled
            if (enabled) it[Keys.FALL_COLORS_ENABLED] = false
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setSantaEnabled(enabled: Boolean, forThemeId: String) =
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
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
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
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
        context.dataStore.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it.remove(Keys.FALL_COLORS_ENABLED)
            it.remove(Keys.WINTER_COLORS_ENABLED)
            it.remove(Keys.CHRISTMAS_DECORATIONS_ENABLED)
            it.remove(Keys.FLOWERS_ENABLED)
            it.remove(Keys.HALLOWEEN_ENABLED)
            it.remove(Keys.HORROR_SKY_ENABLED)
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /**
     * Resets one category's visibility/density/colors back to defaults, **on [forThemeId]**.
     *
     * Takes the theme like every other per-theme mutator, and for the same reason. The flat
     * customization keys belong to whichever theme `PENDING_CUSTOMIZATION_THEME_ID` names -- the
     * last theme *edited*, which is not the theme being *viewed*, because [setTheme] deliberately
     * writes only `THEME_ID` and leaves the tag alone. Until this release `resetCategory` was the
     * one per-theme mutator taking no theme and checking nothing, so a reset pressed as the first
     * action after switching themes removed the *outgoing* theme's keys -- destroying that
     * theme's customization for good the moment [ensureFreshPendingTheme] archived what was left
     * of the scratch -- while the theme on screen, whose values come from its own archive, was not
     * reset at all and the button appeared to do nothing. Reproduced on a device: customise
     * `winter`, customise `christmas`, return to `winter` and reset one category; `christmas` lost
     * that category and `winter` kept it.
     *
     * [ensureFreshPendingTheme] runs first, exactly as it does in the setters, so the scratch
     * space is already this theme's before anything is removed from it -- which also means the
     * reset reaches a theme whose state currently lives in its archive rather than in the scratch.
     * The tag is stamped afterwards for the same reason the setters stamp it.
     */
    suspend fun resetCategory(category: ObjectCategory, forThemeId: String) = context.dataStore.editDurably { prefs ->
        prefs.ensureFreshPendingTheme(forThemeId)
        prefs.remove(Keys.visible(category))
        prefs.remove(Keys.density(category))
        prefs.remove(Keys.colorDay1(category))
        prefs.remove(Keys.colorNight1(category))
        prefs.remove(Keys.colorDay2(category))
        prefs.remove(Keys.colorNight2(category))
        // The two automatic-colour modes belong to this category's two pairs, so a reset that
        // restored the colours but left a pair on FROM_DAY would hand back a default the user
        // cannot see -- the derived half would still be overriding it.
        prefs.remove(Keys.autoMode1(category))
        prefs.remove(Keys.autoMode2(category))
        // People carry a second density that lives outside the per-category keys; resetting the
        // category has to clear it too, or "reset to default" would leave the night population
        // wherever the user had dragged it.
        if (category == ObjectCategory.PEOPLE) prefs.remove(Keys.PEOPLE_NIGHT_DENSITY)
        prefs[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
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
            remove(Keys.autoMode1(category))
            remove(Keys.autoMode2(category))
        }
        // The nine pair modes that live outside the per-category loop. They are scratch state for
        // this theme exactly like the colours they govern, and a wipe that cleared the colour but
        // left the mode would hand back a default the user cannot see -- the derived half would
        // still be overriding it. This is the same failure PEOPLE_NIGHT_DENSITY had below.
        remove(Keys.HILLS_AUTO_MODE)
        remove(Keys.mountainAutoMode(true))
        remove(Keys.mountainAutoMode(false))
        remove(Keys.LAKE_AUTO_MODE)
        remove(Keys.SKY_AUTO_MODE_HIGH)
        remove(Keys.SKY_AUTO_MODE_LOW)
        remove(Keys.CLOUDS_AUTO_MODE)
        remove(Keys.PRECIPITATION_RAIN_AUTO_MODE)
        remove(Keys.PRECIPITATION_SNOW_AUTO_MODE)
        // The night pedestrian density is per-theme scratch state exactly like everything else in
        // this list -- written by a `forThemeId` setter, read by `readFlatCustomization`, archived
        // and restored with the rest -- but it is the one such field that lives *outside* the
        // per-category keys the loop above covers, and it was missed when it was added. Left
        // behind, it survived every wipe this function performs: a theme switch carried one
        // theme's night crowd into the next theme edited (the leak `ensureFreshPendingTheme`
        // exists to prevent), "reset everything to defaults" left the slider where the user had
        // dragged it, and a backup restore left it for the first post-restore edit to pick up.
        // `resetCategory(PEOPLE, ...)` has always removed it; this is the same field, in the wipe
        // that is supposed to clear everything.
        remove(Keys.PEOPLE_NIGHT_DENSITY)
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
        val outgoing = this[Keys.PENDING_CUSTOMIZATION_THEME_ID]
        if (outgoing == forThemeId) return
        // **Archive, do not destroy.** Until v4.3 this branch called
        // [clearAllThemeCustomizationKeys] and nothing else, which is exactly right for stopping
        // the leak it was written for and exactly wrong for the user's data: the scratch space is
        // the *only* place a customization lives until the user explicitly saves the theme in the
        // gallery, so editing one control on a second theme silently deleted everything they had
        // done to the first. Reproduced on a device: customise `beach`, touch one slider on
        // `winter`, and `beach` resolves byte for byte to its factory default.
        //
        // The outgoing theme's state is now written to its own key first. The wipe still happens
        // -- the flat keys must not carry one theme's values into another, which is the whole
        // point of the guard -- but what it wipes has already been kept.
        if (outgoing != null) {
            this[Keys.themeCustomization(outgoing)] =
                readFlatCustomization(this, outgoing).toJson().toString()
        }
        clearAllThemeCustomizationKeys()
        // And the incoming theme's own archived state is restored, so re-editing a theme picks up
        // where the user left off rather than starting from defaults.
        this[Keys.themeCustomization(forThemeId)]?.let { stored ->
            writeFlatCustomization(sceneCustomizationFromJson(runCatching { JSONObject(stored) }.getOrNull()))
        }
    }

    /** Resets every object category (structural and seasonal alike -- both are per-theme scratch
     * state now, see the [ObjectCategory] doc comment) back to defaults and clears the
     * pending-edit tag entirely. */
    suspend fun resetAllCategories(forThemeId: String) = context.dataStore.editDurably { prefs ->
        // Only if the scratch space is *this* theme's. Clearing it unconditionally would reset
        // whichever theme happened to be under live edit -- caught by
        // `ThemeCustomizationPersistenceTest.resetIsTheOnlyThingThatRemovesACustomization`, which
        // resets `beach` while `city` is the theme being edited and asserts `city` is untouched.
        if (prefs[Keys.PENDING_CUSTOMIZATION_THEME_ID] == forThemeId) {
            prefs.clearAllThemeCustomizationKeys()
            prefs.remove(Keys.PENDING_CUSTOMIZATION_THEME_ID)
        }
        // A reset is the one place a customization is *meant* to disappear, so the archive goes
        // too -- otherwise "reset this theme" would leave the old look waiting to be restored the
        // next time the theme was edited. Explicit and user-driven, which is the only way a
        // customization may now be lost.
        prefs.remove(Keys.themeCustomization(forThemeId))
    }
}
