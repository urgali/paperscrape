package com.paperscrape.livewallpaper.icon

import android.content.Context
import com.paperscrape.livewallpaper.engine.SeasonalCalendar
import java.time.LocalDate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Keeps the launcher icon in step with the date, for as long as the wallpaper service lives.
 *
 * ### When it looks, and why it is free when it does not
 *
 * Three moments, none of them a timer, and none at the service's start: the user's calendar arrives
 * only with (2.), and nothing is applied until it is known ([IconApplyGate]), so a user whose moved
 * window gives today another icon never sees the factory calendar's go on first.
 *
 * 1. **The date broadcasts**: `ACTION_DATE_CHANGED`, which the platform sends at local midnight, and
 *    `ACTION_TIME_CHANGED` and `ACTION_TIMEZONE_CHANGED`, because "what day is it" can also change
 *    without midnight passing. They reach [refresh] through the wallpaper service's one receiver for
 *    them (`PaperWallpaperService.dateReceiver`), which on the same broadcast has every engine choose
 *    its theme by date again: the icon and the theme change together. That receiver and this
 *    controller are made in the service's `onCreate` and let go of in its `onDestroy`, so a receiver
 *    of its own here would live exactly as long and add nothing. A receiver registered in code costs
 *    nothing until it fires: no alarm, no wakelock, no wake-up of its own.
 * 2. **[onCalendarChanged]**, from the engine's settings collector -- the user moving a window on
 *    the Holiday calendar screen is a change of date boundaries, and the icon follows those.
 * 3. **[refresh]**, each time an engine becomes visible -- a day may have turned over while it was
 *    not drawing.
 *
 * The receiver is registered in code, not in the manifest: `ACTION_DATE_CHANGED` is an implicit
 * broadcast, and manifest receivers for those have been refused since Android 8. The process is
 * already alive whenever the wallpaper is set, so there is nothing to wake.
 *
 * ### What is *not* covered, deliberately
 *
 * A device whose wallpaper is not this app runs none of this, and neither does one whose process
 * the system has killed. Both correct themselves the next time the wallpaper or the settings
 * screen starts. The alternative -- a periodic alarm to be sure -- would spend battery on an app
 * that is not being looked at to fix an icon nobody is looking at either.
 *
 * ### Never on the main thread
 *
 * All of those moments arrive on the main thread -- the service's receiver, registered without a
 * `Handler`, an engine's visibility callback, the engine's settings collector -- and the work behind
 * them is six `PackageManager` reads plus a write for each alias that changes. That is the main
 * thread of a live wallpaper, where the `Canvas` fallback posts its frames; the same reasoning that
 * keeps a `DataStore` write out of a slider's feedback loop keeps this off it. Everything runs on one
 * single-thread executor, which also means two triggers arriving together are serialised instead of
 * racing to write the same six component states.
 */
class SeasonalIconController(private val context: Context) {

    /** One thread, so the calls are both off the main thread and in order. */
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "SeasonalIcon").apply { isDaemon = true }
    }

    /** Written and read only on [worker]; the trigger threads hand it over rather than share it. */
    private val gate = IconApplyGate()

    /** Ends the worker, with the service: a trigger after this does nothing. */
    fun stop() {
        worker.shutdown()
    }

    /** The user's calendar, as the engine reads it. Re-applies only when it actually changed. */
    fun onCalendarChanged(next: SeasonalCalendar) {
        submit { if (gate.onCalendar(next)) applyNow() }
    }

    /** Re-reads today's date and puts the matching icon on, on [worker] -- once the calendar is known. */
    fun refresh() {
        submit { if (gate.onRefresh()) applyNow() }
    }

    private fun submit(block: () -> Unit) {
        runCatching { worker.execute(block) }   // rejected only after stop(), which is the point
    }

    /** The whole of the work, and the only thing that touches [PackageManager]. On [worker]. */
    private fun applyNow() {
        LauncherIconSwitch.applyForDate(context, LocalDate.now(), gate.calendar)
    }
}

/**
 * When [SeasonalIconController] may put an icon on: only once it knows the user's calendar, and
 * then on every refresh and on every change of calendar. Pure, so the rule is tested on the JVM.
 */
internal class IconApplyGate {
    var calendar: SeasonalCalendar = SeasonalCalendar.DEFAULT
        private set
    private var known = false

    /** The engine reported [next]: apply if it is the first report or a different calendar. */
    fun onCalendar(next: SeasonalCalendar): Boolean {
        if (known && next == calendar) return false
        calendar = next
        known = true
        return true
    }

    /** A date broadcast or a visible engine: apply only with a known calendar. */
    fun onRefresh(): Boolean = known
}
