package com.paperscrape.livewallpaper.engine

import android.graphics.Canvas
import android.os.Handler
import android.os.SystemClock
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import com.paperscrape.livewallpaper.location.DeviceLocationFix
import com.paperscrape.livewallpaper.location.DeviceLocationKind
import com.paperscrape.livewallpaper.location.DeviceLocationProvider
import com.paperscrape.livewallpaper.location.LocationSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.paperscrape.livewallpaper.prefs.CustomThemeStore
import com.paperscrape.livewallpaper.prefs.WallpaperPrefs
import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import com.paperscrape.livewallpaper.weather.LiveWeatherInputs
import com.paperscrape.livewallpaper.weather.LiveWeatherSchedule
import com.paperscrape.livewallpaper.weather.LiveWeatherSnapshot
import com.paperscrape.livewallpaper.weather.LiveWeatherStatus
import com.paperscrape.livewallpaper.weather.WeatherFetchResult
import com.paperscrape.livewallpaper.weather.WeatherRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
// Logcat tag for the engine's own backstop handler. File-scoped for the same reason
// FRAME_INTERVAL_MS is: PaperEngine is an inner class and cannot hold a companion object.
private const val TAG = "PaperEngine"
// Once an hour, matching aa's own explicit choice -- weather doesn't change fast enough to
// justify more frequent network calls (or the battery/data cost of them) on something that's
// running continuously as a live wallpaper, unlike a foreground app a user only glances at.
// v4.6 widened these two from `private` to `internal` and changed nothing else about them: the
// background-location proof needs the cadence to be assertable, and a number a test cannot read is
// a number a release can move without anybody noticing. See `BackgroundLocationContractTest`.
internal const val WEATHER_REFRESH_INTERVAL_MS = 60 * 60 * 1000L
// How often the refresh loop below wakes up to *check* whether an hour has passed (or whether
// Live Weather/location just became available for the first time) -- much shorter than the
// refresh interval itself so a freshly-enabled toggle gets its first fetch promptly instead of
// waiting up to an hour, while the actual network call still only fires once per
// [WEATHER_REFRESH_INTERVAL_MS].
internal const val WEATHER_CHECK_INTERVAL_MS = 2 * 60 * 1000L

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

    /**
     * One position request for the whole service, not one per engine.
     *
     * A wallpaper service commonly runs two engines at once -- the one drawing the home screen and
     * the one drawing the picker's preview -- and each has its own settings collector, so each
     * would ask the device where it is at the same moment. Measured on an Android 17 emulator:
     * three simultaneous registrations against the GPS provider for one user action. They were
     * short and bounded, but they were the same question asked three times, which is exactly the
     * kind of waste the location rework exists to remove.
     *
     * The provider and the lock live here, on the service, so the second and third callers wait
     * for the first answer instead of starting their own.
     */
    private var sharedLocationProvider: DeviceLocationProvider? = null
    private val locationRequestLock = Mutex()

    /**
     * The device's position, asked for at most once at a time across every engine.
     *
     * Whoever gets the lock makes the request; anyone who arrives while it is held waits and then
     * makes their own call, which by then finds a cached fix younger than
     * [DeviceLocationProvider.FRESH_ENOUGH_MS] and returns it without touching the radio.
     */
    private suspend fun deviceFix(kind: DeviceLocationKind): DeviceLocationFix? =
        locationRequestLock.withLock {
            val provider = (sharedLocationProvider ?: DeviceLocationProvider(applicationContext))
                .also { sharedLocationProvider = it }
            provider.currentFix(kind)
        }

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

        /**
         * **The engine survives its own coroutines failing, and the wallpaper survives with it.**
         *
         * Two deliberate choices, both added in v3.1 after a corrupt preferences file was shown to
         * take the whole process down (see [com.paperscrape.livewallpaper.prefs.PrefsRecovery]):
         *
         * - [SupervisorJob], so one collector dying does not cancel its siblings. With a plain
         *   `Job` a failed settings read also stopped the theme collector, the weather loop and
         *   the location refresh -- the wallpaper kept drawing, but it had gone deaf.
         * - A [CoroutineExceptionHandler], so a failure that nothing else caught is logged and
         *   ends there instead of reaching the default handler, which kills the process. The
         *   process here is the one drawing the wallpaper, and Android answers its death by
         *   replacing the live wallpaper with the static system image -- an outcome the user
         *   cannot undo from inside the app, because the app has crashed too.
         *
         * This is a backstop, not a substitute for handling errors where they happen: the three
         * preference stores each recover on their own, and this exists so the *next* collector
         * somebody adds cannot repeat the same failure.
         */
        private val engineJob = SupervisorJob()
        private val engineExceptionHandler = CoroutineExceptionHandler { _, error ->
            Log.e(
                TAG,
                "Engine coroutine failed; the wallpaper keeps drawing on the state it already has",
                error,
            )
        }
        private val scope = CoroutineScope(Dispatchers.Main + engineJob + engineExceptionHandler)
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

        /**
         * Today's sunrise, sunset and whether they came from a real position (**P2-6**).
         *
         * These were three plain fields — `sunriseHour`, `sunsetHour`, `hasFixLocation` — written
         * here on the main thread and read by [renderScene] on the *render* thread, with nothing
         * ordering the two. One `@Volatile` reference to an immutable [SolarDay] replaces them, so
         * the reader gets the visibility edge and the three values arrive as one. See [SolarDay]
         * for why three `@Volatile` fields would have fixed only half of it.
         */
        @Volatile
        private var solarDay: SolarDay = SolarDay.NONE

        /** Which day and UTC offset [solarDay] was worked out for. See [solarDayIsStale]. */
        private var solarDayStamp: Long = Long.MIN_VALUE

        /**
         * When the last weather fetch was attempted, on the **monotonic** clock.
         *
         * `System.currentTimeMillis()` was the wrong clock for a schedule: moving the device's
         * clock backwards -- a timezone edit, an NTP correction, a user setting the date -- made
         * `now - lastFetch` negative, so no fetch was ever due again until the wall clock caught
         * back up. `elapsedRealtime` counts since boot, includes deep sleep, and cannot go
         * backwards. The snapshot's own `fetchedAtMillis` stays on the wall clock, because that one
         * is a *timestamp* and has to survive being compared with dates.
         */
        private var lastWeatherFetchElapsed: Long = Long.MIN_VALUE / 4
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
         * The newest snapshot the loop holds, whether or not the scene is currently drawing it.
         *
         * The loop used to ask the renderer this (`renderer?.liveWeatherOverride != null`), which
         * read render-thread-owned state from the main thread and, worse, made the renderer the
         * memory of what had been fetched. Now the engine remembers and the renderer is only ever
         * told what [LiveWeatherSchedule.decide] authorises -- so "what we have" and "what may be
         * drawn" stop being the same variable.
         */
        @Volatile
        private var lastWeatherSnapshot: LiveWeatherSnapshot? = null

        /**
         * How many transient failures in a row, feeding [LiveWeatherSchedule.nextAttemptDelayMillis].
         *
         * Reset to zero by any outcome that is not a transient failure, which is what returns the
         * loop to its normal hourly cadence after one success.
         */
        @Volatile
        private var weatherTransientFailures = 0

        /**
         * What the renderer was last told, so an unchanged decision costs nothing.
         *
         * Main-thread only: written and read by the weather loop alone, unlike the renderer's own
         * field which the render thread owns.
         */
        private var appliedLiveWeather: LiveWeatherSnapshot? = null

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
         * Exists because [solarDay]'s `hasFix` alone cannot answer "is this fix still the right
         * kind" -- see the collector for the bug that produced.
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

        /**
         * Asks the render thread to drop its GPU textures. Safe to call before it exists.
         *
         * Not a queued event: the trim is GL work, and queued events are drained at a point in the
         * loop where no context is guaranteed to be current. [GlRenderThread.requestTrim] hands it
         * to the one place that has one.
         */
        fun trimGpuResources() {
            glThread?.requestTrim()
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
                themeCustomizations = settings.themeCustomizations,
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
            // **No `setTouchEventsEnabled(true)`, deliberately.** It was here for a tap-to-summon-a-
            // bird gesture that no longer exists; nothing overrides `onTouchEvent` or `onCommand`,
            // so every event the system was dispatching to this engine was being discarded on
            // arrival. Asking the window manager to deliver touches to a wallpaper that ignores
            // them costs IPC on every finger movement over the home screen and buys nothing. Turn
            // it back on in the same change that adds a handler, not before.
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
                        lastWeatherFetchElapsed = Long.MIN_VALUE / 4
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
                    // **A fix belongs to the source it came from.** The "we have a fix" flag (now
                    // [solarDay]'s `hasFix`) used to be set by both the GPS and the custom paths, so
                    // switching Custom -> Phone found it already true, returned from
                    // maybeStartLocationUpdates without ever
                    // starting the provider, and left `lastLocationFix` holding the *custom*
                    // coordinates -- measured on a Pixel 9, where selecting Phone kept fetching
                    // Florence's weather. Treating a source change as an invalidation is what
                    // makes the two sources actually exclusive at runtime and not only in prefs.
                    val requestedSource = LocationSource.of(newSettings)
                    if (requestedSource != locationSource) {
                        locationSource = requestedSource
                        solarDay = SolarDay.NONE
                        lastLocationFix = null
                        // The conditions on screen are the old source's. Nothing about them is
                        // worth keeping, so the next pass must fetch rather than compare.
                        lastWeatherFetchLocation = null
                        lastWeatherFetchElapsed = Long.MIN_VALUE / 4
                    }
                    if (newSettings.useLocationForSunTimes) {
                        // A fix is asked for here and nowhere else on a timer: the weather loop
                        // asks again when it is about to fetch, and that is the only other time.
                        if (!solarDay.hasFix) launch { refreshDeviceFix(requestedSource) }
                    } else {
                        solarDay = SolarDay.NONE
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
                        solarDay = SolarDay.NONE
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
                    // **The only recurring reason to ask the device where it is.**
                    //
                    // A refresh is due, so the position behind it might be stale -- and this is
                    // the one moment in the app's life when a new fix is worth what it costs.
                    // `currentFix` still prefers a cached answer, so most of these cost nothing at
                    // all; a fix is only actually requested when the system's own cache has gone
                    // stale too, which puts an upper bound of one request per refresh interval.
                    val source = LocationSource.of(settings)
                    // How long this pass must have waited before another attempt is allowed: the
                    // normal hourly interval, or a bounded backoff while transient failures are
                    // running (see LiveWeatherSchedule.nextAttemptDelayMillis for the ladder).
                    val attemptDelay = LiveWeatherSchedule.nextAttemptDelayMillis(
                        consecutiveTransientFailures = weatherTransientFailures,
                        normalIntervalMillis = WEATHER_REFRESH_INTERVAL_MS,
                    )
                    if (settings.liveWeatherEnabled && source.deviceKind != null &&
                        LiveWeatherSchedule.isAttemptDue(SystemClock.elapsedRealtime() - lastWeatherFetchElapsed, attemptDelay)
                    ) {
                        refreshDeviceFix(source)
                    }
                    val fix = lastLocationFix
                    // Two reasons to fetch, not one. The hourly timer is about the conditions
                    // going stale; a *different place* is about them being the wrong conditions
                    // entirely, and no amount of waiting fixes that. Moving the custom location
                    // used to leave the scene showing the old town's weather for the rest of the
                    // hour, because the timer was the only gate.
                    val movedSinceLastFetch = fix != null && fix != lastWeatherFetchLocation
                    val timerExpired = LiveWeatherSchedule.isAttemptDue(SystemClock.elapsedRealtime() - lastWeatherFetchElapsed, attemptDelay)
                    val provider = settings.weatherProvider
                    // The outcome of a fetch made on *this* pass, or null when none was due --
                    // the meaning LiveWeatherStatus.of already gives the parameter.
                    var result: WeatherFetchResult? = null
                    if (settings.liveWeatherEnabled && fix != null && (movedSinceLastFetch || timerExpired)) {
                        lastWeatherFetchElapsed = SystemClock.elapsedRealtime()
                        lastWeatherFetchLocation = fix
                        result = WeatherRepository.fetchCurrentConditions(
                            providerId = provider,
                            latitude = fix.latitude,
                            longitude = fix.longitude,
                            apiKey = settings.apiKeyForWeatherProvider,
                        )
                        // A failure leaves the *previous* snapshot in place rather than clearing
                        // it, so one dropped request doesn't momentarily revert the scene to the
                        // theme's manual precipitation/clouds; it keeps showing the last
                        // known-good conditions until the next successful fetch -- now for at most
                        // LiveWeatherSchedule.SNAPSHOT_MAX_AGE_MILLIS, after which conditions
                        // nobody can vouch for stop being drawn.
                        WeatherRepository.snapshotOf(result)?.let { lastWeatherSnapshot = it }
                        // Only a transient failure earns a faster retry. A missing or rejected key
                        // and a spent quota are answers, not accidents: nothing changes by asking
                        // again sooner, so the normal interval stands and the status says what is
                        // wrong. Any non-transient outcome, success included, returns the loop to
                        // its normal cadence.
                        weatherTransientFailures = if (LiveWeatherSchedule.isTransient(result)) {
                            weatherTransientFailures + 1
                        } else {
                            0
                        }
                    }
                    // **The single decision.** Every pass, fetch or no fetch, asks the same
                    // question of the same inputs and gets both halves of the answer at once, so
                    // the status the settings screen reads and the snapshot the renderer draws
                    // cannot disagree -- see LiveWeatherSchedule.decide.
                    val decision = LiveWeatherSchedule.decide(
                        enabled = settings.liveWeatherEnabled,
                        hasLocation = fix != null,
                        result = result,
                        snapshot = lastWeatherSnapshot,
                        nowMillis = System.currentTimeMillis(),
                        previous = publishedWeatherStatus ?: LiveWeatherStatus.OFF,
                    )
                    if (!settings.liveWeatherEnabled) {
                        // Switching the feature off forgets what was fetched, rather than merely
                        // declining to draw it: the next time it is switched on the user expects a
                        // fresh look at the sky, and the immediate refresh that follows depends on
                        // the timer being clear.
                        lastWeatherSnapshot = null
                        lastWeatherFetchLocation = null
                        lastWeatherFetchElapsed = Long.MIN_VALUE / 4
                        weatherTransientFailures = 0
                    }
                    applyLiveWeather(decision.snapshotForScene)
                    publishWeatherStatus(decision.status)
                    // Waits for the tick *or* for a settings change, whichever comes first.
                    withTimeoutOrNull(WEATHER_CHECK_INTERVAL_MS) { weatherWakeUp.receive() }
                }
            }
        }

        /**
         * A surface has arrived: the first one this engine ever gets, or a replacement.
         *
         * **The scene survives the surface.** The renderer holds no GL objects -- those live in the
         * render thread's [GlSceneTarget] -- so a replacement surface reuses it and only updates
         * its size. Rebuilding it would throw away the scroll position, the animation phase and the
         * live-weather override for a window that came back a moment later, and, now that the
         * render thread outlives the surface, would also mean publishing a new renderer to a thread
         * already drawing with the old one. Keeping it makes both problems go away.
         */
        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            val frame = holder.surfaceFrame
            val existing = renderer
            if (existing == null) {
                // Built on the main thread before the render thread starts. `Thread.start()`
                // establishes the happens-before that publishes it safely; every mutation after
                // that point goes through onRenderThread.
                renderer = PaperRenderer(frame.width(), frame.height(), applicationContext).apply {
                    parallaxStrength = settings.parallaxStrength
                    scrollBackground = settings.scrollBackground
                    swipeScrollEnabled = settings.swipeScroll
                    scrollSpeed = settings.scrollSpeed
                }
                applyEffectiveTheme()
            } else {
                // The size is applied by whoever owns the renderer: the render thread reports it
                // through onGlSurfaceChanged once the viewport is set, so touching it from here
                // would be the main thread writing scene state under a live frame loop.
                if (glThread == null) existing.onSizeChanged(frame.width(), frame.height())
            }
            when (GlLifecyclePolicy.surfaceCreated(hasThread = glThread != null, canvasFallback = canvasFallback)) {
                GlLifecyclePolicy.SurfaceAction.START_THREAD -> startGlThread(holder)
                GlLifecyclePolicy.SurfaceAction.REUSE_THREAD -> attachSurfaceToGlThread(holder)
                GlLifecyclePolicy.SurfaceAction.NO_GL -> {
                    // The Canvas loop draws this engine. It was stopped when the surface went away
                    // (see onSurfaceDestroyed); a new surface is what restarts it.
                    if (visible) {
                        lastFrameNanos = System.nanoTime()
                        handler.post(drawRunnable)
                    }
                }
            }
        }

        private fun startGlThread(holder: SurfaceHolder) {
            val thread = GlRenderThread(glCallbacks)
            glThread = thread
            thread.start()
            attachSurfaceToGlThread(holder)
        }

        /**
         * Hands a surface to the thread that already owns this engine's GL.
         *
         * The thread keeps its EGL context across the gap and rebuilds only the EGL surface, which
         * is what its idle branch was written for and what stops a destroy/create cycle from
         * costing a thread, a context and every uploaded texture.
         */
        private fun attachSurfaceToGlThread(holder: SurfaceHolder) {
            val thread = glThread ?: return
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

        /**
         * The surface is going away, but this engine is not.
         *
         * The render thread is deliberately **not** stopped: it owns this engine's GL for the
         * engine's whole life and parks with its context intact until a surface comes back. What
         * must stop is any drawing into a window that no longer exists -- the Canvas fallback's
         * self-rescheduling frame callback kept calling `lockCanvas` on a dead surface at frame
         * cadence, because until now only visibility-false and engine-destroy removed it.
         */
        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            glThread?.onSurfaceDestroyed()
            handler.removeCallbacks(drawRunnable)
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
            // Nothing to unsubscribe from: since v3.0 a position is asked for once and the request
            // ends with itself, so cancelling the engine's job is the whole of the teardown.
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

            // One read of one reference, so the two hours below provably belong to the same fix.
            // SolarDay.NONE already carries the 6/20 defaults this used to substitute here.
            val today = solarDay
            val dayPhase = SunPositionCalculator.compute(
                hour24 = hour,
                sunriseHour = today.sunriseHour,
                sunsetHour = today.sunsetHour,
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
            // **Only the wallpaper writes the status; the preview reads it.**
            //
            // Every engine runs its own weather loop, and while the settings screen is open there
            // are two: the one drawing the home screen and the one drawing the preview card. Both
            // used to write `liveWeatherStatus`, so whichever ticked last won -- and the two do not
            // necessarily agree, because they hold separate snapshots and separate retry counters.
            // A preview that has just started and failed its first fetch would publish FAILED over
            // the home engine's OK, and the settings screen would then describe a scene that was
            // drawing real conditions perfectly well.
            //
            // The preview keeps fetching, so what it *draws* is unchanged; it simply stops being an
            // author of the status the UI reads.
            if (isPreview) return
            scope.launch { prefs.setLiveWeatherStatus(status) }
        }

        /**
         * Hands the renderer exactly what [LiveWeatherSchedule.decide] authorised, and nothing else.
         *
         * Null means "draw the theme's own weather", which is the same valid scene Live Weather
         * shows when it is off. Only on a change, for the same reason [publishWeatherStatus] is:
         * this runs on the two-minute tick, and re-posting an identical override would queue a
         * render-thread event and a redraw every tick for no visible difference.
         */
        private fun applyLiveWeather(snapshot: LiveWeatherSnapshot?) {
            if (appliedLiveWeather == snapshot) return
            appliedLiveWeather = snapshot
            onRenderThread {
                renderer?.liveWeatherOverride = snapshot
                requestRedraw()
            }
        }

        /**
         * Asks the device where it is, once, and only when something needs the answer.
         *
         * There is no subscription any more (see [DeviceLocationProvider]), so this is called at
         * the two moments a position actually matters: when the source changes, and when a weather
         * refresh is due. In between, nothing wakes the positioning stack at all.
         *
         * When the provider cannot answer -- permission refused, radio off, no signal -- the last
         * saved fix is used instead. That is the whole fallback: a town does not move, and last
         * hour's coordinates give a far better scene than a default somewhere else.
         */
        private suspend fun refreshDeviceFix(source: LocationSource) {
            val kind = source.deviceKind ?: return
            val fix = deviceFix(kind)
            if (fix != null) {
                updateSunTimesFromLocation(fix, isDeviceFix = true)
                return
            }
            // Nothing new. Fall back to the saved position, but only once -- if a fix is already
            // held there is nothing to restore, unless the day itself has turned over.
            if (solarDay.hasFix && !solarDayIsStale()) return
            val saved = savedDeviceFix()
            if (saved != null) updateSunTimesFromLocation(saved, isDeviceFix = false)
        }

        /**
         * The position saved by the last successful fix, if there is one.
         *
         * Deliberately has no expiry. An old fix is still a place, and the alternative when the
         * provider is unavailable is not a better position -- it is no position, and a scene that
         * silently stops following the weather.
         */
        private fun savedDeviceFix(): DeviceLocationFix? {
            val latitude = settings.resolvedGpsLatitude ?: return null
            val longitude = settings.resolvedGpsLongitude ?: return null
            return DeviceLocationFix(latitude.toDouble(), longitude.toDouble())
        }

        /**
         * Whether the sunrise/sunset held were worked out for a day that is now over.
         *
         * They were computed once per location and then never again, so a wallpaper left running
         * kept yesterday's sunrise indefinitely -- a few minutes out after a week, and an hour out
         * across a DST change, which is exactly when the scene's own clock disagrees most visibly
         * with the sky outside. The day *and* the UTC offset are both compared, because a DST
         * change moves the offset without moving the date.
         *
         * Read on the weather loop's existing two-minute tick, so this adds no timer of its own.
         */
        private fun solarDayIsStale(): Boolean =
            solarDayStamp != currentSolarStamp()

        private fun currentSolarStamp(): Long {
            val calendar = Calendar.getInstance()
            val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR).toLong()
            val year = calendar.get(Calendar.YEAR).toLong()
            val offsetMinutes = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000L
            return (year * 1000L + dayOfYear) * 10_000L + offsetMinutes
        }

        private fun updateSunTimesFromLocation(fix: DeviceLocationFix, isDeviceFix: Boolean = false) {
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
            // Published as one object, not three stores: see SolarDay.
            solarDay = SolarDay.located(sunrise, sunset)
            solarDayStamp = currentSolarStamp()
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
            // Only a *fresh device* fix is persisted. The custom-location path builds its fix
            // straight from settings.customLocationLatitude/Longitude, which Settings already has
            // without any round trip through this service; and a fix restored from the cache must
            // not be written back, or its timestamp would keep renewing itself and the saved
            // position would always look as if it had just been taken.
            if (isDeviceFix) {
                scope.launch { prefs.setResolvedGpsLocation(fix.latitude.toFloat(), fix.longitude.toFloat()) }
            }
        }
    }
}
