package com.paperscrape.livewallpaper.weather

import com.paperscrape.livewallpaper.engine.WEATHER_REFRESH_INTERVAL_MS

/**
 * What the live-weather loop should do next, and whether what it already has may draw.
 *
 * Pure and outside the service's `while (true)`, as [LiveWeatherInputs] is: these are the rules the
 * feature's correctness rests on, and inline in the loop nothing could reach them, so each of the
 * three could rot in a different direction unseen.
 *
 * ### The one rule
 *
 * [decide] answers a single question -- **may this snapshot draw the scene?** -- and returns both
 * halves of the answer together, so that the settings screen and the renderer cannot disagree:
 *
 * > `status.isDrivingTheScene` is true **if and only if** `snapshotForScene` is non-null.
 *
 * Computed in different places from different inputs, the two disagree: with the status from
 * [LiveWeatherStatus.of], and the renderer's override whatever the last successful fetch left
 * behind, cleared only when Live Weather is off, turning location off or switching to a
 * provider whose key is missing or rejected publishes a status whose
 * [LiveWeatherStatus.isRunningOnThemeWeather] is true -- which unlocks the theme's own cloud and
 * precipitation controls -- while the fetched conditions carry on drawing. The controls the user
 * has just been handed do nothing, and the sky disagrees with the screen that describes it.
 *
 * ### Stale conditions, and their expiry
 *
 * **The deliberate [LiveWeatherStatus.STALE] design holds**: a dropped request leaves
 * the last known-good conditions on screen rather than flicking the scene back to the theme's
 * manual settings. "Last known-good" has an expiry ([SNAPSHOT_MAX_AGE_MILLIS]),
 * and a status which says the theme is driving the scene means it.
 */
object LiveWeatherSchedule {

    /**
     * How old a fetched snapshot may be and still draw: **three refresh intervals**, derived.
     *
     * Written as a multiple of [WEATHER_REFRESH_INTERVAL_MS] rather than as its own three-hour
     * literal, so the policy *is* the declaration and the two cannot drift apart: halving the
     * refresh interval halves this, and no test or reviewer has to notice. (A test pinning the
     * relationship would still let a release ship an hour in which the two disagree, which is
     * exactly the window worth closing.)
     *
     * What derivation cannot check is whether the *result* is still a defensible duration, so a
     * test asserts the absolute value as well: change the refresh interval and the cap follows
     * automatically, but the assertion fails and the decision below has to be made again rather
     * than inherited.
     *
     * The interval is the floor: a cap of one interval would expire every snapshot at the very
     * moment its replacement is due, so any late refresh -- a device in Doze, a screen that has
     * been off, one dropped request -- would drop the scene back to theme weather and then pull it
     * forward again on the next success. That flicker is precisely what [LiveWeatherStatus.STALE]
     * exists to avoid, and an age cap must not undo it.
     *
     * Three is the smallest multiple that tolerates a couple of missed cycles without flapping,
     * and it is also about as long as a drawn sky can be defended: cloud cover and precipitation
     * are hours-scale, not days-scale, so a reading from three hours ago is a plausible sky while
     * one from yesterday is a fabrication. Past the cap the scene returns to the theme's own
     * weather, which is always a valid scene, and the status says so.
     */
    const val SNAPSHOT_MAX_AGE_MILLIS = 3 * WEATHER_REFRESH_INTERVAL_MS

    /**
     * The first retry delay after a transient failure, and the unit the backoff doubles: the loop's
     * own tick, its pass every two minutes. Shorter waits are the quick retries of a showing
     * ([QUICK_RETRY_DELAYS_MILLIS]), for which the loop shortens its wait ([passDelayMillis]).
     */
    const val RETRY_BASE_MILLIS = 2 * 60 * 1000L

    /**
     * **The maintainer's rule** (`DESIGN_NOTES.md` §12, decision 99, 2026-11-04): *«se passa più di un'ora deve
     * aggiornare il meteo nel momento in cui riaccendo lo schermo, non semplicemente fallire e aspettare l'ora dopo»*.
     * Whether the wallpaper coming on screen ([newlyShown]: the screen turned on over it, on the lock screen or the
     * home screen, or an app over it closed) asks for the weather at once: when the last **successful** request is an
     * interval ([intervalMillis]) or more ago ([sinceLastSuccessMillis], on the monotonic clock), whatever failed in
     * between and whatever the backoff ([nextAttemptDelayMillis]) says. **Not after a refusal** ([lastWasRefusal]: a
     * missing or rejected key, the provider's limit, a refused request): the same request gets the same no, so those wait
     * the hour from their last attempt, as the backoff has them.
     */
    fun dueOnShowing(newlyShown: Boolean, sinceLastSuccessMillis: Long, lastWasRefusal: Boolean, intervalMillis: Long): Boolean =
        newlyShown && !lastWasRefusal && isAttemptDue(sinceLastSuccessMillis, intervalMillis)

