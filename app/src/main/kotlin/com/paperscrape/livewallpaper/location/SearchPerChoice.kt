package com.paperscrape.livewallpaper.location

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * **The settings collector's search for the phone's position: one per choice of source.** The collector runs on every
 * write to the settings store, and while no position is held each run would start a search of its own; a write that
 * comes while the first search is still running (the engine publishing its weather status, the page writing anything)
 * would start a second, which waits for the first on [LocationRequestLock] and then asks the radio again. So a search is
 * started only when none is running **for the same source**: another source chosen while one runs gets its own (the
 * first one's answer is dropped by the engine, as an answer for a source left), and once a search has ended the next
 * write may start one again, under [LocationRequestThrottle]'s waits like every other ask.
 *
 * Main thread only, like the collector that uses it.
 */
class SearchPerChoice {

    private var running: Job? = null
    private var runningFor: LocationSource? = null

    /** Whether a search for [source] is running now. */
    fun isRunningFor(source: LocationSource): Boolean = running?.isActive == true && runningFor == source

    /**
     * Starts [search] for [source] in [scope] unless one for the same source is still running. Returns whether it was
     * started.
     */
    fun startUnlessRunning(scope: CoroutineScope, source: LocationSource, search: suspend () -> Unit): Boolean {
        if (isRunningFor(source)) return false
        runningFor = source
        running = scope.launch { search() }
        return true
    }
}
