package com.paperscrape.livewallpaper.prefs

import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.migrateEmbeddedCustomThemes
import com.paperscrape.livewallpaper.engine.optFinite
import com.paperscrape.livewallpaper.engine.BACKUP_SCHEMA_BEFORE_THE_FIELD
import com.paperscrape.livewallpaper.engine.CUSTOM_THEME_SCHEMA_VERSION
import com.paperscrape.livewallpaper.engine.CustomThemeEntry
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.UnreadableThemeEntry
import com.paperscrape.livewallpaper.engine.customThemeEntryFromJson
import com.paperscrape.livewallpaper.engine.sceneCustomizationFromJson
import com.paperscrape.livewallpaper.engine.seasonalCalendarFromJsonString
import com.paperscrape.livewallpaper.engine.themeKnownAs
import com.paperscrape.livewallpaper.engine.toJson
import com.paperscrape.livewallpaper.engine.toJsonString
import com.paperscrape.livewallpaper.location.DeviceLocationKind
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

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
 *  - every saved theme: built-in overrides and standalone custom themes, whole -- and the ones this
 *    build cannot read (a newer build's), as the store keeps them, byte for byte.
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
 * [AppBackup] and [BackupSettings] are data classes, whose generated `toString` would print all
 * three keys, as would a `BackupRepository.ImportResult.Applied` holding one. Nothing prints one,
 * and to keep anything from doing so [BackupSettings] writes its own `toString`, which says how
 * long each key is and never what it is; the others print through it.
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

        /**
         * The longest holiday calendar an import keeps as it is written. Every window moved and Easter too is
         * under a kilobyte; a longer document is read and written back as this build writes it
         * ([readCalendar]), so a file padded to megabytes cannot reach the preferences, which are read again at
         * every change of a setting.
         */
        const val MAX_CALENDAR_CHARS = 4_096

        /**
         * The longest place name and API key an import accepts; a longer one in a file leaves the setting as it was.
         *
         * The app's own limit too: the place's name and the three keys are kept to this many characters when they are
         * written (`keptText`), so a backup gives back whatever the app kept. A ceiling far above anything typed or found
         * there (a key is a few dozen characters; a place found by the search is its name and its country), and it keeps
         * out a file padded to megabytes, whose text the preferences would read again at every change of a setting. It is
         * the holiday calendar's ceiling too ([MAX_CALENDAR_CHARS]): the four texts add 16 384 characters at most.
         */
        const val MAX_TEXT_SETTING_CHARS = 4_096

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

    /**
     * The saved themes a restore writes over [current], the phone's own: this backup's, **with the phone's saved themes
     * this build cannot read kept** ([CustomThemeData.unreadable]), unless the backup carries the same bytes already.
     *
     * Nothing lists or draws those themes, so nothing the user chose in restoring can be about them, and a restore is
     * a whole-document write: a backup taken by a build that did not carry them -- yesterday's, or another phone's --
     * would otherwise delete the only copy, which the store keeps so that no write can lose it.
     */
    fun themesWrittenOver(current: CustomThemeData): CustomThemeData {
        val carried = customThemeData.unreadable
        val kept = current.unreadable.filter { it !in carried }
        return if (kept.isEmpty()) customThemeData else customThemeData.copy(unreadable = carried + kept)
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

    /**
     * This install's settings or saved themes cannot be read just now: what a failed write would be put back to, and what
     * a backup's absent fields would be read against, is unknown, so nothing is applied.
     */
    data object Unreadable : BackupImportError
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
    // reading them as. See migrateEmbeddedCustomThemes.
    put("customThemeSchemaVersion", CUSTOM_THEME_SCHEMA_VERSION)
    put(
        "overrides",
        JSONObject().apply { for ((id, e) in customThemeData.overrides) put(id, e.toJson()) },
    )
    put(
        "customThemes",
        JSONArray().apply { for (e in customThemeData.customThemes) put(e.toJson()) },
    )
    // The saved themes this build cannot read, each item as text (a JSON string), so its bytes come back exactly as
    // the store keeps them: org.json would re-serialise a parsed object, and those bytes are the only copy. A build
    // that does not know the key ignores it, as it ignores any other.
    if (customThemeData.unreadable.isNotEmpty()) {
        put(UNREADABLE_THEMES_KEY, JSONArray().apply { for (item in customThemeData.unreadable) put(item.itemJson) })
    }
}.toString(2)

/** Where a backup carries [CustomThemeData.unreadable]. */
private const val UNREADABLE_THEMES_KEY = "unreadableThemes"

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
    // Read once the backup's own themes are, below: a customization written without its public buildings'
    // colours reads them against the theme it belongs to (`publicsCarriedOver`), which may be one of them.
    val customizationEntries = LinkedHashMap<String, JSONObject>()
    root.optJSONObject("themeCustomizations")?.let { obj ->
        for (id in obj.keys()) {
            customizationEntries[id] = obj.optJSONObject(id)
                ?: return BackupParseResult.Failed(BackupImportError.Malformed("themeCustomizations.$id"))
        }
    }

    val overrides = HashMap<String, CustomThemeEntry>()
    // The embedded theme entries are in whatever schema the app that wrote this backup used. A
    // backup that records no version came from an app at schema 3 (every app that wrote backups
    // without the field was at 3), so absent means 3: not the legacy 0, which would re-run
    // 1 -> 2 and corrupt every object's scale, and not current, which would skip the 3 -> 4
    // repair and the 4 -> 5 school. See migrateEmbeddedCustomThemes.
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

    // Kept as the text they were written in; a later read of the store tries each again (`customThemeDataFromJsonString`).
    // An item that is not a string was not written by an export, and is kept as the text it reads as. A string that is not
    // a JSON object is no theme at all, and kept it would be pasted into the store's document and break every theme in it.
    val unreadable = ArrayList<UnreadableThemeEntry>()
    root.optJSONArray(UNREADABLE_THEMES_KEY)?.let { arr ->
        for (i in 0 until arr.length()) {
            when (val item = arr.opt(i)) {
                is String -> if (isOneJsonObject(item)) unreadable += UnreadableThemeEntry(item)
                is JSONObject -> unreadable += UnreadableThemeEntry(item.toString())
                else -> Unit
            }
        }
    }

    for ((id, entry) in customizationEntries) {
        val theme = overrides[id]?.theme ?: customThemes.firstOrNull { it.id == id }?.theme ?: themeKnownAs(id)
        // `runCatching` for the same reason as the two blocks above: no call in either parser is
        // unguarded, because an exception thrown by a reader downstream would otherwise turn a
        // wrong value in the file into a closed settings screen. What it catches is a
        // `birds.colors` entry that is not an object (`getJSONObject` in
        // `sceneCustomizationFromJson`), and it is here so a throwing accessor added downstream
        // gets a refused document instead of a vanished app.
        customizations[id] = runCatching { sceneCustomizationFromJson(entry, theme) }.getOrNull()
            ?: return BackupParseResult.Failed(BackupImportError.Malformed("themeCustomizations.$id"))
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
                // The five numbers here go through `optFinite` like every number in the theme half.
                // Read raw, a "NaN" or a 1e300 would be accepted and stored in the preferences, and
                // from then on every "Export backup" would fail with "Could not save the backup:
                // Forbidden numeric value" -- `JSONObject.put` refuses non-finite values.
                customLocationLatitude = s.optFinite("customLocationLatitude", defaults.customLocationLatitude),
                customLocationLongitude = s.optFinite("customLocationLongitude", defaults.customLocationLongitude),
                customLocationLabel = s.optTextSetting("customLocationLabel", defaults.customLocationLabel),
                liveWeatherEnabled = s.optBoolean("liveWeatherEnabled", defaults.liveWeatherEnabled),
                liveWeatherApiKey = s.optTextSetting("liveWeatherApiKey", defaults.liveWeatherApiKey),
                weatherProviderId = s.optString("weatherProviderId", defaults.weatherProviderId),
                weatherApiComApiKey = s.optTextSetting("weatherApiComApiKey", defaults.weatherApiComApiKey),
                openWeatherApiKey = s.optTextSetting("openWeatherApiKey", defaults.openWeatherApiKey),
                automaticUpdateCheckEnabled = s.optBoolean("automaticUpdateCheckEnabled", defaults.automaticUpdateCheckEnabled),
                updateNotificationsEnabled = s.optBoolean("updateNotificationsEnabled", defaults.updateNotificationsEnabled),
                fixedHour = s.optFinite("fixedHour", defaults.fixedHour),
                parallaxStrength = s.optFinite("parallaxStrength", defaults.parallaxStrength),
                scrollBackground = s.optBoolean("scrollBackground", defaults.scrollBackground),
                swipeScroll = s.optBoolean("swipeScroll", defaults.swipeScroll),
                scrollSpeed = s.optFinite("scrollSpeed", defaults.scrollSpeed),
                autoThemeByDate = s.optBoolean("autoThemeByDate", defaults.autoThemeByDate),
                // Absent means the factory calendar, which is also what a backup written without
                // this field means. Both read back as the empty string.
                seasonalCalendarJson = readCalendar(s.optString("seasonalCalendar", "")),
            ),
            themeCustomizations = customizations,
            customThemeData = CustomThemeData(overrides = overrides, customThemes = customThemes, unreadable = unreadable),
        ),
    )
}

