package com.paperscrape.livewallpaper.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A cached position "from the future" is not fresh.**
 *
 * The age of the system's cached position is read on the clock that cannot move, where the position carries it, and
 * [DeviceLocationProvider.plan] takes a negative age for what it is: not fresh. Read on the wall clock, with a negative
 * age passing `<= maxAgeMillis`, the phone's clock moved back two hours -- by hand, or with `cmd alarm set-time` --
 * would make a position of ten minutes ago 110 minutes "in the future": it would stand in for every real request for
 * over two hours, and tell the throttle a position had arrived, which sets the tries after a failed search back to the
 * first. A [DeviceLocationProvider.plan] that takes a negative age for fresh fails here on its assertion, `FRESH_CACHE`.
 */
class CachedFixFromTheFutureTest {

    private val hour = 60 * 60 * 1000L

    private fun step(cachedAgeMillis: Long?, throttle: LocationRequestThrottle) = DeviceLocationProvider.plan(
        permitted = true,
        cachedAgeMillis = cachedAgeMillis,
        maxAgeMillis = DeviceLocationProvider.FRESH_ENOUGH_MS,
        providerOn = true,
        throttle = throttle,
        force = false,
    )

    @Test
    fun `a cached fix two hours in the future is not fresh`() {
        val now = 10 * hour
        val throttle = LocationRequestThrottle(now = { now })
        val step = step(-2 * hour, throttle)
        assertNotEquals(DeviceLocationProvider.FixStep.FRESH_CACHE, step)
        assertEquals("a request is made, as for any old position", DeviceLocationProvider.FixStep.REQUEST, step)
    }

    @Test
    fun `a millisecond in the future is the future too, and now is fresh`() {
        val now = 10 * hour
        assertNotEquals(DeviceLocationProvider.FixStep.FRESH_CACHE, step(-1L, LocationRequestThrottle(now = { now })))
        assertEquals(DeviceLocationProvider.FixStep.FRESH_CACHE, step(0L, LocationRequestThrottle(now = { now })))
        assertEquals(DeviceLocationProvider.FixStep.FRESH_CACHE, step(DeviceLocationProvider.FRESH_ENOUGH_MS, LocationRequestThrottle(now = { now })))
    }

    /**
     * What taking it for fresh would cost: the try due five minutes after a search that found nothing would not be
     * made, and the ladder of tries would start again from the first.
     */
    @Test
    fun `a position from the future does not stand in for the try that is due`() {
        var now = 10 * hour
        val throttle = LocationRequestThrottle(now = { now })
        assertTrue(throttle.tryAcquire())
        throttle.requestEnded(gotPosition = false)
        now += 5 * 60 * 1000L
        assertTrue("the first try is due five minutes after", throttle.retryDue())
        assertEquals("and it is made", DeviceLocationProvider.FixStep.REQUEST, step(-2 * hour, throttle))
        throttle.requestEnded(gotPosition = false)
        now += 5 * 60 * 1000L
        assertFalse("the second try waits its fifteen minutes: the ladder was not set back", throttle.retryDue())
    }
}
