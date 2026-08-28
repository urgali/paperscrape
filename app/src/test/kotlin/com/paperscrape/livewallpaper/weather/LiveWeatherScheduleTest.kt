package com.paperscrape.livewallpaper.weather

import com.paperscrape.livewallpaper.engine.PrecipitationType
import com.paperscrape.livewallpaper.engine.WEATHER_CHECK_INTERVAL_MS
import com.paperscrape.livewallpaper.engine.WEATHER_REFRESH_INTERVAL_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live-weather loop's own rules: when to try again, and what may draw.
 *
 * These are the tests the scheduling never had. Until v4.8 the whole of it lived inline in
 * `PaperWallpaperService`'s `while (true)`, where no unit test could reach it, and three separate
 * defects sat there undisturbed -- a transient failure that cost a full hour, a status that said
 * the theme was drawing the scene while a fetched snapshot still was, and conditions with no expiry
 * at all. What was testable was the *predicate* for a single fetch (`LiveWeatherRefreshTest`),
 * which is a different question from what the loop does over time.
 *
 * **No clock is read here.** Every time is an explicit `Long`, so there is nothing to flake: no
 * sleep, no dispatcher, no wall clock. That is also why the schedule takes `nowMillis` as a
 * parameter rather than calling `System.currentTimeMillis()` itself.
 */
class LiveWeatherScheduleTest {

    private val minute = 60 * 1000L
    private val hour = 60 * minute

    private fun snapshotAt(millis: Long) = LiveWeatherSnapshot(
        precipitationType = PrecipitationType.RAIN,
        precipitationIntensity = 0.5f,
        cloudCoverFraction = 0.9f,
        isThunderstorm = false,
        fetchedAtMillis = millis,
    )

    private fun failed(reason: WeatherFailure) =
        WeatherFetchResult.Failed(reason, WeatherProviderId.OPEN_METEO)

    private val success = WeatherFetchResult.Success(
        WeatherObservation(
            precipitationMm = 1.0,
            cloudCoverPercent = 90,
            condition = WeatherCondition.RAIN,
            observedAtMillis = 0L,
            source = WeatherProviderId.OPEN_METEO,
        ),
    )

    // ---------------------------------------------------------------- the constants relate

    /**
     * The cap is *derived* from the refresh interval, so "three intervals" cannot rot; what a test
     * still has to guard is that three intervals is a **defensible duration**.
     *
     * Asserting the absolute value is the point. The derivation makes a changed refresh interval
     * carry the cap with it automatically -- which is right, and is also how a cap could quietly
     * become 20 minutes or 12 hours without anyone deciding that. This fails at that moment and
     * sends the reader to `SNAPSHOT_MAX_AGE_MILLIS`'s own reasoning: hours-scale because cloud
     * cover and precipitation are hours-scale, and more than one interval so that a late refresh
     * does not flick the scene back to theme weather and forward again.
     */
    @Test
    fun `the age cap is three refresh intervals, and that is three hours`() {
        assertEquals(3 * WEATHER_REFRESH_INTERVAL_MS, LiveWeatherSchedule.SNAPSHOT_MAX_AGE_MILLIS)
        assertEquals(3 * hour, LiveWeatherSchedule.SNAPSHOT_MAX_AGE_MILLIS)
        // More than one interval, or the cap undoes the STALE design it is meant to bound.
        assertTrue(LiveWeatherSchedule.SNAPSHOT_MAX_AGE_MILLIS > WEATHER_REFRESH_INTERVAL_MS)
    }

    /** A retry can only land on a tick, so the first one is exactly one tick away. */
    @Test
    fun `the first retry delay is the loop's own tick`() {
        assertEquals(WEATHER_CHECK_INTERVAL_MS, LiveWeatherSchedule.RETRY_BASE_MILLIS)
    }

    // ---------------------------------------------------------------- 1. success -> normal

    @Test
    fun `with nothing failing the next attempt is the normal interval away`() {
        assertEquals(
            WEATHER_REFRESH_INTERVAL_MS,
            LiveWeatherSchedule.nextAttemptDelayMillis(0, WEATHER_REFRESH_INTERVAL_MS),
        )
    }

    // ---------------------------------------------------------------- 2. transient -> early retry

    /**
     * The WEA-01 regression, stated as the thing that was wrong: one dropped request used to cost
     * a full hour because the timer was stamped before the fetch and never reset by its outcome.
     */
    @Test
    fun `one transient failure retries in minutes, not in an hour`() {
        val delay = LiveWeatherSchedule.nextAttemptDelayMillis(1, WEATHER_REFRESH_INTERVAL_MS)
        assertEquals(2 * minute, delay)
        assertTrue("a transient failure must not cost the normal interval", delay < WEATHER_REFRESH_INTERVAL_MS)
    }

