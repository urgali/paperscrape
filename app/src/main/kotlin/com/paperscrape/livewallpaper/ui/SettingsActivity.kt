package com.paperscrape.livewallpaper.ui

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.paperscrape.livewallpaper.engine.PaperWallpaperService
import com.paperscrape.livewallpaper.prefs.WallpaperPrefs
import com.paperscrape.livewallpaper.ui.theme.PaperScrapeTheme

class SettingsActivity : ComponentActivity() {

    private lateinit var prefs: WallpaperPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = WallpaperPrefs(applicationContext)

        setContent {
            PaperScrapeTheme {
                SettingsScreen(
                    prefs = prefs,
                    onApplyWallpaper = { launchSetWallpaperFlow() },
                    onRequestLocationPermission = { onGranted -> requestLocationPermission(onGranted) },
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

    private fun requestLocationPermission(onResult: (Boolean) -> Unit) {
        pendingLocationCallback = onResult
        requestPermissionLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}
