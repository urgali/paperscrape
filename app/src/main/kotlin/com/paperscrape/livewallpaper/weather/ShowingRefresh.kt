package com.paperscrape.livewallpaper.weather

/**
 * What the weather loop holds for the maintainer's rule (`DESIGN_NOTES.md` §12, decision 99): **a showing asks at once when
 * the last success is an hour old**, and a request of a showing that finds no network is asked again within seconds while
 * the wallpaper stays on screen ([LiveWeatherSchedule.dueOnShowing], [LiveWeatherSchedule.QUICK_RETRY_DELAYS_MILLIS]).
 *
 * Pure, the clock handed in (`SystemClock.elapsedRealtime()` in the engine), so every order of showings, answers and passes
 * runs on the JVM. One per engine, used by its weather loop alone. Beside it the loop keeps the last attempt, the backoff's
 * count and where it fetched for, which decide the hourly request, the move to another place and the backoff.
 */
class ShowingRefresh {

    /** The showings this has answered: the engine's count ([asks]'s `showings`) as last seen. */
    private var showingsSeen = 0

    /** How many quick retries the current showing has made. */
    private var quickRetriesMade = 0

    /** When the next quick retry falls due, on the loop's clock, or [NONE]. */
    private var quickRetryAt = NONE

    /** When a request last **succeeded**, on the loop's clock, or [NEVER]. */
    var lastSuccessAt = NEVER
        private set

    /** Whether the last answer was a refusal ([LiveWeatherSchedule.isRefusal]): then a showing does not ask. */
    var lastRefused = false
        private set

    /**
     * Whether this pass asks for the weather for the rule's sake, at [now]: the engine has come on screen since the last
     * pass ([showings] moved) and the last success is [intervalMillis] old or older, or a quick retry has fallen due. A due
     * quick retry is used up by asking, whether or not the loop can make the request (no position, Live Weather off), so
     * none is left behind in the past. A new showing starts its quick retries again.
     */
    fun asks(now: Long, showings: Int, intervalMillis: Long): Boolean {
        val newlyShown = showings != showingsSeen
        showingsSeen = showings
        if (newlyShown) {
            quickRetriesMade = 0
            quickRetryAt = NONE
        }
        val showingDue = LiveWeatherSchedule.dueOnShowing(newlyShown, now - lastSuccessAt, lastRefused, intervalMillis)
        val quickDue = quickRetryAt != NONE && now >= quickRetryAt
        if (quickDue) quickRetryAt = NONE
        return showingDue || quickDue
    }

    /**
     * Takes in the answer to a request made at [now] -- [forShowing] when [asks] asked for it -- and returns the backoff's
     * count of transient failures after it from [transientFailures] ([LiveWeatherSchedule.nextAttemptDelayMillis]): 0 after
     * anything but a transient failure, one more after one, **but one for a showing's whole burst**: its quick retries' own
     * failures add nothing. A request of a showing that found no network sets the next quick retry, while any are left.
     */
    fun answered(now: Long, result: WeatherFetchResult?, forShowing: Boolean, transientFailures: Int): Int {
        lastRefused = LiveWeatherSchedule.isRefusal(result)
        if (result is WeatherFetchResult.Success) lastSuccessAt = now
        val failures = when {
            !LiveWeatherSchedule.isTransient(result) -> 0
            forShowing && quickRetriesMade > 0 -> transientFailures
            else -> transientFailures + 1
        }
        quickRetryAt = NONE
        if (forShowing && LiveWeatherSchedule.isNetworkNotReady(result)) {
            val wait = LiveWeatherSchedule.quickRetryDelayMillis(quickRetriesMade)
            if (wait >= 0L) {
                quickRetryAt = now + wait
                quickRetriesMade++
            }
        }
        return failures
    }

    /** The wallpaper is off screen: no quick retry is made there, and the next showing starts its own. */
    fun offScreen() {
        quickRetryAt = NONE
    }

    /** Live Weather is off: what was learnt goes with it, so turned on again it asks at once. */
    fun forget() {
        lastSuccessAt = NEVER
        lastRefused = false
        quickRetryAt = NONE
    }

    /** How long from [now] until the pending quick retry, or -1 for none ([LiveWeatherSchedule.passDelayMillis]). */
    fun quickRetryInMillis(now: Long): Long = if (quickRetryAt == NONE) -1L else maxOf(0L, quickRetryAt - now)

    companion object {
        /** No request has succeeded: read as long ago, through the ordinary comparison ([LiveWeatherSchedule.isAttemptDue]). */
        const val NEVER = Long.MIN_VALUE / 4

        /** No quick retry pending. */
        private const val NONE = Long.MIN_VALUE
    }
}
