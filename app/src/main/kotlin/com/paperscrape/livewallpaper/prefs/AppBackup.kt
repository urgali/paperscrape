package com.paperscrape.livewallpaper.prefs

import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.migrateEmbeddedCustomThemes
import com.paperscrape.livewallpaper.engine.optFinite
import com.paperscrape.livewallpaper.engine.BACKUP_SCHEMA_BEFORE_THE_FIELD
import com.paperscrape.livewallpaper.engine.CUSTOM_THEME_SCHEMA_VERSION
import com.paperscrape.livewallpaper.engine.CustomThemeEntry
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.customThemeEntryFromJson
import com.paperscrape.livewallpaper.engine.sceneCustomizationFromJson
import com.paperscrape.livewallpaper.engine.toJson
import com.paperscrape.livewallpaper.engine.toJsonString
import com.paperscrape.livewallpaper.location.DeviceLocationKind
import org.json.JSONArray
import org.json.JSONObject

/**
 * The app's whole user-owned state, as one versioned JSON document.
 *
 * ### What a backup is for, and what it is not
 *
 * It is for **moving a user's app to a new phone, or back to yesterday**. So it carries everything
 * the user chose and nothing the app merely worked out for itself:
 *
 *  - every global preference — theme, clock, location, weather provider and keys, updates, motion;
 *  - every theme's own customization, for built-ins and for saved themes alike;
 *  - every saved theme: built-in overrides and standalone custom themes, whole.
 *
 * It deliberately does **not** carry the updater's state (a pending download is not a setting and
 * a restored one would point at a version this install may not want), the resolved GPS fix or its
 * timestamp (a cache of where the phone was, not a choice, and restoring one onto another device
 * would place it somewhere it has never been), or the live-weather status line (a runtime result).
 * Nothing here is a file path, a cache, a log or a credential belonging to the project.
 *
 * ### Sensitive by design
 *
 * A complete backup contains the user's own **weather API keys** and, if they set one, their
 * **custom location**. That is the point — a backup that dropped them would not restore a working
 * app — but it makes the file worth protecting, and the export UI says so before writing it. The
 * keys are never logged, never printed by a test, and never included in any diagnostic output.
 * [AppBackup] and [BackupSettings] are data classes, and until v5.8C their generated `toString`
 * printed all three keys, as did a `BackupRepository.ImportResult.Applied` holding one (v5.8B
 * comment audit: nothing printed one, but nothing stopped it). [BackupSettings] now writes its
 * own `toString`, which says how long each key is and never what it is; the others print through it.
 *
 * ### Versioning
 *
 * [SCHEMA_VERSION] is this document's own, **separate from the custom-theme schema** the entries
 * inside it carry — a backup and a shared theme are two formats with two lifetimes, and merging
 * their version numbers would mean one could not change without the other. Unknown keys are
 * ignored so a file written by a later build still restores what this one understands; a file
 * whose *major* schema is newer than [SCHEMA_VERSION] is refused outright rather than half-read.
 */
