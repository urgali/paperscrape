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
import com.paperscrape.livewallpaper.engine.CustomThemeRegistry
import com.paperscrape.livewallpaper.engine.CalendarWindow
import com.paperscrape.livewallpaper.engine.SeasonalCalendar
import com.paperscrape.livewallpaper.engine.seasonalCalendarFromJsonString
import com.paperscrape.livewallpaper.engine.toJsonString
import com.paperscrape.livewallpaper.engine.ObjectVariantConfig
import com.paperscrape.livewallpaper.engine.PeopleDensity
import com.paperscrape.livewallpaper.engine.MountainLayerConfig
import com.paperscrape.livewallpaper.engine.DecorationExclusion
import com.paperscrape.livewallpaper.engine.DistantHousesConfig
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
import com.paperscrape.livewallpaper.engine.publicsCarriedOver
import com.paperscrape.livewallpaper.engine.sceneCustomizationFromJson
import com.paperscrape.livewallpaper.engine.themeKnownAs
import com.paperscrape.livewallpaper.engine.toJson
import com.paperscrape.livewallpaper.weather.LiveWeatherStatus
import com.paperscrape.livewallpaper.weather.WeatherProviderId
import org.json.JSONObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.IOException

/** The name of this store's file, shared with the instrumented tests rather than copied by hand. */
internal const val WALLPAPER_PREFS_STORE_NAME = "paperscrape_prefs"

// Without `corruptionHandler` a file damaged by a bad shutdown kills the process on every restart until
// the user clears app data. [PrefsRecovery] says why corruption, I/O failure and other errors get three
// answers, and why replacing this file cannot cost the user their custom themes.
private val Context.dataStore by preferencesDataStore(
    name = WALLPAPER_PREFS_STORE_NAME,
    corruptionHandler = PrefsRecovery.replacingCorruptFile(),
)

