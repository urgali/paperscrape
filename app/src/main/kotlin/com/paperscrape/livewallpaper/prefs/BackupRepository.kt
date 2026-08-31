package com.paperscrape.livewallpaper.prefs

import com.paperscrape.livewallpaper.engine.toJsonString
import com.paperscrape.livewallpaper.engine.CustomThemeData
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

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
 *
 * ### v4.6: the staging only holds if nothing can interrupt it
 *
 * Steps 3 and 4 run inside `NonCancellable`. They did not, and the caller's scope is the settings
 * screen's composition — so a rotation or a back press between the two writes produced exactly the
 * half-old, half-new state the staging exists to prevent, *and* skipped the rollback. See the
 * comment at the call site; that is the whole of the change, and nothing about the format, the
 * ordering or the number of stores moved with it.
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
        // **The two writes and the rollback are one uncancellable region.**
        //
        // Everything above this line may be cancelled freely: parsing and the snapshot read
        // change nothing on disk, and abandoning them leaves the app exactly as it was. From here
        // the caller has started a transaction across two stores that have no transaction between
        // them, and the only states worth being in are "both old" and "both new".
        //
        // The failure this closes is not a crash. `import` is called from the settings screen's
        // `rememberCoroutineScope`, which the composition cancels when it goes away -- a rotation,
        // a back press, the system reclaiming the Activity. Without this, a cancellation landing
        // between the two `replaceAll`s left the preferences new and the saved themes old, and
        // then made it worse: the `catch` below caught the `CancellationException`, the rollback
        // suspended on an already-cancelled job, threw again immediately, and the result was
        // `Broken` -- reported to a UI that no longer existed. The window is milliseconds wide and
        // needs no crash to reach.
        //
        // `NonCancellable` is the whole fix. No journal, no write-ahead log, no third store: the
        // pair is short, bounded and already ordered, and what it lacked was only the guarantee
        // that nothing could interrupt it half way.
        // **BCK-06: and a kill is not a cancellation.** `NonCancellable` above stops the coroutine
        // being interrupted; it cannot stop the process being killed, and between the two writes the
        // preferences were new while the saved themes were still old.
        //
        // The two stores have no transaction between them and are not going to get one. What each
        // store *does* guarantee is that its own write is atomic, and that is enough to make the
        // pair recoverable: the second store's whole payload rides inside the first store's atomic
        // edit, is applied, and is then cleared. Whatever instant the process dies in, the next
        // start finds either nothing pending or exactly the bytes the second store was owed, and
        // re-applying them is the same write.
        //
        // This is deliberately not a journal. There is no sequence to replay, no ordering to
        // reconstruct and nothing to undo: the pending document *is* the whole of the remaining
        // work, and [finishPendingImport] is three lines long.
        val pendingJson = backup.customThemeData.toJsonString()
        return withContext(NonCancellable) {
            try {
                ImportStaging.apply(
                    payload = pendingJson,
                    stagePrefs = { prefs.replaceAllStagingThemes(backup.settings, backup.themeCustomizations, it) },
                    writeThemes = { store.replaceAllJson(it) },
                    clearPending = { prefs.clearPendingImportThemes() },
                )
            } catch (failure: Throwable) {
                return@withContext try {
                    prefs.replaceAll(undo.settings, undo.themeCustomizations)
                    store.replaceAll(undo.customThemeData)
                    // `replaceAll` with no pending document clears the key in the same atomic edit,
                    // so a rollback cannot leave a completion step pointing at the import that was
                    // just undone.
                    ImportResult.RolledBack(failure)
                } catch (rollbackFailure: Throwable) {
                    ImportResult.Broken(failure, rollbackFailure)
                }
            }
            ImportResult.Applied(backup)
        }
    }

    /**
     * Finishes an import the process died in the middle of, if there was one.
     *
     * Called at every entry point that reads the saved themes -- the wallpaper service and the
     * settings screen -- so the inconsistent window closes before anything can observe it. Costs one
     * preference read when there is nothing to do, which is every start but the one after a kill.
     *
     * Idempotent by construction: it writes the staged document and then clears it, and writing the
     * same document twice is the same document. A kill *inside* this call leaves the key set and the
     * next start does it again.
     */
    suspend fun finishPendingImport(): Boolean = withContext(NonCancellable) {
        ImportStaging.finish(
            pending = prefs.pendingImportThemes(),
            writeThemes = { store.replaceAllJson(it) },
            clearPending = { prefs.clearPendingImportThemes() },
        )
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

/**
 * The two halves of a two-store import, as an order and a recovery (BCK-06).
 *
 * Written as functions over the writes rather than inline in [BackupRepository] for one reason: the
 * property that matters is **what a kill at each point leaves behind**, and a kill is not something
 * a test can do to Android's `DataStore`. Here it is a lambda that throws, and
 * `AtomicImportTest` walks every point.
 *
 * The order is the whole design and it is only three steps:
 *
 *  1. write the first store **including the second store's entire payload**, in that store's own
 *     atomic edit;
 *  2. write the second store from that payload;
 *  3. clear the payload.
 *
 * Every possible interruption lands on a consistent pair. Before 1: nothing changed. Between 1 and
 * 2, or between 2 and 3: the payload is on disk, and [finish] applies it — writing it a second time
 * is the same write, so it does not matter which side of 2 the process died on. After 3: done.
 *
 * There is no journal here and none is needed. A journal exists to replay a sequence; this has no
 * sequence to replay, because the payload *is* the whole of the remaining work.
 */
internal object ImportStaging {

    /** Steps 1-3. Throws whatever the writes throw, leaving the caller to roll back. */
    suspend fun apply(
        payload: String,
        stagePrefs: suspend (String) -> Unit,
        writeThemes: suspend (String) -> Unit,
        clearPending: suspend () -> Unit,
    ) {
        stagePrefs(payload)
        writeThemes(payload)
        clearPending()
    }

    /** Steps 2-3 for an import that stopped after 1 or 2. `false` when there was nothing pending. */
    suspend fun finish(
        pending: String?,
        writeThemes: suspend (String) -> Unit,
        clearPending: suspend () -> Unit,
    ): Boolean {
        if (pending == null) return false
        writeThemes(pending)
        clearPending()
        return true
    }
}
