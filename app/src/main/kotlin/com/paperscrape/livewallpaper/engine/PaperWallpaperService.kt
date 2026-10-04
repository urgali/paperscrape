package com.paperscrape.livewallpaper.engine

import android.graphics.Canvas
import android.os.Handler
import android.os.SystemClock
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import com.paperscrape.livewallpaper.BuildConfig
import com.paperscrape.livewallpaper.icon.SeasonalIconController
import com.paperscrape.livewallpaper.location.DeviceFixAnswer
import com.paperscrape.livewallpaper.location.DeviceLocationAccess
import com.paperscrape.livewallpaper.location.DeviceLocationFix
import com.paperscrape.livewallpaper.location.DeviceLocationKind
import com.paperscrape.livewallpaper.location.DeviceLocationProvider
import com.paperscrape.livewallpaper.location.LocationRequestThrottle
import com.paperscrape.livewallpaper.location.LocationSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.paperscrape.livewallpaper.prefs.CustomThemeStore
import com.paperscrape.livewallpaper.prefs.BackupRepository
import com.paperscrape.livewallpaper.prefs.WallpaperPrefs
import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import com.paperscrape.livewallpaper.update.ApkDownloader
import com.paperscrape.livewallpaper.update.UpdateCheckResult
import com.paperscrape.livewallpaper.update.UpdateChecker
import com.paperscrape.livewallpaper.update.UpdateNotificationPolicy
import com.paperscrape.livewallpaper.update.UpdateNotifier
import com.paperscrape.livewallpaper.update.UpdatePrefs
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
// waiting up to an hour, while the actual network call fires once per
// [WEATHER_REFRESH_INTERVAL_MS] in steady state -- sooner only after a transient failure (see
// LiveWeatherSchedule.nextAttemptDelayMillis), a changed weather setting or a moved location.
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
     * [DeviceLocationProvider.FRESH_ENOUGH_MS] and returns it without touching the radio -- and since
     * v5.10D a real request is made only when the process's one throttle allows it
     * ([com.paperscrape.livewallpaper.location.LocationRequestThrottle]: an hour after a request that
     * brought a position; 5, 15 and 30 minutes after the first, second and third in a row that did not,
     * then the hour), unless [force] says the user has just chosen this source.
     */
    private suspend fun deviceFix(kind: DeviceLocationKind, force: Boolean): DeviceFixAnswer =
        locationRequestLock.withLock { locationProvider().currentFix(kind, forceRequest = force) }

    /** What the phone allows for [kind] now: permission and location switch (v5.10D). No radio. */
    private fun deviceAccess(kind: DeviceLocationKind): DeviceLocationAccess = locationProvider().access(kind)

    private fun locationProvider(): DeviceLocationProvider =
        (sharedLocationProvider ?: DeviceLocationProvider(applicationContext)).also { sharedLocationProvider = it }

    private fun onEngineVisibilityChanged(nowVisible: Boolean, wasVisible: Boolean) {
        if (nowVisible == wasVisible) return
        visibleEngineCount = (visibleEngineCount + if (nowVisible) 1 else -1).coerceAtLeast(0)
    }

    /**
     * Keeps the launcher icon on the season the date is in.
     *
     * On the service and not on the engine because a wallpaper process commonly runs two engines
     * at once -- the home screen's and the picker's preview -- and one date deserves one receiver
     * and one answer, not two of each racing to write the same component states.
     */
    private var iconController: SeasonalIconController? = null

    override fun onCreate() {
        super.onCreate()
        iconController = SeasonalIconController(applicationContext).also { it.start() }
    }

    override fun onDestroy() {
        iconController?.stop()
        iconController = null
        super.onDestroy()
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
        if (MemoryPressurePolicy.dropsGpuTextures(action)) {
            // The GPU copies are derived from the bitmaps above, but the atlas has no partial mode:
            // dropping it is all or nothing, so it does *not* follow the same policy. See
            // MemoryPressurePolicy.dropsGpuTextures (ARC-11). Each engine drops its own, on its own
            // render thread.
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
         * This is a backstop, not a substitute for handling errors where they happen: the preference
         * stores (four since v5.10D) each recover on their own, and this exists so the *next* collector
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

        /** This engine's first home-screen offset, and whether a later one moved from it (v5.10E). */
        private val swipeReport = SwipeReport()

        /** Whether this engine has asked for `swipe_reported` to be written. Main thread. */
        private var swipeReportSent = false
        // ARC-12: this said "written on the render thread by the settings collector", and the
        // collector runs in `scope`, which is `Dispatchers.Main`. It is written on the **main**
        // thread and read by the weather loop, which is another coroutine on that same dispatcher
        // but not necessarily the same continuation, and by callbacks that are not. The volatile is
        // what makes the write visible wherever it is read; what was wrong was the thread named.
        @Volatile
        private var settings: WallpaperSettings = WallpaperSettings()
        private var visible = false
        private var lastFrameNanos = System.nanoTime()
        private var elapsedSeconds = SceneTime.ZERO

        /** This engine's day phase, rebuilt only when its inputs change; read by [renderScene]. */
        private val dayPhaseCache = DayPhaseCache()

        /**
         * The GPU render thread, or null once the `Canvas` fallback has taken over.
         *
         * While it exists it owns both the EGL context and, by convention, every read and write of
         * the scene state: see [onRenderThread].
         */
        private var glThread: GlRenderThread? = null

        /**
         * Set when GL is given up on: EGL never initialised, or a context that worked failed past
         * [GlLifecyclePolicy.MAX_CONTEXT_REBUILDS] rebuilds (see [switchToCanvasFallback]).
         * From then on the engine drives the `Canvas` path from the main looper, exactly as it did
         * before the GPU backend existed.
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
         * The coordinates the last weather fetch was *attempted* for -- stamped before the request,
         * like [lastWeatherFetchElapsed], whatever its outcome.
         *
         * The refresh timer answers "are these conditions stale"; this answers "was the last fetch
         * for the place we are actually showing". Only the second one changes when the
         * user edits their custom location, and until it existed the first was the only gate.
         * A failed fetch for a new place records it here too -- the retry ladder, not this field,
         * decides when to ask again -- and [LiveWeatherSchedule.heldAfterFetch] drops the previous
         * place's conditions ([lastWeatherSnapshotLocation]) so they do not go on drawing.
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

        /** Where [lastWeatherSnapshot] was fetched for (v5.8C): see [LiveWeatherSchedule.heldAfterFetch]. */
        @Volatile
        private var lastWeatherSnapshotLocation: DeviceLocationFix? = null

        /**
         * How many transient failures in a row, feeding [LiveWeatherSchedule.nextAttemptDelayMillis].
         *
         * Reset to zero by any outcome that is not a transient failure, which is what returns the
         * loop to its normal hourly cadence after one success.
         */
        @Volatile
        private var weatherTransientFailures = 0

        /**
         * When the last **update** check was attempted, on the monotonic clock (v5.7D, A0).
         *
         * The same field, the same sentinel and the same clock as [lastWeatherFetchElapsed], for the
         * same reason: a wall clock moved backwards once froze the weather loop until it caught up,
         * and a schedule that can be frozen by a timezone edit is not a schedule. `elapsedRealtime`
         * counts since boot, includes deep sleep and cannot go backwards.
         *
         * The sentinel is what makes the *first* pass of a freshly-bound engine due immediately, so
         * a user who has just set the wallpaper is told about a waiting release now rather than
         * in three hours. It resets on every rebind, which is the honest cost of keeping the schedule
         * in the engine: a device that rebinds its wallpaper several times a day checks at each
         * rebind. Bounded above by one check per rebind and one every three hours otherwise, plus the
         * retry ladder while checks come back Unreachable (see [updateCheckTransientFailures]).
         */
        private var lastUpdateCheckElapsed: Long = Long.MIN_VALUE / 4

        /**
         * How many update checks in a row came back [UpdateCheckResult.Unreachable].
         *
         * Feeds [UpdateNotificationPolicy.checkDue], which runs the *same*
         * [LiveWeatherSchedule.nextAttemptDelayMillis] ladder the weather loop uses with
         * [UpdateNotificationPolicy.UPDATE_CHECK_INTERVAL_MILLIS] (three hours since v5.10D) as the
         * normal interval instead of the hourly one. From two minutes it doubles to the three-hour
         * cap: seven retries in the first 4 h 14 min of being offline (2, 6, 14, 30, 62, 126, 254 minutes),
         * then one every three hours (`UpdateCheckCadenceTest`).
         */
        private var updateCheckTransientFailures = 0

        /**
         * The snooze and notified-tag store, built once with the engine rather than per pass.
         *
         * Backed by the same DataStore instance the settings screen uses in this process (the
         * `updateDataStore` delegate is one per process, whichever `UpdatePrefs` wraps it), so a
         * snooze written from the dialog is visible to the loop without a restart -- a DataStore
         * reads its own writes, and only its own.
         */
        private val updatePrefs by lazy { UpdatePrefs(applicationContext) }

        /**
         * What the renderer was last told, so an unchanged decision costs nothing.
         *
         * Main-thread only: written and read by the weather loop alone, unlike the renderer's own
         * field which the render thread owns.
         */
        private var appliedLiveWeather: LiveWeatherSnapshot? = null

        /** Whether the renderer was last told the conditions had aged out; see [applyLiveWeather]. */
        private var appliedLiveWeatherLapsed: Boolean = false

        /**
         * What the settings screen was last told about Live Weather's fallback state.
         *
         * Kept so the status is written only when it changes: every write re-emits the settings
         * flow, and the weather loop evaluates every two minutes.
         */
        @Volatile
        private var publishedWeatherStatus: LiveWeatherStatus? = null

        /**
         * Counts [forgetPublishedWeatherStatus] (v5.10C): a pass whose fetch began before the inputs
         * changed -- a request with the old key still in flight while the user typed a new one --
         * publishes nothing, or its answer about the old key would land after the forgetting and
         * stand until the wallpaper is next on screen. Main thread, like every field of the loop.
         */
        private var weatherInputsGeneration = 0

        /**
         * Which location source -- GPS, network, Custom, or none -- the current [lastLocationFix]
         * came from.
         *
         * Exists because [solarDay]'s `hasFix` alone cannot answer "is this fix still the right
         * kind" -- see the collector for the bug that produced.
         */
        private var locationSource: LocationSource = LocationSource.NONE

        /**
         * The user has just picked GPS or Network, and the phone is asked at once, past the throttle's wait
         * (v5.10D, [com.paperscrape.livewallpaper.location.LocationRequestThrottle]), until it has really
         * been consulted for that choice ([DeviceFixAnswer.consulted]): a choice made with the location
         * switched off is answered when it comes on. Set only by a change after this engine's first
         * settings ([SolarDaySchedule.userChoseSource]; the read-only review of v5.10D found it set by
         * every engine start). Main thread, like the collector that sets it and [refreshDeviceFix],
         * which spends it.
         */
        private var userChoseDeviceSource = false

        /** Whether the settings collector has applied this engine's first settings. Main thread. */
        private var settingsApplied = false
        private var lastAppliedThemeId = "sunset"
        private var lastAppliedCustomization: SceneCustomization = SceneCustomization.DEFAULT

        private val drawRunnable = Runnable { drawFrame() }

        /**
         * No frame until the user's settings and saved themes have reached the scene (v5.10B): an
         * engine used to draw its first frames from a fresh install's settings -- Sunset -- and the
         * user's theme a tenth of a second later. See [FirstFrameGate]. Every place that starts
         * drawing asks [drawing] rather than [visible].
         */
        private val firstFrameGate = FirstFrameGate { onFirstFrameAllowed() }
        private val firstFrameWaitLimit = Runnable { firstFrameGate.waitedTooLong() }

        /** Whether this engine draws now: on screen, and past its [firstFrameGate]. */
        private val drawing: Boolean get() = visible && firstFrameGate.isOpen

        /** The gate has opened: start the frames it was holding, on whichever backend draws. */
        private fun onFirstFrameAllowed() {
            val thread = glThread
            if (thread != null) {
                thread.setVisible(drawing)
            } else if (drawing && renderer != null) {
                lastFrameNanos = System.nanoTime()
                handler.post(drawRunnable)
            }
        }

        /**
         * Runs [action] on whichever thread currently owns the scene state.
         *
         * With the GPU backend that is the render thread, so the update is queued and lands between
         * two frames; on the `Canvas` fallback the main looper owns it and the update runs inline.
         * Every path that mutates the renderer from a coroutine or a system callback either goes
         * through here or makes the same thread choice inline (onVisibilityChanged,
         * onSurfaceCreated, onSurfaceChanged, switchToCanvasFallback), which is what keeps the
         * scene single-threaded despite the draw having moved off the main thread — the
         * alternative, a lock around the renderer, would put every settings write in contention
         * with the frame loop.
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
        private fun applyEffectiveTheme(): Boolean {
            val effectiveId = if (settings.autoThemeByDate) {
                SeasonalThemeRules.themeForDate(calendar = settings.seasonalCalendar)
                    ?: settings.themeId
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
            // What the settings screen's top button reads to say whether PaperScrape is the
            // wallpaper: an engine that is not a preview exists only once the system has attached it.
            WallpaperEngineCensus.engineCreated(isPreview)
            handler.postDelayed(firstFrameWaitLimit, FirstFrameGate.WAIT_LIMIT_MS)
            // **No `setTouchEventsEnabled(true)`, deliberately.** It was here for a tap-to-summon-a-
            // bird gesture that no longer exists; nothing overrides `onTouchEvent` or `onCommand`,
            // so every event the system was dispatching to this engine was being discarded on
            // arrival. Asking the window manager to deliver touches to a wallpaper that ignores
            // them costs IPC on every finger movement over the home screen and buys nothing. Turn
            // it back on in the same change that adds a handler, not before.
            prefs = WallpaperPrefs(applicationContext)
            val customThemeStore = CustomThemeStore(applicationContext)
            // An update installed from the app restarts this process, and the wallpaper's engine is
            // the first thing to come back: the APK it was installed from has no further use. See
            // ApkDownloader.pruneInstalled (v5.10B).
            if (!isPreview) {
                scope.launch(Dispatchers.IO) {
                    runCatching { ApkDownloader.pruneInstalled(applicationContext, BuildConfig.VERSION_NAME) }
                }
            }
            scope.launch {
                // BCK-06. An import writes two stores and a process kill between them used to leave
                // the preferences new and the saved themes old. The pending document is applied here
                // before the collector below can observe the half-applied state -- and before this
                // engine draws a scene from it. Costs one preference read on every start but the
                // one after a kill, where it costs the write that was owed.
                runCatching { BackupRepository(prefs, customThemeStore, "").finishPendingImport() }
                customThemeStore.dataFlow.collect { data ->
                    onRenderThread {
                        CustomThemeRegistry.update(data)
                        // An override/reset/delete can change what the *current* themeId resolves
                        // to even though themeId itself didn't change -- re-apply and redraw.
                        if (applyEffectiveTheme()) requestRedraw()
                    }
                    // After the update is queued, so the render thread applies it before the frame
                    // the gate lets through.
                    firstFrameGate.savedThemesArrived()
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
                        forgetPublishedWeatherStatus()
                        weatherWakeUp.trySend(Unit)
                    }
                    // The user moving a window on the Seasons screen moves the icon with it:
                    // both read the same calendar, which is the point of reading it from there.
                    iconController?.onCalendarChanged(newSettings.seasonalCalendar)
                    onRenderThread {
                        val changed = applyEffectiveTheme()
                        renderer?.parallaxStrength = newSettings.parallaxStrength
                        renderer?.scrollBackground = newSettings.scrollBackground
                        renderer?.swipeScrollEnabled = newSettings.swipeScroll
                        renderer?.scrollSpeed = newSettings.scrollSpeed
                        if (changed) requestRedraw()
                    }
                    // After the update is queued, as with the saved themes above.
                    firstFrameGate.settingsArrived()
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
                        // A source the user has just chosen is answered at once, whatever the wait
                        // says (`LocationRequestThrottle`), until the phone has really been consulted
                        // for it -- with a fixed hour, once real time comes back. Not the engine's own
                        // start, which "changes" the source from none (SolarDaySchedule.userChoseSource).
                        userChoseDeviceSource = SolarDaySchedule.userChoseSource(settingsApplied, locationSource, requestedSource)
                        locationSource = requestedSource
                        solarDay = SolarDay.NONE
                        lastLocationFix = null
                        // The conditions on screen are the old source's. Nothing about them is
                        // worth keeping, so the next pass must fetch rather than compare.
                        lastWeatherFetchLocation = null
                        lastWeatherFetchElapsed = Long.MIN_VALUE / 4
                        // And "no position" was the old source's answer, not this one's.
                        forgetPublishedWeatherStatus()
                    }
                    if (newSettings.useLocationForSunTimes) {
                        // A fix is asked for here and by the weather loop (when a refresh is due, or
                        // while none is held), never on a timer of its own. **Not with a fixed hour**
                        // (v5.10D, row 6): no position counts then, and the Location row is grey.
                        if (!solarDay.hasFix && newSettings.syncWithRealTime) launch { refreshDeviceFix(requestedSource) }
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
                    settingsApplied = true
                }
            }
            // Live Weather (Phase 1d point 6): checks every WEATHER_CHECK_INTERVAL_MS, while
            // visible, whether a fetch is due, and calls WeatherRepository once per
            // WEATHER_REFRESH_INTERVAL_MS in steady state -- immediately when a location first
            // becomes available or moves while Live Weather is on, and sooner after a transient
            // failure -- see those constants' own doc comments.
            scope.launch {
                while (true) {
                    // **ARC-02: an invisible engine does not poll.**
                    //
                    // This loop used to tick every WEATHER_CHECK_INTERVAL_MS for the whole life of
                    // the engine, screen off included, and a wallpaper process can host two engines
                    // at once -- the picker's preview alongside the live one -- so the ticks and the
                    // hourly fetch behind them were doubled whenever the picker was open.
                    //
                    // Parking on the channel with **no timeout** is what removes the polling rather
                    // than lengthening it: nothing wakes an invisible engine except a settings
                    // change, a location fix arriving or [onVisibilityChanged], all of which
                    // already send here. Coming back visible therefore re-enters the body
                    // immediately and the due-check runs at once, so a refresh that fell due while
                    // the screen was off is picked up on the first frame instead of up to two
                    // minutes later -- the loop got *more* responsive, not less.
                    if (!visible) {
                        weatherWakeUp.receive()
                        continue
                    }
                    // **The recurring reasons to ask the device where it is**: a weather refresh is
                    // due, so the position behind it might be stale; or none is held at all.
                    //
                    // `currentFix` prefers the system's cached answer, so most of these cost nothing
                    // at all, and **a real request is made at most once an hour while the phone
                    // answers**, whoever asks (`LocationRequestThrottle`, v5.10D). This comment used to
                    // promise "an upper bound of one request per refresh interval in steady state", and
                    // the bound did not hold: during a weather outage the passes came every 2, 4, 8
                    // minutes and each asked again once the cache was fifteen minutes old, and with no
                    // position ever received every two-minute pass searched for up to 20 s (v5.10A,
                    // inventory I-282). **A request that finds nothing is tried again after 5, 15 and
                    // 30 minutes, then the hour, held position or not** (v5.10E, the maintainer's
                    // decision of 2026-10-04): the third reason to ask, `deviceRetryDue`, and the
                    // shorter wait at the bottom of the loop. What the phone allows is read on every
                    // pass, from the phone (permission and location switch, no radio): without the
                    // permission no position from it is kept or asked for
                    // (`DeviceLocationAccess.mayUsePosition`), and no try is counted.
                    val source = LocationSource.of(settings)
                    val deviceAccess = source.deviceKind?.let { deviceAccess(it) }
                    // Live Weather runs only over a scene that follows real time: a fixed hour has
                    // no "now" to fetch for, which is what the settings screen has always said
                    // (`LiveWeatherUiState.canBeTurnedOn`). Until v5.8C this loop never checked it,
                    // so with a location set a frozen clock went on fetching and driving the sky
                    // while the screen said "Not running" and left the weather controls editable
                    // (v5.8B comment audit).
                    val liveWeatherRuns = LiveWeatherSchedule.runs(settings.liveWeatherEnabled, settings.syncWithRealTime)
                    // How long this pass must have waited before another attempt is allowed: the
                    // normal hourly interval, or a bounded backoff while transient failures are
                    // running (see LiveWeatherSchedule.nextAttemptDelayMillis for the ladder).
                    val attemptDelay = LiveWeatherSchedule.nextAttemptDelayMillis(
                        consecutiveTransientFailures = weatherTransientFailures,
                        normalIntervalMillis = WEATHER_REFRESH_INTERVAL_MS,
                    )
                    // And the sunrise/sunset: asked on every pass, whatever the source and whether or
                    // not Live Weather is on -- see SolarDaySchedule for the defect that inline
                    // condition used to be (Custom, or Live Weather off, never recomputed at all).
                    when (SolarDaySchedule.onTick(
                        followRealTime = settings.syncWithRealTime,
                        liveWeatherEnabled = liveWeatherRuns,
                        deviceSource = source.deviceKind != null,
                        devicePositionUsable = deviceAccess?.mayUsePosition == true,
                        weatherAttemptDue = LiveWeatherSchedule.isAttemptDue(SystemClock.elapsedRealtime() - lastWeatherFetchElapsed, attemptDelay),
                        hasPosition = solarDay.hasFix,
                        holdsDevicePosition = source.deviceKind != null && lastLocationFix != null,
                        dayIsStale = solarDayIsStale(),
                        userAskPending = userChoseDeviceSource,
                        deviceRetryDue = source.deviceKind != null && LocationRequestThrottle.shared.retryDue(),
                    )) {
                        SolarDaySchedule.Action.ASK_DEVICE -> refreshDeviceFix(source)
                        SolarDaySchedule.Action.RECOMPUTE_FROM_HELD_POSITION ->
                            lastLocationFix?.let { updateSunTimesFromLocation(it) }
                        SolarDaySchedule.Action.FORGET_DEVICE_POSITION -> forgetDevicePosition()
                        SolarDaySchedule.Action.NOTHING -> Unit
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
                    val generationAtStart = weatherInputsGeneration
                    // The outcome of a fetch made on *this* pass, or null when none was due --
                    // the meaning LiveWeatherStatus.of already gives the parameter.
                    var result: WeatherFetchResult? = null
                    if (liveWeatherRuns && fix != null && (movedSinceLastFetch || timerExpired)) {
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
                        // nobody can vouch for stop being drawn -- **as long as they are this
                        // place's**: after a move, a failure drops the old place's (v5.8C).
                        val (held, heldFor) = LiveWeatherSchedule.heldAfterFetch(
                            held = lastWeatherSnapshot,
                            heldFor = lastWeatherSnapshotLocation,
                            fetched = WeatherRepository.snapshotOf(result),
                            fetchedFor = fix,
                        )
                        lastWeatherSnapshot = held
                        lastWeatherSnapshotLocation = heldFor
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
                        enabled = liveWeatherRuns,
                        hasLocation = fix != null,
                        result = result,
                        snapshot = lastWeatherSnapshot,
                        nowMillis = System.currentTimeMillis(),
                        previous = publishedWeatherStatus ?: LiveWeatherStatus.OFF,
                    )
                    if (!liveWeatherRuns) {
                        // Switching the feature off forgets what was fetched, rather than merely
                        // declining to draw it: the next time it is switched on the user expects a
                        // fresh look at the sky, and the immediate refresh that follows depends on
                        // the timer being clear.
                        lastWeatherSnapshot = null
                        lastWeatherSnapshotLocation = null
                        lastWeatherFetchLocation = null
                        lastWeatherFetchElapsed = Long.MIN_VALUE / 4
                        weatherTransientFailures = 0
                    }
                    applyLiveWeather(decision.snapshotForScene, decision.lapsed)
                    // Not an answer about inputs replaced while this pass was fetching: see
                    // [weatherInputsGeneration]. The next pass, due at once, answers for the new ones.
                    if (generationAtStart == weatherInputsGeneration) publishWeatherStatus(decision.status)
                    // **The update check rides this loop, every three hours** (v5.7D, A0; three
                    // hours since v5.10D, the maintainer's row 3). It is here and not in a job
                    // scheduler, an alarm or a WorkManager worker because that was the decision: no
                    // new dependency, no new component, and it inherits the three things this loop
                    // already had argued out -- the monotonic clock, the bounded backoff, and ARC-02's
                    // parking. Its whole cost is one more `if` per two-minute tick and about eight
                    // GitHub requests a day (one more per rebind, and a short retry ladder while
                    // GitHub is unreachable), each answered with no body while the list's first page
                    // has not changed; when it has, the page is read again, and so is each older page
                    // down to the installed version (UpdateChecker).
                    maybeCheckForUpdate()
                    // Waits for the tick *or* for a settings change, whichever comes first -- and the
                    // tick comes sooner when a try for the phone's position falls due before it
                    // (v5.10E): 5 minutes after a request that found nothing means 5, not the next
                    // two-minute pass after 5.
                    withTimeoutOrNull(
                        SolarDaySchedule.nextPassDelayMillis(
                            checkIntervalMillis = WEATHER_CHECK_INTERVAL_MS,
                            deviceSource = LocationSource.of(settings).deviceKind != null,
                            retryPassDelayMillis = LocationRequestThrottle.shared.passDelay(WEATHER_CHECK_INTERVAL_MS),
                        ),
                    ) { weatherWakeUp.receive() }
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
                    if (drawing) {
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
            thread.setVisible(drawing)
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
            // Releases the Live Weather loop, which parks on this channel while invisible (ARC-02).
            // Sent on the way down as well: an engine that has just gone invisible has to reach the
            // `!visible` check to park, and a loop asleep in `withTimeoutOrNull` would otherwise
            // hold its two-minute timer to the end.
            weatherWakeUp.trySend(Unit)
            // Same reason the theme is re-applied further down: a day may have turned over while
            // this engine was not drawing. The receiver in SeasonalIconController catches midnight
            // while the process is alive; this catches the case where it was not.
            if (visible) iconController?.refresh()
            val thread = glThread
            if (thread != null) {
                if (visible) {
                    // Re-check the date every time the wallpaper becomes visible again (e.g. after
                    // the screen was off overnight) so a day boundary crossed while inactive is
                    // picked up promptly instead of waiting for the next settings change.
                    thread.queueEvent(Runnable { applyEffectiveTheme() })
                }
                thread.setVisible(drawing)
                return
            }
            if (drawing) {
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
            // **The home screen has moved the wallpaper with a swipe** (v5.10E, inventory I-222): said
            // once, so *Swipe scroll* can read on -- until then it reads off, because on a home screen
            // that never reports a swipe it does nothing. Not from the picker's preview, which is sent
            // offsets by the picker's own screen (`SwipeReport`). Written once by this engine, and again
            // only if that write failed, until the settings say so.
            if (!isPreview && swipeReport.moved(xOffset, xOffsetStep) && !settings.swipeReported &&
                !swipeReportSent && ::prefs.isInitialized
            ) {
                swipeReportSent = true
                scope.launch {
                    runCatching { prefs.setSwipeReported() }.onFailure { swipeReportSent = false }
                }
            }
            // On the `Canvas` fallback, redraw right away instead of waiting for the next
            // scheduled ~33ms tick: the launcher fires this callback continuously while the user
            // drags between home screens, so rendering immediately keeps the parallax glued to the
            // finger instead of trailing behind by up to one frame (perceived as stutter during
            // the swipe).
            // Still redraws even with swipeScroll off, so e.g. day/night blending keeps updating
            // smoothly during a swipe rather than looking frozen -- only the parallax shift itself
            // is suppressed. On the GPU backend requestRedraw is a no-op: the queued offset lands
            // on the render thread's next paced frame (about 30 a second).
            // On the GPU backend, the normal path, a swipe therefore moves the parallax only at the
            // render loop's cadence, up to one frame behind the finger, and the presentation time
            // adds two refreshes (`FramePacing.presentAt`). No phone this project has sends offsets,
            // so none of it can be seen or measured here (ROADMAP A56, closed by decision).
            if (visible) requestRedraw()
        }

        override fun onDestroy() {
            super.onDestroy()
            WallpaperEngineCensus.engineDestroyed(isPreview)
            if (canvasFallback) WallpaperEngineCensus.canvasEngineDestroyed()
            // An engine can be destroyed while still marked visible (the picker's preview engine
            // usually is). Without this the counter would never fall back to zero and the memory
            // policy would keep believing something is drawing.
            onEngineVisibilityChanged(nowVisible = false, wasVisible = visible)
            visible = false
            engines.remove(this)
            handler.removeCallbacks(drawRunnable)
            handler.removeCallbacks(firstFrameWaitLimit)
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
         * **Two paths reach it, not one**, and both come through `GlRenderThread.reportUnavailable`
         * when [GlLifecyclePolicy.shouldRebuildContext] says no:
         *
         *  - `hadWorkingContext == false` -- EGL never initialised, so the **first** failed frame
         *    lands here. This is the "this device cannot do GL" case;
         *  - `hadWorkingContext == true` and the context has already been rebuilt
         *    [GlLifecyclePolicy.MAX_CONTEXT_REBUILDS] times -- a GPU that worked and stopped.
         *
         * This said *"Reached only when EGL could not be initialised at all"*, which was true
         * before `MAX_CONTEXT_REBUILDS` existed and describes exactly the behaviour that constant
         * was added to replace: `GlLifecyclePolicy`'s own doc says the old latch-on-any-failure
         * rule "is right for 'this device cannot do EGL' and wrong for everything else". The
         * second path is tested -- `GlLifecyclePolicyTest` pins `(true, 3) -> false` -- so the
         * code was right and only this sentence was wrong. `BACKLOG_v4_31.md` item 110.
         *
         * It matters because this is the surface `BACKLOG_v4_30.md` item 103's cost lands on (+13.6 %
         * at v4.30; re-measured on the BV6600 on 2026-09-27, v5.9D: +5 %, about 3 ms of a 60 ms
         * crowded frame at the phone's own size), and that item described the blast radius with
         * the *other* half of the truth -- "the fallback the wallpaper takes after
         * MAX_CONTEXT_REBUILDS EGL failures", which misses the device that never had GL at all.
         * Both halves are here now.
         *
         * The scene state is untouched by this: the same renderer keeps drawing, through the
         * other backend.
         */
        private fun switchToCanvasFallback() {
            if (canvasFallback) return
            canvasFallback = true
            WallpaperEngineCensus.engineFellBackToCanvas()
            glThread?.shutdown()
            glThread = null
            val frame = surfaceHolder.surfaceFrame
            renderer?.onSizeChanged(frame.width(), frame.height())
            lastFrameNanos = System.nanoTime()
            if (drawing) handler.post(drawRunnable)
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
            if (drawing) drawFrame()
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

            // One read of the settings, so the hour and the day below belong to the same ones.
            val current = settings
            val hour = if (current.syncWithRealTime) {
                SunPositionCalculator.currentHour24()
            } else {
                current.fixedHour
            }

            // One read of one reference, so the two hours below provably belong to the same fix.
            // SolarDay.NONE already carries the 6/20 defaults this used to substitute here -- and is
            // what a fixed hour draws with since v5.10D, whatever position is held (the maintainer's
            // row 6: with a fixed hour the location does not count; see SolarDaySchedule.sceneDay).
            val today = SolarDaySchedule.sceneDay(current.syncWithRealTime, solarDay)
            // Recomputed only when the hour or the sun's times change; see [DayPhaseCache] for why
            // a new DayPhase every frame was worth removing (v5.10B).
            val dayPhase = dayPhaseCache.at(
                hour24 = hour,
                sunriseHour = today.sunriseHour,
                sunsetHour = today.sunsetHour,
                // **The one place the real moon enters the scene, and deliberately the only one.**
                // The renderer used to read this clock itself while painting the disc; a frame is
                // now a function of what it was handed, and what a live wallpaper is handed is the
                // sky over the phone. Computed every frame rather than only when the moon is up:
                // it is three double operations and no allocation, and a phase that appears in the
                // frame description only on the frames that happen to show a moon is the kind of
                // conditional input a golden cannot write down.
                moonPhase = SunPositionCalculator.moonPhase(),
            )

            renderer?.draw(target, dayPhase, elapsedSeconds, deltaSeconds)
        }

        /**
         * One pass of the update check, run from the Live Weather loop (A0, v5.7D).
         *
         * ### Why it lives in this loop and not in a scheduler
         *
         * The maintainer's decision, taken 2026-09-23 with its limit stated first; on 2026-09-30 he
         * kept the place and moved the cadence from once a day to every three hours (row 3 of the
         * v5.10A table). The alternative
         * was a `WorkManager` periodic job, which would run whether or not this wallpaper is set and
         * would survive reboot -- and would cost a **thirteenth dependency** in a project that ships
         * twelve and has argued over each, a new initializer in a process that starts nothing, and a
         * manifest disclosure that currently says truthfully that the app makes no timed background
         * network call. Here, the check is one `if` on a tick that already exists.
         *
         * **The limit, which is not a bug to be fixed later.** This loop parks on `weatherWakeUp`
         * while the engine is invisible (ARC-02), so the check only happens while PaperScrape is
         * the wallpaper *and* on screen. Somebody who has the app installed and a different
         * wallpaper set is never checked and never notified. That was said before the choice was
         * made and accepted with it; *Advanced & about*'s button is what those users have.
         *
         * ### What each guard is for
         *
         * - **[isPreview]**: the system wallpaper picker's preview is a second engine running this
         *   same loop. It must not check and must not post -- the person is choosing a wallpaper,
         *   not waiting for an update, and the same reasoning that stopped the preview publishing
         *   the weather status applies here twice over. (The settings screen's own preview card is
         *   not an engine at all: it is a Compose `Canvas`, `ui/ThemePreview.kt`.);
         * - **the two switches**: the notification reports on the automatic check, so it needs both.
         *   Neither is consulted once, at startup: they are read off `settings` every pass, so
         *   turning either off stops the next check rather than the one after a restart;
         * - **[UpdateNotificationPolicy.mayPost]**, read *before* the network call: a check whose
         *   result could not be shown to anybody is a request made for nothing. On API 33+ with the
         *   permission denied this is what keeps the update check off the network entirely.
         *
         * Nothing here throws but the last write: `checkForUpdate` never does by construction, the
         * notification path returns a boolean rather than raising, and the two preference reads
         * recover from read errors; `setNotifiedTag` is a plain DataStore edit. A failed check only
         * moves the retry ladder. The write is caught: an I/O error from `setNotifiedTag` used to
         * escape into the Live Weather loop and end it until the engine was created again.
         */
        private suspend fun maybeCheckForUpdate() {
            if (isPreview) return
            val current = settings
            if (!current.automaticUpdateCheckEnabled || !current.updateNotificationsEnabled) return
            if (!UpdateNotificationPolicy.mayPost(UpdateNotifier.notificationPermission(applicationContext))) return

            if (!UpdateNotificationPolicy.checkDue(SystemClock.elapsedRealtime() - lastUpdateCheckElapsed, updateCheckTransientFailures)) return

            // Stamped before the request, not after, so a slow or hanging call cannot be retried by
            // the next tick two minutes later. The weather loop stamps in the same place.
            lastUpdateCheckElapsed = SystemClock.elapsedRealtime()
            // With the reply kept from the last check: while nothing has changed GitHub answers with
            // no body (v5.10D, row 16).
            val result = UpdateChecker.checkForUpdate(BuildConfig.VERSION_NAME, updatePrefs)
            updateCheckTransientFailures = if (UpdateNotificationPolicy.isTransient(result)) {
                updateCheckTransientFailures + 1
            } else {
                0
            }
            val info = (result as? UpdateCheckResult.Available)?.info ?: return

            // **The snooze is honoured, not routed around.** "Remind me later -> In a month" is
            // keyed to the version tag, so a month on v5.6 does not hide v5.7 -- and a notification
            // that ignored it would be the app answering a question the user has already answered.
            val snooze = updatePrefs.readSnoozeState()
            val post = UpdateNotificationPolicy.shouldPost(
                notificationsEnabled = current.updateNotificationsEnabled,
                permission = UpdateNotifier.notificationPermission(applicationContext),
                tagName = info.tagName,
                alreadyNotifiedTag = updatePrefs.readNotifiedTag(),
                snoozedTag = snooze.versionTag,
                snoozeUntilMillis = snooze.untilMillis,
                nowMillis = System.currentTimeMillis(),
            )
            if (!post) return

            // Recorded only if it actually went out: a tag written for a notification the platform
            // dropped would silence that release for the one user who never saw it.
            //
            // The write is the one thing here that can throw (a full disk, an I/O error), and it
            // runs inside the Live Weather loop: until v5.8C an exception from it ended that loop,
            // and with it every weather refresh and update check, until the engine was created
            // again (v5.8B comment audit). A failed write now costs at most a second notification
            // for the same release on a later check -- the lesser of the two.
            if (UpdateNotifier.post(applicationContext, info.tagName, BuildConfig.VERSION_NAME)) {
                runCatching { updatePrefs.setNotifiedTag(info.tagName) }
            }
        }

        /**
         * The published status was about the provider, key or location source just replaced: say
         * "not known yet" ([LiveWeatherStatus.OFF]) until the next pass has asked again (v5.10C).
         *
         * The settings screen draws the Live Weather switch off on two of the engine's answers, a
         * refused key and a phone that gives no position (`SettingsUiModel.liveWeather`), and the
         * next pass that could replace them runs only once the wallpaper is visible again -- not
         * while the settings screen is in front of it. Kept, a key typed in a moment ago went on
         * reading "not accepted" until the user had gone home and come back. Forgetting the memory
         * as well is what lets the next pass publish whatever it finds, the same answer included.
         * OFF is also what [LiveWeatherSchedule.decide] starts from, so the next pass is a fresh
         * engine's.
         */
        private fun forgetPublishedWeatherStatus() {
            weatherInputsGeneration++
            publishedWeatherStatus = null
            publishWeatherStatus(LiveWeatherStatus.OFF)
        }

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
            // Every engine runs its own weather loop, and while the system wallpaper picker is
            // showing its preview there are two: the one drawing the home screen and the picker's
            // preview engine. Both
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
        private fun applyLiveWeather(snapshot: LiveWeatherSnapshot?, lapsed: Boolean) {
            if (appliedLiveWeather == snapshot && appliedLiveWeatherLapsed == lapsed) return
            appliedLiveWeather = snapshot
            appliedLiveWeatherLapsed = lapsed
            onRenderThread {
                renderer?.liveWeatherOverride = snapshot
                // Why the override is null, when it is: an expiry eases the sky back to the theme's,
                // anything the user switched snaps it (item 135 -- see LiveWeatherDecision.lapsed).
                renderer?.liveWeatherLapsed = lapsed
                requestRedraw()
            }
        }

        // --- Optional location support (only used if the user opts in from settings) ---
        // Sunrise/sunset today; Live Weather reads the same DeviceLocationFix ([lastLocationFix])
        // rather than fetching its own.

        /**
         * Asks the device where it is, once, and only when something needs the answer.
         *
         * There is no subscription any more (see [DeviceLocationProvider]), so this is called at
         * the moments a position actually matters: when a settings change finds no fix held (a
         * source change always clears it), when a weather refresh is due, and -- since v5.10D, on the
         * loop's pass -- while none is held at all, and since v5.10E when a try after a request that
         * found nothing is due ([SolarDaySchedule.onTick]). A real request is made at most once an hour
         * among all of them while the phone answers; 5, 15 and 30 minutes after the first, second and
         * third in a row that brought nothing, then the hour (`LocationRequestThrottle`); in between, the
         * system's cached position or the saved one answers, and nothing wakes the positioning stack.
         *
         * When the provider cannot answer -- the location switched off, no signal, a request too soon after
         * the last one -- the last saved fix is used instead. That is the whole fallback: a town
         * does not move, and last hour's coordinates give a far better scene than a default
         * somewhere else.
         *
         * **Except without the permission** (v5.10D, the maintainer's row 5): a user who took it away
         * said no to PaperScrape knowing where the phone is, and until then the saved position went on
         * being used -- and sent to the weather service every hour -- for good. Now nothing from the
         * phone is used, fresh or saved, until the permission is back, and the settings screen says
         * "GPS - tap to allow" (`DeviceLocationAccess.mayUsePosition`).
         */
        private suspend fun refreshDeviceFix(source: LocationSource) {
            val kind = source.deviceKind ?: return
            if (!deviceAccess(kind).mayUsePosition) {
                forgetDevicePosition()
                return
            }
            val answer = deviceFix(kind, force = userChoseDeviceSource)
            // Spent only once the phone has really been consulted for the choice: a request the throttle or
            // a switched-off location did not let through leaves it waiting for the next pass.
            if (answer.consulted) userChoseDeviceSource = false
            val fix = answer.fix
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
         * The phone no longer lets PaperScrape use its position (v5.10D): the position held from it
         * goes, with the sunrise and sunset worked out from it and the weather fetched for it, and the
         * next pass decides on what is left -- the default 6:00 and 20:00, and Live Weather with no
         * place ("no location"). Only a position from the phone: a Custom one is not the phone's to
         * take away.
         */
        private fun forgetDevicePosition() {
            if (locationSource.deviceKind == null) return
            solarDay = SolarDay.NONE
            lastLocationFix = null
            lastWeatherFetchLocation = null
            lastWeatherSnapshot = null
            lastWeatherSnapshotLocation = null
        }

        /**
         * Whether the sunrise/sunset held were worked out for a day, or a UTC offset, that is now
         * over. Read on every pass of the weather loop, through [SolarDaySchedule.onTick], so this
         * adds no timer of its own -- and since v5.8 that is true for every location source, not
         * only for the phone's with Live Weather on (see [SolarDaySchedule] for the defect).
         */
        private fun solarDayIsStale(): Boolean =
            solarDayStamp != currentSolarStamp()

        private fun currentSolarStamp(): Long =
            SolarDaySchedule.stamp(System.currentTimeMillis(), TimeZone.getDefault())

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
