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
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** A raw lat/long fix, independent of what it is used for. */
data class DeviceLocationFix(val latitude: Double, val longitude: Double)

/**
 * [DeviceLocationProvider.currentFix]'s answer: the position, if any, and whether the phone was
 * really consulted for it -- a real request made, or a cached position young enough to stand for one.
 * Only that answers a choice the user has just made; a request the throttle or a switched-off location did
 * not let through leaves the choice waiting.
 *
 * [ageMillis] is how long before the answer the phone took [fix] (0 without one): a cached position keeps its true
 * age, stale or not, so what is saved from it says when the phone was there, not when it was asked
 * (`WallpaperPrefs.setResolvedGpsLocation`). Negative only for a cached position "from the future" (a clock moved
 * back): the saving takes that as just taken.
 */
data class DeviceFixAnswer(val fix: DeviceLocationFix?, val consulted: Boolean, val ageMillis: Long = 0L)

/** A position the phone already holds and how old it is, in ms ([DeviceLocationProvider.heldFix]). */
data class HeldFix(val fix: DeviceLocationFix, val ageMillis: Long)

/**
 * One position, asked for when something actually needs it.
 *
 * **A question, not a subscription.** A `requestLocationUpdates` with a ten-minute interval left
 * running for as long as the wallpaper lives would wake the positioning stack every ten minutes
 * forever, to serve a forecast that is refreshed once an hour and a sunrise time that moves by about
 * a minute a day. A provider picked by availability -- network if enabled, otherwise GPS -- would let
 * the cheap setting quietly power the GNSS receiver.
 *
 * So:
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
 *  - **At most one request an hour while the phone answers**: [LocationRequestThrottle.shared]
 *    decides, for every engine of the process, so a weather outage, or a position that never arrives,
 *    does not make the loop ask every few minutes. **A request that finds nothing is tried again after
 *    5, 15 and 30 minutes, then the hour** (`DESIGN_NOTES.md` §12, decision 41): it is told
 *    how each request ended ([LocationRequestThrottle.requestEnded]).
 *  - **One search at a time in the process**: the wallpaper's engines and the settings page's
 *    *Update location now* ask through [LocationRequestLock].
 */
class DeviceLocationProvider(private val context: Context) {

