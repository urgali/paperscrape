package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.BuildConfig
import com.paperscrape.livewallpaper.prefs.BackupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import android.Manifest
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.location.LocationManagerCompat
import com.paperscrape.livewallpaper.engine.PaperWallpaperService
import com.paperscrape.livewallpaper.engine.SpriteCache
import com.paperscrape.livewallpaper.engine.WallpaperEngineCensus
import com.paperscrape.livewallpaper.icon.LauncherIconSwitch
import com.paperscrape.livewallpaper.location.DeviceLocationAccess
import com.paperscrape.livewallpaper.location.DeviceLocationKind
import com.paperscrape.livewallpaper.prefs.CustomThemeStore
import com.paperscrape.livewallpaper.prefs.WallpaperPrefs
import com.paperscrape.livewallpaper.ui.theme.PaperScrapeTheme
import com.paperscrape.livewallpaper.update.ApkDownloader
import com.paperscrape.livewallpaper.update.UpdateNotifier
import com.paperscrape.livewallpaper.update.UpdatePrefs

class SettingsActivity : ComponentActivity() {

    private lateinit var prefs: WallpaperPrefs
    private lateinit var customThemeStore: CustomThemeStore
    private lateinit var updatePrefs: UpdatePrefs

    /**
     * The screen has left the display: give back the pictures its cards decoded (v5.10B).
     *
     * Every gallery card and preview blits from [SpriteCache], which keeps what it decodes until
     * memory runs short -- and in this process that is not soon: the wallpaper's own window keeps it
     * a process showing a UI, so `TRIM_MEMORY_UI_HIDDEN` never arrives (v5.10A measured a clear
     * waiting for it, and it freed nothing). One visit to the gallery left the wallpaper's process at
     * 100.4 MB instead of 52.8. Clearing the cache here, and asking for a collection -- a bitmap's
     * pixels go only when its Java object does -- brought it to 89.6 MB (the ~17 MB the system kept
     * for drawing the screens is not the app's to give back). The next visit decodes the cards again.
     *
     * Not while an engine draws with `Canvas`, which reads the same cache every frame and would have
     * to decode its whole scene at once ([WallpaperEngineCensus.anyCanvasEngine]); and not across a
     * configuration change, where the screen comes straight back.
     */
    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations || WallpaperEngineCensus.anyCanvasEngine) return
        SpriteCache.clear()
        Runtime.getRuntime().gc()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = WallpaperPrefs(applicationContext)
        customThemeStore = CustomThemeStore(applicationContext)
        updatePrefs = UpdatePrefs(applicationContext)

        // BCK-06: finish an import the process was killed in the middle of. Launched alongside the
        // first composition rather than before it, so the screen may show the old saved themes for
        // a moment; the saved-theme flow re-emits once the pending write lands. Idempotent, and a
        // no-op on every start but that one.
        lifecycleScope.launch {
            runCatching { BackupRepository(prefs, customThemeStore, BuildConfig.VERSION_NAME).finishPendingImport() }
        }

        // The seasonal launcher icon, for the user who has this app installed but has not set it
        // as their wallpaper: SeasonalIconController only runs while the wallpaper service does,
        // so opening this screen is the one other moment the app can put the icon right. One read
        // of the stored calendar and the same switch the service uses.
        //
        // **Off the main thread.** On the main dispatcher this work queues behind the first
        // composition and the first frame: measured on a BV6600, the first launch after a clean
        // install put the icon right 10.2 s after the tap, against 7.0 s from Dispatchers.Default,
        // where what is left is the cold start itself. Nothing here touches the UI, and
        // setComponentEnabledSetting is a binder call that has no business on the main thread.
        lifecycleScope.launch(Dispatchers.Default) {
            runCatching {
                LauncherIconSwitch.applyForDate(
                    applicationContext,
                    calendar = prefs.settingsFlow.first().seasonalCalendar,
                )
            }
        }

        // The APK of an update already installed: for the user whose wallpaper is not PaperScrape,
        // opening this screen is the first start after the update (the engine does it otherwise).
        // See ApkDownloader.pruneInstalled.
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { ApkDownloader.pruneInstalled(applicationContext, BuildConfig.VERSION_NAME) }
        }

        // The synchronous CustomThemeRegistry is kept warm for this process too -- theme previews
        // in the settings UI resolve through the same ThemeCatalog.byId / SceneObjectCatalog.
        // layoutFor functions the live wallpaper engine uses -- but **that collector now lives in
        // the composition**, next to the state it has to stay in step with. v4.6 moved it: a
        // second, independent collector here meant the registry and the settings tree's own
        // `customThemeData` were updated in an undefined order, so a composable reading both could
        // see one of them stale. See `rememberCustomThemeData`.

        // **The notification's destination** (A3, v5.7D). A tap on "PaperScrape v5.7 is available"
        // arrives here carrying the release tag, and the settings screen checks again and opens the
        // existing "Update available" dialog as soon as that check finds the release -- so the user
        // keeps all three choices the in-app prompt has always offered: install, remind me later,
        // the release page.
        //
        // Read from `intent` in `onCreate` rather than from `onNewIntent`, which this Activity does
        // not override: the notification's PendingIntent carries FLAG_ACTIVITY_CLEAR_TOP, and this
        // Activity is `standard`, so the tap starts a fresh instance with a fresh intent either way.
        val openUpdateForTag = intent?.getStringExtra(UpdateNotifier.EXTRA_SHOW_UPDATE_TAG)

        setContent {
            PaperScrapeTheme {
                SettingsScreen(
                    prefs = prefs,
                    customThemeStore = customThemeStore,
                    updatePrefs = updatePrefs,
                    onApplyWallpaper = { launchSetWallpaperFlow() },
                    onRequestLocationPermission = { kind, onResult -> requestLocationPermission(kind, onResult) },
                    onRequestNotificationPermission = { onResult -> requestNotificationPermission(onResult) },
                    openUpdateForTag = openUpdateForTag,
                )
            }
        }
    }

    private fun launchSetWallpaperFlow() {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this@SettingsActivity, PaperWallpaperService::class.java),
            )
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            // Fallback for devices without the direct-apply intent: open the general wallpaper chooser.
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }

    private var pendingLocationKind: DeviceLocationKind? = null
    private var pendingLocationCallback: ((LocationPermissionAnswer) -> Unit)? = null

    /**
     * The launcher for the location permissions: **several in one request** since v5.10D, because from
     * Android 12 a request for the precise location alone is ignored by the system (see
     * `DeviceLocationAccess.permissionsToRequest`). The answer is read back from the phone rather than
     * from the result map, which leaves out a permission the user had already given.
     */
    private val requestPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) {
            val kind = pendingLocationKind
            val callback = pendingLocationCallback
            pendingLocationKind = null
            pendingLocationCallback = null
            if (kind != null && callback != null) callback(locationPermissionAnswer(kind))
        }

    private fun locationPermissionAnswer(kind: DeviceLocationKind): LocationPermissionAnswer {
        fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        val manager = getSystemService(LOCATION_SERVICE) as? LocationManager
        return LocationPermissionAnswer(
            fineGranted = granted(Manifest.permission.ACCESS_FINE_LOCATION),
            coarseGranted = granted(Manifest.permission.ACCESS_COARSE_LOCATION),
            locationOn = manager != null && LocationManagerCompat.isLocationEnabled(manager),
            systemWouldAskAgain = shouldShowRequestPermissionRationale(kind.permission),
        )
    }

    private var pendingNotificationCallback: ((granted: Boolean, canAskAgain: Boolean) -> Unit)? = null

    /**
     * The launcher for `POST_NOTIFICATIONS`, separate from the location one on purpose.
     *
     * Two launchers rather than one shared one because `registerForActivityResult` hands the result
     * to whichever callback the launcher was registered with, and a single pending-callback field
     * serving both would deliver a location answer to a notification request if the two ever
     * overlapped. They cannot today; a second field costs nothing and removes the question.
     */
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
            pendingNotificationCallback?.invoke(granted, granted || systemWouldAskForNotificationsAgain())
            pendingNotificationCallback = null
        }

    /**
     * After a refusal, whether the system would put the notification dialog up again: false once the
     * user has refused it for good (twice, from Android 11), and from then on a request returns at
     * once with no dialog -- so the row's tap must open the phone's page instead (v5.10C). Only ever
     * called after a request, so only on API 33+; `InlinedApi` for the reason given on
     * [requestNotificationPermission].
     */
    @SuppressLint("InlinedApi")
    private fun systemWouldAskForNotificationsAgain(): Boolean =
        shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)

    /**
     * Puts the system's notification dialog up, on the platforms that have one.
     *
     * **Never reached below API 33**, because the caller asks
     * `UpdateNotificationPolicy.mustAsk` first and that returns false for `NOT_REQUIRED`. It matters:
     * `RequestPermission` on a permission the platform does not define returns a result immediately
     * and without a dialog, and the result is `false` -- which the caller would read as a refusal
     * and would switch the feature back off on every Android 8 to 12 device, this project's test
     * phone among them.
     *
     * `InlinedApi` is suppressed for that reason: `POST_NOTIFICATIONS` is a String constant the
     * compiler copies in, so naming it below API 33 cannot fail, and this is never called there.
     */
    @SuppressLint("InlinedApi")
    private fun requestNotificationPermission(onResult: (granted: Boolean, canAskAgain: Boolean) -> Unit) {
        pendingNotificationCallback = onResult
        requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * Asks for exactly the permissions the chosen mode needs, and no more.
     *
     * "Network" asks for `ACCESS_COARSE_LOCATION` and stops there -- asking for fine
     * location to serve a mode that will only ever read the network provider would be requesting
     * a capability the feature does not use. "GPS" asks for `ACCESS_FINE_LOCATION` **together with
     * `ACCESS_COARSE_LOCATION`** (v5.10D): the system presents them as one dialog, "Precise" or
     * "Approximate" from Android 12, and ignores a request for the precise one alone there.
     */
    private fun requestLocationPermission(kind: DeviceLocationKind, onResult: (LocationPermissionAnswer) -> Unit) {
        pendingLocationKind = kind
        pendingLocationCallback = onResult
        requestPermissionLauncher.launch(DeviceLocationAccess.permissionsToRequest(kind).toTypedArray())
    }
}
