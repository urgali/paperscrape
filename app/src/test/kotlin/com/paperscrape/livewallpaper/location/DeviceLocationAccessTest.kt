package com.paperscrape.livewallpaper.location

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the phone lets PaperScrape do with its position, read from the phone (v5.10D, row 5 of the
 * v5.10A table, inventory I-206), over every combination of the two permissions and the phone's
 * location switch, for both kinds.
 *
 * **Evidence about the rule, not about a phone.** The project's phone is Android 10, and taking a
 * permission away from it, switching its location off or answering "Approximate" on an Android 12
 * dialog are changes to a phone's settings that these rounds do not make; the settings screen and the
 * wallpaper read these three facts with `ContextCompat.checkSelfPermission` and
 * `LocationManagerCompat.isLocationEnabled` (`DeviceLocationProvider.access`).
 */
class DeviceLocationAccessTest {

    private fun of(kind: DeviceLocationKind, fine: Boolean, coarse: Boolean, on: Boolean) =
        DeviceLocationAccess.of(kind, fine, coarse, on)

    @Test
    fun `GPS works only with the precise location and the phone's location on`() {
        assertEquals(DeviceLocationAccess.ALLOWED, of(DeviceLocationKind.GPS, fine = true, coarse = true, on = true))
        assertEquals(DeviceLocationAccess.LOCATION_OFF, of(DeviceLocationKind.GPS, fine = true, coarse = true, on = false))
        // Android 12+: "Approximate" answered, or the precise location taken away in the phone's settings.
        assertEquals(DeviceLocationAccess.APPROXIMATE_ONLY, of(DeviceLocationKind.GPS, fine = false, coarse = true, on = true))
        assertEquals("the permission is said before the switch", DeviceLocationAccess.APPROXIMATE_ONLY, of(DeviceLocationKind.GPS, fine = false, coarse = true, on = false))
        for (on in listOf(true, false)) {
            assertEquals(DeviceLocationAccess.NOT_ALLOWED, of(DeviceLocationKind.GPS, fine = false, coarse = false, on = on))
        }
    }

    @Test
    fun `Network works with either permission and the phone's location on`() {
        for (fine in listOf(true, false)) for (coarse in listOf(true, false)) for (on in listOf(true, false)) {
            val expected = when {
                !fine && !coarse -> DeviceLocationAccess.NOT_ALLOWED
                on -> DeviceLocationAccess.ALLOWED
                else -> DeviceLocationAccess.LOCATION_OFF
            }
            assertEquals("fine=$fine coarse=$coarse on=$on", expected, of(DeviceLocationKind.NETWORK, fine, coarse, on))
        }
    }

    @Test
    fun `no permission means no position at all, the location switch off means the last one`() {
        assertTrue(DeviceLocationAccess.ALLOWED.mayUsePosition)
        assertTrue("the phone not giving positions right now, like no signal", DeviceLocationAccess.LOCATION_OFF.mayUsePosition)
        assertFalse("the user said no to PaperScrape knowing where the phone is", DeviceLocationAccess.NOT_ALLOWED.mayUsePosition)
        assertFalse("GPS needs the precise one the user did not give", DeviceLocationAccess.APPROXIMATE_ONLY.mayUsePosition)
    }

    @Test
    fun `GPS asks for the precise and the approximate location in one request`() {
        // From Android 12, for an app targeting 31+, a request for the precise location alone is
        // ignored by the system and grants neither: until v5.10D GPS asked for that alone, and could
        // not be chosen there. Both together is also what Android 10 shows as one dialog.
        assertEquals(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            DeviceLocationAccess.permissionsToRequest(DeviceLocationKind.GPS),
        )
        assertEquals(listOf(Manifest.permission.ACCESS_COARSE_LOCATION), DeviceLocationAccess.permissionsToRequest(DeviceLocationKind.NETWORK))
    }

    @Test
    fun `whatever a request grants, the kind it was made for is then allowed or says why not`() {
        // Every answer a GPS request can get, on Android 10 (precise or nothing) and 12+ (precise,
        // approximate, nothing), with the location on: the access after it names what is missing.
        val answers = mapOf(
            Pair(true, true) to DeviceLocationAccess.ALLOWED,
            Pair(false, true) to DeviceLocationAccess.APPROXIMATE_ONLY,
            Pair(false, false) to DeviceLocationAccess.NOT_ALLOWED,
        )
        for ((granted, expected) in answers) {
            assertEquals("$granted", expected, of(DeviceLocationKind.GPS, granted.first, granted.second, on = true))
        }
    }

    @Test
    fun `the provider reads the phone's three facts, not the stored choice`() {
        val provider = source("location/DeviceLocationProvider.kt")
        val access = provider.substring(provider.indexOf("fun access(kind: DeviceLocationKind)"))
        assertTrue(access.contains("Manifest.permission.ACCESS_FINE_LOCATION"))
        assertTrue(access.contains("Manifest.permission.ACCESS_COARSE_LOCATION"))
        assertTrue(access.contains("LocationManagerCompat.isLocationEnabled(manager)"))
        // Since v5.10E through the provider's own plan, which `LocationRetryLadderTest` runs.
        assertTrue("and the request is the throttle's to allow", provider.contains("throttle.tryAcquire(force) -> FixStep.REQUEST"))
        val activity = source("ui/SettingsActivity.kt")
        assertTrue("the settings screen asks for every permission the kind needs", activity.contains("DeviceLocationAccess.permissionsToRequest(kind).toTypedArray()"))
        assertTrue(activity.contains("ActivityResultContracts.RequestMultiplePermissions()"))
    }

    private fun source(path: String): String {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/$path"
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = java.io.File(dir, "$prefix$suffix")
                if (f.isFile) return f.readText()
            }
            dir = dir.parentFile
        }
        error("source not found: $suffix")
    }
}
