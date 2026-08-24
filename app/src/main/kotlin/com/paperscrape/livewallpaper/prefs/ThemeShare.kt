package com.paperscrape.livewallpaper.prefs

import com.paperscrape.livewallpaper.engine.CustomThemeEntry
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.SceneObjectCatalog
import com.paperscrape.livewallpaper.engine.SceneObjectLayout
import com.paperscrape.livewallpaper.engine.SceneTheme
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.engine.sceneCustomizationFromJson
import com.paperscrape.livewallpaper.engine.sceneObjectLayoutFromJson
import com.paperscrape.livewallpaper.engine.sceneThemeFromJson
import com.paperscrape.livewallpaper.engine.toJson
import org.json.JSONObject

/**
 * One theme, packaged so somebody else can use it.
 *
 * ### Separate from the backup, on purpose
 *
 * A backup is *this user's whole app*; a shared theme is *one look*, meant to be posted in a
 * forum and opened by a stranger. They have different contents, different audiences and different
 * lifetimes, so they have **different schema versions** ([SCHEMA_VERSION] here,
 * [AppBackup.SCHEMA_VERSION] there) and different `kind` markers. Importing one where the other is
 * expected is refused by name rather than by a parse failure, so the app can say which file the
 * user picked.
 *
 * ### What it carries, and what it must not
 *
 * Everything needed to *draw* the theme and nothing about the person who made it: the scene's
 * colours ([SceneTheme]), what stands in it ([SceneObjectLayout]), and how it is customised
 * ([SceneCustomization]). **No global settings, no location, no weather API keys, no personal
 * preference of any kind** — `ThemeShareTest` reads a packaged theme back as raw JSON and fails if
 * any of those words appear in it.
 *
 * ### Why an exported theme is self-contained
 *
 * A theme exported today has to keep working when the built-in it came from is redrawn. So the
 * package carries the **resolved** theme and layout — the actual objects and colours at export
 * time — rather than a reference to a built-in id that a future release may define differently.
 * [sourceThemeId] is recorded for provenance only: it is displayed, never resolved against. That
 * is the difference between "a copy of Beach as it looked when I shared it" and "whatever Beach
 * happens to be on your phone", and only the first is shareable.
 */
data class ThemeShare(
    val schemaVersion: Int,
    val appVersionName: String,
    val exportedAtMillis: Long,
    /** Where this look came from, for display. Never used to resolve anything. */
    val sourceThemeId: String,
    /** The name the author gave it. The importer may change it. */
    val name: String,
    val theme: SceneTheme,
    val layout: SceneObjectLayout,
    val customization: SceneCustomization,
) {
    companion object {
        /** This format's own version, unrelated to the backup's and to the store's. */
        const val SCHEMA_VERSION = 1

        /** What an export writes and an import demands. */
        const val DOCUMENT_KIND = "paperscrape-theme"

        /**
         * Packages the theme [sourceThemeId] currently resolves to, whatever kind it is.
         *
         * Works for a built-in, a built-in the user has overridden, and a standalone saved theme
         * alike, because all three resolve through the same catalogue and the same customization
         * lookup. [customization] is passed in rather than looked up so the caller can package
         * exactly what is on screen.
         */
        fun of(
            sourceThemeId: String,
            name: String,
            customization: SceneCustomization,
            appVersionName: String,
            nowMillis: Long,
        ): ThemeShare {
            val theme = ThemeCatalog.byId(sourceThemeId)
            return ThemeShare(
                schemaVersion = SCHEMA_VERSION,
                appVersionName = appVersionName,
                exportedAtMillis = nowMillis,
                sourceThemeId = sourceThemeId,
                name = name,
                theme = theme,
                layout = SceneObjectCatalog.layoutFor(sourceThemeId, theme.accentColor),
                customization = customization,
            )
        }
    }

    /**
     * Turns this package into a saved theme of the importer's own.
     *
     * **Always a new, independent theme; never an overwrite.** The id is freshly minted, so
     * importing the same file twice gives two themes rather than silently replacing one, and the
     * entry carries the packaged theme and layout rather than a pointer to a built-in. The format
     * records [sourceThemeId] so a future release could offer "replace my Beach with this" — but
     * that has to be a thing the user asks for, not something an import does on its own.
     */
    fun asNewCustomTheme(id: String, displayName: String = name): CustomThemeEntry = CustomThemeEntry(
        id = id,
        name = displayName,
        theme = theme.copy(id = id, displayName = displayName),
        layout = layout,
        customization = customization,
    )

    /** A short line for the import confirmation: what this file is, without touching the app. */
    fun summary(): String =
        "\"$name\" — ${layout.staticObjects.size} objects, ${layout.cars.size} vehicles" +
            if (sourceThemeId.isNotBlank()) " (from $sourceThemeId)" else ""
}