    /**
     * Whether a permission that serves [kind] has been granted: the precise location for GPS, either
     * for Network ([DeviceLocationAccess.of]'s rule: the network provider answers to the
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
     * ([DeviceLocationAccess]). Cheap -- two permission checks and one system call, no radio --
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
     * allows it**: an hour after a request that brought a position; 5, 15 and 30 minutes after
     * the first, second and third in a row that did not, then the hour, and round again;
     * whoever asks, unless [forceRequest] says the user has just chosen this source or tapped *Update location now*
     * ([positionNow]). If no request is made or it comes back empty, a stale cached fix is still better than
     * nothing and is returned rather than discarded, with its age ([DeviceFixAnswer.ageMillis]).
     *
     * **What the throttle is told** ([plan]): how a request it let through ended, and a fresh cached fix
     * as a position arrived. Nothing about the two cases where the phone is not asked at all -- no
     * permission, the provider (the phone's location) off: those are not failed tries, and they leave
     * the wait where it was: the settings screen says what to do there (`DESIGN_NOTES.md` §12, decision 41).
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
        // Read once: the plan and the answer speak of the same age.
        val cachedAgeMillis = cached?.ageMillis()
        val step = plan(
            permitted = manager != null,
            cachedAgeMillis = cachedAgeMillis,
            maxAgeMillis = maxAgeMillis,
            // Asking a disabled provider can only wait out the timeout, so it is not asked.
            providerOn = manager != null && isEnabled(manager, kind),
            throttle = throttle,
            force = forceRequest,
        )
        val cachedAge = cachedAgeMillis ?: 0L
        return when (step) {
            FixStep.NOT_PERMITTED -> DeviceFixAnswer(null, consulted = false)
            FixStep.FRESH_CACHE -> DeviceFixAnswer(cached?.toFix(), consulted = true, ageMillis = cachedAge)
            // A stale cached fix beats no fix: a town does not move, and the forecast for where the
            // device was an hour ago is a far better scene than the default one. It keeps its age.
            FixStep.PROVIDER_OFF, FixStep.WAIT -> DeviceFixAnswer(cached?.toFix(), consulted = false, ageMillis = cachedAge)
            FixStep.REQUEST -> {
                // Counted however it ends, a cancellation included (`LocationRequestThrottle.counted`).
                val live = throttle.counted { manager?.let { requestOnce(it, kind, timeoutMillis) } }
                if (live != null) {
                    DeviceFixAnswer(live.fix, consulted = true, ageMillis = live.ageMillis)
                } else {
                    DeviceFixAnswer(cached?.toFix(), consulted = true, ageMillis = cachedAge)
                }
            }
        }
    }

    /**
     * **Update location now**, the *Weather & time* page's tap (decision 111): the position the phone gives now,
     * or null when none comes within [TAP_TIMEOUT_MS].
     *
     * Asked by the page itself, which is on screen, so no limit Android puts on an app in the background applies.
     * Past [LocationRequestThrottle]'s wait, as a choice of source is (`forceRequest`), which also starts the tries
     * afresh: if this search finds nothing the next one is 5 minutes away (decision 41). A position the phone took in
     * the last [TAP_MAX_AGE_MS] stands for one, with no radio. Behind [LocationRequestLock], so it never runs beside
     * the wallpaper's own search: it waits for it, and then finds what that one brought in the system's cache. **The
     * search ends at most [TAP_TIMEOUT_MS] after the tap, the wait for the lock included** ([tapTimeoutAfterWait]; after a
     * wait past 55 s, longer than two of the engine's searches, the request still gets [TAP_MIN_TIMEOUT_MS]): it gets what
     * is left of the minute, longer than the engine's [REQUEST_TIMEOUT_MS] whenever the wait was
     * shorter than 40 s; a GPS receiver starting cold outdoors usually takes from half a minute to a minute.
     *
     * **Cancelled with the caller**: the page left during the search stops it. It is then a request made that brought
     * nothing, counted as a failed try (`LocationRequestThrottle.counted`), and nothing is returned, so nothing is saved.
     */
    suspend fun positionNow(kind: DeviceLocationKind): HeldFix? {
        val tappedAt = SystemClock.elapsedRealtime()
        val answer = LocationRequestLock.withLock {
            val timeout = tapTimeoutAfterWait(SystemClock.elapsedRealtime() - tappedAt)
            currentFix(kind, forceRequest = true, maxAgeMillis = TAP_MAX_AGE_MS, timeoutMillis = timeout)
        }
        return tapResult(answer)
    }

