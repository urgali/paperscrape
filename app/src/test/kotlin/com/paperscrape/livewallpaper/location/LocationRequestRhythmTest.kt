package com.paperscrape.livewallpaper.location

import com.paperscrape.livewallpaper.engine.SolarDaySchedule
import com.paperscrape.livewallpaper.engine.WEATHER_CHECK_INTERVAL_MS
import com.paperscrape.livewallpaper.engine.WEATHER_REFRESH_INTERVAL_MS
import com.paperscrape.livewallpaper.weather.LiveWeatherSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **How often the phone is searched for its position**, measured on a clock this test moves (v5.10D,
 * inventory I-207 and I-282, the maintainer's *sì* of 2026-09-30 to row 5; v5.10E, his decision of
 * 2026-10-04 on the tries after a search that finds nothing).
 *
 * The line under GPS and Network said "checked at most once an hour", and the weather loop's comment
 * promised "an upper bound of one request per refresh interval in steady state". Neither held until
 * v5.10D: the loop asked the phone on every weather attempt -- every 2, 4, 8 minutes during a weather
 * outage -- and a real request followed whenever the system's cached position was older than fifteen
 * minutes; and with no position ever received, every two-minute pass searched for up to 20 s. v5.10D
 * made it one an hour, whoever asks; and a search that found nothing then waited the whole hour, which
 * the maintainer refused: *«riprovi dopo 5, 15 e 30 minuti - anche se ha già una posizione»*.
 *
 * [Loop] replays the wallpaper's loop pass by pass with the same pure parts the service calls, in the
 * service's order: [SolarDaySchedule.onTick], the provider's plan ([DeviceLocationProvider.plan]: cache
 * younger than [DeviceLocationProvider.FRESH_ENOUGH_MS] first, then a request only if
 * [LocationRequestThrottle] allows, and how it ended told back), the saved-position fallback, the
 * weather fetch and its retry ladder ([LiveWeatherSchedule]), and the wait before the next pass
 * ([SolarDaySchedule.nextPassDelayMillis]). [Rule] replays the three rules each test compares: 5.9's
 * (asked only for a due weather refresh, no throttle), v5.10D's (one an hour, a failure waits the hour)
 * and today's. That the service is wired this way is held on its source at the end.
 */
class LocationRequestRhythmTest {

    private val hour = 60 * 60 * 1000L
    private val minute = 60 * 1000L

    private enum class Rule { V5_9, V5_10D, NOW }