/** Why a theme file was refused. */
sealed interface ThemeImportError {
    data object NotJson : ThemeImportError

    /** Valid JSON but not a theme — most likely a whole-app backup. */
    data class WrongKind(val found: String?) : ThemeImportError
    data class TooNew(val fileVersion: Int, val supported: Int) : ThemeImportError
    data class Malformed(val what: String) : ThemeImportError
}

sealed interface ThemeParseResult {
    data class Ok(val share: ThemeShare) : ThemeParseResult
    data class Failed(val error: ThemeImportError) : ThemeParseResult
}

fun ThemeShare.toJsonString(): String = JSONObject().apply {
    put("kind", ThemeShare.DOCUMENT_KIND)
    put("schemaVersion", schemaVersion)
    put("appVersionName", appVersionName)
    put("exportedAtMillis", exportedAtMillis)
    put("sourceThemeId", sourceThemeId)
    put("name", name)
    put("theme", theme.toJson())
    put("layout", layout.toJson())
    put("customization", customization.toJson())
}.toString(2)

/**
 * Reads a shared theme, or says why it will not.
 *
 * Unknown keys are ignored, so a file from a later build still imports what this one understands.
 * A newer *schema version* is refused: ignoring unknown fields is only safe while the known ones
 * still mean what they meant.
 */
fun parseThemeShare(raw: String?): ThemeParseResult {
    if (raw.isNullOrBlank()) return ThemeParseResult.Failed(ThemeImportError.NotJson)
    val root = runCatching { JSONObject(raw) }.getOrNull()
        ?: return ThemeParseResult.Failed(ThemeImportError.NotJson)

    val kind = root.optString("kind").takeIf { it.isNotBlank() }
    if (kind != ThemeShare.DOCUMENT_KIND) {
        return ThemeParseResult.Failed(ThemeImportError.WrongKind(kind))
    }
    val version = root.optInt("schemaVersion", -1)
    if (version <= 0) return ThemeParseResult.Failed(ThemeImportError.Malformed("schemaVersion"))
    if (version > ThemeShare.SCHEMA_VERSION) {
        return ThemeParseResult.Failed(ThemeImportError.TooNew(version, ThemeShare.SCHEMA_VERSION))
    }

    val themeJson = root.optJSONObject("theme")
        ?: return ThemeParseResult.Failed(ThemeImportError.Malformed("theme"))
    val theme = runCatching { sceneThemeFromJson(themeJson) }.getOrNull()
        ?: return ThemeParseResult.Failed(ThemeImportError.Malformed("theme"))
    val layoutJson = root.optJSONObject("layout")
        ?: return ThemeParseResult.Failed(ThemeImportError.Malformed("layout"))
    val layout = runCatching { sceneObjectLayoutFromJson(layoutJson) }.getOrNull()
        ?: return ThemeParseResult.Failed(ThemeImportError.Malformed("layout"))
    val name = root.optString("name").takeIf { it.isNotBlank() }
        ?: return ThemeParseResult.Failed(ThemeImportError.Malformed("name"))

    return ThemeParseResult.Ok(
        ThemeShare(
            schemaVersion = version,
            appVersionName = root.optString("appVersionName", ""),
            exportedAtMillis = root.optLong("exportedAtMillis", 0L),
            sourceThemeId = root.optString("sourceThemeId", ""),
            name = name,
            theme = theme,
            layout = layout,
            customization = sceneCustomizationFromJson(root.optJSONObject("customization")),
        ),
    )
}

/**
 * A name that does not collide with a theme the user already has.
 *
 * "Beach" imported twice becomes "Beach" and "Beach (2)". Names are for humans and are not
 * identity — the id is — so a duplicate is a nuisance rather than an error, and this removes the
 * nuisance without refusing the import.
 */
fun uniqueThemeName(wanted: String, taken: Collection<String>): String {
    if (wanted !in taken) return wanted
    var n = 2
    while ("$wanted ($n)" in taken) n++
    return "$wanted ($n)"
}
