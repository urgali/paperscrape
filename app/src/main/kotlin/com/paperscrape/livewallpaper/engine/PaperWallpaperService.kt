package com.paperscrape.livewallpaper.engine

import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.paperscrape.livewallpaper.location.DeviceLocationFix
import com.paperscrape.livewallpaper.location.DeviceLocationProvider
import com.paperscrape.livewallpaper.location.LocationSource
import com.paperscrape.livewallpaper.prefs.CustomThemeStore
import com.paperscrape.livewallpaper.prefs.WallpaperPrefs
import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import com.paperscrape.livewallpaper.weather.LiveWeatherInputs
import com.paperscrape.livewallpaper.weather.LiveWeatherStatus
import com.paperscrape.livewallpaper.weather.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import java.util.TimeZone

// ~30 fps, plenty smooth for a slow-moving scene. File-scoped rather than a companion object
// because PaperEngine is an *inner* class (needs implicit access to the outer Service's
// Context) — Kotlin does not allow companion objects inside inner classes.
private const val FRAME_INTERVAL_MS = 33L
// Once an hour, matching aa's own explicit choice -- weather doesn't change fast enough to
// justify more frequent network calls (or the battery/data cost of them) on something that's
// running continuously as a live wallpaper, unlike a foreground app a user only glances at.
private const val WEATHER_REFRESH_INTERVAL_MS = 60 * 60 * 1000L
// How often the refresh loop below wakes up to *check* whether an hour has passed (or whether
// Live Weather/location just became available for the first time) -- much shorter than the
// refresh interval itself so a freshly-enabled toggle gets its first fetch promptly instead of
// waiting up to an hour, while the actual network call still only fires once per
// [WEATHER_REFRESH_INTERVAL_MS].
private const val WEATHER_CHECK_INTERVAL_MS = 2 * 60 * 1000L

/**
 * System-facing entry point. Android instantiates a fresh [PaperEngine] per active
 * wallpaper surface (usually one, occasionally two during a live preview transition).
 */
class PaperWallpaperService : WallpaperService() {

    /**
     * How many engines are currently visible, i.e. actually drawing frames.
     *
     * A wallpaper process can host more than one engine at a time -- the picker's preview engine
     * alongside the live one -- and they share a single [SpriteCache], so the memory policy needs
     * to know whether *anything* is drawing, not whether one particular engine is.
     *
     * Only ever touched from the main thread: engine lifecycle callbacks and `onTrimMemory` are
     * both delivered there.
     */
    private var visibleEngineCount = 0

    /**
     * Every live engine, so a memory-pressure signal can reach each one's render thread.
     *
     * GPU textures can only be deleted by the thread whose context owns them, so the trim cannot be
     * applied here the way it used to be. Touched only from the main thread: engine lifecycle
     * callbacks and `onTrimMemory` are both delivered there.
     */
    private val engines = mutableListOf<PaperEngine>()

    private fun onEngineVisibilityChanged(nowVisible: Boolean, wasVisible: Boolean) {
        if (nowVisible == wasVisible) return
        visibleEngineCount = (visibleEngineCount + if (nowVisible) 1 else -1).coerceAtLeast(0)
    }

    override fun onCreateEngine(): Engine = PaperEngine().also { engines.add(it) }