data class AppBackup(
    val schemaVersion: Int,
    val appVersionName: String,
    val createdAtMillis: Long,
    val settings: BackupSettings,
    val themeCustomizations: Map<String, SceneCustomization>,
    val customThemeData: CustomThemeData,
) {

    /**
     * The global preferences a backup carries.
     *
     * A named subset of [WallpaperSettings] rather than the whole thing, so that adding a runtime
     * field to that class cannot silently start being written into users' backup files.
     */
    data class BackupSettings(
        val themeId: String,
        val syncWithRealTime: Boolean,
        val useLocationForSunTimes: Boolean,
        val useCustomLocation: Boolean,
        val deviceLocationKind: String,
        val customLocationLatitude: Float,
        val customLocationLongitude: Float,
        val customLocationLabel: String,
        val liveWeatherEnabled: Boolean,
        val liveWeatherApiKey: String,
        val weatherProviderId: String,
        val weatherApiComApiKey: String,
        val openWeatherApiKey: String,
        val automaticUpdateCheckEnabled: Boolean,
        val updateNotificationsEnabled: Boolean,
        val fixedHour: Float,
        val parallaxStrength: Float,
        val scrollBackground: Boolean,
        val swipeScroll: Boolean,
        val scrollSpeed: Float,
        val autoThemeByDate: Boolean,
        /**
         * The holiday calendar, serialised exactly as DataStore holds it.
         *
         * Carried as the document rather than as its fields so that this mirror does not have to
         * grow a member per window: the calendar's own reader already treats an unknown window and
         * a malformed entry as "take the factory value", which is the behaviour a backup from
         * another version needs anyway. Empty string means the factory calendar.
         */
        val seasonalCalendarJson: String,
    ) {
        /**
         * Every field but the three keys, which are shown only as their length: a data class's
         * generated `toString` would print them, and [AppBackup]'s and
         * `BackupRepository.ImportResult.Applied`'s print through this one. Written out by hand on
         * purpose -- reflection would work in a debug build and print obfuscated names, and so fail
         * to redact, in a minified one. A field added here and forgotten below is merely missing
         * from a debug string, never leaked.
         */
        override fun toString(): String =
            "BackupSettings(themeId=$themeId, syncWithRealTime=$syncWithRealTime, " +
                "useLocationForSunTimes=$useLocationForSunTimes, useCustomLocation=$useCustomLocation, " +
                "deviceLocationKind=$deviceLocationKind, customLocationLatitude=$customLocationLatitude, " +
                "customLocationLongitude=$customLocationLongitude, customLocationLabel=$customLocationLabel, " +
                "liveWeatherEnabled=$liveWeatherEnabled, liveWeatherApiKey=${redacted(liveWeatherApiKey)}, " +
                "weatherProviderId=$weatherProviderId, weatherApiComApiKey=${redacted(weatherApiComApiKey)}, " +
                "openWeatherApiKey=${redacted(openWeatherApiKey)}, " +
                "automaticUpdateCheckEnabled=$automaticUpdateCheckEnabled, " +
                "updateNotificationsEnabled=$updateNotificationsEnabled, fixedHour=$fixedHour, " +
                "parallaxStrength=$parallaxStrength, scrollBackground=$scrollBackground, " +
                "swipeScroll=$swipeScroll, scrollSpeed=$scrollSpeed, autoThemeByDate=$autoThemeByDate, " +
                "seasonalCalendarJson=$seasonalCalendarJson)"

        private fun redacted(key: String): String = if (key.isEmpty()) "<none>" else "<${key.length} characters, hidden>"
    }

    companion object {
        /**
         * This document's schema. Bump when a field changes meaning; adding one does not need it,
         * because a reader fills anything absent from the running defaults.
         */
        const val SCHEMA_VERSION = 1

        /** What an export writes, and what an import will accept as its own kind of file. */
        const val DOCUMENT_KIND = "paperscrape-app-backup"

        fun from(
            settings: WallpaperSettings,
            customThemeData: CustomThemeData,
            appVersionName: String,
            nowMillis: Long,
        ): AppBackup = AppBackup(
            schemaVersion = SCHEMA_VERSION,
            appVersionName = appVersionName,
            createdAtMillis = nowMillis,
            settings = BackupSettings(
                themeId = settings.themeId,
                syncWithRealTime = settings.syncWithRealTime,
                useLocationForSunTimes = settings.useLocationForSunTimes,
                useCustomLocation = settings.useCustomLocation,
                deviceLocationKind = settings.deviceLocationKind.storageId,
                customLocationLatitude = settings.customLocationLatitude,
                customLocationLongitude = settings.customLocationLongitude,
                customLocationLabel = settings.customLocationLabel,
                liveWeatherEnabled = settings.liveWeatherEnabled,
                liveWeatherApiKey = settings.liveWeatherApiKey,
                weatherProviderId = settings.weatherProviderId,
                weatherApiComApiKey = settings.weatherApiComApiKey,
                openWeatherApiKey = settings.openWeatherApiKey,
                automaticUpdateCheckEnabled = settings.automaticUpdateCheckEnabled,
                updateNotificationsEnabled = settings.updateNotificationsEnabled,
                fixedHour = settings.fixedHour,
                parallaxStrength = settings.parallaxStrength,
                scrollBackground = settings.scrollBackground,
                swipeScroll = settings.swipeScroll,
                scrollSpeed = settings.scrollSpeed,
                autoThemeByDate = settings.autoThemeByDate,
                seasonalCalendarJson =
                    if (settings.seasonalCalendar.isFactory) "" else settings.seasonalCalendar.toJsonString(),
            ),
            themeCustomizations = settings.themeCustomizations,
            customThemeData = customThemeData,
        )
    }

    /** A one-line, key-free summary for the confirmation dialog an import shows before applying. */
    fun summary(): String = buildString {
        append("${themeCustomizations.size} customised theme")
        if (themeCustomizations.size != 1) append("s")
        append(", ${customThemeData.overrides.size} built-in override")
        if (customThemeData.overrides.size != 1) append("s")
        append(", ${customThemeData.customThemes.size} saved theme")
        if (customThemeData.customThemes.size != 1) append("s")
        if (hasSecrets()) append(" — includes weather API keys")
    }

    /**
     * Whether this document carries a weather API key. The custom location, the other sensitive
     * content, is not counted.
     */
    fun hasSecrets(): Boolean =
        settings.liveWeatherApiKey.isNotBlank() ||
            settings.weatherApiComApiKey.isNotBlank() ||
            settings.openWeatherApiKey.isNotBlank()
}

