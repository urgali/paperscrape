package com.paperscrape.livewallpaper.engine

/**
 * Where the render thread waits while the wallpaper is hidden, and how the main thread wakes it
 * without a wake-up ever being lost.
 *
 * ## Why a count and not only `wait`/`notifyAll`
 *
 * The render loop reads its inputs -- the surface, the visibility, the queued events, a trim -- with
 * no lock held, decides there is nothing to draw, and then parks. A wake that lands between the read
 * and the park would find nobody waiting, and the thread would sleep through it. Until v5.10B the
 * park therefore had a 200 ms timeout, which bounded the loss and cost five wake-ups a second for as
 * long as the wallpaper stayed hidden (v5.10A: 5.1 a second with the screen off, 0.145 % of a core).
 *
 * Here every [wake] adds one to [generation] under [lock]. The loop reads [generation] **before** it
 * reads its inputs, and [parkLocked] waits only if the count has not moved since: a wake that came
 * after the read has changed it, so the thread goes round again instead of parking; a wake that comes
 * once it is parked finds it waiting and notifies it. Either way the wake is seen, so the wait needs
 * no deadline. [GlRenderThread] keeps a long one ([GlRenderThread]'s `IDLE_WAIT_MS`, 10 s) as a
 * backstop only.
 *
 * `WakeLatchTest` drives the three cases with real threads: a wake before the park, a wake during it,
 * and a producer and a consumer handing over thousands of times with no timeout to rescue them.
 *
 * Plain Java monitors, no Android type: it runs on the JVM.
 */
internal class WakeLatch {

    // `java.lang.Object` and not `Any`, deliberately: `wait` and `notifyAll` exist only on the Java class.
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    val lock = Object()

    /** How many times [wake] has been called. Read it before reading anything the wake announces. */
    @Volatile
    var generation = 0
        private set

    /** Announces that something the waiting thread reads has changed. Call it after writing the change. */
    fun wake() {
        synchronized(lock) {
            generation++
            lock.notifyAll()
        }
    }

    /**
     * Waits on [lock], which the caller already holds, unless a [wake] has happened since the caller
     * read [seen] from [generation]; returns whether it waited. A wait ends at the next [wake], at
     * [timeoutMs], or on an interrupt, which is passed on (the interrupt flag is set again).
     */
    fun parkLocked(seen: Int, timeoutMs: Long): Boolean {
        check(Thread.holdsLock(lock)) { "parkLocked needs the caller to hold the lock" }
        if (generation != seen) return false
        try {
            lock.wait(timeoutMs)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return true
    }
}