    /**
     * Releases cached sprites in proportion to how much trouble the system is in.
     *
     * The decision itself lives in [MemoryPressurePolicy] -- notably, `TRIM_MEMORY_UI_HIDDEN` is
     * *not* treated as pressure even though its numeric value sits above
     * `TRIM_MEMORY_RUNNING_CRITICAL`, because for a wallpaper it only means the settings screen
     * closed while the wallpaper carries on drawing.
     *
     * `onLowMemory()` is deliberately not overridden: it is deprecated as of API 36, and the
     * levels delivered here already cover the same situation on every supported version.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val anyVisible = visibleEngineCount > 0
        val action = MemoryPressurePolicy.actionFor(level, anyVisible)
        SpriteCache.onTrimMemory(level, anyEngineVisible = anyVisible)
        if (action == TrimAction.RELEASE_ALL) {
            // Tiny next to the sprites, but it holds native filter objects and everything in it
            // is rebuilt on demand, so there is no reason to keep it when releasing everything.
            TintFilterCache.clear()
        }
        if (action != TrimAction.KEEP_ALL) {
            // The GPU copies are derived from the bitmaps above and cost only a re-upload, so they
            // follow the same policy. Each engine drops its own, on its own render thread.
            for (engine in engines) engine.trimGpuResources()
        }
    }

    inner class PaperEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private val engineJob = Job()
        private val scope = CoroutineScope(Dispatchers.Main + engineJob)
        private lateinit var prefs: WallpaperPrefs

        private var renderer: PaperRenderer? = null
        // Written on the render thread by the settings collector and read by the weather loop on
        // its own coroutine, so its visibility across the two cannot be left to chance.
        @Volatile
        private var settings: WallpaperSettings = WallpaperSettings()
        private var visible = false
        private var lastFrameNanos = System.nanoTime()
        private var elapsedSeconds = SceneTime.ZERO

        /**
         * The GPU render thread, or null once the `Canvas` fallback has taken over.
         *
         * While it exists it owns both the EGL context and, by convention, every read and write of
         * the scene state: see [onRenderThread].
         */
        private var glThread: GlRenderThread? = null

        /**
         * Set when EGL could not be initialised. From then on the engine drives the `Canvas` path
         * from the main looper, exactly as it did before the GPU backend existed.
         */
        private var canvasFallback = false

        /** Reused by the fallback path so the indirection adds no per-frame allocation. */
        private val canvasTarget = CanvasSceneTarget()

        private var locationProvider: DeviceLocationProvider? = null
        private var sunriseHour = 6f
        private var sunsetHour = 20f
        private var hasFixLocation = false
        // The exact same fix updateSunTimesFromLocation just derived sunrise/sunset from --
        // stored separately so the weather-refresh loop below can reuse it for
        // WeatherRepository.fetchCurrentConditions without re-deriving or re-fetching location
        // itself (see DeviceLocationFix's own doc comment for why this sharing is the point).
        // Written from the location callbacks, read by the weather loop. Same reason as
        // [settings] above.
        @Volatile
        private var lastLocationFix: DeviceLocationFix? = null

        /**
         * Wakes the Live Weather loop when the preference changes, instead of making the user
         * wait for its next scheduled check. Conflated: several rapid edits collapse into one
         * wake-up, and a wake-up sent while the loop is busy is not lost.
         */
        private val weatherWakeUp = Channel<Unit>(Channel.CONFLATED)
        // Cleared by the settings collector when Live Weather is toggled, read and written by the
        // weather loop. Same reason as [settings] above.
        @Volatile
        private var lastWeatherFetchMillis = 0L

        /**
         * The coordinates the last successful weather fetch was made for.
         *
         * The refresh timer answers "are these conditions stale"; this answers "are these the
         * conditions of the place we are actually showing". Only the second one changes when the
         * user edits their custom location, and until it existed the first was the only gate.
         */
        @Volatile
        private var lastWeatherFetchLocation: DeviceLocationFix? = null

        /**
         * What the settings screen was last told about Live Weather's fallback state.
         *
         * Kept so the status is written only when it changes: every write re-emits the settings
         * flow, and the weather loop evaluates every two minutes.
         */
        @Volatile
        private var publishedWeatherStatus: LiveWeatherStatus? = null

        /**
         * Which of the two mutually exclusive location sources the current [lastLocationFix] came
         * from.
         *
         * Exists because `hasFixLocation` alone cannot answer "is this fix still the right kind" --
         * see the collector for the bug that produced.
         */
        private var locationSource: LocationSource = LocationSource.NONE
        private var lastAppliedThemeId = "sunset"
        private var lastAppliedCustomization: SceneCustomization = SceneCustomization.DEFAULT

