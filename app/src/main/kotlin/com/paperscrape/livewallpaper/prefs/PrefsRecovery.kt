package com.paperscrape.livewallpaper.prefs

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException

/**
 * What every one of this app's DataStores does when its file cannot be read.
 *
 * Until v3.1 the answer was "nothing", and the consequence was not a lost setting: the read
 * happens inside the wallpaper engine's own scope, so an unhandled `CorruptionException` killed
 * the process that draws the wallpaper, Android replaced PaperScrape with the static system
 * image, and the same crash repeated on every restart. The only escape was clearing app data --
 * which throws away *every* store, including the custom themes the corrupt one had nothing to do
 * with. That is the failure this file exists to make impossible.
 *
 * ### Three different failures, three different answers
 *
 * They are deliberately **not** collapsed into one `catch`, because they do not deserve the same
 * treatment:
 *
 * 1. **The file is corrupt** -- its bytes are not a preferences proto at all. Nothing can ever be
 *    recovered from it and every future read would throw the same way, so [replacingCorruptFile]
 *    rewrites it empty *once* and the store comes up on its defaults from then on. This is the
 *    only case in which anything on disk is destroyed, and it destroys only the file that was
 *    already unreadable.
 * 2. **The read failed for an I/O reason** -- storage momentarily unavailable, a permission
 *    problem, a device under direct-boot. The bytes may be perfectly fine, so
 *    [recoveringFromReadErrors] serves defaults *for this emission only* and touches nothing:
 *    the next successful read brings the real settings straight back. Overwriting here would
 *    turn a transient glitch into permanent data loss.
 * 3. **Anything else** -- an exception nobody predicted. It is rethrown. Swallowing an unknown
 *    error would leave the app running on defaults with no signal that anything is wrong, and the
 *    engine scope now survives it anyway (see `PaperWallpaperService.PaperEngine`).
 *
 * ### One store's failure is only that store's failure
 *
 * Each store owns its own file, and a handler installed on one store can only ever replace that
 * store's own file. A corrupt `paperscrape_custom_themes` therefore costs the user their custom
 * themes and nothing else; the wallpaper settings and the update snooze are not read, not
 * rewritten and not lost. This is the property the "clear app data" workaround could not offer,
 * and it is what the instrumented recovery tests assert.
 */
internal object PrefsRecovery {

    /**
     * The corruption handler every `preferencesDataStore(...)` declaration in this app installs.
     *
     * DataStore calls it only for a `CorruptionException` -- it is not a general error hook -- and
     * uses what it returns as the file's new contents, so returning [emptyPreferences] means
     * "start this store over at its defaults". Every reader in the app already resolves a missing
     * key to a default, so nothing downstream has to know this happened.
     */
    fun replacingCorruptFile(): ReplaceFileCorruptionHandler<Preferences> =
        ReplaceFileCorruptionHandler { emptyPreferences() }

    /**
     * Serves [emptyPreferences] for one emission when a read fails for an I/O reason, and rethrows
     * anything else.
     *
     * Applied to the *flow*, not to the store, precisely because it must not be durable: this is
     * the "the disk was busy" path, and the settings it is standing in for are still on disk.
     */
    fun Flow<Preferences>.recoveringFromReadErrors(): Flow<Preferences> = catch { cause ->
        if (cause is IOException) emit(emptyPreferences()) else throw cause
    }
}
