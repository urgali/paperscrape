package com.paperscrape.livewallpaper.location

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **The merged manifest, as the permanent record of a decision not to add anything.**
 *
 * v4.6 asked whether a live wallpaper needs `ACCESS_BACKGROUND_LOCATION`, or a foreground service
 * of type `location`, to keep following the weather while the app's UI is closed. Measured on a
 * Pixel 9 / Android 17 emulator: **no**. While PaperScrape is the active wallpaper the system binds
 * its service and the process holds `PROCESS_CAPABILITY_FOREGROUND_LOCATION` in every state that
 * matters -- `BOUND_FOREGROUND_SERVICE` with the screen on, `IMPORTANT_FOREGROUND` with it off --
 * so a "while in use" grant is honoured throughout. A GPS fix was delivered to the app after a
 * reboot with the settings Activity never opened, and `live_weather_status` came back `ok`.
 *
 * Adding either mechanism anyway would cost the user something real: a separate "Allow all the
 * time" prompt and a Play policy declaration for the permission, or a permanent notification for
 * the service. Neither buys any capability the app does not already have.
 *
 * A decision to add nothing leaves no code behind, so it leaves nothing to regress against. This
 * class is what it leaves instead. Every assertion here is a statement the next person to touch
 * the location or lifecycle code has to deliberately overturn, in a diff that says why.
 *
 * The unit-test half is `BackgroundLocationContractTest`, which pins the behaviour -- which
 * permission each mode asks for, and how rarely a position is wanted -- rather than the manifest.
 */
@RunWith(AndroidJUnit4::class)
class BackgroundLocationManifestTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun requestedPermissions(): List<String> {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        return info.requestedPermissions?.toList() ?: emptyList()
    }

    // ------------------------------------------------------------------ what is asked for

    /** Both foreground location permissions, because there are two modes and each needs its own. */
    @Test
    fun theTwoForegroundLocationPermissionsAreDeclared() {
        val requested = requestedPermissions()
        for (kind in DeviceLocationKind.entries) {
            assertTrue(
                "${kind.name} needs ${kind.permission} and the manifest does not request it",
                requested.contains(kind.permission),
            )
        }
    }

    // ------------------------------------------------------------------ what is deliberately not

    /**
     * **The decision.** No background location permission, on purpose and with the measurement to
     * back it.
     *
     * If this fails, one of two things happened. Either somebody added the permission -- in which
     * case the runtime evidence in `RELEASE_HISTORY.md` under v4.6 says it was not needed on
     * Android 17, and the diff should say what changed. Or a dependency started merging it in, in
     * which case it needs a `tools:node="remove"`, because a permission the app never checks still
     * shows the user a prompt and still needs a Play declaration.
     */
    @Test
    fun backgroundLocationIsNotRequested() {
        assertFalse(
            "ACCESS_BACKGROUND_LOCATION is in the merged manifest -- see this class's doc comment",
            requestedPermissions().contains(BACKGROUND_LOCATION),
        )
    }

    /**
     * And no foreground service of any type, which is the other way the same capability is usually
     * bought.
     *
     * Checked as "no services but the wallpaper, and it declares no foreground service type",
     * because a `location` type is only reachable through a service and the app has exactly one.
     * The `FOREGROUND_SERVICE*` permissions are checked too: they are what a type would require,
     * so their absence is the same statement made from the other end.
     */
    @Test
    fun thereIsNoForegroundService() {
        val requested = requestedPermissions()
        for (permission in requested) {
            assertFalse(
                "$permission implies a foreground service, which v4.6 established is not needed",
                permission.startsWith("android.permission.FOREGROUND_SERVICE"),
            )
        }

        val services = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SERVICES,
        ).services.orEmpty()
        assertEquals(
            "the app declares services other than the wallpaper: ${services.map { it.name }}",
            1,
            services.size,
        )
        val wallpaper = services.single()
        assertEquals(
            "com.paperscrape.livewallpaper.engine.PaperWallpaperService",
            wallpaper.name,
        )
        assertEquals(
            "the wallpaper service declared a foreground service type",
            0,
            wallpaper.foregroundServiceType,
        )
    }

    /**
     * The binding that pays for all of it.
     *
     * `BIND_WALLPAPER` is what makes this service *the wallpaper* rather than a service the app
     * started, and being the wallpaper is the entire reason the process keeps foreground location
     * capability with no Activity and no notification. Pinned so that a change to how the service
     * is declared shows up next to the tests that depend on it.
     */
    @Test
    fun theWallpaperServiceIsBoundByTheSystem() {
        val service = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SERVICES,
        ).services.orEmpty().single()
        assertNotNull("the wallpaper service is not exported to the system", service.permission)
        assertEquals("android.permission.BIND_WALLPAPER", service.permission)
        assertTrue("the wallpaper service must be exported for the system to bind it", service.exported)
    }

    private companion object {
        const val BACKGROUND_LOCATION = "android.permission.ACCESS_BACKGROUND_LOCATION"
    }
}
