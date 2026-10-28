package com.paperscrape.livewallpaper.weather

import com.paperscrape.livewallpaper.engine.PrecipitationType
import com.paperscrape.livewallpaper.engine.WEATHER_REFRESH_INTERVAL_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The two statuses of a provider's limit**: [LiveWeatherStatus.RATE_LIMITED] with nothing left to draw, and
 * [LiveWeatherStatus.RATE_LIMITED_STALE] with the last conditions still drawn, as [LiveWeatherStatus.STALE] keeps them
 * -- so under a limit the scene is the scene of any other failed fetch, and only what the screen says is the limit's
 * own (`ProviderLimitMessageTest`).
 */
class ProviderLimitStatusTest {

    private val hour = WEATHER_REFRESH_INTERVAL_MS
    private val limited = WeatherFetchResult.Failed(WeatherFailure.RATE_LIMITED, WeatherProviderId.WEATHER_API_COM)

    private fun snapshotAt(millis: Long) = LiveWeatherSnapshot(
        precipitationType = PrecipitationType.RAIN,
        precipitationIntensity = 0.5f,
        cloudCoverFraction = 1f,
        isThunderstorm = false,
        fetchedAtMillis = millis,
    )

    @Test
    fun `a limit is one of two statuses, by whether conditions are left to draw`() {
        assertEquals(LiveWeatherStatus.RATE_LIMITED, LiveWeatherStatus.of(true, true, limited, hasSnapshotInEffect = false, previous = LiveWeatherStatus.OK))
        assertEquals(LiveWeatherStatus.RATE_LIMITED_STALE, LiveWeatherStatus.of(true, true, limited, hasSnapshotInEffect = true, previous = LiveWeatherStatus.OK))
        assertTrue(LiveWeatherStatus.RATE_LIMITED.isRunningOnThemeWeather)
        assertFalse(LiveWeatherStatus.RATE_LIMITED.isDrivingTheScene)
        assertTrue(LiveWeatherStatus.RATE_LIMITED_STALE.isDrivingTheScene)
        assertFalse(LiveWeatherStatus.RATE_LIMITED_STALE.isRunningOnThemeWeather)
    }

    @Test
    fun `the last conditions are drawn while they last, and then the limit is still what is said`() {
        val now = 10 * hour
        val fresh = snapshotAt(now - hour)
        val held = LiveWeatherSchedule.decide(true, true, limited, fresh, now, LiveWeatherStatus.OK)
        assertEquals(LiveWeatherStatus.RATE_LIMITED_STALE, held.status)
        assertSame(fresh, held.snapshotForScene)

        // Three hours on, no fetch on this pass: the conditions age out, the limit stays the reason.
        val old = snapshotAt(now - 3 * hour)
        val aged = LiveWeatherSchedule.decide(true, true, null, old, now, LiveWeatherStatus.RATE_LIMITED_STALE)
        assertEquals(LiveWeatherStatus.RATE_LIMITED, aged.status)
        assertNull(aged.snapshotForScene)
        assertTrue("it eases to the theme's sky, as any expiry does", aged.lapsed)
    }

    @Test
    fun `a limit is not tried again sooner than the hour`() {
        assertFalse(LiveWeatherSchedule.isTransient(limited))
    }

    @Test
    fun `the two statuses are stored and read back, and an older build reads them as off`() {
        assertEquals("rate_limited", LiveWeatherStatus.RATE_LIMITED.storageId)
        assertEquals("rate_limited_stale", LiveWeatherStatus.RATE_LIMITED_STALE.storageId)
        for (status in listOf(LiveWeatherStatus.RATE_LIMITED, LiveWeatherStatus.RATE_LIMITED_STALE)) {
            assertEquals(status, LiveWeatherStatus.fromStorageId(status.storageId))
        }
    }
}
