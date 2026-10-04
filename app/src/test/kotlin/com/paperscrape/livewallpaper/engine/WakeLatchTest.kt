package com.paperscrape.livewallpaper.engine

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The render thread's park loses no wake-up, so it can wait without a deadline (v5.10B).
 *
 * The three cases of [WakeLatch], each with real threads: a wake that lands after the loop looked
 * at its inputs and before it parked must stop the park; a wake that lands while it is parked must
 * end the park; and a producer and a consumer handing over thousands of times, with a park timeout
 * far longer than the test, must never stall -- a lost wake would be a park that only its timeout
 * ends.
 */
class WakeLatchTest {

    /** Longer than any of these tests: only a lost wake would ever wait this long. */
    private val never = 60_000L

    @Test(timeout = 10_000)
    fun `a wake after the look and before the park stops the park`() {
        val latch = WakeLatch()
        val seen = latch.generation
        latch.wake() // lands between the loop's look at its inputs and its park
        val waited = synchronized(latch.lock) { latch.parkLocked(seen, never) }
        assertFalse("the loop must go round again, not park through the wake", waited)
    }

    @Test(timeout = 10_000)
    fun `a wake during the park ends it`() {
        val latch = WakeLatch()
        val parked = CountDownLatch(1)
        val done = CountDownLatch(1)
        val waited = AtomicBoolean(false)
        val thread = Thread {
            synchronized(latch.lock) {
                val seen = latch.generation
                parked.countDown()
                // `wait` releases the lock, so the wake below can only land once this is parked.
                waited.set(latch.parkLocked(seen, never))
            }
            done.countDown()
        }
        thread.isDaemon = true
        thread.start()
        assertTrue(parked.await(5, TimeUnit.SECONDS))
        latch.wake()
        assertTrue("the park did not end at the wake", done.await(5, TimeUnit.SECONDS))
        assertTrue("it had parked", waited.get())
    }

    /**
     * The loop's own shape, many times over: read the count, read the input **outside the lock**,
     * then park unless the count moved -- the input is not looked at again under the lock, just as
     * `GlRenderThread` does not re-read its surface or its visibility there. The producer writes the
     * input and wakes, at moments that fall anywhere in that sequence. Every handover has to arrive,
     * and quickly: without the count, a wake landing between the read and the park is lost and the
     * handover waits out the park's [never].
     */
    @Test(timeout = 30_000)
    fun `thousands of handovers with no deadline to rescue a lost one`() {
        val latch = WakeLatch()
        val published = AtomicInteger(0)
        val consumed = AtomicInteger(0)
        val handovers = 5_000
        val consumer = Thread {
            var last = 0
            while (last < handovers) {
                val seen = latch.generation
                val now = published.get()
                if (now != last) {
                    last = now
                    consumed.set(now)
                    continue
                }
                synchronized(latch.lock) { latch.parkLocked(seen, never) }
            }
        }
        consumer.isDaemon = true
        consumer.start()
        for (n in 1..handovers) {
            published.set(n)
            latch.wake()
            // Wait for it to be taken, so each handover is a separate chance to be lost.
            val deadline = System.nanoTime() + 5_000_000_000L
            while (consumed.get() < n) {
                assertTrue("handover $n was never taken: a lost wake", System.nanoTime() < deadline)
                Thread.onSpinWait()
            }
        }
        consumer.join(5_000)
        assertEquals(handovers, consumed.get())
    }

    @Test(expected = IllegalStateException::class)
    fun `parking without the lock is refused`() {
        WakeLatch().parkLocked(0, 1L)
    }
}
