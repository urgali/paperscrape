package com.paperscrape.livewallpaper.icon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.paperscrape.livewallpaper.engine.SeasonalCalendar
import java.time.LocalDate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Keeps the launcher icon in step with the date, for as long as the wallpaper service lives.
 *
 * ### When it looks, and why it is free when it does not
 *
 * Three moments, none of them a timer:
 *
 * 1. **[start]**, from the service's `onCreate` -- so the icon is right from the moment the
 *    wallpaper is running, with nothing for the user to open.
 * 2. **`ACTION_DATE_CHANGED`**, which the platform broadcasts at local midnight. A receiver
 *    registered in code costs nothing until it fires: no alarm, no wakelock, no wake-up of its
 *    own, and nothing at all added to the 0.16 % of a core this process costs while invisible
 *    (v5.3B audit). `ACTION_TIME_CHANGED` and `ACTION_TIMEZONE_CHANGED` are in the same filter
 *    because "what day is it" can also change without midnight passing.
 * 3. **[onCalendarChanged]**, from the engine's settings collector -- the user moving a window on
 *    the Seasons screen is a change of date boundaries, and the icon follows those.
 *
 * A registered receiver is also why this is not a manifest receiver: `ACTION_DATE_CHANGED` is an
 * implicit broadcast, and manifest receivers for those have been refused since Android 8. The
 * process is already alive whenever the wallpaper is set, so there is nothing to wake.
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
 * All three of those moments arrive on the main thread -- a service's `onCreate`, an engine's
 * visibility callback, a receiver registered without a `Handler` -- and the work behind them is up
 * to six binder round trips to `PackageManager`. That is the main thread of a live wallpaper,
 * where the `Canvas` fallback posts its frames; the same reasoning that keeps a `DataStore` write
 * out of a slider's feedback loop keeps this off it. Everything runs on one single-thread
 * executor, which also means two triggers arriving together are serialised instead of racing to
 * write the same six component states.
 */
class SeasonalIconController(private val context: Context) {

    /** One thread, so the calls are both off the main thread and in order. */
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "SeasonalIcon").apply { isDaemon = true }
    }

    /** Written and read only on [worker]; the trigger threads hand it over rather than share it. */
    private var calendar: SeasonalCalendar = SeasonalCalendar.DEFAULT
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh()
        }
    }

    /** Registers for the date broadcasts and applies today's icon straight away. */
    fun start() {
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            // All three are protected system broadcasts, so NOT_EXPORTED is both allowed and the
            // right answer: nothing but the platform may deliver to this receiver.
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
        }
        refresh()
    }

    fun stop() {
        if (registered) {
            registered = false
            runCatching { context.unregisterReceiver(receiver) }
        }
        worker.shutdown()
    }

    /** The user's calendar, as the engine reads it. Re-applies only when it actually changed. */
    fun onCalendarChanged(next: SeasonalCalendar) {
        submit {
            if (next == calendar) return@submit
            calendar = next
            applyNow()
        }
    }

    /** Re-reads today's date and puts the matching icon on, on [worker]. */
    fun refresh() {
        submit { applyNow() }
    }

    private fun submit(block: () -> Unit) {
        runCatching { worker.execute(block) }   // rejected only after stop(), which is the point
    }

    /** The whole of the work, and the only thing that touches [PackageManager]. On [worker]. */
    private fun applyNow() {
        LauncherIconSwitch.applyForDate(context, LocalDate.now(), calendar)
    }
}