    /**
     * How long after a request that found **no network** ([isNetworkNotReady]) the loop asks again while the wallpaper
     * stays on screen: 5, then 10, then 20 seconds -- three quick retries, 35 s in all, for a phone whose connection
     * comes up a moment after the screen. They are for the request a showing made ([dueOnShowing]) and its own retries;
     * once spent the loop is back on the backoff, the burst counted as one failure ([nextAttemptDelayMillis]), and the
     * next showing starts again. Off screen none is made.
     */
    val QUICK_RETRY_DELAYS_MILLIS = longArrayOf(5_000L, 10_000L, 20_000L)

    /**
     * The wait before quick retry number [retriesMade] (0 the first), or -1 once the three are spent
     * ([QUICK_RETRY_DELAYS_MILLIS]).
     */
    fun quickRetryDelayMillis(retriesMade: Int): Long =
        if (retriesMade in QUICK_RETRY_DELAYS_MILLIS.indices) QUICK_RETRY_DELAYS_MILLIS[retriesMade] else -1L

    /**
     * Whether [result] failed because the phone could not reach the provider -- no network yet, a timeout, a dropped
     * connection ([WeatherFailure.NETWORK]) -- the one failure a few seconds can mend. A server error or an answer that
     * could not be read ([WeatherFailure.HTTP_ERROR], [WeatherFailure.MALFORMED_RESPONSE]) came from a provider that was
     * reached, and waits for the backoff; so does every refusal ([isRefusal]).
     */
    fun isNetworkNotReady(result: WeatherFetchResult?): Boolean =
        result is WeatherFetchResult.Failed && result.reason == WeatherFailure.NETWORK

    /**
     * Whether [result] is an answer asking again soon would not change: a missing key (nothing is sent), a rejected key,
     * the provider's limit, a refused request. Such an answer keeps a showing from asking ([dueOnShowing]).
     */
    fun isRefusal(result: WeatherFetchResult?): Boolean = when (result) {
        is WeatherFetchResult.MissingApiKey -> true
        is WeatherFetchResult.Failed -> !isTransient(result)
        else -> false
    }

    /**
     * How long the loop waits before its next pass: [basePassMillis], its two-minute tick (or the sooner try for the
     * phone's position), or less when a quick retry falls due before it ([quickRetryInMillis], negative for none; never
     * under a tenth of a second).
     */
    fun passDelayMillis(basePassMillis: Long, quickRetryInMillis: Long): Long =
        if (quickRetryInMillis < 0L) basePassMillis else minOf(basePassMillis, maxOf(quickRetryInMillis, 100L))

    /**
     * Whether this outcome is worth trying again sooner than the normal interval.
     *
     * The vocabulary is [WeatherFailure]'s, which documents which reasons retrying helps, and this
     * consumes it: with every outcome, success or failure, stamping the same hourly timer, a request
     * dropped because the radio was not up yet would cost a full hour of wrong weather. The most
     * repeatable case is a boot: the wallpaper rebinds and fetches before connectivity exists.
     *
     * [WeatherFetchResult.MissingApiKey] is not transient -- nothing is sent in that
     * state, so there is nothing to retry until the user acts. [WeatherFailure.UNAUTHORIZED],
     * [WeatherFailure.RATE_LIMITED] and [WeatherFailure.REQUEST_REFUSED] are answers, not accidents:
     * the service was reached and said no, the same request gets the same no, and hammering it sooner
     * neither helps the user nor is polite to the provider.
     */
    fun isTransient(result: WeatherFetchResult?): Boolean =
        result is WeatherFetchResult.Failed && when (result.reason) {
            WeatherFailure.NETWORK,
            WeatherFailure.HTTP_ERROR,
            WeatherFailure.MALFORMED_RESPONSE,
            -> true

            WeatherFailure.UNAUTHORIZED,
            WeatherFailure.RATE_LIMITED,
            WeatherFailure.REQUEST_REFUSED,
            -> false
        }

