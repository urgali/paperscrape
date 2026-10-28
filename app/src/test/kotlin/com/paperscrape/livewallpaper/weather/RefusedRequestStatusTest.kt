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
 * **The two statuses of a refused request**: [LiveWeatherStatus.REFUSED] with nothing left to draw, and
 * [LiveWeatherStatus.REFUSED_STALE] with the last conditions still drawn, as [LiveWeatherStatus.STALE] keeps them. The
 * provider answered, so the screen does not say it could not be reached; the scene is the scene of any other failed
 * fetch, and the refusal stays the reason once the conditions it was showing age out.
 */
class RefusedRequestStatusTest {

    private val hour = WEATHER_REFRESH_INTERVAL_MS
    private val refused = WeatherFetchResult.Failed(WeatherFailure.REQUEST_REFUSED, WeatherProviderId.WEATHER_API_COM)

    private fun snapshotAt(millis: Long) = LiveWeatherSnapshot(
        precipitationType = PrecipitationType.RAIN,
        precipitationIntensity = 0.5f,
        cloudCoverFraction = 1f,
        isThunderstorm = false,
        fetchedAtMillis = millis,
    )

    @Test
    fun `a refusal is one of two statuses, by whether conditions are left to draw`() {
        assertEquals(LiveWeatherStatus.REFUSED, LiveWeatherStatus.of(true, true, refused, hasSnapshotInEffect = false, previous = LiveWeatherStatus.OK))
        assertEquals(LiveWeatherStatus.REFUSED_STALE, LiveWeatherStatus.of(true, true, refused, hasSnapshotInEffect = true, previous = LiveWeatherStatus.OK))
        assertTrue(LiveWeatherStatus.REFUSED.isRunningOnThemeWeather)
        assertFalse(LiveWeatherStatus.REFUSED.isDrivingTheScene)
        assertTrue(LiveWeatherStatus.REFUSED_STALE.isDrivingTheScene)
        assertFalse(LiveWeatherStatus.REFUSED_STALE.isRunningOnThemeWeather)
    }

    @Test
    fun `the last conditions are drawn while they last, and then the refusal is still what is said`() {
        val now = 10 * hour
        val fresh = snapshotAt(now - hour)
        val held = LiveWeatherSchedule.decide(true, true, refused, fresh, now, LiveWeatherStatus.OK)
        assertEquals(LiveWeatherStatus.REFUSED_STALE, held.status)
        assertSame(fresh, held.snapshotForScene)

        val old = snapshotAt(now - 3 * hour)
        val aged = LiveWeatherSchedule.decide(true, true, null, old, now, LiveWeatherStatus.REFUSED_STALE)
        assertEquals(LiveWeatherStatus.REFUSED, aged.status)
        assertNull(aged.snapshotForScene)
    }

    @Test
    fun `the two statuses are stored and read back`() {
        assertEquals("refused", LiveWeatherStatus.REFUSED.storageId)
        assertEquals("refused_stale", LiveWeatherStatus.REFUSED_STALE.storageId)
        for (status in listOf(LiveWeatherStatus.REFUSED, LiveWeatherStatus.REFUSED_STALE)) {
            assertEquals(status, LiveWeatherStatus.fromStorageId(status.storageId))
        }
    }
}