    /**
     * One engine's loop. [radioAnswers]: whether a real request gets a position; [weatherFails]:
     * whether a fetch at a given moment is a transient failure; [cacheTouchedAt]: when somebody else
     * on the phone last had a position (null: nobody, the system has no cached one); [heldFromStart]:
     * a position held from the start (the saved one), as on a phone that has had one before.
     */
    private class Loop(
        val clock: () -> Long,
        val throttle: LocationRequestThrottle,
        val rule: Rule = Rule.NOW,
        val liveWeather: Boolean = true,
        val radioAnswers: (Long) -> Boolean = { true },
        val weatherFails: (Long) -> Boolean = { false },
        val cacheTouchedAt: () -> Long? = { null },
        heldFromStart: Boolean = false,
    ) {
        val requestTimes = mutableListOf<Long>()
        val requests get() = requestTimes.size
        var hasPosition = heldFromStart
        var lastFetch = Long.MIN_VALUE / 4
        var failures = 0
        var lastRealFix: Long? = null

        fun pass(forceNextAsk: Boolean = false) {
            val now = clock()
            val delay = LiveWeatherSchedule.nextAttemptDelayMillis(failures, WEATHER_REFRESH_INTERVAL_MS)
            val due = LiveWeatherSchedule.isAttemptDue(now - lastFetch, delay)
            val ask = if (rule == Rule.V5_9) {
                liveWeather && due // the v5.10C rule: only for a due weather refresh
            } else {
                SolarDaySchedule.onTick(
                    followRealTime = true, liveWeatherEnabled = liveWeather, deviceSource = true,
                    devicePositionUsable = true, weatherAttemptDue = due, hasPosition = hasPosition,
                    holdsDevicePosition = hasPosition, dayIsStale = false,
                    userAskPending = forceNextAsk,
                    deviceRetryDue = rule == Rule.NOW && throttle.retryDue(),
                ) == SolarDaySchedule.Action.ASK_DEVICE
            }
            if (ask) {
                // DeviceLocationProvider.currentFix: the freshest cached position wins if young enough.
                val cached = listOfNotNull(lastRealFix, cacheTouchedAt()).maxOrNull()
                val step = if (rule == Rule.V5_9) {
                    if (cached != null && now - cached <= DeviceLocationProvider.FRESH_ENOUGH_MS) {
                        DeviceLocationProvider.FixStep.FRESH_CACHE
                    } else {
                        DeviceLocationProvider.FixStep.REQUEST
                    }
                } else {
                    DeviceLocationProvider.plan(
                        permitted = true,
                        cachedAgeMillis = cached?.let { now - it },
                        maxAgeMillis = DeviceLocationProvider.FRESH_ENOUGH_MS,
                        providerOn = true,
                        throttle = throttle,
                        force = forceNextAsk,
                    )
                }
                when (step) {
                    DeviceLocationProvider.FixStep.FRESH_CACHE -> hasPosition = true
                    DeviceLocationProvider.FixStep.REQUEST -> {
                        requestTimes += now
                        val got = radioAnswers(now)
                        if (got) { lastRealFix = now; hasPosition = true }
                        // v5.10D counted the hour from the attempt and was told nothing more.
                        if (rule == Rule.NOW) throttle.requestEnded(got)
                    }
                    else -> Unit // a stale cached position or the saved one answers -- no radio either way
                }
            }
            if (liveWeather && hasPosition && due) {
                lastFetch = now
                failures = if (weatherFails(now)) failures + 1 else 0
            }
        }

        /** How long the service waits before the next pass. */
        fun nextWait(): Long = if (rule == Rule.NOW) {
            SolarDaySchedule.nextPassDelayMillis(WEATHER_CHECK_INTERVAL_MS, deviceSource = true, throttle.passDelay(WEATHER_CHECK_INTERVAL_MS))
        } else {
            WEATHER_CHECK_INTERVAL_MS
        }
    }

    private class FakeClock { var now = 0L; fun read() = now }

    private fun run(loop: Loop, clock: FakeClock, until: Long) {
        while (clock.now < until) {
            loop.pass()
            clock.now += loop.nextWait()
        }
    }

    private fun worstCase(rule: Rule, until: Long): Loop {
        // Indoors, GPS chosen, nothing ever answers: the case of the v5.10A table, and the worst one.
        val clock = FakeClock()
        val loop = Loop(clock::read, LocationRequestThrottle(clock::read), rule = rule, radioAnswers = { false })
        run(loop, clock, until)
        return loop
    }

    @Test
    fun `with no position ever arriving, 5_9 searched every two minutes, v5_10D once an hour, now 5, 15, 30 and the hour`() {
        assertEquals("5.9: every pass of three hours", 90, worstCase(Rule.V5_9, 3 * hour).requests)
        assertEquals("v5.10D: one an hour", 3, worstCase(Rule.V5_10D, 3 * hour).requests)
        val now = worstCase(Rule.NOW, 3 * hour)
        assertEquals(
            "now: at 0, 5, 20, 50 minutes, the hour at 110, then 5, 15, 30 again",
            listOf(0L, 5, 20, 50, 110, 115, 130, 160).map { it * minute },
            now.requestTimes,
        )
    }

