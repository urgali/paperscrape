package com.paperscrape.livewallpaper.engine

import android.graphics.Canvas
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import com.paperscrape.livewallpaper.prefs.WallpaperPrefs
import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

// ~30 fps, plenty smooth for a slow-moving scene. File-scoped rather than a companion object
// because PaperEngine is an *inner* class (needs implicit access to the outer Service's
// Context) — Kotlin does not allow companion objects inside inner classes.
private const val FRAME_INTERVAL_MS = 33L
private const val TEN_MINUTES_MS = 10 * 60 * 1000L

/**
 * System-facing entry point. Android instantiates a fresh [PaperEngine] per active
 * wallpaper surface (usually one, occasionally two during a live preview transition).
 */
class PaperWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = PaperEngine()

    inner class PaperEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private val engineJob = Job()
        private val scope = CoroutineScope(Dispatchers.Main + engineJob)
        private lateinit var prefs: WallpaperPrefs

        private var renderer: PaperRenderer? = null
        private var settings: WallpaperSettings = WallpaperSettings()
        private var visible = false
        private var lastFrameNanos = System.nanoTime()
        private var elapsedSeconds = 0f

        private val activeBirds = mutableListOf<PaperBird>()
        private val reactionSoundPlayer = ReactionSoundPlayer()

        private var locationManager: LocationManager? = null
        private var sunriseHour = 6f
        private var sunsetHour = 20f
        private var hasFixLocation = false

        private val drawRunnable = Runnable { drawFrame() }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            prefs = WallpaperPrefs(applicationContext)
            scope.launch {
                prefs.settingsFlow.collect { newSettings ->
                    val themeChanged = newSettings.themeId != settings.themeId
                    settings = newSettings
                    renderer?.theme = ThemeCatalog.byId(newSettings.themeId)
                    renderer?.parallaxStrength = newSettings.parallaxStrength
                    if (newSettings.useLocationForSunTimes) maybeStartLocationUpdates()
                    if (themeChanged) drawFrame()
                }
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            renderer = PaperRenderer(holder.surfaceFrame.width(), holder.surfaceFrame.height()).apply {
                theme = ThemeCatalog.byId(settings.themeId)
                parallaxStrength = settings.parallaxStrength
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            renderer?.onSizeChanged(width, height)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                lastFrameNanos = System.nanoTime()
                handler.post(drawRunnable)
            } else {
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int,
        ) {
            renderer?.homeScreenOffset = xOffset
        }

        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
            if (event.action != MotionEvent.ACTION_DOWN) return
            if (!settings.touchEffectsEnabled) return
            val r = renderer ?: return

            val hitObject = r.handleTap(event.x, event.y)
            if (hitObject != null) {
                reactionSoundPlayer.playFor(hitObject)
                return
            }

            val screenWidth = surfaceHolder.surfaceFrame.width().toFloat()
            activeBirds.add(PaperBird(event.x, event.y, screenWidth, r.theme.accentColor))
            if (activeBirds.size > 12) activeBirds.removeAt(0)
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacks(drawRunnable)
            engineJob.cancel()
            stopLocationUpdates()
            reactionSoundPlayer.release()
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    render(canvas)
                }
            } finally {
                canvas?.let {
                    try {
                        holder.unlockCanvasAndPost(it)
                    } catch (_: IllegalArgumentException) {
                        // Surface was destroyed mid-frame; safe to ignore.
                    }
                }
            }
            handler.removeCallbacks(drawRunnable)
            if (visible) {
                handler.postDelayed(drawRunnable, FRAME_INTERVAL_MS)
            }
        }

        private fun render(canvas: Canvas) {
            val now = System.nanoTime()
            val deltaSeconds = ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.5f)
            lastFrameNanos = now
            elapsedSeconds += deltaSeconds

            val hour = if (settings.syncWithRealTime) {
                SunPositionCalculator.currentHour24()
            } else {
                settings.fixedHour
            }

            val dayPhase = SunPositionCalculator.compute(
                hour24 = hour,
                sunriseHour = if (hasFixLocation) sunriseHour else 6f,
                sunsetHour = if (hasFixLocation) sunsetHour else 20f,
            )

            renderer?.draw(canvas, dayPhase, elapsedSeconds, deltaSeconds)

            val iterator = activeBirds.iterator()
            while (iterator.hasNext()) {
                val bird = iterator.next()
                bird.update(deltaSeconds)
                if (!bird.alive) {
                    iterator.remove()
                } else {
                    bird.draw(canvas)
                }
            }
        }

        // --- Optional location support (only used if the user opts in from settings) ---

        private val locationListener = LocationListener { location: Location ->
            updateSunTimesFromLocation(location)
        }

        private fun maybeStartLocationUpdates() {
            if (hasFixLocation) return
            val hasPermission = ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return

            try {
                val lm = (locationManager ?: getSystemService(LOCATION_SERVICE) as LocationManager)
                    .also { locationManager = it }
                val provider = when {
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    else -> return
                }
                lm.getLastKnownLocation(provider)?.let { updateSunTimesFromLocation(it) }
                lm.requestLocationUpdates(provider, TEN_MINUTES_MS, 1000f, locationListener, Looper.getMainLooper())
            } catch (_: SecurityException) {
                // Permission revoked between the check and the call; fall back to fixed sun times.
            }
        }

        private fun stopLocationUpdates() {
            try {
                locationManager?.removeUpdates(locationListener)
            } catch (_: SecurityException) {
                // no-op
            }
        }

        private fun updateSunTimesFromLocation(location: Location) {
            val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
            val utcOffsetHours = TimeZone.getDefault().rawOffset / 3_600_000.0
            val (sunrise, sunset) = SunPositionCalculator.approximateSunriseSunset(
                latitudeDeg = location.latitude,
                dayOfYear = dayOfYear,
                utcOffsetHours = utcOffsetHours,
            )
            sunriseHour = sunrise
            sunsetHour = sunset
            hasFixLocation = true
        }
    }
}