    /**
     * Whether enough time has passed since the last attempt, given a monotonic elapsed reading.
     *
     * Stated here rather than inlined at the three call sites because the *clock* is the rule.
     * Compared against a wall-clock stamp with `System.currentTimeMillis()`, a clock moved
     * backwards -- a timezone edit, an NTP correction, a user setting the date -- makes the
     * difference negative, so nothing is ever due again until the wall clock catches back
     * up. `elapsedSince` must come from `SystemClock.elapsedRealtime()`, which counts since boot,
     * includes deep sleep, and cannot go backwards.
     *
     * The negative case is handled rather than assumed away, although nothing produces one:
     * the fresh-engine sentinel (`Long.MIN_VALUE / 4`) reads as "a long time ago" and is due
     * through the ordinary comparison.
     */
    fun isAttemptDue(elapsedSinceLastMillis: Long, delayMillis: Long): Boolean =
        elapsedSinceLastMillis < 0L || elapsedSinceLastMillis >= delayMillis

    /**
     * How long after the last attempt the next one may be made.
     *
     * Zero consecutive transient failures is the normal cadence. After that the delay doubles from
     * [RETRY_BASE_MILLIS] and is capped at [normalIntervalMillis], so the schedule converges back
     * to normal by itself instead of needing a separate "give up" rule. With the weather loop's
     * hourly interval:
     *
     * | consecutive transient failures | delay | attempt at |
     * |---|---|---|
     * | 0 | 60 min | the normal hourly refresh |
     * | 1 | 2 min | 2 min after the failure |
     * | 2 | 4 min | 6 min |
     * | 3 | 8 min | 14 min |
     * | 4 | 16 min | 30 min |
     * | 5 | 32 min | 62 min |
     * | 6+ | 60 min | hourly, as if nothing had happened |
     *
     * That is **four extra requests in the first hour** of an outage from the backoff and none after it -- bounded,
     * deterministic, and no more network than a user who opens the settings screen a few times. A showing past the hour
     * asks besides, with at most three quick retries for no network ([dueOnShowing], decision 99).
     * A live wallpaper cannot afford a fixed short retry: an aeroplane, a tunnel or a dead Wi-Fi
     * network would turn it into a permanent 2-minute poll.
     */
    fun nextAttemptDelayMillis(consecutiveTransientFailures: Int, normalIntervalMillis: Long): Long {
        if (consecutiveTransientFailures <= 0) return normalIntervalMillis
        // Doubling in Long arithmetic, but the shift is bounded first: a counter that ran away
        // would otherwise overflow into a negative delay, which reads as "always due".
        val steps = (consecutiveTransientFailures - 1).coerceAtMost(16)
        val backoff = RETRY_BASE_MILLIS shl steps
        return backoff.coerceAtMost(normalIntervalMillis)
    }

    /**
     * Whether the loop fetches and draws live conditions at all: the switch is on **and** the scene
     * follows real time -- the rule the settings screen states (`LiveWeatherUiState.canBeTurnedOn`:
     * a fixed hour has no "now" to fetch for). One function so the engine and the screen cannot
     * disagree about it.
     */
    fun runs(enabled: Boolean, followRealTime: Boolean): Boolean = enabled && followRealTime

    /**
     * Whether a refresh that falls due would be **sent**: Live Weather [runs], and [provider] needs no key or has one
     * in [apiKey]. A provider waiting for its key answers [WeatherFetchResult.MissingApiKey] without a request, so
     * nothing it does is a reason to ask the phone where it is -- a search of up to 20 s an hour, for a sunrise that
     * the position already held gives.
     */
    fun wouldFetch(runs: Boolean, provider: WeatherProviderId, apiKey: String): Boolean =
        runs && (!WeatherRepository.providerFor(provider).requiresApiKey || apiKey.isNotBlank())