    @Test
    fun `only the failures a retry can fix are transient`() {
        assertTrue(LiveWeatherSchedule.isTransient(failed(WeatherFailure.NETWORK)))
        assertTrue(LiveWeatherSchedule.isTransient(failed(WeatherFailure.HTTP_ERROR)))
        assertTrue(LiveWeatherSchedule.isTransient(failed(WeatherFailure.MALFORMED_RESPONSE)))
        // Answers, not accidents: the service was reached and said no.
        assertFalse(LiveWeatherSchedule.isTransient(failed(WeatherFailure.UNAUTHORIZED)))
        assertFalse(LiveWeatherSchedule.isTransient(failed(WeatherFailure.RATE_LIMITED)))
        // Nothing was sent at all, so there is nothing to retry.
        assertFalse(LiveWeatherSchedule.isTransient(WeatherFetchResult.MissingApiKey))
        assertFalse(LiveWeatherSchedule.isTransient(success))
        assertFalse("no fetch is not a failure", LiveWeatherSchedule.isTransient(null))
    }

    /** A rejected key must not buy a faster schedule, however many times it is rejected. */
    @Test
    fun `a rejected key never shortens the interval`() {
        // The loop only increments the counter for transient failures, so a rejected key leaves it
        // at zero however often it happens -- which is the normal interval.
        assertEquals(
            WEATHER_REFRESH_INTERVAL_MS,
            LiveWeatherSchedule.nextAttemptDelayMillis(0, WEATHER_REFRESH_INTERVAL_MS),
        )
    }

    // ---------------------------------------------------------------- 3. no aggressive polling

    /**
     * The backoff is bounded and converges: an outage must not turn a live wallpaper into a
     * two-minute network poll for as long as it lasts.
     */
    @Test
    fun `repeated failures back off and converge on the normal interval`() {
        val delays = (1..8).map { LiveWeatherSchedule.nextAttemptDelayMillis(it, WEATHER_REFRESH_INTERVAL_MS) }
        assertEquals(
            listOf(2 * minute, 4 * minute, 8 * minute, 16 * minute, 32 * minute, hour, hour, hour),
            delays,
        )
        // Monotonic, so a longer outage never asks more often than a shorter one.
        assertEquals(delays.sorted(), delays)
        // And never more often than the loop can act on.
        assertTrue(delays.all { it >= LiveWeatherSchedule.RETRY_BASE_MILLIS })
    }

    /** What that costs, stated as requests rather than as a ladder: four extra in the first hour. */
    @Test
    fun `an outage costs four extra requests in the first hour and none after`() {
        var t = 0L
        var failures = 1
        val attempts = mutableListOf<Long>()
        repeat(12) {
            t += LiveWeatherSchedule.nextAttemptDelayMillis(failures, WEATHER_REFRESH_INTERVAL_MS)
            attempts += t
            failures++
        }
        assertEquals(
            listOf(2L, 6L, 14L, 30L, 62L).map { it * minute },
            attempts.filter { it <= 62 * minute },
        )
        assertEquals("extra attempts inside the first hour", 4, attempts.count { it < hour })
    }

    /** A counter that ran away must not overflow into a negative delay, which reads as "due now". */
    @Test
    fun `an absurd failure count still yields the normal interval`() {
        assertEquals(
            WEATHER_REFRESH_INTERVAL_MS,
            LiveWeatherSchedule.nextAttemptDelayMillis(Int.MAX_VALUE, WEATHER_REFRESH_INTERVAL_MS),
        )
    }

    // ---------------------------------------------------------------- 4. recovery

    @Test
    fun `a success after failures returns the loop to the normal interval`() {
        // The loop resets the counter on any non-transient outcome; zero is the normal cadence.
        assertTrue(LiveWeatherSchedule.nextAttemptDelayMillis(5, WEATHER_REFRESH_INTERVAL_MS) < hour)
        assertEquals(
            WEATHER_REFRESH_INTERVAL_MS,
            LiveWeatherSchedule.nextAttemptDelayMillis(0, WEATHER_REFRESH_INTERVAL_MS),
        )
    }

    // ---------------------------------------------------------------- 5/6. the age cap

    @Test
    fun `a snapshot inside the age cap is usable`() {
        val now = 10 * hour
        assertTrue(LiveWeatherSchedule.snapshotIsUsable(snapshotAt(now), now))
        assertTrue(LiveWeatherSchedule.snapshotIsUsable(snapshotAt(now - hour), now))
        assertTrue(
            "one millisecond inside the cap is inside it",
            LiveWeatherSchedule.snapshotIsUsable(snapshotAt(now - (3 * hour - 1)), now),
        )
    }