/** Why an import was refused. Each one is something the UI can say in a sentence. */
sealed interface BackupImportError {
    /** The bytes are not JSON at all. */
    data object NotJson : BackupImportError

    /** Valid JSON, but not a PaperScrape backup — most likely a shared *theme* file. */
    data class WrongKind(val found: String?) : BackupImportError

    /** Written by a newer app than this one, in a shape this build cannot be sure it understands. */
    data class TooNew(val fileVersion: Int, val supported: Int) : BackupImportError

    /** A field that must be present and well-formed is not. */
    data class Malformed(val what: String) : BackupImportError
}

/** Either a document ready to apply, or the reason it was refused. Never a partial result. */
sealed interface BackupParseResult {
    data class Ok(val backup: AppBackup) : BackupParseResult
    data class Failed(val error: BackupImportError) : BackupParseResult
}

fun AppBackup.toJsonString(): String = JSONObject().apply {
    put("kind", AppBackup.DOCUMENT_KIND)
    put("schemaVersion", schemaVersion)
    put("appVersionName", appVersionName)
    put("createdAtMillis", createdAtMillis)
    put(
        "settings",
        JSONObject().apply {
            put("themeId", settings.themeId)
            put("syncWithRealTime", settings.syncWithRealTime)
            put("useLocationForSunTimes", settings.useLocationForSunTimes)
            put("useCustomLocation", settings.useCustomLocation)
            put("deviceLocationKind", settings.deviceLocationKind)
            put("customLocationLatitude", settings.customLocationLatitude.toDouble())
            put("customLocationLongitude", settings.customLocationLongitude.toDouble())
            put("customLocationLabel", settings.customLocationLabel)
            put("liveWeatherEnabled", settings.liveWeatherEnabled)
            put("liveWeatherApiKey", settings.liveWeatherApiKey)
            put("weatherProviderId", settings.weatherProviderId)
            put("weatherApiComApiKey", settings.weatherApiComApiKey)
            put("openWeatherApiKey", settings.openWeatherApiKey)
            put("automaticUpdateCheckEnabled", settings.automaticUpdateCheckEnabled)
            put("updateNotificationsEnabled", settings.updateNotificationsEnabled)
            put("fixedHour", settings.fixedHour.toDouble())
            put("parallaxStrength", settings.parallaxStrength.toDouble())
            put("scrollBackground", settings.scrollBackground)
            put("swipeScroll", settings.swipeScroll)
            put("scrollSpeed", settings.scrollSpeed.toDouble())
            put("autoThemeByDate", settings.autoThemeByDate)
            if (settings.seasonalCalendarJson.isNotEmpty()) {
                put("seasonalCalendar", settings.seasonalCalendarJson)
            }
        },
    )
    put(
        "themeCustomizations",
        JSONObject().apply { for ((id, c) in themeCustomizations) put(id, c.toJson()) },
    )
    // The schema the embedded theme entries are written in, so a future app knows what it is
    // reading them as. See migrateEmbeddedCustomThemes (BCK-07).
    put("customThemeSchemaVersion", CUSTOM_THEME_SCHEMA_VERSION)
    put(
        "overrides",
        JSONObject().apply { for ((id, e) in customThemeData.overrides) put(id, e.toJson()) },
    )
    put(
        "customThemes",
        JSONArray().apply { for (e in customThemeData.customThemes) put(e.toJson()) },
    )
}.toString(2)

