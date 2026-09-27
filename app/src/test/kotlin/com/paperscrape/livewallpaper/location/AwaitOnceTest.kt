package com.paperscrape.livewallpaper.location

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * The five ways a callback-shaped platform API can fail to answer, and the one guarantee that
 * covers all of them: [awaitOnceOrNull] always finishes.
 *
 * This is the pure half of P2-4. The Android half — passing a real `Geocoder.GeocodeListener`
 * instead of a lambda that only implements `onGeocode` — is what routes the error case *into* the
 * bridge; these tests are what say the bridge then does something sensible with it, and they can
 * do so on the JVM precisely because the bridge has no Android in it.
 *
 * **Time is virtual (v5.8).** Every test that is about *when* the bridge answers runs under
 * `runTest`, whose scheduler is also the clock `withTimeoutOrNull` reads, so "returns at once"
 * is `currentTime == 0` and "gives up after its own 150 ms" is `currentTime == 150` -- exact, and
 * the same on an idle laptop and on a CI runner compiling something else. The tests used to
 * measure real elapsed time against generous bounds, and one of them still failed on a busy
 * machine: 5 337 ms against 3 000 (item 143). A bound on wall-clock time is a statement about the
 * host, not about the code. The one test left on `runBlocking` races real threads and measures
 * nothing.
 */
class AwaitOnceTest {

    @Test
    fun `a result that arrives is returned`() = runTest {
        val value = awaitOnceOrNull<String>(1_000) { complete -> complete("Florence") }
        assertEquals("Florence", value)
        assertEquals("an answer given at once must not wait", 0L, currentTime)
    }

    @Test
    fun `an error is an answer, not a wait`() = runTest {
        // The exact shape of the v3.1 bug: the platform reports a failure instead of a result.
        // Before the fix this path did not resume the continuation at all.
        assertNull(awaitOnceOrNull<String>(5_000) { complete -> complete(null) })
        assertEquals("an error must return immediately, not after the timeout", 0L, currentTime)
    }

    @Test
    fun `a callback that never comes times out instead of hanging`() = runTest {
        // Without the timeout inside `awaitOnceOrNull` this test would not finish: nothing is left
        // on the scheduler, so virtual time cannot advance, and `runTest`'s own bound turns the
        // hang into a failure rather than a stuck suite.
        assertNull(awaitOnceOrNull<String>(150) { /* the platform simply never answers */ })
        assertEquals("should have given up after exactly its own timeout", 150L, currentTime)
    }

    @Test
    fun `a late callback loses to the timeout rather than crashing`() = runTest {
        val value = awaitOnceOrNull<String>(120) { complete ->
            launch {
                delay(600)
                // Resuming a continuation the timeout already abandoned must be a no-op, not an
                // IllegalStateException -- which, thrown here, would fail this test.
                complete("too late")
            }
        }
        assertNull(value)
        assertEquals("the timeout, not the late callback, decided the answer", 120L, currentTime)
        advanceUntilIdle() // let the late callback actually fire before the test ends
        assertEquals("the late callback never ran, so nothing was proved", 600L, currentTime)
    }

    @Test
    fun `an API that calls back twice resumes once`() = runTest {
        // Nothing stops a platform listener calling both its success and its failure path, and
        // resuming a continuation twice throws on whichever thread is second.
        val value = awaitOnceOrNull<String>(1_000) { complete ->
            complete("first")
            complete("second")
            complete(null)
        }
        assertEquals("first", value)
    }

    @Test
    fun `two threads racing to complete resume once`() = runBlocking {
        val calls = AtomicInteger()
        // Held across the suspension so the racers can be joined after `awaitOnceOrNull` returns:
        // the lambda is what starts them, and only the caller knows when it is safe to stop.
        var racers: List<Thread> = emptyList()
        repeat(50) {
            val value = awaitOnceOrNull<Int>(2_000) { complete ->
                racers = (0 until 4).map { i ->
                    Thread {
                        calls.incrementAndGet()
                        complete(i)
                    }
                }
                racers.forEach { it.start() }
            }
            assertTrue("one of the racers' values, got $value", value in 0..3)
            // **`awaitOnceOrNull` returns on the *first* completion -- by contract -- so three of
            // these four threads may not have run `calls.incrementAndGet()` yet.** Without this
            // join the count below is read while they are still in flight, and the test asserts
            // something `awaitOnceOrNull` never promised: not "one resume", which is checked above
            // and is the point of the test, but "all four callbacks have already happened".
            //
            // That is a defect in this test and it failed in CI as `expected:<200> but was:<199>`
            // -- exactly one straggler on a four-vCPU runner. Reproduced 300 times out of 300 by
            // modelling the same structure without the join, and 0 times out of 300 with it. Not a
            // tolerance, a delay or a retry: the test simply waits for the threads it started.
            racers.forEach { it.join() }
        }
        assertEquals(200, calls.get())
    }

    @Test
    fun `a starter that throws answers immediately instead of waiting out the timeout`() = runTest {
        assertNull(
            awaitOnceOrNull<String>(5_000) { throw IllegalStateException("service not bound") },
        )
        assertEquals("a synchronous failure must not wait", 0L, currentTime)
    }

    @Test
    fun `cancelling the caller cancels the wait`() = runTest {
        val deferred = async {
            awaitOnceOrNull<String>(30_000) { /* never answers */ }
        }
        runCurrent() // the wait has started
        deferred.cancel()

        val thrown = try {
            deferred.await()
            null
        } catch (e: Throwable) {
            e
        }
        assertTrue("cancellation must propagate, got $thrown", thrown is CancellationException)
        assertEquals("the cancelled wait sat out its 30 s timeout", 0L, currentTime)
    }
}