/** Immutable snapshot of all user-configurable wallpaper settings. */
data class WallpaperSettings(
    val themeId: String = "sunset",
    val syncWithRealTime: Boolean = true,
    val useLocationForSunTimes: Boolean = false,
    // A position typed by the user instead of the phone's fix; exclusive with useLocationForSunTimes
    // (WallpaperPrefs.setUseCustomLocation/setUseLocation). The sun times and Live Weather read whichever is on.
    val useCustomLocation: Boolean = false,
    /**
     * Which of the device's positioning systems [useLocationForSunTimes] means. Stored apart from the
     * on/off flag, so an install with one "Phone" setting and no key for this reads as
     * [DeviceLocationKind.NETWORK], the provider it was asking first: its behaviour and its permission
     * stay put.
     */
    val deviceLocationKind: DeviceLocationKind = DeviceLocationKind.NETWORK,
    val customLocationLatitude: Float = 45.4642f, // Milan -- an arbitrary but real default so a
    val customLocationLongitude: Float = 9.19f, // freshly-enabled toggle isn't at (0,0) in the ocean
    val customLocationLabel: String = "",
    // Live Weather: global, like the location modes it needs to know where to fetch for.
    // liveWeatherApiKey is the user's own Open-Meteo key and the only one there is (a key compiled in
    // would be readable in the dex): blank is the free keyless tier, set is the customer endpoint
    // (OpenMeteoProvider.resolveApiKey).
    val liveWeatherEnabled: Boolean = false,
    val liveWeatherApiKey: String = "",
    /**
     * Which service Live Weather fetches from, stored by
     * [com.paperscrape.livewallpaper.weather.WeatherProviderId.storageId]: a string, so reordering the
     * enum cannot reinterpret a choice and an unknown value reads as the default. Apart from every
     * other weather setting: changing provider disturbs neither the location nor the other keys.
     */
    val weatherProviderId: String = WeatherProviderId.DEFAULT.storageId,
    /**
     * The user's WeatherAPI.com key. Never compiled in, never logged, never sent anywhere but
     * WeatherAPI.com; kept apart from [liveWeatherApiKey] so switching provider and back loses neither.
     */
    val weatherApiComApiKey: String = "",
    /**
     * The user's OpenWeather key. Same rules as [weatherApiComApiKey]: never compiled in, never
     * logged, never sent anywhere but OpenWeather, and kept apart so switching provider and back
     * loses neither.
     */
    val openWeatherApiKey: String = "",
    /**
     * What Live Weather is actually doing, as a [LiveWeatherStatus.storageId]: the reason, not the
     * symptom, because the settings screen tells them apart (a missing key is one tap from fixed, a
     * dropped request is not).
     *
     * **Written by the wallpaper service, read by the settings screen**, and here rather than in a
     * store of its own because the settings flow already reaches the UI live. Not a user preference:
     * nothing in the UI sets it.
     */
    val liveWeatherStatus: String = LiveWeatherStatus.OFF.storageId,
    /**
     * Whether the app may check GitHub for a new release on its own: when the settings screen opens,
     * and -- with [updateNotificationsEnabled] -- from the update job every three hours
     * (`UpdateCheckJobService`). Off by default: a network request the user did not ask for. The
     * manual button is always there.
     */
    val automaticUpdateCheckEnabled: Boolean = false,
    /**
     * Whether a release found while nobody is looking may become a system notification. **A second
     * switch, not a new meaning for the first**: the one above was turned on by people who wanted a
     * prompt when they open the app, and a notification would change, unasked, what a control they
     * set does. Off by default, and inert while [automaticUpdateCheckEnabled] is off (the row is greyed,
     * not hidden, so the order to switch them on in shows).
     */
    val updateNotificationsEnabled: Boolean = false,
    // The device fix PaperWallpaperService resolved (WallpaperPrefs.setResolvedGpsLocation, at every
    // fix), apart from the typed custom location. The settings name the place, not the coordinates:
    // the device fix reverse-geocoded (`LocationRow` in WeatherTimeScreen.kt), a custom location by
    // the label saved with it. Null until the first fix.
    val resolvedGpsLatitude: Float? = null,
    val resolvedGpsLongitude: Float? = null,
    /**
     * When the saved device fix above was taken, as epoch millis; 0 when there has never been one.
     * Written with every fix and read by nothing: the engine reuses a saved fix with no expiry
     * (`PaperWallpaperService.savedDeviceFix`) and the settings do not show its age: a place does
     * not move, and the settings already name it.
     */
    val deviceFixTimestampMillis: Long = 0L,
    val fixedHour: Float = 18f, // used only when syncWithRealTime == false
    val parallaxStrength: Float = 1f, // 0.5 .. 2.0: how far near and far layers move apart while scrolling
    // The scroll settings are global, not per theme: how the wallpaper moves, not how a theme looks.
    val scrollBackground: Boolean = false, // whether sun/moon/sky scroll with the parallax hills
    val swipeScroll: Boolean = true, // whether swiping between home screens scrolls the wallpaper at all
    /**
     * Whether this phone's home screen has ever moved the wallpaper with a swipe: written once, by the
     * wallpaper, the first time an engine that is not a preview sees the offset move (`SwipeReport`).
     * *Swipe scroll* reads on only after that (`SettingsUiModel.swipeScroll`), because many home
     * screens never report a swipe and the switch would be on over nothing. Runtime state of this
     * phone, like the GPS fix: a backup does not carry it and a restore leaves it.
     */
    val swipeReported: Boolean = false,
    // The continuous drift, independent of swiping: multiplies each frame's time step, not a swipe
    // offset. On by default, slow.
    val scrollSpeed: Float = 0.15f,
    val autoThemeByDate: Boolean = false, // opt-in: overrides themeId during known seasonal windows
    /**
     * The user's edits to the holiday calendar, or [SeasonalCalendar.DEFAULT] if they have made none.
     * **Only the difference is stored**: the factory dates live in [CalendarWindow], so an install
     * that never edited the calendar resolves every date from them (`SeasonalCalendarIdentityTest`), and
     * a factory boundary moved in the code moves for everyone who has not overridden that window.
     */
    val seasonalCalendar: SeasonalCalendar = SeasonalCalendar.DEFAULT,
    /** In-progress (not yet saved) scene-object edits, and which theme they belong to. Only
     * applied when [pendingCustomizationThemeId] matches the theme actually being rendered --
     * see [com.paperscrape.livewallpaper.engine.CustomThemeRegistry.resolveActiveCustomization].
     * Switching to a different theme does *not* clear these (so switching back mid-edit resumes
     * where you left off), but they simply won't apply anywhere except that one theme. */
    val pendingCustomization: SceneCustomization = SceneCustomization.DEFAULT,
    val pendingCustomizationThemeId: String? = null,
    /**
     * Every theme that has a customization of its own on disk, keyed by theme id: **what makes a
     * per-theme edit survive editing another theme**. A live edit lands in the one-theme scratch space
     * [pendingCustomization] reads; it is archived here when another theme is edited, and restored
     * from here when this one is edited again. A theme never customised is absent and reads its defaults.
     */
    val themeCustomizations: Map<String, SceneCustomization> = emptyMap(),
) {

    /** [weatherProviderId] resolved; an unrecognised stored id reads as the default. */
    val weatherProvider: WeatherProviderId
        get() = WeatherProviderId.fromStorageId(weatherProviderId)

    /**
     * The key the **selected** provider is called with. Open-Meteo's may be blank (its free keyless
     * tier); a blank one for the others gives
     * [com.paperscrape.livewallpaper.weather.WeatherFetchResult.MissingApiKey] instead of a request.
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

/** Object categories customised one by one (visibility, density, two day/night colours), per theme,
 * through [WallpaperSettings.pendingCustomization]: the structural ones (houses, buildings, cars,
 * parasols, trees, shops, public buildings, people) on "World & scene", the seasonal decorations on
 * "Seasons & decorations". Both start from [defaultCustomizationFor], which adjusts them per theme
 * (City's dense towers, Christmas's snowmen). The storage is generic: every category has the four
 * colour keys, read only where the category has colours. */
enum class ObjectCategory {
    HOUSES, BUILDINGS, CARS, PARASOLS, TREES,
    // The shops' colours and opening hours (`SHOP_HOURS_ENABLED` and its two hours, which
    // `resetCategory(SHOPS)` puts back); whether they stand is BUILDINGS' switch. A store without these
    // keys is read as `WallpaperPrefs.readFlatCustomization` says.
    SHOPS,
    // The public buildings (the school, the fire station, the police station): colours and opening hours
    // (`PUBLIC_HOURS_ENABLED` and its two hours), as SHOPS.
    PUBLICS,
    // Visibility and density only: the people's artwork is finished, and a tint over it is what
    // `DESIGN_NOTES.md` decision 25 prevents.
    PEOPLE,
    SNOWMEN, GIFTS, PENGUINS, BUNNIES, EASTER_EGGS, PUMPKINS,
    // Visibility and density only, as PEOPLE: fixed art. A store without them reads the theme's own (off
    // but on Halloween).
    SCARECROWS,
    ;

    companion object {
        /**
         * The seven categories Seasons & decorations shows, each behind its season: what its "Reset
         * decorations to defaults" resets ([WallpaperPrefs.resetDecorations]), and nothing else.
         */
        val DECORATIONS: List<ObjectCategory> = listOf(SNOWMEN, GIFTS, PENGUINS, BUNNIES, EASTER_EGGS, PUMPKINS, SCARECROWS)
    }
}

/** Key-name prefix for [WallpaperPrefs]'s per-theme customization blobs. */
internal const val THEME_CUSTOMIZATION_KEY_PREFIX = "theme_customization_"

/**
 * A place's name or a key as kept: at most [AppBackup.MAX_TEXT_SETTING_CHARS] characters, so a backup gives back every
 * text the app keeps; a character made of two is not cut in half.
 */
internal fun keptText(text: String): String {
    if (text.length <= AppBackup.MAX_TEXT_SETTING_CHARS) return text
    val end = if (Character.isHighSurrogate(text[AppBackup.MAX_TEXT_SETTING_CHARS - 1])) AppBackup.MAX_TEXT_SETTING_CHARS - 1 else AppBackup.MAX_TEXT_SETTING_CHARS
    return text.substring(0, end)
}

/**
 * `DataStore.edit`, but the write finishes even if the caller's scope is cancelled. A control's write
 * is launched from `rememberCoroutineScope()`, which an Activity recreation (rotation, light/dark, font
 * size) cancels: a tap in that frame would bounce back, the app seeming to ignore the user. A write is a
 * few bytes and milliseconds, so making it uninterruptible costs nothing (`DurablePrefsWriteTest`).
 */
private suspend fun DataStore<Preferences>.editDurably(
    transform: suspend (MutablePreferences) -> Unit,
): Preferences = withContext(NonCancellable) { edit(transform) }

/**
 * The wallpaper's preferences, over one DataStore.
 *
 * The app builds it from a [Context], the process's one store file. The store is a constructor
 * parameter so a JVM test hands it a store over a scratch file and reads exactly what the app reads,
 * with the keys an older build wrote (`PalmsPreferenceStoreTest`).
 */
class WallpaperPrefs internal constructor(
    private val store: DataStore<Preferences>,
    /**
     * The look a theme was saved with in the gallery, if it was (`CustomThemeRegistry.savedCustomizationFor`):
     * what the first edit of such a theme starts from ([ensureFreshPendingTheme]). A parameter so a JVM
     * test can hand it its own saved looks; the app reads the registry.
     */
    private val savedLookFor: (String) -> SceneCustomization? = { CustomThemeRegistry.savedCustomizationFor(it) },
) {

    constructor(context: Context) : this(context.dataStore)

    private object Keys {
        /**
         * Night-time pedestrian and car densities: absent reads as the daytime value
         * ([PeopleDensity.resolveNightDensity]), so a store without them draws the night it drew.
         */
        val PEOPLE_NIGHT_DENSITY = floatPreferencesKey("people_night_density")
        val CARS_NIGHT_DENSITY = floatPreferencesKey("cars_night_density")

        /**
         * The opening hours, in three groups: the towers', the shops' (the restaurant, the bar and the
         * shop) and the public buildings' ([PUBLIC_HOURS_ENABLED]). Absent keys read as the
         * [SceneCustomization] defaults (switch off), unless the store holds the single setting
         * [BUSINESS_HOURS_ENABLED], which each group reads in their place.
         */
        val SHOP_HOURS_ENABLED = booleanPreferencesKey("shop_hours_enabled")
        val SHOP_OPEN_HOUR = floatPreferencesKey("shop_open_hour")
        val SHOP_CLOSE_HOUR = floatPreferencesKey("shop_close_hour")
        val TOWER_HOURS_ENABLED = booleanPreferencesKey("tower_hours_enabled")
        val TOWER_OPEN_HOUR = floatPreferencesKey("tower_open_hour")
        val TOWER_CLOSE_HOUR = floatPreferencesKey("tower_close_hour")

        /**
         * The public buildings' opening hours (the school, the fire station and the police station). A
         * store without them kept the school on the shops' hours: each reads the shops' in its place
         * until the first edit writes them ([carryPublicsOver], [Keys.SCRATCH_KNOWS_PUBLICS]).
         */
        val PUBLIC_HOURS_ENABLED = booleanPreferencesKey("public_hours_enabled")
        val PUBLIC_OPEN_HOUR = floatPreferencesKey("public_open_hour")
        val PUBLIC_CLOSE_HOUR = floatPreferencesKey("public_close_hour")

        /**
         * The single opening-hours setting older stores hold, for the shops and the towers alike. Read,
         * never written: it is read into both groups (whoever had the hours on finds them on in both, at
         * the same hours), and the first edit writes both groups' keys from it and removes it
         * ([carryHoursOver]).
         */
        val BUSINESS_HOURS_ENABLED = booleanPreferencesKey("business_hours_enabled")
        val BUSINESS_OPEN_HOUR = floatPreferencesKey("business_open_hour")
        val BUSINESS_CLOSE_HOUR = floatPreferencesKey("business_close_hour")

        /**
         * The saved-themes document an import has written here but not yet into its own store.
         *
         * An import writes two DataStores with no transaction spanning them, and a process killed
         * between the two would leave the preferences new and the saved themes old. So the second
         * store's whole payload is written **inside the first store's atomic edit**, applied, then
         * cleared: the next start finds either nothing pending or the exact bytes the second store
         * should hold, and applying them again is idempotent.
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
        // A store may still hold `visual_crossing_api_key` and the provider id `visual_crossing`, a
        // service the app does not talk to: the key is left unread, and the id reads as the default
        // (`WeatherProviderId.fromStorageId`, WeatherProviderSelectionTest).
        val WEATHER_API_COM_API_KEY = stringPreferencesKey("weatherapi_com_api_key")
        val OPEN_WEATHER_API_KEY = stringPreferencesKey("open_weather_api_key")
        val LIVE_WEATHER_STATUS = stringPreferencesKey("live_weather_status")
        val AUTOMATIC_UPDATE_CHECK = booleanPreferencesKey("automatic_update_check")
        val UPDATE_NOTIFICATIONS = booleanPreferencesKey("update_notifications")
        val RESOLVED_GPS_LAT = floatPreferencesKey("resolved_gps_lat")
        val RESOLVED_GPS_LON = floatPreferencesKey("resolved_gps_lon")
        val DEVICE_FIX_AT = longPreferencesKey("device_fix_at")
        val DEVICE_LOCATION_KIND = stringPreferencesKey("device_location_kind")
        val FIXED_HOUR = floatPreferencesKey("fixed_hour")
        val PARALLAX_STRENGTH = floatPreferencesKey("parallax_strength")
        val AUTO_THEME_BY_DATE = booleanPreferencesKey("auto_theme_by_date")
        val SEASONAL_CALENDAR = stringPreferencesKey("seasonal_calendar_json")
        val PENDING_CUSTOMIZATION_THEME_ID = stringPreferencesKey("pending_customization_theme_id")

        /**
         * One theme's whole customization, as JSON, under a key named after that theme: the same
         * serialisation `CustomThemeStore` saves themes with (round-trip tested by
         * `CustomThemeDataJsonTest`), so a saved theme and a customised built-in are one format.
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
         * Which half of each pair the user owns, stored as [AutoColorMode.storageId]: a string, for
         * three states and so that a constant inserted in the enum repaints nothing. Absent means
         * [AutoColorMode.MANUAL].
         */
        fun autoMode1(category: ObjectCategory) = stringPreferencesKey("obj_${category.name}_auto_mode_1")
        fun autoMode2(category: ObjectCategory) = stringPreferencesKey("obj_${category.name}_auto_mode_2")
        val HILLS_VARIATION = floatPreferencesKey("hills_variation")
        val SNOW_PILES = floatPreferencesKey("snow_piles")
        val LEAF_PILES = floatPreferencesKey("leaf_piles")
        val HILLS_COLOR_DAY = intPreferencesKey("hills_color_day")
        val HILLS_COLOR_NIGHT = intPreferencesKey("hills_color_night")
        val HILLS_AUTO_MODE = stringPreferencesKey("hills_auto_mode")
        val SCROLL_BACKGROUND = booleanPreferencesKey("scroll_background")
        val SWIPE_SCROLL = booleanPreferencesKey("swipe_scroll")
        val SWIPE_REPORTED = booleanPreferencesKey("swipe_reported")
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
        /**
         * *Lights on the buildings*, under the key of the single Christmas switch older stores hold, which the other two
         * read where they have no key of their own ([CHRISTMAS_TREE_LIGHTS_ENABLED], [CHRISTMAS_FIRS_ENABLED]).
         */
        val CHRISTMAS_DECORATIONS_ENABLED = booleanPreferencesKey("christmas_decorations_enabled")
        /** *Lights on the trees* and *Christmas firs*. Absent, each reads [CHRISTMAS_DECORATIONS_ENABLED]. */
        val CHRISTMAS_TREE_LIGHTS_ENABLED = booleanPreferencesKey("christmas_tree_lights_enabled")
        val CHRISTMAS_FIRS_ENABLED = booleanPreferencesKey("christmas_firs_enabled")
        val FLOWERS_ENABLED = booleanPreferencesKey("flowers_enabled")
        val PALMS_ENABLED = booleanPreferencesKey("palms_enabled")
        /**
         * The Palms switch on a theme whose layout plants no palm. Absent reads off: [PALMS_ENABLED]
         * is `true` on every theme, chosen or not (`SceneCustomization.palmsInsteadOfTrees`).
         */
        val PALMS_INSTEAD_OF_TREES = booleanPreferencesKey("palms_instead_of_trees")
        val HALLOWEEN_ENABLED = booleanPreferencesKey("halloween_enabled")
        val HORROR_SKY_ENABLED = booleanPreferencesKey("horror_sky_enabled")
        val SANTA_ENABLED = booleanPreferencesKey("santa_enabled")

        /**
         * The Halloween switches. Absent, each reads as the theme's own: on in the Halloween theme, off in every other --
         * and the decorations off where the Christmas ones are on, the side chosen first (`DecorationExclusion`).
         */
        val LAUGHING_MOON_ENABLED = booleanPreferencesKey("laughing_moon_enabled")
        val NIGHT_SKY_BY_DAY_ENABLED = booleanPreferencesKey("night_sky_by_day_enabled")
        val HALLOWEEN_DECORATIONS_ENABLED = booleanPreferencesKey("halloween_decorations_enabled")
        val BLOOD_RAIN_ENABLED = booleanPreferencesKey("blood_rain_enabled")
        val HEADLESS_HORSEMAN_ENABLED = booleanPreferencesKey("headless_horseman_enabled")

        /** *Moon stays in place*. Absent, it reads as the theme's own, as the switches above. */
        val STILL_MOON_ENABLED = booleanPreferencesKey("still_moon_enabled")

        /** The distant houses on the mountains. Absent reads off, at 50 %. */
        val DISTANT_HOUSES_VISIBLE = booleanPreferencesKey("distant_houses_visible")
        val DISTANT_HOUSES_DENSITY = floatPreferencesKey("distant_houses_density")

        /**
         * Whether the live edit's keys were written by a build that knows the shops have colours of
         * their own. Absent in a store whose shops were drawn in the Buildings colours; set by the
         * first edit, which carries those colours into the shops' keys first ([ensureFreshPendingTheme],
         * [readFlatCustomization]).
         */
        val SCRATCH_KNOWS_SHOPS = booleanPreferencesKey("scratch_knows_shops")

        /**
         * The same for the public buildings' colours and hours: absent in a store whose school wore the
         * shops', set by the first edit, which carries them over first ([carryPublicsOver]).
         */
        val SCRATCH_KNOWS_PUBLICS = booleanPreferencesKey("scratch_knows_publics")
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

    // **Read again when the saved themes change** ([CustomThemeRegistry.generationFlow]): a theme of the
    // user's own resolves its defaults (and a store's public buildings without keys) through the registry,
    // which its own collector fills in no fixed order with this one.
    val settingsFlow: Flow<WallpaperSettings> = store.data.recoveringFromReadErrors()
        .combine(CustomThemeRegistry.generationFlow) { prefs, _ -> prefs }
        .map { prefs -> settingsOf(prefs) }

    /**
     * The settings as stored, read once, or `null` when the store cannot be read just now (an `IOException`).
     *
     * For a reader that keeps what it reads, a backup: [settingsFlow] answers such a read with a fresh install's
     * settings ([PrefsRecovery.recoveringFromReadErrors]), which a backup would keep, and restore, as the user's. A
     * corrupt file is the store's handler's: rewritten empty once, it reads as a fresh install's.
     */
    suspend fun readSettings(): WallpaperSettings? {
        val prefs = try {
            store.data.first()
        } catch (_: IOException) {
            return null
        }
        return settingsOf(prefs)
    }

    /** Every setting [prefs] holds, an absent one read as a fresh install's. */
    private fun settingsOf(prefs: Preferences): WallpaperSettings {
        // The scratch keys are read against the defaults of the theme they are tagged with, so turning
        // Christmas's snowmen off leaves its other categories at Christmas's defaults, not flat ones.
        // Untagged, "sunset" (all off): pendingCustomization applies only to the theme it is tagged with
        // (CustomThemeRegistry.resolveActiveCustomization).
        val pendingThemeId = prefs[Keys.PENDING_CUSTOMIZATION_THEME_ID]
        return WallpaperSettings(
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
            updateNotificationsEnabled = prefs[Keys.UPDATE_NOTIFICATIONS] ?: false,
            resolvedGpsLatitude = prefs[Keys.RESOLVED_GPS_LAT],
            resolvedGpsLongitude = prefs[Keys.RESOLVED_GPS_LON],
            deviceFixTimestampMillis = prefs[Keys.DEVICE_FIX_AT] ?: 0L,
            fixedHour = prefs[Keys.FIXED_HOUR] ?: 18f,
            parallaxStrength = prefs[Keys.PARALLAX_STRENGTH] ?: 1f,
            scrollBackground = prefs[Keys.SCROLL_BACKGROUND] ?: false,
            swipeScroll = prefs[Keys.SWIPE_SCROLL] ?: true,
            swipeReported = prefs[Keys.SWIPE_REPORTED] ?: false,
            scrollSpeed = prefs[Keys.SCROLL_SPEED] ?: 0.15f,
            autoThemeByDate = prefs[Keys.AUTO_THEME_BY_DATE] ?: false,
            seasonalCalendar = seasonalCalendarFromJsonString(prefs[Keys.SEASONAL_CALENDAR]),
            pendingCustomizationThemeId = prefs[Keys.PENDING_CUSTOMIZATION_THEME_ID],
            pendingCustomization = readFlatCustomization(prefs, pendingThemeId ?: "sunset"),
            // The theme under live edit is in the scratch keys until the user leaves it: folded in, so a
            // reader (a backup taken mid-edit) sees the edit.
            themeCustomizations = readThemeCustomizations(prefs).let { stored ->
                if (pendingThemeId == null) stored
                else stored + (pendingThemeId to readFlatCustomization(prefs, pendingThemeId))
            },
        )
    }

    /**
     * The customization the scratch keys hold, read against [themeId]'s defaults: what [settingsFlow]
     * shows and what [ensureFreshPendingTheme] archives.
     */
    private fun readFlatCustomization(prefs: Preferences, themeId: String): SceneCustomization {
        val defaults = defaultCustomizationFor(themeId)
        val shops = readShops(prefs, defaults)
        // Each group's own keys, or the single setting an older store holds.
        val shopHoursEnabled = prefs[Keys.SHOP_HOURS_ENABLED] ?: prefs[Keys.BUSINESS_HOURS_ENABLED] ?: defaults.shopHoursEnabled
        val shopOpenHour = prefs[Keys.SHOP_OPEN_HOUR] ?: prefs[Keys.BUSINESS_OPEN_HOUR] ?: defaults.shopOpenHour
        val shopCloseHour = prefs[Keys.SHOP_CLOSE_HOUR] ?: prefs[Keys.BUSINESS_CLOSE_HOUR] ?: defaults.shopCloseHour
        // The public buildings' hours: their keys; in a store that does not know them, the shops' hours (which
        // its school kept); once it knows them, the theme's defaults.
        val publicsKnown = prefs[Keys.SCRATCH_KNOWS_PUBLICS] == true
        // The three Christmas switches: the single switch's key is the first's, and the other two read it where they
        // have none -- whoever had the lights on finds all three on.
        val christmasLights = prefs[Keys.CHRISTMAS_DECORATIONS_ENABLED]
        val christmasBuildingLights = christmasLights ?: defaults.christmasBuildingLightsEnabled
        val christmasTreeLights = prefs[Keys.CHRISTMAS_TREE_LIGHTS_ENABLED] ?: christmasLights ?: defaults.christmasTreeLightsEnabled
        val christmasFirs = prefs[Keys.CHRISTMAS_FIRS_ENABLED] ?: christmasLights ?: defaults.christmasFirsEnabled
        // Without a key of their own, the Halloween decorations are the theme's -- and off where the Christmas ones are
        // on, the side chosen first (`DecorationExclusion`).
        val halloweenDecorations = prefs[Keys.HALLOWEEN_DECORATIONS_ENABLED]
            ?: (defaults.halloweenDecorationsEnabled && !(christmasBuildingLights || christmasTreeLights || christmasFirs))
        return SceneCustomization(
            houses = readVariantConfig(prefs, ObjectCategory.HOUSES, defaults.houses),
            buildings = readVariantConfig(prefs, ObjectCategory.BUILDINGS, towersDrawnIn(prefs, defaults.buildings)),
            shops = shops,
            publics = readPublics(prefs, defaults, shops),
            cars = readVariantConfig(prefs, ObjectCategory.CARS, defaults.cars),
            parasols = readVariantConfig(prefs, ObjectCategory.PARASOLS, defaults.parasols),
        people = readVariantConfig(prefs, ObjectCategory.PEOPLE, defaults.people),
        peopleNightDensity = PeopleDensity.resolveNightDensity(
            stored = prefs[Keys.PEOPLE_NIGHT_DENSITY],
            dayDensity = prefs[Keys.density(ObjectCategory.PEOPLE)] ?: defaults.people.density,
        ),
        // The pedestrians' rule, against the cars' own daytime value.
        carsNightDensity = PeopleDensity.resolveNightDensity(
            stored = prefs[Keys.CARS_NIGHT_DENSITY],
            dayDensity = prefs[Keys.density(ObjectCategory.CARS)] ?: defaults.cars.density,
        ),
        shopHoursEnabled = shopHoursEnabled,
        shopOpenHour = shopOpenHour,
        shopCloseHour = shopCloseHour,
        towerHoursEnabled = prefs[Keys.TOWER_HOURS_ENABLED] ?: prefs[Keys.BUSINESS_HOURS_ENABLED] ?: defaults.towerHoursEnabled,
        towerOpenHour = prefs[Keys.TOWER_OPEN_HOUR] ?: prefs[Keys.BUSINESS_OPEN_HOUR] ?: defaults.towerOpenHour,
        towerCloseHour = prefs[Keys.TOWER_CLOSE_HOUR] ?: prefs[Keys.BUSINESS_CLOSE_HOUR] ?: defaults.towerCloseHour,
        publicHoursEnabled = prefs[Keys.PUBLIC_HOURS_ENABLED]
            ?: if (publicsKnown) defaults.publicHoursEnabled else shopHoursEnabled,
        publicOpenHour = prefs[Keys.PUBLIC_OPEN_HOUR] ?: if (publicsKnown) defaults.publicOpenHour else shopOpenHour,
        publicCloseHour = prefs[Keys.PUBLIC_CLOSE_HOUR] ?: if (publicsKnown) defaults.publicCloseHour else shopCloseHour,
            trees = readVariantConfig(prefs, ObjectCategory.TREES, defaults.trees),
            snowmen = readVariantConfig(prefs, ObjectCategory.SNOWMEN, defaults.snowmen),
            gifts = readVariantConfig(prefs, ObjectCategory.GIFTS, defaults.gifts),
            penguins = readVariantConfig(prefs, ObjectCategory.PENGUINS, defaults.penguins),
            bunnies = readVariantConfig(prefs, ObjectCategory.BUNNIES, defaults.bunnies),
            easterEggs = readVariantConfig(prefs, ObjectCategory.EASTER_EGGS, defaults.easterEggs),
            pumpkins = readVariantConfig(prefs, ObjectCategory.PUMPKINS, defaults.pumpkins),
            scarecrows = readVariantConfig(prefs, ObjectCategory.SCARECROWS, defaults.scarecrows),
            hillsVariation = prefs[Keys.HILLS_VARIATION] ?: defaults.hillsVariation,
            snowPiles = prefs[Keys.SNOW_PILES] ?: defaults.snowPiles,
            leafPiles = prefs[Keys.LEAF_PILES] ?: defaults.leafPiles,
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
            distantHouses = DistantHousesConfig(
                visible = prefs[Keys.DISTANT_HOUSES_VISIBLE] ?: defaults.distantHouses.visible,
                density = prefs[Keys.DISTANT_HOUSES_DENSITY] ?: defaults.distantHouses.density,
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
            christmasBuildingLightsEnabled = christmasBuildingLights,
            christmasTreeLightsEnabled = christmasTreeLights,
            christmasFirsEnabled = christmasFirs,
            flowersEnabled = prefs[Keys.FLOWERS_ENABLED] ?: defaults.flowersEnabled,
            palmsEnabled = prefs[Keys.PALMS_ENABLED] ?: defaults.palmsEnabled,
            palmsInsteadOfTrees = prefs[Keys.PALMS_INSTEAD_OF_TREES] ?: defaults.palmsInsteadOfTrees,
            halloweenEnabled = prefs[Keys.HALLOWEEN_ENABLED] ?: defaults.halloweenEnabled,
            horrorSkyEnabled = prefs[Keys.HORROR_SKY_ENABLED] ?: defaults.horrorSkyEnabled,
            laughingMoonEnabled = prefs[Keys.LAUGHING_MOON_ENABLED] ?: defaults.laughingMoonEnabled,
            nightSkyByDayEnabled = prefs[Keys.NIGHT_SKY_BY_DAY_ENABLED] ?: defaults.nightSkyByDayEnabled,
            stillMoonEnabled = prefs[Keys.STILL_MOON_ENABLED] ?: defaults.stillMoonEnabled,
            halloweenDecorationsEnabled = halloweenDecorations,
            bloodRainEnabled = prefs[Keys.BLOOD_RAIN_ENABLED] ?: defaults.bloodRainEnabled,
            headlessHorsemanEnabled = prefs[Keys.HEADLESS_HORSEMAN_ENABLED] ?: defaults.headlessHorsemanEnabled,
            santaEnabled = prefs[Keys.SANTA_ENABLED] ?: defaults.santaEnabled,
        ).let { DecorationExclusion.resolvedOn(it, defaults, themeId) }
    }

    /**
     * The shops' colours in the live edit.
     *
     * **A store without shop keys drew its shops in the Buildings colours.** Where its live edit holds
     * a Buildings colour the user chose, the shops read as that pair (each colour stored, or the slate
     * of [SceneCustomization.DEFAULT], and the stored modes), keeping the look; where it holds none,
     * they start from the theme's own shop colours, as the towers do. The first edit writes that into
     * the shop keys and marks the space ([Keys.SCRATCH_KNOWS_SHOPS], [ensureFreshPendingTheme]); then
     * "Reset Shops to default" means the theme's shop colours.
     */
    private fun readShops(prefs: Preferences, defaults: SceneCustomization): ObjectVariantConfig {
        if (prefs[Keys.SCRATCH_KNOWS_SHOPS] == true || !holdsCategoryColour(prefs, ObjectCategory.BUILDINGS)) {
            return readVariantConfig(prefs, ObjectCategory.SHOPS, defaults.shops)
        }
        val drawnIn = SceneCustomization.DEFAULT.buildings
        return defaults.shops.copy(
            colorDay1 = prefs[Keys.colorDay1(ObjectCategory.BUILDINGS)] ?: drawnIn.colorDay1,
            colorNight1 = prefs[Keys.colorNight1(ObjectCategory.BUILDINGS)] ?: drawnIn.colorNight1,
            colorDay2 = prefs[Keys.colorDay2(ObjectCategory.BUILDINGS)] ?: drawnIn.colorDay2,
            colorNight2 = prefs[Keys.colorNight2(ObjectCategory.BUILDINGS)] ?: drawnIn.colorNight2,
            autoMode1 = AutoColorMode.fromStorageId(prefs[Keys.autoMode1(ObjectCategory.BUILDINGS)]),
            autoMode2 = AutoColorMode.fromStorageId(prefs[Keys.autoMode2(ObjectCategory.BUILDINGS)]),
        )
    }

    /**
     * The public buildings' colours in the live edit. **A store without public keys dressed its school
     * in the shops' colours**, read as [publicsCarriedOver] says: [shops] where changed by hand, the
     * theme's own pair where not. The first edit writes that into the public keys and marks the space
     * ([Keys.SCRATCH_KNOWS_PUBLICS], [carryPublicsOver]).
     */
    private fun readPublics(prefs: Preferences, defaults: SceneCustomization, shops: ObjectVariantConfig): ObjectVariantConfig =
        if (prefs[Keys.SCRATCH_KNOWS_PUBLICS] == true || holdsCategoryColour(prefs, ObjectCategory.PUBLICS)) {
            readVariantConfig(prefs, ObjectCategory.PUBLICS, defaults.publics)
        } else {
            publicsCarriedOver(shops, defaults)
        }

    /**
     * What the live edit's Buildings keys are read against: the theme's defaults -- or, for a store
     * without shop keys that holds a Buildings colour the user chose, the slate its towers were drawn
     * in, so the pair keeps its look instead of mixing the user's colour with the theme's. Written into
     * the keys by [carryShopsOver] at the first edit.
     */
    private fun towersDrawnIn(prefs: Preferences, defaults: ObjectVariantConfig): ObjectVariantConfig {
        if (prefs[Keys.SCRATCH_KNOWS_SHOPS] == true || !holdsCategoryColour(prefs, ObjectCategory.BUILDINGS)) return defaults
        val slate = SceneCustomization.DEFAULT.buildings
        return defaults.copy(
            colorDay1 = slate.colorDay1, colorNight1 = slate.colorNight1,
            colorDay2 = slate.colorDay2, colorNight2 = slate.colorNight2,
        )
    }

    /** Whether the live edit holds any of [category]'s four colours or two modes. */
    private fun holdsCategoryColour(prefs: Preferences, category: ObjectCategory): Boolean =
        prefs[Keys.colorDay1(category)] != null || prefs[Keys.colorNight1(category)] != null ||
            prefs[Keys.colorDay2(category)] != null || prefs[Keys.colorNight2(category)] != null ||
            prefs[Keys.autoMode1(category)] != null || prefs[Keys.autoMode2(category)] != null

    /** Every theme that has a persisted customization of its own, keyed by theme id. */
    private fun readThemeCustomizations(prefs: Preferences): Map<String, SceneCustomization> {
        val out = HashMap<String, SceneCustomization>()
        for ((key, value) in prefs.asMap()) {
            val id = key.name.removePrefix(THEME_CUSTOMIZATION_KEY_PREFIX)
            if (id === key.name || value !is String) continue
            out[id] = sceneCustomizationFromJson(runCatching { JSONObject(value) }.getOrNull(), themeKnownAs(id))
        }
        return out
    }


    /**
     * Writes [c] into the flat scratch keys -- the inverse of [readFlatCustomization], key for key,
     * the automatic day/night modes included: a field left out comes back at its default when the
     * theme is restored from its archive, and the next edit writes that loss to disk.
     * `ThemeCustomizationPersistenceTest.everyCustomizationFieldSurvivesTheArchiveRoundTrip` sets every
     * field to a non-default and round-trips it, so a field added to [SceneCustomization] and
     * forgotten here fails there -- provided the test sets it too.
     */
    private fun MutablePreferences.writeFlatCustomization(c: SceneCustomization) {
        fun variant(category: ObjectCategory, config: ObjectVariantConfig) {
            this[Keys.visible(category)] = config.visible
            this[Keys.density(category)] = config.density
            this[Keys.colorDay1(category)] = config.colorDay1
            this[Keys.colorNight1(category)] = config.colorNight1
            this[Keys.colorDay2(category)] = config.colorDay2
            this[Keys.colorNight2(category)] = config.colorNight2
            this[Keys.autoMode1(category)] = config.autoMode1.storageId
            this[Keys.autoMode2(category)] = config.autoMode2.storageId
        }
        variant(ObjectCategory.HOUSES, c.houses)
        variant(ObjectCategory.BUILDINGS, c.buildings)
        variant(ObjectCategory.SHOPS, c.shops)
        variant(ObjectCategory.PUBLICS, c.publics)
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
        variant(ObjectCategory.SCARECROWS, c.scarecrows)
        this[Keys.PEOPLE_NIGHT_DENSITY] = c.peopleNightDensity
        this[Keys.CARS_NIGHT_DENSITY] = c.carsNightDensity
        this[Keys.SHOP_HOURS_ENABLED] = c.shopHoursEnabled
        this[Keys.SHOP_OPEN_HOUR] = c.shopOpenHour
        this[Keys.SHOP_CLOSE_HOUR] = c.shopCloseHour
        this[Keys.TOWER_HOURS_ENABLED] = c.towerHoursEnabled
        this[Keys.TOWER_OPEN_HOUR] = c.towerOpenHour
        this[Keys.TOWER_CLOSE_HOUR] = c.towerCloseHour
        this[Keys.PUBLIC_HOURS_ENABLED] = c.publicHoursEnabled
        this[Keys.PUBLIC_OPEN_HOUR] = c.publicOpenHour
        this[Keys.PUBLIC_CLOSE_HOUR] = c.publicCloseHour
        // All three groups are written, so the single older setting has nothing left to say.
        remove(Keys.BUSINESS_HOURS_ENABLED)
        remove(Keys.BUSINESS_OPEN_HOUR)
        remove(Keys.BUSINESS_CLOSE_HOUR)
        this[Keys.HILLS_VARIATION] = c.hillsVariation
        this[Keys.SNOW_PILES] = c.snowPiles
        this[Keys.LEAF_PILES] = c.leafPiles
        this[Keys.HILLS_COLOR_DAY] = c.hillsColorDay
        this[Keys.HILLS_COLOR_NIGHT] = c.hillsColorNight
        this[Keys.HILLS_AUTO_MODE] = c.hillsAutoMode.storageId
        for ((front, m) in listOf(true to c.mountainsFront, false to c.mountainsBack)) {
            this[Keys.mountainVisible(front)] = m.visible
            this[Keys.mountainDensity(front)] = m.density
            this[Keys.mountainColorDay(front)] = m.colorDay
            this[Keys.mountainColorNight(front)] = m.colorNight
            this[Keys.mountainAutoMode(front)] = m.autoMode.storageId
        }
        this[Keys.DISTANT_HOUSES_VISIBLE] = c.distantHouses.visible
        this[Keys.DISTANT_HOUSES_DENSITY] = c.distantHouses.density
        this[Keys.LAKE_VISIBLE] = c.lake.visible
        this[Keys.LAKE_COLOR_DAY] = c.lake.colorDay
        this[Keys.LAKE_COLOR_NIGHT] = c.lake.colorNight
        this[Keys.LAKE_HEIGHT] = c.lake.height
        this[Keys.LAKE_SAILBOATS_VISIBLE] = c.lake.sailboatsVisible
        this[Keys.LAKE_SAILBOATS_DENSITY] = c.lake.sailboatsDensity
        this[Keys.LAKE_DOLPHINS_VISIBLE] = c.lake.dolphinsVisible
        this[Keys.LAKE_DOLPHINS_DENSITY] = c.lake.dolphinsDensity
        this[Keys.LAKE_AUTO_MODE] = c.lake.autoMode.storageId
        this[Keys.STARS_VISIBLE] = c.stars.visible
        this[Keys.STARS_DENSITY] = c.stars.density
        this[Keys.SKY_COLOR_DAY_HIGH] = c.sky.colorDayHigh
        this[Keys.SKY_COLOR_DAY_LOW] = c.sky.colorDayLow
        this[Keys.SKY_COLOR_NIGHT_HIGH] = c.sky.colorNightHigh
        this[Keys.SKY_COLOR_NIGHT_LOW] = c.sky.colorNightLow
        this[Keys.SKY_COLOR_SUNRISE_LOW] = c.sky.colorSunriseLow
        this[Keys.SKY_COLOR_SUNSET_LOW] = c.sky.colorSunsetLow
        this[Keys.SKY_SUN_CLOUD_HEIGHT] = c.sky.sunCloudHeight
        this[Keys.SKY_AUTO_MODE_HIGH] = c.sky.autoModeHigh.storageId
        this[Keys.SKY_AUTO_MODE_LOW] = c.sky.autoModeLow.storageId
        this[Keys.SUN_VISIBLE] = c.sun.visible
        this[Keys.SUN_COLOR] = c.sun.color
        this[Keys.MOON_VISIBLE] = c.moon.visible
        this[Keys.MOON_COLOR] = c.moon.color
        this[Keys.MOON_REALISTIC_PHASES] = c.moon.realisticPhases
        this[Keys.CLOUDS_VISIBLE] = c.clouds.visible
        this[Keys.CLOUDS_DENSITY] = c.clouds.density
        this[Keys.CLOUDS_COLOR_DAY] = c.clouds.colorDay
        this[Keys.CLOUDS_COLOR_NIGHT] = c.clouds.colorNight
        this[Keys.CLOUDS_AUTO_MODE] = c.clouds.autoMode.storageId
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
        this[Keys.PRECIPITATION_RAIN_AUTO_MODE] = c.precipitation.rainAutoMode.storageId
        this[Keys.PRECIPITATION_SNOW_AUTO_MODE] = c.precipitation.snowAutoMode.storageId
        this[Keys.RAINBOW_VISIBLE] = c.rainbow.visible
        this[Keys.RAINBOW_OPACITY] = c.rainbow.opacity
        this[Keys.FALL_COLORS_ENABLED] = c.fallColorsEnabled
        this[Keys.WINTER_COLORS_ENABLED] = c.winterColorsEnabled
        this[Keys.CHRISTMAS_DECORATIONS_ENABLED] = c.christmasBuildingLightsEnabled
        this[Keys.CHRISTMAS_TREE_LIGHTS_ENABLED] = c.christmasTreeLightsEnabled
        this[Keys.CHRISTMAS_FIRS_ENABLED] = c.christmasFirsEnabled
        this[Keys.FLOWERS_ENABLED] = c.flowersEnabled
        this[Keys.PALMS_ENABLED] = c.palmsEnabled
        this[Keys.PALMS_INSTEAD_OF_TREES] = c.palmsInsteadOfTrees
        this[Keys.HALLOWEEN_ENABLED] = c.halloweenEnabled
        this[Keys.HORROR_SKY_ENABLED] = c.horrorSkyEnabled
        this[Keys.LAUGHING_MOON_ENABLED] = c.laughingMoonEnabled
        this[Keys.NIGHT_SKY_BY_DAY_ENABLED] = c.nightSkyByDayEnabled
        this[Keys.STILL_MOON_ENABLED] = c.stillMoonEnabled
        this[Keys.HALLOWEEN_DECORATIONS_ENABLED] = c.halloweenDecorationsEnabled
        this[Keys.BLOOD_RAIN_ENABLED] = c.bloodRainEnabled
        this[Keys.HEADLESS_HORSEMAN_ENABLED] = c.headlessHorsemanEnabled
        this[Keys.SANTA_ENABLED] = c.santaEnabled
    }


    /**
     * [replaceAll], plus the saved-themes document the caller is about to apply to the other store.
     *
     * One atomic edit carrying both halves of an import's first step ([Keys.PENDING_IMPORT_THEMES] says
     * why the second store's payload travels inside the first store's write).
     */
    suspend fun replaceAllStagingThemes(
        settings: AppBackup.BackupSettings,
        themeCustomizations: Map<String, SceneCustomization>,
        pendingThemesJson: String,
    ) = replaceAll(settings, themeCustomizations, pendingThemesJson)

    /** The document an interrupted import left behind, or `null` if there is none. */
    suspend fun pendingImportThemes(): String? =
        store.data.first()[Keys.PENDING_IMPORT_THEMES]

    /** Marks the import complete. Safe to call when there is nothing pending. */
    suspend fun clearPendingImportThemes() {
        store.editDurably { it.remove(Keys.PENDING_IMPORT_THEMES) }
    }

    /**
     * Replaces every global preference and every per-theme customization in one transaction.
     *
     * Used by backup import and its rollback. **One `edit` block**, so a reader sees the whole previous
     * state or the whole new one, never a mixture; keeping it in step with `CustomThemeStore` is
     * [BackupRepository]'s job. The scratch keys and their marker are cleared, not restored: the
     * restored per-theme blobs are the truth. Runtime state a backup does not carry (the GPS fix, its
     * timestamp, the live weather status) is left as it is on this device.
     */
    suspend fun replaceAll(
        settings: AppBackup.BackupSettings,
        themeCustomizations: Map<String, SceneCustomization>,
        pendingThemesJson: String? = null,
    ) = store.editDurably { prefs ->
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
        prefs[Keys.UPDATE_NOTIFICATIONS] = settings.updateNotificationsEnabled
        prefs[Keys.FIXED_HOUR] = settings.fixedHour
        prefs[Keys.PARALLAX_STRENGTH] = settings.parallaxStrength
        prefs[Keys.SCROLL_BACKGROUND] = settings.scrollBackground
        prefs[Keys.SWIPE_SCROLL] = settings.swipeScroll
        prefs[Keys.SCROLL_SPEED] = settings.scrollSpeed
        prefs[Keys.AUTO_THEME_BY_DATE] = settings.autoThemeByDate
        // The factory calendar is the absence of the key: a document agreeing with it would pin this
        // install to today's factory dates. A backup without the field, or of an untouched install, is "".
        if (settings.seasonalCalendarJson.isEmpty()) prefs.remove(Keys.SEASONAL_CALENDAR)
        else prefs[Keys.SEASONAL_CALENDAR] = settings.seasonalCalendarJson

        prefs.clearAllThemeCustomizationKeys()
        prefs.remove(Keys.PENDING_CUSTOMIZATION_THEME_ID)
        for (key in prefs.asMap().keys.filter { it.name.startsWith(THEME_CUSTOMIZATION_KEY_PREFIX) }) {
            prefs.remove(key)
        }
        for ((themeId, customization) in themeCustomizations) {
            prefs[Keys.themeCustomization(themeId)] = customization.toJson().toString()
        }
    }

    suspend fun setTheme(themeId: String) = store.editDurably { it[Keys.THEME_ID] = themeId }

    /**
     * [themeId] chosen **and shown**: *Automatic theme by date* off in the same edit, what a yes to "The
     * calendar is showing Autumn today. Show it anyway?" writes. One edit, or between two the wallpaper
     * would draw the previous pick for a frame or more.
     */
    suspend fun setThemeTurningAutoThemeOff(themeId: String) = store.editDurably {
        it[Keys.THEME_ID] = themeId
        it[Keys.AUTO_THEME_BY_DATE] = false
    }

    suspend fun setSyncWithRealTime(enabled: Boolean) =
        store.editDurably { it[Keys.SYNC_REAL_TIME] = enabled }

    /** Mutually exclusive with [setUseCustomLocation] -- enabling device-location mode always
     * turns custom location off in the same edit. */
    suspend fun setUseLocation(enabled: Boolean) =
        store.editDurably {
            it[Keys.USE_LOCATION] = enabled
            if (enabled) it[Keys.USE_CUSTOM_LOCATION] = false
        }

    /**
     * Turns on device location and says which positioning system it may use, in one edit: written
     * apart, the engine could see location on with the previous kind and start a fix from the wrong
     * system. Custom location is cleared, as [setUseLocation] does.
     */
    suspend fun setDeviceLocation(kind: DeviceLocationKind) =
        store.editDurably {
            it[Keys.DEVICE_LOCATION_KIND] = kind.storageId
            it[Keys.USE_LOCATION] = true
            it[Keys.USE_CUSTOM_LOCATION] = false
        }

    /** Mutually exclusive with [setUseLocation] -- see that function's own doc comment. */
    suspend fun setUseCustomLocation(enabled: Boolean) =
        store.editDurably {
            it[Keys.USE_CUSTOM_LOCATION] = enabled
            if (enabled) it[Keys.USE_LOCATION] = false
        }

    suspend fun setCustomLocation(latitude: Float, longitude: Float, label: String) =
        store.editDurably {
            it[Keys.CUSTOM_LOCATION_LAT] = latitude
            it[Keys.CUSTOM_LOCATION_LON] = longitude
            it[Keys.CUSTOM_LOCATION_LABEL] = keptText(label)
        }

    suspend fun setLiveWeatherEnabled(enabled: Boolean) =
        store.editDurably { it[Keys.LIVE_WEATHER_ENABLED] = enabled }

    /**
     * Live Weather on **and** the scene back on real time, in one edit: what a tap on the Live Weather
     * switch writes while a fixed hour holds it off. One edit, so the engine never sees half the pair.
     */
    suspend fun setLiveWeatherOnFollowingRealTime() = store.editDurably {
        it[Keys.LIVE_WEATHER_ENABLED] = true
        it[Keys.SYNC_REAL_TIME] = true
    }

    suspend fun setLiveWeatherApiKey(apiKey: String) =
        store.editDurably { it[Keys.LIVE_WEATHER_API_KEY] = keptText(apiKey) }

    /** Writes only the provider. Nothing else about Live Weather or the location is touched. */
    suspend fun setWeatherProvider(provider: WeatherProviderId) =
        store.editDurably { it[Keys.WEATHER_PROVIDER] = provider.storageId }

    suspend fun setWeatherApiComApiKey(apiKey: String) =
        store.editDurably { it[Keys.WEATHER_API_COM_API_KEY] = keptText(apiKey) }

    suspend fun setOpenWeatherApiKey(apiKey: String) =
        store.editDurably { it[Keys.OPEN_WEATHER_API_KEY] = keptText(apiKey) }

    suspend fun setAutomaticUpdateCheckEnabled(enabled: Boolean) =
        store.editDurably { it[Keys.AUTOMATIC_UPDATE_CHECK] = enabled }

    /** The two update switches as stored: what the update job reads, and nothing else. */
    data class UpdateSwitches(val automaticCheck: Boolean, val notifications: Boolean)

    /**
     * The two update switches, read once from the store: what `UpdateCheckJobs` schedules the job from
     * and what each run reads first. Not [settingsFlow], which builds every setting and emits again
     * whenever the saved themes change; never a screen's state, whose first value is a fresh install's.
     *
     * **Null when the read fails for a passing reason** (an `IOException`), not a fresh install's
     * switches: [PrefsRecovery.recoveringFromReadErrors] serves those to a flow that will read again,
     * and here they would be acted on (both off, the job cancelled until the next start). A corrupt
     * file is the store's handler's: rewritten empty once, it reads as both off.
     */
    suspend fun readUpdateSwitches(): UpdateSwitches? {
        val prefs = try {
            store.data.first()
        } catch (_: IOException) {
            return null
        }
        return UpdateSwitches(
            automaticCheck = prefs[Keys.AUTOMATIC_UPDATE_CHECK] ?: false,
            notifications = prefs[Keys.UPDATE_NOTIFICATIONS] ?: false,
        )
    }

    suspend fun setUpdateNotificationsEnabled(enabled: Boolean) =
        store.editDurably { it[Keys.UPDATE_NOTIFICATIONS] = enabled }

    /**
     * Records what Live Weather is doing. Called by the wallpaper service, never the UI, and only on a
     * change: the write re-emits the settings flow, which is how the settings screen learns of it.
     */
    suspend fun setLiveWeatherStatus(status: LiveWeatherStatus) =
        store.editDurably { it[Keys.LIVE_WEATHER_STATUS] = status.storageId }

    /**
     * Saves the position the device reported, with the moment it was saved: the fallback of the whole
     * device-location path, surviving a reboot and a spell with no signal, so the scene keeps the
     * right town when the provider is slow.
     */
    suspend fun setResolvedGpsLocation(latitude: Float, longitude: Float) =
        store.editDurably {
            it[Keys.RESOLVED_GPS_LAT] = latitude
            it[Keys.RESOLVED_GPS_LON] = longitude
            it[Keys.DEVICE_FIX_AT] = System.currentTimeMillis()
        }

    suspend fun setFixedHour(hour: Float) = store.editDurably { it[Keys.FIXED_HOUR] = hour }

    suspend fun setParallaxStrength(strength: Float) =
        store.editDurably { it[Keys.PARALLAX_STRENGTH] = strength }

    suspend fun setScrollBackground(enabled: Boolean) =
        store.editDurably { it[Keys.SCROLL_BACKGROUND] = enabled }

    suspend fun setSwipeScroll(enabled: Boolean) =
        store.editDurably { it[Keys.SWIPE_SCROLL] = enabled }

    /**
     * The home screen has moved the wallpaper with a swipe ([WallpaperSettings.swipeReported]).
     * Written once; never written back to false.
     */
    suspend fun setSwipeReported() =
        store.editDurably { it[Keys.SWIPE_REPORTED] = true }

    suspend fun setScrollSpeed(speed: Float) =
        store.editDurably { it[Keys.SCROLL_SPEED] = speed }

    suspend fun setAutoThemeByDate(enabled: Boolean) =
        store.editDurably { it[Keys.AUTO_THEME_BY_DATE] = enabled }

    /**
     * Stores [calendar], or removes the key when it is back to the factory shape: "Reset to factory
     * dates" leaves the install like one that never touched the calendar, not frozen on today's dates.
     */
    suspend fun setSeasonalCalendar(calendar: SeasonalCalendar) =
        store.editDurably {
            if (calendar.isFactory) it.remove(Keys.SEASONAL_CALENDAR)
            else it[Keys.SEASONAL_CALENDAR] = calendar.toJsonString()
        }

    /** Puts every window back on its code-declared dates. */
    suspend fun resetSeasonalCalendar() = setSeasonalCalendar(SeasonalCalendar.DEFAULT)

    // Every mutator below except resetAllCategories (which removes the tag) stamps
    // PENDING_CUSTOMIZATION_THEME_ID = forThemeId in the same edit: the live edit applies only to the
    // theme it is tagged with (CustomThemeRegistry.resolveActiveCustomization).

    suspend fun setCategoryVisible(category: ObjectCategory, visible: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.visible(category)] = visible
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setCategoryDensity(category: ObjectCategory, density: Float, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.density(category)] = density
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /** The night-time pedestrian density. The daytime one is `setCategoryDensity(PEOPLE, ...)`. */
    suspend fun setPeopleNightDensity(density: Float, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.PEOPLE_NIGHT_DENSITY] = density
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /** The night-time car density. The daytime one is `setCategoryDensity(CARS, ...)`. */
    suspend fun setCarsNightDensity(density: Float, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.CARS_NIGHT_DENSITY] = density
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /** The shops' opening hours on or off (the restaurant, the bar and the shop). */
    suspend fun setShopHoursEnabled(enabled: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.SHOP_HOURS_ENABLED] = enabled
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setShopOpenHour(hour: Float, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.SHOP_OPEN_HOUR] = hour
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setShopCloseHour(hour: Float, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.SHOP_CLOSE_HOUR] = hour
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /** The towers' opening hours on or off. */
    suspend fun setTowerHoursEnabled(enabled: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.TOWER_HOURS_ENABLED] = enabled
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setTowerOpenHour(hour: Float, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.TOWER_OPEN_HOUR] = hour
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setTowerCloseHour(hour: Float, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.TOWER_CLOSE_HOUR] = hour
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /** The public buildings' opening hours on or off (the school's: the two stations never close). */
    suspend fun setPublicHoursEnabled(enabled: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.PUBLIC_HOURS_ENABLED] = enabled
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setPublicOpenHour(hour: Float, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.PUBLIC_OPEN_HOUR] = hour
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setPublicCloseHour(hour: Float, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.PUBLIC_CLOSE_HOUR] = hour
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setCategoryColorDay1(category: ObjectCategory, color: Int, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.colorDay1(category)] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setCategoryColorNight1(category: ObjectCategory, color: Int, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.colorNight1(category)] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setCategoryColorDay2(category: ObjectCategory, color: Int, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.colorDay2(category)] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setCategoryColorNight2(category: ObjectCategory, color: Int, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.colorNight2(category)] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setHillsVariation(variation: Float, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.HILLS_VARIATION] = variation
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setHillsColorDay(color: Int, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.HILLS_COLOR_DAY] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setHillsColorNight(color: Int, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.HILLS_COLOR_NIGHT] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /** *Show distant houses* on [forThemeId]. */
    suspend fun setDistantHousesVisible(visible: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.DISTANT_HOUSES_VISIBLE] = visible
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /** The distant houses' amount on [forThemeId], 0..1. */
    suspend fun setDistantHousesDensity(density: Float, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.DISTANT_HOUSES_DENSITY] = density.coerceIn(0f, 1f)
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setMountainVisible(front: Boolean, visible: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.mountainVisible(front)] = visible
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setMountainDensity(front: Boolean, density: Float, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.mountainDensity(front)] = density
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setMountainColorDay(front: Boolean, color: Int, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.mountainColorDay(front)] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setMountainColorNight(front: Boolean, color: Int, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.mountainColorNight(front)] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setLakeVisible(visible: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.LAKE_VISIBLE] = visible
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setLakeColorDay(color: Int, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.LAKE_COLOR_DAY] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setLakeColorNight(color: Int, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.LAKE_COLOR_NIGHT] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setLakeHeight(height: Float, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.LAKE_HEIGHT] = height
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setLakeSailboatsVisible(visible: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.LAKE_SAILBOATS_VISIBLE] = visible
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setLakeSailboatsDensity(density: Float, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.LAKE_SAILBOATS_DENSITY] = density
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setLakeDolphinsVisible(visible: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.LAKE_DOLPHINS_VISIBLE] = visible
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setLakeDolphinsDensity(density: Float, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.LAKE_DOLPHINS_DENSITY] = density
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setStarsVisible(visible: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.STARS_VISIBLE] = visible; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setStarsDensity(density: Float, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.STARS_DENSITY] = density; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setSkyColorDayHigh(color: Int, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.SKY_COLOR_DAY_HIGH] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setSkyColorDayLow(color: Int, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.SKY_COLOR_DAY_LOW] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setSkyColorNightHigh(color: Int, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.SKY_COLOR_NIGHT_HIGH] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setSkyColorNightLow(color: Int, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.SKY_COLOR_NIGHT_LOW] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setSkyColorSunriseLow(color: Int, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.SKY_COLOR_SUNRISE_LOW] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setSkyColorSunsetLow(color: Int, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.SKY_COLOR_SUNSET_LOW] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setSkySunCloudHeight(height: Float, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.SKY_SUN_CLOUD_HEIGHT] = height; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setSunVisible(visible: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.SUN_VISIBLE] = visible; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setSunColor(color: Int, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.SUN_COLOR] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setMoonVisible(visible: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.MOON_VISIBLE] = visible; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setMoonColor(color: Int, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.MOON_COLOR] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setMoonRealisticPhases(realistic: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.MOON_REALISTIC_PHASES] = realistic; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setCloudsVisible(visible: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.CLOUDS_VISIBLE] = visible; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setCloudsDensity(density: Float, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.CLOUDS_DENSITY] = density; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setCloudsColorDay(color: Int, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.CLOUDS_COLOR_DAY] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setCloudsColorNight(color: Int, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.CLOUDS_COLOR_NIGHT] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    // ---- Automatic day/night colours -------------------------------------------------------
    //
    // One setter per colour pair, writing only the mode. **The colours are never touched here**, so a
    // pair switched to automatic and back returns the two values the user last chose. The derivation
    // happens on read, in `CustomThemeRegistry.resolveActiveCustomization`.

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
    ) = store.editDurably {
        it.ensureFreshPendingTheme(forThemeId)
        it[key] = mode.storageId
        it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
    }

    /** Settled snow on the ground, 0..1. Winter only -- see [SceneCustomization.snowPiles]. */
    suspend fun setSnowPiles(value: Float, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.SNOW_PILES] = value.coerceIn(0f, 1f)
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /** Heaps of fallen leaves, 0..1. Autumn only -- see [SceneCustomization.leafPiles]. */
    suspend fun setLeafPiles(value: Float, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.LEAF_PILES] = value.coerceIn(0f, 1f)
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setBirdsVisible(visible: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.BIRDS_VISIBLE] = visible
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setBirdsDensity(density: Float, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.BIRDS_DENSITY] = density
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setBirdsNight(nightBirds: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.BIRDS_NIGHT] = nightBirds
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setBirdColor(index: Int, color: Int, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.birdColor(index)] = color
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setBirdWeight(index: Int, weight: Float, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.birdWeight(index)] = weight
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setPrecipitationVisible(visible: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.PRECIPITATION_VISIBLE] = visible; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setPrecipitationType(type: PrecipitationType, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.PRECIPITATION_TYPE] = type.name; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setPrecipitationIntensity(intensity: Float, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.PRECIPITATION_INTENSITY] = intensity; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setPrecipitationRainColorDay(color: Int, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.PRECIPITATION_RAIN_COLOR_DAY] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setPrecipitationRainColorNight(color: Int, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.PRECIPITATION_RAIN_COLOR_NIGHT] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setPrecipitationSnowColorDay(color: Int, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.PRECIPITATION_SNOW_COLOR_DAY] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setPrecipitationSnowColorNight(color: Int, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.PRECIPITATION_SNOW_COLOR_NIGHT] = color; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setPrecipitationThunderstorm(thunderstorm: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.PRECIPITATION_THUNDERSTORM] = thunderstorm; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setRainbowVisible(visible: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.RAINBOW_VISIBLE] = visible; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    suspend fun setRainbowOpacity(opacity: Float, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId); it[Keys.RAINBOW_OPACITY] = opacity; it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId }

    /** Exclusive with [setWinterColorsEnabled]: Fall Colors on turns Winter Colors off in the same edit
     * (the Christmas decorations untouched; [SceneCustomization.fallColorsEnabled]). */
    suspend fun setFallColorsEnabled(enabled: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.FALL_COLORS_ENABLED] = enabled
            if (enabled) it[Keys.WINTER_COLORS_ENABLED] = false
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /**
     * *Lights on the buildings*, *Lights on the trees* and *Christmas firs*, the three Christmas switches.
     *
     * **Independent of the seasonal palettes**, which are two readings of the same leaves and exclude
     * each other: the lights hang on whatever the trees look like. **But none turns on while the theme's
     * Halloween decorations count** -- on, with a house or a shop standing to carry them
     * ([DecorationExclusion.halloweenOn]; the first on wins, and the settings grey the switch and say
     * why): a refused tap changes no switch. With nothing to carry them they do not count, the tap goes
     * through and the store holds both on; when the buildings come back, [DecorationExclusion.resolved]
     * reads one side.
     */
    suspend fun setChristmasBuildingLightsEnabled(enabled: Boolean, forThemeId: String) =
        setChristmasDecoration(Keys.CHRISTMAS_DECORATIONS_ENABLED, enabled, forThemeId)

    suspend fun setChristmasTreeLightsEnabled(enabled: Boolean, forThemeId: String) =
        setChristmasDecoration(Keys.CHRISTMAS_TREE_LIGHTS_ENABLED, enabled, forThemeId)

    suspend fun setChristmasFirsEnabled(enabled: Boolean, forThemeId: String) =
        setChristmasDecoration(Keys.CHRISTMAS_FIRS_ENABLED, enabled, forThemeId)

    /**
     * One of the three, **with the other two written as they read now**: a store without the two newer keys reads them
     * from the first's ([readFlatCustomization]), so writing that key alone would move all three. The Halloween
     * decorations are written as they read too: without a key of their own they follow the Christmas ones, and would turn
     * on by themselves when the last of these goes off.
     *
     * The scratch space is the theme's from [ensureFreshPendingTheme] on, so it is stamped before a refusal returns, or a
     * refused tap would leave this theme's look in the scratch marked as the theme edited before.
     */
    private suspend fun setChristmasDecoration(key: Preferences.Key<Boolean>, enabled: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
            val now = readFlatCustomization(it, forThemeId)
            if (enabled && !DecorationExclusion.christmasAllowed(now, DecorationExclusion.streetOf(forThemeId))) return@editDurably
            it[Keys.CHRISTMAS_DECORATIONS_ENABLED] = now.christmasBuildingLightsEnabled
            it[Keys.CHRISTMAS_TREE_LIGHTS_ENABLED] = now.christmasTreeLightsEnabled
            it[Keys.CHRISTMAS_FIRS_ENABLED] = now.christmasFirsEnabled
            // Stored on and set aside by the reading because nothing on the street carries them: kept, as their line says.
            it[Keys.HALLOWEEN_DECORATIONS_ENABLED] = now.halloweenDecorationsEnabled || (it[Keys.HALLOWEEN_DECORATIONS_ENABLED] == true &&
                !DecorationExclusion.halloweenDecorationsHang(now, DecorationExclusion.streetOf(forThemeId)))
            it[key] = enabled
        }

    /**
     * *Halloween decorations*: the cobwebs and the jack-o'-lanterns of the houses and the shops. Not turned
     * on while any of the theme's Christmas decorations counts -- on, with something standing to carry it
     * ([DecorationExclusion.christmasOn]): a refused tap changes no switch, and the scratch space is stamped as the
     * theme's before it returns, as in [setChristmasDecoration]. A Christmas switch on with nothing to carry it does
     * not refuse the tap, and the store then holds both on.
     */
    suspend fun setHalloweenDecorationsEnabled(enabled: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
            if (enabled && !DecorationExclusion.halloweenAllowed(readFlatCustomization(it, forThemeId), DecorationExclusion.streetOf(forThemeId))) return@editDurably
            it[Keys.HALLOWEEN_DECORATIONS_ENABLED] = enabled
        }

    /** *Laughing moon*. Stored as chosen: what it needs to show is the settings' to say (`SettingsUiModel`). */
    suspend fun setLaughingMoonEnabled(enabled: Boolean, forThemeId: String) =
        setFlag(Keys.LAUGHING_MOON_ENABLED, enabled, forThemeId)

    /** *Night sky by day*. Stored as chosen, as [setLaughingMoonEnabled]. */
    suspend fun setNightSkyByDayEnabled(enabled: Boolean, forThemeId: String) =
        setFlag(Keys.NIGHT_SKY_BY_DAY_ENABLED, enabled, forThemeId)

    /** *Moon stays in place*. Stored as chosen, as [setLaughingMoonEnabled]. */
    suspend fun setStillMoonEnabled(enabled: Boolean, forThemeId: String) =
        setFlag(Keys.STILL_MOON_ENABLED, enabled, forThemeId)

    /** *Blood rain*. */
    suspend fun setBloodRainEnabled(enabled: Boolean, forThemeId: String) =
        setFlag(Keys.BLOOD_RAIN_ENABLED, enabled, forThemeId)

    /** *Headless Horseman*, a plain per-theme switch as [setSantaEnabled]. */
    suspend fun setHeadlessHorsemanEnabled(enabled: Boolean, forThemeId: String) =
        setFlag(Keys.HEADLESS_HORSEMAN_ENABLED, enabled, forThemeId)

    private suspend fun setFlag(key: Preferences.Key<Boolean>, enabled: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[key] = enabled
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /** Ground flowers on or off. Independent of every other flag. */
    suspend fun setFlowersEnabled(enabled: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.FLOWERS_ENABLED] = enabled
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /**
     * The Palms switch, on any theme: on a layout that plants palms, whether they stay palms; on one
     * that plants none, whether its trees become palms. Independent of every other flag.
     *
     * **Writes both fields behind the switch** ([SceneCustomization.palmsEnabled] and
     * [SceneCustomization.palmsInsteadOfTrees]), so it needs no layout to know which the theme reads.
     * The first can hold a `true` the user never chose, which is why a theme without palms reads the
     * second. TREES is untouched: only the species filling the tree slots changes.
     */
    suspend fun setPalmsEnabled(enabled: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.PALMS_ENABLED] = enabled
            it[Keys.PALMS_INSTEAD_OF_TREES] = enabled
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /**
     * Halloween on or off. **Clears nothing and is cleared by nothing**: Winter and Christmas are
     * untouched both ways.
     */
    suspend fun setHalloweenEnabled(enabled: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.HALLOWEEN_ENABLED] = enabled
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /** The horror sky, independent of [setHalloweenEnabled] in both directions. */
    suspend fun setHorrorSkyEnabled(enabled: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.HORROR_SKY_ENABLED] = enabled
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /** Mutually exclusive with [setFallColorsEnabled] -- see that function's own doc comment. */
    suspend fun setWinterColorsEnabled(enabled: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.WINTER_COLORS_ENABLED] = enabled
            if (enabled) it[Keys.FALL_COLORS_ENABLED] = false
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    suspend fun setSantaEnabled(enabled: Boolean, forThemeId: String) =
        store.editDurably { it.ensureFreshPendingTheme(forThemeId)
            it[Keys.SANTA_ENABLED] = enabled
            it[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
        }

    /**
     * **"Reset decorations to defaults"**: everything Seasons & decorations shows, in one write, back to
     * the theme's own on [forThemeId] only (`DESIGN_NOTES.md` decision 42) -- the seasonal palette and the
     * snow and leaf piles, the seven decorations with their densities and colours
     * ([ObjectCategory.DECORATIONS]), and the switches: the three Christmas ones, Santa, Halloween and its
     * six, the horror sky, the flowers. Not the palms, whose switch is on the Trees page ([resetCategory]).
     *
     * **Removed, not set false**: a reset means "forget what I chose", and the theme's own default may
     * be on (Winter, Christmas, New Year and Autumn turn their palettes on, Christmas its Santa).
     */
    suspend fun resetDecorations(forThemeId: String) = store.editDurably { prefs ->
        prefs.ensureFreshPendingTheme(forThemeId)
        for (category in ObjectCategory.DECORATIONS) prefs.removeCategoryKeys(category)
        prefs.remove(Keys.FALL_COLORS_ENABLED)
        prefs.remove(Keys.WINTER_COLORS_ENABLED)
        prefs.remove(Keys.SNOW_PILES)
        prefs.remove(Keys.LEAF_PILES)
        prefs.remove(Keys.CHRISTMAS_DECORATIONS_ENABLED)
        prefs.remove(Keys.CHRISTMAS_TREE_LIGHTS_ENABLED)
        prefs.remove(Keys.CHRISTMAS_FIRS_ENABLED)
        prefs.remove(Keys.SANTA_ENABLED)
        prefs.remove(Keys.FLOWERS_ENABLED)
        prefs.remove(Keys.HALLOWEEN_ENABLED)
        prefs.remove(Keys.HORROR_SKY_ENABLED)
        prefs.remove(Keys.LAUGHING_MOON_ENABLED)
        prefs.remove(Keys.NIGHT_SKY_BY_DAY_ENABLED)
        prefs.remove(Keys.STILL_MOON_ENABLED)
        prefs.remove(Keys.HALLOWEEN_DECORATIONS_ENABLED)
        prefs.remove(Keys.BLOOD_RAIN_ENABLED)
        prefs.remove(Keys.HEADLESS_HORSEMAN_ENABLED)
        prefs[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
    }

    /**
     * Resets one category's visibility/density/colors back to defaults, **on [forThemeId]**.
     *
     * The scratch keys belong to the theme `PENDING_CUSTOMIZATION_THEME_ID` names, the last theme
     * *edited*, not necessarily the one *viewed* ([setTheme] writes only `THEME_ID`). So
     * [ensureFreshPendingTheme] runs first, as in the setters: the scratch is this theme's before
     * anything is removed, and the reset reaches a theme whose state is in its archive. Otherwise a
     * reset right after switching themes would remove the other theme's keys and leave this one as it
     * was. The tag is stamped afterwards, as the setters do.
     */
    suspend fun resetCategory(category: ObjectCategory, forThemeId: String) = store.editDurably { prefs ->
        prefs.ensureFreshPendingTheme(forThemeId)
        prefs.removeCategoryKeys(category)
        // Keys outside the per-category ones that belong to the category are reset with it: the people's
        // and the cars' night densities, and the opening hours of the towers ("Reset Buildings"), the
        // shops and the public buildings. [ensureFreshPendingTheme] above has already carried the single
        // older setting into the groups' keys ([carryHoursOver], [carryPublicsOver]), so removing one
        // group's keys leaves the others'.
        if (category == ObjectCategory.PEOPLE) prefs.remove(Keys.PEOPLE_NIGHT_DENSITY)
        if (category == ObjectCategory.CARS) prefs.remove(Keys.CARS_NIGHT_DENSITY)
        if (category == ObjectCategory.BUILDINGS) {
            prefs.remove(Keys.TOWER_HOURS_ENABLED)
            prefs.remove(Keys.TOWER_OPEN_HOUR)
            prefs.remove(Keys.TOWER_CLOSE_HOUR)
        }
        if (category == ObjectCategory.SHOPS) {
            prefs.remove(Keys.SHOP_HOURS_ENABLED)
            prefs.remove(Keys.SHOP_OPEN_HOUR)
            prefs.remove(Keys.SHOP_CLOSE_HOUR)
        }
        if (category == ObjectCategory.PUBLICS) {
            prefs.remove(Keys.PUBLIC_HOURS_ENABLED)
            prefs.remove(Keys.PUBLIC_OPEN_HOUR)
            prefs.remove(Keys.PUBLIC_CLOSE_HOUR)
        }
        // And the trees carry the Palms switch, which is on their page (`DESIGN_NOTES.md` decision 39):
        // back to the theme's own, on for Beach and Desert, off elsewhere. Both fields of the switch.
        if (category == ObjectCategory.TREES) {
            prefs.remove(Keys.PALMS_ENABLED)
            prefs.remove(Keys.PALMS_INSTEAD_OF_TREES)
        }
        prefs[Keys.PENDING_CUSTOMIZATION_THEME_ID] = forThemeId
    }

    /** One category's own keys: the switch, the density, the two colour pairs and their two modes. */
    private fun MutablePreferences.removeCategoryKeys(category: ObjectCategory) {
        remove(Keys.visible(category))
        remove(Keys.density(category))
        remove(Keys.colorDay1(category))
        remove(Keys.colorNight1(category))
        remove(Keys.colorDay2(category))
        remove(Keys.colorNight2(category))
        // The modes go with the colours: a pair left on FROM_DAY would hide the default under the derived half.
        remove(Keys.autoMode1(category))
        remove(Keys.autoMode2(category))
    }

    /**
     * Removes every per-theme scratch key (not the global settings, such as scroll speed or Live
     * Weather). Used by [ensureFreshPendingTheme] on a theme change, [resetAllCategories] ("Reset this
     * theme's scene") and [replaceAll] (a backup restore). **Every key a `forThemeId` setter writes
     * belongs here**: one left out survives every wipe and leaks one theme's value into the next theme
     * edited, a reset and a restore.
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
        // The nine pair modes outside the per-category loop, for the reason removeCategoryKeys gives.
        remove(Keys.HILLS_AUTO_MODE)
        remove(Keys.mountainAutoMode(true))
        remove(Keys.mountainAutoMode(false))
        remove(Keys.LAKE_AUTO_MODE)
        remove(Keys.SKY_AUTO_MODE_HIGH)
        remove(Keys.SKY_AUTO_MODE_LOW)
        remove(Keys.CLOUDS_AUTO_MODE)
        remove(Keys.PRECIPITATION_RAIN_AUTO_MODE)
        remove(Keys.PRECIPITATION_SNOW_AUTO_MODE)
        // Per-theme keys outside the per-category loop.
        remove(Keys.PEOPLE_NIGHT_DENSITY)
        remove(Keys.CARS_NIGHT_DENSITY)
        remove(Keys.SHOP_HOURS_ENABLED)
        remove(Keys.SHOP_OPEN_HOUR)
        remove(Keys.SHOP_CLOSE_HOUR)
        remove(Keys.TOWER_HOURS_ENABLED)
        remove(Keys.TOWER_OPEN_HOUR)
        remove(Keys.TOWER_CLOSE_HOUR)
        remove(Keys.PUBLIC_HOURS_ENABLED)
        remove(Keys.PUBLIC_OPEN_HOUR)
        remove(Keys.PUBLIC_CLOSE_HOUR)
        remove(Keys.BUSINESS_HOURS_ENABLED)
        remove(Keys.BUSINESS_OPEN_HOUR)
        remove(Keys.BUSINESS_CLOSE_HOUR)
        remove(Keys.HILLS_VARIATION)
        remove(Keys.SNOW_PILES)
        remove(Keys.LEAF_PILES)
        remove(Keys.HILLS_COLOR_DAY)
        remove(Keys.HILLS_COLOR_NIGHT)
        for (front in listOf(true, false)) {
            remove(Keys.mountainVisible(front))
            remove(Keys.mountainDensity(front))
            remove(Keys.mountainColorDay(front))
            remove(Keys.mountainColorNight(front))
        }
        remove(Keys.DISTANT_HOUSES_VISIBLE)
        remove(Keys.DISTANT_HOUSES_DENSITY)
        // The marker belongs to the space it describes: an empty space holds no Buildings colour,
        // so the shops read the theme's own either way, and the edit that fills it sets it again.
        remove(Keys.SCRATCH_KNOWS_SHOPS)
        remove(Keys.SCRATCH_KNOWS_PUBLICS)
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
        remove(Keys.FALL_COLORS_ENABLED)
        remove(Keys.WINTER_COLORS_ENABLED)
        remove(Keys.CHRISTMAS_DECORATIONS_ENABLED)
        remove(Keys.CHRISTMAS_TREE_LIGHTS_ENABLED)
        remove(Keys.CHRISTMAS_FIRS_ENABLED)
        remove(Keys.FLOWERS_ENABLED)
        remove(Keys.PALMS_ENABLED)
        remove(Keys.PALMS_INSTEAD_OF_TREES)
        remove(Keys.HALLOWEEN_ENABLED)
        remove(Keys.HORROR_SKY_ENABLED)
        remove(Keys.LAUGHING_MOON_ENABLED)
        remove(Keys.NIGHT_SKY_BY_DAY_ENABLED)
        remove(Keys.STILL_MOON_ENABLED)
        remove(Keys.HALLOWEEN_DECORATIONS_ENABLED)
        remove(Keys.BLOOD_RAIN_ENABLED)
        remove(Keys.HEADLESS_HORSEMAN_ENABLED)
        remove(Keys.SANTA_ENABLED)
    }

    /**
     * The first statement of every per-theme setter (anything taking `forThemeId` except
     * [resetAllCategories]), inside the same edit, before the setter's own change.
     *
     * The scratch keys are one flat set, not namespaced per theme, and
     * `CustomThemeRegistry.resolveActiveCustomization` reads them for whichever theme
     * `PENDING_CUSTOMIZATION_THEME_ID` names. So when [forThemeId] is not the theme the scratch belongs
     * to, the outgoing theme's state is archived under its own key, every per-theme key is wiped
     * ([clearAllThemeCustomizationKeys]) and the incoming theme's state is written in: the setter's
     * change lands on that theme's own baseline, never on another theme's leftovers (the hills one
     * theme turned white showing on the next). On the same theme, an older store's shops, hours and
     * public buildings are carried into their own keys once.
     */
    private fun MutablePreferences.ensureFreshPendingTheme(forThemeId: String) {
        val outgoing = this[Keys.PENDING_CUSTOMIZATION_THEME_ID]
        if (outgoing == forThemeId) {
            carryShopsOver(forThemeId)
            carryHoursOver()
            carryPublicsOver(forThemeId)
            return
        }
        // **Archive, do not destroy**: the scratch is the only place an edit lives until the theme is
        // saved in the gallery, so the outgoing theme's state is written to its own key before the wipe.
        if (outgoing != null) {
            this[Keys.themeCustomization(outgoing)] =
                readFlatCustomization(this, outgoing).toJson().toString()
        }
        clearAllThemeCustomizationKeys()
        // The incoming theme's archived state is restored, so re-editing picks up where the user left off.
        // **With no archive, the look it was saved with in the gallery** (`DESIGN_NOTES.md` decision 43): edits
        // win over a saved version (`CustomThemeRegistry.resolveActiveCustomization`), so starting from the
        // factory values would make the first touch of any control hide the saved look. A reset of one page
        // still means the theme's factory values for that page, as its button says.
        val archived = this[Keys.themeCustomization(forThemeId)]
        if (archived != null) {
            writeFlatCustomization(sceneCustomizationFromJson(runCatching { JSONObject(archived) }.getOrNull(), themeKnownAs(forThemeId)))
        } else {
            savedLookFor(forThemeId)?.let { writeFlatCustomization(it) }
        }
        // Written by this build, whose writer gives the shops and the public buildings keys of their own.
        this[Keys.SCRATCH_KNOWS_SHOPS] = true
        this[Keys.SCRATCH_KNOWS_PUBLICS] = true
    }

    /**
     * A live edit without shop keys, edited again: the shops' colours it is read with ([readShops])
     * written into the shops' keys and the towers' colours it did not store written as the slate
     * ([towersDrawnIn]), once, and the space marked. From then on the keys are the truth, so a reset of
     * either page means the theme's own colours.
     */
    private fun MutablePreferences.carryShopsOver(themeId: String) {
        if (this[Keys.SCRATCH_KNOWS_SHOPS] == true) return
        if (holdsCategoryColour(this, ObjectCategory.BUILDINGS)) {
            val slate = SceneCustomization.DEFAULT.buildings
            if (this[Keys.colorDay1(ObjectCategory.BUILDINGS)] == null) this[Keys.colorDay1(ObjectCategory.BUILDINGS)] = slate.colorDay1
            if (this[Keys.colorNight1(ObjectCategory.BUILDINGS)] == null) this[Keys.colorNight1(ObjectCategory.BUILDINGS)] = slate.colorNight1
            if (this[Keys.colorDay2(ObjectCategory.BUILDINGS)] == null) this[Keys.colorDay2(ObjectCategory.BUILDINGS)] = slate.colorDay2
            if (this[Keys.colorNight2(ObjectCategory.BUILDINGS)] == null) this[Keys.colorNight2(ObjectCategory.BUILDINGS)] = slate.colorNight2
            val shops = readShops(this, defaultCustomizationFor(themeId))
            this[Keys.colorDay1(ObjectCategory.SHOPS)] = shops.colorDay1
            this[Keys.colorNight1(ObjectCategory.SHOPS)] = shops.colorNight1
            this[Keys.colorDay2(ObjectCategory.SHOPS)] = shops.colorDay2
            this[Keys.colorNight2(ObjectCategory.SHOPS)] = shops.colorNight2
            this[Keys.autoMode1(ObjectCategory.SHOPS)] = shops.autoMode1.storageId
            this[Keys.autoMode2(ObjectCategory.SHOPS)] = shops.autoMode2.storageId
        }
        this[Keys.SCRATCH_KNOWS_SHOPS] = true
    }

    /**
     * A live edit holding the single opening-hours setting, edited again: the setting written into the
     * shops' and the towers' keys and removed, once. From then on each group's keys are the truth, so
     * an edit or a reset ([resetCategory]) of one group leaves the other's. A key the setting did not
     * hold is not written: the group reads its default there.
     */
    private fun MutablePreferences.carryHoursOver() {
        val enabled = this[Keys.BUSINESS_HOURS_ENABLED]
        val open = this[Keys.BUSINESS_OPEN_HOUR]
        val close = this[Keys.BUSINESS_CLOSE_HOUR]
        if (enabled == null && open == null && close == null) return
        if (enabled != null) {
            if (this[Keys.SHOP_HOURS_ENABLED] == null) this[Keys.SHOP_HOURS_ENABLED] = enabled
            if (this[Keys.TOWER_HOURS_ENABLED] == null) this[Keys.TOWER_HOURS_ENABLED] = enabled
        }
        if (open != null) {
            if (this[Keys.SHOP_OPEN_HOUR] == null) this[Keys.SHOP_OPEN_HOUR] = open
            if (this[Keys.TOWER_OPEN_HOUR] == null) this[Keys.TOWER_OPEN_HOUR] = open
        }
        if (close != null) {
            if (this[Keys.SHOP_CLOSE_HOUR] == null) this[Keys.SHOP_CLOSE_HOUR] = close
            if (this[Keys.TOWER_CLOSE_HOUR] == null) this[Keys.TOWER_CLOSE_HOUR] = close
        }
        remove(Keys.BUSINESS_HOURS_ENABLED)
        remove(Keys.BUSINESS_OPEN_HOUR)
        remove(Keys.BUSINESS_CLOSE_HOUR)
    }

    /**
     * A live edit without public keys, edited again: the public buildings' colours and hours it is read
     * with ([readPublics], [readFlatCustomization]) written into their keys, once, and the space marked
     * -- after the shops' and the hours' carrying over, which they are read from. From then on an edit
     * or a reset of the shops leaves the public buildings'.
     */
    private fun MutablePreferences.carryPublicsOver(themeId: String) {
        if (this[Keys.SCRATCH_KNOWS_PUBLICS] == true) return
        val read = readFlatCustomization(this, themeId)
        val publics = read.publics
        this[Keys.colorDay1(ObjectCategory.PUBLICS)] = publics.colorDay1
        this[Keys.colorNight1(ObjectCategory.PUBLICS)] = publics.colorNight1
        this[Keys.colorDay2(ObjectCategory.PUBLICS)] = publics.colorDay2
        this[Keys.colorNight2(ObjectCategory.PUBLICS)] = publics.colorNight2
        this[Keys.autoMode1(ObjectCategory.PUBLICS)] = publics.autoMode1.storageId
        this[Keys.autoMode2(ObjectCategory.PUBLICS)] = publics.autoMode2.storageId
        this[Keys.PUBLIC_HOURS_ENABLED] = read.publicHoursEnabled
        this[Keys.PUBLIC_OPEN_HOUR] = read.publicOpenHour
        this[Keys.PUBLIC_CLOSE_HOUR] = read.publicCloseHour
        this[Keys.SCRATCH_KNOWS_PUBLICS] = true
    }

    /**
     * Resets every theme in [themeIds] as [resetAllCategories] resets one, **in one write**, so a crash
     * cannot leave some reset and the rest not: *Advanced & about*'s "Reset current edits to built-in
     * themes".
     */
    suspend fun resetCustomizations(themeIds: Set<String>) = store.editDurably { prefs ->
        val pending = prefs[Keys.PENDING_CUSTOMIZATION_THEME_ID]
        if (pending != null && pending in themeIds) {
            prefs.clearAllThemeCustomizationKeys()
            prefs.remove(Keys.PENDING_CUSTOMIZATION_THEME_ID)
        }
        for (id in themeIds) prefs.remove(Keys.themeCustomization(id))
    }

    /** Resets [forThemeId]'s whole customization to its defaults: removes its archive and -- only
     * if the scratch space is this theme's -- clears every per-theme scratch key and the
     * pending-edit tag. */
    suspend fun resetAllCategories(forThemeId: String) = store.editDurably { prefs ->
        // Only if the scratch is *this* theme's, or the theme under live edit would be reset
        // (`ThemeCustomizationPersistenceTest.resetIsTheOnlyThingThatRemovesACustomization`).
        if (prefs[Keys.PENDING_CUSTOMIZATION_THEME_ID] == forThemeId) {
            prefs.clearAllThemeCustomizationKeys()
            prefs.remove(Keys.PENDING_CUSTOMIZATION_THEME_ID)
        }
        // The archive goes too, or the old look would come back at the next edit: a reset is the one
        // place a customization is meant to disappear.
        prefs.remove(Keys.themeCustomization(forThemeId))
    }
}
