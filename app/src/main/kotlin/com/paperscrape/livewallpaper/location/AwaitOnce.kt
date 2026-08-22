package com.paperscrape.livewallpaper.location

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Bridges a "hand me a callback and I will call it, probably" platform API onto a suspend function
 * that is guaranteed to finish.
 *
 * Pure Kotlin with no Android imports, deliberately, because the failure it exists to prevent is
 * not visible in the Android call site. [LocationLabelResolver] passed a *lambda* to
 * `Geocoder.getFromLocation(..., GeocodeListener)`, and that interface has two methods:
 * `onGeocode` and `onError`. A lambda is a SAM conversion of the first one only. Every error the
 * platform reported therefore arrived at a method nobody had implemented, the continuation was
 * never resumed, and the coroutine waited for a callback that was never coming — forever, since
 * there was no timeout either. Upstream, that was a settings row stuck on "Locating..." for the
 * lifetime of the screen.
 *
 * Three guarantees, and each of them is a separate way that call could hang:
 *
 * 1. **It always completes.** [timeoutMillis] bounds the wait whatever the callback does, which
 *    covers the cases no amount of implementing interfaces can: a service that dies mid-request,
 *    a binder that never returns.
 * 2. **It completes once.** A platform API that calls back twice — or calls both its success and
 *    its failure path, which nothing prevents — must not resume a continuation twice, which is an
 *    `IllegalStateException` on the thread that happened to be second.
 * 3. **It stays cancellable.** The wait is a real suspension point, so a caller that goes away
 *    (a settings screen closing while the lookup is in flight) takes the wait with it rather than
 *    holding the scope open.
 *
 * What it deliberately does *not* do is poll, or interrupt whatever the platform is doing. The
 * work may well still be running when this returns null; what ends is the waiting.
 */
internal suspend fun <T> awaitOnceOrNull(
    timeoutMillis: Long,
    start: (complete: (T?) -> Unit) -> Unit,
): T? = withTimeoutOrNull(timeoutMillis) {
    suspendCancellableCoroutine { continuation ->
        // `isActive` alone is not enough: two threads can both observe an active continuation and
        // both resume it. The flag is what makes "once" true rather than likely.
        val done = AtomicBoolean(false)
        val complete: (T?) -> Unit = { value ->
            if (done.compareAndSet(false, true) && continuation.isActive) {
                continuation.resume(value)
            }
        }
        try {
            start(complete)
        } catch (error: Throwable) {
            // A platform call that throws synchronously would otherwise leave the continuation
            // suspended until the timeout, turning an immediate failure into a slow one.
            if (done.compareAndSet(false, true) && continuation.isActive) continuation.resume(null)
            if (error is Error) throw error
        }
    }
}
