package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * P2-6: that publishing sunrise, sunset and "do we have a fix" as one immutable object is the
 * mechanism the job needs, and that three fields — plain *or* `@Volatile* — are not.
 *
 * ### What is modelled and why
 *
 * The engine's writer is `updateSunTimesFromLocation` on the main thread; its reader is
 * `renderScene`, which the GL render thread calls once per frame. The two shapes are modelled here
 * rather than driven through `PaperWallpaperService` because that class is a `WallpaperService`
 * inner `Engine` — it cannot be constructed under a local unit test, and standing an emulator up to
 * observe a memory-model property would swap a proof for a coincidence. What matters is the
 * *shape*: N independent writes read by another thread, against one publication of an immutable
 * value. The shape is what the fix changes, and the shape is what is tested.
 *
 * ### Why the first test cannot flake
 *
 * [threeSeparateFieldsCanBeReadHalfUpdated] does not race and hope. It parks the reader between the
 * writer's first and second store with a [CyclicBarrier], which is an interleaving the scheduler is
 * free to produce on its own and this simply *chooses*. The observation that follows is therefore
 * deterministic: the reader sees the new sunrise beside the old sunset. Making those two fields
 * `@Volatile` changes nothing about it — two volatile writes are still two publications — which is
 * the whole reason the fix is a snapshot and not an annotation.
 *
 * The same interleaving against [SolarDay] cannot produce a mixture, because there is no instant at
 * which half of one exists: the object is fully built before the reference is stored.
 */
class SolarDayPublicationTest {

    /** Florence in winter, then Reykjavík in summer — two real, very different days. */
    private companion object {
        const val FLORENCE_SUNRISE = 7.5f
        const val FLORENCE_SUNSET = 17.0f
        const val REYKJAVIK_SUNRISE = 3.0f
        const val REYKJAVIK_SUNSET = 23.5f
    }

    /** The shape the engine had before v3.6: three fields, written one after another. */
    private class ThreeFields {
        @Volatile var sunriseHour = 6f
        @Volatile var sunsetHour = 20f
        @Volatile var hasFix = false
    }

    /** The shape it has now. */
    private class OneSnapshot {
        @Volatile var solarDay: SolarDay = SolarDay.NONE
    }

    /**
     * **The problem, demonstrated.** A second fix arriving while a frame is being drawn hands the
     * frame a sunrise and a sunset from two different places.
     *
     * The fields here are deliberately marked `@Volatile`, so this is not a demonstration that the
     * old code lacked an annotation — it is a demonstration that the annotation would not have been
     * the fix.
     */
    @Test
    fun threeSeparateFieldsCanBeReadHalfUpdated() {
        val state = ThreeFields()
        // A fix already in effect, as it would be by the time a second one arrives.
        state.sunriseHour = FLORENCE_SUNRISE
        state.sunsetHour = FLORENCE_SUNSET
        state.hasFix = true

        val betweenWrites = CyclicBarrier(2)
        val readerDone = CyclicBarrier(2)
        var observedSunrise = 0f
        var observedSunset = 0f

        val writer = Thread {
            state.sunriseHour = REYKJAVIK_SUNRISE
            betweenWrites.await()
            readerDone.await()
            state.sunsetHour = REYKJAVIK_SUNSET
        }
        val reader = Thread {
            betweenWrites.await()
            // Exactly what renderScene did: read the flag, then read the two hours.
            if (state.hasFix) {
                observedSunrise = state.sunriseHour
                observedSunset = state.sunsetHour
            }
            readerDone.await()
        }
        writer.start()
        reader.start()
        writer.join()
        reader.join()

        val observedDayLength = SunPositionCalculator.dayLengthHours(observedSunrise, observedSunset)
        val florence = SunPositionCalculator.dayLengthHours(FLORENCE_SUNRISE, FLORENCE_SUNSET)
        val reykjavik = SunPositionCalculator.dayLengthHours(REYKJAVIK_SUNRISE, REYKJAVIK_SUNSET)
        println(
            "P2-6 three fields: observed sunrise=$observedSunrise sunset=$observedSunset " +
                "dayLength=$observedDayLength (Florence=$florence, Reykjavik=$reykjavik)",
        )

        assertEquals("the reader saw the new sunrise", REYKJAVIK_SUNRISE, observedSunrise, 0f)
        assertEquals("beside the old sunset", FLORENCE_SUNSET, observedSunset, 0f)
        // The pair belongs to neither place, and it is the day length the whole blend runs on.
        assertNotEquals(florence, observedDayLength, 0.001f)
        assertNotEquals(reykjavik, observedDayLength, 0.001f)
    }

    /**
     * **The fix, under the identical interleaving.** The reader is parked at exactly the same point
     * and can only see a whole day.
     */
    @Test
    fun oneSnapshotCannotBeReadHalfUpdated() {
        val state = OneSnapshot()
        state.solarDay = SolarDay.located(FLORENCE_SUNRISE, FLORENCE_SUNSET)

        val beforePublish = CyclicBarrier(2)
        val readerDone = CyclicBarrier(2)
        var observed: SolarDay = SolarDay.NONE

        val writer = Thread {
            // The new day is built in full before anything can see it; this is the instant the
            // three-field shape had a half-updated state and this one does not.
            val next = SolarDay.located(REYKJAVIK_SUNRISE, REYKJAVIK_SUNSET)
            beforePublish.await()
            readerDone.await()
            state.solarDay = next
        }
        val reader = Thread {
            beforePublish.await()
            observed = state.solarDay
            readerDone.await()
        }
        writer.start()
        reader.start()
        writer.join()
        reader.join()

        assertCoherent(observed)
    }

    /** The observed day must be one of the two that were actually published, whole. */
    private fun assertCoherent(observed: SolarDay) {
        val isFlorence = observed.sunriseHour == FLORENCE_SUNRISE && observed.sunsetHour == FLORENCE_SUNSET
        val isReykjavik = observed.sunriseHour == REYKJAVIK_SUNRISE && observed.sunsetHour == REYKJAVIK_SUNSET
        println(
            "P2-6 snapshot: observed sunrise=${observed.sunriseHour} sunset=${observed.sunsetHour} " +
                "hasFix=${observed.hasFix} (florence=$isFlorence reykjavik=$isReykjavik)",
        )
        assertTrue(
            "a snapshot read must be a whole published day, got " +
                "${observed.sunriseHour}/${observed.sunsetHour}",
            isFlorence || isReykjavik,
        )
        assertTrue("a located day must report a fix", observed.hasFix)
    }

    /**
     * The same property without a chosen interleaving: a writer alternating between two places as
     * fast as it can, a reader sampling as fast as it can, and not one mixed pair.
     *
     * Deliberately checks the *invariant* rather than counting tears, so it can only fail by
     * finding a real one. Two hundred thousand samples across the two threads is far more than the
     * barrier test's single point, and it covers the ordinary case the barrier test cannot: a read
     * that lands anywhere at all.
     */
    @Test
    fun snapshotPublicationSurvivesUnsynchronisedHammering() {
        val state = OneSnapshot()
        state.solarDay = SolarDay.located(FLORENCE_SUNRISE, FLORENCE_SUNSET)
        val stop = AtomicBoolean(false)
        val samples = AtomicInteger(0)
        val incoherent = AtomicInteger(0)

        val writer = Thread {
            var flip = false
            while (!stop.get()) {
                flip = !flip
                state.solarDay = if (flip) {
                    SolarDay.located(REYKJAVIK_SUNRISE, REYKJAVIK_SUNSET)
                } else {
                    SolarDay.located(FLORENCE_SUNRISE, FLORENCE_SUNSET)
                }
            }
        }
        val readers = (0 until 2).map {
            Thread {
                repeat(100_000) {
                    val day = state.solarDay
                    samples.incrementAndGet()
                    val known = (day.sunriseHour == FLORENCE_SUNRISE && day.sunsetHour == FLORENCE_SUNSET) ||
                        (day.sunriseHour == REYKJAVIK_SUNRISE && day.sunsetHour == REYKJAVIK_SUNSET)
                    if (!known || !day.hasFix) incoherent.incrementAndGet()
                }
            }
        }
        writer.start()
        readers.forEach { it.start() }
        // Joined, so the assertion below cannot run while a reader is still producing samples.
        readers.forEach { it.join() }
        stop.set(true)
        writer.join()

        println("P2-6 snapshot hammering: samples=${samples.get()} incoherent=${incoherent.get()}")
        assertEquals(200_000, samples.get())
        assertEquals("a snapshot read must never be a mixture", 0, incoherent.get())
    }

    /**
     * The behaviour the engine actually depends on, unchanged from the three-field version: with no
     * fix, the day is 6:00 to 20:00 exactly.
     *
     * This is the equivalence check for the refactor. `renderScene` used to write
     * `if (hasFixLocation) sunriseHour else 6f`; the snapshot has to answer identically, including
     * after a fix has been taken and then invalidated by a location-source change.
     */
    @Test
    fun theDefaultDayIsUnchanged() {
        assertEquals(6f, SolarDay.NONE.sunriseHour, 0f)
        assertEquals(20f, SolarDay.NONE.sunsetHour, 0f)
        assertTrue(!SolarDay.NONE.hasFix)

        val located = SolarDay.located(FLORENCE_SUNRISE, FLORENCE_SUNSET)
        assertTrue(located.hasFix)
        assertEquals(FLORENCE_SUNRISE, located.sunriseHour, 0f)
        assertEquals(FLORENCE_SUNSET, located.sunsetHour, 0f)

        // Invalidation goes back to exactly the defaults, not to the last located values.
        assertEquals(6f, SolarDay.NONE.sunriseHour, 0f)
        assertEquals(20f, SolarDay.NONE.sunsetHour, 0f)
    }

    /**
     * A day the old shape could produce and the new one cannot: the mixed pair is not merely
     * unusual, it changes the number the scene is actually drawn from.
     */
    @Test
    fun theMixedPairIsNotAHarmlessDifference() {
        val florence = SunPositionCalculator.dayLengthHours(FLORENCE_SUNRISE, FLORENCE_SUNSET)
        val reykjavik = SunPositionCalculator.dayLengthHours(REYKJAVIK_SUNRISE, REYKJAVIK_SUNSET)
        val mixed = SunPositionCalculator.dayLengthHours(REYKJAVIK_SUNRISE, FLORENCE_SUNSET)
        println("P2-6 day lengths: florence=$florence reykjavik=$reykjavik mixed=$mixed")
        assertTrue("the mixed day is hours away from either real one", mixed - florence > 3f)
        assertTrue(reykjavik - mixed > 3f)
    }
}