/**
 * The holiday calendar a backup carries, as the preferences may hold it: **the empty string for the factory
 * calendar** -- only the windows the user moved are stored, so a document that moves none, however it is written,
 * is no document -- and a document longer than [AppBackup.MAX_CALENDAR_CHARS] read and written back as this build
 * writes one, which is under a kilobyte. Anything else is kept as it was written.
 */
private fun readCalendar(raw: String): String {
    if (raw.isEmpty()) return ""
    val calendar = seasonalCalendarFromJsonString(raw)
    return when {
        calendar.isFactory -> ""
        raw.length > AppBackup.MAX_CALENDAR_CHARS -> calendar.toJsonString()
        else -> raw
    }
}

/** A text setting, or [default] when it is absent or longer than [AppBackup.MAX_TEXT_SETTING_CHARS]. */
private fun JSONObject.optTextSetting(name: String, default: String): String =
    optString(name, default).takeIf { it.length <= AppBackup.MAX_TEXT_SETTING_CHARS } ?: default

/** The `DeviceLocationKind` a backup names, or the safe default if it names nothing known. */
internal fun AppBackup.BackupSettings.locationKind(): DeviceLocationKind =
    DeviceLocationKind.fromStorageId(deviceLocationKind)

/**
 * Whether [text] is one JSON object and nothing after it. `JSONObject(String)` reads an object and ignores what follows it
 * (`{}]` reads as `{}`), and an entry kept so would carry the rest into the saved-themes document.
 */
private fun isOneJsonObject(text: String): Boolean = runCatching {
    val tokener = JSONTokener(text)
    tokener.nextValue() is JSONObject && tokener.nextClean() == '\u0000'
}.getOrDefault(false)
