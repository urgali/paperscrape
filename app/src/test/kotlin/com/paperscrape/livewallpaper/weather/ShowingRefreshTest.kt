package com.paperscrape.livewallpaper.weather

import com.paperscrape.livewallpaper.engine.WEATHER_CHECK_INTERVAL_MS
import com.paperscrape.livewallpaper.engine.WEATHER_REFRESH_INTERVAL_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The rule** (`DESIGN_NOTES.md` §12, decision 99): with the last success an hour old or more, every showing
 * of the wallpaper asks at once, whatever failed before; a request of a showing that finds no network is asked again
 * within seconds while the wallpaper stays on screen; nothing is asked off screen; a refusal waits the hour.
 * [ShowingRefresh] and the pure rules under it, every case and, below, every order of showings, answers and passes.
 */
class ShowingRefreshTest {

    private val hour = WEATHER_REFRESH_INTERVAL_MS
    private val minute = 60_000L
    private val ok = WeatherFetchResult.Success(WeatherObservation(cloudCoverPercent = 50, observedAtMillis = 0L, source = WeatherProviderId.OPEN_METEO))
    private fun failed(reason: WeatherFailure) = WeatherFetchResult.Failed(reason, WeatherProviderId.OPEN_METEO)
    private val network = failed(WeatherFailure.NETWORK)

    @Test
    fun `a showing asks when the last success is an hour old, and only then`() {
        for (newly in listOf(true, false)) for (since in listOf(-1L, 0L, hour - 1, hour, 5 * hour)) for (refused in listOf(true, false)) {
            val due = LiveWeatherSchedule.dueOnShowing(newly, since, refused, hour)
            assertEquals("newly=$newly since=$since refused=$refused", newly && !refused && (since < 0L || since >= hour), due)
        }
    }

    @Test
    fun `only a request that found no network is retried within seconds, and every refusal waits`() {
        for (reason in WeatherFailure.entries) {
            val r = failed(reason)
            assertEquals(reason.name, reason == WeatherFailure.NETWORK, LiveWeatherSchedule.isNetworkNotReady(r))
            assertEquals(reason.name, !LiveWeatherSchedule.isTransient(r), LiveWeatherSchedule.isRefusal(r))
        }
        assertTrue(LiveWeatherSchedule.isRefusal(WeatherFetchResult.MissingApiKey))
        assertFalse(LiveWeatherSchedule.isRefusal(ok))
        assertFalse(LiveWeatherSchedule.isNetworkNotReady(ok))
        assertFalse(LiveWeatherSchedule.isRefusal(null))
        assertEquals(listOf(5_000L, 10_000L, 20_000L, -1L, -1L), (0..4).map { LiveWeatherSchedule.quickRetryDelayMillis(it) })
        assertEquals(-1L, LiveWeatherSchedule.quickRetryDelayMillis(-1))
    }

    @Test
    fun `the wait is the tick unless a quick retry falls due sooner, never under a tenth of a second`() {
        assertEquals(WEATHER_CHECK_INTERVAL_MS, LiveWeatherSchedule.passDelayMillis(WEATHER_CHECK_INTERVAL_MS, -1L))
        assertEquals(5_000L, LiveWeatherSchedule.passDelayMillis(WEATHER_CHECK_INTERVAL_MS, 5_000L))
        assertEquals(100L, LiveWeatherSchedule.passDelayMillis(WEATHER_CHECK_INTERVAL_MS, 0L))
        assertEquals(1_000L, LiveWeatherSchedule.passDelayMillis(1_000L, 20_000L))
    }

    /** A fresh engine's first showing asks; after a success a showing does not within the hour, then at once past it. */
    @Test
    fun `a fresh engine asks, then a showing within the hour does not and one past it does`() {
        val s = ShowingRefresh()
        assertTrue(s.asks(now = 1_000L, showings = 1, intervalMillis = hour))
        assertEquals(0, s.answered(1_000L, ok, forShowing = true, transientFailures = 0))
        assertFalse("the same showing again", s.asks(2_000L, 1, hour))
        assertFalse("a showing half an hour on", s.asks(1_000L + 30 * minute, 2, hour))
        assertFalse("a showing a second short of the hour", s.asks(1_000L + hour - 1_000L, 3, hour))
        assertTrue("a showing on the hour", s.asks(1_000L + hour, 4, hour))
        assertTrue("a showing hours on", s.asks(1_000L + 5 * hour, 5, hour))
        assertFalse("no showing, no ask: the hourly timer is the loop's", s.asks(1_000L + 6 * hour, 5, hour))
    }

