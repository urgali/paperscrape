package com.paperscrape.livewallpaper.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** A raw lat/long fix, independent of what it is used for. */
data class DeviceLocationFix(val latitude: Double, val longitude: Double)

/**
 * [DeviceLocationProvider.currentFix]'s answer (v5.10D): the position, if any, and whether the phone was
 * really consulted for it -- a real request made, or a cached position young enough to stand for one.
 * Only that answers a choice the user has just made; a request the throttle or a switched-off location did
 * not let through leaves the choice waiting.
 */
data class DeviceFixAnswer(val fix: DeviceLocationFix?, val consulted: Boolean)

/**
 * One position, asked for when something actually needs it.
 *
 * **This used to be a subscription and is now a question.** The old shape called
 * `requestLocationUpdates` with a ten-minute interval and left it running for as long as the
 * wallpaper lived, which meant the positioning stack was woken every ten minutes forever to serve
 * a forecast that is refreshed once an hour and a sunrise time that moves by about a minute a day.
 * It also picked its provider by availability -- network if enabled, otherwise GPS -- so the cheap
 * setting could quietly power the GNSS receiver.
 *
 * What replaces it:
 *
 *  - **One fix per request, then nothing.** [currentFix] returns a position and leaves no listener
 *    behind. There is no continuous tracking to stop, and nothing to leak if the engine dies.
 *  - **A cached fix is preferred to a new one.** If the system already knows where the device is
 *    and that answer is younger than `maxAgeMillis`, that is the answer -- no radio, no GNSS, no
 *    wakeup. Only a stale or missing cache costs anything.
 *  - **The kind is obeyed, never substituted.** [DeviceLocationKind.NETWORK] asks the network
 *    provider and nothing else; if it cannot answer, the result is "no fix", and the caller falls
 *    back to the position it saved last time.
 *  - **A request always ends.** Every path is bounded by `timeoutMillis`, so a provider that never
 *    calls back costs one pending continuation and no more.
 *  - **At most one request an hour while the phone answers** (v5.10D): [LocationRequestThrottle.shared]
 *    decides, for every engine of the process. Until then a weather outage, or a position that never
 *    arrived, made the loop ask every few minutes. **A request that finds nothing is tried again after
 *    5, 15 and 30 minutes, then the hour** (v5.10E, the maintainer's decision of 2026-10-04): it is told
 *    how each request ended ([LocationRequestThrottle.requestEnded]).
 */
class DeviceLocationProvider(private val context: Context) {

