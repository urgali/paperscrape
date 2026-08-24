package com.paperscrape.livewallpaper.prefs

import com.paperscrape.livewallpaper.engine.CustomThemeData
import kotlinx.coroutines.flow.first

/**
 * Reads and writes whole-app backups across the two stores that hold user data.
 *
 * ### Why this needs a recovery step at all
 *
 * User state lives in **two** DataStores — `paperscrape_prefs` for preferences and per-theme
 * customizations, `paperscrape_custom_themes` for saved themes — and DataStore has no transaction
 * spanning two files. Each store's own write is atomic; the pair is not. An import that wrote the
 * first and failed on the second would leave a user with their new preferences and their old
 * themes, which is the "half old, half new" outcome this feature must not produce.
 *
 * ### The staging that replaces a transaction
 *
 * 1. **Parse and validate the whole document first.** [parseAppBackup] returns a complete backup
 *    or an error, never a partial one, and nothing is written until it has returned a backup.
 * 2. **Snapshot the current state** into the same in-memory shape a backup has. This costs one
 *    read of each store and is the undo log.
 * 3. **Write both stores.** If the second write throws, the snapshot is written back over the
 *    first, restoring the state the user started from.
 * 4. If the rollback itself fails, say so plainly rather than reporting success — that is the one
 *    case where the app cannot put things back, and the user needs to know which file to re-import.
 */
class BackupRepository(
    private val prefs: WallpaperPrefs,
    private val store: CustomThemeStore,
    private val appVersionName: String,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** Everything this install would put in a backup file, right now. */
    suspend fun snapshot(): AppBackup = AppBackup.from(
        settings = prefs.settingsFlow.first(),
        customThemeData = store.dataFlow.first(),
        appVersionName = appVersionName,
        nowMillis = now(),
    )

    /** The document an export writes, as text. */
    suspend fun export(): String = snapshot().toJsonString()

    /** What an import did, for the UI to report. */
    sealed interface ImportResult {
        data class Applied(val backup: AppBackup) : ImportResult
        data class Refused(val error: BackupImportError) : ImportResult

        /** Both writes failed and the previous state was put back. Nothing changed. */
        data class RolledBack(val cause: Throwable) : ImportResult

        /** The rollback itself failed. The app is in a state the user must be told about. */
        data class Broken(val cause: Throwable, val rollbackCause: Throwable) : ImportResult
    }

    /**
     * Validates [raw] and, only if it is a whole valid backup, applies it to both stores.
     *
     * Returns without touching anything if the document is not a backup, is a shared theme file,
     * is newer than this build understands, or is malformed.
     */
    suspend fun import(raw: String?): ImportResult {
        val parsed = parseAppBackup(raw, prefs.settingsFlow.first())
        if (parsed is BackupParseResult.Failed) return ImportResult.Refused(parsed.error)
        val backup = (parsed as BackupParseResult.Ok).backup

        val undo = snapshot()
        try {
            prefs.replaceAll(backup.settings, backup.themeCustomizations)
            store.replaceAll(backup.customThemeData)
        } catch (failure: Throwable) {
            return try {
                prefs.replaceAll(undo.settings, undo.themeCustomizations)
                store.replaceAll(undo.customThemeData)
                ImportResult.RolledBack(failure)
            } catch (rollbackFailure: Throwable) {
                ImportResult.Broken(failure, rollbackFailure)
            }
        }
        return ImportResult.Applied(backup)
    }

    /** A file name a user will recognise a year from now. */
    fun suggestedFileName(): String {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.US)
            .format(java.util.Date(now()))
        return "paperscrape-backup-$stamp.json"
    }

    /** For tests and for the UI's confirmation step, without going near the stores. */
    fun preview(raw: String?): BackupParseResult = parseAppBackup(raw)

    private companion object {
        @Suppress("unused")
        val EMPTY_THEMES = CustomThemeData.EMPTY
    }
}
