package com.paperscrape.livewallpaper.location

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Decides **when** a device fix is worth reverse-geocoding, and remembers the answer.
 *
 * Added in v4.0 with the place-name row. The lookup itself stays in [LocationLabelResolver] — this
 * owns only the policy around it, and owns it in one pure, testable place because the policy is
 * the part that can quietly turn into a request per fix.
 *
 * ### Why a cache is needed at all
 *
 * The settings row re-resolves whenever the stored fix changes *and* whenever the screen is
 * re-entered. Neither is rare: `resolvedGpsLatitude`/`resolvedGpsLongitude` are rewritten by
 * [com.paperscrape.livewallpaper.engine.PaperWallpaperService] on every fix it takes, and a fix
 * moves by a few metres even on a device sitting still. Without a policy, walking into the weather
 * screen twice is two lookups for the same street.
 *
 * ### The threshold, and why it is 1 km
 *
 * Not a round number picked for looking reasonable. Two things fix it:
 *
 *  - **Network mode is the case that needs the cache most.** "Cell towers and Wi-Fi" is documented
 *    in the settings screen as *"approximate — enough to know your town"*, and a stationary device
 *    on that provider can report positions hundreds of metres apart between consecutive fixes as
 *    it re-associates. A threshold below that jitter would defeat the cache in exactly the mode
 *    that generates the most redundant lookups.
 *  - **It matches what the row actually displays.** The coordinates on this row are written by
 *    [Coordinates.formatCoarse], two decimals, which is about 1 km. Tying the cache to the same
 *    resolution means the cache can never hide a change the row would have shown — if the
 *    displayed number moves, the name is looked up again.
 *
 * The cost of the threshold is a stale name for someone who crosses a municipal boundary without
 * moving a kilometre. For a label under a settings toggle that is the right trade, and it is
 * bounded: the next fix past the threshold corrects it.
 *
 * ### Why successes never expire, and failures do
 *
 * The name of a place at a fixed coordinate does not change, so there is nothing for a
 * time-to-live to protect against — and this cache is in-memory only, so it is at most one process
 * old regardless. A *failure* is the opposite: offline, no geocoder service, a momentary timeout.
 * Those are all transient, so a failure is remembered only long enough to stop an offline device
 * retrying on every recomposition ([RETRY_AFTER_FAILURE_MILLIS]), and is never stored as a label.
 *
 * **Nothing here polls.** Every lookup is caused by a fix arriving or by the user opening the
 * screen; this class only ever *suppresses* work.
 */
class LocalityLabelCache(
    private val significantMoveMetres: Double = SIGNIFICANT_MOVE_METRES,
    private val retryAfterFailureMillis: Long = RETRY_AFTER_FAILURE_MILLIS,
) {

    private data class Entry(val latitude: Double, val longitude: Double, val label: String)

    private data class Failure(val latitude: Double, val longitude: Double, val atMillis: Long)

    private val lock = Any()
    private var entry: Entry? = null
    private var failure: Failure? = null

    /**
     * Monotonic request counter.
     *
     * A lookup is slow and a fix can change while one is in flight, so the newest request is
     * tracked and a result is only stored if it is still the newest. Without this, a lookup for
     * the place the device *was* can land after the lookup for where it *is* and leave the cache
     * naming the wrong town — the classic last-writer-wins bug, and the one that survives
     * `LaunchedEffect` cancellation because the cache outlives the composition.
     */
    private var newestRequest = 0L

    /** The label already known for this position, or null if a lookup is needed. */
    fun cachedLabel(latitude: Double, longitude: Double): String? = synchronized(lock) {
        entry?.takeIf { isNear(it.latitude, it.longitude, latitude, longitude) }?.label
    }

    /** Forgets everything. Only for tests and for a deliberate reset. */
    fun clear() = synchronized(lock) {
        entry = null
        failure = null
    }

    /**
     * The place name for a fix, looked up through [lookup] only when the policy above says one is
     * needed.
     *
     * Returns null when the name is not available — the caller shows the coordinates, which it has
     * regardless. **A geocoder failure is never an error about the position:** the position is
     * already known, and nothing here can invalidate it.
     *
     * [lookup] is passed in rather than called directly so that the policy can be tested without
     * Android, a network or a `Geocoder`.
     */
    suspend fun labelFor(
        latitude: Double,
        longitude: Double,
        nowMillis: Long,
        lookup: suspend (Double, Double) -> String?,
    ): String? {
        val request = synchronized(lock) {
            entry?.let { if (isNear(it.latitude, it.longitude, latitude, longitude)) return it.label }
            failure?.let {
                // Still inside the back-off for this same position: do not ask again yet.
                if (isNear(it.latitude, it.longitude, latitude, longitude) &&
                    nowMillis - it.atMillis < retryAfterFailureMillis
                ) {
                    return null
                }
            }
            ++newestRequest
        }

        val label = lookup(latitude, longitude)

        synchronized(lock) {
            // A newer position has been asked for since this lookup started; its answer is the one
            // that belongs in the cache, so this one is returned to its own caller and dropped.
            if (request != newestRequest) return label
            if (label != null) {
                entry = Entry(latitude, longitude, label)
                failure = null
            } else {
                failure = Failure(latitude, longitude, nowMillis)
            }
        }
        return label
    }

    private fun isNear(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Boolean =
        distanceMetres(aLat, aLon, bLat, bLon) < significantMoveMetres

    companion object {

        /** See the class comment: Network-mode jitter, and the row's own 2-decimal resolution. */
        const val SIGNIFICANT_MOVE_METRES = 1_000.0

        /** Long enough to stop an offline screen retrying on every recomposition, short enough
         * that coming back online is noticed the next time the row is looked at. */
        const val RETRY_AFTER_FAILURE_MILLIS = 60_000L

        /** The one instance the settings row uses, so the answer survives leaving the screen. */
        val shared = LocalityLabelCache()

        private const val EARTH_RADIUS_METRES = 6_371_000.0

        /**
         * Great-circle distance, haversine.
         *
         * Plain Euclidean distance on latitude/longitude would be wrong by the cosine of the
         * latitude — at 60°N a degree of longitude is half the distance it is at the equator — so a
         * threshold expressed in metres would silently mean different things in Nairobi and in
         * Reykjavík. `Location.distanceBetween` would do this too, but it is Android, and keeping
         * it here is what lets the policy be a JVM test.
         */
        fun distanceMetres(
            latitude1: Double,
            longitude1: Double,
            latitude2: Double,
            longitude2: Double,
        ): Double {
            val lat1 = Math.toRadians(latitude1)
            val lat2 = Math.toRadians(latitude2)
            val deltaLat = lat2 - lat1
            val deltaLon = Math.toRadians(longitude2 - longitude1)
            val a = sin(deltaLat / 2).let { it * it } +
                cos(lat1) * cos(lat2) * sin(deltaLon / 2).let { it * it }
            // `min(1.0, ...)` guards the rounding case where `a` creeps just above 1 for
            // antipodal points and `asin` would return NaN.
            return 2 * EARTH_RADIUS_METRES * asin(min(1.0, sqrt(a)))
        }
    }
}