    /**
     * Whether [fix] is **another place** than [lastFetchFor], the position the last fetch was made for, so the weather is
     * asked at once (`DESIGN_NOTES.md` §12, decision 101): a [written] place (*Custom location*) moved more than
     * [WRITTEN_PLACE_KM], a change the user made; the phone's position moved more than [SAME_PLACE_KM]; or no fetch made
     * for any. Closer is the same place to the weather -- the phone's fix trembles by some hundred metres, the saved place
     * comes back as a float -- and its fetch waits for the hourly one. Null [fix]: nowhere to fetch for.
     */
    fun movedToAnotherPlace(
        fix: com.paperscrape.livewallpaper.location.DeviceLocationFix?,
        lastFetchFor: com.paperscrape.livewallpaper.location.DeviceLocationFix?,
        written: Boolean = false,
    ): Boolean = fix != null && (lastFetchFor == null || kmBetween(fix, lastFetchFor) > if (written) WRITTEN_PLACE_KM else SAME_PLACE_KM)

    /**
     * How far apart two of the phone's positions may be and still be **the same place** to the weather (decision 101):
     * 3 km, under the 2 to 11 km cells Open-Meteo's best match uses over Europe, and over what the phone's fix trembles by
     * standing still -- some hundred metres by GPS, up to a few km by the network. Within it the conditions are the
     * last fetch's, and a failed fetch keeps them ([heldAfterFetch]).
     */
    const val SAME_PLACE_KM = 3.0

    /** A written place moved farther than this, 10 m, is a change the user made: another place, asked at once (decision 101). */
    const val WRITTEN_PLACE_KM = 0.01

    /** The two positions are within [SAME_PLACE_KM] of each other. */
    fun isSamePlace(a: com.paperscrape.livewallpaper.location.DeviceLocationFix, b: com.paperscrape.livewallpaper.location.DeviceLocationFix): Boolean =
        kmBetween(a, b) <= SAME_PLACE_KM

    /** The distance between two positions, in km (equirectangular; exact enough at these scales). */
    fun kmBetween(a: com.paperscrape.livewallpaper.location.DeviceLocationFix, b: com.paperscrape.livewallpaper.location.DeviceLocationFix): Double {
        val kmPerDegree = 111.2
        val dLat = (a.latitude - b.latitude) * kmPerDegree
        val dLon = (a.longitude - b.longitude) * kmPerDegree * kotlin.math.cos(Math.toRadians((a.latitude + b.latitude) / 2.0))
        return kotlin.math.sqrt(dLat * dLat + dLon * dLon)
    }

    /**
     * Whether a pass takes the position the phone already holds for the chosen source, read without a request
     * (`DeviceLocationProvider.heldFix`, decision 101): no older than [HELD_MAX_AGE_MS], and [HELD_MOVE_KM] or more from
     * the one the loop keeps (or none kept). Another app that asked the phone where it is -- a map, a navigator -- has
     * left it there; no radio is started for it.
     */
    fun takesHeldPosition(
        held: com.paperscrape.livewallpaper.location.DeviceLocationFix,
        heldAgeMillis: Long,
        kept: com.paperscrape.livewallpaper.location.DeviceLocationFix?,
    ): Boolean = heldAgeMillis <= HELD_MAX_AGE_MS && (kept == null || kmBetween(held, kept) >= HELD_MOVE_KM)

    /** How old a position the phone holds may be for a pass to take it: 30 minutes (decision 101). */
    const val HELD_MAX_AGE_MS = 30 * 60 * 1000L

    /** How far a held position must be from the one kept for a pass to take it: 100 m (decision 101). */
    const val HELD_MOVE_KM = 0.1

    /**
     * The snapshot the loop holds after a fetch for [fetchedFor]: what it [fetched], or -- when
     * the fetch produced nothing -- the one it [held], **unless that one was for another place**.
     *
     * A failed fetch keeps the last known-good conditions on screen ([LiveWeatherStatus.STALE]),
     * which is right while they are this place's. After the user moves the Custom location, or the
     * phone reports a new town, they are not: kept, after a failed first fetch for the new place
     * the old place's weather would draw until a later fetch succeeded -- minutes for a
     * transient failure, up to an hour otherwise. So the old place's
     * conditions are dropped and the scene shows the theme's own until this place's arrive.
     * [heldFor] is where [held] was fetched; returns the snapshot and where it is for.
     */
    fun heldAfterFetch(
        held: LiveWeatherSnapshot?,
        heldFor: com.paperscrape.livewallpaper.location.DeviceLocationFix?,
        fetched: LiveWeatherSnapshot?,
        fetchedFor: com.paperscrape.livewallpaper.location.DeviceLocationFix,
    ): Pair<LiveWeatherSnapshot?, com.paperscrape.livewallpaper.location.DeviceLocationFix?> = when {
        fetched != null -> fetched to fetchedFor
        held != null && heldFor != null && !isSamePlace(heldFor, fetchedFor) -> null to null
        else -> held to heldFor
    }

