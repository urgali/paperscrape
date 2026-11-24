package com.paperscrape.livewallpaper.location

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * **One search for the phone's position at a time, in the whole process.** Three callers can ask at once: the home
 * screen's engine and the picker's preview (each has a settings collector and a weather loop), and the *Weather & time*
 * page's *Update location now*, which runs in the same process as the wallpaper. Each waits here for the one before it,
 * so the radio is never asked twice over, and a caller that waited finds the position the first one brought in the
 * system's cache ([DeviceLocationProvider.currentFix] takes a young enough cached position without a request).
 *
 * A caller whose request depends on what the one before it found -- the engine's force, spent once the phone has been
 * consulted for the user's choice -- reads it **inside** [withLock], after the wait, not before it.
 *
 * Cancellable while waiting: a page left, or an engine destroyed, before its turn takes nothing from the radio.
 */
object LocationRequestLock {

    private val mutex = Mutex()

    /** Runs [block] when no other search of the process is running, and holds the others back until it ends. */
    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }

    /** Whether a search holds the lock now. For tests and diagnostics only: the answer can change the next moment. */
    val isLocked: Boolean get() = mutex.isLocked
}