    /**
     * Whether a permission that serves [kind] has been granted: the precise location for GPS, either
     * for Network ([DeviceLocationAccess.of]'s rule, v5.10D: the network provider answers to the
     * precise permission too, and the screen calls that working).
     */
    fun hasPermission(kind: DeviceLocationKind): Boolean {
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        return when (kind) {
            DeviceLocationKind.GPS -> granted(Manifest.permission.ACCESS_FINE_LOCATION)
            DeviceLocationKind.NETWORK ->
                granted(Manifest.permission.ACCESS_COARSE_LOCATION) || granted(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    /**
     * What the phone allows for [kind] right now: the two permissions and the phone's location switch
     * ([DeviceLocationAccess], v5.10D). Cheap -- two permission checks and one system call, no radio --
     * so the wallpaper reads it on every pass and the settings screen every time it comes back.
     */
    fun access(kind: DeviceLocationKind): DeviceLocationAccess {
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val locationOn = manager != null &&
            runCatching { LocationManagerCompat.isLocationEnabled(manager) }.getOrDefault(false)
        return DeviceLocationAccess.of(
            kind = kind,
            fineGranted = granted(Manifest.permission.ACCESS_FINE_LOCATION),
            coarseGranted = granted(Manifest.permission.ACCESS_COARSE_LOCATION),
            locationOn = locationOn,
        )
    }

    /**
     * One position from [kind], or `null` if it cannot be had right now.
     *
     * `null` is an ordinary answer, not an error: the permission may be refused, the provider may
     * be switched off, the device may be somewhere with no signal. Callers are expected to have a
     * previously saved position to fall back on -- which is why this never guesses with a
     * different provider.
     *
     * A cached fix younger than [maxAgeMillis] short-circuits the whole thing. Beyond that, one
     * current-location request is made and abandoned after [timeoutMillis] -- **only if [throttle]
     * allows it** (v5.10D): an hour after a request that brought a position; 5, 15 and 30 minutes after
     * the first, second and third in a row that did not, then the hour, and round again (v5.10E);
     * whoever asks, unless [forceRequest] says the user has just chosen this source. If no request is made or it comes back empty, a stale cached fix is still better than
     * nothing and is returned rather than discarded.
     *
     * **What the throttle is told** ([plan]): how a request it let through ended, and a fresh cached fix
     * as a position arrived. Nothing about the two cases where the phone is not asked at all -- no
     * permission, the provider (the phone's location) off: those are not failed tries, and they leave
     * the wait where it was (the maintainer, 2026-10-04: the settings screen says what to do there).
     */
    suspend fun currentFix(
        kind: DeviceLocationKind,
        forceRequest: Boolean = false,
        maxAgeMillis: Long = FRESH_ENOUGH_MS,
        timeoutMillis: Long = REQUEST_TIMEOUT_MS,
        throttle: LocationRequestThrottle = LocationRequestThrottle.shared,
    ): DeviceFixAnswer {
        val permitted = hasPermission(kind)
        val manager = if (permitted) context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager else null
        val cached = manager?.let { lastKnown(it, kind) }
        val step = plan(
            permitted = manager != null,
            cachedAgeMillis = cached?.ageMillis(),
            maxAgeMillis = maxAgeMillis,
            // Asking a disabled provider can only wait out the timeout, so it is not asked.
            providerOn = manager != null && isEnabled(manager, kind),
            throttle = throttle,
            force = forceRequest,
        )
        return when (step) {
            FixStep.NOT_PERMITTED -> DeviceFixAnswer(null, consulted = false)
            FixStep.FRESH_CACHE -> DeviceFixAnswer(cached?.toFix(), consulted = true)
            // A stale cached fix beats no fix: a town does not move, and the forecast for where the
            // device was an hour ago is a far better scene than the default one.
            FixStep.PROVIDER_OFF, FixStep.WAIT -> DeviceFixAnswer(cached?.toFix(), consulted = false)
            FixStep.REQUEST -> {
                // Counted however it ends, a cancellation included (`LocationRequestThrottle.counted`).
                val live = throttle.counted { manager?.let { requestOnce(it, kind, timeoutMillis) } }
                DeviceFixAnswer(live ?: cached?.toFix(), consulted = true)
            }
        }
    }

    private fun isEnabled(manager: LocationManager, kind: DeviceLocationKind): Boolean = try {
        manager.isProviderEnabled(kind.providerName)
    } catch (_: Exception) {
        // A device without that provider at all throws rather than answering false.
        false
    }

    private fun lastKnown(manager: LocationManager, kind: DeviceLocationKind): Location? = try {
        manager.getLastKnownLocation(kind.providerName)
    } catch (_: SecurityException) {
        null
    } catch (_: Exception) {
        null
    }

    /**
     * A single position, by whichever API this Android version offers.
     *
     * API 30 gave [LocationManager.getCurrentLocation] exactly this shape -- one fix, a
     * cancellation signal, done. Below that (the project's `minSdk` is 26) the same thing has to be
     * built out of an update subscription that removes itself, which is why the listener is
     * unregistered from three places: the fix, the timeout, and cancellation.
     */
    private suspend fun requestOnce(
        manager: LocationManager,
        kind: DeviceLocationKind,
        timeoutMillis: Long,
    ): DeviceLocationFix? = suspendCancellableCoroutine { continuation ->
        val handler = Handler(Looper.getMainLooper())
        val settled = AtomicBoolean(false)
        fun settle(fix: DeviceLocationFix?) {
            if (settled.compareAndSet(false, true) && continuation.isActive) continuation.resume(fix)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                val executor = Executor { it.run() }
                manager.getCurrentLocation(kind.providerName, signal, executor) { location ->
                    settle(location?.toFix())
                }
                handler.postDelayed({
                    signal.cancel()
                    settle(null)
                }, timeoutMillis)
                continuation.invokeOnCancellation { signal.cancel() }
            } else {
                lateinit var listener: LocationListener
                listener = LocationListener { location ->
                    manager.safeRemove(listener)
                    settle(location.toFix())
                }
                manager.requestLocationUpdates(kind.providerName, 0L, 0f, listener, Looper.getMainLooper())
                handler.postDelayed({
                    manager.safeRemove(listener)
                    settle(null)
                }, timeoutMillis)
                continuation.invokeOnCancellation { manager.safeRemove(listener) }
            }
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
            settle(null)
        } catch (_: Exception) {
            settle(null)
        }
    }

    private fun LocationManager.safeRemove(listener: LocationListener) {
        try {
            removeUpdates(listener)
        } catch (_: Exception) {
            // Already removed, or permission gone. Either way there is nothing left to do.
        }
    }

    private fun Location.toFix() = DeviceLocationFix(latitude, longitude)

    private fun Location.ageMillis(): Long = System.currentTimeMillis() - time

    /** What [currentFix] does, in the order it decides it ([plan]). */
    internal enum class FixStep {
        /** The permission [currentFix]'s kind needs is not held: the phone is not asked. */
        NOT_PERMITTED,

        /** The system's own position is young enough to stand for a request. */
        FRESH_CACHE,

        /** The provider -- the phone's location -- is off: asking could only wait out the timeout. */
        PROVIDER_OFF,

        /** A real request would be too soon ([LocationRequestThrottle]): the last fix stands in. */
        WAIT,

        /** A real request, now. */
        REQUEST,
    }

    companion object {

        /**
         * [currentFix]'s decision, without the phone (v5.10E): the permission, the system's cached
         * position, the provider, then the throttle -- and what the throttle is told on the way. A fresh
         * cached position is a position arrived ([LocationRequestThrottle.positionArrived]); a request is
         * counted from now ([LocationRequestThrottle.tryAcquire]), and [currentFix] tells it how the
         * request ended. **No permission and a provider switched off touch nothing**: they are not tries
         * (the maintainer's rule of 2026-10-04), and `LocationRetryLadderTest` holds that here.
         */
        internal fun plan(
            permitted: Boolean,
            cachedAgeMillis: Long?,
            maxAgeMillis: Long,
            providerOn: Boolean,
            throttle: LocationRequestThrottle,
            force: Boolean,
        ): FixStep = when {
            !permitted -> FixStep.NOT_PERMITTED
            cachedAgeMillis != null && cachedAgeMillis <= maxAgeMillis -> {
                throttle.positionArrived()
                FixStep.FRESH_CACHE
            }
            !providerOn -> FixStep.PROVIDER_OFF
            throttle.tryAcquire(force) -> FixStep.REQUEST
            else -> FixStep.WAIT
        }

        /**
         * How old a cached fix may be and still be used without asking for a new one.
         *
         * Fifteen minutes. The consumer is an hourly weather refresh and a sunrise time, and a
         * device that has moved far enough to change either in fifteen minutes has almost
         * certainly produced a newer cached fix for some other app anyway.
         */
        const val FRESH_ENOUGH_MS = 15 * 60 * 1000L

        /** How long one request may wait before the caller falls back to what it already had. */
        const val REQUEST_TIMEOUT_MS = 20_000L
    }
}