    /**
     * Whether a snapshot is recent enough to draw, per [SNAPSHOT_MAX_AGE_MILLIS].
     *
     * A negative age -- the wall clock moved backwards between the fetch and now -- counts as
     * usable: the data is not old, the clock is wrong, and expiring perfectly good conditions
     * because someone changed the time zone would be its own bug. (The attempt schedule is on
     * `elapsedRealtime`, see [isAttemptDue]; only this expiry reads the wall clock, because
     * the snapshot's stamp is one.)
     */
    fun snapshotIsUsable(snapshot: LiveWeatherSnapshot?, nowMillis: Long): Boolean {
        if (snapshot == null) return false
        return nowMillis - snapshot.fetchedAtMillis < SNAPSHOT_MAX_AGE_MILLIS
    }

    /**
     * The status to publish and the snapshot the scene may draw, decided together.
     *
     * [result] is the outcome of a fetch made on this pass, or null when none was due -- the same
     * meaning [LiveWeatherStatus.of] already gives it. [snapshot] is the newest snapshot held,
     * whether it arrived on this pass or an earlier one.
     *
     * The two post-conditions, both pinned by tests:
     * 1. `snapshotForScene` is non-null only when it is [snapshot] and it is within the age cap;
     * 2. `status.isDrivingTheScene == (snapshotForScene != null)`.
     */
    fun decide(
        enabled: Boolean,
        hasLocation: Boolean,
        result: WeatherFetchResult?,
        snapshot: LiveWeatherSnapshot?,
        nowMillis: Long,
        previous: LiveWeatherStatus,
    ): LiveWeatherDecision {
        val usable = snapshot?.takeIf { snapshotIsUsable(it, nowMillis) }
        val reported = LiveWeatherStatus.of(
            enabled = enabled,
            hasLocation = hasLocation,
            result = result,
            hasSnapshotInEffect = usable != null,
            previous = previous,
        )
        // A status carried forward from a pass when conditions *were* driving the scene has to
        // stop saying so once the last of them expires. Without this the loop could sit on OK or
        // STALE with nothing left to draw -- the same disagreement between the screen and the sky,
        // arrived at by waiting instead of by switching something off. A provider's limit, or its refusal, stays the
        // reason it gives: only the conditions it was still showing are gone.
        val status = when {
            !reported.isDrivingTheScene || usable != null -> reported
            reported == LiveWeatherStatus.RATE_LIMITED_STALE -> LiveWeatherStatus.RATE_LIMITED
            reported == LiveWeatherStatus.REFUSED_STALE -> LiveWeatherStatus.REFUSED
            else -> LiveWeatherStatus.FAILED
        }
        return LiveWeatherDecision(
            status = status,
            snapshotForScene = if (status.isDrivingTheScene) usable else null,
            // Held, switched on, and too old to draw: the one way the scene leaves Live Weather
            // that nobody chose. See [LiveWeatherDecision.lapsed].
            lapsed = enabled && snapshot != null && usable == null,
        )
    }
}

/**
 * One pass's answer: what the settings screen is told, and what the renderer is allowed to draw.
 *
 * They travel together so that no caller can apply one without the other -- applied one at a time,
 * they come apart.
 */
data class LiveWeatherDecision(
    val status: LiveWeatherStatus,
    val snapshotForScene: LiveWeatherSnapshot?,
    /**
     * True when the scene is on the theme's own weather **because the conditions it held aged
     * out**, not because anybody switched anything: Live Weather is on, a snapshot is held, and it
     * is past [LiveWeatherSchedule.SNAPSHOT_MAX_AGE_MILLIS].
     *
     * The renderer eases the sky across this change and snaps it across every other one. The
     * distinction is the one `CloudCoverFade` draws: switching the
     * feature off, turning location off or choosing a provider with no key are deliberate acts
     * made with the settings screen in front of the user, and the sky answering at once is the
     * sign that the switch worked. An expiry happens three hours after the last good reading,
     * with nobody looking at a setting -- the same event, for the person watching the wallpaper,
     * as a forecast changing, and a forecast change dissolves over nine seconds.
     */
    val lapsed: Boolean = false,
)