    /**
     * **The numbers of the report**: the most searches the wallpaper can make, a GPS that never
     * answers, on screen the whole time, in any hour and in a day -- against 5.9 and v5.10D.
     */
    @Test
    fun `the most searches in an hour and in a day, against 5_9 and v5_10D`() {
        val day = 24 * hour
        val now = worstCase(Rule.NOW, day)
        val v510d = worstCase(Rule.V5_10D, day)
        val v59 = worstCase(Rule.V5_9, day)
        fun mostInAnyHour(times: List<Long>): Int = times.maxOf { start -> times.count { it in start until start + hour } }
        assertEquals("now, in a day", 54, now.requests)
        assertEquals("now, in any hour", 4, mostInAnyHour(now.requestTimes))
        assertEquals("v5.10D, in a day", 24, v510d.requests)
        assertEquals("v5.10D, in any hour", 1, mostInAnyHour(v510d.requestTimes))
        assertEquals("5.9, in a day", 720, v59.requests)
        assertEquals("5.9, in any hour", 30, mostInAnyHour(v59.requestTimes))
        // Each search may hold the GPS for up to 20 s: 18 minutes a day now, 8 in v5.10D, 4 hours in 5.9.
        assertEquals(18 * minute, now.requests * DeviceLocationProvider.REQUEST_TIMEOUT_MS)
    }

    @Test
    fun `during a weather outage the phone was asked on the retries, now once an hour`() {
        // The weather service down for three hours, the GPS working, nobody else on the phone asking
        // (no fresh system cache between our own fixes): no search fails, so no try is added.
        val outage = { t: Long -> t < 3 * hour }
        val clock = FakeClock()
        val beforeLoop = Loop(clock::read, LocationRequestThrottle(clock::read), rule = Rule.V5_9, weatherFails = outage)
        run(beforeLoop, clock, 3 * hour)
        val clock2 = FakeClock()
        val now = Loop(clock2::read, LocationRequestThrottle(clock2::read), weatherFails = outage)
        run(now, clock2, 3 * hour)

        assertTrue("before: more than one an hour (${beforeLoop.requests})", beforeLoop.requests > 3)
        assertEquals("now: one an hour", 3, now.requests)
    }

    @Test
    fun `in steady state it stays one an hour, and a fresh system cache costs nothing`() {
        val clock = FakeClock()
        val loop = Loop(clock::read, LocationRequestThrottle(clock::read))
        run(loop, clock, 24 * hour)
        assertTrue("at most one an hour over a day (${loop.requests})", loop.requests <= 24)

        // Somebody else on the phone keeps the system's position fresh: the wallpaper never asks.
        val clock2 = FakeClock()
        val shared = Loop(clock2::read, LocationRequestThrottle(clock2::read), cacheTouchedAt = { clock2.now - minute })
        run(shared, clock2, 24 * hour)
        assertEquals(0, shared.requests)
    }

    @Test
    fun `with Live Weather off a phone source with no position is still asked, with the tries`() {
        // v5.10D asks while none is held, Live Weather or not; v5.10E adds the 5, 15, 30 after a failure.
        val clock = FakeClock()
        val loop = Loop(clock::read, LocationRequestThrottle(clock::read), liveWeather = false, radioAnswers = { false })
        run(loop, clock, 5 * hour)
        assertEquals(listOf(0L, 5, 20, 50, 110, 115, 130, 160, 220, 225, 240, 270).map { it * minute }, loop.requestTimes)
    }

    /**
     * **«anche se ha già una posizione»**: with the saved position held and Live Weather off, nothing
     * asked the phone before v5.10E after a search that found nothing -- it waited for no reason at
     * all. Now the tries go out the same.
     */
    @Test
    fun `with a position already held and Live Weather off, a failed search is still tried again`() {
        val clock = FakeClock()
        val throttle = LocationRequestThrottle(clock::read)
        val loop = Loop(clock::read, throttle, liveWeather = false, radioAnswers = { it >= 20 * minute }, heldFromStart = true)
        // The user picks GPS: asked at once, and the search finds nothing.
        loop.pass(forceNextAsk = true)
        clock.now += loop.nextWait()
        run(loop, clock, 3 * hour)
        assertEquals("the choice, then 5 and 20 minutes after it -- and that one finds the position", listOf(0L, 5, 20).map { it * minute }, loop.requestTimes)
        // Back to the hour: nothing more while a position is held and Live Weather is off.
        assertTrue(!throttle.retryDue())
    }

