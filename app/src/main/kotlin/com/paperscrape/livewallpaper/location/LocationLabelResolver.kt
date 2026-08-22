package com.paperscrape.livewallpaper.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

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

    /**
     * How long to wait for a name before giving up and letting the caller show coordinates.
     *
     * Generous rather than tight: a reverse lookup goes to the network on most devices and a
     * momentary slow answer is not worth losing a place name over. What matters is that the number
     * is finite — before v3.2 there was none.
     */
    private const val LOOKUP_TIMEOUT_MILLIS = 6_000L

    /** Returns null if geocoding fails, times out, or no result comes back (offline, no geocoder
     * service available on this device, coordinates over open ocean, etc.) -- callers should fall
     * back to showing the raw lat/long themselves in that case, never leave a permanent spinner. */
    suspend fun resolveCityLabel(context: Context, latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        val address = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                awaitOnceOrNull<Address>(LOOKUP_TIMEOUT_MILLIS) { complete ->
                    // **The full listener, not a lambda.** `GeocodeListener` declares `onGeocode`
                    // *and* `onError`; a SAM-converted lambda implements only the first, so every
                    // error the platform reported used to land on a default `onError` that does
                    // nothing -- and the coroutine waited for a call that would never come. See
                    // [awaitOnceOrNull], which is where the guarantee that this always finishes
                    // actually lives.
                    geocoder.getFromLocation(
                        latitude, longitude, 1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) =
                                complete(addresses.firstOrNull())

                            override fun onError(errorMessage: String?) = complete(null)
                        },
                    )
                }
            } else {
                // The pre-API-33 overload is synchronous and documented as able to block on
                // network I/O, and this is called from a `LaunchedEffect`, i.e. on the main
                // thread. Moving it to IO is what makes the timeout mean anything at all:
                // `withTimeoutOrNull` can only give up at a suspension point, so wrapping a
                // blocking call running on the caller's own thread would bound nothing.
                //
                // The blocking call is not interrupted when the timeout fires -- there is no API
                // to do that -- so an IO thread may stay busy until the platform gives up. What
                // ends on time is the waiting, which is the part the user can see.
                withTimeoutOrNull(LOOKUP_TIMEOUT_MILLIS) {
                    withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            // Never swallowed. The screen going away while a lookup is in flight has to cancel
            // this coroutine, and a `catch (Exception)` that eats CancellationException would
            // keep the caller's scope alive waiting for a value nobody wants any more.
            throw cancellation
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