/**
 * Reads a backup document, or says why it will not.
 *
 * **Nothing is applied here and nothing can be half-applied.** The caller gets a whole document or
 * an error; the write is a separate step. Unknown keys are ignored rather than rejected, so a file
 * from a later build still restores everything this build knows about — but a *newer schema
 * version* is refused, because "ignore what you don't know" is only safe when the fields you do
 * know still mean what they meant.
 */
fun parseAppBackup(raw: String?, defaults: WallpaperSettings = WallpaperSettings()): BackupParseResult {
    if (raw.isNullOrBlank()) return BackupParseResult.Failed(BackupImportError.NotJson)
    val root = runCatching { JSONObject(raw) }.getOrNull()
        ?: return BackupParseResult.Failed(BackupImportError.NotJson)

    val kind = root.optString("kind").takeIf { it.isNotBlank() }
    if (kind != AppBackup.DOCUMENT_KIND) {
        return BackupParseResult.Failed(BackupImportError.WrongKind(kind))
    }
    val version = root.optInt("schemaVersion", -1)
    if (version <= 0) return BackupParseResult.Failed(BackupImportError.Malformed("schemaVersion"))
    if (version > AppBackup.SCHEMA_VERSION) {
        return BackupParseResult.Failed(BackupImportError.TooNew(version, AppBackup.SCHEMA_VERSION))
    }

    val s = root.optJSONObject("settings")
        ?: return BackupParseResult.Failed(BackupImportError.Malformed("settings"))

    val customizations = HashMap<String, SceneCustomization>()
    root.optJSONObject("themeCustomizations")?.let { obj ->
        for (id in obj.keys()) {
            val entry = obj.optJSONObject(id)
                ?: return BackupParseResult.Failed(BackupImportError.Malformed("themeCustomizations.$id"))
            // `runCatching` for the same reason as the two blocks below, and it was missing here
            // until v5.3 -- the one unguarded call in either parser, and it happened to be the one
            // that reached the four `getInt`s of `objectVariantConfigFromJson`. Two independent
            // oversights lining up is what turned a wrong colour into a closed settings screen
            // (v5.3B audit, S2). The colours are `optInt` now; what it still catches today is a
            // `birds.colors` entry that is not an object (`getJSONObject` in
            // `sceneCustomizationFromJson`), and it is here so the next reader who adds a throwing
            // accessor downstream gets a refused document instead of a vanished app.
            customizations[id] = runCatching { sceneCustomizationFromJson(entry) }.getOrNull()
                ?: return BackupParseResult.Failed(BackupImportError.Malformed("themeCustomizations.$id"))
        }
    }

    val overrides = HashMap<String, CustomThemeEntry>()
    // The embedded theme entries are in whatever schema the app that wrote this backup used. A
    // backup written before the version was recorded came from an app at schema 3 (backups since
    // v4.3, the field since v4.15, 3 -> 4 only in v4.20), so absent means 3: not the legacy 0,
    // which would re-run 1 -> 2 and corrupt every object's scale, and not current, which until
    // v5.8C skipped the 3 -> 4 repair and the 4 -> 5 school. See migrateEmbeddedCustomThemes.
    migrateEmbeddedCustomThemes(
        root,
        root.optInt("customThemeSchemaVersion", BACKUP_SCHEMA_BEFORE_THE_FIELD),
    )

    root.optJSONObject("overrides")?.let { obj ->
        for (id in obj.keys()) {
            val entry = obj.optJSONObject(id)
                ?: return BackupParseResult.Failed(BackupImportError.Malformed("overrides.$id"))
            val parsed = runCatching { customThemeEntryFromJson(entry) }.getOrNull()
                ?: return BackupParseResult.Failed(BackupImportError.Malformed("overrides.$id"))
            overrides[id] = parsed
        }
    }

    val customThemes = ArrayList<CustomThemeEntry>()
    root.optJSONArray("customThemes")?.let { arr ->
        for (i in 0 until arr.length()) {
            val entry = arr.optJSONObject(i)
                ?: return BackupParseResult.Failed(BackupImportError.Malformed("customThemes[$i]"))
            val parsed = runCatching { customThemeEntryFromJson(entry) }.getOrNull()
                ?: return BackupParseResult.Failed(BackupImportError.Malformed("customThemes[$i]"))
            customThemes.add(parsed)
        }
    }

    return BackupParseResult.Ok(
        AppBackup(
            schemaVersion = version,
            appVersionName = root.optString("appVersionName", ""),
            createdAtMillis = root.optLong("createdAtMillis", 0L),
            settings = AppBackup.BackupSettings(
                themeId = s.optString("themeId", defaults.themeId),
                syncWithRealTime = s.optBoolean("syncWithRealTime", defaults.syncWithRealTime),
                useLocationForSunTimes = s.optBoolean("useLocationForSunTimes", defaults.useLocationForSunTimes),
                useCustomLocation = s.optBoolean("useCustomLocation", defaults.useCustomLocation),
                deviceLocationKind = s.optString("deviceLocationKind", defaults.deviceLocationKind.storageId),
                // The five numbers here go through `optFinite` like every number in the theme half
                // (v5.8). They were read raw, so a "NaN" or a 1e300 was accepted and stored in the
                // preferences, and from then on every "Export backup" failed with "Could not save
                // the backup: Forbidden numeric value" -- `JSONObject.put` refuses non-finite values.
                customLocationLatitude = s.optFinite("customLocationLatitude", defaults.customLocationLatitude),
                customLocationLongitude = s.optFinite("customLocationLongitude", defaults.customLocationLongitude),
                customLocationLabel = s.optString("customLocationLabel", defaults.customLocationLabel),
                liveWeatherEnabled = s.optBoolean("liveWeatherEnabled", defaults.liveWeatherEnabled),
                liveWeatherApiKey = s.optString("liveWeatherApiKey", defaults.liveWeatherApiKey),
                weatherProviderId = s.optString("weatherProviderId", defaults.weatherProviderId),
                weatherApiComApiKey = s.optString("weatherApiComApiKey", defaults.weatherApiComApiKey),
                openWeatherApiKey = s.optString("openWeatherApiKey", defaults.openWeatherApiKey),
                automaticUpdateCheckEnabled = s.optBoolean("automaticUpdateCheckEnabled", defaults.automaticUpdateCheckEnabled),
                updateNotificationsEnabled = s.optBoolean("updateNotificationsEnabled", defaults.updateNotificationsEnabled),
                fixedHour = s.optFinite("fixedHour", defaults.fixedHour),
                parallaxStrength = s.optFinite("parallaxStrength", defaults.parallaxStrength),
                scrollBackground = s.optBoolean("scrollBackground", defaults.scrollBackground),
                swipeScroll = s.optBoolean("swipeScroll", defaults.swipeScroll),
                scrollSpeed = s.optFinite("scrollSpeed", defaults.scrollSpeed),
                autoThemeByDate = s.optBoolean("autoThemeByDate", defaults.autoThemeByDate),
                // Absent means the factory calendar, which is also what a backup taken before this
                // field existed means. Both read back as the empty string.
                seasonalCalendarJson = s.optString("seasonalCalendar", ""),
            ),
            themeCustomizations = customizations,
            customThemeData = CustomThemeData(overrides = overrides, customThemes = customThemes),
        ),
    )
}

/** The `DeviceLocationKind` a backup names, or the safe default if it names nothing known. */
internal fun AppBackup.BackupSettings.locationKind(): DeviceLocationKind =
    DeviceLocationKind.fromStorageId(deviceLocationKind)