        private val drawRunnable = Runnable { drawFrame() }

        /**
         * Resolves which themeId should actually be rendered right now: the user's manual pick,
         * or — if "automatic theme by date" is on and a seasonal window currently applies — the
         * seasonal one instead. Also resolves that theme's scene-object customization (a saved
         * theme's own baked-in settings, or the in-progress live edit if it's tagged for this
         * exact theme, or plain defaults otherwise — see
         * [CustomThemeRegistry.resolveActiveCustomization]). Returns true if anything actually
         * rendered changed since the last call, so the caller knows whether an out-of-cycle
         * redraw is worth forcing.
         */
        /**
         * Runs [action] on whichever thread currently owns the scene state.
         *
         * With the GPU backend that is the render thread, so the update is queued and lands between
         * two frames; on the `Canvas` fallback the main looper owns it and the update runs inline.
         * Every path that mutates the renderer from a coroutine or a system callback goes through
         * here, which is what keeps the scene single-threaded despite the draw having moved off the
         * main thread — the alternative, a lock around the renderer, would put every settings write
         * in contention with the frame loop.
         */
        private inline fun onRenderThread(crossinline action: () -> Unit) {
            val thread = glThread
            if (thread != null) thread.queueEvent(Runnable { action() }) else action()
        }

        /** Asks the render thread to drop its GPU textures. Safe to call before it exists. */
        fun trimGpuResources() {
            val thread = glThread ?: return
            thread.queueEvent(Runnable { thread.target.trimTextures() })
        }

