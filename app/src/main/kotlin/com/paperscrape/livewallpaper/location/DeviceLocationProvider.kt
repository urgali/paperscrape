package com.paperscrape.livewallpaper.location

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * A raw lat/long fix, independent of what it's used for. Previously [com.paperscrape.livewallpaper.
 * engine.PaperWallpaperService] only ever turned a device fix directly into sunrise/sunset hours
 * inline, with no reusable coordinate available anywhere else -- fine when sunrise/sunset was the
 * only consumer, but Live Weather (Phase 1d point 6) needs the exact same coordinates to query a
 * forecast for the user's actual area. Rather than duplicate the LocationManager/permission-check
 * wiring a second time in a weather-fetch path, this class is the single shared source of "where
 * is the device right now" that both consumers read from.
 */
data class DeviceLocationFix(val latitude: Double, val longitude: Double)

/**
 * Wraps [LocationManager] with the exact permission-check/provider-selection behavior the
 * sunrise/sunset feature already had (network provider preferred over GPS -- cheaper battery-wise
 * and plenty precise for both a sunrise/sunset calculation and a weather API call, neither of
 * which need GPS-grade accuracy), just extracted into its own reusable class instead of being
 * inlined in the wallpaper engine. No behavior change for the existing sunrise/sunset feature --
 * same provider selection, same 10-minute update interval, same 1km distance filter.
 *
 * Callers own their own [android.os.Handler]/looper for [start] -- this class is Android
 * component-agnostic (doesn't extend Service/Activity) so both [com.paperscrape.livewallpaper.
 * engine.PaperWallpaperService] (sunrise/sunset) and a future weather-fetch path (a WorkManager
 * job or the same wallpaper engine, TBD when point 6 is implemented) can each own an instance.
 */
class DeviceLocationProvider(private val context: Context) {

    private var locationManager: LocationManager? = null
    private var listener: LocationListener? = null

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    /** Fires [onFix] once immediately with the last known fix if one's cached (may not fire at
     * all if none is cached yet), then again every ~10 minutes / 1km of movement. No-ops silently
     * if permission isn't granted or no provider is enabled -- callers should check
     * [hasPermission] themselves before showing location-dependent UI as "on". */
    fun start(onFix: (DeviceLocationFix) -> Unit) {
        if (!hasPermission()) return
        try {
            val lm = (locationManager ?: context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
                .also { locationManager = it }
            val provider = when {
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                else -> return
            }
            val newListener = LocationListener { location: Location -> onFix(location.toFix()) }
            listener = newListener
            lm.getLastKnownLocation(provider)?.let { onFix(it.toFix()) }
            lm.requestLocationUpdates(provider, TEN_MINUTES_MS, 1000f, newListener, Looper.getMainLooper())
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call; caller keeps whatever fix (or
            // lack of one) it already had.
        }
    }

    fun stop() {
        try {
            listener?.let { locationManager?.removeUpdates(it) }
        } catch (_: SecurityException) {
            // no-op
        }
        listener = null
    }

    private fun Location.toFix() = DeviceLocationFix(latitude, longitude)

    private companion object {
        const val TEN_MINUTES_MS = 10 * 60 * 1000L
    }
}
