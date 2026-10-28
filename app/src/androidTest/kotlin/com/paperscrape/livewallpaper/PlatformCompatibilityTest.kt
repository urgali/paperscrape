package com.paperscrape.livewallpaper

import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.location.DeviceLocationProvider
import com.paperscrape.livewallpaper.update.ApkInstaller
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **What Android 8, 9 and 10 do differently from the Android the app is compiled against**, on the phone. Neither test
 * needs the network, the location switched on or a setting touched.
 *
 * - **The location request's listener answers every call of the old interface.** Below API 30 a request is a
 *   `LocationListener` registered with `requestLocationUpdates` (`DeviceLocationProvider.oneFixListener`). A lambda of
 *   the framework's interface implements `onLocationChanged` alone, and on Android 8-10 -- whose `onProviderEnabled`,
 *   `onProviderDisabled` and `onStatusChanged` have no body -- it throws `AbstractMethodError` on the main thread when
 *   the location is switched off during the request: the wallpaper's process ends, and twice in ten seconds puts the
 *   system's wallpaper back. Such a lambda throws for all three on the BV6600, Android 10.
 * - **An APK signed like the installed app is signed by this app.** On Android 9 and 10 `getPackageArchiveInfo` reads
 *   a file's certificates only for `GET_SIGNATURES`; asked with `GET_SIGNING_CERTIFICATES` alone, `signedByThisApp`
 *   would be null for every file, and an update downloaded from the app refused as "not signed by whoever built the
 *   copy on this phone". The app's own installed APK is a file signed by the installed copy.
 */
@RunWith(AndroidJUnit4::class)
class PlatformCompatibilityTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theRequestListenerAnswersEveryCallOfAndroid8To10() {
        val fixes = mutableListOf<Location>()
        val listener = DeviceLocationProvider.oneFixListener { fixes += it }
        val threw = mutableListOf<String>()
        for ((name, call) in listOf<Pair<String, () -> Unit>>(
            "onProviderDisabled" to { listener.onProviderDisabled(LocationManager.GPS_PROVIDER) },
            "onProviderEnabled" to { listener.onProviderEnabled(LocationManager.GPS_PROVIDER) },
            "onStatusChanged" to { @Suppress("DEPRECATION") listener.onStatusChanged(LocationManager.GPS_PROVIDER, 0, null) },
        )) {
            try {
                call()
            } catch (t: Throwable) {
                threw += "$name threw ${t.javaClass.name}: ${t.message}"
            }
        }
        assertEquals("API ${Build.VERSION.SDK_INT}", emptyList<String>(), threw)
        val fix = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 43.77925
            longitude = 11.24626
        }
        listener.onLocationChanged(fix)
        assertEquals("and the position still reaches the request", listOf(fix), fixes)
    }

    @Test
    fun anApkSignedLikeThisAppIsSignedByThisApp() {
        val own = File(context.applicationInfo.sourceDir)
        assertEquals(
            "API ${Build.VERSION.SDK_INT}: this app's own installed APK (${own.length()} B)",
            true,
            ApkInstaller.signedByThisApp(context, own),
        )
    }
}