        private fun applyEffectiveTheme(): Boolean {
            val effectiveId = if (settings.autoThemeByDate) {
                SeasonalThemeRules.themeForDate() ?: settings.themeId
            } else {
                settings.themeId
            }
            val resolvedCustomization = CustomThemeRegistry.resolveActiveCustomization(
                themeId = effectiveId,
                pendingCustomization = settings.pendingCustomization,
                pendingThemeId = settings.pendingCustomizationThemeId,
            )
            val changed = effectiveId != lastAppliedThemeId || resolvedCustomization != lastAppliedCustomization
            renderer?.theme = ThemeCatalog.byId(effectiveId)
            renderer?.sceneCustomization = resolvedCustomization
            renderer?.hillsVariation = resolvedCustomization.hillsVariation
            lastAppliedThemeId = effectiveId
            lastAppliedCustomization = resolvedCustomization
            return changed
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            prefs = WallpaperPrefs(applicationContext)
            val customThemeStore = CustomThemeStore(applicationContext)
            scope.launch {
                customThemeStore.dataFlow.collect { data ->
                    onRenderThread {
                        CustomThemeRegistry.update(data)
                        // An override/reset/delete can change what the *current* themeId resolves
                        // to even though themeId itself didn't change -- re-apply and redraw.
                        if (applyEffectiveTheme()) requestRedraw()
                    }
                }
            }
            scope.launch {
                prefs.settingsFlow.collect { newSettings ->
                    // **Published here, not inside the queued block below.** `settings` used to be
                    // assigned on the render thread, while the custom-location branch further down
                    // runs on this coroutine and wakes the weather loop straight away. The loop
                    // therefore woke, read a `settings` the render thread had not updated yet,
                    // saw Live Weather still off and went back to sleep -- and with a custom
                    // location that is the *only* wake-up that arrives promptly, because there is
                    // no GPS fix coming later to trigger another. Assigning it on the collector,
                    // before anything can observe the change, removes the window entirely.
                    val previousSettings = settings
                    settings = newSettings
                    // Every input the next fetch would use. Switching provider, or entering the
                    // key the selected provider was waiting for, is exactly as much a reason to
                    // re-check as flipping the switch: the answer on screen came from a different
                    // service, or from no service at all.
                    if (LiveWeatherInputs.changed(previousSettings, newSettings)) {
                        // Ignore the cached hourly timer. This is what makes OFF -> ON fetch now
                        // instead of at the next tick; the check-interval loop only ever decides
                        // *whether* a fetch is due, and this is what makes it due.
                        lastWeatherFetchMillis = 0L
                        weatherWakeUp.trySend(Unit)
                    }
                    onRenderThread {
                        val changed = applyEffectiveTheme()
                        renderer?.parallaxStrength = newSettings.parallaxStrength
                        renderer?.scrollBackground = newSettings.scrollBackground
                        renderer?.swipeScrollEnabled = newSettings.swipeScroll
                        renderer?.scrollSpeed = newSettings.scrollSpeed
                        if (changed) requestRedraw()
                    }
                    // **A fix belongs to the source it came from.** `hasFixLocation` used to be
                    // set by both the GPS and the custom paths, so switching Custom -> Phone found
                    // it already true, returned from maybeStartLocationUpdates without ever
                    // starting the provider, and left `lastLocationFix` holding the *custom*
                    // coordinates -- measured on a Pixel 9, where selecting Phone kept fetching
                    // Florence's weather. Treating a source change as an invalidation is what
                    // makes the two sources actually exclusive at runtime and not only in prefs.
                    val requestedSource = LocationSource.of(newSettings)
                    if (requestedSource != locationSource) {
                        locationSource = requestedSource
                        hasFixLocation = false
                        lastLocationFix = null
                        // The conditions on screen are the old source's. Nothing about them is
                        // worth keeping, so the next pass must fetch rather than compare.
                        lastWeatherFetchLocation = null
                        lastWeatherFetchMillis = 0L
                    }
                    if (newSettings.useLocationForSunTimes) {
                        maybeStartLocationUpdates()
                    } else {
                        stopLocationUpdates()
                        hasFixLocation = false
                        if (!newSettings.useCustomLocation) lastLocationFix = null
                    }
                    // Mutually exclusive with the phone-GPS path above (enforced at the
                    // WallpaperPrefs level too, but this recomputes immediately on every settings
                    // change rather than waiting for the next location fix, since a custom
                    // location never needs to "wait" for anything -- it's already known).
                    if (newSettings.useCustomLocation) {
                        updateSunTimesFromLocation(
                            DeviceLocationFix(
                                newSettings.customLocationLatitude.toDouble(),
                                newSettings.customLocationLongitude.toDouble(),
                            ),
                        )
                    } else if (!newSettings.useLocationForSunTimes) {
                        hasFixLocation = false
                        lastLocationFix = null
                    }
                }
            }
            // Live Weather (Phase 1d point 6): checks every WEATHER_CHECK_INTERVAL_MS whether a
            // fetch is due, but only actually calls WeatherRepository once per
            // WEATHER_REFRESH_INTERVAL_MS (or immediately the first time a location becomes
            // available while Live Weather is on) -- see those constants' own doc comments.
            scope.launch {
                while (true) {
                    val fix = lastLocationFix
                    // Two reasons to fetch, not one. The hourly timer is about the conditions
                    // going stale; a *different place* is about them being the wrong conditions
                    // entirely, and no amount of waiting fixes that. Moving the custom location
                    // used to leave the scene showing the old town's weather for the rest of the
                    // hour, because the timer was the only gate.
                    val movedSinceLastFetch = fix != null && fix != lastWeatherFetchLocation
                    val timerExpired = System.currentTimeMillis() - lastWeatherFetchMillis >= WEATHER_REFRESH_INTERVAL_MS
                    val provider = settings.weatherProvider
                    if (settings.liveWeatherEnabled && fix != null && (movedSinceLastFetch || timerExpired)) {
                        lastWeatherFetchMillis = System.currentTimeMillis()
                        lastWeatherFetchLocation = fix
                        val result = WeatherRepository.fetchCurrentConditions(
                            providerId = provider,
                            latitude = fix.latitude,
                            longitude = fix.longitude,
                            apiKey = settings.apiKeyForWeatherProvider,
                        )
                        // A failure leaves the *previous* snapshot in place rather than clearing
                        // it, so one dropped request doesn't momentarily revert the scene to the
                        // theme's manual precipitation/clouds; it keeps showing the last
                        // known-good conditions until the next successful fetch.
                        val snapshot = WeatherRepository.snapshotOf(result)
                        if (snapshot != null) {
                            onRenderThread {
                                renderer?.liveWeatherOverride = snapshot
                                requestRedraw()
                            }
                        }
                        // A missing key is not a transient. Nothing was sent, nothing will succeed
                        // until the user acts, and the two-minute tick would otherwise retry a
                        // request that cannot be made -- so the timer is left running rather than
                        // reset, and the status says what is wrong.
                        publishWeatherStatus(
                            LiveWeatherStatus.of(
                                enabled = true,
                                hasLocation = true,
                                result = result,
                                hasSnapshotInEffect = snapshot != null || renderer?.liveWeatherOverride != null,
                                previous = publishedWeatherStatus ?: LiveWeatherStatus.OFF,
                            ),
                        )
                    } else if (settings.liveWeatherEnabled && fix == null) {
                        // **Live Weather is on and has nowhere to check.** The scene keeps running
                        // on the theme's own clouds and precipitation, which is a valid scene and
                        // exactly what it shows with Live Weather off -- the failure was never
                        // that the scene broke, it was that the switch looked dead and nothing
                        // said why. Saying so is the whole fix; the renderer is left alone.
                        publishWeatherStatus(LiveWeatherStatus.NO_LOCATION)
                    } else if (!settings.liveWeatherEnabled) {
                        publishWeatherStatus(LiveWeatherStatus.OFF)
                        if (renderer?.liveWeatherOverride != null) {
                            lastWeatherFetchMillis = 0L
                            lastWeatherFetchLocation = null
                            onRenderThread {
                                renderer?.liveWeatherOverride = null
                                requestRedraw()
                            }
                        }
                    }
                    // Waits for the tick *or* for a settings change, whichever comes first.
                    withTimeoutOrNull(WEATHER_CHECK_INTERVAL_MS) { weatherWakeUp.receive() }
                }
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            // Built here, on the main thread, before the render thread starts. `Thread.start()`
            // establishes the happens-before that publishes it safely; every mutation after that
            // point goes through onRenderThread.
            renderer = PaperRenderer(holder.surfaceFrame.width(), holder.surfaceFrame.height(), applicationContext).apply {
                parallaxStrength = settings.parallaxStrength
                scrollBackground = settings.scrollBackground
                swipeScrollEnabled = settings.swipeScroll
                scrollSpeed = settings.scrollSpeed
            }
            applyEffectiveTheme()
            if (!canvasFallback) startGlThread(holder)
        }

        private fun startGlThread(holder: SurfaceHolder) {
            val thread = GlRenderThread(glCallbacks)
            glThread = thread
            thread.start()
            thread.onSurfaceCreated(holder)
            val frame = holder.surfaceFrame
            thread.onSurfaceChanged(frame.width(), frame.height())
            thread.setVisible(visible)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            val thread = glThread
            if (thread != null) {
                // The renderer's own size update is applied by the render thread, from
                // onGlSurfaceChanged, so that it happens with the GL viewport in the same state.
                thread.onSurfaceChanged(width, height)
            } else {
                renderer?.onSizeChanged(width, height)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            glThread?.onSurfaceDestroyed()
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            onEngineVisibilityChanged(nowVisible = visible, wasVisible = this.visible)
            this.visible = visible
            val thread = glThread
            if (thread != null) {
                if (visible) {
                    // Re-check the date every time the wallpaper becomes visible again (e.g. after
                    // the screen was off overnight) so a day boundary crossed while inactive is
                    // picked up promptly instead of waiting for the next settings change.
                    thread.queueEvent(Runnable { applyEffectiveTheme() })
                }
                thread.setVisible(visible)
                return
            }
            if (visible) {
                applyEffectiveTheme()
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
            // Always recorded, regardless of swipeScroll -- the renderer itself decides whether
            // this contributes to what's actually drawn (via swipeScrollEnabled), so there's no
            // risk of a stale non-zero value lingering from before the setting was turned off.
            onRenderThread { renderer?.homeScreenOffset = xOffset }
            // Redraw right away instead of waiting for the next scheduled ~33ms tick: the
            // launcher fires this callback continuously while the user drags between home
            // screens, so rendering immediately keeps the parallax glued to the finger instead
            // of trailing behind by up to one frame (perceived as stutter during the swipe).
            // Still redraws even with swipeScroll off, so e.g. day/night blending keeps updating
            // smoothly during a swipe rather than looking frozen -- only the parallax shift itself
            // is suppressed.
            if (visible) requestRedraw()
        }

        override fun onDestroy() {
            super.onDestroy()
            // An engine can be destroyed while still marked visible (the picker's preview engine
            // usually is). Without this the counter would never fall back to zero and the memory
            // policy would keep believing something is drawing.
            onEngineVisibilityChanged(nowVisible = false, wasVisible = visible)
            visible = false
            engines.remove(this)
            handler.removeCallbacks(drawRunnable)
            glThread?.shutdown()
            glThread = null
            engineJob.cancel()
            stopLocationUpdates()
        }

        /**
         * The GPU backend's side of the frame.
         *
         * Everything here runs on the render thread. `applyEffectiveTheme` is re-applied on
         * becoming visible and on every queued settings change, so this callback does nothing but
         * draw.
         */
        private val glCallbacks = object : GlRenderThread.Callbacks {
            override fun onGlSurfaceChanged(width: Int, height: Int) {
                renderer?.onSizeChanged(width, height)
            }

            override fun onGlDrawFrame(target: SceneCanvas, deltaSeconds: Float) {
                renderScene(target, deltaSeconds)
            }

            override fun onGlUnavailable() {
                // Reported from the render thread; the switch itself has to happen on the main
                // thread, which owns the Handler loop the fallback runs on.
                handler.post { switchToCanvasFallback() }
            }
        }

        /**
         * Gives up on GL for the rest of this engine's life and restarts the `Canvas` loop.
         *
         * Reached only when EGL could not be initialised at all. The scene state is untouched by
         * this: the same renderer keeps drawing, through the other backend.
         */
        private fun switchToCanvasFallback() {
            if (canvasFallback) return
            canvasFallback = true
            glThread?.shutdown()
            glThread = null
            val frame = surfaceHolder.surfaceFrame
            renderer?.onSizeChanged(frame.width(), frame.height())
            lastFrameNanos = System.nanoTime()
            if (visible) handler.post(drawRunnable)
        }

        /**
         * Asks for a frame outside the normal cadence.
         *
         * The GPU loop already runs continuously while visible, so there is nothing to nudge there;
         * on the `Canvas` fallback this is the out-of-cycle redraw the engine has always done when
         * something changed between ticks.
         */
        private fun requestRedraw() {
            if (glThread != null) return
            if (visible) drawFrame()
        }

        private fun drawFrame() {
            val frameStartNanos = System.nanoTime()
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    canvasTarget.bind(canvas)
                    val now = System.nanoTime()
                    val deltaSeconds = ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.5f)
                    lastFrameNanos = now
                    renderScene(canvasTarget, deltaSeconds)
                    canvasTarget.unbind()
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
                // Compensated (not fixed) delay: a plain `postDelayed(drawRunnable,
                // FRAME_INTERVAL_MS)` always adds a *full* 33ms on top of however long this
                // frame's own lockCanvas+render+unlockCanvasAndPost just took, so real
                // frame-to-frame spacing drifts above the intended cadence and fluctuates with
                // whatever else is happening on the device (GC pause, other apps competing for
                // CPU, more objects animating at once). Since every animation in this engine
                // already scales its movement by the *actual* measured deltaSeconds (correct
                // position for whatever time really elapsed), the animated values themselves
                // aren't wrong -- but uneven real-world frame spacing still reads to the eye as
                // stutter, most noticeably on the largest/fastest-moving shape on screen (the
                // sleigh group). Subtracting this frame's own cost keeps the *schedule* itself
                // close to a steady 33ms cadence instead of compounding drift on top of it.
                val frameCostMs = (System.nanoTime() - frameStartNanos) / 1_000_000L
                val nextDelayMs = (FRAME_INTERVAL_MS - frameCostMs).coerceIn(0L, FRAME_INTERVAL_MS)
                handler.postDelayed(drawRunnable, nextDelayMs)
            }
        }

        /**
         * One frame of the scene, backend-independent.
         *
         * Both loops call this: the render thread with the GPU target, the fallback with the
         * `Canvas` one. Advancing scene time lives here rather than in either loop so that the two
         * cannot drift apart in how they treat a late or a first frame.
         */
        private fun renderScene(target: SceneCanvas, deltaSeconds: Float) {
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

            renderer?.draw(target, dayPhase, elapsedSeconds, deltaSeconds)
        }

        // --- Optional location support (only used if the user opts in from settings) ---
        // Sunrise/sunset today; Live Weather (Phase 1d point 6) will read the same
        // DeviceLocationFix from this same provider instance rather than fetching its own.

        /**
         * Tells the settings screen whether Live Weather is running on fallback.
         *
         * Only on a change, because every write re-emits the settings flow to every collector and
         * this is evaluated on a two-minute tick.
         */
        private fun publishWeatherStatus(status: LiveWeatherStatus) {
            if (publishedWeatherStatus == status) return
            publishedWeatherStatus = status
            scope.launch { prefs.setLiveWeatherStatus(status) }
        }

        private fun maybeStartLocationUpdates() {
            if (hasFixLocation) return
            val provider = (locationProvider ?: DeviceLocationProvider(applicationContext)).also { locationProvider = it }
            if (!provider.hasPermission()) return
            provider.start { fix -> updateSunTimesFromLocation(fix, isGpsFix = true) }
        }

        private fun stopLocationUpdates() {
            locationProvider?.stop()
        }

        private fun updateSunTimesFromLocation(fix: DeviceLocationFix, isGpsFix: Boolean = false) {
            val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
            // getOffset(instant) — not rawOffset — because rawOffset is explicitly the
            // *standard* (non-DST) offset; using it directly made every sunrise/sunset an hour
            // off during DST. getOffset(now) already includes whatever DST adjustment applies to
            // this exact moment.
            val utcOffsetHours = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3_600_000.0
            val (sunrise, sunset) = SunPositionCalculator.approximateSunriseSunset(
                latitudeDeg = fix.latitude,
                longitudeDeg = fix.longitude,
                dayOfYear = dayOfYear,
                utcOffsetHours = utcOffsetHours,
            )
            sunriseHour = sunrise
            sunsetHour = sunset
            hasFixLocation = true
            lastLocationFix = fix
            // The weather loop's condition has two inputs, and until now only one of them woke
            // it. v76.4 made the *preference* wake it, which is why switching Live Weather on
            // stopped being a no-op; but the loop then finds no location fix yet -- GPS takes
            // seconds to arrive, and the settings screen is usually where the switch is thrown --
            // does nothing, and goes back to waiting out its full two-minute tick. That is the
            // "nothing happens until a restart or a theme change" the maintainer saw: the fetch
            // was two minutes away, not broken.
            //
            // A fix arriving is exactly as much a reason to re-evaluate as the preference
            // changing, so it signals the same conflated channel.
            weatherWakeUp.trySend(Unit)
            // Only the GPS path persists its resolved coordinates back to WallpaperPrefs -- the
            // custom-location path's fix is built directly from settings.customLocationLatitude/
            // Longitude, which Settings already has without any round trip through this service.
            // (See WallpaperSettings.resolvedGpsLatitude/Longitude's own doc comment.)
            if (isGpsFix) {
                scope.launch { prefs.setResolvedGpsLocation(fix.latitude.toFloat(), fix.longitude.toFloat()) }
            }
        }
    }
}