    @Test
    fun `a source the user has just chosen is answered at once, whatever the wait says`() {
        val clock = FakeClock()
        val throttle = LocationRequestThrottle(clock::read)
        assertTrue("the first request of the process", throttle.tryAcquire())
        throttle.requestEnded(gotPosition = true)
        clock.now += 10 * minute
        assertTrue("a pass ten minutes later may not", !throttle.tryAcquire())
        assertTrue("GPS just picked on the settings screen may", throttle.tryAcquire(force = true))
        throttle.requestEnded(gotPosition = true)
        clock.now += 59 * minute
        assertTrue("and the hour counts from that request", !throttle.tryAcquire())
        clock.now += minute
        assertTrue(throttle.tryAcquire())
    }

    @Test
    fun `two engines of one process share the tries`() {
        // The home screen's engine and the picker's preview, both on screen, nothing answering: the
        // tries are the process's, so two engines make the requests one would.
        val clock = FakeClock()
        val throttle = LocationRequestThrottle(clock::read)
        val home = Loop(clock::read, throttle, radioAnswers = { false })
        val preview = Loop(clock::read, throttle, radioAnswers = { false })
        while (clock.now < 4 * hour) {
            home.pass()
            preview.pass()
            clock.now += minOf(home.nextWait(), preview.nextWait())
        }
        assertEquals(
            "four hours: the times of one engine",
            listOf(0L, 5, 20, 50, 110, 115, 130, 160, 220, 225).map { it * minute },
            (home.requestTimes + preview.requestTimes).sorted(),
        )
    }

    @Test
    fun `an engine's start forces nothing, and a choice made with the location off waits for it`() {
        // The two defects the read-only review of v5.10D found in the first version of the force,
        // replayed with the engine's own two rules (SolarDaySchedule.userChoseSource for setting it,
        // DeviceFixAnswer.consulted for spending it).
        val clock = FakeClock()
        val throttle = LocationRequestThrottle(clock::read)
        assertTrue("a request a minute before the engine starts", throttle.tryAcquire())
        throttle.requestEnded(gotPosition = true)
        clock.now += minute

        // A new engine (a rebind, the picker's preview): its first settings are not a choice.
        val forcedAtStart = SolarDaySchedule.userChoseSource(appliedBefore = false, previous = LocationSource.NONE, requested = LocationSource.GPS)
        assertTrue("so the hour holds", !throttle.tryAcquire(force = forcedAtStart))

        // The user picks GPS with the location off: the choice waits, unspent, pass after pass.
        var pending = SolarDaySchedule.userChoseSource(appliedBefore = true, previous = LocationSource.CUSTOM, requested = LocationSource.GPS)
        var locationOn = false
        var requests = 0
        fun pass() {
            val ask = SolarDaySchedule.onTick(
                followRealTime = true, liveWeatherEnabled = false, deviceSource = true, devicePositionUsable = true,
                weatherAttemptDue = false, hasPosition = true, holdsDevicePosition = true, dayIsStale = false,
                userAskPending = pending, deviceRetryDue = throttle.retryDue(),
            ) == SolarDaySchedule.Action.ASK_DEVICE
            if (!ask) return
            // DeviceLocationProvider.currentFix: no request with the provider off, and then not consulted.
            val step = DeviceLocationProvider.plan(
                permitted = true, cachedAgeMillis = null, maxAgeMillis = DeviceLocationProvider.FRESH_ENOUGH_MS,
                providerOn = locationOn, throttle = throttle, force = pending,
            )
            if (step == DeviceLocationProvider.FixStep.REQUEST) {
                requests++
                pending = false
                throttle.requestEnded(gotPosition = true)
            }
        }
        repeat(5) { pass(); clock.now += WEATHER_CHECK_INTERVAL_MS }
        assertEquals("nothing asked of a switched-off location", 0, requests)
        assertTrue("and the choice still waits", pending)
        locationOn = true
        pass()
        assertEquals("answered the moment it is on, inside the hour", 1, requests)
        repeat(10) { clock.now += WEATHER_CHECK_INTERVAL_MS; pass() }
        assertEquals("and once only", 1, requests)
    }