    @Test
    fun `a snapshot past the age cap is not usable`() {
        val now = 10 * hour
        assertFalse(LiveWeatherSchedule.snapshotIsUsable(snapshotAt(now - 3 * hour), now))
        assertFalse(LiveWeatherSchedule.snapshotIsUsable(snapshotAt(now - 24 * hour), now))
        assertFalse(LiveWeatherSchedule.snapshotIsUsable(null, now))
    }

    /** A clock that moved backwards is a wrong clock, not old data. */
    @Test
    fun `a snapshot from the future is not expired`() {
        val now = 10 * hour
        assertTrue(LiveWeatherSchedule.snapshotIsUsable(snapshotAt(now + hour), now))
    }

    // ---------------------------------------------------------------- 7. stale + not authorised

    /**
     * The WEA-06 regression: an expired snapshot stops driving, and the status stops claiming it is.
     */
    @Test
    fun `an expired snapshot neither draws nor is reported as driving`() {
        val now = 10 * hour
        val decision = LiveWeatherSchedule.decide(
            enabled = true,
            hasLocation = true,
            result = null,
            snapshot = snapshotAt(now - 4 * hour),
            nowMillis = now,
            previous = LiveWeatherStatus.OK,
        )
        assertNull("expired conditions must not draw", decision.snapshotForScene)
        assertEquals(LiveWeatherStatus.FAILED, decision.status)
        assertFalse(decision.status.isDrivingTheScene)
    }

    /** A dropped request with fresh data still shows it -- the STALE design is deliberate. */
    @Test
    fun `a transient failure with fresh data keeps drawing it as stale`() {
        val now = 10 * hour
        val held = snapshotAt(now - 30 * minute)
        val decision = LiveWeatherSchedule.decide(
            enabled = true,
            hasLocation = true,
            result = failed(WeatherFailure.NETWORK),
            snapshot = held,
            nowMillis = now,
            previous = LiveWeatherStatus.OK,
        )
        assertEquals(LiveWeatherStatus.STALE, decision.status)
        assertSame(held, decision.snapshotForScene)
    }

    /** The same failure once the data has expired is FAILED, and nothing draws. */
    @Test
    fun `the same failure with expired data is a failure, not stale`() {
        val now = 10 * hour
        val decision = LiveWeatherSchedule.decide(
            enabled = true,
            hasLocation = true,
            result = failed(WeatherFailure.NETWORK),
            snapshot = snapshotAt(now - 5 * hour),
            nowMillis = now,
            previous = LiveWeatherStatus.STALE,
        )
        assertEquals(LiveWeatherStatus.FAILED, decision.status)
        assertNull(decision.snapshotForScene)
    }

    // ---------------------------------------------------------------- 8/9/10. the modes

    /** Off is off: nothing held may draw, whatever was fetched before. */
    @Test
    fun `with Live Weather off a held snapshot does not drive the scene`() {
        val now = 10 * hour
        val decision = LiveWeatherSchedule.decide(
            enabled = false,
            hasLocation = true,
            result = null,
            snapshot = snapshotAt(now),
            nowMillis = now,
            previous = LiveWeatherStatus.OK,
        )
        assertEquals(LiveWeatherStatus.OFF, decision.status)
        assertNull(decision.snapshotForScene)
    }

    /**
     * The WEA-02 regression, in the shape it was reproduced in: location switched off while a
     * perfectly fresh snapshot is in hand. The status has always said NO_LOCATION here -- which
     * unlocks the theme's cloud and precipitation controls -- and the snapshot used to keep
     * drawing anyway, so those controls did nothing.
     */
    @Test
    fun `losing the location stops the fetched conditions drawing`() {
        val now = 10 * hour
        val decision = LiveWeatherSchedule.decide(
            enabled = true,
            hasLocation = false,
            result = null,
            snapshot = snapshotAt(now),
            nowMillis = now,
            previous = LiveWeatherStatus.OK,
        )
        assertEquals(LiveWeatherStatus.NO_LOCATION, decision.status)
        assertTrue(decision.status.isRunningOnThemeWeather)
        assertNull("the theme was told it drives the scene, so it must", decision.snapshotForScene)
    }

