package com.paperscrape.livewallpaper.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.os.Handler
import android.os.SystemClock
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
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
import com.paperscrape.livewallpaper.update.UpdateCheckJobs
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
import java.util.TimeZone

// ~30 fps, smooth enough for a slow scene. File-scoped, as TAG is: PaperEngine is an inner class
// (it needs the service's Context) and Kotlin allows no companion object inside one.
private const val FRAME_INTERVAL_MS = 33L
// Logcat tag for the engine's own backstop handler.
private const val TAG = "PaperEngine"
// Once an hour: the weather does not change faster, and a wallpaper runs all day, so more calls
// would cost battery and data for nothing. `internal` so `BackgroundLocationContractTest` can
// assert the cadence.
internal const val WEATHER_REFRESH_INTERVAL_MS = 60 * 60 * 1000L
// How often the refresh loop wakes to *check* whether a fetch is due, much shorter than the
// refresh interval so a switch just turned on gets its first fetch promptly. The network call
// itself stays once per [WEATHER_REFRESH_INTERVAL_MS], sooner only after a transient failure
// (LiveWeatherSchedule.nextAttemptDelayMillis), a changed weather setting or a moved location.
internal const val WEATHER_CHECK_INTERVAL_MS = 2 * 60 * 1000L

/**
 * System-facing entry point. Android instantiates a fresh [PaperEngine] per active
 * wallpaper surface (usually one, occasionally two during a live preview transition).
 */
class PaperWallpaperService : WallpaperService() {

    /**
     * How many engines are on screen, i.e. drawing frames: shown and holding a surface
     * ([GlLifecyclePolicy.countsAsVisible]). The picker's preview engine can run beside the live
     * one and both share one [SpriteCache], so the memory policy needs to know whether *anything*
     * is drawing. Touched only on the main thread, where engine callbacks and `onTrimMemory` arrive.
     */
    private var visibleEngineCount = 0

    /**
     * Every live engine, so a memory-pressure signal can reach each one's render thread: GPU
     * textures can only be deleted by the thread whose context owns them. Main thread only.
     */
    private val engines = mutableListOf<PaperEngine>()

    /**
     * One position request for the whole service, not one per engine: the home screen's engine and
     * the picker's preview each have a settings collector and would ask the device at the same
     * moment. The provider and the lock live on the service, so later callers wait for the first
     * answer instead of starting their own.
     */
    private var sharedLocationProvider: DeviceLocationProvider? = null
    private val locationRequestLock = Mutex()

    /**
     * The device's position, asked for at most once at a time across every engine. A caller that
     * waited on the lock finds a cached fix younger than [DeviceLocationProvider.FRESH_ENOUGH_MS]
     * and gets it without the radio. A real request is made only when the process's one throttle
     * allows it ([LocationRequestThrottle]: an hour after a request that brought a position; 5, 15
     * and 30 minutes after the first, second and third in a row that did not, then the hour), unless
     * [force] says the user has just chosen this source.
     */
    private suspend fun deviceFix(kind: DeviceLocationKind, force: Boolean): DeviceFixAnswer =
        locationRequestLock.withLock { locationProvider().currentFix(kind, forceRequest = force) }

    /** What the phone allows for [kind] now: permission and location switch. No radio. */
    private fun deviceAccess(kind: DeviceLocationKind): DeviceLocationAccess = locationProvider().access(kind)

    private fun locationProvider(): DeviceLocationProvider =
        (sharedLocationProvider ?: DeviceLocationProvider(applicationContext)).also { sharedLocationProvider = it }

    private fun onEngineVisibilityChanged(nowVisible: Boolean, wasVisible: Boolean) {
        if (nowVisible == wasVisible) return
        visibleEngineCount = (visibleEngineCount + if (nowVisible) 1 else -1).coerceAtLeast(0)
    }

    /**
     * Keeps the launcher icon on the season the date is in. On the service, not the engine: two
     * engines can run at once, and one date deserves one writer of the component states.
     */
    private var iconController: SeasonalIconController? = null

