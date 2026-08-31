package com.paperscrape.livewallpaper.weather

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The clock the weather schedule runs on, and the failure that made it worth naming.
 *
 * WEA-08: the service compared `System.currentTimeMillis()` against a wall-clock stamp. Moving the
 * device's clock backwards made the elapsed difference negative, and `negative >= delay` is false
 * for every delay -- so the loop decided a fetch was never due and Live Weather quietly stopped
 * refreshing until the wall clock caught back up. The reading is now monotonic, and the rule that
 * consumes it is here where it can be stated.
 */
class WeatherClockTest {

    private val hour = 60L * 60L * 1000L

    @Test
    fun `nothing is due before the delay has passed`() {
        assertFalse(LiveWeatherSchedule.isAttemptDue(0L, hour))
        assertFalse(LiveWeatherSchedule.isAttemptDue(hour - 1L, hour))
    }

    @Test
    fun `it is due exactly at the delay and after it`() {
        assertTrue(LiveWeatherSchedule.isAttemptDue(hour, hour))
        assertTrue(LiveWeatherSchedule.isAttemptDue(hour * 5, hour))
    }

    @Test
    fun `a negative elapsed reading is due rather than never`() {
        // The engine starts from a sentinel meaning "no attempt yet", and the old wall-clock code
        // turned that same shape -- a negative difference -- into "never due again".
        assertTrue(LiveWeatherSchedule.isAttemptDue(-1L, hour))
        assertTrue(LiveWeatherSchedule.isAttemptDue(Long.MIN_VALUE / 4, hour))
    }

    @Test
    fun `the retry backoff still shortens the delay this rule is measured against`() {
        // The two halves work together: the backoff decides the delay, this decides whether it has
        // elapsed. One transient failure must make a retry due well inside the hourly cadence.
        val retry = LiveWeatherSchedule.nextAttemptDelayMillis(1, hour)
        assertTrue("a first retry must be sooner than the normal interval", retry < hour)
        assertTrue(LiveWeatherSchedule.isAttemptDue(retry, retry))
        assertFalse(LiveWeatherSchedule.isAttemptDue(retry - 1L, retry))
    }
}
