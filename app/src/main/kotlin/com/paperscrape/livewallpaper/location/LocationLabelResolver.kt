package com.paperscrape.livewallpaper.location

import android.content.Context
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Turns a lat/long into a short human-readable label ("Florence", "Florence, Italy" if the city
 * name alone is ambiguous-sounding, etc.) for display in Settings next to the two location
 * toggles -- aa's own request was explicit: both the GPS toggle and the custom-location toggle
 * need to visibly confirm *which* place they actually resolved to, not just show raw coordinates.
 *
 * [Geocoder.getFromLocation] got a new listener-based overload in API 33 (the old synchronous one
 * is now deprecated, and was always technically allowed to block on network I/O even before that)
 * -- this wraps both paths behind one suspend function so callers never need to branch on SDK
 * version themselves.
 */
object LocationLabelResolver {

    /** Returns null if geocoding fails, times out, or no result comes back (offline, no geocoder
     * service available on this device, coordinates over open ocean, etc.) -- callers should fall
     * back to showing the raw lat/long themselves in that case, never leave a permanent spinner. */
    suspend fun resolveCityLabel(context: Context, latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        val address = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(latitude, longitude, 1) { results ->
                        if (continuation.isActive) continuation.resume(results.firstOrNull())
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
            }
        } catch (_: Exception) {
            null
        } ?: return null

        val city = address.locality ?: address.subAdminArea ?: address.adminArea
        val country = address.countryName
        return when {
            city != null && country != null -> "$city, $country"
            city != null -> city
            country != null -> country
            else -> null
        }
    }
}