    /** After failures in a row, a showing past the hour asks at once, whatever the backoff says. */
    @Test
    fun `after failures in a row a showing past the hour asks at once`() {
        val s = ShowingRefresh()
        s.asks(0L, 1, hour)
        s.answered(0L, ok, forShowing = true, transientFailures = 0)
        var failures = 0
        var t = 2 * hour
        for (k in 0 until 5) {
            failures = s.answered(t, failed(WeatherFailure.HTTP_ERROR), forShowing = false, transientFailures = failures)
            t += 10 * minute
        }
        assertEquals(5, failures)
        assertTrue("the backoff waits 32 minutes, the showing does not", s.asks(t, 2, hour))
        assertTrue(LiveWeatherSchedule.nextAttemptDelayMillis(failures, hour) > 0L)
    }

    /** A refusal waits the hour from its attempt, as the backoff has it; a success after it lets showings ask again. */
    @Test
    fun `after a refusal a showing does not ask, until a success`() {
        for (refusal in listOf(failed(WeatherFailure.UNAUTHORIZED), failed(WeatherFailure.RATE_LIMITED), failed(WeatherFailure.REQUEST_REFUSED), WeatherFetchResult.MissingApiKey)) {
            val s = ShowingRefresh()
            assertTrue(s.asks(0L, 1, hour))
            assertEquals(0, s.answered(0L, refusal, forShowing = true, transientFailures = 2))
            assertFalse("$refusal: a showing asked", s.asks(3 * hour, 2, hour))
            assertEquals("$refusal: no quick retry", -1L, s.quickRetryInMillis(3 * hour))
            s.answered(4 * hour, ok, forShowing = false, transientFailures = 0)
            assertTrue("$refusal: after a success the rule is back", s.asks(6 * hour, 3, hour))
        }
    }

    /** No network at a showing: three quick retries at 5, 10 and 20 s, one failure for the burst, then the backoff. */
    @Test
    fun `a showing that finds no network asks again at 5, 10 and 20 seconds, counted as one failure`() {
        val s = ShowingRefresh()
        var t = 10 * hour
        assertTrue(s.asks(t, 1, hour))
        var failures = s.answered(t, network, forShowing = true, transientFailures = 0)
        assertEquals(1, failures)
        for (wait in listOf(5_000L, 10_000L, 20_000L)) {
            assertEquals(wait, s.quickRetryInMillis(t))
            assertFalse("not before it is due", s.asks(t + wait - 1, 1, hour))
            t += wait
            assertTrue("due at $wait", s.asks(t, 1, hour))
            failures = s.answered(t, network, forShowing = true, transientFailures = failures)
            assertEquals("the burst is one failure", 1, failures)
        }
        assertEquals("three spent: back on the backoff", -1L, s.quickRetryInMillis(t))
        assertFalse(s.asks(t + minute, 1, hour))
        // The next showing starts again.
        assertTrue(s.asks(t + 5 * minute, 2, hour))
        s.answered(t + 5 * minute, network, forShowing = true, transientFailures = failures)
        assertEquals(5_000L, s.quickRetryInMillis(t + 5 * minute))
    }

    /** The first request finds no network, the quick retry succeeds and ends the burst. */
    @Test
    fun `a quick retry that succeeds ends the burst and the hour starts from it`() {
        val s = ShowingRefresh()
        assertTrue(s.asks(0L, 1, hour))
        s.answered(0L, network, forShowing = true, transientFailures = 0)
        assertTrue(s.asks(5_000L, 1, hour))
        assertEquals(0, s.answered(5_000L, ok, forShowing = true, transientFailures = 1))
        assertEquals(-1L, s.quickRetryInMillis(5_001L))
        assertEquals(5_000L, s.lastSuccessAt)
        assertFalse(s.asks(5_000L + hour - 1, 2, hour))
        assertTrue(s.asks(5_000L + hour, 3, hour))
    }

    @Test
    fun `a server error or a request the timer made is not retried within seconds`() {
        for (reason in listOf(WeatherFailure.HTTP_ERROR, WeatherFailure.MALFORMED_RESPONSE)) {
            val s = ShowingRefresh()
            s.asks(0L, 1, hour)
            assertEquals(1, s.answered(0L, failed(reason), forShowing = true, transientFailures = 0))
            assertEquals(reason.name, -1L, s.quickRetryInMillis(0L))
        }
        val timer = ShowingRefresh()
        assertEquals(1, timer.answered(0L, network, forShowing = false, transientFailures = 0))
        assertEquals("the hourly request's own failure waits for the backoff", -1L, timer.quickRetryInMillis(0L))
    }

    /** Off screen nothing is asked: a pending quick retry goes, and the next showing starts its own. */
    @Test
    fun `off screen the quick retry goes, and the next showing starts again`() {
        val s = ShowingRefresh()
        s.asks(0L, 1, hour)
        s.answered(0L, network, forShowing = true, transientFailures = 0)
        s.asks(5_000L, 1, hour)
        s.answered(5_000L, network, forShowing = true, transientFailures = 1)
        s.offScreen()
        assertEquals(-1L, s.quickRetryInMillis(6_000L))
        assertFalse("the loop is parked; on its first pass after, the same count asks nothing", s.asks(60_000L, 1, hour))
        assertTrue(s.asks(90_000L, 2, hour))
        s.answered(90_000L, network, forShowing = true, transientFailures = 1)
        assertEquals("a new showing's first quick retry", 5_000L, s.quickRetryInMillis(90_000L))
    }

