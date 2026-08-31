package com.paperscrape.livewallpaper.weather

import com.paperscrape.livewallpaper.engine.WEATHER_REFRESH_INTERVAL_MS

/**
 * What the live-weather loop should do next, and whether what it already has may draw.
 *
 * Extracted and made pure for the same reason [LiveWeatherInputs] was: these are the rules the
 * feature's correctness rests on, they lived inline in the service's `while (true)` where nothing
 * could reach them, and each of the three had rotted in a different direction.
 *
 * ### The one rule
 *
 * [decide] answers a single question -- **may this snapshot draw the scene?** -- and returns both
 * halves of the answer together, so that the settings screen and the renderer cannot disagree:
 *
 * > `status.isDrivingTheScene` is true **if and only if** `snapshotForScene` is non-null.
 *
 * Before this existed the two were computed in different places from different inputs. The status
 * came from [LiveWeatherStatus.of]; the renderer's override was whatever the last successful fetch
 * had left behind, and only the "Live Weather is off" branch ever cleared it. So turning location
 * off, or switching to a provider whose key is missing or rejected, published a status whose
 * [LiveWeatherStatus.isRunningOnThemeWeather] was true -- which unlocks the theme's own cloud and
 * precipitation controls -- while the fetched conditions carried on drawing. The controls the user
 * had just been handed did nothing, and the sky disagreed with the screen that described it.
 *
 * ### What this does not change
 *
 * The four location modes keep their meanings, the [LiveWeatherStatus] states keep theirs, and
 * **the deliberate [LiveWeatherStatus.STALE] design is preserved**: a dropped request still leaves
 * the last known-good conditions on screen rather than flicking the scene back to the theme's
 * manual settings. What is new is that "last known-good" now has an expiry ([SNAPSHOT_MAX_AGE_MILLIS])
 * and that a status which says the theme is driving the scene now means it.
 */
object LiveWeatherSchedule {

    /**
     * How old a fetched snapshot may be and still draw: **three refresh intervals**, derived.
     *
     * Written as a multiple of [WEATHER_REFRESH_INTERVAL_MS] rather than as its own three-hour
     * literal, so the policy *is* the declaration and the two cannot drift apart: halving the
     * refresh interval halves this, and no test or reviewer has to notice. (A test pinned the
     * relationship before v4.9; a pinned relationship still lets a release ship an hour in which
     * the two disagree, which is exactly the window worth closing.)
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
     * was introduced to avoid, and an age cap must not undo it.
     *
     * Three is the smallest multiple that tolerates a couple of missed cycles without flapping,
     * and it is also about as long as a drawn sky can be defended: cloud cover and precipitation
     * are hours-scale, not days-scale, so a reading from three hours ago is a plausible sky while
     * one from yesterday is a fabrication. Past the cap the scene returns to the theme's own
     * weather, which is always a valid scene, and the status says so.
     */
    const val SNAPSHOT_MAX_AGE_MILLIS = 3 * WEATHER_REFRESH_INTERVAL_MS

    /**
     * The first retry delay after a transient failure, and the unit the backoff doubles.
     *
     * Equal to the loop's own tick, because the tick is the finest schedule the loop can actually
     * keep: asking for anything shorter would not be honoured and would only look precise.
     */
    const val RETRY_BASE_MILLIS = 2 * 60 * 1000L

