package com.paperscrape.livewallpaper.engine

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * [DayPhaseCache] always answers what [SunPositionCalculator.compute] answers, and allocates only
 * when the hour or the sun's times change (v5.10B).
 */
class DayPhaseCacheTest {

    @Test
    fun `whatever the sequence of inputs, the phase equals a fresh compute, field for field`() {
        val random = Random(20260930)
        val cache = DayPhaseCache()
        var hour = 12f
        var sunrise = 6f
        var sunset = 20f
        var moon = 0.3f
        repeat(20_000) {
            when (random.nextInt(10)) {
                0 -> hour = random.nextFloat() * 24f                          // a new minute, or a fixed hour moved
                1 -> { sunrise = 4f + random.nextFloat() * 4f; sunset = 17f + random.nextFloat() * 5f }
                else -> moon = (moon + random.nextFloat() * 1e-6f) % 1f // the moon, every frame
            }
            assertEquals(
                SunPositionCalculator.compute(hour, sunrise, sunset, moon),
                cache.at(hour, sunrise, sunset, moon),
            )
        }
    }

    @Test
    fun `the moon alone moving keeps the one phase, and writes the moon into it`() {
        val cache = DayPhaseCache()
        val first = cache.at(21.5f, 6f, 20f, 0.10f)
        val second = cache.at(21.5f, 6f, 20f, 0.10001f)
        assertSame("no new phase for a moon that moved", first, second)
        assertEquals(0.10001f, second.moonPhase, 0f)
        assertEquals(SunPositionCalculator.compute(21.5f, 6f, 20f, 0.10001f), second)
    }

    @Test
    fun `a new hour or new sun times give a new phase`() {
        val cache = DayPhaseCache()
        val first = cache.at(12f, 6f, 20f, 0.5f)
        assertNotSame(first, cache.at(12.016667f, 6f, 20f, 0.5f))
        val third = cache.at(12.016667f, 6.5f, 20f, 0.5f)
        assertNotSame(first, third)
        assertNotSame(third, cache.at(12.016667f, 6.5f, 19.5f, 0.5f))
    }
}