    /** A quick retry falling due when the loop cannot ask (no position, Live Weather off) is used up: no wait in the past. */
    @Test
    fun `a quick retry the loop cannot make leaves nothing pending`() {
        val s = ShowingRefresh()
        s.asks(0L, 1, hour)
        s.answered(0L, network, forShowing = true, transientFailures = 0)
        assertTrue(s.asks(5_000L, 1, hour))
        assertEquals(-1L, s.quickRetryInMillis(5_000L))
        assertEquals(WEATHER_CHECK_INTERVAL_MS, LiveWeatherSchedule.passDelayMillis(WEATHER_CHECK_INTERVAL_MS, s.quickRetryInMillis(5_000L)))
    }

    @Test
    fun `Live Weather off forgets the success, so turned on again a showing asks`() {
        val s = ShowingRefresh()
        s.asks(0L, 1, hour)
        s.answered(0L, ok, forShowing = true, transientFailures = 0)
        s.forget()
        assertTrue(s.asks(minute, 2, hour))
    }

    /**
     * **Every order**: random runs of showings, hidings, passes and answers of every kind, the loop's own use of the class
     * modelled -- asks once a pass on screen and never off it, a request when it asks or the hour is up, its answer handed
     * back. Held: at most four requests a showing for the rule (one and three quick retries); a request past the hour at
     * every showing unless the last answer was a refusal; a quick retry never more than 20 s away and never pending in the
     * past, where the loop would ask at every pass.
     */
    @Test
    fun `every order of showings, hidings, passes and answers keeps the rule`() {
        val answers = listOf<WeatherFetchResult>(ok, network, failed(WeatherFailure.HTTP_ERROR), failed(WeatherFailure.MALFORMED_RESPONSE),
            failed(WeatherFailure.UNAUTHORIZED), failed(WeatherFailure.RATE_LIMITED), failed(WeatherFailure.REQUEST_REFUSED))
        for (seed in 0 until 400) {
            val rnd = java.util.Random(seed.toLong())
            val s = ShowingRefresh()
            var t = 0L
            var showings = 0
            var onScreen = false
            var lastAttempt = Long.MIN_VALUE / 4
            var failures = 0
            var lastAnswer: WeatherFetchResult? = null
            var lastSuccess = Long.MIN_VALUE / 4
            var forShowingThisShowing = 0
            repeat(300) {
                when (rnd.nextInt(5)) {
                    0 -> if (!onScreen) { onScreen = true; showings++; forShowingThisShowing = 0 }
                    1 -> if (onScreen) { onScreen = false; s.offScreen() }
                    else -> Unit
                }
                t += listOf(100L, 3_000L, 5_000L, 20_000L, minute, 10 * minute, hour, 3 * hour)[rnd.nextInt(8)]
                if (!onScreen) return@repeat
                val newly = showings != showingsSeenBy(s)
                val wantedByRule = newly && lastAnswer?.let { LiveWeatherSchedule.isRefusal(it) } != true && t - lastSuccess >= hour
                val forShowing = s.asks(t, showings, hour)
                if (wantedByRule) assertTrue("seed $seed: a showing past the hour did not ask", forShowing)
                val timer = LiveWeatherSchedule.isAttemptDue(t - lastAttempt, LiveWeatherSchedule.nextAttemptDelayMillis(failures, hour))
                if (forShowing || timer) {
                    if (forShowing) forShowingThisShowing++
                    assertTrue("seed $seed: more than four requests for one showing", forShowingThisShowing <= 4)
                    lastAttempt = t
                    val answer = answers[rnd.nextInt(answers.size)]
                    failures = s.answered(t, answer, forShowing, failures)
                    lastAnswer = answer
                    if (answer is WeatherFetchResult.Success) lastSuccess = t
                    assertEquals(lastSuccess, s.lastSuccessAt)
                }
                val inMillis = s.quickRetryInMillis(t)
                assertTrue("seed $seed: a quick retry beyond 20 s", inMillis <= 20_000L)
                assertTrue("seed $seed: a quick retry pending in the past", inMillis != 0L)
            }
        }
    }

    /** The count of showings [s] has answered, read by reflection: the model above checks a showing is new as the class does. */
    private fun showingsSeenBy(s: ShowingRefresh): Int =
        ShowingRefresh::class.java.getDeclaredField("showingsSeen").apply { isAccessible = true }.getInt(s)
}
