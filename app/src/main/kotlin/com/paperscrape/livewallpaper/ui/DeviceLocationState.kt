package com.paperscrape.livewallpaper.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.paperscrape.livewallpaper.location.DeviceLocationAccess
import com.paperscrape.livewallpaper.location.DeviceLocationKind
import com.paperscrape.livewallpaper.location.DeviceLocationProvider
import com.paperscrape.livewallpaper.prefs.WallpaperSettings

/**
 * What the phone allows for the chosen GPS or Network source, **read from the phone when the screen is
 * composed and again every time it comes back to the front** (v5.10D, row 5 of the v5.10A table: *the
 * location from the phone looks at the phone every time the page is opened*). The way to change it is to
 * leave for the phone's settings -- the permission page, the location switch -- and come back, so a value
 * read once would be stale exactly when it matters; the same reading *Advanced & about* makes of the
 * phone's notification settings (v5.8B).
 *
 * Null when the source is Off or Custom, which use nothing of the phone's.
 */
@Composable
internal fun rememberDeviceLocationAccess(settings: WallpaperSettings): DeviceLocationAccess? {
    val kind: DeviceLocationKind? =
        if (settings.useLocationForSunTimes && !settings.useCustomLocation) settings.deviceLocationKind else null
    val context = LocalContext.current
    fun read(): DeviceLocationAccess? = kind?.let { DeviceLocationProvider(context.applicationContext).access(it) }
    var access by remember(kind) { mutableStateOf(read()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, kind) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) access = read()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return access
}

/** [SettingsUiModel.liveWeather]'s `devicePositionUsable`: true for Off and Custom, where it is not asked. */
internal fun DeviceLocationAccess?.positionUsable(): Boolean = this?.mayUsePosition ?: true

/**
 * The phone's answer to a location permission request, as [SettingsUiModel.afterLocationRequest] reads
 * it: the two permissions as now held, the phone's location switch, and whether the system would show
 * the dialog again (`shouldShowRequestPermissionRationale` on the permission the kind needs).
 */
data class LocationPermissionAnswer(
    val fineGranted: Boolean,
    val coarseGranted: Boolean,
    val locationOn: Boolean,
    val systemWouldAskAgain: Boolean,
)
