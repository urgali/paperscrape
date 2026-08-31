package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.BuildConfig
import com.paperscrape.livewallpaper.prefs.BackupRepository
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.paperscrape.livewallpaper.engine.PaperWallpaperService
import com.paperscrape.livewallpaper.prefs.CustomThemeStore
import com.paperscrape.livewallpaper.prefs.WallpaperPrefs
import com.paperscrape.livewallpaper.ui.theme.PaperScrapeTheme
import com.paperscrape.livewallpaper.update.UpdatePrefs

class SettingsActivity : ComponentActivity() {

    private lateinit var prefs: WallpaperPrefs
    private lateinit var customThemeStore: CustomThemeStore
    private lateinit var updatePrefs: UpdatePrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = WallpaperPrefs(applicationContext)
        customThemeStore = CustomThemeStore(applicationContext)
        updatePrefs = UpdatePrefs(applicationContext)

        // BCK-06: finish an import the process was killed in the middle of, before anything in this
        // screen reads the saved themes. Idempotent, and a no-op on every start but that one.
        lifecycleScope.launch {
            runCatching { BackupRepository(prefs, customThemeStore, BuildConfig.VERSION_NAME).finishPendingImport() }
        }

        // The synchronous CustomThemeRegistry is kept warm for this process too -- theme previews
        // in the settings UI resolve through the same ThemeCatalog.byId / SceneObjectCatalog.
        // layoutFor functions the live wallpaper engine uses -- but **that collector now lives in
        // the composition**, next to the state it has to stay in step with. v4.6 moved it: a
        // second, independent collector here meant the registry and the settings tree's own
        // `customThemeData` were updated in an undefined order, so a composable reading both could
        // see one of them stale. See `rememberCustomThemeData`.

        setContent {
            PaperScrapeTheme {
                SettingsScreen(
                    prefs = prefs,
                    customThemeStore = customThemeStore,
                    updatePrefs = updatePrefs,
                    onApplyWallpaper = { launchSetWallpaperFlow() },
                    onRequestLocationPermission = { permission, onGranted -> requestLocationPermission(permission, onGranted) },
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

    private var pendingLocationCallback: ((Boolean) -> Unit)? = null

    private val requestPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
            pendingLocationCallback?.invoke(granted)
            pendingLocationCallback = null
        }

    /**
     * Asks for exactly the permission the chosen mode needs, and no more.
     *
     * "Network / Cell" asks for `ACCESS_COARSE_LOCATION` and stops there -- asking for fine
     * location to serve a mode that will only ever read the network provider would be requesting
     * a capability the feature does not use. "GPS" asks for `ACCESS_FINE_LOCATION`, which the
     * system presents as the precise-location choice.
     */
    private fun requestLocationPermission(permission: String, onResult: (Boolean) -> Unit) {
        pendingLocationCallback = onResult
        requestPermissionLauncher.launch(permission)
    }
}