    /**
     * **The launcher icon and the theme by date change together when the date moves**, at midnight
     * with the wallpaper on screen too, and the sunrise and sunset with them: when the date, the
     * clock or the time zone moves, [iconController] puts the new day's icon on and every engine
     * chooses its theme again ([PaperEngine.onDateChanged]). Nothing else that chooses the theme
     * (settings, saved themes, a new surface, coming back on screen) happens at midnight on a home
     * screen left on, which would keep Halloween into the 1st of November.
     *
     * The one receiver for these broadcasts in the process: the icon's controller is made in
     * [onCreate], where this is registered, and stopped in [onDestroy], where this is let go of, so
     * the two live alike and one receiver reaches both. Delivered on the main thread, which owns the
     * engines; the icon's work goes on to the controller's own thread. Registered in code: the three
     * are implicit broadcasts a manifest receiver cannot have.
     */
    private val dateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            iconController?.refresh()
            for (engine in engines) engine.onDateChanged()
        }
    }

    override fun onCreate() {
        super.onCreate()
        iconController = SeasonalIconController(applicationContext)
        // All three are protected system broadcasts, so NOT_EXPORTED is allowed and the right answer.
        ContextCompat.registerReceiver(
            this,
            dateReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // The update check is the job scheduler's (`UpdateCheckJobService`, `DESIGN_NOTES.md` §12, decision 72).
        // A force stop (the user's, or the BV6600 firmware's at every restart)
        // cancels the job, and the wallpaper's process starting again is one of the two moments the
        // app may put it back (the settings screen opening is the other). A pending job is kept.
        UpdateCheckJobs.reconcile(applicationContext)
    }

    override fun onDestroy() {
        iconController?.stop()
        iconController = null
        runCatching { unregisterReceiver(dateReceiver) }
        super.onDestroy()
    }

    override fun onCreateEngine(): Engine = PaperEngine().also { engines.add(it) }

    /**
     * Releases cached sprites in proportion to how much trouble the system is in, as
     * [MemoryPressurePolicy] decides: `TRIM_MEMORY_UI_HIDDEN` is not pressure, though numerically
     * above `TRIM_MEMORY_RUNNING_CRITICAL`, because for a wallpaper it only means the settings
     * screen closed. `onLowMemory()` is not overridden: deprecated as of API 36, and these levels
     * cover the same situation on every supported version.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val anyVisible = visibleEngineCount > 0
        val action = MemoryPressurePolicy.actionFor(level, anyVisible)
        SpriteCache.onTrimMemory(level, anyEngineVisible = anyVisible)
        if (action == TrimAction.RELEASE_ALL) {
            // Small, but it holds native filter objects and is rebuilt on demand.
            TintFilterCache.clear()
        }
        if (MemoryPressurePolicy.dropsGpuTextures(action)) {
            // The GPU copies have no LRU of their own over the atlas. Each engine drops its own, on its
            // render thread: a hidden one everything, a drawing one only what lies outside its atlas
            // (MemoryPressurePolicy.gpuTrimFor).
            for (engine in engines) engine.trimGpuResources()
        }
    }

    inner class PaperEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())

        /**
         * **The engine survives its own coroutines failing, and the wallpaper survives with it.**
         *
         * - [SupervisorJob], so one collector dying does not cancel its siblings: with a plain `Job`
         *   a failed settings read would also stop the theme collector, the weather loop and the
         *   location refresh, and the wallpaper would draw on deaf.
         * - A [CoroutineExceptionHandler], so an uncaught failure is logged and ends there instead
         *   of killing the process: Android answers the wallpaper process's death by putting back
         *   the static system wallpaper, which the user cannot undo from inside a crashed app.
         *
         * A backstop, not a substitute for handling errors where they happen: each preference store
         * recovers on its own ([com.paperscrape.livewallpaper.prefs.PrefsRecovery]), and this keeps
         * the next collector somebody adds from taking the process down.
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

        /** This engine's first home-screen offset, and whether a later one moved from it. */
        private val swipeReport = SwipeReport()

        /** Whether this engine has asked for `swipe_reported` to be written. Main thread. */
        private var swipeReportSent = false
        // Written on the main thread by the settings collector (`scope` is `Dispatchers.Main`), read
        // by the weather loop, by callbacks and by the render thread: the volatile makes the write
        // visible wherever it is read.
        @Volatile
        private var settings: WallpaperSettings = WallpaperSettings()
        /** What the system last said of this engine's visibility. Main thread. */
        private var visible = false

        /** Whether the system has given this engine a surface it has not taken back. Main thread. */
        private var hasSurface = false

        /** Set by [onDestroy]: a switch to `Canvas` posted before it does nothing after it. Main thread. */
        private var destroyed = false

        /**
         * Whether this engine is on screen: shown, with a surface ([GlLifecyclePolicy.countsAsVisible]).
         * What the service counts for the memory policy, and what wakes the weather loop. Main thread.
         */
        private val onScreen: Boolean get() = GlLifecyclePolicy.countsAsVisible(visible, hasSurface)

        /** Runs [change] and keeps the service's count of engines on screen in step with [onScreen]. */
        private inline fun changingOnScreen(change: () -> Unit) {
            val before = onScreen
            change()
            onEngineVisibilityChanged(nowVisible = onScreen, wasVisible = before)
        }

        /** Whether the `Canvas` loop may run on this engine now: [GlLifecyclePolicy.canvasLoopMayRun]. */
        private fun canvasLoopMayRun(): Boolean =
            GlLifecyclePolicy.canvasLoopMayRun(canvasFallback, hasSurface, hasGlThread = glThread != null, destroyed = destroyed)
        private var lastFrameNanos = System.nanoTime()
        private var elapsedSeconds = SceneTime.ZERO

        /** This engine's day phase, rebuilt only when its inputs change; read by [renderScene]. */
        private val dayPhaseCache = DayPhaseCache()

        /**
         * The GPU render thread, or null once the `Canvas` fallback has taken over. While it exists it
         * owns the EGL context and, by convention, every read and write of the scene state
         * ([onRenderThread]).
         */
        private var glThread: GlRenderThread? = null

        /**
         * Set when GL is given up on: EGL never initialised, or a context that worked failed past
         * [GlLifecyclePolicy.MAX_CONTEXT_REBUILDS] rebuilds ([switchToCanvasFallback]). From then on
         * the engine drives the `Canvas` path from the main looper.
         */
        private var canvasFallback = false

        /** Reused by the fallback path so the indirection adds no per-frame allocation. */
        private val canvasTarget = CanvasSceneTarget()

        /**
         * Today's sunrise, sunset and whether they came from a real position, with the civil day and UTC
         * offset they were worked out for: written on the main thread and read by [renderScene] on the
         * render thread, so published as one `@Volatile` reference to an immutable [HeldSunTimes], and the
         * values arrive together ([SolarDay] says why separate volatile fields would not). The offset rides
         * along because the frame reads its hour on it ([SolarDaySchedule.sceneHour]).
         */
        @Volatile
        private var sunTimes: HeldSunTimes = HeldSunTimes.NONE

        /** [sunTimes]' sunrise and sunset. */
        private val solarDay: SolarDay get() = sunTimes.day

        /**
         * The minute [sceneHourValue] was read for, and the [sunTimes] it was read against ([sceneHourNow]).
         * Written and read by whichever thread draws: the render thread, or the main one on the `Canvas` fallback.
         */
        private var sceneHourMinute = Long.MIN_VALUE
        private var sceneHourHeld: HeldSunTimes? = null
        private var sceneHourValue = 0f

        /**
         * When the last weather fetch was attempted, on the **monotonic** clock: on the wall clock, a
         * clock moved backwards (an NTP correction, the user setting the date) makes `now - lastFetch`
         * negative and no fetch is due until it catches up. `elapsedRealtime` counts since boot,
         * includes deep sleep and never goes back. The snapshot's `fetchedAtMillis` stays on the wall
         * clock: it is a timestamp compared with dates.
         */
        private var lastWeatherFetchElapsed: Long = Long.MIN_VALUE / 4
        // The fix updateSunTimesFromLocation derived sunrise and sunset from, kept so the weather
        // loop asks WeatherRepository.fetchCurrentConditions for the same place without asking the
        // phone again (DeviceLocationFix says why). Volatile for the reason [settings] is.
        @Volatile
        private var lastLocationFix: DeviceLocationFix? = null

        /**
         * Wakes the Live Weather loop now rather than at its next check: a settings change, a location
         * fix, visibility, a surface and the date send here. Conflated: rapid sends collapse into one
         * wake-up, and one sent while the loop is busy is not lost.
         */
        private val weatherWakeUp = Channel<Unit>(Channel.CONFLATED)
        /**
         * The coordinates the last weather fetch was *attempted* for, stamped before the request like
         * [lastWeatherFetchElapsed], whatever its outcome. The refresh timer answers "are these
         * conditions stale"; this answers "was the last fetch for the place we show", the one that
         * changes when the user edits the custom location. A failed fetch for a new place is recorded
         * too (the retry ladder decides when to ask again), and [LiveWeatherSchedule.heldAfterFetch]
         * drops the previous place's conditions so they do not go on drawing.
         */
        @Volatile
        private var lastWeatherFetchLocation: DeviceLocationFix? = null

        /**
         * The newest snapshot the loop holds, whether or not the scene is drawing it. The engine
         * remembers what was fetched and the renderer is told only what [LiveWeatherSchedule.decide]
         * authorises, so "what we have" and "what may be drawn" are two variables, and the main
         * thread never reads the renderer's.
         */
        @Volatile
        private var lastWeatherSnapshot: LiveWeatherSnapshot? = null

        /** Where [lastWeatherSnapshot] was fetched for: see [LiveWeatherSchedule.heldAfterFetch]. */
        @Volatile
        private var lastWeatherSnapshotLocation: DeviceLocationFix? = null

        /**
         * How many transient failures in a row, feeding [LiveWeatherSchedule.nextAttemptDelayMillis].
         * Any other outcome resets it, returning the loop to its hourly cadence.
         */
        @Volatile
        private var weatherTransientFailures = 0

        /**
         * What the renderer was last told, so an unchanged decision costs nothing. Main thread only,
         * written and read by the weather loop, unlike the renderer's own field.
         */
        private var appliedLiveWeather: LiveWeatherSnapshot? = null

        /** Whether the renderer was last told the conditions had aged out; see [applyLiveWeather]. */
        private var appliedLiveWeatherLapsed: Boolean = false

        /**
         * What the settings screen was last told about Live Weather's fallback state, so the status
         * is written only when it changes: every write re-emits the settings flow, and the loop
         * evaluates every two minutes.
         */
        @Volatile
        private var publishedWeatherStatus: LiveWeatherStatus? = null

        /**
         * Counts [forgetPublishedWeatherStatus]: a pass whose fetch began before the inputs changed (a
         * request with the old key in flight while the user typed a new one) publishes nothing, or its
         * answer about the old key would land after the forgetting and stand until the wallpaper is
         * next on screen. Main thread, like every field of the loop.
         */
        private var weatherInputsGeneration = 0

        /**
         * Which location source (GPS, network, Custom or none) the current [lastLocationFix] came
         * from: [solarDay]'s `hasFix` alone cannot say whether the fix is still of the chosen kind.
         */
        private var locationSource: LocationSource = LocationSource.NONE

        /**
         * The user has just picked GPS or Network: the phone is asked at once, past the throttle's wait
         * ([LocationRequestThrottle]), until it has really been consulted for that choice
         * ([DeviceFixAnswer.consulted]), so a choice made with location switched off is answered when it
         * comes on. Set only by a change after this engine's first settings
         * ([SolarDaySchedule.userChoseSource]), never by an engine starting. Main thread, like the
         * collector that sets it and [refreshDeviceFix], which spends it.
         */
        private var userChoseDeviceSource = false

        /** Whether the settings collector has applied this engine's first settings. Main thread. */
        private var settingsApplied = false
        private var lastAppliedThemeId = "sunset"
        private var lastAppliedCustomization: SceneCustomization = SceneCustomization.DEFAULT

        private val drawRunnable = Runnable { drawFrame() }

        /**
         * No frame until the user's settings and saved themes have reached the scene, or the first
         * frames would be a fresh install's Sunset and the user's theme a tenth of a second later
         * ([FirstFrameGate]). Every place that starts drawing asks [drawing] rather than [visible].
         */
        private val firstFrameGate = FirstFrameGate { onFirstFrameAllowed() }
        private val firstFrameWaitLimit = Runnable { firstFrameGate.waitedTooLong() }

        /** Whether this engine draws now: on screen, and past its [firstFrameGate]. */
        private val drawing: Boolean get() = visible && firstFrameGate.isOpen

        /**
         * The gate has opened: start the frames it was holding, on whichever backend draws; the
         * `Canvas` loop only where it may run ([canvasLoopMayRun]), since an engine with no GL thread
         * may have no surface yet, or none at all.
         */
        private fun onFirstFrameAllowed() {
            val thread = glThread
            if (thread != null) {
                thread.setVisible(drawing)
            } else if (drawing && renderer != null && canvasLoopMayRun()) {
                lastFrameNanos = System.nanoTime()
                handler.post(drawRunnable)
            }
        }

        /**
         * Runs [action] on whichever thread owns the scene state: with the GPU backend the render
         * thread, queued to land between two frames; on the `Canvas` fallback the main looper,
         * inline. Every path that mutates the renderer from a coroutine or a system callback goes
         * through here or makes the same choice inline (onVisibilityChanged, onSurfaceCreated,
         * onSurfaceChanged, switchToCanvasFallback), which keeps the scene single-threaded without a
         * lock that would put every settings write in contention with the frame loop.
         */
        private inline fun onRenderThread(crossinline action: () -> Unit) {
            val thread = glThread
            if (thread != null) thread.queueEvent(Runnable { action() }) else action()
        }

        /**
         * Asks the render thread to drop its GPU textures; safe before it exists. Not a queued event:
         * the trim is GL work, and queued events are drained where no context is guaranteed current.
         * [GlRenderThread.requestTrim] hands it to the one place that has one.
         */
        fun trimGpuResources() {
            glThread?.requestTrim()
        }

        /**
         * The date, the clock or the time zone has moved (the service's [dateReceiver], main thread):
         * the theme by date is chosen again on the thread that owns the scene and redrawn if it changed,
         * and the loop is woken, so the sunrise and sunset are worked out for the new day now rather
         * than at its next pass.
         */
        fun onDateChanged() {
            onRenderThread { if (applyEffectiveTheme()) requestRedraw() }
            weatherWakeUp.trySend(Unit)
        }

        /**
         * Resolves the theme to render now (the user's pick, or the seasonal one when *automatic theme
         * by date* is on and a window applies) and its customization
         * ([CustomThemeRegistry.resolveActiveCustomization]). Returns true if anything rendered
         * changed, so the caller knows whether to force a redraw.
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
            // **No `setTouchEventsEnabled(true)`, deliberately**: nothing overrides `onTouchEvent` or
            // `onCommand`, and touches delivered to a wallpaper that ignores them cost IPC on every
            // finger movement over the home screen. Turn it on in the change that adds a handler.
            prefs = WallpaperPrefs(applicationContext)
            val customThemeStore = CustomThemeStore(applicationContext)
            // An update installed from the app restarts this process, and the wallpaper's engine is
            // the first thing to come back: the APK it was installed from has no further use
            // (ApkDownloader.pruneInstalled).
            if (!isPreview) {
                scope.launch(Dispatchers.IO) {
                    runCatching { ApkDownloader.pruneInstalled(applicationContext, BuildConfig.VERSION_NAME) }
                }
            }
            scope.launch {
                // An import writes two stores, and a process kill between them would leave the
                // preferences new and the saved themes old: the pending document is applied here,
                // before the collector below or a frame can see the half-applied state. One
                // preference read on every start; after such a kill, the write that was owed.
                runCatching { BackupRepository(prefs, customThemeStore, "").finishPendingImport() }
                customThemeStore.dataFlow.collect { data ->
                    onRenderThread {
                        CustomThemeRegistry.update(data)
                        // An override, reset or delete can change what the current themeId resolves to.
                        if (applyEffectiveTheme()) requestRedraw()
                    }
                    // After the update is queued, so the render thread applies it before the frame
                    // the gate lets through.
                    firstFrameGate.savedThemesArrived()
                }
            }
            scope.launch {
                prefs.settingsFlow.collect { newSettings ->
                    // **Published here, not inside the queued block below**: the custom-location
                    // branch further down wakes the weather loop at once, and a loop reading a
                    // `settings` not yet updated would see Live Weather still off and sleep again.
                    // With a custom location that is the only prompt wake-up, as no GPS fix follows.
                    val previousSettings = settings
                    settings = newSettings
                    // Every input the next fetch would use: a new provider, or the key the selected
                    // one was waiting for, is as much a reason to re-check as the switch, because the
                    // answer on screen came from another service or from none.
                    if (LiveWeatherInputs.changed(previousSettings, newSettings)) {
                        // Makes a fetch due now: the loop only decides *whether* one is due.
                        lastWeatherFetchElapsed = Long.MIN_VALUE / 4
                        forgetPublishedWeatherStatus()
                        weatherWakeUp.trySend(Unit)
                    }
                    // A window moved on the Seasons screen moves the icon too: both read this calendar.
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
                    // **A fix belongs to the source it came from**: a change of source forgets the
                    // held fix, or switching Custom to Phone would find `hasFix` already true, never
                    // ask the phone, and go on fetching the custom place's weather.
                    val requestedSource = LocationSource.of(newSettings)
                    if (requestedSource != locationSource) {
                        // A source the user has just chosen is answered at once, whatever the wait
                        // says (`LocationRequestThrottle`), until the phone has really been consulted
                        // for it; with a fixed hour, once real time comes back. Not the engine's own
                        // start, which "changes" the source from none (SolarDaySchedule.userChoseSource).
                        userChoseDeviceSource = SolarDaySchedule.userChoseSource(settingsApplied, locationSource, requestedSource)
                        locationSource = requestedSource
                        sunTimes = HeldSunTimes.NONE
                        lastLocationFix = null
                        // The conditions on screen are the old source's: the next pass fetches.
                        lastWeatherFetchLocation = null
                        lastWeatherFetchElapsed = Long.MIN_VALUE / 4
                        // And "no position" was the old source's answer, not this one's.
                        forgetPublishedWeatherStatus()
                    }
                    if (newSettings.useLocationForSunTimes) {
                        // A fix is asked for here and by the weather loop (when a refresh is due, or
                        // while none is held), never on a timer of its own. **Not with a fixed hour**:
                        // no position counts then, and the Location row is grey.
                        if (!solarDay.hasFix && newSettings.syncWithRealTime) launch { refreshDeviceFix(requestedSource) }
                    } else {
                        sunTimes = HeldSunTimes.NONE
                        if (!newSettings.useCustomLocation) lastLocationFix = null
                    }
                    // Exclusive with the phone path above (WallpaperPrefs enforces it too). A custom
                    // location is already known, so it is applied on every settings change.
                    if (newSettings.useCustomLocation) {
                        updateSunTimesFromLocation(
                            DeviceLocationFix(
                                newSettings.customLocationLatitude.toDouble(),
                                newSettings.customLocationLongitude.toDouble(),
                            ),
                        )
                    } else if (!newSettings.useLocationForSunTimes) {
                        sunTimes = HeldSunTimes.NONE
                        lastLocationFix = null
                    }
                    settingsApplied = true
                }
            }
            // Live Weather: checks every WEATHER_CHECK_INTERVAL_MS, while on screen, whether a fetch
            // is due, and calls WeatherRepository once per WEATHER_REFRESH_INTERVAL_MS in steady
            // state; at once when a location first arrives or moves, sooner after a transient failure.
            scope.launch {
                while (true) {
                    // **An engine off screen does not poll**: it parks on the channel with no timeout,
                    // so with the screen off, or a second engine (the picker's preview) hidden, no
                    // tick and no fetch runs. A settings change, a location fix, visibility and a
                    // surface arriving or going all send here, so coming back on screen re-enters the
                    // body at once and a refresh that fell due meanwhile is picked up immediately.
                    // On screen means shown **with a surface** ([onScreen]): an engine bound to a
                    // window nobody shows has nothing to draw on and fetches nothing.
                    if (!onScreen) {
                        weatherWakeUp.receive()
                        continue
                    }
                    // **The reasons to ask the device where it is**: a weather refresh is due, so the
                    // position behind it might be stale; none is held; or a try is due. `currentFix`
                    // prefers the system's cached answer, and **a real request is made at most once an
                    // hour while the phone answers**, whoever asks (`LocationRequestThrottle`).
                    // A request that finds nothing is tried again after 5, 15 and
                    // 30 minutes, then the hour, held position or not
                    // (`DESIGN_NOTES.md` §12, decision 41): `deviceRetryDue`, and the shorter wait at
                    // the bottom of the loop.
                    // What the phone allows (permission and location switch, no radio) is read on
                    // every pass: without the permission no position is kept or asked for
                    // (`DeviceLocationAccess.mayUsePosition`), and no try is counted.
                    val source = LocationSource.of(settings)
                    val deviceAccess = source.deviceKind?.let { deviceAccess(it) }
                    // Live Weather runs only over a scene that follows real time: a fixed hour has no
                    // "now" to fetch for, as the settings screen says (`LiveWeatherUiState.canBeTurnedOn`).
                    val liveWeatherRuns = LiveWeatherSchedule.runs(settings.liveWeatherEnabled, settings.syncWithRealTime)
                    // The wait before another attempt: the hourly interval, or a bounded backoff while
                    // transient failures run (LiveWeatherSchedule.nextAttemptDelayMillis).
                    val attemptDelay = LiveWeatherSchedule.nextAttemptDelayMillis(
                        consecutiveTransientFailures = weatherTransientFailures,
                        normalIntervalMillis = WEATHER_REFRESH_INTERVAL_MS,
                    )
                    // The sunrise and sunset are decided on every pass, whatever the source and
                    // whether Live Weather is on (SolarDaySchedule.onTick). A weather refresh is a
                    // reason to ask the phone only if it would be sent: a provider waiting for its key
                    // sends nothing (`MissingApiKey`).
                    when (SolarDaySchedule.onTick(
                        followRealTime = settings.syncWithRealTime,
                        liveWeatherEnabled = LiveWeatherSchedule.wouldFetch(liveWeatherRuns, settings.weatherProvider, settings.apiKeyForWeatherProvider),
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
                    // Two reasons to fetch: the hourly timer (the conditions are stale), and another
                    // place, farther than LiveWeatherSchedule.SAME_PLACE_KM from the last fetch's (they
                    // are the wrong conditions). A fix a few metres away is the same place, and waits.
                    val movedSinceLastFetch = LiveWeatherSchedule.movedToAnotherPlace(fix, lastWeatherFetchLocation)
                    val timerExpired = LiveWeatherSchedule.isAttemptDue(SystemClock.elapsedRealtime() - lastWeatherFetchElapsed, attemptDelay)
                    val provider = settings.weatherProvider
                    val generationAtStart = weatherInputsGeneration
                    // The outcome of a fetch made on *this* pass, or null when none was due
                    // (the meaning LiveWeatherStatus.of gives the parameter).
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
                        // A failure keeps the previous snapshot, so one dropped request does not
                        // revert the scene to the theme's own weather, for at most
                        // LiveWeatherSchedule.SNAPSHOT_MAX_AGE_MILLIS and **only if it is this
                        // place's**: after a move, a failure drops the old place's.
                        val (held, heldFor) = LiveWeatherSchedule.heldAfterFetch(
                            held = lastWeatherSnapshot,
                            heldFor = lastWeatherSnapshotLocation,
                            fetched = WeatherRepository.snapshotOf(result),
                            fetchedFor = fix,
                        )
                        lastWeatherSnapshot = held
                        lastWeatherSnapshotLocation = heldFor
                        // Only a transient failure earns a faster retry. A missing or rejected key,
                        // a spent quota and a refused request are answers: asking sooner changes
                        // nothing, so the normal interval stands and the status says what is wrong.
                        weatherTransientFailures = if (LiveWeatherSchedule.isTransient(result)) {
                            weatherTransientFailures + 1
                        } else {
                            0
                        }
                    }
                    // **The single decision**: every pass, fetch or not, gets the status the settings
                    // screen reads and the snapshot the renderer draws from one call, so they cannot
                    // disagree (LiveWeatherSchedule.decide).
                    val decision = LiveWeatherSchedule.decide(
                        enabled = liveWeatherRuns,
                        hasLocation = fix != null,
                        result = result,
                        snapshot = lastWeatherSnapshot,
                        nowMillis = System.currentTimeMillis(),
                        previous = publishedWeatherStatus ?: LiveWeatherStatus.OFF,
                    )
                    if (!liveWeatherRuns) {
                        // Switched off, the feature forgets what was fetched: switched on again it
                        // fetches at once, for a fresh look at the sky.
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
                    // Waits for the tick or a wake-up, whichever comes first; the tick comes sooner
                    // when a try for the phone's position falls due before it, so 5 minutes after a
                    // request that found nothing means 5, not the next two-minute pass after 5.
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
         * A surface has arrived: the first one this engine gets, or a replacement.
         *
         * **The scene survives the surface.** The renderer holds no GL objects (they live in the
         * render thread's [GlSceneTarget]), so a replacement surface reuses it and only updates its
         * size: the scroll position, the animation phase and the live-weather override survive, and
         * the render thread, which outlives the surface, is never handed a second renderer.
         */
        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            // On screen now if the system already called this engine visible ([onScreen]): the count
            // the memory policy reads, and the weather loop, which parks while it is not.
            changingOnScreen { hasSurface = true }
            weatherWakeUp.trySend(Unit)
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
                // The size is applied by whoever owns the renderer: with GL the render thread, from
                // onGlSurfaceChanged once the viewport is set, not the main thread under its frames.
                if (glThread == null) existing.onSizeChanged(frame.width(), frame.height())
            }
            when (GlLifecyclePolicy.surfaceCreated(hasThread = glThread != null, canvasFallback = canvasFallback)) {
                GlLifecyclePolicy.SurfaceAction.START_THREAD -> startGlThread(holder)
                GlLifecyclePolicy.SurfaceAction.REUSE_THREAD -> attachSurfaceToGlThread(holder)
                GlLifecyclePolicy.SurfaceAction.NO_GL -> {
                    // The Canvas loop draws this engine; stopped when the surface went away
                    // (onSurfaceDestroyed), it restarts with the new one.
                    if (drawing && canvasLoopMayRun()) {
                        lastFrameNanos = System.nanoTime()
                        handler.post(drawRunnable)
                    }
                }
            }
        }

        private fun startGlThread(holder: SurfaceHolder) {
            // A `Canvas` loop started on this engine before it had GL stops here, not at the next
            // screen off: from now on the GL thread draws the surface.
            handler.removeCallbacks(drawRunnable)
            val thread = GlRenderThread(glCallbacks)
            glThread = thread
            thread.start()
            attachSurfaceToGlThread(holder)
        }

        /**
         * Hands a surface to the thread that already owns this engine's GL. The thread keeps its EGL
         * context across the gap and rebuilds only the EGL surface, so a destroy/create cycle does not
         * cost a thread, a context and every uploaded texture.
         */
        private fun attachSurfaceToGlThread(holder: SurfaceHolder) {
            val thread = glThread ?: return
            // The size first, with the surface: the render thread that sees the new surface sees its size too.
            val frame = holder.surfaceFrame
            thread.onSurfaceCreated(holder, frame.width(), frame.height())
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
         * The surface is going away, but this engine is not. The render thread is **not** stopped: it
         * owns this engine's GL for the engine's whole life and parks with its context until a surface
         * comes back. What stops is the Canvas fallback's self-rescheduling frame, which would call
         * `lockCanvas` on a dead surface at frame cadence.
         */
        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            changingOnScreen { hasSurface = false }
            weatherWakeUp.trySend(Unit)
            glThread?.onSurfaceDestroyed()
            handler.removeCallbacks(drawRunnable)
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            changingOnScreen { this.visible = visible }
            // Releases the Live Weather loop, which parks on this channel while off screen. Sent on
            // the way down too: the loop must reach its check to park, or it would sleep out its
            // two-minute timer.
            weatherWakeUp.trySend(Unit)
            // A day may have turned over while this engine was not drawing. With the process alive,
            // midnight reaches the icon and the theme through the service's [dateReceiver]; this
            // catches the case where it was not.
            if (visible) iconController?.refresh()
            // **Nothing about the street changes here**: a building among the houses changes only by
            // scrolling (`DESIGN_NOTES.md` §12, decision 60), so coming back from an app or the screen
            // off shows the same ones (`StorefrontStreet`).
            val thread = glThread
            if (thread != null) {
                if (visible) {
                    // Re-check the date on coming back, for a day boundary crossed while hidden.
                    thread.queueEvent(Runnable { applyEffectiveTheme() })
                }
                thread.setVisible(drawing)
                return
            }
            // No GL thread: the `Canvas` loop, only where it may run. An engine with no GL thread may
            // have no surface yet, or be the picker's preview of a stopped package, kept visible over a
            // window that never gets a surface, where the loop would `lockCanvas` on nothing.
            if (drawing && canvasLoopMayRun()) {
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
            // Always recorded, whatever swipeScroll says: the renderer decides through
            // swipeScrollEnabled whether it is drawn, so turning the setting on finds no stale offset.
            onRenderThread { renderer?.homeScreenOffset = xOffset }
            // **The home screen has moved the wallpaper with a swipe**: said once, so *Swipe scroll*
            // can read on; until then it reads off, because on a home screen that never reports a
            // swipe it does nothing. Not from the picker's preview, which the picker's own screen sends
            // offsets (`SwipeReport`). Written once by this engine, and again only if that write failed.
            if (!isPreview && swipeReport.moved(xOffset, xOffsetStep) && !settings.swipeReported &&
                !swipeReportSent && ::prefs.isInitialized
            ) {
                swipeReportSent = true
                scope.launch {
                    runCatching { prefs.setSwipeReported() }.onFailure { swipeReportSent = false }
                }
            }
            // On the `Canvas` fallback, redraw at once rather than at the next ~33 ms tick: the
            // launcher sends this continuously during a drag, and an immediate frame keeps the
            // parallax on the finger. Redrawn with swipeScroll off too, so the rest of the scene keeps
            // moving. On the GPU backend requestRedraw does nothing: the queued offset lands on the
            // render thread's next paced frame, up to one frame behind the finger, plus the
            // presentation delay (`FramePacing.presentAt`). The BV6600's launcher sends no offsets, so
            // none of this can be seen or measured on it.
            if (visible) requestRedraw()
        }

        override fun onDestroy() {
            super.onDestroy()
            WallpaperEngineCensus.engineDestroyed(isPreview)
            if (canvasFallback) WallpaperEngineCensus.canvasEngineDestroyed()
            // An engine can be destroyed while still marked visible (the picker's preview usually
            // is): without this the count would never fall to zero, and the memory policy would
            // believe something is drawing.
            changingOnScreen {
                visible = false
                destroyed = true
            }
            engines.remove(this)
            handler.removeCallbacks(drawRunnable)
            handler.removeCallbacks(firstFrameWaitLimit)
            glThread?.shutdown()
            glThread = null
            engineJob.cancel()
            // Nothing to unsubscribe from: a position request ends by itself, so cancelling the
            // engine's job is the whole teardown.
        }

        /**
         * The GPU backend's side of the frame, on the render thread. `applyEffectiveTheme` runs on
         * becoming visible and on every queued settings change, so this callback only draws.
         */
        private val glCallbacks = object : GlRenderThread.Callbacks {
            override fun onGlSurfaceChanged(width: Int, height: Int) {
                renderer?.onSizeChanged(width, height)
            }

            override fun onGlDrawFrame(target: SceneCanvas, deltaSeconds: Float) {
                renderScene(target, deltaSeconds)
            }

            override fun onGlUnavailable() {
                // Reported from the render thread; the switch happens on the main thread, which
                // owns the Handler loop the fallback runs on.
                handler.post { switchToCanvasFallback() }
            }
        }

        /**
         * Gives up on GL for the rest of this engine's life and restarts the `Canvas` loop. Two paths
         * reach it, both through `GlRenderThread.reportUnavailable` when
         * [GlLifecyclePolicy.shouldRebuildContext] says no:
         *
         *  - `hadWorkingContext == false`: EGL never initialised, so the **first** failed frame lands
         *    here (a device that cannot do GL);
         *  - `hadWorkingContext == true` after [GlLifecyclePolicy.MAX_CONTEXT_REBUILDS] rebuilds: a
         *    GPU that worked and stopped (`GlLifecyclePolicyTest`).
         *
         * Both devices then draw every frame on the `Canvas` path, which costs more CPU than GL. The
         * scene state is untouched: the same renderer keeps drawing, through the other backend.
         */
        private fun switchToCanvasFallback() {
            // Not twice, and not after onDestroy: this is posted from the render thread, and landing
            // after the engine is gone it would count a `Canvas` engine nobody takes off the census.
            if (!GlLifecyclePolicy.mayFallBackToCanvas(alreadyCanvas = canvasFallback, destroyed = destroyed)) return
            canvasFallback = true
            WallpaperEngineCensus.engineFellBackToCanvas()
            glThread?.shutdown()
            glThread = null
            val frame = surfaceHolder.surfaceFrame
            renderer?.onSizeChanged(frame.width(), frame.height())
            lastFrameNanos = System.nanoTime()
            if (drawing && canvasLoopMayRun()) handler.post(drawRunnable)
        }

        /**
         * Asks for a frame outside the normal cadence: on the `Canvas` fallback, a redraw when something
         * changed between ticks. The GPU loop runs continuously while visible, so there it does nothing.
         */
        private fun requestRedraw() {
            if (glThread != null) return
            if (drawing) drawFrame()
        }

        private fun drawFrame() {
            val frameStartNanos = System.nanoTime()
            val holder = surfaceHolder
            // **No `Canvas` frame without a surface to lock, and no next one**: `lockCanvas` on a
            // surface that does not exist, or that GL holds, returns nothing after Android has waited
            // up to 100 ms on the main thread. A new surface or the switch to `Canvas` starts it again.
            if (!canvasLoopMayRun() || holder.surface?.isValid != true) {
                handler.removeCallbacks(drawRunnable)
                return
            }
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
                // A compensated delay: a fixed FRAME_INTERVAL_MS on top of this frame's own cost would
                // space the frames unevenly with the load, which reads as stutter on the fastest shapes
                // (the sleigh) even though every animation moves by the measured deltaSeconds.
                // Subtracting the frame's cost keeps the schedule near a steady 33 ms.
                val frameCostMs = (System.nanoTime() - frameStartNanos) / 1_000_000L
                val nextDelayMs = (FRAME_INTERVAL_MS - frameCostMs).coerceIn(0L, FRAME_INTERVAL_MS)
                handler.postDelayed(drawRunnable, nextDelayMs)
            }
        }

        /**
         * One frame of the scene, for both loops: the render thread with the GPU target, the fallback
         * with the `Canvas` one. Scene time advances here, so the two treat a late or a first frame alike.
         */
        private fun renderScene(target: SceneCanvas, deltaSeconds: Float) {
            elapsedSeconds += deltaSeconds

            // One read of the settings, so the hour and the day below belong to the same ones.
            val current = settings
            // One read of one reference, so the sunrise and sunset belong to the same fix, and the
            // hour is read on the offset they were worked out for (SolarDaySchedule.sceneHour).
            val held = sunTimes
            val hour = if (current.syncWithRealTime) {
                sceneHourNow(held)
            } else {
                current.fixedHour
            }

            // SolarDay.NONE carries the 6/20 defaults, and is what a fixed hour draws with whatever
            // position is held (SolarDaySchedule.sceneDay; `DESIGN_NOTES.md` §12, decision 41).
            val today = SolarDaySchedule.sceneDay(current.syncWithRealTime, held.day)
            // Recomputed only when the hour or the sun's times change ([DayPhaseCache]).
            val dayPhase = dayPhaseCache.at(
                hour24 = hour,
                sunriseHour = today.sunriseHour,
                sunsetHour = today.sunsetHour,
                // **The one place the real moon enters the scene**: a frame is a function of what it
                // is handed, and the renderer reads no clock. Computed every frame, not only with the
                // moon up: three double operations and no allocation, and an input present on some
                // frames only is one a golden cannot write down.
                moonPhase = SunPositionCalculator.moonPhase(),
            )

            renderer?.draw(target, dayPhase, elapsedSeconds, deltaSeconds)
        }

        /**
         * The scene's hour now, read once a minute ([SolarDaySchedule.sceneHour]): on the offset [held]'s
         * sunrise and sunset were worked out for, or the clock's own without a position, and again at once
         * when new times are published, so the hour and the times change in the same frame. When the day or
         * the offset has moved, the loop is woken to work the times out now, not at its next pass. Allocates
         * only once a minute (the zone and its calendar), never per frame.
         */
        private fun sceneHourNow(held: HeldSunTimes): Float {
            val now = System.currentTimeMillis()
            val minute = Math.floorDiv(now, 60_000L)
            if (minute != sceneHourMinute || held !== sceneHourHeld) {
                val zone = TimeZone.getDefault()
                sceneHourMinute = minute
                sceneHourHeld = held
                sceneHourValue = SolarDaySchedule.sceneHour(now, held, zone)
                if (SolarDaySchedule.workedOutForAnotherDay(held, now, zone)) weatherWakeUp.trySend(Unit)
            }
            return sceneHourValue
        }

        /**
         * The published status was about the provider, key or location source just replaced: say "not
         * known yet" ([LiveWeatherStatus.OFF]) until the next pass has asked again. The settings screen
         * draws the switch off on a refused key or a phone giving no position
         * (`SettingsUiModel.liveWeather`), and the next pass runs only once the wallpaper is on screen
         * again, so a key just typed would go on reading "not accepted". Forgetting the memory too lets
         * the next pass publish whatever it finds, the same answer included; OFF is also what
         * [LiveWeatherSchedule.decide] starts from.
         */
        private fun forgetPublishedWeatherStatus() {
            weatherInputsGeneration++
            publishedWeatherStatus = null
            publishWeatherStatus(LiveWeatherStatus.OFF)
        }

        /**
         * Tells the settings screen whether Live Weather is running on fallback; only on a change, as
         * every write re-emits the settings flow and this runs on a two-minute tick.
         */
        private fun publishWeatherStatus(status: LiveWeatherStatus) {
            if (publishedWeatherStatus == status) return
            publishedWeatherStatus = status
            // **Only the wallpaper writes the status**: the picker's preview runs its own loop, with its
            // own snapshot and retry count, and its first failed fetch would publish FAILED over the
            // home screen's OK. The preview still fetches and draws; it only does not write.
            if (isPreview) return
            scope.launch { prefs.setLiveWeatherStatus(status) }
        }

        /**
         * Hands the renderer exactly what [LiveWeatherSchedule.decide] authorised. Null means "draw the
         * theme's own weather", the scene Live Weather off shows. Only on a change: an identical override
         * would queue a render-thread event and a redraw every two-minute tick for nothing.
         */
        private fun applyLiveWeather(snapshot: LiveWeatherSnapshot?, lapsed: Boolean) {
            if (appliedLiveWeather == snapshot && appliedLiveWeatherLapsed == lapsed) return
            appliedLiveWeather = snapshot
            appliedLiveWeatherLapsed = lapsed
            onRenderThread {
                renderer?.liveWeatherOverride = snapshot
                // Why the override is null, when it is: an expiry eases the sky back to the theme's,
                // anything the user switched snaps it (LiveWeatherDecision.lapsed).
                renderer?.liveWeatherLapsed = lapsed
                requestRedraw()
            }
        }

        // --- Location, only if the user opts in: today's sunrise and sunset, and the place Live
        // Weather fetches for ([lastLocationFix]).

        /**
         * Asks the device where it is, once, and only when something needs the answer. There is no
         * subscription ([DeviceLocationProvider]): this is called when a settings change finds no fix
         * held (a source change clears it), when a weather refresh that would be sent is due, and, on
         * the loop's pass, while none is held, when a try after a request that found nothing is due,
         * and when the day or offset the sunrise and sunset were worked out for is over
         * ([SolarDaySchedule.onTick]). A real request is made at most once an hour among all of them
         * while the phone answers; 5, 15 and 30 minutes after the first, second and third in a row that
         * brought nothing, then the hour (`LocationRequestThrottle`). In between, the system's cached
         * position or the saved one answers, and nothing wakes the positioning stack.
         *
         * When the provider cannot answer (location off, no signal, a request too soon after the last)
         * the last saved fix is used: a town does not move, and last hour's coordinates give a better
         * scene than a default somewhere else.
         *
         * **Except without the permission**: a user who took it away said no to PaperScrape knowing
         * where the phone is, so nothing from the phone is used, fresh or saved, until it is back
         * (`DESIGN_NOTES.md` §12, decision 41), and the settings screen says "GPS - tap to allow"
         * (`DeviceLocationAccess.mayUsePosition`).
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
            // Nothing new: fall back to the saved position, once. A fix already held needs nothing,
            // unless the day has turned over: then, the phone having been asked, the position held is
            // the best there is (the saved one, or the one in hand if its save never landed), or the
            // times would stay on the old day and the scene's hour on their offset.
            if (solarDay.hasFix && !solarDayIsStale()) return
            val saved = savedDeviceFix() ?: lastLocationFix?.takeIf { solarDay.hasFix }
            if (saved != null) updateSunTimesFromLocation(saved, isDeviceFix = false)
        }

        /**
         * The position saved by the last successful fix, if there is one. No expiry: an old fix is still
         * a place, and the alternative is no position and a scene that stops following the weather.
         */
        private fun savedDeviceFix(): DeviceLocationFix? {
            val latitude = settings.resolvedGpsLatitude ?: return null
            val longitude = settings.resolvedGpsLongitude ?: return null
            return DeviceLocationFix(latitude.toDouble(), longitude.toDouble())
        }

        /**
         * The phone no longer lets PaperScrape use its position: the position held from it goes, with
         * the sunrise and sunset worked out from it and the weather fetched for it, and the next pass
         * decides on what is left (the default 6:00 and 20:00, Live Weather with "no location"). Only a
         * position from the phone: a Custom one is not the phone's to take away.
         */
        private fun forgetDevicePosition() {
            if (locationSource.deviceKind == null) return
            sunTimes = HeldSunTimes.NONE
            lastLocationFix = null
            lastWeatherFetchLocation = null
            lastWeatherSnapshot = null
            lastWeatherSnapshotLocation = null
        }

        /**
         * Whether the sunrise and sunset held were worked out for a day, or a UTC offset, that is now
         * over. Read on every pass of the weather loop through [SolarDaySchedule.onTick], for every
         * location source, so it adds no timer of its own.
         */
        private fun solarDayIsStale(): Boolean =
            sunTimes.workedOutFor?.stamp != currentSolarStamp()

        private fun currentSolarStamp(): Long =
            SolarDaySchedule.stamp(System.currentTimeMillis(), TimeZone.getDefault())

        private fun updateSunTimesFromLocation(fix: DeviceLocationFix, isDeviceFix: Boolean = false) {
            // The day, the offset and the stamp from one reading of the clock and the zone
            // (SolarDaySchedule.civilDayAt): read apart, a working-out across midnight or the change of
            // the clocks could stamp one day or offset with another's sunrise.
            val at = SolarDaySchedule.civilDayAt(System.currentTimeMillis(), TimeZone.getDefault())
            val (sunrise, sunset) = SunPositionCalculator.approximateSunriseSunset(
                latitudeDeg = fix.latitude,
                longitudeDeg = fix.longitude,
                dayOfYear = at.dayOfYear,
                utcOffsetHours = at.offsetMillis / 3_600_000.0,
            )
            // Published as one object, not separate stores: see SolarDay and HeldSunTimes.
            sunTimes = HeldSunTimes(SolarDay.located(sunrise, sunset), at)
            lastLocationFix = fix
            // A fix arriving is as much a reason to re-evaluate as the preference changing: Live
            // Weather switched on usually finds no fix yet (GPS takes seconds), and without this
            // wake-up the first fetch would wait out the loop's two-minute tick.
            weatherWakeUp.trySend(Unit)
            // Only a fresh device fix is saved: a custom fix is already in the settings, and a saved
            // fix written back would renew its timestamp and always look just taken.
            if (isDeviceFix) {
                scope.launch { prefs.setResolvedGpsLocation(fix.latitude.toFloat(), fix.longitude.toFloat()) }
            }
        }
    }
}
