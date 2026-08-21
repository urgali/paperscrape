package com.paperscrape.livewallpaper.location

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
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** A raw lat/long fix, independent of what it is used for. */
data class DeviceLocationFix(val latitude: Double, val longitude: Double)

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
 */
class DeviceLocationProvider(private val context: Context) {

    /** Whether the permission [kind] needs has been granted. */
    fun hasPermission(kind: DeviceLocationKind): Boolean =
        ContextCompat.checkSelfPermission(context, kind.permission) == PackageManager.PERMISSION_GRANTED

    /**
     * One position from [kind], or `null` if it cannot be had right now.
     *
     * `null` is an ordinary answer, not an error: the permission may be refused, the provider may
     * be switched off, the device may be somewhere with no signal. Callers are expected to have a
     * previously saved position to fall back on -- which is why this never guesses with a
     * different provider.
     *
     * A cached fix younger than [maxAgeMillis] short-circuits the whole thing. Beyond that, one
     * current-location request is made and abandoned after [timeoutMillis]; if it comes back
     * empty, a stale cached fix is still better than nothing and is returned rather than discarded.
     */
    suspend fun currentFix(
        kind: DeviceLocationKind,
        maxAgeMillis: Long = FRESH_ENOUGH_MS,
        timeoutMillis: Long = REQUEST_TIMEOUT_MS,
    ): DeviceLocationFix? {
        if (!hasPermission(kind)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        val cached = lastKnown(manager, kind)
        if (cached != null && cached.ageMillis() <= maxAgeMillis) return cached.toFix()

        // Asking a disabled provider can only wait out the timeout, so do not.
        val live = if (isEnabled(manager, kind)) requestOnce(manager, kind, timeoutMillis) else null

        // A stale cached fix beats no fix: a town does not move, and the forecast for where the
        // device was an hour ago is a far better scene than the default one.
        return live ?: cached?.toFix()
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

    companion object {

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
