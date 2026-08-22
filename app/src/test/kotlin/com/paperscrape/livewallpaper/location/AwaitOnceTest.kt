package com.paperscrape.livewallpaper.location

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

/**
 * The five ways a callback-shaped platform API can fail to answer, and the one guarantee that
 * covers all of them: [awaitOnceOrNull] always finishes.
 *
 * This is the pure half of P2-4. The Android half — passing a real `Geocoder.GeocodeListener`
 * instead of a lambda that only implements `onGeocode` — is what routes the error case *into* the
 * bridge; these tests are what say the bridge then does something sensible with it, and they can
 * do so on the JVM precisely because the bridge has no Android in it.
 *
 * Timeouts are real rather than virtual, and deliberately short. The alternative was a new
 * `kotlinx-coroutines-test` dependency for five tests that together take under a second.
 */
class AwaitOnceTest {

    @Test
    fun `a result that arrives is returned`() = runBlocking {
        val value = awaitOnceOrNull<String>(1_000) { complete -> complete("Florence") }
        assertEquals("Florence", value)
    }

    @Test
    fun `an error is an answer, not a wait`() = runBlocking {
        // The exact shape of the v3.1 bug: the platform reports a failure instead of a result.
        // Before the fix this path did not resume the continuation at all.
        val elapsed = measureTimeMillis {
            assertNull(awaitOnceOrNull<String>(5_000) { complete -> complete(null) })
        }
        assertTrue("an error must return immediately, took ${elapsed}ms", elapsed < 1_000)
    }

    @Test
    fun `a callback that never comes times out instead of hanging`() = runBlocking {
        // The outer bound is not belt-and-braces: without it, deleting the timeout from
        // `awaitOnceOrNull` makes this test *hang* rather than fail, and a suite that hangs in CI
        // is worse than one that goes red. The sentinel is what turns "never finished" into an
        // assertion with a message.
        var elapsed = 0L
        val finished = kotlinx.coroutines.withTimeoutOrNull(4_000) {
            elapsed = measureTimeMillis {
                assertNull(awaitOnceOrNull<String>(150) { /* the platform simply never answers */ })
            }
            "finished"
        }
        assertEquals(
            "awaitOnceOrNull must give up on its own rather than being rescued by the test",
            "finished",
            finished,
        )
        assertTrue("should have given up after its own timeout, took ${elapsed}ms", elapsed in 100..3_000)
    }

    @Test
    fun `a late callback loses to the timeout rather than crashing`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val elapsed = measureTimeMillis {
                assertNull(
                    awaitOnceOrNull<String>(120) { complete ->
                        scope.launch {
                            delay(600)
                            // Resuming a continuation the timeout already abandoned must be a
                            // no-op, not an IllegalStateException on a background thread.
                            complete("too late")
                        }
                    },
                )
            }
            assertTrue("took ${elapsed}ms", elapsed < 3_000)
            delay(800) // let the late callback actually fire before the test ends
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `an API that calls back twice resumes once`() = runBlocking {
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
        repeat(50) {
            val value = awaitOnceOrNull<Int>(2_000) { complete ->
                val racers = (0 until 4).map { i ->
                    Thread {
                        calls.incrementAndGet()
                        complete(i)
                    }
                }
                racers.forEach { it.start() }
            }
            assertTrue("one of the racers' values, got $value", value in 0..3)
        }
        assertEquals(200, calls.get())
    }

    @Test
    fun `a starter that throws answers immediately instead of waiting out the timeout`() = runBlocking {
        val elapsed = measureTimeMillis {
            assertNull(
                awaitOnceOrNull<String>(5_000) { throw IllegalStateException("service not bound") },
            )
        }
        assertTrue("a synchronous failure must not wait, took ${elapsed}ms", elapsed < 1_000)
    }

    @Test
    fun `cancelling the caller cancels the wait`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val started = java.util.concurrent.CountDownLatch(1)
        val deferred = scope.async {
            awaitOnceOrNull<String>(30_000) { started.countDown() }
        }
        started.await()
        deferred.cancel()

        val thrown = try {
            deferred.await()
            null
        } catch (e: Throwable) {
            e
        }
        assertTrue("cancellation must propagate, got $thrown", thrown is CancellationException)
        scope.cancel()
    }
}