    @Test
    fun `a clock that moves backwards does not stop the requests for good`() {
        // The monotonic clock cannot, but the rule is not allowed to depend on that.
        var now = 10 * hour
        val throttle = LocationRequestThrottle({ now })
        assertTrue(throttle.tryAcquire())
        now = 5 * hour
        assertTrue("a negative gap reads as long ago", throttle.tryAcquire())
        throttle.requestEnded(gotPosition = false)
        now = 1 * hour
        assertTrue("and a try waiting reads as due", throttle.retryDue())
    }

    @Test
    fun `the service routes every ask through the provider, the provider through the throttle`() {
        val engine = source("engine/PaperWallpaperService.kt")
        assertTrue(engine.contains("locationRequestLock.withLock { locationProvider().currentFix(kind, forceRequest = force) }"))
        val provider = source("location/DeviceLocationProvider.kt")
        assertTrue("one process-wide throttle", provider.contains("throttle: LocationRequestThrottle = LocationRequestThrottle.shared"))
        // v5.10E: the provider decides through its plan, and the request is counted however it ends.
        assertTrue(provider.contains("val step = plan("))
        assertTrue(provider.contains("val live = throttle.counted { manager?.let { requestOnce(it, kind, timeoutMillis) } }"))
        assertEquals("told only through counted", 0, Regex("""requestEnded\(""").findAll(provider).count())
        assertTrue("the loop asks onTick with the phone's answer", engine.contains("devicePositionUsable = deviceAccess?.mayUsePosition == true,"))
        assertTrue(engine.contains("SolarDaySchedule.Action.FORGET_DEVICE_POSITION -> forgetDevicePosition()"))
        // The tries: the third reason to ask, for a phone source only, and the shorter wait.
        assertTrue(engine.contains("deviceRetryDue = source.deviceKind != null && LocationRequestThrottle.shared.retryDue(),"))
        assertTrue(engine.contains("retryPassDelayMillis = LocationRequestThrottle.shared.passDelay(WEATHER_CHECK_INTERVAL_MS),"))
        // The force: set only by a change after the engine's first settings, spent only when consulted.
        assertTrue(engine.contains("userChoseDeviceSource = SolarDaySchedule.userChoseSource(settingsApplied, locationSource, requestedSource)"))
        assertTrue(engine.contains("if (answer.consulted) userChoseDeviceSource = false"))
        assertTrue(engine.contains("userAskPending = userChoseDeviceSource,"))
        assertEquals("the declaration, the collector, the spend: set nowhere else", 3, Regex("""userChoseDeviceSource = """).findAll(engine).count())
        // I-282: the false bound is gone from the loop's comment, and the true one is said.
        assertTrue(!engine.contains("upper bound of one request per refresh interval in steady state;"))
        assertTrue(engine.contains("A request that finds nothing is tried again after 5, 15 and"))
        assertEquals(LocationRequestThrottle.MIN_INTERVAL_MILLIS, WEATHER_REFRESH_INTERVAL_MS)
    }

    private fun source(path: String): String {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/$path"
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = File(dir, "$prefix$suffix")
                if (f.isFile) return f.readText()
            }
            dir = dir.parentFile
        }
        error("source not found: $suffix")
    }
}