    /** The same for the two key states, which are the other half of the WEA-02 shape. */
    @Test
    fun `a missing or rejected key stops the fetched conditions drawing`() {
        val now = 10 * hour
        val fresh = snapshotAt(now)
        val missing = LiveWeatherSchedule.decide(
            enabled = true, hasLocation = true, result = WeatherFetchResult.MissingApiKey,
            snapshot = fresh, nowMillis = now, previous = LiveWeatherStatus.OK,
        )
        assertEquals(LiveWeatherStatus.MISSING_API_KEY, missing.status)
        assertNull(missing.snapshotForScene)

        val rejected = LiveWeatherSchedule.decide(
            enabled = true, hasLocation = true, result = failed(WeatherFailure.UNAUTHORIZED),
            snapshot = fresh, nowMillis = now, previous = LiveWeatherStatus.OK,
        )
        assertEquals(LiveWeatherStatus.REJECTED_API_KEY, rejected.status)
        assertNull(rejected.snapshotForScene)
    }

    /** A device source with a fresh success draws, which is the whole point of the feature. */
    @Test
    fun `a successful fetch drives the scene`() {
        val now = 10 * hour
        val fetched = snapshotAt(now)
        val decision = LiveWeatherSchedule.decide(
            enabled = true, hasLocation = true, result = success,
            snapshot = fetched, nowMillis = now, previous = LiveWeatherStatus.NO_LOCATION,
        )
        assertEquals(LiveWeatherStatus.OK, decision.status)
        assertSame(fetched, decision.snapshotForScene)
    }

    // ---------------------------------------------------------------- 11. one rule, one answer

    /**
     * **The invariant the whole class exists for**, over every combination that reaches it: what
     * the settings screen is told and what the renderer is given are the same decision.
     */
    @Test
    fun `the status drives the scene exactly when a snapshot is handed to the renderer`() {
        val now = 10 * hour
        val results = listOf(
            null,
            success,
            WeatherFetchResult.MissingApiKey,
            failed(WeatherFailure.NETWORK),
            failed(WeatherFailure.UNAUTHORIZED),
            failed(WeatherFailure.RATE_LIMITED),
            failed(WeatherFailure.HTTP_ERROR),
            failed(WeatherFailure.MALFORMED_RESPONSE),
        )
        val snapshots = listOf(null, snapshotAt(now), snapshotAt(now - 2 * hour), snapshotAt(now - 9 * hour))
        var checked = 0
        for (enabled in listOf(true, false)) {
            for (hasLocation in listOf(true, false)) {
                for (result in results) {
                    for (snapshot in snapshots) {
                        for (previous in LiveWeatherStatus.entries) {
                            val d = LiveWeatherSchedule.decide(
                                enabled, hasLocation, result, snapshot, now, previous,
                            )
                            assertEquals(
                                "enabled=$enabled location=$hasLocation result=$result " +
                                    "snapshot=${snapshot?.fetchedAtMillis} previous=$previous",
                                d.status.isDrivingTheScene,
                                d.snapshotForScene != null,
                            )
                            // And nothing expired is ever handed over.
                            d.snapshotForScene?.let {
                                assertTrue(LiveWeatherSchedule.snapshotIsUsable(it, now))
                            }
                            checked++
                        }
                    }
                }
            }
        }
        assertEquals(2 * 2 * results.size * snapshots.size * LiveWeatherStatus.entries.size, checked)
    }

    // ---------------------------------------------------------------- 12. switching modes

    /**
     * Turning Live Weather back on with an old snapshot still in hand must not flash yesterday's
     * sky before the first fetch lands.
     */
    @Test
    fun `re-enabling with an expired snapshot does not draw it`() {
        val now = 10 * hour
        val decision = LiveWeatherSchedule.decide(
            enabled = true,
            hasLocation = true,
            result = null,
            snapshot = snapshotAt(now - 8 * hour),
            nowMillis = now,
            previous = LiveWeatherStatus.OFF,
        )
        assertNull(decision.snapshotForScene)
        assertFalse(decision.status.isDrivingTheScene)
    }

    /** A custom location behaves like any other source once a fix exists: fresh data draws. */
    @Test
    fun `switching from a device source to custom keeps one coherent answer`() {
        val now = 10 * hour
        val fresh = snapshotAt(now - 10 * minute)
        // Between the switch and the first fetch for the new place there is no fix yet.
        val betweenModes = LiveWeatherSchedule.decide(
            enabled = true, hasLocation = false, result = null,
            snapshot = fresh, nowMillis = now, previous = LiveWeatherStatus.OK,
        )
        assertEquals(LiveWeatherStatus.NO_LOCATION, betweenModes.status)
        assertNull("the old town's weather must not draw for the new one", betweenModes.snapshotForScene)

        // Once the custom fix is in place and a fetch succeeds, it draws again.
        val afterFetch = LiveWeatherSchedule.decide(
            enabled = true, hasLocation = true, result = success,
            snapshot = fresh, nowMillis = now, previous = betweenModes.status,
        )
        assertEquals(LiveWeatherStatus.OK, afterFetch.status)
        assertSame(fresh, afterFetch.snapshotForScene)
    }
}