    /**
     * The position the phone already holds for [kind] and its age in ms, **without a request**: no radio is started, the
     * throttle is not asked and not told (`DESIGN_NOTES.md` §12, decision 101). Null without the permission or with none
     * held. What another app's request left behind is how the weather follows the phone between the hourly requests.
     */
    fun heldFix(kind: DeviceLocationKind): HeldFix? {
        if (!hasPermission(kind)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val cached = lastKnown(manager, kind) ?: return null
        return HeldFix(cached.toFix(), cached.ageMillis())
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
     *
     * The answer carries its age: `getCurrentLocation` may hand back a position the system took a few seconds before.
     * Never negative: a position this request brought is taken as just taken, whatever the clocks say.
     */
    private suspend fun requestOnce(
        manager: LocationManager,
        kind: DeviceLocationKind,
        timeoutMillis: Long,
    ): HeldFix? = suspendCancellableCoroutine { continuation ->
        val handler = Handler(Looper.getMainLooper())
        val settled = AtomicBoolean(false)
        fun settle(fix: HeldFix?) {
            if (settled.compareAndSet(false, true) && continuation.isActive) continuation.resume(fix)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                val executor = Executor { it.run() }
                manager.getCurrentLocation(kind.providerName, signal, executor) { location ->
                    settle(location?.toLiveFix())
                }
                handler.postDelayed({
                    signal.cancel()
                    settle(null)
                }, timeoutMillis)
                continuation.invokeOnCancellation { signal.cancel() }
            } else {
                lateinit var listener: LocationListener
                listener = oneFixListener { location ->
                    manager.safeRemove(listener)
                    settle(location.toLiveFix())
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

    /** A position a request has just brought, with its age, never negative ([requestOnce]). */
    private fun Location.toLiveFix() = HeldFix(toFix(), ageMillis().coerceAtLeast(0L))

    /**
     * How long ago the system took this position, on the clock that cannot move: `elapsedRealtimeNanos`, which every
     * position the system hands out carries. The wall clock's `time` is read only for a position without it; moved
     * back by hand, that clock makes a position of ten minutes ago two hours in the future ([plan]).
     */
    private fun Location.ageMillis(): Long =
        if (elapsedRealtimeNanos > 0L) (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000L
        else System.currentTimeMillis() - time

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
         * [currentFix]'s decision, without the phone: the permission, the system's cached
         * position, the provider, then the throttle -- and what the throttle is told on the way. A fresh
         * cached position is a position arrived ([LocationRequestThrottle.positionArrived]); a request is
         * counted from now ([LocationRequestThrottle.tryAcquire]), and [currentFix] tells it how the
         * request ended. **No permission and a provider switched off touch nothing**: they are not tries
         * (`DESIGN_NOTES.md` §12, decision 41), and `LocationRetryLadderTest` holds that here.
         *
         * **A negative age is not fresh**: a position "from the future" says the clock moved, not that the
         * position is new, and taken for fresh it would stand in for every request -- and reset the tries
         * after a failure -- for as long as the clock was moved back.
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
            cachedAgeMillis != null && cachedAgeMillis in 0L..maxAgeMillis -> {
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

        /**
         * How long after the tap *Update location now*'s search ends at most ([positionNow]), the wait for
         * [LocationRequestLock] included: a minute, what a GPS receiver starting cold needs.
         */
        const val TAP_TIMEOUT_MS = 60_000L

        /** The shortest request the tap makes after a long wait for the lock ([tapTimeoutAfterWait]). */
        const val TAP_MIN_TIMEOUT_MS = 5_000L

        /**
         * The request's timeout once the tap has waited [waitedMillis] for [LocationRequestLock]: what is left of
         * [TAP_TIMEOUT_MS] since the tap, so the search ends within the minute the row promises, never under
         * [TAP_MIN_TIMEOUT_MS]. The engine's searches it can wait for last [REQUEST_TIMEOUT_MS] each, so the floor is
         * reached only past 55 s of waiting. A negative wait (a clock read out of order) counts as none.
         */
        internal fun tapTimeoutAfterWait(waitedMillis: Long): Long =
            (TAP_TIMEOUT_MS - waitedMillis.coerceAtLeast(0L)).coerceAtLeast(TAP_MIN_TIMEOUT_MS)

        /** How old a position the phone holds may be and still answer *Update location now* ([positionNow]). */
        const val TAP_MAX_AGE_MS = 60_000L

        /**
         * What *Update location now* takes from [answer]: a position the phone was really consulted for -- a request
         * made, or one it took within [TAP_MAX_AGE_MS] -- with its age; null for anything else. **The stale position a
         * request that found nothing falls back to is not an answer to the tap**: the row says nothing was found, and
         * nothing is saved, so the last position stays as it was, with its own age.
         */
        internal fun tapResult(answer: DeviceFixAnswer): HeldFix? {
            val fix = answer.fix ?: return null
            if (!answer.consulted || answer.ageMillis !in 0L..TAP_MAX_AGE_MS) return null
            return HeldFix(fix, answer.ageMillis)
        }

        /**
         * The listener a request below API 30 registers, handing [onFix] the first position.
         *
         * **A `LocationListenerCompat`, never a lambda of the framework's `LocationListener`.** Compiled against
         * API 30 and later, where `onProviderEnabled`, `onProviderDisabled` and `onStatusChanged` have bodies, a
         * lambda implements `onLocationChanged` alone; Android 8, 9 and 10 declare the other three abstract, and
         * call `onProviderDisabled` on the main thread when the location is switched off while the request is open
         * -- by the user, or by the battery saver with the screen off -- which on the BV6600 (Android 10) throws
         * `AbstractMethodError` and ends the wallpaper's process. androidx.core's interface gives the three empty
         * bodies on every version. `PlatformCompatibilityTest` calls them on the phone.
         */
        internal fun oneFixListener(onFix: (Location) -> Unit): LocationListener =
            LocationListenerCompat { location -> onFix(location) }
    }
}