    /**
     * Whether this outcome is worth trying again sooner than the normal interval.
     *
     * The vocabulary already existed -- [WeatherFailure] documents which reasons retrying helps --
     * but nothing consumed it: every outcome, success or failure, stamped the same hourly timer, so
     * a request dropped because the radio was not up yet cost a full hour of wrong weather. The
     * most repeatable case is a boot: the wallpaper rebinds and fetches before connectivity exists.
     *
     * [WeatherFetchResult.MissingApiKey] is not transient and never was -- nothing is sent in that
     * state, so there is nothing to retry until the user acts. [WeatherFailure.UNAUTHORIZED] and
     * [WeatherFailure.RATE_LIMITED] are answers, not accidents: the service was reached and said
     * no, and hammering it sooner neither helps the user nor is polite to the provider.
     */
    fun isTransient(result: WeatherFetchResult?): Boolean =
        result is WeatherFetchResult.Failed && when (result.reason) {
            WeatherFailure.NETWORK,
            WeatherFailure.HTTP_ERROR,
            WeatherFailure.MALFORMED_RESPONSE,
            -> true

            WeatherFailure.UNAUTHORIZED,
            WeatherFailure.RATE_LIMITED,
            -> false
        }

    /**
     * How long after the last attempt the next one may be made.
     *
     * Zero consecutive transient failures is the normal cadence. After that the delay doubles from
     * [RETRY_BASE_MILLIS] and is capped at [normalIntervalMillis], so the schedule converges back
     * to normal by itself instead of needing a separate "give up" rule:
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
     * That is **four extra requests in the first hour** of an outage and none after it -- bounded,
     * deterministic, and no more network than a user who opens the settings screen a few times.
     * A live wallpaper cannot afford a fixed short retry: an aeroplane, a tunnel or a dead Wi-Fi
     * network would turn it into a permanent 2-minute poll.
     */
    /**
     * Whether enough time has passed since the last attempt, given a monotonic elapsed reading.
     *
     * Stated here rather than inlined at the two call sites because the *clock* is the rule. Until
     * v4.14 the service compared `System.currentTimeMillis()` against a wall-clock stamp, and a
     * clock moved backwards -- a timezone edit, an NTP correction, a user setting the date -- made
     * the difference negative, so nothing was ever due again until the wall clock caught back up.
     * `elapsedSince` must come from `SystemClock.elapsedRealtime()`, which counts since boot,
     * includes deep sleep, and cannot go backwards.
     *
     * The negative case is still handled rather than assumed away: the first pass after a fresh
     * engine starts from a sentinel, and "a long time ago" must read as due.
     */
    fun isAttemptDue(elapsedSinceLastMillis: Long, delayMillis: Long): Boolean =
        elapsedSinceLastMillis < 0L || elapsedSinceLastMillis >= delayMillis

    fun nextAttemptDelayMillis(consecutiveTransientFailures: Int, normalIntervalMillis: Long): Long {
        if (consecutiveTransientFailures <= 0) return normalIntervalMillis
        // Doubling in Long arithmetic, but the shift is bounded first: a counter that ran away
        // would otherwise overflow into a negative delay, which reads as "always due".
        val steps = (consecutiveTransientFailures - 1).coerceAtMost(16)
        val backoff = RETRY_BASE_MILLIS shl steps
        return backoff.coerceAtMost(normalIntervalMillis)
    }

    /**
     * Whether a snapshot is recent enough to draw, per [SNAPSHOT_MAX_AGE_MILLIS].
     *
     * A negative age -- the wall clock moved backwards between the fetch and now -- counts as
     * usable: the data is not old, the clock is wrong, and expiring perfectly good conditions
     * because someone changed the time zone would be its own bug. (That the schedule reads the
     * wall clock at all is a separate, known issue and is not this batch's.)
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
        // arrived at by waiting instead of by switching something off.
        val status = if (reported.isDrivingTheScene && usable == null) {
            LiveWeatherStatus.FAILED
        } else {
            reported
        }
        return LiveWeatherDecision(
            status = status,
            snapshotForScene = if (status.isDrivingTheScene) usable else null,
        )
    }
}

/**
 * One pass's answer: what the settings screen is told, and what the renderer is allowed to draw.
 *
 * They travel together so that no caller can apply one without the other -- which is how they came
 * apart in the first place.
 */
data class LiveWeatherDecision(
    val status: LiveWeatherStatus,
    val snapshotForScene: LiveWeatherSnapshot?,
)
